/* /administration/assets/js/page/asList.js */

(function() {
	'use strict';

	// =========================
	// 1) 기존: 행정구역 동적 셀렉트
	// =========================
	const provinceSelect = document.getElementById('as-province-select');

	const childWrapper = document.getElementById('as-child-wrapper');
	const childLabel = document.getElementById('as-child-label');

	const citySelect = document.getElementById('as-city-select');
	const districtDirectSelect = document.getElementById('as-district-direct-select');

	const districtWrapper = document.getElementById('as-district-wrapper');
	const districtSelect = document.getElementById('as-district-select');

	const districtHidden = document.getElementById('as-district-hidden');
	const selected = window.__AS_SELECTED__ || {};

	function resetSelect(selectEl, placeholderText) {
		if (!selectEl) return;
		selectEl.innerHTML = '';
		const opt = document.createElement('option');
		opt.value = '';
		opt.textContent = placeholderText || '전체';
		selectEl.appendChild(opt);
	}

	function show(el) { if (el) el.style.display = ''; }
	function hide(el) { if (el) el.style.display = 'none'; }

	async function fetchJson(url) {
		const res = await fetch(url, { headers: { 'Accept': 'application/json' } });
		if (!res.ok) throw new Error('API 요청 실패: ' + url);
		return res.json();
	}

	function fillOptions(selectEl, items, selectedId) {
		if (!selectEl) return;
		items.forEach(item => {
			const opt = document.createElement('option');
			opt.value = String(item.id);
			opt.textContent = item.name;
			if (selectedId != null && String(selectedId) === String(item.id)) {
				opt.selected = true;
			}
			selectEl.appendChild(opt);
		});
	}

	function setDistrictHidden(v) {
		if (!districtHidden) return;
		districtHidden.value = (v == null) ? '' : String(v);
	}

	function hideAllRegionControls() {
		hide(childWrapper);
		hide(citySelect);
		hide(districtDirectSelect);

		hide(districtWrapper);
		hide(districtSelect);
	}

	async function onProvinceChange(isInit) {
		if (!provinceSelect) return;

		const provinceId = provinceSelect.value;

		resetSelect(citySelect, '전체');
		resetSelect(districtDirectSelect, '전체');
		resetSelect(districtSelect, '전체');

		hideAllRegionControls();

		if (!provinceId) {
			setDistrictHidden('');
			return;
		}

		const data = await fetchJson(`/api/regions/provinces/${provinceId}/children`);
		show(childWrapper);

		if (data.type === 'CITY') {
			childLabel.textContent = '시/군';

			show(citySelect);
			fillOptions(citySelect, data.items, isInit ? selected.cityId : null);
			hide(districtDirectSelect);

			if (isInit && selected.cityId) {
				await onCityChange(true);
			} else {
				hide(districtWrapper);
				hide(districtSelect);
				setDistrictHidden('');
			}

		} else if (data.type === 'DISTRICT') {
			childLabel.textContent = '구/군';

			hide(citySelect);

			show(districtDirectSelect);
			fillOptions(districtDirectSelect, data.items, isInit ? selected.districtId : null);

			hide(districtWrapper);
			hide(districtSelect);

			if (isInit && selected.districtId) {
				setDistrictHidden(selected.districtId);
			} else {
				const v = districtDirectSelect.value;
				setDistrictHidden(v || '');
			}
		}
	}

	async function onCityChange(isInit) {
		const cityId = citySelect ? citySelect.value : '';

		resetSelect(districtSelect, '전체');
		hide(districtWrapper);
		hide(districtSelect);

		if (!cityId) {
			setDistrictHidden('');
			return;
		}

		const items = await fetchJson(`/api/regions/cities/${cityId}/districts`);

		show(districtWrapper);
		show(districtSelect);

		fillOptions(districtSelect, items, isInit ? selected.districtId : null);

		if (isInit && selected.districtId) {
			setDistrictHidden(selected.districtId);
		} else {
			const v = districtSelect.value;
			setDistrictHidden(v || '');
		}
	}

	function bindRegion() {
		if (!provinceSelect) return;

		provinceSelect.addEventListener('change', () => {
			selected.cityId = null;
			selected.districtId = null;
			setDistrictHidden('');
			onProvinceChange(false).catch(console.error);
		});

		if (citySelect) {
			citySelect.addEventListener('change', () => {
				selected.districtId = null;
				setDistrictHidden('');
				onCityChange(false).catch(console.error);
			});
		}

		if (districtDirectSelect) {
			districtDirectSelect.addEventListener('change', () => {
				const v = districtDirectSelect.value;
				selected.districtId = v ? Number(v) : null;
				setDistrictHidden(v || '');
			});
		}

		if (districtSelect) {
			districtSelect.addEventListener('change', () => {
				const v = districtSelect.value;
				selected.districtId = v ? Number(v) : null;
				setDistrictHidden(v || '');
			});
		}
	}

	// =========================
	// 2) 신규: Row 클릭 이동 + 모달 완료처리
	// =========================
	const modalEl = document.getElementById('asList-second-complete-modal');
	const formEl = document.getElementById('asList-second-complete-form');
	const alertEl = document.getElementById('asList-second-modal-alert');

	const btnUpload = document.getElementById('asList-second-btn-upload');   // PC: 업로드 / 모바일: 갤러리 선택
	const btnCamera = document.getElementById('asList-second-btn-camera');   // 모바일 전용 촬영

	const fileInput = document.getElementById('asList-second-file-input');
	const imageList = document.getElementById('asList-second-image-list');
	const btnComplete = document.getElementById('asList-second-btn-complete');

	// 상세 표시 필드들
	const field = {
		company: document.getElementById('asList-second-field-company'),
		requester: document.getElementById('asList-second-field-requester'),
		address: document.getElementById('asList-second-field-address'),
		reason: document.getElementById('asList-second-field-reason'),
		adminMemo: document.getElementById('asList-second-field-adminMemo'), // ✅ 추가
		productName: document.getElementById('asList-second-field-productName'),
		productSize: document.getElementById('asList-second-field-productSize'),
		productColor: document.getElementById('asList-second-field-productColor'),
		onsiteContact: document.getElementById('asList-second-field-onsiteContact'),
		requestedAt: document.getElementById('asList-second-field-requestedAt')
	};

	// bootstrap modal instance
	let bsModal = null;

	// 상태
	let currentTaskId = null;

	// ✅ 추가: 현재 작업의 상태(리스트에서 받은 값 or 상세에서 받은 값)
	let currentTaskStatus = null; // 'IN_PROGRESS' | 'REQUESTED' | ...

	let existingResultImages = []; // [{id,url,filename}]
	let selectedFiles = []; // File[] (fileInput과 동기화)

	function isMobile() {
		return /iPhone|iPad|iPod|Android/i.test(navigator.userAgent);
	}

	function setAlert(type, msg) {
		if (!alertEl) return;
		if (!msg) {
			alertEl.className = 'alert d-none';
			alertEl.textContent = '';
			return;
		}
		alertEl.className = 'alert';
		alertEl.classList.add(type === 'danger' ? 'alert-danger' : 'alert-info');
		alertEl.textContent = msg;
		alertEl.classList.remove('d-none');
	}

	function escapeHtml(s) {
		return String(s ?? '').replace(/[&<>"']/g, (m) => ({
			'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#039;'
		}[m]));
	}

	function setText(el, v) {
		if (!el) return;
		el.textContent = (v == null || String(v).trim() === '') ? '-' : String(v);
	}

	function syncFileInputFromSelectedFiles() {
		if (!fileInput) return;
		const dt = new DataTransfer();
		selectedFiles.forEach(f => dt.items.add(f));
		fileInput.files = dt.files;
	}

	function updateCompleteButtonState() {
		// ✅ 진행중이 아니면 무조건 비활성
		if (btnComplete) {
			if (currentTaskStatus !== 'IN_PROGRESS') {
				btnComplete.disabled = true;
				return;
			}
		}

		const total = (existingResultImages?.length || 0) + (selectedFiles?.length || 0);
		if (btnComplete) btnComplete.disabled = total <= 0;
	}

	function renderImages() {
		if (!imageList) return;
		imageList.innerHTML = '';

		// 1) 기존(서버 저장) RESULT 이미지
		(existingResultImages || []).forEach(img => {
			const col = document.createElement('div');
			col.className = 'col-6 col-sm-4 col-md-3';

			col.innerHTML = `
                <div class="border rounded p-2 position-relative asList-second-image-card">
                    <button type="button"
                            class="btn btn-sm btn-danger position-absolute top-0 end-0 m-1 asList-second-btn-remove-existing"
                            data-image-id="${img.id}"
                            title="삭제">×</button>
                    <img src="${escapeHtml(img.url)}" class="img-fluid rounded mb-2" style="max-height: 140px; width: 100%; object-fit: cover;">
                    <div class="small text-truncate text-muted">${escapeHtml(img.filename || '')}</div>
                </div>
            `;
			imageList.appendChild(col);
		});

		// 2) 신규(미저장) 선택 파일 미리보기
		(selectedFiles || []).forEach((file, idx) => {
			const col = document.createElement('div');
			col.className = 'col-6 col-sm-4 col-md-3';

			const url = URL.createObjectURL(file);
			col.innerHTML = `
                <div class="border rounded p-2 position-relative asList-second-image-card">
                    <button type="button"
                            class="btn btn-sm btn-dark position-absolute top-0 end-0 m-1 asList-second-btn-remove-new"
                            data-file-idx="${idx}"
                            title="제거">×</button>
                    <img src="${escapeHtml(url)}" class="img-fluid rounded mb-2" style="max-height: 140px; width: 100%; object-fit: cover;">
                    <div class="small text-truncate text-muted">${escapeHtml(file.name)}</div>
                </div>
            `;
			imageList.appendChild(col);
		});

		updateCompleteButtonState();
	}

	async function deleteExistingImage(imageId) {
		// ✅ 진행중이 아닌 상태에서는 삭제도 막는 게 안전합니다(원하시면 제거 가능)
		if (currentTaskStatus !== 'IN_PROGRESS') {
			alert('진행중 건에 대해서만 가능합니다.');
			return;
		}

		const res = await fetch(`/team/asImageDelete/${imageId}`, { method: 'DELETE' });
		if (!res.ok) throw new Error('이미지 삭제 실패');
		existingResultImages = existingResultImages.filter(x => String(x.id) !== String(imageId));
		renderImages();
	}

	function removeNewFileByIndex(idx) {
		// ✅ 진행중 아니면 신규 선택도 의미가 없으니 막습니다(원하시면 제거 가능)
		if (currentTaskStatus !== 'IN_PROGRESS') {
			alert('진행중 건에 대해서만 가능합니다.');
			return;
		}

		selectedFiles = selectedFiles.filter((_, i) => i !== idx);
		syncFileInputFromSelectedFiles();
		renderImages();
	}

	function bindImageListEvents() {
		if (!imageList) return;

		imageList.addEventListener('click', (e) => {
			const btnExisting = e.target.closest('.asList-second-btn-remove-existing');
			if (btnExisting) {
				e.preventDefault();
				e.stopPropagation();
				const imageId = btnExisting.getAttribute('data-image-id');
				if (!imageId) return;

				if (confirm('이 이미지를 삭제하시겠습니까?')) {
					deleteExistingImage(imageId).catch(err => {
						console.error(err);
						alert('삭제 실패');
					});
				}
				return;
			}

			const btnNew = e.target.closest('.asList-second-btn-remove-new');
			if (btnNew) {
				e.preventDefault();
				e.stopPropagation();
				const idx = parseInt(btnNew.getAttribute('data-file-idx'), 10);
				if (Number.isFinite(idx)) removeNewFileByIndex(idx);
			}
		});
	}

	function bindFileInput() {
		if (!fileInput) return;

		fileInput.addEventListener('change', () => {
			// ✅ 진행중이 아니면 파일 선택 자체를 막습니다
			if (currentTaskStatus !== 'IN_PROGRESS') {
				fileInput.value = '';
				alert('진행중 건에 대해서만 가능합니다.');
				return;
			}

			const incoming = Array.from(fileInput.files || []);
			if (incoming.length > 0) {
				selectedFiles = selectedFiles.concat(incoming);
				syncFileInputFromSelectedFiles();
			} else {
				syncFileInputFromSelectedFiles();
			}
			renderImages();
		});
	}

	function openFilePicker(mode) {
		// mode: 'camera' | 'gallery'
		if (!fileInput) return;

		// ✅ 진행중이 아니면 막기
		if (currentTaskStatus !== 'IN_PROGRESS') {
			alert('진행중 건에 대해서만 가능합니다.');
			return;
		}

		if (mode === 'camera') {
			// 모바일 촬영
			fileInput.setAttribute('capture', 'environment');
		} else {
			// 갤러리(기본 선택)
			fileInput.removeAttribute('capture');
		}
		fileInput.click();
	}

	function bindUploadButtons() {
		// ✅ 정책:
		// - PC: 업로드 버튼만 있고, 클릭 시 기본 파일 선택
		// - 모바일: 업로드(갤러리) 버튼 + 촬영 버튼 2개
		if (btnUpload) {
			btnUpload.addEventListener('click', (e) => {
				e.preventDefault();
				e.stopPropagation();
				openFilePicker('gallery');
			});
		}

		if (btnCamera) {
			btnCamera.addEventListener('click', (e) => {
				e.preventDefault();
				e.stopPropagation();
				openFilePicker('camera');
			});
		}
	}

	function applyMobileButtonsVisibility() {
		if (isMobile()) {
			if (btnCamera) btnCamera.classList.remove('d-none');
		} else {
			if (btnCamera) btnCamera.classList.add('d-none');
		}
	}

	function lockModalIfNotInProgress() {
		// ✅ 진행중 아니면 모달 내 액션 전부 비활성
		if (currentTaskStatus !== 'IN_PROGRESS') {
			setAlert('danger', '진행중 건에 대해서만 가능합니다.');
			if (btnComplete) btnComplete.disabled = true;
		}
		updateCompleteButtonState();
	}

	async function loadTaskForModal(taskId) {
		setAlert(null, null);

		const data = await fetchJson(`/team/asDetailModal/${taskId}`);

		// 필드 채우기
		setText(field.company, data.companyName);
		setText(field.requester, data.requesterName);
		setText(field.address, data.fullAddress);
		setText(field.reason, data.reason);
		setText(field.adminMemo, data.adminMemo); // ✅ 추가
		setText(field.productName, data.productName);
		setText(field.productSize, data.productSize);
		setText(field.productColor, data.productColor);
		setText(field.onsiteContact, data.onsiteContact);
		setText(field.requestedAt, data.requestedAt);

		// ✅ (강력 권장) 상세 API가 status를 내려주면 여기서 2중 검증
		// - data.status 예: 'IN_PROGRESS'
		if (data && data.status) {
			currentTaskStatus = String(data.status);
		}

		existingResultImages = (data.resultImages || []).map(x => ({
			id: x.id,
			url: x.url,
			filename: x.filename
		}));

		// 신규 선택 파일 초기화(모달 열 때마다 초기화)
		selectedFiles = [];
		if (fileInput) {
			fileInput.value = '';
			syncFileInputFromSelectedFiles();
		}

		renderImages();
		lockModalIfNotInProgress();
	}

	function openCompleteModal(taskId, statusFromRow) {
		if (!modalEl) return;

		currentTaskId = taskId;
		currentTaskStatus = statusFromRow ? String(statusFromRow) : null;

		// ✅ 1차 방어: row status가 IN_PROGRESS가 아니면 모달 자체를 열지 않음
		if (currentTaskStatus !== 'IN_PROGRESS') {
			alert('진행중 건에 대해서만 가능합니다.');
			return;
		}

		// form action 세팅: 기존 컨트롤러 그대로 사용
		if (formEl) formEl.action = `/team/asUpdate/${taskId}`;

		if (!bsModal && window.bootstrap && bootstrap.Modal) {
			bsModal = new bootstrap.Modal(modalEl);
		}

		setAlert('info', '불러오는 중입니다...');

		loadTaskForModal(taskId)
			.then(() => {
				// loadTaskForModal 안에서 status 재검증까지 수행
				if (currentTaskStatus !== 'IN_PROGRESS') {
					// 서버에서 상태가 바뀐 경우(경합 상황)
					setAlert('danger', '진행중 건에 대해서만 가능합니다.');
				} else {
					setAlert(null, null);
				}
			})
			.catch(err => {
				console.error(err);
				setAlert('danger', '상세 정보를 불러오지 못했습니다.');
			})
			.finally(() => {
				applyMobileButtonsVisibility();
				if (bsModal) bsModal.show();
			});
	}

	function bindCompleteButtons() {
		document.addEventListener('click', (e) => {
			const btn = e.target.closest('.asList-second-btn-open-complete');
			if (!btn) return;

			e.preventDefault();
			e.stopPropagation();

			const taskId = btn.getAttribute('data-task-id');
			const status = btn.getAttribute('data-status'); // ✅ row에 심어둔 상태값
			if (!taskId) return;

			// ✅ disabled여도 클릭이 들어오는 케이스가 있어(브라우저/DOM 상황) 2중 방어
			if (String(status) !== 'IN_PROGRESS') {
				alert('진행중 건에 대해서만 가능합니다.');
				return;
			}

			openCompleteModal(taskId, status);
		});
	}

	function bindRowClickToDetail() {
		document.addEventListener('click', (e) => {
			const stop = e.target.closest('[data-stop-row="1"], button, a, input, select, textarea, label');
			if (stop && stop.closest('.asList-second-row-click')) {
				if (!stop.classList.contains('asList-second-row-click')) {
					return;
				}
			}

			const row = e.target.closest('.asList-second-row-click');
			if (!row) return;

			const href = row.getAttribute('data-href');
			if (href) window.location.href = href;
		});
	}

	function bindFormSubmitGuard() {
		if (!formEl) return;
		formEl.addEventListener('submit', (e) => {
			// ✅ 최종 JS 방어: 진행중만 제출 가능
			if (currentTaskStatus !== 'IN_PROGRESS') {
				e.preventDefault();
				alert('진행중 건에 대해서만 가능합니다.');
				return;
			}

			const total = (existingResultImages?.length || 0) + (selectedFiles?.length || 0);
			if (total <= 0) {
				e.preventDefault();
				alert('이미지를 1개 이상 등록해 주세요.');
				return;
			}
		});
	}

	// =========================
	// DOMContentLoaded
	// =========================
	document.addEventListener('DOMContentLoaded', () => {
		bindRegion();
		onProvinceChange(true).catch(console.error);

		bindRowClickToDetail();
		bindCompleteButtons();
		bindUploadButtons();
		bindFileInput();
		bindImageListEvents();
		bindFormSubmitGuard();
		applyMobileButtonsVisibility();
	});

})();