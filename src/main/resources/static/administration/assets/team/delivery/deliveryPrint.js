(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const printButton = document.getElementById('delivery-print-button');
        const closeButton = document.getElementById('delivery-print-close');

        if (printButton) {
            printButton.addEventListener('click', function () {
                window.print();
            });
        }

        if (closeButton) {
            closeButton.addEventListener('click', function () {
                window.close();
            });
        }

        preparePrintPages().then(function () {
            window.requestAnimationFrame(function () {
                window.requestAnimationFrame(function () {
                    window.print();
                });
            });
        }).catch(function (error) {
            console.error('배송리스트 인쇄 페이지 분할 실패', error);
            window.alert('인쇄 페이지를 구성하지 못했습니다. 창을 새로고침해주세요.');
        });
    });

    async function preparePrintPages() {
        if (document.fonts && document.fonts.ready) {
            try {
                await document.fonts.ready;
            } catch (ignored) {
                // 폰트 준비 상태를 확인할 수 없어도 기본 폰트로 페이지를 구성합니다.
            }
        }

        paginateRows();
    }

    function paginateRows() {
        const source = document.getElementById('delivery-print-source');
        const pagesHost = document.getElementById('delivery-print-pages');

        if (!source || !pagesHost) {
            throw new Error('인쇄 원본 또는 페이지 영역이 없습니다.');
        }

        const sourceRows = Array.from(source.querySelectorAll('tbody > tr[data-print-row]'));
        const emptyRow = source.querySelector('tbody > tr[data-print-empty-row]');

        pagesHost.replaceChildren();

        let currentPage = createEmptyPage(source, pagesHost);
        let currentBody = currentPage.querySelector('tbody');

        if (sourceRows.length === 0) {
            if (emptyRow) {
                currentBody.appendChild(emptyRow.cloneNode(true));
            }
            finalizePageNumbers(pagesHost);
            return;
        }

        sourceRows.forEach(function (sourceRow) {
            const row = sourceRow.cloneNode(true);
            currentBody.appendChild(row);

            if (!isTableOverflowing(currentPage)) {
                return;
            }

            currentBody.removeChild(row);

            if (!hasDataRows(currentBody)) {
                currentBody.appendChild(row);
                condenseOversizedPage(currentPage);
                currentPage = createEmptyPage(source, pagesHost);
                currentBody = currentPage.querySelector('tbody');
                return;
            }

            currentPage = createEmptyPage(source, pagesHost);
            currentBody = currentPage.querySelector('tbody');
            currentBody.appendChild(row);

            if (isTableOverflowing(currentPage)) {
                condenseOversizedPage(currentPage);
            }
        });

        removeTrailingEmptyPage(pagesHost);
        finalizePageNumbers(pagesHost);
    }

    function createEmptyPage(source, pagesHost) {
        const page = source.cloneNode(true);
        page.removeAttribute('id');
        page.removeAttribute('aria-hidden');
        page.className = 'delivery-print-page';

        const body = page.querySelector('tbody');
        if (body) body.replaceChildren();

        pagesHost.appendChild(page);
        return page;
    }

    function hasDataRows(tbody) {
        return Boolean(tbody && tbody.querySelector('tr[data-print-row]'));
    }

    function isTableOverflowing(page) {
        if (!page) return false;

        const wrap = page.querySelector('.delivery-print-table-wrap');
        const table = page.querySelector('.delivery-print-table');

        if (!wrap || !table) return false;

        const wrapHeight = wrap.getBoundingClientRect().height;
        const tableHeight = table.getBoundingClientRect().height;

        return tableHeight > wrapHeight + 0.75;
    }

    function condenseOversizedPage(page) {
        if (!page || !isTableOverflowing(page)) return;

        page.classList.add('is-condensed');
        void page.offsetHeight;

        if (!isTableOverflowing(page)) return;

        page.classList.add('is-extra-condensed');
        void page.offsetHeight;

        if (isTableOverflowing(page)) {
            scaleOversizedTableToFit(page);
        }
    }

    /**
     * 주소/메모가 매우 길어 주문 한 행 자체가 A4 본문보다 높은 예외 상황의 마지막 안전장치입니다.
     * 이 함수는 새 A4 페이지에 행 하나만 배치한 뒤에도 넘치는 경우에만 호출됩니다.
     */
    function scaleOversizedTableToFit(page) {
        const wrap = page ? page.querySelector('.delivery-print-table-wrap') : null;
        const table = page ? page.querySelector('.delivery-print-table') : null;

        if (!wrap || !table) return;

        table.style.removeProperty('width');
        table.style.removeProperty('transform');
        table.style.removeProperty('transform-origin');
        void table.offsetHeight;

        const wrapHeight = Math.max(1, wrap.getBoundingClientRect().height - 1);
        const naturalHeight = Math.max(1, table.getBoundingClientRect().height);
        let scale = Math.min(1, wrapHeight / naturalHeight);

        if (!(scale > 0) || scale >= 1) return;

        page.classList.add('is-scaled-to-fit');
        table.style.transformOrigin = 'top left';

        // 폭을 역보정하면 축소 후에도 A4 본문 가로폭을 끝까지 사용하므로 줄바꿈이 불필요하게 늘지 않습니다.
        for (let attempt = 0; attempt < 4; attempt += 1) {
            table.style.width = (100 / scale) + '%';
            table.style.transform = 'scale(' + scale + ')';
            void table.offsetHeight;

            const renderedHeight = table.getBoundingClientRect().height;
            if (renderedHeight <= wrapHeight + 0.5) break;

            scale *= wrapHeight / Math.max(1, renderedHeight);
        }

        page.dataset.tableScale = scale.toFixed(4);
    }

    function removeTrailingEmptyPage(pagesHost) {
        const pages = Array.from(pagesHost.querySelectorAll('.delivery-print-page'));
        if (pages.length <= 1) return;

        const lastPage = pages[pages.length - 1];
        const lastBody = lastPage.querySelector('tbody');

        if (lastBody && lastBody.children.length === 0) {
            lastPage.remove();
        }
    }

    function finalizePageNumbers(pagesHost) {
        const pages = Array.from(pagesHost.querySelectorAll('.delivery-print-page'));
        const total = Math.max(1, pages.length);
        const toolbarCount = document.getElementById('delivery-print-page-count');

        pages.forEach(function (page, index) {
            const number = page.querySelector('[data-page-number]');
            const pageTotal = page.querySelector('[data-page-total]');

            if (number) number.textContent = String(index + 1);
            if (pageTotal) pageTotal.textContent = String(total);
        });

        if (toolbarCount) {
            toolbarCount.textContent = String(total);
        }
    }
})();
