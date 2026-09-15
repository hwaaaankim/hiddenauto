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
  S.key = (prefix) => prefix + "_" + crypto.randomUUID().replaceAll("-", "");
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
  S.roles = {
    GENERAL: "일반 그룹",
    CATEGORY: "대분류 · 기본",
    SUBCATEGORY: "중분류 · 기본",
    SERIES: "시리즈 · 기본",
    COLOR: "색상",
    SIZE: "사이즈",
    DOOR_TYPE: "문의 형태",
    HANDLE: "손잡이",
    BASIN: "세면대",
    OPTION: "기타 옵션",
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
    if (!response.ok || data.success === false)
      throw new Error(data.message || "요청 처리에 실패했습니다.");
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
    document.body.append(el);
    setTimeout(() => el.remove(), 3500);
  };
  S.run = async (button, fn) => {
    if (button?.disabled) return;
    if (button) button.disabled = true;
    try {
      return await fn();
    } catch (err) {
      S.notice(err.message);
      S.toast(err.message);
      console.error(err);
    } finally {
      if (button?.isConnected) button.disabled = false;
    }
  };
  S.catalog = async () => {
    S.groups = await S.request("/groups");
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
  S.labels = (labels, prefix, withName = false, name = "") =>
    `<div class="pms-form-grid ${withName ? "four" : ""}">${S.field("고객용", prefix + ".labels.customer", labels?.customer, "text", 'maxlength="120" data-mirror="' + S.e(prefix) + '"')}${S.field("생산팀용", prefix + ".labels.production", labels?.production, "text", 'maxlength="120"')}${S.field("관리팀용", prefix + ".labels.management", labels?.management, "text", 'maxlength="120"')}${withName ? S.field("제품명 구성 문자 (빈칸 가능)", prefix + ".namePart", name, "text", 'maxlength="160"') : ""}</div>`;
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
          if (previous === old || previous === undefined || previous === null) {
            S.set(obj, key, value);
            const input = S.$$("[data-path]", root).find(
              (x) => x.dataset.path === key || x.dataset.path === "." + key,
            );
            if (input) input.value = value;
          }
        }
      }
      S.set(obj, path, value);
      S.dirty = true;
      changed(path, value, el);
    }
  };
  S.newField = () => ({
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
          `<article class="pms-list-row" data-field-index="${i}" draggable="true"><div class="pms-row-head"><span class="pms-handle" title="드래그하여 필드 순서를 변경합니다">⠿</span><strong>입력 ${i + 1}</strong><small class="mono">${S.e(f.key)}</small><button type="button" class="pms-remove" data-remove-field="${i}">×</button></div>${S.labels(f, prefix + "." + i, true, f.namePart)}<div class="pms-form-grid four pms-section">${S.check("필수 입력", prefix + "." + i + ".required", f.required)}${control === "NUMBER" ? S.check("음수 허용", prefix + "." + i + ".allowNegative", f.allowNegative) + S.field("단위", prefix + "." + i + ".unit", f.unit, "text", 'maxlength="20"') + S.field("입력 간격", prefix + "." + i + ".step", f.step, "number", 'min="0.001" step="0.001"') + S.field("최소값", prefix + "." + i + ".min", f.min, "number", 'step="0.001"') + S.field("최대값", prefix + "." + i + ".max", f.max, "number", 'step="0.001"') : control === "FILE" ? S.field("최소 개수", prefix + "." + i + ".minFiles", f.minFiles, "number", 'min="0" max="20"') + S.field("최대 개수", prefix + "." + i + ".maxFiles", f.maxFiles, "number", 'min="1" max="20"') + S.field("파일당 최대 MB", prefix + "." + i + ".maxFileMB", f.maxFileMB, "number", 'min="1" max="20"') : `${S.field("최소 글자 수", prefix + "." + i + ".minLength", f.minLength, "number", 'min="0" max="10000"')}${S.field("최대 글자 수", prefix + "." + i + ".maxLength", f.maxLength, "number", 'min="1" max="10000"')}${S.select("형식 검증", prefix + "." + i + ".format", f.format, { ANY: "제한 없음", EMAIL: "이메일", PHONE: "전화번호", ALPHANUMERIC: "영문·숫자" })}`}</div>${control === "FILE" ? `<div class="pms-check-grid">${["jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "csv", "xlsx", "xls", "docx", "doc", "pptx", "ppt", "zip", "hwp", "hwpx"].map((ext) => `<label class="pms-check"><input type="checkbox" data-field-ext="${i}" value="${ext}" ${f.extensions.includes(ext) ? "checked" : ""}>${ext}</label>`).join("")}</div>` : ""}</article>`,
      )
      .join("");
  S.fieldEvents = (root, fields, rerender, prefix = "fields") => {
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
    root._pmsSortable?.abort();
    root._pmsSortable = new AbortController();
    const eventOptions = { signal: root._pmsSortable.signal };
    let dragging = null;
    root.addEventListener(
      "dragstart",
      (e) => {
        if (e.target.closest("input,textarea,select")) {
          e.preventDefault();
          return;
        }
        const row = e.target.closest(selector);
        if (!row) return;
        dragging = row;
        e.dataTransfer.setData("text/plain", "reorder");
        e.dataTransfer.effectAllowed = "move";
      },
      eventOptions,
    );
    root.addEventListener(
      "dragover",
      (e) => {
        if (dragging && e.target.closest(selector)) {
          e.preventDefault();
          e.dataTransfer.dropEffect = "move";
        }
      },
      eventOptions,
    );
    root.addEventListener(
      "drop",
      (e) => {
        const to = e.target.closest(selector);
        if (dragging && to && to !== dragging) {
          e.preventDefault();
          e.stopPropagation();
          const rows = S.$$(selector, root);
          onMove(rows.indexOf(dragging), rows.indexOf(to));
          S.dirty = true;
        }
        dragging = null;
      },
      eventOptions,
    );
    root.addEventListener("dragend", () => (dragging = null), eventOptions);
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
    `<div class="pms-files">${(files || []).map((f, i) => `<div class="pms-file">${f.image ? `<img src="${S.e(f.url)}" alt="${S.e(f.name)}" loading="lazy">` : "▤"}<a href="${S.e(f.url)}${f.url.includes("?") ? "&" : "?"}download=true" target="_blank" rel="noopener">${S.e(f.name)}</a>${editable && !f.id.startsWith("legacy-") ? `<button type="button" data-remove-file="${i}" class="pms-remove">×</button>` : ""}</div>`).join("")}</div>${editable ? '<div class="pms-drop" tabindex="0" role="button">파일 선택 또는 이곳에 드래그 · 여러 개 가능<input type="file" multiple hidden accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.txt,.csv,.xlsx,.xls,.docx,.doc,.pptx,.ppt,.zip,.hwp,.hwpx"></div>' : ""}`;
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
      try {
        files.push(...(await S.api(uploadPath, "POST", body)));
        S.dirty = true;
        updated();
      } catch (e) {
        S.toast(e.message);
      } finally {
        render();
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
  S.guide = (mode) => {
    const steps =
      mode === "process"
        ? [
            [
              "질문 순서부터 정합니다",
              "왼쪽 질문을 끌어 위아래로 배치합니다. 조건은 앞 질문에서 뒤 질문으로만 연결됩니다.",
            ],
            [
              "각 질문의 입력·보기를 등록합니다",
              "숫자형은 W/H/D 같은 필드를 +로 추가하고 최소·최대·입력 간격을 지정합니다. 선택형은 보기를 직접 추가합니다.",
            ],
            [
              "커스텀 구간을 연결합니다",
              "캔버스의 + 커스텀을 누르고 W 범위, 선택한 보기 등의 조건을 만듭니다. 구간 카드를 대상 질문으로 끌어 연결하면 표시·건너뜀·허용 보기를 설정할 수 있습니다.",
            ],
            [
              "기본 동작과 경계를 확인합니다",
              "조건이 없을 때 표시/숨김을 먼저 정합니다. 모든 범위를 규칙으로 처리하려면 ‘일치 규칙 필수’를 켜고, 경계의 포함 여부를 지정합니다.",
            ],
            [
              "검증 후 등록완료합니다",
              "실제 입력으로 미리보기하고 검증을 누릅니다. 임시저장은 등록중 상태이며, 검증 통과 후 등록완료해야 고객에게 표시됩니다.",
            ],
          ]
        : [
            [
              "그룹·옵션부터 등록합니다",
              "대분류·중분류·시리즈는 기본그룹입니다. 보기는 직접 등록하며 고객명을 입력하면 다른 표시명도 함께 채워집니다.",
            ],
            [
              "규격과 비규격을 나눕니다",
              "비규격 그룹은 입력 방식만 정합니다. 제품의 세 기본그룹을 제외한 구성은 모두 규격이거나 모두 비규격이어야 합니다.",
            ],
            [
              "조합과 제품명을 만듭니다",
              "제품 생성에서 그룹을 끌어 놓고 사용할 보기를 체크합니다. 제품명 구성에서 순서와 구분문자, 괄호 등을 정합니다.",
            ],
            [
              "미리보기 후 등록합니다",
              "중복 행은 ×로 제거하고 제품명·최초재고·첨부를 확인합니다. 등록 버튼을 누르기 전에는 제품이 저장되지 않습니다.",
            ],
            [
              "제품별로 관리합니다",
              "규격은 등록 즉시 사용중 또는 재고없음이 됩니다. 비규격은 프로세스를 설정하고 검증을 통과해야 등록완료됩니다.",
            ],
          ];
    let index = 0;
    const dialog = S.dialog("제품관리 사용 안내", "", {
      wide: false,
      foot: '<button data-back>이전</button><button class="primary" data-next>다음</button>',
    });
    const paint = () => {
      S.$(".pms-dialog-body", dialog).innerHTML =
        `${S.badge(index + 1 + " / " + steps.length, "blue")}<div class="pms-tour-step"><strong>${S.e(steps[index][0])}</strong><p>${S.e(steps[index][1])}</p></div>`;
      S.$("[data-back]", dialog).disabled = index === 0;
      S.$("[data-next]", dialog).textContent =
        index === steps.length - 1 ? "완료" : "다음";
    };
    S.$("[data-back]", dialog).onclick = () => {
      index--;
      paint();
    };
    S.$("[data-next]", dialog).onclick = () => {
      if (index === steps.length - 1) {
        localStorage.setItem("pm-studio-tour-" + mode, "1");
        dialog.close();
      } else {
        index++;
        paint();
      }
    };
    paint();
  };
  window.addEventListener("beforeunload", (e) => {
    if (S.dirty) {
      e.preventDefault();
      e.returnValue = "";
    }
  });
})();
