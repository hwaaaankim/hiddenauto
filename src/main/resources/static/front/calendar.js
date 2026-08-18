document.addEventListener('DOMContentLoaded', function () {
    'use strict';

    const calendarEl = document.getElementById('auto-calendar');
    if (!calendarEl || !window.FullCalendar) {
        console.error('[index-main] FullCalendar 또는 #auto-calendar를 찾을 수 없습니다.');
        return;
    }

    // =========================================================
    // 기준 / 공통 상태
    // =========================================================
    const LS_KEY = 'calendarDateBasis';
    const BASIS = {
        REQUEST: 'REQUEST',
        PROCESS: 'PROCESS'
    };

    const MOBILE_BREAKPOINT = 767;
    const OVERVIEW_DEFAULT_VISIBLE_COUNT = 5;
    const WORK_WINDOW_DEFAULT_VISIBLE_COUNT = 5;

    let currentRange = null;
    let lastOverviewData = null;
    let overviewAbortController = null;
    let detailAbortController = null;
    let workWindowAbortController = null;
    let lastWorkWindowData = null;
    let loadingCount = 0;
    let loadingMessage = '일정 데이터를 불러오는 중입니다.';
    let toastTimer = null;
    let resizeTimer = null;
    let mobileMode = window.innerWidth <= MOBILE_BREAKPOINT;
    const expandedOverviewKeys = new Set();
    const expandedWorkWindowKeys = new Set();

    const btnReq = document.getElementById('index-calendar-basis-request');
    const btnPro = document.getElementById('index-calendar-basis-process');
    const overviewContent = document.getElementById('index-main-overview-content');
    const overviewPeriod = document.getElementById('index-main-overview-period');
    const overviewBasis = document.getElementById('index-main-overview-basis');
    const calendarTitle = document.getElementById('index-main-calendar-title');
    const calendarRangeText = document.getElementById('index-main-calendar-range');
    const heroState = document.getElementById('index-main-hero-state');
    const loadingBar = document.getElementById('index-main-loading-bar');
    const loadingText = document.getElementById('index-main-loading-text');
    const toast = document.getElementById('index-main-toast');
    const workWindowContent = document.getElementById('index-main-work-window-content');
    const workWindowRefreshBtn = document.getElementById('index-main-work-window-refresh');

    function getBasis() {
        const value = localStorage.getItem(LS_KEY);
        return value === BASIS.PROCESS ? BASIS.PROCESS : BASIS.REQUEST;
    }

    function setBasis(value) {
        localStorage.setItem(LS_KEY, value);
    }

    function getBasisText(basis) {
        return basis === BASIS.PROCESS ? '처리일 기준' : '신청일 기준';
    }

    function setButtonActive(basis) {
        if (!btnReq || !btnPro) return;

        const requestActive = basis === BASIS.REQUEST;
        btnReq.classList.toggle('index-main-is-active', requestActive);
        btnPro.classList.toggle('index-main-is-active', !requestActive);
        btnReq.setAttribute('aria-pressed', requestActive ? 'true' : 'false');
        btnPro.setAttribute('aria-pressed', requestActive ? 'false' : 'true');
    }

    // =========================================================
    // 안전 유틸
    // =========================================================
    function escapeHtml(value) {
        if (value == null) return '';
        return String(value)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    function formatNumber(value) {
        const number = Number(value || 0);
        return Number.isFinite(number) ? number.toLocaleString('ko-KR') : '0';
    }

    function formatCurrency(value) {
        return `${formatNumber(value)}원`;
    }

    function parseLocalDate(dateString) {
        if (!dateString) return null;
        const parts = String(dateString).slice(0, 10).split('-').map(Number);
        if (parts.length !== 3 || parts.some(Number.isNaN)) return null;
        return new Date(parts[0], parts[1] - 1, parts[2]);
    }

    function formatDateKo(dateString, includeWeekday) {
        const date = parseLocalDate(dateString);
        if (!date) return dateString || '-';
        return new Intl.DateTimeFormat('ko-KR', {
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            ...(includeWeekday ? { weekday: 'short' } : {})
        }).format(date);
    }

    function formatRangeKo(startString, endExclusiveString) {
        const start = parseLocalDate(startString);
        const endExclusive = parseLocalDate(endExclusiveString);
        if (!start || !endExclusive) return '-';

        const end = new Date(endExclusive.getFullYear(), endExclusive.getMonth(), endExclusive.getDate() - 1);
        const formatter = new Intl.DateTimeFormat('ko-KR', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
        return `${formatter.format(start)} ~ ${formatter.format(end)}`;
    }

    function formatClosedRangeKo(startString, endString) {
        const start = parseLocalDate(startString);
        const end = parseLocalDate(endString);
        if (!start || !end) return '-';
        const formatter = new Intl.DateTimeFormat('ko-KR', {
            month: 'short',
            day: 'numeric'
        });
        return `${formatter.format(start)} ~ ${formatter.format(end)}`;
    }

    function formatWorkDateTime(value) {
        if (!value) return '-';
        const text = String(value);
        const date = parseLocalDate(text);
        if (!date) return text;

        const dateLabel = new Intl.DateTimeFormat('ko-KR', {
            month: 'short',
            day: 'numeric',
            weekday: 'short'
        }).format(date);
        const time = text.length >= 16 ? text.slice(11, 16) : '';
        return time ? `${dateLabel} ${time}` : dateLabel;
    }

    function statusClassName(key) {
        const normalized = String(key || 'unknown')
            .toLowerCase()
            .replaceAll('_', '-');
        return `index-main-status-${normalized}`;
    }

    function getViewRangeFromInfo(info) {
        return {
            start: String(info.startStr || '').slice(0, 10),
            end: String(info.endStr || '').slice(0, 10),
            viewType: info.view ? info.view.type : ''
        };
    }

    function beginLoading(message) {
        loadingCount += 1;
        if (message) loadingMessage = message;

        if (loadingBar) {
            loadingBar.classList.add('index-main-is-active');
            loadingBar.setAttribute('aria-hidden', 'false');
        }
        if (loadingText) {
            loadingText.textContent = loadingMessage;
        }
    }

    function endLoading() {
        loadingCount = Math.max(0, loadingCount - 1);
        if (loadingCount > 0) return;

        window.setTimeout(() => {
            if (loadingCount !== 0) return;
            if (loadingBar) {
                loadingBar.classList.remove('index-main-is-active');
                loadingBar.setAttribute('aria-hidden', 'true');
            }
        }, 180);
    }

    function showToast(message, type) {
        if (!toast) return;
        if (toastTimer) window.clearTimeout(toastTimer);

        toast.textContent = message;
        toast.classList.remove(
            'index-main-toast-info',
            'index-main-toast-success',
            'index-main-toast-error'
        );
        toast.classList.add(`index-main-toast-${type || 'info'}`, 'index-main-is-visible');

        toastTimer = window.setTimeout(() => {
            toast.classList.remove('index-main-is-visible');
        }, 3200);
    }

    function setHeroState(message) {
        if (heroState) heroState.textContent = message;
    }

    async function fetchJson(url, options) {
        const response = await fetch(url, options || {});
        const raw = await response.text();
        let payload = null;

        if (raw) {
            try {
                payload = JSON.parse(raw);
            } catch (_) {
                payload = raw;
            }
        }

        if (!response.ok) {
            const message = payload && typeof payload === 'object' && payload.message
                ? payload.message
                : (typeof payload === 'string' && payload.trim() ? payload.trim() : `HTTP ${response.status}`);
            throw new Error(message);
        }

        return payload;
    }

    // =========================================================
    // 모달
    // =========================================================
    const modal = document.getElementById('auto-modal');
    const modalTitle = document.getElementById('auto-modal-title');
    const modalSubtitle = document.getElementById('index-main-modal-subtitle');
    const modalBody = document.getElementById('auto-modal-body');
    const modalCloseBtn = document.getElementById('auto-close');

    function openModal() {
        if (!modal) return;
        modal.style.display = 'flex';
        modal.setAttribute('aria-hidden', 'false');
        document.body.classList.add('index-main-modal-open');
    }

    function closeModal() {
        if (!modal) return;
        modal.style.display = 'none';
        modal.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('index-main-modal-open');

        if (detailAbortController) {
            detailAbortController.abort();
            detailAbortController = null;
        }
    }

    function renderModalSkeleton(dateStr) {
        if (modalTitle) modalTitle.textContent = formatDateKo(dateStr, true);
        if (modalSubtitle) modalSubtitle.textContent = `${getBasisText(getBasis())} · 상세 데이터를 확인하고 있습니다.`;
        if (!modalBody) return;

        modalBody.innerHTML = `
            <div class="index-main-modal-skeleton" aria-hidden="true">
                <div class="index-main-modal-skeleton-line index-main-modal-skeleton-line-lg"></div>
                <div class="index-main-modal-skeleton-grid">
                    <div class="index-main-modal-skeleton-block"></div>
                    <div class="index-main-modal-skeleton-block"></div>
                    <div class="index-main-modal-skeleton-block"></div>
                </div>
            </div>
        `;
    }

    function renderInfoItem(label, value, wide) {
        return `
            <div class="index-main-modal-info-item ${wide ? 'index-main-modal-info-item-wide' : ''}">
                <span class="index-main-modal-info-label">${escapeHtml(label)}</span>
                <strong class="index-main-modal-info-value">${escapeHtml(value || '-')}</strong>
            </div>
        `;
    }

    function renderAsModalCard(task, index) {
        const scheduledDate = task.scheduledDate || null;
        return `
            <article class="index-main-modal-card index-main-modal-card-as" style="--index-main-delay:${Math.min(index, 8) * 40}ms">
                <div class="index-main-modal-card-header">
                    <div class="index-main-modal-card-title-wrap">
                        <span class="index-main-type-badge index-main-type-badge-as">AS</span>
                        <div>
                            <h4 class="index-main-modal-card-title">${escapeHtml(task.productName || task.title || 'AS 접수')}</h4>
                            <p class="index-main-modal-card-caption">AS #${escapeHtml(task.id ?? '-')} · ${escapeHtml(task.customerName || '고객명 미등록')}</p>
                        </div>
                    </div>
                    <span class="index-main-modal-card-chip">${scheduledDate ? `방문 ${escapeHtml(scheduledDate)}` : '방문일 미정'}</span>
                </div>

                <div class="index-main-modal-section-grid">
                    <section class="index-main-modal-section">
                        <div class="index-main-modal-section-head">
                            <span class="index-main-modal-section-icon index-main-modal-section-icon-product"></span>
                            <div>
                                <h5>제품 정보</h5>
                                <p>접수된 제품과 증상 정보입니다.</p>
                            </div>
                        </div>
                        <div class="index-main-modal-info-grid">
                            ${renderInfoItem('제품명', task.productName || '-')}
                            ${renderInfoItem('사이즈', task.productSize || '-')}
                            ${renderInfoItem('컬러', task.productColor || '-')}
                            ${renderInfoItem('옵션', task.productOptions || '-', true)}
                            ${renderInfoItem('증상', task.symptom || '-', true)}
                        </div>
                    </section>

                    <section class="index-main-modal-section">
                        <div class="index-main-modal-section-head">
                            <span class="index-main-modal-section-icon index-main-modal-section-icon-customer"></span>
                            <div>
                                <h5>고객 · 현장 정보</h5>
                                <p>현장 방문에 필요한 기본 정보입니다.</p>
                            </div>
                        </div>
                        <div class="index-main-modal-info-grid">
                            ${renderInfoItem('고객 성함', task.customerName || '-')}
                            ${renderInfoItem('신청일', task.requestedAt || '-')}
                            ${renderInfoItem('현장 연락처', task.onsiteContact || '-')}
                            ${scheduledDate ? renderInfoItem('방문 예정일', scheduledDate) : ''}
                            ${renderInfoItem('주소', task.address || '-', true)}
                        </div>
                    </section>

                    <section class="index-main-modal-section">
                        <div class="index-main-modal-section-head">
                            <span class="index-main-modal-section-icon index-main-modal-section-icon-handler"></span>
                            <div>
                                <h5>AS 담당자</h5>
                                <p>현재 배정된 담당자 정보입니다.</p>
                            </div>
                        </div>
                        <div class="index-main-modal-info-grid">
                            ${renderInfoItem('담당자', task.handlerName || '미배정')}
                            ${renderInfoItem('연락처', task.handlerContact || '-')}
                        </div>
                    </section>
                </div>
            </article>
        `;
    }

    function renderTaskModalCard(task, index) {
        const orders = Array.isArray(task.orders) ? task.orders : [];
        const orderHtml = orders.map((order, orderIndex) => {
            const showDeliveryHandler = Boolean(order && order.deliveryHandlerVisible);
            const productName = order && order.productName ? order.productName : '-';
            const productSize = order && order.productSize ? order.productSize : '-';
            const productColor = order && order.productColor ? order.productColor : '-';
            const quantity = order && order.quantity != null ? `${formatNumber(order.quantity)}개` : '-';

            return `
                <div class="index-main-modal-order-card" style="--index-main-delay:${Math.min(orderIndex, 8) * 30}ms">
                    <div class="index-main-modal-order-top">
                        <span class="index-main-modal-order-id">ORDER #${escapeHtml(order.orderId ?? '-')}</span>
                        <strong class="index-main-modal-order-price">${order.price != null ? formatCurrency(order.price) : '-'}</strong>
                    </div>

                    <div class="index-main-modal-order-product">
                        <span class="index-main-modal-order-product-label">PRODUCT</span>
                        <strong class="index-main-modal-order-product-name">${escapeHtml(productName)}</strong>
                        <div class="index-main-modal-order-product-facts">
                            <span><em>사이즈</em><b>${escapeHtml(productSize)}</b></span>
                            <span><em>색상</em><b>${escapeHtml(productColor)}</b></span>
                            <span><em>수량</em><b>${escapeHtml(quantity)}</b></span>
                        </div>
                    </div>

                    <div class="index-main-modal-order-grid">
                        ${renderInfoItem('배송수단', order.deliveryMethodName || '-')}
                        ${renderInfoItem('배송 희망일', order.preferredDeliveryDate || '-')}
                        ${showDeliveryHandler ? renderInfoItem('배송팀 담당자', order.deliveryHandlerName || '미배정') : ''}
                        ${showDeliveryHandler ? renderInfoItem('담당자 연락처', order.deliveryHandlerContact || '-') : ''}
                        ${renderInfoItem('배송지', order.address || '-', true)}
                        ${renderInfoItem('주문일', order.createdAt || '-')}
                        ${renderInfoItem('카테고리', order.categoryName || '-')}
                    </div>
                </div>
            `;
        }).join('');

        return `
            <article class="index-main-modal-card index-main-modal-card-task" style="--index-main-delay:${Math.min(index, 8) * 40}ms">
                <div class="index-main-modal-card-header">
                    <div class="index-main-modal-card-title-wrap">
                        <span class="index-main-type-badge index-main-type-badge-task">발주</span>
                        <div>
                            <h4 class="index-main-modal-card-title">TASK #${escapeHtml(task.id ?? '-')}</h4>
                            <p class="index-main-modal-card-caption">포함 오더 ${formatNumber(orders.length)}건</p>
                        </div>
                    </div>
                    <span class="index-main-modal-card-chip">발주 상세</span>
                </div>
                <div class="index-main-modal-order-list">
                    ${orderHtml || '<div class="index-main-empty-state">등록된 오더 정보가 없습니다.</div>'}
                </div>
            </article>
        `;
    }

    function renderModalData(dateStr, data) {
        const list = Array.isArray(data) ? data : [];
        const asCount = list.filter(item => item && item.type === 'AS').length;
        const taskCount = list.filter(item => item && item.type === 'TASK').length;

        if (modalTitle) modalTitle.textContent = formatDateKo(dateStr, true);
        if (modalSubtitle) {
            modalSubtitle.textContent = `${getBasisText(getBasis())} · AS ${asCount}건 · 발주 ${taskCount}건`;
        }

        if (!modalBody) return;
        if (list.length === 0) {
            modalBody.innerHTML = `
                <div class="index-main-empty-state index-main-empty-state-modal">
                    <span class="index-main-empty-icon"></span>
                    <strong>이 날짜에는 표시할 일정이 없습니다.</strong>
                    <p>다른 날짜를 선택하거나 조회 기준을 변경해 주세요.</p>
                </div>
            `;
            return;
        }

        modalBody.innerHTML = list.map((task, index) => {
            if (task.type === 'AS') return renderAsModalCard(task, index);
            if (task.type === 'TASK') return renderTaskModalCard(task, index);
            return '';
        }).join('');
        modalBody.scrollTop = 0;
    }

    async function loadTaskDetails(dateStr) {
        const basis = getBasis();

        if (detailAbortController) detailAbortController.abort();
        detailAbortController = new AbortController();

        openModal();
        renderModalSkeleton(dateStr);
        beginLoading('선택한 날짜의 상세 일정을 불러오는 중입니다.');

        try {
            const data = await fetchJson(
                `/api/v1/calendar/tasks?date=${encodeURIComponent(dateStr)}&basis=${encodeURIComponent(basis)}`,
                { signal: detailAbortController.signal }
            );
            renderModalData(dateStr, data);
        } catch (error) {
            if (error && error.name === 'AbortError') return;
            console.error('[index-main] 일정 상세 조회 실패:', error);
            if (modalBody) {
                modalBody.innerHTML = `
                    <div class="index-main-empty-state index-main-empty-state-error">
                        <strong>일정 정보를 불러오지 못했습니다.</strong>
                        <p>잠시 후 다시 시도해 주세요.</p>
                    </div>
                `;
            }
            showToast('일정 상세 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.', 'error');
        } finally {
            endLoading();
        }
    }

    // =========================================================
    // 최근 완료 / 앞으로 7일 처리 예정
    // =========================================================
    function renderWorkWindowSkeleton() {
        if (!workWindowContent) return;
        workWindowContent.innerHTML = `
            <div class="index-main-work-grid index-main-work-grid-skeleton" aria-hidden="true">
                <div class="index-main-work-skeleton-panel">
                    <div class="index-main-work-skeleton-title"></div>
                    <div class="index-main-work-skeleton-row"></div>
                    <div class="index-main-work-skeleton-row"></div>
                    <div class="index-main-work-skeleton-row"></div>
                </div>
                <div class="index-main-work-skeleton-panel">
                    <div class="index-main-work-skeleton-title"></div>
                    <div class="index-main-work-skeleton-row"></div>
                    <div class="index-main-work-skeleton-row"></div>
                    <div class="index-main-work-skeleton-row"></div>
                </div>
            </div>
        `;
    }

    function buildWorkSearchUrl(item) {
        if (!item) return '';

        if (item.type === 'AS' && item.id != null) {
            return `/customer/asList?textType=id&keyword=${encodeURIComponent(String(item.id))}`;
        }

        if (item.type === 'ORDER' && item.taskId != null) {
            return `/customer/taskList?textType=taskId&keyword=${encodeURIComponent(String(item.taskId))}`;
        }

        return '';
    }

    function renderWorkItem(item, index, recent) {
        const isAs = item && item.type === 'AS';
        const typeLabel = isAs ? 'AS' : '발주';
        const title = item && item.title ? item.title : (isAs ? 'AS 업무' : '발주 업무');
        const statusKey = item && item.statusKey ? item.statusKey : 'UNKNOWN';
        const amount = Number(item && item.amount ? item.amount : 0);
        const searchUrl = buildWorkSearchUrl(item);
        const meta = [];

        if (item && item.region) meta.push(item.region);

        if (!isAs && item && item.taskId) {
            meta.push(`TASK #${item.taskId}`);
        } else if (isAs && item && item.id) {
            meta.push(`AS #${item.id}`);
        }

        if (item && item.contactName) {
            meta.push(`${isAs ? '고객' : '주문자'} ${item.contactName}`);
        }
        if (item && item.contactPhone) {
            meta.push(item.contactPhone);
        }

        return `
            <article class="index-main-work-item ${isAs ? 'index-main-work-item-as' : 'index-main-work-item-order'}"
                     style="--index-main-delay:${Math.min(index, 12) * 34}ms"
                     ${searchUrl ? `data-index-main-work-url="${escapeHtml(searchUrl)}" role="link" tabindex="0" aria-label="${escapeHtml(typeLabel + ' 목록에서 보기')}" title="클릭하면 해당 ${escapeHtml(typeLabel)} 검색 화면으로 이동합니다."` : ''}>
                <div class="index-main-work-item-rail" aria-hidden="true"></div>
                <div class="index-main-work-item-main">
                    <div class="index-main-work-item-top">
                        <div class="index-main-work-item-title-wrap">
                            <span class="index-main-type-badge ${isAs ? 'index-main-type-badge-as' : 'index-main-type-badge-task'}">${typeLabel}</span>
                            <div>
                                <h4 title="${escapeHtml(title)}">${escapeHtml(title)}</h4>
                                <p>${escapeHtml(item && item.description ? item.description : (recent ? '처리 완료' : '처리 예정'))}</p>
                            </div>
                        </div>
                        <time class="index-main-work-item-date" datetime="${escapeHtml(item && item.dateTime ? item.dateTime : '')}">
                            ${escapeHtml(formatWorkDateTime(item && item.dateTime ? item.dateTime : item && item.date))}
                        </time>
                    </div>

                    <div class="index-main-work-item-bottom">
                        <div class="index-main-work-item-meta">
                            ${meta.slice(0, 4).map(value => `<span>${escapeHtml(value)}</span>`).join('')}
                        </div>
                        <div class="index-main-work-item-side">
                            ${amount > 0 ? `<strong class="index-main-work-item-amount">${formatCurrency(amount)}</strong>` : ''}
                            <span class="index-main-work-status ${statusClassName(statusKey)}">${escapeHtml(item && item.statusLabel ? item.statusLabel : '상태 미지정')}</span>
                        </div>
                    </div>
                </div>
            </article>
        `;
    }

    function renderWorkPanelIcon(recent) {
        const iconClass = recent ? 'index-main-work-panel-icon-done' : 'index-main-work-panel-icon-next';
        const iconShape = recent
            ? '<polyline points="7.5 12.2 10.6 15.2 16.8 8.8"></polyline>'
            : '<polyline points="8 10 12 14 16 10"></polyline>';

        return `
            <span class="index-main-work-panel-icon ${iconClass}" aria-hidden="true">
                <svg class="index-main-work-panel-icon-svg" viewBox="0 0 24 24" focusable="false">
                    <rect x="4.5" y="4.5" width="15" height="15" rx="4"></rect>
                    ${iconShape}
                </svg>
            </span>
        `;
    }

    function renderWorkColumn(config) {
        const items = Array.isArray(config.items) ? config.items : [];
        const expanded = expandedWorkWindowKeys.has(config.key);
        const visibleItems = expanded ? items : items.slice(0, WORK_WINDOW_DEFAULT_VISIBLE_COUNT);
        const hasMore = items.length > WORK_WINDOW_DEFAULT_VISIBLE_COUNT;

        return `
            <section class="index-main-work-panel ${config.recent ? 'index-main-work-panel-recent' : 'index-main-work-panel-upcoming'} ${expanded ? 'index-main-is-expanded' : ''}">
                <header class="index-main-work-panel-head">
                    <div class="index-main-work-panel-title-wrap">
                        ${renderWorkPanelIcon(config.recent)}
                        <div>
                            <span class="index-main-work-panel-kicker">${config.recent ? 'RECENTLY COMPLETED' : 'NEXT 7 DAYS'}</span>
                            <h3>${escapeHtml(config.title)}</h3>
                            <p>${escapeHtml(config.period)}</p>
                        </div>
                    </div>
                    <strong class="index-main-work-panel-count">${formatNumber(items.length)}<small>건</small></strong>
                </header>

                <div class="index-main-work-panel-summary">
                    <span>발주 <strong>${formatNumber(config.orderCount)}</strong></span>
                    <span>AS <strong>${formatNumber(config.asCount)}</strong></span>
                    <span>${config.recent ? '오늘 포함 과거 7일' : '오늘 포함 향후 7일'}</span>
                </div>

                <div class="index-main-work-list ${expanded ? 'index-main-is-expanded' : ''}">
                    ${visibleItems.map((item, index) => renderWorkItem(item, index, config.recent)).join('')}
                    ${items.length === 0 ? `
                        <div class="index-main-work-empty">
                            <span class="index-main-work-empty-icon"></span>
                            <strong>${config.recent ? '최근 완료된 업무가 없습니다.' : '앞으로 7일 내 예정된 업무가 없습니다.'}</strong>
                            <p>${config.recent ? '배송완료 또는 AS 완료 데이터가 생기면 여기에 표시됩니다.' : '배송희망일 또는 AS 방문 일정이 등록되면 여기에 표시됩니다.'}</p>
                        </div>
                    ` : ''}
                </div>

                <footer class="index-main-work-panel-footer">
                    ${hasMore ? `
                        <button type="button" class="index-main-more-button"
                                data-index-main-work-more="${escapeHtml(config.key)}"
                                aria-expanded="${expanded ? 'true' : 'false'}">
                            <span>${expanded ? '접기' : `더보기 · ${formatNumber(items.length - WORK_WINDOW_DEFAULT_VISIBLE_COUNT)}건`}</span>
                            <span class="index-main-more-chevron ${expanded ? 'index-main-is-expanded' : ''}" aria-hidden="true"></span>
                        </button>
                    ` : `<span class="index-main-rank-total">표시된 업무 ${formatNumber(items.length)}건</span>`}
                </footer>
            </section>
        `;
    }

    function renderWorkWindow(data) {
        if (!workWindowContent) return;
        lastWorkWindowData = data || {};

        const recentItems = Array.isArray(data && data.recentCompleted) ? data.recentCompleted : [];
        const upcomingItems = Array.isArray(data && data.upcoming) ? data.upcoming : [];

        workWindowContent.innerHTML = `
            <div class="index-main-work-grid">
                ${renderWorkColumn({
                    key: 'recent',
                    title: '최근 7일 처리완료',
                    period: formatClosedRangeKo(data && data.recentStartDate, data && data.recentEndDate),
                    items: recentItems,
                    orderCount: data && data.recentOrderCount || 0,
                    asCount: data && data.recentAsCount || 0,
                    recent: true
                })}
                ${renderWorkColumn({
                    key: 'upcoming',
                    title: '앞으로 7일 처리예정',
                    period: formatClosedRangeKo(data && data.upcomingStartDate, data && data.upcomingEndDate),
                    items: upcomingItems,
                    orderCount: data && data.upcomingOrderCount || 0,
                    asCount: data && data.upcomingAsCount || 0,
                    recent: false
                })}
            </div>
        `;
    }

    async function loadWorkWindow(announce) {
        if (!workWindowContent) return;

        if (workWindowAbortController) workWindowAbortController.abort();
        workWindowAbortController = new AbortController();
        expandedWorkWindowKeys.clear();

        if (!lastWorkWindowData) renderWorkWindowSkeleton();
        workWindowContent.classList.add('index-main-is-refreshing');
        if (workWindowRefreshBtn) workWindowRefreshBtn.classList.add('index-main-is-spinning');
        beginLoading('최근 완료 및 예정 업무를 확인하는 중입니다.');

        try {
            const data = await fetchJson('/api/v1/calendar/work-window', {
                signal: workWindowAbortController.signal
            });
            renderWorkWindow(data || {});
            if (announce) showToast('최근 완료 및 예정 업무를 새로 불러왔습니다.', 'success');
        } catch (error) {
            if (error && error.name === 'AbortError') return;
            console.error('[index-main] 최근/예정 업무 조회 실패:', error);
            if (!lastWorkWindowData) {
                workWindowContent.innerHTML = `
                    <div class="index-main-empty-state index-main-empty-state-error">
                        <strong>최근/예정 업무를 불러오지 못했습니다.</strong>
                        <p>달력과 오버뷰는 계속 사용할 수 있습니다. 우측 새로고침 버튼으로 다시 시도해 주세요.</p>
                    </div>
                `;
            }
            showToast('최근/예정 업무 조회에 실패했습니다.', 'error');
        } finally {
            workWindowContent.classList.remove('index-main-is-refreshing');
            if (workWindowRefreshBtn) workWindowRefreshBtn.classList.remove('index-main-is-spinning');
            endLoading();
        }
    }

    if (workWindowContent) {
        workWindowContent.addEventListener('click', function (event) {
            const moreButton = event.target.closest('[data-index-main-work-more]');
            if (moreButton) {
                const key = moreButton.getAttribute('data-index-main-work-more');
                if (!key) return;

                if (expandedWorkWindowKeys.has(key)) expandedWorkWindowKeys.delete(key);
                else expandedWorkWindowKeys.add(key);

                if (lastWorkWindowData) renderWorkWindow(lastWorkWindowData);
                return;
            }

            const workItem = event.target.closest('[data-index-main-work-url]');
            if (!workItem) return;

            const url = workItem.getAttribute('data-index-main-work-url');
            if (url) window.location.href = url;
        });

        workWindowContent.addEventListener('keydown', function (event) {
            if (event.key !== 'Enter' && event.key !== ' ') return;

            const workItem = event.target.closest('[data-index-main-work-url]');
            if (!workItem) return;

            const url = workItem.getAttribute('data-index-main-work-url');
            if (!url) return;

            event.preventDefault();
            window.location.href = url;
        });
    }

    if (workWindowRefreshBtn) {
        workWindowRefreshBtn.addEventListener('click', function () {
            loadWorkWindow(true);
        });
    }

    // =========================================================
    // 오버뷰
    // =========================================================
    function renderOverviewSkeleton() {
        if (!overviewContent) return;
        overviewContent.innerHTML = `
            <div class="index-main-overview-skeleton">
                <div class="index-main-overview-skeleton-row">
                    <div class="index-main-overview-skeleton-card"></div>
                    <div class="index-main-overview-skeleton-card"></div>
                    <div class="index-main-overview-skeleton-card"></div>
                    <div class="index-main-overview-skeleton-card"></div>
                </div>
                <div class="index-main-overview-skeleton-panel"></div>
                <div class="index-main-overview-skeleton-panel"></div>
            </div>
        `;
    }

    function getMaxCount(items) {
        return Math.max(1, ...(Array.isArray(items) ? items.map(item => Number(item.count || 0)) : [0]));
    }

    function renderStatusStrip(items, kind) {
        const safeItems = Array.isArray(items) ? items : [];
        return `
            <div class="index-main-status-strip">
                ${safeItems.map(item => `
                    <div class="index-main-status-chip ${statusClassName(item.key)}">
                        <span>${escapeHtml(item.label || item.key || '미지정')}</span>
                        <strong>${formatNumber(item.count)}</strong>
                    </div>
                `).join('')}
                ${safeItems.length === 0 ? `<span class="index-main-status-empty">${kind} 상태 데이터가 없습니다.</span>` : ''}
            </div>
        `;
    }

    function renderRankCard(config) {
        const items = Array.isArray(config.items) ? config.items : [];
        const expanded = expandedOverviewKeys.has(config.key);
        const visibleItems = expanded ? items : items.slice(0, OVERVIEW_DEFAULT_VISIBLE_COUNT);
        const maxCount = getMaxCount(items);
        const hasMore = items.length > OVERVIEW_DEFAULT_VISIBLE_COUNT;

        return `
            <article class="index-main-rank-card ${expanded ? 'index-main-is-expanded' : ''}" data-index-main-rank-card="${escapeHtml(config.key)}">
                <div class="index-main-rank-card-head">
                    <div>
                        <span class="index-main-rank-card-kicker">${escapeHtml(config.kicker || 'OVERVIEW')}</span>
                        <h4>${escapeHtml(config.title)}</h4>
                        <p>${escapeHtml(config.subtitle || '')}</p>
                    </div>
                    <span class="index-main-rank-card-icon ${escapeHtml(config.iconClass || '')}"></span>
                </div>

                <div class="index-main-rank-list ${expanded ? 'index-main-is-expanded' : ''}">
                    ${visibleItems.map((item, index) => {
                        const count = Number(item.count || 0);
                        const width = Math.max(count > 0 ? 8 : 0, Math.round((count / maxCount) * 100));
                        return `
                            <div class="index-main-rank-row" style="--index-main-delay:${Math.min(index, 12) * 26}ms">
                                <div class="index-main-rank-row-main">
                                    <span class="index-main-rank-number">${index + 1}</span>
                                    <span class="index-main-rank-label" title="${escapeHtml(item.label || '-')}">${escapeHtml(item.label || '-')}</span>
                                    <strong>${formatNumber(count)}건</strong>
                                </div>
                                <div class="index-main-rank-track" aria-hidden="true">
                                    <span style="width:${width}%"></span>
                                </div>
                            </div>
                        `;
                    }).join('')}

                    ${items.length === 0 ? `
                        <div class="index-main-rank-empty">
                            <span class="index-main-rank-empty-icon"></span>
                            <p>집계할 데이터가 없습니다.</p>
                        </div>
                    ` : ''}
                </div>

                <div class="index-main-rank-card-footer">
                    ${hasMore ? `
                        <button type="button"
                                class="index-main-more-button"
                                data-index-main-overview-more="${escapeHtml(config.key)}"
                                aria-expanded="${expanded ? 'true' : 'false'}">
                            <span>${expanded ? '접기' : `더보기 · ${formatNumber(items.length - OVERVIEW_DEFAULT_VISIBLE_COUNT)}개`}</span>
                            <span class="index-main-more-chevron ${expanded ? 'index-main-is-expanded' : ''}" aria-hidden="true"></span>
                        </button>
                    ` : `
                        <span class="index-main-rank-total">전체 ${formatNumber(items.length)}개 항목</span>
                    `}
                </div>
            </article>
        `;
    }

    function renderOverview(data) {
        if (!overviewContent) return;
        lastOverviewData = data || {};

        const order = data && data.order ? data.order : {};
        const asData = data && data.as ? data.as : {};
        const totalAmount = Number(order.totalAmount || 0) + Number(asData.totalAmount || 0);

        const orderTaskCount = Number(order.taskCount || 0);
        const orderCount = Number(order.orderCount || 0);
        const asCount = Number(asData.totalCount || 0);

        overviewContent.innerHTML = `
            <div class="index-main-kpi-grid">
                <article class="index-main-kpi-card index-main-kpi-card-order">
                    <div class="index-main-kpi-icon index-main-kpi-icon-order"></div>
                    <div>
                        <span>발주서</span>
                        <strong>${formatNumber(orderTaskCount)}<small>건</small></strong>
                        <p>포함 오더 ${formatNumber(orderCount)}건</p>
                    </div>
                </article>

                <article class="index-main-kpi-card index-main-kpi-card-as">
                    <div class="index-main-kpi-icon index-main-kpi-icon-as"></div>
                    <div>
                        <span>AS</span>
                        <strong>${formatNumber(asCount)}<small>건</small></strong>
                        <p>유상 ${formatNumber(asData.chargedCount || 0)}건 · 무상/미정 ${formatNumber(asData.zeroPriceCount || 0)}건</p>
                    </div>
                </article>

                <article class="index-main-kpi-card index-main-kpi-card-money">
                    <div class="index-main-kpi-icon index-main-kpi-icon-money"></div>
                    <div>
                        <span>발주 총 금액</span>
                        <strong class="index-main-kpi-money-value">${formatCurrency(order.totalAmount || 0)}</strong>
                        <p>Order.totalAmount 합계</p>
                    </div>
                </article>

                <article class="index-main-kpi-card index-main-kpi-card-total">
                    <div class="index-main-kpi-icon index-main-kpi-icon-total"></div>
                    <div>
                        <span>조회기간 금액 합계</span>
                        <strong class="index-main-kpi-money-value">${formatCurrency(totalAmount)}</strong>
                        <p>발주 + 유상 AS</p>
                    </div>
                </article>
            </div>

            <section class="index-main-overview-domain index-main-overview-domain-order">
                <div class="index-main-overview-domain-head">
                    <div>
                        <span class="index-main-domain-kicker">ORDER INSIGHT</span>
                        <h3>발주 오버뷰</h3>
                        <p>현재 달력 기간에 포함된 발주서와 오더를 상태, 옵션 카테고리, 배송 정보로 정리했습니다.</p>
                    </div>
                    <div class="index-main-domain-summary">
                        <span>발주서 <strong>${formatNumber(orderTaskCount)}</strong></span>
                        <span>오더 <strong>${formatNumber(orderCount)}</strong></span>
                        <span>금액 <strong>${formatCurrency(order.totalAmount || 0)}</strong></span>
                    </div>
                </div>

                <div class="index-main-domain-status-wrap">
                    <span class="index-main-domain-status-label">오더 상태</span>
                    ${renderStatusStrip(order.statusCounts, '오더')}
                </div>

                <div class="index-main-rank-grid">
                    ${renderRankCard({
                        key: 'order-category',
                        kicker: 'OPTION JSON',
                        title: '카테고리별 발주',
                        subtitle: 'OrderItem.optionJson의 “카테고리” 값을 기준으로 집계합니다.',
                        items: order.categoryCounts,
                        iconClass: 'index-main-rank-icon-category'
                    })}
                    ${renderRankCard({
                        key: 'order-region',
                        kicker: 'DELIVERY AREA',
                        title: '배송 지역별 발주',
                        subtitle: '배송지의 도/시 + 시/군 단위로 묶어 보여줍니다.',
                        items: order.regionCounts,
                        iconClass: 'index-main-rank-icon-region'
                    })}
                    ${renderRankCard({
                        key: 'order-method',
                        kicker: 'DELIVERY METHOD',
                        title: '배송수단별 발주',
                        subtitle: '현장, 화물, 방문, 택배 등 실제 배송수단 기준입니다.',
                        items: order.deliveryMethodCounts,
                        iconClass: 'index-main-rank-icon-method'
                    })}
                </div>
            </section>

            <section class="index-main-overview-domain index-main-overview-domain-as">
                <div class="index-main-overview-domain-head">
                    <div>
                        <span class="index-main-domain-kicker">AS INSIGHT</span>
                        <h3>AS 오버뷰</h3>
                        <p>AS 상태와 제품, 지역, 청구대상을 한 화면에서 확인할 수 있습니다.</p>
                    </div>
                    <div class="index-main-domain-summary">
                        <span>전체 <strong>${formatNumber(asCount)}</strong></span>
                        <span>유상 <strong>${formatNumber(asData.chargedCount || 0)}</strong></span>
                        <span>금액 <strong>${formatCurrency(asData.totalAmount || 0)}</strong></span>
                    </div>
                </div>

                <div class="index-main-domain-status-wrap">
                    <span class="index-main-domain-status-label">AS 상태</span>
                    ${renderStatusStrip(asData.statusCounts, 'AS')}
                </div>

                <div class="index-main-as-money-grid">
                    <div class="index-main-as-money-item">
                        <span>유상 총액</span>
                        <strong>${formatCurrency(asData.totalAmount || 0)}</strong>
                        <small>${formatNumber(asData.chargedCount || 0)}건</small>
                    </div>
                    <div class="index-main-as-money-item">
                        <span>수납 완료액</span>
                        <strong>${formatCurrency(asData.collectedAmount || 0)}</strong>
                        <small>paymentCollected = true</small>
                    </div>
                    <div class="index-main-as-money-item">
                        <span>미수납액</span>
                        <strong>${formatCurrency(asData.uncollectedAmount || 0)}</strong>
                        <small>유상 건 중 미수납</small>
                    </div>
                    <div class="index-main-as-money-item">
                        <span>유상 평균 비용</span>
                        <strong>${formatCurrency(asData.averageChargedAmount || 0)}</strong>
                        <small>최고 ${formatCurrency(asData.maxChargedAmount || 0)}</small>
                    </div>
                    <div class="index-main-as-money-item index-main-as-money-item-soft">
                        <span>무상 / 미정</span>
                        <strong>${formatNumber(asData.zeroPriceCount || 0)}건</strong>
                        <small>현재 모델 규칙상 price = 0</small>
                    </div>
                </div>

                <div class="index-main-rank-grid">
                    ${renderRankCard({
                        key: 'as-product',
                        kicker: 'PRODUCT',
                        title: '제품별 AS',
                        subtitle: 'AS 접수 제품명을 기준으로 빈도를 비교합니다.',
                        items: asData.productCounts,
                        iconClass: 'index-main-rank-icon-product'
                    })}
                    ${renderRankCard({
                        key: 'as-region',
                        kicker: 'PROVINCE / CITY',
                        title: '지역별 AS',
                        subtitle: 'province / city에 해당하는 도/시 + 시/군 단위 집계입니다.',
                        items: asData.regionCounts,
                        iconClass: 'index-main-rank-icon-region'
                    })}
                    ${renderRankCard({
                        key: 'as-billing',
                        kicker: 'BILLING TARGET',
                        title: '청구대상별 AS',
                        subtitle: '등록된 AsBillingTarget의 한글 라벨을 기준으로 집계합니다.',
                        items: asData.billingTargetCounts,
                        iconClass: 'index-main-rank-icon-billing'
                    })}
                </div>
            </section>
        `;
    }

    async function loadOverview(range, announce) {
        if (!range || !range.start || !range.end) return;

        if (overviewAbortController) overviewAbortController.abort();
        overviewAbortController = new AbortController();

        if (!lastOverviewData) renderOverviewSkeleton();
        if (overviewContent) overviewContent.classList.add('index-main-is-refreshing');

        const basis = getBasis();
        beginLoading('현재 달력 기간의 오버뷰를 계산하는 중입니다.');

        try {
            const url = `/api/v1/calendar/overview?basis=${encodeURIComponent(basis)}`
                + `&start=${encodeURIComponent(range.start)}`
                + `&end=${encodeURIComponent(range.end)}`;
            const data = await fetchJson(url, { signal: overviewAbortController.signal });
            renderOverview(data);

            const rangeLabel = formatRangeKo(range.start, range.end);
            if (overviewPeriod) overviewPeriod.textContent = rangeLabel;
            if (overviewBasis) overviewBasis.textContent = getBasisText(basis);
            setHeroState(`${rangeLabel} · ${getBasisText(basis)} 데이터가 동기화되었습니다.`);

            if (announce) {
                showToast(`${getBasisText(basis)} 오버뷰를 새로 불러왔습니다.`, 'success');
            }
        } catch (error) {
            if (error && error.name === 'AbortError') return;
            console.error('[index-main] 오버뷰 조회 실패:', error);
            if (!lastOverviewData && overviewContent) {
                overviewContent.innerHTML = `
                    <div class="index-main-empty-state index-main-empty-state-error">
                        <strong>오버뷰를 불러오지 못했습니다.</strong>
                        <p>달력 일정은 계속 사용할 수 있습니다. 잠시 후 다시 조회해 주세요.</p>
                    </div>
                `;
            }
            setHeroState('오버뷰 갱신 중 문제가 발생했습니다. 달력 일정은 계속 사용할 수 있습니다.');
            showToast('오버뷰 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.', 'error');
        } finally {
            if (overviewContent) overviewContent.classList.remove('index-main-is-refreshing');
            endLoading();
        }
    }

    if (overviewContent) {
        overviewContent.addEventListener('click', function (event) {
            const button = event.target.closest('[data-index-main-overview-more]');
            if (!button) return;

            const key = button.getAttribute('data-index-main-overview-more');
            if (!key) return;

            if (expandedOverviewKeys.has(key)) {
                expandedOverviewKeys.delete(key);
            } else {
                expandedOverviewKeys.add(key);
            }

            if (lastOverviewData) renderOverview(lastOverviewData);
        });
    }

    // =========================================================
    // FullCalendar
    // =========================================================
    function buildCalendarEvent(kind, count, date) {
        const isAs = kind === 'AS';
        return {
            title: isAs ? 'AS' : '발주',
            start: date,
            allDay: true,
            classNames: [
                'index-main-calendar-event-shell',
                isAs ? 'index-main-calendar-event-shell-as' : 'index-main-calendar-event-shell-task'
            ],
            extendedProps: {
                type: kind,
                count: Number(count || 0)
            }
        };
    }

    function renderCalendarEvent(info) {
        const type = info.event.extendedProps.type;
        const count = Number(info.event.extendedProps.count || 0);
        const isAs = type === 'AS';
        const label = isAs ? 'AS' : '발주';

        return {
            html: `
                <div class="index-main-calendar-event ${isAs ? 'index-main-calendar-event-as' : 'index-main-calendar-event-task'}"
                     aria-label="${label} ${count}건">
                    <span class="index-main-calendar-event-icon" aria-hidden="true"></span>
                    <span class="index-main-calendar-event-label">${label}</span>
                    <sup class="index-main-calendar-event-count">${formatNumber(count)}</sup>
                </div>
            `
        };
    }

    const calendar = new FullCalendar.Calendar(calendarEl, {
        initialView: mobileMode ? 'dayGridDay' : 'dayGridMonth',
        locale: 'ko',
        headerToolbar: false,
        editable: false,
        selectable: false,
        height: 'auto',
        fixedWeekCount: false,
        showNonCurrentDates: true,
        dayMaxEvents: false,
        navLinks: false,
        eventDisplay: 'block',
        eventOrder: 'type',

        loading: function (isLoading) {
            if (isLoading) {
                beginLoading('달력 일정을 불러오는 중입니다.');
            } else {
                endLoading();
            }
        },

        datesSet: function (info) {
            currentRange = getViewRangeFromInfo(info);
            expandedOverviewKeys.clear();
            expandedWorkWindowKeys.clear();
            if (lastWorkWindowData) renderWorkWindow(lastWorkWindowData);

            if (calendarTitle) calendarTitle.textContent = info.view.title;
            const rangeLabel = formatRangeKo(currentRange.start, currentRange.end);
            if (calendarRangeText) calendarRangeText.textContent = rangeLabel;
            if (overviewPeriod) overviewPeriod.textContent = rangeLabel;
            if (overviewBasis) overviewBasis.textContent = getBasisText(getBasis());

            loadOverview(currentRange, false);
        },

        events: function (fetchInfo, successCallback, failureCallback) {
            const basis = getBasis();
            const start = String(fetchInfo.startStr || '').slice(0, 10);
            const end = String(fetchInfo.endStr || '').slice(0, 10);

            const url = `/api/v1/calendar/events?basis=${encodeURIComponent(basis)}`
                + `&start=${encodeURIComponent(start)}`
                + `&end=${encodeURIComponent(end)}`;

            fetchJson(url)
                .then(events => {
                    const calendarEvents = [];
                    (events || []).forEach(item => {
                        if (Number(item.asCount || 0) > 0) {
                            calendarEvents.push(buildCalendarEvent('AS', item.asCount, item.date));
                        }
                        if (Number(item.taskCount || 0) > 0) {
                            calendarEvents.push(buildCalendarEvent('TASK', item.taskCount, item.date));
                        }
                    });
                    successCallback(calendarEvents);
                })
                .catch(error => {
                    console.error('[index-main] 일정 목록 조회 실패:', error);
                    showToast('달력 일정 조회에 실패했습니다. 새로고침 후 다시 시도해 주세요.', 'error');
                    failureCallback(error);
                });
        },

        eventContent: renderCalendarEvent,

        dateClick: function (info) {
            loadTaskDetails(info.dateStr);
        },

        eventClick: function (info) {
            loadTaskDetails(String(info.event.startStr || '').slice(0, 10));
        }
    });

    calendar.render();
    setButtonActive(getBasis());
    loadWorkWindow(false);

    // =========================================================
    // 상단 컨트롤
    // =========================================================
    function changeBasis(nextBasis) {
        const current = getBasis();
        if (current === nextBasis) return;

        setBasis(nextBasis);
        setButtonActive(nextBasis);
        expandedOverviewKeys.clear();
        expandedWorkWindowKeys.clear();
        if (lastWorkWindowData) renderWorkWindow(lastWorkWindowData);

        setHeroState(`${getBasisText(nextBasis)}으로 전환했습니다. 현재 기간을 다시 정리하고 있습니다.`);
        showToast(`${getBasisText(nextBasis)}으로 전환했습니다.`, 'info');

        calendar.refetchEvents();
        if (currentRange) loadOverview(currentRange, true);
    }

    if (btnReq) {
        btnReq.addEventListener('click', function () {
            changeBasis(BASIS.REQUEST);
        });
    }

    if (btnPro) {
        btnPro.addEventListener('click', function () {
            changeBasis(BASIS.PROCESS);
        });
    }

    const prevBtn = document.getElementById('index-main-calendar-prev');
    const nextBtn = document.getElementById('index-main-calendar-next');
    const todayBtn = document.getElementById('index-main-calendar-today');

    if (prevBtn) prevBtn.addEventListener('click', () => calendar.prev());
    if (nextBtn) nextBtn.addEventListener('click', () => calendar.next());
    if (todayBtn) todayBtn.addEventListener('click', () => calendar.today());

    // =========================================================
    // 모달 닫기
    // =========================================================
    if (modalCloseBtn) {
        modalCloseBtn.addEventListener('click', function (event) {
            event.preventDefault();
            closeModal();
        });
    }

    if (modal) {
        modal.addEventListener('click', function (event) {
            if (event.target === modal) closeModal();
        });
    }

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && modal && modal.style.display !== 'none') {
            closeModal();
        }
    });

    // =========================================================
    // 반응형: PC 월간 / 모바일 하루
    // =========================================================
    window.addEventListener('resize', function () {
        if (resizeTimer) window.clearTimeout(resizeTimer);
        resizeTimer = window.setTimeout(() => {
            const nextMobileMode = window.innerWidth <= MOBILE_BREAKPOINT;
            if (nextMobileMode !== mobileMode) {
                mobileMode = nextMobileMode;
                const targetView = mobileMode ? 'dayGridDay' : 'dayGridMonth';
                calendar.changeView(targetView, calendar.getDate());
                showToast(mobileMode ? '모바일 하루 보기로 전환했습니다.' : '월간 달력 보기로 전환했습니다.', 'info');
            }
            calendar.updateSize();
        }, 180);
    });
});
