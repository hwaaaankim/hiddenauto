(function (S) {
  "use strict";

  // Shared selection/confirmation behavior for standard, custom and actual-product lists.
  S.bindDeletion = function (root, rows, { custom = false, product = null, onDeleted }) {
    const all = S.$("[data-delete-all]", root),
      button = S.$("[data-delete-button]", root),
      count = S.$("[data-delete-count]", root),
      inputs = S.$$("[data-delete-select]", root),
      selected = new Set();
    let dialogOpen = false,
      sending = false;
    if (!all || !button || !count) return;

    function update() {
      if (!button.isConnected) return;
      const blocked = dialogOpen || sending;
      button.disabled = blocked || selected.size === 0;
      count.textContent = selected.size + "개 선택";
      all.checked = inputs.length > 0 && selected.size === inputs.length;
      all.indeterminate = selected.size > 0 && selected.size < inputs.length;
      all.disabled = blocked || inputs.length === 0;
      for (const input of inputs) {
        input.checked = selected.has(Number(input.dataset.deleteSelect));
        input.disabled = blocked;
        input.closest("tr")?.classList.toggle("pm-row-selected", input.checked);
      }
    }
    for (const input of inputs) {
      input.onchange = () => {
        const id = Number(input.dataset.deleteSelect);
        if (input.checked) selected.add(id);
        else selected.delete(id);
        update();
      };
    }
    all.onchange = () => {
      for (const input of inputs) {
        const id = Number(input.dataset.deleteSelect);
        if (all.checked) selected.add(id);
        else selected.delete(id);
      }
      update();
    };
    button.onclick = () => {
      if (dialogOpen || sending || !selected.size) return;
      if (selected.size > 10000) {
        S.toast("한 번에 최대 10,000개까지 삭제할 수 있습니다. 선택 항목을 나누어 주세요.");
        return;
      }
      const targets = rows.filter((row) => selected.has(row.id)),
        actual = !!product,
        childCount = targets.reduce((sum, row) => sum + (row.actualCount || 0), 0),
        stock = targets.reduce((sum, row) => sum + row.stock, 0),
        kind = actual ? "실 제품" : custom ? "비규격 제품" : "규격 제품";
      const warning = actual
        ? "선택한 실 제품의 실제 옵션 사양, 재고, 입출고 이력 및 첨부파일을 모두 삭제합니다. 원제품의 구성·프로세스, 다른 실 제품과 기존 미배정 재고는 유지됩니다."
        : custom
          ? "선택한 비규격 제품과 등록된 프로세스를 삭제합니다. 각 제품에 포함된 실제 옵션 입력 제품 목록(실 제품), 재고, 입출고 이력 및 제품 전용 이미지·첨부파일도 모두 삭제됩니다."
          : "선택한 규격 제품과 구성 사양, 재고, 입출고·사양 변경 이력 및 제품 전용 이미지·첨부파일을 모두 삭제합니다.";
      dialogOpen = true;
      update();
      const dialog = S.dialog(
        kind + " 삭제 확인",
        `<div class="pms-alert pm-delete-warning"><strong>삭제 후에는 복구할 수 없습니다.</strong><p>${S.e(warning)}</p></div><div class="pm-delete-summary"><strong>${kind} ${targets.length.toLocaleString()}개</strong>${custom && !actual ? `<span>소속 실 제품 ${childCount.toLocaleString()}개도 함께 삭제</span>` : ""}<span>삭제 대상 재고 ${stock.toLocaleString()}개</span></div>${!actual ? '<p class="pms-help">공용 그룹·옵션·FAQ와 다른 제품에서 사용하는 파일은 유지됩니다. 삭제한 제품의 코드·QR 링크는 더 이상 조회할 수 없습니다.</p>' : ""}<ul class="pm-delete-targets">${targets.map((row) => `<li><strong>${S.e(actual ? row.code : row.productName)}</strong><small>${S.e(actual ? row.parentCode : row.catalogCode)} · 재고 ${row.stock.toLocaleString()}개${custom && !actual ? " · 소속 실 제품 " + row.actualCount.toLocaleString() + "개" : ""}</small></li>`).join("")}</ul>`,
        {
          wide: false,
          foot: '<button type="button" data-delete-cancel>취소</button><button type="button" class="danger" data-delete-confirm>삭제</button>',
          onClose: () => {
            dialogOpen = false;
            update();
          },
        },
      );
      dialog.addEventListener("cancel", (event) => {
        if (sending) event.preventDefault();
      });
      S.$("[data-delete-cancel]", dialog).onclick = () => dialog.close();
      S.$("[data-delete-confirm]", dialog).onclick = async () => {
        if (sending) return;
        sending = true;
        update();
        const controls = S.$$("button", dialog),
          body = S.$(".pms-dialog-body", dialog);
        controls.forEach((control) => (control.disabled = true));
        dialog.setAttribute("aria-busy", "true");
        S.clearErrors?.(body);
        let deleted = false;
        try {
          const result = await S.request(
            actual ? "/products/" + product.id + "/actuals/delete" : "/products/delete",
            "POST",
            actual
              ? { productVersion: product.version, items: targets.map((row) => ({ id: row.id, version: row.version })) }
              : { nonStandard: custom, items: targets.map((row) => ({ id: row.id, version: row.version, actualCount: row.actualCount })) },
          );
          deleted = true;
          selected.clear();
          dialog.close();
          await onDeleted(result);
          S.toast(kind + " " + (actual ? result.deletedActuals : result.deletedProducts).toLocaleString() + "개를 삭제했습니다.");
          try {
            await S.overview?.();
          } catch {
            // The deleted list is already refreshed; a summary refresh can be retried later.
          }
        } catch (error) {
          if (deleted) {
            S.notice("삭제는 완료되었지만 목록을 다시 불러오지 못했습니다. 새로고침해 주세요.");
          } else {
            S.showErrors(body, error.fieldErrors || { "": error.message });
            S.toast("삭제하지 못했습니다. 표시된 이유를 확인해 주세요.");
          }
        } finally {
          sending = false;
          if (dialog.isConnected) {
            controls.forEach((control) => (control.disabled = false));
            dialog.removeAttribute("aria-busy");
          }
          update();
        }
      };
      S.$("[data-delete-cancel]", dialog).focus();
    };
    update();
  };
})(window.PMS);
