/*!
 * twocolumn-closed-default.js
 * - twocolumn 레이아웃에서 새로고침/페이지 로드시 "항상 닫힘"으로 시작
 * - app.js가 load/resize 단계에서 열림을 덮어쓰는 경우를 대비해
 *   최초 짧은 시간(기본 800ms) 동안만 '자동으로 다시 닫기'를 수행
 * - 사용자가 햄버거를 클릭하는 순간부터는 자동 강제 종료 (버튼 토글 존중)
 */
(function () {
	'use strict';

	var USER_TOGGLED = false;
	var FORCE_MS = 800; // 필요하면 1200 등으로 늘릴 수 있음

	function isTwocolumn() {
		return document.documentElement.getAttribute('data-layout') === 'twocolumn';
	}

	function applyClosed() {
		if (!isTwocolumn()) return;
		if (!document.body) return;

		// 닫힘 상태 강제
		document.body.classList.add('twocolumn-panel');

		var nav = document.getElementById('navbar-nav');
		if (nav) nav.classList.add('twocolumn-nav-hide');

		var hamburger = document.querySelector('.hamburger-icon');
		if (hamburger) hamburger.classList.add('open');
	}

	function bindUserToggleDetector() {
		// 상단 버튼/아이콘 어떤 걸 눌러도 사용자 토글로 간주
		function mark() { USER_TOGGLED = true; }

		document.addEventListener('click', function (e) {
			var t = e.target;

			// #topnav-hamburger-icon (버튼)
			if (t && (t.id === 'topnav-hamburger-icon' || (t.closest && t.closest('#topnav-hamburger-icon')))) {
				mark();
				return;
			}

			// .hamburger-icon (아이콘)
			if (t && (t.classList && t.classList.contains('hamburger-icon'))) {
				mark();
				return;
			}
			if (t && t.closest && t.closest('.hamburger-icon')) {
				mark();
			}
		}, true);
	}

	function forceCloseForShortTime() {
		var start = Date.now();

		function tick() {
			if (USER_TOGGLED) return;            // 사용자가 누르면 즉시 중단
			if (!isTwocolumn()) return;          // 레이아웃 바뀌면 중단
			applyClosed();

			if (Date.now() - start < FORCE_MS) {
				requestAnimationFrame(tick);
			}
		}

		requestAnimationFrame(tick);
	}

	// 최대한 빨리 사용자 토글 감지 바인딩
	bindUserToggleDetector();

	// DOM 전/후 모두 적용 시도
	if (document.readyState === 'loading') {
		// 아직 body 없을 수 있으니 DOMContentLoaded에서도 한번
		document.addEventListener('DOMContentLoaded', function () {
			applyClosed();
			forceCloseForShortTime();
		});
	} else {
		applyClosed();
		forceCloseForShortTime();
	}

	// load 이후에도 한번 더 (app.js가 load에서 세팅하는 경우 대응)
	window.addEventListener('load', function () {
		applyClosed();
		forceCloseForShortTime();
	});
})();
