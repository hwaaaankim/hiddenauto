/* =========================================================
   생산팀 일괄보기 - 리스트형
   /administration/assets/team/production/productionOverviewList.js
   ========================================================= */
(function() {
	'use strict';

	const config = window.teamProductionOverviewConfig || {};

	const els = {};
	const state = {
		orders: [],
		loaded: false,
		isLoading: false,
		imageModalOpen: false,
		imageOrderIndex: 0,
		imageIndex: 0,
		zoom: 1
	};

	document.addEventListener('DOMContentLoaded', init);

	function init() {
		cacheElements();
		bindEvents();
	}

	function cacheElements() {
		els.openBtn = document.getElementById('team-production-overview-list-open-btn');
		els.modal = document.getElementById('team-production-overview-list-modal');
		els.host = document.getElementById('team-production-overview-list-host');
		els.counter = document.getElementById('team-production-overview-list-counter');
		els.refreshBtn = document.getElementById('team-production-overview-list-refresh-btn');

		els.imageModal = document.getElementById('team-production-overview-list-image-modal');
		els.imageViewer = document.getElementById('team-production-overview-list-image-viewer');
		els.imageCounter = document.getElementById('team-production-overview-list-image-counter');
		els.imagePrevBtn = document.getElementById('team-production-overview-list-image-prev-btn');
		els.imageNextBtn = document.getElementById('team-production-overview-list-image-next-btn');
		els.imageCloseBtn = document.getElementById('team-production-overview-list-image-close-btn');
		els.imageOriginalBtn = document.getElementById('team-production-overview-list-image-original-btn');
		els.imageZoomInBtn = document.getElementById('team-production-overview-list-image-zoom-in-btn');
		els.imageZoomOutBtn = document.getElementById('team-production-overview-list-image-zoom-out-btn');
	}

	function bindEvents() {
		if (els.openBtn) {
			els.openBtn.addEventListener('click', openListModal);
		}

		if (els.refreshBtn) {
			els.refreshBtn.addEventListener('click', function() {
				state.loaded = false;
				loadListOrders();
			});
		}

		if (els.host) {
			els.host.addEventListener('click', handleListHostClick);
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

	function openListModal() {
		if (!els.modal || !els.host) {
			return;
		}

		showBootstrapModal(els.modal);

		if (!state.loaded && !state.isLoading) {
			loadListOrders();
			return;
		}

		renderListOrders();
	}

	async function loadListOrders() {
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
				throw new Error('리스트형 일괄보기 데이터를 불러오지 못했습니다. status=' + response.status);
			}

			const data = await response.json();
			const normalizedOrders = normalizeOrders(data);

			state.orders = normalizedOrders.length > 0 ? normalizedOrders : buildOrdersFromCurrentTable();
			state.loaded = true;

			renderListOrders();
		} catch (error) {
			console.error(error);

			state.orders = buildOrdersFromCurrentTable();
			state.loaded = true;

			if (state.orders.length > 0) {
				renderListOrders();
				return;
			}

			setHostEmpty('리스트형 일괄보기 데이터를 불러오지 못했습니다.');
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
		const fields = normalizeOptionFields(raw, orderItem);

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
			productName: toText(firstValue(raw.productName, orderItem && orderItem.productName, findFieldValue(raw.fields, ['제품명', 'productName']))),
			productCategoryName: toText(firstValue(
				raw.categoryName,
				raw.productCategoryName,
				raw.productCategory && raw.productCategory.name,
				raw.category && raw.category.name,
				findFieldValue(raw.fields, ['제품분류', '카테고리', 'category'])
			)),
			standardLabel: normalizeStandardLabel(firstValue(standardRaw, findFieldValue(raw.fields, ['규격여부', '규격', 'standard']))),
			quantity: toText(firstValue(raw.quantity, orderItem && orderItem.quantity, findFieldValue(raw.fields, ['수량', 'quantity']))),
			createdDateText: formatDateText(firstValue(raw.createdDateText, raw.createdAt, raw.orderDate)),
			preferredDeliveryDateText: formatDateText(firstValue(raw.preferredDeliveryDateText, raw.preferredDeliveryDate, raw.deliveryDate)),
			dateText: formatDateText(firstValue(raw.preferredDeliveryDateText, raw.preferredDeliveryDate, raw.createdDateText, raw.createdAt, raw.deliveryDate, raw.orderDate)),
			orderComment: toText(firstValue(raw.orderComment, raw.memo, findFieldValue(raw.fields, ['발주메모', 'orderComment']))),
			adminMemo: toText(firstValue(raw.adminMemo, findFieldValue(raw.fields, ['관리자메모', 'adminMemo']))),
			options: fields,
			images: normalizeImages(raw),
			listImageIndex: 0
		};

		return order;
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
			parseOptionTextBlock(jsonText).forEach(function(field) {
				target.push(field);
			});
		}
	}

	function appendTextBlock(target, text) {
		parseOptionTextBlock(text).forEach(function(field) {
			target.push(field);
		});
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

		const result = [];

		chunks.forEach(function(chunk) {
			const parsed = splitOptionText(chunk);

			if (parsed) {
				result.push(parsed);
			}
		});

		return result;
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
				standardLabel: getText(cells[5]),
				quantity: '',
				createdDateText: '',
				preferredDeliveryDateText: getText(cells[6]),
				dateText: getText(cells[6]),
				orderComment: '',
				adminMemo: '',
				options: optionFields.length > 0 ? optionFields : [
					{
						label: '제품옵션',
						value: optionText || '-'
					}
				],
				images: [],
				listImageIndex: 0
			};
		}).filter(function(order) {
			return order.id;
		});
	}

	function renderListOrders() {
		if (!els.host) {
			return;
		}

		if (state.orders.length === 0) {
			setHostEmpty('조회된 생산 주문이 없습니다.');
			return;
		}

		els.host.innerHTML = state.orders.map(buildListCardHtml).join('');

		if (els.counter) {
			els.counter.textContent = state.orders.length + '건';
		}

		updateListOptionOverflow();
	}

	function buildListCardHtml(order, orderIndex) {
		const imageIndex = getOrderListImageIndex(order);
		const image = order.images[imageIndex];

		return [
			'<article class="team-production-overview-list-card" data-list-order-index="' + orderIndex + '" data-order-id="' + escapeAttr(order.id) + '">',
			buildListTopHtml(order),
			'<div class="team-production-overview-list-main">',
			buildListOptionPanelHtml(order),
			buildListImagePanelHtml(order, image, imageIndex),
			'</div>',
			'</article>'
		].join('');
	}

	function buildListTopHtml(order) {
		return [
			'<section class="team-production-overview-list-top">',
			'<div class="team-production-overview-list-company">',
			'<div class="team-production-overview-list-company-name" title="' + escapeAttr(order.companyName || '-') + '">' + escapeHtml(order.companyName || '-') + '</div>',
			'<div class="team-production-overview-list-address" title="' + escapeAttr(order.address || '-') + '">' + escapeHtml(order.address || '-') + '</div>',
			'</div>',
			buildListTopItemHtml('ID / 상태', '#' + (order.id || '-') + ' · ' + (order.statusLabel || '-')),
			buildListTopItemHtml('제품명', order.productName || '-'),
			buildListTopItemHtml('분류', order.productCategoryName || '-'),
			buildListTopItemHtml('규격', order.standardLabel || '-'),
			buildListTopItemHtml('일자', order.dateText || '-'),
			'</section>'
		].join('');
	}

	function buildListTopItemHtml(label, value) {
		return [
			'<div class="team-production-overview-list-top-item">',
			'<div class="team-production-overview-list-top-label">' + escapeHtml(label) + '</div>',
			'<div class="team-production-overview-list-top-value" title="' + escapeAttr(value || '-') + '">' + escapeHtml(value || '-') + '</div>',
			'</div>'
		].join('');
	}

	function buildListOptionPanelHtml(order) {
		const options = order.options || [];

		if (options.length === 0) {
			return [
				'<section class="team-production-overview-list-panel team-production-overview-list-option-panel">',
				'<div class="team-production-overview-list-panel-title">제품옵션 <small>없음</small></div>',
				'<div class="team-production-overview-list-option-empty">표시할 제품옵션이 없습니다.</div>',
				'</section>'
			].join('');
		}

		return [
			'<section class="team-production-overview-list-panel team-production-overview-list-option-panel">',
			'<div class="team-production-overview-list-panel-title">제품옵션 <small>' + options.length + '개</small></div>',
			'<div class="team-production-overview-list-option-scroll">',
			'<div class="team-production-overview-list-option-grid">',
			options.map(buildListOptionItemHtml).join(''),
			'</div>',
			'</div>',
			'</section>'
		].join('');
	}

	function buildListOptionItemHtml(option) {
		const className = option.important
			? 'team-production-overview-list-option-item team-production-overview-list-option-item-important'
			: 'team-production-overview-list-option-item';

		return [
			'<div class="' + className + '">',
			'<div class="team-production-overview-list-option-label">' + escapeHtml(option.label || '옵션') + '</div>',
			'<div class="team-production-overview-list-option-value">' + escapeHtml(option.value || '-') + '</div>',
			'</div>'
		].join('');
	}

	function buildListImagePanelHtml(order, image, imageIndex) {
		const images = order.images || [];
		const hasImages = images.length > 0;

		return [
			'<section class="team-production-overview-list-panel team-production-overview-list-image-panel">',
			'<div class="team-production-overview-list-panel-title">이미지 <small>' + (hasImages ? (imageIndex + 1) + ' / ' + images.length : '없음') + '</small></div>',
			'<div class="team-production-overview-list-image-main">',
			hasImages ? buildListMainImageHtml(image) : '<div class="team-production-overview-list-image-empty">등록된 이미지가 없습니다.</div>',
			hasImages && images.length > 1 ? '<button type="button" class="team-production-overview-list-inner-image-nav team-production-overview-list-inner-image-prev" data-list-image-move="-1" aria-label="이전 이미지">‹</button>' : '',
			hasImages && images.length > 1 ? '<button type="button" class="team-production-overview-list-inner-image-nav team-production-overview-list-inner-image-next" data-list-image-move="1" aria-label="다음 이미지">›</button>' : '',
			'</div>',
			hasImages && images.length > 1 ? buildListThumbsHtml(images, imageIndex) : '',
			'</section>'
		].join('');
	}

	function buildListMainImageHtml(image) {
		return '<img src="' + escapeAttr(image.url) + '" alt="' + escapeAttr(image.name || '관리자 업로드 이미지') + '" data-list-image-open="true">';
	}

	function buildListThumbsHtml(images, activeIndex) {
		return [
			'<div class="team-production-overview-list-image-thumbs">',
			images.map(function(image, index) {
				const activeClass = index === activeIndex ? ' active' : '';

				return '<button type="button" class="team-production-overview-list-image-thumb' + activeClass + '" data-list-image-index="' + index + '" aria-label="이미지 ' + (index + 1) + '">' +
					'<img src="' + escapeAttr(image.url) + '" alt="' + escapeAttr(image.name || '이미지') + '">' +
					'</button>';
			}).join(''),
			'</div>'
		].join('');
	}

	function handleListHostClick(event) {
		const card = event.target.closest('.team-production-overview-list-card');

		if (!card) {
			return;
		}

		const orderIndex = Number(card.getAttribute('data-list-order-index')) || 0;

		const imageMoveBtn = event.target.closest('[data-list-image-move]');

		if (imageMoveBtn) {
			event.preventDefault();
			moveListCardImage(orderIndex, Number(imageMoveBtn.getAttribute('data-list-image-move')) || 0);
			return;
		}

		const thumbBtn = event.target.closest('[data-list-image-index]');

		if (thumbBtn) {
			event.preventDefault();
			setListCardImageIndex(orderIndex, Number(thumbBtn.getAttribute('data-list-image-index')) || 0);
			return;
		}

		const openImage = event.target.closest('[data-list-image-open="true"]');

		if (openImage) {
			event.preventDefault();
			openImageModal(orderIndex);
		}
	}

	function moveListCardImage(orderIndex, direction) {
		const order = state.orders[orderIndex];

		if (!order || !order.images || order.images.length === 0) {
			return;
		}

		order.listImageIndex = getLoopedImageIndex(getOrderListImageIndex(order) + direction, order.images.length);
		updateListCardImagePreviewOnly(orderIndex);
	}

	function setListCardImageIndex(orderIndex, index) {
		const order = state.orders[orderIndex];

		if (!order || !order.images || order.images.length === 0) {
			return;
		}

		order.listImageIndex = clamp(index, 0, order.images.length - 1);
		updateListCardImagePreviewOnly(orderIndex);
	}

	function updateListCardImagePreviewOnly(orderIndex) {
		const order = state.orders[orderIndex];

		if (!els.host || !order || !order.images || order.images.length === 0) {
			return;
		}

		const card = els.host.querySelector('.team-production-overview-list-card[data-list-order-index="' + orderIndex + '"]');

		if (!card) {
			return;
		}

		const imageIndex = getOrderListImageIndex(order);
		const image = order.images[imageIndex];
		const mainImage = card.querySelector('.team-production-overview-list-image-main img[data-list-image-open="true"]');

		if (mainImage && image) {
			mainImage.src = image.url;
			mainImage.alt = image.name || '관리자 업로드 이미지';
		}

		const panelTitleSmall = card.querySelector('.team-production-overview-list-image-panel .team-production-overview-list-panel-title small');

		if (panelTitleSmall) {
			panelTitleSmall.textContent = (imageIndex + 1) + ' / ' + order.images.length;
		}

		card.querySelectorAll('.team-production-overview-list-image-thumb').forEach(function(thumb, index) {
			thumb.classList.toggle('active', index === imageIndex);
		});
	}

	function getOrderListImageIndex(order) {
		if (!order) {
			return 0;
		}

		const maxIndex = Math.max(0, (order.images || []).length - 1);
		order.listImageIndex = clamp(Number(order.listImageIndex) || 0, 0, maxIndex);

		return order.listImageIndex;
	}

	function openImageModal(orderIndex) {
		const order = state.orders[orderIndex];

		if (!order || !order.images || order.images.length === 0 || !els.imageModal || !els.imageViewer) {
			return;
		}

		state.imageModalOpen = true;
		state.imageOrderIndex = orderIndex;
		state.imageIndex = getOrderListImageIndex(order);
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

		updateListCardImagePreviewOnly(state.imageOrderIndex);
	}

	function renderImageModal() {
		const order = state.orders[state.imageOrderIndex];

		if (!order || !order.images || order.images.length === 0 || !els.imageViewer) {
			return;
		}

		state.imageIndex = clamp(state.imageIndex, 0, order.images.length - 1);
		order.listImageIndex = state.imageIndex;

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

		updateListCardImagePreviewOnly(state.imageOrderIndex);
	}

	function moveImageModal(direction) {
		const order = state.orders[state.imageOrderIndex];

		if (!order || !order.images || order.images.length === 0) {
			return;
		}

		state.imageIndex = getLoopedImageIndex(state.imageIndex + direction, order.images.length);
		order.listImageIndex = state.imageIndex;

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

	function updateListOptionOverflow() {
		requestAnimationFrame(function() {
			if (!els.host) {
				return;
			}

			els.host.querySelectorAll('.team-production-overview-list-option-scroll').forEach(function(scrollBox) {
				const needsScroll = scrollBox.scrollHeight > scrollBox.clientHeight + 2;
				scrollBox.classList.toggle('has-overflow', needsScroll);
			});
		});
	}

	function setHostLoading() {
		if (!els.host) {
			return;
		}

		els.host.innerHTML = '<div class="team-production-overview-list-loading">리스트형 일괄보기 데이터를 불러오는 중입니다.</div>';

		if (els.counter) {
			els.counter.textContent = '0건';
		}
	}

	function setHostEmpty(message) {
		if (!els.host) {
			return;
		}

		els.host.innerHTML = '<div class="team-production-overview-list-empty">' + escapeHtml(message || '조회된 생산 주문이 없습니다.') + '</div>';

		if (els.counter) {
			els.counter.textContent = '0건';
		}
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
})();