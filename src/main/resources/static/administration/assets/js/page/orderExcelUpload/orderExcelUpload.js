/* /administration/assets/js/page/orderExcelUpload/orderExcelUpload.js */
(function () {
    'use strict';

    const API_BASE = '/management/api/order-excel-upload';
    const BATHROOM_GOODS_DISPATCH_TEAM_CATEGORY_ID = '12';
    const BATHROOM_GOODS_CATEGORY_NAME = '욕실용품';

    const state = {
        options: {
            deliveryMethods: [],
            productionCategories: [],
            managers: [],
            deliveryHandlers: []
        },
        groups: [],
        saving: false,
        previewLoaded: false,
        hoveredImagePasteTarget: null,
        selectedImagePasteTarget: null
    };

    const els = {};

    document.addEventListener('DOMContentLoaded', init);

    async function init() {
        cacheElements();
        bindEvents();
        await loadOptions();
    }

    function cacheElements() {
        els.form = document.getElementById('order-excel-upload-form');
        els.file = document.getElementById('order-excel-file');
        els.directMethod = document.getElementById('order-excel-direct-method');
        els.siteMethod = document.getElementById('order-excel-site-method');
        els.previewBtn = document.getElementById('order-excel-preview-btn');
        els.saveBtn = document.getElementById('order-excel-save-btn');
        els.message = document.getElementById('order-excel-message');
        els.empty = document.getElementById('order-excel-empty');
        els.previewWrap = document.getElementById('order-excel-preview-wrap');
        els.footer = document.getElementById('order-excel-footer');
        els.previewSummary = document.getElementById('order-excel-preview-summary');
        els.footerSummary = document.getElementById('order-excel-footer-summary');
        els.collapseAll = document.getElementById('order-excel-collapse-all');
        els.expandAll = document.getElementById('order-excel-expand-all');
        els.managerList = document.getElementById('order-excel-manager-list');
        els.deliveryHandlerList = document.getElementById('order-excel-delivery-handler-list');
        els.loadingOverlay = document.getElementById('order-excel-loading-overlay');
        els.loadingTitle = document.getElementById('order-excel-loading-title');
        els.loadingDesc = document.getElementById('order-excel-loading-desc');
    }

    function bindEvents() {
        els.form.addEventListener('submit', handlePreviewSubmit);
        els.saveBtn.addEventListener('click', handleSave);

        els.previewWrap.addEventListener('input', handlePreviewInput);
        els.previewWrap.addEventListener('change', handlePreviewChange);
        els.previewWrap.addEventListener('click', handlePreviewClick);
        els.previewWrap.addEventListener('mouseover', handleImageTargetMouseOver);
        els.previewWrap.addEventListener('mouseout', handleImageTargetMouseOut);
        els.previewWrap.addEventListener('focusin', handleImageTargetFocusIn);
        els.previewWrap.addEventListener('click', handleImageTargetClick);
        els.previewWrap.addEventListener('change', handleImageInputChange);
        els.previewWrap.addEventListener('dragover', handleImageDragOver);
        els.previewWrap.addEventListener('dragleave', handleImageDragLeave);
        els.previewWrap.addEventListener('drop', handleImageDrop);
        document.addEventListener('paste', handleImagePaste);

        els.collapseAll.addEventListener('click', () => toggleAllGroups(false));
        els.expandAll.addEventListener('click', () => toggleAllGroups(true));
    }

    async function loadOptions() {
        try {
            const data = await fetchJson(`${API_BASE}/options`);
            state.options = data || state.options;
            renderMethodSelects();
            renderDatalists();
        } catch (error) {
            showMessage(error.message || '옵션 정보를 불러오지 못했습니다.', 'danger');
        }
    }

    function renderMethodSelects() {
        const methods = state.options.deliveryMethods || [];

        els.directMethod.innerHTML = `<option value="">DB에서 직배송 자동 선택</option>` + methods.map(method => `
            <option value="${method.id}" ${method.directCandidate ? 'data-default-direct="true"' : ''}>
                ${escapeHtml(method.methodName)}${method.methodPrice ? ` (${formatCurrency(method.methodPrice)})` : ''}
            </option>
        `).join('');

        els.siteMethod.innerHTML = `<option value="">DB에서 현장배송 자동 선택</option>` + methods.map(method => `
            <option value="${method.id}" ${method.siteCandidate ? 'data-default-site="true"' : ''}>
                ${escapeHtml(method.methodName)}${method.methodPrice ? ` (${formatCurrency(method.methodPrice)})` : ''}
            </option>
        `).join('');

        const directDefault = methods.find(method => method.directCandidate);
        const siteDefault = methods.find(method => method.siteCandidate);
        if (directDefault) {
            els.directMethod.value = String(directDefault.id);
        }
        if (siteDefault) {
            els.siteMethod.value = String(siteDefault.id);
        }
    }

    function renderDatalists() {
        els.managerList.innerHTML = (state.options.managers || [])
            .map(item => `<option value="${escapeHtml(item.name)}" data-id="${item.id}"></option>`)
            .join('');

        els.deliveryHandlerList.innerHTML = (state.options.deliveryHandlers || [])
            .map(item => `<option value="${escapeHtml(item.name)}" data-id="${item.id}"></option>`)
            .join('');
    }

    async function handlePreviewSubmit(event) {
        event.preventDefault();
        hideMessage();

        const file = els.file.files && els.file.files[0];
        if (!file) {
            showMessage('엑셀 파일을 선택해 주세요.', 'warning');
            return;
        }

        const formData = new FormData();
        formData.append('file', file);
        if (els.directMethod.value) {
            formData.append('directDeliveryMethodId', els.directMethod.value);
        }
        if (els.siteMethod.value) {
            formData.append('siteDeliveryMethodId', els.siteMethod.value);
        }

        setLoading(true, '미리보기 생성 중...');

        try {
            const data = await fetchMultipart(`${API_BASE}/preview`, formData);
            state.groups = data.groups || [];
            state.options = data.options || state.options;
            normalizeDeliveryHandlerSelections();
            initializeRowImages();
            state.previewLoaded = true;

            renderMethodSelectsKeepSelected();
            renderDatalists();
            renderPreview();
            showMessage(data.message || '미리보기가 생성되었습니다.', data.issues && hasErrorIssues(data.issues) ? 'warning' : 'success');
        } catch (error) {
            showMessage(error.message || '미리보기 생성에 실패했습니다.', 'danger');
        } finally {
            setLoading(false);
        }
    }

    function renderMethodSelectsKeepSelected() {
        const directValue = els.directMethod.value;
        const siteValue = els.siteMethod.value;
        renderMethodSelects();
        if (directValue) els.directMethod.value = directValue;
        if (siteValue) els.siteMethod.value = siteValue;
    }

    function renderPreview() {
        if (!state.groups.length) {
            els.empty.classList.remove('d-none');
            els.previewWrap.classList.add('d-none');
            els.footer.classList.add('d-none');
            els.previewWrap.innerHTML = '';
            updateSummary();
            return;
        }

        els.empty.classList.add('d-none');
        els.previewWrap.classList.remove('d-none');
        els.footer.classList.remove('d-none');
        els.previewWrap.innerHTML = state.groups.map(renderGroup).join('');
        refreshImagePasteTargetUi();
        updateSummary();
    }

    function renderGroup(group, groupIndex) {
        const groupIssues = group.issues || [];
        const hasError = groupHasError(group);
        const collapsed = Boolean(group.collapsed);

        return `
            <div class="order-excel-group ${hasError ? 'has-error' : ''}" data-group-index="${groupIndex}">
                <div class="order-excel-group-header">
                    <button type="button" class="order-excel-group-toggle" data-action="toggle-group" data-group-index="${groupIndex}">
                        <i class="${collapsed ? 'ri-arrow-down-s-line' : 'ri-arrow-up-s-line'}"></i>
                        <strong>Task ${group.groupNo}</strong>
                        <span>${escapeHtml(group.companyName || '-')}</span>
                        <span class="badge ${group.siteDelivery ? 'bg-primary-subtle text-primary' : 'bg-secondary-subtle text-secondary'}">
                            ${escapeHtml(group.deliveryRuleLabel || (group.siteDelivery ? '현장배송 묶음' : '직배송 묶음'))}
                        </span>
                        ${hasError ? '<span class="badge bg-danger-subtle text-danger">오류 있음</span>' : ''}
                    </button>
                    <div class="order-excel-group-stats">
                        <span>${(group.rows || []).length}개 주문</span>
                        <span>${formatCurrency(getGroupTotal(group))}</span>
                    </div>
                </div>

                <div class="order-excel-group-body ${collapsed ? 'd-none' : ''}">
                    ${renderGroupIssues(groupIssues)}
                    ${renderGroupMeta(group, groupIndex)}
                    ${renderRowsTable(group, groupIndex)}
                </div>
            </div>
        `;
    }

    function renderGroupIssues(issues) {
        if (!issues || !issues.length) {
            return '';
        }

        return `
            <div class="order-excel-issues">
                ${issues.map(renderIssue).join('')}
            </div>
        `;
    }

    function renderIssue(issue) {
        const cls = issue.level === 'ERROR' ? 'danger' : 'warning';
        return `
            <div class="alert alert-${cls} py-2 mb-2">
                ${issue.excelRowNumber ? `엑셀 ${issue.excelRowNumber}행 · ` : ''}${escapeHtml(issue.message || '')}
            </div>
        `;
    }

    function renderGroupMeta(group, groupIndex) {
        return `
            <div class="order-excel-group-meta">
                <div>
                    <label>거래처명</label>
                    <input type="text" class="form-control form-control-sm" data-group-field="companyName" data-group-index="${groupIndex}" value="${escapeHtml(group.companyName || '')}">
                    <input type="hidden" data-group-field="companyId" data-group-index="${groupIndex}" value="${group.companyId || ''}">
                </div>
                <div>
                    <label>사업자등록번호</label>
                    <input type="text" class="form-control form-control-sm" inputmode="numeric" maxlength="10" data-group-field="businessNumber" data-group-index="${groupIndex}" value="${escapeHtml(group.businessNumber || '')}" placeholder="숫자 10자리">
                </div>
                <div>
                    <label>요청자</label>
                    <input type="text" class="form-control form-control-sm" data-group-field="requestedByName" data-group-index="${groupIndex}" value="${escapeHtml(group.requestedByName || '')}" readonly>
                    <input type="hidden" data-group-field="requestedByMemberId" data-group-index="${groupIndex}" value="${group.requestedByMemberId || ''}">
                </div>
                <div>
                    <label>우리회사 담당자</label>
                    <input type="text" class="form-control form-control-sm" list="order-excel-manager-list" data-group-field="managedByName" data-group-index="${groupIndex}" value="${escapeHtml(group.managedByName || '')}">
                    <input type="hidden" data-group-field="managedByMemberId" data-group-index="${groupIndex}" value="${group.managedByMemberId || ''}">
                </div>
                <div>
                    <label>배송수단</label>
                    <select class="form-select form-select-sm" data-group-field="deliveryMethodId" data-group-index="${groupIndex}">
                        ${renderDeliveryMethodOptions(group.deliveryMethodId)}
                    </select>
                </div>
                <div>
                    <label>배송담당자</label>
                    ${renderGroupDeliveryHandlerSelect(group, groupIndex)}
                    <input type="hidden" data-group-field="deliveryHandlerName" data-group-index="${groupIndex}" value="${escapeHtml(group.deliveryHandlerName || '')}">
                </div>
                <div>
                    <label>운임비</label>
                    <input type="text" class="form-control form-control-sm text-end" data-group-field="deliveryCost" data-group-index="${groupIndex}" value="${formatNumber(group.deliveryCost)}">
                </div>
                <div>
                    <label>포장비</label>
                    <input type="text" class="form-control form-control-sm text-end" data-group-field="packingCost" data-group-index="${groupIndex}" value="${formatNumber(group.packingCost)}">
                </div>
            </div>

            <div class="order-excel-address-grid">
                ${renderAddressBox(group, groupIndex, 'basic')}
                ${renderAddressBox(group, groupIndex, 'site')}
            </div>
        `;
    }

    function renderAddressBox(group, groupIndex, type) {
        const isSite = type === 'site';
        const prefix = isSite ? 'site' : '';
        const boxTitle = isSite ? '현장 배송지' : '기본 배송지';
        const disabledClass = isSite && !group.siteDelivery ? 'is-disabled' : '';
        const zipField = isSite ? 'siteZipCode' : 'zipCode';
        const doField = isSite ? 'siteDoName' : 'doName';
        const siField = isSite ? 'siteSiName' : 'siName';
        const guField = isSite ? 'siteGuName' : 'guName';
        const roadField = isSite ? 'siteRoadAddress' : 'roadAddress';
        const detailField = isSite ? 'siteDetailAddress' : 'detailAddress';
        const searchLabel = isSite ? '현장 주소검색' : '기본 주소검색';

        return `
            <div class="order-excel-address-box ${disabledClass}" data-address-type="${type}" data-group-index="${groupIndex}">
                <div class="order-excel-address-title">
                    <span>${boxTitle}</span>
                    <div class="order-excel-address-title-actions">
                        ${isSite ? `
                            <label class="form-check form-switch mb-0 order-excel-site-switch">
                                <input type="checkbox" class="form-check-input" data-group-field="siteDelivery" data-group-index="${groupIndex}" ${group.siteDelivery ? 'checked' : ''} title="현장 배송지 사용">
                            </label>
                        ` : ''}
                        <button type="button" class="btn btn-sm btn-outline-primary" data-action="search-address" data-address-type="${type}" data-group-index="${groupIndex}">${searchLabel}</button>
                        <button type="button" class="btn btn-sm btn-outline-secondary" data-action="clear-address" data-address-type="${type}" data-group-index="${groupIndex}">초기화</button>
                    </div>
                </div>
                ${renderAddressCorrectionInfo(group, type)}
                <div class="order-excel-address-inputs">
                    <input type="text" placeholder="우편번호" data-group-field="${zipField}" data-group-index="${groupIndex}" value="${escapeHtml(group[zipField] || '')}" readonly>
                    <input type="text" placeholder="도/시" data-group-field="${doField}" data-group-index="${groupIndex}" value="${escapeHtml(group[doField] || '')}" readonly>
                    <input type="text" placeholder="시/군" data-group-field="${siField}" data-group-index="${groupIndex}" value="${escapeHtml(group[siField] || '')}" readonly>
                    <input type="text" placeholder="구" data-group-field="${guField}" data-group-index="${groupIndex}" value="${escapeHtml(group[guField] || '')}" readonly>
                    <input type="text" placeholder="도로명 주소" data-group-field="${roadField}" data-group-index="${groupIndex}" value="${escapeHtml(group[roadField] || '')}" readonly>
                    <input type="text" placeholder="상세주소" data-group-field="${detailField}" data-group-index="${groupIndex}" value="${escapeHtml(group[detailField] || '')}">
                    ${isSite ? `
                        <input type="text" placeholder="수령자" data-group-field="siteRecipientName" data-group-index="${groupIndex}" value="${escapeHtml(group.siteRecipientName || '')}">
                        <input type="text" placeholder="수령자 연락처" data-group-field="siteRecipientPhone" data-group-index="${groupIndex}" value="${escapeHtml(group.siteRecipientPhone || '')}">
                    ` : ''}
                </div>
            </div>
        `;
    }

    function renderAddressCorrectionInfo(group, type) {
        if (type !== 'site' || !group || !group.siteAddressDisplayText) {
            return '';
        }

        return `
            <div class="order-excel-address-correction">
                ${escapeHtml(group.siteAddressDisplayText)}
            </div>
        `;
    }

    function renderRowsTable(group, groupIndex) {
        const rows = group.rows || [];
        if (!rows.length) {
            return `<div class="alert alert-warning mb-0">저장할 제품 행이 없습니다.</div>`;
        }

        return `
            <div class="order-excel-table-wrap">
                <table class="table table-sm table-bordered align-middle order-excel-table">
                    <thead>
                        <tr>
                            <th class="order-excel-col-switch">저장</th>
                            <th class="order-excel-col-switch">규격</th>
                            <th class="order-excel-col-switch">거울</th>
                            <th>엑셀행</th>
                            <th>출고일</th>
                            <th>담당팀</th>
                            <th>중분류</th>
                            <th>원본 품목명</th>
                            <th>저장 제품명</th>
                            <th>사이즈</th>
                            <th>색상</th>
                            <th>수량</th>
                            <th>단가</th>
                            <th>공급가</th>
                            <th>부가세</th>
                            <th>합계</th>
                            <th>관리자 메모</th>
                            <th>이미지</th>
                            <th>오류/확인</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${rows.map((row, rowIndex) => renderRow(groupIndex, rowIndex, row)).join('')}
                    </tbody>
                </table>
            </div>
        `;
    }

    function renderRow(groupIndex, rowIndex, row) {
        const rowError = rowHasError(row);
        return `
            <tr class="${rowError ? 'table-danger' : ''}" data-group-index="${groupIndex}" data-row-index="${rowIndex}">
                <td class="text-center order-excel-switch-td">
                    <div class="form-check form-switch order-excel-switch-cell order-excel-switch-cell-mini">
                        <input type="checkbox" class="form-check-input" data-row-field="saveTarget" data-group-index="${groupIndex}" data-row-index="${rowIndex}" ${row.saveTarget !== false ? 'checked' : ''} title="저장 여부">
                    </div>
                </td>
                <td class="text-center order-excel-switch-td">
                    <div class="form-check form-switch order-excel-switch-cell order-excel-switch-cell-mini">
                        <input type="checkbox" class="form-check-input" data-row-field="standard" data-group-index="${groupIndex}" data-row-index="${rowIndex}" ${row.standard ? 'checked' : ''} title="규격 여부">
                    </div>
                </td>
                <td class="text-center order-excel-switch-td">
                    <div class="form-check form-switch order-excel-switch-cell order-excel-switch-cell-mini">
                        <input type="checkbox" class="form-check-input" data-row-field="mirrorCuttingProduct" data-group-index="${groupIndex}" data-row-index="${rowIndex}" ${row.mirrorCuttingProduct ? 'checked' : ''} title="거울재단 여부">
                    </div>
                </td>
                <td class="text-center order-excel-row-no">${row.excelRowNumber || ''}</td>
                <td><input type="date" class="form-control form-control-sm" data-row-field="preferredDeliveryDate" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.preferredDeliveryDate || '')}"></td>
                <td>
                    <select class="form-select form-select-sm order-excel-category-select" data-row-field="productionCategoryId" data-group-index="${groupIndex}" data-row-index="${rowIndex}">
                        ${renderProductionCategoryOptions(row.productionCategoryId)}
                    </select>
                    <input type="hidden" data-row-field="categoryName" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.categoryName || '')}">
                    <div class="order-excel-category-save-label">저장 카테고리: ${escapeHtml(row.categoryName || '-')}</div>
                </td>
                <td><select class="form-select form-select-sm order-excel-middle-category-select" data-row-field="middleCategoryName" data-group-index="${groupIndex}" data-row-index="${rowIndex}">${renderMiddleCategoryOptions(row.categoryName, row.middleCategoryName)}</select></td>
                <td><input type="text" class="form-control form-control-sm" data-row-field="originalItemName" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.originalItemName || '')}"></td>
                <td><input type="text" class="form-control form-control-sm order-excel-product-name" data-row-field="itemNameForSave" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.itemNameForSave || '')}"></td>
                <td><input type="text" class="form-control form-control-sm" data-row-field="size" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.size || '')}"></td>
                <td><input type="text" class="form-control form-control-sm" data-row-field="color" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.color || '')}"></td>
                <td><input type="text" class="form-control form-control-sm text-end order-excel-money order-excel-quantity" data-row-field="quantity" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${formatNumber(row.quantity)}" title="0 또는 음수 입력 가능"></td>
                <td><input type="text" class="form-control form-control-sm text-end order-excel-money" data-row-field="productCost" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${formatNumber(row.productCost)}"></td>
                <td><input type="text" class="form-control form-control-sm text-end order-excel-money" data-row-field="supplyPrice" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${formatNumber(row.supplyPrice)}"></td>
                <td><input type="text" class="form-control form-control-sm text-end order-excel-money" data-row-field="vatAmount" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${formatNumber(row.vatAmount)}"></td>
                <td><input type="text" class="form-control form-control-sm text-end order-excel-money" data-row-field="totalAmount" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${formatNumber(row.totalAmount)}"></td>
                <td class="order-excel-admin-memo-cell"><textarea class="form-control form-control-sm order-excel-admin-memo" rows="2" maxlength="200" data-row-field="adminMemo" data-group-index="${groupIndex}" data-row-index="${rowIndex}" placeholder="관리자 메모 입력">${escapeHtml(row.adminMemo || '')}</textarea></td>
                <td class="order-excel-image-cell">${renderRowImages(groupIndex, rowIndex, row)}</td>
                <td class="order-excel-issue-cell">${renderCellIssues(row.issues || [])}</td>
            </tr>
        `;
    }

    function renderCellIssues(issues) {
        if (!issues.length) {
            return '<span class="text-muted">-</span>';
        }
        return issues.map(issue => `
            <div class="order-excel-cell-issue ${issue.level === 'ERROR' ? 'is-error' : 'is-warn'}">
                ${escapeHtml(issue.message || '')}
            </div>
        `).join('');
    }

    function renderDeliveryMethodOptions(selectedId) {
        const methods = state.options.deliveryMethods || [];
        return `<option value="">선택</option>` + methods.map(method => `
            <option value="${method.id}" ${String(selectedId || '') === String(method.id) ? 'selected' : ''}>
                ${escapeHtml(method.methodName)}
            </option>
        `).join('');
    }

    function renderProductionCategoryOptions(selectedId) {
        const categories = state.options.productionCategories || [];
        return `<option value="">선택</option>` + categories.map(category => `
            <option value="${category.id}" ${String(selectedId || '') === String(category.id) ? 'selected' : ''}>
                ${escapeHtml(category.name)}
            </option>
        `).join('');
    }


    function renderMiddleCategoryOptions(categoryName, selectedName) {
        const categoryKey = String(categoryName || '').trim();
        const byCategory = state.options.middleCategoriesByCategory || {};
        let options = byCategory[categoryKey] || state.options.middleCategories || [];
        const selected = String(selectedName || '').trim() || '분류없음';
        const hasSelected = options.some(item => String(item.name || '').trim() === selected);
        if (!hasSelected && selected) {
            options = [{ id: null, name: selected }].concat(options);
        }
        return options.map(item => `
            <option value="${escapeHtml(item.name || '')}" ${String(item.name || '') === selected ? 'selected' : ''}>
                ${escapeHtml(item.name || '')}
            </option>
        `).join('') || `<option value="분류없음" selected>분류없음</option>`;
    }

    function renderRowImages(groupIndex, rowIndex, row) {
        ensureRowImageState(row);
        const images = row.images || [];
        const imageList = images.length ? `
            <div class="order-excel-image-preview-list">
                ${images.map(image => `
                    <div class="order-excel-image-thumb" title="${escapeHtml(image.name || '')}">
                        <img src="${image.url}" alt="${escapeHtml(image.name || '업로드 이미지')}">
                        <button type="button" class="order-excel-image-remove" data-action="remove-image" data-group-index="${groupIndex}" data-row-index="${rowIndex}" data-image-id="${image.id}" aria-label="이미지 삭제">×</button>
                    </div>
                `).join('')}
            </div>
        ` : '<div class="order-excel-image-empty-text">이미지 없음</div>';

        return `
            <div class="order-excel-image-drop" tabindex="0" role="button" aria-label="이미지 업로드 영역. 파일 선택, 드래그 앤 드롭, 컨트롤 브이 붙여넣기로 이미지를 추가할 수 있습니다." data-group-index="${groupIndex}" data-row-index="${rowIndex}">
                <input type="file" class="order-excel-image-input" accept="image/*" multiple data-group-index="${groupIndex}" data-row-index="${rowIndex}">
                <button type="button" class="btn btn-sm btn-outline-primary order-excel-image-add-btn" data-action="open-image-picker" data-group-index="${groupIndex}" data-row-index="${rowIndex}">이미지 추가</button>
                <div class="order-excel-image-help">드래그 · 파일선택 · Ctrl+V 붙여넣기</div>
                <div class="order-excel-image-paste-hint">영역 위에 마우스를 올리거나 클릭한 뒤 이미지를 붙여넣을 수 있습니다.</div>
                ${imageList}
            </div>
        `;
    }

    function renderGroupDeliveryHandlerSelect(group, groupIndex) {
        const method = findOptionById(state.options.deliveryMethods || [], group.deliveryMethodId);
        const ruleCode = deliveryRuleCodeFromMethod(method);
        const required = isHandlerRequiredRuleCode(ruleCode);
        const disabled = isNoHandlerRuleCode(ruleCode);
        const selectedHandler = disabled ? null : resolveSelectedDeliveryHandler(group);
        const selectedHandlerId = selectedHandler ? selectedHandler.id : null;
        const missingRequiredHandler = required && !selectedHandlerId;
        const requirementText = missingRequiredHandler
            ? '<div class="form-text text-danger">필수 선택</div>'
            : '';

        return `
            <select class="form-select form-select-sm order-excel-delivery-handler-select"
                    data-group-field="deliveryHandlerMemberId"
                    data-group-index="${groupIndex}"
                    ${disabled ? 'disabled' : ''}>
                ${renderDeliveryHandlerOptions(selectedHandlerId, required, disabled)}
            </select>
            ${requirementText}
        `;
    }

    function renderDeliveryHandlerOptions(selectedId, required, disabled) {
        const handlers = state.options.deliveryHandlers || [];
        const emptyLabel = disabled ? '담당자 지정 불필요' : (required ? '배송담당자 선택 필수' : '미지정');
        return `<option value="">${emptyLabel}</option>` + handlers.map(handler => `
            <option value="${handler.id}" ${String(selectedId || '') === String(handler.id) ? 'selected' : ''}>
                ${escapeHtml(handler.name)}
            </option>
        `).join('');
    }

    function handlePreviewInput(event) {
        const target = event.target;
        if (target.dataset.groupField) {
            updateGroupField(target);
            return;
        }
        if (target.dataset.rowField) {
            updateRowField(target);
        }
    }

    function handlePreviewChange(event) {
        const target = event.target;
        if (target.dataset.groupField) {
            updateGroupField(target);
            if (target.dataset.groupField === 'siteDelivery'
                    || target.dataset.groupField === 'deliveryMethodId'
                    || target.dataset.groupField === 'deliveryHandlerMemberId') {
                renderPreview();
            }
            return;
        }
        if (target.dataset.rowField) {
            updateRowField(target);
            if (target.dataset.rowField === 'productionCategoryId') {
                const groupIndex = Number(target.dataset.groupIndex);
                const rowIndex = Number(target.dataset.rowIndex);
                const category = (state.options.productionCategories || []).find(item => String(item.id) === String(target.value));
                if (category && state.groups[groupIndex] && state.groups[groupIndex].rows[rowIndex]) {
                    const categoryNameForSave = String(category.id) === BATHROOM_GOODS_DISPATCH_TEAM_CATEGORY_ID
                        ? BATHROOM_GOODS_CATEGORY_NAME
                        : category.name;
                    state.groups[groupIndex].rows[rowIndex].categoryName = categoryNameForSave;
                    const hidden = target.closest('tr').querySelector('[data-row-field="categoryName"]');
                    if (hidden) hidden.value = categoryNameForSave;
                    renderPreview();
                }
            }
        }
    }

    function handlePreviewClick(event) {
        const button = event.target.closest('[data-action]');
        if (!button) return;

        const action = button.dataset.action;
        if (action === 'toggle-group') {
            const group = state.groups[Number(button.dataset.groupIndex)];
            if (group) {
                group.collapsed = !Boolean(group.collapsed);
                renderPreview();
            }
            return;
        }

        if (action === 'open-image-picker') {
            const drop = button.closest('.order-excel-image-drop');
            if (drop) {
                setSelectedImagePasteTarget(getImagePasteTargetFromDrop(drop));
            }
            const input = drop ? drop.querySelector('.order-excel-image-input') : null;
            if (input) input.click();
            return;
        }

        if (action === 'remove-image') {
            removeRowImage(Number(button.dataset.groupIndex), Number(button.dataset.rowIndex), button.dataset.imageId);
            renderPreview();
            return;
        }

        if (action === 'search-address') {
            openDaumPostcode(Number(button.dataset.groupIndex), button.dataset.addressType || 'basic');
            return;
        }

        if (action === 'clear-address') {
            clearAddress(Number(button.dataset.groupIndex), button.dataset.addressType || 'basic');
            renderPreview();
        }
    }

    function updateGroupField(target) {
        const group = state.groups[Number(target.dataset.groupIndex)];
        if (!group) return;

        const field = target.dataset.groupField;
        let value = target.type === 'checkbox' ? target.checked : target.value;

        if (['deliveryCost', 'packingCost'].includes(field)) {
            value = parseMoney(value);
            target.value = formatNumber(value);
        }

        if (field === 'businessNumber') {
            value = String(value || '').replace(/\D/g, '').slice(0, 10);
            target.value = value;
            clearResolvedCompanySelection(group, target);
        }

        if (field === 'companyName') {
            clearResolvedCompanySelection(group, target);
        }

        if (field === 'managedByName') {
            const match = findOptionByName(state.options.managers || [], value);
            group.managedByMemberId = match ? match.id : null;
            const hidden = target.parentElement.querySelector('[data-group-field="managedByMemberId"]');
            if (hidden) hidden.value = match ? match.id : '';
        }

        if (field === 'deliveryMethodId') {
            const selectedMethod = findOptionById(state.options.deliveryMethods || [], value);
            value = selectedMethod ? selectedMethod.id : null;
            const ruleCode = deliveryRuleCodeFromMethod(selectedMethod);
            group.deliveryRuleCode = ruleCode;
            group.deliveryRuleLabel = selectedMethod ? selectedMethod.methodName : '';

            if (['SITE', 'CARGO'].includes(ruleCode)) {
                group.siteDelivery = true;
            }

            if (isNoHandlerRuleCode(ruleCode)) {
                group.deliveryHandlerMemberId = null;
                group.deliveryHandlerName = '';
                applyGroupDeliveryHandlerToRows(group);
            }
        }

        if (field === 'deliveryHandlerMemberId') {
            const selected = findOptionById(state.options.deliveryHandlers || [], value);
            value = selected ? selected.id : null;
            group.deliveryHandlerMemberId = value;
            group.deliveryHandlerName = selected ? selected.name : '';
            const hiddenName = target.parentElement.querySelector('[data-group-field="deliveryHandlerName"]');
            if (hiddenName) hiddenName.value = selected ? selected.name : '';
            applyGroupDeliveryHandlerToRows(group);
        }

        group[field] = value;
        updateSummary();
    }

    function updateRowField(target) {
        const group = state.groups[Number(target.dataset.groupIndex)];
        const row = group && group.rows ? group.rows[Number(target.dataset.rowIndex)] : null;
        if (!row) return;

        const field = target.dataset.rowField;
        let value = target.type === 'checkbox' ? target.checked : target.value;

        if (['quantity', 'productCost', 'supplyPrice', 'vatAmount', 'totalAmount'].includes(field)) {
            value = parseMoney(value);
            target.value = formatNumber(value);
        }

        if (field === 'deliveryHandlerMemberId') {
            const selected = findOptionById(state.options.deliveryHandlers || [], value);
            value = selected ? selected.id : null;
            row.deliveryHandlerName = selected ? selected.name : '';
            const hiddenName = target.parentElement.querySelector('[data-row-field="deliveryHandlerName"]');
            if (hiddenName) hiddenName.value = selected ? selected.name : '';
        }

        if (field === 'deliveryHandlerName') {
            const match = findOptionByName(state.options.deliveryHandlers || [], value);
            row.deliveryHandlerMemberId = match ? match.id : null;
            const hidden = target.parentElement.querySelector('[data-row-field="deliveryHandlerMemberId"]');
            if (hidden) hidden.value = match ? match.id : '';
        }

        row[field] = value;
        updateSummary();
    }

    function clearResolvedCompanySelection(group, target) {
        group.companyId = null;
        group.requestedByMemberId = null;
        group.requestedByName = '';

        const groupEl = target.closest('.order-excel-group');
        if (!groupEl) return;

        const companyIdEl = groupEl.querySelector('[data-group-field="companyId"]');
        const requestedByIdEl = groupEl.querySelector('[data-group-field="requestedByMemberId"]');
        const requestedByNameEl = groupEl.querySelector('[data-group-field="requestedByName"]');
        if (companyIdEl) companyIdEl.value = '';
        if (requestedByIdEl) requestedByIdEl.value = '';
        if (requestedByNameEl) requestedByNameEl.value = '';
    }

    function applyGroupDeliveryHandlerToRows(group) {
        if (!group || !Array.isArray(group.rows)) return;
        group.rows.forEach(row => {
            row.deliveryHandlerMemberId = group.deliveryHandlerMemberId || null;
            row.deliveryHandlerName = group.deliveryHandlerName || '';
        });
    }

    async function handleSave() {
        hideMessage();
        if (!state.previewLoaded || !state.groups.length) {
            showMessage('먼저 엑셀 미리보기를 생성해 주세요.', 'warning');
            return;
        }

        const payload = buildSavePayload();
        const activeOrderCount = payload.groups.reduce((sum, group) => {
            return sum + (group.rows || []).filter(row => row.saveTarget).length;
        }, 0);

        if (activeOrderCount <= 0) {
            showMessage('저장 대상으로 선택된 주문 행이 없습니다.', 'warning');
            return;
        }

        const missingHandlerGroup = payload.groups.find(group => {
            const hasActiveRow = (group.rows || []).some(row => row.saveTarget);
            if (!hasActiveRow) return false;
            const method = findOptionById(state.options.deliveryMethods || [], group.deliveryMethodId);
            return deliveryMethodRequiresHandler(method) && !group.deliveryHandlerMemberId;
        });
        if (missingHandlerGroup) {
            const method = findOptionById(state.options.deliveryMethods || [], missingHandlerGroup.deliveryMethodId);
            showMessage(`Task ${missingHandlerGroup.groupNo}의 ${method ? method.methodName : '선택한 배송수단'}은(는) 배송담당자를 반드시 선택해야 합니다.`, 'danger');
            const stateGroup = state.groups.find(group => String(group.groupNo) === String(missingHandlerGroup.groupNo));
            if (stateGroup) stateGroup.collapsed = false;
            renderPreview();
            return;
        }

        if (!confirm(`${payload.groups.length}개 Task / ${activeOrderCount}개 Order를 저장하시겠습니까?`)) {
            return;
        }

        setSaving(true);
        try {
            const formData = buildSaveFormData(payload);
            const response = await fetchMultipart(`${API_BASE}/save`, formData);

            if (!response.success) {
                renderSaveIssues(response.issues || []);
                showMessage(response.message || '저장할 수 없는 오류가 있습니다.', 'danger');
                return;
            }

            showMessage(`${response.message} Task ${response.taskCount}건 / Order ${response.orderCount}건 저장 완료`, 'success');
        } catch (error) {
            if (error.payload && error.payload.issues) {
                renderSaveIssues(error.payload.issues);
            }
            showMessage(error.message || '저장에 실패했습니다.', 'danger');
        } finally {
            setSaving(false);
        }
    }

    function buildSavePayload() {
        return {
            groups: state.groups.map(group => ({
                groupNo: group.groupNo,
                companyName: group.companyName,
                businessNumber: String(group.businessNumber || '').replace(/\D/g, ''),
                companyId: normalizeId(group.companyId),
                requestedByName: group.requestedByName,
                requestedByMemberId: normalizeId(group.requestedByMemberId),
                managedByName: group.managedByName,
                managedByMemberId: normalizeId(group.managedByMemberId),
                deliveryMethodId: normalizeId(group.deliveryMethodId),
                deliveryHandlerName: group.deliveryHandlerName,
                deliveryHandlerMemberId: normalizeId(group.deliveryHandlerMemberId),
                deliveryRuleCode: group.deliveryRuleCode,
                siteDelivery: Boolean(group.siteDelivery),
                deliveryCost: Number(group.deliveryCost || 0),
                packingCost: Number(group.packingCost || 0),
                zipCode: group.zipCode,
                doName: group.doName,
                siName: group.siName,
                guName: group.guName,
                roadAddress: group.roadAddress,
                detailAddress: group.detailAddress,
                siteZipCode: group.siteZipCode,
                siteDoName: group.siteDoName,
                siteSiName: group.siteSiName,
                siteGuName: group.siteGuName,
                siteRoadAddress: group.siteRoadAddress,
                siteDetailAddress: group.siteDetailAddress,
                siteRecipientName: group.siteRecipientName,
                siteRecipientPhone: group.siteRecipientPhone,
                ordererName: group.ordererName,
                ordererPhone: group.ordererPhone,
                rows: (group.rows || []).map(row => ({
                    excelRowNumber: row.excelRowNumber,
                    saveTarget: row.saveTarget !== false,
                    preferredDeliveryDate: row.preferredDeliveryDate,
                    originalItemName: row.originalItemName,
                    itemNameForSave: row.itemNameForSave,
                    calculatedProductName: row.calculatedProductName,
                    categoryName: row.categoryName,
                    productionCategoryId: normalizeId(row.productionCategoryId),
                    middleCategoryName: row.middleCategoryName,
                    size: row.size,
                    color: row.color,
                    quantity: Number(row.quantity || 0),
                    adminMemo: row.adminMemo,
                    deliveryHandlerName: row.deliveryHandlerName || group.deliveryHandlerName || '',
                    deliveryHandlerMemberId: normalizeId(row.deliveryHandlerMemberId || group.deliveryHandlerMemberId),
                    productCost: Number(row.productCost || 0),
                    supplyPrice: Number(row.supplyPrice || 0),
                    vatAmount: Number(row.vatAmount || 0),
                    totalAmount: Number(row.totalAmount || 0),
                    standard: Boolean(row.standard),
                    mirrorCuttingProduct: Boolean(row.mirrorCuttingProduct),
                    imageKey: row.imageKey
                }))
            }))
        };
    }


    function openDaumPostcode(groupIndex, type) {
        const group = state.groups[groupIndex];
        if (!group) return;

        if (!window.daum || !window.daum.Postcode) {
            showMessage('Daum 주소검색 스크립트를 불러오지 못했습니다. 네트워크 또는 스크립트 로딩을 확인해 주세요.', 'danger');
            return;
        }

        new window.daum.Postcode({
            oncomplete: function (data) {
                applyDaumAddress(group, type, data || {});
                renderPreview();
            }
        }).open();
    }

    function applyDaumAddress(group, type, data) {
        const region = splitDaumRegion(data.sido || '', data.sigungu || '');
        const roadAddress = data.roadAddress || data.autoRoadAddress || data.address || '';

        if (type === 'site') {
            group.siteDelivery = true;
            group.siteZipCode = data.zonecode || '';
            group.siteDoName = region.doName;
            group.siteSiName = region.siName;
            group.siteGuName = region.guName;
            group.siteRoadAddress = roadAddress;
            group.siteDetailAddress = group.siteDetailAddress || '';
            group.siteAddressDisplayText = '';
            return;
        }

        group.zipCode = data.zonecode || '';
        group.doName = region.doName;
        group.siName = region.siName;
        group.guName = region.guName;
        group.roadAddress = roadAddress;
        group.detailAddress = group.detailAddress || '';
    }

    function clearAddress(groupIndex, type) {
        const group = state.groups[groupIndex];
        if (!group) return;

        if (type === 'site') {
            group.siteZipCode = '';
            group.siteDoName = '';
            group.siteSiName = '';
            group.siteGuName = '';
            group.siteRoadAddress = '';
            group.siteDetailAddress = '';
            group.siteRecipientName = '';
            group.siteRecipientPhone = '';
            group.siteAddressDisplayText = '';
            return;
        }

        group.zipCode = '';
        group.doName = '';
        group.siName = '';
        group.guName = '';
        group.roadAddress = '';
        group.detailAddress = '';
    }

    function splitDaumRegion(sido, sigungu) {
        const doName = String(sido || '').trim();
        const sigunguText = String(sigungu || '').trim();
        const parts = sigunguText.split(/\s+/).filter(Boolean);
        const metropolitan = isMetropolitanProvince(doName);

        if (metropolitan) {
            return {
                doName,
                siName: '',
                guName: parts[0] || sigunguText
            };
        }

        if (parts.length >= 2 && /[시군]$/.test(parts[0])) {
            return {
                doName,
                siName: parts[0],
                guName: parts[1]
            };
        }

        if (parts.length >= 1) {
            return {
                doName,
                siName: /[시군]$/.test(parts[0]) ? parts[0] : '',
                guName: /[구군]$/.test(parts[0]) ? parts[0] : ''
            };
        }

        return { doName, siName: '', guName: '' };
    }

    function isMetropolitanProvince(value) {
        const text = String(value || '').trim();
        return text.endsWith('특별시')
            || text.endsWith('광역시')
            || text.endsWith('특별자치시')
            || text === '세종특별자치시';
    }

    function initializeRowImages() {
        state.groups.forEach((group, groupIndex) => {
            (group.rows || []).forEach((row, rowIndex) => {
                ensureRowImageState(row, groupIndex, rowIndex);
            });
        });
    }

    function ensureRowImageState(row, groupIndex, rowIndex) {
        if (!row) return;
        if (!row.imageKey) {
            row.imageKey = `g${groupIndex || 0}r${row.excelRowNumber || rowIndex || 0}_${Date.now()}_${Math.random().toString(36).slice(2)}`;
        }
        if (!Array.isArray(row.images)) {
            row.images = [];
        }
        row.imageCount = row.images.length;
    }

    function handleImageTargetMouseOver(event) {
        const drop = event.target.closest('.order-excel-image-drop');
        if (!drop) return;
        setHoveredImagePasteTarget(getImagePasteTargetFromDrop(drop));
    }

    function handleImageTargetMouseOut(event) {
        const drop = event.target.closest('.order-excel-image-drop');
        if (!drop) return;

        const nextTarget = event.relatedTarget;
        if (nextTarget && drop.contains(nextTarget)) {
            return;
        }

        const target = getImagePasteTargetFromDrop(drop);
        if (isSameImagePasteTarget(state.hoveredImagePasteTarget, target)) {
            state.hoveredImagePasteTarget = null;
            refreshImagePasteTargetUi();
        }
    }

    function handleImageTargetFocusIn(event) {
        const drop = event.target.closest('.order-excel-image-drop');
        if (!drop) return;
        setSelectedImagePasteTarget(getImagePasteTargetFromDrop(drop));
    }

    function handleImageTargetClick(event) {
        const drop = event.target.closest('.order-excel-image-drop');
        if (!drop) return;
        setSelectedImagePasteTarget(getImagePasteTargetFromDrop(drop));
    }

    function handleImageInputChange(event) {
        const input = event.target.closest('.order-excel-image-input');
        if (!input) return;
        const target = getImagePasteTargetFromElement(input);
        if (target) {
            setSelectedImagePasteTarget(target);
        }
        addFilesToRow(Number(input.dataset.groupIndex), Number(input.dataset.rowIndex), input.files);
        input.value = '';
        renderPreview();
    }

    function handleImageDragOver(event) {
        const drop = event.target.closest('.order-excel-image-drop');
        if (!drop) return;
        event.preventDefault();
        drop.classList.add('is-dragover');
        setHoveredImagePasteTarget(getImagePasteTargetFromDrop(drop));
    }

    function handleImageDragLeave(event) {
        const drop = event.target.closest('.order-excel-image-drop');
        if (!drop) return;
        drop.classList.remove('is-dragover');
    }

    function handleImageDrop(event) {
        const drop = event.target.closest('.order-excel-image-drop');
        if (!drop) return;
        event.preventDefault();
        drop.classList.remove('is-dragover');
        const target = getImagePasteTargetFromDrop(drop);
        setSelectedImagePasteTarget(target);
        addFilesToRow(target.groupIndex, target.rowIndex, event.dataTransfer.files);
        renderPreview();
    }

    function handleImagePaste(event) {
        if (state.saving) return;

        const imageFiles = getImageFilesFromClipboard(event.clipboardData);
        if (!imageFiles.length) {
            return;
        }

        const target = resolveImagePasteTarget(event);
        if (!target) {
            return;
        }

        event.preventDefault();
        setSelectedImagePasteTarget(target);
        addFilesToRow(target.groupIndex, target.rowIndex, imageFiles);
        renderPreview();
    }

    function resolveImagePasteTarget(event) {
        const eventTarget = getImagePasteTargetFromElement(event.target);
        if (eventTarget) {
            return eventTarget;
        }

        if (state.hoveredImagePasteTarget
                && getImageDropElement(state.hoveredImagePasteTarget)
                && getRow(state.hoveredImagePasteTarget.groupIndex, state.hoveredImagePasteTarget.rowIndex)) {
            return state.hoveredImagePasteTarget;
        }

        if (state.selectedImagePasteTarget
                && getImageDropElement(state.selectedImagePasteTarget)
                && getRow(state.selectedImagePasteTarget.groupIndex, state.selectedImagePasteTarget.rowIndex)) {
            return state.selectedImagePasteTarget;
        }

        return null;
    }

    function getImageFilesFromClipboard(clipboardData) {
        if (!clipboardData) return [];

        const files = [];
        const items = clipboardData.items ? Array.from(clipboardData.items) : [];
        items.forEach(item => {
            if (!item || item.kind !== 'file' || !String(item.type || '').startsWith('image/')) {
                return;
            }
            const file = item.getAsFile();
            if (file) {
                files.push(normalizeClipboardImageFile(file, files.length));
            }
        });

        if (!files.length && clipboardData.files && clipboardData.files.length) {
            Array.from(clipboardData.files).forEach(file => {
                if (file && String(file.type || '').startsWith('image/')) {
                    files.push(normalizeClipboardImageFile(file, files.length));
                }
            });
        }

        return files;
    }

    function normalizeClipboardImageFile(file, index) {
        const type = file.type || 'image/png';
        const ext = imageExtensionFromMimeType(type);
        const currentName = String(file.name || '').trim();
        const generatedName = `clipboard-image-${formatClipboardTimestamp(new Date())}-${index + 1}.${ext}`;
        const name = currentName && currentName !== 'image.png' ? currentName : generatedName;

        try {
            return new File([file], name, { type, lastModified: Date.now() });
        } catch (e) {
            return file;
        }
    }

    function imageExtensionFromMimeType(type) {
        const normalized = String(type || '').toLowerCase();
        if (normalized.includes('jpeg') || normalized.includes('jpg')) return 'jpg';
        if (normalized.includes('webp')) return 'webp';
        if (normalized.includes('gif')) return 'gif';
        if (normalized.includes('bmp')) return 'bmp';
        return 'png';
    }

    function formatClipboardTimestamp(date) {
        const pad = value => String(value).padStart(2, '0');
        return [
            date.getFullYear(),
            pad(date.getMonth() + 1),
            pad(date.getDate()),
            pad(date.getHours()),
            pad(date.getMinutes()),
            pad(date.getSeconds())
        ].join('');
    }

    function getImagePasteTargetFromElement(element) {
        if (!element || !element.closest) return null;
        const drop = element.closest('.order-excel-image-drop');
        return drop ? getImagePasteTargetFromDrop(drop) : null;
    }

    function getImagePasteTargetFromDrop(drop) {
        if (!drop) return null;
        return {
            groupIndex: Number(drop.dataset.groupIndex),
            rowIndex: Number(drop.dataset.rowIndex)
        };
    }

    function setHoveredImagePasteTarget(target) {
        state.hoveredImagePasteTarget = target;
        refreshImagePasteTargetUi();
    }

    function setSelectedImagePasteTarget(target) {
        state.selectedImagePasteTarget = target;
        refreshImagePasteTargetUi();
    }

    function refreshImagePasteTargetUi() {
        if (!els.previewWrap) return;
        els.previewWrap.querySelectorAll('.order-excel-image-drop.is-paste-hover, .order-excel-image-drop.is-paste-selected').forEach(drop => {
            drop.classList.remove('is-paste-hover', 'is-paste-selected');
        });

        const hoveredDrop = getImageDropElement(state.hoveredImagePasteTarget);
        if (hoveredDrop) {
            hoveredDrop.classList.add('is-paste-hover');
        }

        const selectedDrop = getImageDropElement(state.selectedImagePasteTarget);
        if (selectedDrop) {
            selectedDrop.classList.add('is-paste-selected');
        }
    }

    function getImageDropElement(target) {
        if (!target || !Number.isFinite(target.groupIndex) || !Number.isFinite(target.rowIndex) || !els.previewWrap) {
            return null;
        }
        return els.previewWrap.querySelector(`.order-excel-image-drop[data-group-index="${target.groupIndex}"][data-row-index="${target.rowIndex}"]`);
    }

    function isSameImagePasteTarget(a, b) {
        return Boolean(a && b && Number(a.groupIndex) === Number(b.groupIndex) && Number(a.rowIndex) === Number(b.rowIndex));
    }

    function addFilesToRow(groupIndex, rowIndex, fileList) {
        const row = getRow(groupIndex, rowIndex);
        if (!row || !fileList || !fileList.length) return;
        ensureRowImageState(row, groupIndex, rowIndex);
        Array.from(fileList).forEach(file => {
            if (!file || !file.type || !file.type.startsWith('image/')) return;
            row.images.push({
                id: `${Date.now()}_${Math.random().toString(36).slice(2)}`,
                file,
                name: file.name || 'clipboard-image.png',
                size: file.size,
                url: URL.createObjectURL(file)
            });
        });
        row.imageCount = row.images.length;
    }

    function removeRowImage(groupIndex, rowIndex, imageId) {
        const row = getRow(groupIndex, rowIndex);
        if (!row || !Array.isArray(row.images)) return;
        const image = row.images.find(item => item.id === imageId);
        if (image && image.url) {
            URL.revokeObjectURL(image.url);
        }
        row.images = row.images.filter(item => item.id !== imageId);
        row.imageCount = row.images.length;
    }

    function getRow(groupIndex, rowIndex) {
        const group = state.groups[groupIndex];
        return group && group.rows ? group.rows[rowIndex] : null;
    }

    function buildSaveFormData(payload) {
        const formData = new FormData();
        formData.append('payload', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
        state.groups.forEach(group => {
            (group.rows || []).forEach(row => {
                if (row.saveTarget === false || !row.imageKey || !Array.isArray(row.images)) return;
                row.images.forEach(image => {
                    if (image.file) {
                        formData.append(`images_${row.imageKey}`, image.file, image.name || image.file.name || 'image');
                    }
                });
            });
        });
        return formData;
    }

    function renderSaveIssues(issues) {
        if (!issues || !issues.length) return;

        state.groups.forEach(group => {
            group.issues = (group.issues || []).filter(issue => !issue.fromSave);
            (group.rows || []).forEach(row => {
                row.issues = (row.issues || []).filter(issue => !issue.fromSave);
            });
        });

        issues.forEach(issue => {
            issue.fromSave = true;
            const group = state.groups.find(item => String(item.groupNo) === String(issue.groupNo));
            if (!group) return;

            if (issue.excelRowNumber) {
                const row = (group.rows || []).find(item => String(item.excelRowNumber) === String(issue.excelRowNumber));
                if (row) {
                    row.issues = row.issues || [];
                    row.issues.push(issue);
                    return;
                }
            }

            group.issues = group.issues || [];
            group.issues.push(issue);
        });

        renderPreview();
    }

    function toggleAllGroups(expand) {
        state.groups.forEach(group => {
            group.collapsed = !expand;
        });
        renderPreview();
    }

    function updateSummary() {
        const groupCount = state.groups.length;
        const rowCount = state.groups.reduce((sum, group) => sum + (group.rows || []).length, 0);
        const activeCount = state.groups.reduce((sum, group) => sum + (group.rows || []).filter(row => row.saveTarget !== false).length, 0);
        const total = state.groups.reduce((sum, group) => sum + getGroupTotal(group), 0);
        const errorCount = countErrors();

        els.previewSummary.textContent = groupCount
            ? `Task ${groupCount}건 / Order ${rowCount}건 / 저장대상 ${activeCount}건 / ${formatCurrency(total)}`
            : '미리보기 없음';

        els.footerSummary.textContent = errorCount
            ? `오류 ${errorCount}건 확인 필요 · 저장대상 ${activeCount}건 · 총액 ${formatCurrency(total)}`
            : `저장대상 ${activeCount}건 · 총액 ${formatCurrency(total)}`;
    }

    function getGroupTotal(group) {
        const rowsTotal = (group.rows || []).reduce((sum, row) => {
            if (row.saveTarget === false) return sum;
            return sum + Number(row.totalAmount || 0);
        }, 0);
        return rowsTotal + Number(group.deliveryCost || 0) + Number(group.packingCost || 0);
    }

    function groupHasError(group) {
        if ((group.issues || []).some(issue => issue.level === 'ERROR')) return true;
        return (group.rows || []).some(rowHasError);
    }

    function rowHasError(row) {
        return (row.issues || []).some(issue => issue.level === 'ERROR');
    }

    function hasErrorIssues(issues) {
        return (issues || []).some(issue => issue.level === 'ERROR');
    }

    function countErrors() {
        return state.groups.reduce((count, group) => {
            const groupErrors = (group.issues || []).filter(issue => issue.level === 'ERROR').length;
            const rowErrors = (group.rows || []).reduce((rowCount, row) => {
                return rowCount + (row.issues || []).filter(issue => issue.level === 'ERROR').length;
            }, 0);
            return count + groupErrors + rowErrors;
        }, 0);
    }

    function deliveryRuleCodeFromMethod(method) {
        const normalized = normalizeText(method && method.methodName);
        if (!normalized) return '';
        if (normalized.includes('미배송')) return 'UNDELIVERED';
        if (normalized.includes('직배송') || normalized.includes('매장출고')) return 'DIRECT';
        if (normalized.includes('현장배송') || normalized === '현장') return 'SITE';
        if (normalized.includes('화물')) return 'CARGO';
        if (normalized.includes('방문')) return 'VISIT';
        if (normalized.includes('택배')) return 'PARCEL';
        return '';
    }

    function deliveryMethodRequiresHandler(method) {
        return isHandlerRequiredRuleCode(deliveryRuleCodeFromMethod(method));
    }

    function isHandlerRequiredRuleCode(code) {
        return ['DIRECT', 'SITE', 'CARGO'].includes(String(code || '').toUpperCase());
    }

    function isNoHandlerRuleCode(code) {
        return ['VISIT', 'PARCEL', 'UNDELIVERED'].includes(String(code || '').toUpperCase());
    }

    function normalizeDeliveryHandlerSelections() {
        (state.groups || []).forEach(group => {
            resolveSelectedDeliveryHandler(group);
        });
    }

    /**
     * 담당자 ID가 있으면 ID를 우선 사용하고, ID가 비어 있더라도 서버가 반환한
     * 담당자 이름이 옵션 목록에서 유일하게 일치하면 실제 선택 상태로 복구합니다.
     * 그룹 값이 비어 있고 행에만 담당자가 들어온 경우도 그룹으로 승격합니다.
     */
    function resolveSelectedDeliveryHandler(group) {
        if (!group) return null;

        const handlers = state.options.deliveryHandlers || [];
        let selected = findOptionById(handlers, group.deliveryHandlerMemberId);

        if (!selected) {
            selected = findUniqueOptionByName(handlers, group.deliveryHandlerName);
        }

        if (!selected && Array.isArray(group.rows)) {
            for (const row of group.rows) {
                selected = findOptionById(handlers, row.deliveryHandlerMemberId)
                    || findUniqueOptionByName(handlers, row.deliveryHandlerName);
                if (selected) break;
            }
        }

        if (!selected) {
            return null;
        }

        group.deliveryHandlerMemberId = selected.id;
        group.deliveryHandlerName = selected.name || '';
        applyGroupDeliveryHandlerToRows(group);
        return selected;
    }

    function findUniqueOptionByName(list, name) {
        const normalized = normalizeText(name);
        if (!normalized) return null;

        const matches = (list || []).filter(item => normalizeText(item.name) === normalized);
        return matches.length === 1 ? matches[0] : null;
    }

    function findOptionByName(list, name) {
        const normalized = normalizeText(name);
        if (!normalized) return null;
        return list.find(item => normalizeText(item.name) === normalized) || null;
    }

    function findOptionById(list, id) {
        if (id == null || id === '') return null;
        return list.find(item => String(item.id) === String(id)) || null;
    }

    function normalizeText(value) {
        return String(value || '').trim().replace(/\s+/g, '').toLowerCase();
    }

    function normalizeId(value) {
        if (value == null || value === '') return null;
        const n = Number(value);
        return Number.isFinite(n) && n > 0 ? n : null;
    }

    function parseMoney(value) {
        const raw = String(value || '').replace(/,/g, '').replace(/원/g, '').replace(/[^0-9-]/g, '');
        if (!raw || raw === '-') return 0;
        const n = Number(raw);
        return Number.isFinite(n) ? n : 0;
    }

    function formatNumber(value) {
        const n = Number(value || 0);
        return Number.isFinite(n) ? n.toLocaleString('ko-KR') : '0';
    }

    function formatCurrency(value) {
        return `${formatNumber(value)}원`;
    }

    function showMessage(message, type) {
        els.message.className = `alert alert-${type || 'info'}`;
        els.message.textContent = message;
        els.message.classList.remove('d-none');
    }

    function hideMessage() {
        els.message.classList.add('d-none');
        els.message.textContent = '';
    }

    function setLoading(loading, text) {
        els.previewBtn.disabled = loading;
        els.previewBtn.textContent = loading ? (text || '처리 중...') : '미리보기 생성';
        setPageLoading(loading, text || '엑셀 미리보기 생성 중입니다.', '엑셀 파일을 분석하고 저장 전 미리보기 데이터를 준비하고 있습니다.');
    }

    function setSaving(saving) {
        state.saving = saving;
        els.saveBtn.disabled = saving;
        els.saveBtn.textContent = saving ? '저장 중...' : '수정 내용 저장';
        setPageLoading(saving, '엑셀 발주 등록 중입니다.', 'Task, Order, 이미지, 배송담당자 인덱스를 저장하고 있습니다.');
    }

    function setPageLoading(loading, title, desc) {
        if (!els.loadingOverlay) {
            return;
        }

        if (els.loadingTitle) {
            els.loadingTitle.textContent = title || '작업 처리 중입니다.';
        }
        if (els.loadingDesc) {
            els.loadingDesc.textContent = desc || '작업이 완전히 끝날 때까지 화면을 닫지 말아 주세요.';
        }

        els.loadingOverlay.classList.toggle('d-none', !loading);
        els.loadingOverlay.setAttribute('aria-hidden', loading ? 'false' : 'true');
        document.body.classList.toggle('order-excel-busy', loading);
    }

    async function fetchMultipart(url, formData) {
        const response = await fetch(url, {
            method: 'POST',
            body: formData
        });
        return handleFetchResponse(response);
    }

    async function fetchJson(url, options) {
        const response = await fetch(url, options || {});
        return handleFetchResponse(response);
    }

    async function handleFetchResponse(response) {
        const contentType = response.headers.get('content-type') || '';
        const payload = contentType.includes('application/json') ? await response.json() : await response.text();

        if (!response.ok) {
            const error = new Error(payload && payload.message ? payload.message : '요청 처리 중 오류가 발생했습니다.');
            error.payload = payload;
            throw error;
        }

        return payload;
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }
})();
