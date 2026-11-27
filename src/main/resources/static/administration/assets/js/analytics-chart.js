"use strict";
window.AnalyticsCharts = {
    render(canvas, chart, container) {
        if (!chart?.labels?.length) return null;
        const series=chart.series?.length?chart.series:[{label:chart.title,unit:chart.unit,color:"#2563eb",values:chart.values}];
        const units=[...new Set(series.map(s=>s.unit||chart.unit))], mixed=units.length>1;
        const horizontal=chart.type==="horizontal", line=chart.type==="line";
        if(container)container.style.height=(horizontal?Math.max(340,chart.labels.length*36+130):420)+"px";
        const axes={};
        units.forEach((unit,index)=>{axes["value"+index]={axis:horizontal?"x":"y",position:horizontal?(index===0?"bottom":"top"):(index===0?"left":"right"),beginAtZero:true,stacked:!!chart.stacked,
            title:{display:true,text:unit+" · "+series.filter(s=>(s.unit||chart.unit)===unit).map(s=>s.label).join(" / ")},
            grid:{drawOnChartArea:index===0,color:"#e2e8f0"},ticks:{precision:0,callback:v=>new Intl.NumberFormat("ko-KR").format(v)}};});
        axes.category={axis:horizontal?"y":"x",type:"category",stacked:!!chart.stacked,grid:{display:false},ticks:{autoSkip:!horizontal,maxRotation:40,callback:function(value){const label=String(this.getLabelForValue(value)),characters=Array.from(label),limit=horizontal?22:14;return characters.length>limit?characters.slice(0,limit-1).join("")+"…":label;}}};
        const white={id:"analyticsWhite",beforeDraw(c){c.ctx.save();c.ctx.globalCompositeOperation="destination-over";c.ctx.fillStyle="#fff";c.ctx.fillRect(0,0,c.width,c.height);c.ctx.restore();}};
        return new Chart(canvas,{type:line?"line":"bar",data:{labels:chart.labels,datasets:series.map(s=>{
            const unit=s.unit||chart.unit,axis="value"+units.indexOf(unit),asLine=line||(!horizontal&&mixed&&unit!=="원");
            return {label:s.label+" ("+unit+")",data:s.values,type:asLine?"line":"bar",unit,borderColor:s.color,backgroundColor:s.color,borderWidth:asLine?2:0,
                xAxisID:horizontal?axis:"category",yAxisID:horizontal?"category":axis,pointRadius:chart.labels.length>60?0:3,pointHitRadius:8,tension:0,fill:false,order:asLine?0:1};
        })},plugins:[white],options:{indexAxis:horizontal?"y":"x",responsive:true,maintainAspectRatio:false,animation:false,interaction:{mode:"index",intersect:false},scales:axes,
            plugins:{legend:{display:series.length>1,position:"bottom"},tooltip:{callbacks:{label:c=>c.dataset.label+": "+new Intl.NumberFormat("ko-KR").format(c.raw),footer:()=>mixed?"단위별 독립 축":chart.stacked?"배송수단별 구성 (저장된 부호 유지)":""}}}}});
    }
};
