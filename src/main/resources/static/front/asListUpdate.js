document.addEventListener("DOMContentLoaded", function() {
	'use strict';

	const modalEl = document.getElementById("client-as-update-first-modal");
	const form = document.getElementById("client-as-update-first-form");
	if (!modalEl || !form || typeof bootstrap === "undefined") return;

	const modal = bootstrap.Modal.getOrCreateInstance(modalEl);

	const taskIdInput = document.getElementById("client-as-update-first-task-id");

	const customerNameInput = document.getElementById("client-as-update-first-customerName");
	const roadAddressInput = document.getElementById("client-as-update-first-roadAddress");
	const detailAddressInput = document.getElementById("client-as-update-first-detailAddress");
	const doNameInput = document.getElementById("client-as-update-first-doName");
	const siNameInput = document.getElementById("client-as-update-first-siName");
	const guNameInput = document.getElementById("client-as-update-first-guName");
	const zipCodeInput = document.getElementById("client-as-update-first-zipCode");
	const onsiteContactInput = document.getElementById("client-as-update-first-onsiteContact");

	const productNameInput = document.getElementById("client-as-update-first-productName");
	const productSizeInput = document.getElementById("client-as-update-first-productSize");
	const productColorInput = document.getElementById("client-as-update-first-productColor");
	const productOptionsInput = document.getElementById("client-as-update-first-productOptions");
	const purchaseDateInput = document.getElementById("client-as-update-first-purchaseDate");
	const billingTargetSelect = document.getElementById("client-as-update-first-billingTarget");

	const applicantNameInput = document.getElementById("client-as-update-first-applicantName");
	const applicantPhoneInput = document.getElementById("client-as-update-first-applicantPhone");
	const applicantEmailInput = document.getElementById("client-as-update-first-applicantEmail");

	const subjectCategorySelect = document.getElementById("client-as-update-first-subjectCategory");
	const subjectSelect = document.getElementById("client-as-update-first-subject");
	const reasonInput = document.getElementById("client-as-update-first-reason");

	const searchAddressBtn = document.getElementById("client-as-update-first-searchAddressBtn");

	const fileTriggerBtn = document.getElementById("client-as-update-first-fileTrigger");
	const newImagesInput = document.getElementById("client-as-update-first-newImages");

	const videoTriggerBtn = document.getElementById("client-as-update-first-videoTrigger");
	const newVideosInput = document.getElementById("client-as-update-first-newVideos");

	const existingImagesWrap = document.getElementById("client-as-update-first-existingImages");
	const deletedImagesWrap = document.getElementById("client-as-update-first-deletedImages");
	const addedImagesWrap = document.getElementById("client-as-update-first-addedImages");

	const existingVideosWrap = document.getElementById("client-as-update-first-existingVideos");
	const deletedVideosWrap = document.getElementById("client-as-update-first-deletedVideos");
	const addedVideosWrap = document.getElementById("client-as-update-first-addedVideos");

	const submitBtn = document.getElementById("client-as-update-first-submitBtn");

	const SUBJECT_MAP = {
		"상부장": ["도어 파손", "도어 스크레치", "도어 휘어짐", "도어 변색", "도어 단차 불량", "도어 마감 불량", "손잡이 불량", "바디 변색", "바디 스크래치", "바디 파손", "개폐 불량", "경첩 불량", "LED 점등 불량", "오출고", "기타 사유"],
		"슬라이드장": ["도어 파손", "도어 스크레치", "도어 변색", "도어 간격 불량", "바디 변색", "바디 스크레치", "바디 파손", "개폐불량", "댐퍼불량", "손잡이 불량", "LED 점등 불량", "오출고", "기타 사유"],
		"플랩장": ["도어 파손", "도어 스크레치", "도어 변색", "도어 단차 불량", "유압 불량", "바디 변색", "바디 스크래치", "바디 파손", "개폐 불량", "경첩 불량", "LED 점등 불량", "오출고", "기타 사유"],
		"하부장": ["도어 단차 불량", "서랍 개폐불량", "도어 마감 불량", "오출고", "기타 사유"],
		"거울": ["테두리 도장 불량", "유리 스크레치", "유리 파손", "유리 변색", "LED 점등 불량", "오출고", "기타 사유"]
	};

	const state = {
		original: null,
		existingImages: [],
		deletedImageIds: new Set(),
		newImageFiles: new DataTransfer(),

		existingVideos: [],
		deletedVideoIds: new Set(),
		newVideoFiles: new DataTransfer()
	};

	function str(v) {
		return v == null ? "" : String(v);
	}

	function trim(v) {
		return str(v).trim();
	}

	function onlyDigits(v) {
		return str(v).replace(/\D/g, "");
	}

	function escapeHtml(v) {
		return str(v)
			.replace(/&/g, "&amp;")
			.replace(/</g, "&lt;")
			.replace(/>/g, "&gt;")
			.replace(/"/g, "&quot;")
			.replace(/'/g, "&#39;");
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
		if (!digits.startsWith("0")) return false;
		return digits.length >= 9 && digits.length <= 11;
	}

	function isValidEmail(email) {
		if (!trim(email)) return true;
		return /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$/.test(trim(email));
	}

	function resetSubjectSelect() {
		subjectSelect.innerHTML = "";
		const opt = document.createElement("option");
		opt.value = "";
		opt.textContent = "== 증상 선택 ==";
		opt.selected = true;
		subjectSelect.appendChild(opt);
		subjectSelect.disabled = true;
	}

	function parseSubject(fullSubject) {
		const s = trim(fullSubject);
		if (!s) return { category: "", symptom: "", full: "" };

		const parts = s.split(" - ");
		if (parts.length >= 2) {
			return {
				category: trim(parts[0]),
				symptom: trim(parts.slice(1).join(" - ")),
				full: s
			};
		}
		return { category: "", symptom: s, full: s };
	}

	function fillSubjectSelect(category, selectedFullValue) {
		resetSubjectSelect();
		if (!category) return;

		const symptoms = Array.isArray(SUBJECT_MAP[category]) ? Array.from(new Set(SUBJECT_MAP[category])) : [];

		if (symptoms.length === 0 && selectedFullValue) {
			const fallback = document.createElement("option");
			fallback.value = selectedFullValue;
			fallback.textContent = parseSubject(selectedFullValue).symptom || selectedFullValue;
			subjectSelect.appendChild(fallback);
			subjectSelect.disabled = false;
			subjectSelect.value = selectedFullValue;
			return;
		}

		symptoms.forEach(function(symptom) {
			const opt = document.createElement("option");
			opt.value = category + " - " + symptom;
			opt.textContent = symptom;
			subjectSelect.appendChild(opt);
		});

		subjectSelect.disabled = false;
		if (selectedFullValue) subjectSelect.value = selectedFullValue;
	}

	function getOriginReason(taskId) {
		const textarea = document.querySelector('.client-as-update-first-origin-reason[data-task-id="' + taskId + '"]');
		return textarea ? textarea.value : "";
	}

	function getOriginImages(taskId) {
		const wrap = document.querySelector('.client-as-update-first-origin-images[data-task-id="' + taskId + '"]');
		if (!wrap) return [];

		return Array.from(wrap.querySelectorAll(".client-as-update-first-origin-image"))
			.map(function(el) {
				const id = Number(el.dataset.imageId);
				return {
					id: id,
					url: str(el.dataset.imageUrl),
					filename: str(el.dataset.imageFilename)
				};
			})
			.filter(function(img) {
				return Number.isFinite(img.id);
			});
	}

	function getOriginVideos(taskId) {
		const wrap = document.querySelector('.client-as-update-first-origin-videos[data-task-id="' + taskId + '"]');
		if (!wrap) return [];

		return Array.from(wrap.querySelectorAll(".client-as-update-first-origin-video"))
			.map(function(el) {
				const id = Number(el.dataset.videoId);
				return {
					id: id,
					url: str(el.dataset.videoUrl),
					filename: str(el.dataset.videoFilename)
				};
			})
			.filter(function(video) {
				return Number.isFinite(video.id);
			});
	}

	function getCurrentScalarState() {
		return {
			customerName: trim(customerNameInput.value),
			roadAddress: trim(roadAddressInput.value),
			detailAddress: trim(detailAddressInput.value),
			doName: trim(doNameInput.value),
			siName: trim(siNameInput.value),
			guName: trim(guNameInput.value),
			zipCode: trim(zipCodeInput.value),
			onsiteContact: formatKoreanPhone(onlyDigits(onsiteContactInput.value)),

			productName: trim(productNameInput.value),
			productSize: trim(productSizeInput.value),
			productColor: trim(productColorInput.value),
			productOptions: trim(productOptionsInput.value),

			purchaseDate: trim(purchaseDateInput.value),
			billingTarget: trim(billingTargetSelect.value),

			applicantName: trim(applicantNameInput.value),
			applicantPhone: formatKoreanPhone(onlyDigits(applicantPhoneInput.value)),
			applicantEmail: trim(applicantEmailInput.value),

			subject: trim(subjectSelect.value),
			reason: trim(reasonInput.value)
		};
	}

	function getKeptExistingImageIds() {
		return state.existingImages
			.filter(function(img) { return !state.deletedImageIds.has(img.id); })
			.map(function(img) { return img.id; })
			.sort(function(a, b) { return a - b; });
	}

	function getKeptExistingVideoIds() {
		return state.existingVideos
			.filter(function(video) { return !state.deletedVideoIds.has(video.id); })
			.map(function(video) { return video.id; })
			.sort(function(a, b) { return a - b; });
	}

	function hasChanges() {
		if (!state.original) return false;

		if (JSON.stringify(state.original.scalars) !== JSON.stringify(getCurrentScalarState())) {
			return true;
		}
		if (JSON.stringify(state.original.imageIds) !== JSON.stringify(getKeptExistingImageIds())) {
			return true;
		}
		if (JSON.stringify(state.original.videoIds) !== JSON.stringify(getKeptExistingVideoIds())) {
			return true;
		}
		if (state.newImageFiles.files.length > 0) return true;
		if (state.newVideoFiles.files.length > 0) return true;

		return false;
	}

	function updateSubmitState() {
		submitBtn.disabled = !hasChanges();
	}

	function renderExistingImages() {
		const kept = state.existingImages.filter(function(img) {
			return !state.deletedImageIds.has(img.id);
		});

		if (kept.length === 0) {
			existingImagesWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">유지 중인 신청 이미지가 없습니다.</div></div>';
			return;
		}

		existingImagesWrap.innerHTML = kept.map(function(img) {
			return ''
				+ '<div class="col-6 col-md-4 col-lg-3">'
				+ '  <div class="client-as-update-first-image-card">'
				+ '      <a href="' + escapeHtml(img.url) + '" target="_blank" rel="noopener">'
				+ '          <img src="' + escapeHtml(img.url) + '" alt="">'
				+ '      </a>'
				+ '      <button type="button" class="client-as-update-first-image-action client-as-update-first-image-action-remove client-as-update-first-remove-existing-btn" data-image-id="' + img.id + '">삭제</button>'
				+ '      <div class="client-as-update-first-image-name">' + escapeHtml(img.filename) + '</div>'
				+ '  </div>'
				+ '</div>';
		}).join("");
	}

	function renderDeletedImages() {
		const deleted = state.existingImages.filter(function(img) {
			return state.deletedImageIds.has(img.id);
		});

		if (deleted.length === 0) {
			deletedImagesWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">삭제 예정인 이미지가 없습니다.</div></div>';
			return;
		}

		deletedImagesWrap.innerHTML = deleted.map(function(img) {
			return ''
				+ '<div class="col-6 col-md-4 col-lg-3">'
				+ '  <div class="client-as-update-first-image-card">'
				+ '      <a href="' + escapeHtml(img.url) + '" target="_blank" rel="noopener">'
				+ '          <img src="' + escapeHtml(img.url) + '" alt="">'
				+ '      </a>'
				+ '      <button type="button" class="client-as-update-first-image-action client-as-update-first-image-action-restore client-as-update-first-restore-existing-btn" data-image-id="' + img.id + '">복원</button>'
				+ '      <div class="client-as-update-first-image-name">' + escapeHtml(img.filename) + '</div>'
				+ '  </div>'
				+ '</div>';
		}).join("");
	}

	function renderAddedImages() {
		const files = Array.from(state.newImageFiles.files);

		if (files.length === 0) {
			addedImagesWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">새로 추가한 이미지가 없습니다.</div></div>';
			return;
		}

		addedImagesWrap.innerHTML = files.map(function(file, index) {
			const url = URL.createObjectURL(file);
			return ''
				+ '<div class="col-6 col-md-4 col-lg-3">'
				+ '  <div class="client-as-update-first-image-card">'
				+ '      <img src="' + escapeHtml(url) + '" alt="">'
				+ '      <button type="button" class="client-as-update-first-image-action client-as-update-first-image-action-remove client-as-update-first-remove-new-btn" data-index="' + index + '">삭제</button>'
				+ '      <div class="client-as-update-first-image-name">' + escapeHtml(file.name) + '</div>'
				+ '  </div>'
				+ '</div>';
		}).join("");
	}

	function renderExistingVideos() {
		const kept = state.existingVideos.filter(function(video) {
			return !state.deletedVideoIds.has(video.id);
		});

		if (kept.length === 0) {
			existingVideosWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">유지 중인 신청 비디오가 없습니다.</div></div>';
			return;
		}

		existingVideosWrap.innerHTML = kept.map(function(video) {
			return ''
				+ '<div class="col-12 col-md-6 col-lg-4">'
				+ '  <div class="client-as-update-first-image-card">'
				+ '      <video src="' + escapeHtml(video.url) + '" controls preload="metadata" style="width:100%; height:180px; object-fit:cover;"></video>'
				+ '      <button type="button" class="client-as-update-first-image-action client-as-update-first-image-action-remove client-as-update-first-remove-existing-video-btn" data-video-id="' + video.id + '">삭제</button>'
				+ '      <div class="client-as-update-first-image-name">' + escapeHtml(video.filename) + '</div>'
				+ '  </div>'
				+ '</div>';
		}).join("");
	}

	function renderDeletedVideos() {
		const deleted = state.existingVideos.filter(function(video) {
			return state.deletedVideoIds.has(video.id);
		});

		if (deleted.length === 0) {
			deletedVideosWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">삭제 예정인 비디오가 없습니다.</div></div>';
			return;
		}

		deletedVideosWrap.innerHTML = deleted.map(function(video) {
			return ''
				+ '<div class="col-12 col-md-6 col-lg-4">'
				+ '  <div class="client-as-update-first-image-card">'
				+ '      <video src="' + escapeHtml(video.url) + '" controls preload="metadata" style="width:100%; height:180px; object-fit:cover;"></video>'
				+ '      <button type="button" class="client-as-update-first-image-action client-as-update-first-image-action-restore client-as-update-first-restore-existing-video-btn" data-video-id="' + video.id + '">복원</button>'
				+ '      <div class="client-as-update-first-image-name">' + escapeHtml(video.filename) + '</div>'
				+ '  </div>'
				+ '</div>';
		}).join("");
	}

	function renderAddedVideos() {
		const files = Array.from(state.newVideoFiles.files);

		if (files.length === 0) {
			addedVideosWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">새로 추가한 비디오가 없습니다.</div></div>';
			return;
		}

		addedVideosWrap.innerHTML = files.map(function(file, index) {
			const url = URL.createObjectURL(file);
			return ''
				+ '<div class="col-12 col-md-6 col-lg-4">'
				+ '  <div class="client-as-update-first-image-card">'
				+ '      <video src="' + escapeHtml(url) + '" controls preload="metadata" style="width:100%; height:180px; object-fit:cover;"></video>'
				+ '      <button type="button" class="client-as-update-first-image-action client-as-update-first-image-action-remove client-as-update-first-remove-new-video-btn" data-index="' + index + '">삭제</button>'
				+ '      <div class="client-as-update-first-image-name">' + escapeHtml(file.name) + '</div>'
				+ '  </div>'
				+ '</div>';
		}).join("");
	}

	function rerenderAllFiles() {
		renderExistingImages();
		renderDeletedImages();
		renderAddedImages();

		renderExistingVideos();
		renderDeletedVideos();
		renderAddedVideos();

		updateSubmitState();
	}

	function resetModalForm() {
		form.reset();

		taskIdInput.value = "";
		doNameInput.value = "";
		siNameInput.value = "";
		guNameInput.value = "";
		zipCodeInput.value = "";

		resetSubjectSelect();

		state.original = null;
		state.existingImages = [];
		state.deletedImageIds = new Set();
		state.newImageFiles = new DataTransfer();

		state.existingVideos = [];
		state.deletedVideoIds = new Set();
		state.newVideoFiles = new DataTransfer();

		existingImagesWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">유지 중인 신청 이미지가 없습니다.</div></div>';
		deletedImagesWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">삭제 예정인 이미지가 없습니다.</div></div>';
		addedImagesWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">새로 추가한 이미지가 없습니다.</div></div>';

		existingVideosWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">유지 중인 신청 비디오가 없습니다.</div></div>';
		deletedVideosWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">삭제 예정인 비디오가 없습니다.</div></div>';
		addedVideosWrap.innerHTML = '<div class="col-12"><div class="client-as-update-first-empty">새로 추가한 비디오가 없습니다.</div></div>';

		submitBtn.disabled = true;
	}

	function appendNewImages(files) {
		const next = new DataTransfer();

		Array.from(state.newImageFiles.files).forEach(function(file) {
			next.items.add(file);
		});

		Array.from(files || []).forEach(function(file) {
			if (file && file.type && file.type.startsWith("image/")) {
				next.items.add(file);
			}
		});

		state.newImageFiles = next;
		newImagesInput.value = "";
		rerenderAllFiles();
	}

	function appendNewVideos(files) {
		const next = new DataTransfer();

		Array.from(state.newVideoFiles.files).forEach(function(file) {
			next.items.add(file);
		});

		Array.from(files || []).forEach(function(file) {
			if (file && file.type && file.type.startsWith("video/")) {
				next.items.add(file);
			}
		});

		state.newVideoFiles = next;
		newVideosInput.value = "";
		rerenderAllFiles();
	}

	function removeNewImage(indexToRemove) {
		const next = new DataTransfer();

		Array.from(state.newImageFiles.files).forEach(function(file, index) {
			if (index !== indexToRemove) next.items.add(file);
		});

		state.newImageFiles = next;
		rerenderAllFiles();
	}

	function removeNewVideo(indexToRemove) {
		const next = new DataTransfer();

		Array.from(state.newVideoFiles.files).forEach(function(file, index) {
			if (index !== indexToRemove) next.items.add(file);
		});

		state.newVideoFiles = next;
		rerenderAllFiles();
	}

	function openUpdateModal(button) {
		const taskId = button.dataset.asId;
		if (!taskId) return;

		resetModalForm();

		taskIdInput.value = taskId;

		customerNameInput.value = str(button.dataset.customerName);
		roadAddressInput.value = str(button.dataset.roadAddress);
		detailAddressInput.value = str(button.dataset.detailAddress);

		doNameInput.value = str(button.dataset.doName);
		siNameInput.value = str(button.dataset.siName);
		guNameInput.value = str(button.dataset.guName);
		zipCodeInput.value = str(button.dataset.zipCode);

		onsiteContactInput.value = formatKoreanPhone(button.dataset.onsiteContact);

		productNameInput.value = str(button.dataset.productName);
		productSizeInput.value = str(button.dataset.productSize);
		productColorInput.value = str(button.dataset.productColor);
		productOptionsInput.value = str(button.dataset.productOptions);

		purchaseDateInput.value = str(button.dataset.purchaseDate);
		billingTargetSelect.value = str(button.dataset.billingTarget);

		applicantNameInput.value = str(button.dataset.applicantName);
		applicantPhoneInput.value = formatKoreanPhone(button.dataset.applicantPhone);
		applicantEmailInput.value = str(button.dataset.applicantEmail);

		const fullSubject = str(button.dataset.subject);
		const parsedSubject = parseSubject(fullSubject);
		subjectCategorySelect.value = parsedSubject.category;
		fillSubjectSelect(parsedSubject.category, fullSubject);

		reasonInput.value = getOriginReason(taskId);

		state.existingImages = getOriginImages(taskId);
		state.existingVideos = getOriginVideos(taskId);

		state.original = {
			scalars: getCurrentScalarState(),
			imageIds: getKeptExistingImageIds(),
			videoIds: getKeptExistingVideoIds()
		};

		rerenderAllFiles();
		modal.show();
	}

	function openAddressSearch() {
		new daum.Postcode({
			oncomplete: function(data) {
				const fullRoadAddr = data.roadAddress || "";
				const zonecode = data.zonecode || "";

				const addrParts = fullRoadAddr.split(" ");
				const doName = addrParts[0] || "";
				let siName = "";
				let guName = "";

				if (addrParts.length >= 2) {
					if ((addrParts[1] || "").endsWith("시") || (addrParts[1] || "").endsWith("군")) {
						siName = addrParts[1];
						guName = addrParts[2] || "";
					} else {
						siName = "";
						guName = addrParts[1] || "";
					}
				}

				roadAddressInput.value = fullRoadAddr;
				zipCodeInput.value = zonecode;
				doNameInput.value = doName;
				siNameInput.value = siName;
				guNameInput.value = guName;

				updateSubmitState();
			}
		}).open();
	}

	function validateForm() {
		if (!trim(customerNameInput.value)) {
			alert("고객 성함을 입력해 주세요.");
			customerNameInput.focus();
			return false;
		}

		if (!trim(roadAddressInput.value)) {
			alert("주소를 입력해 주세요.");
			searchAddressBtn.focus();
			return false;
		}

		const onsiteDigits = onlyDigits(onsiteContactInput.value);
		onsiteContactInput.value = formatKoreanPhone(onsiteDigits);

		if (!isValidPhoneByDigits(onsiteDigits)) {
			alert("현장 연락처를 '- 없이 숫자만' 입력해 주세요.");
			onsiteContactInput.focus();
			return false;
		}

		if (!trim(productNameInput.value)) {
			alert("제품명을 입력해 주세요.");
			productNameInput.focus();
			return false;
		}

		if (!trim(productSizeInput.value)) {
			alert("제품 사이즈를 입력해 주세요.");
			productSizeInput.focus();
			return false;
		}

		if (!trim(productColorInput.value)) {
			alert("제품 컬러를 입력해 주세요.");
			productColorInput.focus();
			return false;
		}

		if (!trim(productOptionsInput.value)) {
			alert("제품 옵션 여부를 입력해 주세요.");
			productOptionsInput.focus();
			return false;
		}

		if (!trim(subjectCategorySelect.value)) {
			alert("AS 카테고리를 선택해 주세요.");
			subjectCategorySelect.focus();
			return false;
		}

		if (!trim(subjectSelect.value)) {
			alert("AS 증상을 선택해 주세요.");
			subjectSelect.focus();
			return false;
		}

		const applicantDigits = onlyDigits(applicantPhoneInput.value);
		if (applicantDigits) {
			applicantPhoneInput.value = formatKoreanPhone(applicantDigits);
			if (!isValidPhoneByDigits(applicantDigits)) {
				alert("접수 담당자 연락처 형식이 올바르지 않습니다.");
				applicantPhoneInput.focus();
				return false;
			}
		}

		if (!isValidEmail(applicantEmailInput.value)) {
			alert("접수 담당자 이메일 형식이 올바르지 않습니다.");
			applicantEmailInput.focus();
			return false;
		}

		return true;
	}

	function buildRequestFormData() {
		const formData = new FormData();

		formData.append("customerName", trim(customerNameInput.value));
		formData.append("roadAddress", trim(roadAddressInput.value));
		formData.append("detailAddress", trim(detailAddressInput.value));
		formData.append("doName", trim(doNameInput.value));
		formData.append("siName", trim(siNameInput.value));
		formData.append("guName", trim(guNameInput.value));
		formData.append("zipCode", trim(zipCodeInput.value));
		formData.append("onsiteContact", formatKoreanPhone(onlyDigits(onsiteContactInput.value)));

		formData.append("productName", trim(productNameInput.value));
		formData.append("productSize", trim(productSizeInput.value));
		formData.append("productColor", trim(productColorInput.value));
		formData.append("productOptions", trim(productOptionsInput.value));

		formData.append("purchaseDate", trim(purchaseDateInput.value));
		formData.append("billingTarget", trim(billingTargetSelect.value));

		formData.append("applicantName", trim(applicantNameInput.value));
		formData.append("applicantPhone", formatKoreanPhone(onlyDigits(applicantPhoneInput.value)));
		formData.append("applicantEmail", trim(applicantEmailInput.value));

		formData.append("subject", trim(subjectSelect.value));
		formData.append("reason", trim(reasonInput.value));

		Array.from(state.deletedImageIds).forEach(function(id) {
			formData.append("deleteImageIds", String(id));
		});

		Array.from(state.deletedVideoIds).forEach(function(id) {
			formData.append("deleteVideoIds", String(id));
		});

		Array.from(state.newImageFiles.files).forEach(function(file) {
			formData.append("newImages", file);
		});

		Array.from(state.newVideoFiles.files).forEach(function(file) {
			formData.append("newVideos", file);
		});

		return formData;
	}

	function getCsrfHeaders() {
		const token = document.querySelector('meta[name="_csrf"]')?.content;
		const header = document.querySelector('meta[name="_csrf_header"]')?.content;
		if (token && header) return { [header]: token };
		return {};
	}

	onsiteContactInput.addEventListener("input", function() {
		onsiteContactInput.value = formatKoreanPhone(onsiteContactInput.value);
		updateSubmitState();
	});

	applicantPhoneInput.addEventListener("input", function() {
		applicantPhoneInput.value = formatKoreanPhone(applicantPhoneInput.value);
		updateSubmitState();
	});

	[
		customerNameInput, roadAddressInput, detailAddressInput, doNameInput, siNameInput, guNameInput, zipCodeInput,
		onsiteContactInput,
		productNameInput, productSizeInput, productColorInput, productOptionsInput,
		purchaseDateInput, billingTargetSelect,
		applicantNameInput, applicantPhoneInput, applicantEmailInput,
		reasonInput
	].forEach(function(el) {
		el.addEventListener("input", updateSubmitState);
		el.addEventListener("change", updateSubmitState);
	});

	subjectCategorySelect.addEventListener("change", function() {
		fillSubjectSelect(subjectCategorySelect.value, "");
		updateSubmitState();
	});

	subjectSelect.addEventListener("change", updateSubmitState);

	fileTriggerBtn.addEventListener("click", function() {
		newImagesInput.click();
	});

	videoTriggerBtn.addEventListener("click", function() {
		newVideosInput.click();
	});

	newImagesInput.addEventListener("change", function() {
		appendNewImages(newImagesInput.files);
	});

	newVideosInput.addEventListener("change", function() {
		appendNewVideos(newVideosInput.files);
	});

	searchAddressBtn.addEventListener("click", openAddressSearch);

	document.addEventListener("click", function(e) {
		const openBtn = e.target.closest(".client-as-update-first-open-btn");
		if (openBtn) {
			openUpdateModal(openBtn);
			return;
		}

		const removeExistingBtn = e.target.closest(".client-as-update-first-remove-existing-btn");
		if (removeExistingBtn) {
			const imageId = Number(removeExistingBtn.dataset.imageId);
			if (Number.isFinite(imageId)) {
				state.deletedImageIds.add(imageId);
				rerenderAllFiles();
			}
			return;
		}

		const restoreExistingBtn = e.target.closest(".client-as-update-first-restore-existing-btn");
		if (restoreExistingBtn) {
			const imageId = Number(restoreExistingBtn.dataset.imageId);
			if (Number.isFinite(imageId)) {
				state.deletedImageIds.delete(imageId);
				rerenderAllFiles();
			}
			return;
		}

		const removeNewBtn = e.target.closest(".client-as-update-first-remove-new-btn");
		if (removeNewBtn) {
			const index = Number(removeNewBtn.dataset.index);
			if (Number.isFinite(index)) {
				removeNewImage(index);
			}
			return;
		}

		const removeExistingVideoBtn = e.target.closest(".client-as-update-first-remove-existing-video-btn");
		if (removeExistingVideoBtn) {
			const videoId = Number(removeExistingVideoBtn.dataset.videoId);
			if (Number.isFinite(videoId)) {
				state.deletedVideoIds.add(videoId);
				rerenderAllFiles();
			}
			return;
		}

		const restoreExistingVideoBtn = e.target.closest(".client-as-update-first-restore-existing-video-btn");
		if (restoreExistingVideoBtn) {
			const videoId = Number(restoreExistingVideoBtn.dataset.videoId);
			if (Number.isFinite(videoId)) {
				state.deletedVideoIds.delete(videoId);
				rerenderAllFiles();
			}
			return;
		}

		const removeNewVideoBtn = e.target.closest(".client-as-update-first-remove-new-video-btn");
		if (removeNewVideoBtn) {
			const index = Number(removeNewVideoBtn.dataset.index);
			if (Number.isFinite(index)) {
				removeNewVideo(index);
			}
		}
	});

	form.addEventListener("submit", function(e) {
		e.preventDefault();

		if (!hasChanges()) {
			submitBtn.disabled = true;
			return;
		}

		if (!validateForm()) {
			return;
		}

		const taskId = trim(taskIdInput.value);
		if (!taskId) {
			alert("수정 대상 정보가 올바르지 않습니다.");
			return;
		}

		const formData = buildRequestFormData();
		submitBtn.disabled = true;

		fetch("/customer/asUpdate/" + taskId, {
			method: "POST",
			headers: getCsrfHeaders(),
			body: formData
		})
			.then(async function(res) {
				const data = await res.json().catch(function() { return {}; });

				if (!res.ok || !data.success) {
					throw new Error(data.message || "AS 수정 중 오류가 발생했습니다.");
				}

				alert(data.message || "AS 신청이 수정되었습니다.");
				modal.hide();
				window.location.reload();
			})
			.catch(function(err) {
				alert(err.message || "서버 오류가 발생했습니다.");
				console.error(err);
				updateSubmitState();
			});
	});

	modalEl.addEventListener("hidden.bs.modal", function() {
		resetModalForm();
	});

	resetModalForm();
});