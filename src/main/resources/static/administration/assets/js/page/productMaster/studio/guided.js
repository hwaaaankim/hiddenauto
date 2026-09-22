(function (S) {
  "use strict";
  const nm = (q) => q?.labels?.management || "삭제된 질문",
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
              (q) => nm(q) === i.location && !S.isBase(S.group(q.groupId)),
            );
            const rules = test.rules.filter(
              (r) =>
                r.name === i.location ||
                i.message.includes("[" + r.name) ||
                i.message.includes(r.name + "]") ||
                i.message.includes(" / " + r.name),
            );
            return `<article class="pm-validation-issue ${i.severity === "ERROR" ? "pms-error" : ""}"><strong>${S.e(i.location)} · ${i.severity === "ERROR" ? "수정 필요" : "확인"}</strong><p>${S.e(i.message)}</p>${S.errorAdvice(i.message) ? `<p class="pm-fix-example">${S.e(S.errorAdvice(i.message))}</p>` : ""}<div class="pms-actions">${question ? `<button data-fix-question="${S.e(question.key)}">${S.e(nm(question))} 답변 수정</button>` : ""}${rules.map((r) => `<button data-fix-rule="${S.e(r.key)}">${S.e(r.name)} 연결 수정</button>`).join("")}</div></article>`;
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
      const ordered = [...fixed(), ...rows];
      const index = new Map(ordered.map((q, i) => [q.key, i]));
      const backward = process.rules.filter((r) =>
        r.conditions.some((c) =>
          r.actions.some(
            (a) => index.get(c.groupKey) >= index.get(a.targetKey),
          ),
        ),
      );
      if (backward.length) {
        paint();
        S.showErrors(S.$("#pm-validation", root), {
          "":
            "순서를 변경할 수 없습니다. 다음 연결은 시작 질문이 대상 질문보다 앞에 있어야 합니다: " +
            backward.map((r) => r.name).join(" / "),
        });
        return;
      }
      process.questions = ordered;
      dirty();
      paint();
    }
    function paint() {
      const saveStatus = S.dirty ? "저장하지 않은 변경사항" : "저장된 상태";
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
      }</div><div id="pm-validation"></div><div class="pms-actions pm-stage-actions"><span class="pm-save-status" role="status">${saveStatus}</span>${stage ? '<button id="pm-prev">이전 단계</button>' : ""}<button id="pm-draft">임시 저장</button>${stage < 2 ? '<button id="pm-next" class="primary">' + (stage === 0 ? "답변 확인 · 연관관계 설정" : "전체 검증 단계로") + "</button>" : `<button id="pm-validate">전체 경로 검증</button><button id="pm-publish" class="primary">검증 후 등록완료</button><a class="pms-button" target="_blank" href="/admin/product-master/products/${p.id}/test">저장된 제품 고객 테스트</a>`}</div></div></section>`;
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
    function question(original) {
      let q = S.copy(original),
        step = 0,
        openRow = 0;
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
          box.innerHTML = `<div class="pms-form-grid two">${S.field("질문 내용", "question", q.question || nm(q), "text", 'maxlength="300"')}${S.field("질문 도움말", "guide", q.guide || "", "text", 'maxlength="1000"')}${S.check("반드시 답변", "required", q.required)}</div><div class="pms-actions pms-section"><strong>${choice ? "선택 가능한 답변" : "입력받을 항목"}</strong><button data-add>+ ${choice ? "답변" : "입력 필드"}</button></div>${rows.map((x, i) => `<details class="pm-answer-editor" ${i === openRow ? "open" : ""}><summary>${i + 1}. <strong data-row-title="${i}">${S.e(x.labels?.management || (choice ? "새 답변" : "새 입력 필드"))}</strong></summary><div class="pm-answer-body">${S.labels(x, prefix + i, true, x.namePart)}${S.field("안내메시지", prefix + i + ".guide", x.guide || "", "text", 'maxlength="2000"')}${S.field("내부 value", prefix + i + ".key", x.key, "text", 'maxlength="80" pattern="[A-Za-z0-9_-]+"')}<button data-remove="${i}">삭제</button>${choice ? `<details><summary>이미지·파일</summary><div data-choice-files="${i}">${S.files(fs(x.assetIds))}</div></details>` : ""}</div></details>`).join("")}<details><summary>질문 이미지·파일</summary><div data-q-files>${S.files(fs(q.assetIds))}</div></details>`;
          S.bind(box, q, (path) => {
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
                S.checkLabels(
                  x.labels,
                  (S.isChoice(q.control) ? "choices." : "fields.") + i + ".",
                  120,
                  errors,
                ),
              );
              S.checkUniqueLabels(
                rows,
                S.isChoice(q.control) ? "choices." : "fields.",
                errors,
              );
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
              q.visible = !hidden;
              q.requireRule = false;
              if (!hidden) q.preset = null;
              const v = await S.request("/validate-question", "POST", q);
              if (!v.valid || !v.exhaustive) throw Error(err(v));
              const updated = S.copy(process);
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
              if (updated.rules.length) {
                const linked = await validate(updated);
                if (!linked.valid || !linked.exhaustive)
                  throw Error(
                    "기존 연관관계에 영향을 주어 질문을 변경할 수 없습니다.\n" +
                      err(linked),
                  );
              }
              process = updated;
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
          !["HIDE", "ALLOW", "RENAME"].includes(old.actions[0].effect));
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
                  `<article class="pm-target ${t.key === targetKey ? "active" : ""}" data-target="${S.e(t.key)}"><h3>${S.e(nm(t))}</h3><button data-kind="question">질문에 연결</button>${S.isChoice(t.control) ? '<button data-kind="options">옵션에 연결</button>' : ""}</article>`,
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
                  : effect === "RENAME"
                    ? "RENAME"
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
            `<div class="pm-rule-summary"><strong>시작: ${S.e(nm(source))}</strong><span>${S.e(conditionText(source, conditions))}</span><strong>대상: ${S.e(nm(target))}</strong><span>${S.e(effect === "ALLOW" ? "표시할 답변: " + targetChoices.map((k) => target.choices.find((c) => c.key === k)?.labels.management || k).join(" / ") : effect === "HIDE" ? "이 질문을 건너뜁니다" : "질문 변경: " + questionText)}</span></div>` +
            '<p class="pms-help">동시에 적용되는 옵션 제한과 건너뜀·질문 변경 충돌을 검사합니다. 충돌한 규칙 이름과 입력 예시를 확인해 조건을 분리하세요.</p>';
        S.$("[data-back]", body)?.addEventListener("click", () => {
          if (step === 3) ruleName = S.$('[data-path="ruleName"]', box).value;
          if (step === 2 && effect === "ALLOW")
            targetChoices = S.$$("[data-result]:checked", box).map(
              (x) => x.value,
            );
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
