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
	const REQUIRED_HANDLER_METHOD_NAMES = new Set(["직배송", "현장배송"]);
	const PROHIBITED_HANDLER_METHOD_NAMES = new Set(["화물", "방문", "택배", "미배송"]);
	const DAUM_POSTCODE_SCRIPT_ID = "admin-task-list-second-daum-postcode-script";
	const DAUM_POSTCODE_SCRIPT_URL = "https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";

	const PROVINCE_ALIASES = new Map([
		["서울", "서울특별시"], ["서울시", "서울특별시"], ["서울특별시", "서울특별시"],
		["부산", "부산광역시"], ["부산시", "부산광역시"], ["부산광역시", "부산광역시"],
		["대구", "대구광역시"], ["대구시", "대구광역시"], ["대구광역시", "대구광역시"],
		["인천", "인천광역시"], ["인천시", "인천광역시"], ["인천광역시", "인천광역시"],
		["광주", "광주광역시"], ["광주시", "광주광역시"], ["광주광역시", "광주광역시"],
		["전남광주통합특별시", "광주광역시"], ["광주전남통합특별시", "광주광역시"],
		["대전", "대전광역시"], ["대전시", "대전광역시"], ["대전광역시", "대전광역시"],
		["울산", "울산광역시"], ["울산시", "울산광역시"], ["울산광역시", "울산광역시"],
		["세종", "세종특별자치시"], ["세종시", "세종특별자치시"], ["세종특별자치시", "세종특별자치시"],
		["경기", "경기도"], ["경기도", "경기도"],
		["강원", "강원특별자치도"], ["강원도", "강원특별자치도"], ["강원특별자치도", "강원특별자치도"],
		["충북", "충청북도"], ["충청북도", "충청북도"],
		["충남", "충청남도"], ["충청남도", "충청남도"],
		["전북", "전북특별자치도"], ["전라북도", "전북특별자치도"], ["전북특별자치도", "전북특별자치도"],
		["전남", "전라남도"], ["전라남도", "전라남도"],
		["경북", "경상북도"], ["경상북도", "경상북도"],
		["경남", "경상남도"], ["경상남도", "경상남도"],
		["제주", "제주특별자치도"], ["제주도", "제주특별자치도"], ["제주특별자치도", "제주특별자치도"]
	]);

	const OPTION_VALUE_FIXED_KEYS = new Set([
		"카테고리",
		"제품시리즈",
		"제품시리즈ID"
	]);

	/*
	 * 신규 등록 방식에서는 규격제품이어도 DB 제품시리즈와 연결되지 않은 상태로
	 * 제품시리즈ID가 없거나 빈 값일 수 있습니다.
	 * 이 값은 수정 가능한 일반 옵션이 아니라 선택적 연동 메타데이터이므로,
	 * 비어 있을 때 저장을 차단하지 않고 optionJson에서 제외합니다.
	 */
	const OPTION_OPTIONAL_FIXED_KEYS = new Set([
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
	let daumPostcodeLoadingPromise = null;

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
		if (!companySelect) return;

		const requester = form.querySelector(SELECTORS.requesterSelect);
		if (requester) {
			renderRequesterSelectFromSource(form, requester.dataset.selectedMemberId || requester.value);
			requester.addEventListener("change", () => { requester.dataset.selectedMemberId = requester.value; });
		}

		const editor = form.querySelector("[data-company-editor]");
		if (!editor) return;
		const control = name => editor.querySelector("[data-company-" + name + "]");
		const caption = control("caption"), action = control("edit"), input = control("query");
		const picker = control("picker"), list = control("results"), all = control("all");
		const controls = control("controls"), cancel = control("cancel"), revert = control("revert");
		const status = control("status"), scope = form.elements.namedItem("companyScope");
		const originalId = form.elements.namedItem("originalCompanyId").value;
		const originalCaption = caption.textContent.trim() || "대리점 미지정";
		const companies = Array.from(companySelect.options).filter(option => option.value);
		const normalized = value => value.trim().toLocaleLowerCase().replace(/\s+/g, "");
		let originalSources, originalRequester;
		let editing = false, busy = false, candidate = null, requestVersion = 0;
		let activeIndex = -1;

		function message(text) {
			status.textContent = text;
			status.hidden = !text;
		}

		function render() {
			const changed = companySelect.value !== originalId;
			editor.classList.toggle("is-editing", editing);
			editor.classList.toggle("is-modified", changed);
			caption.hidden = editing;
			caption.textContent = companySelect.selectedOptions[0]?.value
				? companySelect.selectedOptions[0].text.trim() : originalCaption;
			picker.hidden = !editing;
			controls.hidden = !editing && !changed;
			cancel.hidden = !editing;
			revert.hidden = !changed || editing;
			input.readOnly = busy;
			all.disabled = busy;
			action.textContent = editing ? "적용" : "변경";
			action.disabled = editing && (busy || !candidate);
			if (busy) message("업체 정보를 불러오는 중입니다.");
			else if (changed && !editing) message((scope.value === "TASK" ? "저장 시 같은 발주 전체를 변경합니다." : "저장 시 이 오더만 새 발주로 이동합니다.") +
                " 이동 금액의 1%를 기존 업체에서 차감하고 변경 업체에 적립합니다. 회수 금액은 반대로 반영됩니다.");
			else message("");
		}

		function closeResults() {
			list.hidden = true;
			input.setAttribute("aria-expanded", "false");
			input.removeAttribute("aria-activedescendant");
			activeIndex = -1;
		}

		function closeEditor(focus = true) {
			requestVersion++;
			editing = false;
			busy = false;
			candidate = null;
			input.value = "";
			all.checked = scope.value === "TASK";
			closeResults();
			render();
			if (focus) action.focus();
		}

		function restoreOriginal() {
			companySelect.value = originalId;
			scope.value = "";
			if (originalSources) {
				if (requester) {
					requester.value = "";
					requester.dataset.selectedMemberId = originalRequester;
				}
				applyCompanyOptionsToForm(form, originalSources);
			}
			closeEditor();
		}

		function showResults() {
			closeResults();
			list.replaceChildren();
			const query = normalized(input.value);
			if (!query || !editing || busy) return;
			const items = document.createDocumentFragment();
			for (const option of companies) {
				if (!normalized(option.text).includes(query)) continue;
				const item = document.createElement("div");
				item.id = list.id + "-" + option.value;
				item.dataset.value = option.value;
				item.setAttribute("role", "option");
				item.setAttribute("aria-selected", "false");
				item.textContent = option.text.trim();
				items.append(item);
			}
			list.append(items);
			if (!list.childElementCount) {
				message("검색 결과가 없습니다.");
				return;
			}
			list.hidden = false;
			input.setAttribute("aria-expanded", "true");
		}

		function highlight(index) {
			const options = list.children;
			if (!options.length || list.hidden) return;
			if (activeIndex >= 0) options[activeIndex].setAttribute("aria-selected", "false");
			activeIndex = Math.max(0, Math.min(index, options.length - 1));
			const option = options[activeIndex];
			option.setAttribute("aria-selected", "true");
			input.setAttribute("aria-activedescendant", option.id);
			option.scrollIntoView({block: "nearest"});
		}

		function choose(item) {
			if (!item) return;
			candidate = item.dataset.value;
			input.value = item.textContent;
			closeResults();
			render();
			action.focus();
		}

		async function applyCandidate() {
			if (!candidate || busy) return;
			const id = candidate;
			if (id === originalId) {
				if (companySelect.value !== originalId) restoreOriginal();
				else closeEditor();
				return;
			}
			const version = ++requestVersion;
			if (id !== companySelect.value) {
				busy = true;
				render();
				try {
					const data = await getCompanyOptions(id);
					if (version !== requestVersion || !form.isConnected) return;
					if (companySelect.value === originalId) {
						// Capture the live draft, including the member selected before switching company.
						originalRequester = requester?.value || "";
						originalSources = {members: getMemberSourceItems(form), deliveryAddresses: getAddressSourceItems(form),
							orderers: getOrdererSourceItems(form)};
					}
					companySelect.value = id;
					if (requester) {
						requester.dataset.selectedMemberId = "";
						requester.value = "";
					}
					applyCompanyOptionsToForm(form, data);
				} catch (error) {
					if (version !== requestVersion || !form.isConnected) return;
					busy = false;
					render();
					message("업체 정보를 불러오지 못했습니다. 적용을 눌러 다시 시도해 주세요.");
					action.focus();
					return;
				}
			}
			scope.value = all.checked ? "TASK" : "ORDER";
			closeEditor();
		}

		action.addEventListener("click", () => {
			if (editing) { applyCandidate(); return; }
			editing = true;
			input.value = "";
			// Each new company choice starts with the current order. The applied scope is restored on cancel.
			all.checked = false;
			render();
			input.focus();
		});
		cancel.addEventListener("click", () => closeEditor());
		revert.addEventListener("click", restoreOriginal);
		all.addEventListener("change", () => {
			if (!editing && companySelect.value !== originalId) {
				scope.value = all.checked ? "TASK" : "ORDER";
				render();
			}
		});
		input.addEventListener("input", () => {
			candidate = null;
			render();
			showResults();
		});
		input.addEventListener("click", () => {
			if (!candidate && list.hidden) showResults();
		});
		list.addEventListener("mousedown", event => event.preventDefault());
		list.addEventListener("click", event => choose(event.target.closest('[role="option"]')));
		picker.addEventListener("focusout", event => {
			if (!picker.contains(event.relatedTarget)) closeResults();
		});
		editor.addEventListener("keydown", event => {
			if (!editing || event.isComposing || event.keyCode === 229) return;
			if (event.key === "Escape") {
				event.preventDefault();
				event.stopPropagation();
				closeEditor();
			}
			if (event.target !== input) return;
			if (event.key === "ArrowDown" || event.key === "ArrowUp") {
				event.preventDefault();
				if (list.hidden) showResults();
				highlight(activeIndex + (event.key === "ArrowDown" ? 1 : -1));
			} else if (event.key === "Enter") {
				event.preventDefault();
				if (!list.hidden) choose(list.children[Math.max(activeIndex, 0)]);
				else if (candidate) applyCandidate();
			}
		});
		form.addEventListener("submit", event => {
			if (!editing) return;
			if (!busy && !candidate && !input.value.trim() && !all.checked) {
				closeEditor(false);
				return;
			}
			event.preventDefault();
			event.stopImmediatePropagation();
			message(busy ? "업체 정보를 불러오는 중입니다." : "업체 변경을 적용하거나 취소한 뒤 저장해 주세요.");
			(busy ? cancel : candidate ? action : input).focus();
		}, true);
		render();
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

		syncDeliveryMethodDependentState(form, false, false);

		methodSelect.addEventListener("change", function() {
			syncDeliveryMethodDependentState(form, true, false);
		});

		const statusSelect = form.querySelector(SELECTORS.statusSelect);
		if (statusSelect) {
			statusSelect.addEventListener("change", function() {
				syncDeliveryMethodDependentState(form, false, true);
			});
		}
	}

	function syncDeliveryMethodDependentState(form, changedByUser, statusChangedByUser) {
		syncDeliveryHandlerState(form, statusChangedByUser);
		syncAddressSections(form, changedByUser);
	}

	function syncDeliveryHandlerState(form, statusChangedByUser) {
		const methodSelect = form.querySelector(SELECTORS.deliveryMethodSelect);
		const handlerSelect = form.querySelector(SELECTORS.deliveryHandlerSelect);
		const help = form.querySelector(".admin-task-list-second-delivery-handler-help");

		if (!methodSelect || !handlerSelect) {
			return;
		}

		const methodName = resolveSelectedDeliveryMethodName(methodSelect);
		const required = isDeliveryHandlerRequiredMethodName(methodName);
		const prohibited = isDeliveryHandlerProhibitedMethodName(methodName);
		const assignmentBlocked = isDeliveryAssignmentBlockedStatus(form);

		if (assignmentBlocked) {
			/*
			 * 최초 로딩부터 고객 발주/취소인 과거 데이터에 잘못 남아 있는 담당자는
			 * 복원 후보로 보관하지 않습니다. 사용자가 활성 상태에서 고객 발주/취소로
			 * 직접 바꾼 경우에만 같은 수정 화면 안에서 원래 선택값을 복원합니다.
			 */
			if (statusChangedByUser && handlerSelect.value) {
				handlerSelect.dataset.lastAllowedHandlerId = handlerSelect.value;
			}
			handlerSelect.value = "";
			handlerSelect.disabled = true;
			handlerSelect.required = false;
			handlerSelect.classList.add("bg-light");
			if (help) {
				help.textContent = "고객 발주 또는 취소 상태에서는 배송담당자와 배송순서가 저장되지 않습니다.";
			}
			return;
		}

		if (prohibited) {
			handlerSelect.value = "";
			handlerSelect.disabled = true;
			handlerSelect.required = false;
			handlerSelect.classList.add("bg-light");
			if (help) {
				help.textContent = "배송수단 미지정·화물·방문·택배·미배송은 배송팀 담당자를 지정하지 않으며, 저장 시 기존 담당자와 배송순번도 삭제됩니다.";
			}
			return;
		}

		handlerSelect.disabled = false;
		handlerSelect.required = required;
		handlerSelect.classList.remove("bg-light");

		if (!handlerSelect.value && handlerSelect.dataset.lastAllowedHandlerId) {
			const restorableOption = Array.from(handlerSelect.options).some(function(option) {
				return option.value === handlerSelect.dataset.lastAllowedHandlerId;
			});
			if (restorableOption) {
				handlerSelect.value = handlerSelect.dataset.lastAllowedHandlerId;
			}
		}

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
		const required = isDeliveryHandlerRequiredMethodName(methodName);
		const prohibited = isDeliveryHandlerProhibitedMethodName(methodName);
		const assignmentBlocked = isDeliveryAssignmentBlockedStatus(form);

		if (assignmentBlocked) {
			handlerSelect.value = "";
			handlerSelect.disabled = true;
			handlerSelect.required = false;
			return;
		}

		if (prohibited) {
			handlerSelect.value = "";
			handlerSelect.disabled = true;
			handlerSelect.required = false;
			return;
		}

		handlerSelect.disabled = false;
		handlerSelect.required = required;

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

	/**
	 * 배송수단 비교용 공통 정규화 함수입니다.
	 *
	 * 2026-08 화물 무담당자 정책 반영 과정에서 prohibited 판별 함수가 이 함수를
	 * 호출하도록 변경되었지만 정의가 누락되어 ReferenceError가 발생했습니다.
	 * 모든 배송수단 판별을 이 함수로 통일하여 동일 문제가 재발하지 않도록 합니다.
	 */
	function normalizeDeliveryMethodName(value) {
		return String(value || "")
			.replace(/\(\s*금액\s*:.*?\)/g, "")
			.replace(/\s+/g, "")
			.trim();
	}

	function isDeliveryHandlerProhibitedMethodName(methodName) {
		const normalized = normalizeDeliveryMethodName(methodName);
		if (!normalized) {
			return true;
		}

		return Array.from(PROHIBITED_HANDLER_METHOD_NAMES).some(function(prohibitedName) {
			return normalized.includes(normalizeDeliveryMethodName(prohibitedName));
		});
	}

	function isDeliveryHandlerRequiredMethodName(methodName) {
		const normalized = normalizeDeliveryMethodName(methodName);
		if (!normalized) {
			return false;
		}

		return Array.from(REQUIRED_HANDLER_METHOD_NAMES).some(function(requiredName) {
			return normalized.includes(normalizeDeliveryMethodName(requiredName));
		});
	}

	function isSiteDeliverySelected(methodSelect) {
		const normalized = normalizeDeliveryMethodName(resolveSelectedDeliveryMethodName(methodSelect));
		return normalized.includes(normalizeDeliveryMethodName(SITE_DELIVERY_METHOD_NAME));
	}

	function isDeliveryAssignmentBlockedStatus(form) {
		const statusSelect = form.querySelector(SELECTORS.statusSelect);
		if (!statusSelect) {
			return false;
		}

		return statusSelect.value === "REQUESTED" || statusSelect.value === "CANCELED";
	}

	// =========================================================
	// 금액 계산
	// =========================================================

	function initMoneyBox(form) {
		const unit = getMoneyInput(form, "unit"), quantity = getMoneyInput(form, "quantity");
		const supply = getMoneyInput(form, "supply");
		if (!unit || !quantity || !supply) return;
		supply.dataset.supplyManual = String(Number(supply.value) !== Number(unit.value) * Number(quantity.value));
		for (const field of [unit, quantity, supply]) {
			field.addEventListener("input", () => {
				if (field === supply) supply.dataset.supplyManual = "true";
				// A number input can temporarily be empty while the operator types a minus sign.
				if ([unit, quantity, supply].some(input => !input.value || !input.validity.valid)) return;
				recalculateEditedMoney(form);
			});
		}
	}

	function normalizeMoneyBeforeSubmit(form) {
		for (const role of ["unit", "quantity", "supply", "packing", "delivery"]) {
			const input = getMoneyInput(form, role);
			if (!input) continue;
			const amount = Number(input.value);
			if (input.validity.badInput || !Number.isInteger(amount) || amount < -2147483648 || amount > 2147483647) {
				input.focus();
				throw new Error("금액과 수량은 저장 가능한 범위의 정수로 입력해 주세요.");
			}
			if (role === "unit" && amount < 0 && amount !== Number(input.defaultValue)) {
				input.focus();
				throw new Error("새로 입력하는 단가는 0 이상이어야 합니다.");
			}
			if ((role === "packing" || role === "delivery") && amount < 0) {
				input.focus();
				throw new Error("배송비·포장비는 0 이상이어야 합니다.");
			}
			if (!input.value) input.value = "0";
		}
		recalculateEditedMoney(form);
		for (const role of ["supply", "total"]) {
			const input = getMoneyInput(form, role);
			if (!input) continue;
			const amount = Number(input.value);
			if (!Number.isInteger(amount) || amount < -2147483648 || amount > 2147483647) {
				throw new Error("공급가와 총비용이 저장 가능한 범위를 벗어났습니다.");
			}
		}
	}

	function recalculateEditedMoney(form) {
		const unit = getMoneyInput(form, "unit"), quantity = getMoneyInput(form, "quantity");
		const supply = getMoneyInput(form, "supply"), vat = getMoneyInput(form, "vat"), total = getMoneyInput(form, "total");
		if (!unit || !quantity || !supply || !vat || !total) return;
		if (supply.dataset.supplyManual === "false") supply.value = String(Number(unit.value) * Number(quantity.value));
		if ([unit, quantity, supply].every(input => Number(input.value) === Number(input.defaultValue))) {
			// Keep imported/invoice totals when only the dealer or delivery details changed.
			total.value = total.defaultValue;
			vat.value = vat.defaultValue;
			return;
		}
		const net = Number(supply.value);
		// Round VAT symmetrically for adjustments and returns, using whole won.
		const tax = Math.sign(net) * Math.floor((Math.abs(net) + 5) / 10);
		vat.value = String(tax);
		total.value = String(net + tax);
	}

	function getMoneyInput(form, role) {
		return form.querySelector('[data-money-role="' + role + '"]');
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

		if (!form.isConnected || resolveSelectedCompanyId(form) !== companyId) return;
		const items = getAddressSourceItems(form).filter(function(item) {
			return String(item.companyId || "") === String(companyId || "");
		});

		openPicker({
			form, companyId,
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

		if (!form.isConnected || resolveSelectedCompanyId(form) !== companyId) return;
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

	async function openDaumPostcode(form, role) {
		try {
			await ensureDaumPostcodeLoaded();
		} catch (error) {
			console.error("[taskListOrderEditExtension] Daum Postcode load failed", error);
			alert("Daum 우편번호 서비스를 불러오지 못했습니다. 네트워크 연결 또는 외부 스크립트 차단 여부를 확인한 뒤 다시 시도해 주세요.");
			return;
		}

		new window.daum.Postcode({
			oncomplete: function(data) {
				const parsed = splitSigungu(data.sigungu || "", data.sido || "");

				applyAddress(form, role, {
					zipCode: data.zonecode || "",
					doName: normalizeProvinceName(data.sido),
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

	function ensureDaumPostcodeLoaded() {
		if (window.daum && window.daum.Postcode) {
			return Promise.resolve();
		}

		if (daumPostcodeLoadingPromise) {
			return daumPostcodeLoadingPromise;
		}

		daumPostcodeLoadingPromise = new Promise(function(resolve, reject) {
			/*
			 * 최초 HTML script가 네트워크/CSP 등의 이유로 실패한 경우에도
			 * 주소검색 버튼 클릭 시 한 번 더 명시적 HTTPS 경로로 재시도합니다.
			 */
			const existingScript = document.getElementById(DAUM_POSTCODE_SCRIPT_ID)
				|| document.querySelector('script[src*="postcode/prod/postcode.v2.js"]');

			if (existingScript && existingScript.parentNode) {
				existingScript.parentNode.removeChild(existingScript);
			}

			const script = document.createElement("script");
			script.id = DAUM_POSTCODE_SCRIPT_ID;
			script.src = DAUM_POSTCODE_SCRIPT_URL;
			script.async = true;

			const timeoutId = window.setTimeout(function() {
				reject(new Error("Daum Postcode script load timeout"));
			}, 10000);

			script.onload = function() {
				window.clearTimeout(timeoutId);
				if (window.daum && window.daum.Postcode) {
					resolve();
					return;
				}
				reject(new Error("Daum Postcode global object is unavailable"));
			};

			script.onerror = function() {
				window.clearTimeout(timeoutId);
				reject(new Error("Daum Postcode script network error"));
			};

			document.head.appendChild(script);
		}).catch(function(error) {
			daumPostcodeLoadingPromise = null;
			throw error;
		});

		return daumPostcodeLoadingPromise;
	}

	function splitSigungu(sigungu, sido) {
		const parts = String(sigungu || "").trim().split(/\s+/).filter(Boolean);
		const normalizedSido = normalizeAddressPart(sido);

		if (parts.length === 0) {
			return { siName: "", guName: "" };
		}

		if (parts.length === 1) {
			const single = parts[0];

			// 세종처럼 sido와 sigungu가 같은 값으로 내려오는 경우 중복 저장하지 않습니다.
			if (normalizedSido && normalizeAddressPart(single) === normalizedSido) {
				return { siName: "", guName: "" };
			}

			/*
			 * 서울특별시 강남구처럼 sigungu가 "강남구" 하나만 내려오는 경우
			 * 이를 siName에 넣으면 시/구 컬럼이 뒤섞이므로 guName으로 저장합니다.
			 */
			if (single.endsWith("구")) {
				return { siName: "", guName: single };
			}

			return { siName: single, guName: "" };
		}

		return {
			siName: parts[0] || "",
			guName: parts.slice(1).join(" ")
		};
	}

	function normalizeAddressPart(value) {
		return String(value || "").replace(/\s+/g, "").trim();
	}

	function normalizeProvinceName(value) {
		const text = String(value || "")
			.normalize("NFKC")
			.replace(/\u00A0/g, " ")
			.trim()
			.replace(/\s+/g, " ");
		if (!text) return "";

		const compact = text.replace(/\s+/g, "");
		const aliased = PROVINCE_ALIASES.get(compact);
		if (aliased) return aliased;

		if (compact.includes("광주") && compact.includes("통합특별시")) {
			return "광주광역시";
		}

		return text;
	}

	function applyAddress(form, role, item) {
		setAddressValue(form, role, "zipCode", item.zipCode);
		setAddressValue(form, role, "doName", normalizeProvinceName(item.doName));
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

		const roadAddress = getAddressValue(form, normalizedRole, "roadAddress");
		const detailAddress = getAddressValue(form, normalizedRole, "detailAddress");
		const normalizedRoadAddress = normalizeAddressPart(roadAddress);
		const normalizedDetailAddress = normalizeAddressPart(detailAddress);

		const text = [
			roadAddress,
			detailAddress && (!normalizedRoadAddress || !normalizedRoadAddress.endsWith(normalizedDetailAddress))
				? detailAddress
				: ""
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

			if (!form.isConnected || resolveSelectedCompanyId(form) !== companyId) return;
			const items = getOrdererSourceItems(form).filter(function(item) {
				return String(item.companyId || "") === String(companyId || "");
			});

			openPicker({
				form, companyId,
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

		if (!input.value.trim() && !["색상", "사이즈", "규격"].includes(row.dataset.optionKey)) {
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
			const key = String(row.dataset.optionKey || "").trim();
			const input = row.querySelector(".admin-task-list-second-option-value-input");
			if (!key || !input) {
				return;
			}

			const value = input.value.trim();
			const optionalFixed = row.dataset.optionOptional === "true"
				|| OPTION_OPTIONAL_FIXED_KEYS.has(key);

			/*
			 * 제품시리즈ID는 기존 DB 제품과의 선택적 연결값입니다.
			 * 값이 없거나 빈 문자열로 저장된 규격제품은 해당 키를 JSON에서 제외하고,
			 * 나머지 옵션 수정 내용은 그대로 저장할 수 있도록 허용합니다.
			 */
			if (!value && optionalFixed) {
				return;
			}

			if (!value && !["색상", "사이즈", "규격"].includes(key)) {
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
					const {form, companyId, onSelect} = activePicker;
					closePicker();
					if (!form.isConnected) return;
					if (resolveSelectedCompanyId(form) !== companyId) {
						alert("대리점이 변경되었습니다. 현재 대리점 목록에서 다시 선택해 주세요.");
						return;
					}
					onSelect(item);
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

	async function getCompanyOptions(companyId) {
		const key = String(companyId);
		if (!companyOptionsCache.has(key)) {
			const data = await fetchCompanyOptions(companyId);
			if (String(data.company?.companyId) !== key ||
				![data.members, data.deliveryAddresses, data.orderers].every(Array.isArray)) {
				throw new Error("선택한 업체 정보를 확인해 주세요.");
			}
			companyOptionsCache.set(key, data);
		}
		return companyOptionsCache.get(key);
	}

	async function ensureCompanyOptionsLoaded(form, companyId) {
		const hasMembers = getMemberSourceItems(form).some(member => String(member.companyId) === String(companyId));
		if (!companyId || (getCompanySourceItem(form, companyId) && hasMembers)) return;
		const data = await getCompanyOptions(companyId);
		if (form.isConnected && resolveSelectedCompanyId(form) === companyId) applyCompanyOptionsToForm(form, data);
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
		if (requesterSelect) {
			const changed = resolveSelectedCompanyId(form) !== form.elements.namedItem("originalCompanyId").value;
			requesterSelect.disabled = changed;
			if (changed) {
				requesterSelect.value = "";
				requesterSelect.options[0].textContent = data.company?.representativeName || "업체 대표회원 자동 지정";
			}
		}
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

})();
