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

(function () {
    'use strict';
    const viewport = document.querySelector('.as-console-page .as-table-scroll');
    const table = viewport?.querySelector('table');
    if (!table) return;

    const top = document.createElement('div');
    top.className = 'as-table-scroll-top';
    top.tabIndex = 0;
    top.setAttribute('role', 'region');
    top.setAttribute('aria-label', 'AS 목록 상단 가로 스크롤');
    const spacer = document.createElement('div');
    spacer.className = 'as-table-scroll-spacer';
    top.append(spacer);

    function createGuide(position) {
        const guide = document.createElement('div');
        guide.className = 'as-table-scroll-guide';
        const left = document.createElement('button');
        left.type = 'button';
        left.textContent = '‹ 왼쪽';
        left.setAttribute('aria-label', position + ' 표 왼쪽으로 이동');
        const message = document.createElement('span');
        const right = document.createElement('button');
        right.type = 'button';
        right.textContent = '오른쪽 ›';
        right.setAttribute('aria-label', position + ' 표 오른쪽으로 이동');
        left.addEventListener('click', () => move(-1));
        right.addEventListener('click', () => move(1));
        guide.append(left, message, right);
        return { guide, left, right, message };
    }
    function move(direction) {
        viewport.scrollLeft += direction * Math.max(160, viewport.clientWidth * 0.7);
        synchronize(viewport);
    }
    const upper = createGuide('상단');
    const lower = createGuide('하단');
    viewport.before(upper.guide, top);
    viewport.after(lower.guide);

    function render() {
        const max = Math.max(0, viewport.scrollWidth - viewport.clientWidth);
        const overflow = max > 1;
        const left = viewport.scrollLeft > 1;
        const right = viewport.scrollLeft < max - 1;
        top.hidden = !overflow;
        for (const item of [upper, lower]) {
            item.guide.hidden = !overflow;
            item.left.disabled = !left;
            item.right.disabled = !right;
            item.message.textContent = left && right ? '양쪽에 추가 항목이 있습니다 · 좌우 스크롤'
                : right ? '오른쪽에 추가 항목이 있습니다 →'
                : '← 왼쪽에 추가 항목이 있습니다 · 마지막 항목';
        }
    }
    function synchronize(source) {
        const target = source === top ? viewport : top;
        if (Math.abs(target.scrollLeft - source.scrollLeft) > 0.5) {
            target.scrollLeft = source.scrollLeft;
        }
        render();
    }
    top.addEventListener('scroll', () => synchronize(top), { passive: true });
    viewport.addEventListener('scroll', () => synchronize(viewport), { passive: true });
    function resize() {
        spacer.style.width = viewport.scrollWidth + 'px';
        top.scrollLeft = viewport.scrollLeft;
        render();
    }
    if (window.ResizeObserver) {
        const observer = new ResizeObserver(resize);
        observer.observe(viewport);
        observer.observe(table);
    }
    window.addEventListener('resize', resize, { passive: true });
    document.fonts?.ready.then(resize);
    resize();
})();
