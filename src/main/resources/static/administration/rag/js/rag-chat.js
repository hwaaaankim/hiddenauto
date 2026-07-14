let chatProjects = [];
let chatSessionId = null;
let chatProjectId = null;
let chatVersionId = null;
let currentState = null;
let currentRetrieved = [];
let currentMemory = null;
let chatSelectedFiles = [];

const AI_CHAT_API = '/admin/rag/api/ai-chat';
const RAG_CHAT_JS_VERSION = '20260714-gpt-db-agent-v2';

document.addEventListener('DOMContentLoaded', () => {
    console.info('[RAG_CHAT_JS] loaded', RAG_CHAT_JS_VERSION);
    const badge = document.getElementById('chatJsVersionBadge');
    if (badge) badge.textContent = RAG_CHAT_JS_VERSION;
    bindChatEvents();
    loadChatProjects();
});

function bindChatEvents() {
    document.getElementById('startChatBtn')?.addEventListener('click', startChat);
    document.getElementById('chatForm')?.addEventListener('submit', sendChatMessage);
    document.getElementById('resetStepBtn')?.addEventListener('click', resetStep);
    document.getElementById('inquiryBtn')?.addEventListener('click', saveInquiry);
    document.getElementById('chatFileInput')?.addEventListener('change', (e) => addChatFiles(e.target.files));
    document.getElementById('clearChatFilesBtn')?.addEventListener('click', clearChatFiles);

    const dropZone = document.getElementById('chatDropZone');
    if (dropZone) bindChatDropZone(dropZone);

    const message = document.getElementById('chatMessage');
    const form = document.getElementById('chatForm');
    Rag.bindEnterSubmit(message, form);
}

function bindChatDropZone(zone) {
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
    zone.addEventListener('drop', (e) => addChatFiles(e.dataTransfer.files));
    zone.addEventListener('click', () => document.getElementById('chatFileInput')?.click());
}

function addChatFiles(fileList) {
    const files = Array.from(fileList || []).filter(f => f && f.size > 0);
    for (const file of files) {
        const key = `${file.name}_${file.size}_${file.lastModified}`;
        if (!chatSelectedFiles.some(f => `${f.name}_${f.size}_${f.lastModified}` === key)) chatSelectedFiles.push(file);
    }
    renderChatSelectedFiles();
}

function clearChatFiles() {
    chatSelectedFiles = [];
    const input = document.getElementById('chatFileInput');
    if (input) input.value = '';
    renderChatSelectedFiles();
}

function removeChatFile(index) {
    chatSelectedFiles.splice(index, 1);
    renderChatSelectedFiles();
}

function renderChatSelectedFiles() {
    const list = document.getElementById('chatSelectedFileList');
    if (!list) return;
    if (chatSelectedFiles.length === 0) {
        list.innerHTML = '<span class="text-muted small">선택된 파일 없음</span>';
        return;
    }
    list.innerHTML = chatSelectedFiles.map((f, i) => `
        <span class="rag-file-chip">
            ${Rag.escapeHtml(f.name)} <small>${Math.ceil(f.size / 1024)}KB</small>
            <button type="button" onclick="removeChatFile(${i})" aria-label="파일 제거">×</button>
        </span>
    `).join('');
}

async function loadChatProjects() {
    try {
        chatProjects = await Rag.jsonFetch('/admin/rag/api/projects');
        const select = document.getElementById('chatProjectSelect');
        if (!select) return;
        select.innerHTML = '';
        for (const p of chatProjects) {
            const opt = document.createElement('option');
            opt.value = p.id;
            opt.disabled = !p.active_version_id;
            opt.textContent = `${p.title}${p.active_version_no ? ' / ACTIVE v' + p.active_version_no : ' / ACTIVE 없음'}`;
            opt.dataset.versionId = p.active_version_id || '';
            select.appendChild(opt);
        }
    } catch (e) {
        Rag.toast(e.message, 'danger');
    }
}

async function startChat() {
    const projectId = Rag.selectedOption(document.getElementById('chatProjectSelect'));
    if (!projectId) {
        Rag.toast('ACTIVE 버전이 있는 프로젝트를 선택해 주세요.', 'warning');
        return;
    }
    const select = document.getElementById('chatProjectSelect');
    chatProjectId = projectId;
    chatVersionId = select?.options[select.selectedIndex]?.dataset?.versionId || null;
    const userLabel = document.getElementById('chatUserLabel')?.value?.trim() || '테스트 고객';

    try {
        const data = await Rag.jsonFetch(`${AI_CHAT_API}/start`, {
            method: 'POST',
            body: JSON.stringify({ projectId, userLabel })
        });
        chatSessionId = data.sessionId || data.session?.id;
        currentState = data.state || data.session?.state_json || {};
        currentRetrieved = data.retrieved || [];
        currentMemory = data.memory || null;
        document.getElementById('sessionBadge').textContent = chatSessionId || '-';
        document.getElementById('chatLog').innerHTML = '';
        clearChatFiles();
        Rag.appendMessage('#chatLog', 'assistant', data.answer || '채팅을 시작했습니다.');
        Rag.appendMessage('#chatLog', 'system', '[자동학습 활성화] 이제부터 입력되는 채팅/파일은 대화 로그로 저장되고, 재사용 가능한 지식은 AI 분석 후 벡터DB에 저장됩니다.');
        renderState();
        renderRetrieved();
        renderMemory();
        renderResetStepOptions();
        renderChatAssets([]);
    } catch (e) {
        Rag.toast(e.message, 'danger');
    }
}

async function sendChatMessage(e) {
    e.preventDefault();
    if (!chatSessionId) {
        Rag.toast('먼저 채팅을 시작해 주세요.', 'warning');
        return;
    }
    const input = document.getElementById('chatMessage');
    const message = input.value.trim();
    const files = chatSelectedFiles.slice();
    if (!message && files.length === 0) return;

    input.value = '';
    if (message) Rag.appendMessage('#chatLog', 'user', message);
    for (const f of files) Rag.appendMessage('#chatLog', 'user', `[파일 업로드] ${f.name}`);
    setChatBusy(true);

    try {
        let data;
        if (files.length > 0) {
            const fd = new FormData();
            fd.append('message', message);
            files.forEach(file => fd.append('files', file));
            data = await Rag.jsonFetch(`${AI_CHAT_API}/${chatSessionId}/message-with-files`, {
                method: 'POST',
                body: fd
            });
            clearChatFiles();
        } else {
            data = await Rag.jsonFetch(`${AI_CHAT_API}/message`, {
                method: 'POST',
                body: JSON.stringify({ sessionId: chatSessionId, message })
            });
        }
        if (data.handled) {
            renderChatRoutedResult(data);
        } else {
            currentState = data.state || {};
            currentRetrieved = data.retrieved || [];
            currentMemory = data.memory || null;
            Rag.appendMessage('#chatLog', 'assistant', data.answer || '응답을 생성하지 못했습니다.');
            renderSaveStatus(data);
            renderChatIntent(data);
            renderState();
            renderRetrieved();
            renderMemory();
            renderResetStepOptions();
        }
    } catch (e2) {
        Rag.toast(e2.message, 'danger');
        Rag.appendMessage('#chatLog', 'system', e2.message);
    } finally {
        setChatBusy(false);
        input.focus();
    }
}

function renderChatRoutedResult(data) {
    Rag.appendMessage('#chatLog', data.requiresClarification ? 'system' : 'assistant', data.answer || '처리했습니다.');
    currentState = data.state || currentState || {};
    currentRetrieved = data.retrieved || data.summary?.retrieved || [];
    currentMemory = data.memory || {
        status: data.actionStatus || 'HANDLED',
        saveLabel: data.saveStatus || '지식 저장: 조회/연결 처리',
        message: data.saveMessage || ''
    };
    renderSaveStatus(data);
    renderChatIntent(data);
    renderState();
    renderRetrieved();
    renderMemory();
    renderResetStepOptions();
    renderChatAssets(data.assets || data.summary?.assets || []);
}

function renderChatIntent(data) {
    const box = document.getElementById('chatIntentBox');
    if (!box) return;
    box.textContent = Rag.pretty({
        intentType: data.intentType || data.intent || null,
        agentIntentType: data.agentIntentType || data.requestPlan?.intentType || null,
        actionStatus: data.actionStatus || null,
        confidence: data.confidence || null,
        agentMode: data.agentMode || null,
        agentRunId: data.agentRunId || null,
        agentToolTurns: data.agentToolTurns ?? null,
        agentToolCount: Array.isArray(data.agentToolTrace) ? data.agentToolTrace.length : null,
        recovered: data.recovered ?? null,
        semanticCandidateCount: data.semanticResult?.resultCount ?? data.semanticResult?.candidateCount ?? null,
        priceCalculated: data.pricingResult?.calculated ?? null,
        changeSetId: data.changeResult?.changeSetId || data.memory?.changeSetId || null,
        sqlReadCount: Array.isArray(data.agentSqlResults) ? data.agentSqlResults.length : null,
        entityKey: data.entityKey || data.summary?.entityKey || null,
        counts: data.summary?.counts || null,
        errorCode: data.errorCode || null,
        saveStatus: data.saveStatus || null,
        saveMessage: data.saveMessage || null
    });
}

function renderChatAssets(assets) {
    const list = document.getElementById('helpAssetList');
    if (!list) return;
    const safeAssets = Array.isArray(assets) ? assets : [];
    if (safeAssets.length === 0) {
        list.innerHTML = '<div class="small text-muted">연결된 이미지/파일이 없습니다.</div>';
        return;
    }
    list.innerHTML = safeAssets.slice(0, 12).map(asset => {
        const url = asset.file_url || asset.fileUrl || '';
        const name = asset.original_filename || asset.originalFilename || asset.display_name || '업로드 파일';
        const isImg = /\.(png|jpg|jpeg|webp|gif)$/i.test(url) || String(asset.content_type || '').startsWith('image/');
        return `
            <div class="col-6">
                <div class="rag-asset-card">
                    ${isImg && url ? `<img src="${Rag.escapeHtml(url)}" alt="${Rag.escapeHtml(name)}">` : '<div class="rag-asset-placeholder">FILE</div>'}
                    <div class="small fw-bold text-truncate" title="${Rag.escapeHtml(name)}">${Rag.escapeHtml(name)}</div>
                    ${url ? `<a class="small" href="${Rag.escapeHtml(url)}" target="_blank" rel="noopener">열기</a>` : ''}
                </div>
            </div>
        `;
    }).join('');
}

async function resetStep() {
    if (!chatSessionId) {
        Rag.toast('먼저 채팅을 시작해 주세요.', 'warning');
        return;
    }
    const stepKey = document.getElementById('resetStepKey')?.value || '';
    const reason = document.getElementById('resetReason')?.value?.trim() || '';
    try {
        const data = await Rag.jsonFetch(`${AI_CHAT_API}/${chatSessionId}/reset-step`, {
            method: 'POST',
            body: JSON.stringify({ stepKey, reason })
        });
        currentState = data.state || currentState || {};
        Rag.appendMessage('#chatLog', 'assistant', data.answer || '선택 항목을 초기화했습니다.');
        renderState();
        renderResetStepOptions();
    } catch (e) {
        Rag.toast(e.message, 'danger');
    }
}

async function saveInquiry() {
    if (!chatSessionId) {
        Rag.toast('먼저 채팅을 시작해 주세요.', 'warning');
        return;
    }
    const payload = {
        sessionId: chatSessionId,
        companyName: document.getElementById('inqCompany')?.value?.trim() || '',
        customerName: document.getElementById('inqName')?.value?.trim() || '',
        phone: document.getElementById('inqPhone')?.value?.trim() || '',
        email: document.getElementById('inqEmail')?.value?.trim() || '',
        memo: document.getElementById('inqMemo')?.value?.trim() || ''
    };
    try {
        await Rag.jsonFetch(`${AI_CHAT_API}/inquiry`, {
            method: 'POST',
            body: JSON.stringify(payload)
        });
        Rag.toast('문의가 저장되었습니다.', 'success');
    } catch (e) {
        Rag.toast(e.message, 'danger');
    }
}

function renderState() {
    const box = document.getElementById('stateBox');
    if (box) box.textContent = Rag.pretty(currentState || {});
}

function renderRetrieved() {
    const box = document.getElementById('retrievedBox');
    if (box) box.textContent = Rag.pretty(currentRetrieved || []);
}

function renderMemory() {
    const box = document.getElementById('memoryBox');
    if (box) box.textContent = Rag.pretty(currentMemory || {});
    const badge = document.getElementById('saveStatusBadge');
    if (badge) {
        const label = currentMemory?.saveLabel || currentMemory?.status || '대기';
        badge.textContent = label;
        badge.className = 'badge ' + saveStatusClass(currentMemory?.status);
    }
}

function renderSaveStatus(data) {
    const memory = data.memory || {};
    const label = data.saveStatus || memory.saveLabel || '지식 저장: 저장 생략';
    const status = memory.status || (label.includes('저장됨') ? 'PERSISTED' : label.includes('보류') ? 'WAITING_USER' : 'NO_KNOWLEDGE_CHANGE');
    const msg = data.saveMessage || memory.message || '대화 로그는 저장되었지만 재사용 가능한 새 지식이 아니어서 지식 저장은 생략했습니다.';
    Rag.appendMessage('#chatLog', status === 'WAITING_USER' ? 'system' : 'save', `[${label}] ${msg}`);
    const badge = document.getElementById('saveStatusBadge');
    if (badge) {
        badge.textContent = label;
        badge.className = 'badge ' + saveStatusClass(status);
    }
}

function saveStatusClass(status) {
    if (status === 'PERSISTED') return 'text-bg-success';
    if (status === 'WAITING_USER') return 'text-bg-warning';
    if (status === 'NO_KNOWLEDGE_CHANGE') return 'text-bg-secondary';
    return 'text-bg-info';
}

function renderResetStepOptions() {
    const select = document.getElementById('resetStepKey');
    if (!select) return;
    const answers = currentState && currentState.answers ? currentState.answers : {};
    const keys = Object.keys(answers);
    select.innerHTML = '<option value="">전체/최근 흐름</option>';
    for (const key of keys) {
        const opt = document.createElement('option');
        opt.value = key;
        opt.textContent = key;
        select.appendChild(opt);
    }
}

function setChatBusy(busy) {
    const btn = document.querySelector('#chatForm button[type="submit"]');
    const input = document.getElementById('chatMessage');
    const startBtn = document.getElementById('startChatBtn');
    if (btn) btn.disabled = busy;
    if (input) input.disabled = busy;
    if (startBtn) startBtn.disabled = busy;
}
