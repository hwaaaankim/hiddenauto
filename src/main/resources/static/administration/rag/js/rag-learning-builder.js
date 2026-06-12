let projectId;
let versionId;
let learningSessionId = null;
let currentVersion = null;

document.addEventListener('DOMContentLoaded', () => {
    projectId = document.getElementById('projectId').value;
    versionId = document.getElementById('versionId').value;
    bindEvents();
    initPage();
});

function bindEvents() {
    document.getElementById('learningChatForm').addEventListener('submit', sendLearningMessage);
    document.getElementById('textLearnForm').addEventListener('submit', learnText);
    document.getElementById('excelLearnForm').addEventListener('submit', learnExcel);
    document.getElementById('assetForm').addEventListener('submit', uploadAsset);
    document.getElementById('publishBtn').addEventListener('click', publishVersion);
}

async function initPage() {
    try {
        await loadVersion();
        const data = await Rag.jsonFetch('/admin/rag/api/learning/sessions', {
            method: 'POST',
            body: JSON.stringify({ projectId, versionId })
        });
        learningSessionId = data.session.id;
        document.getElementById('learningSessionBadge').textContent = learningSessionId.substring(0, 8);
        Rag.appendMessage('#learningChatLog', 'assistant', data.reply);
        renderRecentDocuments(data.recentDocuments || []);
        console.log('[RAG-LEARNING][START]', data);
    } catch (err) {
        Rag.toast(err.message, 'danger');
    }
}

async function loadVersion() {
    const version = await Rag.jsonFetch(`/admin/rag/api/versions/${versionId}`);
    currentVersion = version;
    renderVersion(version);
}

async function sendLearningMessage(e) {
    e.preventDefault();
    if (!learningSessionId) {
        Rag.toast('학습 세션이 아직 준비되지 않았습니다.', 'warning');
        return;
    }
    const input = document.getElementById('learningMessage');
    const message = input.value.trim();
    if (!message) return;
    Rag.appendMessage('#learningChatLog', 'user', message);
    input.value = '';
    setLoading(true);
    try {
        const data = await Rag.jsonFetch(`/admin/rag/api/learning/sessions/${learningSessionId}/messages`, {
            method: 'POST',
            body: JSON.stringify({ message })
        });
        Rag.appendMessage('#learningChatLog', 'assistant', data.reply);
        renderVersion(data.version);
        renderRecentDocuments(data.recentDocuments || []);
        console.log('[RAG-LEARNING][MESSAGE_RESULT]', data);
    } catch (err) {
        Rag.toast(err.message, 'danger');
    } finally {
        setLoading(false);
    }
}

async function learnText(e) {
    e.preventDefault();
    const body = Rag.formToObject(e.currentTarget);
    setLoading(true);
    try {
        const data = await Rag.jsonFetch(`/admin/rag/api/projects/${projectId}/versions/${versionId}/learn-text`, {
            method: 'POST',
            body: JSON.stringify(body)
        });
        renderVersion(data.version);
        renderRecentDocuments(data.recentDocuments || []);
        Rag.appendMessage('#learningChatLog', 'system', '텍스트 자료가 현재 구조에 반영되었습니다.');
        Rag.toast('텍스트 자료 반영 완료', 'success');
        e.currentTarget.reset();
        console.log('[RAG-LEARNING][TEXT_RESULT]', data);
    } catch (err) { Rag.toast(err.message, 'danger'); }
    finally { setLoading(false); }
}

async function learnExcel(e) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    setLoading(true);
    try {
        const data = await Rag.jsonFetch(`/admin/rag/api/projects/${projectId}/versions/${versionId}/learn-excel`, {
            method: 'POST',
            body: fd
        });
        renderVersion(data.version);
        renderRecentDocuments(data.recentDocuments || []);
        Rag.appendMessage('#learningChatLog', 'system', '엑셀 자료가 현재 구조에 반영되었습니다.');
        Rag.toast('엑셀 자료 반영 완료', 'success');
        e.currentTarget.reset();
        console.log('[RAG-LEARNING][EXCEL_RESULT]', data);
    } catch (err) { Rag.toast(err.message, 'danger'); }
    finally { setLoading(false); }
}

async function uploadAsset(e) {
    e.preventDefault();
    const fd = new FormData(e.currentTarget);
    try {
        const data = await Rag.jsonFetch(`/admin/rag/api/projects/${projectId}/versions/${versionId}/assets`, { method: 'POST', body: fd });
        Rag.toast('이미지 업로드 완료', 'success');
        e.currentTarget.reset();
        await loadAssets();
        console.log('[RAG-ASSET][UPLOAD]', data);
    } catch (err) { Rag.toast(err.message, 'danger'); }
}

async function loadAssets() {
    const assets = await Rag.jsonFetch(`/admin/rag/api/projects/${projectId}/versions/${versionId}/assets`);
    const list = document.getElementById('assetList');
    list.innerHTML = '';
    for (const a of assets.slice(0, 6)) {
        const col = document.createElement('div');
        col.className = 'col-6';
        col.innerHTML = `<div class="asset-thumb"><img src="${a.file_url}" alt=""><div class="caption"><strong>${a.owner_type}</strong><br>${a.owner_key}<br>${Rag.escapeHtml(a.note || '')}</div></div>`;
        list.appendChild(col);
    }
}

async function publishVersion() {
    if (!confirm('현재 버전을 ACTIVE로 발행하시겠습니까? 챗봇은 이 버전을 사용합니다.')) return;
    try {
        const data = await Rag.jsonFetch(`/admin/rag/api/projects/${projectId}/versions/${versionId}/publish`, { method: 'POST' });
        renderVersion(data);
        Rag.toast('현재 버전이 발행되었습니다.', 'success');
    } catch (err) { Rag.toast(err.message, 'danger'); }
}

function renderVersion(version) {
    currentVersion = version;
    const process = toObj(version.process_json);
    const pricing = toObj(version.pricing_json);
    const constraints = toObj(version.constraints_json);
    const validation = toObj(version.validation_report_json);
    const score = Number(validation.completionScore || 0);

    document.getElementById('pageTitle').textContent = `${version.title || 'RAG 학습 빌더'}`;
    document.getElementById('versionBadge').textContent = `${version.status || 'DRAFT'} / v${version.version_no || '-'}`;
    document.getElementById('versionBadge').className = `badge ${version.status === 'ACTIVE' ? 'text-bg-success' : 'text-bg-secondary'}`;
    document.getElementById('phaseBox').textContent = process.learningPhase || 'PROCESS';
    document.getElementById('summaryBox').textContent = version.summary || '-';
    document.getElementById('completionScore').textContent = `${score}%`;
    document.getElementById('completionBar').style.width = `${Math.min(100, Math.max(0, score))}%`;

    renderGaps(validation.gaps || []);
    renderSteps(process.steps || []);
    document.getElementById('pricingBox').textContent = Rag.pretty(pricing);
    document.getElementById('constraintsBox').textContent = Rag.pretty(constraints);
    document.getElementById('validationBox').textContent = Rag.pretty(validation);
    document.getElementById('fullJsonBox').textContent = Rag.pretty({ process, pricing, constraints, validation });
}

function renderGaps(gaps) {
    const ul = document.getElementById('gapList');
    ul.innerHTML = '';
    if (!gaps.length) {
        ul.innerHTML = '<li>현재 부족 정보 없음</li>';
        return;
    }
    for (const gap of gaps) {
        const li = document.createElement('li');
        li.textContent = typeof gap === 'string' ? gap : JSON.stringify(gap);
        ul.appendChild(li);
    }
}

function renderSteps(steps) {
    const list = document.getElementById('stepList');
    list.innerHTML = '';
    if (!steps.length) {
        list.innerHTML = '<div class="alert alert-light border mb-0">아직 스텝이 없습니다. 관리자 대화에서 큰 스텝 순서를 입력해 주세요.</div>';
        return;
    }
    steps.sort((a,b) => Number(a.orderNo || 0) - Number(b.orderNo || 0));
    for (const s of steps) {
        const div = document.createElement('div');
        div.className = 'rag-step-card';
        const options = Array.isArray(s.answerOptions) ? s.answerOptions.map(o => o.label || o.answerKey).filter(Boolean).join(', ') : '';
        div.innerHTML = `
            <div class="d-flex justify-content-between gap-2">
                <div class="fw-bold">${s.orderNo || '-'} . ${Rag.escapeHtml(s.title || s.stepKey || '')}</div>
                <span class="badge text-bg-light border">${Rag.escapeHtml(s.inputType || '-')}</span>
            </div>
            <div class="small mt-1">${Rag.escapeHtml(s.question || '')}</div>
            <div class="step-meta mt-2">key: ${Rag.escapeHtml(s.stepKey || '')} / price: ${Rag.escapeHtml(s.priceImpact || 'NONE')}</div>
            ${options ? `<div class="step-meta">answers: ${Rag.escapeHtml(options)}</div>` : ''}
        `;
        list.appendChild(div);
    }
}

function renderRecentDocuments(docs) {
    const list = document.getElementById('recentDocumentList');
    list.innerHTML = '';
    if (!docs.length) {
        list.innerHTML = '<div class="text-muted">최근 자료 없음</div>';
        return;
    }
    for (const d of docs) {
        const div = document.createElement('div');
        div.className = 'border rounded p-2 bg-white';
        div.innerHTML = `<strong>${Rag.escapeHtml(d.topic || '')}</strong> / ${Rag.escapeHtml(d.source_type || '')}<br>${Rag.escapeHtml(d.title || d.original_filename || '')}`;
        list.appendChild(div);
    }
}

function toObj(v) {
    if (!v) return {};
    if (typeof v === 'object') return v;
    try { return JSON.parse(v); } catch (e) { return { raw: v }; }
}

function setLoading(on) {
    const btn = document.getElementById('sendLearningBtn');
    btn.disabled = on;
    btn.textContent = on ? '정리 중...' : '전송';
}
