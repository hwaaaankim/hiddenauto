(function (S) {
  "use strict";
  S.processPage = async function (root, product) {
    let process = S.copy(product.process),
      selected =
        process.questions.find((q) => !q.fixed)?.key ||
        process.questions[0]?.key,
      zoom = 1,
      highlight = null,
      lastValidation = null;
    const files = new Map(
      [
        ...(product.processAssets || []),
        ...product.groups.flatMap((g) => [
          ...g.assets,
          ...g.values.flatMap((v) => v.assets),
        ]),
      ].map((a) => [a.id, a]),
    );
    for (const q of process.questions) {
      q.fields = q.fields || [];
      q.choices = q.choices || [];
      q.assetIds = q.assetIds || [];
      q.question = q.question || "";
      q.guide = q.guide || "";
    }
    const qBy = (key) => process.questions.find((q) => q.key === key);
    const rulesFrom = (key) =>
      process.rules.filter((r) => r.conditions.some((c) => c.groupKey === key));
    root.innerHTML = `<section class="pms-panel"><div class="pms-toolbar"><strong>${S.e(product.productName)}</strong>${S.badge(product.registrationStatus, product.status === "DRAFT" ? "amber" : "green")}<small class="mono">${S.e(product.catalogCode)}</small><div class="grow"></div><a class="pms-button" href="/admin/product-master/products/${product.id}">제품 상세</a><button id="pms-validate-process">✓ 검증</button><button id="pms-preview-process">▶ 선택 미리보기</button><button id="pms-draft-process">임시저장</button><button class="primary" id="pms-publish-process">검증·등록완료</button></div><div id="pms-process-health" class="pms-panel-body"><small>질문을 먼저 구성하고, 커스텀 구간을 다음 질문으로 연결해 주세요.</small></div></section><div class="pms-flow-layout pms-section"><section class="pms-panel"><div class="pms-panel-title"><h2>질문 순서</h2><small>드래그 변경</small></div><div id="pms-flow-order" class="pms-palette"></div></section><section class="pms-panel"><div class="pms-toolbar"><h2>조건 연결 캔버스</h2><div class="grow"></div><button id="pms-zoom-out">−</button><span id="pms-zoom">100%</span><button id="pms-zoom-in">+</button><button id="pms-zoom-fit">전체 보기</button></div><div class="pms-canvas-viewport" id="pms-viewport"><div id="pms-canvas-size"><div id="pms-canvas-stage" class="pms-canvas-stage"><canvas id="pms-lines"></canvas><div id="pms-flow-nodes"></div></div></div></div></section><section class="pms-panel"><div class="pms-panel-title"><h2>선택한 질문</h2><button id="pms-edit-question">상세 편집</button></div><div id="pms-question-inspector" class="pms-panel-body pms-inspector"></div></section></div>`;
    const order = S.$("#pms-flow-order", root),
      nodes = S.$("#pms-flow-nodes", root),
      stage = S.$("#pms-canvas-stage", root),
      viewport = S.$("#pms-viewport", root),
      inspector = S.$("#pms-question-inspector", root),
      canvas = S.$("#pms-lines", root);
    let positions = new Map(),
      stageHeight = 900;
    function changed() {
      S.dirty = true;
      lastValidation = null;
      S.$("#pms-process-health", root).innerHTML =
        "<small>변경 사항이 있습니다. 저장 전 검증해 주세요.</small>";
    }
    function paint() {
      paintOrder();
      paintNodes();
      paintInspector();
    }
    function paintOrder() {
      order.innerHTML = process.questions
        .map(
          (q, i) =>
            `<article class="pms-palette-item ${q.key === selected ? "active" : ""}" data-question-key="${S.e(q.key)}" draggable="true"><strong><span class="pms-handle">⠿</span> ${i + 1}. ${S.e(q.labels.management)}</strong><small>${q.fixed ? S.badge("고정 사양", "blue") : S.badge(S.controls[q.control])} ${q.fixed ? "" : S.isChoice(q.control) ? q.choices.length + "개 보기" : q.fields.length + "개 입력"}</small><div class="pms-actions"><button data-question-up="${i}" ${!i ? "disabled" : ""}>↑</button><button data-question-down="${i}" ${i === process.questions.length - 1 ? "disabled" : ""}>↓</button></div></article>`,
        )
        .join("");
      S.$$("[data-question-key]", order).forEach(
        (el) =>
          (el.onclick = (e) => {
            if (e.target.closest("button")) return;
            selected = el.dataset.questionKey;
            paint();
          }),
      );
      for (const dir of ["up", "down"])
        S.$$("[data-question-" + dir + "]", order).forEach(
          (b) =>
            (b.onclick = () => {
              const i = Number(
                b.dataset[dir === "up" ? "questionUp" : "questionDown"],
              );
              process.questions.splice(
                i + (dir === "up" ? -1 : 1),
                0,
                process.questions.splice(i, 1)[0],
              );
              changed();
              paint();
            }),
        );
    }
    S.sortable(order, "[data-question-key]", (from, to) => {
      process.questions.splice(to, 0, process.questions.splice(from, 1)[0]);
      changed();
      paint();
    });
    function conditionText(c) {
      const q = qBy(c.groupKey),
        field = q?.fields.find((f) => f.key === c.fieldKey);
      const name = q?.labels.management || c.groupKey;
      const fieldName = field?.labels.management || "";
      if (c.operator === "PRESENT")
        return name + (fieldName ? " " + fieldName : "") + " 있음";
      if (c.operator === "ABSENT")
        return name + (fieldName ? " " + fieldName : "") + " 없음";
      if (S.isChoice(q?.control))
        return (
          name +
          " " +
          (c.operator === "NE" ? "제외 " : "= ") +
          (c.choiceKeys || [])
            .map(
              (k) => q.choices.find((x) => x.key === k)?.labels.management || k,
            )
            .join(", ")
        );
      if (c.operator === "RANGE")
        return `${fieldName || name} ${c.lowerInclusive ? "[" : "("}${c.lower ?? "…"} ~ ${c.upper ?? "…"}${c.upperInclusive ? "]" : ")"}`;
      return `${fieldName || name} ${{ EQ: "=", NE: "≠", GT: ">", GE: "≥", LT: "<", LE: "≤" }[c.operator]} ${c.lower ?? c.text ?? ""}`;
    }
    const ruleText = (r) =>
      r.conditions
        .map(conditionText)
        .join(r.match === "ANY" ? " 또는 " : " · ");
    function paintNodes() {
      positions = new Map();
      const cols = 3;
      let y = 35;
      for (let i = 0; i < process.questions.length; i += cols) {
        const row = process.questions.slice(i, i + cols),
          height = Math.max(
            ...row.map((q) => 165 + Math.min(4, rulesFrom(q.key).length) * 48),
          );
        row.forEach((q, col) =>
          positions.set(q.key, { x: 35 + col * 340, y, h: height }),
        );
        y += height + 80;
      }
      stageHeight = Math.max(800, y);
      stage.style.width = "1080px";
      stage.style.height = stageHeight + "px";
      nodes.innerHTML = process.questions
        .map((q, i) => {
          const pos = positions.get(q.key),
            related = rulesFrom(q.key);
          return `<article class="pms-flow-node ${q.key === selected ? "selected" : ""}" style="left:${pos.x}px;top:${pos.y}px;min-height:${pos.h}px" data-node="${S.e(q.key)}"><div class="head"><strong>${i + 1}. ${S.e(q.labels.management)}</strong>${q.fixed ? S.badge("고정", "blue") : ""}</div><div class="body"><small>${S.e(S.controls[q.control])} · ${q.fixed ? "제품 사양" : q.visible ? "기본 표시" : "기본 건너뜀"}</small><p>${S.e((q.question || q.labels.customer).slice(0, 60))}</p>${related
            .slice(0, 4)
            .map(
              (r) =>
                `<div class="pms-rule-chip" draggable="true" data-rule-drag="${S.e(r.key)}" title="${S.e(r.name + " · " + ruleText(r))}"><strong>${S.e(r.name)}</strong><small style="display:block">${S.e(ruleText(r).slice(0, 60))}</small></div>`,
            )
            .join(
              "",
            )}${related.length > 4 ? `<small>외 ${related.length - 4}개 · 오른쪽에서 전체 확인</small>` : ""}</div><div class="port" draggable="true" data-source-drag="${S.e(q.key)}">+ 커스텀 구간 → 대상 질문으로 연결</div></article>`;
        })
        .join("");
      S.$$("[data-node]", nodes).forEach((el) => {
        el.onclick = (e) => {
          if (e.target.closest("[data-rule-drag],[data-source-drag]")) return;
          selected = el.dataset.node;
          paint();
        };
        el.ondragover = (e) => {
          if (
            e.dataTransfer.types.some(
              (t) =>
                t === "application/pm-rule" || t === "application/pm-source",
            )
          ) {
            e.preventDefault();
            el.classList.add("receiving");
          }
        };
        el.ondragleave = () => el.classList.remove("receiving");
        el.ondrop = (e) => {
          e.preventDefault();
          e.stopPropagation();
          el.classList.remove("receiving");
          const target = qBy(el.dataset.node),
            source = e.dataTransfer.getData("application/pm-source"),
            ruleKey = e.dataTransfer.getData("application/pm-rule");
          if (target.fixed)
            return S.toast("고정 사양을 조건의 결과로 변경할 수 없습니다.");
          if (ruleKey) {
            const rule = S.copy(process.rules.find((r) => r.key === ruleKey));
            if (!rule.actions.some((a) => a.targetKey === target.key))
              rule.actions.push(newAction(target));
            editRule(rule);
          } else if (source) newRule(source, target.key);
        };
      });
      S.$$("[data-rule-drag]", nodes).forEach((el) => {
        el.ondragstart = (e) => {
          e.stopPropagation();
          e.dataTransfer.setData("application/pm-rule", el.dataset.ruleDrag);
        };
        el.onclick = () =>
          editRule(
            S.copy(process.rules.find((r) => r.key === el.dataset.ruleDrag)),
          );
        el.onmouseenter = () => {
          highlight = el.dataset.ruleDrag;
          draw();
        };
        el.onmouseleave = () => {
          highlight = null;
          draw();
        };
      });
      S.$$("[data-source-drag]", nodes).forEach((el) => {
        el.ondragstart = (e) => {
          e.stopPropagation();
          e.dataTransfer.setData(
            "application/pm-source",
            el.dataset.sourceDrag,
          );
        };
        el.onclick = () => newRule(el.dataset.sourceDrag);
      });
      applyZoom();
      requestAnimationFrame(draw);
    }
    function draw() {
      const dpr = window.devicePixelRatio || 1;
      canvas.width = 1080 * dpr;
      canvas.height = stageHeight * dpr;
      canvas.style.width = "1080px";
      canvas.style.height = stageHeight + "px";
      const ctx = canvas.getContext("2d");
      ctx.scale(dpr, dpr);
      ctx.font = "10px sans-serif";
      for (const rule of process.rules) {
        const sources = [...new Set(rule.conditions.map((c) => c.groupKey))];
        for (const action of rule.actions)
          for (const source of sources) {
            const a = positions.get(source),
              b = positions.get(action.targetKey);
            if (!a || !b) continue;
            const backward =
                process.questions.findIndex((q) => q.key === source) >=
                process.questions.findIndex((q) => q.key === action.targetKey),
              color = backward
                ? "#c44553"
                : highlight === rule.key
                  ? "#2457c5"
                  : "#8e9fb9";
            const start = { x: a.x + 248, y: a.y + 55 },
              end = { x: b.x, y: b.y + 55 };
            ctx.strokeStyle = color;
            ctx.lineWidth = highlight === rule.key ? 3 : 1.3;
            ctx.setLineDash(backward ? [4, 4] : []);
            ctx.beginPath();
            ctx.moveTo(start.x, start.y);
            if (end.x > start.x)
              ctx.bezierCurveTo(
                start.x + 45,
                start.y,
                end.x - 45,
                end.y,
                end.x,
                end.y,
              );
            else
              ctx.bezierCurveTo(
                start.x + 70,
                start.y + 80,
                end.x - 40,
                end.y - 50,
                end.x,
                end.y,
              );
            ctx.stroke();
            ctx.setLineDash([]);
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.moveTo(end.x, end.y);
            ctx.lineTo(end.x - 7, end.y - 4);
            ctx.lineTo(end.x - 7, end.y + 4);
            ctx.fill();
            if (highlight === rule.key) {
              ctx.fillStyle = "#2457c5";
              ctx.fillText(
                {
                  SHOW: "표시",
                  HIDE: "건너뜀",
                  ALLOW: "허용 보기",
                  REQUIRE: "필수",
                  OPTIONAL: "선택",
                }[action.effect],
                end.x + 4,
                end.y - 11,
              );
            }
          }
      }
    }
    function applyZoom() {
      stage.style.transform = `scale(${zoom})`;
      S.$("#pms-canvas-size", root).style.width = 1080 * zoom + "px";
      S.$("#pms-canvas-size", root).style.height = stageHeight * zoom + "px";
      S.$("#pms-zoom", root).textContent = Math.round(zoom * 100) + "%";
    }
    S.$("#pms-zoom-out", root).onclick = () => {
      zoom = Math.max(0.25, zoom - 0.1);
      applyZoom();
    };
    S.$("#pms-zoom-in", root).onclick = () => {
      zoom = Math.min(2, zoom + 0.1);
      applyZoom();
    };
    S.$("#pms-zoom-fit", root).onclick = () => {
      zoom = Math.max(0.25, Math.min(1, (viewport.clientWidth - 20) / 1080));
      applyZoom();
    };
    viewport.addEventListener(
      "wheel",
      (e) => {
        if (e.ctrlKey || e.metaKey) {
          e.preventDefault();
          zoom = Math.max(
            0.25,
            Math.min(2, zoom + (e.deltaY < 0 ? 0.1 : -0.1)),
          );
          applyZoom();
        }
      },
      { passive: false },
    );
    let pan = null;
    viewport.onpointerdown = (e) => {
      if (e.target.closest(".pms-flow-node")) return;
      pan = {
        x: e.clientX,
        y: e.clientY,
        left: viewport.scrollLeft,
        top: viewport.scrollTop,
      };
      viewport.setPointerCapture(e.pointerId);
    };
    viewport.onpointermove = (e) => {
      if (pan) {
        viewport.scrollLeft = pan.left - (e.clientX - pan.x);
        viewport.scrollTop = pan.top - (e.clientY - pan.y);
      }
    };
    viewport.onpointerup = () => (pan = null);
    function paintInspector() {
      const q = qBy(selected);
      if (!q) return;
      const outgoing = rulesFrom(q.key),
        incoming = process.rules.filter((r) =>
          r.actions.some((a) => a.targetKey === q.key),
        );
      inspector.innerHTML = `<h2>${S.e(q.labels.management)}</h2><p class="mono">${S.e(q.key)}</p>${S.badge(S.controls[q.control])}<p>${S.e(q.question || q.labels.customer)}</p>${q.fixed ? '<div class="pms-help">제품에 고정된 사양입니다. 다음 질문에 영향을 주는 조건의 원본으로 사용할 수 있습니다.</div>' : `<div class="pms-section"><div>${S.check("조건이 없을 때 표시", "visible", q.visible, "pms-switch")}</div><div>${S.check("답변 필수", "required", q.required, "pms-switch")}</div><div>${S.check("일치 규칙 필수", "requireRule", q.requireRule, "pms-switch")}</div><small>일치 규칙 필수를 켜면 이 질문에 도달하는 모든 경우에 연결 규칙이 하나 이상 맞아야 합니다.</small></div><div class="pms-section"><strong>${S.isChoice(q.control) ? "등록된 보기" : "입력 필드"}</strong><div class="pms-check-grid">${(S.isChoice(q.control) ? q.choices : q.fields).map((x) => S.badge(x.labels.management)).join("") || '<span class="pms-risk">상세 편집에서 등록해 주세요.</span>'}</div></div>`}<div class="pms-section"><div class="pms-row"><strong>보내는 커스텀 · ${outgoing.length}</strong><button id="pms-new-rule">+</button></div><div class="pms-rule-list">${outgoing.map((r) => `<article class="pms-rule-tile" data-edit-rule="${S.e(r.key)}"><strong>${S.e(r.name)}</strong><p>${S.e(ruleText(r))}</p><small>→ ${r.actions.map((a) => S.e(qBy(a.targetKey)?.labels.management || "대상 없음")).join(", ")}</small></article>`).join("")}</div></div><div class="pms-section"><strong>받는 조건 · ${incoming.length}</strong>${incoming.map((r) => `<p><button data-edit-rule="${S.e(r.key)}">${S.e(r.name)}</button></p>`).join("")}</div>`;
      S.bind(inspector, q, () => {
        changed();
        paintNodes();
      });
      S.$("#pms-new-rule", inspector).onclick = () => newRule(q.key);
      S.$$("[data-edit-rule]", inspector).forEach(
        (b) =>
          (b.onclick = () =>
            editRule(
              S.copy(process.rules.find((r) => r.key === b.dataset.editRule)),
            )),
      );
    }
    S.$("#pms-edit-question", root).onclick = () => editQuestion(qBy(selected));
    function editQuestion(original) {
      if (original.fixed) {
        const g = product.groups.find((x) => x.id === original.groupId);
        S.dialog(
          "고정 제품 사양",
          `<h2>${S.e(original.labels.management)}</h2><div class="pms-help">이 사양은 제품 상세에서 수정합니다. 조건은 + 커스텀을 이용해 연결해 주세요.</div>${S.inputAnswer(original, { choices: original.choices.map((c) => c.key), fields: product.variants.find((v) => v.groupId === original.groupId)?.inputs || {} }, true)}${S.files(g?.assets || [], false)}`,
          { wide: false },
        );
        return;
      }
      let q = S.copy(original);
      const qFiles = q.assetIds.map((id) => files.get(id)).filter(Boolean),
        choiceFiles = new Map(
          q.choices.map((c) => [
            c.key,
            (c.assetIds || []).map((id) => files.get(id)).filter(Boolean),
          ]),
        );
      const dialog = S.dialog("질문 상세 · " + original.labels.management, "");
      const body = S.$(".pms-dialog-body", dialog);
      function paintQuestion() {
        body.innerHTML = `${S.labels(q, "")}<div class="pms-form-grid pms-section">${S.field("고객에게 물어볼 질문", "question", q.question, "text", 'maxlength="300"')}${S.field("선택 도움말", "guide", q.guide, "text", 'maxlength="1000"')}<div class="pms-row">${S.badge(S.controls[q.control], "blue")}</div></div><div class="pms-help">각 항목의 value는 자동 생성되며 이름을 바꾸어도 유지됩니다. 사용 중인 value를 삭제하면 연결된 조건도 함께 수정해야 합니다.</div><div class="pms-row"><h2>${S.isChoice(q.control) ? "선택 보기" : "입력 필드"}</h2><button id="pms-add-question-item">+ ${S.isChoice(q.control) ? "보기" : "필드"}</button></div><div id="pms-question-items">${S.isChoice(q.control) ? q.choices.map((c, i) => `<article class="pms-list-row" draggable="true" data-choice-index="${i}"><div class="pms-row-head"><span class="pms-handle">⠿</span><strong>보기 ${i + 1}</strong><small class="mono">${S.e(c.key)}</small><button data-remove-choice="${i}" class="pms-remove">×</button></div>${S.labels(c, "choices." + i, true, c.namePart)}<details class="pms-section"><summary>보기 이미지·첨부</summary><div data-choice-files="${i}"></div></details></article>`).join("") : S.fieldRows(q.fields, q.control)}</div><section class="pms-section"><h2>이 질문의 이미지·첨부</h2><div id="pms-question-files"></div></section><footer class="pms-actions pms-section"><button class="primary" id="pms-apply-question">질문에 적용</button></footer>`;
        S.bind(body, q);
        const items = S.$("#pms-question-items", body);
        S.$("#pms-add-question-item", body).onclick = () => {
          if (S.isChoice(q.control)) {
            if (q.choices.length >= 200)
              return S.toast("보기는 최대 200개입니다.");
            q.choices.push({
              key: S.key("V"),
              labels: { customer: "", production: "", management: "" },
              namePart: "",
              assetIds: [],
            });
          } else {
            if (q.fields.length >= 20)
              return S.toast("필드는 최대 20개입니다.");
            q.fields.push(S.newField());
          }
          paintQuestion();
        };
        if (S.isChoice(q.control)) {
          S.$$("[data-remove-choice]", body).forEach(
            (b) =>
              (b.onclick = () => {
                q.choices.splice(Number(b.dataset.removeChoice), 1);
                paintQuestion();
              }),
          );
          S.sortable(items, "[data-choice-index]", (from, to) => {
            q.choices.splice(to, 0, q.choices.splice(from, 1)[0]);
            paintQuestion();
          });
          S.$$("[data-choice-files]", body).forEach((el) => {
            const c = q.choices[Number(el.dataset.choiceFiles)];
            if (!choiceFiles.has(c.key)) choiceFiles.set(c.key, []);
            const attachments = choiceFiles.get(c.key);
            el.innerHTML = S.files(attachments);
            S.fileEvents(el, attachments);
          });
        } else S.fieldEvents(items, q.fields, paintQuestion);
        const attachmentBox = S.$("#pms-question-files", body);
        attachmentBox.innerHTML = S.files(qFiles);
        S.fileEvents(attachmentBox, qFiles);
        S.$("#pms-apply-question", body).onclick = () => {
          q.assetIds = qFiles.map((a) => a.id);
          qFiles.forEach((a) => files.set(a.id, a));
          q.choices.forEach((c) => {
            const attachments = choiceFiles.get(c.key) || [];
            c.assetIds = attachments.map((a) => a.id);
            attachments.forEach((a) => files.set(a.id, a));
          });
          process.questions[
            process.questions.findIndex((x) => x.key === q.key)
          ] = q;
          changed();
          dialog.close();
          paint();
        };
      }
      paintQuestion();
    }
    function newCondition(source) {
      const q = qBy(source);
      return {
        groupKey: source,
        fieldKey: S.isChoice(q.control) ? null : q.fields[0]?.key || null,
        operator: S.isChoice(q.control)
          ? "EQ"
          : q.control === "NUMBER"
            ? "RANGE"
            : q.control === "FILE"
              ? "PRESENT"
              : "EQ",
        choiceKeys:
          S.isChoice(q.control) && q.choices[0] ? [q.choices[0].key] : [],
        text: "",
        lower: q.control === "NUMBER" ? (q.fields[0]?.min ?? 0) : null,
        upper: q.control === "NUMBER" ? (q.fields[0]?.max ?? 1000) : null,
        lowerInclusive: true,
        upperInclusive: true,
      };
    }
    function newAction(target) {
      return {
        targetKey: target.key,
        effect:
          S.isChoice(target.control) && target.choices.length
            ? "ALLOW"
            : "SHOW",
        choiceKeys: S.isChoice(target.control)
          ? target.choices.map((c) => c.key)
          : [],
      };
    }
    function newRule(source, target) {
      const q = qBy(source);
      if (!S.isChoice(q.control) && !q.fields.length)
        return S.toast("먼저 원본 질문의 입력 필드를 등록해 주세요.");
      if (S.isChoice(q.control) && !q.choices.length)
        return S.toast("먼저 원본 질문의 보기를 등록해 주세요.");
      const index = process.questions.findIndex((x) => x.key === source),
        targetQuestion = target
          ? qBy(target)
          : process.questions.slice(index + 1).find((x) => !x.fixed);
      if (!targetQuestion)
        return S.toast(
          "이 질문 뒤에 연결할 비규격 질문이 없습니다. 질문 순서를 먼저 조정해 주세요.",
        );
      editRule({
        key: S.key("R"),
        name: q.labels.management + " 커스텀 " + (rulesFrom(source).length + 1),
        match: "ALL",
        conditions: [newCondition(source)],
        actions: [newAction(targetQuestion)],
      });
    }
    function editRule(initial) {
      let rule = S.copy(initial);
      const existing = process.rules.some((r) => r.key === rule.key);
      const dialog = S.dialog("커스텀 조건 연결", "");
      const body = S.$(".pms-dialog-body", dialog);
      const effects = {
        SHOW: "그룹 표시",
        HIDE: "그룹 건너뜀",
        ALLOW: "허용 보기 제한",
        REQUIRE: "필수로 변경",
        OPTIONAL: "선택으로 변경",
      };
      function paintRule() {
        body.innerHTML = `<div class="pms-form-grid">${S.field("커스텀 이름", "name", rule.name, "text", 'maxlength="120"')}${S.select("조건 조합", "match", rule.match, { ALL: "모두 만족 · AND", ANY: "하나 이상 만족 · OR" })}<div class="pms-row">${existing ? '<button class="danger" id="pms-delete-rule">삭제</button><button id="pms-clone-rule">복제</button>' : ""}</div></div><div class="pms-rule-editor pms-section"><section><div class="pms-row"><h2>이 조건일 때</h2><button id="pms-add-condition">+ 조건</button></div>${rule.conditions.map((c, i) => conditionHtml(c, i)).join("")}</section><section><div class="pms-row"><h2>이렇게 처리합니다</h2><button id="pms-add-action">+ 결과</button></div>${rule.actions.map((a, i) => actionHtml(a, i)).join("")}</section></div><div class="pms-help">숫자 구간의 ‘이상/초과, 이하/미만’을 구분해 경계 중복을 없애세요. 여러 허용 보기 규칙이 함께 일치하면 공통으로 허용하는 보기만 남습니다. 표시와 건너뜀이 함께 일치하는 경우는 검증 오류입니다.</div><div class="pms-name-preview" id="pms-rule-sentence"></div><div class="pms-actions pms-section"><button class="primary" id="pms-apply-rule">조건 연결 적용</button></div>`;
        const sentence = () =>
          (S.$("#pms-rule-sentence", body).textContent =
            ruleText(rule) +
            " → " +
            rule.actions
              .map(
                (a) =>
                  (qBy(a.targetKey)?.labels.management || "?") +
                  " " +
                  effects[a.effect],
              )
              .join(" / "));
        S.bind(body, rule, (path, value) => {
          const match = path.match(
            /^conditions\.(\d+)\.(groupKey|fieldKey|operator)$/,
          );
          if (match) {
            const i = Number(match[1]);
            if (match[2] === "groupKey")
              rule.conditions[i] = newCondition(value);
            if (match[2] === "fieldKey") {
              const q = qBy(rule.conditions[i].groupKey),
                f = q.fields.find((f) => f.key === value);
              rule.conditions[i].lower =
                q.control === "NUMBER" ? (f?.min ?? 0) : null;
              rule.conditions[i].upper =
                q.control === "NUMBER" ? (f?.max ?? 1000) : null;
            }
            paintRule();
            return;
          }
          const actionMatch = path.match(
            /^actions\.(\d+)\.(targetKey|effect)$/,
          );
          if (actionMatch) {
            const a = rule.actions[Number(actionMatch[1])];
            if (actionMatch[2] === "targetKey") {
              a.choiceKeys = [];
            }
            paintRule();
            return;
          }
          sentence();
        });
        sentence();
        S.$$("[data-condition-option]", body).forEach(
          (input) =>
            (input.onchange = () => {
              const i = Number(input.dataset.conditionOption);
              rule.conditions[i].choiceKeys = S.$$(
                `[data-condition-option="${i}"]:checked`,
                body,
              ).map((x) => x.value);
              sentence();
            }),
        );
        S.$$("[data-action-option]", body).forEach(
          (input) =>
            (input.onchange = () => {
              const i = Number(input.dataset.actionOption);
              rule.actions[i].choiceKeys = S.$$(
                `[data-action-option="${i}"]:checked`,
                body,
              ).map((x) => x.value);
            }),
        );
        S.$$("[data-remove-condition]", body).forEach(
          (b) =>
            (b.onclick = () => {
              rule.conditions.splice(Number(b.dataset.removeCondition), 1);
              paintRule();
            }),
        );
        S.$$("[data-remove-action]", body).forEach(
          (b) =>
            (b.onclick = () => {
              rule.actions.splice(Number(b.dataset.removeAction), 1);
              paintRule();
            }),
        );
        S.$("#pms-add-condition", body).onclick = () => {
          rule.conditions.push(
            newCondition(
              rule.conditions[0]?.groupKey || process.questions[0].key,
            ),
          );
          paintRule();
        };
        S.$("#pms-add-action", body).onclick = () => {
          const target = process.questions.find((q) => !q.fixed);
          if (target) rule.actions.push(newAction(target));
          paintRule();
        };
        if (existing) {
          S.$("#pms-delete-rule", body).onclick = async () => {
            if (
              !(await S.confirm(
                "이 커스텀 조건과 모든 연결을 삭제하시겠습니까?",
              ))
            )
              return;
            process.rules = process.rules.filter((r) => r.key !== rule.key);
            changed();
            dialog.close();
            paint();
          };
          S.$("#pms-clone-rule", body).onclick = () => {
            dialog.close();
            rule.key = S.key("R");
            rule.name += " 복사";
            editRule(rule);
          };
        }
        S.$("#pms-apply-rule", body).onclick = () => {
          if (
            !rule.name.trim() ||
            !rule.conditions.length ||
            !rule.actions.length
          )
            return S.toast("규칙명·조건·결과를 입력해 주세요.");
          let latest = -1;
          for (const c of rule.conditions)
            latest = Math.max(
              latest,
              process.questions.findIndex((q) => q.key === c.groupKey),
            );
          if (
            rule.actions.some(
              (a) =>
                process.questions.findIndex((q) => q.key === a.targetKey) <=
                  latest || qBy(a.targetKey)?.fixed,
            )
          )
            return S.toast(
              "결과 질문은 모든 원본 질문보다 뒤에 있어야 합니다.",
            );
          const index = process.rules.findIndex((r) => r.key === rule.key);
          if (index < 0) process.rules.push(rule);
          else process.rules[index] = rule;
          changed();
          dialog.close();
          paint();
        };
      }
      function conditionHtml(c, i) {
        const q = qBy(c.groupKey),
          choice = S.isChoice(q?.control),
          numeric = q?.control === "NUMBER",
          file = q?.control === "FILE",
          base = "conditions." + i;
        const ops =
          choice || (!numeric && !file)
            ? {
                EQ: "같음 / 선택한 보기 포함",
                NE: "다름 / 선택한 보기 제외",
                PRESENT: "값 있음",
                ABSENT: "값 없음",
              }
            : file
              ? { PRESENT: "파일 있음", ABSENT: "파일 없음" }
              : {
                  RANGE: "구간",
                  EQ: "같음",
                  NE: "다름",
                  GT: "초과",
                  GE: "이상",
                  LT: "미만",
                  LE: "이하",
                  PRESENT: "값 있음",
                  ABSENT: "값 없음",
                };
        return `<article class="pms-cond"><div class="pms-row"><strong>조건 ${i + 1}</strong><button class="pms-remove" data-remove-condition="${i}">×</button></div><div class="pms-form-grid two">${S.select("원본 질문", base + ".groupKey", c.groupKey, Object.fromEntries(process.questions.map((q) => [q.key, q.labels.management])))}${!choice ? S.select("입력 필드", base + ".fieldKey", c.fieldKey, Object.fromEntries((q?.fields || []).map((f) => [f.key, f.labels.management]))) : ""}${S.select("조건", base + ".operator", c.operator, ops)}${!["PRESENT", "ABSENT"].includes(c.operator) && numeric ? `${S.field(c.operator === "RANGE" ? "시작" : "비교값", base + ".lower", c.lower, "number", 'step="0.001"')}${c.operator === "RANGE" ? S.field("끝", base + ".upper", c.upper, "number", 'step="0.001"') + S.check("시작 포함 (이상)", base + ".lowerInclusive", c.lowerInclusive) + S.check("끝 포함 (이하)", base + ".upperInclusive", c.upperInclusive) : ""}` : !choice && !file && !["PRESENT", "ABSENT"].includes(c.operator) ? S.field("비교 문자", base + ".text", c.text) : ""}</div>${choice && !["PRESENT", "ABSENT"].includes(c.operator) ? `<div class="pms-check-grid">${(q?.choices || []).map((o) => `<label class="pms-check"><input type="checkbox" data-condition-option="${i}" value="${S.e(o.key)}" ${(c.choiceKeys || []).includes(o.key) ? "checked" : ""}>${S.e(o.labels.management)}</label>`).join("")}</div>` : ""}</article>`;
      }
      function actionHtml(a, i) {
        const q = qBy(a.targetKey),
          base = "actions." + i;
        return `<article class="pms-effect"><div class="pms-row"><strong>결과 ${i + 1}</strong><button class="pms-remove" data-remove-action="${i}">×</button></div><div class="pms-form-grid two">${S.select("대상 질문", base + ".targetKey", a.targetKey, Object.fromEntries(process.questions.filter((q) => !q.fixed).map((q) => [q.key, q.labels.management])))}${S.select("실행", base + ".effect", a.effect, effects)}</div>${a.effect === "ALLOW" ? `<div class="pms-check-grid">${(q?.choices || []).map((o) => `<label class="pms-check"><input type="checkbox" data-action-option="${i}" value="${S.e(o.key)}" ${(a.choiceKeys || []).includes(o.key) ? "checked" : ""}>${S.e(o.labels.management)}</label>`).join("") || '<small class="pms-risk">선택형 질문의 보기를 먼저 등록해 주세요.</small>'}</div>` : ""}</article>`;
      }
      paintRule();
    }
    async function validate(show = true) {
      const result = await S.request(
        `/products/${product.id}/validate`,
        "POST",
        process,
      );
      lastValidation = result;
      const errors = result.issues.filter((i) => i.severity === "ERROR"),
        warnings = result.issues.filter((i) => i.severity === "WARNING");
      S.$("#pms-process-health", root).innerHTML =
        `${S.badge(result.valid ? "검증 통과" : "확인 필요", result.valid ? "green" : "red")}${S.badge(result.exhaustive ? "조건 경계 검증 완료" : "검증 범위 미완료", result.exhaustive ? "blue" : "amber")}<span>검사 경로 ${result.scenarios.toLocaleString()} · 오류 ${errors.length} · 안내 ${warnings.length}</span>`;
      if (show) {
        const dialog = S.dialog(
          "프로세스 검증 결과",
          `<div class="pms-help">숫자 범위는 입력 간격과 조건 경계별 대표값을 검사하고 선택형은 가능한 선택 상태를 검사합니다. 조건에 참조되지 않는 값은 같은 동작을 만드는 대표값으로 묶습니다.</div><p><strong>${result.valid ? "등록완료 가능한 상태입니다." : "표시된 오류를 수정해 주세요."}</strong></p>${result.issues.length ? `<table><thead><tr><th>구분</th><th>위치</th><th>내용</th></tr></thead><tbody>${result.issues.map((i) => `<tr><td>${S.badge(i.severity === "ERROR" ? "오류" : "안내", i.severity === "ERROR" ? "red" : "amber")}</td><td>${S.e(i.location)}</td><td>${S.e(i.message)}</td></tr>`).join("")}</tbody></table>` : '<div class="pms-alert ok">누락·충돌·잘못된 연결이 발견되지 않았습니다.</div>'}`,
        );
      }
      return result;
    }
    S.$("#pms-validate-process", root).onclick = (e) =>
      S.run(e.currentTarget, () => validate());
    async function save(publish) {
      if (publish) {
        const result = await validate(false);
        if (!result.valid || !result.exhaustive) {
          await validate(true);
          return;
        }
      }
      product = await S.request(`/products/${product.id}/process`, "PUT", {
        version: product.version,
        process,
        publish,
      });
      process = S.copy(product.process);
      const statusBadge = S.$(".pms-toolbar .pms-tag", root);
      if (statusBadge)
        statusBadge.outerHTML = S.badge(
          product.registrationStatus,
          product.status === "DRAFT" ? "amber" : "green",
        );
      S.dirty = false;
      S.notice(
        publish
          ? "프로세스를 등록완료했습니다. 재고에 따라 사용중 또는 재고없음으로 표시됩니다."
          : "프로세스를 임시저장했습니다. 현재 제품은 등록중 상태입니다.",
        true,
      );
      paint();
    }
    S.$("#pms-draft-process", root).onclick = (e) =>
      S.run(e.currentTarget, () => save(false));
    S.$("#pms-publish-process", root).onclick = (e) =>
      S.run(e.currentTarget, () => save(true));
    S.$("#pms-preview-process", root).onclick = (e) =>
      S.run(e.currentTarget, () => previewChat());
    async function previewChat() {
      const dialog = S.dialog(
        "실제 선택 미리보기",
        '<div class="pms-progress"></div>',
        { wide: false },
      );
      const body = S.$(".pms-dialog-body", dialog);
      let answers = {},
        evaluation = null,
        cursor = 0,
        history = [];
      async function evaluate() {
        evaluation = await S.request(
          `/products/${product.id}/evaluate`,
          "POST",
          { process, answers },
        );
        for (const state of evaluation.questions) {
          if (state.visible) {
            answers[state.question.key] = state.answer;
          } else delete answers[state.question.key];
        }
        paintChat();
      }
      function paintChat() {
        const visible = evaluation.questions.filter((s) => s.visible);
        if (cursor >= visible.length) cursor = visible.length;
        body.innerHTML = `<p><small>고정 사양은 자동 적용됩니다. 앞선 답변을 수정하면 뒤의 허용 보기와 표시 질문을 다시 계산합니다.</small></p><div class="pms-mini-chat">${visible
          .slice(0, cursor)
          .map(
            (s, i) =>
              `<article class="pms-bubble answer"><strong>${S.e(s.question.labels.customer)}</strong><p>${S.e(answerText(s))}</p><button data-chat-back="${i}">수정</button></article>`,
          )
          .join(
            "",
          )}${cursor < visible.length ? chatQuestion(visible[cursor]) : `<div class="pms-alert ${evaluation.complete ? "ok" : ""}">${evaluation.complete ? "모든 필수 답변이 유효합니다." : "입력하지 않았거나 유효하지 않은 답변이 있습니다."}</div>${!evaluation.complete ? evaluation.questions.flatMap((s) => s.errors.map((e) => `<p class="pms-risk">${S.e(s.question.labels.customer + ": " + e)}</p>`)).join("") : ""}`}</div><div class="pms-section"><small>건너뛴 질문: ${
          evaluation.questions
            .filter((s) => !s.visible)
            .map((s) => S.e(s.question.labels.customer))
            .join(", ") || "없음"
        }</small></div>`;
        S.$$("[data-chat-back]", body).forEach(
          (b) =>
            (b.onclick = () => {
              cursor = Number(b.dataset.chatBack);
              paintChat();
            }),
        );
        const state = visible[cursor];
        if (!state) return;
        const inputBox = S.$("#pms-chat-input", body);
        S.$$("input[type=file]", inputBox).forEach(
          (input) =>
            (input.onchange = () =>
              S.run(null, async () => {
                const fd = new FormData();
                for (const f of input.files) fd.append("files", f);
                const uploaded = await S.request("/assets/stage", "POST", fd);
                answers[state.question.key] ??= { choices: [], fields: {} };
                answers[state.question.key].fields[input.dataset.answerField] =
                  uploaded.map((x) => x.id);
                S.$(
                  `[data-answer-files="${input.dataset.answerField}"]`,
                  inputBox,
                ).textContent = uploaded.length + "개 파일 업로드됨";
              })),
        );
        S.$("#pms-chat-next", body).onclick = (e) =>
          S.run(e.currentTarget, async () => {
            answers[state.question.key] = state.question.fixed
              ? state.answer
              : S.readAnswer(
                  inputBox,
                  state.question,
                  answers[state.question.key],
                );
            const result = await S.request(
              `/products/${product.id}/evaluate`,
              "POST",
              { process, answers },
            );
            const fresh = result.questions.find(
              (x) => x.question.key === state.question.key,
            );
            evaluation = result;
            for (const s of result.questions) {
              if (s.visible) answers[s.question.key] = s.answer;
              else delete answers[s.question.key];
            }
            if (!fresh.errors.length) cursor++;
            paintChat();
          });
      }
      function answerText(state) {
        const a = state.answer;
        if (S.isChoice(state.question.control))
          return a.choices
            .map(
              (k) =>
                state.question.choices.find((c) => c.key === k)?.labels
                  .customer || k,
            )
            .join(", ");
        return state.question.fields
          .filter((f) => a.fields[f.key] !== undefined)
          .map(
            (f) =>
              f.labels.customer +
              ": " +
              (Array.isArray(a.fields[f.key])
                ? a.fields[f.key].length + "개 파일"
                : a.fields[f.key]),
          )
          .join(" / ");
      }
      function chatQuestion(state) {
        const q = {
            ...state.question,
            choices: state.question.choices.filter((c) =>
              state.allowed.includes(c.key),
            ),
          },
          related = [
            ...(product.groups.find((g) => g.id === q.groupId)?.assets || []),
            ...q.assetIds.map((id) => files.get(id)).filter(Boolean),
          ];
        return `<article class="pms-bubble"><strong>${S.e(q.question || q.labels.customer + "를 입력해 주세요.")}</strong>${q.guide ? `<p>${S.e(q.guide)}</p>` : ""}${S.files(related, false)}<div id="pms-chat-input">${S.inputAnswer(q, state.answer, q.fixed)}</div>${state.errors.map((e) => `<p class="pms-risk">${S.e(e)}</p>`).join("")}<button class="primary" id="pms-chat-next">${q.fixed ? "확인 · 다음" : "다음"}</button></article>`;
      }
      try {
        await evaluate();
      } catch (e) {
        body.innerHTML = `<div class="pms-alert">${S.e(e.message)}</div><p>빈 질문의 보기·필드를 먼저 등록한 후 미리보기를 실행해 주세요.</p>`;
      }
    }
    paint();
    S.dirty = false;
  };
})(window.PMS);
