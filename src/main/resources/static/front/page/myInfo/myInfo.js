/* /front/page/myInfo/myInfo.js */
/* global daum, bootstrap */

(function() {
	'use strict';

	// =========================
	// DOM helpers
	// =========================
	function qs(id) {
		return document.getElementById(id);
	}

	var mainForm = qs('myinfo-main-form');

	var phoneEl = qs('myinfo-added-phone');
	var phoneHelp = qs('myinfo-added-phone-help');

	var bizEl = qs('myinfo-added-businessNumber');
	var bizHelp = qs('myinfo-added-businessNumber-help');

	var submitBtn = qs('myinfo-added-submit');

	var licenseInput = qs('myinfo-added-license-input');
	var existingBox = qs('myinfo-added-license-existing');
	var existingImg = qs('myinfo-added-license-existing-img');
	var existingRemoveBtn = qs('myinfo-added-license-existing-remove');

	var newBox = qs('myinfo-added-license-new');
	var newImg = qs('myinfo-added-license-new-img');
	var newName = qs('myinfo-license-new-name');
	var newRemoveBtn = qs('myinfo-added-license-new-remove');
	var licenseEmpty = qs('myinfo-license-empty');

	var removeHidden = qs('removeBusinessLicense');
	var licenseHelp = qs('myinfo-added-license-help');

	var existingLicenseUrl = window.__MYINFO_EXISTING_LICENSE_URL__ || null;
	var isRepresentative = !!window.__MYINFO_IS_REPRESENTATIVE__;

	var state = {
		phoneOk: true,
		bizOk: true,
		licenseOk: true,
		hasExisting: !!existingLicenseUrl,
		existingRemoved: false,
		hasNewFile: false
	};

	// =========================
	// Common utilities
	// =========================
	function onlyDigits(value) {
		return (value || '').toString().replace(/[^0-9]/g, '');
	}

	function setHelp(element, message, type) {
		if (!element) return;

		element.textContent = message || '';
		element.classList.remove('ok', 'bad');

		if (type === 'ok') {
			element.classList.add('ok');
		} else if (type === 'bad') {
			element.classList.add('bad');
		}
	}

	function showPageNotice(message, type) {
		var wrap = qs('myinfo-page-notice');
		if (!wrap || !message) return;

		var notice = document.createElement('div');
		notice.className = 'myinfo-notice';

		if (type === 'danger') {
			notice.classList.add('myinfo-notice-danger');
			notice.innerHTML = '<i class="fa fa-exclamation-circle"></i><span></span>';
		} else if (type === 'success') {
			notice.classList.add('myinfo-notice-success');
			notice.innerHTML = '<i class="fa fa-check-circle"></i><span></span>';
		} else {
			notice.classList.add('myinfo-notice-info');
			notice.innerHTML = '<i class="fa fa-info-circle"></i><span></span>';
		}

		notice.querySelector('span').textContent = message;

		var dynamic = wrap.querySelector('.myinfo-notice-dynamic');
		if (dynamic) {
			dynamic.remove();
		}

		notice.classList.add('myinfo-notice-dynamic');
		wrap.prepend(notice);

		if (typeof window.scrollTo === 'function') {
			window.scrollTo({
				top: Math.max(0, wrap.getBoundingClientRect().top + window.pageYOffset - 90),
				behavior: 'smooth'
			});
		}
	}

	function formatPhone(digits) {
		digits = onlyDigits(digits).substring(0, 11);

		if (digits.length >= 11) {
			return digits.substring(0, 3) + '-' + digits.substring(3, 7) + '-' + digits.substring(7, 11);
		}
		if (digits.length === 10) {
			return digits.substring(0, 3) + '-' + digits.substring(3, 6) + '-' + digits.substring(6, 10);
		}
		if (digits.length <= 3) {
			return digits;
		}
		if (digits.length <= 7) {
			return digits.substring(0, 3) + '-' + digits.substring(3);
		}
		return digits.substring(0, 3) + '-' + digits.substring(3, 7) + '-' + digits.substring(7);
	}

	function formatBusinessNumber(digits) {
		digits = onlyDigits(digits).substring(0, 10);

		if (digits.length >= 10) {
			return digits.substring(0, 3) + '-' + digits.substring(3, 5) + '-' + digits.substring(5, 10);
		}
		if (digits.length <= 3) {
			return digits;
		}
		if (digits.length <= 5) {
			return digits.substring(0, 3) + '-' + digits.substring(3);
		}
		return digits.substring(0, 3) + '-' + digits.substring(3, 5) + '-' + digits.substring(5);
	}

	function updateSubmitState() {
		if (!submitBtn) return;

		if (isRepresentative && (licenseInput || existingBox || newBox)) {
			var hasLicenseNow = (!state.existingRemoved && state.hasExisting) || state.hasNewFile;
			state.licenseOk = hasLicenseNow;

			if (!hasLicenseNow) {
				setHelp(
					licenseHelp,
					'사업자등록증은 필수입니다. 새 파일을 선택한 후 저장해 주세요.',
					'bad'
				);
			} else {
				setHelp(licenseHelp, '', null);
			}
		}

		submitBtn.disabled = !(state.phoneOk && state.bizOk && state.licenseOk);
	}

	// =========================
	// Duplicate checks
	// =========================
	function checkPhoneDup() {
		if (!phoneEl) return Promise.resolve(true);

		var digits = onlyDigits(phoneEl.value);
		var formatted = formatPhone(digits);
		phoneEl.value = formatted;

		if (!(digits.length === 10 || digits.length === 11)) {
			state.phoneOk = false;
			setHelp(phoneHelp, '휴대폰 번호 형식이 올바르지 않습니다.', 'bad');
			updateSubmitState();
			return Promise.resolve(false);
		}

		return fetch('/customer/api/dup-check/phone?phone=' + encodeURIComponent(formatted), {
			method: 'GET',
			credentials: 'same-origin',
			headers: {
				'Accept': 'application/json'
			}
		})
			.then(function(response) {
				return response.json();
			})
			.then(function(data) {
				if (!data || data.ok === false) {
					state.phoneOk = false;
					setHelp(phoneHelp, data && data.message ? data.message : '중복체크에 실패했습니다.', 'bad');
					updateSubmitState();
					return false;
				}

				if (data.duplicate) {
					state.phoneOk = false;
					setHelp(phoneHelp, '이미 사용 중인 연락처입니다.', 'bad');
				} else {
					state.phoneOk = true;
					setHelp(phoneHelp, '사용 가능한 연락처입니다.', 'ok');
				}

				updateSubmitState();
				return state.phoneOk;
			})
			.catch(function() {
				state.phoneOk = false;
				setHelp(phoneHelp, '연락처 중복체크 중 네트워크 오류가 발생했습니다.', 'bad');
				updateSubmitState();
				return false;
			});
	}

	function checkBizDup() {
		if (!bizEl) return Promise.resolve(true);

		// 직원 계정은 readonly이므로 서버 중복체크를 반복할 필요가 없습니다.
		if (bizEl.readOnly) {
			state.bizOk = true;
			setHelp(bizHelp, '', null);
			updateSubmitState();
			return Promise.resolve(true);
		}

		var digits = onlyDigits(bizEl.value);
		bizEl.value = formatBusinessNumber(digits);

		if (digits.length !== 10) {
			state.bizOk = false;
			setHelp(bizHelp, '사업자등록번호는 숫자 10자리여야 합니다.', 'bad');
			updateSubmitState();
			return Promise.resolve(false);
		}

		return fetch(
			'/customer/api/dup-check/business-number?businessNumber=' + encodeURIComponent(digits),
			{
				method: 'GET',
				credentials: 'same-origin',
				headers: {
					'Accept': 'application/json'
				}
			}
		)
			.then(function(response) {
				return response.json();
			})
			.then(function(data) {
				if (!data || data.ok === false) {
					state.bizOk = false;
					setHelp(bizHelp, data && data.message ? data.message : '중복체크에 실패했습니다.', 'bad');
					updateSubmitState();
					return false;
				}

				if (data.duplicate) {
					state.bizOk = false;
					setHelp(bizHelp, '이미 등록된 사업자등록번호입니다.', 'bad');
				} else {
					state.bizOk = true;
					setHelp(bizHelp, '사용 가능한 사업자등록번호입니다.', 'ok');
				}

				updateSubmitState();
				return state.bizOk;
			})
			.catch(function() {
				state.bizOk = false;
				setHelp(bizHelp, '사업자등록번호 중복체크 중 네트워크 오류가 발생했습니다.', 'bad');
				updateSubmitState();
				return false;
			});
	}

	// =========================
	// Basic field binding
	// =========================
	function bindPhoneInput() {
		if (!phoneEl) return;

		phoneEl.value = formatPhone(onlyDigits(phoneEl.value));

		phoneEl.addEventListener('input', function() {
			var digits = onlyDigits(phoneEl.value);
			phoneEl.value = formatPhone(digits);
			state.phoneOk = true;
			setHelp(phoneHelp, '', null);
			updateSubmitState();
		});

		phoneEl.addEventListener('blur', function() {
			checkPhoneDup();
		});
	}

	function bindBizInput() {
		if (!bizEl) return;

		var initialDigits = onlyDigits(bizEl.value);
		if (initialDigits) {
			bizEl.value = formatBusinessNumber(initialDigits);
		}

		if (bizEl.readOnly) {
			state.bizOk = true;
			return;
		}

		bizEl.addEventListener('input', function() {
			var digits = onlyDigits(bizEl.value);
			bizEl.value = formatBusinessNumber(digits);
			state.bizOk = true;
			setHelp(bizHelp, '', null);
			updateSubmitState();
		});

		bizEl.addEventListener('blur', function() {
			checkBizDup();
		});
	}

	// =========================
	// Business license
	// =========================
	function syncLicenseEmptyState() {
		if (!licenseEmpty) return;

		var hasAnyVisibleLicense = state.hasNewFile || (state.hasExisting && !state.existingRemoved);

		if (hasAnyVisibleLicense) {
			licenseEmpty.classList.add('d-none');
		} else {
			licenseEmpty.classList.remove('d-none');
		}
	}

	function showExisting() {
		if (existingBox) {
			existingBox.classList.remove('d-none');
		}
		if (newBox) {
			newBox.classList.add('d-none');
		}
		syncLicenseEmptyState();
	}

	function hideExisting() {
		if (existingBox) {
			existingBox.classList.add('d-none');
		}
		syncLicenseEmptyState();
	}

	function showNewPreview(dataUrl, filename) {
		if (!newBox || !newImg) return;

		newImg.src = dataUrl || '';
		if (newName) {
			newName.textContent = filename || '선택한 파일';
		}

		newBox.classList.remove('d-none');

		if (existingBox) {
			existingBox.classList.add('d-none');
		}

		syncLicenseEmptyState();
	}

	function hideNewPreview() {
		if (newBox) {
			newBox.classList.add('d-none');
		}
		if (newImg) {
			newImg.src = '';
		}
		if (newName) {
			newName.textContent = '선택한 파일';
		}
		syncLicenseEmptyState();
	}

	function clearNewFile() {
		if (licenseInput) {
			licenseInput.value = '';
		}
		state.hasNewFile = false;
	}

	function onExistingRemoveClick() {
		// 현재 백엔드는 사업자등록증을 필수로 유지하므로
		// 기존 파일의 단독 삭제는 허용하지 않습니다.
		// 교체 버튼은 기존 파일을 그대로 유지한 상태에서 새 파일 선택창만 엽니다.
		if (licenseInput) {
			licenseInput.click();
		}
	}

	function onNewRemoveClick() {
		clearNewFile();
		hideNewPreview();

		if (state.hasExisting && !state.existingRemoved) {
			showExisting();
		}

		updateSubmitState();
	}

	function onLicenseInputChange() {
		if (!licenseInput) return;

		var file = licenseInput.files && licenseInput.files[0];
		if (!file) {
			updateSubmitState();
			return;
		}

		if (file.type && file.type.indexOf('image/') !== 0) {
			licenseInput.value = '';
			state.hasNewFile = false;
			setHelp(licenseHelp, '이미지 파일만 등록할 수 있습니다.', 'bad');
			updateSubmitState();
			return;
		}

		state.hasNewFile = true;
		state.existingRemoved = false;

		if (removeHidden) {
			removeHidden.value = 'false';
		}

		var reader = new FileReader();

		reader.onload = function(event) {
			showNewPreview(event.target.result, file.name);
			updateSubmitState();
		};

		reader.onerror = function() {
			clearNewFile();
			setHelp(licenseHelp, '선택한 이미지 미리보기를 불러올 수 없습니다.', 'bad');
			updateSubmitState();
		};

		reader.readAsDataURL(file);
	}

	function bindLicenseUI() {
		if (!isRepresentative) return;

		state.hasExisting = !!existingLicenseUrl;
		state.existingRemoved = false;
		state.hasNewFile = false;

		if (!state.hasExisting && existingBox) {
			existingBox.classList.add('d-none');
		}

		if (licenseInput) {
			licenseInput.addEventListener('change', onLicenseInputChange);
		}
		if (existingRemoveBtn) {
			existingRemoveBtn.addEventListener('click', onExistingRemoveClick);
		}
		if (newRemoveBtn) {
			newRemoveBtn.addEventListener('click', onNewRemoveClick);
		}

		syncLicenseEmptyState();
	}

	// =========================
	// Daum postcode
	// =========================
	function applyResolvedRegion(region, fallbackAddress, fallbackZipCode) {
		region = region || {};

		var roadInput = qs('searchAddress');
		var zipInput = qs('zipCode');
		var doInput = qs('doName');
		var siInput = qs('siName');
		var guInput = qs('guName');
		var detailInput = qs('detailAddress');

		if (roadInput) {
			roadInput.value = region.roadAddress || fallbackAddress || '';
		}
		if (zipInput) {
			zipInput.value = region.zipCode || fallbackZipCode || '';
		}
		if (doInput) {
			doInput.value = region.doName || '';
		}
		if (siInput) {
			siInput.value = region.siName || '';
		}
		if (guInput) {
			guInput.value = region.guName || '';
		}

		if (detailInput) {
			detailInput.focus();
		}
	}

	window.execDaumPostcode = function execDaumPostcode() {
		if (!window.daum || !daum.Postcode) {
			showPageNotice('주소검색 모듈을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', 'danger');
			return;
		}

		new daum.Postcode({
			oncomplete: function(data) {
				var address = data.userSelectedType === 'J'
					? (data.jibunAddress || data.roadAddress || '')
					: (data.roadAddress || data.jibunAddress || '');

				if (
					window.HiddenAutoAddressRegion &&
					typeof window.HiddenAutoAddressRegion.fromDaum === 'function'
				) {
					applyResolvedRegion(
						window.HiddenAutoAddressRegion.fromDaum(data),
						address,
						data.zonecode || ''
					);
					return;
				}

				var addrParts = (address || '').trim().split(/\s+/);
				var doName = addrParts[0] || '';
				var siName = '';
				var guName = '';

				if (addrParts.length >= 2) {
					if (/(시|군)$/.test(addrParts[1])) {
						siName = addrParts[1];
						guName = addrParts[2] || '';
					} else {
						guName = addrParts[1] || '';
					}
				}

				applyResolvedRegion({
					doName: doName,
					siName: siName,
					guName: guName,
					roadAddress: address,
					zipCode: data.zonecode || ''
				}, address, data.zonecode || '');
			}
		}).open();
	};

	// =========================
	// Additional delivery addresses
	// =========================
	function setupCompanyDeliveryUI() {
		var addBtn = qs('companyAddDeliveryBtn');
		var listEl = qs('companyDeliveryList');
		var hiddenEl = qs('companyDeliveryAddressesJson');

		if (!addBtn || !listEl || !hiddenEl) return;

		var modalEl = qs('companyDeliveryDetailModal');
		var selectedAddrEl = qs('companyDeliverySelectedAddress');
		var detailInputEl = qs('companyDeliveryDetailInput');
		var saveBtn = qs('companyDeliveryDetailSaveBtn');

		var modalInstance = null;

		if (modalEl && window.bootstrap && bootstrap.Modal) {
			modalInstance = bootstrap.Modal.getOrCreateInstance(modalEl, {
				backdrop: 'static',
				keyboard: true
			});
		}

		var pending = null;

		var items = Array.isArray(window.__COMPANY_DELIVERY_ADDRESSES__)
			? window.__COMPANY_DELIVERY_ADDRESSES__.map(function(item) {
				return {
					id: item.id || null,
					zipCode: item.zipCode || '',
					doName: item.doName || '',
					siName: item.siName || '',
					guName: item.guName || '',
					roadAddress: item.roadAddress || '',
					detailAddress: item.detailAddress || ''
				};
			})
			: [];

		function syncHidden() {
			hiddenEl.value = JSON.stringify(items);
		}

		function createDeleteButton(index) {
			var button = document.createElement('button');
			button.type = 'button';
			button.className = 'myinfo-icon-btn myinfo-icon-btn-danger';
			button.setAttribute('aria-label', '배송지 삭제');
			button.setAttribute('title', '배송지 삭제');
			button.innerHTML = '<i class="fa fa-trash"></i>';

			button.addEventListener('click', function() {
				if (!window.confirm('해당 추가 배송지를 삭제하시겠습니까?')) {
					return;
				}

				items.splice(index, 1);
				syncHidden();
				render();
			});

			return button;
		}

		function render() {
			listEl.innerHTML = '';

			if (!items.length) {
				var empty = document.createElement('div');
				empty.className = 'myinfo-list-empty';
				empty.innerHTML = '<i class="fa fa-map-marker-alt"></i><span>등록된 추가 배송지가 없습니다.</span>';
				listEl.appendChild(empty);
				return;
			}

			items.forEach(function(item, index) {
				var row = document.createElement('div');
				row.className = 'myinfo-delivery-row';

				var zip = document.createElement('div');
				zip.className = 'myinfo-delivery-zip';
				zip.textContent = item.zipCode ? item.zipCode : '-';

				var main = document.createElement('div');
				main.className = 'myinfo-delivery-main';

				var address = document.createElement('div');
				address.className = 'myinfo-delivery-address';
				address.textContent = [
					item.roadAddress || '',
					item.detailAddress || ''
				].filter(Boolean).join(' ') || '-';

				var region = document.createElement('div');
				region.className = 'myinfo-delivery-region';
				region.textContent = [
					item.doName || '',
					item.siName || '',
					item.guName || ''
				].filter(Boolean).join(' ');

				main.appendChild(address);

				if (region.textContent) {
					main.appendChild(region);
				}

				row.appendChild(zip);
				row.appendChild(main);
				row.appendChild(createDeleteButton(index));

				listEl.appendChild(row);
			});
		}

		function openDetailModal(pendingItem) {
			pending = pendingItem;

			if (selectedAddrEl) {
				selectedAddrEl.textContent = [
					pending.doName || '',
					pending.siName || '',
					pending.guName || '',
					pending.roadAddress || ''
				].filter(Boolean).join(' ');
			}

			if (detailInputEl) {
				detailInputEl.value = '';
			}

			if (!modalInstance) {
				showPageNotice('배송지 상세주소 입력창을 열 수 없습니다. Bootstrap 로드를 확인해 주세요.', 'danger');
				return;
			}

			modalInstance.show();

			window.setTimeout(function() {
				if (detailInputEl) {
					detailInputEl.focus();
				}
			}, 150);
		}

		function addPendingToItems(detailText) {
			if (!pending) return false;

			var newItem = {
				id: null,
				zipCode: pending.zipCode || '',
				doName: pending.doName || '',
				siName: pending.siName || '',
				guName: pending.guName || '',
				roadAddress: pending.roadAddress || '',
				detailAddress: (detailText || '').trim()
			};

			if (!newItem.roadAddress) {
				pending = null;
				return false;
			}

			var duplicated = items.some(function(item) {
				return item.roadAddress === newItem.roadAddress &&
					item.detailAddress === newItem.detailAddress;
			});

			if (duplicated) {
				showPageNotice('이미 등록된 추가 배송지입니다.', 'danger');
				pending = null;
				return false;
			}

			items.push(newItem);
			pending = null;
			syncHidden();
			render();
			return true;
		}

		function openDaumAndAdd() {
			if (!window.daum || !daum.Postcode) {
				showPageNotice('주소검색 모듈을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.', 'danger');
				return;
			}

			new daum.Postcode({
				oncomplete: function(data) {
					var address = data.userSelectedType === 'J'
						? (data.jibunAddress || data.roadAddress || '')
						: (data.roadAddress || data.jibunAddress || '');

					var pendingItem;

					if (
						window.HiddenAutoAddressRegion &&
						typeof window.HiddenAutoAddressRegion.fromDaum === 'function'
					) {
						var region = window.HiddenAutoAddressRegion.fromDaum(data);

						pendingItem = {
							zipCode: region.zipCode || data.zonecode || '',
							doName: region.doName || '',
							siName: region.siName || '',
							guName: region.guName || '',
							roadAddress: region.roadAddress || address || ''
						};
					} else {
						pendingItem = {
							zipCode: data.zonecode || '',
							doName: data.sido || '',
							siName: data.sigungu || '',
							guName: data.bname || '',
							roadAddress: address || ''
						};
					}

					if (!pendingItem.roadAddress) return;

					openDetailModal(pendingItem);
				}
			}).open();
		}

		if (saveBtn) {
			saveBtn.addEventListener('click', function() {
				var detailText = detailInputEl ? detailInputEl.value : '';

				if (addPendingToItems(detailText) && modalInstance) {
					modalInstance.hide();
				}
			});
		}

		if (detailInputEl) {
			detailInputEl.addEventListener('keydown', function(event) {
				if (event.key === 'Enter') {
					event.preventDefault();
					if (saveBtn) {
						saveBtn.click();
					}
				}
			});
		}

		if (modalEl) {
			modalEl.addEventListener('hidden.bs.modal', function() {
				pending = null;

				if (detailInputEl) {
					detailInputEl.value = '';
				}
				if (selectedAddrEl) {
					selectedAddrEl.textContent = '';
				}
			});
		}

		addBtn.addEventListener('click', openDaumAndAdd);

		syncHidden();
		render();
	}

	// =========================
	// Orderer information
	// =========================
	function setupCustomerMyInfoOrdererUI() {
		var addBtn = qs('customer-myInfo-orderer-add-btn');
		var listEl = qs('customer-myInfo-orderer-list');

		var modalEl = qs('customer-myInfo-orderer-modal');
		var nameEl = qs('customer-myInfo-orderer-name');
		var phoneInputEl = qs('customer-myInfo-orderer-phone');
		var phoneHelpEl = qs('customer-myInfo-orderer-phone-help');
		var saveBtn = qs('customer-myInfo-orderer-save-btn');

		if (!addBtn || !listEl || !modalEl || !nameEl || !phoneInputEl || !saveBtn) {
			return;
		}

		var modalInstance = null;

		if (window.bootstrap && bootstrap.Modal) {
			modalInstance = bootstrap.Modal.getOrCreateInstance(modalEl, {
				backdrop: 'static',
				keyboard: true
			});
		}

		var ordererItems = Array.isArray(window.__CUSTOMER_MYINFO_ORDERER_INFOS__)
			? window.__CUSTOMER_MYINFO_ORDERER_INFOS__.map(function(item) {
				return {
					id: item.id || null,
					name: item.name || '',
					phone: item.phone || ''
				};
			})
			: [];

		function renderOrdererList() {
			listEl.innerHTML = '';

			if (!ordererItems.length) {
				var empty = document.createElement('div');
				empty.className = 'myinfo-list-empty';
				empty.innerHTML = '<i class="fa fa-address-card"></i><span>등록된 주문자 정보가 없습니다.</span>';
				listEl.appendChild(empty);
				return;
			}

			ordererItems.forEach(function(item) {
				var row = document.createElement('div');
				row.className = 'myinfo-orderer-row';

				var name = document.createElement('div');
				name.className = 'myinfo-orderer-name';
				name.textContent = item.name || '-';

				var phone = document.createElement('a');
				phone.className = 'myinfo-orderer-phone';
				phone.textContent = item.phone || '-';

				if (item.phone) {
					phone.href = 'tel:' + onlyDigits(item.phone);
				} else {
					phone.removeAttribute('href');
				}

				var deleteBtn = document.createElement('button');
				deleteBtn.type = 'button';
				deleteBtn.className = 'myinfo-icon-btn myinfo-icon-btn-danger';
				deleteBtn.setAttribute('aria-label', '주문자 정보 삭제');
				deleteBtn.setAttribute('title', '주문자 정보 삭제');
				deleteBtn.innerHTML = '<i class="fa fa-trash"></i>';

				deleteBtn.addEventListener('click', function() {
					deleteOrdererInfo(item.id);
				});

				row.appendChild(name);
				row.appendChild(phone);
				row.appendChild(deleteBtn);

				listEl.appendChild(row);
			});
		}

		function resetModal() {
			nameEl.value = '';
			phoneInputEl.value = '';
			setHelp(phoneHelpEl, '', null);
			saveBtn.disabled = false;
		}

		function openModal() {
			resetModal();

			if (!modalInstance) {
				showPageNotice('주문자 정보 등록창을 열 수 없습니다. Bootstrap 로드를 확인해 주세요.', 'danger');
				return;
			}

			modalInstance.show();

			window.setTimeout(function() {
				nameEl.focus();
			}, 150);
		}

		function validateOrdererInput() {
			var name = (nameEl.value || '').trim();
			var digits = onlyDigits(phoneInputEl.value);

			if (!name) {
				nameEl.focus();
				showPageNotice('주문자 이름을 입력해 주세요.', 'danger');
				return null;
			}

			if (name.length > 50) {
				nameEl.focus();
				showPageNotice('주문자 이름은 50자 이하로 입력해 주세요.', 'danger');
				return null;
			}

			if (!(digits.length === 10 || digits.length === 11)) {
				setHelp(phoneHelpEl, '연락처는 숫자 10자리 또는 11자리로 입력해 주세요.', 'bad');
				phoneInputEl.focus();
				return null;
			}

			return {
				name: name,
				phone: formatPhone(digits)
			};
		}

		function createOrdererInfo() {
			var payload = validateOrdererInput();
			if (!payload) return;

			saveBtn.disabled = true;

			fetch('/customer/api/orderer-infos', {
				method: 'POST',
				credentials: 'same-origin',
				headers: {
					'Accept': 'application/json',
					'Content-Type': 'application/json'
				},
				body: JSON.stringify(payload)
			})
				.then(function(response) {
					return response.json().then(function(body) {
						return {
							ok: response.ok,
							body: body
						};
					});
				})
				.then(function(result) {
					if (!result.ok || !result.body || result.body.success === false) {
						showPageNotice(
							result.body && result.body.message
								? result.body.message
								: '주문자 정보 등록에 실패했습니다.',
							'danger'
						);
						saveBtn.disabled = false;
						return;
					}

					var saved = result.body.data;

					ordererItems.push({
						id: saved.id,
						name: saved.name,
						phone: saved.phone
					});

					renderOrdererList();

					if (modalInstance) {
						modalInstance.hide();
					}

					showPageNotice(
						result.body.message || '주문자 정보가 등록되었습니다.',
						'success'
					);
				})
				.catch(function() {
					showPageNotice('주문자 정보 등록 중 네트워크 오류가 발생했습니다.', 'danger');
					saveBtn.disabled = false;
				});
		}

		function deleteOrdererInfo(id) {
			if (!id) {
				showPageNotice('삭제할 주문자 정보가 올바르지 않습니다.', 'danger');
				return;
			}

			if (!window.confirm('해당 주문자 정보를 삭제하시겠습니까?')) {
				return;
			}

			fetch('/customer/api/orderer-infos/' + encodeURIComponent(id), {
				method: 'DELETE',
				credentials: 'same-origin',
				headers: {
					'Accept': 'application/json'
				}
			})
				.then(function(response) {
					return response.json().then(function(body) {
						return {
							ok: response.ok,
							body: body
						};
					});
				})
				.then(function(result) {
					if (!result.ok || !result.body || result.body.success === false) {
						showPageNotice(
							result.body && result.body.message
								? result.body.message
								: '주문자 정보 삭제에 실패했습니다.',
							'danger'
						);
						return;
					}

					ordererItems = ordererItems.filter(function(item) {
						return String(item.id) !== String(id);
					});

					renderOrdererList();

					showPageNotice(
						result.body.message || '주문자 정보가 삭제되었습니다.',
						'success'
					);
				})
				.catch(function() {
					showPageNotice('주문자 정보 삭제 중 네트워크 오류가 발생했습니다.', 'danger');
				});
		}

		phoneInputEl.addEventListener('input', function() {
			var digits = onlyDigits(phoneInputEl.value).substring(0, 11);
			phoneInputEl.value = formatPhone(digits);
			setHelp(phoneHelpEl, '', null);
		});

		phoneInputEl.addEventListener('blur', function() {
			var digits = onlyDigits(phoneInputEl.value);

			if (!digits) {
				setHelp(phoneHelpEl, '', null);
				return;
			}

			if (!(digits.length === 10 || digits.length === 11)) {
				setHelp(phoneHelpEl, '연락처는 숫자 10자리 또는 11자리로 입력해 주세요.', 'bad');
				return;
			}

			phoneInputEl.value = formatPhone(digits);
			setHelp(phoneHelpEl, '', null);
		});

		nameEl.addEventListener('keydown', function(event) {
			if (event.key === 'Enter') {
				event.preventDefault();
				phoneInputEl.focus();
			}
		});

		phoneInputEl.addEventListener('keydown', function(event) {
			if (event.key === 'Enter') {
				event.preventDefault();
				createOrdererInfo();
			}
		});

		saveBtn.addEventListener('click', createOrdererInfo);
		addBtn.addEventListener('click', openModal);

		modalEl.addEventListener('hidden.bs.modal', function() {
			resetModal();
		});

		renderOrdererList();
	}

	// =========================
	// Registration key
	// =========================
	function copyText(text) {
		if (!text) {
			return Promise.reject(new Error('empty'));
		}

		if (navigator.clipboard && navigator.clipboard.writeText) {
			return navigator.clipboard.writeText(text);
		}

		return new Promise(function(resolve, reject) {
			var temporary = document.createElement('textarea');
			temporary.value = text;
			temporary.setAttribute('readonly', '');
			temporary.style.position = 'fixed';
			temporary.style.opacity = '0';

			document.body.appendChild(temporary);
			temporary.select();

			try {
				var copied = document.execCommand('copy');
				document.body.removeChild(temporary);

				if (copied) {
					resolve();
				} else {
					reject(new Error('copy failed'));
				}
			} catch (error) {
				document.body.removeChild(temporary);
				reject(error);
			}
		});
	}

	function bindRegistrationKeyUI() {
		var keyInput = qs('myinfo-registration-key');
		var copyBtn = qs('myinfo-registration-copy-btn');
		var generateBtn = qs('myinfo-registration-generate-btn');

		if (copyBtn && keyInput) {
			copyBtn.addEventListener('click', function() {
				copyText((keyInput.value || '').trim())
					.then(function() {
						showPageNotice('대리점 코드를 클립보드에 복사했습니다.', 'success');
					})
					.catch(function() {
						showPageNotice('복사할 대리점 코드가 없거나 클립보드를 사용할 수 없습니다.', 'danger');
					});
			});
		}

		if (generateBtn && keyInput) {
			generateBtn.addEventListener('click', function() {
				if (!window.confirm('새 대리점 코드를 생성하시겠습니까? 기존 코드는 더 이상 사용할 수 없습니다.')) {
					return;
				}

				generateBtn.disabled = true;

				fetch('/customer/generateRegistrationKey', {
					method: 'POST',
					credentials: 'same-origin',
					headers: {
						'Accept': 'application/json'
					}
				})
					.then(function(response) {
						if (!response.ok) {
							throw new Error('registration key request failed');
						}
						return response.json();
					})
					.then(function(data) {
						if (!data || !data.key) {
							throw new Error('registration key missing');
						}

						keyInput.value = data.key;
						showPageNotice('새 대리점 코드가 생성되었습니다.', 'success');
					})
					.catch(function() {
						showPageNotice('대리점 코드 생성 중 오류가 발생했습니다.', 'danger');
					})
					.finally(function() {
						generateBtn.disabled = false;
					});
			});
		}
	}

	// 기존 외부 호출 호환성 유지
	window.generateKey = function generateKey() {
		var button = qs('myinfo-registration-generate-btn');

		if (button) {
			button.click();
			return;
		}

		fetch('/customer/generateRegistrationKey', {
			method: 'POST',
			credentials: 'same-origin',
			headers: {
				'Accept': 'application/json'
			}
		})
			.then(function(response) {
				return response.json();
			})
			.then(function(data) {
				showPageNotice('새 대리점 코드가 생성되었습니다: ' + data.key, 'success');
			});
	};

	// =========================
	// Password
	// =========================
	function bindPasswordForm() {
		var form = qs('passwordForm');
		var password = qs('myinfo-new-password');
		var confirmPassword = qs('myinfo-new-password-confirm');
		var help = qs('myinfo-password-help');

		if (!form || !password || !confirmPassword) return;

		function validateMatch() {
			if (!confirmPassword.value) {
				setHelp(help, '', null);
				return true;
			}

			if (password.value !== confirmPassword.value) {
				setHelp(help, '입력한 비밀번호가 서로 다릅니다.', 'bad');
				return false;
			}

			setHelp(help, '비밀번호가 일치합니다.', 'ok');
			return true;
		}

		password.addEventListener('input', validateMatch);
		confirmPassword.addEventListener('input', validateMatch);

		form.addEventListener('submit', function(event) {
			if (!validateMatch()) {
				event.preventDefault();
				confirmPassword.focus();
			}
		});
	}

	// =========================
	// Main form final guard
	// =========================
	function bindMainFormGuard() {
		if (!mainForm) return;

		mainForm.addEventListener('submit', function(event) {
			// addressRegion.js가 capture 단계에서 먼저 최종 보정합니다.
			var phoneDigits = onlyDigits(phoneEl ? phoneEl.value : '');

			if (!(phoneDigits.length === 10 || phoneDigits.length === 11)) {
				event.preventDefault();
				state.phoneOk = false;
				setHelp(phoneHelp, '휴대폰 번호 형식이 올바르지 않습니다.', 'bad');
				updateSubmitState();
				if (phoneEl) {
					phoneEl.focus();
				}
				return;
			}

			if (bizEl && !bizEl.readOnly) {
				var bizDigits = onlyDigits(bizEl.value);

				if (bizDigits.length !== 10) {
					event.preventDefault();
					state.bizOk = false;
					setHelp(bizHelp, '사업자등록번호는 숫자 10자리여야 합니다.', 'bad');
					updateSubmitState();
					bizEl.focus();
					return;
				}
			}

			if (isRepresentative) {
				var hasLicenseNow =
					(!state.existingRemoved && state.hasExisting) ||
					state.hasNewFile;

				if (!hasLicenseNow) {
					event.preventDefault();
					state.licenseOk = false;
					setHelp(
						licenseHelp,
						'사업자등록증은 필수입니다. 새 파일을 선택한 후 저장해 주세요.',
						'bad'
					);
					updateSubmitState();
				}
			}
		});
	}

	// =========================
	// Init
	// =========================
	document.addEventListener('DOMContentLoaded', function() {
		bindPhoneInput();
		bindBizInput();
		bindLicenseUI();

		setupCompanyDeliveryUI();
		setupCustomerMyInfoOrdererUI();

		bindRegistrationKeyUI();
		bindPasswordForm();
		bindMainFormGuard();

		updateSubmitState();
	});

})();
