(function() {
	"use strict";

	const SELECTORS = {
		form: ".admin-task-list-second-update-form",
		companySelect: ".admin-task-list-second-company-select",
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
		initDeliveryMethodRule(form);
		initAddressButtons(form);
		initOrdererButtons(form);
		initOptionEditor(form);

		/*
		 * capture=true:
		 * taskList.js 쪽의 공통 submit loading보다 먼저 검증하기 위함입니다.
		 * 검증 실패 시 overlay가 켜진 채 멈추는 문제를 방지합니다.
		 */
		form.addEventListener("submit", function(event) {
			try {
				validateDeliveryMethodBeforeSubmit(form);
				normalizeMoneyBeforeSubmit(form);
				buildOptionJsonBeforeSubmit(form);
			} catch (error) {
				event.preventDefault();
				event.stopPropagation();
				alert(error.message || "입력값을 확인해 주세요.");
			}
		}, true);
	}

	// =========================================================
	// 배송수단 / 배송담당자 연동
	// =========================================================

	function initDeliveryMethodRule(form) {
		const methodSelect = form.querySelector(SELECTORS.deliveryMethodSelect);
		const handlerSelect = form.querySelector(SELECTORS.deliveryHandlerSelect);

		if (!methodSelect || !handlerSelect) {
			return;
		}

		syncDeliveryHandlerState(form);

		methodSelect.addEventListener("change", function() {
			syncDeliveryHandlerState(form);
		});

		const statusSelect = form.querySelector(SELECTORS.statusSelect);

		if (statusSelect) {
			statusSelect.addEventListener("change", function() {
				syncDeliveryHandlerState(form);
			});
		}
	}

	function syncDeliveryHandlerState(form) {
		const methodSelect = form.querySelector(SELECTORS.deliveryMethodSelect);
		const handlerSelect = form.querySelector(SELECTORS.deliveryHandlerSelect);
		const help = form.querySelector(".admin-task-list-second-delivery-handler-help");

		if (!methodSelect || !handlerSelect) {
			return;
		}

		const directDelivery = isDirectDeliverySelected(methodSelect);
		const canceled = isCanceledStatus(form);

		if (directDelivery && !canceled) {
			handlerSelect.disabled = false;
			handlerSelect.required = true;
			handlerSelect.classList.remove("bg-light");

			if (help) {
				help.textContent = "직배송 선택 시 배송팀 담당자는 필수입니다.";
			}

			return;
		}

		handlerSelect.value = "";
		handlerSelect.disabled = true;
		handlerSelect.required = false;
		handlerSelect.classList.add("bg-light");

		if (help) {
			help.textContent = canceled
				? "취소 상태에서는 배송순서 인덱스가 저장되지 않습니다."
				: "직배송이 아닌 경우 배송팀 담당자는 저장되지 않습니다.";
		}
	}

	function validateDeliveryMethodBeforeSubmit(form) {
		const methodSelect = form.querySelector(SELECTORS.deliveryMethodSelect);
		const handlerSelect = form.querySelector(SELECTORS.deliveryHandlerSelect);
		const preferredDeliveryDateInput = form.querySelector(SELECTORS.preferredDeliveryDateInput);

		if (!methodSelect || !handlerSelect) {
			return;
		}

		const directDelivery = isDirectDeliverySelected(methodSelect);
		const canceled = isCanceledStatus(form);

		if (canceled) {
			handlerSelect.value = "";
			handlerSelect.disabled = true;
			return;
		}

		if (!directDelivery) {
			handlerSelect.value = "";
			handlerSelect.disabled = true;
			return;
		}

		handlerSelect.disabled = false;
		handlerSelect.required = true;

		if (!preferredDeliveryDateInput || !preferredDeliveryDateInput.value) {
			throw new Error("직배송 선택 시 배송희망일은 필수입니다.");
		}

		if (!handlerSelect.value) {
			throw new Error("직배송 선택 시 배송팀 담당자는 필수입니다.");
		}
	}

	function isDirectDeliverySelected(methodSelect) {
		if (!methodSelect) {
			return false;
		}

		const selectedOption = methodSelect.options[methodSelect.selectedIndex];

		if (!selectedOption || !selectedOption.value) {
			return false;
		}

		const dataName = selectedOption.dataset.deliveryMethodName || "";
		const textName = selectedOption.textContent || "";

		const methodName = String(dataName || textName)
			.replace(/\s*\(금액:.*?\)\s*$/g, "")
			.trim();

		return methodName === "직배송";
	}

	function isCanceledStatus(form) {
		const statusSelect = form.querySelector(SELECTORS.statusSelect);
		return statusSelect && statusSelect.value === "CANCELED";
	}

	// =========================================================
	// 금액 계산
	// =========================================================

	function initMoneyBox(form) {
		const inputs = Array.from(
			form.querySelectorAll(".admin-task-list-second-money-input[data-money-role]")
		);

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
			const initialAutoSupply = initialUnit > 0 && initialQuantity > 0
				? initialUnit * initialQuantity
				: 0;

			/*
			 * 공급가가 단가×수량과 다르면 사용자가 의도적으로 공급가를 별도 지정한 것으로 봅니다.
			 * 예: 단가 50,000 / 수량 3 / 공급가 0
			 * 이 경우 공급가 0이 우선이며, 부가세/총비용도 0이어야 합니다.
			 */
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

		["unit", "quantity", "supply", "total"].forEach(function(role) {
			const input = getMoneyInput(form, role);

			if (!input) {
				return;
			}

			const value = parseInteger(input.value);

			if (value < 0) {
				const label = input
					.closest(".col-xl-2, .col-md-4, .col-sm-6")
					?.querySelector("label")
					?.textContent
					?.trim();

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

		/*
		 * 핵심 기준:
		 * 1. 부가세와 총비용은 무조건 공급가 기준입니다.
		 * 2. 공급가가 0이면 부가세도 0, 총비용도 0입니다.
		 * 3. 공급가를 사용자가 직접 수정한 경우 단가/수량이 공급가를 덮어쓰지 않습니다.
		 */
		if ((changedRole === "unit" || changedRole === "quantity") && !supplyManual) {
			if (unit > 0 && quantity > 0) {
				supply = unit * quantity;
			} else {
				supply = 0;
			}
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
		return form.querySelector(
			'.admin-task-list-second-money-input[data-money-role="' + role + '"]'
		);
	}

	// =========================================================
	// 배송주소
	// =========================================================

	function initAddressButtons(form) {
		const addressSearchBtn = form.querySelector(".admin-task-list-second-address-search-btn");
		const addressBookBtn = form.querySelector(".admin-task-list-second-address-book-btn");

		if (addressSearchBtn) {
			addressSearchBtn.addEventListener("click", function(event) {
				event.preventDefault();
				openDaumPostcode(form);
			});
		}

		if (addressBookBtn) {
			addressBookBtn.addEventListener("click", function(event) {
				event.preventDefault();

				const companyId = resolveSelectedCompanyId(form);

				if (!companyId) {
					alert("먼저 대리점명을 선택해 주세요.");
					return;
				}

				const items = getAddressSourceItems(form).filter(function(item) {
					return item.companyId === companyId;
				});

				openPicker({
					title: "등록 주소지 검색",
					desc: "선택한 대리점에 등록된 배송지를 선택합니다.",
					emptyText: "등록된 배송지가 없습니다.",
					items: items,
					keywordFields: ["fullAddress", "roadAddress", "detailAddress"],
					renderItem: function(item) {
						const title = item.fullAddress || item.roadAddress || "-";
						const sub = [
							item.zipCode,
							item.doName,
							item.siName,
							item.guName
						].filter(Boolean).join(" ");

						return {
							title: title,
							sub: sub
						};
					},
					onSelect: function(item) {
						applyAddress(form, item);
					}
				});
			});
		}

		syncAddressPreview(form);

		const detailInput = form.querySelector(".admin-task-list-second-detail-address");
		if (detailInput) {
			detailInput.addEventListener("input", function() {
				syncAddressPreview(form);
			});
		}
	}

	function openDaumPostcode(form) {
		if (!window.daum || !window.daum.Postcode) {
			alert("Daum 우편번호 스크립트를 불러오지 못했습니다. 네트워크 또는 스크립트 경로를 확인해 주세요.");
			return;
		}

		new window.daum.Postcode({
			oncomplete: function(data) {
				const sigungu = data.sigungu || "";
				const parsed = splitSigungu(sigungu);

				applyAddress(form, {
					zipCode: data.zonecode || "",
					doName: data.sido || "",
					siName: parsed.siName,
					guName: parsed.guName,
					roadAddress: data.roadAddress || data.address || "",
					detailAddress: ""
				});

				const detailInput = form.querySelector(".admin-task-list-second-detail-address");

				if (detailInput) {
					detailInput.focus();
				}
			}
		}).open();
	}

	function splitSigungu(sigungu) {
		const parts = String(sigungu || "")
			.trim()
			.split(/\s+/)
			.filter(Boolean);

		if (parts.length === 0) {
			return {
				siName: "",
				guName: ""
			};
		}

		return {
			siName: parts[0] || "",
			guName: parts.slice(1).join(" ")
		};
	}

	function applyAddress(form, item) {
		setValue(form, ".admin-task-list-second-zip-code", item.zipCode);
		setValue(form, ".admin-task-list-second-do-name", item.doName);
		setValue(form, ".admin-task-list-second-si-name", item.siName);
		setValue(form, ".admin-task-list-second-gu-name", item.guName);
		setValue(form, ".admin-task-list-second-road-address", item.roadAddress);
		setValue(form, ".admin-task-list-second-detail-address", item.detailAddress);

		syncAddressPreview(form);
	}

	function syncAddressPreview(form) {
		const preview = form.querySelector(".admin-task-list-second-address-preview");

		if (!preview) {
			return;
		}

		const zipCode = getValue(form, ".admin-task-list-second-zip-code");
		const doName = getValue(form, ".admin-task-list-second-do-name");
		const siName = getValue(form, ".admin-task-list-second-si-name");
		const guName = getValue(form, ".admin-task-list-second-gu-name");
		const roadAddress = getValue(form, ".admin-task-list-second-road-address");
		const detailAddress = getValue(form, ".admin-task-list-second-detail-address");

		const text = [
			zipCode ? "(" + zipCode + ")" : "",
			doName,
			siName,
			guName,
			roadAddress,
			detailAddress
		].filter(Boolean).join(" ");

		preview.textContent = text || "-";
	}

	// =========================================================
	// 주문자 정보
	// =========================================================

	function initOrdererButtons(form) {
		const btn = form.querySelector(".admin-task-list-second-orderer-search-btn");

		if (!btn) {
			return;
		}

		btn.addEventListener("click", function(event) {
			event.preventDefault();

			const companyId = resolveSelectedCompanyId(form);

			if (!companyId) {
				alert("먼저 대리점명을 선택해 주세요.");
				return;
			}

			const items = getOrdererSourceItems(form).filter(function(item) {
				return item.companyId === companyId;
			});

			openPicker({
				title: "주문자 정보 검색",
				desc: "선택한 대리점에 등록된 주문자 정보를 선택합니다.",
				emptyText: "등록된 주문자 정보가 없습니다.",
				items: items,
				keywordFields: ["ordererName", "ordererPhone"],
				renderItem: function(item) {
					return {
						title: item.ordererName || "-",
						sub: item.ordererPhone || "-"
					};
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

		if (!row || !input) {
			return;
		}

		if (row.dataset.optionEditable !== "true") {
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
		const rows = Array.from(
			optionBox.querySelectorAll(".admin-task-list-second-option-edit-item")
		);

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

		const rows = Array.from(
			optionBox.querySelectorAll(".admin-task-list-second-option-edit-item")
		);

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
			const newKey = hasBase
				? (index === 0 ? "옵션" : "옵션" + (index + 1))
				: "옵션" + (index + 1);

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
			const key = hasBase
				? (index === 0 ? "옵션" : "옵션" + (index + 1))
				: "옵션" + (index + 1);

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
			if (
				event.target.classList.contains("admin-task-list-second-picker-overlay") ||
				event.target.closest(".admin-task-list-second-picker-close-btn")
			) {
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

		activePicker = Object.assign({}, config, {
			filteredItems: []
		});

		title.textContent = config.title || "검색";
		desc.textContent = config.desc || "등록된 항목 중 하나를 선택합니다.";
		keyword.value = "";

		modal.classList.add("is-open");
		modal.setAttribute("aria-hidden", "false");

		renderPickerItems();

		setTimeout(function() {
			keyword.focus();
		}, 0);
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
		const keyword = (
			document.querySelector(SELECTORS.pickerKeyword)?.value || ""
		).trim().toLowerCase();

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
	// 데이터 소스
	// =========================================================

	function resolveSelectedCompanyId(form) {
		const companySelect = form.querySelector(SELECTORS.companySelect);

		if (!companySelect) {
			return "";
		}

		return companySelect.value || companySelect.dataset.selectedCompanyId || "";
	}

	function getAddressSourceItems(form) {
		const root = form?.closest(".admin-task-list-second-detail-panel") || document;
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
		const root = form?.closest(".admin-task-list-second-detail-panel") || document;
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

		if (Number.isNaN(parsed)) {
			return 0;
		}

		return parsed;
	}
})();