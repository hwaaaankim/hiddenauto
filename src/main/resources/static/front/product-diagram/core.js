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
  function sizeStep(v) {
    const n=v?.sizeStep??1;
    if(!integer(n)||n<1||n>1000000)throw Error('도형 크기 조정 단위는 1~1,000,000mm의 정수로 입력해 주세요.');
    return n;
  }
  function bounds(d, answers, step=1) {
    if (d?.mode === 'FIXED') {
      if(!integer(d.value)||d.value<1||d.value>1000000)throw Error('고정 치수는 1~1,000,000mm의 정수로 입력해 주세요.');
      return [d.value,d.value];
    }
    if (d?.mode === 'REFERENCE') {const n=source(d.source,answers);return [n,n];}
    if (d?.mode === 'RANGE') {
      sizeStep({sizeStep:step});
      if(!integer(d.min)||!integer(d.max)||d.min<0||d.max>1000000)throw Error('도형의 최소·최대 치수를 정수 mm로 입력해 주세요.');
      const low=d.min+(d.minInclusive?0:1),high=d.max-(d.maxInclusive?0:1);
      if(low<1||low>high)throw Error('도형 치수 범위가 비어 있거나 허용 크기를 벗어났습니다.');
      const lo=Math.ceil(low/step)*step,hi=Math.floor(high/step)*step;
      if(lo>hi)throw Error('도형 치수 범위 안에 크기 조정 단위의 배수가 없습니다. 최소·최대 또는 조정 단위를 확인해 주세요.');
      return [lo,hi];
    }
    throw Error('치수 방식을 설정해 주세요.');
  }
  // Quantize the total movement from the drag's starting geometry, before any pixel/grid rounding.
  function resize(v, answers, old, requested) {
    const step=sizeStep(v),next={x:old.x,y:old.y,width:old.width,height:old.height};
    for(const axis of ['width','height']) {
      const d=v[axis],pos=axis==='width'?'x':'y',[lo,hi]=bounds(d,answers,step);
      if(d.mode!=='RANGE'){next[axis]=lo;continue;}
      const delta=requested[axis]-old[axis];
      const ticks=Math.floor((Math.abs(delta)+1e-8)/step)*Math.sign(delta);
      let value=old[axis]+ticks*step;
      // An old draft may no longer lie on the new unit after a setting change.
      if(value%step!==0)value=Math.round(value/step)*step;
      next[axis]=Math.max(lo,Math.min(hi,value));
      if(Math.abs(requested[pos]-old[pos])>1e-7)next[pos]=old[pos]+old[axis]-next[axis];
    }
    return next;
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
      if(![x.x,x.y,x.width,x.height].every(integer)||x.width<1||x.height<1||Math.abs(x.x)>1000000||Math.abs(x.y)>1000000)errors.push(`${v.name}: 정수 치수·위치 좌표(±1,000,000mm)를 확인해 주세요.`);
      if(config.mode==='FILL'&&(x.x<0||x.y<0||x.x+x.width>w||x.y+x.height>h))errors.push(`${v.name}: 도형이 도면 밖으로 나갑니다.`);
      try {const step=sizeStep(v);for(const axis of ['width','height']){const d=v[axis],[lo,hi]=bounds(d,answers,step);if(x[axis]<lo||x[axis]>hi||d.mode==='RANGE'&&x[axis]%step!==0)errors.push(`${v.name}: ${axis==='width'?'가로':'높이'} 허용범위 ${lo}~${hi}mm${d.mode==='RANGE'?' · '+step+'mm 배수':''}를 확인해 주세요.`);}}catch(e){errors.push(e.message);}
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
  function markFont(m,w,h){const width=Math.abs(m.x2-m.x1)*w/1000,height=Math.abs(m.y2-m.y1)*h/1000,units=[...String(m.text||'')].reduce((sum,c)=>sum+(c.charCodeAt(0)>127?1:.6),0);return Math.max(1,Math.min(Math.max(10,Math.min(w,h)*.07),width/Math.max(1,units),height*.8));}
  function marksSvg(marks,w,h){return (marks||[]).map(m=>{const x=m.x1*w/1000,y=m.y1*h/1000,x2=m.x2*w/1000,y2=m.y2*h/1000,ww=x2-x,hh=y2-y,base='fill="none" stroke="currentColor" vector-effect="non-scaling-stroke"';
    if(m.kind==='LINE')return `<path d="M${x} ${y}L${x2} ${y2}" ${base}/>`;
    if(m.kind==='TEXT')return `<text x="${(x+x2)/2}" y="${(y+y2)/2}" text-anchor="middle" dominant-baseline="central" font-size="${markFont(m,w,h)}" fill="currentColor">${esc(m.text)}</text>`;
    if(m.kind==='RECT')return `<rect x="${Math.min(x,x2)}" y="${Math.min(y,y2)}" width="${Math.abs(ww)}" height="${Math.abs(hh)}" ${base}/>`;
    if(m.kind==='ELLIPSE')return `<ellipse cx="${(x+x2)/2}" cy="${(y+y2)/2}" rx="${Math.abs(ww)/2}" ry="${Math.abs(hh)/2}" ${base}/>`;
    const left=Math.min(x,x2),right=Math.max(x,x2),top=Math.min(y,y2),bottom=Math.max(y,y2),points=m.kind==='TRIANGLE_LEFT'?`${right},${top} ${left},${(top+bottom)/2} ${right},${bottom}`:`${left},${top} ${right},${(top+bottom)/2} ${left},${bottom}`;return `<polygon points="${points}" ${base}/>`;
  }).join('');}
  let measurementSerial=0;
  function measure(layout,{scale=1,ox=0,oy=0,unit=1,showParts=true,showRuler=true}={}) {
    const u=unit,s=scale,w=layout.width*s,h=layout.height*s,midX=layout.width/2,midY=layout.height/2;
    const items=layout.items||[],bounds={minX:Math.min(0,...items.map(i=>i.x)),minY:Math.min(0,...items.map(i=>i.y)),maxX:Math.max(layout.width,...items.map(i=>i.x+i.width)),maxY:Math.max(layout.height,...items.map(i=>i.y+i.height))};
    const left=ox+bounds.minX*s,top=oy+bounds.minY*s,right=ox+bounds.maxX*s,bottom=oy+bounds.maxY*s;
    const marker='pd-arrow-'+(++measurementSerial),font=11*u;
    const line=(x1,y1,x2,y2,color='#b1bfce',arrow=false)=>`<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="${u}" ${arrow?`marker-start="url(#${marker})" marker-end="url(#${marker})"`:''}/>`;
    const label=(x,y,value,rotate=false,color='#62758c')=>`<text x="${x}" y="${y}" ${rotate?`transform="rotate(-90 ${x} ${y})"`:''} text-anchor="middle" font-family="sans-serif" font-size="${font}" fill="${color}" stroke="white" stroke-width="${3*u}" paint-order="stroke">${esc(value)}</text>`;
    let markup=`<defs><marker id="${marker}" viewBox="0 0 6 6" refX="6" refY="3" markerWidth="${6*u}" markerHeight="${6*u}" markerUnits="userSpaceOnUse" orient="auto-start-reverse"><path d="M0 0 6 3 0 6" fill="none" stroke="#7890a9" stroke-width="1"/></marker></defs>`;
    function horizontal(x1,x2,y,value,color){markup+=line(x1,oy+h,x1,y+4*u)+line(x2,oy+h,x2,y+4*u)+line(x1,y,x2,y,color,true)+label((x1+x2)/2,y-5*u,value,false,color);}
    function vertical(y1,y2,x,value,color){markup+=line(ox+w,y1,x+4*u,y1)+line(ox+w,y2,x+4*u,y2)+line(x,y1,x,y2,color,true)+label(x+14*u,(y1+y2)/2,value,true,color);}
    markup+=line(ox,top-57*u,ox+w,top-57*u,'#7890a9',true)+line(ox,oy,ox,top-63*u)+line(ox+w,oy,ox+w,top-63*u)+label(ox+w/2,top-64*u,layout.width+' mm');
    markup+=line(left-78*u,oy,left-78*u,oy+h,'#7890a9',true)+line(ox,oy,left-84*u,oy)+line(ox,oy+h,left-84*u,oy+h)+label(left-87*u,oy+h/2,layout.height+' mm',true);
    function spans(axis){const result=new Map();for(const item of layout.items||[]){const start=axis==='x'?item.x:item.y,length=axis==='x'?item.width:item.height,key=start+':'+length;if(!result.has(key))result.set(key,{start,length});}return [...result.values()].filter(x=>x.length>0).sort((a,b)=>a.start-b.start||a.length-b.length);}
    function dimensions(axis){const lanes=[];for(const part of spans(axis)){const start=part.start*s,end=(part.start+part.length)*s,center=(start+end)/2,half=Math.max((end-start)/2,String(part.length).length*font*.32+16*u);let lane=0;while(lanes[lane]!=null&&lanes[lane]>center-half+.1)lane++;lanes[lane]=center+half;if(axis==='x')horizontal(ox+start,ox+end,bottom+(33+lane*23)*u,(Math.round(part.length*1e6)/1e6)+' mm','#95a5b8');else vertical(oy+start,oy+end,right+(36+lane*23)*u,(Math.round(part.length*1e6)/1e6)+' mm','#95a5b8');}return lanes.length;}
    const xLanes=showParts?dimensions('x'):0,yLanes=showParts?dimensions('y'):0;
    let major=100,minor=10;
    while(major*s/u<28)major*=10;
    if(minor*s/u<3)minor=major;
    while(Math.max(bounds.maxX-bounds.minX,bounds.maxY-bounds.minY)/minor>2500)minor*=10;
    if(showRuler){
      for(const axis of ['x','y']){const low=axis==='x'?bounds.minX-midX:midY-bounds.maxY,high=axis==='x'?bounds.maxX-midX:midY-bounds.minY;let lastLabel=-Infinity;
        for(let value=Math.ceil(low/minor)*minor;value<=high;value+=minor){const big=value%major===0,x=axis==='x'?ox+(value+midX)*s:left-8*u,y=axis==='y'?oy+(midY-value)*s:top-8*u;markup+=`<circle cx="${x}" cy="${y}" r="${(big?1.8:.7)*u}" fill="${big?'#8397ae':'#c1ccd9'}"/>`;
          const position=axis==='x'?x:-y;if(big&&position-lastLabel>=28*u){markup+=axis==='x'?label(x,top-21*u,value):label(left-32*u,y+3*u,value);lastLabel=position;}
        }
      }
      // Origin labels live on the outer rulers. No coordinate text or axes cover the furniture.
      markup+=label(right+10*u,top-21*u,'+X')+label(left-32*u,top-39*u,'+Y');
    }
    return {markup,extent:{left:110*u-bounds.minX*s,top:85*u-bounds.minY*s,right:Math.max(70,58+yLanes*23)*u+(bounds.maxX-layout.width)*s,bottom:Math.max(54,40+xLanes*23)*u+(bounds.maxY-layout.height)*s},major,minor,markerId:marker,bounds};
  }
  function svg(layout,config=layout?.snapshot,labels=true){
    if(!layout||!config)return '';const w=layout.width,h=layout.height,u=Math.max(w,h)/900,guide=measure(layout,{unit:u,showParts:labels}),p=guide.extent,variants=new Map((config.variants||[]).map(v=>[v.key,v]));
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${-p.left} ${-p.top} ${w+p.left+p.right} ${h+p.top+p.bottom}" role="img" aria-label="중앙 원점 정면 배치 도면 ${w} × ${h} mm"><rect x="0" y="0" width="${w}" height="${h}" fill="white" stroke="#334155" stroke-width="1.5" vector-effect="non-scaling-stroke"/>${layout.items.map(x=>{const v=variants.get(x.variantKey);if(!v)return '';return `<g transform="translate(${x.x} ${x.y})" color="#334155">${v.shape==='ELLIPSE'?`<ellipse cx="${x.width/2}" cy="${x.height/2}" rx="${x.width/2}" ry="${x.height/2}"`:`<rect x="0" y="0" width="${x.width}" height="${x.height}"`} fill="#f1f5f9" stroke="#334155" vector-effect="non-scaling-stroke"/>${marksSvg(v.marks,x.width,x.height)}</g>`;}).join('')}${guide.markup}</svg>`;
  }
  return {copy,integer,esc,id,source,sizeStep,bounds,resize,matches,available,overlap,validate,gaps,markFont,marksSvg,measure,svg};
});
