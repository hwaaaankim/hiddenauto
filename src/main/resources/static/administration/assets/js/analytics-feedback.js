"use strict";

// Analytics-only request feedback. Progress is indeterminate until a real response arrives.
(function (global) {
    const jobs = new Map();
    const exportButtons = ["analyticsExcelBtn", "analyticsA4Btn", "analyticsCsvBtn", "analyticsPngBtn"];
    let sequence = 0, exportActive = false, queryBusy = false, resultCount = 0, hasChart = false;
    const element = value => typeof value === "string" ? document.getElementById(value) : value;

    function syncExports() {
        for (const id of exportButtons) {
            const button = element(id);
            if (button) button.disabled = queryBusy || exportActive || !resultCount || (id === "analyticsPngBtn" && !hasChart);
        }
    }

    function panel(job, compact) {
        const root = document.createElement("div");
        root.className = "analytics-feedback-panel";
        const header = document.createElement("div"), icon = document.createElement("span"), title = document.createElement("strong"), elapsed = document.createElement("span");
        header.className = "analytics-feedback-header"; icon.className = "analytics-feedback-icon";
        icon.setAttribute("aria-hidden", "true"); elapsed.className = "analytics-feedback-elapsed"; elapsed.setAttribute("aria-hidden", "true");
        header.append(icon, title, elapsed);
        const message = document.createElement("div"), track = document.createElement("div"), bar = document.createElement("span"), actions = document.createElement("div");
        message.className = "analytics-feedback-message"; track.className = "analytics-feedback-track";
        track.setAttribute("role", "progressbar"); track.setAttribute("aria-label", job.title + " 진행 중");
        track.append(bar); actions.className = "analytics-feedback-actions";
        const action = document.createElement("button"); action.type = "button"; action.className = "analytics-feedback-action";
        actions.append(action); root.append(header, message, track, actions);
        if (compact) root.classList.add("analytics-feedback-compact");
        return { root, title, elapsed, message, track, icon, action, actions };
    }

    function start(key, options = {}) {
        clear(key);
        const job = { id: ++sequence, key, title: options.title || "처리 중입니다", message: options.message || "서버 응답을 기다리고 있습니다.",
            status: "pending", started: Date.now(), options, panels: [], timer: null, hideTimer: null, observer: null };
        const host = element(options.host);
        if (host) {
            host.replaceChildren(); host.hidden = false; host.classList.add("analytics-feedback");
            host.setAttribute("role", "status"); host.setAttribute("aria-live", "polite"); host.dataset.jobId = String(job.id);
            const view = panel(job, false); host.append(view.root); job.panels.push(view); job.host = host;
        }
        if (options.dock) {
            const dock = element("analyticsFeedbackDock");
            if (dock) {
                const view = panel(job, true); view.root.dataset.feedbackKey = key;
                view.root.setAttribute("role", "status"); view.root.setAttribute("aria-live", "polite");
                dock.append(view.root); job.panels.push(view); job.dock = view.root;
                if (host && global.IntersectionObserver) {
                    job.observer = new IntersectionObserver(entries => { view.root.hidden = entries[0].isIntersecting; }, { threshold: 0.2 });
                    job.observer.observe(host);
                }
            }
        }
        jobs.set(key, job);
        for (const id of options.buttons || []) {
            const button = element(id); if (button) { button.classList.add("analytics-is-loading"); button.setAttribute("aria-busy", "true"); }
        }
        const render = () => {
            const seconds = Math.max(0, Math.floor((Date.now() - job.started) / 1000));
            for (const view of job.panels) {
                view.root.dataset.state = job.status; view.title.textContent = job.title;
                view.elapsed.textContent = job.status === "pending" ? seconds + "초 경과" : "";
                view.message.textContent = job.status === "pending" && seconds >= 10 && !job.received
                    ? job.message + " 데이터 양에 따라 시간이 걸릴 수 있으며 응답을 기다리고 있습니다." : job.message;
                view.track.hidden = job.status !== "pending";
                view.icon.textContent = job.status === "success" ? "✓" : job.status === "error" ? "!" : job.status === "canceled" ? "−" : "";
                view.action.hidden = false;
                if (job.status === "pending" && options.onCancel) {
                    view.action.textContent = "대기 중단"; view.action.title = "이 화면의 응답 대기를 중단합니다. 서버 처리가 취소되는 것은 아닙니다.";
                    view.action.onclick = options.onCancel;
                } else if ((job.status === "error" || job.status === "canceled") && options.onRetry) {
                    view.action.textContent = "다시 시도"; view.action.title = "현재 조건으로 다시 요청합니다."; view.action.onclick = options.onRetry;
                } else if (job.status !== "pending") {
                    view.action.textContent = "닫기"; view.action.title = "처리 안내 닫기"; view.action.onclick = () => clear(key);
                } else view.action.hidden = true;
            }
        };
        job.render = render; render(); job.timer = setInterval(render, 1000);
        return {
            update(title, message) {
                if (jobs.get(key) !== job || job.status !== "pending") return;
                job.received = true; job.title = title; if (message != null) job.message = message; render();
            },
            finish(status = "success", message) {
                if (jobs.get(key) !== job || job.status !== "pending") return;
                clearInterval(job.timer); job.status = status;
                job.title = status === "success" ? "완료했습니다" : status === "error" ? "처리하지 못했습니다" : "응답 대기를 중단했습니다";
                job.message = message || (status === "canceled" ? "조건을 확인한 뒤 다시 조회할 수 있습니다." : "");
                for (const id of options.buttons || []) { const button = element(id); button?.classList.remove("analytics-is-loading"); button?.setAttribute("aria-busy", "false"); }
                if (key === "export") { exportActive = false; syncExports(); }
                if (status === "error") for (const view of job.panels) { view.root.setAttribute("role", "alert"); }
                render();
                if (status === "success") job.hideTimer = setTimeout(() => { if (jobs.get(key) === job) clear(key); }, 4500);
            }
        };
    }

    function clear(key) {
        const job = jobs.get(key); if (!job) return;
        clearInterval(job.timer); clearTimeout(job.hideTimer); job.observer?.disconnect(); job.dock?.remove();
        if (job.host?.dataset.jobId === String(job.id)) { job.host.replaceChildren(); job.host.hidden = true; }
        for (const id of job.options.buttons || []) { const button = element(id); button?.classList.remove("analytics-is-loading"); button?.setAttribute("aria-busy", "false"); }
        jobs.delete(key);
    }

    global.AnalyticsFeedback = {
        start, clear,
        setQueryBusy(busy, count, chart) { queryBusy = busy; resultCount = count; hasChart = chart; syncExports(); },
        startExport(title, button, retry) {
            if (exportActive) return null;
            exportActive = true; syncExports();
            return start("export", { host: "analyticsExportFeedback", title, dock: true, buttons: [button],
                message: "현재 조회 조건의 전체 결과로 파일을 생성하고 있습니다.", onRetry: retry });
        },
        isExporting() { return exportActive; },
        attachExportReport(report) {
            const job = jobs.get("export"); if (!job || !report?.document?.head) return;
            const doc = report.document, link = doc.createElement("link"), layout = doc.createElement("style");
            doc.title = "A4 보고서 생성 중"; link.rel = "stylesheet";
            link.href = new URL("/administration/assets/css/analytics.css?v=20261126-analytics-r4", global.location.href).href;
            layout.textContent = "body{margin:0;padding:48px 20px;background:#f8fafc;font-family:system-ui,sans-serif}body>.analytics-feedback-panel{max-width:520px;margin:12vh auto}";
            doc.head.append(link, layout); doc.body.className = "analytics-page";
            const view = panel(job, false); doc.body.replaceChildren(view.root); job.panels.push(view); job.render();
        }
    };
})(window);
