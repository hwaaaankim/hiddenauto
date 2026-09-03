(function (window, document) {
    'use strict';

    const pm = window.HiddenBathProductMaster;
    if (!pm) return;

    const API = '/admin/api/product-master/automation';
    const state = {
        groups: [],
        products: [],
        rules: [],
        matrices: [],
        priceRules: [],
        edges: [],
        ruleRisks: new Map(),
        ruleDraft: null,
        matrixCells: [],
        matrixWarnings: [],
        tourIndex: 0
    };

    const matrixModal = new bootstrap.Modal(document.getElementById('pm-matrix-modal'));
    const priceRuleModal = new bootstrap.Modal(document.getElementById('pm-price-rule-modal'));

    const sourceFieldLabels = {
        SELECTED_VALUE: '선택 옵션', WIDTH_MM: 'W 가로', DEPTH_MM: 'D 깊이', HEIGHT_MM: 'H 높이', NUMBER_VALUE: '숫자 입력값'
    };
    const operatorLabels = {
        EQUALS: '같음', NOT_EQUALS: '같지 않음', GREATER_THAN_OR_EQUAL: '이상', LESS_THAN_OR_EQUAL: '이하', BETWEEN: '범위 포함'
    };
    const actionLabels = {
        SHOW_GROUP: '그룹 표시', HIDE_GROUP: '그룹 숨김', REQUIRE_GROUP: '필수로 변경', OPTIONAL_GROUP: '선택으로 변경',
        ENABLE_VALUE: '옵션값 허용', DISABLE_VALUE: '옵션값 제외', SET_VALUE: '옵션값 자동 선택', SET_NUMBER: '숫자값 자동 입력', ADD_NOTICE: '안내 메시지 표시'
    };

    async function initialize() {
        pm.showLoading(true, '구성 규칙과 가격 기준을 불러오는 중입니다.');
        try {
            const response = await pm.request(API + '/bootstrap');
            applyBootstrap(response.data || {});
            bindStaticEvents();
            renderAll();
        } catch (error) {
            await pm.alert('error', '관리 화면 준비 실패', error.message);
        } finally {
            pm.showLoading(false);
        }
    }

    function applyBootstrap(data) {
        state.groups = data.groups || [];
        state.products = data.products || [];
        state.rules = data.configurationRules || [];
        state.matrices = data.matrices || [];
        state.priceRules = data.priceRules || [];
        state.edges = data.impactEdges || [];
    }

    async function reload(message) {
        const response = await pm.request(API + '/bootstrap');
        applyBootstrap(response.data || {});
        renderAll();
        if (message) pm.toast(message);
    }

    function renderAll() {
        state.ruleRisks = analyzeRuleRisks();
        document.getElementById('pm-rule-count').textContent = state.rules.filter(function (item) { return item.active; }).length;
        document.getElementById('pm-matrix-count').textContent = state.matrices.filter(function (item) { return item.active; }).length;
        document.getElementById('pm-price-rule-count').textContent = state.priceRules.filter(function (item) { return item.active; }).length;
        const priceDependencyCount = state.priceRules.reduce(function (total, rule) {
            return total + priceImpactGroups(rule).length;
        }, 0);
        document.getElementById('pm-impact-count').textContent = state.edges.length + priceDependencyCount;
        document.getElementById('pm-risk-count').textContent = '충돌 ' + state.ruleRisks.size;
        fillProductSelects();
        renderRuleList();
        renderMatrixList();
        renderPriceRuleList();
        renderImpactMap();
    }

    function analyzeRuleRisks() {
        const risks = new Map();
        const active = state.rules.filter(function (rule) { return rule.active; });
        const opposites = {
            SHOW_GROUP: 'HIDE_GROUP', HIDE_GROUP: 'SHOW_GROUP',
            REQUIRE_GROUP: 'OPTIONAL_GROUP', OPTIONAL_GROUP: 'REQUIRE_GROUP',
            ENABLE_VALUE: 'DISABLE_VALUE', DISABLE_VALUE: 'ENABLE_VALUE'
        };
        function add(rule, message) {
            if (!risks.has(rule.id)) risks.set(rule.id, []);
            if (!risks.get(rule.id).includes(message)) risks.get(rule.id).push(message);
        }
        function scopeOverlaps(left, right) {
            return left.scopeProductId == null || right.scopeProductId == null
                || left.scopeProductId === right.scopeProductId;
        }
        for (let leftIndex = 0; leftIndex < active.length; leftIndex += 1) {
            for (let rightIndex = leftIndex + 1; rightIndex < active.length; rightIndex += 1) {
                const left = active[leftIndex];
                const right = active[rightIndex];
                if (left.priority !== right.priority || !scopeOverlaps(left, right)) continue;
                (left.actions || []).forEach(function (leftAction) {
                    (right.actions || []).forEach(function (rightAction) {
                        if (leftAction.targetGroupId !== rightAction.targetGroupId) return;
                        const sameValueTarget = (leftAction.targetValueId || null) === (rightAction.targetValueId || null);
                        let conflict = sameValueTarget && opposites[leftAction.actionType] === rightAction.actionType;
                        if (leftAction.actionType === 'SET_VALUE' && rightAction.actionType === 'SET_VALUE'
                            && leftAction.targetValueId !== rightAction.targetValueId) conflict = true;
                        if (leftAction.actionType === 'SET_NUMBER' && rightAction.actionType === 'SET_NUMBER'
                            && String(leftAction.actionNumber) !== String(rightAction.actionNumber)) conflict = true;
                        if (!conflict) return;
                        const message = '같은 우선순위 P' + left.priority + '에서 ' + leftAction.targetGroupLabel + '에 서로 다른 결과를 지정합니다.';
                        add(left, right.ruleCode + '와 ' + message);
                        add(right, left.ruleCode + '와 ' + message);
                    });
                });
            }
        }
        return risks;
    }

    function fillProductSelects() {
        const options = state.products.map(function (product) {
            return '<option value="' + product.id + '">' + pm.escapeHtml(product.name + ' · ' + product.catalogCode) + '</option>';
        }).join('');
        ['pm-rule-scope', 'pm-price-rule-scope'].forEach(function (id) {
            const select = document.getElementById(id);
            const previous = select.value;
            select.innerHTML = '<option value="">전체 제품 공통</option>' + options;
            select.value = previous;
        });
        const test = document.getElementById('pm-test-product');
        const previousTest = test.value;
        test.innerHTML = '<option value="">제품 선택</option>' + options;
        test.value = previousTest;
        fillImpactFilter();
        updateCustomerPreviewLink();
    }

    function fillImpactFilter() {
        const select = document.getElementById('pm-impact-group-filter');
        const previous = select.value;
        select.innerHTML = '<option value="">모든 그룹</option>' + state.groups.map(function (group) {
            return '<option value="' + group.id + '">' + pm.escapeHtml(group.managementLabel) + '</option>';
        }).join('');
        select.value = previous;
    }

    function renderRuleList() {
        const query = document.getElementById('pm-rule-search').value.trim().toLowerCase();
        const scope = document.getElementById('pm-rule-scope-filter').value;
        const rules = state.rules.filter(function (rule) {
            const searchable = [rule.ruleName, rule.ruleCode, rule.summary, rule.scopeProductName].join(' ').toLowerCase();
            if (query && !searchable.includes(query)) return false;
            if (scope === 'GLOBAL' && rule.scopeProductId != null) return false;
            if (scope === 'PRODUCT' && rule.scopeProductId == null) return false;
            return true;
        });
        const list = document.getElementById('pm-rule-list');
        if (!rules.length) {
            list.innerHTML = empty('조건에 맞는 규칙이 없습니다.', 'ri-git-branch-line');
            return;
        }
        list.innerHTML = rules.map(function (rule) {
            const selected = Number(document.getElementById('pm-rule-id').value || 0) === rule.id ? ' pm-is-selected' : '';
            const risk = state.ruleRisks.has(rule.id) ? ' pm-has-risk' : '';
            return '<button class="pm-rule-list-item' + selected + risk + '" type="button" data-pm-rule-id="' + rule.id + '">'
                + '<span class="pm-rule-status ' + (rule.active ? 'pm-is-on' : '') + '"></span><span class="pm-rule-list-copy">'
                + '<strong>' + pm.escapeHtml(rule.ruleName) + '</strong><small>' + pm.escapeHtml(rule.summary) + '</small>'
                + '<span><code>' + pm.escapeHtml(rule.ruleCode) + '</code><em>' + pm.escapeHtml(rule.scopeProductName) + '</em><b>P' + rule.priority + '</b></span>'
                + '</span>' + (risk ? '<i class="ri-error-warning-line text-danger" title="동일 우선순위 충돌 가능"></i>' : '<i class="ri-arrow-right-s-line"></i>') + '</button>';
        }).join('');
        list.querySelectorAll('[data-pm-rule-id]').forEach(function (button) {
            button.addEventListener('click', function () { editRule(Number(button.dataset.pmRuleId)); });
        });
    }

    function newRule() {
        state.ruleDraft = {
            id: null, rowVersion: null, ruleName: '', description: '', scopeProductId: null, matchMode: 'ALL', priority: 100, active: true,
            conditions: [blankCondition()], actions: [blankAction()]
        };
        showRuleEditor();
    }

    function editRule(ruleId) {
        const source = state.rules.find(function (item) { return item.id === ruleId; });
        if (!source) return;
        state.ruleDraft = {
            id: source.id, rowVersion: source.rowVersion, ruleCode: source.ruleCode, ruleName: source.ruleName, description: source.description || '',
            scopeProductId: source.scopeProductId, matchMode: source.matchMode, priority: source.priority, active: source.active,
            conditions: source.conditions.map(function (item) {
                return {
                    sourceGroupId: item.sourceGroupId, sourceValueId: item.sourceValueId, sourceField: item.sourceField,
                    operator: item.operator, comparisonFrom: item.comparisonFrom, comparisonTo: item.comparisonTo
                };
            }),
            actions: source.actions.map(function (item) {
                return {
                    actionType: item.actionType, targetGroupId: item.targetGroupId, targetValueId: item.targetValueId,
                    actionNumber: item.actionNumber, message: item.message || ''
                };
            })
        };
        showRuleEditor();
    }

    function showRuleEditor() {
        const draft = state.ruleDraft;
        document.getElementById('pm-rule-editor-empty').classList.add('d-none');
        document.getElementById('pm-rule-form').classList.remove('d-none');
        document.getElementById('pm-rule-id').value = draft.id || '';
        document.getElementById('pm-rule-name').value = draft.ruleName || '';
        document.getElementById('pm-rule-description').value = draft.description || '';
        document.getElementById('pm-rule-scope').value = draft.scopeProductId || '';
        document.getElementById('pm-rule-priority').value = draft.priority == null ? 100 : draft.priority;
        document.getElementById('pm-rule-match-mode').value = draft.matchMode || 'ALL';
        document.getElementById('pm-rule-active').checked = Boolean(draft.active);
        document.getElementById('pm-rule-editor-title').textContent = draft.id ? '조건 규칙 수정' : '새 조건 규칙';
        document.getElementById('pm-rule-editor-code').textContent = draft.ruleCode || '저장 시 고유 코드가 자동 생성됩니다.';
        document.getElementById('pm-delete-rule').classList.toggle('d-none', !draft.id);
        renderConditionRows();
        renderActionRows();
        renderRuleList();
        document.getElementById('pm-rule-name').focus();
    }

    function blankCondition() {
        const group = state.groups.find(function (item) { return item.active; }) || state.groups[0];
        const field = group && (group.inputType === 'CHOICE' || group.inputType === 'DIMENSION') ? 'SELECTED_VALUE'
            : (group && group.inputType === 'NUMBER' ? 'NUMBER_VALUE' : 'SELECTED_VALUE');
        return {sourceGroupId: group ? group.id : null, sourceValueId: null, sourceField: field, operator: 'EQUALS', comparisonFrom: null, comparisonTo: null};
    }

    function blankAction() {
        const group = state.groups.find(function (item) { return item.active; }) || state.groups[0];
        return {actionType: 'SHOW_GROUP', targetGroupId: group ? group.id : null, targetValueId: null, actionNumber: null, message: ''};
    }

    function renderConditionRows() {
        const container = document.getElementById('pm-condition-rows');
        container.innerHTML = state.ruleDraft.conditions.map(function (condition, index) {
            const group = groupById(condition.sourceGroupId);
            const fields = sourceFieldsFor(group);
            if (!fields.includes(condition.sourceField)) condition.sourceField = fields[0];
            const selectedField = condition.sourceField === 'SELECTED_VALUE';
            const operators = selectedField ? ['EQUALS', 'NOT_EQUALS'] : ['EQUALS', 'NOT_EQUALS', 'GREATER_THAN_OR_EQUAL', 'LESS_THAN_OR_EQUAL', 'BETWEEN'];
            if (!operators.includes(condition.operator)) condition.operator = operators[0];
            let comparison = '';
            if (selectedField) {
                comparison = '<select class="form-select" data-pm-condition-value>' + valueOptions(group, condition.sourceValueId, false) + '</select>';
            } else {
                comparison = '<div class="pm-rule-number-comparison"><input class="form-control" type="number" step="0.001" data-pm-condition-from value="'
                    + valueAttr(condition.comparisonFrom) + '" placeholder="기준값">'
                    + (condition.operator === 'BETWEEN' ? '<span>~</span><input class="form-control" type="number" step="0.001" data-pm-condition-to value="' + valueAttr(condition.comparisonTo) + '" placeholder="끝값">' : '') + '</div>';
            }
            return '<div class="pm-sentence-row" data-pm-condition-index="' + index + '"><span class="pm-row-index">' + (index + 1) + '</span>'
                + '<select class="form-select" data-pm-condition-group>' + groupOptions(condition.sourceGroupId) + '</select>'
                + '<select class="form-select" data-pm-condition-field>' + optionMap(fields, sourceFieldLabels, condition.sourceField) + '</select>'
                + '<select class="form-select" data-pm-condition-operator>' + optionMap(operators, operatorLabels, condition.operator) + '</select>'
                + '<div class="pm-sentence-value">' + comparison + '</div>'
                + '<button class="pm-icon-btn pm-danger" type="button" data-pm-remove-condition="' + index + '" title="조건 삭제"><i class="ri-close-line"></i></button></div>';
        }).join('');
        bindConditionEvents();
    }

    function bindConditionEvents() {
        const container = document.getElementById('pm-condition-rows');
        container.querySelectorAll('[data-pm-condition-index]').forEach(function (row) {
            const index = Number(row.dataset.pmConditionIndex);
            row.querySelector('[data-pm-condition-group]').addEventListener('change', function (event) {
                collectRuleRows();
                const condition = state.ruleDraft.conditions[index];
                condition.sourceGroupId = Number(event.target.value);
                condition.sourceValueId = null;
                condition.sourceField = sourceFieldsFor(groupById(condition.sourceGroupId))[0];
                condition.operator = 'EQUALS';
                renderConditionRows();
            });
            row.querySelector('[data-pm-condition-field]').addEventListener('change', function (event) {
                collectRuleRows();
                state.ruleDraft.conditions[index].sourceField = event.target.value;
                state.ruleDraft.conditions[index].sourceValueId = null;
                state.ruleDraft.conditions[index].operator = 'EQUALS';
                renderConditionRows();
            });
            row.querySelector('[data-pm-condition-operator]').addEventListener('change', function (event) {
                collectRuleRows();
                state.ruleDraft.conditions[index].operator = event.target.value;
                renderConditionRows();
            });
        });
        container.querySelectorAll('[data-pm-remove-condition]').forEach(function (button) {
            button.addEventListener('click', function () {
                collectRuleRows();
                if (state.ruleDraft.conditions.length === 1) return pm.toast('조건은 하나 이상 필요합니다.', 'info');
                state.ruleDraft.conditions.splice(Number(button.dataset.pmRemoveCondition), 1);
                renderConditionRows();
            });
        });
    }

    function renderActionRows() {
        const container = document.getElementById('pm-action-rows');
        container.innerHTML = state.ruleDraft.actions.map(function (action, index) {
            const group = groupById(action.targetGroupId);
            const needsValue = ['ENABLE_VALUE', 'DISABLE_VALUE', 'SET_VALUE'].includes(action.actionType);
            const needsNumber = action.actionType === 'SET_NUMBER';
            const needsMessage = action.actionType === 'ADD_NOTICE';
            let target = '<span class="pm-muted-placeholder">추가 입력 없음</span>';
            if (needsValue) target = '<select class="form-select" data-pm-action-value>' + valueOptions(group, action.targetValueId, false) + '</select>';
            if (needsNumber) target = '<input class="form-control" type="number" step="0.001" data-pm-action-number value="' + valueAttr(action.actionNumber) + '" placeholder="자동 입력값">';
            if (needsMessage) target = '<input class="form-control" maxlength="500" data-pm-action-message value="' + pm.escapeHtml(action.message || '') + '" placeholder="고객 안내 메시지">';
            return '<div class="pm-sentence-row" data-pm-action-index="' + index + '"><span class="pm-row-index">' + (index + 1) + '</span>'
                + '<select class="form-select" data-pm-action-type>' + optionMap(Object.keys(actionLabels), actionLabels, action.actionType) + '</select>'
                + '<select class="form-select" data-pm-action-group>' + groupOptions(action.targetGroupId) + '</select>'
                + '<div class="pm-sentence-value pm-sentence-value-wide">' + target + '</div>'
                + '<button class="pm-icon-btn pm-danger" type="button" data-pm-remove-action="' + index + '" title="실행 삭제"><i class="ri-close-line"></i></button></div>';
        }).join('');
        bindActionEvents();
    }

    function bindActionEvents() {
        const container = document.getElementById('pm-action-rows');
        container.querySelectorAll('[data-pm-action-index]').forEach(function (row) {
            const index = Number(row.dataset.pmActionIndex);
            row.querySelector('[data-pm-action-type]').addEventListener('change', function (event) {
                collectRuleRows();
                state.ruleDraft.actions[index].actionType = event.target.value;
                state.ruleDraft.actions[index].targetValueId = null;
                state.ruleDraft.actions[index].actionNumber = null;
                state.ruleDraft.actions[index].message = '';
                renderActionRows();
            });
            row.querySelector('[data-pm-action-group]').addEventListener('change', function (event) {
                collectRuleRows();
                state.ruleDraft.actions[index].targetGroupId = Number(event.target.value);
                state.ruleDraft.actions[index].targetValueId = null;
                renderActionRows();
            });
        });
        container.querySelectorAll('[data-pm-remove-action]').forEach(function (button) {
            button.addEventListener('click', function () {
                collectRuleRows();
                if (state.ruleDraft.actions.length === 1) return pm.toast('실행 항목은 하나 이상 필요합니다.', 'info');
                state.ruleDraft.actions.splice(Number(button.dataset.pmRemoveAction), 1);
                renderActionRows();
            });
        });
    }

    function collectRuleRows() {
        document.querySelectorAll('[data-pm-condition-index]').forEach(function (row) {
            const item = state.ruleDraft.conditions[Number(row.dataset.pmConditionIndex)];
            item.sourceGroupId = numberOrNull(row.querySelector('[data-pm-condition-group]').value);
            item.sourceField = row.querySelector('[data-pm-condition-field]').value;
            item.operator = row.querySelector('[data-pm-condition-operator]').value;
            const value = row.querySelector('[data-pm-condition-value]');
            const from = row.querySelector('[data-pm-condition-from]');
            const to = row.querySelector('[data-pm-condition-to]');
            item.sourceValueId = value ? numberOrNull(value.value) : null;
            item.comparisonFrom = from ? numberOrNull(from.value) : null;
            item.comparisonTo = to ? numberOrNull(to.value) : null;
        });
        document.querySelectorAll('[data-pm-action-index]').forEach(function (row) {
            const item = state.ruleDraft.actions[Number(row.dataset.pmActionIndex)];
            item.actionType = row.querySelector('[data-pm-action-type]').value;
            item.targetGroupId = numberOrNull(row.querySelector('[data-pm-action-group]').value);
            const value = row.querySelector('[data-pm-action-value]');
            const number = row.querySelector('[data-pm-action-number]');
            const message = row.querySelector('[data-pm-action-message]');
            item.targetValueId = value ? numberOrNull(value.value) : null;
            item.actionNumber = number ? numberOrNull(number.value) : null;
            item.message = message ? message.value.trim() : '';
        });
    }

    async function saveRule(event) {
        event.preventDefault();
        if (!event.currentTarget.checkValidity()) return event.currentTarget.reportValidity();
        collectRuleRows();
        const draft = state.ruleDraft;
        const body = {
            ruleName: document.getElementById('pm-rule-name').value.trim(),
            description: document.getElementById('pm-rule-description').value.trim(),
            scopeProductId: numberOrNull(document.getElementById('pm-rule-scope').value),
            matchMode: document.getElementById('pm-rule-match-mode').value,
            priority: Number(document.getElementById('pm-rule-priority').value || 0),
            active: document.getElementById('pm-rule-active').checked,
            conditions: draft.conditions.map(function (item, index) { return Object.assign({}, item, {sortOrder: index * 10}); }),
            actions: draft.actions.map(function (item, index) { return Object.assign({}, item, {sortOrder: index * 10}); }),
            rowVersion: draft.rowVersion
        };
        pm.showLoading(true, '조건 규칙의 참조와 충돌을 검사하는 중입니다.');
        try {
            const response = await pm.request(API + '/rules' + (draft.id ? '/' + draft.id : ''), {
                method: draft.id ? 'PUT' : 'POST', body: body
            });
            state.ruleDraft = null;
            hideRuleEditor();
            await reload(response.message || '조건 규칙을 저장했습니다.');
        } catch (error) {
            await pm.alert('error', '규칙 저장 실패', error.message);
        } finally {
            pm.showLoading(false);
        }
    }

    function hideRuleEditor() {
        document.getElementById('pm-rule-id').value = '';
        document.getElementById('pm-rule-form').classList.add('d-none');
        document.getElementById('pm-rule-editor-empty').classList.remove('d-none');
    }

    async function deleteRule() {
        const id = Number(document.getElementById('pm-rule-id').value || 0);
        if (!id || !(await pm.confirm('조건 규칙을 삭제하시겠습니까?', '삭제 후 고객 질문 흐름에서 즉시 제외됩니다.', '규칙 삭제'))) return;
        try {
            await pm.request(API + '/rules/' + id, {method: 'DELETE'});
            state.ruleDraft = null;
            hideRuleEditor();
            await reload('조건 규칙을 삭제했습니다.');
        } catch (error) { await pm.alert('error', '삭제 실패', error.message); }
    }

    function renderMatrixList() {
        const list = document.getElementById('pm-matrix-list');
        if (!state.matrices.length) {
            list.innerHTML = empty('등록된 2축 가격표가 없습니다.', 'ri-table-line');
            return;
        }
        list.innerHTML = state.matrices.map(function (matrix) {
            return '<article class="pm-matrix-card"><div class="pm-matrix-card-head"><span class="pm-health-icon pm-tone-violet"><i class="ri-table-2"></i></span><div><strong>'
                + pm.escapeHtml(matrix.matrixName) + '</strong><small><code>' + pm.escapeHtml(matrix.matrixCode) + '</code> · '
                + (matrix.active ? '사용중' : '사용중지') + '</small></div><button class="pm-icon-btn" type="button" data-pm-edit-matrix="' + matrix.id + '"><i class="ri-edit-line"></i></button></div>'
                + '<div class="pm-matrix-spec"><span><b>X</b>' + pm.escapeHtml(matrix.xGroupLabel + ' · ' + matrix.xFieldLabel) + '</span><span><b>Y</b>'
                + pm.escapeHtml(matrix.yGroupLabel + ' · ' + matrix.yFieldLabel) + '</span></div>'
                + '<div class="pm-matrix-card-foot"><span>' + matrix.xAxis.length + ' × ' + matrix.yAxis.length + ' 구간</span><span>' + matrix.cells.length + ' 셀</span><span>'
                + pm.escapeHtml(matrix.lookupModeLabel) + '</span>' + (matrix.extensionEnabled ? '<span class="pm-highlight-chip">초과분 규칙</span>' : '') + '</div></article>';
        }).join('');
        list.querySelectorAll('[data-pm-edit-matrix]').forEach(function (button) {
            button.addEventListener('click', function () { openMatrix(Number(button.dataset.pmEditMatrix)); });
        });
    }

    function openMatrix(matrixId) {
        const matrix = matrixId ? state.matrices.find(function (item) { return item.id === matrixId; }) : null;
        document.getElementById('pm-matrix-form').reset();
        document.getElementById('pm-matrix-id').value = matrix ? matrix.id : '';
        document.getElementById('pm-matrix-name').value = matrix ? matrix.matrixName : '';
        document.getElementById('pm-matrix-description').value = matrix && matrix.description ? matrix.description : '';
        fillMatrixGroupSelect('pm-matrix-x-group', matrix ? matrix.xGroupId : null);
        fillMatrixGroupSelect('pm-matrix-y-group', matrix ? matrix.yGroupId : null);
        renderMatrixFieldSelect('x', matrix ? matrix.xField : null);
        renderMatrixFieldSelect('y', matrix ? matrix.yField : null);
        document.getElementById('pm-matrix-lookup').value = matrix ? matrix.lookupMode : 'CEILING';
        document.getElementById('pm-matrix-x-round').value = matrix ? matrix.xRoundUnit : 100;
        document.getElementById('pm-matrix-y-round').value = matrix ? matrix.yRoundUnit : 100;
        document.getElementById('pm-matrix-extension-enabled').checked = Boolean(matrix && matrix.extensionEnabled);
        document.getElementById('pm-matrix-extension-start').value = matrix && matrix.extensionStart != null ? matrix.extensionStart : '';
        document.getElementById('pm-matrix-extension-unit').value = matrix && matrix.extensionUnit != null ? matrix.extensionUnit : '';
        document.getElementById('pm-matrix-extension-amount').value = matrix && matrix.extensionAmount != null ? matrix.extensionAmount : '';
        document.getElementById('pm-matrix-active').checked = matrix ? matrix.active : true;
        document.getElementById('pm-matrix-modal-title').textContent = matrix ? '2축 가격표 수정' : '2축 가격표 등록';
        document.getElementById('pm-matrix-modal-subtitle').textContent = matrix ? matrix.matrixCode + ' · 저장된 가격 규칙의 참조는 유지됩니다.' : '축과 구간 처리방식을 먼저 정한 뒤 표를 입력합니다.';
        document.getElementById('pm-delete-matrix').classList.toggle('d-none', !matrix);
        state.matrixCells = matrix ? matrix.cells.map(function (cell) { return {xValue: cell.xValue, yValue: cell.yValue, amount: cell.amount}; }) : [];
        state.matrixWarnings = [];
        renderMatrixGrid();
        matrixModal.show();
    }

    function fillMatrixGroupSelect(id, selected) {
        const select = document.getElementById(id);
        const groups = state.groups.filter(function (group) { return group.inputType === 'DIMENSION' || group.inputType === 'NUMBER'; });
        select.innerHTML = groups.map(function (group) {
            return '<option value="' + group.id + '"' + (group.id === selected ? ' selected' : '') + '>' + pm.escapeHtml(group.managementLabel + ' · ' + group.inputTypeLabel) + '</option>';
        }).join('');
    }

    function renderMatrixFieldSelect(axis, selected) {
        const group = groupById(Number(document.getElementById('pm-matrix-' + axis + '-group').value));
        const fields = group && group.inputType === 'NUMBER' ? ['NUMBER_VALUE'] : ['WIDTH_MM', 'DEPTH_MM', 'HEIGHT_MM'];
        const value = fields.includes(selected) ? selected : fields[0];
        document.getElementById('pm-matrix-' + axis + '-field').innerHTML = optionMap(fields, sourceFieldLabels, value);
    }

    function buildMatrixGrid() {
        try {
            const xAxis = parseAxis(document.getElementById('pm-matrix-x-axis').value, 'X축');
            const yAxis = parseAxis(document.getElementById('pm-matrix-y-axis').value, 'Y축');
            const previous = new Map(state.matrixCells.map(function (cell) { return [coord(cell.xValue, cell.yValue), cell.amount]; }));
            state.matrixCells = [];
            yAxis.forEach(function (y) { xAxis.forEach(function (x) {
                state.matrixCells.push({xValue: x, yValue: y, amount: previous.get(coord(x, y)) == null ? 0 : previous.get(coord(x, y))});
            }); });
            state.matrixWarnings = [];
            renderMatrixGrid();
        } catch (error) { pm.alert('warning', '축 값을 확인해 주세요.', error.message); }
    }

    function renderMatrixGrid() {
        const container = document.getElementById('pm-matrix-preview');
        if (!state.matrixCells.length) {
            container.innerHTML = empty('파일을 검증하거나 축 값을 입력하면 가격표가 나타납니다.', 'ri-table-line');
            return;
        }
        const xAxis = uniqueSorted(state.matrixCells.map(function (cell) { return cell.xValue; }));
        const yAxis = uniqueSorted(state.matrixCells.map(function (cell) { return cell.yValue; }));
        const map = new Map(state.matrixCells.map(function (cell) { return [coord(cell.xValue, cell.yValue), cell.amount]; }));
        container.innerHTML = (state.matrixWarnings.length ? '<div class="pm-import-warning"><i class="ri-error-warning-line"></i><span>' + pm.escapeHtml(state.matrixWarnings.slice(0, 3).join(' / ')) + '</span></div>' : '')
            + '<div class="pm-matrix-table-wrap"><table class="pm-matrix-table"><thead><tr><th>Y ＼ X</th>'
            + xAxis.map(function (x) { return '<th>' + pm.escapeHtml(x) + '</th>'; }).join('') + '</tr></thead><tbody>'
            + yAxis.map(function (y) { return '<tr><th>' + pm.escapeHtml(y) + '</th>' + xAxis.map(function (x) {
                const key = coord(x, y);
                return '<td><input type="number" min="0" max="2000000000" value="' + pm.escapeHtml(map.get(key) == null ? '' : map.get(key)) + '" data-pm-matrix-cell="' + pm.escapeHtml(key) + '" aria-label="X ' + pm.escapeHtml(x) + ' Y ' + pm.escapeHtml(y) + ' 금액"></td>';
            }).join('') + '</tr>'; }).join('') + '</tbody></table></div><div class="pm-matrix-summary"><span>' + xAxis.length + ' × ' + yAxis.length + ' 구간</span><span>' + state.matrixCells.length + '개 가격 셀</span><span>금액 단위: 원</span></div>';
    }

    function collectMatrixCells() {
        const inputs = document.querySelectorAll('[data-pm-matrix-cell]');
        if (!inputs.length) return;
        const byKey = new Map(state.matrixCells.map(function (cell) { return [coord(cell.xValue, cell.yValue), cell]; }));
        inputs.forEach(function (input) {
            const cell = byKey.get(input.dataset.pmMatrixCell);
            if (cell) cell.amount = Number(input.value || 0);
        });
    }

    async function previewMatrixFile(file) {
        if (!file) return;
        const body = new FormData();
        body.append('file', file);
        pm.showLoading(true, '가격표의 축, 중복 좌표, 금액을 검증하는 중입니다.');
        try {
            const response = await pm.request(API + '/matrices/import-preview', {method: 'POST', body: body});
            const preview = response.data;
            state.matrixCells = preview.cells || [];
            state.matrixWarnings = preview.warnings || [];
            document.getElementById('pm-matrix-x-axis').value = (preview.xAxis || []).join(', ');
            document.getElementById('pm-matrix-y-axis').value = (preview.yAxis || []).join(', ');
            renderMatrixGrid();
            pm.toast(response.message || '가격표를 검증했습니다.');
        } catch (error) { await pm.alert('error', '가격표 검증 실패', error.message); }
        finally { pm.showLoading(false); }
    }

    async function saveMatrix(event) {
        event.preventDefault();
        if (!event.currentTarget.checkValidity()) return event.currentTarget.reportValidity();
        collectMatrixCells();
        if (!state.matrixCells.length) return pm.alert('warning', '가격표가 비어 있습니다.', '엑셀을 업로드하거나 직접 표를 만들어 주세요.');
        const id = numberOrNull(document.getElementById('pm-matrix-id').value);
        const currentMatrix = id ? state.matrices.find(function (item) { return item.id === id; }) : null;
        const extensionEnabled = document.getElementById('pm-matrix-extension-enabled').checked;
        const body = {
            matrixName: document.getElementById('pm-matrix-name').value.trim(),
            description: document.getElementById('pm-matrix-description').value.trim(),
            xGroupId: Number(document.getElementById('pm-matrix-x-group').value),
            xField: document.getElementById('pm-matrix-x-field').value,
            yGroupId: Number(document.getElementById('pm-matrix-y-group').value),
            yField: document.getElementById('pm-matrix-y-field').value,
            lookupMode: document.getElementById('pm-matrix-lookup').value,
            xRoundUnit: Number(document.getElementById('pm-matrix-x-round').value),
            yRoundUnit: Number(document.getElementById('pm-matrix-y-round').value),
            extensionEnabled: extensionEnabled,
            extensionStart: extensionEnabled ? numberOrNull(document.getElementById('pm-matrix-extension-start').value) : null,
            extensionUnit: extensionEnabled ? numberOrNull(document.getElementById('pm-matrix-extension-unit').value) : null,
            extensionAmount: extensionEnabled ? numberOrNull(document.getElementById('pm-matrix-extension-amount').value) : null,
            active: document.getElementById('pm-matrix-active').checked,
            cells: state.matrixCells.map(function (cell) { return {xValue: Number(cell.xValue), yValue: Number(cell.yValue), amount: Number(cell.amount)}; }),
            rowVersion: currentMatrix ? currentMatrix.rowVersion : null
        };
        pm.showLoading(true, '가격표 좌표와 초과분 규칙을 저장하는 중입니다.');
        try {
            const response = await pm.request(API + '/matrices' + (id ? '/' + id : ''), {method: id ? 'PUT' : 'POST', body: body});
            matrixModal.hide();
            await reload(response.message || '가격표를 저장했습니다.');
        } catch (error) { await pm.alert('error', '가격표 저장 실패', error.message); }
        finally { pm.showLoading(false); }
    }

    async function deleteMatrix() {
        const id = numberOrNull(document.getElementById('pm-matrix-id').value);
        if (!id || !(await pm.confirm('가격표를 삭제하시겠습니까?', '가격 규칙에서 참조 중이면 삭제되지 않습니다.', '가격표 삭제'))) return;
        try {
            await pm.request(API + '/matrices/' + id, {method: 'DELETE'});
            matrixModal.hide();
            await reload('가격표를 삭제했습니다.');
        } catch (error) { await pm.alert('error', '가격표 삭제 실패', error.message); }
    }

    function renderPriceRuleList() {
        const list = document.getElementById('pm-price-rule-list');
        if (!state.priceRules.length) {
            list.innerHTML = empty('등록된 가격 계산 규칙이 없습니다.', 'ri-calculator-line');
            return;
        }
        list.innerHTML = state.priceRules.map(function (rule) {
            return '<article class="pm-price-rule-card"><div class="pm-price-rule-head"><span class="pm-health-icon pm-tone-green"><i class="ri-calculator-line"></i></span><div><small>'
                + pm.escapeHtml(rule.ruleTypeLabel) + ' · ' + pm.escapeHtml(rule.scopeProductName) + '</small><strong>' + pm.escapeHtml(rule.ruleName) + '</strong></div><button class="pm-icon-btn" type="button" data-pm-edit-price-rule="' + rule.id + '"><i class="ri-edit-line"></i></button></div>'
                + '<p>' + pm.escapeHtml(rule.summary) + '</p><div class="pm-price-rule-foot"><code>' + pm.escapeHtml(rule.priceRuleCode) + '</code><span>P' + rule.priority + '</span><span>'
                + pm.escapeHtml(rule.applyModeLabel) + '</span><span class="' + (rule.active ? 'text-success' : 'text-muted') + '">' + (rule.active ? '사용중' : '사용중지') + '</span></div></article>';
        }).join('');
        list.querySelectorAll('[data-pm-edit-price-rule]').forEach(function (button) {
            button.addEventListener('click', function () { openPriceRule(Number(button.dataset.pmEditPriceRule)); });
        });
    }

    function openPriceRule(ruleId) {
        const rule = ruleId ? state.priceRules.find(function (item) { return item.id === ruleId; }) : null;
        document.getElementById('pm-price-rule-form').reset();
        document.getElementById('pm-price-rule-id').value = rule ? rule.id : '';
        document.getElementById('pm-price-rule-name').value = rule ? rule.ruleName : '';
        document.getElementById('pm-price-rule-description').value = rule && rule.description ? rule.description : '';
        document.getElementById('pm-price-rule-scope').value = rule && rule.scopeProductId ? rule.scopeProductId : '';
        document.getElementById('pm-price-rule-type').value = rule ? rule.ruleType : 'FIXED_ADD';
        document.getElementById('pm-price-rule-apply').value = rule ? rule.applyMode : 'ADD';
        document.getElementById('pm-price-rule-priority').value = rule ? rule.priority : 100;
        document.getElementById('pm-price-rule-active').checked = rule ? rule.active : true;
        document.getElementById('pm-price-rule-modal-title').textContent = rule ? '가격 규칙 수정' : '가격 규칙 등록';
        document.getElementById('pm-price-rule-modal-subtitle').textContent = rule ? rule.priceRuleCode + ' · ' + rule.summary : '유형을 선택하면 필요한 항목만 표시됩니다.';
        document.getElementById('pm-delete-price-rule').classList.toggle('d-none', !rule);
        renderPriceRuleFields(rule);
        priceRuleModal.show();
    }

    function renderPriceRuleFields(rule) {
        const type = document.getElementById('pm-price-rule-type').value;
        const trigger = rule ? rule.triggerValueId : numberOrNull(valueOf('pm-pr-trigger'));
        let html = '';
        if (type === 'FIXED_ADD') {
            html = fieldSelect('기준 옵션값', 'pm-pr-trigger', allValueOptions(trigger, false), '선택되면 규칙을 적용합니다.')
                + moneyField('적용 금액', 'pm-pr-amount', rule ? rule.amount : null, '비우면 옵션값의 공급가 조정액을 사용합니다.');
        } else if (type === 'OPTION_X_NUMBER') {
            html = fieldSelect('단가 옵션값', 'pm-pr-trigger', allValueOptions(trigger, false), '선택 여부와 기본 단가의 기준입니다.')
                + fieldSelect('수량 그룹', 'pm-pr-quantity-group', numberGroupOptions(rule ? rule.quantityGroupId : null), '예: 손잡이 수량, 문 수량')
                + moneyField('단가 덮어쓰기', 'pm-pr-amount', rule ? rule.amount : null, '비우면 옵션값 공급가 조정액 × 수량으로 계산합니다.');
        } else if (type === 'MATRIX') {
            html = fieldSelect('2축 가격표', 'pm-pr-matrix', matrixOptions(rule ? rule.matrixId : null), '표의 X/Y 그룹 입력값으로 금액을 찾습니다.')
                + fieldSelect('적용 조건 옵션', 'pm-pr-trigger', '<option value="">항상 계산</option>' + allValueOptions(trigger, true), '특정 옵션을 선택했을 때만 계산하려면 지정합니다.');
        } else {
            const groupId = rule ? rule.sourceGroupId : numberOrNull(valueOf('pm-pr-source-group'));
            html = fieldSelect('적용 조건 옵션', 'pm-pr-trigger', '<option value="">항상 계산</option>' + allValueOptions(trigger, true), '특정 분류·시리즈·형태에서만 계산하려면 지정합니다.')
                + fieldSelect('기준 그룹', 'pm-pr-source-group', numericSourceGroupOptions(groupId), '숫자형 또는 사이즈형 그룹')
                + fieldSelect('기준 항목', 'pm-pr-source-field', sourceFieldOptionsForGroup(groupId, rule ? rule.sourceField : null), '초과 여부를 판단할 축')
                + numberField('기준값', 'pm-pr-base', rule ? rule.baseNumber : null, '이 값까지 추가금 없음')
                + numberField('증가 단위', 'pm-pr-step', rule ? rule.stepNumber : null, '초과분을 나눌 단위')
                + moneyField('단위당 금액', 'pm-pr-step-amount', rule ? rule.stepAmount : null, '남은 값은 한 구간으로 올림합니다.');
        }
        document.getElementById('pm-price-rule-dynamic-fields').innerHTML = '<div class="pm-dynamic-field-grid">' + html + '</div>';
        const sourceGroup = document.getElementById('pm-pr-source-group');
        if (sourceGroup) sourceGroup.addEventListener('change', function () { renderPriceRuleFields(null); });
    }

    function fieldSelect(label, id, options, help) {
        return '<div class="pm-dynamic-field"><label class="form-label fw-semibold" for="' + id + '">' + pm.escapeHtml(label) + '</label><select class="form-select" id="' + id + '">' + options + '</select><small>' + pm.escapeHtml(help) + '</small></div>';
    }
    function moneyField(label, id, value, help) {
        return '<div class="pm-dynamic-field"><label class="form-label fw-semibold" for="' + id + '">' + pm.escapeHtml(label) + '</label><div class="input-group"><input class="form-control" id="' + id + '" type="number" value="' + valueAttr(value) + '"><span class="input-group-text">원</span></div><small>' + pm.escapeHtml(help) + '</small></div>';
    }
    function numberField(label, id, value, help) {
        return '<div class="pm-dynamic-field"><label class="form-label fw-semibold" for="' + id + '">' + pm.escapeHtml(label) + '</label><input class="form-control" id="' + id + '" type="number" step="0.001" value="' + valueAttr(value) + '"><small>' + pm.escapeHtml(help) + '</small></div>';
    }

    async function savePriceRule(event) {
        event.preventDefault();
        if (!event.currentTarget.checkValidity()) return event.currentTarget.reportValidity();
        const id = numberOrNull(document.getElementById('pm-price-rule-id').value);
        const type = document.getElementById('pm-price-rule-type').value;
        const currentPriceRule = id ? state.priceRules.find(function (item) { return item.id === id; }) : null;
        const body = {
            ruleName: document.getElementById('pm-price-rule-name').value.trim(),
            description: document.getElementById('pm-price-rule-description').value.trim(),
            scopeProductId: numberOrNull(document.getElementById('pm-price-rule-scope').value),
            ruleType: type,
            applyMode: document.getElementById('pm-price-rule-apply').value,
            triggerValueId: numberOrNull(valueOf('pm-pr-trigger')),
            quantityGroupId: numberOrNull(valueOf('pm-pr-quantity-group')),
            sourceGroupId: numberOrNull(valueOf('pm-pr-source-group')),
            sourceField: valueOf('pm-pr-source-field') || null,
            matrixId: numberOrNull(valueOf('pm-pr-matrix')),
            amount: numberOrNull(valueOf('pm-pr-amount')),
            baseNumber: numberOrNull(valueOf('pm-pr-base')),
            stepNumber: numberOrNull(valueOf('pm-pr-step')),
            stepAmount: numberOrNull(valueOf('pm-pr-step-amount')),
            priority: Number(document.getElementById('pm-price-rule-priority').value || 0),
            active: document.getElementById('pm-price-rule-active').checked,
            rowVersion: currentPriceRule ? currentPriceRule.rowVersion : null
        };
        pm.showLoading(true, '가격 규칙의 입력 유형과 참조를 검증하는 중입니다.');
        try {
            const response = await pm.request(API + '/price-rules' + (id ? '/' + id : ''), {method: id ? 'PUT' : 'POST', body: body});
            priceRuleModal.hide();
            await reload(response.message || '가격 규칙을 저장했습니다.');
        } catch (error) { await pm.alert('error', '가격 규칙 저장 실패', error.message); }
        finally { pm.showLoading(false); }
    }

    async function deletePriceRule() {
        const id = numberOrNull(document.getElementById('pm-price-rule-id').value);
        if (!id || !(await pm.confirm('가격 규칙을 삭제하시겠습니까?', '이후 견적부터 해당 계산식이 제외됩니다.', '가격 규칙 삭제'))) return;
        try {
            await pm.request(API + '/price-rules/' + id, {method: 'DELETE'});
            priceRuleModal.hide();
            await reload('가격 규칙을 삭제했습니다.');
        } catch (error) { await pm.alert('error', '삭제 실패', error.message); }
    }

    function renderImpactMap() {
        const raw = document.getElementById('pm-impact-group-filter').value;
        const groupId = numberOrNull(raw);
        const edges = state.edges.filter(function (edge) {
            return !groupId || edge.sourceGroupId === groupId || edge.targetGroupId === groupId;
        });
        const priceRules = state.priceRules.filter(function (rule) {
            return !groupId || priceImpactGroups(rule).some(function (group) { return group.id === groupId; });
        });
        const container = document.getElementById('pm-impact-map');
        if (!edges.length && !priceRules.length) {
            container.innerHTML = empty('표시할 옵션 영향 연결이 없습니다.', 'ri-radar-line');
            return;
        }
        const byRule = new Map();
        edges.forEach(function (edge) {
            if (!byRule.has(edge.ruleId)) byRule.set(edge.ruleId, []);
            byRule.get(edge.ruleId).push(edge);
        });
        const riskSummary = state.ruleRisks.size
            ? '<div class="pm-rule-risk-banner"><i class="ri-error-warning-line"></i><div><strong>동일 우선순위 충돌 후보 ' + state.ruleRisks.size + '개</strong><span>의도한 덮어쓰기라면 우선순위를 다르게 지정하세요. 실제 평가는 높은 우선순위가 마지막에 적용됩니다.</span></div></div>'
            : '<div class="pm-rule-risk-banner pm-is-safe"><i class="ri-shield-check-line"></i><div><strong>동일 우선순위 충돌 없음</strong><span>런타임에서는 순환 규칙도 감지해 안전하게 계산을 중단합니다.</span></div></div>';
        const configurationHtml = Array.from(byRule.entries()).map(function (entry) {
            const rule = state.rules.find(function (item) { return item.id === entry[0]; });
            const risks = rule ? (state.ruleRisks.get(rule.id) || []) : [];
            return '<article class="pm-impact-rule' + (risks.length ? ' pm-has-risk' : '') + '"><header><div><code>' + pm.escapeHtml(rule ? rule.ruleCode : '') + '</code><strong>'
                + pm.escapeHtml(rule ? rule.ruleName : '규칙') + '</strong></div><span>P' + (rule ? rule.priority : '-') + '</span></header><div class="pm-impact-edges">'
                + entry[1].map(function (edge) {
                    return '<div class="pm-impact-edge"><span>' + pm.escapeHtml(edge.sourceGroupLabel) + '</span><i class="ri-arrow-right-line"></i><b>'
                        + pm.escapeHtml(edge.actionTypeLabel) + '</b><i class="ri-arrow-right-line"></i><span>' + pm.escapeHtml(edge.targetGroupLabel) + '</span></div>';
                }).join('') + '</div>' + (risks.length ? '<div class="pm-impact-risk">' + risks.map(function (risk) { return '<span>' + pm.escapeHtml(risk) + '</span>'; }).join('') + '</div>' : '') + '</article>';
        }).join('');
        const priceHtml = priceRules.map(function (rule) {
            const dependencies = priceImpactGroups(rule);
            const dependencyText = dependencies.length
                ? dependencies.map(function (group) { return group.label + ' (' + group.reason + ')'; }).join(' · ')
                : '제품 범위의 기본 가격';
            return '<article class="pm-impact-rule pm-is-price"><header><div><code>' + pm.escapeHtml(rule.priceRuleCode) + '</code><strong>'
                + pm.escapeHtml(rule.ruleName) + '</strong></div><span>P' + rule.priority + '</span></header>'
                + '<div class="pm-impact-edges"><div class="pm-impact-edge"><span>' + pm.escapeHtml(dependencyText)
                + '</span><i class="ri-arrow-right-line"></i><b>' + pm.escapeHtml(rule.ruleTypeLabel)
                + '</b><i class="ri-arrow-right-line"></i><span>' + pm.escapeHtml(rule.applyModeLabel) + '</span></div></div>'
                + '<div class="pm-price-impact-summary"><span>' + pm.escapeHtml(rule.summary) + '</span><small>'
                + pm.escapeHtml(rule.scopeProductName) + ' · ' + (rule.active ? '사용중' : '사용중지') + '</small></div></article>';
        }).join('');
        container.innerHTML = riskSummary
            + (configurationHtml ? '<div class="pm-impact-section-label"><i class="ri-git-branch-line"></i>질문·선택 영향</div>' + configurationHtml : '')
            + (priceHtml ? '<div class="pm-impact-section-label"><i class="ri-calculator-line"></i>가격 영향</div>' + priceHtml : '');
    }

    function priceImpactGroups(rule) {
        const result = [];
        function add(id, label, reason) {
            if (!id || result.some(function (item) { return item.id === id && item.reason === reason; })) return;
            const group = groupById(id);
            result.push({id: id, label: label || (group ? group.managementLabel : '옵션 그룹'), reason: reason});
        }
        if (rule.triggerValueId) {
            const triggerGroup = state.groups.find(function (group) {
                return (group.values || []).some(function (value) { return value.id === rule.triggerValueId; });
            });
            if (triggerGroup) add(triggerGroup.id, triggerGroup.managementLabel, '적용 조건');
        }
        add(rule.quantityGroupId, rule.quantityGroupLabel, '수량');
        add(rule.sourceGroupId, rule.sourceGroupLabel, '기준값');
        if (rule.matrixId) {
            const matrix = state.matrices.find(function (item) { return item.id === rule.matrixId; });
            if (matrix) {
                add(matrix.xGroupId, matrix.xGroupLabel, '가격표 X축');
                add(matrix.yGroupId, matrix.yGroupLabel, '가격표 Y축');
            }
        }
        return result;
    }

    async function runTest() {
        const productId = numberOrNull(document.getElementById('pm-test-product').value);
        if (!productId) return pm.toast('테스트할 제품을 선택해 주세요.', 'info');
        const target = document.getElementById('pm-test-result');
        target.innerHTML = '<div class="pm-test-loading"><span class="spinner-border spinner-border-sm"></span> 규칙과 가격을 계산하고 있습니다.</div>';
        try {
            const response = await pm.request(API + '/products/' + productId + '/evaluate', {method: 'POST', body: {inputs: []}});
            const result = response.data;
            const visible = (result.groups || []).filter(function (group) { return group.visible; });
            target.innerHTML = '<div class="pm-test-summary"><div><small>검증 상태</small><strong class="' + (result.valid ? 'text-success' : 'text-warning') + '">' + (result.valid ? '완료' : '고객 입력 필요') + '</strong></div><div><small>첫 공급가</small><strong>' + pm.money(result.price.supplyPrice) + '</strong></div><div><small>질문 그룹</small><strong>' + visible.length + '개</strong></div></div>'
                + ((result.firedRules || []).length ? '<div class="pm-test-block"><h6>즉시 적용된 규칙</h6>' + result.firedRules.map(function (rule) { return '<p><code>' + pm.escapeHtml(rule.ruleCode) + '</code>' + pm.escapeHtml(rule.explanation) + '</p>'; }).join('') + '</div>' : '')
                + ((result.errors || []).length ? '<div class="pm-test-block pm-test-errors"><h6>고객이 응답할 항목</h6>' + result.errors.map(function (error) { return '<p><i class="ri-checkbox-blank-circle-line"></i>' + pm.escapeHtml(error) + '</p>'; }).join('') + '</div>' : '')
                + '<div class="pm-test-block"><h6>계산 근거</h6>' + (result.price.lines || []).map(function (line) { return '<p><span>' + pm.escapeHtml(line.label) + '</span><b>' + pm.money(line.amount) + '</b><small>' + pm.escapeHtml(line.formula) + '</small></p>'; }).join('') + '</div>';
        } catch (error) { target.innerHTML = '<div class="alert alert-danger mb-0">' + pm.escapeHtml(error.message) + '</div>'; }
    }

    function updateCustomerPreviewLink() {
        const id = numberOrNull(document.getElementById('pm-test-product').value);
        const product = state.products.find(function (item) { return item.id === id; });
        const link = document.getElementById('pm-open-customer-preview');
        link.classList.toggle('disabled', !product);
        link.href = product ? '/product-spec/' + product.qrPublicToken : '#';
    }

    function bindStaticEvents() {
        document.querySelectorAll('[data-pm-workspace]').forEach(function (button) {
            button.addEventListener('click', function () { activateWorkspace(button.dataset.pmWorkspace); });
        });
        document.getElementById('pm-new-rule').addEventListener('click', newRule);
        document.getElementById('pm-add-condition').addEventListener('click', function () { collectRuleRows(); state.ruleDraft.conditions.push(blankCondition()); renderConditionRows(); });
        document.getElementById('pm-add-action').addEventListener('click', function () { collectRuleRows(); state.ruleDraft.actions.push(blankAction()); renderActionRows(); });
        document.getElementById('pm-rule-form').addEventListener('submit', saveRule);
        document.getElementById('pm-delete-rule').addEventListener('click', deleteRule);
        document.getElementById('pm-rule-search').addEventListener('input', pm.debounce(renderRuleList, 150));
        document.getElementById('pm-rule-scope-filter').addEventListener('change', renderRuleList);

        document.getElementById('pm-new-matrix').addEventListener('click', function () { openMatrix(null); });
        document.getElementById('pm-build-matrix-grid').addEventListener('click', buildMatrixGrid);
        document.getElementById('pm-matrix-form').addEventListener('submit', saveMatrix);
        document.getElementById('pm-delete-matrix').addEventListener('click', deleteMatrix);
        ['x', 'y'].forEach(function (axis) { document.getElementById('pm-matrix-' + axis + '-group').addEventListener('change', function () { renderMatrixFieldSelect(axis, null); }); });
        document.getElementById('pm-matrix-file-trigger').addEventListener('click', function () { document.getElementById('pm-matrix-file').click(); });
        document.getElementById('pm-matrix-file').addEventListener('change', function (event) { previewMatrixFile(event.target.files[0]); });
        const dropzone = document.getElementById('pm-matrix-file-trigger');
        ['dragenter', 'dragover'].forEach(function (name) { dropzone.addEventListener(name, function (event) { event.preventDefault(); dropzone.classList.add('pm-is-dragover'); }); });
        ['dragleave', 'drop'].forEach(function (name) { dropzone.addEventListener(name, function (event) { event.preventDefault(); dropzone.classList.remove('pm-is-dragover'); if (name === 'drop') previewMatrixFile(event.dataTransfer.files[0]); }); });
        document.querySelectorAll('[data-pm-matrix-source]').forEach(function (button) { button.addEventListener('click', function () { activateMatrixSource(button.dataset.pmMatrixSource); }); });
        document.querySelectorAll('[data-pm-toggle-box]').forEach(function (button) { button.addEventListener('click', function () { document.getElementById(button.dataset.pmToggleBox).classList.toggle('pm-is-open'); button.classList.toggle('pm-is-open'); }); });

        document.getElementById('pm-new-price-rule').addEventListener('click', function () { openPriceRule(null); });
        document.getElementById('pm-price-rule-type').addEventListener('change', function () { renderPriceRuleFields(null); });
        document.getElementById('pm-price-rule-form').addEventListener('submit', savePriceRule);
        document.getElementById('pm-delete-price-rule').addEventListener('click', deletePriceRule);
        document.getElementById('pm-impact-group-filter').addEventListener('change', renderImpactMap);
        document.getElementById('pm-test-product').addEventListener('change', updateCustomerPreviewLink);
        document.getElementById('pm-run-test').addEventListener('click', runTest);
        bindTour();
    }

    function activateWorkspace(name) {
        document.querySelectorAll('[data-pm-workspace]').forEach(function (item) { item.classList.toggle('pm-is-active', item.dataset.pmWorkspace === name); });
        document.querySelectorAll('[data-pm-panel]').forEach(function (item) { item.classList.toggle('pm-is-active', item.dataset.pmPanel === name); });
    }

    function activateMatrixSource(name) {
        document.querySelectorAll('[data-pm-matrix-source]').forEach(function (item) { item.classList.toggle('pm-is-active', item.dataset.pmMatrixSource === name); });
        document.querySelectorAll('[data-pm-matrix-source-panel]').forEach(function (item) { item.classList.toggle('pm-is-active', item.dataset.pmMatrixSourcePanel === name); });
    }

    function sourceFieldsFor(group) {
        if (!group) return ['SELECTED_VALUE'];
        if (group.inputType === 'NUMBER') return ['NUMBER_VALUE'];
        if (group.inputType === 'DIMENSION') return ['SELECTED_VALUE', 'WIDTH_MM', 'DEPTH_MM', 'HEIGHT_MM'];
        return ['SELECTED_VALUE'];
    }

    function sourceFieldOptionsForGroup(groupId, selected) {
        const fields = sourceFieldsFor(groupById(groupId)).filter(function (field) { return field !== 'SELECTED_VALUE'; });
        return optionMap(fields, sourceFieldLabels, fields.includes(selected) ? selected : fields[0]);
    }

    function groupById(id) { return state.groups.find(function (group) { return group.id === Number(id); }) || null; }
    function groupOptions(selected) {
        return state.groups.map(function (group) { return '<option value="' + group.id + '"' + (group.id === Number(selected) ? ' selected' : '') + '>' + pm.escapeHtml(group.managementLabel) + '</option>'; }).join('');
    }
    function valueOptions(group, selected, allowEmpty) {
        if (!group) return '<option value="">옵션 없음</option>';
        const values = (group.values || []).filter(function (value) { return value.active || value.id === Number(selected); });
        return (allowEmpty ? '<option value="">선택 안 함</option>' : '<option value="">옵션값 선택</option>') + values.map(function (value) {
            return '<option value="' + value.id + '"' + (value.id === Number(selected) ? ' selected' : '') + '>' + pm.escapeHtml(value.managementLabel + ' · ' + value.valueCode) + '</option>';
        }).join('');
    }
    function allValueOptions(selected, omitLeading) {
        const options = state.groups.filter(function (group) { return group.inputType === 'CHOICE' || group.inputType === 'DIMENSION'; }).map(function (group) {
            return '<optgroup label="' + pm.escapeHtml(group.managementLabel) + '">' + (group.values || []).filter(function (value) { return value.active || value.id === Number(selected); }).map(function (value) {
                return '<option value="' + value.id + '"' + (value.id === Number(selected) ? ' selected' : '') + '>' + pm.escapeHtml(value.managementLabel + ' · ' + value.valueCode) + '</option>';
            }).join('') + '</optgroup>';
        }).join('');
        return (omitLeading ? '' : '<option value="">옵션값 선택</option>') + options;
    }
    function numberGroupOptions(selected) {
        return '<option value="">수량 그룹 선택</option>' + state.groups.filter(function (group) { return group.inputType === 'NUMBER'; }).map(function (group) { return '<option value="' + group.id + '"' + (group.id === Number(selected) ? ' selected' : '') + '>' + pm.escapeHtml(group.managementLabel + (group.unitLabel ? ' (' + group.unitLabel + ')' : '')) + '</option>'; }).join('');
    }
    function numericSourceGroupOptions(selected) {
        return '<option value="">기준 그룹 선택</option>' + state.groups.filter(function (group) { return group.inputType === 'NUMBER' || group.inputType === 'DIMENSION'; }).map(function (group) { return '<option value="' + group.id + '"' + (group.id === Number(selected) ? ' selected' : '') + '>' + pm.escapeHtml(group.managementLabel) + '</option>'; }).join('');
    }
    function matrixOptions(selected) {
        return '<option value="">가격표 선택</option>' + state.matrices.filter(function (matrix) { return matrix.active || matrix.id === Number(selected); }).map(function (matrix) { return '<option value="' + matrix.id + '"' + (matrix.id === Number(selected) ? ' selected' : '') + '>' + pm.escapeHtml(matrix.matrixName + ' · ' + matrix.matrixCode) + '</option>'; }).join('');
    }
    function optionMap(values, labels, selected) { return values.map(function (value) { return '<option value="' + value + '"' + (value === selected ? ' selected' : '') + '>' + pm.escapeHtml(labels[value] || value) + '</option>'; }).join(''); }
    function valueOf(id) { const element = document.getElementById(id); return element ? element.value : ''; }
    function valueAttr(value) { return value == null ? '' : pm.escapeHtml(value); }
    function numberOrNull(value) { return value === null || value === undefined || value === '' ? null : Number(value); }
    function coord(x, y) { return String(Number(x)) + ':' + String(Number(y)); }
    function uniqueSorted(values) { return Array.from(new Set(values.map(Number))).sort(function (a, b) { return a - b; }); }
    function parseAxis(raw, name) {
        const result = String(raw || '').split(/[,\s]+/).filter(Boolean).map(Number);
        if (!result.length || result.some(function (value) { return !Number.isFinite(value) || value < 0; })) throw new Error(name + '은 0 이상의 숫자를 쉼표로 구분해 입력해 주세요.');
        if (new Set(result).size !== result.length) throw new Error(name + '에 중복값이 있습니다.');
        if (result.length > 200) throw new Error(name + '은 최대 200개까지 사용할 수 있습니다.');
        return result.sort(function (a, b) { return a - b; });
    }
    function empty(message, icon) { return '<div class="pm-context-empty pm-context-empty-sm"><span><i class="' + icon + '"></i></span><p>' + pm.escapeHtml(message) + '</p></div>'; }

    function bindTour() {
        const steps = [
            {selector: '[data-pm-tour="intro"]', title: '하나의 제품 구성 기준', text: '질문 흐름, 가격표, 가격 계산을 분리해 수정 이력과 책임 범위를 명확하게 관리합니다.'},
            {selector: '[data-pm-tour="tabs"]', title: '필요한 작업만 펼칩니다', text: '조건, 가격표, 계산식, 영향도는 탭으로 분리되어 긴 폼을 한 화면에 나열하지 않습니다.'},
            {selector: '[data-pm-tour="rule-workspace"]', title: 'IF → THEN 문장형 규칙', text: '캔버스 선 연결 대신 읽을 수 있는 문장 단위로 등록하고 우선순위와 충돌을 검증합니다.'},
            {selector: '[data-pm-tour="impact-workspace"]', title: '영향도와 고객 검증', text: '각 옵션이 어디에 영향을 주는지 확인하고 저장된 제품을 실제 고객 질문 화면에서 시험할 수 있습니다.'}
        ];
        const overlay = document.getElementById('pm-tour-overlay');
        function renderTour() {
            const step = steps[state.tourIndex];
            if (step.selector.includes('impact-workspace')) activateWorkspace('impact'); else if (state.tourIndex === 2) activateWorkspace('rules');
            const target = document.querySelector(step.selector);
            if (!target) return;
            target.scrollIntoView({behavior: 'smooth', block: 'center'});
            window.setTimeout(function () {
                const box = target.getBoundingClientRect();
                const focus = overlay.querySelector('.pm-tour-focus');
                focus.style.left = Math.max(8, box.left - 8) + 'px'; focus.style.top = Math.max(8, box.top - 8) + 'px';
                focus.style.width = Math.min(window.innerWidth - 16, box.width + 16) + 'px'; focus.style.height = Math.min(window.innerHeight - 16, box.height + 16) + 'px';
                document.getElementById('pm-tour-step').textContent = (state.tourIndex + 1) + ' / ' + steps.length;
                document.getElementById('pm-tour-title').textContent = step.title;
                document.getElementById('pm-tour-text').textContent = step.text;
                document.getElementById('pm-tour-prev').disabled = state.tourIndex === 0;
                document.getElementById('pm-tour-next').textContent = state.tourIndex === steps.length - 1 ? '완료' : '다음';
            }, 220);
        }
        function openTour() { state.tourIndex = 0; overlay.classList.add('pm-is-visible'); overlay.setAttribute('aria-hidden', 'false'); renderTour(); }
        function closeTour() { overlay.classList.remove('pm-is-visible'); overlay.setAttribute('aria-hidden', 'true'); }
        document.getElementById('pm-start-automation-tour').addEventListener('click', openTour);
        document.getElementById('pm-tour-prev').addEventListener('click', function () { if (state.tourIndex > 0) { state.tourIndex--; renderTour(); } });
        document.getElementById('pm-tour-next').addEventListener('click', function () { if (state.tourIndex >= steps.length - 1) closeTour(); else { state.tourIndex++; renderTour(); } });
        document.getElementById('pm-tour-close').addEventListener('click', closeTour);
    }

    initialize();
})(window, document);
