(function (window, document) {
    'use strict';

    const pm = window.HiddenBathProductMaster;
    const imageUi = window.ProductMasterImages;
    if (!pm || !imageUi) return;

    const roleLabels = {
        CATEGORY: '대분류',
        SERIES: '시리즈',
        SUBCATEGORY: '중분류',
        DOOR_TYPE: '문 타입',
        COLOR: '색상',
        SIZE: '사이즈',
        HANDLE: '손잡이',
        BASIN: '세면대',
        OPTION: '옵션',
        GENERAL: '일반 속성'
    };

    const state = {
        groups: [],
        selectedGroupId: null,
        draggedGroupId: null,
        draggedValueId: null
    };

    const elements = {
        groupList: document.getElementById('pm-group-list'),
        editorEmpty: document.getElementById('pm-group-editor-empty'),
        editorContent: document.getElementById('pm-group-editor-content'),
        editorTitle: document.getElementById('pm-group-editor-title'),
        editorCode: document.getElementById('pm-group-editor-code'),
        valueCount: document.getElementById('pm-group-value-count'),
        valueBody: document.getElementById('pm-value-table-body'),
        groupModalElement: document.getElementById('pm-group-modal'),
        valueModalElement: document.getElementById('pm-value-modal')
    };

    const groupModal = elements.groupModalElement ? new bootstrap.Modal(elements.groupModalElement) : null;
    const valueModal = elements.valueModalElement ? new bootstrap.Modal(elements.valueModalElement) : null;
    const groupImageManager = imageUi.createManager({
        root: document.getElementById('pm-group-image-manager'),
        onDeleted: function (image) {
            const group = selectedGroup();
            if (!group) return;
            group.images = (group.images || []).filter(function (item) { return item.id !== image.id; });
            renderGroupList();
        }
    });
    const newGroupImageManager = imageUi.createManager({
        root: document.getElementById('pm-new-group-image-manager')
    });
    const valueImageManager = imageUi.createManager({
        root: document.getElementById('pm-value-image-manager'),
        onDeleted: function (image) {
            const group = selectedGroup();
            const valueId = Number(document.getElementById('pm-value-id').value || 0);
            const value = group && (group.values || []).find(function (item) { return item.id === valueId; });
            if (!value) return;
            value.images = (value.images || []).filter(function (item) { return item.id !== image.id; });
            renderValues(group);
        }
    });

    function selectedGroup() {
        return state.groups.find(function (group) { return group.id === state.selectedGroupId; }) || null;
    }

    function populateRoleSelects() {
        ['pm-group-system-role', 'pm-new-group-system-role'].forEach(function (id) {
            const select = document.getElementById(id);
            if (!select) return;
            select.innerHTML = Object.entries(roleLabels).map(function (entry) {
                return '<option value="' + entry[0] + '">' + pm.escapeHtml(entry[1]) + '</option>';
            }).join('');
        });
    }

    async function loadGroups(preferredId) {
        pm.showLoading(true, '옵션 그룹을 불러오는 중입니다.');
        try {
            const response = await pm.request('/admin/api/product-master/groups?includeInactive=true');
            state.groups = response.data || [];
            const candidate = preferredId != null ? Number(preferredId) : state.selectedGroupId;
            state.selectedGroupId = state.groups.some(function (group) { return group.id === candidate; })
                ? candidate
                : (state.groups.length ? state.groups[0].id : null);
            renderGroupList();
            renderEditor();
        } catch (error) {
            await pm.alert('error', '그룹 조회 실패', error.message);
            elements.groupList.innerHTML = emptyHtml('옵션 그룹을 불러오지 못했습니다.');
        } finally {
            pm.showLoading(false);
        }
    }

    function renderGroupList() {
        if (!state.groups.length) {
            elements.groupList.innerHTML = emptyHtml('등록된 옵션 그룹이 없습니다.');
            return;
        }
        elements.groupList.innerHTML = state.groups.map(function (group) {
            const selected = group.id === state.selectedGroupId ? ' pm-is-selected' : '';
            const inactive = group.active ? '' : ' pm-inactive';
            const addon = group.groupType === 'ADD_ON' ? ' pm-is-addon'
                : (group.groupType === 'INTERNAL' ? ' pm-is-internal' : '');
            return '<article class="pm-group-item' + selected + inactive + '" draggable="true" data-pm-group-id="' + group.id + '">'
                + '<span class="pm-drag-handle" title="순서 이동"><i class="ri-drag-move-2-line"></i></span>'
                + '<div class="pm-group-copy">'
                + '<div class="pm-group-name">' + pm.escapeHtml(group.managementLabel) + '</div>'
                + '<div class="pm-group-meta">'
                + '<span class="pm-type-badge' + addon + '">' + pm.escapeHtml(group.groupTypeLabel) + '</span>'
                + '<span>' + pm.escapeHtml(group.systemRoleLabel) + '</span>'
                + '<span>·</span><span>' + pm.escapeHtml(group.inputTypeLabel || '선택형') + '</span>'
                + (group.inputType === 'CHOICE' || group.inputType === 'DIMENSION' ? '<span>·</span><span>' + (group.values || []).length + '개</span>' : '')
                + ((group.images || []).length ? '<span class="pm-image-inline-count"><i class="ri-image-line"></i>' + group.images.length + '</span>' : '')
                + (!group.active ? '<span class="text-danger">사용중지</span>' : '')
                + '</div></div>'
                + '<i class="ri-arrow-right-s-line text-muted"></i>'
                + '</article>';
        }).join('');
        bindGroupListEvents();
    }

    function renderEditor() {
        const group = selectedGroup();
        if (!group) {
            elements.editorEmpty.classList.remove('d-none');
            elements.editorContent.classList.add('d-none');
            elements.editorTitle.textContent = '그룹을 선택해 주세요';
            elements.editorCode.textContent = '왼쪽 목록에서 수정할 그룹을 선택합니다.';
            elements.valueCount.textContent = '0';
            groupImageManager.reset([]);
            return;
        }

        elements.editorEmpty.classList.add('d-none');
        elements.editorContent.classList.remove('d-none');
        elements.editorTitle.textContent = group.managementLabel;
        elements.editorCode.textContent = group.groupCode + ' · ' + group.groupTypeLabel + ' · ' + group.selectionModeLabel;
        elements.valueCount.textContent = String((group.values || []).length);
        document.getElementById('pm-group-id').value = group.id;
        document.getElementById('pm-group-customer-label').value = group.customerLabel || '';
        document.getElementById('pm-group-management-label').value = group.managementLabel || '';
        document.getElementById('pm-group-production-label').value = group.productionLabel || '';
        document.getElementById('pm-group-type').value = group.groupType;
        document.getElementById('pm-group-selection-mode').value = group.selectionMode;
        document.getElementById('pm-group-system-role').value = group.systemRole;
        document.getElementById('pm-group-input-type').value = group.inputType || (group.systemRole === 'SIZE' ? 'DIMENSION' : 'CHOICE');
        document.getElementById('pm-group-question-text').value = group.questionText || '';
        document.getElementById('pm-group-customer-guide').value = group.customerGuide || '';
        document.getElementById('pm-group-required-by-default').checked = Boolean(group.requiredByDefault);
        document.getElementById('pm-group-unit-label').value = group.unitLabel || '';
        document.getElementById('pm-group-minimum-value').value = group.minimumValue == null ? '' : group.minimumValue;
        document.getElementById('pm-group-maximum-value').value = group.maximumValue == null ? '' : group.maximumValue;
        document.getElementById('pm-group-step-value').value = group.stepValue == null ? '1' : group.stepValue;
        document.getElementById('pm-group-custom-dimension-type').value = group.customDimensionType || 'WIDTH_DEPTH_HEIGHT';
        document.getElementById('pm-group-description').value = group.description || '';
        document.getElementById('pm-group-active').checked = Boolean(group.active);
        groupImageManager.reset(group.images || []);
        configureGroupInput('pm-group-', false);
        renderValues(group);
    }

    function renderValues(group) {
        const supportsValues = group.inputType === 'CHOICE' || group.inputType === 'DIMENSION' || !group.inputType;
        const addButton = document.getElementById('pm-add-value-button');
        addButton.disabled = !supportsValues;
        addButton.title = supportsValues ? '옵션값 추가' : '숫자형·문자형은 그룹에 직접 값을 입력하므로 옵션값을 사용하지 않습니다.';
        if (!supportsValues) {
            elements.valueBody.innerHTML = '<tr><td colspan="10">' + emptyHtml(group.inputTypeLabel + ' 그룹은 별도 옵션값 없이 제품·주문에서 직접 입력합니다.') + '</td></tr>';
            return;
        }
        const values = group.values || [];
        if (!values.length) {
            elements.valueBody.innerHTML = '<tr><td colspan="10">' + emptyHtml('등록된 옵션값이 없습니다.') + '</td></tr>';
            return;
        }
        elements.valueBody.innerHTML = values.map(function (value) {
            const inactive = value.active ? '' : ' pm-inactive';
            return '<tr class="pm-value-row' + inactive + '" draggable="true" data-pm-value-id="' + value.id + '">'
                + '<td><span class="pm-drag-handle" title="순서 이동"><i class="ri-drag-move-2-line"></i></span></td>'
                + '<td><span class="pm-code-badge">' + pm.escapeHtml(value.valueCode) + '</span></td>'
                + '<td>' + imageCountHtml(value.images || []) + '</td>'
                + '<td><strong>' + pm.escapeHtml(value.customerLabel) + '</strong></td>'
                + '<td>' + pm.escapeHtml(value.managementLabel) + '</td>'
                + '<td>' + pm.escapeHtml(value.productionLabel) + '</td>'
                + '<td>' + pm.escapeHtml(value.dimensionTypeLabel) + '</td>'
                + '<td class="text-end">' + pm.money(value.priceAdjustment) + '</td>'
                + '<td class="text-center">'
                + '<span class="badge ' + (value.active ? 'bg-soft-success text-success' : 'bg-soft-secondary text-secondary') + '">'
                + (value.active ? '사용중' : '중지') + '</span></td>'
                + '<td><div class="pm-value-actions">'
                + '<button type="button" class="pm-icon-btn" data-pm-action="edit-value" data-pm-value-id="' + value.id + '" title="수정"><i class="ri-edit-line"></i></button>'
                + '<button type="button" class="pm-icon-btn pm-danger" data-pm-action="delete-value" data-pm-value-id="' + value.id + '" title="삭제"><i class="ri-delete-bin-line"></i></button>'
                + '</div></td></tr>';
        }).join('');
        bindValueEvents();
    }

    function bindGroupListEvents() {
        elements.groupList.querySelectorAll('[data-pm-group-id]').forEach(function (item) {
            item.addEventListener('click', function (event) {
                if (event.target.closest('.pm-drag-handle')) return;
                state.selectedGroupId = Number(item.dataset.pmGroupId);
                renderGroupList();
                renderEditor();
            });
            item.addEventListener('dragstart', function () {
                state.draggedGroupId = Number(item.dataset.pmGroupId);
                item.classList.add('pm-is-dragging');
            });
            item.addEventListener('dragend', function () {
                item.classList.remove('pm-is-dragging');
                state.draggedGroupId = null;
            });
            item.addEventListener('dragover', function (event) {
                event.preventDefault();
                const dragged = elements.groupList.querySelector('.pm-is-dragging');
                if (!dragged || dragged === item) return;
                const box = item.getBoundingClientRect();
                if (event.clientY < box.top + box.height / 2) item.before(dragged);
                else item.after(dragged);
            });
        });
        elements.groupList.ondrop = saveGroupOrder;
    }

    async function saveGroupOrder(event) {
        event.preventDefault();
        const ids = Array.from(elements.groupList.querySelectorAll('[data-pm-group-id]'))
            .map(function (item) { return Number(item.dataset.pmGroupId); });
        if (!ids.length) return;
        try {
            await pm.request('/admin/api/product-master/groups/reorder', {
                method: 'POST',
                body: {ids: ids}
            });
            await loadGroups(state.selectedGroupId);
            pm.toast('그룹 순서를 저장했습니다.');
        } catch (error) {
            await pm.alert('error', '순서 저장 실패', error.message);
            await loadGroups(state.selectedGroupId);
        }
    }

    function bindValueEvents() {
        elements.valueBody.querySelectorAll('[data-pm-action="edit-value"]').forEach(function (button) {
            button.addEventListener('click', function () {
                openValueModal(Number(button.dataset.pmValueId));
            });
        });
        elements.valueBody.querySelectorAll('[data-pm-action="delete-value"]').forEach(function (button) {
            button.addEventListener('click', function () {
                deleteValue(Number(button.dataset.pmValueId));
            });
        });
        elements.valueBody.querySelectorAll('[data-pm-value-id]').forEach(function (row) {
            row.addEventListener('dragstart', function (event) {
                if (event.target.closest('button')) {
                    event.preventDefault();
                    return;
                }
                state.draggedValueId = Number(row.dataset.pmValueId);
                row.classList.add('pm-is-dragging');
            });
            row.addEventListener('dragend', function () {
                row.classList.remove('pm-is-dragging');
                state.draggedValueId = null;
            });
            row.addEventListener('dragover', function (event) {
                event.preventDefault();
                const dragged = elements.valueBody.querySelector('.pm-is-dragging');
                if (!dragged || dragged === row) return;
                const box = row.getBoundingClientRect();
                if (event.clientY < box.top + box.height / 2) row.before(dragged);
                else row.after(dragged);
            });
        });
        elements.valueBody.ondrop = saveValueOrder;
    }

    async function saveValueOrder(event) {
        event.preventDefault();
        const group = selectedGroup();
        if (!group) return;
        const ids = Array.from(elements.valueBody.querySelectorAll('[data-pm-value-id]'))
            .map(function (row) { return Number(row.dataset.pmValueId); });
        if (!ids.length) return;
        try {
            await pm.request('/admin/api/product-master/groups/' + group.id + '/values/reorder', {
                method: 'POST',
                body: {ids: ids}
            });
            await loadGroups(group.id);
            pm.toast('옵션값 순서를 저장했습니다.');
        } catch (error) {
            await pm.alert('error', '순서 저장 실패', error.message);
            await loadGroups(group.id);
        }
    }

    function groupPayload(prefix) {
        const inputType = document.getElementById(prefix + 'input-type').value;
        return {
            customerLabel: document.getElementById(prefix + 'customer-label').value.trim(),
            managementLabel: document.getElementById(prefix + 'management-label').value.trim(),
            productionLabel: document.getElementById(prefix + 'production-label').value.trim(),
            groupType: document.getElementById(prefix + 'type').value,
            selectionMode: document.getElementById(prefix + 'selection-mode').value,
            systemRole: document.getElementById(prefix + 'system-role').value,
            inputType: inputType,
            questionText: document.getElementById(prefix + 'question-text').value.trim(),
            customerGuide: document.getElementById(prefix + 'customer-guide').value.trim(),
            requiredByDefault: document.getElementById(prefix + 'required-by-default').checked,
            unitLabel: inputType === 'NUMBER' ? document.getElementById(prefix + 'unit-label').value.trim() : null,
            minimumValue: inputType === 'NUMBER' ? numberOrNull(document.getElementById(prefix + 'minimum-value').value) : null,
            maximumValue: inputType === 'NUMBER' ? numberOrNull(document.getElementById(prefix + 'maximum-value').value) : null,
            stepValue: inputType === 'NUMBER' ? numberOrNull(document.getElementById(prefix + 'step-value').value) : null,
            customDimensionType: inputType === 'DIMENSION' ? document.getElementById(prefix + 'custom-dimension-type').value : null,
            description: document.getElementById(prefix + 'description').value.trim(),
            active: prefix === 'pm-new-group-' ? true : document.getElementById('pm-group-active').checked,
            rowVersion: prefix === 'pm-group-' && selectedGroup() ? selectedGroup().rowVersion : null
        };
    }

    function numberOrNull(value) {
        return value === '' || value == null ? null : Number(value);
    }

    function configureGroupInput(prefix, userChanged) {
        const role = document.getElementById(prefix + 'system-role').value;
        const typeSelect = document.getElementById(prefix + 'input-type');
        const groupType = document.getElementById(prefix + 'type');
        const selection = document.getElementById(prefix + 'selection-mode');
        if (role === 'SIZE') {
            typeSelect.value = 'DIMENSION';
            groupType.value = 'CORE';
            selection.value = 'SINGLE';
        }
        if (groupType.value === 'ADD_ON') {
            typeSelect.value = 'CHOICE';
        }
        if (typeSelect.value !== 'CHOICE') selection.value = 'SINGLE';
        const type = typeSelect.value;
        document.querySelectorAll('[data-pm-group-number-config="' + prefix + '"]').forEach(function (element) {
            element.classList.toggle('d-none', type !== 'NUMBER');
        });
        document.querySelectorAll('[data-pm-group-dimension-config="' + prefix + '"]').forEach(function (element) {
            element.classList.toggle('d-none', type !== 'DIMENSION');
        });
        const note = document.getElementById(prefix + 'input-note');
        if (note) {
            const messages = {
                CHOICE: '등록된 옵션값을 하나 또는 여러 개 선택합니다. 모든 그룹에 비규격 옵션값을 추가할 수 있습니다.',
                NUMBER: '문 수량·손잡이 수량처럼 단위와 범위를 검증하며 가격의 곱셈 기준으로 사용할 수 있습니다.',
                TEXT: '자유 문구는 내부·외부 구성에만 사용하며 제품 정체성 코드는 비규격 옵션값으로 구성해 주세요.',
                DIMENSION: '각 옵션값에서 2차원·3차원·비규격을 선택합니다. 비규격은 제품 등록 시 고정 치수를 입력하지 않습니다.'
            };
            note.querySelector('span').textContent = messages[type];
        }
        if (userChanged && prefix === 'pm-group-') renderValues(Object.assign({}, selectedGroup(), {inputType: type, inputTypeLabel: typeSelect.options[typeSelect.selectedIndex].text}));
    }

    function imageCountHtml(images) {
        if (!images.length) return '<span class="text-muted small">없음</span>';
        return '<span class="pm-image-count-chip"><img src="' + pm.escapeHtml(images[0].contentPath)
            + '" alt="" loading="lazy"><strong>' + images.length + '</strong></span>';
    }

    function imageManagerError(manager) {
        if (manager.isAddMode() && manager.totalCount() < 1) {
            return '이미지 추가를 선택하셨습니다. 파일을 한 장 이상 선택하거나 “이미지 없음”으로 바꿔 주세요.';
        }
        return null;
    }

    function requestWithImages(url, method, body, manager) {
        const files = manager.queuedFiles();
        if (!files.length) {
            return pm.request(url, {method: method, body: body});
        }
        const formData = new FormData();
        formData.append('request', new Blob([JSON.stringify(body)], {type: 'application/json'}));
        imageUi.appendFiles(formData, files, 'images');
        return pm.request(url, {method: method, body: formData});
    }

    async function saveCurrentGroup(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }
        const group = selectedGroup();
        if (!group) return;
        const imageError = imageManagerError(groupImageManager);
        if (imageError) {
            await pm.alert('warning', '그룹 이미지를 확인해 주세요.', imageError);
            return;
        }
        pm.showLoading(true, '옵션 그룹을 저장하는 중입니다.');
        try {
            const response = await requestWithImages(
                '/admin/api/product-master/groups/' + group.id,
                'PUT',
                groupPayload('pm-group-'),
                groupImageManager
            );
            await loadGroups(group.id);
            pm.toast(response.message || '옵션 그룹을 저장했습니다.');
        } catch (error) {
            await pm.alert('error', '그룹 저장 실패', error.message);
        } finally {
            pm.showLoading(false);
        }
    }

    async function createGroup(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }
        const imageError = imageManagerError(newGroupImageManager);
        if (imageError) {
            await pm.alert('warning', '그룹 이미지를 확인해 주세요.', imageError);
            return;
        }
        pm.showLoading(true, '새 옵션 그룹을 등록하는 중입니다.');
        try {
            const response = await requestWithImages(
                '/admin/api/product-master/groups',
                'POST',
                groupPayload('pm-new-group-'),
                newGroupImageManager
            );
            groupModal.hide();
            form.reset();
            await loadGroups(response.data.id);
            pm.toast(response.message || '옵션 그룹을 등록했습니다.');
        } catch (error) {
            await pm.alert('error', '그룹 등록 실패', error.message);
        } finally {
            pm.showLoading(false);
        }
    }

    async function deleteGroup() {
        const group = selectedGroup();
        if (!group) return;
        const confirmed = await pm.confirm(
            '옵션 그룹을 삭제하시겠습니까?',
            '제품이나 재고 이력이 사용하는 그룹은 삭제되지 않습니다. 미사용 그룹의 옵션값도 함께 삭제됩니다.',
            '그룹 삭제'
        );
        if (!confirmed) return;
        pm.showLoading(true, '옵션 그룹을 삭제하는 중입니다.');
        try {
            const response = await pm.request('/admin/api/product-master/groups/' + group.id, {method: 'DELETE'});
            state.selectedGroupId = null;
            await loadGroups();
            pm.toast(response.message || '옵션 그룹을 삭제했습니다.');
        } catch (error) {
            await pm.alert('error', '그룹 삭제 불가', error.message);
        } finally {
            pm.showLoading(false);
        }
    }

    function openValueModal(valueId) {
        const group = selectedGroup();
        if (!group) return;
        const value = valueId == null
            ? null
            : (group.values || []).find(function (item) { return item.id === valueId; });
        document.getElementById('pm-value-id').value = value ? value.id : '';
        document.getElementById('pm-value-customer-label').value = value ? value.customerLabel : '';
        document.getElementById('pm-value-management-label').value = value ? value.managementLabel : '';
        document.getElementById('pm-value-production-label').value = value ? value.productionLabel : '';
        document.getElementById('pm-value-dimension-type').value = value ? value.dimensionType : 'NONE';
        document.getElementById('pm-value-price-adjustment').value = value ? value.priceAdjustment : 0;
        document.getElementById('pm-value-description').value = value && value.description ? value.description : '';
        document.getElementById('pm-value-customer-guide').value = value && value.customerGuide ? value.customerGuide : '';
        document.getElementById('pm-value-active').checked = value ? Boolean(value.active) : true;
        document.getElementById('pm-value-modal-title').textContent = value ? '옵션값 수정' : '옵션값 등록';
        document.getElementById('pm-value-modal-subtitle').textContent = group.managementLabel + ' 그룹의 값을 관리합니다.';
        configureValueType(group, value);
        valueImageManager.reset(value ? (value.images || []) : []);
        valueModal.show();
    }

    function configureValueType(group, value) {
        const select = document.getElementById('pm-value-dimension-type');
        const help = document.getElementById('pm-value-dimension-help');
        const isSize = group.systemRole === 'SIZE';
        const options = isSize
            ? [
                ['WIDTH_HEIGHT', '2차원 · W-H 고정 입력'],
                ['WIDTH_DEPTH_HEIGHT', '3차원 · W-D-H 고정 입력'],
                ['CUSTOM', '비규격 · 주문 시 사이즈 입력']
            ]
            : [
                ['NONE', '일반 고정값'],
                ['CUSTOM', '비규격 · 주문 시 세부 사양 입력']
            ];
        select.innerHTML = options.map(function (option) {
            return '<option value="' + option[0] + '">' + pm.escapeHtml(option[1]) + '</option>';
        }).join('');
        const requested = value ? value.dimensionType : (isSize ? 'WIDTH_HEIGHT' : 'NONE');
        select.value = options.some(function (option) { return option[0] === requested; }) ? requested : options[0][0];
        help.textContent = isSize
            ? '2차원은 W·H, 3차원은 W·D·H를 제품 등록에서 반드시 입력하며 비규격은 고정 치수를 입력하지 않습니다.'
            : '비규격은 이 그룹의 값을 미리 고정하지 않고 실제 주문에서 세부 요청 내용을 입력하게 합니다.';
        document.getElementById('pm-value-dimension-wrap').classList.remove('opacity-50');
    }

    function valuePayload() {
        const group = selectedGroup();
        const valueId = Number(document.getElementById('pm-value-id').value || 0);
        const value = group && valueId
            ? (group.values || []).find(function (item) { return item.id === valueId; })
            : null;
        return {
            customerLabel: document.getElementById('pm-value-customer-label').value.trim(),
            managementLabel: document.getElementById('pm-value-management-label').value.trim(),
            productionLabel: document.getElementById('pm-value-production-label').value.trim(),
            dimensionType: document.getElementById('pm-value-dimension-type').value,
            priceAdjustment: Number(document.getElementById('pm-value-price-adjustment').value || 0),
            description: document.getElementById('pm-value-description').value.trim(),
            customerGuide: document.getElementById('pm-value-customer-guide').value.trim(),
            active: document.getElementById('pm-value-active').checked,
            rowVersion: value ? value.rowVersion : null
        };
    }

    async function saveValue(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (!form.checkValidity()) {
            form.reportValidity();
            return;
        }
        const group = selectedGroup();
        if (!group) return;
        const valueId = Number(document.getElementById('pm-value-id').value || 0);
        const url = valueId
            ? '/admin/api/product-master/values/' + valueId
            : '/admin/api/product-master/groups/' + group.id + '/values';
        const imageError = imageManagerError(valueImageManager);
        if (imageError) {
            await pm.alert('warning', '옵션값 이미지를 확인해 주세요.', imageError);
            return;
        }
        pm.showLoading(true, '옵션값을 저장하는 중입니다.');
        try {
            const response = await requestWithImages(
                url,
                valueId ? 'PUT' : 'POST',
                valuePayload(),
                valueImageManager
            );
            valueModal.hide();
            await loadGroups(group.id);
            pm.toast(response.message || '옵션값을 저장했습니다.');
        } catch (error) {
            await pm.alert('error', '옵션값 저장 실패', error.message);
        } finally {
            pm.showLoading(false);
        }
    }

    async function deleteValue(valueId) {
        const group = selectedGroup();
        if (!group) return;
        const value = (group.values || []).find(function (item) { return item.id === valueId; });
        const confirmed = await pm.confirm(
            '옵션값을 삭제하시겠습니까?',
            (value ? value.managementLabel + ' · ' : '') + '등록된 제품 또는 재고 이력이 사용 중이면 삭제되지 않습니다.',
            '옵션값 삭제'
        );
        if (!confirmed) return;
        pm.showLoading(true, '옵션값을 삭제하는 중입니다.');
        try {
            const response = await pm.request('/admin/api/product-master/values/' + valueId, {method: 'DELETE'});
            await loadGroups(group.id);
            pm.toast(response.message || '옵션값을 삭제했습니다.');
        } catch (error) {
            await pm.alert('error', '옵션값 삭제 불가', error.message);
        } finally {
            pm.showLoading(false);
        }
    }

    function emptyHtml(message) {
        return '<div class="pm-empty"><div><span class="pm-empty-icon"><i class="ri-inbox-archive-line"></i></span>'
            + '<strong>' + pm.escapeHtml(message) + '</strong></div></div>';
    }

    document.getElementById('pm-add-group-button').addEventListener('click', function () {
        document.getElementById('pm-group-create-form').reset();
        document.getElementById('pm-new-group-type').value = 'CORE';
        document.getElementById('pm-new-group-selection-mode').value = 'SINGLE';
        document.getElementById('pm-new-group-system-role').value = 'GENERAL';
        document.getElementById('pm-new-group-input-type').value = 'CHOICE';
        document.getElementById('pm-new-group-required-by-default').checked = true;
        document.getElementById('pm-new-group-step-value').value = '1';
        document.getElementById('pm-new-group-custom-dimension-type').value = 'WIDTH_DEPTH_HEIGHT';
        configureGroupInput('pm-new-group-', false);
        newGroupImageManager.reset([]);
        groupModal.show();
    });
    document.getElementById('pm-add-value-button').addEventListener('click', function () {
        const group = selectedGroup();
        if (group && group.inputType !== 'CHOICE' && group.inputType !== 'DIMENSION') return;
        openValueModal(null);
    });
    document.getElementById('pm-delete-group-button').addEventListener('click', deleteGroup);
    document.getElementById('pm-group-editor-form').addEventListener('submit', saveCurrentGroup);
    document.getElementById('pm-group-create-form').addEventListener('submit', createGroup);
    document.getElementById('pm-value-form').addEventListener('submit', saveValue);
    ['pm-group-', 'pm-new-group-'].forEach(function (prefix) {
        ['input-type', 'system-role', 'type'].forEach(function (field) {
            document.getElementById(prefix + field).addEventListener('change', function () { configureGroupInput(prefix, true); });
        });
    });
    document.getElementById('pm-value-custom-preset').addEventListener('click', function () {
        document.getElementById('pm-value-dimension-type').value = 'CUSTOM';
        ['customer', 'management', 'production'].forEach(function (audience) {
            const input = document.getElementById('pm-value-' + audience + '-label');
            if (input && !input.value.trim()) input.value = '비규격';
        });
        const description = document.getElementById('pm-value-description');
        if (description && !description.value.trim()) {
            description.value = selectedGroup() && selectedGroup().systemRole === 'SIZE'
                ? '실제 주문에서 원하는 사이즈를 입력하는 비규격 값'
                : '실제 주문에서 원하는 세부 사양을 입력하는 비규격 값';
        }
    });

    if (elements.groupModalElement) {
        elements.groupModalElement.addEventListener('hidden.bs.modal', function () {
            newGroupImageManager.reset([]);
        });
    }
    if (elements.valueModalElement) {
        elements.valueModalElement.addEventListener('hidden.bs.modal', function () {
            valueImageManager.reset([]);
        });
    }

    async function initialize() {
        populateRoleSelects();
        await imageUi.loadPolicy();
        groupImageManager.render();
        newGroupImageManager.render();
        valueImageManager.render();
        await loadGroups();
        setupGroupTour();
    }

    function setupGroupTour() {
        const overlay = document.getElementById('pm-group-tour-overlay');
        const start = document.getElementById('pm-start-group-tour');
        if (!overlay || !start) return;
        const steps = [
            {
                selector: '[data-pm-group-tour="intro"]',
                title: '제품을 먼저 분류합니다',
                text: '그룹과 옵션값의 코드는 최초 저장 시 자동 생성됩니다. 세 화면의 표시명은 바꿀 수 있지만 코드는 유지되어 과거 제품과 QR 해석이 흔들리지 않습니다.'
            },
            {
                selector: '[data-pm-group-tour="classification"]',
                title: '재고를 만드는 값만 제품 정체성',
                text: '제품 정체성은 SKU와 재고를 결정하고, 내부 구성은 주문 선택만 바꾸며, 외부 추가옵션은 같은 제품 재고 안에서 포함 수량만 집계합니다.'
            },
            {
                selector: '[data-pm-group-tour="group-list"]',
                title: '그룹을 작게 나누고 순서를 정합니다',
                text: '대분류·시리즈·형태·문 타입·수량처럼 한 질문에 한 의미만 두세요. 목록을 끌어 고객 질문 순서를 조정할 수 있습니다.'
            },
            {
                selector: '[data-pm-group-tour="editor"]',
                title: '필요한 설정만 펼칩니다',
                text: '질문·입력 검증, 다중 이미지, 비규격 값은 필요할 때만 엽니다. 제품이나 규칙이 참조 중인 코드는 삭제 대신 사용중지를 이용하세요.'
            }
        ];
        let index = 0;

        function renderTour() {
            const step = steps[index];
            const target = document.querySelector(step.selector);
            if (!target) return;
            target.scrollIntoView({behavior: 'smooth', block: 'center'});
            window.setTimeout(function () {
                const rect = target.getBoundingClientRect();
                const focus = overlay.querySelector('.pm-tour-focus');
                focus.style.left = Math.max(6, rect.left - 6) + 'px';
                focus.style.top = Math.max(6, rect.top - 6) + 'px';
                focus.style.width = Math.min(window.innerWidth - 12, rect.width + 12) + 'px';
                focus.style.height = Math.min(window.innerHeight - 12, rect.height + 12) + 'px';
                document.getElementById('pm-group-tour-step').textContent = (index + 1) + ' / ' + steps.length;
                document.getElementById('pm-group-tour-title').textContent = step.title;
                document.getElementById('pm-group-tour-text').textContent = step.text;
                document.getElementById('pm-group-tour-prev').disabled = index === 0;
                document.getElementById('pm-group-tour-next').textContent = index === steps.length - 1 ? '완료' : '다음';
            }, 180);
        }

        function openTour() {
            index = 0;
            overlay.classList.add('pm-is-visible');
            overlay.setAttribute('aria-hidden', 'false');
            renderTour();
        }

        function closeTour() {
            overlay.classList.remove('pm-is-visible');
            overlay.setAttribute('aria-hidden', 'true');
            try { window.localStorage.setItem('pm-group-tour-v3-complete', '1'); } catch (error) { /* storage unavailable */ }
        }

        start.addEventListener('click', openTour);
        document.getElementById('pm-group-tour-prev').addEventListener('click', function () {
            if (index > 0) { index -= 1; renderTour(); }
        });
        document.getElementById('pm-group-tour-next').addEventListener('click', function () {
            if (index >= steps.length - 1) closeTour();
            else { index += 1; renderTour(); }
        });
        document.getElementById('pm-group-tour-close').addEventListener('click', closeTour);
        window.addEventListener('resize', function () {
            if (overlay.classList.contains('pm-is-visible')) renderTour();
        });
        let completed = true;
        try { completed = window.localStorage.getItem('pm-group-tour-v3-complete') === '1'; } catch (error) { completed = true; }
        if (!completed) window.setTimeout(openTour, 500);
    }

    initialize();
})(window, document);
