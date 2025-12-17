/* global daum */

(function() {
	'use strict';

	// =========================
	// 0) 공통 유틸
	// =========================
	function qs(id) { return document.getElementById(id); }

	function onlyDigits(v) {
		return (v || '').toString().replace(/\D/g, '');
	}

	function escapeHtml(s) {
		return (s || '').replace(/[&<>"']/g, function(m) {
			switch (m) {
				case '&': return '&amp;';
				case '<': return '&lt;';
				case '>': return '&gt;';
				case '"': return '&quot;';
				case "'": return '&#39;';
				default: return m;
			}
		});
	}

	// =========================
	// 1) 사업자등록증 파일 표시/삭제 (기존)
	// =========================
	var fileUpload = qs('file-upload');
	if (fileUpload) {
		fileUpload.addEventListener('change', function() {
			var fileList = qs('file-list');
			if (!fileList) return;

			fileList.innerHTML = '';
			var files = this.files;

			if (files && files.length > 0) {
				var fileItem = document.createElement('li');
				fileItem.innerHTML =
					escapeHtml(files[0].name) +
					' <span class="remove-file" style="cursor:pointer;" id="removeSelectedFileBtn">✕</span>';

				fileList.appendChild(fileItem);

				var removeBtn = qs('removeSelectedFileBtn');
				if (removeBtn) {
					removeBtn.addEventListener('click', function() {
						fileUpload.value = '';
						fileList.innerHTML = '';
					});
				}
			}
		});
	}

	// =========================
	// 2) 다음 주소 (기존: 대표 기본주소용)
	// =========================
	window.execDaumPostcode = function execDaumPostcode() {
		new daum.Postcode({
			oncomplete: function(data) {
				var address = (data.userSelectedType === 'R') ? data.roadAddress : data.jibunAddress;

				qs('searchAddress').value = address;
				qs('detailAddress').focus();

				qs('doName').value = data.sido;
				qs('siName').value = data.sigungu;
				qs('guName').value = data.bname;
				qs('zipCode').value = data.zonecode;
			}
		}).open();
	};

	// =========================
	// 3) 아이디 중복 체크 (기존)
	// =========================
	['repUsername', 'empUsername'].forEach(function(id) {
		var input = qs(id);
		if (!input) return;

		input.addEventListener('change', async function() {
			var username = (this.value || '').trim();
			if (!username) return;

			try {
				var res = await fetch('/api/v1/validate/username?username=' + encodeURIComponent(username));
				var data = await res.json();
				if (data.duplicate) {
					alert('이미 사용 중인 아이디입니다.');
					this.value = '';
					this.focus();
				}
			} catch (e) {
				console.error('아이디 중복 확인 실패', e);
			}
		});
	});

	// =========================
	// 4) 비밀번호 일치 체크 (기존)
	// =========================
	var repPwCheck = qs('repPasswordCheck');
	if (repPwCheck) {
		repPwCheck.addEventListener('blur', function() {
			var pw = qs('repPassword') ? qs('repPassword').value : '';
			var check = this.value || '';
			if (pw && check && pw !== check) {
				alert('비밀번호가 일치하지 않습니다.');
				this.value = '';
				this.focus();
			}
		});
	}

	var empPwCheck = qs('empPasswordCheck');
	if (empPwCheck) {
		empPwCheck.addEventListener('blur', function() {
			var pw = qs('empPassword') ? qs('empPassword').value : '';
			var check = this.value || '';
			if (pw && check && pw !== check) {
				alert('비밀번호가 일치하지 않습니다.');
				this.value = '';
				this.focus();
			}
		});
	}

	// =========================
	// 5) 휴대폰 포맷 + 중복 체크 (기존)
	// =========================
	['repPhone', 'empPhone'].forEach(function(id) {
		var phoneInput = qs(id);
		if (!phoneInput) return;

		phoneInput.addEventListener('input', function() {
			var val = (this.value || '').replace(/\D/g, '').slice(0, 11);
			var formatted = val;
			if (val.length > 3 && val.length <= 7) formatted = val.replace(/(\d{3})(\d+)/, '$1-$2');
			else if (val.length > 7) formatted = val.replace(/(\d{3})(\d{4})(\d+)/, '$1-$2-$3');
			this.value = formatted;
		});

		phoneInput.addEventListener('blur', async function() {
			var rawNumber = (this.value || '').replace(/\D/g, '');
			if (rawNumber.length !== 11) return;

			try {
				var res = await fetch('/api/v1/validate/phone?phone=' + encodeURIComponent(rawNumber));
				var data = await res.json();
				if (data.duplicate) {
					alert('이미 등록된 연락처입니다.');
					this.value = '';
					this.focus();
				}
			} catch (e) {
				console.error('연락처 중복 확인 실패', e);
			}
		});
	});

	// =========================
	// 6) ✅ 사업자등록번호: 숫자만 입력 + 중복체크 + 제출 차단
	//    ✅ blur + alert + focus로 인한 "연속 검증" 방지 가드 추가
	// =========================
	var repBizInput = qs('repBusinessNumber');
	var repBizOk = true; // 기본 true, 실제로는 blur 체크 결과로 결정

	// ✅ 가드/캐시
	var repBizChecking = false;        // 검증 중 재진입 방지
	var repBizSuppressBlur = false;    // 프로그램 포커스 이동 시 blur 재호출 방지
	var repBizLastCheckedValue = '';   // 마지막으로 서버 검증한 10자리 값

	function focusLater(el) {
		// blur 핸들러 안에서 즉시 focus() 하지 않고 tick을 넘김
		setTimeout(function() {
			if (el) el.focus();
		}, 0);
	}

	if (repBizInput) {
		// 숫자만 입력
		repBizInput.addEventListener('input', function() {
			var digits = onlyDigits(this.value).slice(0, 10);
			this.value = digits;

			// 값이 바뀌면 다시 검증 필요
			repBizOk = false;

			// ✅ 마지막 검증값과 달라졌으면 캐시 무효화
			if (digits !== repBizLastCheckedValue) {
				repBizLastCheckedValue = '';
			}
		});

		// 포커스 아웃(blur) 시 중복 체크
		repBizInput.addEventListener('blur', async function() {
			// ✅ 프로그램적으로 focus를 다시 주는 과정에서 blur가 재호출되는 것 방지
			if (repBizSuppressBlur) return;

			var digits = onlyDigits(this.value);

			// 빈값이면 미통과 처리(원래 로직 유지)
			if (!digits) {
				repBizOk = false;
				return;
			}

			// 길이 체크
			if (digits.length !== 10) {
				repBizOk = false;

				// ✅ 여기서 focus()를 바로 주면 blur/검증이 꼬일 수 있어서 suppress + focusLater
				alert('사업자등록번호는 숫자 10자리로 입력해 주세요.');
				repBizSuppressBlur = true;
				focusLater(this);
				setTimeout(function() { repBizSuppressBlur = false; }, 200);
				return;
			}

			// ✅ 이미 같은 값으로 검증을 성공했고 ok면 재검증 스킵
			if (repBizOk === true && repBizLastCheckedValue === digits) {
				return;
			}

			// ✅ 검증 중 재진입 방지
			if (repBizChecking) return;
			repBizChecking = true;

			try {
				var res = await fetch('/api/v1/validate/businessNumber?businessNumber=' + encodeURIComponent(digits));
				var data = await res.json();

				if (data.duplicate) {
					repBizOk = false;
					repBizLastCheckedValue = '';

					alert('이미 등록된 사업자등록번호입니다.');
					this.value = '';

					repBizSuppressBlur = true;
					focusLater(this);
					setTimeout(function() { repBizSuppressBlur = false; }, 200);
					return;
				}

				// 통과
				repBizOk = true;
				repBizLastCheckedValue = digits;

			} catch (e) {
				console.error('사업자등록번호 중복 확인 실패', e);

				// 서버 통신 실패 시 가입 막는 것이 안전
				repBizOk = false;
				repBizLastCheckedValue = '';

				alert('사업자등록번호 중복 확인에 실패했습니다. 잠시 후 다시 시도해 주세요.');

				repBizSuppressBlur = true;
				focusLater(this);
				setTimeout(function() { repBizSuppressBlur = false; }, 200);
			} finally {
				repBizChecking = false;
			}
		});
	}

	// =========================
	// 7) ✅ 추가 배송지 (대표/직원 공통)
	//    - 버튼 클릭 -> daum.postcode -> 선택 -> 상세주소 prompt -> 리스트 쌓기 -> X 삭제
	//    - hidden input(JSON)로 서버 전송
	// =========================
	function setupDeliveryUI(opts) {
		var addBtn = qs(opts.addBtnId);
		var listEl = qs(opts.listElId);
		var hiddenEl = qs(opts.hiddenInputId);
		if (!addBtn || !listEl || !hiddenEl) return;

		var items = []; // {zipCode, doName, siName, guName, roadAddress, detailAddress}

		function syncHidden() {
			hiddenEl.value = items.length ? JSON.stringify(items) : '';
		}

		function render() {
			listEl.innerHTML = '';
			items.forEach(function(it, idx) {
				var wrap = document.createElement('div');
				wrap.className = 'd-flex align-items-center justify-content-between p-2 mb-1 rounded-sm border';

				var left = document.createElement('div');
				left.className = 'me-2';
				left.innerHTML = '<div class="font-600">' +
					escapeHtml((it.roadAddress || '') + (it.detailAddress ? (' ' + it.detailAddress) : '')) +
					'</div>';

				var right = document.createElement('button');
				right.type = 'button';
				right.className = 'btn btn-sm btn-outline-danger';
				right.textContent = 'X';
				right.addEventListener('click', function() {
					items.splice(idx, 1);
					syncHidden();
					render();
				});

				wrap.appendChild(left);
				wrap.appendChild(right);
				listEl.appendChild(wrap);
			});
		}

		function openDaumAndAdd() {
			new daum.Postcode({
				oncomplete: function(data) {
					var road = (data.userSelectedType === 'R') ? data.roadAddress : data.jibunAddress;

					// 상세주소는 요구사항상 "____" 형태라 prompt로 받습니다(0개 가능/비어도 허용)
					var detail = window.prompt('상세 주소를 입력해 주세요. (없으면 비워도 됩니다)', '') || '';
					detail = detail.trim();

					var newItem = {
						zipCode: data.zonecode || '',
						doName: data.sido || '',
						siName: data.sigungu || '',
						guName: data.bname || '',
						roadAddress: road || '',
						detailAddress: detail
					};

					if (!newItem.roadAddress) return;

					// 중복 방지(같은 road+detail이면 추가 막기)
					var dup = items.some(function(x) {
						return (x.roadAddress === newItem.roadAddress) && (x.detailAddress === newItem.detailAddress);
					});
					if (dup) {
						alert('이미 추가된 배송지입니다.');
						return;
					}

					items.push(newItem);
					syncHidden();
					render();
				}
			}).open();
		}

		addBtn.addEventListener('click', openDaumAndAdd);

		// 초기 동기화
		syncHidden();
		render();
	}

	setupDeliveryUI({
		addBtnId: 'repAddDeliveryBtn',
		listElId: 'repDeliveryList',
		hiddenInputId: 'repDeliveryAddressesJson'
	});

	setupDeliveryUI({
		addBtnId: 'empAddDeliveryBtn',
		listElId: 'empDeliveryList',
		hiddenInputId: 'empDeliveryAddressesJson'
	});

	// =========================
	// 8) ✅ 대표 폼 제출 시 사업자번호 검증 통과 안하면 가입 막기
	// =========================
	var repForm = qs('repSignUpForm');
	if (repForm) {
		repForm.addEventListener('submit', function(e) {
			// 사업자번호 input이 존재할 때만 체크
			if (repBizInput) {
				var digits = onlyDigits(repBizInput.value);
				if (digits.length !== 10) {
					e.preventDefault();
					alert('사업자등록번호는 숫자 10자리로 입력해 주세요.');
					repBizInput.focus();
					return;
				}
				if (!repBizOk) {
					e.preventDefault();
					alert('사업자등록번호 중복 확인이 필요합니다. (입력 후 포커스를 이동해 주세요)');
					repBizInput.focus();
					return;
				}
			}
		});
	}

})();
