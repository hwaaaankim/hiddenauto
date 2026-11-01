/* Dispatch statement print window only. Real row height, never a fixed item count. */
(function () {
    'use strict';
    const safety = 3;
    let started = false;
    let ready = false;

    function copies(paper) { return Array.from(paper.querySelectorAll('.statement-copy')); }
    function itemBody(copy) { return copy.querySelector('.statement-item-table tbody'); }
    function footerNodes(copy) {
        return Array.from(copy.querySelectorAll('.statement-acceptance,.statement-signature,.statement-continuation,.statement-parcel-footer'));
    }
    function fits(copy) {
        const last = copy.lastElementChild;
        if (!last) return true;
        const css = getComputedStyle(copy);
        return last.offsetTop + last.offsetHeight + parseFloat(getComputedStyle(last).marginBottom || 0)
            + parseFloat(css.paddingBottom || 0) + safety <= copy.clientHeight;
    }
    function paperFits(paper) { return copies(paper).every(fits); }
    function rowText(cell) {
        const clone = cell.cloneNode(true);
        clone.querySelectorAll('br').forEach(br => br.replaceWith('\n'));
        return clone.textContent.replace(/\u00a0/g, ' ');
    }
    function updateOrderIds(paper) {
        const first = copies(paper)[0];
        const ids = Array.from(itemBody(first).querySelectorAll('tr[data-statement-order-id]'))
            .map(row => row.dataset.statementOrderId).filter(Boolean);
        const text = Array.from(new Set(ids)).map(id => '#' + id).join(', ') || '-';
        copies(paper).forEach(copy => {
            const target = copy.querySelector('[data-statement-order-ids]');
            if (target) target.textContent = text;
        });
    }
    function appendRow(paper, row) {
        const added = copies(paper).map(copy => {
            const node = row.cloneNode(true); itemBody(copy).append(node); return node;
        });
        updateOrderIds(paper);
        return added;
    }
    function removeRows(paper, nodes) { nodes.forEach(node => node.remove()); updateOrderIds(paper); }

    function paginateGroup(originals) {
        const source = originals[0];
        const firstCopy = copies(source)[0];
        const rows = originals.flatMap(paper => Array.from(itemBody(copies(paper)[0])
            .querySelectorAll('tr[data-statement-item="true"]')).map(row => row.cloneNode(true)));
        const finalCopy = copies(originals[originals.length - 1])[0];
        const finalFooter = footerNodes(finalCopy).map(node => node.cloneNode(true));
        const parcel = firstCopy.classList.contains('statement-copy-parcel');
        const continuation = document.createElement('div');
        continuation.className = parcel ? 'statement-parcel-footer' : 'statement-continuation';
        continuation.textContent = parcel ? '' : '품목 계속 - 확인란은 마지막 페이지에 표시됩니다.';
        const result = [];

        function newPage() {
            const paper = source.cloneNode(true);
            paper.dataset.statementPaginated = 'true';
            copies(paper).forEach(copy => {
                itemBody(copy).replaceChildren();
                footerNodes(copy).forEach(node => node.remove());
                // Reserve the final acknowledgement/signature height on every page.
                finalFooter.forEach(node => copy.append(node.cloneNode(true)));
                const part = copy.querySelector('.statement-page-part');
                if (part) part.textContent = '999 / 999';
            });
            originals[0].before(paper);
            updateOrderIds(paper);
            if (!paperFits(paper)) {
                paper.remove();
                throw new Error('명세서의 거래처·주소 등 기본 정보가 한 페이지 높이를 넘습니다. 인쇄를 중단했으며 원본 데이터는 유지됩니다.');
            }
            result.push(paper);
            return paper;
        }

        let paper = newPage();
        for (const originalRow of rows) {
            let nodes = appendRow(paper, originalRow);
            if (paperFits(paper)) continue;
            removeRows(paper, nodes);
            if (itemBody(copies(paper)[0]).children.length) paper = newPage();
            nodes = appendRow(paper, originalRow);
            if (paperFits(paper)) continue;
            removeRows(paper, nodes);

            // A single unusually long product/memo row must also continue, not be clipped.
            const remaining = Array.from(originalRow.cells).map(cell => Array.from(rowText(cell)));
            const no = remaining[0].join('');
            const quantity = remaining[4].join('');
            let continued = false;
            while (remaining.some((text, index) => index !== 0 && index !== 4 && text.length)) {
                const fragment = originalRow.cloneNode(true);
                fragment.dataset.statementContinuation = continued ? 'true' : 'false';
                Array.from(fragment.cells).forEach(cell => { cell.textContent = ''; });
                fragment.cells[0].textContent = no + (continued ? ' (계속)' : '');
                fragment.cells[4].textContent = continued ? '' : quantity;
                let consumed = 0;
                for (const index of [1, 2, 3, 5]) {
                    if (!fragment.cells[index] || !remaining[index].length) continue;
                    let low = 0, high = remaining[index].length;
                    while (low < high) {
                        const middle = Math.ceil((low + high) / 2);
                        fragment.cells[index].textContent = remaining[index].slice(0, middle).join('');
                        const trial = appendRow(paper, fragment);
                        const ok = paperFits(paper);
                        removeRows(paper, trial);
                        if (ok) low = middle; else high = middle - 1;
                    }
                    fragment.cells[index].textContent = remaining[index].slice(0, low).join('');
                    remaining[index] = remaining[index].slice(low);
                    consumed += low;
                }
                if (!consumed) throw new Error('명세서 품목을 페이지에 배치할 수 없어 인쇄를 중단했습니다. 내용이 잘린 상태로 출력하지 않습니다.');
                appendRow(paper, fragment);
                if (!paperFits(paper)) throw new Error('명세서 페이지 높이를 확인하지 못했습니다. 인쇄를 다시 준비해 주세요.');
                continued = true;
                if (remaining.some((text, index) => index !== 0 && index !== 4 && text.length)) paper = newPage();
            }
        }

        result.forEach((page, index) => {
            copies(page).forEach(copy => {
                const part = copy.querySelector('.statement-page-part');
                if (part) part.textContent = (index + 1) + ' / ' + result.length;
                if (index < result.length - 1) {
                    footerNodes(copy).forEach(node => node.remove());
                    copy.append(continuation.cloneNode(true));
                }
                if (parcel) {
                    const footer = copy.querySelector('.statement-parcel-footer');
                    if (footer) footer.textContent = '품목 ' + (index + 1) + ' / ' + result.length;
                }
            });
            // Retain the familiar blank writing rows only while there is real room.
            const minimum = document.body.classList.contains('layout-vertical') ? 5 : 8;
            while (itemBody(copies(page)[0]).children.length < minimum) {
                const blank = document.createElement('tr');
                blank.dataset.statementItem = 'false';
                for (let col = 0; col < 6; col++) { const cell = document.createElement('td'); cell.innerHTML = '&nbsp;'; blank.append(cell); }
                const nodes = appendRow(page, blank);
                if (!paperFits(page)) { removeRows(page, nodes); break; }
            }
            if (!paperFits(page)) throw new Error('명세서의 최종 확인란이 출력 영역을 넘습니다. 잘림을 방지하기 위해 인쇄를 중단했습니다.');
        });
        originals.forEach(node => node.remove());
        return result;
    }

    function paginate() {
        if (ready) return document.querySelectorAll('.statement-paper').length;
        const papers = Array.from(document.querySelectorAll('.statement-paper'));
        const groups = new Map();
        papers.forEach((paper, index) => {
            const key = paper.dataset.statementGroup || String(index);
            if (!groups.has(key)) groups.set(key, []);
            groups.get(key).push(paper);
        });
        groups.forEach(paginateGroup);
        ready = true;
        document.body.dataset.statementReady = 'true';
        return document.querySelectorAll('.statement-paper').length;
    }

    async function start() {
        if (started) return;
        started = true;
        const status = document.getElementById('dispatch-statement-print-status') || document.createElement('div');
        status.className = 'statement-print-status';
        status.setAttribute('role', 'status');
        status.textContent = '품목과 비고 길이에 맞춰 인쇄 페이지를 준비하고 있습니다…';
        if (!status.isConnected) document.body.prepend(status);
        try {
            if (document.fonts) await document.fonts.ready;
            await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
            const count = paginate();
            status.textContent = '인쇄 준비 완료 · ' + count + '페이지 ';
            const button = document.createElement('button'); button.type = 'button'; button.textContent = '인쇄';
            button.onclick = () => window.print(); status.append(button);
            await new Promise(resolve => requestAnimationFrame(resolve));
            window.focus(); window.print();
        } catch (error) {
            document.body.dataset.statementError = 'true';
            status.setAttribute('role', 'alert');
            status.textContent = error.message || '명세서 페이지를 준비하지 못했습니다. 다시 시도해 주세요.';
        }
    }
    window.DispatchStatementPagination = { paginate: paginate, start: start };
    if (document.readyState === 'complete') start();
    else window.addEventListener('load', start, { once: true });
})();
