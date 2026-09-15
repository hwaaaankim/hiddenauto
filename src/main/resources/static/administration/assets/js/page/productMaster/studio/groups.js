(function (S) {
  "use strict";
  S.groupsPage = async function (root) {
    let selected = null,
      model = null,
      groupFiles = [],
      draftValues = [],
      valueFiles = new Map(),
      filter = "";
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
            `<article draggable="true" data-group="${g.id}" class="pms-palette-item ${g.id === selected ? "active" : ""}"><strong><span class="pms-handle">⠿</span> ${S.e(g.labels.management)}</strong><small>${S.isBase(g) ? S.badge("기본", "blue") : S.badge(g.nonStandard ? "비규격" : "규격", g.nonStandard ? "amber" : "")}${S.e(S.controls[g.control])} · ${g.values.length}개</small>${!g.active ? S.badge("비활성", "red") : ""}</article>`,
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
        S.toast("순서 변경은 검색어를 비운 뒤 진행해 주세요.");
        return;
      }
      const ids = S.groups.map((g) => g.id);
      ids.splice(to, 0, ids.splice(from, 1)[0]);
      await S.run(null, async () => {
        S.groups = await S.request("/groups/reorder", "POST", ids);
        paintList();
        S.dirty = false;
      });
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
        active: v.active,
        key: v.key,
      }));
      valueFiles = new Map(
        (g?.values || []).map((v) => [v.id, S.copy(v.assets)]),
      );
      S.dirty = false;
      paintList();
      paintEditor();
    }
    function paintEditor() {
      const base = S.isBase(model);
      editor.innerHTML = `<header class="pms-panel-title"><div><h2>${model.id ? "그룹 수정" : "새 그룹"}</h2><small class="mono">${S.e(S.group(model.id)?.key || "내부 value는 저장할 때 자동 생성됩니다.")}</small></div><div class="pms-actions">${model.id && !base ? '<button class="danger" id="pms-delete-group">삭제</button>' : ""}<button class="primary" id="pms-save-group">그룹 저장</button></div></header><div class="pms-panel-body">${S.labels(model, "")}<div class="pms-help">고객명을 입력하면 아직 별도로 수정하지 않은 생산팀·관리팀 표시명도 같은 값으로 채웁니다. 내부 value는 표시명과 독립적으로 유지됩니다.</div><div class="pms-form-grid">${S.select("그룹 역할", "role", model.role, S.roles)}${S.select("입력 방식", "control", model.control, S.controls)}<div class="pms-row">${S.check("비규격 그룹", "nonStandard", model.nonStandard, "pms-switch")}</div><div class="wide pms-row">${S.check("제품명 자동생성에 포함", "includeInName", model.includeInName, "pms-switch")}${S.check("신규 제품에 사용", "active", model.active, "pms-switch")}</div>${S.field("챗봇 질문", "question", model.question, "text", 'maxlength="300"')}${S.field("고객 도움말", "guide", model.guide, "text", 'maxlength="1000"')}</div>${model.nonStandard ? '<div class="pms-help">비규격 그룹은 입력 방식만 저장합니다. 입력 필드·보기·조건은 생성된 비규격 제품의 프로세스에서 각각 설정합니다.</div>' : !S.isChoice(model.control) ? `<section class="pms-section"><div class="pms-row"><h2>입력 필드</h2><button id="pms-add-field">+ 필드</button></div><div id="pms-fields">${S.fieldRows(model.fields, model.control)}</div></section>` : ""}<section class="pms-section"><h2>그룹 이미지·첨부파일</h2><div id="pms-group-files">${S.files(groupFiles)}</div></section></div>${model.id && !model.nonStandard && S.isChoice(model.control) ? `<div class="pms-panel-title"><div><h2>옵션 관리</h2><small>드래그로 순서 변경 · +로 여러 보기를 추가한 뒤 한 번에 저장</small></div><div class="pms-actions"><button id="pms-add-value">+ 보기</button><button class="primary" id="pms-save-values">옵션 저장</button></div></div><div class="pms-panel-body" id="pms-values"></div>` : !model.id && S.isChoice(model.control) && !model.nonStandard ? '<div class="pms-help">그룹을 저장하면 아래에서 보기를 여러 개 등록할 수 있습니다.</div>' : ""}`;
      if (base) {
        S.$('[data-path="nonStandard"]', editor).disabled = true;
        S.$('[data-path="control"]', editor).disabled = true;
        S.$('[data-path="active"]', editor).disabled = model.active;
      }
      // Bind only once to the fresh body, avoiding stacked handlers on the persistent editor.
      const body = S.$(".pms-panel-body", editor);
      S.bind(body, model, (path, value) => {
        if (["control", "nonStandard", "role"].includes(path)) {
          if (S.isBase(model)) {
            model.control = "RADIO";
            model.nonStandard = false;
            model.active = true;
          }
          if (model.nonStandard || S.isChoice(model.control)) model.fields = [];
          else if (!model.fields.length) model.fields = [S.newField()];
          paintEditor();
        }
      });
      if (S.$("#pms-fields", editor)) {
        const fields = S.$("#pms-fields", editor);
        S.fieldEvents(fields, model.fields, paintEditor);
        S.$("#pms-add-field", editor).onclick = () => {
          if (model.fields.length >= 20)
            return S.toast("입력 필드는 최대 20개입니다.");
          model.fields.push(S.newField());
          S.dirty = true;
          paintEditor();
        };
      }
      S.fileEvents(S.$("#pms-group-files", editor), groupFiles);
      S.$("#pms-save-group", editor).onclick = (e) =>
        S.run(e.currentTarget, async () => {
          const saved = await S.request("/groups", "POST", model);
          model.id = saved.id;
          model.version = saved.version;
          selected = saved.id;
          await S.request(
            `/assets/GROUP/${saved.id}`,
            "PUT",
            groupFiles
              .filter((f) => !f.id.startsWith("legacy-"))
              .map((f) => f.id),
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
            if (!draftValues.length)
              return S.toast("저장할 보기를 추가해 주세요.");
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
            S.notice(
              "옵션을 저장했습니다. 그룹 설정을 변경하셨다면 그룹 저장도 눌러 주세요.",
              true,
            );
          });
      }
    }
    async function saveOptionDrafts() {
      const saved = await S.request(
        `/groups/${selected}/values`,
        "POST",
        draftValues,
      );
      for (let i = 0; i < saved.length; i++) {
        const previous = draftValues[i];
        const attachments = valueFiles.get(previous.id || previous.temp) || [];
        draftValues[i].id = saved[i].id;
        draftValues[i].version = saved[i].version;
        valueFiles.set(saved[i].id, attachments);
        await S.request(
          `/assets/VALUE/${saved[i].id}`,
          "PUT",
          attachments
            .filter((f) => !f.id.startsWith("legacy-"))
            .map((f) => f.id),
        );
      }
      await S.request(
        `/groups/${selected}/values/reorder`,
        "POST",
        draftValues.map((v) => v.id),
      );
    }
    function paintValues() {
      const container = S.$("#pms-values", editor);
      container.innerHTML =
        draftValues
          .map(
            (v, i) =>
              `<article class="pms-list-row" draggable="true" data-value-index="${i}"><div class="pms-row-head"><span class="pms-handle">⠿</span><strong>보기 ${i + 1}</strong><small class="mono">${S.e(v.key || "저장 시 자동 value")}</small>${S.check("사용", "values." + i + ".active", v.active)}<button data-remove-value="${i}" class="pms-remove">×</button></div>${S.labels(v, "values." + i, true, v.namePart)}<details class="pms-section"><summary>이미지·첨부파일</summary><div data-value-files="${i}"></div></details></article>`,
          )
          .join("") ||
        '<div class="pms-empty">+ 보기로 옵션을 추가해 주세요.</div>';
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
            S.toast("옵션 순서를 저장했습니다.");
          }
        }),
      );
    }
    paintList();
    open(S.groups[0]?.id || null);
  };
})(window.PMS);
