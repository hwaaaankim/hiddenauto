(function (S) {
  "use strict";
  const B = S.pricingBranches = {}, e = S.e;
  const label = v => v?.labels?.management || v?.labels?.customer || v?.label || v?.key || "";
  const key = () => "price_" + crypto.randomUUID().replaceAll("-", "");
  const opt = (value, text, current) => `<option value="${e(value)}" ${value === current ? "selected" : ""}>${e(text)}</option>`;
  const eq = (groupKey, choiceKeys = []) => ({ groupKey, fieldKey: null, operator: "EQ", choiceKeys, text: null, lower: null, upper: null, lowerInclusive: true, upperInclusive: true });
  // Each row owns its source. Changing a row must never rewrite other rows or their tables.
  B.primary = (branch, sources) => (branch.conditions || []).find(c => c.operator === "EQ" && !c.fieldKey && sources.some(q => q.key === c.groupKey));
  B.source = (branch, sources) => sources.find(q => q.key === B.primary(branch, sources)?.groupKey);
  B.setSource = (branch, sources, sourceKey) => {
    if (sourceKey && !sources.some(q => q.key === sourceKey)) throw Error("현재 구성에 없는 기준 질문입니다.");
    if ((B.source(branch, sources)?.key || "") === sourceKey) return;
    // An explicit source switch replaces this row's complete condition selection.
    // Retaining other predicates here silently ANDs the old and new material questions.
    // Existing advanced conditions remain intact when opening/cancelling the editor.
    branch.conditions = sourceKey ? [eq(sourceKey)] : [];
  };
  B.setChoices = (branch, sources, choices) => {
    const primary = B.primary(branch, sources), source = B.source(branch, sources);
    if (!primary || !source || choices.some(k => !source.choices.some(c => c.key === k))) throw Error("기준 질문의 선택 보기를 확인하세요.");
    primary.choiceKeys = [...new Set(choices)];
  };
  B.inventory = (branches, sources) => sources.map(source => {
    const rows = branches.filter(b => B.source(b, sources)?.key === source.key), counts = new Map(source.choices.map(c => [c.key, 0]));
    rows.forEach(b => (B.primary(b, sources)?.choiceKeys || []).forEach(k => counts.set(k, (counts.get(k) || 0) + 1)));
    return { source, rows, missing: source.choices.filter(c => !counts.get(c.key)), repeated: source.choices.filter(c => counts.get(c.key) > 1) };
  }).filter(row => row.rows.length);
  B.validate = (draft, sources) => {
    if (!draft.branches?.length) throw Error("조건 묶음을 하나 이상 추가해 주세요.");
    if (draft.branches.length > 30) throw Error("한 항의 조건별 계산은 최대 30개입니다.");
    if (draft.match !== "ALL") throw Error("항 전체 적용 조건은 모두 일치 방식으로 설정하세요.");
    for (const branch of draft.branches) {
      if (!branch.name?.trim()) throw Error("조건 묶음 이름을 입력해 주세요.");
      const main = B.primary(branch, sources);
      if (main) {
        const source = B.source(branch, sources);
        if (!main.choiceKeys?.length || main.choiceKeys.some(k => !source.choices.some(c => c.key === k)))
          throw Error(branch.name + ": 해당하는 기준 질문의 보기를 하나 이상 선택해 주세요.");
      } else if (!branch.conditions?.length) throw Error(branch.name + ": 기준 질문을 선택하거나 상세 적용 조건을 등록하세요.");
      if (branch.match !== "ALL") throw Error(branch.name + ": 분기 조건은 모두 일치 방식으로 설정하세요.");
    }
  };
  B.open = ({ owner, process, terms, currentTerms, edit, diagnose, apply, single, conditionText, summary }) => {
    const original = JSON.stringify(terms), draft = S.copy(owner), questions = process.questions || [];
    const position = questions.findIndex(q => q.key === owner.groupKey);
    const sources = questions.slice(0, position).filter(q => q.control === "RADIO" && q.choices?.length && !q.fixed);
    // Existing advanced/numeric conditions remain editable even without a simple radio source.
    if (!sources.length && !questions.slice(0, position).some(q => q.control === "NUMBER") && !draft.branches?.length) return S.toast("앞에 조건으로 사용할 숫자 또는 단일선택 질문을 등록해 주세요.");
    draft.branches ??= []; draft.conditions ??= []; draft.match = "ALL";
    let step = 0, checking = false;
    const completed = new Set(draft.branches.filter(b => { try { S.pricingBatch.validate(b); return true; } catch { return false; } }).map(b => b.key));
    const d = S.dialog("조건별 계산 구성 · " + owner.name, "", { foot: '<span data-branch-status role="status"></span><button type="button" data-branch-single>조건 없는 항으로 전환</button><button type="button" data-branch-prev hidden>이전 · 조건 분기</button><button type="button" data-branch-add>조건 묶음 추가</button><button type="button" data-branch-next class="primary">다음 · 조건별 계산 등록</button><button type="button" data-branch-apply class="primary" hidden>분기가 있는 항 완성</button>' });
    d.classList.add("pm-price-branches");
    const body = S.$(".pms-dialog-body", d), before = JSON.stringify(draft);
    S.priceTable.guardClose(d, () => JSON.stringify(draft) !== before);
    function message(text) { const el = S.$("[data-branch-error]", body); el.textContent = text; el.scrollIntoView({ block: "nearest" }); }
    const untouched = () => { if (JSON.stringify(currentTerms()) !== original) throw Error("작성 중 원래 가격식이 변경되었습니다. 작성 내용을 확인한 뒤 다시 열어 주세요."); };
    function editConditions(term, save) {
      edit(S.copy(term), next => { save(S.copy(next.conditions || [])); render(); }, { locked: true, conditionsOnly: true });
    }
    function scopeHtml() {
      const input = questions.find(q => q.key === draft.groupKey);
      const bundles = [...new Set((input?.fields || []).map(f => f.bundleKey || f.key))];
      const bundleIndex = bundles.indexOf(draft.bundleKey);
      const fields = (input?.fields || []).filter(f => (draft.fieldKeys || []).includes(f.key)).map(label).join(" / ");
      return `<section class="pm-branch-scope"><span class="pm-price-kicker">계산할 입력</span><strong>이 항에 연결된 숫자 입력: ${e(label(input))}${input?.groupId != null ? " · 그룹 #" + e(input.groupId) : ""}</strong><p class="pm-branch-input-fields">${bundleIndex >= 0 ? "입력묶음 #" + (bundleIndex + 1) + " · " : ""}${e(fields)}</p><small>모든 조건별 계산은 위 입력을 사용합니다.</small><details class="pm-price-inline-help"><summary>일반용·심리스용 입력이 따로 있다면?</summary><p>각 숫자 그룹의 입력묶음에 별도 항을 등록하세요. 여기에 심리스 조건을 추가해도 다른 그룹의 숫자 입력까지 계산하지 않습니다. 같은 그룹에서 분기로 다른 묶음을 표시하는 경우도 연결을 확인합니다.</p></details></section><section class="pm-branch-scope"><header><span class="pm-price-kicker">이 항을 계산할 경로</span><button type="button" data-branch-scope>적용 경로 설정</button></header><strong>항 전체 적용 조건</strong><p>${draft.conditions.length ? draft.conditions.map(c => e(conditionText(c))).join(" 그리고 ") : "제한 없음 · 이 숫자 입력이 표시되는 경로 전체"}</p><small>${draft.conditions.length ? '위 조건이 맞을 때만 아래 분기에서 계산 하나를 찾습니다.' : '아래 분기가 표시 가능한 모든 재질을 포함해야 합니다.'}</small><details class="pm-price-inline-help"><summary>언제 항 전체에 조건을 지정하나요?</summary><p>한 항에 대리석·심리스 계산을 모두 넣으면 제한 없이 둡니다. 같은 입력에 대리석용 항과 심리스용 항을 따로 만들면 각 항 전체에 해당 상판 선택 조건을 지정하세요. 입력이 숨겨진 항은 계산에서 제외됩니다.</p></details></section>`;
    }
    function answersHtml(branch, index) {
      const source = B.source(branch, sources), primary = B.primary(branch, sources), extra = (branch.conditions || []).filter(c => c !== primary);
      return `${!step && source ? `<div class="pms-check-grid">${source.choices.map(c => `<label class="pms-check"><input type="checkbox" data-branch-choice="${index}" value="${e(c.key)}" ${(primary.choiceKeys || []).includes(c.key) ? "checked" : ""}>${e(label(c))}</label>`).join("")}</div>` : ""}
        ${extra.length || !source ? `<p class="pms-help">${(source ? extra : branch.conditions || []).map(c => e(conditionText(c))).join(" 그리고 ") || "상세 적용 조건을 등록하세요."}</p>` : ""}`;
    }
    function rowHtml(branch, index) {
      const source = B.source(branch, sources), primary = B.primary(branch, sources);
      return `<section class="pm-branch-row" data-branch-row="${index}"><header class="pm-branch-row-heading"><span class="pm-branch-row-index">${String(index + 1).padStart(2, '0')}</span><span><strong>${step ? e(branch.name) : '같은 계산을 적용할 답변'}</strong><small><span data-branch-source-label="${index}">${source ? e(label(source)) : '상세 적용 조건'}</span> · <span data-branch-selection-count="${index}">${primary?.choiceKeys?.length || 0}</span>개 보기 연결</small></span><span class="pm-branch-price-state ${completed.has(branch.key) ? 'is-ready' : ''}">${completed.has(branch.key) ? '계산 자료 있음' : '계산 자료 필요'}</span></header><div class="pms-form-grid two"><label><span>조건 묶음 이름</span><input type="text" data-branch-name="${index}" maxlength="120" value="${e(branch.name)}" ${step ? "readonly" : ""}></label>${!step ? `<label><span>이 분기의 기준 질문</span><select data-branch-source="${index}"><option value="">상세 조건으로 설정</option>${sources.map(q => opt(q.key, label(q), source?.key)).join("")}</select></label>` : `<div class="pm-branch-selected"><span>연결된 답변</span><p>${(primary?.choiceKeys || []).map(k => e(label(source?.choices.find(c => c.key === k)))).join(" / ") || '아래 상세 조건으로 적용'}</p></div>`}</div>
        <div data-branch-answers="${index}">${answersHtml(branch, index)}</div>
        <div class="pms-actions pm-branch-row-actions">${!step ? `<button type="button" data-branch-conditions="${index}">상세 적용 조건</button><button type="button" data-branch-copy="${index}">계산 자료와 함께 복사</button><button type="button" class="danger" data-branch-remove="${index}">이 조건 묶음 삭제</button>` : `<span class="pm-branch-method-summary">${completed.has(branch.key) ? e(summary(branch)) : "이 답변에 사용할 금액·단가·가격표를 등록하세요."}</span><button type="button" class="primary" data-branch-price="${index}">${completed.has(branch.key) ? "계산 조회·수정" : "엑셀·고정금액·곱셈 등록"}</button>`}</div></section>`;
    }
    function inventoryHtml() {
      return B.inventory(draft.branches, sources).map(row => `<div class="pm-branch-inventory-item"><strong>${e(label(row.source))}</strong><span>${row.rows.length}개 계산 · 전체 보기 ${row.source.choices.length}개</span>${row.missing.length ? `<details><summary>아직 연결하지 않은 보기 ${row.missing.length}개</summary><p>${row.missing.map(c => e(label(c))).join(", ")}</p></details>` : '<small class="pm-branch-inventory-ok">모든 보기 연결</small>'}${row.repeated.length ? `<details><summary>여러 묶음에 선택된 보기 ${row.repeated.length}개</summary><p>${row.repeated.map(c => e(label(c))).join(", ")}</p></details>` : ''}</div>`).join("");
    }
    function bindChoices(scope) {
      S.$$("[data-branch-choice]", scope).forEach(input => input.onchange = () => {
        const index = Number(input.dataset.branchChoice);
        B.setChoices(draft.branches[index], sources, S.$$(`[data-branch-choice="${index}"]:checked`, body).map(x => x.value));
        S.$("[data-branch-inventory]", body).innerHTML = inventoryHtml();
        S.$(`[data-branch-selection-count="${index}"]`, body).textContent = B.primary(draft.branches[index], sources)?.choiceKeys?.length || 0;
      });
    }
    function render() {
      body.innerHTML = `<ol class="pm-stepper pm-branch-stepper"><li class="${!step ? "active" : ""}" ${!step ? 'aria-current="step"' : ''}><b>1</b><span>답변 연결<small>어떤 선택에 적용할지</small></span></li><li class="${step ? "active" : ""}" ${step ? 'aria-current="step"' : ''}><b>2</b><span>계산 자료 등록<small>각 답변의 금액·표</small></span></li><li><b>3</b><span>항에 반영<small>검사 후 초안에 추가</small></span></li></ol><div class="pm-branch-stage-heading"><h3>${step ? '각 조건에 사용할 계산을 확인하세요.' : '조건 묶음별로 질문과 답변을 연결하세요.'}</h3><p>${step ? '각 분기에 같은 고정금액·단가·엑셀 가격표를 반복해서 사용할 수 있습니다. 실제 선택 경로마다 이 항의 계산 하나가 적용되는지 검사합니다.' : '대리석용 묶음과 심리스용 묶음을 각각 추가하세요. 기준 질문을 바꾸면 해당 묶음의 이전 선택·상세 조건을 비우며, 이름과 계산 자료는 유지됩니다.'}</p></div><div data-branch-error class="pms-error" role="alert"></div><div class="pm-branch-layout"><aside class="pm-branch-context" aria-label="입력과 적용 범위">${scopeHtml()}<section class="pm-branch-scope"><span class="pm-price-kicker">답변 연결 현황</span><div data-branch-inventory>${inventoryHtml() || '<p>조건 묶음을 추가하면 연결 내역이 표시됩니다.</p>'}</div><small>전체 보기 기준의 참고 내역입니다. 실제 누락·중복 여부는 프로세스 경로와 추가 조건을 함께 검사합니다.</small></section></aside><div class="pm-branch-list"><header><strong>조건 묶음 ${draft.branches.length}개</strong><span>${step ? '각 묶음에서 계산 조회·수정' : '질문 선택 → 답변 체크'}</span></header>${draft.branches.map(rowHtml).join("") || '<div class="pms-empty">아래 ‘조건 묶음 추가’를 눌러 첫 답변 묶음을 만드세요.</div>'}</div></div><div data-branch-scroll-reserve aria-hidden="true"></div>`;
      S.$("[data-branch-prev]", d).hidden = !step; S.$("[data-branch-add]", d).hidden = !!step;
      S.$("[data-branch-next]", d).hidden = !!step; S.$("[data-branch-apply]", d).hidden = !step;
      S.$("[data-branch-scope]", body).onclick = () => editConditions(draft, conditions => { draft.conditions = conditions; });
      S.$$("[data-branch-name]", body).forEach(input => input.oninput = () => { draft.branches[Number(input.dataset.branchName)].name = input.value; });
      S.$$("[data-branch-source]", body).forEach(input => input.onchange = () => {
        const index = Number(input.dataset.branchSource), branch = draft.branches[index];
        if ((B.source(branch, sources)?.key || "") === input.value) return;
        const anchorTop = input.getBoundingClientRect().top, reserve = S.$("[data-branch-scroll-reserve]", body);
        B.setSource(branch, sources, input.value);
        // Keep the native select, its row and focus alive while its popup/key event is active.
        // Replacing the whole dialog here forces every row through layout and destroys the control.
        const answers = S.$(`[data-branch-answers="${index}"]`, body);
        answers.innerHTML = answersHtml(branch, index); bindChoices(answers);
        S.$(`[data-branch-source-label="${index}"]`, body).textContent = B.source(branch, sources) ? label(B.source(branch, sources)) : "상세 적용 조건";
        S.$(`[data-branch-selection-count="${index}"]`, body).textContent = "0";
        S.$("[data-branch-inventory]", body).innerHTML = inventoryHtml();
        S.$("[data-branch-status]", d).textContent = "이전 선택·상세 조건을 비웠습니다. 이 묶음의 답변을 다시 선택하세요.";
        // A shorter answer list near the bottom otherwise clamps scrollTop and moves
        // the still-open native control. Reserve only the space needed for this anchor.
        reserve.style.height = "0px";
        const targetScroll = body.scrollTop + input.getBoundingClientRect().top - anchorTop;
        reserve.style.height = Math.max(0, Math.ceil(targetScroll - (body.scrollHeight - body.clientHeight))) + "px";
        body.scrollTop = targetScroll;
      });
      bindChoices(body);
      S.$$("[data-branch-conditions]", body).forEach(button => button.onclick = () => {
        const branch = draft.branches[Number(button.dataset.branchConditions)];
        editConditions(branch, conditions => { branch.conditions = conditions; });
      });
      S.$$("[data-branch-remove]", body).forEach(button => button.onclick = async () => {
        const index = Number(button.dataset.branchRemove);
        if (completed.has(draft.branches[index].key) && !await S.confirm("이 조건 묶음과 연결된 계산 자료를 초안에서 삭제하시겠습니까?")) return;
        draft.branches.splice(index, 1); render();
      });
      S.$$("[data-branch-copy]", body).forEach(button => button.onclick = () => {
        if (draft.branches.length >= 30) return message("조건 묶음은 최대 30개입니다.");
        const source = draft.branches[Number(button.dataset.branchCopy)], copy = S.copy(source);
        copy.key = key(); copy.name = (copy.name + " 복사").slice(0, 120); draft.branches.push(copy);
        if (completed.has(source.key)) completed.add(copy.key);
        render(); message("계산 자료를 복사했습니다. 새 묶음의 기준 질문·보기와 단가를 확인하세요.");
      });
      S.$$("[data-branch-price]", body).forEach(button => button.onclick = () => {
        const index = Number(button.dataset.branchPrice), branch = draft.branches[index];
        edit(branch, next => { next.conditions = S.copy(branch.conditions); next.match = "ALL"; next.branches = []; next.multipliers = []; next.adjustments = []; draft.branches[index] = next; completed.add(next.key); render(); }, { locked: true, branch: true });
      });
    }
    S.$("[data-branch-single]", d).onclick = async () => {
      if (!await S.confirm("조건 분기를 해제하고 기본 계산 자료를 확인합니다. 이 창의 미반영 수정은 버립니다. 계속하시겠습니까?")) return;
      d.close(); single();
    };
    S.$("[data-branch-add]", d).onclick = () => {
      if (draft.branches.length >= 30) return message("조건 묶음은 최대 30개입니다.");
      const branch = S.copy(owner), lastSource = B.source(draft.branches.at(-1) || {}, sources) || sources[0];
      Object.assign(branch, { key: key(), name: "조건 " + (draft.branches.length + 1), conditions: lastSource ? [eq(lastSource.key)] : [], match: "ALL", optionTarget: null, optionPricing: null, branches: [], multipliers: [], adjustments: [], branchScope: null });
      draft.branches.push(branch); render();
    };
    const startStep = next => { step = next; render(); body.scrollTop = 0; d.scrollTop = 0; };
    S.$("[data-branch-prev]", d).onclick = () => startStep(0);
    S.$("[data-branch-next]", d).onclick = () => { try { B.validate(draft, sources); startStep(1); } catch (err) { message(err.message); } };
    S.$("[data-branch-apply]", d).onclick = event => S.run(event.currentTarget, async () => {
      if (checking) return;
      checking = true;
      try {
        await S.withBusy(async () => {
          B.validate(draft, sources);
          let frameStart = performance.now();
          for (const branch of draft.branches) {
            if (!completed.has(branch.key)) throw Error(branch.name + ": 계산 자료를 등록해 주세요.");
            S.pricingBatch.validate(branch);
            if (performance.now() - frameStart > 12) { await new Promise(resolve => requestAnimationFrame(resolve)); frameStart = performance.now(); }
          }
          untouched(); const stamp = JSON.stringify(draft);
          const next = terms.some(t => t.key === owner.key) ? terms.map(t => t.key === owner.key ? S.copy(draft) : S.copy(t)) : terms.concat(S.copy(draft));
          S.$("[data-branch-status]", d).textContent = "실제 프로세스 경로와 조건별 계산 검사 중…";
          const validation = await diagnose(next);
          if (!d.open) return;
          untouched(); if (stamp !== JSON.stringify(draft)) throw Error("검사 중 작성 내용이 바뀌었습니다. 다시 항 완성을 눌러 주세요.");
          if (!validation.valid || validation.exhaustive === false) throw Error((validation.issues || []).filter(i => i.severity === "ERROR").map(i => i.message).join(" / ") || "전체 검증을 완료하지 못했습니다.");
          apply(S.copy(draft), next); d.close(); S.toast("조건별 계산을 한 항으로 반영했습니다. 실제 입력 테스트 후 메인 화면에서 저장하세요.");
        }, "조건 연결과 가격표를 검증하고 있습니다.");
      } catch (err) { message(err.message); }
      finally { checking = false; if (d.open) S.$("[data-branch-status]", d).textContent = ""; }
    });
    render(); return d;
  };
})(window.PMS);
