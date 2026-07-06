/* amountExcelMasterGrid.js */
(function () {
    'use strict';

    const wrap = document.querySelector('.amount-excel-master-table-wrap');
    if (!wrap) return;

    const apiUrl = wrap.dataset.apiUrl;
    const thead = document.getElementById('amount-excel-master-thead');
    const tbody = document.getElementById('amount-excel-master-tbody');
    const totalEl = document.getElementById('amount-excel-master-total');
    const loadingEl = document.getElementById('amount-excel-master-loading');
    const emptyEl = document.getElementById('amount-excel-master-empty');
    const reloadBtn = document.querySelector('.amount-excel-master-reload-btn');

    const state = {
        columns: [],
        offset: 0,
        limit: 50,
        total: 0,
        hasMore: true,
        loading: false,
        sortField: '',
        sortDir: 'asc',
        filters: {}
    };

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    function buildUrl() {
        const params = new URLSearchParams();
        params.set('offset', state.offset);
        params.set('limit', state.limit);
        if (state.sortField) params.set('sortField', state.sortField);
        params.set('sortDir', state.sortDir);
        Object.entries(state.filters).forEach(([field, value]) => {
            if (value && value.trim()) params.set('f_' + field, value.trim());
        });
        return apiUrl + '?' + params.toString();
    }

    async function loadRows(reset) {
        if (state.loading) return;
        if (!reset && !state.hasMore) return;
        state.loading = true;
        loadingEl.classList.remove('d-none');
        try {
            if (reset) {
                state.offset = 0;
                state.hasMore = true;
                tbody.innerHTML = '';
            }
            const res = await fetch(buildUrl(), {headers: {'Accept': 'application/json'}});
            if (!res.ok) throw new Error('데이터 조회에 실패했습니다.');
            const data = await res.json();
            state.columns = data.columns || [];
            state.total = data.total || 0;
            state.hasMore = !!data.hasMore;
            totalEl.textContent = Number(state.total).toLocaleString();
            if (!thead.dataset.rendered || reset) renderHeader();
            renderRows(data.rows || []);
            state.offset += (data.rows || []).length;
            emptyEl.classList.toggle('d-none', tbody.children.length > 0);
        } catch (e) {
            alert(e.message || '데이터 처리 중 오류가 발생했습니다.');
        } finally {
            state.loading = false;
            loadingEl.classList.add('d-none');
        }
    }

    function renderHeader() {
        thead.dataset.rendered = 'true';
        const headerRow = document.createElement('tr');
        const filterRow = document.createElement('tr');

        headerRow.innerHTML = '<th class="amount-excel-master-id-col">ID</th>';
        filterRow.innerHTML = '<th class="amount-excel-master-id-col"></th>';

        state.columns.forEach(col => {
            const activeAsc = state.sortField === col.field && state.sortDir === 'asc' ? ' is-active' : '';
            const activeDesc = state.sortField === col.field && state.sortDir === 'desc' ? ' is-active' : '';
            const th = document.createElement('th');
            th.innerHTML = `
                <div class="amount-excel-master-head-cell">
                    <span title="${escapeHtml(col.header)}">${escapeHtml(col.header)}</span>
                    <span class="amount-excel-master-sort-box">
                        <button type="button" class="amount-excel-master-sort-btn${activeAsc}" data-field="${escapeHtml(col.field)}" data-dir="asc">▲</button>
                        <button type="button" class="amount-excel-master-sort-btn${activeDesc}" data-field="${escapeHtml(col.field)}" data-dir="desc">▼</button>
                    </span>
                </div>`;
            headerRow.appendChild(th);

            const fth = document.createElement('th');
            fth.innerHTML = `<input type="text" class="form-control form-control-sm amount-excel-master-filter" data-field="${escapeHtml(col.field)}" placeholder="검색" value="${escapeHtml(state.filters[col.field] || '')}">`;
            filterRow.appendChild(fth);
        });

        thead.innerHTML = '';
        thead.appendChild(headerRow);
        thead.appendChild(filterRow);
    }

    function renderRows(rows) {
        const fragment = document.createDocumentFragment();
        rows.forEach(row => {
            const tr = document.createElement('tr');
            tr.dataset.id = row.id;
            tr.innerHTML = `<td class="amount-excel-master-id-col">${escapeHtml(row.id)}</td>`;
            state.columns.forEach(col => {
                const td = document.createElement('td');
                td.className = 'amount-excel-master-cell';
                td.dataset.id = row.id;
                td.dataset.field = col.field;
                td.dataset.value = row[col.field] == null ? '' : row[col.field];
                td.title = td.dataset.value;
                td.textContent = td.dataset.value;
                tr.appendChild(td);
            });
            fragment.appendChild(tr);
        });
        tbody.appendChild(fragment);
    }

    function debounce(fn, delay) {
        let timer;
        return function (...args) {
            clearTimeout(timer);
            timer = setTimeout(() => fn.apply(this, args), delay);
        };
    }

    const handleFilter = debounce(function (event) {
        const input = event.target;
        if (!input.classList.contains('amount-excel-master-filter')) return;
        state.filters[input.dataset.field] = input.value;
        loadRows(true);
    }, 350);

    thead.addEventListener('input', handleFilter);

    thead.addEventListener('click', function (event) {
        const btn = event.target.closest('.amount-excel-master-sort-btn');
        if (!btn) return;
        state.sortField = btn.dataset.field;
        state.sortDir = btn.dataset.dir;
        renderHeader();
        loadRows(true);
    });

    tbody.addEventListener('click', function (event) {
        const td = event.target.closest('.amount-excel-master-cell');
        if (!td || td.querySelector('input')) return;
        activateCell(td);
    });

    function activateCell(td) {
        const originalValue = td.dataset.value || '';
        td.classList.add('is-editing');
        td.innerHTML = `<input type="text" class="form-control form-control-sm amount-excel-master-editor" value="${escapeHtml(originalValue)}">`;
        const input = td.querySelector('input');
        input.focus();
        input.select();

        input.addEventListener('keydown', async function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                await saveCell(td, input.value);
            }
            if (event.key === 'Escape') {
                restoreCell(td, originalValue);
            }
        });
        input.addEventListener('blur', function () {
            restoreCell(td, td.dataset.value || '');
        }, {once: true});
    }

    function restoreCell(td, value) {
        td.classList.remove('is-editing');
        td.dataset.value = value == null ? '' : value;
        td.title = td.dataset.value;
        td.textContent = td.dataset.value;
    }

    async function saveCell(td, value) {
        const id = td.dataset.id;
        const field = td.dataset.field;
        td.classList.add('is-saving');
        try {
            const res = await fetch(`${apiUrl}/${encodeURIComponent(id)}`, {
                method: 'PATCH',
                headers: {'Content-Type': 'application/json', 'Accept': 'application/json'},
                body: JSON.stringify({field, value})
            });
            if (!res.ok) {
                const text = await res.text();
                throw new Error(text || '수정 저장에 실패했습니다.');
            }
            const updated = await res.json();
            restoreCell(td, updated[field] == null ? '' : updated[field]);
            td.classList.add('is-saved');
            setTimeout(() => td.classList.remove('is-saved'), 700);
        } catch (e) {
            alert(e.message || '수정 저장 중 오류가 발생했습니다.');
            restoreCell(td, td.dataset.value || '');
        } finally {
            td.classList.remove('is-saving');
        }
    }

    wrap.addEventListener('scroll', function () {
        const nearBottom = wrap.scrollTop + wrap.clientHeight >= wrap.scrollHeight - 120;
        if (nearBottom) loadRows(false);
    });

    if (reloadBtn) {
        reloadBtn.addEventListener('click', function () {
            loadRows(true);
        });
    }

    loadRows(true);
})();
