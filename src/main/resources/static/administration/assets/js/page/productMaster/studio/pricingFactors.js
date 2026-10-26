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
      return t;
    });
    return { term: copied.find(t => t.key === ids.get(term.key)), terms: copied };
  };
  F.validateLinks = terms => {
    const map = new Map(terms.map(t => [t.key, t]));
    if (map.size !== terms.length) throw Error('가격 항의 내부 식별자가 중복됩니다.');
    for (const term of terms) {
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
    return children.length ? '(' + [term.name, ...children].join(' × ') + ')' : term.name;
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
  F.open = ({ term, terms, groups, process, diagnose, apply, onTest, linkKey = null }) => {
    const initial = S.copy(terms), existing = F.links(term).find(l => l.key === linkKey), sourceGroups = groups.filter(g => ['NUMBER', 'RADIO', 'CHECKBOX'].includes(g.control));
    let stage = 0, plan = null, mode = existing ? 'EXISTING' : '', groupKey = '', fieldKey = '', name = existing?.name || '', values = {},
      bounds = [''], operators = ['LE'], selectedKeys = [...(existing?.termKeys || [])], aggregation = existing?.aggregation || '', sequence = 0;
    let seqStart = '', seqStep = '', seqCount = '';
    const d = S.dialog('× 배율·수량 연결 · ' + term.name, '', { foot: '<span data-factor-status></span><button type="button" data-factor-prev>이전</button><button type="button" class="primary" data-factor-next>다음 · 값 연결</button><button type="button" class="primary" data-factor-apply hidden>검사 후 계산식에 반영</button>' });
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
      const available = sourceGroups.filter(g => mode === 'CHOICE' ? ['RADIO', 'CHECKBOX'].includes(g.control) : g.control === 'NUMBER');
      return `<label><span>값을 가져올 등록 질문</span><select data-factor-group><option value="">질문을 선택하세요</option>${available.map(g => opt(g.groupKey, g.label, groupKey)).join('')}</select></label>${group()?.control === 'NUMBER' ? `<label><span>숫자 입력 항목</span><select data-factor-field><option value="">입력 항목을 선택하세요</option>${fields().map(f => opt(f.key, label(f) + (f.unit ? ' (' + f.unit + ')' : ''), fieldKey)).join('')}</select></label>` : ''}`;
    }
    function rangeSource() {
      const f = currentField(); if (!f) return '';
      return `<div class="pms-help"><strong>프로세스에 등록된 입력 범위</strong><p>${e(group().label)} / ${e(label(f))}: 최소 ${e(f.min ?? (f.allowNegative ? '제한 없음' : 0))}, 최대 ${e(f.max ?? '제한 없음')}, 간격 ${e(f.step ?? '자유')}${f.unit ? ' (' + e(f.unit) + ')' : ''}</p><p>이 값은 프로세스에서 가져온 안내이며 여기서 입력 제한을 변경하지 않습니다. 분기별 실제 범위는 전체 검증에서 확인합니다. 구간 밖 값을 임의의 큰 숫자로 채우지 않습니다.</p></div>`;
    }
    function sourceHtml() {
      return `<p class="pms-help"><strong>${e(term.name)}</strong>의 계산 결과에 곱할 값의 출처를 선택하세요. 배율 항은 최종가격에 별도로 더하지 않습니다. 옵션 이름 ‘2개’를 숫자로 추측하지 않고 직접 연결한 값을 사용합니다.</p><div class="pm-factor-modes">${[
        ['NUMBER', '입력한 수량 그대로', '고객이 입력한 숫자 2 → 원래 항 × 2'],
        ['CHOICE', '옵션마다 배수 지정', '옵션별 1, 2, 1.2 등을 직접 연결'],
        ['RANGE', '숫자 구간마다 배수 지정', '깊이·높이 등의 구간별 배수를 직접 입력'],
        ['EXISTING', '기존 배율 항 연결', '등록한 배율 항을 재사용하거나 연쇄 연결']
      ].map(([value, title, hint]) => `<label class="pm-price-type-choice ${mode === value ? 'selected' : ''}"><input type="radio" name="factor-mode" value="${value}" ${mode === value ? 'checked' : ''}><strong>${e(title)}</strong><small>${e(hint)}</small></label>`).join('')}</div>`;
    }
    function valueHtml() {
      let html = `${input('연결 이름', 'data-factor-name maxlength="120"', name, '예: 문 수량, 깊이 보정')}<div class="pms-form-grid two">${mode !== 'EXISTING' ? sourceOptions() : ''}</div>`;
      if (mode === 'EXISTING') {
        const candidates = initial.filter(t => F.isFactor(t) && t.key !== term.key);
        html += `<p class="pms-help">합계에 별도로 더하지 않는 배율 항만 표시합니다. 자신에게 되돌아오는 순환 연결은 반영할 수 없습니다.</p><div class="pm-factor-existing">${candidates.map(t => `<label class="pms-check"><input type="checkbox" data-factor-existing value="${e(t.key)}" ${selectedKeys.includes(t.key) ? 'checked' : ''}><span><strong>${e(t.name)}</strong><small>${e(F.formula(t, initial))}${t.type === 'FIXED' ? ' · ' + e(F.valueText(t.amount)) : ''}</small></span></label>`).join('') || '<p class="pms-help">기존 배율 항이 없습니다. 이전 단계에서 숫자·옵션·구간으로 새 배율을 만드세요.</p>'}</div><label><span>선택한 배율 항 적용 방식</span><select data-factor-aggregation><option value="">적용 방식을 선택하세요</option>${Object.entries(aggregationLabels).map(([k,v]) => opt(k,v,aggregation)).join('')}</select></label><p class="pms-help">‘하나’는 조건에 맞는 배율이 정확히 1개여야 합니다. 합계·곱은 같은 체크박스 질문의 옵션별 배율에만 사용하며, 선택한 모든 옵션에 배율이 있어야 합니다.</p>`;
      } else if (mode === 'NUMBER') {
        html += rangeSource() + (currentField() ? `<div class="pm-price-review"><strong>${e(term.name)} × ${e(label(currentField()))} 입력값</strong><p>숫자 원값에 1을 곱해 그대로 사용합니다. 입력이 없거나 현재 분기에서 이 항목이 숨겨지면 임의로 1배로 계산하지 않습니다.</p></div>` : '');
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
      return `<div class="pm-price-review"><strong>${e(F.formula(owner, plan.terms, true))}</strong><p>${e(aggregationLabels[plan.link.aggregation])} · ${plan.link.termKeys.length}개 배율 후보</p></div><p class="pms-help">금액 항의 결과에 배율을 곱합니다. 배율 항은 합계에 따로 더하지 않습니다. 금액 항이 적용되는 입력 경로에서 배율이 없거나 하나 선택 조건이 중복되면 계산을 완료할 수 없습니다.</p><div class="pm-factor-review">${plan.link.termKeys.map(id => { const t = plan.terms.find(t => t.key === id); return `<article><strong>${e(t.name)}</strong><span>${t.type === 'FIXED' ? e(F.valueText(t.amount)) : t.type === 'MULTIPLY' ? '등록 숫자 입력 × 1' : e(t.type)}</span></article>`; }).join('')}</div><p class="pms-help">새 배율 ${added.length}개 · 반영 후 전체 항 ${plan.terms.length}개. 현재 화면에 반영한 뒤 실제 프로세스 테스트와 전체 검증을 통과해야 최종 저장됩니다.</p>${onTest ? '<button type="button" data-factor-test>실제 입력으로 연결식 미리 계산</button>' : ''}`;
    }
    function draw() {
      body.innerHTML = `<ol class="pm-stepper"><li class="${stage === 0 ? 'active' : ''}">1. 값의 출처</li><li class="${stage === 1 ? 'active' : ''}">2. 값·구간 연결</li><li class="${stage === 2 ? 'active' : ''}">3. 계산식 검토</li></ol><div data-factor-error class="pms-alert" role="alert" hidden></div>${stage === 0 ? sourceHtml() : stage === 1 ? valueHtml() : reviewHtml()}`;
      S.$('[data-factor-prev]', d).hidden = stage === 0;
      S.$('[data-factor-next]', d).hidden = stage === 2;
      S.$('[data-factor-next]', d).textContent = stage === 0 ? '다음 · 값 연결' : '다음 · 계산식 검토';
      S.$('[data-factor-apply]', d).hidden = stage !== 2;
      S.$('[data-factor-status]', d).textContent = '반영 전까지 원래 계산식은 유지됩니다.';
      S.$$('[name="factor-mode"]', body).forEach(el => el.onchange = () => { mode = el.value; groupKey = ''; fieldKey = ''; values = {}; aggregation = ''; });
      S.$('[data-factor-name]', body)?.addEventListener('input', ev => { name = ev.target.value; });
      S.$('[data-factor-group]', body)?.addEventListener('change', ev => { groupKey = ev.target.value; fieldKey = ''; values = {}; aggregation = ''; if (!name.trim()) name = group()?.label || ''; draw(); });
      S.$('[data-factor-field]', body)?.addEventListener('change', ev => { fieldKey = ev.target.value; draw(); });
      S.$('[data-factor-aggregation]', body)?.addEventListener('change', ev => { aggregation = ev.target.value; });
      S.$$('[data-factor-existing]', body).forEach(el => el.onchange = () => { selectedKeys = S.$$('[data-factor-existing]:checked', body).map(x => x.value); });
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
      if (!mode) throw Error('값의 출처를 먼저 선택해 주세요.');
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
