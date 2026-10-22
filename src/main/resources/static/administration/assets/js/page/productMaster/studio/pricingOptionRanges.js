(function (S) {
  "use strict";
  const R = S.optionRanges = {}, e = S.e, M = S.priceTable;
  const label = v => v?.labels?.management || v?.labels?.customer || v?.label || v?.key || '';
  const opt = (value, text, selected) => `<option value="${e(value)}" ${String(value) === String(selected) ? 'selected' : ''}>${e(text)}</option>`;
  R.sources = (questions, owner) => { const index = questions.findIndex(q => q.key === owner); return index < 0 ? [] : questions.slice(0, index).filter(q => q.control === 'NUMBER' && q.fields?.length); };
  R.validate = (term, questions) => {
    const p = term.optionPricing; if (!p) return;
    if (term.type !== 'FIXED' || !term.choiceKeys?.length) throw Error('범위별 가격을 적용할 선택 옵션을 하나 이상 지정해 주세요.');
    const q = questions && R.sources(questions, term.groupKey).find(q => q.key === p.groupKey);
    if (questions && !q?.fields.some(f => f.key === p.axis.fieldKey)) throw Error('이 옵션보다 앞에 있는 숫자 그룹과 입력 항목을 선택해 주세요.');
    M.validateAxis(p.axis);
    if (p.axis.outsidePolicy === 'EDGE') throw Error('옵션 범위 가격은 마지막 구간 금액을 직접 입력해 주세요.');
    if (M.lower(p.axis)) throw Error('옵션 가격 범위는 구간 끝값(이하/미만)으로 설정해 주세요.');
    if (p.amounts.length !== p.axis.headers.length) throw Error('옵션 가격의 범위 수와 금액 수가 다릅니다.');
    p.amounts.forEach(M.amount); M.amount(p.aboveAmount);
  };
  R.price = (p, input) => { const at = M.index(p.axis, input); return at < 0 ? p.aboveAmount : p.amounts[at]; };
  R.tail = p => {
    const i = p.axis.headers.length - 1, value = p.axis.headers[i];
    return value == null || value === '' ? '마지막 기준 입력 후 표시' : `${value} ${M.operator(p.axis, i) === 'LE' ? '초과' : '이상'} · 상한 없음`;
  };
  R.html = (term, questions) => {
    const p = term.optionPricing, sources = R.sources(questions, term.groupKey), q = sources.find(q => q.key === p?.groupKey);
    return `<section class="pm-option-ranges"><h3>숫자 범위에 따라 이 옵션의 가격 변경</h3><label class="pms-check"><input type="checkbox" data-option-range-enabled ${p ? 'checked' : ''} ${!sources.length && !p ? 'disabled' : ''}>앞에서 입력한 숫자로 옵션 가격을 다르게 적용</label><p class="pms-help">예: LED 추가를 선택했을 때 사이즈 W가 600 이하이면 20,000원, 600 초과~1,000 이하이면 30,000원, 1,000 초과이면 40,000원입니다. <strong>기본금액에 더하지 않고 해당 구간의 금액 한 개만 적용합니다.</strong>${sources.length ? '' : ' 현재 이 옵션 앞에는 연결할 숫자 입력 그룹이 없습니다.'}</p>${!p ? '' : `<div class="pms-form-grid two"><label><span>1. 앞에서 입력한 숫자 그룹</span><select data-range-group><option value="">그룹 선택</option>${sources.map(q => opt(q.key, label(q), p.groupKey)).join('')}</select></label><label><span>2. 범위를 나눌 입력 항목</span><select data-range-field><option value="">입력 항목 선택</option>${(q?.fields || []).map(f => opt(f.key, label(f) + (f.unit ? ' (' + f.unit + ')' : ''), p.axis.fieldKey)).join('')}</select></label></div><div data-option-range-config ${q?.fields.some(f => f.key === p.axis.fieldKey) ? "" : "hidden"}><p class="pms-help">가격계산 영향이 꺼진 앞쪽 숫자 그룹도 조건으로 연결할 수 있습니다. 연결한 숫자가 해당 분기에서 숨겨지거나 입력되지 않으면 기본금액으로 대신 계산하지 않고 오류를 안내합니다. 그런 분기에는 다음 단계에서 적용 조건을 추가해 주세요.</p><strong>3. 구간 끝값과 해당 구간의 옵션 금액</strong><p class="pms-help">끝값은 작은 순서로 입력합니다. 다음 구간은 앞 구간 직후부터 이어져 겹치거나 빠지는 구간이 없습니다. 정수 입력이면 ‘600 초과’는 601부터, 소수 입력을 허용하면 600.1도 다음 구간입니다.</p><div class="pms-actions"><button type="button" data-range-example>LED 예시 채우기 · 600 / 1000</button><button type="button" data-range-add>＋ 구간 끝값 추가</button></div><div class="pm-option-range-rows">${p.axis.headers.map((v, i) => `<article class="pm-option-range-row"><label><span>구간 ${i + 1} 끝값</span><input type="number" min="0" step="any" data-range-bound="${i}" value="${e(v ?? '')}"></label><label><span>끝값 자체의 처리</span><select data-range-op="${i}">${opt('LE', '이 값까지 현재 구간', M.operator(p.axis, i))}${opt('LT', '이 값부터 다음 구간', M.operator(p.axis, i))}</select></label><label><span>이 구간의 옵션 금액 (원)</span><input type="number" min="0" step="0.01" data-range-amount="${i}" value="${e(p.amounts[i] ?? '')}"></label><button type="button" data-range-remove="${i}" ${p.axis.headers.length === 1 ? 'disabled' : ''}>구간 삭제</button><small data-option-range-label="${i}">${e(M.range(p.axis, i))}</small></article>`).join('')}<article class="pm-option-range-row is-tail"><strong data-option-range-tail>${e(R.tail(p))}</strong><label><span>마지막 구간의 옵션 금액 (원)</span><input type="number" min="0" step="0.01" data-range-above value="${e(p.aboveAmount ?? '')}"></label></article></div><details class="pm-price-range-probe"><summary>입력값으로 옵션 금액 테스트</summary><label><span>4. 입력값으로 이 옵션 금액 확인</span><input type="number" min="0" step="any" data-range-probe placeholder="예: 600, 601, 1000, 1001"></label><p data-range-probe-result role="status">입력값을 넣어 적용 금액을 확인하세요. 최종 테스트에서는 옵션 선택·추가 조건·분기도 함께 확인합니다.</p></details></div>`}</section>`;
  };
  R.bind = ({body, term, questions, draw, error, ui}) => {
    const report = fn => Promise.resolve().then(fn).catch(ex => error(ex.message));
    S.$('[data-option-range-enabled]', body)?.addEventListener('change', event => report(async () => {
      if (!event.target.checked) {
        if (!await S.confirm('범위별 가격을 해제하고 위의 고정금액으로 계산하시겠습니까? 작성한 범위표는 실행 취소로 복구할 수 있습니다.')) { event.target.checked = true; return; }
        term.optionPricing = null;
      } else {
        term.optionPricing = { groupKey: '', axis: { fieldKey: '', operator: 'LE', headers: [null], operators: [null] }, amounts: [term.amount], aboveAmount: null };
      }
      draw();
    }));
    const p = term.optionPricing; if (!p) return;
    function refresh() {
      S.$$('[data-option-range-label]', body).forEach(el => { el.textContent = M.range(p.axis, Number(el.dataset.optionRangeLabel)); });
      S.$('[data-option-range-tail]', body).textContent = R.tail(p);
      const value = S.$('[data-range-probe]', body).value, result = S.$('[data-range-probe-result]', body);
      ui.rangeProbe = value;
      if (value === '') { result.textContent = '입력값을 넣어 적용 금액을 확인하세요.'; return; }
      try { R.validate(term, questions); result.textContent = `${value} → ${Number(R.price(p, value)).toLocaleString('ko-KR', {maximumFractionDigits: 2})}원 · 기본금액은 중복 가산하지 않습니다.`; }
      catch (ex) { result.textContent = ex.message; }
    }
    S.$('[data-range-group]', body).onchange = event => { p.groupKey = event.target.value; p.axis.fieldKey = ''; draw(); };
    S.$('[data-range-field]', body).onchange = event => { p.axis.fieldKey = event.target.value; draw(); };
    for (const [selector, assign] of [
      ['[data-range-bound]', (el) => { p.axis.headers[Number(el.dataset.rangeBound)] = el.value === '' ? null : el.value; }],
      ['[data-range-amount]', (el) => { p.amounts[Number(el.dataset.rangeAmount)] = el.value === '' ? null : el.value; }],
      ['[data-range-above]', (el) => { p.aboveAmount = el.value === '' ? null : el.value; }]
    ]) S.$$(selector, body).forEach(el => el.addEventListener('input', () => { assign(el); refresh(); }));
    S.$$('[data-range-op]', body).forEach(el => el.onchange = () => { p.axis.operators = p.axis.headers.map((_, i) => p.axis.operators?.[i] || null); p.axis.operators[Number(el.dataset.rangeOp)] = el.value; refresh(); });
    S.$('[data-range-add]', body).onclick = () => {
      if (p.axis.headers.length >= 200) return error('구간 끝값은 최대 200개입니다.');
      p.axis.operators = p.axis.headers.map((_, i) => p.axis.operators?.[i] || null); p.axis.operators.push(null);
      p.axis.headers.push(null); p.amounts.push(null); draw();
    };
    S.$$('[data-range-remove]', body).forEach(button => button.onclick = () => report(async () => {
      if (!await S.confirm('이 구간 끝값과 금액을 삭제하시겠습니까? 남은 구간이 이어지므로 적용 범위가 바뀝니다.')) return;
      const i = Number(button.dataset.rangeRemove); p.axis.headers.splice(i, 1); p.axis.operators?.splice(i, 1); p.amounts.splice(i, 1); draw();
    }));
    S.$('[data-range-example]', body).onclick = () => report(async () => {
      if (!await S.confirm('범위표를 600 이하 20,000원 / 600 초과~1000 이하 30,000원 / 1000 초과 40,000원 예시로 채우시겠습니까? 숫자 그룹·항목 연결은 유지됩니다.')) return;
      p.axis.headers = ['600', '1000']; p.axis.operator = 'LE'; p.axis.operators = [null, null]; p.amounts = ['20000', '30000']; p.aboveAmount = '40000'; draw();
    });
    S.$('[data-range-probe]', body).value = ui.rangeProbe ?? '';
    S.$('[data-range-probe]', body).oninput = refresh;
    refresh();
  };
})(window.PMS);
