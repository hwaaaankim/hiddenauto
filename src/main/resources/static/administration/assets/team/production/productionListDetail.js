/* /administration/assets/team/production/productionListDetail.js */

(function() {
	'use strict';

	const config = window.teamProductionOverviewConfig || {};

	const els = {};
	const state = {
		orderCache: new Map(),
		openedOrderId: '',
		imageModalOpen: false,
		imageOrderId: '',
		imageIndex: 0,
		zoom: 1
	};

	document.addEventListener('DOMContentLoaded', init);

	function init() {
		cacheElements();
		bindEvents();
	}

	function cacheElements() {
		els.table = document.getElementById('team-production-table');

		els.imageModal = document.getElementById('team-production-inline-image-modal');
		els.imageViewer = document.getElementById('team-production-inline-image-viewer');
		els.imageCounter = document.getElementById('team-production-inline-image-counter');
		els.imagePrevBtn = document.getElementById('team-production-inline-image-prev-btn');
		els.imageNextBtn = document.getElementById('team-production-inline-image-next-btn');
		els.imageCloseBtn = document.getElementById('team-production-inline-image-close-btn');
		els.imageOriginalBtn = document.getElementById('team-production-inline-image-original-btn');
		els.imageZoomInBtn = document.getElementById('team-production-inline-image-zoom-in-btn');
		els.imageZoomOutBtn = document.getElementById('team-production-inline-image-zoom-out-btn');
	}

	function bindEvents() {
		if (els.table) {
			els.table.addEventListener('click', handleTableClick);
		}

		if (els.imagePrevBtn) {
			els.imagePrevBtn.addEventListener('click', function() {
				moveImageModal(-1);
			});
		}

		if (els.imageNextBtn) {
			els.imageNextBtn.addEventListener('click', function() {
				moveImageModal(1);
			});
		}

		if (els.imageCloseBtn) {
			els.imageCloseBtn.addEventListener('click', closeImageModal);
		}

		if (els.imageOriginalBtn) {
			els.imageOriginalBtn.addEventListener('click', function() {
				state.zoom = 1;
				applyImageZoom();
			});
		}

		if (els.imageZoomInBtn) {
			els.imageZoomInBtn.addEventListener('click', function() {
				state.zoom = Math.min(4, state.zoom + 0.2);
				applyImageZoom();
			});
		}

		if (els.imageZoomOutBtn) {
			els.imageZoomOutBtn.addEventListener('click', function() {
				state.zoom = Math.max(0.3, state.zoom - 0.2);
				applyImageZoom();
			});
		}

		if (els.imageModal) {
			els.imageModal.addEventListener('click', function(event) {
				if (event.target === els.imageModal) {
					closeImageModal();
				}
			});
		}

		document.addEventListener('keydown', handleKeydown);
		document.addEventListener('team-production:order-completed', handleProductionCompleted);
	}

	function handleTableClick(event) {
		const inlineCompleteBtn = event.target.closest('[data-inline-complete-order="true"]');

		if (inlineCompleteBtn) {
			event.preventDefault();
			event.stopPropagation();

			const orderId = toText(inlineCompleteBtn.getAttribute('data-inline-complete-order-id'));

			if (orderId) {
				completeInlineOrderByExistingBulkButton(orderId);
			}

			return;
		}

		const detailToggleBtn = event.target.closest('.team-production-detail-toggle-btn');

		if (detailToggleBtn) {
			event.preventDefault();
			event.stopPropagation();

			const orderId = toText(detailToggleBtn.getAttribute('data-detail-order-id'));

			if (orderId) {
				toggleDetail(orderId, detailToggleBtn);
			}

			return;
		}

		const inlineImageMoveBtn = event.target.closest('[data-inline-image-move]');

		if (inlineImageMoveBtn) {
			event.preventDefault();
			event.stopPropagation();

			const orderId = toText(inlineImageMoveBtn.getAttribute('data-inline-image-order-id'));
			const direction = Number(inlineImageMoveBtn.getAttribute('data-inline-image-move')) || 0;

			moveInlineImage(orderId, direction);
			return;
		}

		const inlineThumbBtn = event.target.closest('[data-inline-image-index]');

		if (inlineThumbBtn) {
			event.preventDefault();
			event.stopPropagation();

			const orderId = toText(inlineThumbBtn.getAttribute('data-inline-image-order-id'));
			const index = Number(inlineThumbBtn.getAttribute('data-inline-image-index')) || 0;

			setInlineImageIndex(orderId, index);
			return;
		}

		const inlineImageOpen = event.target.closest('[data-inline-image-open="true"]');

		if (inlineImageOpen) {
			event.preventDefault();
			event.stopPropagation();

			const orderId = toText(inlineImageOpen.getAttribute('data-inline-image-order-id'));

			openImageModal(orderId);
			return;
		}

		const orderRow = event.target.closest('.team-production-clickable-row');

		if (!orderRow) {
			return;
		}

		if (isInteractiveClick(event.target)) {
			return;
		}

		const detailUrl = toText(orderRow.getAttribute('data-detail-url'));

		if (detailUrl) {
			window.location.href = detailUrl;
		}
	}



	function isInteractiveClick(target) {
		if (!target) {
			return false;
		}

		return Boolean(target.closest(
			'a, button, input, select, textarea, label, .team-production-detail-row, [data-inline-complete-order], [data-inline-image-open], [data-inline-image-index], [data-inline-image-move]'
		));
	}

	async function toggleDetail(orderId, button) {
		const detailRow = getDetailRow(orderId);

		if (!detailRow) {
			return;
		}

		const isOpen = detailRow.classList.contains('is-open');

		if (isOpen) {
			closeDetail(orderId);
			return;
		}

		closeAllDetails();

		openDetailRow(detailRow);
		setToggleButtonState(button, true);
		state.openedOrderId = orderId;

		if (state.orderCache.has(orderId)) {
			const cachedOrder = state.orderCache.get(orderId);
			markOrderViewed(orderId, cachedOrder);
			renderDetail(orderId, cachedOrder);
			scrollDetailIntoView(detailRow);
			return;
		}

		setDetailLoading(orderId);

		try {
			const order = await fetchOrderDetail(orderId);

			if (!order) {
				setDetailEmpty(orderId, '상세 데이터를 찾지 못했습니다.');
				return;
			}

			state.orderCache.set(orderId, order);
			markOrderViewed(orderId, order);
			renderDetail(orderId, order);
			scrollDetailIntoView(detailRow);
		} catch (error) {
			console.error(error);
			setDetailEmpty(orderId, '상세 데이터를 불러오지 못했습니다.');
		}
	}

	function openDetailRow(detailRow) {
		detailRow.style.display = '';

		requestAnimationFrame(function() {
			detailRow.classList.add('is-open');
		});
	}

	function closeDetail(orderId) {
		const detailRow = getDetailRow(orderId);

		if (!detailRow) {
			return;
		}

		detailRow.classList.remove('is-open');

		window.setTimeout(function() {
			if (!detailRow.classList.contains('is-open')) {
				detailRow.style.display = 'none';
			}
		}, 280);

		const button = getToggleButton(orderId);
		setToggleButtonState(button, false);

		if (state.openedOrderId === orderId) {
			state.openedOrderId = '';
		}
	}

	function closeAllDetails() {
		document.querySelectorAll('.team-production-detail-row.is-open').forEach(function(row) {
			const orderId = toText(row.getAttribute('data-detail-order-id'));
			closeDetail(orderId);
		});
	}

	function setToggleButtonState(button, opened) {
		if (!button) {
			return;
		}

		const label = opened ? '닫기' : '상세보기';
		let icon = button.querySelector('i');

		if (!icon) {
			icon = document.createElement('i');
			button.replaceChildren(icon);
		}

		icon.className = opened ? 'fa-solid fa-xmark' : 'fa-solid fa-circle-info';
		button.setAttribute('aria-expanded', opened ? 'true' : 'false');
		button.setAttribute('aria-label', label);
		button.title = label;
		button.classList.toggle('btn-primary', opened);
		button.classList.toggle('btn-outline-primary', !opened);
	}

	function getDetailRow(orderId) {
		return document.querySelector('.team-production-detail-row[data-detail-order-id="' + cssEscape(orderId) + '"]');
	}

	function getToggleButton(orderId) {
		return document.querySelector('.team-production-detail-toggle-btn[data-detail-order-id="' + cssEscape(orderId) + '"]');
	}

	function getDetailContent(orderId) {
		const row = getDetailRow(orderId);

		if (!row) {
			return null;
		}

		return row.querySelector('.team-production-inline-detail-content');
	}

	function setDetailLoading(orderId) {
		const content = getDetailContent(orderId);

		if (!content) {
			return;
		}

		content.innerHTML = '<div class="team-production-inline-loading">상세 정보를 불러오는 중입니다.</div>';
	}

	function setDetailEmpty(orderId, message) {
		const content = getDetailContent(orderId);

		if (!content) {
			return;
		}

		content.innerHTML = '<div class="team-production-inline-empty">' + escapeHtml(message || '상세 정보가 없습니다.') + '</div>';
	}

	async function fetchOrderDetail(orderId) {
		const url = buildDetailUrl(orderId);

		const response = await fetch(url, {
			method: 'GET',
			credentials: 'same-origin',
			headers: {
				'Accept': 'application/json'
			}
		});

		if (!response.ok) {
			throw new Error('상세 조회 실패 status=' + response.status);
		}

		const data = await response.json();
		const rawOrders = extractOrderArray(data);
		const rawOrder = rawOrders.length > 0 ? rawOrders[0] : null;

		return rawOrder ? normalizeOrder(rawOrder) : null;
	}

	function buildDetailUrl(orderId) {
		const fallbackUrl = '/team/productionList/overview-data';
		const baseUrl = config.overviewUrl || fallbackUrl;
		const url = new URL(baseUrl, window.location.origin);

		url.searchParams.set('orderIds', orderId);

		return url.toString();
	}

	function extractOrderArray(data) {
		if (Array.isArray(data)) {
			return data;
		}

		if (!data || typeof data !== 'object') {
			return [];
		}

		const candidates = [
			data.orders,
			data.items,
			data.rows,
			data.content,
			data.orderList,
			data.data,
			data.result
		];

		for (const candidate of candidates) {
			if (Array.isArray(candidate)) {
				return candidate;
			}

			if (candidate && typeof candidate === 'object') {
				const nested = extractOrderArray(candidate);

				if (nested.length > 0) {
					return nested;
				}
			}
		}

		return [];
	}

	function normalizeOrder(raw) {
		const orderItem = firstObject(raw.orderItem, raw.item, raw.order_item);
		const fields = normalizeFields(raw, orderItem);
		const optionFields = fields.filter(function(field) {
			return !isExcludedOptionLabel(field.label);
		});

		const order = {
			id: toText(firstValue(raw.orderId, raw.id, raw.order_id)),
			status: toText(firstValue(raw.status, raw.orderStatus, raw.statusName)),
			statusLabel: toText(firstValue(raw.statusLabel, raw.status_label)),
			companyName: toText(firstValue(raw.companyName, raw.clientCompanyName, raw.customerCompanyName)),
			productName: toText(firstValue(raw.productName, orderItem && orderItem.productName, findFieldValue(fields, ['제품명', 'productName']))),
			categoryName: toText(firstValue(raw.categoryName, raw.productCategoryName, findFieldValue(fields, ['제품분류', '카테고리', 'category']))),
			standardLabel: toText(firstValue(raw.standardLabel, findFieldValue(fields, ['규격여부', '규격', 'standard']))),
			quantity: toText(firstValue(raw.quantity, orderItem && orderItem.quantity, findFieldValue(fields, ['수량', 'quantity']))),
			createdDateText: formatDateText(firstValue(raw.createdDateText, raw.createdAt)),
			preferredDeliveryDateText: formatDateText(firstValue(raw.preferredDeliveryDateText, raw.preferredDeliveryDate, raw.deliveryDate)),
			orderComment: toText(firstValue(raw.orderComment, raw.memo, findFieldValue(fields, ['발주메모', 'orderComment']))),
			adminMemo: toText(firstValue(raw.adminMemo, findFieldValue(fields, ['관리자메모', 'adminMemo']))),
			address: toText(firstValue(raw.address, raw.fullAddress, findFieldValue(fields, ['주소', 'address', '배송지']))),
			fields: fields,
			options: optionFields,
			images: normalizeImages(raw),
			canComplete: Boolean(raw.canComplete),
			checkState: normalizeCheckState(raw),
			checkStateLabel: normalizeCheckStateLabel(raw),
			checked: isLatestCheckedRaw(raw),
			checkedByUsername: toText(raw.checkedByUsername),
			checkedAtText: toText(raw.checkedAtText),
			revisionMarkedByUsername: toText(raw.revisionMarkedByUsername),
			revisionMarkedAtText: toText(raw.revisionMarkedAtText),
			revisionReason: toText(raw.revisionReason),
			revisionCount: Number(raw.revisionCount || 0),
			inlineImageIndex: 0
		};

		if (!order.statusLabel) {
			order.statusLabel = normalizeStatusLabel(order.status);
		}

		return order;
	}

	function normalizeCheckState(raw) {
		if (window.TeamProductionOrderCheck && typeof window.TeamProductionOrderCheck.normalizeState === 'function') {
			return window.TeamProductionOrderCheck.normalizeState(raw);
		}

		const state = toText(firstValue(raw && raw.checkState, raw && raw.check_state)).toUpperCase();

		if (state === 'REVISED_AFTER_CHECK' || state === 'REVISED' || state === '재수정') {
			return 'REVISED_AFTER_CHECK';
		}

		if (state === 'CHECKED' || state === '확인') {
			return 'CHECKED';
		}

		if (raw && (raw.checked === true || raw.checked === 'true' || raw.checked === '1')) {
			return 'CHECKED';
		}

		return 'UNCHECKED';
	}

	function normalizeCheckStateLabel(raw) {
		const explicit = toText(firstValue(raw && raw.checkStateLabel, raw && raw.check_state_label));

		if (explicit) {
			return explicit;
		}

		const state = normalizeCheckState(raw);
		const labels = {
			REVISED_AFTER_CHECK: '재수정',
			UNCHECKED: '미확인',
			CHECKED: '확인'
		};

		return labels[state] || '미확인';
	}

	function isLatestCheckedRaw(raw) {
		return normalizeCheckState(raw) === 'CHECKED';
	}

	function markOrderObjectChecked(order) {
		if (window.TeamProductionOrderCheck && typeof window.TeamProductionOrderCheck.markObjectChecked === 'function') {
			return window.TeamProductionOrderCheck.markObjectChecked(order);
		}

		if (order) {
			order.checked = true;
			order.checkState = 'CHECKED';
			order.checkStateLabel = '확인';
		}

		return order;
	}

	function buildCheckStateBadgeHtml(order) {
		const state = normalizeCheckState(order);
		const label = normalizeCheckStateLabel(order);
		let className = 'badge bg-secondary-subtle text-secondary';

		if (state === 'CHECKED') {
			className = 'badge bg-success-subtle text-success';
		} else if (state === 'REVISED_AFTER_CHECK') {
			className = 'badge bg-warning-subtle text-warning';
		}

		return '<span class="' + className + '">' + escapeHtml(label) + '</span>';
	}

	function buildCheckStateMetaHtml(order) {
		const state = normalizeCheckState(order);

		if (state === 'CHECKED' && order && order.checkedByUsername) {
			return '<span class="team-production-inline-check-user">확인자: ' + escapeHtml(order.checkedByUsername) + '</span>';
		}

		if (state === 'REVISED_AFTER_CHECK') {
			const tokens = [];

			if (order && order.revisionMarkedByUsername) {
				tokens.push('수정자: ' + escapeHtml(order.revisionMarkedByUsername));
			}

			if (order && order.revisionMarkedAtText) {
				tokens.push('수정일: ' + escapeHtml(order.revisionMarkedAtText));
			}

			if (order && order.revisionReason) {
				tokens.push('사유: ' + escapeHtml(order.revisionReason));
			}

			return tokens.length > 0
				? '<span class="team-production-inline-check-user">' + tokens.join(' / ') + '</span>'
				: '<span class="team-production-inline-check-user">관리자 수정 후 재확인이 필요합니다.</span>';
		}

		return '';
	}

	function normalizeFields(raw, orderItem) {
		const fields = [];

		appendFields(fields, raw.fields);
		appendFields(fields, raw.optionFields);
		appendFields(fields, raw.options);
		appendFields(fields, raw.briefFields);
		appendFields(fields, raw.orderBriefFields);
		appendFields(fields, raw.optionList);
		appendFields(fields, raw.productOptions);

		appendMap(fields, raw.optionMap);
		appendMap(fields, raw.parsedOptionMap);
		appendMap(fields, raw.optionsMap);
		appendMap(fields, raw.productOptionMap);

		appendJsonMap(fields, raw.optionJson);
		appendTextBlock(fields, raw.formattedOptionText);
		appendTextBlock(fields, raw.optionText);
		appendTextBlock(fields, raw.productOptionText);

		if (orderItem) {
			appendFields(fields, orderItem.fields);
			appendFields(fields, orderItem.optionFields);
			appendFields(fields, orderItem.options);
			appendFields(fields, orderItem.optionList);
			appendFields(fields, orderItem.productOptions);

			appendMap(fields, orderItem.optionMap);
			appendMap(fields, orderItem.parsedOptionMap);
			appendMap(fields, orderItem.optionsMap);
			appendMap(fields, orderItem.productOptionMap);

			appendJsonMap(fields, orderItem.optionJson);
			appendTextBlock(fields, orderItem.formattedOptionText);
			appendTextBlock(fields, orderItem.optionText);
			appendTextBlock(fields, orderItem.productOptionText);
		}

		return dedupeFields(fields)
			.filter(function(field) {
				return field.label && field.value;
			})
			.map(function(field) {
				return {
					label: field.label,
					value: field.value,
					important: Boolean(field.important) || isImportantOptionLabel(field.label)
				};
			});
	}

	function appendFields(target, source) {
		if (!Array.isArray(source)) {
			return;
		}

		source.forEach(function(item) {
			if (!item) {
				return;
			}

			if (typeof item === 'string') {
				appendTextBlock(target, item);
				return;
			}

			if (typeof item === 'object') {
				const label = toText(firstValue(
					item.label,
					item.name,
					item.key,
					item.optionName,
					item.title,
					item.fieldLabel,
					item.fieldName,
					item.optionLabel,
					item.optionKey,
					item.displayName
				));

				const value = toText(firstValue(
					item.value,
					item.optionValue,
					item.text,
					item.content,
					item.fieldValue,
					item.displayValue,
					item.optionText
				));

				if (label || value) {
					target.push({
						label: label || '제품옵션',
						value: value || '-',
						important: Boolean(item.important)
					});
				}
			}
		});
	}

	function appendMap(target, source) {
		if (!source || typeof source !== 'object' || Array.isArray(source)) {
			return;
		}

		Object.keys(source).forEach(function(key) {
			const value = source[key];

			if (value === undefined || value === null || value === '') {
				return;
			}

			target.push({
				label: toText(key),
				value: flattenOptionValue(value),
				important: isImportantOptionLabel(key)
			});
		});
	}

	function appendJsonMap(target, jsonText) {
		if (!jsonText || typeof jsonText !== 'string') {
			return;
		}

		try {
			const parsed = JSON.parse(jsonText);

			if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
				appendMap(target, parsed);
				return;
			}

			if (Array.isArray(parsed)) {
				appendFields(target, parsed);
				return;
			}
		} catch (error) {
			appendTextBlock(target, jsonText);
		}
	}

	function appendTextBlock(target, text) {
		parseOptionTextBlock(text).forEach(function(field) {
			target.push(field);
		});
	}

	function parseOptionTextBlock(text) {
		const normalizedText = toText(text);

		if (!normalizedText || normalizedText === '-') {
			return [];
		}

		const chunks = normalizedText
			.replace(/\r\n/g, '\n')
			.replace(/\s+\/\s+/g, '\n')
			.replace(/\s+·\s+/g, '\n')
			.replace(/\s+\|\s+/g, '\n')
			.replace(/\s*;\s*/g, '\n')
			.split(/[\n,]/)
			.map(function(value) {
				return value.trim();
			})
			.filter(Boolean);

		return chunks.map(splitOptionText).filter(Boolean);
	}

	function splitOptionText(text) {
		const value = toText(text);

		if (!value || value === '-') {
			return null;
		}

		const separators = [':', '：', '=', ' - '];

		for (const separator of separators) {
			const index = value.indexOf(separator);

			if (index > 0) {
				const label = value.substring(0, index).trim();
				const optionValue = value.substring(index + separator.length).trim();

				if (label && optionValue) {
					return {
						label: label,
						value: optionValue
					};
				}
			}
		}

		return {
			label: '제품옵션',
			value: value
		};
	}

	function dedupeFields(fields) {
		const seen = new Set();
		const result = [];

		fields.forEach(function(field) {
			const label = toText(field.label);
			const value = toText(field.value);
			const key = label + '::' + value;

			if (!label || !value || seen.has(key)) {
				return;
			}

			seen.add(key);

			result.push({
				label: label,
				value: value,
				important: Boolean(field.important)
			});
		});

		return result;
	}

	function flattenOptionValue(value) {
		if (value === undefined || value === null) {
			return '';
		}

		if (Array.isArray(value)) {
			return value.map(flattenOptionValue).filter(Boolean).join(' / ');
		}

		if (typeof value === 'object') {
			return Object.keys(value).map(function(key) {
				const v = flattenOptionValue(value[key]);
				return v ? key + ': ' + v : '';
			}).filter(Boolean).join(' / ');
		}

		return toText(value);
	}

	function renderDetail(orderId, order) {
		const content = getDetailContent(orderId);

		if (!content || !order) {
			return;
		}

		content.innerHTML = buildDetailHtml(order);
	}

	function buildDetailHtml(order) {
		return [
			'<section class="team-production-inline-summary">',
			buildSummaryItemHtml('업체명', order.companyName || '-'),
			buildSummaryItemHtml('ID / 상태', '#' + (order.id || '-') + ' · ' + (order.statusLabel || '-')),
			buildSummaryItemHtml('제품명', order.productName || '-'),
			buildSummaryItemHtml('분류', order.categoryName || '-'),
			buildSummaryItemHtml('규격', order.standardLabel || '-'),
			buildSummaryItemHtml('배송예정일', order.preferredDeliveryDateText || '-'),
			'</section>',

			buildInlineActionHtml(order),

			'<section class="team-production-inline-memo-row">',
			buildMemoBoxHtml('발주 메모', order.orderComment || '-'),
			buildMemoBoxHtml('관리자 비고', order.adminMemo || '-'),
			'</section>',

			'<section class="team-production-inline-main">',
			buildOptionPanelHtml(order),
			buildImagePanelHtml(order),
			'</section>'
		].join('');
	}

	function buildSummaryItemHtml(label, value) {
		return [
			'<div class="team-production-inline-summary-item">',
			'<div class="team-production-inline-summary-label">' + escapeHtml(label) + '</div>',
			'<div class="team-production-inline-summary-value" title="' + escapeAttr(value || '-') + '">' + escapeHtml(value || '-') + '</div>',
			'</div>'
		].join('');
	}

	function buildMemoBoxHtml(label, value) {
		return [
			'<div class="team-production-inline-memo-box">',
			'<div class="team-production-inline-memo-label">' + escapeHtml(label) + '</div>',
			'<div class="team-production-inline-memo-value">' + escapeHtml(value || '-') + '</div>',
			'</div>'
		].join('');
	}

	function buildInlineActionHtml(order) {
		const completeState = getOrderCompleteState(order);
		const disabledAttr = completeState.available ? '' : ' disabled';

		return [
			'<section class="team-production-inline-action-row">',
			'<div class="team-production-inline-check-state">',
			buildCheckStateBadgeHtml(order),
			buildCheckStateMetaHtml(order),
			'</div>',
			'<button type="button"',
			' class="btn btn-success btn-sm team-production-inline-complete-btn"',
			' data-inline-complete-order="true"',
			' data-inline-complete-order-id="' + escapeAttr(order.id) + '"',
			' title="' + escapeAttr(completeState.message) + '"',
			disabledAttr,
			'>생산완료</button>',
			'</section>'
		].join('');
	}

	function buildOptionPanelHtml(order) {
		const options = order.options || [];

		if (options.length === 0) {
			return [
				'<div class="team-production-inline-panel">',
				'<div class="team-production-inline-panel-title">제품옵션 <small>없음</small></div>',
				'<div class="team-production-inline-option-empty">표시할 제품옵션이 없습니다.</div>',
				'</div>'
			].join('');
		}

		return [
			'<div class="team-production-inline-panel">',
			'<div class="team-production-inline-panel-title">제품옵션 <small>' + options.length + '개</small></div>',
			'<div class="team-production-inline-option-scroll">',
			'<div class="team-production-inline-option-grid">',
			options.map(buildOptionItemHtml).join(''),
			'</div>',
			'</div>',
			'</div>'
		].join('');
	}

	function buildOptionItemHtml(option) {
		const className = option.important
			? 'team-production-inline-option-item team-production-inline-option-item-important'
			: 'team-production-inline-option-item';

		return [
			'<div class="' + className + '">',
			'<div class="team-production-inline-option-label">' + escapeHtml(option.label || '옵션') + '</div>',
			'<div class="team-production-inline-option-value">' + escapeHtml(option.value || '-') + '</div>',
			'</div>'
		].join('');
	}

	function buildImagePanelHtml(order) {
		const images = order.images || [];
		const imageIndex = getInlineImageIndex(order);
		const image = images[imageIndex];
		const hasImages = images.length > 0;

		return [
			'<div class="team-production-inline-panel team-production-inline-image-panel" data-inline-image-panel-order-id="' + escapeAttr(order.id) + '">',
			'<div class="team-production-inline-panel-title">이미지 <small>' + (hasImages ? (imageIndex + 1) + ' / ' + images.length : '없음') + '</small></div>',
			'<div class="team-production-inline-image-main">',
			hasImages ? buildMainImageHtml(order.id, image) : '<div class="team-production-inline-image-empty">등록된 이미지가 없습니다.</div>',
			hasImages && images.length > 1 ? '<button type="button" class="team-production-inline-inner-image-nav team-production-inline-inner-image-prev" data-inline-image-order-id="' + escapeAttr(order.id) + '" data-inline-image-move="-1" aria-label="이전 이미지">‹</button>' : '',
			hasImages && images.length > 1 ? '<button type="button" class="team-production-inline-inner-image-nav team-production-inline-inner-image-next" data-inline-image-order-id="' + escapeAttr(order.id) + '" data-inline-image-move="1" aria-label="다음 이미지">›</button>' : '',
			'</div>',
			hasImages && images.length > 1 ? buildThumbsHtml(order.id, images, imageIndex) : '',
			'</div>'
		].join('');
	}

	function buildMainImageHtml(orderId, image) {
		return '<img src="' + escapeAttr(image.url) + '" alt="' + escapeAttr(image.name || '생산 발주 이미지') + '" data-inline-image-open="true" data-inline-image-order-id="' + escapeAttr(orderId) + '">';
	}

	function buildThumbsHtml(orderId, images, activeIndex) {
		return [
			'<div class="team-production-inline-image-thumbs">',
			images.map(function(image, index) {
				const activeClass = index === activeIndex ? ' active' : '';

				return [
					'<button type="button"',
					' class="team-production-inline-image-thumb' + activeClass + '"',
					' data-inline-image-order-id="' + escapeAttr(orderId) + '"',
					' data-inline-image-index="' + index + '"',
					' aria-label="이미지 ' + (index + 1) + '">',
					'<img src="' + escapeAttr(image.url) + '" alt="' + escapeAttr(image.name || '이미지') + '">',
					'</button>'
				].join('');
			}).join(''),
			'</div>'
		].join('');
	}

	function moveInlineImage(orderId, direction) {
		const order = state.orderCache.get(orderId);

		if (!order || !order.images || order.images.length === 0) {
			return;
		}

		order.inlineImageIndex = getLoopedImageIndex(getInlineImageIndex(order) + direction, order.images.length);
		updateInlineImagePreview(orderId);
	}

	function setInlineImageIndex(orderId, index) {
		const order = state.orderCache.get(orderId);

		if (!order || !order.images || order.images.length === 0) {
			return;
		}

		order.inlineImageIndex = clamp(index, 0, order.images.length - 1);
		updateInlineImagePreview(orderId);
	}

	function updateInlineImagePreview(orderId) {
		const order = state.orderCache.get(orderId);

		if (!order || !order.images || order.images.length === 0) {
			return;
		}

		const panel = document.querySelector('.team-production-inline-image-panel[data-inline-image-panel-order-id="' + cssEscape(orderId) + '"]');

		if (!panel) {
			return;
		}

		const imageIndex = getInlineImageIndex(order);
		const image = order.images[imageIndex];
		const mainImage = panel.querySelector('.team-production-inline-image-main img[data-inline-image-open="true"]');

		if (mainImage && image) {
			mainImage.src = image.url;
			mainImage.alt = image.name || '생산 발주 이미지';
		}

		const counter = panel.querySelector('.team-production-inline-panel-title small');

		if (counter) {
			counter.textContent = (imageIndex + 1) + ' / ' + order.images.length;
		}

		panel.querySelectorAll('.team-production-inline-image-thumb').forEach(function(thumb, index) {
			thumb.classList.toggle('active', index === imageIndex);
		});
	}

	function getInlineImageIndex(order) {
		if (!order) {
			return 0;
		}

		const maxIndex = Math.max(0, (order.images || []).length - 1);
		order.inlineImageIndex = clamp(Number(order.inlineImageIndex) || 0, 0, maxIndex);

		return order.inlineImageIndex;
	}

	function openImageModal(orderId) {
		const order = state.orderCache.get(orderId);

		if (!order || !order.images || order.images.length === 0 || !els.imageModal || !els.imageViewer) {
			return;
		}

		state.imageModalOpen = true;
		state.imageOrderId = orderId;
		state.imageIndex = getInlineImageIndex(order);
		state.zoom = 1;

		els.imageModal.classList.add('is-open');
		els.imageModal.setAttribute('aria-hidden', 'false');

		renderImageModal();
	}

	function closeImageModal() {
		state.imageModalOpen = false;
		state.zoom = 1;

		if (els.imageModal) {
			els.imageModal.classList.remove('is-open');
			els.imageModal.setAttribute('aria-hidden', 'true');
		}

		if (els.imageViewer) {
			els.imageViewer.removeAttribute('src');
			els.imageViewer.style.transform = 'scale(1)';
		}

		if (state.imageOrderId) {
			updateInlineImagePreview(state.imageOrderId);
		}
	}

	function renderImageModal() {
		const order = state.orderCache.get(state.imageOrderId);

		if (!order || !order.images || order.images.length === 0 || !els.imageViewer) {
			return;
		}

		state.imageIndex = clamp(state.imageIndex, 0, order.images.length - 1);
		order.inlineImageIndex = state.imageIndex;

		const image = order.images[state.imageIndex];

		els.imageViewer.src = image.url;
		els.imageViewer.alt = image.name || '생산 발주 이미지';

		applyImageZoom();

		if (els.imageCounter) {
			els.imageCounter.textContent = (state.imageIndex + 1) + ' / ' + order.images.length;
		}

		if (els.imagePrevBtn) {
			els.imagePrevBtn.disabled = order.images.length <= 1;
		}

		if (els.imageNextBtn) {
			els.imageNextBtn.disabled = order.images.length <= 1;
		}

		updateInlineImagePreview(state.imageOrderId);
	}

	function moveImageModal(direction) {
		const order = state.orderCache.get(state.imageOrderId);

		if (!order || !order.images || order.images.length === 0) {
			return;
		}

		state.imageIndex = getLoopedImageIndex(state.imageIndex + direction, order.images.length);
		order.inlineImageIndex = state.imageIndex;

		renderImageModal();
	}

	function applyImageZoom() {
		if (els.imageViewer) {
			els.imageViewer.style.transform = 'scale(' + state.zoom.toFixed(2) + ')';
		}
	}

	function handleKeydown(event) {
		if (!state.imageModalOpen) {
			return;
		}

		if (event.key === 'Escape') {
			closeImageModal();
			return;
		}

		if (event.key === 'ArrowLeft') {
			moveImageModal(-1);
			return;
		}

		if (event.key === 'ArrowRight') {
			moveImageModal(1);
		}
	}

	function normalizeImages(raw) {
		const result = [];

		const candidates = [
			raw.adminImages,
			raw.adminImageList,
			raw.managementImages,
			raw.managementImageList,
			raw.images,
			raw.orderImages,
			raw.orderImageList,
			raw.imageList,
			raw.imageUrls,
			raw.imageUrlList,
			raw.urls,
			raw.files,
			raw.fileList,
			raw.attachments,
			raw.attachmentList
		];

		candidates.forEach(function(candidate) {
			appendImageCandidate(result, candidate);
		});

		if (raw.orderItem) {
			appendImageCandidate(result, raw.orderItem.images);
			appendImageCandidate(result, raw.orderItem.orderImages);
			appendImageCandidate(result, raw.orderItem.imageList);
			appendImageCandidate(result, raw.orderItem.imageUrls);
		}

		return dedupeImages(result);
	}

	function appendImageCandidate(target, candidate) {
		if (!candidate) {
			return;
		}

		if (typeof candidate === 'string') {
			const normalized = normalizeImage(candidate);

			if (normalized) {
				target.push(normalized);
			}

			return;
		}

		if (Array.isArray(candidate)) {
			candidate.forEach(function(image) {
				const normalized = normalizeImage(image);

				if (normalized) {
					target.push(normalized);
				}
			});

			return;
		}

		if (typeof candidate === 'object') {
			const normalized = normalizeImage(candidate);

			if (normalized) {
				target.push(normalized);
			}
		}
	}

	function normalizeImage(image) {
		if (!image) {
			return null;
		}

		if (typeof image === 'string') {
			const url = normalizeImageUrl(image);

			if (!url) {
				return null;
			}

			return {
				url: url,
				name: '이미지'
			};
		}

		if (typeof image === 'object') {
			const url = normalizeImageUrl(firstValue(
				image.url,
				image.imageUrl,
				image.imageURL,
				image.fileUrl,
				image.fileURL,
				image.src,
				image.path,
				image.imagePath,
				image.image_path,
				image.filePath,
				image.file_path,
				image.storedPath,
				image.stored_path,
				image.uploadUrl,
				image.upload_url,
				image.downloadUrl,
				image.download_url
			));

			if (!url) {
				return null;
			}

			return {
				url: url,
				name: toText(firstValue(
					image.filename,
					image.fileName,
					image.originalFilename,
					image.originalFileName,
					image.name,
					image.type
				)) || '이미지'
			};
		}

		return null;
	}

	function normalizeImageUrl(value) {
		const url = toText(value).replace(/\\/g, '/');

		if (!url) {
			return '';
		}

		if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:')) {
			return url;
		}

		if (url.startsWith('/')) {
			return url;
		}

		return '/' + url;
	}

	function dedupeImages(images) {
		const seen = new Set();

		return images.filter(function(image) {
			if (!image.url || seen.has(image.url)) {
				return false;
			}

			seen.add(image.url);
			return true;
		});
	}

	function isExcludedOptionLabel(label) {
		const normalized = normalizeLabel(label);
		const excluded = [
			'id',
			'orderid',
			'제품명',
			'productname',
			'수량',
			'quantity',
			'주소',
			'address',
			'배송지',
			'발주메모',
			'ordermemo',
			'ordercomment',
			'관리자메모',
			'adminmemo',
			'memo',
			'제품분류',
			'카테고리',
			'category',
			'제품시리즈id',
			'seriesid',
			'규격여부',
			'규격',
			'standard'
		];

		return excluded.includes(normalized);
	}

	function isImportantOptionLabel(label) {
		const normalized = normalizeLabel(label);

		return normalized.includes('사이즈')
			|| normalized.includes('색상')
			|| normalized.includes('티슈')
			|| normalized.includes('드라이')
			|| normalized.includes('콘센트')
			|| normalized.includes('led')
			|| normalized.includes('옵션');
	}

	function findFieldValue(fields, labels) {
		if (!Array.isArray(fields)) {
			return '';
		}

		for (const field of fields) {
			const fieldLabel = normalizeLabel(field.label);
			const matched = labels.some(function(label) {
				const targetLabel = normalizeLabel(label);
				return fieldLabel === targetLabel || fieldLabel.includes(targetLabel);
			});

			if (matched) {
				return toText(field.value);
			}
		}

		return '';
	}

	function normalizeStatusLabel(status) {
		const statusMap = {
			REQUESTED: '고객 발주',
			CONFIRMED: '승인 완료',
			PRODUCTION_DONE: '생산 완료',
			DELIVERY_DONE: '배송 완료',
			CANCELED: '취소'
		};

		return statusMap[toText(status).toUpperCase()] || toText(status) || '-';
	}

	function scrollDetailIntoView(detailRow) {
		if (!detailRow) {
			return;
		}

		window.setTimeout(function() {
			detailRow.scrollIntoView({
				behavior: 'smooth',
				block: 'center'
			});
		}, 120);
	}

	function firstObject() {
		for (const value of arguments) {
			if (value && typeof value === 'object' && !Array.isArray(value)) {
				return value;
			}
		}

		return null;
	}

	function firstValue() {
		for (const value of arguments) {
			if (value !== undefined && value !== null && value !== '') {
				return value;
			}
		}

		return '';
	}

	function formatDateText(value) {
		const text = toText(value);

		if (!text) {
			return '-';
		}

		if (/^\d{4}-\d{2}-\d{2}/.test(text)) {
			return text.substring(0, 10);
		}

		return text;
	}

	function normalizeLabel(label) {
		return toText(label).replace(/\s+/g, '').toLowerCase();
	}

	function getLoopedImageIndex(index, total) {
		if (!total || total <= 0) {
			return 0;
		}

		if (index < 0) {
			return total - 1;
		}

		if (index >= total) {
			return 0;
		}

		return index;
	}

	function toText(value) {
		if (value === undefined || value === null) {
			return '';
		}

		if (typeof value === 'object') {
			return toText(firstValue(value.label, value.name, value.value, value.text));
		}

		return String(value).replace(/\s+/g, ' ').trim();
	}

	function clamp(value, min, max) {
		return Math.min(Math.max(value, min), max);
	}

	function escapeHtml(value) {
		return toText(value)
			.replace(/&/g, '&amp;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;')
			.replace(/"/g, '&quot;')
			.replace(/'/g, '&#039;');
	}

	function escapeAttr(value) {
		return escapeHtml(value);
	}

	function cssEscape(value) {
		const text = String(value || '');

		if (window.CSS && typeof window.CSS.escape === 'function') {
			return window.CSS.escape(text);
		}

		return text.replace(/([ #;?%&,.+*~':"!^$[\]()=>|/@])/g, '\\$1');
	}
	function markOrderViewed(orderId, order) {
		markOrderObjectChecked(order);

		if (window.TeamProductionOrderCheck && typeof window.TeamProductionOrderCheck.mark === 'function') {
			window.TeamProductionOrderCheck.mark(orderId);
		}
	}

	function handleProductionCompleted(event) {
		const detail = event && event.detail ? event.detail : null;
		const orderId = toText(detail && detail.orderId);

		if (!orderId) {
			return;
		}

		const order = state.orderCache.get(orderId);

		if (!order) {
			return;
		}

		order.status = toText(detail.status) || 'PRODUCTION_DONE';
		order.statusLabel = toText(detail.statusLabel) || '생산 완료';
		order.canComplete = false;

		if (state.openedOrderId === orderId) {
			renderDetail(orderId, order);
		}
	}

	function completeInlineOrderByExistingBulkButton(orderId) {
		const order = state.orderCache.get(orderId) || {
			id: orderId,
			status: getOrderStatusFromTable(orderId)
		};

		const completeState = getOrderCompleteState(order);

		if (!completeState.available) {
			alert(completeState.message);
			return;
		}

		triggerProductionCompleteForOrder(orderId);
	}

	function getOrderCompleteState(order) {
		if (!order || !order.id) {
			return {
				available: false,
				message: '주문 정보가 없습니다.'
			};
		}

		if (!isBulkCompleteAllowed()) {
			return {
				available: false,
				message: '생산완료 처리 권한이 없습니다.'
			};
		}

		if (!isCompletableOrderStatus(order)) {
			return {
				available: false,
				message: '승인 완료 상태의 주문만 생산완료 처리할 수 있습니다.'
			};
		}

		const checkbox = getOrderCheckbox(order.id);

		if (!checkbox || checkbox.disabled) {
			return {
				available: false,
				message: '현재 주문은 이 화면에서 생산완료 처리할 수 없습니다.'
			};
		}

		return {
			available: true,
			message: '생산완료 처리'
		};
	}

	function triggerProductionCompleteForOrder(orderId) {
		const currentCheckbox = getOrderCheckbox(orderId);
		const bulkButton = document.getElementById('team-production-bulk-done-btn');

		if (!currentCheckbox || currentCheckbox.disabled || !bulkButton) {
			alert('현재 주문은 이 화면에서 생산완료 처리할 수 없습니다.');
			return;
		}

		document.querySelectorAll('.team-production-check-item').forEach(function(checkbox) {
			checkbox.checked = false;
			checkbox.dispatchEvent(new Event('change', { bubbles: true }));
		});

		currentCheckbox.checked = true;
		currentCheckbox.dispatchEvent(new Event('change', { bubbles: true }));

		if (bulkButton.disabled) {
			alert('생산완료 처리 버튼이 활성화되지 않았습니다. 주문 상태 또는 권한을 확인해 주세요.');
			return;
		}

		bulkButton.click();
	}

	function getOrderCheckbox(orderId) {
		return document.querySelector('.team-production-check-item[data-order-id="' + cssEscape(orderId) + '"]');
	}

	function isBulkCompleteAllowed() {
		const flag = document.getElementById('team-production-can-bulk-complete');

		if (!flag) {
			return true;
		}

		return String(flag.value || '').trim().toLowerCase() === 'true';
	}

	function isCompletableOrderStatus(order) {
		const status = normalizeOrderStatus(order.status);

		if (status === 'CONFIRMED') {
			return true;
		}

		const statusLabel = toText(order.statusLabel).replace(/\s+/g, '');

		return statusLabel === '승인완료';
	}

	function getOrderStatusFromTable(orderId) {
		const checkbox = getOrderCheckbox(orderId);
		return checkbox ? toText(checkbox.getAttribute('data-status')) : '';
	}

	function normalizeOrderStatus(status) {
		return toText(status).replace(/\s+/g, '').toUpperCase();
	}
})();