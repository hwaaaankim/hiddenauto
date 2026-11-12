(function (S) {
  "use strict";
  const F = S.pricingFactors, e = S.e, label = (v) => v?.labels?.management || v?.labels?.customer || v?.label || v?.key || "";
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
  const branchLayoutsHelp = `<details class="pms-panel pm-price-layout-guide" data-branch-layouts><summary><span class="pm-price-guide-icon" aria-hidden="true">?</span><span><strong>재질에 따라 가격표가 다르다면</strong><small>공통 입력과 분리 입력, 두 방식의 연결 예시</small></span></summary><div class="pm-price-layout-options"><article><span class="pm-price-kicker">같은 치수를 입력할 때</span><h3>① 공통 입력 하나</h3><p>마구리 가로·높이를 한 번 입력받고, 그 입력에 연결한 한 가격 항 안에서 계산을 나눕니다.</p><ol><li>공통 마구리 입력묶음으로 항을 만듭니다.</li><li>‘조건별 가격표’에서 대리석 종류 기준과 심리스 컬러 기준을 각각 추가합니다.</li><li>각 재질에 해당하는 표·단가를 등록합니다.</li></ol><small>선택한 경로에서 이 항의 계산 하나가 적용됩니다.</small></article><article><span class="pm-price-kicker">입력 질문도 다를 때</span><h3>② 입력 그룹 분리</h3><p>일반용·심리스용 숫자 그룹을 만들고, 프로세스에서 해당 재질의 입력만 표시합니다.</p><ol><li>프로세스에 두 숫자 그룹과 표시 분기를 연결합니다.</li><li>각 그룹의 입력묶음에 별도 가격 항을 만듭니다.</li><li>일반용에는 대리석 표, 심리스용에는 심리스 표를 등록합니다.</li></ol><small>숨겨진 입력의 항은 제외되고, 표시된 입력의 항만 계산됩니다.</small></article></div><div class="pm-price-layout-note"><strong>입력 연결과 추가금을 구분해 주세요.</strong><p>일반용 입력에 표를 여러 개 넣어도 심리스용 입력까지 연결되지는 않습니다. 컬러·담수갯수 자체의 추가금은 각 그룹에서 별도 금액 항으로 등록하세요.</p><details><summary>공통 입력에 가격 항을 두 개 만드는 경우</summary><p>대리석용 항 전체에는 ‘일반대리석 선택’, 심리스용 항 전체에는 ‘심리스 선택’ 조건을 지정합니다. 각 항 안에는 해당 재질의 가격표만 넣으세요.</p></details></div></details>`;
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
  const api = (id, suffix = "", method = "GET", data, signal) => S.request(`/products/${id}/pricing${suffix}`, method, data, signal);
  // Mirror the server's reference rule; full structure/path validation still runs on the server.
  S.pricingParticipation = (groups, terms) => groups.filter(g => g.control === "NUMBER").map(group => {
    const fields = (group.bundles || []).flatMap(b => b.fields || []), referenced = new Set();
    const flattened = (terms || []).flatMap(t => [t, ...(t.branches || [])]);
    for (const term of flattened) {
      for (const axis of [term.xAxis, term.yAxis]) if (axis?.fieldKey?.startsWith(group.groupKey + ':')) referenced.add(axis.fieldKey.split(':')[1]);
      if (term.groupKey === group.groupKey) (term.fieldKeys || []).forEach(key => referenced.add(key));
      if (term.optionPricing?.groupKey === group.groupKey) referenced.add(term.optionPricing.axis?.fieldKey);
      (term.conditions || []).filter(c => c.groupKey === group.groupKey).forEach(c => referenced.add(c.fieldKey));
      (term.adjustments || []).filter(a => a.groupKey === group.groupKey).forEach(a => referenced.add(a.axis?.fieldKey));
    }
    for (const other of groups) if (flattened.some(t => t.groupKey === other.groupKey && t.fieldKeys?.includes('__numeric_value')))
      for (const c of other.choices || []) for (const token of c.numericValue?.expression || [])
        if (token.kind === 'FIELD' && token.groupKey === group.groupKey) referenced.add(token.fieldKey);
    return { group, fields, used: fields.filter(f => referenced.has(f.key)) };
  });
  S.pricingInputBindingMessage = row => `${row.group.label}: 이 그룹 또는 현재 표시된 숫자 입력묶음에 연결된 가격계산이 없습니다. 다른 숫자 그룹·다른 입력묶음의 항에 조건별 가격표를 여러 개 등록해도 이 입력이 자동 연결되지는 않습니다. 이 그룹의 입력묶음에 별도 항을 등록하거나, 프로세스에서 공통 숫자 입력 하나를 사용하는 구성으로 맞춰 주세요.`;
  S.pricingChoiceUsage = (groupKey, terms) => {
    const direct = (terms || []).filter(t => t.groupKey === groupKey || t.optionTarget?.groupKey === groupKey).length;
    const conditions = (terms || []).flatMap(t => [t, ...(t.branches || [])]).filter(t => (t.conditions || []).some(c => c.groupKey === groupKey)).length;
    return { direct, conditions };
  };
  S.descriptions = { ...(S.descriptions || {}), pricing: "고정 금액·숫자 범위별 옵션 금액·입력값 × 단가·사이즈 가격표를 항으로 만들고 합산합니다. 금액 항에 숫자·옵션·구간별 배율을 연결할 수 있으며 배율 항은 별도로 합산하지 않습니다. 규격 제품 단가와 별도로 관리합니다." };
  S.pricingGuide = () => S.dialog("비규격 가격계산 · 작업별 도움말", `<div class="pm-price-help-intro"><span class="pm-price-kicker">제품 생성 → 프로세스 등록 → 가격계산</span><h3>먼저 입력을 만들고, 그 입력에 가격을 연결합니다.</h3><p>그룹으로 비규격 제품을 만든 뒤 프로세스에서 옵션·숫자 입력·입력묶음·분기를 등록하고 ‘검증 후 등록완료’를 실행합니다. 이후 이 화면에서 계산할 항을 작성합니다.</p></div><ol class="pm-price-guide"><li><strong>계산할 입력묶음이나 옵션을 찾으세요.</strong><p>숫자는 함께 계산할 가로·높이 등을 같은 묶음으로 만듭니다. W·D·H 묶음에서 W·H만 가격표에 쓰는 것도 가능합니다. 가격영향이 켜진 숫자 그룹마다 최소 한 입력을 연결하며, 분기로 다른 묶음만 표시되는 경로도 연결됐는지 검사합니다.</p></li><li><strong>금액을 어떻게 구할지 선택하세요.</strong><div class="pm-price-help-methods"><span><b>고정 금액</b>선택하면 20,000원 추가</span><span><b>입력값 × 단가</b>개수 × 개당 단가</span><span><b>1차원 가격표</b>높이 구간별 금액</span><span><b>2차원 가격표</b>가로·높이의 교차 금액</span></div><p>‘옵션별 일괄 설정’으로 여러 옵션을 한 화면에서 등록하거나, ‘항 개별 생성’으로 하나씩 작성할 수 있습니다.</p></li><li><strong>재질에 따라 다르면 조건별로 나누세요.</strong><p>한 항의 ‘조건별 가격표’에서 각 분기의 기준 질문과 답변을 고릅니다. 대리석 질문과 심리스 컬러 질문을 같은 항에 함께 사용할 수 있습니다. 입력 그룹을 분리했다면 각 그룹의 묶음에 별도 항을 연결합니다.</p></li><li><strong>실제 입력 테스트와 전체 검사를 구분해 실행하세요.</strong><p>‘현재 입력으로 가격계산’은 화면에서 선택한 한 사례의 금액을 확인합니다. ‘누락·중복·계산 범위 검사’는 다른 가능한 분기와 표 경계까지 검사합니다. 오류가 있으면 결과의 수정 버튼으로 해당 입력이나 가격 항을 확인하세요.</p></li><li><strong>최종가격과 계산 근거를 확인한 뒤 저장하세요.</strong><p>기본금이 포함된 가격표에 기본금 항을 또 더하면 중복 청구가 됩니다. 테스트의 항별 계산 결과를 확인하고 저장하세요. 창에서 ‘항 반영’을 눌러도 제품의 계산식 저장은 아래 최종 저장 버튼에서 진행합니다.</p></li></ol><div class="pm-price-help-topics"><details><summary>엑셀·가격표의 기준값과 경계</summary><p>직접 표 생성, 엑셀 셀 붙여넣기, XLSX 업로드를 지원합니다. 기준 포함 양식은 X·Y 기준과 이하·미만을 함께 읽고, 금액 전용 양식은 업로드 후 기준을 연결합니다. 예: 높이 500·600·700과 금액 0·20,000·40,000에 ‘이하’를 쓰면 H=500은 0원, 501~600은 20,000원입니다. 소수를 허용하면 ‘500 초과’처럼 경계를 맞추세요.</p><p>행·열 편집, 기준 일괄 변경 미리보기, 실행 취소·다시 실행이 가능합니다. ‘X·Y 축 교환’은 같은 입력의 가격을 유지하고, ‘연결만 변경’은 입력의 의미가 바뀌므로 구분해서 사용하세요.</p></details><details><summary>선택 옵션에 숫자 구간별 금액 추가</summary><p>예: LED 추가 선택 시 가로 600 이하 20,000원, 1000 이하 30,000원, 그보다 크면 40,000원. 해당 옵션에서 ‘숫자 범위별 가격’을 켜고 가로 입력을 연결합니다. 선택된 구간의 금액 한 개를 사용하며 기본금액을 다시 더하지 않습니다.</p></details><details><summary>수량·배율과 항 안의 연관식</summary><p>‘수량·배율 연결’은 금액 항에 수량 또는 배율을 곱합니다. 배율 항 자체는 합계에 더하지 않습니다. ‘연관식 추가’는 같은 입력묶음의 남은 숫자로 조건별 더하기·곱하기를 구성할 때 사용합니다. 연산 순서에 따라 결과가 달라지므로 항별 계산 근거를 확인하세요.</p></details><details><summary>프로세스를 바꾸거나 다른 제품 가격식을 불러올 때</summary><p>실 제품이 없는 제품은 기존 정책에 따라 구성을 수정할 수 있습니다. 변경 후 ‘최신 프로세스 불러오기’로 현재 초안을 유지하며 연결을 점검하세요. ‘가격계산 불러오기’는 그룹·옵션·분기·입력 제한이 모두 같은 제품만 대상으로 하며 원본을 독립적으로 복사합니다.</p></details></div>`);

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
    let draftWarnings = [], groupSearch = "", groupFilter = "all", termSearch = "";
    const api = async (...args) => {
      const response = await S.request(`/products/${args[0]}/pricing${args[1] || ""}`, args[2] || "GET", args[3], args[4]);
      if (args[1]?.startsWith("/diagnose?draft=true") && args[3]?.definition?.schemaFingerprint === context?.schemaFingerprint) {
        draftWarnings = (response.issues || []).filter(i => ["PRICE_TERM_MONEY_LIMIT", "PRICE_TOTAL_MONEY_LIMIT", "PRICE_TABLE_GAP", "PRICE_BRANCH_GAP", "PRICE_BRANCH_OVERLAP", "PRICE_AXIS_RANGE", "PRICE_UNREACHABLE_TERM", "UNREACHABLE_QUESTION", "PRICE_CHOICE_GROUP_UNUSED", "PRICE_NUMERIC_PATH_UNUSED", "PRICE_NUMERIC_GROUP_UNUSED"].includes(i.code));
        renderDraftNotices();
      }
      return response;
    };
    let context, definition, answers = {}, evaluation = null, result = null, sequence = 0, testTimer, active = true;
    const assetMap = new Map([...(product.assets || []), ...(product.processAssets || []), ...(product.groups || []).flatMap((g) => [...(g.assets || []), ...(g.values || []).flatMap((v) => v.assets || [])])].map((a) => [a.id, a]));
    const supported = (g) => ["NUMBER", "RADIO", "CHECKBOX"].includes(g?.control);
    const pricingGroups = () => context.groups.filter(supported);
    const group = (groupKey) => context.groups.find((g) => g.groupKey === groupKey);
    const process = () => context.process || product.process || { questions: [] };
    const changed = () => { S.dirty = true; result = null; const diagnostics = S.$("[data-price-diagnostics]", root); if (diagnostics) diagnostics.textContent = "계산식이 변경되었습니다. 조건 검사를 다시 실행해 주세요."; updateFormula(); clearResult("계산식을 변경했습니다. 아래에서 다시 계산해 주세요."); };
    const cancelTest = () => { clearTimeout(testTimer); sequence++; };
    async function refreshProcess() {
      const previous = context;
      const next = await api(product.id, "/context");
      // A different administrator's saved price must never be overwritten by a refresh.
      if (JSON.stringify(next.definition) !== JSON.stringify(previous.definition))
        throw Error("서버의 저장된 가격계산식도 변경되었습니다. 현재 작성 내용을 확인한 뒤 화면을 새로 열어 주세요. 프로세스만 자동 병합하지 않습니다.");
      if (!next.ready) throw Error((next.issues || []).join("\n") || "수정한 프로세스를 먼저 검증·등록완료해 주세요.");
      cancelTest(); context = next;
      definition.schemaFingerprint = context.schemaFingerprint;
      definition.testAnswers = {}; answers = {}; evaluation = null; result = null;
      paint(); changed();
      await evaluateProcess(true);
      S.toast("최신 프로세스를 불러왔습니다. 작성 중인 가격 항은 유지되며 전체 검사와 테스트 후 저장해야 합니다.");
    }
    function processRangeHelp() {
      return `<div class="pms-help pm-price-quantity-help"><strong>수량·단가와 최종 합계 확인</strong><p>하한·상한을 빈칸으로 두어도 고객 수량이 최댓값으로 저장되는 것은 아닙니다. 음수 불가 필드의 하한은 0, 상한 미설정은 기술 한도 10억 안에서 검사하며 입력 간격·분기·합계 제한도 적용합니다. 수량 항을 여러 개 더하거나 배율을 곱하면 각 항이 정상이더라도 합계 한도를 넘을 수 있습니다. 실제 주문 가능한 수량은 프로세스의 해당 숫자 필드에서 설정하세요. 같은 그룹의 서로 다른 독립 입력은 각각 단가를 연결할 수 있습니다.</p><ol><li>새 탭에서 프로세스의 해당 수량 최대값·입력 간격·조건별 제한을 수정하고 ‘검증·등록완료’를 실행합니다.</li><li>이 창으로 돌아와 ‘최신 프로세스 불러오기’를 누르고 초안의 여러 항·조건은 계속 작성할 수 있습니다. 실제 입력 범위·단가·조건을 정리한 뒤 전체 검사 → 실제 입력 테스트 → 저장을 진행합니다.</li></ol><p>수량 상한은 자동 지정하지 않습니다. 저장 전 항은 현재 창에만 있으므로 이 창을 닫거나 새로고침하지 마세요.</p><div class="pms-actions"><a class="pms-button" href="/admin/product-master/products/${product.id}/process${product.actualCount ? '?mode=view' : ''}" target="_blank" rel="noopener">프로세스 ${product.actualCount ? '조회' : '입력 범위 수정'} ↗</a><button type="button" data-process-refresh>최신 프로세스 불러오기</button></div>${product.actualCount ? '<p>실 제품이 있는 제품은 기존 정책상 프로세스 수정이 제한됩니다. 기존 실 제품에 영향을 주지 않도록 새 제품 구성에서 진행하세요.</p>' : ''}</div>`;
    }
    function mergeTestAnswers(next, draft) {
      const merged = S.copy(next.answers || {});
      for (const state of next.questions || []) {
        const q = state.question;
        if (!state.visible && !q.fixed) { delete merged[q.key]; continue; }
        if (q.fixed || !draft[q.key]) continue;
        if (!state.errors?.length) { merged[q.key] = S.copy(state.answer); continue; }
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
      definition.schemaFingerprint = context.schemaFingerprint;
      answers = S.copy(definition.testAnswers); evaluation = null; result = null; S.dirty = false; paint();
      if (context.ready) await evaluateProcess(true);
    }
    function paint() {
      root.innerHTML = `<div class="pm-price-page"><section class="pms-panel pm-price-hero"><header class="pms-panel-title"><div><span class="pm-price-kicker">비규격 가격계산</span><strong>${e(product.productName)}</strong><small class="pm-price-product-code">${e(product.catalogCode || product.productCode)}</small></div><div class="pms-actions">${S.badge(context.registered ? "가격계산 등록됨" : "가격계산 미등록", context.registered ? "green" : "")}${context.stale ? S.badge("프로세스 변경 · 재검증 필요", "red") : ""}<a class="pms-button" href="/admin/product-master/non-standard">비규격 목록</a><a class="pms-button" href="/admin/product-master/products/${product.id}/process${product.actualCount ? "?mode=view" : ""}">프로세스 ${product.actualCount ? "조회" : "확인·수정"}</a></div></header><div class="pms-panel-body"><p class="pm-price-next-task"><strong>${!context.ready ? "먼저 프로세스 등록을 완료해 주세요." : context.stale ? "변경한 입력과 기존 가격 항의 연결을 확인해 주세요." : context.registered ? "등록된 항을 확인하고, 바꾼 내용만 다시 테스트·저장하세요." : "계산할 입력묶음이나 옵션을 선택해 첫 항을 만드세요."}</strong></p><nav class="pm-price-workflow" aria-label="가격계산 작업 위치"><button type="button" data-price-jump="groups"><b>01</b><span>입력·옵션 찾기<small data-price-overview="groups"></small></span></button><button type="button" data-price-jump="terms"><b>02</b><span>가격 항 작성<small data-price-overview="terms"></small></span></button><button type="button" data-price-jump="test"><b>03</b><span>현재 입력 테스트<small>한 사례의 금액 확인</small></span></button><button type="button" data-price-jump="diagnostics"><b>04</b><span>전체 검사·저장<small>다른 경로·표 경계 확인</small></span></button></nav><details class="pm-price-inline-help" data-price-workflow><summary>그룹·제품·프로세스·가격계산의 등록 순서</summary><ol><li>그룹으로 비규격 제품을 생성합니다.</li><li>이 제품의 프로세스에서 옵션·숫자 입력·묶음·분기를 등록합니다.</li><li>프로세스의 ‘검증 후 등록완료’를 실행합니다.</li><li>가격영향 그룹에 금액·조건을 연결하고 테스트·저장합니다.</li></ol><p>옵션이나 입력 항목을 추가하려면 상단의 프로세스 화면을 사용하세요. 이 화면은 등록된 입력으로 가격 항을 만듭니다.</p></details>${!context.ready ? `<div class="pms-alert"><strong>가격 항은 계속 편집할 수 있습니다. 계산·저장은 다음 사항을 먼저 해결하세요.</strong>${(context.issues || []).map((x) => `<p>${e(x)}</p>`).join("")}</div>` : ""}${context.stale ? '<div class="pms-alert"><strong>저장된 가격식과 현재 프로세스가 다릅니다.</strong><p>기존 항은 유지됩니다. 연결을 수정한 뒤 실제 입력 테스트와 전체 검사를 통과해 다시 저장해 주세요.</p></div>' : ""}</div></section>
        ${branchLayoutsHelp}
        <section class="pms-panel" data-price-section="groups" tabindex="-1"><header class="pms-panel-title"><div><span class="pm-price-step">1</span><strong>가격에 연결할 입력·옵션</strong></div></header><div class="pms-panel-body"><p class="pm-price-section-intro">숫자는 계산할 <strong>입력묶음</strong>을, 선택형은 금액을 더할 <strong>옵션</strong>을 선택하세요.</p><div class="pm-price-group-tools"><label class="pm-price-search"><span>그룹·옵션 찾기</span><input type="search" data-price-group-search placeholder="예: 마구리, 심리스, 손잡이" value="${e(groupSearch)}"></label><div class="pm-price-filters" role="group" aria-label="그룹 표시 범위"><button type="button" data-price-group-filter="all">전체</button><button type="button" data-price-group-filter="number">숫자 입력</button><button type="button" data-price-group-filter="choice">선택 옵션</button><button type="button" data-price-group-filter="missing">미연결 숫자</button></div><span data-price-group-count role="status"></span></div><div data-price-participation role="status" aria-live="polite"></div><div class="pm-price-groups" data-price-groups></div><p class="pms-empty" data-price-group-empty hidden>일치하는 그룹이 없습니다. 검색어나 표시 범위를 바꿔 주세요.</p><details class="pm-price-inline-help"><summary>‘금액 항’과 ‘조건 연결’은 무엇이 다른가요?</summary><p>금액 항은 최종가격에 더할 돈을 계산합니다. 조건 연결은 다른 항이 언제 적용될지 정합니다. 심리스 컬러를 마구리 표의 조건으로 사용했더라도 컬러 자체의 추가금은 자동 등록되지 않습니다.</p><p>숫자 그룹마다 최소 한 입력을 가격 항·구간 금액·숫자 조건에 연결합니다. 모든 숫자를 사용할 필요는 없지만, 분기로 다른 묶음만 표시되는 경로도 계산이 연결돼야 합니다. 다른 제품의 금액은 이 화면의 수정으로 함께 변경되지 않습니다.</p></details></div></section>
        <section class="pms-panel" data-price-section="terms" tabindex="-1"><header class="pms-panel-title"><div><span class="pm-price-step">2</span><strong>가격을 구성하는 항</strong><span data-price-term-count class="pm-price-muted"></span></div><div class="pms-actions"><button type="button" class="primary" data-price-batch ${pricingGroups().length ? "" : "disabled"}>옵션별 일괄 설정</button><button type="button" data-price-add ${pricingGroups().length ? "" : "disabled"}>＋ 항 개별 생성</button></div></header><div class="pms-panel-body"><label class="pm-price-search pm-price-term-search"><span>등록된 항 찾기</span><input type="search" data-price-term-search placeholder="항 이름 또는 연결된 그룹" value="${e(termSearch)}"></label><div data-price-draft-notices role="status"></div><div data-price-terms></div><p class="pms-empty" data-price-term-empty hidden>검색한 항이 없습니다. 검색어를 지우면 전체 항이 다시 표시됩니다.</p></div></section>
        <section class="pms-panel pm-price-formula-panel"><header class="pms-panel-title"><strong>최종가격에 더하는 항</strong></header><div class="pms-panel-body"><div class="pm-price-formula" data-price-formula></div><details class="pm-price-inline-help"><summary>숨겨진 입력·배율은 어떻게 계산하나요?</summary><p>현재 경로에서 숨겨진 입력의 항은 제외합니다. 별도 금액 항은 더하고, 연결한 수량·배율은 해당 금액에 곱합니다. 배율 전용 항을 합계에 다시 더하지 않습니다. ↑↓로 항을 옮겨도 연결 관계는 유지됩니다.</p></details></div></section>
        <section class="pms-panel" data-price-section="test" tabindex="-1"><header class="pms-panel-title"><div><span class="pm-price-step">3</span><strong>현재 입력으로 가격 테스트</strong></div><button type="button" data-price-reset ${context.ready ? "" : "disabled"}>테스트 초기화</button></header><div class="pms-panel-body"><p class="pm-price-section-intro">고객처럼 답변을 선택한 뒤 계산하세요. 앞 답변을 바꾸면 뒤 질문과 입력 제한도 다시 적용됩니다.</p><div data-price-test></div><div data-price-test-errors role="alert"></div><div class="pms-actions pm-price-test-actions"><button type="button" class="primary" data-price-evaluate ${context.ready ? "" : "disabled"}>현재 입력으로 가격계산</button><span data-price-test-status role="status"></span></div><div data-price-result aria-live="polite"></div></div></section>
        <section class="pms-panel" data-price-section="diagnostics" tabindex="-1"><header class="pms-panel-title"><div><span class="pm-price-step">4</span><strong>모든 경로의 가격 연결 검사</strong></div><button type="button" data-price-diagnose ${context.ready ? "" : "disabled"}>누락·중복·계산 범위 검사</button></header><div class="pms-panel-body"><p class="pm-price-section-intro">현재 테스트한 사례 외에, 실제로 표시 가능한 입력·조건·표 경계를 함께 검사합니다.</p><details class="pm-price-inline-help"><summary>오류와 확인 안내의 차이</summary><p><b>오류:</b> 적용할 표가 없거나 같은 표가 겹치는 등 정확한 계산이 불가능한 경우입니다. 수정 후 저장할 수 있습니다.</p><p><b>확인 필요:</b> 선택 그룹의 직접 추가금이 없거나 여러 독립 항이 더해지는 경우 등입니다. 기본금+추가금처럼 의도된 계산이면 안내를 확인하고 저장할 수 있습니다. 검사만으로 신규 사업 단가를 대신 정하지 않습니다.</p><p>정수 간격 1에서는 600 이하 다음이 601 이상입니다. 소수를 허용하면 600 초과를 사용하세요. 600 이하와 600 이상은 겹치고, 600 이하와 602 이상 사이에는 601이 빠집니다.</p></details><div data-price-diagnostics role="status">아직 검사하지 않았습니다. 저장할 때도 자동으로 검사합니다.</div></div></section>
        <div class="pms-actions pm-price-save-bar"><span data-price-save-status role="status">${context.registered ? "등록된 가격계산식을 불러왔습니다." : "저장하기 전까지 계산식은 등록되지 않습니다."}</span><button type="button" data-price-copy ${!context.registered && context.ready ? "" : "disabled"}>가격계산 불러오기</button>${context.registered ? '<button type="button" data-price-delete class="danger">등록 계산식 삭제</button>' : ""}<button type="button" data-price-save class="primary" ${context.ready ? "" : "disabled"}>테스트 확인 후 가격계산식 저장</button></div></div>`;
      renderGroups(); renderTerms(); updateFormula();
      S.$('[data-price-group-search]', root).oninput = event => { groupSearch = event.target.value; filterGroups(); };
      S.$$('[data-price-group-filter]', root).forEach(button => button.onclick = () => { groupFilter = button.dataset.priceGroupFilter; filterGroups(); });
      S.$('[data-price-term-search]', root).oninput = event => { termSearch = event.target.value; filterTerms(); };
      S.$("[data-price-batch]", root).onclick = () => openBatch();
      S.$("[data-price-add]", root).onclick = () => { if (definition.terms.length >= 100) return S.toast("금액·배율 항은 합쳐서 최대 100개까지 등록할 수 있습니다."); editTerm(); };
      S.$("[data-price-evaluate]", root).onclick = (event) => S.run(event.currentTarget, () => calculate());
      S.$("[data-price-reset]", root).onclick = (event) => S.run(event.currentTarget, async () => { cancelTest(); answers = {}; evaluation = null; result = null; await evaluateProcess(true); clearResult("새 답변을 입력하고 계산해 주세요."); });
      S.$("[data-price-copy]", root).onclick = (event) => S.run(event.currentTarget, async () => { if (S.dirty && !await S.confirm("작성 중인 계산식 대신 불러온 계산식을 표시합니다. 계속하시겠습니까?")) return; await S.openPricingCopy(product, load); });
      S.$("[data-price-delete]", root)?.addEventListener("click", (event) => S.run(event.currentTarget, async () => {
        if (!await S.confirm("등록된 가격계산식을 삭제하시겠습니까? 실 제품 가격계산에서도 이 계산식을 더 이상 사용할 수 없습니다.")) return;
        await api(product.id, "?version=" + context.version, "DELETE"); await load(); S.toast("가격계산식을 삭제했습니다.");
      }));
      S.$("[data-price-diagnose]", root).onclick = event => S.run(event.currentTarget, () => diagnose());
      S.$("[data-price-save]", root).onclick = (event) => S.run(event.currentTarget, async () => {
        if (!context.ready) throw Error("현재 프로세스를 먼저 검증 후 등록완료해 주세요. 작성한 가격 항은 화면에 유지됩니다.");
        F.validateLinks(definition.terms);
        if (!definition.terms.some(t => !F.isFactor(t))) throw Error("최종가격에 더할 금액 항을 하나 이상 추가해 주세요.");
        const missing = S.pricingParticipation(context.groups, definition.terms).filter(row => !row.used.length);
        if (missing.length) throw Error(missing.map(row => `${row.group.label}: ${row.fields.map(label).join(' / ')} 중 최소 1개를 가격 항·숫자 범위별 옵션 금액·숫자 적용 조건에 연결해 주세요. 모든 입력이나 묶음을 사용할 필요는 없습니다.`).join('\n'));
        const signature = JSON.stringify(definition.terms);
        if (!await calculate()) throw Error("실제 입력값과 항별 계산 오류를 수정한 뒤 저장해 주세요.");
        const validation = await diagnose();
        if (!validation?.valid || !validation.exhaustive) throw Error("전체 조건 검증의 오류를 수정한 뒤 저장해 주세요. 아래 검사 결과에서 재현 입력을 확인할 수 있습니다.");
        if (validation.issues.some(issue => issue.severity === "WARNING") && !await S.priceTable.review("가격 조건 확인 안내", diagnosticHtml(validation, false), "안내 확인 후 저장")) return;
        if (signature !== JSON.stringify(definition.terms)) throw Error("검사 중 계산식이 변경되었습니다. 다시 검사하고 저장해 주세요.");
        const saved = S.copy(definition); saved.schemaFingerprint = context.schemaFingerprint; saved.testAnswers = S.copy(answers);
        await api(product.id, "", "PUT", { version: context.version, definition: saved }); await load(); S.toast("가격계산식을 저장했습니다.");
      });
    }
    root.addEventListener('click', event => {
      const jump = event.target.closest('[data-price-jump]');
      if (jump && root.contains(jump)) {
        const section = S.$$('[data-price-section]', root).find(el => el.dataset.priceSection === jump.dataset.priceJump);
        section?.focus({ preventScroll: true }); section?.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
      }
      const button = event.target.closest('[data-process-refresh]');
      if (button && root.contains(button)) S.run(button, refreshProcess);
      const target = event.target.closest('[data-diagnostic-target]');
      if (target && root.contains(target)) {
        const match = target.dataset.diagnosticTarget.match(/^terms\.(.+)\.([xy])Axis\.outsidePolicy$/);
        const key = match?.[1] || target.dataset.diagnosticTarget.replace(/^terms\./, '');
        const index = definition.terms.findIndex(t => t.key === key);
        if (index >= 0) editTerm(index, '', '', null, null, match ? { focusAxis: match[2] } : {});
      }
      const groupButton = event.target.closest('[data-diagnostic-group]');
      if (groupButton && root.contains(groupButton)) openBatch(groupButton.dataset.diagnosticGroup);

    });
    function diagnosticHtml(validation, actions = true) {
      const issueLink = issue => {
        if (!actions) return "";
        if (issue.target?.startsWith('terms.')) {
          const owner = definition.terms.find(t => issue.target === `terms.${t.key}` || issue.target === `terms.${t.key}.xAxis.outsidePolicy` || issue.target === `terms.${t.key}.yAxis.outsidePolicy`);
          if (owner) return `<button type="button" data-diagnostic-target="${e(issue.target)}">해당 가격 항 수정</button>`;
        }
        if (issue.target?.startsWith('groups.')) return `<button type="button" data-diagnostic-group="${e(issue.target.slice(7))}">이 그룹의 추가금 등록</button>`;
        if (process().questions.some(q => q.key === issue.target)) return `<a class="pms-button" href="/admin/product-master/products/${product.id}/process?question=${e(encodeURIComponent(issue.target))}&amp;connections=show" target="_blank" rel="noopener">이 질문의 표시 연결 확인 ↗</a>`;
        return '';
      };
      const issues = validation.issues || [], count = severity => issues.filter(i => i.severity === severity).length;
      const hints = {
        PRICE_BRANCH_GAP: ['이 경로에 적용할 계산이 없습니다', '이 항의 전체 적용 조건과 각 분기의 답변 연결을 확인하세요.'],
        PRICE_BRANCH_OVERLAP: ['같은 입력에 여러 조건별 계산이 적용됩니다', '같은 항의 분기 답변과 추가 조건이 겹치는 부분을 수정하세요.'],
        PRICE_NUMERIC_GROUP_UNUSED: ['숫자 그룹이 가격계산에 연결되지 않았습니다', '아래 그룹 등록 버튼에서 실제 입력묶음에 항을 연결하세요.'],
        PRICE_NUMERIC_PATH_UNUSED: ['이 경로에서 보이는 입력묶음의 계산이 없습니다', '다른 그룹·묶음의 표를 늘리는 대신, 현재 표시되는 묶음에 연결하세요.'],
        PRICE_UNREACHABLE_TERM: ['이 가격 항을 적용할 경로가 없습니다', '항의 조건과 프로세스의 질문 표시 조건을 함께 확인하세요.'],
        UNREACHABLE_QUESTION: ['이 질문으로 도달하는 경로가 없습니다', '앞 질문의 답변에 따라 이 질문을 표시하도록 연결했는지 확인하세요.'],
        PRICE_CHOICE_GROUP_UNUSED: ['이 선택 그룹의 직접 추가금이 없습니다', '별도 추가금이 필요한지 확인하세요. 무료 옵션이면 금액을 임의로 만들 필요는 없습니다.'],
        PRICE_TABLE_GAP: ['허용 입력을 가격표가 모두 포함하지 않습니다', '재현 입력이 포함되도록 표의 경계나 프로세스 허용 범위를 맞추세요.'],
        PRICE_VALIDATION_PARTITIONED: ['실제 연결 관계를 나누어 검사했습니다', '대표 입력 수는 각 검사 묶음의 건수 합계입니다. 전체 질문 조합 수와는 다릅니다.']
      };
      return `<div class="pm-price-check-summary"><strong>${validation.valid && validation.exhaustive ? '계산 오류 검사 완료' : '수정이 필요한 오류가 있습니다'}</strong><div>${S.badge('오류 ' + count('ERROR'), count('ERROR') ? 'red' : '')}${S.badge('확인 필요 ' + count('WARNING'), '')}${S.badge('안내 ' + count('INFO'), '')}</div><small>검사한 대표 상태 ${e(validation.scenarios)}개${validation.exhaustive ? '' : ' · 검사 미완료'}</small></div>${issues.some(i => ['PRICE_TERM_MONEY_LIMIT', 'PRICE_TOTAL_MONEY_LIMIT', 'PRICE_AXIS_RANGE'].includes(i.code)) ? processRangeHelp() : ''}${issues.length ? `<ul class="pm-price-diagnostic-list">${issues.map(issue => {
        const hint = hints[issue.code], message = String(issue.message || '');
        return `<li data-diagnostic-severity="${e(issue.severity)}"><header><span class="pm-price-issue-kind">${issue.severity === 'ERROR' ? '오류' : issue.severity === 'WARNING' ? '확인 필요' : '안내'}</span><strong>${e(issue.location)}</strong></header>${hint ? `<h4>${e(hint[0])}</h4><p>${e(hint[1])}</p>` : ''}${hint || message.length > 320 ? `<details class="pm-price-issue-detail"><summary>재현 입력·상세 근거 보기</summary><p>${e(message)}</p></details>` : `<p>${e(message)}</p>`}<div class="pms-actions">${issueLink(issue)}</div></li>`;
      }).join('')}</ul>` : '<p class="pm-price-check-clear">조건 누락·중복·계산 범위 오류가 발견되지 않았습니다.</p>'}`;
    }
    async function diagnose() {
      const signature = JSON.stringify(definition), draft = S.copy(definition);
      draft.schemaFingerprint = context.schemaFingerprint;
      const target = S.$("[data-price-diagnostics]", root); target.textContent = "프로세스 분기와 가격 조건을 검사하고 있습니다…";
      try {
        const validation = await api(product.id, "/diagnose", "POST", { definition: draft, answers: {} });
        if (signature !== JSON.stringify(definition)) { target.textContent = "검사 중 계산식이 변경되었습니다. 다시 검사해 주세요."; return null; }
        draftWarnings = []; renderDraftNotices();
        target.innerHTML = diagnosticHtml(validation);
        return validation;
      } catch (error) { target.textContent = error.message; throw error; }
    }
    function openBatch(selectedGroup) {
      S.pricingBatch.open({ groups: pricingGroups(), definition, selectedGroup, summary,
        processUrl: `/admin/product-master/products/${product.id}/process`,
        diagnose: context.ready ? (terms, signal) => api(product.id, '/diagnose?draft=true', 'POST', { definition: { ...S.copy(definition), terms, schemaFingerprint: context.schemaFingerprint }, answers: {} }, signal) : null,
        edit: (term, onDraft, scope) => editTerm(null, "", "", term, onDraft, scope),
        apply: terms => { definition.terms = terms; changed(); renderTerms(); renderGroups(); S.toast("일괄 설정을 항에 반영했습니다. 실제 입력으로 테스트한 뒤 저장해 주세요."); }
      });
    }
    function renderGroups() {
      const choiceUsage = new Map(context.groups.map(g => [g.groupKey, S.pricingChoiceUsage(g.groupKey, definition.terms)]));
      const participation = S.pricingParticipation(context.groups, definition.terms);
      const missing = participation.filter(row => !row.used.length);
      const status = S.$('[data-price-participation]', root);
      status.innerHTML = participation.length ? `<div class="pm-price-binding-summary ${missing.length ? 'needs-attention' : ''}"><strong>${missing.length ? `가격 연결 필요 · 숫자 그룹 ${missing.length}개` : '숫자 그룹의 최소 연결 확인'}</strong><span>${participation.length - missing.length}/${participation.length}개 그룹 연결 · 전체 경로의 유효성은 아래 검사에서 확인</span>${missing.length ? `<p>${missing.map(row => e(row.group.label)).join(' · ')} — 각 그룹의 입력묶음에서 항을 추가하세요.</p>` : ''}<details><summary>그룹별 연결 내역</summary><ul>${participation.map(row => `<li><b>${e(row.group.label)}</b><span>입력 ${row.fields.length}개 · 묶음 ${row.group.bundles.length}개 · 연결된 입력 ${row.used.length}개</span>${row.used.length ? `<small>${row.used.map(f => e(label(f))).join(' · ')}</small>` : '<small>최소 한 입력의 연결이 필요합니다.</small>'}</li>`).join('')}</ul></details></div>` : '';
      S.$('[data-price-overview="groups"]', root).textContent = `${context.groups.length}개 그룹 · ${missing.length ? '숫자 연결 ' + missing.length + '개 필요' : '입력·옵션별로 등록'}`;
      S.$("[data-price-groups]", root).innerHTML = context.groups.map((g) => `<article class="pm-price-group" data-price-group-card="${e(g.groupKey)}"><header><strong>${e(g.label)}</strong>${S.badge(typesFor(g), "")}${g.control === "NUMBER" ? S.badge((participation.find(row => row.group.groupKey === g.groupKey)?.used.length || 0) + "개 입력 연결", participation.find(row => row.group.groupKey === g.groupKey)?.used.length ? "green" : "") : S.badge((choiceUsage.get(g.groupKey).direct ? "직접 추가금 " + choiceUsage.get(g.groupKey).direct + "개 항" : "직접 추가금 항 없음") + (choiceUsage.get(g.groupKey).conditions ? " · 조건 연결 " + choiceUsage.get(g.groupKey).conditions + "개" : ""), choiceUsage.get(g.groupKey).direct ? "green" : "")}</header>${!supported(g) ? '<p class="pms-help">가격 항은 숫자 입력형·선택형에서 등록합니다. 이 그룹은 계산에 사용하지 않습니다.</p>' : g.control === "NUMBER" ? `<div class="pm-price-bundles">${g.bundles.map((b) => `<button type="button" data-price-bundle="${e(b.key)}" data-price-group="${e(g.groupKey)}" ${pricingGroups().length ? "" : "disabled"}><strong>${b.fields.length > 1 ? `묶음 · ${b.fields.length}개 입력` : "독립 · 1개 입력"} — ${e(b.label || b.fields.map(label).join(" · "))}</strong><small>${b.fields.map((f) => e(label(f)) + (f.unit ? " (" + e(f.unit) + ")" : "")).join(" · ")}</small><span>${definition.terms.filter((term) => term.groupKey === g.groupKey && term.bundleKey === b.key).length ? definition.terms.filter((term) => term.groupKey === g.groupKey && term.bundleKey === b.key).length + "개 직접 항 · " : "직접 항 없음 · "}이 묶음으로 항 추가 ＋</span></button>`).join("")}</div>` : `<div class="pm-price-choice-tags">${g.choices.map((c) => S.badge(label(c))).join("")}</div><button type="button" data-price-group="${e(g.groupKey)}" ${pricingGroups().length ? "" : "disabled"}>이 그룹으로 항 추가 ＋</button>`}${supported(g) ? `<button type="button" data-group-batch="${e(g.groupKey)}" ${pricingGroups().length ? "" : "disabled"}>이 그룹 옵션별 일괄 설정</button>` : ""}</article>`).join("") || '<div class="pms-empty">이 제품에 연결된 그룹 중 가격계산 영향이 설정된 그룹이 없습니다. 제품 구성에 필요한 그룹이 포함되어 있는지, 그룹·옵션 관리에서 그 비규격 그룹의 가격영향이 켜져 있는지 확인해 주세요.</div>';
      S.$$('[data-group-batch]', root).forEach(b => { b.onclick = () => openBatch(b.dataset.groupBatch); });
      S.$$("[data-price-group]", root).forEach((b) => { b.onclick = () => { if (definition.terms.length >= 100) return S.toast("금액·배율 항은 합쳐서 최대 100개까지 등록할 수 있습니다."); editTerm(null, b.dataset.priceGroup, b.dataset.priceBundle); }; });
      filterGroups();
    }
    function filterGroups() {
      const search = groupSearch.trim().toLocaleLowerCase(), participation = S.pricingParticipation(context.groups, definition.terms);
      let shown = 0;
      S.$$('[data-price-group-card]', root).forEach(card => {
        const g = group(card.dataset.priceGroupCard), isNumber = g.control === 'NUMBER';
        const text = [g.label, ...(g.choices || []).map(label), ...(g.bundles || []).flatMap(b => [b.label, ...(b.fields || []).map(label)])].join(' ').toLocaleLowerCase();
        const match = groupFilter === 'all' || groupFilter === 'number' && isNumber || groupFilter === 'choice' && S.isChoice(g.control) || groupFilter === 'missing' && isNumber && !participation.find(row => row.group.groupKey === g.groupKey)?.used.length;
        card.hidden = !match || !!search && !text.includes(search); if (!card.hidden) shown++;
      });
      S.$$('[data-price-group-filter]', root).forEach(button => { button.setAttribute('aria-pressed', String(button.dataset.priceGroupFilter === groupFilter)); });
      S.$('[data-price-group-count]', root).textContent = `${shown} / ${context.groups.length}개 그룹`;
      S.$('[data-price-group-empty]', root).hidden = shown > 0 || !context.groups.length;
    }
    function filterTerms() {
      const search = termSearch.trim().toLocaleLowerCase(); let shown = 0;
      S.$$('[data-price-term-card]', root).forEach(card => {
        const term = definition.terms[Number(card.dataset.priceTermCard)];
        const text = [term.name, group(term.groupKey)?.label, summary(term), ...(term.conditions || []).map(conditionText)].join(' ').toLocaleLowerCase();
        card.hidden = !!search && !text.includes(search); if (!card.hidden) shown++;
      });
      S.$('[data-price-term-count]', root).textContent = `${definition.terms.length}개 항`;
      S.$('[data-price-overview="terms"]', root).textContent = `${definition.terms.filter(t => !F.isFactor(t)).length}개 금액 항 · ${definition.terms.filter(F.isFactor).length}개 배율 항`;
      S.$('[data-price-term-empty]', root).hidden = shown > 0 || !definition.terms.length;
    }
    function typesFor(g) { return g.control === "NUMBER" ? (g.choices?.some(c => c.numericValue) ? "단일선택 · 계산용 숫자" : "숫자 입력묶음") : S.controls[g.control] || g.control; }
    function summary(term) {
      const g = group(term.groupKey), b = g?.bundles?.find((b) => b.key === term.bundleKey);
      const selected = (term.fieldKeys || []).map((k) => label(b?.fields.find((f) => f.key === k)) || k);
      if (term.optionPricing) {
        const p = term.optionPricing, q = process().questions.find(q => q.key === p.groupKey), f = q?.fields?.find(f => f.key === p.axis?.fieldKey);
        return `${label(q)} · ${label(f)} 범위별 옵션 금액 · ${p.axis?.headers?.length + 1}개 구간 (기본금액과 중복 가산 안 함)`;
      }
      if (term.type === "FIXED") return `${F.isFactor(term) ? "배율 " : "＋ "}${F.isFactor(term) ? F.valueText(term.amount) : money(term.amount)}${term.choiceKeys?.length ? " · " + term.choiceKeys.map((k) => label(g?.choices?.find((c) => c.key === k)) || k).join(" / ") : ""}`;
      if (term.type === "MULTIPLY") return selected.map((name, i) => `${name} × ${F.isFactor(term) ? F.valueText(term.rates?.[term.fieldKeys[i]]) : rateText(term.rates?.[term.fieldKeys[i]])}`).join(" ＋ ");
      const axisLabel = axis => {
        if (!axis?.fieldKey) return "";
        const [questionKey, fieldKey] = axis.fieldKey.split(":");
        if (!fieldKey) return label(b?.fields.find(f => f.key === questionKey));
        const question = process().questions.find(q => q.key === questionKey);
        const source = question?.fields?.find(f => f.key === fieldKey);
        return source ? label(question) + " · " + label(source) : "연결한 입력 확인 필요";
      };
      const x = axisLabel(term.xAxis), y = axisLabel(term.yAxis);
      return term.type === "MATRIX_2D" ? `${x} × ${y} 기준표 · ${term.yAxis?.headers?.length || 0}행 × ${term.xAxis?.headers?.length || 0}열` : `${x} 기준표 · ${term.xAxis?.headers?.length || 0}개 기준`;
    }
    function conditionText(c) {
        const q = process().questions.find((q) => q.key === c.groupKey), f = q?.fields?.find((f) => f.key === c.fieldKey);
        if (!q) return `삭제된 질문 (${c.groupKey})의 조건 · 수정하거나 삭제해 주세요.`;
        if (c.fieldKey && !f) return `${label(q)} · 삭제된 입력 (${c.fieldKey})의 조건 · 다시 연결해 주세요.`;
        if (["PRESENT", "ABSENT"].includes(c.operator)) return `${label(q)}${f ? " · " + label(f) : ""}: ${c.operator === "PRESENT" ? "답변 있음" : "답변 없음"}`;
        if (q && S.isChoice(q.control)) return `${label(q)}: ${(c.choiceKeys || []).map((k) => label(q.choices.find((x) => x.key === k)) || k).join(" / ")} ${c.operator === "NE" ? "제외" : "선택"}`;
        if (c.operator === "RANGE") return `${label(q)} · ${label(f)}: ${c.lower} ${c.lowerInclusive ? "이상" : "초과"} ~ ${c.upper} ${c.upperInclusive ? "이하" : "미만"}`;
        return `${label(q)} · ${label(f)}: ${c.lower ?? c.text ?? ""} ${{ EQ: "일치", NE: "불일치", LE: "이하", LT: "미만", GE: "이상", GT: "초과" }[c.operator] || c.operator}`;
      }
    function applyTerms(next) { definition.terms = next; changed(); renderTerms(); renderGroups(); }
    function branchEditor(owner, onApply = null, extraTerms = []) {
      const original = JSON.stringify(definition.terms);
      const single = () => {
        const draft = S.copy(owner); draft.branches = [];
        editTerm(null, '', '', draft, next => {
          if (JSON.stringify(definition.terms) !== original) return S.toast('작성 중 가격식이 변경되었습니다. 다시 열어 주세요.');
          next.key = owner.key; next.branches = [];
          if (onApply) onApply(next); else applyTerms(definition.terms.map(t => t.key === owner.key ? next : t));
        }, { locked: true, allowTypeChange: true });
      };
      return S.pricingBranches.open({ owner, process: process(), terms: definition.terms.concat(extraTerms), currentTerms: () => definition.terms.concat(extraTerms),
        conditionText, summary, single,
        edit: (term, done, scope) => editTerm(null, '', '', term, done, scope),
        diagnose: terms => api(product.id, '/diagnose?draft=true', 'POST', { definition: { ...S.copy(definition), terms, schemaFingerprint: context.schemaFingerprint }, answers: {} }),
        apply: (draft, next) => { if (onApply) onApply(draft); else applyTerms(next); }
      });
    }
    function relatedEditor(owner, adjustmentKey = null) {
      const question = process().questions.find(q => q.key === owner.groupKey);
      if (question?.control === 'CHECKBOX') return S.toast('다중선택에는 가격 연관식을 등록할 수 없습니다.');
      if (S.arithmetic.numeric(question)) return branchEditor(owner);
      const existingRelated = owner.adjustments?.find(a => a.key === adjustmentKey);
      if(F.isFactor(owner))return;
      const base = group(owner.groupKey), used = new Set(owner.fieldKeys || []);
      (owner.adjustments || []).forEach(a=>{if(a.key!==adjustmentKey && a.groupKey===owner.groupKey)used.add(a.axis?.fieldKey);});
      try { F.closure(definition.terms,owner).filter(t=>t.key!==owner.key && t.groupKey===owner.groupKey).forEach(t=>(t.fieldKeys || []).forEach(f=>used.add(f))); } catch { return S.toast('기존 연결의 삭제된 참조를 먼저 수정하세요.'); }
      const remaining = (base?.bundles || []).flatMap(b=>b.fields || []).filter(f=>!used.has(f.key));
      if(!remaining.length)return S.toast('이 그룹의 남은 숫자 입력이 없습니다. 기존 연관식을 수정하세요.');
      const original=JSON.stringify(definition.terms), d=S.dialog('연관식 추가 · '+owner.name,'',{foot:'<button type="button" data-related-next class="primary">다음 · 연관식 설정</button>'});
      d.classList.add('pm-related-editor');
      const body=S.$('.pms-dialog-body',d);
      body.innerHTML=`<p>현재 항에 사용하지 않은 같은 그룹의 입력만 연결합니다. 기본 표 → 연관식 → 기존 수량·배율 순서로 계산합니다.</p><label>남은 입력<select data-related-field>${remaining.map(f=>`<option value="${e(f.key)}">${e(label(f))}</option>`).join('')}</select></label><label>적용 방식<select data-related-mode><option value="RAW">고객 입력값 그대로 적용</option><option value="FIXED">조건 없이 고정 금액·배율 적용</option><option value="RANGE">입력 구간을 나누어 +금액·×배율 적용</option></select></label><label>연산<select data-related-operation><option value="ADD">더하기</option><option value="MULTIPLY">곱하기</option></select></label><label>고정 금액 또는 배율<input data-related-value type="number" min="0" step="0.000001" placeholder="고정 방식일 때 직접 입력"></label><p data-related-error role="alert"></p>`;
      if(existingRelated) { S.$('[data-related-field]',body).value=existingRelated.axis.fieldKey; S.$('[data-related-mode]',body).value=existingRelated.above.useInput?'RAW':'FIXED'; S.$('[data-related-operation]',body).value=existingRelated.above.operation; S.$('[data-related-value]',body).value=existingRelated.above.value; }
      const updateRelatedMode=()=>{const mode=S.$('[data-related-mode]',body).value;S.$('[data-related-value]',body).disabled=mode!=='FIXED';S.$('[data-related-value]',body).closest('label').hidden=mode!=='FIXED';S.$('[data-related-operation]',body).disabled=mode==='RANGE';};S.$('[data-related-mode]',body).onchange=updateRelatedMode;updateRelatedMode();
      const relatedSignature=()=>JSON.stringify(['[data-related-field]','[data-related-mode]','[data-related-operation]','[data-related-value]'].map(selector=>S.$(selector,body).value));
      const initialRelated=relatedSignature();S.priceTable.guardClose(d,()=>relatedSignature()!==initialRelated);
      S.$('[data-related-next]',d).onclick=event=>S.run(event.currentTarget,async()=>{
        try {
          const checkedRelated=relatedSignature(), field=S.$('[data-related-field]',body).value, mode=S.$('[data-related-mode]',body).value;
          if(JSON.stringify(definition.terms)!==original)throw Error('가격식이 변경되었습니다. 다시 여세요.');
          const subset=[{...base,bundles:base.bundles.map(b=>({...b,fields:b.fields.filter(f=>f.key===field)})).filter(b=>b.fields.length)}];
          if(mode==='RANGE') {
            F.openAdjustment({term:owner,terms:definition.terms,groups:subset,sameGroupOnly:true,process:process(),diagnose:(terms,signal)=>api(product.id,'/diagnose?draft=true','POST',{definition:{...S.copy(definition),terms,schemaFingerprint:context.schemaFingerprint},answers:{}},signal),apply:terms=>{if(JSON.stringify(definition.terms)!==original)throw Error('작성 중 가격식이 변경되었습니다.');applyTerms(terms);},onTest:(term,terms,options)=>testSingleTerm(term,terms,options)});
            d.close();return;
          }
          const operation=S.$('[data-related-operation]',body).value;
          const value=mode==='RAW'?'1':S.priceTable.money(S.$('[data-related-value]',body).value,operation==='ADD'?2:6);
          const a={sameGroupOnly:true,key:adjustmentKey || key(),name:label(remaining.find(f=>f.key===field))+' 연관식',groupKey:owner.groupKey,axis:{fieldKey:field,operator:'LE',headers:['0']},values:[{operation,value,useInput:mode==='RAW'}],above:{operation,value,useInput:mode==='RAW'}};
          const next=definition.terms.map(t=>t.key===owner.key?{...S.copy(t),adjustments:adjustmentKey?(t.adjustments || []).map(item=>item.key===adjustmentKey?a:item):[...(t.adjustments || []),a]}:t);
          const validation=await api(product.id,'/diagnose?draft=true','POST',{definition:{...S.copy(definition),terms:next,schemaFingerprint:context.schemaFingerprint},answers:{}});
          if(!d.open)return;
          if(!validation.valid || validation.exhaustive===false)throw Error((validation.issues || []).filter(i=>i.severity==='ERROR').map(i=>i.message).join(' / ') || '전체 검증 미완료');
          if(JSON.stringify(definition.terms)!==original)throw Error('검사 중 가격식이 변경되었습니다.');
          if(relatedSignature()!==checkedRelated)throw Error('검사 중 연관식 입력이 변경되었습니다. 다시 검사하세요.');
          applyTerms(next);d.close();
        }catch(error){S.$('[data-related-error]',body).textContent=error.message;}
      });
    }
    function factorEditor(term, linkKey = null) {
      if (group(term.groupKey)?.control === "CHECKBOX") return S.toast("다중선택에는 가격 연관식을 등록할 수 없습니다.");
      const original = JSON.stringify(definition.terms);
      return F.open({ term, terms: definition.terms, groups: context.groups, process: process(), linkKey,
        diagnose: context.ready ? (terms, signal) => api(product.id, '/diagnose?draft=true', 'POST', { definition: { ...S.copy(definition), terms, schemaFingerprint: context.schemaFingerprint }, answers: {} }, signal) : null,
        apply: terms => { if (JSON.stringify(definition.terms) !== original) throw Error('배율 작성 중 계산식이 변경되었습니다. 현재 항을 다시 확인해 주세요.'); applyTerms(terms); S.toast('배율 연결을 반영했습니다. 실제 입력으로 계산한 뒤 저장하세요.'); },
        onTest: context.ready ? (term, terms, options) => testSingleTerm(term, terms, options) : null });
    }
    function adjustmentEditor(term, adjustmentKey = null) {
      const existing = term?.adjustments?.find(a=>a.key===adjustmentKey);
      if(existing && [...existing.values,existing.above].some(v=>v.useInput)) return relatedEditor(term,adjustmentKey);
      if (!term || F.isFactor(term)) return;
      const original = JSON.stringify(definition.terms);
      return F.openAdjustment({ term, terms: definition.terms, groups: existing?.sameGroupOnly ? context.groups.filter(g=>g.groupKey===term.groupKey).map(g=>({...g,bundles:g.bundles.map(b=>({...b,fields:b.fields.filter(f=>f.key===existing.axis.fieldKey)})).filter(b=>b.fields.length)})) : context.groups, process: process(), adjustmentKey, sameGroupOnly: !!existing?.sameGroupOnly,
        diagnose: context.ready ? (terms, signal) => api(product.id, '/diagnose?draft=true', 'POST', { definition: { ...S.copy(definition), terms, schemaFingerprint: context.schemaFingerprint }, answers: {} }, signal) : null,
        apply: terms => { if (JSON.stringify(definition.terms) !== original) throw Error('조건별 계산을 작성하는 동안 계산식이 변경되었습니다. 현재 항을 다시 확인해 주세요.'); applyTerms(terms); S.toast('이 항 안에 조건별 계산을 반영했습니다. 실제 입력으로 계산한 뒤 저장하세요.'); },
        onTest: context.ready ? (term, terms, options) => testSingleTerm(term, terms, options) : null });
    }
    const intermediateMoney = value => value == null ? '계산 대기' : decimalText(value, 100) + '원';
    function adjustmentValueText(value) {
      if (!value || value.value == null) return '계산값 미입력';
      if(value.useInput)return (value.operation==='ADD'?'＋ ':'× ')+'고객 입력값 × '+value.value;
      return value.operation === 'ADD' ? '＋ ' + money(value.value) : value.operation === 'MULTIPLY' ? '× ' + F.valueText(value.value) : '계산 방식 확인 필요';
    }
    function adjustmentCards(term) {
      if (F.isFactor(term) || !term.adjustments?.length) return '';
      return `<section class="pm-price-adjustments"><h4>이 항 안의 조건별 계산</h4><p class="pms-help">기본 계산 결과 → 아래 순서의 조건별 더하기·곱하기 → 기존 배율·수량 연결 순서로 계산합니다. 별도 금액 항으로 합산되지 않습니다. 질문을 보여주거나 입력을 제한하는 프로세스 분기와는 별개의 가격 조건입니다.</p><ol>${term.adjustments.map((adjustment, index) => {
        const source = group(adjustment.groupKey), field = source?.bundles?.flatMap(b => b.fields || []).find(f => f.key === adjustment.axis?.fieldKey), headers = adjustment.axis?.headers || [];
        const rows = headers.map((bound, i) => `${S.priceTable.range({ ...adjustment.axis, outsidePolicy: 'ERROR' }, i)} → ${adjustmentValueText(adjustment.values?.[i])}`);
        if (headers.length) rows.push(`${headers.at(-1)} ${S.priceTable.operator(adjustment.axis, headers.length - 1) === 'LE' ? '초과' : '이상'} → ${adjustmentValueText(adjustment.above)}`);
        return `<li><header><strong>${index + 1}. ${e(adjustment.name)}</strong><span>${e(source?.label || '현재 구성에서 삭제된 그룹')} · ${e(field ? label(field) : '현재 구성에서 삭제된 입력')}</span></header><details><summary>입력 구간별 더하기·곱하기 ${rows.length}개 확인</summary><ul>${rows.map(row => `<li>${e(row)}</li>`).join('')}</ul></details><div class="pms-actions"><button type="button" data-adjustment-move="${e(adjustment.key)}" data-adjustment-owner="${e(term.key)}" data-direction="-1" aria-label="${e(adjustment.name)} 계산 순서 위로" ${index ? '' : 'disabled'}>↑ 위로</button><button type="button" data-adjustment-move="${e(adjustment.key)}" data-adjustment-owner="${e(term.key)}" data-direction="1" aria-label="${e(adjustment.name)} 계산 순서 아래로" ${index < term.adjustments.length - 1 ? '' : 'disabled'}>↓ 아래로</button><button type="button" data-adjustment-edit="${e(adjustment.key)}" data-adjustment-owner="${e(term.key)}">조건·계산 수정</button><button type="button" data-adjustment-remove="${e(adjustment.key)}" data-adjustment-owner="${e(term.key)}">이 조건 계산 삭제</button></div></li>`;
      }).join('')}</ol><p class="pms-help">순서를 바꾸면 금액도 달라질 수 있습니다. 예: (100,000원 ＋ 30,000원) × 2 = 260,000원 / (100,000원 × 2) ＋ 30,000원 = 230,000원.</p></section>`;
    }
    function waitingForInput(result) {
      return result && !result.complete && !result.errors?.length && result.total == null;
    }
    function resultValue(row) { return row?.role === 'FACTOR' ? F.valueText(row.amount) : money(row.amount); }
    function breakdown(rows) {
      return `<div class="pm-price-breakdown">${(rows || []).map(row => `<article class="${row.role === 'FACTOR' ? 'is-factor' : ''}"><div><strong>${e(row.name)}</strong>${row.role === 'FACTOR' ? S.badge('배율 · 별도 합산 안 함', 'blue') : ''}<small>${e(row.explanation)}</small>${(row.adjustments || []).length || (row.multipliers || []).length ? `<details><summary>기본값·조건별 계산·배율 적용 순서 확인</summary><p>기본 계산 결과: ${e(row.role === 'FACTOR' ? F.valueText(row.baseAmount) : intermediateMoney(row.baseAmount))}</p>${(row.adjustments || []).map((adjustment, index) => `<section class="pm-price-adjustment-result"><strong>${index + 1}. ${e(adjustment.name)}</strong><small>입력 ${e(adjustment.input)} · ${e(adjustment.range || '')}</small><p>${e(intermediateMoney(adjustment.before))} ${e(adjustmentValueText(adjustment))} = ${e(intermediateMoney(adjustment.after))}</p></section>`).join('')}${(row.multipliers || []).map(link => `<p>이어서 × ${e(link.name)}: ${(link.terms || []).map(t => e(t.name) + ' ' + e(F.valueText(t.value))).join(link.aggregation === 'SUM' ? ' ＋ ' : ' × ')} → ${e(F.valueText(link.value))}</p>`).join('')}<p>이 항의 최종값: ${e(resultValue(row))}</p></details>` : ''}</div><span class="${row.applied ? '' : 'pm-muted'}">${row.applied ? e(resultValue(row)) : row.role === 'FACTOR' ? '현재 조건에서 미사용' : '적용 제외 · 0원'}</span></article>`).join('')}</div>`;
    }
    function renderDraftNotices() {
      const target = S.$('[data-price-draft-notices]', root); if (!target) return;
      target.innerHTML = draftWarnings.length ? `<details class="pms-help pm-price-draft-summary"><summary><strong>초안 확인 ${draftWarnings.length}건 · 항과 조건은 계속 작성할 수 있습니다</strong></summary><p>이 안내는 최근 초안 검사 결과입니다. 수정 후 전체 검사를 다시 실행하고 실제 입력으로 테스트한 뒤 저장하세요.</p>${diagnosticHtml({valid: true, exhaustive: true, scenarios: '초안 검사', issues: draftWarnings})}</details>` : '';
    }
    function renderTerms() {
      renderDraftNotices();
      S.$('[data-price-terms]', root).innerHTML = definition.terms.map((term, i) => `<article class="pm-price-term ${F.isFactor(term) ? 'is-factor' : ''}" data-price-term-card="${i}"><div class="pm-price-term-number">${i + 1}</div><div class="pm-price-term-copy"><header><strong>${e(term.name)}</strong>${S.badge(F.isFactor(term) ? '배율 전용 · 합계에 더하지 않음' : term.optionPricing ? '숫자 범위별 옵션 금액' : term.branches?.length ? '조건별 계산' : types[term.type], F.isFactor(term) ? 'blue' : '')}</header><p>${e(group(term.groupKey)?.label || '현재 구성에서 삭제된 그룹')} · ${term.branches?.length ? `조건별 계산 ${term.branches.length}개 · 한 항으로 합산` : e(summary(term))}</p>${term.optionTarget ? `<small>가격 대상: ${e(group(term.optionTarget.groupKey)?.label || term.optionTarget.groupKey)} · ${e(label(group(term.optionTarget.groupKey)?.choices?.find(c => c.key === term.optionTarget.choiceKey)))}</small>` : ''}${term.conditions?.length ? `<small>적용 조건 · ${term.match === 'ANY' ? '하나 이상 일치' : '모두 일치'}: ${term.conditions.map(c => e(conditionText(c))).join(term.match === 'ANY' ? ' 또는 ' : ' 그리고 ')}</small>` : ''}${F.isFactor(term) ? `<small>연결된 항: ${F.parents(definition.terms, term.key).map(t => e(t.name)).join(', ') || '없음 · 금액 항의 × 배율 연결에서 연결하세요'}</small>` : ''}${adjustmentCards(term)}<div class="pm-factor-links">${F.links(term).map(link => `<section><span>× <strong>${e(link.name)}</strong> · ${link.aggregation === 'SUM' ? '선택값의 합' : link.aggregation === 'PRODUCT' ? '선택값의 곱' : '조건에 맞는 값 1개'}</span><small>${link.termKeys.map(id => e(definition.terms.find(t => t.key === id)?.name || '삭제된 항')).join(' / ')}</small><div class="pms-actions"><button type="button" data-factor-edit="${e(term.key)}" data-factor-link="${e(link.key)}">연결 수정</button><button type="button" data-factor-unlink="${e(term.key)}" data-factor-link="${e(link.key)}">연결 해제</button></div></section>`).join('')}</div></div><div class="pms-actions"><button type="button" data-term-move="${i}" data-direction="-1" aria-label="${e(term.name)} 위로" ${i ? '' : 'disabled'}>↑</button><button type="button" data-term-move="${i}" data-direction="1" aria-label="${e(term.name)} 아래로" ${i < definition.terms.length - 1 ? '' : 'disabled'}>↓</button>${!F.isFactor(term) && group(term.groupKey)?.control !== "CHECKBOX" ? `<button type="button" data-term-adjustment="${e(term.key)}" ${(term.adjustments || []).length >= 20 ? 'disabled title="한 항의 조건별 계산은 최대 20개입니다. 아래 기존 조건을 수정하거나 삭제하세요."' : ''}>조건 연결(고급)</button>` : ''}${!F.isFactor(term) && group(term.groupKey)?.control === 'NUMBER' ? `<button type="button" data-term-branches="${e(term.key)}">조건별 가격표 ${term.branches?.length || 0}개</button>` : ''}${!F.isFactor(term) && group(term.groupKey)?.control !== "CHECKBOX" ? `<button type="button" data-term-related="${e(term.key)}" class="primary">연관식 추가</button>` : ''}${group(term.groupKey)?.control !== "CHECKBOX" ? `<button type="button" data-term-factor="${e(term.key)}">수량·배율 연결(고급)</button>` : ""}<button type="button" data-term-edit="${i}">조회·수정</button><button type="button" data-term-duplicate="${i}">복사해서 새 항</button><button type="button" data-term-remove="${i}" class="danger pm-icon-remove" aria-label="${e(term.name)} 항 삭제">×</button></div></article>`).join('') || '<div class="pms-empty">상단의 입력묶음 또는 ‘항 생성’을 눌러 금액 항을 만든 뒤 필요한 배율을 연결하세요.</div>';
      filterTerms();
      S.$$('[data-term-related]', root).forEach(b=>b.onclick=()=>relatedEditor(definition.terms.find(t=>t.key===b.dataset.termRelated)));
      S.$$('[data-term-branches]', root).forEach(b => b.onclick = () => branchEditor(definition.terms.find(t => t.key === b.dataset.termBranches)));
      S.$$('[data-term-edit]', root).forEach(b => b.onclick = () => editTerm(Number(b.dataset.termEdit)));
      S.$$('[data-term-factor]', root).forEach(b => b.onclick = () => factorEditor(definition.terms.find(t => t.key === b.dataset.termFactor)));
      S.$$('[data-term-adjustment]', root).forEach(b => b.onclick = () => { if (!b.disabled) return S.run(b, () => adjustmentEditor(definition.terms.find(t => t.key === b.dataset.termAdjustment))); });
      S.$$('[data-adjustment-edit]', root).forEach(b => b.onclick = () => S.run(b, () => adjustmentEditor(definition.terms.find(t => t.key === b.dataset.adjustmentOwner), b.dataset.adjustmentEdit)));
      S.$$('[data-adjustment-move]', root).forEach(b => b.onclick = () => {
        if (b.disabled) return;
        const next = S.copy(definition.terms), term = next.find(t => t.key === b.dataset.adjustmentOwner), index = term?.adjustments?.findIndex(a => a.key === b.dataset.adjustmentMove), direction = Number(b.dataset.direction), to = index + direction;
        if (!term || !Number.isInteger(index) || ![1, -1].includes(direction) || index < 0 || to < 0 || to >= term.adjustments.length) return;
        term.adjustments.splice(to, 0, term.adjustments.splice(index, 1)[0]); applyTerms(next);
        S.toast('이 항 안의 조건별 계산 순서를 변경했습니다. 전체 가격을 다시 계산해 주세요.');
      });
      S.$$('[data-adjustment-remove]', root).forEach(b => b.onclick = () => S.run(b, async () => {
        const original = JSON.stringify(definition.terms), owner = definition.terms.find(t => t.key === b.dataset.adjustmentOwner), adjustment = owner?.adjustments?.find(a => a.key === b.dataset.adjustmentRemove);
        if (!adjustment || !await S.confirm(`‘${owner.name}’ 안의 ‘${adjustment.name}’ 조건별 계산을 삭제하시겠습니까? 기본 가격표와 기존 배율 연결은 유지되며 최종 금액은 달라질 수 있습니다.`)) return;
        if (JSON.stringify(definition.terms) !== original) throw Error('삭제를 확인하는 동안 계산식이 변경되었습니다. 현재 내용을 다시 확인해 주세요.');
        const next = S.copy(definition.terms), term = next.find(t => t.key === owner.key); term.adjustments = term.adjustments.filter(a => a.key !== adjustment.key); applyTerms(next);
      }));
      S.$$('[data-factor-edit]', root).forEach(b => b.onclick = () => factorEditor(definition.terms.find(t => t.key === b.dataset.factorEdit), b.dataset.factorLink));
      S.$$('[data-factor-unlink]', root).forEach(b => b.onclick = () => S.run(b, async () => {
        const before = definition.terms, next = F.removeLink(before, b.dataset.factorUnlink, b.dataset.factorLink), removed = before.filter(t => !next.some(x => x.key === t.key));
        if (!await S.confirm('이 배율 연결을 해제하면 원래 항에 해당 배율을 곱하지 않습니다.' + (removed.length ? '\n다른 항에서 사용하지 않는 배율도 함께 정리합니다: ' + removed.map(t => t.name).join(', ') : '') + '\n전체 가격을 다시 테스트해야 합니다. 해제하시겠습니까?')) return;
        applyTerms(next);
      }));
      S.$$('[data-term-duplicate]', root).forEach(b => b.onclick = () => {
        if (definition.terms.length >= 100) return S.toast('금액·배율 항은 합쳐서 최대 100개입니다.');
        const original = definition.terms[Number(b.dataset.termDuplicate)];
        if (F.isFactor(original)) return S.toast('배율 항은 금액 항의 ‘× 배율·수량 연결’에서 새로 만들거나 기존 배율로 연결하세요.');
        const copied = F.duplicate(definition.terms, original);
        if (definition.terms.length + copied.terms.length > 100) return S.toast('연결된 배율까지 복사하면 전체 100개를 초과합니다.');
        editTerm(null, '', '', copied.term, null, { extraTerms: copied.terms.filter(t => t.key !== copied.term.key) });
      });
      S.$$('[data-term-remove]', root).forEach(b => b.onclick = () => S.run(b, async () => {
        const term = definition.terms[Number(b.dataset.termRemove)], next = F.removeTerm(definition.terms, term.key), removed = definition.terms.filter(t => !next.some(x => x.key === t.key));
        if (!await S.confirm('계산식에서 삭제할 항: ' + removed.map(t => t.name).join(', ') + '\n다른 항에서 사용하는 배율은 유지됩니다. 실제 가격을 다시 테스트해야 합니다. 삭제하시겠습니까?')) return;
        applyTerms(next);
      }));
      S.$$('[data-term-move]', root).forEach(b => b.onclick = () => { const from = Number(b.dataset.termMove), to = from + Number(b.dataset.direction); definition.terms.splice(to, 0, definition.terms.splice(from, 1)[0]); changed(); renderTerms(); });
    }
    function updateFormula() {
      const box = S.$('[data-price-formula]', root); if (!box) return;
      const prices = definition.terms.filter(t => !F.isFactor(t));
      box.innerHTML = '<strong>최종가격</strong><span>＝</span>' + (prices.map((t, i) => `${i ? '<span class="pm-price-plus">＋</span>' : ''}<span class="pm-price-formula-term">${e(F.formula(t, definition.terms, true))}</span>`).join('') || '<span class="pm-muted">금액 항을 추가해 주세요. 배율 항은 단독으로 합산하지 않습니다.</span>');
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
        S.bindEqualInputs(inputBox, q, process(), () => { collectAnswers(); return answers; });
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
      if (!context.ready) throw Error("프로세스를 검증 후 등록완료한 다음 계산해 주세요.");
      F.validateLinks(definition.terms);
      cancelTest(); collectAnswers(); S.$("[data-price-test-errors]", root).innerHTML = "";
      const serial = ++sequence, draft = S.copy(answers), termSignature = JSON.stringify(definition.terms);
      const next = await api(product.id, "/evaluate", "POST", { definition: { ...definition, schemaFingerprint: context.schemaFingerprint }, answers: draft });
      if (serial !== sequence || !active || termSignature !== JSON.stringify(definition.terms)) return false;
      const states = new Map((next.evaluation?.questions || []).map(state => [state.question.key, state]));
      const unpriced = S.pricingParticipation(context.groups, definition.terms).filter(row => {
        if (!row.used.length) return true;
        const state = states.get(row.group.groupKey);
        return state?.visible && !(state.errors || []).length
          && row.fields.some(f => state.allowed.includes(f.key))
          && !row.used.some(f => state.allowed.includes(f.key));
      });
      result = unpriced.length ? { ...next, complete: false, total: null, errors: [...(next.errors || []), ...unpriced.map(S.pricingInputBindingMessage)] } : next;
      if (next.evaluation) { evaluation = next.evaluation; answers = mergeTestAnswers(evaluation, draft); renderTest(); }
      definition.testAnswers = S.copy(answers);
      S.$("[data-price-result]", root).innerHTML = `<section class="pm-price-total ${result.complete ? "is-complete" : ""}"><span>${result.complete ? "테스트 최종가격" : waitingForInput(result) ? "다음 답변을 기다리고 있습니다." : "계산을 완료할 수 없습니다."}</span><strong>${result.complete ? money(result.total) : waitingForInput(result) ? "입력 대기" : "입력·기준표 확인 필요"}</strong></section>${(result.errors || []).length ? `<div class="pms-alert">${result.errors.map((text) => `<p>${e(text)}</p>`).join("")}</div>` : ""}${unpriced.map(row => `<button type="button" data-diagnostic-group="${e(row.group.groupKey)}">${e(row.group.label)}에 가격 항 등록</button>`).join("")}${breakdown(result.terms)}`;
      return !!result.complete;
    }

    async function testSingleTerm(term, sourceTerms = definition.terms, options = {}) {
      if (!context.ready) throw Error("프로세스를 등록완료한 뒤 실제 입력 테스트를 진행해 주세요.");
      const testTerms = F.testClosure(sourceTerms, term);
      let localAnswers = S.copy(answers), localEvaluation = null, calculation = null, localSequence = 0;
      const dialog = S.dialog("연결된 계산식 입력 테스트 · " + term.name, '<p class="pms-help">입력 항목을 불러오는 중입니다.</p>', { foot: '<button type="button" data-test-term-run class="primary">분기·입력 적용 후 이 항 계산</button><button type="button" data-test-use disabled>테스트값을 메인 화면에 가져오기</button>' });
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
        area.innerHTML = `<div class="pm-price-review"><strong>${e(F.formula(term, sourceTerms, true))} · ${term.branches?.length ? `조건별 계산 ${term.branches.length}개 · 한 항으로 합산` : e(summary(term))}</strong><p>${(term.conditions || []).length ? term.conditions.map(c => e(conditionText(c))).join(term.match === 'ANY' ? ' 또는 ' : ' 그리고 ') : '추가 조건 없음'}</p></div><p class="pms-help">이 항과 여기에 연결된 배율을 함께 계산합니다. 다른 독립 금액 항은 합산하지 않습니다. 앞선 답변을 바꾼 후 계산 버튼을 누르면 뒤 질문·입력 제한이 갱신됩니다. 여기서 입력한 테스트값은 가격표나 가격식에 저장되지 않습니다.</p><div class="pm-price-test-grid">${localEvaluation.questions.filter(state => state.visible && !state.question.fixed).map(state => {
          const q = { ...state.question, choices: (state.question.choices || []).filter(c => state.allowed.includes(c.key)), fields: (state.question.fields || []).filter(f => state.allowed.includes(f.key)) };
          return `<article class="pm-price-test-question"><header><strong>${e(q.question || label(q))}</strong>${state.required ? S.badge('필수') : ''}</header><div data-single-answer="${e(q.key)}">${S.inputAnswer(q, localAnswers[q.key] || state.answer, false, assetMap)}</div>${state.errors?.length ? `<div class="pm-price-input-errors">${state.errors.map(x => `<p>${e(x)}</p>`).join('')}</div>` : ''}${(state.notices || []).map(x => `<p class="pms-help">${e(x)}</p>`).join('')}</article>`;
        }).join('')}</div><div data-single-result role="status">${calculation ? `<div class="pm-price-total"><span>${calculation.complete ? result?.applied ? '이 항의 계산 금액' : '이 항은 적용되지 않음' : waitingForInput(calculation) ? '다음 답변을 기다리고 있습니다.' : '입력·조건 확인 필요'}</span><strong>${calculation.complete ? result?.applied ? e(resultValue(result)) : F.isFactor(term) ? '현재 조건에서 미사용' : '0원' : waitingForInput(calculation) ? '입력 대기' : '계산 미완료'}</strong></div><p>${e(result?.explanation || '')}</p>${breakdown(calculation.terms)}${calculation.errors.map(x => `<p class="pms-alert">${e(x)}</p>`).join('')}` : '<p class="pms-help">입력값과 조건을 확인한 뒤 계산해 주세요.</p>'}</div>`;
        S.$('[data-test-use]', dialog).disabled = !calculation?.complete;
        S.$$('[data-single-answer]', area).forEach(box => {
          const q = localEvaluation.questions.find(s => s.question.key === box.dataset.singleAnswer).question;
          S.bindEqualInputs(box, q, process(), () => { collect(); return localAnswers; });
          const invalidate = () => { localSequence++; calculation = null; S.$('[data-test-use]', dialog).disabled = true; S.$('[data-single-result]', area).textContent = '입력이 변경되었습니다. 다시 계산해 주세요.'; };
          box.addEventListener('input', invalidate); box.addEventListener('change', invalidate);
          S.answerFileEvents(box, q, S.copy(localAnswers[q.key] || {}), assetMap, { onChange: a => { localAnswers[q.key] = S.copy(a); invalidate(); }, onError: error => S.toast(error.message), isCurrent: () => dialog.open && box.isConnected });
        });
        S.bindMedia?.(area);
      }
      S.$('[data-test-term-run]', dialog).onclick = event => S.run(event.currentTarget, async () => {
        if (!localEvaluation) return;
        collect(); const draft = S.copy(localAnswers), serial = ++localSequence;
        const next = await api(product.id, '/evaluate', 'POST', { definition: { schemaVersion: 1, schemaFingerprint: context.schemaFingerprint, terms: testTerms, testAnswers: {} }, answers: draft });
        if (!dialog.open || serial !== localSequence) return;
        calculation = next; if (next.evaluation) { localEvaluation = next.evaluation; localAnswers = mergeTestAnswers(next.evaluation, draft); } render(); options.onCalculated?.(next);
      });
      S.$('[data-test-use]', dialog).onclick = () => { if (!calculation?.complete) return; cancelTest(); answers = S.copy(localAnswers); evaluation = localEvaluation; result = null; renderTest(); clearResult('항 테스트의 입력값을 가져왔습니다. 모든 항을 반영한 뒤 전체 가격을 다시 계산해 주세요.'); dialog.close(); };
      try { localEvaluation = await S.request(`/products/${product.id}/evaluate`, 'POST', { answers: localAnswers }); if (dialog.open) { localAnswers = mergeTestAnswers(localEvaluation, localAnswers); render(); } }
      catch (error) { dialog.close(); throw error; }
    }

    function editTerm(index = null, selectedGroup = "", selectedBundle = "", draft = null, onDraft = null, scope = {}) {
      if (index != null && definition.terms[index]?.branches?.length) return branchEditor(definition.terms[index]);
      if (draft?.branches?.length && !scope.conditionsOnly && !scope.branch) {
        const extra = S.copy(scope.extraTerms || []);
        return branchEditor(draft, onDraft || (next => { applyTerms(definition.terms.concat(extra, next)); }), extra);
      }

      let mode = null;
      let term = draft ? S.copy(draft) : index == null ? { key: key(), name: "", groupKey: selectedGroup || pricingGroups()[0]?.groupKey || "", bundleKey: selectedBundle || null, fieldKeys: [], type: "FIXED", amount: null, rates: {}, xAxis: null, yAxis: null, values: [], conditions: [], match: "ALL", choiceKeys: [] } : S.copy(definition.terms[index]);
      let stage = scope.conditionsOnly ? 2 : !draft && index == null || !supported(group(term.groupKey)) || (group(term.groupKey)?.control === "NUMBER" && !group(term.groupKey)?.bundles?.some((b) => b.key === term.bundleKey)) ? 0 : 1, importing = false, conditionDraft = null;
      const d = S.dialog(scope.conditionsOnly ? "항·분기 적용 조건 설정" : index == null ? "가격계산 항 생성" : "가격계산 항 조회·수정", "", { foot: '<span data-term-footer-status role="status"></span><button type="button" data-term-prev>이전</button><button type="button" data-term-next class="primary">다음 · 계산 방식 설정</button><button type="button" data-term-commit class="primary" hidden>항 반영</button>' });
      d.classList.add("pm-price-editor"); const body = S.$(".pms-dialog-body", d);
      if (scope.conditionsOnly && !scope.singleConditions) term.match = "ALL";
      const factor = () => F.isFactor(term), valueScale = () => factor() ? 6 : 2;
      const g = () => group(term.groupKey), bundle = () => g()?.bundles?.find((b) => b.key === term.bundleKey);
      const chooseMode = () => !scope.locked && !factor() && g()?.control === "NUMBER";
      if (stage === 1 && chooseMode() && !scope.focusAxis) stage = "mode";
      if (term.optionTarget) scope = { ...scope, requiredCondition: { groupKey: term.optionTarget.groupKey, fieldKey: null, operator: 'EQ', choiceKeys: [term.optionTarget.choiceKey], text: null, lower: null, upper: null, lowerInclusive: true, upperInclusive: true } };
      const missingOptionTarget = () => term.optionTarget && !group(term.optionTarget.groupKey)?.choices?.some(c => c.key === term.optionTarget.choiceKey);
      const allFields = () => bundle()?.fields || [], fields = () => allFields().filter((f) => term.fieldKeys.includes(f.key));
      const linkedChoice = () => fields().some(f => f.key === '__numeric_value');
      const externalFields = () => process().questions.filter(q => q.control === 'NUMBER' && !q.fixed)
        .flatMap(q => q.fields.map(f => ({ ...f, key: q.key + ':' + f.key, labels: { management: label(q) + ' · ' + label(f) } })));
      const matrixFields = () => term.type === 'MATRIX_2D' && linkedChoice() ? [...fields(), ...externalFields()] : fields();
      const unusedFields = b => b.fields.filter(f => !definition.terms.some(t => t.key !== term.key && F.isFactor(t) === factor() && t.groupKey === term.groupKey && !t.optionTarget && t.type.startsWith('MATRIX') && t.fieldKeys.includes(f.key)));
      function selectBundle() {
        if (g()?.control !== "NUMBER") { term.bundleKey = null; term.fieldKeys = []; term.type = "FIXED"; return; }
        if (!bundle()) term.bundleKey = g().bundles[0]?.key || "";
        if (index == null && !draft && !unusedFields(bundle() || { fields: [] }).length) term.bundleKey = g().bundles.find(b => unusedFields(b).length)?.key || term.bundleKey;
        const available = index == null && !draft ? unusedFields(bundle() || { fields: [] }) : allFields();
        term.fieldKeys = available.slice(0, 2).map(f => f.key);
      }
      if (index == null && !draft) { selectBundle(); term.name = (g()?.label || "가격") + " 항"; }
      const tableUi = { method: factor() ? "direct" : "excel" }, initialTerm = S.priceTable.snapshot(term);
      const history = S.priceTable.history(() => term, value => { term = value; conditionDraft = null; S.priceTable.resetDraft(tableUi); });
      let preflightTimer, preflightBusy = false, preflightAgain = false, preflightSequence = 0, preflightSignature = "";
      d.addEventListener("close", () => { clearTimeout(preflightTimer); preflightSequence++; });
      let focusSequence = 0;
      body.addEventListener("focusin", () => { focusSequence++; });
      const recordInput = event => {
        if (event.target.matches('[data-term-name],[data-term-amount],[data-term-rate],[data-term-choice],[data-matrix-header],[data-matrix-price],[data-bound-operator],[data-term-match],[data-range-bound],[data-range-op],[data-range-amount],[data-range-above],[data-range-field]')) { history.record(event.type === 'input' ? 'input-' + focusSequence : null); updateHistory(); }
      };
      body.addEventListener("input", recordInput); body.addEventListener("change", recordInput);
      body.addEventListener("input", schedulePreflight); body.addEventListener("change", schedulePreflight);
      S.priceTable.guardClose(d, () => initialTerm !== S.priceTable.snapshot(term) || S.priceTable.pending(tableUi) || !!conditionDraft);
      function updateHistory() {
        const undo = S.$('[data-price-undo]', body), redo = S.$('[data-price-redo]', body);
        if (undo) undo.disabled = importing || !history.canUndo(); if (redo) redo.disabled = importing || !history.canRedo();
      }
      function pendingTable() {
        if (!S.priceTable.pending(tableUi)) return false;
        editorError("작성한 일괄 기준값을 먼저 미리보기로 반영하거나 ‘현재 표의 기준으로 되돌리기’를 눌러 주세요.", "[data-build-table]"); return true;
      }
      const requiredCondition = c => scope.requiredCondition && c.groupKey === scope.requiredCondition.groupKey && c.operator === "EQ" && c.choiceKeys?.length === 1 && c.choiceKeys[0] === scope.requiredCondition.choiceKeys[0];
      function resetPrice(type) {
        term.optionPricing = null; term.type = type; term.amount = null; term.rates = Object.fromEntries(fields().map((f) => [f.key, null]));
        term.xAxis = type.startsWith("MATRIX") ? { fieldKey: term.fieldKeys[0] || "", operator: "LE", headers: [null], operators: [null] } : null;
        term.yAxis = type === "MATRIX_2D" ? { fieldKey: term.fieldKeys[1] || "", operator: "LE", headers: [null], operators: [null] } : null;
        term.values = type.startsWith("MATRIX") ? [[null]] : []; tableUi.matrixStep = 0; S.priceTable.resetDraft(tableUi);
      }
      function hasPriceData() {
        if (term.optionPricing) return true;
        if (term.type === "FIXED") return term.amount != null && compareDecimal(term.amount, 0) !== 0;
        if (term.type === "MULTIPLY") return Object.values(term.rates || {}).some((value) => value != null && compareDecimal(value, 0) !== 0);
        return (term.values || []).some((row) => row.some((value) => value != null)) || [term.xAxis, term.yAxis].some((axis) => axis?.headers?.some((value) => value != null));
      }
      function draw() {
        S.priceTable.remember(body, tableUi);
        history.record();
        const isNumber = g()?.control === "NUMBER";
        if (stage === "mode") {
          body.innerHTML = `<h3>${e(term.name)} · 분기 여부 선택</h3><div class="pm-price-type-grid">${[["single","조건 없이 계산식 하나","연결한 입력에 같은 계산식을 적용합니다."],["branches","조건별로 계산식 등록","대리석 종류 등 답변을 나누고 분기마다 계산 방식을 선택합니다."]].map(([value,title,help]) => `<label class="pm-price-type-choice"><input type="radio" name="price-mode" value="${value}" ${mode === value ? "checked" : ""}><strong>${title}</strong><small>${help}</small></label>`).join("")}</div><div data-term-errors role="alert"></div>`;
          const next = S.$("[data-term-next]", d);
          {
            const edit = document.createElement("button");
            edit.type = "button";
            edit.textContent = term.conditions?.length ? "항 전체 적용 조건 확인·수정" : "항 전체 적용 조건 추가 (선택)";
            body.append(edit);
            edit.onclick = () => editTerm(null, "", "", term, next => {
              term.conditions = next.conditions; term.match = next.match; draw();
            }, {locked:true, conditionsOnly:true, singleConditions:true});
          }
          const update = () => {
            next.disabled = !mode;
            next.textContent = mode === "branches" ? "다음 · 분기 조건 구성" : "다음 · 계산 방식 설정";
          };
          S.$$('[name="price-mode"]', body).forEach(input => input.onchange = () => { mode = input.value; update(); });
          S.$("[data-term-prev]", d).hidden = false;
          S.$("[data-term-commit]", d).hidden = true;
          S.$("[data-term-footer-status]", d).textContent = "";
          next.hidden = false;
          update();
          return;
        }
        S.$("[data-term-next]", d).disabled = false;
        const singleReview = chooseMode() && mode === "single";
        const steps = scope.branch ? ["분기 조건", "계산 방식·금액", "분기 목록"] :
          chooseMode() ? ["등록 항목 연결", "분기 여부", "계산 방식·금액", "확인"] :
          ["등록 항목 연결", "계산 방식·금액", "적용 조건·확인"];
        const active = chooseMode() && stage > 0 ? stage + 1 : stage;
        body.innerHTML = `<ol class="pm-stepper">${steps.map((name,i)=>`<li class="${active === i ? "active" : ""}">${i+1}. ${name}</li>`).join("")}</ol><div class="pms-actions pm-price-history"><button type="button" data-price-undo>↶ 실행 취소</button><button type="button" data-price-redo>↷ 다시 실행</button><small>이 창에서의 변경을 되돌립니다. 항 반영 전까지 원본은 유지됩니다.</small></div><div data-term-errors role="alert"></div>${F.links(term).length ? `<p class="pms-help">유지되는 배율 연결: ${F.links(term).map(link => e(link.name)).join(', ')}. 공유 배율의 값을 수정하면 연결된 모든 항에 반영됩니다.</p>` : ''}${missingOptionTarget() ? '<div class="pms-alert">이 항의 가격 대상 옵션이 현재 구성에 없습니다. 필요한 항인지 확인하고 연결을 해제한 후 질문·옵션·조건을 다시 지정하세요.<button type="button" data-clear-missing-option-target>삭제된 옵션 연결 해제</button></div>' : ""}${factor() ? '<div class="pms-help"><strong>배율 전용 항</strong><p>여기에 입력한 값은 원화가 아닌 배율입니다. 연결된 항에 곱하며 최종가격에 별도로 더하지 않습니다. 1은 변화 없음, 0은 연결 항의 결과를 0으로 만듭니다.</p></div>' : ''}${stage === 0 ? sourceHtml() : stage === 1 ? (factor() ? factorPriceHtml(isNumber) : priceHtml(isNumber)) : singleReview ? `<div class="pm-price-review"><strong>${e(term.name)}</strong><p>${e(summary(term))}</p><p>${(term.conditions || []).map(c=>e(conditionText(c))).join(term.match === "ANY" ? " 또는 " : " 그리고 ") || "연결한 입력이 활성화되면 적용"}</p></div><button type="button" data-term-test>이 항만 테스트</button>` : conditionsHtml()}${stage > 0 && !scope.conditionsOnly ? '<div data-term-preflight class="pm-price-preflight" role="status" aria-live="polite"></div>' : ""}`;
        S.$("[data-term-prev]", d).hidden = scope.conditionsOnly || stage === 0 || scope.locked && stage === 1;
        S.$("[data-term-next]", d).hidden = stage === 2; S.$("[data-term-next]", d).textContent = stage === 0 ? "다음 · 계산 방식 설정" : "다음 · 적용 조건 확인";
        S.$("[data-term-commit]", d).hidden = stage !== 2;
        S.$("[data-term-commit]", d).textContent = scope.conditionsOnly ? "적용 조건 반영" : "항 반영";
        S.$("[data-term-footer-status]", d).textContent = "항 반영 후 화면 아래의 저장 버튼으로 최종 등록합니다.";
        if (stage === 1 && term.type.startsWith('MATRIX')) {
          const step = tableUi.matrixStep || 0;
          S.$('[data-term-next]', d).textContent = ['다음 · 기준·금액 입력', '다음 · 적용 범위', '다음 · 가격 확인', '다음 · 적용 조건 확인'][step];
          S.$('[data-term-prev]', d).hidden = scope.locked && step === 0;
          S.$('[data-term-footer-status]', d).textContent = `가격표 ${step + 1}/4 · ${['자료를 가져오면 기준 입력으로 이동합니다.', '빈 기준·금액은 입력해야 합니다.', '주문 범위와 가격표 범위를 구분해 확인하세요.', '원하는 값으로 확인 후 조건 설정으로 진행하세요.'][step]}`;
        }
        if (stage === 1 && term.type.startsWith('MATRIX') && tableUi.matrixStep > 0) {
          const first = S.$('.pm-price-source-summary', body), last = S.$('.pm-price-type-grid', body);
          if (first && last) {
            const compact = document.createElement('details'); compact.className = 'pm-price-completed-source';
            compact.innerHTML = `<summary>${e(term.name)} · ${e(types[term.type])} · 연결·방식 수정</summary>`;
            first.before(compact); let node = first;
            while (node) { const next = node.nextSibling; compact.append(node); if (node === last) break; node = next; }
          }
        }
        if (stage === 0 && chooseMode()) S.$("[data-term-next]", d).textContent = "다음 · 분기 여부 선택";
        if (singleReview && stage === 1 && (!term.type.startsWith("MATRIX") || tableUi.matrixStep === 3)) S.$("[data-term-next]", d).textContent = "다음 · 계산식 확인";
        if (scope.branch && stage === 1 && (!term.type.startsWith("MATRIX") || tableUi.matrixStep === 3)) {
          S.$("[data-term-next]", d).hidden = true;
          const commit = S.$("[data-term-commit]", d);
          commit.hidden = false;
          commit.textContent = "계산식 반영 · 분기 목록으로";
        }
        bindEditor(); bindMatrixFlow(); updateHistory();
        schedulePreflight(true);
      }
      function sourceHtml() {
        return `<div class="pms-form-grid two">${field("항 이름 (예: 사이즈 추가금)", 'data-term-name type="text" maxlength="120"', term.name)}<label><span>이 제품의 가격계산 영향 그룹</span><select data-term-group>${!supported(g()) ? '<option value="" disabled selected>그룹을 선택해 주세요.</option>' : ""}${pricingGroups().map((x) => opt(x.groupKey, x.label, term.groupKey)).join("")}</select></label></div>${g()?.control === "NUMBER" ? `<h3 class="pm-price-editor-subtitle">1. 입력묶음 가져오기</h3><p class="pms-help">먼저 묶음을 고른 뒤 계산할 숫자를 선택하세요. 한 숫자는 1차원 표, 두 숫자는 2차원 표에 연결할 수 있습니다.</p><details class="pm-price-inline-help"><summary>남은 숫자나 기존 가격표를 사용하려면</summary><p>W·H 표에 D의 금액도 더하려면 D를 별도 항에 연결합니다. 새 항은 미사용 숫자를 우선 선택합니다. 이미 등록한 표를 바꿀 때는 해당 항의 조회·수정을 사용하세요. 세 숫자를 함께 쓰는 3차원 엑셀표는 지원하지 않습니다.</p></details><div class="pm-price-source-bundles">${g().bundles.map((b) => `<label class="pm-price-bundle-choice"><input type="radio" name="term-bundle" value="${e(b.key)}" ${b.key === term.bundleKey ? "checked" : ""}><span><strong>${b.fields.length > 1 ? `묶음 · ${b.fields.length}개 입력` : "독립 · 1개 입력"} — ${e(b.label || b.fields.map(label).join(" · "))}</strong><small>${b.fields.map((f) => e(label(f)) + (f.unit ? " (" + e(f.unit) + ")" : "")).join(" · ")}</small></span></label>`).join("")}</div>${!g().choices?.some(c => c.numericValue) && g().bundles.every(b => b.fields.length < 2) ? `<p class="pms-help"><b>현재는 독립 입력만 있어 2차원을 선택할 수 없습니다.</b> 프로세스에서 두 필드 체크 → ‘선택 필드 그룹화’ → ‘이 질문 완성’ → ‘검증 후 등록완료’ 후 이 화면을 다시 여세요. <a href="/admin/product-master/products/${product.id}/process" target="_blank" rel="noopener">프로세스 확인</a></p>` : ""}<h3 class="pm-price-editor-subtitle">2. 이 항에서 사용할 숫자 항목 · 1개는 1차원 / 2개는 2차원</h3><div class="pms-check-grid">${allFields().map((f) => `<label class="pms-check"><input type="checkbox" data-term-field value="${e(f.key)}" ${term.fieldKeys.includes(f.key) ? "checked" : ""} ${!term.fieldKeys.includes(f.key) && fields().length >= 2 ? 'disabled title="선택한 두 항목 중 하나를 해제하면 이 항목을 선택할 수 있습니다."' : ""}>${e(label(f))}${f.unit ? " (" + e(f.unit) + ")" : ""}${!term.optionTarget && !unusedFields(bundle()).some(x => x.key === f.key) ? " · 다른 가격표에서 사용 중" : ""}</label>`).join("")}</div><p class="pms-help pm-price-axis-selection" role="status">선택: ${e(fields().map(label).join(" · ") || "없음")} · ${fields().length}/2개. ${fields().length >= 2 ? "두 항목을 선택하여 나머지 선택을 잠갔습니다. 바꾸려면 선택한 항목 하나를 먼저 해제하세요." : "하나를 선택하면 1차원, 두 개를 선택하면 2차원 가격표를 만들 수 있습니다."} 남은 숫자는 별도의 1차원 금액으로 더하거나, 이 가격표에서 결정된 금액에 곱할 배율로 연결할 수 있습니다.</p>` : `<fieldset class="pm-price-choice-scope"><legend>금액을 적용할 옵션</legend><p class="pms-help">이 항은 선택 옵션의 고정금액 또는 앞 숫자 범위에 따른 옵션 금액을 설정합니다. 옵션별 곱셈·가격표는 메인 화면의 ‘옵션별 일괄 설정’에서 숫자 입력과 연결하세요. 여러 옵션의 고정금액도 일괄 설정할 수 있습니다. 고정금액만 사용할 때는 미지정 시 어떤 옵션이든 한 번 적용됩니다. 숫자 범위별 가격은 적용할 옵션을 반드시 지정하세요.</p><div class="pms-check-grid">${(g()?.choices || []).map((c) => `<label class="pms-check"><input type="checkbox" data-term-choice value="${e(c.key)}" ${(term.choiceKeys || []).includes(c.key) ? "checked" : ""}>${e(label(c))}</label>`).join("")}</div></fieldset>`}`;
      }
      function priceHtml(isNumber) {
        const allowed = isNumber ? ["FIXED", "MULTIPLY", ...(fields().length === 1 ? ["MATRIX_1D"] : []), ...((fields().length === 2 || linkedChoice() && externalFields().length) ? ["MATRIX_2D"] : [])] : ["FIXED"];
        return `<div class="pm-price-source-summary"><strong>${e(g()?.label)}</strong><span>${e(fields().map(label).join(" · ") || "선택형 옵션")}</span>${scope.branch ? '<small>연결한 숫자와 조건은 유지하며, 이 조건의 계산 방식은 아래에서 선택합니다.</small>' : scope.allowTypeChange ? '<small>연결한 숫자는 유지하며, 계산 방식은 아래에서 선택합니다.</small>' : scope.locked ? '<small>연결 항목·방식은 일괄 설정 화면에서 변경합니다.</small>' : '<button type="button" data-source-edit>그룹·묶음 변경</button>'}</div>${field("항 이름", 'data-term-name type="text" maxlength="120"', term.name)}${isNumber ? `<details class="pm-price-limits"><summary>연결한 숫자의 단위·기본 허용 범위 확인</summary><ul>${fields().map(f => `<li><strong>${e(label(f))}</strong> · ${e(f.unit || "단위 없음")} · 최소 ${e(f.min ?? "제한 없음")} / 최대 ${e(f.max ?? "제한 없음")} · 입력 간격 ${e(f.step ?? "자유")}</li>`).join("")}</ul><p class="pms-help">프로세스 분기에 따라 범위가 더 좁아질 수 있습니다. 실제 입력 테스트와 최종 저장 검증에서 분기별 제한을 확인합니다.</p></details>` : ""}${isNumber && fields().length > 2 ? '<p class="pms-help">가격표 연동은 1개 또는 2개의 입력을 사용합니다. 위 ‘그룹·묶음 변경’에서 사용할 항목만 선택하세요.</p>' : ""}<div class="pm-price-type-grid" ${scope.locked && !scope.branch && !scope.allowTypeChange ? "hidden" : ""}>${allowed.map((type) => `<label class="pm-price-type-choice ${type === term.type ? "selected" : ""}"><input type="radio" name="term-type" value="${type}" ${type === term.type ? "checked" : ""}><strong>${e(linkedChoice() && type === "MATRIX_2D" ? "2차원으로 등록 · 다른 질문의 숫자 연결" : !isNumber && type === "FIXED" ? "고정·숫자 범위별 금액" : types[type])}</strong><small>${{ FIXED: "실제 입력값에 관계없이 한 번 더할 금액", MULTIPLY: "각 입력값에 단가를 곱한 뒤 더하기", MATRIX_1D: "예: 높이 500 이하 0원, 600 이하 20,000원", MATRIX_2D: "예: 가로·세로를 X·Y축으로 연결해 교차 금액 찾기" }[type]}</small></label>`).join("")}</div>${term.type === "FIXED" ? `<div class="pms-form-grid two">${field(term.optionPricing ? "범위별 가격을 끌 때 사용할 고정금액 (원)" : "더할 고정 금액 (원)", 'data-term-amount type="number" step="0.01"', term.amount, 'min="0"')}</div>${!isNumber ? `<fieldset class="pm-price-choice-scope"><legend>적용할 선택 옵션</legend><p class="pms-help">아무것도 지정하지 않으면 이 질문에 어떤 옵션이든 선택되었을 때 고정금액을 한 번 적용합니다. 범위별 가격을 사용하려면 옵션을 지정하세요. 여러 옵션이 동시에 선택되어도 이 항은 한 번만 더해집니다.</p><div class="pms-check-grid">${g().choices.map((c) => `<label class="pms-check"><input type="checkbox" data-term-choice value="${e(c.key)}" ${(term.choiceKeys || []).includes(c.key) ? "checked" : ""}><span>${e(label(c))}</span></label>`).join("")}</div></fieldset>` : '<p class="pms-help">이 입력묶음이 현재 분기에 있고 허용된 경우에만 고정 금액을 더합니다.</p>'}` : term.type === "MULTIPLY" ? `<p class="pms-help pm-price-quantity-note">수량 최대값을 미설정하면 프로세스가 허용한 큰 수량까지 검증합니다. 실제 주문 가능한 최대 수량·입력 간격을 확인하세요. 조건별·합계 제한이 있으면 함께 적용하며, 최대값이 없다는 이유만으로 등록을 막지는 않습니다.</p><p class="pms-help">${e(fields().map((f) => label(f) + " × 단가").join(" ＋ "))} 형태로 계산합니다. 각 숫자에 해당 단가를 곱한 뒤 합산하며, 가로 × 세로의 면적 곱셈은 아닙니다. 제외할 입력은 단가를 0으로 두세요.</p><div class="pms-form-grid two">${fields().map((f) => field(label(f) + " 1" + (f.unit || "단위") + "당 단가 (원)", `data-term-rate="${e(f.key)}" type="number" min="0" step="0.000001"`, term.rates?.[f.key] ?? "")).join("")}</div>` : matrixHtml()}${!isNumber && term.type === "FIXED" ? S.optionRanges.html(term, process().questions) : ""}`;
      }
      function factorPriceHtml(isNumber) {
        const allowed = isNumber ? ['FIXED', 'MULTIPLY', ...(fields().length === 1 ? ['MATRIX_1D'] : []), ...((fields().length === 2 || linkedChoice() && externalFields().length) ? ['MATRIX_2D'] : [])] : ['FIXED'];
        return `<div class="pm-price-source-summary"><strong>${e(g()?.label)}</strong><span>${e(fields().map(label).join(' · ') || '선택형 옵션')}</span>${scope.locked ? '' : '<button type="button" data-source-edit>그룹·묶음 변경</button>'}</div>${field('배율 항 이름', 'data-term-name type="text" maxlength="120"', term.name)}<div class="pm-price-type-grid">${allowed.map(type => `<label class="pm-price-type-choice ${term.type === type ? 'selected' : ''}"><input type="radio" name="term-type" value="${type}" ${term.type === type ? 'checked' : ''}><strong>${e({ FIXED: '고정 배율', MULTIPLY: '입력값 × 계수', MATRIX_1D: '숫자 1개 배율표', MATRIX_2D: '숫자 2개 배율표' }[type])}</strong></label>`).join('')}</div>${term.type === 'FIXED' ? `${field('곱할 배율 (원화 아님)', 'data-term-amount type="number" min="0" step="0.000001"', term.amount)}${!isNumber ? `<fieldset><legend>이 배율을 적용할 선택 옵션</legend><div class="pms-check-grid">${(g()?.choices || []).map(c => `<label class="pms-check"><input type="checkbox" data-term-choice value="${e(c.key)}" ${(term.choiceKeys || []).includes(c.key) ? 'checked' : ''}><span>${e(label(c))}</span></label>`).join('')}</div></fieldset>` : ''}` : term.type === 'MULTIPLY' ? `<p class="pms-help">${e(fields().map(f => label(f) + ' × 계수').join(' ＋ '))}. 수량 원값을 그대로 사용하려면 해당 계수를 1로 입력합니다.</p><div class="pms-form-grid two">${fields().map(f => field(label(f) + '에 곱할 계수', `data-term-rate="${e(f.key)}" type="number" min="0" step="0.000001"`, term.rates?.[f.key] ?? '')).join('')}</div>` : matrixHtml()}`;
      }
      function templateLink(includeHeaders) {
        const x = term.xAxis, y = term.yAxis;
        const params = new URLSearchParams({ role: factor() ? "FACTOR" : "PRICE", includeHeaders: String(includeHeaders), dimensions: y ? "2" : "1",
          rows: String(y?.headers?.some(v => v != null) || term.values?.some(row => row.some(v => v != null)) ? term.values.length : y ? 3 : 1),
          columns: String(x?.headers?.some(v => v != null) || term.values?.some(row => row.some(v => v != null)) ? x.headers.length : 3),
          xLabel: label(fields().find(f => f.key === x?.fieldKey)) || "X 입력", yLabel: label(fields().find(f => f.key === y?.fieldKey)) || "Y 입력" });
        return `${S.base}/pricing/excel/template?${params}`;
      }
      function templateLinks() {
        return `<div class="pms-actions"><a class="pms-button" href="${e(templateLink(false))}">${factor() ? "배율만" : "금액만"} · 회색 예시가 있는 양식 다운로드</a><a class="pms-button" href="${e(templateLink(true))}">기준 포함 · 회색 예시가 있는 양식 다운로드</a></div>`;
      }
      function matrixHtml() {
        const two = term.type === "MATRIX_2D", axes = [['x', term.xAxis], ...(two ? [['y', term.yAxis]] : [])];
        const current = tableUi.matrixStep ?? (hasPriceData() ? 1 : 0);
        tableUi.matrixStep = current;
        const panel = (i, html) => `<section data-matrix-step="${i}" ${current === i ? '' : 'hidden'}>${html}</section>`;
        return `<nav class="pm-price-flow" aria-label="가격표 작성 순서">${['자료 가져오기', '기준·금액 입력', '적용 범위', '가격 확인'].map((text, i) => `<button type="button" data-matrix-go="${i}" aria-current="${current === i ? 'step' : 'false'}"><b>${i + 1}</b><span>${text}</span></button>`).join('')}</nav>
          ${panel(0, `<h3 class="pm-price-editor-subtitle">어떤 자료로 가격표를 만드시나요?</h3><p class="pms-help">먼저 표의 방향을 연결하고 자료를 가져오세요. 기준값과 계산 범위는 다음 단계에서 정합니다.</p>${linkedChoice() && two ? '<p class="pms-help">한 축은 선택지의 계산값, 다른 축은 같은 주문 경로의 앞·뒤 숫자 입력입니다. 뒤 질문은 답변 후 금액이 정해집니다. 설치 안 함에서 높이를 건너뛰는 경우, 표에는 설치 조건을 걸고 미설치는 고정 0원으로 연결하세요.</p>' : ''}<div class="pms-form-grid two">${axes.map(([key, axis]) => `<label><span>${key.toUpperCase()}축 · ${key === 'x' ? '가로 열' : '세로 행'}에 연결할 숫자</span><select data-axis-field="${key}">${axis.fieldKey ? '' : '<option value="">숫자 질문과 필드 선택</option>'}${matrixFields().filter(f => (!linkedChoice() || !two) || (term[key === 'x' ? 'yAxis' : 'xAxis']?.fieldKey === '__numeric_value' ? f.key.includes(':') : f.key === '__numeric_value')).map(f => opt(f.key, label(f), axis.fieldKey)).join('')}</select></label>`).join('')}</div>
          <div class="pms-actions pm-price-methods" role="group" aria-label="가격표 작성 방법"><button type="button" data-price-method="excel">엑셀 파일</button><button type="button" data-price-method="paste">셀 붙여넣기</button><button type="button" data-price-method="direct">직접 입력</button></div>
          <div class="pm-price-excel-section" data-price-method-panel="excel"><div class="pms-form-grid two"><label><span>엑셀에 들어 있는 내용</span><select data-matrix-import-mode><option value="amounts">금액만 · 업로드 후 사이즈 기준 입력</option><option value="table">기준 포함 · 첫 행·첫 열의 300, 400도 가져오기</option></select></label></div><p data-import-help class="pms-help"></p><div class="pms-actions">${templateLinks()}<button type="button" class="primary" data-matrix-import>엑셀 업로드</button><input type="file" data-matrix-file hidden accept=".xlsx"></div><div class="pms-drop pm-price-file-drop" data-matrix-drop tabindex="0" role="button" aria-label="엑셀 파일 선택 또는 여기에 끌어 놓기"><strong>엑셀 파일을 여기에 끌어 놓으세요.</strong><small>또는 클릭하여 선택 · .xlsx 파일 한 개 · 선택 후 기존 미리보기에서 확인합니다.</small></div><details><summary>수식·빈칸·회색 샘플은 어떻게 준비하나요?</summary><p>엑셀 수식은 <strong>복사 → 선택하여 붙여넣기 → 값</strong>으로 바꾸세요. 회색 예시는 지우고 실제 값을 넣으세요. 무료 구간만 0원이며 빈칸은 미설정입니다. 금액 전용 ‘가격데이터’ 또는 기준 포함 ‘가격표’ 시트를 읽습니다. 다른 시트명은 내용이 있는 표시 시트가 하나일 때 읽습니다.</p></details></div><div data-paste-slot></div><div data-direct-help hidden><p class="pms-help">다음 단계에서 기준값을 생성하면 입력할 금액 칸이 만들어집니다. 처음부터 큰 표를 비워 둘 필요가 없습니다.</p><button type="button" data-direct-start>기준값부터 작성하기</button></div>`)}
          ${panel(1, `<h3 class="pm-price-editor-subtitle">각 금액이 어떤 크기의 가격인지 연결하세요</h3><div data-matrix-import-status role="status">${e(tableUi.importMessage || `${term.values.length}행 × ${term.xAxis.headers.length}열 · 기준값과 실제 금액을 입력하세요.`)}</div>${S.priceTable.toolsHtml(term, tableUi)}<div class="pms-actions"><button type="button" data-matrix-column>＋ X 기준 추가</button>${two ? '<button type="button" data-matrix-row>＋ Y 기준 추가</button>' : ''}${two ? '<button type="button" data-axis-swap>X·Y 축 교환</button>' : ''}</div><p class="pms-help">기준값과 금액은 필수입니다. 무료 구간은 0원을 넣으세요. 포함 경계는 다음 단계의 기본 방식을 따르며 필요할 때 각 기준 아래에서 바꿀 수 있습니다.</p><div class="pm-price-matrix-wrap" tabindex="0" aria-label="가로로 스크롤할 수 있는 가격표">${two ? table2d() : table1d()}</div>`)}
          ${panel(2, `<h3 class="pm-price-editor-subtitle">입력값 사이와 표 밖의 가격을 정하세요</h3><p class="pms-help">최소·최대 주문 크기는 프로세스 설정입니다. 가격표의 첫·마지막 기준은 금액을 찾는 경계이며, 업로드한다고 주문 제한값이 바뀌지 않습니다.</p><div class="pms-form-grid two">${axes.map(([key, axis]) => {
            const f = matrixFields().find(f => f.key === axis.fieldKey), lower = S.priceTable.lower(axis), edge = axis.headers[lower ? 0 : axis.headers.length - 1];
            return `<section class="pm-price-axis-policy"><h4>${key.toUpperCase()}축 · ${e(label(f))}</h4><p class="pms-help">프로세스: 최소 ${e(f?.min ?? (f?.allowNegative ? '미설정' : '0 (음수 불가)'))} / 최대 ${e(f?.max ?? '미설정')} ${e(f?.unit || '')}<br>현재 표: 첫 기준 ${e(axis.headers[0] ?? '미입력')} / 마지막 기준 ${e(axis.headers.at(-1) ?? '미입력')}</p>${S.priceTable.coverageHtml(axis, f)}${!factor() ? `<label class="pms-check"><input type="checkbox" data-axis-free="${key}" ${axis.zeroThrough != null ? "checked" : ""}>${e(axis.headers[0])} 이하를 0원으로 처리</label><p class="pms-help">선택하면 이 축의 첫 기준값 이하에서 이 항의 기본 금액이 0원입니다. 엑셀에 0원 행·열을 추가할 필요가 없습니다. 첫 기준에 있던 금액도 무료 처리됩니다. 기존 계산은 선택하지 않으면 유지됩니다.</p>` : ""}<label><span>기준 사이의 입력값 처리</span><select data-axis-operator="${key}">${axisOptions(axis)}</select></label><div data-axis-example="${key}">${S.priceTable.axisExample(axis)}</div><label><span>표 밖 입력 처리</span><select data-axis-outside="${key}">${opt('ERROR', '계산 중단 · 기준 추가 또는 주문 범위 수정', axis.outsidePolicy || 'ERROR')}${opt('EDGE', '표의 끝 기준 가격 적용', axis.outsidePolicy || 'ERROR')}</select></label><p class="pms-help">${axis.outsidePolicy === 'EDGE' ? `${e(edge)} 기준 ${key === 'x' ? '열' : '행'}의 금액을 표 밖에도 적용합니다. 고객 입력 크기와 주문 허용 범위는 그대로입니다.` : '표 밖 값을 오류로 처리하는 방식도 사용할 수 있습니다. 실제 허용 범위를 표가 모두 덮도록 기준을 추가하거나, 프로세스의 주문 범위·적용 조건을 확인하세요.'}</p><div class="pms-actions"><button type="button" data-matrix-go="1">표 기준·금액 추가 또는 수정</button><button type="button" data-range-help="${key}">값의 출처·계산 예시 확인</button><a href="/admin/product-master/products/${product.id}/process" target="_blank" rel="noopener">프로세스 입력 범위·분기 확인</a></div></section>`;
          }).join('')}</div><details><summary>실제 주문 범위를 바꾸려면?</summary><p>제품의 프로세스 → 해당 숫자 질문 → 입력 항목 설정에서 최소·최대값을 수정합니다. 이 창에서는 가격표 처리만 바꿉니다. 최종 검사에서는 분기별 입력 제한과 항의 적용 조건도 함께 확인합니다.</p><a href="/admin/product-master/products/${product.id}/process" target="_blank" rel="noopener">프로세스 설정 새 창으로 열기</a><p>아직 반영하지 않은 항은 이 창을 닫지 않고 보관하세요. 프로세스 변경 후에는 가격계산 화면을 다시 열어 최신 연결을 확인하세요. 이미 실 제품이 연결되어 프로세스가 잠긴 경우, 이 가격표의 기준을 추가하거나 표 밖 처리 방식을 지정할 수 있습니다.</p></details>`)}
          ${panel(3, `${S.priceTable.previewHtml(term, matrixFields())}<p class="pms-help">입력 테스트는 선택 사항입니다. 다음 단계에서 필요한 적용 조건을 정한 뒤 항 반영 시 전체 허용 경로를 검사합니다.</p><div class="pms-actions"><button type="button" data-matrix-go="1">기준·금액 수정</button><button type="button" data-matrix-export>현재 가격표 다운로드</button></div>`)} `;
      }
      function focusMatrix(selector, message = '') {
        let input = S.$(selector, body);
        if (!input) return false;
        const panel = input.closest('[data-matrix-step]');
        if (panel && panel.hidden) { tableUi.matrixStep = Number(panel.dataset.matrixStep); draw(); input = S.$(selector, body); }
        return S.focusField(input, message);
      }
      function matrixMove(next) {
        if (pendingTable()) return;
        if (next > 1) { const errors = priceErrors(); if (errors.length) return editorError(errors[0]); }
        tableUi.matrixStep = Math.max(0, Math.min(3, next)); draw();
        S.$('.pm-price-flow', body)?.scrollIntoView({ block: 'start' });
      }
      function bindMatrixFlow() {
        if (!term.type.startsWith('MATRIX') || stage !== 1) return;
        const paste = S.$('.pm-price-paste', body); if (paste) { S.$('[data-paste-slot]', body).append(paste); paste.open = true; }
        const method = tableUi.method || 'excel';
        const methodChange = value => {
          tableUi.method = value;
          S.$$('[data-price-method-panel]', body).forEach(el => el.hidden = el.dataset.priceMethodPanel !== value);
          S.$$('[data-price-method]', body).forEach(el => { el.classList.toggle('primary', el.dataset.priceMethod === value); el.setAttribute('aria-pressed', String(el.dataset.priceMethod === value)); });
          S.$('[data-direct-help]', body).hidden = value !== 'direct';
        };
        methodChange(method);
        S.$$('[data-price-method]', body).forEach(el => el.onclick = () => methodChange(el.dataset.priceMethod));
        S.$('[data-direct-start]', body).onclick = () => { tableUi.buildMode = 'identity'; matrixMove(1); };
        S.$$('[data-matrix-go]', body).forEach(el => el.onclick = () => matrixMove(Number(el.dataset.matrixGo)));
        const importMode = S.$('[data-matrix-import-mode]', body), help = S.$('[data-import-help]', body);
        importMode.value = tableUi.importMode || 'amounts';
        const importHelp = () => { tableUi.importMode = importMode.value; help.innerHTML = importMode.value === 'table' ? '첫 행의 X 기준과 첫 열의 Y 기준(300, 400 등)을 <strong>포함해</strong> 올리세요. 좌측 상단 칸은 제목 또는 빈칸입니다.' : '첫 행·첫 열의 <strong>300, 400 등 사이즈 기준은 지우고</strong> 실제 가격만 새 파일 A1에 붙여넣으세요. 업로드 후 기준값을 입력합니다.'; };
        importMode.onchange = importHelp; importHelp();
        for (const key of ['x', ...(term.yAxis ? ['y'] : [])]) {
          const axis = term[key === 'x' ? 'xAxis' : 'yAxis'], field = matrixFields().find(f => f.key === axis.fieldKey);
          const describe = () => {
            const lower = S.priceTable.lower(axis), edge = axis.headers[lower ? 0 : axis.headers.length - 1];
            return `<p><strong>${key.toUpperCase()}축 · ${e(label(field))}</strong></p><p>가격 기준 출처: 업로드한 기준 또는 직접 입력한 기준 ${e(axis.headers[0])} ~ ${e(axis.headers.at(-1))}.</p><p>주문 제한 출처: 프로세스의 최소 ${e(field?.min ?? '미설정')} / 최대 ${e(field?.max ?? '미설정')}. 이 선택으로 주문 제한을 자동 변경하지 않습니다.</p><p>‘표의 끝 기준 가격 적용’을 선택하면 ${lower ? '첫 기준보다 작은' : '마지막 기준보다 큰'} 허용 입력도 <strong>${e(edge)} 기준의 ${key === 'x' ? '열' : '행'} 금액</strong>을 사용합니다. 경계에서 미만·초과로 제외한 값에도 같은 끝 가격이 적용됩니다. 2차원은 다른 축의 해당 기준과 만나는 금액을 사용합니다.</p><p>‘계산 중단’을 선택하면 표 밖 입력은 계산하지 않습니다. 기준을 추가하거나 실제 주문 범위·분기 조건이 표 안에 들어오도록 구성하면 이 방식으로도 등록할 수 있습니다.</p>`;
          };
          S.$(`[data-range-help="${key}"]`, body).onclick = () => { const popup = S.dialog('가격 기준과 입력 제한의 차이', describe(), { wide: false, foot: '<button type="button" data-range-close>확인</button>' }); S.$('[data-range-close]', popup).onclick = () => popup.close(); };
          S.$(`[data-axis-outside="${key}"]`, body).onchange = async event => {
            const next = event.target.value, old = axis.outsidePolicy || 'ERROR'; event.target.value = old;
            if (next === old) return;
            if (next === 'EDGE' && !await S.priceTable.review('표 밖에도 끝 가격을 적용할까요?', describe(), '내용 확인 후 끝 가격 적용')) return;
            axis.outsidePolicy = next; draw();
          };
        }
      }
      function axisOptions(axis) {
        return [opt("LE", "큰 기준 적용 · 450 → 500 (이하)", axis?.operator),
          opt("LT", "큰 기준 적용 · 450 → 500 (미만)", axis?.operator),
          opt("GE", "작은 기준 적용 · 450 → 400 (이상)", axis?.operator),
          opt("GT", "작은 기준 적용 · 450 → 400 (초과)", axis?.operator)].join("");
      }
      function headerInput(axis, i, value) {
        const a = term[axis === "x" ? "xAxis" : "yAxis"], lower = S.priceTable.lower(a);
        const text = { LE: "현재 가격에 포함", LT: "다음 가격부터", GE: "현재 가격부터", GT: "이전 가격까지" };
        return `<input type="number" min="0" step="any" data-matrix-header="${axis}" data-index="${i}" value="${e(value)}" placeholder="기준값" aria-label="${axis.toUpperCase()}축 ${i + 1}번째 기준값"><select data-bound-operator="${axis}:${i}" aria-label="${axis.toUpperCase()}축 ${i + 1}번째 경계 처리">${opt("", "기본 (" + ({LE:"이하",LT:"미만",GE:"이상",GT:"초과"}[a.operator]) + ")", a.operators?.[i] || "")}${(lower ? ["GE", "GT"] : ["LE", "LT"]).map(op => opt(op, text[op], a.operators?.[i])).join("")}</select><small data-bound-range="${axis}:${i}">${e(S.priceTable.range(a, i))}</small>`;
      }
      function priceInput(row, col, value) { return `<input type="number" min="0" step="${factor() ? "0.000001" : "0.01"}" data-matrix-price="${row}:${col}" value="${e(value)}" placeholder="${factor() ? "배율" : "금액"}" aria-label="${row + 1}행 ${col + 1}열 ${factor() ? "배율" : "금액"}">`; }
      function table1d() {
        return `<table class="pm-price-matrix"><thead><tr><th scope="col">X 기준값 / 포함 여부</th>${term.xAxis.headers.map((v, i) => `<th scope="col">${headerInput("x", i, v)}<button type="button" data-matrix-del-col="${i}" aria-label="X ${i + 1}번째 기준 삭제" ${term.xAxis.headers.length === 1 ? "disabled" : ""}>삭제</button></th>`).join("")}</tr></thead><tbody><tr><th scope="row">${factor() ? "배율 (원화 아님)" : "금액 (원)"}</th>${term.xAxis.headers.map((_, i) => `<td>${priceInput(0, i, term.values[0]?.[i])}</td>`).join("")}</tr></tbody></table>`;
      }
      function table2d() {
        return `<table class="pm-price-matrix"><thead><tr><th scope="col">Y↓ / X→<small>각 기준의 포함 여부 적용</small></th>${term.xAxis.headers.map((v, i) => `<th scope="col">${headerInput("x", i, v)}<button type="button" data-matrix-del-col="${i}" aria-label="X ${i + 1}번째 기준 삭제" ${term.xAxis.headers.length === 1 ? "disabled" : ""}>열 삭제</button></th>`).join("")}</tr></thead><tbody>${term.yAxis.headers.map((v, r) => `<tr><th scope="row">${headerInput("y", r, v)}<button type="button" data-matrix-del-row="${r}" aria-label="Y ${r + 1}번째 기준 삭제" ${term.yAxis.headers.length === 1 ? "disabled" : ""}>행 삭제</button></th>${term.xAxis.headers.map((_, c) => `<td>${priceInput(r, c, term.values[r]?.[c])}</td>`).join("")}</tr>`).join("")}</tbody></table>`;
      }
      function conditionsHtml() {
        if (scope.branch) return `<h3>이 조건의 계산 확인</h3><p class="pms-help">앞에서 정한 적용 조건을 유지합니다. 다른 조건의 가격표는 이 창을 닫고 조건별 계산 구성에서 이어서 등록하세요.</p><p>${term.conditions.map(c=>e(conditionText(c))).join(' 그리고 ')}</p>`;
        return `<div class="pm-price-review"><h3>${e(term.name)}</h3><p>${e(g()?.label)} · ${term.branches?.length ? `조건별 계산 ${term.branches.length}개 · 한 항으로 합산` : e(summary(term))}</p></div><h3 class="pm-price-editor-subtitle">추가 적용 조건 (선택 사항)</h3><p class="pms-help">이 항의 입력묶음이 실제 분기에 표시되고 답변이 유효해야 계산됩니다. 아래 조건은 그 위에 추가로 적용합니다. 예: ‘세면대 종류 = A’일 때 세면대 수량 × A 단가를 적용합니다.</p>${scope.requiredCondition ? '<p class="pms-help"><strong>선택한 옵션 조건은 필수입니다.</strong> 추가 조건도 모두 일치할 때만 적용합니다. 옵션 연결 변경은 일괄 설정 화면에서 진행하세요.</p>' : ''}<div class="pms-form-grid two"><label><span>추가 조건 적용 방식</span><select data-term-match ${scope.requiredCondition || (scope.conditionsOnly && !scope.singleConditions) ? 'disabled' : ''}>${opt("ALL", "모든 조건이 일치 (AND)", term.match)}${opt("ANY", "하나 이상 일치 (OR)", term.match)}</select></label></div><div class="pm-price-conditions">${(term.conditions || []).map((c, i) => `<article><span>${e(conditionText(c))}</span><div class="pms-actions">${requiredCondition(c) ? '<span class="pms-help">옵션 연결 필수 조건</span>' : `<button type="button" data-condition-edit="${i}">수정</button><button type="button" data-condition-delete="${i}">삭제</button>`}</div></article>`).join("") || '<p class="pms-help">추가 조건 없음 · 해당 입력묶음의 현재 답변에 따라 적용합니다.</p>'}</div><button type="button" data-condition-add>＋ 적용 조건 추가</button><div data-condition-editor></div>${scope.conditionsOnly ? '<p class="pms-help">항 전체의 조건은 모든 하위 분기에 공통으로 적용합니다. 공통 입력의 모든 경로를 한 항에서 계산하려면 추가 조건을 비워 두세요. 다른 상판 경로까지 이 항의 계산을 요구하면 해당 상판 선택 조건으로 적용 범위를 제한하세요.</p>' : '<div class="pms-actions pm-price-term-test"><button type="button" data-term-test>입력값을 바꾸며 이 항만 테스트</button><span class="pms-help">아래 계산식에 반영하기 전에도 확인할 수 있습니다.</span></div>'}<div class="pms-help pm-price-review-note">최종가격은 배율을 반영한 금액 항의 합입니다. 배율 전용 항은 별도로 합산하지 않습니다. 같은 숫자에 여러 가격표가 동시에 적용되면 반영할 수 없습니다. 다른 입력의 항과 기본금·추가금은 합산합니다. 항 반영 후 아래 실제 프로세스 테스트를 완료해야 저장할 수 있습니다.</div>`;
      }
      function bindEditor() {
        S.$('[data-clear-missing-option-target]', body)?.addEventListener('click', event => S.run(event.currentTarget, async () => {
          if (!await S.confirm('삭제된 가격 대상 옵션 연결과 그 옵션의 필수 선택 조건을 해제합니다. 이 항을 적용할 현재 질문·옵션과 추가 조건을 다시 지정하고 검증해야 합니다. 계속하시겠습니까?')) return;
          term.conditions = (term.conditions || []).filter(c => !requiredCondition(c));
          term.optionTarget = null; scope.requiredCondition = null; draw();
        }));
        S.$('[data-term-test]', body)?.addEventListener('click', event => S.run(event.currentTarget, async () => {
          if (conditionDraft) return editorError("작성 중인 조건을 반영하거나 취소해 주세요.");
          const errors = [...sourceErrors(), ...priceErrors()]; if (errors.length) return editorError(errors.join("\n"));
          await testSingleTerm(S.copy(term), definition.terms.concat(scope.extraTerms || []));
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
          if (input.disabled) { input.checked = term.fieldKeys.includes(input.value); return; }
          const next = S.$$("[data-term-field]:checked", body).map((el) => el.value);
          if (next.length > 2) { input.checked = false; return editorError("가격표에는 숫자 입력을 1개 또는 2개만 연결할 수 있습니다. 세 번째 숫자는 별도의 항으로 추가해 주세요. 3차원 엑셀 가격표는 지원하지 않습니다."); }
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
          if (linkedChoice() && next.includes(':')) {
            if (hasPriceData() && !await S.confirm("가격표의 기준과 금액을 유지하고 연결할 숫자 필드를 변경할까요?")) return;
            if (term[other]) term[other].fieldKey = '__numeric_value';
            term[axis].fieldKey = next;
            draw();
            return;
          }
          const mode = await new Promise(resolve => {
            const choice = S.dialog("축 변경 방식 선택", '<p><strong>같은 가로·세로 입력의 금액을 유지하려면 축 교환을 선택하세요.</strong></p><p>축 교환: 입력 항목·기준값·이하/미만·금액표를 함께 교환합니다.</p><p>연결만 변경: 표를 그대로 두고 입력 항목만 바꿉니다. 같은 고객 입력의 금액이 달라질 수 있습니다.</p>', { wide: false, foot: '<button type="button" data-axis-cancel>취소</button><button type="button" data-axis-relink>연결만 변경 · 금액 달라짐</button><button type="button" class="primary" data-axis-transpose>축 교환 · 금액 관계 유지</button>', onClose: () => resolve(null) });
            S.$('[data-axis-cancel]', choice).onclick = () => choice.close();
            for (const [selector, value] of [['[data-axis-relink]', 'relink'], ['[data-axis-transpose]', 'swap']]) S.$(selector, choice).onclick = () => { resolve(value); choice.close(); };
          });
          if (mode === 'swap') swap();
          else if (mode === 'relink') { const old = term[axis].fieldKey; term[axis].fieldKey = next; if (term[other]?.fieldKey === next) term[other].fieldKey = old; draw(); }
        }; });
        S.$$("[data-axis-operator]", body).forEach((input) => { input.onchange = async () => {
          const axis = term[input.dataset.axisOperator === "x" ? "xAxis" : "yAxis"], next = input.value;
          if (S.priceTable.lower(axis) !== ["GE", "GT"].includes(next)) {
            input.value = axis.operator;
            if (!await S.confirm("큰 기준↔작은 기준을 바꾸면 같은 입력값의 가격이 달라집니다. 개별 경계 설정은 새 방향의 기본값으로 초기화합니다. 금액과 기준값은 유지합니다. 계속하시겠습니까?")) return;
            axis.operators = axis.headers.map(() => null);
          }
          axis.operator = next; draw();
        }; });
        S.$$("[data-matrix-header]", body).forEach((input) => { input.oninput = () => { term[input.dataset.matrixHeader === "x" ? "xAxis" : "yAxis"].headers[Number(input.dataset.index)] = numeric(input.value); }; });
        S.$$("[data-matrix-price]", body).forEach((input) => { input.oninput = () => { const [r, c] = input.dataset.matrixPrice.split(":").map(Number); term.values[r][c] = numeric(input.value); }; });
        S.$("[data-matrix-column]", body)?.addEventListener("click", () => { if (pendingTable()) return; if (term.xAxis.headers.length >= 200 || (term.xAxis.headers.length + 1) * term.values.length > 20000) return editorError("축은 최대 200개 기준, 전체 표는 최대 20,000개 금액까지 등록할 수 있습니다."); term.xAxis.operators = term.xAxis.headers.map((_, i) => term.xAxis.operators?.[i] || null); term.xAxis.operators.push(null); term.xAxis.headers.push(null); term.values.forEach((row) => row.push(null)); draw(); });
        S.$("[data-matrix-row]", body)?.addEventListener("click", () => { if (pendingTable()) return; if (term.yAxis.headers.length >= 200 || (term.yAxis.headers.length + 1) * term.xAxis.headers.length > 20000) return editorError("축은 최대 200개 기준, 전체 표는 최대 20,000개 금액까지 등록할 수 있습니다."); term.yAxis.operators = term.yAxis.headers.map((_, i) => term.yAxis.operators?.[i] || null); term.yAxis.operators.push(null); term.yAxis.headers.push(null); term.values.push(term.xAxis.headers.map(() => null)); draw(); });
        S.$$("[data-matrix-del-col]", body).forEach((b) => { b.onclick = async () => { if (pendingTable()) return; const i = Number(b.dataset.matrixDelCol); const count = term.values.filter(row => row[i] != null && row[i] !== "").length; if (!await S.confirm(`X 기준 ${term.xAxis.headers[i] ?? '(빈 기준)'} 열과 입력 금액 ${count}개를 삭제하시겠습니까? 실행 취소로 복구할 수 있습니다.`)) return; term.xAxis.headers.splice(i, 1); term.xAxis.operators?.splice(i, 1); term.values.forEach((row) => row.splice(i, 1)); draw(); }; });
        S.$$("[data-matrix-del-row]", body).forEach((b) => { b.onclick = async () => { if (pendingTable()) return; const i = Number(b.dataset.matrixDelRow); const count = term.values[i].filter(v => v != null && v !== "").length; if (!await S.confirm(`Y 기준 ${term.yAxis.headers[i] ?? '(빈 기준)'} 행과 입력 금액 ${count}개를 삭제하시겠습니까? 실행 취소로 복구할 수 있습니다.`)) return; term.yAxis.headers.splice(i, 1); term.yAxis.operators?.splice(i, 1); term.values.splice(i, 1); draw(); }; });
        S.$("[data-matrix-import]", body)?.addEventListener("click", () => S.$("[data-matrix-file]", body).click());
        const importMatrixFile = file => S.run(S.$("[data-matrix-import]", body), async () => {
          if (!file || importing || pendingTable()) return;
          if (term.yAxis && !term.yAxis.fieldKey) return editorError("엑셀을 가져오기 전에 두 번째 축의 숫자 필드를 선택해 주세요.", '[data-axis-field="y"]');
          if (!S.acceptsFile(file,".xlsx")) return editorError(".xlsx 엑셀 파일을 선택해 주세요.","[data-matrix-drop]");
          const withHeaders = S.$("[data-matrix-import-mode]", body).value === "table";
          if (hasPriceData() && !await S.confirm(withHeaders ? "엑셀의 기준값·포함 여부·금액으로 현재 표를 교체합니다. 입력 항목 연결은 유지됩니다. 계속하시겠습니까?" : "현재 표의 금액을 엑셀로 교체합니다. 행·열 수가 바뀌면 기준값을 다시 입력해야 합니다. 계속하시겠습니까?")) return;
          importing = true; lockImport(true);
          try {
            const form = new FormData(); form.append("file", file); form.append("dimensions", term.type === "MATRIX_2D" ? "2" : "1"); form.append("includeHeaders", String(withHeaders)); form.append("role", factor() ? "FACTOR" : "PRICE");
            const data = await S.request("/pricing/excel/import", "POST", form), prices = data.amounts;
            if (!d.open) return;
            const cols = prices[0].length, rows = prices.length;
            const preview = `<p><strong>${e(file.name)} · ${rows}행 × ${cols}열 · 금액 ${rows * cols}개</strong></p><p>X(열): ${e(label(matrixFields().find(f => f.key === term.xAxis.fieldKey)))}${term.yAxis ? ` / Y(행): ${e(label(matrixFields().find(f => f.key === term.yAxis.fieldKey)))}` : ""}</p>${withHeaders ? `<ul>${[['X', data.xHeaders, data.xOperator || term.xAxis.operator, data.xOutsidePolicy], ...(term.yAxis ? [['Y', data.yHeaders, data.yOperator || term.yAxis.operator, data.yOutsidePolicy]] : [])].map(([name, headers, operator, policy]) => `<li>${name}축: 파일 기준 ${e(headers?.[0])} ~ ${e(headers?.at(-1))} · ${e({LE:'큰 기준·이하',LT:'큰 기준·미만',GE:'작은 기준·이상',GT:'작은 기준·초과'}[operator])} · 표 밖 ${policy === 'EDGE' ? '끝 기준 가격 적용' : '계산 중단'}</li>`).join('')}</ul>` : ''}<p class="pms-help">${withHeaders ? "파일의 기준값과 금액을 함께 가져옵니다. 주문 최소·최대값은 변경하지 않습니다. 다음 화면에서 기준을 확인한 뒤 표 밖 입력 처리 방식을 정하세요." : "가격 데이터만 가져옵니다. 행·열 개수는 이 파일에서 자동 설정합니다. 기존 기준의 개수가 같으면 유지하고, 다르면 기준값은 비워 둡니다. 시작값은 기존 기준 또는 프로세스에 명시한 최소값만 제안합니다. 간격 100은 수정 가능한 예시이며 기준 생성·반영을 누르기 전에는 표에 적용되지 않습니다. 다음 화면에서 기준값을 완성하세요."}</p><div class="pm-price-matrix-wrap"><table class="pm-price-upload-preview"><caption>금액 미리보기 · 최대 5행 × 6열</caption><tbody>${prices.slice(0, 5).map(row => `<tr>${row.slice(0, 6).map(value => `<td>${e(value)}${factor() ? "배" : "원"}</td>`).join("")}${cols > 6 ? "<td>…</td>" : ""}</tr>`).join("")}</tbody></table></div>${rows > 5 ? `<p>나머지 ${rows - 5}개 행은 반영 후 전체 표에서 확인할 수 있습니다.</p>` : ""}`;
            const importWarnings = withHeaders ? [['X',data.xHeaders],...(term.yAxis?[['Y',data.yHeaders]]:[])].map(([axis,headers])=>{try{S.priceTable.validateHeaders(headers);return '';}catch(err){return `<p class="pms-alert">${axis}축: ${e(err.message)} 원본 엑셀은 그대로 두고 다음 화면의 해당 기준값을 수정할 수 있습니다.</p>`;}}).join('') : '';
            if (!await S.priceTable.review("엑셀 업로드 미리보기", importWarnings + preview, "반영하고 기준·금액 수정하기") || !d.open) return;
            if (withHeaders) {
              term.xAxis.zeroThrough = data.xZeroThrough ?? null;
              if(term.yAxis)term.yAxis.zeroThrough = data.yZeroThrough ?? null;
              term.xAxis.outsidePolicy = data.xOutsidePolicy || 'ERROR';
              if (term.yAxis) term.yAxis.outsidePolicy = data.yOutsidePolicy || 'ERROR';
              term.xAxis.headers = data.xHeaders; term.xAxis.operator = data.xOperator || term.xAxis.operator;
              term.xAxis.operators = data.xOperators || data.xHeaders.map(() => null);
              if (term.yAxis) { term.yAxis.headers = data.yHeaders; term.yAxis.operator = data.yOperator || term.yAxis.operator; term.yAxis.operators = data.yOperators || data.yHeaders.map(() => null); }
            } else {
              if (term.xAxis.headers.length !== prices[0].length) { term.xAxis.headers = prices[0].map(() => null); term.xAxis.operators = []; }
              if (term.yAxis && term.yAxis.headers.length !== prices.length) { term.yAxis.headers = prices.map(() => null); term.yAxis.operators = []; }
            }
            term.values = S.copy(prices); tableUi.buildMode = withHeaders ? 'identity' : 'position'; tableUi.seqCountx = prices[0].length; tableUi.seqCounty = prices.length;
            for (const [key, axis] of [['x', term.xAxis], ...(term.yAxis ? [['y', term.yAxis]] : [])]) {
              const f = matrixFields().find(f => f.key === axis.fieldKey);
              tableUi['seqStart' + key] = axis.headers[0] ?? f?.min ?? '';
              if (axis.headers.length > 1 && axis.headers[0] != null && axis.headers[1] != null) tableUi['seqStep' + key] = S.priceTable.spacingInfo(axis.headers).step;
            } tableUi.importMessage = `업로드 완료: ${prices.length}행 × ${prices[0].length}열 · 아래 기준값 입력·자동 생성에서 각 축을 연결해 주세요.`; S.priceTable.resetDraft(tableUi); tableUi.matrixStep = 1; draw(); S.$("[data-matrix-import-status]", body).textContent = `업로드 완료: ${prices.length}행 × ${prices[0].length}열 · 축 기준값을 확인해 주세요.`;
          } catch (error) {
            if (d.open) { editorError(error.message || "엑셀 파일을 읽을 수 없습니다.", "[data-matrix-import]"); S.$("[data-term-errors]", body).insertAdjacentHTML("beforeend", templateLinks()); }
          } finally {
            importing = false; lockImport(false); updateHistory();
            if (tableUi.matrixStep === 1 && !S.$('[data-term-errors]', body)?.textContent) {
              const missing = firstInvalid();
              if (missing) S.focusField(S.$(missing[1], body));
            }
          }
        });
        const matrixInput = S.$("[data-matrix-file]", body), matrixDrop = S.$("[data-matrix-drop]", body);
        if (matrixInput) matrixInput.onchange = () => { const selected=Array.from(matrixInput.files || []);matrixInput.value="";if(selected.length>1)return editorError("엑셀 파일은 한 개씩 등록해 주세요.","[data-matrix-drop]");importMatrixFile(selected[0]); };
        if (matrixDrop) {
          const pick = () => { if (!importing && !S.pendingUploads && !pendingTable()) {matrixInput.value="";matrixInput.click();} };
          matrixDrop.onclick=pick;matrixDrop.onkeydown=event=>{if(["Enter"," "].includes(event.key)){event.preventDefault();pick();}};
          S.bindFileDrop(matrixDrop,selected=>importMatrixFile(selected[0]), {input:matrixInput,multiple:false,isBusy:()=>importing || S.pendingUploads>0,onError:message=>editorError(message,"[data-matrix-drop]")});
        }
        S.$("[data-matrix-export]", body)?.addEventListener("click", (event) => S.run(event.currentTarget, async () => {
          if (pendingTable()) return;
          const error = priceErrors(); if (error.length) throw Error(error.join("\n"));
          await downloadExcel({ role: factor() ? "FACTOR" : "PRICE", dimensions: term.type === "MATRIX_2D" ? 2 : 1, title: term.name, xLabel: label(matrixFields().find((f) => f.key === term.xAxis.fieldKey)), yLabel: label(matrixFields().find((f) => f.key === term.yAxis?.fieldKey)), xOperator: term.xAxis.operator, yOperator: term.yAxis?.operator, xHeaders: term.xAxis.headers, yHeaders: term.yAxis?.headers || [], amounts: term.values, xOperators: term.xAxis.operators || [], yOperators: term.yAxis?.operators || [], xOutsidePolicy: term.xAxis.outsidePolicy || "ERROR", yOutsidePolicy: term.yAxis?.outsidePolicy || "ERROR", xZeroThrough: term.xAxis.zeroThrough ?? null, yZeroThrough: term.yAxis?.zeroThrough ?? null });
        }));
        if (!factor() && g()?.control === "RADIO" && term.type === "FIXED" && stage === 1) S.optionRanges.bind({ body, term, questions: process().questions, draw, error: editorError, ui: tableUi });
        if (term.type.startsWith("MATRIX") && stage === 1) S.priceTable.bind({ body, term, draw, error: editorError, ui: tableUi, fields: matrixFields() });
        S.$$('[data-axis-free]',body).forEach(input=>input.onchange=()=>{const axis=term[input.dataset.axisFree==='x'?'xAxis':'yAxis'];axis.zeroThrough=input.checked?axis.headers[0]:null;history.record();draw();});
        S.$("[data-term-match]", body)?.addEventListener("change", (event) => { term.match = event.target.value; });
        S.$("[data-condition-add]", body)?.addEventListener("click", () => conditionEditor());
        S.$$("[data-condition-edit]", body).forEach((b) => { b.onclick = () => conditionEditor(Number(b.dataset.conditionEdit)); });
        S.$$("[data-condition-delete]", body).forEach((b) => { b.onclick = () => { term.conditions.splice(Number(b.dataset.conditionDelete), 1); conditionDraft = null; draw(); }; });
      }
      function lockImport(busy) { S.$$("button,input,select,textarea", d).forEach((el) => { if (busy) { el.dataset.priceWasDisabled = String(el.disabled); el.disabled = true; } else if (el.dataset.priceWasDisabled !== undefined) { el.disabled = el.dataset.priceWasDisabled === "true"; delete el.dataset.priceWasDisabled; } }); }
      function firstInvalid() {
        const invalidMoney = (v, scale = valueScale()) => { try { S.priceTable.money(v, scale); return false; } catch { return true; } };
        if (!term.name.trim()) return [stage === 0 ? 0 : 1, '[data-term-name]'];
        if (!supported(g())) return [0, '[data-term-group]'];
        if (g()?.control === 'NUMBER' && (!bundle() || !fields().length)) return [0, '[data-term-field]'];
        if (term.optionPricing) {
          const p = term.optionPricing;
          if (!term.choiceKeys?.length) return [1, '[data-term-choice]'];
          if (!S.optionRanges.sources(process().questions, term.groupKey).some(q => q.key === p.groupKey)) return [1, '[data-range-group]'];
          if (!process().questions.find(q => q.key === p.groupKey)?.fields.some(f => f.key === p.axis.fieldKey)) return [1, '[data-range-field]'];
          for (let i = 0; i < p.axis.headers.length; i++) {
            try { S.priceTable.validateHeaders(p.axis.headers.slice(0, i + 1)); } catch { return [1, `[data-range-bound="${i}"]`]; }
            if (invalidMoney(p.amounts[i])) return [1, `[data-range-amount="${i}"]`];
          }
          if (invalidMoney(p.aboveAmount)) return [1, '[data-range-above]'];
        }
        if (term.type === 'FIXED' && invalidMoney(term.amount, valueScale())) return [1, '[data-term-amount]'];
        if (term.type === 'MULTIPLY') for (const f of fields()) if (invalidMoney(term.rates?.[f.key], 6)) return [1, `[data-term-rate="${CSS.escape(f.key)}"]`];
        if (term.type.startsWith('MATRIX')) {
          for (const [key, axis] of [['x', term.xAxis], ...(term.yAxis ? [['y', term.yAxis]] : [])]) {
            for (let i = 0; i < (axis?.headers.length || 0); i++) {
              try { S.priceTable.validateHeaders(axis.headers.slice(0, i + 1)); }
              catch { return [1, `[data-matrix-header="${key}"][data-index="${i}"]`]; }
            }
          }
          for (let r = 0; r < term.values.length; r++) for (let c = 0; c < term.values[r].length; c++) if (invalidMoney(term.values[r][c], valueScale())) return [1, `[data-matrix-price="${r}:${c}"]`];
        }
        return null;
      }
      function editorError(text, selector = '') {
        const invalid = selector ? [1, selector] : firstInvalid();
        if (invalid) {
          if (stage !== invalid[0]) { stage = invalid[0]; conditionDraft = null; draw(); }
          const element = S.$(invalid[1], body), panel = element?.closest('[data-matrix-step]');
          if (panel?.hidden) { tableUi.matrixStep = Number(panel.dataset.matrixStep); draw(); }
        }
        const lines = String(text).split('\n').filter(Boolean);
        const target = S.$('[data-term-errors]', body);
        target.innerHTML = `<div class="pms-alert pm-price-error-detail"><strong>${e(lines[0])}</strong>${lines.length > 1 ? `<details><summary>다른 확인 사항 ${lines.length - 1}개</summary>${lines.slice(1).map(line => `<p>${e(line)}</p>`).join('')}</details>` : ''}${invalid ? '<p>표시된 입력을 수정한 뒤 다시 진행하면 다음 확인 위치로 이동합니다.</p>' : ''}</div>`;
        if (invalid) S.focusField(S.$(invalid[1], body), lines[0]);
        else { target.tabIndex = -1; target.scrollIntoView({ block: 'center' }); target.focus({ preventScroll: true }); }
      }
      function bindProcessRefresh(target) {
        const button = S.$('[data-process-refresh]', target); if (!button) return;
        button.onclick = event => S.run(event.currentTarget, async () => {
          try {
            await refreshProcess();
            if (!supported(g()) || g()?.control === "NUMBER" && (!bundle() || fields().length !== term.fieldKeys.length)) stage = 0;
            draw();
          } catch (error) {
            serverErrors([{ severity: 'ERROR', code: 'PRICE_TOTAL_MONEY_LIMIT', location: '프로세스 불러오기', message: error.message }]);
          }
        });
      }
      function schedulePreflight(force = false) {
        if (scope.conditionsOnly || scope.branch || stage === "mode") return;
        const signature = JSON.stringify([term, stage, context.schemaFingerprint]);
        // input then blur/change for the same value must not replace a link being clicked.
        if (force !== true && signature === preflightSignature) return;
        preflightSignature = signature;
        clearTimeout(preflightTimer); preflightSequence++;
        if (!d.open || stage === 0) return;
        const target = S.$('[data-term-preflight]', body); if (!target) return;
        const local = [...sourceErrors(), ...priceErrors()];
        if (local.length) {
          target.innerHTML = `<div class="pms-help"><strong>입력 확인이 필요합니다.</strong>${local.map(message => `<p>${e(message)}</p>`).join('')}<p>빈 금액을 0원으로 대신하지 않습니다. 무료라면 0을 직접 입력하세요.</p></div>`;
          return;
        }
        if (onDraft || !context.ready) {
          target.textContent = '작성 중인 항입니다. 전체 계산식에 연결한 뒤 입력 범위·금액·조건을 함께 검사합니다.';
          return;
        }
        target.textContent = '입력값 변경 확인 중… 다른 항·조건을 계속 작성할 수 있습니다.';
        preflightTimer = setTimeout(runPreflight, 700);
      }
      async function runPreflight() {
        if (!d.open || stage === 0) return;
        if (preflightBusy) { preflightAgain = true; return; }
        const target = S.$('[data-term-preflight]', body); if (!target) return;
        const serial = preflightSequence, fingerprint = context.schemaFingerprint;
        preflightBusy = true;
        try {
          const terms = S.copy(definition.terms).concat(S.copy(scope.extraTerms || []));
          if (index == null) terms.push(S.copy(term)); else terms[index] = S.copy(term);
          F.validateLinks(terms);
          const validation = await api(product.id, '/diagnose?draft=true', 'POST', { definition: { ...S.copy(definition), terms, schemaFingerprint: fingerprint }, answers: {} });
          if (!d.open || serial !== preflightSequence || fingerprint !== context.schemaFingerprint || !target.isConnected) return;
          const issues = (validation.issues || []).filter(i => i.severity !== 'INFO');
          target.innerHTML = issues.length ? `<div class="pms-help"><strong>작성 중 입력·계산 안내</strong>${issues.slice(0, 6).map(i => `<p>${e(i.location)}: ${e(i.message)}</p>`).join('')}${issues.length > 6 ? '<p>전체 검사에서 나머지 안내를 확인할 수 있습니다.</p>' : ''}<p>가격 범위 안내가 있어도 다른 항·조건을 계속 작성할 수 있습니다. 구조 오류나 빠진 필수값은 항 반영 전에 수정해야 합니다.</p></div>${issues.some(i => ["PRICE_TERM_MONEY_LIMIT", "PRICE_TOTAL_MONEY_LIMIT", "PRICE_TABLE_GAP", "PRICE_BRANCH_GAP", "PRICE_AXIS_RANGE"].includes(i.code)) ? processRangeHelp() : ''}` : '<p class="pms-help">현재 입력으로 초안 검사를 통과했습니다. 항·조건을 더 바꾸면 다시 검사하며 최종 저장 검증은 별도입니다.</p>';
          bindProcessRefresh(target);
        } catch (error) {
          if (d.open && serial === preflightSequence && target.isConnected) target.textContent = '미리 확인하지 못했습니다: ' + error.message + '. 작성 내용은 유지됩니다. 항 반영 또는 전체 검사에서 다시 확인하세요.';
        } finally {
          preflightBusy = false;
          if (preflightAgain) { preflightAgain = false; if (d.open) schedulePreflight(true); }
        }
      }
      function serverErrors(issues) {
        const errors = issues.filter(i => i.severity === 'ERROR');
        const relevant = errors.find(i => i.target === `terms.${term.key}.xAxis.outsidePolicy` || i.target === `terms.${term.key}.yAxis.outsidePolicy`);
        if (relevant) {
          const axis = relevant.target.endsWith('.xAxis.outsidePolicy') ? 'x' : 'y';
          return editorError(`${relevant.location}: ${relevant.message}`, `[data-axis-outside="${axis}"]`);
        }
        editorError(errors.map(i => `${i.location}: ${i.message}`).join('\n'));
        if (errors.some(i => ["PRICE_TERM_MONEY_LIMIT", "PRICE_TOTAL_MONEY_LIMIT", "PRICE_TABLE_GAP", "PRICE_BRANCH_GAP", "PRICE_AXIS_RANGE"].includes(i.code))) {
          const target = S.$('[data-term-errors]', body);
          target.insertAdjacentHTML('beforeend', processRangeHelp());
          bindProcessRefresh(target);
        }
      }
      function sourceErrors() { const errors = []; if (!term.name.trim()) errors.push("항 이름을 입력해 주세요."); if (!supported(g())) errors.push("숫자 입력형·선택형 가격 영향 그룹을 선택해 주세요."); if (g()?.control === "NUMBER" && (!bundle() || !fields().length)) errors.push("입력묶음과 이 항에서 사용할 숫자 항목을 하나 이상 선택해 주세요."); return errors; }
      function priceErrors() {
        const errors = [];
        try { S.optionRanges.validate(term, process().questions); } catch (ex) { errors.push(ex.message); }
        const nonnegative = (v) => v != null && v !== "" && Number.isFinite(Number(v)) && Number(v) >= 0;
        const validMoney = (v, scale) => {
          if (!nonnegative(v)) return false;
          try { S.priceTable.money(v, scale); return true; } catch { return false; }
        };
        if (term.type === "FIXED" && !validMoney(term.amount, valueScale())) errors.push((factor() ? "배율" : "고정 금액") + "은 0 이상, 소수 " + valueScale() + "자리까지 입력해 주세요. 빈칸은 입력이 필요합니다.");
        if (term.type === "MULTIPLY" && fields().some((f) => !validMoney(term.rates?.[f.key], 6))) errors.push("모든 입력 항목의 단가를 입력해 주세요. 무료는 0을 직접 입력하세요. 허용 범위는 0~9,999,999,999,999.99, 소수 6자리까지입니다.");
        if (term.type.startsWith("MATRIX")) {
          if ((term.type === "MATRIX_1D" && fields().length !== 1) || (term.type === "MATRIX_2D" && !linkedChoice() && fields().length !== 2)) errors.push("1차원 가격표는 1개, 2차원 가격표는 같은 묶음의 2개 항목을 선택해 주세요.");
          const two = term.type === "MATRIX_2D";
          if (!matrixFields().some((f) => f.key === term.xAxis?.fieldKey)) errors.push("X축 입력 항목을 선택해 주세요.");
          if (two && (!matrixFields().some((f) => f.key === term.yAxis?.fieldKey) || term.xAxis.fieldKey === term.yAxis.fieldKey)) errors.push("X축과 다른 Y축 입력 항목을 선택해 주세요.");
          for (const [name, axis] of [["X", term.xAxis], ...(two ? [["Y", term.yAxis]] : [])]) { if (!axis?.headers?.length || axis.headers.some((v) => v == null || !Number.isFinite(Number(v)) || compareDecimal(v, 0) < 0)) errors.push(name + "축의 모든 기준값을 0 이상의 숫자로 입력해 주세요."); else if (axis.headers.some((v, i) => i > 0 && compareDecimal(v, axis.headers[i - 1]) <= 0)) errors.push(name + "축 기준값은 중복 없이 작은 값부터 순서대로 입력해 주세요."); }
          for (const axis of [term.xAxis, ...(two ? [term.yAxis] : [])]) {
            try { S.priceTable.validateAxis(axis); } catch (ex) { errors.push(ex.message); }
          }
          const rows = two ? term.yAxis?.headers?.length : 1, cols = term.xAxis?.headers?.length;
          if (term.values?.length !== rows || term.values.some(row => row.length !== cols)) errors.push("기준값 개수와 금액 행·열 수가 다릅니다. 기준 생성 개수를 확인하세요.");
          const badCells = []; (term.values || []).forEach((row, r) => row.forEach((v, c) => { if (!validMoney(v, valueScale())) badCells.push(`${r + 1}행 ${c + 1}열`); }));
          if (badCells.length) errors.push(`잘못되거나 비어 있는 금액 ${badCells.length}개: ${badCells.slice(0, 8).join(", ")}${badCells.length > 8 ? " 외" : ""}. 0~9,999,999,999,999.99원, 소수 ${valueScale()}자리까지 입력하세요. 0을 적용할 구간에만 0을 입력하세요.`);
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
          if (number && !conditionDraft.fieldKey) conditionDraft.fieldKey = q.fields?.[0]?.key;
          const presence = ["PRESENT", "ABSENT"].includes(conditionDraft.operator), range = conditionDraft.operator === "RANGE";
          S.$("[data-condition-editor]", body).innerHTML = `<section class="pm-price-condition-editor"><h4>${conditionIndex == null ? "적용 조건 추가" : "적용 조건 수정"}</h4><div class="pms-form-grid two"><label><span>조건의 질문</span><select data-c-question>${!q ? `<option value="${e(conditionDraft.groupKey)}" selected disabled>삭제된 질문 · 새 질문 선택 필요</option>` : ""}${qs.map((q) => opt(q.key, label(q), conditionDraft.groupKey)).join("")}</select></label>${number ? `<label><span>조건의 입력 항목</span><select data-c-field>${!(q.fields || []).some(f => f.key === conditionDraft.fieldKey) ? `<option value="${e(conditionDraft.fieldKey || "")}" selected disabled>삭제된 입력 · 새 항목 선택 필요</option>` : ""}${(q.fields || []).map((f) => opt(f.key, label(f), conditionDraft.fieldKey)).join("")}</select></label>` : ""}<label><span>비교 방식</span><select data-c-operator>${Object.entries(number ? { EQ: "일치", NE: "불일치", LE: "이하", LT: "미만", GE: "이상", GT: "초과", RANGE: "구간", PRESENT: "답변 있음", ABSENT: "답변 없음" } : { EQ: "선택된 보기 포함", NE: "선택된 보기 제외", PRESENT: "답변 있음", ABSENT: "답변 없음" }).map(([v, text]) => opt(v, text, conditionDraft.operator)).join("")}</select></label>${!presence && number ? field(range ? "하한값" : "비교값", 'data-c-lower type="number" step="any"', conditionDraft.lower) : ""}${!presence && range ? field("상한값", 'data-c-upper type="number" step="any"', conditionDraft.upper) : ""}</div>${!presence && range ? `<div class="pms-check-grid"><label class="pms-check"><input type="checkbox" data-c-inclusive="lower" ${conditionDraft.lowerInclusive ? "checked" : ""}>하한 포함 (이상)</label><label class="pms-check"><input type="checkbox" data-c-inclusive="upper" ${conditionDraft.upperInclusive ? "checked" : ""}>상한 포함 (이하)</label></div>` : ""}${!presence && number ? '<p class="pms-help">예: W 600 이하 / 600 초과~1000 이하 / 1000 초과. 정수 간격 1이면 600 초과는 601 이상입니다. 소수를 허용하면 601 이상 사이에 600.1 등이 빠지므로 ‘600 초과’를 사용하세요. 조건을 반영한 뒤 전체 영향 관계 검사에서 누락·중복 예시를 확인할 수 있습니다.</p>' : ""}${!presence && !number ? `<div class="pms-check-grid">${(q?.choices || []).map((c) => `<label class="pms-check"><input type="checkbox" data-c-choice value="${e(c.key)}" ${conditionDraft.choiceKeys.includes(c.key) ? "checked" : ""}>${e(label(c))}</label>`).join("")}</div>` : ""}<div data-c-errors role="alert"></div><div class="pms-actions"><button type="button" data-c-cancel>조건 작성 취소</button><button type="button" class="primary" data-c-save>조건 반영</button></div></section>`;
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
            if (!q) message = "삭제된 질문을 현재 프로세스의 질문으로 다시 선택해 주세요.";
            else if (number && !(q.fields || []).some(f => f.key === conditionDraft.fieldKey)) message = "삭제된 숫자 입력 항목을 다시 선택해 주세요.";
            else if (!presence && number && (conditionDraft.lower == null || !Number.isFinite(Number(conditionDraft.lower)))) message = "조건의 비교값을 입력해 주세요.";
            else if (!presence && range && (conditionDraft.upper == null || !Number.isFinite(Number(conditionDraft.upper)) || compareDecimal(conditionDraft.lower, conditionDraft.upper) > 0 || (compareDecimal(conditionDraft.lower, conditionDraft.upper) === 0 && (!conditionDraft.lowerInclusive || !conditionDraft.upperInclusive)))) message = "상한·하한으로 이루어진 유효한 구간을 입력해 주세요.";
            else if (!presence && !number && !conditionDraft.choiceKeys.length) message = "조건에 사용할 보기를 하나 이상 선택해 주세요.";
            if (message) {
              S.$("[data-c-errors]", area).innerHTML = `<div class="pms-alert">${e(message)}</div>`;
              S.focusField(S.$(number ? (conditionDraft.lower == null ? '[data-c-lower]' : range ? '[data-c-upper]' : '[data-c-lower]') : '[data-c-choice]', area), message); return;
            }
            if (!number) conditionDraft.fieldKey = null;
            if (conditionIndex == null) term.conditions.push(S.copy(conditionDraft)); else term.conditions[conditionIndex] = S.copy(conditionDraft);
            conditionDraft = null; draw();
          };
        }
        drawCondition(); S.$("[data-condition-editor]", body).scrollIntoView({ block: "nearest", behavior: "smooth" });
      }
      S.$("[data-term-prev]", d).onclick = async () => { if (importing || stage === 1 && pendingTable()) return; if (conditionDraft && !await S.confirm("아직 반영하지 않은 조건 초안을 버리고 이전 단계로 이동하시겠습니까?")) return; if (stage === 1 && term.type.startsWith('MATRIX') && tableUi.matrixStep > 0) return matrixMove(tableUi.matrixStep - 1); stage = stage === "mode" ? 0 : stage === 1 && chooseMode() ? "mode" : Math.max(0, stage - 1); conditionDraft = null; draw(); };
      S.$("[data-term-next]", d).onclick = () => {
        if (importing || stage === 1 && pendingTable()) return;
        if (stage === "mode") {
          if (!mode) return;
          if (mode === "single") { stage = 1; draw(); return; }
          const editor = branchEditor(term, next => {
            if (onDraft) return onDraft(next);
            const terms = definition.terms.concat(scope.extraTerms || []);
            if (index == null) terms.push(next); else terms[index] = next;
            applyTerms(terms);
          }, scope.extraTerms || []);
          if (editor?.open) d.close();
          return;
        }
        if (stage === 1 && term.type.startsWith('MATRIX') && tableUi.matrixStep < 3) return matrixMove(tableUi.matrixStep + 1);
        const errors = [...sourceErrors(), ...(stage === 1 ? priceErrors() : [])]; if (errors.length) return editorError(errors.join("\n"));
        stage = stage === 0 && chooseMode() ? "mode" : stage + 1; draw();
      };
      S.$("[data-term-commit]", d).onclick = event => S.run(event.currentTarget, async () => {
        if (importing || pendingTable()) return;
        if (conditionDraft) return editorError("작성 중인 적용 조건을 반영하거나 취소해 주세요.");
        const errors = [...sourceErrors(), ...(scope.conditionsOnly ? [] : priceErrors())]; if (errors.length) return editorError(errors.join("\n"));
        if (scope.choiceKey && (term.groupKey !== scope.choiceGroup || term.choiceKeys?.length !== 1 || term.choiceKeys[0] !== scope.choiceKey)) return editorError("일괄 설정에서 선택한 옵션만 이 항에 연결해야 합니다.");
        if (scope.requiredCondition && (term.match !== "ALL" || !term.conditions.some(requiredCondition))) return editorError("선택한 옵션의 필수 조건을 유지해야 합니다.");
        if (onDraft) { onDraft(S.copy(term)); d.close(); return; }
        try {
          const before = JSON.stringify(term);
          const nextTerms = S.copy(definition.terms).concat(S.copy(scope.extraTerms || [])); if (index == null) nextTerms.push(S.copy(term)); else nextTerms[index] = S.copy(term);
          F.validateLinks(nextTerms);
          const validation = context.ready ? await api(product.id, '/diagnose?draft=true', 'POST', { definition: { ...S.copy(definition), terms: nextTerms, schemaFingerprint: context.schemaFingerprint }, answers: {} }) : { valid: true };
          if (before !== JSON.stringify(term)) return editorError('검사 중 항이 변경되었습니다. 항 반영을 다시 눌러 주세요.');
          if (!validation.valid) return serverErrors(validation.issues);
        } catch (error) { return editorError(error.message); }
        if (index == null && !term.type.startsWith('MATRIX') && definition.terms.some(t => S.pricingBatch.overlap(t, term)) && !await S.confirm('같은 입력·옵션에 다른 금액 항이 있습니다. 기본금·추가금처럼 더할 항이 맞는지 확인하고 추가하세요.')) return;
        if (index == null && definition.terms.length + (scope.extraTerms || []).length >= 100) return editorError("금액·배율 항은 합쳐서 최대 100개까지 등록할 수 있습니다.");
        if (index == null) definition.terms.push(...S.copy(scope.extraTerms || []), S.copy(term)); else definition.terms[index] = S.copy(term);
        d.close(); changed(); renderTerms(); renderGroups(); S.toast("항을 계산식에 반영했습니다. 테스트한 뒤 저장해 주세요.");
      });
      draw();
      if (scope.focusAxis && term.type.startsWith('MATRIX')) { stage = 1; tableUi.matrixStep = 2; draw(); focusMatrix(`[data-axis-outside="${scope.focusAxis}"]`); }
    }
    await load();
  };

  async function downloadExcel(payload) {
    return S.withBusy(async () => {
    const headers = { "Content-Type": "application/json" }, token = S.$('meta[name="_csrf"]')?.content, csrfHeader = S.$('meta[name="_csrf_header"]')?.content;
    if (token && csrfHeader) headers[csrfHeader] = token;
    const response = await fetch(S.base + "/pricing/excel/export", { method: "POST", headers, credentials: "same-origin", body: JSON.stringify(payload) });
    if (!response.ok || response.redirected) { let message = "엑셀 다운로드에 실패했습니다."; try { message = (await response.json()).message || message; } catch {} throw Error(message); }
    const url = URL.createObjectURL(await response.blob()), a = document.createElement("a"); a.href = url; a.download = "price-matrix.xlsx"; document.body.append(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    }, "가격표 엑셀을 준비하고 있습니다.");
  }
})(window.PMS);
