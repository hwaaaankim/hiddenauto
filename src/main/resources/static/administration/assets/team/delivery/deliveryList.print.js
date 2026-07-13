/* 기존 배송리스트 A4 가로 인쇄 버튼 */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const printButton = document.getElementById('delivery-list-added-printBtn');
        if (!printButton) return;

        printButton.addEventListener('click', function () {
            const deliveryDate = getDeliveryDate();
            const orderedOrderIds = getAllOrderIdsInCurrentDomOrder();

            if (!deliveryDate) {
                window.alert('인쇄할 배송 날짜를 선택해 주세요.');
                return;
            }

            if (orderedOrderIds.length === 0) {
                window.alert('인쇄할 배송 데이터가 없습니다.');
                return;
            }

            if (orderedOrderIds.length > 1000) {
                window.alert('한 번에 인쇄할 수 있는 주문은 최대 1,000건입니다.');
                return;
            }

            const printForm = document.getElementById('delivery-list-added-print-form');

            if (printForm) {
                submitPrintForm(printForm, deliveryDate, orderedOrderIds);
                return;
            }

            // 템플릿이 부분 적용된 환경을 위한 GET fallback입니다.
            const query = new URLSearchParams();
            query.set('deliveryDate', deliveryDate);
            query.set('orderIds', orderedOrderIds.join(','));

            const printWindow = window.open(`/team/deliveryPrint?${query.toString()}`, '_blank');

            if (!printWindow) {
                window.alert('인쇄 창이 차단되었습니다. 브라우저의 팝업 허용 설정을 확인해 주세요.');
                return;
            }

            try {
                printWindow.opener = null;
            } catch (ignored) {
                // 브라우저 정책상 opener 변경이 불가능해도 인쇄 창 자체는 정상 동작합니다.
            }
        });
    });

    function submitPrintForm(form, deliveryDate, orderedOrderIds) {
        const targetName = `hiddenbath_delivery_print_${Date.now()}`;
        const printWindow = window.open('', targetName);

        if (!printWindow) {
            window.alert('인쇄 창이 차단되었습니다. 브라우저의 팝업 허용 설정을 확인해 주세요.');
            return;
        }

        try {
            printWindow.opener = null;
        } catch (ignored) {
            // 브라우저 정책상 opener 변경이 불가능해도 인쇄 창 자체는 정상 동작합니다.
        }

        form.querySelectorAll('.delivery-print-dynamic-field').forEach(field => field.remove());
        form.target = targetName;
        form.appendChild(createHiddenField('deliveryDate', deliveryDate));

        orderedOrderIds.forEach(orderId => {
            form.appendChild(createHiddenField('orderIds', String(orderId)));
        });

        form.submit();

        // submit() 호출 시점에 요청 본문 직렬화가 완료되므로 동적 필드는 즉시 정리해도 됩니다.
        form.querySelectorAll('.delivery-print-dynamic-field').forEach(field => field.remove());
    }

    function createHiddenField(name, value) {
        const input = document.createElement('input');
        input.type = 'hidden';
        input.name = name;
        input.value = value;
        input.className = 'delivery-print-dynamic-field';
        return input;
    }

    function getDeliveryDate() {
        const dateInput = document.getElementById('deliveryDate');
        return dateInput ? String(dateInput.value || '').trim() : '';
    }

    function getAllOrderIdsInCurrentDomOrder() {
        const result = [];
        const seen = new Set();
        const sectionIds = ['pendingList', 'doneList', 'otherList'];

        sectionIds.forEach(sectionId => {
            const section = document.getElementById(sectionId);
            if (!section) return;

            section.querySelectorAll('[data-order-id]').forEach(element => {
                const orderId = Number(element.getAttribute('data-order-id'));

                if (!Number.isSafeInteger(orderId) || orderId <= 0 || seen.has(orderId)) {
                    return;
                }

                seen.add(orderId);
                result.push(orderId);
            });
        });

        return result;
    }
})();
