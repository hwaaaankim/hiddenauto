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
  const types = { FIXED: "고정 금액", MULTIPLY: "입력값 × 단가", MATRIX_1D: "가격표 연동 · 1개 입력", MATRIX_2D: "가격표 연동 · 2개 입력" };
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
  S.descriptions = { ...(S.descriptions || {}), pricing: "고정 금액·입력값 × 단가·사이즈별 가격표를 항으로 만들고 합산합니다. 규격 제품 단가와 별도로 관리합니다." };
  S.pricingGuide = () => S.dialog("비규격 가격계산 사용 순서", `<ol class="pm-price-guide"><li><strong>그룹과 입력묶음을 확인합니다.</strong><p>프로세스를 등록완료한 제품만 계산식을 저장할 수 있습니다. W·D를 함께 계산하려면 프로세스에서 두 필드를 같은 입력묶음으로 등록합니다. 높이 H는 독립묶음으로 등록하면 별도의 항으로 계산할 수 있습니다.</p></li><li><strong>옵션별 일괄 설정으로 계산 방식을 정합니다.</strong><p>그룹을 선택하면 숫자 항목과 선택 옵션이 행별로 표시됩니다. 고정금액과 단가는 바로 입력하고, 가격표는 해당 행에서 설정하세요. 선택형 옵션에 곱셈·가격표를 연결하면 선택 조건이 자동으로 붙습니다. 기존 관련 항은 같은 화면에서 수정합니다. 같은 묶음의 W·D를 선택하면 2차원 가격표, H만 선택하면 1차원 가격표, 문·세면대 수량은 ‘입력값 × 단가’를 선택하세요. 고정 추가비용은 고정 금액으로 등록합니다. 사용하지 않을 그룹이나 묶음은 항을 만들지 않아도 됩니다.</p></li><li><strong>기준표와 추가 적용 조건을 작성합니다.</strong><p>직접 표 생성, 엑셀 셀 붙여넣기, XLSX 업로드 중 편한 방법을 사용하세요. 기준 포함 양식은 X·Y 기준값과 이하·미만까지 가져오며, 기존 금액 전용 양식도 지원합니다. 각 기준의 적용 구간과 입력값 테스트로 500 포함 여부를 확인하세요. 행·열을 추가·삭제하며 표를 조정할 수 있습니다. 일괄 기준 변경은 미리보기로 확인하며, 실행 취소·다시 실행으로 복구할 수 있습니다. X·Y 축 교환은 같은 고객 입력의 가격을 유지합니다. 연결만 변경은 계산 의미가 달라지므로 구분해서 선택하세요. 세면대 A의 수량 단가는 해당 분기 묶음에 연결하세요. 필요한 경우 ‘세면대 종류 = A’와 같은 추가 조건을 지정합니다.</p></li><li><strong>최종 합계식과 실제 입력을 확인하고 저장합니다.</strong><p>아래 테스트에서 사이즈와 문·세면대 종류를 선택하면 프로세스와 동일한 분기와 입력 제한이 적용됩니다. 표시·허용된 항만 계산되며, 가격계산 버튼에서 항별 근거를 볼 수 있습니다. 저장할 때 전체 분기와 기준표 범위를 다시 검증합니다.</p></li></ol><p class="pms-help">예: 높이 기준 500·600·700에 금액 0·20,000·40,000원을 입력하고 ‘이하’를 선택하면 H=500은 0원, H=501~600은 20,000원이 더해집니다. 사용 가능한 최대 높이까지 기준을 작성해 주세요.</p><p class="pms-help">가격계산식이 연결된 프로세스와 가격 항에 사용 중인 그룹의 가격영향 설정은 잠깁니다. 금액과 기준표는 수정할 수 있습니다. 가격계산 불러오기는 그룹·옵션·분기·입력 제한이 모두 같은 제품만 사용할 수 있습니다.</p>`);

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
      root.innerHTML = `<div class="pm-price-page"><section class="pms-panel"><header class="pms-panel-title"><div><strong>${e(product.productName)}</strong><small class="pm-price-product-code">${e(product.catalogCode || product.productCode)}</small></div><div class="pms-actions">${S.badge(context.registered ? "가격계산 등록됨" : "가격계산 미등록", context.registered ? "green" : "")}${context.stale ? S.badge("프로세스 변경 · 삭제 후 재등록 필요", "red") : ""}<a class="pms-button" href="/admin/product-master/non-standard">비규격 목록</a><a class="pms-button" href="/admin/product-master/products/${product.id}/process${product.actualCount || context.registered ? "?mode=view" : ""}">프로세스 ${context.registered ? "조회 (가격식 연결)" : product.actualCount ? "조회" : "확인·수정"}</a></div></header><div class="pms-panel-body"><div class="pms-help" data-price-workflow><strong>전체 등록 흐름 · 현재 화면은 가격계산식 단계입니다</strong><ol><li>사용할 그룹으로 비규격 제품을 생성합니다.</li><li>생성한 제품의 프로세스에서 선택 옵션·숫자 입력 항목·묶음·입력 제한·분기를 등록합니다.</li><li>프로세스에서 ‘검증 후 등록완료’를 실행합니다.</li><li>이 제품의 가격계산식 화면에서 가격계산 영향 그룹의 등록 옵션을 가져와 금액·가격표·조건을 설정하고 테스트 후 저장합니다.</li></ol><p>아래에는 이 제품에 연결되어 있고 ‘가격계산 영향’이 설정된 그룹만 표시합니다. 선택 옵션과 숫자 항목은 이 제품에 저장된 프로세스에서 가져옵니다. 옵션·입력 항목의 추가·변경은 상단의 프로세스 화면에서 진행합니다.</p></div><div class="pm-price-intro"><strong>가로·세로에 따른 금액은 어디서 정하나요?</strong><p>‘옵션별 일괄 설정’에서 항목별 고정금액·곱셈·가격표를 지정하세요. 가로·세로는 ‘숫자 2개를 함께 계산하는 가격표’로 연결합니다. 규격 제품의 단가를 먼저 등록할 필요는 없습니다.</p><div class="pm-price-examples"><span><b>기본금</b>고정 금액 100,000원</span><span><b>수량 추가</b>수량 × 개당 5,000원</span><span><b>사이즈 추가</b>가로·세로 가격표의 교차 금액</span></div><small>예시 금액입니다. 가격표에 기본금이 포함되어 있으면 기본금 항을 또 더하지 않도록 확인하세요.</small></div><p class="pms-help"><strong>이 가격계산식 화면 안의 순서:</strong> ① 등록 옵션 확인 → ② 옵션에 가격 항 연결 → ③ 합계식 확인 → ④ 실제 분기 테스트 → 저장. 사용하지 않는 그룹·입력묶음은 항으로 등록하지 않아도 됩니다.</p>${context.registered && !context.stale ? '<p class="pms-help">가격계산식과 연결된 프로세스는 조회로 열립니다. 가격 금액·기준표는 이 화면에서 계속 수정할 수 있습니다.</p>' : ""}${!context.ready ? `<div class="pms-alert"><strong>가격계산 등록 조건을 확인해 주세요.</strong>${(context.issues || []).map((x) => `<p>${e(x)}</p>`).join("")}</div>` : ""}${context.stale ? '<div class="pms-alert">저장 이후 구성이나 프로세스가 변경되어 기존 가격계산식을 적용할 수 없습니다. 기존 계산식을 삭제한 뒤 프로세스 등록을 확인하고 새 계산식을 등록해 주세요.</div>' : ""}</div></section>
        <section class="pms-panel"><header class="pms-panel-title"><div><span class="pm-price-step">1</span><strong>이 제품의 가격계산 영향 그룹 · 등록된 옵션</strong></div></header><div class="pms-panel-body"><p class="pms-help">제품 프로세스에 저장한 선택 옵션과 숫자 항목을 불러왔습니다. 숫자는 등록한 <strong>입력묶음 단위</strong>로 표시합니다. 같은 그룹의 다른 묶음은 별도의 항으로 추가할 수 있습니다. 선택형 옵션을 조건으로 사용하려면 항의 ‘추가 적용 조건’을 지정하세요.</p><div class="pm-price-groups" data-price-groups></div></div></section>
        <section class="pms-panel"><header class="pms-panel-title"><div><span class="pm-price-step">2</span><strong>가격을 구성하는 항</strong></div><div class="pms-actions"><button type="button" class="primary" data-price-batch ${context.ready && pricingGroups().length ? "" : "disabled"}>옵션별 일괄 설정</button><button type="button" data-price-add ${context.ready && pricingGroups().length ? "" : "disabled"}>＋ 항 개별 생성</button></div></header><div class="pms-panel-body"><div data-price-terms></div></div></section>
        <section class="pms-panel pm-price-formula-panel"><header class="pms-panel-title"><div><span class="pm-price-step">3</span><strong>최종가격 결과식</strong></div></header><div class="pms-panel-body"><div class="pm-price-formula" data-price-formula></div><p class="pms-help">현재 분기에서 표시·허용된 입력만 계산합니다. 숨겨진 질문이나 다른 입력묶음의 항은 0원으로 제외합니다. 같은 입력을 여러 항에서 사용하면 각 항의 금액이 모두 더해집니다.</p></div></section>
        <section class="pms-panel"><header class="pms-panel-title"><div><span class="pm-price-step">4</span><strong>실제 프로세스와 가격계산 테스트</strong></div><button type="button" data-price-reset ${context.ready ? "" : "disabled"}>테스트 초기화</button></header><div class="pms-panel-body"><p class="pms-help">가격에 영향을 주지 않는 선택 질문도 함께 확인합니다. 앞의 답변을 바꾸면 뒤의 분기·입력묶음·입력 제한을 다시 적용합니다. 테스트 답변과 항별 계산 결과를 확인한 뒤 저장해 주세요. 저장 시 가능한 모든 분기와 기준표 범위를 다시 검증합니다.</p><div data-price-test></div><div data-price-test-errors role="alert"></div><div class="pms-actions pm-price-test-actions"><button type="button" class="primary" data-price-evaluate ${context.ready ? "" : "disabled"}>현재 입력으로 가격계산</button><span data-price-test-status role="status"></span></div><div data-price-result aria-live="polite"></div></div></section>
        <div class="pms-actions pm-price-save-bar"><span data-price-save-status role="status">${context.registered ? "등록된 가격계산식을 불러왔습니다." : "저장하기 전까지 계산식은 등록되지 않습니다."}</span><button type="button" data-price-copy ${!context.registered && context.ready ? "" : "disabled"}>가격계산 불러오기</button>${context.registered ? '<button type="button" data-price-delete class="danger">등록 계산식 삭제</button>' : ""}<button type="button" data-price-save class="primary" ${context.ready ? "" : "disabled"}>테스트 확인 후 가격계산식 저장</button></div></div>`;
      renderGroups(); renderTerms(); updateFormula();
      S.$("[data-price-batch]", root).onclick = () => openBatch();
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
    function openBatch(selectedGroup) {
      S.pricingBatch.open({ groups: pricingGroups(), definition, selectedGroup, summary,
        edit: (term, onDraft, scope) => editTerm(null, "", "", term, onDraft, scope),
        apply: terms => { definition.terms = terms; changed(); renderTerms(); renderGroups(); S.toast("일괄 설정을 항에 반영했습니다. 실제 입력으로 테스트한 뒤 저장해 주세요."); }
      });
    }
    function renderGroups() {
      const counts = new Map(); definition.terms.forEach((term) => counts.set(term.groupKey, (counts.get(term.groupKey) || 0) + 1));
      S.$("[data-price-groups]", root).innerHTML = context.groups.map((g) => `<article class="pm-price-group"><header><strong>${e(g.label)}</strong>${S.badge(typesFor(g), "")}${S.badge(counts.has(g.groupKey) ? counts.get(g.groupKey) + "개 항에 사용" : "미사용", counts.has(g.groupKey) ? "green" : "")}</header>${!supported(g) ? '<p class="pms-help">가격 항은 숫자 입력형·선택형에서 등록합니다. 이 그룹은 계산에 사용하지 않습니다.</p>' : g.control === "NUMBER" ? `<div class="pm-price-bundles">${g.bundles.map((b) => `<button type="button" data-price-bundle="${e(b.key)}" data-price-group="${e(g.groupKey)}" ${context.ready ? "" : "disabled"}><strong>${e(b.label || b.fields.map(label).join(" · "))}</strong><small>${b.fields.map((f) => e(label(f)) + (f.unit ? " (" + e(f.unit) + ")" : "")).join(" · ")}</small><span>${definition.terms.filter((term) => term.groupKey === g.groupKey && term.bundleKey === b.key).length ? definition.terms.filter((term) => term.groupKey === g.groupKey && term.bundleKey === b.key).length + "개 항에 사용 · " : "미사용 · "}이 묶음으로 항 추가 ＋</span></button>`).join("")}</div>` : `<div class="pm-price-choice-tags">${g.choices.map((c) => S.badge(label(c))).join("")}</div><button type="button" data-price-group="${e(g.groupKey)}" ${context.ready ? "" : "disabled"}>이 그룹으로 항 추가 ＋</button>`}${supported(g) ? `<button type="button" data-group-batch="${e(g.groupKey)}" ${context.ready ? "" : "disabled"}>이 그룹 옵션별 일괄 설정</button>` : ""}</article>`).join("") || '<div class="pms-empty">이 제품에 연결된 그룹 중 가격계산 영향이 설정된 그룹이 없습니다. 제품 구성에 필요한 그룹이 포함되어 있는지, 그룹·옵션 관리에서 그 비규격 그룹의 가격영향이 켜져 있는지 확인해 주세요.</div>';
      S.$$('[data-group-batch]', root).forEach(b => { b.onclick = () => openBatch(b.dataset.groupBatch); });
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
    function conditionText(c) {
        const q = process().questions.find((q) => q.key === c.groupKey), f = q?.fields?.find((f) => f.key === c.fieldKey);
        if (["PRESENT", "ABSENT"].includes(c.operator)) return `${label(q)}${f ? " · " + label(f) : ""}: ${c.operator === "PRESENT" ? "답변 있음" : "답변 없음"}`;
        if (q && S.isChoice(q.control)) return `${label(q)}: ${(c.choiceKeys || []).map((k) => label(q.choices.find((x) => x.key === k)) || k).join(" / ")} ${c.operator === "NE" ? "제외" : "선택"}`;
        if (c.operator === "RANGE") return `${label(q)} · ${label(f)}: ${c.lower} ${c.lowerInclusive ? "이상" : "초과"} ~ ${c.upper} ${c.upperInclusive ? "이하" : "미만"}`;
        return `${label(q)} · ${label(f)}: ${c.lower ?? c.text ?? ""} ${{ EQ: "일치", NE: "불일치", LE: "이하", LT: "미만", GE: "이상", GT: "초과" }[c.operator] || c.operator}`;
      }
    function renderTerms() {
      S.$("[data-price-terms]", root).innerHTML = definition.terms.map((term, i) => `<article class="pm-price-term"><div class="pm-price-term-number">${i + 1}</div><div class="pm-price-term-copy"><header><strong>${e(term.name)}</strong>${S.badge(types[term.type])}</header><p>${e(group(term.groupKey)?.label || "삭제된 그룹")} · ${e(summary(term))}</p>${term.conditions?.length ? `<small>적용 조건 · ${term.match === "ANY" ? "하나 이상 일치" : "모두 일치"}: ${term.conditions.map(c => e(conditionText(c))).join(term.match === "ANY" ? " 또는 " : " 그리고 ")}</small>` : ""}</div><div class="pms-actions"><button type="button" data-term-move="${i}" data-direction="-1" aria-label="${e(term.name)} 위로" ${context.ready && i ? "" : "disabled"}>↑</button><button type="button" data-term-move="${i}" data-direction="1" aria-label="${e(term.name)} 아래로" ${context.ready && i < definition.terms.length - 1 ? "" : "disabled"}>↓</button><button type="button" data-term-edit="${i}" ${context.ready ? "" : "disabled"}>조회·수정</button><button type="button" data-term-duplicate="${i}" ${context.ready ? "" : "disabled"}>복사해서 새 항</button><button type="button" data-term-remove="${i}" class="danger" ${context.ready ? "" : "disabled"}>삭제</button></div></article>`).join("") || '<div class="pms-empty">상단의 입력묶음 또는 ‘항 생성’을 눌러 첫 번째 항을 만들어 주세요.</div>';
      S.$$("[data-term-edit]", root).forEach((b) => { b.onclick = () => editTerm(Number(b.dataset.termEdit)); });
      S.$$('[data-term-duplicate]', root).forEach(button => { button.onclick = () => {
        if (definition.terms.length >= 100) return S.toast("가격 항은 최대 100개입니다.");
        const draft = S.copy(definition.terms[Number(button.dataset.termDuplicate)]); draft.key = key(); draft.name = (draft.name.slice(0, 112) + " 복사본");
        editTerm(null, "", "", draft);
      }; });
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
        const q = { ...state.question, choices: (state.question.choices || []).filter((c) => state.allowed.includes(c.key)), fields: (state.question.fields || []).filter((f) => state.allowed.includes(f.key)) }, g = group(q.key);
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

    async function testSingleTerm(term) {
      let localAnswers = S.copy(answers), localEvaluation = null, calculation = null, localSequence = 0;
      const dialog = S.dialog("이 항만 입력 테스트 · " + term.name, '<p class="pms-help">입력 항목을 불러오는 중입니다.</p>', { foot: '<button type="button" data-test-term-run class="primary">분기·입력 적용 후 이 항 계산</button><button type="button" data-test-use disabled>테스트값을 메인 화면에 가져오기</button>' });
      const area = S.$('.pms-dialog-body', dialog);
      function collect() {
        S.$$('[data-single-answer]', area).forEach(box => {
          const state = localEvaluation.questions.find(s => s.question.key === box.dataset.singleAnswer), q = state.question;
          const answer = S.readAnswer(box, q, localAnswers[q.key]);
          if (q.control === 'NUMBER') answer.fields = Object.fromEntries(S.$$('input[data-answer-field]', box).filter(i => i.value !== '').map(i => [i.dataset.answerField, i.value]));
          localAnswers[q.key] = answer;
        });
      }
      function render() {
        const result = calculation?.terms?.find(t => t.key === term.key);
        area.innerHTML = `<div class="pm-price-review"><strong>${e(summary(term))}</strong><p>${term.conditions.length ? term.conditions.map(c => e(conditionText(c))).join(term.match === 'ANY' ? ' 또는 ' : ' 그리고 ') : '추가 조건 없음'}</p></div><p class="pms-help">이 항만 계산하며 다른 항의 금액은 합산하지 않습니다. 앞선 답변을 바꾼 후 계산 버튼을 누르면 뒤 질문·입력 제한이 갱신됩니다. 여기서 입력한 테스트값은 가격표나 가격식에 저장되지 않습니다.</p><div class="pm-price-test-grid">${localEvaluation.questions.filter(state => state.visible && !state.question.fixed).map(state => {
          const q = { ...state.question, choices: (state.question.choices || []).filter(c => state.allowed.includes(c.key)), fields: (state.question.fields || []).filter(f => state.allowed.includes(f.key)) };
          return `<article class="pm-price-test-question"><header><strong>${e(q.question || label(q))}</strong>${state.required ? S.badge('필수') : ''}</header><div data-single-answer="${e(q.key)}">${S.inputAnswer(q, localAnswers[q.key] || state.answer, false, assetMap)}</div>${state.errors?.length ? `<div class="pm-price-input-errors">${state.errors.map(x => `<p>${e(x)}</p>`).join('')}</div>` : ''}${(state.notices || []).map(x => `<p class="pms-help">${e(x)}</p>`).join('')}</article>`;
        }).join('')}</div><div data-single-result role="status">${calculation ? `<div class="pm-price-total"><span>${calculation.complete ? result?.applied ? '이 항의 계산 금액' : '이 항은 적용되지 않음' : '입력·조건 확인 필요'}</span><strong>${calculation.complete ? result?.applied ? money(result.amount) : '0원' : '계산 미완료'}</strong></div><p>${e(result?.explanation || '')}</p>${calculation.errors.map(x => `<p class="pms-alert">${e(x)}</p>`).join('')}` : '<p class="pms-help">입력값과 조건을 확인한 뒤 계산해 주세요.</p>'}</div>`;
        S.$('[data-test-use]', dialog).disabled = !calculation?.complete;
        S.$$('[data-single-answer]', area).forEach(box => {
          const q = localEvaluation.questions.find(s => s.question.key === box.dataset.singleAnswer).question;
          const invalidate = () => { localSequence++; calculation = null; S.$('[data-test-use]', dialog).disabled = true; S.$('[data-single-result]', area).textContent = '입력이 변경되었습니다. 다시 계산해 주세요.'; };
          box.addEventListener('input', invalidate); box.addEventListener('change', invalidate);
          S.answerFileEvents(box, q, S.copy(localAnswers[q.key] || {}), assetMap, { onChange: a => { localAnswers[q.key] = S.copy(a); invalidate(); }, onError: error => S.toast(error.message), isCurrent: () => dialog.open && box.isConnected });
        });
        S.bindMedia?.(area);
      }
      S.$('[data-test-term-run]', dialog).onclick = event => S.run(event.currentTarget, async () => {
        if (!localEvaluation) return;
        collect(); const draft = S.copy(localAnswers), serial = ++localSequence;
        const next = await api(product.id, '/evaluate', 'POST', { definition: { schemaVersion: 1, schemaFingerprint: context.schemaFingerprint, terms: [term], testAnswers: {} }, answers: draft });
        if (!dialog.open || serial !== localSequence) return;
        calculation = next; if (next.evaluation) { localEvaluation = next.evaluation; localAnswers = mergeTestAnswers(next.evaluation, draft); } render();
      });
      S.$('[data-test-use]', dialog).onclick = () => { if (!calculation?.complete) return; cancelTest(); answers = S.copy(localAnswers); evaluation = localEvaluation; result = null; renderTest(); clearResult('항 테스트의 입력값을 가져왔습니다. 모든 항을 반영한 뒤 전체 가격을 다시 계산해 주세요.'); dialog.close(); };
      try { localEvaluation = await S.request(`/products/${product.id}/evaluate`, 'POST', { answers: localAnswers }); if (dialog.open) { localAnswers = mergeTestAnswers(localEvaluation, localAnswers); render(); } }
      catch (error) { dialog.close(); throw error; }
    }

    function editTerm(index = null, selectedGroup = "", selectedBundle = "", draft = null, onDraft = null, scope = {}) {
      let term = draft ? S.copy(draft) : index == null ? { key: key(), name: "", groupKey: selectedGroup || pricingGroups()[0]?.groupKey || "", bundleKey: selectedBundle || null, fieldKeys: [], type: "FIXED", amount: 0, rates: {}, xAxis: null, yAxis: null, values: [], conditions: [], match: "ALL", choiceKeys: [] } : S.copy(definition.terms[index]);
      let stage = !draft && index == null || !supported(group(term.groupKey)) || (group(term.groupKey)?.control === "NUMBER" && !group(term.groupKey)?.bundles?.some((b) => b.key === term.bundleKey)) ? 0 : 1, importing = false, conditionDraft = null;
      const d = S.dialog(index == null ? "가격계산 항 생성" : "가격계산 항 조회·수정", "", { foot: '<span data-term-footer-status role="status"></span><button type="button" data-term-prev>이전</button><button type="button" data-term-next class="primary">다음 · 계산 방식 설정</button><button type="button" data-term-commit class="primary" hidden>항 반영</button>' });
      d.classList.add("pm-price-editor"); const body = S.$(".pms-dialog-body", d);
      const g = () => group(term.groupKey), bundle = () => g()?.bundles?.find((b) => b.key === term.bundleKey);
      const allFields = () => bundle()?.fields || [], fields = () => allFields().filter((f) => term.fieldKeys.includes(f.key));
      function selectBundle() {
        if (g()?.control !== "NUMBER") { term.bundleKey = null; term.fieldKeys = []; term.type = "FIXED"; return; }
        if (!bundle()) term.bundleKey = g().bundles[0]?.key || "";
        term.fieldKeys = allFields().map((f) => f.key);
      }
      if (index == null && !draft) { selectBundle(); term.name = (g()?.label || "가격") + " 항"; }
      const tableUi = {}, initialTerm = S.priceTable.snapshot(term);
      const history = S.priceTable.history(() => term, value => { term = value; conditionDraft = null; S.priceTable.resetDraft(tableUi); });
      let focusSequence = 0;
      body.addEventListener("focusin", () => { focusSequence++; });
      const recordInput = event => {
        if (event.target.matches('[data-term-name],[data-term-amount],[data-term-rate],[data-term-choice],[data-matrix-header],[data-matrix-price],[data-bound-operator],[data-term-match]')) { history.record(event.type === 'input' ? 'input-' + focusSequence : null); updateHistory(); }
      };
      body.addEventListener("input", recordInput); body.addEventListener("change", recordInput);
      S.priceTable.guardClose(d, () => initialTerm !== S.priceTable.snapshot(term) || S.priceTable.pending(tableUi) || !!conditionDraft);
      function updateHistory() {
        const undo = S.$('[data-price-undo]', body), redo = S.$('[data-price-redo]', body);
        if (undo) undo.disabled = importing || !history.canUndo(); if (redo) redo.disabled = importing || !history.canRedo();
      }
      function pendingTable() {
        if (!S.priceTable.pending(tableUi)) return false;
        editorError("작성한 일괄 기준값을 먼저 미리보기로 반영하거나 ‘현재 표의 기준으로 되돌리기’를 눌러 주세요."); return true;
      }
      const requiredCondition = c => scope.requiredCondition && c.groupKey === scope.requiredCondition.groupKey && c.operator === "EQ" && c.choiceKeys?.length === 1 && c.choiceKeys[0] === scope.requiredCondition.choiceKeys[0];
      function resetPrice(type) {
        term.type = type; term.amount = 0; term.rates = Object.fromEntries(fields().map((f) => [f.key, 0]));
        term.xAxis = type.startsWith("MATRIX") ? { fieldKey: term.fieldKeys[0] || "", operator: "LE", headers: [null], operators: [null] } : null;
        term.yAxis = type === "MATRIX_2D" ? { fieldKey: term.fieldKeys[1] || "", operator: "LE", headers: [null], operators: [null] } : null;
        term.values = type.startsWith("MATRIX") ? [[null]] : []; S.priceTable.resetDraft(tableUi);
      }
      function hasPriceData() {
        if (term.type === "FIXED") return term.amount != null && compareDecimal(term.amount, 0) !== 0;
        if (term.type === "MULTIPLY") return Object.values(term.rates || {}).some((value) => value != null && compareDecimal(value, 0) !== 0);
        return (term.values || []).some((row) => row.some((value) => value != null)) || [term.xAxis, term.yAxis].some((axis) => axis?.headers?.some((value) => value != null));
      }
      function draw() {
        S.priceTable.remember(body, tableUi);
        history.record();
        const isNumber = g()?.control === "NUMBER";
        body.innerHTML = `<ol class="pm-stepper"><li class="${stage === 0 ? "active" : ""}">1. 등록 항목 연결</li><li class="${stage === 1 ? "active" : ""}">2. 계산 방식·금액</li><li class="${stage === 2 ? "active" : ""}">3. 적용 조건·확인</li></ol><div class="pms-actions pm-price-history"><button type="button" data-price-undo>↶ 실행 취소</button><button type="button" data-price-redo>↷ 다시 실행</button><small>이 창에서의 변경을 되돌립니다. 항 반영 전까지 원본은 유지됩니다.</small></div><div data-term-errors role="alert"></div>${stage === 0 ? sourceHtml() : stage === 1 ? priceHtml(isNumber) : conditionsHtml()}`;
        S.$("[data-term-prev]", d).hidden = stage === 0 || scope.locked && stage === 1;
        S.$("[data-term-next]", d).hidden = stage === 2; S.$("[data-term-next]", d).textContent = stage === 0 ? "다음 · 계산 방식 설정" : "다음 · 적용 조건 확인";
        S.$("[data-term-commit]", d).hidden = stage !== 2;
        S.$("[data-term-footer-status]", d).textContent = "항 반영 후 화면 아래의 저장 버튼으로 최종 등록합니다.";
        bindEditor(); updateHistory();
      }
      function sourceHtml() {
        return `<div class="pms-form-grid two">${field("항 이름 (예: 사이즈 추가금)", 'data-term-name type="text" maxlength="120"', term.name)}<label><span>이 제품의 가격계산 영향 그룹</span><select data-term-group>${!supported(g()) ? '<option value="" disabled selected>그룹을 선택해 주세요.</option>' : ""}${pricingGroups().map((x) => opt(x.groupKey, x.label, term.groupKey)).join("")}</select></label></div>${g()?.control === "NUMBER" ? `<h3 class="pm-price-editor-subtitle">1. 입력묶음 가져오기</h3><p class="pms-help">함께 가격을 계산할 숫자는 같은 입력묶음에 있어야 합니다. 묶음을 고른 뒤 이 항에 사용할 항목을 선택하세요.</p><div class="pm-price-source-bundles">${g().bundles.map((b) => `<label class="pm-price-bundle-choice"><input type="radio" name="term-bundle" value="${e(b.key)}" ${b.key === term.bundleKey ? "checked" : ""}><span><strong>${e(b.label || b.fields.map(label).join(" · "))}</strong><small>${b.fields.map((f) => e(label(f)) + (f.unit ? " (" + e(f.unit) + ")" : "")).join(" · ")}</small></span></label>`).join("")}</div><h3 class="pm-price-editor-subtitle">2. 이 항에서 사용할 숫자 항목</h3><div class="pms-check-grid">${allFields().map((f) => `<label class="pms-check"><input type="checkbox" data-term-field value="${e(f.key)}" ${term.fieldKeys.includes(f.key) ? "checked" : ""}>${e(label(f))}${f.unit ? " (" + e(f.unit) + ")" : ""}</label>`).join("")}</div><p class="pms-help">예: 가로·세로만 선택하면 2차원 가격표로 금액을 찾습니다. 높이는 별도 항을 만들어 곱셈이나 1차원 가격표로 계산할 수 있습니다. 항목마다 방식이 다르면 메인 화면의 ‘옵션별 일괄 설정’에서 한 번에 지정할 수 있습니다.</p>` : `<fieldset class="pm-price-choice-scope"><legend>금액을 적용할 옵션</legend><p class="pms-help">이 개별 항은 선택형 옵션의 고정 추가금을 설정합니다. 옵션별 곱셈·가격표는 메인 화면의 ‘옵션별 일괄 설정’에서 숫자 입력과 연결하세요. 여러 옵션의 고정금액도 일괄 설정할 수 있습니다. 아무것도 지정하지 않으면 어떤 옵션이든 선택했을 때 한 번 적용됩니다.</p><div class="pms-check-grid">${(g()?.choices || []).map((c) => `<label class="pms-check"><input type="checkbox" data-term-choice value="${e(c.key)}" ${(term.choiceKeys || []).includes(c.key) ? "checked" : ""}>${e(label(c))}</label>`).join("")}</div></fieldset>`}`;
      }
      function priceHtml(isNumber) {
        const allowed = isNumber ? ["FIXED", "MULTIPLY", ...(fields().length === 1 ? ["MATRIX_1D"] : []), ...(fields().length === 2 ? ["MATRIX_2D"] : [])] : ["FIXED"];
        return `<div class="pm-price-source-summary"><strong>${e(g()?.label)}</strong><span>${e(fields().map(label).join(" · ") || "선택형 옵션")}</span>${scope.locked ? '<small>연결 항목·방식은 일괄 설정 화면에서 변경합니다.</small>' : '<button type="button" data-source-edit>그룹·묶음 변경</button>'}</div>${field("항 이름", 'data-term-name type="text" maxlength="120"', term.name)}${isNumber ? `<details class="pm-price-limits"><summary>연결한 숫자의 단위·기본 허용 범위 확인</summary><ul>${fields().map(f => `<li><strong>${e(label(f))}</strong> · ${e(f.unit || "단위 없음")} · 최소 ${e(f.min ?? "제한 없음")} / 최대 ${e(f.max ?? "제한 없음")} · 입력 간격 ${e(f.step ?? "자유")}</li>`).join("")}</ul><p class="pms-help">프로세스 분기에 따라 범위가 더 좁아질 수 있습니다. 실제 입력 테스트와 최종 저장 검증에서 분기별 제한을 확인합니다.</p></details>` : ""}${isNumber && fields().length > 2 ? '<p class="pms-help">가격표 연동은 1개 또는 2개의 입력을 사용합니다. 위 ‘그룹·묶음 변경’에서 사용할 항목만 선택하세요.</p>' : ""}<div class="pm-price-type-grid" ${scope.locked ? "hidden" : ""}>${allowed.map((type) => `<label class="pm-price-type-choice ${type === term.type ? "selected" : ""}"><input type="radio" name="term-type" value="${type}" ${type === term.type ? "checked" : ""}><strong>${e(types[type])}</strong><small>${{ FIXED: "실제 입력값에 관계없이 한 번 더할 금액", MULTIPLY: "각 입력값에 단가를 곱한 뒤 더하기", MATRIX_1D: "예: 높이 500 이하 0원, 600 이하 20,000원", MATRIX_2D: "예: 가로·세로를 X·Y축으로 연결해 교차 금액 찾기" }[type]}</small></label>`).join("")}</div>${term.type === "FIXED" ? `<div class="pms-form-grid two">${field("더할 고정 금액 (원)", 'data-term-amount type="number" step="0.01"', term.amount, 'min="0"')}</div>${!isNumber ? `<fieldset class="pm-price-choice-scope"><legend>적용할 선택 옵션</legend><p class="pms-help">아무것도 지정하지 않으면 이 질문에 어떤 옵션이든 선택되었을 때 한 번 적용합니다. 여러 옵션이 동시에 선택되어도 이 항은 한 번만 더해집니다.</p><div class="pms-check-grid">${g().choices.map((c) => `<label class="pms-check"><input type="checkbox" data-term-choice value="${e(c.key)}" ${(term.choiceKeys || []).includes(c.key) ? "checked" : ""}><span>${e(label(c))}</span></label>`).join("")}</div></fieldset>` : '<p class="pms-help">이 입력묶음이 현재 분기에 있고 허용된 경우에만 고정 금액을 더합니다.</p>'}` : term.type === "MULTIPLY" ? `<p class="pms-help">${e(fields().map((f) => label(f) + " × 단가").join(" ＋ "))} 형태로 계산합니다. 각 숫자에 해당 단가를 곱한 뒤 합산하며, 가로 × 세로의 면적 곱셈은 아닙니다. 제외할 입력은 단가를 0으로 두세요.</p><div class="pms-form-grid two">${fields().map((f) => field(label(f) + " 1" + (f.unit || "단위") + "당 단가 (원)", `data-term-rate="${e(f.key)}" type="number" min="0" step="0.000001"`, term.rates?.[f.key] ?? 0)).join("")}</div>` : matrixHtml()}`;
      }
      function matrixHtml() {
        const two = term.type === "MATRIX_2D", x = term.xAxis, y = term.yAxis;
        return `<h3 class="pm-price-editor-subtitle">1. 입력 연결과 기본 경계</h3><div class="pms-form-grid ${two ? "four" : "two"}"><label><span>X축 · 표의 가로 방향</span><select data-axis-field="x">${fields().map((f) => opt(f.key, label(f), x?.fieldKey)).join("")}</select></label><label><span>X축 기본 포함 여부</span><select data-axis-operator="x">${opt("LE", "이하 · 기준값 포함 (500 포함)", x?.operator)}${opt("LT", "미만 · 기준값 제외 (500 제외)", x?.operator)}</select></label>${two ? `<label><span>Y축 · 표의 세로 방향</span><select data-axis-field="y">${fields().map((f) => opt(f.key, label(f), y?.fieldKey)).join("")}</select></label><label><span>Y축 기본 포함 여부</span><select data-axis-operator="y">${opt("LE", "이하 · 기준값 포함 (500 포함)", y?.operator)}${opt("LT", "미만 · 기준값 제외 (500 제외)", y?.operator)}</select></label>` : ""}</div><p class="pms-help"><strong>X·Y는 표의 방향입니다.</strong> 실제 가로·세로·높이 중 어떤 입력을 연결할지는 위에서 선택하세요. 각 기준 아래에서 포함 여부를 개별로 바꿀 수도 있습니다. 축 기본값을 바꾸면 ‘기본값 사용’인 기준만 변경됩니다. 축 교환은 기준·포함 여부·금액표를 함께 바꾸어 같은 고객 입력의 금액을 유지합니다.</p><div class="pm-price-boundary-help"><span><b>500 이하 (≤)</b>500은 현재 칸에 포함</span><span><b>500 미만 (&lt;)</b>500은 다음 구간으로 이동</span><span><b>마지막 기준 밖</b>마지막 금액을 임의 적용하지 않고 오류 안내</span></div>${two ? '<div class="pms-actions"><button type="button" data-axis-swap>X·Y 축 교환 · 금액 관계 유지</button></div>' : ""}<h3 class="pm-price-editor-subtitle">2. 가격표 작성 방법 선택</h3><div class="pms-actions pm-price-methods" role="group" aria-label="가격표 작성 방법"><button type="button" data-price-method="direct">직접 입력</button><button type="button" data-price-method="excel">엑셀 파일</button><button type="button" data-price-method="paste">셀 붙여넣기</button></div><p class="pms-help">편한 방법 하나로 작성하세요. 방법을 바꿔도 작성한 표는 유지됩니다. 작성한 일괄 기준값은 표에 반영하거나 되돌린 후 가져오기를 진행하세요.</p>${S.priceTable.toolsHtml(term, tableUi)}<div class="pm-price-excel-section" data-price-method-panel="excel"><strong>엑셀로 가격표 작성</strong><div class="pms-actions pm-price-matrix-actions"><a class="pms-button" href="${S.base}/pricing/excel/template?includeHeaders=true&dimensions=${two ? 2 : 1}&rows=${y?.headers?.length || 1}&columns=${x?.headers?.length || 1}">기준 포함 엑셀 양식</a><a class="pms-button" href="${S.base}/pricing/excel/template?dimensions=${two ? 2 : 1}&rows=${y?.headers?.length || 1}&columns=${x?.headers?.length || 1}">금액 전용 양식</a><label><span>업로드 방식</span><select data-matrix-import-mode><option value="table">기준값·포함 여부·금액 함께</option><option value="amounts">금액만 (기존 양식)</option></select></label><button type="button" data-matrix-import>엑셀 업로드</button><input type="file" data-matrix-file hidden accept=".xlsx"><button type="button" data-matrix-export>현재 가격표 다운로드</button></div><p class="pms-help">기준 포함은 ‘가격표’ 시트의 첫 행 X·첫 열 Y와 ‘가격표설정’의 포함 여부를 읽습니다. 금액만은 ‘가격데이터’ 시트를 읽으며 크기가 달라지면 기준값을 다시 입력합니다. 수식은 값으로 붙여넣으세요.</p></div><h3 class="pm-price-editor-subtitle">3. 기준값·포함 여부·금액 확인</h3><p class="pms-help">아래 표에서 직접 수정할 수 있습니다. 표가 넓으면 좌우로 스크롤하세요. 무료 구간은 0원을 입력하세요. 빈칸은 미설정입니다. 각 기준 아래의 실제 적용 구간을 확인해 주세요.</p><div class="pms-actions"><button type="button" data-matrix-column>＋ X 기준 추가</button>${two ? '<button type="button" data-matrix-row>＋ Y 기준 추가</button>' : ""}</div><div class="pm-price-matrix-wrap" tabindex="0" aria-label="가로로 스크롤할 수 있는 가격표">${two ? table2d() : table1d()}</div><div data-matrix-import-status role="status"></div>${S.priceTable.previewHtml(term, fields())}`;
      }
      function headerInput(axis, i, value) {
        const a = term[axis === "x" ? "xAxis" : "yAxis"];
        return `<input type="number" min="0" step="any" data-matrix-header="${axis}" data-index="${i}" value="${e(value)}" placeholder="기준값" aria-label="${axis.toUpperCase()}축 ${i + 1}번째 기준값"><select data-bound-operator="${axis}:${i}" aria-label="${axis.toUpperCase()}축 ${i + 1}번째 포함 여부">${opt("", "기본값 사용 (" + (a.operator === "LT" ? "미만" : "이하") + ")", a.operators?.[i] || "")}${opt("LE", "이하 · 이 기준값 포함", a.operators?.[i])}${opt("LT", "미만 · 이 기준값 제외", a.operators?.[i])}</select><small data-bound-range="${axis}:${i}">${e(S.priceTable.range(a, i))}</small>`;
      }
      function priceInput(row, col, value) { return `<input type="number" min="0" step="0.01" data-matrix-price="${row}:${col}" value="${e(value)}" placeholder="금액" aria-label="${row + 1}행 ${col + 1}열 금액">`; }
      function table1d() {
        return `<table class="pm-price-matrix"><thead><tr><th scope="col">X 기준값 / 포함 여부</th>${term.xAxis.headers.map((v, i) => `<th scope="col">${headerInput("x", i, v)}<button type="button" data-matrix-del-col="${i}" aria-label="X ${i + 1}번째 기준 삭제" ${term.xAxis.headers.length === 1 ? "disabled" : ""}>삭제</button></th>`).join("")}</tr></thead><tbody><tr><th scope="row">금액 (원)</th>${term.xAxis.headers.map((_, i) => `<td>${priceInput(0, i, term.values[0]?.[i])}</td>`).join("")}</tr></tbody></table>`;
      }
      function table2d() {
        return `<table class="pm-price-matrix"><thead><tr><th scope="col">Y↓ / X→<small>각 기준의 포함 여부 적용</small></th>${term.xAxis.headers.map((v, i) => `<th scope="col">${headerInput("x", i, v)}<button type="button" data-matrix-del-col="${i}" aria-label="X ${i + 1}번째 기준 삭제" ${term.xAxis.headers.length === 1 ? "disabled" : ""}>열 삭제</button></th>`).join("")}</tr></thead><tbody>${term.yAxis.headers.map((v, r) => `<tr><th scope="row">${headerInput("y", r, v)}<button type="button" data-matrix-del-row="${r}" aria-label="Y ${r + 1}번째 기준 삭제" ${term.yAxis.headers.length === 1 ? "disabled" : ""}>행 삭제</button></th>${term.xAxis.headers.map((_, c) => `<td>${priceInput(r, c, term.values[r]?.[c])}</td>`).join("")}</tr>`).join("")}</tbody></table>`;
      }
      function conditionsHtml() {
        return `<div class="pm-price-review"><h3>${e(term.name)}</h3><p>${e(g()?.label)} · ${e(summary(term))}</p></div><h3 class="pm-price-editor-subtitle">추가 적용 조건 (선택 사항)</h3><p class="pms-help">이 항의 입력묶음이 실제 분기에 표시되고 답변이 유효해야 계산됩니다. 아래 조건은 그 위에 추가로 적용합니다. 예: ‘세면대 종류 = A’일 때 세면대 수량 × A 단가를 적용합니다.</p>${scope.requiredCondition ? '<p class="pms-help"><strong>선택한 옵션 조건은 필수입니다.</strong> 추가 조건도 모두 일치할 때만 적용합니다. 옵션 연결 변경은 일괄 설정 화면에서 진행하세요.</p>' : ''}<div class="pms-form-grid two"><label><span>추가 조건 적용 방식</span><select data-term-match ${scope.requiredCondition ? 'disabled' : ''}>${opt("ALL", "모든 조건이 일치 (AND)", term.match)}${opt("ANY", "하나 이상 일치 (OR)", term.match)}</select></label></div><div class="pm-price-conditions">${(term.conditions || []).map((c, i) => `<article><span>${e(conditionText(c))}</span><div class="pms-actions">${requiredCondition(c) ? '<span class="pms-help">옵션 연결 필수 조건</span>' : `<button type="button" data-condition-edit="${i}">수정</button><button type="button" data-condition-delete="${i}">삭제</button>`}</div></article>`).join("") || '<p class="pms-help">추가 조건 없음 · 해당 입력묶음의 현재 답변에 따라 적용합니다.</p>'}</div><button type="button" data-condition-add>＋ 적용 조건 추가</button><div data-condition-editor></div><div class="pms-actions pm-price-term-test"><button type="button" data-term-test>입력값을 바꾸며 이 항만 테스트</button><span class="pms-help">아래 계산식에 반영하기 전에도 확인할 수 있습니다.</span></div><div class="pms-help pm-price-review-note">모든 항의 합이 최종가격입니다. 같은 입력에 대한 항이 이미 있다면 의도한 중복 가산인지 확인해 주세요. 항 반영 후 아래 실제 프로세스 테스트를 완료해야 저장할 수 있습니다.</div>`;
      }
      function bindEditor() {
        S.$('[data-term-test]', body)?.addEventListener('click', event => S.run(event.currentTarget, async () => {
          if (conditionDraft) return editorError("작성 중인 조건을 반영하거나 취소해 주세요.");
          const errors = [...sourceErrors(), ...priceErrors()]; if (errors.length) return editorError(errors.join("\n"));
          await testSingleTerm(S.copy(term));
        }));
        for (const [selector, direction] of [['[data-price-undo]', -1], ['[data-price-redo]', 1]]) S.$(selector, body)?.addEventListener('click', async () => {
          if (importing) return;
          if ((S.priceTable.pending(tableUi) || conditionDraft) && !await S.confirm("반영하지 않은 기준값·조건 초안을 버리고 실행 이력을 이동하시겠습니까?")) return;
          history.record(); if (history.move(direction)) draw();
        });
        S.$("[data-term-name]", body)?.addEventListener("input", (event) => { term.name = event.target.value; });
        S.$("[data-term-group]", body)?.addEventListener("change", async (event) => { const next = event.target.value; if (hasPriceData() && !await S.confirm("그룹을 변경하면 현재 항의 기준표·금액을 초기화합니다. 계속하시겠습니까?")) { draw(); return; } term.groupKey = next; term.bundleKey = ""; selectBundle(); resetPrice("FIXED"); term.choiceKeys = []; draw(); });
        S.$$('[name="term-bundle"]', body).forEach((input) => { input.onchange = async () => { const next = input.value; if (hasPriceData() && !await S.confirm("입력묶음을 변경하면 현재 항의 기준표·금액을 초기화합니다. 계속하시겠습니까?")) { draw(); return; } term.bundleKey = next; selectBundle(); resetPrice("FIXED"); draw(); }; });
        S.$$("[data-term-field]", body).forEach((input) => { input.onchange = async () => {
          const next = S.$$("[data-term-field]:checked", body).map((el) => el.value);
          if (hasPriceData() && !await S.confirm("계산에 쓸 항목을 변경하면 현재 항의 금액·가격표를 초기화합니다. 계속하시겠습니까?")) { draw(); return; }
          term.fieldKeys = next; resetPrice("FIXED"); draw();
        }; });
        S.$("[data-source-edit]", body)?.addEventListener("click", () => { if (!pendingTable()) { stage = 0; draw(); } });
        S.$$('[name="term-type"]', body).forEach((input) => { input.onchange = async () => { if (pendingTable()) { input.checked = false; S.$('[name="term-type"][value="' + term.type + '"]', body).checked = true; return; } if (hasPriceData() && !await S.confirm("계산 방식을 변경하면 현재 기준표·금액을 새 방식으로 초기화합니다. 계속하시겠습니까?")) { draw(); return; } resetPrice(input.value); draw(); }; });
        S.$("[data-term-amount]", body)?.addEventListener("input", (event) => { term.amount = numeric(event.target.value); });
        S.$$("[data-term-rate]", body).forEach((input) => { input.oninput = () => { term.rates ??= {}; term.rates[input.dataset.termRate] = numeric(input.value); }; });
        S.$$("[data-term-choice]", body).forEach((input) => { input.onchange = () => { term.choiceKeys = S.$$("[data-term-choice]:checked", body).map((x) => x.value); }; });
        const swap = () => { S.priceTable.swapAxes(term); S.priceTable.resetDraft(tableUi); draw(); };
        S.$('[data-axis-swap]', body)?.addEventListener('click', () => { if (!pendingTable()) swap(); });
        S.$$("[data-axis-field]", body).forEach((input) => { input.onchange = async () => {
          const axis = input.dataset.axisField === "x" ? "xAxis" : "yAxis", other = axis === "xAxis" ? "yAxis" : "xAxis", next = input.value;
          input.value = term[axis].fieldKey;
          if (pendingTable() || next === term[axis].fieldKey) return;
          const mode = await new Promise(resolve => {
            const choice = S.dialog("축 변경 방식 선택", '<p><strong>같은 가로·세로 입력의 금액을 유지하려면 축 교환을 선택하세요.</strong></p><p>축 교환: 입력 항목·기준값·이하/미만·금액표를 함께 교환합니다.</p><p>연결만 변경: 표를 그대로 두고 입력 항목만 바꿉니다. 같은 고객 입력의 금액이 달라질 수 있습니다.</p>', { wide: false, foot: '<button type="button" data-axis-cancel>취소</button><button type="button" data-axis-relink>연결만 변경 · 금액 달라짐</button><button type="button" class="primary" data-axis-transpose>축 교환 · 금액 관계 유지</button>', onClose: () => resolve(null) });
            S.$('[data-axis-cancel]', choice).onclick = () => choice.close();
            for (const [selector, value] of [['[data-axis-relink]', 'relink'], ['[data-axis-transpose]', 'swap']]) S.$(selector, choice).onclick = () => { resolve(value); choice.close(); };
          });
          if (mode === 'swap') swap();
          else if (mode === 'relink') { const old = term[axis].fieldKey; term[axis].fieldKey = next; if (term[other]?.fieldKey === next) term[other].fieldKey = old; draw(); }
        }; });
        S.$$("[data-axis-operator]", body).forEach((input) => { input.onchange = () => { term[input.dataset.axisOperator === "x" ? "xAxis" : "yAxis"].operator = input.value; draw(); }; });
        S.$$("[data-matrix-header]", body).forEach((input) => { input.oninput = () => { term[input.dataset.matrixHeader === "x" ? "xAxis" : "yAxis"].headers[Number(input.dataset.index)] = numeric(input.value); }; });
        S.$$("[data-matrix-price]", body).forEach((input) => { input.oninput = () => { const [r, c] = input.dataset.matrixPrice.split(":").map(Number); term.values[r][c] = numeric(input.value); }; });
        S.$("[data-matrix-column]", body)?.addEventListener("click", () => { if (pendingTable()) return; if (term.xAxis.headers.length >= 200 || (term.xAxis.headers.length + 1) * term.values.length > 20000) return editorError("축은 최대 200개 기준, 전체 표는 최대 20,000개 금액까지 등록할 수 있습니다."); term.xAxis.operators = term.xAxis.headers.map((_, i) => term.xAxis.operators?.[i] || null); term.xAxis.operators.push(null); term.xAxis.headers.push(null); term.values.forEach((row) => row.push(null)); draw(); });
        S.$("[data-matrix-row]", body)?.addEventListener("click", () => { if (pendingTable()) return; if (term.yAxis.headers.length >= 200 || (term.yAxis.headers.length + 1) * term.xAxis.headers.length > 20000) return editorError("축은 최대 200개 기준, 전체 표는 최대 20,000개 금액까지 등록할 수 있습니다."); term.yAxis.operators = term.yAxis.headers.map((_, i) => term.yAxis.operators?.[i] || null); term.yAxis.operators.push(null); term.yAxis.headers.push(null); term.values.push(term.xAxis.headers.map(() => null)); draw(); });
        S.$$("[data-matrix-del-col]", body).forEach((b) => { b.onclick = async () => { if (pendingTable()) return; const i = Number(b.dataset.matrixDelCol); const count = term.values.filter(row => row[i] != null && row[i] !== "").length; if (!await S.confirm(`X 기준 ${term.xAxis.headers[i] ?? '(빈 기준)'} 열과 입력 금액 ${count}개를 삭제하시겠습니까? 실행 취소로 복구할 수 있습니다.`)) return; term.xAxis.headers.splice(i, 1); term.xAxis.operators?.splice(i, 1); term.values.forEach((row) => row.splice(i, 1)); draw(); }; });
        S.$$("[data-matrix-del-row]", body).forEach((b) => { b.onclick = async () => { if (pendingTable()) return; const i = Number(b.dataset.matrixDelRow); const count = term.values[i].filter(v => v != null && v !== "").length; if (!await S.confirm(`Y 기준 ${term.yAxis.headers[i] ?? '(빈 기준)'} 행과 입력 금액 ${count}개를 삭제하시겠습니까? 실행 취소로 복구할 수 있습니다.`)) return; term.yAxis.headers.splice(i, 1); term.yAxis.operators?.splice(i, 1); term.values.splice(i, 1); draw(); }; });
        S.$("[data-matrix-import]", body)?.addEventListener("click", () => S.$("[data-matrix-file]", body).click());
        S.$("[data-matrix-file]", body)?.addEventListener("change", (event) => S.run(S.$("[data-matrix-import]", body), async () => {
          const file = event.target.files[0]; if (!file) return; if (pendingTable()) { event.target.value = ""; return; }
          const withHeaders = S.$("[data-matrix-import-mode]", body).value === "table";
          event.target.value = "";
          if (hasPriceData() && !await S.confirm(withHeaders ? "엑셀의 기준값·포함 여부·금액으로 현재 표를 교체합니다. 입력 항목 연결은 유지됩니다. 계속하시겠습니까?" : "현재 표의 금액을 엑셀로 교체합니다. 행·열 수가 바뀌면 기준값을 다시 입력해야 합니다. 계속하시겠습니까?")) return;
          importing = true; lockImport(true);
          try {
            const form = new FormData(); form.append("file", file); form.append("dimensions", term.type === "MATRIX_2D" ? "2" : "1"); form.append("includeHeaders", String(withHeaders));
            const data = await S.request("/pricing/excel/import", "POST", form), prices = data.amounts;
            if (!d.open) return;
            if (withHeaders) {
              term.xAxis.headers = data.xHeaders; term.xAxis.operator = data.xOperator || term.xAxis.operator;
              term.xAxis.operators = data.xOperators || data.xHeaders.map(() => null);
              if (term.yAxis) { term.yAxis.headers = data.yHeaders; term.yAxis.operator = data.yOperator || term.yAxis.operator; term.yAxis.operators = data.yOperators || data.yHeaders.map(() => null); }
            } else {
              if (term.xAxis.headers.length !== prices[0].length) { term.xAxis.headers = prices[0].map(() => null); term.xAxis.operators = []; }
              if (term.yAxis && term.yAxis.headers.length !== prices.length) { term.yAxis.headers = prices.map(() => null); term.yAxis.operators = []; }
            }
            term.values = S.copy(prices); S.priceTable.resetDraft(tableUi); draw(); S.$("[data-matrix-import-status]", body).textContent = `업로드 완료: ${prices.length}행 × ${prices[0].length}열 · 축 기준값을 확인해 주세요.`;
          } finally { importing = false; lockImport(false); updateHistory(); }
        }));
        S.$("[data-matrix-export]", body)?.addEventListener("click", (event) => S.run(event.currentTarget, async () => {
          if (pendingTable()) return;
          const error = priceErrors(); if (error.length) throw Error(error.join("\n"));
          await downloadExcel({ dimensions: term.type === "MATRIX_2D" ? 2 : 1, title: term.name, xLabel: label(fields().find((f) => f.key === term.xAxis.fieldKey)), yLabel: label(fields().find((f) => f.key === term.yAxis?.fieldKey)), xOperator: term.xAxis.operator, yOperator: term.yAxis?.operator, xHeaders: term.xAxis.headers, yHeaders: term.yAxis?.headers || [], amounts: term.values, xOperators: term.xAxis.operators || [], yOperators: term.yAxis?.operators || [] });
        }));
        if (term.type.startsWith("MATRIX") && stage === 1) S.priceTable.bind({ body, term, draw, error: editorError, ui: tableUi });
        S.$("[data-term-match]", body)?.addEventListener("change", (event) => { term.match = event.target.value; });
        S.$("[data-condition-add]", body)?.addEventListener("click", () => conditionEditor());
        S.$$("[data-condition-edit]", body).forEach((b) => { b.onclick = () => conditionEditor(Number(b.dataset.conditionEdit)); });
        S.$$("[data-condition-delete]", body).forEach((b) => { b.onclick = () => { term.conditions.splice(Number(b.dataset.conditionDelete), 1); conditionDraft = null; draw(); }; });
      }
      function lockImport(busy) { S.$$("button,input,select,textarea", d).forEach((el) => { if (busy) { el.dataset.priceWasDisabled = String(el.disabled); el.disabled = true; } else if (el.dataset.priceWasDisabled !== undefined) { el.disabled = el.dataset.priceWasDisabled === "true"; delete el.dataset.priceWasDisabled; } }); }
      function editorError(text) { S.$("[data-term-errors]", body).innerHTML = `<div class="pms-alert">${e(text)}</div>`; S.$("[data-term-errors]", body).scrollIntoView({ block: "nearest" }); }
      function sourceErrors() { const errors = []; if (!term.name.trim()) errors.push("항 이름을 입력해 주세요."); if (!supported(g())) errors.push("숫자 입력형·선택형 가격 영향 그룹을 선택해 주세요."); if (g()?.control === "NUMBER" && (!bundle() || !fields().length)) errors.push("입력묶음과 이 항에서 사용할 숫자 항목을 하나 이상 선택해 주세요."); return errors; }
      function priceErrors() {
        const errors = [], nonnegative = (v) => v != null && v !== "" && Number.isFinite(Number(v)) && Number(v) >= 0;
        const validMoney = (v, scale) => {
          if (!nonnegative(v)) return false;
          try { S.priceTable.money(v, scale); return true; } catch { return false; }
        };
        if (term.type === "FIXED" && !validMoney(term.amount, 2)) errors.push("고정 금액은 0~9,999,999,999,999.99원, 소수 2자리까지 입력해 주세요.");
        if (term.type === "MULTIPLY" && fields().some((f) => !validMoney(term.rates?.[f.key], 6))) errors.push("모든 입력 항목의 곱셈 단가는 0~9,999,999,999,999.99원, 소수 6자리까지 입력해 주세요.");
        if (term.type.startsWith("MATRIX")) {
          if ((term.type === "MATRIX_1D" && fields().length !== 1) || (term.type === "MATRIX_2D" && fields().length !== 2)) errors.push("1차원 가격표는 1개, 2차원 가격표는 같은 묶음의 2개 항목을 선택해 주세요.");
          const two = term.type === "MATRIX_2D";
          if (!fields().some((f) => f.key === term.xAxis?.fieldKey)) errors.push("X축 입력 항목을 선택해 주세요.");
          if (two && (!fields().some((f) => f.key === term.yAxis?.fieldKey) || term.xAxis.fieldKey === term.yAxis.fieldKey)) errors.push("X축과 다른 Y축 입력 항목을 선택해 주세요.");
          for (const [name, axis] of [["X", term.xAxis], ...(two ? [["Y", term.yAxis]] : [])]) { if (!axis?.headers?.length || axis.headers.some((v) => v == null || !Number.isFinite(Number(v)) || compareDecimal(v, 0) < 0)) errors.push(name + "축의 모든 기준값을 0 이상의 숫자로 입력해 주세요."); else if (axis.headers.some((v, i) => i > 0 && compareDecimal(v, axis.headers[i - 1]) <= 0)) errors.push(name + "축 기준값은 중복 없이 작은 값부터 순서대로 입력해 주세요."); }
          for (const axis of [term.xAxis, ...(two ? [term.yAxis] : [])]) {
            try { S.priceTable.validateHeaders(axis?.headers || []); } catch (ex) { errors.push(ex.message); }
          }
          const rows = two ? term.yAxis?.headers?.length : 1, cols = term.xAxis?.headers?.length;
          if (term.values?.length !== rows || term.values.some((row) => row.length !== cols || row.some((v) => !validMoney(v, 2)))) errors.push("표의 모든 금액을 0~9,999,999,999,999.99원, 소수 2자리까지 입력해 주세요. 무료 구간은 0을 입력하세요.");
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
      S.$("[data-term-prev]", d).onclick = async () => { if (importing || stage === 1 && pendingTable()) return; if (conditionDraft && !await S.confirm("아직 반영하지 않은 조건 초안을 버리고 이전 단계로 이동하시겠습니까?")) return; stage = Math.max(0, stage - 1); conditionDraft = null; draw(); };
      S.$("[data-term-next]", d).onclick = () => {
        if (importing || stage === 1 && pendingTable()) return;
        const errors = [...sourceErrors(), ...(stage === 1 ? priceErrors() : [])]; if (errors.length) return editorError(errors.join("\n"));
        stage++; draw();
      };
      S.$("[data-term-commit]", d).onclick = async () => {
        if (importing || pendingTable()) return;
        if (conditionDraft) return editorError("작성 중인 적용 조건을 반영하거나 취소해 주세요.");
        const errors = [...sourceErrors(), ...priceErrors()]; if (errors.length) return editorError(errors.join("\n"));
        if (scope.requiredCondition && (term.match !== "ALL" || !term.conditions.some(requiredCondition))) return editorError("선택한 옵션의 필수 조건을 유지해야 합니다.");
        if (onDraft) { onDraft(S.copy(term)); d.close(); return; }
        if (index == null && definition.terms.some(t => S.pricingBatch.overlap(t, term)) && !await S.confirm("같은 입력·옵션을 사용하는 기존 항이 있습니다. 조건이 함께 성립하면 모든 항이 더해집니다. 새 항으로 추가하시겠습니까?")) return;
        if (index == null && definition.terms.length >= 100) return editorError("가격 항은 최대 100개까지 등록할 수 있습니다.");
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
