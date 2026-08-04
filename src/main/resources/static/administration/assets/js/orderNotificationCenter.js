(function () {
    'use strict';

    const API = {
        summary: '/api/internal/order-notifications/summary',
        list: '/api/internal/order-notifications',
        read: function (id) { return '/api/internal/order-notifications/' + encodeURIComponent(id) + '/read'; },
        readAll: '/api/internal/order-notifications/read-all',
        adminRequest: function (orderId) { return '/api/internal/orders/' + encodeURIComponent(orderId) + '/admin-request'; },
        managementOrderList: function (orderId, orderStatus) {
            const params = new URLSearchParams();
            params.set('orderId', String(orderId));
            params.set('productName', '');
            params.set('keyword', '');
            params.set('dateCriteria', 'all');
            params.set('productCategoryId', 'all');
            /*
             * 알림 목록을 조회하는 시점의 실제 Order.status 값을 그대로 전달합니다.
             * orderId와 상태가 모두 동일한 한 건만 조회되므로 다른 상태의 발주가 섞이지 않습니다.
             */
            params.set('orderStatus', String(orderStatus));
            params.set('standard', 'all');
            params.set('size', '10');
            params.set('sortState', '');
            return '/management/nonStandardTaskList?' + params.toString();
        }
    };

    const state = {
        category: 'PRODUCTION',
        summary: { totalUnreadCount: 0, unreadCountByCategory: {} },
        socket: null,
        reconnectTimer: null,
        reconnectDelay: 1000,
        modal: null,
        loading: false
    };

    const els = {};
    let adminRequestDelegationBound = false;
    let adminRequestButtonObserver = null;

    /*
     * 관리자요청은 종 아이콘/알림 모달의 렌더링 여부와 무관하게
     * 생산·배송·출고 화면 어디서든 사용할 수 있어야 합니다.
     * 함수 선언은 호이스팅되므로 DOMContentLoaded 이전에도 안전하게 공개할 수 있습니다.
     */
    window.HiddenBathOrderNotification = {
        requestAdmin: requestAdmin,
        refreshSummary: refreshSummary
    };

    document.addEventListener('DOMContentLoaded', init);

    function init() {
        bindElements();
        bindAdminRequestDelegation();
        enableStateIndependentAdminRequestButtons();

        /*
         * 알림 센터 UI가 없는 화면에서도 관리자요청 API는 계속 동작합니다.
         * 아래부터는 종 아이콘/알림 모달 전용 초기화입니다.
         */
        if (!els.bellButton || !els.modalElement) {
            return;
        }

        if (!window.bootstrap || !window.bootstrap.Modal) {
            console.error('[발주알림] Bootstrap Modal을 찾을 수 없습니다.');
            return;
        }

        state.modal = window.bootstrap.Modal.getOrCreateInstance(els.modalElement);
        bindNotificationCenterEvents();
        refreshSummary();
        connectWebSocket();
        window.setInterval(refreshSummary, 60000);
    }

    function bindElements() {
        els.bellButton = document.getElementById('order-notification-bell-btn');
        els.badge = document.getElementById('order-notification-badge');
        els.modalElement = document.getElementById('order-notification-center-modal');
        els.list = document.getElementById('order-notification-list');
        els.readAllButton = document.getElementById('order-notification-read-all-btn');
        els.managementOrderLinkEnabled = document.getElementById('order-notification-management-link-enabled');
        els.tabs = Array.from(document.querySelectorAll('[data-order-notification-category]'));
    }

    function bindNotificationCenterEvents() {
        els.bellButton.addEventListener('click', function () {
            state.modal.show();
        });

        els.modalElement.addEventListener('shown.bs.modal', function () {
            loadNotifications();
        });

        els.tabs.forEach(function (tab) {
            tab.addEventListener('click', function () {
                state.category = tab.getAttribute('data-order-notification-category') || 'PRODUCTION';
                updateTabs();
                loadNotifications();
            });
        });

        if (els.readAllButton) {
            els.readAllButton.addEventListener('click', markAllRead);
        }

        if (els.list) {
            els.list.addEventListener('click', handleNotificationClick);
            els.list.addEventListener('keydown', handleNotificationKeydown);
        }
    }

    function bindAdminRequestDelegation() {
        if (adminRequestDelegationBound) {
            return;
        }

        adminRequestDelegationBound = true;

        document.addEventListener('click', function (event) {
            const target = event.target;
            const button = target && typeof target.closest === 'function'
                ? target.closest('[data-order-admin-request]')
                : null;

            if (!button) {
                return;
            }

            event.preventDefault();
            event.stopPropagation();

            if (button.disabled || button.getAttribute('aria-disabled') === 'true') {
                return;
            }

            const orderId = Number(button.getAttribute('data-order-id'));

            if (!Number.isFinite(orderId) || orderId <= 0) {
                console.error('[관리자요청] 유효한 발주 ID가 없습니다.', {
                    orderId: button.getAttribute('data-order-id'),
                    button: button
                });
                showAlert('error', '관리자요청을 보낼 발주 ID가 없습니다. 화면을 새로고침해 주세요.');
                return;
            }

            requestAdmin(
                orderId,
                button.getAttribute('data-admin-request-message') || null,
                button
            );
        });
    }


    /**
     * 관리자요청은 발주 상태와 무관합니다.
     * 서버에서 상태 제한을 두지 않는 것과 동일하게, 화면에 남아 있는 과거 disabled
     * 속성이나 동적으로 생성된 버튼의 상태 제한도 공통 스크립트에서 제거합니다.
     * 단, 발주 ID가 아직 지정되지 않았거나 실제 전송 중인 버튼은 활성화하지 않습니다.
     */
    function enableStateIndependentAdminRequestButtons() {
        normalizeAdminRequestButtons(document);

        if (!document.body || typeof MutationObserver === 'undefined' || adminRequestButtonObserver) {
            return;
        }

        adminRequestButtonObserver = new MutationObserver(function (mutations) {
            mutations.forEach(function (mutation) {
                if (mutation.type === 'childList') {
                    mutation.addedNodes.forEach(function (node) {
                        if (!node || node.nodeType !== 1) {
                            return;
                        }
                        normalizeAdminRequestButtons(node);
                    });
                    return;
                }

                if (mutation.type === 'attributes' && mutation.target) {
                    normalizeAdminRequestButton(mutation.target);
                }
            });
        });

        adminRequestButtonObserver.observe(document.body, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: [
                'data-order-id',
                'disabled',
                'aria-disabled',
                'data-admin-request-busy'
            ]
        });
    }

    function normalizeAdminRequestButtons(root) {
        if (!root) {
            return;
        }

        if (typeof root.matches === 'function' && root.matches('[data-order-admin-request]')) {
            normalizeAdminRequestButton(root);
        }

        if (typeof root.querySelectorAll !== 'function') {
            return;
        }

        root.querySelectorAll('[data-order-admin-request]').forEach(normalizeAdminRequestButton);
    }

    function normalizeAdminRequestButton(button) {
        if (!button || typeof button.matches !== 'function' || !button.matches('[data-order-admin-request]')) {
            return;
        }

        if (button.getAttribute('data-admin-request-busy') === 'true') {
            return;
        }

        const orderId = Number(button.getAttribute('data-order-id'));
        const hasValidOrderId = Number.isFinite(orderId) && orderId > 0;

        if (!hasValidOrderId) {
            if (!button.disabled) {
                button.disabled = true;
            }
            if (button.getAttribute('aria-disabled') !== 'true') {
                button.setAttribute('aria-disabled', 'true');
            }
            return;
        }

        if (button.disabled) {
            button.disabled = false;
        }
        if (button.hasAttribute('disabled')) {
            button.removeAttribute('disabled');
        }
        if (button.hasAttribute('aria-disabled')) {
            button.removeAttribute('aria-disabled');
        }

        if (!button.title || /상태|승인완료|생산완료|요청을 보낼 수 없/.test(button.title)) {
            button.title = '발주 상태와 무관하게 이 발주의 관리 담당자에게 긴급 확인을 요청합니다.';
        }
    }

    async function refreshSummary() {
        try {
            const response = await fetch(API.summary, { headers: ajaxHeaders() });
            if (!response.ok) return;
            state.summary = await response.json();
            renderSummary();
        } catch (error) {
            console.warn('알림 요약 조회 실패', error);
        }
    }

    function renderSummary() {
        const total = Number(state.summary.totalUnreadCount || 0);
        els.badge.textContent = total > 99 ? '99+' : String(total);
        els.badge.classList.toggle('is-empty', total <= 0);

        els.tabs.forEach(function (tab) {
            const category = tab.getAttribute('data-order-notification-category');
            const countElement = tab.querySelector('.order-notification-tab-count');
            const count = Number((state.summary.unreadCountByCategory || {})[category] || 0);
            if (countElement) countElement.textContent = count > 99 ? '99+' : String(count);
        });
    }

    async function loadNotifications() {
        if (state.loading || !els.list) return;
        state.loading = true;
        els.list.innerHTML = '<div class="order-notification-loading"><div><span class="spinner-border spinner-border-sm me-2"></span>알림을 불러오는 중입니다.</div></div>';

        try {
            const url = API.list + '?category=' + encodeURIComponent(state.category) + '&page=0&size=50';
            const response = await fetch(url, { headers: ajaxHeaders() });
            const data = await parseResponse(response);
            if (!response.ok) throw new Error(data.message || '알림 조회에 실패했습니다.');
            renderList(data.content || []);
        } catch (error) {
            els.list.innerHTML = '<div class="order-notification-empty">' + escapeHtml(error.message || '알림 조회에 실패했습니다.') + '</div>';
        } finally {
            state.loading = false;
        }
    }

    function renderList(items) {
        if (!items.length) {
            els.list.innerHTML = '<div class="order-notification-empty">이 카테고리의 알림이 없습니다.</div>';
            return;
        }

        els.list.innerHTML = items.map(function (item) {
            const unreadClass = item.read ? '' : ' is-unread';
            const emergencyClass = item.category === 'EMERGENCY' ? ' is-emergency' : '';
            const changes = Array.isArray(item.changes) ? item.changes : [];
            const changeHtml = changes.length
                ? '<div class="order-notification-changes">' + changes.map(renderChange).join('') + '</div>'
                : '';
            const orderId = Number(item.orderId);
            const orderStatus = normalizeOrderStatus(item.orderStatus);
            const canOpenManagementOrder = Boolean(els.managementOrderLinkEnabled) &&
                Number.isFinite(orderId) && orderId > 0 && orderStatus !== null;
            const shortcutHtml = canOpenManagementOrder
                ? [
                    '<div class="order-notification-item-actions">',
                    '  <a class="btn btn-sm btn-primary order-notification-go-btn"',
                    '     href="' + escapeAttr(API.managementOrderList(orderId, orderStatus)) + '"',
                    '     data-order-notification-go',
                    '     data-order-id="' + escapeAttr(orderId) + '"',
                    '     data-order-status="' + escapeAttr(orderStatus) + '">',
                    '    <i class="ri-external-link-line me-1"></i>해당 발주 바로가기',
                    '  </a>',
                    '</div>'
                ].join('')
                : '';

            /*
             * 바로가기 링크를 내부에 둘 수 있도록 최상위 요소를 button이 아닌 article로 사용합니다.
             * button 안에 a/button을 넣는 중첩 인터랙티브 마크업은 브라우저별 클릭 오류를 유발합니다.
             */
            return [
                '<article class="order-notification-item' + unreadClass + emergencyClass + '"',
                ' data-notification-id="' + escapeAttr(item.id) + '"',
                ' role="button" tabindex="0" aria-expanded="false">',
                '  <div class="order-notification-item-header">',
                '    <span class="order-notification-item-title">' + escapeHtml(item.title || '발주 알림') + '</span>',
                '    <span class="order-notification-item-time">' + escapeHtml(item.createdAtText || '') + '</span>',
                '  </div>',
                '  <div class="order-notification-item-message">' + escapeHtml(item.message || '') + '</div>',
                '  <div class="order-notification-meta">',
                '    <span>발주 #' + escapeHtml(item.orderId || '-') + '</span>',
                '    <span>현재상태: ' + escapeHtml(item.orderStatusLabel || item.orderStatus || '-') + '</span>',
                '    <span>처리자: ' + escapeHtml(item.actorDisplayName || item.actorUsername || '시스템') + '</span>',
                '    <span>' + escapeHtml(item.operationLabel || '') + '</span>',
                '  </div>',
                changeHtml,
                shortcutHtml,
                '</article>'
            ].join('');
        }).join('');
    }

    function normalizeOrderStatus(value) {
        if (value == null) {
            return null;
        }

        const normalized = String(value).trim().toUpperCase();
        const allowed = [
            'REQUESTED',
            'CONFIRMED',
            'PRODUCTION_DONE',
            'DISPATCH_DONE',
            'DELIVERY_DONE',
            'CANCELED'
        ];

        return allowed.includes(normalized) ? normalized : null;
    }

    function renderChange(change) {
        return [
            '<div class="order-notification-change-row">',
            '  <div class="order-notification-change-label">' + escapeHtml(change.fieldLabel || change.fieldKey || '-') + '</div>',
            '  <div><span class="text-muted">' + escapeHtml(valueOrDash(change.beforeValue)) + '</span>',
            '  <i class="ri-arrow-right-line mx-1"></i>',
            '  <span class="fw-semibold">' + escapeHtml(valueOrDash(change.afterValue)) + '</span></div>',
            '</div>'
        ].join('');
    }

    async function handleNotificationClick(event) {
        const shortcut = event.target.closest('[data-order-notification-go]');

        if (shortcut) {
            event.preventDefault();
            event.stopPropagation();

            const item = shortcut.closest('.order-notification-item');
            const href = shortcut.getAttribute('href');

            if (!href) {
                console.error('[발주알림] 바로가기 URL이 없습니다.', shortcut);
                return;
            }

            shortcut.classList.add('disabled');
            shortcut.setAttribute('aria-disabled', 'true');
            shortcut.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>이동 중';

            /* 읽음 처리 실패가 발생해도 업무 화면 이동은 막지 않습니다. */
            await markNotificationItemRead(item, false);
            window.location.assign(href);
            return;
        }

        const item = event.target.closest('.order-notification-item');
        if (!item) return;

        const expanded = !item.classList.contains('is-expanded');
        item.classList.toggle('is-expanded', expanded);
        item.setAttribute('aria-expanded', String(expanded));
        await markNotificationItemRead(item, true);
    }

    function handleNotificationKeydown(event) {
        if (event.target.closest('[data-order-notification-go]')) {
            return;
        }

        if (event.key !== 'Enter' && event.key !== ' ') {
            return;
        }

        const item = event.target.closest('.order-notification-item');
        if (!item) {
            return;
        }

        event.preventDefault();
        item.click();
    }

    async function markNotificationItemRead(item, refreshBadge) {
        if (!item || !item.classList.contains('is-unread')) {
            return true;
        }

        const id = item.getAttribute('data-notification-id');
        if (!id) {
            return false;
        }

        try {
            const response = await fetch(API.read(id), { method: 'POST', headers: ajaxHeaders() });
            if (!response.ok) {
                return false;
            }

            item.classList.remove('is-unread');
            if (refreshBadge) {
                await refreshSummary();
            }
            return true;
        } catch (error) {
            console.warn('알림 읽음 처리 실패', error);
            return false;
        }
    }

    async function markAllRead() {
        try {
            const response = await fetch(
                API.readAll + '?category=' + encodeURIComponent(state.category),
                { method: 'POST', headers: ajaxHeaders() }
            );
            const data = await parseResponse(response);
            if (!response.ok) throw new Error(data.message || '전체 읽음 처리에 실패했습니다.');
            await Promise.all([refreshSummary(), loadNotifications()]);
        } catch (error) {
            showAlert('error', error.message || '전체 읽음 처리에 실패했습니다.');
        }
    }

    function updateTabs() {
        els.tabs.forEach(function (tab) {
            tab.classList.toggle(
                'active',
                tab.getAttribute('data-order-notification-category') === state.category
            );
        });
    }

    async function requestAdmin(orderId, message, button) {
        const normalizedOrderId = Number(orderId);

        if (!Number.isFinite(normalizedOrderId) || normalizedOrderId <= 0) {
            const invalidIdError = new Error('관리자요청을 보낼 발주 ID가 올바르지 않습니다.');
            console.error('[관리자요청] 잘못된 발주 ID', orderId);
            showAlert('error', invalidIdError.message);
            return false;
        }

        if (button && button.getAttribute('data-admin-request-busy') === 'true') {
            return false;
        }

        console.info('[관리자요청] 확인창 열기', {
            orderId: normalizedOrderId,
            button: button || null
        });

        const confirmed = await confirmAdminRequest(normalizedOrderId);
        if (!confirmed) {
            return false;
        }

        const originalHtml = button ? button.innerHTML : '';
        const originalDisabled = button ? button.disabled : false;

        if (button) {
            button.setAttribute('data-admin-request-busy', 'true');
            button.disabled = true;
            button.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>요청 중';
        }

        console.info('[관리자요청] 전송 시작', {
            orderId: normalizedOrderId,
            message: message || null
        });

        try {
            const response = await fetch(API.adminRequest(normalizedOrderId), {
                method: 'POST',
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, ajaxHeaders()),
                body: JSON.stringify({ message: message || null })
            });
            const data = await parseResponse(response);

            if (!response.ok) {
                const requestError = new Error(data.message || ('관리자요청 전송에 실패했습니다. HTTP ' + response.status));
                requestError.status = response.status;
                requestError.response = data;
                throw requestError;
            }

            console.info('[관리자요청] 전송 완료', data);
            showAlert('success', data.message || '관리자요청을 전달했습니다.');
            return true;
        } catch (error) {
            console.error('[관리자요청] 전송 실패', error);
            showAlert('error', error && error.message ? error.message : '관리자요청 전송에 실패했습니다.');
            return false;
        } finally {
            if (button) {
                button.removeAttribute('data-admin-request-busy');
                button.disabled = originalDisabled;
                button.innerHTML = originalHtml;
            }
        }
    }

    async function confirmAdminRequest(orderId) {
        /*
         * SweetAlert2 핵심 CSS가 페이지별로 누락되거나 Bootstrap 전체화면 모달의
         * stacking context/overflow에 가려지는 문제를 피하기 위해 네이티브 <dialog>를 사용합니다.
         * <dialog>.showModal()은 브라우저 top layer에 표시되므로 상세/넓게보기/리스트형
         * 어느 화면에서도 부모 overflow와 z-index의 영향을 받지 않습니다.
         */
        if (supportsNativeDialog()) {
            return openOrderActionDialog({
                mode: 'confirm',
                icon: 'warning',
                title: '관리자요청을 보내시겠습니까?',
                message: '발주 #' + orderId + '의 Task 관리 담당자에게 긴급 알림이 전달됩니다.',
                confirmText: '요청 보내기',
                cancelText: '취소'
            });
        }

        return window.confirm('발주 #' + orderId + '의 관리 담당자에게 관리자요청을 보내시겠습니까?');
    }

    function connectWebSocket() {
        if (!window.WebSocket || !els.bellButton) return;
        clearTimeout(state.reconnectTimer);

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const url = protocol + '//' + window.location.host + '/ws/order-notifications';

        try {
            state.socket = new WebSocket(url);
        } catch (error) {
            scheduleReconnect();
            return;
        }

        state.socket.onopen = function () {
            state.reconnectDelay = 1000;
        };

        state.socket.onmessage = function (event) {
            try {
                const payload = JSON.parse(event.data || '{}');
                if (payload.type !== 'ORDER_NOTIFICATION_CREATED') return;
                animateBell();
                refreshSummary();
                if (els.modalElement.classList.contains('show') &&
                    payload.notification && payload.notification.category === state.category) {
                    loadNotifications();
                }
            } catch (error) {
                console.warn('실시간 알림 메시지 처리 실패', error);
            }
        };

        state.socket.onclose = scheduleReconnect;
        state.socket.onerror = function () {
            try { state.socket.close(); } catch (ignore) {}
        };
    }

    function scheduleReconnect() {
        clearTimeout(state.reconnectTimer);
        state.reconnectTimer = window.setTimeout(connectWebSocket, state.reconnectDelay);
        state.reconnectDelay = Math.min(30000, state.reconnectDelay * 2);
    }

    function animateBell() {
        els.bellButton.classList.remove('is-alerting');
        void els.bellButton.offsetWidth;
        els.bellButton.classList.add('is-alerting');
        window.setTimeout(function () { els.bellButton.classList.remove('is-alerting'); }, 2500);
    }

    function ajaxHeaders() {
        return { 'X-Requested-With': 'fetch', 'Accept': 'application/json' };
    }

    async function parseResponse(response) {
        const text = await response.text();
        if (!text) return {};
        try { return JSON.parse(text); } catch (error) { return { message: text }; }
    }

    function showAlert(icon, message) {
        if (supportsNativeDialog()) {
            openOrderActionDialog({
                mode: 'alert',
                icon: icon,
                title: icon === 'success' ? '처리되었습니다.' : '확인이 필요합니다.',
                message: message,
                confirmText: '확인'
            });
            return;
        }

        window.alert(message);
    }

    function supportsNativeDialog() {
        return typeof window.HTMLDialogElement === 'function' &&
            typeof document.createElement('dialog').showModal === 'function';
    }

    function ensureOrderActionDialog() {
        let dialog = document.getElementById('hiddenbath-order-action-dialog');

        if (dialog) {
            return dialog;
        }

        dialog = document.createElement('dialog');
        dialog.id = 'hiddenbath-order-action-dialog';
        dialog.className = 'hiddenbath-order-action-dialog';
        dialog.setAttribute('aria-labelledby', 'hiddenbath-order-action-dialog-title');
        dialog.setAttribute('aria-describedby', 'hiddenbath-order-action-dialog-message');
        dialog.innerHTML = [
            '<div class="hiddenbath-order-action-dialog-shell">',
            '  <button type="button" class="hiddenbath-order-action-dialog-close" data-order-action-dialog-cancel aria-label="닫기">×</button>',
            '  <div class="hiddenbath-order-action-dialog-icon" data-order-action-dialog-icon aria-hidden="true"></div>',
            '  <h2 class="hiddenbath-order-action-dialog-title" id="hiddenbath-order-action-dialog-title"></h2>',
            '  <p class="hiddenbath-order-action-dialog-message" id="hiddenbath-order-action-dialog-message"></p>',
            '  <div class="hiddenbath-order-action-dialog-actions">',
            '    <button type="button" class="btn btn-light hiddenbath-order-action-dialog-cancel" data-order-action-dialog-cancel>취소</button>',
            '    <button type="button" class="btn btn-danger hiddenbath-order-action-dialog-confirm" data-order-action-dialog-confirm>확인</button>',
            '  </div>',
            '</div>'
        ].join('');

        document.body.appendChild(dialog);
        return dialog;
    }

    function openOrderActionDialog(options) {
        const dialog = ensureOrderActionDialog();
        const titleElement = dialog.querySelector('#hiddenbath-order-action-dialog-title');
        const messageElement = dialog.querySelector('#hiddenbath-order-action-dialog-message');
        const iconElement = dialog.querySelector('[data-order-action-dialog-icon]');
        const confirmButton = dialog.querySelector('[data-order-action-dialog-confirm]');
        const cancelButtons = Array.from(dialog.querySelectorAll('[data-order-action-dialog-cancel]'));
        const cancelActionButton = dialog.querySelector('.hiddenbath-order-action-dialog-cancel.btn');
        const normalizedIcon = options.icon === 'success' ? 'success' :
            (options.icon === 'error' ? 'error' : 'warning');
        const showCancel = options.mode === 'confirm';

        titleElement.textContent = options.title || '';
        messageElement.textContent = options.message || '';
        confirmButton.textContent = options.confirmText || '확인';
        confirmButton.className = 'btn hiddenbath-order-action-dialog-confirm ' +
            (normalizedIcon === 'success' ? 'btn-success' : 'btn-danger');
        cancelActionButton.textContent = options.cancelText || '취소';
        cancelActionButton.hidden = !showCancel;
        iconElement.className = 'hiddenbath-order-action-dialog-icon is-' + normalizedIcon;
        iconElement.textContent = normalizedIcon === 'success' ? '✓' :
            (normalizedIcon === 'error' ? '!' : '!');

        if (dialog.open) {
            dialog.close('cancel');
        }

        return new Promise(function (resolve) {
            let settled = false;

            function finish(result) {
                if (settled) {
                    return;
                }

                settled = true;
                cleanup();

                if (dialog.open) {
                    dialog.close(result ? 'confirm' : 'cancel');
                }

                resolve(Boolean(result));
            }

            function onConfirm(event) {
                event.preventDefault();
                finish(true);
            }

            function onCancel(event) {
                event.preventDefault();
                finish(false);
            }

            function onNativeCancel(event) {
                event.preventDefault();
                finish(false);
            }

            function cleanup() {
                confirmButton.removeEventListener('click', onConfirm);
                cancelButtons.forEach(function (button) {
                    button.removeEventListener('click', onCancel);
                });
                dialog.removeEventListener('cancel', onNativeCancel);
            }

            confirmButton.addEventListener('click', onConfirm);
            cancelButtons.forEach(function (button) {
                button.addEventListener('click', onCancel);
            });
            dialog.addEventListener('cancel', onNativeCancel);

            dialog.showModal();
        });
    }

    function valueOrDash(value) {
        return value === null || value === undefined || String(value).trim() === '' ? '-' : String(value);
    }

    function escapeHtml(value) {
        return String(value === null || value === undefined ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function escapeAttr(value) {
        return escapeHtml(value).replace(/`/g, '&#096;');
    }
})();
