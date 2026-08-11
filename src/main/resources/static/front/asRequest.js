(function () {
    'use strict';

    const SUBJECT_MAP = {
        '상부장': [
            '도어 파손', '도어 스크레치', '도어 휘어짐', '도어 변색', '도어 단차 불량',
            '도어 마감 불량', '손잡이 불량', '바디 변색', '바디 스크래치', '바디 파손',
            '개폐 불량', '경첩 불량', 'LED 점등 불량', '오출고', '기타 사유'
        ],
        '슬라이드장': [
            '도어 파손', '도어 스크레치', '도어 변색', '도어 간격 불량', '바디 변색',
            '바디 스크레치', '바디 파손', '개폐불량', '댐퍼불량', '손잡이 불량',
            'LED 점등 불량', '오출고', '기타 사유'
        ],
        '플랩장': [
            '도어 파손', '도어 스크레치', '도어 변색', '도어 단차 불량', '유압 불량',
            '바디 변색', '바디 스크래치', '바디 파손', '개폐 불량', '경첩 불량',
            'LED 점등 불량', '오출고', '기타 사유'
        ],
        '하부장': [
            '도어 단차 불량', '서랍 개폐불량', '도어 마감 불량', '오출고', '기타 사유'
        ],
        '거울': [
            '테두리 도장 불량', '유리 스크레치', '유리 파손', '유리 변색',
            'LED 점등 불량', '오출고', '기타 사유'
        ]
    };

    const SUPPORTED_IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'heic', 'heif'];
    const SUPPORTED_VIDEO_EXTENSIONS = ['mp4', 'mov', 'avi', 'm4v', 'wmv', 'webm', 'mkv'];

    const state = {
        submitting: false,
        toastTimer: null
    };

    document.addEventListener('DOMContentLoaded', init);

    function init() {
        const ctx = collectContext();
        if (!ctx.root || !ctx.form || !ctx.template || !ctx.sections) {
            return;
        }

        bindPhoneFormatter(ctx.onsiteContact);
        bindPhoneFormatter(ctx.applicantPhone);
        bindCommonEvents(ctx);

        if (getSections(ctx).length === 0) {
            addProductSection(ctx, { scroll: false, collapseOthers: false });
        }

        refreshAll(ctx);
    }

    function collectContext() {
        return {
            root: document.getElementById('as-request-root'),
            form: document.getElementById('as-request-form'),
            template: document.getElementById('as-request-product-template'),
            sections: document.getElementById('as-request-product-sections'),
            addTop: document.getElementById('as-request-add-product'),
            addBottom: document.getElementById('as-request-add-product-bottom'),
            submit: document.getElementById('as-request-submit'),
            productCount: document.getElementById('as-request-product-count'),
            overviewCount: document.getElementById('as-request-overview-count'),
            statProducts: document.getElementById('as-request-stat-products'),
            statFiles: document.getElementById('as-request-stat-files'),
            statReady: document.getElementById('as-request-stat-ready'),
            overviewList: document.getElementById('as-request-overview-list'),
            submitSummary: document.getElementById('as-request-submit-summary'),
            progress: document.getElementById('as-request-progress'),
            progressText: document.getElementById('as-request-progress-text'),
            progressSubtext: document.getElementById('as-request-progress-subtext'),
            progressPercent: document.getElementById('as-request-progress-percent'),
            progressBar: document.getElementById('as-request-progress-bar'),
            toast: document.getElementById('as-request-toast'),

            sameAddress: document.getElementById('as-request-same-address'),
            sameMember: document.getElementById('as-request-same-member-info'),
            customerName: document.getElementById('as-request-customer-name'),
            onsiteContact: document.getElementById('as-request-onsite-contact'),
            roadAddress: document.getElementById('as-request-road-address'),
            detailAddress: document.getElementById('as-request-detail-address'),
            doName: document.getElementById('as-request-do-name'),
            siName: document.getElementById('as-request-si-name'),
            guName: document.getElementById('as-request-gu-name'),
            zipCode: document.getElementById('as-request-zip-code'),
            searchAddress: document.getElementById('as-request-search-address'),
            applicantName: document.getElementById('as-request-applicant-name'),
            applicantPhone: document.getElementById('as-request-applicant-phone'),
            applicantEmail: document.getElementById('as-request-applicant-email'),

            companyAddress: window.companyAddress || {},
            loginMemberInfo: window.loginMemberInfo || {}
        };
    }

    function bindCommonEvents(ctx) {
        ctx.addTop?.addEventListener('click', function () {
            addProductSection(ctx, { scroll: true, collapseOthers: true });
        });

        ctx.addBottom?.addEventListener('click', function () {
            addProductSection(ctx, { scroll: true, collapseOthers: true });
        });

        ctx.sameAddress?.addEventListener('change', function () {
            if (ctx.sameAddress.checked) {
                applyCompanyAddress(ctx);
            } else {
                clearCompanyAddress(ctx);
            }
            clearFieldError(ctx.roadAddress);
            refreshAll(ctx);
        });

        ctx.sameMember?.addEventListener('change', function () {
            if (ctx.sameMember.checked) {
                applyMemberInfo(ctx);
            } else {
                clearMemberInfo(ctx);
            }
            [ctx.applicantName, ctx.applicantPhone, ctx.applicantEmail].forEach(clearFieldError);
            refreshAll(ctx);
        });

        ctx.searchAddress?.addEventListener('click', function () {
            if (ctx.searchAddress.disabled || state.submitting) return;
            openAddressSearch(ctx);
        });

        ctx.form.addEventListener('keydown', function (event) {
            handleEnterNavigation(event);
        });

        ctx.form.addEventListener('input', function (event) {
            const target = event.target;
            if (target instanceof HTMLElement) {
                clearFieldError(target);
            }
            refreshAll(ctx);
        });

        ctx.form.addEventListener('change', function (event) {
            const target = event.target;
            if (target instanceof HTMLElement) {
                clearFieldError(target);
            }
            refreshAll(ctx);
        });

        ctx.form.addEventListener('submit', function (event) {
            event.preventDefault();
            submitAll(ctx);
        });
    }

    function addProductSection(ctx, options) {
        if (state.submitting) return;

        const fragment = ctx.template.content.cloneNode(true);
        const section = fragment.querySelector('.as-request-product-card');
        if (!section) return;

        if (options?.collapseOthers) {
            getSections(ctx).forEach(collapseSection);
        }

        ctx.sections.appendChild(fragment);
        bindProductSection(ctx, section);
        updateSectionIndexes(ctx);
        refreshAll(ctx);

        if (options?.scroll) {
            requestAnimationFrame(function () {
                expandSection(section);
                scrollToSection(section);
                const first = section.querySelector('.as-request-product-name');
                if (first) first.focus({ preventScroll: true });
            });
        }
    }

    function bindProductSection(ctx, section) {
        const toggle = section.querySelector('.as-request-product-toggle');
        const remove = section.querySelector('.as-request-remove-product');
        const category = section.querySelector('.as-request-subject-category');
        const subject = section.querySelector('.as-request-subject');
        const fileInput = section.querySelector('.as-request-attachments');
        const dropzone = section.querySelector('.as-request-dropzone');

        resetSubjectSelect(subject);

        toggle?.addEventListener('click', function () {
            if (section.classList.contains('as-request-is-collapsed')) {
                expandSection(section);
            } else {
                collapseSection(section);
            }
        });

        remove?.addEventListener('click', function () {
            removeProductSection(ctx, section);
        });

        category?.addEventListener('change', function () {
            fillSubjectSelect(category.value, subject);
            clearFieldError(category);
            clearFieldError(subject);
            refreshAll(ctx);
        });

        dropzone?.addEventListener('click', function () {
            if (state.submitting || isSubmitted(section)) return;
            fileInput?.click();
        });

        fileInput?.addEventListener('change', function () {
            const files = Array.from(fileInput.files || []);
            if (!validateAttachmentTypes(ctx, files)) {
                fileInput.value = '';
            }
            renderAttachmentPreview(ctx, section);
            clearFieldError(fileInput);
            refreshAll(ctx);
        });

        if (dropzone && fileInput) {
            ['dragenter', 'dragover'].forEach(function (name) {
                dropzone.addEventListener(name, function (event) {
                    event.preventDefault();
                    if (state.submitting || isSubmitted(section)) return;
                    dropzone.classList.add('as-request-is-dragover');
                });
            });

            ['dragleave', 'drop'].forEach(function (name) {
                dropzone.addEventListener(name, function (event) {
                    event.preventDefault();
                    dropzone.classList.remove('as-request-is-dragover');
                });
            });

            dropzone.addEventListener('drop', function (event) {
                if (state.submitting || isSubmitted(section)) return;
                const files = Array.from(event.dataTransfer?.files || []);
                if (!files.length) return;
                if (!validateAttachmentTypes(ctx, files)) return;
                mergeFiles(fileInput, files);
                renderAttachmentPreview(ctx, section);
                clearFieldError(fileInput);
                refreshAll(ctx);
            });
        }

        section.addEventListener('input', function () {
            refreshSectionHeader(section);
        });
        section.addEventListener('change', function () {
            refreshSectionHeader(section);
        });
    }

    function removeProductSection(ctx, section) {
        if (state.submitting || isSubmitted(section)) return;

        const sections = getSections(ctx);
        if (sections.length <= 1) {
            showToast(ctx, '최소 1개의 제품 신청은 남아 있어야 합니다.', 'info');
            return;
        }

        revokeSectionPreviewUrls(section);
        section.remove();
        updateSectionIndexes(ctx);
        refreshAll(ctx);
    }

    function updateSectionIndexes(ctx) {
        const sections = getSections(ctx);

        sections.forEach(function (section, index) {
            const displayIndex = index + 1;
            section.dataset.asRequestIndex = String(displayIndex);

            const number = section.querySelector('.as-request-product-index');
            const title = section.querySelector('.as-request-product-title');
            const remove = section.querySelector('.as-request-remove-product');
            const radios = section.querySelectorAll('.as-request-billing-target');

            if (number) number.textContent = String(displayIndex).padStart(2, '0');
            if (title) title.textContent = '제품 신청 ' + displayIndex;
            if (remove) remove.hidden = sections.length <= 1 || isSubmitted(section);

            radios.forEach(function (radio) {
                radio.name = 'as-request-billing-target-' + displayIndex;
            });
        });
    }

    function refreshAll(ctx) {
        updateSectionIndexes(ctx);
        const sections = getSections(ctx);
        sections.forEach(refreshSectionHeader);

        const totalFiles = sections.reduce(function (sum, section) {
            return sum + getSectionFiles(section).length;
        }, 0);

        const readyCount = sections.filter(isSectionReady).length;

        if (ctx.productCount) ctx.productCount.textContent = String(sections.length);
        if (ctx.overviewCount) ctx.overviewCount.textContent = String(sections.length);
        if (ctx.statProducts) ctx.statProducts.textContent = String(sections.length);
        if (ctx.statFiles) ctx.statFiles.textContent = String(totalFiles);
        if (ctx.statReady) ctx.statReady.textContent = String(readyCount);
        if (ctx.submitSummary) ctx.submitSummary.textContent = '제품 ' + sections.length + '건 · 첨부 ' + totalFiles + '개 · 입력완료 ' + readyCount + '건';

        renderOverview(ctx, sections);
    }

    function refreshSectionHeader(section) {
        if (!section) return;
        const name = safeTrim(section.querySelector('.as-request-product-name')?.value) || '제품명 미입력';
        const category = safeTrim(section.querySelector('.as-request-subject-category')?.value) || '카테고리 미선택';
        const files = getSectionFiles(section).length;

        const nameEl = section.querySelector('.as-request-product-summary-name');
        const categoryEl = section.querySelector('.as-request-product-summary-category');
        const filesEl = section.querySelector('.as-request-product-summary-files');
        const countEl = section.querySelector('.as-request-file-count');
        const stateEl = section.querySelector('.as-request-product-state');

        if (nameEl) nameEl.textContent = name;
        if (categoryEl) categoryEl.textContent = category;
        if (filesEl) filesEl.textContent = '첨부 ' + files + '개';
        if (countEl) countEl.textContent = files + '개';

        if (stateEl) {
            if (isSubmitted(section)) {
                stateEl.textContent = '접수 완료';
            } else if (isSectionReady(section)) {
                stateEl.textContent = '입력 완료';
            } else {
                stateEl.textContent = '작성 중';
            }
        }
    }

    function renderOverview(ctx, sections) {
        if (!ctx.overviewList) return;
        ctx.overviewList.innerHTML = '';

        sections.forEach(function (section, index) {
            const productName = safeTrim(section.querySelector('.as-request-product-name')?.value) || '제품명 미입력';
            const category = safeTrim(section.querySelector('.as-request-subject-category')?.value) || '카테고리 미선택';
            const subject = safeTrim(section.querySelector('.as-request-subject')?.value);
            const size = safeTrim(section.querySelector('.as-request-product-size')?.value) || '사이즈 미입력';
            const color = safeTrim(section.querySelector('.as-request-product-color')?.value) || '색상 미입력';
            const files = getSectionFiles(section).length;

            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'as-request-overview-item';
            if (isSubmitted(section)) {
                button.classList.add('as-request-overview-submitted');
            } else if (isSectionReady(section)) {
                button.classList.add('as-request-overview-ready');
            }

            const indexEl = document.createElement('span');
            indexEl.className = 'as-request-overview-item-index';
            indexEl.textContent = String(index + 1).padStart(2, '0');

            const main = document.createElement('span');
            main.className = 'as-request-overview-item-main';
            const strong = document.createElement('strong');
            strong.textContent = productName;
            const sub = document.createElement('span');
            sub.textContent = category + (subject ? ' · ' + stripCategoryPrefix(subject) : '') + ' · ' + size + ' / ' + color;
            main.append(strong, sub);

            const side = document.createElement('span');
            side.className = 'as-request-overview-item-side';
            const fileLine = document.createElement('span');
            fileLine.textContent = '첨부 ' + files;
            const stateLine = document.createElement('span');
            stateLine.textContent = isSubmitted(section) ? '접수완료' : (isSectionReady(section) ? '완료' : '작성중');
            side.append(fileLine, stateLine);

            button.append(indexEl, main, side);
            button.addEventListener('click', function () {
                expandSection(section);
                scrollToSection(section);
            });
            ctx.overviewList.appendChild(button);
        });
    }

    function stripCategoryPrefix(subject) {
        const index = String(subject || '').indexOf(' - ');
        return index >= 0 ? String(subject).substring(index + 3) : String(subject || '');
    }

    function isSectionReady(section) {
        if (!section) return false;
        return Boolean(
            safeTrim(section.querySelector('.as-request-product-name')?.value) &&
            safeTrim(section.querySelector('.as-request-product-size')?.value) &&
            safeTrim(section.querySelector('.as-request-product-color')?.value) &&
            safeTrim(section.querySelector('.as-request-product-options')?.value) &&
            section.querySelector('.as-request-billing-target:checked') &&
            safeTrim(section.querySelector('.as-request-subject-category')?.value) &&
            safeTrim(section.querySelector('.as-request-subject')?.value) &&
            getSectionFiles(section).length > 0
        );
    }

    function collapseSection(section) {
        if (!section) return;
        section.classList.add('as-request-is-collapsed');
        const toggle = section.querySelector('.as-request-product-toggle');
        if (toggle) toggle.setAttribute('aria-expanded', 'false');
    }

    function expandSection(section) {
        if (!section) return;
        section.classList.remove('as-request-is-collapsed');
        const toggle = section.querySelector('.as-request-product-toggle');
        if (toggle) toggle.setAttribute('aria-expanded', 'true');
    }

    function scrollToSection(section) {
        if (!section) return;
        section.classList.remove('as-request-is-active');
        void section.offsetWidth;
        section.classList.add('as-request-is-active');
        section.scrollIntoView({ behavior: 'smooth', block: 'center' });
        window.setTimeout(function () {
            section.classList.remove('as-request-is-active');
        }, 1200);
    }

    function resetSubjectSelect(select) {
        if (!select) return;
        select.innerHTML = '<option value="">카테고리를 먼저 선택해 주세요.</option>';
        select.disabled = true;
    }

    function fillSubjectSelect(category, select) {
        if (!select) return;
        select.innerHTML = '';

        if (!category || !SUBJECT_MAP[category]) {
            resetSubjectSelect(select);
            return;
        }

        const first = document.createElement('option');
        first.value = '';
        first.textContent = '증상 선택';
        select.appendChild(first);

        Array.from(new Set(SUBJECT_MAP[category])).forEach(function (symptom) {
            const option = document.createElement('option');
            option.value = category + ' - ' + symptom;
            option.textContent = symptom;
            select.appendChild(option);
        });
        select.disabled = false;
    }

    function bindPhoneFormatter(input) {
        if (!input) return;
        input.addEventListener('input', function () {
            input.value = formatKoreanPhone(input.value);
        });
    }

    function onlyDigits(value) {
        return String(value == null ? '' : value).replace(/\D/g, '');
    }

    function formatKoreanPhone(value) {
        let digits = onlyDigits(value);
        if (digits.length > 11) digits = digits.slice(0, 11);
        if (digits.length <= 3) return digits;

        if (digits.startsWith('02')) {
            if (digits.length <= 5) return digits.slice(0, 2) + '-' + digits.slice(2);
            if (digits.length === 9) return digits.slice(0, 2) + '-' + digits.slice(2, 5) + '-' + digits.slice(5);
            if (digits.length >= 10) return digits.slice(0, 2) + '-' + digits.slice(2, 6) + '-' + digits.slice(6, 10);
        }
        if (digits.length === 10) return digits.slice(0, 3) + '-' + digits.slice(3, 6) + '-' + digits.slice(6);
        if (digits.length >= 11) return digits.slice(0, 3) + '-' + digits.slice(3, 7) + '-' + digits.slice(7, 11);
        return digits.slice(0, 3) + '-' + digits.slice(3);
    }

    function isValidPhone(value) {
        const digits = onlyDigits(value);
        return digits.startsWith('0') && digits.length >= 9 && digits.length <= 11;
    }

    function isValidEmail(value) {
        const email = safeTrim(value);
        if (!email) return true;
        return /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$/.test(email);
    }

    function safeTrim(value) {
        return String(value == null ? '' : value).trim();
    }

    function applyCompanyAddress(ctx) {
        ctx.roadAddress.value = ctx.companyAddress.main || '';
        ctx.detailAddress.value = ctx.companyAddress.detail || '';
        ctx.doName.value = ctx.companyAddress.doName || '';
        ctx.siName.value = ctx.companyAddress.siName || '';
        ctx.guName.value = ctx.companyAddress.guName || '';
        ctx.zipCode.value = ctx.companyAddress.zipCode || '';
        ctx.searchAddress.disabled = true;
    }

    function clearCompanyAddress(ctx) {
        ctx.roadAddress.value = '';
        ctx.detailAddress.value = '';
        ctx.doName.value = '';
        ctx.siName.value = '';
        ctx.guName.value = '';
        ctx.zipCode.value = '';
        ctx.searchAddress.disabled = false;
    }

    function applyMemberInfo(ctx) {
        ctx.applicantName.value = ctx.loginMemberInfo.name || '';
        ctx.applicantPhone.value = formatKoreanPhone(ctx.loginMemberInfo.phone || '');
        ctx.applicantEmail.value = ctx.loginMemberInfo.email || '';
    }

    function clearMemberInfo(ctx) {
        ctx.applicantName.value = '';
        ctx.applicantPhone.value = '';
        ctx.applicantEmail.value = '';
    }

    function openAddressSearch(ctx) {
        if (!window.daum || !window.daum.Postcode) {
            showToast(ctx, '주소 검색 모듈을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', 'error');
            return;
        }

        new window.daum.Postcode({
            oncomplete: function (data) {
                const fullRoadAddr = data.roadAddress || data.address || '';
                const sido = data.sido || '';
                const sigungu = safeTrim(data.sigungu || '');
                let siName = '';
                let guName = '';

                if (sigungu) {
                    const parts = sigungu.split(/\s+/).filter(Boolean);
                    if (parts.length >= 2) {
                        siName = parts[0] || '';
                        guName = parts.slice(1).join(' ');
                    } else if (parts[0] && (parts[0].endsWith('시') || parts[0].endsWith('군'))) {
                        siName = parts[0];
                    } else {
                        guName = parts[0] || '';
                    }
                }

                ctx.roadAddress.value = fullRoadAddr;
                ctx.zipCode.value = data.zonecode || '';
                ctx.doName.value = sido || (fullRoadAddr.split(' ')[0] || '');
                ctx.siName.value = siName;
                ctx.guName.value = guName;
                ctx.detailAddress.focus();
                clearFieldError(ctx.roadAddress);
                refreshAll(ctx);
            }
        }).open();
    }

    function handleEnterNavigation(event) {
        if (event.key !== 'Enter' || event.isComposing) return;
        const target = event.target;
        if (!(target instanceof HTMLElement)) return;

        if (target.matches('textarea, select, button, [type="date"], [type="file"], [type="checkbox"], [type="radio"], [readonly]')) {
            return;
        }

        const explicitId = target.dataset.asRequestEnter;
        if (explicitId) {
            moveFocus(event, document.getElementById(explicitId));
            return;
        }

        const section = target.closest('.as-request-product-card');
        if (!section) return;

        const nextRole = target.dataset.asRequestNext;
        if (!nextRole) return;

        const selectorMap = {
            size: '.as-request-product-size',
            color: '.as-request-product-color',
            options: '.as-request-product-options'
        };
        moveFocus(event, section.querySelector(selectorMap[nextRole] || ''));
    }

    function moveFocus(event, next) {
        if (!next || typeof next.focus !== 'function') return;
        event.preventDefault();
        next.focus({ preventScroll: true });
        next.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function getSections(ctx) {
        return Array.from(ctx.sections.querySelectorAll('.as-request-product-card'));
    }

    function isSubmitted(section) {
        return section?.dataset.asRequestSubmitted === 'true';
    }

    function getSectionFiles(section) {
        const input = section?.querySelector('.as-request-attachments');
        return Array.from(input?.files || []);
    }

    function getFileExtension(filename) {
        const name = String(filename || '');
        const index = name.lastIndexOf('.');
        return index >= 0 ? name.substring(index + 1).toLowerCase() : '';
    }

    function isImageFile(file) {
        const type = String(file?.type || '').toLowerCase();
        return type.startsWith('image/') || SUPPORTED_IMAGE_EXTENSIONS.includes(getFileExtension(file?.name));
    }

    function isVideoFile(file) {
        const type = String(file?.type || '').toLowerCase();
        return type.startsWith('video/') || SUPPORTED_VIDEO_EXTENSIONS.includes(getFileExtension(file?.name));
    }

    function isSupportedAttachment(file) {
        return isImageFile(file) || isVideoFile(file);
    }

    function validateAttachmentTypes(ctx, files) {
        const invalid = (files || []).find(function (file) {
            return !isSupportedAttachment(file);
        });
        if (!invalid) return true;
        showToast(ctx, '이미지 또는 동영상 파일만 첨부할 수 있습니다: ' + invalid.name, 'error');
        return false;
    }

    function mergeFiles(input, incomingFiles) {
        if (!input || typeof DataTransfer === 'undefined') return;
        const dt = new DataTransfer();
        const seen = new Set();

        [...Array.from(input.files || []), ...incomingFiles].forEach(function (file) {
            const key = [file.name, file.size, file.lastModified].join('|');
            if (seen.has(key)) return;
            seen.add(key);
            dt.items.add(file);
        });
        input.files = dt.files;
    }

    function removeFileAt(input, index) {
        if (!input || typeof DataTransfer === 'undefined') return;
        const dt = new DataTransfer();
        Array.from(input.files || []).forEach(function (file, currentIndex) {
            if (currentIndex !== index) dt.items.add(file);
        });
        input.files = dt.files;
    }

    function revokeSectionPreviewUrls(section) {
        section?.querySelectorAll('[data-as-request-object-url]').forEach(function (element) {
            const url = element.dataset.asRequestObjectUrl;
            if (url) URL.revokeObjectURL(url);
        });
    }

    function renderAttachmentPreview(ctx, section) {
        const input = section.querySelector('.as-request-attachments');
        const area = section.querySelector('.as-request-preview-area');
        const list = section.querySelector('.as-request-preview-list');
        if (!input || !area || !list) return;

        revokeSectionPreviewUrls(section);
        list.innerHTML = '';
        const files = Array.from(input.files || []);
        area.hidden = files.length === 0;

        files.forEach(function (file, index) {
            const item = document.createElement('div');
            item.className = 'as-request-preview-item';

            if (isImageFile(file)) {
                const url = URL.createObjectURL(file);
                const image = document.createElement('img');
                image.src = url;
                image.alt = file.name;
                image.dataset.asRequestObjectUrl = url;
                item.appendChild(image);
            } else if (isVideoFile(file)) {
                const url = URL.createObjectURL(file);
                const video = document.createElement('video');
                video.src = url;
                video.controls = true;
                video.muted = true;
                video.playsInline = true;
                video.preload = 'metadata';
                video.dataset.asRequestObjectUrl = url;
                item.appendChild(video);
            } else {
                const box = document.createElement('div');
                box.className = 'as-request-preview-file';
                box.textContent = file.name;
                item.appendChild(box);
            }

            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'as-request-preview-remove';
            remove.setAttribute('aria-label', file.name + ' 첨부 제외');
            remove.textContent = '×';
            remove.addEventListener('click', function () {
                if (state.submitting || isSubmitted(section)) return;
                removeFileAt(input, index);
                renderAttachmentPreview(ctx, section);
                refreshAll(ctx);
            });
            item.appendChild(remove);
            list.appendChild(item);
        });

        refreshSectionHeader(section);
    }

    function validateCommon(ctx) {
        let valid = true;

        ctx.customerName.value = safeTrim(ctx.customerName.value);
        ctx.onsiteContact.value = formatKoreanPhone(ctx.onsiteContact.value);
        ctx.applicantName.value = safeTrim(ctx.applicantName.value);
        ctx.applicantPhone.value = formatKoreanPhone(ctx.applicantPhone.value);
        ctx.applicantEmail.value = safeTrim(ctx.applicantEmail.value);
        ctx.detailAddress.value = safeTrim(ctx.detailAddress.value);

        if (!ctx.customerName.value) valid = setFieldError(ctx.customerName, '고객 성함을 입력해 주세요.') && valid;
        if (!isValidPhone(ctx.onsiteContact.value)) valid = setFieldError(ctx.onsiteContact, '현장 연락처를 확인해 주세요.') && valid;
        if (!safeTrim(ctx.roadAddress.value)) valid = setFieldError(ctx.roadAddress, 'AS 장소 주소를 검색해 주세요.') && valid;
        if (!ctx.applicantName.value) valid = setFieldError(ctx.applicantName, '접수자 이름을 입력해 주세요.') && valid;
        if (!isValidPhone(ctx.applicantPhone.value)) valid = setFieldError(ctx.applicantPhone, '접수자 연락처를 확인해 주세요.') && valid;
        if (!isValidEmail(ctx.applicantEmail.value)) valid = setFieldError(ctx.applicantEmail, '이메일 형식을 확인해 주세요.') && valid;

        return valid;
    }

    function validateSection(section) {
        if (isSubmitted(section)) return true;
        let valid = true;

        const name = section.querySelector('.as-request-product-name');
        const size = section.querySelector('.as-request-product-size');
        const color = section.querySelector('.as-request-product-color');
        const options = section.querySelector('.as-request-product-options');
        const category = section.querySelector('.as-request-subject-category');
        const subject = section.querySelector('.as-request-subject');
        const billing = section.querySelector('.as-request-billing-target:checked');
        const fileInput = section.querySelector('.as-request-attachments');

        [name, size, color, options].forEach(function (input) {
            if (input) input.value = safeTrim(input.value);
        });

        if (!name?.value) valid = setFieldError(name, '제품명을 입력해 주세요.') && valid;
        if (!size?.value) valid = setFieldError(size, '사이즈를 입력해 주세요.') && valid;
        if (!color?.value) valid = setFieldError(color, '색상을 입력해 주세요.') && valid;
        if (!options?.value) valid = setFieldError(options, '옵션을 입력해 주세요. 없으면 없음으로 입력해 주세요.') && valid;
        if (!billing) valid = setFieldError(section.querySelector('.as-request-billing-target'), '비용 청구 주체를 선택해 주세요.') && valid;
        if (!safeTrim(category?.value)) valid = setFieldError(category, '카테고리를 선택해 주세요.') && valid;
        if (!safeTrim(subject?.value)) valid = setFieldError(subject, '증상을 선택해 주세요.') && valid;

        const files = Array.from(fileInput?.files || []);
        if (!files.length) valid = setFieldError(fileInput, '사진 또는 동영상을 1개 이상 첨부해 주세요.') && valid;
        if (files.length && files.some(function (file) { return !isSupportedAttachment(file); })) {
            valid = setFieldError(fileInput, '지원하지 않는 첨부파일이 포함되어 있습니다.') && valid;
        }

        return valid;
    }

    function setFieldError(target, message) {
        const field = target?.closest?.('.as-request-field');
        if (!field) return false;
        field.classList.add('as-request-is-invalid');
        const error = field.querySelector('.as-request-field-error');
        if (error) error.textContent = message;
        return false;
    }

    function clearFieldError(target) {
        const field = target?.closest?.('.as-request-field');
        if (!field) return;
        field.classList.remove('as-request-is-invalid');
        const error = field.querySelector('.as-request-field-error');
        if (error) error.textContent = '';
    }

    function focusFirstInvalid(ctx) {
        const invalid = ctx.root.querySelector('.as-request-field.as-request-is-invalid');
        if (!invalid) return;
        const section = invalid.closest('.as-request-product-card');
        if (section) expandSection(section);
        const control = invalid.querySelector('input:not([type="hidden"]), select, textarea, button');
        invalid.scrollIntoView({ behavior: 'smooth', block: 'center' });
        window.setTimeout(function () {
            if (control && typeof control.focus === 'function') control.focus({ preventScroll: true });
        }, 260);
    }

    function buildCommonValues(ctx) {
        return {
            customerName: safeTrim(ctx.customerName.value),
            roadAddress: safeTrim(ctx.roadAddress.value),
            detailAddress: safeTrim(ctx.detailAddress.value),
            doName: safeTrim(ctx.doName.value),
            siName: safeTrim(ctx.siName.value),
            guName: safeTrim(ctx.guName.value),
            zipCode: safeTrim(ctx.zipCode.value),
            onsiteContact: safeTrim(ctx.onsiteContact.value),
            applicantName: safeTrim(ctx.applicantName.value),
            applicantPhone: safeTrim(ctx.applicantPhone.value),
            applicantEmail: safeTrim(ctx.applicantEmail.value)
        };
    }

    function buildSectionFormData(section, common) {
        const data = new FormData();
        Object.entries(common).forEach(function ([key, value]) {
            data.append(key, value || '');
        });

        const billing = section.querySelector('.as-request-billing-target:checked');
        data.append('purchaseDate', section.querySelector('.as-request-purchase-date')?.value || '');
        data.append('billingTarget', billing?.value || '');
        data.append('productName', safeTrim(section.querySelector('.as-request-product-name')?.value));
        data.append('productSize', safeTrim(section.querySelector('.as-request-product-size')?.value));
        data.append('productColor', safeTrim(section.querySelector('.as-request-product-color')?.value));
        data.append('productOptions', safeTrim(section.querySelector('.as-request-product-options')?.value));
        data.append('subject', safeTrim(section.querySelector('.as-request-subject')?.value));
        data.append('reason', safeTrim(section.querySelector('.as-request-reason')?.value));

        getSectionFiles(section).forEach(function (file) {
            data.append('attachments', file);
        });
        return data;
    }

    function getSectionBytes(section) {
        return getSectionFiles(section).reduce(function (sum, file) {
            return sum + (file.size || 0);
        }, 0);
    }

    async function submitAll(ctx) {
        if (state.submitting) return;

        ctx.root.querySelectorAll('.as-request-is-invalid').forEach(function (field) {
            field.classList.remove('as-request-is-invalid');
            const error = field.querySelector('.as-request-field-error');
            if (error) error.textContent = '';
        });

        const commonValid = validateCommon(ctx);
        const pendingSections = getSections(ctx).filter(function (section) {
            return !isSubmitted(section);
        });

        let sectionValid = true;
        pendingSections.forEach(function (section) {
            if (!validateSection(section)) sectionValid = false;
        });

        if (!commonValid || !sectionValid) {
            focusFirstInvalid(ctx);
            showToast(ctx, '필수 입력 항목을 확인해 주세요.', 'error');
            refreshAll(ctx);
            return;
        }

        if (!pendingSections.length) {
            showToast(ctx, '모든 제품이 이미 접수 완료되었습니다.', 'info');
            return;
        }

        const common = buildCommonValues(ctx);
        const totalBytes = Math.max(1, pendingSections.reduce(function (sum, section) {
            return sum + getSectionBytes(section);
        }, 0));

        state.submitting = true;
        setSubmittingState(ctx, true);
        showProgress(ctx);

        let uploadedBaseBytes = 0;
        let lastRedirectUrl = '/customer/asList';

        try {
            for (let index = 0; index < pendingSections.length; index++) {
                const section = pendingSections[index];
                expandSection(section);
                const sectionBytes = getSectionBytes(section);
                const formData = buildSectionFormData(section, common);

                const result = await uploadSingleSection(ctx, formData, {
                    sectionOrder: index + 1,
                    totalSections: pendingSections.length,
                    uploadedBaseBytes,
                    sectionBytes,
                    totalBytes
                });

                uploadedBaseBytes += sectionBytes;
                markSectionSubmitted(section);
                collapseSection(section);
                if (result?.redirectUrl) lastRedirectUrl = result.redirectUrl;
                refreshAll(ctx);
            }

            setProgress(ctx, 100, '모든 AS 신청이 완료되었습니다.', '접수 내역으로 이동합니다.');
            showToast(ctx, pendingSections.length + '건의 AS 신청이 정상적으로 접수되었습니다.', 'success');
            window.setTimeout(function () {
                window.location.href = lastRedirectUrl;
            }, 650);
        } catch (error) {
            state.submitting = false;
            setSubmittingState(ctx, false);
            setProgress(ctx, 0, '업로드가 중단되었습니다.', '접수 완료된 제품은 유지되고 미완료 제품만 다시 신청할 수 있습니다.');
            showToast(ctx, error?.message || 'AS 신청 중 오류가 발생했습니다.', 'error');
            refreshAll(ctx);
        }
    }

    function uploadSingleSection(ctx, formData, meta) {
        return new Promise(function (resolve, reject) {
            const xhr = new XMLHttpRequest();
            xhr.open('POST', '/customer/asSubmit');
            xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');

            xhr.upload.addEventListener('progress', function (event) {
                let percent = Math.round(((meta.sectionOrder - 1) / meta.totalSections) * 100);
                if (event.lengthComputable && meta.totalBytes > 0) {
                    percent = Math.round(((meta.uploadedBaseBytes + event.loaded) / meta.totalBytes) * 100);
                }
                percent = Math.max(0, Math.min(99, percent));
                setProgress(
                    ctx,
                    percent,
                    meta.sectionOrder + ' / ' + meta.totalSections + '번째 제품 업로드 중',
                    '첨부파일을 서버에 전송하고 있습니다.'
                );
            });

            xhr.onload = function () {
                let data = null;
                try {
                    data = JSON.parse(xhr.responseText);
                } catch (_) {
                    data = null;
                }

                if (xhr.status >= 200 && xhr.status < 300 && data?.success) {
                    const completed = Math.round(((meta.uploadedBaseBytes + meta.sectionBytes) / meta.totalBytes) * 100);
                    setProgress(
                        ctx,
                        Math.min(100, completed),
                        meta.sectionOrder + ' / ' + meta.totalSections + '번째 제품 접수 완료',
                        '다음 제품을 계속 처리합니다.'
                    );
                    resolve(data);
                    return;
                }

                reject(new Error(data?.message || (meta.sectionOrder + '번째 제품 신청 중 오류가 발생했습니다.')));
            };

            xhr.onerror = function () {
                reject(new Error(meta.sectionOrder + '번째 제품 신청 중 네트워크 오류가 발생했습니다.'));
            };

            xhr.send(formData);
        });
    }

    function markSectionSubmitted(section) {
        section.dataset.asRequestSubmitted = 'true';
        section.classList.add('as-request-is-submitted');
        section.querySelectorAll('input, select, textarea').forEach(function (element) {
            element.disabled = true;
        });
        const dropzone = section.querySelector('.as-request-dropzone');
        const remove = section.querySelector('.as-request-remove-product');
        if (dropzone) dropzone.disabled = true;
        if (remove) remove.hidden = true;
        refreshSectionHeader(section);
    }

    function setSubmittingState(ctx, disabled) {
        ctx.submit.disabled = disabled;
        ctx.addTop.disabled = disabled;
        ctx.addBottom.disabled = disabled;
        ctx.sameAddress.disabled = disabled;
        ctx.sameMember.disabled = disabled;
        ctx.searchAddress.disabled = disabled || Boolean(ctx.sameAddress.checked);

        getSections(ctx).forEach(function (section) {
            if (isSubmitted(section)) return;
            section.querySelectorAll('input, select, textarea, .as-request-dropzone, .as-request-remove-product').forEach(function (element) {
                element.disabled = disabled;
            });
        });

        [ctx.customerName, ctx.onsiteContact, ctx.detailAddress, ctx.applicantName, ctx.applicantPhone, ctx.applicantEmail].forEach(function (element) {
            element.disabled = disabled;
        });
    }

    function showProgress(ctx) {
        ctx.progress.hidden = false;
        setProgress(ctx, 0, '업로드를 준비하고 있습니다.', '제품별 첨부파일과 신청 정보를 순서대로 전송합니다.');
        ctx.progress.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function setProgress(ctx, percent, text, subtext) {
        const normalized = Math.max(0, Math.min(100, Number(percent) || 0));
        ctx.progressBar.style.width = normalized + '%';
        ctx.progressPercent.textContent = normalized + '%';
        ctx.progressText.textContent = text;
        ctx.progressSubtext.textContent = subtext;
    }

    function showToast(ctx, message, type) {
        if (!ctx.toast) return;
        window.clearTimeout(state.toastTimer);
        ctx.toast.className = 'as-request-toast as-request-is-visible as-request-toast-' + (type || 'info');
        ctx.toast.textContent = message;
        state.toastTimer = window.setTimeout(function () {
            ctx.toast.classList.remove('as-request-is-visible');
        }, 3200);
    }
})();
