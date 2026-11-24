(function (root, factory) {
  const api = factory(); if (typeof module === 'object' && module.exports) module.exports = api; else root.ProductDiagram = api;
})(typeof globalThis === 'object' ? globalThis : this, function () {
  'use strict';
  const copy = x => JSON.parse(JSON.stringify(x)), integer = n => Number.isSafeInteger(n), esc = x => String(x ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const id = () => 'D' + (globalThis.crypto?.randomUUID?.() || Math.random().toString(36).slice(2)).replace(/-/g,'');
  function source(ref, answers) {
    const v = answers?.[ref?.questionKey]?.fields?.[ref?.fieldKey];
    if (v == null || v === '' || !integer(Number(v)) || Number(v)<1 || Number(v)>1000000) throw Error('앞 질문의 가로·높이는 1~1,000,000mm 정수로 입력해 주세요.');
    return Number(v);
  }
  function bounds(d, answers) {
    if (d?.mode === 'FIXED') return [d.value,d.value];
    if (d?.mode === 'REFERENCE') {const n=source(d.source,answers);return [n,n];}
    if (d?.mode === 'RANGE') return [d.min+(d.minInclusive?0:1),d.max-(d.maxInclusive?0:1)];
    throw Error('치수 방식을 설정해 주세요.');
  }
  function matches(c, answers) {
    const a=answers?.[c.groupKey]; if (!a) return false;
    let v=c.fieldKey ? a.fields?.[c.fieldKey] : (a.choices||[]), present=Array.isArray(v)?v.length>0:v!=null && v!=='';
    if(c.operator==='PRESENT')return present; if(c.operator==='ABSENT')return !present;
    if(Array.isArray(v)){const yes=v.some(k=>(c.choiceKeys||[]).includes(k));return c.operator==='NE'?!yes:yes;}
    if(!present)return false;v=Number(v);const lo=Number(c.lower),hi=Number(c.upper);
    return {EQ:()=>v===lo,NE:()=>v!==lo,GT:()=>v>lo,GE:()=>v>=lo,LT:()=>v<lo,LE:()=>v<=lo,RANGE:()=> (c.lowerInclusive?v>=lo:v>lo)&&(c.upperInclusive?v<=hi:v<hi)}[c.operator]?.() || false;
  }
  const available=(v,answers,fields)=> (!fields || fields.includes(v.fieldKey)) && (v.conditions||[]).every(c=>matches(c,answers));
  function overlap(a,as,b,bs) {
    if(a.x+a.width<=b.x||b.x+b.width<=a.x||a.y+a.height<=b.y||b.y+b.height<=a.y)return false;
    if(as==='RECT'&&bs==='RECT')return true;
    function er(e,r){const ax=e.width/2,ay=e.height/2,cx=e.x+ax,cy=e.y+ay,dx=cx-Math.max(r.x,Math.min(cx,r.x+r.width)),dy=cy-Math.max(r.y,Math.min(cy,r.y+r.height));return dx*dx/(ax*ax)+dy*dy/(ay*ay)<1-1e-12;}
    if(as==='RECT')return er(b,a);if(bs==='RECT')return er(a,b);
    const ax=a.width/2,ay=a.height/2,bx=b.width/2,by=b.height/2,dx=Math.abs(a.x+ax-b.x-bx),dy=Math.abs(a.y+ay-b.y-by);
    if(dx*dx/(ax*ax)+dy*dy/(ay*ay)<=1)return true;
    const projected=l=>{const x=dx/(1+l*bx*bx/(ax*ax)),y=dy/(1+l*by*by/(ay*ay));return x*x/(ax*ax)+y*y/(ay*ay);};
    let lo=0,hi=1;while(projected(hi)>1)hi*=2;for(let i=0;i<100;i++){const m=(lo+hi)/2;if(projected(m)>1)lo=m;else hi=m;}
    const x=dx/(1+hi*bx*bx/(ax*ax)),y=dy/(1+hi*by*by/(ay*ay));return (x-dx)**2/(bx*bx)+(y-dy)**2/(by*by)<1-1e-12;
  }
  function validate(config,layout,answers,fields,allowGaps=false) {
    const errors=[], variants=new Map((config?.variants||[]).map(v=>[v.key,v])),counts=Object.fromEntries((fields||[...new Set((config?.variants||[]).map(v=>v.fieldKey))]).map(k=>[k,0]));
    if(!layout || layout.schemaVersion!==1)return {errors:['도면 배치를 완료해 주세요.'],counts};
    if(layout.revision!==config.revision)errors.push('도형 설정이 변경되었습니다. 배치를 다시 확인해 주세요.');
    let w,h;try {w=source(config.width,answers);h=source(config.height,answers);}catch(e){return {errors:[e.message],counts};}
    if(layout.width!==w||layout.height!==h)errors.push('앞 질문의 가로·높이가 바뀌었습니다. 도면을 다시 확인해 주세요.');
    if(!Array.isArray(layout.items)||layout.items.length>500)return {errors:['배치 도형은 최대 500개입니다.'],counts};
    const ids=new Set(); let area=0;
    layout.items.forEach((x,i)=>{
      const v=variants.get(x.variantKey);if(!v || !available(v,answers,fields)){errors.push(`${i+1}번 도형은 현재 분기에서 사용할 수 없습니다.`);return;}
      if(!/^[A-Za-z0-9_-]{1,80}$/.test(x.id)||ids.has(x.id))errors.push('도형 식별값이 중복되거나 올바르지 않습니다.');ids.add(x.id);
      if(![x.x,x.y,x.width,x.height].every(integer)||x.width<1||x.height<1||x.x<0||x.y<0||x.x+x.width>w||x.y+x.height>h)errors.push(`${v.name}: 도면 밖으로 나가거나 정수 치수가 아닙니다.`);
      try {for(const axis of ['width','height']){const [lo,hi]=bounds(v[axis],answers);if(x[axis]<lo||x[axis]>hi)errors.push(`${v.name}: ${axis==='width'?'가로':'높이'} 허용범위 ${lo}~${hi}mm를 확인해 주세요.`);}}catch(e){errors.push(e.message);}
      for(let j=0;j<i;j++){const ov=variants.get(layout.items[j].variantKey);if(ov&&overlap(x,v.shape,layout.items[j],ov.shape))errors.push(`${i+1}번 ${v.name}: ${j+1}번 도형과 겹칩니다.`);}
      if(config.mode==='FILL'&&v.shape!=='RECT')errors.push('꽉 채우기는 사각형만 사용할 수 있습니다.');
      area+=x.width*x.height;counts[v.fieldKey]=(counts[v.fieldKey]||0)+1;
    });
    if(!allowGaps&&config.mode==='FILL'&&area!==w*h)errors.push(`빈 공간이 남아 있습니다. 채움 ${Math.min(100,100*area/(w*h)).toFixed(1)}%`);
    return {errors:[...new Set(errors)],counts,area,total:w*h};
  }
  function gaps(layout){
    let regions=[{x:0,y:0,width:layout.width,height:layout.height}];
    for(const p of layout.items){const next=[];for(const r of regions){const x=Math.max(r.x,p.x),y=Math.max(r.y,p.y),x2=Math.min(r.x+r.width,p.x+p.width),y2=Math.min(r.y+r.height,p.y+p.height);if(x>=x2||y>=y2){next.push(r);continue;}
      if(y>r.y)next.push({...r,height:y-r.y});if(y2<r.y+r.height)next.push({...r,y:y2,height:r.y+r.height-y2});if(x>r.x)next.push({x:r.x,y,width:x-r.x,height:y2-y});if(x2<r.x+r.width)next.push({x:x2,y,width:r.x+r.width-x2,height:y2-y});}regions=next;}return regions;
  }
  function marksSvg(marks,w,h){return (marks||[]).map(m=>{const x=m.x1*w/1000,y=m.y1*h/1000,x2=m.x2*w/1000,y2=m.y2*h/1000,ww=x2-x,hh=y2-y,base='fill="none" stroke="currentColor" vector-effect="non-scaling-stroke"';
    if(m.kind==='LINE')return `<path d="M${x} ${y}L${x2} ${y2}" ${base}/>`;
    if(m.kind==='TEXT')return `<text x="${x}" y="${y}" font-size="${Math.max(10,Math.min(w,h)*.07)}" fill="currentColor">${esc(m.text)}</text>`;
    if(m.kind==='RECT')return `<rect x="${Math.min(x,x2)}" y="${Math.min(y,y2)}" width="${Math.abs(ww)}" height="${Math.abs(hh)}" ${base}/>`;
    if(m.kind==='ELLIPSE')return `<ellipse cx="${(x+x2)/2}" cy="${(y+y2)/2}" rx="${Math.abs(ww)/2}" ry="${Math.abs(hh)/2}" ${base}/>`;
    const points=m.kind==='TRIANGLE_LEFT'?`${x2},${y} ${x},${(y+y2)/2} ${x2},${y2}`:`${x},${y} ${x2},${(y+y2)/2} ${x},${y2}`;return `<polygon points="${points}" ${base}/>`;
  }).join('');}
  function svg(layout,config=layout?.snapshot,labels=true){if(!layout||!config)return '';const w=layout.width,h=layout.height,pad=Math.max(w,h)*.08,variants=new Map((config.variants||[]).map(v=>[v.key,v]));
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${-pad} ${-pad} ${w+pad*2} ${h+pad*2}" role="img" aria-label="정면 배치 도면 ${w} × ${h} mm"><rect x="0" y="0" width="${w}" height="${h}" fill="white" stroke="#334155" stroke-width="1.5" vector-effect="non-scaling-stroke"/>${layout.items.map((x,i)=>{const v=variants.get(x.variantKey);if(!v)return '';return `<g transform="translate(${x.x} ${x.y})" color="#334155">${v.shape==='ELLIPSE'?`<ellipse cx="${x.width/2}" cy="${x.height/2}" rx="${x.width/2}" ry="${x.height/2}"`:`<rect x="0" y="0" width="${x.width}" height="${x.height}"`} fill="#f1f5f9" stroke="#334155" vector-effect="non-scaling-stroke"/>${marksSvg(v.marks,x.width,x.height)}${labels?`<text x="${x.width/2}" y="${Math.min(x.height*.2,Math.max(w,h)*.035)}" text-anchor="middle" font-size="${Math.max(w,h)*.02}" fill="#475569">${i+1}</text>`:''}</g>`;}).join('')}<text x="${w/2}" y="${-pad*.35}" text-anchor="middle" font-family="sans-serif" font-size="${pad*.36}">${w} mm × ${h} mm</text></svg>`;
  }
  return {copy,integer,esc,id,source,bounds,matches,available,overlap,validate,gaps,marksSvg,svg};
});
