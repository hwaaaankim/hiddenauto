(function () {
	'use strict';

	const API = {
		preview: '/team/dispatchList/api/orders/order-management/preview',
		save: '/team/dispatchList/api/orders/order-management'
	};

	const state = {
		rows: new Map(),
		drafts: new Map(),
		busy: false,
		pendingItems: [],
		modalTransition: null,
		completeAllTouched: false
	};

	let modalEl = null;
	let modal = null;
	let confirmModalEl = null;
	let confirmModal = null;
	let listEl = null;
	let loadingEl = null;
	let alertEl = null;
	let saveBtn = null;
	let countEl = null;
	let bulkMethodEl = null;
	let bulkHandlerEl = null;
	let bulkHandlerHelpEl = null;
	let completeAllEl = null;
	let confirmListEl = null;
	let confirmCountEl = null;
	let confirmAlertEl = null;
	let confirmBackBtn = null;
	let confirmSaveBtn = null;
	let methodNames = new Map();
	let handlerNames = new Map();

	document.addEventListener('DOMContentLoaded', init);

	function init() {
		modalEl = document.getElementById('dispatch-list-order-management-modal');
		if (!modalEl || !window.bootstrap) return;

		modal = window.bootstrap.Modal.getOrCreateInstance(modalEl);
		confirmModalEl = document.getElementById('dispatch-list-order-management-confirm-modal');
		confirmModal = confirmModalEl ? window.bootstrap.Modal.getOrCreateInstance(confirmModalEl) : null;
		listEl = document.getElementById('dispatch-order-management-list');
		loadingEl = document.getElementById('dispatch-order-management-loading');
		alertEl = document.getElementById('dispatch-order-management-alert');
		saveBtn = document.getElementById('dispatch-order-management-save');
		countEl = document.getElementById('dispatch-order-management-count');
		bulkMethodEl = document.getElementById('dispatch-order-management-bulk-method');
		bulkHandlerEl = document.getElementById('dispatch-order-management-bulk-handler');
		bulkHandlerHelpEl = document.getElementById('dispatch-order-management-bulk-handler-help');
		completeAllEl = document.getElementById('dispatch-order-management-complete-all');
		confirmListEl = document.getElementById('dispatch-order-management-confirm-list');
		confirmCountEl = document.getElementById('dispatch-order-management-confirm-count');
		confirmAlertEl = document.getElementById('dispatch-order-management-confirm-alert');
		confirmBackBtn = document.getElementById('dispatch-order-management-confirm-back');
		confirmSaveBtn = document.getElementById('dispatch-order-management-confirm-save');

		Array.from(bulkMethodEl ? bulkMethodEl.options : []).forEach(function (option) {
			if (option.value) methodNames.set(String(option.value), option.dataset.methodName || option.textContent.trim());
		});
		Array.from(bulkHandlerEl ? bulkHandlerEl.options : []).forEach(function (option) {
			if (option.value) handlerNames.set(String(option.value), option.textContent.trim());
		});

		document.querySelectorAll('.dispatch-list-order-management-open').forEach(function (button) {
			button.addEventListener('click', openManagement);
		});
		document.addEventListener('change', updateOpenButtons);

		const tbody = document.getElementById('dispatch-list-tbody');
		if (tbody) new MutationObserver(updateOpenButtons).observe(tbody, { childList: true });

		if (listEl) {
			listEl.addEventListener('change', handleDraftChange);
			listEl.addEventListener('input', handleDraftInput);
			listEl.addEventListener('click', handleListClick);
		}
		document.getElementById('dispatch-order-management-apply-bulk')?.addEventListener('click', applyBulkValues);
		bulkMethodEl?.addEventListener('change', syncBulkHandlerAvailability);
		completeAllEl?.addEventListener('change', function () {
			state.completeAllTouched = true;
			applyCompleteAll();
		});
		saveBtn?.addEventListener('click', prepareSaveConfirmation);
		confirmBackBtn?.addEventListener('click', returnToEditing);
		confirmSaveBtn?.addEventListener('click', commitSave);
		modalEl.addEventListener('hidden.bs.modal', handleManagementModalHidden);
		confirmModalEl?.addEventListener('hidden.bs.modal', handleConfirmationModalHidden);
		syncBulkHandlerAvailability();
		syncCompleteAllState();
		updateOpenButtons();
	}

	function selectedOrderIds() {
		return Array.from(document.querySelectorAll('.dispatch-list-row-check:checked'))
			.map(function (checkbox) { return Number(checkbox.dataset.orderId); })
			.filter(function (id) { return Number.isFinite(id) && id > 0; });
	}

	function updateOpenButtons() {
		const disabled = selectedOrderIds().length === 0 || state.busy;
		document.querySelectorAll('.dispatch-list-order-management-open').forEach(function (button) {
			button.disabled = disabled;
		});
	}

	async function openManagement() {
		const ids = selectedOrderIds();
		if (!ids.length || state.busy) return;

		const controlEl = document.getElementById('dispatch-list-control-modal');
		const controlModal = controlEl ? window.bootstrap.Modal.getInstance(controlEl) : null;
		if (controlModal) controlModal.hide();

		resetModal();
		setBusy(true);
		setLoading(true);
		if (countEl) countEl.textContent = ids.length + '건';
		modal.show();

		try {
			const rows = await postJson(API.preview, { orderIds: ids });
			(rows || []).forEach(function (row) {
				const id = Number(row.orderId);
				state.rows.set(id, row);
				state.drafts.set(id, createDraft(row));
			});
			renderCards();
			syncBulkHandlerAvailability();
			if (saveBtn) saveBtn.disabled = state.drafts.size === 0;
		} catch (error) {
			showError(error.message || '선택 주문을 불러오지 못했습니다.');
		} finally {
			setLoading(false);
			setBusy(false);
		}
	}

	function createDraft(row) {
		return {
			orderId: Number(row.orderId),
			methodId: row.deliveryMethodId == null ? '' : String(row.deliveryMethodId),
			handlerId: row.deliveryHandlerId == null ? '' : String(row.deliveryHandlerId),
			deliveryCost: row.deliveryCost == null ? '0' : String(row.deliveryCost),
			dispatchComplete: false,
			general: addressFromRow(row, false),
			site: addressFromRow(row, true),
			generalAddressSearched: false,
			siteAddressSearched: false,
			ordererName: row.ordererName || '',
			ordererPhone: row.ordererPhone || ''
		};
	}

	function addressFromRow(row, site) {
		const prefix = site ? 'site' : '';
		const field = function (name) {
			const key = prefix ? prefix + name.charAt(0).toUpperCase() + name.slice(1) : name;
			return row[key] || '';
		};
		return {
			zipCode: field('zipCode'),
			doName: field('doName'),
			siName: field('siName'),
			guName: field('guName'),
			roadAddress: field('roadAddress'),
			detailAddress: field('detailAddress')
		};
	}

	function renderCards() {
		if (!listEl) return;
		listEl.innerHTML = Array.from(state.drafts.values()).map(function (draft) {
			const row = state.rows.get(draft.orderId) || {};
			const site = isSiteMethod(draft.methodId);
			const handlerRequired = requiresHandler(draft.methodId);
			const address = site ? draft.site : draft.general;
			const requirements = getDraftRequirements(draft, row);
			const requirementGuide = requirements.messages.length
				? '<div class="dispatch-order-management-requirement-guide" role="status">' +
				'<i class="ri-error-warning-line" aria-hidden="true"></i><div><strong>추가 입력 필요</strong><div>' +
				requirements.messages.map(escapeHtml).join(' · ') + '</div></div></div>'
				: '';
			return '<article class="dispatch-order-management-card ' +
				(requirements.messages.length ? 'dispatch-order-management-card-needs-action' : '') +
				'" data-order-id="' + escapeAttr(draft.orderId) + '">' +
				'<div class="dispatch-order-management-card-head">' +
				'<div><strong>ORDER #' + escapeHtml(draft.orderId) + '</strong>' +
				'<div class="text-muted small">' + escapeHtml(row.companyName || '-') + ' · ' + escapeHtml(row.productName || '-') +
				' · ' + escapeHtml(row.sizeText || '-') + ' · 배송희망일 ' + escapeHtml(row.preferredDeliveryDateText || '-') + '</div></div>' +
				'<span class="badge bg-' + (row.dispatchCompletable ? 'success' : 'secondary') + '-subtle text-' +
				(row.dispatchCompletable ? 'success' : 'secondary') + '">' + escapeHtml(row.statusLabel || '-') + '</span>' +
				'</div>' +
				requirementGuide +
				'<div class="dispatch-order-management-grid">' +
				fieldSelect('배송수단', 'methodId', methodOptions(draft.methodId), true, false, requirements.method) +
				fieldSelect('배송담당자', 'handlerId', handlerRequired
					? handlerOptions(draft.handlerId)
					: '<option value="">담당자 지정 대상 아님</option>', handlerRequired, !handlerRequired, requirements.handler) +
				fieldInput('배송비', 'deliveryCost', draft.deliveryCost, 'number', '0', false, requirements.deliveryCost) +
				'<div><label class="dispatch-list-label">출고완료</label><label class="form-check mt-2">' +
				'<input class="form-check-input" type="checkbox" data-field="dispatchComplete" ' +
				(draft.dispatchComplete ? 'checked ' : '') + (!row.dispatchCompletable ? 'disabled ' : '') + '>' +
				'<span class="form-check-label">' + (row.dispatchCompletable ? '출고완료 처리' : '생산완료 후 가능') + '</span></label></div>' +
				'<div class="dispatch-order-management-address ' +
				(requirements.address ? 'dispatch-order-management-field-needs-action' : '') +
				'" data-address-role="' + (site ? 'site' : 'general') + '">' +
				'<div class="dispatch-order-management-address-row">' +
				'<div><label class="dispatch-list-label dispatch-order-management-required">' + (site ? '현장주소' : '일반 배송주소') + '</label>' +
				'<input class="form-control" value="' + escapeAttr(address.roadAddress) + '" readonly aria-label="도로명주소"></div>' +
				'<div><label class="dispatch-list-label">상세주소</label><input class="form-control" data-address-field="detailAddress" value="' + escapeAttr(address.detailAddress) + '"></div>' +
				'<button type="button" class="btn btn-outline-primary" data-address-search="' + (site ? 'site' : 'general') + '">' +
				'<i class="ri-map-pin-search-line me-1"></i>Daum 주소검색</button></div>' +
				'<div class="text-muted small mt-2">[' + escapeHtml(address.zipCode || '-') + '] ' + escapeHtml(address.roadAddress || '주소를 검색해 주세요.') +
				(address.detailAddress ? ' ' + escapeHtml(address.detailAddress) : '') + '</div>' +
				(site ? '<div class="row g-2 mt-1"><div class="col-md-6 ' +
				(requirements.ordererName ? 'dispatch-order-management-field-needs-action' : '') + '">' +
				'<label class="dispatch-list-label dispatch-order-management-required">현장배송 수령자</label>' +
				'<input class="form-control" data-field="ordererName" value="' + escapeAttr(draft.ordererName) + '"></div>' +
				'<div class="col-md-6 ' + (requirements.ordererPhone ? 'dispatch-order-management-field-needs-action' : '') +
				'"><label class="dispatch-list-label dispatch-order-management-required">수령자 연락처</label>' +
				'<input class="form-control" data-field="ordererPhone" value="' + escapeAttr(draft.ordererPhone) + '"></div></div>' : '') +
				'</div></div></article>';
		}).join('');
		syncCompleteAllState();
	}

	function fieldSelect(label, field, options, required, disabled, needsAction) {
		return '<div class="' + (needsAction ? 'dispatch-order-management-field-needs-action' : '') +
			'"><label class="dispatch-list-label ' + (required ? 'dispatch-order-management-required' : '') + '">' +
			escapeHtml(label) + '</label><select class="form-select" data-field="' + escapeAttr(field) + '" ' +
			(disabled ? 'disabled' : '') + '>' + options + '</select></div>';
	}

	function fieldInput(label, field, value, type, min, required, needsAction) {
		return '<div class="' + (needsAction ? 'dispatch-order-management-field-needs-action' : '') +
			'"><label class="dispatch-list-label ' + (required ? 'dispatch-order-management-required' : '') + '">' +
			escapeHtml(label) + '</label><input class="form-control" data-field="' + escapeAttr(field) + '" type="' +
			escapeAttr(type) + '" min="' + escapeAttr(min) + '" step="1" value="' + escapeAttr(value) + '">' +
			'<div class="form-text">빈칸은 0원으로 저장</div></div>';
	}

	function methodOptions(selected) {
		let html = '<option value="">배송수단 선택</option>';
		methodNames.forEach(function (name, id) {
			html += '<option value="' + escapeAttr(id) + '" ' + (String(selected) === String(id) ? 'selected' : '') + '>' + escapeHtml(name) + '</option>';
		});
		return html;
	}

	function handlerOptions(selected) {
		let html = '<option value="">배송담당자 선택</option>';
		Array.from(bulkHandlerEl ? bulkHandlerEl.options : []).forEach(function (option) {
			if (!option.value) return;
			html += '<option value="' + escapeAttr(option.value) + '" ' +
			(String(selected) === String(option.value) ? 'selected' : '') + '>' + escapeHtml(option.textContent.trim()) + '</option>';
		});
		return html;
	}

	function getDraftRequirements(draft, row) {
		const result = {
			messages: [],
			method: false,
			handler: false,
			address: false,
			deliveryCost: false,
			ordererName: false,
			ordererPhone: false
		};
		const site = isSiteMethod(draft.methodId);
		const handlerRequired = requiresHandler(draft.methodId);
		const address = site ? draft.site : draft.general;
		const originalAddress = site ? addressFromRow(row, true) : addressFromRow(row, false);
		const originallySite = isSiteMethod(row.deliveryMethodId);

		if (!draft.methodId) {
			result.method = true;
			result.messages.push('배송수단을 선택해 주세요.');
		}
		if (handlerRequired && !draft.handlerId) {
			result.handler = true;
			result.messages.push('직배송·현장배송은 배송담당자 지정이 필수입니다.');
		}
		if (handlerRequired && !row.preferredDeliveryDateText) {
			result.handler = true;
			result.messages.push('배송담당자 배정을 위해 배송희망일이 필요합니다.');
		}
		if (!address.zipCode || !address.roadAddress) {
			result.address = true;
			result.messages.push((site ? '현장주소' : '일반 배송주소') + '를 Daum 주소검색으로 입력해 주세요.');
		} else if ((addressChanged(address, originalAddress) || site !== originallySite)
				&& !draft[site ? 'siteAddressSearched' : 'generalAddressSearched']) {
			result.address = true;
			result.messages.push((site ? '현장주소' : '일반 배송주소') + ' 변경은 Daum 주소검색이 필요합니다.');
		}
		if (site && !String(draft.ordererName || '').trim()) {
			result.ordererName = true;
			result.messages.push('현장배송 수령자를 입력해 주세요.');
		}
		if (site && !String(draft.ordererPhone || '').trim()) {
			result.ordererPhone = true;
			result.messages.push('현장배송 수령자 연락처를 입력해 주세요.');
		}
		if (draft.deliveryCost !== ''
				&& (!Number.isInteger(Number(draft.deliveryCost)) || Number(draft.deliveryCost) < 0)) {
			result.deliveryCost = true;
			result.messages.push('배송비는 0 이상의 정수로 입력해 주세요.');
		}

		return result;
	}

	function handleDraftChange(event) {
		const card = event.target.closest('[data-order-id]');
		if (!card) return;
		const draft = state.drafts.get(Number(card.dataset.orderId));
		if (!draft) return;
		const field = event.target.dataset.field;
		if (field === 'methodId' || field === 'handlerId') {
			draft[field] = event.target.value;
			renderCards();
			syncBulkHandlerAvailability();
		} else if (field === 'ordererName' || field === 'ordererPhone' || field === 'deliveryCost') {
			draft[field] = event.target.value;
			renderCards();
		} else if (field === 'dispatchComplete') {
			draft.dispatchComplete = event.target.checked;
			syncCompleteAllState();
		}
	}

	function handleDraftInput(event) {
		const card = event.target.closest('[data-order-id]');
		if (!card) return;
		const draft = state.drafts.get(Number(card.dataset.orderId));
		if (!draft) return;
		if (event.target.dataset.addressField) {
			const role = card.querySelector('[data-address-role]')?.dataset.addressRole;
			if (role && draft[role]) draft[role][event.target.dataset.addressField] = event.target.value;
			return;
		}
		const field = event.target.dataset.field;
		if (field) draft[field] = event.target.value;
	}

	function handleListClick(event) {
		const button = event.target.closest('[data-address-search]');
		if (!button) return;
		const card = button.closest('[data-order-id]');
		if (!card) return;
		openPostcode(Number(card.dataset.orderId), button.dataset.addressSearch);
	}

	function applyBulkValues() {
		const methodId = bulkMethodEl ? bulkMethodEl.value : '';
		const handlerId = bulkHandlerEl && !bulkHandlerEl.disabled ? bulkHandlerEl.value : '';
		if (!methodId && !handlerId && !state.completeAllTouched) {
			showError('전체에 적용할 배송수단·배송담당자 또는 출고완료 항목을 선택해 주세요.');
			return;
		}
		state.drafts.forEach(function (draft) {
			if (methodId) draft.methodId = methodId;
			if (handlerId && requiresHandler(draft.methodId)) draft.handlerId = handlerId;
		});
		hideError();
		renderCards();
		syncBulkHandlerAvailability();
		state.completeAllTouched = false;
	}

	function syncBulkHandlerAvailability() {
		if (!bulkHandlerEl) return;
		const selectedMethodId = bulkMethodEl ? bulkMethodEl.value : '';
		const selectedMethodBlocksHandler = Boolean(selectedMethodId && !requiresHandler(selectedMethodId));
		let eligibleCount = 0;
		state.drafts.forEach(function (draft) {
			const effectiveMethodId = selectedMethodId || draft.methodId;
			if (requiresHandler(effectiveMethodId)) eligibleCount += 1;
		});

		const disabled = selectedMethodBlocksHandler || eligibleCount === 0;
		bulkHandlerEl.disabled = disabled;
		if (disabled) bulkHandlerEl.value = '';

		if (!bulkHandlerHelpEl) return;
		if (selectedMethodBlocksHandler) {
			bulkHandlerHelpEl.textContent = '해당 배송수단은 담당자가 필요하지 않아 일괄선택이 불가합니다.';
			bulkHandlerHelpEl.classList.add('text-warning');
		} else if (eligibleCount === 0) {
			bulkHandlerHelpEl.textContent = '선택 오더에는 담당자가 필요한 직배·현장배송 건이 없습니다.';
			bulkHandlerHelpEl.classList.add('text-warning');
		} else {
			bulkHandlerHelpEl.textContent = '담당자가 필요한 직배송·현장배송 ' + eligibleCount + '건에만 적용됩니다.';
			bulkHandlerHelpEl.classList.remove('text-warning');
		}
	}

	function applyCompleteAll() {
		const checked = completeAllEl && completeAllEl.checked;
		state.drafts.forEach(function (draft, id) {
			const row = state.rows.get(id);
			draft.dispatchComplete = Boolean(checked && row && row.dispatchCompletable);
		});
		renderCards();
	}

	function syncCompleteAllState() {
		if (!completeAllEl) return;
		const eligibleDrafts = Array.from(state.drafts.entries())
			.filter(function (entry) {
				const row = state.rows.get(entry[0]);
				return Boolean(row && row.dispatchCompletable);
			})
			.map(function (entry) { return entry[1]; });
		const selectedCount = eligibleDrafts.filter(function (draft) { return draft.dispatchComplete; }).length;
		completeAllEl.disabled = eligibleDrafts.length === 0;
		completeAllEl.checked = eligibleDrafts.length > 0 && selectedCount === eligibleDrafts.length;
		completeAllEl.indeterminate = selectedCount > 0 && selectedCount < eligibleDrafts.length;
	}

	function openPostcode(orderId, role) {
		if (!window.daum || !window.daum.Postcode) {
			showError('Daum 우편번호 서비스를 불러오지 못했습니다. 네트워크 연결 후 다시 시도해 주세요.');
			return;
		}
		const draft = state.drafts.get(orderId);
		if (!draft || !draft[role]) return;

		new window.daum.Postcode({
			oncomplete: function (data) {
				const region = splitSigungu(data.sigungu || '', data.sido || '');
				draft[role] = {
					zipCode: data.zonecode || '',
					doName: data.sido || '',
					siName: region.siName,
					guName: region.guName,
					roadAddress: data.roadAddress || data.address || '',
					detailAddress: ''
				};
				draft[role + 'AddressSearched'] = true;
				hideError();
				renderCards();
				window.setTimeout(function () {
					listEl?.querySelector('[data-order-id="' + orderId + '"] [data-address-field="detailAddress"]')?.focus();
				}, 0);
			}
		}).open();
	}

	function splitSigungu(sigungu, sido) {
		const parts = String(sigungu || '').trim().split(/\s+/).filter(Boolean);
		if (!parts.length) return { siName: '', guName: '' };
		if (parts.length === 1) {
			if (parts[0] === sido) return { siName: '', guName: '' };
			return parts[0].endsWith('구') ? { siName: '', guName: parts[0] } : { siName: parts[0], guName: '' };
		}
		return { siName: parts[0], guName: parts.slice(1).join(' ') };
	}

	function prepareSaveConfirmation() {
		if (state.busy || state.modalTransition) return;
		const validation = validateDrafts();
		if (!validation.ok) {
			showError(validation.message);
			renderCards();
			listEl?.querySelector('[data-order-id="' + validation.orderId + '"]')
				?.scrollIntoView({ behavior: 'smooth', block: 'center' });
			return;
		}

		const changeEntries = buildChangeEntries(validation.items);
		if (!changeEntries.length) {
			showError('변경된 내용이 없습니다. 배송수단·담당자·주소·배송비 또는 출고완료 값을 확인해 주세요.');
			return;
		}

		state.pendingItems = changeEntries.map(function (entry) { return entry.payload; });
		renderConfirmation(changeEntries);
		hideError();
		state.modalTransition = 'TO_CONFIRM';
		modal.hide();
	}

	async function commitSave() {
		if (state.busy || !state.pendingItems.length) return;
		setBusy(true);
		hideConfirmError();
		try {
			const result = await postJson(API.save, { items: state.pendingItems });
			window.alert((result.updatedCount || 0) + '건의 발주관리 내용이 저장되었습니다.');
			window.location.reload();
		} catch (error) {
			showConfirmError(error.message || '발주관리 저장 중 오류가 발생했습니다.');
		} finally {
			setBusy(false);
		}
	}

	function returnToEditing() {
		if (state.busy || !confirmModal) return;
		state.modalTransition = 'TO_EDIT';
		confirmModal.hide();
	}

	function handleManagementModalHidden() {
		if (state.modalTransition === 'TO_CONFIRM') {
			state.modalTransition = null;
			confirmModal?.show();
			return;
		}
		resetModal();
	}

	function handleConfirmationModalHidden() {
		if (state.modalTransition === 'TO_EDIT') {
			state.modalTransition = null;
			modal?.show();
			return;
		}
		if (!state.busy) resetModal();
	}

	function validateDrafts() {
		const items = [];
		for (const draft of state.drafts.values()) {
			const row = state.rows.get(draft.orderId) || {};
			const requirements = getDraftRequirements(draft, row);
			let message = requirements.messages.length ? requirements.messages[0] : '';
			if (!message && draft.dispatchComplete && !row.dispatchCompletable) {
				message = '생산완료 상태가 아닌 주문은 출고완료 처리할 수 없습니다.';
			}

			if (message) {
				return {
					ok: false,
					message: 'ORDER #' + draft.orderId + ': ' + message,
					orderId: draft.orderId
				};
			}

			items.push(toPayload(draft));
		}
		return { ok: true, items: items };
	}

	function buildChangeEntries(items) {
		const payloadById = new Map((items || []).map(function (item) { return [Number(item.orderId), item]; }));
		const entries = [];

		state.drafts.forEach(function (draft, orderId) {
			const row = state.rows.get(orderId) || {};
			const payload = payloadById.get(Number(orderId));
			if (!payload) return;
			const changes = [];
			const finalSite = isSiteMethod(draft.methodId);
			const originalSite = isSiteMethod(row.deliveryMethodId);

			appendChange(changes, '배송수단', row.deliveryMethodName || methodName(row.deliveryMethodId), methodName(draft.methodId),
				!sameId(row.deliveryMethodId, draft.methodId));

			const finalHandlerId = requiresHandler(draft.methodId) ? draft.handlerId : '';
			appendChange(changes, '배송담당자', row.deliveryHandlerName || handlerName(row.deliveryHandlerId), handlerName(finalHandlerId),
				!sameId(row.deliveryHandlerId, finalHandlerId));

			const beforeCost = Number(row.deliveryCost || 0);
			const afterCost = draft.deliveryCost === '' ? 0 : Number(draft.deliveryCost);
			appendChange(changes, '배송비', formatMoney(beforeCost), formatMoney(afterCost), beforeCost !== afterCost);

			const beforeAddress = originalSite ? addressFromRow(row, true) : addressFromRow(row, false);
			const afterAddress = finalSite ? draft.site : draft.general;
			appendChange(
				changes,
				'배송주소',
				(originalSite ? '현장 · ' : '일반 · ') + formatAddress(beforeAddress),
				(finalSite ? '현장 · ' : '일반 · ') + formatAddress(afterAddress),
				originalSite !== finalSite || addressChanged(beforeAddress, afterAddress)
			);

			if (finalSite) {
				appendChange(changes, '현장 수령자', row.ordererName || '-', draft.ordererName || '-',
					String(row.ordererName || '').trim() !== String(draft.ordererName || '').trim());
				appendChange(changes, '수령자 연락처', row.ordererPhone || '-', draft.ordererPhone || '-',
					String(row.ordererPhone || '').trim() !== String(draft.ordererPhone || '').trim());
			}

			if (draft.dispatchComplete) {
				appendChange(changes, '주문 상태', row.statusLabel || '-', '출고완료', true);
			}

			if (changes.length) {
				entries.push({
					orderId: orderId,
					companyName: row.companyName || '-',
					productName: row.productName || '-',
					changes: changes,
					payload: payload
				});
			}
		});

		return entries;
	}

	function appendChange(changes, label, before, after, changed) {
		if (!changed) return;
		changes.push({ label: label, before: before || '-', after: after || '-' });
	}

	function renderConfirmation(entries) {
		if (confirmCountEl) confirmCountEl.textContent = entries.length + '건 변경';
		if (confirmSaveBtn) confirmSaveBtn.disabled = entries.length === 0;
		if (!confirmListEl) return;
		confirmListEl.innerHTML = entries.map(function (entry) {
			return '<article class="dispatch-order-management-confirm-card">' +
				'<div class="dispatch-order-management-confirm-head"><div><strong>ORDER #' + escapeHtml(entry.orderId) +
				'</strong><div class="text-muted small">' + escapeHtml(entry.companyName) + ' · ' +
				escapeHtml(entry.productName) + '</div></div><span class="badge bg-primary-subtle text-primary">' +
				entry.changes.length + '개 변경</span></div><div>' +
				entry.changes.map(function (change) {
					return '<div class="dispatch-order-management-confirm-change"><span>' + escapeHtml(change.label) +
					'</span><strong><span class="text-muted">' + escapeHtml(change.before) + '</span> <i class="ri-arrow-right-line mx-1" aria-hidden="true"></i> ' +
					escapeHtml(change.after) + '</strong></div>';
				}).join('') + '</div></article>';
		}).join('');
	}

	function sameId(left, right) {
		return String(left == null ? '' : left) === String(right == null ? '' : right);
	}

	function methodName(methodId) {
		return methodNames.get(String(methodId == null ? '' : methodId)) || '미지정';
	}

	function handlerName(handlerId) {
		return handlerNames.get(String(handlerId == null ? '' : handlerId)) || '미지정';
	}

	function formatMoney(value) {
		return Number(value || 0).toLocaleString('ko-KR') + '원';
	}

	function formatAddress(address) {
		if (!address) return '-';
		const body = [address.roadAddress, address.detailAddress].filter(Boolean).join(' ');
		return (address.zipCode ? '[' + address.zipCode + '] ' : '') + (body || '-');
	}

	function toPayload(draft) {
		return {
			orderId: draft.orderId,
			deliveryMethodId: Number(draft.methodId),
			deliveryHandlerId: requiresHandler(draft.methodId) && draft.handlerId ? Number(draft.handlerId) : null,
			deliveryCost: draft.deliveryCost === '' ? 0 : Number(draft.deliveryCost),
			dispatchComplete: Boolean(draft.dispatchComplete),
			zipCode: draft.general.zipCode,
			doName: draft.general.doName,
			siName: draft.general.siName,
			guName: draft.general.guName,
			roadAddress: draft.general.roadAddress,
			detailAddress: draft.general.detailAddress,
			generalAddressSearched: draft.generalAddressSearched,
			siteZipCode: draft.site.zipCode,
			siteDoName: draft.site.doName,
			siteSiName: draft.site.siName,
			siteGuName: draft.site.guName,
			siteRoadAddress: draft.site.roadAddress,
			siteDetailAddress: draft.site.detailAddress,
			siteAddressSearched: draft.siteAddressSearched,
			ordererName: draft.ordererName,
			ordererPhone: draft.ordererPhone
		};
	}

	function addressChanged(left, right) {
		return ['zipCode', 'doName', 'siName', 'guName', 'roadAddress', 'detailAddress'].some(function (key) {
			return String(left[key] || '').trim() !== String(right[key] || '').trim();
		});
	}

	function isSiteMethod(methodId) {
		return String(methodNames.get(String(methodId)) || '').replace(/\s/g, '').includes('현장배송');
	}

	function requiresHandler(methodId) {
		const name = String(methodNames.get(String(methodId)) || '').replace(/\s/g, '');
		return name.includes('직배송') || name.includes('현장배송');
	}

	async function postJson(url, body) {
		const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
		const csrfHeader = document.getElementById('dispatch-list-csrf-header')?.value;
		const csrfToken = document.getElementById('dispatch-list-csrf-token')?.value;
		if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

		const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
		const text = await response.text();
		let payload = {};
		try { payload = text ? JSON.parse(text) : {}; } catch (_) { payload = {}; }
		if (!response.ok) throw new Error(payload.message || '요청 처리에 실패했습니다.');
		return payload;
	}

	function setBusy(busy) {
		state.busy = busy;
		if (saveBtn) {
			saveBtn.disabled = busy || state.drafts.size === 0;
			saveBtn.innerHTML = busy
				? '<span class="spinner-border spinner-border-sm me-1"></span>검증 및 저장 중'
				: '<i class="ri-save-3-line me-1"></i>전체 검증 후 저장';
		}
		if (confirmBackBtn) confirmBackBtn.disabled = busy;
		if (confirmSaveBtn) {
			confirmSaveBtn.disabled = busy || state.pendingItems.length === 0;
			confirmSaveBtn.innerHTML = busy
				? '<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>최종 저장 중'
				: '<i class="ri-save-3-line me-1" aria-hidden="true"></i>확인 후 최종 저장';
		}
		updateOpenButtons();
	}

	function setLoading(loading) {
		if (loadingEl) loadingEl.classList.toggle('d-none', !loading);
		if (listEl) listEl.classList.toggle('d-none', loading);
	}

	function showError(message) {
		if (!alertEl) return;
		alertEl.textContent = message;
		alertEl.classList.remove('d-none');
	}

	function hideError() {
		if (alertEl) alertEl.classList.add('d-none');
	}

	function showConfirmError(message) {
		if (!confirmAlertEl) return;
		confirmAlertEl.textContent = message;
		confirmAlertEl.classList.remove('d-none');
	}

	function hideConfirmError() {
		if (confirmAlertEl) confirmAlertEl.classList.add('d-none');
	}

	function resetModal() {
		state.rows.clear();
		state.drafts.clear();
		state.pendingItems = [];
		state.modalTransition = null;
		state.completeAllTouched = false;
		if (listEl) listEl.innerHTML = '';
		if (confirmListEl) confirmListEl.innerHTML = '';
		if (confirmCountEl) confirmCountEl.textContent = '0건 변경';
		if (bulkMethodEl) bulkMethodEl.value = '';
		if (bulkHandlerEl) bulkHandlerEl.value = '';
		if (completeAllEl) completeAllEl.checked = false;
		if (completeAllEl) completeAllEl.indeterminate = false;
		if (saveBtn) saveBtn.disabled = true;
		hideError();
		hideConfirmError();
		syncBulkHandlerAvailability();
		syncCompleteAllState();
	}

	function escapeHtml(value) {
		return String(value == null ? '' : value)
			.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
			.replaceAll('"', '&quot;').replaceAll("'", '&#039;');
	}

	function escapeAttr(value) {
		return escapeHtml(value);
	}
})();
