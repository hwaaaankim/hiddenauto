/* /administration/assets/js/teamActionFeedback.js */
(function (window, document) {
    'use strict';

    if (window.TeamActionFeedback) {
        return;
    }

    var DEFAULT_SUCCESS_DURATION = 1100;
    var DEFAULT_ERROR_DURATION = 1800;
    var sequence = 0;
    var hideTimer = null;
    var pendingResolve = null;
    var elements = null;

    function ensureElements() {
        if (elements) {
            return elements;
        }

        var root = document.getElementById('team-action-feedback');

        if (!root) {
            root = document.createElement('div');
            root.id = 'team-action-feedback';
            root.className = 'team-action-feedback';
            root.hidden = true;
            root.setAttribute('aria-hidden', 'true');
            root.innerHTML = [
                '<div class="team-action-feedback-backdrop"></div>',
                '<section class="team-action-feedback-panel" role="status" aria-live="assertive" aria-atomic="true">',
                '  <div class="team-action-feedback-icon" aria-hidden="true">',
                '    <span class="team-action-feedback-spinner"></span>',
                '    <span class="team-action-feedback-symbol"></span>',
                '  </div>',
                '  <div class="team-action-feedback-eyebrow">처리 중</div>',
                '  <h2 class="team-action-feedback-title">잠시만 기다려 주세요.</h2>',
                '  <p class="team-action-feedback-message">요청하신 작업을 안전하게 반영하고 있습니다.</p>',
                '  <div class="team-action-feedback-detail" hidden></div>',
                '  <div class="team-action-feedback-progress" aria-hidden="true"><span></span></div>',
                '  <div class="team-action-feedback-guide">창을 닫거나 같은 버튼을 다시 누르지 마세요.</div>',
                '</section>'
            ].join('');
            document.body.appendChild(root);
        }

        elements = {
            root: root,
            panel: root.querySelector('.team-action-feedback-panel'),
            eyebrow: root.querySelector('.team-action-feedback-eyebrow'),
            title: root.querySelector('.team-action-feedback-title'),
            message: root.querySelector('.team-action-feedback-message'),
            detail: root.querySelector('.team-action-feedback-detail'),
            guide: root.querySelector('.team-action-feedback-guide')
        };

        return elements;
    }

    function clearScheduledHide() {
        if (hideTimer) {
            window.clearTimeout(hideTimer);
            hideTimer = null;
        }

        if (pendingResolve) {
            pendingResolve();
            pendingResolve = null;
        }
    }

    function normalizeOptions(options) {
        if (typeof options === 'string') {
            return { message: options };
        }
        return options && typeof options === 'object' ? options : {};
    }

    function setText(element, value, fallback) {
        if (!element) {
            return;
        }
        var text = value == null ? '' : String(value).trim();
        element.textContent = text || fallback || '';
    }

    function renderState(type, options) {
        var ui = ensureElements();
        var normalized = normalizeOptions(options);
        var state = type === 'success' || type === 'error' ? type : 'loading';

        ui.root.classList.remove(
            'team-action-feedback-loading',
            'team-action-feedback-success',
            'team-action-feedback-error'
        );
        ui.root.classList.add('team-action-feedback-' + state);

        if (state === 'success') {
            setText(ui.eyebrow, normalized.eyebrow, '완료');
            setText(ui.title, normalized.title, '작업이 완료되었습니다.');
            setText(ui.message, normalized.message, '변경 내용이 정상적으로 반영되었습니다.');
            setText(ui.guide, normalized.guide, '화면을 정리하고 있습니다.');
        } else if (state === 'error') {
            setText(ui.eyebrow, normalized.eyebrow, '처리 실패');
            setText(ui.title, normalized.title, '작업을 완료하지 못했습니다.');
            setText(ui.message, normalized.message, '입력 내용과 네트워크 상태를 확인한 뒤 다시 시도해 주세요.');
            setText(ui.guide, normalized.guide, '잠시 후 현재 화면으로 돌아갑니다.');
        } else {
            setText(ui.eyebrow, normalized.eyebrow, '처리 중');
            setText(ui.title, normalized.title, '잠시만 기다려 주세요.');
            setText(ui.message, normalized.message, '요청하신 작업을 안전하게 반영하고 있습니다.');
            setText(ui.guide, normalized.guide, '창을 닫거나 같은 버튼을 다시 누르지 마세요.');
        }

        var detailText = normalized.detail == null ? '' : String(normalized.detail).trim();
        if (ui.detail) {
            ui.detail.textContent = detailText;
            ui.detail.hidden = !detailText;
        }

        ui.panel.setAttribute('role', state === 'error' ? 'alert' : 'status');
        ui.root.hidden = false;
        ui.root.setAttribute('aria-hidden', 'false');
        document.body.classList.add('team-action-feedback-open');

        window.requestAnimationFrame(function () {
            ui.root.classList.add('is-visible');
        });
    }

    function begin(options) {
        clearScheduledHide();
        sequence += 1;
        renderState('loading', options);
        return sequence;
    }

    function update(options, token) {
        if (token && token !== sequence) {
            return false;
        }
        renderState('loading', options);
        return true;
    }

    function finish(type, options, token) {
        if (token && token !== sequence) {
            return Promise.resolve(false);
        }

        clearScheduledHide();
        renderState(type, options);

        var normalized = normalizeOptions(options);
        var duration = Number(normalized.duration);
        if (!Number.isFinite(duration) || duration < 0) {
            duration = type === 'error' ? DEFAULT_ERROR_DURATION : DEFAULT_SUCCESS_DURATION;
        }

        if (normalized.stay === true) {
            return Promise.resolve(true);
        }

        return new Promise(function (resolve) {
            pendingResolve = resolve;
            hideTimer = window.setTimeout(function () {
                hide(token);
            }, duration);
        });
    }

    function success(options, token) {
        return finish('success', options, token);
    }

    function error(options, token) {
        return finish('error', options, token);
    }

    function hide(token) {
        if (token && token !== sequence) {
            return false;
        }

        var ui = ensureElements();
        clearScheduledHide();
        ui.root.classList.remove('is-visible');
        ui.root.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('team-action-feedback-open');

        window.setTimeout(function () {
            if (!ui.root.classList.contains('is-visible')) {
                ui.root.hidden = true;
            }
        }, 180);

        return true;
    }

    function showFlash(options) {
        var normalized = normalizeOptions(options);
        var successMessage = normalized.successMessage == null
            ? ''
            : String(normalized.successMessage).trim();
        var errorMessage = normalized.errorMessage == null
            ? ''
            : String(normalized.errorMessage).trim();

        if (errorMessage) {
            return error({
                title: normalized.errorTitle || '처리 결과를 확인해 주세요.',
                message: errorMessage,
                detail: normalized.errorDetail || '',
                duration: normalized.errorDuration
            });
        }

        if (successMessage) {
            return success({
                title: normalized.successTitle || '작업이 완료되었습니다.',
                message: successMessage,
                detail: normalized.successDetail || '',
                duration: normalized.successDuration
            });
        }

        return Promise.resolve(false);
    }

    window.TeamActionFeedback = {
        begin: begin,
        update: update,
        success: success,
        error: error,
        hide: hide,
        showFlash: showFlash,
        isVisible: function () {
            var ui = ensureElements();
            return !ui.root.hidden && ui.root.classList.contains('is-visible');
        }
    };
})(window, document);
