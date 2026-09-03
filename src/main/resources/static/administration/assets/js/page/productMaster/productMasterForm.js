(function (window, document) {
    'use strict';

    const pm = window.HiddenBathProductMaster;
    if (!pm) return;
    const DIRECT_KEY = '__DIRECT__';

    const wrapper = document.getElementById('layout-wrapper');
    const rawProductId = wrapper ? wrapper.dataset.pmProductId : '';
    const productId = rawProductId && rawProductId !== 'null' ? Number(rawProductId) : null;
    const embedded = wrapper && wrapper.dataset.pmEmbedded === 'true';

    const state = {
        groups: [],
        builderOrder: [],
        expandedGroups: new Set(),
        selections: new Map(),
        product: null,
        draggedGroupId: null,
        qrSvg: ''
    };

    const builderList = document.getElementById('pm-builder-list');
    const builderCanvas = document.getElementById('pm-builder-canvas');
    const availableList = document.getElementById('pm-available-group-list');
    const qrModalElement = document.getElementById('pm-product-qr-modal');
    const qrModal = qrModalElement ? new bootstrap.Modal(qrModalElement) : null;
    const mediaModalElement = document.getElementById('pm-product-media-modal');
    const mediaModal = mediaModalElement ? new bootstrap.Modal(mediaModalElement) : null;

    function groupById(groupId) {
        return state.groups.find(function (group) { return group.id === Number(groupId); }) || null;
    }

    function selectionFor(groupId) {
        if (!state.selections.has(groupId)) {
            state.selections.set(groupId, new Map());
        }
        return state.selections.get(groupId);
    }

    async function initialize() {
        pm.showLoading(true, productId ? '제품 정보를 불러오는 중입니다.' : '제품 등록 화면을 준비하는 중입니다.');
        try {
            const groupResponse = await pm.request('/admin/api/product-master/groups?includeInactive=true');
            state.groups = groupResponse.data || [];
            if (productId) {
                const productResponse = await pm.request('/admin/api/product-master/products/' + productId);
                state.product = productResponse.data;
                loadProductIntoState(state.product);
                renderProductMeta();
            }
            renderAll();
        } catch (error) {
            await pm.alert('error', '제품 화면 준비 실패', error.message);
        } finally {
            pm.showLoading(false);
        }
    }

    function loadProductIntoState(product) {
        document.getElementById('pm-product-name').value = product.productName || '';
        document.getElementById('pm-product-status').value = product.status || 'DRAFT';
        document.getElementById('pm-product-safety-stock').value = product.safetyStock || 0;
        document.getElementById('pm-product-description').value = product.description || '';
        document.getElementById('pm-pricing-mode').value = product.pricingMode || 'FIXED';
        document.getElementById('pm-base-supply-price').value = product.baseSupplyPrice || 0;
        document.getElementById('pm-vat-rate').value = product.vatRate == null ? '10.00' : product.vatRate;

        state.builderOrder = [];
        state.expandedGroups.clear();
        state.selections.clear();
        (product.components || []).forEach(function (component) {
            if (!state.builderOrder.includes(component.groupId)) state.builderOrder.push(component.groupId);
            selectionFor(component.groupId).set(component.valueId == null ? DIRECT_KEY : component.valueId, {
                widthMm: component.widthMm,
                depthMm: component.depthMm,
                heightMm: component.heightMm,
                numericValue: component.numericValue,
                textValue: component.textValue
            });
        });
    }

    function renderProductMeta() {
        document.getElementById('pm-product-form-title').textContent = '제품 상세 · 수정';
        document.getElementById('pm-product-form-description').textContent = '카탈로그 코드는 유지하면서 핵심 사양, 팀별 해석, 가격, 재고 원장을 관리합니다.';
        const catalog = document.getElementById('pm-form-catalog-code');
        catalog.textContent = state.product.catalogCode;
        catalog.classList.remove('d-none');
        document.getElementById('pm-initial-stock-section').classList.add('d-none');
        document.getElementById('pm-stock-section').classList.remove('d-none');
        const qrButton = document.getElementById('pm-product-qr-button');
        if (qrButton) qrButton.classList.remove('d-none');
        const saveText = document.querySelector('#pm-save-product-button span');
        if (saveText) saveText.textContent = '변경사항 저장';
        renderStockSection();
    }

    function renderAll() {
        renderAvailableGroups();
        renderBuilder();
        renderPreviews();
        renderPricePreview();
        renderInitialAddonGrid();
        updateSaveStatus();
    }

    function renderAvailableGroups() {
        const groups = state.groups.filter(function (group) {
            return group.active || state.builderOrder.includes(group.id);
        });
        if (!groups.length) {
            availableList.innerHTML = emptyHtml('사용 가능한 그룹이 없습니다. 코드 그룹을 먼저 등록해 주세요.');
            return;
        }
        availableList.innerHTML = groups.map(function (group) {
            const used = state.builderOrder.includes(group.id);
            const addon = group.groupType === 'ADD_ON' ? ' pm-is-addon' : '';
            return '<button type="button" class="pm-available-group' + (used ? ' pm-is-used' : '') + '"'
                + ' draggable="' + (!used) + '" data-pm-available-group-id="' + group.id + '"'
                + (used ? ' disabled' : '') + '>'
                + '<div class="d-flex align-items-center gap-2">'
                + '<span class="pm-drag-handle"><i class="ri-drag-move-2-line"></i></span>'
                + '<div class="pm-group-copy text-start"><div class="pm-group-name">' + pm.escapeHtml(group.managementLabel) + '</div>'
                + '<div class="pm-group-meta"><span class="pm-type-badge' + addon + '">' + pm.escapeHtml(group.groupTypeLabel) + '</span>'
                + '<span>' + pm.escapeHtml(group.selectionModeLabel) + '</span></div></div>'
                + (used ? '<i class="ri-check-line text-success"></i>' : '<i class="ri-add-line text-primary"></i>')
                + '</div></button>';
        }).join('');

        availableList.querySelectorAll('[data-pm-available-group-id]:not(:disabled)').forEach(function (button) {
            const groupId = Number(button.dataset.pmAvailableGroupId);
            button.addEventListener('click', function () { addGroup(groupId); });
            button.addEventListener('dragstart', function (event) {
                event.dataTransfer.setData('text/product-master-group', String(groupId));
                event.dataTransfer.effectAllowed = 'copy';
            });
        });
    }

    function addGroup(groupId) {
        const group = groupById(groupId);
        if (!group || state.builderOrder.includes(groupId)) return;
        state.builderOrder.push(groupId);
        state.expandedGroups.add(groupId);
        const selection = selectionFor(groupId);
        if (group.inputType === 'NUMBER' || group.inputType === 'TEXT') {
            selection.set(DIRECT_KEY, {});
        }
        renderAll();
        const item = builderList.querySelector('[data-pm-builder-group-id="' + groupId + '"]');
        if (item) item.scrollIntoView({behavior: 'smooth', block: 'center'});
    }

    function removeGroup(groupId) {
        const balance = state.product && (state.product.addonBalances || []).find(function (item) {
            return selectionFor(groupId).has(item.valueId) && item.quantity > 0;
        });
        if (balance) {
            pm.alert('warning', '그룹을 제거할 수 없습니다.', balance.managementLabel + ' 포함 재고를 먼저 0으로 조정해 주세요.');
            return;
        }
        state.builderOrder = state.builderOrder.filter(function (id) { return id !== groupId; });
        state.expandedGroups.delete(groupId);
        state.selections.delete(groupId);
        renderAll();
    }

    function renderBuilder() {
        document.getElementById('pm-builder-count').textContent = String(state.builderOrder.length);
        document.getElementById('pm-builder-empty').classList.toggle('d-none', state.builderOrder.length > 0);
        if (!state.builderOrder.length) {
            builderList.innerHTML = '';
            return;
        }

        builderList.innerHTML = state.builderOrder.map(function (groupId) {
            const group = groupById(groupId);
            if (!group) return '';
            const selected = selectionFor(groupId);
            const addon = group.groupType === 'ADD_ON' ? ' pm-is-addon'
                : (group.groupType === 'INTERNAL' ? ' pm-is-internal' : '');
            const expanded = state.expandedGroups.has(groupId);
            const inputType = group.selectionMode === 'MULTIPLE' ? 'checkbox' : 'radio';
            const values = (group.values || []).filter(function (value) {
                return value.active || selected.has(value.id);
            });
            const choiceHtml = values.length ? values.map(function (value) {
                const checked = selected.has(value.id);
                const inactive = !value.active ? ' · 사용중지' : '';
                return '<div class="pm-option-choice">'
                    + '<input type="' + inputType + '" id="pm-choice-' + group.id + '-' + value.id + '"'
                    + ' name="pm-choice-' + group.id + '" data-pm-choice-group-id="' + group.id + '"'
                    + ' data-pm-choice-value-id="' + value.id + '" ' + (checked ? 'checked' : '') + '>'
                    + '<label for="pm-choice-' + group.id + '-' + value.id + '">'
                    + '<span><strong>' + pm.escapeHtml(value.managementLabel) + '</strong>'
                    + '<small class="d-block text-muted">' + pm.escapeHtml(value.valueCode + inactive) + '</small></span>'
                    + (value.priceAdjustment !== 0 ? '<span class="small fw-semibold">' + pm.money(value.priceAdjustment) + '</span>' : '')
                    + '</label>'
                    + valueMediaButton(group, value)
                    + (checked ? dimensionFields(group, value, selected.get(value.id)) : '')
                    + '</div>';
            }).join('') : emptyHtml('이 그룹에 사용할 수 있는 옵션값이 없습니다.');
            const valueHtml = (group.inputType === 'NUMBER' || group.inputType === 'TEXT')
                ? directInputHtml(group, selected.get(DIRECT_KEY) || {})
                : choiceHtml;

            const selectedValues = values.filter(function (value) { return selected.has(value.id); });
            const mediaCount = (group.images || []).length + selectedValues.reduce(function (sum, value) {
                return sum + (value.images || []).length;
            }, 0);

            return '<article class="pm-builder-item' + (expanded ? ' pm-is-expanded' : '') + '" draggable="true" data-pm-builder-group-id="' + group.id + '">'
                + '<div class="pm-builder-head">'
                + '<span class="pm-drag-handle" title="구성 순서 이동"><i class="ri-drag-move-2-line"></i></span>'
                + '<div class="pm-builder-head-copy"><div class="pm-group-name mb-1">' + pm.escapeHtml(group.managementLabel) + '</div>'
                + '<div class="pm-group-meta"><span class="pm-code-badge">' + pm.escapeHtml(group.groupCode) + '</span>'
                + '<span class="pm-type-badge' + addon + '">' + pm.escapeHtml(group.groupTypeLabel) + '</span>'
                + '<span>' + pm.escapeHtml(group.inputTypeLabel || group.selectionModeLabel) + '</span></div>'
                + '<div class="pm-builder-selection-summary">' + selectedSummary(group, selectedValues, selected) + '</div></div>'
                + (mediaCount ? '<button class="pm-builder-media-btn" type="button" data-pm-open-group-media="' + group.id
                    + '" title="그룹과 선택값 이미지 보기"><i class="ri-gallery-line"></i><span>' + mediaCount + '</span></button>' : '')
                + '<button class="pm-builder-toggle" type="button" data-pm-toggle-group-id="' + group.id + '" aria-expanded="' + expanded + '">'
                + '<span>' + (expanded ? '접기' : '선택·변경') + '</span><i class="ri-arrow-down-s-line"></i></button>'
                + '<button class="pm-icon-btn pm-danger" type="button" data-pm-remove-group-id="' + group.id + '" title="구성에서 제거"><i class="ri-close-line"></i></button>'
                + '</div><div class="pm-builder-collapse" aria-hidden="' + (!expanded) + '"' + (expanded ? '' : ' inert') + '>'
                + '<div class="pm-builder-collapse-inner"><div class="pm-builder-body"><div class="pm-option-grid">' + valueHtml
                + '</div></div></div></div></article>';
        }).join('');
        bindBuilderEvents();
    }

    function selectedSummary(group, values, selected) {
        if (group.inputType === 'NUMBER' || group.inputType === 'TEXT') {
            const direct = selected.get(DIRECT_KEY) || {};
            const value = group.inputType === 'NUMBER' ? direct.numericValue : direct.textValue;
            return value === null || value === undefined || value === ''
                ? '<span class="pm-builder-summary-empty">아직 입력하지 않았습니다.</span>'
                : '<span class="pm-builder-summary-chip">' + pm.escapeHtml(String(value) + (group.unitLabel || '')) + '</span>';
        }
        if (!values.length) {
            return '<span class="pm-builder-summary-empty">아직 선택하지 않았습니다.</span>';
        }
        return values.map(function (value) {
            const dimensions = selected.get(value.id) || {};
            const item = {
                group: group,
                value: value,
                widthMm: dimensions.widthMm,
                depthMm: dimensions.depthMm,
                heightMm: dimensions.heightMm
            };
            const detail = dimensionText(item, 'management');
            return '<span class="pm-builder-summary-chip">' + pm.escapeHtml(value.managementLabel + (detail ? ' · ' + detail : '')) + '</span>';
        }).join('');
    }

    function directInputHtml(group, data) {
        const guide = group.customerGuide || group.description || '';
        if (group.inputType === 'NUMBER') {
            const min = group.minimumValue == null ? '' : ' min="' + pm.escapeHtml(group.minimumValue) + '"';
            const max = group.maximumValue == null ? '' : ' max="' + pm.escapeHtml(group.maximumValue) + '"';
            const step = group.stepValue == null ? '1' : group.stepValue;
            return '<div class="pm-direct-input-card"><label class="form-label fw-semibold" for="pm-direct-' + group.id + '">'
                + pm.escapeHtml(group.questionText || group.managementLabel + ' 값을 입력해 주세요.') + '</label>'
                + '<div class="input-group"><input class="form-control" type="number" id="pm-direct-' + group.id + '"'
                + min + max + ' step="' + pm.escapeHtml(step) + '" value="'
                + pm.escapeHtml(data.numericValue == null ? '' : data.numericValue) + '" data-pm-direct-group-id="' + group.id + '" data-pm-direct-kind="number">'
                + (group.unitLabel ? '<span class="input-group-text">' + pm.escapeHtml(group.unitLabel) + '</span>' : '') + '</div>'
                + (guide ? '<p class="pm-form-help mt-2 mb-0">' + pm.escapeHtml(guide) + '</p>' : '') + '</div>';
        }
        return '<div class="pm-direct-input-card"><label class="form-label fw-semibold" for="pm-direct-' + group.id + '">'
            + pm.escapeHtml(group.questionText || group.managementLabel + ' 내용을 입력해 주세요.') + '</label>'
            + '<textarea class="form-control" id="pm-direct-' + group.id + '" rows="3" maxlength="500" data-pm-direct-group-id="'
            + group.id + '" data-pm-direct-kind="text">' + pm.escapeHtml(data.textValue || '') + '</textarea>'
            + (guide ? '<p class="pm-form-help mt-2 mb-0">' + pm.escapeHtml(guide) + '</p>' : '') + '</div>';
    }

    function valueMediaButton(group, value) {
        const count = (value.images || []).length;
        if (!count && !(group.images || []).length) return '';
        return '<button class="pm-option-media-btn" type="button" data-pm-open-value-media="' + value.id
            + '" data-pm-media-group-id="' + group.id + '"><i class="ri-image-line"></i> 이미지 '
            + ((group.images || []).length + count) + '</button>';
    }

    function dimensionFields(group, value, dimensions) {
        if (value.dimensionType === 'NONE') return '';
        if (value.dimensionType === 'CUSTOM') {
            const message = group.systemRole === 'SIZE'
                ? '고정 치수를 저장하지 않습니다. 실제 주문에서 고객이 원하는 사이즈를 입력합니다.'
                : '고정 옵션값을 저장하지 않는 비규격 구성입니다. 실제 주문에서 세부 요청 내용을 입력합니다.';
            return '<div class="pm-size-fields pm-custom-fields"><div class="text-muted small"><i class="ri-magic-line me-1"></i>'
                + pm.escapeHtml(message) + '</div></div>';
        }
        const data = dimensions || {};
        const depthField = value.dimensionType === 'WIDTH_DEPTH_HEIGHT'
            ? sizeInput(group.id, value.id, 'depthMm', 'D', data.depthMm)
            : '';
        return '<div class="pm-size-fields">'
            + sizeInput(group.id, value.id, 'widthMm', 'W', data.widthMm)
            + depthField
            + sizeInput(group.id, value.id, 'heightMm', 'H', data.heightMm)
            + '</div>';
    }

    function sizeInput(groupId, valueId, field, label, value) {
        return '<div><label class="form-label small fw-bold" for="pm-size-' + groupId + '-' + valueId + '-' + field + '">' + label + ' (mm)</label>'
            + '<input class="form-control form-control-sm" type="number" min="1" max="100000"'
            + ' id="pm-size-' + groupId + '-' + valueId + '-' + field + '" value="' + pm.escapeHtml(value == null ? '' : value) + '"'
            + ' data-pm-size-group-id="' + groupId + '" data-pm-size-value-id="' + valueId + '" data-pm-size-field="' + field + '"></div>';
    }

    function bindBuilderEvents() {
        builderList.querySelectorAll('[data-pm-toggle-group-id]').forEach(function (button) {
            button.addEventListener('click', function () {
                const groupId = Number(button.dataset.pmToggleGroupId);
                if (state.expandedGroups.has(groupId)) state.expandedGroups.delete(groupId);
                else state.expandedGroups.add(groupId);
                renderBuilder();
            });
        });
        builderList.querySelectorAll('[data-pm-open-group-media]').forEach(function (button) {
            button.addEventListener('click', function () {
                openMediaModal(Number(button.dataset.pmOpenGroupMedia), null);
            });
        });
        builderList.querySelectorAll('[data-pm-open-value-media]').forEach(function (button) {
            button.addEventListener('click', function () {
                openMediaModal(Number(button.dataset.pmMediaGroupId), Number(button.dataset.pmOpenValueMedia));
            });
        });
        builderList.querySelectorAll('[data-pm-choice-group-id]').forEach(function (input) {
            input.addEventListener('change', function () {
                const groupId = Number(input.dataset.pmChoiceGroupId);
                const valueId = Number(input.dataset.pmChoiceValueId);
                const group = groupById(groupId);
                const selected = selectionFor(groupId);
                if (group.selectionMode === 'SINGLE') selected.clear();
                if (input.checked) {
                    const value = (group.values || []).find(function (item) { return item.id === valueId; });
                    if (value && value.dimensionType === 'CUSTOM') {
                        selected.clear();
                    } else {
                        (group.values || []).filter(function (item) {
                            return item.dimensionType === 'CUSTOM';
                        }).forEach(function (item) { selected.delete(item.id); });
                    }
                    selected.set(valueId, selected.get(valueId) || {});
                }
                else selected.delete(valueId);
                renderAll();
            });
        });
        builderList.querySelectorAll('[data-pm-size-field]').forEach(function (input) {
            input.addEventListener('input', function () {
                const groupId = Number(input.dataset.pmSizeGroupId);
                const valueId = Number(input.dataset.pmSizeValueId);
                const dimensions = selectionFor(groupId).get(valueId) || {};
                const numeric = input.value === '' ? null : Number(input.value);
                dimensions[input.dataset.pmSizeField] = numeric;
                selectionFor(groupId).set(valueId, dimensions);
                renderBuilderSummary(groupId);
                renderPreviews();
                updateSaveStatus();
            });
        });
        builderList.querySelectorAll('[data-pm-direct-group-id]').forEach(function (input) {
            input.addEventListener('input', function () {
                const groupId = Number(input.dataset.pmDirectGroupId);
                const data = selectionFor(groupId).get(DIRECT_KEY) || {};
                if (input.dataset.pmDirectKind === 'number') {
                    data.numericValue = input.value === '' ? null : Number(input.value);
                } else {
                    data.textValue = input.value;
                }
                selectionFor(groupId).set(DIRECT_KEY, data);
                renderBuilderSummary(groupId);
                renderPreviews();
                updateSaveStatus();
            });
        });
        builderList.querySelectorAll('[data-pm-remove-group-id]').forEach(function (button) {
            button.addEventListener('click', function () {
                removeGroup(Number(button.dataset.pmRemoveGroupId));
            });
        });
        builderList.querySelectorAll('[data-pm-builder-group-id]').forEach(function (item) {
            item.addEventListener('dragstart', function (event) {
                if (event.target.closest('input, label, button, select')) {
                    event.preventDefault();
                    return;
                }
                state.draggedGroupId = Number(item.dataset.pmBuilderGroupId);
                item.classList.add('pm-is-dragging');
                event.dataTransfer.effectAllowed = 'move';
            });
            item.addEventListener('dragend', function () {
                item.classList.remove('pm-is-dragging');
                state.draggedGroupId = null;
                syncBuilderOrderFromDom();
            });
            item.addEventListener('dragover', function (event) {
                event.preventDefault();
                const dragged = builderList.querySelector('.pm-is-dragging');
                if (!dragged || dragged === item) return;
                const box = item.getBoundingClientRect();
                if (event.clientY < box.top + box.height / 2) item.before(dragged);
                else item.after(dragged);
            });
        });
    }

    function renderBuilderSummary(groupId) {
        const group = groupById(groupId);
        const card = builderList.querySelector('[data-pm-builder-group-id="' + groupId + '"]');
        if (!group || !card) return;
        const selected = selectionFor(groupId);
        const selectedValues = (group.values || []).filter(function (value) { return selected.has(value.id); });
        const summary = card.querySelector('.pm-builder-selection-summary');
        if (summary) summary.innerHTML = selectedSummary(group, selectedValues, selected);
    }

    function syncBuilderOrderFromDom() {
        const ids = Array.from(builderList.querySelectorAll('[data-pm-builder-group-id]'))
            .map(function (item) { return Number(item.dataset.pmBuilderGroupId); });
        if (ids.length) state.builderOrder = ids;
        renderPreviews();
    }

    builderCanvas.addEventListener('dragover', function (event) {
        if (event.dataTransfer.types.includes('text/product-master-group')) {
            event.preventDefault();
            builderCanvas.classList.add('pm-is-dragover');
        }
    });
    builderCanvas.addEventListener('dragleave', function (event) {
        if (!builderCanvas.contains(event.relatedTarget)) builderCanvas.classList.remove('pm-is-dragover');
    });
    builderCanvas.addEventListener('drop', function (event) {
        const raw = event.dataTransfer.getData('text/product-master-group');
        builderCanvas.classList.remove('pm-is-dragover');
        if (!raw) return;
        event.preventDefault();
        addGroup(Number(raw));
    });

    function flattenedSelections() {
        const result = [];
        let sortOrder = 1;
        state.builderOrder.forEach(function (groupId) {
            const group = groupById(groupId);
            const selected = selectionFor(groupId);
            if (group.inputType === 'NUMBER' || group.inputType === 'TEXT') {
                const direct = selected.get(DIRECT_KEY) || {};
                result.push({
                    group: group,
                    value: null,
                    widthMm: null,
                    depthMm: null,
                    heightMm: null,
                    numericValue: direct.numericValue == null ? null : Number(direct.numericValue),
                    textValue: direct.textValue == null ? null : String(direct.textValue).trim(),
                    sortOrder: sortOrder
                });
                sortOrder += 1;
                return;
            }
            (group.values || []).forEach(function (value) {
                if (!selected.has(value.id)) return;
                const dimensions = selected.get(value.id) || {};
                result.push({
                    group: group,
                    value: value,
                    widthMm: dimensions.widthMm == null ? null : Number(dimensions.widthMm),
                    depthMm: dimensions.depthMm == null ? null : Number(dimensions.depthMm),
                    heightMm: dimensions.heightMm == null ? null : Number(dimensions.heightMm),
                    numericValue: null,
                    textValue: dimensions.textValue == null ? null : dimensions.textValue,
                    sortOrder: sortOrder
                });
                sortOrder += 1;
            });
        });
        return result;
    }

    function renderPreviews() {
        const selections = flattenedSelections();
        renderAudiencePreview('pm-customer-preview', selections, 'customer');
        renderAudiencePreview('pm-management-preview', selections, 'management');
        renderAudiencePreview('pm-production-preview', selections, 'production');
        document.getElementById('pm-product-code-preview').textContent = buildPreviewCode(selections);
        renderInitialAddonGrid();
    }

    function renderAudiencePreview(elementId, selections, audience) {
        const element = document.getElementById(elementId);
        if (!selections.length) {
            element.innerHTML = '<p class="text-muted small mb-0">구성요소를 선택하면 표시됩니다.</p>';
            return;
        }
        element.innerHTML = selections.map(function (item) {
            const groupLabel = item.group[audience + 'Label'];
            const valueLabel = item.value
                ? item.value[audience + 'Label']
                : (item.group.inputType === 'NUMBER'
                    ? (item.numericValue == null ? '미입력' : item.numericValue + (item.group.unitLabel || ''))
                    : (item.textValue || '미입력'));
            const dimension = dimensionText(item, audience);
            return '<div class="pm-preview-line"><span class="pm-preview-key">' + pm.escapeHtml(groupLabel) + '</span>'
                + '<span class="pm-preview-value">' + pm.escapeHtml(valueLabel + (dimension ? ' · ' + dimension : '')) + '</span></div>';
        }).join('');
    }

    function dimensionText(item, audience) {
        if (!item.value) return '';
        const type = item.value.dimensionType;
        if (type === 'NONE') return '';
        if (type === 'CUSTOM') {
            if (audience !== 'customer') return '비규격';
            return item.group.systemRole === 'SIZE' ? '주문 시 원하는 사이즈 입력' : '주문 시 원하는 세부 사양 입력';
        }
        if (audience === 'customer') {
            if (type === 'WIDTH_HEIGHT') return 'W:' + safeDimension(item.widthMm) + ' / H:' + safeDimension(item.heightMm) + ' mm';
            return 'W:' + safeDimension(item.widthMm) + ' / D:' + safeDimension(item.depthMm) + ' / H:' + safeDimension(item.heightMm) + ' mm';
        }
        if (type === 'WIDTH_HEIGHT') return safeDimension(item.widthMm) + '*' + safeDimension(item.heightMm);
        return safeDimension(item.widthMm) + '*' + safeDimension(item.depthMm) + '*' + safeDimension(item.heightMm);
    }

    function safeDimension(value) {
        return value == null || Number.isNaN(Number(value)) ? '?' : Number(value);
    }

    function buildPreviewCode(selections) {
        const core = selections.filter(function (item) { return item.group.groupType === 'CORE'; }).slice();
        if (!core.length) return 'PM1|핵심 구성요소를 선택하면 코드가 표시됩니다.';
        core.sort(function (left, right) {
            return left.group.groupCode.localeCompare(right.group.groupCode);
        });
        return 'PM1|' + core.map(function (item) {
            if (!item.value) {
                if (item.group.inputType === 'NUMBER') return item.group.groupCode + '=N:' + (item.numericValue == null ? '?' : item.numericValue);
                return item.group.groupCode + '=T:직접입력';
            }
            let token = item.group.groupCode + '=' + item.value.valueCode;
            if (item.value.dimensionType === 'WIDTH_HEIGHT') token += '@' + safeDimension(item.widthMm) + 'X' + safeDimension(item.heightMm);
            if (item.value.dimensionType === 'WIDTH_DEPTH_HEIGHT') token += '@' + safeDimension(item.widthMm) + 'X' + safeDimension(item.depthMm) + 'X' + safeDimension(item.heightMm);
            if (item.value.dimensionType === 'CUSTOM') token += '@CUSTOM';
            return token;
        }).join('|');
    }

    function renderPricePreview() {
        const mode = document.getElementById('pm-pricing-mode').value;
        const base = Number(document.getElementById('pm-base-supply-price').value || 0);
        const vatRate = Number(document.getElementById('pm-vat-rate').value || 0);
        const componentPrice = mode === 'BASE_PLUS_COMPONENTS'
            ? flattenedSelections()
                .filter(function (item) { return item.group.groupType === 'CORE'; })
                .reduce(function (sum, item) { return sum + Number(item.value ? item.value.priceAdjustment || 0 : 0); }, 0)
            : 0;
        const supply = Math.max(0, base + componentPrice);
        const vat = Math.round(supply * vatRate / 100);
        document.getElementById('pm-preview-component-price').textContent = pm.money(componentPrice);
        document.getElementById('pm-preview-supply-price').textContent = pm.money(supply);
        document.getElementById('pm-preview-total-price').textContent = pm.money(supply + vat);
        document.getElementById('pm-pricing-mode-help').textContent = mode === 'BASE_PLUS_COMPONENTS'
            ? '기본 공급가에 선택한 제품 정체성 구성요소의 조정액을 합산합니다.'
            : (mode === 'RULE_ENGINE'
                ? '기본 공급가에서 시작해 주문 시 옵션 단가·수량·가격표·구간 규칙을 적용합니다. 규칙·가격 관리에서 미리 검증할 수 있습니다.'
                : '입력한 기본 공급가를 제품 공급가로 사용합니다.');
    }

    function renderInitialAddonGrid() {
        const container = document.getElementById('pm-initial-addon-grid');
        if (!container || productId) return;
        const addons = flattenedSelections().filter(function (item) { return item.group.groupType === 'ADD_ON' && item.value; });
        const previous = new Map();
        container.querySelectorAll('[data-pm-initial-addon-value-id]').forEach(function (input) {
            previous.set(Number(input.dataset.pmInitialAddonValueId), input.value);
        });
        if (!addons.length) {
            container.innerHTML = '<p class="text-muted small mb-0">추가 옵션을 제품 구성에 선택하면 포함 수량 입력란이 표시됩니다.</p>';
            return;
        }
        container.innerHTML = addons.map(function (item) {
            return '<div class="pm-addon-balance"><label class="form-label small fw-semibold" for="pm-initial-addon-' + item.value.id + '">'
                + pm.escapeHtml(item.group.managementLabel + ' · ' + item.value.managementLabel) + '</label>'
                + '<div class="input-group input-group-sm"><input class="form-control text-end" type="number" min="0" max="10000000" value="'
                + pm.escapeHtml(previous.get(item.value.id) || 0) + '" id="pm-initial-addon-' + item.value.id + '" data-pm-initial-addon-value-id="' + item.value.id + '">'
                + '<span class="input-group-text">개</span></div></div>';
        }).join('');
    }

    function payload() {
        const components = flattenedSelections().map(function (item) {
            return {
                groupId: item.group.id,
                valueId: item.value ? item.value.id : null,
                widthMm: item.widthMm,
                depthMm: item.depthMm,
                heightMm: item.heightMm,
                numericValue: item.numericValue,
                textValue: item.textValue,
                sortOrder: item.sortOrder
            };
        });
        const initialAddons = Array.from(document.querySelectorAll('[data-pm-initial-addon-value-id]')).map(function (input) {
            return {valueId: Number(input.dataset.pmInitialAddonValueId), quantityDelta: Number(input.value || 0)};
        });
        return {
            productName: document.getElementById('pm-product-name').value.trim(),
            description: document.getElementById('pm-product-description').value.trim(),
            status: document.getElementById('pm-product-status').value,
            pricingMode: document.getElementById('pm-pricing-mode').value,
            baseSupplyPrice: Number(document.getElementById('pm-base-supply-price').value || 0),
            vatRate: Number(document.getElementById('pm-vat-rate').value || 0),
            safetyStock: Number(document.getElementById('pm-product-safety-stock').value || 0),
            initialStock: productId ? null : Number(document.getElementById('pm-initial-stock').value || 0),
            initialStockReason: productId ? null : document.getElementById('pm-initial-stock-reason').value.trim(),
            initialAddonQuantities: productId ? [] : initialAddons,
            components: components,
            rowVersion: productId && state.product ? state.product.rowVersion : null
        };
    }

    function validateClient(payloadValue) {
        if (!payloadValue.productName) return '제품명을 입력해 주세요.';
        if (!payloadValue.components.length) return '제품 구성요소를 하나 이상 선택해 주세요.';
        const componentPrice = payloadValue.pricingMode === 'BASE_PLUS_COMPONENTS'
            ? flattenedSelections()
                .filter(function (item) { return item.group.groupType === 'CORE'; })
                .reduce(function (sum, item) { return sum + Number(item.value ? item.value.priceAdjustment || 0 : 0); }, 0)
            : 0;
        const supplyPrice = payloadValue.baseSupplyPrice + componentPrice;
        const vatAmount = Math.round(supplyPrice * payloadValue.vatRate / 100);
        if (!Number.isSafeInteger(supplyPrice) || supplyPrice < 0 || supplyPrice > 2000000000) {
            return '구성요소 반영 후 공급가는 0~2,000,000,000원 범위여야 합니다.';
        }
        if (!Number.isSafeInteger(vatAmount) || supplyPrice + vatAmount > 2147483647) {
            return 'VAT 포함 가격이 저장 가능한 범위를 초과합니다.';
        }
        const categories = flattenedSelections().filter(function (item) {
            return item.group.groupType === 'CORE' && item.group.systemRole === 'CATEGORY';
        });
        if (categories.length !== 1) return '핵심 구성의 대분류 값을 정확히 하나 선택해 주세요.';
        for (const item of flattenedSelections()) {
            if (item.group.inputType === 'NUMBER') {
                const number = Number(item.numericValue);
                if (!Number.isFinite(number)) return item.group.managementLabel + ' 숫자값을 입력해 주세요.';
                if (item.group.minimumValue != null && number < Number(item.group.minimumValue)) return item.group.managementLabel + ' 최소값을 확인해 주세요.';
                if (item.group.maximumValue != null && number > Number(item.group.maximumValue)) return item.group.managementLabel + ' 최대값을 확인해 주세요.';
                if (Number(item.group.stepValue || 0) > 0) {
                    const start = item.group.minimumValue == null ? 0 : Number(item.group.minimumValue);
                    const quotient = (number - start) / Number(item.group.stepValue);
                    if (Math.abs(quotient - Math.round(quotient)) > 1e-8) return item.group.managementLabel + ' 입력 간격을 확인해 주세요.';
                }
                continue;
            }
            if (item.group.inputType === 'TEXT') {
                if (!item.textValue) return item.group.managementLabel + ' 내용을 입력해 주세요.';
                continue;
            }
            if (item.value.dimensionType === 'WIDTH_HEIGHT' && (!validDimension(item.widthMm) || !validDimension(item.heightMm))) {
                return item.group.managementLabel + '의 W와 H를 입력해 주세요.';
            }
            if (item.value.dimensionType === 'WIDTH_DEPTH_HEIGHT'
                && (!validDimension(item.widthMm) || !validDimension(item.depthMm) || !validDimension(item.heightMm))) {
                return item.group.managementLabel + '의 W, D, H를 모두 입력해 주세요.';
            }
        }
        if (!productId) {
            for (const addon of payloadValue.initialAddonQuantities) {
                if (addon.quantityDelta > payloadValue.initialStock) return '추가 옵션 포함 수량은 최초 총재고를 넘을 수 없습니다.';
            }
        }
        return null;
    }

    function validDimension(value) {
        const number = Number(value);
        return Number.isFinite(number) && number > 0 && number <= 100000;
    }

    async function saveProduct(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }
        const body = payload();
        const error = validateClient(body);
        if (error) {
            revealFirstIncompleteDimension();
            await pm.alert('warning', '입력 내용을 확인해 주세요.', error);
            return;
        }

        const previewCode = buildPreviewCode(flattenedSelections());
        if (productId && state.product && state.product.productCode !== previewCode) {
            const hasActiveInventory = state.product.currentStock !== 0
                || (state.product.stockMovements || []).some(function (movement) { return !movement.voided; });
            if (hasActiveInventory) {
                await pm.alert(
                    'warning',
                    '핵심 사양을 변경할 수 없습니다.',
                    '재고 또는 유효한 재고 원장이 있습니다. 현재 제품은 유지하고 새 제품으로 등록해 주세요.'
                );
                return;
            }
            const confirmed = await pm.confirm(
                '핵심 제품 사양이 변경됩니다.',
                '긴 제품 코드는 새 사양에 맞게 바뀌지만 카탈로그 코드와 QR 주소는 유지됩니다. 계속하시겠습니까?',
                '사양 변경 저장'
            );
            if (!confirmed) return;
        }

        pm.showLoading(true, productId ? '제품 변경사항을 저장하는 중입니다.' : '제품과 최초재고를 등록하는 중입니다.');
        try {
            const response = await pm.request(
                productId ? '/admin/api/product-master/products/' + productId : '/admin/api/product-master/products',
                {method: productId ? 'PUT' : 'POST', body: body}
            );
            pm.toast(response.message || '제품을 저장했습니다.');
            if (embedded && window.parent !== window) {
                window.parent.postMessage({type: 'pm-product-saved', productId: response.data.id}, window.location.origin);
            }
            if (!productId) {
                window.location.href = '/admin/product-master/products/' + response.data.id;
                return;
            }
            state.product = response.data;
            loadProductIntoState(state.product);
            renderProductMeta();
            renderAll();
        } catch (saveError) {
            await pm.alert('error', '제품 저장 실패', saveError.message);
        } finally {
            pm.showLoading(false);
        }
    }

    function revealFirstIncompleteDimension() {
        const incomplete = flattenedSelections().find(function (item) {
            if (item.value.dimensionType === 'WIDTH_HEIGHT') {
                return !validDimension(item.widthMm) || !validDimension(item.heightMm);
            }
            if (item.value.dimensionType === 'WIDTH_DEPTH_HEIGHT') {
                return !validDimension(item.widthMm) || !validDimension(item.depthMm) || !validDimension(item.heightMm);
            }
            return false;
        });
        if (!incomplete) return;
        state.expandedGroups.add(incomplete.group.id);
        renderBuilder();
        const card = builderList.querySelector('[data-pm-builder-group-id="' + incomplete.group.id + '"]');
        if (card) card.scrollIntoView({behavior: 'smooth', block: 'center'});
    }

    function updateSaveStatus() {
        const count = flattenedSelections().length;
        const code = buildPreviewCode(flattenedSelections());
        document.getElementById('pm-save-status').textContent = count
            ? count + '개 구성값 선택 · ' + (code.includes('?') ? '사이즈 입력 필요' : '저장 준비됨')
            : '대분류를 포함한 핵심 구성요소를 선택해 주세요.';
    }

    function renderStockSection() {
        if (!state.product) return;
        document.getElementById('pm-current-stock').textContent = pm.number(state.product.currentStock);
        document.getElementById('pm-current-stock-status').textContent = state.product.stockStatusLabel + ' · 안전재고 ' + pm.number(state.product.safetyStock);
        const balances = state.product.addonBalances || [];
        const balanceGrid = document.getElementById('pm-addon-balance-grid');
        const deltaGrid = document.getElementById('pm-stock-addon-delta-grid');
        if (!balances.length) {
            balanceGrid.innerHTML = '<p class="text-muted small mb-0">제품 구성에 등록된 추가 옵션이 없습니다.</p>';
            deltaGrid.innerHTML = '';
        } else {
            balanceGrid.innerHTML = balances.map(function (balance) {
                return '<div class="pm-addon-balance"><span class="small text-muted">' + pm.escapeHtml(balance.groupManagementLabel) + '</span>'
                    + '<div class="fw-semibold">' + pm.escapeHtml(balance.managementLabel) + '</div>'
                    + '<strong>' + pm.number(balance.quantity) + '<small class="fs-12 ms-1">개</small></strong>'
                    + (balance.unitPrice ? '<small class="d-block text-muted mt-1">옵션단가 ' + pm.money(balance.unitPrice) + '</small>' : '')
                    + '</div>';
            }).join('');
            deltaGrid.innerHTML = balances.map(function (balance) {
                return '<div class="pm-addon-balance"><label class="form-label small fw-semibold" for="pm-stock-addon-delta-' + balance.valueId + '">'
                    + pm.escapeHtml(balance.managementLabel) + ' 포함 수량 증감</label>'
                    + '<input class="form-control form-control-sm text-end" type="number" min="-10000000" max="10000000" value="0"'
                    + ' id="pm-stock-addon-delta-' + balance.valueId + '" data-pm-stock-addon-value-id="' + balance.valueId + '"></div>';
            }).join('');
        }
        renderStockHistory();
        renderPriceHistory();
    }

    function renderStockHistory() {
        const body = document.getElementById('pm-stock-history-body');
        const movements = state.product.stockMovements || [];
        if (!movements.length) {
            body.innerHTML = '<tr><td colspan="8">' + emptyHtml('재고 이력이 없습니다.') + '</td></tr>';
            return;
        }
        body.innerHTML = movements.map(function (movement) {
            const addons = (movement.addonLines || []).map(function (line) {
                const sign = line.quantityDelta > 0 ? '+' : '';
                return '<span class="pm-addon-tag">' + pm.escapeHtml(line.valueLabel) + ' ' + sign + pm.number(line.quantityDelta) + '</span>';
            }).join('') || '<span class="text-muted">-</span>';
            const sign = movement.quantityDelta > 0 ? '+' : '';
            const voidInfo = movement.voided
                ? '<div class="text-danger small">취소: ' + pm.escapeHtml(movement.voidReason || '-') + ' · ' + pm.escapeHtml(movement.voidedBy || '-') + '</div>'
                : '';
            return '<tr class="' + (movement.voided ? 'pm-voided-row' : '') + '">'
                + '<td>' + pm.escapeHtml(pm.dateTime(movement.createdAt)) + '</td>'
                + '<td><span class="badge bg-soft-primary text-primary">' + pm.escapeHtml(movement.movementTypeLabel) + '</span></td>'
                + '<td class="text-end fw-bold ' + (movement.quantityDelta < 0 ? 'text-danger' : 'text-success') + '">' + sign + pm.number(movement.quantityDelta) + '</td>'
                + '<td class="text-end">' + pm.number(movement.stockBefore) + '</td>'
                + '<td class="text-end">' + pm.number(movement.stockAfter) + '</td>'
                + '<td><div class="pm-addon-tags">' + addons + '</div></td>'
                + '<td><div>' + pm.escapeHtml(movement.reason) + '</div><small class="text-muted">' + pm.escapeHtml(movement.createdBy) + '</small>' + voidInfo + '</td>'
                + '<td class="text-end">' + (movement.voided ? '<span class="text-muted small">삭제됨</span>'
                    : '<button class="pm-icon-btn pm-danger" type="button" data-pm-void-movement-id="' + movement.id + '" title="이력 삭제(취소)"><i class="ri-delete-bin-line"></i></button>') + '</td></tr>';
        }).join('');
        body.querySelectorAll('[data-pm-void-movement-id]').forEach(function (button) {
            button.addEventListener('click', function () { voidStockMovement(Number(button.dataset.pmVoidMovementId)); });
        });
    }

    function renderPriceHistory() {
        const body = document.getElementById('pm-price-history-body');
        const history = state.product.priceHistory || [];
        if (!history.length) {
            body.innerHTML = '<tr><td colspan="8">' + emptyHtml('가격 이력이 없습니다.') + '</td></tr>';
            return;
        }
        body.innerHTML = history.map(function (item) {
            return '<tr><td>' + pm.escapeHtml(pm.dateTime(item.createdAt)) + '</td>'
                + '<td>' + pm.escapeHtml(item.pricingModeLabel) + '</td>'
                + '<td class="text-end">' + pm.money(item.baseSupplyPrice) + '</td>'
                + '<td class="text-end">' + pm.money(item.componentSupplyPrice) + '</td>'
                + '<td class="text-end fw-semibold">' + pm.money(item.supplyPrice) + '</td>'
                + '<td class="text-end">' + pm.money(item.vatAmount) + ' <small class="text-muted">(' + item.vatRate + '%)</small></td>'
                + '<td class="text-end fw-bold">' + pm.money(item.totalPrice) + '</td>'
                + '<td>' + pm.escapeHtml(item.changeReason) + '<small class="d-block text-muted">' + pm.escapeHtml(item.createdBy) + '</small></td></tr>';
        }).join('');
    }

    async function adjustStock(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }
        const type = document.getElementById('pm-stock-movement-type').value;
        let delta = Number(document.getElementById('pm-stock-delta').value || 0);
        if (['INBOUND', 'RETURN'].includes(type)) delta = Math.abs(delta);
        if (['OUTBOUND', 'DAMAGE'].includes(type)) delta = -Math.abs(delta);
        const addonQuantities = Array.from(document.querySelectorAll('[data-pm-stock-addon-value-id]')).map(function (input) {
            let addonDelta = Number(input.value || 0);
            if (['INBOUND', 'RETURN'].includes(type)) addonDelta = Math.abs(addonDelta);
            if (['OUTBOUND', 'DAMAGE'].includes(type)) addonDelta = -Math.abs(addonDelta);
            return {valueId: Number(input.dataset.pmStockAddonValueId), quantityDelta: addonDelta};
        });
        const body = {
            movementType: type,
            quantityDelta: delta,
            reason: document.getElementById('pm-stock-reason').value.trim(),
            addonQuantities: addonQuantities
        };
        pm.showLoading(true, '재고 원장을 반영하는 중입니다.');
        try {
            const response = await pm.request('/admin/api/product-master/products/' + productId + '/stock-movements', {
                method: 'POST',
                body: body
            });
            state.product = response.data;
            document.getElementById('pm-stock-reason').value = '';
            document.getElementById('pm-stock-delta').value = type === 'ADJUSTMENT' ? 0 : 1;
            renderStockSection();
            pm.toast(response.message || '재고를 변경했습니다.');
            notifyParent();
        } catch (error) {
            await pm.alert('error', '재고 변경 실패', error.message);
        } finally {
            pm.showLoading(false);
        }
    }

    async function voidStockMovement(movementId) {
        let reason = '';
        if (window.Swal) {
            const result = await window.Swal.fire({
                icon: 'warning',
                title: '재고 이력을 삭제(취소)하시겠습니까?',
                text: '현재 재고와 추가 옵션 잔액에서 해당 이력의 증감을 되돌립니다.',
                input: 'textarea',
                inputLabel: '삭제 사유',
                inputPlaceholder: '삭제 사유를 입력해 주세요.',
                inputAttributes: {maxlength: '500'},
                showCancelButton: true,
                confirmButtonText: '삭제(취소)',
                cancelButtonText: '닫기',
                confirmButtonColor: '#ef5b5b',
                inputValidator: function (value) { return value && value.trim() ? null : '삭제 사유가 필요합니다.'; }
            });
            if (!result.isConfirmed) return;
            reason = result.value.trim();
        } else {
            reason = window.prompt('삭제 사유를 입력해 주세요.') || '';
            if (!reason.trim()) return;
        }
        pm.showLoading(true, '재고 이력을 취소하는 중입니다.');
        try {
            const response = await pm.request('/admin/api/product-master/products/' + productId + '/stock-movements/' + movementId + '/void', {
                method: 'POST',
                body: {reason: reason}
            });
            state.product = response.data;
            renderStockSection();
            pm.toast(response.message || '재고 이력을 취소했습니다.');
            notifyParent();
        } catch (error) {
            await pm.alert('error', '이력 삭제 불가', error.message);
        } finally {
            pm.showLoading(false);
        }
    }

    function notifyParent() {
        if (embedded && window.parent !== window) {
            window.parent.postMessage({type: 'pm-product-saved', productId: productId}, window.location.origin);
        }
    }

    function normalizeStockDeltaByType() {
        const type = document.getElementById('pm-stock-movement-type').value;
        const input = document.getElementById('pm-stock-delta');
        const magnitude = Math.abs(Number(input.value || 0));
        if (['OUTBOUND', 'DAMAGE'].includes(type)) input.value = magnitude ? -magnitude : -1;
        else if (['INBOUND', 'RETURN'].includes(type)) input.value = magnitude || 1;
        else input.value = 0;
        document.querySelectorAll('[data-pm-stock-addon-value-id]').forEach(function (addonInput) {
            const addonMagnitude = Math.abs(Number(addonInput.value || 0));
            if (['OUTBOUND', 'DAMAGE'].includes(type)) addonInput.value = addonMagnitude ? -addonMagnitude : 0;
            else if (['INBOUND', 'RETURN'].includes(type)) addonInput.value = addonMagnitude;
        });
    }

    function openMediaModal(groupId, valueId) {
        const group = groupById(groupId);
        if (!group || !mediaModal) return;
        const values = valueId == null
            ? (group.values || []).filter(function (value) { return selectionFor(group.id).has(value.id); })
            : (group.values || []).filter(function (value) { return value.id === valueId; });
        const sections = [];
        if ((group.images || []).length) {
            sections.push(mediaCarouselSection(
                'group-' + group.id,
                group.managementLabel + ' · 그룹 안내 이미지',
                '옵션값 전체에 공통으로 적용되는 그룹 이미지입니다.',
                group.images
            ));
        }
        values.forEach(function (value) {
            if (!(value.images || []).length) return;
            sections.push(mediaCarouselSection(
                'value-' + value.id,
                value.managementLabel + ' · 옵션값 이미지',
                value.valueCode + '에 직접 연결된 이미지입니다.',
                value.images
            ));
        });

        document.getElementById('pm-product-media-title').textContent = group.managementLabel + ' 이미지';
        document.getElementById('pm-product-media-subtitle').textContent = valueId == null
            ? '그룹 이미지와 현재 선택한 옵션값 이미지를 구분해 보여드립니다.'
            : '그룹 공통 이미지와 선택한 옵션값 이미지를 구분해 보여드립니다.';
        document.getElementById('pm-product-media-content').innerHTML = sections.join('')
            || emptyHtml('등록된 이미지가 없습니다. 코드 그룹 관리에서 이미지를 추가해 주세요.');
        mediaModal.show();
    }

    function mediaCarouselSection(key, title, description, images) {
        const carouselId = 'pm-media-carousel-' + key;
        const slides = images.map(function (image, index) {
            return '<div class="carousel-item' + (index === 0 ? ' active' : '') + '">'
                + '<div class="pm-media-slide"><img src="' + pm.escapeHtml(image.contentPath) + '" alt="'
                + pm.escapeHtml(title + ' ' + (index + 1)) + '" ' + (index ? 'loading="lazy"' : '') + '>'
                + '<div class="pm-media-caption"><span>' + (index + 1) + ' / ' + images.length + '</span><strong>'
                + pm.escapeHtml(image.originalFilename) + '</strong></div></div></div>';
        }).join('');
        const controls = images.length > 1
            ? '<button class="carousel-control-prev" type="button" data-bs-target="#' + carouselId + '" data-bs-slide="prev">'
                + '<span class="carousel-control-prev-icon" aria-hidden="true"></span><span class="visually-hidden">이전</span></button>'
                + '<button class="carousel-control-next" type="button" data-bs-target="#' + carouselId + '" data-bs-slide="next">'
                + '<span class="carousel-control-next-icon" aria-hidden="true"></span><span class="visually-hidden">다음</span></button>'
            : '';
        const indicators = images.length > 1
            ? '<div class="carousel-indicators">' + images.map(function (image, index) {
                return '<button type="button" data-bs-target="#' + carouselId + '" data-bs-slide-to="' + index + '"'
                    + (index === 0 ? ' class="active" aria-current="true"' : '') + ' aria-label="이미지 ' + (index + 1) + '"></button>';
            }).join('') + '</div>'
            : '';
        return '<section class="pm-media-section"><div class="pm-media-section-head"><div><h6>' + pm.escapeHtml(title)
            + '</h6><p>' + pm.escapeHtml(description) + '</p></div><span class="pm-count-badge">' + images.length + '장</span></div>'
            + '<div id="' + carouselId + '" class="carousel slide pm-media-carousel" data-bs-interval="false">'
            + indicators + '<div class="carousel-inner">' + slides + '</div>' + controls + '</div></section>';
    }

    function openQrModal() {
        if (!state.product || !window.ProductMasterQr) return;
        try {
            const url = window.location.origin + state.product.publicSpecPath;
            document.getElementById('pm-product-qr-name').textContent = state.product.productName;
            document.getElementById('pm-product-qr-code').textContent = state.product.catalogCode;
            document.getElementById('pm-product-qr-url').textContent = url;
            document.getElementById('pm-open-public-spec').href = url;
            state.qrSvg = window.ProductMasterQr.render(document.getElementById('pm-product-qr-stage'), url, {
                dark: '#17243c',
                light: '#ffffff'
            });
            qrModal.show();
        } catch (error) {
            pm.alert('error', 'QR 생성 실패', error.message);
        }
    }

    function emptyHtml(message) {
        return '<div class="pm-empty"><div><span class="pm-empty-icon"><i class="ri-inbox-archive-line"></i></span>'
            + '<strong>' + pm.escapeHtml(message) + '</strong></div></div>';
    }

    document.getElementById('pm-product-form').addEventListener('submit', saveProduct);
    document.getElementById('pm-pricing-mode').addEventListener('change', renderPricePreview);
    document.getElementById('pm-base-supply-price').addEventListener('input', renderPricePreview);
    document.getElementById('pm-vat-rate').addEventListener('input', renderPricePreview);
    document.getElementById('pm-product-name').addEventListener('input', updateSaveStatus);
    document.getElementById('pm-copy-product-code').addEventListener('click', function () {
        pm.copy(buildPreviewCode(flattenedSelections()));
    });

    const stockForm = document.getElementById('pm-stock-adjust-form');
    if (stockForm) stockForm.addEventListener('submit', adjustStock);
    const stockType = document.getElementById('pm-stock-movement-type');
    if (stockType) stockType.addEventListener('change', normalizeStockDeltaByType);
    const qrButton = document.getElementById('pm-product-qr-button');
    if (qrButton) qrButton.addEventListener('click', openQrModal);
    document.getElementById('pm-copy-qr-url').addEventListener('click', function () {
        pm.copy(document.getElementById('pm-product-qr-url').textContent);
    });
    document.getElementById('pm-download-qr').addEventListener('click', function () {
        if (!state.qrSvg || !state.product) return;
        pm.downloadText(state.product.catalogCode + '-QR.svg', state.qrSvg, 'image/svg+xml;charset=utf-8');
    });

    initialize();
})(window, document);
