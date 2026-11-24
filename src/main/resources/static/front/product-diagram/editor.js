(function (D) {
  'use strict';
  const $=s=>document.querySelector(s), container=$('#graph'), frameToken=new URLSearchParams(location.search).get('channel');
  const post=(type,data={})=>parent.postMessage({app:'hiddenbath-diagram',channel:frameToken,type,...data},location.origin);
  let opts,config,answers,fields,layout,graph,board,selected=null,history=[],cursor=-1,loading=false,tool=null,emptyRegions=[],measurementCache=null;
  const editable=()=>!opts?.readOnly;
  const cellKey=cell=>cell?.pdItem?.id||cell?.pdId;
  const selectionKeys=()=>graph.getSelectionCells().map(cellKey);
  const coordinate=g=>({x:g.x+g.width/2-layout.width/2,y:layout.height/2-g.y-g.height/2});
  function notice(s){$('#message').textContent=s||'';}
  function updateStepGuide(){
    const node=$('#step-guide');if(!node||!opts)return;
    let action,link;
    if(opts.readOnly){action='도형을 선택하면 중심 좌표·치수·주변 간격을 확인할 수 있습니다. 출력으로 종이 도면을 만들 수 있습니다.';link='view';}
    else if(opts.template){action=tool?'선택한 도구로 외곽 안에서 드래그하세요. 그리기를 마치면 기호 적용을 누르고, 이전 창에서 도형저장으로 종류를 저장하세요.':'오른쪽에서 선·삼각형 등 도구를 고르고 외곽 안에서 드래그하세요. 선택 도구 또는 오른쪽 기호 목록에서 선택해 이동·크기 변경·삭제할 수 있습니다.';link='marks';}
    else if(!board||!graph.getChildVertices(board).length){action='오른쪽 항목 아래의 ＋ 도형 이름을 누르세요. 도형 1개를 만들면 해당 항목의 개수가 1개 늘어납니다.';link='customer';}
    else if(selected){action='선택한 도형을 이동하거나 오른쪽에 위치·치수를 입력하세요. 중심 X/Y는 바탕 도면 중앙 기준이며 오른쪽 +X, 위쪽 +Y입니다.';link='coordinates';}
    else if(config.mode==='FILL'){action='노란 빈 공간까지 모두 채운 뒤 확정을 누르세요. 도형 선택 후 남은 공간에 맞추기는 허용 치수 안에서만 동작합니다.';link='mode';}
    else{action='필요한 도형을 배치한 뒤 확정을 누르세요. 빈 공간을 남겨도 되며, 확정한 개수가 이전 질문 화면에 반영됩니다.';link='customer';}
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
    opts=input;if(opts.template)opts.marks=(opts.marks||[]).map(m=>({...m,_editorId:D.id()}));config=D.copy(input.config);opts.labels=opts.labels||config.fieldLabels||{};answers=input.answers||{};fields=input.fields||[...new Set(config.variants.map(v=>v.fieldKey))];
    const w=opts.template?(opts.sampleWidth||1000):opts.readOnly?input.layout.width:D.source(config.width,answers),h=opts.template?(opts.sampleHeight||1000):opts.readOnly?input.layout.height:D.source(config.height,answers);
    layout=opts.template?{schemaVersion:1,revision:config.revision,width:w,height:h,items:[]} : D.copy(input.layout||{schemaVersion:1,revision:config.revision,width:w,height:h,items:[]});
    // Preserve old coordinates when upstream answers change, require explicit application again.
    if(!opts.readOnly){layout.width=w;layout.height=h;layout.revision=config.revision;layout.snapshot=config;}
    $('#title').textContent=opts.title||'정면 도면 배치';$('#subtitle').textContent=opts.template?'내부 기호만 그려 주세요. 외곽 치수 규칙은 이전 단계에서 설정합니다. 휠 확대·축소 / Shift+휠 가로 이동.':`${layout.width} × ${layout.height} mm · 1mm 단위 · 중앙 (0,0) · 오른쪽 +X / 위쪽 +Y · ${config.mode==='FILL'?'전체 영역 채우기':'위치 지정'}`;
    $('#apply').textContent=opts.template?'기호 적용':'확정';$('#apply').hidden=!!opts.readOnly;$('#cancel').textContent=opts.readOnly?'닫기':'취소';
    if(typeof mxGraph==='undefined')throw Error('도형 도구를 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.');
    function MarkLine(){mxShape.call(this);} mxUtils.extend(MarkLine,mxShape); MarkLine.prototype.svgStrokeTolerance=14;MarkLine.prototype.paintVertexShape=function(canvas,x,y,w,h){canvas.begin();canvas.moveTo(x,this.style.reverse==='1'?y+h:y);canvas.lineTo(x+w,this.style.reverse==='1'?y:y+h);canvas.stroke();}; mxCellRenderer.registerShape('pdLine',MarkLine);
    graph=new mxGraph(container);graph.setExtendParents(false);graph.setExtendParentsOnAdd(false);graph.setConstrainChildren(false);graph.setConnectable(false);graph.setCellsEditable(false);graph.setCellsDisconnectable(false);graph.setAllowDanglingEdges(false);graph.setDropEnabled(false);graph.setMultigraph(false);graph.setCellsCloneable(false);graph.setCellsSelectable(true);graph.setPanning(true);graph.panningHandler.useLeftButtonForPanning=false;graph.setGridEnabled(true);graph.gridSize=1;graph.setHtmlLabels(false);graph.setCellsBendable(false);graph.setCellsDeletable(!opts.readOnly);graph.setCellsResizable(!opts.readOnly);graph.setCellsMovable(!opts.readOnly);
    if(graph.graphHandler){
      graph.graphHandler.guidesEnabled=true;
      graph.graphHandler.isPropagateSelectionCell=()=>false;
      graph.graphHandler.getGuideStates=()=>board?graph.getChildVertices(board).filter(c=>!graph.isCellSelected(c)).map(c=>graph.view.getState(c)).filter(Boolean):[];
      const originalPreview=graph.graphHandler.updatePreview;
      graph.graphHandler.updatePreview=function(...args){const result=originalPreview.apply(this,args);drawGuides();return result;};
    }
    graph.isCellSelectable=cell=>cell!==board && cell?.parent===board;
    graph.isCellMovable=cell=>editable() && cell!==board && cell?.parent===board;
    graph.isCellResizable=cell=>editable() && cell!==board && cell?.parent===board && (opts.template||['width','height'].some(axis=>variant(cell)?.[axis]?.mode==='RANGE'));
    graph.isCellDeletable=cell=>editable()&&cell?.parent===board;
    const updateEvent=graph.updateMouseEvent;
    graph.updateMouseEvent=function(event,name){updateEvent.call(this,event,name);let cell=event.getCell();while(cell&&cell!==board&&cell.parent!==board)cell=cell.parent;if(cell&&cell!==board)event.state=graph.view.getState(cell);return event;};
    graph.isValidDropTarget=()=>false;graph.isCellFoldable=()=>false;
    graph.convertValueToString=cell=>cell?.pdMark?.kind==='TEXT'?cell.pdMark.text:'';
    const moving=graph.moveCells;
    graph.moveCells=function(cells,dx,dy,...rest){
      if(!editable())return cells;const previous=state();loading=true;
      let result;try{result=moving.call(this,cells,Math.round(dx),Math.round(dy),...rest);const raw=cells.filter(c=>c.parent===board).map(cell=>{const g=cell.geometry.clone();g.x=Math.round(g.x);g.y=Math.round(g.y);return {cell,g};});if(raw.length){const left=Math.min(...raw.map(x=>x.g.x)),top=Math.min(...raw.map(x=>x.g.y)),right=Math.max(...raw.map(x=>x.g.x+x.g.width)),bottom=Math.max(...raw.map(x=>x.g.y+x.g.height)),group=new mxRectangle(left,top,right-left,bottom-top);snap(null,group);for(const {cell,g} of raw){const next=g.clone();next.x+=group.x-left;next.y+=group.y-top;graph.model.setGeometry(cell,next);}if(!opts.template&&check(state(),true).errors.length)for(const {cell,g} of raw)graph.model.setGeometry(cell,g);}}finally{loading=false;}
      finish(previous);return result;
    };
    const resizing=graph.resizeCells;
    graph.resizeCells=function(cells,bounds,...rest){
      if(!editable())return cells;const previous=state();const corrected=bounds.map((b,i)=>{const n=new mxRectangle(Math.round(b.x),Math.round(b.y),Math.round(b.width),Math.round(b.height));const v=variant(cells[i]);
        if(!opts.template&&v)for(const axis of ['width','height']){const [lo,hi]=D.bounds(v[axis],answers);n[axis]=Math.max(lo,Math.min(hi,n[axis]));}return n;});
      loading=true;let result;try{result=resizing.call(this,cells,corrected,...rest);}finally{loading=false;}finish(previous);return result;
    };
    graph.getSelectionModel().addListener(mxEvent.CHANGE,()=>{if(loading)return;selected=graph.getSelectionCell();drawSelection();drawGuides();renderObjects();});
    graph.view.addListener(mxEvent.SCALE_AND_TRANSLATE,drawGuides);graph.view.addListener(mxEvent.SCALE,drawGuides);
    container.addEventListener('wheel',event=>{if(event.shiftKey||event.altKey)return;event.preventDefault();const delta=event.deltaY*(event.deltaMode===1?16:event.deltaMode===2?container.clientHeight:1);zoomAt(Math.exp(Math.max(-.16,Math.min(.16,-delta*.001))),event.clientX,event.clientY);},{passive:false});
    for(const id of ['show-grid','show-rulers','show-dimensions'])$('#'+id).onchange=drawGuides;
    draw();remember();palette();fit();
    new ResizeObserver(()=>{if(graph)drawGuides();}).observe(container);
    $('#select-tool').onclick=()=>chooseTool(null);$('#toolbar-delete').onclick=remove;$('#toolbar-duplicate').onclick=duplicate;
    $('#undo').onclick=()=>restore(cursor-1);$('#redo').onclick=()=>restore(cursor+1);
    $('#fit').onclick=fit;$('#zoom-in').onclick=()=>zoom(1.2);$('#zoom-out').onclick=()=>zoom(1/1.2);
    $('#print').onclick=()=>{const win=window.open('','_blank');if(!win)return notice('출력 창을 열 수 없습니다. 팝업 설정을 확인해 주세요.');win.document.write('<!doctype html><html lang="ko"><meta charset="UTF-8"><title>확정 도면</title><body>'+D.svg(state(),config)+table()+'</body></html>');win.document.close();win.focus();win.print();};
    $('#apply').onclick=()=>{
      if(opts.template){const marks=state().marks;if(marks.some(m=>[m.x1,m.y1,m.x2,m.y2].some(n=>!D.integer(n)||n<0||n>1000)))return notice('기호는 바탕 도형 안에 놓아 주세요.');if(marks.length>100)return notice('내부 기호는 최대 100개입니다.');const cleaned=marks.map(({_editorId,...m})=>m);post('apply',{marks:cleaned});return;}
      const value=state(),result=check(value);if(result.errors.length)return notice(result.errors.join(' / '));
      post('apply',{answer:{choices:[],fields:result.counts,diagram:{...value,snapshot:config}}});
    };
    document.addEventListener('keydown',event=>{if(/INPUT|TEXTAREA|SELECT/.test(event.target.tagName))return;
      if(event.key==='Escape'){event.preventDefault();if(tool){chooseTool(null);notice('선택 도구로 전환했습니다.');}else graph.clearSelection();}
      if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='a'){event.preventDefault();graph.setSelectionCells(graph.getChildVertices(board));}
      if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='z'){event.preventDefault();restore(cursor+(event.shiftKey?1:-1));}
      if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='y'){event.preventDefault();restore(cursor+1);}
      if(event.key==='Delete'||event.key==='Backspace'){event.preventDefault();remove();}
      if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='d'){event.preventDefault();duplicate();}
      const delta={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,-1],ArrowDown:[0,1]}[event.key];if(delta&&editable()&&selected){event.preventDefault();graph.moveCells(graph.getSelectionCells(),delta[0]*(event.shiftKey?10:1),delta[1]*(event.shiftKey?10:1));}
    });
    let start=null;
    container.addEventListener('pointerdown',event=>{if(!opts.template||!tool||!editable()||event.button>0)return;start=point(event);try{container.setPointerCapture(event.pointerId);}catch{}event.preventDefault();event.stopImmediatePropagation();},true);
    container.addEventListener('pointercancel',()=>{start=null;});
    container.addEventListener('pointerup',event=>{if(!start||!tool)return;const end=point(event),mark={kind:tool,_editorId:D.id(),x1:Math.max(0,Math.min(1000,Math.round(start.x/layout.width*1000))),y1:Math.max(0,Math.min(1000,Math.round(start.y/layout.height*1000))),x2:Math.max(0,Math.min(1000,Math.round(end.x/layout.width*1000))),y2:Math.max(0,Math.min(1000,Math.round(end.y/layout.height*1000))),text:null};start=null;
      if(tool==='TEXT'){mark.text=prompt('표시할 문자 (80자 이하)','')?.slice(0,80);if(!mark.text)return;mark.x2=Math.min(1000,mark.x1+300);mark.y2=Math.min(1000,mark.y1+100);}
      if(mark.x1===mark.x2&&mark.y1===mark.y2)return;if(tool!=='LINE'&&(mark.x1===mark.x2||mark.y1===mark.y2))return notice('가로·세로가 있는 영역을 드래그해 주세요.');const s=state();s.marks.push(mark);if(s.marks.length>100)return notice('내부 기호는 최대 100개입니다.');applyState(s,[mark._editorId]);remember();chooseTool(null);notice('기호를 선택했습니다. 조절점을 드래그하거나 오른쪽에서 위치·치수를 바꾸세요.');});
  }
  function check(value,allowGaps=false){const result=D.validate(config,value,answers,fields,allowGaps);for(const f of opts.countRules||[]){const n=result.counts[f.key]||0;if(f.max!=null&&n>Number(f.max))result.errors.push((f.labels?.customer||'항목')+': 최대 '+f.max+'개까지 배치할 수 있습니다.');if(!allowGaps&&f.min!=null&&n<Number(f.min))result.errors.push((f.labels?.customer||'항목')+': 최소 '+f.min+'개를 배치해 주세요.');}return result;}
  function variant(cell){return config.variants.find(v=>v.key===cell?.pdItem?.variantKey);}
  function state(){
    if(opts.template)return {marks:graph.getChildVertices(board).map(cell=>{const g=cell.geometry,m=cell.pdMark;return {...m,_editorId:cell.pdId,x1:Math.round((m.x1<=m.x2?g.x:g.x+g.width)/layout.width*1000),y1:Math.round((m.y1<=m.y2?g.y:g.y+g.height)/layout.height*1000),x2:Math.round((m.x1<=m.x2?g.x+g.width:g.x)/layout.width*1000),y2:Math.round((m.y1<=m.y2?g.y+g.height:g.y)/layout.height*1000)};})};
    return {...layout,items:graph.getChildVertices(board).map(cell=>({id:cell.pdItem.id,variantKey:cell.pdItem.variantKey,x:Math.round(cell.geometry.x),y:Math.round(cell.geometry.y),width:Math.round(cell.geometry.width),height:Math.round(cell.geometry.height)}))};
  }
  function draw(){
    loading=true;graph.model.beginUpdate();try{graph.clearSelection();for(const old of graph.getChildVertices(graph.getDefaultParent()))graph.model.remove(old);board=graph.insertVertex(graph.getDefaultParent(),'board','',-layout.width/2,-layout.height/2,layout.width,layout.height,`shape=${opts.template&&config.variants[0]?.shape==='ELLIPSE'?'ellipse':'rectangle'};fillColor=#ffffff;strokeColor=#334155;strokeWidth=2;resizable=0;movable=0;deletable=0;selectable=0;foldable=0;`);
      if(opts.template){for(const m of opts.marks||[])markCell(m);}else for(const item of layout.items)itemCell(item);
    }finally{graph.model.endUpdate();loading=false;}selected=null;drawSelection();drawGuides();status();renderObjects();
  }
  function itemCell(item){const v=config.variants.find(v=>v.key===item.variantKey);const cell=graph.insertVertex(board,item.id,'',item.x,item.y,item.width,item.height,`shape=${v?.shape==='ELLIPSE'?'ellipse':'rectangle'};fillColor=#eff6ff;strokeColor=${v?'#475569':'#dc2626'};strokeWidth=1.5;rotatable=0;editable=0;recursiveResize=0;`);cell.pdItem=item;
    // Decorative children never become standalone parts or counts.
    for(const mark of v?.marks||[])addMark(cell,mark,item.width,item.height);return cell;}
  function addMark(parent,m,w,h){const x=m.x1*w/1000,y=m.y1*h/1000,x2=m.x2*w/1000,y2=m.y2*h/1000;
    if(m.kind==='LINE'){const edge=graph.insertEdge(parent,null,'',null,null,'endArrow=none;startArrow=none;strokeColor=#475569;strokeWidth=1.4;movable=0;resizable=0;selectable=0;');const g=edge.geometry.clone();g.setTerminalPoint(new mxPoint(x,y),true);g.setTerminalPoint(new mxPoint(x2,y2),false);graph.model.setGeometry(edge,g);return edge;}
    const shape={RECT:'rectangle',ELLIPSE:'ellipse',TEXT:'label',TRIANGLE_LEFT:'triangle;direction=west',TRIANGLE_RIGHT:'triangle;direction=east'}[m.kind]||'rectangle';
    return graph.insertVertex(parent,null,m.kind==='TEXT'?m.text:'',Math.min(x,x2),Math.min(y,y2),Math.max(1,Math.abs(x2-x)),Math.max(1,Math.abs(y2-y)),`shape=${shape};fillColor=none;strokeColor=${m.kind==='TEXT'?'none':'#475569'};fontColor=#334155;fontSize=${Math.max(12,Math.min(w,h)*.07)};whiteSpace=wrap;movable=0;resizable=0;selectable=0;editable=0;`);}
  function markCell(m){const shape={LINE:'pdLine',RECT:'rectangle',ELLIPSE:'ellipse',TEXT:'label',TRIANGLE_LEFT:'triangle;direction=west',TRIANGLE_RIGHT:'triangle;direction=east'}[m.kind];const cell=graph.insertVertex(board,null,m.kind==='TEXT'?m.text:'',Math.min(m.x1,m.x2)*layout.width/1000,Math.min(m.y1,m.y2)*layout.height/1000,Math.max(.1,Math.abs(m.x2-m.x1)*layout.width/1000),Math.max(.1,Math.abs(m.y2-m.y1)*layout.height/1000),`shape=${shape};reverse=${(m.x2-m.x1)*(m.y2-m.y1)<0?1:0};fillColor=#ffffff;fillOpacity=0;pointerEvents=1;strokeColor=${m.kind==='TEXT'?'none':'#475569'};fontSize=36;strokeWidth=2;rotatable=0;editable=0;`);cell.pdMark=m;cell.pdId=m._editorId||D.id();return cell;}
  function applyState(s,keys=[]){if(opts.template)opts.marks=D.copy(s.marks);else layout=D.copy(s);draw();const cells=graph.getChildVertices(board).filter(c=>keys.includes(cellKey(c)));graph.setSelectionCells(cells);selected=graph.getSelectionCell();drawSelection();renderObjects();drawGuides();}
  function remember(){const s=JSON.stringify(state());if(history[cursor]===s)return;history=history.slice(0,cursor+1);history.push(s);if(history.length>100)history.shift();cursor=history.length-1;buttons();}
  function restore(i){if(i<0||i>=history.length||!editable())return;cursor=i;applyState(JSON.parse(history[cursor]));buttons();notice('');}
  function buttons(){const edit=editable();$('#undo').disabled=!edit||cursor<=0;$('#redo').disabled=!edit||cursor>=history.length-1;$('#toolbar-delete').disabled=!edit||!graph.getSelectionCount();$('#toolbar-duplicate').disabled=!edit||!graph.getSelectionCount();$('#select-tool').setAttribute('aria-pressed',String(!tool));$('#apply').disabled=!!opts.readOnly;}
  function finish(previous){
    const next=state(),keys=selectionKeys();let errors=[];
    if(opts.template){for(const m of next.marks)if(Math.min(m.x1,m.x2)<0||Math.min(m.y1,m.y2)<0||Math.max(m.x1,m.x2)>1000||Math.max(m.y1,m.y2)>1000)errors.push('내부 기호는 외곽 도형 안에 놓아 주세요.');}
    else errors=check(next,true).errors;
    if(errors.length){applyState(previous,keys);notice(errors.join(' / '));return;}
    applyState(next,keys);remember();notice('');
  }
  function snap(cell,g){if(!$('#snap')?.checked)return;const threshold=7/graph.view.scale,others=graph.getChildVertices(board).filter(c=>c!==cell&&!graph.isCellSelected(c)).map(c=>c.geometry);let bestX=threshold,bestY=threshold,newX=g.x,newY=g.y;
    const xs=[0,layout.width/2,layout.width],ys=[0,layout.height/2,layout.height];for(const o of others){xs.push(o.x,o.x+o.width/2,o.x+o.width);ys.push(o.y,o.y+o.height/2,o.y+o.height);}
    for(const x of xs)for(const offset of [0,g.width/2,g.width]){const dx=x-g.x-offset;if(Math.abs(dx)<bestX){bestX=Math.abs(dx);newX=Math.round(g.x+dx);}}
    for(const y of ys)for(const offset of [0,g.height/2,g.height]){const dy=y-g.y-offset;if(Math.abs(dy)<bestY){bestY=Math.abs(dy);newY=Math.round(g.y+dy);}}g.x=newX;g.y=newY;
  }
  function point(event){const r=container.getBoundingClientRect(),v=graph.view;return {x:Math.round((event.clientX-r.left+container.scrollLeft)/v.scale-v.translate.x+layout.width/2),y:Math.round((event.clientY-r.top+container.scrollTop)/v.scale-v.translate.y+layout.height/2)};}
  function fit(){const drawing=stateForMeasurements();let scale=.5,m,left,right,top,bottom,bounds;for(let pass=0;pass<3;pass++){m=D.measure(drawing,{scale});bounds=m.bounds;left=m.extent.left+bounds.minX*scale;right=m.extent.right-(bounds.maxX-layout.width)*scale;top=m.extent.top+bounds.minY*scale;bottom=m.extent.bottom-(bounds.maxY-layout.height)*scale;scale=Math.max(.0001,Math.min((container.clientWidth-left-right-30)/(bounds.maxX-bounds.minX),(container.clientHeight-top-bottom-30)/(bounds.maxY-bounds.minY),2));}const x=left+Math.max(0,(container.clientWidth-left-right-(bounds.maxX-bounds.minX)*scale)/2)-bounds.minX*scale,y=top+Math.max(0,(container.clientHeight-top-bottom-(bounds.maxY-bounds.minY)*scale)/2)-bounds.minY*scale;graph.view.scaleAndTranslate(scale,layout.width/2+x/scale,layout.height/2+y/scale);drawGuides();}
  function zoom(factor){const rect=container.getBoundingClientRect();zoomAt(factor,rect.left+container.clientWidth/2,rect.top+container.clientHeight/2);}
  function zoomAt(factor,clientX,clientY){const rect=container.getBoundingClientRect(),old=graph.view.scale,next=Math.max(.0001,Math.min(10,old*factor)),sx=container.scrollLeft,sy=container.scrollTop,px=clientX-rect.left+sx,py=clientY-rect.top+sy,worldX=px/old-graph.view.translate.x,worldY=py/old-graph.view.translate.y,padding=D.measure(stateForMeasurements(),{scale:next}).extent;let tx=px/next-worldX,ty=py/next-worldY;const dx=Math.max(0,padding.left+10-(tx-layout.width/2)*next),dy=Math.max(0,padding.top+10-(ty-layout.height/2)*next);tx+=dx/next;ty+=dy/next;graph.view.scaleAndTranslate(next,tx,ty);container.scrollLeft=sx+dx;container.scrollTop=sy+dy;}
  function stateForMeasurements(){return opts.template?{width:layout.width,height:layout.height,items:selected?[{x:Math.round(selected.geometry.x),y:Math.round(selected.geometry.y),width:Math.round(selected.geometry.width),height:Math.round(selected.geometry.height)}]:[]}:state();}
  function drawGuides(){if(!graph||!board)return;const s=graph.view.scale,t=graph.view.translate,ox=(t.x-layout.width/2)*s,oy=(t.y-layout.height/2)*s,w=layout.width*s,h=layout.height*s;
    $('#zoom').textContent=Math.round(s*100)+'%';let minor=10,major=100;while(minor*s<6)minor*=5;while(major*s<35)major*=10;container.style.backgroundSize=`${major*s}px ${major*s}px,${major*s}px ${major*s}px,${minor*s}px ${minor*s}px,${minor*s}px ${minor*s}px`;container.style.backgroundPosition=`${ox}px ${oy}px`;container.classList.toggle('pd-no-grid',!$('#show-grid').checked);
    const drawing=stateForMeasurements(),measureOptions={scale:s,ox,oy,showParts:$('#show-dimensions').checked,showRuler:$('#show-rulers').checked},key=JSON.stringify([layout.width,layout.height,measureOptions,drawing.items.map(i=>[i.x,i.y,i.width,i.height])]);if(measurementCache?.key!==key)measurementCache={key,value:D.measure(drawing,measureOptions)};const m=measurementCache.value;let out=m.markup;
    out+=emptyRegions.slice(0,50).map(r=>`<rect x="${ox+r.x*s}" y="${oy+r.y*s}" width="${r.width*s}" height="${r.height*s}" fill="#fbbf24" fill-opacity=".13" stroke="#d97706" stroke-opacity=".35" stroke-dasharray="3 3"/>`).join('');
    const line=(x1,y1,x2,y2,color='#c1ccd9',dash=false,arrow=false)=>`<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="1" ${dash?'stroke-dasharray="4 4"':''} ${arrow?`marker-start="url(#${m.markerId})" marker-end="url(#${m.markerId})"`:''}/>`;
    const text=(x,y,value,color='#60758c')=>`<text x="${x}" y="${y}" fill="${color}" font-size="11" text-anchor="middle" stroke="white" stroke-width="3" paint-order="stroke">${D.esc(value)}</text>`;
    // Every coordinate/dimension label stays outside the complete board, including gap labels.
    let g=selected?.geometry;if(g&&graph.graphHandler?.currentDx!=null){g=g.clone();g.x+=Math.round(graph.graphHandler.currentDx/s);g.y+=Math.round(graph.graphHandler.currentDy/s);}
    if(g){const x=ox+g.x*s,r=x+g.width*s,y=oy+g.y*s,b=y+g.height*s,c=coordinate(g),gap=nearest(g),bottom=oy+h+m.extent.bottom+12,right=ox+w+m.extent.right+12,outerLeft=ox+m.bounds.minX*s,outerTop=oy+m.bounds.minY*s;
      out+=line(x,oy-36,x,oy+h+15)+line(r,oy-36,r,oy+h+15)+line(ox-15,y,ox+w+20,y)+line(ox-15,b,ox+w+20,b);
      out+=text((x+r)/2,outerTop-39,`중심 X ${c.x} mm`,'#0e7490')+text(outerLeft-45,(y+b)/2,`Y ${c.y}`,'#0e7490');
      if(gap.left>0){out+=line(x-gap.left*s,bottom,x,bottom,'#d6a769',false,true)+text(x-gap.left*s/2,bottom-5,'간격 '+gap.left+' mm','#ac7b3b');}
      if(gap.right>0){out+=line(r,bottom,r+gap.right*s,bottom,'#d6a769',false,true)+text(r+gap.right*s/2,bottom-5,'간격 '+gap.right+' mm','#ac7b3b');}
      if(gap.top>0)out+=line(right,y-gap.top*s,right,y,'#d6a769',false,true)+text(right+32,y-gap.top*s/2,'간격 '+gap.top,'#ac7b3b');
      if(gap.bottom>0)out+=line(right,b,right,b+gap.bottom*s,'#d6a769',false,true)+text(right+32,b+gap.bottom*s/2,'간격 '+gap.bottom,'#ac7b3b');
      for(const cell of graph.getChildVertices(board)){if(cell===selected)continue;const a=cell.geometry;for(const [u,v] of [[g.x,a.x],[g.x+g.width/2,a.x+a.width/2],[g.x+g.width,a.x+a.width]])if(u===v)out+=line(ox+u*s,oy+Math.min(g.y,a.y)*s,ox+u*s,oy+Math.max(g.y+g.height,a.y+a.height)*s,'#d946ef',true);for(const [u,v] of [[g.y,a.y],[g.y+g.height/2,a.y+a.height/2],[g.y+g.height,a.y+a.height]])if(u===v)out+=line(ox+Math.min(g.x,a.x)*s,oy+u*s,ox+Math.max(g.x+g.width,a.x+a.width)*s,oy+u*s,'#d946ef',true);}
    }
    const svg=$('#guides');svg.style.width=Math.max(container.clientWidth,ox+w+m.extent.right+(g?110:25))+'px';svg.style.height=Math.max(container.clientHeight,oy+h+m.extent.bottom+(g?45:25))+'px';svg.innerHTML=out;
  }
  function nearest(g){const out={left:g.x>=layout.width?g.x-layout.width:g.x>=0?g.x:null,right:g.x+g.width<=0?-g.x-g.width:g.x+g.width<=layout.width?layout.width-g.x-g.width:null,top:g.y>=layout.height?g.y-layout.height:g.y>=0?g.y:null,bottom:g.y+g.height<=0?-g.y-g.height:g.y+g.height<=layout.height?layout.height-g.y-g.height:null};for(const c of graph.getChildVertices(board)){if(c===selected)continue;const a=c.geometry;
    if(a.y<g.y+g.height&&a.y+a.height>g.y){if(a.x+a.width<=g.x)out.left=Math.min(out.left??Infinity,g.x-a.x-a.width);if(a.x>=g.x+g.width)out.right=Math.min(out.right??Infinity,a.x-g.x-g.width);}
    if(a.x<g.x+g.width&&a.x+a.width>g.x){if(a.y+a.height<=g.y)out.top=Math.min(out.top??Infinity,g.y-a.y-a.height);if(a.y>=g.y+g.height)out.bottom=Math.min(out.bottom??Infinity,a.y-g.y-g.height);}}
    return out;}
  function palette(){if(opts.template){$('#palette').innerHTML='<h3>내부 기호 도구</h3><small>한 번 그린 뒤 선택 도구로 자동 전환합니다. Shift+클릭은 여러 개 선택입니다.</small><label><input id="snap" type="checkbox" checked>경계·기호에 붙이기</label>'+Object.entries({LINE:'선',RECT:'사각형',ELLIPSE:'원·타원',TRIANGLE_LEFT:'좌향 삼각형',TRIANGLE_RIGHT:'우향 삼각형',TEXT:'문자'}).map(([k,v])=>`<button data-tool="${k}">${v}</button>`).join('');document.querySelectorAll('[data-tool]').forEach(b=>{b.disabled=!!opts.readOnly;b.onclick=()=>{chooseTool(b.dataset.tool);graph.clearSelection();notice(b.textContent+' · 도형 안에서 드래그해 주세요.');};});bindSnap();return;}
    if(opts.readOnly){$('#palette').innerHTML='<h3>확정 배치 도면</h3><small>주문·실제품 사양에 저장된 원본 도면입니다. 선택하면 위치·중심점·치수를 확인할 수 있습니다.</small>';return;}
    $('#palette').innerHTML='<h3>배치할 항목</h3><label><input id="snap" type="checkbox" checked>경계·도형에 붙이기</label>'+fields.map(k=>`<div><strong>${D.esc(opts.labels?.[k]||config.variants.find(v=>v.fieldKey===k)?.fieldLabel||config.variants.find(v=>v.fieldKey===k)?.name||'항목')}</strong>${config.variants.filter(v=>v.fieldKey===k&&D.available(v,answers,fields)).map(v=>`<button class="pd-variant" data-variant="${D.esc(v.key)}" ${opts.readOnly?'disabled':''}>＋ ${D.esc(v.name)}</button>`).join('')||'<small>현재 조건에서 생성할 도형이 없습니다.</small>'}</div>`).join('');document.querySelectorAll('[data-variant]').forEach(b=>b.onclick=()=>add(b.dataset.variant));bindSnap();}
  function bindSnap(){if($('#snap'))$('#snap').onchange=event=>{if(graph.graphHandler)graph.graphHandler.guidesEnabled=event.target.checked;};}
  function chooseTool(value){tool=value;document.querySelectorAll('[data-tool]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.tool===tool)));container.classList.toggle('pd-drawing',!!tool);buttons();updateStepGuide();}
  function add(key){if(!editable())return;const v=config.variants.find(v=>v.key===key),s=state();if(s.items.length>=500)return notice('배치 도형은 최대 500개입니다.');let w,h;try{w=D.bounds(v.width,answers)[0];h=D.bounds(v.height,answers)[0];}catch(e){return notice(e.message);}
    const xs=[0,...s.items.flatMap(i=>[i.x+i.width,i.x-w]),Math.floor((s.width-w)/2)],ys=[0,...s.items.flatMap(i=>[i.y+i.height,i.y-h]),Math.floor((s.height-h)/2)];
    let found;for(const y of ys){for(const x of xs){const n={id:D.id(),variantKey:key,x,y,width:w,height:h},test={...s,items:[...s.items,n]};if(!check(test,true).errors.length){found=n;break;}}if(found)break;}
    if(!found)return notice(config.mode==='FILL'?'도형을 놓을 공간이 없습니다. 기존 도형의 위치·크기를 바꾸거나 삭제한 뒤 추가하세요.':'겹치지 않는 위치에 도형을 놓을 수 없습니다. 기존 도형을 옮긴 뒤 추가하세요.');
    s.items.push(found);applyState(s);selected=graph.getChildVertices(board).find(c=>c.pdItem.id===found.id);graph.setSelectionCell(selected);remember();notice('도형을 추가했습니다. 오른쪽에서 위치와 치수를 직접 입력할 수 있습니다.');}
  function renderObjects(){const node=$('#objects');if(!node||!graph||!board)return;const cells=graph.getChildVertices(board),keys=new Set(selectionKeys());node.innerHTML='<h3>'+ (opts.template?'내부 기호 목록':'배치 도형 목록')+'</h3><small>목록에서도 선택할 수 있습니다. Shift+클릭으로 여러 개를 선택하세요.</small><div class="pd-object-list">'+cells.map((cell,i)=>{const g=cell.geometry,name=opts.template?({LINE:'선',RECT:'사각형',ELLIPSE:'원·타원',TEXT:'문자',TRIANGLE_LEFT:'좌향 삼각형',TRIANGLE_RIGHT:'우향 삼각형'}[cell.pdMark.kind]):variant(cell)?.name||'사용 불가 도형';return `<button type="button" data-object="${i}" aria-pressed="${keys.has(cellKey(cell))}" title="${D.esc(name)} 선택"><span>${i+1}. ${D.esc(name)}</span><small>${Math.round(g.width)} × ${Math.round(g.height)} mm</small></button>`;}).join('')+'</div>';
    node.querySelectorAll('[data-object]').forEach(b=>b.onclick=event=>{chooseTool(null);const cell=cells[Number(b.dataset.object)],selection=event.shiftKey?graph.getSelectionCells().filter(c=>c!==cell):[];if(!event.shiftKey||!graph.isCellSelected(cell))selection.push(cell);graph.setSelectionCells(selection);});
  }
  function drawSelection(){updateStepGuide();buttons();const cells=graph.getSelectionCells();if(!selected||selected===board){$('#selection').innerHTML='<h3>선택·편집</h3><small>선택 도구로 도형을 누르거나 아래 목록에서 고르세요. 방향키 1mm, Shift+방향키 10mm, Delete 삭제, Ctrl+D 복제.</small>';return;}
    const g=selected.geometry,v=variant(selected),gap=nearest(g),center=coordinate(g),disabled=editable()?'':'disabled',multi=cells.length>1;
    $('#selection').innerHTML=`<h3>${multi?cells.length+'개 선택':D.esc(opts.template?'내부 기호':v?.name||'사용 불가 도형')}</h3><div class="pd-grid">${[['x','중심 X',center.x],['y','중심 Y',center.y],['width','가로',g.width],['height','높이',g.height]].map(([key,label,value])=>`<label>${label} (mm)<input data-geometry="${key}" type="number" step="1" value="${value}" ${disabled} ${['width','height'].includes(key)&&(multi||!opts.template&&v?.[key]?.mode!=='RANGE')?'readonly':''}></label>`).join('')}</div><small class="pd-selection-help">도면 중앙 (0,0) · 오른쪽 +X / 위쪽 +Y. 실제 위치·치수는 1mm 단위입니다.${opts.template?' 내부 기호는 고객 치수에 비례해 표시됩니다.':' 고정값·앞 답변 연결 축은 잠기며, 범위 축만 크기를 바꿀 수 있습니다.'}</small><div class="pd-metric">중심에서 외곽까지 가로 ${g.width/2} / 세로 ${g.height/2} mm<br>왼쪽 간격 ${gap.left??'—'} / 오른쪽 ${gap.right??'—'}<br>위 간격 ${gap.top??'—'} / 아래 ${gap.bottom??'—'} mm${graph.getChildVertices(board).some(c=>c!==selected&&c.geometry.width===g.width)?'<br>같은 폭의 도형 있음':''}${graph.getChildVertices(board).some(c=>c!==selected&&c.geometry.height===g.height)?'<br>같은 높이의 도형 있음':''}</div>${opts.template&&selected.pdMark?.kind==='LINE'&&!multi?'<button type="button" id="line-endpoints">선의 시작점·끝점 입력</button>':''}${opts.template&&selected.pdMark?.kind==='TEXT'&&!multi?'<button type="button" id="edit-mark-text">문자 수정</button>':''}<div class="pd-actions"><button id="duplicate" ${disabled}>복제</button><button id="delete" ${disabled}>삭제</button>${!opts.template?'<button id="remaining" '+disabled+'>남은 공간에 맞추기</button>':''}</div>${multi?'<div class="pd-align-tools">'+[['left','왼쪽'],['centerX','가로 중앙'],['right','오른쪽'],['top','위쪽'],['centerY','세로 중앙'],['bottom','아래쪽']].map(([key,name])=>'<button type="button" data-align="'+key+'" '+disabled+'>'+name+' 정렬</button>').join('')+'</div>':''}${opts.template?'<div class="pd-actions"><button type="button" data-layer="up" '+disabled+'>앞으로</button><button type="button" data-layer="down" '+disabled+'>뒤로</button></div>':''}`;
    document.querySelectorAll('[data-geometry]').forEach(input=>input.onchange=()=>{if(!editable())return;const n=Number(input.value),key=input.dataset.geometry;if(!Number.isFinite(n))return notice('유효한 숫자를 입력해 주세요.');const target=key==='x'?n+layout.width/2-g.width/2:key==='y'?layout.height/2-n-g.height/2:n;if(!D.integer(target)) {drawSelection();return notice('실제 위치·치수는 1mm 간격이어야 합니다. 홀수 치수의 중심은 0.5mm로 표시될 수 있습니다.');}if(key==='x'||key==='y'){graph.moveCells(cells,key==='x'?target-g.x:0,key==='y'?target-g.y:0);}else{const previous=state(),next=selected.geometry.clone();next[key]=target;graph.model.setGeometry(selected,next);finish(previous);}});
    $('#duplicate').onclick=duplicate;$('#delete').onclick=remove;$('#remaining')?.addEventListener('click',remaining);
    $('#line-endpoints')?.addEventListener('click',()=>{if(!editable())return;const old=state(),mark=old.marks.find(m=>m._editorId===selected.pdId),values=['x1','y1','x2','y2'].map(key=>key[0]==='x'?mark[key]*layout.width/1000-layout.width/2:layout.height/2-mark[key]*layout.height/1000),raw=prompt('시작X, 시작Y, 끝X, 끝Y (도면 중앙 기준 · 오른쪽 +X / 위쪽 +Y · mm)',values.join(', '));if(raw==null)return;const input=raw.split(',').map(v=>Number(v.trim())),list=input.map((v,i)=>i%2?layout.height/2-v:v+layout.width/2);if(list.length!==4||!list.every(D.integer)||list[0]===list[2]&&list[1]===list[3])return notice('서로 다른 두 점을 1mm 간격으로 입력해 주세요.');if(list.some((v,i)=>v<0||v>(i%2?layout.height:layout.width)))return notice('두 점은 바탕 도형 안에 있어야 합니다.');['x1','y1','x2','y2'].forEach((key,i)=>mark[key]=Math.round(list[i]/(key[0]==='x'?layout.width:layout.height)*1000));applyState(old,[mark._editorId]);remember();});
    $('#edit-mark-text')?.addEventListener('click',()=>{if(!editable())return;const text=prompt('표시할 문자 (80자 이하)',selected.pdMark.text||'');if(text==null)return;const next=state(),mark=next.marks.find(m=>m._editorId===selected.pdId);mark.text=text.slice(0,80);applyState(next,[mark._editorId]);remember();});
    document.querySelectorAll('[data-align]').forEach(b=>b.onclick=()=>alignSelection(b.dataset.align));document.querySelectorAll('[data-layer]').forEach(b=>b.onclick=()=>reorder(b.dataset.layer));
  }
  function remove(){if(!editable()||!graph.getSelectionCount())return;const keys=new Set(selectionKeys()),next=state();if(opts.template)next.marks=next.marks.filter(m=>!keys.has(m._editorId));else next.items=next.items.filter(i=>!keys.has(i.id));applyState(next);remember();notice('선택한 도형을 삭제했습니다. 실행 취소로 되돌릴 수 있습니다.');}
  function duplicate(){if(!editable()||!graph.getSelectionCount())return;const keys=new Set(selectionKeys()),next=state();if(opts.template){const source=next.marks.filter(m=>keys.has(m._editorId));if(next.marks.length+source.length>100)return notice('내부 기호는 최대 100개입니다.');const dx=10000/layout.width,dy=10000/layout.height,added=source.map(m=>({...m,_editorId:D.id(),x1:m.x1+dx,x2:m.x2+dx,y1:m.y1+dy,y2:m.y2+dy}));if(added.some(m=>Math.max(m.x1,m.x2)>1000||Math.max(m.y1,m.y2)>1000))return notice('10mm 옮겨 복제할 공간이 없습니다. 위치를 옮긴 뒤 복제하세요.');next.marks.push(...added.map(m=>({...m,x1:Math.round(m.x1),x2:Math.round(m.x2),y1:Math.round(m.y1),y2:Math.round(m.y2)})));applyState(next,added.map(m=>m._editorId));}else{const source=next.items.filter(i=>keys.has(i.id));if(next.items.length+source.length>500)return notice('배치 도형은 최대 500개입니다.');const added=[];for(const item of source){let found;const xs=[item.x+item.width,...next.items.flatMap(i=>[i.x+i.width,i.x-item.width]),0],ys=[item.y,...next.items.flatMap(i=>[i.y+i.height,i.y-item.height]),0];for(const y of ys){for(const x of xs){const test={...item,id:D.id(),x,y};if(!check({...next,items:[...next.items,test]},true).errors.length){found=test;break;}}if(found)break;}if(!found)return notice('선택한 도형을 모두 복제할 공간이 없습니다. 기존 배치는 유지됩니다.');next.items.push(found);added.push(found);}applyState(next,added.map(i=>i.id));}remember();notice('복제했습니다. 위치를 조정한 뒤 확정해 주세요.');}
  function alignSelection(mode){if(!editable())return;const cells=graph.getSelectionCells();if(cells.length<2)return;const previous=state(),left=Math.min(...cells.map(c=>c.geometry.x)),top=Math.min(...cells.map(c=>c.geometry.y)),right=Math.max(...cells.map(c=>c.geometry.x+c.geometry.width)),bottom=Math.max(...cells.map(c=>c.geometry.y+c.geometry.height));loading=true;graph.model.beginUpdate();try{for(const cell of cells){const g=cell.geometry.clone();if(mode==='left')g.x=left;if(mode==='right')g.x=right-g.width;if(mode==='centerX')g.x=Math.round((left+right-g.width)/2);if(mode==='top')g.y=top;if(mode==='bottom')g.y=bottom-g.height;if(mode==='centerY')g.y=Math.round((top+bottom-g.height)/2);graph.model.setGeometry(cell,g);}}finally{graph.model.endUpdate();loading=false;}finish(previous);}
  function reorder(direction){if(!opts.template||!editable())return;const next=state(),keys=new Set(selectionKeys()),a=next.marks;if(direction==='up'){for(let i=a.length-2;i>=0;i--)if(keys.has(a[i]._editorId)&&!keys.has(a[i+1]._editorId))[a[i],a[i+1]]=[a[i+1],a[i]];}else for(let i=1;i<a.length;i++)if(keys.has(a[i]._editorId)&&!keys.has(a[i-1]._editorId))[a[i],a[i-1]]=[a[i-1],a[i]];applyState(next,[...keys]);remember();}
  function remaining(){if(!selected||opts.template||!editable())return;const previous=state(),sid=selected.pdItem.id,v=variant(selected),regions=D.gaps({...previous,items:previous.items.filter(x=>x.id!==sid)}),g=selected.geometry;
    for(const r of regions.filter(r=>r.x<=g.x+g.width/2&&r.x+r.width>=g.x+g.width/2&&r.y<=g.y+g.height/2&&r.y+r.height>=g.y+g.height/2)){
      const next=D.copy(previous),i=next.items.find(x=>x.id===sid);Object.assign(i,r);if(!check(next,true).errors.length){applyState(next);remember();return notice('허용 치수 안에서 남은 영역에 맞췄습니다.');}}
    notice(v.name+': 남은 영역이 허용 치수를 만족하지 않습니다.');}
  function status(){if(opts.template){$('#status').textContent='내부 기호 '+graph.getChildVertices(board).length+'개 · 고객 치수에 비례해 표시됩니다. 기호 적용 후 이전 창에서 도형저장을 눌러 주세요.';buttons();renderObjects();updateStepGuide();return;}
    const result=opts.readOnly?{errors:[],counts:state().items.reduce((a,i)=>{const v=config.variants.find(v=>v.key===i.variantKey);if(v)a[v.fieldKey]=(a[v.fieldKey]||0)+1;return a;},{})}:check(state());$('#counts').innerHTML='<h3>배치 개수</h3>'+fields.map(k=>`<div class="pd-count"><span>${D.esc(opts.labels?.[k]||config.variants.find(v=>v.fieldKey===k)?.fieldLabel||config.variants.find(v=>v.fieldKey===k)?.name||'항목')}</span><strong>${result.counts[k]||0}개</strong></div>`).join('');
    $('#status').textContent=result.errors.length?result.errors.join(' / '):opts.readOnly?'저장된 확정 도면 · 조회 전용':'배치 검사를 통과했습니다. 확정을 누르면 개수를 질문에 반영합니다.';$('#status').classList.toggle('pd-error',!!result.errors.length);
    emptyRegions=!opts.readOnly&&config.mode==='FILL'?D.gaps(state()).sort((a,b)=>b.width*b.height-a.width*a.height):[];
    if(emptyRegions.length)$('#status').textContent+=' · 남은 영역 '+emptyRegions.length+'곳: '+emptyRegions.slice(0,3).map(r=>r.width+' × '+r.height+' mm').join(' / ');
    buttons();drawGuides();updateStepGuide();renderObjects();document.querySelectorAll('[data-variant]').forEach(b=>b.disabled=!editable());}
  function table(){if(opts.template)return '';return '<table border="1" cellpadding="8"><tr><th>번호</th><th>종류</th><th>가로×높이 mm</th><th>중앙 원점 중심 X / Y mm</th></tr>'+state().items.map((i,n)=>`<tr><td>${n+1}</td><td>${D.esc(config.variants.find(v=>v.key===i.variantKey)?.name)}</td><td>${i.width} × ${i.height}</td><td>${i.x+i.width/2-layout.width/2} / ${layout.height/2-i.y-i.height/2}</td></tr>`).join('')+'</table>';}
})(ProductDiagram);
