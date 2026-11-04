(function () {
    'use strict';
    const form = document.getElementById('as-list-new-added-filter-form');
    if (!form) return;
    const currentUrl = new URL(window.location.href);
    const state = form.elements.sortState;
    const sorts = new Map();
    (state.value || '').split(',').forEach(token => {
        const [field, direction] = token.split(':');
        if (field && ['asc', 'desc'].includes(direction)) sorts.set(field, direction);
    });
    const summary = document.createElement('div');
    summary.className = 'as-query-summary';
    summary.setAttribute('aria-live', 'polite');
    const period = document.createElement('span');
    const start = form.elements.fromDate;
    const end = form.elements.toDate;
    period.textContent = '조회 중 기간 ' + (start.value || end.value
        ? (start.value || '처음') + ' ~ ' + (end.value || '현재') : '전체');
    summary.append(period);
    const help = document.createElement('span');
    help.textContent = '정렬은 선택한 순서대로 적용됩니다. 선택된 항목의 화살표를 누르면 해당 정렬이 해제됩니다.';
    summary.append(help);
    form.after(summary);

    const grid = form.querySelector('.row');
    grid.classList.add('as-filter-grid');
    Array.from(grid.children).filter(el => el.tagName === 'DIV').forEach(el => {
        const control = el.querySelector('[name]');
        if (control) el.dataset.filter = control.name;
        else el.classList.add('as-filter-submit');
    });

    const startBox = start.parentElement;
    const endBox = end.parentElement;
    const dates = document.createElement('div');
    dates.className = 'as-date-filter';
    dates.innerHTML = '<div class="as-date-heading"><span>날짜 조회</span><button type="button" class="as-date-toggle" role="switch" aria-label="범위검색"><span data-mode-label></span><span class="as-switch-track"></span></button></div><div class="as-date-single"><button type="button" data-day="-1" aria-label="하루 전">‹</button><input type="date" class="form-control" aria-label="조회일"><button type="button" data-day="1" aria-label="하루 후">›</button></div><div class="as-date-range"></div>';
    startBox.before(dates);
    const range = dates.querySelector('.as-date-range');
    const singleBox = dates.querySelector('.as-date-single');
    const single = singleBox.querySelector('input');
    const toggle = dates.querySelector('.as-date-toggle');
    startBox.querySelector('label').textContent = '시작';
    endBox.querySelector('label').textContent = '종료';
    range.append(startBox, endBox);
    const mode = document.createElement('input');
    mode.type = 'hidden';
    mode.name = 'dateMode';
    form.append(mode);
    let rangeMode = currentUrl.searchParams.get('dateMode') === 'range'
        || (currentUrl.searchParams.get('dateMode') !== 'single'
            && (start.value !== end.value));
    single.value = start.value || end.value;
    function renderDateMode() {
        mode.value = rangeMode ? 'range' : 'single';
        range.hidden = !rangeMode;
        singleBox.hidden = rangeMode;
        start.disabled = end.disabled = !rangeMode;
        toggle.setAttribute('aria-checked', String(rangeMode));
        toggle.querySelector('[data-mode-label]').textContent = rangeMode ? '범위검색' : '날짜별검색';
    }
    toggle.addEventListener('click', () => {
        rangeMode = !rangeMode;
        renderDateMode();
    });
    renderDateMode();
    const appliedDateMode = mode.value;
    dates.querySelectorAll('[data-day]').forEach(button => {
        button.addEventListener('click', () => {
            const value = single.value;
            const date = value ? new Date(value + 'T12:00:00') : new Date();
            date.setDate(date.getDate() + Number(button.dataset.day));
            single.value = date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0')
                + '-' + String(date.getDate()).padStart(2, '0');
            form.requestSubmit();
        });
    });
    form.addEventListener('submit', event => {
        if (rangeMode && start.value && end.value && start.value > end.value) {
            event.preventDefault();
            end.setCustomValidity('종료일은 시작일 이후로 선택해 주세요.');
            end.reportValidity();
            return;
        }
        end.setCustomValidity('');
        if (!rangeMode) {
            start.disabled = end.disabled = false;
            start.value = end.value = single.value;
        }
    });
    end.addEventListener('input', () => end.setCustomValidity(''));
    start.addEventListener('input', () => end.setCustomValidity(''));

    document.querySelectorAll('[data-as-sort]').forEach(button => {
        const field = button.dataset.asSort;
        const active = sorts.get(field) === button.dataset.dir;
        button.classList.toggle('is-active', active);
        button.setAttribute('aria-pressed', String(active));
        button.title = sorts.has(field) ? '이 항목 정렬 해제' : button.getAttribute('aria-label');
        button.addEventListener('click', () => {
            if (sorts.has(field)) sorts.delete(field);
            else sorts.set(field, button.dataset.dir);
            const url = new URL(window.location.href);
            url.searchParams.set('sortState', Array.from(sorts, pair => pair.join(':')).join(','));
            url.searchParams.delete('sortField');
            url.searchParams.delete('sortDir');
            url.searchParams.set('page', '0');
            url.searchParams.set('dateMode', appliedDateMode);
            window.location.href = url.href;
        });
    });
    Array.from(sorts.keys()).forEach((field, index) => {
        const badge = document.querySelector('[data-sort-priority="' + field + '"]');
        if (badge) badge.textContent = String(index + 1);
    });
    document.querySelectorAll('a[href]').forEach(link => {
        const url = new URL(link.href, window.location.href);
        if (url.origin === currentUrl.origin && ['/management/asList', '/management/asList/excel'].includes(url.pathname)) {
            url.searchParams.set('dateMode', appliedDateMode);
            link.href = url.href;
        }
    });
})();
