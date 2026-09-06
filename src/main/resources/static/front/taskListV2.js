(function () {
    'use strict';

	let suppressRowNavigationUntil = 0;

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
        initOrderDetailToggle();
        initProductNameHighlight();
		initTableHorizontalScroll();
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
                const dir = String(button.dataset.sortDir || '').toLowerCase();
                if (!field || (dir !== 'asc' && dir !== 'desc')) return;

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
			button.setAttribute('aria-pressed', 'false');
			button.removeAttribute('data-active-direction');
			const dir = String(button.dataset.sortDir || '').toLowerCase();
			button.title = dir === 'desc' ? '내림차순 정렬 추가' : '오름차순 정렬 추가';
        });
        specs.forEach(function (spec, index) {
			const button = document.querySelector('.task-list-sort-buttons button[data-sort-field="' + spec.field
				+ '"][data-sort-dir="' + spec.dir + '"]');
            if (!button) return;
            button.classList.add('task-list-sort-active');
			button.setAttribute('aria-pressed', 'true');
			button.dataset.activeDirection = spec.dir;
			button.title = (index + 1) + '순위 ' + (spec.dir === 'asc' ? '오름차순' : '내림차순')
				+ ' / 다시 누르면 이 정렬만 해제';
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


    function initOrderDetailToggle() {
        document.querySelectorAll('.task-list-order-detail-toggle[data-target]').forEach(function (button) {
            button.addEventListener('click', function (event) {
                event.preventDefault();
                event.stopPropagation();

                const targetSelector = button.dataset.target;
                if (!targetSelector) return;

                let slide = null;
                try {
                    slide = document.querySelector(targetSelector);
                } catch (error) {
                    console.error('[task-list] invalid order detail target', targetSelector, error);
                    return;
                }
                if (!slide) return;

                const willOpen = !slide.classList.contains('task-list-is-open');
                slide.classList.toggle('task-list-is-open', willOpen);
                button.classList.toggle('task-list-is-open', willOpen);
                button.setAttribute('aria-expanded', String(willOpen));
                slide.setAttribute('aria-hidden', String(!willOpen));

                const icon = button.querySelector('i');
                if (icon) {
                    icon.classList.toggle('fa-expand-alt', !willOpen);
                    icon.classList.toggle('fa-compress-alt', willOpen);
                }
            });
        });
    }

    function initProductNameHighlight() {
        const type = document.getElementById('task-list-text-type')?.value || '';
        const keyword = (document.getElementById('task-list-keyword')?.value || '').trim();
        if (type !== 'productName' || !keyword) return;

        document.querySelectorAll('.task-list-product-name-text').forEach(function (element) {
            highlightText(element, keyword);
        });
    }

    function highlightText(element, keyword) {
        if (!element || !keyword) return;

        const source = element.textContent || '';
        const sourceLower = source.toLocaleLowerCase();
        const keywordLower = keyword.toLocaleLowerCase();
        let cursor = 0;
        let matchIndex = sourceLower.indexOf(keywordLower, cursor);
        if (matchIndex < 0) return;

        const fragment = document.createDocumentFragment();
        while (matchIndex >= 0) {
            if (matchIndex > cursor) {
                fragment.appendChild(document.createTextNode(source.slice(cursor, matchIndex)));
            }

            const match = document.createElement('span');
            match.className = 'task-list-product-search-match';
            match.textContent = source.slice(matchIndex, matchIndex + keyword.length);
            fragment.appendChild(match);

            cursor = matchIndex + keyword.length;
            matchIndex = sourceLower.indexOf(keywordLower, cursor);
        }

        if (cursor < source.length) {
            fragment.appendChild(document.createTextNode(source.slice(cursor)));
        }

        element.replaceChildren(fragment);
    }

	function initTableHorizontalScroll() {
		const topScroll = document.getElementById('task-list-table-scroll-top');
		const mainScroll = document.getElementById('task-list-table-scroll-main');
		const spacer = document.getElementById('task-list-table-scroll-spacer');
		const frame = document.getElementById('task-list-table-scroll-frame');
		const table = mainScroll?.querySelector('.task-list-table');
		const status = document.getElementById('task-list-horizontal-scroll-status');
		if (!topScroll || !mainScroll || !spacer || !frame || !table) return;

		let syncing = false;
		const syncScroll = function (source, target) {
			if (syncing) return;
			syncing = true;
			target.scrollLeft = source.scrollLeft;
			syncing = false;
			updateScrollState();
		};

		topScroll.addEventListener('scroll', function () { syncScroll(topScroll, mainScroll); }, { passive: true });
		mainScroll.addEventListener('scroll', function () { syncScroll(mainScroll, topScroll); }, { passive: true });

		function updateDimensions() {
			spacer.style.width = Math.max(table.scrollWidth, mainScroll.clientWidth) + 'px';
			const scrollable = table.scrollWidth > mainScroll.clientWidth + 2;
			frame.classList.toggle('task-list-is-scrollable', scrollable);
			topScroll.classList.toggle('d-none', !scrollable);
			if (status) {
				status.textContent = scrollable
					? '상단·하단 스크롤바 또는 표 위 드래그로 이동하세요.'
					: '현재 화면 너비에 모든 열이 표시됩니다.';
			}
			updateScrollState();
		}

		function updateScrollState() {
			const max = Math.max(0, mainScroll.scrollWidth - mainScroll.clientWidth);
			const atStart = mainScroll.scrollLeft <= 2;
			const atEnd = mainScroll.scrollLeft >= max - 2;
			frame.classList.toggle('task-list-scroll-at-start', atStart);
			frame.classList.toggle('task-list-scroll-at-end', atEnd);
			topScroll.setAttribute('aria-valuemin', '0');
			topScroll.setAttribute('aria-valuemax', String(Math.round(max)));
			topScroll.setAttribute('aria-valuenow', String(Math.round(mainScroll.scrollLeft)));
		}

		function bindDragScroll(scroller) {
			let pointerId = null;
			let startX = 0;
			let startScrollLeft = 0;
			let dragged = false;

			scroller.addEventListener('pointerdown', function (event) {
				if (event.button !== 0 || event.pointerType === 'touch') return;
				if (event.target.closest('a, button, input, select, textarea, label')) return;
				pointerId = event.pointerId;
				startX = event.clientX;
				startScrollLeft = scroller.scrollLeft;
				dragged = false;
				scroller.setPointerCapture?.(pointerId);
			});

			scroller.addEventListener('pointermove', function (event) {
				if (pointerId !== event.pointerId) return;
				const delta = event.clientX - startX;
				if (!dragged && Math.abs(delta) < 5) return;
				dragged = true;
				event.preventDefault();
				document.body.classList.add('task-list-is-dragging');
				window.getSelection()?.removeAllRanges();
				scroller.scrollLeft = startScrollLeft - delta;
			});

			const finish = function (event) {
				if (pointerId == null || (event.pointerId != null && pointerId !== event.pointerId)) return;
				if (dragged) suppressRowNavigationUntil = Date.now() + 350;
				const finishedPointerId = pointerId;
				pointerId = null;
				dragged = false;
				document.body.classList.remove('task-list-is-dragging');
				try { scroller.releasePointerCapture?.(finishedPointerId); } catch (_) { /* 이미 해제된 포인터 */ }
			};

			scroller.addEventListener('pointerup', finish);
			scroller.addEventListener('pointercancel', finish);
			scroller.addEventListener('lostpointercapture', finish);
		}

		mainScroll.addEventListener('click', function (event) {
			if (Date.now() >= suppressRowNavigationUntil) return;
			event.preventDefault();
			event.stopImmediatePropagation();
		}, true);

		bindDragScroll(topScroll);
		bindDragScroll(mainScroll);
		if (window.ResizeObserver) {
			const observer = new ResizeObserver(updateDimensions);
			observer.observe(table);
			observer.observe(mainScroll);
		} else {
			window.addEventListener('resize', updateDimensions);
		}
		updateDimensions();
	}

    function initRowNavigation() {
        document.querySelectorAll('.task-list-main-row[data-detail-url]').forEach(function (row) {
            row.addEventListener('click', function (event) {
				if (Date.now() < suppressRowNavigationUntil) return;
                if (event.target.closest('a, button, input, select, textarea, label')) return;
                const url = row.dataset.detailUrl;
                if (url) window.location.href = url;
            });
        });
    }
})();
