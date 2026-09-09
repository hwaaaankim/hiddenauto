/* deliveryManager.js */
document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    const rows = Array.from(document.querySelectorAll(".delivery-manager-added-row"));
    const rowChecks = Array.from(document.querySelectorAll(".delivery-manager-added-row-check"));
    const checkAll = document.getElementById("delivery-manager-added-check-all");
    const excelButton = document.getElementById("delivery-manager-added-excel-btn");

    const siteStatementPrintButton = document.getElementById("delivery-manager-added-site-statement-btn");
    const siteStatementDownloadButton = document.getElementById("delivery-manager-added-site-statement-download-btn");
    const teamSiteStatementPrintButton = document.getElementById("delivery-manager-added-team-site-statement-print-btn");
    const teamSiteStatementDownloadButton = document.getElementById("delivery-manager-added-team-site-statement-download-btn");

    const statementRenderer = window.HiddenAutoDeliveryStatementRenderer || null;

    const SELECTED_STATEMENT_PREVIEW_URL = "/team/deliveryManager/site-statement/preview";
    const SELECTED_STATEMENT_DATA_URL = "/team/deliveryManager/site-statement/data";
    const SELECTED_STATEMENT_EXCEL_URL = "/team/deliveryManager/site-statement/excel";
    const TEAM_STATEMENT_DATA_URL = "/team/deliveryRoute/team-site-statement/data";
    const TEAM_STATEMENT_EXCEL_URL = "/team/deliveryRoute/team-site-statement/excel";

    rows.forEach(function (row) {
        row.addEventListener("click", function (event) {
            const ignored = event.target.closest("a, button, input, select, textarea, label");

            if (ignored) {
                return;
            }

            const href = row.getAttribute("data-href");
            if (href) {
                window.location.href = href;
            }
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
            if (href) {
                window.location.href = href;
            }
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

    if (siteStatementPrintButton) {
        siteStatementPrintButton.addEventListener("click", function () {
            printSelectedSiteStatement(siteStatementPrintButton);
        });
    }

    if (siteStatementDownloadButton) {
        siteStatementDownloadButton.addEventListener("click", function () {
            downloadSelectedSiteStatement(siteStatementDownloadButton);
        });
    }

    if (teamSiteStatementPrintButton) {
        teamSiteStatementPrintButton.addEventListener("click", function () {
            printTeamSiteStatement(teamSiteStatementPrintButton);
        });
    }

    if (teamSiteStatementDownloadButton) {
        teamSiteStatementDownloadButton.addEventListener("click", function () {
            downloadTeamSiteStatement(teamSiteStatementDownloadButton);
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
                return Number.isSafeInteger(value) && value > 0;
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
        const disabled = getSelectedOrderIds().length === 0;

        if (siteStatementPrintButton) {
            siteStatementPrintButton.disabled = disabled;
        }

        if (siteStatementDownloadButton) {
            siteStatementDownloadButton.disabled = disabled;
        }
    }

    async function printSelectedSiteStatement(button) {
        const selectedOrderIds = getSelectedOrderIds();

        if (selectedOrderIds.length === 0) {
            window.alert("현장명세서로 출력할 배송건을 하나 이상 선택해 주세요.");
            return;
        }

        if (!statementRenderer || typeof statementRenderer.buildPrintDocument !== "function") {
            window.alert("현장명세서 출력 모듈을 불러오지 못했습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.");
            return;
        }

        const printWindow = window.open("", "_blank");
        if (!printWindow) {
            window.alert("인쇄 창이 차단되었습니다. 브라우저의 팝업 허용 설정을 확인해 주세요.");
            return;
        }

        writePreparingWindow(printWindow, "현장명세서 출력 준비");
        const originalText = button.innerHTML;

        try {
            setSelectedStatementButtonsBusy(true, button, "대상 확인 중...");

            const preview = await requestJson(SELECTED_STATEMENT_PREVIEW_URL, {
                orderIds: selectedOrderIds,
                layoutType: "HORIZONTAL"
            });

            notifyExcludedOrders(preview, "출력");

            const eligibleOrderIds = normalizePositiveIds(preview && preview.eligibleOrderIds);
            if (eligibleOrderIds.length === 0) {
                safeCloseWindow(printWindow);
                window.alert("선택한 배송건 중 현장명세서 출력 대상(현장배송/화물)이 없습니다.");
                return;
            }

            setSelectedStatementButtonsBusy(true, button, "출력 준비 중...");

            const data = await requestJson(SELECTED_STATEMENT_DATA_URL, {
                orderIds: eligibleOrderIds,
                layoutType: "HORIZONTAL"
            });

            if (!data || !Array.isArray(data.pages) || data.pages.length === 0) {
                throw new Error("출력할 현장명세서 데이터가 없습니다.");
            }

            printWindow.document.open();
            printWindow.document.write(statementRenderer.buildPrintDocument(data));
            printWindow.document.close();
        } catch (error) {
            console.error(error);
            safeCloseWindow(printWindow);
            window.alert(error && error.message
                ? error.message
                : "현장명세서 출력 중 오류가 발생했습니다.");
        } finally {
            setSelectedStatementButtonsBusy(false, button, originalText);
        }
    }

    async function downloadSelectedSiteStatement(button) {
        const selectedOrderIds = getSelectedOrderIds();

        if (selectedOrderIds.length === 0) {
            window.alert("현장명세서로 다운로드할 배송건을 하나 이상 선택해 주세요.");
            return;
        }

        const originalText = button.innerHTML;

        try {
            setSelectedStatementButtonsBusy(true, button, "대상 확인 중...");

            const preview = await requestJson(SELECTED_STATEMENT_PREVIEW_URL, {
                orderIds: selectedOrderIds,
                layoutType: "HORIZONTAL"
            });

            notifyExcludedOrders(preview, "다운로드");

            const eligibleOrderIds = normalizePositiveIds(preview && preview.eligibleOrderIds);
            if (eligibleOrderIds.length === 0) {
                window.alert("선택한 배송건 중 현장명세서 다운로드 대상(현장배송/화물)이 없습니다.");
                return;
            }

            setSelectedStatementButtonsBusy(true, button, "엑셀 생성 중...");

            const response = await fetch(SELECTED_STATEMENT_EXCEL_URL, {
                method: "POST",
                credentials: "same-origin",
                headers: buildJsonHeaders(),
                body: JSON.stringify({
                    orderIds: eligibleOrderIds,
                    layoutType: "HORIZONTAL"
                })
            });

            if (!response.ok) {
                const errorBody = await readResponseBody(response);
                throw new Error(resolveErrorMessage(errorBody, "현장명세서 다운로드에 실패했습니다."));
            }

            const blob = await response.blob();
            const filename = resolveDownloadFilename(
                response.headers.get("Content-Disposition"),
                "현장명세서_가로형_선택건.xlsx"
            );
            downloadBlob(blob, filename);
        } catch (error) {
            console.error(error);
            window.alert(error && error.message
                ? error.message
                : "현장명세서 다운로드 중 오류가 발생했습니다.");
        } finally {
            setSelectedStatementButtonsBusy(false, button, originalText);
        }
    }

    async function printTeamSiteStatement(button) {
        const deliveryDate = resolveSingleTeamStatementDate();
        if (!deliveryDate) {
            return;
        }

        if (!statementRenderer || typeof statementRenderer.buildPrintDocument !== "function") {
            window.alert("현장명세서 출력 모듈을 불러오지 못했습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.");
            return;
        }

        const printWindow = window.open("", "_blank");
        if (!printWindow) {
            window.alert("인쇄 창이 차단되었습니다. 브라우저의 팝업 허용 설정을 확인해 주세요.");
            return;
        }

        writePreparingWindow(printWindow, "배송팀 전체 현장명세서 출력 준비");
        const originalText = button.innerHTML;

        try {
            setTeamStatementButtonsBusy(true, button, "팀 전체 출력 준비 중...");

            const data = await requestJson(TEAM_STATEMENT_DATA_URL, {
                deliveryDate: deliveryDate,
                layoutType: "HORIZONTAL"
            });

            if (!data || !Array.isArray(data.pages) || data.pages.length === 0) {
                throw new Error("출력할 배송팀 전체 현장명세서 데이터가 없습니다.");
            }

            printWindow.document.open();
            printWindow.document.write(statementRenderer.buildPrintDocument(data));
            printWindow.document.close();
        } catch (error) {
            console.error(error);
            safeCloseWindow(printWindow);
            window.alert(error && error.message
                ? error.message
                : "배송팀 전체 현장명세서 출력 중 오류가 발생했습니다.");
        } finally {
            setTeamStatementButtonsBusy(false, button, originalText);
        }
    }

    async function downloadTeamSiteStatement(button) {
        const deliveryDate = resolveSingleTeamStatementDate();
        if (!deliveryDate) {
            return;
        }

        const originalText = button.innerHTML;

        try {
            setTeamStatementButtonsBusy(true, button, "팀 전체 엑셀 생성 중...");

            const response = await fetch(TEAM_STATEMENT_EXCEL_URL, {
                method: "POST",
                credentials: "same-origin",
                headers: buildJsonHeaders(),
                body: JSON.stringify({
                    deliveryDate: deliveryDate,
                    layoutType: "HORIZONTAL"
                })
            });

            if (!response.ok) {
                const errorBody = await readResponseBody(response);
                throw new Error(resolveErrorMessage(errorBody, "배송팀 전체 현장명세서 다운로드에 실패했습니다."));
            }

            const blob = await response.blob();
            const filename = resolveDownloadFilename(
                response.headers.get("Content-Disposition"),
                "배송팀현장명세서_가로형_" + deliveryDate + ".xlsx"
            );
            downloadBlob(blob, filename);
        } catch (error) {
            console.error(error);
            window.alert(error && error.message
                ? error.message
                : "배송팀 전체 현장명세서 다운로드 중 오류가 발생했습니다.");
        } finally {
            setTeamStatementButtonsBusy(false, button, originalText);
        }
    }

    function resolveSingleTeamStatementDate() {
        const fromDateInput = document.querySelector("input[name='fromDate']");
        const toDateInput = document.querySelector("input[name='toDate']");
        const fromDate = fromDateInput ? String(fromDateInput.value || "").trim() : "";
        const toDate = toDateInput ? String(toDateInput.value || "").trim() : "";

        if (!fromDate || !toDate) {
            window.alert("배송팀 전체 현장명세서를 출력할 조회 날짜를 먼저 선택해 주세요.");
            return "";
        }

        if (fromDate !== toDate) {
            window.alert(
                "배송팀 전체 현장명세서는 업체별 오늘 배송 페이지와 동일하게 하루 단위로 생성됩니다.\n"
                + "From과 To를 같은 날짜로 조회한 뒤 다시 실행해 주세요."
            );
            return "";
        }

        return fromDate;
    }

    function notifyExcludedOrders(preview, actionLabel) {
        const excludedOrders = Array.isArray(preview && preview.excludedOrders)
            ? preview.excludedOrders
            : [];

        if (excludedOrders.length === 0) {
            return;
        }

        const requestedCount = Number(preview && preview.requestedCount) || 0;
        const eligibleCount = Number(preview && preview.eligibleCount) || 0;
        const lines = excludedOrders.map(function (item) {
            const orderId = item && item.orderId != null ? item.orderId : "?";
            const reason = item && item.reason ? item.reason : "현장명세서 출력 대상이 아닙니다.";
            return "- Order #" + orderId + ": " + reason;
        });

        const continuation = eligibleCount > 0
            ? "\n\n나머지 " + eligibleCount + "건만 계속 " + actionLabel + "합니다."
            : "\n\n계속 " + actionLabel + "할 수 있는 대상이 없습니다.";

        window.alert(
            "선택 " + requestedCount + "건 중 " + excludedOrders.length
            + "건은 현장명세서 대상이 아니어서 제외됩니다.\n\n"
            + lines.join("\n")
            + continuation
        );
    }

    async function requestJson(url, body) {
        const response = await fetch(url, {
            method: "POST",
            credentials: "same-origin",
            headers: buildJsonHeaders(),
            body: JSON.stringify(body || {})
        });

        const responseBody = await readResponseBody(response);
        if (!response.ok) {
            throw new Error(resolveErrorMessage(responseBody, "요청 처리에 실패했습니다."));
        }

        if (responseBody && typeof responseBody === "object") {
            return responseBody;
        }

        throw new Error("서버 응답 형식이 올바르지 않습니다.");
    }

    function setSelectedStatementButtonsBusy(isBusy, activeButton, busyTextOrOriginal) {
        [siteStatementPrintButton, siteStatementDownloadButton].forEach(function (button) {
            if (!button) {
                return;
            }

            if (isBusy) {
                button.disabled = true;
                if (button === activeButton) {
                    button.innerHTML = busyTextOrOriginal;
                }
                return;
            }

            if (button === activeButton && busyTextOrOriginal) {
                button.innerHTML = busyTextOrOriginal;
            }
        });

        if (!isBusy) {
            updateStatementButtonState();
        }
    }

    function setTeamStatementButtonsBusy(isBusy, activeButton, busyTextOrOriginal) {
        [teamSiteStatementPrintButton, teamSiteStatementDownloadButton].forEach(function (button) {
            if (!button) {
                return;
            }

            button.disabled = isBusy;
            if (button === activeButton && busyTextOrOriginal) {
                button.innerHTML = busyTextOrOriginal;
            }
        });
    }

    function writePreparingWindow(printWindow, title) {
        try {
            printWindow.document.open();
            printWindow.document.write(
                '<!doctype html><html lang="ko"><head><meta charset="utf-8"><title>'
                + escapeHtml(title)
                + '</title></head><body style="font-family:Malgun Gothic,Apple SD Gothic Neo,sans-serif;padding:32px;">'
                + escapeHtml(title)
                + ' 데이터를 준비하고 있습니다.</body></html>'
            );
            printWindow.document.close();
        } catch (error) {
            console.warn(error);
        }
    }

    function safeCloseWindow(printWindow) {
        try {
            if (printWindow && !printWindow.closed) {
                printWindow.close();
            }
        } catch (error) {
            console.warn(error);
        }
    }

    async function downloadCurrentDeliveryManagerExcel() {
        const orderedOrderIds = rows
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
            window.alert("엑셀로 출력할 배송건이 없습니다.");
            return;
        }

        const deliveryHandlerId = Number(excelButton.getAttribute("data-delivery-handler-id"));
        const fromDateInput = document.querySelector("input[name='fromDate']");
        const toDateInput = document.querySelector("input[name='toDate']");

        const fromDate = fromDateInput ? fromDateInput.value : null;
        const toDate = toDateInput ? toDateInput.value : null;

        if (!Number.isFinite(deliveryHandlerId) || deliveryHandlerId <= 0) {
            window.alert("배송 담당자 정보가 올바르지 않습니다.");
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
                const errorBody = await readResponseBody(response);
                throw new Error(resolveErrorMessage(errorBody, "엑셀 출력에 실패했습니다."));
            }

            const blob = await response.blob();
            const filename = resolveFilename(
                response.headers.get("Content-Disposition"),
                fromDate,
                toDate
            );

            downloadBlob(blob, filename);
        } catch (error) {
            console.error(error);
            window.alert(error.message || "엑셀 출력 중 오류가 발생했습니다.");
        } finally {
            excelButton.disabled = false;
            excelButton.innerHTML = originalText;
        }
    }

    function buildJsonHeaders() {
        const headers = {
            "Content-Type": "application/json",
            "Accept": "application/json"
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

    async function readResponseBody(response) {
        const contentType = response.headers.get("content-type") || "";

        if (contentType.includes("application/json")) {
            return response.json();
        }

        return response.text();
    }

    function resolveErrorMessage(body, fallback) {
        if (body && typeof body === "object" && body.message) {
            return String(body.message);
        }

        if (typeof body === "string" && body.trim()) {
            return body.trim();
        }

        return fallback;
    }

    function normalizePositiveIds(values) {
        const source = Array.isArray(values) ? values : [];
        const result = [];
        const seen = new Set();

        source.forEach(function (value) {
            const id = Number(value);
            if (!Number.isSafeInteger(id) || id <= 0 || seen.has(id)) {
                return;
            }
            seen.add(id);
            result.push(id);
        });

        return result;
    }

    function downloadBlob(blob, filename) {
        if (statementRenderer && typeof statementRenderer.downloadBlob === "function") {
            statementRenderer.downloadBlob(blob, filename);
            return;
        }

        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(url);
    }

    function resolveDownloadFilename(contentDisposition, fallback) {
        if (statementRenderer && typeof statementRenderer.resolveDownloadFilename === "function") {
            return statementRenderer.resolveDownloadFilename(contentDisposition, fallback);
        }

        return parseContentDispositionFilename(contentDisposition) || fallback;
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

    function escapeHtml(value) {
        return String(value == null ? "" : value)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }
});
