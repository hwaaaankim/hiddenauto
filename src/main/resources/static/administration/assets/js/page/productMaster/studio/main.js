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
    }[mode] || "제품관리";
  S.$(
    `[data-tab="${mode === "detail" ? "standard" : mode === "process" ? "custom" : mode}"]`,
  )?.classList.add("active");
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
    else if (mode === "detail")
      await S.builderPage(
        work,
        await S.request("/products/" + root.dataset.productId),
      );
    else if (mode === "process")
      await S.processPage(
        work,
        await S.request("/products/" + root.dataset.productId),
      );
    else await S.listPage(work, mode === "custom");
    if (!localStorage.getItem("pm-studio-tour-dismissed-v3-" + mode))
      S.guide(mode);
  } catch (e) {
    work.innerHTML = `<div class="pms-alert">${S.e(e.message)}</div>`;
    console.error(e);
  }
})(window.PMS);
