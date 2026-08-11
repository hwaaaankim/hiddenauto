(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const form = document.getElementById('task-list-filter-form');
        const sortInput = document.getElementById('task-list-sort-value');

        initSort(form, sortInput);
        initRegionFilter();
        initAdvancedReset();
        initAdvancedModalLayer();
        initDateValidation(form);
        initExcelDownload(form);
        initPagination(form);
        initRowNavigation();
    });

    function parseSort(raw) {
        if (!raw) return [];
        return raw.split(',').map(function (token) {
            const parts = token.split(':');
            if (parts.length !== 2) return null;
            const field = parts[0].trim();
            const dir = parts[1].trim().toLowerCase();
            if (!field || (dir !== 'asc' && dir !== 'desc')) return null;
            return { field: field, dir: dir };
        }).filter(Boolean);
    }

    function initSort(form, sortInput) {
        if (!form || !sortInput) return;
        let specs = parseSort(sortInput.value);
        paintSortState(specs);

        document.querySelectorAll('.task-list-sort-buttons button').forEach(function (button) {
            button.addEventListener('click', function () {
                const field = button.dataset.sortField;
                const dir = button.dataset.sortDir;
                if (!field || !dir) return;

                const fieldIndex = specs.findIndex(function (item) { return item.field === field; });
                if (fieldIndex >= 0 && specs[fieldIndex].dir === dir) {
                    specs.splice(fieldIndex, 1);
                } else if (fieldIndex >= 0) {
                    specs[fieldIndex] = { field: field, dir: dir };
                } else {
                    specs.push({ field: field, dir: dir });
                }

                sortInput.value = specs.map(function (item) { return item.field + ':' + item.dir; }).join(',');
                const pageInput = form.querySelector('input[name="page"]');
                if (pageInput) pageInput.value = '0';
                form.action = '/customer/taskList';
                submitForm(form);
            });
        });
    }

    function paintSortState(specs) {
        document.querySelectorAll('.task-list-sort-buttons button').forEach(function (button) {
            button.classList.remove('task-list-sort-active');
        });
        specs.forEach(function (spec, index) {
            const button = document.querySelector('.task-list-sort-buttons button[data-sort-field="' + spec.field + '"][data-sort-dir="' + spec.dir + '"]');
            if (!button) return;
            button.classList.add('task-list-sort-active');
            button.title = (index + 1) + '순위 ' + (spec.dir === 'asc' ? '오름차순' : '내림차순') + ' / 다시 누르면 이 정렬만 해제';
        });
    }

    function initRegionFilter() {
        const province = document.getElementById('task-list-province');
        const city = document.getElementById('task-list-city');
        const district = document.getElementById('task-list-district');
        const cityWrap = document.getElementById('task-list-city-wrap');
        const districtWrap = document.getElementById('task-list-district-wrap');
        if (!province || !city || !district || !cityWrap || !districtWrap) return;

        province.addEventListener('change', async function () {
            resetSelect(city, '전체');
            resetSelect(district, '전체');
            cityWrap.classList.add('d-none');
            districtWrap.classList.add('d-none');
            if (!province.value) return;

            try {
                const response = await fetch('/customer/api/regions/province/' + encodeURIComponent(province.value) + '/children', { headers: { 'Accept': 'application/json' } });
                if (!response.ok) throw new Error('지역 정보를 불러오지 못했습니다.');
                const data = await response.json();
                if (data.mode === 'CITY') {
                    fillSelect(city, data.items, '전체');
                    cityWrap.classList.remove('d-none');
                } else if (data.mode === 'DISTRICT') {
                    fillSelect(district, data.items, '전체');
                    districtWrap.classList.remove('d-none');
                }
            } catch (error) {
                console.error('[task-list] province region load failed', error);
            }
        });

        city.addEventListener('change', async function () {
            resetSelect(district, '전체');
            districtWrap.classList.add('d-none');
            if (!city.value) return;
            try {
                const response = await fetch('/customer/api/regions/city/' + encodeURIComponent(city.value) + '/districts', { headers: { 'Accept': 'application/json' } });
                if (!response.ok) throw new Error('구/군 정보를 불러오지 못했습니다.');
                const data = await response.json();
                if (data.mode === 'DISTRICT' && Array.isArray(data.items) && data.items.length > 0) {
                    fillSelect(district, data.items, '전체');
                    districtWrap.classList.remove('d-none');
                }
            } catch (error) {
                console.error('[task-list] city region load failed', error);
            }
        });
    }

    function resetSelect(select, firstText) {
        if (!select) return;
        select.innerHTML = '';
        const option = document.createElement('option');
        option.value = '';
        option.textContent = firstText;
        select.appendChild(option);
    }

    function fillSelect(select, items, firstText) {
        resetSelect(select, firstText);
        (items || []).forEach(function (item) {
            const option = document.createElement('option');
            option.value = String(item.id);
            option.textContent = item.name || '-';
            select.appendChild(option);
        });
    }

    function initAdvancedReset() {
        const resetButton = document.querySelector('.task-list-advanced-reset');
        if (!resetButton) return;
        resetButton.addEventListener('click', function () {
            setValue('task-list-date-type', 'created');
            setValue('task-list-start-date', '');
            setValue('task-list-end-date', '');
            setValue('task-list-status', 'all');
            setValue('task-list-category', 'all');
            setValue('task-list-province', '');
            resetSelect(document.getElementById('task-list-city'), '전체');
            resetSelect(document.getElementById('task-list-district'), '전체');
            document.getElementById('task-list-city-wrap')?.classList.add('d-none');
            document.getElementById('task-list-district-wrap')?.classList.add('d-none');
        });
    }

    function setValue(id, value) {
        const element = document.getElementById(id);
        if (element) element.value = value;
    }

    function initAdvancedModalLayer() {
        const modal = document.getElementById('task-list-advanced-modal');
        if (!modal) return;

        modal.addEventListener('shown.bs.modal', function () {
            const backdrops = document.querySelectorAll('.modal-backdrop');
            const backdrop = backdrops.length > 0 ? backdrops[backdrops.length - 1] : null;
            if (backdrop) backdrop.classList.add('task-list-modal-backdrop');
        });

        modal.addEventListener('hidden.bs.modal', function () {
            document.querySelectorAll('.task-list-modal-backdrop').forEach(function (backdrop) {
                backdrop.classList.remove('task-list-modal-backdrop');
            });
        });
    }

    function initDateValidation(form) {
        if (!form) return;
        form.addEventListener('submit', function (event) {
            if (!validateDateRange()) {
                event.preventDefault();
            }
        });
    }

    function validateDateRange() {
        const start = document.getElementById('task-list-start-date')?.value || '';
        const end = document.getElementById('task-list-end-date')?.value || '';
        if (start && end && start > end) {
            alert('From 날짜는 To 날짜보다 이후일 수 없습니다.');
            return false;
        }
        return true;
    }

    function initExcelDownload(form) {
        const button = document.getElementById('task-list-excel-download');
        if (!form || !button) return;

        button.addEventListener('click', function () {
            if (!validateDateRange()) return;
            const params = buildFormParams(form);
            params.delete('page');
            window.location.href = '/customer/taskList/excel?' + params.toString();
        });
    }

    function initPagination(form) {
        if (!form) return;

        document.querySelectorAll('.task-list-page-btn[data-page]').forEach(function (button) {
            button.addEventListener('click', function () {
                if (button.disabled) return;

                const page = button.dataset.page;
                const pageInput = form.querySelector('input[name="page"]');
                if (!pageInput || page == null || page === '') return;

                pageInput.value = page;
                submitForm(form);
            });
        });
    }

    function buildFormParams(form) {
        return new URLSearchParams(new FormData(form));
    }

    function submitForm(form) {
        if (typeof form.requestSubmit === 'function') {
            form.requestSubmit();
            return;
        }
        form.submit();
    }


    function initRowNavigation() {
        document.querySelectorAll('.task-list-main-row[data-detail-url]').forEach(function (row) {
            row.addEventListener('click', function (event) {
                if (event.target.closest('a, button, input, select, textarea, label')) return;
                const url = row.dataset.detailUrl;
                if (url) window.location.href = url;
            });
        });
    }
})();
