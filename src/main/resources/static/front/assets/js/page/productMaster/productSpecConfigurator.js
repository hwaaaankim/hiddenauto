(function (window, document) {
    'use strict';

    const token = document.body.dataset.pmsPublicToken;
    if (!token) return;

    const elements = {
        chat: document.getElementById('pms-chat-stream'),
        composer: document.getElementById('pms-answer-composer'),
        progressLabel: document.getElementById('pms-progress-label'),
        progressPercent: document.getElementById('pms-progress-percent'),
        progressBar: document.getElementById('pms-progress-bar'),
        totalPrice: document.getElementById('pms-total-price'),
        basePrice: document.getElementById('pms-base-price'),
        optionPrice: document.getElementById('pms-option-price'),
        supplyPrice: document.getElementById('pms-supply-price'),
        vatPrice: document.getElementById('pms-vat-price'),
        priceLines: document.getElementById('pms-price-lines'),
        stock: document.getElementById('pms-stock-value'),
        selectionList: document.getElementById('pms-selection-list'),
        answerCount: document.getElementById('pms-answer-count'),
        alerts: document.getElementById('pms-alert-stack'),
        complete: document.getElementById('pms-complete-button'),
        reset: document.getElementById('pms-reset-button'),
        modal: document.getElementById('pms-confirm-modal'),
        confirmCode: document.getElementById('pms-confirm-code'),
        confirmSummary: document.getElementById('pms-confirm-summary'),
        copySummary: document.getElementById('pms-copy-summary')
    };

    const state = {
        data: null,
        inputs: new Map(),
        skipped: new Set(),
        editGroupId: null,
        loading: false,
        copyText: ''
    };

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    function money(value) {
        return new Intl.NumberFormat('ko-KR').format(Number(value || 0)) + '원';
    }

    function numberText(value) {
        return new Intl.NumberFormat('ko-KR', {maximumFractionDigits: 3}).format(Number(value || 0));
    }

    async function request(path, options) {
        const config = Object.assign({
            credentials: 'same-origin',
            headers: {'Accept': 'application/json'}
        }, options || {});
        if (config.body && typeof config.body !== 'string') {
            config.headers['Content-Type'] = 'application/json';
            config.body = JSON.stringify(config.body);
        }
        let response;
        try {
            response = await fetch('/product-spec/api/' + encodeURIComponent(token) + path, config);
        } catch (error) {
            throw new Error('서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.');
        }
        const payload = await response.json().catch(function () {
            return {success: false, message: '서버 응답을 해석할 수 없습니다.'};
        });
        if (!response.ok || payload.success === false) {
            throw new Error(payload.message || '제품 구성을 계산하지 못했습니다.');
        }
        return payload.data;
    }

    function blankInput(group) {
        return {
            groupId: group.groupId,
            valueIds: [],
            widthMm: null,
            depthMm: null,
            heightMm: null,
            numberValue: null,
            textValue: null
        };
    }

    function inputFromGroup(group) {
        return {
            groupId: group.groupId,
            valueIds: Array.isArray(group.selectedValueIds) ? group.selectedValueIds.slice() : [],
            widthMm: group.widthMm == null ? null : Number(group.widthMm),
            depthMm: group.depthMm == null ? null : Number(group.depthMm),
            heightMm: group.heightMm == null ? null : Number(group.heightMm),
            numberValue: group.numberValue == null ? null : Number(group.numberValue),
            textValue: group.textValue || null
        };
    }

    function hasServerAnswer(group) {
        if (group.inputType === 'NUMBER') return group.numberValue != null;
        if (group.inputType === 'TEXT') return Boolean(group.textValue);
        return Array.isArray(group.selectedValueIds) && group.selectedValueIds.length > 0;
    }

    function initializeInputs(data) {
        state.inputs.clear();
        state.skipped.clear();
        data.groups.forEach(function (group) {
            if (group.locked) return;
            if (group.groupType === 'ADD_ON') {
                state.inputs.set(group.groupId, blankInput(group));
                return;
            }
            if (hasServerAnswer(group)) state.inputs.set(group.groupId, inputFromGroup(group));
        });
    }

    function reconcileInputs(data) {
        const existingIds = new Set(data.groups.map(function (group) { return group.groupId; }));
        Array.from(state.inputs.keys()).forEach(function (groupId) {
            if (!existingIds.has(groupId)) state.inputs.delete(groupId);
        });
        data.groups.forEach(function (group) {
            if (group.locked) {
                state.inputs.delete(group.groupId);
                return;
            }
            const current = state.inputs.get(group.groupId);
            if (current) {
                current.valueIds = (group.selectedValueIds || []).slice();
                current.widthMm = group.widthMm;
                current.depthMm = group.depthMm;
                current.heightMm = group.heightMm;
                current.numberValue = group.numberValue;
                current.textValue = group.textValue;
            } else if (hasServerAnswer(group)) {
                state.inputs.set(group.groupId, inputFromGroup(group));
            }
            if (group.required || hasServerAnswer(group)) state.skipped.delete(group.groupId);
            if (!group.visible) {
                state.inputs.delete(group.groupId);
                state.skipped.delete(group.groupId);
            }
        });
    }

    function selectedValues(group) {
        const ids = new Set(group.selectedValueIds || []);
        return (group.values || []).filter(function (value) { return ids.has(value.id); });
    }

    function selectedDimensionType(group) {
        const values = selectedValues(group);
        if (!values.length) return null;
        const type = values[0].dimensionType;
        if (type === 'CUSTOM' && group.inputType === 'DIMENSION') {
            return group.customDimensionType || 'WIDTH_DEPTH_HEIGHT';
        }
        return type;
    }

    function isAnswered(group) {
        if (group.locked) return true;
        if (state.skipped.has(group.groupId)) return true;
        if (group.inputType === 'NUMBER') return group.numberValue != null && group.numberValue !== '';
        if (group.inputType === 'TEXT') return Boolean(group.textValue && String(group.textValue).trim());
        if (!group.selectedValueIds || !group.selectedValueIds.length) return false;
        const selected = selectedValues(group);
        const customNonDimension = selected.some(function (value) {
            return value.dimensionType === 'CUSTOM' && group.inputType !== 'DIMENSION';
        });
        if (customNonDimension && !(group.textValue && String(group.textValue).trim())) return false;
        if (group.inputType === 'DIMENSION') {
            const type = selectedDimensionType(group);
            if (type === 'WIDTH_HEIGHT') return validDimension(group.widthMm) && validDimension(group.heightMm);
            if (type === 'WIDTH_DEPTH_HEIGHT') {
                return validDimension(group.widthMm) && validDimension(group.depthMm) && validDimension(group.heightMm);
            }
        }
        return true;
    }

    function validDimension(value) {
        const number = Number(value);
        return Number.isFinite(number) && number >= 1 && number <= 100000;
    }

    function visibleGroups() {
        return state.data ? state.data.groups.filter(function (group) { return group.visible; }) : [];
    }

    function currentGroup() {
        const groups = visibleGroups();
        if (state.editGroupId != null) {
            const edited = groups.find(function (group) { return group.groupId === state.editGroupId && !group.locked; });
            if (edited) return edited;
            state.editGroupId = null;
        }
        return groups.find(function (group) { return !group.locked && !isAnswered(group); }) || null;
    }

    function answerText(group) {
        if (state.skipped.has(group.groupId)) return '선택 안 함';
        if (group.inputType === 'NUMBER') {
            return numberText(group.numberValue) + (group.unitLabel || '');
        }
        if (group.inputType === 'TEXT') return group.textValue || '-';
        const labels = selectedValues(group).map(function (value) { return value.label; });
        let answer = labels.join(', ') || '선택 안 함';
        if (group.inputType === 'DIMENSION') {
            const type = selectedDimensionType(group);
            const dimensions = [];
            if (group.widthMm != null) dimensions.push('W ' + numberText(group.widthMm));
            if (type === 'WIDTH_DEPTH_HEIGHT' && group.depthMm != null) dimensions.push('D ' + numberText(group.depthMm));
            if (group.heightMm != null) dimensions.push('H ' + numberText(group.heightMm));
            if (dimensions.length) answer += ' · ' + dimensions.join(' × ') + ' mm';
        }
        if (group.textValue) answer += ' · ' + group.textValue;
        return answer;
    }

    function messageRow(group, includeAnswer) {
        const guide = group.guide ? '<small>' + escapeHtml(group.guide) + '</small>' : '';
        let html = '<div class="pms-message-row"><span class="pms-bot-avatar">HB</span>'
            + '<div class="pms-bubble pms-bot-bubble"><strong>' + escapeHtml(group.question || group.label) + '</strong>'
            + (group.required ? '필수 선택 항목입니다.' : '필요한 경우 선택해 주세요.') + guide + '</div></div>';
        if (includeAnswer) {
            html += '<div class="pms-message-row pms-user-row"><div class="pms-bubble pms-user-bubble">'
                + escapeHtml(answerText(group))
                + (group.locked ? '<span class="pms-locked-label">규격 고정</span>' : '<button class="pms-answer-edit" type="button" data-pms-edit-group="' + group.groupId + '">수정</button>')
                + '</div></div>';
        }
        return html;
    }

    function renderConversation() {
        const groups = visibleGroups();
        const active = currentGroup();
        let html = '<div class="pms-message-row"><span class="pms-bot-avatar">HB</span>'
            + '<div class="pms-bubble pms-bot-bubble"><strong>안녕하세요. 제품 구성을 도와드릴게요.</strong>'
            + '선택에 따라 필요한 질문만 이어지고, 오른쪽 견적과 계산 근거가 바로 갱신됩니다.</div></div>';
        for (const group of groups) {
            if (active && group.groupId === active.groupId) {
                html += messageRow(group, false);
                break;
            }
            if (group.locked || isAnswered(group)) html += messageRow(group, true);
            else break;
        }
        (state.data.notices || []).forEach(function (notice) {
            html += '<div class="pms-rule-notice"><strong>구성 안내</strong> · ' + escapeHtml(notice) + '</div>';
        });
        if (!active) {
            html += '<div class="pms-chat-complete">필요한 질문에 모두 답했습니다. 구성 내용과 계산 금액을 확인해 주세요.</div>';
        }
        elements.chat.innerHTML = html;
        elements.chat.querySelectorAll('[data-pms-edit-group]').forEach(function (button) {
            button.addEventListener('click', function () {
                state.editGroupId = Number(button.dataset.pmsEditGroup);
                render();
                elements.composer.scrollIntoView({behavior: 'smooth', block: 'nearest'});
            });
        });
        requestAnimationFrame(function () {
            elements.chat.scrollTop = elements.chat.scrollHeight;
        });
    }

    function imageStrip(images) {
        if (!images || !images.length) return '';
        return '<div class="pms-group-media">' + images.map(function (image) {
            return '<img src="' + escapeHtml(image.contentPath) + '" alt="' + escapeHtml(image.originalFilename || '옵션 안내 이미지') + '" loading="lazy">';
        }).join('') + '</div>';
    }

    function choiceMarkup(group) {
        const multiple = group.selectionMode === 'MULTIPLE';
        const inputType = multiple ? 'checkbox' : 'radio';
        const selectedIds = new Set(group.selectedValueIds || []);
        return '<div class="pms-choice-grid">' + (group.values || []).map(function (value) {
            const image = value.images && value.images.length
                ? '<img class="pms-choice-image" src="' + escapeHtml(value.images[0].contentPath) + '" alt="">'
                : '';
            const guide = value.guide ? '<small>' + escapeHtml(value.guide) + '</small>' : '';
            const price = value.priceAdjustment ? '<small>' + (value.priceAdjustment > 0 ? '+' : '') + money(value.priceAdjustment) + '</small>' : '';
            return '<div class="pms-choice"><input id="pms-choice-' + group.groupId + '-' + value.id + '" type="' + inputType
                + '" name="pms-choice-' + group.groupId + '" value="' + value.id + '" '
                + (selectedIds.has(value.id) ? 'checked ' : '') + (value.disabled ? 'disabled ' : '') + '>'
                + '<label for="pms-choice-' + group.groupId + '-' + value.id + '">' + image
                + '<span class="pms-choice-copy"><span>' + escapeHtml(value.label) + '</span>' + guide + price + '</span></label></div>';
        }).join('') + '</div>';
    }

    function numericField(label, name, value, unit, min, max, step) {
        return '<div class="pms-field pms-field-unit"><label for="' + name + '">' + escapeHtml(label) + '</label>'
            + '<input id="' + name + '" data-pms-answer-field="' + name + '" type="number" value="' + escapeHtml(value == null ? '' : value)
            + '" min="' + escapeHtml(min == null ? 1 : min) + '" max="' + escapeHtml(max == null ? 100000 : max)
            + '" step="' + escapeHtml(step == null ? 1 : step) + '"><span>' + escapeHtml(unit || '') + '</span></div>';
    }

    function customFields(group) {
        if (group.inputType === 'DIMENSION') {
            const type = composerSelectedDimensionType(group);
            if (type !== 'WIDTH_HEIGHT' && type !== 'WIDTH_DEPTH_HEIGHT') return '';
            let fields = numericField('W 가로', 'widthMm', group.widthMm, 'mm', 1, 100000, 1);
            if (type === 'WIDTH_DEPTH_HEIGHT') {
                fields += numericField('D 깊이', 'depthMm', group.depthMm, 'mm', 1, 100000, 1);
            }
            fields += numericField('H 높이', 'heightMm', group.heightMm, 'mm', 1, 100000, 1);
            return '<div class="pms-custom-fields"><p>실제 제작 치수를 mm 단위로 입력해 주세요. 가격표는 설정된 올림 단위를 적용합니다.</p>'
                + '<div class="pms-input-grid">' + fields + '</div></div>';
        }
        const selectedIds = composerSelectedIds(group);
        const hasCustom = (group.values || []).some(function (value) {
            return selectedIds.includes(value.id) && value.dimensionType === 'CUSTOM';
        });
        if (!hasCustom) return '';
        return '<div class="pms-custom-fields"><div class="pms-field"><label for="pms-custom-text">비규격 요청 내용</label>'
            + '<textarea id="pms-custom-text" maxlength="500" placeholder="원하는 사양을 구체적으로 입력해 주세요.">'
            + escapeHtml(group.textValue || '') + '</textarea></div></div>';
    }

    function composerSelectedIds(group) {
        const choices = Array.from(elements.composer.querySelectorAll('.pms-choice input'));
        if (!choices.length) return (group && group.selectedValueIds ? group.selectedValueIds.slice() : []);
        return choices.filter(function (input) { return input.checked; }).map(function (input) {
            return Number(input.value);
        });
    }

    function composerSelectedDimensionType(group) {
        const ids = composerSelectedIds(group);
        const value = (group.values || []).find(function (item) { return ids.includes(item.id); });
        if (!value) return null;
        return value.dimensionType === 'CUSTOM'
            ? (group.customDimensionType || 'WIDTH_DEPTH_HEIGHT')
            : value.dimensionType;
    }

    function renderComposer() {
        const group = currentGroup();
        if (!group) {
            elements.composer.innerHTML = '<div class="pms-composer-head"><strong>모든 구성 질문을 완료했습니다.</strong></div>';
            return;
        }
        const marker = group.required ? '<span class="pms-required-label">필수</span>' : '<span>선택</span>';
        let body = imageStrip(group.images);
        if (group.inputType === 'CHOICE' || group.inputType === 'DIMENSION') {
            body += choiceMarkup(group);
            body += customFields(group);
        } else if (group.inputType === 'NUMBER') {
            body += '<div class="pms-input-grid">' + numericField(
                group.label,
                'numberValue',
                group.numberValue,
                group.unitLabel || '',
                group.minimumValue,
                group.maximumValue,
                group.stepValue
            ) + '</div>';
        } else {
            body += '<div class="pms-field"><label for="pms-text-value">' + escapeHtml(group.label) + '</label>'
                + '<textarea id="pms-text-value" maxlength="500" placeholder="내용을 입력해 주세요.">'
                + escapeHtml(group.textValue || '') + '</textarea></div>';
        }
        elements.composer.innerHTML = '<div class="pms-composer-head"><strong>' + escapeHtml(group.label) + '</strong>' + marker + '</div>'
            + body + '<p class="pms-inline-error" id="pms-inline-error" hidden></p>'
            + '<div class="pms-composer-actions">'
            + (!group.required ? '<button class="pms-skip-button" type="button" id="pms-skip-question">선택 안 함</button>' : '')
            + '<button class="pms-next-button" type="button" id="pms-submit-answer">' + (state.editGroupId ? '변경 적용' : '선택하고 다음') + '</button>'
            + '</div>';
        elements.composer.querySelectorAll('.pms-choice input').forEach(function (input) {
            input.addEventListener('change', function () {
                if (input.checked && group.selectionMode === 'MULTIPLE') {
                    const selectedValue = (group.values || []).find(function (value) {
                        return value.id === Number(input.value);
                    });
                    elements.composer.querySelectorAll('.pms-choice input').forEach(function (other) {
                        if (other === input) return;
                        const otherValue = (group.values || []).find(function (value) {
                            return value.id === Number(other.value);
                        });
                        if ((selectedValue && selectedValue.dimensionType === 'CUSTOM')
                            || (otherValue && otherValue.dimensionType === 'CUSTOM')) {
                            other.checked = false;
                        }
                    });
                }
                const old = elements.composer.querySelector('.pms-custom-fields');
                const markup = customFields(group);
                if (old) old.remove();
                if (markup) {
                    const error = document.getElementById('pms-inline-error');
                    error.insertAdjacentHTML('beforebegin', markup);
                }
            });
        });
        const submit = document.getElementById('pms-submit-answer');
        if (submit) submit.addEventListener('click', function () { submitAnswer(group); });
        const skip = document.getElementById('pms-skip-question');
        if (skip) skip.addEventListener('click', function () { skipAnswer(group); });
    }

    function fieldValue(id) {
        const input = document.getElementById(id);
        return input && input.value !== '' ? input.value : null;
    }

    function composerInput(group) {
        const input = blankInput(group);
        if (group.inputType === 'CHOICE' || group.inputType === 'DIMENSION') {
            input.valueIds = composerSelectedIds(group);
            input.widthMm = asNullableNumber(fieldValue('widthMm'));
            input.depthMm = asNullableNumber(fieldValue('depthMm'));
            input.heightMm = asNullableNumber(fieldValue('heightMm'));
            const customText = document.getElementById('pms-custom-text');
            input.textValue = customText && customText.value.trim() ? customText.value.trim() : null;
        } else if (group.inputType === 'NUMBER') {
            input.numberValue = asNullableNumber(fieldValue('numberValue'));
        } else {
            const text = document.getElementById('pms-text-value');
            input.textValue = text && text.value.trim() ? text.value.trim() : null;
        }
        return input;
    }

    function asNullableNumber(value) {
        if (value == null || value === '') return null;
        const number = Number(value);
        return Number.isFinite(number) ? number : null;
    }

    function validateComposer(group, input) {
        if ((group.inputType === 'CHOICE' || group.inputType === 'DIMENSION') && group.required && !input.valueIds.length) {
            return group.label + '을(를) 선택해 주세요.';
        }
        if (group.inputType === 'NUMBER') {
            if (group.required && input.numberValue == null) return group.label + '을(를) 입력해 주세요.';
            if (input.numberValue != null && group.minimumValue != null && input.numberValue < Number(group.minimumValue)) {
                return group.label + '은(는) ' + group.minimumValue + (group.unitLabel || '') + ' 이상이어야 합니다.';
            }
            if (input.numberValue != null && group.maximumValue != null && input.numberValue > Number(group.maximumValue)) {
                return group.label + '은(는) ' + group.maximumValue + (group.unitLabel || '') + ' 이하여야 합니다.';
            }
            if (input.numberValue != null && Number(group.stepValue || 0) > 0) {
                const start = group.minimumValue == null ? 0 : Number(group.minimumValue);
                const quotient = (input.numberValue - start) / Number(group.stepValue);
                if (Math.abs(quotient - Math.round(quotient)) > 1e-8) {
                    return group.label + '은(는) ' + group.stepValue + (group.unitLabel || '') + ' 단위로 입력해 주세요.';
                }
            }
        }
        if (group.inputType === 'TEXT' && group.required && !input.textValue) return group.label + '을(를) 입력해 주세요.';
        const selected = (group.values || []).filter(function (value) { return input.valueIds.includes(value.id); });
        if (selected.some(function (value) { return value.dimensionType === 'CUSTOM'; })
            && group.inputType !== 'DIMENSION' && !input.textValue) {
            return '비규격 요청 내용을 입력해 주세요.';
        }
        if (group.inputType === 'DIMENSION' && input.valueIds.length) {
            const type = composerSelectedDimensionType(group);
            if (type === 'WIDTH_HEIGHT' && (!validDimension(input.widthMm) || !validDimension(input.heightMm))) {
                return 'W와 H 치수를 모두 입력해 주세요.';
            }
            if (type === 'WIDTH_DEPTH_HEIGHT'
                && (!validDimension(input.widthMm) || !validDimension(input.depthMm) || !validDimension(input.heightMm))) {
                return 'W, D, H 치수를 모두 입력해 주세요.';
            }
        }
        return null;
    }

    function showInlineError(message) {
        const element = document.getElementById('pms-inline-error');
        if (!element) return;
        element.textContent = message;
        element.hidden = false;
    }

    async function submitAnswer(group) {
        const input = composerInput(group);
        const error = validateComposer(group, input);
        if (error) {
            showInlineError(error);
            return;
        }
        state.inputs.set(group.groupId, input);
        state.skipped.delete(group.groupId);
        state.editGroupId = null;
        await evaluate();
    }

    async function skipAnswer(group) {
        state.inputs.set(group.groupId, blankInput(group));
        state.skipped.add(group.groupId);
        state.editGroupId = null;
        await evaluate();
    }

    async function evaluate() {
        if (state.loading) return;
        state.loading = true;
        elements.composer.classList.add('pms-is-loading');
        try {
            const data = await request('/evaluate', {
                method: 'POST',
                body: {inputs: Array.from(state.inputs.values())}
            });
            state.data = data;
            reconcileInputs(data);
            render();
        } catch (error) {
            showFatal(error.message);
        } finally {
            state.loading = false;
            elements.composer.classList.remove('pms-is-loading');
        }
    }

    function renderProgress() {
        const groups = visibleGroups();
        const answered = groups.filter(isAnswered).length;
        const percent = groups.length ? Math.round(answered / groups.length * 100) : 100;
        elements.progressLabel.textContent = answered + ' / ' + groups.length + ' 항목 완료';
        elements.progressPercent.textContent = percent + '%';
        elements.progressBar.style.width = percent + '%';
    }

    function renderPrice() {
        const price = state.data.price || {};
        const lines = price.lines || [];
        const additions = Number(price.supplyPrice || 0) - Number(price.baseSupplyPrice || 0);
        elements.totalPrice.textContent = money(price.totalPrice);
        elements.basePrice.textContent = money(price.baseSupplyPrice);
        elements.optionPrice.textContent = (additions > 0 ? '+' : '') + money(additions);
        elements.supplyPrice.textContent = money(price.supplyPrice);
        elements.vatPrice.textContent = money(price.vatAmount) + ' (' + numberText(price.vatRate) + '%)';
        elements.stock.textContent = Number(state.data.currentStock || 0) > 0
            ? numberText(state.data.currentStock) + '개'
            : '재고 확인 필요';
        if (!lines.length && !(price.explanations || []).length) {
            elements.priceLines.innerHTML = '<p class="pms-quote-note">추가로 적용된 가격 규칙이 없습니다.</p>';
            return;
        }
        let html = lines.map(function (line) {
            return '<div class="pms-price-line"><div><span>' + escapeHtml(line.label) + '</span><strong>'
                + (line.amount > 0 ? '+' : '') + money(line.amount) + '</strong></div>'
                + '<small>' + escapeHtml(line.formula || '등록 가격 규칙') + '</small></div>';
        }).join('');
        (price.explanations || []).forEach(function (explanation) {
            html += '<div class="pms-price-line"><small>' + escapeHtml(explanation) + '</small></div>';
        });
        elements.priceLines.innerHTML = html;
    }

    function renderSummary() {
        const answered = visibleGroups().filter(function (group) {
            return isAnswered(group) && !state.skipped.has(group.groupId);
        });
        elements.answerCount.textContent = answered.length + '개';
        if (!answered.length) {
            elements.selectionList.innerHTML = '<p>답변하면 여기에 요약됩니다.</p>';
            return;
        }
        elements.selectionList.innerHTML = answered.map(function (group) {
            return '<div class="pms-selection-item"><span>' + escapeHtml(group.label) + '</span><strong>'
                + escapeHtml(answerText(group)) + '</strong></div>';
        }).join('');
    }

    function renderAlerts() {
        let html = '';
        (state.data.errors || []).forEach(function (message) {
            html += '<div class="pms-alert">' + escapeHtml(message) + '</div>';
        });
        (state.data.warnings || []).forEach(function (message) {
            html += '<div class="pms-alert pms-warning">' + escapeHtml(message) + '</div>';
        });
        elements.alerts.innerHTML = html;
    }

    function render() {
        renderProgress();
        renderConversation();
        renderComposer();
        renderPrice();
        renderSummary();
        renderAlerts();
        const ready = !currentGroup() && state.data.valid && !state.loading;
        elements.complete.disabled = !ready;
    }

    function showFatal(message) {
        elements.chat.innerHTML = '<div class="pms-message-row"><span class="pms-bot-avatar">!</span>'
            + '<div class="pms-bubble pms-bot-bubble"><strong>구성을 불러오지 못했습니다.</strong>'
            + escapeHtml(message) + '<small>잠시 후 새로고침하거나 관리팀에 문의해 주세요.</small></div></div>';
        elements.composer.innerHTML = '<button class="pms-next-button" type="button" id="pms-retry">다시 시도</button>';
        const retry = document.getElementById('pms-retry');
        if (retry) retry.addEventListener('click', load);
    }

    function fingerprint() {
        const normalized = Array.from(state.inputs.values())
            .sort(function (left, right) { return left.groupId - right.groupId; })
            .map(function (input) {
                return [input.groupId, (input.valueIds || []).slice().sort().join('.'), input.widthMm || '',
                    input.depthMm || '', input.heightMm || '', input.numberValue || '', input.textValue || ''].join(':');
            }).join('|');
        let hash = 2166136261;
        for (let index = 0; index < normalized.length; index += 1) {
            hash ^= normalized.charCodeAt(index);
            hash = Math.imul(hash, 16777619);
        }
        return 'CFG-' + (hash >>> 0).toString(36).toUpperCase().padStart(7, '0').slice(-7);
    }

    function openConfirmation() {
        const groups = visibleGroups().filter(function (group) {
            return isAnswered(group) && !state.skipped.has(group.groupId);
        });
        const configCode = state.data.catalogCode + ' / ' + fingerprint();
        elements.confirmCode.textContent = configCode;
        elements.confirmSummary.innerHTML = groups.map(function (group) {
            return '<div class="pms-selection-item"><span>' + escapeHtml(group.label) + '</span><strong>'
                + escapeHtml(answerText(group)) + '</strong></div>';
        }).join('');
        state.copyText = [
            '[HiddenBath 제품 구성]',
            state.data.productName + ' (' + state.data.catalogCode + ')',
            '구성코드: ' + configCode,
            groups.map(function (group) { return '- ' + group.label + ': ' + answerText(group); }).join('\n'),
            '공급가: ' + money(state.data.price.supplyPrice),
            'VAT 포함: ' + money(state.data.price.totalPrice)
        ].join('\n');
        elements.modal.setAttribute('aria-hidden', 'false');
        document.body.style.overflow = 'hidden';
    }

    function closeConfirmation() {
        elements.modal.setAttribute('aria-hidden', 'true');
        document.body.style.overflow = '';
    }

    async function copySummary() {
        try {
            await navigator.clipboard.writeText(state.copyText);
            elements.copySummary.textContent = '복사되었습니다';
            window.setTimeout(function () { elements.copySummary.textContent = '구성 내용 복사'; }, 1500);
        } catch (error) {
            const area = document.createElement('textarea');
            area.value = state.copyText;
            document.body.appendChild(area);
            area.select();
            document.execCommand('copy');
            area.remove();
        }
    }

    async function reset() {
        state.editGroupId = null;
        await load();
    }

    async function load() {
        state.loading = true;
        elements.chat.innerHTML = '<div class="pms-chat-loading"><span class="pms-bot-avatar">HB</span>'
            + '<div class="pms-bubble pms-bot-bubble"><span class="pms-typing"><i></i><i></i><i></i></span>'
            + '제품 구성을 불러오고 있습니다.</div></div>';
        try {
            const data = await request('/schema');
            state.data = data;
            initializeInputs(data);
            if (state.inputs.size) {
                const evaluated = await request('/evaluate', {
                    method: 'POST',
                    body: {inputs: Array.from(state.inputs.values())}
                });
                state.data = evaluated;
                reconcileInputs(evaluated);
            }
            render();
        } catch (error) {
            showFatal(error.message);
        } finally {
            state.loading = false;
        }
    }

    elements.reset.addEventListener('click', reset);
    elements.complete.addEventListener('click', openConfirmation);
    elements.copySummary.addEventListener('click', copySummary);
    elements.modal.querySelectorAll('[data-pms-close-modal]').forEach(function (button) {
        button.addEventListener('click', closeConfirmation);
    });
    elements.modal.addEventListener('click', function (event) {
        if (event.target === elements.modal) closeConfirmation();
    });
    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && elements.modal.getAttribute('aria-hidden') === 'false') closeConfirmation();
    });

    load();
})(window, document);
