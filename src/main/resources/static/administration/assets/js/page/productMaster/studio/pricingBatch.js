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
    if (numeric && row.kind === 'choice') term.optionTarget = { groupKey: group.groupKey, choiceKey: row.choiceKey };
    if (row.mode.startsWith('MATRIX')) { term.xAxis = { fieldKey: fields[0], operator: 'LE', headers: [null], operators: [null] }; term.yAxis = row.mode === 'MATRIX_2D' ? { fieldKey: fields[1], operator: 'LE', headers: [null], operators: [null] } : null; term.values = [[null]]; }
    return term;
  };
  B.validate = term => {
    S.optionRanges.validate(term);
    if (term.type === 'FIXED') S.priceTable.money(term.amount, term.role === 'FACTOR' ? 6 : 2);
    else if (term.type === 'MULTIPLY') {
      for (const k of term.fieldKeys) S.priceTable.money(term.rates[k], 6);
    } else {
      S.priceTable.validateAxis(term.xAxis); if (term.yAxis) S.priceTable.validateAxis(term.yAxis);
      const rows = term.yAxis?.headers.length || 1, cols = term.xAxis.headers.length;
      if (rows * cols > 20000 || term.values.length !== rows || term.values.some(row => row.length !== cols)) throw Error('가격표 행·열 수를 확인하세요.');
      term.values.forEach(row => row.forEach(value => S.priceTable.money(value, term.role === 'FACTOR' ? 6 : 2)));
    }
  };
  B.overlap = (a, b) => a.role !== 'FACTOR' && b.role !== 'FACTOR' && a.optionTarget?.groupKey === b.optionTarget?.groupKey && a.optionTarget?.choiceKey === b.optionTarget?.choiceKey && a.groupKey === b.groupKey && (a.bundleKey ? a.bundleKey === b.bundleKey && a.fieldKeys.some(k => b.fieldKeys.includes(k)) : !b.bundleKey && (!a.choiceKeys?.length || !b.choiceKeys?.length || a.choiceKeys.some(k => b.choiceKeys.includes(k))));
  // The bundle controls visibility; each price term independently chooses one or two fields.
  B.numericPlan = (group, mode, bundleKey, fieldKeys = []) => {
    if (group?.control !== 'NUMBER') throw Error('숫자 입력 그룹을 선택해 주세요.');
    const single = (bundle, field) => ({ bundle, fields: [field.key], mode: 'MATRIX_1D' });
    if (mode === 'EACH') return group.bundles.flatMap(b => b.fields.map(f => single(b, f)));
    if (!['PAIR', 'MIXED'].includes(mode)) throw Error('1차원 또는 2차원 구성을 선택해 주세요.');
    const bundle = group.bundles.find(b => b.key === bundleKey);
    if (!bundle || fieldKeys.length !== 2 || new Set(fieldKeys).size !== 2 || !fieldKeys.every(k => bundle.fields.some(f => f.key === k)))
      throw Error('같은 입력묶음에서 서로 다른 숫자 2개를 선택해 주세요. 3차원 가격표는 지원하지 않습니다.');
    return [{ bundle, fields: [...fieldKeys], mode: 'MATRIX_2D' }, ...(mode === 'MIXED' ? bundle.fields.filter(f => !fieldKeys.includes(f.key)).map(f => single(bundle, f)) : [])];
  };
  B.open = ({ groups, definition, selectedGroup, edit, summary, apply, processUrl, diagnose }) => {
    const sources = groups.filter(g => g.control === 'NUMBER').flatMap(group => group.bundles.map(bundle => ({ group, bundle })));
    let groupKey = selectedGroup || groups[0]?.groupKey, rows = [], changes = new Map(), removed = new Set(), planBundle = '', planFields = [];
    const group = () => groups.find(g => g.groupKey === groupKey);
    const dirty = () => removed.size > 0 || changes.size > 0 || rows.some(r => r.mode !== 'SKIP');
    const d = S.dialog('옵션별 계산 방식 일괄 설정', '', { foot: '<span class="pms-help">검토 후 항에 반영 → 실제 입력 테스트 → 최종 저장</span><button type="button" class="primary" data-batch-apply>변경 내용 검토</button>' });
    d.classList.add('pm-price-batch'); const body = S.$('.pms-dialog-body', d);
    S.priceTable.guardClose(d, dirty);
    const relevant = t => t.role !== 'FACTOR' && (t.groupKey === groupKey || t.conditions?.some(c => c.groupKey === groupKey));
    const originalTerms = () => definition.terms.filter(relevant);
    function reset() {
      changes = new Map(); removed = new Set(); rows = [];
      const g = group();
      const pair = g.bundles?.find(b => b.fields.length >= 2);
      planBundle = pair?.key || ''; planFields = pair?.fields.slice(0, 2).map(f => f.key) || [];
      if (g.control === 'NUMBER') for (const bundle of g.bundles) for (const f of bundle.fields) rows.push({ id: rows.length, kind: 'field', title: `${g.label} · ${label(f)}`, note: `${bundle.label || '입력묶음'}${f.unit ? ' / ' + f.unit : ''}`, source: sources.findIndex(s => s.group.groupKey === g.groupKey && s.bundle.key === bundle.key), fields: [f.key], mode: 'SKIP', value: '', term: null });
      else for (const c of g.choices) rows.push({ id: rows.length, kind: 'choice', title: `${g.label} · ${label(c)}`, note: '이 옵션이 선택된 경우에만 적용', choiceKey: c.key, source: '', fields: [], mode: 'SKIP', value: '', term: null });
    }
    function showError(message) { S.$('[data-batch-error]', body).hidden = false; S.$('[data-batch-error]', body).textContent = message; S.$('[data-batch-error]', body).scrollIntoView({ block: 'nearest' }); }
    function usedByMatrix(field) {
      return definition.terms.filter(t => !removed.has(t.key)).map(t => changes.get(t.key) || t)
        .filter(t => t.role !== 'FACTOR' && t.groupKey === groupKey && !t.optionTarget && t.type.startsWith('MATRIX') && t.fieldKeys.includes(field));
    }
    function plannedRows(mode) {
      let plans = B.numericPlan(group(), mode, planBundle, planFields);
      if (mode === 'EACH') plans = plans.filter(p => !usedByMatrix(p.fields[0]).length);
      else {
        const used = plans.flatMap(p => p.fields.flatMap(usedByMatrix));
        if (used.length) throw Error('선택한 숫자의 가격표가 이미 등록되어 있습니다: ' + [...new Set(used.map(t => t.name))].join(', ') + '. 위의 기존 항 수정에서 가격표를 변경하세요. 남은 숫자는 ‘항 생성’ 또는 ‘각 숫자를 1차원으로’에서 추가합니다.');
      }
      if (!plans.length) throw Error('새 가격표에 사용할 미사용 숫자가 없습니다. 위의 기존 항을 수정해 주세요.');
      return plans.map(({ bundle, fields, mode }, id) => ({
        id, kind: fields.length === 2 ? 'pair' : 'field', title: `${group().label} · ${fields.map(k => label(bundle.fields.find(f => f.key === k))).join(' × ')}`,
        note: fields.length === 2 ? '두 숫자의 교차 금액 · 2차원 가격표' : '한 숫자의 기준별 금액 · 1차원 가격표',
        source: sources.findIndex(s => s.group.groupKey === groupKey && s.bundle.key === bundle.key), fields, mode, value: '', term: null
      }));
    }
    function planHtml() {
      if (group().control !== 'NUMBER') return '';
      const bundles = group().bundles.filter(b => b.fields.length >= 2), bundle = bundles.find(b => b.key === planBundle);
      return `<section class="pm-batch-plan"><h3>숫자 가격표 구성 선택</h3><p>W·D·H를 같은 묶음으로 등록해도 각각 1차원 항 3개를 만들 수 있습니다. 두 숫자만 묶어 2차원으로 계산하고 나머지를 별도 계산할 수도 있습니다.</p>${bundle ? `<div class="pms-form-grid two"><label><span>2차원으로 계산할 입력묶음</span><select data-plan-bundle>${bundles.map(b => opt(b.key, b.label || b.fields.map(label).join(' / '), planBundle)).join('')}</select></label><fieldset class="pms-inline-fieldset"><legend>두 숫자 선택 · 최대 2개</legend><div class="pms-check-grid">${bundle.fields.map(f => `<label class="pms-check"><input type="checkbox" data-plan-field value="${e(f.key)}" ${planFields.includes(f.key) ? 'checked' : ''}>${e(label(f))}</label>`).join('')}</div></fieldset></div>` : `<p class="pms-help">현재 저장된 프로세스에는 숫자 2개 이상인 입력묶음이 없습니다. 가로와 높이를 함께 계산하려면 프로세스의 ‘가능한 답변 등록’에서 두 필드를 체크 → ‘선택 필드 그룹화’ → ‘이 질문 완성’ → ‘검증 후 등록완료’를 실행한 뒤 가격계산 화면을 다시 여세요.${processUrl ? ` <a href="${e(processUrl)}" target="_blank" rel="noopener">프로세스 확인</a>` : ''}</p>`}<div class="pms-actions"><button type="button" data-plan="EACH">각 숫자를 1차원으로</button><button type="button" data-plan="PAIR" ${planFields.length === 2 ? '' : 'disabled'}>선택한 두 숫자로 2차원</button><button type="button" data-plan="MIXED" ${planFields.length === 2 && bundle?.fields.length > 2 ? '' : 'disabled'}>2차원 + 나머지 1차원</button></div><small class="pms-help">구성을 선택하면 아래의 새 항 목록을 교체합니다. 반복해서 눌러도 같은 가격표가 더 추가되지 않습니다. 한 항에는 가격표 하나만 작성합니다. 기존 등록 항과 기존 항의 수정 내용은 유지됩니다. 이미 가격표에 사용 중인 숫자는 새 구성에서 제외하며 기존 항을 수정합니다. 나머지 1차원은 선택한 입력묶음의 남은 숫자에만 만듭니다. W·H 묶음과 독립 D라면 W·H 표를 반영한 뒤 메인 화면의 ‘항 생성’에서 남은 D를 선택하세요. 같은 숫자의 가격표는 적용 조건이 겹치지 않아야 합니다.</small></section>`;
    }
    function used(row) {
      return definition.terms.filter(t => {
        if (t.role === 'FACTOR') return false;
        if (row.kind === 'choice') return t.groupKey === groupKey && (!t.choiceKeys?.length || t.choiceKeys.includes(row.choiceKey)) || t.conditions?.some(c => c.groupKey === groupKey && c.choiceKeys?.includes(row.choiceKey));
        return t.groupKey === groupKey && t.bundleKey === sources[row.source]?.bundle.key && t.fieldKeys?.some(k => row.fields.includes(k));
      }).length;
    }
    function rowHtml(row) {
      const numeric = row.kind !== 'choice' || !['SKIP', 'FIXED'].includes(row.mode), source = sources[row.source];
      const allowed = row.kind === 'pair' ? ['SKIP', 'MATRIX_2D'] : ['SKIP', 'FIXED', ...(row.kind !== 'choice' || sources.length ? ['MULTIPLY', 'MATRIX_1D', ...(row.kind === 'choice' && sources.some(s => s.bundle.fields.length >= 2) ? ['MATRIX_2D'] : [])] : [])];
      let controls = `<label><span>계산 방식</span><select data-batch-mode="${row.id}">${allowed.map(v => opt(v, row.kind === 'choice' && v === 'FIXED' ? '고정·숫자 범위별 금액' : types[v], row.mode)).join('')}</select></label>`;
      if (row.mode === 'SKIP') controls += '<div class="pms-control-field"><span class="pms-field-label">설정 안내</span><p class="pm-batch-control-note">기존 항은 유지됩니다. 새로 추가할 항목의 계산 방식을 선택하세요.</p></div>';
      else {
        if (numeric && row.kind !== 'field') controls += `<label><span>연결할 숫자 입력묶음</span><select data-batch-source="${row.id}"><option value="">묶음을 선택하세요</option>${sources.filter(s => row.kind !== 'pair' || s.group.groupKey === groupKey).filter(s => row.mode !== 'MATRIX_2D' || s.bundle.fields.length >= 2).map(s => opt(sources.indexOf(s), `${s.group.label} / ${s.bundle.label || s.bundle.fields.map(label).join(' · ')}`, row.source)).join('')}</select></label>${source ? `<fieldset class="pm-batch-fields"><legend>${row.mode === 'MATRIX_2D' ? '함께 계산할 숫자 2개' : '계산할 숫자 1개'}</legend>${source.bundle.fields.map(f => `<label class="pms-check"><input type="checkbox" data-batch-field="${row.id}" value="${e(f.key)}" ${row.fields.includes(f.key) ? 'checked' : ''}>${e(label(f))}${f.unit ? ' (' + e(f.unit) + ')' : ''}</label>`).join('')}</fieldset>` : ''}`;
        if (['FIXED', 'MULTIPLY'].includes(row.mode)) controls += `<label><span>${row.mode === 'FIXED' ? '한 번 더할 금액 (원)' : '입력 1단위당 단가 (원)'}</span><input type="number" min="0" step="${row.mode === 'FIXED' ? '0.01' : '0.000001'}" data-batch-value="${row.id}" value="${e(row.value)}" placeholder="무료인 경우에도 0 입력">${row.term?.optionPricing ? '<small class="pms-help">범위별 가격 사용 중 · 구간 금액은 세부설정에서 수정하세요.</small>' : ''}</label>`;
        else controls += `<div class="pms-control-field"><span class="pms-field-label">가격표 상태</span><div class="pm-batch-table-state"><strong>${row.term ? '가격표 작성 완료' : '가격표 작성 필요'}</strong><small>${row.term ? e(summary(row.term)) : '기준값·경계 처리·금액을 설정하세요.'}</small></div></div>`;
        controls += `<div class="pms-control-field pm-batch-detail"><span class="pms-field-label">${row.mode.startsWith('MATRIX') ? '가격표 편집' : '세부 설정'}</span><div class="pms-actions"><button type="button" data-batch-detail="${row.id}">${row.mode.startsWith('MATRIX') ? '가격표 설정' : row.kind === 'choice' && row.mode === 'FIXED' ? '숫자 범위별 옵션 가격' : '이름·조건 설정'}</button></div></div>`;
      }
      return `<article class="pm-price-batch-row" data-batch-row="${row.id}"><header><div><strong>${e(row.title)}</strong><small>${e(row.note)}${used(row) ? ` · 기존 관련 항 ${used(row)}개` : ''}</small></div>${row.kind === 'pair' || row.mode.startsWith('MATRIX') ? `<button type="button" class="pm-icon-remove" data-batch-remove="${row.id}" aria-label="${e(row.title)} 새 가격표 삭제" title="새 가격표 삭제">×</button>` : ''}</header><div class="pm-price-batch-controls">${controls}</div></article>`;
    }
    function render() {
      body.innerHTML = `<ol class="pm-stepper"><li>1. 등록 옵션·기존 항 확인</li><li>2. 옵션별 방식·금액 설정</li><li>3. 변경 검토·항 반영</li></ol><p class="pms-help">이 제품의 프로세스에 등록된 옵션을 가져와 가격 항을 연결합니다. 옵션마다 계산 방식을 한 화면에서 정합니다. 기존 금액 항은 아래에서 수정하고, 추가할 항목만 선택하세요. 배율 항과 배율 연결은 메인 화면에서 수정합니다. 금액 항을 삭제하면 다른 항에서 사용하지 않는 연결 배율도 정리됩니다. 다른 입력의 항과 고정 추가금은 합산됩니다. 같은 가격 대상·숫자를 쓰는 가격표는 적용 조건이 겹치면 저장할 수 없습니다.</p><label><span>가격을 설정할 등록 그룹</span><select data-batch-group>${groups.map(g => opt(g.groupKey, g.label, groupKey)).join('')}</select></label><div data-batch-error role="alert" class="pms-alert" hidden></div><h3>기존 관련 항 ${originalTerms().length}개</h3><div class="pm-batch-existing">${originalTerms().map(original => { const t = changes.get(original.key) || original; return `<article><div><strong>${e(t.name)}${removed.has(t.key) ? ' · 삭제 예정' : changes.has(t.key) ? ' · 수정됨' : ''}</strong><p>${e(summary(t))}</p><small>${t.conditions?.length || 0}개 추가 조건 유지</small></div><div class="pms-actions"><button type="button" data-batch-existing="${e(t.key)}" ${removed.has(t.key) ? 'disabled' : ''}>기존 항 수정</button><button type="button" data-batch-existing-remove="${e(t.key)}" class="${removed.has(t.key) ? '' : 'pm-icon-remove'}" aria-label="${e(t.name)} ${removed.has(t.key) ? '삭제 취소' : '삭제'}" title="${removed.has(t.key) ? '삭제 취소' : '기존 항 삭제'}">${removed.has(t.key) ? '삭제 취소' : '×'}</button></div></article>`; }).join('') || '<p class="pms-help">아직 관련 항이 없습니다.</p>'}</div>${planHtml()}<h3>옵션별 새 항 추가</h3><p class="pms-help">고정 금액은 한 번 더합니다. 선택형의 ‘고정·숫자 범위별 옵션 가격’에서 앞의 사이즈 W 범위에 따라 LED 금액을 바꿀 수 있습니다. 범위별 금액은 기본금액과 중복 가산하지 않습니다. 곱셈은 선택한 숫자 × 단가입니다. 선택형 옵션의 곱셈·가격표는 숫자 입력에 연결하고 ‘이 옵션이 선택됨’을 필수 조건으로 붙입니다. 체크박스에서 여러 옵션을 선택하면 해당 옵션들의 항이 각각 더해집니다.</p><div class="pm-price-batch-rows">${rows.map(rowHtml).join('')}</div>`;
      const run = fn => Promise.resolve().then(fn).catch(ex => showError(ex.message));
      S.$('[data-batch-group]', body).onchange = event => run(async () => {
        const next = event.target.value;
        if (dirty() && !await S.confirm('현재 그룹에서 아직 반영하지 않은 설정을 버리고 다른 그룹을 여시겠습니까?')) { event.target.value = groupKey; return; }
        groupKey = next; reset(); render();
      });
      S.$('[data-plan-bundle]', body)?.addEventListener('change', event => {
        planBundle = event.target.value; planFields = group().bundles.find(b => b.key === planBundle).fields.slice(0, 2).map(f => f.key); render();
      });
      S.$$('[data-plan-field]', body).forEach(input => input.onchange = () => {
        const fields = S.$$('[data-plan-field]:checked', body).map(f => f.value);
        if (fields.length > 2) { input.checked = false; showError('2차원 가격표에는 숫자 2개만 선택할 수 있습니다. 남은 숫자는 1차원 항으로 추가하세요.'); return; }
        planFields = fields; render();
      });
      S.$$('[data-plan]', body).forEach(button => button.onclick = () => run(async () => {
        const next = plannedRows(button.dataset.plan);
        if (rows.some(r => r.mode !== 'SKIP') && !await S.confirm('아래의 새 항 작성 내용을 선택한 구성으로 바꾸시겠습니까? 작성 중인 새 금액·가격표·조건은 초기화하며 기존 등록 항과 기존 항 수정 내용은 유지합니다.')) return;
        rows = next; render(); S.$('.pm-price-batch-rows', body).scrollIntoView({ block: 'nearest' });
      }));
      S.$$('[data-batch-remove]', body).forEach(button => button.onclick = () => run(async () => {
        const index = Number(button.dataset.batchRemove), row = rows[index];
        if (row.term && !await S.confirm(`‘${row.title}’의 작성한 가격표와 조건을 삭제하시겠습니까?`)) return;
        if (row.kind === 'pair') rows.splice(index, 1); else { row.mode = 'SKIP'; row.term = null; row.value = ''; }
        rows.forEach((r, i) => r.id = i); render();
      }));
      S.$$('[data-batch-existing]', body).forEach(button => button.onclick = () => {
        const original = definition.terms.find(t => t.key === button.dataset.batchExisting);
        edit(changes.get(original.key) || original, next => { changes.set(original.key, next); render(); });
      });
      S.$$('[data-batch-existing-remove]', body).forEach(button => button.onclick = () => run(async () => {
        const key = button.dataset.batchExistingRemove;
        if (removed.has(key)) removed.delete(key);
        else if (await S.confirm('이 기존 항을 계산식에서 삭제할 예정으로 표시하시겠습니까? 변경 검토·항 반영 후 메인 화면에서 저장해야 최종 반영됩니다.')) removed.add(key);
        render();
      }));
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
        const maximum = row.mode === 'MATRIX_2D' ? 2 : 1;
        if (fields.length > maximum) { input.checked = false; throw Error(`이 계산 방식에는 숫자를 ${maximum}개만 연결할 수 있습니다. 3차원 엑셀 가격표는 지원하지 않으며, 나머지 숫자는 별도 항으로 추가해 주세요.`); }
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
        edit(term, next => { row.term = next; row.title = next.name; row.value = next.type === 'FIXED' ? next.amount : next.type === 'MULTIPLY' ? next.rates[row.fields[0]] : ''; render(); }, { locked: true, requiredCondition, choiceKey: row.kind === 'choice' && row.mode === 'FIXED' ? row.choiceKey : null, choiceGroup: groupKey });
      }));

    }
    S.$('[data-batch-apply]', d).onclick = event => S.run(event.currentTarget, async () => {
      try {
        const signature = () => JSON.stringify({ rows, changes: [...changes], removed: [...removed], groupKey });
        const before = signature();
        const additions = rows.filter(row => row.mode !== 'SKIP').map(row => {
          try { const term = B.term(row, group(), sources[row.source]); B.validate(term); return term; }
          catch (ex) { const error = Error(row.title + ': ' + ex.message); error.selector = row.mode.startsWith('MATRIX') || row.term?.optionPricing ? `[data-batch-detail="${row.id}"]` : `[data-batch-value="${row.id}"]`; throw error; }
        });
        if (!additions.length && !changes.size && !removed.size) throw Error('추가할 계산 방식을 선택하거나 기존 항을 수정해 주세요.');
        let current = definition.terms.map(t => changes.get(t.key) || t);
        for (const id of removed) current = S.pricingFactors.removeTerm(current, id);
        const affected = [...changes.values()].filter(t => !removed.has(t.key)).concat(additions);
        affected.forEach(B.validate);
        const all = [...current, ...additions];
        if (all.length > 100) throw Error('금액·배율 항은 합쳐서 최대 100개까지 가능합니다. 기존 항을 수정하거나 추가 수를 줄여 주세요.');
        S.pricingFactors.validateLinks(all);
        if (diagnose && all.length) {
          const validation = await diagnose(all);
          if (signature() !== before) throw Error("검사 중 작성 내용이 변경되었습니다. 변경 내용 검토를 다시 눌러 주세요.");
          if (!validation.valid) {
            const problems = validation.issues.filter(i => i.severity === 'ERROR'), box = S.$('[data-batch-error]', body);
            box.hidden = false;
            box.innerHTML = problems.map((issue, i) => `<section><strong>${e(issue.location)}</strong><p>${e(issue.message)}</p>${issue.target ? `<button type="button" data-batch-fix="${i}">해당 설정 수정</button>` : ''}</section>`).join('');
            S.$$('[data-batch-fix]', box).forEach(button => button.onclick = () => {
              const issue = problems[Number(button.dataset.batchFix)], match = issue.target.match(/^terms\.(.+)\.([xy])Axis\.outsidePolicy$/);
              const term = match && all.find(t => t.key === match[1]); if (!term) return;
              const row = rows.find(row => row.term?.key === term.key);
              edit(term, next => {
                if (row) { row.term = next; row.title = next.name; }
                else changes.set(next.key, next);
                render();
              }, { locked: !!row, focusAxis: match[2], choiceKey: row?.kind === 'choice' && row.mode === 'FIXED' ? row.choiceKey : null, choiceGroup: groupKey });
            });
            box.tabIndex = -1; box.scrollIntoView({ block: 'center' }); box.focus(); return;
          }
        }
        const overlapping = affected.filter(t => all.some(other => other.key !== t.key && B.overlap(t, other)));
        if (!await S.priceTable.review('일괄 설정 반영 전 확인', `<p><strong>기존 항 수정 ${[...changes.keys()].filter(k => !removed.has(k)).length}개 · 삭제 ${removed.size}개 · 새 항 추가 ${additions.length}개</strong></p>${removed.size ? `<p>삭제할 항: ${definition.terms.filter(t => removed.has(t.key)).map(t => e(t.name)).join(', ')}</p>` : ''}<ul>${affected.map(t => `<li><strong>${e(t.name)}</strong><br>${e(summary(t))}<br><small>추가 조건 ${t.conditions.length}개</small></li>`).join('')}</ul>${overlapping.length ? `<div class="pms-alert">같은 입력·옵션을 사용하는 다른 항이 있는 변경 항 ${overlapping.length}개: ${overlapping.map(t => e(t.name)).join(', ')}. 조건이 함께 성립하면 모두 더해집니다. 기존 항 수정으로 처리해야 하는지 확인하세요.</div>` : ''}<p class="pms-help">여기서 반영해도 아직 서버에 저장되지 않습니다. 메인 화면에서 실제 입력 테스트 후 저장하세요.</p>`, '확인한 항 반영')) return;
        if (signature() !== before) throw Error("검토 중 작성 내용이 변경되었습니다. 변경 내용 검토를 다시 눌러 주세요.");
        apply(current.concat(additions)); d.close();
      } catch (ex) { showError(ex.message); if (ex.selector) S.focusField(S.$(ex.selector, body), ex.message); }
    });
    reset(); render();
  };
})(window.PMS);
