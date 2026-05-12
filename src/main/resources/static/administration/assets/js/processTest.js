(function() {
	'use strict';

	const API_BASE = '/admin/process-test/api';

	const state = {
		processes: [],
		selectedProcessId: null,
		session: null,
		busy: false
	};

	document.addEventListener('DOMContentLoaded', function() {
		bindEvents();
		loadProcesses();
	});

	function bindEvents() {
		bindClick('process-test-reload-btn', loadProcesses);
		bindClick('process-test-start-btn', startSession);
		bindClick('process-test-exit-btn', backToStartScreen);
		bindClick('process-test-reload-session-btn', reloadSession);
	}

	function bindClick(id, handler) {
		const el = byId(id);

		if (!el) {
			return;
		}

		el.addEventListener('click', handler);
	}

	async function loadProcesses() {
		try {
			const result = await apiFetch(`${API_BASE}/processes`, {
				method: 'GET'
			});

			state.processes = Array.isArray(result) ? result : [];

			if (!state.selectedProcessId && state.processes.length > 0) {
				state.selectedProcessId = state.processes[0].id;
			}

			renderProcessList();
		 } catch (e) {
			alert(e.message || '프로세스 목록 조회 중 오류가 발생했습니다.');
		}
	}

	function renderProcessList() {
		const listEl = byId('process-test-process-list');

		if (!listEl) {
			return;
		}

		if (state.processes.length === 0) {
			listEl.innerHTML = `
				<div class="process-test-empty-box">
					<div class="fw-bold mb-1">등록된 프로세스가 없습니다.</div>
					<div class="small text-muted">프로세스 등록 화면에서 먼저 프로세스를 생성해주세요.</div>
				</div>
			`;
			return;
		}

		listEl.innerHTML = state.processes.map(function(process) {
			const active = Number(state.selectedProcessId) === Number(process.id) ? 'active' : '';

			return `
				<button type="button"
					class="process-test-process-item ${active}"
					data-process-id="${escapeAttr(process.id)}">
					<div class="d-flex justify-content-between align-items-center gap-2">
						<div class="process-test-process-name">${escapeHtml(process.name)}</div>
						<span class="badge ${getStatusClass(process.status)}">${escapeHtml(process.status || 'DRAFT')}</span>
					</div>
					<div class="process-test-process-desc">${escapeHtml(process.description || '-')}</div>
					<div class="process-test-process-desc">생성: ${formatDateTime(process.createdAt)}</div>
				</button>
			`;
		}).join('');

		listEl.querySelectorAll('.process-test-process-item').forEach(function(item) {
			item.addEventListener('click', function() {
				state.selectedProcessId = Number(item.dataset.processId);
				renderProcessList();
			});
		});
	}

	async function startSession() {
		if (state.busy) {
			return;
		}

		if (!state.selectedProcessId) {
			alert('테스트할 프로세스를 선택해주세요.');
			return;
		}

		const payload = {
			processId: state.selectedProcessId,
			actorType: byId('process-test-actor-type')?.value || 'ADMIN',
			actorName: byId('process-test-actor-name')?.value.trim() || '',
			actorPhone: byId('process-test-actor-phone')?.value.trim() || ''
		};

		const formData = new FormData();
		formData.append('payload', new Blob([JSON.stringify(payload)], {
			type: 'application/json'
		}));

		try {
			setBusy(true);

			const session = await apiFetch(`${API_BASE}/sessions`, {
				method: 'POST',
				body: formData,
				isFormData: true
			});

			state.session = session;

			showChatScreen();
			renderSession({
				forceScroll: true
			});
		} catch (e) {
			alert(e.message || '테스트 세션 생성 중 오류가 발생했습니다.');
		} finally {
			setBusy(false);
		}
	}

	function backToStartScreen() {
		const confirmBack = confirm('현재 테스트 화면을 닫고 처음 화면으로 돌아가시겠습니까? 저장된 세션 데이터는 삭제되지 않습니다.');

		if (!confirmBack) {
			return;
		}

		state.session = null;

		byId('process-test-chat-screen')?.classList.add('d-none');
		byId('process-test-start-screen')?.classList.remove('d-none');

		renderSessionHeader();
		renderPrice();
		renderAnswerHistory();
		renderSelectedStrip();
	}

	function showChatScreen() {
		byId('process-test-start-screen')?.classList.add('d-none');
		byId('process-test-chat-screen')?.classList.remove('d-none');
	}

	function renderSession(options) {
		const renderOptions = options || {};

		renderSessionHeader();
		renderSelectedStrip();
		renderChatThread();
		renderPrice();
		renderAnswerHistory();

		if (renderOptions.forceScroll) {
			requestAnimationFrame(function() {
				focusCurrentQuestion(true);
			});
		}
	}

	function renderSessionHeader() {
		const titleEl = byId('process-test-session-title');
		const metaEl = byId('process-test-session-meta');
		const badgeEl = byId('process-test-status-badge');

		if (!titleEl || !metaEl || !badgeEl) {
			return;
		}

		if (!state.session) {
			titleEl.textContent = '테스트 세션 없음';
			metaEl.textContent = '프로세스를 선택 후 테스트를 시작해주세요.';
			badgeEl.textContent = 'READY';
			badgeEl.className = 'badge bg-light text-dark';
			return;
		}

		titleEl.textContent = state.session.processName || '프로세스 테스트';
		metaEl.textContent = `세션: ${state.session.sessionKey}`;

		badgeEl.textContent = state.session.status || 'IN_PROGRESS';
		badgeEl.className = state.session.status === 'COMPLETED'
			? 'badge bg-success'
			: 'badge bg-primary';
	}

	function renderSelectedStrip() {
		const stripEl = byId('process-test-selected-strip');

		if (!stripEl) {
			return;
		}

		const answers = state.session && Array.isArray(state.session.answers)
			? state.session.answers
			: [];

		if (answers.length === 0) {
			stripEl.innerHTML = '<span class="text-muted small">아직 선택된 답변이 없습니다.</span>';
			return;
		}

		stripEl.innerHTML = answers.map(function(answer, index) {
			const latest = index === answers.length - 1 ? 'latest' : '';

			return `
				<span class="process-test-selected-chip ${latest}" title="${escapeAttr(answer.questionText || '')}">
					<span class="process-test-selected-chip-index">${index + 1}</span>
					${escapeHtml(answer.displayAnswerText || '-')}
				</span>
			`;
		}).join('');
	}

	function renderChatThread() {
		const threadEl = byId('process-test-chat-thread');

		if (!threadEl) {
			return;
		}

		if (!state.session) {
			threadEl.innerHTML = `
				<div class="text-center text-muted py-5">
					프로세스를 선택하고 테스트를 시작하면 현재 질문이 표시됩니다.
				</div>
			`;
			return;
		}

		const answers = Array.isArray(state.session.answers) ? state.session.answers : [];
		let html = '';

		html += `
			<div class="process-test-system-message">
				<div class="fw-bold mb-1">테스트가 시작되었습니다.</div>
				<div>
					답변을 선택하거나 입력하면 다음 질문이 이어집니다.
					이전 답변의 <strong>초기화</strong>를 누르면 해당 답변부터 다시 진행합니다.
				</div>
			</div>
		`;

		answers.forEach(function(answer, index) {
			html += buildAnsweredChatPair(answer, index);
		});

		if (state.session.status === 'COMPLETED') {
			html += buildCompleteMessage();
		} else if (state.session.currentUnit) {
			html += buildCurrentQuestion(state.session.currentUnit);
		} else {
			html += `
				<div class="process-test-system-message process-test-warning-message">
					현재 진행할 질문 정보가 없습니다.
				</div>
			`;
		}

		threadEl.innerHTML = html;

		bindChatEvents(threadEl);
	}

	function buildAnsweredChatPair(answer, index) {
		const filesHtml = answer.files && answer.files.length > 0
			? `
				<div class="process-test-bubble-files">
					${answer.files.map(function(file) {
						return `
							<a href="${escapeAttr(file.fileUrl)}" target="_blank" class="process-test-bubble-file">
								${escapeHtml(file.originalFilename || '첨부파일')}
							</a>
						`;
					}).join('')}
				</div>
			`
			: '';

		return `
			<div class="process-test-message-row bot process-test-animate-in">
				<div class="process-test-avatar">Q</div>
				<div class="process-test-bubble bot">
					<div class="process-test-bubble-meta">질문 ${index + 1}</div>
					<div class="process-test-bubble-title">${escapeHtml(answer.questionText || '-')}</div>
				</div>
			</div>

			<div class="process-test-message-row user process-test-animate-in">
				<div class="process-test-bubble user">
					<div class="process-test-bubble-meta">선택한 답변</div>
					<div class="process-test-bubble-title">${escapeHtml(answer.displayAnswerText || '-')}</div>
					${filesHtml}
					<div class="process-test-bubble-actions">
						<button type="button"
							class="btn btn-sm btn-light process-test-reset-answer-btn"
							data-unit-key="${escapeAttr(answer.unitKey)}">
							이 답변부터 다시 진행
						</button>
					</div>
				</div>
				<div class="process-test-avatar user">A</div>
			</div>
		`;
	}

	function buildCurrentQuestion(unit) {
		return `
			<div class="process-test-message-row bot process-test-current-question process-test-animate-in"
				data-current-question="true">
				<div class="process-test-avatar">Q</div>
				<div class="process-test-bubble bot process-test-current-bubble">
					<div class="process-test-step-chip">
						${escapeHtml(unit.stepTitle || '-')} · ${escapeHtml(unit.unitTitle || '-')}
					</div>
					<div class="process-test-question-title">${escapeHtml(unit.questionText || '질문 없음')}</div>
					${unit.helperText ? `<div class="process-test-helper">${escapeHtml(unit.helperText)}</div>` : ''}
					<div class="process-test-answer-box">
						${buildAnswerInput(unit)}
					</div>
				</div>
			</div>
		`;
	}

	function buildCompleteMessage() {
		return `
			<div class="process-test-complete-box process-test-animate-in" data-current-question="true">
				<div class="process-test-complete-title">프로세스 완료</div>
				<div class="text-muted mb-3">모든 질문이 완료되었고 답변이 저장되었습니다.</div>
				<button type="button" class="btn btn-outline-primary" data-action="reload-session">
					세션 다시 조회
				</button>
			</div>
		`;
	}

	function buildAnswerInput(unit) {
		const answerType = normalizeAnswerType(unit.answerType);

		if (answerType === 'SINGLE_SELECT') {
			if (!unit.options || unit.options.length === 0) {
				return '<div class="text-muted small">선택 가능한 답변이 없습니다.</div>';
			}

			return `
				<div class="process-test-option-button-list">
					${unit.options.map(function(option) {
						return `
							<button type="button"
								class="process-test-option-button"
								data-action="select-option"
								data-option-key="${escapeAttr(option.optionKey)}"
								data-option-label="${escapeAttr(option.label)}">
								<span class="process-test-option-label">${escapeHtml(option.label)}</span>
								${option.valueText ? `<span class="process-test-option-desc">${escapeHtml(option.valueText)}</span>` : ''}
							</button>
						`;
					}).join('')}
				</div>
			`;
		}

		if (answerType === 'TEXT_INPUT' || answerType === 'NUMBER_INPUT' || answerType === 'MULTI_INPUT') {
			if (!unit.fields || unit.fields.length === 0) {
				return '<div class="text-muted small">입력 필드가 없습니다.</div>';
			}

			return `
				<form class="process-test-answer-form" data-answer-type="${escapeAttr(answerType)}">
					${unit.fields.map(function(field) {
						const inputType = getFieldInputType(answerType, field);

						return `
							<div class="process-test-field-row">
								<label>
									${escapeHtml(field.label)}
									${field.requiredYn ? '<span class="text-danger">*</span>' : ''}
								</label>
								<div class="input-group">
									<input type="${inputType}"
										class="form-control process-test-field-input"
										data-field-key="${escapeAttr(field.fieldKey)}"
										data-field-label="${escapeAttr(field.label)}"
										data-field-type="${escapeAttr(field.inputValueType)}"
										placeholder="${escapeAttr(field.placeholder || '')}"
										${field.requiredYn ? 'required' : ''}>
									${field.unitText ? `<span class="input-group-text">${escapeHtml(field.unitText)}</span>` : ''}
								</div>
							</div>
						`;
					}).join('')}

					<div class="d-flex justify-content-end mt-3">
						<button type="submit" class="btn btn-primary">
							답변 입력
						</button>
					</div>
				</form>
			`;
		}

		if (answerType === 'FILE_UPLOAD') {
			return `
				<form class="process-test-answer-form" data-answer-type="${escapeAttr(answerType)}">
					<div class="process-test-field-row">
						<label>파일 등록</label>
						<input type="file" class="form-control" id="process-test-file-input" multiple>
						<div class="small text-muted mt-2">여러 파일을 선택할 수 있습니다.</div>
					</div>

					<div class="d-flex justify-content-end mt-3">
						<button type="submit" class="btn btn-primary">
							파일 등록 후 다음
						</button>
					</div>
				</form>
			`;
		}

		return '<div class="text-muted small">지원하지 않는 답변 형태입니다.</div>';
	}

	function bindChatEvents(threadEl) {
		threadEl.querySelectorAll('[data-action="select-option"]').forEach(function(button) {
			button.addEventListener('click', function() {
				submitSelectedOption(button);
			});
		});

		threadEl.querySelectorAll('.process-test-answer-form').forEach(function(form) {
			form.addEventListener('submit', function(event) {
				event.preventDefault();
				submitFormAnswer(form);
			});
		});

		threadEl.querySelectorAll('.process-test-reset-answer-btn').forEach(function(button) {
			button.addEventListener('click', function() {
				resetFromAnswer(button.dataset.unitKey);
			});
		});

		threadEl.querySelectorAll('[data-action="reload-session"]').forEach(function(button) {
			button.addEventListener('click', reloadSession);
		});
	}

	async function submitSelectedOption(button) {
		if (state.busy || !state.session || !state.session.currentUnit) {
			return;
		}

		const unit = state.session.currentUnit;
		const optionKey = button.dataset.optionKey;
		const optionLabel = button.dataset.optionLabel || optionKey;

		if (!optionKey) {
			alert('선택한 답변 정보가 없습니다.');
			return;
		}

		const payload = {
			unitKey: unit.unitKey,
			selectedOptionKey: optionKey,
			selectedOptionLabel: optionLabel,
			answerValues: {
				selectedOptionKey: optionKey,
				selectedOptionLabel: optionLabel
			}
		};

		await submitAnswerPayload(payload, null);
	}

	async function submitFormAnswer(form) {
		if (state.busy || !state.session || !state.session.currentUnit) {
			return;
		}

		const unit = state.session.currentUnit;
		const answerType = normalizeAnswerType(unit.answerType);

		const payload = {
			unitKey: unit.unitKey,
			selectedOptionKey: null,
			selectedOptionLabel: null,
			answerValues: {}
		};

		let files = null;

		if (answerType === 'TEXT_INPUT' || answerType === 'NUMBER_INPUT' || answerType === 'MULTI_INPUT') {
			const inputs = form.querySelectorAll('.process-test-field-input');

			for (const input of inputs) {
				const rawValue = input.value.trim();
				const fieldLabel = input.dataset.fieldLabel || '입력값';
				const fieldKey = input.dataset.fieldKey;
				const fieldType = input.dataset.fieldType;

				if (input.required && !rawValue) {
					alert(`${fieldLabel} 값을 입력해주세요.`);
					input.focus();
					return;
				}

				if (!rawValue) {
					continue;
				}

				if (answerType === 'NUMBER_INPUT' || fieldType === 'NUMBER') {
					const numberValue = Number(rawValue);

					if (Number.isNaN(numberValue)) {
						alert(`${fieldLabel} 값은 숫자로 입력해주세요.`);
						input.focus();
						return;
					}

					payload.answerValues[fieldKey] = numberValue;
				} else {
					payload.answerValues[fieldKey] = rawValue;
				}
			}
		}

		if (answerType === 'FILE_UPLOAD') {
			const fileInput = form.querySelector('#process-test-file-input');

			files = fileInput && fileInput.files
				? Array.from(fileInput.files)
				: [];

			if (unit.requiredYn && files.length === 0) {
				alert('파일을 선택해주세요.');
				return;
			}

			payload.answerValues = {
				fileUpload: true,
				fileCount: files.length
			};
		}

		await submitAnswerPayload(payload, files);
	}

	async function submitAnswerPayload(payload, files) {
		if (!state.session || !state.session.sessionKey) {
			alert('진행 중인 세션이 없습니다.');
			return;
		}

		const formData = new FormData();
		formData.append('payload', new Blob([JSON.stringify(payload)], {
			type: 'application/json'
		}));

		if (Array.isArray(files) && files.length > 0) {
			files.forEach(function(file) {
				formData.append('files', file);
			});
		}

		try {
			setBusy(true);

			const session = await apiFetch(`${API_BASE}/sessions/${encodeURIComponent(state.session.sessionKey)}/answers`, {
				method: 'POST',
				body: formData,
				isFormData: true
			});

			state.session = session;

			renderSession({
				forceScroll: true
			});
		} catch (e) {
			alert(e.message || '답변 저장 중 오류가 발생했습니다.');
		} finally {
			setBusy(false);
		}
	}

	async function resetFromAnswer(unitKey) {
		if (state.busy || !state.session || !state.session.sessionKey) {
			return;
		}

		if (!unitKey) {
			alert('초기화할 답변 정보가 없습니다.');
			return;
		}

		const ok = confirm('이 답변부터 다시 진행하시겠습니까?\n해당 답변과 이후 답변은 삭제되고, 이 질문부터 다시 시작합니다.');

		if (!ok) {
			return;
		}

		try {
			setBusy(true);

			const session = await apiFetch(
				`${API_BASE}/sessions/${encodeURIComponent(state.session.sessionKey)}/answers/${encodeURIComponent(unitKey)}/reset`,
				{
					method: 'POST'
				}
			);

			state.session = session;

			renderSession({
				forceScroll: true
			});
		} catch (e) {
			alert(e.message || '답변 초기화 중 오류가 발생했습니다.');
		} finally {
			setBusy(false);
		}
	}

	async function reloadSession() {
		if (!state.session || !state.session.sessionKey) {
			return;
		}

		try {
			setBusy(true);

			const session = await apiFetch(`${API_BASE}/sessions/${encodeURIComponent(state.session.sessionKey)}`, {
				method: 'GET'
			});

			state.session = session;

			renderSession({
				forceScroll: false
			});
		} catch (e) {
			alert(e.message || '세션 조회 중 오류가 발생했습니다.');
		} finally {
			setBusy(false);
		}
	}

	function renderPrice() {
		const amountEl = byId('process-test-price-amount');
		const jsonEl = byId('process-test-price-json');

		if (!amountEl || !jsonEl) {
			return;
		}

		if (!state.session) {
			amountEl.textContent = '0원';
			jsonEl.textContent = '{}';
			return;
		}

		amountEl.textContent = `${formatNumber(state.session.calculatedPriceAmount || 0)}원`;

		try {
			jsonEl.textContent = JSON.stringify(JSON.parse(state.session.priceResultJson || '{}'), null, 2);
		} catch (e) {
			jsonEl.textContent = state.session.priceResultJson || '{}';
		}
	}

	function renderAnswerHistory() {
		const historyEl = byId('process-test-answer-history');

		if (!historyEl) {
			return;
		}

		if (!state.session || !state.session.answers || state.session.answers.length === 0) {
			historyEl.innerHTML = '<div class="p-3 text-muted small">저장된 답변이 없습니다.</div>';
			return;
		}

		historyEl.innerHTML = state.session.answers.map(function(answer, index) {
			const filesHtml = answer.files && answer.files.length > 0
				? answer.files.map(function(file) {
					return `
						<a class="process-test-history-file"
							href="${escapeAttr(file.fileUrl)}"
							target="_blank">
							${escapeHtml(file.originalFilename)}
						</a>
					`;
				}).join('')
				: '';

			return `
				<div class="process-test-history-item">
					<div class="d-flex justify-content-between align-items-start gap-2">
						<div class="process-test-history-question">
							${index + 1}. ${escapeHtml(answer.questionText || '-')}
						</div>
						<button type="button"
							class="btn btn-sm btn-outline-secondary process-test-history-reset-btn"
							data-unit-key="${escapeAttr(answer.unitKey)}">
							초기화
						</button>
					</div>
					<div class="process-test-history-answer">${escapeHtml(answer.displayAnswerText || '-')}</div>
					${filesHtml}
					<div class="small text-muted mt-1">${formatDateTime(answer.createdAt)}</div>
				</div>
			`;
		}).join('');

		historyEl.querySelectorAll('.process-test-history-reset-btn').forEach(function(button) {
			button.addEventListener('click', function() {
				resetFromAnswer(button.dataset.unitKey);
			});
		});
	}

	function focusCurrentQuestion(force) {
		const container = byId('process-test-chat-thread');

		if (!container) {
			return;
		}

		const target = container.querySelector('[data-current-question="true"]')
			|| container.querySelector('.process-test-message-row:last-child');

		if (!target) {
			return;
		}

		if (!force && isComfortablyVisible(container, target)) {
			return;
		}

		const containerHeight = container.clientHeight;
		const targetTop = target.offsetTop;

		/*
		 * 질문이 화면 정중앙보다 약간 위쪽에 오도록 배치합니다.
		 * 그래야 사용자의 시선 아래쪽으로 답변 영역이 자연스럽게 이어집니다.
		 */
		const eyeLevelTop = Math.max(targetTop - Math.floor(containerHeight * 0.22), 0);

		container.scrollTo({
			top: eyeLevelTop,
			behavior: 'smooth'
		});
	}

	function isComfortablyVisible(container, target) {
		const containerRect = container.getBoundingClientRect();
		const targetRect = target.getBoundingClientRect();

		const topComfort = containerRect.top + 80;
		const bottomComfort = containerRect.bottom - 160;

		return targetRect.top >= topComfort && targetRect.bottom <= bottomComfort;
	}

	async function apiFetch(url, options) {
		const opts = options || {};
		const headers = opts.headers || {};

		if (!opts.isFormData) {
			headers['Content-Type'] = 'application/json';
		}

		const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
		const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

		if (csrfToken && csrfHeader) {
			headers[csrfHeader] = csrfToken;
		}

		const response = await fetch(url, {
			...opts,
			headers: headers
		});

		const text = await response.text();

		let data = null;

		try {
			data = text ? JSON.parse(text) : null;
		} catch (e) {
			data = null;
		}

		if (!response.ok) {
			throw new Error(data && data.message ? data.message : '요청 처리 중 오류가 발생했습니다.');
		}

		return data;
	}

	function setBusy(busy) {
		state.busy = busy;

		document.body.classList.toggle('process-test-busy', busy);

		document.querySelectorAll(
			'#process-test-start-btn, .process-test-option-button, .process-test-answer-form button, .process-test-reset-answer-btn, .process-test-history-reset-btn'
		).forEach(function(el) {
			el.disabled = busy;
		});
	}

	function byId(id) {
		return document.getElementById(id);
	}

	function getStatusClass(status) {
		if (status === 'ACTIVE') return 'bg-success';
		if (status === 'INACTIVE') return 'bg-secondary';
		return 'bg-warning text-dark';
	}

	function formatDateTime(value) {
		if (!value) return '-';

		const date = new Date(value);
		if (Number.isNaN(date.getTime())) return value;

		const yyyy = date.getFullYear();
		const mm = String(date.getMonth() + 1).padStart(2, '0');
		const dd = String(date.getDate()).padStart(2, '0');
		const hh = String(date.getHours()).padStart(2, '0');
		const mi = String(date.getMinutes()).padStart(2, '0');

		return `${yyyy}-${mm}-${dd} ${hh}:${mi}`;
	}

	function formatNumber(value) {
		return Number(value || 0).toLocaleString('ko-KR');
	}

	function escapeHtml(value) {
		return String(value ?? '')
			.replaceAll('&', '&amp;')
			.replaceAll('<', '&lt;')
			.replaceAll('>', '&gt;')
			.replaceAll('"', '&quot;')
			.replaceAll("'", '&#039;');
	}

	function escapeAttr(value) {
		return escapeHtml(value);
	}

	function normalizeAnswerType(answerType) {
		if (!answerType) {
			return 'SINGLE_SELECT';
		}

		return answerType;
	}

	function getFieldInputType(answerType, field) {
		if (field && field.inputValueType === 'NUMBER') {
			return 'number';
		}

		if (answerType === 'NUMBER_INPUT') {
			return 'number';
		}

		return 'text';
	}
})();