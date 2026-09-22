(async function (S) {
  "use strict";
  const root = document.getElementById("pm-studio");
  if (!root) return;
  const mode = root.dataset.mode || "standard",
    work = S.$("#pms-workspace");
  S.$("#pms-title").textContent =
    {
      groups: "그룹·옵션 관리",
      builder: "제품 조합 생성",
      standard: "규격 제품 관리",
      custom: "비규격 제품 관리",
      detail: "제품 상세",
      process: "비규격 질문 프로세스",
      faq: "FAQ 관리",
      view: "제품 상세 조회",
      actuals: "실 제품 재고",
    }[mode] || "제품관리";
  const markTab = (tab) => {
    S.$$(".pms-nav [data-tab]").forEach((a) => {
      a.classList.toggle("active", a.dataset.tab === tab);
      if (a.dataset.tab === tab) a.setAttribute("aria-current", "page");
      else a.removeAttribute("aria-current");
    });
    requestAnimationFrame(() => {
      const nav = S.$(".pms-nav"),
        active = nav?.querySelector(".active");
      if (nav && active) {
        const n = nav.getBoundingClientRect(),
          a = active.getBoundingClientRect();
        if (a.left < n.left || a.right > n.right)
          nav.scrollLeft += a.left - n.left - (nav.clientWidth - a.width) / 2;
      }
    });
  };
  markTab(mode === "process" || mode === "actuals" ? "custom" : mode);
  S.mode = mode;
  S.initTheme();
  S.$("#pms-description").textContent = S.descriptions[mode] || "";
  S.$("#pms-guide").onclick = () => S.guide(mode);
  try {
    try {
      await S.request("/bootstrap", "POST");
    } catch (e) {
      S.notice(e.message);
    }
    await S.catalog();
    if (mode === "groups") await S.groupsPage(work);
    else if (mode === "builder") await S.builderPage(work);
    else if (mode === "detail") {
      const p = await S.request("/products/" + root.dataset.productId);
      markTab(p.nonStandard ? "custom" : "standard");
      await S.builderPage(work, p);
    } else if (mode === "process")
      await S.processPage(
        work,
        await S.request("/products/" + root.dataset.productId),
      );
    else if (mode === "faq") await S.faqPage(work);
    else if (mode === "view" || mode === "actuals") {
      const p = await S.request("/products/" + root.dataset.productId);
      markTab(p.nonStandard ? "custom" : "standard");
      await S[mode === "view" ? "viewPage" : "actualsPage"](work, p);
    } else await S.listPage(work, mode === "custom");
    if (
      ["groups", "standard", "custom"].includes(mode) &&
      !localStorage.getItem("pm-studio-tour-dismissed-v3-" + mode)
    )
      S.guide(mode);
  } catch (e) {
    work.innerHTML = `<div class="pms-alert">${S.e(e.message)}</div>`;
    console.error(e);
  }
})(window.PMS);
