/* =========================================================
   생산팀 발주 확인 처리 공통
   /administration/assets/team/production/productionOrderCheck.js

   checkState 기준 표시/정렬
   - REVISED_AFTER_CHECK : 재수정, 최우선 노출
   - UNCHECKED           : 미확인
   - CHECKED             : 확인
   ========================================================= */
(function() {
    'use strict';

    const config = window.teamProductionOverviewConfig || {};
    const sentOrderIds = new Set();

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

        if (!id) {
            return null;
        }

        if (isOrderChecked(id) || sentOrderIds.has(id)) {
            markLocalChecked(id, null);
            return null;
        }

        sentOrderIds.add(id);

        try {
            const response = await fetch(buildCheckUrl(id), {
                method: 'POST',
                credentials: 'same-origin',
                headers: buildHeaders()
            });

            if (!response.ok) {
                throw new Error('확인 처리 실패 status=' + response.status);
            }

            const data = await response.json();
            markLocalChecked(id, data);

            return data;
        } catch (error) {
            console.error(error);
            sentOrderIds.delete(id);
            return null;
        }
    }

    function buildCheckUrl(orderId) {
        const prefix = config.checkUrlPrefix || '/team/productionList/';
        return prefix + encodeURIComponent(orderId) + '/check';
    }

    function buildHeaders() {
        const headers = {
            'Accept': 'application/json'
        };

        const csrfToken = document.querySelector('meta[name="_csrf"]');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]');

        if (csrfToken && csrfHeader && csrfToken.content && csrfHeader.content) {
            headers[csrfHeader.content] = csrfToken.content;
        }

        return headers;
    }

    function markLocalChecked(orderId, data) {
        const id = toText(orderId);

        if (!id) {
            return;
        }

        const checkedBy = data && data.checkedByUsername ? toText(data.checkedByUsername) : '';
        const checkedAtText = data && data.checkedAtText ? toText(data.checkedAtText) : '';
        const nextState = CHECK_STATE.CHECKED;
        const nextLabel = getCheckStateLabel(nextState);

        document.querySelectorAll('[data-overview-order-id="' + cssEscape(id) + '"]').forEach(function(row) {
            row.setAttribute('data-checked', 'true');
            row.setAttribute('data-check-state', nextState);
            row.setAttribute('data-check-state-label', nextLabel);
            row.classList.add('team-production-row-checked');
            row.classList.remove('team-production-row-unchecked', 'team-production-row-revised');

            if (checkedBy) {
                row.setAttribute('data-checked-by', checkedBy);
            }

            if (checkedAtText) {
                row.setAttribute('data-checked-at', checkedAtText);
            }

            const badge = row.querySelector('.team-production-check-badge');

            if (badge) {
                badge.textContent = nextLabel;
                resetBadgeClass(badge);
                badge.classList.add('bg-success-subtle', 'text-success');
                badge.title = checkedBy ? '확인자: ' + checkedBy : '확인된 발주입니다.';
            }
        });

        document.querySelectorAll('[data-order-id="' + cssEscape(id) + '"]').forEach(function(el) {
            el.setAttribute('data-checked', 'true');
            el.setAttribute('data-check-state', nextState);
            el.setAttribute('data-check-state-label', nextLabel);
            el.classList.add('is-checked');
            el.classList.remove('is-unchecked', 'is-revised');
        });

        document.querySelectorAll('[data-list-check-state-text][data-order-id="' + cssEscape(id) + '"]').forEach(function(el) {
            el.textContent = nextLabel;
            el.title = checkedBy ? '확인자: ' + checkedBy : '확인된 발주입니다.';
        });
    }

    function resetBadgeClass(badge) {
        badge.classList.remove(
            'bg-secondary-subtle', 'text-secondary',
            'bg-success-subtle', 'text-success',
            'bg-warning-subtle', 'text-warning', 'text-dark'
        );
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

        if (!row) {
            return CHECK_STATE.UNCHECKED;
        }

        return normalizeCheckState({
            checkState: row.getAttribute('data-check-state'),
            checked: row.getAttribute('data-checked')
        });
    }

    function sortUncheckedFirst(orders) {
        if (!Array.isArray(orders)) {
            return [];
        }

        return orders.slice().sort(function(a, b) {
            const ar = getCheckRankFromOrder(a);
            const br = getCheckRankFromOrder(b);

            if (ar !== br) {
                return ar - br;
            }

            return Number(a && a.originalIndex || 0) - Number(b && b.originalIndex || 0);
        });
    }

    function getCheckRankFromOrder(order) {
        const state = normalizeCheckState(order || {});
        return CHECK_STATE_RANK[state] !== undefined ? CHECK_STATE_RANK[state] : CHECK_STATE_RANK.UNCHECKED;
    }

    function normalizeCheckState(source) {
        if (!source) {
            return CHECK_STATE.UNCHECKED;
        }

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

        if (rawState === CHECK_STATE.CHECKED || rawState === '확인') {
            return CHECK_STATE.CHECKED;
        }

        if (rawState === CHECK_STATE.UNCHECKED || rawState === '미확인') {
            return CHECK_STATE.UNCHECKED;
        }

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
        if (!order) {
            return order;
        }

        order.checked = true;
        order.checkState = CHECK_STATE.CHECKED;
        order.checkStateLabel = CHECK_STATE_LABEL.CHECKED;
        return order;
    }

    function firstValue() {
        for (let i = 0; i < arguments.length; i++) {
            const value = arguments[i];

            if (value !== undefined && value !== null && value !== '') {
                return value;
            }
        }

        return '';
    }

    function toText(value) {
        if (value === undefined || value === null) {
            return '';
        }

        return String(value).trim();
    }

    function cssEscape(value) {
        if (window.CSS && typeof window.CSS.escape === 'function') {
            return window.CSS.escape(String(value));
        }

        return String(value).replace(/([ #;?%&,.+*~':"!^$[\]()=>|/@])/g, '\\$1');
    }
})();
