/* /administration/assets/js/page/asManagement.js */
/* global FullCalendar, Sortable */

(function () {
	'use strict';

	// ===== DOM =====
	const calendarEl = document.getElementById('as-calendar-calendar');

	const drawerOpenBtn = document.getElementById('as-management-added-open-drawer'); // '업무' 버튼(FAB)
	const drawerOverlay = document.getElementById('as-management-added-drawer-overlay');
	const drawer = drawerOverlay ? drawerOverlay.querySelector('.as-management-added-drawer') : null;
	const drawerCloseBtn = document.getElementById('as-management-added-close-drawer');

	const externalListEl = document.getElementById('as-calendar-external-list'); // drawer 안 리스트

	const modalOverlay = document.getElementById('as-calendar-modal-overlay');
	const modalCloseBtn = document.getElementById('as-calendar-modal-close');
	const modalDateText = document.getElementById('as-calendar-modal-date-text');
	const modalListEl = document.getElementById('as-calendar-modal-list');
	const modalSaveBtn = document.getElementById('as-calendar-modal-save-order');

	// ===== state =====
	let calendar = null;
	let modalDate = null;
	let modalSortable = null;
	let isMobile = false;

	// ✅ 외부 드래그 인스턴스(PC에서만 생성/유지)
	let externalDraggable = null;

	// 날짜별 이벤트 수(“N건” 배지용)
	let eventCountByDate = {};

	// ✅ Drawer 자동닫기/재오픈을 위한 드래그 감시 상태
	let drawerDragWatch = {
		active: false,
		pointerId: null,
		startX: 0,
		startY: 0,
		moved: false,
		closedByDrag: false,
		openedAgain: false
	};

	// ✅ 재오픈 트리거(‘업무’ 버튼) 인식 여유 영역
	const DRAWER_REOPEN_BTN_PADDING = 18; // 버튼 주변 여유
	const DRAWER_REOPEN_BTN_TOP_EXTRA = 36; // 버튼 "위" 쪽 추가 여유(요청 포인트)

	// ✅ 닫힘 히스테리시스
	const DRAWER_CLOSE_OUT_MARGIN = 6;

	// ===== utils =====
	function qs(sel, root) { return (root || document).querySelector(sel); }
	function qsa(sel, root) { return Array.from((root || document).querySelectorAll(sel)); }

	function detectMobile() {
		const byWidth = window.matchMedia('(max-width: 991px)').matches;
		const byTouch = (navigator.maxTouchPoints && navigator.maxTouchPoints > 0);
		return byWidth || byTouch;
	}

	function toYmd(v) {
		if (!v) return '';
		const s = String(v);
		return s.length >= 10 ? s.substring(0, 10) : s;
	}

	function isSchedulableStatus(status) {
		return status === 'IN_PROGRESS';
	}

	function isBlockedStatus(status) {
		return status === 'COMPLETED' || status === 'CANCELED';
	}

	function apiJson(url, method, body) {
		return fetch(url, {
			method: method,
			headers: { 'Content-Type': 'application/json' },
			body: body ? JSON.stringify(body) : null
		}).then(async (res) => {
			if (!res.ok) {
				let msg = '요청 실패';
				try {
					const t = await res.text();
					msg = t || msg;
				} catch (e) { }
				throw new Error(msg);
			}
			return res.json().catch(() => ({}));
		});
	}

	function apiGet(url) {
		return fetch(url).then(async (res) => {
			if (!res.ok) throw new Error('요청 실패');
			return res.json();
		});
	}

	function escapeHtml(s) {
		return String(s || '')
			.replaceAll('&', '&amp;')
			.replaceAll('<', '&lt;')
			.replaceAll('>', '&gt;')
			.replaceAll('"', '&quot;')
			.replaceAll("'", "&#039;");
	}

	function getPointerXY(e) {
		if (e && typeof e.clientX === 'number' && typeof e.clientY === 'number') {
			return { x: e.clientX, y: e.clientY };
		}
		if (e && e.touches && e.touches[0]) {
			return { x: e.touches[0].clientX, y: e.touches[0].clientY };
		}
		return { x: 0, y: 0 };
	}

	function isPointInsideRect(x, y, rect) {
		return x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
	}

	function isDrawerOpen() {
		return document.body.classList.contains('as-management-added-drawer-open');
	}

	function expandRect(rect, padLeft, padTop, padRight, padBottom) {
		return {
			left: rect.left - (padLeft || 0),
			top: rect.top - (padTop || 0),
			right: rect.right + (padRight || 0),
			bottom: rect.bottom + (padBottom || 0)
		};
	}

	// ===== Drawer =====
	function openDrawer() {
		if (!drawerOverlay || !drawer) return;
		drawerOverlay.style.display = 'flex';
		window.setTimeout(() => drawer.classList.add('as-management-added-open'), 10);
		document.body.classList.add('as-management-added-drawer-open');
	}

	function closeDrawer() {
		if (!drawerOverlay || !drawer) return;
		drawer.classList.remove('as-management-added-open');
		document.body.classList.remove('as-management-added-drawer-open');
		window.setTimeout(() => {
			drawerOverlay.style.display = 'none';
		}, 180);
	}

	function bindDrawer() {
		if (drawerOpenBtn) drawerOpenBtn.addEventListener('click', openDrawer);
		if (drawerCloseBtn) drawerCloseBtn.addEventListener('click', closeDrawer);

		if (drawerOverlay) {
			drawerOverlay.addEventListener('click', function (e) {
				if (e.target === drawerOverlay) closeDrawer();
			});
		}
	}

	// ===== Modal =====
	function openModal() {
		modalOverlay.style.display = 'flex';
		document.body.classList.add('as-calendar-modal-open');
	}

	function closeModal() {
		modalOverlay.style.display = 'none';
		document.body.classList.remove('as-calendar-modal-open');
		modalDate = null;
		modalListEl.innerHTML = '';
		if (modalSortable) {
			modalSortable.destroy();
			modalSortable = null;
		}
	}

	function bindModalClose() {
		if (modalCloseBtn) modalCloseBtn.addEventListener('click', closeModal);
		if (modalOverlay) {
			modalOverlay.addEventListener('click', function (e) {
				if (e.target === modalOverlay) closeModal();
			});
		}
		if (modalSaveBtn) modalSaveBtn.addEventListener('click', saveModalOrder);
	}

	// ===== slide toggle (drawer + modal 공용) =====
	function slideToggle(el, open) {
		if (!el) return;

		el.style.overflow = 'hidden';

		if (open) {
			el.style.display = 'block';
			const h = el.scrollHeight;
			el.style.height = '0px';
			el.style.transition = 'height .18s ease';
			requestAnimationFrame(() => {
				el.style.height = h + 'px';
			});
			window.setTimeout(() => {
				el.style.height = '';
				el.style.transition = '';
				el.style.overflow = '';
			}, 190);
		} else {
			const h = el.scrollHeight;
			el.style.height = h + 'px';
			el.style.transition = 'height .18s ease';
			requestAnimationFrame(() => {
				el.style.height = '0px';
			});
			window.setTimeout(() => {
				el.style.display = 'none';
				el.style.height = '';
				el.style.transition = '';
				el.style.overflow = '';
			}, 190);
		}
	}

	// ===== Right list normalize + draggable marker =====
	function normalizeTaskList() {
		if (!externalListEl) return;

		const items = qsa('.as-management-added-task', externalListEl);

		// 정렬: 1) 등록가능(IN_PROGRESS + 미등록) 2) 이미등록(IN_PROGRESS + 등록) 3) 나머지
		items.sort((a, b) => {
			const sa = a.getAttribute('data-status');
			const sb = b.getAttribute('data-status');
			const da = a.getAttribute('data-scheduled-date') || '';
			const db = b.getAttribute('data-scheduled-date') || '';

			function rank(status, scheduled) {
				const schedulable = isSchedulableStatus(status);
				const registered = !!scheduled;
				if (schedulable && !registered) return 0;
				if (schedulable && registered) return 1;
				return 2;
			}
			return rank(sa, da) - rank(sb, db);
		});

		const frag = document.createDocumentFragment();
		items.forEach(el => frag.appendChild(el));
		externalListEl.appendChild(frag);

		// draggable marker: 모바일이면 불가, PC라도 IN_PROGRESS + 미등록만 가능
		items.forEach(el => {
			const status = el.getAttribute('data-status');
			const scheduled = el.getAttribute('data-scheduled-date') || '';
			const dragArea = qs('.as-management-added-drag-area', el);

			const draggable = (!isMobile) && isSchedulableStatus(status) && !scheduled;
			if (draggable) {
				dragArea.classList.add('as-management-added-draggable');
			} else {
				dragArea.classList.remove('as-management-added-draggable');
			}
		});

		bindListButtons();

		// ✅ 드래그 중: Drawer 밖으로 나가면 닫고, '업무' 버튼 위로 가면 다시 열기
		bindExternalDragAutoCloseAndReopen();
	}

	function bindListButtons() {
		// 상세 토글
		qsa('.as-management-added-toggle-btn', externalListEl).forEach(btn => {
			btn.onclick = function (e) {
				e.preventDefault();
				e.stopPropagation();
				const taskEl = btn.closest('.as-management-added-task');
				const detail = qs('.as-management-added-task-detail', taskEl);
				if (!detail) return;

				const isOpen = detail.style.display !== 'none';
				slideToggle(detail, !isOpen);
			};
		});

		// 달력 이동
		qsa('.as-management-added-jump-btn', externalListEl).forEach(btn => {
			btn.onclick = function (e) {
				e.preventDefault();
				e.stopPropagation();
				const taskEl = btn.closest('.as-management-added-task');
				const dateStr = taskEl.getAttribute('data-scheduled-date') || '';

				if (!dateStr) {
					window.alert('아직 달력에 등록된 일정이 없습니다.');
					return;
				}
				if (!calendar) return;

				calendar.gotoDate(dateStr);
				closeDrawer();
				highlightDateCell(dateStr);
			};
		});
	}

	function highlightDateCell(dateStr) {
		const cell = calendarEl ? calendarEl.querySelector(`[data-date="${dateStr}"]`) : null;
		if (!cell) return;

		const prev = cell.style.boxShadow;
		cell.style.boxShadow = '0 0 0 3px rgba(59,130,246,0.35) inset';
		window.setTimeout(() => { cell.style.boxShadow = prev; }, 700);
	}

	// ============================================================
	// ✅ 외부 드래그 시 Drawer 자동 닫기/재오픈
	// ============================================================
	function bindExternalDragAutoCloseAndReopen() {
		if (!externalListEl) return;
		if (!drawer || !drawerOverlay) return;

		qsa('.as-management-added-drag-area.as-management-added-draggable', externalListEl).forEach(area => {
			if (area.dataset.asManagementAddedBoundLeaveReopen === '1') return;
			area.dataset.asManagementAddedBoundLeaveReopen = '1';

			if (window.PointerEvent) {
				area.addEventListener('pointerdown', onDragWatchStartPointer, { passive: true });
			} else {
				area.addEventListener('mousedown', onDragWatchStartMouse, { passive: true });
				area.addEventListener('touchstart', onDragWatchStartTouch, { passive: true });
			}
		});
	}

	function onDragWatchStartPointer(e) {
		if (!isDrawerOpen()) return;

		const pos = getPointerXY(e);
		drawerDragWatch.active = true;
		drawerDragWatch.pointerId = (e && typeof e.pointerId === 'number') ? e.pointerId : null;
		drawerDragWatch.startX = pos.x;
		drawerDragWatch.startY = pos.y;
		drawerDragWatch.moved = false;
		drawerDragWatch.closedByDrag = false;
		drawerDragWatch.openedAgain = false;

		window.addEventListener('pointermove', onDragWatchMovePointer, { passive: true });
		window.addEventListener('pointerup', onDragWatchEndPointer, { passive: true });
		window.addEventListener('pointercancel', onDragWatchEndPointer, { passive: true });
	}

	function onDragWatchMovePointer(e) {
		if (!drawerDragWatch.active) return;

		if (drawerDragWatch.pointerId != null && e && typeof e.pointerId === 'number') {
			if (e.pointerId !== drawerDragWatch.pointerId) return;
		}

		onDragWatchMoveCore(e);
	}

	function onDragWatchEndPointer() {
		if (!drawerDragWatch.active) return;

		drawerDragWatch.active = false;
		drawerDragWatch.pointerId = null;
		drawerDragWatch.moved = false;
		drawerDragWatch.closedByDrag = false;
		drawerDragWatch.openedAgain = false;

		window.removeEventListener('pointermove', onDragWatchMovePointer);
		window.removeEventListener('pointerup', onDragWatchEndPointer);
		window.removeEventListener('pointercancel', onDragWatchEndPointer);
	}

	// ----- fallback: Mouse -----
	function onDragWatchStartMouse(e) {
		if (!isDrawerOpen()) return;

		const pos = getPointerXY(e);
		drawerDragWatch.active = true;
		drawerDragWatch.pointerId = null;
		drawerDragWatch.startX = pos.x;
		drawerDragWatch.startY = pos.y;
		drawerDragWatch.moved = false;
		drawerDragWatch.closedByDrag = false;
		drawerDragWatch.openedAgain = false;

		window.addEventListener('mousemove', onDragWatchMoveMouse, { passive: true });
		window.addEventListener('mouseup', onDragWatchEndMouse, { passive: true });
	}

	function onDragWatchMoveMouse(e) {
		if (!drawerDragWatch.active) return;
		onDragWatchMoveCore(e);
	}

	function onDragWatchEndMouse() {
		if (!drawerDragWatch.active) return;

		drawerDragWatch.active = false;
		drawerDragWatch.pointerId = null;
		drawerDragWatch.moved = false;
		drawerDragWatch.closedByDrag = false;
		drawerDragWatch.openedAgain = false;

		window.removeEventListener('mousemove', onDragWatchMoveMouse);
		window.removeEventListener('mouseup', onDragWatchEndMouse);
	}

	// ----- fallback: Touch -----
	function onDragWatchStartTouch(e) {
		if (!isDrawerOpen()) return;

		const pos = getPointerXY(e);
		drawerDragWatch.active = true;
		drawerDragWatch.pointerId = null;
		drawerDragWatch.startX = pos.x;
		drawerDragWatch.startY = pos.y;
		drawerDragWatch.moved = false;
		drawerDragWatch.closedByDrag = false;
		drawerDragWatch.openedAgain = false;

		window.addEventListener('touchmove', onDragWatchMoveTouch, { passive: true });
		window.addEventListener('touchend', onDragWatchEndTouch, { passive: true });
		window.addEventListener('touchcancel', onDragWatchEndTouch, { passive: true });
	}

	function onDragWatchMoveTouch(e) {
		if (!drawerDragWatch.active) return;
		onDragWatchMoveCore(e);
	}

	function onDragWatchEndTouch() {
		if (!drawerDragWatch.active) return;

		drawerDragWatch.active = false;
		drawerDragWatch.pointerId = null;
		drawerDragWatch.moved = false;
		drawerDragWatch.closedByDrag = false;
		drawerDragWatch.openedAgain = false;

		window.removeEventListener('touchmove', onDragWatchMoveTouch);
		window.removeEventListener('touchend', onDragWatchEndTouch);
		window.removeEventListener('touchcancel', onDragWatchEndTouch);
	}

	// ----- shared core -----
	function onDragWatchMoveCore(e) {
		const pos = getPointerXY(e);

		const dx = Math.abs(pos.x - drawerDragWatch.startX);
		const dy = Math.abs(pos.y - drawerDragWatch.startY);
		if (!drawerDragWatch.moved) {
			if (dx < 6 && dy < 6) return;
			drawerDragWatch.moved = true;
		}

		if (isDrawerOpen()) {
			const rect0 = drawer.getBoundingClientRect();
			const rect = expandRect(rect0, -DRAWER_CLOSE_OUT_MARGIN, -DRAWER_CLOSE_OUT_MARGIN, -DRAWER_CLOSE_OUT_MARGIN, -DRAWER_CLOSE_OUT_MARGIN);

			const inside = isPointInsideRect(pos.x, pos.y, rect);
			if (!inside) {
				closeDrawer();
				drawerDragWatch.closedByDrag = true;
				drawerDragWatch.openedAgain = false;
			}
			return;
		}

		if (drawerDragWatch.closedByDrag) {
			if (!drawerOpenBtn) return;

			const btnRect0 = drawerOpenBtn.getBoundingClientRect();
			const btnRect = expandRect(
				btnRect0,
				DRAWER_REOPEN_BTN_PADDING,
				DRAWER_REOPEN_BTN_PADDING + DRAWER_REOPEN_BTN_TOP_EXTRA,
				DRAWER_REOPEN_BTN_PADDING,
				DRAWER_REOPEN_BTN_PADDING
			);

			const onBtnZone = isPointInsideRect(pos.x, pos.y, btnRect);
			if (onBtnZone && !drawerDragWatch.openedAgain) {
				openDrawer();
				drawerDragWatch.openedAgain = true;
			}
		}
	}

	// ===== FullCalendar init =====
	function getHeaderToolbarForCurrentMode() {
		if (isMobile) {
			return { left: 'prev,next,today', center: 'title', right: '' };
		}
		return { left: 'prev,next', center: 'title', right: 'today' };
	}

	function initCalendar() {
		if (!calendarEl) return;

		isMobile = detectMobile();
		const initialView = isMobile ? 'dayGridDay' : 'dayGridMonth';

		calendar = new FullCalendar.Calendar(calendarEl, {
			initialView: initialView,
			locale: 'ko',
			height: '100%',
			expandRows: true,

			droppable: !isMobile,

			editable: !isMobile,
			eventStartEditable: !isMobile,
			eventDurationEditable: false,

			dayMaxEvents: isMobile ? 10 : 3,
			displayEventTime: false,
			eventDisplay: 'block',

			headerToolbar: getHeaderToolbarForCurrentMode(),

			dayCellContent: function (arg) {
				const dayNum = arg.date.getDate();
				return { html: String(dayNum) };
			},

			eventContent: function (arg) {
				const status = (arg.event.extendedProps && arg.event.extendedProps.status) ? String(arg.event.extendedProps.status) : '';
				const title = escapeHtml(arg.event.title || '');

				const badgeHtml = status
					? `<span class="as-management-added-evt-badge as-management-added-evt-badge-${escapeHtml(status)}">${escapeHtml(status)}</span>`
					: '';

				return {
					html: `
						<div class="as-management-added-evt-row">
							<span class="as-management-added-evt-title">${title}</span>
							${badgeHtml}
						</div>
					`
				};
			},

			eventAllow: function (dropInfo, draggedEvent) {
				const status = (draggedEvent.extendedProps && draggedEvent.extendedProps.status) ? String(draggedEvent.extendedProps.status) : '';
				return isSchedulableStatus(status);
			},

			eventSources: [
				{
					events: function (fetchInfo, success, failure) {
						const start = toYmd(fetchInfo.startStr || fetchInfo.start);
						const end = toYmd(fetchInfo.endStr || fetchInfo.end);

						apiGet(`/team/asSchedule/events?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`)
							.then((events) => {
								eventCountByDate = {};
								(events || []).forEach(ev => {
									const d = toYmd(ev.start);
									if (!d) return;
									eventCountByDate[d] = (eventCountByDate[d] || 0) + 1;
								});

								success(events || []);
								if (!isMobile) updateDayCountBadges();
							})
							.catch(failure);
					}
				}
			],

			dateClick: function (info) {
				const dateStr = toYmd(info.dateStr || info.date);
				openDateModal(dateStr);
			},

			eventReceive: function (info) {
				if (isMobile) {
					info.event.remove();
					return;
				}

				const taskId = Number(info.event.id);
				const dateStr = toYmd(info.event.startStr || info.event.start);

				const ok = window.confirm('해당 날짜에 업무를 등록하시겠습니까?');
				if (!ok) {
					info.event.remove();
					return;
				}

				apiJson('/team/asSchedule/register', 'POST', {
					taskId: taskId,
					scheduledDate: dateStr
				}).then(() => {
					window.alert('등록되었습니다.');
					info.event.remove();
					calendar.refetchEvents();
					markTaskRegistered(taskId, dateStr);
					normalizeTaskList();
				}).catch((e) => {
					info.event.remove();
					window.alert(e.message || '등록 실패');
				});
			},

			eventDrop: function (info) {
				if (isMobile) {
					info.revert();
					return;
				}

				const status = (info.event.extendedProps && info.event.extendedProps.status) ? String(info.event.extendedProps.status) : '';
				if (!isSchedulableStatus(status)) {
					window.alert('진행중(IN_PROGRESS) 상태의 업무만 날짜 이동이 가능합니다.');
					info.revert();
					return;
				}

				const taskId = Number(info.event.id);
				const newDateStr = toYmd(info.event.startStr || info.event.start);

				const ok = window.confirm(`업무 날짜를 ${newDateStr}(으)로 변경하시겠습니까?`);
				if (!ok) {
					info.revert();
					return;
				}

				apiJson('/team/asSchedule/move', 'POST', {
					taskId: taskId,
					scheduledDate: newDateStr
				}).then(() => {
					window.alert('날짜가 변경되었습니다.');
					calendar.refetchEvents();
					markTaskRegistered(taskId, newDateStr);
					normalizeTaskList();
				}).catch((e) => {
					info.revert();
					window.alert(e.message || '날짜 변경 실패');
				});
			}
		});

		calendar.render();
		applyResponsiveCalendarView();
	}

	function updateDayCountBadges() {
		const threshold = calendar.getOption('dayMaxEvents');

		qsa('.fc-daygrid-day', calendarEl).forEach(dayEl => {
			const dateStr = dayEl.getAttribute('data-date');
			if (!dateStr) return;

			const count = eventCountByDate[dateStr] || 0;

			const old = dayEl.querySelector('.as-management-added-daycount');
			if (old) old.remove();

			if (count > threshold) {
				const top = dayEl.querySelector('.fc-daygrid-day-top');
				if (!top) return;

				const badge = document.createElement('span');
				badge.className = 'as-management-added-daycount';
				badge.textContent = count + '건';
				top.appendChild(badge);
			}
		});
	}

	function markTaskRegistered(taskId, dateStr) {
		if (!externalListEl) return;
		const taskEl = externalListEl.querySelector(`.as-management-added-task[data-task-id="${taskId}"]`);
		if (!taskEl) return;

		taskEl.setAttribute('data-scheduled-date', dateStr || '');

		const regBadge = qs('.as-management-added-badge-registered', taskEl);
		if (regBadge) regBadge.style.display = '';

		const detail = qs('.as-management-added-task-detail', taskEl);
		if (detail) {
			const grid = qs('.as-management-added-detail-grid', detail);
			if (grid) {
				const items = grid.querySelectorAll('div');
				if (items && items.length >= 3) {
					const third = items[2];
					const spans = third.querySelectorAll('span');
					if (spans && spans.length >= 2) {
						spans[1].textContent = dateStr || '-';
					}
				}
			}
		}
	}

	function ensureExternalDraggable() {
		if (isMobile) {
			if (externalDraggable && typeof externalDraggable.destroy === 'function') {
				try { externalDraggable.destroy(); } catch (e) { }
			}
			externalDraggable = null;
			return;
		}

		if (!externalListEl) return;
		if (externalDraggable) return;

		try {
			externalDraggable = new FullCalendar.Draggable(externalListEl, {
				itemSelector: '.as-management-added-drag-area.as-management-added-draggable',
				eventData: function (el) {
					const taskEl = el.closest('.as-management-added-task');
					const taskId = taskEl.getAttribute('data-task-id');
					const company = taskEl.getAttribute('data-company');
					const status = taskEl.getAttribute('data-status');
					return {
						id: String(taskId),
						title: company,
						allDay: true,
						classNames: ['as-management-added-evt', 'as-management-added-evt-' + status],
						extendedProps: { status: status }
					};
				}
			});
		} catch (e) {
			externalDraggable = null;
		}
	}

	function initExternalDraggable() {
		ensureExternalDraggable();
	}

	function applyResponsiveCalendarView() {
		if (!calendar) return;

		const nextIsMobile = detectMobile();
		const nextView = nextIsMobile ? 'dayGridDay' : 'dayGridMonth';

		isMobile = nextIsMobile;

		calendar.setOption('droppable', !isMobile);
		calendar.setOption('editable', !isMobile);
		calendar.setOption('eventStartEditable', !isMobile);
		calendar.setOption('dayMaxEvents', isMobile ? 10 : 5);

		calendar.setOption('headerToolbar', getHeaderToolbarForCurrentMode());

		if (calendar.view && calendar.view.type !== nextView) {
			calendar.changeView(nextView);
		}

		if (!isMobile) {
			updateDayCountBadges();
		}

		ensureExternalDraggable();
	}

	// ===== Modal list (등록된 업무) =====
	function openDateModal(dateStr) {
		modalDate = toYmd(dateStr);
		modalDateText.textContent = modalDate;
		loadModalList(modalDate);
	}

	function loadModalList(dateStr) {
		const ymd = toYmd(dateStr);

		apiGet(`/team/asSchedule/date?date=${encodeURIComponent(ymd)}`)
			.then((items) => {
				renderModalList(items);
				openModal();

				modalSortable = new Sortable(modalListEl, {
					animation: 150,
					handle: '.as-calendar-modal-drag-handle',
					ghostClass: 'as-calendar-sort-ghost'
				});
			})
			.catch(() => {
				modalListEl.innerHTML = '<div class="text-muted small">불러오기 실패</div>';
				openModal();
			});
	}

	// ✅ 모달 리스트: "상세 ▼" 슬라이드 토글 추가
	function renderModalList(items) {
		if (!items || items.length === 0) {
			modalListEl.innerHTML = '<div class="text-muted small">배정된 업무가 없습니다.</div>';
			return;
		}

		modalListEl.innerHTML = items.map((it) => {
			const status = it.status;
			const disabledRemove = isBlockedStatus(status) ? 'disabled' : '';

			const badge =
				(status === 'REQUESTED') ? 'badge bg-info' :
					(status === 'IN_PROGRESS') ? 'badge bg-warning' :
						(status === 'COMPLETED') ? 'badge bg-success' :
							'badge bg-danger';

			const reqDate = it.requestedAt ? String(it.requestedAt).substring(0, 10) : '-';
			const procDate = it.asProcessDate ? String(it.asProcessDate).substring(0, 10) : '-';

			// 모달은 "해당 날짜"가 곧 등록일(스케줄 날짜)
			const schedDate = modalDate ? modalDate : '-';

			return `
        <div class="as-calendar-modal-item" data-task-id="${it.taskId}">
          <div class="as-calendar-modal-drag-handle" title="드래그로 순서 변경">↕</div>

          <div class="as-calendar-modal-main">
            <div class="as-calendar-modal-row1">
              <div class="as-calendar-modal-company">${escapeHtml(it.companyName)}</div>

              <div class="as-calendar-modal-actions">
                <span class="${badge}">${escapeHtml(status)}</span>

                <button type="button"
                        class="btn btn-sm btn-light as-calendar-modal-toggle"
                        aria-label="상세 보기"
                        title="상세">
                  ▼
                </button>

                <button type="button"
                        class="btn btn-sm btn-outline-danger as-calendar-modal-remove"
                        ${disabledRemove}
                        title="해당 날짜에서 제거">
                  ×
                </button>
              </div>
            </div>

            <div class="as-calendar-modal-row2">
              <div><span class="as-calendar-label">신청일</span> ${reqDate}</div>
              <div><span class="as-calendar-label">처리일</span> ${procDate}</div>
            </div>

            <!-- ✅ 상세 영역(슬라이드 토글) -->
            <div class="as-calendar-modal-detail" style="display:none;">
              <div class="as-calendar-modal-detail-grid">
                <div>
                  <span class="as-calendar-modal-detail-label">업무ID</span>
                  <span>${escapeHtml(it.taskId)}</span>
                </div>
                <div>
                  <span class="as-calendar-modal-detail-label">등록일</span>
                  <span>${escapeHtml(schedDate)}</span>
                </div>
                <div>
                  <span class="as-calendar-modal-detail-label">상태</span>
                  <span>${escapeHtml(status)}</span>
                </div>
              </div>

              <div class="as-calendar-modal-detail-hint text-muted small mt-2">
                - 완료/취소는 제거 불가, 진행중은 제거 후 다른 날짜로 재등록 가능합니다.
              </div>
            </div>
          </div>
        </div>
      `;
		}).join('');

		bindModalItemButtons();
	}

	function bindModalItemButtons() {
		// 상세 토글 버튼
		qsa('.as-calendar-modal-toggle', modalListEl).forEach(btn => {
			btn.addEventListener('click', function (e) {
				e.preventDefault();
				e.stopPropagation();

				const itemEl = btn.closest('.as-calendar-modal-item');
				const detailEl = qs('.as-calendar-modal-detail', itemEl);
				if (!detailEl) return;

				const isOpen = detailEl.style.display !== 'none';
				slideToggle(detailEl, !isOpen);

				// 화살표 회전/상태표시용 클래스
				btn.classList.toggle('is-open', !isOpen);
			});
		});

		// 제거 버튼
		qsa('.as-calendar-modal-remove', modalListEl).forEach(btn => {
			btn.addEventListener('click', function (e) {
				e.preventDefault();
				e.stopPropagation();

				if (btn.disabled) {
					window.alert('완료/취소된 업무는 제거할 수 없습니다.');
					return;
				}
				const itemEl = btn.closest('.as-calendar-modal-item');
				const taskId = Number(itemEl.getAttribute('data-task-id'));
				const ok = window.confirm('해당 날짜에서 업무를 제거하시겠습니까?\n(미완료 상태라면 제거 후 다른 날짜로 재등록 가능합니다.)');
				if (!ok) return;

				fetch(`/team/asSchedule/remove/${taskId}`, { method: 'DELETE' })
					.then(async (res) => {
						if (!res.ok) {
							const t = await res.text().catch(() => '');
							throw new Error(t || '제거 실패');
						}
						return res.json().catch(() => ({}));
					})
					.then(() => {
						window.alert('제거되었습니다.');
						closeModal();
						calendar.refetchEvents();
						unmarkTaskRegistered(taskId);
						normalizeTaskList();
					})
					.catch((err) => window.alert(err.message || '제거 실패'));
			});
		});
	}

	function unmarkTaskRegistered(taskId) {
		if (!externalListEl) return;
		const taskEl = externalListEl.querySelector(`.as-management-added-task[data-task-id="${taskId}"]`);
		if (!taskEl) return;

		taskEl.setAttribute('data-scheduled-date', '');

		const regBadge = qs('.as-management-added-badge-registered', taskEl);
		if (regBadge) regBadge.style.display = 'none';

		const detail = qs('.as-management-added-task-detail', taskEl);
		if (detail) {
			const grid = qs('.as-management-added-detail-grid', detail);
			if (grid) {
				const items = grid.querySelectorAll('div');
				if (items && items.length >= 3) {
					const third = items[2];
					const spans = third.querySelectorAll('span');
					if (spans && spans.length >= 2) {
						spans[1].textContent = '-';
					}
				}
			}
		}
	}

	function saveModalOrder() {
		if (!modalDate) return;

		const ids = qsa('.as-calendar-modal-item', modalListEl)
			.map(el => Number(el.getAttribute('data-task-id')))
			.filter(Boolean);

		apiJson('/team/asSchedule/reorder', 'POST', {
			scheduledDate: modalDate,
			taskIdsInOrder: ids
		}).then(() => {
			window.alert('순서가 변경되었습니다.');
			closeModal();
			calendar.refetchEvents();
		}).catch((e) => {
			window.alert(e.message || '순서 변경 실패');
		});
	}

	// ===== boot =====
	document.addEventListener('DOMContentLoaded', function () {
		isMobile = detectMobile();

		bindDrawer();
		bindModalClose();

		normalizeTaskList();
		initExternalDraggable();
		initCalendar();

		let resizeTimer = null;
		window.addEventListener('resize', function () {
			window.clearTimeout(resizeTimer);
			resizeTimer = window.setTimeout(function () {
				if (calendar) applyResponsiveCalendarView();
				normalizeTaskList();
			}, 120);
		});
	});
})();
