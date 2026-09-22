(function () {
    'use strict';
    const states = new WeakMap();
    function csrfHeaders() {
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (token && header) return { [header]: token };
        const input = document.querySelector('input[data-csrf-header]');
        return input?.value ? { [input.dataset.csrfHeader]: input.value } : {};
    }
    async function responseData(response) {
        if (response.redirected) throw new Error('로그인이 만료되었습니다. 새로고침 후 다시 로그인해주세요.');
        const text = await response.text();
        let data;
        try { data = JSON.parse(text); } catch (_) { throw new Error('서버 응답을 확인할 수 없습니다. 로그인 또는 업로드 용량 제한을 확인해주세요.'); }
        if (!response.ok) throw new Error(data.message || '요청 처리에 실패했습니다.');
        return data;
    }
    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text;
        return node;
    }
    function clear(host) {
        if (!host) return;
        const state = states.get(host);
        if (state) state.files.forEach(item => URL.revokeObjectURL(item.url));
        states.delete(host);
        host.replaceChildren(); host.hidden = true;
    }
    function busy(host) { return Boolean(host && states.get(host)?.busy); }
    async function mount(host, orderId) {
        if (!host) return;
        clear(host); host.hidden = false;
        const state = { orderId, files: [], images: [], keep: new Set(), busy: true, loaded: false };
        states.set(host, state);
        const title = el('h6', 'mt-3', '배송완료 이미지');
        const help = el('p', 'text-muted small', '기존 사진을 유지하거나 삭제하고 새 사진을 추가한 뒤 저장해주세요. 배송 상태와 완료일은 변경되지 않습니다.');
        const message = el('p', 'delivery-image-message', '이미지를 불러오는 중입니다.');
        message.setAttribute('role', 'status');
        const grid = el('div', 'delivery-image-grid');
        const actions = el('div', 'delivery-image-actions');
        const gallery = el('button', 'btn btn-outline-primary', '사진 추가'); gallery.type = 'button';
        const camera = el('button', 'btn btn-outline-primary', '사진 촬영'); camera.type = 'button';
        const save = el('button', 'btn btn-success', '이미지 저장'); save.type = 'button';
        const inputs = [false, true].map(capture => {
            const input = el('input', 'd-none'); input.type = 'file'; input.accept = 'image/*,.heic,.heif';
            if (capture) input.setAttribute('capture', 'environment'); else input.multiple = true;
            input.addEventListener('change', async () => {
                if (state.busy || !input.files.length) return;
                setBusy(true); message.textContent = '이미지 확인·변환 중입니다. 잠시 기다려주세요.';
                try {
                    if (!window.HiddenAutoImageUpload) throw new Error('이미지 변환 모듈을 불러오지 못했습니다. 새로고침해주세요.');
                    const result = await window.HiddenAutoImageUpload.normalizeFiles(input.files);
                    if (result.rejected.length) throw new Error('이미지가 아닌 파일이 포함되어 있습니다. 이미지 파일만 선택해주세요.');
                    result.files.forEach(file => state.files.push({ file, url: URL.createObjectURL(file) }));
                    message.textContent = result.converted.length ? `${result.converted.length}장을 JPEG로 변환했습니다. 저장 버튼을 눌러주세요.` : '변경사항을 저장해주세요.';
                    render();
                } catch (error) { message.textContent = error.message; }
                finally { input.value = ''; setBusy(false); }
            });
            return input;
        });
        gallery.onclick = () => inputs[0].click(); camera.onclick = () => inputs[1].click();
        actions.append(gallery, camera, save, ...inputs);
        host.append(title, help, message, grid, actions);
        function setBusy(value) {
            state.busy = value;
            host.querySelectorAll('button').forEach(button => { button.disabled = value || !state.loaded; });
            save.disabled = value || !state.loaded || state.keep.size + state.files.length < 1;
        }
        function render() {
            grid.replaceChildren();
            function tile(url, label, buttonLabel, callback, deleted) {
                const item = el('div', 'delivery-image-tile' + (deleted ? ' is-removed' : ''));
                const link = el('a'); link.href = url; link.target = '_blank'; link.rel = 'noopener noreferrer';
                const img = el('img'); img.src = url; img.alt = label; img.loading = 'lazy';
                img.onerror = () => { img.alt = '미리보기 불가 (원본 보기)'; };
                link.append(img);
                const button = el('button', 'btn btn-sm btn-outline-secondary', buttonLabel); button.type = 'button';
                button.onclick = () => { callback(); render(); setBusy(false); message.textContent = '변경사항을 저장해주세요.'; };
                item.append(link, button); grid.append(item);
            }
            state.images.forEach(img => {
                const kept = state.keep.has(img.id);
                tile(img.url, img.filename || '배송 증빙', kept ? '삭제' : '삭제 취소', () => {
                    if (kept) state.keep.delete(img.id); else state.keep.add(img.id);
                }, !kept);
            });
            state.files.forEach((item, index) => tile(item.url, item.file.name, '추가 취소', () => {
                URL.revokeObjectURL(item.url); state.files.splice(index, 1);
            }, false));
            if (!state.images.length && !state.files.length) grid.append(el('p', 'text-muted', '등록된 배송완료 이미지가 없습니다.'));
        }
        save.onclick = async () => {
            if (state.busy || !state.loaded) return;
            const changed = state.files.length || state.keep.size !== state.images.length;
            if (!changed) { message.textContent = '변경된 이미지가 없습니다.'; return; }
            if (!confirm('이 주문의 배송완료 이미지만 저장하시겠습니까?')) return;
            setBusy(true); message.textContent = '저장 중입니다.';
            const form = new FormData();
            state.images.forEach(img => form.append('expectedIds', img.id));
            state.keep.forEach(id => form.append('keepIds', id));
            state.files.forEach(item => form.append('files', item.file, item.file.name));
            try {
                const data = await responseData(await fetch(`/team/delivery-tools/${state.orderId}/images`, {
                    method: 'POST', headers: csrfHeaders(), body: form, credentials: 'same-origin'
                }));
                state.files.forEach(item => URL.revokeObjectURL(item.url)); state.files = [];
                state.images = data; state.keep = new Set(data.map(img => img.id)); render();
                message.textContent = '이미지를 저장했습니다. 배송 상태와 완료일은 유지됩니다.';
            } catch (error) { message.textContent = error.message; }
            finally { setBusy(false); }
        };
        setBusy(true);
        try {
            const images = await responseData(await fetch(`/team/delivery-tools/${orderId}/images`, { credentials: 'same-origin' }));
            if (states.get(host) !== state) return;
            state.images = images; state.keep = new Set(images.map(img => img.id)); state.loaded = true;
            render(); message.textContent = '';
        } catch (error) { if (states.get(host) === state) message.textContent = error.message; }
        finally { if (states.get(host) === state) setBusy(false); }
    }
    window.HiddenAutoDeliveryImages = Object.freeze({ mount, clear, busy });
    document.addEventListener('DOMContentLoaded', () => {
        const company = document.getElementById('delivery-company-name');
        const options = document.getElementById('delivery-company-options');
        if (company && options) {
            let timer, controller, selected = -1;
            const close = () => { options.hidden = true; company.setAttribute('aria-expanded', 'false'); company.removeAttribute('aria-activedescendant'); selected = -1; };
            function choose(button) { company.value = button.textContent; close(); company.focus(); }
            company.addEventListener('input', () => {
                clearTimeout(timer); controller?.abort(); close();
                const keyword = company.value.trim();
                if (!keyword) return;
                timer = setTimeout(async () => {
                    controller = new AbortController();
                    try {
                        const names = await responseData(await fetch('/team/delivery-tools/companies?' + new URLSearchParams({ keyword }), { signal: controller.signal }));
                        if (company.value.trim() !== keyword || document.activeElement !== company) return;
                        options.replaceChildren();
                        names.forEach((name, index) => {
                            const option = el('button', 'delivery-company-option', name); option.type = 'button'; option.id = 'delivery-company-option-' + index;
                            option.setAttribute('role', 'option'); option.setAttribute('aria-selected', 'false');
                            option.addEventListener('mousedown', e => e.preventDefault());
                            option.onclick = () => choose(option); options.append(option);
                        });
                        options.hidden = names.length === 0; company.setAttribute('aria-expanded', String(names.length > 0));
                    } catch (e) { if (e.name !== 'AbortError') close(); }
                }, 200);
            });
            company.addEventListener('keydown', event => {
                const items = Array.from(options.children);
                if (event.key === 'Escape') return close();
                if (options.hidden || !items.length) return;
                if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                    event.preventDefault(); selected = (selected + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
                    items.forEach((item, i) => item.setAttribute('aria-selected', String(i === selected)));
                    company.setAttribute('aria-activedescendant', items[selected].id); items[selected].scrollIntoView({ block: 'nearest' });
                } else if (event.key === 'Enter' && selected >= 0) { event.preventDefault(); choose(items[selected]); }
            });
            document.addEventListener('click', e => { if (!e.target.closest('.delivery-company-field')) close(); });
            company.addEventListener('blur', () => setTimeout(close, 150));
        }
        document.querySelectorAll('.delivery-filter-grid').forEach(form => form.addEventListener('submit', event => {
            const from = form.elements.orderIdFrom, to = form.elements.orderIdTo;
            if (from.value && to.value && Number(from.value) > Number(to.value)) {
                event.preventDefault(); alert('Order ID From은 To보다 클 수 없습니다.'); from.focus();
            }
        }));
        document.querySelectorAll('[data-delivery-check]').forEach(button => button.addEventListener('click', () => {
            const checked = button.dataset.deliveryCheck === 'true';
            document.querySelectorAll('.delivery-list-added-handler-check:not(:disabled)').forEach(input => {
                input.checked = checked;
            });
            document.querySelector('.delivery-list-added-handler-check')?.dispatchEvent(new Event('change', { bubbles: true }));
        }));
        if (document.querySelector('.delivery-mobile-actions')) {
            document.body.classList.add('delivery-list-enhanced');
            document.getElementById('delivery-mobile-top').onclick = () => window.scrollTo({ top: 0, behavior: 'smooth' });
        }
        const modal = document.getElementById('delivery-image-modal');
        if (modal) {
            const host = document.getElementById('delivery-route-image-editor');
            const instance = new bootstrap.Modal(modal, { backdrop: 'static' });
            document.addEventListener('click', event => {
                const button = event.target.closest('.delivery-image-open');
                if (!button) return;
                event.preventDefault(); event.stopPropagation();
                document.getElementById('delivery-image-order-label').textContent = '#' + button.dataset.imageOrderId;
                mount(host, button.dataset.imageOrderId); instance.show();
            });
            modal.addEventListener('hide.bs.modal', event => { if (busy(host)) event.preventDefault(); });
            modal.addEventListener('hidden.bs.modal', () => clear(host));
        }
    });
})();
