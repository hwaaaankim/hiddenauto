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
  let rows=[],drafts=[],options,busy=false,dialog,area,changedAddress=null,matchedBulkHandler=false;
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
    select.disabled=busy||!count;if(!count){select.value='';matchedBulkHandler=false;dialog.querySelector('[data-match-status]').textContent='현재 배송수단·발주 상태에는 담당자를 배정하지 않습니다.';}
    dialog.querySelector('[data-match-handler]').disabled=busy||!changedAddress||!selected||!handlerAllowed(method(selected))||!count;
    dialog.querySelector('[data-clear-address]').disabled=busy||!changedAddress;
    dialog.querySelector('[data-bulk-handler-hint]').textContent=count?`배정 가능한 ${count}건에만 적용합니다.`:'선택 수단·발주 상태에는 담당자를 배정하지 않습니다.';
    dialog.querySelector('[data-routing-hint]').textContent=routingHint();
  }
  function applyShippingSettings(settings){
    drafts.forEach(d=>{
      if(settings.methodId)d.deliveryMethodId=settings.methodId;
      if(!canAssign(d))d.deliveryHandlerId='';else if(settings.handlerId)d.deliveryHandlerId=settings.handlerId;
      if(settings.date)d.preferredDeliveryDate=settings.date;
      if(settings.address){d.address={...settings.address};d.searched=true;}
    });
  }
  function applyDirectProposals(proposals,date){
    drafts.forEach(d=>{
      const p=proposals.get(d.orderId);
      d.deliveryMethodId=p.deliveryMethodId;
      d.deliveryHandlerId=canAssign(d)?p.deliveryHandlerId||'':'';
      d.address=Object.fromEntries(['zipCode','doName','siName','guName','roadAddress','jibunAddress','detailAddress'].map(k=>[k,p[k]||'']));
      d.searched=true;
      if(date)d.preferredDeliveryDate=date;
    });
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
      <div class="row g-2"><label class="col-md-6">주문자 이름<input class="form-control" maxlength="50" data-field="ordererName" value="${e(d.ordererName)}"></label><label class="col-md-6">주문자 연락처<input type="tel" class="form-control" maxlength="30" data-field="ordererPhone" value="${e(d.ordererPhone)}"></label></div><div class="d-flex align-items-center justify-content-between gap-2 mt-2"><small>${method(d.deliveryMethodId)?.site?'현장배송은 이 이름·연락처를 수령자 정보로 사용하므로 모두 입력해 주세요.':'이 발주의 주문자 정보를 변경합니다.'}</small><button type="button" class="btn btn-sm btn-outline-secondary" data-reset-orderer="${i}">주문자 정보 되돌리기</button></div><label class="w-100 mt-2">비고 (관리자 메모)<textarea class="form-control" rows="2" data-field="adminMemo">${e(d.adminMemo)}</textarea></label><button type="button" class="btn btn-sm btn-outline-secondary mt-1" data-reset-memo="${i}">비고 되돌리기</button></article>`).join('');
    syncBulkHandler();
  }
  function capture(){area.querySelectorAll('[data-index]').forEach(card=>{const d=drafts[Number(card.dataset.index)];card.querySelectorAll('[data-field]').forEach(el=>d[el.dataset.field]=el.value);d.address.detailAddress=card.querySelector('[data-detail]').value;});}
  function postcode(done){
    if(!window.daum?.Postcode)throw Error('주소검색을 불러오는 중입니다. 잠시 후 다시 시도해 주세요.');
    const targetDialog=dialog;
    new window.daum.Postcode({oncomplete:data=>{
      if(dialog!==targetDialog||!targetDialog.open||!targetDialog.isConnected||busy)return;
      const sig=(data.sigungu||'').trim().split(/\s+/),first=sig[0]||'',city=/(시|군)$/.test(first);
      done({zipCode:data.zonecode||'',doName:data.sido||'',siName:city?first:'',guName:city?sig.slice(1).join(' '):sig.join(' '),roadAddress:data.roadAddress||data.autoRoadAddress||data.address||'',jibunAddress:data.jibunAddress||data.autoJibunAddress||'',detailAddress:data.buildingName?'('+data.buildingName+')':''});
    }}).open();
  }
  function payload(d,i){
    const site=method(d.deliveryMethodId)?.site;
    // Send the request contract only. Draft fields such as `searched` must stay in the UI.
    return {orderId:d.orderId,deliveryMethodId:Number(d.deliveryMethodId),deliveryHandlerId:canAssign(d)?Number(d.deliveryHandlerId)||null:null,
      expectedUpdatedAt:rows[i].updatedAtToken,preferredDeliveryDate:d.preferredDeliveryDate||null,deliveryCost:rows[i].deliveryCost,dispatchComplete:false,
      ordererName:d.ordererName,ordererPhone:d.ordererPhone,adminMemo:d.adminMemo===(rows[i].adminMemoValue??'')?null:d.adminMemo,keepDeliveryAddress:JSON.stringify(d.address)===JSON.stringify(actual(rows[i])),
      ...Object.fromEntries(['zipCode','doName','siName','guName','roadAddress','jibunAddress','detailAddress'].map(k=>[site?'site'+k[0].toUpperCase()+k.slice(1):k,d.address[k]])),
      generalAddressSearched:!site&&d.searched===true,siteAddressSearched:!!site&&d.searched===true};
  }
  launch.onclick=async()=>{
    if(busy||!ids().length)return;busy=true;sync();
    try{
      [options,rows]=await Promise.all([api('/options'),api('/preview',{orderIds:ids()})]);
      drafts=rows.map(r=>({orderId:r.orderId,deliveryMethodId:r.deliveryMethodId,deliveryHandlerId:r.deliveryHandlerId||'',preferredDeliveryDate:r.preferredDeliveryDateText||'',ordererName:r.ordererName||'',ordererPhone:r.ordererPhone||'',adminMemo:r.adminMemoValue??'',address:actual(r),searched:false}));changedAddress=null;matchedBulkHandler=false;
      dialog?.remove();dialog=document.createElement('dialog');dialog.className='admin-bulk-order-dialog';
      dialog.innerHTML=`<header class="order-bulk-head"><div><h4>선택 발주 일괄변경 <span>${rows.length}건</span></h4><p>변경 내용 확인 후 저장해야 반영됩니다.</p></div><button type="button" class="btn btn-sm btn-light" data-close aria-label="저장하지 않고 닫기"><i class="ri-close-line" aria-hidden="true"></i> 닫기</button></header>
        <div class="order-bulk-body"><section class="order-bulk-settings"><h5>배송 정보 일괄 적용</h5>
        <label class="d-block">공통 출고일<input type="date" class="form-control" data-bulk-date></label><p class="order-bulk-routing">아래 두 방식에서 같은 출고일을 사용합니다. 날짜를 비워두면 각 발주의 현재 출고일을 유지합니다. 입력한 뒤 원하는 방식의 적용 버튼을 눌러 주세요.</p>
        <section class="order-bulk-destination"><h5>주소·배송수단·담당자 직접 설정</h5>
        <div class="order-bulk-actions"><button type="button" class="btn btn-sm btn-outline-primary" data-address><i class="ri-map-pin-line" aria-hidden="true"></i> 1. 공통 주소 검색</button><button type="button" class="btn btn-sm btn-outline-secondary" data-clear-address disabled>검색 주소 선택 해제</button></div>
        <div data-bulk-address-preview class="order-bulk-address-preview" role="status"><p>주소를 검색하면 아래 적용 버튼으로 전체 발주에 반영할 수 있습니다. 검색하지 않으면 각 발주의 현재 주소를 유지합니다.</p></div>
        <label class="w-100">일괄 상세주소<input class="form-control" data-bulk-detail placeholder="주소 검색 후 동·호수 등을 입력하세요" disabled></label>
        <div class="row g-2 mt-2"><label class="col-md-6">2. 배송수단<select class="form-select" data-bulk-method>${opts(options.methods,'')}</select><small>선택하지 않으면 각 발주의 수단을 유지합니다.</small></label>
        <label class="col-md-6">3. 배송담당자<select class="form-select" data-bulk-handler>${opts(options.handlers,'')}</select><small data-bulk-handler-hint></small></label></div>
        <div class="order-bulk-actions"><button type="button" class="btn btn-sm btn-outline-primary" data-match-handler disabled>검색 주소의 행정구역으로 담당자 찾기</button></div>
        <p data-match-status class="order-bulk-routing" role="status" aria-live="polite">공통 주소와 배정 가능한 배송수단을 선택하면 담당자를 찾을 수 있습니다. 직접 선택도 가능합니다. 같은 우선순위 후보가 여러 명이면 기존 자동배정 기준으로 한 명을 선택합니다.</p>
        <p data-routing-hint class="order-bulk-routing"></p><p class="order-bulk-routing">설정한 주소·수단·담당자·공통 출고일을 한 번에 적용합니다. 설정하지 않은 항목은 유지하며, 담당자 비대상 수단·상태는 배정을 해제합니다.</p>
        <button type="button" class="btn btn-sm btn-primary" data-apply>주소·배송수단·담당자·출고일 전체에 적용</button></section>
        <section class="order-bulk-destination"><h5>업체 주소 기준 직배송 자동변경</h5><p>각 발주를 직배송으로 변경하고 해당 업체 기본 주소와 지역 담당자를 자동으로 설정합니다. 위에서 선택한 공통 출고일을 함께 적용합니다. 직접 설정 영역의 주소·수단·담당자는 사용하지 않습니다.</p><button type="button" class="btn btn-sm btn-outline-primary" data-direct-preview>직배송·업체 주소·담당자 미리보기</button><div data-direct-review class="order-bulk-review" hidden></div></section></section>
        <section class="order-bulk-destination"><h5>주문자 정보 일괄 적용</h5><div class="row g-2"><label class="col-md-6">일괄 주문자 이름<input class="form-control" maxlength="50" data-bulk-orderer-name placeholder="적용할 이름"></label><label class="col-md-6">일괄 주문자 연락처<input type="tel" class="form-control" maxlength="30" data-bulk-orderer-phone placeholder="적용할 연락처"></label></div><p class="order-bulk-routing">입력한 항목만 전체 발주에 덮어씁니다. 빈칸은 기존 값을 유지합니다. 아래에서 개별 수정할 수 있으며, 최종 저장 전까지 서버에 반영되지 않습니다.</p><div class="order-bulk-actions"><button type="button" class="btn btn-sm btn-outline-primary" data-apply-orderer>주문자 정보 전체에 적용</button><button type="button" class="btn btn-sm btn-outline-secondary" data-reset-orderer="all">전체 주문자 정보 되돌리기</button></div><small>되돌리기는 창을 열었을 때의 이름·연락처만 복원합니다. 변경한 배송수단·주소·담당자·출고일은 유지됩니다.</small></section>
        <section class="order-bulk-destination"><h5>비고 일괄 적용</h5><textarea class="form-control" rows="2" data-bulk-memo placeholder="전체 발주에 적용할 관리자 비고"></textarea><p class="order-bulk-routing">입력한 비고로 전체를 덮어씁니다. 비고를 지우려면 ‘전체 비고 비우기’를 사용하세요. 적용 후 개별 수정할 수 있습니다.</p><div class="order-bulk-actions"><button type="button" class="btn btn-sm btn-outline-primary" data-apply-memo>비고 전체에 적용</button><button type="button" class="btn btn-sm btn-outline-secondary" data-clear-memo>전체 비고 비우기</button><button type="button" class="btn btn-sm btn-outline-secondary" data-reset-memo="all">전체 비고 되돌리기</button></div></section>
        <p data-orderer-status class="order-bulk-routing" role="status" aria-live="polite"></p>
        <div data-alert class="alert alert-danger" role="alert" hidden></div><div data-rows></div><div data-review class="order-bulk-review" hidden></div></div>
        <footer class="order-bulk-foot"><span>닫으면 저장하지 않은 변경은 취소됩니다.</span><button type="button" class="btn btn-sm btn-light" data-close>닫기</button><button type="button" class="btn btn-sm btn-primary" data-save>변경 내용 확인</button></footer>`;
      document.body.append(dialog);area=dialog.querySelector('[data-rows]');busy=false;paint();dialog.showModal();
      const alert=message=>{const el=dialog.querySelector('[data-alert]');el.hidden=false;el.textContent=message;el.scrollIntoView({block:'nearest'});};
      let directProposals=null;
      const resetReview=()=>{directProposals=null;dialog.querySelector('[data-direct-review]').hidden=true;dialog.querySelector('[data-review]').hidden=true;dialog.querySelector('[data-save]').textContent='변경 내용 확인';};
      dialog.querySelectorAll('[data-close]').forEach(b=>b.onclick=()=>{if(!busy)dialog.close();});dialog.addEventListener('cancel',ev=>{if(busy)ev.preventDefault();});
      const openedDialog=dialog;dialog.addEventListener('close',()=>{if(dialog===openedDialog){changedAddress=null;drafts=[];}openedDialog.remove();sync();});
      dialog.addEventListener('input',resetReview);
      dialog.addEventListener('change',ev=>{capture();resetReview();if(ev.target.dataset.field==='deliveryMethodId'){drafts.forEach(d=>{if(!canAssign(d))d.deliveryHandlerId='';});paint();}else if(ev.target.matches('[data-bulk-method]'))syncBulkHandler();else if(ev.target.matches('[data-bulk-handler]')){matchedBulkHandler=false;dialog.querySelector('[data-match-status]').textContent='담당자 선택을 직접 변경했습니다. 적용 버튼을 눌러 반영해 주세요.';}});
      const commonDate=()=>{
        const input=dialog.querySelector('[data-bulk-date]');
        if(!input.checkValidity())throw Error('공통 출고일을 올바르게 입력해 주세요.');
        return input.value;
      };
      dialog.querySelector('[data-apply]').onclick=()=>{
        if(busy)return;
        try{
          capture();const methodId=dialog.querySelector('[data-bulk-method]').value,handlerId=dialog.querySelector('[data-bulk-handler]').value,date=commonDate();
          if(!methodId&&!handlerId&&!date&&!changedAddress){alert('적용할 주소·배송수단·담당자·출고일 중 하나 이상을 설정해 주세요.');return;}
          applyShippingSettings({methodId,handlerId,date,address:changedAddress?{...changedAddress,detailAddress:dialog.querySelector('[data-bulk-detail]').value}:null});
          paint();ordererStatus('설정한 배송 정보와 공통 출고일을 편집 목록에 적용했습니다. 아래에서 개별 수정 후 최종 저장해 주세요.');
        }catch(err){alert(err.message);}
      };
      const ordererStatus=message=>{dialog.querySelector('[data-orderer-status]').textContent=message;dialog.querySelector('[data-alert]').hidden=true;resetReview();};
      dialog.querySelector('[data-apply-memo]').onclick=()=>{
        const memo=dialog.querySelector('[data-bulk-memo]').value;
        if(!memo.trim()){alert('적용할 비고를 입력하거나 전체 비고 비우기를 사용해 주세요.');return;}
        capture();drafts.forEach(d=>d.adminMemo=memo);paint();ordererStatus('전체 비고에 적용했습니다. 아직 저장하지 않았습니다.');
      };
      dialog.querySelector('[data-clear-memo]').onclick=()=>{capture();drafts.forEach(d=>d.adminMemo='');paint();ordererStatus('전체 비고를 비웠습니다. 최종 저장 전까지 되돌릴 수 있습니다.');};
      dialog.addEventListener('click',ev=>{
        const b=ev.target.closest('[data-reset-memo]');if(!b||busy)return;
        capture();drafts.forEach((d,i)=>{if(b.dataset.resetMemo==='all'||i===Number(b.dataset.resetMemo))d.adminMemo=rows[i].adminMemoValue??'';});
        paint();ordererStatus('비고를 창을 열었을 때의 값으로 되돌렸습니다.');
      });
      dialog.querySelector('[data-direct-preview]').onclick=async()=>{
        if(busy)return;
        let selectedDate;try{selectedDate=commonDate();}catch(err){alert(err.message);return;}
        capture();resetReview();busy=true;
        const controls=[...dialog.querySelectorAll('button,input,select,textarea')].map(el=>[el,el.disabled]);
        controls.forEach(([el])=>el.disabled=true);
        try{
          const proposals=await api('/direct-delivery-preview',{orderIds:drafts.map(d=>d.orderId),preferredDeliveryDate:selectedDate||null});
          if(proposals.length!==drafts.length||drafts.some(d=>!proposals.some(p=>p.orderId===d.orderId)))throw Error('미리보기 발주 목록이 일치하지 않습니다. 다시 조회해 주세요.');
          directProposals=new Map(proposals.map(p=>[p.orderId,p]));
          const blocked=proposals.filter(p=>!p.applicable).length;
          const panel=dialog.querySelector('[data-direct-review]');
          panel.innerHTML='<h5>직배송 변경 미리보기</h5><p>현재 편집 중인 값 → 업체 기준 변경값입니다. 공통 출고일: '+e(selectedDate||'각 발주의 현재 출고일 유지')+'. 주문자·비고는 유지하며 같은 업체는 이번 조회에서 같은 담당자를 사용합니다.</p>'+drafts.map(d=>{
            const p=directProposals.get(d.orderId);
            return `<section><strong>#${d.orderId} · ${e(p.companyName||'업체 없음')}</strong><p>배송수단: ${e(method(d.deliveryMethodId)?.name)} → ${e(method(p.deliveryMethodId)?.name)}<br>담당자: ${e(options.handlers.find(h=>h.id===Number(d.deliveryHandlerId))?.name||'없음')} → ${e(p.deliveryHandlerName||'미배정')}</p><p>출고일: ${e(d.preferredDeliveryDate||'미등록')} → ${e(selectedDate||d.preferredDeliveryDate||'미등록')}</p><p>현재 주소: ${e(d.address.roadAddress)} ${e(d.address.detailAddress)}</p>${addressHtml(p)}<p>상세주소: ${e(p.detailAddress)||'없음'}</p>${p.message?`<p class="${p.applicable?'text-warning':'text-danger'}">${e(p.message)}</p>`:''}</section>`;
          }).join('')+`<p>${blocked?`${blocked}건의 업체 주소를 보완한 후 다시 조회해 주세요. 전체 적용은 보류됩니다.`:'적용하면 아래 편집 목록에 반영됩니다. 담당자 미배정 건은 저장 전에 직접 선택해 주세요.'}</p><div class="order-bulk-actions"><button type="button" class="btn btn-sm btn-primary" data-direct-apply ${blocked?'disabled':''}>미리보기 내용 적용</button><button type="button" class="btn btn-sm btn-outline-secondary" data-direct-cancel>취소</button></div>`;
          panel.hidden=false;
          panel.querySelector('[data-direct-cancel]').onclick=resetReview;
          panel.querySelector('[data-direct-apply]').onclick=()=>{
            if(busy||!directProposals||[...directProposals.values()].some(p=>!p.applicable))return;
            capture();applyDirectProposals(directProposals,selectedDate);
            paint();ordererStatus('직배송·업체 기본 주소·지역 담당자와 공통 출고일을 편집 목록에 적용했습니다. 개별 수정 또는 공통 덮어쓰기 후 최종 저장해 주세요.');
          };
          panel.scrollIntoView({block:'nearest'});
        }catch(err){alert(err.message);}finally{busy=false;controls.forEach(([el,disabled])=>el.disabled=disabled);sync();}
      };
      dialog.querySelector('[data-apply-orderer]').onclick=()=>{
        capture();const name=dialog.querySelector('[data-bulk-orderer-name]').value.trim(),phone=dialog.querySelector('[data-bulk-orderer-phone]').value.trim();
        if(!name&&!phone){alert('일괄 적용할 주문자 이름 또는 연락처를 입력해 주세요.');return;}
        drafts.forEach(d=>{if(name)d.ordererName=name;if(phone)d.ordererPhone=phone;});paint();ordererStatus(`${drafts.length}건에 입력한 주문자 정보를 적용했습니다. 변경 내용을 확인한 뒤 저장해 주세요.`);
      };
      dialog.addEventListener('click',ev=>{
        const b=ev.target.closest('[data-reset-orderer]');if(!b||busy)return;capture();
        const all=b.dataset.resetOrderer==='all';
        drafts.forEach((d,i)=>{if(all||i===Number(b.dataset.resetOrderer)){d.ordererName=rows[i].ordererName||'';d.ordererPhone=rows[i].ordererPhone||'';}});
        if(all){dialog.querySelector('[data-bulk-orderer-name]').value='';dialog.querySelector('[data-bulk-orderer-phone]').value='';}
        paint();ordererStatus(all?'전체 발주의 주문자 정보를 창을 열었을 때의 값으로 되돌렸습니다. 아직 저장하지 않았습니다.':'선택한 발주의 주문자 정보를 창을 열었을 때의 값으로 되돌렸습니다. 아직 저장하지 않았습니다.');
      });
      area.onclick=ev=>{const b=ev.target.closest('[data-search]');if(b)try{capture();postcode(a=>{capture();drafts[Number(b.dataset.search)].address=a;drafts[Number(b.dataset.search)].searched=true;paint();resetReview();});}catch(err){alert(err.message);}};
      const clearMatchedHandler=()=>{
        if(matchedBulkHandler)dialog.querySelector('[data-bulk-handler]').value='';
        matchedBulkHandler=false;
        dialog.querySelector('[data-match-status]').textContent='주소가 변경되었습니다. 새 주소로 담당자를 다시 찾거나 직접 선택해 주세요.';
      };
      dialog.querySelector('[data-address]').onclick=()=>{
        if(busy)return;
        try{postcode(a=>{
          clearMatchedHandler();changedAddress=a;
          dialog.querySelector('[data-bulk-address-preview]').innerHTML=addressHtml(a);
          dialog.querySelector('[data-bulk-detail]').value=a.detailAddress;
          dialog.querySelector('[data-bulk-detail]').disabled=false;
          syncBulkHandler();resetReview();
        });}catch(err){alert(err.message);}
      };
      dialog.querySelector('[data-clear-address]').onclick=()=>{
        if(busy)return;clearMatchedHandler();changedAddress=null;
        dialog.querySelector('[data-bulk-address-preview]').innerHTML='<p>주소를 일괄 변경하지 않습니다. 편집 목록의 현재 주소를 유지합니다.</p>';
        dialog.querySelector('[data-bulk-detail]').value='';dialog.querySelector('[data-bulk-detail]').disabled=true;
        syncBulkHandler();resetReview();
      };
      dialog.querySelector('[data-match-handler]').onclick=async()=>{
        if(busy||!changedAddress)return;
        const selected=method(dialog.querySelector('[data-bulk-method]').value);
        if(!selected||!handlerAllowed(selected)){alert('담당자를 배정할 수 있는 배송수단을 먼저 선택해 주세요.');return;}
        resetReview();busy=true;
        const controls=[...dialog.querySelectorAll('button,input,select,textarea')].map(el=>[el,el.disabled]);
        controls.forEach(([el])=>el.disabled=true);
        try{
          const result=await api('/region-handler',{deliveryMethodId:selected.id,zipCode:changedAddress.zipCode,roadAddress:changedAddress.roadAddress,doName:changedAddress.doName,siName:changedAddress.siName,guName:changedAddress.guName});
          if(result.deliveryHandlerId&&!options.handlers.some(h=>h.id===Number(result.deliveryHandlerId)))throw Error('조회된 담당자가 선택 목록에 없습니다. 창을 다시 열어 최신 담당자 목록을 확인해 주세요.');
          dialog.querySelector('[data-bulk-handler]').value=result.deliveryHandlerId?String(result.deliveryHandlerId):'';
          matchedBulkHandler=!!result.deliveryHandlerId;
          dialog.querySelector('[data-match-status]').textContent=[changedAddress.doName,changedAddress.siName,changedAddress.guName].filter(Boolean).join(' ')+' → '+(result.deliveryHandlerName||'미매칭')+'. '+result.message;
          dialog.querySelector('[data-alert]').hidden=true;
        }catch(err){alert(err.message);}finally{busy=false;controls.forEach(([el,disabled])=>el.disabled=disabled);syncBulkHandler();sync();}
      };
      dialog.querySelector('[data-save]').onclick=async()=>{
        if(busy)return;capture();const review=dialog.querySelector('[data-review]');
        try{
          drafts.forEach(d=>{if(!method(d.deliveryMethodId))throw Error('#'+d.orderId+' 배송수단을 선택해 주세요.');if(needsHandler(d)&&(!d.deliveryHandlerId||!d.preferredDeliveryDate))throw Error('#'+d.orderId+' 담당자와 출고일을 입력해 주세요.');if(canAssign(d)&&d.deliveryHandlerId&&!d.preferredDeliveryDate)throw Error('#'+d.orderId+' 담당자를 배정하려면 출고일을 입력해 주세요.');if(d.ordererName.trim().length>50||d.ordererPhone.trim().length>30)throw Error('#'+d.orderId+' 주문자 이름은 50자, 연락처는 30자 이내로 입력해 주세요.');if(method(d.deliveryMethodId).site&&(!d.ordererName.trim()||!d.ordererPhone.trim()))throw Error('#'+d.orderId+' 현장 수령자와 연락처를 입력해 주세요.');});
          if(review.hidden){review.innerHTML='<h5>저장할 변경 내용</h5>'+drafts.map((d,i)=>`<section><strong>#${d.orderId}</strong> ${e(rows[i].deliveryMethodName)} → ${e(method(d.deliveryMethodId)?.name)} / 담당자 ${e(rows[i].deliveryHandlerName||'없음')} → ${e(options.handlers.find(h=>h.id===Number(d.deliveryHandlerId))?.name||'없음')} / 출고일 ${e(rows[i].preferredDeliveryDateText||'미등록')} → ${e(d.preferredDeliveryDate||'유지')}<p>기존: ${e(actual(rows[i]).roadAddress)} ${e(actual(rows[i]).detailAddress)}</p><strong>${destination(d)}에 반영</strong>${addressHtml(d.address)}<p>상세: ${e(d.address.detailAddress)||'없음'}</p><p>주문자 이름: ${e(rows[i].ordererName||'미등록')} → ${e(d.ordererName.trim()||'미등록')}<br>주문자 연락처: ${e(rows[i].ordererPhone||'미등록')} → ${e(d.ordererPhone.trim()||'미등록')}</p><p style="white-space:pre-wrap">비고: ${e(rows[i].adminMemoValue||'없음')} → ${e(d.adminMemo||'없음')}</p></section>`).join('');review.hidden=false;dialog.querySelector('[data-save]').textContent='확인한 내용 저장';review.scrollIntoView({block:'nearest'});return;}
          busy=true;dialog.querySelectorAll('button,input,select,textarea').forEach(el=>el.disabled=true);
          const result=await api('',{items:drafts.map(payload)});window.alert(result.updatedCount+'건을 저장했습니다.');location.reload();
        }catch(err){busy=false;dialog.querySelectorAll('button,input,select,textarea').forEach(el=>el.disabled=false);dialog.querySelector('[data-bulk-detail]').disabled=!changedAddress;paint();alert(err.message);}
      };
    }catch(err){window.alert(err.message);}finally{busy=false;sync();}
  };
})();
