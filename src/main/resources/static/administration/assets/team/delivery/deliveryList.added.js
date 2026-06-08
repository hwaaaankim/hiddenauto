/* deliveryList.added.js */
(function() {
	'use strict';

	const saveButton = document.getElementById("saveOrderIndexBtn");
	const reorderByTaskBtn = document.getElementById("reorderByTaskBtn");

	const pendingListEl = document.getElementById("pendingList");
	const doneListEl = document.getElementById("doneList");
	const otherListEl = document.getElementById("otherList");

	const excelBtn = document.getElementById("delivery-list-added-excelBtn");

	const bulkHandlerOpenBtn = document.getElementById("delivery-list-added-open-bulk-handler-modal");
	const floatingHandlerBtn = document.getElementById("delivery-list-added-floating-handler-btn");
	const selectedCountBadgeEl = document.getElementById("delivery-list-added-selected-count-badge");
	const floatingSelectedCountBadgeEl = document.getElementById("delivery-list-added-floating-selected-count-badge");

	// Modal elements
	const modalEl = document.getElementById("delivery-list-added-modal");
	const modalTitleEl = document.getElementById("delivery-list-added-modal-title");
	const skeletonEl = document.getElementById("delivery-list-added-modal-skeleton");
	const bodyEl = document.getElementById("delivery-list-added-modal-body");

	const companyNameEl = document.getElementById("delivery-list-added-companyName");
	const requesterNameEl = document.getElementById("delivery-list-added-requesterName");
	const companyContactEl = document.getElementById("delivery-list-added-companyContact");
	const orderAddressEl = document.getElementById("delivery-list-added-orderAddress");
	const productTextEl = document.getElementById("delivery-list-added-productText");
	const ordererPhoneEl = document.getElementById("delivery-list-added-ordererPhone");

	// existing images
	const existingWrapEl = document.getElementById("delivery-list-added-existing-wrap");
	const existingListEl = document.getElementById("delivery-list-added-existing-list");

	// upload
	const uploadWrapEl = document.getElementById("delivery-list-added-upload-wrap");

	const targetInfoEl = document.getElementById("delivery-list-added-target-info");
	const targetCountEl = document.getElementById("delivery-list-added-target-count");
	const requiredImageCountEl = document.getElementById("delivery-list-added-required-image-count");
	const targetHelpEl = document.getElementById("delivery-list-added-target-help");

	const btnSubmitSingleEl = document.getElementById("delivery-list-added-btn-submit-single");
	const btnSubmitBulkEl = document.getElementById("delivery-list-added-btn-submit-bulk");

	const btnCamera = document.getElementById("delivery-list-added-btn-camera");
	const btnGallery = document.getElementById("delivery-list-added-btn-gallery");
	const inputCamera = document.getElementById("delivery-list-added-file-camera");
	const inputGallery = document.getElementById("delivery-list-added-file-gallery");
	const thumbListEl = document.getElementById("delivery-list-added-thumb-list");

	// handler change modal
	const handlerModalEl = document.getElementById("delivery-list-added-handler-modal");
	const handlerSelectEl = document.getElementById("delivery-list-added-handler-select");
	const handlerSubmitBtn = document.getElementById("delivery-list-added-handler-submit");
	const handlerSelectedCountEl = document.getElementById("delivery-list-added-handler-selected-count");

	// CSRF
	const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
	const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

	let modalInstance = null;
	let currentOrderId = null;
	let currentMode = "detail"; // "complete" | "detail"

	let selectedFiles = [];
	let selectedObjectUrls = [];

	let requiredImageCount = 0;
	let targetCount = 0;
	let targetOrderIds = [];
	let targetPreviewLoaded = false;
	let targetPreviewError = "";

	function getDeliveryHandlerId() {
		return document.getElementById("deliveryHandlerId")?.value || "";
	}

	function getDeliveryDate() {
		return document.getElementById("deliveryDate")?.value || "";
	}

	function getOrderIdsFromList(listEl) {
		if (!listEl) return [];

		return Array.from(listEl.querySelectorAll(".draggable-item"))
			.map(el => Number(el.getAttribute("data-order-id")))
			.filter(n => Number.isFinite(n) && n > 0);
	}

	function getAllOrderIdsInDomOrder() {
		const pending = getOrderIdsFromList(pendingListEl);
		const done = getOrderIdsFromList(doneListEl);
		const other = getOrderIdsFromList(otherListEl);

		return pending.concat(done).concat(other);
	}

	function getPendingOrderIdsInDomOrder() {
		return getOrderIdsFromList(pendingListEl);
	}

	function setElementText(el, text) {
		if (el) {
			el.textContent = text;
		}
	}

	function setElementDisplay(el, displayValue) {
		if (el) {
			el.style.display = displayValue;
		}
	}

	function getErrorMessageFromResponse(res) {
		return res.clone().json()
			.then(data => data?.message || "")
			.catch(() => res.text().catch(() => ""));
	}

	function getHandlerCheckboxes() {
		return Array.from(document.querySelectorAll(".delivery-list-added-handler-check"));
	}

	function getSelectedHandlerOrderIds() {
		return getHandlerCheckboxes()
			.filter(chk => chk.checked && !chk.disabled)
			.map(chk => Number(chk.getAttribute("data-order-id")))
			.filter(id => Number.isFinite(id) && id > 0)
			.filter((id, idx, arr) => arr.indexOf(id) === idx);
	}

	function setBulkHandlerButtonCount(count) {
		if (selectedCountBadgeEl) selectedCountBadgeEl.textContent = String(count);
		if (floatingSelectedCountBadgeEl) floatingSelectedCountBadgeEl.textContent = String(count);
		if (handlerSelectedCountEl) handlerSelectedCountEl.textContent = String(count);

		if (bulkHandlerOpenBtn) bulkHandlerOpenBtn.disabled = count < 1;
	}

	function isElementFullyHiddenFromViewport(el) {
		if (!el) return true;

		const rect = el.getBoundingClientRect();
		const viewHeight = window.innerHeight || document.documentElement.clientHeight;
		const viewWidth = window.innerWidth || document.documentElement.clientWidth;

		return rect.bottom < 0 || rect.top > viewHeight || rect.right < 0 || rect.left > viewWidth;
	}

	function updateFloatingHandlerButtonVisibility() {
		if (!floatingHandlerBtn) return;

		const selectedCount = getSelectedHandlerOrderIds().length;
		const shouldShow = selectedCount > 0 && isElementFullyHiddenFromViewport(bulkHandlerOpenBtn);

		floatingHandlerBtn.classList.toggle("is-visible", shouldShow);
	}

	function updateHandlerSelectionUi() {
		const selectedCount = getSelectedHandlerOrderIds().length;
		setBulkHandlerButtonCount(selectedCount);
		updateFloatingHandlerButtonVisibility();
	}

	/* =========================
	   기존 그룹 스타일 로직(유지)
	   ========================= */
	function applyTaskGroupStyles(listEl) {
		const items = Array.from(listEl.querySelectorAll(".draggable-item"));
		let lastTaskId = null;
		let alt = 0;

		for (const el of items) {
			el.classList.remove("delivery-list-added-task-group-alt-0");
			el.classList.remove("delivery-list-added-task-group-alt-1");
			el.classList.remove("delivery-list-added-task-group-break");

			const taskId = el.getAttribute("data-task-id") || "0";

			if (lastTaskId !== null && taskId !== lastTaskId) {
				alt = (alt === 0 ? 1 : 0);
				el.classList.add("delivery-list-added-task-group-break");
			}

			el.classList.add(alt === 0 ? "delivery-list-added-task-group-alt-0" : "delivery-list-added-task-group-alt-1");
			lastTaskId = taskId;
		}
	}

	function applyAllTaskGroupStyles() {
		if (pendingListEl) applyTaskGroupStyles(pendingListEl);
		if (doneListEl) applyTaskGroupStyles(doneListEl);
		if (otherListEl) applyTaskGroupStyles(otherListEl);
	}

	document.addEventListener("DOMContentLoaded", function() {
		applyAllTaskGroupStyles();
		updateHandlerSelectionUi();
	});

	document.addEventListener("change", (e) => {
		if (!e.target || !e.target.matches(".delivery-list-added-handler-check")) return;
		updateHandlerSelectionUi();
	});

	window.addEventListener("scroll", updateFloatingHandlerButtonVisibility, { passive: true });
	window.addEventListener("resize", updateFloatingHandlerButtonVisibility);

	/* =========================
	   Sortable
	   ========================= */
	if (pendingListEl && typeof Sortable !== "undefined") {
		new Sortable(pendingListEl, {
			group: "pending",
			animation: 150,
			fallbackOnBody: true,
			swapThreshold: 0.65,
			handle: ".delivery-list-added-drag-handle",
			filter: ".action-btn, .delivery-list-added-open-complete-modal, .delivery-list-added-open-detail-modal, .delivery-list-added-handler-check, .delivery-list-added-check-wrap",
			preventOnFilter: false,
			onEnd: () => {
				if (saveButton) saveButton.disabled = false;
				applyAllTaskGroupStyles();
			}
		});
	}

	/* =========================
	   업체별정렬
	   ========================= */
	if (reorderByTaskBtn) {
		reorderByTaskBtn.addEventListener("click", async () => {
			const deliveryHandlerId = getDeliveryHandlerId();
			const deliveryDate = getDeliveryDate();

			if (!deliveryHandlerId) return alert("담당자 정보가 없습니다.");
			if (!deliveryDate) return alert("날짜를 선택해주세요.");

			const pendingOrderIds = getPendingOrderIdsInDomOrder();
			if (pendingOrderIds.length <= 1) return alert("업체별정렬할 직배송/현장배송 항목이 없습니다.");

			const headers = { "Content-Type": "application/json" };
			if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

			try {
				const res = await fetch("/team/reorderByTask", {
					method: "POST",
					headers,
					body: JSON.stringify({
						deliveryHandlerId: Number(deliveryHandlerId),
						deliveryDate: deliveryDate,
						pendingOrderIds: pendingOrderIds
					})
				});

				if (!res.ok) {
					const message = await getErrorMessageFromResponse(res);
					return alert("업체별정렬 실패\n" + (message || ("HTTP " + res.status)));
				}

				const data = await res.json();
				const reordered = Array.isArray(data?.pendingOrderIds) ? data.pendingOrderIds : [];

				if (reordered.length !== pendingOrderIds.length) {
					return alert("업체별정렬 결과가 올바르지 않습니다.(개수 불일치)");
				}

				const elById = new Map();
				Array.from(pendingListEl.querySelectorAll(".draggable-item")).forEach(el => {
					const id = Number(el.getAttribute("data-order-id"));
					if (Number.isFinite(id)) elById.set(id, el);
				});

				for (const id of reordered) {
					const el = elById.get(Number(id));
					if (el) pendingListEl.appendChild(el);
				}

				if (saveButton) saveButton.disabled = false;
				applyAllTaskGroupStyles();

			} catch (err) {
				console.error(err);
				alert("업체별정렬 중 오류가 발생했습니다.");
			}
		});
	}

	/* =========================
	   순서 저장
	   ========================= */
	if (saveButton) {
		saveButton.addEventListener("click", async () => {
			const ok = confirm("업체별(동일배송지별)로 재배치 됩니다. 진행하시겠습니까? 진행하시려면 '순서저장' 버튼을 클릭 해 주세요.");
			if (!ok) return;

			const deliveryHandlerId = getDeliveryHandlerId();
			const deliveryDate = getDeliveryDate();

			if (!deliveryHandlerId) return alert("담당자 정보가 없습니다.");
			if (!deliveryDate) return alert("날짜를 선택해주세요.");

			const pendingItems = Array.from(pendingListEl.querySelectorAll(".draggable-item"));

			if (pendingItems.length === 0) {
				return alert("순서 저장할 직배송/현장배송 항목이 없습니다.");
			}

			const orderedIds = pendingItems.map((el, idx) => ({
				orderId: Number(el.getAttribute("data-order-id")),
				orderIndex: idx + 1
			}));

			const headers = { "Content-Type": "application/json" };
			if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

			try {
				const res = await fetch("/team/updateOrderIndex", {
					method: "POST",
					headers,
					body: JSON.stringify({
						deliveryHandlerId: Number(deliveryHandlerId),
						deliveryDate: deliveryDate,
						orderList: orderedIds
					})
				});

				if (!res.ok) {
					const message = await getErrorMessageFromResponse(res);
					return alert("순서 저장 실패\n" + (message || ("HTTP " + res.status)));
				}

				alert("순서가 저장되었습니다.");
				saveButton.disabled = true;

			} catch (err) {
				console.error(err);
				alert("순서 저장 중 오류가 발생했습니다.");
			}
		});
	}

	/* =========================
	   엑셀출력
	   ========================= */
	if (excelBtn) {
		excelBtn.addEventListener("click", async () => {
			const deliveryHandlerId = getDeliveryHandlerId();
			const deliveryDate = getDeliveryDate();

			if (!deliveryHandlerId) return alert("담당자 정보가 없습니다.");
			if (!deliveryDate) return alert("날짜를 선택해주세요.");

			const orderedOrderIds = getAllOrderIdsInDomOrder();
			if (orderedOrderIds.length === 0) return alert("출력할 데이터가 없습니다.");

			const headers = { "Content-Type": "application/json" };
			if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

			try {
				const res = await fetch("/team/deliveryExcel", {
					method: "POST",
					headers,
					body: JSON.stringify({
						deliveryHandlerId: Number(deliveryHandlerId),
						deliveryDate: deliveryDate,
						orderedOrderIds: orderedOrderIds
					})
				});

				if (!res.ok) {
					const message = await getErrorMessageFromResponse(res);
					return alert("엑셀 출력 실패\n" + (message || ("HTTP " + res.status)));
				}

				const blob = await res.blob();
				const url = window.URL.createObjectURL(blob);

				const a = document.createElement("a");
				a.href = url;
				a.download = `배송리스트_${deliveryDate}.xlsx`;
				document.body.appendChild(a);
				a.click();
				a.remove();

				window.URL.revokeObjectURL(url);

			} catch (err) {
				console.error(err);
				alert("엑셀 출력 중 오류가 발생했습니다.");
			}
		});
	}

	/* =========================
	   모달 상태 초기화
	   ========================= */
	function resetModalUi() {
		currentOrderId = null;
		currentMode = "detail";

		setElementText(companyNameEl, "-");
		setElementText(requesterNameEl, "-");
		setElementText(companyContactEl, "-");
		setElementText(orderAddressEl, "-");
		setElementText(productTextEl, "-");
		setElementText(ordererPhoneEl, "-");

		setElementDisplay(existingWrapEl, "none");
		if (existingListEl) existingListEl.innerHTML = "";

		setElementDisplay(skeletonEl, "");
		setElementDisplay(bodyEl, "none");

		setElementDisplay(uploadWrapEl, "none");

		setElementDisplay(btnSubmitSingleEl, "none");
		setElementDisplay(btnSubmitBulkEl, "none");

		if (btnSubmitSingleEl) btnSubmitSingleEl.disabled = true;
		if (btnSubmitBulkEl) btnSubmitBulkEl.disabled = true;

		cleanupThumbs();
		selectedFiles = [];

		requiredImageCount = 0;
		targetCount = 0;
		targetOrderIds = [];
		targetPreviewLoaded = false;
		targetPreviewError = "";

		setElementDisplay(targetInfoEl, "none");
		setElementText(targetCountEl, "0");
		setElementText(requiredImageCountEl, "0");
		setElementText(targetHelpEl, "동일 업체 + 동일 주소 + 선택 주문과 같은 배송일 기준으로 계산됩니다. 이미지는 1장 이상이면 처리할 수 있습니다.");
	}

	function cleanupThumbs() {
		for (const u of selectedObjectUrls) {
			try {
				URL.revokeObjectURL(u);
			} catch (e) { }
		}

		selectedObjectUrls = [];

		if (thumbListEl) thumbListEl.innerHTML = "";
		if (inputCamera) inputCamera.value = "";
		if (inputGallery) inputGallery.value = "";
	}

	function updateSubmitState() {
		if (currentMode !== "complete") {
			if (btnSubmitSingleEl) btnSubmitSingleEl.disabled = true;
			if (btnSubmitBulkEl) btnSubmitBulkEl.disabled = true;
			return;
		}

		const fileCount = selectedFiles.length;

		// 단건 완료: 이미지 1장 이상이면 가능
		if (btnSubmitSingleEl) {
			btnSubmitSingleEl.disabled = (fileCount < 1);
		}

		// 일괄 완료: 대상 2건 이상 + 이미지 1장 이상이면 가능
		if (btnSubmitBulkEl) {
			btnSubmitBulkEl.disabled = !(
				targetPreviewLoaded &&
				targetCount > 1 &&
				fileCount >= 1
			);
		}

		if (!targetHelpEl) return;

		if (targetPreviewError) {
			targetHelpEl.textContent =
				`동일주소 일괄완료 대상 조회 실패: ${targetPreviewError} / 단건 완료는 이미지 1장 이상이면 가능합니다.`;
			return;
		}

		if (!targetPreviewLoaded) {
			targetHelpEl.textContent =
				`동일주소 일괄완료 대상을 확인하는 중입니다. 현재 이미지 ${fileCount}장 첨부됨.`;
			return;
		}

		if (targetCount <= 1) {
			targetHelpEl.textContent =
				`동일주소 일괄완료 대상은 ${targetCount}건입니다. 이미지 1장 이상으로 이 주문만 완료할 수 있습니다.`;
			return;
		}

		targetHelpEl.textContent =
			`현재 이미지 ${fileCount}장 첨부됨 / 이 주문만 완료와 동일주소 일괄완료 모두 이미지 1장 이상이면 가능합니다.`;
	}

	function setMode(mode) {
		currentMode = mode;

		if (mode === "complete") {
			setElementText(modalTitleEl, "배송완료 처리");
			setElementDisplay(uploadWrapEl, "");

			setElementDisplay(btnSubmitSingleEl, "");
			setElementDisplay(btnSubmitBulkEl, "");

			if (btnSubmitSingleEl) btnSubmitSingleEl.disabled = true;
			if (btnSubmitBulkEl) btnSubmitBulkEl.disabled = true;

			setElementDisplay(targetInfoEl, "");
			updateSubmitState();

		} else {
			setElementText(modalTitleEl, "배송상세");
			setElementDisplay(uploadWrapEl, "none");

			setElementDisplay(btnSubmitSingleEl, "none");
			setElementDisplay(btnSubmitBulkEl, "none");

			if (btnSubmitSingleEl) btnSubmitSingleEl.disabled = true;
			if (btnSubmitBulkEl) btnSubmitBulkEl.disabled = true;
		}
	}

	/* =========================
	   상세/preview 조회
	   ========================= */
	async function loadSummary(orderId) {
		const headers = {};
		if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

		const res = await fetch(`/team/deliveryOrderSummary/${orderId}`, {
			method: "GET",
			headers
		});

		if (!res.ok) {
			const message = await getErrorMessageFromResponse(res);
			throw new Error(message || ("HTTP " + res.status));
		}

		return await res.json();
	}

	async function loadSameAddressPreview(orderId) {
		const headers = {};
		if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

		const res = await fetch(`/team/deliveryStatus/${orderId}/same-address/preview`, {
			method: "GET",
			headers
		});

		if (!res.ok) {
			const message = await getErrorMessageFromResponse(res);
			throw new Error(message || ("HTTP " + res.status));
		}

		return await res.json();
	}

	function renderExistingImages(imageUrls) {
		const urls = Array.isArray(imageUrls)
			? imageUrls.filter(u => typeof u === "string" && u.length > 0)
			: [];

		if (urls.length === 0) {
			setElementDisplay(existingWrapEl, "none");
			if (existingListEl) existingListEl.innerHTML = "";
			return;
		}

		if (!existingListEl) return;

		existingListEl.innerHTML = "";

		for (const url of urls) {
			const item = document.createElement("div");
			item.className = "delivery-list-added-existing-item";

			const a = document.createElement("a");
			a.href = url;
			a.target = "_blank";
			a.rel = "noopener noreferrer";

			const img = document.createElement("img");
			img.src = url;
			img.alt = "배송 증빙 이미지";

			a.appendChild(img);
			item.appendChild(a);
			existingListEl.appendChild(item);
		}

		setElementDisplay(existingWrapEl, "");
	}

	function renderSummary(data) {
		setElementText(companyNameEl, data?.companyName || "-");
		setElementText(requesterNameEl, data?.requesterName || "-");
		setElementText(companyContactEl, data?.companyContact || "-");
		setElementText(orderAddressEl, data?.orderAddress || "-");
		setElementText(productTextEl, data?.productText || "-");
		setElementText(ordererPhoneEl, data?.ordererPhone || "-");

		renderExistingImages(data?.deliveryImageUrls);

		setElementDisplay(skeletonEl, "none");
		setElementDisplay(bodyEl, "");
	}

	function renderSameAddressPreview(data) {
		targetPreviewLoaded = true;
		targetPreviewError = "";

		targetCount = Number(data?.targetCount || 0);
		requiredImageCount = Number(data?.requiredImageCount || targetCount || 0);
		targetOrderIds = Array.isArray(data?.targetOrderIds) ? data.targetOrderIds : [];

		setElementDisplay(targetInfoEl, "");
		setElementText(targetCountEl, String(targetCount));
		setElementText(requiredImageCountEl, String(requiredImageCount));

		const deliveryDateText = data?.deliveryDate ? ` / 기준 배송일: ${data.deliveryDate}` : "";

		if (targetHelpEl) {
			targetHelpEl.textContent =
				`동일 업체 + 동일 주소 + 동일 배송일 기준입니다${deliveryDateText}. ` +
				`현재 이미지 ${selectedFiles.length}장 첨부됨 / 일괄완료는 이미지 1장 이상이면 가능합니다.`;
		}

		updateSubmitState();
	}

	function markPreviewFailed(error) {
		targetPreviewLoaded = false;
		targetPreviewError = error?.message || "대상 조회 실패";

		targetCount = 0;
		requiredImageCount = 0;
		targetOrderIds = [];

		setElementDisplay(targetInfoEl, "");
		setElementText(targetCountEl, "0");
		setElementText(requiredImageCountEl, "0");

		updateSubmitState();
	}

	function openModal(orderId, mode) {
		resetModalUi();

		currentOrderId = orderId;
		setMode(mode);

		if (!modalInstance) {
			modalInstance = new bootstrap.Modal(modalEl, { backdrop: "static" });
		}

		modalInstance.show();

		(async () => {
			try {
				const data = await loadSummary(orderId);
				renderSummary(data);
			} catch (e) {
				console.error(e);
				alert("상세 정보를 불러오지 못했습니다.\n" + (e?.message || ""));
				modalInstance.hide();
				return;
			}

			if (mode === "complete") {
				try {
					const preview = await loadSameAddressPreview(orderId);
					renderSameAddressPreview(preview);
				} catch (e) {
					console.error(e);
					markPreviewFailed(e);
				}
			}
		})();
	}

	/* =========================
	   카드/버튼 클릭
	   ========================= */
	function shouldIgnoreCardClick(target) {
		if (!target) return false;

		return !!target.closest(
			".delivery-list-added-drag-handle," +
			".delivery-list-added-check-wrap," +
			".delivery-list-added-handler-check," +
			".action-btn," +
			".delivery-list-added-open-complete-modal," +
			".delivery-list-added-open-detail-modal"
		);
	}

	function openByCard(elCard) {
		const orderId = elCard?.getAttribute("data-order-id");
		const status = elCard?.getAttribute("data-order-status");
		const section = elCard?.getAttribute("data-delivery-section");

		if (!orderId) return;

		if (section === "actionable" && (status === "PRODUCTION_DONE" || status === "DISPATCH_DONE")) {
			openModal(orderId, "complete");
		} else {
			openModal(orderId, "detail");
		}
	}

	[pendingListEl, doneListEl, otherListEl].forEach(listEl => {
		if (!listEl) return;

		listEl.addEventListener("click", (e) => {
			if (shouldIgnoreCardClick(e.target)) return;

			const card = e.target.closest(".delivery-list-added-card-clickable");
			if (!card) return;

			openByCard(card);
		});
	});

	document.addEventListener("click", (e) => {
		const btn = e.target.closest(".delivery-list-added-open-complete-modal");
		if (!btn) return;

		const orderId = btn.getAttribute("data-order-id");
		if (!orderId) return;

		openModal(orderId, "complete");
	});

	document.addEventListener("click", (e) => {
		const btn = e.target.closest(".delivery-list-added-open-detail-modal");
		if (!btn) return;

		const orderId = btn.getAttribute("data-order-id");
		if (!orderId) return;

		openModal(orderId, "detail");
	});

	if (modalEl) {
		modalEl.addEventListener("hidden.bs.modal", () => {
			resetModalUi();
		});
	}

	/* =========================
	   이미지 첨부
	   ========================= */
	if (btnCamera && inputCamera) {
		btnCamera.addEventListener("click", () => {
			inputCamera.click();
		});
	}

	if (btnGallery && inputGallery) {
		btnGallery.addEventListener("click", () => {
			inputGallery.click();
		});
	}

	function addFiles(fileList) {
		const files = Array.from(fileList || [])
			.filter(f => f && f.type && f.type.startsWith("image/"));

		if (files.length === 0) return;

		for (const f of files) {
			selectedFiles.push(f);
		}

		renderThumbs();
		updateSubmitState();
	}

	if (inputCamera) {
		inputCamera.addEventListener("change", (e) => {
			addFiles(e.target.files);
			inputCamera.value = "";
		});
	}

	if (inputGallery) {
		inputGallery.addEventListener("change", (e) => {
			addFiles(e.target.files);
			inputGallery.value = "";
		});
	}

	function renderThumbs() {
		cleanupThumbs();

		if (!thumbListEl) return;

		selectedFiles.forEach((file, idx) => {
			const url = URL.createObjectURL(file);
			selectedObjectUrls.push(url);

			const item = document.createElement("div");
			item.className = "delivery-list-added-thumb-item";

			const img = document.createElement("img");
			img.src = url;
			img.alt = "첨부이미지";

			const rm = document.createElement("button");
			rm.type = "button";
			rm.className = "delivery-list-added-thumb-remove";
			rm.textContent = "×";
			rm.addEventListener("click", () => {
				selectedFiles.splice(idx, 1);
				renderThumbs();
				updateSubmitState();
			});

			item.appendChild(img);
			item.appendChild(rm);
			thumbListEl.appendChild(item);
		});
	}

	/* =========================
	   배송완료 - 단건
	   ========================= */
	if (btnSubmitSingleEl) {
		btnSubmitSingleEl.addEventListener("click", async () => {
			if (currentMode !== "complete") return;
			if (!currentOrderId) return;

			if (selectedFiles.length < 1) {
				alert("이 주문만 완료하려면 이미지가 1장 이상 필요합니다.");
				return;
			}

			const ok = confirm("이 주문만 배송완료 처리하시겠습니까?");
			if (!ok) return;

			const headers = {};
			if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
			headers["X-Requested-With"] = "fetch";

			try {
				const form = new FormData();
				for (const f of selectedFiles) {
					form.append("files", f);
				}

				const res = await fetch(`/team/deliveryStatus/${currentOrderId}?status=DELIVERY_DONE`, {
					method: "POST",
					headers,
					body: form
				});

				if (!res.ok) {
					const message = await getErrorMessageFromResponse(res);
					return alert("배송완료 실패\n" + (message || ("HTTP " + res.status)));
				}

				alert("1건 배송완료 처리되었습니다.");
				modalInstance.hide();
				location.reload();

			} catch (err) {
				console.error(err);
				alert("배송완료 처리 중 오류가 발생했습니다.");
			}
		});
	}

	/* =========================
	   배송완료 - 동일주소 일괄
	   ========================= */
	if (btnSubmitBulkEl) {
		btnSubmitBulkEl.addEventListener("click", async () => {
			if (currentMode !== "complete") return;
			if (!currentOrderId) return;

			if (!targetPreviewLoaded) {
				alert("동일주소 일괄완료 대상을 아직 확인하지 못했습니다.");
				return;
			}

			if (targetCount <= 1) {
				alert("일괄완료 대상이 없습니다. 이 주문만 배송완료를 사용해주세요.");
				return;
			}

			if (selectedFiles.length < 1) {
				alert("동일주소 일괄완료를 처리하려면 이미지가 1장 이상 필요합니다.");
				return;
			}

			const ok = confirm(
				`동일 업체 + 동일 주소 + 동일 배송일 기준 ${targetCount}건을 일괄 배송완료 처리하시겠습니까?\n\n` +
				`업로드 이미지 ${selectedFiles.length}장이 완료 대상 주문에 증빙 이미지로 저장됩니다.`
			);

			if (!ok) return;

			const headers = {};
			if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
			headers["X-Requested-With"] = "fetch";

			try {
				const form = new FormData();

				for (const f of selectedFiles) {
					form.append("files", f);
				}

				const res = await fetch(`/team/deliveryStatus/${currentOrderId}/same-address?status=DELIVERY_DONE`, {
					method: "POST",
					headers,
					body: form
				});

				if (!res.ok) {
					const message = await getErrorMessageFromResponse(res);
					return alert("일괄 배송완료 실패\n" + (message || ("HTTP " + res.status)));
				}

				let data = null;
				try {
					data = await res.json();
				} catch (e) {
					data = null;
				}

				if (data && typeof data.completedCount !== "undefined") {
					alert(`${data.completedCount}건 배송완료 처리되었습니다.`);
				} else {
					alert("일괄 배송완료 처리되었습니다.");
				}

				modalInstance.hide();
				location.reload();

			} catch (err) {
				console.error(err);
				alert("일괄 배송완료 처리 중 오류가 발생했습니다.");
			}
		});
	}

	/* =========================
	   담당자 일괄 변경
	   ========================= */
	let handlerModalInstance = null;

	function openBulkHandlerModal() {
		const selectedOrderIds = getSelectedHandlerOrderIds();

		if (selectedOrderIds.length < 1) {
			alert("담당자를 변경할 주문을 1개 이상 선택해주세요.");
			return;
		}

		if (!handlerModalEl || !handlerSelectEl) {
			alert("담당자 변경 모달이 준비되지 않았습니다.");
			return;
		}

		if (handlerSelectedCountEl) {
			handlerSelectedCountEl.textContent = String(selectedOrderIds.length);
		}

		handlerSelectEl.value = "";

		if (!handlerModalInstance) {
			handlerModalInstance = new bootstrap.Modal(handlerModalEl, { backdrop: "static" });
		}

		handlerModalInstance.show();
	}

	if (bulkHandlerOpenBtn) {
		bulkHandlerOpenBtn.addEventListener("click", openBulkHandlerModal);
	}

	if (floatingHandlerBtn) {
		floatingHandlerBtn.addEventListener("click", openBulkHandlerModal);
	}

	if (handlerSubmitBtn) {
		handlerSubmitBtn.addEventListener("click", async () => {
			const selectedOrderIds = getSelectedHandlerOrderIds();
			const newHandlerId = handlerSelectEl?.value || "";

			if (selectedOrderIds.length < 1) {
				alert("담당자를 변경할 주문을 1개 이상 선택해주세요.");
				return;
			}

			if (!newHandlerId) {
				alert("변경할 담당자를 선택해주세요.");
				return;
			}

			const ok = confirm(
				`선택한 ${selectedOrderIds.length}건의 담당자를 변경하시겠습니까?\n\n` +
				`변경 후 현재 목록에서 사라지고, 선택한 담당자의 같은 배송일 목록 마지막 순서로 이동합니다.`
			);

			if (!ok) return;

			const headers = { "Content-Type": "application/json" };
			if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

			try {
				handlerSubmitBtn.disabled = true;

				const res = await fetch("/team/deliveryHandler/bulk", {
					method: "POST",
					headers,
					body: JSON.stringify({
						orderIds: selectedOrderIds,
						newHandlerId: Number(newHandlerId)
					})
				});

				if (!res.ok) {
					const message = await getErrorMessageFromResponse(res);
					return alert("담당자 변경 실패\n" + (message || ("HTTP " + res.status)));
				}

				let data = null;
				try {
					data = await res.json();
				} catch (e) {
					data = null;
				}

				const changedCount = data && typeof data.changedCount !== "undefined"
					? Number(data.changedCount)
					: selectedOrderIds.length;

				if (changedCount > 0) {
					alert(`${changedCount}건의 담당자가 변경되었습니다.`);
				} else {
					alert("변경된 주문이 없습니다. 현재 담당자와 동일한 담당자를 선택했는지 확인해주세요.");
				}

				handlerModalInstance.hide();
				location.reload();

			} catch (err) {
				console.error(err);
				alert("담당자 변경 중 오류가 발생했습니다.");
			} finally {
				handlerSubmitBtn.disabled = false;
			}
		});
	}

	/* =========================
	   좌측 섹션 이동 버튼
	   ========================= */
	document.addEventListener("click", (e) => {
		const btn = e.target.closest(".delivery-list-added-scroll-btn");
		if (!btn) return;

		const selector = btn.getAttribute("data-scroll-target");
		if (!selector) return;

		const target = document.querySelector(selector);
		if (!target) return;

		target.scrollIntoView({
			behavior: "smooth",
			block: "start"
		});
	});

})();