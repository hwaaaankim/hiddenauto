(function() {
	'use strict';

	const API_BASE = '/admin/process-maker/api/processes';

	const state = {
		processes: [],
		currentProcess: null,
		selected: {
			type: null,
			stepKey: null,
			unitKey: null
		},
		sortables: [],
		isDirty: false,
		zoom: 1
	};

	document.addEventListener('DOMContentLoaded', function() {
		bindEvents();
		showHomeScreen();
		loadProcessList();
	});

	function bindEvents() {
		byId('process-maker-refresh-btn').addEventListener('click', loadProcessList);
		byId('process-maker-create-btn').addEventListener('click', createProcess);
		byId('process-maker-save-btn').addEventListener('click', saveCurrentProcess);
		byId('process-maker-add-step-btn').addEventListener('click', addStep);

		const backHomeBtn = byId('process-maker-back-home-btn');
		if (backHomeBtn) {
			backHomeBtn.addEventListener('click', goBackToProcessHome);
		}

		const workspaceScreen = byId('process-maker-workspace-screen');
		if (workspaceScreen) {
			workspaceScreen.addEventListener('input', markDirty);
			workspaceScreen.addEventListener('change', markDirty);
		}

		byId('process-maker-process-name').addEventListener('input', function() {
			if (!state.currentProcess) return;
			state.currentProcess.name = this.value;
			renderCurrentTitle();
			renderProcessList();
		});

		byId('process-maker-process-description').addEventListener('input', function() {
			if (!state.currentProcess) return;
			state.currentProcess.description = this.value;
		});

		byId('process-maker-process-status').addEventListener('change', function() {
			if (!state.currentProcess) return;
			state.currentProcess.status = this.value;
			renderCurrentTitle();
		});

		byId('process-maker-step-title').addEventListener('input', function() {
			const step = getSelectedStep();
			if (!step) return;
			step.title = this.value;
			renderCanvas();
		});

		byId('process-maker-step-description').addEventListener('input', function() {
			const step = getSelectedStep();
			if (!step) return;
			step.description = this.value;
		});

		byId('process-maker-delete-step-btn').addEventListener('click', deleteSelectedStep);

		byId('process-maker-unit-title').addEventListener('input', function() {
			const unit = getSelectedUnit();
			if (!unit) return;
			unit.title = this.value;
			renderCanvas();
		});

		byId('process-maker-question-text').addEventListener('input', function() {
			const unit = getSelectedUnit();
			if (!unit) return;
			ensureQuestion(unit).questionText = this.value;
			renderCanvas();
		});

		byId('process-maker-answer-type').addEventListener('change', function() {
			const unit = getSelectedUnit();
			if (!unit) return;

			const question = ensureQuestion(unit);
			const nextType = normalizeAnswerType(this.value);

			question.answerType = nextType;
			question.options = question.options || [];
			question.fields = question.fields || [];

			if (nextType === 'SINGLE_SELECT') {
				if (question.options.length === 0) {
					question.options.push(createAnswerOption('답변 1'));
				}
				question.fields = [];
				removeInvalidBranchesForAnswerType(unit);
			}

			if (nextType === 'TEXT_INPUT') {
				question.options = [];
				if (question.fields.length === 0) {
					question.fields.push(createAnswerField('입력 1', 'TEXT'));
				}
				question.fields.forEach(function(field) {
					field.inputValueType = 'TEXT';
					field.unitText = '';
				});
				unit.branches = [];
			}

			if (nextType === 'NUMBER_INPUT') {
				question.options = [];
				if (question.fields.length === 0) {
					question.fields.push(createAnswerField('넓이', 'NUMBER'));
				}
				question.fields.forEach(function(field) {
					field.inputValueType = 'NUMBER';
					if (!field.unitText) {
						field.unitText = 'mm';
					}
				});
				removeInvalidBranchesForAnswerType(unit);
			}

			if (nextType === 'FILE_UPLOAD') {
				question.options = [];
				if (question.fields.length === 0) {
					question.fields.push(createAnswerField('파일', 'FILE'));
				}
				question.fields.forEach(function(field) {
					field.inputValueType = 'FILE';
					field.unitText = '';
				});
				removeInvalidBranchesForAnswerType(unit);
			}

			markDirty();

			renderEditor();
			renderCanvas();
		});

		byId('process-maker-add-option-btn').addEventListener('click', addAnswerOption);
		byId('process-maker-add-field-btn').addEventListener('click', addAnswerField);
		byId('process-maker-add-branch-btn').addEventListener('click', addBranch);
		byId('process-maker-delete-unit-btn').addEventListener('click', deleteSelectedUnit);

		const zoomInBtn = byId('process-maker-zoom-in-btn');
		const zoomOutBtn = byId('process-maker-zoom-out-btn');
		const zoomResetBtn = byId('process-maker-zoom-reset-btn');

		if (zoomInBtn) {
			zoomInBtn.addEventListener('click', function() {
				setCanvasZoom(state.zoom + 0.1);
			});
		}

		if (zoomOutBtn) {
			zoomOutBtn.addEventListener('click', function() {
				setCanvasZoom(state.zoom - 0.1);
			});
		}

		if (zoomResetBtn) {
			zoomResetBtn.addEventListener('click', function() {
				setCanvasZoom(1);
			});
		}

		window.addEventListener('resize', debounce(drawArrows, 120));
	}

	async function loadProcessList() {
		try {
			const result = await apiFetch(API_BASE, { method: 'GET' });
			state.processes = Array.isArray(result) ? result : [];
			renderProcessList();
		} catch (e) {
			alert(e.message || '프로세스 목록 조회 중 오류가 발생했습니다.');
		}
	}

	async function createProcess() {
		const nameEl = byId('process-maker-create-name');
		const descEl = byId('process-maker-create-description');

		const name = nameEl.value.trim();
		if (!name) {
			alert('프로세스 이름을 입력해주세요.');
			nameEl.focus();
			return;
		}

		try {
			const created = await apiFetch(API_BASE, {
				method: 'POST',
				body: JSON.stringify({
					name: name,
					description: descEl.value.trim()
				})
			});

			nameEl.value = '';
			descEl.value = '';

			await loadProcessList();
			await openProcess(created.id);
		} catch (e) {
			alert(e.message || '프로세스 생성 중 오류가 발생했습니다.');
		}
	}

	async function openProcess(processId) {
		try {
			const process = await apiFetch(`${API_BASE}/${processId}`, { method: 'GET' });
			normalizeProcess(process);

			state.currentProcess = process;
			state.selected = { type: 'PROCESS', stepKey: null, unitKey: null };
			clearDirty();

			showWorkspaceScreen();
			renderAll();
		} catch (e) {
			alert(e.message || '프로세스 조회 중 오류가 발생했습니다.');
		}
	}

	async function saveCurrentProcess() {
		if (!state.currentProcess || !state.currentProcess.id) {
			alert('저장할 프로세스를 선택해주세요.');
			return;
		}

		reindexAll();

		const validationErrors = validateProcessForSave(state.currentProcess);
		if (validationErrors.length > 0) {
			alert('[저장 전 검증 오류]\n\n' + validationErrors.map(function(error) {
				return '- ' + error;
			}).join('\n'));
			return;
		}

		try {
			const payload = buildSavePayload(state.currentProcess);

			const saved = await apiFetch(`${API_BASE}/${state.currentProcess.id}`, {
				method: 'PUT',
				body: JSON.stringify(payload)
			});

			normalizeProcess(saved);

			state.currentProcess = saved;
			clearDirty();

			await loadProcessList();
			renderAll();

			let message = '프로세스가 저장되었습니다.';

			if (Array.isArray(saved.validationWarnings) && saved.validationWarnings.length > 0) {
				message += '\n\n[검증 경고]\n';
				message += saved.validationWarnings.map(function(warning) {
					return '- ' + warning;
				}).join('\n');
			}

			alert(message);
		} catch (e) {
			alert(e.message || '프로세스 저장 중 오류가 발생했습니다.');
		}
	}

	function showHomeScreen() {
		const homeScreen = byId('process-maker-home-screen');
		const workspaceScreen = byId('process-maker-workspace-screen');
		const saveBtn = byId('process-maker-save-btn');

		if (homeScreen) {
			homeScreen.classList.remove('d-none');
		}

		if (workspaceScreen) {
			workspaceScreen.classList.add('d-none');
		}

		if (saveBtn) {
			saveBtn.classList.add('d-none');
		}

		drawArrows();
	}

	function showWorkspaceScreen() {
		const homeScreen = byId('process-maker-home-screen');
		const workspaceScreen = byId('process-maker-workspace-screen');
		const saveBtn = byId('process-maker-save-btn');

		if (homeScreen) {
			homeScreen.classList.add('d-none');
		}

		if (workspaceScreen) {
			workspaceScreen.classList.remove('d-none');
		}

		if (saveBtn) {
			saveBtn.classList.remove('d-none');
		}
	}

	function markDirty() {
		if (!state.currentProcess) return;
		state.isDirty = true;
	}

	function clearDirty() {
		state.isDirty = false;
	}

	function goBackToProcessHome() {
		if (state.currentProcess && state.isDirty) {
			const confirmed = confirm(
				'현재 작업 중인 내용이 저장되지 않았습니다.\n\n' +
				'확인을 누르면 저장하지 않고 프로세스 생성 / 리스트 화면으로 돌아갑니다.'
			);

			if (!confirmed) {
				return;
			}
		}

		closeCurrentProcessAndGoHome();
	}

	function closeCurrentProcessAndGoHome() {
		state.currentProcess = null;
		state.selected = {
			type: null,
			stepKey: null,
			unitKey: null
		};
		clearDirty();

		destroySortables();

		const stepArea = byId('process-maker-step-area');
		if (stepArea) {
			stepArea.innerHTML = '';
		}

		const arrowLayer = byId('process-maker-arrow-layer');
		if (arrowLayer) {
			arrowLayer.innerHTML = '';
		}

		hideAllEditorSections();
		byId('process-maker-editor-empty').classList.remove('d-none');

		showHomeScreen();
		renderProcessList();
	}

	function renderAll() {
		renderProcessList();
		renderCurrentTitle();
		renderCanvas();
		renderEditor();
	}

	function renderProcessList() {
		const listEl = byId('process-maker-list');
		const countEl = byId('process-maker-list-count');

		countEl.textContent = String(state.processes.length);

		if (state.processes.length === 0) {
			listEl.innerHTML = '<div class="p-3 text-muted small">등록된 프로세스가 없습니다.</div>';
			return;
		}

		listEl.innerHTML = state.processes.map(function(p) {
			const active = state.currentProcess && state.currentProcess.id === p.id ? 'active' : '';
			return `
				<div class="process-maker-process-item ${active}" data-process-id="${escapeAttr(p.id)}">
					<div class="d-flex justify-content-between align-items-center">
						<div class="process-maker-process-name">${escapeHtml(p.name)}</div>
						<span class="badge ${getStatusBadgeClass(p.status)}">${escapeHtml(p.status || 'DRAFT')}</span>
					</div>
					<div class="process-maker-process-desc">${escapeHtml(p.description || '-')}</div>
					<div class="process-maker-process-desc">생성: ${formatDateTime(p.createdAt)}</div>
				</div>
			`;
		}).join('');

		listEl.querySelectorAll('.process-maker-process-item').forEach(function(el) {
			el.addEventListener('click', function() {
				openProcess(Number(el.dataset.processId));
			});
		});
	}

	function renderCurrentTitle() {
		const titleEl = byId('process-maker-current-title');
		const metaEl = byId('process-maker-current-meta');

		if (!state.currentProcess) {
			titleEl.textContent = '프로세스를 선택해주세요';
			metaEl.textContent = 'STEP / UNIT을 구성합니다.';
			return;
		}

		const stepCount = state.currentProcess.steps.length;
		const unitCount = state.currentProcess.steps.reduce(function(sum, step) {
			return sum + step.units.length;
		}, 0);

		titleEl.textContent = state.currentProcess.name || '이름 없는 프로세스';
		metaEl.textContent = `${state.currentProcess.status || 'DRAFT'} · STEP ${stepCount}개 · UNIT ${unitCount}개`;
	}

	function renderCanvas() {
		const area = byId('process-maker-step-area');
		destroySortables();

		if (!state.currentProcess) {
			area.innerHTML = '<div class="text-muted p-4">왼쪽에서 프로세스를 생성하거나 선택해주세요.</div>';
			drawArrows();
			return;
		}

		if (!state.currentProcess.steps || state.currentProcess.steps.length === 0) {
			area.innerHTML = `
			<div class="text-center text-muted p-5">
				<div class="mb-2">아직 STEP이 없습니다.</div>
				<button type="button" class="btn btn-primary btn-sm" id="process-maker-empty-add-step-btn">첫 STEP 추가</button>
			</div>
		`;

			byId('process-maker-empty-add-step-btn').addEventListener('click', addStep);
			drawArrows();
			return;
		}

		area.innerHTML = state.currentProcess.steps.map(function(step, stepIndex) {
			const selected = state.selected.type === 'STEP' && state.selected.stepKey === step.stepKey ? 'selected' : '';

			const unitsHtml = (step.units || []).map(function(unit) {
				const unitSelected = state.selected.type === 'UNIT' && state.selected.unitKey === unit.unitKey ? 'selected' : '';
				const branchTreeKeys = getSelectedBranchTreeUnitKeys();
				const branchTreeRelated = branchTreeKeys.has(unit.unitKey) ? 'branch-tree-related' : '';
				const question = ensureQuestion(unit);
				const badges = buildUnitBadges(unit);

				return `
				<div class="process-maker-unit ${unitSelected} ${branchTreeRelated}" data-unit-key="${escapeAttr(unit.unitKey)}">
					<div class="d-flex justify-content-between align-items-start gap-2">
						<div class="process-maker-unit-title">${escapeHtml(unit.title)}</div>
						<span class="badge bg-light text-muted border" title="UNIT 순서는 고정입니다. 메인 질문 변경은 UNIT 내용을 수정해서 처리해주세요.">
							순서고정
						</span>
					</div>
					<div class="process-maker-question-preview">${escapeHtml(question.questionText || '질문을 입력해주세요.')}</div>
					<div class="process-maker-badge-row">${badges}</div>
				</div>
			`;
			}).join('');

			return `
			<div class="process-maker-step ${selected}" data-step-key="${escapeAttr(step.stepKey)}">
				<div class="process-maker-step-header">
					<div>
						<div class="process-maker-step-title">
							<span class="process-maker-handle me-1" title="STEP 순서 변경">☰</span>
							${stepIndex + 1}. ${escapeHtml(step.title)}
						</div>
						<div class="process-maker-step-key">${escapeHtml(step.stepKey)}</div>
					</div>
					<div class="d-flex gap-2">
						<button type="button" class="btn btn-sm btn-outline-success process-maker-add-unit-btn"
							data-step-key="${escapeAttr(step.stepKey)}">UNIT 추가</button>
					</div>
				</div>
				<div class="process-maker-unit-area" data-step-key="${escapeAttr(step.stepKey)}">
					${unitsHtml}
				</div>
			</div>
		`;
		}).join('');

		area.querySelectorAll('.process-maker-step').forEach(function(el) {
			el.addEventListener('click', function(event) {
				if (event.target.closest('.process-maker-add-unit-btn')) return;
				if (event.target.closest('.process-maker-unit')) return;

				state.selected = {
					type: 'STEP',
					stepKey: el.dataset.stepKey,
					unitKey: null
				};

				renderCanvas();
				renderEditor();
			});
		});

		area.querySelectorAll('.process-maker-unit').forEach(function(el) {
			el.addEventListener('click', function(event) {
				event.stopPropagation();

				const stepEl = el.closest('.process-maker-step');

				state.selected = {
					type: 'UNIT',
					stepKey: stepEl.dataset.stepKey,
					unitKey: el.dataset.unitKey
				};

				renderCanvas();
				renderEditor();
			});
		});

		area.querySelectorAll('.process-maker-add-unit-btn').forEach(function(btn) {
			btn.addEventListener('click', function(event) {
				event.stopPropagation();
				addUnit(btn.dataset.stepKey);
			});
		});

		initSortables();

		setTimeout(drawArrows, 30);
	}

	function renderEditor() {
		hideAllEditorSections();

		if (!state.currentProcess) {
			byId('process-maker-editor-empty').classList.remove('d-none');
			return;
		}

		if (state.selected.type === 'PROCESS') {
			byId('process-maker-editor-process').classList.remove('d-none');
			byId('process-maker-process-name').value = state.currentProcess.name || '';
			byId('process-maker-process-description').value = state.currentProcess.description || '';
			byId('process-maker-process-status').value = state.currentProcess.status || 'DRAFT';
			return;
		}

		if (state.selected.type === 'STEP') {
			const step = getSelectedStep();
			if (!step) return;

			byId('process-maker-editor-step').classList.remove('d-none');
			byId('process-maker-step-title').value = step.title || '';
			byId('process-maker-step-description').value = step.description || '';
			return;
		}

		if (state.selected.type === 'UNIT') {
			const unit = getSelectedUnit();
			if (!unit) return;

			const question = ensureQuestion(unit);

			question.answerType = normalizeAnswerType(question.answerType || 'SINGLE_SELECT');

			byId('process-maker-editor-unit').classList.remove('d-none');
			byId('process-maker-unit-title').value = unit.title || '';
			byId('process-maker-question-text').value = question.questionText || '';
			byId('process-maker-answer-type').value = question.answerType;

			renderAnswerEditor(unit);
			renderBranchEditor(unit);
			renderBranchTree(unit);

			return;
		}

		byId('process-maker-editor-empty').classList.remove('d-none');
	}

	function renderAnswerEditor(unit) {
		const question = ensureQuestion(unit);
		question.answerType = normalizeAnswerType(question.answerType);

		const optionsBox = byId('process-maker-options-box');
		const fieldsBox = byId('process-maker-fields-box');

		optionsBox.classList.toggle('d-none', question.answerType !== 'SINGLE_SELECT');
		fieldsBox.classList.toggle('d-none', question.answerType === 'SINGLE_SELECT');

		renderOptionList(unit);
		renderFieldList(unit);
	}

	function renderOptionList(unit) {
		const question = ensureQuestion(unit);
		const listEl = byId('process-maker-option-list');

		if (!question.options || question.options.length === 0) {
			listEl.innerHTML = '<div class="text-muted small">선택 답변이 없습니다.</div>';
			return;
		}

		listEl.innerHTML = question.options.map(function(option, index) {
			return `
			<div class="process-maker-option-item" data-index="${index}">
				<div class="row g-2 align-items-center">
					<div class="col-5">
						<input type="text" class="form-control form-control-sm process-maker-option-label"
							value="${escapeAttr(option.label)}" placeholder="라벨">
					</div>
					<div class="col-5">
						<input type="text" class="form-control form-control-sm process-maker-option-value"
							value="${escapeAttr(option.valueText || '')}" placeholder="저장값">
					</div>
					<div class="col-2">
						<button type="button" class="btn btn-sm btn-outline-danger w-100 process-maker-remove-option-btn">삭제</button>
					</div>
				</div>
			</div>
		`;
		}).join('');

		listEl.querySelectorAll('.process-maker-option-item').forEach(function(item) {
			const index = Number(item.dataset.index);

			item.querySelector('.process-maker-option-label').addEventListener('input', function() {
				question.options[index].label = this.value;
				markDirty();

				renderCanvas();
			});

			item.querySelector('.process-maker-option-value').addEventListener('input', function() {
				question.options[index].valueText = this.value;
				markDirty();
			});

			item.querySelector('.process-maker-remove-option-btn').addEventListener('click', function() {
				const removed = question.options.splice(index, 1)[0];

				if (removed && removed.optionKey) {
					removeBranchesByOption(unit, removed.optionKey);
				}

				markDirty();

				renderEditor();
				renderCanvas();
			});
		});
	}

	function renderFieldList(unit) {
		const question = ensureQuestion(unit);
		const listEl = byId('process-maker-field-list');
		const answerType = normalizeAnswerType(question.answerType);

		if (!question.fields || question.fields.length === 0) {
			listEl.innerHTML = '<div class="text-muted small">입력 필드가 없습니다.</div>';
			return;
		}

		const typeLabel = answerType === 'TEXT_INPUT'
			? '텍스트'
			: answerType === 'NUMBER_INPUT'
				? '숫자'
				: answerType === 'FILE_UPLOAD'
					? '파일'
					: '입력';

		listEl.innerHTML = question.fields.map(function(field, index) {
			return `
            <div class="process-maker-field-item" data-index="${index}">
                <div class="row g-2">
                    <div class="col-7">
                        <label class="form-label small">필드명</label>
                        <input type="text" class="form-control form-control-sm process-maker-field-label"
                            value="${escapeAttr(field.label)}" placeholder="라벨">
                    </div>
                    <div class="col-5">
                        <label class="form-label small">타입</label>
                        <input type="text" class="form-control form-control-sm" value="${escapeAttr(typeLabel)}" disabled>
                    </div>

                    <div class="col-6">
                        <label class="form-label small">placeholder</label>
                        <input type="text" class="form-control form-control-sm process-maker-field-placeholder"
                            value="${escapeAttr(field.placeholder || '')}" placeholder="placeholder">
                    </div>

                    <div class="col-4">
                        <label class="form-label small">단위</label>
                        <input type="text" class="form-control form-control-sm process-maker-field-unit"
                            value="${escapeAttr(field.unitText || '')}" placeholder="예: mm"
                            ${answerType === 'NUMBER_INPUT' ? '' : 'disabled'}>
                    </div>

                    <div class="col-2 d-flex align-items-end">
                        <button type="button"
                            class="btn btn-sm btn-outline-danger w-100 process-maker-remove-field-btn"
                            ${question.fields.length <= 1 ? 'disabled' : ''}>
                            삭제
                        </button>
                    </div>
                </div>
            </div>
        `;
		}).join('');

		listEl.querySelectorAll('.process-maker-field-item').forEach(function(item) {
			const index = Number(item.dataset.index);

			item.querySelector('.process-maker-field-label').addEventListener('input', function() {
				question.fields[index].label = this.value;
				markDirty();

				renderBranchEditor(unit);
				renderBranchTree(unit);
				renderCanvas();
			});

			item.querySelector('.process-maker-field-placeholder').addEventListener('input', function() {
				question.fields[index].placeholder = this.value;
				markDirty();
			});

			const unitInput = item.querySelector('.process-maker-field-unit');
			if (unitInput) {
				unitInput.addEventListener('input', function() {
					question.fields[index].unitText = this.value;
					markDirty();
				});
			}

			item.querySelector('.process-maker-remove-field-btn').addEventListener('click', function() {
				if (question.fields.length <= 1) {
					alert('입력 필드는 최소 1개가 필요합니다.');
					return;
				}

				const removed = question.fields.splice(index, 1)[0];

				if (removed && removed.fieldKey) {
					removeNumberConditionsByField(unit, removed.fieldKey);
				}

				markDirty();

				renderEditor();
				renderCanvas();
			});
		});
	}

	function renderBranchEditor(unit) {
		const question = ensureQuestion(unit);
		question.answerType = normalizeAnswerType(question.answerType);

		normalizeBranchesForUnit(unit);

		const listEl = byId('process-maker-branch-list');
		const addBranchBtn = byId('process-maker-add-branch-btn');

		if (addBranchBtn) {
			const branchDisabled = question.answerType === 'TEXT_INPUT';
			addBranchBtn.disabled = branchDisabled;
			addBranchBtn.classList.toggle('disabled', branchDisabled);
		}

		if (question.answerType === 'TEXT_INPUT') {
			unit.branches = [];

			listEl.innerHTML = `
			<div class="process-maker-branch-disabled-box">
				텍스트 입력은 별도 분기를 추가할 수 없습니다.<br>
				입력값을 저장한 뒤 <strong>다음 STEP의 첫 번째 UNIT</strong>으로 진행합니다.
			</div>
		`;

			drawArrows();
			return;
		}

		if (!unit.branches || unit.branches.length === 0) {
			listEl.innerHTML = `
			<div class="text-muted small">
				분기 설정이 없습니다. 이 UNIT 완료 후 <strong>다음 STEP의 첫 번째 UNIT</strong>으로 진행합니다.
			</div>
		`;
			drawArrows();
			return;
		}

		listEl.innerHTML = unit.branches.map(function(branch, index) {
			const readonly = branch.branchType === 'DEFAULT';
			const branchTypeLabel = getBranchTypeLabel(branch.branchType);
			const autoNextTargetText = branch.targetMode === 'AUTO_NEXT'
				? `<div class="small text-muted mt-2">AUTO_NEXT 대상: ${escapeHtml(getAutoNextTargetLabel(unit))}</div>`
				: '';

			return `
			<div class="process-maker-branch-item ${readonly ? 'process-maker-branch-readonly' : ''}" data-index="${index}">
				<div class="mb-2 d-flex justify-content-between align-items-center">
					<strong class="small">분기 ${index + 1}</strong>
					<button type="button"
						class="btn btn-sm btn-outline-danger process-maker-remove-branch-btn"
						${readonly ? 'disabled' : ''}>
						삭제
					</button>
				</div>

				<div class="mb-2">
					<label class="form-label small">분기명</label>
					<input type="text" class="form-control form-control-sm process-maker-branch-label"
						value="${escapeAttr(branch.label || '')}" ${readonly ? 'disabled' : ''}>
				</div>

				<div class="row g-2">
					<div class="col-6">
						<label class="form-label small">분기 타입</label>
						<input type="text" class="form-control form-control-sm" value="${escapeAttr(branchTypeLabel)}" disabled>
					</div>
					<div class="col-6">
						<label class="form-label small">이동 방식</label>
						<select class="form-select form-select-sm process-maker-target-mode" ${readonly ? 'disabled' : ''}>
							<option value="AUTO_NEXT" ${branch.targetMode === 'AUTO_NEXT' ? 'selected' : ''}>AUTO_NEXT - 다음 STEP 1번 UNIT</option>
							<option value="JUMP_UNIT" ${branch.targetMode === 'JUMP_UNIT' ? 'selected' : ''}>JUMP_UNIT - 지정 UNIT 즉시 이동</option>
							<option value="DEFER_TO_UNIT" ${branch.targetMode === 'DEFER_TO_UNIT' ? 'selected' : ''}>DEFER_TO_UNIT - 뒤쪽 STEP UNIT 활성화</option>
						</select>
					</div>
				</div>

				${autoNextTargetText}

				<div class="process-maker-branch-choice-box mt-2 ${branch.branchType === 'CHOICE' ? '' : 'd-none'}">
					<label class="form-label small">연결할 답변</label>
					<select class="form-select form-select-sm process-maker-answer-option-key">
						${buildOptionSelectHtml(unit, branch.answerOptionKey)}
					</select>
				</div>

				<div class="process-maker-branch-condition-box mt-2 ${branch.branchType === 'CONDITION' ? '' : 'd-none'}">
					${buildConditionHtml(unit, branch)}
				</div>

				<div class="process-maker-branch-target-box mt-2 ${branch.targetMode === 'AUTO_NEXT' ? 'd-none' : ''}">
					<label class="form-label small">대상 UNIT</label>
					<select class="form-select form-select-sm process-maker-target-unit-key">
						${buildUnitSelectHtml(branch.targetUnitKey)}
					</select>
				</div>

				${readonly ? '<div class="small text-muted mt-2">위 조건에 해당하지 않는 경우 다음 STEP의 첫 번째 UNIT으로 진행됩니다.</div>' : ''}
			</div>
		`;
		}).join('');

		listEl.querySelectorAll('.process-maker-branch-item').forEach(function(item) {
			const index = Number(item.dataset.index);
			const branch = unit.branches[index];
			const readonly = branch.branchType === 'DEFAULT';

			const labelInput = item.querySelector('.process-maker-branch-label');
			if (labelInput && !readonly) {
				labelInput.addEventListener('input', function() {
					branch.label = this.value;
					markDirty();
					renderBranchTree(unit);
					drawArrows();
				});
			}

			const removeBtn = item.querySelector('.process-maker-remove-branch-btn');
			if (removeBtn && !readonly) {
				removeBtn.addEventListener('click', function() {
					unit.branches.splice(index, 1);
					markDirty();

					renderBranchEditor(unit);
					renderBranchTree(unit);
					renderCanvas();
					drawArrows();
				});
			}

			const targetModeSelect = item.querySelector('.process-maker-target-mode');
			if (targetModeSelect && !readonly) {
				targetModeSelect.addEventListener('change', function() {
					branch.targetMode = this.value;

					if (branch.targetMode === 'AUTO_NEXT') {
						branch.targetUnitKey = null;
					}

					markDirty();

					renderBranchEditor(unit);
					renderBranchTree(unit);
					renderCanvas();
					drawArrows();
				});
			}

			const optionSelect = item.querySelector('.process-maker-answer-option-key');
			if (optionSelect && !readonly) {
				optionSelect.addEventListener('change', function() {
					const nextOptionKey = this.value || null;

					if (nextOptionKey && isOptionAlreadyUsedInOtherBranch(unit, nextOptionKey, index)) {
						alert('이미 다른 분기에서 사용 중인 답변입니다. 동일 답변에는 분기를 중복 추가할 수 없습니다.');
						renderBranchEditor(unit);
						return;
					}

					branch.answerOptionKey = nextOptionKey;
					markDirty();

					renderBranchTree(unit);
					drawArrows();
				});
			}

			const targetSelect = item.querySelector('.process-maker-target-unit-key');
			if (targetSelect && !readonly) {
				targetSelect.addEventListener('change', function() {
					branch.targetUnitKey = this.value || null;
					markDirty();

					renderBranchTree(unit);
					renderCanvas();
					drawArrows();
				});
			}

			bindConditionEvents(item, unit, branch);
		});

		drawArrows();
	}

	function buildConditionHtml(unit, branch) {
		const rows = getRangeRowsFromConditionJson(unit, branch.conditionJson);

		const rowsHtml = rows.map(function(row, index) {
			return `
            <div class="process-maker-condition-range-item" data-condition-index="${index}">
                <div class="row g-2 align-items-end">
                    <div class="col-12">
                        <label class="form-label small">숫자 필드</label>
                        <select class="form-select form-select-sm process-maker-condition-field">
                            ${buildNumberFieldSelectHtml(unit, row.fieldKey)}
                        </select>
                    </div>

                    <div class="col-5">
                        <label class="form-label small">시작값</label>
                        <input type="number" class="form-control form-control-sm process-maker-condition-min"
                            value="${escapeAttr(row.minValue ?? '')}" placeholder="예: 400">
                    </div>

                    <div class="col-2">
                        <label class="form-label small">포함</label>
                        <select class="form-select form-select-sm process-maker-condition-min-inclusive">
                            <option value="true" ${row.minInclusive !== false ? 'selected' : ''}>이상</option>
                            <option value="false" ${row.minInclusive === false ? 'selected' : ''}>초과</option>
                        </select>
                    </div>

                    <div class="col-5">
                        <label class="form-label small">종료값</label>
                        <input type="number" class="form-control form-control-sm process-maker-condition-max"
                            value="${escapeAttr(row.maxValue ?? '')}" placeholder="예: 600">
                    </div>

                    <div class="col-8">
                        <label class="form-label small">종료 포함</label>
                        <select class="form-select form-select-sm process-maker-condition-max-inclusive">
                            <option value="true" ${row.maxInclusive !== false ? 'selected' : ''}>이하</option>
                            <option value="false" ${row.maxInclusive === false ? 'selected' : ''}>미만</option>
                        </select>
                    </div>

                    <div class="col-4">
                        <button type="button"
                            class="btn btn-sm btn-outline-danger w-100 process-maker-remove-condition-btn"
                            ${rows.length <= 1 ? 'disabled' : ''}>
                            삭제
                        </button>
                    </div>
                </div>
            </div>
        `;
		}).join('');

		return `
        <div class="process-maker-branch-warning">
            숫자 조건은 같은 필드 기준으로 범위가 겹치면 저장할 수 없습니다.  
            예: 400~600, 700~900처럼 사이가 비면 650 입력 시 갈 수 있는 분기가 없으므로 저장 전 오류로 안내합니다.
        </div>

        <div class="process-maker-condition-list">
            ${rowsHtml}
        </div>

        <button type="button" class="btn btn-sm btn-outline-primary w-100 process-maker-add-condition-btn">
            숫자 조건 필드 추가
        </button>
    `;
	}

	function bindConditionEvents(branchItem, unit, branch) {
		const addBtn = branchItem.querySelector('.process-maker-add-condition-btn');
		if (!addBtn) return;

		addBtn.addEventListener('click', function() {
			const rows = getRangeRowsFromConditionJson(unit, branch.conditionJson);
			const numberFields = getNumberFields(unit);

			rows.push({
				fieldKey: numberFields[0] ? numberFields[0].fieldKey : '',
				minValue: null,
				minInclusive: true,
				maxValue: null,
				maxInclusive: true
			});

			branch.conditionJson = buildRangeConditionJson(rows);

			markDirty();

			renderBranchEditor(unit);
			renderBranchTree(unit);
		});

		branchItem.querySelectorAll('.process-maker-condition-range-item').forEach(function(rowEl) {
			const rowIndex = Number(rowEl.dataset.conditionIndex);

			function updateRowValue(key, value) {
				const rows = getRangeRowsFromConditionJson(unit, branch.conditionJson);
				if (!rows[rowIndex]) return;

				rows[rowIndex][key] = value;
				branch.conditionJson = buildRangeConditionJson(rows);

				markDirty();
				renderBranchTree(unit);
			}

			rowEl.querySelector('.process-maker-condition-field').addEventListener('change', function() {
				updateRowValue('fieldKey', this.value);
			});

			rowEl.querySelector('.process-maker-condition-min').addEventListener('input', function() {
				updateRowValue('minValue', this.value === '' ? null : Number(this.value));
			});

			rowEl.querySelector('.process-maker-condition-min-inclusive').addEventListener('change', function() {
				updateRowValue('minInclusive', this.value === 'true');
			});

			rowEl.querySelector('.process-maker-condition-max').addEventListener('input', function() {
				updateRowValue('maxValue', this.value === '' ? null : Number(this.value));
			});

			rowEl.querySelector('.process-maker-condition-max-inclusive').addEventListener('change', function() {
				updateRowValue('maxInclusive', this.value === 'true');
			});

			rowEl.querySelector('.process-maker-remove-condition-btn').addEventListener('click', function() {
				const rows = getRangeRowsFromConditionJson(unit, branch.conditionJson);

				if (rows.length <= 1) {
					alert('숫자 분기는 조건을 최소 1개 이상 가져야 합니다.');
					return;
				}

				rows.splice(rowIndex, 1);
				branch.conditionJson = buildRangeConditionJson(rows);

				markDirty();

				renderBranchEditor(unit);
				renderBranchTree(unit);
			});
		});
	}

	function addStep() {
		if (!state.currentProcess) {
			alert('먼저 프로세스를 생성하거나 선택해주세요.');
			return;
		}

		const step = {
			stepKey: createKey('step'),
			title: `STEP ${state.currentProcess.steps.length + 1}`,
			description: '',
			sortOrder: state.currentProcess.steps.length,
			units: []
		};

		state.currentProcess.steps.push(step);

		state.selected = {
			type: 'STEP',
			stepKey: step.stepKey,
			unitKey: null
		};

		markDirty();

		renderCanvas();
		renderEditor();
		renderCurrentTitle();
	}

	function addUnit(stepKey) {
		const step = findStep(stepKey);
		if (!step) return;

		const unit = {
			unitKey: createKey('unit'),
			title: `UNIT ${step.units.length + 1}`,
			description: '',
			sortOrder: step.units.length,
			useYn: true,
			question: {
				questionText: '질문을 입력해주세요.',
				answerType: 'SINGLE_SELECT',
				requiredYn: true,
				helperText: '',
				options: [createAnswerOption('답변 1')],
				fields: []
			},
			branches: []
		};

		step.units.push(unit);

		state.selected = {
			type: 'UNIT',
			stepKey: step.stepKey,
			unitKey: unit.unitKey
		};

		markDirty();

		renderCanvas();
		renderEditor();
		renderCurrentTitle();
	}

	function deleteSelectedStep() {
		if (!state.currentProcess || state.selected.type !== 'STEP') return;

		const stepKey = state.selected.stepKey;

		if (!confirm('선택한 STEP을 삭제하시겠습니까? STEP 내부 UNIT과 분기도 함께 삭제됩니다.')) {
			return;
		}

		state.currentProcess.steps = state.currentProcess.steps.filter(function(step) {
			return step.stepKey !== stepKey;
		});

		removeBranchesToDeletedUnits();

		state.selected = {
			type: 'PROCESS',
			stepKey: null,
			unitKey: null
		};

		reindexAll();
		markDirty();

		renderAll();
	}

	function deleteSelectedUnit() {
		if (!state.currentProcess || state.selected.type !== 'UNIT') return;

		const step = getSelectedStep();
		const unitKey = state.selected.unitKey;

		if (!step) return;

		if (!confirm('선택한 UNIT을 삭제하시겠습니까? 연결된 분기도 함께 정리됩니다.')) {
			return;
		}

		step.units = step.units.filter(function(unit) {
			return unit.unitKey !== unitKey;
		});

		removeBranchesToDeletedUnits();

		state.selected = {
			type: 'STEP',
			stepKey: step.stepKey,
			unitKey: null
		};

		reindexAll();
		markDirty();

		renderAll();
	}

	function addAnswerOption() {
		const unit = getSelectedUnit();
		if (!unit) return;

		const question = ensureQuestion(unit);

		question.options.push(createAnswerOption(`답변 ${question.options.length + 1}`));

		markDirty();

		renderEditor();
		renderCanvas();
	}

	function addAnswerField() {
		const unit = getSelectedUnit();
		if (!unit) return;

		const question = ensureQuestion(unit);
		const answerType = normalizeAnswerType(question.answerType);

		if (answerType === 'SINGLE_SELECT') {
			alert('선택 답변 방식에서는 입력 필드를 추가할 수 없습니다.');
			return;
		}

		if (answerType === 'TEXT_INPUT') {
			question.fields.push(createAnswerField(`입력 ${question.fields.length + 1}`, 'TEXT'));
		} else if (answerType === 'NUMBER_INPUT') {
			question.fields.push(createAnswerField(`숫자 ${question.fields.length + 1}`, 'NUMBER'));
		} else if (answerType === 'FILE_UPLOAD') {
			question.fields.push(createAnswerField(`파일 ${question.fields.length + 1}`, 'FILE'));
		}

		markDirty();

		renderEditor();
		renderCanvas();
	}

	function addBranch() {
		const unit = getSelectedUnit();
		if (!unit) return;

		const question = ensureQuestion(unit);
		question.answerType = normalizeAnswerType(question.answerType);
		unit.branches = unit.branches || [];

		if (question.answerType === 'TEXT_INPUT') {
			alert('텍스트 입력은 분기를 추가할 수 없습니다.');
			return;
		}

		if (question.answerType === 'SINGLE_SELECT') {
			if (!question.options || question.options.length === 0) {
				alert('선택 답변을 먼저 추가해주세요.');
				return;
			}

			const usedOptionKeys = new Set(
				unit.branches
					.filter(function(branch) {
						return branch.branchType === 'CHOICE' && branch.answerOptionKey;
					})
					.map(function(branch) {
						return branch.answerOptionKey;
					})
			);

			const availableOption = question.options.find(function(option) {
				return !usedOptionKeys.has(option.optionKey);
			});

			if (!availableOption) {
				alert('모든 선택 답변에 이미 분기가 설정되어 있습니다.');
				return;
			}

			unit.branches.push({
				branchKey: createKey('branch'),
				label: `${availableOption.label} 선택 시`,
				branchType: 'CHOICE',
				answerOptionKey: availableOption.optionKey,
				conditionJson: null,
				targetMode: 'AUTO_NEXT',
				targetUnitKey: null,
				priority: unit.branches.length,
				useYn: true
			});
		} else if (question.answerType === 'NUMBER_INPUT') {
			const numberFields = getNumberFields(unit);
			if (numberFields.length === 0) {
				alert('숫자 필드를 먼저 추가해주세요.');
				return;
			}

			unit.branches.push({
				branchKey: createKey('branch'),
				label: `숫자 조건 ${unit.branches.length + 1}`,
				branchType: 'CONDITION',
				answerOptionKey: null,
				conditionJson: createInitialRangeConditionJson(numberFields[0].fieldKey),
				targetMode: 'AUTO_NEXT',
				targetUnitKey: null,
				priority: unit.branches.length,
				useYn: true
			});
		} else if (question.answerType === 'FILE_UPLOAD') {
			unit.branches.push({
				branchKey: createKey('branch'),
				label: `파일 등록 분기 ${unit.branches.length + 1}`,
				branchType: 'UPLOAD',
				answerOptionKey: null,
				conditionJson: null,
				targetMode: 'AUTO_NEXT',
				targetUnitKey: null,
				priority: unit.branches.length,
				useYn: true
			});
		}

		normalizeBranchesForUnit(unit);
		markDirty();

		renderBranchEditor(unit);
		renderBranchTree(unit);
		renderCanvas();
		drawArrows();
	}

	function initSortables() {
		const stepArea = byId('process-maker-step-area');

		if (!stepArea || !state.currentProcess) {
			return;
		}

		/*
		 * UNIT 수평 순서 변경은 막습니다.
		 * 이유:
		 * - UNIT index는 "해당 STEP의 기본/메인 UNIT" 의미에 직접 영향을 줍니다.
		 * - 기존 분기가 있는 상태에서 UNIT 순서를 바꾸면 도달성/화살표/실행 흐름이 쉽게 꼬입니다.
		 * - 메인 질문 변경은 UNIT 순서 변경이 아니라 기존 1번 UNIT의 질문/답변 내용을 수정해서 처리합니다.
		 */
		state.sortables.push(new Sortable(stepArea, {
			animation: 180,
			handle: '.process-maker-step-header .process-maker-handle',
			ghostClass: 'sortable-ghost',
			chosenClass: 'sortable-chosen',
			draggable: '.process-maker-step',
			onEnd: function() {
				if (!state.currentProcess || !Array.isArray(state.currentProcess.steps)) {
					renderCanvas();
					return;
				}

				const oldStepKeys = state.currentProcess.steps.map(function(step) {
					return step.stepKey;
				});

				const nextStepKeys = Array.from(stepArea.querySelectorAll('.process-maker-step')).map(function(el) {
					return el.dataset.stepKey;
				});

				if (isSameStringArray(oldStepKeys, nextStepKeys)) {
					renderCanvas();
					return;
				}

				const resetPreview = getStepReorderBranchResetPreview(oldStepKeys, nextStepKeys);

				if (resetPreview.resetCount > 0) {
					const previewLines = resetPreview.items.slice(0, 8).map(function(item) {
						return `- ${item.sourceStepTitle} / ${item.sourceUnitTitle} / ${item.branchLabel}`;
					});

					const moreText = resetPreview.resetCount > previewLines.length
						? `\n외 ${resetPreview.resetCount - previewLines.length}개`
						: '';

					const confirmed = confirm(
						'STEP 순서를 변경하면 영향받는 JUMP/DEFER 분기가 AUTO_NEXT로 초기화됩니다.\n\n'
						+ '초기화 대상:\n'
						+ previewLines.join('\n')
						+ moreText
						+ '\n\n계속 진행하시겠습니까?'
					);

					if (!confirmed) {
						renderCanvas();
						return;
					}
				}

				state.currentProcess.steps.sort(function(a, b) {
					return nextStepKeys.indexOf(a.stepKey) - nextStepKeys.indexOf(b.stepKey);
				});

				const resetResult = resetBranchesAffectedByStepReorder(oldStepKeys, nextStepKeys);

				reindexAll();
				markDirty();

				if (state.selected.type === 'UNIT' && state.selected.unitKey) {
					const selectedLocation = findStepAndUnitIndexByUnitKey(state.selected.unitKey);
					if (selectedLocation) {
						state.selected.stepKey = selectedLocation.step.stepKey;
					}
				}

				renderCanvas();
				renderEditor();
				renderCurrentTitle();

				if (resetResult.resetCount > 0) {
					alert(
						'STEP 순서 변경으로 영향받는 분기 '
						+ resetResult.resetCount
						+ '개가 AUTO_NEXT로 초기화되었습니다.\n\n'
						+ '초기화된 분기는 다시 대상 UNIT을 연결해주세요.'
					);
				}
			}
		}));
	}

	function isSameStringArray(a, b) {
		if (!Array.isArray(a) || !Array.isArray(b)) {
			return false;
		}

		if (a.length !== b.length) {
			return false;
		}

		for (let i = 0; i < a.length; i++) {
			if (a[i] !== b[i]) {
				return false;
			}
		}

		return true;
	}

	function buildUnitLocationMapByStepOrder(stepKeys) {
		const locationMap = new Map();

		if (!state.currentProcess || !Array.isArray(state.currentProcess.steps)) {
			return locationMap;
		}

		const stepMap = new Map();

		state.currentProcess.steps.forEach(function(step) {
			stepMap.set(step.stepKey, step);
		});

		(stepKeys || []).forEach(function(stepKey, stepIndex) {
			const step = stepMap.get(stepKey);

			if (!step || !Array.isArray(step.units)) {
				return;
			}

			step.units.forEach(function(unit, unitIndex) {
				locationMap.set(unit.unitKey, {
					stepKey: step.stepKey,
					stepTitle: step.title || step.stepKey,
					unitKey: unit.unitKey,
					unitTitle: unit.title || unit.unitKey,
					stepIndex: stepIndex,
					unitIndex: unitIndex,
					step: step,
					unit: unit
				});
			});
		});

		return locationMap;
	}

	function shouldResetBranchByStepReorder(branch, sourceLocation, targetLocation, oldStepKeys, nextStepKeys) {
		if (!branch || !sourceLocation) {
			return false;
		}

		const targetMode = branch.targetMode || 'AUTO_NEXT';

		// AUTO_NEXT는 STEP 순서 변경 후에도 "현재 순서 기준 다음 STEP 1번 UNIT"으로 계산되므로 초기화 대상 아님
		if (targetMode === 'AUTO_NEXT') {
			return false;
		}

		// 명시 대상이 없으면 깨진 분기이므로 초기화
		if (!branch.targetUnitKey || !targetLocation) {
			return true;
		}

		const oldSourceIndex = oldStepKeys.indexOf(sourceLocation.stepKey);
		const oldTargetIndex = oldStepKeys.indexOf(targetLocation.stepKey);

		const nextSourceIndex = nextStepKeys.indexOf(sourceLocation.stepKey);
		const nextTargetIndex = nextStepKeys.indexOf(targetLocation.stepKey);

		if (oldSourceIndex < 0 || oldTargetIndex < 0 || nextSourceIndex < 0 || nextTargetIndex < 0) {
			return true;
		}

		// 변경 후 target이 source보다 앞이거나 같은 STEP이면 역방향/순환 위험
		if (nextTargetIndex <= nextSourceIndex) {
			return true;
		}

		const oldPath = getStepPathBetween(oldStepKeys, sourceLocation.stepKey, targetLocation.stepKey);
		const nextPath = getStepPathBetween(nextStepKeys, sourceLocation.stepKey, targetLocation.stepKey);

		return !isSameStringArray(oldPath, nextPath);
	}

	function getStepPathBetween(stepKeys, sourceStepKey, targetStepKey) {
		const sourceIndex = stepKeys.indexOf(sourceStepKey);
		const targetIndex = stepKeys.indexOf(targetStepKey);

		if (sourceIndex < 0 || targetIndex < 0) {
			return [];
		}

		if (targetIndex <= sourceIndex) {
			return [];
		}

		return stepKeys.slice(sourceIndex, targetIndex + 1);
	}

	function getStepReorderBranchResetPreview(oldStepKeys, nextStepKeys) {
		const nextLocationMap = buildUnitLocationMapByStepOrder(nextStepKeys);

		const items = [];

		if (!state.currentProcess || !Array.isArray(state.currentProcess.steps)) {
			return {
				resetCount: 0,
				items: items
			};
		}

		state.currentProcess.steps.forEach(function(step) {
			(step.units || []).forEach(function(unit) {
				const sourceLocation = nextLocationMap.get(unit.unitKey);

				(unit.branches || []).forEach(function(branch) {
					const targetLocation = branch && branch.targetUnitKey
						? nextLocationMap.get(branch.targetUnitKey)
						: null;

					if (!shouldResetBranchByStepReorder(
						branch,
						sourceLocation,
						targetLocation,
						oldStepKeys,
						nextStepKeys
					)) {
						return;
					}

					items.push({
						sourceStepTitle: sourceLocation ? sourceLocation.stepTitle : step.title || step.stepKey,
						sourceUnitTitle: sourceLocation ? sourceLocation.unitTitle : unit.title || unit.unitKey,
						branchLabel: branch.label || branch.branchKey || '분기',
						targetMode: branch.targetMode || 'AUTO_NEXT',
						targetUnitKey: branch.targetUnitKey
					});
				});
			});
		});

		return {
			resetCount: items.length,
			items: items
		};
	}

	function resetBranchesAffectedByStepReorder(oldStepKeys, nextStepKeys) {
		const nextLocationMap = buildUnitLocationMapByStepOrder(nextStepKeys);

		let resetCount = 0;
		const resetItems = [];

		if (!state.currentProcess || !Array.isArray(state.currentProcess.steps)) {
			return {
				resetCount: resetCount,
				items: resetItems
			};
		}

		state.currentProcess.steps.forEach(function(step) {
			(step.units || []).forEach(function(unit) {
				const sourceLocation = nextLocationMap.get(unit.unitKey);

				(unit.branches || []).forEach(function(branch) {
					const targetLocation = branch && branch.targetUnitKey
						? nextLocationMap.get(branch.targetUnitKey)
						: null;

					if (!shouldResetBranchByStepReorder(
						branch,
						sourceLocation,
						targetLocation,
						oldStepKeys,
						nextStepKeys
					)) {
						return;
					}

					resetItems.push({
						sourceStepTitle: sourceLocation ? sourceLocation.stepTitle : step.title || step.stepKey,
						sourceUnitTitle: sourceLocation ? sourceLocation.unitTitle : unit.title || unit.unitKey,
						branchLabel: branch.label || branch.branchKey || '분기',
						beforeTargetMode: branch.targetMode || 'AUTO_NEXT',
						beforeTargetUnitKey: branch.targetUnitKey || null
					});

					branch.targetMode = 'AUTO_NEXT';
					branch.targetUnitKey = null;
					resetCount++;
				});
			});
		});

		return {
			resetCount: resetCount,
			items: resetItems
		};
	}

	function destroySortables() {
		state.sortables.forEach(function(sortable) {
			try {
				sortable.destroy();
			} catch (e) {
				console.warn(e);
			}
		});
		state.sortables = [];
	}

	function drawArrows() {
		const svg = byId('process-maker-arrow-layer');
		const viewport = byId('process-maker-canvas-viewport');

		if (!svg || !viewport || !state.currentProcess) {
			if (svg) svg.innerHTML = '';
			return;
		}

		const viewportRect = viewport.getBoundingClientRect();
		const visibleGraph = getVisibleExecutionGraph();
		const relatedUnitKeys = getSelectedBranchTreeUnitKeys();
		const hasActiveSelection = relatedUnitKeys.size > 0;

		const unitElements = new Map();
		document.querySelectorAll('.process-maker-unit').forEach(function(el) {
			unitElements.set(el.dataset.unitKey, el);
		});

		const lines = [];

		visibleGraph.edges.forEach(function(edge) {
			const sourceEl = unitElements.get(edge.sourceUnitKey);
			const targetEl = unitElements.get(edge.targetUnitKey);

			if (!sourceEl || !targetEl) {
				return;
			}

			const sourceRect = sourceEl.getBoundingClientRect();
			const targetRect = targetEl.getBoundingClientRect();

			const anchor = getArrowAnchor(sourceRect, targetRect, viewportRect);
			const pathD = buildDynamicArrowPath(anchor);

			const isActiveLine = hasActiveSelection
				&& relatedUnitKeys.has(edge.sourceUnitKey)
				&& relatedUnitKeys.has(edge.targetUnitKey);

			const classNames = ['process-maker-arrow-line'];

			if (edge.targetMode === 'DEFER_TO_UNIT') {
				classNames.push('defer');
			}

			if (edge.targetMode === 'AUTO_NEXT' || edge.implicitYn === true || edge.fallbackYn === true) {
				classNames.push('auto-next');
			}

			if (isActiveLine) {
				classNames.push('active');
			}

			let markerId = 'process-maker-arrow-head';

			if (edge.targetMode === 'DEFER_TO_UNIT') {
				markerId = isActiveLine
					? 'process-maker-arrow-head-defer-active'
					: 'process-maker-arrow-head-defer';
			} else if (edge.targetMode === 'AUTO_NEXT' || edge.implicitYn === true || edge.fallbackYn === true) {
				markerId = isActiveLine
					? 'process-maker-arrow-head-auto-next-active'
					: 'process-maker-arrow-head-auto-next';
			} else if (isActiveLine) {
				markerId = 'process-maker-arrow-head-active';
			}

			lines.push(`
			<path class="${classNames.join(' ')}" marker-end="url(#${markerId})"
				d="${pathD}"></path>
		`);
		});

		const viewBoxWidth = Math.max(
			viewport.scrollWidth,
			Math.ceil(viewportRect.width),
			1
		);

		const viewBoxHeight = Math.max(
			viewport.scrollHeight,
			Math.ceil(viewportRect.height),
			1
		);

		svg.setAttribute('viewBox', `0 0 ${viewBoxWidth} ${viewBoxHeight}`);
		svg.setAttribute('width', viewBoxWidth);
		svg.setAttribute('height', viewBoxHeight);

		svg.innerHTML = `
		<defs>
			<marker id="process-maker-arrow-head" markerWidth="10" markerHeight="10" refX="8" refY="3"
				orient="auto" markerUnits="strokeWidth">
				<path d="M0,0 L0,6 L9,3 z" fill="#0d6efd" opacity="0.65"></path>
			</marker>

			<marker id="process-maker-arrow-head-active" markerWidth="10" markerHeight="10" refX="8" refY="3"
				orient="auto" markerUnits="strokeWidth">
				<path d="M0,0 L0,6 L9,3 z" fill="#7c3aed" opacity="0.85"></path>
			</marker>

			<marker id="process-maker-arrow-head-auto-next" markerWidth="10" markerHeight="10" refX="8" refY="3"
				orient="auto" markerUnits="strokeWidth">
				<path d="M0,0 L0,6 L9,3 z" fill="#6f42c1" opacity="0.65"></path>
			</marker>

			<marker id="process-maker-arrow-head-auto-next-active" markerWidth="10" markerHeight="10" refX="8" refY="3"
				orient="auto" markerUnits="strokeWidth">
				<path d="M0,0 L0,6 L9,3 z" fill="#7c3aed" opacity="0.9"></path>
			</marker>

			<marker id="process-maker-arrow-head-defer" markerWidth="10" markerHeight="10" refX="8" refY="3"
				orient="auto" markerUnits="strokeWidth">
				<path d="M0,0 L0,6 L9,3 z" fill="#fd7e14" opacity="0.7"></path>
			</marker>

			<marker id="process-maker-arrow-head-defer-active" markerWidth="10" markerHeight="10" refX="8" refY="3"
				orient="auto" markerUnits="strokeWidth">
				<path d="M0,0 L0,6 L9,3 z" fill="#fd7e14" opacity="0.9"></path>
			</marker>
		</defs>
		${lines.join('')}
	`;
	}

	function buildUnitBadges(unit) {
		const question = ensureQuestion(unit);
		const badges = [];

		badges.push(`<span class="process-maker-mini-badge">${escapeHtml(question.answerType || 'SINGLE_SELECT')}</span>`);

		if (question.answerType === 'SINGLE_SELECT') {
			badges.push(`<span class="process-maker-mini-badge">답변 ${question.options.length}</span>`);
		} else {
			badges.push(`<span class="process-maker-mini-badge">필드 ${question.fields.length}</span>`);
		}

		if (unit.branches && unit.branches.length > 0) {
			badges.push(`<span class="process-maker-mini-badge">분기 ${unit.branches.length}</span>`);
		}

		return badges.join('');
	}

	function buildOptionSelectHtml(unit, selectedKey) {
		const question = ensureQuestion(unit);

		if (!question.options || question.options.length === 0) {
			return '<option value="">선택 답변 없음</option>';
		}

		return [
			'<option value="">선택</option>',
			...question.options.map(function(option) {
				const selected = option.optionKey === selectedKey ? 'selected' : '';
				return `<option value="${escapeAttr(option.optionKey)}" ${selected}>${escapeHtml(option.label)}</option>`;
			})
		].join('');
	}

	function buildUnitSelectHtml(selectedKey) {
		const units = getAllUnits();

		return [
			'<option value="">대상 UNIT 선택</option>',
			...units.map(function(unit) {
				const selected = unit.unitKey === selectedKey ? 'selected' : '';
				return `<option value="${escapeAttr(unit.unitKey)}" ${selected}>${escapeHtml(unit.title)} (${escapeHtml(unit.unitKey)})</option>`;
			})
		].join('');
	}

	function buildNumberFieldSelectHtml(unit, selectedKey) {
		const fields = getNumberFields(unit);

		if (fields.length === 0) {
			return '<option value="">숫자 필드 없음</option>';
		}

		return fields.map(function(field) {
			const selected = field.fieldKey === selectedKey ? 'selected' : '';
			return `<option value="${escapeAttr(field.fieldKey)}" ${selected}>${escapeHtml(field.label)}</option>`;
		}).join('');
	}

	function getNumberFields(unit) {
		const question = ensureQuestion(unit);
		return (question.fields || []).filter(function(field) {
			return field.inputValueType === 'NUMBER';
		});
	}

	function removeBranchesByOption(unit, optionKey) {
		if (!unit.branches) return;

		unit.branches = unit.branches.filter(function(branch) {
			return branch.answerOptionKey !== optionKey;
		});
	}

	function removeBranchesToDeletedUnits() {
		const validUnitKeys = new Set(getAllUnits().map(function(unit) {
			return unit.unitKey;
		}));

		getAllUnits().forEach(function(unit) {
			unit.branches = (unit.branches || []).filter(function(branch) {
				return !branch.targetUnitKey || validUnitKeys.has(branch.targetUnitKey);
			});
		});
	}

	function getAllUnits() {
		if (!state.currentProcess) return [];

		return state.currentProcess.steps.flatMap(function(step) {
			return step.units || [];
		});
	}

	function getSelectedStep() {
		if (!state.currentProcess || !state.selected.stepKey) return null;
		return findStep(state.selected.stepKey);
	}

	function getSelectedUnit() {
		if (!state.currentProcess || !state.selected.unitKey) return null;

		for (const step of state.currentProcess.steps) {
			const unit = step.units.find(function(item) {
				return item.unitKey === state.selected.unitKey;
			});
			if (unit) return unit;
		}

		return null;
	}

	function findStep(stepKey) {
		if (!state.currentProcess) return null;
		return state.currentProcess.steps.find(function(step) {
			return step.stepKey === stepKey;
		});
	}

	function ensureQuestion(unit) {
		if (!unit.question) {
			unit.question = {
				questionText: '질문을 입력해주세요.',
				answerType: 'SINGLE_SELECT',
				requiredYn: true,
				helperText: '',
				options: [],
				fields: []
			};
		}

		unit.question.options = unit.question.options || [];
		unit.question.fields = unit.question.fields || [];

		return unit.question;
	}

	function normalizeProcess(process) {
		process.steps = process.steps || [];
		process.status = process.status || 'DRAFT';
		process.useYn = process.useYn !== false;

		process.steps.forEach(function(step) {
			step.units = step.units || [];
			step.units.forEach(function(unit) {
				unit.branches = unit.branches || [];

				const question = ensureQuestion(unit);
				question.answerType = normalizeAnswerType(question.answerType);

				if (question.answerType === 'TEXT_INPUT') {
					question.options = [];
					question.fields = question.fields && question.fields.length > 0
						? question.fields
						: [createAnswerField('입력 1', 'TEXT')];

					question.fields.forEach(function(field) {
						field.inputValueType = 'TEXT';
					});

					unit.branches = [];
				}

				if (question.answerType === 'NUMBER_INPUT') {
					question.options = [];
					question.fields = question.fields && question.fields.length > 0
						? question.fields
						: [createAnswerField('넓이', 'NUMBER')];

					question.fields.forEach(function(field) {
						field.inputValueType = 'NUMBER';
					});
				}

				if (question.answerType === 'SINGLE_SELECT') {
					question.fields = [];
					question.options = question.options || [];
				}

				normalizeBranchesForUnit(unit);
			});
		});
	}

	function reindexAll() {
		if (!state.currentProcess) return;

		state.currentProcess.steps.forEach(function(step, stepIndex) {
			step.sortOrder = stepIndex;

			step.units.forEach(function(unit, unitIndex) {
				unit.sortOrder = unitIndex;

				const question = ensureQuestion(unit);

				question.options.forEach(function(option, optionIndex) {
					option.sortOrder = optionIndex;
				});

				question.fields.forEach(function(field, fieldIndex) {
					field.sortOrder = fieldIndex;
				});

				unit.branches.forEach(function(branch, branchIndex) {
					branch.priority = branchIndex;
				});
			});
		});
	}

	function hideAllEditorSections() {
		byId('process-maker-editor-empty').classList.add('d-none');
		byId('process-maker-editor-process').classList.add('d-none');
		byId('process-maker-editor-step').classList.add('d-none');
		byId('process-maker-editor-unit').classList.add('d-none');
	}

	function createAnswerOption(label) {
		return {
			optionKey: createKey('option'),
			label: label,
			valueText: label,
			sortOrder: 0
		};
	}

	function createAnswerField(label, inputValueType) {
		return {
			fieldKey: createKey('field'),
			label: label,
			inputValueType: inputValueType || 'TEXT',
			placeholder: '',
			unitText: inputValueType === 'NUMBER' ? 'mm' : '',
			requiredYn: false,
			sortOrder: 0
		};
	}

	async function apiFetch(url, options) {
		const opts = options || {};
		const headers = opts.headers || {};

		headers['Content-Type'] = 'application/json';

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

	function createKey(prefix) {
		const random = window.crypto && window.crypto.getRandomValues
			? Array.from(window.crypto.getRandomValues(new Uint8Array(6)))
				.map(function(v) { return v.toString(16).padStart(2, '0'); })
				.join('')
			: Math.random().toString(16).slice(2, 14);

		return `${prefix}_${random}`;
	}

	function getStatusBadgeClass(status) {
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

	function debounce(fn, wait) {
		let timer = null;

		return function() {
			clearTimeout(timer);
			timer = setTimeout(fn, wait);
		};
	}

	function normalizeAnswerType(answerType) {
		if (!answerType) return 'SINGLE_SELECT';

		if (answerType === 'MULTI_INPUT') {
			return 'NUMBER_INPUT';
		}

		return answerType;
	}

	function normalizeBranchesForUnit(unit) {
		const question = ensureQuestion(unit);
		const answerType = normalizeAnswerType(question.answerType);

		unit.branches = unit.branches || [];

		if (answerType === 'TEXT_INPUT') {
			unit.branches = [];
			return;
		}

		unit.branches = unit.branches.filter(function(branch) {
			if (!branch.branchKey) {
				branch.branchKey = createKey('branch');
			}

			if (!branch.targetMode) {
				branch.targetMode = 'AUTO_NEXT';
			}

			if (branch.targetMode === 'AUTO_NEXT') {
				branch.targetUnitKey = null;
			}

			if (answerType === 'SINGLE_SELECT') {
				return branch.branchType === 'CHOICE' || branch.branchType === 'DEFAULT';
			}

			if (answerType === 'NUMBER_INPUT') {
				return branch.branchType === 'CONDITION' || branch.branchType === 'DEFAULT';
			}

			if (answerType === 'FILE_UPLOAD') {
				return branch.branchType === 'UPLOAD' || branch.branchType === 'DEFAULT';
			}

			return false;
		});

		unit.branches.forEach(function(branch, index) {
			branch.priority = index;

			if (answerType === 'SINGLE_SELECT' && branch.branchType === 'CHOICE') {
				branch.conditionJson = null;
			}

			if (answerType === 'NUMBER_INPUT' && branch.branchType === 'CONDITION') {
				branch.answerOptionKey = null;

				if (!branch.conditionJson) {
					const numberFields = getNumberFields(unit);
					branch.conditionJson = createInitialRangeConditionJson(numberFields[0] ? numberFields[0].fieldKey : '');
				}
			}

			if (branch.branchType === 'DEFAULT') {
				branch.answerOptionKey = null;
				branch.conditionJson = null;
				branch.targetMode = 'AUTO_NEXT';
				branch.targetUnitKey = null;
			}
		});
	}

	function removeInvalidBranchesForAnswerType(unit) {
		normalizeBranchesForUnit(unit);
	}

	function createInitialRangeConditionJson(fieldKey) {
		return buildRangeConditionJson([
			{
				fieldKey: fieldKey || '',
				minValue: null,
				minInclusive: true,
				maxValue: null,
				maxInclusive: true
			}
		]);
	}

	function getRangeRowsFromConditionJson(unit, json) {
		const numberFields = getNumberFields(unit);
		const fallbackFieldKey = numberFields[0] ? numberFields[0].fieldKey : '';

		if (!json) {
			return [
				{
					fieldKey: fallbackFieldKey,
					minValue: null,
					minInclusive: true,
					maxValue: null,
					maxInclusive: true
				}
			];
		}

		try {
			const parsed = JSON.parse(json);

			if (Array.isArray(parsed.ranges)) {
				return parsed.ranges.map(function(row) {
					return {
						fieldKey: row.fieldKey || fallbackFieldKey,
						minValue: row.minValue ?? null,
						minInclusive: row.minInclusive !== false,
						maxValue: row.maxValue ?? null,
						maxInclusive: row.maxInclusive !== false
					};
				});
			}

			const grouped = new Map();

			if (Array.isArray(parsed.conditions)) {
				parsed.conditions.forEach(function(condition) {
					const fieldKey = condition.fieldKey || fallbackFieldKey;
					if (!grouped.has(fieldKey)) {
						grouped.set(fieldKey, {
							fieldKey: fieldKey,
							minValue: null,
							minInclusive: true,
							maxValue: null,
							maxInclusive: true
						});
					}

					const row = grouped.get(fieldKey);
					const value = condition.value ?? null;

					if (condition.operator === 'GTE') {
						row.minValue = value;
						row.minInclusive = true;
					}

					if (condition.operator === 'GT') {
						row.minValue = value;
						row.minInclusive = false;
					}

					if (condition.operator === 'LTE') {
						row.maxValue = value;
						row.maxInclusive = true;
					}

					if (condition.operator === 'LT') {
						row.maxValue = value;
						row.maxInclusive = false;
					}

					if (condition.operator === 'EQ') {
						row.minValue = value;
						row.maxValue = value;
						row.minInclusive = true;
						row.maxInclusive = true;
					}
				});
			}

			const rows = Array.from(grouped.values());
			return rows.length > 0
				? rows
				: [
					{
						fieldKey: fallbackFieldKey,
						minValue: null,
						minInclusive: true,
						maxValue: null,
						maxInclusive: true
					}
				];
		} catch (e) {
			return [
				{
					fieldKey: fallbackFieldKey,
					minValue: null,
					minInclusive: true,
					maxValue: null,
					maxInclusive: true
				}
			];
		}
	}

	function buildRangeConditionJson(rows) {
		const conditions = [];

		rows.forEach(function(row) {
			if (row.minValue !== null && row.minValue !== undefined && row.minValue !== '') {
				conditions.push({
					fieldKey: row.fieldKey,
					operator: row.minInclusive === false ? 'GT' : 'GTE',
					value: Number(row.minValue)
				});
			}

			if (row.maxValue !== null && row.maxValue !== undefined && row.maxValue !== '') {
				conditions.push({
					fieldKey: row.fieldKey,
					operator: row.maxInclusive === false ? 'LT' : 'LTE',
					value: Number(row.maxValue)
				});
			}
		});

		return JSON.stringify({
			mode: 'ALL',
			ranges: rows,
			conditions: conditions
		});
	}

	function removeNumberConditionsByField(unit, fieldKey) {
		if (!unit.branches) return;

		unit.branches.forEach(function(branch) {
			if (branch.branchType !== 'CONDITION') return;

			const rows = getRangeRowsFromConditionJson(unit, branch.conditionJson)
				.filter(function(row) {
					return row.fieldKey !== fieldKey;
				});

			branch.conditionJson = buildRangeConditionJson(rows.length > 0 ? rows : [
				{
					fieldKey: getNumberFields(unit)[0] ? getNumberFields(unit)[0].fieldKey : '',
					minValue: null,
					minInclusive: true,
					maxValue: null,
					maxInclusive: true
				}
			]);
		});
	}

	function getBranchTypeLabel(branchType) {
		if (branchType === 'CHOICE') return '선택 답변';
		if (branchType === 'CONDITION') return '숫자 조건식';
		if (branchType === 'UPLOAD') return '파일 업로드';
		if (branchType === 'DEFAULT') return '기본 진행';
		return branchType || '-';
	}

	function isOptionAlreadyUsedInOtherBranch(unit, optionKey, currentIndex) {
		return (unit.branches || []).some(function(branch, index) {
			return index !== currentIndex
				&& branch.branchType === 'CHOICE'
				&& branch.answerOptionKey === optionKey;
		});
	}

	function validateProcessForSave(process) {
		const errors = [];
		const allUnits = [];

		(process.steps || []).forEach(function(step) {
			(step.units || []).forEach(function(unit) {
				allUnits.push(unit);
			});
		});

		const validUnitKeys = new Set(allUnits.map(function(unit) {
			return unit.unitKey;
		}));

		validateAutoNextFlow(process, errors);

		allUnits.forEach(function(unit) {
			const question = ensureQuestion(unit);
			const answerType = normalizeAnswerType(question.answerType);
			const unitTitle = unit.title || unit.unitKey;

			if (answerType === 'SINGLE_SELECT') {
				validateChoiceBranches(unit, errors, validUnitKeys, unitTitle);
			}

			if (answerType === 'TEXT_INPUT') {
				if (unit.branches && unit.branches.length > 0) {
					errors.push(`${unitTitle}: 텍스트 입력은 분기를 가질 수 없습니다.`);
				}
			}

			if (answerType === 'NUMBER_INPUT') {
				validateNumberBranches(unit, errors, validUnitKeys, unitTitle);
			}

			if (answerType === 'FILE_UPLOAD') {
				validateCommonBranchTargets(unit, errors, validUnitKeys, unitTitle);
			}
		});

		return errors;
	}

	function validateAutoNextFlow(process, errors) {
		const steps = Array.isArray(process.steps) ? process.steps : [];

		steps.forEach(function(step, stepIndex) {
			const units = Array.isArray(step.units) ? step.units : [];
			const nextStep = steps[stepIndex + 1];

			units.forEach(function(unit) {
				const unitTitle = unit.title || unit.unitKey;
				const branches = Array.isArray(unit.branches) ? unit.branches : [];

				const needsAutoNext = branches.length === 0 || branches.some(function(branch) {
					return isAutoNextTargetMode(branch);
				});

				if (!needsAutoNext) {
					return;
				}

				if (!nextStep) {
					return;
				}

				const nextStepUnits = Array.isArray(nextStep.units) ? nextStep.units : [];

				if (nextStepUnits.length === 0) {
					errors.push(`${unitTitle}: AUTO_NEXT 또는 분기 없음은 다음 STEP의 첫 번째 UNIT으로 이동해야 하지만, 다음 STEP에 UNIT이 없습니다.`);
				}
			});
		});
	}
	function validateChoiceBranches(unit, errors, validUnitKeys, unitTitle) {
		const question = ensureQuestion(unit);
		const optionKeys = new Set((question.options || []).map(function(option) {
			return option.optionKey;
		}));

		const usedOptionKeys = new Set();

		(unit.branches || []).forEach(function(branch) {
			if (branch.branchType === 'DEFAULT') return;

			if (branch.branchType !== 'CHOICE') {
				errors.push(`${unitTitle}: 여러 개 중 하나 선택 답변은 선택 답변 분기만 사용할 수 있습니다.`);
				return;
			}

			if (!branch.answerOptionKey) {
				errors.push(`${unitTitle}: 선택 답변 분기에 연결할 답변이 없습니다.`);
				return;
			}

			if (!optionKeys.has(branch.answerOptionKey)) {
				errors.push(`${unitTitle}: 존재하지 않는 답변에 분기가 연결되어 있습니다.`);
				return;
			}

			if (usedOptionKeys.has(branch.answerOptionKey)) {
				errors.push(`${unitTitle}: 동일 답변에 분기가 중복 설정되어 있습니다.`);
				return;
			}

			usedOptionKeys.add(branch.answerOptionKey);
		});

		validateCommonBranchTargets(unit, errors, validUnitKeys, unitTitle);
	}

	function validateNumberBranches(unit, errors, validUnitKeys, unitTitle) {
		const numberFields = getNumberFields(unit);

		if (numberFields.length === 0) {
			errors.push(`${unitTitle}: 숫자 입력은 숫자 필드가 최소 1개 필요합니다.`);
			return;
		}

		(unit.branches || []).forEach(function(branch) {
			if (branch.branchType === 'DEFAULT') return;

			if (branch.branchType !== 'CONDITION') {
				errors.push(`${unitTitle}: 숫자 입력은 숫자 조건식 분기만 사용할 수 있습니다.`);
				return;
			}

			const rows = getRangeRowsFromConditionJson(unit, branch.conditionJson);

			if (!rows || rows.length === 0) {
				errors.push(`${unitTitle}: 숫자 분기는 조건을 최소 1개 이상 가져야 합니다.`);
				return;
			}

			rows.forEach(function(row) {
				if (!row.fieldKey) {
					errors.push(`${unitTitle}: 숫자 조건에 필드가 선택되지 않았습니다.`);
				}

				if (
					(row.minValue === null || row.minValue === undefined || row.minValue === '') &&
					(row.maxValue === null || row.maxValue === undefined || row.maxValue === '')
				) {
					errors.push(`${unitTitle}: 숫자 조건은 시작값 또는 종료값 중 하나 이상 입력해야 합니다.`);
				}

				if (
					row.minValue !== null &&
					row.minValue !== undefined &&
					row.minValue !== '' &&
					row.maxValue !== null &&
					row.maxValue !== undefined &&
					row.maxValue !== '' &&
					Number(row.minValue) > Number(row.maxValue)
				) {
					errors.push(`${unitTitle}: 숫자 조건의 시작값이 종료값보다 클 수 없습니다.`);
				}
			});
		});

		validateNumberRangeOverlapAndGap(unit, errors, unitTitle);
		validateCommonBranchTargets(unit, errors, validUnitKeys, unitTitle);
	}

	function validateNumberRangeOverlapAndGap(unit, errors, unitTitle) {
		const byField = new Map();

		(unit.branches || []).forEach(function(branch) {
			if (branch.branchType !== 'CONDITION') return;

			const rows = getRangeRowsFromConditionJson(unit, branch.conditionJson);

			rows.forEach(function(row) {
				if (!row.fieldKey) return;

				if (!byField.has(row.fieldKey)) {
					byField.set(row.fieldKey, []);
				}

				byField.get(row.fieldKey).push({
					branchLabel: branch.label || branch.branchKey,
					minValue: row.minValue === null || row.minValue === undefined || row.minValue === '' ? null : Number(row.minValue),
					minInclusive: row.minInclusive !== false,
					maxValue: row.maxValue === null || row.maxValue === undefined || row.maxValue === '' ? null : Number(row.maxValue),
					maxInclusive: row.maxInclusive !== false
				});
			});
		});

		byField.forEach(function(ranges, fieldKey) {
			if (ranges.length <= 1) return;

			ranges.sort(function(a, b) {
				if (a.minValue === null && b.minValue === null) return 0;
				if (a.minValue === null) return -1;
				if (b.minValue === null) return 1;
				return a.minValue - b.minValue;
			});

			for (let i = 0; i < ranges.length - 1; i++) {
				const current = ranges[i];
				const next = ranges[i + 1];

				if (current.maxValue === null) {
					errors.push(`${unitTitle}: 숫자 조건 범위가 겹칩니다. [${current.branchLabel}] 이후 조건은 도달할 수 없습니다.`);
					continue;
				}

				if (next.minValue === null) {
					errors.push(`${unitTitle}: 숫자 조건 범위가 겹칩니다. [${next.branchLabel}] 조건을 확인해주세요.`);
					continue;
				}

				if (current.maxValue > next.minValue) {
					errors.push(`${unitTitle}: 숫자 조건 범위가 겹칩니다. [${current.branchLabel}] / [${next.branchLabel}]`);
				}

				if (
					current.maxValue === next.minValue &&
					current.maxInclusive === true &&
					next.minInclusive === true
				) {
					errors.push(`${unitTitle}: 숫자 조건 경계값 ${current.maxValue}이 중복 포함됩니다. [${current.branchLabel}] / [${next.branchLabel}]`);
				}

				if (current.maxValue < next.minValue) {
					errors.push(`${unitTitle}: 숫자 조건 범위 사이에 빈 구간이 있습니다. ${current.maxValue} ~ ${next.minValue} 사이 입력값은 갈 수 있는 분기가 없습니다.`);
				}
			}
		});
	}

	function validateCommonBranchTargets(unit, errors, validUnitKeys, unitTitle) {
		(unit.branches || []).forEach(function(branch) {
			if (branch.targetMode && branch.targetMode !== 'AUTO_NEXT') {
				if (!branch.targetUnitKey) {
					errors.push(`${unitTitle}: ${branch.targetMode} 분기는 대상 UNIT이 필요합니다.`);
					return;
				}

				if (!validUnitKeys.has(branch.targetUnitKey)) {
					errors.push(`${unitTitle}: 분기 대상 UNIT을 찾을 수 없습니다. ${branch.targetUnitKey}`);
				}
			}
		});
	}

	function buildSavePayload(process) {
		return {
			name: process.name || '',
			description: process.description || '',
			status: process.status || 'DRAFT',
			useYn: process.useYn !== false,
			steps: (process.steps || []).map(function(step, stepIndex) {
				return {
					stepKey: step.stepKey,
					title: step.title || `STEP ${stepIndex + 1}`,
					description: step.description || '',
					sortOrder: stepIndex,
					units: (step.units || []).map(function(unit, unitIndex) {
						const question = ensureQuestion(unit);
						const normalizedAnswerType = normalizeAnswerType(question.answerType || 'SINGLE_SELECT');

						return {
							unitKey: unit.unitKey,
							title: unit.title || `UNIT ${unitIndex + 1}`,
							description: unit.description || '',
							sortOrder: unitIndex,
							useYn: unit.useYn !== false,
							question: {
								questionText: question.questionText || '질문을 입력해주세요.',
								answerType: normalizedAnswerType,
								requiredYn: question.requiredYn !== false,
								helperText: question.helperText || '',
								options: normalizedAnswerType === 'SINGLE_SELECT'
									? (question.options || []).map(function(option, optionIndex) {
										return {
											optionKey: option.optionKey,
											label: option.label || `답변 ${optionIndex + 1}`,
											valueText: option.valueText || '',
											sortOrder: optionIndex
										};
									})
									: [],
								fields: normalizedAnswerType !== 'SINGLE_SELECT'
									? (question.fields || []).map(function(field, fieldIndex) {
										return {
											fieldKey: field.fieldKey,
											label: field.label || `입력 ${fieldIndex + 1}`,
											inputValueType: field.inputValueType || getDefaultInputValueTypeByAnswerType(normalizedAnswerType),
											placeholder: field.placeholder || '',
											unitText: field.unitText || '',
											requiredYn: field.requiredYn === true,
											sortOrder: fieldIndex
										};
									})
									: []
							},
							branches: buildSaveBranches(unit, normalizedAnswerType)
						};
					})
				};
			})
		};
	}

	function buildSaveBranches(unit, answerType) {
		if (answerType === 'TEXT_INPUT') {
			return [];
		}

		const branches = unit.branches || [];

		return branches
			.filter(function(branch) {
				if (!branch || !branch.branchType) {
					return false;
				}

				if (answerType === 'SINGLE_SELECT') {
					return branch.branchType === 'CHOICE' || branch.branchType === 'DEFAULT';
				}

				if (answerType === 'NUMBER_INPUT') {
					return branch.branchType === 'CONDITION' || branch.branchType === 'DEFAULT';
				}

				if (answerType === 'FILE_UPLOAD') {
					return branch.branchType === 'UPLOAD' || branch.branchType === 'DEFAULT';
				}

				return false;
			})
			.map(function(branch, branchIndex) {
				const targetMode = branch.targetMode || 'AUTO_NEXT';

				return {
					branchKey: branch.branchKey,
					label: branch.label || `분기 ${branchIndex + 1}`,
					branchType: branch.branchType,
					answerOptionKey: branch.branchType === 'CHOICE' ? branch.answerOptionKey || null : null,
					conditionJson: branch.branchType === 'CONDITION' ? branch.conditionJson || null : null,
					targetMode: targetMode,
					targetUnitKey: targetMode === 'AUTO_NEXT' ? null : branch.targetUnitKey || null,
					priority: branchIndex,
					useYn: branch.useYn !== false
				};
			});
	}

	function getDefaultInputValueTypeByAnswerType(answerType) {
		if (answerType === 'NUMBER_INPUT') {
			return 'NUMBER';
		}

		if (answerType === 'FILE_UPLOAD') {
			return 'FILE';
		}

		return 'TEXT';
	}

	function renderBranchTree(unit) {
		const box = byId('process-maker-branch-tree-box');
		if (!box) return;

		if (!unit) {
			box.classList.add('d-none');
			box.innerHTML = '';
			return;
		}

		const branches = getRenderableBranches(unit)
			.map(function(branch) {
				const resolvedTargetUnitKey = resolveBranchTargetUnitKey(unit, branch);

				return {
					branch: branch,
					resolvedTargetUnitKey: resolvedTargetUnitKey,
					targetUnit: resolvedTargetUnitKey
						? getAllUnits().find(function(item) {
							return item.unitKey === resolvedTargetUnitKey;
						})
						: null
				};
			})
			.filter(function(row) {
				return !!row.resolvedTargetUnitKey;
			});

		if (branches.length === 0) {
			box.classList.add('d-none');
			box.innerHTML = '';
			return;
		}

		box.classList.remove('d-none');

		box.innerHTML = `
		<div class="process-maker-branch-tree-title">이 UNIT에서 이동되는 질문 트리</div>
		${branches.map(function(row) {
			const branch = row.branch;
			const targetUnit = row.targetUnit;

			const targetModeText = isAutoNextTargetMode(branch)
				? 'AUTO_NEXT · 다음 STEP 1번 UNIT'
				: branch.targetMode;

			return `
				<div class="process-maker-branch-tree-row">
					<div class="process-maker-branch-tree-node">
						<div class="process-maker-branch-tree-node-title">${escapeHtml(unit.title || unit.unitKey)}</div>
						<div class="process-maker-branch-tree-node-desc">현재 UNIT</div>
					</div>

					<div class="process-maker-branch-tree-arrow">→</div>

					<div class="process-maker-branch-tree-node">
						<div class="process-maker-branch-tree-node-title">${escapeHtml(targetUnit ? targetUnit.title : row.resolvedTargetUnitKey)}</div>
						<div class="process-maker-branch-tree-node-desc">${escapeHtml(targetModeText)}</div>
						<div class="process-maker-branch-tree-condition">${escapeHtml(describeBranchCondition(unit, branch))}</div>
					</div>
				</div>
			`;
		}).join('')}
	`;
	}

	function describeBranchCondition(unit, branch) {
		if (!branch) {
			return '다음 STEP의 첫 번째 UNIT으로 진행';
		}

		if (branch.implicitYn || branch.branchType === 'IMPLICIT_AUTO_NEXT') {
			return '분기 없음: 다음 STEP의 첫 번째 UNIT으로 진행';
		}

		if (branch.branchType === 'CHOICE') {
			const question = ensureQuestion(unit);
			const option = (question.options || []).find(function(item) {
				return item.optionKey === branch.answerOptionKey;
			});

			const conditionText = option ? `${option.label} 선택 시` : '선택 답변 조건';
			return isAutoNextTargetMode(branch)
				? `${conditionText}: 다음 STEP의 첫 번째 UNIT으로 진행`
				: conditionText;
		}

		if (branch.branchType === 'CONDITION') {
			const rows = getRangeRowsFromConditionJson(unit, branch.conditionJson);

			const conditionText = rows.map(function(row) {
				const field = getNumberFields(unit).find(function(item) {
					return item.fieldKey === row.fieldKey;
				});

				const fieldLabel = field ? field.label : row.fieldKey;
				const minText = row.minValue === null || row.minValue === undefined || row.minValue === ''
					? ''
					: `${row.minValue}${row.minInclusive === false ? ' 초과' : ' 이상'}`;
				const maxText = row.maxValue === null || row.maxValue === undefined || row.maxValue === ''
					? ''
					: `${row.maxValue}${row.maxInclusive === false ? ' 미만' : ' 이하'}`;

				return `${fieldLabel}: ${[minText, maxText].filter(Boolean).join(' ~ ')}`;
			}).join(', ');

			return isAutoNextTargetMode(branch)
				? `${conditionText}: 다음 STEP의 첫 번째 UNIT으로 진행`
				: conditionText;
		}

		if (branch.branchType === 'UPLOAD') {
			return isAutoNextTargetMode(branch)
				? '파일 업로드 시: 다음 STEP의 첫 번째 UNIT으로 진행'
				: '파일 업로드 시';
		}

		if (branch.branchType === 'DEFAULT') {
			return '기본 진행: 다음 STEP의 첫 번째 UNIT으로 진행';
		}

		return isAutoNextTargetMode(branch)
			? '다음 STEP의 첫 번째 UNIT으로 진행'
			: '지정 UNIT으로 진행';
	}

	function getSelectedBranchTreeUnitKeys() {
		const keys = new Set();

		if (!state.currentProcess || !state.selected.type) {
			return keys;
		}

		let startUnitKey = null;

		if (state.selected.type === 'UNIT' && state.selected.unitKey) {
			startUnitKey = state.selected.unitKey;
		}

		if (state.selected.type === 'STEP' && state.selected.stepKey) {
			const selectedStep = findStep(state.selected.stepKey);
			if (selectedStep && selectedStep.units && selectedStep.units.length > 0) {
				startUnitKey = selectedStep.units[0].unitKey;
			}
		}

		if (!startUnitKey) {
			return keys;
		}

		/*
		 * 중요:
		 * 시작 UNIT에서 도달 불가능한 UNIT을 클릭한 경우에는
		 * branch-tree-related / active line 표시를 하지 않습니다.
		 * 예: 1-2가 어떤 분기로도 도달되지 않는다면 1-2 → 2-1 선을 ACTIVE처럼 보여주지 않습니다.
		 */
		const visibleGraph = getVisibleExecutionGraph();

		if (!visibleGraph.reachableUnitKeys.has(startUnitKey)) {
			return keys;
		}

		keys.add(startUnitKey);

		const queue = [startUnitKey];

		while (queue.length > 0) {
			const currentUnitKey = queue.shift();

			visibleGraph.edges.forEach(function(edge) {
				if (edge.sourceUnitKey !== currentUnitKey) {
					return;
				}

				if (keys.has(edge.targetUnitKey)) {
					return;
				}

				keys.add(edge.targetUnitKey);
				queue.push(edge.targetUnitKey);
			});
		}

		return keys;
	}

	function setCanvasZoom(nextZoom) {
		state.zoom = Math.max(0.5, Math.min(1.6, Number(nextZoom.toFixed(2))));

		const viewport = byId('process-maker-canvas-viewport');
		const resetBtn = byId('process-maker-zoom-reset-btn');

		if (viewport) {
			/*
			 * transform scale을 쓰지 않습니다.
			 * STEP 폭은 항상 100%로 두고, 내부 UNIT 크기/간격만 CSS 변수로 조정합니다.
			 * 이렇게 해야 축소 시 좌우 스크롤이 생기지 않고, UNIT은 줄바꿈됩니다.
			 */
			viewport.style.setProperty('--process-maker-zoom', state.zoom);
			viewport.style.transform = '';
			viewport.style.width = '100%';
			viewport.style.minWidth = '0';
		}

		if (resetBtn) {
			resetBtn.textContent = `${Math.round(state.zoom * 100)}%`;
		}

		requestAnimationFrame(function() {
			requestAnimationFrame(drawArrows);
		});
	}

	function getVisibleExecutionGraph() {
		const edges = [];
		const reachableUnitKeys = new Set();
		const edgeKeySet = new Set();

		const startUnit = getProcessStartUnit();

		if (!startUnit) {
			return {
				edges: edges,
				reachableUnitKeys: reachableUnitKeys
			};
		}

		const queue = [startUnit.unitKey];
		reachableUnitKeys.add(startUnit.unitKey);

		while (queue.length > 0) {
			const sourceUnitKey = queue.shift();
			const sourceUnit = findUnitByKey(sourceUnitKey);

			if (!sourceUnit) {
				continue;
			}

			const unitEdges = buildExecutionEdgesForUnit(sourceUnit);

			unitEdges.forEach(function(edge) {
				if (!edge.targetUnitKey) {
					return;
				}

				const edgeKey = [
					edge.sourceUnitKey,
					edge.targetUnitKey,
					edge.targetMode || 'AUTO_NEXT',
					edge.branchKey || '',
					edge.fallbackYn === true ? 'fallback' : ''
				].join('__');

				if (edgeKeySet.has(edgeKey)) {
					return;
				}

				edgeKeySet.add(edgeKey);
				edges.push(edge);

				if (!reachableUnitKeys.has(edge.targetUnitKey)) {
					reachableUnitKeys.add(edge.targetUnitKey);
					queue.push(edge.targetUnitKey);
				}
			});
		}

		return {
			edges: edges,
			reachableUnitKeys: reachableUnitKeys
		};
	}

	function buildExecutionEdgesForUnit(sourceUnit) {
		if (!sourceUnit) {
			return [];
		}

		const edges = [];
		const branches = Array.isArray(sourceUnit.branches) ? sourceUnit.branches : [];
		const normalNextUnit = findNextStepFirstUnitBySourceUnitKey(sourceUnit.unitKey);
		const normalNextUnitKey = normalNextUnit ? normalNextUnit.unitKey : null;

		if (branches.length === 0) {
			if (normalNextUnitKey) {
				edges.push({
					sourceUnitKey: sourceUnit.unitKey,
					targetUnitKey: normalNextUnitKey,
					targetMode: 'AUTO_NEXT',
					branchType: 'IMPLICIT_AUTO_NEXT',
					branchKey: `implicit_auto_next_${sourceUnit.unitKey}`,
					implicitYn: true,
					fallbackYn: false
				});
			}

			return edges;
		}

		let hasDefault = false;

		branches.forEach(function(branch) {
			if (!branch) {
				return;
			}

			const targetMode = branch.targetMode || 'AUTO_NEXT';

			if (branch.branchType === 'DEFAULT') {
				hasDefault = true;
			}

			if (targetMode === 'JUMP_UNIT') {
				if (branch.targetUnitKey) {
					edges.push({
						sourceUnitKey: sourceUnit.unitKey,
						targetUnitKey: branch.targetUnitKey,
						targetMode: 'JUMP_UNIT',
						branchType: branch.branchType,
						branchKey: branch.branchKey,
						implicitYn: false,
						fallbackYn: false
					});
				}
				return;
			}

			if (targetMode === 'DEFER_TO_UNIT') {
				/*
				 * DEFER는 현재 진행 흐름도 다음 STEP 1번으로 이어지고,
				 * 예약 대상 UNIT도 별도 표시합니다.
				 */
				if (normalNextUnitKey) {
					edges.push({
						sourceUnitKey: sourceUnit.unitKey,
						targetUnitKey: normalNextUnitKey,
						targetMode: 'AUTO_NEXT',
						branchType: 'DEFER_CONTINUE',
						branchKey: `${branch.branchKey || createKey('branch')}_continue`,
						implicitYn: true,
						fallbackYn: false
					});
				}

				if (branch.targetUnitKey) {
					edges.push({
						sourceUnitKey: sourceUnit.unitKey,
						targetUnitKey: branch.targetUnitKey,
						targetMode: 'DEFER_TO_UNIT',
						branchType: branch.branchType,
						branchKey: branch.branchKey,
						implicitYn: false,
						fallbackYn: false
					});
				}

				return;
			}

			if (normalNextUnitKey) {
				edges.push({
					sourceUnitKey: sourceUnit.unitKey,
					targetUnitKey: normalNextUnitKey,
					targetMode: 'AUTO_NEXT',
					branchType: branch.branchType,
					branchKey: branch.branchKey,
					implicitYn: false,
					fallbackYn: branch.branchType === 'DEFAULT'
				});
			}
		});

		/*
		 * DEFAULT가 없으면 조건/선택에 매칭되지 않는 경우도
		 * 다음 STEP의 첫 번째 UNIT으로 fallback 됩니다.
		 */
		if (!hasDefault && normalNextUnitKey) {
			edges.push({
				sourceUnitKey: sourceUnit.unitKey,
				targetUnitKey: normalNextUnitKey,
				targetMode: 'AUTO_NEXT',
				branchType: 'IMPLICIT_FALLBACK_AUTO_NEXT',
				branchKey: `implicit_fallback_auto_next_${sourceUnit.unitKey}`,
				implicitYn: true,
				fallbackYn: true
			});
		}

		return edges;
	}

	function getProcessStartUnit() {
		if (!state.currentProcess || !Array.isArray(state.currentProcess.steps)) {
			return null;
		}

		for (const step of state.currentProcess.steps) {
			if (Array.isArray(step.units) && step.units.length > 0) {
				return step.units[0];
			}
		}

		return null;
	}

	function findUnitByKey(unitKey) {
		if (!unitKey || !state.currentProcess || !Array.isArray(state.currentProcess.steps)) {
			return null;
		}

		for (const step of state.currentProcess.steps) {
			const units = Array.isArray(step.units) ? step.units : [];

			for (const unit of units) {
				if (unit.unitKey === unitKey) {
					return unit;
				}
			}
		}

		return null;
	}

	function getArrowAnchor(sourceRect, targetRect, viewportRect) {
		const sourceCenterX = sourceRect.left + sourceRect.width / 2;
		const sourceCenterY = sourceRect.top + sourceRect.height / 2;
		const targetCenterX = targetRect.left + targetRect.width / 2;
		const targetCenterY = targetRect.top + targetRect.height / 2;

		const verticalGap = 12;
		const horizontalGap = 12;

		if (targetRect.top >= sourceRect.bottom - verticalGap) {
			return {
				type: 'vertical-down',
				x1: sourceCenterX - viewportRect.left,
				y1: sourceRect.bottom - viewportRect.top,
				x2: targetCenterX - viewportRect.left,
				y2: targetRect.top - viewportRect.top
			};
		}

		if (sourceRect.top >= targetRect.bottom - verticalGap) {
			return {
				type: 'vertical-up',
				x1: sourceCenterX - viewportRect.left,
				y1: sourceRect.top - viewportRect.top,
				x2: targetCenterX - viewportRect.left,
				y2: targetRect.bottom - viewportRect.top
			};
		}

		if (targetRect.left >= sourceRect.right - horizontalGap) {
			return {
				type: 'horizontal-right',
				x1: sourceRect.right - viewportRect.left,
				y1: sourceCenterY - viewportRect.top,
				x2: targetRect.left - viewportRect.left,
				y2: targetCenterY - viewportRect.top
			};
		}

		if (sourceRect.left >= targetRect.right - horizontalGap) {
			return {
				type: 'horizontal-left',
				x1: sourceRect.left - viewportRect.left,
				y1: sourceCenterY - viewportRect.top,
				x2: targetRect.right - viewportRect.left,
				y2: targetCenterY - viewportRect.top
			};
		}

		return {
			type: 'overlap',
			x1: sourceRect.right - viewportRect.left,
			y1: sourceCenterY - viewportRect.top,
			x2: targetRect.right - viewportRect.left - 2,
			y2: targetCenterY - viewportRect.top
		};
	}

	function buildDynamicArrowPath(anchor) {
		const x1 = anchor.x1;
		const y1 = anchor.y1;
		const x2 = anchor.x2;
		const y2 = anchor.y2;

		if (anchor.type === 'vertical-down' || anchor.type === 'vertical-up') {
			const midY = y1 + (y2 - y1) / 2;
			return `M ${x1} ${y1} C ${x1} ${midY}, ${x2} ${midY}, ${x2} ${y2}`;
		}

		if (anchor.type === 'horizontal-left') {
			const midX = x1 - Math.max(60, Math.abs(x1 - x2) / 2);
			return `M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`;
		}

		const midX = x1 + Math.max(60, Math.abs(x2 - x1) / 2);
		return `M ${x1} ${y1} C ${midX} ${y1}, ${midX} ${y2}, ${x2} ${y2}`;
	}

	function findStepAndUnitIndexByUnitKey(unitKey) {
		if (!state.currentProcess || !Array.isArray(state.currentProcess.steps) || !unitKey) {
			return null;
		}

		for (let stepIndex = 0; stepIndex < state.currentProcess.steps.length; stepIndex++) {
			const step = state.currentProcess.steps[stepIndex];
			const units = Array.isArray(step.units) ? step.units : [];

			for (let unitIndex = 0; unitIndex < units.length; unitIndex++) {
				if (units[unitIndex].unitKey === unitKey) {
					return {
						stepIndex: stepIndex,
						unitIndex: unitIndex,
						step: step,
						unit: units[unitIndex]
					};
				}
			}
		}

		return null;
	}

	function findNextStepFirstUnitBySourceUnitKey(sourceUnitKey) {
		const location = findStepAndUnitIndexByUnitKey(sourceUnitKey);

		if (!location || !state.currentProcess || !Array.isArray(state.currentProcess.steps)) {
			return null;
		}

		const nextStep = state.currentProcess.steps[location.stepIndex + 1];

		if (!nextStep) {
			return null;
		}

		const nextStepUnits = Array.isArray(nextStep.units) ? nextStep.units : [];

		if (nextStepUnits.length === 0) {
			return null;
		}

		return nextStepUnits[0];
	}

	function hasNextStep(sourceUnitKey) {
		const location = findStepAndUnitIndexByUnitKey(sourceUnitKey);

		if (!location || !state.currentProcess || !Array.isArray(state.currentProcess.steps)) {
			return false;
		}

		return !!state.currentProcess.steps[location.stepIndex + 1];
	}

	function isAutoNextTargetMode(branch) {
		return !branch || !branch.targetMode || branch.targetMode === 'AUTO_NEXT';
	}

	function resolveBranchTargetUnitKey(sourceUnit, branch) {
		if (!sourceUnit) {
			return null;
		}

		if (isAutoNextTargetMode(branch)) {
			const nextStepFirstUnit = findNextStepFirstUnitBySourceUnitKey(sourceUnit.unitKey);
			return nextStepFirstUnit ? nextStepFirstUnit.unitKey : null;
		}

		return branch && branch.targetUnitKey ? branch.targetUnitKey : null;
	}

	function createImplicitAutoNextBranch(unit) {
		return {
			branchKey: `implicit_auto_next_${unit.unitKey}`,
			label: '분기 없음',
			branchType: 'IMPLICIT_AUTO_NEXT',
			answerOptionKey: null,
			conditionJson: null,
			targetMode: 'AUTO_NEXT',
			targetUnitKey: null,
			priority: 0,
			useYn: true,
			implicitYn: true
		};
	}

	function getRenderableBranches(unit) {
		if (!unit) {
			return [];
		}

		const branches = Array.isArray(unit.branches) ? unit.branches : [];

		if (branches.length === 0) {
			return [createImplicitAutoNextBranch(unit)];
		}

		return branches;
	}

	function getAutoNextTargetLabel(sourceUnit) {
		const nextStepFirstUnit = findNextStepFirstUnitBySourceUnitKey(sourceUnit.unitKey);

		if (!nextStepFirstUnit) {
			if (hasNextStep(sourceUnit.unitKey)) {
				return '다음 STEP에 UNIT이 없습니다.';
			}

			return '다음 STEP 없음 - 프로세스 완료';
		}

		return `${nextStepFirstUnit.title || nextStepFirstUnit.unitKey} (다음 STEP 1번 UNIT)`;
	}
})();