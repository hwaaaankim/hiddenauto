(function() {
	'use strict';

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

	const modalEl = document.getElementById('asList-second-complete-modal');
	const formEl = document.getElementById('asList-second-complete-form');
	const alertEl = document.getElementById('asList-second-modal-alert');

	const btnUpload = document.getElementById('asList-second-btn-upload');
	const btnCamera = document.getElementById('asList-second-btn-camera');

	const fileInput = document.getElementById('asList-second-file-input');
	const imageList = document.getElementById('asList-second-image-list');
	const btnComplete = document.getElementById('asList-second-btn-complete');

	const field = {
		company: document.getElementById('asList-second-field-company'),
		requester: document.getElementById('asList-second-field-requester'),
		address: document.getElementById('asList-second-field-address'),
		reason: document.getElementById('asList-second-field-reason'),
		adminMemo: document.getElementById('asList-second-field-adminMemo'),
		handlerMemo: document.getElementById('asList-second-field-handlerMemo'),
		visitPlannedTime: document.getElementById('asList-second-field-visitPlannedTime'),
		productName: document.getElementById('asList-second-field-productName'),
		productSize: document.getElementById('asList-second-field-productSize'),
		productColor: document.getElementById('asList-second-field-productColor'),
		onsiteContact: document.getElementById('asList-second-field-onsiteContact'),
		requestedAt: document.getElementById('asList-second-field-requestedAt')
	};

	let bsModal = null;
	let currentTaskId = null;
	let currentTaskStatus = null;

	let existingResultImages = [];
	let selectedFiles = [];

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
		if (!fileInput) return;

		if (currentTaskStatus !== 'IN_PROGRESS') {
			alert('진행중 건에 대해서만 가능합니다.');
			return;
		}

		if (mode === 'camera') {
			fileInput.setAttribute('capture', 'environment');
		} else {
			fileInput.removeAttribute('capture');
		}
		fileInput.click();
	}

	function bindUploadButtons() {
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
		if (currentTaskStatus !== 'IN_PROGRESS') {
			setAlert('danger', '진행중 건에 대해서만 가능합니다.');
			if (btnComplete) btnComplete.disabled = true;
		}
		updateCompleteButtonState();
	}

	async function loadTaskForModal(taskId) {
		setAlert(null, null);

		const data = await fetchJson(`/team/asDetailModal/${taskId}`);

		setText(field.company, data.companyName);
		setText(field.requester, data.requesterName);
		setText(field.address, data.fullAddress);
		setText(field.reason, data.reason);
		setText(field.adminMemo, data.adminMemo);
		setText(field.handlerMemo, data.handlerMemo);
		setText(field.visitPlannedTime, data.visitPlannedTime);
		setText(field.productName, data.productName);
		setText(field.productSize, data.productSize);
		setText(field.productColor, data.productColor);
		setText(field.onsiteContact, data.onsiteContact);
		setText(field.requestedAt, data.requestedAt);

		if (data && data.status) {
			currentTaskStatus = String(data.status);
		}

		existingResultImages = (data.resultImages || []).map(x => ({
			id: x.id,
			url: x.url,
			filename: x.filename
		}));

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

		if (currentTaskStatus !== 'IN_PROGRESS') {
			alert('진행중 건에 대해서만 가능합니다.');
			return;
		}

		if (formEl) formEl.action = `/team/asUpdate/${taskId}`;

		if (!bsModal && window.bootstrap && bootstrap.Modal) {
			bsModal = new bootstrap.Modal(modalEl);
		}

		setAlert('info', '불러오는 중입니다...');

		loadTaskForModal(taskId)
			.then(() => {
				if (currentTaskStatus !== 'IN_PROGRESS') {
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
			const status = btn.getAttribute('data-status');
			if (!taskId) return;

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