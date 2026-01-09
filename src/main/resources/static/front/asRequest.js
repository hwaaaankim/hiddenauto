document.addEventListener("DOMContentLoaded", function() {
	const form = document.querySelector("form");

	// =========================================================
	// ✅ 현장 연락처(onsiteContact) 숫자만 허용 + 자동 하이픈 포맷
	// =========================================================
	const onsiteContactInput = document.getElementById("onsiteContact");

	// 숫자만 추출
	function onlyDigits(v) {
		return String(v || "").replace(/\D/g, "");
	}

	/**
	 * 휴대폰 번호 포맷
	 * - 입력은 숫자만 받되, 화면 표시/전송은 010-1234-5678 형태로 맞춥니다.
	 * - 기본적으로 010xxxxxxxx(11자리)을 정상 케이스로 보며,
	 *   010xxxxxxx(10자리)도 010-123-4567 형태로 자연스럽게 표시됩니다.
	 */
	function formatKoreanMobile(digits) {
		digits = onlyDigits(digits);

		// 010으로 시작하지 않는 경우도 입력은 허용하되, 제출 검증에서 걸러냅니다.
		// 입력 단계에서는 최대 11자리까지만 유지
		if (digits.length > 11) digits = digits.slice(0, 11);

		// 길이에 따라 하이픈 위치 결정
		if (digits.length <= 3) {
			return digits;
		}
		if (digits.length <= 7) {
			// 010-1234 (또는 010-123)
			return digits.slice(0, 3) + "-" + digits.slice(3);
		}
		if (digits.length === 10) {
			// 010-123-4567
			return digits.slice(0, 3) + "-" + digits.slice(3, 6) + "-" + digits.slice(6);
		}
		// 11자리(정상): 010-1234-5678
		return digits.slice(0, 3) + "-" + digits.slice(3, 7) + "-" + digits.slice(7);
	}

	// 커서 위치 보정(사용성 개선)
	function setCaretToEnd(el) {
		try {
			const len = el.value.length;
			el.setSelectionRange(len, len);
		} catch (e) {
			// 일부 환경에서 setSelectionRange가 실패할 수 있어 무시
		}
	}

	// 입력 중 숫자만 + 자동 포맷
	onsiteContactInput.addEventListener("input", function() {
		const digits = onlyDigits(onsiteContactInput.value);
		onsiteContactInput.value = formatKoreanMobile(digits);
		setCaretToEnd(onsiteContactInput);
	});

	// 붙여넣기 시에도 숫자만 반영
	onsiteContactInput.addEventListener("paste", function() {
		// paste 직후 input 이벤트가 다시 발생하므로 여기서는 별도 처리 불필요
	});

	// 제출 직전 최종 검증
	function isValidMobileFormatted(v) {
		// 최종적으로는 010-1234-5678 또는 010-123-4567 형태를 허용
		// (원하시면 11자리만 강제도 가능합니다)
		const s = String(v || "").trim();
		const r11 = /^010-\d{4}-\d{4}$/;
		const r10 = /^010-\d{3}-\d{4}$/;
		return r11.test(s) || r10.test(s);
	}

	form.addEventListener("submit", function(e) {
		e.preventDefault();

		// 연락처 강제 포맷/정리 후 검증
		const formatted = formatKoreanMobile(onsiteContactInput.value);
		onsiteContactInput.value = formatted;

		if (!isValidMobileFormatted(formatted)) {
			alert("현장연락처를 '- 없이 숫자만' 입력해 주세요.\n예) 01012345678");
			onsiteContactInput.focus();
			return;
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

	const fileInput = document.getElementById("file-upload");
	const uploadButton = document.getElementById("custom-upload-button");
	const previewList = document.getElementById("preview-list");

	const isMobile = /Mobi|Android|iPhone|iPad|iPod/i.test(navigator.userAgent);

	uploadButton.addEventListener("click", function() {
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

	// 이미지 미리보기 및 삭제 기능
	fileInput.addEventListener("change", function() {
		previewList.innerHTML = ""; // 기존 미리보기 초기화

		Array.from(fileInput.files).forEach((file, index) => {
			const reader = new FileReader();
			reader.onload = function(e) {
				const previewItem = document.createElement("div");
				previewItem.className = "preview-item";
				previewItem.dataset.index = index;

				const img = document.createElement("img");
				img.src = e.target.result;

				const removeBtn = document.createElement("button");
				removeBtn.className = "remove-btn";
				removeBtn.textContent = "×";

				removeBtn.addEventListener("click", function() {
					removeImage(index);
				});

				previewItem.appendChild(img);
				previewItem.appendChild(removeBtn);
				previewList.appendChild(previewItem);
			};
			reader.readAsDataURL(file);
		});
	});

	// FileList를 조작하는 건 불가능하므로 trick: 새 FileList 생성
	function removeImage(indexToRemove) {
		const dt = new DataTransfer();
		Array.from(fileInput.files).forEach((file, index) => {
			if (index !== indexToRemove) dt.items.add(file);
		});
		fileInput.files = dt.files;
		fileInput.dispatchEvent(new Event("change")); // 다시 미리보기 렌더링
	}

	const checkbox = document.getElementById("same-address");
	const searchAddressBtn = document.getElementById("searchAddressBtn");

	const roadAddressInput = document.getElementById("searchAddress");
	const detailAddressInput = document.getElementById("detailAddress");
	const doNameInput = document.getElementById("doName");
	const siNameInput = document.getElementById("siName");
	const guNameInput = document.getElementById("guName");
	const zipCodeInput = document.getElementById("zipCode");

	// 체크박스 상태 변화 처리
	checkbox.addEventListener("change", function() {
		const useCompany = checkbox.checked;

		if (useCompany) {
			roadAddressInput.value = companyAddress.main || "";
			detailAddressInput.value = companyAddress.detail || "";
			doNameInput.value = companyAddress.doName || "";
			siNameInput.value = companyAddress.siName || "";
			guNameInput.value = companyAddress.guName || "";
			zipCodeInput.value = companyAddress.zipCode || "";

			searchAddressBtn.disabled = true; // 주소검색 비활성화
		} else {
			roadAddressInput.value = "";
			detailAddressInput.value = "";
			doNameInput.value = "";
			siNameInput.value = "";
			guNameInput.value = "";
			zipCodeInput.value = "";

			searchAddressBtn.disabled = false; // 주소검색 재활성화
		}
	});

	// 주소검색 버튼 클릭
	searchAddressBtn.addEventListener("click", function() {
		new daum.Postcode({
			oncomplete: function(data) {
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

				// 주소 입력
				roadAddressInput.value = fullRoadAddr;
				zipCodeInput.value = zonecode;
				doNameInput.value = doName;
				siNameInput.value = siName;
				guNameInput.value = guName;

				// 체크 상태일 경우에도 다시 회원 주소로 덮어씀
				if (checkbox.checked) {
					roadAddressInput.value = companyAddress.main || "";
					detailAddressInput.value = companyAddress.detail || "";
					doNameInput.value = companyAddress.doName || "";
					siNameInput.value = companyAddress.siName || "";
					guNameInput.value = companyAddress.guName || "";
					zipCodeInput.value = companyAddress.zipCode || "";
				}
			}
		}).open();
	});
});
