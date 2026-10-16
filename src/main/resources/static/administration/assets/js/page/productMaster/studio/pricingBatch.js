(function (S) {
  "use strict";
  const B = S.pricingBatch = {}, e = S.e;
  const label = v => v?.labels?.management || v?.labels?.customer || v?.label || v?.key || "";
  const types = { SKIP: "추가하지 않음", FIXED: "고정 금액", MULTIPLY: "입력값 × 단가", MATRIX_1D: "가격표 · 숫자 1개", MATRIX_2D: "가격표 · 숫자 2개" };
  const opt = (value, text, current) => `<option value="${e(value)}" ${String(value) === String(current) ? "selected" : ""}>${e(text)}</option>`;
  const condition = (groupKey, choiceKey) => ({ groupKey, fieldKey: null, operator: "EQ", choiceKeys: [choiceKey], text: null, lower: null, upper: null, lowerInclusive: true, upperInclusive: true });
  B.term = (row, group, source) => {
    if (row.term) return S.copy(row.term);
    const numeric = row.kind !== 'choice' || row.mode !== 'FIXED';
    if (numeric && !source) throw Error(`${row.title}: 계산에 사용할 숫자 입력묶음을 선택해 주세요.`);
    const fields = numeric ? row.fields : [];
    if (numeric && (fields.length !== (row.mode === 'MATRIX_2D' ? 2 : 1) || !fields.every(k => source.bundle.fields.some(f => f.key === k)))) throw Error(`${row.title}: ${row.mode === 'MATRIX_2D' ? '서로 다른 숫자 2개' : '숫자 1개'}를 선택해 주세요.`);
    const term = { key: 'price_' + crypto.randomUUID().replaceAll('-', ''), name: row.title.slice(0, 120), groupKey: numeric ? source.group.groupKey : group.groupKey, bundleKey: numeric ? source.bundle.key : null, fieldKeys: [...fields], type: row.mode, amount: row.mode === 'FIXED' ? row.value : null, rates: row.mode === 'MULTIPLY' ? { [fields[0]]: row.value } : {}, xAxis: null, yAxis: null, values: [], conditions: numeric && row.kind === 'choice' ? [condition(group.groupKey, row.choiceKey)] : [], match: 'ALL', choiceKeys: numeric ? [] : [row.choiceKey] };
    if (row.mode.startsWith('MATRIX')) { term.xAxis = { fieldKey: fields[0], operator: 'LE', headers: [null], operators: [null] }; term.yAxis = row.mode === 'MATRIX_2D' ? { fieldKey: fields[1], operator: 'LE', headers: [null], operators: [null] } : null; term.values = [[null]]; }
    return term;
  };
  B.validate = term => {
    if (term.type === 'FIXED') S.priceTable.amount(term.amount);
    else if (term.type === 'MULTIPLY') {
      for (const k of term.fieldKeys) S.priceTable.money(term.rates[k], 6);
    } else {
      S.priceTable.validateHeaders(term.xAxis.headers); if (term.yAxis) S.priceTable.validateHeaders(term.yAxis.headers);
      const rows = term.yAxis?.headers.length || 1, cols = term.xAxis.headers.length;
      if (rows * cols > 20000 || term.values.length !== rows || term.values.some(row => row.length !== cols)) throw Error('가격표 행·열 수를 확인하세요.');
      term.values.forEach(row => row.forEach(S.priceTable.amount));
    }
  };
  B.overlap = (a, b) => a.groupKey === b.groupKey && (a.bundleKey ? a.bundleKey === b.bundleKey && a.fieldKeys.some(k => b.fieldKeys.includes(k)) : !b.bundleKey && (!a.choiceKeys?.length || !b.choiceKeys?.length || a.choiceKeys.some(k => b.choiceKeys.includes(k))));
  B.open = ({ groups, definition, selectedGroup, edit, summary, apply }) => {
    const sources = groups.filter(g => g.control === 'NUMBER').flatMap(group => group.bundles.map(bundle => ({ group, bundle })));
    let groupKey = selectedGroup || groups[0]?.groupKey, rows = [], changes = new Map();
    const group = () => groups.find(g => g.groupKey === groupKey);
    const dirty = () => changes.size > 0 || rows.some(r => r.mode !== 'SKIP');
    const d = S.dialog('옵션별 계산 방식 일괄 설정', '', { foot: '<span class="pms-help">검토 후 항에 반영 → 실제 입력 테스트 → 최종 저장</span><button type="button" class="primary" data-batch-apply>변경 내용 검토</button>' });
    d.classList.add('pm-price-batch'); const body = S.$('.pms-dialog-body', d);
    S.priceTable.guardClose(d, dirty);
    const relevant = t => t.groupKey === groupKey || t.conditions?.some(c => c.groupKey === groupKey);
    const originalTerms = () => definition.terms.filter(relevant);
    function reset() {
      changes = new Map(); rows = [];
      const g = group();
      if (g.control === 'NUMBER') for (const bundle of g.bundles) for (const f of bundle.fields) rows.push({ id: rows.length, kind: 'field', title: `${g.label} · ${label(f)}`, note: `${bundle.label || '입력묶음'}${f.unit ? ' / ' + f.unit : ''}`, source: sources.findIndex(s => s.group.groupKey === g.groupKey && s.bundle.key === bundle.key), fields: [f.key], mode: 'SKIP', value: '', term: null });
      else for (const c of g.choices) rows.push({ id: rows.length, kind: 'choice', title: `${g.label} · ${label(c)}`, note: '이 옵션이 선택된 경우에만 적용', choiceKey: c.key, source: '', fields: [], mode: 'SKIP', value: '', term: null });
    }
    function showError(message) { S.$('[data-batch-error]', body).hidden = false; S.$('[data-batch-error]', body).textContent = message; S.$('[data-batch-error]', body).scrollIntoView({ block: 'nearest' }); }
    function used(row) {
      return definition.terms.filter(t => {
        if (row.kind === 'choice') return t.groupKey === groupKey && (!t.choiceKeys?.length || t.choiceKeys.includes(row.choiceKey)) || t.conditions?.some(c => c.groupKey === groupKey && c.choiceKeys?.includes(row.choiceKey));
        return t.groupKey === groupKey && t.bundleKey === sources[row.source]?.bundle.key && t.fieldKeys?.some(k => row.fields.includes(k));
      }).length;
    }
    function rowHtml(row) {
      const numeric = row.kind !== 'choice' || !['SKIP', 'FIXED'].includes(row.mode), source = sources[row.source];
      const allowed = row.kind === 'pair' ? ['SKIP', 'MATRIX_2D'] : ['SKIP', 'FIXED', ...(row.kind !== 'choice' || sources.length ? ['MULTIPLY', 'MATRIX_1D', ...(row.kind === 'choice' && sources.some(s => s.bundle.fields.length >= 2) ? ['MATRIX_2D'] : [])] : [])];
      return `<article class="pm-price-batch-row" data-batch-row="${row.id}"><header><strong>${e(row.title)}</strong><small>${e(row.note)}${used(row) ? ` · 기존 관련 항 ${used(row)}개` : ''}</small></header><div class="pm-price-batch-controls"><label><span>계산 방식</span><select data-batch-mode="${row.id}">${allowed.map(v => opt(v, types[v], row.mode)).join('')}</select></label>${row.mode !== 'SKIP' ? `${numeric && row.kind !== 'field' ? `<label><span>연결할 숫자 입력묶음</span><select data-batch-source="${row.id}"><option value="">묶음을 선택하세요</option>${sources.filter(s => row.kind !== 'pair' || s.group.groupKey === groupKey).filter(s => row.mode !== 'MATRIX_2D' || s.bundle.fields.length >= 2).map(s => opt(sources.indexOf(s), `${s.group.label} / ${s.bundle.label || s.bundle.fields.map(label).join(' · ')}`, row.source)).join('')}</select></label>${source ? `<fieldset class="pm-batch-fields"><legend>${row.mode === 'MATRIX_2D' ? '함께 계산할 숫자 2개' : '계산할 숫자 1개'}</legend>${source.bundle.fields.map(f => `<label class="pms-check"><input type="checkbox" data-batch-field="${row.id}" value="${e(f.key)}" ${row.fields.includes(f.key) ? 'checked' : ''}>${e(label(f))}${f.unit ? ' (' + e(f.unit) + ')' : ''}</label>`).join('')}</fieldset>` : ''}` : ''}${['FIXED', 'MULTIPLY'].includes(row.mode) ? `<label><span>${row.mode === 'FIXED' ? '한 번 더할 금액 (원)' : '입력 1단위당 단가 (원)'}</span><input type="number" min="0" step="${row.mode === 'FIXED' ? '0.01' : '0.000001'}" data-batch-value="${row.id}" value="${e(row.value)}" placeholder="무료인 경우에도 0 입력"></label>` : `<div class="pm-batch-table-state"><strong>${row.term ? '가격표 작성 완료' : '가격표 작성 필요'}</strong><small>${row.term ? e(summary(row.term)) : '기준값·포함 여부·금액을 설정하세요.'}</small></div>`}<button type="button" data-batch-detail="${row.id}">${row.mode.startsWith('MATRIX') ? '가격표 설정' : '이름·조건 세부설정'}</button>` : '<small class="pms-help">기존 항은 유지됩니다. 새로 추가할 항목만 방식을 선택하세요.</small>'}</div></article>`;
    }
    function render() {
      body.innerHTML = `<ol class="pm-stepper"><li>1. 등록 옵션·기존 항 확인</li><li>2. 옵션별 방식·금액 설정</li><li>3. 변경 검토·항 반영</li></ol><p class="pms-help">이 제품의 프로세스에 등록된 옵션을 가져와 가격 항을 연결합니다. 옵션마다 계산 방식을 한 화면에서 정합니다. 기존 항은 아래에서 수정하고, 추가할 항목만 선택하세요. 가격표와 다른 항이 같은 입력을 사용하면 금액이 합산됩니다.</p><label><span>가격을 설정할 등록 그룹</span><select data-batch-group>${groups.map(g => opt(g.groupKey, g.label, groupKey)).join('')}</select></label><div data-batch-error role="alert" class="pms-alert" hidden></div><h3>기존 관련 항 ${originalTerms().length}개</h3><div class="pm-batch-existing">${originalTerms().map(original => { const t = changes.get(original.key) || original; return `<article><div><strong>${e(t.name)}${changes.has(t.key) ? ' · 수정됨' : ''}</strong><p>${e(summary(t))}</p><small>${t.conditions?.length || 0}개 추가 조건 유지</small></div><button type="button" data-batch-existing="${e(t.key)}">기존 항 수정</button></article>`; }).join('') || '<p class="pms-help">아직 관련 항이 없습니다.</p>'}</div><h3>옵션별 새 항 추가</h3><p class="pms-help">고정 금액은 한 번 더합니다. 곱셈은 선택한 숫자 × 단가입니다. 선택형 옵션의 곱셈·가격표는 숫자 입력에 연결하고 ‘이 옵션이 선택됨’을 필수 조건으로 붙입니다. 체크박스에서 여러 옵션을 선택하면 해당 옵션들의 항이 각각 더해집니다.</p><div class="pm-price-batch-rows">${rows.map(rowHtml).join('')}</div>${group().control === 'NUMBER' && group().bundles.some(b => b.fields.length >= 2) ? '<div class="pms-actions"><button type="button" data-batch-pair>＋ 숫자 2개를 함께 계산하는 가격표</button></div>' : ''}`;
      const run = fn => Promise.resolve().then(fn).catch(ex => showError(ex.message));
      S.$('[data-batch-group]', body).onchange = event => run(async () => {
        const next = event.target.value;
        if (dirty() && !await S.confirm('현재 그룹에서 아직 반영하지 않은 설정을 버리고 다른 그룹을 여시겠습니까?')) { event.target.value = groupKey; return; }
        groupKey = next; reset(); render();
      });
      S.$$('[data-batch-existing]', body).forEach(button => button.onclick = () => {
        const original = definition.terms.find(t => t.key === button.dataset.batchExisting);
        edit(changes.get(original.key) || original, next => { changes.set(original.key, next); render(); });
      });
      S.$$('[data-batch-mode]', body).forEach(input => input.onchange = () => run(async () => {
        const row = rows[Number(input.dataset.batchMode)], next = input.value;
        if ((row.term || row.value !== '') && !await S.confirm('이 행의 계산 방식을 바꾸면 작성한 금액·가격표·추가 조건을 초기화합니다. 계속하시겠습니까?')) { input.value = row.mode; return; }
        row.mode = next; row.term = null; row.value = '';
        if (row.kind === 'choice' && sources[row.source]) row.fields = sources[row.source].bundle.fields.slice(0, next === 'MATRIX_2D' ? 2 : 1).map(f => f.key);
        render();
      }));
      S.$$('[data-batch-source]', body).forEach(input => input.onchange = () => run(async () => {
        const row = rows[Number(input.dataset.batchSource)], next = input.value;
        if ((row.term || row.value !== '') && !await S.confirm('입력묶음 변경 시 이 행의 금액·가격표·추가 조건을 초기화합니다. 계속하시겠습니까?')) { input.value = row.source; return; }
        row.source = next; row.fields = (sources[next]?.bundle.fields || []).slice(0, row.mode === 'MATRIX_2D' ? 2 : 1).map(f => f.key); row.term = null; row.value = ''; render();
      }));
      S.$$('[data-batch-field]', body).forEach(input => input.onchange = () => run(async () => {
        const row = rows[Number(input.dataset.batchField)], fields = S.$$(`[data-batch-field="${row.id}"]:checked`, body).map(i => i.value);
        if (row.term && !await S.confirm('사용할 숫자를 변경하면 이 행의 가격표·추가 조건을 초기화합니다. 계속하시겠습니까?')) { render(); return; }
        row.fields = fields; row.term = null; render();
      }));
      S.$$('[data-batch-value]', body).forEach(input => input.oninput = () => {
        const row = rows[Number(input.dataset.batchValue)]; row.value = input.value;
        if (row.term) { if (row.mode === 'FIXED') row.term.amount = input.value; else row.term.rates[row.fields[0]] = input.value; }
      });
      S.$$('[data-batch-detail]', body).forEach(button => button.onclick = () => run(() => {
        const row = rows[Number(button.dataset.batchDetail)], term = B.term(row, group(), sources[row.source]);
        const requiredCondition = row.kind === 'choice' && row.mode !== 'FIXED' ? condition(groupKey, row.choiceKey) : null;
        edit(term, next => { row.term = next; row.title = next.name; row.value = next.type === 'FIXED' ? next.amount : next.type === 'MULTIPLY' ? next.rates[row.fields[0]] : ''; render(); }, { locked: true, requiredCondition });
      }));
      S.$('[data-batch-pair]', body)?.addEventListener('click', () => {
        const source = sources.findIndex(s => s.group.groupKey === groupKey && s.bundle.fields.length >= 2);
        rows.push({ id: rows.length, kind: 'pair', title: `${group().label} · 두 입력 가격표`, note: '같은 입력묶음의 숫자 2개를 X·Y로 연결', source, fields: sources[source].bundle.fields.slice(0, 2).map(f => f.key), mode: 'MATRIX_2D', value: '', term: null });
        render(); S.$(`[data-batch-row="${rows.length - 1}"]`, body).scrollIntoView({ block: 'nearest' });
      });
    }
    S.$('[data-batch-apply]', d).onclick = event => S.run(event.currentTarget, async () => {
      try {
        const additions = rows.filter(row => row.mode !== 'SKIP').map(row => {
          try { const term = B.term(row, group(), sources[row.source]); B.validate(term); return term; }
          catch (ex) { throw Error(row.title + ': ' + ex.message); }
        });
        if (!additions.length && !changes.size) throw Error('추가할 계산 방식을 선택하거나 기존 항을 수정해 주세요.');
        if (definition.terms.length + additions.length > 100) throw Error('가격 항은 최대 100개까지 가능합니다. 기존 항을 수정하거나 추가 수를 줄여 주세요.');
        const current = definition.terms.map(t => changes.get(t.key) || t), affected = [...changes.values(), ...additions];
        affected.forEach(B.validate);
        const all = [...current, ...additions];
        const overlapping = affected.filter(t => all.some(other => other.key !== t.key && B.overlap(t, other)));
        if (!await S.priceTable.review('일괄 설정 반영 전 확인', `<p><strong>기존 항 수정 ${changes.size}개 · 새 항 추가 ${additions.length}개</strong></p><ul>${affected.map(t => `<li><strong>${e(t.name)}</strong><br>${e(summary(t))}<br><small>추가 조건 ${t.conditions.length}개</small></li>`).join('')}</ul>${overlapping.length ? `<div class="pms-alert">같은 입력·옵션을 사용하는 다른 항이 있는 변경 항 ${overlapping.length}개: ${overlapping.map(t => e(t.name)).join(', ')}. 조건이 함께 성립하면 모두 더해집니다. 기존 항 수정으로 처리해야 하는지 확인하세요.</div>` : ''}<p class="pms-help">여기서 반영해도 아직 서버에 저장되지 않습니다. 메인 화면에서 실제 입력 테스트 후 저장하세요.</p>`, '확인한 항 반영')) return;
        apply(current.concat(additions)); d.close();
      } catch (ex) { showError(ex.message); }
    });
    reset(); render();
  };
})(window.PMS);
