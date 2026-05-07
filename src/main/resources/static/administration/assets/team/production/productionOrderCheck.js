/* =========================================================
   생산팀 발주 확인 처리 공통
   /administration/assets/team/production/productionOrderCheck.js
   ========================================================= */
(function() {
    'use strict';

    const config = window.teamProductionOverviewConfig || {};
    const sentOrderIds = new Set();

    window.TeamProductionOrderCheck = {
        mark: markOrderChecked,
        markLocal: markLocalChecked,
        isChecked: isOrderChecked,
        sortUncheckedFirst: sortUncheckedFirst
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
        const checkedBy = data && data.checkedByUsername ? data.checkedByUsername : '';

        document.querySelectorAll('[data-overview-order-id="' + cssEscape(id) + '"]').forEach(function(row) {
            row.setAttribute('data-checked', 'true');
            row.classList.add('team-production-row-checked');
            row.classList.remove('team-production-row-unchecked');

            if (checkedBy) {
                row.setAttribute('data-checked-by', checkedBy);
            }

            const badge = row.querySelector('.team-production-check-badge');

            if (badge) {
                badge.textContent = '확인';
                badge.classList.remove('bg-secondary-subtle', 'text-secondary');
                badge.classList.add('bg-success-subtle', 'text-success');
                badge.title = checkedBy ? '확인자: ' + checkedBy : '확인된 발주입니다.';
            }
        });

        document.querySelectorAll('[data-order-id="' + cssEscape(id) + '"]').forEach(function(el) {
            el.setAttribute('data-checked', 'true');
            el.classList.add('is-checked');
            el.classList.remove('is-unchecked');
        });
    }

    function isOrderChecked(orderId) {
        const id = toText(orderId);
        const row = document.querySelector('[data-overview-order-id="' + cssEscape(id) + '"]');

        if (!row) {
            return false;
        }

        return String(row.getAttribute('data-checked') || '').toLowerCase() === 'true';
    }

    function sortUncheckedFirst(orders) {
        if (!Array.isArray(orders)) {
            return [];
        }

        return orders.slice().sort(function(a, b) {
            const ac = isCheckedValue(a);
            const bc = isCheckedValue(b);

            if (ac !== bc) {
                return ac ? 1 : -1;
            }

            return Number(a.originalIndex || 0) - Number(b.originalIndex || 0);
        });
    }

    function isCheckedValue(order) {
        if (!order) {
            return false;
        }

        if (order.checked === true || order.checked === 'true') {
            return true;
        }

        return isOrderChecked(order.id);
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

        return String(value).replace(/["\\]/g, '\\$&');
    }
})();