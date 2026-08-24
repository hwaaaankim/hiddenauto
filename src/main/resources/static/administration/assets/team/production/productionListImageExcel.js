/* /administration/assets/team/production/productionListImageExcel.js */
(function () {
    'use strict';

    const config = window.teamProductionOverviewConfig || {};

    // 엑셀/바로출력은 productionPrint.js에서 체크된 주문 기준으로 단일 관리합니다.
    // 이 파일은 제품명 강조, 플로팅 생산완료 버튼, 관리자 이미지 모달만 담당합니다.
    function getFieldValue(name) {
        const form = document.getElementById('team-production-filter-form');
        if (!form) return '';
        const field = form.elements.namedItem(name);
        return field && typeof field.value !== 'undefined' ? String(field.value || '').trim() : '';
    }

    // =========================================================
    // 제품명 검색어 밑줄 표시
    // =========================================================
    function highlightProductNameMatches() {
        const keyword = String(config.productNameKeyword || getFieldValue('productName') || '').trim();
        if (!keyword) return;

        const lowerKeyword = keyword.toLocaleLowerCase('ko-KR');

        document.querySelectorAll('.team-production-product-name-text').forEach(element => {
            const originalText = element.dataset.originalText || String(element.textContent || '');
            element.dataset.originalText = originalText;
            element.replaceChildren();

            let cursor = 0;
            const lowerText = originalText.toLocaleLowerCase('ko-KR');

            while (cursor < originalText.length) {
                const matchIndex = lowerText.indexOf(lowerKeyword, cursor);
                if (matchIndex < 0) {
                    element.appendChild(document.createTextNode(originalText.substring(cursor)));
                    break;
                }

                if (matchIndex > cursor) {
                    element.appendChild(document.createTextNode(originalText.substring(cursor, matchIndex)));
                }

                const mark = document.createElement('span');
                mark.className = 'team-production-product-name-highlight';
                mark.textContent = originalText.substring(matchIndex, matchIndex + keyword.length);
                element.appendChild(mark);
                cursor = matchIndex + keyword.length;
            }
        });
    }

    highlightProductNameMatches();

    // =========================================================
    // 원본 생산완료 버튼이 보이지 않을 때 플로팅 버튼 표시
    // =========================================================
    const originalCompleteBtn = document.getElementById('team-production-bulk-done-btn');
    const floatingCompleteBtn = document.getElementById('team-production-floating-bulk-done-btn');

    function syncFloatingCompleteState() {
        if (!originalCompleteBtn || !floatingCompleteBtn) return;
        floatingCompleteBtn.disabled = originalCompleteBtn.disabled;
        floatingCompleteBtn.title = originalCompleteBtn.title || '선택한 주문을 생산완료 처리합니다.';
    }

    if (originalCompleteBtn && floatingCompleteBtn) {
        floatingCompleteBtn.addEventListener('click', function () {
            if (!floatingCompleteBtn.disabled) originalCompleteBtn.click();
        });

        if ('IntersectionObserver' in window) {
            const observer = new IntersectionObserver(entries => {
                const entry = entries[0];
                floatingCompleteBtn.classList.toggle('is-visible', !entry.isIntersecting);
                syncFloatingCompleteState();
            }, { threshold: 0.15 });
            observer.observe(originalCompleteBtn);
        }

        const mutationObserver = new MutationObserver(syncFloatingCompleteState);
        mutationObserver.observe(originalCompleteBtn, {
            attributes: true,
            attributeFilter: ['disabled', 'title']
        });

        document.addEventListener('change', function (event) {
            if (event.target.matches('.team-production-check-item, #team-production-check-all')) {
                window.setTimeout(syncFloatingCompleteState, 0);
            }
        });

        syncFloatingCompleteState();
    }

    // =========================================================
    // 관리자 이미지 모달
    // =========================================================
    const modal = document.getElementById('team-production-management-image-modal');
    const stage = document.getElementById('team-production-management-image-stage');
    const viewer = document.getElementById('team-production-management-image-viewer');
    const counter = document.getElementById('team-production-management-image-counter');
    const thumbs = document.getElementById('team-production-management-image-thumbs');

    const closeBtn = document.getElementById('team-production-management-image-close-btn');
    const prevBtn = document.getElementById('team-production-management-image-prev-btn');
    const nextBtn = document.getElementById('team-production-management-image-next-btn');
    const originalBtn = document.getElementById('team-production-management-image-original-btn');
    const zoomInBtn = document.getElementById('team-production-management-image-zoom-in-btn');
    const zoomOutBtn = document.getElementById('team-production-management-image-zoom-out-btn');

    let images = [];
    let currentIndex = 0;
    let zoom = 1;
    let originalMode = false;

    function buildManagementImageUrl(orderId) {
        const prefix = config.managementImageUrlPrefix || '/team/productionList/';
        const normalizedPrefix = prefix.endsWith('/') ? prefix : prefix + '/';
        return normalizedPrefix + encodeURIComponent(orderId) + '/management-images';
    }

    async function fetchManagementImages(orderId) {
        const response = await fetch(buildManagementImageUrl(orderId), {
            method: 'GET',
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' }
        });

        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || '이미지를 불러오지 못했습니다.');
        }

        const data = await response.json();
        return Array.isArray(data) ? data : [];
    }

    function openImageModal() {
        if (!modal) return;
        modal.classList.add('is-open');
        modal.setAttribute('aria-hidden', 'false');
        document.body.classList.add('team-production-image-modal-open');
    }

    function closeImageModal() {
        if (!modal) return;

        modal.classList.remove('is-open');
        modal.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('team-production-image-modal-open');

        if (viewer) {
            viewer.removeAttribute('src');
            viewer.classList.remove('is-original');
            viewer.style.transform = '';
        }

        images = [];
        currentIndex = 0;
        zoom = 1;
        originalMode = false;
    }

    function applyImageMode() {
        if (!viewer) return;
        viewer.classList.toggle('is-original', originalMode);
        viewer.style.transform = 'scale(' + zoom + ')';
    }

    function renderThumbs() {
        if (!thumbs) return;
        thumbs.innerHTML = '';

        images.forEach((image, index) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'team-production-management-image-thumb';
            if (index === currentIndex) button.classList.add('is-active');

            const imageElement = document.createElement('img');
            imageElement.src = image.url || '';
            imageElement.alt = image.filename || '이미지 ' + (index + 1);
            button.appendChild(imageElement);

            button.addEventListener('click', function (event) {
                event.preventDefault();
                event.stopPropagation();
                currentIndex = index;
                renderCurrentImage();
            });

            thumbs.appendChild(button);
        });
    }

    function renderCurrentImage() {
        if (!viewer || !counter) return;

        if (!images.length) {
            viewer.removeAttribute('src');
            counter.textContent = '0 / 0';
            renderThumbs();
            return;
        }

        if (currentIndex < 0) currentIndex = images.length - 1;
        if (currentIndex >= images.length) currentIndex = 0;

        const image = images[currentIndex];
        const filename = image.filename || '';
        zoom = 1;
        originalMode = false;

        viewer.src = image.url || '';
        viewer.alt = filename || '관리자 업로드 이미지';
        counter.textContent = (currentIndex + 1) + ' / ' + images.length + (filename ? ' · ' + filename : '');

        if (stage) {
            stage.scrollLeft = 0;
            stage.scrollTop = 0;
        }

        applyImageMode();
        renderThumbs();
    }

    function movePrev() {
        if (!images.length) return;
        currentIndex = currentIndex <= 0 ? images.length - 1 : currentIndex - 1;
        renderCurrentImage();
    }

    function moveNext() {
        if (!images.length) return;
        currentIndex = currentIndex >= images.length - 1 ? 0 : currentIndex + 1;
        renderCurrentImage();
    }

    async function openImagesByOrderId(orderId) {
        if (!orderId) {
            window.alert('주문 ID가 없습니다.');
            return;
        }

        try {
            const result = await fetchManagementImages(orderId);
            if (!result.length) {
                window.alert('등록된 관리자 이미지가 없습니다.');
                return;
            }

            images = result;
            currentIndex = 0;
            renderCurrentImage();
            openImageModal();
        } catch (error) {
            window.alert(error && error.message ? error.message : '이미지를 불러오는 중 오류가 발생했습니다.');
        }
    }

    document.querySelectorAll('.team-production-management-image-btn').forEach(button => {
        button.addEventListener('click', function (event) {
            event.preventDefault();
            event.stopPropagation();
            openImagesByOrderId(button.getAttribute('data-order-id'));
        });
    });

    if (closeBtn) closeBtn.addEventListener('click', closeImageModal);
    if (prevBtn) prevBtn.addEventListener('click', movePrev);
    if (nextBtn) nextBtn.addEventListener('click', moveNext);

    if (originalBtn) {
        originalBtn.addEventListener('click', function () {
            originalMode = true;
            zoom = 1;
            applyImageMode();
        });
    }

    if (zoomInBtn) {
        zoomInBtn.addEventListener('click', function () {
            zoom = Math.min(4, Math.round((zoom + 0.2) * 10) / 10);
            applyImageMode();
        });
    }

    if (zoomOutBtn) {
        zoomOutBtn.addEventListener('click', function () {
            zoom = Math.max(0.3, Math.round((zoom - 0.2) * 10) / 10);
            applyImageMode();
        });
    }

    if (modal) {
        modal.addEventListener('click', function (event) {
            if (event.target === modal) closeImageModal();
        });
    }

    document.addEventListener('keydown', function (event) {
        if (!modal || !modal.classList.contains('is-open')) return;
        if (event.key === 'Escape') closeImageModal();
        if (event.key === 'ArrowLeft') movePrev();
        if (event.key === 'ArrowRight') moveNext();
    });
})();
