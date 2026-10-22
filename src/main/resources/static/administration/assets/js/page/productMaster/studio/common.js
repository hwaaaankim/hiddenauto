(function () {
  "use strict";
  const S = (window.PMS = {
    base: "/admin/api/product-master/studio",
    groups: [],
    dirty: false,
    pendingUploads: 0,
  });
  S.e = (v) =>
    String(v ?? "").replace(
      /[&<>"']/g,
      (c) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#39;",
        })[c],
    );
  S.copy = (v) => JSON.parse(JSON.stringify(v));
  S.key = (prefix) => {
    const key = prefix + "_" + crypto.randomUUID().replaceAll("-", "");
    S.openEditor?.(key);
    return key;
  };
  S.$ = (q, r = document) => r.querySelector(q);
  S.$$ = (q, r = document) => [...r.querySelectorAll(q)];
  S.controls = {
    RADIO: "하나 선택 · radio",
    CHECKBOX: "여러 개 선택 · checkbox",
    NUMBER: "숫자 입력",
    TEXT: "한 줄 텍스트",
    TEXTAREA: "긴 텍스트",
    FILE: "파일 입력",
  };
  S.isBase = (g) => ["CATEGORY", "SUBCATEGORY", "SERIES"].includes(g.role);
  S.isChoice = (t) => ["RADIO", "CHECKBOX"].includes(t);
  S.group = (id) => S.groups.find((g) => g.id === Number(id));
  S.status = {
    ACTIVE: "사용중",
    OUT_OF_STOCK: "재고없음",
    DISCONTINUED: "단종",
    DRAFT: "등록중",
  };
  S.badge = (text, color = "") =>
    `<span class="pms-tag ${S.e(color)}">${S.e(text)}</span>`;
  S.specSummary = (rows) => {
    if (!rows.length) return '<span class="pm-muted">—</span>';
    return `<button type="button" class="pm-spec-peek" data-spec-peek="${S.e(JSON.stringify(rows))}" aria-expanded="false" aria-label="실제 사양 전체 ${rows.length}개 보기"><span class="pm-spec-first"><span class="pm-spec-label">${S.e(rows[0].label)}</span><span>${S.e(rows[0].value)}</span></span>${rows.length > 1 ? `<span class="pm-spec-count">+${rows.length - 1}</span>` : ""}</button>`;
  };
  S.api = async (path, method = "GET", body, signal) => {
    const headers = { Accept: "application/json" };
    const opts = { method, headers, credentials: "same-origin", signal };
    const token = S.$('meta[name="_csrf"]')?.content,
      header = S.$('meta[name="_csrf_header"]')?.content;
    if (token && header) headers[header] = token;
    if (body !== undefined) {
      if (body instanceof FormData) opts.body = body;
      else {
        headers["Content-Type"] = "application/json";
        opts.body = JSON.stringify(body);
      }
    }
    const response = await fetch(
      path.startsWith("/") ? path : S.base + path,
      opts,
    );
    let data;
    try {
      data = await response.json();
    } catch {
      throw new Error(
        response.status === 401 || response.redirected
          ? "로그인이 만료되었습니다. 다시 로그인해 주세요."
          : "서버 응답을 읽을 수 없습니다. 상태 " + response.status,
      );
    }
    if (!response.ok || data.success === false) {
      const error = new Error(data.message || "요청 처리에 실패했습니다.");
      error.fieldErrors = data.fieldErrors;
      error.status = response.status;
      throw error;
    }
    return data.data === undefined ? data : data.data;
  };
  // API suffixes begin with '/'; pass them through this scoped helper.
  S.request = (path, method = "GET", body, signal) =>
    S.api(S.base + path, method, body, signal);
  S.notice = (text, ok = false) => {
    const el = S.$("#pms-notice");
    if (el)
      el.innerHTML = text
        ? `<div class="pms-alert ${ok ? "ok" : ""}">${S.e(text)}</div>`
        : "";
  };
  S.toast = (text) => {
    const el = document.createElement("div");
    el.className = "pms-toast";
    el.textContent = text;
    (S.$$("dialog[open]").at(-1) || document.body).append(el);
    el.setAttribute("role", "status");
    setTimeout(() => el.remove(), 3500);
  };
  S.run = async (button, fn) => {
    if (button?.disabled) return;
    if (S.pendingUploads) { S.toast("파일 업로드가 끝난 후 저장·진행해 주세요."); return; }
    if (button) button.disabled = true;
    const errorScope =
      button?.closest(".pms-dialog-body,.pms-panel") ||
      button?.closest("dialog")?.querySelector(".pms-dialog-body");
    if (errorScope) S.clearErrors?.(errorScope);
    try {
      return await fn();
    } catch (err) {
      const scope = errorScope || S.$$("dialog[open]").at(-1);
      if (!err.inlineShown) {
        if (scope) S.showErrors(scope, err.fieldErrors || { "": err.message });
        else S.notice(err.message);
      }
      S.toast(
        scope || err.inlineShown
          ? "저장·진행할 수 없습니다. 표시된 위치의 이유와 수정 안내를 확인해 주세요."
          : err.message,
      );
      console.error(err);
    } finally {
      if (button?.isConnected) button.disabled = false;
    }
  };
  S.catalog = async () => {
    S.groups = await S.request("/groups");
    S.overview?.();
    return S.groups;
  };
  S.field = (label, path, value, type = "text", extra = "") =>
    `<label><span>${S.e(label)}</span><input data-path="${S.e(path)}" type="${type}" value="${S.e(value)}" ${extra}></label>`;
  S.textarea = (label, path, value, max = 2000) =>
    `<label class="pms-textarea-field"><span>${S.e(label)}</span><textarea data-path="${S.e(path)}" rows="3" maxlength="${max}">${S.e(value)}</textarea></label>`;
  S.check = (label, path, value, css = "") =>
    `<label class="${css || "pms-check"}"><input type="checkbox" data-path="${S.e(path)}" ${value ? "checked" : ""}><span>${S.e(label)}</span></label>`;
  S.checkField = (title, label, path, value) =>
    `<div class="pms-control-field"><span class="pms-field-label">${S.e(title)}</span>${S.check(label, path, value)}</div>`;
  S.select = (label, path, value, options) =>
    `<label><span>${S.e(label)}</span><select data-path="${S.e(path)}">${Object.entries(
      options,
    )
      .map(
        ([key, text]) =>
          `<option value="${S.e(key)}" ${key === value ? "selected" : ""}>${S.e(text)}</option>`,
      )
      .join("")}</select></label>`;
  S.labels = (source, prefix, withName = false, name = "") => {
    const labels = source?.labels || source;
    const labelMax = withName ? 120 : 80;
    return `<div class="pms-form-grid ${withName ? "four" : ""}">${S.field("고객용 표시명", prefix + ".labels.customer", labels?.customer, "text", 'maxlength="' + labelMax + '" data-mirror="' + S.e(prefix) + '"')}${S.field("생산팀용 표시명", prefix + ".labels.production", labels?.production, "text", 'maxlength="' + labelMax + '"')}${S.field("관리팀용 표시명", prefix + ".labels.management", labels?.management, "text", 'maxlength="' + labelMax + '"')}${withName ? S.field("제품명 구성 문자 (빈칸 가능)", prefix + ".namePart", name, "text", 'maxlength="160"') : ""}</div>`;
  };
  S.customerLabel = (source, prefix = "", title = "질문 이름 (고객용)") =>
    S.field(title, prefix + ".labels.customer", source?.labels?.customer, "text", 'maxlength="80" data-customer-label');
  S.get = (obj, path) =>
    path
      .split(".")
      .filter(Boolean)
      .reduce((v, k) => v?.[k], obj);
  S.set = (obj, path, value) => {
    const keys = path.split(".").filter(Boolean);
    if (keys.some((k) => ["__proto__", "prototype", "constructor"].includes(k)))
      throw Error("잘못된 경로");
    const last = keys.pop();
    let target = obj;
    for (const k of keys) {
      if (target[k] === undefined) target[k] = {};
      target = target[k];
    }
    target[last] = value;
  };
  S.bind = (root, obj, changed = () => {}) => {
    root._pmsBinding?.abort();
    root._pmsBinding = new AbortController();
    const eventOptions = { signal: root._pmsBinding.signal };
    root.addEventListener(
      "input",
      (event) => {
        const el = event.target;
        if (
          !el.dataset.path ||
          el.tagName === "SELECT" ||
          el.type === "checkbox"
        )
          return;
        update(el);
      },
      eventOptions,
    );
    root.addEventListener(
      "change",
      (event) => {
        const el = event.target;
        if (!el.dataset.path) return;
        if (el.tagName === "SELECT" || el.type === "checkbox") update(el);
      },
      eventOptions,
    );
    function update(el) {
      const path = el.dataset.path;
      const old = S.get(obj, path);
      let value =
        el.type === "checkbox"
          ? el.checked
          : el.type === "number"
            ? el.value === ""
              ? null
              : Number(el.value)
            : el.value;
      if (el.dataset.mirror !== undefined) {
        const p = el.dataset.mirror;
        for (const suffix of [
          "labels.production",
          "labels.management",
          "namePart",
        ]) {
          const key = (p ? p + "." : "") + suffix,
            previous = S.get(obj, key);
          const input = S.$$("[data-path]", root).find(
            (x) => x.dataset.path === key || x.dataset.path === "." + key,
          );
          if (
            input &&
            (previous === old || previous === undefined || previous === null)
          ) {
            S.set(obj, key, value);
            input.value = value;
          }
        }
      }
      S.set(obj, path, value);
      if (el.hasAttribute("data-customer-label")) {
        const prefix = path.slice(0, -"customer".length);
        S.set(obj, prefix + "production", value);
        S.set(obj, prefix + "management", value);
      }
      S.dirty = true;
      changed(path, value, el);
    }
  };
  S.newField = () => ({
    guide: "",
    key: S.key("F"),
    labels: { customer: "", production: "", management: "" },
    namePart: "",
    bundleKey: null,
    assetIds: [],
    required: true,
    allowNegative: false,
    min: 0,
    max: null,
    step: 1,
    minLength: 0,
    maxLength: 500,
    format: "ANY",
    unit: "",
    extensions: ["jpg", "jpeg", "png", "pdf"],
    minFiles: 0,
    maxFiles: 5,
    maxFileMB: 10,
  });
  S.fieldRows = (fields, control, prefix = "fields") =>
    fields
      .map(
        (f, i) =>
          `<details class="pms-list-row pms-editor" ${S.editorAttrs(f.key, i === 0)} data-field-index="${i}"><summary class="pms-row-head"><span class="pms-handle" title="드래그하여 필드 순서를 변경합니다">⠿</span><strong data-editor-title>입력 ${i + 1} · ${S.e(f.labels?.management || "새 필드")}</strong><small class="mono">${S.e(f.key)}</small><button type="button" class="pms-remove" data-remove-field="${i}">×</button></summary><div class="pms-editor-body">${S.labels(f, prefix + "." + i, true, f.namePart)}${S.textarea("입력 시 안내메시지", prefix + "." + i + ".guide", f.guide || "", 2000)}<div class="pms-form-grid four pms-section">${S.checkField("입력 여부", "필수 입력", prefix + "." + i + ".required", f.required)}${control === "NUMBER" ? S.checkField("음수 입력", "음수 허용", prefix + "." + i + ".allowNegative", f.allowNegative) + S.field("단위", prefix + "." + i + ".unit", f.unit, "text", 'maxlength="20"') + S.field("입력 간격", prefix + "." + i + ".step", f.step, "number", 'min="0.001" step="0.001"') + S.field("최소값", prefix + "." + i + ".min", f.min, "number", 'step="0.001"') + S.field("최대값", prefix + "." + i + ".max", f.max, "number", 'step="0.001"') : control === "FILE" ? S.field("최소 개수", prefix + "." + i + ".minFiles", f.minFiles, "number", 'min="0" max="20"') + S.field("최대 개수", prefix + "." + i + ".maxFiles", f.maxFiles, "number", 'min="1" max="20"') + S.field("파일당 최대 MB", prefix + "." + i + ".maxFileMB", f.maxFileMB, "number", 'min="1" max="20"') : `${S.field("최소 글자 수", prefix + "." + i + ".minLength", f.minLength, "number", 'min="0" max="10000"')}${S.field("최대 글자 수", prefix + "." + i + ".maxLength", f.maxLength, "number", 'min="1" max="10000"')}${S.select("형식 검증", prefix + "." + i + ".format", f.format, { ANY: "제한 없음", EMAIL: "이메일", PHONE: "전화번호", ALPHANUMERIC: "영문·숫자" })}`}</div>${control === "FILE" ? `<div class="pms-check-grid" data-validation-path="${prefix}.${i}.extensions">${["jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "csv", "xlsx", "xls", "docx", "doc", "pptx", "ppt", "zip", "hwp", "hwpx"].map((ext) => `<label class="pms-check"><input type="checkbox" data-field-ext="${i}" value="${ext}" ${f.extensions.includes(ext) ? "checked" : ""}>${ext}</label>`).join("")}</div>` : ""}</div></details>`,
      )
      .join("");
  S.fieldEvents = (root, fields, rerender, prefix = "fields") => {
    S.trackEditors(root);
    root.addEventListener("click", (e) => {
      const button = e.target.closest("[data-remove-field]");
      if (button) {
        fields.splice(Number(button.dataset.removeField), 1);
        S.dirty = true;
        rerender();
      }
    });
    root.addEventListener("change", (e) => {
      if (e.target.dataset.fieldExt !== undefined) {
        const f = fields[Number(e.target.dataset.fieldExt)];
        f.extensions = S.$$(
          `[data-field-ext="${e.target.dataset.fieldExt}"]:checked`,
          root,
        ).map((x) => x.value);
        S.dirty = true;
      }
    });
    S.sortable(root, "[data-field-index]", (from, to) => {
      fields.splice(to, 0, fields.splice(from, 1)[0]);
      rerender();
    });
  };
  S.sortable = (root, selector, onMove) => {
    root._pmsSortable?.destroy?.();
    if (!window.Sortable)
      throw new Error(
        "순서 변경 기능을 불러오지 못했습니다. 화면을 새로고침해 주세요.",
      );
    root._pmsSortable = new Sortable(root, {
      draggable: selector,
      handle: ".pms-handle",
      animation: matchMedia("(prefers-reduced-motion: reduce)").matches
        ? 0
        : 220,
      easing: "cubic-bezier(.2,.7,.2,1)",
      ghostClass: "pms-sort-ghost",
      chosenClass: "pms-sort-chosen",
      dragClass: "pms-sort-drag",
      fallbackClass: "pms-sort-fallback",
      forceFallback: true,
      fallbackOnBody: false,
      fallbackTolerance: 4,
      delay: 100,
      delayOnTouchOnly: true,
      touchStartThreshold: 5,
      scroll: true,
      scrollSensitivity: 70,
      scrollSpeed: 12,
      onStart: () => {
        root._pmsDirtyBeforeDrag = S.dirty;
        document.body.classList.add("pms-sorting");
      },
      onEnd: async (event) => {
        document.body.classList.remove("pms-sorting");
        const from = event.oldDraggableIndex,
          to = event.newDraggableIndex;
        if (from === to || from == null || to == null) return;
        try {
          S.dirty = true;
          await onMove(from, to);
          const moved = S.$$(selector, root)[to];
          moved?.classList.add("pms-moved");
          setTimeout(() => moved?.classList.remove("pms-moved"), 850);
          S.toast(`${from + 1}번째 항목을 ${to + 1}번째 위치로 이동했습니다.`);
        } catch (error) {
          S.notice(error.message);
          S.toast(error.message);
        }
      },
    });
  };
  let dialogSequence = 0;
  S.dialog = (title, body, { wide = true, foot = "", onClose } = {}) => {
    const el = document.createElement("dialog");
    el.className = "pms pms-dialog" + (wide ? "" : " narrow");
    el.innerHTML = `<header class="pms-dialog-head"><h2>${S.e(title)}</h2><button type="button" data-close aria-label="닫기">×</button></header><div class="pms-dialog-body">${body}</div>${foot ? `<footer class="pms-dialog-foot">${foot}</footer>` : ""}`;
    const titleId = "pms-dialog-title-" + ++dialogSequence;
    S.$("h2", el).id = titleId;
    el.setAttribute("aria-labelledby", titleId);
    document.body.append(el);
    S.$("[data-close]", el).onclick = () => el.close();
    el.addEventListener("close", () => {
      S.$$("[data-pms-upload-scope]", el).forEach((scope) => scope._pmsFileState?.controller?.abort());
      onClose?.();
      el.remove();
    });
    el.showModal();
    return el;
  };
  S.confirm = async (text) =>
    new Promise((resolve) => {
      const dialog = S.dialog("확인", `<p>${S.e(text)}</p>`, {
        wide: false,
        foot: '<button data-no>취소</button><button class="primary" data-yes>계속</button>',
        onClose: () => resolve(false),
      });
      S.$("[data-no]", dialog).onclick = () => dialog.close();
      S.$("[data-yes]", dialog).onclick = () => {
        resolve(true);
        dialog.close();
      };
    });
  const fileDrafts = new WeakMap();
  const fileIcon = '<svg viewBox="0 0 24 24" width="32" height="32" aria-hidden="true"><path d="M5 3h9l5 5v13H5zM14 3v6h5M8 13h8M8 17h6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/></svg>';
  const fileAccept = ".jpg,.jpeg,.png,.gif,.webp,.pdf,.txt,.csv,.xlsx,.xls,.docx,.doc,.pptx,.ppt,.zip,.hwp,.hwpx";
  S.files = (files, editable = true, options = {}) => {
    const rows = (files || []).filter(Boolean), images = rows.filter((f) => f.image);
    return `<section class="pm-upload-area ${editable ? "editable" : "readonly"}" aria-label="이미지·첨부파일"><header class="pm-upload-heading"><div><strong>${editable ? "이미지·파일 등록" : "등록된 이미지·파일"}</strong><small>${rows.length}개 · 이미지를 누르면 확대해서 볼 수 있습니다.</small></div>${editable ? '<button type="button" data-cancel-files hidden>등록 변경 취소</button>' : ""}</header><div class="pms-files" data-pm-gallery="${S.e(JSON.stringify(images))}" data-pm-gallery-title="등록된 이미지">${rows.map((f, i) => `<article class="pms-file">${f.image && f.url ? `<button type="button" class="pms-file-image" data-pm-image="${images.indexOf(f)}" aria-label="${S.e(f.name)} 미리보기 확대"><img src="${S.e(f.url)}" alt="${S.e(f.name)}" loading="lazy"></button>` : `<div class="pm-file-placeholder">${fileIcon}<span>${S.e((f.name || "파일").split(".").pop().toUpperCase())}</span></div>`}<div class="pm-file-copy">${f.url ? `<a href="${S.e(f.url)}${f.url.includes("?") ? "&" : "?"}download=true" target="_blank" rel="noopener">${S.e(f.name)}</a>` : `<strong>${S.e(f.name || "등록된 파일")}</strong>`}<small>${f.image ? "이미지 미리보기" : "첨부파일"}</small></div>${editable ? `<div class="pm-file-actions"><button type="button" data-replace-file="${i}" aria-label="${S.e(f.name)} 새 파일로 교체">교체</button><button type="button" data-remove-file="${i}" class="danger" aria-label="${S.e(f.name)} 등록 목록에서 삭제">삭제</button></div>` : ""}</article>`).join("")}${!rows.length ? `<div class="pm-upload-empty">${fileIcon}<strong>${editable ? "등록할 이미지나 파일을 추가해 주세요." : "등록된 파일이 없습니다."}</strong>${editable ? "<span>아래 영역을 누르거나 파일을 끌어 놓으세요.</span>" : ""}</div>` : ""}</div>${editable ? `<div class="pms-drop" tabindex="0" role="button" aria-label="이미지·파일 선택"><span class="pm-upload-add" aria-hidden="true">＋</span><span><strong>이미지·파일 선택</strong><small>클릭 또는 드래그하여 추가 · 여러 파일 선택 가능</small></span><input type="file" multiple hidden accept="${S.e(options.accept || fileAccept)}" ${options.answerField ? `data-answer-field="${S.e(options.answerField)}"` : ""}></div><input type="file" data-replace-picker hidden><p class="pms-help pm-upload-help">삭제·교체는 현재 등록 목록에만 적용됩니다. <strong>${options.answerField ? "다음 질문을 누를 때 답변으로 반영됩니다." : "해당 저장 버튼을 눌러야 최종 반영됩니다."}</strong> ‘등록 변경 취소’로 이 영역의 변경을 되돌릴 수 있습니다.</p><span class="pm-upload-status" role="status" aria-live="polite"></span>` : ""}${options.answerField ? `<small data-answer-files="${S.e(options.answerField)}">${rows.length ? rows.length + "개 파일 등록됨" : ""}</small>` : ""}</section>`;
  };
  S.fileEvents = (root, files, updated = () => {}, uploadPath = S.base + "/assets/stage", options = {}) => {
    if (!root) return;
    root.dataset.pmsUploadScope = "";
    let state = fileDrafts.get(files);
    if (!state) { state = { baseline: files.slice(), controller: null, serial: 0, busy: false }; fileDrafts.set(files, state); }
    state.files = files;
    root._pmsFileState = state;
    root._pmsFileOptions = options;
    const drop = S.$(".pms-drop", root), input = S.$(".pms-drop input[type=file]", root), replacement = S.$("[data-replace-picker]", root);
    if (!drop || !input) { S.bindMedia?.(root); return; }
    const accept = options.accept || input.accept;
    input.accept = accept;
    const changed = () => JSON.stringify(files.map((f) => f.id)) !== JSON.stringify(state.baseline.map((f) => f.id));
    const refreshStatus = () => {
      const cancel = S.$("[data-cancel-files]", root);
      if (cancel) { cancel.hidden = !changed() && !state.busy; cancel.textContent = state.busy ? "업로드 취소" : "등록 변경 취소"; }
      root.setAttribute("aria-busy", String(state.busy));
      S.$$("[data-remove-file],[data-replace-file]", root).forEach((b) => { b.disabled = state.busy; });
      drop.setAttribute("aria-disabled", String(state.busy));
      const status = S.$(".pm-upload-status", root);
      if (status) status.textContent = state.busy ? "파일을 업로드하고 있습니다…" : changed() ? "등록 목록이 변경되었습니다. 저장하거나 변경을 취소해 주세요." : "";
    };
    function render() {
      if (!root.isConnected) return;
      root.innerHTML = S.files(files, true, { ...options, accept });
      S.fileEvents(root, files, updated, uploadPath, { ...options, accept });
    }
    async function add(selected, index = null) {
      if (!selected.length || state.busy) return;
      if (index != null && selected.length !== 1) return S.toast("교체할 파일은 하나만 선택해 주세요.");
      if (options.maxFiles != null && files.length + selected.length - (index == null ? 0 : 1) > options.maxFiles)
        return S.toast(`파일은 최대 ${options.maxFiles}개까지 등록할 수 있습니다. 기존 파일을 삭제한 뒤 추가해 주세요.`);
      const allowed = accept.split(",").map((ext) => ext.trim().replace(/^\./, "").toLowerCase()).filter(Boolean);
      for (const file of selected) {
        const extension = file.name.split(".").pop().toLowerCase();
        if (allowed.length && !allowed.includes(extension)) return S.toast(`${file.name}: 허용된 파일 형식을 선택해 주세요.`);
        if (file.size > (options.maxFileMB ?? 20) * 1024 * 1024) return S.toast(`${file.name}: 파일당 ${options.maxFileMB ?? 20}MB 이하로 등록해 주세요.`);
      }
      const selectedId = index == null ? null : files[index]?.id, body = new FormData();
      selected.forEach((file) => body.append("files", file));
      const serial = ++state.serial;
      state.controller = new AbortController(); state.busy = true; root._pmsUploading = true; S.pendingUploads++;
      options.onBusy?.(true); refreshStatus();
      let failure = null;
      try {
        const added = await S.api(uploadPath, "POST", body, state.controller.signal);
        if (serial !== state.serial || !root.isConnected || options.isCurrent?.() === false) return;
        if (!Array.isArray(added) || !added.length) throw Error("업로드한 파일을 확인해 주세요.");
        if (index == null) files.push(...added);
        else {
          const currentIndex = files.findIndex((f) => f.id === selectedId);
          if (currentIndex < 0) return;
          files.splice(currentIndex, 1, added[0]);
        }
        S.dirty = true; updated();
      } catch (e) { if (e.name !== "AbortError") failure = e.message; }
      finally {
        S.pendingUploads = Math.max(0, S.pendingUploads - 1);
        if (serial === state.serial) { state.busy = false; state.controller = null; root._pmsUploading = false; }
        options.onBusy?.(false); render();
        if (failure && root.isConnected) { if (options.onError) options.onError(failure); else S.showErrors(root, { "": failure }); }
      }
    }
    drop.onclick = () => { if (!state.busy) { input.value = ""; input.click(); } };
    input.onclick = (e) => e.stopPropagation();
    drop.onkeydown = (e) => { if ((e.key === "Enter" || e.key === " ") && !state.busy) { e.preventDefault(); input.value = ""; input.click(); } };
    input.onchange = () => add([...input.files]);
    drop.ondragover = (e) => { e.preventDefault(); if (!state.busy) drop.classList.add("over"); };
    drop.ondragleave = () => drop.classList.remove("over");
    drop.ondrop = (e) => { e.preventDefault(); e.stopPropagation(); drop.classList.remove("over"); add([...e.dataTransfer.files]); };
    S.$$("[data-replace-file]", root).forEach((b) => { b.onclick = () => {
      if (state.busy) return;
      replacement.dataset.replaceIndex = b.dataset.replaceFile; replacement.accept = accept; replacement.value = ""; replacement.click();
    }; });
    replacement.onchange = () => add([...replacement.files], Number(replacement.dataset.replaceIndex));
    S.$$("[data-remove-file]", root).forEach((b) => { b.onclick = () => {
      if (state.busy) return;
      files.splice(Number(b.dataset.removeFile), 1); S.dirty = true; updated(); render();
    }; });
    S.$("[data-cancel-files]", root).onclick = () => {
      if (state.busy) { state.controller?.abort(); return; }
      files.splice(0, files.length, ...state.baseline); S.dirty = true; updated(); render();
    };
    S.bindMedia?.(root); refreshStatus();
  };
  S.commitFileDrafts = (root) => S.$$("[data-pms-upload-scope]", root).forEach((scope) => {
    if (scope._pmsFileState) scope._pmsFileState.baseline = (scope._pmsFileState.files || []).slice();
  });
  S.pager = (page, total, action = "page") => {
    let start = Math.floor(page / 5) * 5;
    return `<div class="pms-pager"><button data-${action}="0" ${page <= 0 ? "disabled" : ""}>FIRST</button><button data-${action}="${Math.max(0, page - 1)}" ${page <= 0 ? "disabled" : ""}>PREV</button>${Array.from({ length: Math.max(0, Math.min(5, total - start)) }, (_, i) => `<button data-${action}="${start + i}" class="${page === start + i ? "current" : ""}">${start + i + 1}</button>`).join("")}<button data-${action}="${page + 1}" ${page >= total - 1 ? "disabled" : ""}>NEXT</button><button data-${action}="${Math.max(0, total - 1)}" ${page >= total - 1 ? "disabled" : ""}>LAST</button></div>`;
  };
  S.labelSamples = (f) => `<div class="pm-live-sample"><strong>실제 표시 예시</strong><div class="pm-label-examples">${Object.entries({ customer: "고객", production: "생산팀", management: "관리팀" }).map(([key, name]) => `<span><small>${name} 화면</small><b>${S.e(f.labels?.[key] || "표시명")}</b> : <em>___</em>${f.unit ? ` <span>(${S.e(f.unit)})</span>` : ""}</span>`).join("")}</div><small>표시명은 입력칸 앞에, 단위는 입력한 값 뒤에 표시됩니다. 저장되는 숫자는 세 화면에서 같습니다.</small></div>`;
  S.inputAnswer = (q, answer = {}, disabled = false, assets = new Map()) => {
    const choices = answer.choices || [], fields = answer.fields || {};
    if (S.isChoice(q.control))
      return `<div class="pms-check-grid">${(q.choices || []).map((c) => `<label class="pms-check"><input type="${q.control === "RADIO" ? "radio" : "checkbox"}" name="q-${S.e(q.key)}" data-answer-choice="${S.e(c.key)}" ${choices.includes(c.key) ? "checked" : ""} ${disabled ? "disabled" : ""}><span>${S.e(c.labels?.customer || c.key)}</span></label>`).join("")}</div>`;
    const resolve = (id) => assets instanceof Map ? assets.get(id) : assets?.[id];
    function fieldHtml(f) {
      const attr = `data-answer-field="${S.e(f.key)}" aria-label="${S.e(f.labels?.customer || f.key)}" ${f.required ? 'aria-required="true"' : ""} ${disabled ? "disabled" : ""}`;
      let input;
      if (q.control === "TEXTAREA") input = `<textarea ${attr} rows="4" maxlength="${f.maxLength ?? 500}">${S.e(fields[f.key])}</textarea>`;
      else if (q.control === "FILE") {
        const ids = Array.isArray(fields[f.key]) ? fields[f.key] : [];
        const list = ids.map((id) => resolve(id) || { id, name: "등록된 파일", image: false });
        input = `<div data-answer-upload="${S.e(f.key)}">${S.files(list, !disabled, { answerField: f.key, accept: (f.extensions || []).map((x) => "." + x).join(",") })}</div><small>허용 형식: ${S.e((f.extensions || []).join(", "))} · 최대 ${f.maxFiles ?? 5}개 · 파일당 ${f.maxFileMB ?? 10}MB</small>`;
      } else {
        const number = q.control === "NUMBER";
        input = `<div class="pm-answer-value"><input ${attr} type="${number ? "number" : "text"}" value="${S.e(fields[f.key])}" ${number ? `step="${f.step ?? 1}" ${f.min != null ? `min="${f.min}"` : !f.allowNegative ? 'min="0"' : ""} ${f.max != null ? `max="${f.max}"` : ""}` : `maxlength="${f.maxLength ?? 500}"`}>${f.unit ? `<span class="pm-answer-unit">(${S.e(f.unit)})</span>` : ""}</div>`;
      }
      return `<div class="pm-answer-field ${q.control === "TEXTAREA" || q.control === "FILE" ? "wide" : ""}" data-field-key="${S.e(f.key)}"><label><span><strong>${S.e(f.labels?.customer || f.key)}</strong>${f.required ? ' <span class="pm-required-mark">필수</span>' : ""}</span>${q.control === "FILE" ? "" : input}</label>${q.control === "FILE" ? input : ""}${q.control === "NUMBER" ? `<small class="pm-effective-limit">입력 범위 <strong>${S.e(f.min ?? (!f.allowNegative ? 0 : "제한 없음"))} ~ ${S.e(f.max ?? "제한 없음")}</strong> · 간격 ${S.e(f.step ?? 1)}</small>` : ""}${S.media ? S.media((f.assetIds || []).map(resolve).filter(Boolean), (f.labels?.customer || "입력 항목") + " 안내 이미지") : ""}</div>`;
    }
    if (q.control !== "NUMBER") return `<div class="pms-form-grid two pm-answer-fields">${(q.fields || []).map(fieldHtml).join("")}</div>`;
    const bundles = new Map();
    for (const f of q.fields || []) { const key = f.bundleKey || f.key; if (!bundles.has(key)) bundles.set(key, []); bundles.get(key).push(f); }
    return `<div class="pm-number-answer-bundles">${[...bundles.values()].map((fs) => `<section class="pm-number-answer-bundle ${fs.length > 1 ? "grouped" : ""}">${fs.length > 1 ? '<div class="pm-answer-bundle-title">함께 입력하는 항목</div>' : ""}<div class="pms-form-grid two pm-answer-fields">${fs.map(fieldHtml).join("")}</div></section>`).join("")}</div>`;
  };
  S.readAnswer = (root, q, previous = {}) => {
    if (S.isChoice(q.control)) return { choices: S.$$("[data-answer-choice]:checked", root).map((x) => x.dataset.answerChoice), fields: {} };
    const fields = {}, allowed = new Set((q.fields || []).map((f) => f.key));
    S.$$("[data-answer-field]", root).forEach((input) => {
      const key = input.dataset.answerField;
      if (!allowed.has(key)) return;
      if (input.type === "file") {
        const upload = input.closest("[data-answer-upload]");
        const list = upload?._pmsFileState?.files;
        if (list) fields[key] = list.map((a) => a.id);
        else if (Array.isArray(previous.fields?.[key])) fields[key] = previous.fields[key].slice();
      } else if (input.value !== "") fields[key] = input.type === "number" ? Number(input.value) : input.value;
    });
    return { choices: [], fields };
  };
  S.answerFileEvents = (root, q, answer = {}, assets = new Map(), options = {}) => {
    if (q.control !== "FILE") return;
    S.$$('[data-answer-upload]', root).forEach((upload) => {
      const field = (q.fields || []).find((f) => f.key === upload.dataset.answerUpload);
      if (!field) return;
      const ids = Array.isArray(answer.fields?.[field.key]) ? answer.fields[field.key] : [];
      const files = ids.map((id) => assets.get(id) || { id, name: "등록된 파일", image: false });
      S.fileEvents(upload, files, () => {
        files.forEach((a) => assets.set(a.id, a));
        answer.fields ??= {}; answer.fields[field.key] = files.map((a) => a.id);
        options.onChange?.(answer, field);
        upload.dispatchEvent(new Event("change", { bubbles: true }));
      }, options.uploadPath || S.base + "/assets/stage", { ...options, answerField: field.key,
        accept: (field.extensions || []).map((ext) => "." + ext).join(","), maxFiles: field.maxFiles ?? 5, maxFileMB: field.maxFileMB ?? 10 });
    });
  };
  window.addEventListener("beforeunload", (e) => {
    if (S.dirty) {
      e.preventDefault();
      e.returnValue = "";
    }
  });
})();

(function (S) {
  // Validation paths use the same dot notation as data-path.
  S.clearErrors = (root) => {
    S.$$("[data-pms-error]", root).forEach((el) => el.remove());
    S.$$("[data-pms-invalid]", root).forEach((el) => {
      el.removeAttribute("aria-invalid");
      const ids = (el.getAttribute("aria-describedby") || "")
        .split(" ")
        .filter((x) => !x.startsWith("pms-error-"));
      if (ids.length) el.setAttribute("aria-describedby", ids.join(" "));
      else el.removeAttribute("aria-describedby");
      delete el.dataset.pmsInvalid;
    });
  };
  let errorSequence = 0;
  S.errorAdvice = (message) => {
    const m = String(message || "");
    if (m.includes("수정 예시:")) return "";
    if (/의존성|선행.*(생략|건너)|앞 질문.*(생략|건너)/.test(m))
      return "수정 예시: 문 수량의 입력묶음을 ‘문의 종류’가 정한다면 그 질문을 건너뛸 수 없습니다. 앞 질문의 생략 연결을 해제하거나 영향을 받는 문 수량도 같은 경로에서 생략하세요. 사이즈로 입력 범위를 정하는 경우에도 사이즈 답변이 먼저 필요합니다.";
    if (/합계.*(충돌|범위|제한)|입력제한.*(충돌|모순)/.test(m))
      return "수정 예시: 혼합 수량의 합계 최대값이 4이면 여닫이 2 + 서랍 2는 가능하고 3 + 2는 불가능합니다. 개별 최대·최소와 합계 제한이 함께 충족되는지, 앞 답변의 모든 조건에 허용값을 등록했는지 확인해 주세요.";
    if (/범위 중복/.test(m))
      return "수정 예시: ‘W 300 이하’와 ‘W 500 이하’는 겹칩니다. 두 번째를 ‘W 300 초과 AND W 500 이하’로 나누세요. W 조건과 H 조건도 동시에 성립할 수 있으므로 함께 확인해 주세요.";
    if (/충돌|동시에 (적용|일치|성립)/.test(m))
      return "수정 예시: 같은 대상에 ‘1·2·3 선택 → A·B 표시’와 ‘3·4 선택 → B·C 표시’를 함께 연결할 수 없습니다. 3을 한 조건에만 넣거나 하나의 연결로 합치세요. 체크박스는 다른 답변도 동시에 선택할 수 있으므로, 같은 대상의 동작은 한 연결에서 정의하세요.";
    if (/만족하는 값이 입력 범위에 없|입력값이 없/.test(m))
      return "수정 예시: 입력 최대값이 500이면 ‘500 초과’ 조건은 실행되지 않습니다. 입력 제한 또는 조건값을 변경하고, ‘500 초과 AND 300 이하’처럼 서로 모순되는 조건도 제거해 주세요.";
    if (/검증.*한도|경우의 수.*한도/.test(m))
      return "수정 예시: 겹치거나 반복된 조건을 정리하고 질문·범위를 단순화한 뒤 다시 검증하세요. 전체 검증이 끝나지 않은 상태에서는 등록완료할 수 없습니다.";
    if (/원본.*앞|대상.*뒤|순서|후속/.test(m) && /규칙|조건|연결|질문/.test(m))
      return "수정 예시: ‘사이즈 → 문 수량’ 연결이 있으면 사이즈 질문을 먼저 두세요. 순서를 뒤집으려면 해당 연관관계를 먼저 수정·삭제해야 합니다.";
    if (/보기.*없|필드.*없/.test(m))
      return "수정 예시: 삭제한 답변이나 필드를 참조하는 연관관계가 있는지 확인하세요. 관계를 새 답변으로 바꾸거나 삭제한 뒤 다시 저장하세요.";
    if (/고정 사양/.test(m))
      return "수정 예시: 질문에서 제외한 그룹은 ‘고정 사양 입력’ 단계에서 실제 저장할 값 하나를 지정해 주세요.";
    if (/실 제품.*(수정|변경|삭제)|소속.*제품/.test(m))
      return "수정 예시: 현재 사양과 재고를 보존하려면 제품을 복사한 뒤 복사본의 구성을 수정하세요.";
    return "";
  };
  S.showErrors = (root, errors, focus = true) => {
    if (!root) return;
    S.clearErrors(root);
    let first = null;
    for (const [path, message] of Object.entries(errors || {})) {
      const input = S.$$("[data-path]", root).find(
        (el) => el.dataset.path.replace(/^\./, "") === path.replace(/^\./, ""),
      );
      const target =
        input ||
        S.$$("[data-validation-path]", root).find(
          (el) => el.dataset.validationPath === path,
        ) ||
        (path === "fields" ? S.$("#pms-fields", root) : null) ||
        root;
      for (
        let el = target;
        el && el !== root.parentElement;
        el = el.parentElement
      )
        if (el.tagName === "DETAILS") el.open = true;
      const note = document.createElement("div");
      note.className = "pms-field-error";
      note.dataset.pmsError = path;
      note.id = "pms-error-" + ++errorSequence;
      note.textContent = message;
      const advice = S.errorAdvice(message);
      if (advice) note.append(document.createTextNode("\n" + advice));
      note.setAttribute("role", "alert");
      if (input) {
        input.setAttribute("aria-invalid", "true");
        input.dataset.pmsInvalid = "1";
        input.setAttribute(
          "aria-describedby",
          (
            (input.getAttribute("aria-describedby") || "") +
            " " +
            note.id
          ).trim(),
        );
        (input.closest("label") || input.parentElement).append(note);
      } else {
        note.classList.add("pms-section-error");
        target.prepend(note);
        note.tabIndex = -1;
      }
      first ||= input || note;
    }
    if (first && focus) {
      first.scrollIntoView({ block: "center", behavior: "auto" });
      first.focus({ preventScroll: true });
    }
  };
  S.requestAt = async (root, path, method, body, prefix = "") => {
    try {
      return await S.request(path, method, body);
    } catch (error) {
      const fields = error.fieldErrors || { "": error.message };
      S.showErrors(
        root,
        Object.fromEntries(
          Object.entries(fields).map(([key, value]) => [
            key ? prefix + key : "",
            value,
          ]),
        ),
      );
      error.inlineShown = true;
      throw error;
    }
  };
  S.checkCustomerLabel = (labels, prefix, max, errors) => {
    const value = labels?.customer ?? "";
    if (!value.trim()) errors[prefix + "labels.customer"] = "고객용 이름을 입력해 주세요.";
    else if (value.length > max)
      errors[prefix + "labels.customer"] = "고객용 이름은 " + max + "자 이하로 입력해 주세요.";
  };
  S.checkLabels = (labels, prefix, max, errors) => {
    for (const [key, name] of Object.entries({
      customer: "고객용",
      production: "생산팀용",
      management: "관리팀용",
    })) {
      const value = labels?.[key] ?? "";
      if (!value.trim())
        errors[prefix + "labels." + key] = name + " 표시명을 입력해 주세요.";
      else if (value.length > max)
        errors[prefix + "labels." + key] =
          name + " 표시명은 " + max + "자 이하로 입력해 주세요.";
    }
  };
  S.checkUniqueLabels = (items, prefix, errors) => {
    for (const key of ["customer", "production", "management"]) {
      const seen = new Map();
      items.forEach((item, i) => {
        const name = (item.labels?.[key] || "")
          .normalize("NFC")
          .trim()
          .toLowerCase();
        if (!name) return;
        if (seen.has(name)) {
          const other = seen.get(name);
          errors[prefix + i + ".labels." + key] =
            `${other + 1}번째 항목과 표시명이 같습니다.`;
          errors[prefix + other + ".labels." + key] =
            `${i + 1}번째 항목과 표시명이 같습니다.`;
        } else seen.set(name, i);
      });
    }
  };
  S.checkFields = (fields, control, errors, prefix = "fields.") => {
    if (S.isChoice(control)) return;
    if (!fields.length) {
      errors.fields = "입력 필드를 한 개 이상 추가해 주세요.";
      return;
    }
    if (control === "NUMBER" && fields.some((f) => f.bundleKey)) {
      const bundles = new Map();
      fields.forEach((field, index) => {
        const key = field.bundleKey || "__independent__";
        if (!bundles.has(key)) bundles.set(key, []);
        bundles.get(key).push({ field, index });
      });
      for (const rows of bundles.values()) {
        const local = {};
        S.checkUniqueLabels(rows.map((row) => row.field), "", local);
        for (const [path, message] of Object.entries(local)) {
          const [index, ...rest] = path.split(".");
          errors[prefix + rows[Number(index)].index + "." + rest.join(".")] = message;
        }
      }
    } else S.checkUniqueLabels(fields, prefix, errors);
    const fieldKeys = new Set();
    fields.forEach((f, i) => {
      if (fieldKeys.has(f.key)) errors[prefix + i + ".key"] = "입력 필드의 내부 value가 중복됩니다. 새 입력 필드로 다시 생성해 주세요.";
      fieldKeys.add(f.key);
      const p = prefix + i + ".";
      S.checkLabels(f.labels, p, 120, errors);
      if ((f.namePart || "").length > 160)
        errors[p + "namePart"] = "제품명 구성 문자는 160자 이하입니다.";
      if ((f.unit || "").length > 20)
        errors[p + "unit"] = "단위는 20자 이하입니다.";
      const validNumber = (
        key,
        min,
        max,
        integer = false,
        optional = false,
      ) => {
        const n = f[key];
        if (optional && (n == null || n === "")) return;
        if (
          n == null ||
          n === "" ||
          !Number.isFinite(Number(n)) ||
          (integer && !Number.isInteger(Number(n))) ||
          n < min ||
          n > max
        )
          errors[p + key] =
            `${min}~${max} 범위의 ${integer ? "정수" : "숫자"}를 입력해 주세요.`;
      };
      if (control === "NUMBER") {
        validNumber("min", f.allowNegative ? -1e9 : 0, 1e9, false, true);
        validNumber("max", f.allowNegative ? -1e9 : 0, 1e9, false, true);
        validNumber("step", 0.001, 1e9, false, true);
        for (const key of ["min", "max", "step"])
          if (
            f[key] != null &&
            Math.abs(
              Number(f[key]) * 1000 - Math.round(Number(f[key]) * 1000),
            ) > 1e-5
          )
            errors[p + key] = "소수 셋째 자리까지만 입력할 수 있습니다.";
        if (f.min != null && f.max != null && f.min > f.max) {
          errors[p + "min"] = "최소값은 최대값 이하여야 합니다.";
          errors[p + "max"] = "최대값은 최소값 이상이어야 합니다.";
        }
      } else if (control === "FILE") {
        validNumber("minFiles", 0, 20, true);
        validNumber("maxFiles", 1, 20, true);
        validNumber("maxFileMB", 1, 20, true);
        if (f.minFiles > f.maxFiles)
          errors[p + "minFiles"] = "최소 개수는 최대 개수 이하여야 합니다.";
        if (!f.extensions?.length)
          errors[p + "extensions"] =
            "허용할 파일 확장자를 한 개 이상 선택해 주세요.";
      } else {
        validNumber("minLength", 0, 10000, true);
        validNumber("maxLength", 1, 10000, true);
        if (f.minLength > f.maxLength)
          errors[p + "minLength"] =
            "최소 글자 수는 최대 글자 수 이하여야 합니다.";
      }
    });
  };
})(window.PMS);
(function (S) {
  // A body-level popup remains visible above horizontally scrollable tables.
  let trigger = null,
    popup = null,
    pinned = false,
    timer = null;
  const cancelClose = () => clearTimeout(timer);
  const close = () => {
    cancelClose();
    trigger?.setAttribute("aria-expanded", "false");
    trigger?.removeAttribute("aria-controls");
    popup?.remove();
    trigger = popup = null;
    pinned = false;
  };
  S.closeSpecPeek = close;
  const place = () => {
    if (!trigger?.isConnected || !popup) return close();
    const r = trigger.getBoundingClientRect();
    const box = popup.getBoundingClientRect();
    popup.style.left =
      Math.max(12, Math.min(r.left, innerWidth - box.width - 12)) + "px";
    popup.style.top =
      Math.max(
        12,
        r.bottom + box.height + 8 <= innerHeight - 12
          ? r.bottom + 8
          : r.top - box.height - 8,
      ) + "px";
  };
  const open = (button, pin = false) => {
    cancelClose();
    if (trigger === button) {
      pinned ||= pin;
      return;
    }
    close();
    let rows;
    try {
      rows = JSON.parse(button.dataset.specPeek);
    } catch {
      return;
    }
    trigger = button;
    pinned = pin;
    popup = document.createElement("section");
    popup.className = "pms pm-spec-popover";
    popup.id = "pm-spec-popover";
    popup.setAttribute("role", "region");
    popup.setAttribute("aria-label", "전체 구성 항목");
    popup.innerHTML = `<strong>전체 항목 · ${rows.length}개</strong><dl>${rows.map((r) => `<div><dt>${S.e(r.label)}</dt><dd>${S.e(r.value)}</dd></div>`).join("")}</dl>`;
    document.body.append(popup);
    trigger.setAttribute("aria-expanded", "true");
    trigger.setAttribute("aria-controls", popup.id);
    popup.onpointerenter = cancelClose;
    popup.onpointerleave = scheduleClose;
    place();
  };
  function scheduleClose() {
    cancelClose();
    if (!pinned) timer = setTimeout(close, 180);
  }
  document.addEventListener("pointerover", (e) => {
    if (e.pointerType === "touch") return;
    const b = e.target.closest("[data-spec-peek]");
    if (b) open(b);
  });
  document.addEventListener("pointerout", (e) => {
    if (trigger?.contains(e.target) && !trigger.contains(e.relatedTarget))
      scheduleClose();
  });
  document.addEventListener("focusin", (e) => {
    const b = e.target.closest("[data-spec-peek]");
    if (b) open(b);
  });
  document.addEventListener("focusout", (e) => {
    if (e.target === trigger && !popup?.contains(e.relatedTarget))
      scheduleClose();
  });
  document.addEventListener("click", (e) => {
    const b = e.target.closest("[data-spec-peek]");
    if (b) {
      if (trigger === b && pinned) close();
      else open(b, true);
    } else if (!popup?.contains(e.target)) close();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && popup) close();
  });
  document.addEventListener(
    "scroll",
    (e) => {
      if (popup && !popup.contains(e.target)) close();
    },
    true,
  );
  window.addEventListener("resize", close);
})(window.PMS);
