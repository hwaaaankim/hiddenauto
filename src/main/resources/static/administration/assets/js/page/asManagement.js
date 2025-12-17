/* /administration/team/as/asManagement.js */
/* global FullCalendar, Sortable */

(function() {
	'use strict';

	// ===== DOM =====
	const calendarEl = document.getElementById('as-calendar-calendar');
	const externalListEl = document.getElementById('as-calendar-external-list');

	const modalOverlay = document.getElementById('as-calendar-modal-overlay');
	const modalCloseBtn = document.getElementById('as-calendar-modal-close');
	const modalDateText = document.getElementById('as-calendar-modal-date-text');
	const modalListEl = document.getElementById('as-calendar-modal-list');
	const modalSaveBtn = document.getElementById('as-calendar-modal-save-order');

	// ===== state =====
	let calendar = null;
	let modalDate = null;
	let modalSortable = null;

	// ===== utils =====
	function qs(sel, root) { return (root || document).querySelector(sel); }
	function qsa(sel, root) { return Array.from((root || document).querySelectorAll(sel)); }

	function toYmd(v) {
		if (!v) return '';
		const s = String(v);
		return s.length >= 10 ? s.substring(0, 10) : s; // yyyy-MM-dd
	}

	function isSchedulableStatus(status) {
		return status === 'REQUESTED' || status === 'IN_PROGRESS';
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

	// ===== Right list: enabled 우선 정렬 + draggable class 부여 =====
	function normalizeRightList() {
		const cards = qsa('.as-calendar-task-card', externalListEl);

		// 1) enabled(등록가능) 위로, 그 다음 registered, 그 다음 disabled
		cards.sort((a, b) => {
			const sa = a.getAttribute('data-status');
			const sb = b.getAttribute('data-status');

			const da = a.getAttribute('data-scheduled-date') || '';
			const db = b.getAttribute('data-scheduled-date') || '';

			function rank(status, scheduled) {
				const schedulable = isSchedulableStatus(status);
				const registered = !!scheduled;
				if (schedulable && !registered) return 0;  // 등록 가능
				if (schedulable && registered) return 1;   // 이미 등록됨
				return 2;                                  // 완료/취소(등록불가)
			}
			return rank(sa, da) - rank(sb, db);
		});

		// 2) DOM 재배치
		const frag = document.createDocumentFragment();
		cards.forEach(c => frag.appendChild(c));
		externalListEl.appendChild(frag);

		// 3) 드래그 가능한 카드에만 draggable marker 붙이기
		cards.forEach(card => {
			const status = card.getAttribute('data-status');
			const scheduled = card.getAttribute('data-scheduled-date');
			const inner = qs('.as-calendar-task-card-inner', card);

			// 이미 등록된 항목은 "드래그 불가" 처리(요구사항상 구분만 되면 되지만, 오등록 방지 위해 막음)
			const draggable = isSchedulableStatus(status) && !scheduled;
			if (draggable) {
				inner.classList.add('as-calendar-task-draggable');
			} else {
				inner.classList.remove('as-calendar-task-draggable');
			}
		});
	}

	// ===== FullCalendar init =====
	// ===== FullCalendar init =====
	function initCalendar() {
		if (!calendarEl) return;

		calendar = new FullCalendar.Calendar(calendarEl, {
			initialView: 'dayGridMonth',
			height: 'auto',
			locale: 'ko',
			height: '100%',        // ✅ 핵심
			expandRows: true,      // ✅ 행을 균등 분배해서 아래 공백 제거
			droppable: true,
			editable: false,
			dayMaxEvents: 5,          // ✅ 5개 초과 시 "+n more"
			displayEventTime: false,  // ✅ 시간 숨김(하루종일이라도 더 깔끔)
			eventDisplay: 'block',    // ✅ 혹시 점으로만 보이는 경우 방지

			// ✅ "1일" 같은 접미사 제거: 숫자만 표시
			dayCellContent: function(arg) {
				// arg.date: Date
				const dayNum = arg.date.getDate();
				return { html: String(dayNum) };
			},

			eventSources: [
				{
					events: function(fetchInfo, success, failure) {
						// ✅ start/end가 시간 포함 포맷으로 들어와도 yyyy-MM-dd로 강제 변환
						const start = toYmd(fetchInfo.startStr || fetchInfo.start);
						const end = toYmd(fetchInfo.endStr || fetchInfo.end);

						apiGet(`/team/asSchedule/events?start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`)
							.then(success)
							.catch(failure);
					}
				}
			],

			// 날짜 클릭 → 모달
			dateClick: function(info) {
				modalDate = toYmd(info.dateStr || info.date);
				modalDateText.textContent = modalDate;
				loadModalList(modalDate);
			},

			// 외부에서 드랍됨(아직 서버등록 전)
			eventReceive: function(info) {
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
					calendar.refetchEvents();
					window.location.reload();
				}).catch((e) => {
					info.event.remove();
					window.alert(e.message || '등록 실패');
				});
			}
		});

		calendar.render();
	}

	// ===== External draggable init =====
	function initExternalDraggable() {
		if (!externalListEl) return;

		// FullCalendar 제공 Draggable
		new FullCalendar.Draggable(externalListEl, {
			itemSelector: '.as-calendar-task-draggable',
			eventData: function(el) {
				const card = el.closest('.as-calendar-task-card');
				const taskId = card.getAttribute('data-task-id');
				const company = card.getAttribute('data-company');
				return {
					id: String(taskId),
					title: company,  // ✅ 캘린더에는 업체명만
					allDay: true
				};
			}
		});
	}

	// ===== Modal list =====
	function loadModalList(dateStr) {
		const ymd = toYmd(dateStr);

		apiGet(`/team/asSchedule/date?date=${encodeURIComponent(ymd)}`)
			.then((items) => {
				renderModalList(items);
				openModal();

				// Sortable init
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

	function renderModalList(items) {
		if (!items || items.length === 0) {
			modalListEl.innerHTML = '<div class="text-muted small">배정된 업무가 없습니다.</div>';
			return;
		}

		modalListEl.innerHTML = items.map((it) => {
			const disabledRemove = (it.status === 'COMPLETED' || it.status === 'CANCELED') ? 'disabled' : '';
			const badge =
				(it.status === 'REQUESTED') ? 'badge bg-info' :
					(it.status === 'IN_PROGRESS') ? 'badge bg-primary' :
						(it.status === 'COMPLETED') ? 'badge bg-success' :
							'badge bg-secondary';

			const reqDate = it.requestedAt ? String(it.requestedAt).substring(0, 10) : '-';
			const procDate = it.asProcessDate ? String(it.asProcessDate).substring(0, 10) : '-';

			return `
        <div class="as-calendar-modal-item" data-task-id="${it.taskId}">
          <div class="as-calendar-modal-drag-handle">↕</div>
          <div class="as-calendar-modal-main">
            <div class="as-calendar-modal-row1">
              <div class="as-calendar-modal-company">${escapeHtml(it.companyName)}</div>
              <span class="${badge}">${it.status}</span>
            </div>
            <div class="as-calendar-modal-row2">
              <div><span class="as-calendar-label">신청일</span> ${reqDate}</div>
              <div><span class="as-calendar-label">처리일</span> ${procDate}</div>
            </div>
            <div class="as-calendar-modal-addr">${escapeHtml(it.address || '')}</div>
          </div>
          <button type="button" class="btn btn-sm btn-outline-danger as-calendar-modal-remove" ${disabledRemove} title="해당 날짜에서 제거">x</button>
        </div>
      `;
		}).join('');

		// remove 버튼 바인딩
		qsa('.as-calendar-modal-remove', modalListEl).forEach(btn => {
			btn.addEventListener('click', function() {
				if (btn.disabled) {
					window.alert('완료/취소된 업무는 제거할 수 없습니다.');
					return;
				}
				const itemEl = btn.closest('.as-calendar-modal-item');
				const taskId = Number(itemEl.getAttribute('data-task-id'));
				const ok = window.confirm('해당 날짜에서 업무를 제거하시겠습니까?');
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
						window.location.reload();
					})
					.catch((e) => window.alert(e.message || '제거 실패'));
			});
		});
	}

	// ===== Modal save order =====
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

	// ===== overlay close behavior =====
	function bindModalClose() {
		modalCloseBtn.addEventListener('click', closeModal);
		modalOverlay.addEventListener('click', function(e) {
			if (e.target === modalOverlay) closeModal; // 실수 방지
		});
		modalOverlay.addEventListener('click', function(e) {
			if (e.target === modalOverlay) closeModal();
		});
		modalSaveBtn.addEventListener('click', saveModalOrder);
	}

	function escapeHtml(s) {
		return String(s || '')
			.replaceAll('&', '&amp;')
			.replaceAll('<', '&lt;')
			.replaceAll('>', '&gt;')
			.replaceAll('"', '&quot;')
			.replaceAll("'", "&#039;");
	}

	// ===== boot =====
	document.addEventListener('DOMContentLoaded', function() {
		normalizeRightList();
		initExternalDraggable();
		initCalendar();
		bindModalClose();
	});

})();
