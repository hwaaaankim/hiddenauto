/* /administration/assets/js/page/clientList/clientListBulkImport.js */
(function () {
    'use strict';

    const API_BASE = '/management/clientList/excel-import';

    const state = {
        file: null,
        rows: [],
        previewing: false,
        saving: false,
        previewLoaded: false
    };

    const els = {};
    let modalInstance = null;

    document.addEventListener('DOMContentLoaded', init);

    function init() {
        cacheElements();
        if (!els.openButton || !els.modal) {
            return;
        }
        bindEvents();
        if (window.bootstrap && window.bootstrap.Modal) {
            modalInstance = typeof window.bootstrap.Modal.getOrCreateInstance === 'function'
                ? window.bootstrap.Modal.getOrCreateInstance(els.modal)
                : new window.bootstrap.Modal(els.modal);
        }
    }

    function cacheElements() {
        els.openButton = document.getElementById('clientList-bulk-import-btn');
        els.modal = document.getElementById('clientList-bulk-import-modal');
        els.dropZone = document.getElementById('clientList-bulk-import-drop-zone');
        els.fileInput = document.getElementById('clientList-bulk-import-file');
        els.fileName = document.getElementById('clientList-bulk-import-file-name');
        els.previewButton = document.getElementById('clientList-bulk-import-preview-btn');
        els.saveButton = document.getElementById('clientList-bulk-import-save-btn');
        els.resetButton = document.getElementById('clientList-bulk-import-reset-btn');
        els.message = document.getElementById('clientList-bulk-import-message');
        els.summary = document.getElementById('clientList-bulk-import-summary');
        els.empty = document.getElementById('clientList-bulk-import-empty');
        els.previewWrap = document.getElementById('clientList-bulk-import-preview-wrap');
        els.loading = document.getElementById('clientList-bulk-import-loading');
    }

    function bindEvents() {
        els.openButton.addEventListener('click', openModal);
        els.fileInput.addEventListener('change', handleFileInputChange);
        els.dropZone.addEventListener('click', () => els.fileInput.click());
        els.dropZone.addEventListener('keydown', handleDropZoneKeydown);
        els.dropZone.addEventListener('dragover', handleDragOver);
        els.dropZone.addEventListener('dragleave', handleDragLeave);
        els.dropZone.addEventListener('drop', handleDrop);
        els.previewButton.addEventListener('click', createPreview);
        els.saveButton.addEventListener('click', saveRows);
        els.resetButton.addEventListener('click', resetAll);
        els.previewWrap.addEventListener('input', handlePreviewInput);
        els.previewWrap.addEventListener('change', handlePreviewChange);
        els.previewWrap.addEventListener('click', handlePreviewClick);
        els.modal.addEventListener('hidden.bs.modal', function () {
            if (!state.saving && !state.previewing) {
                hideMessage();
            }
        });
    }

    function openModal() {
        if (modalInstance) {
            modalInstance.show();
            return;
        }
        els.modal.classList.add('show');
        els.modal.style.display = 'block';
    }

    function handleDropZoneKeydown(event) {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            els.fileInput.click();
        }
    }

    function handleDragOver(event) {
        event.preventDefault();
        if (!state.previewing && !state.saving) {
            els.dropZone.classList.add('is-dragover');
        }
    }

    function handleDragLeave(event) {
        if (!els.dropZone.contains(event.relatedTarget)) {
            els.dropZone.classList.remove('is-dragover');
        }
    }

    function handleDrop(event) {
        event.preventDefault();
        els.dropZone.classList.remove('is-dragover');
        if (state.previewing || state.saving) {
            return;
        }
        const files = event.dataTransfer && event.dataTransfer.files;
        if (!files || !files.length) {
            return;
        }
        selectFile(files[0]);
    }

    function handleFileInputChange() {
        const file = els.fileInput.files && els.fileInput.files[0];
        selectFile(file || null);
    }

    function selectFile(file) {
        if (!file) {
            state.file = null;
            els.fileName.textContent = '선택된 파일 없음';
            return;
        }

        const name = String(file.name || '').toLowerCase();
        if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
            showMessage('.xlsx 또는 .xls 파일만 선택할 수 있습니다.', 'warning');
            state.file = null;
            els.fileInput.value = '';
            els.fileName.textContent = '선택된 파일 없음';
            return;
        }

        state.file = file;
        els.fileName.textContent = `${file.name} (${formatFileSize(file.size)})`;
        hideMessage();
    }

    async function createPreview() {
        if (!state.file) {
            showMessage('엑셀 파일을 선택해 주세요.', 'warning');
            return;
        }

        const formData = new FormData();
        formData.append('file', state.file);

        setPreviewing(true);
        hideMessage();

        try {
            const response = await fetchWithCsrf(`${API_BASE}/preview`, {
                method: 'POST',
                body: formData
            });
            const payload = await parseResponse(response);

            state.rows = (payload.rows || []).map(row => ({
                ...row,
                issues: Array.isArray(row.issues) ? row.issues : [],
                _previewBusinessNumber: normalizeBusinessNumber(row.businessNumber)
            }));
            state.previewLoaded = true;
            renderPreview();
            showMessage(payload.message || '미리보기가 생성되었습니다.', payload.saveableCount > 0 ? 'success' : 'warning');
        } catch (error) {
            state.rows = [];
            state.previewLoaded = false;
            renderPreview();
            showMessage(error.message || '미리보기 생성에 실패했습니다.', 'danger');
        } finally {
            setPreviewing(false);
        }
    }

    function renderPreview() {
        if (!state.rows.length) {
            els.empty.classList.remove('d-none');
            els.previewWrap.classList.add('d-none');
            els.previewWrap.innerHTML = '';
            els.saveButton.disabled = true;
            updateSummary();
            return;
        }

        els.empty.classList.add('d-none');
        els.previewWrap.classList.remove('d-none');
        els.previewWrap.innerHTML = state.rows.map(renderRow).join('');
        els.saveButton.disabled = state.saving || getSelectedRows().length === 0;
        updateSummary();
    }

    function renderRow(row, rowIndex) {
        const errorCount = countIssues(row, 'ERROR');
        const warningCount = countIssues(row, 'WARNING');
        const rowClass = errorCount > 0 ? 'has-error' : (warningCount > 0 ? 'has-warning' : 'is-valid');
        const addressStatus = row.addressResolved
            ? `<span class="badge bg-success-subtle text-success">주소확인 ${escapeHtml(row.addressSource || '')}</span>`
            : `<span class="badge bg-warning-subtle text-warning">주소 미확인 · 빈값 저장 가능</span>`;

        return `
            <div class="client-bulk-row ${rowClass}" data-row-index="${rowIndex}">
                <div class="client-bulk-row-header">
                    <div class="d-flex align-items-center gap-3 flex-wrap">
                        <label class="form-check form-switch mb-0">
                            <input class="form-check-input" type="checkbox"
                                   data-row-field="saveTarget" data-row-index="${rowIndex}"
                                   ${row.saveTarget ? 'checked' : ''}>
                            <span class="form-check-label">저장</span>
                        </label>
                        <strong>엑셀 ${row.excelRowNumber}행</strong>
                        <span>${escapeHtml(row.companyName || '-')}</span>
                        <span class="badge bg-light text-dark">아이디: ${escapeHtml(normalizeBusinessNumber(row.businessNumber) || '-')}</span>
                        ${addressStatus}
                    </div>
                    <div class="client-bulk-row-badges">
                        ${errorCount ? `<span class="badge bg-danger">오류 ${errorCount}</span>` : ''}
                        ${warningCount ? `<span class="badge bg-warning text-dark">경고 ${warningCount}</span>` : ''}
                    </div>
                </div>

                ${renderIssues(row.issues || [])}

                <div class="client-bulk-section-title">회사 및 대표 멤버</div>
                <div class="client-bulk-field-grid client-bulk-field-grid-main">
                    ${renderTextField(rowIndex, 'companyName', '대리점명', row.companyName, 'text')}
                    ${renderTextField(rowIndex, 'businessNumber', '사업자등록번호 / 대표 아이디', normalizeBusinessNumber(row.businessNumber), 'text', 'inputmode="numeric" maxlength="10"')}
                    ${renderTextField(rowIndex, 'representativeName', '대표자명', row.representativeName, 'text')}
                    ${renderTextField(rowIndex, 'telephone', '전화', row.telephone, 'text')}
                    ${renderTextField(rowIndex, 'phone', '휴대폰', row.phone, 'text')}
                    ${renderTextField(rowIndex, 'email', '이메일', row.email, 'email')}
                </div>

                <div class="client-bulk-section-title client-bulk-address-title">
                    <span>주소</span>
                    <div class="d-flex gap-2">
                        <button type="button" class="btn btn-sm btn-outline-primary"
                                data-action="search-address" data-row-index="${rowIndex}">다음 주소검색</button>
                        <button type="button" class="btn btn-sm btn-outline-secondary"
                                data-action="clear-address" data-row-index="${rowIndex}">구조화 주소 비우기</button>
                    </div>
                </div>

                <div class="client-bulk-field-grid client-bulk-field-grid-address">
                    <div class="client-bulk-field client-bulk-field-wide">
                        <label>원본주소</label>
                        <textarea class="form-control form-control-sm" rows="2"
                                  data-row-field="originAddress" data-row-index="${rowIndex}">${escapeHtml(row.originAddress || '')}</textarea>
                    </div>
                    ${renderTextField(rowIndex, 'zipCode', '우편번호', row.zipCode, 'text')}
                    ${renderTextField(rowIndex, 'doName', '도/광역시', row.doName, 'text')}
                    ${renderTextField(rowIndex, 'siName', '시/군', row.siName, 'text')}
                    ${renderTextField(rowIndex, 'guName', '구', row.guName, 'text')}
                    <div class="client-bulk-field client-bulk-field-wide">
                        <label>지번주소</label>
                        <input class="form-control form-control-sm" type="text"
                               data-row-field="jibunAddress" data-row-index="${rowIndex}"
                               value="${escapeHtml(row.jibunAddress || '')}">
                    </div>
                    <div class="client-bulk-field client-bulk-field-wide">
                        <label>도로명주소</label>
                        <input class="form-control form-control-sm" type="text"
                               data-row-field="roadAddress" data-row-index="${rowIndex}"
                               value="${escapeHtml(row.roadAddress || '')}">
                    </div>
                    <div class="client-bulk-field client-bulk-field-wide">
                        <label>상세주소</label>
                        <input class="form-control form-control-sm" type="text"
                               data-row-field="detailAddress" data-row-index="${rowIndex}"
                               value="${escapeHtml(row.detailAddress || '')}">
                    </div>
                </div>
            </div>
        `;
    }

    function renderTextField(rowIndex, field, label, value, type, extraAttributes) {
        return `
            <div class="client-bulk-field">
                <label>${escapeHtml(label)}</label>
                <input class="form-control form-control-sm" type="${type || 'text'}"
                       data-row-field="${field}" data-row-index="${rowIndex}"
                       value="${escapeHtml(value || '')}" ${extraAttributes || ''}>
            </div>
        `;
    }

    function renderIssues(issues) {
        if (!issues || !issues.length) {
            return '';
        }
        return `
            <div class="client-bulk-issues">
                ${issues.map(issue => {
                    const cls = String(issue.level || '').toUpperCase() === 'ERROR' ? 'danger' : 'warning';
                    return `<div class="alert alert-${cls} py-2 px-3 mb-2">${escapeHtml(issue.message || '')}</div>`;
                }).join('')}
            </div>
        `;
    }

    function handlePreviewInput(event) {
        const target = event.target;
        if (!target.dataset.rowField) {
            return;
        }
        updateRowField(target, false);
    }

    function handlePreviewChange(event) {
        const target = event.target;
        if (!target.dataset.rowField) {
            return;
        }
        updateRowField(target, true);
        if (target.dataset.rowField === 'saveTarget') {
            applyClientValidation();
            renderPreview();
        }
    }

    function updateRowField(target, finalChange) {
        const rowIndex = Number(target.dataset.rowIndex);
        const row = state.rows[rowIndex];
        if (!row) {
            return;
        }

        const field = target.dataset.rowField;
        let value = target.type === 'checkbox' ? target.checked : target.value;

        if (field === 'businessNumber') {
            value = normalizeBusinessNumber(value).slice(0, 10);
            target.value = value;
            if (value !== row._previewBusinessNumber) {
                row.existingCompanyDuplicate = false;
                row.existingMemberDuplicate = false;
                row.issues = (row.issues || []).filter(issue => ![
                    'COMPANY_ALREADY_EXISTS',
                    'MEMBER_USERNAME_ALREADY_EXISTS',
                    'BUSINESS_NUMBER_INVALID',
                    'DUPLICATE_IN_EXCEL',
                    'DUPLICATE_IN_REQUEST'
                ].includes(issue.code));
            }
        }

        row[field] = value;

        if (['companyName', 'businessNumber', 'representativeName'].includes(field) && finalChange) {
            applyClientValidation();
            renderPreview();
            return;
        }

        updateSummary();
    }

    function handlePreviewClick(event) {
        const button = event.target.closest('[data-action]');
        if (!button) {
            return;
        }

        const rowIndex = Number(button.dataset.rowIndex);
        const action = button.dataset.action;

        if (action === 'search-address') {
            openDaumPostcode(rowIndex);
            return;
        }
        if (action === 'clear-address') {
            clearStructuredAddress(rowIndex);
            renderPreview();
        }
    }

    function openDaumPostcode(rowIndex) {
        const row = state.rows[rowIndex];
        if (!row) {
            return;
        }
        if (!window.daum || !window.daum.Postcode) {
            showMessage('다음 주소검색 스크립트를 불러오지 못했습니다.', 'danger');
            return;
        }

        new window.daum.Postcode({
            oncomplete: function (data) {
                const region = splitDaumRegion(data.sido || '', data.sigungu || '');
                row.zipCode = data.zonecode || '';
                row.doName = region.doName;
                row.siName = region.siName;
                row.guName = region.guName;
                row.roadAddress = data.roadAddress || data.autoRoadAddress || data.address || '';
                row.jibunAddress = data.jibunAddress || data.autoJibunAddress || '';
                row.detailAddress = row.detailAddress || '';
                row.addressResolved = true;
                row.addressSource = 'DAUM';
                row.issues = (row.issues || []).filter(issue => issue.code !== 'ADDRESS_NOT_RESOLVED');
                if (!row.originAddress) {
                    row.originAddress = [row.roadAddress, row.detailAddress].filter(Boolean).join(' ');
                }
                renderPreview();
            }
        }).open();
    }

    function clearStructuredAddress(rowIndex) {
        const row = state.rows[rowIndex];
        if (!row) {
            return;
        }
        row.zipCode = '';
        row.doName = '';
        row.siName = '';
        row.guName = '';
        row.jibunAddress = '';
        row.roadAddress = '';
        row.detailAddress = '';
        row.addressResolved = false;
        row.addressSource = 'NONE';
        row.issues = (row.issues || []).filter(issue => issue.code !== 'ADDRESS_NOT_RESOLVED');
        if (row.originAddress) {
            row.issues.push({
                level: 'WARNING',
                code: 'ADDRESS_NOT_RESOLVED',
                message: '구조화 주소가 비어 있습니다. 요청하신 정책에 따라 이 상태로도 저장할 수 있습니다.',
                excelRowNumber: row.excelRowNumber
            });
        }
    }

    function applyClientValidation() {
        state.rows.forEach(row => {
            row.issues = (row.issues || []).filter(issue => ![
                'COMPANY_NAME_REQUIRED',
                'BUSINESS_NUMBER_INVALID',
                'REPRESENTATIVE_NAME_REQUIRED',
                'DUPLICATE_IN_EXCEL',
                'DUPLICATE_IN_REQUEST'
            ].includes(issue.code));

            if (!String(row.companyName || '').trim()) {
                row.issues.push(makeIssue('ERROR', 'COMPANY_NAME_REQUIRED', '대리점명이 비어 있습니다.', row.excelRowNumber));
            }
            if (normalizeBusinessNumber(row.businessNumber).length !== 10) {
                row.issues.push(makeIssue('ERROR', 'BUSINESS_NUMBER_INVALID', '사업자등록번호는 숫자 10자리여야 합니다.', row.excelRowNumber));
            }
            if (!String(row.representativeName || '').trim()) {
                row.issues.push(makeIssue('ERROR', 'REPRESENTATIVE_NAME_REQUIRED', '대표자명이 비어 있습니다.', row.excelRowNumber));
            }
        });

        const map = new Map();
        state.rows.filter(row => row.saveTarget).forEach(row => {
            const businessNumber = normalizeBusinessNumber(row.businessNumber);
            if (businessNumber.length !== 10) {
                return;
            }
            const list = map.get(businessNumber) || [];
            list.push(row);
            map.set(businessNumber, list);
        });

        map.forEach((rows, businessNumber) => {
            if (rows.length <= 1) {
                return;
            }
            rows.forEach(row => {
                row.issues.push(makeIssue(
                    'ERROR',
                    'DUPLICATE_IN_REQUEST',
                    `미리보기 안에서 사업자등록번호가 중복되었습니다: ${businessNumber}`,
                    row.excelRowNumber
                ));
            });
        });
    }

    async function saveRows() {
        if (!state.previewLoaded || !state.rows.length) {
            showMessage('먼저 미리보기를 생성해 주세요.', 'warning');
            return;
        }

        syncInputsToState();
        applyClientValidation();

        const selectedRows = getSelectedRows();
        if (!selectedRows.length) {
            renderPreview();
            showMessage('저장 대상으로 선택된 행이 없습니다.', 'warning');
            return;
        }

        const selectedWithError = selectedRows.find(row => countIssues(row, 'ERROR') > 0);
        if (selectedWithError) {
            renderPreview();
            showMessage(`엑셀 ${selectedWithError.excelRowNumber}행의 오류를 수정하거나 저장 선택을 해제해 주세요.`, 'danger');
            return;
        }

        if (!window.confirm(`${selectedRows.length}개 대리점과 대표 멤버를 신규 등록하시겠습니까?\n기존 대리점 정보는 변경하지 않습니다.`)) {
            return;
        }

        setSaving(true);
        hideMessage();

        try {
            const response = await fetchWithCsrf(`${API_BASE}/save`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ rows: buildSaveRequestRows(selectedRows) })
            });
            const payload = await parseResponse(response);

            showMessage(
                `${payload.message} 회사 ${payload.savedCompanyCount}건 / 대표 멤버 ${payload.savedMemberCount}건`,
                'success'
            );
            state.rows.forEach(row => {
                row.saveTarget = false;
            });
            renderPreview();
            window.setTimeout(function () {
                if (modalInstance) {
                    modalInstance.hide();
                }
                window.location.reload();
            }, 900);
        } catch (error) {
            if (error.payload && Array.isArray(error.payload.issues)) {
                applyServerSaveIssues(error.payload.issues);
                renderPreview();
            }
            showMessage(error.message || '저장에 실패했습니다.', 'danger');
        } finally {
            setSaving(false);
        }
    }

    /**
     * 화면 상태에는 중복검증 비교용 _previewBusinessNumber 같은
     * 클라이언트 전용 필드가 포함됩니다. 이 객체를 그대로 JSON 직렬화하면
     * 서버 DTO에 없는 속성으로 Jackson 역직렬화 오류가 발생할 수 있으므로,
     * 저장 API에는 서버 계약에 정의된 필드만 명시적으로 전송합니다.
     */
    function buildSaveRequestRows(rows) {
        return (rows || []).map(row => ({
            excelRowNumber: Number(row.excelRowNumber || 0),
            saveTarget: true,
            companyName: String(row.companyName || ''),
            businessNumber: normalizeBusinessNumber(row.businessNumber),
            representativeName: String(row.representativeName || ''),
            telephone: String(row.telephone || ''),
            phone: String(row.phone || ''),
            email: String(row.email || ''),
            originAddress: String(row.originAddress || ''),
            zipCode: String(row.zipCode || ''),
            doName: String(row.doName || ''),
            siName: String(row.siName || ''),
            guName: String(row.guName || ''),
            jibunAddress: String(row.jibunAddress || ''),
            roadAddress: String(row.roadAddress || ''),
            detailAddress: String(row.detailAddress || ''),
            addressResolved: Boolean(row.addressResolved),
            addressSource: String(row.addressSource || ''),
            existingCompanyDuplicate: Boolean(row.existingCompanyDuplicate),
            existingMemberDuplicate: Boolean(row.existingMemberDuplicate)
        }));
    }

    function syncInputsToState() {
        els.previewWrap.querySelectorAll('[data-row-field]').forEach(target => {
            updateRowField(target, false);
        });
    }

    function applyServerSaveIssues(issues) {
        state.rows.forEach(row => {
            row.issues = (row.issues || []).filter(issue => !issue.fromSave);
        });

        issues.forEach(issue => {
            const row = state.rows.find(item => Number(item.excelRowNumber) === Number(issue.excelRowNumber));
            if (row) {
                row.issues.push({ ...issue, fromSave: true });
                row.saveTarget = false;
            }
        });
    }

    function getSelectedRows() {
        return state.rows.filter(row => row.saveTarget);
    }

    function updateSummary() {
        const total = state.rows.length;
        const selected = getSelectedRows().length;
        const errorCount = state.rows.reduce((sum, row) => sum + countIssues(row, 'ERROR'), 0);
        const warningCount = state.rows.reduce((sum, row) => sum + countIssues(row, 'WARNING'), 0);

        els.summary.textContent = total
            ? `전체 ${total}행 · 저장선택 ${selected}행 · 오류 ${errorCount}건 · 경고 ${warningCount}건`
            : '미리보기 없음';
        els.saveButton.disabled = state.saving || selected === 0;
    }

    function resetAll() {
        if (state.saving || state.previewing) {
            return;
        }
        state.file = null;
        state.rows = [];
        state.previewLoaded = false;
        els.fileInput.value = '';
        els.fileName.textContent = '선택된 파일 없음';
        hideMessage();
        renderPreview();
    }

    function setPreviewing(previewing) {
        state.previewing = previewing;
        els.previewButton.disabled = previewing || state.saving;
        els.previewButton.textContent = previewing ? '미리보기 생성 중...' : '미리보기';
        setLoading(previewing, '엑셀을 분석하고 주소를 검색하고 있습니다.');
    }

    function setSaving(saving) {
        state.saving = saving;
        els.saveButton.disabled = saving || getSelectedRows().length === 0;
        els.saveButton.textContent = saving ? '저장 중...' : '저장';
        els.previewButton.disabled = saving || state.previewing;
        els.resetButton.disabled = saving || state.previewing;
        setLoading(saving, '대리점과 대표 멤버를 저장하고 있습니다.');
    }

    function setLoading(loading, text) {
        if (!els.loading) {
            return;
        }
        els.loading.classList.toggle('d-none', !loading);
        const textEl = els.loading.querySelector('[data-loading-text]');
        if (textEl) {
            textEl.textContent = text || '처리 중입니다.';
        }
    }

    async function fetchWithCsrf(url, options) {
        const requestOptions = options || {};
        const headers = new Headers(requestOptions.headers || {});
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
        if (csrfToken && csrfHeader) {
            headers.set(csrfHeader, csrfToken);
        }
        return fetch(url, { ...requestOptions, headers });
    }

    async function parseResponse(response) {
        const contentType = response.headers.get('content-type') || '';
        const payload = contentType.includes('application/json')
            ? await response.json()
            : { message: await response.text() };

        if (!response.ok) {
            const error = new Error(payload.message || '요청 처리 중 오류가 발생했습니다.');
            error.payload = payload;
            throw error;
        }
        return payload;
    }

    function splitDaumRegion(sido, sigungu) {
        const doName = String(sido || '').trim();
        const sigunguText = String(sigungu || '').trim();
        const parts = sigunguText.split(/\s+/).filter(Boolean);

        if (isMetropolitanProvince(doName)) {
            return { doName, siName: '', guName: parts[0] || sigunguText };
        }

        if (parts.length >= 2 && /[시군]$/.test(parts[0])) {
            return { doName, siName: parts[0], guName: parts[1] };
        }

        if (parts.length >= 1) {
            return {
                doName,
                siName: /[시군]$/.test(parts[0]) ? parts[0] : '',
                guName: /구$/.test(parts[0]) ? parts[0] : ''
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

    function countIssues(row, level) {
        return (row.issues || []).filter(issue => String(issue.level || '').toUpperCase() === level).length;
    }

    function makeIssue(level, code, message, excelRowNumber) {
        return { level, code, message, excelRowNumber };
    }

    function normalizeBusinessNumber(value) {
        return String(value || '').replace(/\D/g, '');
    }

    function formatFileSize(bytes) {
        const size = Number(bytes || 0);
        if (size < 1024) return `${size} B`;
        if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
        return `${(size / 1024 / 1024).toFixed(1)} MB`;
    }

    function showMessage(message, type) {
        els.message.className = `alert alert-${type || 'info'} mb-3`;
        els.message.textContent = message;
        els.message.classList.remove('d-none');
    }

    function hideMessage() {
        els.message.classList.add('d-none');
        els.message.textContent = '';
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
