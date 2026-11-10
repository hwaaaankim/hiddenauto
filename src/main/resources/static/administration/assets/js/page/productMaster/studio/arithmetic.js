(function (S) {
  'use strict';
  const A = S.arithmetic = {};
  const name = x => x?.labels?.customer || x?.key || '항목 없음';
  const symbols = { ADD: '+', SUB: '−', MUL: '×', DIV: '÷', LPAREN: '(', RPAREN: ')' };
  const enabledQuestions = new WeakSet();
  A.enabled = q => enabledQuestions.has(q) || q.choices?.some(c => c.numericValue);
  A.zero = () => [{ kind: 'NUMBER', value: 0 }];
  A.numeric = q => q?.control === 'RADIO' && q.choices?.length && q.choices.every(c => c.numericValue);
  A.text = (tokens, process) => (tokens || []).map(t => {
    if (t.kind === 'NUMBER') return String(t.value ?? 0);
    if (t.kind !== 'FIELD') return symbols[t.kind] || '?';
    const q = process?.questions?.find(q => q.key === t.groupKey), f = q?.fields?.find(f => f.key === t.fieldKey);
    return `${name(q)} · ${name(f)}`;
  }).join(' ');
  // Preview only. The server recomputes with BigDecimal before accepting any answer or price.
  A.evaluate = (tokens, answers = {}, syntaxOnly = false) => {
    if (!tokens?.length || tokens.length > 100) throw Error('숫자 또는 앞 입력값을 넣어 주세요.');
    let i = 0;
    const take = k => tokens[i]?.kind === k ? (++i, true) : false;
    const atom = () => {
      if (take('SUB')) return -atom(); if (take('ADD')) return atom();
      if (take('LPAREN')) { const n = sum(); if (!take('RPAREN')) throw Error('닫는 괄호가 필요합니다.'); return n; }
      const t = tokens[i++]; if (!t) throw Error('연산자 뒤에 값을 넣어 주세요.');
      const raw = t.kind === 'NUMBER' ? t.value : t.kind === 'FIELD' ? answers[t.groupKey]?.fields?.[t.fieldKey] : undefined;
      if (raw === undefined || raw === null || raw === '') throw Error(t.kind === 'FIELD' ? '참조하는 앞 숫자를 먼저 입력해 주세요.' : '숫자·연산자 순서를 확인해 주세요.');
      const n = Number(raw); if (!Number.isFinite(n)) throw Error('유효한 숫자가 필요합니다.'); return n;
    };
    const product = () => { let n = atom(); for (;;) { if (take('MUL')) n *= atom(); else if (take('DIV')) { const d = atom(); if (!syntaxOnly && !d) throw Error('0으로 나눌 수 없습니다.'); n = syntaxOnly ? 1 : n / d; } else return n; } };
    const sum = () => { let n = product(); for (;;) { if (take('ADD')) n += product(); else if (take('SUB')) n -= product(); else return n; } };
    const value = sum(); if (i !== tokens.length || !Number.isFinite(value)) throw Error('계산식의 연산자·괄호 순서를 확인해 주세요.'); return value;
  };
  A.syntax = tokens => {
    const dummy = {}; (tokens || []).filter(t => t.kind === 'FIELD').forEach(t => { (dummy[t.groupKey] ??= {fields:{}}).fields[t.fieldKey] = 1; });
    // Validate grammar independently of a trial denominator that may happen to become zero.
    A.evaluate(tokens, dummy, true);
  };
  A.editor = (root, initial, process, targetKey, onChange, readOnly = false) => {
    let tokens = S.copy(initial || []), drag = null;
    const questions = process.questions.slice(0, process.questions.findIndex(q => q.key === targetKey)).filter(q => q.control === 'NUMBER');
    let selected = questions[0]?.key || '';
    const publish = () => { onChange(S.copy(tokens)); draw(); };
    function insert(t, index = tokens.length) { if (tokens.length >= 100) return S.toast('계산식은 최대 100개 요소입니다.'); tokens.splice(index, 0, S.copy(t)); publish(); }
    function draw() {
      const q = questions.find(q => q.key === selected);
      root.innerHTML = `<div class="pm-expression-editor"><p class="pms-help">앞 질문을 고른 뒤 숫자 항목을 클릭하거나 아래 식으로 끌어 놓으세요. 연산자·직접 숫자·괄호를 조합합니다. 곱하기·나누기를 먼저 계산합니다.</p><label><span>1. 참조할 앞 숫자 질문</span><select data-expr-question>${questions.map(x => `<option value="${S.e(x.key)}" ${x.key===selected?'selected':''}>${S.e(name(x))}</option>`).join('') || '<option>앞 숫자 질문 없음 · 직접 숫자 사용 가능</option>'}</select></label><div class="pms-actions pm-expression-palette">${(q?.fields || []).map(f => `<button type="button" draggable="${!readOnly}" data-expr-field="${S.e(f.key)}">${S.e(name(f))}<small> ${S.e(f.min??(f.allowNegative?'최소 미설정':0))} ~ ${S.e(f.max??'최대 미설정')}</small></button>`).join('')}</div><p class="pms-help">계산용 선택값의 가격 범위는 앞 필드의 실제 최소·최대로 검증합니다. 최대 미설정 또는 범위를 확정할 수 없는 식은 전체 검증에서 확인 안내를 표시합니다. 실제 입력 범위를 지정하면 더 정확히 검증할 수 있습니다.</p><div class="pms-actions">${Object.entries(symbols).map(([k,v]) => `<button type="button" data-expr-op="${k}" aria-label="${S.e(v)} 추가">${v}</button>`).join('')}<label><span>직접 숫자</span><input data-expr-number type="number" step="any" value="0"></label><button type="button" data-expr-add-number>숫자 추가</button></div><strong>2. 계산식 · 항목 사이로 끌어 놓아 순서 변경</strong><div class="pm-expression-tokens" data-expr-drop>${tokens.map((t,i) => `<span class="pm-expression-token" draggable="${!readOnly}" data-expr-index="${i}"><b>${S.e(A.text([t],process))}</b><button type="button" data-expr-left="${i}" aria-label="앞으로 이동" ${!i?'disabled':''}>←</button><button type="button" data-expr-remove="${i}" aria-label="항목 삭제">×</button></span>`).join('') || '<span class="pms-help">여기에 숫자 항목을 놓아 주세요.</span>'}</div><p data-expr-status class="pms-help" aria-live="polite"></p></div>`;
      const status = root.querySelector('[data-expr-status]');
      try { A.syntax(tokens); status.textContent = '식 구조 확인: ' + A.text(tokens,process); } catch(e) { status.textContent = e.message; }
      root.querySelector('[data-expr-question]').onchange = e => { selected=e.target.value; draw(); };
      root.querySelectorAll('[data-expr-field]').forEach(b => {
        const token = {kind:'FIELD',groupKey:selected,fieldKey:b.dataset.exprField};
        b.onclick = () => insert(token); b.ondragstart = e => { drag={token}; e.dataTransfer.setData('text/plain','expression'); };
      });
      root.querySelectorAll('[data-expr-op]').forEach(b => b.onclick=()=>insert({kind:b.dataset.exprOp}));
      root.querySelector('[data-expr-add-number]').onclick=()=>{ const el=root.querySelector('[data-expr-number]'); if (!el.validity.valid) return S.toast('올바른 숫자를 입력해 주세요.'); insert({kind:'NUMBER',value:el.value===''?0:Number(el.value)}); };
      root.querySelectorAll('[data-expr-remove]').forEach(b=>b.onclick=()=>{tokens.splice(Number(b.dataset.exprRemove),1);publish();});
      root.querySelectorAll('[data-expr-left]').forEach(b=>b.onclick=()=>{const i=Number(b.dataset.exprLeft);tokens.splice(i-1,0,tokens.splice(i,1)[0]);publish();});
      root.querySelectorAll('[data-expr-index]').forEach(el=>el.ondragstart=e=>{drag={index:Number(el.dataset.exprIndex)};e.dataTransfer.setData('text/plain','expression');});
      const drop=root.querySelector('[data-expr-drop]');
      drop.ondragover=e=>{if (!readOnly && drag) e.preventDefault();};
      drop.ondrop=e=>{e.preventDefault();if(readOnly||!drag)return;let index=Number(e.target.closest('[data-expr-index]')?.dataset.exprIndex??tokens.length);if(drag.token)insert(drag.token,index);else {const old=drag.index;const t=tokens.splice(old,1)[0];if(old<index)index--;tokens.splice(index,0,t);publish();}drag=null;};
      if(readOnly) root.querySelectorAll('input,select,button').forEach(el=>el.disabled=true);
    }
    draw();
  };
  A.choicePanel = (root, q, process, redraw, readOnly = false) => {
    if (q.control !== 'RADIO') {root.innerHTML=q.control==='CHECKBOX'?'<p class="pms-help">다중선택에는 계산용 숫자값과 가격 연관식을 등록할 수 없습니다.</p>':'';return;}
    const enabled = A.enabled(q);
    root.innerHTML=`<section class="pms-card"><label class="pms-check"><input type="checkbox" data-numeric-choice ${enabled?'checked':''} ${readOnly?'disabled':''}>이 단일선택의 옵션에 계산용 숫자값 사용</label><p class="pms-help">팀별 표시명과 선택 항목은 유지합니다. 필요할 때만 켜고 옵션별 숫자 또는 앞 입력을 이용한 식을 설정하세요. 빈 직접 숫자는 0입니다.</p>${enabled? q.choices.map((c,i)=>`<div class="pms-card"><strong>${S.e(name(c))}</strong><p>${S.e(A.text(c.numericValue?.expression,process))}</p><button type="button" data-choice-expression="${i}">${readOnly?'값·식 보기':'값·식 설정'}</button></div>`).join(''):''}</section>`;
    root.querySelector('[data-numeric-choice]').onchange=e=>S.run(e.target,async()=>{
      const enabled=e.target.checked;
      if(!enabled && q.choices.some(c=>c.numericValue) && !await S.confirm('계산용 숫자값 사용을 해제하면 이 질문의 모든 옵션에 설정한 숫자·수식이 삭제됩니다. 다시 켜면 0부터 설정합니다. 연결된 가격식도 재검토해야 합니다. 해제하시겠습니까?')){e.target.checked=true;return;}
      if(enabled)enabledQuestions.add(q);else enabledQuestions.delete(q);
      q.choices.forEach(c=>c.numericValue=enabled?{expression:A.zero()}:null);S.dirty=true;redraw();
    });
    root.querySelectorAll('[data-choice-expression]').forEach(b=>b.onclick=()=>{
      const c=q.choices[Number(b.dataset.choiceExpression)];let expression=S.copy(c.numericValue?.expression || A.zero());
      let mode=expression.length===1&&expression[0].kind==='NUMBER'?'number':'formula';
      let numberDraft=mode==='number'?S.copy(expression):A.zero(), formulaDraft=mode==='formula'?S.copy(expression):[];
      const d=S.dialog('계산용 숫자 · '+name(c),'');const body=S.$('.pms-dialog-body',d);
      function draw(){body.innerHTML=`<ol class="pm-stepper"><li class="active">1. 값 설정</li><li>2. 확인 후 적용</li></ol><label><span>값을 정하는 방식</span><select data-value-mode><option value="number" ${mode==='number'?'selected':''}>숫자 직접 입력</option><option value="formula" ${mode==='formula'?'selected':''}>앞 숫자 입력으로 계산</option></select></label>${mode==='number'?`<label><span>계산용 숫자 · 빈칸은 0</span><input data-direct-value type="number" min="0" max="1000000000" step="any" value="${S.e(expression[0]?.value??0)}"></label>`:'<div data-value-formula></div>'}<p class="pms-help">확정 숫자는 고객 답변을 기준으로 서버에서 다시 계산합니다. 표시명은 변경되지 않습니다.</p><button type="button" data-value-apply class="primary">${readOnly?'닫기':'확인 · 적용'}</button>`;
        body.querySelector('[data-value-mode]').onchange=e=>{if(mode==='number'){const el=body.querySelector('[data-direct-value]');numberDraft=[{kind:'NUMBER',value:el.value===''?0:Number(el.value)}];}else formulaDraft=S.copy(expression);mode=e.target.value;expression=S.copy(mode==='number'?numberDraft:formulaDraft);draw();};
        if(mode==='formula')A.editor(body.querySelector('[data-value-formula]'),expression,process,q.key,v=>expression=v,readOnly);
        if(readOnly)body.querySelectorAll('input,select').forEach(el=>el.disabled=true);
        body.querySelector('[data-value-apply]').onclick=e=>S.run(e.currentTarget,async()=>{if(!readOnly){if(mode==='number'){const el=body.querySelector('[data-direct-value]');if(!el.validity.valid)throw Error('0 이상 유효한 숫자를 입력해 주세요.');expression=[{kind:'NUMBER',value:el.value===''?0:Number(el.value)}];}A.syntax(expression);c.numericValue={expression};S.dirty=true;}d.close();redraw();});
      }draw();
    });
  };
  A.equalDialog = (q, context, old) => {
    const {limits,getProcess,redraw,readOnly=false}=context;
    const m=old?S.copy(old):{key:S.key('L'),name:'',targetKey:q.key,fieldKeys:[],aggregation:'SUM',min:null,max:null,sourceKey:null,cases:[],equalTo:[]};
    let stage=0;
    const d=S.dialog('합계가 일정한 입력 제한 · '+name(q),''),body=S.$('.pms-dialog-body',d);
    function draw(){body.innerHTML=`<ol class="pm-stepper">${['합계 대상','기준 숫자·계산식','확인'].map((x,i)=>`<li class="${stage===i?'active':''}">${i+1}. ${x}</li>`).join('')}</ol><div data-equal-content></div><div class="pms-actions">${stage?'<button type="button" data-equal-back>이전</button>':''}<button type="button" data-equal-next class="primary">${stage===2?readOnly?'닫기':'설정 적용':'다음'}</button></div>`;
      const box=body.querySelector('[data-equal-content]');
      if(stage===0){box.innerHTML=`${S.field('제한 이름','name',m.name)}<p class="pms-help">합계를 맞출 입력을 두 개 이상 선택하세요. W·H·D 중 W·H만 선택할 수 있습니다. 두 개라면 한쪽을 입력할 때 다른 쪽을 자동 계산합니다.</p><div class="pms-check-grid">${q.fields.map(f=>`<label class="pms-check"><input type="checkbox" data-equal-field="${S.e(f.key)}" ${m.fieldKeys.includes(f.key)?'checked':''}>${S.e(name(f))}</label>`).join('')}</div>`;S.bind(box,m);box.querySelectorAll('[data-equal-field]').forEach(el=>el.onchange=()=>m.fieldKeys=[...box.querySelectorAll('[data-equal-field]:checked')].map(x=>x.dataset.equalField));}
      if(stage===1)A.editor(box,m.equalTo,getProcess(),q.key,v=>m.equalTo=v,readOnly);
      if(stage===2)box.innerHTML=`<h3>${S.e(m.name)}</h3><p>${m.fieldKeys.map(k=>S.e(name(q.fields.find(f=>f.key===k)))).join(' + ')} = <strong>${S.e(A.text(m.equalTo,getProcess()))}</strong></p><p class="pms-help">각 입력의 최소·최대·간격도 동시에 검사합니다. 앞 답변을 바꾸면 기준값이 다시 계산됩니다. ${m.fieldKeys.length===2?'두 입력 중 마지막으로 수정한 값이 기준이 됩니다.':'세 개 이상은 직접 입력 후 합계를 검증합니다.'}</p>`;
      if(readOnly)box.querySelectorAll('input,select,button').forEach(el=>el.disabled=true);
      body.querySelector('[data-equal-back]')?.addEventListener('click',()=>{stage--;draw();});
      body.querySelector('[data-equal-next]').onclick=e=>S.run(e.currentTarget,async()=>{if(!readOnly){if(!m.name.trim()||m.fieldKeys.length<2)throw Error('제한 이름과 합계 대상 두 개 이상을 지정해 주세요.');if(stage>0)A.syntax(m.equalTo);}if(stage<2){stage++;draw();return;}if(!readOnly){const i=limits.findIndex(l=>l.key===m.key);if(i<0)limits.push(m);else limits[i]=m;S.dirty=true;}d.close();redraw();});
    }draw();
  };
  S.confirmationEditor = (root, process, readOnly=false, onChange=()=>{}) => {
    root.innerHTML=`<section class="pms-card"><h3>마지막 확인창 · 선택사항</h3><p class="pms-help">문구가 있으면 모든 옵션 선택 후 발주·장바구니 진행 전에 확인을 받습니다. 빈칸이면 확인창 없이 진행합니다.</p>${S.textarea('고객에게 확인받을 문구','confirmationMessage',process.confirmationMessage||'',2000)}<button type="button" data-confirm-preview>확인창 미리보기</button></section>`;
    S.bind(root,process,onChange);root.querySelector('textarea').disabled=readOnly;
    root.querySelector('[data-confirm-preview]').onclick=()=>window.alert(process.confirmationMessage?.trim()||'문구가 없어 확인창 없이 진행됩니다.');
  };
  S.confirmationTest = (root, process) => {
    const panel=document.createElement('section');panel.className='pms-card';panel.innerHTML='<h3>마지막 진행 테스트</h3><p class="pms-help">확인창과 진행 여부를 테스트합니다. 실제 주문·장바구니는 저장되지 않습니다.</p><div class="pms-actions"><button type="button" data-final-action="발주">발주하기</button><button type="button" data-final-action="장바구니">장바구니 담기</button></div><p data-final-status role="status"></p>';
    panel.querySelectorAll('[data-final-action]').forEach(b=>b.onclick=()=>{const message=process?.confirmationMessage?.trim();if(message)window.alert(message);panel.querySelector('[data-final-status]').textContent=`${b.dataset.finalAction} 진행 테스트 완료${message?' · 확인 문구 확인됨':''}. 실제 저장은 하지 않았습니다.`;});root.append(panel);
  };
  S.bindEqualInputs = (root, q, process, answers) => {
    const limits=(process?.inputLimits||[]).filter(l=>l.targetKey===q.key&&l.equalTo&&l.fieldKeys?.length===2);
    if(!limits.length)return;
    root.addEventListener('input',event=>{
      const changed=event.target.dataset.answerField;if(!changed)return;
      for(const l of limits.filter(l=>l.fieldKeys.includes(changed))){const partner=l.fieldKeys.find(k=>k!==changed),input=[...root.querySelectorAll('[data-answer-field]')].find(el=>el.dataset.answerField===partner);if(!input)continue;
        try {if(event.target.value===''){input.value='';continue;}const total=A.evaluate(l.equalTo,typeof answers === 'function' ? answers() : answers),n=total-Number(event.target.value);input.value=Number.isFinite(n)?String(Number(n.toFixed(12))):'';}catch(e){input.value='';}
      }
    },true);
  };
})(window.PMS);
