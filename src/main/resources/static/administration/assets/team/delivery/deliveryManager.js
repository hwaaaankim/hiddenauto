/* deliveryManager.js */
document.addEventListener("DOMContentLoaded", function () {
	"use strict";

	const rows = document.querySelectorAll(".delivery-manager-added-row");
	const rowChecks = Array.from(document.querySelectorAll(".delivery-manager-added-row-check"));
	const checkAll = document.getElementById("delivery-manager-added-check-all");
	const excelButton = document.getElementById("delivery-manager-added-excel-btn");
	const siteStatementButton = document.getElementById("delivery-manager-added-site-statement-btn");
	const parcelStatementButton = document.getElementById("delivery-manager-added-parcel-statement-btn");

	rows.forEach(function (row) {
		row.addEventListener("click", function (event) {
			const ignored = event.target.closest("a, button, input, select, textarea, label");

			if (ignored) {
				return;
			}

			const href = row.getAttribute("data-href");

			if (!href) {
				return;
			}

			window.location.href = href;
		});

		row.addEventListener("keydown", function (event) {
			if (event.key !== "Enter") {
				return;
			}

			const ignored = event.target.closest("a, button, input, select, textarea, label");

			if (ignored) {
				return;
			}

			const href = row.getAttribute("data-href");

			if (!href) {
				return;
			}

			window.location.href = href;
		});

		row.setAttribute("tabindex", "0");
	});

	if (checkAll) {
		checkAll.addEventListener("change", function () {
			rowChecks.forEach(function (checkbox) {
				checkbox.checked = checkAll.checked;
			});

			updateStatementButtonState();
		});
	}

	rowChecks.forEach(function (checkbox) {
		checkbox.addEventListener("change", function () {
			updateCheckAllState();
			updateStatementButtonState();
		});
	});

	if (excelButton) {
		excelButton.addEventListener("click", downloadCurrentDeliveryManagerExcel);
	}

	if (siteStatementButton) {
		siteStatementButton.addEventListener("click", function () {
			downloadDeliveryStatement("SITE", siteStatementButton);
		});
	}

	if (parcelStatementButton) {
		parcelStatementButton.addEventListener("click", function () {
			downloadDeliveryStatement("PARCEL", parcelStatementButton);
		});
	}

	updateCheckAllState();
	updateStatementButtonState();

	function getSelectedOrderIds() {
		return rowChecks
			.filter(function (checkbox) {
				return checkbox.checked;
			})
			.map(function (checkbox) {
				return Number(checkbox.getAttribute("data-order-id") || checkbox.value);
			})
			.filter(function (value) {
				return Number.isFinite(value) && value > 0;
			});
	}

	function updateCheckAllState() {
		if (!checkAll) {
			return;
		}

		if (rowChecks.length === 0) {
			checkAll.checked = false;
			checkAll.indeterminate = false;
			checkAll.disabled = true;
			return;
		}

		const checkedCount = rowChecks.filter(function (checkbox) {
			return checkbox.checked;
		}).length;

		checkAll.disabled = false;
		checkAll.checked = checkedCount > 0 && checkedCount === rowChecks.length;
		checkAll.indeterminate = checkedCount > 0 && checkedCount < rowChecks.length;
	}

	function updateStatementButtonState() {
		const selectedCount = getSelectedOrderIds().length;
		const disabled = selectedCount === 0;

		if (siteStatementButton) {
			siteStatementButton.disabled = disabled;
		}

		if (parcelStatementButton) {
			parcelStatementButton.disabled = disabled;
		}
	}

	async function downloadDeliveryStatement(statementType, button) {
		const selectedOrderIds = getSelectedOrderIds();

		if (selectedOrderIds.length === 0) {
			alert("명세서로 출력할 배송건을 하나 이상 선택해 주세요.");
			return;
		}

		const originalText = button ? button.innerHTML : "";

		try {
			setStatementButtonsBusy(true, button);

			const response = await fetch("/api/internal/delivery-statement/excel", {
				method: "POST",
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					statementType: statementType,
					orderIds: selectedOrderIds
				})
			});

			if (!response.ok) {
				const errorText = await response.text();
				throw new Error(errorText || "명세서 출력에 실패했습니다.");
			}

			const blob = await response.blob();
			const contentDisposition = response.headers.get("Content-Disposition");
			const filename = resolveStatementFilename(
				contentDisposition,
				statementType === "SITE" ? "현장명세서" : "택배명세서"
			);

			downloadBlob(blob, filename);
		} catch (error) {
			console.error(error);
			alert(error.message || "명세서 출력 중 오류가 발생했습니다.");
		} finally {
			setStatementButtonsBusy(false, button, originalText);
		}
	}

	async function downloadCurrentDeliveryManagerExcel() {
		const orderedOrderIds = Array.from(document.querySelectorAll(".delivery-manager-added-row"))
			.map(function (row) {
				return row.getAttribute("data-order-id");
			})
			.filter(function (value) {
				return value !== null && value !== undefined && String(value).trim() !== "";
			})
			.map(function (value) {
				return Number(value);
			})
			.filter(function (value) {
				return Number.isFinite(value) && value > 0;
			});

		if (orderedOrderIds.length === 0) {
			alert("엑셀로 출력할 배송건이 없습니다.");
			return;
		}

		const deliveryHandlerId = Number(excelButton.getAttribute("data-delivery-handler-id"));
		const fromDateInput = document.querySelector("input[name='fromDate']");
		const toDateInput = document.querySelector("input[name='toDate']");

		const fromDate = fromDateInput ? fromDateInput.value : null;
		const toDate = toDateInput ? toDateInput.value : null;

		if (!Number.isFinite(deliveryHandlerId) || deliveryHandlerId <= 0) {
			alert("배송 담당자 정보가 올바르지 않습니다.");
			return;
		}

		const originalText = excelButton.innerHTML;

		try {
			excelButton.disabled = true;
			excelButton.innerHTML = "엑셀 생성중...";

			const response = await fetch("/team/deliveryExcel", {
				method: "POST",
				headers: buildJsonHeaders(),
				body: JSON.stringify({
					deliveryHandlerId: deliveryHandlerId,
					fromDate: fromDate || null,
					toDate: toDate || null,
					deliveryDate: fromDate && toDate && fromDate === toDate ? fromDate : null,
					orderedOrderIds: orderedOrderIds
				})
			});

			if (!response.ok) {
				const errorText = await response.text();
				throw new Error(errorText || "엑셀 출력에 실패했습니다.");
			}

			const blob = await response.blob();
			const contentDisposition = response.headers.get("Content-Disposition");
			const filename = resolveFilename(contentDisposition, fromDate, toDate);

			downloadBlob(blob, filename);
		} catch (error) {
			console.error(error);
			alert(error.message || "엑셀 출력 중 오류가 발생했습니다.");
		} finally {
			excelButton.disabled = false;
			excelButton.innerHTML = originalText;
		}
	}

	function buildJsonHeaders() {
		const headers = {
			"Content-Type": "application/json"
		};

		const csrfToken =
			document.querySelector("meta[name='_csrf']")?.getAttribute("content")
			|| document.querySelector("input[name='_csrf']")?.value;

		const csrfHeader =
			document.querySelector("meta[name='_csrf_header']")?.getAttribute("content")
			|| "X-CSRF-TOKEN";

		if (csrfToken) {
			headers[csrfHeader] = csrfToken;
		}

		return headers;
	}

	function setStatementButtonsBusy(isBusy, activeButton, originalText) {
		[siteStatementButton, parcelStatementButton].forEach(function (button) {
			if (!button) {
				return;
			}

			if (isBusy) {
				button.disabled = true;
				if (button === activeButton) {
					button.innerHTML = "명세서 생성중...";
				}
				return;
			}

			if (button === activeButton && originalText) {
				button.innerHTML = originalText;
			}
		});

		if (!isBusy) {
			updateStatementButtonState();
		}
	}

	function downloadBlob(blob, filename) {
		const url = window.URL.createObjectURL(blob);
		const a = document.createElement("a");

		a.href = url;
		a.download = filename;
		document.body.appendChild(a);
		a.click();

		a.remove();
		window.URL.revokeObjectURL(url);
	}

	function resolveStatementFilename(contentDisposition, fallbackPrefix) {
		const headerFilename = parseContentDispositionFilename(contentDisposition);
		if (headerFilename) {
			return headerFilename;
		}

		const today = new Date().toISOString().slice(0, 10);
		return fallbackPrefix + "_" + today + ".xlsx";
	}

	function resolveFilename(contentDisposition, fromDate, toDate) {
		const headerFilename = parseContentDispositionFilename(contentDisposition);
		if (headerFilename) {
			return headerFilename;
		}

		const dateLabel = fromDate && toDate
			? fromDate === toDate
				? fromDate
				: fromDate + "_" + toDate
			: "current";

		return "delivery_" + dateLabel + ".xlsx";
	}

	function parseContentDispositionFilename(contentDisposition) {
		if (!contentDisposition) {
			return "";
		}

		const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
		if (utf8Match && utf8Match[1]) {
			return decodeURIComponent(utf8Match[1].replace(/"/g, ""));
		}

		const asciiMatch = contentDisposition.match(/filename="?([^";]+)"?/i);
		if (asciiMatch && asciiMatch[1]) {
			return asciiMatch[1];
		}

		return "";
	}
});
