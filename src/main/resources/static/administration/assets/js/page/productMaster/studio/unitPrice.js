(function (S) {
  "use strict";
  S.unitPriceText = (value) => value == null ? "단가 미등록" : Number(value).toLocaleString("ko-KR", { maximumFractionDigits: 2 }) + "원";
  S.unitPriceCard = (product) => `<section class="pm-unit-price-card" aria-label="규격 제품 단가 관리"><div><span>규격 제품 단가</span><strong>${S.e(S.unitPriceText(product.unitPrice))}</strong><small>비규격의 사이즈별 추가금은 해당 비규격 제품의 ‘가격계산식’에서 별도로 설정합니다.</small></div><button type="button" class="primary" data-open-unit-price>단가 수정</button></section>`;
  S.unitPriceDialog = async function (product, onSaved) {
    // Always display the current price/version, even when opened from an older list.
    const latest = await S.request("/products/" + product.id);
    if (product.version != null && latest.version !== product.version && S.dirty) throw Error("다른 작업에서 제품이 변경되었습니다. 작성 중인 내용을 확인하고 화면을 새로 열어 주세요.");
    if (latest.nonStandard) throw Error("비규격 제품의 단가는 가격계산식에서 관리해 주세요.");
    const d = S.dialog("규격 제품 단가 수정", `<p><strong>${S.e(latest.productName)}</strong></p><p class="pms-help">현재 단가: <strong>${S.e(S.unitPriceText(latest.unitPrice))}</strong></p><label class="pm-unit-price-input"><span>변경할 단가 (원)</span><input data-unit-price type="number" min="0" max="1000000000000" step="0.01" inputmode="decimal" value="${S.e(latest.unitPrice)}" placeholder="예: 150000"><small>0~1,000,000,000,000원 · 소수 둘째 자리까지 · 무료는 0 입력</small></label><div data-unit-price-error role="alert"></div><p class="pms-help">저장하면 이 규격 제품의 현재 단가가 바뀝니다. 기존 주문의 금액을 다시 계산하거나 비규격 가격계산식을 변경하지 않습니다.</p>`, { wide: false, foot: '<button type="button" class="primary" data-save-unit-price>단가만 저장</button>' });
    const input = S.$("[data-unit-price]", d), errorBox = S.$("[data-unit-price-error]", d);
    input.focus(); input.select();
    const save = S.$("[data-save-unit-price]", d);
    save.onclick = () => S.run(save, async () => {
      errorBox.textContent = "";
      const value = input.value.trim();
      if (!/^\d+(?:\.\d{1,2})?$/.test(value) || Number(value) > 1000000000000) {
        errorBox.textContent = "단가를 0 이상, 최대 1,000,000,000,000원까지 소수 2자리 이내로 입력해 주세요.";
        input.focus(); return;
      }
      try {
        const saved = await S.request(`/products/${latest.id}/unit-price`, "PUT", { version: latest.version, unitPrice: value });
        d.close(); S.toast("규격 제품 단가를 저장했습니다.");
        await onSaved?.(saved);
      } catch (error) { errorBox.textContent = error.message; }
    });
    input.addEventListener("keydown", (event) => { if (event.key === "Enter") { event.preventDefault(); if (!save.disabled) save.click(); } });
    return d;
  };
})(window.PMS);
