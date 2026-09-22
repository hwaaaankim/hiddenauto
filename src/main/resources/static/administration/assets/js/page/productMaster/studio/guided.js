(function (S) {
  "use strict";
  const nm = (q) => q?.labels?.management || "삭제된 질문",
    err = (v) =>
      v.issues
        .filter((i) => i.severity === "ERROR")
        .map((i) => i.location + ": " + i.message)
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
    let process = S.copy(p.process),
      stage = 0;
    const qs = () =>
        process.questions.filter((q) => !S.isBase(S.group(q.groupId))),
      fixed = () =>
        process.questions.filter((q) => S.isBase(S.group(q.groupId)));
    const files = new Map((p.processAssets || []).map((a) => [a.id, a]));
    const fs = (ids) => (ids || []).map((id) => files.get(id)).filter(Boolean);
    if (p.actualCount) {
      root.innerHTML = `<p class="pms-help">실 제품 ${p.actualCount}개가 있어 구성과 프로세스를 수정할 수 없습니다. 제품을 복사하면 별도로 편집할 수 있습니다.</p><a class="pms-button" href="/admin/product-master/products/${p.id}/view">제품 상세 조회</a>`;
      return;
    }
    function dirty() {
      S.dirty = true;
    }
    async function validate(test = process) {
      const v = await S.request(
        "/products/" + p.id + "/validate",
        "POST",
        test,
      );
      const el = S.$("#pm-validation", root);
      if (el)
        el.innerHTML = `<p class="pms-help">${v.valid && v.exhaustive ? "검증 통과" : "수정 필요"} · ${v.scenarios}개 경로</p>${v.issues.map((i) => `<p class="${i.severity === "ERROR" ? "pms-error" : "pms-help"}">${S.e(i.location)}: ${S.e(i.message)}</p>`).join("")}`;
      return v;
    }
    async function save(publish) {
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
      process.questions = [...fixed(), ...rows];
      dirty();
      paint();
    }
    function paint() {
      root.innerHTML = `<section class="pms-panel"><header class="pms-panel-title"><div><h2>${S.e(p.productName)}</h2><small>고정 분류: ${fixed()
        .map((q) => q.choices.map((c) => S.e(c.labels.management)).join("/"))
        .join(
          " · ",
        )}</small></div><a class="pms-button" href="/admin/product-master/products/${p.id}">제품 구성</a></header>${steps(["질문·답변 완성", "연관관계 설정", "전체 검증·등록"], stage)}<div class="pms-panel-body"><p class="pms-help">${["각 질문을 열어 가능한 답변을 차례로 등록하세요. 숫자는 필드 구성 → 입력 제한 → 범위 조건 순서입니다.", "앞 질문의 답변을 조건으로 묶어 뒤 질문 또는 옵션에 연결합니다. 겹치는 조건은 등록되지 않습니다.", "전체 입력 경로를 검사한 뒤 등록을 완료합니다. 저장된 제품으로 고객 흐름을 테스트할 수 있습니다."][stage]}</p><div class="pm-order-strip">${qs()
        .map(
          (q, i) =>
            `<div data-order="${S.e(q.key)}"><span class="pms-handle">⠿</span>${i + 1}. ${S.e(nm(q))}<button data-move="${i}:-1" ${i === 0 ? "disabled" : ""}>↑</button><button data-move="${i}:1" ${i === qs().length - 1 ? "disabled" : ""}>↓</button></div>`,
        )
        .join("")}</div><div class="pm-flow-canvas">${
        qs()
          .map(
            (q, i) =>
              `<article class="pm-flow-node"><span class="pms-badge">${i + 1} · ${S.e(S.controls[q.control])}</span><h3>${S.e(q.question || nm(q))}</h3><p>${S.group(q.groupId)?.askQuestion === false ? "고정 사양 · 질문 제외" : S.isChoice(q.control) ? q.choices.length + "개 답변" : q.fields.length + "개 입력 항목"}${q.control === "NUMBER" ? " · " + (q.numberCases || []).length + "개 범위" : ""}</p>${stage === 0 ? `<button data-edit="${S.e(q.key)}" class="primary">답변 등록·수정</button>` : ""}${stage === 1 ? `<button data-relate="${S.e(q.key)}" ${i === qs().length - 1 ? "disabled" : ""}>이 답변에서 연관관계 설정</button>` : ""}${process.rules
                .filter((r) => r.conditions.some((c) => c.groupKey === q.key))
                .map(
                  (r) =>
                    `<div class="pm-rule-line">${S.e(r.name)} → ${r.actions.map((a) => S.e(nm(process.questions.find((x) => x.key === a.targetKey))) + " · " + S.e({ HIDE: "건너뜀", ALLOW: "옵션 제한", RENAME: "질문 변경" }[a.effect] || a.effect)).join(", ")}${stage === 1 ? `<button data-rule-edit="${S.e(r.key)}">수정</button><button data-rule-remove="${S.e(r.key)}">삭제</button>` : ""}</div>`,
                )
                .join(
                  "",
                )}</article>${i < qs().length - 1 ? '<div class="pm-flow-arrow">↓</div>' : ""}`,
          )
          .join("") ||
        '<p class="pms-empty">추가 질문 없이 필수 분류만으로 등록할 수 있습니다.</p>'
      }</div><div id="pm-validation"></div><div class="pms-actions pm-stage-actions">${stage ? '<button id="pm-prev">이전 단계</button>' : ""}<button id="pm-draft">임시 저장</button>${stage < 2 ? '<button id="pm-next" class="primary">' + (stage === 0 ? "답변 확인 · 연관관계 설정" : "전체 검증 단계로") + "</button>" : `<button id="pm-validate">전체 경로 검증</button><button id="pm-publish" class="primary">검증 후 등록완료</button><a class="pms-button" target="_blank" href="/admin/product-master/products/${p.id}/test">저장된 제품 고객 테스트</a>`}</div></div></section>`;
      S.$$("[data-edit]", root).forEach(
        (b) =>
          (b.onclick = () =>
            question(process.questions.find((q) => q.key === b.dataset.edit))),
      );
      S.$$("[data-relate]", root).forEach(
        (b) => (b.onclick = () => relation(b.dataset.relate)),
      );
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
      S.sortable(S.$(".pm-order-strip", root), "[data-order]", reorder);
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
          const v = await validate(
            stage === 0 ? { ...process, rules: [] } : process,
          );
          if (!v.valid || !v.exhaustive) throw Error(err(v));
          stage++;
          paint();
        }),
      );
      S.$("#pm-draft", root).onclick = (e) =>
        S.run(e.currentTarget, () => save(false));
      S.$("#pm-validate", root)?.addEventListener("click", (e) =>
        S.run(e.currentTarget, () => validate()),
      );
      S.$("#pm-publish", root)?.addEventListener("click", (e) =>
        S.run(e.currentTarget, () => save(true)),
      );
    }
    function question(original) {
      let q = S.copy(original),
        step = 0;
      q.numberCases ??= [];
      q.preset ??= { choices: [], fields: {} };
      const hidden = S.group(q.groupId)?.askQuestion === false;
      const titles = [
        "가능한 답변 등록",
        ...(!S.isChoice(q.control) ? ["입력 제한 설정"] : []),
        ...(q.control === "NUMBER" ? ["범위 조건 설정"] : []),
        ...(hidden ? ["고정 사양 입력"] : []),
        "확인",
      ];
      const d = S.dialog(nm(q) + " · 답변 설정", ""),
        body = S.$(".pms-dialog-body", d);
      function draw() {
        const title = titles[step];
        body.innerHTML =
          steps(titles, step) +
          '<div data-content></div><div class="pms-actions pm-stage-actions">' +
          (step ? "<button data-back>이전</button>" : "") +
          '<button data-next class="primary">' +
          (step === titles.length - 1 ? "이 질문 완성" : "다음") +
          "</button></div>";
        const box = S.$("[data-content]", body);
        if (title === "가능한 답변 등록") {
          const choice = S.isChoice(q.control),
            rows = choice ? q.choices : q.fields,
            prefix = choice ? "choices." : "fields.";
          box.innerHTML = `<div class="pms-form-grid two">${S.field("질문 내용", "question", q.question || nm(q), "text", 'maxlength="300"')}${S.field("질문 도움말", "guide", q.guide || "", "text", 'maxlength="1000"')}${S.check("반드시 답변", "required", q.required)}</div><div class="pms-actions pms-section"><strong>${choice ? "선택 가능한 답변" : "입력받을 항목"}</strong><button data-add>+ ${choice ? "답변" : "입력 필드"}</button></div>${rows.map((x, i) => `<article class="pms-card">${S.labels(x, prefix + i, true, x.namePart)}${S.field("안내메시지", prefix + i + ".guide", x.guide || "", "text", 'maxlength="2000"')}${S.field("내부 value", prefix + i + ".key", x.key, "text", 'maxlength="80" pattern="[A-Za-z0-9_-]+"')}<button data-remove="${i}">삭제</button>${choice ? `<details><summary>이미지·파일</summary><div data-choice-files="${i}">${S.files(fs(x.assetIds))}</div></details>` : ""}</article>`).join("")}<details><summary>질문 이미지·파일</summary><div data-q-files>${S.files(fs(q.assetIds))}</div></details>`;
          S.bind(box, q);
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
            draw();
          };
          S.$$("[data-remove]", box).forEach(
            (b) =>
              (b.onclick = () => {
                rows.splice(Number(b.dataset.remove), 1);
                draw();
              }),
          );
          S.$$("[data-choice-files]", box).forEach((el) =>
            bindFiles(el, q.choices[Number(el.dataset.choiceFiles)]),
          );
          bindFiles(S.$("[data-q-files]", box), q);
        } else if (title === "입력 제한 설정") {
          box.innerHTML =
            '<p class="pms-help">각 필드의 허용 범위를 정하세요. 숫자 범위 조건은 다음 단계입니다.</p>' +
            S.fieldRows(q.fields, q.control);
          S.bind(box, q);
          S.fieldEvents(box, q.fields, draw);
        } else if (title === "범위 조건 설정") {
          box.innerHTML = `<p class="pms-help">범위 안의 조건은 모두 만족(AND)해야 합니다. 다른 범위와 값이 겹치면 등록할 수 없습니다. 어떤 범위에도 해당하지 않는 값에는 기본 흐름이 적용됩니다.</p><button data-case-add>+ 범위 조건</button>${q.numberCases.map((r, i) => `<article class="pms-card"><h3>${S.e(r.name)}</h3><p>${r.conditions.map((c) => S.e(q.fields.find((f) => f.key === c.fieldKey)?.labels.management) + " " + S.e(c.operator) + " " + S.e(c.lower) + (c.operator === "RANGE" ? " ~ " + S.e(c.upper) : "")).join(" AND ")}</p>${r.guide ? "<p>" + S.e(r.guide) + "</p>" : ""}<button data-case-edit="${i}">수정</button><button data-case-delete="${i}">삭제</button></article>`).join("")}`;
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
            S.inputAnswer(q, q.preset);
          S.$$("input[type=file]", box).forEach(
            (input) =>
              (input.onchange = () =>
                S.run(null, async () => {
                  const fd = new FormData();
                  for (const f of input.files) fd.append("files", f);
                  const added = await S.request("/assets/stage", "POST", fd);
                  q.preset.fields[input.dataset.answerField] = added.map(
                    (a) => a.id,
                  );
                  added.forEach((a) => files.set(a.id, a));
                  S.$(
                    '[data-answer-files="' + input.dataset.answerField + '"]',
                    box,
                  ).textContent = added.map((a) => a.name).join(", ");
                })),
          );
        } else
          box.innerHTML = `<h3>${S.e(q.question || nm(q))}</h3><p>${(S.isChoice(q.control) ? q.choices : q.fields).map((x) => S.e(x.labels.management)).join(" / ")}</p><p>${q.numberCases.length}개 범위 조건 · ${hidden ? "고정 사양 저장" : "고객 답변"}</p>`;
        S.$("[data-back]", body)?.addEventListener("click", () => {
          if (title === "고정 사양 입력")
            q.preset = S.readAnswer(box, q, q.preset);
          step--;
          draw();
        });
        S.$("[data-next]", body).onclick = (e) =>
          S.run(e.currentTarget, async () => {
            if (title === "가능한 답변 등록") {
              const rows = S.isChoice(q.control) ? q.choices : q.fields;
              if (!rows.length)
                throw Error("답변 또는 입력 필드를 먼저 추가해 주세요.");
              const errors = {};
              rows.forEach((x, i) =>
                S.checkLabels(x.labels, "rows." + i + ".", 120, errors),
              );
              S.checkUniqueLabels(rows, "rows.", errors);
              if (Object.keys(errors).length)
                throw Error(Object.values(errors).join(" / "));
            }
            if (title === "입력 제한 설정") {
              const errors = {};
              S.checkFields(q.fields, q.control, errors);
              if (Object.keys(errors).length)
                throw Error(Object.values(errors).join(" / "));
            }
            if (title === "고정 사양 입력")
              q.preset = S.readAnswer(box, q, q.preset);
            if (step === titles.length - 1) {
              q.fixed = hidden;
              q.visible = !hidden;
              q.requireRule = false;
              if (!hidden) q.preset = null;
              const v = await S.request("/validate-question", "POST", q);
              if (!v.valid || !v.exhaustive) throw Error(err(v));
              process.questions[
                process.questions.findIndex((x) => x.key === q.key)
              ] = q;
              dirty();
              d.close();
              paint();
            } else {
              step++;
              draw();
            }
          });
      }
      function bindFiles(el, owner) {
        const list = fs(owner.assetIds);
        S.fileEvents(el, list, () => {
          list.forEach((a) => files.set(a.id, a));
          owner.assetIds = list.map((a) => a.id);
        });
      }
      function editCase(index) {
        const r =
          index == null
            ? { key: S.key("N"), name: "", guide: "", conditions: [] }
            : S.copy(q.numberCases[index]);
        const cd = S.dialog("숫자 범위 조건", ""),
          cb = S.$(".pms-dialog-body", cd);
        function cp() {
          cb.innerHTML = `<div class="pms-form-grid two">${S.field("범위 이름", "name", r.name, "text", 'maxlength="120"')}${S.field("범위 해당 시 안내메시지", "guide", r.guide || "", "text", 'maxlength="2000"')}</div><p>필드를 선택해 조건을 추가하세요. 한 필드에 여러 조건을 추가할 수 있습니다.</p><div class="pms-actions">${q.fields.map((f) => `<button data-add-condition="${S.e(f.key)}">+ ${S.e(f.labels.management)}</button>`).join("")}</div>${r.conditions.map((c, i) => `<div class="pms-card pms-form-grid four"><strong>${S.e(q.fields.find((f) => f.key === c.fieldKey)?.labels.management)}</strong>${S.select("비교", "conditions." + i + ".operator", c.operator, { GE: "이상", GT: "초과", LE: "이하", LT: "미만", EQ: "일치", RANGE: "구간" })}${S.field("기준값 / 하한", "conditions." + i + ".lower", c.lower, "number", 'step="0.001"')}${c.operator === "RANGE" ? S.field("상한", "conditions." + i + ".upper", c.upper, "number", 'step="0.001"') + S.check("하한 포함", "conditions." + i + ".lowerInclusive", c.lowerInclusive) + S.check("상한 포함", "conditions." + i + ".upperInclusive", c.upperInclusive) : ""}<button data-del-condition="${i}">조건 삭제</button></div>`).join("")}<button data-save-case class="primary">겹침 검사 · 범위 등록</button>`;
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
              const test = S.copy(q);
              test.fixed = false;
              test.visible = true;
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
    function relation(sourceKey, old) {
      const source = process.questions.find((q) => q.key === sourceKey),
        targets = qs().filter(
          (q) =>
            process.questions.indexOf(q) > process.questions.indexOf(source) &&
            S.group(q.groupId)?.askQuestion !== false,
        );
      if (!targets.length) return S.toast("연결할 다음 질문이 없습니다.");
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
        ruleName = old?.name || "";
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
          (step === 3 ? "검증 후 연결" : "다음") +
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
              S.select("시작 범위", "range", "", {
                "": "범위 선택",
                ...Object.fromEntries(
                  (source.numberCases || []).map((r) => [r.key, r.name]),
                ),
              }) +
              (old
                ? '<p class="pms-help">다른 범위를 선택하지 않으면 기존 조건을 유지합니다.</p>'
                : "");
            S.$("select", box).onchange = (e) => {
              conditions = S.copy(
                source.numberCases.find((r) => r.key === e.target.value)
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
            S.e(nm(source)) +
            ' · 선택한 조건 묶음</div><div class="pms-check-grid">' +
            targets
              .map(
                (t) =>
                  `<article class="pm-target ${t.key === targetKey ? "active" : ""}" data-target="${S.e(t.key)}"><h3>${S.e(nm(t))}</h3><button data-kind="question">질문에 연결</button>${S.isChoice(t.control) ? '<button data-kind="options">옵션에 연결</button>' : ""}</article>`,
              )
              .join("") +
            "</div>";
          S.$(".pm-condition-bundle", box).ondragstart = (e) =>
            e.dataTransfer.setData("application/pm-condition", sourceKey);
          S.$$("[data-kind]", box).forEach((b) => {
            const choose = () => {
              targetKey = b.closest("[data-target]").dataset.target;
              effect = b.dataset.kind === "options" ? "ALLOW" : "HIDE";
              targetChoices = [];
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
          box.innerHTML = `<h3>${S.e(nm(target))}</h3>${effect === "ALLOW" ? '<p>이 조건에서 표시할 옵션을 묶어 주세요.</p><div class="pms-check-grid">' + checks(target.choices, targetChoices, "data-result") + "</div>" : S.select("질문에 적용할 동작", "effect", effect, { HIDE: "질문 건너뜀", RENAME: "질문 내용 변경" })}${effect === "RENAME" ? S.field("변경할 질문 내용", "questionText", questionText, "text", 'maxlength="300"') : ""}`;
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
            '<p class="pms-help">동시에 적용되는 옵션 제한과 건너뜀·질문 변경 충돌을 검사합니다. 충돌한 규칙 이름과 입력 예시를 확인해 조건을 분리하세요.</p>';
        S.$("[data-back]", body)?.addEventListener("click", () => {
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
                targetChoices = S.$$("[data-result]:checked", box).map(
                  (x) => x.value,
                );
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
              const v = await validate(test);
              if (!v.valid || !v.exhaustive) throw Error(err(v));
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
  };
})(window.PMS);
