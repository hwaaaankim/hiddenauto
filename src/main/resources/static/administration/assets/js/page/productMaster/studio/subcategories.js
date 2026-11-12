(function (S) {
  "use strict";
  S.categoryChoices = (category, candidates, selected) => {
    const unavailable = selected.filter(id => !candidates.some(m => m.id === id));
    const notice = unavailable.length ? `<p class="pms-help">현재 배정할 수 없는 기존 계정 ${unavailable.map(id => "#" + Number(id)).join(", ")}이 있습니다. 저장하려면 이 분류의 멤버를 다시 선택해 주세요.</p>` : "";
    const choices = candidates.map(m => {
      const caption = m.company && category.kind === "PARTNER" ? m.company + " · " + m.name : m.name;
      return `<label class="pms-check"><input type="checkbox" value="${m.id}" ${selected.includes(m.id) ? "checked" : ""}>${S.e(caption)} <small>#${m.id}</small></label>`;
    });
    return `<fieldset class="pms-pane" data-category-members="${category.id}"><legend>${S.e(category.name)}</legend>
      <div class="pms-actions"><button type="button" data-select="all">전체 선택</button><button type="button" data-select="none">전체 해제</button></div>
      ${notice}<div class="pms-check-grid">${choices.join("") || "<p>중분류 관리에서 멤버를 먼저 등록해 주세요.</p>"}</div></fieldset>`;
  };
  S.bindCategoryChoices = (root, change) => {
    for (const section of S.$$("[data-category-members]", root)) {
      const boxes = S.$$('input[type="checkbox"]',section);
      const sync = () => change(Number(section.dataset.categoryMembers), boxes.filter(b => b.checked).map(b => Number(b.value)));
      section.onchange = sync;
      S.$$("[data-select]", section).forEach(b => b.onclick = () => {
        boxes.forEach(input => input.checked = b.dataset.select === "all");
        sync();
      });
    }
  };
  S.productCategoryDialog = async (id, reload) => {
    const [product,categories,candidates,assigned] = await Promise.all([
      S.request("/products/"+id), S.request("/subcategories"), S.request("/subcategories/candidates"),
      S.request("/products/"+id+"/category-members")
    ]);
    const row=product.variants.find(v=>S.group(v.groupId)?.role==="SUBCATEGORY");
    if (!row) throw Error("이 제품에는 중분류 그룹이 없습니다.");
    const selected=new Set(row.valueIds);
    const dialog=S.dialog(product.productName+" · 중분류 관리",'<div data-category-form></div>',{foot:'<button class="primary" data-save>변경 저장</button>'});
    const form=S.$("[data-category-form]",dialog);
    function draw() {
      form.innerHTML=`<div class="pms-actions"><button data-all>전체 선택</button><button data-none>전체 해제</button></div><div class="pms-check-grid">${categories.map(c=>`<label class="pms-check"><input type="checkbox" data-id="${c.id}" ${selected.has(c.id)?"checked":""}>${S.e(c.name)}</label>`).join("")}</div>${categories.filter(c=>c.kind && selected.has(c.id)).map(c=>S.categoryChoices(c,candidates.filter(m=>m.kind===c.kind && c.members.includes(m.id)),assigned[c.id]||[])).join("")}`;
      S.$("[data-all]",form).onclick=()=>{categories.forEach(c=>selected.add(c.id));draw();};
      S.$("[data-none]",form).onclick=()=>{selected.clear();draw();};
      S.$$("[data-id]",form).forEach(b=>b.onchange=()=>{if(b.checked) selected.add(Number(b.dataset.id));else selected.delete(Number(b.dataset.id));draw();});
      S.bindCategoryChoices(form,(key,ids)=>assigned[key]=ids);
    }
    draw();
    const save=S.$("[data-save]",dialog);
    save.onclick=()=>S.run(save,async()=>{
      const members=Object.fromEntries(categories.filter(c=>c.kind && selected.has(c.id)).map(c=>[c.id,assigned[c.id]||[]]));
      await S.request("/products/"+id+"/subcategories","PUT",{version:product.version,valueIds:[...selected],members});
      dialog.close();await reload();
    });
  };
  S.subcategoriesPage = async (root, showCustom = false) => {
    const categories = await S.request("/subcategories");
    const candidates = await S.request("/subcategories/candidates");
    root.innerHTML = `<section class="pms-panel"><header class="pms-panel-title"><strong>중분류 관리</strong></header><div class="pms-panel-body"><div class="pms-table-scroll"><table><thead><tr><th>중분류</th><th>연결 제품</th><th>등록 멤버</th><th>관리</th></tr></thead><tbody>${categories.map(c => `<tr><td>${S.e(c.name)}</td><td>${c.count}개</td><td>${c.kind ? c.members.length + '명' : '해당 없음'}</td><td><button data-edit="${c.id}">수정</button> <button data-remove="${c.id}" ${c.count ? 'disabled title="연결 제품이 있습니다."' : ''}>삭제</button></td></tr>`).join("")}</tbody></table></div></div></section><section class="pms-panel"><header class="pms-panel-title"><strong>제품별 중분류</strong><div class="pms-actions"><button data-kind="standard">규격 제품</button><button data-kind="custom">비규격 제품</button></div></header><div data-products></div></section>`;
    const products=S.$("[data-products]",root);
    const load = custom => S.listPage(products, custom, () => S.subcategoriesPage(root, custom));
    S.$$("[data-kind]",root).forEach(b => b.onclick=()=>S.run(b,()=>load(b.dataset.kind==="custom")));
    S.$$("[data-remove]",root).forEach(b => b.onclick=()=>S.run(b,async()=>{
      if(!await S.confirm("이 중분류와 등록된 멤버 명단을 삭제할까요?")) return;
      await S.request("/subcategories/"+b.dataset.remove,"DELETE");
      await S.catalog();
      await S.subcategoriesPage(root, showCustom);
    }));
    S.$$("[data-edit]",root).forEach(b => b.onclick=()=>{
      const c=categories.find(v=>v.id===Number(b.dataset.edit));
      let members=[...c.members];
      const options=candidates.filter(m=>m.kind===c.kind);
      for(const id of members) if(!options.some(m=>m.id===id)) options.push({id,name:"기존 배정 계정",company:""});
      const dialog=S.dialog("중분류 수정",`<label>명칭 <input maxlength="120" data-name value="${S.e(c.name)}"></label>${c.kind ? S.categoryChoices(c,options,members) : ""}`,{foot:'<button class="primary" data-save>저장</button>'});
      S.bindCategoryChoices(dialog,(_,ids)=>members=ids);
      const save=S.$("[data-save]",dialog);
      save.onclick=()=>S.run(save,async()=>{
        await S.request("/subcategories/"+c.id,"PUT",{name:S.$("[data-name]",dialog).value,version:c.version,members});
        dialog.close(); await S.catalog(); await S.subcategoriesPage(root);
      });
    });
    await load(showCustom);
  };
})(window.PMS);
