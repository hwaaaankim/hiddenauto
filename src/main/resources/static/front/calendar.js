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

	// ✅ XSS 방지용 최소 escape
	function escapeHtml(v) {
		if (v == null) return '';
		return String(v)
			.replaceAll('&', '&amp;')
			.replaceAll('<', '&lt;')
			.replaceAll('>', '&gt;')
			.replaceAll('"', '&quot;')
			.replaceAll("'", '&#039;');
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
				modalTitle.innerText = `${dateStr} 일정 (${(data && data.length) ? data.length : 0}건) - ${basisText}`;

				if (!data || data.length === 0) {
					modalBody.innerHTML = '<p class="calendar-task-empty">해당 날짜에 일정이 없습니다.</p>';
				} else {
					modalBody.innerHTML = data.map((task) => {
						// -------------------------
						// ✅ AS
						// -------------------------
						if (task.type === 'AS') {
							// 제품정보
							const productName = task.productName || '-';
							const productSize = task.productSize || '-';
							const productColor = task.productColor || '-';
							const productOptions = task.productOptions || '-';
							const symptom = task.symptom || '-';

							// 고객정보
							const customerName = task.customerName || '-';
							const address = task.address || '-';
							const requestedAt = task.requestedAt || '-';
							const onsiteContact = task.onsiteContact || '-';

							// (선택) 방문예정일 / 담당자 정보가 내려오면 표시
							const scheduledDate = task.scheduledDate || null;
							const managerName = task.managerName || null;
							const managerContact = task.managerContact || null;

							return `
								<div class="calendar-task calendar-task-as">
									<!-- ✅ 제품정보+고객정보를 "한 카드"에 함께 -->
									<div class="index-third-modal-card">

										<div class="index-third-modal-section">
											<div class="index-third-modal-section-title">제품정보</div>

											<div class="index-third-modal-row">
												<div class="index-third-modal-label">제품명</div>
												<div class="index-third-modal-value">${escapeHtml(productName)}</div>
											</div>

											<div class="index-third-modal-row">
												<div class="index-third-modal-label">사이즈</div>
												<div class="index-third-modal-value">${escapeHtml(productSize)}</div>
											</div>

											<div class="index-third-modal-row">
												<div class="index-third-modal-label">컬러</div>
												<div class="index-third-modal-value">${escapeHtml(productColor)}</div>
											</div>

											<div class="index-third-modal-row">
												<div class="index-third-modal-label">옵션 여부</div>
												<div class="index-third-modal-value">${escapeHtml(productOptions)}</div>
											</div>

											<div class="index-third-modal-row">
												<div class="index-third-modal-label">증상</div>
												<div class="index-third-modal-value">${escapeHtml(symptom)}</div>
											</div>
										</div>

										<div class="index-third-modal-section">
											<div class="index-third-modal-section-title">고객 정보</div>

											<div class="index-third-modal-row">
												<div class="index-third-modal-label">고객 성함</div>
												<div class="index-third-modal-value">${escapeHtml(customerName)}</div>
											</div>

											<div class="index-third-modal-row">
												<div class="index-third-modal-label">주소</div>
												<div class="index-third-modal-value">${escapeHtml(address)}</div>
											</div>

											<div class="index-third-modal-row">
												<div class="index-third-modal-label">신청일</div>
												<div class="index-third-modal-value">${escapeHtml(requestedAt)}</div>
											</div>

											<div class="index-third-modal-row">
												<div class="index-third-modal-label">현장연락처</div>
												<div class="index-third-modal-value">${escapeHtml(onsiteContact)}</div>
											</div>

											${scheduledDate ? `
											<div class="index-third-modal-row">
												<div class="index-third-modal-label">방문예정일</div>
												<div class="index-third-modal-value">${escapeHtml(scheduledDate)}</div>
											</div>` : ''}

											${(managerName || managerContact) ? `
											<div class="index-third-modal-row">
												<div class="index-third-modal-label">AS 담당자</div>
												<div class="index-third-modal-value">
													${escapeHtml(managerName || '-')}${managerContact ? ' / ' + escapeHtml(managerContact) : ''}
												</div>
											</div>` : ''}
										</div>

									</div>
								</div>
							`;
						}

						// -------------------------
						// ✅ TASK
						// -------------------------
						if (task.type === 'TASK') {
							const orders = task.orders || [];

							const orderListHtml = orders.map((order) => `
								<div class="calendar-task-order">
									<p><strong>#TASK_${escapeHtml(task.id ?? '')}</strong></p>
									<p><span>주문일:</span> ${escapeHtml(order.createdAt || '-')}</p>
									<p><span>배송희망일:</span> ${escapeHtml(order.preferredDeliveryDate || '-')}</p>
									<p><span>배송지:</span> ${escapeHtml(order.address || '-')}</p>
									<p><span>수량:</span> ${order.quantity != null ? order.quantity : '-'}</p>
									<p><span>가격:</span> ${order.price != null ? Number(order.price).toLocaleString() + '원' : '-'}</p>
									<p><span>카테고리:</span> ${escapeHtml(order.categoryName || '-')}</p>
								</div>
							`).join('');

							return `
								<div class="calendar-task calendar-task-task">
									<div class="index-third-modal-card">
										${orderListHtml}
									</div>
								</div>
							`;
						}

						return '';
					}).join('');
				}

				modal.style.display = 'block';

				// ✅ 모달 열릴 때 스크롤 상단으로
				if (modalBody) modalBody.scrollTop = 0;
			})
			.catch(err => {
				console.error("일정 상세 조회 실패:", err);
				alert("일정 정보를 불러오는 데 실패했습니다.");
			});
	}

	// =========================
	// ✅ FullCalendar 초기화
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