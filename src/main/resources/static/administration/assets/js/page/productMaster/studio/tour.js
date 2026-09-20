(function (S) {
  "use strict";
  const paths = {
    layers: '<path d="m12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5M3 16l9 5 9-5"/>',
    box: '<path d="m3 7 9-4 9 4v10l-9 4-9-4V7Zm0 0 9 5 9-5M12 12v9M7 5l10 5"/>',
    combine:
      '<rect x="3" y="3" width="6" height="6" rx="1"/><rect x="15" y="15" width="6" height="6" rx="1"/><path d="M15 3h6v6M3 15v6h6M12 6h3M18 9v3M6 9v6M9 18h6"/>',
    flow: '<circle cx="6" cy="4" r="2"/><circle cx="18" cy="9" r="2"/><circle cx="6" cy="20" r="2"/><path d="M6 6v12M16 9h-4a6 6 0 0 0-6 6"/>',
    help: '<circle cx="12" cy="12" r="9"/><path d="M9 9a3 3 0 0 1 6 0c0 2-3 2-3 4M12 16h.01"/>',
    chat: '<path d="M21 11a9 8 0 0 1-9 8H8l-5 3 1-6a8 8 0 0 1-1-5 9 8 0 0 1 18 0ZM8 10h8M8 14h5"/>',
    search: '<circle cx="10" cy="10" r="6"/><path d="m15 15 6 6"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    check: '<path d="m5 12 4 4L19 6"/>',
    upload: '<path d="M12 16V3m-5 5 5-5 5 5M4 15v6h16v-6"/>',
    edit: '<path d="m15 4 5 5M3 21l5-1L21 7l-5-5L3 15v6Z"/>',
    grip: '<path d="M8 5h.01M16 5h.01M8 12h.01M16 12h.01M8 19h.01M16 19h.01" stroke-width="3"/>',
  };
  S.icon = (name) =>
    `<svg class="pms-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${paths[name] || paths.layers}</svg>`;
  const opened = new Map();
  S.openEditor = (key) => opened.set(key, true);
  S.editorAttrs = (key, initial = true) =>
    `data-editor-key="${S.e(key)}" ${(opened.has(key) ? opened.get(key) : initial) ? "open" : ""}`;
  S.trackEditors = (root) => {
    S.$$("details[data-editor-key]", root).forEach((el) => {
      el.ontoggle = () => opened.set(el.dataset.editorKey, el.open);
      const summary = S.$("summary", el);
      if (summary)
        summary.onclick = (e) => {
          if (e.target.closest(".pms-handle")) e.preventDefault();
        };
    });
  };
  S.descriptions = {
    groups:
      "기본 분류와 그룹을 만들고, 표시명·입력 방식·보기를 관리합니다. 손잡이를 끌면 순서가 이동합니다.",
    builder:
      "그룹을 구성 영역으로 끌어온 뒤 보기를 선택하세요. 제품명과 중복 여부를 미리 확인하고 등록합니다.",
    standard:
      "기본 분류와 상세 사양으로 제품을 찾고, 제품 정보·이미지·재고를 관리합니다.",
    custom:
      "커스텀 가능한 제품을 찾고 질문과 조건을 완성합니다. 검증을 통과한 제품만 고객에게 공개됩니다.",
    detail:
      "제품의 실제 사양과 표시 정보를 수정합니다. 저장할 때 동일한 구성의 제품이 있는지 다시 확인합니다.",
    process:
      "질문의 순서를 정하고, 커스텀 조건을 다음 질문에 연결하세요. 미리보기와 검증으로 흐름을 확인합니다.",
  };
  S.overview = async () => {
    const root = S.$("#pms-overview");
    if (!root) return;
    const render = (counts = {}) => {
      root.innerHTML = [
        ["layers", "구성 그룹", S.groups.length, "필수 기본그룹 3개", "blue"],
        [
          "combine",
          "등록된 보기",
          S.groups.reduce((n, g) => n + g.values.length, 0),
          "그룹별 옵션과 표시명",
          "purple",
        ],
        [
          "box",
          "규격 제품",
          counts.standard ?? "…",
          "조합 · 사양 · 재고",
          "green",
        ],
        [
          "flow",
          "비규격 제품",
          counts.custom ?? "…",
          `등록중 ${counts.draft ?? "…"}개`,
          "amber",
        ],
      ]
        .map(
          ([icon, title, value, sub, tone]) =>
            `<div class="pms-metric ${tone}"><span class="pms-metric-icon">${S.icon(icon)}</span><div><strong>${typeof value === "number" ? value.toLocaleString() : value}</strong><span>${title}</span><small>${sub}</small></div></div>`,
        )
        .join("");
    };
    render(S.counts);
    try {
      S.counts = await S.request("/summary");
      render(S.counts);
    } catch (e) {
      root.title = e.message;
    }
  };
  S.decorate = (root = document) => {
    S.$$(".pms [data-icon]:not([data-icon-ready])", root).forEach((el) => {
      el.dataset.iconReady = "1";
      el.innerHTML = S.icon(el.dataset.icon);
    });
    S.$$(".pms .pms-handle:not([data-icon-ready])", root).forEach((el) => {
      el.dataset.iconReady = "1";
      el.innerHTML = S.icon("grip");
      el.title = "누른 채 끌어 순서를 변경합니다";
    });
    S.$$(".pms .pms-panel-title h2:not([data-icon-ready])", root).forEach(
      (el) => {
        el.dataset.iconReady = "1";
        el.insertAdjacentHTML(
          "afterbegin",
          S.icon(S.mode === "process" ? "flow" : "layers"),
        );
      },
    );
    S.$$(
      ".pms details.pms-section > summary:not([data-disclosure])",
      root,
    ).forEach((el) => {
      el.dataset.disclosure = "1";
      el.classList.add("pms-disclosure");
      el.insertAdjacentHTML(
        "beforeend",
        '<span class="pms-disclosure-hint">클릭하여 펼치기/접기</span><svg class="pms-chevron" width="16" height="16" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6" fill="none" stroke="currentColor" stroke-width="2"/></svg>',
      );
    });
    S.trackEditors(root);
  };
  S.initTheme = () => {
    S.decorate();
    let pending = false;
    new MutationObserver(() => {
      if (pending) return;
      pending = true;
      requestAnimationFrame(() => {
        pending = false;
        S.decorate();
      });
    }).observe(document.body, { childList: true, subtree: true });
  };
  const guides = {
    groups: [
      [
        "#pms-group-list",
        "01 · 구조부터 확인",
        "대분류·중분류·시리즈는 필수 기본그룹입니다. 목록에서 그룹을 선택하면 오른쪽에 설정이 열립니다.",
      ],
      [
        "#pms-new-group",
        "02 · 필요한 그룹 추가",
        "색상이나 사이즈처럼 필요한 그룹을 추가합니다. 기본그룹 외에는 비규격을 선택할 수 있습니다.",
      ],
      [
        "#pms-group-editor [data-mirror]",
        "03 · 표시명 입력",
        "고객명을 입력하면 생산팀과 관리팀 명칭도 채워집니다. 각 명칭은 따로 수정할 수 있습니다.",
      ],
      [
        '#pms-group-editor [data-path="control"]',
        "04 · 입력 방식 선택",
        "선택형은 보기를, 입력형은 필요한 필드를 추가합니다. 비규격은 여기서 형태만 정하고 제품 프로세스에서 내용을 구성합니다.",
      ],
      [
        "#pms-save-group",
        "05 · 저장 후 옵션 편집",
        "그룹을 저장한 다음 + 보기로 옵션을 추가하세요. 각 보기의 제목을 누르면 편집 영역이 펼쳐집니다. 저장 시 중복 표시명을 검사합니다.",
      ],
      [
        "#pms-group-list .pms-handle",
        "06 · 손잡이로 순서 변경",
        "손잡이를 잡고 위아래로 끌면 새 위치가 표시됩니다. 그룹·보기·입력 필드는 같은 방식으로 순서를 바꿉니다.",
      ],
    ],
    builder: [
      [
        "#pms-palette",
        "01 · 그룹 구성",
        "왼쪽 그룹을 가운데 구성 영역으로 끌거나 추가 버튼을 누르세요. 기본그룹 3개는 항상 포함됩니다.",
      ],
      [
        "#pms-selected",
        "02 · 순서와 보기 선택",
        "손잡이로 질문 순서를 정하고 생성에 사용할 보기를 활성화합니다. 비규격을 넣으면 기본그룹 외에는 비규격 그룹만 구성할 수 있습니다.",
      ],
      [
        "#pms-name-rule",
        "03 · 제품명 규칙",
        "구성요소를 끌어 순서를 정하고 구분문자와 괄호를 입력하세요. 구성 문자가 빈 옵션은 해당 구분문자도 함께 생략합니다.",
      ],
      [
        "#pms-generate",
        "04 · 미리보기와 등록",
        "모든 조합을 미리 계산합니다. 중복 제품과 주체별 표시를 확인하고 불필요한 행을 제거하세요. 마지막 등록 버튼을 눌러야 저장됩니다.",
      ],
    ],
    standard: [
      [
        ".pms-search-grid",
        "01 · 제품 찾기",
        "제품명·상태·페이지 크기를 정하고 조회하세요. 기본 페이지 크기는 50개입니다.",
      ],
      [
        "#pms-basic-filters",
        "02 · 여러 분류 함께 선택",
        "같은 그룹의 여러 옵션은 하나라도 맞으면 검색하고, 서로 다른 그룹은 모두 만족해야 검색합니다.",
      ],
      [
        "#pms-advanced",
        "03 · 고급검색",
        "필요할 때만 상세 검색을 열어 색상·사이즈 등 그룹별 옵션과 입력값을 추가로 좁힙니다.",
      ],
      [
        "#pms-products",
        "04 · 상세 정보 관리",
        "제품 행을 누르면 사양·제품명·첨부파일·재고를 수정할 수 있습니다. 사양을 저장할 때 중복을 다시 확인합니다.",
      ],
    ],
    custom: [
      [
        ".pms-search-grid",
        "01 · 비규격 제품 찾기",
        "기본 분류·제품명·상태로 검색하세요. 등록중 제품은 고객 챗봇에 공개되지 않습니다.",
      ],
      [
        "#pms-advanced",
        "02 · 커스텀 항목 검색",
        "고급검색에서 색상·크기 등 커스텀 가능한 그룹을 선택해 제품을 찾습니다.",
      ],
      [
        "#pms-products",
        "03 · 프로세스 완성",
        "제품을 열고 비규격 프로세스에서 보기와 입력 필드를 구성하세요. 조건 검증 후 등록완료하면 재고에 따라 사용중·재고없음이 결정됩니다.",
      ],
    ],
    detail: [
      [
        "#pms-selected",
        "01 · 실제 사양 수정",
        "제품에 포함된 그룹과 옵션을 확인하고 수정합니다. 실제 구성이 같으면 제품명이 달라도 중복입니다.",
      ],
      [
        "#pms-summary",
        "02 · 제품 정보",
        "제품명은 규칙으로 다시 생성하거나 직접 수정할 수 있습니다. 제품 자체의 이미지와 첨부파일도 여기서 관리합니다.",
      ],
      [
        "#pms-stock",
        "03 · 재고 관리",
        "입고·출고·반품·조정을 선택하고 변경 수량과 사유를 남기세요. 음수 재고는 허용하지 않습니다.",
      ],
      [
        "#pms-save-product",
        "04 · 중복 확인 후 저장",
        "변경 저장 시 다른 제품과의 사양 충돌을 검사합니다. 비규격은 프로세스 화면에서 커스텀 조건도 완성해 주세요.",
      ],
    ],
    process: [
      [
        "#pms-flow-order",
        "01 · 질문 순서",
        "손잡이로 순서를 정합니다. 앞선 답변이 뒤의 질문에만 영향을 주도록 연결하세요.",
      ],
      [
        "#pms-edit-question",
        "02 · 보기·입력 필드",
        "질문을 선택한 뒤 상세 편집을 여세요. +로 W·D·H 같은 필드와 최소·최대값, 선택 보기를 등록합니다.",
      ],
      [
        "#pms-viewport",
        "03 · 커스텀 조건 연결",
        "질문의 + 커스텀으로 조건을 만들고 연결점을 다음 질문으로 끌어 보세요. 여러 필드 범위와 선택값을 AND·OR로 묶을 수 있습니다.",
      ],
      [
        "#pms-question-inspector",
        "04 · 빠진 구간 관리",
        "일치 규칙 필수를 켜면 이 질문에 도달하는 모든 경우가 규칙에 포함되어야 합니다. 허용 보기 제한·표시·건너뜀을 조건별로 지정하세요.",
      ],
      [
        "#pms-preview-process",
        "05 · 직접 선택하며 확인",
        "고객처럼 값을 선택하고 질문이 표시되거나 생략되는지 확인합니다. 앞 답변을 바꾸면 더 이상 유효하지 않은 뒤 답변은 초기화됩니다.",
      ],
      [
        "#pms-validate-process",
        "06 · 검증",
        "누락 구간·서로 충돌하는 결과·역방향 연결·잘못된 입력을 확인하세요. 검증 한도를 넘는 경우도 등록을 차단합니다.",
      ],
      [
        "#pms-publish-process",
        "07 · 등록완료",
        "임시저장은 작업을 보관합니다. 검증·등록완료를 눌러 오류 없는 프로세스를 고객에게 공개하세요.",
      ],
    ],
  };
  S.guide = (mode) => {
    S.closeGuide?.();
    const steps = guides[mode] || guides.standard,
      key = "pm-studio-tour-dismissed-v3-" + mode;
    const previousFocus = document.activeElement;
    const root = document.createElement("div");
    root.className = "pms pms-tour";
    root.innerHTML =
      '<div class="pms-tour-shade"></div><div class="pms-tour-focus"></div><section class="pms-tour-card" role="dialog" aria-modal="true" aria-labelledby="pms-tour-title"><div class="pms-tour-top"><span class="pms-tour-count"></span><button data-tour-close aria-label="화면 안내 닫기">×</button></div><h2 id="pms-tour-title"></h2><p class="pms-tour-text"></p><label class="pms-check"><input type="checkbox" data-tour-dismiss><span>다시 보지 않기</span></label><footer><button data-tour-prev>이전</button><button class="primary" data-tour-next>다음</button></footer></section>';
    document.body.append(root);
    const card = S.$(".pms-tour-card", root),
      ring = S.$(".pms-tour-focus", root),
      dismiss = S.$("[data-tour-dismiss]", root);
    dismiss.checked = localStorage.getItem(key) === "1";
    let index = 0,
      target = null,
      frame = 0;
    function place() {
      if (!target?.isConnected) target = S.$("#pms-workspace");
      const r = target.getBoundingClientRect(),
        pad = 5;
      const left = Math.max(6, r.left - pad),
        top = Math.max(6, r.top - pad),
        right = Math.min(innerWidth - 6, r.right + pad),
        bottom = Math.min(innerHeight - 6, r.bottom + pad);
      Object.assign(ring.style, {
        left: left + "px",
        top: top + "px",
        width: Math.max(0, right - left) + "px",
        height: Math.max(0, bottom - top) + "px",
      });
      const w = Math.min(350, innerWidth - 24);
      card.style.width = w + "px";
      const h = card.offsetHeight;
      let x = right + 16,
        y = top;
      if (x + w > innerWidth - 12) {
        x = Math.max(12, Math.min(left, innerWidth - w - 12));
        y = bottom + 16;
        if (y + h > innerHeight - 12) y = top - h - 16;
      }
      Object.assign(card.style, {
        left: Math.max(12, x) + "px",
        top: Math.max(12, Math.min(y, innerHeight - h - 12)) + "px",
      });
    }
    function schedule() {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(place);
    }
    function show() {
      const [selector, title, text] = steps[index];
      target = S.$(selector) || S.$("#pms-workspace");
      S.$(".pms-tour-count", root).textContent =
        `화면 안내 · ${index + 1} / ${steps.length}`;
      S.$("h2", root).textContent = title;
      S.$(".pms-tour-text", root).textContent = text;
      S.$("[data-tour-prev]", root).disabled = index === 0;
      S.$("[data-tour-next]", root).textContent =
        index === steps.length - 1 ? "닫기" : "다음";
      target.scrollIntoView({ behavior: "instant", block: "nearest" });
      place();
    }
    function close() {
      if (dismiss.checked) localStorage.setItem(key, "1");
      else localStorage.removeItem(key);
      removeEventListener("resize", schedule);
      removeEventListener("scroll", schedule, true);
      document.removeEventListener("keydown", keyboard, true);
      cancelAnimationFrame(frame);
      root.remove();
      previousFocus?.focus();
      S.closeGuide = null;
    }
    function keyboard(e) {
      if (e.key === "Escape") {
        e.preventDefault();
        close();
        return;
      }
      if (e.key === "Tab") {
        const focusable = S.$$("button:not(:disabled),input", card),
          first = focusable[0],
          last = focusable.at(-1);
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    }
    S.closeGuide = close;
    S.$("[data-tour-close]", root).onclick = close;
    S.$("[data-tour-prev]", root).onclick = () => {
      if (index > 0) {
        index--;
        show();
      }
    };
    S.$("[data-tour-next]", root).onclick = () => {
      if (index === steps.length - 1) close();
      else {
        index++;
        show();
      }
    };
    addEventListener("resize", schedule);
    addEventListener("scroll", schedule, true);
    document.addEventListener("keydown", keyboard, true);
    show();
    S.$("[data-tour-next]", root).focus();
  };
})(window.PMS);
