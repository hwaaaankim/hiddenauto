document.addEventListener("DOMContentLoaded", function() {
	'use strict';

	const form = document.getElementById("as-management-third-form");
	if (!form) return;

	// ---------------------------------------------------------
	// bootstrap tooltip (guard)
	// ---------------------------------------------------------
	try {
		if (window.bootstrap && document.querySelectorAll('[data-bs-toggle="tooltip"]').length > 0) {
			[...document.querySelectorAll('[data-bs-toggle="tooltip"]')].forEach(el => new bootstrap.Tooltip(el));
		}
	} catch (e) { /* ignore */ }

	// ---------------------------------------------------------
	// Elements
	// ---------------------------------------------------------
	const saveBtn = document.getElementById("as-management-third-saveBtn");
	const dirtyBadge = document.getElementById("as-management-third-dirtyBadge");

	const companyIdEl = document.getElementById("as-management-third-companyId");
	const companyNameDisplay = document.getElementById("as-management-third-companyNameDisplay");
	const requestedByNameDisplay = document.getElementById("as-management-third-requestedByNameDisplay");
	const companyEditBtn = document.getElementById("as-management-third-companyEditBtn");
	const companySearchWrap = document.getElementById("as-management-third-companySearchWrap");
	const companyKeywordEl = document.getElementById("as-management-third-companyKeyword");
	const companyResultsEl = document.getElementById("as-management-third-companyResults");

	const addressEditBtn = document.getElementById("as-management-third-addressEditBtn");
	const addressModalEl = document.getElementById("as-management-third-addressModal");
	const addressSearchBtn = document.getElementById("as-management-third-addressSearchBtn");
	const addressConfirmBtn = document.getElementById("as-management-third-addressConfirmBtn");

	const zipCodeEl = document.getElementById("as-management-third-zipCode");
	const doNameEl = document.getElementById("as-management-third-doName");
	const siNameEl = document.getElementById("as-management-third-siName");
	const guNameEl = document.getElementById("as-management-third-guName");
	const roadAddressEl = document.getElementById("as-management-third-roadAddress");
	const detailAddressEl = document.getElementById("as-management-third-detailAddress");

	const modalZip = document.getElementById("as-management-third-modalZip");
	const modalDo = document.getElementById("as-management-third-modalDo");
	const modalSi = document.getElementById("as-management-third-modalSi");
	const modalGu = document.getElementById("as-management-third-modalGu");
	const modalRoad = document.getElementById("as-management-third-modalRoad");
	const modalDetail = document.getElementById("as-management-third-modalDetail");

	const customerNameEl = document.getElementById("as-management-third-customerName");
	const onsiteContactEl = document.getElementById("as-management-third-onsiteContact");
	const productNameEl = document.getElementById("as-management-third-productName");
	const productSizeEl = document.getElementById("as-management-third-productSize");
	const productColorEl = document.getElementById("as-management-third-productColor");
	const productOptionsEl = document.getElementById("as-management-third-productOptions");

	const subjectInputEl = document.getElementById("as-management-third-subjectInput");
	const subjectCategoryEl = document.getElementById("as-management-third-subjectCategory");
	const subjectSymptomEl = document.getElementById("as-management-third-subjectSymptom");

	const priceEl = document.getElementById("as-management-third-price");
	const statusEl = document.getElementById("as-management-third-status");
	const assignedHandlerEl = document.getElementById("as-management-third-assignedHandlerId");

	const existingImagesWrap = document.getElementById("as-management-third-existingRequestImages");
	const deleteRequestImageIdsEl = document.getElementById("as-management-third-deleteRequestImageIds");

	const uploadBtn = document.getElementById("as-management-third-uploadBtn");
	const newImagesInput = document.getElementById("as-management-third-newRequestImages");
	const previewList = document.getElementById("as-management-third-previewList");

	const deleteBtn = document.getElementById("as-management-third-deleteBtn");
	const deleteForm = document.getElementById("as-management-third-deleteForm");

	// ---------------------------------------------------------
	// Dirty tracking
	// ---------------------------------------------------------
	const initial = {
		companyId: (companyIdEl?.value || "").trim(),
		zipCode: (zipCodeEl?.value || "").trim(),
		doName: (doNameEl?.value || "").trim(),
		siName: (siNameEl?.value || "").trim(),
		guName: (guNameEl?.value || "").trim(),
		roadAddress: (roadAddressEl?.value || "").trim(),
		detailAddress: (detailAddressEl?.value || "").trim(),

		customerName: (customerNameEl?.value || "").trim(),
		onsiteContact: (onsiteContactEl?.value || "").trim(),
		productName: (productNameEl?.value || "").trim(),
		productSize: (productSizeEl?.value || "").trim(),
		productColor: (productColorEl?.value || "").trim(),
		productOptions: (productOptionsEl?.value || "").trim(),

		subject: (subjectInputEl?.value || "").trim(),

		price: (priceEl?.value || "").trim(),
		status: (statusEl?.value || "").trim(),
		assignedHandlerId: (assignedHandlerEl?.value || "").trim(),
	};

	let deletedRequestImageIds = []; // number[]
	let companySearchOpen = false;

	function setDirty(isDirty) {
		if (!saveBtn || !dirtyBadge) return;
		saveBtn.disabled = !isDirty;

		if (isDirty) {
			dirtyBadge.textContent = "변경사항 있음";
			dirtyBadge.classList.remove("bg-light", "text-dark");
			dirtyBadge.classList.add("bg-warning", "text-dark");
		} else {
			dirtyBadge.textContent = "변경사항 없음";
			dirtyBadge.classList.remove("bg-warning", "text-dark");
			dirtyBadge.classList.add("bg-light", "text-dark");
		}
	}

	function getV(el) {
		return (el && typeof el.value === "string") ? el.value.trim() : "";
	}

	function computeDirty() {
		// 기본 필드 비교
		const now = {
			companyId: getV(companyIdEl),
			zipCode: getV(zipCodeEl),
			doName: getV(doNameEl),
			siName: getV(siNameEl),
			guName: getV(guNameEl),
			roadAddress: getV(roadAddressEl),
			detailAddress: getV(detailAddressEl),

			customerName: getV(customerNameEl),
			onsiteContact: getV(onsiteContactEl),
			productName: getV(productNameEl),
			productSize: getV(productSizeEl),
			productColor: getV(productColorEl),
			productOptions: getV(productOptionsEl),

			subject: getV(subjectInputEl),

			price: getV(priceEl),
			status: getV(statusEl),
			assignedHandlerId: getV(assignedHandlerEl),
		};

		let dirty = false;
		for (const k of Object.keys(initial)) {
			if ((initial[k] || "") !== (now[k] || "")) {
				dirty = true;
				break;
			}
		}

		// 이미지 변경(삭제/추가)
		if (!dirty && deletedRequestImageIds.length > 0) dirty = true;
		if (!dirty && newImagesInput && newImagesInput.files && newImagesInput.files.length > 0) dirty = true;

		setDirty(dirty);
	}

	// 초기 상태
	setDirty(false);

	// ---------------------------------------------------------
	// Company search (AJAX)
	// ---------------------------------------------------------
	function toggleCompanySearch(open) {
		if (!companySearchWrap) return;
		companySearchOpen = open;

		if (open) {
			companySearchWrap.classList.remove("d-none");
			companyKeywordEl?.focus();
		} else {
			companySearchWrap.classList.add("d-none");
			if (companyKeywordEl) companyKeywordEl.value = "";
			if (companyResultsEl) companyResultsEl.innerHTML = "";
		}
	}

	if (companyEditBtn) {
		companyEditBtn.addEventListener("click", function() {
			toggleCompanySearch(!companySearchOpen);
		});
	}

	let companySearchTimer = null;
	function debounceCompanySearch(fn, waitMs) {
		return function(...args) {
			if (companySearchTimer) clearTimeout(companySearchTimer);
			companySearchTimer = setTimeout(() => fn.apply(null, args), waitMs);
		};
	}

	function escapeHtml(str) {
		return String(str || "")
			.replaceAll("&", "&amp;")
			.replaceAll("<", "&lt;")
			.replaceAll(">", "&gt;")
			.replaceAll('"', "&quot;")
			.replaceAll("'", "&#039;");
	}

	async function searchCompanies(keyword) {
		if (!companyResultsEl) return;

		const kw = String(keyword || "").trim();
		if (!kw) {
			companyResultsEl.innerHTML = "";
			return;
		}

		companyResultsEl.innerHTML = `<div class="list-group-item text-muted small">검색 중...</div>`;

		try {
			const url = `/management/api/companies/search?keyword=${encodeURIComponent(kw)}&limit=30`;
			const res = await fetch(url, { method: "GET" });
			if (!res.ok) throw new Error("search failed");
			const list = await res.json();

			if (!Array.isArray(list) || list.length === 0) {
				companyResultsEl.innerHTML = `<div class="list-group-item text-muted small">검색 결과가 없습니다.</div>`;
				return;
			}

			const html = list.map(item => {
				const disabled = item.hasRepresentative === false;
				const cls = disabled ? "as-management-third-company-item as-management-third-company-disabled" : "as-management-third-company-item";

				const addr = escapeHtml(item.address || "-");
				const name = escapeHtml(item.companyName || "-");
				const rep = escapeHtml(item.representativeName || "(대표 없음)");

				const hint = disabled ? `<div class="as-management-third-company-addr text-danger">대표(CUSTOMER_REPRESENTATIVE)가 없어 선택할 수 없습니다.</div>` : "";

				return `
					<button type="button" class="${cls}"
						${disabled ? "disabled" : ""}
						data-company-id="${item.companyId}"
						data-company-name="${name}"
						data-rep-name="${rep}">
						<div class="as-management-third-company-title">
							<div class="as-management-third-company-name">${name}</div>
							<div class="as-management-third-company-rep">${rep}</div>
						</div>
						<div class="as-management-third-company-addr">${addr}</div>
						${hint}
					</button>
				`;
			}).join("");

			companyResultsEl.innerHTML = html;

		} catch (e) {
			companyResultsEl.innerHTML = `<div class="list-group-item text-danger small">검색 중 오류가 발생했습니다.</div>`;
		}
	}

	const debouncedSearch = debounceCompanySearch(searchCompanies, 250);

	if (companyKeywordEl) {
		companyKeywordEl.addEventListener("input", function() {
			debouncedSearch(companyKeywordEl.value);
		});
	}

	if (companyResultsEl) {
		companyResultsEl.addEventListener("click", function(e) {
			const btn = e.target.closest("button[data-company-id]");
			if (!btn) return;

			const companyId = String(btn.getAttribute("data-company-id") || "").trim();
			const companyName = String(btn.getAttribute("data-company-name") || "").trim();
			const repName = String(btn.getAttribute("data-rep-name") || "").trim();

			if (!companyId) return;

			if (companyIdEl) companyIdEl.value = companyId;
			if (companyNameDisplay) companyNameDisplay.value = companyName || "-";
			if (requestedByNameDisplay) requestedByNameDisplay.value = repName || "-";

			// 검색 닫기
			toggleCompanySearch(false);

			// 변경감지
			computeDirty();
		});
	}

	// ---------------------------------------------------------
	// Address modal (Daum Postcode)
	// ---------------------------------------------------------
	let addressModal = null;
	try {
		if (window.bootstrap && addressModalEl) {
			addressModal = new bootstrap.Modal(addressModalEl);
		}
	} catch (e) {
		addressModal = null;
	}

	function openAddressModal() {
		// 현재 값 복사
		if (modalZip) modalZip.value = getV(zipCodeEl);
		if (modalDo) modalDo.value = getV(doNameEl);
		if (modalSi) modalSi.value = getV(siNameEl);
		if (modalGu) modalGu.value = getV(guNameEl);
		if (modalRoad) modalRoad.value = getV(roadAddressEl);
		if (modalDetail) modalDetail.value = getV(detailAddressEl);

		if (addressModal) addressModal.show();
		else alert("주소 모달을 열 수 없습니다. (bootstrap 모달 확인 필요)");
	}

	if (addressEditBtn) {
		addressEditBtn.addEventListener("click", openAddressModal);
	}

	if (addressSearchBtn) {
		addressSearchBtn.addEventListener("click", function() {
			if (!window.daum || !window.daum.Postcode) {
				alert("Daum 우편번호 스크립트를 불러오지 못했습니다.");
				return;
			}

			new daum.Postcode({
				oncomplete: function(data) {
					const fullRoadAddr = data.roadAddress || "";
					const zonecode = data.zonecode || "";

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

					if (modalZip) modalZip.value = zonecode;
					if (modalDo) modalDo.value = doName;
					if (modalSi) modalSi.value = siName;
					if (modalGu) modalGu.value = guName;
					if (modalRoad) modalRoad.value = fullRoadAddr;
				}
			}).open();
		});
	}

	if (addressConfirmBtn) {
		addressConfirmBtn.addEventListener("click", function() {
			// 모달 값 -> 본문 반영
			if (zipCodeEl) zipCodeEl.value = getV(modalZip);
			if (doNameEl) doNameEl.value = getV(modalDo);
			if (siNameEl) siNameEl.value = getV(modalSi);
			if (guNameEl) guNameEl.value = getV(modalGu);
			if (roadAddressEl) roadAddressEl.value = getV(modalRoad);
			if (detailAddressEl) detailAddressEl.value = getV(modalDetail);

			// 닫기
			if (addressModal) addressModal.hide();

			// 변경감지
			computeDirty();
		});
	}

	// ---------------------------------------------------------
	// Subject 2-step select
	// ---------------------------------------------------------
	const SUBJECT_MAP = {
		"상부장": [
			"도어 파손", "도어 스크레치", "도어 휘어짐", "도어 변색", "도어 단차 불량", "도어 마감 불량",
			"손잡이 불량", "바디 변색", "바디 스크래치", "바디 파손", "개폐 불량", "경첩 불량",
			"LED 점등 불량", "오출고", "기타 사유"
		],
		"슬라이드장": [
			"도어 파손", "도어 스크레치", "도어 변색", "도어 간격 불량", "바디 변색", "바디 스크레치",
			"바디 파손", "개폐불량", "댐퍼불량", "손잡이 불량", "LED 점등 불량", "오출고", "기타 사유"
		],
		"플랩장": [
			"도어 파손", "도어 스크레치", "도어 변색", "도어 단차 불량", "유압 불량", "바디 변색",
			"바디 스크래치", "바디 파손", "개폐 불량", "경첩 불량", "LED 점등 불량", "오출고", "기타 사유"
		],
		"하부장": [
			"도어 단차 불량", "서랍 개폐불량", "도어 마감 불량", "오출고", "기타 사유"
		],
		"거울": [
			"테두리 도장 불량", "유리 스크레치", "유리 파손", "유리 변색", "LED 점등 불량", "오출고", "기타 사유"
		]
	};

	function resetSymptomSelect() {
		if (!subjectSymptomEl) return;
		subjectSymptomEl.innerHTML = `<option value="">== 증상 선택 ==</option>`;
		subjectSymptomEl.disabled = true;
	}

	function fillSymptomSelect(category) {
		resetSymptomSelect();
		if (!category || !SUBJECT_MAP[category] || !subjectSymptomEl) return;

		const unique = Array.from(new Set(SUBJECT_MAP[category]));
		unique.forEach(symptom => {
			const opt = document.createElement("option");
			opt.value = `${category} - ${symptom}`;
			opt.textContent = symptom;
			subjectSymptomEl.appendChild(opt);
		});

		subjectSymptomEl.disabled = false;
	}

	// 초기 subject에서 category/symptom 미리 세팅(가능하면)
	function initSubjectSelectFromCurrent() {
		const cur = getV(subjectInputEl);
		if (!cur || !subjectCategoryEl || !subjectSymptomEl) return;

		// "카테고리 - 증상" 형태만 처리
		const parts = cur.split(" - ");
		if (parts.length !== 2) return;

		const category = parts[0].trim();
		const symptom = parts[1].trim();
		if (!category || !symptom) return;

		// category set
		subjectCategoryEl.value = category;
		fillSymptomSelect(category);

		// symptom match by textContent/value
		const targetValue = `${category} - ${symptom}`;
		subjectSymptomEl.value = targetValue;
	}

	if (subjectCategoryEl && subjectSymptomEl) {
		resetSymptomSelect();
		initSubjectSelectFromCurrent();

		subjectCategoryEl.addEventListener("change", function() {
			const category = subjectCategoryEl.value;
			fillSymptomSelect(category);

			// ✅ 1단계만 변경 시 subject는 변경하지 않음 (요구사항)
			// 변경감지도 하지 않음. (subjectInput이 안 바뀌므로 computeDirty 결과에도 영향 없음)
		});

		subjectSymptomEl.addEventListener("change", function() {
			const v = getV(subjectSymptomEl);
			if (!v) return; // ✅ 2단계가 선택되지 않으면 변경하지 않음 (요구사항)

			// ✅ 2단계까지 선택되면 subject에 반영
			if (subjectInputEl) subjectInputEl.value = v;

			computeDirty();
		});
	}

	// ---------------------------------------------------------
	// Phone formatting (onsiteContact) - 기존 등록 코드 스타일 반영
	// ---------------------------------------------------------
	function onlyDigits(v) {
		return String(v || "").replace(/\D/g, "");
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

		if (digits.length === 10) return digits.slice(0, 3) + "-" + digits.slice(3, 6) + "-" + digits.slice(6);
		if (digits.length >= 11) return digits.slice(0, 3) + "-" + digits.slice(3, 7) + "-" + digits.slice(7, 11);

		return digits.slice(0, 3) + "-" + digits.slice(3);
	}

	function isValidPhoneByDigits(digits) {
		digits = onlyDigits(digits);
		if (!digits.startsWith("0")) return false;
		return digits.length >= 9 && digits.length <= 11;
	}

	if (onsiteContactEl) {
		onsiteContactEl.addEventListener("input", function() {
			const digits = onlyDigits(onsiteContactEl.value);
			onsiteContactEl.value = formatKoreanPhone(digits);
			computeDirty();
		});
	}

	// ---------------------------------------------------------
	// Existing image delete (X)
	// ---------------------------------------------------------
	function syncDeletedImageIds() {
		if (deleteRequestImageIdsEl) {
			deleteRequestImageIdsEl.value = deletedRequestImageIds.join(",");
		}
	}

	if (existingImagesWrap) {
		existingImagesWrap.addEventListener("click", function(e) {
			const btn = e.target.closest(".as-management-third-img-remove-btn");
			if (!btn) return;

			const cardCol = btn.closest("[data-image-id]");
			if (!cardCol) return;

			const id = String(cardCol.getAttribute("data-image-id") || "").trim();
			if (!id) return;

			// UI에서 제거 + 삭제 목록에 추가
			deletedRequestImageIds.push(Number(id));
			syncDeletedImageIds();

			cardCol.remove();
			computeDirty();
		});
	}

	// ---------------------------------------------------------
	// New images upload + preview + remove
	// ---------------------------------------------------------
	function renderPreview() {
		if (!previewList || !newImagesInput) return;
		previewList.innerHTML = "";

		const files = Array.from(newImagesInput.files || []);
		if (files.length === 0) {
			previewList.innerHTML = `<div class="text-muted small">추가 업로드된 이미지가 없습니다.</div>`;
			return;
		}

		files.forEach((file, idx) => {
			const reader = new FileReader();
			reader.onload = function(ev) {
				const item = document.createElement("div");
				item.className = "as-management-third-preview-item";
				item.dataset.index = String(idx);

				const img = document.createElement("img");
				img.src = ev.target.result;

				const removeBtn = document.createElement("button");
				removeBtn.type = "button";
				removeBtn.className = "as-management-third-preview-remove";
				removeBtn.textContent = "×";
				removeBtn.addEventListener("click", function() {
					removeNewImage(idx);
				});

				item.appendChild(img);
				item.appendChild(removeBtn);
				previewList.appendChild(item);
			};
			reader.readAsDataURL(file);
		});
	}

	function removeNewImage(indexToRemove) {
		if (!newImagesInput) return;

		const dt = new DataTransfer();
		Array.from(newImagesInput.files || []).forEach((file, idx) => {
			if (idx !== indexToRemove) dt.items.add(file);
		});

		newImagesInput.files = dt.files;
		renderPreview();
		computeDirty();
	}

	if (uploadBtn && newImagesInput) {
		uploadBtn.addEventListener("click", function() {
			newImagesInput.click();
		});
	}

	if (newImagesInput) {
		newImagesInput.addEventListener("change", function() {
			renderPreview();
			computeDirty();
		});
	}

	// 초기 프리뷰
	renderPreview();

	// ---------------------------------------------------------
	// Input change watchers
	// ---------------------------------------------------------
	const watchEls = [
		companyIdEl,
		zipCodeEl, doNameEl, siNameEl, guNameEl, roadAddressEl, detailAddressEl,
		customerNameEl, onsiteContactEl, productNameEl, productSizeEl, productColorEl, productOptionsEl,
		subjectInputEl,
		priceEl, statusEl, assignedHandlerEl
	];

	watchEls.forEach(el => {
		if (!el) return;
		el.addEventListener("input", computeDirty);
		el.addEventListener("change", computeDirty);
	});

	// ---------------------------------------------------------
	// Submit validation
	// ---------------------------------------------------------
	form.addEventListener("submit", function(e) {
		// 변경사항 없으면 막기
		if (saveBtn && saveBtn.disabled) {
			e.preventDefault();
			return;
		}

		// 담당자/상태 필수
		if (assignedHandlerEl && !getV(assignedHandlerEl)) {
			e.preventDefault();
			alert("담당자를 선택해 주세요.");
			assignedHandlerEl.focus();
			return;
		}

		if (statusEl && !getV(statusEl)) {
			e.preventDefault();
			alert("AS 상태를 선택해 주세요.");
			statusEl.focus();
			return;
		}

		// 연락처 유효성(입력했을 때만 검사)
		if (onsiteContactEl) {
			const digits = onlyDigits(onsiteContactEl.value);
			if (digits && !isValidPhoneByDigits(digits)) {
				e.preventDefault();
				alert("현장연락처 형식이 올바르지 않습니다.\n예) 0311234567 / 01012345678");
				onsiteContactEl.focus();
				return;
			}
			onsiteContactEl.value = formatKoreanPhone(digits);
		}
	});

	// ---------------------------------------------------------
	// Delete (schedule + images + task)
	// ---------------------------------------------------------
	if (deleteBtn && deleteForm) {
		deleteBtn.addEventListener("click", function() {
			const ok = confirm("등록한 일정 및 이미지 모두 삭제됩니다.\n정말 삭제하시겠습니까?");
			if (!ok) return;
			deleteForm.submit();
		});
	}
});