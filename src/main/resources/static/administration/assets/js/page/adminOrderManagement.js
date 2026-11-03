(() => {
  'use strict';
  const launch = document.getElementById('admin-order-management-open');
  if (!launch) return;
  const e = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const api = async (path, data) => {
    const headers = {'Content-Type':'application/json'};
    const key = document.querySelector('meta[name="_csrf_header"]')?.content, token = document.querySelector('meta[name="_csrf"]')?.content;
    if (key && token) headers[key] = token;
    const response = await fetch('/admin/order-management' + path, {method: data ? 'POST' : 'GET', headers, body: data ? JSON.stringify(data) : undefined});
    const text = await response.text(); let result; try { result=JSON.parse(text); } catch { throw Error('응답을 확인하지 못했습니다. 로그인 상태를 확인해 주세요.'); }
    if (!response.ok) throw Error(result.message || '발주관리 처리에 실패했습니다.'); return result;
  };
  const ids = () => [...new Set([...document.querySelectorAll('.task-list-order-checkbox:checked:not(:disabled),.admin-task-list-second-bulk-checkbox:checked:not(:disabled)')].map(c=>Number(c.value)).filter(n=>n>0))];
  const sync = () => launch.disabled = !ids().length;
  document.addEventListener('change',sync);document.addEventListener('click',sync); sync();
  let rows=[], drafts=[], options, busy=false, dialog, area, changedAddress=null;
  const eligible = d => !['REQUESTED','CANCELED'].includes(rows.find(r=>r.orderId===d.orderId)?.status);
  const needsHandler = d => method(d.deliveryMethodId)?.handlerRequired && eligible(d);
  const method = id => options.methods.find(m=>m.id===Number(id));
  const opts = (list,value) => '<option value="">선택</option>'+list.map(x=>`<option value="${x.id}" ${String(x.id)===String(value)?'selected':''}>${e(x.name)}</option>`).join('');
  const actual = r => {
    const site = ['siteRoadAddress','siteDetailAddress','siteDoName','siteSiName','siteGuName'].some(k => r[k] && r[k] !== '-');
    const p = site ? 'site' : '';
    return Object.fromEntries(['zipCode','doName','siName','guName','roadAddress','jibunAddress','detailAddress'].map(k=>[k, r[p ? p+k[0].toUpperCase()+k.slice(1) : k] || '']));
  };
  function paint() {
    area.innerHTML = drafts.map((d,i)=>`<article class="border rounded p-3 mb-3" data-index="${i}"><strong>#${d.orderId} · ${e(rows[i].companyName)} · ${e(rows[i].productName)}</strong>
      <div class="row g-2 mt-1"><label class="col-md-4">배송수단<select class="form-select" data-field="deliveryMethodId">${opts(options.methods,d.deliveryMethodId)}</select></label>
      <label class="col-md-4">배송담당자<select class="form-select" data-field="deliveryHandlerId" ${needsHandler(d)?'':'disabled'}>${opts(options.handlers,d.deliveryHandlerId)}</select><small>${needsHandler(d)?'배송담당자 필수':!eligible(d)?'승인 전·취소 상태에는 담당자를 배정하지 않습니다.':'담당자 배정 불필요'}</small></label>
      <label class="col-md-4">출고일(배송희망일)<input type="date" class="form-control" data-field="preferredDeliveryDate" value="${e(d.preferredDeliveryDate)}"></label></div>
      <div class="mt-2"><span class="badge bg-light text-dark">${method(d.deliveryMethodId)?.site?'현장주소에 저장':'일반 배송주소에 저장'}</span> <button class="btn btn-sm btn-outline-primary" data-search="${i}">주소 검색</button>
      <p class="mb-1 mt-2"><strong>도로명:</strong> [${e(d.address.zipCode)}] ${e(d.address.roadAddress)}</p><p class="mb-1"><strong>지번:</strong> ${e(d.address.jibunAddress)||'미등록'}</p>
      <label class="w-100">상세주소<input class="form-control" data-detail value="${e(d.address.detailAddress)}"></label></div>
      <div class="row g-2 mt-1"><label class="col-md-6">수령자<input class="form-control" data-field="ordererName" ${method(d.deliveryMethodId)?.site?'':'readonly'} value="${e(d.ordererName)}"></label><label class="col-md-6">수령자 연락처<input class="form-control" data-field="ordererPhone" ${method(d.deliveryMethodId)?.site?'':'readonly'} value="${e(d.ordererPhone)}"></label>${method(d.deliveryMethodId)?.site?'':'<small>수령자 변경은 현장배송을 선택한 경우에 적용됩니다.</small>'}</div></article>`).join('');
  }
  function capture() { area.querySelectorAll('[data-index]').forEach(card=>{ const d=drafts[Number(card.dataset.index)];card.querySelectorAll('[data-field]').forEach(el=>d[el.dataset.field]=el.value);d.address.detailAddress=card.querySelector('[data-detail]').value; }); }
  function postcode(done) {
    if (!window.daum?.Postcode) throw Error('주소검색을 불러오는 중입니다. 잠시 후 다시 시도해 주세요.');
    new window.daum.Postcode({oncomplete:data=>{
      const sig=(data.sigungu||'').trim().split(/\s+/), first=sig[0]||'', city=/(시|군)$/.test(first);
      done({zipCode:data.zonecode||'',doName:data.sido||'',siName:city?first:'',guName:city?sig.slice(1).join(' '):sig.join(' '),roadAddress:data.roadAddress||data.autoRoadAddress||'',jibunAddress:data.jibunAddress||data.autoJibunAddress||'',detailAddress:data.buildingName?'('+data.buildingName+')':''});
    }}).open();
  }
  function payload(d,i) {
    const site=method(d.deliveryMethodId)?.site;
    return {...d, address:undefined, deliveryMethodId:Number(d.deliveryMethodId),deliveryHandlerId:needsHandler(d)?Number(d.deliveryHandlerId)||null:null,
      expectedUpdatedAt:rows[i].updatedAtToken,preferredDeliveryDate:d.preferredDeliveryDate||null,deliveryCost:rows[i].deliveryCost,dispatchComplete:false,keepDeliveryAddress:JSON.stringify(d.address)===JSON.stringify(actual(rows[i])),
      ...Object.fromEntries(Object.entries(d.address).map(([k,v])=>[site?'site'+k[0].toUpperCase()+k.slice(1):k,v])),
      generalAddressSearched:!site&&d.searched,siteAddressSearched:!!site&&d.searched};
  }
  launch.onclick=async()=>{
    if (busy || !ids().length) return; busy=true;launch.disabled=true;
    try {
      const selected=ids(); const results=await Promise.all([api('/options'),api('/preview',{orderIds:selected})]); options=results[0];rows=results[1];
      drafts=rows.map(r=>({orderId:r.orderId,deliveryMethodId:r.deliveryMethodId,deliveryHandlerId:r.deliveryHandlerId||'',preferredDeliveryDate:r.preferredDeliveryDateText||'',ordererName:r.ordererName||'',ordererPhone:r.ordererPhone||'',address:actual(r),searched:false}));
      if(dialog) dialog.remove(); dialog=document.createElement('dialog');dialog.className='admin-bulk-order-dialog';dialog.style.cssText='width:min(1200px,96vw);max-height:92vh;border:1px solid #cbd5e1;border-radius:12px;padding:24px';
      dialog.innerHTML=`<h4>선택 발주 일괄변경 · ${rows.length}건</h4><p>배송수단 변경 시 기존 배송지를 유지합니다. 주소를 교체하려면 검색해 주세요. 저장 전 변경 내용을 확인합니다.</p>
        <div class="row g-2 border rounded p-2 mb-3"><label class="col-md-4">일괄 배송수단<select class="form-select" data-bulk-method>${opts(options.methods,'')}</select></label><label class="col-md-4">일괄 담당자<select class="form-select" data-bulk-handler>${opts(options.handlers,'')}</select></label><label class="col-md-4">일괄 출고일<input type="date" class="form-control" data-bulk-date></label><div class="col-12"><button class="btn btn-outline-primary" data-apply>입력한 수단·담당자·출고일 일괄 적용</button> <button class="btn btn-outline-primary" data-address>일괄 주소 검색</button><label class="d-block mt-2">일괄 상세주소<input class="form-control" data-bulk-detail placeholder="주소 검색 후 입력하세요"></label><button class="btn btn-outline-secondary mt-2" data-apply-address disabled>검색한 주소를 전체에 적용</button></div></div>
        <div data-alert class="alert alert-danger" role="alert" hidden></div><div data-rows></div><div data-review hidden></div><footer class="d-flex gap-2 justify-content-end"><button class="btn btn-light" data-close>닫기</button><button class="btn btn-primary" data-save>변경 내용 확인</button></footer>`;
      document.body.append(dialog);area=dialog.querySelector('[data-rows]');paint();dialog.showModal(); changedAddress=null;
      const alert=message=>{const el=dialog.querySelector('[data-alert]');el.hidden=false;el.textContent=message;el.scrollIntoView({block:'nearest'});};
      const resetReview=()=>{dialog.querySelector('[data-review]').hidden=true;dialog.querySelector('[data-save]').textContent='변경 내용 확인';};
      dialog.addEventListener('input',resetReview);dialog.addEventListener('change',ev=>{capture();resetReview();if(ev.target.dataset.field==='deliveryMethodId'){drafts.forEach(d=>{if(!needsHandler(d))d.deliveryHandlerId='';});paint();}});
      dialog.querySelector('[data-close]').onclick=()=>dialog.close();
      dialog.querySelector('[data-apply]').onclick=()=>{capture();const m=dialog.querySelector('[data-bulk-method]').value,h=dialog.querySelector('[data-bulk-handler]').value,date=dialog.querySelector('[data-bulk-date]').value;drafts.forEach(d=>{if(m)d.deliveryMethodId=m;if(!needsHandler(d))d.deliveryHandlerId='';else if(h)d.deliveryHandlerId=h;if(date)d.preferredDeliveryDate=date;});paint();resetReview();};
      area.onclick=ev=>{const button=ev.target.closest('[data-search]');if(button)try{capture();postcode(a=>{drafts[Number(button.dataset.search)].address=a;drafts[Number(button.dataset.search)].searched=true;paint();resetReview();});}catch(err){alert(err.message);}};
      dialog.querySelector('[data-address]').onclick=()=>{try{postcode(a=>{changedAddress=a;dialog.querySelector('[data-bulk-detail]').value=a.detailAddress;dialog.querySelector('[data-apply-address]').disabled=false;});}catch(err){alert(err.message);}};
      dialog.querySelector('[data-apply-address]').onclick=()=>{if(!changedAddress)return;capture();drafts.forEach(d=>{d.address={...changedAddress,detailAddress:dialog.querySelector('[data-bulk-detail]').value};d.searched=true;});paint();resetReview();};
      dialog.querySelector('[data-save]').onclick=async()=>{
        if(busy)return;capture();const review=dialog.querySelector('[data-review]');
        try{
          drafts.forEach(d=>{if(!method(d.deliveryMethodId))throw Error('#'+d.orderId+' 배송수단을 선택해 주세요.');if(needsHandler(d)&&(!d.deliveryHandlerId||!d.preferredDeliveryDate))throw Error('#'+d.orderId+' 담당자와 출고일을 입력해 주세요.');if(method(d.deliveryMethodId).site&&(!d.ordererName.trim()||!d.ordererPhone.trim()))throw Error('#'+d.orderId+' 현장 수령자와 연락처를 입력해 주세요.');});
          if(review.hidden){review.innerHTML='<h5>저장할 변경 내용</h5>'+drafts.map((d,i)=>`<p><strong>#${d.orderId}</strong> ${e(rows[i].deliveryMethodName)} → ${e(method(d.deliveryMethodId)?.name)} / 담당자 ${e(rows[i].deliveryHandlerName||'없음')} → ${e(options.handlers.find(h=>h.id===Number(d.deliveryHandlerId))?.name||'없음')} / 출고일 ${e(rows[i].preferredDeliveryDateText||'미등록')} → ${e(d.preferredDeliveryDate||'유지')}<br>주소: ${e(actual(rows[i]).roadAddress)} ${e(actual(rows[i]).detailAddress)} → <strong>${e(d.address.roadAddress)} ${e(d.address.detailAddress)}</strong><br>수령자: ${e(rows[i].ordererName)} ${e(rows[i].ordererPhone)} → ${e(d.ordererName)} ${e(d.ordererPhone)}</p>`).join('');review.hidden=false;dialog.querySelector('[data-save]').textContent='확인한 내용 저장';review.scrollIntoView({block:'nearest'});return;}
          busy=true;dialog.querySelectorAll('button,input,select').forEach(el=>el.disabled=true);
          const result=await api('',{items:drafts.map(payload)});window.alert(result.updatedCount+'건을 저장했습니다.');location.reload();
        }catch(err){alert(err.message);busy=false;dialog.querySelectorAll('button,input,select').forEach(el=>el.disabled=false);paint();}
      };
    }catch(err){window.alert(err.message);}finally{busy=false;sync();}
  };
})();
