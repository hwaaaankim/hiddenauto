/* deliveryRoute.js */
/* 업체별 배송 묶음 화면 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', init);

    function init() {
        const page = document.getElementById('delivery-route-page');
        if (!page) return;

        const groups = Array.from(document.querySelectorAll('.delivery-route-group'));
        const toggleAllButton = document.getElementById('delivery-route-toggle-all');

        /*
         * 서버도 미완료 묶음 -> 완료 묶음 순서로 내려주지만,
         * 과거 캐시/부분 갱신 상태에서도 화면 순서를 확실하게 보정합니다.
         */
        normalizeRenderedRoute(groups);

        initGroupToggles(groups, toggleAllButton);
        initExportControls(page);
        initCompletionControls(page, groups, toggleAllButton);
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

    function initExportControls(page) {
        const excelButton = document.getElementById('delivery-route-excel-button');
        const printButton = document.getElementById('delivery-route-print-button');
        const printForm = document.getElementById('delivery-route-print-form');
        const exportForm = document.getElementById('delivery-route-export-form');

        if (excelButton) {
            excelButton.addEventListener('click', async function () {
                const handlerId = Number(page.dataset.handlerId);
                const deliveryDate = String(page.dataset.deliveryDate || '').trim();
                const orderedOrderIds = getAllRouteOrderIdsInCurrentDomOrder();

                if (!Number.isSafeInteger(handlerId) || handlerId <= 0) {
                    await showMessage('담당자 정보가 없습니다.', '로그인한 배송 담당자 정보를 다시 확인해 주세요.', 'warning');
                    return;
                }

                if (!deliveryDate) {
                    await showMessage('배송일이 없습니다.', '엑셀로 출력할 배송 날짜를 선택해 주세요.', 'warning');
                    return;
                }

                if (orderedOrderIds.length === 0) {
                    await showMessage('출력할 데이터가 없습니다.', '현재 조회된 배송 주문이 없습니다.', 'warning');
                    return;
                }

                try {
                    setExportButtonBusy(excelButton, true);

                    const headers = { 'Content-Type': 'application/json' };
                    applyCsrfHeader(headers, exportForm);

                    const action = exportForm && exportForm.dataset.excelAction
                        ? exportForm.dataset.excelAction
                        : '/team/deliveryExcel';

                    const response = await fetch(action, {
                        method: 'POST',
                        headers: headers,
                        credentials: 'same-origin',
                        body: JSON.stringify({
                            deliveryHandlerId: handlerId,
                            fromDate: deliveryDate,
                            toDate: deliveryDate,
                            orderedOrderIds: orderedOrderIds
                        })
                    });

                    if (!response.ok) {
                        const errorBody = await parseResponseBody(response);
                        throw new Error(errorBody.message || `엑셀 출력에 실패했습니다. (${response.status})`);
                    }

                    const blob = await response.blob();
                    const filename = resolveDownloadFilename(
                        response.headers.get('content-disposition'),
                        `배송리스트_${deliveryDate}.xlsx`
                    );

                    downloadBlob(blob, filename);

                } catch (error) {
                    await showMessage(
                        '엑셀 출력 실패',
                        error && error.message ? error.message : '엑셀 파일 생성 중 오류가 발생했습니다.',
                        'error'
                    );
                } finally {
                    setExportButtonBusy(excelButton, false);
                }
            });
        }

        if (printButton) {
            printButton.addEventListener('click', async function () {
                const deliveryDate = String(page.dataset.deliveryDate || '').trim();
                const orderedOrderIds = getAllRouteOrderIdsInCurrentDomOrder();

                if (!deliveryDate) {
                    await showMessage('배송일이 없습니다.', '인쇄할 배송 날짜를 선택해 주세요.', 'warning');
                    return;
                }

                if (orderedOrderIds.length === 0) {
                    await showMessage('인쇄할 데이터가 없습니다.', '현재 조회된 배송 주문이 없습니다.', 'warning');
                    return;
                }

                if (orderedOrderIds.length > 1000) {
                    await showMessage('인쇄 대상이 너무 많습니다.', '한 번에 인쇄할 수 있는 주문은 최대 1,000건입니다.', 'warning');
                    return;
                }

                if (printForm) {
                    submitRoutePrintForm(printForm, deliveryDate, orderedOrderIds);
                    return;
                }

                const query = new URLSearchParams();
                query.set('deliveryDate', deliveryDate);
                query.set('orderIds', orderedOrderIds.join(','));

                const printWindow = window.open(`/team/deliveryPrint?${query.toString()}`, '_blank');

                if (!printWindow) {
                    await showMessage('인쇄 창이 차단되었습니다.', '브라우저의 팝업 허용 설정을 확인해 주세요.', 'warning');
                    return;
                }

                try {
                    printWindow.opener = null;
                } catch (ignored) {
                    // 브라우저 정책상 opener 변경이 불가능해도 인쇄 창 자체는 정상 동작합니다.
                }
            });
        }
    }

    function getAllRouteOrderIdsInCurrentDomOrder() {
        const result = [];
        const seen = new Set();
        const cards = document.querySelectorAll(
            '#delivery-route-direct-section .delivery-route-order-card[data-order-id], ' +
            '#delivery-route-freight-section .delivery-route-order-card[data-order-id]'
        );

        cards.forEach(card => {
            const orderId = Number(card.getAttribute('data-order-id'));

            if (!Number.isSafeInteger(orderId) || orderId <= 0 || seen.has(orderId)) {
                return;
            }

            seen.add(orderId);
            result.push(orderId);
        });

        return result;
    }

    function submitRoutePrintForm(form, deliveryDate, orderedOrderIds) {
        const targetName = `hiddenbath_delivery_route_print_${Date.now()}`;
        const printWindow = window.open('', targetName);

        if (!printWindow) {
            showMessage('인쇄 창이 차단되었습니다.', '브라우저의 팝업 허용 설정을 확인해 주세요.', 'warning');
            return;
        }

        try {
            printWindow.opener = null;
        } catch (ignored) {
            // 브라우저 정책상 opener 변경이 불가능해도 인쇄 창 자체는 정상 동작합니다.
        }

        form.querySelectorAll('.delivery-route-export-dynamic-field').forEach(field => field.remove());
        form.target = targetName;
        form.appendChild(createRouteHiddenField('deliveryDate', deliveryDate));

        orderedOrderIds.forEach(orderId => {
            form.appendChild(createRouteHiddenField('orderIds', String(orderId)));
        });

        form.submit();

        window.setTimeout(function () {
            form.querySelectorAll('.delivery-route-export-dynamic-field').forEach(field => field.remove());
        }, 0);
    }

    function createRouteHiddenField(name, value) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        input.className = 'delivery-route-export-dynamic-field';
        return input;
    }

    function applyCsrfHeader(headers, form) {
        if (!headers || !form) return;

        const csrfInput = form.querySelector('input[type="hidden"]');
        if (!csrfInput || !csrfInput.value) return;

        const headerName = csrfInput.dataset.csrfHeader;
        if (headerName) {
            headers[headerName] = csrfInput.value;
        }
    }

    function setExportButtonBusy(button, busy) {
        if (!button) return;

        if (!button.dataset.originalHtml) {
            button.dataset.originalHtml = button.innerHTML;
        }

        button.disabled = Boolean(busy);
        button.innerHTML = busy
            ? '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>생성 중'
            : button.dataset.originalHtml;
    }

    function resolveDownloadFilename(contentDisposition, fallback) {
        const value = String(contentDisposition || '');
        const utf8Match = value.match(/filename\*=UTF-8''([^;]+)/i);

        if (utf8Match && utf8Match[1]) {
            try {
                return decodeURIComponent(utf8Match[1].trim().replace(/^"|"$/g, ''));
            } catch (ignored) {
                return utf8Match[1].trim().replace(/^"|"$/g, '');
            }
        }

        const filenameMatch = value.match(/filename="?([^";]+)"?/i);
        return filenameMatch && filenameMatch[1]
            ? filenameMatch[1].trim()
            : fallback;
    }

    function downloadBlob(blob, filename) {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');

        link.href = url;
        link.download = filename || '배송리스트.xlsx';
        document.body.appendChild(link);
        link.click();
        link.remove();

        window.setTimeout(function () {
            window.URL.revokeObjectURL(url);
        }, 0);
    }

    function initCompletionControls(page, groups, toggleAllButton) {
        const modalElement = document.getElementById('delivery-route-complete-modal');
        const completeForm = document.getElementById('delivery-route-complete-form');

        if (!modalElement || !completeForm || !window.bootstrap || !window.bootstrap.Modal) {
            return;
        }

        const modal = window.bootstrap.Modal.getOrCreateInstance
            ? window.bootstrap.Modal.getOrCreateInstance(modalElement)
            : new window.bootstrap.Modal(modalElement);

        const cameraButton = document.getElementById('delivery-route-camera-button');
        const galleryButton = document.getElementById('delivery-route-gallery-button');
        const cameraInput = document.getElementById('delivery-route-camera-input');
        const galleryInput = document.getElementById('delivery-route-gallery-input');
        const previewList = document.getElementById('delivery-route-image-preview-list');
        const emptyPreview = document.getElementById('delivery-route-image-empty');
        const orderCountElement = document.getElementById('delivery-route-modal-order-count');
        const imageCountElement = document.getElementById('delivery-route-modal-image-count');
        const selectedOrderIdsElement = document.getElementById('delivery-route-selected-order-ids');
        const feedbackElement = document.getElementById('delivery-route-complete-feedback');
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
                    getCompletableOrderChecks(group).forEach(checkbox => {
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
                    clearCompletionFeedback();
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
                    showCompletionFeedback('배송완료 처리할 주문을 다시 선택해 주세요.', 'warning');
                    return;
                }

                if (selectedFiles.length === 0) {
                    showCompletionFeedback('배송완료 이미지를 1장 이상 등록해 주세요.', 'warning');
                    return;
                }

                const requestedOrderIds = activeOrderIds.slice();
                const requestedImageCount = selectedFiles.length;
                const targetGroup = activeGroup;

                try {
                    clearCompletionFeedback();
                    setSubmitting(true);

                    const responseBody = await submitCompletion(
                        completeForm,
                        page.dataset.deliveryDate,
                        requestedOrderIds,
                        selectedFiles.map(item => item.file)
                    );

                    const snapshot = normalizeCompletionSnapshot(responseBody.completionSnapshot);
                    const deliveryDoneOrderIds = snapshot && snapshot.deliveryDoneOrderIds.length > 0
                        ? snapshot.deliveryDoneOrderIds
                        : normalizePositiveIds(responseBody.completedOrderIds || requestedOrderIds);

                    applyCompletionState(
                        targetGroup,
                        deliveryDoneOrderIds,
                        snapshot,
                        groups,
                        toggleAllButton
                    );

                    modal.hide();

                    await showMessage(
                        '배송완료 처리되었습니다.',
                        responseBody.message
                            || `${requestedOrderIds.length}건을 ${requestedImageCount}장의 이미지로 배송완료 처리했습니다.`,
                        'success'
                    );

                } catch (error) {
                    const message = error && error.message
                        ? error.message
                        : '요청 처리 중 오류가 발생했습니다.';

                    showCompletionFeedback(message, 'error');
                    await showMessage('배송완료 처리 실패', message, 'error');
                } finally {
                    setSubmitting(false);
                }
            });
        }

        modalElement.addEventListener('show.bs.modal', function () {
            document.body.classList.add('delivery-route-completion-modal-open');
        });

        modalElement.addEventListener('hidden.bs.modal', function () {
            document.body.classList.remove('delivery-route-completion-modal-open');

            if (submitting) return;

            activeGroup = null;
            activeOrderIds = [];
            resetSelectedFiles();
            clearCompletionFeedback();
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

            if (imageFiles.length > 0) {
                clearCompletionFeedback();
            }

            renderModalState();

            if (invalidFiles.length > 0) {
                showCompletionFeedback(
                    `이미지 파일이 아닌 ${invalidFiles.length}개 파일은 제외했습니다.`,
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

                const label = submitButton.querySelector('.delivery-route-submit-label');
                if (label && !submitting) {
                    label.textContent = activeOrderIds.length > 0
                        ? `${activeOrderIds.length}건 배송완료`
                        : '배송완료';
                }
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
                if (label) {
                    label.textContent = submitting
                        ? '처리 중'
                        : (activeOrderIds.length > 0 ? `${activeOrderIds.length}건 배송완료` : '배송완료');
                }
            }

            renderModalState();
        }

        function clearCompletionFeedback() {
            if (!feedbackElement) return;

            feedbackElement.hidden = true;
            feedbackElement.textContent = '';
            feedbackElement.classList.remove('is-warning', 'is-error', 'is-success');
        }

        function showCompletionFeedback(message, type) {
            if (!feedbackElement) {
                showMessage('확인해 주세요.', message, type || 'warning');
                return;
            }

            feedbackElement.hidden = false;
            feedbackElement.textContent = String(message || '요청 내용을 확인해 주세요.');
            feedbackElement.classList.remove('is-warning', 'is-error', 'is-success');
            feedbackElement.classList.add(`is-${normalizeMessageType(type)}`);
            feedbackElement.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
        }
    }

    function normalizeCompletionSnapshot(value) {
        if (!value || typeof value !== 'object') {
            return null;
        }

        const groupOrderIds = normalizePositiveIds(value.groupOrderIds);
        const deliveryDoneOrderIds = normalizePositiveIds(value.deliveryDoneOrderIds);
        const groupOrderCount = toNonNegativeInteger(value.groupOrderCount, groupOrderIds.length);
        const groupDeliveryDoneCount = toNonNegativeInteger(
            value.groupDeliveryDoneCount,
            deliveryDoneOrderIds.length
        );
        const groupCompletableOrderCount = toNonNegativeInteger(value.groupCompletableOrderCount, 0);
        const pageDeliveryDoneCount = toNonNegativeInteger(value.pageDeliveryDoneCount, -1);
        const groupFullyCompleted = Boolean(value.groupFullyCompleted)
            || (groupOrderCount > 0 && groupDeliveryDoneCount >= groupOrderCount);

        return {
            groupOrderIds: groupOrderIds,
            deliveryDoneOrderIds: deliveryDoneOrderIds,
            groupOrderCount: groupOrderCount,
            groupDeliveryDoneCount: Math.min(groupDeliveryDoneCount, groupOrderCount || groupDeliveryDoneCount),
            groupCompletableOrderCount: groupCompletableOrderCount,
            pageDeliveryDoneCount: pageDeliveryDoneCount,
            groupFullyCompleted: groupFullyCompleted
        };
    }

    function applyCompletionState(group, deliveryDoneOrderIds, snapshot, groups, toggleAllButton) {
        if (!group) return;

        const doneIds = new Set(normalizePositiveIds(deliveryDoneOrderIds));

        doneIds.forEach(orderId => {
            const card = findOrderCard(group, orderId);
            if (card) markOrderCardAsDone(card);
        });

        const orderCards = getOrderCards(group);
        const calculatedOrderCount = orderCards.length;
        const calculatedDoneCount = orderCards.filter(isDeliveryDoneCard).length;
        const calculatedCompletableCount = orderCards.filter(card => card.dataset.completable === 'true').length;

        const orderCount = snapshot && snapshot.groupOrderCount > 0
            ? snapshot.groupOrderCount
            : calculatedOrderCount;
        const doneCount = snapshot
            ? Math.min(snapshot.groupDeliveryDoneCount, orderCount)
            : calculatedDoneCount;
        const completableCount = snapshot
            ? snapshot.groupCompletableOrderCount
            : calculatedCompletableCount;
        const allCompleted = snapshot
            ? snapshot.groupFullyCompleted
            : orderCount > 0 && doneCount >= orderCount;

        group.dataset.orderCount = String(orderCount);
        group.dataset.deliveryDoneCount = String(doneCount);
        group.dataset.completableCount = String(completableCount);
        group.dataset.allCompleted = allCompleted ? 'true' : 'false';

        updateGroupCompletionCounters(group, doneCount, orderCount);
        updateGroupCompletionBadge(group, doneCount, orderCount, allCompleted);
        updateGroupBulkState(group, completableCount, allCompleted);

        group.classList.toggle('is-fully-completed', allCompleted);
        group.classList.remove('has-selection');
        group.classList.add('is-completion-updated');

        window.setTimeout(function () {
            group.classList.remove('is-completion-updated');
        }, 760);

        if (allCompleted) {
            forceGroupCollapsed(group);
        }

        updatePageDeliveryDoneCount(snapshot);
        normalizeRenderedRoute(groups);
        refreshGroupSelection(group);
        refreshToggleAllButton(groups, toggleAllButton);
    }

    function findOrderCard(group, orderId) {
        const cards = getOrderCards(group);
        return cards.find(card => Number(card.dataset.orderId) === Number(orderId)) || null;
    }

    function getOrderCards(group) {
        return Array.from(group.querySelectorAll('.delivery-route-order-card[data-order-id]'));
    }

    function markOrderCardAsDone(card) {
        if (!card) return;

        card.dataset.completable = 'false';
        card.dataset.deliveryDone = 'true';
        card.classList.remove('is-selected-for-completion', 'is-not-completable');
        card.classList.add('is-delivery-done');

        const checkbox = card.querySelector('.delivery-route-complete-check');
        if (checkbox) {
            checkbox.checked = false;
            checkbox.disabled = true;
        }

        const checkLabel = card.querySelector('.delivery-route-check-label');
        if (checkLabel) {
            checkLabel.classList.add('is-disabled');
            const text = checkLabel.querySelector('span');
            if (text) text.textContent = '배송완료';
        }

        const statusBadge = card.querySelector('[data-delivery-route-status-badge]');
        if (statusBadge) {
            statusBadge.classList.remove(
                'bg-info',
                'bg-secondary',
                'bg-warning',
                'bg-warning-subtle',
                'text-dark',
                'text-warning'
            );
            statusBadge.classList.add('bg-success');
            statusBadge.textContent = '배송완료';
        }

        const orderNumberMeta = card.querySelector('.delivery-route-order-number small');
        if (orderNumberMeta) {
            orderNumberMeta.textContent = '배송완료';
        }
    }

    function updateGroupCompletionCounters(group, doneCount, orderCount) {
        group.querySelectorAll('[data-delivery-route-done-count]').forEach(element => {
            element.textContent = String(doneCount);
        });

        group.querySelectorAll('[data-delivery-route-total-count]').forEach(element => {
            element.textContent = String(orderCount);
        });
    }

    function updateGroupCompletionBadge(group, doneCount, orderCount, allCompleted) {
        const badge = group.querySelector('[data-delivery-route-group-completion]');
        if (!badge) return;

        badge.classList.remove(
            'bg-success',
            'text-white',
            'bg-warning-subtle',
            'text-warning',
            'bg-light',
            'text-dark'
        );

        if (allCompleted) {
            badge.classList.add('bg-success', 'text-white');
        } else if (doneCount > 0) {
            badge.classList.add('bg-warning-subtle', 'text-warning');
        } else {
            badge.classList.add('bg-light', 'text-dark');
        }

        const doneElement = badge.querySelector('[data-delivery-route-done-count]');
        const totalElement = badge.querySelector('[data-delivery-route-total-count]');
        if (doneElement) doneElement.textContent = String(doneCount);
        if (totalElement) totalElement.textContent = String(orderCount);
    }

    function updateGroupBulkState(group, completableCount, allCompleted) {
        const bulkBar = group.querySelector('.delivery-route-bulk-bar');
        const selectAllLabel = group.querySelector('.delivery-route-select-all-label');
        const selectAll = group.querySelector('.delivery-route-group-select-all');
        const completeButton = group.querySelector('[data-delivery-route-complete-button]');
        const progress = getGroupProgressElement(group);

        if (bulkBar) {
            bulkBar.classList.toggle('is-completed', allCompleted);
        }

        if (selectAllLabel) {
            selectAllLabel.classList.toggle('is-disabled', allCompleted || completableCount === 0);

            const strong = selectAllLabel.querySelector('strong');
            const small = selectAllLabel.querySelector('small');

            if (strong) {
                strong.textContent = allCompleted
                    ? '모든 주문 배송완료'
                    : '완료 대상 전체 선택';
            }

            if (small) {
                small.textContent = allCompleted
                    ? '추가 완료처리할 주문이 없습니다.'
                    : `생산완료 처리 가능 ${completableCount}건`;
            }
        }

        if (selectAll) {
            selectAll.checked = false;
            selectAll.indeterminate = false;
            selectAll.disabled = allCompleted || completableCount === 0;
        }

        if (completeButton) {
            completeButton.disabled = true;

            const icon = completeButton.querySelector('i');
            const label = completeButton.querySelector('[data-delivery-route-complete-label]');
            const countBadge = completeButton.querySelector('[data-delivery-route-selected-count]');

            if (icon) {
                icon.className = allCompleted
                    ? 'ri-checkbox-circle-line me-1'
                    : 'ri-camera-line me-1';
            }

            if (label) {
                label.textContent = allCompleted ? '전체 배송완료' : '배송완료처리';
            }

            if (countBadge) {
                countBadge.textContent = '0';
                countBadge.hidden = allCompleted;
            }
        }

        if (progress && !group.classList.contains('is-freight')) {
            progress.textContent = allCompleted
                ? '전체 완료'
                : (completableCount > 0 ? `선택 0/${completableCount}` : '완료 대기');
        }
    }

    function updatePageDeliveryDoneCount(snapshot) {
        const summary = document.getElementById('delivery-route-summary-done-count');
        if (!summary) return;

        if (snapshot && snapshot.pageDeliveryDoneCount >= 0) {
            summary.textContent = String(snapshot.pageDeliveryDoneCount);
            return;
        }

        const doneCount = document.querySelectorAll('.delivery-route-order-card.is-delivery-done').length;
        summary.textContent = String(doneCount);
    }

    function normalizeRenderedRoute(groups) {
        document.querySelectorAll('[data-delivery-route-group-list]').forEach(list => {
            reorderGroupList(list);
        });

        groups.forEach(group => {
            reorderOrderCards(group);
        });

        refreshRouteSequences();
    }

    function reorderGroupList(list) {
        if (!list) return;

        const groups = Array.from(list.children)
            .filter(element => element.classList && element.classList.contains('delivery-route-group'));
        const pendingGroups = groups.filter(group => group.dataset.allCompleted !== 'true'
            && !group.classList.contains('is-fully-completed'));
        const completedGroups = groups.filter(group => !pendingGroups.includes(group));

        const oldDivider = Array.from(list.children).find(element =>
            element.hasAttribute && element.hasAttribute('data-delivery-route-completed-divider'));
        if (oldDivider) oldDivider.remove();

        pendingGroups.forEach(group => list.appendChild(group));

        if (completedGroups.length > 0) {
            const divider = createCompletedGroupDivider(completedGroups.length);
            list.appendChild(divider);
            completedGroups.forEach(group => list.appendChild(group));
        }
    }

    function createCompletedGroupDivider(count) {
        const divider = document.createElement('div');
        divider.className = 'delivery-route-completed-divider';
        divider.setAttribute('data-delivery-route-completed-divider', 'true');
        divider.innerHTML = '<span><i class="ri-checkbox-circle-line" aria-hidden="true"></i>배송완료 묶음</span>'
            + `<b>${count}곳</b>`;
        return divider;
    }

    function reorderOrderCards(group) {
        if (!group) return;

        const list = group.querySelector('.delivery-route-order-list');
        if (!list) return;

        const cards = Array.from(list.children)
            .filter(element => element.classList && element.classList.contains('delivery-route-order-card'));
        const pendingCards = cards.filter(card => !isDeliveryDoneCard(card));
        const completedCards = cards.filter(isDeliveryDoneCard);

        const oldDivider = Array.from(list.children).find(element =>
            element.hasAttribute && element.hasAttribute('data-delivery-route-order-completed-divider'));
        if (oldDivider) oldDivider.remove();

        pendingCards.forEach(card => list.appendChild(card));

        if (pendingCards.length > 0 && completedCards.length > 0) {
            const divider = document.createElement('div');
            divider.className = 'delivery-route-order-completed-divider';
            divider.setAttribute('data-delivery-route-order-completed-divider', 'true');
            divider.innerHTML = '<i class="ri-checkbox-circle-line" aria-hidden="true"></i>'
                + `<span>이 묶음의 배송완료 주문 ${completedCards.length}건</span>`;
            list.appendChild(divider);
        }

        completedCards.forEach(card => list.appendChild(card));
    }

    function isDeliveryDoneCard(card) {
        return Boolean(card)
            && (card.dataset.deliveryDone === 'true' || card.classList.contains('is-delivery-done'));
    }

    function refreshRouteSequences() {
        let sequence = 1;

        ['direct', 'freight'].forEach(section => {
            const list = document.querySelector(`[data-delivery-route-group-list="${section}"]`);
            if (!list) return;

            Array.from(list.children)
                .filter(element => element.classList && element.classList.contains('delivery-route-group'))
                .forEach(group => {
                    const sequenceElement = group.querySelector('.delivery-route-sequence');
                    if (sequenceElement) sequenceElement.textContent = String(sequence);
                    sequence += 1;
                });
        });
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
        const allCompleted = group.dataset.allCompleted === 'true';
        const selectAll = group.querySelector('.delivery-route-group-select-all');
        const completeButton = group.querySelector('[data-delivery-route-complete-button]');
        const countBadge = completeButton
            ? completeButton.querySelector('[data-delivery-route-selected-count]')
            : null;
        const progress = getGroupProgressElement(group);

        if (selectAll) {
            selectAll.checked = !allCompleted && totalCount > 0 && selectedCount === totalCount;
            selectAll.indeterminate = !allCompleted && selectedCount > 0 && selectedCount < totalCount;
            selectAll.disabled = allCompleted || totalCount === 0;
        }

        if (completeButton) {
            completeButton.disabled = allCompleted || selectedCount === 0;
        }

        if (countBadge) {
            countBadge.textContent = String(selectedCount);
            countBadge.hidden = allCompleted;
        }

        if (progress && !group.classList.contains('is-freight')) {
            progress.textContent = allCompleted
                ? '전체 완료'
                : (totalCount > 0 ? `선택 ${selectedCount}/${totalCount}` : '완료 대기');
        }

        group.classList.toggle('has-selection', selectedCount > 0);

        boxes.forEach(checkbox => {
            const card = checkbox.closest('.delivery-route-order-card');
            if (card) {
                card.classList.toggle('is-selected-for-completion', checkbox.checked);
            }
        });
    }

    function getGroupProgressElement(group) {
        if (!group) return null;

        const groupId = group.dataset.groupId || '';
        if (!groupId) return group.querySelector('.delivery-route-selection-progress');

        return document.querySelector(`[data-progress-for="${cssEscape(groupId)}"]`)
            || group.querySelector('.delivery-route-selection-progress');
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

    async function showMessage(title, text, icon) {
        const toastElement = document.getElementById('delivery-route-toast');
        const titleElement = document.getElementById('delivery-route-toast-title');
        const bodyElement = document.getElementById('delivery-route-toast-body');
        const iconElement = document.querySelector('#delivery-route-toast-icon i');
        const type = normalizeMessageType(icon);

        if (toastElement && titleElement && bodyElement
            && window.bootstrap && window.bootstrap.Toast) {

            titleElement.textContent = String(title || '알림');
            bodyElement.textContent = String(text || '');

            toastElement.classList.remove('is-success', 'is-warning', 'is-error', 'is-info');
            toastElement.classList.add(`is-${type}`);

            if (iconElement) {
                iconElement.className = resolveToastIconClass(type);
            }

            const options = {
                autohide: true,
                delay: type === 'error' ? 7000 : 4500
            };

            const toast = window.bootstrap.Toast.getOrCreateInstance
                ? window.bootstrap.Toast.getOrCreateInstance(toastElement, options)
                : new window.bootstrap.Toast(toastElement, options);

            toast.show();
            return null;
        }

        window.alert(`${title || '알림'}\n${text || ''}`);
        return null;
    }

    function normalizeMessageType(type) {
        const value = String(type || 'info').toLowerCase();
        if (value === 'success' || value === 'warning' || value === 'error') return value;
        return 'info';
    }

    function resolveToastIconClass(type) {
        switch (type) {
            case 'success':
                return 'ri-checkbox-circle-line';
            case 'warning':
                return 'ri-alert-line';
            case 'error':
                return 'ri-error-warning-line';
            default:
                return 'ri-information-line';
        }
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

    function forceGroupCollapsed(group) {
        if (!group) return;

        const toggle = group.querySelector('[data-delivery-route-toggle]');
        const bodyId = toggle ? toggle.getAttribute('aria-controls') : null;
        const body = bodyId ? document.getElementById(bodyId) : null;

        if (toggle) toggle.setAttribute('aria-expanded', 'false');
        group.classList.remove('is-open');

        if (body) {
            if (body._deliveryRouteTransitionHandler) {
                body.removeEventListener('transitionend', body._deliveryRouteTransitionHandler);
                body._deliveryRouteTransitionHandler = null;
            }

            body.hidden = true;
            body.style.height = '';
            body.style.overflow = '';
            body.style.transition = '';
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

    function normalizePositiveIds(values) {
        const source = Array.isArray(values) ? values : [];
        const result = [];
        const seen = new Set();

        source.forEach(value => {
            const id = Number(value);
            if (!Number.isSafeInteger(id) || id <= 0 || seen.has(id)) return;
            seen.add(id);
            result.push(id);
        });

        return result;
    }

    function toNonNegativeInteger(value, fallback) {
        const number = Number(value);
        if (Number.isSafeInteger(number) && number >= 0) return number;
        return fallback;
    }

    function cssEscape(value) {
        const text = String(value || '');

        if (window.CSS && typeof window.CSS.escape === 'function') {
            return window.CSS.escape(text);
        }

        return text.replace(/([ #;?%&,+*~':"!^$[\]()=>|/@])/g, '\\$1');
    }
})();
