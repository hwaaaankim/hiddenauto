(function (S) {
  'use strict';
  const F = S.pricingFactors = {}, e = S.e;
  const label = x => x?.labels?.management || x?.labels?.customer || x?.label || x?.key || '';
  const key = prefix => prefix + '_' + crypto.randomUUID().replaceAll('-', '');
  const opt = (v, text, selected) => `<option value="${e(v)}" ${String(v) === String(selected) ? 'selected' : ''}>${e(text)}</option>`;
  const aggregationLabels = { EXACT_ONE: '현재 조건에 맞는 값 하나', SUM: '선택한 옵션의 값을 더한 뒤 곱하기', PRODUCT: '선택한 옵션의 값을 서로 곱하기' };
  F.isFactor = term => term?.role === 'FACTOR';
  F.links = term => term?.multipliers || [];
  F.parents = (terms, key) => terms.filter(t => F.links(t).some(link => (link.termKeys || []).includes(key)));
  F.value = value => S.priceTable.money(value, 6);
  F.valueText = value => value == null || value === '' ? '미입력' : String(value) + '배';
  F.closure = (terms, term) => {
    const map = new Map(terms.map(t => [t.key, t])), found = new Set(), active = new Set();
    if (typeof term !== 'string') map.set(term.key, term);
    const visit = id => {
      if (active.has(id)) throw Error('배율 연결이 순환합니다. 연결한 항을 확인해 주세요.');
      if (found.has(id)) return;
      const current = map.get(id); if (!current) throw Error('연결한 항이 삭제되었거나 존재하지 않습니다: ' + id);
      active.add(id); F.links(current).forEach(link => (link.termKeys || []).forEach(visit)); active.delete(id); found.add(id);
    };
    visit(typeof term === 'string' ? term : term.key);
    return [...found].map(id => map.get(id));
  };
  F.testClosure = (terms, term) => {
    const current = terms.some(t => t.key === term.key) ? terms.map(t => t.key === term.key ? term : t) : [...terms, term];
    if (!F.isFactor(term)) return F.closure(current, term);
    const connected = new Map();
    current.filter(t => !F.isFactor(t)).forEach(root => {
      const branch = F.closure(current, root);
      if (branch.some(t => t.key === term.key)) branch.forEach(t => connected.set(t.key, t));
    });
    if (!connected.size) throw Error('배율 항은 금액 항에 연결한 뒤 계산할 수 있습니다. 금액 항의 × 배율 연결을 확인해 주세요.');
    return [...connected.values()];
  };
  F.duplicate = (terms, term) => {
    const source = F.closure(terms, term), ids = new Map(source.map(t => [t.key, key(F.isFactor(t) ? 'factor' : 'price')]));
    const copied = source.map(original => {
      const t = S.copy(original); t.key = ids.get(original.key); t.name = t.name.slice(0, 112) + ' 복사본';
      t.multipliers = F.links(t).map(link => ({ ...link, key: key('multiplier'), termKeys: link.termKeys.map(id => ids.get(id)) }));
      if (t.branches) t.branches = t.branches.map(b => ({...b,key:key('branch')}));
      if (t.adjustments) t.adjustments = t.adjustments.map(adjustment => ({ ...adjustment, key: key('adjustment') }));
      return t;
    });
    return { term: copied.find(t => t.key === ids.get(term.key)), terms: copied };
  };
  F.validateLinks = terms => {
    const map = new Map(terms.map(t => [t.key, t]));
    if (map.size !== terms.length) throw Error('가격 항의 내부 식별자가 중복됩니다.');
    for (const term of terms) {
      F.validateAdjustments?.(term);
      const linkKeys = new Set();
      for (const link of F.links(term)) {
        if (!link.key || linkKeys.has(link.key)) throw Error(term.name + ': 배율 연결 식별자가 없거나 중복됩니다.');
        linkKeys.add(link.key);
        if (!String(link.name || '').trim()) throw Error(term.name + ': 배율 연결 이름을 입력해 주세요.');
        if (!['EXACT_ONE', 'SUM', 'PRODUCT'].includes(link.aggregation || 'EXACT_ONE')) throw Error(term.name + ': 여러 배율의 계산 방식을 선택해 주세요.');
        if (!link.termKeys?.length || new Set(link.termKeys).size !== link.termKeys.length) throw Error(term.name + ': 연결할 배율 항을 중복 없이 선택해 주세요.');
        for (const id of link.termKeys) {
          if (id === term.key) throw Error(term.name + ': 자기 자신을 곱할 수 없습니다.');
          if (!F.isFactor(map.get(id))) throw Error(term.name + ': 배율 전용 항만 곱하기 대상으로 연결할 수 있습니다.');
        }
      }
      F.closure(terms, term);
    }
    return terms;
  };
  F.formula = (term, terms, expanded = false, visited = new Set()) => {
    if (visited.has(term.key)) return '순환 연결 오류';
    const path = new Set(visited); path.add(term.key);
    const children = F.links(term).map(link => {
      const names = (link.termKeys || []).map(id => {
        const child = terms.find(t => t.key === id);
        return !child ? '삭제된 항' : expanded && F.links(child).length ? F.formula(child, terms, true, path) : child.name;
      });
      return link.aggregation === 'SUM' ? '(' + names.join(' ＋ ') + ')' : link.aggregation === 'PRODUCT' ? '(' + names.join(' × ') + ')' : names.length === 1 ? names[0] : `${link.name} [조건에 맞는 값 1개]`;
    });
    const base = (term.adjustments || []).reduce((result, adjustment) => `(${result} → ${adjustment.name} [구간별 +/×])`, term.name);
    return children.length ? '(' + [base, ...children].join(' × ') + ')' : base;
  };
  // Remove only newly unreachable descendants of an explicitly removed term/link.
  // A factor still referenced elsewhere remains untouched.
  function prune(terms, seeds) {
    let next = terms.slice(), pending = new Set(seeds);
    while (pending.size) {
      let changed = false;
      for (const id of [...pending]) {
        const term = next.find(t => t.key === id);
        if (!term || !F.isFactor(term)) { pending.delete(id); continue; }
        if (F.parents(next, id).length) continue;
        F.links(term).flatMap(link => link.termKeys || []).forEach(child => pending.add(child));
        next = next.filter(t => t.key !== id); pending.delete(id); changed = true;
      }
      if (!changed) break;
    }
    return next;
  }
  F.removeTerm = (terms, id) => {
    const target = terms.find(t => t.key === id); if (!target) return terms;
    const parents = F.parents(terms, id);
    if (parents.length) throw Error(`‘${target.name}’을 사용하는 항: ${parents.map(t => t.name).join(', ')}. 각 항의 ‘× 배율 연결’에서 연결을 먼저 해제해 주세요. 연결되지 않은 배율은 함께 정리됩니다.`);
    return prune(terms.filter(t => t.key !== id), F.links(target).flatMap(link => link.termKeys || []));
  };
  F.removeLink = (terms, ownerKey, linkKey) => {
    const owner = terms.find(t => t.key === ownerKey), link = F.links(owner).find(l => l.key === linkKey);
    if (!owner || !link) return terms;
    return prune(terms.map(t => t.key === ownerKey ? { ...t, multipliers: F.links(t).filter(l => l.key !== linkKey) } : t), link.termKeys || []);
  };
  const condition = (groupKey, fieldKey, operator, lower, upper, lowerInclusive = true, upperInclusive = true) =>
    ({ groupKey, fieldKey, operator, choiceKeys: [], lower, upper, lowerInclusive, upperInclusive, text: null });
  F.rangeConditions = (groupKey, fieldKey, bounds, operators) => {
    S.priceTable.validateHeaders(bounds);
    if (operators.length !== bounds.length || operators.some(op => !['LE', 'LT'].includes(op))) throw Error('각 구간 끝값의 포함 여부를 선택해 주세요.');
    return bounds.map((bound, i) => i === 0
      ? condition(groupKey, fieldKey, operators[i], String(bound), null)
      : condition(groupKey, fieldKey, 'RANGE', String(bounds[i - 1]), String(bound), operators[i - 1] === 'LT', operators[i] === 'LE'))
      .concat(condition(groupKey, fieldKey, operators.at(-1) === 'LE' ? 'GT' : 'GE', String(bounds.at(-1)), null));
  };
  F.rangeLabels = (bounds, operators) => bounds.map((bound, i) =>
    `${i ? `${bounds[i - 1] || '미입력'} ${operators[i - 1] === 'LT' ? '이상' : '초과'} ~ ` : '허용 최소값 ~ '}${bound || '미입력'} ${operators[i] === 'LT' ? '미만' : '이하'}`)
    .concat(`${bounds.at(-1) || '미입력'} ${operators.at(-1) === 'LT' ? '이상' : '초과'} ~ 허용 최대값`);
  F.makePlan = ({ term, terms, groups, mode, groupKey, fieldKey, name, values = {}, bounds = [], operators = [], selectedKeys = [], aggregation, replaceKey }) => {
    const title = String(name || '').trim(); if (!title) throw Error('배율 연결 이름을 입력해 주세요.');
    if (title.length > 120) throw Error('배율 연결 이름은 120자 이내로 입력해 주세요.');
    const owner = terms.find(t => t.key === term.key); if (!owner) throw Error('연결할 원래 항을 먼저 반영해 주세요.');
    const old = F.links(owner).find(l => l.key === replaceKey), links = F.links(owner).filter(l => l.key !== replaceKey);
    let created = [], refs = [];
    const group = groups.find(g => g.groupKey === groupKey);
    const source = group?.bundles?.flatMap(b => b.fields.map(f => ({ bundle: b, field: f }))).find(x => x.field.key === fieldKey);
    const base = () => ({ key: key('factor'), name: title, groupKey, bundleKey: source?.bundle.key || null,
      fieldKeys: source ? [fieldKey] : [], type: 'FIXED', amount: null, rates: {}, xAxis: null, yAxis: null,
      values: [], conditions: [], match: 'ALL', choiceKeys: [], optionPricing: null, optionTarget: null, role: 'FACTOR', multipliers: [] });
    if (mode === 'EXISTING') {
      if (!selectedKeys.length) throw Error('연결할 배율 항을 선택해 주세요.');
      refs = [...selectedKeys];
      if (!aggregation) throw Error('선택한 배율을 어떻게 적용할지 정해 주세요.');
    } else if (mode === 'NUMBER') {
      if (group?.control !== 'NUMBER' || !source) throw Error('수량·배율로 사용할 숫자 입력 항목을 선택해 주세요.');
      created = [{ ...base(), type: 'MULTIPLY', amount: null, rates: { [fieldKey]: '1' } }]; aggregation = 'EXACT_ONE';
    } else if (mode === 'CHOICE') {
      if (!['RADIO', 'CHECKBOX'].includes(group?.control) || !group.choices?.length) throw Error('배수를 연결할 선택형 질문을 선택해 주세요.');
      if (group.control === 'CHECKBOX' && !['SUM', 'PRODUCT'].includes(aggregation)) throw Error('체크박스에서 여러 옵션을 선택했을 때 값을 더할지 곱할지 직접 선택해 주세요.');
      if (group.control === 'RADIO') aggregation = 'EXACT_ONE';
      created = group.choices.map(choice => ({ ...base(), name: (title + ' · ' + label(choice)).slice(0, 120), amount: F.value(values[choice.key]), choiceKeys: [choice.key] }));
    } else if (mode === 'RANGE') {
      if (group?.control !== 'NUMBER' || !source) throw Error('범위를 판단할 숫자 입력 항목을 선택해 주세요.');
      const conditions = F.rangeConditions(groupKey, fieldKey, bounds, operators), labels = F.rangeLabels(bounds, operators);
      created = conditions.map((c, i) => ({ ...base(), name: (title + ' · ' + labels[i]).slice(0, 120), amount: F.value(values[i]), conditions: [c] })); aggregation = 'EXACT_ONE';
    } else throw Error('배율을 가져올 방식을 선택해 주세요.');
    refs = refs.length ? refs : created.map(t => t.key);
    const link = { key: replaceKey || key('multiplier'), name: title, termKeys: refs, aggregation };
    let next = terms.map(t => t.key === owner.key ? { ...t, multipliers: [...links, link] } : t).concat(created);
    if (old) next = prune(next, old.termKeys || []);
    if (next.length > 100) throw Error(`금액·배율 항은 합쳐서 100개까지 등록할 수 있습니다. 현재 계획은 ${next.length}개입니다.`);
    F.validateLinks(next);
    return { terms: next, link, created };
  };
  F.adjustmentOperationText = entry => {
    const [whole, decimal] = String(entry?.value ?? '').split('.');
    const value = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (decimal ? '.' + decimal : '');
    if (!value) return '값 미입력';
    return entry.operation === 'ADD' ? '+ ' + value + '원' : entry.operation === 'MULTIPLY' ? '× ' + value + '배' : '연산 미선택';
  };
  const adjustmentValue = (operation, value) => {
    if (!['ADD', 'MULTIPLY'].includes(operation)) throw Error('각 구간에서 더하기 또는 곱하기를 선택해 주세요.');
    try { return { operation, value: S.priceTable.money(value, operation === 'ADD' ? 2 : 6) }; }
    catch { throw Error(operation === 'ADD' ? '더할 금액은 0 이상, 최대 9,999,999,999,999.99원·소수 2자리로 입력하세요. 빈칸은 0원이 아닙니다.' : '곱할 배율은 0 이상, 최대 9,999,999,999,999.99·소수 6자리로 입력하세요. 그대로 유지하려면 1을 입력합니다.'); }
  };
  F.validateAdjustments = (term, groups) => {
    if (term.adjustments == null) return term;
    if (!Array.isArray(term.adjustments) || term.adjustments.length > 20) throw Error('한 금액 항에는 조건별 계산을 20개까지 연결할 수 있습니다.');
    if (F.isFactor(term) && term.adjustments.length) throw Error('조건별 더하기·곱하기는 금액 항 안에만 연결할 수 있습니다. 배율 전용 항에는 원 단위 금액을 더하지 않습니다.');
    const keys = new Set();
    for (const adjustment of term.adjustments) {
      if (!adjustment || !/^[A-Za-z0-9_-]{1,80}$/.test(adjustment.key || '') || keys.has(adjustment.key)) throw Error('조건별 계산의 내부 식별자가 없거나 중복됩니다.');
      keys.add(adjustment.key);
      if (!String(adjustment.name || '').trim() || String(adjustment.name).length > 120) throw Error('조건별 계산 이름을 1~120자로 입력해 주세요.');
      const axis = adjustment.axis;
      if (!adjustment.groupKey || !axis?.fieldKey) throw Error('조건을 판단할 숫자 질문과 입력 항목을 선택해 주세요.');
      if (groups) {
        const group = groups.find(g => g.groupKey === adjustment.groupKey);
        if (group?.control !== 'NUMBER' || !(group.bundles || []).some(b => b.fields?.some(f => f.key === axis.fieldKey))) throw Error('조건별 계산에 연결한 숫자 질문·입력이 현재 구성에 없습니다.');
      }
      S.priceTable.validateAxis(axis);
      if (!['LE', 'LT'].includes(axis.operator) || axis.headers.some((_, i) => !['LE', 'LT'].includes(S.priceTable.operator(axis, i))) || axis.outsidePolicy && axis.outsidePolicy !== 'ERROR') throw Error('조건별 계산은 작은 순서의 구간 끝값과 이하·미만으로 나눕니다. 마지막 초과 구간은 따로 설정하세요.');
      if (!Array.isArray(adjustment.values) || adjustment.values.length !== axis.headers.length) throw Error('구간 경계와 연산·값의 개수가 다릅니다.');
      [...adjustment.values, adjustment.above].forEach((entry, i) => {
        try { adjustmentValue(entry?.operation, entry?.value); }
        catch (error) { throw Error(`${adjustment.name} · 구간 ${i + 1}: ${error.message}`); }
      });
    }
    return term;
  };
  F.makeAdjustmentPlan = ({ term, terms, groups, name, groupKey, fieldKey, bounds = [], operators = [], operations = {}, values = {}, replaceKey = null, sameGroupOnly = false }) => {
    const owner = terms.find(t => t.key === term.key);
    if (!owner) throw Error('조건별 계산을 연결할 금액 항을 먼저 반영해 주세요.');
    if (F.isFactor(owner)) throw Error('조건별 더하기·곱하기는 금액 항 안에만 연결할 수 있습니다.');
    const current = owner.adjustments || [], at = current.findIndex(a => a.key === replaceKey);
    if (replaceKey && at < 0) throw Error('수정하려던 조건별 계산이 삭제되었습니다. 현재 항을 다시 확인해 주세요.');
    const title = String(name || '').trim();
    // Reuse the same exact boundary validation as the existing range multiplier.
    F.rangeConditions(groupKey, fieldKey, bounds, operators);
    const entries = Array.from({ length: bounds.length + 1 }, (_, i) => {
      try { return adjustmentValue(operations[i], values[i]); }
      catch (error) { throw Error(`구간 ${i + 1}: ${error.message}`); }
    });
    const adjustment = { ...(sameGroupOnly ? { sameGroupOnly: true } : {}), key: replaceKey || key('adjustment'), name: title, groupKey,
      axis: { fieldKey, operator: 'LE', headers: bounds.map(v => String(v).trim()), operators: [...operators] },
      values: entries.slice(0, -1), above: entries.at(-1) };
    const adjustments = at < 0 ? [...current, adjustment] : current.map((a, i) => i === at ? adjustment : a);
    const nextOwner = { ...owner, adjustments };
    F.validateAdjustments(nextOwner, groups);
    const next = terms.map(t => t.key === owner.key ? nextOwner : t);
    F.validateLinks(next);
    return { terms: next, adjustment };
  };
  F.openAdjustment = ({ term, terms, groups = [], process = {}, diagnose, apply, onTest, adjustmentKey = null, sameGroupOnly = false }) => {
    const initial = S.copy(terms), owner = initial.find(t => t.key === term.key);
    if (!owner || F.isFactor(owner)) throw Error('먼저 반영한 금액 항 안에서 조건별 계산을 연결해 주세요.');
    const current = owner.adjustments || [], existing = current.find(a => a.key === adjustmentKey);
    if (adjustmentKey && !existing) throw Error('수정할 조건별 계산을 찾지 못했습니다. 현재 항을 다시 확인해 주세요.');
    if (!existing && current.length >= 20) throw Error('한 금액 항에는 조건별 계산을 최대 20개까지 연결할 수 있습니다. 기존 연결을 수정하거나 삭제해 주세요.');
    const numeric = groups.filter(g => g.control === 'NUMBER' && g.bundles?.some(b => b.fields?.length));
    const descriptor = F.sourceDescription(owner, groups, process), position = existing ? current.indexOf(existing) : current.length;
    let stage = 0, groupKey = existing?.groupKey || '', fieldKey = existing?.axis?.fieldKey || '', name = existing?.name || '',
      bounds = existing ? existing.axis.headers.map(String) : [''], operators = existing ? existing.axis.headers.map((_, i) => S.priceTable.operator(existing.axis, i)) : ['LE'],
      operations = {}, values = {}, seqStart = '', seqStep = '', seqCount = '', plan = null, actual = null, sequence = 0, controller = null;
    if (existing) [...existing.values, existing.above].forEach((entry, i) => { operations[i] = entry.operation; values[i] = String(entry.value); });
    const d = S.dialog('조건별 더하기·곱하기 · ' + descriptor.source, '', { foot: '<span data-adjustment-status role="status"></span><button type="button" data-adjustment-cancel-check hidden>검사 중지</button><button type="button" data-adjustment-prev>이전</button><button type="button" data-adjustment-next class="primary">다음 · 구간별 계산</button><button type="button" data-adjustment-apply class="primary" hidden>검사 후 현재 항에 반영</button>' });
    d.classList.add('pm-factor-editor', 'pm-adjustment-editor');
    const body = S.$('.pms-dialog-body', d), snapshot = () => JSON.stringify({ groupKey, fieldKey, name, bounds, operators, operations, values, seqStart, seqStep, seqCount });
    const start = snapshot(); S.priceTable.guardClose(d, () => snapshot() !== start);
    const group = () => numeric.find(g => g.groupKey === groupKey);
    const fields = () => (group()?.bundles || []).flatMap(b => b.fields || []);
    const field = () => fields().find(f => f.key === fieldKey);
    const error = message => { const box = S.$('[data-adjustment-error]', body); if (!box) return; box.hidden = false; box.textContent = message; box.tabIndex = -1; box.focus(); };
    const run = fn => Promise.resolve().then(fn).catch(ex => { if (d.open) error(ex.message); });
    const invalidate = () => { sequence++; controller?.abort(); controller = null; actual = null; S.$('[data-adjustment-cancel-check]', d).hidden = true; S.$('[data-adjustment-apply]', d).disabled = false; S.$('[data-adjustment-status]', d).textContent = '작성 중인 내용은 아직 현재 항에 반영되지 않았습니다.'; };
    const input = (text, attr, value = '', placeholder = '') => `<label><span>${e(text)}</span><input ${attr} value="${e(value)}" placeholder="${e(placeholder)}"></label>`;
    const amountText = value => { const [whole, decimal] = String(value ?? '').split('.'); return whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (decimal ? '.' + decimal : '') + '원'; };
    const sourceValid = () => !!group() && !!field();
    function contextHtml() {
      const order = [...current.slice(0, position).map(a => a.name), name.trim() || '이번 조건별 계산', ...current.slice(position + (existing ? 1 : 0)).map(a => a.name)];
      return `<section class="pm-factor-context"><strong>어느 금액 안에 연결하나요?</strong><p>${e(descriptor.result)}</p><p><strong>현재 금액 항 ‘${e(owner.name)}’ 안의 계산 연결입니다.</strong> 별도 금액 항을 추가하지 않습니다.</p><div class="pm-adjustment-order"><span>기본 계산값</span>${order.map((text, i) => `<span>→ ${i + 1}. ${e(text)}</span>`).join('')}<span>→ ${F.links(owner).length ? '기존 수량·배율 (' + F.links(owner).map(l => e(l.name)).join(' · ') + ')' : '수량·배율을 연결하면 그 값을 곱함'}</span><strong>→ 이 항의 최종 금액</strong></div><small>조건별 계산은 위 순서대로 앞의 결과에 적용합니다. 예: (표 금액 + 추가금) × 수량. 최종 금액은 계산을 모두 마친 뒤 반올림합니다.</small></section>`;
    }
    function sourceHtml() {
      const remaining = (numeric.find(g => g.groupKey === owner.groupKey)?.bundles || []).filter(b => !owner.bundleKey || b.key === owner.bundleKey).flatMap(b => b.fields || []).filter(f => !(owner.fieldKeys || []).includes(f.key));
      return `${input('조건별 계산 이름', 'data-adjustment-name maxlength="120"', name, '예: 깊이에 따른 가격 계산')}<div class="pms-form-grid two"><label><span>조건을 판단할 숫자 질문</span><select data-adjustment-group><option value="">질문을 선택하세요</option>${groupKey && !group() ? opt(groupKey, '현재 구성에서 삭제된 질문 · 다시 선택', groupKey) : ''}${numeric.map(g => opt(g.groupKey, g.label, groupKey)).join('')}</select></label><label><span>숫자 입력 항목</span><select data-adjustment-field><option value="">항목을 선택하세요</option>${fieldKey && !field() ? opt(fieldKey, '현재 구성에서 삭제된 입력 · 다시 선택', fieldKey) : ''}${fields().map(f => opt(f.key, label(f) + (f.unit ? ' (' + f.unit + ')' : ''), fieldKey)).join('')}</select></label></div>${remaining.length ? `<p class="pms-help">이 기본가격 항에서 아직 사용하지 않은 입력: ${remaining.map(f => e(label(f))).join(' · ')}. 깊이별 가격을 정하려면 위에서 해당 입력을 선택하세요.</p>` : ''}${!numeric.length ? '<p class="pms-alert">현재 가격 조건으로 연결할 숫자 질문이 없습니다. 프로세스에 숫자 입력을 등록하고 해당 그룹의 가격영향 설정을 확인해 주세요.</p>' : ''}<div class="pms-help"><strong>프로세스 조건과 가격 조건은 다릅니다.</strong><p>프로세스 조건은 질문 표시, 선택 가능한 답변, 숫자의 최소·최대값을 결정합니다. 이 화면의 가격 조건은 프로세스에서 표시·허용된 숫자로 이 항에 더할지 곱할지만 결정합니다. 질문이나 주문 가능 범위를 변경하지 않습니다.</p></div>${rangeSource()}`;
    }
    function rangeSource() {
      if (!field()) return '';
      return `<div class="pms-help"><strong>${e(group().label)} · ${e(label(field()))}의 프로세스 입력 범위</strong><p>최소 ${e(field().min ?? (field().allowNegative ? '제한 없음' : 0))} / 최대 ${e(field().max ?? '제한 없음')} / 입력 간격 ${e(field().step ?? '자유')}${field().unit ? ' ' + e(field().unit) : ''}</p><p>실제 분기에서 허용되는 범위 안에만 적용합니다. 이 항이 적용될 때 조건 입력이 숨겨지거나 허용되지 않으면 임의의 추가금 0원·배율 1로 처리하지 않습니다. 실제 입력 테스트와 전체 검증에서 확인합니다.</p></div>`;
    }
    function valueControls(index) {
      const operation = operations[index] || '', adding = operation === 'ADD';
      return `<label><span>이 구간의 계산</span><select data-adjustment-operation="${index}">${opt('', '더하기 또는 곱하기 선택', operation)}${opt('ADD', '앞의 금액에 더하기 (+ 원)', operation)}${opt('MULTIPLY', '앞의 금액에 곱하기 (× 배)', operation)}</select></label>${input(adding ? '더할 금액 (원)' : operation ? '곱할 배율 (배)' : '연산 선택 후 값 입력', `data-adjustment-value="${index}" type="number" min="0" step="${adding ? '0.01' : '0.000001'}" ${operation ? '' : 'disabled'}`, values[index] ?? '', adding ? '예: 30000' : operation ? '예: 2' : '')}`;
    }
    function rangesHtml() {
      const labels = F.rangeLabels(bounds, operators);
      return `${rangeSource()}<p class="pms-help">끝값을 작은 순서로 입력하면 다음 시작값을 자동으로 이어서 겹침과 빈 구간을 막습니다. 각 구간은 더하기·곱하기 중 하나를 선택합니다. 0원 더하기·1배 곱하기는 그대로 유지하며, 0배는 결과를 0원으로 만듭니다. 값을 비우면 반영하지 않습니다.</p><details class="pm-factor-sequence"><summary>같은 간격으로 구간 끝값 만들기</summary><div class="pms-form-grid two">${input('첫 구간 끝값', 'data-adjustment-seq-start type="number" min="0" step="any"', seqStart)}${input('증가 간격', 'data-adjustment-seq-step type="number" min="0" step="any"', seqStep)}${input('끝값 개수', 'data-adjustment-seq-count type="number" min="1" max="200" step="1"', seqCount)}</div><button type="button" data-adjustment-generate>경계 초안 만들기</button></details><div class="pm-factor-range-rows pm-adjustment-range-rows">${bounds.map((bound, i) => `<article><strong data-adjustment-caption="${i}">${e(label(field()))} · ${e(labels[i])}</strong><div class="pms-form-grid two">${input('구간 ' + (i + 1) + ' 끝값', `data-adjustment-bound="${i}" type="number" min="0" step="any"`, bound)}<label><span>끝값 자체의 처리</span><select data-adjustment-bound-op="${i}">${opt('LE', '끝값까지 현재 구간 (이하)', operators[i])}${opt('LT', '끝값부터 다음 구간 (미만)', operators[i])}</select></label>${valueControls(i)}</div><button type="button" data-adjustment-remove="${i}" ${bounds.length === 1 ? 'disabled' : ''}>경계 삭제·인접 구간 합치기</button></article>`).join('')}<article><strong data-adjustment-caption="${bounds.length}">${e(label(field()))} · ${e(labels.at(-1))}</strong><div class="pms-form-grid two">${valueControls(bounds.length)}</div><small>마지막 구간도 직접 정합니다. 프로세스의 최대값을 여기서 변경하지 않습니다.</small></article></div><button type="button" data-adjustment-add ${bounds.length >= 200 ? 'disabled' : ''}>＋ 구간 추가</button>`;
    }
    function actualHtml() {
      if (!actual) return '<p class="pms-help">실제 W·H 등 필요한 답변과 조건 입력을 넣어 계산하면, 표에서 결정된 금액과 이 연결의 계산 결과를 여기에서도 확인할 수 있습니다. 임의의 기본금액을 실제 가격처럼 사용하지 않습니다.</p>';
      return `<div class="pm-price-review"><strong>${e(label(field()))} = ${e(actual.input)}일 때</strong><p>${e(amountText(actual.before))} ${e(F.adjustmentOperationText({ operation: actual.operation, value: actual.value }))} = <strong>${e(amountText(actual.after))}</strong></p><small>${e(actual.range || '')} · 이 보정 직전/직후 금액입니다. 이후 보정과 수량·배율은 이어서 적용됩니다.</small></div>`;
    }
    function existingSourceWarning() {
      const usesSource = t => t.groupKey === groupKey && (t.fieldKeys || []).includes(fieldKey)
        || t.optionPricing?.groupKey === groupKey && t.optionPricing.axis?.fieldKey === fieldKey
        || (t.conditions || []).some(c => c.groupKey === groupKey && c.fieldKey === fieldKey)
        || (t.adjustments || []).some(a => a.groupKey === groupKey && a.axis?.fieldKey === fieldKey);
      const otherPrices = initial.filter(t => t.key !== owner.key && !F.isFactor(t) && usesSource(t));
      const links = F.links(owner).filter(link => (link.termKeys || []).some(id => {
        try { return F.closure(initial, id).some(usesSource); } catch { return false; }
      }));
      if (!otherPrices.length && !links.length) return '';
      return `<div class="pms-alert" data-adjustment-existing-warning><strong>${e(label(field()))}을 사용하는 기존 설정도 있습니다.</strong>${otherPrices.length ? `<p>별도 금액 항: ${otherPrices.map(t => e(t.name)).join(' · ')}</p>` : ''}${links.length ? `<p>현재 항의 기존 수량·배율 연결: ${links.map(l => e(l.name)).join(' · ')}</p>` : ''}<p>새 조건별 연결이 기존 설정을 자동으로 대체하지 않습니다. 각 적용 조건이 성립하면 기존 항·배율도 함께 적용됩니다. 대체하려는 경우 메인 화면에서 기존 금액 항을 수정·삭제하거나 배율 연결을 해제하세요.</p></div>`;
    }
    function reviewHtml() {
      const adjustment = plan.adjustment, labels = F.rangeLabels(adjustment.axis.headers, adjustment.axis.operators);
      return `<div class="pm-price-review"><strong>${e(descriptor.result)}에 연결할 ‘${e(adjustment.name)}’</strong><p>B = ${position ? '앞선 조건별 계산까지 적용한 금액' : '이 항의 기본 계산값'}입니다. 아래에서 현재 입력에 맞는 구간 하나만 적용합니다.</p></div><div class="pm-factor-review">${[...adjustment.values, adjustment.above].map((entry, i) => `<article><strong>${e(label(field()))} · ${e(labels[i])}</strong><span>B ${e(F.adjustmentOperationText(entry))}</span></article>`).join('')}</div><p class="pms-help">현재 금액 항의 조건별 연결 ${position + 1}번째에 ${existing ? '수정' : '추가'}합니다. 기존 가격표, 다른 조건별 연결, 수량·배율 연결은 유지합니다. 새 금액 항은 생성하지 않습니다.</p>${existingSourceWarning()}<section class="pm-adjustment-preview"><strong>실제 기본금액으로 계산 확인</strong><div data-adjustment-actual>${actualHtml()}</div>${onTest ? '<button type="button" data-adjustment-test>실제 입력으로 이 항·조건별 계산 테스트</button>' : '<p class="pms-help">현재 항에 반영한 뒤 메인 화면에서 실제 입력을 테스트하세요.</p>'}</section><p class="pms-help">반영 버튼은 전체 연결을 검증합니다. 항별 테스트가 성공해도 전체 검증과 최종 저장은 별도로 통과해야 합니다.</p>`;
    }
    function draw() {
      body.innerHTML = `<ol class="pm-stepper"><li class="${stage === 0 ? 'active' : ''}">1. 조건 입력 연결</li><li class="${stage === 1 ? 'active' : ''}">2. 구간별 +·× 지정</li><li class="${stage === 2 ? 'active' : ''}">3. 실제 계산·검토</li></ol>${contextHtml()}<div data-adjustment-error class="pms-alert" role="alert" hidden></div>${stage === 0 ? sourceHtml() : stage === 1 ? rangesHtml() : reviewHtml()}`;
      S.$('[data-adjustment-prev]', d).hidden = stage === 0;
      S.$('[data-adjustment-next]', d).hidden = stage === 2;
      S.$('[data-adjustment-next]', d).disabled = stage === 0 && !sourceValid();
      S.$('[data-adjustment-next]', d).textContent = stage === 0 ? '다음 · 구간별 계산' : '다음 · 실제 계산·검토';
      S.$('[data-adjustment-apply]', d).hidden = stage !== 2;
      S.$('[data-adjustment-name]', body)?.addEventListener('input', ev => { invalidate(); name = ev.target.value; });
      S.$('[data-adjustment-group]', body)?.addEventListener('change', ev => run(async () => {
        const next = ev.target.value;
        if ((Object.values(values).some(v => v !== '') || bounds.some(v => v !== '')) && !await S.confirm('조건 질문을 바꾸면 기존 구간과 연산·값을 비웁니다. 새 입력에 맞게 다시 작성하시겠습니까?')) { ev.target.value = groupKey; return; }
        invalidate(); groupKey = next; fieldKey = ''; bounds = ['']; operators = ['LE']; operations = {}; values = {}; if (!name.trim()) name = group()?.label ? group().label + ' 조건별 계산' : ''; draw();
      }));
      S.$('[data-adjustment-field]', body)?.addEventListener('change', ev => run(async () => {
        const next = ev.target.value;
        if (fieldKey && fieldKey !== next && (Object.values(values).some(v => v !== '') || bounds.some(v => v !== '')) && !await S.confirm('조건 입력을 바꾸면 기존 구간과 연산·값을 비웁니다. 다시 작성하시겠습니까?')) { ev.target.value = fieldKey; return; }
        invalidate(); if (fieldKey && fieldKey !== next) { bounds = ['']; operators = ['LE']; operations = {}; values = {}; } fieldKey = next; draw();
      }));
      function captions() { const labels = F.rangeLabels(bounds, operators); S.$$('[data-adjustment-caption]', body).forEach(el => { el.textContent = label(field()) + ' · ' + labels[Number(el.dataset.adjustmentCaption)]; }); }
      S.$$('[data-adjustment-bound]', body).forEach(el => el.oninput = () => { invalidate(); bounds[Number(el.dataset.adjustmentBound)] = el.value; captions(); });
      S.$$('[data-adjustment-bound-op]', body).forEach(el => el.onchange = () => { invalidate(); operators[Number(el.dataset.adjustmentBoundOp)] = el.value; captions(); });
      S.$$('[data-adjustment-operation]', body).forEach(el => el.onchange = () => { invalidate(); const i = Number(el.dataset.adjustmentOperation); if (operations[i] !== el.value) values[i] = ''; operations[i] = el.value; draw(); });
      S.$$('[data-adjustment-value]', body).forEach(el => el.oninput = () => { invalidate(); values[Number(el.dataset.adjustmentValue)] = el.value; });
      for (const [selector, setter] of [['[data-adjustment-seq-start]', v => { seqStart = v; }], ['[data-adjustment-seq-step]', v => { seqStep = v; }], ['[data-adjustment-seq-count]', v => { seqCount = v; }]]) S.$(selector, body)?.addEventListener('input', ev => { invalidate(); setter(ev.target.value); });
      S.$('[data-adjustment-generate]', body)?.addEventListener('click', () => run(async () => {
        const next = S.priceTable.sequence(seqStart, seqStep, seqCount);
        if ((Object.values(values).some(v => v !== '') || Object.values(operations).some(Boolean)) && !await S.confirm('경계를 새로 만들면 각 구간의 더하기·곱하기와 값을 비웁니다. 다시 입력하시겠습니까?')) return;
        invalidate(); bounds = next; operators = next.map(() => 'LE'); operations = {}; values = {}; draw();
      }));
      S.$('[data-adjustment-add]', body)?.addEventListener('click', () => run(() => {
        if (bounds.length >= 200) throw Error('구간 끝값은 최대 200개까지 입력할 수 있습니다.');
        invalidate(); const i = bounds.length, oldValue = values[i], oldOperation = operations[i]; delete values[i]; delete operations[i]; bounds.push(''); operators.push('LE'); values[i + 1] = oldValue ?? ''; operations[i + 1] = oldOperation ?? ''; draw();
      }));
      S.$$('[data-adjustment-remove]', body).forEach(el => el.onclick = () => run(async () => {
        if (bounds.length <= 1) return;
        if (!await S.confirm('이 경계를 지우면 인접 구간이 합쳐집니다. 합쳐진 구간의 연산과 값은 비우므로 다시 입력해 주세요.')) return;
        invalidate(); const i = Number(el.dataset.adjustmentRemove), previousValues = { ...values }, previousOperations = { ...operations }, count = bounds.length;
        bounds.splice(i, 1); operators.splice(i, 1); values = {}; operations = {};
        for (let n = 0; n < count; n++) { values[n] = n < i ? previousValues[n] ?? '' : n === i ? '' : previousValues[n + 1] ?? ''; operations[n] = n < i ? previousOperations[n] ?? '' : n === i ? '' : previousOperations[n + 1] ?? ''; } draw();
      }));
      S.$('[data-adjustment-test]', body)?.addEventListener('click', () => run(() => {
        const signature = snapshot(), testPlan = plan;
        return onTest(testPlan.terms.find(t => t.key === owner.key), testPlan.terms, { onCalculated: result => {
          if (!d.open || signature !== snapshot() || testPlan !== plan) return;
          actual = result.complete ? result.terms?.find(t => t.key === owner.key)?.adjustments?.find(a => a.key === testPlan.adjustment.key) || null : null;
          const target = S.$('[data-adjustment-actual]', body); if (target) target.innerHTML = actualHtml();
        } });
      }));
    }
    S.$('[data-adjustment-prev]', d).onclick = () => { invalidate(); stage = Math.max(0, stage - 1); draw(); };
    S.$('[data-adjustment-next]', d).onclick = () => run(() => {
      if (!sourceValid()) throw Error('현재 구성에 있는 숫자 질문과 입력 항목을 선택해 주세요.');
      if (!name.trim() || name.trim().length > 120) throw Error('조건별 계산 이름을 1~120자로 입력해 주세요.');
      if (stage === 1) plan = F.makeAdjustmentPlan({ term: owner, terms: initial, groups, name, groupKey, fieldKey, bounds, operators, operations, values, replaceKey: adjustmentKey, sameGroupOnly: sameGroupOnly || existing?.sameGroupOnly });
      invalidate(); stage++; draw();
    });
    S.$('[data-adjustment-apply]', d).onclick = ev => S.run(ev.currentTarget, async () => {
      const serial = ++sequence, signature = snapshot(), currentPlan = plan;
      if (!currentPlan || stage !== 2) return;
      controller?.abort(); controller = new AbortController(); const signal = controller.signal;
      S.$('[data-adjustment-cancel-check]', d).hidden = false; S.$('[data-adjustment-status]', d).textContent = '현재 항과 전체 가격 연결을 검증하고 있습니다. 작성 내용은 유지됩니다.';
      try {
        if (diagnose) {
          const result = await diagnose(currentPlan.terms, signal);
          if (signal.aborted || !d.open || serial !== sequence || signature !== snapshot() || currentPlan !== plan) return;
          if (!result.valid || result.exhaustive === false) throw Error((result.issues || []).filter(i => i.severity === 'ERROR').map(i => `${i.location || '조건별 계산'}: ${i.message}`).join('\n') || '전체 연결 검증을 완료하지 못했습니다. 작성한 구간을 확인하고 다시 검사해 주세요.');
        }
        if (signal.aborted || !d.open || serial !== sequence || signature !== snapshot() || currentPlan !== plan) return;
        apply(currentPlan.terms); d.close();
      } catch (ex) { if (d.open && serial === sequence && ex.name !== 'AbortError') error(ex.message); }
      finally { if (d.open && serial === sequence) { controller = null; S.$('[data-adjustment-cancel-check]', d).hidden = true; S.$('[data-adjustment-status]', d).textContent = '현재 항에 반영하기 전까지 원래 계산식은 유지됩니다.'; } }
    });
    S.$('[data-adjustment-cancel-check]', d).onclick = () => { invalidate(); S.$('[data-adjustment-status]', d).textContent = '검사를 중지했습니다. 작성한 구간·연산·값은 유지됩니다.'; };
    d.addEventListener('close', () => { sequence++; controller?.abort(); controller = null; });
    draw(); return d;
  };
  // Describe the value being multiplied using registered inputs, never a generic term name alone.
  F.sourceDescription = (term, groups, process = {}) => {
    const g = (groups || []).find(g => g.groupKey === term.groupKey);
    const q = (process.questions || []).find(q => q.key === term.groupKey);
    const fields = g?.bundles?.flatMap(b => b.fields || []) || q?.fields || [];
    const selected = (term.fieldKeys || []).map(id => fields.find(f => f.key === id));
    const names = selected.filter(Boolean).map(label);
    const groupName = g?.label || label(q) || '현재 구성에서 삭제된 질문';
    const inputNames = names.length ? names.join(' · ') : '연결할 입력 항목';
    const unit = F.isFactor(term) ? '값' : '금액';
    const source = groupName + (names.length ? ' · ' + inputNames : '');
    let result;
    if (!g && !q || selected.some(f => !f)) result = '이전 질문·입력 연결을 현재 구성에 맞게 먼저 확인할 ' + unit;
    else if (term.type?.startsWith('MATRIX')) result = groupName + ' · ' + inputNames + ' 입력값으로 엑셀 가격표에서 결정된 ' + unit;
    else if (term.type === 'MULTIPLY') result = groupName + ' · ' + inputNames + ' 입력값에 등록 단가·계수를 적용해 계산한 ' + unit;
    else if (term.optionPricing) result = groupName + '에서 선택한 옵션과 등록 숫자 구간에 따라 결정된 ' + unit;
    else {
      const choices = (g?.choices || q?.choices || []).filter(c => (term.choiceKeys || []).includes(c.key)).map(label);
      result = (choices.length ? groupName + ' · ' + choices.join(' / ') + ' 선택에 정한 ' : groupName + '에 정한 ') + '고정 ' + unit;
    }
    return { source, result, unit, missing: !g && !q || selected.some(f => !f) };
  };
  F.sourceAvailability = (term, terms, groups, process = {}) => {
    const numeric = groups.filter(g => g.control === 'NUMBER' && g.bundles?.some(b => b.fields?.length));
    const choices = groups.filter(g => ['RADIO', 'CHECKBOX'].includes(g.control) && g.choices?.length);
    const byGroup = new Map(groups.map(g => [g.groupKey, g]));
    const byQuestion = new Map((process.questions || []).map(q => [q.key, q]));
    const validSource = t => {
      const g = byGroup.get(t.groupKey); if (!g) return false;
      const fields = new Set((g.bundles || []).flatMap(b => b.fields || []).map(f => f.key));
      if ((t.fieldKeys || []).some(id => !fields.has(id))) return false;
      if ((t.choiceKeys || []).some(id => !(g.choices || []).some(c => c.key === id))) return false;
      if (g.control === 'CHECKBOX' && t.choiceKeys?.length !== 1) return false;
      const references = [...(t.conditions || []), ...(t.optionPricing ? [{ groupKey: t.optionPricing.groupKey, fieldKey: t.optionPricing.axis?.fieldKey }] : []), ...(t.optionTarget ? [{ groupKey: t.optionTarget.groupKey, choiceKeys: [t.optionTarget.choiceKey] }] : [])];
      return references.every(c => {
        const source = byGroup.get(c.groupKey) || byQuestion.get(c.groupKey); if (!source) return false;
        return (!c.fieldKey || (source.fields || []).some(f => f.key === c.fieldKey) || (source.bundles || []).some(b => b.fields?.some(f => f.key === c.fieldKey)))
          && (c.choiceKeys || []).every(id => (source.choices || []).some(option => option.key === id));
      });
    };
    const existing = terms.filter(t => {
      if (!F.isFactor(t) || t.key === term.key) return false;
      try { const chain = F.closure(terms, t); return !chain.some(child => child.key === term.key) && chain.every(validSource); }
      catch { return false; }
    });
    return { numeric, choices, existing };
  };
  F.open = ({ term, terms, groups, process, diagnose, apply, onTest, linkKey = null }) => {
    const initial = S.copy(terms), existing = F.links(term).find(l => l.key === linkKey), sourceGroups = groups.filter(g => ['NUMBER', 'RADIO', 'CHECKBOX'].includes(g.control));
    const available = F.sourceAvailability(term, initial, sourceGroups, process), descriptor = F.sourceDescription(term, groups, process);
    const currentLinks = F.links(term).filter(l => l.key !== linkKey);
    const adjustments = term.adjustments || [], adjustedResult = descriptor.result + (adjustments.length ? '에 조건별 계산(' + adjustments.map(a => a.name).join(' → ') + ')까지 적용한 금액' : '');
    const remaining = available.numeric.flatMap(g => (g.bundles || []).filter(b => !term.bundleKey || b.key === term.bundleKey).flatMap(b => (b.fields || []).filter(f => g.groupKey === term.groupKey && !(term.fieldKeys || []).includes(f.key)).map(f => ({ group: g, field: f }))));
    const namesPreview = names => names.slice(0, 3).join(' · ') + (names.length > 3 ? ' 외 ' + (names.length - 3) + '개' : '');
    const numericNames = namesPreview(available.numeric.map(g => g.label)), choiceNames = namesPreview(available.choices.map(g => g.label));
    const modeInfo = [
      { value: 'NUMBER', title: '입력한 수량 그대로', detail: '고객이 입력한 숫자를 그대로 곱합니다. 수량을 입력받을 때 사용합니다.', example: '계산된 ' + descriptor.unit + ' × 입력 숫자 (수량 2 → × 2)', available: available.numeric.length > 0, sources: numericNames, reason: '숫자 입력 항목이 등록된 가격영향 질문이 없습니다.' },
      { value: 'CHOICE', title: '옵션마다 배수 지정', detail: '어떤 옵션을 선택했는지에 따라 관리자가 정한 숫자를 곱합니다.', example: '계산된 ' + descriptor.unit + ' × 옵션에 지정한 값 (2개 → 2, 보정 옵션 → 1.2 등)', available: available.choices.length > 0, sources: choiceNames, reason: '보기가 등록된 선택형 가격영향 질문이 없습니다.' },
      { value: 'RANGE', title: '숫자 구간마다 배수 지정', detail: '입력 숫자로 구간을 찾고, 그 구간에 정한 배율을 곱합니다. 깊이별 보정에 사용합니다.', example: '계산된 ' + descriptor.unit + ' × 해당 구간의 배율 (깊이 350 자체를 곱하는 방식이 아닙니다)', available: available.numeric.length > 0, sources: numericNames, reason: '구간을 판단할 숫자 입력 항목이 없습니다.' },
      { value: 'EXISTING', title: '기존 배율 항 연결', detail: '이미 만든 배율·수량 계산을 가져와 곱합니다. 같은 배율을 여러 금액에서 공유할 때 사용합니다.', example: '계산된 ' + descriptor.unit + ' × 기존 배율의 계산 결과', available: available.existing.length > 0, sources: namesPreview(available.existing.map(t => t.name)), reason: '현재 구성에서 사용할 수 있는 기존 배율이 없습니다. 새 배율을 먼저 만들면 여기에 표시됩니다.' }
    ];
    let stage = 0, plan = null, mode = existing && available.existing.length ? 'EXISTING' : '', groupKey = '', fieldKey = '', name = existing?.name || '', values = {},
      bounds = [''], operators = ['LE'], selectedKeys = [...(existing?.termKeys || [])], aggregation = existing?.aggregation || '', sequence = 0;
    let seqStart = '', seqStep = '', seqCount = '';
    const d = S.dialog('곱할 값 설정 · ' + descriptor.source, '', { foot: '<span data-factor-status></span><button type="button" data-factor-prev>이전</button><button type="button" class="primary" data-factor-next>다음 · 값 연결</button><button type="button" class="primary" data-factor-apply hidden>검사 후 계산식에 반영</button>' });
    d.classList.add('pm-factor-editor'); const body = S.$('.pms-dialog-body', d);
    const snapshot = () => JSON.stringify({ mode, groupKey, fieldKey, name, values, bounds, operators, selectedKeys, aggregation });
    const start = snapshot(); S.priceTable.guardClose(d, () => snapshot() !== start);
    const group = () => sourceGroups.find(g => g.groupKey === groupKey);
    const fields = () => (group()?.bundles || []).flatMap(b => b.fields.map(f => ({ ...f, bundle: b })));
    const currentField = () => fields().find(f => f.key === fieldKey);
    const error = message => { const box = S.$('[data-factor-error]', body); box.hidden = false; box.textContent = message; box.tabIndex = -1; box.focus(); };
    const run = fn => Promise.resolve().then(fn).catch(ex => error(ex.message));
    function input(text, attr, value = '', placeholder = '') { return `<label><span>${e(text)}</span><input ${attr} value="${e(value)}" placeholder="${e(placeholder)}"></label>`; }
    function sourceOptions() {
      const candidates = mode === 'CHOICE' ? available.choices : available.numeric;
      return `<label><span>값을 가져올 등록 질문</span><select data-factor-group><option value="">질문을 선택하세요</option>${candidates.map(g => opt(g.groupKey, g.label, groupKey)).join('')}</select></label>${group()?.control === 'NUMBER' ? `<label><span>숫자 입력 항목</span><select data-factor-field><option value="">입력 항목을 선택하세요</option>${fields().map(f => opt(f.key, label(f) + (f.unit ? ' (' + f.unit + ')' : ''), fieldKey)).join('')}</select></label>` : ''}`;
    }
    function rangeSource() {
      const f = currentField(); if (!f) return '';
      return `<div class="pms-help"><strong>프로세스에 등록된 입력 범위</strong><p>${e(group().label)} / ${e(label(f))}: 최소 ${e(f.min ?? (f.allowNegative ? '제한 없음' : 0))}, 최대 ${e(f.max ?? '제한 없음')}, 간격 ${e(f.step ?? '자유')}${f.unit ? ' (' + e(f.unit) + ')' : ''}</p><p>이 값은 프로세스에서 가져온 안내이며 여기서 입력 제한을 변경하지 않습니다. 분기별 실제 범위는 전체 검증에서 확인합니다. 구간 밖 값을 임의의 큰 숫자로 채우지 않습니다.</p></div>`;
    }
    function contextHtml() {
      return `<section class="pm-factor-context" data-factor-context><strong>어떤 결과에 곱하나요?</strong><p>${e(adjustedResult)}${currentLinks.length ? `에 기존 연결(${currentLinks.map(l => e(l.name)).join(' · ')})까지 적용한 결과` : ''}</p><div class="pm-factor-context-formula"><span>${e(descriptor.result)}</span>${adjustments.map(a => `<span>→ ${e(a.name)} [구간별 +/×]</span>`).join('')}${currentLinks.map(l => `<span>× ${e(l.name)}</span>`).join('')}<strong>× 이번에 연결할 값</strong></div><small>관리용 항 이름: ${e(term.name)} · ${F.isFactor(term) ? '배율 계산의 결과값입니다.' : '이 계산 결과가 최종 가격에 더해집니다.'} ${adjustments.length ? '조건별 계산을 마친 금액에 수량·배율을 곱합니다. ' : ''}새로 연결하는 배율은 별도 추가금으로 더하지 않습니다.</small></section>`;
    }
    function sourceHtml() {
      const hidden = modeInfo.filter(x => !x.available);
      return `<p class="pms-help">곱할 숫자를 가져오는 방법을 선택하세요. 아래 예시는 방식 설명이며 실제 값은 다음 단계에서 직접 연결합니다.</p>${!existing && !F.isFactor(term) && available.numeric.length ? '<div class="pm-factor-context pm-factor-conditional-entry"><strong>어떤 구간은 더하고, 다른 구간은 곱하려면</strong><p>예: 깊이 조건에 따라 +30,000원 또는 ×2배. 현재 금액 항 안에 조건별 계산을 연결하며 별도 금액 항을 만들지 않습니다.</p><button type="button" data-factor-adjustment>구간에 따라 +와 × 바꾸기 · 조건별 계산 연결</button></div>' : ''}${term.type === 'MATRIX_2D' && remaining.length ? `<div class="pm-factor-recommendation"><strong>${e(remaining.map(x => label(x.field)).join(' · '))}에 따라 가격을 보정하려면</strong><p>현재 표는 ${e((term.fieldKeys || []).map(id => (sourceGroups.find(g => g.groupKey === term.groupKey)?.bundles || []).flatMap(b => b.fields || []).find(f => f.key === id)).filter(Boolean).map(label).join(' · '))}를 사용합니다. 남은 ${e(remaining.map(x => label(x.field)).join(' · '))}의 구간마다 1배·1.2배처럼 보정하려면 ‘숫자 구간마다 배수 지정’을 선택하세요. 별도 금액을 더하려면 이 창을 닫고 1차원 금액 항을 추가합니다.</p></div>` : ''}<div class="pm-factor-modes">${modeInfo.filter(x => x.available).map(x => `<label class="pm-price-type-choice ${mode === x.value ? 'selected' : ''}"><input type="radio" name="factor-mode" value="${x.value}" ${mode === x.value ? 'checked' : ''}><strong>${e(x.title)}</strong><small>${e(x.detail)}</small><small class="pm-factor-mode-example">${e(x.example)}</small><small class="pm-factor-mode-sources">연결 가능: ${e(x.sources)}</small></label>`).join('') || '<p class="pms-alert">현재 연결할 수 있는 입력이나 배율이 없습니다. 프로세스에 답변을 등록하고 해당 그룹의 가격영향 설정을 확인해 주세요.</p>'}</div>${hidden.length ? `<details class="pm-factor-unavailable"><summary>지금 사용할 수 없는 방식 ${hidden.length}개와 이유</summary>${hidden.map(x => `<p><strong>${e(x.title)}</strong> · ${e(x.reason)}</p>`).join('')}</details>` : ''}${existing && !available.existing.length ? '<p class="pms-alert">이전 연결의 배율이 삭제되었거나 현재 질문과 맞지 않습니다. 위에서 새 연결 방식을 선택하거나 이 창을 닫고 연결을 해제해 주세요.</p>' : ''}`;
    }
    function existingApplication() {
      if (!selectedKeys.length) return { allowed: [], message: '먼저 사용할 기존 배율을 선택하세요.' };
      const selected = selectedKeys.map(id => available.existing.find(t => t.key === id));
      if (selected.some(t => !t)) return { allowed: [], message: '현재 사용할 수 없는 이전 배율을 선택 해제하거나 다른 배율로 바꿔 주세요.' };
      const checkbox = selected.filter(t => sourceGroups.find(g => g.groupKey === t.groupKey)?.control === 'CHECKBOX');
      if (checkbox.length && (checkbox.length !== selected.length || new Set(checkbox.map(t => t.groupKey)).size !== 1))
        return { allowed: [], message: '체크박스 배율은 같은 질문의 옵션끼리 연결하세요. 다른 질문의 배율은 별도의 곱셈 연결로 추가할 수 있습니다.' };
      return checkbox.length ? { allowed: ['SUM', 'PRODUCT'], message: '같은 체크박스 질문의 배율입니다. 선택한 옵션의 값을 더할지 곱할지 정하세요.' }
        : { allowed: ['EXACT_ONE'], message: '하나의 값이 적용되는 배율입니다. 여러 후보를 연결하면 각 입력 경로에서 정확히 한 후보가 적용되어야 합니다.' };
    }
    function valueHtml() {
      let html = `${input('연결 이름', 'data-factor-name maxlength="120"', name, '예: 문 수량, 깊이 보정')}<div class="pms-form-grid two">${mode !== 'EXISTING' ? sourceOptions() : ''}</div>`;
      if (mode === 'EXISTING') {
        const candidates = available.existing;
        const invalid = selectedKeys.filter(id => !candidates.some(t => t.key === id)), application = existingApplication();
        html += `<p class="pms-help">현재 질문·입력에 연결되어 있고 순환을 만들지 않는 배율만 표시합니다. 공유한 배율 값을 나중에 수정하면 그 배율을 사용하는 모든 금액에 반영됩니다.</p>${invalid.length ? `<p class="pms-alert">이전 연결 중 현재 사용할 수 없는 배율 ${invalid.length}개가 있습니다. 아래에서 제외하거나 현재 배율로 교체하세요.</p>${invalid.map(id => `<label class="pms-check"><input type="checkbox" data-factor-existing value="${e(id)}" checked><span>${e(initial.find(t => t.key === id)?.name || '삭제된 배율')} · 현재 연결 불가</span></label>`).join('')}` : ''}<div class="pm-factor-existing">${candidates.map(t => `<label class="pms-check"><input type="checkbox" data-factor-existing value="${e(t.key)}" ${selectedKeys.includes(t.key) ? 'checked' : ''}><span><strong>${e(t.name)}</strong><small>${e(F.formula(t, initial))}${t.type === 'FIXED' ? ' · ' + e(F.valueText(t.amount)) : ''}</small></span></label>`).join('') || '<p class="pms-help">기존 배율 항이 없습니다. 이전 단계에서 숫자·옵션·구간으로 새 배율을 만드세요.</p>'}</div><label><span>선택한 배율 항 적용 방식</span><select data-factor-aggregation><option value="">적용 방식을 선택하세요</option>${application.allowed.map(k => opt(k,aggregationLabels[k],aggregation)).join('')}</select></label><p class="pms-help">${e(application.message)} 선택한 모든 옵션에 배율이 있어야 합니다.</p>`;
      } else if (mode === 'NUMBER') {
        html += rangeSource() + (currentField() ? `<div class="pm-price-review"><strong>${e(adjustedResult)} × ${e(label(currentField()))} 입력값</strong><p>${e(label(currentField()))}에 입력한 숫자를 그대로 곱합니다. 입력값 자체가 곱할 배수입니다. 구간에 따라 1.2배처럼 바꾸려면 이전 단계의 ‘숫자 구간마다 배수 지정’을 사용하세요.</p><p>숫자 원값에 1을 곱해 그대로 사용합니다. 입력이 없거나 현재 분기에서 이 항목이 숨겨지면 임의로 1배로 계산하지 않습니다.</p></div>` : '');
      } else if (mode === 'CHOICE' && group()) {
        html += `<p class="pms-help">모든 등록 옵션의 값을 입력하세요. 변화가 없는 옵션은 <strong>1</strong>, 계산 결과를 0으로 만들 옵션은 <strong>0</strong>을 직접 입력합니다. 빈칸은 0이 아닙니다.</p><div class="pm-factor-values">${(group().choices || []).map(c => input(label(c) + ' 선택 시 곱할 값', `data-factor-value="${e(c.key)}" type="number" min="0" step="0.000001"`, values[c.key] ?? '', '직접 입력')).join('')}</div>${group().control === 'CHECKBOX' ? `<label><span>여러 옵션을 동시에 선택했을 때</span><select data-factor-aggregation><option value="">계산 방식을 직접 선택하세요</option>${opt('SUM', '옵션 값의 합 × 원래 항 (수량의 합)', aggregation)}${opt('PRODUCT', '옵션 값의 곱 × 원래 항 (배율의 곱)', aggregation)}</select></label><p class="pms-help">예시: 값 2와 3을 선택하면 합은 5배, 곱은 6배입니다. 실제 옵션 값은 위에서 입력한 값으로 계산합니다.</p>` : '<p class="pms-help">하나 선택 질문은 선택한 옵션의 배율 하나만 적용합니다.</p>'}`;
      } else if (mode === 'RANGE' && currentField()) {
        const labels = F.rangeLabels(bounds, operators);
        html += rangeSource() + `<p class="pms-help">구간 끝값을 작은 순서로 입력하세요. 다음 구간의 시작은 이전 끝값에서 자동으로 이어져 중간 누락·중복을 막습니다. 각 구간의 배수는 직접 입력합니다.</p><details class="pm-factor-sequence"><summary>같은 간격으로 구간 끝값 만들기</summary><p>예: 깊이 기준 250, 증가 간격 100. 이 값은 경계만 생성하며 배율은 정하지 않습니다.</p><div class="pms-form-grid two">${input('첫 구간 끝값', 'data-factor-seq-start type="number" min="0" step="any"', seqStart, '예: 250')}${input('증가 간격', 'data-factor-seq-step type="number" min="0" step="any"', seqStep, '예: 100')}${input('끝값 개수', 'data-factor-seq-count type="number" min="1" max="98" step="1"', seqCount, '직접 입력')}</div><button type="button" data-factor-generate>경계 초안 만들기</button></details><div class="pm-factor-range-rows">${bounds.map((bound, i) => `<article><div class="pms-form-grid two">${input('구간 ' + (i + 1) + ' 끝값', `data-factor-bound="${i}" type="number" min="0" step="any"`, bound)}<label><span>끝값 자체의 처리</span><select data-factor-bound-op="${i}">${opt('LE', '끝값까지 현재 구간 (이하)', operators[i])}${opt('LT', '끝값부터 다음 구간 (미만)', operators[i])}</select></label>${input('이 구간에서 곱할 값', `data-factor-range-value="${i}" type="number" min="0" step="0.000001"`, values[i] ?? '', '배율 직접 입력')}</div><small data-factor-range-caption="${i}">${e(labels[i])}</small><button type="button" data-factor-range-remove="${i}" ${bounds.length === 1 ? 'disabled' : ''}>구간 삭제</button></article>`).join('')}<article><strong data-factor-tail>${e(labels.at(-1))}</strong>${input('마지막 구간에서 곱할 값', 'data-factor-tail-value type="number" min="0" step="0.000001"', values[bounds.length] ?? '', '마지막 구간도 직접 입력')}</article></div><button type="button" data-factor-range-add>＋ 구간 추가</button>`;
      }
      return html;
    }
    function reviewHtml() {
      const owner = plan.terms.find(t => t.key === term.key), added = plan.created;
      return `<div class="pm-price-review"><strong>${e(adjustedResult)} × ${e(plan.link.name)}</strong><small>관리용 계산식: ${e(F.formula(owner, plan.terms, true))}</small><p>${e(aggregationLabels[plan.link.aggregation])} · ${plan.link.termKeys.length}개 배율 후보</p></div><p class="pms-help">금액 항의 결과에 배율을 곱합니다. ${adjustments.length ? '등록된 조건별 계산을 모두 적용한 뒤에 곱합니다. ' : ''}배율 항은 합계에 따로 더하지 않습니다. 금액 항이 적용되는 입력 경로에서 배율이 없거나 하나 선택 조건이 중복되면 계산을 완료할 수 없습니다.</p><div class="pm-factor-review">${plan.link.termKeys.map(id => { const t = plan.terms.find(t => t.key === id); return `<article><strong>${e(t.name)}</strong><span>${t.type === 'FIXED' ? e(F.valueText(t.amount)) : t.type === 'MULTIPLY' ? '등록 숫자 입력 × 1' : e(t.type)}</span></article>`; }).join('')}</div><p class="pms-help">새 배율 ${added.length}개 · 반영 후 전체 항 ${plan.terms.length}개. 현재 화면에 반영한 뒤 실제 프로세스 테스트와 전체 검증을 통과해야 최종 저장됩니다.</p>${onTest ? '<button type="button" data-factor-test>실제 입력으로 연결식 미리 계산</button>' : ''}`;
    }
    function draw() {
      body.innerHTML = `<ol class="pm-stepper"><li class="${stage === 0 ? 'active' : ''}">1. 값의 출처</li><li class="${stage === 1 ? 'active' : ''}">2. 값·구간 연결</li><li class="${stage === 2 ? 'active' : ''}">3. 계산식 검토</li></ol>${contextHtml()}<div data-factor-error class="pms-alert" role="alert" hidden></div>${stage === 0 ? sourceHtml() : stage === 1 ? valueHtml() : reviewHtml()}`;
      S.$('[data-factor-prev]', d).hidden = stage === 0;
      S.$('[data-factor-next]', d).hidden = stage === 2;
      S.$('[data-factor-next]', d).disabled = stage === 0 && !mode;
      S.$('[data-factor-next]', d).textContent = stage === 0 ? '다음 · 값 연결' : '다음 · 계산식 검토';
      S.$('[data-factor-apply]', d).hidden = stage !== 2;
      S.$('[data-factor-status]', d).textContent = '반영 전까지 원래 계산식은 유지됩니다.';
      S.$$('[name="factor-mode"]', body).forEach(el => el.onchange = () => { if (!modeInfo.find(x => x.value === el.value)?.available) return; mode = el.value; groupKey = ''; fieldKey = ''; values = {}; aggregation = ''; draw(); });
      S.$('[data-factor-adjustment]', body)?.addEventListener('click', () => run(async () => {
        if (snapshot() !== start && !await S.confirm('이 창에서 아직 반영하지 않은 배율 설정을 버리고 현재 금액 항의 조건별 계산을 여시겠습니까?')) return;
        const next = F.openAdjustment({ term, terms: initial, groups, process, diagnose, apply, onTest });
        if (next) d.close();
      }));
      S.$('[data-factor-name]', body)?.addEventListener('input', ev => { name = ev.target.value; });
      S.$('[data-factor-group]', body)?.addEventListener('change', ev => { groupKey = ev.target.value; fieldKey = ''; values = {}; aggregation = ''; if (!name.trim()) name = group()?.label || ''; draw(); });
      S.$('[data-factor-field]', body)?.addEventListener('change', ev => { fieldKey = ev.target.value; draw(); });
      S.$('[data-factor-aggregation]', body)?.addEventListener('change', ev => { aggregation = ev.target.value; });
      S.$$('[data-factor-existing]', body).forEach(el => el.onchange = () => { selectedKeys = S.$$('[data-factor-existing]:checked', body).map(x => x.value); const allowed = existingApplication().allowed; if (allowed.length === 1) aggregation = allowed[0]; else if (!allowed.includes(aggregation)) aggregation = ''; draw(); });
      S.$$('[data-factor-value]', body).forEach(el => el.oninput = () => { values[el.dataset.factorValue] = el.value; });
      S.$$('[data-factor-range-value]', body).forEach(el => el.oninput = () => { values[el.dataset.factorRangeValue] = el.value; });
      S.$('[data-factor-tail-value]', body)?.addEventListener('input', ev => { values[bounds.length] = ev.target.value; });
      function captions() { const labels = F.rangeLabels(bounds, operators); S.$$('[data-factor-range-caption]', body).forEach(el => { el.textContent = labels[Number(el.dataset.factorRangeCaption)]; }); const tail = S.$('[data-factor-tail]', body); if (tail) tail.textContent = labels.at(-1); }
      S.$$('[data-factor-bound]', body).forEach(el => el.oninput = () => { bounds[Number(el.dataset.factorBound)] = el.value; captions(); });
      S.$$('[data-factor-bound-op]', body).forEach(el => el.onchange = () => { operators[Number(el.dataset.factorBoundOp)] = el.value; captions(); });
      S.$('[data-factor-seq-start]', body)?.addEventListener('input', ev => { seqStart = ev.target.value; });
      S.$('[data-factor-seq-step]', body)?.addEventListener('input', ev => { seqStep = ev.target.value; });
      S.$('[data-factor-seq-count]', body)?.addEventListener('input', ev => { seqCount = ev.target.value; });
      S.$('[data-factor-generate]', body)?.addEventListener('click', () => run(async () => {
        const next = S.priceTable.sequence(seqStart, seqStep, seqCount);
        if (next.length + initial.length + 1 > 100) throw Error('경계 개수에 마지막 구간 1개를 더한 배율 항을 생성합니다. 기존 항과 합쳐 100개 이내로 정해 주세요.');
        if (Object.values(values).some(v => v !== '') && !await S.confirm('경계를 새로 만들면 기존 구간 배율은 비웁니다. 새 구간마다 배율을 다시 입력하시겠습니까?')) return;
        bounds = next; operators = next.map(() => 'LE'); values = {}; draw();
      }));
      S.$('[data-factor-range-add]', body)?.addEventListener('click', () => run(() => {
        if (bounds.length + initial.length + 2 > 100) throw Error('금액·배율 항을 합쳐 최대 100개입니다.');
        const tail = values[bounds.length]; delete values[bounds.length]; bounds.push(''); operators.push('LE'); values[bounds.length] = tail ?? ''; draw();
      }));
      S.$$('[data-factor-range-remove]', body).forEach(el => el.onclick = () => run(async () => {
        if (!await S.confirm('이 경계를 제거하면 인접 구간이 합쳐집니다. 합쳐진 구간의 배율은 비우므로 다시 입력해 주세요.')) return;
        const index = Number(el.dataset.factorRangeRemove), previous = { ...values }, count = bounds.length;
        bounds.splice(index, 1); operators.splice(index, 1); values = {};
        for (let i = 0; i < count; i++) values[i] = i < index ? previous[i] ?? '' : i === index ? '' : previous[i + 1] ?? '';
        draw();
      }));
      S.$('[data-factor-test]', body)?.addEventListener('click', () => run(() => onTest(plan.terms.find(t => t.key === term.key), plan.terms)));
    }
    S.$('[data-factor-prev]', d).onclick = () => { stage = Math.max(0, stage - 1); draw(); };
    S.$('[data-factor-next]', d).onclick = () => run(() => {
      if (!mode || !modeInfo.find(x => x.value === mode)?.available) throw Error('현재 사용할 수 있는 값의 출처를 먼저 선택해 주세요.');
      if (stage === 1 && mode === 'EXISTING' && !existingApplication().allowed.includes(aggregation)) throw Error(existingApplication().message);
      if (stage === 1) plan = F.makePlan({ term, terms: initial, groups: sourceGroups, mode, groupKey, fieldKey, name, values, bounds, operators, selectedKeys, aggregation, replaceKey: linkKey });
      stage++; draw();
    });
    S.$('[data-factor-apply]', d).onclick = ev => S.run(ev.currentTarget, async () => {
      try {
        if (!plan || stage !== 2) return;
        const serial = ++sequence, signature = snapshot();
        if (diagnose) {
          const result = await diagnose(plan.terms);
          if (!d.open || serial !== sequence || signature !== snapshot()) return;
          if (!result.valid) throw Error((result.issues || []).filter(i => i.severity === 'ERROR').map(i => `${i.location}: ${i.message}`).join('\n') || '배율 연결을 검증하지 못했습니다.');
        }
        apply(plan.terms); d.close();
      } catch (ex) { error(ex.message); }
    });
    draw(); return d;
  };
})(window.PMS);
