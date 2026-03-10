(function() {
	'use strict';

	function getCsrf() {
		const tokenMeta = document.querySelector('meta[name="_csrf"]');
		const headerMeta = document.querySelector('meta[name="_csrf_header"]');

		return {
			token: tokenMeta ? tokenMeta.getAttribute('content') : null,
			header: headerMeta ? headerMeta.getAttribute('content') : null
		};
	}

	async function postJson(url, bodyObj) {
		const csrf = getCsrf();
		const headers = { 'Content-Type': 'application/json' };

		if (csrf.token && csrf.header) {
			headers[csrf.header] = csrf.token;
		}

		const res = await fetch(url, {
			method: 'POST',
			headers,
			body: JSON.stringify(bodyObj || {})
		});

		const text = await res.text();
		let json = null;

		try {
			json = JSON.parse(text);
		} catch (e) {
			// ignore
		}

		if (!res.ok) {
			const msg = (json && json.message) ? json.message : (text || ('HTTP ' + res.status));
			throw new Error(msg);
		}

		return json || { message: text };
	}

	async function postFormData(url, formData) {
		const csrf = getCsrf();
		const headers = {};

		if (csrf.token && csrf.header) {
			headers[csrf.header] = csrf.token;
		}

		const res = await fetch(url, {
			method: 'POST',
			headers,
			body: formData
		});

		const text = await res.text();
		let json = null;

		try {
			json = JSON.parse(text);
		} catch (e) {
			// ignore
		}

		if (!res.ok) {
			const msg = (json && json.message) ? json.message : (text || ('HTTP ' + res.status));
			throw new Error(msg);
		}

		return json || { message: text };
	}

	function nvl(value) {
		return value == null ? '' : String(value);
	}

	function trimValue(value) {
		return nvl(value).trim();
	}

	function normalizeBusinessNumber(value) {
		return nvl(value).replace(/\D/g, '').slice(0, 10);
	}

	function isNonNegativeIntegerString(value) {
		const v = trimValue(value);
		return /^[0-9]+$/.test(v);
	}

	function isLikelyImageUrl(url) {
		const u = trimValue(url).toLowerCase().split('?')[0].split('#')[0];
		return u.endsWith('.png') || u.endsWith('.jpg') || u.endsWith('.jpeg') || u.endsWith('.gif') || u.endsWith('.webp');
	}

	function isLikelyPdfUrl(url) {
		const u = trimValue(url).toLowerCase().split('?')[0].split('#')[0];
		return u.endsWith('.pdf');
	}

	function isImageFile(file) {
		return !!file && !!file.type && file.type.startsWith('image/');
	}

	function isPdfFile(file) {
		return !!file && file.type === 'application/pdf';
	}

	function joinAddressText(parts) {
		return parts
			.map(function(part) { return trimValue(part); })
			.filter(function(part) { return part.length > 0; })
			.join(' ');
	}

	function splitSigungu(sigungu) {
		const tokens = trimValue(sigungu).split(/\s+/).filter(Boolean);

		if (tokens.length === 0) {
			return { siName: '', guName: '' };
		}

		if (tokens.length === 1) {
			return { siName: tokens[0], guName: '' };
		}

		return {
			siName: tokens.slice(0, tokens.length - 1).join(' '),
			guName: tokens[tokens.length - 1]
		};
	}

	/* =========================
	   1) 비밀번호 초기화 / 접속금지
	========================= */
	document.querySelectorAll('.js-reset-password').forEach(function(el) {
		el.addEventListener('click', async function() {
			const memberId = el.getAttribute('data-member-id');
			const username = el.getAttribute('data-username') || '';

			if (!memberId) {
				return;
			}

			const ok = confirm('해당 유저의 비밀번호를 초기화 하시겠습니까?');
			if (!ok) {
				return;
			}

			try {
				await postJson('/management/member/' + memberId + '/resetPassword', {});
				alert('비밀번호가 초기화되었고, 해당 휴대폰번호로 안내 문자가 발송되었습니다.\n(아이디: ' + username + ')');
			} catch (e) {
				alert('실패: ' + (e && e.message ? e.message : e));
			}
		});
	});

	document.querySelectorAll('.js-disable-member').forEach(function(el) {
		el.addEventListener('click', async function() {
			const memberId = el.getAttribute('data-member-id');
			const username = el.getAttribute('data-username') || '';

			if (!memberId) {
				return;
			}

			const ok = confirm('해당 유저를 접속금지 처리하시겠습니까?');
			if (!ok) {
				return;
			}

			try {
				await postJson('/management/member/' + memberId + '/disable', {});
				alert('접속금지 처리되었습니다.\n(아이디: ' + username + ')');
			} catch (e) {
				alert('실패: ' + (e && e.message ? e.message : e));
			}
		});
	});

	/* =========================
	   2) 사업자등록증 열람 모달
	========================= */
	const licenseOpenBtn = document.getElementById('admin-client-detail-fourth-open-business-license');
	const noLicenseTextEl = document.getElementById('admin-client-detail-fourth-no-license-text');
	const businessLicenseModalEl = document.getElementById('admin-client-detail-fourth-businessLicenseModal');
	const businessLicenseImgEl = document.getElementById('admin-client-detail-fourth-businessLicenseImg');
	const businessLicenseFrameEl = document.getElementById('admin-client-detail-fourth-businessLicenseFrame');

	if (licenseOpenBtn) {
		licenseOpenBtn.addEventListener('click', function() {
			const url = licenseOpenBtn.getAttribute('data-license-url');

			if (!url) {
				return;
			}

			if (businessLicenseImgEl) {
				businessLicenseImgEl.style.display = 'none';
				businessLicenseImgEl.src = '';
			}

			if (businessLicenseFrameEl) {
				businessLicenseFrameEl.style.display = 'none';
				businessLicenseFrameEl.src = '';
			}

			if (isLikelyImageUrl(url)) {
				businessLicenseImgEl.src = url;
				businessLicenseImgEl.style.display = 'block';
			} else {
				businessLicenseFrameEl.src = url;
				businessLicenseFrameEl.style.display = 'block';
			}

			const modal = bootstrap.Modal.getOrCreateInstance(businessLicenseModalEl);
			modal.show();
		});
	}

	if (businessLicenseModalEl) {
		businessLicenseModalEl.addEventListener('hidden.bs.modal', function() {
			if (businessLicenseImgEl) {
				businessLicenseImgEl.src = '';
			}
			if (businessLicenseFrameEl) {
				businessLicenseFrameEl.src = '';
			}
		});
	}

	/* =========================
	   3) 회사정보 수정
	========================= */
	const companyId = window.ADMIN_CLIENT_DETAIL_FOURTH_COMPANY_ID;
	const companySaveBtn = document.getElementById('admin-client-detail-fourth-company-save-btn');
	const searchAddressBtn = document.getElementById('admin-client-detail-fourth-search-address-btn');
	const addressPreviewInlineEl = document.getElementById('admin-client-detail-fourth-address-preview-inline');
	const currentAddressPreviewEl = document.getElementById('admin-client-detail-fourth-current-address-preview');

	const companyFields = {
		companyName: document.getElementById('admin-client-detail-fourth-companyName'),
		point: document.getElementById('admin-client-detail-fourth-point'),
		businessNumber: document.getElementById('admin-client-detail-fourth-businessNumber'),
		zipCode: document.getElementById('admin-client-detail-fourth-zipCode'),
		doName: document.getElementById('admin-client-detail-fourth-doName'),
		siName: document.getElementById('admin-client-detail-fourth-siName'),
		guName: document.getElementById('admin-client-detail-fourth-guName'),
		roadAddress: document.getElementById('admin-client-detail-fourth-roadAddress'),
		detailAddress: document.getElementById('admin-client-detail-fourth-detailAddress'),
		businessLicenseFile: document.getElementById('admin-client-detail-fourth-businessLicenseFile')
	};

	const existingLicenseUrlInput = document.getElementById('admin-client-detail-fourth-existing-license-url');
	const existingLicenseFilenameInput = document.getElementById('admin-client-detail-fourth-existing-license-filename');
	const licensePreviewListEl = document.getElementById('admin-client-detail-fourth-license-preview-list');

	const companyLicenseState = {
		existingUrl: existingLicenseUrlInput ? trimValue(existingLicenseUrlInput.value) : '',
		existingFilename: existingLicenseFilenameInput ? trimValue(existingLicenseFilenameInput.value) : '',
		existingRemoved: false,
		newFile: null,
		newFileObjectUrl: null
	};

	function revokeNewFileObjectUrl() {
		if (companyLicenseState.newFileObjectUrl) {
			URL.revokeObjectURL(companyLicenseState.newFileObjectUrl);
			companyLicenseState.newFileObjectUrl = null;
		}
	}

	function buildAddressPreviewText() {
		const text = joinAddressText([
			companyFields.doName ? companyFields.doName.value : '',
			companyFields.siName ? companyFields.siName.value : '',
			companyFields.guName ? companyFields.guName.value : '',
			companyFields.roadAddress ? companyFields.roadAddress.value : '',
			companyFields.detailAddress ? companyFields.detailAddress.value : ''
		]);

		return text || '주소 정보 없음';
	}

	function updateAddressPreview() {
		const text = buildAddressPreviewText();

		if (addressPreviewInlineEl) {
			addressPreviewInlineEl.textContent = text;
		}

		if (currentAddressPreviewEl) {
			currentAddressPreviewEl.textContent = text;
		}
	}

	function updateLicenseStatusDisplay() {
		const showExistingOpen =
			!!licenseOpenBtn &&
			!!companyLicenseState.existingUrl &&
			!companyLicenseState.existingRemoved &&
			!companyLicenseState.newFile;

		if (licenseOpenBtn) {
			licenseOpenBtn.classList.toggle('d-none', !showExistingOpen);

			if (showExistingOpen) {
				licenseOpenBtn.setAttribute('data-license-url', companyLicenseState.existingUrl);
			}
		}

		if (noLicenseTextEl) {
			const showNoText = !showExistingOpen && !companyLicenseState.newFile;
			noLicenseTextEl.classList.toggle('d-none', !showNoText);
		}
	}

	function createLicensePreviewItem(kind, thumbMode, thumbValue, fileName) {
		const item = document.createElement('div');
		item.className = 'admin-client-detail-fourth-license-preview-item';

		const removeBtn = document.createElement('button');
		removeBtn.type = 'button';
		removeBtn.className = 'admin-client-detail-fourth-license-remove-btn';
		removeBtn.setAttribute('data-kind', kind);
		removeBtn.textContent = '×';

		const thumb = document.createElement('div');
		thumb.className = 'admin-client-detail-fourth-license-preview-thumb';

		if (thumbMode === 'image') {
			thumb.style.backgroundImage = 'url("' + thumbValue + '")';
		} else {
			thumb.textContent = thumbValue;
		}

		const meta = document.createElement('div');
		meta.className = 'admin-client-detail-fourth-license-preview-meta';

		const name = document.createElement('div');
		name.className = 'admin-client-detail-fourth-license-preview-name';
		name.textContent = fileName;

		meta.appendChild(name);
		item.appendChild(removeBtn);
		item.appendChild(thumb);
		item.appendChild(meta);

		return item;
	}

	function renderLicensePreview() {
		if (!licensePreviewListEl) {
			return;
		}

		licensePreviewListEl.innerHTML = '';

		if (companyLicenseState.newFile) {
			if (isImageFile(companyLicenseState.newFile)) {
				if (!companyLicenseState.newFileObjectUrl) {
					companyLicenseState.newFileObjectUrl = URL.createObjectURL(companyLicenseState.newFile);
				}

				licensePreviewListEl.appendChild(
					createLicensePreviewItem(
						'new',
						'image',
						companyLicenseState.newFileObjectUrl,
						companyLicenseState.newFile.name
					)
				);
			} else if (isPdfFile(companyLicenseState.newFile)) {
				licensePreviewListEl.appendChild(
					createLicensePreviewItem(
						'new',
						'text',
						'PDF 파일',
						companyLicenseState.newFile.name
					)
				);
			} else {
				licensePreviewListEl.appendChild(
					createLicensePreviewItem(
						'new',
						'text',
						'파일',
						companyLicenseState.newFile.name
					)
				);
			}

			updateLicenseStatusDisplay();
			return;
		}

		if (companyLicenseState.existingUrl && !companyLicenseState.existingRemoved) {
			if (isLikelyImageUrl(companyLicenseState.existingUrl)) {
				licensePreviewListEl.appendChild(
					createLicensePreviewItem(
						'existing',
						'image',
						companyLicenseState.existingUrl,
						companyLicenseState.existingFilename || '기존 사업자등록증'
					)
				);
			} else if (isLikelyPdfUrl(companyLicenseState.existingUrl)) {
				licensePreviewListEl.appendChild(
					createLicensePreviewItem(
						'existing',
						'text',
						'PDF 파일',
						companyLicenseState.existingFilename || '기존 사업자등록증'
					)
				);
			} else {
				licensePreviewListEl.appendChild(
					createLicensePreviewItem(
						'existing',
						'text',
						'기존 파일',
						companyLicenseState.existingFilename || '기존 사업자등록증'
					)
				);
			}
		}

		updateLicenseStatusDisplay();
	}

	function hasCurrentLicense() {
		if (companyLicenseState.newFile) {
			return true;
		}

		return !!(companyLicenseState.existingUrl && !companyLicenseState.existingRemoved);
	}

	function getLicenseAction() {
		if (companyLicenseState.newFile) {
			return 'REPLACE';
		}

		if (companyLicenseState.existingUrl && !companyLicenseState.existingRemoved) {
			return 'KEEP';
		}

		return 'DELETE';
	}

	function createCompanySnapshot() {
		return JSON.stringify({
			companyName: trimValue(companyFields.companyName ? companyFields.companyName.value : ''),
			point: trimValue(companyFields.point ? companyFields.point.value : ''),
			businessNumber: normalizeBusinessNumber(companyFields.businessNumber ? companyFields.businessNumber.value : ''),
			zipCode: trimValue(companyFields.zipCode ? companyFields.zipCode.value : ''),
			doName: trimValue(companyFields.doName ? companyFields.doName.value : ''),
			siName: trimValue(companyFields.siName ? companyFields.siName.value : ''),
			guName: trimValue(companyFields.guName ? companyFields.guName.value : ''),
			roadAddress: trimValue(companyFields.roadAddress ? companyFields.roadAddress.value : ''),
			detailAddress: trimValue(companyFields.detailAddress ? companyFields.detailAddress.value : ''),
			existingRemoved: companyLicenseState.existingRemoved,
			hasNewFile: !!companyLicenseState.newFile,
			newFileName: companyLicenseState.newFile ? companyLicenseState.newFile.name : '',
			newFileSize: companyLicenseState.newFile ? companyLicenseState.newFile.size : 0
		});
	}

	const companyOriginalState = createCompanySnapshot();

	function validateCompanyForm() {
		const companyName = trimValue(companyFields.companyName.value);
		const point = trimValue(companyFields.point.value);
		const businessNumber = normalizeBusinessNumber(companyFields.businessNumber.value);
		const zipCode = trimValue(companyFields.zipCode.value);
		const doName = trimValue(companyFields.doName.value);
		const siName = trimValue(companyFields.siName.value);
		const roadAddress = trimValue(companyFields.roadAddress.value);

		if (!companyName) {
			return false;
		}

		if (!isNonNegativeIntegerString(point)) {
			return false;
		}

		if (businessNumber.length !== 10) {
			return false;
		}

		if (!zipCode) {
			return false;
		}

		if (!doName) {
			return false;
		}

		if (!siName) {
			return false;
		}

		if (!roadAddress) {
			return false;
		}

		if (!hasCurrentLicense()) {
			return false;
		}

		return true;
	}

	function hasCompanyChanges() {
		return createCompanySnapshot() !== companyOriginalState;
	}

	function toggleCompanySaveButton() {
		if (!companySaveBtn) {
			return;
		}

		companySaveBtn.disabled = !(validateCompanyForm() && hasCompanyChanges());
	}

	function openAddressSearch() {
		if (!window.kakao || !window.kakao.Postcode) {
			alert('주소 검색 스크립트가 아직 로드되지 않았습니다. 새로고침 후 다시 시도해주세요.');
			return;
		}

		new window.kakao.Postcode({
			oncomplete: function(data) {
				const region = splitSigungu(data.sigungu);
				const selectedAddress =
					trimValue(data.roadAddress) ||
					trimValue(data.address) ||
					trimValue(data.jibunAddress);

				companyFields.zipCode.value = trimValue(data.zonecode);
				companyFields.doName.value = trimValue(data.sido);
				companyFields.siName.value = region.siName;
				companyFields.guName.value = region.guName;
				companyFields.roadAddress.value = selectedAddress;

				updateAddressPreview();
				toggleCompanySaveButton();

				if (companyFields.detailAddress) {
					companyFields.detailAddress.focus();
				}
			}
		}).open();
	}

	Object.keys(companyFields).forEach(function(key) {
		const field = companyFields[key];

		if (!field || key === 'businessLicenseFile') {
			return;
		}

		field.addEventListener('input', function() {
			if (key === 'businessNumber') {
				field.value = normalizeBusinessNumber(field.value);
			}

			updateAddressPreview();
			toggleCompanySaveButton();
		});

		field.addEventListener('change', function() {
			if (key === 'businessNumber') {
				field.value = normalizeBusinessNumber(field.value);
			}

			updateAddressPreview();
			toggleCompanySaveButton();
		});
	});

	if (companyFields.businessLicenseFile) {
		companyFields.businessLicenseFile.addEventListener('change', function(e) {
			revokeNewFileObjectUrl();

			const file = e.target.files && e.target.files[0] ? e.target.files[0] : null;
			companyLicenseState.newFile = file;

			renderLicensePreview();
			toggleCompanySaveButton();
		});
	}

	if (licensePreviewListEl) {
		licensePreviewListEl.addEventListener('click', function(e) {
			const btn = e.target.closest('.admin-client-detail-fourth-license-remove-btn');

			if (!btn) {
				return;
			}

			const kind = btn.getAttribute('data-kind');

			if (kind === 'existing') {
				companyLicenseState.existingRemoved = true;
			}

			if (kind === 'new') {
				revokeNewFileObjectUrl();
				companyLicenseState.newFile = null;

				if (companyFields.businessLicenseFile) {
					companyFields.businessLicenseFile.value = '';
				}
			}

			renderLicensePreview();
			toggleCompanySaveButton();
		});
	}

	if (searchAddressBtn) {
		searchAddressBtn.addEventListener('click', openAddressSearch);
	}

	if (companySaveBtn) {
		companySaveBtn.addEventListener('click', async function() {
			if (!validateCompanyForm()) {
				alert('필수 입력값을 확인해주세요. 사업자등록증은 삭제만 할 수 없고 유지 또는 새 파일 등록이 필요합니다.');
				return;
			}

			const ok = confirm('회사정보 수정사항을 반영하시겠습니까?');
			if (!ok) {
				return;
			}

			const formData = new FormData();
			formData.append('companyName', trimValue(companyFields.companyName.value));
			formData.append('point', trimValue(companyFields.point.value));
			formData.append('businessNumber', normalizeBusinessNumber(companyFields.businessNumber.value));
			formData.append('zipCode', trimValue(companyFields.zipCode.value));
			formData.append('doName', trimValue(companyFields.doName.value));
			formData.append('siName', trimValue(companyFields.siName.value));
			formData.append('guName', trimValue(companyFields.guName.value));
			formData.append('roadAddress', trimValue(companyFields.roadAddress.value));
			formData.append('detailAddress', trimValue(companyFields.detailAddress.value));
			formData.append('licenseAction', getLicenseAction());

			if (companyLicenseState.newFile) {
				formData.append('businessLicenseFile', companyLicenseState.newFile);
			}

			try {
				await postFormData('/management/clientDetail/' + companyId + '/updateCompany', formData);
				alert('회사정보가 수정되었습니다.');
				location.reload();
			} catch (e) {
				alert('실패: ' + (e && e.message ? e.message : e));
			}
		});
	}

	updateAddressPreview();
	renderLicensePreview();
	toggleCompanySaveButton();

	/* =========================
	   4) 회원정보 수정 모달
	========================= */
	const memberEditModalEl = document.getElementById('admin-client-detail-fourth-memberEditModal');
	const memberEditModal = memberEditModalEl ? bootstrap.Modal.getOrCreateInstance(memberEditModalEl) : null;

	const memberInputs = {
		id: document.getElementById('admin-client-detail-fourth-member-id'),
		name: document.getElementById('admin-client-detail-fourth-member-name'),
		phone: document.getElementById('admin-client-detail-fourth-member-phone'),
		email: document.getElementById('admin-client-detail-fourth-member-email'),
		telephone: document.getElementById('admin-client-detail-fourth-member-telephone')
	};

	const memberSaveBtn = document.getElementById('admin-client-detail-fourth-member-save-btn');
	let memberOriginalState = '';

	function serializeMemberForm() {
		return JSON.stringify({
			name: trimValue(memberInputs.name.value),
			phone: trimValue(memberInputs.phone.value),
			email: trimValue(memberInputs.email.value),
			telephone: trimValue(memberInputs.telephone.value)
		});
	}

	function validateMemberForm() {
		const name = trimValue(memberInputs.name.value);
		const phone = trimValue(memberInputs.phone.value);
		const email = trimValue(memberInputs.email.value);

		if (!name) {
			return false;
		}

		if (!phone) {
			return false;
		}

		if (email) {
			const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
			if (!emailRegex.test(email)) {
				return false;
			}
		}

		return true;
	}

	function toggleMemberSaveButton() {
		if (!memberSaveBtn) {
			return;
		}

		memberSaveBtn.disabled = !(validateMemberForm() && serializeMemberForm() !== memberOriginalState);
	}

	document.querySelectorAll('.admin-client-detail-fourth-edit-member-btn').forEach(function(btn) {
		btn.addEventListener('click', function() {
			memberInputs.id.value = btn.getAttribute('data-member-id') || '';
			memberInputs.name.value = btn.getAttribute('data-member-name') || '';
			memberInputs.phone.value = btn.getAttribute('data-member-phone') || '';
			memberInputs.email.value = btn.getAttribute('data-member-email') || '';
			memberInputs.telephone.value = btn.getAttribute('data-member-telephone') || '';

			memberOriginalState = serializeMemberForm();
			toggleMemberSaveButton();

			if (memberEditModal) {
				memberEditModal.show();
			}
		});
	});

	if (memberEditModalEl) {
		memberEditModalEl.addEventListener('shown.bs.modal', function() {
			if (memberInputs.name) {
				memberInputs.name.focus();
			}
		});
	}

	['name', 'phone', 'email', 'telephone'].forEach(function(key) {
		if (!memberInputs[key]) {
			return;
		}

		memberInputs[key].addEventListener('input', toggleMemberSaveButton);
		memberInputs[key].addEventListener('change', toggleMemberSaveButton);
	});

	if (memberSaveBtn) {
		memberSaveBtn.addEventListener('click', async function() {
			if (!validateMemberForm()) {
				alert('이름 / 연락처를 확인해주세요. 이메일 형식도 확인해주세요.');
				return;
			}

			const memberId = memberInputs.id.value;

			if (!memberId) {
				alert('회원 식별자가 없습니다.');
				return;
			}

			const ok = confirm('고객정보 수정사항을 반영하시겠습니까?');
			if (!ok) {
				return;
			}

			try {
				await postJson('/management/member/' + memberId + '/updateInfo', {
					name: trimValue(memberInputs.name.value),
					phone: trimValue(memberInputs.phone.value),
					email: trimValue(memberInputs.email.value),
					telephone: trimValue(memberInputs.telephone.value)
				});

				alert('고객정보가 수정되었습니다.');
				location.reload();
			} catch (e) {
				alert('실패: ' + (e && e.message ? e.message : e));
			}
		});
	}
})();