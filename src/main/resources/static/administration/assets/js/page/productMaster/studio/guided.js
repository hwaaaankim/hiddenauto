(function (S) {
  "use strict";
  const nm = (q) => q?.labels?.customer || "삭제된 질문",
    err = (v) =>
      v.issues
        .filter((i) => i.severity === "ERROR")
        .map(
          (i) =>
            i.location +
            ": " +
            i.message +
            (S.errorAdvice(i.message) ? "\n" + S.errorAdvice(i.message) : ""),
        )
        .join("\n");
  const checks = (xs, sel, tag) =>
    xs
      .map(
        (x) =>
          `<label class="pms-check"><input type="checkbox" ${tag} value="${S.e(x.key)}" ${sel.includes(x.key) ? "checked" : ""}>${S.e(x.labels?.management || x.name)}</label>`,
      )
      .join("");
  const steps = (xs, i) =>
    `<ol class="pm-stepper">${xs.map((x, n) => `<li class="${i === n ? "active" : ""}">${n + 1}. ${x}</li>`).join("")}</ol>`;
  S.processPage = async function (root, p) {
    let priceLinked = false;
    try { priceLinked = (await S.request("/pricing/status", "POST", { productIds: [p.id] }))[0]?.registered || false; }
    catch (err) { S.notice("가격식 연결 상태를 확인하지 못했습니다. " + err.message); }
    const readOnly = priceLinked || Number(p.actualCount || 0) > 0 || new URLSearchParams(location.search).get("mode") === "view";
    const lockMessage = Number(p.actualCount || 0) > 0
      ? `실 제품 ${p.actualCount}종이 등록되어 구성·프로세스의 변경과 저장은 잠겨 있습니다.`
      : priceLinked ? "등록된 가격계산식과 연결되어 프로세스를 조회합니다. 가격계산식을 삭제한 뒤 프로세스를 변경하고 가격식을 다시 등록해 주세요."
      : "등록된 프로세스의 질문·답변·입력 제한·분기를 조회합니다.";
    let process = S.copy(p.process),
      stage = 0, zoom = 100, canvasObserver = null, canvasScroll = { x: 0, y: 0 };
    process.inputLimits ??= [];
    process.notices ??= [];
    const qs = () =>
        process.questions.filter((q) => !S.isBase(S.group(q.groupId))),
      fixed = () =>
        process.questions.filter((q) => S.isBase(S.group(q.groupId)));
    const files = new Map((p.processAssets || []).map((a) => [a.id, a]));
    const fs = (ids) => (ids || []).map((id) => files.get(id)).filter(Boolean);
    if (!p.nonStandard) { root.innerHTML = '<p class="pms-help">규격 제품은 공통 고객 테스트에서 옵션으로 찾습니다.</p><a class="pms-button" href="/admin/product-master/standard-test">규격 고객 테스트</a>'; return; }
    function dirty() {
      S.dirty = true;
    }
    const conditionSignature = (conditions) =>
      JSON.stringify(
        (conditions || [])
          .map((c) => ({
            groupKey: c.groupKey,
            fieldKey: c.fieldKey || null,
            operator: c.operator,
            choiceKeys: [...(c.choiceKeys || [])].sort(),
            lower: c.lower ?? null,
            upper: c.upper ?? null,
            lowerInclusive: !!c.lowerInclusive,
            upperInclusive: !!c.upperInclusive,
          }))
          .sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b))),
      );
    const conditionText = (q, cs) =>
      (cs || [])
        .map((c) => {
          if (!q) return "삭제된 질문을 참조하는 조건";
          if (S.isChoice(q.control))
            return (c.choiceKeys || [])
              .map(
                (key) =>
                  q.choices.find((x) => x.key === key)?.labels.management ||
                  key,
              )
              .join(" / ");
          const name =
            q.fields.find((f) => f.key === c.fieldKey)?.labels.management ||
            c.fieldKey;
          const op = {
            GE: "이상",
            GT: "초과",
            LE: "이하",
            LT: "미만",
            EQ: "일치",
            PRESENT: "입력함",
            ABSENT: "입력 안 함",
          }[c.operator];
          if (c.operator === "RANGE")
            return `${name}: ${c.lower} ${c.lowerInclusive ? "이상" : "초과"} ~ ${c.upper} ${c.upperInclusive ? "이하" : "미만"}`;
          return `${name}: ${c.lower ?? ""} ${op || c.operator}`;
        })
        .join(S.isChoice(q.control) ? " / " : " · ");
    async function validate(test = process) {
      const v = await S.request(
        "/products/" + p.id + "/validate",
        "POST",
        test,
      );
      const el = S.$("#pm-validation", root);
      if (el && !S.$$("dialog[open]").length) {
        el.innerHTML = `<p class="pms-help" role="status">${v.valid && v.exhaustive ? "검증 통과" : "수정 필요"} · ${v.scenarios}개 경로</p>${v.issues
          .map((i, index) => {
            const question = test.questions.find(
              (q) => (nm(q) === i.location || q.key === i.location) && !S.isBase(S.group(q.groupId)),
            );
            const relatedLimits = (test.inputLimits || []).filter((l) => l.name === i.location || i.message.includes("[" + l.name + "]"));
            const relatedNotices = i.code === "INPUT_NOTICE" ? test.questions.filter((q) => q.key === i.location) : [];
            const rules = test.rules.filter(
              (r) =>
                r.name === i.location ||
                i.message.includes("[" + r.name) ||
                i.message.includes(r.name + "]") ||
                i.message.includes(" / " + r.name),
            );
            return `<article class="pm-validation-issue ${i.severity === "ERROR" ? "pms-error" : ""}"><strong>${S.e(i.location)} · ${i.severity === "ERROR" ? "수정 필요" : "확인"}</strong><p>${S.e(i.message)}</p>${S.errorAdvice(i.message) ? `<p class="pm-fix-example">${S.e(S.errorAdvice(i.message))}</p>` : ""}<div class="pms-actions">${question ? `<button data-fix-question="${S.e(question.key)}">${S.e(nm(question))} 답변 수정</button>` : ""}${rules.map((r) => `<button data-fix-rule="${S.e(r.key)}">${S.e(r.name)} 연결 ${readOnly ? "조회" : "수정"}</button>`).join("")}${relatedLimits.map((l) => `<button data-fix-limit="${S.e(l.targetKey)}">${S.e(l.name)} 입력제한 ${readOnly ? "조회" : "수정"}</button>`).join("")}${relatedNotices.map((q) => `<button data-fix-notice="${S.e(q.key)}">조건별 안내 ${readOnly ? "조회" : "수정"}</button>`).join("")}</div></article>`;
          })
          .join("")}`;
        S.$$("[data-fix-question]", el).forEach(
          (b) =>
            (b.onclick = () => {
              stage = 0;
              paint();
              question(
                process.questions.find((q) => q.key === b.dataset.fixQuestion),
              );
            }),
        );
        S.$$("[data-fix-limit],[data-fix-notice]", el).forEach((button) => button.onclick = () => { const key = button.dataset.fixLimit || button.dataset.fixNotice, q = process.questions.find((q) => q.key === key); if (!q) return; stage = 0; paint(); question(q, button.dataset.fixLimit ? "입력 제한 설정" : "조건별 안내메시지"); });
        S.$$("[data-fix-rule]", el).forEach(
          (b) =>
            (b.onclick = () => {
              const rule = process.rules.find(
                (r) => r.key === b.dataset.fixRule,
              );
              if (!rule) return;
              stage = 1;
              paint();
              relation(rule.conditions[0].groupKey, rule);
            }),
        );
      }
      return v;
    }
    async function validateStructure(test = process, allowIncomplete = false) {
      const result = await S.request("/products/" + p.id + "/validate-structure", "POST", test);
      if (!allowIncomplete) return result;
      const remaining = result.issues.filter((issue) => !(issue.code === "QUESTION" && test.questions.some((q) => nm(q) === issue.location && !ready(q))));
      return { ...result, issues: remaining, valid: remaining.every((issue) => issue.severity !== "ERROR") };
    }
    async function save(publish) {
      if (readOnly) throw Error(lockMessage);
      if (publish) {
        const v = await validate();
        if (!v.valid || !v.exhaustive) throw Error(err(v));
      }
      p = await S.request("/products/" + p.id + "/process", "PUT", {
        version: p.version,
        process,
        publish,
      });
      process = S.copy(p.process);
      S.dirty = false;
      S.toast(publish ? "등록을 완료했습니다." : "임시 저장했습니다.");
      paint();
    }
    function reorder(from, to) {
      const rows = qs();
      rows.splice(to, 0, rows.splice(from, 1)[0]);
      const ordered = [...fixed(), ...rows];
      const index = new Map(ordered.map((q, i) => [q.key, i]));
      const backward = process.rules.filter((r) =>
        r.conditions.some((c) =>
          r.actions.some(
            (a) => index.get(c.groupKey) >= index.get(a.targetKey),
          ),
        ),
      );
      const backwardLimits = process.inputLimits.filter((r) => r.sourceKey && index.get(r.sourceKey) >= index.get(r.targetKey));
      if (backward.length || backwardLimits.length) {
        paint();
        S.showErrors(S.$("#pm-validation", root), {
          "":
            "순서를 변경할 수 없습니다. 다음 연결은 시작 질문이 대상 질문보다 앞에 있어야 합니다: " +
            [...backward, ...backwardLimits].map((r) => r.name).join(" / "),
        });
        return;
      }
      process.questions = ordered;
      dirty();
      paint();
    }
    function ready(q) {
      const rows = S.isChoice(q.control) ? q.choices : q.fields;
      return rows?.length && rows.every((x) => x.key && x.labels?.customer?.trim() && x.labels?.production?.trim() && x.labels?.management?.trim())
        && (!q.fixed || (q.preset && (q.preset.choices?.length || Object.keys(q.preset.fields || {}).length)));
    }
    function canvas() {
      const view = S.$(".pm-flow-canvas", root), plane = S.$(".pm-flow-scale", root), size = S.$(".pm-flow-size", root);
      function layout() {
        plane.style.width = Math.max(300, view.clientWidth) + "px";
        plane.style.transform = `scale(${zoom / 100})`;
        size.style.width = Math.max(view.clientWidth, Math.ceil(plane.offsetWidth * zoom / 100)) + "px";
        size.style.height = Math.max(view.clientHeight, Math.ceil(plane.offsetHeight * zoom / 100)) + "px";
      }
      function setZoom(value) {
        const previous = zoom / 100, cx = (view.scrollLeft + view.clientWidth / 2) / previous, cy = (view.scrollTop + view.clientHeight / 2) / previous;
        zoom = Math.max(10, Math.min(200, Math.round(value / 10) * 10));
        layout(); view.scrollLeft = cx * zoom / 100 - view.clientWidth / 2; view.scrollTop = cy * zoom / 100 - view.clientHeight / 2;
        S.$("[data-zoom-range]", root).value = zoom; S.$("[data-zoom-output]", root).textContent = zoom + "%";
        S.$("[data-zoom-minus]", root).disabled = zoom === 10; S.$("[data-zoom-plus]", root).disabled = zoom === 200;
      }
      S.$("[data-zoom-range]", root).oninput = (e) => setZoom(Number(e.target.value));
      S.$("[data-zoom-minus]", root).onclick = () => setZoom(zoom - 10);
      S.$("[data-zoom-plus]", root).onclick = () => setZoom(zoom + 10);
      S.$("[data-zoom-reset]", root).onclick = () => setZoom(100);
      S.$("[data-zoom-fit]", root).onclick = () => {
        setZoom(Math.floor(Math.min(view.clientWidth / plane.offsetWidth, view.clientHeight / plane.offsetHeight) * 100 / 10) * 10);
        view.scrollLeft = 0; view.scrollTop = 0;
      };
      view.addEventListener("wheel", (e) => { if (e.ctrlKey) { e.preventDefault(); setZoom(zoom + (e.deltaY < 0 ? 10 : -10)); } }, { passive: false });
      canvasObserver = new ResizeObserver(layout); canvasObserver.observe(view); canvasObserver.observe(plane);
      layout(); setZoom(zoom); view.scrollLeft = canvasScroll.x; view.scrollTop = canvasScroll.y;
    }
    function answerDetails(q, answer) {
      if (!answer) return "";
      const lines = [S.isChoice(q.control) ? "선택 답변: " + answer.labels.management : "입력 필드: " + answer.labels.management];
      if (q.control === "NUMBER") {
        const b = S.numberBundles(q).find((b) => b.fields.some((f) => f.key === answer.key));
        lines.push("표시·가격 묶음: " + b.fields.map((f) => f.labels.management).join(" + "));
        lines.push("기본 허용값: " + (answer.min ?? "제한 없음") + " ~ " + (answer.max ?? "제한 없음") + " " + (answer.unit || ""));
        for (const l of process.inputLimits.filter((l) => l.targetKey === q.key && l.fieldKeys.includes(answer.key))) lines.push("입력제한 [" + l.name + "]: " + (l.aggregation === "SUM" ? "합계" : "각 값") + (l.sourceKey ? " · 앞 질문 " + nm(process.questions.find((x) => x.key === l.sourceKey)) + "의 " + l.cases.length + "개 조건" : " " + (l.min ?? "없음") + "~" + (l.max ?? "없음")));
      }
      const linked = process.rules.filter((r) => r.conditions.some((c) => c.groupKey === q.key && (S.isChoice(q.control) ? c.operator === "NE" ? !(c.choiceKeys || []).includes(answer.key) : c.operator === "PRESENT" || (c.choiceKeys || []).includes(answer.key) : c.fieldKey === answer.key)) || r.actions.some((a) => a.targetKey === q.key && a.effect === "ALLOW" && (a.choiceKeys || []).includes(answer.key)));
      for (const r of linked) lines.push("분기 [" + r.name + "]: " + r.conditions.map((c) => nm(process.questions.find((x) => x.key === c.groupKey)) + " · " + conditionText(process.questions.find((x) => x.key === c.groupKey), [c])).join(" / ") + " → " + r.actions.map((a) => nm(process.questions.find((x) => x.key === a.targetKey)) + " " + ({HIDE:"이 질문만 생략",SHOW:"표시",ALLOW:"선택 답변·입력 묶음 표시",RENAME:"질문 변경: " + (a.questionText || "")}[a.effect] || a.effect)).join(" / "));
      if (!linked.length) lines.push("이 항목에 등록된 표시 분기가 없습니다.");
      return lines.join("\n");
    }
    function paint() {
      const oldCanvas = S.$(".pm-flow-canvas", root);
      if (oldCanvas) canvasScroll = { x: oldCanvas.scrollLeft, y: oldCanvas.scrollTop };
      canvasObserver?.disconnect();
      const saveStatus = S.dirty ? "저장하지 않은 변경사항" : "저장된 상태";
      root.innerHTML = `${readOnly ? `<div class="pms-alert pm-readonly-notice"><strong>등록내용 조회 모드</strong><p>${S.e(lockMessage)} 질문·답변을 열거나 답변에 마우스를 올리면 묶음, 입력제한, 분기 연결을 확인할 수 있습니다.</p>${priceLinked ? `<a class="pms-button" href="/admin/product-master/products/${p.id}/pricing">연결된 가격계산식 관리</a>` : ""}</div>` : ""}<section class="pms-panel"><header class="pms-panel-title"><div><h2>${S.e(p.productName)}</h2><small>고정 분류: ${fixed()
        .map((q) => q.choices.map((c) => S.e(c.labels.management)).join("/"))
        .join(
          " · ",
        )}</small></div><a class="pms-button" href="/admin/product-master/products/${p.id}">제품 구성</a></header>${steps(["질문·답변 완성", "연관관계 설정", "전체 검증·등록"], stage)}<div class="pms-panel-body"><p class="pms-help">${["각 질문을 열어 가능한 답변을 차례로 등록하세요. 숫자는 필드 구성 → 입력 제한 → 범위 조건 순서입니다.", "앞 답변의 조건으로 뒤 질문·선택지·숫자 묶음의 표시를 정합니다. 연결은 하나씩 작성하고, 모두 작성한 뒤 전체 경로의 빠짐·겹침·모순을 검증합니다.", "전체 입력 경로를 검사한 뒤 등록을 완료합니다. 저장된 제품으로 고객 흐름을 테스트할 수 있습니다."][stage]}</p><div class="pm-process-progress"><strong>답변 설정 ${qs().filter(ready).length} / ${qs().length}개</strong><span>답변 준비 → 입력 제한·조건 → 연결 → 검증 순서로 완성합니다.</span>${stage === 0 && qs().some((q) => !ready(q)) ? '<button type="button" data-next-question class="primary">다음 미완성 질문 설정</button>' : ""}</div><div class="pm-order-strip">${qs()
        .map(
          (q, i) =>
            `<div data-order="${S.e(q.key)}"><span class="pms-handle">⠿</span>${i + 1}. ${S.e(nm(q))}<button data-move="${i}:-1" ${i === 0 ? "disabled" : ""}>↑</button><button data-move="${i}:1" ${i === qs().length - 1 ? "disabled" : ""}>↓</button></div>`,
        )
        .join("")}</div><div class="pm-canvas-toolbar" aria-label="프로세스 캔버스 배율"><strong>질문 흐름</strong><button type="button" data-zoom-minus aria-label="캔버스 축소">−</button><label>배율 <input type="range" data-zoom-range min="10" max="200" step="10" value="${zoom}" aria-label="캔버스 배율 10에서 200퍼센트"></label><output data-zoom-output>${zoom}%</output><button type="button" data-zoom-plus aria-label="캔버스 확대">+</button><button type="button" data-zoom-reset>100%</button><button type="button" data-zoom-fit>전체 흐름 맞춤</button><small>10~200% · Ctrl + 휠로도 조절</small></div><div class="pm-flow-canvas is-zoomable" tabindex="0" role="region" aria-label="스크롤 가능한 질문 흐름 캔버스"><div class="pm-flow-size"><div class="pm-flow-scale">${
        qs()
          .map(
            (q, i) =>
              `<article class="pm-flow-node"><span class="pm-flow-state">${S.badge(ready(q) ? "답변 등록됨" : "설정 필요", ready(q) ? "green" : "amber")}</span><span class="pms-badge">${i + 1} · ${S.e(S.controls[q.control])}</span><h3>${S.e(q.question || nm(q))}</h3><p>${S.group(q.groupId)?.askQuestion === false ? "고정 사양 · 질문 제외" : S.isChoice(q.control) ? q.choices.length + "개 답변" : q.fields.length + "개 입력 항목"}${q.control === "NUMBER" ? " · " + (q.numberCases || []).length + "개 범위" : ""}</p><div class="pm-flow-answer-list">${(S.isChoice(q.control) ? q.choices.map((c) => c.labels.management) : q.fields.map((f) => f.labels.management + (q.control === "NUMBER" ? " · " + (f.min ?? "제한 없음") + "~" + (f.max ?? "제한 없음") + " " + (f.unit || "") : ""))).map((text, answerIndex) => `<span tabindex="0" class="pm-branch-tooltip" title="${S.e(answerDetails(q, (S.isChoice(q.control) ? q.choices : q.fields)[answerIndex]))}">${S.e(text)}</span>`).join("")}</div>${stage === 0 ? `<button data-edit="${S.e(q.key)}" class="primary">${readOnly ? "등록 답변·제한 보기" : "답변 등록·수정"}</button>` : ""}${stage === 1 ? `<button data-relate="${S.e(q.key)}" ${i === qs().length - 1 ? "disabled" : ""}>${readOnly ? "연결 내용 안내" : "이 답변에서 연관관계 설정"}</button>${!readOnly && i < qs().length - 1 ? `<button data-advanced-relate="${S.e(q.key)}">복합 조건·여러 동작 연결</button>` : ""}` : ""}${process.rules
                .filter((r) => r.conditions.some((c) => c.groupKey === q.key))
                .map(
                  (r) =>
                    `<div class="pm-rule-line">${S.e(r.name)} → ${r.actions.map((a) => S.e(nm(process.questions.find((x) => x.key === a.targetKey))) + " · " + S.e({ HIDE: "건너뜀", SHOW: "질문 표시", ALLOW: "옵션 제한", RENAME: "질문 변경" }[a.effect] || a.effect)).join(", ")}${stage === 1 ? `<button data-rule-edit="${S.e(r.key)}">${readOnly ? "연결 보기" : "수정"}</button><button data-rule-remove="${S.e(r.key)}" ${readOnly ? "disabled" : ""}>삭제</button>` : ""}</div>`,
                )
                .join(
                  "",
                )}</article>${i < qs().length - 1 ? '<div class="pm-flow-arrow">↓</div>' : ""}`,
          )
          .join("") ||
        '<p class="pms-empty">추가 질문 없이 필수 분류만으로 등록할 수 있습니다.</p>'
      }</div></div></div><div id="pm-validation"></div><div class="pms-actions pm-stage-actions"><span class="pm-save-status" role="status">${saveStatus}</span>${stage ? '<button id="pm-prev">이전 단계</button>' : ""}<button id="pm-draft">임시 저장</button>${stage < 2 ? '<button id="pm-next" class="primary">' + (stage === 0 ? "답변 확인 · 연관관계 설정" : "전체 검증 단계로") + "</button>" : `<button id="pm-validate">전체 경로 검증</button><button id="pm-publish" class="primary">검증 후 등록완료</button><a class="pms-button" target="_blank" href="/admin/product-master/products/${p.id}/test">저장된 제품 고객 테스트</a>`}</div></div></section>`;
      canvas();
      S.$("[data-next-question]", root)?.addEventListener("click", () => question(qs().find((q) => !ready(q))));
      S.$$("[data-edit]", root).forEach(
        (b) =>
          (b.onclick = () =>
            question(process.questions.find((q) => q.key === b.dataset.edit))),
      );
      S.$$("[data-relate]", root).forEach(
        (b) => (b.onclick = () => relation(b.dataset.relate)),
      );
      S.$$("[data-advanced-relate]", root).forEach((b) => b.onclick = () => advanced(b.dataset.advancedRelate));
      S.$$("[data-rule-edit]", root).forEach(
        (b) =>
          (b.onclick = () => {
            const r = process.rules.find((r) => r.key === b.dataset.ruleEdit);
            relation(r.conditions[0].groupKey, r);
          }),
      );
      S.$$("[data-rule-remove]", root).forEach(
        (b) =>
          (b.onclick = () => {
            process.rules = process.rules.filter(
              (r) => r.key !== b.dataset.ruleRemove,
            );
            dirty();
            paint();
          }),
      );
      if (!readOnly) S.sortable(S.$(".pm-order-strip", root), "[data-order]", reorder);
      S.$$("[data-move]", root).forEach(
        (b) =>
          (b.onclick = () => {
            const [i, d] = b.dataset.move.split(":").map(Number);
            reorder(i, i + d);
          }),
      );
      S.$("#pm-prev", root)?.addEventListener("click", () => {
        stage--;
        paint();
      });
      S.$("#pm-next", root)?.addEventListener("click", (e) =>
        S.run(e.currentTarget, async () => {
          if (readOnly) { stage++; paint(); return; }
          const v = stage === 0 ? await validateStructure(process) : await validate(process);
          if (!v.valid || !v.exhaustive) throw Error(err(v));
          stage++;
          paint();
        }),
      );
      if (readOnly) S.$$("#pm-draft,#pm-publish,[data-move],[data-next-question]", root).forEach((el) => el.disabled = true);
      S.$("#pm-draft", root).onclick = (e) =>
        S.run(e.currentTarget, () => save(false));
      S.$("#pm-validate", root)?.addEventListener("click", (e) =>
        S.run(e.currentTarget, async () => {
          const v = await validate();
          if (!v.valid)
            S.$("#pm-validation", root).scrollIntoView({ block: "center" });
        }),
      );
      S.$("#pm-publish", root)?.addEventListener("click", (e) =>
        S.run(e.currentTarget, () => save(true)),
      );
    }
    function question(original, startTitle) {
      let q = S.copy(original),
        step = 0,
        openRow = 0;
      const localLimits = S.copy(process.inputLimits), localNotices = S.copy(process.notices);
      const questionProcess = () => ({ ...process, questions: process.questions.map((x) => x.key === q.key ? q : x), inputLimits: localLimits, notices: localNotices });
      q.numberCases ??= [];
      if (!S.isChoice(q.control) && !readOnly) for (const f of q.fields) {
        if (q.control === "NUMBER" && f.guide?.trim()) { localNotices.push({ key: S.key("M"), targetKey: q.key, fieldKey: f.key, message: f.guide, mode: "CONDITIONAL", conditions: [{groupKey:q.key,fieldKey:f.key,operator:"PRESENT",choiceKeys:[]}] }); f.guide = ""; }
      }
      q.preset ??= { choices: [], fields: {} };
      let hidden = S.group(q.groupId)?.askQuestion === false;
      const questionSteps = () => [
        "가능한 답변 등록",
        ...(!S.isChoice(q.control) ? ["입력 제한 설정"] : []),
        ...(q.control === "NUMBER" ? ["범위 조건 설정"] : []),
        ...(!S.isChoice(q.control) ? ["조건별 안내메시지"] : []),
        ...(hidden ? ["고정 사양 입력"] : []),
        "확인",
      ];
      let titles = questionSteps();
      if (startTitle) step = Math.max(0, titles.indexOf(startTitle));
      const dirtyBefore = S.dirty;
      let applied = false;
      const d = S.dialog(nm(q) + " · 답변 설정", "", { onClose: () => { if (!applied) S.dirty = dirtyBefore; } }),
        body = S.$(".pms-dialog-body", d);
      function draw() {
        const title = titles[step];
        body.innerHTML =
          steps(titles, step) +
          '<div data-content></div><div class="pms-actions pm-stage-actions">' +
          (step ? "<button data-back>이전</button>" : "") +
          '<button data-next class="primary">' +
          (step === titles.length - 1 ? readOnly ? "조회 닫기" : "이 질문 완성" : "다음") +
          "</button></div>";
        const guidance = document.createElement("div");
        guidance.className = "pm-editor-guidance";
        guidance.innerHTML = `<strong>${step + 1}단계 · ${S.e(title)}</strong><p>${{
          "가능한 답변 등록": "질문과 튜토리얼은 고객용으로 작성합니다. 선택 옵션·입력 항목의 이름만 고객용·생산팀용·관리팀용으로 각각 정하세요. 안내메시지와 이미지·파일의 추가·교체·삭제는 질문 완성 후 프로세스를 저장할 때 반영됩니다.",
          "입력 제한 설정": "각 필드의 기본 제한과 묶음 합계 제한, 앞 질문의 조건별 제한을 각각 정합니다. 입력제한은 값의 허용범위를 정하며 질문·필드 표시를 바꾸는 연관관계와 독립적입니다.",
          "조건별 안내메시지": "숫자의 특정 범위, 텍스트·파일 입력 유무에 따라 고객에게 안내합니다. 기본 사용법은 튜토리얼·FAQ로, 상황별 안내는 여기서 설정하세요.",
          "범위 조건 설정": "W만 또는 W·H·D를 조합해 숫자 조건을 만듭니다. 조건 사이의 겹침은 서버에서 검사합니다. 범위가 필요 없으면 기본 흐름으로 진행할 수 있습니다.",
          "고정 사양 입력": "고객에게 묻지 않는 항목입니다. 제품에 저장할 답변을 입력하세요.",
          "확인": "등록된 질문·답변·안내·범위를 확인합니다. 질문을 완성한 뒤 다음 질문을 이어서 설정할 수 있습니다.",
        }[title]}</p>`;
        S.$("[data-content]", body).before(guidance);
        const box = S.$("[data-content]", body);
        if (title === "가능한 답변 등록") {
          const choice = S.isChoice(q.control),
            rows = choice ? q.choices : q.fields,
            prefix = choice ? "choices." : "fields.";
          box.innerHTML = `<div class="pm-question-labels pms-form-grid one">${S.customerLabel(q)}${S.field("고객에게 물어볼 질문", "question", q.question || nm(q), "text", 'maxlength="300"')}${S.textarea("고객 튜토리얼·도움말", "guide", q.guide || "", 1000)}</div><div class="pms-check-grid">${S.check("반드시 답변", "required", q.required)}${!hidden ? S.check("기본 흐름에서 질문 표시", "visible", q.visible) : ""}${q.control === "NUMBER" ? S.check("앞 분기에서 표시할 입력 묶음을 반드시 지정", "requireRule", q.requireRule) : ""}</div>${q.control === "NUMBER" ? '<p class="pms-help">서랍·여닫이·혼합처럼 서로 다른 입력 묶음 중 하나를 앞 답변으로 정한다면 ‘앞 분기에서 표시할 입력 묶음을 반드시 지정’을 체크하세요. 앞 질문을 건너뛰어 모든 수량 필드가 함께 노출되는 경로는 검증에서 막습니다.</p><div data-number-grouping></div>' : ""}${!hidden && !q.visible ? '<p class="pms-help">기본 질문에서는 생략됩니다. 앞 답변에서 이 질문으로 ‘질문 표시’ 연결을 설정하면 조건이 맞을 때 나타납니다.</p>' : ""}<div class="pms-actions pms-section"><strong>${choice ? "선택 가능한 답변" : "입력받을 항목"}</strong><button data-add>+ ${choice ? "답변" : "입력 필드"}</button></div>${rows.map((x, i) => `<details class="pm-answer-editor" ${i === openRow ? "open" : ""}><summary>${i + 1}. <strong data-row-title="${i}">${S.e(x.labels?.management || (choice ? "새 답변" : "새 입력 필드"))}</strong>${q.control === "NUMBER" ? `<small>${x.bundleKey ? "묶음 입력" : "독립 입력"}</small>` : ""}</summary><div class="pm-answer-body">${S.labels(x, prefix + i, false)}${!choice ? `<div data-field-preview="${i}">${S.fieldLabelSample(x)}</div>` : '<p class="pms-help">이 이름은 고객의 선택지와 생산팀·관리팀의 선택 결과 앞에 표시됩니다.</p>'}${choice ? `<div class="pms-form-grid one">${S.textarea("이 답변 선택 시 고객 안내메시지", prefix + i + ".guide", x.guide || "", 2000)}</div>` : '<p class="pms-help">숫자 범위·입력 유무별 안내는 ‘조건별 안내메시지’ 단계에서 설정합니다.</p>'}${S.field("내부 value · 저장·분기 연결용 고유값", prefix + i + ".key", x.key, "text", 'maxlength="80" pattern="[A-Za-z0-9_-]+"')}<p class="pms-help">내부 value는 고객에게 표시되지 않습니다. 이미 연결한 항목의 value를 바꾸면 연결·입력제한도 다시 확인해야 합니다.</p><div class="pms-actions"><button data-remove="${i}">이 ${choice ? "답변" : "입력 필드"} 삭제</button></div><section class="pms-section"><h3>${choice ? "답변" : "입력 필드"} 이미지·파일</h3><p class="pms-help">이 ${choice ? "답변" : "입력 필드"} 옆에 표시될 참고자료입니다. 필드별 최대 20개, 프로세스 전체 최대 200개까지 등록할 수 있습니다.</p><div data-row-files="${i}">${S.files(fs(x.assetIds), !readOnly)}</div></section></div></details>`).join("")}<section class="pms-section"><h3>이 제품의 질문 이미지·파일</h3><div data-q-files>${S.files(fs(q.assetIds), !readOnly)}</div></section><div class="pms-actions pms-section"><button type="button" data-common-settings>${S.group(q.groupId)?.definitionLocked ? "공통 그룹 정보 (조회)" : "공통 그룹 이름·안내·파일 수정"}</button></div>${S.media(S.group(q.groupId)?.assets || [], "공통 그룹 이미지")}`;
          if (q.control === "NUMBER") S.renderNumberGrouping(S.$("[data-number-grouping]", box), q, draw, readOnly);
          S.bind(box, q, (path) => {
            const fieldPath = /^fields\.(\d+)\.labels\./.exec(path);
            if (fieldPath) S.$(`[data-field-preview="${fieldPath[1]}"]`, box).innerHTML = S.fieldLabelSample(q.fields[Number(fieldPath[1])]);
            const m = /^(?:choices|fields)\.(\d+)\.labels\.management$/.exec(
              path,
            );
            if (m)
              S.$('[data-row-title="' + m[1] + '"]', box).textContent =
                rows[Number(m[1])].labels.management || "이름 미입력";
          });
          S.$$(".pm-answer-editor", box).forEach((el, i) =>
            el.addEventListener("toggle", () => {
              if (el.open) openRow = i;
            }),
          );
          S.$("[data-add]", box).onclick = () => {
            if (rows.length >= (choice ? 200 : 20))
              return S.toast("최대 개수를 초과했습니다.");
            rows.push(
              choice
                ? {
                    key: S.key("C"),
                    labels: { customer: "", production: "", management: "" },
                    namePart: "",
                    guide: "",
                    assetIds: [],
                  }
                : S.newField(),
            );
            openRow = rows.length - 1;
            draw();
            S.$$(".pm-answer-editor", body)
              .at(-1)
              ?.scrollIntoView({ block: "nearest" });
          };
          S.$$("[data-remove]", box).forEach(
            (b) =>
              (b.onclick = () => {
                rows.splice(Number(b.dataset.remove), 1);
                draw();
              }),
          );
          S.$$("[data-row-files]", box).forEach((el) =>
            bindFiles(el, rows[Number(el.dataset.rowFiles)]),
          );
          bindFiles(S.$("[data-q-files]", box), q);
          S.bindMedia(body);
          S.$("[data-common-settings]", box).onclick = (e) => S.run(e.currentTarget, () => S.groupSettingsDialog(q.groupId, async () => {
            const saved = await S.request("/products/" + p.id);
            for (const key of ["version", "status", "groups", "processAssets"]) p[key] = saved[key];
            (saved.processAssets || []).forEach((a) => files.set(a.id, a));
            hidden = S.group(q.groupId)?.askQuestion === false;
            titles = questionSteps();
            step = 0;
            q.preset ??= { choices: [], fields: {} };
            draw();
          }));
        } else if (title === "입력 제한 설정") {
          if (q.control === "NUMBER") S.renderIndependentLimits(box, q, { limits: localLimits, redraw: draw, getProcess: questionProcess, readOnly });
          else {
            box.innerHTML = '<p class="pms-help">텍스트·파일의 기본 허용범위입니다. 필수 여부와 글자 수·파일 개수·형식을 정하세요.</p>' + S.fieldRows(q.fields, q.control);
            S.$$("[data-path$='.namePart'],[data-path$='.guide'],[data-remove-field]", box).forEach((el) => el.closest("label")?.remove() || el.remove());
            S.bind(box, q); S.fieldEvents(box, q.fields, draw);
          }
        } else if (title === "조건별 안내메시지") {
          S.renderConditionalNotices(box, q, { notices: localNotices, redraw: draw, readOnly });
        } else if (title === "범위 조건 설정") {
          box.innerHTML = `<p class="pms-help">범위 안의 조건은 모두 만족(AND)해야 합니다. 연결에 사용 중인 범위를 수정하면 연결 조건도 함께 갱신·검증됩니다. 사용 중인 범위 삭제는 연결을 먼저 수정·삭제해야 합니다. 다른 범위와 값이 겹치면 등록할 수 없습니다. 어떤 범위에도 해당하지 않는 값에는 기본 흐름이 적용됩니다.</p><button data-case-add>+ 범위 조건</button>${q.numberCases.map((r, i) => `<article class="pms-card"><h3>${S.e(r.name)}</h3><p>${S.e(conditionText(q, r.conditions))}</p>${r.guide ? "<p>" + S.e(r.guide) + "</p>" : ""}<button data-case-edit="${i}">수정</button><button data-case-delete="${i}">삭제</button></article>`).join("")}`;
          S.$("[data-case-add]", box).onclick = () => editCase(null);
          S.$$("[data-case-edit]", box).forEach(
            (b) => (b.onclick = () => editCase(Number(b.dataset.caseEdit))),
          );
          S.$$("[data-case-delete]", box).forEach(
            (b) =>
              (b.onclick = () => {
                q.numberCases.splice(Number(b.dataset.caseDelete), 1);
                draw();
              }),
          );
        } else if (title === "고정 사양 입력") {
          box.innerHTML =
            '<p class="pms-help">고객에게 질문하지 않고 제품 스펙으로 저장할 값을 선택해 주세요.</p>' +
            S.inputAnswer(q, q.preset, readOnly, files);
          if (!readOnly) S.answerFileEvents(box, q, q.preset, files);
          S.bindMedia(box);
        } else
          box.innerHTML = S.registeredQuestion(q, fs, S.group(q.groupId), questionProcess()) + `<p>${q.numberCases.length}개 범위 조건 · ${hidden ? "고정 사양 저장" : "고객 답변"}</p>${qs().indexOf(original) < qs().length - 1 ? '<label class="pms-check"><input type="checkbox" data-continue-question checked>완성 후 다음 질문을 이어서 설정</label>' : ""}`;
        if (readOnly) S.$$("input,select,textarea,button:not([data-notice-edit]):not([data-limit-edit]):not([data-map-edit]),.pms-handle", box).forEach((el) => { if (!el.matches("[data-pm-image]")) el.disabled = true; });
        S.$("[data-back]", body)?.addEventListener("click", () => {
          if (title === "고정 사양 입력")
            q.preset = S.readAnswer(box, q, q.preset);
          step--;
          draw();
        });
        S.$("[data-next]", body).onclick = (e) =>
          S.run(e.currentTarget, async () => {
            if (readOnly) { if (step === titles.length - 1) d.close(); else { step++; draw(); } return; }
            if (title === "가능한 답변 등록") {
              const rows = S.isChoice(q.control) ? q.choices : q.fields;
              if (!rows.length)
                throw Error("답변 또는 입력 필드를 먼저 추가해 주세요.");
              const errors = {};
              S.checkCustomerLabel(q.labels, "", 80, errors);
              rows.forEach((x, i) =>
                S.checkLabels(
                  x.labels,
                  (S.isChoice(q.control) ? "choices." : "fields.") + i + ".",
                  120,
                  errors,
                ),
              );
              if (q.control === "NUMBER") {
                const scopes = new Map();
                rows.forEach((row, i) => {
                  const scope = row.bundleKey || "__independent__";
                  for (const role of ["customer", "production", "management"]) {
                    const key = scope + ":" + role + ":" + (row.labels?.[role] || "").trim();
                    if (scopes.has(key)) errors["fields." + i + ".labels." + role] = "같은 묶음 안에서는 입력 이름을 구분해 주세요. 예: W / D. 종류별 W·D를 각각 묶은 경우에는 다른 묶음의 같은 이름을 사용할 수 있습니다.";
                    else scopes.set(key, i);
                  }
                });
              } else S.checkUniqueLabels(rows, S.isChoice(q.control) ? "choices." : "fields.", errors);
              const keys = new Map();
              rows.forEach((row, i) => {
                const prefix = S.isChoice(q.control) ? "choices." : "fields.";
                if (!/^[A-Za-z0-9_-]{1,80}$/.test(row.key || ""))
                  errors[prefix + i + ".key"] =
                    "내부 value는 영문·숫자·밑줄·하이픈으로 1~80자 입력해 주세요. 예: width 또는 color_white.";
                else if (keys.has(row.key)) {
                  errors[prefix + i + ".key"] =
                    "같은 내부 value가 이미 있습니다. 서로 다른 값을 사용해 주세요. 예: width / height.";
                  errors[prefix + keys.get(row.key) + ".key"] =
                    "이 내부 value가 다른 항목과 중복됩니다. 각 항목은 독립된 value가 필요합니다.";
                } else keys.set(row.key, i);
              });
              if (Object.keys(errors).length) {
                const failure = Error("입력 항목을 확인해 주세요.");
                failure.fieldErrors = errors;
                throw failure;
              }
            }
            if (title === "입력 제한 설정") {
              const errors = {};
              S.checkFields(q.fields, q.control, errors);
              if (Object.keys(errors).length) {
                const failure = Error("입력 항목을 확인해 주세요.");
                failure.fieldErrors = errors;
                throw failure;
              }
            }
            if (title === "고정 사양 입력")
              q.preset = S.readAnswer(box, q, q.preset);
            if (step === titles.length - 1) {
              q.fixed = hidden;
              q.visible = !hidden && q.visible;
              if (!hidden) q.preset = null;
              // Rule coverage belongs to the full process; keep the saved flag on the question.
              const v = await S.request("/validate-question", "POST", { ...q, requireRule: false });
              if (!v.valid || !v.exhaustive) throw Error(err(v));
              const updated = S.copy(process);
              updated.inputLimits = S.copy(localLimits); updated.notices = S.copy(localNotices);
              updated.questions[
                updated.questions.findIndex((x) => x.key === q.key)
              ] = q;
              // Relations store conditions, so synchronize rules linked to an edited numeric bucket.
              for (const rule of updated.rules) {
                const previousCase = (original.numberCases || []).find(
                  (c) =>
                    rule.match === "ALL" &&
                    conditionSignature(c.conditions) ===
                      conditionSignature(rule.conditions),
                );
                if (!previousCase) continue;
                const currentCase = q.numberCases.find(
                  (c) => c.key === previousCase.key,
                );
                if (!currentCase)
                  throw Error(
                    `범위 [${previousCase.name}]은 연관관계 [${rule.name}]에서 사용 중입니다. 연관관계를 먼저 수정·삭제한 뒤 범위를 삭제해 주세요.`,
                  );
                rule.conditions = S.copy(currentCase.conditions);
              }
              if (updated.rules.length || updated.inputLimits.length || updated.notices.length) {
                const linked = await validateStructure(updated, true);
                if (!linked.valid) throw Error("연관관계·입력제한 구조에서 수정이 필요한 항목을 찾았습니다.\n" + err(linked));
              }
              const continueNext = S.$("[data-continue-question]", body)?.checked;
              const nextKey = qs()[qs().findIndex((x) => x.key === q.key) + 1]?.key;
              process = updated;
              applied = true;
              dirty();
              d.close();
              paint();
              if (continueNext && nextKey) question(process.questions.find((x) => x.key === nextKey));
            } else {
              step++;
              draw();
            }
          });
      }
      function bindFiles(el, owner) {
        if (readOnly) { S.bindMedia(el); return; }
        const list = fs(owner.assetIds);
        S.fileEvents(el, list, () => {
          list.forEach((a) => files.set(a.id, a));
          owner.assetIds = list.map((a) => a.id);
        }, S.base + "/assets/stage", { maxFiles: q.fields.includes(owner) ? 20 : 200 });
      }
      function editCase(index) {
        const r =
          index == null
            ? { key: S.key("N"), name: "", guide: "", conditions: [] }
            : S.copy(q.numberCases[index]);
        const cd = S.dialog("숫자 범위 조건", ""),
          cb = S.$(".pms-dialog-body", cd);
        function cp() {
          cb.innerHTML = `<div class="pms-form-grid one">${S.field("범위 이름", "name", r.name, "text", 'maxlength="120"')}${S.textarea("범위 해당 시 고객 안내메시지", "guide", r.guide || "", 2000)}</div><p>필드를 선택해 조건을 추가하세요. 한 필드에 여러 조건을 추가할 수 있습니다.</p>${r.conditions.map((c, i) => `<div class="pms-card pms-form-grid four"><div class="pms-control-field"><span class="pms-field-label">조건 대상</span><strong class="pms-control-value">${S.e(q.fields.find((f) => f.key === c.fieldKey)?.labels.management)}</strong></div>${S.select("비교", "conditions." + i + ".operator", c.operator, { GE: "이상", GT: "초과", LE: "이하", LT: "미만", EQ: "일치", RANGE: "구간" })}${S.field("기준값 / 하한", "conditions." + i + ".lower", c.lower, "number", 'step="0.001"')}${c.operator === "RANGE" ? S.field("상한", "conditions." + i + ".upper", c.upper, "number", 'step="0.001"') + S.check("하한 포함", "conditions." + i + ".lowerInclusive", c.lowerInclusive) + S.check("상한 포함", "conditions." + i + ".upperInclusive", c.upperInclusive) : ""}<button data-del-condition="${i}">조건 삭제</button></div>`).join("")}<div class="pms-actions">${q.fields.map((f) => `<button data-add-condition="${S.e(f.key)}">+ ${S.e(f.labels.management)}</button>`).join("")}<button type="button" data-save-case class="primary">겹침 검사 · 범위 등록</button></div>`;
          S.bind(cb, r, (path) => {
            if (path.endsWith(".operator")) cp();
          });
          S.$$("[data-add-condition]", cb).forEach(
            (b) =>
              (b.onclick = () => {
                if (r.conditions.length >= 20)
                  return S.toast("한 범위는 최대 20개 조건입니다.");
                r.conditions.push({
                  groupKey: q.key,
                  fieldKey: b.dataset.addCondition,
                  operator: "GE",
                  choiceKeys: [],
                  lower: 0,
                  upper: null,
                  lowerInclusive: true,
                  upperInclusive: true,
                });
                cp();
              }),
          );
          S.$$("[data-del-condition]", cb).forEach(
            (b) =>
              (b.onclick = () => {
                r.conditions.splice(Number(b.dataset.delCondition), 1);
                cp();
              }),
          );
          S.$("[data-save-case]", cb).onclick = (e) =>
            S.run(e.currentTarget, async () => {
              const errors = {};
              if (!r.name.trim())
                errors.name = "범위 이름을 입력해 주세요. 예: ‘작은 사이즈’.";
              if (
                q.numberCases.some(
                  (other, i) =>
                    i !== index && other.name.trim() === r.name.trim(),
                )
              )
                errors.name =
                  "이미 사용한 범위 이름입니다. 구분되는 이름으로 입력해 주세요. 예: ‘W 301~500’.";
              if (!r.conditions.length)
                errors[""] =
                  "조건이 없습니다. 위의 ‘+ W’ 같은 필드 버튼을 눌러 비교와 기준값을 추가해 주세요.";
              r.conditions.forEach((c, i) => {
                const path = "conditions." + i + ".";
                if (c.lower == null || !Number.isFinite(Number(c.lower)))
                  errors[path + "lower"] =
                    "기준값을 입력해 주세요. 예: 300 이하라면 300.";
                if (c.operator === "RANGE") {
                  if (c.upper == null || !Number.isFinite(Number(c.upper)))
                    errors[path + "upper"] =
                      "상한값을 입력해 주세요. 예: 300~500이면 500.";
                  else if (
                    c.lower != null &&
                    (Number(c.lower) > Number(c.upper) ||
                      (Number(c.lower) === Number(c.upper) &&
                        (!c.lowerInclusive || !c.upperInclusive)))
                  )
                    errors[path + "upper"] =
                      "이 구간에는 가능한 값이 없습니다. 하한보다 큰 상한을 입력하거나, 한 값만 허용할 때 ‘일치’를 선택해 주세요.";
                }
              });
              if (Object.keys(errors).length) {
                const failure = Error("범위 조건의 입력값을 확인해 주세요.");
                failure.fieldErrors = errors;
                throw failure;
              }
              const test = S.copy(q);
              test.fixed = false;
              test.visible = true;
              test.requireRule = false;
              test.preset = null;
              if (index == null) test.numberCases.push(r);
              else test.numberCases[index] = r;
              const v = await S.request("/validate-question", "POST", test);
              if (!v.valid || !v.exhaustive) throw Error(err(v));
              q.numberCases = test.numberCases;
              cd.close();
              draw();
            });
        }
        cp();
      }
      draw();
    }
    function advanced(sourceKey, old) {
      return S.advancedRelationDialog(sourceKey, old, { process, getProcess: () => process, validate: validateStructure, validateStructure, apply: (updated) => { process = updated; dirty(); paint(); }, conditionText, readOnly });
    }
    function relation(sourceKey, old) {
      const source = process.questions.find((q) => q.key === sourceKey),
        targets = qs().filter(
          (q) =>
            process.questions.indexOf(q) > process.questions.indexOf(source) &&
            S.group(q.groupId)?.askQuestion !== false,
        );
      if (!targets.length)
        return S.toast(
          "연결할 다음 질문이 없습니다. 대상 질문을 시작 질문 뒤에 배치해 주세요.",
        );
      const complex =
        old &&
        (old.actions.length !== 1 ||
          old.match !== "ALL" ||
          old.conditions.some((c) => c.groupKey !== sourceKey) ||
          (S.isChoice(source.control) &&
            (old.conditions.length !== 1 ||
              old.conditions[0].operator !== "EQ")) ||
          (!S.isChoice(source.control) &&
            source.control !== "NUMBER" &&
            (old.conditions.length !== 1 ||
              !["PRESENT", "ABSENT"].includes(old.conditions[0].operator))) ||
          !["SHOW", "HIDE", "ALLOW", "RENAME"].includes(old.actions[0].effect));
      if ((readOnly && old || complex) && S.advancedRelationDialog) return advanced(sourceKey, old);
      if (readOnly) {
        S.dialog("등록된 연관관계 보기", `<div class="pm-rule-summary"><strong>${S.e(old?.name || nm(source))}</strong>${(old?.conditions || []).map((c) => `<span>${S.e(nm(process.questions.find((x) => x.key === c.groupKey)))}: ${S.e(conditionText(process.questions.find((x) => x.key === c.groupKey), [c]))}</span>`).join("")}<strong>적용 동작</strong>${(old?.actions || []).map((a) => `<span>${S.e(nm(process.questions.find((x) => x.key === a.targetKey)))} · ${S.e(a.effect)} ${S.e(a.questionText || (a.choiceKeys || []).join(" / "))}</span>`).join("")}</div>${!old ? '<p class="pms-help">등록된 개별 연결의 ‘연결 보기’를 누르시면 조건과 동작을 확인할 수 있습니다.</p>' : ""}`); return;
      }
      if (complex) {
        S.dialog(
          "기존 복합 연관관계 확인",
          `<div class="pms-alert">[${S.e(old.name)}]에는 현재 단계별 편집 방식과 다른 복수 시작 질문·동작 또는 조건 방식이 포함되어 있습니다. 일부 조건이 사라지는 것을 방지하기 위해 이 창에서 단순 연결로 덮어쓰지 않습니다.</div><p class="pms-help">대안: 원본 제품을 복사해 보관한 다음 이 연결을 삭제하고, 시작 답변과 대상별로 새 연결을 등록하세요. 예: ‘사이즈 → 문 수량 제한’과 ‘사이즈 → 설치 질문 건너뜀’을 각각 등록한 후 전체 검증을 진행합니다. 기존 연결을 그대로 쓰려면 닫기를 누르세요.</p><div class="pm-rule-summary"><strong>조건 결합: ${S.e(old.match === "ANY" ? "하나라도 만족" : "모두 만족")}</strong>${old.conditions
            .map((c) => {
              const q = process.questions.find((x) => x.key === c.groupKey);
              return `<span>${S.e(nm(q))}: ${S.e(conditionText(q, [c]))}</span>`;
            })
            .join(
              "",
            )}<strong>적용 동작</strong>${old.actions.map((a) => `<span>${S.e(nm(process.questions.find((q) => q.key === a.targetKey)))} · ${S.e({ HIDE: "건너뜀", ALLOW: "옵션 제한", RENAME: "질문 변경", SHOW: "표시", REQUIRE: "필수 답변", OPTIONAL: "선택 답변" }[a.effect] || a.effect)}</span>`).join("")}</div>`,
        );
        return;
      }
      let step = 0,
        selected = S.isChoice(source.control)
          ? old?.conditions[0]?.choiceKeys || []
          : [],
        conditions = S.copy(old?.conditions || []),
        match = old?.match || "ALL",
        targetKey = old?.actions[0]?.targetKey || "",
        effect = old?.actions[0]?.effect || "HIDE",
        targetChoices = old?.actions[0]?.choiceKeys || [],
        questionText = old?.actions[0]?.questionText || "",
        ruleName = old?.name || "",
        rangeKey =
          (source.numberCases || []).find(
            (c) =>
              conditionSignature(c.conditions) ===
              conditionSignature(conditions),
          )?.key || "";
      const d = S.dialog(nm(source) + " · 연관관계", ""),
        body = S.$(".pms-dialog-body", d);
      function rp() {
        const target = targets.find((t) => t.key === targetKey);
        body.innerHTML =
          steps(
            ["시작 답변 선택", "영향받을 대상", "동작 설정", "충돌 검사"],
            step,
          ) +
          '<div data-content></div><div class="pms-actions pm-stage-actions">' +
          (step ? "<button data-back>이전</button>" : "") +
          '<button data-next class="primary">' +
          (step === 3 ? "구조 확인 후 연결" : "다음") +
          "</button></div>";
        const box = S.$("[data-content]", body);
        if (step === 0) {
          if (S.isChoice(source.control))
            box.innerHTML =
              '<p>다음 답변 중 하나라도 선택되면 적용합니다.</p><div class="pms-check-grid">' +
              checks(source.choices, selected, "data-source") +
              "</div>";
          else if (source.control === "NUMBER") {
            box.innerHTML =
              "<p>등록한 숫자 범위를 선택하세요. 여러 범위를 사용하려면 범위별로 관계를 추가합니다.</p>" +
              S.select("시작 범위", "range", rangeKey, {
                "": "범위 선택",
                ...Object.fromEntries(
                  (source.numberCases || []).map((r) => [r.key, r.name]),
                ),
              }) +
              (old
                ? '<p class="pms-help">다른 범위를 선택하지 않으면 기존 조건을 유지합니다.</p>'
                : "");
            S.$("select", box).onchange = (e) => {
              rangeKey = e.target.value;
              conditions = S.copy(
                source.numberCases.find((r) => r.key === rangeKey)
                  ?.conditions || [],
              );
              match = "ALL";
            };
          } else
            box.innerHTML =
              S.select(
                "입력 필드",
                "field",
                conditions[0]?.fieldKey || source.fields[0]?.key,
                Object.fromEntries(
                  source.fields.map((f) => [f.key, f.labels.management]),
                ),
              ) +
              S.select(
                "조건",
                "presence",
                conditions[0]?.operator || "PRESENT",
                { PRESENT: "입력함", ABSENT: "입력 안 함" },
              );
        }
        if (step === 1) {
          box.innerHTML =
            '<p>조건 묶음을 대상 버튼에 드래그하거나 클릭해 연결하세요.</p><div class="pm-condition-bundle" draggable="true">' +
            S.e(nm(source) + " · " + conditionText(source, conditions)) +
            '</div><div class="pms-check-grid">' +
            targets
              .map(
                (t) =>
                  `<article class="pm-target ${t.key === targetKey ? "active" : ""}" data-target="${S.e(t.key)}"><h3>${S.e(nm(t))}</h3><button data-kind="question">질문에 연결</button>${S.isChoice(t.control) || t.control === "NUMBER" ? '<button data-kind="options">답변·입력 묶음에 연결</button>' : ""}</article>`,
              )
              .join("") +
            "</div>";
          S.$(".pm-condition-bundle", box).ondragstart = (e) =>
            e.dataTransfer.setData("application/pm-condition", sourceKey);
          S.$$("[data-kind]", box).forEach((b) => {
            const choose = () => {
              const nextTarget = b.closest("[data-target]").dataset.target;
              const nextEffect =
                b.dataset.kind === "options"
                  ? "ALLOW"
                  : ["SHOW", "RENAME"].includes(effect)
                    ? effect
                    : "HIDE";
              if (targetKey !== nextTarget || effect !== nextEffect)
                targetChoices = [];
              targetKey = nextTarget;
              effect = nextEffect;
              step = 2;
              rp();
            };
            b.onclick = choose;
            b.ondragover = (e) => e.preventDefault();
            b.ondrop = (e) => {
              e.preventDefault();
              if (
                e.dataTransfer.getData("application/pm-condition") === sourceKey
              )
                choose();
            };
          });
        }
        if (step === 2) {
          box.innerHTML = `<h3>${S.e(nm(target))}</h3>${effect === "ALLOW" ? '<p>이 조건에서 표시할 옵션을 묶어 주세요.</p><div class="pms-check-grid">' + (target.control === "NUMBER" ? S.numberBundleChecks(target, targetChoices) : checks(target.choices, targetChoices, "data-result")) + "</div>" : S.select("질문에 적용할 동작", "effect", effect, { HIDE: "지정한 질문만 건너뜀", SHOW: "지정한 질문 표시", RENAME: "질문 내용 변경" })}${effect === "RENAME" ? S.field("변경할 질문 내용", "questionText", questionText, "text", 'maxlength="300"') : ""}`;
          if (effect === "HIDE") box.insertAdjacentHTML("beforeend", '<p class="pms-help"><strong>지정한 질문 하나만 생략합니다.</strong> 예: 2번 답변으로 9번 질문을 건너뛰면 3~8번은 순서대로 진행하고 9번만 생략한 뒤 10번으로 진행합니다.</p>');
          S.$('[data-path="effect"]', box)?.addEventListener("change", (e) => {
            effect = e.target.value;
            rp();
          });
          S.$('[data-path="questionText"]', box)?.addEventListener(
            "input",
            (e) => (questionText = e.target.value),
          );
        }
        if (step === 3)
          box.innerHTML =
            S.field(
              "연관관계 이름",
              "ruleName",
              ruleName || nm(source) + " → " + nm(target),
              "text",
              'maxlength="120"',
            ) +
            `<div class="pm-rule-summary"><strong>시작: ${S.e(nm(source))}</strong><span>${S.e(conditionText(source, conditions))}</span><strong>대상: ${S.e(nm(target))}</strong><span>${S.e(effect === "ALLOW" ? "표시할 답변: " + targetChoices.map((k) => (S.isChoice(target.control) ? target.choices : target.fields).find((c) => c.key === k)?.labels.management || k).join(" / ") : effect === "HIDE" ? "이 질문을 건너뜁니다" : effect === "SHOW" ? "이 질문을 표시합니다" : "질문 변경: " + questionText)}</span></div>` +
            '<p class="pms-help"><strong>연결은 차례로 작성할 수 있습니다.</strong> 이 단계에서는 참조·순서·묶음 구조를 확인해 연결을 추가합니다. 모든 연결을 만든 뒤 ‘전체 경로 검증’에서 빠진 분기·겹침·건너뜀·입력제한 충돌을 검사해야 등록을 완료할 수 있습니다.</p>';
        S.$("[data-back]", body)?.addEventListener("click", () => {
          if (step === 3) ruleName = S.$('[data-path="ruleName"]', box).value;
          if (step === 2 && effect === "ALLOW") targetChoices = target.control === "NUMBER" ? S.readNumberBundleChecks(box, target) : S.$$("[data-result]:checked", box).map((x) => x.value);
          step--;
          rp();
        });
        S.$("[data-next]", body).onclick = (e) =>
          S.run(e.currentTarget, async () => {
            if (step === 0) {
              if (S.isChoice(source.control)) {
                selected = S.$$("[data-source]:checked", box).map(
                  (x) => x.value,
                );
                if (!selected.length) throw Error("시작 답변을 선택해 주세요.");
                conditions = [
                  {
                    groupKey: sourceKey,
                    fieldKey: null,
                    operator: "EQ",
                    choiceKeys: selected,
                  },
                ];
                match = "ALL";
              } else if (source.control !== "NUMBER") {
                conditions = [
                  {
                    groupKey: sourceKey,
                    fieldKey: S.$('[data-path="field"]', box).value,
                    operator: S.$('[data-path="presence"]', box).value,
                    choiceKeys: [],
                  },
                ];
                match = "ALL";
              }
              if (!conditions.length)
                throw Error("숫자 범위를 먼저 등록하고 선택해 주세요.");
            }
            if (step === 1 && !targetKey)
              throw Error("영향받을 질문 또는 옵션을 선택해 주세요.");
            if (step === 2) {
              if (effect === "ALLOW") {
                targetChoices = target.control === "NUMBER" ? S.readNumberBundleChecks(box, target) : S.$$("[data-result]:checked", box).map((x) => x.value);
                if (!targetChoices.length)
                  throw Error("표시할 옵션을 한 개 이상 선택해 주세요.");
              }
              if (effect === "RENAME" && !questionText.trim())
                throw Error("변경할 질문 내용을 입력해 주세요.");
            }
            if (step === 3) {
              const r = {
                key: old?.key || S.key("R"),
                name: S.$('[data-path="ruleName"]', box).value,
                match,
                conditions,
                actions: [
                  {
                    targetKey,
                    effect,
                    choiceKeys: effect === "ALLOW" ? targetChoices : [],
                    questionText: effect === "RENAME" ? questionText : null,
                  },
                ],
              };
              const test = S.copy(process);
              test.rules = test.rules.filter((x) => x.key !== r.key);
              test.rules.push(r);
              const v = await validateStructure(test);
              if (!v.valid) throw Error(err(v));
              process = test;
              dirty();
              d.close();
              paint();
            } else {
              step++;
              rp();
            }
          });
      }
      rp();
    }
    paint();
    S.dirty = false;
    const initialKey = new URLSearchParams(location.search).get("question");
    const initialQuestion = qs().find((q) => q.key === initialKey);
    if (initialQuestion) question(initialQuestion);
  };
})(window.PMS);
