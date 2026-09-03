(function (window, document) {
    'use strict';

    const pm = window.HiddenBathProductMaster;
    if (!pm) return;

    const state = {
        groups: [],
        products: [],
        page: 0,
        size: 30,
        totalElements: 0,
        totalPages: 0,
        first: true,
        last: true,
        sorts: [],
        qrProduct: null,
        qrSvg: ''
    };

    const sortLabels = {
        productName: '제품명',
        catalogCode: '카탈로그 코드',
        currentStock: '총재고',
        totalPrice: 'VAT 포함가',
        createdAt: '등록일'
    };

    const dimensionFilters = [
        {axis: 'W', minParam: 'widthMin', maxParam: 'widthMax', minId: 'pm-filter-width-min', maxId: 'pm-filter-width-max'},
        {axis: 'D', minParam: 'depthMin', maxParam: 'depthMax', minId: 'pm-filter-depth-min', maxId: 'pm-filter-depth-max'},
        {axis: 'H', minParam: 'heightMin', maxParam: 'heightMax', minId: 'pm-filter-height-min', maxId: 'pm-filter-height-max'}
    ];

    const elements = {
        filterForm: document.getElementById('pm-product-filter-form'),
        attributeGrid: document.getElementById('pm-attribute-filter-grid'),
        activeFilters: document.getElementById('pm-active-filters'),
        tableBody: document.getElementById('pm-product-table-body'),
        pageButtons: document.getElementById('pm-page-buttons'),
        pageSummary: document.getElementById('pm-page-summary'),
        total: document.getElementById('pm-product-total'),
        sortSummary: document.getElementById('pm-sort-summary'),
        wideFrame: document.getElementById('pm-wide-frame'),
        resolverResult: document.getElementById('pm-code-resolver-result')
    };

    const wideModal = new bootstrap.Modal(document.getElementById('pm-wide-modal'));
    const qrModal = new bootstrap.Modal(document.getElementById('pm-list-qr-modal'));
    const resolverModal = new bootstrap.Modal(document.getElementById('pm-code-resolver-modal'));

    async function initialize() {
        restoreQueryState();
        pm.showLoading(true, '제품 필터와 목록을 준비하는 중입니다.');
        try {
            const groupResponse = await pm.request('/admin/api/product-master/groups?includeInactive=true');
            state.groups = groupResponse.data || [];
            renderAttributeFilters();
            applyRestoredAttributeValues();
            updateAdvancedFilterCount();
            if (hasAdvancedFilters()) setAdvancedFilter(true);
            await loadProducts(false);
        } catch (error) {
            await pm.alert('error', '제품 목록 준비 실패', error.message);
            elements.tableBody.innerHTML = emptyRow('제품 목록을 불러오지 못했습니다.');
        } finally {
            pm.showLoading(false);
        }
    }

    function restoreQueryState() {
        const query = new URLSearchParams(window.location.search);
        document.getElementById('pm-filter-keyword').value = query.get('keyword') || '';
        document.getElementById('pm-filter-status').value = query.get('status') || '';
        document.getElementById('pm-filter-stock').value = query.get('stockStatus') || '';
        const size = Number(query.get('size') || 30);
        state.size = [30, 50, 100, 200].includes(size) ? size : 30;
        const requestedPage = Number(query.get('page') || 0);
        state.page = Number.isSafeInteger(requestedPage) && requestedPage >= 0 ? requestedPage : 0;
        document.getElementById('pm-filter-size').value = String(state.size);
        const seenSorts = new Set();
        state.sorts = query.getAll('sort').map(function (raw) {
            const parts = raw.split(',');
            return {key: parts[0], direction: parts[1] === 'desc' ? 'desc' : 'asc'};
        }).filter(function (item) {
            if (!Object.hasOwn(sortLabels, item.key) || seenSorts.has(item.key)) return false;
            seenSorts.add(item.key);
            return true;
        });
        state.restoredAttributes = query.getAll('attribute');
        state.restoredNumberAttributes = query.getAll('numberAttribute');
        state.restoredTextAttributes = query.getAll('textAttribute');
        dimensionFilters.forEach(function (filter) {
            document.getElementById(filter.minId).value = query.get(filter.minParam) || '';
            document.getElementById(filter.maxId).value = query.get(filter.maxParam) || '';
        });
    }

    function renderAttributeFilters() {
        const basicIds = new Set();
        ['CATEGORY', 'COLOR', 'SIZE'].forEach(function (role) {
            const slot = document.querySelector('[data-pm-basic-role="' + role + '"] [data-pm-basic-control]');
            if (!slot) return;
            const group = state.groups.find(function (item) {
                return item.systemRole === role && item.active && (item.values || []).length;
            }) || state.groups.find(function (item) { return item.systemRole === role && (item.values || []).length; });
            if (!group) {
                slot.innerHTML = '<span class="pm-basic-filter-empty">등록값 없음</span>';
                return;
            }
            basicIds.add(group.id);
            slot.innerHTML = attributeSelectHtml(group, true);
        });
        const advancedGroups = state.groups.filter(function (group) {
            return !basicIds.has(group.id) && ((group.values || []).length
                || group.inputType === 'NUMBER' || group.inputType === 'TEXT');
        });
        if (!advancedGroups.length) {
            elements.attributeGrid.innerHTML = '<p class="text-muted small mb-0">추가 옵션 필터가 없습니다.</p>';
            return;
        }
        elements.attributeGrid.innerHTML = advancedGroups.map(function (group) {
            return '<label class="pm-advanced-attribute"><span>' + pm.escapeHtml(group.managementLabel)
                + (!group.active ? ' <small>사용중지</small>' : '') + '</span>' + advancedFilterControl(group) + '</label>';
        }).join('');
    }

    function advancedFilterControl(group) {
        if (group.inputType === 'NUMBER') {
            return '<div class="pm-direct-filter-range"><input class="form-control form-control-sm" type="number" min="-1000000000" max="1000000000" step="0.001" placeholder="최소" '
                + 'data-pm-number-group-id="' + group.id + '" data-pm-number-bound="min" aria-label="' + pm.escapeHtml(group.managementLabel) + ' 최소">'
                + '<b>~</b><input class="form-control form-control-sm" type="number" min="-1000000000" max="1000000000" step="0.001" placeholder="최대" '
                + 'data-pm-number-group-id="' + group.id + '" data-pm-number-bound="max" aria-label="' + pm.escapeHtml(group.managementLabel) + ' 최대"></div>';
        }
        if (group.inputType === 'TEXT') {
            return '<input class="form-control form-control-sm" maxlength="100" placeholder="포함 문구" data-pm-text-group-id="'
                + group.id + '" aria-label="' + pm.escapeHtml(group.managementLabel) + ' 포함 문구">';
        }
        return attributeSelectHtml(group, false);
    }

    function attributeSelectHtml(group, compact) {
            const options = (group.values || []).map(function (value) {
                const suffix = !value.active ? ' (사용중지)' : '';
                return '<option value="' + value.id + '">' + pm.escapeHtml(value.managementLabel + suffix) + '</option>';
            }).join('');
            return '<select class="form-select' + (compact ? ' form-select-sm pm-basic-select' : ' form-select-sm')
                + '" id="pm-attribute-filter-' + group.id + '" data-pm-attribute-group-id="' + group.id + '">'
                + '<option value="">전체</option>' + options + '</select>';
    }

    function applyRestoredAttributeValues() {
        (state.restoredAttributes || []).forEach(function (raw) {
            const parts = raw.split(':');
            const select = document.querySelector('[data-pm-attribute-group-id="' + Number(parts[0]) + '"]');
            if (select && Array.from(select.options).some(function (option) { return option.value === parts[1]; })) {
                select.value = parts[1];
            }
        });
        (state.restoredNumberAttributes || []).forEach(function (raw) {
            const parts = raw.split(':');
            if (parts.length !== 3) return;
            const minimum = document.querySelector('[data-pm-number-group-id="' + Number(parts[0]) + '"][data-pm-number-bound="min"]');
            const maximum = document.querySelector('[data-pm-number-group-id="' + Number(parts[0]) + '"][data-pm-number-bound="max"]');
            if (minimum) minimum.value = parts[1];
            if (maximum) maximum.value = parts[2];
        });
        (state.restoredTextAttributes || []).forEach(function (raw) {
            const separator = raw.indexOf(':');
            if (separator < 1) return;
            const input = document.querySelector('[data-pm-text-group-id="' + Number(raw.slice(0, separator)) + '"]');
            if (input) input.value = raw.slice(separator + 1);
        });
        state.restoredAttributes = [];
        state.restoredNumberAttributes = [];
        state.restoredTextAttributes = [];
    }

    function selectedAttributes() {
        return Array.from(document.querySelectorAll('[data-pm-attribute-group-id]'))
            .filter(function (select) { return select.value; })
            .map(function (select) {
                return select.dataset.pmAttributeGroupId + ':' + select.value;
            });
    }

    function selectedNumberAttributes() {
        const result = [];
        const ids = new Set(Array.from(document.querySelectorAll('[data-pm-number-group-id]')).map(function (input) {
            return input.dataset.pmNumberGroupId;
        }));
        ids.forEach(function (groupId) {
            const minimum = document.querySelector('[data-pm-number-group-id="' + groupId + '"][data-pm-number-bound="min"]');
            const maximum = document.querySelector('[data-pm-number-group-id="' + groupId + '"][data-pm-number-bound="max"]');
            const minValue = minimum ? minimum.value : '';
            const maxValue = maximum ? maximum.value : '';
            if (minValue || maxValue) result.push(groupId + ':' + minValue + ':' + maxValue);
        });
        return result;
    }

    function selectedTextAttributes() {
        return Array.from(document.querySelectorAll('[data-pm-text-group-id]')).filter(function (input) {
            return input.value.trim();
        }).map(function (input) {
            return input.dataset.pmTextGroupId + ':' + input.value.trim();
        });
    }

    function buildQuery() {
        const query = new URLSearchParams();
        query.set('page', String(state.page));
        query.set('size', String(state.size));
        const keyword = document.getElementById('pm-filter-keyword').value.trim();
        const status = document.getElementById('pm-filter-status').value;
        const stock = document.getElementById('pm-filter-stock').value;
        if (keyword) query.set('keyword', keyword);
        if (status) query.set('status', status);
        if (stock) query.set('stockStatus', stock);
        selectedAttributes().forEach(function (attribute) { query.append('attribute', attribute); });
        selectedNumberAttributes().forEach(function (attribute) { query.append('numberAttribute', attribute); });
        selectedTextAttributes().forEach(function (attribute) { query.append('textAttribute', attribute); });
        state.sorts.forEach(function (sort) { query.append('sort', sort.key + ',' + sort.direction); });
        dimensionFilters.forEach(function (filter) {
            const minimum = document.getElementById(filter.minId).value;
            const maximum = document.getElementById(filter.maxId).value;
            if (minimum) query.set(filter.minParam, minimum);
            if (maximum) query.set(filter.maxParam, maximum);
        });
        return query;
    }

    async function loadProducts(showLoading) {
        if (showLoading !== false) pm.showLoading(true, '제품을 조회하는 중입니다.');
        try {
            const query = buildQuery();
            const response = await pm.request('/admin/api/product-master/products?' + query.toString());
            const page = response.data;
            state.products = page.content || [];
            state.page = page.page;
            state.size = page.size;
            state.totalElements = page.totalElements;
            state.totalPages = page.totalPages;
            state.first = page.first;
            state.last = page.last;
            window.history.replaceState(null, '', window.location.pathname + '?' + buildQuery().toString());
            renderProducts();
            renderPagination();
            renderActiveFilters();
            renderSortState();
        } catch (error) {
            elements.tableBody.innerHTML = emptyRow(error.message);
            await pm.alert('error', '제품 조회 실패', error.message);
        } finally {
            if (showLoading !== false) pm.showLoading(false);
        }
    }

    function renderProducts() {
        elements.total.textContent = pm.number(state.totalElements);
        if (!state.products.length) {
            elements.tableBody.innerHTML = emptyRow('조건에 맞는 제품이 없습니다.');
            return;
        }
        elements.tableBody.innerHTML = state.products.map(function (product) {
            const core = (product.components || []).filter(function (component) { return component.groupType === 'CORE'; });
            const coreTags = core.map(function (component) {
                const dimension = component.managementDimensionText ? ' · ' + component.managementDimensionText : '';
                return '<span class="pm-spec-tag" title="' + pm.escapeHtml(component.groupManagementLabel) + '">'
                    + pm.escapeHtml(component.valueManagementLabel + dimension) + '</span>';
            }).join('');
            const addonTags = (product.addonBalances || []).map(function (balance) {
                const unitPrice = balance.unitPrice
                    ? '<small> · 옵션단가 ' + pm.money(balance.unitPrice) + '</small>'
                    : '';
                return '<span class="pm-addon-tag">' + pm.escapeHtml(balance.managementLabel) + ' <strong>'
                    + pm.number(balance.quantity) + '</strong>개' + unitPrice + '</span>';
            }).join('') || '<span class="text-muted small">추가 옵션 없음</span>';
            return '<tr class="pm-product-row" data-pm-product-row-id="' + product.id + '">'
                + '<td><div class="pm-product-name">' + pm.escapeHtml(product.productName) + '</div>'
                + '<span class="badge ' + statusBadgeClass(product.status) + '">' + pm.escapeHtml(product.statusLabel) + '</span></td>'
                + '<td><button type="button" class="btn btn-sm btn-light font-monospace fw-bold" data-pm-copy-code="' + pm.escapeHtml(product.catalogCode) + '">' + pm.escapeHtml(product.catalogCode) + '</button></td>'
                + '<td><span class="pm-product-code" title="' + pm.escapeHtml(product.productCode) + '">' + pm.escapeHtml(product.productCode) + '</span></td>'
                + '<td><div class="pm-spec-tags">' + coreTags + '</div></td>'
                + '<td><div class="pm-addon-tags">' + addonTags + '</div></td>'
                + '<td class="text-end"><span class="pm-stock-number">' + pm.number(product.currentStock) + '</span>'
                + '<span class="' + pm.stockClass(product.stockStatus) + '">' + pm.escapeHtml(product.stockStatusLabel) + '</span></td>'
                + '<td class="text-end"><strong>' + pm.money(product.totalPrice) + '</strong><small class="d-block text-muted">공급 ' + pm.money(product.supplyPrice) + '</small></td>'
                + '<td class="pm-date-cell">' + pm.escapeHtml(pm.date(product.createdAt)) + '</td>'
                + '<td><div class="pm-value-actions">'
                + '<button type="button" class="pm-icon-btn" data-pm-action="qr" data-pm-product-id="' + product.id + '" title="QR"><i class="ri-qr-code-line"></i></button>'
                + '<button type="button" class="pm-icon-btn" data-pm-action="wide" data-pm-product-id="' + product.id + '" title="넓게보기"><i class="ri-fullscreen-line"></i></button>'
                + '<a class="pm-icon-btn" href="/admin/product-master/products/' + product.id + '" data-pm-action="edit" title="상세 수정"><i class="ri-edit-line"></i></a>'
                + '</div></td></tr>';
        }).join('');
        bindProductEvents();
    }

    function bindProductEvents() {
        elements.tableBody.querySelectorAll('[data-pm-product-row-id]').forEach(function (row) {
            row.addEventListener('click', function (event) {
                if (event.target.closest('button, a')) return;
                window.location.href = '/admin/product-master/products/' + row.dataset.pmProductRowId;
            });
        });
        elements.tableBody.querySelectorAll('[data-pm-copy-code]').forEach(function (button) {
            button.addEventListener('click', function () { pm.copy(button.dataset.pmCopyCode); });
        });
        elements.tableBody.querySelectorAll('[data-pm-action="wide"]').forEach(function (button) {
            button.addEventListener('click', function () { openWide(Number(button.dataset.pmProductId)); });
        });
        elements.tableBody.querySelectorAll('[data-pm-action="qr"]').forEach(function (button) {
            button.addEventListener('click', function () { openQr(Number(button.dataset.pmProductId)); });
        });
    }

    function renderPagination() {
        const from = state.totalElements === 0 ? 0 : state.page * state.size + 1;
        const to = Math.min(state.totalElements, (state.page + 1) * state.size);
        elements.pageSummary.textContent = pm.number(state.totalElements) + '개 중 ' + pm.number(from) + '–' + pm.number(to) + '개 표시';
        if (state.totalPages <= 1) {
            elements.pageButtons.innerHTML = '';
            return;
        }
        const pages = pageRange(state.page, state.totalPages, 5);
        let html = '<button class="pm-page-button" type="button" data-pm-page="' + (state.page - 1) + '" ' + (state.first ? 'disabled' : '') + '><i class="ri-arrow-left-s-line"></i></button>';
        pages.forEach(function (page) {
            html += '<button class="pm-page-button' + (page === state.page ? ' pm-is-active' : '') + '" type="button" data-pm-page="' + page + '">' + (page + 1) + '</button>';
        });
        html += '<button class="pm-page-button" type="button" data-pm-page="' + (state.page + 1) + '" ' + (state.last ? 'disabled' : '') + '><i class="ri-arrow-right-s-line"></i></button>';
        elements.pageButtons.innerHTML = html;
        elements.pageButtons.querySelectorAll('[data-pm-page]:not(:disabled)').forEach(function (button) {
            button.addEventListener('click', function () {
                state.page = Number(button.dataset.pmPage);
                loadProducts(true);
                document.getElementById('pm-product-list-page').scrollIntoView({behavior: 'smooth'});
            });
        });
    }

    function pageRange(current, total, visible) {
        const length = Math.min(total, visible);
        let start = Math.max(0, current - Math.floor(length / 2));
        start = Math.min(start, Math.max(0, total - length));
        return Array.from({length: length}, function (_, index) { return start + index; });
    }

    function renderActiveFilters() {
        const chips = [];
        const keyword = document.getElementById('pm-filter-keyword').value.trim();
        const status = document.getElementById('pm-filter-status');
        const stock = document.getElementById('pm-filter-stock');
        if (keyword) chips.push('검색: ' + keyword);
        if (status.value) chips.push('상태: ' + status.options[status.selectedIndex].text);
        if (stock.value) chips.push('재고: ' + stock.options[stock.selectedIndex].text);
        document.querySelectorAll('[data-pm-attribute-group-id]').forEach(function (select) {
            if (!select.value) return;
            const group = state.groups.find(function (item) { return item.id === Number(select.dataset.pmAttributeGroupId); });
            chips.push((group ? group.managementLabel : '옵션') + ': ' + select.options[select.selectedIndex].text);
        });
        selectedNumberAttributes().forEach(function (raw) {
            const parts = raw.split(':');
            const group = state.groups.find(function (item) { return item.id === Number(parts[0]); });
            chips.push((group ? group.managementLabel : '숫자 옵션') + ': '
                + (parts[1] || '제한없음') + '~' + (parts[2] || '제한없음') + (group && group.unitLabel ? group.unitLabel : ''));
        });
        selectedTextAttributes().forEach(function (raw) {
            const separator = raw.indexOf(':');
            const group = state.groups.find(function (item) { return item.id === Number(raw.slice(0, separator)); });
            chips.push((group ? group.managementLabel : '문자 옵션') + ': ' + raw.slice(separator + 1));
        });
        dimensionFilters.forEach(function (filter) {
            const minimum = document.getElementById(filter.minId).value;
            const maximum = document.getElementById(filter.maxId).value;
            if (minimum || maximum) {
                chips.push(filter.axis + ': ' + (minimum || '제한없음') + '~' + (maximum || '제한없음') + 'mm');
            }
        });
        elements.activeFilters.innerHTML = chips.length
            ? chips.map(function (chip) { return '<span class="pm-filter-chip"><i class="ri-checkbox-circle-line"></i>' + pm.escapeHtml(chip) + '</span>'; }).join('')
            : '<span class="text-muted small">적용된 상세 필터가 없습니다.</span>';
        updateAdvancedFilterCount();
    }

    function hasAdvancedFilters() {
        if (document.getElementById('pm-filter-status').value || document.getElementById('pm-filter-stock').value) return true;
        if (Number(document.getElementById('pm-filter-size').value || 30) !== 30) return true;
        const basicIds = new Set(Array.from(document.querySelectorAll('[data-pm-basic-role] [data-pm-attribute-group-id]'))
            .map(function (select) { return select.dataset.pmAttributeGroupId; }));
        const advancedAttribute = Array.from(document.querySelectorAll('#pm-attribute-filter-grid [data-pm-attribute-group-id]'))
            .some(function (select) { return select.value && !basicIds.has(select.dataset.pmAttributeGroupId); });
        if (advancedAttribute) return true;
        if (selectedNumberAttributes().length || selectedTextAttributes().length) return true;
        return dimensionFilters.some(function (filter) {
            return document.getElementById(filter.minId).value || document.getElementById(filter.maxId).value;
        });
    }

    function updateAdvancedFilterCount() {
        let count = 0;
        if (document.getElementById('pm-filter-status').value) count++;
        if (document.getElementById('pm-filter-stock').value) count++;
        if (Number(document.getElementById('pm-filter-size').value || 30) !== 30) count++;
        document.querySelectorAll('#pm-attribute-filter-grid [data-pm-attribute-group-id]').forEach(function (select) { if (select.value) count++; });
        count += selectedNumberAttributes().length + selectedTextAttributes().length;
        dimensionFilters.forEach(function (filter) {
            if (document.getElementById(filter.minId).value || document.getElementById(filter.maxId).value) count++;
        });
        const badge = document.getElementById('pm-advanced-filter-count');
        badge.textContent = String(count);
        badge.classList.toggle('pm-has-count', count > 0);
    }

    function setAdvancedFilter(open) {
        const panel = document.getElementById('pm-advanced-filter');
        const button = document.getElementById('pm-toggle-advanced-filter');
        panel.classList.toggle('pm-is-open', open);
        panel.setAttribute('aria-hidden', open ? 'false' : 'true');
        button.classList.toggle('pm-is-open', open);
        button.setAttribute('aria-expanded', open ? 'true' : 'false');
    }

    function cycleSort(key) {
        const index = state.sorts.findIndex(function (sort) { return sort.key === key; });
        if (index < 0) state.sorts.push({key: key, direction: 'asc'});
        else if (state.sorts[index].direction === 'asc') state.sorts[index].direction = 'desc';
        else state.sorts.splice(index, 1);
        state.page = 0;
        loadProducts(true);
    }

    function renderSortState() {
        document.querySelectorAll('[data-pm-sort]').forEach(function (button) {
            button.classList.remove('pm-sort-asc', 'pm-sort-desc');
            const orderBadge = button.querySelector('.pm-sort-order');
            const index = state.sorts.findIndex(function (sort) { return sort.key === button.dataset.pmSort; });
            if (index < 0) {
                orderBadge.classList.add('d-none');
                orderBadge.textContent = '';
            } else {
                button.classList.add('pm-sort-' + state.sorts[index].direction);
                orderBadge.classList.remove('d-none');
                orderBadge.textContent = String(index + 1);
            }
        });
        elements.sortSummary.textContent = state.sorts.length
            ? '다중 정렬: ' + state.sorts.map(function (sort, index) {
                return (index + 1) + '. ' + sortLabels[sort.key] + ' ' + (sort.direction === 'asc' ? '오름차순' : '내림차순');
            }).join(' · ')
            : '기본 정렬: 최근 등록순';
    }

    function openWide(productId) {
        elements.wideFrame.src = '/admin/product-master/products/' + productId + '?embedded=true';
        wideModal.show();
    }

    function openQr(productId) {
        const product = state.products.find(function (item) { return item.id === productId; });
        if (!product || !window.ProductMasterQr) return;
        try {
            state.qrProduct = product;
            const url = window.location.origin + product.publicSpecPath;
            document.getElementById('pm-list-qr-name').textContent = product.productName;
            document.getElementById('pm-list-qr-code').textContent = product.catalogCode;
            document.getElementById('pm-list-qr-url').textContent = url;
            document.getElementById('pm-list-open-spec').href = url;
            state.qrSvg = window.ProductMasterQr.render(document.getElementById('pm-list-qr-stage'), url, {dark: '#17243c', light: '#ffffff'});
            qrModal.show();
        } catch (error) {
            pm.alert('error', 'QR 생성 실패', error.message);
        }
    }

    async function resolveCode(event) {
        event.preventDefault();
        const code = document.getElementById('pm-code-resolver-input').value.trim();
        if (!code) return;
        elements.resolverResult.innerHTML = '<div class="pm-empty"><span class="spinner-border spinner-border-sm text-primary"></span></div>';
        try {
            const response = await pm.request('/admin/api/product-master/products/resolve?code=' + encodeURIComponent(code));
            const product = response.data;
            const specs = (product.components || []).map(function (component) {
                const dimension = component.managementDimensionText ? ' · ' + component.managementDimensionText : '';
                return '<div class="pm-preview-line"><span class="pm-preview-key">' + pm.escapeHtml(component.groupManagementLabel) + '</span>'
                    + '<span class="pm-preview-value">' + pm.escapeHtml(component.valueManagementLabel + dimension) + '</span></div>';
            }).join('');
            elements.resolverResult.innerHTML = '<div class="pm-card border shadow-none"><div class="pm-card-body">'
                + '<div class="d-flex justify-content-between align-items-start gap-3 mb-3"><div><span class="pm-code-badge">' + pm.escapeHtml(product.catalogCode) + '</span>'
                + '<h4 class="fw-bold mt-2 mb-1">' + pm.escapeHtml(product.productName) + '</h4><div class="pm-product-code" title="' + pm.escapeHtml(product.productCode) + '">' + pm.escapeHtml(product.productCode) + '</div></div>'
                + '<div class="text-end"><span class="pm-stock-number">' + pm.number(product.currentStock) + '</span><span class="' + pm.stockClass(product.stockStatus) + '">' + pm.escapeHtml(product.stockStatusLabel) + '</span></div></div>'
                + '<div class="pm-preview-grid"><article class="pm-preview-card"><h6>관리팀 기준 해석</h6>' + specs + '</article>'
                + '<article class="pm-preview-card"><h6>가격</h6><div class="pm-preview-line"><span class="pm-preview-key">공급가</span><span class="pm-preview-value">' + pm.money(product.supplyPrice) + '</span></div>'
                + '<div class="pm-preview-line"><span class="pm-preview-key">VAT 포함</span><span class="pm-preview-value">' + pm.money(product.totalPrice) + '</span></div></article>'
                + '<article class="pm-preview-card"><h6>바로가기</h6><div class="d-grid gap-2"><a class="btn btn-primary pm-btn" href="/admin/product-master/products/' + product.id + '">상세 수정</a>'
                + '<button class="btn btn-light pm-btn" type="button" data-pm-resolver-wide="' + product.id + '">넓게보기</button></div></article></div></div></div>';
            const wideButton = elements.resolverResult.querySelector('[data-pm-resolver-wide]');
            if (wideButton) wideButton.addEventListener('click', function () {
                resolverModal.hide();
                openWide(Number(wideButton.dataset.pmResolverWide));
            });
        } catch (error) {
            elements.resolverResult.innerHTML = '<div class="alert alert-danger mb-0"><i class="ri-error-warning-line me-1"></i>' + pm.escapeHtml(error.message) + '</div>';
        }
    }

    function resetFilters() {
        elements.filterForm.reset();
        document.getElementById('pm-filter-size').value = '30';
        state.size = 30;
        state.page = 0;
        state.sorts = [];
        setAdvancedFilter(false);
        updateAdvancedFilterCount();
        loadProducts(true);
    }

    function dimensionRangeError() {
        for (const filter of dimensionFilters) {
            const minimumText = document.getElementById(filter.minId).value;
            const maximumText = document.getElementById(filter.maxId).value;
            if (!minimumText || !maximumText) continue;
            if (Number(minimumText) > Number(maximumText)) {
                return filter.axis + ' 최소 치수는 최대 치수보다 클 수 없습니다.';
            }
        }
        for (const raw of selectedNumberAttributes()) {
            const parts = raw.split(':');
            if (parts[1] && parts[2] && Number(parts[1]) > Number(parts[2])) {
                const group = state.groups.find(function (item) { return item.id === Number(parts[0]); });
                return (group ? group.managementLabel : '숫자 옵션') + ' 최소값은 최대값보다 클 수 없습니다.';
            }
        }
        return null;
    }

    function statusBadgeClass(status) {
        if (status === 'ACTIVE') return 'bg-soft-success text-success';
        if (status === 'DISCONTINUED') return 'bg-soft-danger text-danger';
        return 'bg-soft-secondary text-secondary';
    }

    function emptyRow(message) {
        return '<tr><td colspan="9"><div class="pm-empty"><div><span class="pm-empty-icon"><i class="ri-inbox-archive-line"></i></span>'
            + '<strong>' + pm.escapeHtml(message) + '</strong></div></div></td></tr>';
    }

    elements.filterForm.addEventListener('submit', function (event) {
        event.preventDefault();
        if (!elements.filterForm.checkValidity()) {
            elements.filterForm.reportValidity();
            return;
        }
        const rangeError = dimensionRangeError();
        if (rangeError) {
            pm.alert('warning', '치수 범위를 확인해 주세요.', rangeError);
            return;
        }
        state.page = 0;
        state.size = Number(document.getElementById('pm-filter-size').value);
        loadProducts(true);
    });
    document.getElementById('pm-filter-size').addEventListener('change', function () {
        state.page = 0;
        state.size = Number(this.value);
        loadProducts(true);
    });
    document.getElementById('pm-toggle-advanced-filter').addEventListener('click', function () {
        setAdvancedFilter(this.getAttribute('aria-expanded') !== 'true');
    });
    elements.filterForm.addEventListener('change', updateAdvancedFilterCount);
    elements.filterForm.addEventListener('input', pm.debounce(updateAdvancedFilterCount, 80));
    document.getElementById('pm-reset-filters').addEventListener('click', resetFilters);
    document.getElementById('pm-refresh-products').addEventListener('click', function () { loadProducts(true); });
    document.querySelectorAll('[data-pm-sort]').forEach(function (button) {
        button.addEventListener('click', function () { cycleSort(button.dataset.pmSort); });
    });
    document.getElementById('pm-open-code-resolver').addEventListener('click', function () {
        document.getElementById('pm-code-resolver-input').value = '';
        elements.resolverResult.innerHTML = '<div class="pm-empty"><div><span class="pm-empty-icon"><i class="ri-scan-2-line"></i></span><strong>제품 코드를 입력해 주세요.</strong></div></div>';
        resolverModal.show();
        setTimeout(function () { document.getElementById('pm-code-resolver-input').focus(); }, 250);
    });
    document.getElementById('pm-code-resolver-form').addEventListener('submit', resolveCode);
    document.getElementById('pm-wide-modal').addEventListener('hidden.bs.modal', function () {
        elements.wideFrame.src = 'about:blank';
    });
    document.getElementById('pm-list-copy-qr-url').addEventListener('click', function () {
        pm.copy(document.getElementById('pm-list-qr-url').textContent);
    });
    document.getElementById('pm-list-download-qr').addEventListener('click', function () {
        if (!state.qrProduct || !state.qrSvg) return;
        pm.downloadText(state.qrProduct.catalogCode + '-QR.svg', state.qrSvg, 'image/svg+xml;charset=utf-8');
    });
    window.addEventListener('message', function (event) {
        if (event.origin !== window.location.origin || !event.data || event.data.type !== 'pm-product-saved') return;
        loadProducts(true);
    });

    initialize();
})(window, document);
