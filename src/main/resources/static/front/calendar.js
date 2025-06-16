document.addEventListener('DOMContentLoaded', function() {
	const calendarEl = document.getElementById('auto-calendar');

	// 📌 서버에서 일정 데이터 가져오기
	fetch('/api/v1/calendar/events')
		.then(res => res.json())
		.then(events => {
			const calendarEvents = [];

			// 👉 각각 조건에 맞게 분리해서 push
			events.forEach(e => {
				if (e.asCount > 0) {
					calendarEvents.push({
						title: `AS신청: ${e.asCount}건`,
						start: e.date,
						backgroundColor: '#007bff', // 파랑
						borderColor: '#007bff',
						textColor: '#fff',
						extendedProps: {
							type: 'AS',
							count: e.asCount
						}
					});
				}

				if (e.taskCount > 0) {
					calendarEvents.push({
						title: `주문: ${e.taskCount}건`,
						start: e.date,
						backgroundColor: '#dc3545', // 빨강
						borderColor: '#dc3545',
						textColor: '#fff',
						extendedProps: {
							type: 'TASK',
							count: e.taskCount
						}
					});
				}
			});

			// 📅 FullCalendar 초기화
			const calendar = new FullCalendar.Calendar(calendarEl, {
				initialView: 'dayGridMonth',
				locale: 'ko',
				events: calendarEvents,
				editable: false,
				height: 'auto',

				dateClick: function(info) {
					loadTaskDetails(info.dateStr);
				},
				eventClick: function(info) {
					loadTaskDetails(info.event.startStr);
				}
			});

			calendar.render();
		})
		.catch(err => {
			console.error("일정 목록 조회 실패:", err);
		});

	// 📌 상세 태스크 정보 모달로 표시
	function loadTaskDetails(dateStr) {
		fetch(`/api/v1/calendar/tasks?date=${dateStr}`)
			.then(res => res.json())
			.then(data => {
				const modal = document.getElementById('auto-modal');
				const modalTitle = document.getElementById('auto-modal-title');
				const modalBody = document.getElementById('auto-modal-body');

				modalTitle.innerText = `${dateStr} 일정 (${data.length}건)`;

				if (data.length === 0) {
					modalBody.innerHTML = '<p class="calendar-task-empty">해당 날짜에 일정이 없습니다.</p>';
				} else {
					modalBody.innerHTML = data.map((task) => {
						if (task.type === 'AS') {
							return `
                            <div class="calendar-task calendar-task-as">
                                <p><strong>#AS_${task.id}</strong></p>
                                <p><span>제품명:</span> ${task.title}</p>
                                <p><span>신청일:</span> ${task.date}</p>
                                <p><span>주소:</span> ${task.address}</p>
                            </div>
                        `;
						} else if (task.type === 'TASK') {
							const orders = task.orders || [];
							const orderListHtml = orders.map((order, idx) => `
                            <div class="calendar-task-order">
                                <p><strong>#TASK_${task.id}</strong></p>
                                <p><span>주문일:</span> ${order.createdAt}</p>
                                <p><span>배송희망일:</span> ${order.preferredDeliveryDate || '-'}</p>
                                <p><span>배송지:</span> ${order.address}</p>
                                <p><span>수량:</span> ${order.quantity}</p>
                                <p><span>가격:</span> ${order.price.toLocaleString()}원</p>
                                <p><span>카테고리:</span> ${order.categoryName}</p>
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


	// 📌 모달 닫기 처리
	const modalCloseBtn = document.getElementById('auto-close');
	modalCloseBtn.onclick = function() {
		document.getElementById('auto-modal').style.display = 'none';
	};

	window.onclick = function(event) {
		const modal = document.getElementById('auto-modal');
		if (event.target === modal) {
			modal.style.display = 'none';
		}
	};
});
