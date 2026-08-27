/* productionPrint.js */
(function () {
    'use strict';

    function qs(selector) {
        return document.querySelector(selector);
    }

    function qsa(selector) {
        return Array.prototype.slice.call(document.querySelectorAll(selector));
    }

    var config = window.teamProductionOverviewConfig || {};
    var canCompleteProduction = config.canCompleteProduction === true
        || String(config.canCompleteProduction || '').toLowerCase() === 'true';

    var btnBulkDone = qs('#team-production-bulk-done-btn');
    var btnSticker = qs('#team-production-sticker-print-btn');
    var checkAll = qs('#team-production-check-all');

    var btnExcel = qs('#team-production-excel-download-btn');
    var btnDirectPrint = qs('#team-production-direct-print-btn');
    var outputModalElement = qs('#team-production-output-option-modal');
    var outputConfirmButton = qs('#team-production-output-confirm-btn');
    var outputFontSize = qs('#team-production-output-font-size');
    var outputCompanyName = qs('#team-production-output-company-name');
    var outputDeliveryDate = qs('#team-production-output-delivery-date');
    var outputFilterPreview = qs('#team-production-output-filter-preview');

    var outputMode = null;
    var outputModal = null;

    function getItemChecks() {
        return qsa('.team-production-check-item');
    }

    function getCheckedIds() {
        return getItemChecks()
            .filter(function (checkbox) {
                return checkbox.checked && !checkbox.disabled;
            })
            .map(function (checkbox) {
                return checkbox.getAttribute('data-order-id');
            })
            .filter(function (value) {
                return value !== null && value !== '';
            });
    }

    function syncButtonsAndCheckAll() {
        var items = getItemChecks().filter(function (checkbox) {
            return !checkbox.disabled;
        });
        var checked = getCheckedIds();
        var hasAny = checked.length > 0;

        if (btnSticker) {
            btnSticker.disabled = !hasAny;
        }

        if (btnBulkDone) {
            // 출력 대상 선택과 생산완료 권한은 별개입니다.
            // 재단팀은 체크/출력은 가능하지만 이 버튼은 항상 비활성 상태를 유지해야 합니다.
            btnBulkDone.disabled = !canCompleteProduction || !hasAny;
        }

        if (btnExcel) {
            btnExcel.disabled = !hasAny;
        }

        if (btnDirectPrint) {
            btnDirectPrint.disabled = !hasAny;
        }

        if (!checkAll) {
            return;
        }

        if (items.length === 0) {
            checkAll.checked = false;
            checkAll.indeterminate = false;
            return;
        }

        var checkedCount = items.filter(function (checkbox) {
            return checkbox.checked;
        }).length;

        checkAll.checked = checkedCount === items.length;
        checkAll.indeterminate = checkedCount > 0 && checkedCount < items.length;
    }

    function bindCheckboxEvents() {
        getItemChecks().forEach(function (checkbox) {
            checkbox.addEventListener('change', syncButtonsAndCheckAll);
        });

        if (!checkAll) {
            return;
        }

        checkAll.addEventListener('change', function () {
            getItemChecks()
                .filter(function (checkbox) {
                    return !checkbox.disabled;
                })
                .forEach(function (checkbox) {
                    checkbox.checked = checkAll.checked;
                });

            syncButtonsAndCheckAll();
        });
    }

    function appendHiddenInput(form, name, value) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value == null ? '' : String(value);
        form.appendChild(input);
    }

    function appendCsrf(form) {
        var token = qs('meta[name="_csrf"]');
        var parameter = qs('meta[name="_csrf_parameter"]');

        if (!token || !token.content) {
            return;
        }

        appendHiddenInput(
            form,
            parameter && parameter.content ? parameter.content : '_csrf',
            token.content
        );
    }

    function bindStickerPrint() {
        if (!btnSticker) {
            return;
        }

        btnSticker.addEventListener('click', function () {
            var ids = getCheckedIds();

            if (ids.length === 0) {
                return;
            }

            var form = document.createElement('form');
            form.method = 'POST';
            form.action = '/team/productionStickerPrint';
            form.target = '_blank';
            form.style.display = 'none';
            appendCsrf(form);

            ids.forEach(function (id) {
                appendHiddenInput(form, 'orderIds', id);
            });

            document.body.appendChild(form);
            form.submit();

            window.setTimeout(function () {
                form.remove();
            }, 1000);
        });
    }

    function selectedOptionText(selectElement) {
        if (!selectElement || selectElement.selectedIndex < 0) {
            return '';
        }

        var option = selectElement.options[selectElement.selectedIndex];
        return option ? String(option.textContent || '').trim() : '';
    }

    function inputValue(selector) {
        var element = qs(selector);
        return element ? String(element.value || '').trim() : '';
    }

    function buildFilterSummary() {
        var tokens = [];
        var orderId = inputValue('#team-production-orderId');
        var productName = inputValue('#team-production-productName');
        var categorySelect = qs('#team-production-productCategoryId');
        var dateTypeSelect = qs('#team-production-dateType');
        var statusSelect = qs('#team-production-statusFilter');
        var sizeSelect = qs('#team-production-size');
        var startDate = inputValue('#team-production-filter-form input[name="startDate"]');
        var endDate = inputValue('#team-production-filter-form input[name="endDate"]');
        var sortKey = inputValue('#team-production-sortKey');
        var sortDir = inputValue('#team-production-sortDir');

        if (orderId) {
            tokens.push('오더ID: ' + orderId);
        }

        if (productName) {
            tokens.push('제품명: ' + productName);
        }

        if (categorySelect) {
            tokens.push('제품분류: ' + selectedOptionText(categorySelect));
        }

        if (dateTypeSelect) {
            tokens.push('날짜기준: ' + selectedOptionText(dateTypeSelect));
        }

        if (startDate || endDate) {
            tokens.push('날짜: ' + (startDate || '처음') + ' ~ ' + (endDate || '현재'));
        } else {
            tokens.push('날짜: 전체 기간');
        }

        if (statusSelect) {
            tokens.push('상태: ' + selectedOptionText(statusSelect));
        }

        if (sizeSelect) {
            tokens.push('표시: ' + selectedOptionText(sizeSelect) + '건');
        }

        if (sortKey) {
            tokens.push('정렬: ' + sortKey + ' ' + (sortDir || 'ASC'));
        }

        return tokens.join(' | ');
    }

    function resolveOutputModal() {
        if (!outputModalElement || !window.bootstrap || !window.bootstrap.Modal) {
            return null;
        }

        if (!outputModal) {
            outputModal = window.bootstrap.Modal.getOrCreateInstance(outputModalElement);
        }

        return outputModal;
    }

    function updateOutputModal(mode, orderCount, filterSummary) {
        var modalTitle = outputModalElement
            ? outputModalElement.querySelector('.modal-title')
            : null;

        if (modalTitle) {
            modalTitle.textContent = mode === 'print'
                ? '생산 제작목록 바로출력 설정'
                : '생산 제작목록 엑셀 출력 설정';
        }

        if (outputConfirmButton) {
            outputConfirmButton.textContent = mode === 'print' ? '바로출력' : '엑셀 다운로드';
            outputConfirmButton.disabled = orderCount === 0;
        }

        if (outputFilterPreview) {
            outputFilterPreview.textContent = '출력 대상: 체크한 주문 ' + orderCount + '건 / ' + filterSummary;
        }
    }

    function openOutputOptions(mode, event) {
        if (event) {
            event.preventDefault();
            event.stopImmediatePropagation();
        }

        var orderIds = getCheckedIds();

        if (orderIds.length === 0) {
            window.alert('엑셀 다운로드/바로출력할 주문을 먼저 체크해 주세요.');
            return;
        }

        var modal = resolveOutputModal();

        if (!modal) {
            window.alert('출력 설정 모달을 초기화할 수 없습니다.');
            return;
        }

        outputMode = mode;
        if (outputCompanyName) {
            outputCompanyName.checked = true;
        }
        updateOutputModal(mode, orderIds.length, buildFilterSummary());
        modal.show();
    }

    function ensureExcelDownloadFrame() {
        var frameName = 'team-production-excel-download-frame';
        var frame = qs('iframe[name="' + frameName + '"]');

        if (!frame) {
            frame = document.createElement('iframe');
            frame.name = frameName;
            frame.style.display = 'none';
            frame.setAttribute('aria-hidden', 'true');
            document.body.appendChild(frame);
        }

        return frameName;
    }

    function normalizeFontSize(value) {
        var parsed = Number(value);

        if (!Number.isFinite(parsed)) {
            return 10;
        }

        parsed = Math.round(parsed);
        return Math.max(8, Math.min(14, parsed));
    }

    function submitOutput() {
        var mode = outputMode;
        var orderIds = getCheckedIds();

        if (mode !== 'excel' && mode !== 'print') {
            window.alert('출력 방식을 확인할 수 없습니다.');
            return;
        }

        if (orderIds.length === 0) {
            window.alert('엑셀 다운로드/바로출력할 주문을 먼저 체크해 주세요.');
            return;
        }

        var fontSize = normalizeFontSize(outputFontSize ? outputFontSize.value : 10);
        var includeCompanyName = !!(outputCompanyName && outputCompanyName.checked);
        var includeDeliveryDate = !!(outputDeliveryDate && outputDeliveryDate.checked);
        var filterSummary = buildFilterSummary();
        var form = document.createElement('form');
        var targetName;

        form.method = 'POST';
        form.action = mode === 'print'
            ? (config.printUrl || '/team/productionList/print')
            : (config.excelUrl || '/team/productionList/excel');
        form.style.display = 'none';

        if (mode === 'print') {
            targetName = 'team-production-print-' + Date.now();

            if (!window.open('', targetName)) {
                window.alert('팝업이 차단되어 바로출력 창을 열 수 없습니다. 브라우저 팝업을 허용해 주세요.');
                return;
            }

            form.target = targetName;
        } else {
            form.target = ensureExcelDownloadFrame();
        }

        appendCsrf(form);

        orderIds.forEach(function (id) {
            appendHiddenInput(form, 'orderIds', id);
        });

        appendHiddenInput(form, 'fontSize', fontSize);
        appendHiddenInput(form, 'includeCompanyName', includeCompanyName);
        appendHiddenInput(form, 'includeDeliveryDate', includeDeliveryDate);
        appendHiddenInput(form, 'filterSummary', filterSummary);

        document.body.appendChild(form);

        if (outputConfirmButton) {
            outputConfirmButton.disabled = true;
        }

        var modal = resolveOutputModal();
        if (modal) {
            modal.hide();
        }

        form.submit();

        window.setTimeout(function () {
            form.remove();
            if (outputConfirmButton) {
                outputConfirmButton.disabled = false;
            }
        }, 2000);
    }

    function bindOutputEvents() {
        if (btnExcel) {
            btnExcel.addEventListener('click', function (event) {
                openOutputOptions('excel', event);
            });
        }

        if (btnDirectPrint) {
            btnDirectPrint.addEventListener('click', function (event) {
                openOutputOptions('print', event);
            });
        }

        if (outputConfirmButton) {
            outputConfirmButton.addEventListener('click', function (event) {
                event.preventDefault();
                submitOutput();
            });
        }
    }

    bindCheckboxEvents();
    bindStickerPrint();
    bindOutputEvents();
    document.addEventListener('team-production:order-completed', syncButtonsAndCheckAll);
    syncButtonsAndCheckAll();
})();
