(function (S) {
  "use strict";
  const name = (q) => q?.labels?.customer || q?.key || "질문 없음";
  const effectNames = { SHOW: "질문 표시", HIDE: "지정한 질문만 건너뜀", ALLOW: "표시할 답변·숫자 묶음 지정", RENAME: "질문 문구 변경", REQUIRE: "필수 답변으로 변경", OPTIONAL: "선택 답변으로 변경" };
  S.advancedRelationDialog = function (sourceKey, original, context) {
    const process = S.copy(context.getProcess ? context.getProcess() : context.process);
    const find = (key) => process.questions.find((q) => q.key === key);
    const targets = () => {
      const lastSource = Math.max(-1, ...model.conditions.map((c) => process.questions.findIndex((q) => q.key === c.groupKey)));
      return process.questions.filter((q, index) => !q.fixed && index > lastSource);
    };
    const initial = find(sourceKey) || process.questions[0];
    const makeCondition = (q) => ({ groupKey: q?.key || "", fieldKey: S.isChoice(q?.control) ? null : q?.fields?.[0]?.key || "", operator: S.isChoice(q?.control) ? "EQ" : q?.control === "NUMBER" ? "GE" : "PRESENT", choiceKeys: [], text: "", lower: q?.control === "NUMBER" ? 0 : null, upper: null, lowerInclusive: true, upperInclusive: true });
    const model = original ? S.copy(original) : { key: S.key("R"), name: "", match: "ALL", conditions: [makeCondition(initial)], actions: [] };
    const readOnly = !!context.readOnly;
    const fieldName = (q, f) => {
      if (q?.control !== "NUMBER") return f.labels.management;
      const bundles = S.numberBundles(q), i = bundles.findIndex((b) => b.fields.some((x) => x.key === f.key));
      return `${bundles[i]?.fields.length > 1 ? "묶음 " + (i + 1) : "독립 입력 " + (i + 1)} · ${f.labels.management} (${f.key})`;
    };
    const dialog = S.dialog(readOnly ? "복합 연관관계 조회" : "복합 연관관계 설정", "", { foot: readOnly ? "" : '<button type="button" class="primary" data-save-advanced>구조 확인 후 임시 연결</button>' });
    const body = S.$(".pms-dialog-body", dialog);
    function conditionHtml(c, i) {
      const q = find(c.groupKey), base = "conditions." + i;
      const choice = S.isChoice(q?.control), number = q?.control === "NUMBER", file = q?.control === "FILE";
      const operators = choice || (!number && !file) ? { EQ: "일치", NE: "불일치", PRESENT: "입력·선택 있음", ABSENT: "입력·선택 없음" } : file ? { PRESENT: "파일 있음", ABSENT: "파일 없음" } : { GE: "이상", GT: "초과", LE: "이하", LT: "미만", EQ: "일치", NE: "불일치", RANGE: "범위", PRESENT: "입력 있음", ABSENT: "입력 없음" };
      const compare = !["PRESENT", "ABSENT"].includes(c.operator);
      return `<article class="pms-card pm-condition-editor" data-advanced-condition="${i}"><header class="pm-card-heading"><strong>조건 ${i + 1}</strong><button type="button" class="danger" data-remove-condition="${i}">조건 삭제</button></header><div class="pms-form-grid two">${S.select("영향을 주는 질문", base + ".groupKey", c.groupKey, Object.fromEntries(process.questions.map((q) => [q.key, name(q)])))}${!choice ? S.select("조건에 사용할 입력 필드", base + ".fieldKey", c.fieldKey, Object.fromEntries((q?.fields || []).map((f) => [f.key, fieldName(q, f)]))) : ""}${S.select("조건 방식", base + ".operator", c.operator, operators)}${number && compare ? S.field(c.operator === "RANGE" ? "구간 시작" : "비교값", base + ".lower", c.lower, "number", 'step="0.001"') : !choice && !file && compare ? S.field("비교 문자", base + ".text", c.text || "") : ""}${number && c.operator === "RANGE" ? S.field("구간 끝", base + ".upper", c.upper, "number", 'step="0.001"') + S.check("시작값 포함", base + ".lowerInclusive", c.lowerInclusive) + S.check("끝값 포함", base + ".upperInclusive", c.upperInclusive) : ""}</div>${choice && compare ? `<div class="pms-check-grid">${(q?.choices || []).map((o) => `<label class="pms-check"><input type="checkbox" data-advanced-choice="${i}" value="${S.e(o.key)}" ${(c.choiceKeys || []).includes(o.key) ? "checked" : ""}>${S.e(o.labels.management)}</label>`).join("")}</div><p class="pms-help">한 조건 안에 여러 선택지를 넣으면 그중 하나가 선택될 때 일치합니다. 서로 다른 질문의 조건은 아래 결합 방식으로 처리합니다.</p>` : ""}</article>`;
    }
    function actionHtml(a, i) {
      const q = find(a.targetKey), base = "actions." + i;
      const numeric = q?.control === "NUMBER", eligible = targets();
      const validTarget = eligible.some((target) => target.key === a.targetKey);
      const targetOptions = { "": "대상 질문을 선택해 주세요", ...Object.fromEntries(eligible.map((target) => [target.key, name(target)])) };
      if (a.targetKey && !validTarget) targetOptions[a.targetKey] = "순서·고정 여부 확인 필요 · " + name(q);
      return `<article class="pms-card" data-advanced-action="${i}"><header class="pm-card-heading"><strong>동작 ${i + 1}</strong><button type="button" class="danger" data-remove-action="${i}">동작 삭제</button></header><div class="pms-form-grid two">${S.select("영향받을 질문", base + ".targetKey", a.targetKey, targetOptions)}${S.select("적용 동작", base + ".effect", a.effect, effectNames)}</div>${a.targetKey && !validTarget ? '<p class="pms-alert">현재 대상이 조건보다 앞에 있거나 고정 사양입니다. 모든 시작 조건보다 뒤의 질문을 선택해 주세요. 기존 대상은 자동으로 다른 질문으로 바뀌지 않습니다.</p>' : ""}${a.effect === "RENAME" ? S.field("조건이 맞을 때 보여줄 질문", base + ".questionText", a.questionText || "", "text", 'maxlength="300"') : ""}${a.effect === "ALLOW" ? numeric ? `<div class="pms-check-grid">${S.numberBundleChecks(q, a.choiceKeys || [])}</div><p class="pms-help">숫자 묶음은 전체 필드가 함께 표시됩니다. 공통 H도 표시할 때 선택하세요. 입력 가능한 수량은 별도의 입력 제한에서 설정합니다.</p>` : S.isChoice(q?.control) ? `<div class="pms-check-grid">${(q.choices || []).map((o) => `<label class="pms-check"><input type="checkbox" data-advanced-allowed="${i}" value="${S.e(o.key)}" ${(a.choiceKeys || []).includes(o.key) ? "checked" : ""}>${S.e(o.labels.management)}</label>`).join("")}</div>` : '<p class="pms-alert">답변 제한은 선택형 또는 숫자입력형 질문에 적용하세요.</p>' : ""}${a.effect === "HIDE" ? '<p class="pms-help"><strong>선택한 질문만 생략합니다.</strong> 중간 질문들은 순서대로 진행합니다. 생략한 답변을 참조하는 뒤 질문이 남아 있으면 검증 오류가 발생합니다.</p>' : ""}</article>`;
    }
    function draw() {
      body.innerHTML = `<div class="pm-editor-guidance"><strong>여러 질문의 조건과 여러 동작을 한 연결로 구성합니다.</strong><p>예: W ≤ 600 <b>그리고</b> 문 종류가 혼합일 때 → 수량 질문을 변경하고 혼합 묶음만 표시합니다. 입력 제한은 이 연결과 별도로 설정합니다. 모든 조건의 원본은 모든 대상 질문보다 앞에 있어야 합니다.</p><p>같은 대상에 질문 문구 변경과 답변 표시 제한은 함께 적용할 수 있습니다. 서로 다른 표시 제한이나 상반되는 표시·건너뜀은 충돌합니다.</p><p><strong>이 창은 임시 연결을 구성합니다.</strong> 대리석 A·B·C 등의 연결을 차례로 추가한 뒤, 마지막 전체 경로 검증을 통과해야 등록완료할 수 있습니다.</p></div><div class="pms-form-grid two">${S.field("연결 이름", "name", model.name, "text", 'maxlength="120"')}${S.select("조건 결합 방식", "match", model.match, { ALL: "모든 조건을 만족할 때 (AND)", ANY: "하나 이상 조건을 만족할 때 (OR)" })}</div><div class="pms-actions"><strong>시작 조건</strong><button type="button" data-add-condition>+ 조건 추가</button></div>${model.conditions.map(conditionHtml).join("")}<div class="pms-actions pms-section"><strong>적용할 동작</strong><button type="button" data-add-action>+ 동작 추가</button></div>${model.actions.map(actionHtml).join("") || '<p class="pms-help">영향받을 질문과 동작을 한 개 이상 추가하세요.</p>'}<div data-advanced-issues role="alert"></div>`;
      S.bind(body, model, (path) => {
        let m = /^conditions\.(\d+)\.groupKey$/.exec(path);
        if (m) { model.conditions[Number(m[1])] = makeCondition(find(model.conditions[Number(m[1])].groupKey)); draw(); return; }
        m = /^actions\.(\d+)\.targetKey$/.exec(path);
        if (m) { const a = model.actions[Number(m[1])]; a.choiceKeys = []; a.questionText = ""; draw(); return; }
        if (/^conditions\.\d+\.(operator|fieldKey)$/.test(path) || /^actions\.\d+\.effect$/.test(path)) draw();
      });
      S.$("[data-add-condition]", body).onclick = () => { if (model.conditions.length >= 20) return S.toast("조건은 한 연결에 최대 20개입니다."); model.conditions.push(makeCondition(initial)); draw(); };
      S.$("[data-add-action]", body).onclick = () => { if (model.actions.length >= 40) return S.toast("동작은 한 연결에 최대 40개입니다."); const q = targets()[0]; if (!q) return S.toast("모든 시작 조건보다 뒤에 있는 수정 가능한 질문이 없습니다. 조건 질문 또는 질문 순서를 확인해 주세요."); model.actions.push({ targetKey: q.key, effect: "HIDE", choiceKeys: [], questionText: "" }); draw(); };
      S.$$('[data-remove-condition]', body).forEach((b) => b.onclick = () => { model.conditions.splice(Number(b.dataset.removeCondition), 1); draw(); });
      S.$$('[data-remove-action]', body).forEach((b) => b.onclick = () => { model.actions.splice(Number(b.dataset.removeAction), 1); draw(); });
      S.$$('[data-advanced-choice]', body).forEach((input) => input.onchange = () => { const i = Number(input.dataset.advancedChoice); model.conditions[i].choiceKeys = S.$$(`[data-advanced-choice="${i}"]:checked`, body).map((x) => x.value); });
      S.$$('[data-advanced-action]', body).forEach((el) => {
        const i = Number(el.dataset.advancedAction), a = model.actions[i], q = find(a.targetKey);
        if (a.effect === "ALLOW") S.$$('input[type=checkbox]', el).forEach((input) => input.onchange = () => { a.choiceKeys = q?.control === "NUMBER" ? S.readNumberBundleChecks(el, q) : S.$$('[data-advanced-allowed]:checked', el).map((x) => x.value); });
      });
      if (readOnly) S.$$('input,textarea,select,button', body).forEach((el) => el.disabled = true);
    }
    draw();
    if (!readOnly) S.$('[data-save-advanced]', dialog).onclick = (e) => S.run(e.currentTarget, async () => {
      const updated = S.copy(context.getProcess ? context.getProcess() : context.process);
      if (!model.name.trim()) throw Error("연결 이름을 입력해 주세요.");
      if (!model.conditions.length || !model.actions.length) throw Error("조건과 동작을 각각 한 개 이상 추가해 주세요.");
      if (model.actions.some((action) => !targets().some((target) => target.key === action.targetKey)))
        throw Error("적용 대상은 모든 시작 조건보다 뒤에 있는 수정 가능한 질문이어야 합니다. 순서·고정 여부 확인이 표시된 대상을 다시 선택해 주세요.");
      model.actions.forEach((a) => { if (a.effect !== "ALLOW") a.choiceKeys = []; if (a.effect !== "RENAME") a.questionText = null; });
      const index = updated.rules.findIndex((r) => r.key === model.key);
      if (index < 0) updated.rules.push(S.copy(model)); else updated.rules[index] = S.copy(model);
      // Other alternatives (marble B/C, drawer/swing/mixed) can still be awaiting their own
      // connections. Check references now; exhaustive coverage gates final publication.
      const v = await (context.validateStructure || context.validate)(updated);
      const errors = (v.issues || []).filter((i) => i.severity === "ERROR");
      const issues = S.$('[data-advanced-issues]', body);
      issues.innerHTML = (v.issues || []).map((i) => `<article class="pm-validation-issue ${i.severity === "ERROR" ? "pms-error" : ""}"><strong>${S.e(i.location)}</strong><p>${S.e(i.message)}</p>${S.errorAdvice?.(i.message) ? `<p>${S.e(S.errorAdvice(i.message))}</p>` : ""}</article>`).join("");
      if (!v.valid || !v.exhaustive) throw Error(errors.map((i) => `${i.location}: ${i.message}`).join("\n") || "전체 경로 검증을 완료하지 못했습니다. 조건을 확인해 주세요.");
      context.apply(updated);
      dialog.close();
    });
    return dialog;
  };
})(window.PMS);
