// ============ index-popup.js ============
(function() {
	'use strict';

	// ===== 셀렉터/키 상수 =====
	const OVERLAY_SEL = '#index-popup-overlay';
	const MODAL_SEL = '#index-popup-modal';
	const SWIPER_ID = 'index-popup-swiper';
	const HIDE_INPUT = '#index-popup-hide7';
	const BTN_CLOSE = '#index-popup-closebtn';
	const BTN_X = '.index-popup-close';
	const STORAGE_KEY = 'index-popup-hide-until'; // ISO 문자열 타임스탬프

	let swiperInstance = null;

	// ===== 유틸 =====
	function qs(sel, root = document) { return root.querySelector(sel); }
	function qsa(sel, root = document) { return Array.from(root.querySelectorAll(sel)); }

	function shouldHide() {
		try {
			const v = localStorage.getItem(STORAGE_KEY);
			if (!v) return false;
			return Date.now() < new Date(v).getTime();
		} catch (_) {
			return false;
		}
	}

	function hideFor7DaysIfChecked() {
		const el = qs(HIDE_INPUT);
		if (el && el.checked) {
			const until = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
			localStorage.setItem(STORAGE_KEY, until.toISOString());
		}
	}

	function fadeOutAndClose() {
		const overlay = qs(OVERLAY_SEL);
		if (!overlay) return;
		overlay.classList.add('index-popup-fadeout');
		setTimeout(() => {
			try {
				overlay.style.display = 'none';
				overlay.remove();
			} catch (_) { /* noop */ }
			// 파괴 안전
			if (swiperInstance) {
				try { swiperInstance.destroy(true, true); } catch (_) { /* noop */ }
				swiperInstance = null;
			}
		}, 300);
	}

	function initSwiperOnce() {
		const swiperEl = document.getElementById(SWIPER_ID);
		if (!swiperEl || !window.Swiper) return;

		// 선행 인스턴스/외부 auto-init 방어
		if (swiperEl.swiper) { try { swiperEl.swiper.destroy(true, true); } catch (_) { /* noop */ } }
		if (swiperInstance) { try { swiperInstance.destroy(true, true); } catch (_) { /* noop */ } swiperInstance = null; }

		const slideEls = qsa('.swiper-slide', swiperEl);
		const hasMultiple = slideEls.length > 1;

		const prevSel = '.indexpop-button-prev';
		const nextSel = '.indexpop-button-next';
		const prevEl = qs(prevSel, swiperEl);
		const nextEl = qs(nextSel, swiperEl);

		// 1장일 때 내비 버튼 숨김
		if (!hasMultiple) {
			if (prevEl) prevEl.style.display = 'none';
			if (nextEl) nextEl.style.display = 'none';
		} else {
			if (prevEl) prevEl.style.display = '';
			if (nextEl) nextEl.style.display = '';
		}

		// ✅ navigation은 항상 객체로 전달 + enabled만 토글 (에러 방지)
		const navigation = {
			enabled: hasMultiple,
			prevEl: prevSel,
			nextEl: nextSel
		};

		// ✅ autoplay는 2장 이상일 때만
		const autoplay = hasMultiple ? {
			delay: 4000,
			disableOnInteraction: false,
			pauseOnMouseEnter: true
		} : false;

		// 초기화
		swiperInstance = new Swiper(swiperEl, {
			loop: hasMultiple,
			autoplay,
			speed: 500,
			allowTouchMove: hasMultiple,
			effect: 'slide',
			observer: true,
			observeParents: true,
			navigation
			// (pagination 없음)
		});

		// 이미지 로드 후 update (레이아웃 안정화)
		slideEls.forEach(slide => {
			const img = slide.querySelector('img');
			if (!img) return;
			const update = () => { try { swiperInstance.update(); } catch (_) { /* noop */ } };
			if (img.complete) update();
			else {
				img.addEventListener('load', update, { once: true });
				img.addEventListener('error', update, { once: true });
			}
		});
	}

	function bindCloseHandlers() {
		const overlay = qs(OVERLAY_SEL);
		const modal = qs(MODAL_SEL);
		if (!overlay) return;

		// 바깥(오버레이) 클릭 시 닫기
		overlay.addEventListener('click', (e) => {
			if (e.target !== overlay) return;
			hideFor7DaysIfChecked();
			fadeOutAndClose();
		});

		// 모달 내부 클릭은 전파 차단 (바깥 클릭 닫기 방지)
		if (modal) modal.addEventListener('click', (e) => e.stopPropagation());

		// 닫기 버튼
		const closeBtn = qs(BTN_CLOSE);
		if (closeBtn) {
			closeBtn.addEventListener('click', (e) => {
				e.preventDefault();
				hideFor7DaysIfChecked();
				fadeOutAndClose();
			});
		}

		// X 버튼
		const xBtn = qs(BTN_X);
		if (xBtn) {
			xBtn.addEventListener('click', (e) => {
				e.preventDefault();
				hideFor7DaysIfChecked();
				fadeOutAndClose();
			});
		}

		// 키보드 ESC 닫기
		document.addEventListener('keydown', (e) => {
			if (e.key === 'Escape') {
				hideFor7DaysIfChecked();
				fadeOutAndClose();
			}
		}, { passive: true });
	}

	function openIfNeeded() {
		const overlay = qs(OVERLAY_SEL);
		if (!overlay) return;
		if (shouldHide()) return;

		// 가시화 먼저
		overlay.style.display = 'flex';

		// 초기화 실패해도 닫기 바인딩은 반드시 진행
		try {
			initSwiperOnce();
		} catch (e) {
			console.error('[index-popup] Swiper init failed:', e);
		}
		bindCloseHandlers();
	}

	document.addEventListener('DOMContentLoaded', openIfNeeded);
})();
