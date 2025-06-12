document.addEventListener('DOMContentLoaded', () => {
	const categorySelect = document.getElementById('category-select');
	const seriesSelect = document.getElementById('series-select');
	const searchField = document.querySelector('[data-search]');
	const searchResults = document.querySelector('.search-results');
	const searchNoResults = document.querySelector('.search-no-results');
	const searchTrending = document.querySelector('.search-trending');
	const clearSearch = document.querySelector('.clear-search');

	let selectedCategory = '';
	let selectedSeries = '';
	let keyword = '';

	// ✅ 제품 필터링 통합 함수
	function filterProducts() {
		const items = document.querySelectorAll('[data-filter-item]');
		let visibleCount = 0;

		items.forEach(item => {
			const itemName = item.dataset.filterName.toLowerCase();
			const itemCategory = item.dataset.filterCategory;
			const itemSeries = item.dataset.filterSeries;

			const matchKeyword = !keyword || itemName.includes(keyword);
			const matchCategory = !selectedCategory || selectedCategory === itemCategory;
			const matchSeries = !selectedSeries || selectedSeries === itemSeries;

			if (matchKeyword && matchCategory && matchSeries) {
				item.classList.remove('disabled');
				visibleCount++;
			} else {
				item.classList.add('disabled');
			}
		});

		searchResults.classList.toggle('disabled-search-list', visibleCount === 0);
		searchNoResults.classList.toggle('disabled', visibleCount !== 0);
		searchTrending.classList.toggle('disabled', keyword !== '' || visibleCount === 0);
		clearSearch.classList.toggle('disabled', keyword === '');
	}

	// ✅ 키워드 검색 함수 (기존 구조 유지 + 확장)
	function searchFunction() {
		keyword = searchField.value.trim().toLowerCase(); // 추가
		filterProducts(); // 추가
	}

	// ✅ 초기화
	clearSearch.addEventListener('click', function() {
		searchField.value = "";
		keyword = "";
		clearSearch.classList.add('disabled');
		searchNoResults.classList.add('disabled');
		searchResults.classList.add('disabled-search-list');
		if (searchTrending) searchTrending.classList.remove('disabled');
		const searchFilterItem = document.querySelectorAll('[data-filter-item]');
		for (let i = 0; i < searchFilterItem.length; i++) {
			searchFilterItem[i].classList.add('disabled');
		}
		filterProducts(); // 필터 재적용
	});

	// ✅ 키워드 입력 이벤트
	searchField.addEventListener('keyup', function() {
		searchFunction();
	});
	searchField.addEventListener('click', function() {
		searchFunction();
	});

	// ✅ 트렌딩 클릭 이벤트
	const searchClick = document.querySelectorAll('.search-trending a');
	searchClick.forEach(el => el.addEventListener('click', event => {
		const trendingResult = el.querySelector('span')?.textContent?.toLowerCase() ?? '';
		searchField.value = trendingResult;
		keyword = trendingResult;
		filterProducts();
	}));

	// ✅ 카테고리 선택 시 → 시리즈 동적 로딩
	categorySelect.addEventListener('change', async (e) => {
		selectedCategory = e.target.value;
		selectedSeries = '';
		seriesSelect.innerHTML = '<option value="">제품 중분류 선택</option>';
		seriesSelect.disabled = true;

		if (selectedCategory) {
			const res = await fetch(`/api/standard/standard-series?categoryId=${selectedCategory}`);
			const list = await res.json();
			list.forEach(series => {
				const opt = document.createElement('option');
				opt.value = series.id;
				opt.textContent = series.name;
				seriesSelect.appendChild(opt);
			});
			seriesSelect.disabled = false;
		}
		filterProducts();
	});

	// ✅ 시리즈 선택 시 필터 적용
	seriesSelect.addEventListener('change', (e) => {
		selectedSeries = e.target.value;

		const selectedOptionText = e.target.options[e.target.selectedIndex].text;
		if (selectedOptionText === '분류전체') {
			// '분류전체'일 경우 시리즈 필터 무시
			selectedSeries = '';
		}

		filterProducts();
	});


	// ✅ 헤더 검색 toggle 유지
	const searchHeader = document.querySelectorAll('[data-toggle-search]');
	if (searchHeader) {
		searchHeader.forEach(el =>
			el.addEventListener('click', () => {
				window.scrollTo({ top: 0, behavior: `smooth` });
				document.querySelector('.header')?.classList.toggle('header-search-active');
			})
		);
	}
	document.querySelectorAll('.product-select').forEach(link => {
        link.addEventListener('click', function(e) {
            e.preventDefault(); // 바로 이동 막기
            const confirmed = confirm("해당 제품을 선택하시겠습니까?\n옵션 선택화면으로 이동합니다.");
            if (confirmed) {
                location.href = this.getAttribute('href');
            }
        });
    });
});
