(async function (S) {
  "use strict";
  const root = document.getElementById("pm-public-chat");
  if (!root) return;
  const content = S.$("#pm-chat-content", root), heading = S.$("#pm-chat-title", root);
  const seedId = Number(root.dataset.adminProductId) || null;
  const seedToken = root.dataset.token || "";
  const admin = root.dataset.adminCatalog === "true" || seedId != null;
  let kind = root.dataset.kind || new URLSearchParams(location.search).get("kind") || "standard";
  let selections = {}, filters = {}, baseHistory = [], page = 0, browseResult = null;
  let activeId = null, token = "", product = null, schema = null, faq = null, evaluation = null;
  let answers = {}, completed = [], currentKey = null, attempted = new Set(), assetMap = new Map();
  let browseSequence = 0, browseController = null, pendingUploads = 0, seedProduct = null, flowSequence = 0, productSequence = 0;
  S.bindMedia(content);

  function error(message) {
    let box = S.$("#pm-chat-error", root);
    if (!box) { box = document.createElement("div"); box.id = "pm-chat-error"; box.setAttribute("role", "alert"); content.before(box); }
    box.innerHTML = message ? `<p class="pms-alert">${S.e(message)}</p>` : "";
  }
  async function guarded(fn) {
    error("");
    try { await fn(); } catch (e) { if (e.name !== "AbortError") error(e.message); }
  }
  function resetAnswers() {
    flowSequence++;
    answers = {}; completed = []; currentKey = null; attempted.clear(); evaluation = null;
  }
  function removeFaq() { document.getElementById("pm-faq-button")?.remove(); faq = null; }
  async function restart() {
    if (pendingUploads) return S.toast("파일 업로드가 끝난 후 다시 시작해 주세요.");
    productSequence++;
    resetAnswers(); selections = {}; filters = {}; baseHistory = []; page = 0;
    activeId = null; token = ""; product = null; schema = null; removeFaq();
    await browse();
  }
  S.$("#pm-chat-restart", root).onclick = () => guarded(restart);

  function historyHtml() {
    return `<div class="pm-base-history">${baseHistory.map((h, i) => `<article class="pms-bubble answer"><div class="pms-chat-head"><strong>${S.e(h.label)}</strong><button type="button" data-base-back="${i}">다시 선택</button></div>
      <p>${S.e(h.answer)}</p>${h.guide?.trim() ? `<p class="pm-answer-guide">${S.e(h.guide)}</p>` : ""}</article>`).join("")}</div>`;
  }
  function bindBaseHistory() {
    S.$$("[data-base-back]", content).forEach((b) => { b.onclick = () => guarded(async () => {
      if (pendingUploads) throw Error("파일 업로드가 끝난 후 분류를 변경해 주세요.");
      const index = Number(b.dataset.baseBack);
      for (const h of baseHistory.slice(index)) delete selections[h.id];
      baseHistory = baseHistory.slice(0, index); filters = {}; page = 0;
      resetAnswers(); activeId = null; token = ""; product = null; schema = null; removeFaq();
      await browse();
    }); });
  }
  function kindPicker() {
    const host = S.$("#pm-kind-picker", root);
    host.hidden = !!seedProduct || root.dataset.kind === "standard";
    host.innerHTML = ["standard", "custom"].map((k) => `<button type="button" data-kind="${k}" class="${kind === k ? "primary" : ""}" aria-pressed="${kind === k}">${k === "custom" ? "비규격" : "규격"} 고객 테스트</button>`).join("");
    S.$$("[data-kind]", host).forEach((b) => { b.onclick = () => guarded(async () => { kind = b.dataset.kind; await restart(); }); });
  }
  async function browse() {
    const seq = ++browseSequence;
    browseController?.abort(); browseController = new AbortController();
    kindPicker(); heading.textContent = `${kind === "custom" ? "비규격" : "규격"} 제품을 찾는 과정부터 시작합니다.`;
    content.setAttribute("aria-busy", "true");
    const focus = document.activeElement?.dataset.filterKey;
    try {
      const result = await S.api(admin ? S.base + "/customer-browse" : "/product-spec/studio/browse", "POST", {
        nonStandard: kind === "custom", selections, filters, page, size: 12,
        // A product test entry identifies the product to review; it never skips discovery.
        productId: null, token: null,
      }, browseController.signal);
      if (seq !== browseSequence) return;
      browseResult = result; page = result.page; renderBrowse(result);
      if (focus) S.$$("[data-filter-key]", content).find((el) => el.dataset.filterKey === focus)?.focus({ preventScroll: true });
    } finally { if (seq === browseSequence) content.setAttribute("aria-busy", "false"); }
  }
  function renderBrowse(result) {
    content.innerHTML = `<div class="pm-customer-steps"><span class="${result.next ? "active" : "done"}">1 분류 선택</span><span class="${!result.next ? "active" : ""}">2 ${kind === "custom" ? "제품 선택" : "옵션으로 제품 찾기"}</span><span>3 ${kind === "custom" ? "옵션 구성·결과 확인" : "제품 상세 확인"}</span></div>${historyHtml()}`;
    if (result.next) {
      const q = result.next;
      content.innerHTML += `<article class="pms-bubble pm-discovery-question"><span class="pm-detail-eyebrow">STEP ${baseHistory.length + 1}</span><h2>${S.e(q.question || q.label + "를 선택해 주세요.")}</h2>
        ${q.guide?.trim() ? `<p class="pm-preserve-lines">${S.e(q.guide)}</p>` : ""}${S.media(q.assets, q.label + " 가이드")}
        <div class="pm-category-options">${q.options.map((o) => `<button type="button" data-base-choice="${S.e(o.key)}"><span>${S.e(o.label)}</span><span aria-hidden="true">→</span></button>`).join("")}</div>
        <div id="pm-option-preview"></div>${!q.options.length ? '<p class="pms-help">선택 가능한 제품이 없습니다. 등록 상태와 기본 분류를 확인해 주세요.</p>' : `<small>현재 선택 가능한 제품 ${result.total.toLocaleString()}개</small>`}</article>`;
      S.$$("[data-base-choice]", content).forEach((b) => {
        const option = q.options.find((o) => o.key === b.dataset.baseChoice);
        const preview = () => { S.$("#pm-option-preview", content).innerHTML = S.media(option.assets, option.label + " 이미지") + (option.guide?.trim() ? `<p class="pm-answer-guide">${S.e(option.guide)}</p>` : ""); };
        b.onmouseenter = preview; b.onfocus = preview;
        b.onclick = () => guarded(async () => {
          if (b.disabled || content.getAttribute("aria-busy") === "true") return;
          b.disabled = true;
          selections[q.groupId] = option.key;
          baseHistory.push({ id: q.groupId, label: q.label, answer: option.label, guide: option.guide });
          filters = {}; page = 0; await browse();
          S.$(".pm-discovery-question,.pm-browse-layout", content)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
        });
      });
    } else {
      const selectedCount = Object.values(filters).reduce((n, xs) => n + xs.length, 0);
      content.innerHTML += `<div class="pm-browse-layout ${kind === "custom" ? "is-custom" : ""}">${kind === "standard" ? `<aside class="pm-option-filters"><header><h2>제품 옵션</h2><button type="button" data-filter-reset ${selectedCount ? "" : "disabled"}>초기화</button></header>
        <p>선택한 옵션을 <strong>모두 포함한</strong> 제품을 찾습니다. 같은 그룹 안에서도 복수 선택은 AND 조건입니다.</p>
        ${result.filters.map((g) => `<fieldset><legend>${S.e(g.label)}</legend>${g.guide?.trim() ? `<p class="pm-filter-guide">${S.e(g.guide)}</p>` : ""}
          ${g.options.map((o) => `<label class="pms-check"><input type="checkbox" data-filter-group="${g.groupId}" data-filter-key="${g.groupId}:${S.e(o.key)}" value="${S.e(o.key)}" ${(filters[g.groupId] || []).includes(o.key) ? "checked" : ""}><span>${S.e(o.label)}</span></label>`).join("")}${g.options.filter((o) => (filters[g.groupId] || []).includes(o.key)).map((o) => `${o.guide?.trim() ? `<p class="pm-answer-guide">${S.e(o.guide)}</p>` : ""}${S.media(o.assets, g.label + " · " + o.label)}`).join("")}</fieldset>`).join("") || '<p class="pms-help">이 분류에는 추가 옵션이 없습니다.</p>'}</aside>` : ""}
        <section class="pm-catalog-results"><header><div><h2>${kind === "custom" ? "구성할 비규격 제품을 선택해 주세요." : "조건에 맞는 제품"}</h2><p><strong>${result.total.toLocaleString()}</strong>개 제품${selectedCount ? " · " + selectedCount + "개 옵션 선택" : ""}</p></div></header>
          ${seedProduct ? `<p class="pm-seed-note">테스트를 연 제품: <strong>${S.e(seedProduct.productName)}</strong> · 해당 분류와 시리즈를 선택하면 아래 목록에서 찾을 수 있습니다.</p>` : ""}
          <div class="pm-catalog-grid">${result.products.map((p, i) => {
            const image = p.assets.find((a) => a.image), seed = seedId ? p.id === seedId : p.token === seedToken;
            return `<button type="button" class="pm-catalog-card ${seed ? "is-seed" : ""}" data-product="${i}">
              <div class="pm-card-image">${image ? `<img src="${S.e(image.url)}" alt="${S.e(p.productName)}" loading="lazy">` : '<span>HIDDENBATH</span>'}${seed ? '<span class="pm-card-seed">테스트를 연 제품</span>' : ""}</div>
              <div class="pm-card-copy"><span class="pm-card-code mono">${S.e(p.catalogCode)}</span><h3>${S.e(p.productName)}</h3><div class="pm-card-tags">${(p.tags || []).map((t) => S.badge(t)).join("")}</div>
                <div class="pm-card-meta"><span>생산 ${p.productionHours == null ? "미등록" : S.e(p.productionHours) + "시간"}</span>${!p.nonStandard && p.unitPrice != null ? `<strong>${Number(p.unitPrice).toLocaleString()}원</strong>` : ""}</div>
                <span class="pm-card-cta">${p.nonStandard ? "옵션 구성 시작" : "제품 상세 보기"} <span aria-hidden="true">→</span></span></div></button>`;
          }).join("")}</div>${!result.total ? '<div class="pms-empty">선택한 옵션을 모두 가진 제품이 없습니다. 옵션을 해제하거나 초기화해 주세요.</div>' : ""}
          ${result.totalPages > 1 ? S.pager(result.page, result.totalPages, "browse-page") : ""}</section></div>`;
      S.$$("[data-filter-group]", content).forEach((input) => { input.onchange = () => guarded(async () => {
        const id = input.dataset.filterGroup;
        const values = S.$$("[data-filter-group]", content).filter((x) => x.dataset.filterGroup === id && x.checked).map((x) => x.value);
        if (values.length) filters[id] = values; else delete filters[id];
        page = 0; await browse();
      }); });
      S.$("[data-filter-reset]", content)?.addEventListener("click", () => guarded(async () => { filters = {}; page = 0; await browse(); }));
      S.$$("[data-product]", content).forEach((b) => { b.onclick = () => guarded(async () => {
        if (b.disabled || content.getAttribute("aria-busy") === "true") return;
        b.disabled = true;
        const card = result.products[Number(b.dataset.product)]; activeId = card.id; token = card.token;
        resetAnswers();
        try { await loadProduct(); } finally { if (b.isConnected) b.disabled = false; }
      }); });
      S.$$("[data-browse-page]", content).forEach((b) => { b.onclick = () => guarded(async () => { page = Number(b.dataset.browsePage); await browse(); }); });
    }
    bindBaseHistory();
  }

  async function loadProduct() {
    const version = ++productSequence, selectedId = activeId, selectedToken = token;
    browseSequence++; browseController?.abort(); content.setAttribute("aria-busy", "true");
    S.$("#pm-kind-picker", root).hidden = true;
    let loadedProduct, loadedSchema, loadedFaq;
    try {
      if (admin) {
        [loadedProduct, loadedSchema] = await Promise.all([S.request(`/products/${selectedId}`), S.request(`/products/${selectedId}/preview-schema`)]);
        loadedFaq = (await S.request("/faq")).find((t) => t.id === loadedProduct.faqTopicId) || null;
      } else {
        [loadedProduct, loadedSchema, loadedFaq] = await Promise.all([
          S.api(`/product-spec/studio/${encodeURIComponent(selectedToken)}/detail`),
          S.api(`/product-spec/studio/${encodeURIComponent(selectedToken)}/schema`),
          S.api(`/product-spec/studio/${encodeURIComponent(selectedToken)}/faq`),
        ]);
      }
    } finally { if (version === productSequence) content.setAttribute("aria-busy", "false"); }
    if (version !== productSequence) return;
    product = loadedProduct; schema = loadedSchema; faq = loadedFaq;
    assetMap = new Map((schema.assets || []).map((a) => [a.id, a]));
    heading.textContent = product.productName;
    if (product.nonStandard) installFaq();
    if (!product.nonStandard) {
      S.mountProductDetail(content, product, { faq, onBack: () => guarded(async () => { removeFaq(); await browse(); }) });
      root.scrollIntoView({ block: "start", behavior: "smooth" });
      return;
    }
    await evaluate();
  }
  async function requestEvaluation() {
    return S.api(admin ? `${S.base}/products/${activeId}/evaluate` : `/product-spec/studio/${encodeURIComponent(token)}/evaluate`, "POST", admin ? { answers } : answers);
  }
  async function evaluate() { const version = flowSequence, result = await requestEvaluation(); if (version !== flowSequence) return; evaluation = result; merge(); paintProcess(); }
  function merge() {
    for (const state of evaluation.questions) {
      const key = state.question.key;
      if (!state.visible && !state.question.fixed) delete answers[key];
      else if (!state.errors.length) answers[key] = state.answer;
    }
    const visible = evaluation.questions.filter((s) => s.visible && !s.question.fixed);
    completed = completed.filter((key) => visible.some((s) => s.question.key === key && !s.errors.length));
    currentKey = visible.find((s) => !completed.includes(s.question.key))?.question.key || null;
  }
  function clearAfter(key, keepCurrent = true) {
    flowSequence++;
    const index = schema.process.questions.findIndex((q) => q.key === key);
    for (const q of schema.process.questions.slice(index + (keepCurrent ? 1 : 0))) {
      if (!q.fixed) { delete answers[q.key]; attempted.delete(q.key); }
    }
    completed = completed.filter((k) => schema.process.questions.findIndex((q) => q.key === k) < index);
  }
  function currentContext() {
    const current = S.copy(answers), state = evaluation?.questions.find((s) => s.question.key === currentKey);
    const box = S.$("#pm-answer-input", content);
    if (box && state) current[currentKey] = S.readAnswer(box, state.question, current[currentKey]);
    return { product: { name: product.productName, code: product.catalogCode }, productionHours: product.productionHours, unitPrice: product.unitPrice, answers: current,
      selections: (evaluation?.questions || []).filter((s) => s.visible || s.question.fixed).map((s) => ({ groupKey: s.question.key, groupName: s.question.labels.customer, answer: current[s.question.key] || s.answer })) };
  }
  function installFaq() {
    document.getElementById("pm-faq-button")?.remove();
    if (!faq) return;
    const b = document.createElement("button"); b.id = "pm-faq-button"; b.className = "pm-faq-float"; b.textContent = "?";
    b.type = "button"; b.setAttribute("aria-label", "제품 FAQ 및 별도 문의"); b.onclick = () => S.showProductFaq(faq, currentContext); root.append(b);
  }
  function paintProcess() {
    const visible = evaluation.questions.filter((s) => s.visible && !s.question.fixed);
    if (!currentKey && evaluation.complete) {
      const finalProduct = { ...product, processAssets: [...(product.processAssets || []), ...assetMap.values()] };
      S.mountProductDetail(content, finalProduct, { faq, summary: evaluation, onBack: () => {
        const key = completed.at(-1);
        if (key) guarded(async () => { clearAfter(key); await evaluate(); });
        else guarded(browse);
      } });
      root.scrollIntoView({ block: "start", behavior: "smooth" });
      return;
    }
    content.innerHTML = `<div class="pm-customer-steps"><span class="done">1 분류 선택</span><span class="done">2 제품 선택</span><span class="active">3 옵션 구성</span></div>${historyHtml()}
      <div class="pm-selected-product"><div><span class="pm-detail-eyebrow">CUSTOM PRODUCT</span><h2>${S.e(product.productName)}</h2><small class="mono">${S.e(product.catalogCode)}</small></div><button type="button" data-change-product>제품 다시 선택</button></div>
      <div class="pm-question-progress"><span>${completed.length} / ${visible.length} 질문 완료</span><progress max="${Math.max(1, visible.length)}" value="${completed.length}"></progress><span>생산기간 ${product.productionHours == null ? "미등록" : S.e(product.productionHours) + "시간"}</span></div>
      <div class="pm-chat-timeline">${completed.map((key) => {
        const state = evaluation.questions.find((s) => s.question.key === key), q = state.question;
        return `<article class="pms-bubble answer"><div class="pms-chat-head"><strong>${S.e(q.labels.customer)}</strong><button type="button" data-answer-back="${S.e(key)}">수정</button></div>
          <p>${S.e(S.answerText(q, state.answer, assetMap))}</p>${S.answerGuides(q, state.answer).map((t) => `<p class="pm-answer-guide">${S.e(t)}</p>`).join("")}</article>`;
      }).join("")}<div id="pm-current-question"></div></div>`;
    bindBaseHistory();
    S.$("[data-change-product]", content).onclick = () => guarded(async () => { if (pendingUploads) throw Error("업로드 완료 후 제품을 변경해 주세요."); resetAnswers(); removeFaq(); await browse(); });
    S.$$("[data-answer-back]", content).forEach((b) => { b.onclick = () => guarded(async () => {
      if (pendingUploads) throw Error("업로드 완료 후 답변을 수정해 주세요."); clearAfter(b.dataset.answerBack); await evaluate();
    }); });
    const target = S.$("#pm-current-question", content);
    if (!currentKey) {
      target.innerHTML = `<p class="pms-alert">완료할 수 없는 제품 구성입니다. 등록된 질문과 고정 사양을 확인해 주세요.</p>${evaluation.questions.flatMap((s) => s.errors.map((e) => `<p class="pms-error">${S.e(s.question.labels.customer + ": " + e)}</p>`)).join("")}`;
      return;
    }
    const state = evaluation.questions.find((s) => s.question.key === currentKey);
    const q = { ...state.question, choices: (state.question.choices || []).filter((c) => state.allowed.includes(c.key)) };
    target.innerHTML = `<article class="pms-bubble pm-current-question"><span class="pm-detail-eyebrow">QUESTION ${completed.length + 1}</span><h2 tabindex="-1">${S.e(q.question || q.labels.customer + "를 입력해 주세요.")}</h2>
      <div class="pms-actions">${S.badge(S.controls[q.control].split(" · ")[0], "blue")}${S.badge(state.required ? "필수 답변" : "선택사항")}</div>
      ${q.guide?.trim() ? `<div class="pm-tutorial"><strong>선택 가이드</strong><p class="pm-preserve-lines">${S.e(q.guide)}</p></div>` : ""}${S.media((q.assetIds || []).map((id) => assetMap.get(id)), q.labels.customer + " 가이드")}
      <div id="pm-answer-input">${S.inputAnswer(q, answers[q.key] || state.answer)}</div><div id="pm-choice-assets"></div><div id="pm-answer-guides" aria-live="polite"></div>
      <div id="pm-answer-errors" role="alert">${attempted.has(q.key) ? state.errors.map((e) => `<p class="pms-error">${S.e(e)}</p>`).join("") : ""}</div>
      <div class="pms-actions"><button type="button" class="primary" id="pm-answer-next">${completed.length + 1 === visible.length ? "구성 완료 · 결과 확인" : "다음 질문"}</button></div></article>`;
    const box = S.$("#pm-answer-input", content);
    function preview() {
      const a = S.readAnswer(box, q, answers[q.key]);
      S.$("#pm-answer-guides", content).innerHTML = S.answerGuides(q, a).map((t) => `<p class="pm-answer-guide">${S.e(t)}</p>`).join("");
      S.$("#pm-choice-assets", content).innerHTML = (a.choices || []).map((key) => {
        const c = q.choices.find((c) => c.key === key);
        return S.media((c?.assetIds || []).map((id) => assetMap.get(id)), q.labels.customer + " · " + c?.labels.customer);
      }).join("");
    }
    preview(); box.addEventListener("input", preview); box.addEventListener("change", preview);
    S.$$("input[type=file]", box).forEach((input) => { input.onchange = () => guarded(async () => {
      const f = q.fields.find((f) => f.key === input.dataset.answerField), selectedFiles = [...input.files];
      if (!selectedFiles.length) return;
      if (selectedFiles.length > (f.maxFiles ?? 1)) throw Error("파일은 최대 " + f.maxFiles + "개입니다.");
      for (const file of selectedFiles) {
        const ext = file.name.split(".").pop().toLowerCase();
        if (!(f.extensions || []).includes(ext) || file.size > (f.maxFileMB ?? 10) * 1024 * 1024) throw Error(f.labels.customer + "의 파일 확장자 또는 용량을 확인해 주세요.");
      }
      const fd = new FormData(); selectedFiles.forEach((file) => fd.append("files", file));
      pendingUploads++; input.disabled = true; S.$("#pm-answer-next", content).disabled = true;
      try {
        const uploaded = await S.api(admin ? S.base + "/assets/stage" : `/product-spec/studio/${encodeURIComponent(token)}/upload`, "POST", fd);
        answers[q.key] ??= { choices: [], fields: {} }; answers[q.key].fields[f.key] = uploaded.map((a) => a.id);
        uploaded.forEach((a) => assetMap.set(a.id, a));
        S.$$("[data-answer-files]", box).find((el) => el.dataset.answerFiles === f.key).textContent = uploaded.map((a) => a.name).join(", "); preview();
      } finally { pendingUploads--; input.disabled = false; S.$("#pm-answer-next", content).disabled = pendingUploads > 0; }
    }); });
    S.$("#pm-answer-next", content).onclick = (e) => guarded(async () => {
      const b = e.currentTarget; if (b.disabled || pendingUploads) return; b.disabled = true;
      try {
        const value = S.readAnswer(box, q, answers[q.key]); clearAfter(q.key); answers[q.key] = value; attempted.add(q.key);
        const version = flowSequence, result = await requestEvaluation();
        if (version !== flowSequence) return;
        evaluation = result;
        const fresh = evaluation.questions.find((s) => s.question.key === q.key);
        if (!fresh.errors.length) completed.push(q.key);
        merge(); paintProcess();
        S.$(".pm-current-question h2", content)?.focus({ preventScroll: true });
        S.$("#pm-current-question", content)?.scrollIntoView({ block: "nearest", behavior: "smooth" });
      } finally { if (b.isConnected) b.disabled = false; }
    });
  }

  await guarded(async () => {
    if (seedId || seedToken) {
      seedProduct = admin ? await S.request(`/products/${seedId}`) : await S.api(`/product-spec/studio/${encodeURIComponent(seedToken)}/detail`);
      kind = seedProduct.nonStandard ? "custom" : "standard";
      // Direct standard product URLs are detail links; customer tests always use the common entry.
      if (!seedProduct.nonStandard && !admin) { token = seedToken; await loadProduct(); return; }
    }
    await browse();
  });
})(window.PMS);
