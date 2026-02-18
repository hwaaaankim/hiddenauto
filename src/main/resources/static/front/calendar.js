document.addEventListener('DOMContentLoaded', function () {
	const calendarEl = document.getElementById('auto-calendar');

	// =========================
	// ✅ 기준(LocalStorage)
	// =========================
	const LS_KEY = 'calendarDateBasis';
	const BASIS = {
		REQUEST: 'REQUEST', // 신청일 기준
		PROCESS: 'PROCESS'  // 처리일 기준
	};

	function getBasis() {
		const v = localStorage.getItem(LS_KEY);
		if (v === BASIS.PROCESS) return BASIS.PROCESS;
		return BASIS.REQUEST; // 기본
	}

	function setBasis(v) {
		localStorage.setItem(LS_KEY, v);
	}

	function setButtonActive(basis) {
		const btnReq = document.getElementById('index-calendar-basis-request');
		const btnPro = document.getElementById('index-calendar-basis-process');

		if (!btnReq || !btnPro) return;

		// active 스타일: btn-primary vs btn-outline-primary
		if (basis === BASIS.REQUEST) {
			btnReq.classList.remove('btn-outline-primary');
			btnReq.classList.add('btn-primary');

			btnPro.classList.remove('btn-primary');
			btnPro.classList.add('btn-outline-primary');
		} else {
			btnPro.classList.remove('btn-outline-primary');
			btnPro.classList.add('btn-primary');

			btnReq.classList.remove('btn-primary');
			btnReq.classList.add('btn-outline-primary');
		}
	}

	// =========================
	// ✅ 모달 상세조회
	// =========================
	function loadTaskDetails(dateStr) {
		const basis = getBasis();
		fetch(`/api/v1/calendar/tasks?date=${encodeURIComponent(dateStr)}&basis=${encodeURIComponent(basis)}`)
			.then(res => {
				if (!res.ok) throw new Error('HTTP ' + res.status);
				return res.json();
			})
			.then(data => {
				const modal = document.getElementById('auto-modal');
				const modalTitle = document.getElementById('auto-modal-title');
				const modalBody = document.getElementById('auto-modal-body');

				const basisText = (basis === BASIS.REQUEST) ? '신청일 기준' : '처리일 기준';
				modalTitle.innerText = `${dateStr} 일정 (${data.length}건) - ${basisText}`;

				if (!data || data.length === 0) {
					modalBody.innerHTML = '<p class="calendar-task-empty">해당 날짜에 일정이 없습니다.</p>';
				} else {
					modalBody.innerHTML = data.map((task) => {
						if (task.type === 'AS') {
							return `
								<div class="calendar-task calendar-task-as">
									<p><strong>#AS_${task.id}</strong></p>
									<p><span>제목:</span> ${task.title || '-'}</p>
									<p><span>${basis === BASIS.REQUEST ? '신청일' : '처리일'}:</span> ${task.date || '-'}</p>
									<p><span>주소:</span> ${task.address || '-'}</p>
								</div>
							`;
						} else if (task.type === 'TASK') {
							const orders = task.orders || [];
							const orderListHtml = orders.map((order) => `
								<div class="calendar-task-order">
									<p><strong>#TASK_${task.id}</strong></p>
									<p><span>주문일:</span> ${order.createdAt || '-'}</p>
									<p><span>배송희망일:</span> ${order.preferredDeliveryDate || '-'}</p>
									<p><span>배송지:</span> ${order.address || '-'}</p>
									<p><span>수량:</span> ${order.quantity != null ? order.quantity : '-'}</p>
									<p><span>가격:</span> ${order.price != null ? Number(order.price).toLocaleString() + '원' : '-'}</p>
									<p><span>카테고리:</span> ${order.categoryName || '-'}</p>
								</div>
							`).join('');

							return `
								<div class="calendar-task calendar-task-task">
									${orderListHtml}
								</div>
							`;
						}
						return '';
					}).join('');
				}

				modal.style.display = 'block';
			})
			.catch(err => {
				console.error("일정 상세 조회 실패:", err);
				alert("일정 정보를 불러오는 데 실패했습니다.");
			});
	}

	// =========================
	// ✅ FullCalendar 초기화
	// - 이벤트를 함수형 소스로 구성
	// =========================
	const calendar = new FullCalendar.Calendar(calendarEl, {
		initialView: 'dayGridMonth',
		locale: 'ko',
		editable: false,
		height: 'auto',

		events: function (fetchInfo, successCallback, failureCallback) {
			const basis = getBasis();
			fetch(`/api/v1/calendar/events?basis=${encodeURIComponent(basis)}`)
				.then(res => {
					if (!res.ok) throw new Error('HTTP ' + res.status);
					return res.json();
				})
				.then(events => {
					const calendarEvents = [];

					(events || []).forEach(e => {
						if (e.asCount > 0) {
							calendarEvents.push({
								title: `AS신청: ${e.asCount}건`,
								start: e.date,
								backgroundColor: '#007bff',
								borderColor: '#007bff',
								textColor: '#fff',
								extendedProps: { type: 'AS', count: e.asCount }
							});
						}
						if (e.taskCount > 0) {
							calendarEvents.push({
								title: `주문: ${e.taskCount}건`,
								start: e.date,
								backgroundColor: '#dc3545',
								borderColor: '#dc3545',
								textColor: '#fff',
								extendedProps: { type: 'TASK', count: e.taskCount }
							});
						}
					});

					successCallback(calendarEvents);
				})
				.catch(err => {
					console.error("일정 목록 조회 실패:", err);
					failureCallback(err);
				});
		},

		dateClick: function (info) {
			loadTaskDetails(info.dateStr);
		},
		eventClick: function (info) {
			loadTaskDetails(info.event.startStr);
		}
	});

	calendar.render();

	// =========================
	// ✅ 버튼 바인딩 + 초기 상태 적용
	// =========================
	const btnReq = document.getElementById('index-calendar-basis-request');
	const btnPro = document.getElementById('index-calendar-basis-process');

	// 초기 버튼 active
	setButtonActive(getBasis());

	if (btnReq) {
		btnReq.addEventListener('click', function () {
			setBasis(BASIS.REQUEST);
			setButtonActive(BASIS.REQUEST);
			calendar.refetchEvents();
		});
	}

	if (btnPro) {
		btnPro.addEventListener('click', function () {
			setBasis(BASIS.PROCESS);
			setButtonActive(BASIS.PROCESS);
			calendar.refetchEvents();
		});
	}

	// =========================
	// ✅ 모달 닫기 처리
	// =========================
	const modalCloseBtn = document.getElementById('auto-close');
	if (modalCloseBtn) {
		modalCloseBtn.onclick = function () {
			document.getElementById('auto-modal').style.display = 'none';
		};
	}

	window.onclick = function (event) {
		const modal = document.getElementById('auto-modal');
		if (event.target === modal) {
			modal.style.display = 'none';
		}
	};
});
