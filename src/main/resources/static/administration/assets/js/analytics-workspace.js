"use strict";

// Results stay in the page; the nonmodal tools panel remains available while scrolling.
(function (global) {
    let toolsOpen = false, closeTimer = null, activeView = "GRAPH", chartData = null;
    const el = id => document.getElementById(id);
    const frame = fn => global.requestAnimationFrame ? global.requestAnimationFrame(fn) : setTimeout(fn, 0);

    function openTools() {
        const panel = el("analyticsToolsPanel"); if (!panel) return;
        clearTimeout(closeTimer); toolsOpen = true; panel.hidden = false; panel.removeAttribute("inert");
        el("analyticsToolsToggle").setAttribute("aria-expanded", "true");
        panel.getBoundingClientRect(); // Establish the collapsed geometry before the opening transition.
        frame(() => { if (toolsOpen) panel.classList.add("is-open"); });
        frame(() => { if (toolsOpen) el("fromDate")?.focus({ preventScroll: true }); });
    }

    function closeTools(focus = true) {
        if (!toolsOpen) return;
        toolsOpen = false; const panel = el("analyticsToolsPanel");
        panel.classList.remove("is-open"); panel.setAttribute("inert", "");
        el("analyticsToolsToggle").setAttribute("aria-expanded", "false");
        if (focus) el("analyticsToolsToggle").focus({ preventScroll: true });
        closeTimer = setTimeout(() => { if (!toolsOpen) panel.hidden = true; }, 180);
    }

    function selectView(view) {
        if (!el("analyticsGraphPanel") || !["GRAPH", "TABLE"].includes(view)) return;
        activeView = view;
        for (const [key, tab, panel] of [["GRAPH", "analyticsGraphTab", "analyticsGraphPanel"], ["TABLE", "analyticsTableTab", "analyticsTablePanel"]]) {
            const selected = key === view, button = el(tab);
            button.setAttribute("aria-selected", String(selected)); button.tabIndex = selected ? 0 : -1;
            button.classList.toggle("is-active", selected); el(panel).hidden = !selected;
        }
        if (view === "GRAPH") {
            if (!chartInstance && chartData?.labels?.length) workspaceRenderChart(chartData);
            frame(() => { if (activeView === "GRAPH") chartInstance?.resize?.(); });
        }
    }

    function updateToolsStatus() {
        const status = el("analyticsToolsStatus"), badge = document.querySelector(".analytics-tools-dirty"); if (!status || !badge) return;
        let changed = false;
        try { changed = !!appliedRequest && requestSignature(buildRequestBody()) !== requestSignature(appliedRequest); } catch (_) { changed = true; }
        badge.hidden = !changed; el("analyticsToolsToggle").classList.toggle("has-changes", changed);
        status.textContent = el("analyticsSearchBtn").getAttribute("aria-busy") === "true" ? "조건에 맞는 발주를 집계하고 있습니다." : changed ? "조건이 변경되었습니다. 조회를 눌러 반영해 주세요." : appliedRequest ? "현재 조회 결과를 내려받을 수 있습니다." : "조건을 정한 뒤 조회를 눌러 주세요.";
    }

    const workspaceRenderChart = renderChart;
    renderChart = function (chart) {
        chartData = chart;
        if (activeView === "TABLE") { chartInstance?.destroy(); chartInstance = null; return; }
        workspaceRenderChart(chart);
    };
    const workspaceContext = renderResultContext;
    renderResultContext = function (data) { workspaceContext(data); updateToolsStatus(); };
    const workspaceAxis = renderAxisUI;
    renderAxisUI = function () { workspaceAxis(); updateToolsStatus(); };
    const workspaceSync = syncAxisOrder;
    syncAxisOrder = function () {
        const before = JSON.stringify([xMetrics, xDims]); workspaceSync();
        if (before !== JSON.stringify([xMetrics, xDims])) frame(updateToolsStatus);
    };
    const workspaceBusy = setQueryBusy;
    setQueryBusy = function (busy) { workspaceBusy(busy); updateToolsStatus(); };
    analyticsExportPng = async function () {
        if (global.AnalyticsFeedback?.isExporting()) return;
        let feedback;
        try {
            buildRequestBodyForExport();
            if (!chartData?.labels?.length) throw new Error("그래프를 먼저 조회해 주세요.");
            const signature = requestSignature(appliedRequest);
            feedback = global.AnalyticsFeedback?.startExport("그래프 이미지를 준비하고 있습니다", "analyticsPngBtn", analyticsExportPng);
            if (!chartInstance) selectView("GRAPH");
            await new Promise(resolve => frame(resolve));
            buildRequestBodyForExport();
            if (signature !== requestSignature(appliedRequest)) throw new Error("이미지 준비 중 조회 결과가 변경되었습니다. 다시 저장해 주세요.");
            const link = document.createElement("a"); link.download = "analytics_chart.png";
            link.href = chartInstance.toBase64Image("image/png", 1); link.click();
            feedback?.finish("success", "현재 그래프 이미지의 다운로드를 요청했습니다.");
        } catch (error) {
            feedback?.finish("error", error.message || "그래프 이미지를 저장하지 못했습니다.");
            if (!feedback) alert(error.message || "그래프 이미지를 저장하지 못했습니다.");
        }
    };

    global.AnalyticsWorkspace = { openTools, closeTools, selectView, hasChartData: () => !!chartData?.labels?.length };
    document.addEventListener("DOMContentLoaded", () => {
        el("analyticsToolsToggle").addEventListener("click", () => toolsOpen ? closeTools() : openTools());
        el("analyticsToolsClose").addEventListener("click", () => closeTools());
        document.addEventListener("keydown", event => { if (event.key === "Escape" && toolsOpen && !event.defaultPrevented) closeTools(); });
        document.addEventListener("pointerdown", event => {
            if (toolsOpen && !el("analyticsToolsPanel").contains(event.target) && !el("analyticsToolsToggle").contains(event.target)) closeTools(false);
        });
        document.addEventListener("show.bs.modal", () => closeTools(false));
        for (const [id, view] of [["analyticsGraphTab", "GRAPH"], ["analyticsTableTab", "TABLE"]]) {
            const tab = el(id); tab.addEventListener("click", () => selectView(view));
            tab.addEventListener("keydown", event => {
                if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
                event.preventDefault(); const next = event.key === "Home" ? "GRAPH" : event.key === "End" ? "TABLE" : activeView === "GRAPH" ? "TABLE" : "GRAPH";
                selectView(next); el(next === "GRAPH" ? "analyticsGraphTab" : "analyticsTableTab").focus();
            });
        }
        el("analyticsToolsPanel").addEventListener("input", updateToolsStatus);
        el("analyticsToolsPanel").addEventListener("change", () => frame(updateToolsStatus));
        el("analyticsToolsPanel").addEventListener("click", () => frame(updateToolsStatus));
        el("analyticsSearchBtn").addEventListener("click", () => { if (el("analyticsSearchBtn").getAttribute("aria-busy") === "true") closeTools(false); });
        for (const id of ["analyticsExcelBtn", "analyticsA4Btn", "analyticsCsvBtn", "analyticsPngBtn"]) el(id).addEventListener("click", () => closeTools(false));
        selectView("GRAPH");
        if (!yPrimary && !appliedRequest) {
            setYPrimary("COMPANY"); xMetrics = ["SUPPLY_AMOUNT", "VAT_AMOUNT", "SALES", "TASK_COUNT", "QTY"]; sortKey = "sales"; renderAxisUI(); configureRevenueChart(); queryAndRender(true);
        }
        updateToolsStatus();
    });
})(window);
