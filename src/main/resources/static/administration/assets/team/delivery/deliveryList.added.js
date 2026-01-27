(function() {
	'use strict';

	const saveButton = document.getElementById("saveOrderIndexBtn");
	const reorderByTaskBtn = document.getElementById("reorderByTaskBtn");

	const pendingListEl = document.getElementById("pendingList");
	const doneListEl = document.getElementById("doneList");

	const excelBtn = document.getElementById("delivery-list-added-excelBtn");

	// Modal elements
	const modalEl = document.getElementById("delivery-list-added-modal");
	const modalTitleEl = document.getElementById("delivery-list-added-modal-title");
	const skeletonEl = document.getElementById("delivery-list-added-modal-skeleton");
	const bodyEl = document.getElementById("delivery-list-added-modal-body");

	const companyNameEl = document.getElementById("delivery-list-added-companyName");
	const requesterNameEl = document.getElementById("delivery-list-added-requesterName");
	const companyContactEl = document.getElementById("delivery-list-added-companyContact");
	const companyAddressEl = document.getElementById("delivery-list-added-companyAddress");
	const orderAddressEl = document.getElementById("delivery-list-added-orderAddress");
	const productTextEl = document.getElementById("delivery-list-added-productText");

	// existing images
	const existingWrapEl = document.getElementById("delivery-list-added-existing-wrap");
	const existingListEl = document.getElementById("delivery-list-added-existing-list");

	// upload
	const uploadWrapEl = document.getElementById("delivery-list-added-upload-wrap");
	const btnSubmitEl = document.getElementById("delivery-list-added-btn-submit");

	const btnCamera = document.getElementById("delivery-list-added-btn-camera");
	const btnGallery = document.getElementById("delivery-list-added-btn-gallery");
	const inputCamera = document.getElementById("delivery-list-added-file-camera");
	const inputGallery = document.getElementById("delivery-list-added-file-gallery");
	const thumbListEl = document.getElementById("delivery-list-added-thumb-list");

	// CSRF
	const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
	const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

	function isMobileCoarsePointer() {
		return window.matchMedia && window.matchMedia("(hover: none) and (pointer: coarse)").matches;
	}

	function getDeliveryHandlerId() {
		return document.getElementById("deliveryHandlerId")?.value || "";
	}

	function getDeliveryDate() {
		return document.getElementById("deliveryDate")?.value || "";
	}

	function getAllOrderIdsInDomOrder() {
		const pending = Array.from(pendingListEl.querySelectorAll(".draggable-item"))
			.map(el => Number(el.getAttribute("data-order-id")))
			.filter(n => Number.isFinite(n) && n > 0);

		const done = Array.from(doneListEl.querySelectorAll(".draggable-item"))
			.map(el => Number(el.getAttribute("data-order-id")))
			.filter(n => Number.isFinite(n) && n > 0);

		return pending.concat(done);
	}

	function getPendingOrderIdsInDomOrder() {
		return Array.from(pendingListEl.querySelectorAll(".draggable-item"))
			.map(el => Number(el.getAttribute("data-order-id")))
			.filter(n => Number.isFinite(n) && n > 0);
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
			if (lastTaskId === null) {
				// first
			} else if (taskId !== lastTaskId) {
				alt = (alt === 0 ? 1 : 0);
				el.classList.add("delivery-list-added-task-group-break");
			}
			el.classList.add(alt === 0 ? "delivery-list-added-task-group-alt-0" : "delivery-list-added-task-group-alt-1");
			lastTaskId = taskId;
		}
	}

	function applyAllTaskGroupStyles() {
		applyTaskGroupStyles(pendingListEl);
		applyTaskGroupStyles(doneListEl);
	}

	document.addEventListener("DOMContentLoaded", function() {
		applyAllTaskGroupStyles();
	});

	/* =========================
	   ✅ Sortable (pending only)
	   - 요청사항: 모바일에서는 핸들버튼으로만 순서변경
	   - 충돌 방지 위해 PC도 동일하게 handle만 드래그 시작
	   ========================= */
	const sortable = new Sortable(pendingListEl, {
		group: "pending",
		animation: 150,
		fallbackOnBody: true,
		swapThreshold: 0.65,
		handle: ".delivery-list-added-drag-handle",
		filter: ".action-btn, .delivery-list-added-open-complete-modal, .delivery-list-added-open-detail-modal",
		preventOnFilter: false,
		onEnd: () => {
			saveButton.disabled = false;
			applyAllTaskGroupStyles();
		}
	});

	/* =========================
	   업체별정렬 버튼 (기존 로직 유지)
	   ========================= */
	reorderByTaskBtn.addEventListener("click", async () => {
		const deliveryHandlerId = getDeliveryHandlerId();
		const deliveryDate = getDeliveryDate();

		if (!deliveryHandlerId) return alert("담당자 정보가 없습니다.");
		if (!deliveryDate) return alert("날짜를 선택해주세요.");

		const pendingOrderIds = getPendingOrderIdsInDomOrder();
		if (pendingOrderIds.length <= 1) return alert("정렬할 항목이 없습니다.");

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
				const text = await res.text().catch(() => "");
				return alert("업체별정렬 실패\n" + (text || ("HTTP " + res.status)));
			}

			const data = await res.json();
			const reordered = Array.isArray(data?.pendingOrderIds) ? data.pendingOrderIds : [];

			if (reordered.length !== pendingOrderIds.length) {
				return alert("업체별정렬 결과가 올바르지 않습니다.(개수 불일치)");
			}

			// DOM 재배치
			const elById = new Map();
			Array.from(pendingListEl.querySelectorAll(".draggable-item")).forEach(el => {
				const id = Number(el.getAttribute("data-order-id"));
				if (Number.isFinite(id)) elById.set(id, el);
			});

			for (const id of reordered) {
				const el = elById.get(Number(id));
				if (el) pendingListEl.appendChild(el);
			}

			saveButton.disabled = false;
			applyAllTaskGroupStyles();
		} catch (err) {
			console.error(err);
			alert("업체별정렬 중 오류가 발생했습니다.");
		}
	});

	/* =========================
	   순서 저장 (기존 로직 유지)
	   ========================= */
	saveButton.addEventListener("click", async () => {
		const ok = confirm("업체별(동일배송지별)로 재배치 됩니다. 진행하시겠습니까? 진행하시려면 '순서저장' 버튼을 클릭 해 주세요.");
		if (!ok) return;

		const deliveryHandlerId = getDeliveryHandlerId();
		const deliveryDate = getDeliveryDate();
		if (!deliveryHandlerId) return alert("담당자 정보가 없습니다.");
		if (!deliveryDate) return alert("날짜를 선택해주세요.");

		const pendingItems = Array.from(pendingListEl.querySelectorAll(".draggable-item"));
		const doneItems = Array.from(doneListEl.querySelectorAll(".draggable-item"));
		const allItems = pendingItems.concat(doneItems);

		const orderedIds = allItems.map((el, idx) => ({
			orderId: Number(el.getAttribute("data-order-id")),
			orderIndex: idx + 1
		}));

		try {
			const headers = { "Content-Type": "application/json" };
			if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

			const res = await fetch("/team/updateOrderIndex", {
				method: "POST",
				headers,
				body: JSON.stringify({
					deliveryHandlerId: Number(deliveryHandlerId),
					deliveryDate: deliveryDate,
					orderList: orderedIds
				})
			});

			if (res.ok) {
				alert("순서가 저장되었습니다.");
				saveButton.disabled = true;
			} else {
				const text = await res.text().catch(() => "");
				alert("순서 저장 실패\n" + (text || ("HTTP " + res.status)));
			}
		} catch (err) {
			console.error(err);
			alert("순서 저장 중 오류가 발생했습니다.");
		}
	});

	/* =========================
	   엑셀출력: 현재 DOM 순서 그대로 서버에 보내서 다운로드
	   ========================= */
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
				const text = await res.text().catch(() => "");
				return alert("엑셀 출력 실패\n" + (text || ("HTTP " + res.status)));
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

	/* =========================
	   ✅ 모달: 배송완료 / 배송상세
	   - 배송완료(complete): 업로드 활성
	   - 배송상세(detail): 업로드 비활성
	   - 공통: 기존 배송 증빙 이미지 표시
	   ========================= */

	let modalInstance = null;
	let currentOrderId = null;
	let currentMode = "detail"; // "complete" | "detail"

	// 첨부 파일 상태
	let selectedFiles = []; // File[]
	let selectedObjectUrls = []; // for revoke

	function resetModalUi() {
		currentOrderId = null;
		currentMode = "detail";

		companyNameEl.textContent = "-";
		requesterNameEl.textContent = "-";
		companyContactEl.textContent = "-";
		orderAddressEl.textContent = "-";
		productTextEl.textContent = "-";

		// existing images reset
		existingWrapEl.style.display = "none";
		existingListEl.innerHTML = "";

		// skeleton on
		skeletonEl.style.display = "";
		bodyEl.style.display = "none";

		// upload area off
		uploadWrapEl.style.display = "none";
		btnSubmitEl.style.display = "none";
		btnSubmitEl.disabled = true;

		// file reset
		cleanupThumbs();
		selectedFiles = [];
	}

	function cleanupThumbs() {
		for (const u of selectedObjectUrls) {
			try { URL.revokeObjectURL(u); } catch (e) { }
		}
		selectedObjectUrls = [];
		thumbListEl.innerHTML = "";
		inputCamera.value = "";
		inputGallery.value = "";
	}

	function setMode(mode) {
		currentMode = mode;

		if (mode === "complete") {
			modalTitleEl.textContent = "배송완료 처리";
			uploadWrapEl.style.display = "";
			btnSubmitEl.style.display = "";
			btnSubmitEl.disabled = (selectedFiles.length === 0);
		} else {
			modalTitleEl.textContent = "배송상세";
			uploadWrapEl.style.display = "none";
			btnSubmitEl.style.display = "none";
			btnSubmitEl.disabled = true;
		}
	}

	async function loadSummary(orderId) {
		const headers = {};
		if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;

		const res = await fetch(`/team/deliveryOrderSummary/${orderId}`, {
			method: "GET",
			headers
		});

		if (!res.ok) {
			const text = await res.text().catch(() => "");
			throw new Error(text || ("HTTP " + res.status));
		}

		return await res.json();
	}

	function renderExistingImages(imageUrls) {
		const urls = Array.isArray(imageUrls) ? imageUrls.filter(u => typeof u === "string" && u.length > 0) : [];

		if (urls.length === 0) {
			existingWrapEl.style.display = "none";
			existingListEl.innerHTML = "";
			return;
		}

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

		existingWrapEl.style.display = "";
	}

	function renderSummary(data) {
		companyNameEl.textContent = data.companyName || "-";
		requesterNameEl.textContent = data.requesterName || "-";
		companyContactEl.textContent = data.companyContact || "-";
		orderAddressEl.textContent = data.orderAddress || "-";
		productTextEl.textContent = data.productText || "-";

		// ✅ 완료처리에 사용된 이미지(배송 증빙 이미지) 표시
		renderExistingImages(data.deliveryImageUrls);

		skeletonEl.style.display = "none";
		bodyEl.style.display = "";
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
			}
		})();
	}

	/* =========================
	   ✅ 카드 전체 클릭 -> 모달 오픈
	   - 드래그 핸들/버튼 클릭은 제외
	   - 상태가 PRODUCTION_DONE면 complete 모드, 그 외 detail 모드
	   ========================= */
	function shouldIgnoreCardClick(target) {
		if (!target) return false;
		return !!target.closest(
			".delivery-list-added-drag-handle," +
			".action-btn," +
			".delivery-list-added-open-complete-modal," +
			".delivery-list-added-open-detail-modal"
		);
	}

	function openByCard(elCard) {
		const orderId = elCard?.getAttribute("data-order-id");
		const status = elCard?.getAttribute("data-order-status");

		if (!orderId) return;

		if (status === "PRODUCTION_DONE") {
			openModal(orderId, "complete");
		} else {
			openModal(orderId, "detail");
		}
	}

	// pending/done 공통: 카드 클릭
	[pendingListEl, doneListEl].forEach(listEl => {
		listEl.addEventListener("click", (e) => {
			if (shouldIgnoreCardClick(e.target)) return;
			const card = e.target.closest(".delivery-list-added-card-clickable");
			if (!card) return;
			openByCard(card);
		});
	});

	// 버튼 클릭(기존 로직 유지하되 상세보기는 없음)
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

	// modal close cleanup
	modalEl.addEventListener("hidden.bs.modal", () => {
		resetModalUi();
	});

	/* =========================
	   이미지 첨부: 카메라/갤러리
	   ========================= */
	btnCamera.addEventListener("click", () => {
		inputCamera.click();
	});

	btnGallery.addEventListener("click", () => {
		inputGallery.click();
	});

	function addFiles(fileList) {
		const files = Array.from(fileList || []).filter(f => f && f.type && f.type.startsWith("image/"));
		if (files.length === 0) return;

		for (const f of files) {
			selectedFiles.push(f);
		}
		renderThumbs();
		btnSubmitEl.disabled = (selectedFiles.length === 0);
	}

	inputCamera.addEventListener("change", (e) => {
		addFiles(e.target.files);
		inputCamera.value = "";
	});

	inputGallery.addEventListener("change", (e) => {
		addFiles(e.target.files);
		inputGallery.value = "";
	});

	function renderThumbs() {
		cleanupThumbs();

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
				btnSubmitEl.disabled = (selectedFiles.length === 0);
			});

			item.appendChild(img);
			item.appendChild(rm);
			thumbListEl.appendChild(item);
		});
	}

	/* =========================
	   배송완료 제출: 기존 컨트롤러(/team/deliveryStatus/{orderId}) 사용
	   ========================= */
	btnSubmitEl.addEventListener("click", async () => {
		if (currentMode !== "complete") return;
		if (!currentOrderId) return;

		if (selectedFiles.length === 0) {
			alert("이미지를 1장 이상 첨부해주세요.");
			return;
		}

		const ok = confirm("배송완료 처리 하시겠습니까? (이미지가 함께 업로드 됩니다)");
		if (!ok) return;

		const headers = {};
		if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
		headers["X-Requested-With"] = "fetch";

		try {
			const form = new FormData();
			for (const f of selectedFiles) {
				form.append("files", f);
			}

			const url = `/team/deliveryStatus/${currentOrderId}?status=DELIVERY_DONE`;

			const res = await fetch(url, {
				method: "POST",
				headers,
				body: form
			});

			if (!res.ok) {
				const text = await res.text().catch(() => "");
				return alert("배송완료 실패\n" + (text || ("HTTP " + res.status)));
			}

			try { await res.text().catch(() => ""); } catch (e) { }

			alert("배송완료 처리되었습니다.");
			modalInstance.hide();
			location.reload();
		} catch (err) {
			console.error(err);
			alert("배송완료 처리 중 오류가 발생했습니다.");
		}
	});

})();
