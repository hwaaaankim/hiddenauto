(function() {
	'use strict';

	const API_BASE = '/admin/process-test/api';

	const state = {
		processes: [],
		selectedProcessId: null,
		session: null
	};

	document.addEventListener('DOMContentLoaded', function() {
		bindEvents();
		loadProcesses();
	});

	function bindEvents() {
		byId('process-test-reload-btn').addEventListener('click', loadProcesses);
		byId('process-test-start-btn').addEventListener('click', startSession);
	}

	async function loadProcesses() {
		try {
			const result = await apiFetch(`${API_BASE}/processes`, {
				method: 'GET'
			});

			state.processes = Array.isArray(result) ? result : [];
			renderProcessList();
		} catch (e) {
			alert(e.message || '프로세스 목록 조회 중 오류가 발생했습니다.');
		}
	}

	function renderProcessList() {
		const listEl = byId('process-test-process-list');

		if (state.processes.length === 0) {
			listEl.innerHTML = '<div class="text-muted small">등록된 프로세스가 없습니다.</div>';
			return;
		}

		listEl.innerHTML = state.processes.map(function(process) {
			const active = Number(state.selectedProcessId) === Number(process.id) ? 'active' : '';

			return `
				<div class="process-test-process-item ${active}" data-process-id="${escapeAttr(process.id)}">
					<div class="d-flex justify-content-between align-items-center">
						<div class="process-test-process-name">${escapeHtml(process.name)}</div>
						<span class="badge ${getStatusClass(process.status)}">${escapeHtml(process.status || 'DRAFT')}</span>
					</div>
					<div class="process-test-process-desc">${escapeHtml(process.description || '-')}</div>
					<div class="process-test-process-desc">생성: ${formatDateTime(process.createdAt)}</div>
				</div>
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
		if (!state.selectedProcessId) {
			alert('테스트할 프로세스를 선택해주세요.');
			return;
		}

		const payload = {
			processId: state.selectedProcessId,
			actorType: byId('process-test-actor-type').value,
			actorName: byId('process-test-actor-name').value.trim(),
			actorPhone: byId('process-test-actor-phone').value.trim()
		};

		const formData = new FormData();
		formData.append('payload', new Blob([JSON.stringify(payload)], {
			type: 'application/json'
		}));

		try {
			const session = await apiFetch(`${API_BASE}/sessions`, {
				method: 'POST',
				body: formData,
				isFormData: true
			});

			state.session = session;
			renderSession();
		} catch (e) {
			alert(e.message || '테스트 세션 생성 중 오류가 발생했습니다.');
		}
	}

	function renderSession() {
		renderSessionHeader();
		renderCurrentUnit();
		renderPrice();
		renderAnswerHistory();
	}

	function renderSessionHeader() {
		const titleEl = byId('process-test-session-title');
		const metaEl = byId('process-test-session-meta');
		const badgeEl = byId('process-test-status-badge');

		if (!state.session) {
			titleEl.textContent = '테스트 세션 없음';
			metaEl.textContent = '프로세스를 선택 후 테스트를 시작해주세요.';
			badgeEl.textContent = 'READY';
			badgeEl.className = 'badge bg-light text-dark';
			return;
		}

		titleEl.textContent = state.session.processName;
		metaEl.textContent = `세션: ${state.session.sessionKey}`;

		badgeEl.textContent = state.session.status;
		badgeEl.className = state.session.status === 'COMPLETED'
			? 'badge bg-success'
			: 'badge bg-primary';
	}

	function renderCurrentUnit() {
		const area = byId('process-test-current-area');

		if (!state.session) {
			area.innerHTML = `
			<div class="text-center text-muted py-5">
				프로세스를 선택하고 테스트를 시작하면 현재 질문이 표시됩니다.
			</div>
		`;
			return;
		}

		if (state.session.status === 'COMPLETED') {
			area.innerHTML = `
			<div class="process-test-complete-box">
				<div class="process-test-complete-title">프로세스 완료</div>
				<div class="text-muted mb-3">모든 질문이 완료되었고 답변이 저장되었습니다.</div>
				<button type="button" class="btn btn-outline-primary" id="process-test-reload-session-btn">
					세션 다시 조회
				</button>
			</div>
		`;

			byId('process-test-reload-session-btn').addEventListener('click', reloadSession);
			return;
		}

		const unit = state.session.currentUnit;
		if (!unit) {
			area.innerHTML = '<div class="text-muted py-5 text-center">현재 UNIT 정보가 없습니다.</div>';
			return;
		}

		area.innerHTML = `
		<div class="process-test-step-chip">${escapeHtml(unit.stepTitle || '-')} · ${escapeHtml(unit.unitTitle || '-')}</div>
		<div class="process-test-question-title">${escapeHtml(unit.questionText || '질문 없음')}</div>
		${unit.helperText ? `<div class="process-test-helper">${escapeHtml(unit.helperText)}</div>` : ''}

		<div class="alert alert-light border small mb-3">
			분기가 없거나 AUTO_NEXT인 경우, 현재 STEP 안의 다음 UNIT이 아니라
			<strong>다음 STEP의 첫 번째 UNIT</strong>으로 이동합니다.
		</div>

		<div class="process-test-answer-box">
			<form id="process-test-answer-form">
				${buildAnswerForm(unit)}
				<div class="d-flex justify-content-end gap-2 mt-3">
					<button type="button" class="btn btn-outline-secondary" id="process-test-reload-session-btn">세션 다시 조회</button>
					<button type="submit" class="btn btn-primary">답변 저장 후 다음 STEP 이동</button>
				</div>
			</form>
		</div>
	`;

		byId('process-test-reload-session-btn').addEventListener('click', reloadSession);

		byId('process-test-answer-form').addEventListener('submit', function(event) {
			event.preventDefault();
			submitAnswer();
		});
	}

	function buildAnswerForm(unit) {
		const answerType = normalizeAnswerType(unit.answerType);

		if (answerType === 'SINGLE_SELECT') {
			if (!unit.options || unit.options.length === 0) {
				return '<div class="text-muted small">선택 가능한 답변이 없습니다.</div>';
			}

			return unit.options.map(function(option, index) {
				const id = `process-test-option-${index}`;

				return `
				<label class="process-test-option" for="${id}">
					<input type="radio"
						name="process-test-selected-option"
						id="${id}"
						value="${escapeAttr(option.optionKey)}"
						data-label="${escapeAttr(option.label)}"
						${index === 0 ? 'checked' : ''}>
					<strong>${escapeHtml(option.label)}</strong>
					${option.valueText ? `<div class="small text-muted">${escapeHtml(option.valueText)}</div>` : ''}
				</label>
			`;
			}).join('');
		}

		if (answerType === 'TEXT_INPUT' || answerType === 'NUMBER_INPUT' || answerType === 'MULTI_INPUT') {
			if (!unit.fields || unit.fields.length === 0) {
				return '<div class="text-muted small">입력 필드가 없습니다.</div>';
			}

			return unit.fields.map(function(field) {
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
							data-answer-type="${escapeAttr(answerType)}"
							placeholder="${escapeAttr(field.placeholder || '')}"
							${field.requiredYn ? 'required' : ''}>
						${field.unitText ? `<span class="input-group-text">${escapeHtml(field.unitText)}</span>` : ''}
					</div>
				</div>
			`;
			}).join('');
		}

		if (answerType === 'FILE_UPLOAD') {
			return `
			<div class="process-test-field-row">
				<label>파일 등록</label>
				<input type="file" class="form-control" id="process-test-file-input" multiple>
				<div class="small text-muted mt-2">여러 파일을 선택할 수 있습니다.</div>
			</div>
		`;
		}

		return '<div class="text-muted small">지원하지 않는 답변 형태입니다.</div>';
	}

	async function submitAnswer() {
		if (!state.session || !state.session.currentUnit) {
			alert('현재 진행 중인 질문이 없습니다.');
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

		if (answerType === 'SINGLE_SELECT') {
			const checked = document.querySelector('input[name="process-test-selected-option"]:checked');

			if (!checked) {
				alert('답변을 선택해주세요.');
				return;
			}

			payload.selectedOptionKey = checked.value;
			payload.selectedOptionLabel = checked.dataset.label || checked.value;
			payload.answerValues = {
				selectedOptionKey: payload.selectedOptionKey,
				selectedOptionLabel: payload.selectedOptionLabel
			};
		}

		if (answerType === 'TEXT_INPUT' || answerType === 'NUMBER_INPUT' || answerType === 'MULTI_INPUT') {
			const inputs = document.querySelectorAll('.process-test-field-input');

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
			payload.answerValues = {
				fileUpload: true
			};
		}

		const formData = new FormData();
		formData.append('payload', new Blob([JSON.stringify(payload)], {
			type: 'application/json'
		}));

		const fileInput = byId('process-test-file-input');
		if (fileInput && fileInput.files && fileInput.files.length > 0) {
			Array.from(fileInput.files).forEach(function(file) {
				formData.append('files', file);
			});
		}

		try {
			const session = await apiFetch(`${API_BASE}/sessions/${state.session.sessionKey}/answers`, {
				method: 'POST',
				body: formData,
				isFormData: true
			});

			state.session = session;
			renderSession();
		} catch (e) {
			alert(e.message || '답변 저장 중 오류가 발생했습니다.');
		}
	}

	async function reloadSession() {
		if (!state.session || !state.session.sessionKey) {
			return;
		}

		try {
			const session = await apiFetch(`${API_BASE}/sessions/${state.session.sessionKey}`, {
				method: 'GET'
			});

			state.session = session;
			renderSession();
		} catch (e) {
			alert(e.message || '세션 조회 중 오류가 발생했습니다.');
		}
	}

	function renderPrice() {
		const amountEl = byId('process-test-price-amount');
		const jsonEl = byId('process-test-price-json');

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

		if (!state.session || !state.session.answers || state.session.answers.length === 0) {
			historyEl.innerHTML = '<div class="p-3 text-muted small">저장된 답변이 없습니다.</div>';
			return;
		}

		historyEl.innerHTML = state.session.answers.map(function(answer) {
			const filesHtml = answer.files && answer.files.length > 0
				? answer.files.map(function(file) {
					return `<a class="process-test-history-file" href="${escapeAttr(file.fileUrl)}" target="_blank">${escapeHtml(file.originalFilename)}</a>`;
				}).join('')
				: '';

			return `
				<div class="process-test-history-item">
					<div class="process-test-history-question">${escapeHtml(answer.questionText || '-')}</div>
					<div class="process-test-history-answer">${escapeHtml(answer.displayAnswerText || '-')}</div>
					${filesHtml}
					<div class="small text-muted mt-1">${formatDateTime(answer.createdAt)}</div>
				</div>
			`;
		}).join('');
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
		const data = text ? JSON.parse(text) : null;

		if (!response.ok) {
			throw new Error(data && data.message ? data.message : '요청 처리 중 오류가 발생했습니다.');
		}

		return data;
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

		if (answerType === 'MULTI_INPUT') {
			return 'NUMBER_INPUT';
		}

		return answerType;
	}

	function getFieldInputType(answerType, field) {
		if (answerType === 'NUMBER_INPUT') {
			return 'number';
		}

		if (answerType === 'TEXT_INPUT') {
			return 'text';
		}

		if (answerType === 'MULTI_INPUT') {
			return field && field.inputValueType === 'NUMBER' ? 'number' : 'text';
		}

		return field && field.inputValueType === 'NUMBER' ? 'number' : 'text';
	}
})();