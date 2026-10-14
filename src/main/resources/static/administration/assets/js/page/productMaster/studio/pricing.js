(function (S) {
  "use strict";
  const e = S.e, label = (v) => v?.labels?.management || v?.labels?.customer || v?.label || v?.key || "";
  const decimalText = (value, scale) => {
    const text = String(value), match = text.match(/^([+-]?)(\d*\.?\d+)(?:[eE]([+-]?\d+))?$/);
    if (!match || Math.abs(Number(match[3] || 0)) > 100) return text;
    const pieces = match[2].split("."), digits = pieces.join(""), point = pieces[0].length + Number(match[3] || 0);
    let whole = point <= 0 ? "0" : point >= digits.length ? digits.padEnd(point, "0") : digits.slice(0, point);
    let fraction = point <= 0 ? "0".repeat(-point) + digits : point >= digits.length ? "" : digits.slice(point);
    whole = (whole.replace(/^0+/, "") || "0").replace(/\B(?=(\d{3})+(?!\d))/g, ",");
    fraction = fraction.slice(0, scale).replace(/0+$/, "");
    return (match[1] === "-" ? "-" : "") + whole + (fraction ? "." + fraction : "");
  };
  const money = (v) => v == null ? "계산 대기" : decimalText(v, 2) + "원";
  const rateText = (v) => v == null ? "미입력" : decimalText(v, 6) + "원";
  const types = { FIXED: "고정 금액", MULTIPLY: "입력값 × 단가", MATRIX_1D: "1차원 기준표", MATRIX_2D: "2차원 기준표" };
  const key = () => "price_" + crypto.randomUUID().replaceAll("-", "");
  const emptyDefinition = (context) => ({ schemaVersion: 1, schemaFingerprint: context.schemaFingerprint, terms: [], testAnswers: {} });
  // Keep decimal text intact; Jackson converts it to BigDecimal without floating-point rounding.
  const numeric = (value) => value === "" ? null : value;
  // Headers and conditions can contain six decimals and up to 24 digits. Compare decimal text exactly.
  const compareDecimal = (left, right) => {
    const parts = (value) => {
      const match = String(value).trim().match(/^([+-]?)(\d*\.?\d+)(?:[eE]([+-]?\d+))?$/);
      if (!match) return null;
      const exponent = Number(match[3] || 0), fraction = match[2].split(".")[1]?.length || 0;
      const digits = match[2].replace(".", "").replace(/^0+/, "") || "0";
      return { sign: digits === "0" ? 0 : match[1] === "-" ? -1 : 1, digits, magnitude: digits.length + exponent - fraction };
    };
    const a = parts(left), b = parts(right); if (!a || !b) return NaN;
    if (a.sign !== b.sign) return a.sign < b.sign ? -1 : 1; if (!a.sign) return 0;
    if (a.magnitude !== b.magnitude) return (a.magnitude < b.magnitude ? -1 : 1) * a.sign;
    const size = Math.max(a.digits.length, b.digits.length), x = a.digits.padEnd(size, "0"), y = b.digits.padEnd(size, "0");
    return (x === y ? 0 : x < y ? -1 : 1) * a.sign;
  };
  const opt = (value, text, selected) => `<option value="${e(value)}" ${String(value) === String(selected) ? "selected" : ""}>${e(text)}</option>`;
  const field = (text, attr, value, more = "") => `<label><span>${e(text)}</span><input ${attr} value="${e(value)}" ${more}></label>`;
  const api = (id, suffix = "", method = "GET", data) => S.request(`/products/${id}/pricing${suffix}`, method, data);
  S.descriptions = { ...(S.descriptions || {}), pricing: "가격 영향 그룹과 입력묶음으로 항을 만든 뒤, 실제 질문 흐름에서 계산을 확인하고 저장합니다." };
  S.pricingGuide = () => S.dialog("비규격 가격계산 사용 순서", `<ol class="pm-price-guide"><li><strong>그룹과 입력묶음을 확인합니다.</strong><p>프로세스를 등록완료한 제품만 계산식을 저장할 수 있습니다. W·D를 함께 계산하려면 프로세스에서 두 필드를 같은 입력묶음으로 등록합니다. 높이 H는 독립묶음으로 등록하면 별도의 항으로 계산할 수 있습니다.</p></li><li><strong>필요한 입력묶음마다 항을 만듭니다.</strong><p>W·D 묶음은 2차원 기준표, H는 1차원 기준표, 문·세면대 수량은 ‘입력값 × 단가’를 선택하세요. 고정 추가비용은 고정 금액으로 등록합니다. 사용하지 않을 그룹이나 묶음은 항을 만들지 않아도 됩니다.</p></li><li><strong>기준표와 추가 적용 조건을 작성합니다.</strong><p>엑셀에는 금액만 입력하고, 업로드 후 X·Y 기준값과 이하·미만을 지정합니다. 행·열을 추가·삭제하며 표를 조정할 수 있습니다. 세면대 A의 수량 단가는 해당 분기 묶음에 연결하세요. 필요한 경우 ‘세면대 종류 = A’와 같은 추가 조건을 지정합니다.</p></li><li><strong>최종 합계식과 실제 입력을 확인하고 저장합니다.</strong><p>아래 테스트에서 사이즈와 문·세면대 종류를 선택하면 프로세스와 동일한 분기와 입력 제한이 적용됩니다. 표시·허용된 항만 계산되며, 가격계산 버튼에서 항별 근거를 볼 수 있습니다. 저장할 때 전체 분기와 기준표 범위를 다시 검증합니다.</p></li></ol><p class="pms-help">예: 높이 기준 500·600·700에 금액 0·20,000·40,000원을 입력하고 ‘이하’를 선택하면 H=500은 0원, H=501~600은 20,000원이 더해집니다. 사용 가능한 최대 높이까지 기준을 작성해 주세요.</p><p class="pms-help">가격계산식이 연결된 프로세스와 가격 항에 사용 중인 그룹의 가격영향 설정은 잠깁니다. 금액과 기준표는 수정할 수 있습니다. 가격계산 불러오기는 그룹·옵션·분기·입력 제한이 모두 같은 제품만 사용할 수 있습니다.</p>`);

  // Both the list and the price editor use this entry point. Server fingerprints determine compatibility.
  S.openPricingCopy = async function (product, onCopied) {
    const context = await api(product.id, "/context");
    if (context.registered) throw Error("가격계산식이 이미 등록되어 있습니다. 가격계산 화면에서 조회·수정·삭제해 주세요.");
    if (!context.ready) throw Error((context.issues || []).join("\n") || "프로세스의 옵션·분기 등록을 먼저 완료해 주세요.");
    const sources = await api(product.id, "/copy-sources");
    const d = S.dialog("동일 구성 제품의 가격계산 불러오기", `<p class="pms-help">그룹, 입력묶음, 옵션, 분기와 입력 제한이 모두 같은 제품만 표시됩니다. 원본의 가격계산식을 독립적으로 복사합니다.</p><div class="pm-price-copy-list">${sources.map((p) => `<label class="pm-price-copy-item"><input type="radio" name="price-source" value="${p.productId}"><span><strong>${e(p.productName)}</strong><small>${e(p.catalogCode)}</small></span></label>`).join("") || '<div class="pms-empty">그룹과 프로세스가 완전히 같은 등록 제품이 없습니다.</div>'}</div>`, { foot: `<button type="button" data-copy class="primary" ${sources.length ? "" : "disabled"}>선택한 가격계산식 불러오기</button>` });
    S.$("[data-copy]", d).onclick = (event) => S.run(event.currentTarget, async () => {
      const selected = S.$('[name="price-source"]:checked', d), source = sources.find((p) => String(p.productId) === selected?.value);
      if (!source) throw Error("불러올 제품을 선택해 주세요.");
      await api(product.id, "/copy", "POST", { sourceProductId: source.productId, version: context.version, sourceVersion: source.version });
      d.close(); S.toast("가격계산식을 불러왔습니다."); await onCopied?.();
    });
    return d;
  };

  S.pricingPage = async function (root, product) {
    let context, definition, answers = {}, evaluation = null, result = null, sequence = 0, testTimer, active = true;
    const assetMap = new Map([...(product.assets || []), ...(product.processAssets || []), ...(product.groups || []).flatMap((g) => [...(g.assets || []), ...(g.values || []).flatMap((v) => v.assets || [])])].map((a) => [a.id, a]));
    const supported = (g) => ["NUMBER", "RADIO", "CHECKBOX"].includes(g?.control);
    const pricingGroups = () => context.groups.filter(supported);
    const group = (groupKey) => context.groups.find((g) => g.groupKey === groupKey);
    const process = () => context.process || product.process || { questions: [] };
    const changed = () => { S.dirty = true; result = null; updateFormula(); clearResult("계산식을 변경했습니다. 아래에서 다시 계산해 주세요."); };
    const cancelTest = () => { clearTimeout(testTimer); sequence++; };
    function mergeTestAnswers(next, draft) {
      const merged = S.copy(next.answers || {});
      for (const state of next.questions || []) {
        const q = state.question;
        if (!state.visible && !q.fixed) { delete merged[q.key]; continue; }
        if (q.fixed || !draft[q.key]) continue;
        if (S.isChoice(q.control)) merged[q.key] = { choices: (draft[q.key].choices || []).filter((value) => state.allowed.includes(value)), fields: {} };
        else {
          const fields = new Set((q.fields || []).map((f) => f.key));
          merged[q.key] = { choices: [], fields: Object.fromEntries(Object.entries(draft[q.key].fields || {}).filter(([fieldKey]) => fields.has(fieldKey) && state.allowed.includes(fieldKey))) };
        }
      }
      return merged;
    }
    window.addEventListener("pagehide", () => { active = false; cancelTest(); }, { once: true });
    async function load() {
      cancelTest(); context = await api(product.id, "/context");
      definition = S.copy(context.definition || emptyDefinition(context));
      definition.terms ??= []; definition.testAnswers ??= {};
      answers = S.copy(definition.testAnswers); evaluation = null; result = null; S.dirty = false; paint();
      if (context.ready) await evaluateProcess(true);
    }
    function paint() {
      root.innerHTML = `<div class="pm-price-page"><section class="pms-panel"><header class="pms-panel-title"><div><strong>${e(product.productName)}</strong><small class="pm-price-product-code">${e(product.catalogCode || product.productCode)}</small></div><div class="pms-actions">${S.badge(context.registered ? "가격계산 등록됨" : "가격계산 미등록", context.registered ? "green" : "")}${context.stale ? S.badge("프로세스 변경 · 삭제 후 재등록 필요", "red") : ""}<a class="pms-button" href="/admin/product-master/non-standard">비규격 목록</a><a class="pms-button" href="/admin/product-master/products/${product.id}/process${product.actualCount || context.registered ? "?mode=view" : ""}">프로세스 ${context.registered ? "조회 (가격식 연결)" : product.actualCount ? "조회" : "확인·수정"}</a></div></header><div class="pms-panel-body"><p class="pms-help">① 가격 영향 그룹 확인 → ② 항 추가 → ③ 합계식 확인 → ④ 실제 분기 테스트 → 저장 순서로 진행합니다. 사용하지 않는 그룹·입력묶음은 항으로 등록하지 않아도 됩니다.</p>${context.registered && !context.stale ? '<p class="pms-help">가격계산식과 연결된 프로세스는 조회로 열립니다. 가격 금액·기준표는 이 화면에서 계속 수정할 수 있습니다.</p>' : ""}${!context.ready ? `<div class="pms-alert"><strong>가격계산 등록 조건을 확인해 주세요.</strong>${(context.issues || []).map((x) => `<p>${e(x)}</p>`).join("")}</div>` : ""}${context.stale ? '<div class="pms-alert">저장 이후 구성이나 프로세스가 변경되어 기존 가격계산식을 적용할 수 없습니다. 기존 계산식을 삭제한 뒤 프로세스 등록을 확인하고 새 계산식을 등록해 주세요.</div>' : ""}</div></section>
        <section class="pms-panel"><header class="pms-panel-title"><div><span class="pm-price-step">1</span><strong>가격계산에 사용할 그룹과 옵션</strong></div></header><div class="pms-panel-body"><p class="pms-help">숫자 입력은 프로세스에 등록한 <strong>입력묶음 단위</strong>로 표시합니다. 같은 그룹의 다른 묶음은 별도의 항으로 추가할 수 있습니다. 선택형 옵션을 조건으로 사용하려면 항의 ‘추가 적용 조건’을 지정하세요.</p><div class="pm-price-groups" data-price-groups></div></div></section>
        <section class="pms-panel"><header class="pms-panel-title"><div><span class="pm-price-step">2</span><strong>가격을 구성하는 항</strong></div><button type="button" class="primary" data-price-add ${context.ready && pricingGroups().length ? "" : "disabled"}>＋ 항 생성</button></header><div class="pms-panel-body"><div data-price-terms></div></div></section>
        <section class="pms-panel pm-price-formula-panel"><header class="pms-panel-title"><div><span class="pm-price-step">3</span><strong>최종가격 결과식</strong></div></header><div class="pms-panel-body"><div class="pm-price-formula" data-price-formula></div><p class="pms-help">현재 분기에서 표시·허용된 입력만 계산합니다. 숨겨진 질문이나 다른 입력묶음의 항은 0원으로 제외합니다. 같은 입력을 여러 항에서 사용하면 각 항의 금액이 모두 더해집니다.</p></div></section>
        <section class="pms-panel"><header class="pms-panel-title"><div><span class="pm-price-step">4</span><strong>실제 프로세스와 가격계산 테스트</strong></div><button type="button" data-price-reset ${context.ready ? "" : "disabled"}>테스트 초기화</button></header><div class="pms-panel-body"><p class="pms-help">가격에 영향을 주지 않는 선택 질문도 함께 확인합니다. 앞의 답변을 바꾸면 뒤의 분기·입력묶음·입력 제한을 다시 적용합니다. 테스트 답변과 항별 계산 결과를 확인한 뒤 저장해 주세요. 저장 시 가능한 모든 분기와 기준표 범위를 다시 검증합니다.</p><div data-price-test></div><div data-price-test-errors role="alert"></div><div class="pms-actions pm-price-test-actions"><button type="button" class="primary" data-price-evaluate ${context.ready ? "" : "disabled"}>현재 입력으로 가격계산</button><span data-price-test-status role="status"></span></div><div data-price-result aria-live="polite"></div></div></section>
        <div class="pms-actions pm-price-save-bar"><span data-price-save-status role="status">${context.registered ? "등록된 가격계산식을 불러왔습니다." : "저장하기 전까지 계산식은 등록되지 않습니다."}</span><button type="button" data-price-copy ${!context.registered && context.ready ? "" : "disabled"}>가격계산 불러오기</button>${context.registered ? '<button type="button" data-price-delete class="danger">등록 계산식 삭제</button>' : ""}<button type="button" data-price-save class="primary" ${context.ready ? "" : "disabled"}>테스트 확인 후 가격계산식 저장</button></div></div>`;
      renderGroups(); renderTerms(); updateFormula();
      S.$("[data-price-add]", root).onclick = () => { if (definition.terms.length >= 100) return S.toast("가격 항은 최대 100개까지 등록할 수 있습니다."); editTerm(); };
      S.$("[data-price-evaluate]", root).onclick = (event) => S.run(event.currentTarget, () => calculate());
      S.$("[data-price-reset]", root).onclick = (event) => S.run(event.currentTarget, async () => { cancelTest(); answers = {}; evaluation = null; result = null; await evaluateProcess(true); clearResult("새 답변을 입력하고 계산해 주세요."); });
      S.$("[data-price-copy]", root).onclick = (event) => S.run(event.currentTarget, async () => { if (S.dirty && !await S.confirm("작성 중인 계산식 대신 불러온 계산식을 표시합니다. 계속하시겠습니까?")) return; await S.openPricingCopy(product, load); });
      S.$("[data-price-delete]", root)?.addEventListener("click", (event) => S.run(event.currentTarget, async () => {
        if (!await S.confirm("등록된 가격계산식을 삭제하시겠습니까? 실 제품 가격계산에서도 이 계산식을 더 이상 사용할 수 없습니다.")) return;
        await api(product.id, "?version=" + context.version, "DELETE"); await load(); S.toast("가격계산식을 삭제했습니다.");
      }));
      S.$("[data-price-save]", root).onclick = (event) => S.run(event.currentTarget, async () => {
        if (!definition.terms.length) throw Error("가격을 구성하는 항을 하나 이상 추가해 주세요.");
        if (!await calculate()) throw Error("실제 입력값과 항별 계산 오류를 수정한 뒤 저장해 주세요.");
        const saved = S.copy(definition); saved.schemaFingerprint = context.schemaFingerprint; saved.testAnswers = S.copy(answers);
        await api(product.id, "", "PUT", { version: context.version, definition: saved }); await load(); S.toast("가격계산식을 저장했습니다.");
      });
    }
    function renderGroups() {
      const counts = new Map(); definition.terms.forEach((term) => counts.set(term.groupKey, (counts.get(term.groupKey) || 0) + 1));
      S.$("[data-price-groups]", root).innerHTML = context.groups.map((g) => `<article class="pm-price-group"><header><strong>${e(g.label)}</strong>${S.badge(typesFor(g), "")}${S.badge(counts.has(g.groupKey) ? counts.get(g.groupKey) + "개 항에 사용" : "미사용", counts.has(g.groupKey) ? "green" : "")}</header>${!supported(g) ? '<p class="pms-help">가격 항은 숫자 입력형·선택형에서 등록합니다. 이 그룹은 계산에 사용하지 않습니다.</p>' : g.control === "NUMBER" ? `<div class="pm-price-bundles">${g.bundles.map((b) => `<button type="button" data-price-bundle="${e(b.key)}" data-price-group="${e(g.groupKey)}" ${context.ready ? "" : "disabled"}><strong>${e(b.label || b.fields.map(label).join(" · "))}</strong><small>${b.fields.map((f) => e(label(f)) + (f.unit ? " (" + e(f.unit) + ")" : "")).join(" · ")}</small><span>${definition.terms.filter((term) => term.groupKey === g.groupKey && term.bundleKey === b.key).length ? definition.terms.filter((term) => term.groupKey === g.groupKey && term.bundleKey === b.key).length + "개 항에 사용 · " : "미사용 · "}이 묶음으로 항 추가 ＋</span></button>`).join("")}</div>` : `<div class="pm-price-choice-tags">${g.choices.map((c) => S.badge(label(c))).join("")}</div><button type="button" data-price-group="${e(g.groupKey)}" ${context.ready ? "" : "disabled"}>이 그룹으로 항 추가 ＋</button>`}</article>`).join("") || '<div class="pms-empty">가격계산영향 그룹이 없습니다. 그룹·옵션 관리에서 해당 비규격 그룹의 가격계산영향을 설정해 주세요.</div>';
      S.$$("[data-price-group]", root).forEach((b) => { b.onclick = () => { if (definition.terms.length >= 100) return S.toast("가격 항은 최대 100개까지 등록할 수 있습니다."); editTerm(null, b.dataset.priceGroup, b.dataset.priceBundle); }; });
    }
    function typesFor(g) { return g.control === "NUMBER" ? "숫자 입력묶음" : S.controls[g.control] || g.control; }
    function summary(term) {
      const g = group(term.groupKey), b = g?.bundles?.find((b) => b.key === term.bundleKey);
      const selected = (term.fieldKeys || []).map((k) => label(b?.fields.find((f) => f.key === k)) || k);
      if (term.type === "FIXED") return `＋ ${money(term.amount)}${term.choiceKeys?.length ? " · " + term.choiceKeys.map((k) => label(g?.choices?.find((c) => c.key === k)) || k).join(" / ") : ""}`;
      if (term.type === "MULTIPLY") return selected.map((name, i) => `${name} × ${rateText(term.rates?.[term.fieldKeys[i]])}`).join(" ＋ ");
      const x = label(b?.fields.find((f) => f.key === term.xAxis?.fieldKey)) || term.xAxis?.fieldKey;
      const y = label(b?.fields.find((f) => f.key === term.yAxis?.fieldKey)) || term.yAxis?.fieldKey;
      return term.type === "MATRIX_2D" ? `${x} × ${y} 기준표 · ${term.yAxis?.headers?.length || 0}행 × ${term.xAxis?.headers?.length || 0}열` : `${x} 기준표 · ${term.xAxis?.headers?.length || 0}개 기준`;
    }
    function renderTerms() {
      S.$("[data-price-terms]", root).innerHTML = definition.terms.map((term, i) => `<article class="pm-price-term"><div class="pm-price-term-number">${i + 1}</div><div class="pm-price-term-copy"><header><strong>${e(term.name)}</strong>${S.badge(types[term.type])}</header><p>${e(group(term.groupKey)?.label || "삭제된 그룹")} · ${e(summary(term))}</p>${term.conditions?.length ? `<small>추가 적용 조건 ${term.conditions.length}개 · ${term.match === "ANY" ? "하나 이상 일치" : "모두 일치"}</small>` : ""}</div><div class="pms-actions"><button type="button" data-term-move="${i}" data-direction="-1" aria-label="${e(term.name)} 위로" ${context.ready && i ? "" : "disabled"}>↑</button><button type="button" data-term-move="${i}" data-direction="1" aria-label="${e(term.name)} 아래로" ${context.ready && i < definition.terms.length - 1 ? "" : "disabled"}>↓</button><button type="button" data-term-edit="${i}" ${context.ready ? "" : "disabled"}>조회·수정</button><button type="button" data-term-remove="${i}" class="danger" ${context.ready ? "" : "disabled"}>삭제</button></div></article>`).join("") || '<div class="pms-empty">상단의 입력묶음 또는 ‘항 생성’을 눌러 첫 번째 항을 만들어 주세요.</div>';
      S.$$("[data-term-edit]", root).forEach((b) => { b.onclick = () => editTerm(Number(b.dataset.termEdit)); });
      S.$$("[data-term-remove]", root).forEach((b) => { b.onclick = async () => { const index = Number(b.dataset.termRemove); if (await S.confirm("‘" + definition.terms[index].name + "’ 항을 계산식에서 제거하시겠습니까? 저장해야 최종 반영됩니다.")) { definition.terms.splice(index, 1); changed(); renderTerms(); renderGroups(); } }; });
      S.$$("[data-term-move]", root).forEach((b) => { b.onclick = () => { const from = Number(b.dataset.termMove), to = from + Number(b.dataset.direction); definition.terms.splice(to, 0, definition.terms.splice(from, 1)[0]); changed(); renderTerms(); }; });
    }
    function updateFormula() {
      const box = S.$("[data-price-formula]", root); if (!box) return;
      box.innerHTML = '<strong>최종가격</strong><span>＝</span>' + (definition.terms.map((t, i) => `${i ? '<span class="pm-price-plus">＋</span>' : ""}<span class="pm-price-formula-term">${e(t.name)}</span>`).join("") || '<span class="pm-muted">등록한 항의 합계</span>');
    }
    function clearResult(message) { const box = S.$("[data-price-result]", root); if (box) box.innerHTML = message ? `<p class="pms-help">${e(message)}</p>` : ""; }
    function collectAnswers() {
      S.$$("[data-price-answer]", root).forEach((box) => {
        const state = evaluation?.questions.find((x) => x.question.key === box.dataset.priceAnswer);
        if (!state) return;
        const answer = S.readAnswer(box, state.question, answers[state.question.key]);
        if (state.question.control === "NUMBER") {
          const allowed = new Set((state.question.fields || []).map((f) => f.key));
          answer.fields = Object.fromEntries(S.$$("input[data-answer-field]", box)
            .filter((input) => allowed.has(input.dataset.answerField) && input.value !== "")
            .map((input) => [input.dataset.answerField, input.value]));
        }
        answers[state.question.key] = answer;
      });
    }
    async function evaluateProcess(first = false) {
      if (!first) collectAnswers();
      const serial = ++sequence, draft = S.copy(answers), next = await S.request(`/products/${product.id}/evaluate`, "POST", { answers: draft });
      if (serial !== sequence || !active) return null;
      evaluation = next; answers = mergeTestAnswers(next, draft); renderTest(); return next;
    }
    function renderTest() {
      const box = S.$("[data-price-test]", root); if (!box) return;
      S.$("[data-price-test-errors]", root).innerHTML = "";
      const visible = evaluation.questions.filter((s) => s.visible && !s.question.fixed);
      box.innerHTML = `<div class="pm-price-test-grid">${visible.map((state) => {
        const q = { ...state.question, choices: (state.question.choices || []).filter((c) => state.allowed.includes(c.key)) }, g = group(q.key);
        return `<article class="pm-price-test-question"><header><strong>${e(q.question || label(q))}</strong>${g ? S.badge("가격 영향", "blue") : S.badge("분기·사양")}${state.required ? S.badge("필수") : ""}</header>${q.guide?.trim() ? `<p class="pms-help pm-preserve-lines">${e(q.guide)}</p>` : ""}<div data-price-answer="${e(q.key)}">${S.inputAnswer(q, answers[q.key] || state.answer, false, assetMap)}</div>${state.errors?.length ? `<div class="pm-price-input-errors">${state.errors.map((text) => `<p>${e(text)}</p>`).join("")}</div>` : ""}${(state.notices || []).map((text) => `<p class="pms-help">${e(text)}</p>`).join("")}</article>`;
      }).join("") || '<p class="pms-help">입력할 질문이 없습니다. 고정된 사양으로 계산됩니다.</p>'}</div>`;
      S.$("[data-price-test-status]", root).textContent = evaluation.complete ? "현재 프로세스 입력 검증 완료" : "필수 답변과 입력 제한을 확인해 주세요.";
      S.$$("[data-price-answer]", box).forEach((inputBox) => {
        const q = evaluation.questions.find((s) => s.question.key === inputBox.dataset.priceAnswer).question;
        const update = () => {
          clearTimeout(testTimer); result = null; clearResult("입력값을 변경했습니다. 가격계산을 다시 눌러 주세요.");
          // Preserve every field on screen before applying a changed upstream choice.
          collectAnswers(); const stamp = ++sequence;
          testTimer = setTimeout(async () => {
            const focused = document.activeElement, fieldKey = focused?.dataset.answerField, choiceKey = focused?.dataset.answerChoice, questionKey = focused?.closest("[data-price-answer]")?.dataset.priceAnswer;
            const selection = focused?.selectionStart;
            try {
              const draft = S.copy(answers), next = await S.request(`/products/${product.id}/evaluate`, "POST", { answers: draft });
              if (stamp !== sequence || !active) return;
              evaluation = next; answers = mergeTestAnswers(next, draft); renderTest();
              if (fieldKey && questionKey) { const scope = S.$$("[data-price-answer]", root).find((x) => x.dataset.priceAnswer === questionKey); const target = scope && S.$$("[data-answer-field]", scope).find((x) => x.dataset.answerField === fieldKey); target?.focus({ preventScroll: true }); if (selection != null && target?.type === "text") target.setSelectionRange(selection, selection); }
              else if (choiceKey && questionKey) { const scope = S.$$("[data-price-answer]", root).find((x) => x.dataset.priceAnswer === questionKey); const target = scope && S.$$("[data-answer-choice]", scope).find((x) => x.dataset.answerChoice === choiceKey); target?.focus({ preventScroll: true }); }
            } catch (error) { if (stamp === sequence) S.$("[data-price-test-errors]", root).innerHTML = `<div class="pms-alert">${e(error.message)}</div>`; }
          }, 350);
        };
        inputBox.addEventListener("input", update); inputBox.addEventListener("change", update);
        S.answerFileEvents(inputBox, q, S.copy(answers[q.key] || {}), assetMap, { onChange: (a) => { answers[q.key] = S.copy(a); }, onError: (error) => S.toast(error.message), isCurrent: () => active && inputBox.isConnected });
      });
      S.bindMedia?.(box);
    }
    async function calculate() {
      cancelTest(); collectAnswers(); S.$("[data-price-test-errors]", root).innerHTML = "";
      const serial = ++sequence, draft = S.copy(answers);
      const next = await api(product.id, "/evaluate", "POST", { definition: { ...definition, schemaFingerprint: context.schemaFingerprint }, answers: draft });
      if (serial !== sequence || !active) return false;
      result = next; if (next.evaluation) { evaluation = next.evaluation; answers = mergeTestAnswers(evaluation, draft); renderTest(); }
      definition.testAnswers = S.copy(answers);
      S.$("[data-price-result]", root).innerHTML = `<section class="pm-price-total ${next.complete ? "is-complete" : ""}"><span>${next.complete ? "테스트 최종가격" : "계산을 완료할 수 없습니다."}</span><strong>${next.complete ? money(next.total) : "입력·기준표 확인 필요"}</strong></section>${(next.errors || []).length ? `<div class="pms-alert">${next.errors.map((text) => `<p>${e(text)}</p>`).join("")}</div>` : ""}<div class="pm-price-breakdown">${(next.terms || []).map((term) => `<article><div><strong>${e(term.name)}</strong><small>${e(term.explanation)}</small></div><span class="${term.applied ? "" : "pm-muted"}">${term.applied ? money(term.amount) : "적용 제외 · 0원"}</span></article>`).join("")}</div>`;
      return !!next.complete;
    }

    function editTerm(index = null, selectedGroup = "", selectedBundle = "") {
      let term = index == null ? { key: key(), name: "", groupKey: selectedGroup || pricingGroups()[0]?.groupKey || "", bundleKey: selectedBundle || null, fieldKeys: [], type: "FIXED", amount: 0, rates: {}, xAxis: null, yAxis: null, values: [], conditions: [], match: "ALL", choiceKeys: [] } : S.copy(definition.terms[index]);
      let stage = index == null || !supported(group(term.groupKey)) || (group(term.groupKey)?.control === "NUMBER" && !group(term.groupKey)?.bundles?.some((b) => b.key === term.bundleKey)) ? 0 : 1, importing = false, conditionDraft = null;
      const d = S.dialog(index == null ? "가격계산 항 생성" : "가격계산 항 조회·수정", "", { foot: '<span data-term-footer-status role="status"></span><button type="button" data-term-prev>이전</button><button type="button" data-term-next class="primary">다음 · 계산 방식 설정</button><button type="button" data-term-commit class="primary" hidden>항 반영</button>' });
      d.classList.add("pm-price-editor"); const body = S.$(".pms-dialog-body", d);
      const g = () => group(term.groupKey), bundle = () => g()?.bundles?.find((b) => b.key === term.bundleKey), fields = () => bundle()?.fields || [];
      function selectBundle() {
        if (g()?.control !== "NUMBER") { term.bundleKey = null; term.fieldKeys = []; term.type = "FIXED"; return; }
        if (!bundle()) term.bundleKey = g().bundles[0]?.key || "";
        term.fieldKeys = fields().map((f) => f.key);
      }
      if (index == null) { selectBundle(); term.name = (g()?.label || "가격") + " 항"; }
      function resetPrice(type) {
        term.type = type; term.amount = 0; term.rates = Object.fromEntries(fields().map((f) => [f.key, 0]));
        term.xAxis = { fieldKey: term.fieldKeys[0] || "", operator: "LE", headers: [null] };
        term.yAxis = type === "MATRIX_2D" ? { fieldKey: term.fieldKeys[1] || "", operator: "LE", headers: [null] } : null;
        term.values = [[null]];
      }
      function hasPriceData() {
        if (term.type === "FIXED") return term.amount != null && compareDecimal(term.amount, 0) !== 0;
        if (term.type === "MULTIPLY") return Object.values(term.rates || {}).some((value) => value != null && compareDecimal(value, 0) !== 0);
        return (term.values || []).some((row) => row.some((value) => value != null)) || [term.xAxis, term.yAxis].some((axis) => axis?.headers?.some((value) => value != null));
      }
      function draw() {
        const isNumber = g()?.control === "NUMBER";
        body.innerHTML = `<ol class="pm-stepper"><li class="${stage === 0 ? "active" : ""}">1. 그룹·입력묶음</li><li class="${stage === 1 ? "active" : ""}">2. 계산 방식·금액</li><li class="${stage === 2 ? "active" : ""}">3. 적용 조건·확인</li></ol><div data-term-errors role="alert"></div>${stage === 0 ? sourceHtml() : stage === 1 ? priceHtml(isNumber) : conditionsHtml()}`;
        S.$("[data-term-prev]", d).hidden = stage === 0;
        S.$("[data-term-next]", d).hidden = stage === 2; S.$("[data-term-next]", d).textContent = stage === 0 ? "다음 · 계산 방식 설정" : "다음 · 적용 조건 확인";
        S.$("[data-term-commit]", d).hidden = stage !== 2;
        S.$("[data-term-footer-status]", d).textContent = "항 반영 후 화면 아래의 저장 버튼으로 최종 등록합니다.";
        bindEditor();
      }
      function sourceHtml() {
        return `<div class="pms-form-grid two">${field("항 이름", 'data-term-name type="text" maxlength="120"', term.name)}<label><span>가격 영향 그룹</span><select data-term-group>${!supported(g()) ? '<option value="" disabled selected>계산 가능한 그룹을 다시 선택해 주세요.</option>' : ""}${pricingGroups().map((x) => opt(x.groupKey, x.label, term.groupKey)).join("")}</select></label></div>${g()?.control === "NUMBER" ? `<h3 class="pm-price-editor-subtitle">가격계산에 사용할 입력묶음</h3><p class="pms-help">한 항은 하나의 입력묶음만 사용합니다. 예: W·D 묶음은 함께 선택하고, H 독립묶음은 별도의 항으로 등록합니다. 단독묶음은 1차원, 두 항목 묶음은 2차원 기준표를 사용할 수 있습니다. 세 항목 이상의 묶음은 고정 금액·곱셈 계산을 사용할 수 있습니다.</p><div class="pm-price-source-bundles">${g().bundles.map((b) => `<label class="pm-price-bundle-choice"><input type="radio" name="term-bundle" value="${e(b.key)}" ${b.key === term.bundleKey ? "checked" : ""}><span><strong>${e(b.label || b.fields.map(label).join(" · "))}</strong><small>${b.fields.map((f) => e(label(f)) + (f.unit ? " (" + e(f.unit) + ")" : "")).join(" · ")}</small></span></label>`).join("")}</div>` : `<p class="pms-help">선택형 그룹의 고정 금액은 답변이 선택된 경우에만 적용됩니다. 다음 단계에서 특정 옵션을 지정할 수 있습니다.</p>`}`;
      }
      function priceHtml(isNumber) {
        const allowed = isNumber ? ["FIXED", "MULTIPLY", ...(fields().length === 1 ? ["MATRIX_1D"] : []), ...(fields().length === 2 ? ["MATRIX_2D"] : [])] : ["FIXED"];
        return `<div class="pm-price-source-summary"><strong>${e(g()?.label)}</strong><span>${e(fields().map(label).join(" · ") || "선택형 옵션")}</span><button type="button" data-source-edit>그룹·묶음 변경</button></div>${field("항 이름", 'data-term-name type="text" maxlength="120"', term.name)}<div class="pm-price-type-grid">${allowed.map((type) => `<label class="pm-price-type-choice ${type === term.type ? "selected" : ""}"><input type="radio" name="term-type" value="${type}" ${type === term.type ? "checked" : ""}><strong>${e(types[type])}</strong><small>${{ FIXED: "실제 입력값에 관계없이 한 번 더할 금액", MULTIPLY: "각 입력값에 단가를 곱한 뒤 더하기", MATRIX_1D: "숫자 한 개의 기준 구간에서 금액 찾기", MATRIX_2D: "숫자 두 개의 X·Y 구간에서 금액 찾기" }[type]}</small></label>`).join("")}</div>${term.type === "FIXED" ? `<div class="pms-form-grid two">${field("더할 고정 금액 (원)", 'data-term-amount type="number" step="0.01"', term.amount, 'min="0"')}</div>${!isNumber ? `<fieldset class="pm-price-choice-scope"><legend>적용할 선택 옵션</legend><p class="pms-help">아무것도 지정하지 않으면 이 질문에 어떤 옵션이든 선택되었을 때 한 번 적용합니다. 여러 옵션이 동시에 선택되어도 이 항은 한 번만 더해집니다.</p><div class="pms-check-grid">${g().choices.map((c) => `<label class="pms-check"><input type="checkbox" data-term-choice value="${e(c.key)}" ${(term.choiceKeys || []).includes(c.key) ? "checked" : ""}><span>${e(label(c))}</span></label>`).join("")}</div></fieldset>` : '<p class="pms-help">이 입력묶음이 현재 분기에 있고 허용된 경우에만 고정 금액을 더합니다.</p>'}` : term.type === "MULTIPLY" ? `<p class="pms-help">${e(fields().map((f) => label(f) + " × 단가").join(" ＋ "))} 형태로 계산합니다. 제외할 입력은 단가를 0으로 두세요.</p><div class="pms-form-grid two">${fields().map((f) => field(label(f) + " 1" + (f.unit || "단위") + "당 단가 (원)", `data-term-rate="${e(f.key)}" type="number" min="0" step="0.000001"`, term.rates?.[f.key] ?? 0)).join("")}</div>` : matrixHtml()}`;
      }
      function matrixHtml() {
        const two = term.type === "MATRIX_2D", x = term.xAxis, y = term.yAxis;
        return `<div class="pms-form-grid ${two ? "four" : "two"}"><label><span>X축 (가로) 입력 항목</span><select data-axis-field="x">${fields().map((f) => opt(f.key, label(f), x?.fieldKey)).join("")}</select></label><label><span>X축 기준의 의미</span><select data-axis-operator="x">${opt("LE", "기준값 이하 (≤)", x?.operator)}${opt("LT", "기준값 미만 (<)", x?.operator)}</select></label>${two ? `<label><span>Y축 (세로) 입력 항목</span><select data-axis-field="y">${fields().map((f) => opt(f.key, label(f), y?.fieldKey)).join("")}</select></label><label><span>Y축 기준의 의미</span><select data-axis-operator="y">${opt("LE", "기준값 이하 (≤)", y?.operator)}${opt("LT", "기준값 미만 (<)", y?.operator)}</select></label>` : ""}</div><p class="pms-help">기준값은 작은 값부터 순서대로 입력하세요. <strong>입력값을 포함하는 첫 번째 기준</strong>의 가격을 사용합니다. 예: 기준 500·600에서 ‘이하’는 500 → 첫 구간, 501 → 두 번째 구간입니다. ‘미만’은 500 → 두 번째 구간입니다. 마지막 기준 밖의 값은 계산 오류로 표시합니다.</p><div class="pms-actions pm-price-matrix-actions"><a class="pms-button" href="${S.base}/pricing/excel/template?dimensions=${two ? 2 : 1}&rows=${y?.headers?.length || 1}&columns=${x?.headers?.length || 1}">엑셀 입력 양식</a><button type="button" data-matrix-import>금액만 엑셀 업로드</button><input type="file" data-matrix-file hidden accept=".xlsx"><button type="button" data-matrix-export>현재 기준표 엑셀 다운로드</button><button type="button" data-matrix-column>＋ X 기준 추가</button>${two ? '<button type="button" data-matrix-row>＋ Y 기준 추가</button>' : ""}</div><p class="pms-help">엑셀에는 <strong>금액 데이터만</strong> 입력합니다. 업로드로 행·열 수가 바뀌면 기준값을 비웁니다. 업로드 후 아래 표에서 X${two ? "·Y" : ""} 기준을 직접 입력해 주세요. 금액 0원도 반드시 ‘0’으로 입력하세요.</p><div class="pm-price-matrix-wrap" tabindex="0" aria-label="가로로 스크롤할 수 있는 가격 기준표">${two ? table2d() : table1d()}</div><div data-matrix-import-status role="status"></div>`;
      }
      function headerInput(axis, i, value) { return `<input type="number" min="0" step="any" data-matrix-header="${axis}" data-index="${i}" value="${e(value)}" placeholder="기준값" aria-label="${axis.toUpperCase()}축 ${i + 1}번째 기준값">`; }
      function priceInput(row, col, value) { return `<input type="number" min="0" step="0.01" data-matrix-price="${row}:${col}" value="${e(value)}" placeholder="금액" aria-label="${row + 1}행 ${col + 1}열 금액">`; }
      function table1d() {
        return `<table class="pm-price-matrix"><thead><tr><th scope="col">X 기준 ${term.xAxis.operator === "LT" ? "미만" : "이하"}</th>${term.xAxis.headers.map((v, i) => `<th scope="col">${headerInput("x", i, v)}<button type="button" data-matrix-del-col="${i}" aria-label="X ${i + 1}번째 기준 삭제" ${term.xAxis.headers.length === 1 ? "disabled" : ""}>삭제</button></th>`).join("")}</tr></thead><tbody><tr><th scope="row">금액 (원)</th>${term.xAxis.headers.map((_, i) => `<td>${priceInput(0, i, term.values[0]?.[i])}</td>`).join("")}</tr></tbody></table>`;
      }
      function table2d() {
        return `<table class="pm-price-matrix"><thead><tr><th scope="col">Y↓ / X→<small>${term.yAxis.operator === "LT" ? "미만" : "이하"} / ${term.xAxis.operator === "LT" ? "미만" : "이하"}</small></th>${term.xAxis.headers.map((v, i) => `<th scope="col">${headerInput("x", i, v)}<button type="button" data-matrix-del-col="${i}" aria-label="X ${i + 1}번째 기준 삭제" ${term.xAxis.headers.length === 1 ? "disabled" : ""}>열 삭제</button></th>`).join("")}</tr></thead><tbody>${term.yAxis.headers.map((v, r) => `<tr><th scope="row">${headerInput("y", r, v)}<button type="button" data-matrix-del-row="${r}" aria-label="Y ${r + 1}번째 기준 삭제" ${term.yAxis.headers.length === 1 ? "disabled" : ""}>행 삭제</button></th>${term.xAxis.headers.map((_, c) => `<td>${priceInput(r, c, term.values[r]?.[c])}</td>`).join("")}</tr>`).join("")}</tbody></table>`;
      }
      function conditionsHtml() {
        return `<div class="pm-price-review"><h3>${e(term.name)}</h3><p>${e(g()?.label)} · ${e(summary(term))}</p></div><h3 class="pm-price-editor-subtitle">추가 적용 조건 (선택 사항)</h3><p class="pms-help">이 항의 입력묶음이 실제 분기에 표시되고 답변이 유효해야 계산됩니다. 아래 조건은 그 위에 추가로 적용합니다. 예: ‘세면대 종류 = A’일 때 세면대 수량 × A 단가를 적용합니다.</p><div class="pms-form-grid two"><label><span>추가 조건 적용 방식</span><select data-term-match>${opt("ALL", "모든 조건이 일치 (AND)", term.match)}${opt("ANY", "하나 이상 일치 (OR)", term.match)}</select></label></div><div class="pm-price-conditions">${(term.conditions || []).map((c, i) => `<article><span>${e(conditionText(c))}</span><div class="pms-actions"><button type="button" data-condition-edit="${i}">수정</button><button type="button" data-condition-delete="${i}">삭제</button></div></article>`).join("") || '<p class="pms-help">추가 조건 없음 · 해당 입력묶음의 현재 답변에 따라 적용합니다.</p>'}</div><button type="button" data-condition-add>＋ 적용 조건 추가</button><div data-condition-editor></div><div class="pms-help pm-price-review-note">모든 항의 합이 최종가격입니다. 같은 입력에 대한 항이 이미 있다면 의도한 중복 가산인지 확인해 주세요. 항 반영 후 아래 실제 프로세스 테스트를 완료해야 저장할 수 있습니다.</div>`;
      }
      function conditionText(c) {
        const q = process().questions.find((q) => q.key === c.groupKey), f = q?.fields?.find((f) => f.key === c.fieldKey);
        if (["PRESENT", "ABSENT"].includes(c.operator)) return `${label(q)}${f ? " · " + label(f) : ""}: ${c.operator === "PRESENT" ? "답변 있음" : "답변 없음"}`;
        if (q && S.isChoice(q.control)) return `${label(q)}: ${(c.choiceKeys || []).map((k) => label(q.choices.find((x) => x.key === k)) || k).join(" / ")} ${c.operator === "NE" ? "제외" : "선택"}`;
        if (c.operator === "RANGE") return `${label(q)} · ${label(f)}: ${c.lower} ${c.lowerInclusive ? "이상" : "초과"} ~ ${c.upper} ${c.upperInclusive ? "이하" : "미만"}`;
        return `${label(q)} · ${label(f)}: ${c.lower ?? c.text ?? ""} ${{ EQ: "일치", NE: "불일치", LE: "이하", LT: "미만", GE: "이상", GT: "초과" }[c.operator] || c.operator}`;
      }
      function bindEditor() {
        S.$("[data-term-name]", body)?.addEventListener("input", (event) => { term.name = event.target.value; });
        S.$("[data-term-group]", body)?.addEventListener("change", async (event) => { const next = event.target.value; if (hasPriceData() && !await S.confirm("그룹을 변경하면 현재 항의 기준표·금액을 초기화합니다. 계속하시겠습니까?")) { draw(); return; } term.groupKey = next; term.bundleKey = ""; selectBundle(); resetPrice("FIXED"); term.choiceKeys = []; draw(); });
        S.$$('[name="term-bundle"]', body).forEach((input) => { input.onchange = async () => { const next = input.value; if (hasPriceData() && !await S.confirm("입력묶음을 변경하면 현재 항의 기준표·금액을 초기화합니다. 계속하시겠습니까?")) { draw(); return; } term.bundleKey = next; selectBundle(); resetPrice("FIXED"); draw(); }; });
        S.$("[data-source-edit]", body)?.addEventListener("click", () => { stage = 0; draw(); });
        S.$$('[name="term-type"]', body).forEach((input) => { input.onchange = async () => { if (hasPriceData() && !await S.confirm("계산 방식을 변경하면 현재 기준표·금액을 새 방식으로 초기화합니다. 계속하시겠습니까?")) { draw(); return; } resetPrice(input.value); draw(); }; });
        S.$("[data-term-amount]", body)?.addEventListener("input", (event) => { term.amount = numeric(event.target.value); });
        S.$$("[data-term-rate]", body).forEach((input) => { input.oninput = () => { term.rates ??= {}; term.rates[input.dataset.termRate] = numeric(input.value); }; });
        S.$$("[data-term-choice]", body).forEach((input) => { input.onchange = () => { term.choiceKeys = S.$$("[data-term-choice]:checked", body).map((x) => x.value); }; });
        S.$$("[data-axis-field]", body).forEach((input) => { input.onchange = () => { const axis = input.dataset.axisField === "x" ? "xAxis" : "yAxis", other = axis === "xAxis" ? "yAxis" : "xAxis"; const old = term[axis].fieldKey; term[axis].fieldKey = input.value; if (term[other]?.fieldKey === input.value) term[other].fieldKey = old; draw(); }; });
        S.$$("[data-axis-operator]", body).forEach((input) => { input.onchange = () => { term[input.dataset.axisOperator === "x" ? "xAxis" : "yAxis"].operator = input.value; draw(); }; });
        S.$$("[data-matrix-header]", body).forEach((input) => { input.oninput = () => { term[input.dataset.matrixHeader === "x" ? "xAxis" : "yAxis"].headers[Number(input.dataset.index)] = numeric(input.value); }; });
        S.$$("[data-matrix-price]", body).forEach((input) => { input.oninput = () => { const [r, c] = input.dataset.matrixPrice.split(":").map(Number); term.values[r][c] = numeric(input.value); }; });
        S.$("[data-matrix-column]", body)?.addEventListener("click", () => { if (term.xAxis.headers.length >= 200 || (term.xAxis.headers.length + 1) * term.values.length > 20000) return editorError("축은 최대 200개 기준, 전체 표는 최대 20,000개 금액까지 등록할 수 있습니다."); term.xAxis.headers.push(null); term.values.forEach((row) => row.push(null)); draw(); });
        S.$("[data-matrix-row]", body)?.addEventListener("click", () => { if (term.yAxis.headers.length >= 200 || (term.yAxis.headers.length + 1) * term.xAxis.headers.length > 20000) return editorError("축은 최대 200개 기준, 전체 표는 최대 20,000개 금액까지 등록할 수 있습니다."); term.yAxis.headers.push(null); term.values.push(term.xAxis.headers.map(() => null)); draw(); });
        S.$$("[data-matrix-del-col]", body).forEach((b) => { b.onclick = () => { const i = Number(b.dataset.matrixDelCol); term.xAxis.headers.splice(i, 1); term.values.forEach((row) => row.splice(i, 1)); draw(); }; });
        S.$$("[data-matrix-del-row]", body).forEach((b) => { b.onclick = () => { const i = Number(b.dataset.matrixDelRow); term.yAxis.headers.splice(i, 1); term.values.splice(i, 1); draw(); }; });
        S.$("[data-matrix-import]", body)?.addEventListener("click", () => S.$("[data-matrix-file]", body).click());
        S.$("[data-matrix-file]", body)?.addEventListener("change", (event) => S.run(S.$("[data-matrix-import]", body), async () => {
          const file = event.target.files[0]; if (!file) return;
          if (hasPriceData() && !await S.confirm("현재 표의 금액을 엑셀 데이터로 교체합니다. 행·열 수가 바뀌면 축 기준값을 다시 입력해야 합니다. 계속하시겠습니까?")) return;
          importing = true; lockImport(true);
          try {
            const form = new FormData(); form.append("file", file); form.append("dimensions", term.type === "MATRIX_2D" ? "2" : "1");
            const data = await S.request("/pricing/excel/import", "POST", form), prices = data.amounts;
            if (!d.open) return;
            if (term.xAxis.headers.length !== prices[0].length) term.xAxis.headers = prices[0].map(() => null);
            if (term.yAxis && term.yAxis.headers.length !== prices.length) term.yAxis.headers = prices.map(() => null);
            term.values = S.copy(prices); draw(); S.$("[data-matrix-import-status]", body).textContent = `업로드 완료: ${prices.length}행 × ${prices[0].length}열 · 축 기준값을 확인해 주세요.`;
          } finally { importing = false; lockImport(false); }
        }));
        S.$("[data-matrix-export]", body)?.addEventListener("click", (event) => S.run(event.currentTarget, async () => {
          const error = priceErrors(); if (error.length) throw Error(error.join("\n"));
          await downloadExcel({ dimensions: term.type === "MATRIX_2D" ? 2 : 1, title: term.name, xLabel: label(fields().find((f) => f.key === term.xAxis.fieldKey)), yLabel: label(fields().find((f) => f.key === term.yAxis?.fieldKey)), xOperator: term.xAxis.operator, yOperator: term.yAxis?.operator, xHeaders: term.xAxis.headers, yHeaders: term.yAxis?.headers || [], amounts: term.values });
        }));
        S.$("[data-term-match]", body)?.addEventListener("change", (event) => { term.match = event.target.value; });
        S.$("[data-condition-add]", body)?.addEventListener("click", () => conditionEditor());
        S.$$("[data-condition-edit]", body).forEach((b) => { b.onclick = () => conditionEditor(Number(b.dataset.conditionEdit)); });
        S.$$("[data-condition-delete]", body).forEach((b) => { b.onclick = () => { term.conditions.splice(Number(b.dataset.conditionDelete), 1); conditionDraft = null; draw(); }; });
      }
      function lockImport(busy) { S.$$("button,input,select", d).forEach((el) => { if (busy) { el.dataset.priceWasDisabled = String(el.disabled); el.disabled = true; } else if (el.dataset.priceWasDisabled !== undefined) { el.disabled = el.dataset.priceWasDisabled === "true"; delete el.dataset.priceWasDisabled; } }); }
      function editorError(text) { S.$("[data-term-errors]", body).innerHTML = `<div class="pms-alert">${e(text)}</div>`; S.$("[data-term-errors]", body).scrollIntoView({ block: "nearest" }); }
      function sourceErrors() { const errors = []; if (!term.name.trim()) errors.push("항 이름을 입력해 주세요."); if (!supported(g())) errors.push("숫자 입력형·선택형 가격 영향 그룹을 선택해 주세요."); if (g()?.control === "NUMBER" && !bundle()) errors.push("가격계산에 사용할 입력묶음을 선택해 주세요."); return errors; }
      function priceErrors() {
        const errors = [], nonnegative = (v) => v != null && Number.isFinite(Number(v)) && Number(v) >= 0;
        if (term.type === "FIXED" && !nonnegative(term.amount)) errors.push("고정 금액을 0 이상의 숫자로 입력해 주세요.");
        if (term.type === "MULTIPLY" && fields().some((f) => !nonnegative(term.rates?.[f.key]))) errors.push("모든 입력 항목의 단가를 0 이상의 숫자로 입력해 주세요.");
        if (term.type.startsWith("MATRIX")) {
          if ((term.type === "MATRIX_1D" && fields().length !== 1) || (term.type === "MATRIX_2D" && fields().length !== 2)) errors.push("1차원 기준표는 단독 입력묶음, 2차원 기준표는 두 항목의 입력묶음에 등록할 수 있습니다.");
          const two = term.type === "MATRIX_2D";
          if (!fields().some((f) => f.key === term.xAxis?.fieldKey)) errors.push("X축 입력 항목을 선택해 주세요.");
          if (two && (!fields().some((f) => f.key === term.yAxis?.fieldKey) || term.xAxis.fieldKey === term.yAxis.fieldKey)) errors.push("X축과 다른 Y축 입력 항목을 선택해 주세요.");
          for (const [name, axis] of [["X", term.xAxis], ...(two ? [["Y", term.yAxis]] : [])]) { if (!axis?.headers?.length || axis.headers.some((v) => v == null || !Number.isFinite(Number(v)) || compareDecimal(v, 0) < 0)) errors.push(name + "축의 모든 기준값을 0 이상의 숫자로 입력해 주세요."); else if (axis.headers.some((v, i) => i > 0 && compareDecimal(v, axis.headers[i - 1]) <= 0)) errors.push(name + "축 기준값은 중복 없이 작은 값부터 순서대로 입력해 주세요."); }
          const rows = two ? term.yAxis?.headers?.length : 1, cols = term.xAxis?.headers?.length;
          if (term.values?.length !== rows || term.values.some((row) => row.length !== cols || row.some((v) => !nonnegative(v)))) errors.push("표의 모든 금액을 0 이상의 숫자로 입력해 주세요. 무료 구간은 0을 입력하세요.");
        }
        return errors;
      }
      function conditionEditor(conditionIndex = null) {
        if (conditionIndex == null && term.conditions.length >= 20) return editorError("하나의 항에는 적용 조건을 최대 20개 등록할 수 있습니다.");
        const qs = process().questions.filter((q) => ["RADIO", "CHECKBOX", "NUMBER"].includes(q.control));
        if (!qs.length) return editorError("조건에 사용할 선택형·숫자형 질문이 없습니다.");
        conditionDraft = conditionIndex == null ? { groupKey: qs[0].key, fieldKey: null, operator: "EQ", choiceKeys: [], lower: null, upper: null, lowerInclusive: true, upperInclusive: true, text: null } : S.copy(term.conditions[conditionIndex]);
        function drawCondition() {
          const q = qs.find((q) => q.key === conditionDraft.groupKey), number = q?.control === "NUMBER";
          if (number && !(q.fields || []).some((f) => f.key === conditionDraft.fieldKey)) conditionDraft.fieldKey = q.fields?.[0]?.key;
          const presence = ["PRESENT", "ABSENT"].includes(conditionDraft.operator), range = conditionDraft.operator === "RANGE";
          S.$("[data-condition-editor]", body).innerHTML = `<section class="pm-price-condition-editor"><h4>${conditionIndex == null ? "적용 조건 추가" : "적용 조건 수정"}</h4><div class="pms-form-grid two"><label><span>조건의 질문</span><select data-c-question>${qs.map((q) => opt(q.key, label(q), conditionDraft.groupKey)).join("")}</select></label>${number ? `<label><span>조건의 입력 항목</span><select data-c-field>${q.fields.map((f) => opt(f.key, label(f), conditionDraft.fieldKey)).join("")}</select></label>` : ""}<label><span>비교 방식</span><select data-c-operator>${Object.entries(number ? { EQ: "일치", NE: "불일치", LE: "이하", LT: "미만", GE: "이상", GT: "초과", RANGE: "구간", PRESENT: "답변 있음", ABSENT: "답변 없음" } : { EQ: "선택된 보기 포함", NE: "선택된 보기 제외", PRESENT: "답변 있음", ABSENT: "답변 없음" }).map(([v, text]) => opt(v, text, conditionDraft.operator)).join("")}</select></label>${!presence && number ? field(range ? "하한값" : "비교값", 'data-c-lower type="number" step="any"', conditionDraft.lower) : ""}${!presence && range ? field("상한값", 'data-c-upper type="number" step="any"', conditionDraft.upper) : ""}</div>${!presence && range ? `<div class="pms-check-grid"><label class="pms-check"><input type="checkbox" data-c-inclusive="lower" ${conditionDraft.lowerInclusive ? "checked" : ""}>하한 포함 (이상)</label><label class="pms-check"><input type="checkbox" data-c-inclusive="upper" ${conditionDraft.upperInclusive ? "checked" : ""}>상한 포함 (이하)</label></div>` : ""}${!presence && !number ? `<div class="pms-check-grid">${q.choices.map((c) => `<label class="pms-check"><input type="checkbox" data-c-choice value="${e(c.key)}" ${conditionDraft.choiceKeys.includes(c.key) ? "checked" : ""}>${e(label(c))}</label>`).join("")}</div>` : ""}<div data-c-errors role="alert"></div><div class="pms-actions"><button type="button" data-c-cancel>조건 작성 취소</button><button type="button" class="primary" data-c-save>조건 반영</button></div></section>`;
          const area = S.$("[data-condition-editor]", body);
          S.$("[data-c-question]", area).onchange = (event) => { conditionDraft.groupKey = event.target.value; conditionDraft.fieldKey = null; conditionDraft.choiceKeys = []; conditionDraft.operator = "EQ"; drawCondition(); };
          S.$("[data-c-field]", area)?.addEventListener("change", (event) => { conditionDraft.fieldKey = event.target.value; });
          S.$("[data-c-operator]", area).onchange = (event) => { conditionDraft.operator = event.target.value; drawCondition(); };
          S.$("[data-c-lower]", area)?.addEventListener("input", (event) => { conditionDraft.lower = numeric(event.target.value); });
          S.$("[data-c-upper]", area)?.addEventListener("input", (event) => { conditionDraft.upper = numeric(event.target.value); });
          S.$$("[data-c-inclusive]", area).forEach((input) => { input.onchange = () => { conditionDraft[input.dataset.cInclusive + "Inclusive"] = input.checked; }; });
          S.$$("[data-c-choice]", area).forEach((input) => { input.onchange = () => { conditionDraft.choiceKeys = S.$$("[data-c-choice]:checked", area).map((x) => x.value); }; });
          S.$("[data-c-cancel]", area).onclick = () => { conditionDraft = null; area.innerHTML = ""; };
          S.$("[data-c-save]", area).onclick = () => {
            let message = "";
            if (!presence && number && (conditionDraft.lower == null || !Number.isFinite(Number(conditionDraft.lower)))) message = "조건의 비교값을 입력해 주세요.";
            else if (!presence && range && (conditionDraft.upper == null || !Number.isFinite(Number(conditionDraft.upper)) || compareDecimal(conditionDraft.lower, conditionDraft.upper) > 0 || (compareDecimal(conditionDraft.lower, conditionDraft.upper) === 0 && (!conditionDraft.lowerInclusive || !conditionDraft.upperInclusive)))) message = "상한·하한으로 이루어진 유효한 구간을 입력해 주세요.";
            else if (!presence && !number && !conditionDraft.choiceKeys.length) message = "조건에 사용할 보기를 하나 이상 선택해 주세요.";
            if (message) { S.$("[data-c-errors]", area).innerHTML = `<div class="pms-alert">${e(message)}</div>`; return; }
            if (!number) conditionDraft.fieldKey = null;
            if (conditionIndex == null) term.conditions.push(S.copy(conditionDraft)); else term.conditions[conditionIndex] = S.copy(conditionDraft);
            conditionDraft = null; draw();
          };
        }
        drawCondition(); S.$("[data-condition-editor]", body).scrollIntoView({ block: "nearest", behavior: "smooth" });
      }
      S.$("[data-term-prev]", d).onclick = () => { if (importing) return; stage = Math.max(0, stage - 1); conditionDraft = null; draw(); };
      S.$("[data-term-next]", d).onclick = () => {
        if (importing) return;
        const errors = [...sourceErrors(), ...(stage === 1 ? priceErrors() : [])]; if (errors.length) return editorError(errors.join("\n"));
        stage++; draw();
      };
      S.$("[data-term-commit]", d).onclick = () => {
        if (importing) return;
        if (conditionDraft) return editorError("작성 중인 적용 조건을 반영하거나 취소해 주세요.");
        const errors = [...sourceErrors(), ...priceErrors()]; if (errors.length) return editorError(errors.join("\n"));
        if (index == null) definition.terms.push(S.copy(term)); else definition.terms[index] = S.copy(term);
        d.close(); changed(); renderTerms(); renderGroups(); S.toast("항을 계산식에 반영했습니다. 테스트한 뒤 저장해 주세요.");
      };
      draw();
    }
    await load();
  };

  async function downloadExcel(payload) {
    const headers = { "Content-Type": "application/json" }, token = S.$('meta[name="_csrf"]')?.content, csrfHeader = S.$('meta[name="_csrf_header"]')?.content;
    if (token && csrfHeader) headers[csrfHeader] = token;
    const response = await fetch(S.base + "/pricing/excel/export", { method: "POST", headers, credentials: "same-origin", body: JSON.stringify(payload) });
    if (!response.ok || response.redirected) { let message = "엑셀 다운로드에 실패했습니다."; try { message = (await response.json()).message || message; } catch {} throw Error(message); }
    const url = URL.createObjectURL(await response.blob()), a = document.createElement("a"); a.href = url; a.download = "price-matrix.xlsx"; document.body.append(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
})(window.PMS);
