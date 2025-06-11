document.addEventListener('DOMContentLoaded', () => {
	var searchField = document.querySelectorAll('[data-search]');
	if (searchField.length) {
		var searchResults = document.querySelectorAll('.search-results')
		var searchNoResults = document.querySelectorAll('.search-no-results');
		var searchTotal = document.querySelectorAll(".search-results div")[0].childElementCount;
		var searchTrending = document.querySelectorAll('.search-trending');
		var clearSearch = document.querySelectorAll('.clear-search')[0];
		clearSearch.addEventListener('click', function() {
			searchField[0].value = "";
			clearSearch.classList.add('disabled');
			searchNoResults[0].classList.add('disabled');
			searchResults[0].classList.add('disabled-search-list');
			if (searchTrending[0]) { searchTrending[0].classList.remove('disabled'); }
			var searchFilterItem = document.querySelectorAll('[data-filter-item]');
			for (let i = 0; i < searchFilterItem.length; i++) { searchFilterItem[i].classList.add('disabled'); }
		})
		function searchFunction() {
			var searchStr = searchField[0].value;
			var searchVal = searchStr.toLowerCase();
			if (searchVal != '') {
				clearSearch.classList.remove('disabled');
				searchResults[0].classList.remove('disabled-search-list');
				var searchFilterItem = document.querySelectorAll('[data-filter-item]');
				for (let i = 0; i < searchFilterItem.length; i++) {
					var searchData = searchFilterItem[i].getAttribute('data-filter-name');
					if (searchData.includes(searchVal)) {
						searchFilterItem[i].classList.remove('disabled');
						if (searchTrending.length) { searchTrending[0].classList.add('disabled'); }
					} else {
						searchFilterItem[i].classList.add('disabled');
						if (searchTrending.length) { searchTrending[0].classList.remove('disabled'); }
					}
					var disabledResults = document.querySelectorAll(".search-results div")[0].getElementsByClassName("disabled").length;
					if (disabledResults === searchTotal) {
						searchNoResults[0].classList.remove('disabled');
						if (searchTrending.length) { searchTrending[0].classList.add('disabled'); }
					} else {
						searchNoResults[0].classList.add('disabled');
						if (searchTrending.length) { searchTrending[0].classList.add('disabled'); }
					}
				}
			}
			if (searchVal === '') {
				clearSearch.classList.add('disabled');
				searchResults[0].classList.add('disabled-search-list');
				searchNoResults[0].classList.add('disabled');
				if (searchTrending.length) { searchTrending[0].classList.remove('disabled'); }
			}
		};

		searchField[0].addEventListener('keyup', function() { searchFunction(); })
		searchField[0].addEventListener('click', function() { searchFunction(); })

		var searchClick = document.querySelectorAll('.search-trending a');
		searchClick.forEach(el => el.addEventListener('click', event => {
			var trendingResult = el.querySelectorAll('span')[0].textContent.toLowerCase();
			searchField[0].value = trendingResult;
			searchField[0].click();
		}));
	}

	//Search Header
	var searchHeader = document.querySelectorAll('[data-toggle-search]');
	if (searchHeader) {
		searchHeader.forEach(el => el.addEventListener('click', event => {
			window.scrollTo({ top: 0, behavior: `smooth` })
			document.querySelectorAll('.header')[0].classList.toggle('header-search-active');
		})
		)
	};
});