(() => {
  'use strict';
  const page=document.querySelector('.admin-task-list-second-page'),form=document.getElementById('task-list-filter-form');if(!page||!form)return;
  const names={company:'대리점명',requester:'발주자명',product:'제품명',address:'배송지'};
  const activeFields=new Set((page.dataset.keywordFields||'company,requester').split(',')),keyword=(page.dataset.keyword||'').trim();
  function markText(text){
    const result=document.createDocumentFragment(),lower=text.toLocaleLowerCase(),key=keyword.toLocaleLowerCase();let from=0,pos=lower.indexOf(key);
    if(!key||pos<0)return null;
    while(pos>=0){result.append(document.createTextNode(text.slice(from,pos)));const mark=document.createElement('mark');mark.className='order-keyword-hit';mark.textContent=text.slice(pos,pos+keyword.length);result.append(mark);from=pos+keyword.length;pos=lower.indexOf(key,from);}
    result.append(document.createTextNode(text.slice(from)));return result;
  }
  function highlight(root=document){
    if(!keyword)return;
    root.querySelectorAll('[data-keyword-field]').forEach(target=>{
      if(!activeFields.has(target.dataset.keywordField))return;
      const walk=document.createTreeWalker(target,NodeFilter.SHOW_TEXT,{acceptNode:node=>node.parentElement.closest('mark,script,style,textarea')?NodeFilter.FILTER_REJECT:NodeFilter.FILTER_ACCEPT}),nodes=[];
      while(walk.nextNode())nodes.push(walk.currentNode);
      nodes.forEach(node=>{const fragment=markText(node.textContent);if(fragment)node.replaceWith(fragment);});
    });
    root.querySelectorAll('[data-keyword-address]').forEach(target=>{
      const text=target.dataset.keywordAddress||'',key=keyword.toLocaleLowerCase(),pos=text.toLocaleLowerCase().indexOf(key);
      if(!activeFields.has('address')||pos<0){target.hidden=true;return;}
      if(target.dataset.matchKey===keyword+'\n'+text)return;
      const from=Math.max(0,pos-14),to=Math.min(text.length,pos+keyword.length+18),snippet=(from?'…':'')+text.slice(from,to)+(to<text.length?'…':'');
      target.replaceChildren(document.createTextNode('* 배송지: '),markText(snippet)||document.createTextNode(snippet));target.title='배송지 검색 일치 · '+text;target.hidden=false;target.dataset.matchKey=keyword+'\n'+text;
    });
  }
  window.HiddenBathOrderListUi={highlight};
  function init(){
    const checks=[...form.querySelectorAll('[data-keyword-option]')],hidden=form.querySelector('[name="keywordFields"]'),chips=form.querySelector('[data-keyword-chips]'),input=form.querySelector('[name="keyword"]');
    function syncFields(){
      const selected=checks.filter(c=>c.checked).map(c=>c.value);hidden.value=selected.join(',');chips.replaceChildren();
      for(const key of selected){const chip=document.createElement('span');chip.textContent=names[key];chips.append(chip);}
      if(!selected.length)chips.textContent='검색 항목 선택';
    }
    checks.forEach(c=>c.addEventListener('change',syncFields));syncFields();
    document.addEventListener('click',ev=>{const picker=form.querySelector('.order-keyword-picker');if(picker&&!picker.contains(ev.target))picker.open=false;});
    form.querySelector('[data-clear-legacy-product]')?.addEventListener('click',ev=>{ev.target.closest('.order-legacy-product').remove();form.requestSubmit();});
    form.addEventListener('submit',ev=>{syncFields();if(input.value.trim()&&!hidden.value){ev.preventDefault();ev.stopImmediatePropagation();form.querySelector('.order-keyword-picker').open=true;checks[0]?.focus();window.alert('키워드를 검색할 항목을 한 개 이상 선택해 주세요.');}},true);
    const consolePanel=document.getElementById('task-list-console'),remote=document.getElementById('task-list-remote-open');let remoteDialog=null,placeholder=null,returnFocus=true;
    function selection(){const n=new Set([...document.querySelectorAll('.task-list-order-checkbox:checked:not(:disabled),.admin-task-list-second-bulk-checkbox:checked:not(:disabled)')].map(c=>c.value)).size;document.querySelector('[data-selection-count]').textContent='선택 '+n+'건';document.querySelector('[data-remote-selection]').textContent=String(n);}
    document.addEventListener('change',selection);document.addEventListener('click',selection);selection();
    function visibility(){if(remoteDialog?.open)return;const r=consolePanel.getBoundingClientRect();remote.hidden=r.bottom>90&&r.top<innerHeight;}
    function restore(){if(placeholder?.isConnected){placeholder.replaceWith(consolePanel);placeholder=null;}}
    function closeRemote(focus=true){returnFocus=focus;restore();remoteDialog?.close();visibility();}
    remote.onclick=()=>{
      if(remoteDialog?.open)return;
      placeholder=document.createElement('div');placeholder.style.height=consolePanel.getBoundingClientRect().height+'px';placeholder.setAttribute('aria-hidden','true');consolePanel.before(placeholder);
      remoteDialog=document.createElement('dialog');remoteDialog.className='order-console-dialog';remoteDialog.innerHTML='<header><div><strong>발주 조회·작업</strong><small>목록의 선택 상태와 입력값을 그대로 사용합니다.</small></div><button type="button" class="btn btn-sm btn-light" data-remote-close aria-label="조회 작업 도구 닫기">닫기 <i class="ri-close-line" aria-hidden="true"></i></button></header><div data-remote-body></div>';
      remoteDialog.querySelector('[data-remote-body]').append(consolePanel);document.body.append(remoteDialog);remoteDialog.querySelector('[data-remote-close]').onclick=()=>closeRemote();remoteDialog.addEventListener('cancel',ev=>{ev.preventDefault();closeRemote();});const openedDialog=remoteDialog;remoteDialog.addEventListener('close',()=>{if(remoteDialog!==openedDialog){openedDialog.remove();return;}restore();openedDialog.remove();visibility();if(returnFocus&&!remote.hidden)remote.focus({preventScroll:true});});remoteDialog.showModal();remote.hidden=true;
    };
    // Existing action handlers and IDs remain mounted once. Release the native top layer before opening other dialogs.
    consolePanel.addEventListener('click',ev=>{if(remoteDialog?.open&&ev.target.closest('.order-tool-actions button,.order-tool-actions a'))closeRemote(false);},true);
    form.addEventListener('submit',()=>{if(remoteDialog?.open)closeRemote(false);});
    if(window.IntersectionObserver){new IntersectionObserver(entries=>{if(!remoteDialog?.open)remote.hidden=entries[0].isIntersecting;},{rootMargin:'-80px 0px 0px 0px',threshold:0}).observe(consolePanel);}
    window.addEventListener('scroll',visibility,{passive:true});window.addEventListener('resize',visibility);visibility();
    const table=document.getElementById('tasksTable');highlight(document);
    if(table&&window.MutationObserver){let scheduled=false;new MutationObserver(()=>{if(scheduled)return;scheduled=true;requestAnimationFrame(()=>{scheduled=false;highlight(table);});}).observe(table,{childList:true,subtree:true});}
    let refreshing=false;
    async function refreshPending(){
      if(refreshing||document.hidden)return;const badges=[...document.querySelectorAll('[data-order-change-badge]')],ids=[...new Set(badges.map(b=>b.dataset.orderId).filter(Boolean))];if(!ids.length)return;
      refreshing=true;
      try{const pending=new Set();for(let i=0;i<ids.length;i+=500){const res=await fetch('/management/nonStandardTaskList/pending-changes?orderIds='+encodeURIComponent(ids.slice(i,i+500).join(',')),{headers:{'X-Requested-With':'XMLHttpRequest'}});if(!res.ok)return;for(const id of await res.json())pending.add(String(id));}badges.forEach(b=>b.hidden=!pending.has(b.dataset.orderId));}
      catch(err){console.warn('발주 변경 확인 상태를 갱신하지 못했습니다.',err);}finally{refreshing=false;}
    }
    window.addEventListener('hiddenbath:order-notifications-updated',refreshPending);window.addEventListener('focus',refreshPending);document.addEventListener('visibilitychange',()=>{if(!document.hidden)refreshPending();});
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init);else init();
})();
