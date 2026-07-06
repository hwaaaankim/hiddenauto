/* /administration/assets/js/page/orderExcelUpload/orderExcelUpload.js */
(function () {
    'use strict';

    const API_BASE = '/management/api/order-excel-upload';

    const state = {
        options: {
            deliveryMethods: [],
            productionCategories: [],
            managers: [],
            deliveryHandlers: []
        },
        groups: [],
        saving: false,
        previewLoaded: false
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
    }

    function bindEvents() {
        els.form.addEventListener('submit', handlePreviewSubmit);
        els.saveBtn.addEventListener('click', handleSave);

        els.previewWrap.addEventListener('input', handlePreviewInput);
        els.previewWrap.addEventListener('change', handlePreviewChange);
        els.previewWrap.addEventListener('click', handlePreviewClick);

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
                            ${group.siteDelivery ? '현장배송 묶음' : '직배송 묶음'}
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
                    <label>운임비</label>
                    <input type="text" class="form-control form-control-sm text-end" data-group-field="deliveryCost" data-group-index="${groupIndex}" value="${formatNumber(group.deliveryCost)}">
                </div>
                <div>
                    <label>포장비</label>
                    <input type="text" class="form-control form-control-sm text-end" data-group-field="packingCost" data-group-index="${groupIndex}" value="${formatNumber(group.packingCost)}">
                </div>
            </div>

            <div class="order-excel-address-grid">
                <div class="order-excel-address-box">
                    <div class="order-excel-address-title">기본 배송지</div>
                    <div class="order-excel-address-inputs">
                        <input type="text" placeholder="우편번호" data-group-field="zipCode" data-group-index="${groupIndex}" value="${escapeHtml(group.zipCode || '')}">
                        <input type="text" placeholder="도/시" data-group-field="doName" data-group-index="${groupIndex}" value="${escapeHtml(group.doName || '')}">
                        <input type="text" placeholder="시/군" data-group-field="siName" data-group-index="${groupIndex}" value="${escapeHtml(group.siName || '')}">
                        <input type="text" placeholder="구" data-group-field="guName" data-group-index="${groupIndex}" value="${escapeHtml(group.guName || '')}">
                        <input type="text" placeholder="도로명 주소" data-group-field="roadAddress" data-group-index="${groupIndex}" value="${escapeHtml(group.roadAddress || '')}">
                        <input type="text" placeholder="상세주소" data-group-field="detailAddress" data-group-index="${groupIndex}" value="${escapeHtml(group.detailAddress || '')}">
                    </div>
                </div>
                <div class="order-excel-address-box ${group.siteDelivery ? '' : 'is-disabled'}">
                    <div class="order-excel-address-title">
                        현장 배송지
                        <label class="form-check form-switch mb-0">
                            <input type="checkbox" class="form-check-input" data-group-field="siteDelivery" data-group-index="${groupIndex}" ${group.siteDelivery ? 'checked' : ''}>
                        </label>
                    </div>
                    <div class="order-excel-address-inputs">
                        <input type="text" placeholder="우편번호" data-group-field="siteZipCode" data-group-index="${groupIndex}" value="${escapeHtml(group.siteZipCode || '')}">
                        <input type="text" placeholder="도/시" data-group-field="siteDoName" data-group-index="${groupIndex}" value="${escapeHtml(group.siteDoName || '')}">
                        <input type="text" placeholder="시/군" data-group-field="siteSiName" data-group-index="${groupIndex}" value="${escapeHtml(group.siteSiName || '')}">
                        <input type="text" placeholder="구" data-group-field="siteGuName" data-group-index="${groupIndex}" value="${escapeHtml(group.siteGuName || '')}">
                        <input type="text" placeholder="현장 주소" data-group-field="siteRoadAddress" data-group-index="${groupIndex}" value="${escapeHtml(group.siteRoadAddress || '')}">
                        <input type="text" placeholder="현장 상세주소" data-group-field="siteDetailAddress" data-group-index="${groupIndex}" value="${escapeHtml(group.siteDetailAddress || '')}">
                        <input type="text" placeholder="수령자" data-group-field="siteRecipientName" data-group-index="${groupIndex}" value="${escapeHtml(group.siteRecipientName || '')}">
                        <input type="text" placeholder="수령자 연락처" data-group-field="siteRecipientPhone" data-group-index="${groupIndex}" value="${escapeHtml(group.siteRecipientPhone || '')}">
                    </div>
                </div>
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
                            <th class="order-excel-col-save">저장</th>
                            <th>엑셀행</th>
                            <th>출고일</th>
                            <th>대분류</th>
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
                            <th>규격</th>
                            <th>거울재단</th>
                            <th>배송 담당자</th>
                            <th>관리자 메모</th>
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
                <td class="text-center">
                    <div class="form-check form-switch order-excel-switch-cell">
                        <input type="checkbox" class="form-check-input" data-row-field="saveTarget" data-group-index="${groupIndex}" data-row-index="${rowIndex}" ${row.saveTarget !== false ? 'checked' : ''} title="저장 여부">
                    </div>
                </td>
                <td class="text-center order-excel-row-no">${row.excelRowNumber || ''}</td>
                <td><input type="date" class="form-control form-control-sm" data-row-field="preferredDeliveryDate" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.preferredDeliveryDate || '')}"></td>
                <td>
                    <select class="form-select form-select-sm order-excel-category-select" data-row-field="productionCategoryId" data-group-index="${groupIndex}" data-row-index="${rowIndex}">
                        ${renderProductionCategoryOptions(row.productionCategoryId)}
                    </select>
                    <input type="hidden" data-row-field="categoryName" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.categoryName || '')}">
                </td>
                <td><input type="text" class="form-control form-control-sm" data-row-field="middleCategoryName" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.middleCategoryName || '')}"></td>
                <td><input type="text" class="form-control form-control-sm" data-row-field="originalItemName" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.originalItemName || '')}"></td>
                <td><input type="text" class="form-control form-control-sm order-excel-product-name" data-row-field="itemNameForSave" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.itemNameForSave || '')}"></td>
                <td><input type="text" class="form-control form-control-sm" data-row-field="size" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.size || '')}"></td>
                <td><input type="text" class="form-control form-control-sm" data-row-field="color" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.color || '')}"></td>
                <td><input type="text" class="form-control form-control-sm text-end order-excel-money order-excel-quantity" data-row-field="quantity" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${formatNumber(row.quantity)}" title="0 또는 음수 입력 가능"></td>
                <td><input type="text" class="form-control form-control-sm text-end order-excel-money" data-row-field="productCost" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${formatNumber(row.productCost)}"></td>
                <td><input type="text" class="form-control form-control-sm text-end order-excel-money" data-row-field="supplyPrice" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${formatNumber(row.supplyPrice)}"></td>
                <td><input type="text" class="form-control form-control-sm text-end order-excel-money" data-row-field="vatAmount" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${formatNumber(row.vatAmount)}"></td>
                <td><input type="text" class="form-control form-control-sm text-end order-excel-money" data-row-field="totalAmount" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${formatNumber(row.totalAmount)}"></td>
                <td class="text-center">
                    <div class="form-check form-switch order-excel-switch-cell">
                        <input type="checkbox" class="form-check-input" data-row-field="standard" data-group-index="${groupIndex}" data-row-index="${rowIndex}" ${row.standard ? 'checked' : ''} title="규격 여부">
                    </div>
                </td>
                <td class="text-center">
                    <div class="form-check form-switch order-excel-switch-cell">
                        <input type="checkbox" class="form-check-input" data-row-field="mirrorCuttingProduct" data-group-index="${groupIndex}" data-row-index="${rowIndex}" ${row.mirrorCuttingProduct ? 'checked' : ''} title="거울재단 여부">
                    </div>
                </td>
                <td>
                    <select class="form-select form-select-sm order-excel-delivery-handler-select" data-row-field="deliveryHandlerMemberId" data-group-index="${groupIndex}" data-row-index="${rowIndex}">
                        ${renderDeliveryHandlerOptions(row.deliveryHandlerMemberId)}
                    </select>
                    <input type="hidden" data-row-field="deliveryHandlerName" data-group-index="${groupIndex}" data-row-index="${rowIndex}" value="${escapeHtml(row.deliveryHandlerName || '')}">
                </td>
                <td class="order-excel-admin-memo-cell"><textarea class="form-control form-control-sm order-excel-admin-memo" rows="2" maxlength="200" data-row-field="adminMemo" data-group-index="${groupIndex}" data-row-index="${rowIndex}" placeholder="관리자 메모 입력">${escapeHtml(row.adminMemo || '')}</textarea></td>
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

    function renderDeliveryHandlerOptions(selectedId) {
        const handlers = state.options.deliveryHandlers || [];
        return `<option value="">미지정</option>` + handlers.map(handler => `
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
            if (target.dataset.groupField === 'siteDelivery') {
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
                    state.groups[groupIndex].rows[rowIndex].categoryName = category.name;
                    const hidden = target.closest('tr').querySelector('[data-row-field="categoryName"]');
                    if (hidden) hidden.value = category.name;
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

        if (field === 'managedByName') {
            const match = findOptionByName(state.options.managers || [], value);
            group.managedByMemberId = match ? match.id : null;
            const hidden = target.parentElement.querySelector('[data-group-field="managedByMemberId"]');
            if (hidden) hidden.value = match ? match.id : '';
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

        if (!confirm(`${payload.groups.length}개 Task / ${activeOrderCount}개 Order를 저장하시겠습니까?`)) {
            return;
        }

        setSaving(true);
        try {
            const response = await fetchJson(`${API_BASE}/save`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

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
                companyId: normalizeId(group.companyId),
                requestedByName: group.requestedByName,
                requestedByMemberId: normalizeId(group.requestedByMemberId),
                managedByName: group.managedByName,
                managedByMemberId: normalizeId(group.managedByMemberId),
                deliveryMethodId: normalizeId(group.deliveryMethodId),
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
                    deliveryHandlerName: row.deliveryHandlerName,
                    deliveryHandlerMemberId: normalizeId(row.deliveryHandlerMemberId),
                    productCost: Number(row.productCost || 0),
                    supplyPrice: Number(row.supplyPrice || 0),
                    vatAmount: Number(row.vatAmount || 0),
                    totalAmount: Number(row.totalAmount || 0),
                    standard: Boolean(row.standard),
                    mirrorCuttingProduct: Boolean(row.mirrorCuttingProduct)
                }))
            }))
        };
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
    }

    function setSaving(saving) {
        state.saving = saving;
        els.saveBtn.disabled = saving;
        els.saveBtn.textContent = saving ? '저장 중...' : '수정 내용 저장';
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
