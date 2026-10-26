(function (S) {
  "use strict";
  const esc = S.e,
    path = (id) => "/admin/product-master/products/" + id;
  S.copyProductDialog = async function (product) {
    const p = await S.request("/products/" + product.id);
    let priceStatus = null;
    try { priceStatus = (await S.request("/pricing/status", "POST", { productIds: [p.id] }))[0] || null; }
    catch (_) { /* Product copying remains available; pricing copying requires a verified status. */ }
    const optional = p.variants.filter((v) => !S.isBase(S.group(v.groupId)));
    const m = {
      productName: p.productName + " 복사",
      fixed: S.copy(p.variants.filter((v) => S.isBase(S.group(v.groupId)))),
      includedGroupIds: optional.map((v) => v.groupId),
      copyPricing: false,
    };
    const sourceProcess = p.process || { questions: [], rules: [], inputLimits: [], notices: [] };
    const d = S.dialog(
      "비규격 제품 복사 · 가져올 질문 선택",
      `<p class="pms-help">새 제품의 구성·질문·옵션·분기·첨부파일은 원본과 별도로 저장됩니다. <strong>실 제품·재고·입출고 이력은 복사하지 않습니다.</strong> 필수 분류와 공통 그룹 정의·FAQ 연결은 공유하며, 가격계산식은 아래에서 함께 복사할지 선택하며, 복사한 뒤에는 새 제품에서 독립적으로 수정·삭제할 수 있습니다.</p>${S.field("새 제품명", "productName", m.productName, "text", 'maxlength="160"')}<h3>1. 필수 분류 선택</h3><p class="pms-help">대분류·중분류·시리즈는 유지하며, 선택값은 새 제품에만 적용합니다.</p><div class="pms-form-grid three">${m.fixed
        .map((v, i) => {
          const g = S.group(v.groupId);
          return S.select(g.labels.management, "fixed." + i + ".valueId", v.valueIds[0],
            Object.fromEntries(g.values.filter((v) => v.active).map((v) => [v.id, v.labels.management])));
        }).join("")}</div><section class="pms-section"><h3>2. 복사할 질문·그룹 선택</h3><p class="pms-help">기본은 전체 선택입니다. 필요 없는 질문의 체크를 해제하세요. 제외 질문을 참조하는 분기·입력제한·안내는 설정 전체를 함께 제외하며, 일부 조건만 제거하여 적용 범위를 바꾸지 않습니다.</p><div class="pms-actions"><button type="button" data-copy-select-all>전체 선택</button><button type="button" data-copy-select-none>비필수 질문 모두 제외</button></div><div class="pms-check-grid">${optional.map((v, i) => {
          const g = S.group(v.groupId), q = sourceProcess.questions.find((q) => q.groupId === g.id);
          return `<label class="pms-check"><input type="checkbox" data-copy-include value="${g.id}" checked><span>${i + 1}. ${esc(q?.labels?.customer || g.labels.customer)} <small>(${esc(S.controls[g.control])})</small></span></label>`;
        }).join("") || '<p>복사할 비필수 질문이 없습니다. 필수 분류만 복사합니다.</p>'}</div><div class="pms-help" data-copy-summary role="status" aria-live="polite"></div></section><section class="pms-section"><h3>3. 가격계산식 복사 선택</h3><label class="pms-check"><input type="checkbox" data-copy-pricing-option><span>등록된 가격계산식과 테스트 입력도 함께 복사</span></label><p class="pms-help" data-copy-pricing-reason role="status" aria-live="polite"></p></section>`,
      { foot: '<button data-copy class="primary">선택한 구성으로 제품 복사</button>' },
    );
    m.fixed.forEach((v) => (v.valueId = v.valueIds[0]));
    S.bind(d, m, selectionSummary);
    function selectionSummary() {
      const groupIds = new Set([...m.fixed.map((v) => v.groupId), ...m.includedGroupIds]);
      const kept = sourceProcess.questions.filter((q) => groupIds.has(q.groupId));
      const keys = new Set(kept.map((q) => q.key));
      const conditionsPresent = (conditions) => (conditions || []).every((c) => c && keys.has(c.groupKey));
      const removedRules = (sourceProcess.rules || []).filter((r) => !conditionsPresent(r.conditions) || (r.actions || []).some((a) => !a || !keys.has(a.targetKey))).length;
      const removedLimits = (sourceProcess.inputLimits || []).filter((l) => !keys.has(l.targetKey) || (l.sourceKey && !keys.has(l.sourceKey)) || (l.cases || []).some((c) => !conditionsPresent(c.conditions))).length;
      const removedNotices = (sourceProcess.notices || []).filter((n) => !keys.has(n.targetKey) || !conditionsPresent(n.conditions)).length;
      const allCases = sourceProcess.questions.reduce((n, q) => n + (q.numberCases || []).length, 0);
      const keptCases = kept.reduce((n, q) => n + (q.numberCases || []).filter((c) => conditionsPresent(c.conditions)).length, 0);
      const removedTokens = (p.nameTokens || []).filter((t) => !groupIds.has(t.groupId)).length;
      const excluded = optional.length - m.includedGroupIds.length;
      const unchangedFixed = m.fixed.every((v) => Number(v.valueId) === Number(v.valueIds[0]));
      const pricingEligible = !!priceStatus?.registered && priceStatus.ready && !priceStatus.stale && !excluded && unchangedFixed;
      const priceInput = S.$("[data-copy-pricing-option]", d);
      priceInput.disabled = !pricingEligible;
      if (!pricingEligible) { m.copyPricing = false; priceInput.checked = false; }
      S.$("[data-copy-pricing-reason]", d).textContent = !priceStatus ? "가격 등록상태를 확인하지 못했습니다. 제품만 복사한 뒤 가격계산식 불러오기를 이용해 주세요."
        : !priceStatus.registered ? "원제품에 등록된 가격계산식이 없습니다."
        : priceStatus.stale || !priceStatus.ready ? "원제품의 프로세스·가격계산식 검증이 필요합니다. 원제품에서 검증·저장한 뒤 복사할 수 있습니다."
        : excluded || !unchangedFixed ? "모든 질문·옵션·입력묶음·분기·입력제한과 필수 분류 선택값이 동일해야 함께 복사할 수 있습니다. 현재 선택은 달라 제품만 복사합니다."
        : "현재 원본과 동일한 구성입니다. 선택하면 가격 항·곱셈 연결·가격표·테스트 입력을 새 제품에 별도로 복제합니다. 저장할 때 서버에서 동일성과 계산을 다시 검증합니다.";
      S.$("[data-copy-summary]", d).innerHTML = `<strong>필수 분류 3개 + 선택한 질문 ${m.includedGroupIds.length}개를 복사합니다.</strong><p>${excluded ? `제외: 질문 ${excluded}개 · 분기 ${removedRules}개 · 입력제한 ${removedLimits}개 · 안내 ${removedNotices}개 · 숫자안내 ${allCases - keptCases}개 · 제품명 요소 ${removedTokens}개` : "모든 질문과 연결 설정을 새 제품으로 복사합니다."}</p><p>${excluded ? "일부 질문을 제외한 제품은 ‘등록중’으로 생성됩니다. 복사 후 프로세스의 남은 연결을 확인하고 검증·등록완료해 주세요." : "복사 후 구성·질문·옵션을 수정해도 원본은 바뀌지 않습니다."} 실 제품 0종 · 재고 0개로 시작합니다.</p>`;
    }
    function readSelection() {
      m.includedGroupIds = S.$$("[data-copy-include]:checked", d).map((input) => Number(input.value));
      selectionSummary();
    }
    S.$$("[data-copy-include]", d).forEach((input) => { input.onchange = readSelection; });
    S.$("[data-copy-select-all]", d).onclick = () => { S.$$("[data-copy-include]", d).forEach((input) => { input.checked = true; }); readSelection(); };
    S.$("[data-copy-select-none]", d).onclick = () => { S.$$("[data-copy-include]", d).forEach((input) => { input.checked = false; }); readSelection(); };
    S.$("[data-copy-pricing-option]", d).onchange = (event) => { m.copyPricing = !event.currentTarget.disabled && event.currentTarget.checked; };
    selectionSummary();
    S.$("[data-copy]", d).onclick = (e) => S.run(e.currentTarget, async () => {
      const saved = await S.request("/products/" + p.id + "/copy", "POST", {
        productName: m.productName,
        sourceVersion: p.version,
        includedGroupIds: m.includedGroupIds,
        copyPricing: m.copyPricing,
        fixed: m.fixed.map((v) => ({ groupId: v.groupId, valueIds: [Number(v.valueId)], inputs: {} })),
      });
      S.dirty = false;
      location.href = path(saved.id) + "?copied=1";
    });
  };
  S.faqPage = async function (root) {
    let topics = await S.request("/faq");
    function paint() {
      root.innerHTML = `<section class="pms-panel"><div class="pms-panel-title"><h2>FAQ 주제 관리</h2><button data-new class="primary">+ 주제 등록</button></div><div class="pms-panel-body pm-faq-grid">${topics.map((t) => `<article class="pms-card pm-faq-topic"><h3>${esc(t.title)}</h3><p>${t.entries.length}개 FAQ · ${esc(t.phone || "전화 미등록")}</p><p>${esc(t.link || "링크 미등록")}</p><div class="pms-actions"><button data-edit="${t.id}">주제·FAQ 수정</button><button data-delete="${t.id}" class="danger">삭제</button></div></article>`).join("") || "<p>주제를 만든 뒤 그 안에 FAQ를 추가해 주세요.</p>"}</div></section>`;
      S.$("[data-new]", root).onclick = () => edit(null);
      S.$$("[data-edit]", root).forEach(
        (b) =>
          (b.onclick = () =>
            edit(topics.find((t) => t.id === Number(b.dataset.edit)))),
      );
      S.$$("[data-delete]", root).forEach(
        (b) =>
          (b.onclick = () =>
            S.run(b, async () => {
              const t = topics.find((t) => t.id === Number(b.dataset.delete));
              if (
                !(await S.confirm(
                  "주제와 FAQ를 삭제하고 연결된 제품에서 해제하시겠습니까?",
                ))
              )
                return;
              await S.request(
                "/faq/" + t.id + "?version=" + t.version,
                "DELETE",
              );
              await reload();
            })),
      );
    }
    async function reload() {
      topics = await S.request("/faq");
      S.dirty = false;
      paint();
    }
    function edit(topic) {
      const m = topic
        ? S.copy(topic)
        : {
            id: null,
            version: null,
            title: "",
            phone: "",
            link: "",
            entries: [],
          };
      const assets = new Map((topic?.assets || []).map((a) => [a.id, a]));
      const d = S.dialog(topic ? "FAQ 주제 수정" : "FAQ 주제 등록", "", {
          foot: '<button data-save class="primary">주제·FAQ 저장</button>',
        }),
        body = S.$(".pms-dialog-body", d);
      function draw() {
        body.innerHTML = `<div class="pms-form-grid two">${S.field("주제명", "title", m.title, "text", 'maxlength="160"')}${S.field("문의 전화번호", "phone", m.phone || "", "tel", 'maxlength="60"')}<div class="wide">${S.field("별도 문의 링크", "link", m.link || "", "url", 'maxlength="2000"')}</div></div><p class="pms-help"><strong>연결된 제품의 FAQ 버튼에서 제목·내용·이미지가 카드로 표시됩니다.</strong> 예: ‘설치 전 확인사항’ 주제에 배관 위치, 설치 공간, 사용 시 주의사항을 FAQ로 추가합니다. 전화번호와 문의 링크는 선택 항목입니다. 같은 주제를 여러 제품에 연결했다면 내용 수정은 그 제품들에 함께 반영됩니다.</p><div class="pms-actions"><button data-add>+ FAQ 추가</button></div>${m.entries.map((r, i) => `<article class="pms-card pm-faq-entry"><header class="pm-card-heading"><h3>FAQ ${i + 1}</h3><button data-remove="${i}" class="danger">삭제</button></header><div class="pms-form-grid one">${S.field("FAQ 제목", "entries." + i + ".title", r.title, "text", 'maxlength="200"')}<label><span>내용</span><textarea data-path="entries.${i}.content" maxlength="10000" rows="5">${esc(r.content)}</textarea></label></div><div data-faq-files="${i}">${S.files((r.assetIds || []).map((id) => assets.get(id)).filter(Boolean))}</div></article>`).join("")}`;
        S.bind(body, m);
        S.$("[data-add]", body).onclick = () => {
          m.entries.push({
            key: S.key("FAQ"),
            title: "",
            content: "",
            assetIds: [],
          });
          draw();
          const last = S.$$(".pm-faq-entry", body).at(-1);
          last?.scrollIntoView({ block: "nearest" });
          S.$("input", last)?.focus({ preventScroll: true });
        };
        S.$$("[data-remove]", body).forEach(
          (b) =>
            (b.onclick = () => {
              m.entries.splice(Number(b.dataset.remove), 1);
              draw();
            }),
        );
        S.$$("[data-faq-files]", body).forEach((el) => {
          const r = m.entries[Number(el.dataset.faqFiles)],
            fs = r.assetIds.map((id) => assets.get(id)).filter(Boolean);
          S.fileEvents(el, fs, () => {
            fs.forEach((a) => assets.set(a.id, a));
            r.assetIds = fs.map((a) => a.id);
          });
          S.$("input[type=file]", el)?.setAttribute("accept", "image/*");
        });
        S.$("[data-save]", d).onclick = (e) =>
          S.run(e.currentTarget, async () => {
            await S.request("/faq", "POST", {
              id: m.id,
              version: m.version,
              title: m.title,
              phone: m.phone,
              link: m.link,
              entries: m.entries,
            });
            d.close();
            await reload();
          });
      }
      draw();
    }
    paint();
    const topicId = Number(new URLSearchParams(location.search).get("topicId"));
    const initialTopic = topics.find((t) => t.id === topicId);
    if (initialTopic) edit(initialTopic);
  };
  S.productFaqDialog = async function (p, onSaved) {
    const topics = await S.request("/faq");
    const m = { faqTopicId: p.faqTopicId ?? "" };
    const previousDirty = S.dirty;
    const d = S.dialog("제품 FAQ 연결 · " + p.productName,
      `<p class="pms-help"><strong>규격·비규격 제품 모두 FAQ를 연결할 수 있습니다.</strong> 연결한 주제는 제품 상세와 고객 테스트의 FAQ 버튼에 표시됩니다. 실 제품이 등록되어도 FAQ 연결은 변경할 수 있습니다.</p><div class="pms-form-grid one">${S.select("이 제품에 표시할 FAQ 주제", "faqTopicId", m.faqTopicId, { "": "연결하지 않음", ...Object.fromEntries(topics.map((t) => [t.id, t.title])) })}</div><div data-topic-preview></div><div class="pms-actions"><a class="pms-button" href="/admin/product-master/faq" target="_blank" rel="noopener">FAQ 주제·내용 등록 / 수정</a><small>새 주제를 등록한 뒤 이 창을 다시 열어 연결해 주세요.</small></div>`,
      { foot: '<button type="button" data-save-product-faq class="primary">FAQ 연결 저장</button>', onClose: () => { S.dirty = previousDirty; } });
    const preview = () => {
      const t = topics.find((t) => t.id === Number(m.faqTopicId));
      S.$("[data-topic-preview]", d).innerHTML = t
        ? `<div class="pms-card"><strong>${esc(t.title)}</strong><p>등록된 FAQ ${(t.entries || []).length}개</p><button type="button" data-preview-product-faq>고객에게 표시되는 FAQ 미리보기</button></div>`
        : '<p class="pms-help">FAQ를 연결하지 않으면 고객 화면의 FAQ 버튼은 표시되지 않습니다.</p>';
      S.$("[data-preview-product-faq]", d)?.addEventListener("click", () => S.showProductFaq(t, { product: { name: p.productName, code: p.catalogCode } }));
    };
    S.bind(d, m, preview);
    preview();
    S.$("[data-save-product-faq]", d).onclick = (e) => S.run(e.currentTarget, async () => {
      const saved = await S.request("/products/" + p.id + "/faq", "PUT", { version: p.version, faqTopicId: m.faqTopicId ? Number(m.faqTopicId) : null });
      d.close();
      S.dirty = false;
      S.toast("제품 FAQ 연결을 저장했습니다.");
      if (onSaved) await onSaved(saved);
    });
  };
  S.viewPage = async function (root, p) {
    const topics = await S.request("/faq"), faq = topics.find((t) => t.id === p.faqTopicId);
    const locked = p.actualCount > 0;
    let priceStatus = null;
    if (p.nonStandard) {
      try { priceStatus = (await S.request("/pricing/status", "POST", { productIds: [p.id] }))[0]; }
      catch (err) { S.notice("가격계산 상태를 불러오지 못했습니다. " + err.message); }
    }
    const actions = `${locked ? '<button type="button" disabled title="실 제품이 등록되어 구성 수정이 잠겨 있습니다.">제품 구성 수정</button>' : `<a class="pms-button primary" href="${path(p.id)}">제품 구성 수정</a>`}${!p.nonStandard ? S.drawingLinks("products", p.id) : ""}${p.nonStandard ? `${locked ? '<button type="button" disabled title="실 제품이 등록되어 프로세스 수정이 잠겨 있습니다.">질문·프로세스 수정</button><a class="pms-button" href="' + path(p.id) + '/process?mode=view">등록 프로세스·분기 조회</a>' : `<a class="pms-button" href="${path(p.id)}/process">질문·프로세스 수정</a>`}<a class="pms-button" href="${path(p.id)}/actuals">실 제품·재고</a><a class="pms-button" href="${path(p.id)}/test" target="_blank" rel="noopener">비규격 테스트</a>` : ""}${p.nonStandard ? S.pricingLinks(p, priceStatus) : ""}<button type="button" data-link-product-faq>${faq ? "FAQ 연결 변경" : "+ FAQ 연결"}</button>${faq ? `<a class="pms-button" href="/admin/product-master/faq?topicId=${faq.id}" target="_blank" rel="noopener">FAQ 내용 수정</a>` : ""}`;
    S.mountProductDetail(root, p, { admin: true, faq, actions });
    if (!p.nonStandard) {
      root.insertAdjacentHTML("afterbegin", S.unitPriceCard(p));
      S.$("[data-open-unit-price]", root).onclick = (event) => S.run(event.currentTarget,
        () => S.unitPriceDialog(p, (saved) => S.viewPage(root, saved)));
    }
    S.$("[data-copy-pricing]", root)?.addEventListener("click", (e) => S.run(e.currentTarget, () => S.openPricingCopy(p, async () => S.viewPage(root, await S.request("/products/" + p.id)))));
    S.$("[data-link-product-faq]", root).onclick = () => S.productFaqDialog(p, (saved) => S.viewPage(root, saved));
  };
  // Each populated field is independent. An omitted field never narrows inventory results.
  S.actualMatchesFilter = function (row, filter, questions) {
    if (filter.code && !String(row.code || "").toLocaleLowerCase().includes(filter.code.toLocaleLowerCase()) && !String(row.parentCode || "").toLocaleLowerCase().includes(filter.code.toLocaleLowerCase())) return false;
    if (filter.minStock !== "" && Number(row.stock) < Number(filter.minStock)) return false;
    if (filter.maxStock !== "" && Number(row.stock) > Number(filter.maxStock)) return false;
    for (const q of questions) {
      const cond = filter.questions[q.key];
      if (!cond) continue;
      const answer = row.answers?.[q.key] || { choices: [], fields: {} };
      const keys = cond.choices || [];
      if (keys.length) {
        const saved = answer.choices || [];
        const match = cond.choiceMatch === "ANY" ? keys.some((key) => saved.includes(key))
          : cond.choiceMatch === "EXACT" ? keys.length === saved.length && keys.every((key) => saved.includes(key))
          : keys.every((key) => saved.includes(key));
        if (!match) return false;
      }
      for (const f of q.fields || []) {
        const value = cond.fields?.[f.key];
        if (value === undefined || value === "") continue;
        const raw = answer.fields?.[f.key];
        const present = raw !== undefined && raw !== null && raw !== "" && (!Array.isArray(raw) || raw.length > 0);
        if (q.control === "FILE") {
          if ((value === "PRESENT") !== present) return false;
        } else if (q.control === "NUMBER") {
          if (!present || !Number.isFinite(Number(raw)) || Number(raw) !== Number(value)) return false;
        } else {
          if (!present) return false;
          const saved = String(raw), query = String(value);
          if (cond.textMatch === "EXACT" ? saved !== query : !saved.toLocaleLowerCase().includes(query.toLocaleLowerCase())) return false;
        }
      }
    }
    return true;
  };
  S.actualsPage = async function (root, p) {
    let rows = [];
    let priceResults = new Map(), pricingIssue = "";
    const priceText = (r) => {
      const c = priceResults.get(r.id);
      return c?.complete ? `<strong>${S.e(Number(c.total).toLocaleString("ko-KR", { maximumFractionDigits: 2 }))}원</strong>` : `<small title="${esc((c?.errors || [pricingIssue || "가격식 미등록"]).join(" / "))}">${c ? "계산 불가" : "가격 확인 필요"}</small>`;
    };
    let filter = { code: "", minStock: "", maxStock: "", questions: {} };
    const questions = () => (p.process?.questions || []).filter((q) => !q.fixed && !S.isBase((p.groups || []).find((g) => g.id === q.groupId) || S.group(q.groupId) || {}));
    async function load() {
      [rows, p] = await Promise.all([S.request("/products/" + p.id + "/actuals"), S.request("/products/" + p.id)]);
      pricingIssue = "";
      try {
        priceResults = new Map((await S.request(`/products/${p.id}/pricing/actuals`)).map((c) => [c.actualId, c]));
      } catch (err) { priceResults = new Map(); pricingIssue = err.message; }
      paint();
    }
    function fieldFiltersHtml(q, saved) {
      const fieldHtml = (f, caption = "") => `<label${caption ? ` title="${esc(caption)}"` : ""}><span>${esc(f.labels.management || f.labels.customer)}${f.unit ? " (" + esc(f.unit) + ")" : ""}</span>${q.control === "FILE"
        ? `<select data-actual-filter-field="${esc(f.key)}"><option value="">첨부 여부 무관</option><option value="PRESENT" ${saved.fields?.[f.key] === "PRESENT" ? "selected" : ""}>파일 첨부한 제품</option><option value="ABSENT" ${saved.fields?.[f.key] === "ABSENT" ? "selected" : ""}>파일 첨부하지 않은 제품</option></select>`
        : `<input data-actual-filter-field="${esc(f.key)}" type="${q.control === "NUMBER" ? "number" : "text"}" ${q.control === "NUMBER" ? 'step="any"' : 'maxlength="10000"'} value="${esc(saved.fields?.[f.key] ?? "")}" placeholder="${q.control === "NUMBER" ? "정확히 일치하는 값 · 빈 칸은 전체" : "찾을 문자 · 빈 칸은 전체"}">`}</label>`;
      if (q.control !== "NUMBER") return `<div class="pms-form-grid two pm-actual-filter-fields">${(q.fields || []).map((f) => fieldHtml(f)).join("")}</div>`;
      const grouped = new Map();
      for (const f of q.fields || []) {
        const key = f.bundleKey || f.key;
        if (!grouped.has(key)) grouped.set(key, { key, fields: [] });
        grouped.get(key).fields.push(f);
      }
      const bundles = S.numberBundles ? S.numberBundles(q) : [...grouped.values()];
      return `<div class="pm-number-answer-bundles pm-actual-filter-bundles">${bundles.map((bundle, i) => {
        const names = bundle.fields.map((f) => f.labels.management || f.labels.customer).join(" + ");
        const title = `${bundle.fields.length > 1 ? "묶음" : "독립 입력"} ${i + 1} · ${names}`;
        const rules = (p.process?.rules || []).filter((r) => (r.actions || []).some((a) => a.targetKey === q.key && a.effect === "ALLOW" && bundle.fields.some((f) => (a.choiceKeys || []).includes(f.key))));
        const sourceText = (rule) => (rule.conditions || []).map((c) => {
          const source = (p.process.questions || []).find((x) => x.key === c.groupKey);
          return (source?.labels.management || source?.labels.customer || "앞 질문") + " · " + S.conditionText(source, c);
        }).join(rule.match === "ANY" ? " 또는 " : " 그리고 ");
        const caption = title + (rules.length ? "\n표시 분기: " + rules.map((r) => r.name + " (" + sourceText(r) + ")").join("\n") : "\n별도 표시 분기 없음");
        return `<section class="pm-number-answer-bundle ${bundle.fields.length > 1 ? "grouped" : ""}" data-actual-filter-bundle="${i + 1}"><div class="pm-answer-bundle-title"><strong>${esc(title)}</strong></div>${rules.length ? `<div class="pms-help pm-actual-bundle-sources"><strong>등록된 표시 분기</strong>${rules.map((r) => `<p><b>${esc(r.name)}</b> · ${esc(sourceText(r))}</p>`).join("")}<small>위 분기는 항목을 구별하기 위한 설명입니다. 재고 조회에서는 분기를 실행하지 않고 아래에 입력한 값만 비교합니다.</small></div>` : ""}<div class="pms-form-grid two pm-actual-filter-fields">${bundle.fields.map((f) => fieldHtml(f, caption)).join("")}</div></section>`;
      }).join("")}</div>`;
    }
    function filterHtml() {
      return `<details class="pm-actual-filters" open><summary><strong>실제 사양 상세 필터</strong><small>필요한 값만 입력하여 재고를 찾습니다.</small></summary><div class="pms-panel-body"><p class="pms-help"><strong>입력한 조건끼리는 모두 일치해야 합니다.</strong> 분기 순서와 상관없이 커스텀 항목을 직접 검색합니다. 빈 칸은 검색에서 제외합니다. 예: 사이즈 W에 <strong>500</strong>만 입력하면 H·D에 관계없이 W=500인 모든 실 제품이 나옵니다. 해당 항목을 입력하지 않은 제품은 값 검색 결과에 포함되지 않습니다.</p><div class="pms-search-grid pm-actual-filter-bar"><label><span>실 제품 코드 일부</span><input data-actual-filter-code value="${esc(filter.code)}" placeholder="코드를 모르면 비워 두세요"></label><label><span>재고 수량 · 최소</span><input data-actual-stock-min type="number" min="0" step="1" value="${esc(filter.minStock)}" placeholder="제한 없음"></label><label><span>재고 수량 · 최대</span><input data-actual-stock-max type="number" min="0" step="1" value="${esc(filter.maxStock)}" placeholder="제한 없음"></label></div><div class="pm-actual-filter-grid">${questions().map((q) => {
        const saved = filter.questions[q.key] || { choices: [], fields: {} };
        return `<article class="pms-card pm-actual-filter-question" data-actual-filter-question="${esc(q.key)}"><header class="pm-card-heading"><div><h3>${esc(q.labels.management || q.labels.customer)}</h3><small>${esc(S.controls[q.control])} · ${esc(q.question || q.labels.customer)}</small></div></header>${S.isChoice(q.control)
          ? `<div class="pms-check-grid">${(q.choices || []).map((c) => `<label class="pms-check"><input type="checkbox" data-actual-filter-choice value="${esc(c.key)}" ${(saved.choices || []).includes(c.key) ? "checked" : ""}>${esc(c.labels.management || c.labels.customer)}</label>`).join("") || '<small>등록된 답변이 없습니다.</small>'}</div>${q.control === "CHECKBOX" ? `<label><span>선택 조건 적용 방식</span><select data-actual-choice-match><option value="ALL" ${saved.choiceMatch !== "ANY" && saved.choiceMatch !== "EXACT" ? "selected" : ""}>선택한 항목 모두 포함</option><option value="ANY" ${saved.choiceMatch === "ANY" ? "selected" : ""}>선택한 항목 중 하나 포함</option><option value="EXACT" ${saved.choiceMatch === "EXACT" ? "selected" : ""}>선택한 항목과 완전히 동일</option></select></label>` : '<small>여러 답변을 체크하면 그중 하나를 선택한 실 제품을 찾습니다.</small>'}`
          : `${q.control === "TEXT" || q.control === "TEXTAREA" ? `<label><span>문자 검색 방식</span><select data-actual-text-match><option value="CONTAINS" ${saved.textMatch !== "EXACT" ? "selected" : ""}>입력한 문자 포함 · 영문 대소문자 구분 없음</option><option value="EXACT" ${saved.textMatch === "EXACT" ? "selected" : ""}>입력한 문자와 완전히 동일</option></select></label>` : ""}${fieldFiltersHtml(q, saved)}`}</article>`;
      }).join("") || '<p class="pms-help">검색 가능한 커스텀 항목이 없습니다.</p>'}</div><div data-actual-filter-errors></div><div class="pms-actions"><button type="button" data-apply-actual-filter class="primary">입력한 사양으로 조회</button><button type="button" data-reset-actual-filter>필터 초기화 · 전체 조회</button></div></div></details>`;
    }
    function paint() {
      const visibleRows = rows.filter((r) => S.actualMatchesFilter(r, filter, questions()));
      root.innerHTML = `<section class="pms-panel"><div class="pms-panel-title"><div class="pm-header-content"><h2>${esc(p.productName)} · 실 제품 재고</h2><small>${esc(p.catalogCode)} · ${rows.length}종 / 총 재고 ${p.stock}개${p.unallocatedStock ? " / 기존 미배정 재고 " + p.unallocatedStock + "개" : ""}</small></div><div class="pms-actions"><a class="pms-button" href="${path(p.id)}/view">원제품 상세·분기 조회</a><a class="pms-button" href="${path(p.id)}/pricing">가격계산식 관리</a><button type="button" data-refresh-prices>가격 다시 계산</button><button data-create class="primary" ${p.status === "DRAFT" || p.status === "DISCONTINUED" ? "disabled" : ""}>+ 실제 사양 등록</button></div></div><div class="pms-panel-body"><p class="pms-help">검증 완료된 제품의 허용 범위 안에서 실제 사양을 선택합니다. 실 제품이 하나라도 등록되면 원본 제품의 구성·프로세스는 잠깁니다. 등록된 구성과 분기는 원제품 상세에서 계속 확인할 수 있습니다. 도면은 각 실 제품에 개별 등록합니다.</p>${pricingIssue ? `<div class="pms-alert">가격계산: ${esc(pricingIssue)}</div>` : ""}<p class="pms-help">금액은 현재 가격식으로 계산한 실 제품 1개의 단가이며, 재고 수량과 별개입니다. 가격식 수정 시 새 가격으로 다시 계산됩니다.</p>${filterHtml()}<div class="pm-code-search"><label><span>제품 코드의 상세 조회</span><input data-code placeholder="제품 코드 또는 실 제품 코드"></label><button data-decode>코드 조회</button></div><div data-decoded></div><div class="pms-toolbar"><strong class="pm-actual-result-count" aria-live="polite">검색 결과 ${visibleRows.length.toLocaleString()}종 / 전체 ${rows.length.toLocaleString()}종</strong><small>결과 재고 합계 ${visibleRows.reduce((sum, r) => sum + Number(r.stock || 0), 0).toLocaleString()}개</small><span class="pm-delete-count" data-delete-count aria-live="polite">0개 선택</span><button type="button" class="danger" data-delete-button disabled>삭제</button><small>전체선택·삭제는 현재 검색 결과에만 적용됩니다. 필터를 바꾸면 선택이 해제됩니다.</small></div><div class="pms-table-scroll"><table class="pm-actual-table"><thead><tr><th class="pm-select-cell"><label class="pms-check pm-delete-check"><input type="checkbox" data-delete-all aria-label="검색 결과 실 제품 전체선택"></label></th><th>실 제품 코드</th><th>실제 사양</th><th>재고</th><th>계산 단가</th><th>관리</th></tr></thead><tbody>${visibleRows.map((r) => `<tr><td class="pm-select-cell"><label class="pms-check pm-delete-check"><input type="checkbox" data-delete-select="${r.id}" aria-label="${esc(r.code)} 삭제 선택"></label></td><td>${esc(r.code)}<small>소속: ${esc(r.parentCode)}</small></td><td>${S.specSummary(r.specs.map((s) => ({ label: s.group, value: s.value })))}</td><td>${r.stock}</td><td data-actual-price="${r.id}">${priceText(r)}</td><td><div class="pms-actions pm-stock-actions" style="flex-wrap:nowrap;white-space:nowrap"><button type="button" data-calculate-price="${r.id}">가격계산</button><button data-stock="${r.id}">입출고·조정</button><button data-history="${r.id}">이력</button>${S.drawingLinks("actuals", r.id)}</div></td></tr>`).join("") || '<tr><td colspan="6"><div class="pms-empty">조건에 맞는 실 제품이 없습니다. 필터를 초기화하거나 입력값을 확인해 주세요.</div></td></tr>'}</tbody></table></div></div></section>`;
      S.bindDeletion(root, visibleRows, { product: p, onDeleted: load });
      S.$("[data-refresh-prices]", root).onclick = (e) => S.run(e.currentTarget, load);
      S.$$("[data-calculate-price]", root).forEach((button) => {
        button.onclick = () => S.run(button, async () => {
          const result = await S.request(`/products/${p.id}/pricing/actuals/${button.dataset.calculatePrice}`);
          priceResults.set(result.actualId, result);
          const row = rows.find((r) => r.id === result.actualId);
          const cell = S.$(`[data-actual-price="${result.actualId}"]`, root);
          if (cell && row) cell.innerHTML = priceText(row);
          S.showPricingCalculation(result, "가격계산 · " + (row?.code || "실 제품"));
        });
      });
      S.$("[data-apply-actual-filter]", root).onclick = applyFilter;
      S.$("[data-reset-actual-filter]", root).onclick = () => { filter = { code: "", minStock: "", maxStock: "", questions: {} }; paint(); };
      S.$(".pm-actual-filters", root).onkeydown = (e) => { if (e.key === "Enter" && e.target.matches("input:not([type=checkbox])")) { e.preventDefault(); applyFilter(); } };
      S.$("[data-create]", root).onclick = create;
      S.$("[data-code]", root).onkeydown = (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          S.$("[data-decode]", root).click();
        }
      };
      S.$("[data-decode]", root).onclick = (e) =>
        S.run(e.currentTarget, async () => {
          const r = await S.request(
            "/decode?code=" +
              encodeURIComponent(S.$("[data-code]", root).value.trim()),
          );
          S.$("[data-decoded]", root).innerHTML =
            `<div class="pms-card"><strong>${esc(r.productName)}</strong><p>${esc(r.code || r.catalogCode)} ${r.parentCode ? " / 소속 " + esc(r.parentCode) : ""}</p>${r.specs ? r.specs.map((s) => `<p>${esc(s.group)}: ${esc(s.value)}</p>`).join("") : `<a href="${path(r.id)}/view">제품 사양 보기</a>`}</div>`;
        });
      S.$$("[data-stock]", root).forEach(
        (b) =>
          (b.onclick = () =>
            stock(rows.find((r) => r.id === Number(b.dataset.stock)))),
      );
      S.$$("[data-history]", root).forEach(
        (b) =>
          (b.onclick = () =>
            S.run(b, async () => {
              const history = await S.request(
                "/products/" +
                  p.id +
                  "/actuals/" +
                  b.dataset.history +
                  "/history",
              );
              S.dialog(
                "실 제품 재고 이력",
                '<div class="pms-table-scroll pm-history-table"><table><thead><tr><th>일시</th><th>변경</th><th>잔고</th><th>사유</th><th>담당자</th></tr></thead><tbody>' +
                  history
                    .map(
                      (h) =>
                        `<tr><td>${esc(h.createdAt)}</td><td>${h.delta}</td><td>${h.stockAfter}</td><td>${esc(h.reason)}</td><td>${esc(h.actor)}</td></tr>`,
                    )
                    .join("") +
                  "</tbody></table></div>",
              );
            })),
      );
    }
    function applyFilter() {
      const next = { code: S.$("[data-actual-filter-code]", root).value.trim(), minStock: S.$("[data-actual-stock-min]", root).value, maxStock: S.$("[data-actual-stock-max]", root).value, questions: {} };
      const errors = [];
      const finiteInteger = (v) => v === "" || (Number.isSafeInteger(Number(v)) && Number(v) >= 0);
      if (!finiteInteger(next.minStock) || !finiteInteger(next.maxStock)) errors.push("재고 수량은 0 이상의 정수로 입력해 주세요.");
      if (next.minStock !== "" && next.maxStock !== "" && Number(next.minStock) > Number(next.maxStock)) errors.push("최소 재고 수량을 최대 재고 수량 이하로 수정해 주세요.");
      S.$$("[data-actual-filter-question]", root).forEach((el) => {
        const key = el.dataset.actualFilterQuestion, q = questions().find((q) => q.key === key);
        const condition = { choices: S.$$("[data-actual-filter-choice]:checked", el).map((x) => x.value), fields: {}, choiceMatch: S.$("[data-actual-choice-match]", el)?.value || "ANY", textMatch: S.$("[data-actual-text-match]", el)?.value || "CONTAINS" };
        S.$$("[data-actual-filter-field]", el).forEach((input) => {
          if (!input.validity.valid || (q.control === "NUMBER" && input.value !== "" && !Number.isFinite(Number(input.value)))) errors.push((q.labels.management || q.labels.customer) + " · " + (q.fields.find((f) => f.key === input.dataset.actualFilterField)?.labels.management || input.dataset.actualFilterField) + ": 숫자를 올바르게 입력해 주세요.");
          if (input.value !== "") condition.fields[input.dataset.actualFilterField] = input.value;
        });
        next.questions[key] = condition;
      });
      const box = S.$("[data-actual-filter-errors]", root);
      box.innerHTML = errors.length ? `<div class="pms-alert">${errors.map((text) => `<p>${esc(text)}</p>`).join("")}</div>` : "";
      if (errors.length) { box.scrollIntoView({ block: "nearest" }); return; }
      filter = next;
      paint();
    }
    function stock(r) {
      const m = { version: r.version, delta: 0, reason: "" };
      const d = S.dialog(
        r.code + " · 재고 변경",
        `<p>현재 ${r.stock}개 · 입고는 양수, 출고는 음수</p><div class="pms-form-grid two">${S.field("변경 수량", "delta", 0, "number", 'step="1"')}${S.field("사유", "reason", "", "text", 'maxlength="500"')}${p.unallocatedStock ? S.check("기존 미배정 재고에서 배정", "allocateLegacy", false) : ""}</div>`,
        {
          wide: false,
          foot: '<button data-save class="primary">재고 반영</button>',
        },
      );
      S.bind(d, m);
      S.$("[data-save]", d).onclick = (e) =>
        S.run(e.currentTarget, async () => {
          await S.request(
            "/products/" + p.id + "/actuals/" + r.id + "/stock",
            "POST",
            m,
          );
          d.close();
          await load();
        });
    }
    async function create() {
      let answers = {},
        visited = [],
        evaluation;
      const assets = new Map([...(p.assets || []), ...(p.processAssets || []), ...(p.groups || []).flatMap((g) => [...(g.assets || []), ...(g.values || []).flatMap((v) => v.assets || [])])].map((a) => [a.id, a]));
      let evaluationSequence = 0, timer;
      const d = S.dialog("실제 사양 등록", ""),
        body = S.$(".pms-dialog-body", d);
      d.addEventListener("close", () => { clearTimeout(timer); evaluationSequence++; });
      async function evaluate() {
        evaluation = await S.request(
          "/products/" + p.id + "/evaluate",
          "POST",
          { answers },
        );
        answers = S.copy(evaluation.answers);
        draw();
      }
      function draw() {
        clearTimeout(timer);
        evaluationSequence++;
        const states = evaluation.questions.filter(
            (s) => s.visible && !s.question.fixed,
          ),
          state = states.find((s) => !visited.includes(s.question.key));
        if (!state) {
          body.innerHTML =
            "<h3>실제 사양 확인</h3>" +
            evaluation.questions
              .filter((s) => s.visible || s.question.fixed)
              .map(
                (s) =>
                  `<p>${esc(s.question.labels.management)}: ${esc(
                    S.isChoice(s.question.control)
                      ? s.answer.choices
                          .map(
                            (k) =>
                              s.question.choices.find((c) => c.key === k)
                                ?.labels.management,
                          )
                          .join(", ")
                      : Object.entries(s.answer.fields)
                          .map(
                            ([k, v]) =>
                              (s.question.fields.find((f) => f.key === k)
                                ?.labels.management || k) +
                              ": " +
                              (Array.isArray(v) ? v.length + "개 파일" : v),
                          )
                          .join(" / "),
                  )}</p>`,
              )
              .join("") +
            '<div class="pms-form-grid">' +
            S.field("최초 재고", "quantity", 0, "number", 'min="0" step="1"') +
            S.field("등록 사유", "reason", "실 제품 최초 등록") +
            (p.unallocatedStock
              ? S.check(
                  "기존 미배정 재고에서 배정 (" + p.unallocatedStock + "개)",
                  "allocateLegacy",
                  true,
                )
              : "") +
            `</div>${evaluation.complete ? "" : `<div class="pms-alert"><strong>실제 사양의 검증을 완료해야 등록할 수 있습니다.</strong>${(evaluation.errors || []).map((text) => `<p>${esc(text)}</p>`).join("")}</div>`}<div class="pms-actions"><button data-back>선택 다시하기</button><button data-save class="primary" ${evaluation.complete ? "" : "disabled"}>실 제품 등록</button></div>`;
          const m = {
            answers,
            quantity: 0,
            reason: "실 제품 최초 등록",
            allocateLegacy: !!p.unallocatedStock,
          };
          S.bind(body, m);
          S.$("[data-back]", body).onclick = () => {
            visited = [];
            draw();
          };
          S.$("[data-save]", body).onclick = (e) =>
            S.run(e.currentTarget, async () => {
              await S.request("/products/" + p.id + "/actuals", "POST", m);
              d.close();
              await load();
            });
          return;
        }
        const q = {
          ...state.question,
          choices: state.question.choices.filter((c) =>
            state.allowed.includes(c.key),
          ),
        };
        body.innerHTML = `<p>${visited.length + 1} / ${states.length}</p><h3>${esc(q.question || q.labels.management)}</h3>${q.guide?.trim() ? `<div class="pms-help pm-preserve-lines">${esc(q.guide)}</div>` : ""}${S.media((q.assetIds || []).map((id) => assets.get(id)), q.labels.customer + " 안내 이미지")}<div data-answer>${S.inputAnswer(q, state.answer, false, assets)}</div>${S.inputLimitSummary(p.process, q)}<div data-answer-notices></div><div data-error></div><div class="pms-actions"><button data-back ${visited.length ? "" : "disabled"}>이전</button><button data-next class="primary">다음</button></div>`;
        const box = S.$("[data-answer]", body);
        S.bindMedia(body);
        const notices = (fresh, answer) => {
          S.$("[data-answer-notices]", body).innerHTML = S.answerGuides(q, answer, fresh).map((text) => `<div class="pms-help pm-preserve-lines">${esc(text)}</div>`).join("");
        };
        notices(state, state.answer);
        const liveEvaluate = () => {
          clearTimeout(timer);
          const sequence = ++evaluationSequence;
          timer = setTimeout(async () => {
            if (!box.isConnected) return;
            const currentAnswer = S.readAnswer(box, q, answers[q.key] || state.answer);
            try {
              const result = await S.request("/products/" + p.id + "/evaluate", "POST", { answers: { ...answers, [q.key]: currentAnswer } });
              if (sequence !== evaluationSequence || !box.isConnected) return;
              notices(result.questions.find((s) => s.question.key === q.key), currentAnswer);
            } catch (error) {
              if (sequence === evaluationSequence && box.isConnected) S.$("[data-error]", body).innerHTML = `<div class="pms-alert">${esc(error.message)}</div>`;
            }
          }, 250);
        };
        box.addEventListener("input", liveEvaluate);
        box.addEventListener("change", liveEvaluate);
        S.answerFileEvents(box, q, S.copy(state.answer || { choices: [], fields: {} }), assets, {
          onChange: (answer) => { answers[q.key] = S.copy(answer); },
          onBusy: (busy) => { S.$("[data-next]", body).disabled = busy; S.$("[data-back]", body).disabled = busy || !visited.length; },
          onError: (error) => { if (box.isConnected) S.$("[data-error]", body).innerHTML = `<div class="pms-alert">${esc(error.message)}</div>`; },
          isCurrent: () => box.isConnected && d.open,
        });
        S.$("[data-back]", body).onclick = () => {
          visited.pop();
          draw();
        };
        S.$("[data-next]", body).onclick = (e) =>
          S.run(e.currentTarget, async () => {
            if (S.pendingUploads) throw Error("첨부파일 등록을 완료한 뒤 다음 단계로 진행해 주세요.");
            clearTimeout(timer); evaluationSequence++;
            answers[q.key] = S.readAnswer(box, q, answers[q.key]);
            const next = await S.request(
              "/products/" + p.id + "/evaluate",
              "POST",
              { answers },
            );
            const fresh = next.questions.find((s) => s.question.key === q.key);
            if (fresh.errors.length) {
              S.$("[data-error]", body).innerHTML = `<div class="pms-alert">${fresh.errors.map((text) => `<p>${esc(text)}</p>`).join("")}</div>`;
              return;
            }
            visited.push(q.key);
            evaluation = next;
            answers = S.copy(next.answers);
            visited = visited.filter((k) =>
              next.questions.some(
                (s) => s.question.key === k && s.visible && !s.errors.length,
              ),
            );
            draw();
          });
      }
      await evaluate();
    }
    await load();
  };
})(window.PMS);
