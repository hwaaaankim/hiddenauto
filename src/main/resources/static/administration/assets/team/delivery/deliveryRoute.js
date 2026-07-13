/* 업체별 배송 묶음 화면 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', init);

    function init() {
        const page = document.getElementById('delivery-route-page');
        if (!page) return;

        const groups = Array.from(document.querySelectorAll('.delivery-route-group'));
        const toggleAllButton = document.getElementById('delivery-route-toggle-all');

        initGroupToggles(groups, toggleAllButton);
        initCompletionControls(page, groups);
        refreshToggleAllButton(groups, toggleAllButton);
    }

    function initGroupToggles(groups, toggleAllButton) {
        groups.forEach(group => {
            const toggle = group.querySelector('[data-delivery-route-toggle]');
            if (!toggle) return;

            toggle.addEventListener('click', function () {
                setGroupExpanded(group, toggle.getAttribute('aria-expanded') !== 'true');
                refreshToggleAllButton(groups, toggleAllButton);
            });
        });

        if (toggleAllButton) {
            toggleAllButton.addEventListener('click', function () {
                const shouldOpen = !areAllGroupsExpanded(groups);
                groups.forEach(group => setGroupExpanded(group, shouldOpen));
                refreshToggleAllButton(groups, toggleAllButton);
            });
        }
    }

    function initCompletionControls(page, groups) {
        const modalElement = document.getElementById('delivery-route-complete-modal');
        const completeForm = document.getElementById('delivery-route-complete-form');

        if (!modalElement || !completeForm || !window.bootstrap) {
            return;
        }

        const modal = window.bootstrap.Modal.getOrCreateInstance(modalElement);
        const cameraButton = document.getElementById('delivery-route-camera-button');
        const galleryButton = document.getElementById('delivery-route-gallery-button');
        const cameraInput = document.getElementById('delivery-route-camera-input');
        const galleryInput = document.getElementById('delivery-route-gallery-input');
        const previewList = document.getElementById('delivery-route-image-preview-list');
        const emptyPreview = document.getElementById('delivery-route-image-empty');
        const orderCountElement = document.getElementById('delivery-route-modal-order-count');
        const imageCountElement = document.getElementById('delivery-route-modal-image-count');
        const selectedOrderIdsElement = document.getElementById('delivery-route-selected-order-ids');
        const submitButton = document.getElementById('delivery-route-submit-complete');

        let activeGroup = null;
        let activeOrderIds = [];
        let selectedFiles = [];
        let fileSequence = 0;
        let submitting = false;

        groups.forEach(group => {
            const orderChecks = getCompletableOrderChecks(group);
            const selectAll = group.querySelector('.delivery-route-group-select-all');
            const completeButton = group.querySelector('[data-delivery-route-complete-button]');

            orderChecks.forEach(checkbox => {
                checkbox.addEventListener('change', function () {
                    refreshGroupSelection(group);
                });
            });

            if (selectAll) {
                selectAll.addEventListener('change', function () {
                    orderChecks.forEach(checkbox => {
                        checkbox.checked = selectAll.checked;
                    });
                    refreshGroupSelection(group);
                });
            }

            if (completeButton) {
                completeButton.addEventListener('click', function () {
                    const selectedOrderIds = getSelectedOrderIds(group);
                    if (selectedOrderIds.length === 0) {
                        showMessage('선택된 주문이 없습니다.', '배송완료 처리할 주문을 1개 이상 선택해 주세요.', 'warning');
                        return;
                    }

                    activeGroup = group;
                    activeOrderIds = selectedOrderIds;
                    resetSelectedFiles();
                    renderModalState();
                    modal.show();
                });
            }

            refreshGroupSelection(group);
        });

        if (cameraButton && cameraInput) {
            cameraButton.addEventListener('click', function () {
                if (!submitting) cameraInput.click();
            });
            cameraInput.addEventListener('change', function () {
                appendFiles(cameraInput.files);
                cameraInput.value = '';
            });
        }

        if (galleryButton && galleryInput) {
            galleryButton.addEventListener('click', function () {
                if (!submitting) galleryInput.click();
            });
            galleryInput.addEventListener('change', function () {
                appendFiles(galleryInput.files);
                galleryInput.value = '';
            });
        }

        if (previewList) {
            previewList.addEventListener('click', function (event) {
                const removeButton = event.target.closest('[data-delivery-route-remove-file]');
                if (!removeButton || submitting) return;

                const fileId = removeButton.getAttribute('data-delivery-route-remove-file');
                removeSelectedFile(fileId);
            });
        }

        if (submitButton) {
            submitButton.addEventListener('click', async function () {
                if (submitting) return;

                if (activeOrderIds.length === 0) {
                    await showMessage('선택된 주문이 없습니다.', '배송완료 처리할 주문을 다시 선택해 주세요.', 'warning');
                    return;
                }

                if (selectedFiles.length === 0) {
                    await showMessage('이미지가 필요합니다.', '배송완료 이미지를 1장 이상 등록해 주세요.', 'warning');
                    return;
                }

                const confirmed = await confirmCompletion(activeOrderIds.length, selectedFiles.length);
                if (!confirmed) return;

                try {
                    setSubmitting(true);

                    const responseBody = await submitCompletion(
                        completeForm,
                        page.dataset.deliveryDate,
                        activeOrderIds,
                        selectedFiles.map(item => item.file)
                    );

                    modal.hide();
                    await showMessage(
                        '배송완료 처리되었습니다.',
                        responseBody.message || `${activeOrderIds.length}건을 배송완료 처리했습니다.`,
                        'success'
                    );
                    window.location.reload();
                } catch (error) {
                    await showMessage(
                        '배송완료 처리 실패',
                        error && error.message ? error.message : '요청 처리 중 오류가 발생했습니다.',
                        'error'
                    );
                } finally {
                    setSubmitting(false);
                }
            });
        }

        modalElement.addEventListener('hidden.bs.modal', function () {
            if (submitting) return;
            activeGroup = null;
            activeOrderIds = [];
            resetSelectedFiles();
            renderModalState();
        });

        window.addEventListener('beforeunload', revokeAllPreviewUrls);

        function appendFiles(fileList) {
            const files = Array.from(fileList || []);
            const invalidFiles = files.filter(file => !isImageFile(file));
            const imageFiles = files.filter(isImageFile);

            imageFiles.forEach(file => {
                const id = `delivery-route-file-${Date.now()}-${++fileSequence}`;
                selectedFiles.push({
                    id: id,
                    file: file,
                    previewUrl: URL.createObjectURL(file)
                });
            });

            renderModalState();

            if (invalidFiles.length > 0) {
                showMessage(
                    '일부 파일을 제외했습니다.',
                    `이미지 파일이 아닌 ${invalidFiles.length}개 파일은 등록하지 않았습니다.`,
                    'warning'
                );
            }
        }

        function removeSelectedFile(fileId) {
            const index = selectedFiles.findIndex(item => item.id === fileId);
            if (index < 0) return;

            const removed = selectedFiles.splice(index, 1)[0];
            if (removed && removed.previewUrl) {
                URL.revokeObjectURL(removed.previewUrl);
            }

            renderModalState();
        }

        function resetSelectedFiles() {
            revokeAllPreviewUrls();
            selectedFiles = [];
            if (cameraInput) cameraInput.value = '';
            if (galleryInput) galleryInput.value = '';
        }

        function revokeAllPreviewUrls() {
            selectedFiles.forEach(item => {
                if (item.previewUrl) URL.revokeObjectURL(item.previewUrl);
            });
        }

        function renderModalState() {
            if (orderCountElement) orderCountElement.textContent = String(activeOrderIds.length);
            if (imageCountElement) imageCountElement.textContent = String(selectedFiles.length);
            if (selectedOrderIdsElement) {
                selectedOrderIdsElement.textContent = activeOrderIds.length > 0
                    ? `선택 오더: ${activeOrderIds.map(orderId => `#${orderId}`).join(', ')}`
                    : '-';
            }

            if (previewList) {
                previewList.innerHTML = '';
                selectedFiles.forEach(item => {
                    previewList.appendChild(createPreviewElement(item));
                });
            }

            if (emptyPreview) {
                emptyPreview.hidden = selectedFiles.length > 0;
            }

            if (submitButton) {
                submitButton.disabled = submitting
                    || activeOrderIds.length === 0
                    || selectedFiles.length === 0;
            }
        }

        function createPreviewElement(item) {
            const wrapper = document.createElement('div');
            wrapper.className = 'delivery-route-image-preview-item';

            const image = document.createElement('img');
            image.src = item.previewUrl;
            image.alt = item.file.name || '배송완료 이미지 미리보기';

            const removeButton = document.createElement('button');
            removeButton.type = 'button';
            removeButton.className = 'delivery-route-image-remove';
            removeButton.setAttribute('aria-label', `${item.file.name || '이미지'} 삭제`);
            removeButton.setAttribute('data-delivery-route-remove-file', item.id);
            removeButton.innerHTML = '<i class="ri-close-line" aria-hidden="true"></i>';

            const meta = document.createElement('div');
            meta.className = 'delivery-route-image-preview-meta';

            const name = document.createElement('span');
            name.className = 'delivery-route-image-preview-name';
            name.textContent = item.file.name || 'delivery-image';

            const size = document.createElement('span');
            size.className = 'delivery-route-image-preview-size';
            size.textContent = formatFileSize(item.file.size);

            meta.append(name, size);
            wrapper.append(image, removeButton, meta);
            return wrapper;
        }

        function setSubmitting(value) {
            submitting = Boolean(value);

            [cameraButton, galleryButton].forEach(button => {
                if (button) button.disabled = submitting;
            });

            modalElement.querySelectorAll('[data-bs-dismiss="modal"]').forEach(button => {
                button.disabled = submitting;
            });

            if (submitButton) {
                const spinner = submitButton.querySelector('.spinner-border');
                const label = submitButton.querySelector('.delivery-route-submit-label');

                if (spinner) spinner.classList.toggle('d-none', !submitting);
                if (label) label.textContent = submitting ? '처리 중' : '배송완료';
            }

            renderModalState();
        }
    }

    function getCompletableOrderChecks(group) {
        return Array.from(group.querySelectorAll('.delivery-route-complete-check:not(:disabled)'))
            .filter(checkbox => {
                const card = checkbox.closest('.delivery-route-order-card');
                return card && card.dataset.completable === 'true';
            });
    }

    function getSelectedOrderIds(group) {
        const result = [];
        const seen = new Set();

        getCompletableOrderChecks(group)
            .filter(checkbox => checkbox.checked)
            .forEach(checkbox => {
                const orderId = Number(checkbox.dataset.orderId);
                if (!Number.isSafeInteger(orderId) || orderId <= 0 || seen.has(orderId)) return;
                seen.add(orderId);
                result.push(orderId);
            });

        return result;
    }

    function refreshGroupSelection(group) {
        const boxes = getCompletableOrderChecks(group);
        const selectedBoxes = boxes.filter(checkbox => checkbox.checked);
        const selectedCount = selectedBoxes.length;
        const totalCount = boxes.length;
        const selectAll = group.querySelector('.delivery-route-group-select-all');
        const completeButton = group.querySelector('[data-delivery-route-complete-button]');
        const countBadge = completeButton
            ? completeButton.querySelector('[data-delivery-route-selected-count]')
            : null;
        const groupId = group.dataset.groupId || '';
        const progress = document.querySelector(`[data-progress-for="${cssEscape(groupId)}"]`);

        if (selectAll) {
            selectAll.checked = totalCount > 0 && selectedCount === totalCount;
            selectAll.indeterminate = selectedCount > 0 && selectedCount < totalCount;
        }

        if (completeButton) {
            completeButton.disabled = selectedCount === 0;
        }

        if (countBadge) {
            countBadge.textContent = String(selectedCount);
        }

        if (progress && !group.classList.contains('is-freight')) {
            progress.textContent = `선택 ${selectedCount}/${totalCount}`;
        }

        group.classList.toggle('has-selection', selectedCount > 0);

        boxes.forEach(checkbox => {
            const card = checkbox.closest('.delivery-route-order-card');
            if (card) {
                card.classList.toggle('is-selected-for-completion', checkbox.checked);
            }
        });
    }

    async function submitCompletion(form, deliveryDate, orderIds, files) {
        const action = form.getAttribute('action') || '/team/deliveryRoute/complete';
        const formData = new FormData();
        const csrfInput = form.querySelector('input[type="hidden"]');
        const headers = { 'X-Requested-With': 'fetch' };

        formData.append('deliveryDate', String(deliveryDate || ''));
        orderIds.forEach(orderId => formData.append('orderIds', String(orderId)));
        files.forEach(file => formData.append('files', file, file.name));

        if (csrfInput && csrfInput.name && csrfInput.value) {
            formData.append(csrfInput.name, csrfInput.value);

            const headerName = csrfInput.dataset.csrfHeader;
            if (headerName) headers[headerName] = csrfInput.value;
        }

        const response = await fetch(action, {
            method: 'POST',
            headers: headers,
            body: formData,
            credentials: 'same-origin'
        });

        const responseBody = await parseResponseBody(response);

        if (!response.ok || responseBody.success === false) {
            throw new Error(responseBody.message || `배송완료 처리에 실패했습니다. (${response.status})`);
        }

        return responseBody;
    }

    async function parseResponseBody(response) {
        const contentType = response.headers.get('content-type') || '';

        if (contentType.includes('application/json')) {
            return response.json();
        }

        const text = await response.text();
        return { success: response.ok, message: text };
    }

    async function confirmCompletion(orderCount, imageCount) {
        const text = `${orderCount}개 오더에 대해 ${imageCount}장의 이미지로 완료처리 하시겠습니까? 해당 이미지는 체크된 모든 오더에 등록됩니다.`;

        if (window.Swal && typeof window.Swal.fire === 'function') {
            const result = await window.Swal.fire({
                icon: 'question',
                title: '배송완료 처리 확인',
                text: text,
                showCancelButton: true,
                confirmButtonText: '배송완료',
                cancelButtonText: '취소',
                reverseButtons: true,
                allowOutsideClick: false
            });

            return Boolean(result.isConfirmed);
        }

        return window.confirm(text);
    }

    async function showMessage(title, text, icon) {
        if (window.Swal && typeof window.Swal.fire === 'function') {
            return window.Swal.fire({
                icon: icon || 'info',
                title: title,
                text: text,
                confirmButtonText: '확인'
            });
        }

        window.alert(`${title}\n${text}`);
        return null;
    }

    function isImageFile(file) {
        return Boolean(file && file.type && file.type.toLowerCase().startsWith('image/'));
    }

    function formatFileSize(bytes) {
        const value = Number(bytes || 0);
        if (value < 1024) return `${value} B`;
        if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
        return `${(value / (1024 * 1024)).toFixed(1)} MB`;
    }

    function setGroupExpanded(group, expanded) {
        if (!group) return;

        const toggle = group.querySelector('[data-delivery-route-toggle]');
        if (!toggle) return;

        const bodyId = toggle.getAttribute('aria-controls');
        const body = bodyId ? document.getElementById(bodyId) : null;

        toggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        group.classList.toggle('is-open', expanded);

        if (body) {
            animateGroupBody(body, expanded);
        }
    }

    function animateGroupBody(body, expanded) {
        if (!body) return;

        const reduceMotion = window.matchMedia
            && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

        if (body._deliveryRouteTransitionHandler) {
            body.removeEventListener('transitionend', body._deliveryRouteTransitionHandler);
            body._deliveryRouteTransitionHandler = null;
        }

        if (reduceMotion) {
            body.hidden = !expanded;
            body.style.height = '';
            body.style.overflow = '';
            body.style.transition = '';
            return;
        }

        body.style.overflow = 'hidden';
        body.style.transition = 'height 220ms ease';

        if (expanded) {
            body.hidden = false;
            body.style.height = '0px';
            void body.offsetHeight;
            body.style.height = `${body.scrollHeight}px`;
        } else {
            body.hidden = false;
            body.style.height = `${body.scrollHeight}px`;
            void body.offsetHeight;
            body.style.height = '0px';
        }

        const onTransitionEnd = function (event) {
            if (event.target !== body || event.propertyName !== 'height') return;

            body.removeEventListener('transitionend', onTransitionEnd);
            body._deliveryRouteTransitionHandler = null;
            body.hidden = !expanded;
            body.style.height = '';
            body.style.overflow = '';
            body.style.transition = '';
        };

        body._deliveryRouteTransitionHandler = onTransitionEnd;
        body.addEventListener('transitionend', onTransitionEnd);
    }

    function areAllGroupsExpanded(groups) {
        return groups.length > 0 && groups.every(group => {
            const toggle = group.querySelector('[data-delivery-route-toggle]');
            return toggle && toggle.getAttribute('aria-expanded') === 'true';
        });
    }

    function refreshToggleAllButton(groups, button) {
        if (!button) return;

        if (groups.length === 0) {
            button.disabled = true;
            setButtonLabel(button, '열 항목 없음', 'ri-forbid-line');
            return;
        }

        button.disabled = false;

        if (areAllGroupsExpanded(groups)) {
            button.setAttribute('aria-label', '모든 업체 묶음 닫기');
            setButtonLabel(button, '전체 닫기', 'ri-contract-up-down-line');
        } else {
            button.setAttribute('aria-label', '모든 업체 묶음 열기');
            setButtonLabel(button, '전체 열기', 'ri-expand-up-down-line');
        }
    }

    function setButtonLabel(button, label, iconClass) {
        const icon = button.querySelector('i');
        const text = button.querySelector('span');

        if (icon) icon.className = iconClass;
        if (text) text.textContent = label;
    }

    function cssEscape(value) {
        const text = String(value || '');

        if (window.CSS && typeof window.CSS.escape === 'function') {
            return window.CSS.escape(text);
        }

        return text.replace(/([ #;?%&,+*~':"!^$[\]()=>|/@])/g, '\\$1');
    }
})();
