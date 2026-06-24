/* taskListOrderEditExtension.js */
(function() {
	"use strict";

	const SELECTORS = {
		form: ".admin-task-list-second-update-form",
		companySelect: ".admin-task-list-second-company-select",
		requesterSelect: ".admin-task-list-second-requester-select",
		deliveryMethodSelect: ".admin-task-list-second-delivery-method-select",
		deliveryHandlerSelect: ".admin-task-list-second-delivery-handler-select",
		preferredDeliveryDateInput: 'input[name="preferredDeliveryDate"]',
		statusSelect: 'select[name="status"]',
		addressSource: ".admin-task-list-second-delivery-address-source button, #admin-task-list-second-delivery-address-source button",
		ordererSource: ".admin-task-list-second-orderer-source button, #admin-task-list-second-orderer-source button",
		pickerModal: "#admin-task-list-second-picker-modal",
		pickerTitle: "#admin-task-list-second-picker-title",
		pickerDesc: "#admin-task-list-second-picker-desc",
		pickerKeyword: "#admin-task-list-second-picker-keyword",
		pickerList: "#admin-task-list-second-picker-list"
	};

	const SITE_DELIVERY_METHOD_NAME = "현장배송";
	const REQUIRED_HANDLER_METHOD_NAMES = new Set(["직배송", "화물", "현장배송"]);

	const OPTION_VALUE_FIXED_KEYS = new Set([
		"카테고리",
		"제품시리즈",
		"제품시리즈ID"
	]);

	const OPTION_DELETE_BLOCKED_KEYS = new Set([
		"카테고리",
		"제품시리즈",
		"제품시리즈ID",
		"제품명",
		"사이즈",
		"색상"
	]);

	let activePicker = null;

	const companyOptionsCache = new Map();
	const companyOptionsLoading = new Map();

	window.AdminTaskListSecondOrderEditExtension = {
		initForms: initForms,
		initForm: initForm
	};

	document.addEventListener("DOMContentLoaded", function() {
		initForms(document);
		initPickerModal();
	});

	function initForms(root) {
		const base = root || document;
		base.querySelectorAll(SELECTORS.form).forEach(function(form) {
			initForm(form);
		});
	}

	function initForm(form) {
		if (!form || form.dataset.orderEditInitialized === "true") {
			return;
		}

		form.dataset.orderEditInitialized = "true";

		initMoneyBox(form);
		initCompanySelect(form);
		initDeliveryMethodRule(form);
		initAddressButtons(form);
		initOrdererButtons(form);
		initOptionEditor(form);

		form.addEventListener("submit", function(event) {
			try {
				validateDeliveryMethodBeforeSubmit(form);
				validateAddressBeforeSubmit(form);
				normalizeMoneyBeforeSubmit(form);
				normalizeHiddenAddressBeforeSubmit(form);
				buildOptionJsonBeforeSubmit(form);
			} catch (error) {
				event.preventDefault();
				event.stopPropagation();
				alert(error.message || "입력값을 확인해 주세요.");
			}
		}, true);
	}

	// =========================================================
	// 대리점 / 신청자 연동
	// =========================================================

	function initCompanySelect(form) {
		const companySelect = form.querySelector(SELECTORS.companySelect);
		const requesterSelect = form.querySelector(SELECTORS.requesterSelect);

		if (!companySelect) {
			return;
		}

		if (requesterSelect) {
			renderRequesterSelectFromSource(form, requesterSelect.dataset.selectedMemberId || requesterSelect.value || "");
		}

		companySelect.addEventListener("change", async function() {
			const selectedCompanyId = companySelect.value || "";

			if (requesterSelect) {
				requesterSelect.dataset.selectedMemberId = "";
				requesterSelect.value = "";
			}

			if (!selectedCompanyId) {
				clearCompanyDependentSources(form);
				renderRequesterSelectFromSource(form, "");
				return;
			}

			const requestKey = String(Date.now());
			form.dataset.companyOptionsRequestKey = requestKey;

			try {
				setCompanySelectBusy(companySelect, true);
				await loadCompanyOptionsIntoForm(form, selectedCompanyId, requestKey);
			} catch (error) {
				alert(error.message || "대리점 정보를 불러오지 못했습니다.");
			} finally {
				setCompanySelectBusy(companySelect, false);
			}
		});

		if (requesterSelect) {
			requesterSelect.addEventListener("change", function() {
				requesterSelect.dataset.selectedMemberId = requesterSelect.value || "";
			});
		}
	}

	// =========================================================
	// 배송수단 / 배송담당자 / 주소 섹션 연동
	// =========================================================

	function initDeliveryMethodRule(form) {
		const methodSelect = form.querySelector(SELECTORS.deliveryMethodSelect);
		const handlerSelect = form.querySelector(SELECTORS.deliveryHandlerSelect);

		if (!methodSelect || !handlerSelect) {
			return;
		}

		syncDeliveryMethodDependentState(form, false);

		methodSelect.addEventListener("change", function() {
			syncDeliveryMethodDependentState(form, true);
		});

		const statusSelect = form.querySelector(SELECTORS.statusSelect);
		if (statusSelect) {
			statusSelect.addEventListener("change", function() {
				syncDeliveryMethodDependentState(form, false);
			});
		}
	}

	function syncDeliveryMethodDependentState(form, changedByUser) {
		syncDeliveryHandlerState(form);
		syncAddressSections(form, changedByUser);
	}

	function syncDeliveryHandlerState(form) {
		const methodSelect = form.querySelector(SELECTORS.deliveryMethodSelect);
		const handlerSelect = form.querySelector(SELECTORS.deliveryHandlerSelect);
		const help = form.querySelector(".admin-task-list-second-delivery-handler-help");

		if (!methodSelect || !handlerSelect) {
			return;
		}

		const methodName = resolveSelectedDeliveryMethodName(methodSelect);
		const required = REQUIRED_HANDLER_METHOD_NAMES.has(methodName);
		const canceled = isCanceledStatus(form);

		if (canceled) {
			handlerSelect.value = "";
			handlerSelect.disabled = true;
			handlerSelect.required = false;
			handlerSelect.classList.add("bg-light");
			if (help) {
				help.textContent = "취소 상태에서는 배송담당자와 배송순서가 저장되지 않습니다.";
			}
			return;
		}

		handlerSelect.disabled = false;
		handlerSelect.required = required;
		handlerSelect.classList.remove("bg-light");

		if (help) {
			help.textContent = required
				? methodName + " 선택 시 배송팀 담당자는 필수입니다."
				: "이 배송수단은 담당자 지정이 선택사항입니다. 지정하면 배송담당자 화면의 기타 영역에 표시됩니다.";
		}
	}

	function syncAddressSections(form, changedByUser) {
		const deliverySection = findAddressSection(form, "delivery");
		const siteSection = findAddressSection(form, "site");
		const methodSelect = form.querySelector(SELECTORS.deliveryMethodSelect);

		if (!deliverySection || !siteSection || !methodSelect) {
			return;
		}

		const siteDelivery = isSiteDeliverySelected(methodSelect);

		deliverySection.classList.toggle("d-none", siteDelivery);
		siteSection.classList.toggle("d-none", !siteDelivery);

		setAddressInputsRequired(form, "delivery", !siteDelivery);
		setAddressInputsRequired(form, "site", siteDelivery);

		if (changedByUser && siteDelivery && !getAddressValue(form, "site", "roadAddress")) {
			copyAddress(form, "delivery", "site");
		}

		syncAddressPreview(form, "delivery");
		syncAddressPreview(form, "site");
	}

	function normalizeAddressRole(role) {
		if (role === "normal" || role === "delivery") {
			return "delivery";
		}

		if (role === "site") {
			return "site";
		}

		return "delivery";
	}

	function resolveAddressTarget(button) {
		const rawRole = button.dataset.addressTarget
			|| button.closest(".admin-task-list-second-address-section")?.dataset.addressRole
			|| button.closest(".admin-task-list-second-site-address-section")?.dataset.addressTarget
			|| "delivery";

		return normalizeAddressRole(rawRole);
	}

	function findAddressSection(form, role) {
		const normalizedRole = normalizeAddressRole(role);

		if (normalizedRole === "site") {
			return form.querySelector('.admin-task-list-second-address-section[data-address-role="site"]')
				|| form.querySelector(".admin-task-list-second-site-address-section");
		}

		return form.querySelector('.admin-task-list-second-address-section[data-address-role="delivery"]')
			|| form.querySelector(".admin-task-list-second-normal-address-section");
	}

	function setAddressInputsRequired(form, role, required) {
		const roadInput = getAddressInput(form, role, "roadAddress");
		if (roadInput) {
			roadInput.required = required;
		}
	}

	function validateDeliveryMethodBeforeSubmit(form) {
		const methodSelect = form.querySelector(SELECTORS.deliveryMethodSelect);
		const handlerSelect = form.querySelector(SELECTORS.deliveryHandlerSelect);
		const preferredDeliveryDateInput = form.querySelector(SELECTORS.preferredDeliveryDateInput);

		if (!methodSelect || !handlerSelect) {
			return;
		}

		const methodName = resolveSelectedDeliveryMethodName(methodSelect);
		const required = REQUIRED_HANDLER_METHOD_NAMES.has(methodName);
		const canceled = isCanceledStatus(form);

		if (canceled) {
			handlerSelect.value = "";
			handlerSelect.disabled = true;
			return;
		}

		handlerSelect.disabled = false;

		if (required && !handlerSelect.value) {
			throw new Error(methodName + " 선택 시 배송팀 담당자는 필수입니다.");
		}

		if (handlerSelect.value && (!preferredDeliveryDateInput || !preferredDeliveryDateInput.value)) {
			throw new Error("배송팀 담당자를 지정하는 경우 배송희망일은 필수입니다.");
		}
	}

	function validateAddressBeforeSubmit(form) {
		const methodSelect = form.querySelector(SELECTORS.deliveryMethodSelect);
		if (!methodSelect) {
			return;
		}

		const siteDelivery = isSiteDeliverySelected(methodSelect);

		if (siteDelivery) {
			if (!getAddressValue(form, "site", "roadAddress")) {
				throw new Error("현장배송 선택 시 현장주소는 필수입니다.");
			}
			return;
		}

		if (!getAddressValue(form, "delivery", "roadAddress")) {
			throw new Error("현장배송이 아닌 경우 배송주소는 필수입니다.");
		}
	}

	function normalizeHiddenAddressBeforeSubmit(form) {
		const methodSelect = form.querySelector(SELECTORS.deliveryMethodSelect);
		if (!methodSelect) {
			return;
		}

		if (!isSiteDeliverySelected(methodSelect)) {
			clearAddress(form, "site");
		}
	}

	function resolveSelectedDeliveryMethodName(methodSelect) {
		if (!methodSelect) {
			return "";
		}

		const selectedOption = methodSelect.options[methodSelect.selectedIndex];
		if (!selectedOption || !selectedOption.value) {
			return "";
		}

		const dataName = selectedOption.dataset.deliveryMethodName || "";
		const textName = selectedOption.textContent || "";

		return String(dataName || textName)
			.replace(/\s*\(금액:.*?\)\s*$/g, "")
			.trim();
	}

	function isSiteDeliverySelected(methodSelect) {
		return resolveSelectedDeliveryMethodName(methodSelect) === SITE_DELIVERY_METHOD_NAME;
	}

	function isCanceledStatus(form) {
		const statusSelect = form.querySelector(SELECTORS.statusSelect);
		return statusSelect && statusSelect.value === "CANCELED";
	}

	// =========================================================
	// 금액 계산
	// =========================================================

	function initMoneyBox(form) {
		const inputs = Array.from(form.querySelectorAll(".admin-task-list-second-money-input[data-money-role]"));
		if (inputs.length === 0) {
			return;
		}

		const unitInput = getMoneyInput(form, "unit");
		const quantityInput = getMoneyInput(form, "quantity");
		const supplyInput = getMoneyInput(form, "supply");
		const totalInput = getMoneyInput(form, "total");

		if (supplyInput && unitInput && quantityInput) {
			const initialUnit = parseInteger(unitInput.value);
			const initialQuantity = parseInteger(quantityInput.value);
			const initialSupply = parseInteger(supplyInput.value);
			const initialAutoSupply = initialUnit > 0 && initialQuantity > 0 ? initialUnit * initialQuantity : 0;
			supplyInput.dataset.supplyManual = initialSupply !== initialAutoSupply ? "true" : "false";
		}

		if (totalInput) {
			totalInput.readOnly = true;
		}

		inputs.forEach(function(input) {
			input.addEventListener("input", function() {
				const changedRole = input.dataset.moneyRole;
				if (changedRole === "supply") {
					input.dataset.supplyManual = "true";
				}
				recalculateMoney(form, changedRole);
			});
		});

		recalculateMoney(form, "initial");
	}

	function normalizeMoneyBeforeSubmit(form) {
		recalculateMoney(form, "submit");

		["unit", "quantity", "supply", "total", "packing", "delivery"].forEach(function(role) {
			const input = getMoneyInput(form, role);
			if (!input) {
				return;
			}

			const value = parseInteger(input.value);
			if (value < 0) {
				const label = input.closest(".admin-task-list-second-field")?.querySelector("label")?.textContent?.trim();
				throw new Error((label || "금액") + " 값은 0보다 작을 수 없습니다.");
			}
			input.value = String(value);
		});
	}

	function recalculateMoney(form, changedRole) {
		const unitInput = getMoneyInput(form, "unit");
		const quantityInput = getMoneyInput(form, "quantity");
		const supplyInput = getMoneyInput(form, "supply");
		const vatInput = getMoneyInput(form, "vat");
		const totalInput = getMoneyInput(form, "total");

		if (!unitInput || !quantityInput || !supplyInput || !vatInput || !totalInput) {
			return;
		}

		let unit = parseInteger(unitInput.value);
		let quantity = parseInteger(quantityInput.value);
		let supply = parseInteger(supplyInput.value);
		const supplyManual = supplyInput.dataset.supplyManual === "true";

		if ((changedRole === "unit" || changedRole === "quantity") && !supplyManual) {
			supply = unit > 0 && quantity > 0 ? unit * quantity : 0;
		}

		supply = Math.max(0, supply);
		const total = Math.round(supply * 1.1);
		const vat = Math.max(0, total - supply);

		unitInput.value = String(Math.max(0, unit));
		quantityInput.value = String(Math.max(0, quantity));
		supplyInput.value = String(supply);
		vatInput.value = String(vat);
		totalInput.value = String(total);
	}

	function getMoneyInput(form, role) {
		return form.querySelector('.admin-task-list-second-money-input[data-money-role="' + role + '"]');
	}

	// =========================================================
	// 배송주소 / 현장주소
	// =========================================================

	function initAddressButtons(form) {
		form.querySelectorAll(".admin-task-list-second-address-search-btn").forEach(function(btn) {
			btn.addEventListener("click", function(event) {
				event.preventDefault();
				openDaumPostcode(form, resolveAddressTarget(btn));
			});
		});

		form.querySelectorAll(".admin-task-list-second-address-book-btn").forEach(function(btn) {
			btn.addEventListener("click", function(event) {
				event.preventDefault();
				openCompanyAddressBook(form, resolveAddressTarget(btn));
			});
		});

		form.querySelectorAll(".admin-task-list-second-company-address-btn, .admin-task-list-second-company-address-same-btn").forEach(function(btn) {
			btn.addEventListener("click", function(event) {
				event.preventDefault();
				applySelectedCompanyAddress(form, resolveAddressTarget(btn));
			});
		});

		["delivery", "site"].forEach(function(role) {
			syncAddressPreview(form, role);

			const detailInput = getAddressInput(form, role, "detailAddress");
			if (detailInput) {
				detailInput.addEventListener("input", function() {
					syncAddressPreview(form, role);
				});
			}

			const roadInput = getAddressInput(form, role, "roadAddress");
			if (roadInput) {
				roadInput.addEventListener("input", function() {
					syncAddressPreview(form, role);
				});
			}
		});
	}

	async function openCompanyAddressBook(form, role) {
		const companyId = resolveSelectedCompanyId(form);
		if (!companyId) {
			alert("먼저 대리점명을 선택해 주세요.");
			return;
		}

		try {
			await ensureCompanyOptionsLoaded(form, companyId);
		} catch (error) {
			alert(error.message || "등록 배송지 정보를 불러오지 못했습니다.");
			return;
		}

		const items = getAddressSourceItems(form).filter(function(item) {
			return String(item.companyId || "") === String(companyId || "");
		});

		openPicker({
			title: role === "site" ? "현장주소 검색" : "등록 배송지 검색",
			desc: "선택한 대리점에 등록된 배송지를 선택합니다.",
			emptyText: "등록된 배송지가 없습니다.",
			items: items,
			keywordFields: ["fullAddress", "roadAddress", "detailAddress"],
			renderItem: function(item) {
				const title = item.fullAddress || item.roadAddress || "-";
				const sub = [item.zipCode, item.doName, item.siName, item.guName].filter(Boolean).join(" ");
				return { title: title, sub: sub };
			},
			onSelect: function(item) {
				applyAddress(form, role, item);
			}
		});
	}

	async function applySelectedCompanyAddress(form, role) {
		const companyId = resolveSelectedCompanyId(form);
		if (!companyId) {
			alert("먼저 대리점명을 선택해 주세요.");
			return;
		}

		try {
			await ensureCompanyOptionsLoaded(form, companyId);
		} catch (error) {
			alert(error.message || "대리점 기본주소 정보를 불러오지 못했습니다.");
			return;
		}

		const item = getCompanySourceItem(form, companyId);

		if (!item || !item.roadAddress) {
			alert("선택한 대리점에 기본 주소가 등록되어 있지 않습니다.");
			return;
		}

		applyAddress(form, role, item);
	}

	function getCompanySourceItem(form, companyId) {
		const root = getDetailPanelRoot(form);

		const source = Array.from(root.querySelectorAll(".admin-task-list-second-company-source [data-company-id]"))
			.find(function(el) {
				return String(el.dataset.companyId || "") === String(companyId || "");
			});

		if (!source) {
			return null;
		}

		return {
			companyId: source.dataset.companyId || "",
			zipCode: source.dataset.zipCode || "",
			doName: source.dataset.doName || "",
			siName: source.dataset.siName || "",
			guName: source.dataset.guName || "",
			roadAddress: source.dataset.roadAddress || "",
			detailAddress: source.dataset.detailAddress || ""
		};
	}

	function openDaumPostcode(form, role) {
		if (!window.daum || !window.daum.Postcode) {
			alert("Daum 우편번호 스크립트를 불러오지 못했습니다. 네트워크 또는 스크립트 경로를 확인해 주세요.");
			return;
		}

		new window.daum.Postcode({
			oncomplete: function(data) {
				const sigungu = data.sigungu || "";
				const parsed = splitSigungu(sigungu);

				applyAddress(form, role, {
					zipCode: data.zonecode || "",
					doName: data.sido || "",
					siName: parsed.siName,
					guName: parsed.guName,
					roadAddress: data.roadAddress || data.address || "",
					detailAddress: ""
				});

				const detailInput = getAddressInput(form, role, "detailAddress");
				if (detailInput) {
					detailInput.focus();
				}
			}
		}).open();
	}

	function splitSigungu(sigungu) {
		const parts = String(sigungu || "").trim().split(/\s+/).filter(Boolean);
		if (parts.length === 0) {
			return { siName: "", guName: "" };
		}
		return { siName: parts[0] || "", guName: parts.slice(1).join(" ") };
	}

	function applyAddress(form, role, item) {
		setAddressValue(form, role, "zipCode", item.zipCode);
		setAddressValue(form, role, "doName", item.doName);
		setAddressValue(form, role, "siName", item.siName);
		setAddressValue(form, role, "guName", item.guName);
		setAddressValue(form, role, "roadAddress", item.roadAddress);
		setAddressValue(form, role, "detailAddress", item.detailAddress);
		syncAddressPreview(form, role);
	}

	function copyAddress(form, fromRole, toRole) {
		["zipCode", "doName", "siName", "guName", "roadAddress", "detailAddress"].forEach(function(field) {
			setAddressValue(form, toRole, field, getAddressValue(form, fromRole, field));
		});
		syncAddressPreview(form, toRole);
	}

	function clearAddress(form, role) {
		["zipCode", "doName", "siName", "guName", "roadAddress", "detailAddress"].forEach(function(field) {
			setAddressValue(form, role, field, "");
		});
		syncAddressPreview(form, role);
	}

	function syncAddressPreview(form, role) {
		const normalizedRole = normalizeAddressRole(role);

		let preview = form.querySelector('.admin-task-list-second-address-preview[data-address-role="' + normalizedRole + '"]');

		if (!preview && normalizedRole === "site") {
			preview = form.querySelector(".admin-task-list-second-site-address-preview");
		}

		if (!preview && normalizedRole === "delivery") {
			preview = form.querySelector(".admin-task-list-second-normal-address-section .admin-task-list-second-address-preview");
		}

		if (!preview) {
			return;
		}

		const text = [
			getAddressValue(form, normalizedRole, "zipCode") ? "(" + getAddressValue(form, normalizedRole, "zipCode") + ")" : "",
			getAddressValue(form, normalizedRole, "doName"),
			getAddressValue(form, normalizedRole, "siName"),
			getAddressValue(form, normalizedRole, "guName"),
			getAddressValue(form, normalizedRole, "roadAddress"),
			getAddressValue(form, normalizedRole, "detailAddress")
		].filter(Boolean).join(" ");

		preview.textContent = text || "-";
	}

	function getAddressInput(form, role, field) {
		const normalizedRole = normalizeAddressRole(role);

		const direct = form.querySelector(
			'.admin-task-list-second-address-input[data-address-role="' + normalizedRole + '"][data-address-field="' + field + '"]'
		);

		if (direct) {
			return direct;
		}

		const nameMap = {
			delivery: {
				zipCode: "zipCode",
				doName: "doName",
				siName: "siName",
				guName: "guName",
				roadAddress: "roadAddress",
				detailAddress: "detailAddress"
			},
			site: {
				zipCode: "siteZipCode",
				doName: "siteDoName",
				siName: "siteSiName",
				guName: "siteGuName",
				roadAddress: "siteRoadAddress",
				detailAddress: "siteDetailAddress"
			}
		};

		const inputName = nameMap[normalizedRole]?.[field];
		if (!inputName) {
			return null;
		}

		return form.querySelector('[name="' + inputName + '"]');
	}

	function getAddressValue(form, role, field) {
		const input = getAddressInput(form, role, field);
		return input ? input.value.trim() : "";
	}

	function setAddressValue(form, role, field, value) {
		const input = getAddressInput(form, role, field);
		if (input) {
			input.value = value || "";
		}
	}

	// =========================================================
	// 주문자 정보
	// =========================================================

	function initOrdererButtons(form) {
		const btn = form.querySelector(".admin-task-list-second-orderer-search-btn");
		if (!btn) {
			return;
		}

		btn.addEventListener("click", async function(event) {
			event.preventDefault();

			const companyId = resolveSelectedCompanyId(form);
			if (!companyId) {
				alert("먼저 대리점명을 선택해 주세요.");
				return;
			}

			try {
				await ensureCompanyOptionsLoaded(form, companyId);
			} catch (error) {
				alert(error.message || "주문자 정보를 불러오지 못했습니다.");
				return;
			}

			const items = getOrdererSourceItems(form).filter(function(item) {
				return String(item.companyId || "") === String(companyId || "");
			});

			openPicker({
				title: "주문자 정보 검색",
				desc: "선택한 대리점에 등록된 주문자 정보를 선택합니다.",
				emptyText: "등록된 주문자 정보가 없습니다.",
				items: items,
				keywordFields: ["ordererName", "ordererPhone"],
				renderItem: function(item) {
					return { title: item.ordererName || "-", sub: item.ordererPhone || "-" };
				},
				onSelect: function(item) {
					setValue(form, ".admin-task-list-second-orderer-name", item.ordererName);
					setValue(form, ".admin-task-list-second-orderer-phone", item.ordererPhone);
				}
			});
		});
	}

	// =========================================================
	// 옵션 수정
	// =========================================================

	function initOptionEditor(form) {
		const optionBox = form.querySelector(".admin-task-list-second-option-box");
		if (!optionBox) {
			return;
		}

		optionBox.addEventListener("click", function(event) {
			const editBtn = event.target.closest(".admin-task-list-second-option-edit-btn");
			const deleteBtn = event.target.closest(".admin-task-list-second-option-delete-btn");

			if (editBtn) {
				event.preventDefault();
				toggleOptionEdit(editBtn);
				return;
			}

			if (deleteBtn) {
				event.preventDefault();
				deleteOptionRow(deleteBtn);
			}
		});

		optionBox.addEventListener("dblclick", function(event) {
			const input = event.target.closest(".admin-task-list-second-option-value-input");
			if (!input) {
				return;
			}

			const row = input.closest(".admin-task-list-second-option-edit-item");
			if (!row || row.dataset.optionEditable !== "true") {
				return;
			}

			const editBtn = row.querySelector(".admin-task-list-second-option-edit-btn");
			if (editBtn && input.disabled) {
				toggleOptionEdit(editBtn);
			}
		});
	}

	function toggleOptionEdit(button) {
		const row = button.closest(".admin-task-list-second-option-edit-item");
		const input = row ? row.querySelector(".admin-task-list-second-option-value-input") : null;
		if (!row || !input || row.dataset.optionEditable !== "true") {
			return;
		}

		if (input.disabled) {
			row.dataset.beforeEditValue = input.value;
			input.disabled = false;
			input.focus();
			input.select();
			button.textContent = "완료";
			button.classList.remove("btn-outline-primary");
			button.classList.add("btn-primary");
			return;
		}

		if (!input.value.trim()) {
			alert("옵션 값은 비울 수 없습니다.");
			input.focus();
			return;
		}

		input.value = input.value.trim();
		input.disabled = true;
		button.textContent = "수정";
		button.classList.remove("btn-primary");
		button.classList.add("btn-outline-primary");
	}

	function deleteOptionRow(button) {
		const row = button.closest(".admin-task-list-second-option-edit-item");
		if (!row) {
			return;
		}

		const key = row.dataset.optionKey || "";
		if (OPTION_DELETE_BLOCKED_KEYS.has(key) || row.dataset.optionDeletable !== "true") {
			alert("해당 항목은 삭제할 수 없습니다.");
			return;
		}

		if (!confirm("이 옵션을 삭제하시겠습니까? 저장 버튼을 눌러야 실제 DB에 반영됩니다.")) {
			return;
		}

		const optionBox = row.closest(".admin-task-list-second-option-box");
		row.remove();
		reindexOptionRows(optionBox);
	}

	function buildOptionJsonBeforeSubmit(form) {
		const optionBox = form.querySelector(".admin-task-list-second-option-box");
		const hiddenInput = form.querySelector(".admin-task-list-second-option-json-input");
		if (!optionBox || !hiddenInput) {
			return;
		}

		const optionMap = {};
		const rows = Array.from(optionBox.querySelectorAll(".admin-task-list-second-option-edit-item"));

		rows.forEach(function(row) {
			const key = row.dataset.optionKey;
			const input = row.querySelector(".admin-task-list-second-option-value-input");
			if (!key || !input) {
				return;
			}

			const value = input.value.trim();
			if (!value) {
				throw new Error(key + " 값은 비울 수 없습니다.");
			}
			if (OPTION_VALUE_FIXED_KEYS.has(key) && input.disabled === false) {
				throw new Error(key + " 값은 수정할 수 없습니다.");
			}

			optionMap[key] = value;
		});

		hiddenInput.value = JSON.stringify(reindexOptionObject(optionMap));
	}

	function reindexOptionRows(optionBox) {
		if (!optionBox) {
			return;
		}

		const rows = Array.from(optionBox.querySelectorAll(".admin-task-list-second-option-edit-item"));
		const optionRows = rows.filter(function(row) {
			return /^옵션\d*$/.test(row.dataset.optionKey || "");
		});
		if (optionRows.length === 0) {
			return;
		}

		const hasBase = optionRows.some(function(row) {
			return row.dataset.optionKey === "옵션";
		});

		optionRows.forEach(function(row, index) {
			const newKey = hasBase ? (index === 0 ? "옵션" : "옵션" + (index + 1)) : "옵션" + (index + 1);
			row.dataset.optionKey = newKey;
			const keyEl = row.querySelector(".admin-task-list-second-option-key");
			if (keyEl) {
				keyEl.textContent = newKey;
			}
		});
	}

	function reindexOptionObject(optionMap) {
		const result = {};
		const optionValues = [];
		const hasBase = Object.prototype.hasOwnProperty.call(optionMap, "옵션");

		Object.keys(optionMap).forEach(function(key) {
			if (/^옵션\d*$/.test(key)) {
				optionValues.push(optionMap[key]);
				return;
			}
			result[key] = optionMap[key];
		});

		optionValues.forEach(function(value, index) {
			const key = hasBase ? (index === 0 ? "옵션" : "옵션" + (index + 1)) : "옵션" + (index + 1);
			result[key] = value;
		});

		return result;
	}

	// =========================================================
	// 공통 선택 모달
	// =========================================================

	function initPickerModal() {
		const modal = document.querySelector(SELECTORS.pickerModal);
		if (!modal || modal.dataset.pickerInitialized === "true") {
			return;
		}

		modal.dataset.pickerInitialized = "true";

		modal.addEventListener("click", function(event) {
			if (event.target.classList.contains("admin-task-list-second-picker-overlay") || event.target.closest(".admin-task-list-second-picker-close-btn")) {
				closePicker();
				return;
			}

			const itemBtn = event.target.closest(".admin-task-list-second-picker-item");
			if (itemBtn && activePicker) {
				const index = Number(itemBtn.dataset.index);
				const item = activePicker.filteredItems[index];
				if (item) {
					activePicker.onSelect(item);
					closePicker();
				}
			}
		});

		const keywordInput = document.querySelector(SELECTORS.pickerKeyword);
		if (keywordInput) {
			keywordInput.addEventListener("input", renderPickerItems);
		}

		document.addEventListener("keydown", function(event) {
			if (event.key === "Escape" && modal.classList.contains("is-open")) {
				closePicker();
			}
		});
	}

	function openPicker(config) {
		const modal = document.querySelector(SELECTORS.pickerModal);
		const title = document.querySelector(SELECTORS.pickerTitle);
		const desc = document.querySelector(SELECTORS.pickerDesc);
		const keyword = document.querySelector(SELECTORS.pickerKeyword);
		if (!modal || !title || !desc || !keyword) {
			return;
		}

		activePicker = Object.assign({}, config, { filteredItems: [] });
		title.textContent = config.title || "검색";
		desc.textContent = config.desc || "등록된 항목 중 하나를 선택합니다.";
		keyword.value = "";
		modal.classList.add("is-open");
		modal.setAttribute("aria-hidden", "false");
		renderPickerItems();
		setTimeout(function() { keyword.focus(); }, 0);
	}

	function closePicker() {
		const modal = document.querySelector(SELECTORS.pickerModal);
		if (modal) {
			modal.classList.remove("is-open");
			modal.setAttribute("aria-hidden", "true");
		}
		activePicker = null;
	}

	function renderPickerItems() {
		if (!activePicker) {
			return;
		}

		const list = document.querySelector(SELECTORS.pickerList);
		const keyword = (document.querySelector(SELECTORS.pickerKeyword)?.value || "").trim().toLowerCase();
		if (!list) {
			return;
		}

		const keywordFields = activePicker.keywordFields || [];
		activePicker.filteredItems = (activePicker.items || []).filter(function(item) {
			if (!keyword) {
				return true;
			}
			return keywordFields.some(function(field) {
				return String(item[field] || "").toLowerCase().includes(keyword);
			});
		});

		list.innerHTML = "";
		if (activePicker.filteredItems.length === 0) {
			const empty = document.createElement("div");
			empty.className = "admin-task-list-second-picker-empty";
			empty.textContent = activePicker.emptyText || "조회된 항목이 없습니다.";
			list.appendChild(empty);
			return;
		}

		activePicker.filteredItems.forEach(function(item, index) {
			const view = activePicker.renderItem(item);
			const btn = document.createElement("button");
			btn.type = "button";
			btn.className = "admin-task-list-second-picker-item";
			btn.dataset.index = String(index);

			const title = document.createElement("strong");
			title.textContent = view.title || "-";
			const sub = document.createElement("span");
			sub.textContent = view.sub || "";

			btn.appendChild(title);
			btn.appendChild(sub);
			list.appendChild(btn);
		});
	}

	// =========================================================
	// 대리점 변경 AJAX 데이터 소스
	// =========================================================

	async function loadCompanyOptionsIntoForm(form, companyId, requestKey) {
		if (!companyId) {
			clearCompanyDependentSources(form);
			renderRequesterSelectFromSource(form, "");
			return;
		}

		const cacheKey = String(companyId);
		let data = companyOptionsCache.get(cacheKey);

		if (!data) {
			data = await fetchCompanyOptions(companyId);
			companyOptionsCache.set(cacheKey, data);
		}

		if (requestKey && form.dataset.companyOptionsRequestKey !== requestKey) {
			return;
		}

		applyCompanyOptionsToForm(form, data);
	}

	async function ensureCompanyOptionsLoaded(form, companyId) {
		if (!companyId) {
			return;
		}

		const cacheKey = String(companyId);

		const hasCompanySource = getCompanySourceItem(form, companyId) != null;
		const hasMemberSource = getMemberSourceItems(form).some(function(item) {
			return String(item.companyId || "") === cacheKey;
		});
		const hasCached = companyOptionsCache.has(cacheKey);

		if (hasCompanySource && hasMemberSource) {
			return;
		}

		if (hasCached) {
			applyCompanyOptionsToForm(form, companyOptionsCache.get(cacheKey));
			return;
		}

		await loadCompanyOptionsIntoForm(form, companyId, null);
	}

	async function fetchCompanyOptions(companyId) {
		const cacheKey = String(companyId);

		if (companyOptionsLoading.has(cacheKey)) {
			return companyOptionsLoading.get(cacheKey);
		}

		const promise = fetch("/management/nonStandardTaskList/company-options/" + encodeURIComponent(companyId), {
			method: "GET",
			headers: {
				"X-Requested-With": "XMLHttpRequest"
			}
		})
			.then(async function(response) {
				let data = null;

				try {
					data = await response.json();
				} catch (e) {
					data = null;
				}

				if (!response.ok || !data || data.success !== true) {
					throw new Error("대리점 상세 선택 정보를 불러오지 못했습니다.");
				}

				return data;
			})
			.finally(function() {
				companyOptionsLoading.delete(cacheKey);
			});

		companyOptionsLoading.set(cacheKey, promise);
		return promise;
	}

	function applyCompanyOptionsToForm(form, data) {
		if (!form || !data) {
			return;
		}

		upsertCompanySource(form, data.company);
		replaceMemberSources(form, data.members || []);
		replaceDeliveryAddressSources(form, data.deliveryAddresses || []);
		replaceOrdererSources(form, data.orderers || []);

		const requesterSelect = form.querySelector(SELECTORS.requesterSelect);
		const selectedMemberId = requesterSelect?.dataset.selectedMemberId || requesterSelect?.value || "";
		renderRequesterSelectFromSource(form, selectedMemberId);
	}

	function setCompanySelectBusy(companySelect, busy) {
		if (!companySelect) {
			return;
		}

		companySelect.dataset.loading = busy ? "true" : "false";
		companySelect.classList.toggle("bg-light", busy);
		companySelect.setAttribute("aria-busy", busy ? "true" : "false");
	}

	function clearCompanyDependentSources(form) {
		replaceMemberSources(form, []);
		replaceDeliveryAddressSources(form, []);
		replaceOrdererSources(form, []);
	}

	function getDetailPanelRoot(form) {
		return form?.closest(".admin-task-list-second-detail-panel") || form || document;
	}

	function ensureSourceContainer(form, selectorClassName) {
		const root = getDetailPanelRoot(form);
		let container = root.querySelector("." + selectorClassName);

		if (!container) {
			const sources = root.querySelector(".admin-task-list-second-detail-sources");
			if (!sources) {
				return null;
			}

			container = document.createElement("div");
			container.className = selectorClassName;
			sources.appendChild(container);
		}

		return container;
	}

	function upsertCompanySource(form, company) {
		if (!company || !company.companyId) {
			return;
		}

		const container = ensureSourceContainer(form, "admin-task-list-second-company-source");
		if (!container) {
			return;
		}

		let button = Array.from(container.querySelectorAll("[data-company-id]"))
			.find(function(item) {
				return String(item.dataset.companyId || "") === String(company.companyId || "");
			});

		if (!button) {
			button = document.createElement("button");
			button.type = "button";
			container.appendChild(button);
		}

		button.dataset.companyId = company.companyId || "";
		button.dataset.companyName = company.companyName || "";
		button.dataset.representativeName = company.representativeName || "";
		button.dataset.zipCode = company.zipCode || "";
		button.dataset.doName = company.doName || "";
		button.dataset.siName = company.siName || "";
		button.dataset.guName = company.guName || "";
		button.dataset.roadAddress = company.roadAddress || "";
		button.dataset.detailAddress = company.detailAddress || "";
	}

	function replaceMemberSources(form, members) {
		const container = ensureSourceContainer(form, "admin-task-list-second-member-source");
		if (!container) {
			return;
		}

		container.innerHTML = "";

		(members || []).forEach(function(member) {
			const button = document.createElement("button");
			button.type = "button";
			button.dataset.companyId = member.companyId || "";
			button.dataset.memberId = member.memberId || "";
			button.dataset.memberName = member.memberName || "";
			container.appendChild(button);
		});
	}

	function replaceDeliveryAddressSources(form, addresses) {
		const container = ensureSourceContainer(form, "admin-task-list-second-delivery-address-source");
		if (!container) {
			return;
		}

		container.innerHTML = "";

		(addresses || []).forEach(function(address) {
			const button = document.createElement("button");
			button.type = "button";
			button.dataset.companyId = address.companyId || "";
			button.dataset.addressId = address.addressId || "";
			button.dataset.zipCode = address.zipCode || "";
			button.dataset.doName = address.doName || "";
			button.dataset.siName = address.siName || "";
			button.dataset.guName = address.guName || "";
			button.dataset.roadAddress = address.roadAddress || "";
			button.dataset.detailAddress = address.detailAddress || "";
			button.dataset.fullAddress = address.fullAddress || "";
			container.appendChild(button);
		});
	}

	function replaceOrdererSources(form, orderers) {
		const container = ensureSourceContainer(form, "admin-task-list-second-orderer-source");
		if (!container) {
			return;
		}

		container.innerHTML = "";

		(orderers || []).forEach(function(orderer) {
			const button = document.createElement("button");
			button.type = "button";
			button.dataset.companyId = orderer.companyId || "";
			button.dataset.ordererInfoId = orderer.ordererInfoId || "";
			button.dataset.ordererName = orderer.ordererName || "";
			button.dataset.ordererPhone = orderer.ordererPhone || "";
			container.appendChild(button);
		});
	}

	function renderRequesterSelectFromSource(form, selectedMemberId) {
		const requesterSelect = form.querySelector(SELECTORS.requesterSelect);
		const companyId = resolveSelectedCompanyId(form);

		if (!requesterSelect) {
			return;
		}

		requesterSelect.innerHTML = "";

		const defaultOption = document.createElement("option");
		defaultOption.value = "";
		defaultOption.textContent = "신청자 선택";
		requesterSelect.appendChild(defaultOption);

		if (!companyId) {
			requesterSelect.value = "";
			return;
		}

		getMemberSourceItems(form)
			.filter(function(member) {
				return String(member.companyId || "") === String(companyId || "");
			})
			.forEach(function(member) {
				const option = document.createElement("option");
				option.value = member.memberId || "";
				option.textContent = member.memberName || "이름없음";
				option.dataset.companyId = member.companyId || "";

				if (String(member.memberId || "") === String(selectedMemberId || "")) {
					option.selected = true;
				}

				requesterSelect.appendChild(option);
			});

		if (selectedMemberId && requesterSelect.value !== String(selectedMemberId)) {
			requesterSelect.value = "";
		}
	}

	function getMemberSourceItems(form) {
		const root = getDetailPanelRoot(form);

		return Array.from(root.querySelectorAll(".admin-task-list-second-member-source [data-member-id]"))
			.map(function(el) {
				return {
					companyId: el.dataset.companyId || "",
					memberId: el.dataset.memberId || "",
					memberName: el.dataset.memberName || ""
				};
			});
	}

	function resolveSelectedCompanyId(form) {
		const companySelect = form.querySelector(SELECTORS.companySelect);
		if (!companySelect) {
			return "";
		}
		return companySelect.value || companySelect.dataset.selectedCompanyId || "";
	}

	function getAddressSourceItems(form) {
		const root = getDetailPanelRoot(form);

		return Array.from(root.querySelectorAll(SELECTORS.addressSource)).map(function(el) {
			return {
				companyId: el.dataset.companyId || "",
				addressId: el.dataset.addressId || "",
				zipCode: el.dataset.zipCode || "",
				doName: el.dataset.doName || "",
				siName: el.dataset.siName || "",
				guName: el.dataset.guName || "",
				roadAddress: el.dataset.roadAddress || "",
				detailAddress: el.dataset.detailAddress || "",
				fullAddress: el.dataset.fullAddress || ""
			};
		});
	}

	function getOrdererSourceItems(form) {
		const root = getDetailPanelRoot(form);

		return Array.from(root.querySelectorAll(SELECTORS.ordererSource)).map(function(el) {
			return {
				companyId: el.dataset.companyId || "",
				ordererInfoId: el.dataset.ordererInfoId || "",
				ordererName: el.dataset.ordererName || "",
				ordererPhone: el.dataset.ordererPhone || ""
			};
		});
	}

	// =========================================================
	// 유틸
	// =========================================================

	function getValue(form, selector) {
		const input = form.querySelector(selector);
		return input ? input.value.trim() : "";
	}

	function setValue(form, selector, value) {
		const input = form.querySelector(selector);
		if (input) {
			input.value = value || "";
		}
	}

	function parseInteger(value) {
		const normalized = String(value || "").replace(/[^\d-]/g, "");
		const parsed = Number.parseInt(normalized, 10);
		return Number.isNaN(parsed) ? 0 : parsed;
	}
})();