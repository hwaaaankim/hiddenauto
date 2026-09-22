(async function (S) {
  "use strict";
  const root = document.getElementById("pm-public-chat");
  if (!root) return;
  const content = S.$("#pm-chat-content", root),
    heading = S.$("#pm-chat-title", root);
  const adminId = root.dataset.adminProductId || "";
  let kind = new URLSearchParams(location.search).get("kind") || "standard",
    faq = null;
  let token = root.dataset.token || "",
    catalogPage = 0,
    picks = {},
    catalogHistory = [],
    answers = {},
    schema = null,
    evaluation = null,
    historyKeys = [],
    currentKey = null,
    attempted = new Set(),
    assetMap = new Map();
  function gallery(files) {
    const images = files.filter((f) => f.image),
      docs = files.filter((f) => !f.image);
    return `${images.length ? `<div class="pms-row"><small>이미지 ${images.length}장 · 좌우로 넘기거나 눌러 다운로드</small></div><div class="pms-gallery">${images.map((f) => `<a href="${S.e(f.url)}?download=true" download="${S.e(f.name)}"><img src="${S.e(f.url)}" alt="${S.e(f.name)}" loading="lazy"></a>`).join("")}</div>` : ""}${docs.length ? `<div class="pms-attachments">${docs.map((f) => `<a href="${S.e(f.url)}?download=true">▤ 첨부파일 · ${S.e(f.name)}</a>`).join("")}</div>` : ""}`;
  }
  async function guarded(fn) {
    try {
      await fn();
    } catch (e) {
      const error = document.createElement("p");
      error.className = "pms-error";
      error.textContent = e.message;
      content.append(error);
    }
  }
  S.$("#pm-chat-restart", root).onclick = () => {
    if (adminId) {
      answers = {};
      historyKeys = [];
      attempted.clear();
      currentKey = null;
      guarded(loadProduct);
      return;
    }
    token = "";
    faq = null;
    document.getElementById("pm-faq-button")?.remove();
    catalogPage = 0;
    picks = {};
    answers = {};
    catalogHistory = [];
    historyKeys = [];
    currentKey = null;
    attempted.clear();
    schema = null;
    guarded(catalog);
  };
  async function catalog() {
    document.getElementById("pm-kind-picker")?.remove();
    const picker = document.createElement("div");
    picker.id = "pm-kind-picker";
    picker.className = "pms-actions";
    picker.innerHTML = ["standard", "custom"]
      .map(
        (k) =>
          `<button data-kind="${k}" class="${k === kind ? "primary" : ""}">${k === "custom" ? "비규격" : "규격"} 제품 테스트</button>`,
      )
      .join("");
    heading.before(picker);
    picker.querySelectorAll("button").forEach(
      (b) =>
        (b.onclick = () => {
          kind = b.dataset.kind;
          picks = {};
          catalogHistory = [];
          catalogPage = 0;
          guarded(catalog);
        }),
    );
    const result = await S.api(
      "/product-spec/studio/catalog?page=" +
        catalogPage +
        "&nonStandard=" +
        (kind === "custom"),
      "POST",
      picks,
    );
    heading.textContent = "제품의 구성부터 차례로 선택해 주세요.";
    content.innerHTML = catalogHistory
      .map(
        (h, i) =>
          `<article class="pms-bubble answer"><div class="pms-chat-head"><strong>${S.e(h.label)}</strong><button data-catalog-back="${i}">수정</button></div><p>${S.e(h.answer)}</p>${h.guide ? `<p class="pm-answer-guide">${S.e(h.guide)}</p>` : ""}</article>`,
      )
      .join("");
    if (result.next) {
      const step = result.next;
      content.innerHTML += `<article class="pms-bubble"><strong>${S.e(step.label)}를 선택해 주세요.</strong>${gallery(step.assets)}<div class="pms-check-grid">${step.options.map((o) => `<button data-catalog-choice="${S.e(o.key)}">${S.e(o.label)}</button>`).join("")}</div><div id="pm-option-preview"></div><small>선택 가능한 제품 ${result.total.toLocaleString()}개</small></article>`;
      S.$$("[data-catalog-choice]", content).forEach((button) => {
        const option = step.options.find(
          (o) => o.key === button.dataset.catalogChoice,
        );
        button.onmouseenter = () => {
          S.$("#pm-option-preview", content).innerHTML = gallery(option.assets);
        };
        button.onfocus = button.onmouseenter;
        button.onclick = () =>
          guarded(async () => {
            catalogPage = 0;
            picks[step.groupId] = option.key;
            catalogHistory.push({
              id: step.groupId,
              label: step.label,
              answer: option.label,
              guide: option.guide,
            });
            await catalog();
          });
      });
    } else {
      content.innerHTML += `<article class="pms-bubble"><strong>${result.total ? "제품을 선택해 주세요." : "선택 조건에 맞는 제품이 없습니다."}</strong><div class="pms-public-products">${result.products.map((p) => `<div class="pms-public-product"><strong>${S.e(p.productName)}</strong>${S.badge(p.nonStandard ? "비규격" : "규격", "blue")}${S.badge(S.status[p.status])}<p class="mono">${S.e(p.catalogCode)}</p><button class="primary" data-product-token="${S.e(p.token)}">${p.nonStandard ? "상세 구성 시작" : "제품 사양 확인"}</button></div>`).join("")}</div>${result.total > 200 ? S.pager(result.page, result.totalPages, "catalog-page") : ""}</article>`;
      S.$$("[data-product-token]", content).forEach(
        (b) =>
          (b.onclick = () =>
            guarded(async () => {
              token = b.dataset.productToken;
              answers = {};
              historyKeys = [];
              attempted.clear();
              await loadProduct();
            })),
      );
    }
    S.$$("[data-catalog-page]", content).forEach(
      (b) =>
        (b.onclick = () =>
          guarded(async () => {
            catalogPage = Number(b.dataset.catalogPage);
            await catalog();
          })),
    );
    S.$$("[data-catalog-back]", content).forEach(
      (b) =>
        (b.onclick = () =>
          guarded(async () => {
            const i = Number(b.dataset.catalogBack);
            for (const h of catalogHistory.slice(i)) delete picks[h.id];
            catalogPage = 0;
            catalogHistory = catalogHistory.slice(0, i);
            await catalog();
          })),
    );
  }
  async function loadProduct() {
    schema = await S.api(
      adminId
        ? `/admin/api/product-master/studio/products/${adminId}/preview-schema`
        : `/product-spec/studio/${encodeURIComponent(token)}/schema`,
    );
    document.getElementById("pm-kind-picker")?.remove();
    faq = adminId
      ? (await S.request("/faq")).find((t) => t.id === schema.faqTopicId)
      : await S.api(`/product-spec/studio/${encodeURIComponent(token)}/faq`);
    installFaq();
    assetMap = new Map(schema.assets.map((a) => [a.id, a]));
    heading.textContent = schema.productName;
    await evaluate();
  }
  async function evaluate() {
    evaluation = await S.api(
      adminId
        ? `/admin/api/product-master/studio/products/${adminId}/evaluate`
        : `/product-spec/studio/${encodeURIComponent(token)}/evaluate`,
      "POST",
      adminId ? { answers } : answers,
    );
    merge();
    paint();
  }
  function merge() {
    for (const state of evaluation.questions) {
      if (state.visible || state.question.fixed)
        answers[state.question.key] = state.answer;
      else delete answers[state.question.key];
    }
    const visible = new Set(
      evaluation.questions
        .filter((s) => s.visible && !s.question.fixed)
        .map((s) => s.question.key),
    );
    historyKeys = historyKeys.filter(
      (k) =>
        visible.has(k) &&
        evaluation.questions.find((s) => s.question.key === k).errors.length ===
          0,
    );
    currentKey =
      evaluation.questions.find(
        (s) =>
          s.visible &&
          !s.question.fixed &&
          !historyKeys.includes(s.question.key),
      )?.question.key || null;
  }
  function answerText(state) {
    const q = state.question,
      a = state.answer;
    if (S.isChoice(q.control))
      return (
        a.choices
          .map((k) => q.choices.find((c) => c.key === k)?.labels.customer || k)
          .join(", ") || "선택 안 함"
      );
    return (
      q.fields
        .filter((f) => a.fields[f.key] !== undefined)
        .map(
          (f) =>
            f.labels.customer +
            ": " +
            (Array.isArray(a.fields[f.key])
              ? a.fields[f.key].length + "개 파일"
              : a.fields[f.key] + (f.unit ? " " + f.unit : "")),
        )
        .join(" / ") || "입력 안 함"
    );
  }
  function paint() {
    const fixedGuides = [
      ...new Set(
        evaluation.questions
          .filter((s) => s.question.fixed)
          .flatMap((s) => [
            ...s.question.choices
              .filter((c) => s.answer.choices.includes(c.key))
              .map((c) => c.guide),
            ...s.question.fields
              .filter((f) => s.answer.fields[f.key] !== undefined)
              .map((f) => f.guide),
          ])
          .filter((t) => t && t.trim()),
      ),
    ];
    const productAssets = (schema.productAssetIds || [])
      .map((id) => assetMap.get(id))
      .filter(Boolean);
    content.innerHTML =
      `<div class="pms-chat-head"><div>${S.badge(S.status[schema.status])}<small class="mono">${S.e(schema.catalogCode)}</small></div><small>${historyKeys.length} / ${evaluation.questions.filter((s) => s.visible && !s.question.fixed).length}</small></div>${gallery(productAssets)}<div class="pms-help">생산기간 ${schema.productionHours ?? 0}시간${schema.unitPrice != null ? " · 단가 " + Number(schema.unitPrice).toLocaleString() + "원" : ""}</div>${fixedGuides.map((t) => `<p class="pm-answer-guide">${S.e(t)}</p>`).join("")}<details><summary>제품의 고정 사양</summary>${evaluation.questions
        .filter((s) => s.question.fixed)
        .map(
          (s) =>
            `<p>${S.e(s.question.labels.customer)}: ${S.e(answerText(s))}</p>`,
        )
        .join("")}</details>` +
      historyKeys
        .map((key) => {
          const s = evaluation.questions.find((x) => x.question.key === key);
          return `<article class="pms-bubble answer"><div class="pms-chat-head"><strong>${S.e(s.question.labels.customer)}</strong><button data-answer-back="${S.e(key)}">수정</button></div><p>${S.e(answerText(s))}</p></article>`;
        })
        .join("");
    if (currentKey) {
      const state = evaluation.questions.find(
          (s) => s.question.key === currentKey,
        ),
        q = {
          ...state.question,
          choices: state.question.choices.filter((c) =>
            state.allowed.includes(c.key),
          ),
        };
      const attachments = [
        ...(q.assetIds || []),
        ...(q.fixed && q.control === "FILE"
          ? Object.values(state.answer.fields).flat()
          : []),
      ]
        .map((id) => assetMap.get(id))
        .filter(Boolean);
      content.innerHTML += `<article class="pms-bubble"><div class="pms-chat-head"><strong>${S.e(q.question || q.labels.customer + "를 입력해 주세요.")}</strong>${!state.required ? S.badge("선택사항") : ""}</div>${q.guide ? `<details><summary>? 선택 도움말</summary><p>${S.e(q.guide)}</p></details>` : ""}${gallery(attachments)}<div id="pm-answer-input">${S.inputAnswer(q, state.answer, q.fixed)}</div><div id="pm-choice-assets"></div><div id="pm-answer-guides" aria-live="polite"></div>${attempted.has(q.key) ? state.errors.map((e) => `<p class="pms-error">${S.e(e)}</p>`).join("") : ""}<div class="pms-actions pms-section"><button class="primary" id="pm-answer-next">${q.fixed ? "사양 확인 · 다음" : "다음"}</button></div></article>`;
      const box = S.$("#pm-answer-input", content);
      function selectedAssets() {
        const a = q.fixed ? state.answer : S.readAnswer(box, q, answers[q.key]);
        renderGuides(q, a);
        S.$("#pm-choice-assets", content).innerHTML = gallery(
          a.choices.flatMap((key) =>
            (q.choices.find((c) => c.key === key)?.assetIds || [])
              .map((id) => assetMap.get(id))
              .filter(Boolean),
          ),
        );
      }
      selectedAssets();
      box.addEventListener("input", selectedAssets);
      S.$$("[data-answer-choice]", box).forEach(
        (input) => (input.onchange = selectedAssets),
      );
      S.$$("input[type=file]", box).forEach(
        (input) =>
          (input.onchange = () =>
            guarded(async () => {
              const f = q.fields.find(
                (f) => f.key === input.dataset.answerField,
              );
              if (input.files.length > f.maxFiles)
                throw Error("파일은 최대 " + f.maxFiles + "개입니다.");
              for (const file of input.files) {
                const ext = file.name.split(".").pop().toLowerCase();
                if (
                  !f.extensions.includes(ext) ||
                  file.size > f.maxFileMB * 1024 * 1024
                )
                  throw Error(
                    f.labels.customer +
                      "의 파일 확장자 또는 용량을 확인해 주세요.",
                  );
              }
              const fd = new FormData();
              for (const file of input.files) fd.append("files", file);
              const uploaded = await S.api(
                adminId
                  ? "/admin/api/product-master/studio/assets/stage"
                  : `/product-spec/studio/${encodeURIComponent(token)}/upload`,
                "POST",
                fd,
              );
              answers[q.key] ??= { choices: [], fields: {} };
              answers[q.key].fields[f.key] = uploaded.map((a) => a.id);
              S.$(`[data-answer-files="${f.key}"]`, box).textContent = uploaded
                .map((a) => a.name)
                .join(", ");
              selectedAssets();
            })),
      );
      S.$("#pm-answer-next", content).onclick = async (e) => {
        const b = e.currentTarget;
        if (b.disabled) return;
        b.disabled = true;
        await guarded(async () => {
          attempted.add(q.key);
          answers[q.key] = q.fixed
            ? state.answer
            : S.readAnswer(box, q, answers[q.key]);
          evaluation = await S.api(
            adminId
              ? `/admin/api/product-master/studio/products/${adminId}/evaluate`
              : `/product-spec/studio/${encodeURIComponent(token)}/evaluate`,
            "POST",
            adminId ? { answers } : answers,
          );
          const fresh = evaluation.questions.find(
            (s) => s.question.key === q.key,
          );
          if (!fresh.errors.length) historyKeys.push(q.key);
          merge();
          paint();
        });
        if (b.isConnected) b.disabled = false;
      };
    } else {
      content.innerHTML += `<article class="pms-bubble"><strong>${evaluation.complete ? "제품 구성이 완료되었습니다." : "구성을 다시 확인해 주세요."}</strong><p>선택하신 사양을 확인해 주세요.</p><table><tbody>${evaluation.questions
        .filter((s) => s.visible || s.question.fixed)
        .map(
          (s) =>
            `<tr><th>${S.e(s.question.labels.customer)}</th><td>${S.e(answerText(s))}</td></tr>`,
        )
        .join(
          "",
        )}</tbody></table><button id="pm-copy-spec">구성 내용 복사</button><p class="pms-help">구성 테스트 결과이며, 주문이나 재고 차감은 발생하지 않습니다.</p></article>`;
      S.$("#pm-copy-spec", content).onclick = () =>
        guarded(async () => {
          await navigator.clipboard.writeText(
            schema.productName +
              "\n" +
              schema.catalogCode +
              "\n" +
              evaluation.questions
                .filter((s) => s.visible || s.question.fixed)
                .map((s) => s.question.labels.customer + ": " + answerText(s))
                .join("\n"),
          );
          S.toast("구성 내용을 복사했습니다.");
        });
    }
    S.$$("[data-answer-back]", content).forEach(
      (b) =>
        (b.onclick = () => {
          const i = historyKeys.indexOf(b.dataset.answerBack);
          historyKeys = historyKeys.slice(0, i);
          currentKey = b.dataset.answerBack;
          paint();
        }),
    );
  }
  function renderGuides(q, a) {
    const texts = [];
    for (const c of q.choices || [])
      if (a.choices?.includes(c.key) && c.guide?.trim()) texts.push(c.guide);
    for (const f of q.fields || [])
      if (
        a.fields?.[f.key] !== undefined &&
        a.fields[f.key] !== "" &&
        (!Array.isArray(a.fields[f.key]) || a.fields[f.key].length) &&
        f.guide?.trim()
      )
        texts.push(f.guide);
    for (const r of q.numberCases || [])
      if (
        r.guide?.trim() &&
        r.conditions.every((c) => {
          const raw = a.fields?.[c.fieldKey];
          if (raw === undefined || raw === "") return false;
          const v = Number(raw),
            n = Number(c.lower);
          return {
            EQ: () => v === n,
            GE: () => v >= n,
            GT: () => v > n,
            LE: () => v <= n,
            LT: () => v < n,
            RANGE: () =>
              (c.lowerInclusive ? v >= n : v > n) &&
              (c.upperInclusive ? v <= Number(c.upper) : v < Number(c.upper)),
          }[c.operator]?.();
        })
      )
        texts.push(r.guide);
    S.$("#pm-answer-guides", content).innerHTML = [...new Set(texts)]
      .map((t) => `<p class="pm-answer-guide">${S.e(t)}</p>`)
      .join("");
  }
  function installFaq() {
    document.getElementById("pm-faq-button")?.remove();
    if (!faq) return;
    const button = document.createElement("button");
    button.id = "pm-faq-button";
    button.className = "pm-faq-float";
    button.textContent = "?";
    button.setAttribute("aria-label", "자주 묻는 질문 및 별도 문의");
    root.append(button);
    button.onclick = () => {
      const amap = new Map(faq.assets.map((a) => [a.id, a]));
      const d = S.dialog(
        faq.title,
        `<div class="pm-faq-cards">${faq.entries.map((f) => `<article class="pm-faq-card"><h3>${S.e(f.title)}</h3><p>${S.e(f.content)}</p>${gallery(f.assetIds.map((id) => amap.get(id)).filter(Boolean))}</article>`).join("") || "<p>등록된 FAQ가 없습니다. 아래 연락처로 문의해 주세요.</p>"}</div>`,
        {
          foot: `${faq.phone ? `<a class="pms-button" href="tel:${S.e(faq.phone.replace(/[^+0-9]/g, ""))}">전화 ${S.e(faq.phone)}</a>` : ""}${faq.link ? '<button data-inquiry class="primary">별도 문의하기</button>' : ""}`,
        },
      );
      S.$("[data-inquiry]", d)?.addEventListener("click", () => {
        const current = S.copy(answers),
          box = S.$("#pm-answer-input", content),
          state = evaluation.questions.find(
            (s) => s.question.key === currentKey,
          );
        if (box && state)
          current[currentKey] = S.readAnswer(
            box,
            state.question,
            current[currentKey],
          );
        const inquiryPayload = {
          product: { name: schema.productName, code: schema.catalogCode },
          topic: { id: faq.id, title: faq.title },
          productionHours: schema.productionHours,
          unitPrice: schema.unitPrice,
          answers: current,
          selections: evaluation.questions
            .filter((s) => s.visible || s.question.fixed)
            .map((s) => ({
              groupKey: s.question.key,
              groupName: s.question.labels.customer,
              answer: current[s.question.key] ?? s.answer,
            })),
          createdAt: new Date().toISOString(),
        };
        window.productInquiryPayload = inquiryPayload;
        console.log("[ProductMaster 별도 문의]", inquiryPayload);
        const url = new URL(faq.link);
        if (!["http:", "https:"].includes(url.protocol))
          throw Error("문의 링크 형식이 올바르지 않습니다.");
        window.open(url.href, "_blank", "noopener,noreferrer");
      });
    };
  }
  await guarded(adminId || token ? loadProduct : catalog);
})(window.PMS);
