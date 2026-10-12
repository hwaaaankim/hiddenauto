(function () {
  "use strict";
  const root = document.querySelector("#pm-drawing");
  if (!root) return;
  const $ = (selector, scope = root) => scope.querySelector(selector);
  const $$ = (selector, scope = root) => Array.from(scope.querySelectorAll(selector));
  const base = "/admin/api/product-master/drawings";
  const kind = root.dataset.kind;
  const targetId = Number(root.dataset.targetId);
  const targetPath = "/" + kind + "/" + targetId;
  const frame = $("#pmd-editor");
  const bundledEditorPath = "/administration/assets/libs/diagrams/index.html";
  const maxXmlBytes = 5 * 1024 * 1024;
  const maxPages = 40;
  const aiDiagnosticsEnabled = String(root.dataset.aiDiagnostics || "").toLowerCase() === "true";
  const configuredLogChars = Number(root.dataset.aiLogMaxChars);
  // Console payloads have a separate hard ceiling even if server logs allow more.
  const aiDiagnosticLogMaxChars = Number.isFinite(configuredLogChars) && configuredLogChars > 0
    ? Math.min(65536, Math.floor(configuredLogChars)) : 65536;
  const blankXml = '<mxfile><diagram id="drawing-1" name="정면도"><mxGraphModel grid="1" gridSize="10" guides="1" page="1" pageScale="1" pageWidth="1169" pageHeight="827"><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel></diagram></mxfile>';
  const state = {
    xml: "", savedSignature: "", version: null, label: "제품 도면", updatedAt: null,
    dirty: false, maybeDirty: false, ready: false, busy: null, loading: false,
    conflict: false, origin: "", editorUrl: "", selfHosted: false, checkingEditor: false, pendingExport: null,
    pendingLoad: null, connectTimer: null, previewUrl: null, aiAbort: null,
    searchAbort: null, searchPage: 0, searchQuery: "", hasNext: false, searchSeq: 0,
    aiActiveTraceId: null, aiLastTraceId: null,
  };

  function safeTraceId(value) {
    const trace = typeof value === "string" ? value.trim() : "";
    return /^[A-Za-z0-9][A-Za-z0-9._-]{7,99}$/.test(trace) && !/^sk-/i.test(trace) ? trace : null;
  }
  function redactDiagnosticText(value, maxChars = 2048) {
    return String(value ?? "")
      .replace(/data:image\/[A-Za-z0-9.+-]+(?:;[^,\s]*)?;base64,[A-Za-z0-9+/=\r\n]+/gi, "[IMAGE_BASE64_OMITTED]")
      .replace(/\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{8,}/g, "[API_KEY_REDACTED]")
      .replace(/\bBearer\s+[A-Za-z0-9._~+/=-]+/gi, "Bearer [REDACTED]")
      .replace(/((?:api[-_]?key|authorization|csrf|_csrf)["']?\s*[=:]\s*)["'][^"']*["']/gi, "$1\"[REDACTED]\"")
      .replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, "")
      .slice(0, maxChars);
  }
  function diagnosticMetadata(value, depth = 0) {
    if (depth > 4) return "[DEPTH_LIMIT]";
    if (typeof value === "string") return redactDiagnosticText(value);
    if (typeof value === "number" || typeof value === "boolean" || value === null) return value;
    if (Array.isArray(value)) return value.slice(0, 20).map((item) => diagnosticMetadata(item, depth + 1));
    if (value && typeof value === "object") {
      const clean = Object.create(null);
      for (const [key, item] of Object.entries(value).slice(0, 30)) {
        if (/api.?key|csrf|authorization|cookie|headers|token|base64/i.test(key)) continue;
        clean[key] = diagnosticMetadata(item, depth + 1);
      }
      return clean;
    }
    return undefined;
  }
  function aiLog(traceId, stage, metadata = {}) {
    const trace = safeTraceId(traceId);
    if (!aiDiagnosticsEnabled || !trace) return;
    try { console.info("[ProductDrawingAI][" + trace + "][" + stage + "]", diagnosticMetadata(metadata)); }
    catch { /* Diagnostics must never interrupt drawing or registration. */ }
  }
  function drawingMetadata(xml) {
    if (!aiDiagnosticsEnabled) return {};
    const summary = { xmlChars: typeof xml === "string" ? xml.length : 0 };
    if (typeof xml !== "string" || !xml.trim()) return summary;
    if (xml.length > maxXmlBytes) return { ...summary, parseSkipped: "XML exceeds document limit" };
    try {
      const doc = parseDiagram(xml);
      const pages = Array.from(doc.querySelectorAll("diagram"));
      return { ...summary, pageCount: pages.length, shapeCount: doc.querySelectorAll('mxCell[vertex="1"]').length,
        lineCount: doc.querySelectorAll('mxCell[edge="1"]').length, cellCount: doc.querySelectorAll("mxCell").length,
        compressedPageCount: pages.filter((page) => !page.querySelector("mxGraphModel")).length,
        pageNames: pages.slice(0, 8).map((page) => redactDiagnosticText(page.getAttribute("name"), 100)) };
    } catch (error) { return { ...summary, parseError: redactDiagnosticText(error.message) }; }
  }
  function aiLogXml(traceId, stage, xml, metadata = {}) {
    const trace = safeTraceId(traceId);
    if (!aiDiagnosticsEnabled || !trace) return;
    const original = typeof xml === "string" ? xml : "";
    const clean = redactDiagnosticText(original, aiDiagnosticLogMaxChars);
    const fragments = Math.ceil(clean.length / 4096);
    aiLog(trace, stage, { ...metadata, ...drawingMetadata(original), loggedChars: clean.length,
      maxLogChars: aiDiagnosticLogMaxChars, truncated: original.length > aiDiagnosticLogMaxChars, fragments });
    for (let offset = 0; offset < clean.length; offset += 4096) {
      try { console.info("[ProductDrawingAI][" + trace + "][" + stage + "_XML]", { fragment: offset / 4096 + 1, totalFragments: fragments }, clean.slice(offset, offset + 4096)); }
      catch { /* Continue the normal editor flow when console capture is unavailable. */ }
    }
  }
  function activeAiTrace() { return state.aiActiveTraceId || state.aiLastTraceId; }
  function errorWithTrace(error, fallbackTraceId) {
    const trace = safeTraceId(error?.traceId) || safeTraceId(fallbackTraceId);
    const message = error?.message || "처리 중 오류가 발생했습니다.";
    return trace ? message + "\n조회 코드: " + trace : message;
  }
  function fileMetadata(file) {
    return file ? { name: redactDiagnosticText(file.name, 160), mime: redactDiagnosticText(file.type, 80), bytes: file.size } : null;
  }

  function notice(message, tone = "info") {
    const el = $("#pmd-notice");
    el.textContent = message || "";
    el.dataset.tone = tone;
    el.hidden = !message;
  }
  function displayDate(value) {
    if (!value) return "";
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString("ko-KR");
  }
  function updateControls() {
    $$('[data-editor-tool]').forEach((button) => {
      button.disabled = !state.ready || !!state.busy || state.loading || (button.id === "pmd-save" && state.conflict);
    });
    $("#pmd-latest").disabled = !!state.busy || state.loading;
    $("#pmd-conflict-backup").disabled = !!state.busy || state.loading;
    $$("#pmd-import-dialog button, #pmd-import-dialog input").forEach((control) => { control.disabled = !!state.busy || state.loading; });
    $$("#pmd-ai-dialog input, #pmd-ai-dialog textarea").forEach((control) => { control.disabled = !!state.aiAbort; });
    const el = $("#pmd-state");
    const status = state.conflict ? ["error", "최신 도면 확인 필요"]
      : state.busy ? ["busy", { saving: "등록 중", importing: "도면 불러오는 중", ai: "AI 초안 생성 중", export: "내려받는 중", latest: "최신 도면 불러오는 중", snapshot: "변경 확인 중" }[state.busy] || "처리 중"]
      : !state.ready ? ["loading", "편집기 연결 중"]
      : state.dirty ? ["dirty", "미등록 변경사항"]
      : state.version === null ? ["empty", "도면 미등록"] : ["saved", "등록된 도면"];
    el.dataset.state = status[0];
    el.textContent = status[1];
    $("#pmd-updated").textContent = state.updatedAt ? "마지막 등록 " + displayDate(state.updatedAt) : "";
    $("#pmd-conflict").hidden = !state.conflict;
  }
  function showCover(message, detail = "", error = false, retryData = false) {
    const el = $("#pmd-cover");
    el.hidden = false;
    el.dataset.error = String(error);
    $("#pmd-cover-text").textContent = message;
    $("#pmd-cover-detail").textContent = detail;
    $("#pmd-retry").hidden = !error || retryData;
    $("#pmd-retry-data").hidden = !error || !retryData;
    $("#pmd-cover-backup").hidden = !error || !state.xml;
  }
  function hideCover() { $("#pmd-cover").hidden = true; }
  async function api(path, method = "GET", body, signal, timeout = 30000) {
    const controller = new AbortController();
    const abort = () => controller.abort();
    if (signal?.aborted) controller.abort();
    signal?.addEventListener("abort", abort, { once: true });
    const timer = setTimeout(abort, timeout);
    const headers = { Accept: "application/json" };
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (token && header) headers[header] = token;
    const options = { method, headers, credentials: "same-origin", signal: controller.signal };
    if (body !== undefined) {
      if (body instanceof FormData) options.body = body;
      else { headers["Content-Type"] = "application/json"; options.body = JSON.stringify(body); }
    }
    try {
      const response = await fetch(base + path, options);
      const responseTraceId = safeTraceId(response.headers?.get("X-Drawing-Trace-Id"));
      let data;
      try { data = await response.json(); }
      catch {
        const error = new Error(response.redirected || response.status === 401 ? "로그인이 만료되었습니다. 다시 로그인해 주세요." : "서버 응답을 읽을 수 없습니다. (" + response.status + ")");
        error.status = response.status; error.traceId = responseTraceId;
        throw error;
      }
      if (!response.ok || data.success === false) {
        const error = new Error(data.message || "요청 처리에 실패했습니다.");
        error.status = response.status;
        error.traceId = safeTraceId(data.traceId) || safeTraceId(data.data?.traceId) || responseTraceId;
        throw error;
      }
      const result = data.data === undefined ? data : data.data;
      if (path === "/draft" && result && typeof result === "object" && !safeTraceId(result.traceId) && responseTraceId) return { ...result, traceId: responseTraceId };
      return result;
    } catch (error) {
      if (controller.signal.aborted && !signal?.aborted) throw new Error("요청 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.");
      throw error;
    } finally {
      clearTimeout(timer);
      signal?.removeEventListener("abort", abort);
    }
  }
  function parseDiagram(xml) {
    if (typeof xml !== "string" || !xml.trim()) throw new Error("도면 내용이 비어 있습니다.");
    if (new Blob([xml]).size > maxXmlBytes) throw new Error("도면은 5MB 이내로 등록해 주세요. 큰 이미지나 사용하지 않는 페이지를 정리해 주세요.");
    if (/<!DOCTYPE|<!ENTITY/i.test(xml)) throw new Error("외부 문서 선언이 포함된 도면은 불러올 수 없습니다.");
    const doc = new DOMParser().parseFromString(xml, "application/xml");
    if (doc.querySelector("parsererror")) throw new Error("도면 XML 형식이 올바르지 않습니다.");
    if (doc.documentElement.nodeName === "mxGraphModel") {
      const wrapped = new DOMParser().parseFromString('<mxfile><diagram id="import-page" name="도면"/></mxfile>', "application/xml");
      wrapped.querySelector("diagram").append(wrapped.importNode(doc.documentElement, true));
      return wrapped;
    }
    if (doc.documentElement.nodeName !== "mxfile") throw new Error(".drawio 또는 draw.io XML 도면 파일을 선택해 주세요.");
    const children = Array.from(doc.documentElement.children);
    if (children.some((node) => node.nodeName !== "diagram")) throw new Error("도면 문서에는 도면 페이지만 포함할 수 있습니다.");
    const pages = children;
    if (!pages.length || pages.length > maxPages) throw new Error("도면은 1~40개의 페이지로 구성해 주세요.");
    const ids = new Set();
    for (const page of pages) {
      const id = page.getAttribute("id");
      if (id && ids.has(id)) throw new Error("중복된 도면 페이지 ID가 있습니다. 원본 도면 파일을 확인해 주세요.");
      if (id) ids.add(id);
    }
    return doc;
  }
  function signature(xml) {
    const doc = parseDiagram(xml);
    ["modified", "agent", "host", "version", "etag", "type"].forEach((attr) => doc.documentElement.removeAttribute(attr));
    return new XMLSerializer().serializeToString(doc);
  }
  function adoptXml(xml) {
    parseDiagram(xml);
    state.xml = xml;
    state.dirty = signature(xml) !== state.savedSignature;
    state.maybeDirty = document.activeElement === frame;
    updateControls();
  }
  function post(message) { frame.contentWindow?.postMessage(JSON.stringify(message), state.origin); }
  function resolveMessageId(message) {
    if (typeof message === "string") {
      try { message = JSON.parse(message); } catch { return null; }
    }
    return message?.pmdRequestId || message?.message?.pmdRequestId || null;
  }
  function exportFromEditor(format, options = {}) {
    if (!state.ready) return Promise.reject(new Error("도면 편집기 연결을 먼저 확인해 주세요."));
    if (state.pendingExport) return Promise.reject(new Error("이전 도면 요청을 처리하고 있습니다."));
    const id = "pmd-" + crypto.randomUUID();
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        if (state.pendingExport?.id === id) state.pendingExport = null;
        aiLog(activeAiTrace(), "EDITOR_EXPORT_TIMEOUT", { format, editorRequestId: id });
        reject(new Error("편집기 응답이 지연됩니다. 현재 작업을 백업한 뒤 다시 연결해 주세요."));
      }, format === "xml" ? 15000 : 45000);
      state.pendingExport = { id, format, resolve, reject, timer, traceId: activeAiTrace() };
      // Commits the currently edited text before serialising all pages. Messages are FIFO.
      post({ action: "resetEditor" });
      post({ action: "export", format, pmdRequestId: id, ...options });
    });
  }
  async function snapshot() {
    const result = await exportFromEditor("xml");
    adoptXml(result.xml);
    return result.xml;
  }
  async function operation(name, fn, message = "처리하고 있습니다.") {
    if (state.busy || state.loading) return;
    state.busy = name;
    if (document.activeElement === frame) root.focus({ preventScroll: true });
    showCover(message);
    updateControls();
    try { return await fn(); }
    catch (error) {
      if (error.name !== "AbortError") notice(error.message || "처리 중 오류가 발생했습니다.", "error");
      throw error;
    } finally {
      state.busy = null;
      if (state.ready && !state.loading) hideCover();
      updateControls();
    }
  }
  function safeRun(fn) { return Promise.resolve().then(fn).catch(() => {}); }
  function downloadBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url; anchor.download = filename;
    document.body.append(anchor); anchor.click(); anchor.remove();
    aiLog(state.aiLastTraceId, "DOWNLOAD_COMPLETE", { filename, mime: blob.type, bytes: blob.size });
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  }
  function fileName(extension) {
    return (state.label || "제품_" + targetId).replace(/[\\/:*?"<>|\u0000-\u001f]/g, "_").slice(0, 100) + "." + extension;
  }
  function downloadXml(xml = state.xml) {
    parseDiagram(xml);
    downloadBlob(new Blob([xml], { type: "application/xml;charset=utf-8" }), fileName("drawio"));
  }
  function downloadExport(result) {
    let format = result.format;
    if (format === "xml") { downloadXml(result.xml || result.data); return; }
    if (format === "xmlpng") format = "png";
    if (format === "xmlsvg") format = "svg";
    const mime = { png: "image/png", jpg: "image/jpeg", jpeg: "image/jpeg", webp: "image/webp", svg: "image/svg+xml", pdf: "application/pdf" }[format];
    if (!mime || typeof result.data !== "string" || result.data.length > 80 * 1024 * 1024) throw new Error("이 내보내기 형식은 상단 PNG·SVG·.drawio 버튼을 이용해 주세요.");
    if (!result.data.startsWith("data:" + mime)) throw new Error("이미지 내보내기에 실패했습니다. SVG로 내려받거나 잠시 후 다시 시도해 주세요.");
    const comma = result.data.indexOf(",");
    if (comma < 0) throw new Error("내보낸 이미지 형식이 올바르지 않습니다.");
    const prefix = result.data.slice(0, comma);
    const payload = result.data.slice(comma + 1);
    const bytes = /;base64/i.test(prefix) ? Uint8Array.from(atob(payload), (c) => c.charCodeAt(0)) : decodeURIComponent(payload);
    downloadBlob(new Blob([bytes], { type: mime }), fileName(format === "jpeg" ? "jpg" : format));
  }
  async function save(xml) {
    if (state.conflict) { notice("다른 창에서 변경한 최신 도면을 먼저 확인해 주세요.", "error"); return; }
    return operation("saving", async () => {
      notice("");
      const current = xml || await snapshot();
      adoptXml(current);
      aiLog(state.aiLastTraceId, "PRODUCT_REGISTRATION_SENT", { targetKind: kind, targetId, expectedVersion: state.version, ...drawingMetadata(current) });
      try {
        const saved = await api(targetPath, "PUT", { xml: current, expectedVersion: state.version });
        state.version = saved.version;
        state.updatedAt = saved.updatedAt;
        state.savedSignature = signature(current);
        state.dirty = signature(state.xml) !== state.savedSignature;
        aiLog(state.aiLastTraceId, "PRODUCT_REGISTRATION_COMPLETE", { targetKind: kind, targetId, version: state.version, updatedAt: state.updatedAt, unsavedChangesRemain: state.dirty });
        post({ action: "status", message: state.dirty ? "등록 이후 변경사항이 있습니다. 다시 도면 등록을 눌러 주세요." : "제품 도면을 등록했습니다.", modified: state.dirty });
        notice(state.dirty ? "요청한 도면을 등록했습니다. 등록 이후의 변경사항은 다시 등록해 주세요." : "모든 페이지를 제품 도면으로 등록했습니다.", "ok");
      } catch (error) {
        aiLog(state.aiLastTraceId, "PRODUCT_REGISTRATION_FAILED", { status: error.status || null, message: error.message });
        if (error.status === 409) state.conflict = true;
        post({ action: "status", message: "등록되지 않았습니다. 상단 오류 안내를 확인해 주세요.", modified: true });
        throw error;
      }
    }, "모든 도면 페이지를 등록하고 있습니다.");
  }
  function finishLoad(error) {
    const pending = state.pendingLoad;
    if (!pending) return;
    clearTimeout(pending.timer);
    state.pendingLoad = null;
    if (error) {
      aiLog(pending.traceId, "EDITOR_LOAD_FAILED", { message: error.message });
      state.loading = false; state.ready = false;
      showCover("도면을 열지 못했습니다.", error.message, true);
      pending.reject(error); updateControls(); return;
    }
    (async () => {
      // Normalise through the editor once so a fresh load does not appear dirty.
      const exported = await exportFromEditor("xml");
      aiLogXml(pending.traceId, "EDITOR_XML_NORMALIZED", exported.xml, { editorRequestId: resolveMessageId(exported.message) });
      if (pending.baseline) state.savedSignature = signature(exported.xml);
      adoptXml(exported.xml);
      state.loading = false;
      if (!state.busy) hideCover();
      updateControls();
      pending.resolve();
    })().catch((error) => {
      aiLog(pending.traceId, "EDITOR_NORMALIZATION_FAILED", { message: error.message });
      state.loading = false; state.ready = false;
      showCover("도면 편집기 상태를 확인하지 못했습니다.", error.message + " 현재 도면을 백업한 뒤 다시 연결해 주세요.", true);
      pending.reject(error); updateControls();
    });
  }
  function loadEditor(xml, baseline = false, traceId = activeAiTrace()) {
    parseDiagram(xml);
    if (state.pendingLoad) return Promise.reject(new Error("이전 도면을 불러오는 중입니다."));
    state.loading = true;
    state.xml = xml;
    if (!baseline) state.dirty = signature(xml) !== state.savedSignature;
    updateControls();
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        aiLog(traceId, "EDITOR_LOAD_TIMEOUT", { baseline });
        state.pendingLoad = null;
        state.loading = false; state.ready = false;
        showCover("도면을 열지 못했습니다.", "현재 작업은 유지됩니다. 백업 후 편집기를 다시 연결해 주세요.", true);
        updateControls();
        reject(new Error("편집기가 도면 불러오기에 응답하지 않았습니다."));
      }, 25000);
      state.pendingLoad = { resolve, reject, timer, baseline, traceId };
      aiLogXml(traceId, "EDITOR_LOAD_SENT", xml, { baseline, targetKind: kind, targetId });
      post({ action: "load", xml, autosave: 1, title: state.label + ".drawio", modified: "unsavedChanges", noSaveBtn: 1, noExitBtn: 1, saveAndExit: 0, exportProtocol: true });
    });
  }
  function buildEditorUrl() {
    const url = new URL(root.dataset.editorUrl || bundledEditorPath, location.href);
    if (url.username || url.password || (url.protocol !== "https:" && !(url.origin === location.origin && url.protocol === "http:"))) throw new Error("도면 편집기 주소는 HTTPS 또는 같은 서버의 주소로 설정해 주세요.");
    url.hash = "";
    const params = { embed: "1", proto: "json", configure: "1", ui: "atlas", lang: "ko", libraries: "1", spin: "1", modified: "unsavedChanges", keepmodified: "1", noSaveBtn: "1", noExitBtn: "1", saveAndExit: "0", suppressNewWindows: "1" };
    Object.entries(params).forEach(([key, value]) => url.searchParams.set(key, value));
    state.selfHosted = url.origin === location.origin;
    if (state.selfHosted) {
      // The bundled editor uses our drawing API and needs no cloud-storage login.
      Object.entries({ stealth: "1", lockdown: "1", pwa: "0", plugins: "0" }).forEach(([key, value]) => url.searchParams.set(key, value));
      // Trusted same-origin static application; preserve normal storage and export APIs.
      frame.removeAttribute("sandbox");
    }
    state.origin = url.origin;
    return url.href;
  }
  async function verifyLocalEditor() {
    if (!state.selfHosted) return;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(state.editorUrl, { method: "HEAD", credentials: "same-origin", cache: "no-cache", signal: controller.signal });
      if (!response.ok || response.redirected || !/text\/html/i.test(response.headers.get("content-type") || "")) {
        throw new Error("내부 편집기 파일을 읽을 수 없습니다. (HTTP " + response.status + ") 배포 파일과 편집기 주소 설정을 확인해 주세요.");
      }
    } catch (error) {
      if (controller.signal.aborted) throw new Error("내부 편집기 응답이 지연됩니다. 서버 연결을 확인한 뒤 다시 연결해 주세요.");
      throw error;
    } finally { clearTimeout(timer); }
  }
  async function connectEditor() {
    if (state.checkingEditor) return;
    state.checkingEditor = true;
    clearTimeout(state.connectTimer);
    state.ready = false;
    if (state.pendingExport) {
      clearTimeout(state.pendingExport.timer);
      state.pendingExport.reject(new Error("편집기를 다시 연결했습니다."));
      state.pendingExport = null;
    }
    showCover("도면 편집기를 준비하고 있습니다.", "도형 · 정렬 안내선 · 텍스트 · 여러 페이지");
    updateControls();
    try {
      await verifyLocalEditor();
      frame.src = state.editorUrl;
      state.checkingEditor = false;
      state.connectTimer = setTimeout(() => {
        if (state.ready) return;
        showCover("편집기에 연결하지 못했습니다.", state.selfHosted
          ? "내부 편집기의 배포 파일 또는 브라우저 차단 설정을 확인해 주세요. 현재 도면은 유지됩니다."
          : "외부 편집기 접속이 차단되었을 수 있습니다. 편집기 주소 설정과 네트워크를 확인해 주세요. 현재 도면은 유지됩니다.", true);
      }, state.selfHosted ? 45000 : 25000);
    } catch (error) {
      state.checkingEditor = false;
      showCover("도면 편집기를 불러오지 못했습니다.", error.message || "서버 연결을 확인해 주세요. 현재 도면은 유지됩니다.", true);
    }
  }
  async function receive(event) {
    if (state.checkingEditor || event.source !== frame.contentWindow || event.origin !== state.origin) return;
    let msg = event.data;
    if (typeof msg === "string") { try { msg = JSON.parse(msg); } catch { return; } }
    if (!msg || typeof msg !== "object") return;
    if (msg.event === "configure") {
      post({ action: "configure", config: { enableAi: false, autosaveDelay: 300, defaultGridEnabled: true, defaultGridSize: 10, enablePositionGuides: true, enableDistanceGuides: true, enableSizeGuides: true, defaultPageVisible: true, defaultPageBackgroundColor: "#ffffff", defaultLibraries: "general;basic;arrows2;flowchart", defaultFonts: ["Arial", "Malgun Gothic", "Noto Sans KR"], settingsName: "hiddenbath-product-drawing", includeDiagram: true } });
    } else if (msg.event === "init") {
      clearTimeout(state.connectTimer);
      state.ready = true;
      try { await loadEditor(state.xml || blankXml, !state.savedSignature); }
      catch (error) { notice(error.message, "error"); }
    } else if (msg.event === "load") {
      aiLog(state.pendingLoad?.traceId, "EDITOR_LOAD_RECEIVED", { error: msg.error ? String(msg.error) : null, currentPage: Number.isFinite(msg.currentPage) ? msg.currentPage : null });
      finishLoad(msg.error ? new Error("도면을 불러오지 못했습니다. " + msg.error) : null);
    } else if (msg.event === "autosave") {
      // This only updates browser state; it deliberately never sends a PUT.
      if (!state.loading && typeof msg.xml === "string") {
        try { adoptXml(msg.xml); } catch (error) { notice(error.message, "error"); }
      }
    } else if (msg.event === "save") {
      if (!state.busy && !state.loading && typeof msg.xml === "string") await save(msg.xml);
    } else if (msg.event === "export") {
      const pending = state.pendingExport;
      const id = resolveMessageId(msg.message);
      if (pending && id === pending.id) {
        clearTimeout(pending.timer); state.pendingExport = null;
        if (msg.error || (pending.format === "xml" && typeof msg.xml !== "string")) {
          aiLog(pending.traceId, "EDITOR_EXPORT_FAILED", { format: pending.format, editorRequestId: pending.id, message: msg.error ? String(msg.error) : "XML response missing" });
          pending.reject(new Error("도면 내보내기에 실패했습니다."));
        }
        else pending.resolve(msg);
      } else if (!pending && !id && !state.loading) {
        // Native File > Export uses the same protocol and is an explicit download.
        try { downloadExport(msg); } catch (error) { notice(error.message, "error"); }
      }
    } else if (msg.event === "exit") {
      await leavePage();
    } else if (msg.error === "unknownMessage") {
      // Custom/self-hosted older editors must implement the documented XML export.
      let incoming = msg.data;
      if (typeof incoming === "string") { try { incoming = JSON.parse(incoming); } catch { return; } }
      if (incoming?.pmdRequestId === state.pendingExport?.id) {
        aiLog(state.pendingExport.traceId, "EDITOR_PROTOCOL_FAILED", { editorRequestId: state.pendingExport.id, format: state.pendingExport.format, message: "unknownMessage" });
        clearTimeout(state.pendingExport.timer);
        state.pendingExport.reject(new Error("이 편집기 버전은 XML 내보내기를 지원하지 않습니다. 최신 draw.io 편집기로 설정해 주세요."));
        state.pendingExport = null;
      }
    }
  }

  async function pageGraph(page) {
    const existing = page.querySelector("mxGraphModel");
    if (existing) return existing;
    if (typeof DecompressionStream !== "function") throw new Error("이 브라우저는 압축 도면 불러오기를 지원하지 않습니다. 최신 Chrome·Edge를 사용하거나 미압축 XML로 내보낸 파일을 이용해 주세요.");
    const bytes = Uint8Array.from(atob(page.textContent.trim()), (char) => char.charCodeAt(0));
    const reader = new Blob([bytes]).stream().pipeThrough(new DecompressionStream("deflate-raw")).getReader();
    const chunks = [];
    let total = 0;
    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        total += value.byteLength;
        if (total > 6 * 1024 * 1024) { await reader.cancel(); throw new Error("압축 해제 후 도면 페이지가 너무 큽니다. 이미지를 줄이거나 불필요한 요소를 정리해 주세요."); }
        chunks.push(value);
      }
    } finally { reader.releaseLock(); }
    const decoded = decodeURIComponent(await new Blob(chunks).text());
    if (new Blob([decoded]).size > 2 * 1024 * 1024) throw new Error("도면 페이지는 압축 해제 후 2MB 이내로 등록해 주세요.");
    if (/<!DOCTYPE|<!ENTITY/i.test(decoded)) throw new Error("외부 문서 선언이 포함된 도면은 불러올 수 없습니다.");
    const graph = new DOMParser().parseFromString(decoded, "application/xml");
    if (graph.querySelector("parsererror") || graph.documentElement.nodeName !== "mxGraphModel") throw new Error("압축 도면의 페이지를 해석할 수 없습니다.");
    return graph.documentElement;
  }
  async function appendPages(currentXml, importedXml) {
    const current = parseDiagram(currentXml);
    const source = parseDiagram(importedXml);
    const currentPages = Array.from(current.documentElement.children).filter((node) => node.nodeName === "diagram");
    const sourcePages = Array.from(source.documentElement.children).filter((node) => node.nodeName === "diagram");
    // A brand-new, untouched empty page is a placeholder, not a user drawing.
    if (state.version === null && !state.dirty && currentPages.length === 1 && currentPages[0].getAttribute("name") === "정면도") {
      const graph = await pageGraph(currentPages[0]);
      const cells = Array.from(graph.querySelector("root")?.children || []);
      if (cells.length === 2 && cells.every((cell) => cell.nodeName === "mxCell" && ["0", "1"].includes(cell.getAttribute("id")) && !cell.getAttribute("value"))) {
        currentPages[0].remove(); currentPages.length = 0;
      }
    }
    if (currentPages.length + sourcePages.length > maxPages) throw new Error("페이지 추가 후 40개를 초과합니다. 불필요한 페이지를 삭제하거나 전체 교체를 선택해 주세요.");
    const names = new Set(currentPages.map((page) => page.getAttribute("name")));
    const idMap = new Map(sourcePages.filter((page) => page.getAttribute("id")).map((page) => [page.getAttribute("id"), "copy-" + crypto.randomUUID()]));
    let decodedTotal = 0;
    for (const page of sourcePages) {
      const copy = current.importNode(page, true);
      copy.setAttribute("id", idMap.get(page.getAttribute("id")) || "copy-" + crypto.randomUUID());
      // Decode copied pages only. Existing compressed diagrams remain intact.
      if (!copy.querySelector("mxGraphModel") && copy.textContent.trim()) {
        copy.replaceChildren(current.importNode(await pageGraph(copy), true));
      }
      decodedTotal += new Blob([new XMLSerializer().serializeToString(copy)]).size;
      if (decodedTotal > 8 * 1024 * 1024) throw new Error("불러오는 도면의 전체 페이지가 너무 큽니다. 페이지나 이미지 크기를 줄여 주세요.");
      // draw.io links between pages are stored in cell/object attributes.
      for (const node of copy.querySelectorAll("*")) {
        for (const attr of Array.from(node.attributes)) {
          const value = attr.value.replace(/data:page\/([^\s"'<>;&]+)/g, (match, oldId) => idMap.has(oldId) ? "data:page/" + idMap.get(oldId) : match);
          if (value !== attr.value) node.setAttribute(attr.name, value);
        }
      }
      const original = page.getAttribute("name") || "도면";
      let name = original, suffix = 2;
      while (names.has(name)) name = original + " (" + suffix++ + ")";
      names.add(name); copy.setAttribute("name", name);
      current.documentElement.append(copy);
    }
    const result = new XMLSerializer().serializeToString(current);
    parseDiagram(result);
    return result;
  }
  async function applyImported(xml, mode, description) {
    parseDiagram(xml);
    const current = await snapshot();
    if (mode === "replace" && !window.confirm(description + "으로 전체 도면을 교체하시겠습니까?\n현재 모든 페이지가 교체됩니다. 제품에 반영하려면 도면 등록을 눌러 주세요.")) {
      aiLog(state.aiActiveTraceId, "DRAFT_APPLICATION_CANCELED", { mode });
      return false;
    }
    const next = mode === "replace" ? xml : await appendPages(current, xml);
    await loadEditor(next);
    state.dirty = true;
    post({ action: "status", message: "불러온 초안입니다. 제품에 반영하려면 도면 등록을 눌러 주세요.", modified: true });
    updateControls();
    aiLog(state.aiActiveTraceId, "DRAFT_APPLICATION_COMPLETE", { mode, productSaved: false, ...drawingMetadata(state.xml) });
    notice(description + (mode === "replace" ? "으로 도면을 교체했습니다." : "을 새 페이지로 추가했습니다.") + " 내용을 확인한 뒤 ‘도면 등록’을 눌러 주세요.", "ok");
    return true;
  }
  async function searchTemplates(reset) {
    state.searchAbort?.abort();
    const controller = new AbortController();
    state.searchAbort = controller;
    const seq = ++state.searchSeq;
    if (reset) {
      state.searchPage = 0; state.searchQuery = $("#pmd-query").value.trim();
      $("#pmd-template-results").replaceChildren();
      $("#pmd-more").hidden = true;
    }
    const requestedPage = reset ? 0 : state.searchPage + 1;
    const status = $("#pmd-search-status");
    status.textContent = "등록된 도면을 검색하고 있습니다.";
    status.dataset.error = "false";
    $("#pmd-more").disabled = true;
    try {
      const params = new URLSearchParams({ q: state.searchQuery, page: String(requestedPage), size: "20" });
      const data = await api("/templates?" + params, "GET", undefined, controller.signal);
      if (seq !== state.searchSeq) return;
      state.searchPage = requestedPage;
      for (const item of data.items || []) {
        const row = document.createElement("div"); row.className = "pmd-template";
        const content = document.createElement("div");
        const title = document.createElement("strong"); title.textContent = item.label;
        const sub = document.createElement("small");
        sub.textContent = (item.kind === "products" ? "규격 제품" : "비규격 실제 제품") + " · ID " + item.targetId + " · " + (item.pageCount || 1) + "페이지" + (item.updatedAt ? " · " + displayDate(item.updatedAt) : "");
        content.append(title, sub);
        const button = document.createElement("button"); button.type = "button"; button.textContent = "불러오기";
        button.addEventListener("click", () => safeRun(async () => {
          try { await operation("importing", async () => {
          if (!["products", "actuals"].includes(item.kind) || !Number.isSafeInteger(Number(item.targetId))) throw new Error("도면 대상 정보가 올바르지 않습니다.");
          const source = await api("/" + item.kind + "/" + Number(item.targetId));
          if (!source.xml) throw new Error("선택한 도면이 삭제되었습니다. 다시 검색해 주세요.");
          const mode = $('input[name="pmd-template-mode"]:checked').value;
          if (await applyImported(source.xml, mode, source.label + " 도면")) $("#pmd-import-dialog").close();
          }, "제품 도면을 복사하고 있습니다."); }
          catch (error) { status.textContent = error.message; status.dataset.error = "true"; }
        }));
        row.append(content, button); $("#pmd-template-results").append(row);
      }
      state.hasNext = !!data.hasNext;
      $("#pmd-more").hidden = !state.hasNext;
      status.textContent = $("#pmd-template-results").childElementCount ? "규격 제품과 비규격 실제 제품의 도면을 모두 불러올 수 있습니다." : "검색 결과가 없습니다. 도면이 등록된 제품만 표시됩니다.";
    } catch (error) {
      if (error.name !== "AbortError" && seq === state.searchSeq) { status.textContent = error.message; status.dataset.error = "true"; }
    } finally {
      if (seq === state.searchSeq) $("#pmd-more").disabled = false;
    }
  }
  function checkImage(file) {
    if (!file) throw new Error("참고 이미지를 선택해 주세요.");
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) throw new Error("PNG·JPG·WEBP 이미지를 선택해 주세요.");
    if (file.size > 10 * 1024 * 1024 || file.size === 0) throw new Error("참고 이미지는 0보다 크고 10MB 이내여야 합니다.");
  }
  function clearPreview() {
    if (state.previewUrl) URL.revokeObjectURL(state.previewUrl);
    state.previewUrl = null;
    $("#pmd-image-preview img").removeAttribute("src");
    $("#pmd-image-preview").hidden = true;
  }
  async function normaliseAiImage(file) {
    checkImage(file);
    if (file.type !== "image/webp") return file;
    let bitmap;
    try {
      bitmap = await createImageBitmap(file);
      if (bitmap.width > 20000 || bitmap.height > 20000 || bitmap.width * bitmap.height > 24000000) throw new Error("이미지가 너무 큽니다. 2,400만 픽셀 이하의 이미지를 선택해 주세요.");
      const ratio = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
      const canvas = document.createElement("canvas");
      canvas.width = Math.max(1, Math.round(bitmap.width * ratio));
      canvas.height = Math.max(1, Math.round(bitmap.height * ratio));
      const context = canvas.getContext("2d");
      if (!context) throw new Error("브라우저에서 이미지를 변환할 수 없습니다. PNG·JPG로 저장한 뒤 다시 선택해 주세요.");
      context.fillStyle = "#ffffff"; context.fillRect(0, 0, canvas.width, canvas.height);
      context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
      const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
      if (!blob || blob.size > 10 * 1024 * 1024) throw new Error("이미지를 처리할 수 없습니다. PNG·JPG로 저장한 뒤 다시 선택해 주세요.");
      return new File([blob], file.name.replace(/\.[^.]+$/, "") + ".png", { type: "image/png" });
    } catch (error) {
      if (error instanceof DOMException) throw new Error("WEBP 이미지를 읽을 수 없습니다. PNG·JPG로 저장한 뒤 다시 선택해 주세요.");
      throw error;
    } finally { bitmap?.close(); }
  }
  async function generateDraft() {
    if (state.busy || state.loading || state.aiAbort) return;
    const status = $("#pmd-ai-status");
    status.dataset.error = "false";
    const file = $("#pmd-ai-file").files[0];
    const requestedTraceId = crypto.randomUUID();
    let traceId = requestedTraceId;
    aiLog(traceId, "CLIENT_FILE_SELECTED", { targetKind: kind, targetId, file: fileMetadata(file) });
    try { checkImage(file); }
    catch (error) {
      aiLog(traceId, "CLIENT_VALIDATION_FAILED", { message: error.message });
      status.textContent = error.message; status.dataset.error = "true"; return;
    }
    const controller = new AbortController();
    state.aiAbort = controller;
    state.aiActiveTraceId = traceId;
    const mode = $('input[name="pmd-ai-mode"]:checked').value;
    const instruction = $("#pmd-ai-instruction").value.trim();
    $("#pmd-ai-submit").disabled = true;
    $("#pmd-ai-cancel").hidden = false;
    status.textContent = "이미지를 해석해 편집 가능한 도형으로 구성하고 있습니다. 잠시 기다려 주세요.";
    try {
      await operation("ai", async () => {
        const upload = await normaliseAiImage(file);
        if (controller.signal.aborted) return;
        aiLog(traceId, "CLIENT_UPLOAD_NORMALIZED", { original: fileMetadata(file), upload: fileMetadata(upload), converted: upload !== file });
        const data = new FormData(); data.append("file", upload); data.append("instruction", instruction); data.append("traceId", requestedTraceId);
        aiLog(traceId, "DRAFT_REQUEST_SENT", { targetKind: kind, targetId, mode, instructionChars: instruction.length, upload: fileMetadata(upload), timeoutMs: 330000 });
        const draft = await api("/draft", "POST", data, controller.signal, 330000);
        if (controller.signal.aborted) return;
        traceId = safeTraceId(draft?.traceId) || requestedTraceId;
        state.aiActiveTraceId = traceId;
        const warnings = Array.isArray(draft?.warnings) ? draft.warnings : [];
        aiLogXml(traceId, "DRAFT_RESPONSE_RECEIVED", draft?.xml, { requestedTraceId, summary: draft?.summary || "", warnings });
        if (!draft || typeof draft.xml !== "string") throw new Error("도면 초안 응답을 읽을 수 없습니다. 다시 시도해 주세요.");
        const applied = await applyImported(draft.xml, mode, "AI 초안");
        if (applied) state.aiLastTraceId = traceId;
        status.textContent = [draft.summary || "초안을 생성했습니다.", ...warnings.map((text) => "• " + text)].join("\n");
        if (applied) {
          $("#pmd-ai-dialog").close();
          notice(["AI 초안을 적용했습니다. 구조와 실제 치수를 확인한 뒤 ‘도면 등록’을 눌러 주세요.", draft.summary || "", ...warnings.map((text) => "• " + text)].filter(Boolean).join("\n"), "ok");
        }
      }, "AI가 제품 도면의 기본 구조를 만들고 있습니다.");
    } catch (error) {
      traceId = safeTraceId(error.traceId) || traceId;
      aiLog(traceId, controller.signal.aborted ? "DRAFT_REQUEST_CANCELED" : "DRAFT_REQUEST_FAILED", { requestedTraceId, status: error.status || null, message: error.message });
      status.textContent = controller.signal.aborted ? "초안 생성을 취소했습니다. 기존 도면은 유지됩니다." : errorWithTrace(error, traceId);
      status.dataset.error = String(!controller.signal.aborted);
    } finally {
      state.aiAbort = null;
      state.aiActiveTraceId = null;
      $("#pmd-ai-submit").disabled = false;
      $("#pmd-ai-cancel").hidden = true;
      updateControls();
    }
  }
  async function reloadLatest() {
    return operation("latest", async () => {
      if (state.ready) await snapshot();
      if ((state.dirty || state.maybeDirty) && !window.confirm("미등록 작업을 버리고 서버의 최신 도면을 불러오시겠습니까?\n작업을 보관하려면 먼저 ‘현재 작업 백업’을 눌러 주세요.")) return;
      const latest = await api(targetPath);
      const xml = latest.xml || blankXml;
      parseDiagram(xml);
      // Only update the baseline/version after the latest document actually opens.
      await loadEditor(xml, true);
      state.version = latest.version ?? null; state.updatedAt = latest.updatedAt;
      state.conflict = false; state.dirty = false; state.maybeDirty = false;
      notice("최신 등록 도면을 불러왔습니다.", "ok");
    }, "서버의 최신 도면을 확인하고 있습니다.");
  }
  async function leavePage() {
    if (state.busy || state.loading) { notice("현재 작업이 끝난 후 목록으로 이동해 주세요."); return; }
    await operation("snapshot", async () => {
      if (state.ready) await snapshot();
      if ((state.dirty || state.maybeDirty) && !window.confirm("등록되지 않은 도면 변경사항이 있습니다. 저장하지 않고 목록으로 이동하시겠습니까?")) return;
      state.dirty = false; state.maybeDirty = false;
      location.href = $("#pmd-back").href;
    }, "등록되지 않은 변경사항을 확인하고 있습니다.");
  }
  async function boot() {
    try {
      if (!["products", "actuals"].includes(kind) || !Number.isSafeInteger(targetId) || targetId < 1) throw new Error("제품 도면 대상 정보가 올바르지 않습니다.");
      state.editorUrl = buildEditorUrl();
      const back = new URL(root.dataset.backUrl || (kind === "products" ? "/admin/product-master/products" : "/admin/product-master/non-standard"), location.origin);
      if (back.origin === location.origin) $("#pmd-back").href = back.href;
      showCover("제품의 등록 도면을 불러오고 있습니다.");
      const record = await api(targetPath);
      state.xml = record.xml || blankXml;
      parseDiagram(state.xml);
      state.version = record.version ?? null; state.updatedAt = record.updatedAt;
      state.label = record.label || "제품 " + targetId;
      $("#pmd-product-label").textContent = (kind === "products" ? "규격 제품" : "비규격 실제 제품") + " · " + state.label;
      document.title = state.label + " | 제품 도면";
      await connectEditor();
    } catch (error) {
      notice(error.message, "error");
      showCover("제품 도면을 불러오지 못했습니다.", error.message, true, true);
    }
  }

  window.addEventListener("message", (event) => safeRun(() => receive(event)));
  window.addEventListener("beforeunload", (event) => {
    if (state.dirty || state.maybeDirty || state.busy) { event.preventDefault(); event.returnValue = ""; }
  });
  window.addEventListener("blur", () => {
    // Text being typed inside a cross-origin iframe can precede its debounced autosave.
    if (document.activeElement === frame && state.ready && !state.loading) state.maybeDirty = true;
  });
  $("#pmd-back").addEventListener("click", (event) => { event.preventDefault(); safeRun(leavePage); });
  $("#pmd-save").addEventListener("click", () => safeRun(() => save()));
  $("#pmd-xml").addEventListener("click", () => safeRun(() => operation("export", async () => downloadXml(await snapshot()), "전체 도면 파일을 준비하고 있습니다.")));
  ["png", "svg"].forEach((format) => $("#pmd-" + format).addEventListener("click", () => safeRun(() => operation("export", async () => {
    // Image exports can echo compressed XML; compare changes using a separate XML snapshot.
    await snapshot();
    const result = await exportFromEditor(format, { currentPage: true, scale: format === "png" ? 2 : 1, border: 16, background: "#ffffff", embedImages: true, embedFonts: false });
    downloadExport(result);
    notice("선택한 페이지를 " + format.toUpperCase() + "로 내려받았습니다.", "ok");
  }, "선택한 도면 페이지를 이미지로 만들고 있습니다."))));
  $("#pmd-help").addEventListener("click", () => $("#pmd-help-dialog").showModal());
  $("#pmd-import").addEventListener("click", () => { $("#pmd-import-dialog").showModal(); searchTemplates(true); });
  $("#pmd-ai").addEventListener("click", () => $("#pmd-ai-dialog").showModal());
  $$('[data-close]').forEach((button) => button.addEventListener("click", () => {
    const dialog = button.closest("dialog");
    if (state.aiAbort && dialog.id === "pmd-ai-dialog") state.aiAbort.abort();
    if (state.busy && dialog.id === "pmd-import-dialog") return;
    dialog.close();
  }));
  $$('dialog').forEach((dialog) => dialog.addEventListener("cancel", (event) => {
    if (state.busy && dialog.id === "pmd-import-dialog") event.preventDefault();
    if (dialog.id === "pmd-ai-dialog") state.aiAbort?.abort();
  }));
  $("#pmd-import-dialog").addEventListener("close", () => state.searchAbort?.abort());
  $("#pmd-search").addEventListener("submit", (event) => { event.preventDefault(); searchTemplates(true); });
  $("#pmd-more").addEventListener("click", () => { if (state.hasNext) searchTemplates(false); });
  $("#pmd-ai-form").addEventListener("submit", (event) => { event.preventDefault(); safeRun(generateDraft); });
  $("#pmd-ai-cancel").addEventListener("click", () => state.aiAbort?.abort());
  $("#pmd-ai-file").addEventListener("change", () => {
    clearPreview();
    const file = $("#pmd-ai-file").files[0];
    const status = $("#pmd-ai-status");
    status.textContent = ""; status.dataset.error = "false";
    try {
      checkImage(file); state.previewUrl = URL.createObjectURL(file);
      $("#pmd-image-preview img").src = state.previewUrl; $("#pmd-image-preview").hidden = false;
    } catch (error) { status.textContent = error.message; status.dataset.error = "true"; }
  });
  $("#pmd-open-file").addEventListener("click", () => $("#pmd-file").click());
  $("#pmd-file").addEventListener("change", () => safeRun(() => operation("importing", async () => {
    const file = $("#pmd-file").files[0];
    $("#pmd-file").value = "";
    if (!file) return;
    if (file.size > maxXmlBytes) throw new Error("도면 파일은 5MB 이내로 선택해 주세요.");
    const xml = await file.text();
    parseDiagram(xml);
    await applyImported(xml, "append", file.name + " 도면");
  }, "도면 파일의 페이지를 불러오고 있습니다.")));
  $("#pmd-conflict-backup").addEventListener("click", () => safeRun(() => operation("export", async () => downloadXml(state.ready ? await snapshot() : state.xml), "현재 작업을 백업하고 있습니다.")));
  $("#pmd-latest").addEventListener("click", () => safeRun(reloadLatest));
  $("#pmd-cover-backup").addEventListener("click", () => { try { downloadXml(); } catch (error) { notice(error.message, "error"); } });
  $("#pmd-retry-data").addEventListener("click", () => safeRun(boot));
  $("#pmd-retry").addEventListener("click", () => { if (!state.busy && !state.loading) connectEditor(); });
  $("#pmd-fullscreen").addEventListener("click", () => safeRun(async () => {
    try { if (document.fullscreenElement === root) await document.exitFullscreen(); else await root.requestFullscreen(); }
    catch { notice("브라우저에서 화면 확대를 허용하지 않았습니다."); }
  }));
  document.addEventListener("fullscreenchange", () => { $("#pmd-fullscreen").textContent = document.fullscreenElement === root ? "화면 축소" : "화면 확대"; });
  document.addEventListener("keydown", (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s" && !event.altKey) { event.preventDefault(); if (state.ready && !state.busy && !state.loading && !$$("dialog[open]").length) safeRun(() => save()); }
  });
  updateControls();
  safeRun(boot);
})();
