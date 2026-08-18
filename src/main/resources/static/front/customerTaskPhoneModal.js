(function () {
    'use strict';

    const DESKTOP_QUERY = '(min-width: 992px)';
    const MODAL_ID = 'customer-task-phone-modal';
    const NUMBER_ID = 'customer-task-phone-number';
    const CONTEXT_ID = 'customer-task-phone-context';
    const COPY_ID = 'customer-task-phone-copy';

    function isDesktop() {
        return window.matchMedia && window.matchMedia(DESKTOP_QUERY).matches;
    }

    function getPhoneFromLink(link) {
        if (!link) return '';
        const href = link.getAttribute('href') || '';
        if (!href.toLowerCase().startsWith('tel:')) return '';

        try {
            return decodeURIComponent(href.substring(4)).trim();
        } catch (e) {
            return href.substring(4).trim();
        }
    }

    function getContextText(link) {
        const title = (link && link.getAttribute('title')) || '';
        if (!title) return '연락처';

        return title
            .replace(/에게\s*전화/g, '')
            .replace(/로\s*전화/g, '')
            .replace(/전화/g, '')
            .trim() || '연락처';
    }

    function ensureModal() {
        let modal = document.getElementById(MODAL_ID);
        if (modal) return modal;

        modal = document.createElement('div');
        modal.className = 'modal fade customer-task-phone-modal';
        modal.id = MODAL_ID;
        modal.tabIndex = -1;
        modal.setAttribute('aria-labelledby', MODAL_ID + '-title');
        modal.setAttribute('aria-hidden', 'true');
        modal.innerHTML = [
            '<div class="modal-dialog modal-dialog-centered customer-task-phone-dialog">',
            '  <div class="modal-content customer-task-phone-content">',
            '    <div class="modal-header customer-task-phone-header">',
            '      <div>',
            '        <div class="customer-task-phone-kicker">CONTACT</div>',
            '        <h5 class="modal-title customer-task-phone-title" id="' + MODAL_ID + '-title">연락처 확인</h5>',
            '      </div>',
            '      <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="닫기"></button>',
            '    </div>',
            '    <div class="modal-body customer-task-phone-body">',
            '      <div class="customer-task-phone-context" id="' + CONTEXT_ID + '">연락처</div>',
            '      <div class="customer-task-phone-number" id="' + NUMBER_ID + '">-</div>',
            '      <div class="customer-task-phone-guide">PC에서는 전화 연결 대신 번호를 확인하거나 복사할 수 있습니다.</div>',
            '    </div>',
            '    <div class="modal-footer customer-task-phone-footer">',
            '      <button type="button" class="btn btn-light customer-task-phone-close" data-bs-dismiss="modal">닫기</button>',
            '      <button type="button" class="btn btn-primary customer-task-phone-copy" id="' + COPY_ID + '">',
            '        <i class="fa fa-copy me-1"></i>번호 복사',
            '      </button>',
            '    </div>',
            '  </div>',
            '</div>'
        ].join('');

        document.body.appendChild(modal);

        const copyButton = document.getElementById(COPY_ID);
        if (copyButton) {
            copyButton.addEventListener('click', function () {
                const numberElement = document.getElementById(NUMBER_ID);
                const phone = numberElement ? (numberElement.textContent || '').trim() : '';
                if (!phone || phone === '-') return;
                copyPhone(phone, copyButton);
            });
        }

        return modal;
    }

    async function copyPhone(phone, button) {
        let copied = false;

        try {
            if (navigator.clipboard && window.isSecureContext) {
                await navigator.clipboard.writeText(phone);
                copied = true;
            } else {
                const textarea = document.createElement('textarea');
                textarea.value = phone;
                textarea.setAttribute('readonly', 'readonly');
                textarea.style.position = 'fixed';
                textarea.style.opacity = '0';
                document.body.appendChild(textarea);
                textarea.select();
                copied = document.execCommand('copy');
                textarea.remove();
            }
        } catch (e) {
            console.warn('[customer-task-phone] clipboard copy failed', e);
        }

        if (!button) return;
        const originalHtml = button.innerHTML;
        button.innerHTML = copied
            ? '<i class="fa fa-check me-1"></i>복사 완료'
            : '<i class="fa fa-copy me-1"></i>복사 실패';
        button.disabled = true;

        window.setTimeout(function () {
            button.innerHTML = originalHtml;
            button.disabled = false;
        }, 1200);
    }

    function openPhoneModal(link) {
        const phone = getPhoneFromLink(link);
        if (!phone) return;

        const modal = ensureModal();
        const numberElement = document.getElementById(NUMBER_ID);
        const contextElement = document.getElementById(CONTEXT_ID);

        if (numberElement) numberElement.textContent = phone;
        if (contextElement) contextElement.textContent = getContextText(link);

        if (window.bootstrap && window.bootstrap.Modal) {
            window.bootstrap.Modal.getOrCreateInstance(modal).show();
            return;
        }

        // Bootstrap가 비정상적으로 누락된 경우에도 PC에서 tel: 실행은 막고 번호는 확인할 수 있게 합니다.
        window.alert(phone);
    }

    document.addEventListener('click', function (event) {
        const link = event.target.closest('a[href^="tel:"], a[href^="TEL:"]');
        if (!link || !isDesktop()) return;

        const page = link.closest('.task-list-page, .as-list-page');
        if (!page) return;

        event.preventDefault();
        event.stopPropagation();
        event.stopImmediatePropagation();
        openPhoneModal(link);
    }, true);
})();
