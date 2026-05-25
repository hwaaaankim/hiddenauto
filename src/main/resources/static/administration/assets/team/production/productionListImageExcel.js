/* /administration/assets/team/production/productionListImageExcel.js */

(function () {
	'use strict';

	const config = window.teamProductionOverviewConfig || {};

	// =========================================================
	// 엑셀 다운로드
	// =========================================================
	const $excelBtn = document.getElementById('team-production-excel-download-btn');

	function getCurrentPageOrderIds() {
		return Array.from(document.querySelectorAll('.team-production-check-item'))
			.map(el => Number(el.getAttribute('data-order-id')))
			.filter(id => !Number.isNaN(id));
	}

	function downloadExcel() {
		const ids = getCurrentPageOrderIds();

		if (ids.length === 0) {
			alert('다운로드할 생산 주문이 없습니다.');
			return;
		}

		const excelUrl = config.excelUrl || '/team/productionList/excel';
		const params = new URLSearchParams();

		ids.forEach(id => params.append('orderIds', String(id)));

		window.location.href = `${excelUrl}?${params.toString()}`;
	}

	if ($excelBtn) {
		$excelBtn.addEventListener('click', downloadExcel);
	}

	// =========================================================
	// 관리자 이미지 모달
	// =========================================================
	const $modal = document.getElementById('team-production-management-image-modal');
	const $stage = document.getElementById('team-production-management-image-stage');
	const $viewer = document.getElementById('team-production-management-image-viewer');
	const $counter = document.getElementById('team-production-management-image-counter');
	const $thumbs = document.getElementById('team-production-management-image-thumbs');

	const $closeBtn = document.getElementById('team-production-management-image-close-btn');
	const $prevBtn = document.getElementById('team-production-management-image-prev-btn');
	const $nextBtn = document.getElementById('team-production-management-image-next-btn');
	const $originalBtn = document.getElementById('team-production-management-image-original-btn');
	const $zoomInBtn = document.getElementById('team-production-management-image-zoom-in-btn');
	const $zoomOutBtn = document.getElementById('team-production-management-image-zoom-out-btn');

	let images = [];
	let currentIndex = 0;
	let zoom = 1;
	let originalMode = false;

	function buildManagementImageUrl(orderId) {
		const prefix = config.managementImageUrlPrefix || '/team/productionList/';
		const normalizedPrefix = prefix.endsWith('/') ? prefix : `${prefix}/`;

		return `${normalizedPrefix}${encodeURIComponent(orderId)}/management-images`;
	}

	async function fetchManagementImages(orderId) {
		const res = await fetch(buildManagementImageUrl(orderId), {
			method: 'GET',
			headers: {
				'Accept': 'application/json'
			}
		});

		if (!res.ok) {
			const text = await res.text();
			throw new Error(text || '이미지를 불러오지 못했습니다.');
		}

		const data = await res.json();

		return Array.isArray(data) ? data : [];
	}

	function openModal() {
		if (!$modal) return;

		$modal.classList.add('is-open');
		$modal.setAttribute('aria-hidden', 'false');
		document.body.classList.add('team-production-image-modal-open');
	}

	function closeModal() {
		if (!$modal) return;

		$modal.classList.remove('is-open');
		$modal.setAttribute('aria-hidden', 'true');
		document.body.classList.remove('team-production-image-modal-open');

		if ($viewer) {
			$viewer.removeAttribute('src');
			$viewer.classList.remove('is-original');
			$viewer.style.transform = '';
		}

		images = [];
		currentIndex = 0;
		zoom = 1;
		originalMode = false;
	}

	function renderCurrentImage() {
		if (!$viewer || !$counter) return;

		if (!images.length) {
			$viewer.removeAttribute('src');
			$counter.textContent = '0 / 0';
			renderThumbs();
			return;
		}

		if (currentIndex < 0) {
			currentIndex = images.length - 1;
		}

		if (currentIndex >= images.length) {
			currentIndex = 0;
		}

		const image = images[currentIndex];
		const url = image.url || '';
		const filename = image.filename || '';

		zoom = 1;
		originalMode = false;

		$viewer.src = url;
		$viewer.alt = filename || '관리자 업로드 이미지';
		$counter.textContent = `${currentIndex + 1} / ${images.length}${filename ? ' · ' + filename : ''}`;

		if ($stage) {
			$stage.scrollLeft = 0;
			$stage.scrollTop = 0;
		}

		applyImageMode();
		renderThumbs();
	}

	function renderThumbs() {
		if (!$thumbs) return;

		$thumbs.innerHTML = '';

		images.forEach((image, index) => {
			const btn = document.createElement('button');
			btn.type = 'button';
			btn.className = 'team-production-management-image-thumb';

			if (index === currentIndex) {
				btn.classList.add('is-active');
			}

			const img = document.createElement('img');
			img.src = image.url || '';
			img.alt = image.filename || `이미지 ${index + 1}`;

			btn.appendChild(img);

			btn.addEventListener('click', function (e) {
				e.preventDefault();
				e.stopPropagation();

				currentIndex = index;
				renderCurrentImage();
			});

			$thumbs.appendChild(btn);
		});
	}

	function applyImageMode() {
		if (!$viewer) return;

		$viewer.classList.toggle('is-original', originalMode);
		$viewer.style.transform = `scale(${zoom})`;
	}

	function movePrev() {
		if (!images.length) return;

		currentIndex -= 1;

		if (currentIndex < 0) {
			currentIndex = images.length - 1;
		}

		renderCurrentImage();
	}

	function moveNext() {
		if (!images.length) return;

		currentIndex += 1;

		if (currentIndex >= images.length) {
			currentIndex = 0;
		}

		renderCurrentImage();
	}

	async function openImagesByOrderId(orderId) {
		if (!orderId) {
			alert('주문 ID가 없습니다.');
			return;
		}

		try {
			const result = await fetchManagementImages(orderId);

			if (!result.length) {
				alert('등록된 관리자 이미지가 없습니다.');
				return;
			}

			images = result;
			currentIndex = 0;
			renderCurrentImage();
			openModal();
		 } catch (e) {
			alert(e && e.message ? e.message : '이미지를 불러오는 중 오류가 발생했습니다.');
		}
	}

	document.querySelectorAll('.team-production-management-image-btn').forEach(btn => {
		btn.addEventListener('click', function (e) {
			e.preventDefault();
			e.stopPropagation();

			const orderId = btn.getAttribute('data-order-id');
			openImagesByOrderId(orderId);
		});
	});

	if ($closeBtn) {
		$closeBtn.addEventListener('click', function (e) {
			e.preventDefault();
			e.stopPropagation();
			closeModal();
		});
	}

	if ($prevBtn) {
		$prevBtn.addEventListener('click', function (e) {
			e.preventDefault();
			e.stopPropagation();
			movePrev();
		});
	}

	if ($nextBtn) {
		$nextBtn.addEventListener('click', function (e) {
			e.preventDefault();
			e.stopPropagation();
			moveNext();
		});
	}

	if ($originalBtn) {
		$originalBtn.addEventListener('click', function (e) {
			e.preventDefault();
			e.stopPropagation();

			originalMode = true;
			zoom = 1;
			applyImageMode();
		});
	}

	if ($zoomInBtn) {
		$zoomInBtn.addEventListener('click', function (e) {
			e.preventDefault();
			e.stopPropagation();

			zoom = Math.min(4, Math.round((zoom + 0.2) * 10) / 10);
			applyImageMode();
		});
	}

	if ($zoomOutBtn) {
		$zoomOutBtn.addEventListener('click', function (e) {
			e.preventDefault();
			e.stopPropagation();

			zoom = Math.max(0.3, Math.round((zoom - 0.2) * 10) / 10);
			applyImageMode();
		});
	}

	if ($modal) {
		$modal.addEventListener('click', function (e) {
			if (e.target === $modal) {
				closeModal();
			}
		});
	}

	document.addEventListener('keydown', function (e) {
		if (!$modal || !$modal.classList.contains('is-open')) {
			return;
		}

		if (e.key === 'Escape') {
			closeModal();
		}

		if (e.key === 'ArrowLeft') {
			movePrev();
		}

		if (e.key === 'ArrowRight') {
			moveNext();
		}
	});
})();