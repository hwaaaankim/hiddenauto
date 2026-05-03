/* =========================================================
   생산팀 일괄보기
   /administration/assets/team/production/productionOverview.js
   ========================================================= */
(function() {
	'use strict';

	const config = window.teamProductionOverviewConfig || {};

	const els = {};
	const state = {
		orders: [],
		currentIndex: 0,
		loaded: false,
		isLoading: false,
		imageModalOpen: false,
		imageIndex: 0,
		zoom: 1
	};

	document.addEventListener('DOMContentLoaded', init);

	function init() {
		cacheElements();
		bindEvents();
	}

	function cacheElements() {
		els.openBtn = document.getElementById('team-production-overview-open-btn');
		els.modal = document.getElementById('team-production-overview-modal');
		els.host = document.getElementById('team-production-overview-slide-host');
		els.counter = document.getElementById('team-production-overview-counter');
		els.prevBtn = document.getElementById('team-production-overview-prev-btn');
		els.nextBtn = document.getElementById('team-production-overview-next-btn');
		els.completeBtn = document.getElementById('team-production-overview-complete-btn');

		els.imageModal = document.getElementById('team-production-overview-image-modal');
		els.imageViewer = document.getElementById('team-production-overview-image-viewer');
		els.imageCounter = document.getElementById('team-production-overview-image-counter');
		els.imagePrevBtn = document.getElementById('team-production-overview-image-prev-btn');
		els.imageNextBtn = document.getElementById('team-production-overview-image-next-btn');
		els.imageCloseBtn = document.getElementById('team-production-overview-image-close-btn');
		els.imageOriginalBtn = document.getElementById('team-production-overview-image-original-btn');
		els.imageZoomInBtn = document.getElementById('team-production-overview-image-zoom-in-btn');
		els.imageZoomOutBtn = document.getElementById('team-production-overview-image-zoom-out-btn');
	}

	function bindEvents() {
		if (els.openBtn) {
			els.openBtn.addEventListener('click', openOverviewModal);
		}

		if (els.prevBtn) {
			els.prevBtn.addEventListener('click', function() {
				moveOrder(-1);
			});
		}

		if (els.nextBtn) {
			els.nextBtn.addEventListener('click', function() {
				moveOrder(1);
			});
		}

		if (els.completeBtn) {
			els.completeBtn.addEventListener('click', completeCurrentOrderByExistingBulkButton);
		}

		if (els.host) {
			els.host.addEventListener('click', handleHostClick);
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
				state.zoom = Math.min(3, state.zoom + 0.2);
				applyImageZoom();
			});
		}

		if (els.imageZoomOutBtn) {
			els.imageZoomOutBtn.addEventListener('click', function() {
				state.zoom = Math.max(0.4, state.zoom - 0.2);
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

		if (els.modal) {
			els.modal.addEventListener('hidden.bs.modal', function() {
				closeImageModal();
			});
		}
	}

	function openOverviewModal() {
		if (!els.modal || !els.host) {
			return;
		}

		showBootstrapModal(els.modal);

		if (!state.loaded && !state.isLoading) {
			loadOverviewOrders();
			return;
		}

		renderCurrentOrder();
	}

	async function loadOverviewOrders() {
		state.isLoading = true;
		setHostLoading();

		try {
			const response = await fetch(buildOverviewUrl(), {
				method: 'GET',
				credentials: 'same-origin',
				headers: {
					'Accept': 'application/json'
				}
			});

			if (!response.ok) {
				throw new Error('일괄보기 데이터를 불러오지 못했습니다. status=' + response.status);
			}

			const data = await response.json();
			const normalizedOrders = normalizeOrders(data);

			state.orders = normalizedOrders.length > 0 ? normalizedOrders : buildOrdersFromCurrentTable();
			state.currentIndex = 0;
			state.loaded = true;

			renderCurrentOrder();
		} catch (error) {
			console.error(error);

			state.orders = buildOrdersFromCurrentTable();
			state.currentIndex = 0;
			state.loaded = true;

			if (state.orders.length > 0) {
				renderCurrentOrder();
				return;
			}

			setHostEmpty('일괄보기 데이터를 불러오지 못했습니다.');
		} finally {
			state.isLoading = false;
		}
	}

	function buildOverviewUrl() {
		const fallbackUrl = '/team/productionList/overview-data';
		const baseUrl = config.overviewUrl || fallbackUrl;
		const url = new URL(baseUrl, window.location.origin);
		const currentParams = new URLSearchParams(window.location.search || '');

		currentParams.forEach(function(value, key) {
			if (!url.searchParams.has(key)) {
				url.searchParams.set(key, value);
			}
		});

		const currentOrderIds = getCurrentTableOrderIds();

		if (currentOrderIds.length > 0 && !url.searchParams.has('orderIds')) {
			url.searchParams.set('orderIds', currentOrderIds.join(','));
		}

		return url.toString();
	}

	function getCurrentTableOrderIds() {
		return Array.from(document.querySelectorAll('.team-production-overview-row[data-overview-order-id]'))
			.map(function(row) {
				return String(row.getAttribute('data-overview-order-id') || '').trim();
			})
			.filter(Boolean);
	}

	function normalizeOrders(data) {
		const rawOrders = extractOrderArray(data);

		return rawOrders.map(normalizeOrder).filter(function(order) {
			return order && order.id !== '';
		});
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
		const statusRaw = firstValue(raw.status, raw.orderStatus, raw.statusName);
		const standardRaw = firstValue(raw.standard, raw.isStandard, raw.standardYn, raw.standardLabel);

		const order = {
			id: toText(firstValue(raw.id, raw.orderId, raw.order_id)),
			status: toText(statusRaw),
			statusLabel: normalizeStatusLabel(statusRaw, raw.statusLabel, raw.status_label),
			companyName: toText(firstValue(
				raw.companyName,
				raw.clientCompanyName,
				raw.customerCompanyName,
				raw.requestedCompanyName,
				raw.company && raw.company.companyName,
				raw.requestedBy && raw.requestedBy.company && raw.requestedBy.company.companyName
			)),
			address: normalizeAddress(raw) || findFieldValue(raw.fields, ['주소', 'address', '배송지']),
			productName: toText(firstValue(raw.productName, orderItem && orderItem.productName)),
			productCategoryName: toText(firstValue(
				raw.productCategoryName,
				raw.categoryName,
				raw.productCategory && raw.productCategory.name,
				raw.category && raw.category.name
			)),
			productSeries: toText(firstValue(raw.productSeries, raw.productSeriesName, raw.seriesName)),
			quantity: toText(firstValue(raw.quantity, orderItem && orderItem.quantity)),
			standardLabel: normalizeStandardLabel(standardRaw),
			dateText: normalizeDateText(raw),
			options: normalizeOptionFields(raw, orderItem),
			images: normalizeImages(raw)
		};

		if (!order.productSeries) {
			order.productSeries = findOptionValue(order.options, ['제품시리즈', '시리즈', 'productSeries', 'series']);
		}

		return order;
	}
	function findFieldValue(fields, labels) {
		if (!Array.isArray(fields)) {
			return '';
		}

		for (const field of fields) {
			if (!field || typeof field !== 'object') {
				continue;
			}

			const fieldLabel = normalizeLabel(firstValue(
				field.label,
				field.name,
				field.key,
				field.optionName,
				field.fieldLabel,
				field.fieldName
			));

			const matched = labels.some(function(label) {
				const targetLabel = normalizeLabel(label);
				return fieldLabel === targetLabel || fieldLabel.includes(targetLabel);
			});

			if (matched) {
				return toText(firstValue(
					field.value,
					field.optionValue,
					field.text,
					field.content,
					field.fieldValue,
					field.displayValue
				));
			}
		}

		return '';
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

	function normalizeStatusLabel(statusRaw) {
		const explicit = Array.from(arguments).slice(1).find(function(value) {
			return value !== undefined && value !== null && value !== '';
		});

		if (explicit) {
			return toText(explicit);
		}

		if (statusRaw && typeof statusRaw === 'object') {
			return toText(firstValue(statusRaw.label, statusRaw.displayName, statusRaw.name));
		}

		const status = toText(statusRaw);
		const statusMap = {
			REQUESTED: '고객 발주',
			CONFIRMED: '승인 완료',
			PRODUCTION_DONE: '생산 완료',
			DELIVERY_DONE: '배송 완료',
			CANCELED: '취소'
		};

		return statusMap[status] || status || '-';
	}

	function normalizeStandardLabel(value) {
		if (value === true || value === 'true' || value === 'Y' || value === 'YES' || value === 'STANDARD' || value === '규격') {
			return '규격';
		}

		if (value === false || value === 'false' || value === 'N' || value === 'NO' || value === 'NON_STANDARD' || value === '비규격') {
			return '비규격';
		}

		return toText(value) || '-';
	}

	function normalizeAddress(raw) {
		const direct = firstValue(raw.address, raw.fullAddress, raw.deliveryAddress, raw.originAddress, raw.origin_address);

		if (direct) {
			return toText(direct);
		}

		const parts = [
			raw.zipCode || raw.zip_code,
			raw.doName || raw.do_name,
			raw.siName || raw.si_name,
			raw.guName || raw.gu_name,
			raw.roadAddress || raw.road_address,
			raw.detailAddress || raw.detail_address
		]
			.map(toText)
			.filter(Boolean);

		return parts.join(' ');
	}

	function normalizeDateText(raw) {
		const date = firstValue(
			raw.dateText,
			raw.displayDate,
			raw.preferredDeliveryDateText,
			raw.createdDateText,
			raw.preferredDeliveryDate,
			raw.deliveryDate,
			raw.createdAt,
			raw.orderDate
		);

		return formatDateText(date);
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

	function normalizeOptionFields(raw, orderItem) {
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
			appendFields(fields, raw.fields);
			appendFields(fields, raw.optionFields);
			appendFields(fields, raw.options);
			appendFields(fields, raw.briefFields);
			appendFields(fields, raw.orderBriefFields);
			appendFields(fields, raw.optionList);
			appendFields(fields, raw.productOptions);

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
				return field.label && field.value && !isExcludedOptionLabel(field.label);
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
				value: toText(value),
				important: isImportantOptionLabel(key)
			});
		});
	}
	function appendTextBlock(target, text) {
		parseOptionTextBlock(text).forEach(function(field) {
			target.push(field);
		});
	}
	function appendJsonMap(target, jsonText) {
		if (!jsonText || typeof jsonText !== 'string') {
			return;
		}

		try {
			const parsed = JSON.parse(jsonText);
			appendMap(target, parsed);
		} catch (error) {
			parseOptionTextBlock(jsonText).forEach(function(field) {
				target.push(field);
			});
		}
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

		const known = extractKnownOptionsFromPlainText(value);

		if (known.length === 1) {
			return known[0];
		}

		return {
			label: '제품옵션',
			value: value
		};
	}

	function parseOptionTextBlock(text) {
		const normalizedText = toText(text);

		if (!normalizedText || normalizedText === '-') {
			return [];
		}

		const jsonLike = tryParseOptionJsonText(normalizedText);

		if (jsonLike.length > 0) {
			return jsonLike;
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

		const result = [];

		chunks.forEach(function(chunk) {
			const parsed = splitOptionText(chunk);

			if (parsed) {
				result.push(parsed);
			}
		});

		if (result.length > 0) {
			return result;
		}

		return extractKnownOptionsFromPlainText(normalizedText);
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

	function normalizeLabel(label) {
		return toText(label).replace(/\s+/g, '').toLowerCase();
	}

	function findOptionValue(options, labels) {
		for (const option of options || []) {
			const normalized = normalizeLabel(option.label);
			const matched = labels.some(function(label) {
				return normalized === normalizeLabel(label) || normalized.includes(normalizeLabel(label));
			});

			if (matched) {
				return option.value;
			}
		}

		return '';
	}

	function normalizeImages(raw) {
		const result = [];

		const candidates = [
			raw.images,
			raw.orderImages,
			raw.orderImageList,
			raw.managementImages,
			raw.managementImageList,
			raw.adminImages,
			raw.adminImageList,
			raw.imageList,
			raw.imageUrls,
			raw.imageUrlList,
			raw.managementImageUrls,
			raw.managementImageUrlList,
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

	function buildOrdersFromCurrentTable() {
		return Array.from(document.querySelectorAll('.team-production-overview-row')).map(function(row) {
			const cells = row.querySelectorAll('td');
			const optionText = getText(row.querySelector('.team-production-list-option-line')) || getText(cells[3]);
			const optionFields = parseOptionTextBlock(optionText);

			return {
				id: toText(row.getAttribute('data-overview-order-id')) || getText(cells[0]),
				status: toText(row.getAttribute('data-overview-status')),
				statusLabel: getText(cells[1]),
				companyName: getText(cells[2]),
				address: '',
				productName: getText(cells[4]),
				productCategoryName: '',
				productSeries: '',
				quantity: '',
				standardLabel: getText(cells[5]),
				dateText: getText(cells[6]),
				options: optionFields.length > 0 ? optionFields : [
					{
						label: '제품옵션',
						value: optionText || '-'
					}
				],
				images: []
			};
		}).filter(function(order) {
			return order.id;
		});
	}

	function renderCurrentOrder() {
		if (!els.host) {
			return;
		}

		if (state.orders.length === 0) {
			setHostEmpty('조회된 생산 주문이 없습니다.');
			updateNavigationState();
			return;
		}

		state.currentIndex = clamp(state.currentIndex, 0, state.orders.length - 1);

		const order = state.orders[state.currentIndex];
		const imageIndex = getOrderImageIndex(order);
		const image = order.images[imageIndex];

		els.host.innerHTML = buildOrderCardHtml(order, image, imageIndex);

		updateNavigationState();
		updateOptionOverflow();
	}

	function buildOrderCardHtml(order, image, imageIndex) {
		return [
			'<article class="team-production-overview-order-card" data-order-id="' + escapeAttr(order.id) + '">',
			buildTopInfoHtml(order),
			'<div class="team-production-overview-main-row">',
			buildOptionsPanelHtml(order),
			buildImagePanelHtml(order, image, imageIndex),
			'</div>',
			'</article>'
		].join('');
	}

	function buildTopInfoHtml(order) {
		return [
			'<section class="team-production-overview-top-info">',
			'<div class="team-production-overview-top-item team-production-overview-top-item-wide">',
			'<div class="team-production-overview-top-label">업체정보</div>',
			'<div class="team-production-overview-top-value team-production-overview-top-value-main" title="' + escapeAttr(order.companyName || '-') + '">' + escapeHtml(order.companyName || '-') + '</div>',
			'<div class="team-production-overview-address" title="' + escapeAttr(order.address || '-') + '">' + escapeHtml(order.address || '-') + '</div>',
			'</div>',
			buildTopItemHtml('ID / 상태', '#' + (order.id || '-') + ' · ' + (order.statusLabel || '-')),
			buildTopItemHtml('제품명', order.productName || '-'),
			buildTopItemHtml('제품분류', order.productCategoryName || '-'),
			buildTopItemHtml('제품시리즈', order.productSeries || '-'),
			buildTopItemHtml('규격', order.standardLabel || '-'),
			buildTopItemHtml('일자', order.dateText || '-'),
			'</section>'
		].join('');
	}

	function buildTopItemHtml(label, value) {
		return [
			'<div class="team-production-overview-top-item">',
			'<div class="team-production-overview-top-label">' + escapeHtml(label) + '</div>',
			'<div class="team-production-overview-top-value" title="' + escapeAttr(value || '-') + '">' + escapeHtml(value || '-') + '</div>',
			'</div>'
		].join('');
	}

	function buildOptionsPanelHtml(order) {
		const options = order.options || [];
		const countText = options.length > 0 ? options.length + '개' : '없음';

		if (options.length === 0) {
			return [
				'<section class="team-production-overview-panel team-production-overview-option-panel">',
				'<div class="team-production-overview-panel-title">제품옵션 <small>' + countText + '</small></div>',
				'<div class="team-production-overview-option-empty">표시할 제품옵션이 없습니다.</div>',
				'</section>'
			].join('');
		}

		return [
			'<section class="team-production-overview-panel team-production-overview-option-panel">',
			'<div class="team-production-overview-panel-title">제품옵션 <small>' + countText + '</small></div>',
			'<div class="team-production-overview-option-scroll">',
			'<div class="team-production-overview-option-grid">',
			options.map(buildOptionItemHtml).join(''),
			'</div>',
			'</div>',
			'</section>'
		].join('');
	}

	function buildOptionItemHtml(option) {
		const className = option.important
			? 'team-production-overview-option-item team-production-overview-option-item-important'
			: 'team-production-overview-option-item';

		return [
			'<div class="' + className + '">',
			'<div class="team-production-overview-option-label">' + escapeHtml(option.label || '옵션') + '</div>',
			'<div class="team-production-overview-option-value">' + escapeHtml(option.value || '-') + '</div>',
			'</div>'
		].join('');
	}

	function buildImagePanelHtml(order, image, imageIndex) {
		const images = order.images || [];
		const hasImages = images.length > 0;

		return [
			'<section class="team-production-overview-panel team-production-overview-image-panel">',
			'<div class="team-production-overview-panel-title">이미지 <small>' + (hasImages ? (imageIndex + 1) + ' / ' + images.length : '없음') + '</small></div>',
			'<div class="team-production-overview-image-main">',
			hasImages ? buildMainImageHtml(image) : '<div class="team-production-overview-image-empty">등록된 이미지가 없습니다.</div>',
			hasImages && images.length > 1 ? '<button type="button" class="team-production-overview-inner-image-nav team-production-overview-inner-image-prev" data-image-move="-1" aria-label="이전 이미지">‹</button>' : '',
			hasImages && images.length > 1 ? '<button type="button" class="team-production-overview-inner-image-nav team-production-overview-inner-image-next" data-image-move="1" aria-label="다음 이미지">›</button>' : '',
			'</div>',
			hasImages && images.length > 1 ? buildThumbsHtml(images, imageIndex) : '',
			'</section>'
		].join('');
	}

	function buildMainImageHtml(image) {
		return '<img src="' + escapeAttr(image.url) + '" alt="' + escapeAttr(image.name || '관리자 업로드 이미지') + '" data-image-open="true">';
	}

	function buildThumbsHtml(images, activeIndex) {
		return [
			'<div class="team-production-overview-image-thumbs">',
			images.map(function(image, index) {
				const activeClass = index === activeIndex ? ' active' : '';

				return '<button type="button" class="team-production-overview-image-thumb' + activeClass + '" data-image-index="' + index + '" aria-label="이미지 ' + (index + 1) + '">' +
					'<img src="' + escapeAttr(image.url) + '" alt="' + escapeAttr(image.name || '이미지') + '">' +
					'</button>';
			}).join(''),
			'</div>'
		].join('');
	}

	function updateOptionOverflow() {
		requestAnimationFrame(function() {
			const scrollBox = els.host && els.host.querySelector('.team-production-overview-option-scroll');

			if (!scrollBox) {
				return;
			}

			const needsScroll = scrollBox.scrollHeight > scrollBox.clientHeight + 2;
			scrollBox.classList.toggle('has-overflow', needsScroll);
		});
	}

	function handleHostClick(event) {
		const imageMoveBtn = event.target.closest('[data-image-move]');

		if (imageMoveBtn) {
			event.preventDefault();
			moveCurrentOrderImage(Number(imageMoveBtn.getAttribute('data-image-move')) || 0);
			return;
		}

		const thumbBtn = event.target.closest('[data-image-index]');

		if (thumbBtn) {
			event.preventDefault();
			setCurrentOrderImageIndex(Number(thumbBtn.getAttribute('data-image-index')) || 0);
			return;
		}

		const openImage = event.target.closest('[data-image-open="true"]');

		if (openImage) {
			event.preventDefault();
			openImageModal();
		}
	}

	function moveOrder(direction) {
		if (state.orders.length === 0) {
			return;
		}

		const nextIndex = state.currentIndex + direction;

		if (nextIndex < 0 || nextIndex >= state.orders.length) {
			return;
		}

		state.currentIndex = nextIndex;
		renderCurrentOrder();
	}

	function updateNavigationState() {
		const total = state.orders.length;
		const current = total > 0 ? state.currentIndex + 1 : 0;

		if (els.counter) {
			els.counter.textContent = current + ' / ' + total;
		}

		if (els.prevBtn) {
			els.prevBtn.disabled = total === 0 || state.currentIndex <= 0;
		}

		if (els.nextBtn) {
			els.nextBtn.disabled = total === 0 || state.currentIndex >= total - 1;
		}

		if (els.completeBtn) {
			const order = getCurrentOrder();
			const checkbox = order ? document.querySelector('.team-production-check-item[data-order-id="' + cssEscape(order.id) + '"]') : null;
			els.completeBtn.disabled = !checkbox || checkbox.disabled;
		}
	}

	function getCurrentOrder() {
		return state.orders[state.currentIndex] || null;
	}

	function getOrderImageIndex(order) {
		if (!order) {
			return 0;
		}

		const maxIndex = Math.max(0, (order.images || []).length - 1);

		order.imageIndex = clamp(Number(order.imageIndex) || 0, 0, maxIndex);

		return order.imageIndex;
	}

	function moveCurrentOrderImage(direction) {
		const order = getCurrentOrder();

		if (!order || !order.images || order.images.length === 0) {
			return;
		}

		order.imageIndex = getLoopedImageIndex(getOrderImageIndex(order) + direction, order.images.length);

		updateCurrentOrderImagePreviewOnly(order);
	}

	function setCurrentOrderImageIndex(index) {
		const order = getCurrentOrder();

		if (!order || !order.images || order.images.length === 0) {
			return;
		}

		order.imageIndex = clamp(index, 0, order.images.length - 1);

		updateCurrentOrderImagePreviewOnly(order);
	}

	function openImageModal() {
		const order = getCurrentOrder();

		if (!order || !order.images || order.images.length === 0 || !els.imageModal || !els.imageViewer) {
			return;
		}

		state.imageModalOpen = true;
		state.imageIndex = getOrderImageIndex(order);
		state.zoom = 1;

		els.imageModal.classList.add('is-open');
		els.imageModal.setAttribute('aria-hidden', 'false');

		renderImageModal();
	}

	function closeImageModal() {
		const order = getCurrentOrder();

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

		if (order) {
			updateCurrentOrderImagePreviewOnly(order);
		}
	}

	function renderImageModal() {
		const order = getCurrentOrder();

		if (!order || !order.images || order.images.length === 0 || !els.imageViewer) {
			return;
		}

		state.imageIndex = clamp(state.imageIndex, 0, order.images.length - 1);
		order.imageIndex = state.imageIndex;

		const image = order.images[state.imageIndex];

		els.imageViewer.src = image.url;
		els.imageViewer.alt = image.name || '관리자 업로드 이미지';

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
	}

	function moveImageModal(direction) {
		const order = getCurrentOrder();

		if (!order || !order.images || order.images.length === 0) {
			return;
		}

		state.imageIndex = getLoopedImageIndex(state.imageIndex + direction, order.images.length);
		order.imageIndex = state.imageIndex;

		renderImageModal();
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

	function updateCurrentOrderImagePreviewOnly(order) {
		if (!els.host || !order || !order.images || order.images.length === 0) {
			return;
		}

		const imageIndex = getOrderImageIndex(order);
		const image = order.images[imageIndex];

		const mainImage = els.host.querySelector('.team-production-overview-image-main img[data-image-open="true"]');

		if (mainImage && image) {
			mainImage.src = image.url;
			mainImage.alt = image.name || '관리자 업로드 이미지';
		}

		const panelTitleSmall = els.host.querySelector('.team-production-overview-image-panel .team-production-overview-panel-title small');

		if (panelTitleSmall) {
			panelTitleSmall.textContent = (imageIndex + 1) + ' / ' + order.images.length;
		}

		els.host.querySelectorAll('.team-production-overview-image-thumb').forEach(function(thumb, index) {
			thumb.classList.toggle('active', index === imageIndex);
		});
	}

	function applyImageZoom() {
		if (els.imageViewer) {
			els.imageViewer.style.transform = 'scale(' + state.zoom.toFixed(2) + ')';
		}
	}

	function handleKeydown(event) {
		if (state.imageModalOpen) {
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

			return;
		}

		if (!isOverviewModalVisible()) {
			return;
		}

		if (event.key === 'ArrowLeft') {
			moveOrder(-1);
		}

		if (event.key === 'ArrowRight') {
			moveOrder(1);
		}
	}

	function isOverviewModalVisible() {
		return Boolean(els.modal && els.modal.classList.contains('show'));
	}

	function completeCurrentOrderByExistingBulkButton() {
		const order = getCurrentOrder();

		if (!order) {
			return;
		}

		const currentCheckbox = document.querySelector('.team-production-check-item[data-order-id="' + cssEscape(order.id) + '"]');
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

		bulkButton.click();
	}

	function setHostLoading() {
		if (!els.host) {
			return;
		}

		els.host.innerHTML = '<div class="team-production-overview-loading">일괄보기 데이터를 불러오는 중입니다.</div>';

		updateNavigationState();
	}

	function setHostEmpty(message) {
		if (!els.host) {
			return;
		}

		els.host.innerHTML = '<div class="team-production-overview-empty">' + escapeHtml(message || '조회된 생산 주문이 없습니다.') + '</div>';

		if (els.counter) {
			els.counter.textContent = '0 / 0';
		}

		updateNavigationState();
	}

	function showBootstrapModal(modalEl) {
		if (window.bootstrap && window.bootstrap.Modal) {
			window.bootstrap.Modal.getOrCreateInstance(modalEl).show();
			return;
		}

		modalEl.classList.add('show');
		modalEl.style.display = 'block';
		modalEl.removeAttribute('aria-hidden');
		document.body.classList.add('modal-open');
	}

	function getText(element) {
		return element ? toText(element.textContent) : '';
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
	function tryParseOptionJsonText(text) {
		try {
			const parsed = JSON.parse(text);
			const result = [];

			if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
				appendMap(result, parsed);
				return result;
			}

			if (Array.isArray(parsed)) {
				appendFields(result, parsed);
				return result;
			}
		} catch (error) {
			// JSON 문자열이 아니면 일반 텍스트 파싱으로 진행
		}

		return [];
	}

	function extractKnownOptionsFromPlainText(text) {
		const source = toText(text);

		if (!source) {
			return [];
		}

		const labels = [
			'제품시리즈',
			'시리즈',
			'사이즈',
			'색상',
			'티슈위치',
			'드라이걸이',
			'콘센트',
			'LED',
			'타공',
			'도어',
			'문짝',
			'상판',
			'하부장',
			'세면대',
			'수전',
			'거울',
			'옵션'
		];

		const result = [];
		const escapedLabels = labels.map(escapeRegExp).join('|');

		const pattern = new RegExp(
			'(' + escapedLabels + ')\\s*[:：=]?\\s*([^\\n,|/;·]+?)(?=\\s*(?:' + escapedLabels + ')\\s*[:：=]?|$)',
			'gi'
		);

		let match;

		while ((match = pattern.exec(source)) !== null) {
			const label = toText(match[1]);
			const value = toText(match[2]);

			if (label && value) {
				result.push({
					label: label,
					value: value,
					important: isImportantOptionLabel(label)
				});
			}
		}

		return result;
	}

	function escapeRegExp(value) {
		return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
	}
	function cssEscape(value) {
		const text = String(value || '');

		if (window.CSS && typeof window.CSS.escape === 'function') {
			return window.CSS.escape(text);
		}

		return text.replace(/([ #;?%&,.+*~':"!^$[\]()=>|/@])/g, '\\$1');
	}
})();