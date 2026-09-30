(function (S) {
  "use strict";
  const controls = {
    RADIO: "하나 선택", CHECKBOX: "여러 개 선택", NUMBER: "숫자 입력",
    TEXT: "텍스트 입력", TEXTAREA: "긴 글 입력", FILE: "파일 첨부",
  };
  const label = (x) => x?.labels?.customer || x?.key || "";
  const present = (x) => x !== undefined && x !== null && x !== "" &&
    (!Array.isArray(x) || x.length > 0);
  const unique = (files) => [...new Map((files || []).filter(Boolean).map((a) => [a.id || a.url, a])).values()];
  const safeUrl = (value) => {
    try {
      const url = new URL(value, location.origin);
      return ["http:", "https:"].includes(url.protocol) ? url.href : "";
    } catch { return ""; }
  };
  const download = (url) => {
    const value = new URL(url, location.origin);
    value.searchParams.set("download", "true");
    return value.href;
  };
  let detailSequence = 0;

  S.media = function (files, title = "이미지", hero = false) {
    const items = unique(files).filter((a) => safeUrl(a.url));
    const images = items.filter((a) => a.image), docs = items.filter((a) => !a.image);
    if (!items.length) return "";
    return `<section class="pm-media ${hero ? "is-hero" : ""}" data-pm-gallery="${S.e(JSON.stringify(images))}" data-pm-gallery-title="${S.e(title)}">
      ${images.length ? `<div class="pm-media-caption"><span>${S.e(title)}</span><small>${images.length}장 · 클릭하여 확대</small></div>
        <div class="pm-media-images">${images.map((a, i) => `<button type="button" data-pm-image="${i}" aria-label="${S.e(title + " · " + a.name)} 확대">
          <img src="${S.e(safeUrl(a.url))}" alt="${S.e(a.name)}" loading="${hero && i === 0 ? "eager" : "lazy"}"></button>`).join("")}</div>` : ""}
      ${docs.length ? `<div class="pm-media-files">${docs.map((a) => `<a href="${S.e(download(a.url))}" download="${S.e(a.name)}">첨부 · ${S.e(a.name)}</a>`).join("")}</div>` : ""}
    </section>`;
  };

  S.bindMedia = function (root) {
    if (root._pmMediaBound) return;
    root._pmMediaBound = true;
    root.addEventListener("click", (event) => {
      if (event._pmMediaHandled) return;
      const button = event.target.closest("[data-pm-image]");
      if (!button || !root.contains(button)) return;
      event._pmMediaHandled = true;
      const album = button.closest("[data-pm-gallery]");
      const images = JSON.parse(album.dataset.pmGallery);
      let index = Number(button.dataset.pmImage), touchX = null;
      const d = S.dialog(album.dataset.pmGalleryTitle || "이미지", `
        <div class="pm-lightbox" role="region" aria-label="이미지 슬라이드">
          <button type="button" data-image-prev aria-label="이전 이미지">‹</button>
          <figure><img data-image-full alt=""><figcaption data-image-caption aria-live="polite"></figcaption></figure>
          <button type="button" data-image-next aria-label="다음 이미지">›</button>
        </div><div class="pm-lightbox-bottom"><span data-image-count></span><a data-image-download class="pms-button">이미지 다운로드</a></div>`);
      d.classList.add("pm-image-dialog");
      function show() {
        const a = images[index];
        S.$("[data-image-full]", d).src = safeUrl(a.url);
        S.$("[data-image-full]", d).alt = a.name;
        S.$("[data-image-caption]", d).textContent = a.name;
        S.$("[data-image-count]", d).textContent = `${index + 1} / ${images.length}`;
        S.$("[data-image-download]", d).href = download(a.url);
        S.$("[data-image-download]", d).download = a.name;
        S.$$("[data-image-prev],[data-image-next]", d).forEach((b) => { b.disabled = images.length < 2; });
      }
      function move(delta) { index = (index + delta + images.length) % images.length; show(); }
      S.$("[data-image-prev]", d).onclick = () => move(-1);
      S.$("[data-image-next]", d).onclick = () => move(1);
      d.addEventListener("keydown", (e) => {
        if (e.key === "ArrowLeft" || e.key === "ArrowRight") {
          e.preventDefault(); move(e.key === "ArrowLeft" ? -1 : 1);
        }
      });
      const view = S.$(".pm-lightbox", d);
      view.addEventListener("touchstart", (e) => { touchX = e.touches[0]?.clientX ?? null; }, { passive: true });
      view.addEventListener("touchend", (e) => {
        if (touchX != null && Math.abs(e.changedTouches[0].clientX - touchX) > 40)
          move(e.changedTouches[0].clientX < touchX ? 1 : -1);
        touchX = null;
      }, { passive: true });
      show();
    });
  };

  S.conditionText = function (q, c) {
    if (S.isChoice(q?.control))
      return (c.choiceKeys || []).map((key) => label(q.choices?.find((x) => x.key === key)) || key).join(" 또는 ");
    const name = label(q?.fields?.find((f) => f.key === c.fieldKey)) || c.fieldKey;
    const op = { EQ: "일치", NE: "다름", GE: "이상", GT: "초과", LE: "이하", LT: "미만", PRESENT: "입력함", ABSENT: "입력 안 함" };
    return c.operator === "RANGE"
      ? `${name} ${c.lower} ${c.lowerInclusive ? "이상" : "초과"} ~ ${c.upper} ${c.upperInclusive ? "이하" : "미만"}`
      : `${name} ${c.lower ?? c.text ?? ""} ${op[c.operator] || c.operator}`.replace(/\s+/g, " ");
  };

  S.answerGuides = function (q, answer = {}) {
    const texts = [];
    for (const c of q.choices || [])
      if (answer.choices?.includes(c.key) && c.guide?.trim()) texts.push(c.guide);
    for (const f of q.fields || [])
      if (present(answer.fields?.[f.key]) && f.guide?.trim()) texts.push(f.guide);
    for (const r of q.numberCases || []) {
      if (!r.guide?.trim() || !r.conditions?.length) continue;
      const matches = r.conditions.every((c) => {
        const raw = answer.fields?.[c.fieldKey];
        if (!present(raw)) return false;
        const v = Number(raw), lo = Number(c.lower), hi = Number(c.upper);
        if (!Number.isFinite(v) || !Number.isFinite(lo)) return false;
        return {
          EQ: () => v === lo, GE: () => v >= lo, GT: () => v > lo,
          LE: () => v <= lo, LT: () => v < lo,
          RANGE: () => Number.isFinite(hi) && (c.lowerInclusive ? v >= lo : v > lo) && (c.upperInclusive ? v <= hi : v < hi),
        }[c.operator]?.() || false;
      });
      if (matches) texts.push(r.guide);
    }
    return [...new Set(texts)];
  };

  S.answerText = function (q, answer = {}, assets = new Map()) {
    if (S.isChoice(q.control))
      return (answer.choices || []).map((key) => label(q.choices?.find((c) => c.key === key)) || key).join(", ") || "선택 안 함";
    return (q.fields || []).filter((f) => present(answer.fields?.[f.key])).map((f) => {
      const value = answer.fields[f.key];
      const text = Array.isArray(value)
        ? value.map((id) => assets.get(id)?.name).filter(Boolean).join(", ") || `${value.length}개 파일`
        : String(value) + (f.unit ? " " + f.unit : "");
      return `${label(f)}: ${text}`;
    }).join(" / ") || "입력 안 함";
  };

  S.showProductFaq = function (faq, context) {
    if (!faq) return;
    const assets = new Map((faq.assets || []).map((a) => [a.id, a]));
    const d = S.dialog(faq.title, `<div class="pm-faq-cards">${(faq.entries || []).map((f, i) => `
      <article class="pm-faq-card"><span class="pm-detail-eyebrow">FAQ ${String(i + 1).padStart(2, "0")}</span>
        <h3>${S.e(f.title)}</h3>${f.content?.trim() ? `<p class="pm-preserve-lines">${S.e(f.content)}</p>` : ""}
        ${S.media((f.assetIds || []).map((id) => assets.get(id)), f.title + " 이미지")}</article>`).join("") || '<p class="pms-help">등록된 FAQ가 없습니다. 아래 연락처로 문의해 주세요.</p>'}</div>`, {
      foot: `${faq.phone ? `<a class="pms-button" href="tel:${S.e(faq.phone.replace(/[^+0-9]/g, ""))}">전화 ${S.e(faq.phone)}</a>` : ""}
        ${safeUrl(faq.link) && faq.link ? '<button type="button" data-inquiry class="primary">별도 문의하기</button>' : ""}`,
    });
    S.bindMedia(d);
    S.$("[data-inquiry]", d)?.addEventListener("click", () => {
      const value = typeof context === "function" ? context() : context || {};
      window.productInquiryPayload = { ...value, topic: { id: faq.id, title: faq.title }, createdAt: new Date().toISOString() };
      console.log("[ProductMaster 별도 문의]", window.productInquiryPayload);
      window.open(safeUrl(faq.link), "_blank", "noopener,noreferrer");
    });
  };

  S.registeredQuestion = function (q, fs = () => [], g = {}) {
    const names = (x) => `<dl><div><dt>고객용</dt><dd>${S.e(x.labels?.customer)}</dd></div><div><dt>생산팀용</dt><dd>${S.e(x.labels?.production)}</dd></div><div><dt>관리팀용</dt><dd>${S.e(x.labels?.management)}</dd></div></dl>`;
    return `<div class="pm-registered-summary">${names(q)}<p><strong>질문</strong> · ${S.e(q.question || label(q))}</p>
      <small class="mono">질문 value: ${S.e(q.key)}</small>${q.guide?.trim() ? `<div class="pm-tutorial"><strong>튜토리얼·도움말</strong><p class="pm-preserve-lines">${S.e(q.guide)}</p></div>` : ""}
      ${S.media(unique([...fs(q.assetIds), ...(g.assets || [])]), label(q) + " 질문 이미지")}
      ${q.choices?.length ? `<details><summary>등록된 답변 ${q.choices.length}개 · 표시명·value·안내·파일</summary>${q.choices.map((c) => {
        const v = g.values?.find((v) => v.key === c.key);
        return `<article class="pm-spec-option"><strong>${S.e(c.labels.management)}</strong>${names(c)}<small class="mono">value: ${S.e(c.key)}</small>
          ${c.namePart != null ? `<p>제품명 구성 문자: ${S.e(c.namePart) || "없음"}</p>` : ""}${c.guide?.trim() ? `<p class="pm-preserve-lines">${S.e(c.guide)}</p>` : ""}
          ${S.media(unique([...fs(c.assetIds), ...(v?.assets || [])]), label(q) + " · " + label(c))}</article>`;
      }).join("")}</details>` : ""}
      ${q.fields?.length ? `<details><summary>등록된 입력 항목 ${q.fields.length}개 · 표시명·value·허용 범위</summary>${q.fields.map((f) => `<article class="pm-spec-option"><strong>${S.e(f.labels.management)}</strong>${names(f)}<small class="mono">value: ${S.e(f.key)}</small>
        <p>${q.control === "NUMBER" ? S.e(`${f.min ?? (f.allowNegative ? "제한 없음" : 0)} ~ ${f.max ?? "제한 없음"} ${f.unit || ""} · 간격 ${f.step ?? 1}`)
          : q.control === "FILE" ? S.e(`${(f.extensions || []).join(", ")} · ${f.minFiles ?? 0}~${f.maxFiles ?? 1}개 · 최대 ${f.maxFileMB ?? 10}MB`)
          : S.e(`${f.minLength ?? 0}~${f.maxLength ?? 500}자 · ${f.format || "형식 제한 없음"}`)} · ${f.required ? "필수" : "선택"}</p>
        ${f.guide?.trim() ? `<p class="pm-preserve-lines">${S.e(f.guide)}</p>` : ""}</article>`).join("")}</details>` : ""}
      ${q.numberCases?.length ? `<details><summary>숫자 범위 조건 ${q.numberCases.length}개</summary>${q.numberCases.map((c) => `<article class="pm-spec-option"><strong>${S.e(c.name)}</strong><p>${S.e(c.conditions.map((x) => S.conditionText(q, x)).join(" 그리고 "))}</p>
        ${c.guide?.trim() ? `<p class="pm-preserve-lines">${S.e(c.guide)}</p>` : ""}<small class="mono">value: ${S.e(c.key)}</small></article>`).join("")}</details>` : ""}</div>`;
  };

  S.mountProductDetail = function (root, p, { admin = false, faq = null, summary = null, actions = "", onBack } = {}) {
    const prefix = "pm-detail-" + ++detailSequence;
    const groups = new Map((p.groups || []).map((g) => [g.id, g]));
    const files = unique([...(p.assets || []), ...(p.processAssets || []),
      ...(p.groups || []).flatMap((g) => [...(g.assets || []), ...(g.values || []).flatMap((v) => v.assets || [])])]);
    const amap = new Map(files.map((a) => [a.id, a]));
    const fs = (ids) => (ids || []).map((id) => amap.get(id)).filter(Boolean);
    const questions = summary
      ? summary.questions.filter((s) => s.visible || s.question.fixed).map((s) => s.question)
      : p.process?.questions || [];
    const isBase = (q) => S.isBase(groups.get(q.groupId) || {});
    const basics = questions.filter(isBase).sort((a, b) =>
      ["CATEGORY", "SUBCATEGORY", "SERIES"].indexOf(groups.get(a.groupId).role) - ["CATEGORY", "SUBCATEGORY", "SERIES"].indexOf(groups.get(b.groupId).role));
    function answer(q) {
      if (summary) return summary.questions.find((s) => s.question.key === q.key)?.answer || {};
      if (p.fixedAnswers?.[q.key]) return p.fixedAnswers[q.key];
      if (q.preset) return q.preset;
      if (!q.fixed) return null;
      const variant = p.variants?.find((v) => v.groupId === q.groupId);
      const g = groups.get(q.groupId);
      return { choices: g?.values ? g.values.filter((v) => variant?.valueIds?.includes(v.id)).map((v) => v.key) : (q.choices || []).map((c) => c.key), fields: variant?.inputs || {} };
    }
    const basicChips = basics.map((q) => `<div><span>${S.e(label(q))}</span><strong>${S.e(S.answerText(q, answer(q) || {}, amap))}</strong></div>`).join("");
    const currency = p.unitPrice == null ? "가격 문의" : Number(p.unitPrice).toLocaleString("ko-KR") + "원";
    const metrics = `<div class="pm-product-metrics"><div><span>생산기간</span><strong>${p.productionHours == null ? "미등록" : S.e(p.productionHours) + '<small>시간</small>'}</strong></div>
      ${!p.nonStandard ? `<div><span>제품 단가</span><strong>${currency}</strong></div>` : ""}<div><span>총 재고</span><strong>${Number(p.stock || 0).toLocaleString()}<small>개</small></strong></div>
      ${admin && p.nonStandard ? `<div><span>실 제품 종류</span><strong>${p.actualCount || 0}<small>종</small></strong></div>` : ""}</div>`;
    let heroFiles = p.assets || [];
    if (!heroFiles.some((a) => a.image)) heroFiles = [...heroFiles, ...files.filter((a) => a.image).slice(0, 1)];
    const custom = questions.filter((q) => !isBase(q));
    const topicButton = faq ? `<button type="button" data-pm-faq class="pm-faq-link">? FAQ · ${S.e(faq.title)}</button>` : "";
    const note = p.description?.trim() ? `<p class="pm-product-description pm-preserve-lines">${S.e(p.description)}</p>` : "";

    function technical(x, extra = "") {
      if (!admin) return "";
      return `<details class="pm-technical"><summary>value·상세 설정</summary><dl>
        <div><dt>내부 value</dt><dd class="mono">${S.e(x.key)}</dd></div>
        <div><dt>생산팀 표시명</dt><dd>${S.e(x.labels?.production)}</dd></div>
        <div><dt>관리팀 표시명</dt><dd>${S.e(x.labels?.management)}</dd></div>
        ${x.namePart != null ? `<div><dt>제품명 구성 문자</dt><dd>${S.e(x.namePart) || "없음"}</dd></div>` : ""}${extra}</dl></details>`;
    }
    function fieldLimits(q, f) {
      if (q.control === "NUMBER") return `${f.min ?? (f.allowNegative ? "제한 없음" : 0)} ~ ${f.max ?? "제한 없음"} ${f.unit || ""} · 입력 간격 ${f.step ?? 1}`;
      if (q.control === "FILE") return `${(f.extensions || []).join(", ")} · ${f.minFiles ?? 0}~${f.maxFiles ?? 1}개 · 파일당 최대 ${f.maxFileMB ?? 10}MB`;
      return `${f.minLength ?? 0}~${f.maxLength ?? 500}자${f.format ? " · " + f.format : ""}`;
    }
    function questionCard(q, i) {
      const g = groups.get(q.groupId) || {}, a = answer(q), chosen = a != null;
      const choices = (q.choices || []).filter((c) => !chosen || a.choices?.includes(c.key));
      const media = unique([...fs(q.assetIds), ...(g.assets || [])]);
      return `<article class="pm-spec-card" id="${prefix}-${isBase(q) ? "base" : "q"}-${i}"><header class="pm-spec-head">
        <span class="pm-question-number">${String(i + 1).padStart(2, "0")}</span><div><div class="pm-spec-tags">${S.badge(controls[q.control])}
          ${S.badge(chosen ? "확정 사양" : q.visible ? "고객 질문" : "조건에 따라 표시", chosen ? "" : "blue")}
          ${!chosen && q.required ? S.badge("필수 답변") : ""}${g.priceImpact ? S.badge("단가 영향", "amber") : ""}</div>
          <h3>${S.e(label(q))}</h3>${!chosen ? `<p>${S.e(q.question || label(q) + "를 입력해 주세요.")}</p>` : ""}</div></header>
        ${q.guide?.trim() ? `<div class="pm-tutorial"><strong>선택 가이드</strong><p class="pm-preserve-lines">${S.e(q.guide)}</p></div>` : ""}
        ${S.media(media, label(q) + " 가이드 이미지")}
        ${choices.length ? `<div class="pm-spec-options">${choices.map((c) => {
          const global = g.values?.find((v) => v.key === c.key);
          const media = unique([...fs(c.assetIds), ...(global?.assets || [])]);
          return `<section class="pm-spec-option"><strong>${S.e(label(c))}</strong>${c.guide?.trim() ? `<p class="pm-preserve-lines">${S.e(c.guide)}</p>` : ""}
            ${S.media(media, label(q) + " · " + label(c))}${technical(c)}</section>`;
        }).join("")}</div>` : ""}
        ${!chosen && S.isChoice(q.control) && !choices.length ? '<p class="pms-help">선택 가능한 답변이 아직 등록되지 않았습니다.</p>' : ""}
        ${(q.fields || []).length ? `<div class="pm-spec-options">${q.fields.map((f) => {
          const raw = a?.fields?.[f.key];
          const value = Array.isArray(raw) ? `${raw.length}개 파일` : present(raw) ? String(raw) + (f.unit ? " " + f.unit : "") : "입력 안 함";
          return `<section class="pm-spec-option"><div class="pm-field-heading"><strong>${S.e(label(f))}</strong>${!chosen && f.required ? S.badge("필수") : ""}</div>
            ${chosen ? `<p class="pm-chosen-value">${S.e(value)}</p>` : ""}<p class="pm-field-limit">${S.e(fieldLimits(q, f))}</p>
            ${f.guide?.trim() && (!chosen || present(raw)) ? `<p class="pm-preserve-lines">${S.e(f.guide)}</p>` : ""}
            ${Array.isArray(raw) ? S.media(fs(raw), label(f) + " 첨부파일") : ""}${technical(f)}</section>`;
        }).join("")}</div>` : ""}
        ${!chosen && q.numberCases?.length ? `<div class="pm-number-cases"><h4>입력값에 따른 범위 조건</h4>${q.numberCases.map((c) => `<article><strong>${S.e(c.name)}</strong>
          <p>${S.e((c.conditions || []).map((x) => S.conditionText(q, x)).join(" 그리고 "))}</p>${c.guide?.trim() ? `<p class="pm-preserve-lines">${S.e(c.guide)}</p>` : ""}${admin ? `<small class="mono">value: ${S.e(c.key)}</small>` : ""}</article>`).join("")}</div>` : ""}
        ${chosen ? S.answerGuides({ ...q, choices: [], fields: [] }, a).map((t) => `<div class="pm-tutorial"><p class="pm-preserve-lines">${S.e(t)}</p></div>`).join("") : ""}
        ${admin ? `<div class="pms-actions pm-spec-edit-links"><a class="pms-button" href="/admin/product-master/groups?groupId=${q.groupId}" target="_blank" rel="noopener">공통 그룹·옵션 설정 수정</a>${p.nonStandard && !isBase(q) ? `<a class="pms-button" href="/admin/product-master/products/${p.id}/process?question=${encodeURIComponent(q.key)}">이 제품의 질문·답변 수정</a>` : ""}</div>` : ""}
        ${technical(q, g.key ? `<div><dt>그룹 value</dt><dd class="mono">${S.e(g.key)}</dd></div><div><dt>그룹 질문 설정</dt><dd>${g.askQuestion ? "고객 질문 포함" : "질문 제외 · 고정 사양"}</dd></div>
          <div><dt>제품 질문 기본 표시</dt><dd>${q.fixed ? "고정 사양" : q.visible ? "표시" : "조건에 따라 표시"}</dd></div><div><dt>조건 연결 필수</dt><dd>${q.requireRule ? "모든 입력 경로에 연결 필요" : "기본 흐름 허용"}</dd></div>
          ${g.labels && (JSON.stringify(g.labels) !== JSON.stringify(q.labels) || g.question !== q.question || g.guide !== q.guide) ? `<div><dt>공통 그룹 고객용</dt><dd>${S.e(g.labels.customer)}</dd></div><div><dt>공통 그룹 생산팀용</dt><dd>${S.e(g.labels.production)}</dd></div><div><dt>공통 그룹 관리팀용</dt><dd>${S.e(g.labels.management)}</dd></div>
            <div><dt>공통 질문</dt><dd>${S.e(g.question)}</dd></div>${g.guide?.trim() ? `<div><dt>공통 안내</dt><dd class="pm-preserve-lines">${S.e(g.guide)}</dd></div>` : ""}` : ""}` : "")}
      </article>`;
    }
    const rules = admin && !summary && p.nonStandard ? `<section id="${prefix}-rules" class="pm-product-section"><header><span class="pm-detail-eyebrow">PROCESS RULES</span><h2>답변에 따른 연관관계</h2></header>
      <div class="pm-detail-rules">${(p.process?.rules || []).map((r) => `<article><h3>${S.e(r.name)}</h3><p>${S.e((r.conditions || []).map((c) => {
        const q = questions.find((x) => x.key === c.groupKey);
        return label(q) + " · " + S.conditionText(q, c);
      }).join(r.match === "ANY" ? " 또는 " : " 그리고 "))}</p><ul>${(r.actions || []).map((a) => {
        const target = questions.find((q) => q.key === a.targetKey);
        const text = a.effect === "ALLOW" ? "표시할 답변: " + (a.choiceKeys || []).map((key) => label(target?.choices?.find((c) => c.key === key)) || key).join(", ")
          : a.effect === "RENAME" ? "질문 변경: " + a.questionText
          : { HIDE: "질문 건너뜀", SHOW: "질문 표시", REQUIRE: "필수 답변으로 변경", OPTIONAL: "선택 답변으로 변경" }[a.effect] || a.effect;
        return `<li><strong>${S.e(label(target))}</strong> · ${S.e(text)}</li>`;
      }).join("")}</ul></article>`).join("") || '<p class="pms-help">추가 연결 없이 위 질문 순서대로 진행합니다.</p>'}</div></section>` : "";
    const summarySection = summary ? `<section id="${prefix}-selection" class="pm-product-section"><header><span class="pm-detail-eyebrow">YOUR SELECTION</span><h2>선택·입력한 제품 구성</h2><p>입력하신 사양이 이 제품의 제작 구성으로 정리되었습니다.</p></header>
      <div class="pm-selection-grid">${questions.map((q) => `<div><span>${S.e(label(q))}</span><strong>${S.e(S.answerText(q, answer(q) || {}, amap))}</strong></div>`).join("")}</div>
      <div class="pms-actions"><button type="button" data-copy-configuration>구성 내용 복사</button>${onBack ? '<button type="button" data-detail-back>선택 내용 수정</button>' : ""}</div></section>` : "";

    root.innerHTML = `<div class="pm-product-detail">${onBack && !summary ? '<div class="pm-detail-top"><button type="button" data-detail-back>← 제품 목록으로</button></div>' : ""}
      <section class="pm-product-hero"><div class="pm-product-visual">${S.media(heroFiles, "제품 이미지", true) || '<div class="pm-no-image"><span>HIDDENBATH</span><p>등록된 제품 이미지가 없습니다.</p></div>'}</div>
        <div class="pm-product-intro"><span class="pm-detail-eyebrow">${summary ? "MY CONFIGURATION" : p.nonStandard ? "CUSTOM PRODUCT" : "STANDARD PRODUCT"}</span>
          <div class="pms-actions">${S.badge(p.nonStandard ? "비규격" : "규격", "blue")}${S.badge(S.status[p.status] || p.status)}</div><h1>${S.e(p.productName)}</h1>
          <p class="pm-product-code mono">${S.e(p.catalogCode)}</p>${note}<div class="pm-basic-specs">${basicChips}</div>${metrics}
          ${p.nonStandard && !summary ? `<p class="pm-custom-intro">${custom.filter((q) => !q.fixed).length}개 커스텀 항목 · 선택지와 입력 범위를 아래에서 확인하세요.</p>` : ""}
          <div class="pms-actions pm-detail-actions">${topicButton}${actions}</div></div></section>
      <nav class="pm-product-tabs" aria-label="상세 정보 영역">${summary ? `<a href="#${prefix}-selection">내 선택 내용</a>` : ""}<a href="#${prefix}-spec">${p.nonStandard && !summary ? "질문·선택 범위" : "구성 사양·가이드"}</a>
        ${rules ? `<a href="#${prefix}-rules">연관관계</a>` : ""}${faq ? '<button type="button" data-pm-faq>FAQ 보기</button>' : ""}</nav>${summarySection}
      <section id="${prefix}-spec" class="pm-product-section"><header><span class="pm-detail-eyebrow">${p.nonStandard && !summary ? "CUSTOMIZATION GUIDE" : "PRODUCT SPECIFICATION"}</span>
        <h2>${p.nonStandard && !summary ? "어떤 질문이 나올까요?" : "제품 사양과 사용 가이드"}</h2>${p.nonStandard && !summary ? '<p>번호 순서로 질문이 진행됩니다. 답변에 따라 뒤의 질문이나 선택지가 달라질 수 있습니다.</p>' : ""}</header>
        ${custom.length ? `<div class="pm-question-index">${custom.map((q, i) => `<a href="#${prefix}-q-${i}">${String(i + 1).padStart(2, "0")} ${S.e(label(q))}</a>`).join("")}</div>
          <div class="pm-spec-cards">${custom.map(questionCard).join("")}</div>` : '<p class="pms-help">추가 옵션 없이 위 기본 분류로 구성된 제품입니다.</p>'}
        ${admin || basics.some((q) => q.guide?.trim() || (q.choices || []).some((c) => c.guide?.trim() || fs(c.assetIds).length || groups.get(q.groupId)?.values?.find((v) => v.key === c.key)?.assets?.length) || fs(q.assetIds).length || groups.get(q.groupId)?.assets?.length)
          ? `<details class="pm-base-guides"><summary>${admin ? "기본 분류의 등록 내용·value·이미지·안내 보기" : "기본 분류의 이미지·안내 보기"}</summary>${basics.map(questionCard).join("")}</details>` : ""}</section>${rules}
      ${admin ? `<details class="pm-product-identity"><summary>제품 코드·관리 정보·제품명 생성 규칙</summary><dl><div><dt>제품 ID</dt><dd>${p.id}</dd></div><div><dt>제품 코드</dt><dd class="mono">${S.e(p.productCode)}</dd></div><div><dt>카탈로그 코드</dt><dd class="mono">${S.e(p.catalogCode)}</dd></div><div><dt>등록 상태</dt><dd>${S.e(p.registrationStatus)}</dd></div>
        ${(p.nameTokens || []).map((t, i) => `<div><dt>제품명 규칙 ${i + 1}</dt><dd>${S.e(groups.get(t.groupId)?.labels?.management || t.groupId)} · 구분자 ${S.e(JSON.stringify(t.before || ""))} · 앞 문자 ${S.e(JSON.stringify(t.prefix || ""))} · 뒤 문자 ${S.e(JSON.stringify(t.suffix || ""))}</dd></div>`).join("")}</dl></details>` : ""}
    </div>`;
    S.bindMedia(root);
    const context = () => ({ product: { name: p.productName, code: p.catalogCode }, productionHours: p.productionHours, unitPrice: p.unitPrice,
      selections: questions.map((q) => ({ groupKey: q.key, groupName: label(q), answer: answer(q) })), answers: summary?.answers || p.fixedAnswers || {} });
    S.$$("[data-pm-faq]", root).forEach((b) => { b.onclick = () => S.showProductFaq(faq, context); });
    S.$$("[data-detail-back]", root).forEach((b) => { b.onclick = onBack; });
    S.$("[data-copy-configuration]", root)?.addEventListener("click", async (e) => {
      try {
        await navigator.clipboard.writeText(`${p.productName}\n${p.catalogCode}\n` + questions.map((q) => `${label(q)}: ${S.answerText(q, answer(q) || {}, amap)}`).join("\n"));
        S.toast("구성 내용을 복사했습니다.");
      } catch { S.toast("복사 권한을 확인해 주세요."); }
    });
  };
})(window.PMS);
