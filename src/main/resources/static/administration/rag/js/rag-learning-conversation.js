let learningConversationSessionId = null;
let learningSelectedFiles = [];
let learningActiveJobTimer = null;
let learningActiveJobId = null;

const LEARNING_CONVERSATION_API = '/admin/rag/api/learning-conversation';
const RAG_LEARNING_JS_VERSION = '20260623-gpt-sql-agent-v1';

const LEARNING_JOB_DONE = new Set(['COMPLETED', 'FAILED', 'CANCELED']);
const LEARNING_JOB_POLL_INTERVAL_MS = 2500;
const LEARNING_MAX_RENDERED_JOB_LOGS = 80;
const LEARNING_MAX_PRETTY_STRING = 3000;
const LEARNING_MAX_PRETTY_LIST = 40;
const LEARNING_MAX_PRETTY_DEPTH = 5;
let learningLastJobRenderSignature = '';
let learningLastJobLogSignature = '';


function learningJobApiBase() {
    return `${LEARNING_CONVERSATION_API}/${learningConversationSessionId}/jobs`;
}

document.addEventListener('DOMContentLoaded', () => {
    console.info('[RAG_LEARNING_CONVERSATION_JS] loaded', RAG_LEARNING_JS_VERSION);
    const badge = document.getElementById('learningJsVersionBadge');
    if (badge) badge.textContent = RAG_LEARNING_JS_VERSION;
    bindLearningConversationEvents();
});

function bindLearningConversationEvents() {
    document.getElementById('learningConversationStartBtn')?.addEventListener('click', startLearningConversation);
    document.getElementById('learningConversationForm')?.addEventListener('submit', sendLearningConversationMessage);
    document.getElementById('learningConversationFile')?.addEventListener('change', (e) => addLearningFiles(e.target.files));
    document.getElementById('clearLearningFilesBtn')?.addEventListener('click', clearLearningFiles);
    document.getElementById('resetKnowledgeBtn')?.addEventListener('click', resetLearningKnowledge);
    document.getElementById('retryRawKnowledgeNodesBtn')?.addEventListener('click', retryRawKnowledgeNodes);

    const dropZone = document.getElementById('learningDropZone');
    if (dropZone) bindDropZone(dropZone, addLearningFiles);

    const textarea = document.getElementById('learningConversationMessage');
    const form = document.getElementById('learningConversationForm');
    Rag.bindEnterSubmit(textarea, form);
}

function bindDropZone(zone, onFiles) {
    ['dragenter', 'dragover'].forEach(evt => zone.addEventListener(evt, (e) => {
        e.preventDefault();
        e.stopPropagation();
        zone.classList.add('dragover');
    }));
    ['dragleave', 'drop'].forEach(evt => zone.addEventListener(evt, (e) => {
        e.preventDefault();
        e.stopPropagation();
        zone.classList.remove('dragover');
    }));
    zone.addEventListener('drop', (e) => onFiles(e.dataTransfer.files));
    zone.addEventListener('click', () => document.getElementById('learningConversationFile')?.click());
}

function addLearningFiles(fileList) {
    const files = Array.from(fileList || []).filter(f => f && f.size > 0);
    for (const file of files) {
        const key = `${file.name}_${file.size}_${file.lastModified}`;
        if (!learningSelectedFiles.some(f => `${f.name}_${f.size}_${f.lastModified}` === key)) learningSelectedFiles.push(file);
    }
    renderLearningSelectedFiles();
}

function clearLearningFiles() {
    learningSelectedFiles = [];
    const input = document.getElementById('learningConversationFile');
    if (input) input.value = '';
    renderLearningSelectedFiles();
}

function removeLearningFile(index) {
    learningSelectedFiles.splice(index, 1);
    renderLearningSelectedFiles();
}

function renderLearningSelectedFiles() {
    const list = document.getElementById('learningSelectedFileList');
    if (!list) return;
    if (learningSelectedFiles.length === 0) {
        list.innerHTML = '<span class="text-muted small">선택된 파일 없음</span>';
        return;
    }
    list.innerHTML = learningSelectedFiles.map((f, i) => `
        <span class="rag-file-chip">
            ${Rag.escapeHtml(f.name)} <small>${Math.ceil(f.size / 1024)}KB</small>
            <button type="button" onclick="removeLearningFile(${i})" aria-label="파일 제거">×</button>
        </span>
    `).join('');
}

async function startLearningConversation() {
    stopLearningJobPolling();
    const projectId = resolveLearningProjectId();
    const versionId = resolveLearningVersionId();
    const title = document.getElementById('learningTitleInput')?.value?.trim() || '대화형 지식 학습';
    const topic = document.getElementById('learningTopicInput')?.value?.trim() || title;
    if (!projectId) {
        Rag.toast('프로젝트를 먼저 선택해 주세요.', 'warning');
        return;
    }
    try {
        const data = await Rag.jsonFetch(`${LEARNING_CONVERSATION_API}/start`, {
            method: 'POST',
            body: JSON.stringify({ projectId, versionId, title, topic })
        });
        learningConversationSessionId = data.sessionId || data.session?.id;
        const badge = document.getElementById('learningConversationSessionBadge');
        if (badge) badge.textContent = learningConversationSessionId || '-';
        const log = document.getElementById('learningConversationLog');
        if (log) log.innerHTML = '';
        Rag.appendMessage('#learningConversationLog', 'assistant', data.answer || '대화형 학습을 시작했습니다.');
        updateLearningSaveBadge('지식 저장: 대기', 'NONE');
        const intentBox = document.getElementById('learningIntentBox');
        if (intentBox) intentBox.textContent = '';
        resetLearningJobProgress();
    } catch (e) {
        Rag.toast(e.message, 'danger');
    }
}

async function sendLearningConversationMessage(e) {
    e.preventDefault();
    if (!learningConversationSessionId) {
        await startLearningConversation();
        if (!learningConversationSessionId) return;
    }
    const textarea = document.getElementById('learningConversationMessage');
    const message = textarea?.value?.trim() || '';
    const forceSave = !!document.getElementById('learningForceSave')?.checked;
    const files = learningSelectedFiles.slice();
    if (!message && files.length === 0) return;

    if (message) Rag.appendMessage('#learningConversationLog', 'user', learningPreviewText(message, 3000));
    for (const f of files) Rag.appendMessage('#learningConversationLog', 'user', `[파일 업로드] ${f.name}`);

    resetLearningJobProgress();
    updateLearningSaveBadge('입력 해석 중', 'RUNNING');
    setLearningConversationBusy(true);
    try {
        updateLearningSaveBadge('입력 의미 해석 중', 'RUNNING');
        let data;
        if (files.length > 0) {
            const fd = new FormData();
            fd.append('message', message);
            fd.append('forceSave', String(forceSave));
            files.forEach(file => fd.append('files', file));
            data = await Rag.jsonFetch(`${LEARNING_CONVERSATION_API}/${learningConversationSessionId}/message-with-files`, {
                method: 'POST',
                body: fd
            });
            clearLearningFiles();
        } else {
            data = await Rag.jsonFetch(`${LEARNING_CONVERSATION_API}/${learningConversationSessionId}/message`, {
                method: 'POST',
                body: JSON.stringify({ message, forceSave })
            });
        }
        if (textarea) textarea.value = '';
        if (data.handled) {
            renderLearningRoutedResult(data);
            setLearningConversationBusy(false);
            textarea?.focus();
        } else if (data.accepted && data.jobId) {
            Rag.appendMessage('#learningConversationLog', 'system', data.answer || '학습 작업을 접수했습니다.');
            renderLearningJobProgress(data);
            startLearningJobPolling(data.jobId);
        } else {
            Rag.appendMessage('#learningConversationLog', data.requiresClarification ? 'system' : 'assistant', data.answer || '분석 결과를 생성하지 못했습니다.');
            renderLearningSaveStatus(data);
            renderLearningConversationResult(data);
            setLearningConversationBusy(false);
            textarea?.focus();
        }
    } catch (err) {
        Rag.toast(err.message, 'danger');
        Rag.appendMessage('#learningConversationLog', 'system', err.message);
        setLearningConversationBusy(false);
        textarea?.focus();
    }
}

function renderLearningRoutedResult(data) {
    Rag.appendMessage('#learningConversationLog', data.requiresClarification ? 'system' : 'assistant', learningPreviewText(data.answer || '처리했습니다.', 12000));
    renderLearningSaveStatus(data);
    renderLearningConversationResult(data);
    renderLearningIntentResult(data);
    resetLearningJobProgress();
}

function renderLearningIntentResult(data) {
    const box = document.getElementById('learningIntentBox');
    if (!box) return;
    box.textContent = learningPrettyForScreen({
        intentType: data.intentType || data.intent,
        actionStatus: data.actionStatus,
        confidence: data.confidence,
        agentRunId: data.agentRunId || null,
        changeSetId: data.changeResult?.changeSetId || data.memory?.changeSetId || null,
        sqlReadCount: Array.isArray(data.agentSqlResults) ? data.agentSqlResults.length : null,
        entityKey: data.entityKey || data.summary?.entityKey,
        counts: data.summary?.counts || null,
        saveStatus: data.saveStatus || null,
        assets: data.assets || data.summary?.assets || []
    });
}

function startLearningJobPolling(jobId) {
    stopLearningJobPolling();
    learningActiveJobId = jobId;
    pollLearningJob(jobId);
    learningActiveJobTimer = window.setInterval(() => pollLearningJob(jobId), LEARNING_JOB_POLL_INTERVAL_MS);
}

function stopLearningJobPolling() {
    if (learningActiveJobTimer) window.clearInterval(learningActiveJobTimer);
    learningActiveJobTimer = null;
    learningActiveJobId = null;
    learningLastJobRenderSignature = '';
    learningLastJobLogSignature = '';
}

async function pollLearningJob(jobId) {
    if (!learningConversationSessionId || !jobId) return;
    try {
        const job = await Rag.jsonFetch(`${learningJobApiBase()}/${jobId}`);
        renderLearningJobProgress(job);
        const runStatus = String(job.runStatus || job.run_status || '').toUpperCase();
        const status = String(job.status || '').toUpperCase();
        if (LEARNING_JOB_DONE.has(runStatus) || LEARNING_JOB_DONE.has(status)) {
            stopLearningJobPolling();
            setLearningConversationBusy(false);
            const textarea = document.getElementById('learningConversationMessage');
            textarea?.focus();
            if (runStatus === 'FAILED' || status === 'FAILED') {
                const msg = job.error_message || job.errorMessage || job.statusMessage || '학습 작업이 실패했습니다.';
                Rag.appendMessage('#learningConversationLog', 'system', `[학습 실패] ${msg}`);
                updateLearningSaveBadge('지식 저장: 실패', 'RESET');
                renderLearningConversationResult(job.result && Object.keys(job.result).length ? job.result : job);
                return;
            }
            const finalResult = job.result && Object.keys(job.result).length ? job.result : job;
            if (!finalResult.answer && job.answer) finalResult.answer = job.answer;
            if (!finalResult.answer && finalResult.saveMessage) finalResult.answer = finalResult.saveMessage;
            if (!finalResult.answer && job.statusMessage) finalResult.answer = job.statusMessage;
            Rag.appendMessage('#learningConversationLog', finalResult.requiresClarification ? 'system' : 'assistant', learningPreviewText(finalResult.answer || '작업은 완료됐지만 표시할 답변을 찾지 못했습니다. 진행 로그와 저장 상태를 확인해 주세요.', 12000));
            renderLearningSaveStatus(finalResult);
            renderLearningConversationResult(finalResult);
        }
    } catch (e) {
        stopLearningJobPolling();
        setLearningConversationBusy(false);
        Rag.toast(e.message, 'danger');
        Rag.appendMessage('#learningConversationLog', 'system', e.message);
    }
}

function renderLearningJobProgress(job) {
    const status = job.status || '-';
    const runStatus = job.runStatus || job.run_status || 'RUNNING';
    const progress = Number(job.progress || 0);
    const msg = job.statusMessage || job.status_message || '';
    const statusEl = document.getElementById('learningJobStatusText');
    const progressBar = document.getElementById('learningJobProgressBar');
    const logEl = document.getElementById('learningJobLogList');
    const jobIdEl = document.getElementById('learningActiveJobId');

    const renderSignature = `${job.jobId || job.id || '-'}|${runStatus}|${status}|${progress}|${msg}`;
    if (renderSignature !== learningLastJobRenderSignature) {
        learningLastJobRenderSignature = renderSignature;
        if (jobIdEl) jobIdEl.textContent = job.jobId || job.id || '-';
        if (statusEl) statusEl.textContent = `${runStatus} / ${status} / ${progress}%${msg ? ' - ' + msg : ''}`;
        if (progressBar) {
            const safeProgress = Math.max(0, Math.min(100, progress));
            progressBar.style.width = `${safeProgress}%`;
            progressBar.textContent = `${safeProgress}%`;
        }
        updateLearningSaveBadge(`학습 작업: ${status}`, runStatus === 'FAILED' ? 'RESET' : 'RUNNING');
    }

    if (logEl && Array.isArray(job.logs)) {
        const total = job.logs.length;
        const visibleLogs = total > LEARNING_MAX_RENDERED_JOB_LOGS ? job.logs.slice(total - LEARNING_MAX_RENDERED_JOB_LOGS) : job.logs;
        const logSignature = `${total}|${visibleLogs.map(log => `${log.status || ''}:${log.progress ?? ''}:${log.message || ''}`).join('|')}`;
        if (logSignature !== learningLastJobLogSignature) {
            learningLastJobLogSignature = logSignature;
            const omitted = total - visibleLogs.length;
            const omittedHtml = omitted > 0
                ? `<div class="rag-job-log-row text-muted"><span class="badge text-bg-light border">...</span><span></span><span>이전 로그 ${omitted}건은 화면 성능을 위해 접었습니다.</span></div>`
                : '';
            logEl.innerHTML = omittedHtml + visibleLogs.map(log => `
                <div class="rag-job-log-row">
                    <span class="badge text-bg-light border">${Rag.escapeHtml(log.status || '')}</span>
                    <span>${Rag.escapeHtml(String(log.progress ?? ''))}%</span>
                    <span>${Rag.escapeHtml(learningPreviewText(log.message || '', 500))}</span>
                </div>
            `).join('');
            logEl.scrollTop = logEl.scrollHeight;
        }
    }
}

function resetLearningJobProgress() {
    learningLastJobRenderSignature = '';
    learningLastJobLogSignature = '';
    const statusEl = document.getElementById('learningJobStatusText');
    const progressBar = document.getElementById('learningJobProgressBar');
    const logEl = document.getElementById('learningJobLogList');
    const jobIdEl = document.getElementById('learningActiveJobId');
    if (jobIdEl) jobIdEl.textContent = '-';
    if (statusEl) statusEl.textContent = '대기';
    if (progressBar) {
        progressBar.style.width = '0%';
        progressBar.textContent = '0%';
    }
    if (logEl) logEl.innerHTML = '<div class="text-muted small">아직 진행 로그가 없습니다.</div>';
}

async function retryRawKnowledgeNodes() {
    if (!learningConversationSessionId) {
        Rag.toast('먼저 학습 세션을 시작해 주세요.', 'warning');
        return;
    }
    stopLearningJobPolling();
    resetLearningJobProgress();
    updateLearningSaveBadge('재해석 작업: 접수 준비', 'RUNNING');
    setLearningConversationBusy(true);
    try {
        const limit = Number(document.getElementById('retryRawNodeLimit')?.value || 20);
        const data = await Rag.jsonFetch(`${LEARNING_CONVERSATION_API}/${learningConversationSessionId}/retry-raw-nodes?limit=${encodeURIComponent(limit)}`, {
            method: 'POST'
        });
        if (data.accepted && data.jobId) {
            Rag.appendMessage('#learningConversationLog', 'system', data.answer || '재해석 작업을 접수했습니다.');
            renderLearningJobProgress(data);
            startLearningJobPolling(data.jobId);
        } else {
            Rag.appendMessage('#learningConversationLog', 'system', data.message || data.answer || '재해석 작업 결과를 받았습니다.');
            renderLearningConversationResult(data);
            setLearningConversationBusy(false);
        }
    } catch (e) {
        Rag.toast(e.message, 'danger');
        Rag.appendMessage('#learningConversationLog', 'system', e.message);
        setLearningConversationBusy(false);
    }
}

async function resetLearningKnowledge() {
    if (!learningConversationSessionId) {
        Rag.toast('먼저 학습 세션을 시작해 주세요.', 'warning');
        return;
    }
    const topic = '';
    const reason = document.getElementById('resetKnowledgeReason')?.value?.trim() || '현재 버전 전체 학습 지식 초기화';
    const resetWholeVersion = true;
    if (!confirm('현재 버전의 전체 학습 지식을 초기화합니다. 이 작업은 되돌리기 어렵습니다. 계속 진행하시겠습니까?')) return;

    try {
        stopLearningJobPolling();
        const data = await Rag.jsonFetch(`${LEARNING_CONVERSATION_API}/${learningConversationSessionId}/reset-knowledge`, {
            method: 'POST',
            body: JSON.stringify({ topic, reason, resetWholeVersion })
        });
        Rag.appendMessage('#learningConversationLog', 'system', data.answer || '초기화되었습니다.');
        Rag.appendMessage('#learningConversationLog', 'system', '초기화 범위의 기존 학습 세션도 정리되었으므로, 계속 학습하려면 [대화형 학습 시작]을 다시 눌러 주세요.');
        learningConversationSessionId = null;
        const badge = document.getElementById('learningConversationSessionBadge');
        if (badge) badge.textContent = '-';
        updateLearningSaveBadge(data.saveStatus || '지식 저장: 초기화', 'RESET');
        renderLearningConversationResult(data);
        resetLearningJobProgress();
    } catch (e) {
        Rag.toast(e.message, 'danger');
    }
}

function renderLearningConversationResult(data) {
    const analysisBox = document.getElementById('learningConversationAnalysisBox');
    const versionBox = document.getElementById('learningConversationVersionBox');
    const retrievedBox = document.getElementById('learningConversationRetrievedBox');
    const structuredBox = document.getElementById('learningConversationStructuredBox');
    if (analysisBox) {
        const title = data.requiresClarification ? '[저장 보류 - 확인 필요]\n' : (data.shouldPersist ? '[저장 완료]\n' : '[저장 생략/응답 전용]\n');
        analysisBox.textContent = title + learningPrettyForScreen(data.analysis || data.resetResult || data.result || data || {});
    }
    if (versionBox) versionBox.textContent = learningPrettyForScreen(data.version || {});
    if (retrievedBox) retrievedBox.textContent = learningPrettyForScreen(data.retrieved || []);
    if (structuredBox) structuredBox.textContent = learningPrettyForScreen({
        preprocess: data.preprocess || null,
        structuredResults: data.structuredResults || [],
        structuredReset: data.structuredReset || null,
        structuredSaveStatus: data.structuredSaveStatus || null
    });
    renderLearningIntentResult(data);
}

function learningPreviewText(value, maxLength) {
    const text = String(value || '');
    const limit = Math.max(100, Number(maxLength || 1000));
    if (text.length <= limit) return text;
    return text.slice(0, limit) + `\n\n... 화면 성능을 위해 ${text.length - limit}자를 접었습니다. 전체 내용은 서버 작업/DB에는 그대로 전달됩니다.`;
}

function learningPrettyForScreen(value) {
    try {
        return JSON.stringify(learningCompactForScreen(value, 0), null, 2);
    } catch (e) {
        return learningPreviewText(String(value || ''), LEARNING_MAX_PRETTY_STRING);
    }
}

function learningCompactForScreen(value, depth) {
    if (value == null) return value;
    if (typeof value === 'string') return learningPreviewText(value, LEARNING_MAX_PRETTY_STRING);
    if (typeof value !== 'object') return value;
    if (depth >= LEARNING_MAX_PRETTY_DEPTH) return '[화면 표시 생략: 중첩 데이터가 너무 깊습니다]';
    if (Array.isArray(value)) {
        const result = value.slice(0, LEARNING_MAX_PRETTY_LIST).map(v => learningCompactForScreen(v, depth + 1));
        if (value.length > LEARNING_MAX_PRETTY_LIST) result.push(`[화면 표시 생략: ${value.length - LEARNING_MAX_PRETTY_LIST}건 추가 데이터]`);
        return result;
    }
    const result = {};
    const entries = Object.entries(value);
    for (const [key, val] of entries.slice(0, 120)) {
        if (learningIsHeavyJsonKey(key)) {
            result[key] = '[화면 표시 생략: 원문/대용량 데이터]';
            continue;
        }
        result[key] = learningCompactForScreen(val, depth + 1);
    }
    if (entries.length > 120) result._screenOmittedKeys = `${entries.length - 120}개 키 생략`;
    return result;
}

function learningIsHeavyJsonKey(key) {
    const k = String(key || '').toLowerCase();
    return k.includes('rawtext')
        || k.includes('enrichedattachmenttext')
        || k.includes('attachmenttext')
        || k.includes('fulltext')
        || k === 'content'
        || k === 'documenttext'
        || k === 'embedding'
        || k === 'vector'
        || k === 'result_json';
}

function setLearningConversationBusy(busy) {
    const btn = document.querySelector('#learningConversationForm button[type="submit"]');
    const textarea = document.getElementById('learningConversationMessage');
    const startBtn = document.getElementById('learningConversationStartBtn');
    if (btn) {
        btn.disabled = busy;
        btn.textContent = busy ? '진행 중...' : '전송';
    }
    if (textarea) textarea.disabled = busy;
    if (startBtn) startBtn.disabled = busy;
}

function resolveLearningProjectId() {
    const direct = document.getElementById('learningProjectId')?.value;
    if (direct) return direct;
    const select = document.getElementById('projectSelect') || document.getElementById('learningProjectSelect') || document.getElementById('chatProjectSelect');
    return Rag.selectedOption(select);
}

function resolveLearningVersionId() {
    const direct = document.getElementById('learningVersionId')?.value;
    if (direct) return direct;
    const select = document.getElementById('versionSelect') || document.getElementById('learningVersionSelect');
    return Rag.selectedOption(select);
}

function renderLearningSaveStatus(data) {
    const label = data.saveStatus || (data.memory && data.memory.saveLabel) || '지식 저장: 저장 생략';
    let status = data.memory?.status || 'NO_KNOWLEDGE_CHANGE';
    if (label.includes('저장됨')) status = 'PERSISTED';
    else if (label.includes('보류')) status = 'WAITING_USER';
    else if (label.includes('초기화')) status = 'RESET';
    const message = data.saveMessage || data.memory?.message || '대화 로그는 저장되었지만 재사용 가능한 새 지식이 아니어서 지식 저장은 생략했습니다.';
    updateLearningSaveBadge(label, status);
    Rag.appendMessage('#learningConversationLog', status === 'WAITING_USER' ? 'system' : 'save', learningPreviewText(`[${label}] ${message}`, 4000));
}

function updateLearningSaveBadge(label, status) {
    const badge = document.getElementById('learningSaveStatusBadge');
    if (!badge) return;
    badge.textContent = label || '-';
    if (status === 'PERSISTED') badge.className = 'badge text-bg-success fs-6';
    else if (status === 'WAITING_USER') badge.className = 'badge text-bg-warning fs-6';
    else if (status === 'RESET') badge.className = 'badge text-bg-danger fs-6';
    else if (status === 'RUNNING') badge.className = 'badge text-bg-info fs-6';
    else if (status === 'NO_KNOWLEDGE_CHANGE') badge.className = 'badge text-bg-secondary fs-6';
    else badge.className = 'badge text-bg-info fs-6';
}
