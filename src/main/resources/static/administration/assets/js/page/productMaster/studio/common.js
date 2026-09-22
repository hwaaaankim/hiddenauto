(function () {
  "use strict";
  const S = (window.PMS = {
    base: "/admin/api/product-master/studio",
    groups: [],
    dirty: false,
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
    if (button) button.disabled = true;
    try {
      return await fn();
    } catch (err) {
      if (!err.inlineShown) {
        const scope =
          button?.closest(".pms-dialog-body,.pms-panel") ||
          S.$$("dialog[open]").at(-1);
        if (scope) S.showErrors(scope, err.fieldErrors || { "": err.message });
        else S.notice(err.message);
      }
      S.toast(err.message);
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
  S.check = (label, path, value, css = "") =>
    `<label class="${css || "pms-check"}"><input type="checkbox" data-path="${S.e(path)}" ${value ? "checked" : ""}><span>${S.e(label)}</span></label>`;
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
    return `<div class="pms-form-grid ${withName ? "four" : ""}">${S.field("고객용", prefix + ".labels.customer", labels?.customer, "text", 'maxlength="' + labelMax + '" data-mirror="' + S.e(prefix) + '"')}${S.field("생산팀용", prefix + ".labels.production", labels?.production, "text", 'maxlength="' + labelMax + '"')}${S.field("관리팀용", prefix + ".labels.management", labels?.management, "text", 'maxlength="' + labelMax + '"')}${withName ? S.field("제품명 구성 문자 (빈칸 가능)", prefix + ".namePart", name, "text", 'maxlength="160"') : ""}</div>`;
  };
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
      S.dirty = true;
      changed(path, value, el);
    }
  };
  S.newField = () => ({
    guide: "",
    key: S.key("F"),
    labels: { customer: "", production: "", management: "" },
    namePart: "",
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
          `<details class="pms-list-row pms-editor" ${S.editorAttrs(f.key, i === 0)} data-field-index="${i}"><summary class="pms-row-head"><span class="pms-handle" title="드래그하여 필드 순서를 변경합니다">⠿</span><strong data-editor-title>입력 ${i + 1} · ${S.e(f.labels?.management || "새 필드")}</strong><small class="mono">${S.e(f.key)}</small><button type="button" class="pms-remove" data-remove-field="${i}">×</button></summary><div class="pms-editor-body">${S.labels(f, prefix + "." + i, true, f.namePart)}${S.field("입력 시 안내메시지", prefix + "." + i + ".guide", f.guide || "", "text", 'maxlength="2000"')}<div class="pms-form-grid four pms-section">${S.check("필수 입력", prefix + "." + i + ".required", f.required)}${control === "NUMBER" ? S.check("음수 허용", prefix + "." + i + ".allowNegative", f.allowNegative) + S.field("단위", prefix + "." + i + ".unit", f.unit, "text", 'maxlength="20"') + S.field("입력 간격", prefix + "." + i + ".step", f.step, "number", 'min="0.001" step="0.001"') + S.field("최소값", prefix + "." + i + ".min", f.min, "number", 'step="0.001"') + S.field("최대값", prefix + "." + i + ".max", f.max, "number", 'step="0.001"') : control === "FILE" ? S.field("최소 개수", prefix + "." + i + ".minFiles", f.minFiles, "number", 'min="0" max="20"') + S.field("최대 개수", prefix + "." + i + ".maxFiles", f.maxFiles, "number", 'min="1" max="20"') + S.field("파일당 최대 MB", prefix + "." + i + ".maxFileMB", f.maxFileMB, "number", 'min="1" max="20"') : `${S.field("최소 글자 수", prefix + "." + i + ".minLength", f.minLength, "number", 'min="0" max="10000"')}${S.field("최대 글자 수", prefix + "." + i + ".maxLength", f.maxLength, "number", 'min="1" max="10000"')}${S.select("형식 검증", prefix + "." + i + ".format", f.format, { ANY: "제한 없음", EMAIL: "이메일", PHONE: "전화번호", ALPHANUMERIC: "영문·숫자" })}`}</div>${control === "FILE" ? `<div class="pms-check-grid" data-validation-path="${prefix}.${i}.extensions">${["jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "csv", "xlsx", "xls", "docx", "doc", "pptx", "ppt", "zip", "hwp", "hwpx"].map((ext) => `<label class="pms-check"><input type="checkbox" data-field-ext="${i}" value="${ext}" ${f.extensions.includes(ext) ? "checked" : ""}>${ext}</label>`).join("")}</div>` : ""}</div></details>`,
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
  S.dialog = (title, body, { wide = true, foot = "", onClose } = {}) => {
    const el = document.createElement("dialog");
    el.className = "pms pms-dialog" + (wide ? "" : " narrow");
    el.innerHTML = `<header class="pms-dialog-head"><h2>${S.e(title)}</h2><button type="button" data-close aria-label="닫기">×</button></header><div class="pms-dialog-body">${body}</div>${foot ? `<footer class="pms-dialog-foot">${foot}</footer>` : ""}`;
    document.body.append(el);
    S.$("[data-close]", el).onclick = () => el.close();
    el.addEventListener("close", () => {
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
  S.files = (files, editable = true) =>
    `<div class="pms-files">${(files || []).map((f, i) => `<div class="pms-file">${f.image ? `<img src="${S.e(f.url)}" alt="${S.e(f.name)}" loading="lazy">` : "▤"}<a href="${S.e(f.url)}${f.url.includes("?") ? "&" : "?"}download=true" target="_blank" rel="noopener">${S.e(f.name)}</a>${editable ? `<button type="button" data-remove-file="${i}" class="pms-remove">×</button>` : ""}</div>`).join("")}</div>${editable ? '<div class="pms-drop" tabindex="0" role="button">파일 선택 또는 이곳에 드래그 · 여러 개 가능<input type="file" multiple hidden accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.txt,.csv,.xlsx,.xls,.docx,.doc,.pptx,.ppt,.zip,.hwp,.hwpx"></div>' : ""}`;
  S.fileEvents = (
    root,
    files,
    updated = () => {},
    uploadPath = S.base + "/assets/stage",
  ) => {
    const drop = S.$(".pms-drop", root),
      input = S.$("input[type=file]", root);
    if (!drop) return;
    async function add(selected) {
      if (!selected.length) return;
      const body = new FormData();
      for (const file of selected) body.append("files", file);
      drop.textContent = "파일을 업로드하고 있습니다…";
      let uploadError = null;
      try {
        files.push(...(await S.api(uploadPath, "POST", body)));
        S.dirty = true;
        updated();
      } catch (e) {
        uploadError = e.message;
      } finally {
        render();
        if (uploadError) S.showErrors(root, { "": uploadError });
      }
    }
    function render() {
      root.innerHTML = S.files(files);
      S.fileEvents(root, files, updated, uploadPath);
    }
    drop.onclick = () => input.click();
    input.onclick = (e) => e.stopPropagation();
    drop.onkeydown = (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        input.click();
      }
    };
    input.onchange = () => add([...input.files]);
    drop.ondragover = (e) => {
      e.preventDefault();
      drop.classList.add("over");
    };
    drop.ondragleave = () => drop.classList.remove("over");
    drop.ondrop = (e) => {
      e.preventDefault();
      e.stopPropagation();
      add([...e.dataTransfer.files]);
    };
    S.$$("[data-remove-file]", root).forEach(
      (button) =>
        (button.onclick = () => {
          files.splice(Number(button.dataset.removeFile), 1);
          S.dirty = true;
          updated();
          render();
        }),
    );
  };
  S.pager = (page, total, action = "page") => {
    let start = Math.floor(page / 5) * 5;
    return `<div class="pms-pager"><button data-${action}="0" ${page <= 0 ? "disabled" : ""}>FIRST</button><button data-${action}="${Math.max(0, page - 1)}" ${page <= 0 ? "disabled" : ""}>PREV</button>${Array.from({ length: Math.max(0, Math.min(5, total - start)) }, (_, i) => `<button data-${action}="${start + i}" class="${page === start + i ? "current" : ""}">${start + i + 1}</button>`).join("")}<button data-${action}="${page + 1}" ${page >= total - 1 ? "disabled" : ""}>NEXT</button><button data-${action}="${Math.max(0, total - 1)}" ${page >= total - 1 ? "disabled" : ""}>LAST</button></div>`;
  };
  S.inputAnswer = (q, answer = {}, disabled = false) => {
    const choices = answer.choices || [],
      fields = answer.fields || {};
    if (S.isChoice(q.control))
      return `<div class="pms-check-grid">${(q.choices || []).map((c) => `<label class="pms-check"><input type="${q.control === "RADIO" ? "radio" : "checkbox"}" name="q-${S.e(q.key)}" data-answer-choice="${S.e(c.key)}" ${choices.includes(c.key) ? "checked" : ""} ${disabled ? "disabled" : ""}>${S.e(c.labels.customer)}</label>`).join("")}</div>`;
    return `<div class="pms-form-grid two">${(q.fields || [])
      .map((f) => {
        const attr = `data-answer-field="${S.e(f.key)}" ${disabled ? "disabled" : ""}`;
        let input;
        if (q.control === "TEXTAREA")
          input = `<textarea ${attr} rows="3" maxlength="${f.maxLength || 500}">${S.e(fields[f.key])}</textarea>`;
        else if (q.control === "FILE")
          input = `<input type="file" ${attr} multiple accept="${(f.extensions || []).map((x) => "." + x).join(",")}"><small data-answer-files="${S.e(f.key)}">${Array.isArray(fields[f.key]) ? fields[f.key].length + "개 파일 업로드됨" : ""}</small>`;
        else
          input = `<input ${attr} type="${q.control === "NUMBER" ? "number" : "text"}" value="${S.e(fields[f.key])}" ${q.control === "NUMBER" ? `step="${f.step || 1}" ${f.min !== null ? `min="${f.min}"` : !f.allowNegative ? 'min="0"' : ""} ${f.max !== null ? `max="${f.max}"` : ""}` : `maxlength="${f.maxLength || 500}"`}>`;
        return `<label><span>${S.e(f.labels.customer)} ${f.required ? "*" : ""} ${S.e(f.unit || "")}</span>${input}${q.control === "NUMBER" ? `<small>${f.min ?? (!f.allowNegative ? 0 : "제한 없음")} ~ ${f.max ?? "제한 없음"} · 간격 ${f.step || 1}</small>` : ""}</label>`;
      })
      .join("")}</div>`;
  };
  S.readAnswer = (root, q, previous = {}) => {
    if (S.isChoice(q.control))
      return {
        choices: S.$$("[data-answer-choice]:checked", root).map(
          (x) => x.dataset.answerChoice,
        ),
        fields: {},
      };
    const fields = {};
    S.$$("[data-answer-field]", root).forEach((input) => {
      if (input.type === "file") {
        if (previous.fields?.[input.dataset.answerField])
          fields[input.dataset.answerField] =
            previous.fields[input.dataset.answerField];
      } else if (input.value !== "")
        fields[input.dataset.answerField] =
          input.type === "number" ? Number(input.value) : input.value;
    });
    return { choices: [], fields };
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
    S.checkUniqueLabels(fields, prefix, errors);
    fields.forEach((f, i) => {
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
