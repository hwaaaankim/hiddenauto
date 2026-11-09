(() => {
  'use strict';
  const launch=document.getElementById('admin-order-management-open');if(!launch)return;
  const e=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  async function api(path,data){
    const headers={'Content-Type':'application/json'},key=document.querySelector('meta[name="_csrf_header"]')?.content,token=document.querySelector('meta[name="_csrf"]')?.content;
    if(key&&token)headers[key]=token;
    const response=await fetch('/admin/order-management'+path,{method:data?'POST':'GET',headers,body:data?JSON.stringify(data):undefined});
    let result;try{result=JSON.parse(await response.text());}catch{throw Error('응답을 확인하지 못했습니다. 로그인 상태를 확인해 주세요.');}
    if(!response.ok)throw Error(result.message||(response.status===400?'변경 요청을 읽을 수 없습니다. 화면을 새로고침한 뒤 다시 시도해 주세요.':'발주관리 처리에 실패했습니다.'));return result;
  }
  const ids=()=>[...new Set([...document.querySelectorAll('.task-list-order-checkbox:checked:not(:disabled),.admin-task-list-second-bulk-checkbox:checked:not(:disabled)')].map(c=>Number(c.value)).filter(n=>n>0))];
  let rows=[],drafts=[],options,busy=false,dialog,area,changedAddress=null;
  const sync=()=>launch.disabled=busy||!ids().length;
  document.addEventListener('change',sync);document.addEventListener('click',sync);sync();
  const method=id=>options.methods.find(m=>m.id===Number(id));
  const eligible=d=>['CONFIRMED','PRODUCTION_DONE','DISPATCH_DONE','DELIVERY_DONE'].includes(rows.find(r=>r.orderId===d.orderId)?.status);
  const needsHandler=d=>!!method(d.deliveryMethodId)?.handlerRequired&&eligible(d);
  const handlerAllowed=m=>!!(m?.handlerAllowed??m?.handlerRequired);
  const canAssign=d=>handlerAllowed(method(d.deliveryMethodId))&&eligible(d);
  const opts=(list,value)=>'<option value="">선택</option>'+list.map(x=>`<option value="${x.id}" ${String(x.id)===String(value)?'selected':''}>${e(x.name)}</option>`).join('');
  const actual=r=>{
    const meaningful=v=>!['','없음','null','미입력','해당없음'].includes(String(v||'').normalize('NFKC').replace(/[\s‐‑‒–—−－-]/g,'').toLowerCase());
    const site=['siteRoadAddress','siteDetailAddress','siteDoName','siteSiName','siteGuName'].some(k=>meaningful(r[k]));
    return Object.fromEntries(['zipCode','doName','siName','guName','roadAddress','jibunAddress','detailAddress'].map(k=>[k,r[site?'site'+k[0].toUpperCase()+k.slice(1):k]||'']));
  };
  const addressHtml=a=>`<dl class="order-bulk-address"><div><dt>우편번호</dt><dd>${e(a.zipCode)||'미등록'}</dd></div><div><dt>도로명</dt><dd>${e(a.roadAddress)||'미등록'}</dd></div><div><dt>지번</dt><dd>${e(a.jibunAddress)||'미등록'}</dd></div></dl>`;
  const destination=d=>method(d.deliveryMethodId)?.site?'현장 배송지':'일반 배송지';
  function routingHint(){
    const site=drafts.filter(d=>method(d.deliveryMethodId)?.site).length;
    return `현재 선택 수단 기준: 현장 배송지 ${site}건 · 일반 배송지 ${drafts.length-site}건. 현장배송은 현장주소, 나머지 수단은 일반 배송주소에 반영합니다. 수단만 바꾸면 기존 실제 목적지를 유지합니다.`;
  }
  function syncBulkHandler(){
    const select=dialog.querySelector('[data-bulk-handler]'),selected=dialog.querySelector('[data-bulk-method]').value;
    const count=drafts.filter(d=>eligible(d)&&(selected?handlerAllowed(method(selected)):canAssign(d))).length;
    select.disabled=busy||!count;if(!count)select.value='';
    dialog.querySelector('[data-bulk-handler-hint]').textContent=count?`배정 가능한 ${count}건에만 적용합니다.`:'선택 수단·발주 상태에는 담당자를 배정하지 않습니다.';
    dialog.querySelector('[data-routing-hint]').textContent=routingHint();
  }
  function paint(){
    // Reflect the same assignment policy in the row, review and submitted request.
    drafts.forEach(d=>{if(!canAssign(d))d.deliveryHandlerId='';});
    area.innerHTML=drafts.map((d,i)=>`<article class="order-bulk-card" data-index="${i}"><header><strong>#${d.orderId}</strong><span>${e(rows[i].companyName)} · ${e(rows[i].productName)}</span></header>
      <div class="row g-2"><label class="col-md-4">배송수단<select class="form-select" data-field="deliveryMethodId">${opts(options.methods,d.deliveryMethodId)}</select></label>
      <label class="col-md-4">배송담당자<select class="form-select" data-field="deliveryHandlerId" ${canAssign(d)?'':'disabled'}>${opts(options.handlers,d.deliveryHandlerId)}</select><small>${needsHandler(d)?'배송담당자 필수':canAssign(d)?'배송담당자 선택사항':!eligible(d)?'승인 전·취소 상태에는 배정하지 않습니다.':'담당자 배정 불필요'}</small></label>
      <label class="col-md-4">출고일(배송희망일)<input type="date" class="form-control" data-field="preferredDeliveryDate" value="${e(d.preferredDeliveryDate)}"></label></div>
      <section class="order-bulk-destination"><div class="d-flex align-items-center justify-content-between gap-2"><strong>${e(method(d.deliveryMethodId)?.name||'수단 미선택')} → ${destination(d)}에 반영</strong><button type="button" class="btn btn-sm btn-outline-primary" data-search="${i}"><i class="ri-map-pin-line" aria-hidden="true"></i> 주소 검색</button></div>${addressHtml(d.address)}
      <label class="w-100">상세주소<input class="form-control" data-detail value="${e(d.address.detailAddress)}"></label></section>
      <div class="row g-2"><label class="col-md-6">수령자<input class="form-control" data-field="ordererName" ${method(d.deliveryMethodId)?.site?'':'readonly'} value="${e(d.ordererName)}"></label><label class="col-md-6">수령자 연락처<input class="form-control" data-field="ordererPhone" ${method(d.deliveryMethodId)?.site?'':'readonly'} value="${e(d.ordererPhone)}"></label>${method(d.deliveryMethodId)?.site?'':'<small>수령자 변경은 현장배송에 적용됩니다.</small>'}</div></article>`).join('');
    syncBulkHandler();
  }
  function capture(){area.querySelectorAll('[data-index]').forEach(card=>{const d=drafts[Number(card.dataset.index)];card.querySelectorAll('[data-field]').forEach(el=>d[el.dataset.field]=el.value);d.address.detailAddress=card.querySelector('[data-detail]').value;});}
  function postcode(done){
    if(!window.daum?.Postcode)throw Error('주소검색을 불러오는 중입니다. 잠시 후 다시 시도해 주세요.');
    const targetDialog=dialog;
    new window.daum.Postcode({oncomplete:data=>{
      if(dialog!==targetDialog||!targetDialog.open||!targetDialog.isConnected)return;
      const sig=(data.sigungu||'').trim().split(/\s+/),first=sig[0]||'',city=/(시|군)$/.test(first);
      done({zipCode:data.zonecode||'',doName:data.sido||'',siName:city?first:'',guName:city?sig.slice(1).join(' '):sig.join(' '),roadAddress:data.roadAddress||data.autoRoadAddress||data.address||'',jibunAddress:data.jibunAddress||data.autoJibunAddress||'',detailAddress:data.buildingName?'('+data.buildingName+')':''});
    }}).open();
  }
  function payload(d,i){
    const site=method(d.deliveryMethodId)?.site;
    // Send the request contract only. Draft fields such as `searched` must stay in the UI.
    return {orderId:d.orderId,deliveryMethodId:Number(d.deliveryMethodId),deliveryHandlerId:canAssign(d)?Number(d.deliveryHandlerId)||null:null,
      expectedUpdatedAt:rows[i].updatedAtToken,preferredDeliveryDate:d.preferredDeliveryDate||null,deliveryCost:rows[i].deliveryCost,dispatchComplete:false,
      ordererName:d.ordererName,ordererPhone:d.ordererPhone,keepDeliveryAddress:JSON.stringify(d.address)===JSON.stringify(actual(rows[i])),
      ...Object.fromEntries(['zipCode','doName','siName','guName','roadAddress','jibunAddress','detailAddress'].map(k=>[site?'site'+k[0].toUpperCase()+k.slice(1):k,d.address[k]])),
      generalAddressSearched:!site&&d.searched===true,siteAddressSearched:!!site&&d.searched===true};
  }
  launch.onclick=async()=>{
    if(busy||!ids().length)return;busy=true;sync();
    try{
      [options,rows]=await Promise.all([api('/options'),api('/preview',{orderIds:ids()})]);
      drafts=rows.map(r=>({orderId:r.orderId,deliveryMethodId:r.deliveryMethodId,deliveryHandlerId:r.deliveryHandlerId||'',preferredDeliveryDate:r.preferredDeliveryDateText||'',ordererName:r.ordererName||'',ordererPhone:r.ordererPhone||'',address:actual(r),searched:false}));changedAddress=null;
      dialog?.remove();dialog=document.createElement('dialog');dialog.className='admin-bulk-order-dialog';
      dialog.innerHTML=`<header class="order-bulk-head"><div><h4>선택 발주 일괄변경 <span>${rows.length}건</span></h4><p>변경 내용 확인 후 저장해야 반영됩니다.</p></div><button type="button" class="btn btn-sm btn-light" data-close aria-label="저장하지 않고 닫기"><i class="ri-close-line" aria-hidden="true"></i> 닫기</button></header>
        <div class="order-bulk-body"><section class="order-bulk-settings"><h5>공통으로 적용할 항목</h5><div class="row g-2"><label class="col-md-4">일괄 배송수단<select class="form-select" data-bulk-method>${opts(options.methods,'')}</select></label><label class="col-md-4">일괄 배송담당자<select class="form-select" data-bulk-handler>${opts(options.handlers,'')}</select><small data-bulk-handler-hint></small></label><label class="col-md-4">일괄 출고일<input type="date" class="form-control" data-bulk-date></label></div>
        
        <div data-bulk-address-preview class="order-bulk-address-preview" role="status"><p>아직 공통 주소를 검색하지 않았습니다. 아래 각 발주의 현재 주소는 그대로 유지됩니다.</p></div>
        <label class="w-100">일괄 상세주소<input class="form-control" data-bulk-detail placeholder="주소 검색 후 동·호수 등을 입력하세요" disabled></label><p data-routing-hint class="order-bulk-routing"></p><div class="order-bulk-actions"><button type="button" class="btn btn-sm btn-outline-primary" data-apply>수단·담당자·출고일 적용</button><button type="button" class="btn btn-sm btn-outline-primary" data-address><i class="ri-map-pin-line" aria-hidden="true"></i> 일괄 주소 검색</button><button type="button" class="btn btn-sm btn-outline-primary" data-apply-address disabled>검색한 주소를 전체에 적용</button></div></section>
        <div data-alert class="alert alert-danger" role="alert" hidden></div><div data-rows></div><div data-review class="order-bulk-review" hidden></div></div>
        <footer class="order-bulk-foot"><span>닫으면 저장하지 않은 변경은 취소됩니다.</span><button type="button" class="btn btn-sm btn-light" data-close>닫기</button><button type="button" class="btn btn-sm btn-primary" data-save>변경 내용 확인</button></footer>`;
      document.body.append(dialog);area=dialog.querySelector('[data-rows]');busy=false;paint();dialog.showModal();
      const alert=message=>{const el=dialog.querySelector('[data-alert]');el.hidden=false;el.textContent=message;el.scrollIntoView({block:'nearest'});};
      const resetReview=()=>{dialog.querySelector('[data-review]').hidden=true;dialog.querySelector('[data-save]').textContent='변경 내용 확인';};
      dialog.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>{if(!busy)dialog.close();});dialog.addEventListener('cancel',ev=>{if(busy)ev.preventDefault();});
      const openedDialog=dialog;dialog.addEventListener('close',()=>{if(dialog===openedDialog){changedAddress=null;drafts=[];}openedDialog.remove();sync();});
      dialog.addEventListener('input',resetReview);
      dialog.addEventListener('change',ev=>{capture();resetReview();if(ev.target.dataset.field==='deliveryMethodId'){drafts.forEach(d=>{if(!canAssign(d))d.deliveryHandlerId='';});paint();}else if(ev.target.matches('[data-bulk-method]'))syncBulkHandler();});
      dialog.querySelector('[data-apply]').onclick=()=>{capture();const m=dialog.querySelector('[data-bulk-method]').value,h=dialog.querySelector('[data-bulk-handler]').value,date=dialog.querySelector('[data-bulk-date]').value;drafts.forEach(d=>{if(m)d.deliveryMethodId=m;if(!canAssign(d))d.deliveryHandlerId='';else if(h)d.deliveryHandlerId=h;if(date)d.preferredDeliveryDate=date;});paint();resetReview();};
      area.onclick=ev=>{const b=ev.target.closest('[data-search]');if(b)try{capture();postcode(a=>{drafts[Number(b.dataset.search)].address=a;drafts[Number(b.dataset.search)].searched=true;paint();resetReview();});}catch(err){alert(err.message);}};
      dialog.querySelector('[data-address]').onclick=()=>{try{postcode(a=>{changedAddress=a;dialog.querySelector('[data-bulk-address-preview]').innerHTML=addressHtml(a);dialog.querySelector('[data-bulk-detail]').value=a.detailAddress;dialog.querySelector('[data-bulk-detail]').disabled=false;dialog.querySelector('[data-apply-address]').disabled=false;resetReview();});}catch(err){alert(err.message);}};
      dialog.querySelector('[data-apply-address]').onclick=()=>{if(!changedAddress)return;capture();drafts.forEach(d=>{d.address={...changedAddress,detailAddress:dialog.querySelector('[data-bulk-detail]').value};d.searched=true;});paint();resetReview();};
      dialog.querySelector('[data-save]').onclick=async()=>{
        if(busy)return;capture();const review=dialog.querySelector('[data-review]');
        try{
          drafts.forEach(d=>{if(!method(d.deliveryMethodId))throw Error('#'+d.orderId+' 배송수단을 선택해 주세요.');if(needsHandler(d)&&(!d.deliveryHandlerId||!d.preferredDeliveryDate))throw Error('#'+d.orderId+' 담당자와 출고일을 입력해 주세요.');if(canAssign(d)&&d.deliveryHandlerId&&!d.preferredDeliveryDate)throw Error('#'+d.orderId+' 담당자를 배정하려면 출고일을 입력해 주세요.');if(method(d.deliveryMethodId).site&&(!d.ordererName.trim()||!d.ordererPhone.trim()))throw Error('#'+d.orderId+' 현장 수령자와 연락처를 입력해 주세요.');});
          if(review.hidden){review.innerHTML='<h5>저장할 변경 내용</h5>'+drafts.map((d,i)=>`<section><strong>#${d.orderId}</strong> ${e(rows[i].deliveryMethodName)} → ${e(method(d.deliveryMethodId)?.name)} / 담당자 ${e(rows[i].deliveryHandlerName||'없음')} → ${e(options.handlers.find(h=>h.id===Number(d.deliveryHandlerId))?.name||'없음')} / 출고일 ${e(rows[i].preferredDeliveryDateText||'미등록')} → ${e(d.preferredDeliveryDate||'유지')}<p>기존: ${e(actual(rows[i]).roadAddress)} ${e(actual(rows[i]).detailAddress)}</p><strong>${destination(d)}에 반영</strong>${addressHtml(d.address)}<p>상세: ${e(d.address.detailAddress)||'없음'}</p>${method(d.deliveryMethodId)?.site?`<p>현장 수령자: ${e(d.ordererName)} ${e(d.ordererPhone)}</p>`:''}</section>`).join('');review.hidden=false;dialog.querySelector('[data-save]').textContent='확인한 내용 저장';review.scrollIntoView({block:'nearest'});return;}
          busy=true;dialog.querySelectorAll('button,input,select').forEach(el=>el.disabled=true);
          const result=await api('',{items:drafts.map(payload)});window.alert(result.updatedCount+'건을 저장했습니다.');location.reload();
        }catch(err){busy=false;dialog.querySelectorAll('button,input,select').forEach(el=>el.disabled=false);dialog.querySelector('[data-apply-address]').disabled=!changedAddress;dialog.querySelector('[data-bulk-detail]').disabled=!changedAddress;paint();alert(err.message);}
      };
    }catch(err){window.alert(err.message);}finally{busy=false;sync();}
  };
})();
