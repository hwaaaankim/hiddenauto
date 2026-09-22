(function (S) {
  "use strict";
  const esc = S.e,
    path = (id) => "/admin/product-master/products/" + id;
  S.copyProductDialog = async function (product) {
    const p = await S.request("/products/" + product.id);
    const m = {
      productName: p.productName + " 복사",
      fixed: S.copy(p.variants.filter((v) => S.isBase(S.group(v.groupId)))),
    };
    const d = S.dialog(
      "비규격 제품 복사",
      `<p class="pms-help">제품명과 필수 분류만 바꿉니다. 질문·옵션·범위·관계·이미지·FAQ 연결을 독립적으로 복제합니다. 실 제품과 재고는 복사하지 않습니다.</p>${S.field("새 제품명", "productName", m.productName, "text", 'maxlength="160"')}${m.fixed
        .map((v, i) => {
          const g = S.group(v.groupId);
          return S.select(
            g.labels.management,
            "fixed." + i + ".valueId",
            v.valueIds[0],
            Object.fromEntries(
              g.values
                .filter((v) => v.active)
                .map((v) => [v.id, v.labels.management]),
            ),
          );
        })
        .join("")}`,
      { foot: '<button data-copy class="primary">제품 복사</button>' },
    );
    m.fixed.forEach((v) => (v.valueId = v.valueIds[0]));
    S.bind(d, m);
    S.$("[data-copy]", d).onclick = (e) =>
      S.run(e.currentTarget, async () => {
        const saved = await S.request("/products/" + p.id + "/copy", "POST", {
          productName: m.productName,
          fixed: m.fixed.map((v) => ({
            groupId: v.groupId,
            valueIds: [Number(v.valueId)],
            inputs: {},
          })),
        });
        S.dirty = false;
        location.href = path(saved.id);
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
        body.innerHTML = `<div class="pms-form-grid two">${S.field("주제명", "title", m.title, "text", 'maxlength="160"')}${S.field("문의 전화번호", "phone", m.phone || "", "tel", 'maxlength="60"')}<div class="wide">${S.field("별도 문의 링크", "link", m.link || "", "url", 'maxlength="2000"')}</div></div><p class="pms-help">연결된 제품에서 ? 버튼으로 이 주제의 FAQ가 표시됩니다. 문의 링크를 누르면 현재 선택값을 콘솔에 출력한 뒤 이동합니다.</p><div class="pms-actions"><button data-add>+ FAQ 추가</button></div>${m.entries.map((r, i) => `<article class="pms-card pm-faq-entry"><header class="pm-card-heading"><h3>FAQ ${i + 1}</h3><button data-remove="${i}" class="danger">삭제</button></header><div class="pms-form-grid one">${S.field("FAQ 제목", "entries." + i + ".title", r.title, "text", 'maxlength="200"')}<label><span>내용</span><textarea data-path="entries.${i}.content" maxlength="10000" rows="5">${esc(r.content)}</textarea></label></div><div data-faq-files="${i}">${S.files((r.assetIds || []).map((id) => assets.get(id)).filter(Boolean))}</div></article>`).join("")}`;
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
  };
  S.viewPage = async function (root, p) {
    const topics = await S.request("/faq"),
      faq = topics.find((t) => t.id === p.faqTopicId);
    root.innerHTML = `<section class="pms-panel"><div class="pms-panel-title"><div><h2>${esc(p.productName)}</h2><span class="mono">${esc(p.catalogCode)}</span> ${S.badge(p.nonStandard ? "비규격" : "규격")}</div><div class="pms-actions"><a class="pms-button" href="${path(p.id)}">구성 수정</a>${p.nonStandard ? `<a class="pms-button" href="${path(p.id)}/process">프로세스</a><a class="pms-button" href="${path(p.id)}/actuals">실 제품·재고</a>` : ""}<a class="pms-button" href="${path(p.id)}/test" target="_blank">고객 테스트</a></div></div><div class="pms-panel-body"><p>${esc(p.description || "")}</p><div class="pm-detail-metrics"><span>생산기간 ${p.productionHours ?? 0}시간</span>${!p.nonStandard ? `<span>단가 ${(p.unitPrice ?? 0).toLocaleString()}원</span>` : ""}<span>총 재고 ${p.stock}</span>${p.nonStandard ? `<span>실 제품 ${p.actualCount}종</span>` : ""}<span>FAQ ${esc(faq?.title || "연결 없음")}</span></div><div class="pms-gallery">${p.assets
      .filter((a) => a.image)
      .map(
        (a) =>
          `<a href="${esc(a.url)}" target="_blank"><img src="${esc(a.url)}" alt="${esc(a.name)}"></a>`,
      )
      .join("")}</div>${p.process.questions
      .map((q) => {
        const g = S.group(q.groupId);
        return `<article class="pms-card"><h3>${esc(q.labels.management)}</h3><p>${esc(q.question || "")} · ${esc(S.controls[q.control])} · ${g.askQuestion ? "고객 질문 포함" : "질문 제외 / 고정 사양"}${g.priceImpact ? " · 단가 영향" : ""}</p>${q.guide ? `<p>${esc(q.guide)}</p>` : ""}<div class="pms-table-scroll pm-spec-table"><table><thead><tr><th>항목</th><th>내부 value</th><th>사양·입력 제한</th><th>안내메시지</th></tr></thead><tbody>${q.choices
          .map(
            (c) =>
              `<tr><td>${esc(c.labels.customer)}<small>생산: ${esc(c.labels.production)} / 관리: ${esc(c.labels.management)}</small></td><td>${esc(c.key)}</td><td>${(
                c.assetIds || []
              )
                .map((id) => {
                  const a =
                    p.processAssets.find((a) => a.id === id) ||
                    g.values.flatMap((v) => v.assets).find((a) => a.id === id);
                  return a
                    ? `<a href="${esc(a.url)}" target="_blank">${esc(a.name)}</a>`
                    : "";
                })
                .join(" ")}</td><td>${esc(c.guide || "")}</td></tr>`,
          )
          .join(
            "",
          )}${q.fields.map((f) => `<tr><td>${esc(f.labels.management)}</td><td>${esc(f.key)}</td><td>${q.control === "NUMBER" ? `${f.min ?? "제한 없음"} ~ ${f.max ?? "제한 없음"} ${esc(f.unit || "")} / 간격 ${f.step}` : q.control === "FILE" ? esc((f.extensions || []).join(", ")) + " / 최대 " + f.maxFiles + "개" : `${f.minLength ?? 0} ~ ${f.maxLength ?? 500}자`} ${f.required ? "필수" : ""}</td><td>${esc(f.guide || "")}</td></tr>`).join("")}</tbody></table></div>${(q.numberCases || []).map((r) => `<p><strong>${esc(r.name)}</strong>: ${r.conditions.map((c) => esc(q.fields.find((f) => f.key === c.fieldKey)?.labels.management) + " " + esc(c.operator) + " " + esc(c.lower) + (c.upper != null ? " ~ " + esc(c.upper) : "")).join(" AND ")} ${esc(r.guide || "")}</p>`).join("")}</article>`;
      })
      .join(
        "",
      )}<section class="pms-card"><h3>등록된 연관관계</h3>${p.process.rules.map((r) => `<p>${esc(r.name)} · ${r.actions.map((a) => esc(p.process.questions.find((q) => q.key === a.targetKey)?.labels.management) + " " + esc(a.effect)).join(", ")}</p>`).join("") || "<p>기본 순서로 진행합니다.</p>"}</section></div></section>`;
  };
  S.actualsPage = async function (root, p) {
    let rows = [];
    async function load() {
      rows = await S.request("/products/" + p.id + "/actuals");
      p = await S.request("/products/" + p.id);
      paint();
    }
    function paint() {
      root.innerHTML = `<section class="pms-panel"><div class="pms-panel-title"><div><h2>${esc(p.productName)} · 실 제품 재고</h2><small>${esc(p.catalogCode)} · ${rows.length}종 / 총 재고 ${p.stock}개${p.unallocatedStock ? " / 기존 미배정 재고 " + p.unallocatedStock + "개" : ""}</small></div><button data-create class="primary" ${p.status === "DRAFT" || p.status === "DISCONTINUED" ? "disabled" : ""}>+ 실제 사양 등록</button></div><div class="pms-panel-body"><p class="pms-help">검증 완료된 제품의 허용 범위 안에서 실제 사양을 선택합니다. 실 제품이 하나라도 등록되면 원본 제품의 구성·프로세스는 잠깁니다.</p><div class="pm-code-search"><label><span>제품 코드 조회</span><input data-code placeholder="제품 코드 또는 실 제품 코드"></label><button data-decode>코드 조회</button></div><div data-decoded></div><div class="pms-table-scroll"><table><thead><tr><th>실 제품 코드</th><th>실제 사양</th><th>재고</th><th>관리</th></tr></thead><tbody>${rows.map((r) => `<tr><td>${esc(r.code)}<small>소속: ${esc(r.parentCode)}</small></td><td>${S.specSummary(r.specs.map((s) => ({ label: s.group, value: s.value })))}</td><td>${r.stock}</td><td><div class="pms-actions pm-stock-actions"><button data-stock="${r.id}">입출고·조정</button><button data-history="${r.id}">이력</button></div></td></tr>`).join("") || '<tr><td colspan="4">등록된 실 제품이 없습니다.</td></tr>'}</tbody></table></div></div></section>`;
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
      const d = S.dialog("실제 사양 등록", ""),
        body = S.$(".pms-dialog-body", d);
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
            '</div><div class="pms-actions"><button data-back>선택 다시하기</button><button data-save class="primary">실 제품 등록</button></div>';
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
        body.innerHTML = `<p>${visited.length + 1} / ${states.length}</p><h3>${esc(q.question || q.labels.management)}</h3><div data-answer>${S.inputAnswer(q, state.answer)}</div><div data-error></div><div class="pms-actions"><button data-back ${visited.length ? "" : "disabled"}>이전</button><button data-next class="primary">다음</button></div>`;
        const box = S.$("[data-answer]", body);
        S.$$("input[type=file]", box).forEach(
          (input) =>
            (input.onchange = () =>
              S.run(null, async () => {
                const fd = new FormData();
                for (const f of input.files) fd.append("files", f);
                const fs = await S.request("/assets/stage", "POST", fd);
                answers[q.key] ??= { choices: [], fields: {} };
                answers[q.key].fields[input.dataset.answerField] = fs.map(
                  (f) => f.id,
                );
                S.$(
                  '[data-answer-files="' + input.dataset.answerField + '"]',
                  box,
                ).textContent = fs.map((f) => f.name).join(", ");
              })),
        );
        S.$("[data-back]", body).onclick = () => {
          visited.pop();
          draw();
        };
        S.$("[data-next]", body).onclick = (e) =>
          S.run(e.currentTarget, async () => {
            answers[q.key] = S.readAnswer(box, q, answers[q.key]);
            const next = await S.request(
              "/products/" + p.id + "/evaluate",
              "POST",
              { answers },
            );
            const fresh = next.questions.find((s) => s.question.key === q.key);
            if (fresh.errors.length) throw Error(fresh.errors.join(" / "));
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
