(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        initPasswordToggle();
        initPhoneInputs();
        initRecoveryForms();
        initRecoveryModalReset();
    });

    function initPasswordToggle() {
        const button = document.getElementById('sign-in-password-toggle');
        const input = document.getElementById('sign-in-password');
        if (!button || !input) return;

        button.addEventListener('click', function () {
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            button.setAttribute('aria-pressed', show ? 'true' : 'false');
            button.setAttribute('aria-label', show ? '비밀번호 숨기기' : '비밀번호 표시');
            button.innerHTML = show
                ? '<i class="fa-regular fa-eye-slash"></i>'
                : '<i class="fa-regular fa-eye"></i>';
            input.focus({ preventScroll: true });
        });
    }

    function initPhoneInputs() {
        document.querySelectorAll('.sign-in-phone-input').forEach(function (input) {
            input.addEventListener('input', function () {
                input.value = formatPhone(input.value);
            });
        });
    }

    function formatPhone(value) {
        const digits = String(value || '').replace(/\D/g, '').slice(0, 11);
        if (digits.length <= 3) return digits;
        if (digits.length <= 7) return digits.slice(0, 3) + '-' + digits.slice(3);
        return digits.slice(0, 3) + '-' + digits.slice(3, 7) + '-' + digits.slice(7);
    }

    function initRecoveryForms() {
        document.querySelectorAll('[data-sign-in-recovery-form]').forEach(function (form) {
            form.addEventListener('submit', function (event) {
                event.preventDefault();
                submitRecoveryForm(form);
            });
        });
    }

    async function submitRecoveryForm(form) {
        const type = form.getAttribute('data-sign-in-recovery-form');
        const endpoint = type === 'password'
            ? '/api/v1/account-recovery/password'
            : '/api/v1/account-recovery/username';
        const feedback = document.getElementById(
            type === 'password'
                ? 'sign-in-recovery-password-feedback'
                : 'sign-in-recovery-username-feedback'
        );
        const submitButton = form.querySelector('button[type="submit"]');

        if (!form.reportValidity()) return;

        setFeedback(feedback, '', '');
        setBusy(submitButton, true);

        try {
            const params = new URLSearchParams(new FormData(form));
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                    'Accept': 'application/json',
                    'X-Requested-With': 'fetch'
                },
                body: params.toString()
            });

            let data = null;
            try {
                data = await response.json();
            } catch (e) {
                data = null;
            }

            if (!response.ok || !data || data.success !== true) {
                throw new Error(data && data.message ? data.message : '요청을 처리하지 못했습니다. 입력 정보를 확인해 주세요.');
            }

            setFeedback(feedback, data.message || '문자 발송이 완료되었습니다.', 'success');
        } catch (error) {
            setFeedback(feedback, error.message || '요청 처리 중 오류가 발생했습니다.', 'error');
        } finally {
            setBusy(submitButton, false);
        }
    }

    function setBusy(button, busy) {
        if (!button) return;

        if (busy) {
            button.dataset.originalText = button.textContent;
            button.textContent = '처리 중...';
            button.disabled = true;
        } else {
            button.textContent = button.dataset.originalText || button.textContent;
            button.disabled = false;
        }
    }

    function setFeedback(element, message, type) {
        if (!element) return;

        element.classList.remove('is-visible', 'is-success', 'is-error');
        element.textContent = message || '';
        if (!message) return;

        element.classList.add('is-visible');
        element.classList.add(type === 'success' ? 'is-success' : 'is-error');
    }

    function initRecoveryModalReset() {
        const modal = document.getElementById('sign-in-recovery-modal');
        if (!modal) return;

        modal.addEventListener('hidden.bs.modal', function () {
            modal.querySelectorAll('form').forEach(function (form) {
                form.reset();
            });
            modal.querySelectorAll('.sign-in-recovery-feedback').forEach(function (feedback) {
                setFeedback(feedback, '', '');
            });
        });
    }
})();
