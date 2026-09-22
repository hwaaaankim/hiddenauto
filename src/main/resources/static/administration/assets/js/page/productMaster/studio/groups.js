(function (S) {
  "use strict";
  S.groupsPage = async function (root) {
    let selected = null,
      model = null,
      groupFiles = [],
      draftValues = [],
      valueFiles = new Map(),
      filter = "",
      groupDirty = false;
    root.innerHTML =
      '<div class="pms-split"><section class="pms-panel"><div class="pms-panel-title"><h2>그룹 목록</h2><button id="pms-new-group" class="primary">+ 그룹</button></div><div class="pms-toolbar"><input id="pms-group-filter" placeholder="그룹 검색"></div><div id="pms-group-list" class="pms-palette"></div></section><section class="pms-panel"><div id="pms-group-editor"></div></section></div>';
    const list = S.$("#pms-group-list", root),
      editor = S.$("#pms-group-editor", root);
    function paintList() {
      list.innerHTML = S.groups
        .filter(
          (g) =>
            !filter ||
            [g.labels.customer, g.labels.production, g.labels.management].some(
              (x) => x.includes(filter),
            ),
        )
        .map(
          (g) =>
            `<article  data-group="${g.id}" class="pms-palette-item ${g.id === selected ? "active" : ""}"><strong><span class="pms-handle">⠿</span> ${S.e(g.labels.management)}</strong><small>${S.isBase(g) ? S.badge("기본", "blue") : S.badge(g.nonStandard ? "비규격" : "규격", g.nonStandard ? "amber" : "")}${S.e(S.controls[g.control])} · ${g.values.length}개</small>${!g.active ? S.badge("비활성", "red") : ""}</article>`,
        )
        .join("");
    }
    list.onclick = async (e) => {
      const item = e.target.closest("[data-group]");
      if (!item) return;
      if (
        S.dirty &&
        !(await S.confirm(
          "저장하지 않은 변경을 닫고 다른 그룹을 여시겠습니까?",
        ))
      )
        return;
      open(Number(item.dataset.group));
    };
    S.sortable(list, "[data-group]", async (from, to) => {
      if (filter) {
        paintList();
        S.dirty = list._pmsDirtyBeforeDrag;
        S.toast("순서 변경은 검색어를 비운 뒤 진행해 주세요.");
        return;
      }
      const ids = S.groups.map((g) => g.id);
      ids.splice(to, 0, ids.splice(from, 1)[0]);
      try {
        S.groups = await S.request("/groups/reorder", "POST", ids);
        if (model?.id) model.version = S.group(model.id).version;
        paintList();
        S.dirty = list._pmsDirtyBeforeDrag;
      } finally {
        paintList();
      }
    });
    S.$("#pms-group-filter", root).oninput = (e) => {
      filter = e.target.value.trim();
      paintList();
    };
    S.$("#pms-new-group", root).onclick = async () => {
      if (S.dirty && !(await S.confirm("저장하지 않은 변경을 닫으시겠습니까?")))
        return;
      open(null);
    };
    function open(id) {
      selected = id;
      const g = S.group(id);
      model = g
        ? {
            id: g.id,
            version: g.version,
            labels: S.copy(g.labels),
            role: g.role,
            control: g.control,
            nonStandard: g.nonStandard,
            askQuestion: g.askQuestion,
            priceImpact: g.priceImpact,
            includeInName: g.includeInName,
            active: g.active,
            question: g.question || "",
            guide: g.guide || "",
            fields: S.copy(g.fields),
          }
        : {
            id: null,
            version: null,
            labels: { customer: "", production: "", management: "" },
            role: "GENERAL",
            control: "RADIO",
            nonStandard: false,
            askQuestion: true,
            priceImpact: false,
            includeInName: true,
            active: true,
            question: "",
            guide: "",
            fields: [],
          };
      groupFiles = S.copy(g?.assets || []);
      draftValues = S.copy(g?.values || []).map((v) => ({
        id: v.id,
        version: v.version,
        labels: v.labels,
        namePart: v.namePart,
        guide: v.guide,
        active: v.active,
        key: v.key,
      }));
      valueFiles = new Map(
        (g?.values || []).map((v) => [v.id, S.copy(v.assets)]),
      );
      S.dirty = false;
      groupDirty = false;
      paintList();
      paintEditor();
    }
    function locked(label, path, value, reason) {
      return `<div class="pms-locked-setting">${label ? `<label><span>${S.e(label)}</span></label>` : ""}<button type="button" class="pms-locked-button" data-lock="${path}" aria-expanded="false" aria-controls="pms-lock-${path}">${S.e(value)} <span aria-hidden="true">ⓘ</span></button><div id="pms-lock-${path}" class="pms-lock-reason" role="status" hidden>${S.e(reason)}</div></div>`;
    }
    function validateGroup() {
      const errors = {};
      S.checkLabels(model.labels, "", 80, errors);
      if (!model.nonStandard)
        S.checkFields(model.fields, model.control, errors);
      for (const key of ["customer", "production", "management"]) {
        const canonical = (x) =>
          (x || "").normalize("NFC").trim().toLowerCase();
        if (
          model.labels[key]?.trim() &&
          S.groups.some(
            (g) =>
              g.id !== model.id &&
              canonical(g.labels[key]) === canonical(model.labels[key]),
          )
        )
          errors["labels." + key] = "같은 표시명을 사용하는 그룹이 있습니다.";
      }
      const body = S.$(".pms-panel-body", editor);
      S.showErrors(body, errors);
      return !Object.keys(errors).length;
    }
    function validateOptions(requireAny = false, focus = true) {
      const box = S.$("#pms-values", editor);
      if (!box) return true;
      const errors = {};
      if (requireAny && !draftValues.length)
        errors[""] = "등록할 보기를 + 보기 버튼으로 추가해 주세요.";
      draftValues.forEach((v, i) => {
        S.checkLabels(v.labels, "values." + i + ".", 120, errors);
        if ((v.namePart || "").length > 160)
          errors["values." + i + ".namePart"] =
            "제품명 구성 문자는 160자 이하입니다.";
      });
      S.checkUniqueLabels(draftValues, "values.", errors);
      S.showErrors(box, errors, focus);
      return !Object.keys(errors).length;
    }
    function paintEditor() {
      const base = S.isBase(model);
      editor.innerHTML = `<header class="pms-panel-title"><div><h2>${model.id ? "그룹 수정" : "새 그룹"} ${S.badge(base ? "필수그룹" : "옵션그룹", base ? "blue" : "")}</h2><small class="mono">${S.e(S.group(model.id)?.key || "내부 value는 저장할 때 자동 생성됩니다.")}</small></div><div class="pms-actions">${model.id && !base ? '<button class="danger" id="pms-delete-group">삭제</button>' : ""}<button class="primary" id="pms-save-group">그룹 저장</button></div></header><div class="pms-panel-body">${S.labels(model, "")}<div class="pms-help">고객명을 입력하면 아직 별도로 수정하지 않은 생산팀·관리팀 표시명도 같은 값으로 채웁니다. 내부 value는 표시명과 독립적으로 유지됩니다.</div><div class="pms-form-grid two">${base ? locked("입력 방식", "control", "하나 선택 · 필수그룹", "대분류·중분류·시리즈는 제품마다 하나의 분류가 필요하므로 하나선택형으로 고정되어 변경할 수 없습니다.") : S.select("입력 방식", "control", model.control, S.controls)}${base ? locked("비규격용 그룹", "nonStandard", "규격 전용 · 필수그룹", "대분류·중분류·시리즈는 모든 제품의 고정 분류이므로 비규격용 그룹으로 변경할 수 없습니다.") : `<div class="pms-row">${S.check("비규격용 그룹", "nonStandard", model.nonStandard, "pms-switch")}</div>`}<div class="wide pms-row">${S.check("제품명 자동생성에 포함", "includeInName", model.includeInName, "pms-switch")}${base ? locked("", "active", "필수그룹 · 항상 사용", "모든 제품에 필요한 필수그룹이므로 사용을 중지할 수 없습니다.") : S.check("새 제품 구성에 사용", "active", model.active, "pms-switch")}</div>${S.check("고객 질문에 포함", "askQuestion", model.askQuestion)}${model.nonStandard ? S.check("제품 단가에 영향", "priceImpact", model.priceImpact) : ""}${S.field("챗봇 질문", "question", model.question, "text", 'maxlength="300"')}${S.field("고객 도움말", "guide", model.guide, "text", 'maxlength="1000"')}</div>${model.nonStandard ? '<div class="pms-help">비규격 그룹은 입력 방식만 저장합니다. 입력 필드·보기·조건은 생성된 비규격 제품의 프로세스에서 각각 설정합니다.</div>' : !S.isChoice(model.control) ? `<section class="pms-section"><div class="pms-row"><h2>입력 필드</h2><button id="pms-add-field">+ 필드</button></div><div id="pms-fields">${S.fieldRows(model.fields, model.control)}</div></section>` : ""}<details class="pms-section pms-attachment-section"><summary>그룹 이미지·첨부파일 <span class="pms-badge">${groupFiles.length}</span></summary><div id="pms-group-files">${S.files(groupFiles)}</div></details></div>${model.id && !model.nonStandard && S.isChoice(model.control) ? `<div class="pms-panel-title"><div><h2>옵션 관리</h2><small>드래그로 순서 변경 · +로 여러 보기를 추가한 뒤 한 번에 저장</small></div><div class="pms-actions"><button id="pms-add-value">+ 보기</button><button class="primary" id="pms-save-values">옵션 저장</button></div></div><div class="pms-panel-body" id="pms-values"></div>` : !model.id && S.isChoice(model.control) && !model.nonStandard ? '<div class="pms-help">그룹을 저장하면 아래에서 보기를 여러 개 등록할 수 있습니다.</div>' : ""}`;
      S.$$("[data-lock]", editor).forEach(
        (button) =>
          (button.onclick = () => {
            const note = S.$("#pms-lock-" + button.dataset.lock, editor);
            note.hidden = !note.hidden;
            button.setAttribute("aria-expanded", String(!note.hidden));
          }),
      );
      // Bind only once to the fresh body, avoiding stacked handlers on the persistent editor.
      const body = S.$(".pms-panel-body", editor);
      S.bind(body, model, (path, value) => {
        groupDirty = true;
        if (["control", "nonStandard"].includes(path)) {
          if (S.isBase(model)) {
            model.control = "RADIO";
            model.nonStandard = false;
            model.active = true;
          }
          if (!model.nonStandard) model.priceImpact = false;
          if (model.nonStandard || S.isChoice(model.control)) model.fields = [];
          else if (!model.fields.length) model.fields = [S.newField()];
          paintEditor();
        }
      });
      if (S.$("#pms-fields", editor)) {
        const fields = S.$("#pms-fields", editor);
        S.fieldEvents(fields, model.fields, () => {
          groupDirty = true;
          paintEditor();
        });
        S.$("#pms-add-field", editor).onclick = () => {
          if (model.fields.length >= 20)
            return S.toast("입력 필드는 최대 20개입니다.");
          model.fields.push(S.newField());
          groupDirty = true;
          S.dirty = true;
          paintEditor();
        };
      }
      S.fileEvents(S.$("#pms-group-files", editor), groupFiles, () => {
        groupDirty = true;
      });
      S.$("#pms-save-group", editor).onclick = (e) =>
        S.run(e.currentTarget, async () => {
          const groupValid = validateGroup();
          const optionsValid = validateOptions(false, groupValid);
          if (!groupValid || !optionsValid) return;
          const {
            id,
            version,
            labels,
            control,
            nonStandard,
            askQuestion,
            priceImpact,
            includeInName,
            active,
            question,
            guide,
            fields,
          } = model;
          const saved = await S.requestAt(body, "/groups", "POST", {
            id,
            version,
            labels,
            role: base ? model.role : "GENERAL",
            control,
            nonStandard,
            askQuestion,
            priceImpact,
            includeInName,
            active,
            question,
            guide,
            fields,
          });
          model.id = saved.id;
          groupDirty = false;
          model.version = saved.version;
          selected = saved.id;
          await S.request(
            `/assets/GROUP/${saved.id}`,
            "PUT",
            groupFiles.map((f) => f.id),
          );
          if (draftValues.length) await saveOptionDrafts();
          await S.catalog();
          open(saved.id);
          S.notice("그룹과 옵션을 저장했습니다.", true);
        });
      if (S.$("#pms-delete-group", editor))
        S.$("#pms-delete-group", editor).onclick = (e) =>
          S.run(e.currentTarget, async () => {
            if (
              !(await S.confirm(
                "이 그룹을 삭제하시겠습니까? 제품에서 참조 중이면 삭제할 수 없습니다.",
              ))
            )
              return;
            await S.request("/groups/" + selected, "DELETE");
            await S.catalog();
            open(S.groups[0]?.id || null);
            S.notice("그룹을 삭제했습니다.", true);
          });
      if (S.$("#pms-values", editor)) {
        paintValues();
        S.$("#pms-add-value", editor).onclick = () => {
          if (draftValues.length >= 200) {
            S.showErrors(S.$("#pms-values", editor), {
              "": "보기는 최대 200개까지 등록합니다.",
            });
            return;
          }
          draftValues.push({
            id: null,
            version: null,
            labels: { customer: "", production: "", management: "" },
            namePart: "",
            active: true,
            temp: S.key("new"),
          });
          S.dirty = true;
          paintValues();
        };
        S.$("#pms-save-values", editor).onclick = (e) =>
          S.run(e.currentTarget, async () => {
            if (!validateOptions(true)) return;
            await saveOptionDrafts();
            await S.catalog();
            model.version = S.group(selected).version;
            draftValues = draftValues.map((v) => ({
              ...v,
              version:
                S.group(selected).values.find((x) => x.id === v.id)?.version ??
                v.version,
            }));
            paintValues();
            S.dirty = groupDirty;
            S.notice(
              "옵션을 저장했습니다. 그룹 설정을 변경하셨다면 그룹 저장도 눌러 주세요.",
              true,
            );
          });
      }
    }
    async function saveOptionDrafts() {
      const saved = await S.requestAt(
        S.$("#pms-values", editor),
        `/groups/${selected}/values`,
        "POST",
        draftValues.map(({ id, version, labels, namePart, active, guide }) => ({
          id,
          version,
          labels,
          namePart,
          active,
          guide,
        })),
        "values.",
      );
      for (let i = 0; i < saved.length; i++) {
        const previous = draftValues[i];
        const attachments = valueFiles.get(previous.id || previous.temp) || [];
        draftValues[i].id = saved[i].id;
        draftValues[i].version = saved[i].version;
        draftValues[i].key = saved[i].key;
        valueFiles.set(saved[i].id, attachments);
        await S.request(
          `/assets/VALUE/${saved[i].id}`,
          "PUT",
          attachments.map((f) => f.id),
        );
      }
      const reordered = await S.requestAt(
        S.$("#pms-values", editor),
        `/groups/${selected}/values/reorder`,
        "POST",
        draftValues.map((v) => v.id),
      );
      model.version = reordered.version;
      for (const v of draftValues)
        v.version =
          reordered.values.find((x) => x.id === v.id)?.version ?? v.version;
    }
    function paintValues() {
      const container = S.$("#pms-values", editor);
      container.innerHTML =
        draftValues
          .map(
            (v, i) =>
              `<details class="pms-list-row pms-editor" ${S.editorAttrs("value-" + (v.id || v.temp), !v.id || i === 0)} data-value-index="${i}"><summary class="pms-row-head"><span class="pms-handle">⠿</span><strong data-editor-title>보기 ${i + 1} · ${S.e(v.labels.management || "새 보기")}</strong><small class="mono">${S.e(v.key || "저장 시 자동 value")}</small>${S.check("사용", "values." + i + ".active", v.active)}<button data-remove-value="${i}" class="pms-remove">×</button></summary><div class="pms-editor-body">${S.labels(v, "values." + i, true, v.namePart)}${S.field("선택 시 안내메시지", "values." + i + ".guide", v.guide || "", "text", 'maxlength="2000"')}<details class="pms-section"><summary>이미지·첨부파일</summary><div data-value-files="${i}"></div></details></div></details>`,
          )
          .join("") ||
        '<div class="pms-empty">+ 보기로 옵션을 추가해 주세요.</div>';
      S.trackEditors(container);
      const holder = { values: draftValues };
      S.bind(container, holder);
      S.$$("[data-value-files]", container).forEach((el) => {
        const v = draftValues[Number(el.dataset.valueFiles)],
          key = v.id || v.temp;
        if (!valueFiles.has(key)) valueFiles.set(key, []);
        const files = valueFiles.get(key);
        el.innerHTML = S.files(files);
        S.fileEvents(el, files);
      });
      S.$$("[data-remove-value]", container).forEach(
        (button) =>
          (button.onclick = () =>
            S.run(button, async () => {
              const index = Number(button.dataset.removeValue),
                v = draftValues[index];
              if (v.id) {
                if (
                  !(await S.confirm(
                    "이 옵션을 삭제하시겠습니까? 제품이 참조 중이면 삭제할 수 없습니다.",
                  ))
                )
                  return;
                await S.request("/values/" + v.id, "DELETE");
              }
              draftValues.splice(index, 1);
              S.dirty = true;
              paintValues();
            })),
      );
      S.sortable(container, "[data-value-index]", (from, to) =>
        S.run(null, async () => {
          const moved = draftValues.splice(from, 1)[0];
          draftValues.splice(to, 0, moved);
          paintValues();
          if (draftValues.every((v) => v.id)) {
            await S.request(
              `/groups/${selected}/values/reorder`,
              "POST",
              draftValues.map((v) => v.id),
            );
            const all = await S.catalog();
            for (const v of draftValues) {
              const latest = S.group(selected).values.find(
                (x) => x.id === v.id,
              );
              v.version = latest.version;
            }
            model.version = S.group(selected).version;
            S.dirty = container._pmsDirtyBeforeDrag;
            S.toast("옵션 순서를 저장했습니다.");
          }
        }),
      );
    }
    paintList();
    open(S.groups[0]?.id || null);
  };
})(window.PMS);
