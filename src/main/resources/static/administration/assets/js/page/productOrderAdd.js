/* productOrderAdd.js */
(function() {
	'use strict';

	const API_BASE = '/management/api/product-order-add';
	const DEFAULT_PRODUCTION_CATEGORY_ID = 1;
	const DEFAULT_PRODUCTION_CATEGORY_LABEL = '기본카테고리(ID 1)';

	const NON_STANDARD_EXCLUDED_PRODUCTION_CATEGORY_NAMES = new Set([
		'재단',
		'재단(거울)'
	]);

	const state = {
		company: null,
		deliveryMethod: null,
		deliveryHandler: null,
		companyResults: [],
		deliveryResults: [],
		companyActiveIndex: -1,
		deliveryActiveIndex: -1,
		deliveryMethods: [],
		standardCategories: [],
		productionCategories: [],
		seriesCache: {},
		orders: [],
		saving: false,
		activePasteOrderId: null,
		companyDeliveryAddresses: [],
		companyDeliveryAddressLoadedForCompanyId: null,
		companyOrdererInfos: [],
		companyOrdererInfoLoadedForCompanyId: null,

		addressSource: 'COMPANY',
		useCompanyAddress: true,

		companyAddressSelectMode: 'COMMON',
		siteAddressSource: 'COMMON',
		useCommonSiteAddress: true,

		siteAddress: {
			zipCode: '',
			doName: '',
			siName: '',
			guName: '',
			roadAddress: '',
			detailAddress: ''
		},

		ordererSource: 'COMPANY',
		useCompanyOrderer: true,

		packingCost: '0',
		deliveryCost: '0',

		address: {
			zipCode: '',
			doName: '',
			siName: '',
			guName: '',
			roadAddress: '',
			detailAddress: ''
		},

		orderer: {
			ordererName: '',
			ordererPhone: ''
		}
	};

	const els = {};

	document.addEventListener('DOMContentLoaded', init);

	async function init() {
		cacheElements();
		bindStaticEvents();

		await Promise.all([
			loadDeliveryMethods(),
			loadStandardCategories(),
			loadProductionCategories()
		]);

		renderOrders();
		refreshBottomSummary();
		refreshActionButtons();
		renderAddressSummary();
		renderSiteAddressSummary();
		renderOrdererSummary();
		syncSiteAddressSection();
		syncDeliveryHandlerAvailability();
	}

	function cacheElements() {
		els.companyInput = document.getElementById('product-admin-add-company-input');
		els.companyId = document.getElementById('product-admin-add-company-id');
		els.companyDropdown = document.getElementById('product-admin-add-company-dropdown');
		els.companyFeedback = document.getElementById('product-admin-add-company-feedback');
		els.companySummary = document.getElementById('product-admin-add-company-summary');

		els.preferredDate = document.getElementById('product-admin-add-preferred-date');
		els.preferredDateFeedback = document.getElementById('product-admin-add-preferred-date-feedback');

		els.deliveryMethod = document.getElementById('product-admin-add-delivery-method');
		els.deliveryMethodFeedback = document.getElementById('product-admin-add-delivery-method-feedback');
		els.packingCost = document.getElementById('product-admin-add-packing-cost');
		els.deliveryCost = document.getElementById('product-admin-add-delivery-cost');

		els.deliveryInput = document.getElementById('product-admin-add-delivery-member-input');
		els.deliveryId = document.getElementById('product-admin-add-delivery-member-id');
		els.deliveryDropdown = document.getElementById('product-admin-add-delivery-member-dropdown');
		els.deliveryFeedback = document.getElementById('product-admin-add-delivery-member-feedback');
		els.deliverySummary = document.getElementById('product-admin-add-delivery-member-summary');

		els.useCompanyAddress = document.getElementById('product-admin-add-use-company-address');
		els.companyAddressSearchBtn = document.getElementById('product-admin-add-company-address-search-btn');
		els.companyAddressModalEl = document.getElementById('product-admin-add-company-address-modal');
		els.companyAddressModal = els.companyAddressModalEl ? new bootstrap.Modal(els.companyAddressModalEl) : null;
		els.companyAddressList = document.getElementById('product-admin-add-company-address-list');
		els.companyAddressEmpty = document.getElementById('product-admin-add-company-address-empty');

		els.useCompanyOrderer = document.getElementById('product-admin-add-use-company-orderer');
		els.companyOrdererSearchBtn = document.getElementById('product-admin-add-company-orderer-search-btn');
		els.companyOrdererModalEl = document.getElementById('product-admin-add-company-orderer-modal');
		els.companyOrdererModal = els.companyOrdererModalEl ? new bootstrap.Modal(els.companyOrdererModalEl) : null;
		els.companyOrdererList = document.getElementById('product-admin-add-company-orderer-list');
		els.companyOrdererEmpty = document.getElementById('product-admin-add-company-orderer-empty');
		els.ordererName = document.getElementById('product-admin-add-orderer-name');
		els.ordererPhone = document.getElementById('product-admin-add-orderer-phone');
		els.ordererFeedback = document.getElementById('product-admin-add-orderer-feedback');
		els.ordererSummary = document.getElementById('product-admin-add-orderer-summary');

		els.searchAddressBtn = document.getElementById('product-admin-add-search-address-btn');
		els.zipCode = document.getElementById('product-admin-add-zip-code');
		els.roadAddress = document.getElementById('product-admin-add-road-address');
		els.detailAddress = document.getElementById('product-admin-add-detail-address');
		els.doName = document.getElementById('product-admin-add-do-name');
		els.siName = document.getElementById('product-admin-add-si-name');
		els.guName = document.getElementById('product-admin-add-gu-name');
		els.addressFeedback = document.getElementById('product-admin-add-address-feedback');
		els.addressSummary = document.getElementById('product-admin-add-address-summary');

		els.siteAddressSection = document.getElementById('product-admin-add-site-address-section');
		els.useCommonSiteAddress = document.getElementById('product-admin-add-use-common-site-address');
		els.siteCompanyAddressSearchBtn = document.getElementById('product-admin-add-site-company-address-search-btn');
		els.siteSearchAddressBtn = document.getElementById('product-admin-add-site-search-address-btn');
		els.siteZipCode = document.getElementById('product-admin-add-site-zip-code');
		els.siteRoadAddress = document.getElementById('product-admin-add-site-road-address');
		els.siteDetailAddress = document.getElementById('product-admin-add-site-detail-address');
		els.siteDoName = document.getElementById('product-admin-add-site-do-name');
		els.siteSiName = document.getElementById('product-admin-add-site-si-name');
		els.siteGuName = document.getElementById('product-admin-add-site-gu-name');
		els.siteAddressFeedback = document.getElementById('product-admin-add-site-address-feedback');
		els.siteAddressSummary = document.getElementById('product-admin-add-site-address-summary');

		els.orderAddTopSlot = document.getElementById('product-admin-add-order-add-top-slot');
		els.orderAddBottomSlot = document.getElementById('product-admin-add-order-add-bottom-slot');

		els.orderAddBtn = document.getElementById('product-admin-add-order-add-btn');
		els.orderList = document.getElementById('product-admin-add-order-list');
		els.emptyState = document.getElementById('product-admin-add-empty-state');
		els.topMessage = document.getElementById('product-admin-add-top-message');

		els.bottomOrderCount = document.getElementById('product-admin-add-bottom-order-count');
		els.bottomTotalPrice = document.getElementById('product-admin-add-bottom-total-price');
		els.bottomOrderSummaryBody = document.getElementById('product-admin-add-bottom-order-summary-body');

		els.previewBtn = document.getElementById('product-admin-add-preview-btn');
		els.summaryContent = document.getElementById('product-admin-add-summary-content');
		els.confirmBtn = document.getElementById('product-admin-add-confirm-btn');

		els.summaryModalEl = document.getElementById('product-admin-add-summary-modal');
		els.summaryModal = els.summaryModalEl ? new bootstrap.Modal(els.summaryModalEl) : null;
	}

	function bindStaticEvents() {
		const debouncedCompanySearch = debounce(() => loadCompanies(els.companyInput.value.trim()), 180);
		const debouncedDeliverySearch = debounce(() => loadDeliveryHandlers(els.deliveryInput.value.trim()), 180);

		els.companyInput.addEventListener('focus', () => loadCompanies(els.companyInput.value.trim()));
		els.companyInput.addEventListener('input', () => {
			clearSelectedCompany(false);
			debouncedCompanySearch();
			refreshActionButtons();
		});
		els.companyInput.addEventListener('keydown', handleCompanyKeydown);
		els.companyInput.addEventListener('blur', () => {
			setTimeout(() => {
				validateExactCompanySelection();
				closeCompanyDropdown();
			}, 140);
		});

		els.preferredDate.addEventListener('change', () => {
			clearDateFeedback();
			refreshActionButtons();
		});

		if (els.deliveryMethod) {
			els.deliveryMethod.addEventListener('change', handleDeliveryMethodChange);
		}

		if (els.packingCost) {
			els.packingCost.addEventListener('input', () => {
				state.packingCost = sanitizeMoneyInputElement(els.packingCost);
				refreshBottomSummary();
			});
		}

		if (els.deliveryCost) {
			els.deliveryCost.addEventListener('input', () => {
				state.deliveryCost = sanitizeMoneyInputElement(els.deliveryCost);
				refreshBottomSummary();
			});
		}

		els.deliveryInput.addEventListener('focus', () => {
			if (isDeliveryHandlerAssignableSelected()) {
				loadDeliveryHandlers(els.deliveryInput.value.trim());
			}
		});
		els.deliveryInput.addEventListener('input', () => {
			if (!isDeliveryHandlerAssignableSelected()) {
				clearSelectedDeliveryHandler(false);
				refreshActionButtons();
				return;
			}

			clearSelectedDeliveryHandler(false);
			debouncedDeliverySearch();
			refreshActionButtons();
		});
		els.deliveryInput.addEventListener('keydown', handleDeliveryKeydown);
		els.deliveryInput.addEventListener('blur', () => {
			setTimeout(() => {
				validateExactDeliverySelection();
				closeDeliveryDropdown();
			}, 140);
		});

		if (els.useCompanyAddress) {
			els.useCompanyAddress.addEventListener('change', handleUseCompanyAddressChange);
		}

		if (els.companyAddressSearchBtn) {
			els.companyAddressSearchBtn.addEventListener('click', () => openCompanyAddressModal('COMMON'));
		}

		if (els.companyAddressList) {
			els.companyAddressList.addEventListener('click', handleCompanyAddressModalClick);
		}

		if (els.useCommonSiteAddress) {
			els.useCommonSiteAddress.addEventListener('change', handleUseCommonSiteAddressChange);
		}

		if (els.siteCompanyAddressSearchBtn) {
			els.siteCompanyAddressSearchBtn.addEventListener('click', () => openCompanyAddressModal('SITE'));
		}

		if (els.siteSearchAddressBtn) {
			els.siteSearchAddressBtn.addEventListener('click', () => openAddressSearch('SITE'));
		}

		if (els.siteDetailAddress) {
			els.siteDetailAddress.addEventListener('input', () => {
				if (state.useCommonSiteAddress && els.useCommonSiteAddress) {
					els.useCommonSiteAddress.checked = false;
					state.useCommonSiteAddress = false;
					state.siteAddressSource = 'MANUAL';
				}

				state.siteAddress.detailAddress = (els.siteDetailAddress.value || '').trim();
				clearSiteAddressFeedback();
				renderSiteAddressSummary();
				refreshActionButtons();
			});
		}

		if (els.useCompanyOrderer) {
			els.useCompanyOrderer.addEventListener('change', handleUseCompanyOrdererChange);
		}

		if (els.companyOrdererSearchBtn) {
			els.companyOrdererSearchBtn.addEventListener('click', openCompanyOrdererModal);
		}

		if (els.companyOrdererList) {
			els.companyOrdererList.addEventListener('click', handleCompanyOrdererModalClick);
		}

		if (els.ordererName) {
			els.ordererName.addEventListener('input', () => {
				if (state.useCompanyOrderer && els.useCompanyOrderer) {
					els.useCompanyOrderer.checked = false;
					state.useCompanyOrderer = false;
					state.ordererSource = 'MANUAL';
				}

				state.orderer.ordererName = (els.ordererName.value || '').trim();
				renderOrdererSummary();
			});
		}

		if (els.ordererPhone) {
			els.ordererPhone.addEventListener('input', () => {
				if (state.useCompanyOrderer && els.useCompanyOrderer) {
					els.useCompanyOrderer.checked = false;
					state.useCompanyOrderer = false;
					state.ordererSource = 'MANUAL';
				}

				state.orderer.ordererPhone = (els.ordererPhone.value || '').trim();
				renderOrdererSummary();
			});
		}

		els.searchAddressBtn.addEventListener('click', () => openAddressSearch('COMMON'));

		els.detailAddress.addEventListener('input', () => {
			if (state.useCompanyAddress && els.useCompanyAddress) {
				els.useCompanyAddress.checked = false;
				state.useCompanyAddress = false;
				state.addressSource = 'MANUAL';
			}

			state.address.detailAddress = (els.detailAddress.value || '').trim();
			if (state.useCommonSiteAddress) {
				applyCommonAddressToSite();
			}
			clearAddressFeedback();
			renderAddressSummary();
			renderSiteAddressSummary();
			refreshActionButtons();
		});

		els.companyDropdown.addEventListener('pointerdown', handleCompanyDropdownPointerDown);
		els.deliveryDropdown.addEventListener('pointerdown', handleDeliveryDropdownPointerDown);

		els.orderAddBtn.addEventListener('click', handleAddOrder);
		els.previewBtn.addEventListener('click', openSummaryModal);
		els.confirmBtn.addEventListener('click', submitForm);

		els.orderList.addEventListener('click', handleOrderListClick);
		els.orderList.addEventListener('change', handleOrderListChange);
		els.orderList.addEventListener('input', handleOrderListInput);
		els.orderList.addEventListener('keydown', handleOrderListKeydown);
		els.orderList.addEventListener('focusin', handleOrderListFocusIn);
		els.orderList.addEventListener('focusout', handleOrderListFocusOut);
		els.orderList.addEventListener('pointerdown', handleProductionDropdownPointerDown);
		els.orderList.addEventListener('dragover', handleOrderListDragOver);
		els.orderList.addEventListener('dragleave', handleOrderListDragLeave);
		els.orderList.addEventListener('drop', handleOrderListDrop);

		els.orderList.addEventListener('pointerdown', rememberPasteTargetByEvent);
		els.orderList.addEventListener('pointerdown', handleProductionDropdownPointerDown);
		els.orderList.addEventListener('dragover', handleOrderListDragOver);
		els.orderList.addEventListener('dragleave', handleOrderListDragLeave);
		els.orderList.addEventListener('drop', handleOrderListDrop);

		document.addEventListener('paste', handleDocumentPaste);

		document.addEventListener('click', (event) => {
			const companyWrap = els.companyInput.closest('.product-admin-add-autocomplete-wrap');
			const deliveryWrap = els.deliveryInput.closest('.product-admin-add-autocomplete-wrap');

			if (companyWrap && !companyWrap.contains(event.target)) {
				closeCompanyDropdown();
			}

			if (deliveryWrap && !deliveryWrap.contains(event.target)) {
				closeDeliveryDropdown();
			}

			if (!event.target.closest('.product-admin-add-order-card')) {
				document.querySelectorAll('.product-admin-add-production-dropdown').forEach(dropdown => {
					dropdown.classList.add('d-none');
				});
			}
		});
	}

	async function loadStandardCategories() {
		state.standardCategories = await fetchJson(`${API_BASE}/standard-categories`);
	}

	async function loadProductionCategories(keyword = '') {
		const query = keyword ? `?keyword=${encodeURIComponent(keyword)}` : '';
		state.productionCategories = await fetchJson(`${API_BASE}/production-categories${query}`);
	}

	async function loadDeliveryMethods() {
		state.deliveryMethods = await fetchJson(`${API_BASE}/delivery-methods`);
		renderDeliveryMethodOptions();
	}

	async function loadStandardSeries(categoryId) {
		if (!categoryId) {
			return [];
		}

		if (state.seriesCache[categoryId]) {
			return state.seriesCache[categoryId];
		}

		const series = await fetchJson(`${API_BASE}/standard-series?categoryId=${encodeURIComponent(categoryId)}`);
		state.seriesCache[categoryId] = series;
		return series;
	}

	async function loadCompanies(keyword) {
		state.companyResults = await fetchJson(`${API_BASE}/companies?keyword=${encodeURIComponent(keyword || '')}`);
		state.companyActiveIndex = state.companyResults.length > 0 ? 0 : -1;
		renderCompanyDropdown();
	}

	async function loadDeliveryHandlers(keyword) {
		state.deliveryResults = await fetchJson(`${API_BASE}/delivery-handlers?keyword=${encodeURIComponent(keyword || '')}`);
		state.deliveryActiveIndex = state.deliveryResults.length > 0 ? 0 : -1;
		renderDeliveryDropdown();
	}

	function renderCompanyDropdown() {
		if (!state.companyResults.length) {
			els.companyDropdown.innerHTML = `
                <div class="product-admin-add-autocomplete-item">
                    <span class="product-admin-add-autocomplete-title">검색 결과가 없습니다.</span>
                    <span class="product-admin-add-autocomplete-sub">정확한 업체명을 선택해 주세요.</span>
                </div>
            `;
			els.companyDropdown.classList.remove('d-none');
			return;
		}

		els.companyDropdown.innerHTML = state.companyResults.map((item, index) => `
            <button type="button"
                class="product-admin-add-autocomplete-item ${index === state.companyActiveIndex ? 'active' : ''}"
                data-company-index="${index}">
                <span class="product-admin-add-autocomplete-title">
                    ${escapeHtml(item.companyName)} (${escapeHtml(item.representativeName || '-')})
                </span>
                <span class="product-admin-add-autocomplete-sub">
                    가입일 ${formatDate(item.joinedAt)} ${item.address ? `ㆍ ${escapeHtml(item.address)}` : ''}
                </span>
            </button>
        `).join('');

		els.companyDropdown.classList.remove('d-none');
	}

	function renderDeliveryDropdown() {
		if (!state.deliveryResults.length) {
			els.deliveryDropdown.innerHTML = `
                <div class="product-admin-add-autocomplete-item">
                    <span class="product-admin-add-autocomplete-title">검색 결과가 없습니다.</span>
                    <span class="product-admin-add-autocomplete-sub">정확한 멤버를 선택해 주세요.</span>
                </div>
            `;
			els.deliveryDropdown.classList.remove('d-none');
			return;
		}

		els.deliveryDropdown.innerHTML = state.deliveryResults.map((item, index) => `
            <button type="button"
                class="product-admin-add-autocomplete-item ${index === state.deliveryActiveIndex ? 'active' : ''}"
                data-delivery-index="${index}">
                <span class="product-admin-add-autocomplete-title">
                    ${escapeHtml(item.name)} (${escapeHtml(item.username || '-')})
                </span>
                <span class="product-admin-add-autocomplete-sub">
                    ${item.phone ? escapeHtml(item.phone) + ' ㆍ ' : ''}가입일 ${formatDate(item.joinedAt)}
                </span>
            </button>
        `).join('');

		els.deliveryDropdown.classList.remove('d-none');
	}

	function handleCompanyDropdownPointerDown(event) {
		const button = event.target.closest('[data-company-index]');
		if (!button) {
			return;
		}

		event.preventDefault();
		event.stopPropagation();

		const item = state.companyResults[Number(button.dataset.companyIndex)];
		if (item) {
			selectCompany(item);
		}
	}

	function handleDeliveryDropdownPointerDown(event) {
		const button = event.target.closest('[data-delivery-index]');
		if (!button) {
			return;
		}

		event.preventDefault();
		event.stopPropagation();

		const item = state.deliveryResults[Number(button.dataset.deliveryIndex)];
		if (item) {
			selectDeliveryHandler(item);
		}
	}

	function handleCompanyKeydown(event) {
		if (els.companyDropdown.classList.contains('d-none')) {
			return;
		}

		if (event.key === 'ArrowDown') {
			event.preventDefault();
			state.companyActiveIndex = Math.min(state.companyActiveIndex + 1, state.companyResults.length - 1);
			renderCompanyDropdown();
			return;
		}

		if (event.key === 'ArrowUp') {
			event.preventDefault();
			state.companyActiveIndex = Math.max(state.companyActiveIndex - 1, 0);
			renderCompanyDropdown();
			return;
		}

		if (event.key === 'Enter') {
			event.preventDefault();

			if (state.companyResults[state.companyActiveIndex]) {
				selectCompany(state.companyResults[state.companyActiveIndex]);
			} else {
				validateExactCompanySelection(true);
			}
			return;
		}

		if (event.key === 'Escape') {
			closeCompanyDropdown();
		}
	}

	function handleDeliveryKeydown(event) {
		if (els.deliveryDropdown.classList.contains('d-none')) {
			return;
		}

		if (event.key === 'ArrowDown') {
			event.preventDefault();
			state.deliveryActiveIndex = Math.min(state.deliveryActiveIndex + 1, state.deliveryResults.length - 1);
			renderDeliveryDropdown();
			return;
		}

		if (event.key === 'ArrowUp') {
			event.preventDefault();
			state.deliveryActiveIndex = Math.max(state.deliveryActiveIndex - 1, 0);
			renderDeliveryDropdown();
			return;
		}

		if (event.key === 'Enter') {
			event.preventDefault();

			if (state.deliveryResults[state.deliveryActiveIndex]) {
				selectDeliveryHandler(state.deliveryResults[state.deliveryActiveIndex]);
			} else {
				validateExactDeliverySelection(true);
			}
			return;
		}

		if (event.key === 'Escape') {
			closeDeliveryDropdown();
		}
	}
	function selectCompany(item) {
		state.company = item;
		state.companyDeliveryAddresses = [];
		state.companyDeliveryAddressLoadedForCompanyId = null;
		state.companyOrdererInfos = [];
		state.companyOrdererInfoLoadedForCompanyId = null;

		els.companyId.value = item.companyId;
		els.companyInput.value = item.companyName;
		els.companyInput.classList.remove('product-admin-add-invalid');
		els.companyFeedback.textContent = '';

		els.companySummary.innerHTML = `
            <div><strong>대표자</strong> ${escapeHtml(item.representativeName || '-')}</div>
            <div><strong>대표자 연락처</strong> ${escapeHtml(item.representativePhone || '-')}</div>
            <div><strong>가입일</strong> ${formatDate(item.joinedAt)}</div>
            <div><strong>업체 기본주소</strong> ${escapeHtml(item.address || '-')}</div>
        `;
		els.companySummary.classList.remove('d-none');

		if (els.useCompanyAddress) {
			els.useCompanyAddress.checked = true;
		}

		if (els.useCompanyOrderer) {
			els.useCompanyOrderer.checked = true;
		}

		if (els.useCommonSiteAddress) {
			els.useCommonSiteAddress.checked = true;
		}

		state.useCompanyAddress = true;
		state.useCommonSiteAddress = true;
		state.useCompanyOrderer = true;
		if (els.useCommonSiteAddress) {
			els.useCommonSiteAddress.checked = true;
		}
		applyCompanyDefaultAddress();
		applyCommonAddressToSite();
		applyCompanyDefaultOrderer();

		closeCompanyDropdown();
		refreshActionButtons();
		els.companyInput.blur();
	}
	function clearSelectedCompany(clearInput = true) {
		state.company = null;
		state.companyDeliveryAddresses = [];
		state.companyDeliveryAddressLoadedForCompanyId = null;
		state.companyOrdererInfos = [];
		state.companyOrdererInfoLoadedForCompanyId = null;

		els.companyId.value = '';

		if (clearInput) {
			els.companyInput.value = '';
		}

		els.companySummary.classList.add('d-none');
		els.companySummary.innerHTML = '';

		if (els.useCompanyAddress) {
			els.useCompanyAddress.checked = true;
		}

		if (els.useCompanyOrderer) {
			els.useCompanyOrderer.checked = true;
		}

		state.useCompanyAddress = true;
		state.addressSource = 'COMPANY';
		state.useCommonSiteAddress = true;
		state.siteAddressSource = 'COMMON';
		state.useCompanyOrderer = true;
		state.ordererSource = 'COMPANY';
		clearAddressFields();
		clearSiteAddressFields();
		clearOrdererFields();
		refreshActionButtons();
	}

	function validateExactCompanySelection(showMessage = false) {
		const value = (els.companyInput.value || '').trim();

		if (!value) {
			clearSelectedCompany(false);
			els.companyInput.classList.remove('product-admin-add-invalid');
			els.companyFeedback.textContent = '';
			return true;
		}

		if (state.company && String(state.company.companyId) === String(els.companyId.value)) {
			els.companyInput.classList.remove('product-admin-add-invalid');
			els.companyFeedback.textContent = '';
			return true;
		}

		if (showMessage || value) {
			els.companyInput.classList.add('product-admin-add-invalid');
			els.companyFeedback.textContent = '정확한 업체를 선택해 주세요.';
		}

		return false;
	}

	function selectDeliveryHandler(item) {
		state.deliveryHandler = item;
		els.deliveryId.value = item.memberId;
		els.deliveryInput.value = item.name;
		els.deliveryInput.classList.remove('product-admin-add-invalid');
		els.deliveryFeedback.textContent = '';

		els.deliverySummary.innerHTML = `
            <div><strong>아이디</strong> ${escapeHtml(item.username || '-')}</div>
            <div><strong>연락처</strong> ${escapeHtml(item.phone || '-')}</div>
            <div><strong>가입일</strong> ${formatDate(item.joinedAt)}</div>
        `;
		els.deliverySummary.classList.remove('d-none');

		closeDeliveryDropdown();
		refreshActionButtons();
		els.deliveryInput.blur();
	}

	function clearSelectedDeliveryHandler(clearInput = true) {
		state.deliveryHandler = null;
		els.deliveryId.value = '';

		if (clearInput) {
			els.deliveryInput.value = '';
		}

		els.deliverySummary.classList.add('d-none');
		els.deliverySummary.innerHTML = '';
	}
	function validateExactDeliverySelection(showMessage = false) {
		if (!isDeliveryHandlerAssignableSelected()) {
			clearSelectedDeliveryHandler(false);
			els.deliveryInput.classList.remove('product-admin-add-invalid');
			els.deliveryFeedback.textContent = '';
			return true;
		}

		const value = (els.deliveryInput.value || '').trim();

		// 직배송/현장배송/화물이어도 담당자 선택은 선택사항입니다.
		// 값을 입력한 경우에만 반드시 자동완성 목록에서 정확히 선택되어야 합니다.
		if (!value) {
			clearSelectedDeliveryHandler(false);
			els.deliveryInput.classList.remove('product-admin-add-invalid');
			els.deliveryFeedback.textContent = '';
			return true;
		}

		if (state.deliveryHandler && String(state.deliveryHandler.memberId) === String(els.deliveryId.value)) {
			els.deliveryInput.classList.remove('product-admin-add-invalid');
			els.deliveryFeedback.textContent = '';
			return true;
		}

		if (showMessage || value) {
			els.deliveryInput.classList.add('product-admin-add-invalid');
			els.deliveryFeedback.textContent = '정확한 멤버를 선택해 주세요.';
		}

		return false;
	}

	function closeCompanyDropdown() {
		els.companyDropdown.classList.add('d-none');
	}

	function closeDeliveryDropdown() {
		els.deliveryDropdown.classList.add('d-none');
	}

	function clearDateFeedback() {
		els.preferredDate.classList.remove('product-admin-add-invalid');
		els.preferredDateFeedback.textContent = '';
	}
	function renderDeliveryMethodOptions() {
		if (!els.deliveryMethod) {
			return;
		}

		els.deliveryMethod.innerHTML = `
            <option value="">배송수단 선택</option>
            ${state.deliveryMethods.map(method => `
                <option value="${method.id}" data-direct="${method.directDelivery ? 'true' : 'false'}">
                    ${escapeHtml(method.methodName || '-')}${method.methodPrice ? ` (${formatCurrency(method.methodPrice)})` : ''}
                </option>
            `).join('')}
        `;
	}

	function handleDeliveryMethodChange() {
		const selectedId = els.deliveryMethod ? els.deliveryMethod.value : '';

		state.deliveryMethod = state.deliveryMethods.find(method =>
			String(method.id) === String(selectedId)
		) || null;

		if (els.deliveryMethod) {
			els.deliveryMethod.classList.remove('product-admin-add-invalid');
		}

		if (els.deliveryMethodFeedback) {
			els.deliveryMethodFeedback.textContent = '';
		}

		if (!isDeliveryHandlerAssignableSelected()) {
			clearSelectedDeliveryHandler(true);
		}

		if (isSiteDeliverySelected() && state.useCommonSiteAddress) {
			applyCommonAddressToSite();
		}

		syncSiteAddressSection();
		syncDeliveryHandlerAvailability();
		refreshActionButtons();
	}

	function getSelectedDeliveryMethodName() {
		return normalizeText(state.deliveryMethod ? state.deliveryMethod.methodName : '').replaceAll(' ', '');
	}

	function isDirectDeliverySelected() {
		return getSelectedDeliveryMethodName().includes('직배송');
	}

	function isSiteDeliverySelected() {
		return getSelectedDeliveryMethodName().includes('현장배송');
	}

	function isFreightDeliverySelected() {
		return getSelectedDeliveryMethodName().includes('화물');
	}

	function isDeliveryHandlerAssignableSelected() {
		if (!state.deliveryMethod) {
			return false;
		}

		return Boolean(state.deliveryMethod.directDelivery) ||
			isDirectDeliverySelected() ||
			isSiteDeliverySelected() ||
			isFreightDeliverySelected();
	}

	function syncDeliveryHandlerAvailability() {
		const assignable = isDeliveryHandlerAssignableSelected();

		if (!els.deliveryInput || !els.deliveryId) {
			return;
		}

		els.deliveryInput.disabled = !assignable;
		els.deliveryInput.placeholder = assignable
			? '선택사항입니다. 미선택 시 주소 기준으로 자동 배정됩니다.'
			: '직배송/현장배송/화물 선택 시 지정할 수 있습니다.';

		if (!assignable) {
			els.deliveryInput.classList.remove('product-admin-add-invalid');
			els.deliveryFeedback.textContent = '';
			closeDeliveryDropdown();
		}
	}


	function validateDeliveryMethod(showMessage = false) {
		const valid = Boolean(state.deliveryMethod && state.deliveryMethod.id);

		if (valid) {
			if (els.deliveryMethod) {
				els.deliveryMethod.classList.remove('product-admin-add-invalid');
			}
			if (els.deliveryMethodFeedback) {
				els.deliveryMethodFeedback.textContent = '';
			}
			return true;
		}

		if (showMessage) {
			if (els.deliveryMethod) {
				els.deliveryMethod.classList.add('product-admin-add-invalid');
			}
			if (els.deliveryMethodFeedback) {
				els.deliveryMethodFeedback.textContent = '배송수단을 선택해 주세요.';
			}
		}

		return false;
	}


	function handleUseCompanyAddressChange() {
		state.useCompanyAddress = Boolean(els.useCompanyAddress.checked);

		if (state.useCompanyAddress) {
			applyCompanyDefaultAddress();
			return;
		}

		state.addressSource = 'MANUAL';
		clearAddressFields();
	}

	function applyCompanyDefaultAddress() {
		if (!state.company) {
			clearAddressFields();
			return;
		}

		setAddressState({
			zipCode: state.company.zipCode || '',
			doName: state.company.doName || '',
			siName: state.company.siName || '',
			guName: state.company.guName || '',
			roadAddress: state.company.roadAddress || '',
			detailAddress: state.company.detailAddress || ''
		}, 'COMPANY');
	}

	function clearAddressFields() {
		setAddressState({
			zipCode: '',
			doName: '',
			siName: '',
			guName: '',
			roadAddress: '',
			detailAddress: ''
		}, state.addressSource || 'MANUAL');
	}

	function setAddressState(address, source) {
		state.address = {
			zipCode: normalizeZipCode(address.zipCode || ''),
			doName: (address.doName || '').trim(),
			siName: (address.siName || '').trim(),
			guName: (address.guName || '').trim(),
			roadAddress: (address.roadAddress || '').trim(),
			detailAddress: (address.detailAddress || '').trim()
		};

		state.addressSource = source || 'MANUAL';

		syncAddressInputs();

		if (state.useCommonSiteAddress) {
			applyCommonAddressToSite();
		}

		clearAddressFeedback();
		renderAddressSummary();
		renderSiteAddressSummary();
		refreshActionButtons();
	}
	function normalizeZipCode(value) {
		const raw = String(value || '').trim();

		const matched = raw.match(/\b\d{5}\b/);

		if (matched) {
			return matched[0];
		}

		return raw;
	}

	function syncAddressInputs() {
		els.zipCode.value = state.address.zipCode || '';
		els.doName.value = state.address.doName || '';
		els.siName.value = state.address.siName || '';
		els.guName.value = state.address.guName || '';
		els.roadAddress.value = state.address.roadAddress || '';
		els.detailAddress.value = state.address.detailAddress || '';
	}

	async function openCompanyAddressModal(mode = 'COMMON') {
		if (!state.company) {
			alert('먼저 대리점을 선택해 주세요.');
			return;
		}

		if (!els.companyAddressModal || !els.companyAddressList || !els.companyAddressEmpty) {
			alert('등록주소지검색 모달 HTML이 없습니다.');
			return;
		}

		state.companyAddressSelectMode = mode === 'SITE' ? 'SITE' : 'COMMON';

		try {
			await loadCompanyDeliveryAddressesIfNeeded();
			renderCompanyAddressModal();
			els.companyAddressModal.show();
		} catch (error) {
			alert(error.message || '등록주소지 목록을 불러오지 못했습니다.');
		}
	}

	async function loadCompanyDeliveryAddressesIfNeeded() {
		if (
			state.companyDeliveryAddressLoadedForCompanyId &&
			String(state.companyDeliveryAddressLoadedForCompanyId) === String(state.company.companyId)
		) {
			return;
		}

		state.companyDeliveryAddresses = await fetchJson(
			`${API_BASE}/companies/${encodeURIComponent(state.company.companyId)}/delivery-addresses`
		);
		state.companyDeliveryAddressLoadedForCompanyId = state.company.companyId;
	}

	function getCompanyAddressModalItems() {
		const items = [];

		if (state.company) {
			items.push({
				key: `COMPANY-${state.company.companyId}`,
				label: '회원주소',
				zipCode: state.company.zipCode || '',
				doName: state.company.doName || '',
				siName: state.company.siName || '',
				guName: state.company.guName || '',
				roadAddress: state.company.roadAddress || '',
				detailAddress: state.company.detailAddress || '',
				address: [state.company.roadAddress, state.company.detailAddress]
					.filter(Boolean)
					.join(' ')
			});
		}

		(state.companyDeliveryAddresses || []).forEach(item => {
			items.push({
				key: `DELIVERY-${item.id}`,
				label: '등록주소지',
				zipCode: item.zipCode || '',
				doName: item.doName || '',
				siName: item.siName || '',
				guName: item.guName || '',
				roadAddress: item.roadAddress || '',
				detailAddress: item.detailAddress || '',
				address: item.address || [item.roadAddress, item.detailAddress].filter(Boolean).join(' ')
			});
		});

		return items;
	}

	function renderCompanyAddressModal() {
		const items = getCompanyAddressModalItems();

		if (!items.length) {
			els.companyAddressEmpty.classList.remove('d-none');
			els.companyAddressList.innerHTML = '';
			return;
		}

		els.companyAddressEmpty.classList.add('d-none');

		els.companyAddressList.innerHTML = items.map(item => {
			const region = [item.doName, item.siName, item.guName]
				.filter(Boolean)
				.join(' / ');

			return `
                <button type="button"
                    class="product-admin-add-company-address-item"
                    data-action="select-company-address"
                    data-address-key="${escapeHtml(item.key)}">
                    <span class="badge bg-primary-subtle text-primary me-2">${escapeHtml(item.label)}</span>
                    <span class="product-admin-add-company-address-main">
                        ${escapeHtml(item.address || '-')}
                    </span>
                    <span class="product-admin-add-company-address-sub">
                        ${escapeHtml(item.zipCode ? `(${item.zipCode}) ` : '')}${escapeHtml(region || '')}
                    </span>
                </button>
            `;
		}).join('');
	}

	function handleCompanyAddressModalClick(event) {
		const button = event.target.closest('[data-action="select-company-address"][data-address-key]');
		if (!button) {
			return;
		}

		const item = getCompanyAddressModalItems().find(address =>
			String(address.key) === String(button.dataset.addressKey)
		);

		if (!item) {
			return;
		}

		if (state.companyAddressSelectMode === 'SITE') {
			if (els.useCommonSiteAddress) {
				els.useCommonSiteAddress.checked = false;
			}

			state.useCommonSiteAddress = false;

			setSiteAddressState({
				zipCode: item.zipCode,
				doName: item.doName,
				siName: item.siName,
				guName: item.guName,
				roadAddress: item.roadAddress,
				detailAddress: item.detailAddress
			}, item.label === '회원주소' ? 'COMPANY' : 'COMPANY_DELIVERY_ADDRESS');

			if (els.companyAddressModal) {
				els.companyAddressModal.hide();
			}

			setTimeout(() => {
				if (els.siteDetailAddress) {
					els.siteDetailAddress.focus();
				}
			}, 120);

			return;
		}

		if (els.useCompanyAddress) {
			els.useCompanyAddress.checked = item.label === '회원주소';
		}

		state.useCompanyAddress = item.label === '회원주소';

		setAddressState({
			zipCode: item.zipCode,
			doName: item.doName,
			siName: item.siName,
			guName: item.guName,
			roadAddress: item.roadAddress,
			detailAddress: item.detailAddress
		}, item.label === '회원주소' ? 'COMPANY' : 'COMPANY_DELIVERY_ADDRESS');

		if (els.companyAddressModal) {
			els.companyAddressModal.hide();
		}

		setTimeout(() => {
			els.detailAddress.focus();
		}, 120);
	}


	function handleUseCommonSiteAddressChange() {
		state.useCommonSiteAddress = Boolean(els.useCommonSiteAddress.checked);

		if (state.useCommonSiteAddress) {
			applyCommonAddressToSite();
			return;
		}

		state.siteAddressSource = 'MANUAL';
		clearSiteAddressFields();
	}

	function applyCommonAddressToSite() {
		setSiteAddressState({
			zipCode: state.address.zipCode || '',
			doName: state.address.doName || '',
			siName: state.address.siName || '',
			guName: state.address.guName || '',
			roadAddress: state.address.roadAddress || '',
			detailAddress: state.address.detailAddress || ''
		}, 'COMMON', true);
	}

	function clearSiteAddressFields() {
		setSiteAddressState({
			zipCode: '',
			doName: '',
			siName: '',
			guName: '',
			roadAddress: '',
			detailAddress: ''
		}, state.siteAddressSource || 'MANUAL');
	}

	function setSiteAddressState(address, source, skipRefresh = false) {
		state.siteAddress = {
			zipCode: normalizeZipCode(address.zipCode || ''),
			doName: (address.doName || '').trim(),
			siName: (address.siName || '').trim(),
			guName: (address.guName || '').trim(),
			roadAddress: (address.roadAddress || '').trim(),
			detailAddress: (address.detailAddress || '').trim()
		};

		state.siteAddressSource = source || 'MANUAL';

		syncSiteAddressInputs();
		clearSiteAddressFeedback();
		renderSiteAddressSummary();

		if (!skipRefresh) {
			refreshActionButtons();
		}
	}

	function syncSiteAddressInputs() {
		if (!els.siteZipCode) {
			return;
		}

		els.siteZipCode.value = state.siteAddress.zipCode || '';
		els.siteDoName.value = state.siteAddress.doName || '';
		els.siteSiName.value = state.siteAddress.siName || '';
		els.siteGuName.value = state.siteAddress.guName || '';
		els.siteRoadAddress.value = state.siteAddress.roadAddress || '';
		els.siteDetailAddress.value = state.siteAddress.detailAddress || '';
	}

	function clearSiteAddressFeedback() {
		if (!els.siteZipCode) {
			return;
		}

		els.siteZipCode.classList.remove('product-admin-add-invalid');
		els.siteRoadAddress.classList.remove('product-admin-add-invalid');
		els.siteDetailAddress.classList.remove('product-admin-add-invalid');

		if (els.siteAddressFeedback) {
			els.siteAddressFeedback.textContent = '';
		}
	}

	function hasSiteAddressCoreFields() {
		if (!isSiteDeliverySelected()) {
			return true;
		}

		return Boolean(
			(state.siteAddress.zipCode || '').trim() &&
			(state.siteAddress.doName || '').trim() &&
			(state.siteAddress.roadAddress || '').trim()
		);
	}

	function validateSiteAddressFields(showMessage = false) {
		if (!isSiteDeliverySelected()) {
			clearSiteAddressFeedback();
			return true;
		}

		const valid = hasSiteAddressCoreFields();

		if (valid) {
			clearSiteAddressFeedback();
			return true;
		}

		if (showMessage && els.siteZipCode) {
			els.siteZipCode.classList.add('product-admin-add-invalid');
			els.siteRoadAddress.classList.add('product-admin-add-invalid');
			els.siteAddressFeedback.textContent = '현장 배송지를 선택해 주세요.';
		}

		return false;
	}

	function renderSiteAddressSummary() {
		if (!els.siteAddressSummary) {
			return;
		}

		if (!isSiteDeliverySelected()) {
			els.siteAddressSummary.classList.add('d-none');
			els.siteAddressSummary.innerHTML = '';
			return;
		}

		const hasAddress = Boolean(
			(state.siteAddress.zipCode || '').trim() ||
			(state.siteAddress.roadAddress || '').trim() ||
			(state.siteAddress.detailAddress || '').trim()
		);

		if (!hasAddress) {
			els.siteAddressSummary.classList.add('d-none');
			els.siteAddressSummary.innerHTML = '';
			return;
		}

		const region = [
			state.siteAddress.doName,
			state.siteAddress.siName,
			state.siteAddress.guName
		].filter(item => item && item.trim()).join(' / ');

		const fullAddress = [
			state.siteAddress.roadAddress,
			state.siteAddress.detailAddress
		].filter(item => item && item.trim()).join(' ');

		els.siteAddressSummary.innerHTML = `
            <div><strong>우편번호</strong> ${escapeHtml(state.siteAddress.zipCode || '-')}</div>
            <div><strong>현장 배송지</strong> ${escapeHtml(fullAddress || '-')}</div>
            <div><strong>행정구역</strong> ${escapeHtml(region || '-')}</div>
        `;
		els.siteAddressSummary.classList.remove('d-none');
	}

	function syncSiteAddressSection() {
		const visible = isSiteDeliverySelected();

		if (els.siteAddressSection) {
			els.siteAddressSection.classList.toggle('d-none', !visible);
		}

		if (visible && state.useCommonSiteAddress) {
			applyCommonAddressToSite();
		}

		renderSiteAddressSummary();
	}


	function handleUseCompanyOrdererChange() {
		state.useCompanyOrderer = Boolean(els.useCompanyOrderer.checked);

		if (state.useCompanyOrderer) {
			applyCompanyDefaultOrderer();
			return;
		}

		state.ordererSource = 'MANUAL';
		clearOrdererFields();
	}

	function applyCompanyDefaultOrderer() {
		if (!state.company) {
			clearOrdererFields();
			return;
		}

		setOrdererState({
			ordererName: state.company.representativeName || '',
			ordererPhone: state.company.representativePhone || ''
		}, 'COMPANY');
	}

	function clearOrdererFields() {
		setOrdererState({
			ordererName: '',
			ordererPhone: ''
		}, state.ordererSource || 'MANUAL');
	}

	function setOrdererState(orderer, source) {
		state.orderer = {
			ordererName: (orderer.ordererName || '').trim(),
			ordererPhone: (orderer.ordererPhone || '').trim()
		};

		state.ordererSource = source || 'MANUAL';

		syncOrdererInputs();
		renderOrdererSummary();
	}

	function syncOrdererInputs() {
		if (els.ordererName) {
			els.ordererName.value = state.orderer.ordererName || '';
		}

		if (els.ordererPhone) {
			els.ordererPhone.value = state.orderer.ordererPhone || '';
		}
	}

	async function openCompanyOrdererModal() {
		if (!state.company) {
			alert('먼저 대리점을 선택해 주세요.');
			return;
		}

		if (!els.companyOrdererModal || !els.companyOrdererList || !els.companyOrdererEmpty) {
			alert('주문자정보검색 모달 HTML이 없습니다.');
			return;
		}

		try {
			await loadCompanyOrdererInfosIfNeeded();
			renderCompanyOrdererModal();
			els.companyOrdererModal.show();
		} catch (error) {
			alert(error.message || '주문자 정보 목록을 불러오지 못했습니다.');
		}
	}

	async function loadCompanyOrdererInfosIfNeeded() {
		if (
			state.companyOrdererInfoLoadedForCompanyId &&
			String(state.companyOrdererInfoLoadedForCompanyId) === String(state.company.companyId)
		) {
			return;
		}

		state.companyOrdererInfos = await fetchJson(
			`${API_BASE}/companies/${encodeURIComponent(state.company.companyId)}/orderer-infos`
		);
		state.companyOrdererInfoLoadedForCompanyId = state.company.companyId;
	}

	function renderCompanyOrdererModal() {
		if (!state.companyOrdererInfos.length) {
			els.companyOrdererEmpty.classList.remove('d-none');
			els.companyOrdererList.innerHTML = '';
			return;
		}

		els.companyOrdererEmpty.classList.add('d-none');

		els.companyOrdererList.innerHTML = state.companyOrdererInfos.map(item => `
            <button type="button"
                class="product-admin-add-company-orderer-item"
                data-action="select-company-orderer"
                data-orderer-id="${item.id}">
                <span class="product-admin-add-company-orderer-main">
                    ${escapeHtml(item.ordererName || '-')}
                </span>
                <span class="product-admin-add-company-orderer-sub">
                    ${escapeHtml(item.ordererPhone || '-')}
                </span>
            </button>
        `).join('');
	}

	function handleCompanyOrdererModalClick(event) {
		const button = event.target.closest('[data-action="select-company-orderer"][data-orderer-id]');
		if (!button) {
			return;
		}

		const item = state.companyOrdererInfos.find(orderer =>
			String(orderer.id) === String(button.dataset.ordererId)
		);

		if (!item) {
			return;
		}

		if (els.useCompanyOrderer) {
			els.useCompanyOrderer.checked = false;
		}

		state.useCompanyOrderer = false;

		setOrdererState({
			ordererName: item.ordererName,
			ordererPhone: item.ordererPhone
		}, 'COMPANY_ORDERER_INFO');

		if (els.companyOrdererModal) {
			els.companyOrdererModal.hide();
		}

		setTimeout(() => {
			if (els.ordererName) {
				els.ordererName.focus();
			}
		}, 120);
	}

	function renderOrdererSummary() {
		if (!els.ordererSummary) {
			return;
		}

		const hasOrderer = Boolean(
			(state.orderer.ordererName || '').trim() ||
			(state.orderer.ordererPhone || '').trim()
		);

		if (!hasOrderer) {
			els.ordererSummary.classList.add('d-none');
			els.ordererSummary.innerHTML = '';
			return;
		}

		els.ordererSummary.innerHTML = `
            <div><strong>주문자</strong> ${escapeHtml(state.orderer.ordererName || '-')}</div>
            <div><strong>연락처</strong> ${escapeHtml(state.orderer.ordererPhone || '-')}</div>
        `;
		els.ordererSummary.classList.remove('d-none');
	}


	function openAddressSearch(mode = 'COMMON') {
		if (!window.daum || !window.daum.Postcode) {
			alert('주소검색 스크립트를 불러오지 못했습니다.');
			return;
		}

		const targetMode = mode === 'SITE' ? 'SITE' : 'COMMON';

		new daum.Postcode({
			oncomplete: function(data) {
				const parsed = parsePostcodeData(data);

				if (targetMode === 'SITE') {
					if (els.useCommonSiteAddress) {
						els.useCommonSiteAddress.checked = false;
					}

					state.useCommonSiteAddress = false;

					setSiteAddressState({
						zipCode: parsed.zipCode,
						doName: parsed.doName,
						siName: parsed.siName,
						guName: parsed.guName,
						roadAddress: parsed.roadAddress,
						detailAddress: state.siteAddress.detailAddress || ''
					}, 'MANUAL');

					setTimeout(() => {
						if (els.siteDetailAddress) {
							els.siteDetailAddress.focus();
						}
					}, 0);

					return;
				}

				if (els.useCompanyAddress) {
					els.useCompanyAddress.checked = false;
				}

				state.useCompanyAddress = false;

				setAddressState({
					zipCode: parsed.zipCode,
					doName: parsed.doName,
					siName: parsed.siName,
					guName: parsed.guName,
					roadAddress: parsed.roadAddress,
					detailAddress: state.address.detailAddress || ''
				}, 'MANUAL');

				setTimeout(() => {
					els.detailAddress.focus();
				}, 0);
			}
		}).open();
	}

	function parsePostcodeData(data) {
		const sigunguParts = splitSigungu(data.sigungu || '');

		return {
			zipCode: (data.zonecode || '').trim(),
			doName: (data.sido || '').trim(),
			siName: sigunguParts.siName,
			guName: sigunguParts.guName,
			roadAddress: ((data.roadAddress || data.jibunAddress || '')).trim()
		};
	}

	function splitSigungu(sigungu) {
		const normalized = String(sigungu || '').trim();

		if (!normalized) {
			return {
				siName: '',
				guName: ''
			};
		}

		const tokens = normalized.split(/\s+/).filter(Boolean);

		if (tokens.length >= 2) {
			return {
				siName: tokens[0],
				guName: tokens.slice(1).join(' ')
			};
		}

		const only = tokens[0];

		if (/(구|군)$/.test(only)) {
			return {
				siName: '',
				guName: only
			};
		}

		return {
			siName: only,
			guName: ''
		};
	}

	function renderAddressSummary() {
		const hasAddress = hasAddressCoreFields();

		if (!hasAddress) {
			els.addressSummary.classList.add('d-none');
			els.addressSummary.innerHTML = '';
			return;
		}

		const region = [
			state.address.doName,
			state.address.siName,
			state.address.guName
		].filter(item => item && item.trim()).join(' / ');

		const fullAddress = [
			state.address.roadAddress,
			state.address.detailAddress
		].filter(item => item && item.trim()).join(' ');

		els.addressSummary.innerHTML = `
            <div><strong>우편번호</strong> ${escapeHtml(state.address.zipCode || '-')}</div>
            <div><strong>배송지</strong> ${escapeHtml(fullAddress || '-')}</div>
            <div><strong>행정구역</strong> ${escapeHtml(region || '-')}</div>
        `;
		els.addressSummary.classList.remove('d-none');
	}

	function clearAddressFeedback() {
		els.zipCode.classList.remove('product-admin-add-invalid');
		els.roadAddress.classList.remove('product-admin-add-invalid');
		els.detailAddress.classList.remove('product-admin-add-invalid');
		els.addressFeedback.textContent = '';
	}

	function hasAddressCoreFields() {
		return Boolean(
			(state.address.zipCode || '').trim() &&
			(state.address.doName || '').trim() &&
			(state.address.roadAddress || '').trim()
		);
	}

	function validateAddressFields(showMessage = false) {
		const valid = hasAddressCoreFields();

		if (valid) {
			clearAddressFeedback();
			return true;
		}

		if (showMessage) {
			els.zipCode.classList.add('product-admin-add-invalid');
			els.roadAddress.classList.add('product-admin-add-invalid');
			els.addressFeedback.textContent = '공통 배송지를 선택해 주세요.';
		}

		return false;
	}
	function refreshActionButtons() {
		const commonReady = Boolean(
			state.company &&
			state.deliveryMethod &&
			els.preferredDate.value &&
			hasAddressCoreFields() &&
			hasSiteAddressCoreFields()
		);

		els.orderAddBtn.disabled = !commonReady;
		els.previewBtn.disabled = state.saving || !commonReady || state.orders.length === 0;

		if (els.companyAddressSearchBtn) {
			els.companyAddressSearchBtn.disabled = !state.company;
		}

		if (els.siteCompanyAddressSearchBtn) {
			els.siteCompanyAddressSearchBtn.disabled = !state.company;
		}

		if (els.companyOrdererSearchBtn) {
			els.companyOrdererSearchBtn.disabled = !state.company;
		}

		syncSiteAddressSection();
		syncDeliveryHandlerAvailability();
		syncOrderAddButtonPosition();
	}
	function handleAddOrder() {
		hideTopMessage();

		if (!validateCommonFields(true)) {
			return;
		}

		state.orders.forEach(item => {
			item.collapsed = true;
		});

		const order = createEmptyOrder();
		state.orders.push(order);
		state.activePasteOrderId = order.id;

		els.emptyState.classList.add('d-none');
		renderOrders();

		refreshBottomSummary();
		refreshActionButtons();

		smoothScrollToOrder(order.id, '.product-admin-add-type-btn[data-standard="true"]');
	}
	function createEmptyOrder() {
		return {
			id: `order-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
			collapsed: false,
			validationErrors: {},

			standard: null,
			standardCategoryId: '',
			standardSeriesId: '',
			standardSeriesOptions: [],

			assignedProductionCategoryId: null,
			assignedProductionCategoryName: '',
			assignedProductionCategoryFallback: false,
			assignedProductionCategorySourceName: '',

			customProductionCategoryId: null,
			customProductionCategoryName: '',
			customProductionInput: '',
			productionDropdownIndex: -1,

			productName: '',
			productSize: '',
			productColor: '',
			mirrorCuttingProduct: false,
			optionDraft: '',
			optionPairs: [],

			files: [],
			productCost: '',
			quantity: 1,
			supplyPrice: '',
			totalAmount: '',
			orderComment: '',
			adminMemo: ''
		};
	}

	function renderOrders() {
		els.emptyState.classList.toggle('d-none', state.orders.length > 0);

		if (!state.orders.length) {
			els.orderList.innerHTML = '';
			refreshBottomSummary();
			refreshActionButtons();
			return;
		}

		els.orderList.innerHTML = state.orders
			.map((order, index) => renderOrderCardWrap(order, index))
			.join('');

		refreshBottomSummary();
		refreshActionButtons();
	}

	function appendOrderCard(order, index) {
		els.orderList.insertAdjacentHTML('beforeend', renderOrderCardWrap(order, index));
	}

	function rerenderSingleOrder(orderId, options = {}) {
		const { focusSelector = null } = options;

		const order = findOrder(orderId);
		const orderIndex = state.orders.findIndex(item => item.id === orderId);
		const wrap = document.getElementById(`product-admin-add-order-wrap-${orderId}`);

		if (!order || orderIndex < 0 || !wrap) {
			renderOrders();
			return;
		}

		wrap.outerHTML = renderOrderCardWrap(order, orderIndex);

		refreshBottomSummary();
		refreshActionButtons();

		if (focusSelector) {
			requestAnimationFrame(() => {
				const card = document.getElementById(`product-admin-add-order-card-${orderId}`);
				const target = card ? card.querySelector(focusSelector) : null;

				if (target) {
					target.focus();
				}
			});
		}
	}

	function smoothScrollToOrder(orderId, focusSelector) {
		requestAnimationFrame(() => {
			const wrap = document.getElementById(`product-admin-add-order-wrap-${orderId}`);

			if (!wrap) {
				return;
			}

			wrap.scrollIntoView({
				behavior: 'smooth',
				block: 'start'
			});

			setTimeout(() => {
				const card = document.getElementById(`product-admin-add-order-card-${orderId}`);
				const target = card ? card.querySelector(focusSelector) : null;

				if (target) {
					target.focus();
				}
			}, 320);
		});
	}

	function renderOrderCardWrap(order, index) {
		return `
            <div class="col-12 product-admin-add-order-card-wrap" id="product-admin-add-order-wrap-${order.id}">
                ${renderOrderCard(order, index)}
            </div>
        `;
	}
	function renderOrderCard(order, index) {
		const collapsed = Boolean(order.collapsed);
		const orderTotal = getOrderTotalAmount(order);
		const orderTypeLabel = order.standard === true ? '규격' : order.standard === false ? '비규격' : '미선택';
		const errorCount = Object.keys(order.validationErrors || {}).length;

		return `
            <div class="card product-admin-add-order-card ${errorCount ? 'product-admin-add-order-card-has-error' : ''}"
                id="product-admin-add-order-card-${order.id}">
                <div class="card-header">
                    <div class="d-flex align-items-center justify-content-between gap-3 flex-wrap">
                        <button type="button"
                            class="product-admin-add-order-toggle"
                            data-action="toggle-order"
                            data-order-id="${order.id}"
                            aria-expanded="${collapsed ? 'false' : 'true'}">
                            <i class="${collapsed ? 'ri-arrow-down-s-line' : 'ri-arrow-up-s-line'}"></i>
                            <span class="product-admin-add-order-no">주문 ${index + 1}</span>
                            <span class="product-admin-add-order-title">${escapeHtml(getOrderDisplayName(order))}</span>
                        </button>

                        <div class="d-flex align-items-center gap-2 flex-wrap justify-content-end">
                            ${errorCount ? `<span class="badge bg-danger-subtle text-danger">미비 ${errorCount}건</span>` : ''}
                            <span class="badge bg-soft-primary text-primary">${orderTypeLabel}</span>
                            <span class="badge bg-light text-dark border">${formatCurrency(orderTotal)}</span>

                            <button type="button"
                                class="btn btn-outline-danger btn-sm"
                                data-action="remove-order"
                                data-order-id="${order.id}">
                                삭제
                            </button>
                        </div>
                    </div>
                </div>

                <div class="product-admin-add-order-collapse ${collapsed ? 'd-none' : ''}" id="product-admin-add-order-body-${order.id}">
                    <div class="card-body">
                        <div class="row g-4">
                            <div class="col-12">
                                <div class="product-admin-add-section-box">
                                    <div class="product-admin-add-section-title">
                                        <h6>주문 타입 선택</h6>
                                    </div>

                                    <div class="product-admin-add-type-toggle" data-error-anchor="standard">
                                        <button type="button"
                                            class="product-admin-add-type-btn ${order.standard === true ? 'active' : ''}"
                                            data-action="set-type"
                                            data-order-id="${order.id}"
                                            data-standard="true">
                                            규격 주문
                                        </button>

                                        <button type="button"
                                            class="product-admin-add-type-btn ${order.standard === false ? 'active' : ''}"
                                            data-action="set-type"
                                            data-order-id="${order.id}"
                                            data-standard="false">
                                            비규격 주문
                                        </button>
                                    </div>

                                    ${renderFieldError(order, 'standard')}
                                </div>
                            </div>

                            ${renderOrderTypeSection(order)}
                            ${renderProductOptionSection(order)}
                            ${renderFileUploadSection(order)}
                            ${renderOrderMetaSection(order)}
                        </div>
                    </div>
                </div>
            </div>
        `;
	}
	function renderOrderTypeSection(order) {
		if (order.standard === true) {
			return `
                <div class="col-12">
                    <div class="product-admin-add-section-box">
                        <div class="product-admin-add-section-title">
						    <h6>규격 주문 정보</h6>
						    <span id="product-admin-add-standard-badge-${order.id}"
						        class="product-admin-add-standard-badge-wrap">
						        ${renderStandardAssignedBadge(order)}
						    </span>
						</div>

                        <div class="row g-3">
                            <div class="col-12 col-lg-6">
                                <label class="form-label product-admin-add-label">규격 대분류</label>
                                <select class="form-select ${hasFieldError(order, 'standardCategoryId') ? 'product-admin-add-invalid' : ''}"
                                    data-field="standardCategoryId"
                                    data-error-anchor="standardCategoryId"
                                    data-order-id="${order.id}">
                                    <option value="">대분류 선택</option>
                                    ${state.standardCategories.map(item => `
                                        <option value="${item.id}" ${String(order.standardCategoryId) === String(item.id) ? 'selected' : ''}>
                                            ${escapeHtml(item.name)}
                                        </option>
                                    `).join('')}
                                </select>
                                ${renderFieldError(order, 'standardCategoryId')}
                            </div>

                            <div class="col-12 col-lg-6">
                                <label class="form-label product-admin-add-label">규격 중분류</label>
                                <select class="form-select"
                                    data-field="standardSeriesId"
                                    data-order-id="${order.id}">
                                    <option value="">중분류 없음 / 선택 안 함</option>
                                    ${(order.standardSeriesOptions || []).map(item => `
                                        <option value="${item.id}" ${String(order.standardSeriesId) === String(item.id) ? 'selected' : ''}>
                                            ${escapeHtml(item.name)}
                                        </option>
                                    `).join('')}
                                </select>
                            </div>
                        </div>
                    </div>
                </div>
            `;
		}

		if (order.standard === false) {
			return `
                <div class="col-12">
                    <div class="product-admin-add-section-box">
                        <div class="product-admin-add-section-title">
                            <h6>비규격 주문 정보</h6>
                        </div>

                        <label class="form-label product-admin-add-label">생산팀 분류 선택</label>

                        <div class="position-relative" data-error-anchor="customProductionCategoryId">
                            <input type="text"
                                class="form-control product-admin-add-control product-admin-add-production-input ${hasFieldError(order, 'customProductionCategoryId') ? 'product-admin-add-invalid' : ''}"
                                data-order-id="${order.id}"
                                value="${escapeHtml(order.customProductionInput || '')}"
                                autocomplete="off"
                                placeholder="생산팀 분류명을 입력 후 선택해 주세요.">

                            <div class="product-admin-add-autocomplete product-admin-add-production-dropdown d-none"
                                id="product-admin-add-production-dropdown-${order.id}"></div>
                        </div>

                        ${renderFieldError(order, 'customProductionCategoryId')}

                        <div class="invalid-feedback d-block product-admin-add-feedback"
                            id="product-admin-add-production-feedback-${order.id}"></div>

                        ${order.customProductionCategoryName
					? `<div class="product-admin-add-selected-summary mt-2">선택된 생산팀 분류: <strong>${escapeHtml(order.customProductionCategoryName)}</strong></div>`
					: ''
				}
                    </div>
                </div>
            `;
		}

		return `
            <div class="col-12">
                <div class="product-admin-add-section-box">
                    <div class="text-muted">먼저 규격 / 비규격 중 하나를 선택해 주세요.</div>
                </div>
            </div>
        `;
	}

	function renderStandardAssignedBadge(order) {
		if (!order.standardCategoryId) {
			return `<span class="badge bg-soft-warning text-warning">대분류 선택 시 생산팀 분류가 자동 지정됩니다.</span>`;
		}

		if (order.assignedProductionCategoryName) {
			if (order.assignedProductionCategoryFallback) {
				return `
                    <span class="product-admin-add-inline-badge">
                        생산팀 분류 자동지정: ${escapeHtml(order.assignedProductionCategoryName)} (기본 분류)
                    </span>
                `;
			}

			return `
                <span class="product-admin-add-inline-badge">
                    생산팀 분류 자동지정: ${escapeHtml(order.assignedProductionCategoryName)}
                </span>
            `;
		}

		return `<span class="badge bg-soft-danger text-danger">생산팀 분류 자동 지정 실패</span>`;
	}

	function updateStandardAssignedBadge(orderId) {
		const order = findOrder(orderId);
		const badgeWrap = document.getElementById(`product-admin-add-standard-badge-${orderId}`);

		if (!order || !badgeWrap) {
			return;
		}

		badgeWrap.innerHTML = renderStandardAssignedBadge(order);
	}

	function updateStandardSeriesSelect(orderId) {
		const order = findOrder(orderId);

		if (!order) {
			return;
		}

		const select = els.orderList.querySelector(
			`select[data-field="standardSeriesId"][data-order-id="${orderId}"]`
		);

		if (!select) {
			return;
		}

		select.innerHTML = `
		<option value="">중분류 없음 / 선택 안 함</option>
		${(order.standardSeriesOptions || []).map(item => `
			<option value="${item.id}" ${String(order.standardSeriesId) === String(item.id) ? 'selected' : ''}>
				${escapeHtml(item.name)}
			</option>
		`).join('')}
	`;
	}
	function renderProductOptionSection(order) {
		return `
            <div class="col-12">
                <div class="product-admin-add-section-box">
                    <div class="product-admin-add-section-title">

                        <h6>제품 정보 / 옵션 입력</h6>


                        <div class="form-check form-switch mb-0">

                            <input type="checkbox"

                                class="form-check-input"

                                id="product-admin-add-mirror-cutting-${order.id}"

                                data-field="mirrorCuttingProduct"

                                data-order-id="${order.id}"

                                ${order.mirrorCuttingProduct ? 'checked' : ''}>

                            <label class="form-check-label product-admin-add-label mb-0"

                                for="product-admin-add-mirror-cutting-${order.id}">

                                거울재단용

                            </label>

                        </div>

                    </div>

                    <div class="product-admin-add-option-help">
                        제품명 → Enter → 사이즈 → Enter → 색상 → Enter → 옵션 입력 순서로 이동합니다.
                        옵션은 텍스트만 입력 후 Enter 를 누르면 <strong>옵션 : 입력값</strong> 형태로 추가됩니다.
                    </div>

                    <div class="product-admin-add-fixed-option-grid mt-3">
                        <div>
                            <label class="form-label product-admin-add-label">제품명</label>
                            <input type="text"
                                class="form-control product-admin-add-control product-admin-add-product-name ${hasFieldError(order, 'productName') ? 'product-admin-add-invalid' : ''}"
                                data-field="productName"
                                data-error-anchor="productName"
                                data-order-id="${order.id}"
                                value="${escapeHtml(order.productName || '')}"
                                placeholder="제품명을 입력해 주세요.">
                            ${renderFieldError(order, 'productName')}
                        </div>

                        <div>
                            <label class="form-label product-admin-add-label">사이즈</label>
                            <input type="text"
                                class="form-control product-admin-add-control product-admin-add-product-size ${hasFieldError(order, 'productSize') ? 'product-admin-add-invalid' : ''}"
                                data-field="productSize"
                                data-error-anchor="productSize"
                                data-order-id="${order.id}"
                                value="${escapeHtml(order.productSize || '')}"
                                placeholder="사이즈를 입력해 주세요.">
                            ${renderFieldError(order, 'productSize')}
                        </div>

                        <div>
                            <label class="form-label product-admin-add-label">색상</label>
                            <input type="text"
                                class="form-control product-admin-add-control product-admin-add-product-color ${hasFieldError(order, 'productColor') ? 'product-admin-add-invalid' : ''}"
                                data-field="productColor"
                                data-error-anchor="productColor"
                                data-order-id="${order.id}"
                                value="${escapeHtml(order.productColor || '')}"
                                placeholder="색상을 입력해 주세요.">
                            ${renderFieldError(order, 'productColor')}
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-12 col-lg-9">
                            <label class="form-label product-admin-add-label">옵션</label>
                            <textarea
                                class="form-control product-admin-add-option-textarea"
                                rows="2"
                                data-order-id="${order.id}"
                                placeholder="옵션 내용을 입력 후 Enter">${escapeHtml(order.optionDraft || '')}</textarea>
                        </div>

                       <div class="col-12 col-lg-3">
						    <label class="form-label product-admin-add-label product-admin-add-option-btn-label">&nbsp;</label>
						
						    <button type="button"
						        class="btn btn-outline-primary w-100 product-admin-add-main-btn product-admin-add-option-add-btn"
						        data-action="add-option"
						        data-order-id="${order.id}">
						        옵션 추가
						    </button>
						</div>
                    </div>

                    <div class="mt-3 product-admin-add-option-list" data-order-id="${order.id}">
                        ${renderOptionListHtml(order)}
                    </div>
                </div>
            </div>
        `;
	}

	function renderFileUploadSection(order) {
		return `
            <div class="col-12">
                <div class="product-admin-add-section-box">
                    <div class="product-admin-add-section-title">
                        <h6>파일 업로드</h6>
                    </div>

                    <div class="product-admin-add-upload-box product-admin-add-drop-zone"
					    data-drop-zone="true"
					    data-order-id="${order.id}"
					    tabindex="0"
					    aria-label="파일 업로드 영역">

                        <div class="product-admin-add-file-toolbar">
                            <div>
                                <div class="text-muted">0장 ~ 여러 장까지 업로드할 수 있습니다.</div>
                                <div class="product-admin-add-drop-help">
                                    파일을 이 영역으로 드래그 앤 드랍하거나, 파일 선택 또는 Ctrl+V로 복사한 이미지를 추가해 주세요.
                                </div>
                            </div>

                            <div class="d-flex gap-2">
                                <button type="button"
                                    class="btn btn-outline-secondary"
                                    data-action="open-file"
                                    data-order-id="${order.id}">
                                    파일 선택
                                </button>
                            </div>
                        </div>

                        <input type="file"
                            class="d-none product-admin-add-file-input"
                            id="product-admin-add-file-input-${order.id}"
                            data-order-id="${order.id}"
                            multiple>

                        <div class="product-admin-add-file-preview-list" data-order-id="${order.id}">
                            ${renderFileListHtml(order)}
                        </div>
                    </div>
                </div>
            </div>
        `;
	}
	function renderOrderMetaSection(order) {
		const supplyPrice = getOrderSupplyPrice(order);
		const totalAmount = getOrderTotalAmount(order);
		const vatAmount = getOrderVatAmount(order);

		return `
            <div class="col-12">
                <div class="product-admin-add-section-box">
                    <div class="product-admin-add-section-title">
                        <h6>가격 및 수량</h6>
                    </div>

                    <div class="row g-3 product-admin-add-order-meta-row">
                        <div class="col-12 col-lg-2">
                            <label class="form-label product-admin-add-label">단가</label>
                            <input type="text"
                                inputmode="numeric"
                                class="form-control product-admin-add-control product-admin-add-money-input ${hasFieldError(order, 'productCost') ? 'product-admin-add-invalid' : ''}"
                                data-field="productCost"
                                data-error-anchor="productCost"
                                data-order-id="${order.id}"
                                value="${escapeHtml(formatNumberInput(order.productCost))}"
                                placeholder="0">
                            ${renderFieldError(order, 'productCost')}
                        </div>

                        <div class="col-12 col-lg-2">
                            <label class="form-label product-admin-add-label">수량</label>
                            <input type="text"
                                inputmode="numeric"
                                class="form-control product-admin-add-control product-admin-add-money-input ${hasFieldError(order, 'quantity') ? 'product-admin-add-invalid' : ''}"
                                data-field="quantity"
                                data-error-anchor="quantity"
                                data-order-id="${order.id}"
                                value="${escapeHtml(formatNumberInput(order.quantity))}"
                                placeholder="1">
                            ${renderFieldError(order, 'quantity')}
                        </div>

                        <div class="col-12 col-lg-3">
                            <label class="form-label product-admin-add-label">공급가</label>
                            <input type="text"
                                inputmode="numeric"
                                class="form-control product-admin-add-control product-admin-add-money-input"
                                data-field="supplyPrice"
                                data-order-id="${order.id}"
                                value="${escapeHtml(formatNumberInput(order.supplyPrice))}"
                                placeholder="0">
                        </div>

                        <div class="col-12 col-lg-2">
                            <label class="form-label product-admin-add-label">부가세 10%</label>
                            <div class="form-control product-admin-add-control d-flex align-items-center bg-light product-admin-add-vat-display"
                                data-price-display="vatAmount"
                                data-order-id="${order.id}">
                                <strong>${formatCurrency(vatAmount)}</strong>
                            </div>
                        </div>

                        <div class="col-12 col-lg-3">
                            <label class="form-label product-admin-add-label">총액</label>
                            <input type="text"
                                inputmode="numeric"
                                class="form-control product-admin-add-control product-admin-add-money-input"
                                data-field="totalAmount"
                                data-order-id="${order.id}"
                                value="${escapeHtml(formatNumberInput(order.totalAmount))}"
                                placeholder="0">
                        </div>

                        <div class="col-12">
                            <div class="product-admin-add-price-help">
                                단가 또는 수량 입력 시 공급가와 총액이 자동 계산됩니다. 공급가 또는 총액은 할인/DP/무상 지급 상황에 맞게 직접 수정할 수 있습니다.
                            </div>
                        </div>

                        <div class="col-12">
                            <label class="form-label product-admin-add-label">고객 남김말 (선택)</label>
                            <textarea
                                class="form-control"
                                rows="3"
                                data-field="orderComment"
                                data-order-id="${order.id}"
                                placeholder="고객 남김말로 저장됩니다.">${escapeHtml(order.orderComment || '')}</textarea>
                        </div>

                        <div class="col-12">
                            <label class="form-label product-admin-add-label">관리자 남김말 (선택)</label>
                            <textarea
                                class="form-control"
                                rows="3"
                                data-field="adminMemo"
                                data-order-id="${order.id}"
                                placeholder="관리자 내부 메모로 저장됩니다.">${escapeHtml(order.adminMemo || '')}</textarea>
                        </div>
                    </div>
                </div>
            </div>
        `;
	}

	function renderOptionListHtml(order) {
		if (!order.optionPairs.length) {
			return '<span class="text-muted">등록된 옵션이 없습니다.</span>';
		}

		return order.optionPairs.map(pair => `
            <div class="product-admin-add-option-chip">
                <span class="product-admin-add-option-chip-text">옵션 : ${escapeHtml(pair.value)}</span>

                <button type="button"
                    class="product-admin-add-chip-remove"
                    data-action="remove-option"
                    data-order-id="${order.id}"
                    data-pair-id="${pair.id}">
                    ×
                </button>
            </div>
        `).join('');
	}

	function renderFileListHtml(order) {
		if (!order.files.length) {
			return '<span class="text-muted">선택된 파일이 없습니다.</span>';
		}

		return order.files.map(file => renderFileCard(order.id, file)).join('');
	}

	function renderFileCard(orderId, fileWrapper) {
		const isImage = fileWrapper.file &&
			fileWrapper.file.type &&
			fileWrapper.file.type.startsWith('image/');

		return `
            <div class="product-admin-add-file-card">
                <button type="button"
                    class="product-admin-add-file-remove"
                    data-action="remove-file"
                    data-order-id="${orderId}"
                    data-file-id="${fileWrapper.id}">
                    ×
                </button>

                ${isImage
				? `<img src="${fileWrapper.previewUrl}" alt="${escapeHtml(fileWrapper.file.name)}">`
				: `
                        <div class="product-admin-add-file-fallback">
                            <i class="ri-file-line"></i>
                            <div class="product-admin-add-file-name">${escapeHtml(fileWrapper.file.name)}</div>
                        </div>
                    `
			}
            </div>
        `;
	}
	function handleOrderListClick(event) {
		const actionEl = event.target.closest('[data-action]');

		if (!actionEl) {
			return;
		}

		const action = actionEl.dataset.action;
		const orderId = actionEl.dataset.orderId;
		const order = findOrder(orderId);

		if (action === 'toggle-order' && order) {
			order.collapsed = !Boolean(order.collapsed);
			renderOrders();
			return;
		}

		if (action === 'set-type' && order) {
			const standard = actionEl.dataset.standard === 'true';
			resetOrderTypeState(order, standard);
			clearOrderValidationError(order, 'standard');

			rerenderSingleOrder(orderId, {
				focusSelector: standard
					? '[data-field="standardCategoryId"]'
					: '.product-admin-add-production-input'
			});
			return;
		}

		if (action === 'remove-order' && order) {
			removeOrder(orderId);
			return;
		}

		if (action === 'add-option' && order) {
			const textarea = els.orderList.querySelector(
				`.product-admin-add-option-textarea[data-order-id="${orderId}"]`
			);

			if (textarea) {
				addOptionFromTextarea(order, textarea);
			}
			return;
		}

		if (action === 'remove-option' && order) {
			order.optionPairs = order.optionPairs.filter(pair => pair.id !== actionEl.dataset.pairId);
			updateOptionList(orderId);
			return;
		}

		if (action === 'open-file' && order) {
			const fileInput = document.getElementById(`product-admin-add-file-input-${orderId}`);

			if (fileInput) {
				fileInput.click();
			}
			return;
		}

		if (action === 'remove-file' && order) {
			const target = order.files.find(item => item.id === actionEl.dataset.fileId);

			if (target && target.previewUrl) {
				URL.revokeObjectURL(target.previewUrl);
			}

			order.files = order.files.filter(item => item.id !== actionEl.dataset.fileId);
			updateFileList(orderId);
		}
	}

	async function handleOrderListChange(event) {
		const target = event.target;
		const orderId = target.dataset.orderId;
		const order = findOrder(orderId);

		if (!order) {
			return;
		}

		if (target.matches('[data-field="mirrorCuttingProduct"]')) {
			order.mirrorCuttingProduct = Boolean(target.checked);
			hideTopMessage();
			return;
		}

		if (target.matches('[data-field="standardCategoryId"]')) {
			const selectedCategoryId = target.value;

			order.standardCategoryId = selectedCategoryId;
			order.standardSeriesId = '';
			order.standardSeriesOptions = [];
			clearOrderValidationError(order, 'standardCategoryId');

			assignProductionCategoryByStandardCategory(order);
			updateStandardAssignedBadge(orderId);
			updateStandardSeriesSelect(orderId);

			const seriesSelect = els.orderList.querySelector(
				`select[data-field="standardSeriesId"][data-order-id="${orderId}"]`
			);

			if (seriesSelect) {
				seriesSelect.disabled = true;
				seriesSelect.innerHTML = '<option value="">중분류 불러오는 중...</option>';
			}

			try {
				const series = selectedCategoryId
					? await loadStandardSeries(selectedCategoryId)
					: [];

				if (String(order.standardCategoryId) !== String(selectedCategoryId)) {
					return;
				}

				order.standardSeriesOptions = series;
				order.standardSeriesId = '';

				updateStandardSeriesSelect(orderId);
				updateStandardAssignedBadge(orderId);

				focusOrderField(orderId, '[data-field="standardSeriesId"]');
			} finally {
				const latestSeriesSelect = els.orderList.querySelector(
					`select[data-field="standardSeriesId"][data-order-id="${orderId}"]`
				);

				if (latestSeriesSelect) {
					latestSeriesSelect.disabled = false;
				}
			}

			hideTopMessage();
			refreshActionButtons();
			return;
		}

		if (target.matches('[data-field="standardSeriesId"]')) {
			order.standardSeriesId = target.value;
			hideTopMessage();
			return;
		}

		if (target.matches('.product-admin-add-file-input')) {
			appendFiles(order, Array.from(target.files || []));
			target.value = '';
			updateFileList(orderId);
		}
	}
	function handleOrderListInput(event) {
		const target = event.target;
		const orderId = target.dataset.orderId;
		const order = findOrder(orderId);

		if (!order) {
			return;
		}

		if (target.matches('[data-field="productName"]')) {
			order.productName = target.value;
			clearOrderValidationError(order, 'productName');
			return;
		}

		if (target.matches('[data-field="productSize"]')) {
			order.productSize = target.value;
			clearOrderValidationError(order, 'productSize');
			return;
		}

		if (target.matches('[data-field="productColor"]')) {
			order.productColor = target.value;
			clearOrderValidationError(order, 'productColor');
			return;
		}

		if (target.matches('.product-admin-add-option-textarea')) {
			order.optionDraft = target.value;
			return;
		}

		if (target.matches('[data-field="productCost"]')) {
			order.productCost = sanitizeMoneyInputElement(target);
			clearOrderValidationError(order, 'productCost');
			recalculateOrderByUnitAndQuantity(order);
			updateOrderCardPriceSummary(orderId);
			refreshBottomSummary();
			return;
		}

		if (target.matches('[data-field="quantity"]')) {
			order.quantity = sanitizeMoneyInputElement(target);
			clearOrderValidationError(order, 'quantity');
			recalculateOrderByUnitAndQuantity(order);
			updateOrderCardPriceSummary(orderId);
			refreshBottomSummary();
			return;
		}

		if (target.matches('[data-field="supplyPrice"]')) {
			order.supplyPrice = sanitizeMoneyInputElement(target);
			recalculateOrderBySupplyPrice(order);
			updateOrderCardPriceSummary(orderId);
			refreshBottomSummary();
			return;
		}

		if (target.matches('[data-field="totalAmount"]')) {
			order.totalAmount = sanitizeMoneyInputElement(target);
			recalculateOrderByTotalAmount(order);
			updateOrderCardPriceSummary(orderId);
			refreshBottomSummary();
			return;
		}

		if (target.matches('[data-field="orderComment"]')) {
			order.orderComment = target.value;
			return;
		}

		if (target.matches('[data-field="adminMemo"]')) {
			order.adminMemo = target.value;
			return;
		}

		if (target.matches('.product-admin-add-production-input')) {
			order.customProductionInput = target.value;
			order.customProductionCategoryId = null;
			order.customProductionCategoryName = '';
			clearOrderValidationError(order, 'customProductionCategoryId');
			renderProductionDropdown(order);
		}
	}

	function handleOrderListKeydown(event) {
		const target = event.target;
		const orderId = target.dataset.orderId;
		const order = findOrder(orderId);

		if (!order) {
			return;
		}

		if (target.matches('[data-field="productName"]') && event.key === 'Enter') {
			event.preventDefault();
			focusOrderField(orderId, '[data-field="productSize"]');
			return;
		}

		if (target.matches('[data-field="productSize"]') && event.key === 'Enter') {
			event.preventDefault();
			focusOrderField(orderId, '[data-field="productColor"]');
			return;
		}

		if (target.matches('[data-field="productColor"]') && event.key === 'Enter') {
			event.preventDefault();
			focusOrderField(orderId, '.product-admin-add-option-textarea');
			return;
		}

		if (target.matches('.product-admin-add-option-textarea') && event.key === 'Enter' && !event.shiftKey) {
			event.preventDefault();
			addOptionFromTextarea(order, target);
			return;
		}

		if (event.key === 'Enter' && target.matches('[data-field="productCost"]')) {
			event.preventDefault();
			focusOrderField(orderId, '[data-field="quantity"]');
			return;
		}

		if (event.key === 'Enter' && target.matches('[data-field="quantity"]')) {
			event.preventDefault();
			focusOrderField(orderId, '[data-field="supplyPrice"]');
			return;
		}

		if (event.key === 'Enter' && target.matches('[data-field="supplyPrice"]')) {
			event.preventDefault();
			focusOrderField(orderId, '[data-field="totalAmount"]');
			return;
		}

		if (event.key === 'Enter' && target.matches('[data-field="totalAmount"]')) {
			event.preventDefault();
			focusOrderField(orderId, '[data-field="orderComment"]');
			return;
		}

		if (target.matches('.product-admin-add-production-input')) {
			const filtered = getFilteredProductionCategories(order.customProductionInput || '');

			if (event.key === 'ArrowDown') {
				if (!filtered.length) {
					return;
				}

				event.preventDefault();
				order.productionDropdownIndex = Math.min(
					(order.productionDropdownIndex ?? -1) + 1,
					filtered.length - 1
				);
				renderProductionDropdown(order);
				return;
			}

			if (event.key === 'ArrowUp') {
				if (!filtered.length) {
					return;
				}

				event.preventDefault();
				order.productionDropdownIndex = Math.max(
					(order.productionDropdownIndex ?? 0) - 1,
					0
				);
				renderProductionDropdown(order);
				return;
			}

			if (event.key === 'Enter') {
				event.preventDefault();

				if (!filtered.length) {
					validateExactProductionSelection(order);
					return;
				}

				const item = filtered[Math.max(order.productionDropdownIndex ?? 0, 0)];

				if (item) {
					selectProductionCategory(orderId, item);
				} else {
					validateExactProductionSelection(order);
				}
				return;
			}

			if (event.key === 'Escape') {
				closeProductionDropdown(order.id);
			}
		}
	}

	function focusOrderField(orderId, selector) {
		const card = document.getElementById(`product-admin-add-order-card-${orderId}`);
		const target = card ? card.querySelector(selector) : null;

		if (target) {
			target.focus();
		}
	}

	function handleOrderListFocusIn(event) {
		rememberPasteTargetByEvent(event);

		const target = event.target;
		const orderId = target.dataset.orderId;
		const order = findOrder(orderId);

		if (!order) {
			return;
		}

		if (target.matches('.product-admin-add-production-input')) {
			renderProductionDropdown(order);
		}
	}

	function handleOrderListFocusOut(event) {
		const target = event.target;
		const orderId = target.dataset.orderId;
		const order = findOrder(orderId);

		if (!order) {
			return;
		}

		if (target.matches('.product-admin-add-production-input')) {
			setTimeout(() => {
				validateExactProductionSelection(order);
				closeProductionDropdown(orderId);
			}, 140);
		}
	}

	function handleProductionDropdownPointerDown(event) {
		const button = event.target.closest('[data-production-index][data-order-id]');

		if (!button) {
			return;
		}

		event.preventDefault();
		event.stopPropagation();

		const orderId = button.dataset.orderId;
		const order = findOrder(orderId);

		if (!order) {
			return;
		}

		const items = getFilteredProductionCategories(order.customProductionInput || '');
		const item = items[Number(button.dataset.productionIndex)];

		if (item) {
			selectProductionCategory(orderId, item);
		}
	}

	function renderProductionDropdown(order) {
		const dropdown = document.getElementById(`product-admin-add-production-dropdown-${order.id}`);

		if (!dropdown) {
			return;
		}

		const items = getFilteredProductionCategories(order.customProductionInput || '');
		order.productionDropdownIndex = items.length
			? Math.max(order.productionDropdownIndex ?? 0, 0)
			: -1;

		if (!items.length) {
			dropdown.innerHTML = `
                <div class="product-admin-add-autocomplete-item">
                    <span class="product-admin-add-autocomplete-title">검색 결과가 없습니다.</span>
                    <span class="product-admin-add-autocomplete-sub">정확한 생산팀 분류를 선택해 주세요.</span>
                </div>
            `;
			dropdown.classList.remove('d-none');
			return;
		}

		dropdown.innerHTML = items.map((item, index) => `
            <button type="button"
                class="product-admin-add-autocomplete-item ${index === (order.productionDropdownIndex ?? 0) ? 'active' : ''}"
                data-order-id="${order.id}"
                data-production-index="${index}">
                <span class="product-admin-add-autocomplete-title">${escapeHtml(item.name)}</span>
                <span class="product-admin-add-autocomplete-sub">생산팀 분류</span>
            </button>
        `).join('');

		dropdown.classList.remove('d-none');
	}

	function closeProductionDropdown(orderId) {
		const dropdown = document.getElementById(`product-admin-add-production-dropdown-${orderId}`);

		if (dropdown) {
			dropdown.classList.add('d-none');
		}
	}

	function selectProductionCategory(orderId, item) {
		const order = findOrder(orderId);

		if (!order) {
			return;
		}

		order.customProductionCategoryId = item.id;
		order.customProductionCategoryName = item.name;
		order.customProductionInput = item.name;
		clearOrderValidationError(order, 'customProductionCategoryId');

		setProductionFeedback(orderId, '');
		closeProductionDropdown(orderId);

		rerenderSingleOrder(orderId, {
			focusSelector: '.product-admin-add-production-input'
		});
	}

	function validateExactProductionSelection(order) {
		const value = (order.customProductionInput || '').trim();
		const input = els.orderList.querySelector(
			`.product-admin-add-production-input[data-order-id="${order.id}"]`
		);

		if (!value) {
			setProductionFeedback(order.id, '');

			if (input) {
				input.classList.remove('product-admin-add-invalid');
			}
			return true;
		}

		if (order.customProductionCategoryId && order.customProductionCategoryName) {
			setProductionFeedback(order.id, '');

			if (input) {
				input.classList.remove('product-admin-add-invalid');
			}
			return true;
		}

		setProductionFeedback(order.id, '정확한 생산팀 분류를 선택해 주세요.');

		if (input) {
			input.classList.add('product-admin-add-invalid');
		}

		return false;
	}

	function setProductionFeedback(orderId, message) {
		const feedback = document.getElementById(`product-admin-add-production-feedback-${orderId}`);

		if (feedback) {
			feedback.textContent = message || '';
		}
	}

	function getFilteredProductionCategories(keyword) {
		const normalized = normalizeText(keyword);

		const selectableCategories = state.productionCategories.filter(item =>
			!isExcludedNonStandardProductionCategory(item)
		);

		if (!normalized) {
			return selectableCategories;
		}

		return selectableCategories.filter(item =>
			normalizeText(item.name).includes(normalized)
		);
	}

	function isExcludedNonStandardProductionCategory(item) {
		if (!item) {
			return true;
		}

		const normalizedName = normalizeProductionCategoryName(item.name);

		return NON_STANDARD_EXCLUDED_PRODUCTION_CATEGORY_NAMES.has(normalizedName);
	}

	function normalizeProductionCategoryName(value) {
		return String(value || '')
			.trim()
			.replace(/\s+/g, '')
			.toLowerCase();
	}

	function addOptionFromTextarea(order, textarea) {
		const raw = (textarea.value || '').trim();

		if (!raw) {
			textarea.focus();
			return;
		}

		const lines = raw
			.split(/\n+/)
			.map(item => item.trim())
			.filter(Boolean);

		if (!lines.length) {
			textarea.focus();
			return;
		}

		lines.forEach(line => {
			order.optionPairs.push({
				id: `pair-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
				key: '옵션',
				value: line
			});
		});

		order.optionDraft = '';
		textarea.value = '';

		updateOptionList(order.id);
		textarea.focus();
	}

	function appendFiles(order, files) {
		files.forEach(file => {
			if (!file) {
				return;
			}

			const wrapper = {
				id: `file-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
				file,
				previewUrl: file.type && file.type.startsWith('image/')
					? URL.createObjectURL(file)
					: ''
			};

			order.files.push(wrapper);
		});
	}

	function handleOrderListDragOver(event) {
		const zone = event.target.closest('[data-drop-zone="true"][data-order-id]');

		if (!zone) {
			return;
		}

		event.preventDefault();
		zone.classList.add('is-drag-over');
	}

	function handleOrderListDragLeave(event) {
		const zone = event.target.closest('[data-drop-zone="true"][data-order-id]');

		if (!zone) {
			return;
		}

		if (!zone.contains(event.relatedTarget)) {
			zone.classList.remove('is-drag-over');
		}
	}

	function handleOrderListDrop(event) {
		const zone = event.target.closest('[data-drop-zone="true"][data-order-id]');

		if (!zone) {
			return;
		}

		event.preventDefault();
		zone.classList.remove('is-drag-over');

		const orderId = zone.dataset.orderId;
		const order = findOrder(orderId);

		if (!order) {
			return;
		}

		const files = Array.from(event.dataTransfer.files || []);

		if (!files.length) {
			return;
		}

		appendFiles(order, files);
		updateFileList(orderId);
	}

	function handleDocumentPaste(event) {
		if (!state.orders.length) {
			return;
		}

		if (els.summaryModalEl && els.summaryModalEl.classList.contains('show')) {
			return;
		}

		const files = extractClipboardImageFiles(event.clipboardData);

		if (!files.length) {
			return;
		}

		const orderId = resolvePasteOrderId(event);
		const order = findOrder(orderId);

		if (!order) {
			showTopMessage('이미지를 추가할 주문 영역을 먼저 클릭한 뒤 Ctrl+V 해주세요.', true);
			return;
		}

		event.preventDefault();

		appendFiles(order, files);
		updateFileList(orderId);
		hideTopMessage();
	}

	function rememberPasteTargetByEvent(event) {
		if (!event || !event.target || !event.target.closest) {
			return;
		}

		const orderElement = event.target.closest('[data-order-id]');

		if (!orderElement) {
			return;
		}

		const orderId = orderElement.dataset.orderId;

		if (findOrder(orderId)) {
			state.activePasteOrderId = orderId;
		}
	}

	function resolvePasteOrderId(event) {
		if (event && event.target && event.target.closest) {
			const orderElement = event.target.closest('[data-order-id]');

			if (orderElement && findOrder(orderElement.dataset.orderId)) {
				return orderElement.dataset.orderId;
			}
		}

		if (state.activePasteOrderId && findOrder(state.activePasteOrderId)) {
			return state.activePasteOrderId;
		}

		if (state.orders.length === 1) {
			return state.orders[0].id;
		}

		return null;
	}

	function extractClipboardImageFiles(clipboardData) {
		if (!clipboardData) {
			return [];
		}

		const result = [];

		const items = Array.from(clipboardData.items || []);

		items.forEach((item, index) => {
			if (!item || item.kind !== 'file' || !item.type || !item.type.startsWith('image/')) {
				return;
			}

			const file = item.getAsFile();

			if (!file) {
				return;
			}

			result.push(convertClipboardImageFile(file, index));
		});

		if (result.length) {
			return result;
		}

		return Array.from(clipboardData.files || [])
			.filter(file => file && file.type && file.type.startsWith('image/'))
			.map((file, index) => convertClipboardImageFile(file, index));
	}

	function convertClipboardImageFile(file, index) {
		const extension = getExtensionFromMimeType(file.type);
		const fallbackName = `clipboard-${formatDateTimeForFileName(new Date())}-${index + 1}.${extension}`;
		const filename = file.name && file.name.trim() ? file.name : fallbackName;

		try {
			return new File([file], filename, {
				type: file.type || 'image/png',
				lastModified: Date.now()
			});
		} catch (error) {
			return file;
		}
	}

	function getExtensionFromMimeType(mimeType) {
		const type = String(mimeType || '').toLowerCase();

		if (type.includes('jpeg') || type.includes('jpg')) {
			return 'jpg';
		}

		if (type.includes('webp')) {
			return 'webp';
		}

		if (type.includes('gif')) {
			return 'gif';
		}

		if (type.includes('bmp')) {
			return 'bmp';
		}

		return 'png';
	}

	function formatDateTimeForFileName(date) {
		const y = date.getFullYear();
		const m = String(date.getMonth() + 1).padStart(2, '0');
		const d = String(date.getDate()).padStart(2, '0');
		const hh = String(date.getHours()).padStart(2, '0');
		const mm = String(date.getMinutes()).padStart(2, '0');
		const ss = String(date.getSeconds()).padStart(2, '0');

		return `${y}${m}${d}-${hh}${mm}${ss}`;
	}

	function updateOptionList(orderId) {
		const order = findOrder(orderId);
		const list = els.orderList.querySelector(
			`.product-admin-add-option-list[data-order-id="${orderId}"]`
		);

		if (!order || !list) {
			return;
		}

		list.innerHTML = renderOptionListHtml(order);
	}

	function updateFileList(orderId) {
		const order = findOrder(orderId);
		const list = els.orderList.querySelector(
			`.product-admin-add-file-preview-list[data-order-id="${orderId}"]`
		);

		if (!order || !list) {
			return;
		}

		list.innerHTML = renderFileListHtml(order);

		refreshBottomSummary();
		refreshActionButtons();
	}

	function removeOrder(orderId) {
		const target = findOrder(orderId);

		if (target) {
			target.files.forEach(file => {
				if (file.previewUrl) {
					URL.revokeObjectURL(file.previewUrl);
				}
			});
		}

		state.orders = state.orders.filter(order => order.id !== orderId);

		if (state.activePasteOrderId === orderId) {
			state.activePasteOrderId = state.orders.length ? state.orders[0].id : null;
		}

		renderOrders();
		refreshBottomSummary();
		refreshActionButtons();
	}

	function resetOrderTypeState(order, standard) {
		order.standard = standard;

		order.standardCategoryId = '';
		order.standardSeriesId = '';
		order.standardSeriesOptions = [];

		order.assignedProductionCategoryId = null;
		order.assignedProductionCategoryName = '';
		order.assignedProductionCategoryFallback = false;
		order.assignedProductionCategorySourceName = '';

		order.customProductionCategoryId = null;
		order.customProductionCategoryName = '';
		order.customProductionInput = '';
		order.productionDropdownIndex = -1;
	}

	function assignProductionCategoryByStandardCategory(order) {
		order.assignedProductionCategoryId = null;
		order.assignedProductionCategoryName = '';
		order.assignedProductionCategoryFallback = false;
		order.assignedProductionCategorySourceName = '';

		if (!order.standardCategoryId) {
			return;
		}

		const standardCategory = state.standardCategories.find(item =>
			String(item.id) === String(order.standardCategoryId)
		);

		if (!standardCategory) {
			return;
		}

		order.assignedProductionCategorySourceName = standardCategory.name;

		const matched = state.productionCategories.find(item =>
			normalizeText(item.name) === normalizeText(standardCategory.name)
		);

		if (matched) {
			order.assignedProductionCategoryId = matched.id;
			order.assignedProductionCategoryName = matched.name;
			order.assignedProductionCategoryFallback = false;
			return;
		}

		order.assignedProductionCategoryId = DEFAULT_PRODUCTION_CATEGORY_ID;
		order.assignedProductionCategoryName = DEFAULT_PRODUCTION_CATEGORY_LABEL;
		order.assignedProductionCategoryFallback = true;
	}
	function updateOrderCardTotal(orderId) {
		updateOrderCardPriceSummary(orderId);
	}

	function updateOrderCardPriceSummary(orderId) {
		const order = findOrder(orderId);

		if (!order) {
			return;
		}

		const card = document.getElementById(`product-admin-add-order-card-${orderId}`);

		if (!card) {
			return;
		}

		const vatDisplay = card.querySelector(`[data-price-display="vatAmount"][data-order-id="${orderId}"] strong`);

		if (vatDisplay) {
			vatDisplay.textContent = formatCurrency(getOrderVatAmount(order));
		}

		const totalBadge = card.querySelector('.card-header .badge.bg-light');

		if (totalBadge) {
			totalBadge.textContent = formatCurrency(getOrderTotalAmount(order));
		}
	}
	function refreshBottomSummary() {
		const totalOrderCount = state.orders.length;
		const ordersTotalAmount = state.orders.reduce((sum, order) => {
			return sum + getOrderTotalAmount(order);
		}, 0);
		const grandTotal = ordersTotalAmount + toNumber(state.packingCost) + toNumber(state.deliveryCost);

		els.bottomOrderCount.textContent = `${totalOrderCount}건`;
		els.bottomTotalPrice.textContent = formatCurrency(grandTotal);

		if (els.bottomOrderSummaryBody) {
			renderBottomOrderSummary();
		}
	}
	function syncOrderAddButtonPosition() {
		if (!els.orderAddBtn || !els.orderAddTopSlot || !els.orderAddBottomSlot) {
			return;
		}

		const shouldMoveToBottom = state.orders.length > 0;
		const targetSlot = shouldMoveToBottom
			? els.orderAddBottomSlot
			: els.orderAddTopSlot;

		if (els.orderAddBtn.parentElement !== targetSlot) {
			targetSlot.appendChild(els.orderAddBtn);
		}

		els.orderAddBottomSlot.classList.toggle('d-none', !shouldMoveToBottom);
		els.orderAddBtn.classList.toggle('product-admin-add-order-add-btn-bottom', shouldMoveToBottom);
	}
	function validateCommonFields(showMessage = false) {
		const companyValid = validateExactCompanySelection(showMessage);
		const deliveryMethodValid = validateDeliveryMethod(showMessage);
		const deliveryValid = validateExactDeliverySelection(showMessage);
		const addressValid = validateAddressFields(showMessage);
		const siteAddressValid = validateSiteAddressFields(showMessage);

		let dateValid = true;

		if (!els.preferredDate.value) {
			dateValid = false;

			if (showMessage) {
				els.preferredDate.classList.add('product-admin-add-invalid');
				els.preferredDateFeedback.textContent = '배송 희망일을 선택해 주세요.';
			}
		} else {
			clearDateFeedback();
		}

		refreshActionButtons();

		if (showMessage && !(companyValid && deliveryMethodValid && addressValid && siteAddressValid && dateValid)) {
			scrollToFirstCommonError();
		}

		return companyValid && deliveryMethodValid && deliveryValid && addressValid && siteAddressValid && dateValid;
	}
	function validateOrders(showMessage = false) {
		state.orders.forEach(order => {
			order.validationErrors = {};
		});

		if (!state.orders.length) {
			if (showMessage) {
				showTopMessage('최소 1개의 주문을 추가해 주세요.', true);
			}
			return false;
		}

		let firstError = null;

		for (let i = 0; i < state.orders.length; i++) {
			const order = state.orders[i];
			const orderNo = i + 1;

			if (order.standard === null) {
				firstError = firstError || buildOrderError(order.id, 'standard');
				setOrderValidationError(order, 'standard', `주문 ${orderNo}: 규격 / 비규격을 선택해 주세요.`);
			}

			if (order.standard === true) {
				if (!order.standardCategoryId) {
					firstError = firstError || buildOrderError(order.id, 'standardCategoryId');
					setOrderValidationError(order, 'standardCategoryId', `주문 ${orderNo}: 규격 대분류를 선택해 주세요.`);
				}

				if (!order.assignedProductionCategoryId) {
					firstError = firstError || buildOrderError(order.id, 'standardCategoryId');
					setOrderValidationError(order, 'standardCategoryId', `주문 ${orderNo}: 대분류 기준 생산팀 분류를 찾지 못했습니다.`);
				}
			}

			if (order.standard === false) {
				if (!order.customProductionCategoryId) {
					firstError = firstError || buildOrderError(order.id, 'customProductionCategoryId');
					setOrderValidationError(order, 'customProductionCategoryId', `주문 ${orderNo}: 비규격 생산팀 분류를 정확히 선택해 주세요.`);
				}
			}

			if (!(order.productName || '').trim()) {
				firstError = firstError || buildOrderError(order.id, 'productName');
				setOrderValidationError(order, 'productName', `주문 ${orderNo}: 제품명을 입력해 주세요.`);
			}

			if (!(order.productSize || '').trim()) {
				firstError = firstError || buildOrderError(order.id, 'productSize');
				setOrderValidationError(order, 'productSize', `주문 ${orderNo}: 사이즈를 입력해 주세요.`);
			}

			if (!(order.productColor || '').trim()) {
				firstError = firstError || buildOrderError(order.id, 'productColor');
				setOrderValidationError(order, 'productColor', `주문 ${orderNo}: 색상을 입력해 주세요.`);
			}

			if (`${order.productCost}`.trim() === '' || toNumber(order.productCost) < 0) {
				firstError = firstError || buildOrderError(order.id, 'productCost');
				setOrderValidationError(order, 'productCost', `주문 ${orderNo}: 단가를 입력해 주세요.`);
			}

			if (`${order.quantity}`.trim() === '' || toNumber(order.quantity) <= 0) {
				firstError = firstError || buildOrderError(order.id, 'quantity');
				setOrderValidationError(order, 'quantity', `주문 ${orderNo}: 수량은 1 이상이어야 합니다.`);
			}
		}

		if (!firstError) {
			renderOrders();
			return true;
		}

		state.orders.forEach(order => {
			if (Object.keys(order.validationErrors || {}).length > 0) {
				order.collapsed = false;
			}
		});

		if (showMessage) {
			hideTopMessage();
			renderOrders();
			scrollToOrderFieldError(firstError.orderId, firstError.field);
		}

		return false;
	}

	function showTopMessage(message, shouldScroll = false) {
		els.topMessage.textContent = message;
		els.topMessage.classList.remove('d-none');

		if (shouldScroll) {
			els.topMessage.scrollIntoView({
				behavior: 'smooth',
				block: 'center'
			});
		}
	}

	function hideTopMessage() {
		els.topMessage.textContent = '';
		els.topMessage.classList.add('d-none');
	}
	function openSummaryModal() {
		hideTopMessage();

		if (!validateCommonFields(true)) {
			return;
		}

		if (!validateOrders(true)) {
			return;
		}

		renderSummary();

		if (els.summaryModal) {
			els.summaryModal.show();
		}
	}
	function renderSummary() {
		const ordersTotalAmount = state.orders.reduce((sum, order) => {
			return sum + getOrderTotalAmount(order);
		}, 0);
		const grandTotal = ordersTotalAmount + toNumber(state.packingCost) + toNumber(state.deliveryCost);

		const rows = state.orders.map((order, index) => {
			const productName = getOrderDisplayName(order);

			const categoryLabel = order.standard
				? getStandardCategoryName(order.standardCategoryId)
				: (order.customProductionCategoryName || '-');

			const seriesLabel = getStandardSeriesName(order) || '중분류 없음';

			const detailLabel = order.standard
				? `${seriesLabel} / 생산팀: ${order.assignedProductionCategoryName || '-'}`
				: `생산팀 분류: ${order.customProductionCategoryName || '-'}`;

			const mirrorCuttingLabel = order.mirrorCuttingProduct
				? '거울재단용 직접 지정'
				: '자동판정 대상';

			return `
                <tr>
                    <td>${index + 1}</td>
                    <td>
                        <span class="product-admin-add-summary-product-name">${escapeHtml(productName)}</span>
                        <span class="product-admin-add-summary-sub">
                            ${escapeHtml(order.productSize || '-')} / ${escapeHtml(order.productColor || '-')}
                        </span>
                        <span class="product-admin-add-summary-sub">${escapeHtml(detailLabel)}</span>
                    </td>
                    <td>${escapeHtml(categoryLabel || '-')}</td>
                    <td>${order.standard ? '규격' : '비규격'}</td>
                    <td>${toNumber(order.quantity)}개</td>
                    <td>${formatCurrency(toNumber(order.productCost))}</td>
                    <td>${formatCurrency(getOrderSupplyPrice(order))}</td>
                    <td>${formatCurrency(getOrderVatAmount(order))}</td>
                    <td>${formatCurrency(getOrderTotalAmount(order))}</td>
                </tr>
            `;
		}).join('');

		els.summaryContent.innerHTML = `
            <div class="product-admin-add-summary-box">
                <div class="product-admin-add-summary-title">공통 정보</div>

                <div class="product-admin-add-summary-list">
                    <div class="product-admin-add-summary-item">
                        <span class="product-admin-add-summary-item-label">대리점</span>
                        <span class="product-admin-add-summary-item-value">${escapeHtml(state.company.companyName)}</span>
                    </div>

                    <div class="product-admin-add-summary-item">
                        <span class="product-admin-add-summary-item-label">대표자</span>
                        <span class="product-admin-add-summary-item-value">${escapeHtml(state.company.representativeName || '-')}</span>
                    </div>

                    <div class="product-admin-add-summary-item">
                        <span class="product-admin-add-summary-item-label">주문자</span>
                        <span class="product-admin-add-summary-item-value">${escapeHtml(getOrdererSummaryText())}</span>
                    </div>

                    <div class="product-admin-add-summary-item">
                        <span class="product-admin-add-summary-item-label">배송 희망일</span>
                        <span class="product-admin-add-summary-item-value">${escapeHtml(els.preferredDate.value)}</span>
                    </div>

                    <div class="product-admin-add-summary-item">
                        <span class="product-admin-add-summary-item-label">배송수단</span>
                        <span class="product-admin-add-summary-item-value">${escapeHtml(state.deliveryMethod ? state.deliveryMethod.methodName : '-')}</span>
                    </div>

                    <div class="product-admin-add-summary-item">
                        <span class="product-admin-add-summary-item-label">배송 담당자</span>
                        <span class="product-admin-add-summary-item-value">
                            ${isDeliveryHandlerAssignableSelected() && state.deliveryHandler ? `${escapeHtml(state.deliveryHandler.name)} (${escapeHtml(state.deliveryHandler.username || '-')})` : '자동배정 또는 미지정'}
                        </span>
                    </div>

                    <div class="product-admin-add-summary-item">
                        <span class="product-admin-add-summary-item-label">공통 배송지</span>
                        <span class="product-admin-add-summary-item-value">${escapeHtml(getDeliveryAddressSummaryText(state.address))}</span>
                    </div>

                    ${isSiteDeliverySelected() ? `
                    <div class="product-admin-add-summary-item">
                        <span class="product-admin-add-summary-item-label">현장 배송지</span>
                        <span class="product-admin-add-summary-item-value">${escapeHtml(getDeliveryAddressSummaryText(state.siteAddress))}</span>
                    </div>
                    ` : ''}

                    <div class="product-admin-add-summary-item">
                        <span class="product-admin-add-summary-item-label">포장비</span>
                        <span class="product-admin-add-summary-item-value">${formatCurrency(toNumber(state.packingCost))}</span>
                    </div>

                    <div class="product-admin-add-summary-item">
                        <span class="product-admin-add-summary-item-label">운임비</span>
                        <span class="product-admin-add-summary-item-value">${formatCurrency(toNumber(state.deliveryCost))}</span>
                    </div>

                    <div class="product-admin-add-summary-item">
                        <span class="product-admin-add-summary-item-label">총 예상 금액</span>
                        <span class="product-admin-add-summary-item-value">${formatCurrency(grandTotal)}</span>
                    </div>
                </div>
            </div>

            <div class="product-admin-add-summary-box">
                <div class="product-admin-add-summary-title">주문 요약</div>

                <div class="product-admin-add-summary-table-wrap">
                    <table class="product-admin-add-summary-table">
                        <thead>
                            <tr>
                                <th>No</th>
                                <th>제품</th>
                                <th>카테고리</th>
                                <th>구분</th>
                                <th>수량</th>
                                <th>단가</th>
                                <th>공급가</th>
                                <th>부가세</th>
                                <th>총액</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${rows}
                        </tbody>
                    </table>
                </div>
            </div>
        `;
	}
	function getOrdererSummaryText() {
		return [
			state.orderer.ordererName || '',
			state.orderer.ordererPhone || ''
		].filter(Boolean).join(' / ') || '-';
	}

	function renderBottomOrderSummary() {
		if (!els.bottomOrderSummaryBody) {
			return;
		}

		if (!state.orders.length) {
			els.bottomOrderSummaryBody.innerHTML = `
                <tr>
                    <td colspan="7" class="text-center text-muted py-3">등록된 주문이 없습니다.</td>
                </tr>
            `;
			return;
		}

		els.bottomOrderSummaryBody.innerHTML = state.orders.map((order, index) => `
            <tr>
                <td>주문 ${index + 1}</td>
                <td>${escapeHtml(getOrderDisplayName(order))}</td>
                <td class="text-end">${formatCurrency(toNumber(order.productCost))}</td>
                <td class="text-end">${toNumber(order.quantity)}</td>
                <td class="text-end">${formatCurrency(getOrderSupplyPrice(order))}</td>
                <td class="text-end">${formatCurrency(getOrderVatAmount(order))}</td>
                <td class="text-end fw-semibold">${formatCurrency(getOrderTotalAmount(order))}</td>
            </tr>
        `).join('');
	}

	function recalculateOrderByUnitAndQuantity(order) {
		const supplyPrice = toNumber(order.productCost) * toNumber(order.quantity);
		order.supplyPrice = String(supplyPrice);
		order.totalAmount = String(calculateTotalFromSupply(supplyPrice));
		syncPriceInputs(order.id);
	}

	function recalculateOrderBySupplyPrice(order) {
		const supplyPrice = toNumber(order.supplyPrice);
		order.totalAmount = String(calculateTotalFromSupply(supplyPrice));
		syncPriceInputs(order.id);
	}

	function recalculateOrderByTotalAmount(order) {
		const totalAmount = toNumber(order.totalAmount);
		const supplyPrice = calculateSupplyFromTotal(totalAmount);
		order.supplyPrice = String(supplyPrice);
		order.totalAmount = String(totalAmount);
		syncPriceInputs(order.id);
	}

	function syncPriceInputs(orderId) {
		const order = findOrder(orderId);
		const card = document.getElementById(`product-admin-add-order-card-${orderId}`);

		if (!order || !card) {
			return;
		}

		const supplyInput = card.querySelector(`[data-field="supplyPrice"][data-order-id="${orderId}"]`);
		const totalInput = card.querySelector(`[data-field="totalAmount"][data-order-id="${orderId}"]`);

		if (supplyInput && document.activeElement !== supplyInput) {
			supplyInput.value = formatNumberInput(order.supplyPrice);
		}

		if (totalInput && document.activeElement !== totalInput) {
			totalInput.value = formatNumberInput(order.totalAmount);
		}
	}

	function calculateTotalFromSupply(supplyPrice) {
		return Math.floor(toNumber(supplyPrice) * 1.1);
	}

	function calculateSupplyFromTotal(totalAmount) {
		return Math.floor(toNumber(totalAmount) / 1.1);
	}

	function getOrderSupplyPrice(order) {
		return toNumber(order.supplyPrice);
	}

	function getOrderTotalAmount(order) {
		return toNumber(order.totalAmount);
	}

	function getOrderVatAmount(order) {
		return Math.max(getOrderTotalAmount(order) - getOrderSupplyPrice(order), 0);
	}

	function sanitizeMoneyInputElement(input) {
		const sanitized = String(input.value || '').replace(/[^0-9]/g, '');
		input.value = sanitized;
		return sanitized;
	}

	function formatNumberInput(value) {
		const raw = String(value ?? '').replace(/[^0-9]/g, '');
		return raw;
	}

	function hasFieldError(order, field) {
		return Boolean(order && order.validationErrors && order.validationErrors[field]);
	}

	function renderFieldError(order, field) {
		if (!hasFieldError(order, field)) {
			return '';
		}

		return `
            <div class="product-admin-add-field-error" data-error-anchor="${field}">
                ${escapeHtml(order.validationErrors[field])}
            </div>
        `;
	}

	function setOrderValidationError(order, field, message) {
		if (!order.validationErrors) {
			order.validationErrors = {};
		}
		order.validationErrors[field] = message;
	}

	function clearOrderValidationError(order, field) {
		if (!order || !order.validationErrors) {
			return;
		}
		delete order.validationErrors[field];
	}

	function buildOrderError(orderId, field) {
		return { orderId, field };
	}

	function scrollToOrderFieldError(orderId, field) {
		requestAnimationFrame(() => {
			const card = document.getElementById(`product-admin-add-order-card-${orderId}`);
			const target = card
				? card.querySelector(`[data-error-anchor="${field}"]`) || card
				: null;

			if (!target) {
				return;
			}

			target.scrollIntoView({
				behavior: 'smooth',
				block: 'center'
			});

			const focusable = card.querySelector(`[data-error-anchor="${field}"][data-order-id], [data-field][data-error-anchor="${field}"]`);
			if (focusable && typeof focusable.focus === 'function') {
				setTimeout(() => focusable.focus(), 250);
			}
		});
	}

	function scrollToFirstCommonError() {
		const candidates = [
			els.companyInput,
			els.deliveryMethod,
			els.preferredDate,
			isDeliveryHandlerAssignableSelected() ? els.deliveryInput : null,
			els.zipCode,
			els.roadAddress,
			isSiteDeliverySelected() ? els.siteZipCode : null,
			isSiteDeliverySelected() ? els.siteRoadAddress : null
		].filter(Boolean);

		const target = candidates.find(el => el.classList && el.classList.contains('product-admin-add-invalid'));

		if (!target) {
			return;
		}

		target.scrollIntoView({
			behavior: 'smooth',
			block: 'center'
		});

		setTimeout(() => {
			if (typeof target.focus === 'function' && !target.readOnly && !target.disabled) {
				target.focus();
			}
		}, 250);
	}


	function getDeliveryAddressSummaryText(targetAddress = state.address) {
		const address = [
			targetAddress.roadAddress,
			targetAddress.detailAddress
		].filter(item => item && item.trim()).join(' ');

		const region = [
			targetAddress.doName,
			targetAddress.siName,
			targetAddress.guName
		].filter(item => item && item.trim()).join(' / ');

		return [
			targetAddress.zipCode ? `(${targetAddress.zipCode})` : '',
			address,
			region ? `[${region}]` : ''
		].filter(Boolean).join(' ');
	}

	async function submitForm() {
		if (state.saving) {
			return;
		}

		if (!validateCommonFields(true) || !validateOrders(true)) {
			return;
		}

		state.saving = true;
		els.confirmBtn.disabled = true;
		els.confirmBtn.textContent = '저장 중...';

		refreshActionButtons();

		try {
			const payload = {
				companyId: state.company.companyId,
				preferredDeliveryDate: els.preferredDate.value,
				deliveryMethodId: state.deliveryMethod ? Number(state.deliveryMethod.id) : null,
				deliveryHandlerId: isDeliveryHandlerAssignableSelected() && state.deliveryHandler
					? Number(state.deliveryHandler.memberId)
					: null,

				packingCost: toNumber(state.packingCost),
				deliveryCost: toNumber(state.deliveryCost),

				ordererName: (state.orderer.ordererName || '').trim() || null,
				ordererPhone: (state.orderer.ordererPhone || '').trim() || null,

				zipCode: (state.address.zipCode || '').trim(),
				doName: (state.address.doName || '').trim(),
				siName: (state.address.siName || '').trim(),
				guName: (state.address.guName || '').trim(),
				roadAddress: (state.address.roadAddress || '').trim(),
				detailAddress: (state.address.detailAddress || '').trim(),

				siteZipCode: isSiteDeliverySelected() ? (state.siteAddress.zipCode || '').trim() : null,
				siteDoName: isSiteDeliverySelected() ? (state.siteAddress.doName || '').trim() : null,
				siteSiName: isSiteDeliverySelected() ? (state.siteAddress.siName || '').trim() : null,
				siteGuName: isSiteDeliverySelected() ? (state.siteAddress.guName || '').trim() : null,
				siteRoadAddress: isSiteDeliverySelected() ? (state.siteAddress.roadAddress || '').trim() : null,
				siteDetailAddress: isSiteDeliverySelected() ? (state.siteAddress.detailAddress || '').trim() : null,

				orders: state.orders.map(order => ({
					standard: order.standard,

					standardCategoryId: order.standard
						? Number(order.standardCategoryId)
						: null,

					standardProductSeriesId: order.standard && order.standardSeriesId
						? Number(order.standardSeriesId)
						: null,

					productionCategoryId: order.standard
						? Number(order.assignedProductionCategoryId)
						: Number(order.customProductionCategoryId),

					productName: (order.productName || '').trim(),
					productSize: (order.productSize || '').trim(),
					productColor: (order.productColor || '').trim(),
					mirrorCuttingProduct: Boolean(order.mirrorCuttingProduct),

					productCost: toNumber(order.productCost),
					quantity: toNumber(order.quantity),
					supplyPrice: getOrderSupplyPrice(order),
					totalAmount: getOrderTotalAmount(order),
					orderComment: (order.orderComment || '').trim() || null,
					adminMemo: (order.adminMemo || '').trim() || null,

					optionEntries: order.optionPairs.map(pair => ({
						title: '옵션',
						answer: pair.value
					}))
				}))
			};

			const formData = new FormData();
			formData.append(
				'request',
				new Blob([JSON.stringify(payload)], {
					type: 'application/json'
				})
			);

			state.orders.forEach((order, index) => {
				order.files.forEach(fileWrapper => {
					formData.append(`orderFiles_${index}`, fileWrapper.file);
				});
			});

			const headers = {};
			const csrfHeader = getCsrfHeader();

			if (csrfHeader) {
				headers[csrfHeader.headerName] = csrfHeader.token;
			}

			const response = await fetch(API_BASE, {
				method: 'POST',
				headers,
				body: formData
			});

			const data = await response.json();

			if (!response.ok || !data.success) {
				throw new Error(data.message || '발주 등록에 실패했습니다.');
			}

			alert('발주가 정상 등록되었습니다.');
			window.location.reload();

		} catch (error) {
			alert(error.message || '발주 등록 중 오류가 발생했습니다.');
		} finally {
			state.saving = false;
			els.confirmBtn.disabled = false;
			els.confirmBtn.textContent = '확인 후 저장';
			refreshActionButtons();
		}
	}

	function getOrderDisplayName(order) {
		if ((order.productName || '').trim()) {
			return order.productName.trim();
		}

		if (order.standard) {
			return getStandardSeriesName(order) ||
				getStandardCategoryName(order.standardCategoryId) ||
				'규격 제품';
		}

		return order.customProductionCategoryName || '비규격 제품';
	}

	function getStandardCategoryName(categoryId) {
		const item = state.standardCategories.find(category =>
			String(category.id) === String(categoryId)
		);

		return item ? item.name : '';
	}

	function getStandardSeriesName(order) {
		const item = (order.standardSeriesOptions || []).find(series =>
			String(series.id) === String(order.standardSeriesId)
		);

		return item ? item.name : '';
	}

	function findOrder(orderId) {
		return state.orders.find(order => order.id === orderId);
	}

	async function fetchJson(url) {
		const response = await fetch(url, {
			headers: buildJsonHeaders()
		});

		if (!response.ok) {
			throw new Error('데이터를 불러오지 못했습니다.');
		}

		return response.json();
	}

	function buildJsonHeaders() {
		const headers = {
			'Accept': 'application/json'
		};

		const csrf = getCsrfHeader();

		if (csrf) {
			headers[csrf.headerName] = csrf.token;
		}

		return headers;
	}

	function getCsrfHeader() {
		const tokenMeta = document.querySelector('meta[name="_csrf"]');
		const headerMeta = document.querySelector('meta[name="_csrf_header"]');

		if (!tokenMeta || !headerMeta) {
			return null;
		}

		return {
			token: tokenMeta.getAttribute('content'),
			headerName: headerMeta.getAttribute('content')
		};
	}

	function toNumber(value) {
		const number = Number(value);
		return Number.isFinite(number) ? number : 0;
	}

	function formatCurrency(value) {
		return `${toNumber(value).toLocaleString('ko-KR')}원`;
	}

	function formatDate(value) {
		if (!value) {
			return '-';
		}

		const date = new Date(value);

		if (Number.isNaN(date.getTime())) {
			return '-';
		}

		const y = date.getFullYear();
		const m = String(date.getMonth() + 1).padStart(2, '0');
		const d = String(date.getDate()).padStart(2, '0');

		return `${y}-${m}-${d}`;
	}

	function escapeHtml(value) {
		return String(value ?? '')
			.replaceAll('&', '&amp;')
			.replaceAll('<', '&lt;')
			.replaceAll('>', '&gt;')
			.replaceAll('"', '&quot;')
			.replaceAll("'", '&#39;');
	}

	function normalizeText(value) {
		return String(value || '').trim().toLowerCase();
	}

	function debounce(fn, wait) {
		let timer = null;

		return function(...args) {
			clearTimeout(timer);
			timer = setTimeout(() => fn.apply(this, args), wait);
		};
	}

})();