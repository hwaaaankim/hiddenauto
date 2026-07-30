/* =========================================================
   생산팀 사용자별 발주 확인 처리
   /administration/assets/team/production/productionOrderCheck.js

   - 오더 + 생산팀 사용자별로 확인상태를 독립 관리합니다.
   - 사용자 본인이 확인한 이후 발생한 변경만 REVISED_AFTER_CHECK로 표시합니다.
   - 재확인 시 서버가 반환한 변경내역을 한 번만 안내합니다.
   ========================================================= */
(function () {
    'use strict';

    const config = window.teamProductionOverviewConfig || {};
    const pendingRequests = new Map();

    const CHECK_STATE = {
        REVISED_AFTER_CHECK: 'REVISED_AFTER_CHECK',
        UNCHECKED: 'UNCHECKED',
        CHECKED: 'CHECKED'
    };

    const CHECK_STATE_LABEL = {
        REVISED_AFTER_CHECK: '재수정',
        UNCHECKED: '미확인',
        CHECKED: '확인'
    };

    const CHECK_STATE_RANK = {
        REVISED_AFTER_CHECK: 0,
        UNCHECKED: 1,
        CHECKED: 2
    };

    window.TeamProductionOrderCheck = {
        mark: markOrderChecked,
        markLocal: markLocalChecked,
        isChecked: isOrderChecked,
        isNeedCheck: isNeedProductionCheck,
        getState: getOrderCheckState,
        getStateLabel: getCheckStateLabel,
        sortUncheckedFirst: sortUncheckedFirst,
        normalizeState: normalizeCheckState,
        markObjectChecked: markOrderObjectChecked
    };

    async function markOrderChecked(orderId) {
        const id = toText(orderId);
        if (!id) return null;

        if (isOrderChecked(id)) {
            return null;
        }

        if (pendingRequests.has(id)) {
            return pendingRequests.get(id);
        }

        const promise = executeMarkRequest(id)
            .finally(function () {
                pendingRequests.delete(id);
            });

        pendingRequests.set(id, promise);
        return promise;
    }

    async function executeMarkRequest(id) {
        try {
            const response = await fetch(buildCheckUrl(id), {
                method: 'POST',
                credentials: 'same-origin',
                headers: buildHeaders()
            });

            const data = await readResponsePayload(response);

            if (!response.ok) {
                throw new Error(resolveMessage(data, '확인 처리에 실패했습니다. status=' + response.status));
            }

            markLocalChecked(id, data);
            showRevisionNotices(data);

            document.dispatchEvent(new CustomEvent('team-production:order-checked', {
                detail: data || { orderId: id }
            }));

            return data;
        } catch (error) {
            console.error(error);
            return null;
        }
    }

    function buildCheckUrl(orderId) {
        const prefix = config.checkUrlPrefix || '/team/productionList/';
        const normalizedPrefix = prefix.endsWith('/') ? prefix : prefix + '/';
        return normalizedPrefix + encodeURIComponent(orderId) + '/check';
    }

    function buildHeaders() {
        const headers = { 'Accept': 'application/json' };
        const csrfToken = document.querySelector('meta[name="_csrf"]');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]');

        if (csrfToken && csrfHeader && csrfToken.content && csrfHeader.content) {
            headers[csrfHeader.content] = csrfToken.content;
        }

        return headers;
    }

    async function readResponsePayload(response) {
        const contentType = String(response.headers.get('content-type') || '').toLowerCase();
        if (contentType.includes('application/json')) {
            try {
                return await response.json();
            } catch (error) {
                return null;
            }
        }

        try {
            const text = await response.text();
            return text ? { message: text } : null;
        } catch (error) {
            return null;
        }
    }

    function resolveMessage(payload, fallback) {
        if (payload && typeof payload === 'object') {
            const message = payload.message || payload.error || payload.detail;
            if (message) return String(message);
        }
        return fallback;
    }

    function markLocalChecked(orderId, data) {
        const id = toText(orderId);
        if (!id) return;

        const checkedBy = data && data.checkedByUsername ? toText(data.checkedByUsername) : '';
        const checkedAtText = data && data.checkedAtText ? toText(data.checkedAtText) : '';
        const nextState = CHECK_STATE.CHECKED;
        const nextLabel = CHECK_STATE_LABEL.CHECKED;

        document.querySelectorAll('[data-overview-order-id="' + cssEscape(id) + '"]').forEach(function (row) {
            row.setAttribute('data-checked', 'true');
            row.setAttribute('data-check-state', nextState);
            row.setAttribute('data-check-state-label', nextLabel);
            row.setAttribute('data-checked-by', checkedBy);
            row.setAttribute('data-checked-at', checkedAtText);
            row.setAttribute('data-revision-marked-by', '');
            row.setAttribute('data-revision-marked-at', '');
            row.setAttribute('data-revision-reason', '');
            row.classList.add('team-production-row-checked');
            row.classList.remove('team-production-row-unchecked', 'team-production-row-revised');

            const badge = row.querySelector('.team-production-check-badge');
            if (badge) {
                badge.textContent = nextLabel;
                resetBadgeClass(badge);
                badge.classList.add('bg-success-subtle', 'text-success');
                badge.title = checkedBy ? '확인자: ' + checkedBy : '확인된 발주입니다.';
            }
        });

        document.querySelectorAll('[data-order-id="' + cssEscape(id) + '"]').forEach(function (element) {
            element.setAttribute('data-checked', 'true');
            element.setAttribute('data-check-state', nextState);
            element.setAttribute('data-check-state-label', nextLabel);
            element.classList.add('is-checked');
            element.classList.remove('is-unchecked', 'is-revised');
        });

        document.querySelectorAll('[data-list-check-state-text][data-order-id="' + cssEscape(id) + '"]').forEach(function (element) {
            element.textContent = checkedBy ? '확인 / ' + checkedBy : nextLabel;
            element.title = checkedBy ? '확인자: ' + checkedBy : '확인된 발주입니다.';
        });
    }

    function resetBadgeClass(badge) {
        badge.classList.remove(
            'bg-secondary-subtle', 'text-secondary',
            'bg-success-subtle', 'text-success',
            'bg-warning-subtle', 'text-warning', 'text-dark'
        );
    }

    function showRevisionNotices(data) {
        const notices = data && Array.isArray(data.changeNotices) ? data.changeNotices : [];
        if (!data || data.revisedBeforeCheck !== true || notices.length === 0) return;

        const modalElement = document.getElementById('team-production-revision-notice-modal');
        const body = document.getElementById('team-production-revision-notice-body');

        if (!modalElement || !body || !window.bootstrap) {
            window.alert(buildRevisionAlertText(notices));
            return;
        }

        body.replaceChildren();
        notices.forEach(function (notice) {
            body.appendChild(buildRevisionEventElement(notice));
        });

        window.bootstrap.Modal.getOrCreateInstance(modalElement).show();
    }

    function buildRevisionEventElement(notice) {
        const section = document.createElement('section');
        section.className = 'team-production-revision-event';

        const title = document.createElement('div');
        title.className = 'fw-bold';
        title.textContent = toText(notice.summary) || '오더 정보 변경';
        section.appendChild(title);

        const meta = document.createElement('div');
        meta.className = 'small text-muted mt-1';
        const metaTokens = [
            toText(notice.sourceAreaLabel),
            toText(notice.actorDisplayName) || toText(notice.actorUsername) || '시스템',
            toText(notice.changedAtText),
            toText(notice.operationLabel)
        ].filter(Boolean);
        meta.textContent = metaTokens.join(' · ');
        section.appendChild(meta);

        if (notice.requestPath) {
            const path = document.createElement('div');
            path.className = 'small text-muted mt-1';
            path.textContent = '처리 경로: ' + notice.requestPath;
            section.appendChild(path);
        }

        const fields = Array.isArray(notice.fields) ? notice.fields : [];
        if (fields.length > 0) {
            const table = document.createElement('table');
            table.className = 'team-production-revision-fields';
            const tbody = document.createElement('tbody');

            fields.forEach(function (field) {
                const row = document.createElement('tr');
                const label = document.createElement('th');
                const before = document.createElement('td');
                const arrow = document.createElement('td');
                const after = document.createElement('td');

                label.textContent = toText(field.fieldLabel) || toText(field.fieldKey) || '변경항목';
                before.textContent = toText(field.beforeValue) || '-';
                arrow.textContent = '→';
                arrow.className = 'text-center';
                arrow.style.width = '34px';
                after.textContent = toText(field.afterValue) || '-';

                row.append(label, before, arrow, after);
                tbody.appendChild(row);
            });

            table.appendChild(tbody);
            section.appendChild(table);
        }

        return section;
    }

    function buildRevisionAlertText(notices) {
        const lines = ['확인 이후 변경된 내용입니다.'];

        notices.forEach(function (notice, index) {
            lines.push('');
            lines.push((index + 1) + '. ' + (toText(notice.summary) || '오더 정보 변경'));
            lines.push('수정자: ' + (toText(notice.actorDisplayName) || toText(notice.actorUsername) || '시스템'));
            lines.push('수정일: ' + (toText(notice.changedAtText) || '-'));

            (Array.isArray(notice.fields) ? notice.fields : []).forEach(function (field) {
                lines.push('- ' + (toText(field.fieldLabel) || '변경항목') + ': '
                    + (toText(field.beforeValue) || '-') + ' → ' + (toText(field.afterValue) || '-'));
            });
        });

        return lines.join('\n');
    }

    function isOrderChecked(orderId) {
        return getOrderCheckState(orderId) === CHECK_STATE.CHECKED;
    }

    function isNeedProductionCheck(orderId) {
        const state = getOrderCheckState(orderId);
        return state === CHECK_STATE.REVISED_AFTER_CHECK || state === CHECK_STATE.UNCHECKED;
    }

    function getOrderCheckState(orderId) {
        const id = toText(orderId);
        const row = document.querySelector('[data-overview-order-id="' + cssEscape(id) + '"]');
        if (!row) return CHECK_STATE.UNCHECKED;

        return normalizeCheckState({
            checkState: row.getAttribute('data-check-state'),
            checked: row.getAttribute('data-checked')
        });
    }

    function sortUncheckedFirst(orders) {
        if (!Array.isArray(orders)) return [];

        return orders.slice().sort(function (a, b) {
            const ar = getCheckRankFromOrder(a);
            const br = getCheckRankFromOrder(b);
            if (ar !== br) return ar - br;
            return Number(a && a.originalIndex || 0) - Number(b && b.originalIndex || 0);
        });
    }

    function getCheckRankFromOrder(order) {
        const state = normalizeCheckState(order || {});
        return CHECK_STATE_RANK[state] !== undefined ? CHECK_STATE_RANK[state] : CHECK_STATE_RANK.UNCHECKED;
    }

    function normalizeCheckState(source) {
        if (!source) return CHECK_STATE.UNCHECKED;

        const rawState = toText(firstValue(
            source.checkState,
            source.check_state,
            source.checkStatus,
            source.checkStatusName,
            source.check_state_name
        )).toUpperCase();

        if (rawState === CHECK_STATE.REVISED_AFTER_CHECK || rawState === 'REVISED' || rawState === '재수정') {
            return CHECK_STATE.REVISED_AFTER_CHECK;
        }
        if (rawState === CHECK_STATE.CHECKED || rawState === '확인') return CHECK_STATE.CHECKED;
        if (rawState === CHECK_STATE.UNCHECKED || rawState === '미확인') return CHECK_STATE.UNCHECKED;

        const checked = firstValue(source.checked, source.isChecked);
        if (checked === true || checked === 'true' || checked === 'Y' || checked === '1') {
            return CHECK_STATE.CHECKED;
        }

        return CHECK_STATE.UNCHECKED;
    }

    function getCheckStateLabel(state) {
        const normalized = normalizeCheckState({ checkState: state });
        return CHECK_STATE_LABEL[normalized] || CHECK_STATE_LABEL.UNCHECKED;
    }

    function markOrderObjectChecked(order) {
        if (!order) return order;
        order.checked = true;
        order.checkState = CHECK_STATE.CHECKED;
        order.checkStateLabel = CHECK_STATE_LABEL.CHECKED;
        order.revisionMarkedByUsername = '';
        order.revisionMarkedAtText = '';
        order.revisionReason = '';
        return order;
    }

    function firstValue() {
        for (let i = 0; i < arguments.length; i++) {
            const value = arguments[i];
            if (value !== undefined && value !== null && value !== '') return value;
        }
        return '';
    }

    function toText(value) {
        if (value === undefined || value === null) return '';
        return String(value).trim();
    }

    function cssEscape(value) {
        if (window.CSS && typeof window.CSS.escape === 'function') {
            return window.CSS.escape(String(value));
        }
        return String(value).replace(/([ #;?%&,.+*~':"!^$[\]()=>|/@])/g, '\\$1');
    }
})();
