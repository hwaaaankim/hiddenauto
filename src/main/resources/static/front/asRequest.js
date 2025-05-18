document.addEventListener("DOMContentLoaded", function() {
	const form = document.querySelector("form");
	form.addEventListener("submit", function(e) {
		e.preventDefault();

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