(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const form = document.getElementById('as-list-filter-form');
        const sortInput = document.getElementById('as-list-sort-value');

        initSort(form, sortInput);
        initRegionFilter();
        initAdvancedReset();
        initAdvancedModalLayer();
        initDateValidation(form);
        initExcelDownload(form);
        initPagination(form);
        initDetailToggle();
        initMediaModal();
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

        document.querySelectorAll('.as-list-sort-buttons button').forEach(function (button) {
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

                sortInput.value = specs.map(function (item) {
                    return item.field + ':' + item.dir;
                }).join(',');

                const pageInput = form.querySelector('input[name="page"]');
                if (pageInput) pageInput.value = '0';
                form.action = '/customer/asList';
                submitForm(form);
            });
        });
    }

    function paintSortState(specs) {
        document.querySelectorAll('.as-list-sort-buttons button').forEach(function (button) {
            button.classList.remove('as-list-sort-active');
            button.removeAttribute('data-sort-order');
        });

        specs.forEach(function (spec, index) {
            const selector = '.as-list-sort-buttons button[data-sort-field="' + cssEscape(spec.field)
                + '"][data-sort-dir="' + cssEscape(spec.dir) + '"]';
            const button = document.querySelector(selector);
            if (!button) return;
            button.classList.add('as-list-sort-active');
            button.dataset.sortOrder = String(index + 1);
            button.title = (index + 1) + '순위 ' + (spec.dir === 'asc' ? '오름차순' : '내림차순') + ' / 다시 누르면 이 정렬만 해제';
        });
    }

    function initRegionFilter() {
        const province = document.getElementById('as-list-province');
        const city = document.getElementById('as-list-city');
        const district = document.getElementById('as-list-district');
        const cityWrap = document.getElementById('as-list-city-wrap');
        const districtWrap = document.getElementById('as-list-district-wrap');

        if (!province || !city || !district || !cityWrap || !districtWrap) return;

        province.addEventListener('change', async function () {
            resetSelect(city, '전체');
            resetSelect(district, '전체');
            cityWrap.classList.add('d-none');
            districtWrap.classList.add('d-none');

            if (!province.value) return;

            try {
                const response = await fetch('/customer/api/regions/province/' + encodeURIComponent(province.value) + '/children', {
                    headers: { 'Accept': 'application/json' }
                });
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
                console.error('[as-list] province region load failed', error);
            }
        });

        city.addEventListener('change', async function () {
            resetSelect(district, '전체');
            districtWrap.classList.add('d-none');
            if (!city.value) return;

            try {
                const response = await fetch('/customer/api/regions/city/' + encodeURIComponent(city.value) + '/districts', {
                    headers: { 'Accept': 'application/json' }
                });
                if (!response.ok) throw new Error('구/군 정보를 불러오지 못했습니다.');
                const data = await response.json();
                if (data.mode === 'DISTRICT' && Array.isArray(data.items) && data.items.length > 0) {
                    fillSelect(district, data.items, '전체');
                    districtWrap.classList.remove('d-none');
                }
            } catch (error) {
                console.error('[as-list] city region load failed', error);
            }
        });
    }

    function resetSelect(select, firstText) {
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
        const resetButton = document.querySelector('.as-list-advanced-reset');
        if (!resetButton) return;

        resetButton.addEventListener('click', function () {
            setValue('as-list-date-type', 'requested');
            setValue('as-list-start-date', '');
            setValue('as-list-end-date', '');
            setValue('as-list-billing-type', 'all');
            setValue('as-list-status', 'all');
            setValue('as-list-province', '');
            resetSelect(document.getElementById('as-list-city'), '전체');
            resetSelect(document.getElementById('as-list-district'), '전체');
            document.getElementById('as-list-city-wrap')?.classList.add('d-none');
            document.getElementById('as-list-district-wrap')?.classList.add('d-none');
        });
    }

    function setValue(id, value) {
        const element = document.getElementById(id);
        if (element) element.value = value;
    }

    function initAdvancedModalLayer() {
        const modal = document.getElementById('as-list-advanced-modal');
        if (!modal) return;

        modal.addEventListener('shown.bs.modal', function () {
            const backdrops = document.querySelectorAll('.modal-backdrop');
            const backdrop = backdrops.length > 0 ? backdrops[backdrops.length - 1] : null;
            if (backdrop) backdrop.classList.add('as-list-modal-backdrop');
        });

        modal.addEventListener('hidden.bs.modal', function () {
            document.querySelectorAll('.as-list-modal-backdrop').forEach(function (backdrop) {
                backdrop.classList.remove('as-list-modal-backdrop');
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
        const start = document.getElementById('as-list-start-date')?.value || '';
        const end = document.getElementById('as-list-end-date')?.value || '';
        if (start && end && start > end) {
            alert('From 날짜는 To 날짜보다 이후일 수 없습니다.');
            return false;
        }
        return true;
    }

    function initExcelDownload(form) {
        const button = document.getElementById('as-list-excel-download');
        if (!form || !button) return;

        button.addEventListener('click', function () {
            if (!validateDateRange()) return;
            const params = buildFormParams(form);
            params.delete('page');
            window.location.href = '/customer/asList/excel?' + params.toString();
        });
    }

    function initPagination(form) {
        if (!form) return;

        document.querySelectorAll('.as-list-page-btn[data-page]').forEach(function (button) {
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


    function initDetailToggle() {
        document.querySelectorAll('.as-list-detail-toggle').forEach(function (button) {
            button.addEventListener('click', function () {
                const target = document.querySelector(button.dataset.target || '');
                if (!target) return;

                if (window.jQuery) {
                    window.jQuery(target).stop(true, true).slideToggle(170, function () {
                        button.textContent = window.jQuery(target).is(':visible') ? '접기' : '넓게보기';
                    });
                } else {
                    const opened = target.style.display === 'block';
                    target.style.display = opened ? 'none' : 'block';
                    button.textContent = opened ? '넓게보기' : '접기';
                }
            });
        });
    }

    function initMediaModal() {
        const modalElement = document.getElementById('as-list-media-modal');
        const titleElement = document.getElementById('as-list-media-modal-title');
        const inner = document.getElementById('as-list-media-modal-inner');
        const carousel = document.getElementById('as-list-media-modal-carousel');
        if (!modalElement || !titleElement || !inner || !carousel) return;

        bindModalBackdrop(modalElement, 'as-list-modal-backdrop');

        document.querySelectorAll('.as-list-media-open').forEach(function (button) {
            button.addEventListener('click', function () {
                const sourceId = button.dataset.mediaSourceId;
                const source = sourceId ? document.getElementById(sourceId) : null;
                if (!source) return;

                const items = Array.from(source.querySelectorAll('.as-list-media-source-item'));
                if (items.length === 0) return;

                titleElement.textContent = button.dataset.mediaTitle || '미디어 보기';
                inner.innerHTML = '';

                items.forEach(function (item, index) {
                    const slide = document.createElement('div');
                    slide.className = 'carousel-item' + (index === 0 ? ' active' : '');

                    const url = item.dataset.mediaUrl || '';
                    const name = item.dataset.mediaName || '';
                    const type = item.dataset.mediaType || 'image';
                    let media;

                    if (type === 'video') {
                        media = document.createElement('video');
                        media.controls = true;
                        media.preload = 'metadata';
                        media.src = url;
                    } else {
                        media = document.createElement('img');
                        media.src = url;
                        media.alt = name;
                    }
                    slide.appendChild(media);
                    inner.appendChild(slide);
                });

                const controls = carousel.querySelectorAll('.carousel-control-prev, .carousel-control-next');
                controls.forEach(function (control) {
                    control.style.display = items.length > 1 ? '' : 'none';
                });

                if (window.bootstrap && window.bootstrap.Modal) {
                    window.bootstrap.Modal.getOrCreateInstance(modalElement).show();
                }
            });
        });

        modalElement.addEventListener('hidden.bs.modal', function () {
            inner.querySelectorAll('video').forEach(function (video) {
                try { video.pause(); } catch (ignore) { }
            });
            inner.innerHTML = '';
        });

        carousel.addEventListener('slide.bs.carousel', function () {
            inner.querySelectorAll('video').forEach(function (video) {
                try { video.pause(); } catch (ignore) { }
            });
        });
    }

    function bindModalBackdrop(modalElement, className) {
        if (!modalElement || !className) return;

        modalElement.addEventListener('shown.bs.modal', function () {
            const backdrops = document.querySelectorAll('.modal-backdrop');
            const backdrop = backdrops.length > 0 ? backdrops[backdrops.length - 1] : null;
            if (backdrop) backdrop.classList.add(className);
        });

        modalElement.addEventListener('hidden.bs.modal', function () {
            document.querySelectorAll('.' + className).forEach(function (backdrop) {
                backdrop.classList.remove(className);
            });
        });
    }

    function cssEscape(value) {
        if (window.CSS && typeof window.CSS.escape === 'function') return window.CSS.escape(value);
        return String(value).replace(/(["'\\.#:[\]()])/g, '\\$1');
    }
})();
