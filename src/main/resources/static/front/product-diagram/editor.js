(function (D) {
  'use strict';
  const $=s=>document.querySelector(s), container=$('#graph'), frameToken=new URLSearchParams(location.search).get('channel');
  const post=(type,data={})=>parent.postMessage({app:'hiddenbath-diagram',channel:frameToken,type,...data},location.origin);
  let opts,config,answers,fields,layout,graph,board,selected=null,history=[],cursor=-1,loading=false,tool=null,emptyRegions=[];
  function notice(s){$('#message').textContent=s||'';}
  function updateStepGuide(){
    const node=$('#step-guide');if(!node||!opts)return;
    let action,link;
    if(opts.readOnly){action='도형을 선택하면 중심 좌표·치수·주변 간격을 확인할 수 있습니다. 출력으로 종이 도면을 만들 수 있습니다.';link='view';}
    else if(opts.template){action=tool?'선택한 도구로 외곽 안에서 드래그하세요. 그리기를 마치면 기호 적용을 누르고, 이전 창에서 도형저장으로 종류를 저장하세요.':'오른쪽에서 선·삼각형 등 도구를 고르고 외곽 안에서 드래그하세요. 내부 기호는 고객이 배치할 도형의 표시가 됩니다.';link='marks';}
    else if(!board||!graph.getChildVertices(board).length){action='오른쪽 항목 아래의 ＋ 도형 이름을 누르세요. 도형 1개를 만들면 해당 항목의 개수가 1개 늘어납니다.';link='customer';}
    else if(selected){action='선택한 도형을 이동하거나 오른쪽에 위치·치수를 입력하세요. 중심 X/Y는 바탕 도면의 왼쪽 위에서 잰 좌표입니다.';link='coordinates';}
    else if(config.mode==='FILL'){action='노란 빈 공간까지 모두 채운 뒤 답변 적용을 누르세요. 도형 선택 후 남은 공간에 맞추기는 허용 치수 안에서만 동작합니다.';link='mode';}
    else{action='필요한 도형을 배치한 뒤 답변 적용을 누르세요. 빈 공간을 남겨도 되며, 확정한 개수가 이전 질문 화면에 반영됩니다.';link='customer';}
    node.innerHTML='<strong>지금 할 일</strong> '+D.esc(action)+' <a href="/front/product-diagram/help.html#'+link+'" target="_blank" rel="noopener">사용법 ↗</a>';
  }
  function sendCancel(){if(history.length>1&&!opts?.readOnly&&!confirm('편집 내용을 적용하지 않고 닫으시겠습니까?'))return;post('cancel');}
  $('#cancel').onclick=sendCancel;
  window.addEventListener('message',event=>{
    if(event.origin!==location.origin||event.source!==parent||event.data?.app!=='hiddenbath-diagram'||event.data.channel!==frameToken||event.data.type!=='init'||opts)return;
    try{init(event.data.options);}catch(e){notice(e.message);post('error',{message:e.message});}
  });
  post('ready');
  function init(input){
    opts=input;config=D.copy(input.config);opts.labels=opts.labels||config.fieldLabels||{};answers=input.answers||{};fields=input.fields||[...new Set(config.variants.map(v=>v.fieldKey))];
    const w=opts.template?(opts.sampleWidth||1000):opts.readOnly?input.layout.width:D.source(config.width,answers),h=opts.template?(opts.sampleHeight||1000):opts.readOnly?input.layout.height:D.source(config.height,answers);
    layout=opts.template?{schemaVersion:1,revision:config.revision,width:w,height:h,items:[]} : D.copy(input.layout||{schemaVersion:1,revision:config.revision,width:w,height:h,items:[]});
    // Preserve old coordinates when upstream answers change, require explicit application again.
    if(!opts.readOnly){layout.width=w;layout.height=h;layout.revision=config.revision;layout.snapshot=config;}
    $('#title').textContent=opts.title||'정면 도면 배치';$('#subtitle').textContent=opts.template?'내부 기호만 그려 주세요. 외곽 치수 규칙은 이전 단계에서 설정합니다.':`${layout.width} × ${layout.height} mm · 1mm 단위 · 좌상단 기준 · ${config.mode==='FILL'?'전체 영역 채우기':'위치 지정'}`;
    $('#apply').textContent=opts.template?'기호 적용':'답변 적용';$('#apply').hidden=!!opts.readOnly;$('#cancel').textContent=opts.readOnly?'닫기':'취소';
    if(typeof mxGraph==='undefined')throw Error('도형 도구를 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.');
    function MarkLine(){mxShape.call(this);} mxUtils.extend(MarkLine,mxShape); MarkLine.prototype.paintVertexShape=function(canvas,x,y,w,h){canvas.begin();canvas.moveTo(x,this.style.reverse==='1'?y+h:y);canvas.lineTo(x+w,this.style.reverse==='1'?y:y+h);canvas.stroke();}; mxCellRenderer.registerShape('pdLine',MarkLine);
    graph=new mxGraph(container);graph.setConnectable(false);graph.setCellsEditable(false);graph.setCellsDisconnectable(false);graph.setAllowDanglingEdges(false);graph.setDropEnabled(false);graph.setMultigraph(false);graph.setCellsCloneable(false);graph.setCellsSelectable(true);graph.setPanning(true);graph.panningHandler.useLeftButtonForPanning=false;graph.setGridEnabled(true);graph.gridSize=opts.template?10:1;graph.setHtmlLabels(false);graph.setCellsBendable(false);graph.setCellsDeletable(!opts.readOnly);graph.setCellsResizable(!opts.readOnly);graph.setCellsMovable(!opts.readOnly);
    if(graph.graphHandler){
      graph.graphHandler.guidesEnabled=true;
      graph.graphHandler.getGuideStates=()=>board?graph.getChildVertices(board).filter(c=>c!==selected).map(c=>graph.view.getState(c)).filter(Boolean):[];
      const originalPreview=graph.graphHandler.updatePreview;
      graph.graphHandler.updatePreview=function(...args){const result=originalPreview.apply(this,args);drawGuides();return result;};
    }
    graph.isCellSelectable=cell=>cell!==board && cell?.parent===board;
    graph.isCellMovable=cell=>!opts.readOnly && cell!==board && cell?.parent===board;
    graph.isCellResizable=cell=>!opts.readOnly && cell!==board && cell?.parent===board && (opts.template||['width','height'].some(axis=>variant(cell)?.[axis]?.mode==='RANGE'));
    graph.isValidDropTarget=()=>false;graph.isCellFoldable=()=>false;
    graph.convertValueToString=cell=>cell?.pdMark?.kind==='TEXT'?cell.pdMark.text:'';
    const moving=graph.moveCells;
    graph.moveCells=function(cells,dx,dy,...rest){
      if(opts.readOnly)return cells;const previous=state();loading=true;
      let result;try{result=moving.call(this,cells,Math.round(dx),Math.round(dy),...rest);for(const cell of cells)if(cell.parent===board){const g=cell.geometry.clone();g.x=Math.round(g.x);g.y=Math.round(g.y);if(!opts.template)snap(cell,g);graph.model.setGeometry(cell,g);}}finally{loading=false;}
      finish(previous);return result;
    };
    const resizing=graph.resizeCells;
    graph.resizeCells=function(cells,bounds,...rest){
      if(opts.readOnly)return cells;const previous=state();const corrected=bounds.map((b,i)=>{const n=new mxRectangle(Math.round(b.x),Math.round(b.y),Math.round(b.width),Math.round(b.height));const v=variant(cells[i]);
        if(!opts.template&&v)for(const axis of ['width','height']){const [lo,hi]=D.bounds(v[axis],answers);n[axis]=Math.max(lo,Math.min(hi,n[axis]));}return n;});
      loading=true;let result;try{result=resizing.call(this,cells,corrected,...rest);}finally{loading=false;}finish(previous);return result;
    };
    graph.getSelectionModel().addListener(mxEvent.CHANGE,()=>{selected=graph.getSelectionCell();drawSelection();drawGuides();});
    graph.view.addListener(mxEvent.SCALE_AND_TRANSLATE,drawGuides);graph.view.addListener(mxEvent.SCALE,drawGuides);
    container.addEventListener('scroll',drawGuides);
    draw();remember();palette();fit();
    new ResizeObserver(()=>{if(graph)drawGuides();}).observe(container);
    $('#undo').onclick=()=>restore(cursor-1);$('#redo').onclick=()=>restore(cursor+1);
    $('#fit').onclick=fit;$('#zoom-in').onclick=()=>zoom(1.2);$('#zoom-out').onclick=()=>zoom(1/1.2);
    $('#print').onclick=()=>{const win=window.open('','_blank');if(!win)return notice('출력 창을 열 수 없습니다. 팝업 설정을 확인해 주세요.');win.document.write('<!doctype html><html lang="ko"><meta charset="UTF-8"><title>확정 도면</title><body>'+D.svg(state(),config)+table()+'</body></html>');win.document.close();win.focus();win.print();};
    $('#apply').onclick=()=>{
      if(opts.template){const marks=state().marks;if(marks.length>100)return notice('내부 기호는 최대 100개입니다.');post('apply',{marks});return;}
      const value=state(),result=check(value);if(result.errors.length)return notice(result.errors.join(' / '));
      post('apply',{answer:{choices:[],fields:result.counts,diagram:{...value,snapshot:config}}});
    };
    document.addEventListener('keydown',event=>{if(/INPUT|TEXTAREA|SELECT/.test(event.target.tagName))return;
      if(event.key==='Escape'){event.preventDefault();if(tool){tool=null;notice('그리기 도구를 해제했습니다.');}else graph.clearSelection();}
      if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='z'){event.preventDefault();restore(cursor+(event.shiftKey?1:-1));}
      if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='y'){event.preventDefault();restore(cursor+1);}
      if(event.key==='Delete'||event.key==='Backspace'){event.preventDefault();remove();}
      if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='d'){event.preventDefault();duplicate();}
      const delta={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]}[event.key];if(delta&&!opts.readOnly&&selected){event.preventDefault();graph.moveCells([selected],delta[0]*(event.shiftKey?10:1),delta[1]*(event.shiftKey?10:1));}
    });
    let start=null;
    container.addEventListener('pointerdown',event=>{if(!opts.template||!tool||opts.readOnly)return;start=point(event);event.preventDefault();event.stopImmediatePropagation();},true);
    container.addEventListener('pointerup',event=>{if(!start||!tool)return;const end=point(event),mark={kind:tool,x1:Math.max(0,Math.min(1000,Math.round(start.x/layout.width*1000))),y1:Math.max(0,Math.min(1000,Math.round(start.y/layout.height*1000))),x2:Math.max(0,Math.min(1000,Math.round(end.x/layout.width*1000))),y2:Math.max(0,Math.min(1000,Math.round(end.y/layout.height*1000))),text:null};start=null;
      if(tool==='TEXT'){mark.text=prompt('표시할 문자 (80자 이하)','')?.slice(0,80);if(!mark.text)return;mark.x2=Math.min(1000,mark.x1+300);mark.y2=Math.min(1000,mark.y1+100);}
      if(mark.x1===mark.x2&&mark.y1===mark.y2)return;const s=state();s.marks.push(mark);if(s.marks.length>100)return notice('내부 기호는 최대 100개입니다.');applyState(s);remember();tool=null;notice('내부 기호를 추가했습니다. 선택 후 이동·크기 변경이 가능합니다.');});
  }
  function check(value,allowGaps=false){const result=D.validate(config,value,answers,fields,allowGaps);for(const f of opts.countRules||[]){const n=result.counts[f.key]||0;if(f.max!=null&&n>Number(f.max))result.errors.push((f.labels?.customer||'항목')+': 최대 '+f.max+'개까지 배치할 수 있습니다.');if(!allowGaps&&f.min!=null&&n<Number(f.min))result.errors.push((f.labels?.customer||'항목')+': 최소 '+f.min+'개를 배치해 주세요.');}return result;}
  function variant(cell){return config.variants.find(v=>v.key===cell?.pdItem?.variantKey);}
  function state(){
    if(opts.template)return {marks:graph.getChildVertices(board).map(cell=>{const g=cell.geometry,m=cell.pdMark;return {...m,x1:Math.round((m.x1<=m.x2?g.x:g.x+g.width)/layout.width*1000),y1:Math.round((m.y1<=m.y2?g.y:g.y+g.height)/layout.height*1000),x2:Math.round((m.x1<=m.x2?g.x+g.width:g.x)/layout.width*1000),y2:Math.round((m.y1<=m.y2?g.y+g.height:g.y)/layout.height*1000)};})};
    return {...layout,items:graph.getChildVertices(board).map(cell=>({id:cell.pdItem.id,variantKey:cell.pdItem.variantKey,x:Math.round(cell.geometry.x),y:Math.round(cell.geometry.y),width:Math.round(cell.geometry.width),height:Math.round(cell.geometry.height)}))};
  }
  function draw(){
    loading=true;graph.model.beginUpdate();try{graph.removeCells(graph.getChildVertices(graph.getDefaultParent()));board=graph.insertVertex(graph.getDefaultParent(),'board','',0,0,layout.width,layout.height,`shape=${opts.template&&config.variants[0]?.shape==='ELLIPSE'?'ellipse':'rectangle'};fillColor=#ffffff;strokeColor=#334155;strokeWidth=2;resizable=0;movable=0;deletable=0;selectable=0;foldable=0;`);
      if(opts.template){for(const m of opts.marks||[])markCell(m);}else for(const item of layout.items)itemCell(item);
    }finally{graph.model.endUpdate();loading=false;}selected=null;drawSelection();drawGuides();status();
  }
  function itemCell(item){const v=config.variants.find(v=>v.key===item.variantKey);const cell=graph.insertVertex(board,item.id,'',item.x,item.y,item.width,item.height,`shape=${v?.shape==='ELLIPSE'?'ellipse':'rectangle'};fillColor=#eff6ff;strokeColor=${v?'#475569':'#dc2626'};strokeWidth=1.5;rotatable=0;editable=0;recursiveResize=0;`);cell.pdItem=item;
    // Decorative children never become standalone parts or counts.
    for(const mark of v?.marks||[])addMark(cell,mark,item.width,item.height);return cell;}
  function addMark(parent,m,w,h){const x=m.x1*w/1000,y=m.y1*h/1000,x2=m.x2*w/1000,y2=m.y2*h/1000;
    if(m.kind==='LINE'){const edge=graph.insertEdge(parent,null,'',null,null,'endArrow=none;startArrow=none;strokeColor=#475569;strokeWidth=1.4;movable=0;resizable=0;selectable=0;');const g=edge.geometry.clone();g.setTerminalPoint(new mxPoint(x,y),true);g.setTerminalPoint(new mxPoint(x2,y2),false);graph.model.setGeometry(edge,g);return edge;}
    const shape={RECT:'rectangle',ELLIPSE:'ellipse',TEXT:'label',TRIANGLE_LEFT:'triangle;direction=west',TRIANGLE_RIGHT:'triangle;direction=east'}[m.kind]||'rectangle';
    return graph.insertVertex(parent,null,m.kind==='TEXT'?m.text:'',Math.min(x,x2),Math.min(y,y2),Math.max(1,Math.abs(x2-x)),Math.max(1,Math.abs(y2-y)),`shape=${shape};fillColor=none;strokeColor=${m.kind==='TEXT'?'none':'#475569'};fontColor=#334155;fontSize=${Math.max(12,Math.min(w,h)*.07)};whiteSpace=wrap;movable=0;resizable=0;selectable=0;editable=0;`);}
  function markCell(m){const shape={LINE:'pdLine',RECT:'rectangle',ELLIPSE:'ellipse',TEXT:'label',TRIANGLE_LEFT:'triangle;direction=west',TRIANGLE_RIGHT:'triangle;direction=east'}[m.kind];const cell=graph.insertVertex(board,null,m.kind==='TEXT'?m.text:'',Math.min(m.x1,m.x2)*layout.width/1000,Math.min(m.y1,m.y2)*layout.height/1000,Math.max(.1,Math.abs(m.x2-m.x1)*layout.width/1000),Math.max(.1,Math.abs(m.y2-m.y1)*layout.height/1000),`shape=${shape};reverse=${(m.x2-m.x1)*(m.y2-m.y1)<0?1:0};fillColor=none;strokeColor=${m.kind==='TEXT'?'none':'#475569'};fontSize=36;strokeWidth=2;rotatable=0;editable=0;`);cell.pdMark=m;return cell;}
  function applyState(s){if(opts.template)opts.marks=D.copy(s.marks);else layout=D.copy(s);draw();}
  function remember(){const s=JSON.stringify(state());if(history[cursor]===s)return;history=history.slice(0,cursor+1);history.push(s);if(history.length>100)history.shift();cursor=history.length-1;buttons();}
  function restore(i){if(i<0||i>=history.length||opts.readOnly)return;cursor=i;applyState(JSON.parse(history[cursor]));buttons();notice('');}
  function buttons(){$('#undo').disabled=!!opts.readOnly||cursor<=0;$('#redo').disabled=!!opts.readOnly||cursor>=history.length-1;}
  function finish(previous){
    const s=state();let errors=[];if(opts.template){for(const m of s.marks)if(Math.min(m.x1,m.x2)<0||Math.min(m.y1,m.y2)<0||Math.max(m.x1,m.x2)>1000||Math.max(m.y1,m.y2)>1000)errors.push('내부 기호는 외곽 도형 안에 그려 주세요.');}
    else errors=check(s,true).errors;
    if(errors.length){applyState(previous);notice(errors.join(' / '));return;}
    const sid=selected?.pdItem?.id;applyState(s);if(sid){selected=graph.getChildVertices(board).find(c=>c.pdItem.id===sid);graph.setSelectionCell(selected);}remember();notice('');
  }
  function snap(cell,g){if(!$('#snap')?.checked)return;const threshold=7/graph.view.scale,others=graph.getChildVertices(board).filter(c=>c!==cell).map(c=>c.geometry);let bestX=threshold,bestY=threshold,newX=g.x,newY=g.y;
    const xs=[0,layout.width/2,layout.width],ys=[0,layout.height/2,layout.height];for(const o of others){xs.push(o.x,o.x+o.width/2,o.x+o.width);ys.push(o.y,o.y+o.height/2,o.y+o.height);}
    for(const x of xs)for(const offset of [0,g.width/2,g.width]){const dx=x-g.x-offset;if(Math.abs(dx)<bestX){bestX=Math.abs(dx);newX=Math.round(g.x+dx);}}
    for(const y of ys)for(const offset of [0,g.height/2,g.height]){const dy=y-g.y-offset;if(Math.abs(dy)<bestY){bestY=Math.abs(dy);newY=Math.round(g.y+dy);}}g.x=newX;g.y=newY;
  }
  function point(event){const r=container.getBoundingClientRect(),v=graph.view;return {x:Math.round((event.clientX-r.left+container.scrollLeft)/v.scale-v.translate.x),y:Math.round((event.clientY-r.top+container.scrollTop)/v.scale-v.translate.y)};}
  function fit(){const pad=90,scale=Math.max(.0001,Math.min((container.clientWidth-pad*2)/layout.width,(container.clientHeight-pad*2)/layout.height,2));graph.view.scaleAndTranslate(scale,pad/scale,pad/scale);drawGuides();}
  function zoom(factor){graph.zoomTo(Math.max(.0001,Math.min(10,graph.view.scale*factor)));drawGuides();}
  function drawGuides(){if(!graph||!board)return;const s=graph.view.scale,t=graph.view.translate,ox=t.x*s,oy=t.y*s,w=layout.width*s,h=layout.height*s;let g=selected?.geometry;if(g&&graph.graphHandler?.currentDx!=null){g=g.clone();g.x+=Math.round(graph.graphHandler.currentDx/s);g.y+=Math.round(graph.graphHandler.currentDy/s);}
    $('#zoom').textContent=Math.round(s*100)+'%';container.style.backgroundSize=(opts.template?50:Math.max(10,Math.round(40/s)))*s+'px '+(opts.template?50:Math.max(10,Math.round(40/s)))*s+'px';container.style.backgroundPosition=`${ox}px ${oy}px`;
    const svg=$('#guides');svg.style.width=Math.max(container.clientWidth,ox+w+100)+'px';svg.style.height=Math.max(container.clientHeight,oy+h+100)+'px';
    const line=(x1,y1,x2,y2,color='#94a3b8',dash=true)=>`<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="1" ${dash?'stroke-dasharray="4 4"':''}/>`;
    const text=(x,y,v,color='#475569')=>`<text x="${x}" y="${y}" fill="${color}" font-size="12" text-anchor="middle" stroke="white" stroke-width="3" paint-order="stroke">${D.esc(v)}</text>`;
    let out=emptyRegions.slice(0,50).map(r=>`<rect x="${ox+r.x*s}" y="${oy+r.y*s}" width="${r.width*s}" height="${r.height*s}" fill="#fbbf24" fill-opacity=".16" stroke="#d97706" stroke-opacity=".45" stroke-dasharray="3 3"/>`).join('')+line(ox,oy-28,ox+w,oy-28,'#64748b',false)+text(ox+w/2,oy-36,layout.width+' mm')+line(ox-35,oy,ox-35,oy+h,'#64748b',false)+text(ox-45,oy+h/2,layout.height+' mm');
    if(g){const x=ox+g.x*s,y=oy+g.y*s,r=x+g.width*s,b=y+g.height*s,cx=x+g.width*s/2,cy=y+g.height*s/2;
      out+=line(x,oy-60,x,oy+h+45)+line(r,oy-60,r,oy+h+45)+line(ox-70,y,ox+w+65,y)+line(ox-70,b,ox+w+65,b);
      out+=line(x,oy+h+30,r,oy+h+30,'#2563eb',false)+text((x+r)/2,oy+h+47,g.width+' mm','#1d4ed8');
      out+=line(ox+w+30,y,ox+w+30,b,'#2563eb',false)+text(ox+w+53,(y+b)/2,g.height+' mm','#1d4ed8');
      out+=line(cx,oy,cx,oy+h,'#0891b2')+line(ox,cy,ox+w,cy,'#0891b2')+`<circle cx="${cx}" cy="${cy}" r="4" fill="#0891b2"/>`;
      out+=text(cx,oy-63,`중심 X ${g.x+g.width/2}`,'#0e7490')+text(ox+w+53,cy-10,`Y ${g.y+g.height/2}`,'#0e7490');
      const gaps=nearest(g);if(gaps.left>0)out+=line(ox+(g.x-gaps.left)*s,cy,x,cy,'#d97706',false)+text(x-gaps.left*s/2,cy-9,gaps.left+' mm','#b45309');if(gaps.right>0)out+=line(r,cy,r+gaps.right*s,cy,'#d97706',false)+text(r+gaps.right*s/2,cy-9,gaps.right+' mm','#b45309');
      if(gaps.top>0)out+=line(cx,y-gaps.top*s,cx,y,'#d97706',false)+text(cx+26,y-gaps.top*s/2,gaps.top+' mm','#b45309');if(gaps.bottom>0)out+=line(cx,b,cx,b+gaps.bottom*s,'#d97706',false)+text(cx+26,b+gaps.bottom*s/2,gaps.bottom+' mm','#b45309');
      for(const c of graph.getChildVertices(board)){if(c===selected)continue;const a=c.geometry;
        for(const [u,v] of [[g.x,a.x],[g.x+g.width/2,a.x+a.width/2],[g.x+g.width,a.x+a.width]])if(u===v)out+=line(ox+u*s,oy+Math.min(g.y,a.y)*s,ox+u*s,oy+Math.max(g.y+g.height,a.y+a.height)*s,'#d946ef');
        for(const [u,v] of [[g.y,a.y],[g.y+g.height/2,a.y+a.height/2],[g.y+g.height,a.y+a.height]])if(u===v)out+=line(ox+Math.min(g.x,a.x)*s,oy+u*s,ox+Math.max(g.x+g.width,a.x+a.width)*s,oy+u*s,'#d946ef');
        if(a.width===g.width)out+=text(ox+(a.x+a.width/2)*s,oy+a.y*s-8,'동일 폭','#a21caf');if(a.height===g.height)out+=text(ox+(a.x+a.width/2)*s,oy+(a.y+a.height)*s+15,'동일 높이','#a21caf');
      }
    }
    svg.innerHTML=out;
  }
  function nearest(g){const out={left:g.x,right:layout.width-g.x-g.width,top:g.y,bottom:layout.height-g.y-g.height};for(const c of graph.getChildVertices(board)){if(c===selected)continue;const a=c.geometry;
    if(a.y<g.y+g.height&&a.y+a.height>g.y){if(a.x+a.width<=g.x)out.left=Math.min(out.left,g.x-a.x-a.width);if(a.x>=g.x+g.width)out.right=Math.min(out.right,a.x-g.x-g.width);}
    if(a.x<g.x+g.width&&a.x+a.width>g.x){if(a.y+a.height<=g.y)out.top=Math.min(out.top,g.y-a.y-a.height);if(a.y>=g.y+g.height)out.bottom=Math.min(out.bottom,a.y-g.y-g.height);}}
    return out;}
  function palette(){if(opts.template){$('#palette').innerHTML='<h3>내부 기호 그리기</h3><small>도구를 선택하고 도형 안에서 드래그하세요.</small>'+Object.entries({LINE:'선',RECT:'사각형',ELLIPSE:'원·타원',TRIANGLE_LEFT:'좌향 삼각형',TRIANGLE_RIGHT:'우향 삼각형',TEXT:'문자'}).map(([k,v])=>`<button data-tool="${k}">${v}</button>`).join('');document.querySelectorAll('[data-tool]').forEach(b=>{b.disabled=!!opts.readOnly;b.onclick=()=>{tool=b.dataset.tool;graph.clearSelection();notice(b.textContent+' · 도형 안에서 드래그해 주세요.');updateStepGuide();};});return;}
    if(opts.readOnly){$('#palette').innerHTML='<h3>확정 배치 도면</h3><small>주문·실제품 사양에 저장된 원본 도면입니다. 선택하면 위치·중심점·치수를 확인할 수 있습니다.</small>';return;}
    $('#palette').innerHTML='<h3>배치할 항목</h3><label><input id="snap" type="checkbox" checked>경계·도형에 붙이기</label>'+fields.map(k=>`<div><strong>${D.esc(opts.labels?.[k]||config.variants.find(v=>v.fieldKey===k)?.fieldLabel||config.variants.find(v=>v.fieldKey===k)?.name||'항목')}</strong>${config.variants.filter(v=>v.fieldKey===k&&D.available(v,answers,fields)).map(v=>`<button class="pd-variant" data-variant="${D.esc(v.key)}" ${opts.readOnly?'disabled':''}>＋ ${D.esc(v.name)}</button>`).join('')||'<small>현재 조건에서 생성할 도형이 없습니다.</small>'}</div>`).join('');document.querySelectorAll('[data-variant]').forEach(b=>b.onclick=()=>add(b.dataset.variant));$('#snap').onchange=event=>{if(graph.graphHandler)graph.graphHandler.guidesEnabled=event.target.checked;};}
  function add(key){if(opts.readOnly)return;const v=config.variants.find(v=>v.key===key),s=state();if(s.items.length>=500)return notice('배치 도형은 최대 500개입니다.');let w,h;try{w=D.bounds(v.width,answers)[0];h=D.bounds(v.height,answers)[0];}catch(e){return notice(e.message);}
    const xs=[0,...s.items.flatMap(i=>[i.x+i.width,i.x-w]),Math.floor((s.width-w)/2)],ys=[0,...s.items.flatMap(i=>[i.y+i.height,i.y-h]),Math.floor((s.height-h)/2)];
    let found;for(const y of ys){for(const x of xs){const n={id:D.id(),variantKey:key,x,y,width:w,height:h},test={...s,items:[...s.items,n]};if(!check(test,true).errors.length){found=n;break;}}if(found)break;}
    if(!found)return notice('현재 위치에는 이 도형을 추가할 공간이 없습니다. 기존 도형의 위치·치수를 조정해 주세요.');
    s.items.push(found);applyState(s);selected=graph.getChildVertices(board).find(c=>c.pdItem.id===found.id);graph.setSelectionCell(selected);remember();notice('도형을 추가했습니다. 오른쪽에서 위치와 치수를 직접 입력할 수 있습니다.');}
  function drawSelection(){updateStepGuide();if(!selected||selected===board){$('#selection').innerHTML='<h3>도형 선택</h3><small>도형을 누르면 이곳에 위치·치수가 나옵니다. 방향키 1mm, Shift+방향키 10mm 이동.</small>';return;}
    const g=selected.geometry,v=variant(selected),gap=nearest(g),disabled=opts.readOnly?'disabled':'';
    $('#selection').innerHTML=`<h3>${D.esc(opts.template?'내부 기호':v?.name||'사용 불가 도형')}</h3><div class="pd-grid">${[['x','왼쪽 X',g.x],['y','위쪽 Y',g.y],['width','가로',g.width],['height','높이',g.height]].map(([key,label,value])=>`<label>${label} (mm)<input data-geometry="${key}" type="number" step="1" value="${value}" ${disabled} ${!opts.template&&['width','height'].includes(key)&&v?.[key]?.mode!=='RANGE'?'readonly':''}></label>`).join('')}</div>${!opts.template&&!opts.readOnly?`<small class="pd-selection-help">고정값·앞 답변 연결 축은 잠겨 있습니다. 범위로 등록한 축만 크기를 바꿀 수 있습니다.</small>`:""}<div class="pd-metric">중심 X ${g.x+g.width/2} / Y ${g.y+g.height/2} mm<br>중심에서 외곽까지 가로 ${g.width/2} / 세로 ${g.height/2} mm<br>왼쪽 간격 ${gap.left} / 오른쪽 ${gap.right}<br>위 간격 ${gap.top} / 아래 ${gap.bottom} mm</div><small>간격은 수평·수직 투영이 겹치는 주변 도형의 외접 경계까지입니다. 원·타원의 곡선 최단거리와는 구별됩니다.</small><div class="pd-actions"><button id="duplicate" ${disabled}>복제</button><button id="delete" ${disabled}>삭제</button>${!opts.template?'<button id="remaining" title="선택 도형을 빈 영역에 맞춥니다. 등록한 최소·최대 치수에 맞을 때만 적용합니다." '+disabled+'>남은 공간에 맞추기</button>':''}</div>`;
    document.querySelectorAll('[data-geometry]').forEach(i=>i.onchange=()=>{const n=Number(i.value);if(!D.integer(n)){drawSelection();return notice('소수 없이 정수 mm를 입력해 주세요.');}const previous=state(),g=selected.geometry.clone();g[i.dataset.geometry]=n;graph.model.setGeometry(selected,g);finish(previous);});$('#duplicate').onclick=duplicate;$('#delete').onclick=remove;$('#remaining')?.addEventListener('click',remaining);
  }
  function remove(){if(!selected||opts.readOnly)return;graph.removeCells([selected]);selected=null;const s=state();applyState(s);remember();notice('도형을 삭제했습니다.');}
  function duplicate(){if(!selected||opts.readOnly)return;if(opts.template){const s=state(),old=s.marks[graph.getChildVertices(board).indexOf(selected)],m={...old,x1:old.x1+10,y1:old.y1+10,x2:old.x2+10,y2:old.y2+10};if(m.x2>1000||m.y2>1000)return notice('복제할 공간이 없습니다.');s.marks.push(m);applyState(s);remember();}else add(selected.pdItem.variantKey);}
  function remaining(){if(!selected||opts.template)return;const previous=state(),sid=selected.pdItem.id,v=variant(selected),regions=D.gaps({...previous,items:previous.items.filter(x=>x.id!==sid)}),g=selected.geometry;
    for(const r of regions.filter(r=>r.x<=g.x+g.width/2&&r.x+r.width>=g.x+g.width/2&&r.y<=g.y+g.height/2&&r.y+r.height>=g.y+g.height/2)){
      const next=D.copy(previous),i=next.items.find(x=>x.id===sid);Object.assign(i,r);if(!check(next,true).errors.length){applyState(next);remember();return notice('허용 치수 안에서 남은 영역에 맞췄습니다.');}}
    notice(v.name+': 남은 영역이 허용 치수를 만족하지 않습니다.');}
  function status(){if(opts.template){$('#status').textContent='내부 기호 '+graph.getChildVertices(board).length+'개 · 고객 치수에 비례해 표시됩니다. 기호 적용 후 이전 창에서 도형저장을 눌러 주세요.';updateStepGuide();return;}
    const result=opts.readOnly?{errors:[],counts:state().items.reduce((a,i)=>{const v=config.variants.find(v=>v.key===i.variantKey);if(v)a[v.fieldKey]=(a[v.fieldKey]||0)+1;return a;},{})}:check(state());$('#counts').innerHTML='<h3>배치 개수</h3>'+fields.map(k=>`<div class="pd-count"><span>${D.esc(opts.labels?.[k]||config.variants.find(v=>v.fieldKey===k)?.fieldLabel||config.variants.find(v=>v.fieldKey===k)?.name||'항목')}</span><strong>${result.counts[k]||0}개</strong></div>`).join('');
    $('#status').textContent=result.errors.length?result.errors.join(' / '):opts.readOnly?'저장된 확정 도면 · 조회 전용':'배치 검사를 통과했습니다. 답변 적용을 눌러 확정해 주세요.';$('#status').classList.toggle('pd-error',!!result.errors.length);
    emptyRegions=!opts.readOnly&&config.mode==='FILL'?D.gaps(state()).sort((a,b)=>b.width*b.height-a.width*a.height):[];
    if(emptyRegions.length)$('#status').textContent+=' · 남은 영역 '+emptyRegions.length+'곳: '+emptyRegions.slice(0,3).map(r=>r.width+' × '+r.height+' mm').join(' / ');
    buttons();drawGuides();updateStepGuide();}
  function table(){if(opts.template)return '';return '<table border="1" cellpadding="8"><tr><th>번호</th><th>종류</th><th>가로×높이 mm</th><th>왼쪽 X / 위쪽 Y mm</th><th>중심 X / Y mm</th></tr>'+state().items.map((i,n)=>`<tr><td>${n+1}</td><td>${D.esc(config.variants.find(v=>v.key===i.variantKey)?.name)}</td><td>${i.width} × ${i.height}</td><td>${i.x} / ${i.y}</td><td>${i.x+i.width/2} / ${i.y+i.height/2}</td></tr>`).join('')+'</table>';}
})(ProductDiagram);
