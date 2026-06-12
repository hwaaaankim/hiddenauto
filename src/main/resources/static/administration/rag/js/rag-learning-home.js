const RAG_LEARNING_HOME_JS_VERSION = '20260611-final';

document.addEventListener('DOMContentLoaded', () => {
    console.info('[RAG_LEARNING_HOME_JS] loaded', RAG_LEARNING_HOME_JS_VERSION);
    document.getElementById('createProjectForm')?.addEventListener('submit', createLearningProject);
    loadLearningHomeProjects();
});

async function createLearningProject(e) {
    e.preventDefault();
    const title = document.getElementById('projectTitle')?.value?.trim() || '';
    const description = document.getElementById('projectDescription')?.value?.trim() || '';
    const learningDirection = document.getElementById('learningDirection')?.value?.trim() || '';
    if (!title) {
        Rag.toast('학습 타이틀을 입력해 주세요.', 'warning');
        return;
    }
    try {
        const data = await Rag.jsonFetch('/admin/rag/api/projects', {
            method: 'POST',
            body: JSON.stringify({ title, description, learningDirection })
        });
        const projectId = data.project?.id || data.id || data.projectId;
        const versionId = data.version?.id || data.versionId || data.active_version_id;
        if (projectId && versionId) {
            location.href = `/admin/rag/learning/${projectId}/${versionId}`;
            return;
        }
        Rag.toast('프로젝트가 생성되었습니다.', 'success');
        await loadLearningHomeProjects();
    } catch (err) {
        Rag.toast(err.message, 'danger');
    }
}

async function loadLearningHomeProjects() {
    const list = document.getElementById('learningHomeProjectList');
    if (!list) return;
    list.innerHTML = '<div class="col-12 text-muted">프로젝트를 불러오는 중입니다...</div>';
    try {
        const projects = await Rag.jsonFetch('/admin/rag/api/projects');
        if (!projects || projects.length === 0) {
            list.innerHTML = '<div class="col-12"><div class="alert alert-secondary mb-0">프로젝트가 없습니다. 위에서 학습 타이틀을 입력해 새 프로젝트를 생성해 주세요.</div></div>';
            return;
        }
        list.innerHTML = '';
        for (const p of projects) {
            const versionId = p.active_version_id || p.open_version_id || p.latest_version_id;
            const versionNo = p.active_version_no || p.open_version_no || p.latest_version_no || '-';
            const activeText = p.active_version_id ? `ACTIVE v${p.active_version_no}` : 'ACTIVE 없음';
            const disabled = !versionId;
            const url = disabled ? '#' : `/admin/rag/learning/${p.id}/${versionId}`;
            const html = `
                <div class="col-lg-4 col-md-6">
                    <div class="card h-100 rag-project-card">
                        <div class="card-body d-flex flex-column">
                            <div class="d-flex justify-content-between gap-2 mb-2">
                                <h2 class="h5 fw-bold mb-0">${Rag.escapeHtml(p.title || '제목 없음')}</h2>
                                <span class="badge ${p.active_version_id ? 'text-bg-success' : 'text-bg-secondary'}">${Rag.escapeHtml(activeText)}</span>
                            </div>
                            <p class="text-muted small flex-grow-1">${Rag.escapeHtml(p.description || p.active_summary || '설명 없음')}</p>
                            <div class="small mb-2">열 버전: v${Rag.escapeHtml(versionNo)}</div>
                            <a class="btn ${disabled ? 'btn-outline-secondary disabled' : 'btn-primary'}" href="${url}">대화형 학습 열기</a>
                        </div>
                    </div>
                </div>`;
            list.insertAdjacentHTML('beforeend', html);
        }
    } catch (e) {
        list.innerHTML = `<div class="col-12"><div class="alert alert-danger mb-0">${Rag.escapeHtml(e.message)}</div></div>`;
    }
}
