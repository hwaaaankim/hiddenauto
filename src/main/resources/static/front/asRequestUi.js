(function () {
    'use strict';

    const SELECTORS = {
        form: '#as-request-fifth-form',
        productContainer: '#as-request-fifth-productSections',
        productSection: '.as-request-fifth-product-section',
        productTitle: '.as-request-fifth-product-title-text',
        productIndexBadge: '.as-request-product-index-badge',
        productName: '.as-request-fifth-productName',
        productSize: '.as-request-fifth-productSize',
        productColor: '.as-request-fifth-productColor',
        productOptions: '.as-request-fifth-productOptions',
        category: '.as-request-fifth-subjectCategory',
        subject: '.as-request-fifth-subject',
        billing: '.as-request-fifth-billingTarget:checked',
        fileInput: '.as-request-fifth-attachments-input',
        previewList: '.as-request-fifth-preview-list',
        overviewList: '#as-request-overview-list',
        productCount: '#as-request-product-count',
        overviewCount: '#as-request-overview-count',
        overviewProducts: '#as-request-overview-products',
        overviewFiles: '#as-request-overview-files',
        overviewReady: '#as-request-overview-ready',
        actionSummary: '#as-request-action-summary'
    };

    let refreshQueued = false;

    document.addEventListener('DOMContentLoaded', function () {
        const form = document.querySelector(SELECTORS.form);
        const productContainer = document.querySelector(SELECTORS.productContainer);
        if (!form || !productContainer) return;

        bindSafeEnterNavigation(form);
        bindOverviewNavigation();
        bindOverviewRefreshEvents(form);
        observeDynamicProducts(productContainer);
        refreshProductUi();
    });

    function bindSafeEnterNavigation(form) {
        form.addEventListener('keydown', function (event) {
            if (event.key !== 'Enter' || event.isComposing) return;

            const target = event.target;
            if (!(target instanceof HTMLElement)) return;

            // textarea/select/radio/date/file/button/readonly는 기본 조작을 보존합니다.
            if (target.matches('textarea, select, button, [type="radio"], [type="checkbox"], [type="date"], [type="file"], [readonly]')) {
                return;
            }

            if (target.id === 'as-request-fifth-customerName') {
                moveFocus(event, document.getElementById('as-request-fifth-onsiteContact'));
                return;
            }

            if (target.id === 'as-request-fifth-applicantName') {
                moveFocus(event, document.getElementById('as-request-fifth-applicantPhone'));
                return;
            }

            if (target.id === 'as-request-fifth-applicantPhone') {
                moveFocus(event, document.getElementById('as-request-fifth-applicantEmail'));
                return;
            }

            const section = target.closest(SELECTORS.productSection);
            if (!section) return;

            if (target.matches(SELECTORS.productName)) {
                moveFocus(event, section.querySelector(SELECTORS.productSize));
            } else if (target.matches(SELECTORS.productSize)) {
                moveFocus(event, section.querySelector(SELECTORS.productColor));
            } else if (target.matches(SELECTORS.productColor)) {
                moveFocus(event, section.querySelector(SELECTORS.productOptions));
            }
        });
    }

    function moveFocus(event, next) {
        if (!next || typeof next.focus !== 'function') return;
        event.preventDefault();
        next.focus({ preventScroll: true });
        if (typeof next.select === 'function' && !next.readOnly) {
            try { next.select(); } catch (_) { /* noop */ }
        }
        next.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    }

    function bindOverviewRefreshEvents(form) {
        form.addEventListener('input', queueRefresh);
        form.addEventListener('change', queueRefresh);
        form.addEventListener('click', function (event) {
            if (event.target.closest('.as-request-fifth-remove-section-btn, #as-request-fifth-addProductBtnTop, #as-request-fifth-addProductBtnBottom')) {
                setTimeout(queueRefresh, 0);
                setTimeout(queueRefresh, 60);
            }
        });
    }

    function observeDynamicProducts(container) {
        const observer = new MutationObserver(function (mutations) {
            let shouldRefresh = false;
            for (const mutation of mutations) {
                if (mutation.type === 'childList') {
                    shouldRefresh = true;
                    break;
                }
                if (mutation.type === 'attributes') {
                    shouldRefresh = true;
                    break;
                }
            }
            if (shouldRefresh) queueRefresh();
        });

        observer.observe(container, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['style', 'class', 'disabled']
        });
    }

    function queueRefresh() {
        if (refreshQueued) return;
        refreshQueued = true;
        window.requestAnimationFrame(function () {
            refreshQueued = false;
            refreshProductUi();
        });
    }

    function refreshProductUi() {
        const sections = getLiveProductSections();
        renumberVisuals(sections);
        renderOverview(sections);
    }

    function getLiveProductSections() {
        const container = document.querySelector(SELECTORS.productContainer);
        if (!container) return [];
        return Array.from(container.querySelectorAll(SELECTORS.productSection));
    }

    function renumberVisuals(sections) {
        sections.forEach(function (section, index) {
            const visibleIndex = index + 1;
            const badge = section.querySelector(SELECTORS.productIndexBadge);
            if (badge) {
                const nextBadge = String(visibleIndex).padStart(2, '0');
                if (badge.textContent !== nextBadge) badge.textContent = nextBadge;
            }

            // 기존 asRequest.js가 지정한 제목이 있으면 번호만 정상화합니다.
            const title = section.querySelector(SELECTORS.productTitle);
            if (title) {
                const nextTitle = '제품 신청 ' + visibleIndex;
                if (title.textContent !== nextTitle) title.textContent = nextTitle;
            }

            section.dataset.asRequestVisualIndex = String(visibleIndex);
        });
    }

    function renderOverview(sections) {
        const list = document.querySelector(SELECTORS.overviewList);
        if (!list) return;

        const summaries = sections.map(buildSectionSummary);
        const fileCount = summaries.reduce(function (sum, item) { return sum + item.fileCount; }, 0);
        const readyCount = summaries.filter(function (item) { return item.ready; }).length;

        setText(SELECTORS.productCount, sections.length);
        setText(SELECTORS.overviewCount, sections.length);
        setText(SELECTORS.overviewProducts, sections.length);
        setText(SELECTORS.overviewFiles, fileCount);
        setText(SELECTORS.overviewReady, readyCount);
        setText(SELECTORS.actionSummary, '제품 ' + sections.length + '건 · 첨부 ' + fileCount + '개');

        if (!summaries.length) {
            list.innerHTML = '<div class="as-request-overview-empty">등록된 제품이 없습니다.</div>';
            return;
        }

        list.innerHTML = summaries.map(function (item, index) {
            const statusClass = item.ready ? ' as-request-overview-item-ready' : '';
            const meta = [item.category, item.subject].filter(Boolean).join(' · ') || '카테고리/증상 미선택';
            const spec = [item.size, item.color].filter(Boolean).join(' / ') || '사이즈·색상 미입력';
            return '' +
                '<button type="button" class="as-request-overview-item' + statusClass + '" data-as-request-overview-index="' + index + '">' +
                    '<span class="as-request-overview-item-number">' + String(index + 1).padStart(2, '0') + '</span>' +
                    '<span class="as-request-overview-item-main">' +
                        '<strong>' + escapeHtml(item.name || '제품명 미입력') + '</strong>' +
                        '<small>' + escapeHtml(meta) + '</small>' +
                        '<em>' + escapeHtml(spec) + '</em>' +
                    '</span>' +
                    '<span class="as-request-overview-item-side">' +
                        '<b>' + item.fileCount + '</b><small>첨부</small>' +
                    '</span>' +
                '</button>';
        }).join('');
    }

    function buildSectionSummary(section) {
        const name = readValue(section, SELECTORS.productName);
        const size = readValue(section, SELECTORS.productSize);
        const color = readValue(section, SELECTORS.productColor);
        const categorySelect = section.querySelector(SELECTORS.category);
        const subjectSelect = section.querySelector(SELECTORS.subject);
        const category = readSelectText(categorySelect);
        const subject = readSelectText(subjectSelect);
        const billingInput = section.querySelector(SELECTORS.billing);
        const billing = billingInput ? (billingInput.value === 'CUSTOMER' ? '고객 청구' : '대리점 청구') : '';
        const fileCount = countSectionFiles(section);

        return {
            name: name,
            size: size,
            color: color,
            category: category,
            subject: subject,
            billing: billing,
            fileCount: fileCount,
            // '완료'는 서버 검증을 대신하지 않는 UI 보조 지표입니다.
            ready: Boolean(name && category && subject)
        };
    }

    function countSectionFiles(section) {
        const preview = section.querySelector(SELECTORS.previewList);
        const previewCount = preview ? Array.from(preview.children).filter(function (node) {
            return node.nodeType === Node.ELEMENT_NODE;
        }).length : 0;

        const input = section.querySelector(SELECTORS.fileInput);
        const inputCount = input && input.files ? input.files.length : 0;

        return Math.max(previewCount, inputCount);
    }

    function readValue(root, selector) {
        const element = root.querySelector(selector);
        return element && 'value' in element ? String(element.value || '').trim() : '';
    }

    function readSelectText(select) {
        if (!select || !select.value) return '';
        const option = select.options && select.selectedIndex >= 0 ? select.options[select.selectedIndex] : null;
        return option ? String(option.textContent || '').trim() : String(select.value || '').trim();
    }

    function bindOverviewNavigation() {
        document.addEventListener('click', function (event) {
            const button = event.target.closest('[data-as-request-overview-index]');
            if (!button) return;

            const index = Number(button.dataset.asRequestOverviewIndex);
            const sections = getLiveProductSections();
            const section = sections[index];
            if (!section) return;

            section.classList.remove('as-request-highlight-product');
            void section.offsetWidth;
            section.classList.add('as-request-highlight-product');
            section.scrollIntoView({ behavior: 'smooth', block: 'center' });

            window.setTimeout(function () {
                const firstInput = section.querySelector(SELECTORS.productName);
                if (firstInput && typeof firstInput.focus === 'function') {
                    firstInput.focus({ preventScroll: true });
                }
            }, 380);
        });
    }

    function setText(selector, value) {
        const element = document.querySelector(selector);
        if (element) element.textContent = String(value);
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }
})();
