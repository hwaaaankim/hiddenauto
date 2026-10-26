(function (S) {
  "use strict";
  const label = (x) => x?.labels?.management || x?.labels?.customer || x?.key || "이름 미입력";
  const fail = (message) => { throw Error(message); };
  const limitsText = (x) => `${x.min ?? "제한 없음"} ~ ${x.max ?? "제한 없음"}`;
  S.numberBundles = (q) => {
    const groups = new Map();
    for (const f of q.fields || []) {
      const key = f.bundleKey || f.key;
      if (!groups.has(key)) groups.set(key, { key, fields: [] });
      groups.get(key).fields.push(f);
    }
    return [...groups.values()];
  };
  S.numberFieldCaption = (q, f) => {
    const bundles = S.numberBundles(q), index = bundles.findIndex((b) => b.fields.some((x) => x.key === f?.key)), bundle = bundles[index];
    return label(f) + (bundle?.fields.length > 1 ? ` · 묶음 ${index + 1} (${bundle.fields.map(label).join(" + ")})` : " · 독립 입력") + ` [${f?.key || ""}]`;
  };
  S.numberBundleChecks = (q, selected) => S.numberBundles(q).map((b, i) =>
    `<label class="pms-check pm-number-bundle"><input type="checkbox" data-result-bundle value="${S.e(b.key)}" ${b.fields.every((f) => selected.includes(f.key)) ? "checked" : ""}><span><strong>${b.fields.length > 1 ? `묶음 ${i + 1} · ` : ""}${b.fields.map((f) => S.e(label(f))).join(" + ")}</strong><small>${b.fields.length > 1 ? "묶음 전체가 함께 표시됩니다" : "독립 입력·가격 항목"}</small></span></label>`).join("");
  S.readNumberBundleChecks = (root, q) => {
    const selected = new Set(S.$$('[data-result-bundle]:checked', root).map((x) => x.value));
    return S.numberBundles(q).filter((b) => selected.has(b.key)).flatMap((b) => b.fields.map((f) => f.key));
  };
  S.fieldLabelSample = (f) => `<div class="pm-live-sample" aria-live="polite"><strong>입력한 라벨이 보이는 위치</strong><div class="pms-form-grid three">${[["customer", "고객 입력 화면"], ["production", "생산팀 결과 화면"], ["management", "관리팀 결과 화면"]].map(([key, title]) => `<div><small>${title}</small><p><b>${S.e(f.labels?.[key] || "라벨 미입력")}</b> : <span class="pm-sample-value">${key === "customer" ? "___" : "500"}</span>${f.unit ? ` <b>(${S.e(f.unit)})</b>` : ""}</p></div>`).join("")}</div><small>라벨은 입력칸·결과값 앞에 붙는 이름입니다. 예: 고객용 ‘사이즈’, 생산팀용·관리팀용 ‘규격’. 단위 ‘mm’는 ‘사이즈 : ___ (mm)’로 표시됩니다.</small></div>`;
  S.numberPricingHelp = () => `<details class="pms-card pm-pricing-note" open><summary><strong>2차원 가격표를 쓰려면 먼저 숫자 필드를 그룹화하세요</strong></summary><p>묶음은 분기에서 함께 표시할 입력 단위입니다. <b>같은 묶음 안에서도 계산 항은 자유롭게 나눌 수 있습니다.</b> 필드를 체크한 것만으로는 묶음이 저장되지 않습니다.</p><ul><li><b>거울 W·H:</b> 두 필드를 체크 → ‘선택 필드 그룹화’ → 한 묶음으로 표시되는지 확인 → 가격계산에서 두 숫자를 선택해 2차원 표를 만듭니다.</li><li><b>W·D·H:</b> 세 개를 같은 묶음으로 만든 뒤 W·D·H 각각 1차원 3개, 또는 W·D 2차원 + H 1차원으로 계산할 수 있습니다. W·H 2차원 + D 1차원도 가능합니다.</li><li><b>분기에 따라 다른 입력:</b> 세면대 A용 W·D와 B용 W·D는 각각 묶고 앞 선택 조건에서 해당 묶음을 표시합니다.</li></ul><p><b>이 제품만의 입력 구성을 설정합니다.</b> 공통 비규격사이즈 그룹을 다른 제품에서도 사용해도, 여기서 정한 입력 개수와 묶음은 이 제품에 저장됩니다. 가격영향이 켜진 숫자 그룹은 전체 입력 중 최소 한 개를 가격 항·숫자 범위별 옵션 금액·숫자 적용 조건에 연결해야 계산식을 저장할 수 있습니다. W·H와 D로 나눴다면 W·H 표만 또는 D 표만 사용해도 됩니다.</p><p><b>저장 순서: 선택 필드 그룹화 → 이 질문 완성 → 검증 후 등록완료 → 가격계산 화면 다시 열기.</b> 숫자 3개를 묶어도 3차원 가격표가 되지는 않습니다. 가격표 한 개에는 숫자를 최대 2개만 사용합니다.</p></details>`;
  S.renderNumberGrouping = (root, q, redraw, readOnly = false) => {
    root.innerHTML = S.numberPricingHelp() + `<div class="pms-actions"><strong>함께 표시할 입력묶음 · 가격 항은 1개/2개씩 선택</strong><span data-bundle-selection role="status">선택 0개</span><button type="button" data-create-bundle ${readOnly ? "disabled" : ""}>선택 필드 그룹화</button><button type="button" data-ungroup ${readOnly ? "disabled" : ""}>선택 묶음 해제</button></div><p class="pms-help">두 개 이상 선택 후 그룹화하세요. 분기에서는 묶음 전체를 선택합니다. 묶음을 해제하면 기존 합계 제한·표시 연결도 확인해야 합니다.</p>${S.numberBundles(q).map((b) => `<article class="pms-card pm-number-bundle"><div class="pm-bundle-heading"><strong>${b.fields.length > 1 ? `묶음 · ${b.fields.length}개 입력` : "독립 · 1개 입력"} · ${b.fields.map((f) => S.e(label(f))).join(" + ")}</strong><small>${b.fields.length > 1 ? "가격표에서 1개 또는 2개 선택 가능" : "현재는 1차원만 가능"}</small></div><div class="pms-check-grid">${b.fields.map((f) => `<label class="pms-check"><input type="checkbox" data-group-field value="${S.e(f.key)}" ${readOnly ? "disabled" : ""}>${S.e(label(f))}</label>`).join("")}</div></article>`).join("")}`;
    S.$$('[data-group-field]', root).forEach(input => input.onchange = () => {
      S.$('[data-bundle-selection]', root).textContent = `선택 ${S.$$('[data-group-field]:checked', root).length}개 · 그룹화 버튼을 눌러 적용`;
    });
    S.$('[data-create-bundle]', root).onclick = () => {
      const keys = S.$$('[data-group-field]:checked', root).map((x) => x.value);
      if (keys.length < 2) return S.toast("함께 묶을 입력 필드를 두 개 이상 선택해 주세요.");
      const chosen = new Set(keys);
      const partial = S.numberBundles(q).find((b) => b.fields.length > 1 && b.fields.some((f) => chosen.has(f.key)) && !b.fields.every((f) => chosen.has(f.key)));
      if (partial) return S.toast("기존 묶음은 전체 필드를 선택해야 합니다. 먼저 묶음을 해제하면 필드별로 다시 묶을 수 있습니다.");
      const key = S.key("B");
      q.fields.filter((f) => chosen.has(f.key)).forEach((f) => { f.bundleKey = key; });
      S.dirty = true; redraw();
    };
    S.$('[data-ungroup]', root).onclick = () => {
      const keys = new Set(S.$$('[data-group-field]:checked', root).map((x) => x.value));
      if (!keys.size) return S.toast("해제할 묶음의 필드를 선택해 주세요.");
      const bundles = new Set(q.fields.filter((f) => keys.has(f.key)).map((f) => f.bundleKey).filter(Boolean));
      q.fields.filter((f) => bundles.has(f.bundleKey)).forEach((f) => { f.bundleKey = null; });
      S.dirty = true; redraw();
    };
  };
  const numberControls = (f, i) => `<article class="pms-card"><h3>${S.e(label(f))} · 기본 입력 제한</h3><div class="pms-form-grid four">${S.checkField("입력 여부", "필수 입력", `fields.${i}.required`, f.required)}${S.checkField("음수 입력", "음수 허용", `fields.${i}.allowNegative`, f.allowNegative)}${S.field("단위 (표시용)", `fields.${i}.unit`, f.unit || "", "text", 'maxlength="20"')}${S.field("입력 간격", `fields.${i}.step`, f.step, "number", 'min="0.001" step="0.001"')}${S.field("기본 최소값", `fields.${i}.min`, f.min, "number", 'step="0.001"')}${S.field("기본 최대값", `fields.${i}.max`, f.max, "number", 'step="0.001"')}</div><p class="pms-help">기본 최소·최대값은 실제 주문 가능한 입력 범위입니다. 빈칸이면 해당 제한을 따로 두지 않습니다. 가격 엑셀의 첫·마지막 기준으로 자동 변경되지 않으며, 가격표를 등록할 때 표 밖 입력 처리도 확인해야 합니다.</p><div data-limit-sample="${i}">${S.fieldLabelSample(f)}</div></article>`;
  const conditionPreview = (source, conditions) => (conditions || []).map((c) => {
    if (["PRESENT", "ABSENT"].includes(c.operator)) return `${S.isChoice(source?.control) ? label(source) : label(source?.fields?.find((f) => f.key === c.fieldKey))} ${c.operator === "PRESENT" ? "입력·선택 있음" : "입력·선택 없음"}`;
    if (S.isChoice(source?.control)) return (c.operator === "NE" ? "다음 선택지 제외: " : "") + (c.choiceKeys || []).map((key) => label(source.choices.find((x) => x.key === key)) || key).join(" / ");
    const field = source?.fields?.find((f) => f.key === c.fieldKey);
    if (c.operator === "RANGE") return `${label(field)} ${c.lower}${c.lowerInclusive ? " 이상" : " 초과"} ~ ${c.upper}${c.upperInclusive ? " 이하" : " 미만"}`;
    return `${label(field)} ${c.lower ?? ""} ${{GE:"이상",GT:"초과",LE:"이하",LT:"미만",EQ:"일치",PRESENT:"입력함",ABSENT:"입력 없음"}[c.operator] || c.operator}`;
  }).join(" · ");
  function validateBounds(row) {
    if (row.min == null && row.max == null) fail("허용 최소값 또는 최대값을 한 개 이상 입력해 주세요. 예: 0~4개는 최소 0, 최대 4입니다.");
    if ((row.min != null && !Number.isFinite(row.min)) || (row.max != null && !Number.isFinite(row.max))) fail("제한값은 유한한 숫자로 입력해 주세요.");
    if (row.min != null && row.max != null && row.min > row.max) fail("최소값이 최대값보다 큽니다. 최소·최대 순서를 확인해 주세요.");
  }
  function conditionEditor(root, source, owner, redraw, numericFieldKey, allowPresence = false) {
    if (S.isChoice(source.control)) {
      const c = owner.conditions[0] || {groupKey: source.key, fieldKey: null, operator:"EQ", choiceKeys: []};
      owner.conditions = [c];
      const compare = !["PRESENT", "ABSENT"].includes(c.operator);
      root.innerHTML = `${allowPresence ? S.select("앞 답변의 조건", "conditions.0.operator", c.operator, {EQ:"선택지 중 하나와 일치",NE:"아래 선택지가 모두 선택되지 않음",PRESENT:"어떤 선택지든 선택함",ABSENT:"선택하지 않음"}) : ""}<p class="pms-help">한 조건에 묶은 선택지 중 하나라도 선택하면 일치합니다. 여러 개 선택형에서 서로 다른 조건들이 동시에 맞으면 중복으로 검증됩니다. 선택하지 않은 경우는 별도 ‘선택하지 않음’ 조건으로 정하세요.</p>${compare ? `<div class="pms-check-grid">${source.choices.map((x) => `<label class="pms-check"><input type="checkbox" data-case-choice value="${S.e(x.key)}" ${(c.choiceKeys || []).includes(x.key) ? "checked" : ""}>${S.e(label(x))}</label>`).join("")}</div>` : ""}`;
      S.bind(root, owner, (path) => { if (path === "conditions.0.operator") { if (["PRESENT", "ABSENT"].includes(c.operator)) c.choiceKeys = []; redraw(); } });
      S.$$('[data-case-choice]', root).forEach((input) => input.onchange = () => { c.choiceKeys = S.$$('[data-case-choice]:checked', root).map((x) => x.value); });
      return;
    }
    root.innerHTML = `<div class="pms-actions"><strong>숫자 범위 조건 · 모두 만족 (AND)</strong><button type="button" data-condition-add>+ 조건</button></div>${owner.conditions.map((c, i) => `<article class="pms-card pm-condition-editor"><div class="pms-form-grid four">${S.select("영향을 주는 입력 필드", `conditions.${i}.fieldKey`, c.fieldKey, Object.fromEntries(source.fields.filter((f) => !numericFieldKey || f.key === numericFieldKey).map((f) => [f.key, S.numberFieldCaption(source, f)])))}${S.select("비교 방식", `conditions.${i}.operator`, c.operator, {GE:"이상",GT:"초과",LE:"이하",LT:"미만",EQ:"일치",RANGE:"구간",...(allowPresence ? {PRESENT:"입력했을 때",ABSENT:"입력이 없을 때"} : {})})}${["PRESENT","ABSENT"].includes(c.operator) ? "" : S.field("기준값 / 하한", `conditions.${i}.lower`, c.lower, "number", 'step="0.001"')}${c.operator === "RANGE" ? S.field("상한", `conditions.${i}.upper`, c.upper, "number", 'step="0.001"') + S.check("하한 포함", `conditions.${i}.lowerInclusive`, c.lowerInclusive) + S.check("상한 포함", `conditions.${i}.upperInclusive`, c.upperInclusive) : ""}</div><button type="button" data-condition-delete="${i}">이 조건 삭제</button></article>`).join("")}`;
    S.bind(root, owner, (path) => { if (path.endsWith(".operator")) redraw(); });
    S.$('[data-condition-add]', root).onclick = () => {
      if (owner.conditions.length >= 20) return S.toast("하나의 범위에는 최대 20개 조건을 등록할 수 있습니다.");
      owner.conditions.push({groupKey:source.key,fieldKey:numericFieldKey || source.fields[0]?.key,operator:"GE",choiceKeys:[],lower:0,upper:null,lowerInclusive:true,upperInclusive:true}); redraw();
    };
    S.$$('[data-condition-delete]', root).forEach((b) => b.onclick = () => { owner.conditions.splice(Number(b.dataset.conditionDelete),1); redraw(); });
  }
  S.renderIndependentLimits = (root, q, context) => {
    const { limits, redraw, getProcess, readOnly = false } = context;
    root.innerHTML = `<p class="pms-help"><strong>입력제한과 분기는 독립적입니다.</strong> 기본 제한은 항상 적용됩니다. 아래에서 ‘각 값’ 또는 ‘묶음 합계’를 추가하고 앞 질문의 답변에 따라 허용범위를 바꿀 수 있습니다. 추가 제한은 기본 제한과 동시에 만족해야 합니다. 기존 최대값보다 큰 값까지 허용하려면 기본 최대값도 넓혀 주세요.</p>${q.fields.map(numberControls).join("")}<div class="pms-actions pms-section"><strong>추가 입력제한 · 각 값 / 묶음 합계 / 앞 질문 영향</strong><button type="button" data-add-independent-limit ${readOnly ? "disabled" : ""}>+ 입력제한 설정</button></div>${limits.filter((r) => r.targetKey === q.key).map((r) => `<article class="pms-card pm-limit-card"><h3>${S.e(r.name)}</h3><p><strong>${r.aggregation === "SUM" ? "묶음 합계" : "각 필드 값"}:</strong> ${r.fieldKeys.map((k) => S.e(label(q.fields.find((f) => f.key === k)))).join(" + ")}</p>${r.sourceKey ? `<p><strong>앞 질문:</strong> ${S.e(label(getProcess().questions.find((s) => s.key === r.sourceKey)))}</p>${r.cases.map((c) => `<p>${S.e(c.name)} · ${S.e(conditionPreview(getProcess().questions.find((s) => s.key === r.sourceKey), c.conditions))} → <b>${S.e(limitsText(c))}</b></p>`).join("")}` : `<p><strong>허용 범위:</strong> ${S.e(limitsText(r))}</p>`}<div class="pms-actions"><button type="button" data-limit-edit="${S.e(r.key)}">${readOnly ? "설정 보기" : "수정"}</button><button type="button" data-limit-delete="${S.e(r.key)}" ${readOnly ? "disabled" : ""}>삭제</button></div></article>`).join("") || '<p class="pms-empty">기본 최대·최소만 사용 중입니다. 합계·앞 질문 조건이 필요하면 추가해 주세요.</p>'}`;
    S.bind(root, q, (path) => {
      const m = /^fields\.(\d+)\.(unit|labels\..*)$/.exec(path);
      if (m) S.$(`[data-limit-sample="${m[1]}"]`, root).innerHTML = S.fieldLabelSample(q.fields[Number(m[1])]);
    });
    S.$('[data-add-independent-limit]', root).onclick = () => limits.length >= 200 ? S.toast("입력제한은 프로세스 전체에서 최대 200개입니다. 사용하지 않는 제한을 정리해 주세요.") : editLimit(null);
    S.$$('[data-limit-edit]', root).forEach((b) => b.onclick = () => editLimit(limits.find((r) => r.key === b.dataset.limitEdit)));
    S.$$('[data-limit-delete]', root).forEach((b) => b.onclick = () => { limits.splice(limits.findIndex((r) => r.key === b.dataset.limitDelete),1); S.dirty = true; redraw(); });
    function editLimit(old) {
      const m = old ? S.copy(old) : {key:S.key("L"),name:"",targetKey:q.key,fieldKeys:[],aggregation:"EACH",min:null,max:null,sourceKey:null,cases:[]};
      m.cases ??= []; m.fieldKeys ??= [];
      let useSource = !!m.sourceKey, selectedSourceField = m.cases.flatMap((c) => c.conditions).find((c) => c.fieldKey)?.fieldKey || "";
      const available = getProcess().questions.slice(0,getProcess().questions.findIndex((x) => x.key === q.key)).filter((x) => S.isChoice(x.control) || x.control === "NUMBER");
      const dialog = S.dialog("독립 입력제한 설정 · " + label(q), ""), body = S.$('.pms-dialog-body', dialog);
      let part = 0;
      function paint() {
        const source = available.find((x) => x.key === m.sourceKey);
        body.innerHTML = `<ol class="pm-stepper"><li class="${part===0?"active":""}">1. 제한 대상·방식</li><li class="${part===1?"active":""}">2. 허용 범위·조건</li><li class="${part===2?"active":""}">3. 확인</li></ol><div data-limit-content></div><div class="pms-actions pm-stage-actions">${part ? '<button type="button" data-limit-back>이전</button>' : ""}<button type="button" data-limit-next class="primary">${part === 2 ? readOnly ? "닫기" : "설정 적용" : "다음"}</button></div>`;
        const box = S.$('[data-limit-content]',body);
        if (part === 0) {
          box.innerHTML = `<div class="pms-form-grid two">${S.field("입력제한 이름", "name", m.name, "text", 'maxlength="120"')}${S.select("제한 계산 방식", "aggregation", m.aggregation, {EACH:"각 값의 최소·최대",SUM:"묶음 값의 합계 최소·최대"})}</div><p class="pms-help">각 값 방식은 선택한 모든 필드에 동일한 범위를 적용합니다. 필드별 범위가 다르면 필드를 하나씩 선택해 제한을 따로 만드세요. 합계 방식은 같은 묶음의 필드 전체(2개 이상)를 선택해야 합니다.</p><div class="pm-limit-source-row"><fieldset class="pms-inline-fieldset"><legend>제한할 숫자 입력</legend><div class="pms-check-grid">${q.fields.map((f) => `<label class="pms-check"><input type="checkbox" data-limit-field value="${S.e(f.key)}" ${m.fieldKeys.includes(f.key)?"checked":""}>${S.e(label(f))}</label>`).join("")}</div></fieldset><label class="pms-check"><input type="checkbox" data-use-source ${useSource?"checked":""}><span>앞 스탭의 영향을 받음</span></label></div><p class="pms-help">앞 질문은 표시 여부와 별개로 허용범위만 정합니다. 필요한 앞 질문을 건너뛰는 분기는 전체 검증에서 거부됩니다. 숫자 기준은 제한 한 개당 한 필드입니다. W와 H의 제한을 각각 추가하면 두 제한을 동시에 만족하는 범위만 허용합니다. W·H 조합별 선택지를 바꾸려면 ‘복합 조건·여러 동작 연결’의 AND 조건을 사용하세요.</p>${useSource ? S.select("영향을 주는 앞 질문", "sourceKey", m.sourceKey || "", {"":"앞 질문 선택",...Object.fromEntries(available.map((x)=>[x.key,label(x)]))}) + (source?.control === "NUMBER" ? S.select("영향을 주는 숫자 입력 필드", "sourceField", selectedSourceField, {"":"입력 필드 선택",...Object.fromEntries(source.fields.map((f)=>[f.key,S.numberFieldCaption(source,f)]))}).replace('data-path="sourceField"','data-source-field') : "") : ""}`;
          S.bind(box,m,(path,value) => { if(path === "sourceKey"){m.cases=[];selectedSourceField="";paint();} if(path === "aggregation") paint(); });
          S.$('[data-use-source]',box).onchange=(e)=>{useSource=e.target.checked;if(!useSource){m.sourceKey=null;m.cases=[];}paint();};
          S.$('[data-source-field]',box)?.addEventListener('change',(e)=>{const key=e.target.value;if(selectedSourceField!==key)m.cases=[];selectedSourceField=key;});
          S.$$('[data-limit-field]',box).forEach((el)=>el.onchange=()=>{m.fieldKeys=S.$$('[data-limit-field]:checked',box).map((x)=>x.value);});
        } else if(part===1){
          box.innerHTML = useSource ? `<p class="pms-help"><strong>조건마다 허용범위를 모두 채우세요.</strong> 조건은 영향 필드의 전체 입력범위를 빈 구간·겹침 없이 나눠야 합니다. 예: W ≤ 600 / 600 &lt; W ≤ 800 / W &gt; 800. 600·800 경계는 한 조건에만 포함합니다. 각 조건 안의 비교식은 모두 만족(AND)해야 합니다. 앞 질문을 선택사항으로 두었다면 ‘입력·선택 없음’ 조건의 허용값도 등록하세요. 이것은 표시된 질문을 비워 둔 경우이며, 질문 자체를 분기로 건너뛰는 것과는 다릅니다.</p><div class="pms-actions"><button type="button" data-limit-case-add>+ ${S.isChoice(source.control)?"선택지 묶음":"숫자 범위"} 조건</button>${source.control==="NUMBER" && (source.numberCases||[]).length ? '<button type="button" data-import-cases>앞 질문의 범위 조건 가져오기</button>' : ""}</div>${m.cases.map((c,i)=>`<article class="pms-card"><strong>${S.e(c.name)}</strong><p>${S.e(conditionPreview(source,c.conditions))} → <b>${S.e(limitsText(c))}</b></p><button type="button" data-map-edit="${i}">조건·허용값 ${readOnly?"보기":"수정"}</button><button type="button" data-map-delete="${i}">삭제</button></article>`).join("")}` : `<h3>조건 없이 적용할 ${m.aggregation==="SUM"?"합계":"각 값"} 범위</h3><div class="pms-form-grid two">${S.field("최소값", "min",m.min,"number",'step="0.001"')}${S.field("최대값", "max",m.max,"number",'step="0.001"')}</div><p class="pms-help">합계 0~4이면 0+4, 1+3, 2+2를 허용하며 3+2는 거부합니다. 각각의 필수 입력·음수·기본 제한도 함께 검사합니다.</p>`;
          if(!useSource)S.bind(box,m);
          else{
            S.$('[data-limit-case-add]',box).onclick=()=>m.cases.length>=100?S.toast("하나의 입력제한에 조건은 최대 100개입니다."):editCase(null);
            S.$$('[data-map-edit]',box).forEach((b)=>b.onclick=()=>editCase(Number(b.dataset.mapEdit)));
            S.$$('[data-map-delete]',box).forEach((b)=>b.onclick=()=>{m.cases.splice(Number(b.dataset.mapDelete),1);paint();});
            S.$('[data-import-cases]',box)?.addEventListener('click',()=>{
              const ranges=(source.numberCases||[]).filter((r)=>r.conditions.length&&r.conditions.every((c)=>c.fieldKey===selectedSourceField));
              if(!ranges.length)return S.toast("선택한 숫자 필드만 사용한 앞 질문의 범위 조건이 없습니다. 직접 숫자 범위를 추가해 주세요.");
              for(const c of ranges)if(!m.cases.some((r)=>r.name===c.name))m.cases.push({key:S.key("LC"),name:c.name,conditions:S.copy(c.conditions),min:null,max:null});
              paint();
            });
          }
        } else box.innerHTML=`<div class="pm-rule-summary"><strong>${S.e(m.name)}</strong><span>${m.aggregation==="SUM"?"묶음 합계":"각 값"}: ${m.fieldKeys.map((k)=>S.e(label(q.fields.find((f)=>f.key===k)))).join(" + ")}</span>${useSource?`<strong>앞 질문: ${S.e(label(source))}</strong>${m.cases.map((c)=>`<span>${S.e(c.name)} · ${S.e(conditionPreview(source,c.conditions))} → ${S.e(limitsText(c))}</span>`).join("")}`:`<span>허용값: ${S.e(limitsText(m))}</span>`}</div><p class="pms-help">설정 적용 후 ‘전체 경로 검증’에서 조건 범위의 빠짐·겹침, 앞 질문 건너뜀, 함께 적용되는 제한의 모순을 검사합니다. 입력제한만으로 질문·필드가 숨겨지지는 않습니다.</p>`;
        if(readOnly)S.$$('input,select,textarea,button:not([data-limit-next]):not([data-limit-back]):not([data-map-edit])',box).forEach((el)=>el.disabled=true);
        S.$('[data-limit-back]',body)?.addEventListener('click',()=>{part--;paint();});
        S.$('[data-limit-next]',body).onclick=(e)=>S.run(e.currentTarget,async()=>{
          if(readOnly&&part===2){dialog.close();return;}
          if(part===0 && !readOnly){
            if(!m.name.trim())fail("입력제한 이름을 입력해 주세요. 예: 혼합 문의 합계 제한.");
            if(!m.fieldKeys.length)fail("제한을 적용할 입력 필드를 선택해 주세요.");
            if(m.aggregation==="SUM"){
              const bundle=S.numberBundles(q).find((b)=>b.fields.length===m.fieldKeys.length&&b.fields.every((f)=>m.fieldKeys.includes(f.key)));
              if(m.fieldKeys.length<2||!bundle)fail("합계 제한은 같은 묶음의 필드 전체에 적용해야 합니다. 먼저 답변 등록 단계에서 그룹화한 뒤 이 묶음의 모든 필드를 선택해 주세요.");
            }
            if(useSource&&!source)fail("영향을 주는 앞 질문을 선택해 주세요.");
            if(useSource&&source.control==="NUMBER"&&!selectedSourceField)fail("앞 질문에서 영향주는 숫자 입력 필드를 선택해 주세요. 예: 사이즈의 W.");
          }
          if(part===1 && !readOnly){
            if(useSource){if(!m.cases.length)fail("조건과 각 조건의 입력제한을 추가해 주세요.");m.cases.forEach(validateBounds);}
            else validateBounds(m);
          }
          if(part===2){
            if(!useSource){m.sourceKey=null;m.cases=[];}else{m.min=null;m.max=null;}
            delete m.sourceField;
            const index=limits.findIndex((x)=>x.key===m.key);if(index<0)limits.push(m);else limits[index]=m;
            S.dirty=true;dialog.close();redraw();
          }else{part++;paint();}
        });
      }
      function editCase(index){
        const source=available.find((x)=>x.key===m.sourceKey),c=index==null?{key:S.key("LC"),name:"",conditions:[],min:null,max:null}:S.copy(m.cases[index]);
        const child=S.dialog("조건별 입력제한 · "+label(source),""),cb=S.$('.pms-dialog-body',child);
        function cp(){
          cb.innerHTML=`${S.field("조건 이름","name",c.name,"text",'maxlength="120"')}<div data-condition-box></div><h3>${m.aggregation==="SUM"?"이 조건에서 현재 묶음 합계의":"이 조건에서 각 현재 필드의"} 허용범위</h3><div class="pms-form-grid two">${S.field("최소값","min",c.min,"number",'step="0.001"')}${S.field("최대값","max",c.max,"number",'step="0.001"')}</div><div class="pms-actions"><button type="button" data-case-apply class="primary">${readOnly?"닫기":"조건 적용"}</button></div>`;
          S.bind(cb,c);conditionEditor(S.$('[data-condition-box]',cb),source,c,cp,selectedSourceField,true);
          if(readOnly)S.$$('input,select,textarea,button:not([data-case-apply])',cb).forEach((x)=>x.disabled=true);
          S.$('[data-case-apply]',cb).onclick=(e)=>S.run(e.currentTarget,async()=>{
            if(readOnly){child.close();return;}
            if(!c.name.trim())fail("조건 이름을 입력해 주세요.");
            if(!c.conditions.length||S.isChoice(source.control)&&!["PRESENT","ABSENT"].includes(c.conditions[0].operator)&&!c.conditions[0].choiceKeys.length)fail("선택지 또는 숫자 비교 조건을 추가해 주세요.");
            if(source.control==="NUMBER")for(const x of c.conditions){if(["PRESENT","ABSENT"].includes(x.operator))continue;if(x.lower==null||!Number.isFinite(x.lower))fail("모든 비교식에 기준값을 입력해 주세요.");if(x.operator==="RANGE"&&(x.upper==null||!Number.isFinite(x.upper)||x.upper<x.lower||(x.upper===x.lower&&(!x.lowerInclusive||!x.upperInclusive))))fail("숫자 구간의 상한·하한을 확인해 주세요. 비어 있는 구간은 사용할 수 없습니다.");}
            validateBounds(c);if(m.cases.some((x,i)=>i!==index&&x.name===c.name))fail("조건 이름이 중복됩니다. 조건마다 구분되는 이름을 입력해 주세요.");
            if(index==null)m.cases.push(c);else m.cases[index]=c;child.close();paint();
          });
        }cp();
      }paint();
    }
  };
  S.renderConditionalNotices = (root,q,context) => {
    const {notices,redraw,readOnly=false}=context;
    const rows=notices.filter((n)=>n.targetKey===q.key), legacy=q.fields.filter((f)=>f.guide?.trim());
    root.innerHTML=`<div class="pms-actions pms-section"><strong>조건에 연결하는 안내메시지</strong><button type="button" data-notice-add ${readOnly?"disabled":""}>+ 안내 조건</button></div><p class="pms-help">숫자는 입력값의 범위에 맞을 때 안내합니다. 텍스트·파일은 ‘항상 출력’ 또는 ‘입력이 없을 때만 출력’을 선택합니다. 일반 사용방법은 고객 튜토리얼·FAQ에 작성하세요.</p>${legacy.map((f)=>`<article class="pms-card"><strong>기존 입력 시 안내 · ${S.e(label(f))}</strong><p>${S.e(f.guide)}</p><p class="pms-help">기존에는 이 필드를 입력한 뒤 표시됩니다. 아래에서 새 출력 조건을 선택하면 기존 안내가 조건별 안내로 이동합니다.</p><button type="button" data-legacy-notice="${S.e(f.key)}" ${readOnly?"disabled":""}>항상 / 입력 없음 조건으로 변경</button></article>`).join("")}${rows.map((n)=>`<article class="pms-card"><strong>${S.e(label(q.fields.find((f)=>f.key===n.fieldKey)))}</strong><p>${S.e(n.mode==="ALWAYS"?"항상 출력":n.mode==="ABSENT"?"입력이 없을 때만 출력":conditionPreview(q,n.conditions))}</p><p>${S.e(n.message)}</p><button type="button" data-notice-edit="${S.e(n.key)}">${readOnly?"보기":"수정"}</button><button type="button" data-notice-delete="${S.e(n.key)}" ${readOnly?"disabled":""}>삭제</button></article>`).join("")}`;
    S.$('[data-notice-add]',root).onclick=()=>notices.length>=200?S.toast("조건별 안내메시지는 프로세스 전체에서 최대 200개입니다."):edit(null);
    S.$$('[data-legacy-notice]',root).forEach((b)=>b.onclick=()=>{const f=q.fields.find((x)=>x.key===b.dataset.legacyNotice);edit({key:S.key("M"),targetKey:q.key,fieldKey:f.key,message:f.guide,mode:"ABSENT",conditions:[]},f);});
    S.$$('[data-notice-edit]',root).forEach((b)=>b.onclick=()=>edit(notices.find((n)=>n.key===b.dataset.noticeEdit)));
    S.$$('[data-notice-delete]',root).forEach((b)=>b.onclick=()=>{notices.splice(notices.findIndex((n)=>n.key===b.dataset.noticeDelete),1);S.dirty=true;redraw();});
    function edit(old, legacyField){
      const n=old?S.copy(old):{key:S.key("M"),targetKey:q.key,fieldKey:q.fields[0]?.key||null,message:"",mode:q.control==="NUMBER"?"CONDITIONAL":"ABSENT",conditions:[]};
      const d=S.dialog("안내메시지 표시 조건",""),body=S.$('.pms-dialog-body',d);
      function paint(){
        body.innerHTML=`<div class="pms-form-grid two">${S.select("안내가 표시될 입력 필드","fieldKey",n.fieldKey,Object.fromEntries(q.fields.map((f)=>[f.key,q.control === "NUMBER" ? S.numberFieldCaption(q,f) : label(f)])))}${q.control==="NUMBER"?S.select("출력 시점","mode",n.mode,{CONDITIONAL:"숫자 범위에 해당할 때",ALWAYS:"반드시 출력 (전체 범위)"}):`<div><span>출력 시점 · 하나를 선택하세요</span><label class="pms-check"><input type="checkbox" data-notice-mode="ALWAYS" ${n.mode==="ALWAYS"?"checked":""}>반드시 출력</label><label class="pms-check"><input type="checkbox" data-notice-mode="ABSENT" ${n.mode==="ABSENT"?"checked":""}>이 필드의 입력이 없을 때만 출력</label></div>`}</div><div class="pms-form-grid one">${S.textarea("고객에게 보여줄 안내메시지","message",n.message,2000)}</div>${n.mode==="CONDITIONAL"?'<div data-notice-conditions></div>':""}<p class="pms-help">예: W가 800 초과일 때 ‘큰 제품은 배송 전 출입문 폭을 확인해 주세요.’ / 요청 파일이 없을 때 ‘참고 도면을 첨부하면 상담이 빠릅니다.’</p><button type="button" data-notice-apply class="primary">${readOnly?"닫기":"안내 적용"}</button>`;
        S.bind(body,n,(path)=>{if(path==="mode"){if(n.mode!=="CONDITIONAL")n.conditions=[];paint();}if(path==="fieldKey"&&n.mode==="CONDITIONAL"){n.conditions=[];paint();}});
        S.$$('[data-notice-mode]',body).forEach((input)=>input.onchange=()=>{n.mode=input.dataset.noticeMode;paint();});
        if(n.mode==="CONDITIONAL")conditionEditor(S.$('[data-notice-conditions]',body),q,n,paint,n.fieldKey,true);
        if(readOnly)S.$$('input,select,textarea,button:not([data-notice-apply])',body).forEach((x)=>x.disabled=true);
        S.$('[data-notice-apply]',body).onclick=(e)=>S.run(e.currentTarget,async()=>{
          if(readOnly){d.close();return;}
          if(!n.message.trim())fail("고객에게 표시할 안내메시지를 입력해 주세요.");
          if(n.mode==="CONDITIONAL"&&!n.conditions.length)fail("숫자 범위 조건을 하나 이상 추가해 주세요. 모든 값에 안내하려면 ‘반드시 출력’을 선택하세요.");
          if(n.mode==="CONDITIONAL")for(const c of n.conditions){if(["PRESENT","ABSENT"].includes(c.operator))continue;if(c.lower==null||!Number.isFinite(c.lower))fail("범위 조건의 기준값을 입력해 주세요.");if(c.operator==="RANGE"&&(c.upper==null||!Number.isFinite(c.upper)||c.upper<c.lower||(c.upper===c.lower&&(!c.lowerInclusive||!c.upperInclusive))))fail("안내 범위의 상한·하한을 확인해 주세요.");}
          const index=notices.findIndex((x)=>x.key===n.key);if(index<0)notices.push(n);else notices[index]=n;if(legacyField)legacyField.guide="";S.dirty=true;d.close();redraw();
        });
      }paint();
    }
  };
})(window.PMS);
