(function (S) {
  "use strict";
  S.dimensionText = (inputs) => {
    const parts = Object.entries({ widthMm: "W", heightMm: "H", depthMm: "D" })
      .filter(([key]) => inputs?.[key] != null)
      .map(([key, label]) => label + inputs[key]);
    return parts.length ? " " + parts.join("×") : "";
  };
  S.nameText = function (groups, tokens) {
    let result = "";
    for (const token of tokens) {
      const row = groups.find((x) => x.groupId === token.groupId),
        g = S.group(token.groupId);
      if (!g || !row || g.nonStandard) continue;
      let part;
      if (S.isChoice(g.control))
        part = (row.valueIds || [])
          .map((id) => {
            const part = g.values.find((v) => v.id === id)?.namePart ?? "";
            return part.trim() ? part + S.dimensionText(row.inputs) : "";
          })
          .filter((x) => x.trim())
          .join("+");
      else
        part = (g.fields || [])
          .filter((f) => row.inputs?.[f.key] !== undefined)
          .map((f) => (f.namePart || "") + row.inputs[f.key])
          .join("*");
      if (!part.trim()) continue;
      result +=
        (result ? token.before || "" : "") +
        (token.prefix || "") +
        part +
        (token.suffix || "");
    }
    return result;
  };
  S.nameDialog = function (selected, tokens, onSave) {
    let current = S.copy(tokens);
    const dialog = S.dialog("제품명 구성요소 관리", "");
    const body = S.$(".pms-dialog-body", dialog);
    function paint() {
      const candidates = selected
        .map((x) => S.group(x.groupId))
        .filter(
          (g) => g?.includeInName && !current.some((t) => t.groupId === g.id),
        );
      body.innerHTML = `<div class="pms-help">구성요소를 끌어 순서를 바꾸고 각 요소 앞의 구분문자, 요소의 앞·뒤 문자를 지정합니다. 공백은 스페이스로 입력합니다. 구성 문자가 빈 옵션은 구분문자와 괄호까지 함께 생략합니다.</div><div class="pms-split"><section class="pms-panel"><div class="pms-panel-title"><h2>사용 가능한 요소</h2></div><div class="pms-palette">${candidates.map((g) => `<div class="pms-palette-item" draggable="true" data-name-add="${g.id}">${S.e(g.labels.management)} <button data-add-token="${g.id}">+</button></div>`).join("") || "<small>모든 요소가 사용 중입니다.</small>"}</div></section><section><div class="pms-token-list" id="pms-name-tokens">${current.map((t, i) => `<div class="pms-token" draggable="true" data-token-index="${i}"><span class="pms-handle">⠿</span><strong>${S.e(S.group(t.groupId)?.labels.management)}</strong>${S.field("앞 구분문자", "tokens." + i + ".before", t.before)}${S.field("요소 앞 문자", "tokens." + i + ".prefix", t.prefix)}${S.field("요소 뒤 문자", "tokens." + i + ".suffix", t.suffix)}<button data-remove-token="${i}" class="pms-remove">×</button></div>`).join("")}</div><div class="pms-section"><small>현재 선택의 첫 번째 조합 예시</small><div id="pms-name-sample" class="pms-name-preview"></div></div></section></div><div class="pms-actions pms-section"><button class="primary" id="pms-save-tokens">구성 적용</button></div>`;
      const sample = () => {
        S.$("#pms-name-sample", body).textContent =
          S.nameText(
            selected.map((r) => ({
              ...r,
              valueIds: (r.valueIds || []).slice(0, 1),
            })),
            current,
          ) || "(제품명 구성 문자 없음)";
      };
      S.bind(body, { tokens: current }, sample);
      sample();
      const add = (id) => {
        current.push({
          groupId: Number(id),
          before: "-",
          prefix: "",
          suffix: "",
        });
        paint();
      };
      S.$$("[data-add-token]", body).forEach(
        (b) => (b.onclick = () => add(b.dataset.addToken)),
      );
      S.$$("[data-remove-token]", body).forEach(
        (b) =>
          (b.onclick = () => {
            current.splice(Number(b.dataset.removeToken), 1);
            paint();
          }),
      );
      const lane = S.$("#pms-name-tokens", body);
      S.sortable(lane, "[data-token-index]", (from, to) => {
        current.splice(to, 0, current.splice(from, 1)[0]);
        paint();
      });
      S.$$("[data-name-add]", body).forEach(
        (el) =>
          (el.ondragstart = (e) =>
            e.dataTransfer.setData("application/pm-name", el.dataset.nameAdd)),
      );
      lane.addEventListener("dragover", (e) => e.preventDefault());
      lane.addEventListener("drop", (e) => {
        const id = e.dataTransfer.getData("application/pm-name");
        if (id) {
          e.preventDefault();
          add(id);
        }
      });
      S.$("#pms-save-tokens", body).onclick = () => {
        S.dirty = true;
        onSave(current);
        dialog.close();
      };
    }
    paint();
  };
  S.builderPage = async function (root, product) {
    const detail = !!product;
    let selected = detail
      ? S.copy(product.variants)
      : S.groups
          .filter(S.isBase)
          .sort(
            (a, b) =>
              ["CATEGORY", "SUBCATEGORY", "SERIES"].indexOf(a.role) -
              ["CATEGORY", "SUBCATEGORY", "SERIES"].indexOf(b.role),
          )
          .map((g) => ({
            groupId: g.id,
            valueIds: g.values.filter((v) => v.active).map((v) => v.id),
            inputs: {},
          }));
    let tokens = detail
      ? S.copy(product.nameTokens)
      : selected.map((r) => ({
          groupId: r.groupId,
          before: "-",
          prefix: "",
          suffix: "",
        }));
    let productFiles = S.copy(product?.assets || []),
      query = "";
    let model = {
      productName: product?.productName || "",
      description: product?.description || "",
      status: product?.status || "ACTIVE",
    };
    root.innerHTML =
      '<div class="pms-triple"><section class="pms-panel"><div class="pms-panel-title"><h2>구성 가능한 그룹</h2></div><div class="pms-toolbar"><input id="pms-palette-filter" placeholder="그룹 검색"></div><div id="pms-palette" class="pms-palette"></div></section><section class="pms-panel"><div class="pms-panel-title"><div><h2>제품 구성 · 질문 순서</h2><small>그룹을 드래그하여 추가·순서 변경</small></div><button id="pms-name-rule">제품명 구성</button></div><div id="pms-selected" class="pms-selected"></div></section><section class="pms-panel"><div class="pms-panel-title"><h2>' +
      (detail ? "제품 정보" : "생성 요약") +
      '</h2></div><div class="pms-panel-body" id="pms-summary"></div></section></div>';
    const palette = S.$("#pms-palette", root),
      lane = S.$("#pms-selected", root),
      summary = S.$("#pms-summary", root);
    function paintPalette() {
      palette.innerHTML =
        S.groups
          .filter(
            (g) =>
              g.active &&
              !selected.some((r) => r.groupId === g.id) &&
              (!query || g.labels.management.includes(query)),
          )
          .map(
            (g) =>
              `<article class="pms-palette-item" draggable="true" data-palette-group="${g.id}"><strong>${S.e(g.labels.management)}</strong><small>${S.badge(g.nonStandard ? "비규격" : "규격", g.nonStandard ? "amber" : "")}${S.e(S.controls[g.control])}</small><button data-add-group="${g.id}" title="구성에 추가">+</button></article>`,
          )
          .join("") || "<small>추가할 그룹이 없습니다.</small>";
      S.$$("[data-palette-group]", palette).forEach(
        (el) =>
          (el.ondragstart = (e) =>
            e.dataTransfer.setData(
              "application/pm-group",
              el.dataset.paletteGroup,
            )),
      );
      S.$$("[data-add-group]", palette).forEach(
        (b) => (b.onclick = () => add(Number(b.dataset.addGroup))),
      );
    }
    function add(id) {
      const g = S.group(id);
      if (selected.some((r) => r.groupId === id)) return;
      const other = selected
        .map((x) => S.group(x.groupId))
        .filter((x) => !S.isBase(x));
      if (!S.isBase(g) && other.some((x) => x.nonStandard !== g.nonStandard)) {
        S.toast(
          "세 기본그룹을 제외한 구성은 모두 규격 또는 모두 비규격이어야 합니다.",
        );
        return;
      }
      selected.push({
        groupId: id,
        valueIds: g.nonStandard
          ? []
          : g.values.filter((v) => v.active).map((v) => v.id),
        inputs: {},
      });
      if (detail && g.control === "RADIO")
        selected[selected.length - 1].valueIds = selected
          .at(-1)
          .valueIds.slice(0, 1);
      if (g.includeInName)
        tokens.push({ groupId: id, before: "-", prefix: "", suffix: "" });
      S.dirty = true;
      paint();
    }
    lane.addEventListener("dragover", (e) => {
      e.preventDefault();
      lane.classList.add("over");
    });
    lane.addEventListener("dragleave", () => lane.classList.remove("over"));
    lane.addEventListener("drop", (e) => {
      lane.classList.remove("over");
      const id = e.dataTransfer.getData("application/pm-group");
      if (id) {
        e.preventDefault();
        add(Number(id));
      }
    });
    S.sortable(lane, "[data-selected-group]", (from, to) => {
      selected.splice(to, 0, selected.splice(from, 1)[0]);
      paint();
    });
    S.$("#pms-palette-filter", root).oninput = (e) => {
      query = e.target.value;
      paintPalette();
    };
    S.$("#pms-name-rule", root).onclick = () =>
      S.nameDialog(selected, tokens, (value) => {
        tokens = value;
        paintSummary();
      });
    function paint() {
      paintPalette();
      lane.innerHTML = selected
        .map((r, i) => {
          const g = S.group(r.groupId);
          return `<article class="pms-card" draggable="true" data-selected-group="${g.id}"><div class="pms-card-head"><span class="pms-handle">⠿</span><strong>${i + 1}. ${S.e(g.labels.management)}</strong>${S.isBase(g) ? S.badge("필수", "blue") : S.badge(g.nonStandard ? "비규격" : "규격", g.nonStandard ? "amber" : "")}<div class="grow"></div><button data-up="${i}" ${i === 0 ? "disabled" : ""} title="위로">↑</button><button data-down="${i}" ${i === selected.length - 1 ? "disabled" : ""} title="아래로">↓</button>${!S.isBase(g) ? `<button data-remove-group="${g.id}" class="pms-remove">×</button>` : ""}</div>${
            g.nonStandard
              ? `<div class="pms-help">${S.e(S.controls[g.control])} · 제품 생성 후 비규격 프로세스에서 상세 설정</div>`
              : S.isChoice(g.control)
                ? `<div class="pms-row pms-section"><small>${detail ? "이 제품의 옵션을 선택합니다." : "활성 옵션마다 제품을 하나씩 조합합니다."}</small>${!detail ? `<button data-toggle-all="${g.id}:on">전체 활성</button><button data-toggle-all="${g.id}:off">전체 해제</button>` : ""}</div><div class="pms-check-grid">${g.values
                    .filter((v) => v.active || r.valueIds.includes(v.id))
                    .map(
                      (v) =>
                        `<label class="pms-check" title="고객: ${S.e(v.labels.customer)} / 생산: ${S.e(v.labels.production)} / 관리: ${S.e(v.labels.management)}"><input type="${detail && g.control === "RADIO" ? "radio" : "checkbox"}" name="variant-${g.id}" data-value-group="${g.id}" value="${v.id}" ${r.valueIds.includes(v.id) ? "checked" : ""}>${S.e(v.labels.management)}${!v.active ? " (비활성)" : ""}</label>`,
                    )
                    .join(
                      "",
                    )}</div>${g.values.some((v) => r.valueIds.includes(v.id) && ["WIDTH_HEIGHT", "WIDTH_DEPTH_HEIGHT"].includes(v.dimensionType)) ? `<div class="pms-form-grid pms-section">${["widthMm", "heightMm", ...(g.values.some((v) => r.valueIds.includes(v.id) && v.dimensionType === "WIDTH_DEPTH_HEIGHT") ? ["depthMm"] : [])].map((key) => `<label><span>${{ widthMm: "W", heightMm: "H", depthMm: "D" }[key]} (기존 치수형)</span><input type="number" min="1" max="100000" data-fixed-input="${g.id}:${key}" value="${S.e(r.inputs[key])}"></label>`).join("")}</div>` : ""}`
                : `<div class="pms-section" data-fixed-fields="${g.id}">${S.inputAnswer({ key: g.key, control: g.control, fields: g.fields }, { fields: r.inputs })}</div>`
          }</article>`;
        })
        .join("");
      S.$$("[data-value-group]", lane).forEach(
        (input) =>
          (input.onchange = () => {
            const r = selected.find(
              (x) => x.groupId === Number(input.dataset.valueGroup),
            );
            r.valueIds = S.$$(
              `[data-value-group="${r.groupId}"]:checked`,
              lane,
            ).map((x) => Number(x.value));
            const dimensions = S.group(r.groupId).values.filter((v) =>
              r.valueIds.includes(v.id),
            );
            if (
              !dimensions.some((v) =>
                ["WIDTH_HEIGHT", "WIDTH_DEPTH_HEIGHT"].includes(
                  v.dimensionType,
                ),
              )
            )
              r.inputs = {};
            else if (
              !dimensions.some((v) => v.dimensionType === "WIDTH_DEPTH_HEIGHT")
            )
              delete r.inputs.depthMm;
            S.dirty = true;
            paint();
          }),
      );
      S.$$("[data-toggle-all]", lane).forEach(
        (b) =>
          (b.onclick = () => {
            const [id, on] = b.dataset.toggleAll.split(":");
            selected.find((x) => x.groupId === Number(id)).valueIds =
              on === "on"
                ? S.group(id)
                    .values.filter((v) => v.active)
                    .map((v) => v.id)
                : [];
            S.dirty = true;
            paint();
          }),
      );
      S.$$("[data-remove-group]", lane).forEach(
        (b) =>
          (b.onclick = () => {
            const id = Number(b.dataset.removeGroup);
            selected = selected.filter((x) => x.groupId !== id);
            tokens = tokens.filter((x) => x.groupId !== id);
            S.dirty = true;
            paint();
          }),
      );
      for (const direction of ["up", "down"])
        S.$$("[data-" + direction + "]", lane).forEach(
          (b) =>
            (b.onclick = () => {
              const index = Number(b.dataset[direction]);
              selected.splice(
                index + (direction === "up" ? -1 : 1),
                0,
                selected.splice(index, 1)[0],
              );
              S.dirty = true;
              paint();
            }),
        );
      S.$$("[data-fixed-input]", lane).forEach(
        (input) =>
          (input.oninput = () => {
            const [id, key] = input.dataset.fixedInput.split(":");
            const inputs = selected.find(
              (x) => x.groupId === Number(id),
            ).inputs;
            if (input.value === "") delete inputs[key];
            else inputs[key] = Number(input.value);
            S.dirty = true;
            paintSummary();
          }),
      );
      S.$$("[data-fixed-fields]", lane).forEach((el) => {
        const row = selected.find(
            (x) => x.groupId === Number(el.dataset.fixedFields),
          ),
          g = S.group(row.groupId);
        el.oninput = () => {
          if (g.control === "FILE") return;
          row.inputs = S.readAnswer(
            el,
            { control: g.control },
            { fields: row.inputs },
          ).fields;
          S.dirty = true;
          paintSummary();
        };
        S.$$("input[type=file]", el).forEach(
          (input) =>
            (input.onchange = () =>
              S.run(null, async () => {
                const fd = new FormData();
                for (const f of input.files) fd.append("files", f);
                const uploaded = await S.request("/assets/stage", "POST", fd);
                row.inputs[input.dataset.answerField] = uploaded.map(
                  (f) => f.id,
                );
                S.$(
                  `[data-answer-files="${input.dataset.answerField}"]`,
                  el,
                ).textContent = uploaded.length + "개 파일 업로드됨";
                S.dirty = true;
                paintSummary();
              })),
        );
      });
      paintSummary();
    }
    function paintSummary() {
      const custom = selected.some((r) => S.group(r.groupId).nonStandard);
      let count = 1;
      for (const r of selected) {
        const g = S.group(r.groupId);
        if (!g.nonStandard && S.isChoice(g.control)) count *= r.valueIds.length;
      }
      summary.innerHTML = detail
        ? `${S.badge(S.status[product.status], product.status === "DRAFT" ? "amber" : "blue")}<p class="mono">${S.e(product.catalogCode)} · #${product.id}</p><div class="pms-form-grid two"><div class="wide">${S.field("제품명", "productName", model.productName, "text", 'maxlength="160"')}</div><button id="pms-regenerate-name">규칙으로 제품명 생성</button><div class="wide">${S.field("제품 설명", "description", model.description, "text", 'maxlength="1000"')}</div><div class="wide">${S.select("상태", "status", model.status, { ACTIVE: "사용중 (재고로 자동 판단)", OUT_OF_STOCK: "재고없음 (재고로 자동 판단)", DISCONTINUED: "단종", ...(product.status === "DRAFT" ? { DRAFT: "등록중" } : {}) })}</div></div><section class="pms-section"><h2>제품 이미지·첨부</h2><div id="pms-product-files">${S.files(productFiles)}</div></section><div class="pms-section pms-actions"><button class="primary" id="pms-save-product">변경 저장</button>${custom ? `<a class="pms-button" href="/admin/product-master/products/${product.id}/process">비규격 프로세스</a>` : ""}<button id="pms-stock">재고 관리 · ${product.stock}</button><a class="pms-button" href="/product-spec/${S.e(product.token)}" target="_blank" rel="noopener">고객 미리보기</a></div>`
        : `<div class="pms-stat"><small>생성될 조합 수</small><strong>${count.toLocaleString()}</strong>${S.badge(custom ? "비규격 제품" : "규격 제품", custom ? "amber" : "blue")}</div><h3>제품명 예시</h3><p class="pms-name-preview">${S.e(
            S.nameText(
              selected.map((r) => ({ ...r, valueIds: r.valueIds.slice(0, 1) })),
              tokens,
            ) || "(구성 문자를 확인해 주세요)",
          )}</p><div class="pms-help">기본그룹은 필수입니다. 그룹마다 활성화된 보기 하나씩을 뽑아 조합합니다. 한 번에 최대 10,000개까지 생성할 수 있습니다.</div>${custom ? '<div class="pms-help">비규격은 등록중으로 생성됩니다. 각 제품에서 보기·입력 필드·관계 규칙을 설정하고 검증해야 등록완료됩니다.</div>' : ""}<button class="primary" id="pms-generate" ${count < 1 || count > 10000 ? "disabled" : ""}>조합 미리보기 · 자동 생성</button>`;
      if (detail) {
        S.bind(summary, model);
        S.fileEvents(S.$("#pms-product-files", summary), productFiles);
        S.$("#pms-regenerate-name", summary).onclick = () => {
          model.productName = S.nameText(selected, tokens);
          S.dirty = true;
          paintSummary();
        };
        S.$("#pms-save-product", summary).onclick = (e) =>
          S.run(e.currentTarget, async () => {
            const saved = await S.request("/products/" + product.id, "PUT", {
              version: product.version,
              ...model,
              variants: selected,
              nameTokens: tokens,
              assetIds: productFiles
                .filter((f) => !f.id.startsWith("legacy-"))
                .map((f) => f.id),
            });
            product = saved;
            S.dirty = false;
            S.notice(
              "제품을 저장했습니다. 동일 제품 여부를 다시 확인했습니다.",
              true,
            );
            await S.builderPage(root, saved);
          });
        S.$("#pms-stock", summary).onclick = () => stockDialog();
      } else
        S.$("#pms-generate", summary).onclick = (e) =>
          S.run(e.currentTarget, () =>
            preview({
              groups: S.copy(selected),
              nameTokens: S.copy(tokens),
              offset: 0,
              limit: 200,
            }),
          );
    }
    async function stockDialog() {
      const state = { movementType: "INBOUND", quantityDelta: 0, reason: "" };
      const dialog = S.dialog(
        "제품 재고 관리",
        `<p>현재 재고 <strong>${product.stock.toLocaleString()}</strong></p><div class="pms-form-grid">${S.select("변경 유형", "movementType", state.movementType, { INBOUND: "입고", OUTBOUND: "출고", RETURN: "반품입고", DAMAGE: "파손·폐기", ADJUSTMENT: "재고조정" })}${S.field("변경 수량 (출고는 음수)", "quantityDelta", 0, "number", 'step="1"')}${S.field("변경 사유", "reason", "")}</div><p class="pms-help">출고·파손은 음수, 입고·반품은 양수로 입력합니다. 현재 재고에서 더하거나 뺄 수량입니다.</p>`,
        {
          wide: false,
          foot: '<button class="primary" data-save-stock>재고 반영</button>',
        },
      );
      S.bind(dialog, state);
      S.$("[data-save-stock]", dialog).onclick = (e) =>
        S.run(e.currentTarget, async () => {
          await S.api(
            `/admin/api/product-master/products/${product.id}/stock-movements`,
            "POST",
            { ...state, addonQuantities: [] },
          );
          product = await S.request("/products/" + product.id);
          dialog.close();
          S.dirty = false;
          paintSummary();
          S.toast("재고를 반영했습니다.");
        });
    }
    async function preview(generation) {
      let rows = [],
        page = 0,
        stock = 0,
        canceled = false;
      const controller = new AbortController();
      const dialog = S.dialog(
        "제품 자동생성 미리보기",
        '<p>조합을 계산하고 중복을 확인하고 있습니다.</p><div class="pms-progress"></div><p id="pms-preview-progress"></p>',
        {
          onClose: () => {
            canceled = true;
            controller.abort();
          },
        },
      );
      const body = S.$(".pms-dialog-body", dialog);
      let stamp = "";
      try {
        let total = 1;
        while (rows.length < total) {
          const response = await S.request(
            "/preview",
            "POST",
            { ...generation, offset: rows.length },
            controller.signal,
          );
          if (stamp && stamp !== response.definitionStamp)
            throw Error(
              "생성 중 옵션이 변경되었습니다. 미리보기를 다시 실행해 주세요.",
            );
          stamp = response.definitionStamp;
          total = response.combinations;
          rows.push(
            ...response.rows.map((r) => ({
              ...r,
              initialStock: 0,
              files: [],
              removed: false,
            })),
          );
          S.$("#pms-preview-progress", body).textContent =
            rows.length.toLocaleString() + " / " + total.toLocaleString();
        }
        if (!canceled) paintPreview();
      } catch (e) {
        if (e.name !== "AbortError") {
          body.innerHTML = `<div class="pms-alert">${S.e(e.message)}</div>`;
        }
      }
      function paintPreview() {
        const live = rows.filter((r) => !r.removed),
          dupes = live.filter((r) => r.duplicateId);
        const pages = Math.ceil(live.length / 50);
        page = Math.max(0, Math.min(page, pages - 1));
        const visible = live.slice(page * 50, page * 50 + 50);
        body.innerHTML = `<div class="pms-toolbar"><strong>${live.length.toLocaleString()}개 등록 예정</strong>${S.badge("중복 " + dupes.length, dupes.length ? "red" : "green")}<div class="grow"></div><label>전체 최초재고 <input id="pms-all-stock" type="number" min="0" max="10000000" value="${stock}" style="width:80px"></label><button id="pms-set-stock">일괄 적용</button><button id="pms-remove-duplicates" ${!dupes.length ? "disabled" : ""}>중복 행 모두 제거</button><button class="primary" id="pms-register" ${dupes.length || !live.length ? "disabled" : ""}>등록 · ${live.length}개</button></div><div class="pms-help">×는 미리보기에서만 행을 제외합니다. 닫고 조건을 바꾸어 다시 생성할 수 있습니다. 같은 제품명이 있어도 실제 사양이 다르면 등록할 수 있습니다.</div><div class="pms-table-scroll"><table><thead><tr><th>#</th><th style="min-width:200px">제품명</th><th>구성 / 주체별 표시</th><th>최초재고</th><th>제품 이미지·첨부</th><th>중복 확인</th><th></th></tr></thead><tbody>${visible
          .map((r) => {
            const index = rows.indexOf(r);
            return `<tr class="${r.duplicateId ? "duplicate" : ""}"><td>${index + 1}</td><td><input data-row-name="${index}" maxlength="160" value="${S.e(r.productName)}"></td><td><details><summary>${r.variants
              .map((v) => {
                const g = S.group(v.groupId);
                return S.e(
                  (v.valueIds || [])
                    .map(
                      (id) =>
                        g.values.find((o) => o.id === id)?.labels.management,
                    )
                    .join("+") ||
                    (g.nonStandard
                      ? g.labels.management + "(커스텀)"
                      : Object.values(v.inputs).join("*")),
                );
              })
              .join(
                " / ",
              )}</summary><table><thead><tr><th>그룹</th><th>고객</th><th>생산</th><th>관리</th></tr></thead><tbody>${r.variants
              .map((v) => {
                const g = S.group(v.groupId),
                  options = v.valueIds.map((id) =>
                    g.values.find((o) => o.id === id),
                  );
                return `<tr><td>${S.e(g.labels.management)}</td>${["customer", "production", "management"].map((a) => `<td>${S.e((options.length ? options.map((o) => o.labels[a]).join("+") + S.dimensionText(v.inputs) : "") || (g.nonStandard ? "고객 커스텀" : Object.values(v.inputs).join("*")))}</td>`).join("")}</tr>`;
              })
              .join(
                "",
              )}</tbody></table></details></td><td><input data-row-stock="${index}" type="number" min="0" max="10000000" style="width:70px" value="${r.initialStock}"></td><td><button data-row-files="${index}">첨부 ${r.files.length}</button></td><td>${r.duplicateId ? `<a href="/admin/product-master/products/${r.duplicateId}" target="_blank" rel="noopener">중복 #${r.duplicateId}</a><small>${S.e(r.duplicateName)}</small>` : S.badge("신규", "green")}</td><td><button data-remove-row="${index}" class="pms-remove">×</button></td></tr>`;
          })
          .join(
            "",
          )}</tbody></table></div>${S.pager(page, pages, "preview-page")}`;
        S.$$("[data-row-name]", body).forEach(
          (input) =>
            (input.oninput = () =>
              (rows[Number(input.dataset.rowName)].productName = input.value)),
        );
        S.$$("[data-row-stock]", body).forEach(
          (input) =>
            (input.oninput = () =>
              (rows[Number(input.dataset.rowStock)].initialStock = Number(
                input.value,
              ))),
        );
        S.$$("[data-remove-row]", body).forEach(
          (b) =>
            (b.onclick = () => {
              rows[Number(b.dataset.removeRow)].removed = true;
              paintPreview();
            }),
        );
        S.$$("[data-preview-page]", body).forEach(
          (b) =>
            (b.onclick = () => {
              page = Number(b.dataset.previewPage);
              paintPreview();
            }),
        );
        S.$("#pms-remove-duplicates", body).onclick = () => {
          rows.forEach((r) => {
            if (r.duplicateId) r.removed = true;
          });
          paintPreview();
        };
        S.$("#pms-set-stock", body).onclick = () => {
          stock = Number(S.$("#pms-all-stock", body).value);
          if (!Number.isInteger(stock) || stock < 0 || stock > 10000000)
            return S.toast("최초재고는 0~10,000,000 정수입니다.");
          live.forEach((r) => (r.initialStock = stock));
          paintPreview();
        };
        S.$$("[data-row-files]", body).forEach(
          (b) =>
            (b.onclick = () => {
              const r = rows[Number(b.dataset.rowFiles)];
              const fileDialog = S.dialog(
                "제품 첨부 · " + r.productName,
                S.files(r.files),
                { wide: false, onClose: paintPreview },
              );
              S.fileEvents(S.$(".pms-dialog-body", fileDialog), r.files);
            }),
        );
        S.$("#pms-register", body).onclick = (e) =>
          S.run(e.currentTarget, async () => {
            if (
              live.some(
                (r) => !r.productName.trim() || r.productName.length > 160,
              )
            )
              throw Error("제품명을 1~160자로 입력해 주세요.");
            const ids = await S.request("/register", "POST", {
              generation,
              definitionStamp: stamp,
              rows: live.map((r) => ({
                key: r.key,
                productName: r.productName,
                initialStock: r.initialStock,
                assetIds: r.files.map((f) => f.id),
              })),
            });
            S.dirty = false;
            dialog.close();
            S.notice(
              ids.length.toLocaleString() + "개 제품을 등록했습니다.",
              true,
            );
            const custom = selected.some((r) => S.group(r.groupId).nonStandard);
            location.href = custom
              ? "/admin/product-master/non-standard"
              : "/admin/product-master/products";
          });
      }
    }
    paint();
    S.dirty = false;
  };
  S.listPage = async function (root, custom) {
    let filter = {
      nonStandard: custom,
      keyword: "",
      status: "",
      options: {},
      customGroups: [],
      inputs: [],
      page: 0,
      size: 50,
    };
    root.innerHTML = `<section class="pms-panel"><div class="pms-panel-title"><div><h2>${custom ? "비규격" : "규격"} 제품 목록</h2><small>같은 그룹의 선택은 OR, 서로 다른 그룹은 AND 조건입니다.</small></div><a class="pms-button" href="/admin/product-master/products/new">+ 제품 생성</a></div><div class="pms-panel-body"><div class="pms-form-grid four"><label><span>제품명 검색</span><input id="pms-keyword" maxlength="160" placeholder="제품명 일부"></label><label><span>상태</span><select id="pms-status"><option value="">전체</option>${Object.entries(
      S.status,
    )
      .filter(([k]) => custom || k !== "DRAFT")
      .map(([k, v]) => `<option value="${k}">${v}</option>`)
      .join(
        "",
      )}</select></label><div class="pms-row"><button class="primary" id="pms-search">조회</button><button id="pms-reset">초기화</button><button id="pms-advanced">고급검색</button></div><label><span>페이지 크기</span><select id="pms-page-size"><option>20</option><option selected>50</option><option>100</option><option>200</option></select></label></div><div id="pms-basic-filters">${S.groups
      .filter(S.isBase)
      .map((g) => filterGroup(g))
      .join(
        "",
      )}</div><div id="pms-filter-summary" class="pms-check-grid"></div></div><div id="pms-products"></div></section>`;
    function filterGroup(g) {
      const current = filter.options[g.id] || [];
      return `<div class="pms-filter-group"><strong>${S.e(g.labels.management)}</strong><div class="pms-check-grid">${g.values.map((v) => `<label class="pms-check"><input type="checkbox" data-filter-group="${g.id}" value="${v.id}" ${current.includes(v.id) ? "checked" : ""}>${S.e(v.labels.management)}</label>`).join("") || "<small>등록된 옵션 없음</small>"}</div></div>`;
    }
    const content = S.$("#pms-products", root);
    function bindFilters(el) {
      S.$$("[data-filter-group]", el).forEach(
        (input) =>
          (input.onchange = () => {
            const id = input.dataset.filterGroup;
            filter.options[id] = S.$$(
              `[data-filter-group="${id}"]:checked`,
              el,
            ).map((x) => Number(x.value));
          }),
      );
    }
    bindFilters(S.$("#pms-basic-filters", root));
    S.$("#pms-advanced", root).onclick = () => {
      const before = S.copy(filter);
      let applied = false;
      const dialog = S.dialog(
        "고급 검색",
        custom
          ? `<p class="pms-help">선택한 커스텀 그룹을 모두 포함하는 제품을 찾습니다.</p><div class="pms-check-grid">${S.groups
              .filter((g) => g.nonStandard)
              .map(
                (g) =>
                  `<label class="pms-check"><input type="checkbox" data-filter-custom="${g.id}" ${filter.customGroups.includes(g.id) ? "checked" : ""}>${S.e(g.labels.management)}</label>`,
              )
              .join("")}</div>`
          : S.groups
              .filter(
                (g) => !S.isBase(g) && !g.nonStandard && S.isChoice(g.control),
              )
              .map(filterGroup)
              .join("") + inputFilterHtml(),
        {
          foot: '<button id="pms-apply-filter" class="primary">필터 적용·조회</button>',
          onClose: () => {
            if (!applied) filter = before;
          },
        },
      );
      bindFilters(dialog);
      S.$("#pms-apply-filter", dialog).onclick = () => {
        if (custom)
          filter.customGroups = S.$$(
            "[data-filter-custom]:checked",
            dialog,
          ).map((x) => Number(x.dataset.filterCustom));
        else
          filter.inputs = S.$$("[data-input-filter]", dialog)
            .map((el) => ({
              groupId: Number(el.dataset.groupId),
              fieldKey: el.dataset.fieldKey,
              min: S.$("[data-input-min]", el)?.value
                ? Number(S.$("[data-input-min]", el).value)
                : null,
              max: S.$("[data-input-max]", el)?.value
                ? Number(S.$("[data-input-max]", el).value)
                : null,
              contains: S.$("[data-input-contains]", el)?.value || "",
            }))
            .filter((x) => x.min !== null || x.max !== null || x.contains);
        applied = true;
        dialog.close();
        filter.page = 0;
        load();
      };
    };
    function inputFilterHtml() {
      return S.groups
        .filter(
          (g) =>
            !g.nonStandard && !S.isChoice(g.control) && g.control !== "FILE",
        )
        .map(
          (g) =>
            `<section class="pms-filter-group"><strong>${S.e(g.labels.management)}</strong>${g.fields
              .map((f) => {
                const saved =
                  filter.inputs.find(
                    (x) => x.groupId === g.id && x.fieldKey === f.key,
                  ) || {};
                return `<div class="pms-row" data-input-filter data-group-id="${g.id}" data-field-key="${S.e(f.key)}"><span>${S.e(f.labels.management)}</span>${g.control === "NUMBER" ? `<input data-input-min type="number" step="0.001" placeholder="최소" value="${S.e(saved.min)}" style="width:100px"> ~ <input data-input-max type="number" step="0.001" placeholder="최대" value="${S.e(saved.max)}" style="width:100px">` : `<input data-input-contains placeholder="포함하는 문자" value="${S.e(saved.contains)}" style="width:220px">`}</div>`;
              })
              .join("")}</section>`,
        )
        .join("");
    }
    async function load() {
      filter.keyword = S.$("#pms-keyword", root).value;
      filter.status = S.$("#pms-status", root).value;
      filter.size = Number(S.$("#pms-page-size", root).value);
      content.innerHTML = '<div class="pms-progress"></div>';
      try {
        const data = await S.request("/products/search", "POST", filter);
        filter.page = data.page;
        content.innerHTML = `<div class="pms-toolbar"><strong>총 ${data.totalElements.toLocaleString()}개</strong><small>${data.totalPages ? data.page + 1 : 0} / ${data.totalPages} 페이지</small></div><div class="pms-table-scroll"><table><thead><tr><th>ID</th><th>제품명 / 코드</th><th>기본 분류</th><th>${custom ? "커스텀 가능 항목" : "구성 사양"}</th>${custom ? "<th>등록 상태</th>" : ""}<th>제품 상태</th><th>재고</th></tr></thead><tbody>${data.content.map((p) => `<tr data-open="${p.id}" tabindex="0"><td>${p.id}</td><td><strong>${S.e(p.productName)}</strong><div class="mono">${S.e(p.catalogCode)}</div>${p.legacy ? S.badge("기존 제품") : ""}</td><td>${describe(p, true)}</td><td>${describe(p, false)}</td>${custom ? `<td>${S.badge(p.registrationStatus, p.status === "DRAFT" ? "amber" : "green")}</td>` : ""}<td>${S.badge(S.status[p.status], p.status === "ACTIVE" ? "green" : p.status === "DRAFT" ? "amber" : "")}</td><td>${p.stock.toLocaleString()}</td></tr>`).join("") || '<tr><td colspan="7"><div class="pms-empty">검색 조건에 맞는 제품이 없습니다.</div></td></tr>'}</tbody></table></div>${S.pager(data.page, data.totalPages)}`;
        S.$$("[data-open]", content).forEach((tr) => {
          tr.onclick = () =>
            (location.href =
              "/admin/product-master/products/" + tr.dataset.open);
          tr.onkeydown = (e) => {
            if (e.key === "Enter") tr.click();
          };
        });
        S.$$("[data-page]", content).forEach(
          (b) =>
            (b.onclick = () => {
              filter.page = Number(b.dataset.page);
              load();
            }),
        );
        S.$("#pms-filter-summary", root).innerHTML = custom
          ? filter.customGroups
              .map((id) =>
                S.badge(S.group(id)?.labels.management || id, "blue"),
              )
              .join("")
          : Object.entries(filter.options)
              .filter(([id]) => !S.isBase(S.group(id) || {}))
              .flatMap(([id, ids]) =>
                ids.map((v) =>
                  S.badge(
                    (S.group(id)?.labels.management || id) +
                      ": " +
                      (S.group(id)?.values.find((x) => x.id === v)?.labels
                        .management || v),
                    "blue",
                  ),
                ),
              )
              .join("");
      } catch (e) {
        content.innerHTML = `<div class="pms-alert">${S.e(e.message)}</div>`;
      }
    }
    function describe(p, base) {
      return p.variants
        .filter((v) => S.isBase(S.group(v.groupId) || {}) === base)
        .map((v) => {
          const g = S.group(v.groupId);
          if (!g) return "";
          return `<div><small>${S.e(g.labels.management)}</small> ${g.nonStandard ? S.badge(S.controls[g.control], "blue") : S.e(v.valueIds.map((id) => g.values.find((x) => x.id === id)?.labels.management || id).join("+") || Object.values(v.inputs || {}).join("*"))}</div>`;
        })
        .join("");
    }
    S.$("#pms-search", root).onclick = () => {
      filter.page = 0;
      load();
    };
    S.$("#pms-keyword", root).onkeydown = (e) => {
      if (e.key === "Enter") {
        filter.page = 0;
        load();
      }
    };
    S.$("#pms-status", root).onchange = () => {
      filter.page = 0;
      load();
    };
    S.$("#pms-page-size", root).onchange = () => {
      filter.page = 0;
      load();
    };
    S.$("#pms-reset", root).onclick = () => S.listPage(root, custom);
    await load();
    S.dirty = false;
  };
})(window.PMS);
