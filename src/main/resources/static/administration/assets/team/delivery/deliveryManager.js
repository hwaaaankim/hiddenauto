/* deliveryManager.js */
document.addEventListener("DOMContentLoaded", function () {
	const rows = document.querySelectorAll(".delivery-manager-added-row");
	const excelButton = document.getElementById("delivery-manager-added-excel-btn");

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

			const href = row.getAttribute("data-href");

			if (!href) {
				return;
			}

			window.location.href = href;
		});

		row.setAttribute("tabindex", "0");
	});

	if (excelButton) {
		excelButton.addEventListener("click", downloadCurrentDeliveryManagerExcel);
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

		const originalText = excelButton.innerHTML;

		try {
			excelButton.disabled = true;
			excelButton.innerHTML = "엑셀 생성중...";

			const response = await fetch("/management/deliveryExcel", {
				method: "POST",
				headers: headers,
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

			const url = window.URL.createObjectURL(blob);
			const a = document.createElement("a");

			a.href = url;
			a.download = filename;
			document.body.appendChild(a);
			a.click();

			a.remove();
			window.URL.revokeObjectURL(url);
		} catch (error) {
			console.error(error);
			alert(error.message || "엑셀 출력 중 오류가 발생했습니다.");
		} finally {
			excelButton.disabled = false;
			excelButton.innerHTML = originalText;
		}
	}

	function resolveFilename(contentDisposition, fromDate, toDate) {
		if (contentDisposition) {
			const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
			if (utf8Match && utf8Match[1]) {
				return decodeURIComponent(utf8Match[1].replace(/"/g, ""));
			}

			const asciiMatch = contentDisposition.match(/filename="?([^"]+)"?/i);
			if (asciiMatch && asciiMatch[1]) {
				return asciiMatch[1];
			}
		}

		const dateLabel = fromDate && toDate
			? fromDate === toDate
				? fromDate
				: fromDate + "_" + toDate
			: "current";

		return "delivery_" + dateLabel + ".xlsx";
	}
});