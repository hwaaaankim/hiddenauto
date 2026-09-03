(function (window, document) {
    'use strict';

    const api = {};

    api.request = async function (url, options) {
        const config = Object.assign({
            credentials: 'same-origin',
            headers: {}
        }, options || {});

        if (config.body && !(config.body instanceof FormData)) {
            config.headers = Object.assign({'Content-Type': 'application/json'}, config.headers || {});
            if (typeof config.body !== 'string') {
                config.body = JSON.stringify(config.body);
            }
        }

        let response;
        try {
            response = await fetch(url, config);
        } catch (error) {
            throw new Error('서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.');
        }

        let payload = null;
        const contentType = response.headers.get('content-type') || '';
        if (contentType.includes('application/json')) {
            try {
                payload = await response.json();
            } catch (error) {
                payload = {success: false, message: '서버 응답을 해석할 수 없습니다.'};
            }
        } else {
            const text = await response.text();
            const productMasterApi = String(url || '').startsWith('/admin/api/product-master');
            payload = {
                success: response.ok && !productMasterApi,
                message: productMasterApi
                    ? (response.redirected ? '로그인 세션이 만료되었습니다. 다시 로그인해 주세요.' : '서버가 올바른 API 응답을 반환하지 않았습니다.')
                    : text
            };
        }

        if (!response.ok || payload.success === false) {
            throw new Error(payload && payload.message
                ? payload.message
                : '요청을 처리하지 못했습니다.');
        }
        return payload;
    };

    api.escapeHtml = function (value) {
        return String(value == null ? '' : value)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    };

    api.money = function (value) {
        return new Intl.NumberFormat('ko-KR').format(Number(value || 0)) + '원';
    };

    api.number = function (value) {
        return new Intl.NumberFormat('ko-KR').format(Number(value || 0));
    };

    function dateParts(value) {
        if (value == null || value === '') return null;
        if (Array.isArray(value)) {
            const parts = value.map(Number);
            return parts.length >= 3 && parts.slice(0, 3).every(Number.isFinite) ? parts : null;
        }
        if (typeof value === 'string') {
            const normalized = value.trim();
            if (/^\d{4},\d{1,2},\d{1,2}/.test(normalized)) {
                const parts = normalized.split(',').map(Number);
                return parts.slice(0, 3).every(Number.isFinite) ? parts : null;
            }
            const match = normalized.match(/^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[T\s](\d{1,2}):?(\d{1,2})?)?/);
            if (match) return [Number(match[1]), Number(match[2]), Number(match[3]), Number(match[4] || 0), Number(match[5] || 0)];
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return null;
        return [date.getFullYear(), date.getMonth() + 1, date.getDate(), date.getHours(), date.getMinutes()];
    }

    function pad2(value) {
        return String(Number(value || 0)).padStart(2, '0');
    }

    api.date = function (value) {
        const parts = dateParts(value);
        return parts ? parts[0] + '/' + pad2(parts[1]) + '/' + pad2(parts[2]) : '-';
    };

    api.dateTime = function (value) {
        const parts = dateParts(value);
        return parts ? parts[0] + '/' + pad2(parts[1]) + '/' + pad2(parts[2]) + ' ' + pad2(parts[3]) + ':' + pad2(parts[4]) : '-';
    };

    api.showLoading = function (visible, text) {
        const overlay = document.getElementById('pm-loading-overlay');
        if (!overlay) return;
        const label = overlay.querySelector('[data-pm-loading-label]');
        if (label && text) label.textContent = text;
        overlay.classList.toggle('pm-is-visible', Boolean(visible));
        overlay.setAttribute('aria-hidden', visible ? 'false' : 'true');
    };

    api.alert = async function (icon, title, text) {
        if (window.Swal) {
            return window.Swal.fire({
                icon: icon || 'info',
                title: title || '',
                text: text || '',
                confirmButtonText: '확인',
                confirmButtonColor: '#3d66f5'
            });
        }
        window.alert([title, text].filter(Boolean).join('\n'));
    };

    api.confirm = async function (title, text, confirmText) {
        if (window.Swal) {
            const result = await window.Swal.fire({
                icon: 'warning',
                title: title,
                text: text,
                showCancelButton: true,
                confirmButtonText: confirmText || '진행',
                cancelButtonText: '취소',
                confirmButtonColor: '#ef5b5b'
            });
            return result.isConfirmed;
        }
        return window.confirm([title, text].filter(Boolean).join('\n'));
    };

    api.toast = function (message, icon) {
        if (window.Swal) {
            window.Swal.fire({
                toast: true,
                position: 'top-end',
                icon: icon || 'success',
                title: message,
                showConfirmButton: false,
                timer: 2200,
                timerProgressBar: true
            });
            return;
        }
        console.info(message);
    };

    api.copy = async function (text) {
        try {
            await navigator.clipboard.writeText(String(text || ''));
            api.toast('클립보드에 복사했습니다.');
        } catch (error) {
            const input = document.createElement('textarea');
            input.value = String(text || '');
            input.style.position = 'fixed';
            input.style.opacity = '0';
            document.body.appendChild(input);
            input.select();
            document.execCommand('copy');
            input.remove();
            api.toast('클립보드에 복사했습니다.');
        }
    };

    api.downloadText = function (filename, text, mimeType) {
        const blob = new Blob([text], {type: mimeType || 'text/plain;charset=utf-8'});
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
    };

    api.debounce = function (callback, delay) {
        let timer = null;
        return function () {
            const args = arguments;
            clearTimeout(timer);
            timer = setTimeout(function () {
                callback.apply(null, args);
            }, delay || 250);
        };
    };

    api.stockClass = function (code) {
        return 'pm-state-badge pm-state-' + api.escapeHtml(code || 'OUT_OF_STOCK');
    };

    window.HiddenBathProductMaster = api;
})(window, document);
