(function () {
    'use strict';

    const API = {
        summary: '/api/internal/order-notifications/summary',
        list: '/api/internal/order-notifications',
        read: function (id) { return '/api/internal/order-notifications/' + encodeURIComponent(id) + '/read'; },
        readLoaded: '/api/internal/order-notifications/read-loaded',
        adminRequest: function (orderId) { return '/api/internal/orders/' + encodeURIComponent(orderId) + '/admin-request'; }
    };

    const state = {
        category: 'PRODUCTION',
        isManagement: false,
        summary: { totalUnreadCount: 0, unreadCountByCategory: {} },
        items: [],
        nextCursor: null,
        hasNext: false,
        loadingInitial: false,
        loadingMore: false,
        hasLoaded: false,
        socket: null,
        reconnectTimer: null,
        reconnectDelay: 1000,
        modal: null,
        socketBuffer: new Map(),
        socketFlushTimer: null,
        summaryTimer: null,
        listRequestVersion: 0
    };

    const els = {};
    let adminRequestDelegationBound = false;
    let adminRequestButtonObserver = null;

    window.HiddenBathOrderNotification = {
        requestAdmin: requestAdmin,
        refreshSummary: refreshSummary
    };

    document.addEventListener('DOMContentLoaded', init);

    function init() {
        bindElements();
        bindAdminRequestDelegation();
        enableStateIndependentAdminRequestButtons();

        if (!els.bellButton || !els.modalElement) return;
        if (!window.bootstrap || !window.bootstrap.Modal) {
            console.error('[발주알림] Bootstrap Modal을 찾을 수 없습니다.');
            return;
        }

        state.isManagement = Boolean(els.managementMode);
        if (!state.isManagement) state.category = null;
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
        els.refreshButton = document.getElementById('order-notification-refresh-btn');
        els.loadMoreButton = document.getElementById('order-notification-load-more-btn');
        els.loadMoreWrap = document.getElementById('order-notification-load-more-wrap');
        els.newArrival = document.getElementById('order-notification-new-arrival');
        els.managementMode = document.getElementById('order-notification-management-mode');
        els.tabs = Array.from(document.querySelectorAll('[data-order-notification-category]'));
    }

    function bindNotificationCenterEvents() {
        els.bellButton.addEventListener('click', function () { state.modal.show(); });
        els.modalElement.addEventListener('shown.bs.modal', function () {
            if (!state.hasLoaded) reloadNotifications();
        });

        els.tabs.forEach(function (tab) {
            tab.addEventListener('click', function () {
                state.category = tab.getAttribute('data-order-notification-category') || 'PRODUCTION';
                updateTabs();
                reloadNotifications();
            });
        });

        if (els.readAllButton) els.readAllButton.addEventListener('click', markLoadedRead);
        if (els.refreshButton) els.refreshButton.addEventListener('click', reloadNotifications);
        if (els.loadMoreButton) els.loadMoreButton.addEventListener('click', loadMoreNotifications);
        if (els.list) {
            els.list.addEventListener('click', handleNotificationClick);
            els.list.addEventListener('keydown', handleNotificationKeydown);
            els.list.addEventListener('scroll', function () {
                if (!state.hasNext || state.loadingMore || state.loadingInitial) return;
                if (els.list.scrollTop + els.list.clientHeight >= els.list.scrollHeight - 120) {
                    loadMoreNotifications();
                }
            });
        }
    }

    function bindAdminRequestDelegation() {
        if (adminRequestDelegationBound) return;
        adminRequestDelegationBound = true;
        document.addEventListener('click', function (event) {
            const target = event.target;
            const button = target && typeof target.closest === 'function'
                ? target.closest('[data-order-admin-request]') : null;
            if (!button) return;
            event.preventDefault();
            event.stopPropagation();
            if (button.disabled || button.getAttribute('aria-disabled') === 'true') return;

            const orderId = Number(button.getAttribute('data-order-id'));
            if (!Number.isFinite(orderId) || orderId <= 0) {
                console.error('[관리자요청] 유효한 발주 ID가 없습니다.', button);
                showAlert('error', '관리자요청을 보낼 발주 ID가 없습니다. 화면을 새로고침해 주세요.');
                return;
            }
            requestAdmin(orderId, button.getAttribute('data-admin-request-message') || null, button);
        });
    }

    function enableStateIndependentAdminRequestButtons() {
        normalizeAdminRequestButtons(document);
        if (!document.body || typeof MutationObserver === 'undefined' || adminRequestButtonObserver) return;
        adminRequestButtonObserver = new MutationObserver(function (mutations) {
            mutations.forEach(function (mutation) {
                if (mutation.type === 'childList') {
                    mutation.addedNodes.forEach(function (node) {
                        if (node && node.nodeType === 1) normalizeAdminRequestButtons(node);
                    });
                } else if (mutation.type === 'attributes' && mutation.target) {
                    normalizeAdminRequestButton(mutation.target);
                }
            });
        });
        adminRequestButtonObserver.observe(document.body, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['data-order-id', 'disabled', 'aria-disabled', 'data-admin-request-busy']
        });
    }

    function normalizeAdminRequestButtons(root) {
        if (!root) return;
        if (typeof root.matches === 'function' && root.matches('[data-order-admin-request]')) {
            normalizeAdminRequestButton(root);
        }
        if (typeof root.querySelectorAll === 'function') {
            root.querySelectorAll('[data-order-admin-request]').forEach(normalizeAdminRequestButton);
        }
    }

    function normalizeAdminRequestButton(button) {
        if (!button || typeof button.matches !== 'function' || !button.matches('[data-order-admin-request]')) return;
        if (button.getAttribute('data-admin-request-busy') === 'true') return;
        const orderId = Number(button.getAttribute('data-order-id'));
        const valid = Number.isFinite(orderId) && orderId > 0;
        if (!valid) {
            button.disabled = true;
            button.setAttribute('aria-disabled', 'true');
            return;
        }
        button.disabled = false;
        button.removeAttribute('disabled');
        button.removeAttribute('aria-disabled');
        if (!button.title || /상태|승인완료|생산완료|요청을 보낼 수 없/.test(button.title)) {
            button.title = '이 발주의 관리 담당자에게 긴급 확인을 요청합니다.';
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

    function scheduleSummaryRefresh() {
        window.clearTimeout(state.summaryTimer);
        state.summaryTimer = window.setTimeout(refreshSummary, 350);
    }

    function renderSummary() {
        const total = Number(state.summary.totalUnreadCount || 0);
        if (els.badge) {
            els.badge.textContent = total > 99 ? '99+' : String(total);
            els.badge.classList.toggle('is-empty', total <= 0);
        }
        els.tabs.forEach(function (tab) {
            const category = tab.getAttribute('data-order-notification-category');
            const countElement = tab.querySelector('.order-notification-tab-count');
            const count = Number((state.summary.unreadCountByCategory || {})[category] || 0);
            if (countElement) countElement.textContent = count > 99 ? '99+' : String(count);
        });
    }

    async function reloadNotifications() {
        if (!els.list) return;
        const requestVersion = ++state.listRequestVersion;
        state.items = [];
        state.nextCursor = null;
        state.hasNext = false;
        state.loadingInitial = true;
        state.loadingMore = false;
        hideNewArrival();
        els.list.innerHTML = '<div class="order-notification-loading"><div><span class="spinner-border spinner-border-sm me-2"></span>알림을 한 번에 불러오는 중입니다.</div></div>';
        updateLoadMoreControl();
        try {
            const applied = await fetchNotificationPage(true, 50, requestVersion);
            if (applied && requestVersion === state.listRequestVersion) state.hasLoaded = true;
        } catch (error) {
            if (requestVersion === state.listRequestVersion) {
                els.list.innerHTML = '<div class="order-notification-empty">' + escapeHtml(error.message || '알림 조회에 실패했습니다.') + '</div>';
            }
        } finally {
            if (requestVersion === state.listRequestVersion) {
                state.loadingInitial = false;
                updateLoadMoreControl();
            }
        }
    }

    async function loadMoreNotifications() {
        if (!state.hasNext || state.loadingMore || state.loadingInitial) return;
        const requestVersion = state.listRequestVersion;
        state.loadingMore = true;
        updateLoadMoreControl();
        try {
            await fetchNotificationPage(false, 30, requestVersion);
        } catch (error) {
            if (requestVersion === state.listRequestVersion) {
                showAlert('error', error.message || '추가 알림 조회에 실패했습니다.');
            }
        } finally {
            if (requestVersion === state.listRequestVersion) {
                state.loadingMore = false;
                updateLoadMoreControl();
            }
        }
    }

    async function fetchNotificationPage(reset, size, requestVersion) {
        const params = new URLSearchParams();
        const categorySnapshot = state.category;
        const cursorSnapshot = reset ? null : state.nextCursor;
        if (state.isManagement && categorySnapshot) params.set('category', categorySnapshot);
        if (cursorSnapshot) params.set('cursor', String(cursorSnapshot));
        params.set('size', String(size));

        const response = await fetch(API.list + '?' + params.toString(), { headers: ajaxHeaders() });
        const data = await parseResponse(response);
        if (!response.ok) throw new Error(data.message || '알림 조회에 실패했습니다.');
        if (requestVersion !== state.listRequestVersion || categorySnapshot !== state.category) return false;

        const incoming = Array.isArray(data.content) ? data.content : [];
        if (reset) {
            state.items = deduplicateItems(incoming);
        } else {
            state.items = deduplicateItems(state.items.concat(incoming));
        }
        state.nextCursor = data.nextCursor || null;
        state.hasNext = Boolean(data.hasNext);
        renderList();
        return true;
    }

    function deduplicateItems(items) {
        const seen = new Set();
        return items.filter(function (item) {
            const key = String(item && item.id);
            if (!item || !item.id || seen.has(key)) return false;
            seen.add(key);
            return true;
        });
    }

    function renderList() {
        if (!state.items.length) {
            els.list.innerHTML = '<div class="order-notification-empty">확인할 미확인 알림이 없습니다.</div>';
            updateLoadMoreControl();
            return;
        }

        els.list.innerHTML = state.items.map(function (item) {
            const unreadClass = item.read ? '' : ' is-unread';
            const emergencyClass = item.category === 'EMERGENCY' ? ' is-emergency' : '';
            const changes = Array.isArray(item.changes) ? item.changes : [];
            const changeHtml = changes.length
                ? '<div class="order-notification-changes">' + changes.map(renderChange).join('') + '</div>'
                : '';
            const shortcutHtml = item.shortcutEnabled && item.shortcutUrl
                ? [
                    '<div class="order-notification-item-actions">',
                    '  <a class="btn btn-sm btn-primary order-notification-go-btn"',
                    '     href="' + escapeAttr(item.shortcutUrl) + '" data-order-notification-go>',
                    '    <i class="ri-external-link-line me-1"></i>' + escapeHtml(item.shortcutLabel || '해당 발주 바로가기'),
                    '  </a>',
                    '</div>'
                ].join('')
                : '';

            return [
                '<article class="order-notification-item' + unreadClass + emergencyClass + '"',
                ' data-notification-id="' + escapeAttr(item.id) + '" role="button" tabindex="0" aria-expanded="false">',
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
        updateLoadMoreControl();
    }

    function updateLoadMoreControl() {
        if (!els.loadMoreWrap || !els.loadMoreButton) return;
        els.loadMoreWrap.classList.toggle('d-none', !state.hasNext);
        els.loadMoreButton.disabled = state.loadingMore;
        els.loadMoreButton.innerHTML = state.loadingMore
            ? '<span class="spinner-border spinner-border-sm me-1"></span>불러오는 중'
            : '<i class="ri-arrow-down-line me-1"></i>30개 더보기';
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
            if (!href) return;
            shortcut.classList.add('disabled');
            shortcut.setAttribute('aria-disabled', 'true');
            shortcut.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>이동 중';
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
        if (event.target.closest('[data-order-notification-go]')) return;
        if (event.key !== 'Enter' && event.key !== ' ') return;
        const item = event.target.closest('.order-notification-item');
        if (!item) return;
        event.preventDefault();
        item.click();
    }

    async function markNotificationItemRead(item, refreshBadge) {
        if (!item || !item.classList.contains('is-unread')) return true;
        const id = item.getAttribute('data-notification-id');
        if (!id) return false;
        try {
            const response = await fetch(API.read(id), { method: 'POST', headers: ajaxHeaders() });
            if (!response.ok) return false;
            item.classList.remove('is-unread');
            const target = state.items.find(function (row) { return String(row.id) === String(id); });
            if (target) target.read = true;
            if (refreshBadge) await refreshSummary();
            return true;
        } catch (error) {
            console.warn('알림 읽음 처리 실패', error);
            return false;
        }
    }

    async function markLoadedRead() {
        const ids = state.items.map(function (item) { return Number(item.id); })
            .filter(function (id) { return Number.isFinite(id) && id > 0; });
        if (!ids.length) {
            showAlert('success', '현재 화면에 확인할 알림이 없습니다.');
            return;
        }
        setBatchButtonBusy(true);
        try {
            const response = await fetch(API.readLoaded, {
                method: 'POST',
                headers: Object.assign({ 'Content-Type': 'application/json' }, ajaxHeaders()),
                body: JSON.stringify({ notificationIds: ids })
            });
            const data = await parseResponse(response);
            if (!response.ok) throw new Error(data.message || '일괄확인 처리에 실패했습니다.');
            await Promise.all([refreshSummary(), reloadNotifications()]);
        } catch (error) {
            showAlert('error', error.message || '일괄확인 처리에 실패했습니다.');
        } finally {
            setBatchButtonBusy(false);
        }
    }

    function setBatchButtonBusy(busy) {
        if (!els.readAllButton) return;
        els.readAllButton.disabled = busy;
        els.readAllButton.innerHTML = busy
            ? '<span class="spinner-border spinner-border-sm me-1"></span>확인 처리 중'
            : '<i class="ri-check-double-line me-1"></i>현재 표시 알림 일괄확인';
    }

    function updateTabs() {
        els.tabs.forEach(function (tab) {
            tab.classList.toggle('active', tab.getAttribute('data-order-notification-category') === state.category);
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

        state.socket.onopen = function () { state.reconnectDelay = 1000; };
        state.socket.onmessage = function (event) {
            try {
                const payload = JSON.parse(event.data || '{}');
                if (payload.type !== 'ORDER_NOTIFICATION_CREATED' || !payload.notification) return;
                animateBell();
                scheduleSummaryRefresh();
                bufferSocketNotification(payload.notification);
            } catch (error) {
                console.warn('실시간 알림 메시지 처리 실패', error);
            }
        };
        state.socket.onclose = scheduleReconnect;
        state.socket.onerror = function () { try { state.socket.close(); } catch (ignore) {} };
    }

    function bufferSocketNotification(notification) {
        /* 모달이 닫혀 있어도 메모리/DOM 목록에 누적하여 다시 열 때 읽은 항목이 임의로 사라지지 않게 합니다. */
        if (state.isManagement && notification.category !== state.category) return;
        if (!notification.id) return;
        state.socketBuffer.set(String(notification.id), notification);
        window.clearTimeout(state.socketFlushTimer);
        state.socketFlushTimer = window.setTimeout(flushSocketNotifications, 500);
    }

    function flushSocketNotifications() {
        const incoming = Array.from(state.socketBuffer.values());
        state.socketBuffer.clear();
        if (!incoming.length) return;
        incoming.sort(function (a, b) { return Number(b.id || 0) - Number(a.id || 0); });
        state.items = deduplicateItems(incoming.concat(state.items));
        renderList();
        showNewArrival(incoming.length);
    }

    function showNewArrival(count) {
        if (!els.newArrival) return;
        els.newArrival.textContent = '새 알림 ' + count + '건이 도착했습니다.';
        els.newArrival.classList.remove('d-none');
        window.setTimeout(hideNewArrival, 2800);
    }

    function hideNewArrival() {
        if (els.newArrival) els.newArrival.classList.add('d-none');
    }

    function scheduleReconnect() {
        clearTimeout(state.reconnectTimer);
        state.reconnectTimer = window.setTimeout(connectWebSocket, state.reconnectDelay);
        state.reconnectDelay = Math.min(30000, state.reconnectDelay * 2);
    }

    function animateBell() {
        if (!els.bellButton) return;
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
