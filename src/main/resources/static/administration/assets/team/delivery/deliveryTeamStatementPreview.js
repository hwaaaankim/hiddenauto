/* deliveryTeamStatementPreview.js */
(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const openButton = document.getElementById('delivery-route-team-statement-preview-button');
        const modalElement = document.getElementById('delivery-route-team-statement-preview-modal');

        if (!openButton || !modalElement || !window.bootstrap) {
            return;
        }

        const modal = window.bootstrap.Modal.getOrCreateInstance(modalElement);
        const page = document.getElementById('delivery-route-page');
        const loading = document.getElementById('delivery-route-team-preview-loading');
        const feedback = document.getElementById('delivery-route-team-preview-feedback');
        const tableWrap = document.getElementById('delivery-route-team-preview-table-wrap');
        const tableBody = document.getElementById('delivery-route-team-preview-body');
        const dateText = document.getElementById('delivery-route-team-preview-date');
        const memberCount = document.getElementById('delivery-route-team-preview-member-count');
        const groupCount = document.getElementById('delivery-route-team-preview-group-count');
        const orderCount = document.getElementById('delivery-route-team-preview-order-count');
        const printButton = document.getElementById('delivery-route-team-preview-print-button');
        const downloadButton = document.getElementById('delivery-route-team-preview-download-button');
        const hiddenPrintButton = document.getElementById(
            'delivery-route-team-statement-site-horizontal-print-button'
        );
        const hiddenDownloadButton = document.getElementById(
            'delivery-route-team-statement-site-horizontal-download-button'
        );

        let currentPreview = null;
        let loadingPreview = false;

        openButton.addEventListener('click', function () {
            const deliveryDate = page ? page.dataset.deliveryDate : '';

            resetPreview(deliveryDate);
            modal.show();
            loadPreview(deliveryDate);
        });

        printButton.addEventListener('click', function () {
            if (!hasOutputOrders() || !hiddenPrintButton) {
                return;
            }

            modal.hide();
            hiddenPrintButton.click();
        });

        downloadButton.addEventListener('click', function () {
            if (!hasOutputOrders() || !hiddenDownloadButton) {
                return;
            }

            hiddenDownloadButton.click();
        });

        async function loadPreview(deliveryDate) {
            if (loadingPreview) {
                return;
            }

            if (!deliveryDate) {
                showError('배송일을 확인할 수 없습니다. 날짜를 다시 조회해 주세요.');
                return;
            }

            loadingPreview = true;

            try {
                const response = await fetch('/team/deliveryRoute/team-site-statement/preview', {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: buildJsonHeaders(),
                    body: JSON.stringify({
                        deliveryDate: deliveryDate,
                        layoutType: 'HORIZONTAL'
                    })
                });

                const body = await readResponseBody(response);

                if (!response.ok) {
                    throw new Error(resolveErrorMessage(body, '프리뷰를 불러오지 못했습니다.'));
                }

                currentPreview = body;
                renderPreview(body);
            } catch (error) {
                currentPreview = null;
                showError(error && error.message
                    ? error.message
                    : '프리뷰를 불러오는 중 오류가 발생했습니다.');
            } finally {
                loadingPreview = false;
            }
        }

        function resetPreview(deliveryDate) {
            currentPreview = null;
            loading.classList.remove('d-none');
            tableWrap.classList.add('d-none');
            feedback.classList.add('d-none');
            feedback.textContent = '';
            tableBody.innerHTML = '';
            dateText.textContent = deliveryDate || '-';
            memberCount.textContent = '0';
            groupCount.textContent = '0';
            orderCount.textContent = '0';
            printButton.disabled = true;
            downloadButton.disabled = true;
        }

        function renderPreview(preview) {
            const members = Array.isArray(preview && preview.members)
                ? preview.members
                : [];

            loading.classList.add('d-none');
            feedback.classList.add('d-none');
            tableWrap.classList.remove('d-none');

            dateText.textContent = preview && preview.deliveryDate
                ? preview.deliveryDate
                : '-';
            memberCount.textContent = String(toNumber(preview && preview.memberWithOrdersCount));
            groupCount.textContent = String(toNumber(preview && preview.totalGroupCount));
            orderCount.textContent = String(toNumber(preview && preview.totalOrderCount));

            if (members.length === 0) {
                tableBody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">배송팀 멤버가 없습니다.</td></tr>';
            } else {
                tableBody.innerHTML = members.map(function (member, index) {
                    const empty = toNumber(member.orderCount) === 0;

                    return '<tr class="' + (empty ? 'is-empty-member' : '') + '">' +
                        '<td>' + (index + 1) + '</td>' +
                        '<td>' + escapeHtml(member.memberId) + '</td>' +
                        '<td><strong>' + escapeHtml(member.memberName || '-') + '</strong></td>' +
                        '<td>' + escapeHtml(member.username || '-') + '</td>' +
                        '<td class="text-end"><strong>' + toNumber(member.groupCount) + '</strong>개</td>' +
                        '<td class="text-end"><strong>' + toNumber(member.orderCount) + '</strong>건</td>' +
                        '</tr>';
                }).join('');
            }

            const enabled = hasOutputOrders();
            printButton.disabled = !enabled || !hiddenPrintButton;
            downloadButton.disabled = !enabled || !hiddenDownloadButton;

            if (!enabled) {
                feedback.textContent = '선택한 날짜에 현장배송 또는 화물 명세서 대상 주문이 없습니다.';
                feedback.classList.remove('d-none');
                feedback.classList.remove('alert-danger');
                feedback.classList.add('alert-info');
            }
        }

        function showError(message) {
            loading.classList.add('d-none');
            tableWrap.classList.add('d-none');
            feedback.textContent = message;
            feedback.classList.remove('d-none', 'alert-info');
            feedback.classList.add('alert-danger');
            printButton.disabled = true;
            downloadButton.disabled = true;
        }

        function hasOutputOrders() {
            return currentPreview && toNumber(currentPreview.totalOrderCount) > 0;
        }

        function buildJsonHeaders() {
            const headers = {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            };
            const csrf = resolveCsrf();

            if (csrf.headerName && csrf.token) {
                headers[csrf.headerName] = csrf.token;
            }

            return headers;
        }

        function resolveCsrf() {
            const csrfInput = document.querySelector(
                '#delivery-route-complete-form input[type="hidden"][data-csrf-header]'
            );

            return {
                headerName: csrfInput ? csrfInput.dataset.csrfHeader : '',
                token: csrfInput ? csrfInput.value : ''
            };
        }

        async function readResponseBody(response) {
            const contentType = response.headers.get('content-type') || '';

            if (contentType.includes('application/json')) {
                return response.json();
            }

            return response.text();
        }

        function resolveErrorMessage(body, fallback) {
            if (body && typeof body === 'object' && body.message) {
                return String(body.message);
            }

            if (typeof body === 'string' && body.trim()) {
                return body.trim();
            }

            return fallback;
        }

        function toNumber(value) {
            const number = Number(value);
            return Number.isFinite(number) ? number : 0;
        }

        function escapeHtml(value) {
            return String(value == null ? '' : value)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        }
    });
})();
