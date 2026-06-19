const Rag = (() => {
    async function jsonFetch(url, options = {}) {
        const opt = Object.assign({ headers: {} }, options);
        if (!(opt.body instanceof FormData)) {
            opt.headers['Content-Type'] = opt.headers['Content-Type'] || 'application/json';
        }
        const res = await fetch(url, opt);
        const text = await res.text();
        let data = null;
        try { data = text ? JSON.parse(text) : null; } catch (e) { data = { raw: text }; }
        if (!res.ok) {
            const msg = data && data.message ? data.message : (`요청 실패: ${res.status}`);
            throw new Error(msg);
        }
        return data;
    }

    async function optionalJsonFetch(url, options = {}) {
        try {
            return await jsonFetch(url, options);
        } catch (e) {
            const msg = String(e && e.message ? e.message : e);
            if (msg.includes('404') || msg.includes('Not Found') || msg.includes('요청 실패: 404')) {
                return { __notAvailable: true, message: msg };
            }
            throw e;
        }
    }

    function pretty(value) {
        try { return JSON.stringify(value || {}, null, 2); }
        catch (e) { return String(value); }
    }

    function toast(message, type = 'info') {
        const area = document.getElementById('toastArea');
        if (!area) { alert(message); return; }
        const id = 'toast-' + Date.now();
        const html = `
        <div id="${id}" class="toast align-items-center text-bg-${type} border-0" role="alert">
          <div class="d-flex">
            <div class="toast-body">${escapeHtml(message)}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
          </div>
        </div>`;
        area.insertAdjacentHTML('beforeend', html);
        const el = document.getElementById(id);
        const t = new bootstrap.Toast(el, { delay: 3500 });
        t.show();
        el.addEventListener('hidden.bs.toast', () => el.remove());
    }

    function appendMessage(selector, role, content) {
        const log = typeof selector === 'string' ? document.querySelector(selector) : selector;
        if (!log) return;
        const div = document.createElement('div');
        div.className = `rag-message ${role}`;
        const bubble = document.createElement('div');
        bubble.className = 'bubble';
        bubble.textContent = content || '';
        div.appendChild(bubble);
        log.appendChild(div);
        log.scrollTop = log.scrollHeight;
    }

    function escapeHtml(value) {
        return String(value || '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    function formToObject(form) {
        const fd = new FormData(form);
        const obj = {};
        for (const [k, v] of fd.entries()) obj[k] = typeof v === 'string' ? v.trim() : v;
        return obj;
    }

    function selectedOption(select) {
        if (!select) return null;
        const opt = select.options[select.selectedIndex];
        if (!opt || opt.disabled) return null;
        return opt.value || null;
    }



    function bindEnterSubmit(textarea, form) {
        if (!textarea || !form) return;

        // 기존 화면에 Enter 처리 리스너가 따로 있어도 Shift+Enter가 전송으로 뺏기지 않게
        // capture 단계에서 먼저 잡습니다. Shift+Enter는 기본 줄바꿈 동작만 남깁니다.
        textarea.addEventListener('keydown', (e) => {
            if (e.key !== 'Enter') return;

            if (e.shiftKey) {
                e.stopImmediatePropagation();
                return;
            }

            e.preventDefault();
            e.stopImmediatePropagation();
            if (typeof form.requestSubmit === 'function') form.requestSubmit();
            else form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
        }, true);
    }

    return { jsonFetch, optionalJsonFetch, pretty, toast, appendMessage, escapeHtml, formToObject, selectedOption, bindEnterSubmit };
})();
