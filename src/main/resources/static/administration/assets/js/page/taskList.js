document.addEventListener("DOMContentLoaded", function () {
	const dateCriteriaSelect = document.getElementById("dateCriteria");
	const startDateInput = document.getElementById("startDate");
	const endDateInput = document.getElementById("endDate");
	const pageSizeSelect = document.getElementById("task-list-added-pageSize");

	const checkAllBox = document.getElementById("task-list-check-all");
	const deleteTasksBtn = document.getElementById("task-list-delete-tasks-btn");
	const deleteOrdersBtn = document.getElementById("task-list-delete-orders-btn");

	const csrfToken = document.getElementById("admin-task-list-second-csrf-token")?.value;
	const csrfHeader = document.getElementById("admin-task-list-second-csrf-header")?.value;

	const bulkOpenBtn = document.getElementById("admin-task-list-second-bulk-open-btn");
	const bulkModal = document.getElementById("admin-task-list-second-bulk-modal");
	const bulkOverlay = document.getElementById("admin-task-list-second-bulk-overlay");
	const bulkCloseBtn = document.getElementById("admin-task-list-second-bulk-close-btn");
	const bulkCheckAllBtn = document.getElementById("admin-task-list-second-bulk-check-all-btn");
	const bulkCompleteBtn = document.getElementById("admin-task-list-second-bulk-complete-btn");

	function getOrderCheckboxes() {
		return Array.from(document.querySelectorAll(".task-list-order-checkbox"));
	}

	function getSelectedOrderIds() {
		return getOrderCheckboxes()
			.filter(checkbox => checkbox.checked)
			.map(checkbox => Number(checkbox.value))
			.filter(id => !Number.isNaN(id));
	}

	function updateDateInputs() {
		if (!dateCriteriaSelect || !startDateInput || !endDateInput) {
			return;
		}

		const selectedValue = dateCriteriaSelect.value;
		const shouldEnable = selectedValue === "order" || selectedValue === "delivery";

		startDateInput.disabled = !shouldEnable;
		endDateInput.disabled = !shouldEnable;
	}

	function updateBulkButtons() {
		const checkboxes = getOrderCheckboxes();
		const checkedCount = checkboxes.filter(cb => cb.checked).length;
		const totalCount = checkboxes.length;

		if (deleteTasksBtn) {
			deleteTasksBtn.disabled = checkedCount === 0;
		}

		if (deleteOrdersBtn) {
			deleteOrdersBtn.disabled = checkedCount === 0;
		}

		if (checkAllBox) {
			checkAllBox.checked = totalCount > 0 && checkedCount === totalCount;
			checkAllBox.indeterminate = checkedCount > 0 && checkedCount < totalCount;
		}
	}

	function setDeleteButtonsBusy(isBusy) {
		const selectedCount = getSelectedOrderIds().length;

		if (deleteTasksBtn) {
			deleteTasksBtn.disabled = isBusy || selectedCount === 0;
		}

		if (deleteOrdersBtn) {
			deleteOrdersBtn.disabled = isBusy || selectedCount === 0;
		}

		if (isBusy) {
			if (deleteTasksBtn && !deleteTasksBtn.dataset.originalText) {
				deleteTasksBtn.dataset.originalText = deleteTasksBtn.innerHTML;
				deleteTasksBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> 삭제중';
			}

			if (deleteOrdersBtn && !deleteOrdersBtn.dataset.originalText) {
				deleteOrdersBtn.dataset.originalText = deleteOrdersBtn.innerHTML;
				deleteOrdersBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> 삭제중';
			}
		} else {
			if (deleteTasksBtn?.dataset.originalText) {
				deleteTasksBtn.innerHTML = deleteTasksBtn.dataset.originalText;
				deleteTasksBtn.dataset.originalText = "";
			}

			if (deleteOrdersBtn?.dataset.originalText) {
				deleteOrdersBtn.innerHTML = deleteOrdersBtn.dataset.originalText;
				deleteOrdersBtn.dataset.originalText = "";
			}
		}
	}

	function buildJsonHeaders() {
		const headers = {
			"Content-Type": "application/json"
		};

		if (csrfHeader && csrfToken) {
			headers[csrfHeader] = csrfToken;
		}

		return headers;
	}

	async function callDeleteApi(url, confirmMessage) {
		const selectedOrderIds = getSelectedOrderIds();

		if (selectedOrderIds.length === 0) {
			alert("삭제할 주문을 하나 이상 선택해 주세요.");
			return;
		}

		if (!confirm(confirmMessage)) {
			return;
		}

		try {
			setDeleteButtonsBusy(true);

			const response = await fetch(url, {
				method: "POST",
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					orderIds: selectedOrderIds
				})
			});

			let data = null;

			try {
				data = await response.json();
			} catch (e) {
				data = null;
			}

			if (!response.ok || !data || data.success !== true) {
				throw new Error(data?.message || "삭제 처리 중 오류가 발생했습니다.");
			}

			alert(data.message || "삭제가 완료되었습니다.");
			window.location.reload();
		} catch (error) {
			alert(error.message || "삭제 처리 중 오류가 발생했습니다.");
		} finally {
			setDeleteButtonsBusy(false);
			updateBulkButtons();
		}
	}

	function toggleDetailRow(orderId, forceOpen) {
		if (!orderId) {
			return;
		}

		const row = document.querySelector(`.admin-task-list-second-detail-row[data-order-id="${orderId}"]`);
		const button = document.querySelector(`.admin-task-list-second-wide-toggle-btn[data-order-id="${orderId}"]`);

		if (!row) {
			return;
		}

		const isOpen = !row.classList.contains("d-none");
		const nextOpen = typeof forceOpen === "boolean" ? forceOpen : !isOpen;

		row.classList.toggle("d-none", !nextOpen);

		if (button) {
			button.textContent = nextOpen ? "닫기" : "넓게보기";
			button.classList.toggle("btn-primary", nextOpen);
			button.classList.toggle("btn-outline-primary", !nextOpen);
		}
	}

	function readCompanySource() {
		return Array.from(document.querySelectorAll("#admin-task-list-second-company-source [data-company-id]"))
			.map(item => ({
				companyId: String(item.dataset.companyId || ""),
				companyName: item.dataset.companyName || "",
				representativeName: item.dataset.representativeName || ""
			}));
	}

	function readMemberSource() {
		return Array.from(document.querySelectorAll("#admin-task-list-second-member-source [data-member-id]"))
			.map(item => ({
				companyId: String(item.dataset.companyId || ""),
				memberId: String(item.dataset.memberId || ""),
				memberName: item.dataset.memberName || ""
			}));
	}

	const memberOptions = readMemberSource();

	function syncRequesterSelect(companySelect) {
		if (!companySelect) {
			return;
		}

		const form = companySelect.closest(".admin-task-list-second-update-form");
		const requesterSelect = form?.querySelector(".admin-task-list-second-requester-select");

		if (!requesterSelect) {
			return;
		}

		const selectedCompanyId = String(companySelect.value || "");
		const selectedMemberId = String(requesterSelect.dataset.selectedMemberId || "");

		requesterSelect.innerHTML = "";

		const defaultOption = document.createElement("option");
		defaultOption.value = "";
		defaultOption.textContent = "신청자 선택";
		requesterSelect.appendChild(defaultOption);

		if (!selectedCompanyId) {
			requesterSelect.value = "";
			return;
		}

		const matchedMembers = memberOptions.filter(member => member.companyId === selectedCompanyId);

		matchedMembers.forEach(member => {
			const option = document.createElement("option");
			option.value = member.memberId;
			option.textContent = member.memberName;

			if (member.memberId === selectedMemberId) {
				option.selected = true;
			}

			requesterSelect.appendChild(option);
		});

		if (requesterSelect.value !== selectedMemberId) {
			requesterSelect.value = "";
		}
	}

	function initializeCompanyMemberSelects() {
		document.querySelectorAll(".admin-task-list-second-company-select").forEach(companySelect => {
			syncRequesterSelect(companySelect);

			companySelect.addEventListener("change", function () {
				const form = companySelect.closest(".admin-task-list-second-update-form");
				const requesterSelect = form?.querySelector(".admin-task-list-second-requester-select");

				if (requesterSelect) {
					requesterSelect.dataset.selectedMemberId = "";
				}

				syncRequesterSelect(companySelect);
			});
		});

		document.querySelectorAll(".admin-task-list-second-requester-select").forEach(requesterSelect => {
			requesterSelect.addEventListener("change", function () {
				requesterSelect.dataset.selectedMemberId = requesterSelect.value || "";
			});
		});
	}

	function openBulkModal() {
		if (!bulkModal) {
			return;
		}

		bulkModal.classList.add("is-open");
		bulkModal.setAttribute("aria-hidden", "false");
		document.body.classList.add("admin-task-list-second-modal-open");
		updateBulkModalButtons();
	}

	function closeBulkModal() {
		if (!bulkModal) {
			return;
		}

		bulkModal.classList.remove("is-open");
		bulkModal.setAttribute("aria-hidden", "true");
		document.body.classList.remove("admin-task-list-second-modal-open");
	}

	function getBulkPendingCheckboxes() {
		return Array.from(document.querySelectorAll(".admin-task-list-second-bulk-checkbox"))
			.filter(checkbox => !checkbox.disabled);
	}

	function getSelectedBulkOrderIds() {
		return getBulkPendingCheckboxes()
			.filter(checkbox => checkbox.checked)
			.map(checkbox => Number(checkbox.value))
			.filter(id => !Number.isNaN(id));
	}

	function updateBulkModalButtons() {
		const pendingCheckboxes = getBulkPendingCheckboxes();
		const checkedCount = pendingCheckboxes.filter(checkbox => checkbox.checked).length;
		const totalCount = pendingCheckboxes.length;

		if (bulkCompleteBtn) {
			bulkCompleteBtn.disabled = checkedCount === 0;
		}

		if (bulkCheckAllBtn) {
			const allChecked = totalCount > 0 && checkedCount === totalCount;
			bulkCheckAllBtn.textContent = allChecked ? "체크해제" : "일괄체크";
			bulkCheckAllBtn.dataset.allChecked = allChecked ? "true" : "false";
			bulkCheckAllBtn.disabled = totalCount === 0;
		}
	}

	function toggleBulkAllCheck() {
		const pendingCheckboxes = getBulkPendingCheckboxes();

		if (pendingCheckboxes.length === 0) {
			updateBulkModalButtons();
			return;
		}

		const allChecked = pendingCheckboxes.every(checkbox => checkbox.checked);
		const nextChecked = !allChecked;

		pendingCheckboxes.forEach(checkbox => {
			checkbox.checked = nextChecked;
		});

		updateBulkModalButtons();
	}

	function setBulkCompleteBusy(isBusy) {
		if (!bulkCompleteBtn) {
			return;
		}

		if (isBusy) {
			if (!bulkCompleteBtn.dataset.originalText) {
				bulkCompleteBtn.dataset.originalText = bulkCompleteBtn.textContent;
			}

			bulkCompleteBtn.disabled = true;
			bulkCompleteBtn.textContent = "처리중";
		} else {
			bulkCompleteBtn.textContent = bulkCompleteBtn.dataset.originalText || "체크완료";
			updateBulkModalButtons();
		}

		if (bulkCheckAllBtn) {
			bulkCheckAllBtn.disabled = isBusy || getBulkPendingCheckboxes().length === 0;
		}
	}

	async function completeBulkChecks() {
		const selectedOrderIds = getSelectedBulkOrderIds();

		if (selectedOrderIds.length === 0) {
			alert("체크완료 처리할 오더를 하나 이상 선택해 주세요.");
			return;
		}

		if (!confirm(selectedOrderIds.length + "건을 체크완료 처리하시겠습니까?")) {
			return;
		}

		try {
			setBulkCompleteBusy(true);

			const response = await fetch("/management/api/non-standard-task-list-second/check-complete", {
				method: "POST",
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					orderIds: selectedOrderIds
				})
			});

			let data = null;

			try {
				data = await response.json();
			} catch (e) {
				data = null;
			}

			if (!response.ok || !data || data.success !== true) {
				throw new Error(data?.message || "체크완료 처리 중 오류가 발생했습니다.");
			}

			alert(data.message || "체크완료 처리되었습니다.");
			window.location.reload();
		} catch (error) {
			alert(error.message || "체크완료 처리 중 오류가 발생했습니다.");
		} finally {
			setBulkCompleteBusy(false);
		}
	}

	function initializeBaseEvents() {
		updateDateInputs();

		if (dateCriteriaSelect) {
			dateCriteriaSelect.addEventListener("change", updateDateInputs);
		}

		if (pageSizeSelect) {
			pageSizeSelect.addEventListener("change", function () {
				const form = pageSizeSelect.closest("form");

				if (!form) {
					return;
				}

				form.submit();
			});
		}

		if (checkAllBox) {
			checkAllBox.addEventListener("change", function () {
				getOrderCheckboxes().forEach(checkbox => {
					checkbox.checked = checkAllBox.checked;
				});

				updateBulkButtons();
			});
		}

		document.addEventListener("change", function (event) {
			if (event.target.classList.contains("task-list-order-checkbox")) {
				updateBulkButtons();
			}

			if (event.target.classList.contains("admin-task-list-second-bulk-checkbox")) {
				updateBulkModalButtons();
			}
		});

		if (deleteTasksBtn) {
			deleteTasksBtn.addEventListener("click", function () {
				callDeleteApi(
					"/management/api/non-standard-task/delete-tasks",
					"선택한 주문이 속한 태스크 전체가 삭제됩니다.\n\n해당 태스크의 모든 주문, 주문항목, 주문이미지, 배송순서 인덱스까지 함께 삭제됩니다.\n\n계속 진행하시겠습니까?"
				);
			});
		}

		if (deleteOrdersBtn) {
			deleteOrdersBtn.addEventListener("click", function () {
				callDeleteApi(
					"/management/api/non-standard-task/delete-orders",
					"선택한 주문만 삭제됩니다.\n\n삭제 후 태스크에 남은 주문이 하나도 없으면 태스크도 자동 삭제됩니다.\n\n계속 진행하시겠습니까?"
				);
			});
		}

		document.addEventListener("click", function (event) {
			const toggleButton = event.target.closest(".admin-task-list-second-wide-toggle-btn");

			if (toggleButton) {
				event.preventDefault();
				toggleDetailRow(toggleButton.dataset.orderId);
				return;
			}

			const closeButton = event.target.closest(".admin-task-list-second-wide-close-btn");

			if (closeButton) {
				event.preventDefault();
				toggleDetailRow(closeButton.dataset.orderId, false);
			}
		});

		updateBulkButtons();
	}

	function initializeBulkModalEvents() {
		if (bulkOpenBtn) {
			bulkOpenBtn.addEventListener("click", openBulkModal);
		}

		if (bulkOverlay) {
			bulkOverlay.addEventListener("click", closeBulkModal);
		}

		if (bulkCloseBtn) {
			bulkCloseBtn.addEventListener("click", closeBulkModal);
		}

		if (bulkCheckAllBtn) {
			bulkCheckAllBtn.addEventListener("click", toggleBulkAllCheck);
		}

		if (bulkCompleteBtn) {
			bulkCompleteBtn.addEventListener("click", completeBulkChecks);
		}

		document.addEventListener("keydown", function (event) {
			if (event.key === "Escape" && bulkModal?.classList.contains("is-open")) {
				closeBulkModal();
			}
		});

		updateBulkModalButtons();
	}

	initializeBaseEvents();
	initializeCompanyMemberSelects();
	initializeBulkModalEvents();
});