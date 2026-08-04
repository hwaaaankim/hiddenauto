(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const page = document.getElementById('management-delivery-list-page');
        if (!page) {
            return;
        }

        const searchForm = document.getElementById('management-delivery-search-form');
        const orderIdFromInput = document.getElementById('management-delivery-order-id-from');
        const orderIdToInput = document.getElementById('management-delivery-order-id-to');
        const startDateInput = document.getElementById('management-delivery-start-date');
        const endDateInput = document.getElementById('management-delivery-end-date');
        const pageSizeSelect = document.getElementById('management-delivery-page-size');
        const sortFieldInput = document.getElementById('management-delivery-sort-field');
        const sortDirInput = document.getElementById('management-delivery-sort-dir');

        const imageModalElement = document.getElementById('management-delivery-image-modal');
        const modalMainImage = document.getElementById('management-delivery-modal-main-image');
        const modalImageName = document.getElementById('management-delivery-modal-image-name');
        const modalCounter = document.getElementById('management-delivery-modal-counter');
        const modalThumbnails = document.getElementById('management-delivery-modal-thumbnails');
        const modalPrevButton = document.getElementById('management-delivery-modal-prev');
        const modalNextButton = document.getElementById('management-delivery-modal-next');

        const modalInstance = imageModalElement && window.bootstrap
            ? window.bootstrap.Modal.getOrCreateInstance(imageModalElement)
            : null;

        const galleryStateByGroup = new Map();
        let modalImages = [];
        let modalIndex = 0;

        function parsePositiveNumber(input) {
            if (!input || !input.value) {
                return null;
            }
            const value = Number(input.value);
            return Number.isFinite(value) ? value : null;
        }

        function validateSearchForm(event) {
            const orderIdFrom = parsePositiveNumber(orderIdFromInput);
            const orderIdTo = parsePositiveNumber(orderIdToInput);

            if (orderIdFrom !== null && orderIdTo !== null && orderIdFrom > orderIdTo) {
                event.preventDefault();
                window.alert('오더 ID TO는 FROM보다 크거나 같아야 합니다.');
                orderIdToInput.focus();
                return false;
            }

            if (startDateInput && endDateInput
                && startDateInput.value && endDateInput.value
                && startDateInput.value > endDateInput.value) {
                event.preventDefault();
                window.alert('종료일은 시작일보다 빠를 수 없습니다.');
                endDateInput.focus();
                return false;
            }

            return true;
        }

        function getImages(groupId) {
            const sourceList = page.querySelector(
                '[data-management-delivery-image-source-list="' + CSS.escape(groupId) + '"]'
            );

            if (!sourceList) {
                return [];
            }

            return Array.from(sourceList.querySelectorAll('[data-management-delivery-image-source]'))
                .map(function (source) {
                    return {
                        url: source.dataset.imageUrl || '',
                        name: source.dataset.imageName || '배송완료 이미지'
                    };
                })
                .filter(function (image) {
                    return Boolean(image.url);
                });
        }

        function normalizeIndex(index, length) {
            if (length <= 0) {
                return 0;
            }
            return ((index % length) + length) % length;
        }

        function renderGroupGallery(groupId, requestedIndex) {
            const gallery = page.querySelector(
                '[data-management-delivery-gallery="' + CSS.escape(groupId) + '"]'
            );
            const images = getImages(groupId);

            if (!gallery || images.length === 0) {
                return;
            }

            const currentIndex = normalizeIndex(requestedIndex, images.length);
            galleryStateByGroup.set(groupId, currentIndex);

            const mainImage = gallery.querySelector('[data-management-delivery-gallery-main-image]');
            const counter = gallery.querySelector('[data-management-delivery-gallery-counter]');
            const thumbnails = gallery.querySelectorAll('[data-management-delivery-gallery-thumbnail]');

            if (mainImage) {
                mainImage.src = images[currentIndex].url;
                mainImage.alt = images[currentIndex].name;
            }

            if (counter) {
                counter.textContent = (currentIndex + 1) + ' / ' + images.length;
            }

            thumbnails.forEach(function (thumbnail) {
                const thumbnailIndex = Number(thumbnail.dataset.imageIndex || 0);
                thumbnail.classList.toggle('is-active', thumbnailIndex === currentIndex);
            });
        }

        function openImageModal(groupId, requestedIndex) {
            const images = getImages(groupId);
            if (images.length === 0) {
                return;
            }

            modalImages = images;
            modalIndex = normalizeIndex(requestedIndex, modalImages.length);
            renderModalImage();

            if (modalInstance) {
                modalInstance.show();
            }
        }

        function renderModalImage() {
            if (!modalMainImage || modalImages.length === 0) {
                return;
            }

            modalIndex = normalizeIndex(modalIndex, modalImages.length);
            const currentImage = modalImages[modalIndex];

            modalMainImage.src = currentImage.url;
            modalMainImage.alt = currentImage.name;

            if (modalImageName) {
                modalImageName.textContent = currentImage.name;
            }
            if (modalCounter) {
                modalCounter.textContent = (modalIndex + 1) + ' / ' + modalImages.length;
            }

            if (modalThumbnails) {
                modalThumbnails.innerHTML = '';
                modalImages.forEach(function (image, index) {
                    const button = document.createElement('button');
                    button.type = 'button';
                    button.className = 'management-delivery-modal-thumbnail';
                    if (index === modalIndex) {
                        button.classList.add('is-active');
                    }
                    button.dataset.imageIndex = String(index);
                    button.setAttribute('aria-label', (index + 1) + '번 이미지 보기');

                    const imageElement = document.createElement('img');
                    imageElement.src = image.url;
                    imageElement.alt = image.name;
                    button.appendChild(imageElement);
                    modalThumbnails.appendChild(button);
                });
            }

            const hasMultipleImages = modalImages.length > 1;
            if (modalPrevButton) {
                modalPrevButton.hidden = !hasMultipleImages;
            }
            if (modalNextButton) {
                modalNextButton.hidden = !hasMultipleImages;
            }
        }

        function toggleDetail(button) {
            const groupId = button.dataset.groupId;
            if (!groupId) {
                return;
            }

            const detailRow = document.getElementById(groupId + '-detail');
            if (!detailRow) {
                return;
            }

            const opening = detailRow.hidden;
            detailRow.hidden = !opening;
            button.setAttribute('aria-expanded', opening ? 'true' : 'false');

            const label = button.querySelector('span');
            if (label) {
                label.textContent = opening ? '닫기' : '넓게보기';
            }

            const icon = button.querySelector('i');
            if (icon) {
                icon.className = opening ? 'ri-contract-left-right-line me-1' : 'ri-layout-row-line me-1';
            }

            if (opening) {
                renderGroupGallery(groupId, galleryStateByGroup.get(groupId) || 0);
            }
        }

        if (searchForm) {
            searchForm.addEventListener('submit', validateSearchForm);
        }

        if (pageSizeSelect && searchForm) {
            pageSizeSelect.addEventListener('change', function () {
                if (validateSearchForm(new Event('submit', {cancelable: true}))) {
                    searchForm.submit();
                }
            });
        }

        page.querySelectorAll('[data-management-delivery-sort]').forEach(function (button) {
            button.addEventListener('click', function () {
                if (!searchForm || !sortFieldInput || !sortDirInput) {
                    return;
                }

                const nextField = button.dataset.managementDeliverySort;
                const currentField = sortFieldInput.value;
                const currentDirection = sortDirInput.value === 'asc' ? 'asc' : 'desc';

                sortFieldInput.value = nextField;
                sortDirInput.value = currentField === nextField && currentDirection === 'desc'
                    ? 'asc'
                    : 'desc';

                if (validateSearchForm(new Event('submit', {cancelable: true}))) {
                    searchForm.submit();
                }
            });
        });

        page.addEventListener('click', function (event) {
            const toggleButton = event.target.closest('[data-management-delivery-toggle]');
            if (toggleButton) {
                toggleDetail(toggleButton);
                return;
            }

            const directImageButton = event.target.closest('[data-management-delivery-open-images]');
            if (directImageButton && !directImageButton.disabled) {
                openImageModal(directImageButton.dataset.groupId, 0);
                return;
            }

            const previousButton = event.target.closest('[data-management-delivery-gallery-prev]');
            if (previousButton) {
                const groupId = previousButton.dataset.groupId;
                const currentIndex = galleryStateByGroup.get(groupId) || 0;
                renderGroupGallery(groupId, currentIndex - 1);
                return;
            }

            const nextButton = event.target.closest('[data-management-delivery-gallery-next]');
            if (nextButton) {
                const groupId = nextButton.dataset.groupId;
                const currentIndex = galleryStateByGroup.get(groupId) || 0;
                renderGroupGallery(groupId, currentIndex + 1);
                return;
            }

            const thumbnailButton = event.target.closest('[data-management-delivery-gallery-thumbnail]');
            if (thumbnailButton) {
                renderGroupGallery(
                    thumbnailButton.dataset.groupId,
                    Number(thumbnailButton.dataset.imageIndex || 0)
                );
                return;
            }

            const galleryOpenButton = event.target.closest('[data-management-delivery-gallery-open]');
            if (galleryOpenButton) {
                const groupId = galleryOpenButton.dataset.groupId;
                openImageModal(groupId, galleryStateByGroup.get(groupId) || 0);
            }
        });

        if (modalPrevButton) {
            modalPrevButton.addEventListener('click', function () {
                modalIndex -= 1;
                renderModalImage();
            });
        }

        if (modalNextButton) {
            modalNextButton.addEventListener('click', function () {
                modalIndex += 1;
                renderModalImage();
            });
        }

        if (modalThumbnails) {
            modalThumbnails.addEventListener('click', function (event) {
                const thumbnail = event.target.closest('[data-image-index]');
                if (!thumbnail) {
                    return;
                }
                modalIndex = Number(thumbnail.dataset.imageIndex || 0);
                renderModalImage();
            });
        }

        document.addEventListener('keydown', function (event) {
            if (!imageModalElement || !imageModalElement.classList.contains('show')) {
                return;
            }

            if (event.key === 'ArrowLeft') {
                event.preventDefault();
                modalIndex -= 1;
                renderModalImage();
            } else if (event.key === 'ArrowRight') {
                event.preventDefault();
                modalIndex += 1;
                renderModalImage();
            }
        });
    });
})();
