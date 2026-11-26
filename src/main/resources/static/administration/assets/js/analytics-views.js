"use strict";
let analyticsLastData=null, analyticsDetailsModal=null, analyticsDetailsContext=null;
let analyticsDetailsPage=0, analyticsDetailsTotal=0, analyticsDetailsSequence=0, analyticsDetailsController=null, analyticsDetailsDebounce=null;
const analyticsKindForMetric=key=>key.startsWith("return")?"RETURN":key.startsWith("collection")?"COLLECTION":key.startsWith("paid")?"PAID":key.startsWith("adjustment")?"ADJUSTMENT":({productionRequestQty:"PRODUCTION_REQUEST",productionDoneQty:"PRODUCTION_DONE",productionPendingQty:"PRODUCTION_PENDING"}[key]||"ALL");
const analyticsViewElement=id=>document.getElementById(id);
function analyticsChartCodes() {
    const primary=analyticsViewElement("analyticsChartMetric")?.value||xMetrics[0]||"SALES";
    return [primary,...Array.from(document.querySelectorAll("[data-chart-compare]:checked")).map(input=>input.dataset.chartCompare)];
}
function analyticsRefreshCompare() {
    const box=analyticsViewElement("analyticsChartCompare");if(!box)return;
    const selected=new Set(Array.from(box.querySelectorAll("input:checked")).map(input=>input.dataset.chartCompare));
    const primary=analyticsViewElement("analyticsChartMetric")?.value||xMetrics[0]||"SALES",unit=analyticsMetricDefinitions[primary].unit;
    box.replaceChildren();
    for(const [code,metric]of Object.entries(analyticsMetricDefinitions)) {
        if(code===primary||metric.unit!==unit)continue;
        const label=document.createElement("label"),input=document.createElement("input"),text=document.createElement("span");
        input.type="checkbox";input.dataset.chartCompare=code;input.checked=selected.has(code);text.textContent=metric.label;
        input.onchange=()=>{if(analyticsChartCodes().length>4){input.checked=false;alert("그래프 지표는 같은 단위로 최대 4개까지 선택해 주세요.");return;}analyticsRefreshIfLoaded();};
        label.append(input,text);box.append(label);
    }
}
function analyticsRefreshIfLoaded(){if(appliedRequest)queryAndRender(true);}
const analyticsBaseAxis=renderAxisUI;
renderAxisUI=function(){analyticsBaseAxis();analyticsRefreshCompare();};
const analyticsBaseSync=syncAxisOrder;
syncAxisOrder=function(){const first=xMetrics[0];analyticsBaseSync();if(first!==xMetrics[0])analyticsRefreshCompare();};
const analyticsBaseRequest=buildRequestBody;
buildRequestBody=function(){
    const body=analyticsBaseRequest(),grouping=analyticsViewElement("analyticsTimeGrouping")?.value||"NONE";
    const selected=xMetrics.length?xMetrics:["SUPPLY_AMOUNT","VAT_AMOUNT","SALES","TASK_COUNT","QTY"];
    const visible=new Set(selected.map(code=>analyticsMetricDefinitions[code].key));
    for(const key of grouping!=="NONE"?["period"]:yPrimary==="COMPANY"?["companyName","ownerName",...(ySubs.includes("ADDRESS")?["address"]:[])]:["type","label"])visible.add(key);
    if(!visible.has(sortKey))sortKey="";
    const first=selected[0],defaultMetric=["SUPPLY_AMOUNT","VAT_AMOUNT","SALES"].includes(first)&&["SUPPLY_AMOUNT","VAT_AMOUNT","SALES"].every(code=>selected.includes(code))?"SALES":first;
    if(!sortKey) {sortKey=grouping==="NONE"?analyticsMetricDefinitions[defaultMetric].key:"period";sortDir=grouping==="NONE"?"desc":"asc";}
    body.sortKey=sortKey;body.sortDir=sortDir;
    body.keyword=analyticsViewElement("analyticsKeyword")?.value.trim()||"";
    body.recordKinds=Array.from(document.querySelectorAll("[data-record-kind]:checked")).map(input=>input.dataset.recordKind);
    body.timeGrouping=grouping;body.chartType=analyticsViewElement("analyticsChartType")?.value||"BAR";
    body.chartMetrics=analyticsChartCodes();body.chartLimit=Number(analyticsViewElement("analyticsChartLimit")?.value||"0");
    body.chartSortDir=analyticsViewElement("analyticsChartSortDir")?.value||"desc";
    body.compactTable=!!analyticsViewElement("analyticsCompact")?.checked;body.size=Number(analyticsViewElement("analyticsPageSize")?.value||50);
    return body;
};
const analyticsBaseQuery=queryAndRender;
queryAndRender=async function(reset){
    const body=buildRequestBody();
    if(!body.recordKinds.length){alert("주문 유형을 하나 이상 선택해 주세요.");return;}
    if(!reset&&appliedRequest&&body.size!==appliedRequest.size)reset=true;
    return analyticsBaseQuery(reset);
};
const analyticsBaseBusy=setQueryBusy;
setQueryBusy=function(busy){
    analyticsBaseBusy(busy);const loading=analyticsViewElement("analyticsLoading");if(loading&&!window.AnalyticsFeedback)loading.hidden=!busy;
    for(const id of ["analyticsCsvBtn","analyticsPngBtn"]){const node=analyticsViewElement(id);if(node)node.disabled=busy||!totalRows||(id==="analyticsPngBtn"&&!chartInstance);}
    window.AnalyticsFeedback?.setQueryBusy(busy,totalRows,!!chartInstance||!!window.AnalyticsWorkspace?.hasChartData());
};
const analyticsBaseTable=renderTable;
renderTable=function(rows,reset){
    analyticsBaseTable(rows,reset);
    const head=analyticsViewElement("analyticsThead"),body=analyticsViewElement("analyticsTbody");
    if(!head||!body)return;
    if(reset&&head.firstElementChild){const th=document.createElement("th");th.scope="col";th.className="analytics-action-cell";th.textContent="상세";head.firstElementChild.append(th);}
    if(!rows?.length&&body.querySelector(".analytics-empty-cell"))body.querySelector(".analytics-empty-cell").colSpan=currentColumns.length+1;
    if(rows?.length)Array.from(body.children).slice(-rows.length).forEach((tr,index)=>{
        const row=rows[index],scope=row.detailScope;
        currentColumns.forEach((column,colIndex)=>{
            if(!column.unit||!scope)return;
            const cell=tr.children[colIndex],button=document.createElement("button");button.type="button";button.className="analytics-value-button";
            button.textContent=cell.textContent;button.title=column.label+"에 포함된 실제 품목 보기";
            button.onclick=()=>analyticsOpenDetails(scope,analyticsKindForMetric(column.key),String(row.companyName||row.label||row.period||"선택 행")+" · "+column.label);
            cell.replaceChildren(button);
        });
        const td=document.createElement("td"),button=document.createElement("button");td.className="analytics-action-cell";button.type="button";button.className="btn btn-sm btn-light border analytics-detail-button";button.textContent="상세보기";button.disabled=!scope;
        button.onclick=()=>analyticsOpenDetails(scope,"ALL",String(row.companyName||row.label||row.period||"선택 행"));td.append(button);tr.append(td);
    });
    for(const th of head.querySelectorAll("th"))th.removeAttribute("aria-sort");
    const sortIndex=currentColumns.findIndex(column=>column.key===sortKey);if(sortIndex>=0&&head.firstElementChild)head.firstElementChild.children[sortIndex].setAttribute("aria-sort",sortDir==="asc"?"ascending":"descending");
    analyticsSizeColumns(rows,reset);
};
function analyticsSizeColumns(rows=[],reset=false){
    const table=analyticsViewElement("analyticsTable");if(!table)return;
    let group=table.querySelector("colgroup");if(reset||!group){group?.remove();group=document.createElement("colgroup");for(let i=0;i<currentColumns.length+1;i++)group.append(document.createElement("col"));table.prepend(group);}
    let minimum=108;
    currentColumns.forEach((column,index)=>{
        const col=group.children[index];if(!col)return;
        let width=column.key==="rank"?90:column.key==="type"?88:column.key==="period"?140:analyticsIsNumericColumn(column)?200:column.key==="address"?260:220;
        if(analyticsIsNumericColumn(column)&&column.key!=="rank")for(const row of rows||[])width=Math.max(width,formatAnalyticsNumber(row[column.key]).length*8+70);
        if(!reset&&col.style.width)width=Math.max(width,parseFloat(col.style.width));
        if(analyticsIsNumericColumn(column)||["type","period"].includes(column.key))col.style.width=width+"px";
        col.dataset.columnKey=column.key;minimum+=width;
    });
    if(group.lastElementChild)group.lastElementChild.style.width="108px";
    table.style.setProperty("--analytics-table-min-width",minimum+"px");
}
function analyticsRenderFooter(data){
    const table=analyticsViewElement("analyticsTable");if(!table)return;
    table.tFoot?.remove();if(!data.totals)return;
    const footer=table.createTFoot(),tr=footer.insertRow();
    currentColumns.forEach((column,index)=>{const th=document.createElement("th");th.dataset.columnKey=column.key;th.textContent=column.unit?formatAnalyticsNumber(data.totals.metrics?.[column.key]??data.totals[column.key]??0):index===0?"합계":"";th.className=analyticsColumnClass(column);if(column.unit||column.key==="rank")th.classList.add("text-end");tr.append(th);});
    const th=document.createElement("th");th.className="analytics-action-cell";th.textContent="전체 범위";tr.append(th);
    analyticsSizeColumns([data.totals.metrics||data.totals],false);
}
const analyticsBaseContext=renderResultContext;
renderResultContext=function(data){
    analyticsLastData=data;analyticsBaseContext(data);analyticsRenderFooter(data);
    document.body.classList.toggle("analytics-compact",!!appliedRequest?.compactTable);
    const box=analyticsViewElement("analyticsResultContext");if(box){
        const kinds=["PAID","RETURN","PRODUCTION_REQUEST","COLLECTION"];
        Array.from(box.querySelectorAll(".row>div>div")).forEach((card,index)=>{
            if(index>3)return;card.classList.add("analytics-kpi-action");card.tabIndex=0;card.setAttribute("role","button");card.title="포함된 실제 품목 보기";
            const open=()=>analyticsOpenDetails({mode:"ALL"},kinds[index],["유상 주문","0원 반품용","생산 요청","회수"][index]);
            card.onclick=open;card.onkeydown=event=>{if(event.target===card&&(event.key==="Enter"||event.key===" ")){event.preventDefault();open();}};
            if(index===2){const detail=card.lastElementChild;detail.replaceChildren();for(const [kind,key,label]of[["PRODUCTION_DONE","productionDoneQty","완료"],["PRODUCTION_PENDING","productionPendingQty","대기"]]){const button=document.createElement("button");button.type="button";button.className="analytics-value-button me-2";button.textContent=label+" "+formatAnalyticsNumber(data.totals.metrics[key])+"개";button.onclick=event=>{event.stopPropagation();analyticsOpenDetails({mode:"ALL"},kind,"생산 "+label);};detail.append(button);}}
        });
    }
    const select=analyticsViewElement("analyticsSortKey");if(select){select.replaceChildren();for(const column of currentColumns.filter(c=>c.key!=="rank")){const option=document.createElement("option");option.value=column.key;option.textContent=column.label;select.append(option);}select.value=sortKey;}
    if(analyticsViewElement("analyticsSortDir"))analyticsViewElement("analyticsSortDir").value=sortDir;
};
renderChart=function(chart){
    const canvas=analyticsViewElement("analyticsChart");if(!canvas)return;
    chartInstance?.destroy();chartInstance=null;
    if(!chart?.labels?.length){analyticsViewElement("analyticsGraphNote").textContent="현재 조건에 맞는 그래프 데이터가 없습니다.";return;}
    const horizontal=chart.type==="horizontal",line=chart.type==="line";
    const series=chart.series?.length?chart.series:[{label:chart.title,values:chart.values,color:"#2563eb"}];
    analyticsViewElement("analyticsChartInner").style.height=(horizontal?Math.max(320,chart.labels.length*32+115):380)+"px";
    const whiteBackground={id:"analyticsWhiteBackground",beforeDraw(instance){const context=instance.ctx;context.save();context.globalCompositeOperation="destination-over";context.fillStyle="#fff";context.fillRect(0,0,instance.width,instance.height);context.restore();}};
    chartInstance=new Chart(canvas,{type:line?"line":"bar",data:{labels:chart.labels,datasets:series.map(s=>({label:s.label,data:s.values,borderColor:s.color,backgroundColor:s.color||"#2563eb",borderWidth:line?2:0,borderRadius:line?0:4,pointRadius:chart.labels.length>60?0:3,pointHitRadius:8,tension:0,fill:false}))},plugins:[whiteBackground],options:{indexAxis:horizontal?"y":"x",responsive:true,maintainAspectRatio:false,animation:false,interaction:{mode:"index",intersect:false},scales:{[horizontal?"x":"y"]:{beginAtZero:true,grid:{color:"#e2e8f0"},ticks:{precision:0,callback:value=>formatAnalyticsNumber(value)}},[horizontal?"y":"x"]:{grid:{display:false},ticks:{autoSkip:!horizontal,maxTicksLimit:horizontal?undefined:12}}},plugins:{title:{display:true,text:chart.title||"분석 그래프",color:"#334155",font:{size:15,weight:"600"},padding:{bottom:6}},subtitle:{display:true,text:[(appliedRequest?(appliedRequest.fromDate||"과거 전체")+" ~ "+(appliedRequest.toDate||"미래 전체")+" · ":"")+"단위: "+(chart.unit||""),chart.note||""].filter(Boolean),color:"#64748b",font:{size:11},padding:{bottom:8}},legend:{position:"top",labels:{boxWidth:12,padding:18}},tooltip:{callbacks:{label:context=>`${context.dataset.label}: ${formatAnalyticsNumber(horizontal?context.parsed.x:context.parsed.y)}${chart.unit||""}`}}}}});
    analyticsViewElement("analyticsGraphNote").textContent=(chart.note||chart.title||"")+" · 단위: "+(chart.unit||"");
};
function analyticsSaveBlob(blob,filename){const url=URL.createObjectURL(blob),link=document.createElement("a");link.href=url;link.download=filename;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
async function analyticsExportCsv(){
    let feedback;
    try{
        const body=buildRequestBodyForExport();if(window.AnalyticsFeedback?.isExporting())return;
        feedback=window.AnalyticsFeedback?.startExport("CSV 파일을 생성하고 있습니다","analyticsCsvBtn",analyticsExportCsv);
        const response=await fetch("/analytics/api/export/csv",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
        if(!response.ok)throw new Error(await responseError(response,"CSV 출력에 실패했습니다."));
        feedback?.update("CSV 다운로드를 준비하고 있습니다","생성된 파일 데이터를 읽고 있습니다.");
        analyticsSaveBlob(await response.blob(),"analytics.csv");feedback?.finish("success","CSV 파일을 생성하여 다운로드를 요청했습니다.");
    }catch(error){feedback?.finish("error",error.message||"CSV 출력에 실패했습니다.");if(!feedback)alert(error.message);}
}
function analyticsExportPng(){try{buildRequestBodyForExport();if(!chartInstance)throw new Error("그래프를 먼저 조회해 주세요.");const link=document.createElement("a");link.download="analytics_chart.png";link.href=chartInstance.toBase64Image("image/png",1);link.click();}catch(error){alert(error.message);}}
function analyticsDateText(date){return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,"0")}-${String(date.getDate()).padStart(2,"0")}`;}
function analyticsQuickDate(kind){const today=new Date(),from=new Date(today),to=new Date(today);if(kind==="7D")from.setDate(from.getDate()-6);if(kind==="MONTH")from.setDate(1);if(kind==="LAST_MONTH"){from.setDate(1);from.setMonth(from.getMonth()-1);to.setDate(0);}if(kind==="QUARTER"){from.setDate(1);from.setMonth(Math.floor(from.getMonth()/3)*3);}if(kind==="YEAR"){from.setDate(1);from.setMonth(0);}analyticsViewElement("fromDate").value=analyticsDateText(from);analyticsViewElement("toDate").value=analyticsDateText(to);analyticsRefreshIfLoaded();}
function analyticsOpenDetails(scope,kind,title){
    if(!appliedRequest)return;analyticsDetailsContext={query:{...JSON.parse(JSON.stringify(appliedRequest)),page:0,size:50},scope:JSON.parse(JSON.stringify(scope))};analyticsDetailsPage=0;analyticsDetailsTotal=0;
    analyticsViewElement("analyticsDetailsSummary").textContent="";
    analyticsViewElement("analyticsDetailsTitle").textContent=title+" · 상세 품목";
    analyticsViewElement("analyticsDetailKind").value=kind||"ALL";analyticsViewElement("analyticsDetailKeyword").value="";analyticsViewElement("analyticsDetailDay").replaceChildren(new Option("날짜 전체",""));analyticsViewElement("analyticsDetailSort").value="date-desc";
    analyticsDetailsModal?.show();analyticsLoadDetails(0);
}
async function analyticsLoadDetails(pageNumber=0){
    if(!analyticsDetailsContext)return;
    const sequence=++analyticsDetailsSequence;analyticsDetailsController?.abort();const controller=new AbortController();analyticsDetailsController=controller;
    const [sortKey,sortDir]=analyticsViewElement("analyticsDetailSort").value.split("-");
    const request={...analyticsDetailsContext,page:pageNumber,size:50,kind:analyticsViewElement("analyticsDetailKind").value,keyword:analyticsViewElement("analyticsDetailKeyword").value.trim(),day:analyticsViewElement("analyticsDetailDay").value,sortKey,sortDir};
    const feedback=window.AnalyticsFeedback?.start("details",{
        host:"analyticsDetailsLoading",title:analyticsViewElement("analyticsDetailKind").selectedOptions[0].textContent+" 품목을 조회하고 있습니다",
        message:(request.day||"조회 기간 전체")+" · 업체·상품·수량 등 실제 품목을 불러오고 있습니다.",
        onCancel:()=>controller.abort(),onRetry:()=>analyticsLoadDetails(pageNumber)
    });
    analyticsViewElement("analyticsDetailsSummary").textContent=`${request.query.fromDate||"과거 전체"} ~ ${request.query.toDate||"미래 전체"}`;
    analyticsViewElement("analyticsDetailsMessage").textContent="집계에 포함된 실제 품목을 조회하고 있습니다.";
    const loadingBody=analyticsViewElement("analyticsDetailsBody");loadingBody.replaceChildren();const loadingRow=document.createElement("tr"),loadingCell=document.createElement("td");loadingCell.colSpan=11;loadingCell.className="text-center text-muted p-4";loadingCell.textContent="상세 품목 조회 중…";loadingRow.append(loadingCell);loadingBody.append(loadingRow);loadingBody.setAttribute("aria-busy","true");
    analyticsViewElement("analyticsDetailsPrev").disabled=true;analyticsViewElement("analyticsDetailsNext").disabled=true;
    try{
        const response=await fetch("/analytics/api/details",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(request),signal:controller.signal});
        if(!response.ok)throw new Error(await responseError(response,"상세 목록을 불러오지 못했습니다."));
        if(sequence!==analyticsDetailsSequence||controller.signal.aborted)return;
        feedback?.update("상세 응답을 읽고 있습니다","날짜별 건수와 실제 품목을 정리하고 있습니다.");
        const data=await response.json();if(sequence!==analyticsDetailsSequence||controller.signal.aborted)return;
        analyticsDetailsPage=data.page;analyticsDetailsTotal=data.totalRows;
        const daySelect=analyticsViewElement("analyticsDetailDay"),selected=request.day;daySelect.replaceChildren(new Option("날짜 전체",""));
        for(const day of data.days||[])daySelect.append(new Option(`${day.date} · ${formatAnalyticsNumber(day.count)}건`,day.date));
        if(selected&&!Array.from(daySelect.options).some(o=>o.value===selected))daySelect.append(new Option(selected+" · 0건",selected));daySelect.value=selected;
        const body=analyticsViewElement("analyticsDetailsBody");body.replaceChildren();body.setAttribute("aria-busy","false");let previousDay=null;
        const dayCounts=new Map((data.days||[]).map(day=>[day.date,day.count]));
        if(!data.rows.length){const tr=document.createElement("tr"),td=document.createElement("td");td.colSpan=11;td.className="text-center text-muted p-4";td.textContent="현재 범위와 조건에 해당하는 실제 품목이 없습니다.";tr.append(td);body.append(tr);}
        for(const row of data.rows){
            if(sortKey==="date"&&row.date!==previousDay){const tr=document.createElement("tr"),td=document.createElement("td");tr.className="analytics-detail-day";td.colSpan=11;td.textContent=`${row.date} · 해당일 전체 ${formatAnalyticsNumber(dayCounts.get(row.date)||0)}건`;tr.append(td);body.append(tr);previousDay=row.date;}
            const tr=document.createElement("tr");
            for(const [key,value]of[["date",row.dateTime],["company",row.companyName],["category",row.type+" · "+row.category],["product",row.series+" / "+row.product],["kind",row.kind],["quantity",formatAnalyticsNumber(row.quantity)],["supplyAmount",formatAnalyticsNumber(row.supplyAmount)],["vatAmount",formatAnalyticsNumber(row.vatAmount)],["amount",formatAnalyticsNumber(row.amount)],["status",row.status],["id",String(row.orderId)]]){
                const td=document.createElement("td");if(key==="kind"){const badge=document.createElement("span");badge.className="analytics-kind-badge analytics-kind-"+row.kindCode;badge.textContent=value;td.append(badge);}else td.textContent=value;
                if(key==="company"&&row.companyId!=null)td.title="업체번호 "+row.companyId;
                if(key==="product"&&(row.color||row.size)){const detail=document.createElement("small");detail.className="d-block text-muted mt-1";detail.textContent=[row.color,row.size].filter(Boolean).join(" · ");td.append(detail);}
                if(["quantity","supplyAmount","vatAmount","amount","id"].includes(key)){td.className="text-end analytics-detail-number";td.dataset.columnKey=key;if(Number(row[key])<0)td.classList.add("analytics-negative");}tr.append(td);
            }body.append(tr);
        }
        analyticsViewElement("analyticsDetailsSummary").textContent=`${request.query.fromDate||"과거 전체"} ~ ${request.query.toDate||"미래 전체"} · ${formatAnalyticsNumber(data.totalRows)}건 · 수량 합계 ${formatAnalyticsNumber(data.totals.qty)}개 · 공급가액 ${formatAnalyticsNumber(data.totals.supplyAmount)}원 · 부가세 ${formatAnalyticsNumber(data.totals.vatAmount)}원 · 합계 ${formatAnalyticsNumber(data.totals.sales)}원`;
        analyticsViewElement("analyticsDetailsMessage").textContent="날짜를 선택하거나 업체·상품을 검색하여 포함된 품목을 좁혀 볼 수 있습니다.";
        analyticsViewElement("analyticsDetailsPage").textContent=data.totalRows?`${data.page+1} / ${Math.ceil(data.totalRows/data.size)}페이지` : "0건";
        analyticsViewElement("analyticsDetailsPrev").disabled=data.page===0;analyticsViewElement("analyticsDetailsNext").disabled=(data.page+1)*data.size>=data.totalRows;
        feedback?.finish("success",data.totalRows?`${formatAnalyticsNumber(data.totalRows)}건 중 ${formatAnalyticsNumber(data.rows.length)}건을 표시했습니다.`:"현재 조건에 맞는 품목이 없습니다.");
    }catch(error){
        if(sequence===analyticsDetailsSequence){
            const canceled=error.name==="AbortError";
            feedback?.finish(canceled?"canceled":"error",canceled?"상세 조회 대기를 중단했습니다. 다시 시도할 수 있습니다.":error.message||"상세 목록을 불러오지 못했습니다.");
            analyticsViewElement("analyticsDetailsMessage").textContent=canceled?"조회 대기가 중단되었습니다.":error.message;
            loadingCell.textContent=canceled?"다시 시도를 누르거나 조건을 바꾸어 조회해 주세요.":"상세 목록을 불러오지 못했습니다. 다시 시도를 누르거나 조건을 바꾸어 주세요.";loadingBody.replaceChildren(loadingRow);
            analyticsViewElement("analyticsDetailsPrev").disabled=true;analyticsViewElement("analyticsDetailsNext").disabled=true;
        }
    }finally{
        if(sequence===analyticsDetailsSequence){
            if(controller.signal.aborted){feedback?.finish("canceled","상세 조회 대기를 중단했습니다. 다시 시도할 수 있습니다.");loadingCell.textContent="다시 시도를 누르거나 조건을 바꾸어 조회해 주세요.";loadingBody.replaceChildren(loadingRow);}
            loadingBody.setAttribute("aria-busy","false");analyticsDetailsController=null;
        }
    }
}
document.addEventListener("DOMContentLoaded",()=>{
    const primary=analyticsViewElement("analyticsChartMetric");for(const [code,metric]of Object.entries(analyticsMetricDefinitions)){const option=document.createElement("option");option.value=code;option.textContent=metric.label+" ("+metric.unit+")";primary.append(option);}
    analyticsRefreshCompare();
    for(const node of document.querySelectorAll("[data-date-range]"))node.onclick=()=>analyticsQuickDate(node.dataset.dateRange);
    analyticsQuickDate("MONTH");
    for(const id of ["analyticsChartType","analyticsChartLimit","analyticsChartSortDir","analyticsPageSize","analyticsCompact"])analyticsViewElement(id).onchange=()=>{document.body.classList.toggle("analytics-compact",analyticsViewElement("analyticsCompact").checked);analyticsRefreshIfLoaded();};
    primary.onchange=()=>{analyticsRefreshCompare();analyticsRefreshIfLoaded();};
    analyticsViewElement("analyticsTimeGrouping").onchange=()=>{
        const trend=analyticsViewElement("analyticsTimeGrouping").value!=="NONE";sortKey="";sortDir=trend?"asc":"desc";
        analyticsViewElement("analyticsChartType").value=trend?"LINE":yPrimary==="COMPANY"?"HORIZONTAL":"BAR";
        analyticsViewElement("analyticsChartLimit").value=trend?"0":"10";analyticsViewElement("analyticsChartSortDir").disabled=trend;
        analyticsViewElement("analyticsChartLimit").querySelector('[value="0"]').disabled=!trend;analyticsRefreshIfLoaded();
    };
    analyticsViewElement("analyticsChartLimit").querySelector('[value="0"]').disabled=true;
    analyticsViewElement("analyticsSortKey").onchange=()=>{sortKey=analyticsViewElement("analyticsSortKey").value;analyticsRefreshIfLoaded();};
    analyticsViewElement("analyticsSortDir").onchange=()=>{sortDir=analyticsViewElement("analyticsSortDir").value;analyticsRefreshIfLoaded();};
    analyticsViewElement("analyticsKeyword").onkeydown=event=>{if(event.key==="Enter"){event.preventDefault();queryAndRender(true);}};
    analyticsViewElement("analyticsCsvBtn").onclick=analyticsExportCsv;analyticsViewElement("analyticsPngBtn").onclick=analyticsExportPng;
    const modalElement=analyticsViewElement("analyticsDetailsModal");analyticsDetailsModal=new bootstrap.Modal(modalElement);
    modalElement.addEventListener("hidden.bs.modal",()=>{clearTimeout(analyticsDetailsDebounce);analyticsDetailsController?.abort();analyticsDetailsController=null;analyticsDetailsContext=null;analyticsDetailsSequence++;window.AnalyticsFeedback?.clear("details");});
    for(const id of ["analyticsDetailKind","analyticsDetailDay","analyticsDetailSort"])analyticsViewElement(id).onchange=()=>analyticsLoadDetails(0);
    analyticsViewElement("analyticsDetailKeyword").oninput=()=>{clearTimeout(analyticsDetailsDebounce);analyticsDetailsDebounce=setTimeout(()=>analyticsLoadDetails(0),300);};
    analyticsViewElement("analyticsDetailsPrev").onclick=()=>analyticsLoadDetails(Math.max(0,analyticsDetailsPage-1));analyticsViewElement("analyticsDetailsNext").onclick=()=>analyticsLoadDetails(analyticsDetailsPage+1);
});
