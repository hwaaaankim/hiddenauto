document.addEventListener("DOMContentLoaded", () => {
	initAdminOrderItemDetailFilePreview();
	initAdminOrderItemDetailUploadedDelete();
	initAdminOrderItemDetailCompanyAutocomplete();
	initAdminOrderItemDetailImageViewer();
});

// ==============================
// 관리자 신규 업로드 미리보기
// ==============================
function initAdminOrderItemDetailFilePreview() {
	const input = document.getElementById("adminImages");
	const previewArea = document.getElementById("admin-order-item-detail-preview-area");

	if (!input || !previewArea) {
		return;
	}

	let fileList = [];

	input.addEventListener("change", (e) => {
		const newFiles = Array.from(e.target.files || []);
		if (newFiles.length === 0) {
			return;
		}

		fileList.push(...newFiles);
		updateFileInput();
		renderPreviews();
	});

	function updateFileInput() {
		const dataTransfer = new DataTransfer();
		fileList.forEach((file) => dataTransfer.items.add(file));
		input.files = dataTransfer.files;
	}

	function renderPreviews() {
		previewArea.innerHTML = "";

		fileList.forEach((file, index) => {
			const reader = new FileReader();

			reader.onload = (e) => {
				const card = document.createElement("div");
				card.className = "admin-order-item-detail-preview-card";

				card.innerHTML = `
					<button type="button" class="admin-order-item-detail-preview-remove" data-preview-index="${index}" aria-label="삭제">
						<i class="ri-close-line"></i>
					</button>
					<img src="${e.target.result}" alt="${escapeHtml(file.name)}">
					<div class="small text-muted text-break w-100">${escapeHtml(file.name)}</div>
				`;

				const removeButton = card.querySelector(".admin-order-item-detail-preview-remove");
				removeButton.addEventListener("click", () => {
					fileList.splice(index, 1);
					updateFileInput();
					renderPreviews();
				});

				previewArea.appendChild(card);
			};

			reader.readAsDataURL(file);
		});
	}
}

// ==============================
// 관리자 업로드 이미지 삭제
// ==============================
function initAdminOrderItemDetailUploadedDelete() {
	document.addEventListener("click", (event) => {
		const deleteButton = event.target.closest(".admin-order-item-detail-delete-uploaded-img");
		if (!deleteButton) {
			return;
		}

		const imageId = deleteButton.getAttribute("data-delete-id");
		if (!imageId) {
			return;
		}

		if (!confirm("이미지를 삭제하시겠습니까?")) {
			return;
		}

		fetch(`/management/order-image/delete/${imageId}`, {
			method: "DELETE"
		})
			.then((response) => {
				if (!response.ok) {
					throw new Error("이미지 삭제에 실패했습니다.");
				}
				location.reload();
			})
			.catch((error) => {
				console.error(error);
				alert("이미지 삭제 중 오류가 발생했습니다.");
			});
	});
}

// ==============================
// 대리점 자동완성 + 대표자명 자동반영
// - companyId(hidden) / requesterMemberId(hidden) 유지
// ==============================
function initAdminOrderItemDetailCompanyAutocomplete() {
	const companyInput = document.getElementById("admin-order-item-detail-company-search");
	const representativeInput = document.getElementById("admin-order-item-detail-representative-name");
	const dropdown = document.getElementById("admin-order-item-detail-company-dropdown");
	const companyIdInput = document.getElementById("companyId");
	const requesterMemberIdInput = document.getElementById("requesterMemberId");
	const companySource = document.getElementById("admin-order-item-detail-company-source");

	if (!companyInput || !representativeInput || !dropdown || !companyIdInput || !requesterMemberIdInput || !companySource) {
		return;
	}

	const companies = Array.from(companySource.querySelectorAll("[data-company-id]")).map((element) => ({
		id: String(element.dataset.companyId || "").trim(),
		companyName: String(element.dataset.companyName || "").trim(),
		representativeName: String(element.dataset.representativeName || "").trim()
	}));

	let selectedCompany = null;
	let filteredCompanies = [...companies];
	let activeIndex = -1;
	let mouseSelecting = false;

	function normalizeText(value) {
		return String(value || "").trim().toLowerCase();
	}

	function findCompanyById(companyId) {
		return companies.find((company) => company.id === String(companyId));
	}

	function isExactSelectedCompanyText() {
		return selectedCompany && companyInput.value.trim() === selectedCompany.companyName;
	}

	function openDropdown() {
		dropdown.classList.remove("d-none");
	}

	function closeDropdown() {
		dropdown.classList.add("d-none");
		activeIndex = -1;
	}

	function highlightActiveItem() {
		const items = dropdown.querySelectorAll(".admin-order-item-detail-company-item");
		items.forEach((item, index) => {
			item.classList.toggle("admin-order-item-detail-company-item-active", index === activeIndex);
			if (index === activeIndex) {
				item.scrollIntoView({ block: "nearest" });
			}
		});
	}

	function renderDropdown(items) {
		filteredCompanies = items;
		dropdown.innerHTML = "";

		if (!items.length) {
			dropdown.innerHTML = `<div class="admin-order-item-detail-company-empty">일치하는 대리점이 없습니다.</div>`;
			openDropdown();
			return;
		}

		items.forEach((company, index) => {
			const button = document.createElement("button");
			button.type = "button";
			button.className = "admin-order-item-detail-company-item";
			button.setAttribute("data-company-index", String(index));

			button.innerHTML = `
				<span class="admin-order-item-detail-company-name">${escapeHtml(company.companyName)}</span>
				<span class="admin-order-item-detail-company-rep">${escapeHtml(company.representativeName || "-")}</span>
			`;

			button.addEventListener("mouseenter", () => {
				activeIndex = index;
				highlightActiveItem();
			});

			button.addEventListener("mousedown", (event) => {
				event.preventDefault();
				mouseSelecting = true;
				selectCompany(company, true);
				setTimeout(() => {
					mouseSelecting = false;
				}, 0);
			});

			dropdown.appendChild(button);
		});

		openDropdown();
		highlightActiveItem();
	}

	function filterCompanies(keyword) {
		const normalizedKeyword = normalizeText(keyword);

		if (!normalizedKeyword) {
			return [...companies];
		}

		return companies.filter((company) => {
			return normalizeText(company.companyName).includes(normalizedKeyword)
				|| normalizeText(company.representativeName).includes(normalizedKeyword);
		});
	}

	function syncRequesterMember(company) {
		if (!company || !company.id) {
			requesterMemberIdInput.value = "";
			return;
		}

		fetch(`/api/companies/${company.id}/members`)
			.then((response) => {
				if (!response.ok) {
					throw new Error("대리점 멤버 조회 실패");
				}
				return response.json();
			})
			.then((members) => {
				if (!Array.isArray(members) || members.length === 0) {
					requesterMemberIdInput.value = "";
					return;
				}

				const repName = normalizeText(company.representativeName);
				const matchedRepresentative = members.find((member) => normalizeText(member.name) === repName);

				requesterMemberIdInput.value = String(
					matchedRepresentative?.id ?? members[0].id ?? ""
				);
			})
			.catch((error) => {
				console.error(error);
				requesterMemberIdInput.value = "";
				alert("대리점 담당자 정보를 불러오지 못했습니다.");
			});
	}

	function selectCompany(company, shouldSyncRequester) {
		selectedCompany = company;
		companyInput.value = company.companyName;
		representativeInput.value = company.representativeName || "";
		companyIdInput.value = company.id;
		closeDropdown();

		if (shouldSyncRequester) {
			syncRequesterMember(company);
		}
	}

	function restoreOrClearSelection() {
		const exactMatch = companies.find(
			(company) => normalizeText(company.companyName) === normalizeText(companyInput.value)
		);

		if (exactMatch) {
			selectCompany(exactMatch, true);
			return;
		}

		if (selectedCompany) {
			companyInput.value = selectedCompany.companyName;
			representativeInput.value = selectedCompany.representativeName || "";
			companyIdInput.value = selectedCompany.id;
			return;
		}

		companyInput.value = "";
		representativeInput.value = "";
		companyIdInput.value = "";
		requesterMemberIdInput.value = "";
	}

	const initialCompany = findCompanyById(companyIdInput.value);
	if (initialCompany) {
		selectCompany(initialCompany, false);
	}

	companyInput.addEventListener("focus", () => {
		activeIndex = -1;
		renderDropdown(filterCompanies(companyInput.value));
	});

	companyInput.addEventListener("input", () => {
		const value = companyInput.value || "";

		if (!isExactSelectedCompanyText()) {
			companyIdInput.value = "";
			requesterMemberIdInput.value = "";
			representativeInput.value = "";
		}

		activeIndex = -1;
		renderDropdown(filterCompanies(value));
	});

	companyInput.addEventListener("keydown", (event) => {
		const isDropdownOpen = !dropdown.classList.contains("d-none");

		if (event.key === "ArrowDown") {
			event.preventDefault();
			if (!isDropdownOpen) {
				renderDropdown(filterCompanies(companyInput.value));
			}
			if (filteredCompanies.length > 0) {
				activeIndex = Math.min(activeIndex + 1, filteredCompanies.length - 1);
				highlightActiveItem();
			}
			return;
		}

		if (event.key === "ArrowUp") {
			event.preventDefault();
			if (filteredCompanies.length > 0) {
				activeIndex = Math.max(activeIndex - 1, 0);
				highlightActiveItem();
			}
			return;
		}

		if (event.key === "Enter") {
			if (isDropdownOpen) {
				event.preventDefault();
				if (activeIndex >= 0 && filteredCompanies[activeIndex]) {
					selectCompany(filteredCompanies[activeIndex], true);
				} else {
					const exactMatch = companies.find(
						(company) => normalizeText(company.companyName) === normalizeText(companyInput.value)
					);
					if (exactMatch) {
						selectCompany(exactMatch, true);
					}
				}
			}
			return;
		}

		if (event.key === "Escape") {
			closeDropdown();
		}
	});

	companyInput.addEventListener("blur", () => {
		setTimeout(() => {
			if (mouseSelecting) {
				return;
			}
			restoreOrClearSelection();
			closeDropdown();
		}, 120);
	});

	document.addEventListener("click", (event) => {
		const companyWrap = event.target.closest(".admin-order-item-detail-company-wrap");
		if (!companyWrap) {
			closeDropdown();
		}
	});
}

// ==============================
// 이미지 크게보기 모달 + 같은 카테고리 이동
// ==============================
function initAdminOrderItemDetailImageViewer() {
	const modal = document.getElementById("admin-order-item-detail-image-modal");
	const modalImage = document.getElementById("admin-order-item-detail-modal-image");
	const modalGroup = document.getElementById("admin-order-item-detail-modal-group");
	const modalFilename = document.getElementById("admin-order-item-detail-modal-filename");
	const modalCount = document.getElementById("admin-order-item-detail-modal-count");
	const modalDownload = document.getElementById("admin-order-item-detail-modal-download");
	const modalClose = document.getElementById("admin-order-item-detail-modal-close");
	const modalPrev = document.getElementById("admin-order-item-detail-modal-prev");
	const modalNext = document.getElementById("admin-order-item-detail-modal-next");
	const modalOverlay = modal ? modal.querySelector(".admin-order-item-detail-modal-overlay") : null;
	const imageWrap = document.getElementById("admin-order-item-detail-modal-image-wrap");

	if (!modal || !modalImage || !modalGroup || !modalFilename || !modalCount || !modalDownload || !modalClose || !modalPrev || !modalNext || !modalOverlay || !imageWrap) {
		return;
	}

	const galleryItems = Array.from(document.querySelectorAll(".admin-order-item-detail-gallery-item"));
	const groups = {};

	galleryItems.forEach((item) => {
		const groupKey = item.dataset.galleryGroup;
		const groupLabel = item.dataset.galleryLabel;
		const imageUrl = item.dataset.imageUrl;
		const imageName = item.dataset.imageName;

		if (!groupKey || !imageUrl) {
			return;
		}

		if (!groups[groupKey]) {
			groups[groupKey] = [];
		}

		const imageData = {
			groupKey,
			groupLabel: groupLabel || groupKey,
			imageUrl,
			imageName: imageName || "image"
		};

		const itemIndex = groups[groupKey].push(imageData) - 1;
		item.dataset.galleryRuntimeIndex = String(itemIndex);

		const viewButton = item.querySelector(".admin-order-item-detail-view-image-btn");
		const thumb = item.querySelector(".admin-order-item-detail-gallery-thumb img");

		if (viewButton) {
			viewButton.addEventListener("click", () => openViewer(groupKey, itemIndex));
		}

		if (thumb) {
			thumb.style.cursor = "zoom-in";
			thumb.addEventListener("click", () => openViewer(groupKey, itemIndex));
		}
	});

	const state = {
		groupKey: null,
		index: 0
	};

	function updateViewer() {
		if (!state.groupKey || !groups[state.groupKey] || !groups[state.groupKey].length) {
			return;
		}

		const currentGroup = groups[state.groupKey];
		const currentImage = currentGroup[state.index];

		modalImage.src = currentImage.imageUrl;
		modalImage.alt = currentImage.imageName;
		modalGroup.textContent = currentImage.groupLabel;
		modalFilename.textContent = currentImage.imageName;
		modalCount.textContent = `${state.index + 1} / ${currentGroup.length}`;
		modalDownload.href = currentImage.imageUrl;
		modalDownload.setAttribute("download", currentImage.imageName);

		const disabled = currentGroup.length <= 1;
		modalPrev.disabled = disabled;
		modalNext.disabled = disabled;
	}

	function openViewer(groupKey, index) {
		if (!groups[groupKey] || !groups[groupKey][index]) {
			return;
		}

		state.groupKey = groupKey;
		state.index = index;
		updateViewer();

		modal.classList.add("admin-order-item-detail-modal-show");
		modal.setAttribute("aria-hidden", "false");
		document.body.classList.add("admin-order-item-detail-modal-open");
	}

	function closeViewer() {
		modal.classList.remove("admin-order-item-detail-modal-show");
		modal.setAttribute("aria-hidden", "true");
		document.body.classList.remove("admin-order-item-detail-modal-open");
	}

	function moveViewer(step) {
		if (!state.groupKey || !groups[state.groupKey] || groups[state.groupKey].length <= 1) {
			return;
		}

		const currentGroup = groups[state.groupKey];
		state.index = (state.index + step + currentGroup.length) % currentGroup.length;
		updateViewer();
	}

	modalClose.addEventListener("click", closeViewer);
	modalOverlay.addEventListener("click", closeViewer);
	modalPrev.addEventListener("click", () => moveViewer(-1));
	modalNext.addEventListener("click", () => moveViewer(1));

	document.addEventListener("keydown", (event) => {
		if (!modal.classList.contains("admin-order-item-detail-modal-show")) {
			return;
		}

		if (event.key === "Escape") {
			closeViewer();
			return;
		}

		if (event.key === "ArrowLeft") {
			moveViewer(-1);
			return;
		}

		if (event.key === "ArrowRight") {
			moveViewer(1);
		}
	});

	let touchStartX = 0;
	let touchEndX = 0;

	imageWrap.addEventListener("touchstart", (event) => {
		touchStartX = event.changedTouches[0].clientX;
	});

	imageWrap.addEventListener("touchend", (event) => {
		touchEndX = event.changedTouches[0].clientX;
		const distance = touchEndX - touchStartX;

		if (Math.abs(distance) < 50) {
			return;
		}

		if (distance > 0) {
			moveViewer(-1);
		} else {
			moveViewer(1);
		}
	});
}

// ==============================
// 공통 유틸
// ==============================
function escapeHtml(value) {
	return String(value ?? "")
		.replaceAll("&", "&amp;")
		.replaceAll("<", "&lt;")
		.replaceAll(">", "&gt;")
		.replaceAll('"', "&quot;")
		.replaceAll("'", "&#39;");
}