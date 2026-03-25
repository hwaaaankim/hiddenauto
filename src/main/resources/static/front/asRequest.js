document.addEventListener("DOMContentLoaded", function() {
	'use strict';

	const MAX_SECTION_COUNT = 3;

	const form = document.getElementById("as-request-fifth-form");
	if (!form) return;

	const productSectionsWrap = document.getElementById("as-request-fifth-productSections");
	const productTemplate = document.getElementById("as-request-fifth-productTemplate");
	const addBtnTop = document.getElementById("as-request-fifth-addProductBtnTop");
	const addBtnBottom = document.getElementById("as-request-fifth-addProductBtnBottom");
	const submitBtn = document.getElementById("as-request-fifth-submitBtn");

	const progressCard = document.getElementById("as-request-fifth-progressCard");
	const progressText = document.getElementById("as-request-fifth-progressText");
	const progressBar = document.getElementById("as-request-fifth-progressBar");

	const sameAddressCheckbox = document.getElementById("as-request-fifth-same-address");
	const sameMemberInfoCheckbox = document.getElementById("as-request-fifth-same-member-info");

	const customerNameInput = document.getElementById("as-request-fifth-customerName");
	const roadAddressInput = document.getElementById("as-request-fifth-roadAddress");
	const detailAddressInput = document.getElementById("as-request-fifth-detailAddress");
	const doNameInput = document.getElementById("as-request-fifth-doName");
	const siNameInput = document.getElementById("as-request-fifth-siName");
	const guNameInput = document.getElementById("as-request-fifth-guName");
	const zipCodeInput = document.getElementById("as-request-fifth-zipCode");
	const onsiteContactInput = document.getElementById("as-request-fifth-onsiteContact");

	const applicantNameInput = document.getElementById("as-request-fifth-applicantName");
	const applicantPhoneInput = document.getElementById("as-request-fifth-applicantPhone");
	const applicantEmailInput = document.getElementById("as-request-fifth-applicantEmail");

	const searchAddressBtn = document.getElementById("as-request-fifth-searchAddressBtn");

	const companyAddress = window.companyAddress || {};
	const loginMemberInfo = window.loginMemberInfo || {};

	const SUBJECT_MAP = {
		"상부장": [
			"도어 파손", "도어 스크레치", "도어 휘어짐", "도어 변색", "도어 단차 불량",
			"도어 마감 불량", "손잡이 불량", "바디 변색", "바디 스크래치", "바디 파손",
			"개폐 불량", "경첩 불량", "LED 점등 불량", "오출고", "기타 사유"
		],
		"슬라이드장": [
			"도어 파손", "도어 스크레치", "도어 변색", "도어 간격 불량", "바디 변색",
			"바디 스크레치", "바디 파손", "개폐불량", "댐퍼불량", "손잡이 불량",
			"LED 점등 불량", "오출고", "기타 사유"
		],
		"플랩장": [
			"도어 파손", "도어 스크레치", "도어 변색", "도어 단차 불량", "유압 불량",
			"바디 변색", "바디 스크래치", "바디 파손", "개폐 불량", "경첩 불량",
			"LED 점등 불량", "오출고", "기타 사유"
		],
		"하부장": [
			"도어 단차 불량", "서랍 개폐불량", "도어 마감 불량", "오출고", "기타 사유"
		],
		"거울": [
			"테두리 도장 불량", "유리 스크레치", "유리 파손", "유리 변색",
			"LED 점등 불량", "오출고", "기타 사유"
		]
	};

	function safeTrim(value) {
		return String(value || "").trim();
	}

	function onlyDigits(value) {
		return String(value || "").replace(/\D/g, "");
	}

	function formatKoreanPhone(digits) {
		digits = onlyDigits(digits);

		if (digits.length > 11) digits = digits.slice(0, 11);
		if (digits.length <= 3) return digits;

		if (digits.startsWith("02")) {
			if (digits.length <= 5) return digits.slice(0, 2) + "-" + digits.slice(2);
			if (digits.length === 9) return digits.slice(0, 2) + "-" + digits.slice(2, 5) + "-" + digits.slice(5);
			if (digits.length >= 10) return digits.slice(0, 2) + "-" + digits.slice(2, 6) + "-" + digits.slice(6, 10);
		}

		if (digits.length === 10) {
			return digits.slice(0, 3) + "-" + digits.slice(3, 6) + "-" + digits.slice(6);
		}

		if (digits.length >= 11) {
			return digits.slice(0, 3) + "-" + digits.slice(3, 7) + "-" + digits.slice(7, 11);
		}

		return digits.slice(0, 3) + "-" + digits.slice(3);
	}

	function isValidPhoneByDigits(digits) {
		digits = onlyDigits(digits);
		return digits.startsWith("0") && digits.length >= 9 && digits.length <= 11;
	}

	function isValidEmail(value) {
		const email = safeTrim(value);
		if (!email) return true;
		return /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$/.test(email);
	}

	function bindPhoneFormatter(input) {
		if (!input) return;

		input.addEventListener("input", function() {
			const digits = onlyDigits(input.value);
			input.value = formatKoreanPhone(digits);
		});
	}

	bindPhoneFormatter(onsiteContactInput);
	bindPhoneFormatter(applicantPhoneInput);

	function getAllSections() {
		return Array.from(productSectionsWrap.querySelectorAll(".as-request-fifth-product-section"));
	}

	function resetSubjectSelect(subjectSelect) {
		if (!subjectSelect) return;
		subjectSelect.innerHTML = "";
		const opt = document.createElement("option");
		opt.value = "";
		opt.textContent = "== 증상 선택 ==";
		opt.selected = true;
		subjectSelect.appendChild(opt);
		subjectSelect.disabled = true;
	}

	function fillSubjectSelect(category, subjectSelect) {
		resetSubjectSelect(subjectSelect);
		if (!category || !SUBJECT_MAP[category] || !subjectSelect) return;

		const unique = Array.from(new Set(SUBJECT_MAP[category]));
		unique.forEach(function(symptom) {
			const opt = document.createElement("option");
			opt.value = category + " - " + symptom;
			opt.textContent = symptom;
			subjectSelect.appendChild(opt);
		});

		subjectSelect.disabled = false;
	}

	function getFileExtension(filename) {
		const name = String(filename || "");
		const idx = name.lastIndexOf(".");
		if (idx < 0 || idx === name.length - 1) return "";
		return name.substring(idx + 1).toLowerCase();
	}

	function isImageFile(file) {
		const type = String(file.type || "").toLowerCase();
		if (type.startsWith("image/")) return true;

		const ext = getFileExtension(file.name);
		return ["jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif"].includes(ext);
	}

	function isVideoFile(file) {
		const type = String(file.type || "").toLowerCase();
		if (type.startsWith("video/")) return true;

		const ext = getFileExtension(file.name);
		return ["mp4", "mov", "avi", "m4v", "wmv", "webm", "mkv"].includes(ext);
	}

	function isSupportedAttachment(file) {
		return isImageFile(file) || isVideoFile(file);
	}

	function renderAttachmentPreview(section) {
		const fileInput = section.querySelector(".as-request-fifth-attachments-input");
		const previewList = section.querySelector(".as-request-fifth-preview-list");

		if (!fileInput || !previewList) return;

		previewList.innerHTML = "";

		Array.from(fileInput.files || []).forEach(function(file, index) {
			const item = document.createElement("div");
			item.className = "as-request-fifth-preview-item";

			if (isImageFile(file)) {
				const img = document.createElement("img");
				img.src = URL.createObjectURL(file);
				item.appendChild(img);
			} else if (isVideoFile(file)) {
				const video = document.createElement("video");
				video.src = URL.createObjectURL(file);
				video.controls = true;
				video.muted = true;
				video.playsInline = true;
				video.preload = "metadata";
				item.appendChild(video);
			} else {
				const fileBox = document.createElement("div");
				fileBox.className = "as-request-fifth-preview-file";
				fileBox.innerHTML = "<div>첨부파일</div><div>" + file.name + "</div>";
				item.appendChild(fileBox);
			}

			const removeBtn = document.createElement("button");
			removeBtn.type = "button";
			removeBtn.className = "as-request-fifth-remove-attachment-btn";
			removeBtn.textContent = "×";

			removeBtn.addEventListener("click", function() {
				const dt = new DataTransfer();
				Array.from(fileInput.files || []).forEach(function(targetFile, targetIndex) {
					if (targetIndex !== index) {
						dt.items.add(targetFile);
					}
				});
				fileInput.files = dt.files;
				renderAttachmentPreview(section);
			});

			item.appendChild(removeBtn);
			previewList.appendChild(item);
		});
	}

	function bindSection(section) {
		const categorySelect = section.querySelector(".as-request-fifth-subjectCategory");
		const subjectSelect = section.querySelector(".as-request-fifth-subject");
		const fileInput = section.querySelector(".as-request-fifth-attachments-input");
		const triggerBtn = section.querySelector(".as-request-fifth-attachment-trigger");
		const removeBtn = section.querySelector(".as-request-fifth-remove-section-btn");

		if (categorySelect && subjectSelect) {
			resetSubjectSelect(subjectSelect);

			categorySelect.addEventListener("change", function() {
				fillSubjectSelect(categorySelect.value, subjectSelect);
			});
		}

		if (triggerBtn && fileInput) {
			triggerBtn.addEventListener("click", function() {
				fileInput.click();
			});

			fileInput.addEventListener("change", function() {
				const invalid = Array.from(fileInput.files || []).find(function(file) {
					return !isSupportedAttachment(file);
				});

				if (invalid) {
					alert("지원하지 않는 파일 형식이 포함되어 있습니다.\n이미지 또는 동영상만 업로드해 주세요.");
					fileInput.value = "";
					renderAttachmentPreview(section);
					return;
				}

				renderAttachmentPreview(section);
			});
		}

		if (removeBtn) {
			removeBtn.addEventListener("click", function() {
				removeSection(section);
			});
		}
	}

	function updateSectionIndexes() {
		const sections = getAllSections();

		sections.forEach(function(section, index) {
			const displayIndex = index + 1;
			section.dataset.sectionIndex = String(displayIndex);

			const title = section.querySelector(".as-request-fifth-product-title-text");
			if (title) {
				title.textContent = "제품 신청 " + displayIndex;
			}

			const radioItems = section.querySelectorAll(".as-request-fifth-billingTarget");
			radioItems.forEach(function(radio) {
				radio.name = "as-request-fifth-billingTarget-" + displayIndex;
			});

			const removeBtn = section.querySelector(".as-request-fifth-remove-section-btn");
			if (removeBtn) {
				removeBtn.style.display = displayIndex === 1 ? "none" : "";
			}
		});

		const disableAdd = sections.length >= MAX_SECTION_COUNT;
		if (addBtnTop) addBtnTop.disabled = disableAdd;
		if (addBtnBottom) addBtnBottom.disabled = disableAdd;
	}

	function addSection() {
		const currentCount = getAllSections().length;
		if (currentCount >= MAX_SECTION_COUNT) {
			alert("제품 신청은 최대 3개까지 추가할 수 있습니다.");
			return;
		}

		const cloned = productTemplate.content.firstElementChild.cloneNode(true);
		productSectionsWrap.appendChild(cloned);
		bindSection(cloned);
		updateSectionIndexes();

		requestAnimationFrame(function() {
			cloned.classList.add("as-request-fifth-mounted");
			cloned.scrollIntoView({ behavior: "smooth", block: "start" });
		});
	}

	function removeSection(section) {
		if (!section) return;

		section.classList.remove("as-request-fifth-mounted");

		setTimeout(function() {
			section.remove();
			updateSectionIndexes();
		}, 320);
	}

	function applyCompanyAddress() {
		roadAddressInput.value = companyAddress.main || "";
		detailAddressInput.value = companyAddress.detail || "";
		doNameInput.value = companyAddress.doName || "";
		siNameInput.value = companyAddress.siName || "";
		guNameInput.value = companyAddress.guName || "";
		zipCodeInput.value = companyAddress.zipCode || "";
		searchAddressBtn.disabled = true;
	}

	function clearCompanyAddress() {
		roadAddressInput.value = "";
		detailAddressInput.value = "";
		doNameInput.value = "";
		siNameInput.value = "";
		guNameInput.value = "";
		zipCodeInput.value = "";
		searchAddressBtn.disabled = false;
	}

	function applyMemberInfo() {
		applicantNameInput.value = loginMemberInfo.name || "";
		applicantPhoneInput.value = formatKoreanPhone(loginMemberInfo.phone || "");
		applicantEmailInput.value = loginMemberInfo.email || "";
	}

	function clearMemberInfo() {
		applicantNameInput.value = "";
		applicantPhoneInput.value = "";
		applicantEmailInput.value = "";
	}

	if (sameAddressCheckbox) {
		sameAddressCheckbox.addEventListener("change", function() {
			if (sameAddressCheckbox.checked) {
				applyCompanyAddress();
			} else {
				clearCompanyAddress();
			}
		});
	}

	if (sameMemberInfoCheckbox) {
		sameMemberInfoCheckbox.addEventListener("change", function() {
			if (sameMemberInfoCheckbox.checked) {
				applyMemberInfo();
			} else {
				clearMemberInfo();
			}
		});
	}

	if (searchAddressBtn) {
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

					roadAddressInput.value = fullRoadAddr || "";
					zipCodeInput.value = zonecode || "";
					doNameInput.value = doName || "";
					siNameInput.value = siName || "";
					guNameInput.value = guName || "";
				}
			}).open();
		});
	}

	function validateCommonFields() {
		customerNameInput.value = safeTrim(customerNameInput.value);
		detailAddressInput.value = safeTrim(detailAddressInput.value);

		const onsiteDigits = onlyDigits(onsiteContactInput.value);
		onsiteContactInput.value = formatKoreanPhone(onsiteDigits);

		const applicantDigits = onlyDigits(applicantPhoneInput.value);
		applicantPhoneInput.value = formatKoreanPhone(applicantDigits);

		if (!safeTrim(customerNameInput.value)) {
			alert("고객 성함을 입력해 주세요.");
			customerNameInput.focus();
			return false;
		}

		if (!safeTrim(roadAddressInput.value)) {
			alert("주소를 입력해 주세요.");
			roadAddressInput.focus();
			return false;
		}

		if (!isValidPhoneByDigits(onsiteDigits)) {
			alert("현장 연락처 형식이 올바르지 않습니다.");
			onsiteContactInput.focus();
			return false;
		}

		if (!safeTrim(applicantNameInput.value)) {
			alert("신청 담당자 이름을 입력해 주세요.");
			applicantNameInput.focus();
			return false;
		}

		if (!isValidPhoneByDigits(applicantDigits)) {
			alert("신청 담당자 연락처 형식이 올바르지 않습니다.");
			applicantPhoneInput.focus();
			return false;
		}

		if (!isValidEmail(applicantEmailInput.value)) {
			alert("신청 담당자 이메일 형식이 올바르지 않습니다.");
			applicantEmailInput.focus();
			return false;
		}

		return true;
	}

	function validateSection(section) {
		const productName = section.querySelector(".as-request-fifth-productName");
		const productSize = section.querySelector(".as-request-fifth-productSize");
		const productColor = section.querySelector(".as-request-fifth-productColor");
		const productOptions = section.querySelector(".as-request-fifth-productOptions");
		const subject = section.querySelector(".as-request-fifth-subject");
		const fileInput = section.querySelector(".as-request-fifth-attachments-input");
		const checkedBilling = section.querySelector(".as-request-fifth-billingTarget:checked");

		productName.value = safeTrim(productName.value);
		productSize.value = safeTrim(productSize.value);
		productColor.value = safeTrim(productColor.value);
		productOptions.value = safeTrim(productOptions.value);

		if (!productName.value) {
			alert("제품명을 입력해 주세요.");
			productName.focus();
			return false;
		}

		if (!productSize.value) {
			alert("제품 사이즈를 입력해 주세요.");
			productSize.focus();
			return false;
		}

		if (!productColor.value) {
			alert("제품 색상을 입력해 주세요.");
			productColor.focus();
			return false;
		}

		if (!productOptions.value) {
			alert("제품 옵션을 입력해 주세요.");
			productOptions.focus();
			return false;
		}

		if (!checkedBilling) {
			alert("비용 청구 주체를 선택해 주세요.");
			return false;
		}

		if (!safeTrim(subject.value)) {
			alert("AS 증상을 선택해 주세요.");
			const category = section.querySelector(".as-request-fifth-subjectCategory");
			if (category) category.focus();
			return false;
		}

		const files = Array.from(fileInput.files || []);
		if (!files.length) {
			alert("사진 또는 동영상을 1개 이상 첨부해 주세요.");
			return false;
		}

		const invalid = files.find(function(file) {
			return !isSupportedAttachment(file);
		});

		if (invalid) {
			alert("지원하지 않는 첨부파일 형식이 포함되어 있습니다.");
			return false;
		}

		return true;
	}

	function buildCommonValues() {
		return {
			customerName: safeTrim(customerNameInput.value),
			roadAddress: safeTrim(roadAddressInput.value),
			detailAddress: safeTrim(detailAddressInput.value),
			doName: safeTrim(doNameInput.value),
			siName: safeTrim(siNameInput.value),
			guName: safeTrim(guNameInput.value),
			zipCode: safeTrim(zipCodeInput.value),
			onsiteContact: safeTrim(onsiteContactInput.value),
			applicantName: safeTrim(applicantNameInput.value),
			applicantPhone: safeTrim(applicantPhoneInput.value),
			applicantEmail: safeTrim(applicantEmailInput.value)
		};
	}

	function buildSectionFormData(section, commonValues) {
		const formData = new FormData();

		Object.keys(commonValues).forEach(function(key) {
			formData.append(key, commonValues[key] || "");
		});

		const purchaseDate = section.querySelector(".as-request-fifth-purchaseDate").value || "";
		const checkedBilling = section.querySelector(".as-request-fifth-billingTarget:checked");
		const productName = safeTrim(section.querySelector(".as-request-fifth-productName").value);
		const productSize = safeTrim(section.querySelector(".as-request-fifth-productSize").value);
		const productColor = safeTrim(section.querySelector(".as-request-fifth-productColor").value);
		const productOptions = safeTrim(section.querySelector(".as-request-fifth-productOptions").value);
		const subject = safeTrim(section.querySelector(".as-request-fifth-subject").value);
		const reason = safeTrim(section.querySelector(".as-request-fifth-reason").value);
		const fileInput = section.querySelector(".as-request-fifth-attachments-input");

		formData.append("purchaseDate", purchaseDate);
		formData.append("billingTarget", checkedBilling ? checkedBilling.value : "");
		formData.append("productName", productName);
		formData.append("productSize", productSize);
		formData.append("productColor", productColor);
		formData.append("productOptions", productOptions);
		formData.append("subject", subject);
		formData.append("reason", reason);

		Array.from(fileInput.files || []).forEach(function(file) {
			formData.append("attachments", file);
		});

		return formData;
	}

	function getSectionBytes(section) {
		const fileInput = section.querySelector(".as-request-fifth-attachments-input");
		return Array.from(fileInput.files || []).reduce(function(sum, file) {
			return sum + (file.size || 0);
		}, 0);
	}

	function showProgress() {
		progressCard.classList.add("active");
		progressText.textContent = "업로드를 시작합니다.";
		progressBar.style.width = "0%";
	}

	function setProgress(percent, text) {
		progressBar.style.width = percent + "%";
		progressText.textContent = text;
	}

	function toggleSubmitting(disabled) {
		form.querySelectorAll("input, textarea, select, button").forEach(function(el) {
			el.disabled = disabled;
		});

		if (!disabled) {
			updateSectionIndexes();
		}
	}

	function uploadSingleSection(formData, sectionOrder, totalSections, uploadedBaseBytes, currentSectionBytes, totalBytes) {
		return new Promise(function(resolve, reject) {
			const xhr = new XMLHttpRequest();
			xhr.open("POST", "/customer/asSubmit");
			xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");

			xhr.upload.addEventListener("progress", function(e) {
				let percent = 0;

				if (e.lengthComputable && totalBytes > 0) {
					percent = Math.round(((uploadedBaseBytes + e.loaded) / totalBytes) * 100);
				} else {
					const roughCompleted = ((sectionOrder - 1) / totalSections) * 100;
					percent = Math.round(roughCompleted);
				}

				if (percent > 99) percent = 99;

				setProgress(percent, sectionOrder + " / " + totalSections + "번째 제품 신청 업로드 중...");
			});

			xhr.onload = function() {
				let data = null;

				try {
					data = JSON.parse(xhr.responseText);
				} catch (e) {
					data = null;
				}

				if (xhr.status >= 200 && xhr.status < 300 && data && data.success) {
					const completedPercent = Math.round(((uploadedBaseBytes + currentSectionBytes) / totalBytes) * 100);
					setProgress(Math.min(completedPercent, 100), sectionOrder + " / " + totalSections + "번째 제품 접수 완료");
					resolve(data);
				} else {
					reject(new Error((data && data.message) ? data.message : (sectionOrder + "번째 제품 신청 중 오류가 발생했습니다.")));
				}
			};

			xhr.onerror = function() {
				reject(new Error(sectionOrder + "번째 제품 신청 중 네트워크 오류가 발생했습니다."));
			};

			xhr.send(formData);
		});
	}

	async function submitAllSections() {
		if (!validateCommonFields()) return;

		const sections = getAllSections();
		for (let i = 0; i < sections.length; i++) {
			if (!validateSection(sections[i])) {
				return;
			}
		}

		const commonValues = buildCommonValues();
		const totalBytes = Math.max(sections.reduce(function(sum, section) {
			return sum + getSectionBytes(section);
		}, 0), 1);

		toggleSubmitting(true);
		showProgress();

		let uploadedBaseBytes = 0;
		let lastRedirectUrl = "/customer/asList";

		try {
			for (let i = 0; i < sections.length; i++) {
				const section = sections[i];
				const sectionBytes = getSectionBytes(section);
				const formData = buildSectionFormData(section, commonValues);

				const result = await uploadSingleSection(
					formData,
					i + 1,
					sections.length,
					uploadedBaseBytes,
					sectionBytes,
					totalBytes
				);

				uploadedBaseBytes += sectionBytes;
				if (result && result.redirectUrl) {
					lastRedirectUrl = result.redirectUrl;
				}
			}

			setProgress(100, "모든 제품 신청이 완료되었습니다.");
			alert(sections.length + "건의 AS 신청이 정상적으로 접수되었습니다.");
			window.location.href = lastRedirectUrl;

		} catch (e) {
			alert(e.message || "AS 신청 중 오류가 발생했습니다.");
			toggleSubmitting(false);
			setProgress(0, "업로드가 중단되었습니다.");
		}
	}

	if (addBtnTop) {
		addBtnTop.addEventListener("click", addSection);
	}

	if (addBtnBottom) {
		addBtnBottom.addEventListener("click", addSection);
	}

	getAllSections().forEach(bindSection);
	updateSectionIndexes();

	form.addEventListener("submit", function(e) {
		e.preventDefault();
		submitAllSections();
	});
});