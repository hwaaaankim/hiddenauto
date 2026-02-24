document.addEventListener("DOMContentLoaded", function () {
	'use strict';

	const form = document.querySelector("form");

	// =========================================================
	// 0) 안전장치: 필수 엘리먼트 존재 체크
	// =========================================================
	if (!form) return;

	// =========================================================
	// 1) 현장 연락처(onsiteContact) - 010 제한 제거
	//    - 숫자만 유지
	//    - 9~11자리 허용(02/지역번호/휴대폰)
	//    - 자동 하이픈 포맷
	// =========================================================
	const onsiteContactInput = document.getElementById("onsiteContact");

	function onlyDigits(v) {
		return String(v || "").replace(/\D/g, "");
	}

	/**
	 * 한국 전화번호 간단 포맷(9~11자리)
	 * - 02: 02-123-4567(9~10자리)
	 * - 그 외: 0xx-xxx-xxxx(10자리), 0xx-xxxx-xxxx(11자리)
	 * - 특정 국번/휴대폰 규칙(010 강제 등)은 제거
	 */
	function formatKoreanPhone(digits) {
		digits = onlyDigits(digits);

		// 최대 11자리까지만
		if (digits.length > 11) digits = digits.slice(0, 11);

		// 너무 짧으면 그대로
		if (digits.length <= 3) return digits;

		// 02 서울
		if (digits.startsWith("02")) {
			// 02-xxx-xxxx (9자리) / 02-xxxx-xxxx (10자리)
			if (digits.length <= 2) return digits;
			if (digits.length <= 5) return digits.slice(0, 2) + "-" + digits.slice(2);
			if (digits.length === 9) return digits.slice(0, 2) + "-" + digits.slice(2, 5) + "-" + digits.slice(5);
			// 10자리(정상)
			if (digits.length >= 10) return digits.slice(0, 2) + "-" + digits.slice(2, 6) + "-" + digits.slice(6, 10);
		}

		// 그 외 지역번호/휴대폰: 보통 3자리 지역/식별(031, 010 등)
		// 10자리: 0xx-xxx-xxxx
		if (digits.length === 10) {
			return digits.slice(0, 3) + "-" + digits.slice(3, 6) + "-" + digits.slice(6);
		}

		// 11자리: 0xx-xxxx-xxxx
		if (digits.length >= 11) {
			return digits.slice(0, 3) + "-" + digits.slice(3, 7) + "-" + digits.slice(7, 11);
		}

		// 4~9자리까지 입력 중: 0xx-...
		return digits.slice(0, 3) + "-" + digits.slice(3);
	}

	function setCaretToEnd(el) {
		try {
			const len = el.value.length;
			el.setSelectionRange(len, len);
		} catch (e) { }
	}

	function isValidPhoneByDigits(digits) {
		digits = onlyDigits(digits);

		// 0으로 시작하는 번호만 허용(한국 번호 전제)
		// 필요 없으시면 이 조건도 제거 가능합니다.
		if (!digits.startsWith("0")) return false;

		// 9~11자리 허용
		return digits.length >= 9 && digits.length <= 11;
	}

	if (onsiteContactInput) {
		onsiteContactInput.addEventListener("input", function () {
			const digits = onlyDigits(onsiteContactInput.value);
			onsiteContactInput.value = formatKoreanPhone(digits);
			setCaretToEnd(onsiteContactInput);
		});
	}

	// =========================================================
	// 2) AS 신청 증상 - 2단계 셀렉트
	//    - 1차: 카테고리(subjectCategory)
	//    - 2차: 증상(subject, name="subject")
	//    - 최종 value: "상부장 - 도어 파손"
	// =========================================================
	const subjectCategorySelect = document.getElementById("subjectCategory");
	const subjectSelect = document.getElementById("subject");

	// ✅ 증상 데이터(중복 제거 반영)
	const SUBJECT_MAP = {
		"상부장": [
			"도어 파손",
			"도어 스크레치",
			"도어 휘어짐",
			"도어 변색",
			"도어 단차 불량",
			"도어 마감 불량",
			"손잡이 불량",
			"바디 변색",
			"바디 스크래치",
			"바디 파손",
			"개폐 불량",
			"경첩 불량",
			"LED 점등 불량",
			"오출고",
			"기타 사유"
		],
		"슬라이드장": [
			"도어 파손",
			"도어 스크레치",
			"도어 변색",
			"도어 간격 불량",
			"바디 변색",
			"바디 스크레치",
			"바디 파손",
			"개폐불량",
			"댐퍼불량",
			"손잡이 불량",
			"LED 점등 불량",
			"오출고",
			"기타 사유"
		],
		"플랩장": [
			"도어 파손",
			"도어 스크레치",
			"도어 변색",
			"도어 단차 불량",
			"유압 불량",
			"바디 변색",
			"바디 스크래치",
			"바디 파손",
			"개폐 불량",
			"경첩 불량",
			"LED 점등 불량",
			"오출고",
			"기타 사유"
		],
		"하부장": [
			"도어 단차 불량",
			"서랍 개폐불량",
			"도어 마감 불량",
			"오출고",
			"기타 사유"
		],
		"거울": [
			"테두리 도장 불량",
			"유리 스크레치",
			"유리 파손",
			"유리 변색",
			"LED 점등 불량",
			"오출고",
			"기타 사유"
		]
	};

	function resetSubjectSelect() {
		if (!subjectSelect) return;
		subjectSelect.innerHTML = '';
		const opt = document.createElement('option');
		opt.value = '';
		opt.textContent = '== 증상 선택 ==';
		opt.selected = true;
		subjectSelect.appendChild(opt);

		subjectSelect.disabled = true;
	}

	function fillSubjectSelect(category) {
		resetSubjectSelect();

		if (!category || !SUBJECT_MAP[category] || !subjectSelect) return;

		// 중복 제거(혹시 데이터가 수정되며 중복이 들어가도 방어)
		const unique = Array.from(new Set(SUBJECT_MAP[category]));

		unique.forEach((symptom) => {
			const opt = document.createElement('option');
			// ✅ 서버로 넘어가는 값
			opt.value = `${category} - ${symptom}`;
			opt.textContent = symptom;
			subjectSelect.appendChild(opt);
		});

		subjectSelect.disabled = false;
	}

	if (subjectCategorySelect && subjectSelect) {
		resetSubjectSelect();

		subjectCategorySelect.addEventListener('change', function () {
			const category = subjectCategorySelect.value;
			fillSubjectSelect(category);
		});
	}

	// =========================================================
	// 3) 이미지 업로드 - 미리보기/삭제 + 모바일 카메라 옵션
	// =========================================================
	const fileInput = document.getElementById("file-upload");
	const uploadButton = document.getElementById("custom-upload-button");
	const previewList = document.getElementById("preview-list");

	const isMobile = /Mobi|Android|iPhone|iPad|iPod/i.test(navigator.userAgent);

	if (uploadButton && fileInput) {
		uploadButton.addEventListener("click", function () {
			if (!isMobile) {
				fileInput.removeAttribute("capture");
				fileInput.click();
			} else {
				const useCamera = confirm("사진을 촬영하시겠습니까?\n취소를 누르면 갤러리에서 선택합니다.");
				if (useCamera) {
					fileInput.setAttribute("capture", "environment");
				} else {
					fileInput.removeAttribute("capture");
				}
				fileInput.click();
			}
		});
	}

	if (fileInput && previewList) {
		fileInput.addEventListener("change", function () {
			previewList.innerHTML = "";

			Array.from(fileInput.files).forEach((file, index) => {
				const reader = new FileReader();
				reader.onload = function (e) {
					const previewItem = document.createElement("div");
					previewItem.className = "preview-item";
					previewItem.dataset.index = String(index);

					const img = document.createElement("img");
					img.src = e.target.result;

					const removeBtn = document.createElement("button");
					removeBtn.className = "remove-btn";
					removeBtn.type = "button";
					removeBtn.textContent = "×";

					removeBtn.addEventListener("click", function () {
						removeImage(index);
					});

					previewItem.appendChild(img);
					previewItem.appendChild(removeBtn);
					previewList.appendChild(previewItem);
				};
				reader.readAsDataURL(file);
			});
		});
	}

	function removeImage(indexToRemove) {
		if (!fileInput) return;
		const dt = new DataTransfer();
		Array.from(fileInput.files).forEach((file, index) => {
			if (index !== indexToRemove) dt.items.add(file);
		});
		fileInput.files = dt.files;
		fileInput.dispatchEvent(new Event("change"));
	}

	// =========================================================
	// 4) 회원 정보와 동일 체크박스 + 주소검색
	// =========================================================
	const checkbox = document.getElementById("same-address");
	const searchAddressBtn = document.getElementById("searchAddressBtn");

	const roadAddressInput = document.getElementById("searchAddress");
	const detailAddressInput = document.getElementById("detailAddress");
	const doNameInput = document.getElementById("doName");
	const siNameInput = document.getElementById("siName");
	const guNameInput = document.getElementById("guName");
	const zipCodeInput = document.getElementById("zipCode");

	if (checkbox && searchAddressBtn && roadAddressInput && detailAddressInput && doNameInput && siNameInput && guNameInput && zipCodeInput) {
		checkbox.addEventListener("change", function () {
			const useCompany = checkbox.checked;

			if (useCompany) {
				roadAddressInput.value = companyAddress.main || "";
				detailAddressInput.value = companyAddress.detail || "";
				doNameInput.value = companyAddress.doName || "";
				siNameInput.value = companyAddress.siName || "";
				guNameInput.value = companyAddress.guName || "";
				zipCodeInput.value = companyAddress.zipCode || "";

				searchAddressBtn.disabled = true;
			} else {
				roadAddressInput.value = "";
				detailAddressInput.value = "";
				doNameInput.value = "";
				siNameInput.value = "";
				guNameInput.value = "";
				zipCodeInput.value = "";

				searchAddressBtn.disabled = false;
			}
		});
	}

	if (searchAddressBtn) {
		searchAddressBtn.addEventListener("click", function () {
			new daum.Postcode({
				oncomplete: function (data) {
					const fullRoadAddr = data.roadAddress;
					const zonecode = data.zonecode;

					const addrParts = fullRoadAddr.split(" ");
					const doName = addrParts[0] || "";
					let siName = "";
					let guName = "";

					if (addrParts.length >= 2) {
						if (addrParts[1].endsWith("시") || addrParts[1].endsWith("군")) {
							siName = addrParts[1];
							guName = addrParts[2] || "";
						} else {
							siName = "";
							guName = addrParts[1] || "";
						}
					}

					if (roadAddressInput) roadAddressInput.value = fullRoadAddr;
					if (zipCodeInput) zipCodeInput.value = zonecode;
					if (doNameInput) doNameInput.value = doName;
					if (siNameInput) siNameInput.value = siName;
					if (guNameInput) guNameInput.value = guName;

					// 체크 상태면 회원 주소로 유지
					if (checkbox && checkbox.checked) {
						if (roadAddressInput) roadAddressInput.value = companyAddress.main || "";
						if (detailAddressInput) detailAddressInput.value = companyAddress.detail || "";
						if (doNameInput) doNameInput.value = companyAddress.doName || "";
						if (siNameInput) siNameInput.value = companyAddress.siName || "";
						if (guNameInput) guNameInput.value = companyAddress.guName || "";
						if (zipCodeInput) zipCodeInput.value = companyAddress.zipCode || "";
					}
				}
			}).open();
		});
	}

	// =========================================================
	// 5) 제출 처리 - 연락처(9~11자리) 검증 + subject(2단계) 검증
	// =========================================================
	form.addEventListener("submit", function (e) {
		e.preventDefault();

		// 연락처 검증(010 제한 제거)
		if (onsiteContactInput) {
			const digits = onlyDigits(onsiteContactInput.value);
			onsiteContactInput.value = formatKoreanPhone(digits);

			if (!isValidPhoneByDigits(digits)) {
				alert("현장연락처를 '- 없이 숫자만' 입력해 주세요.\n예) 0311234567 / 01012345678");
				onsiteContactInput.focus();
				return;
			}
		}

		// subject(증상) 필수 검증
		if (subjectSelect) {
			const v = String(subjectSelect.value || "").trim();
			if (!v) {
				alert("AS 신청 증상을 선택해 주세요.");
				if (subjectCategorySelect) subjectCategorySelect.focus();
				return;
			}
		}

		const formData = new FormData(form);

		fetch("/customer/asSubmit", {
			method: "POST",
			body: formData
		})
			.then(res => res.json())
			.then(data => {
				alert(data.message);
				if (data.success) {
					window.location.href = data.redirectUrl;
				}
			})
			.catch(err => {
				alert("서버 오류가 발생했습니다.");
				console.error(err);
			});
	});
});