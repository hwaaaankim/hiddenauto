package com.dev.HiddenBATHAuto.service.analytics;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsQueryResponse;

/** Deterministic vector chart for reports. Input labels are always escaped. */
public final class AnalyticsChartRenderer {
    private AnalyticsChartRenderer() {}
    private static String esc(String value) { return value == null ? "" : value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }
    private static String num(double value) { return String.format(Locale.ROOT,"%.3f",value); }
    private static String label(double value) { return new DecimalFormat("#,##0.##",DecimalFormatSymbols.getInstance(Locale.US)).format(value); }
    private static String shortened(String value,int max) { return value.codePointCount(0,value.length())<=max?value:value.substring(0,value.offsetByCodePoints(0,max-1))+"…"; }
    private static void text(StringBuilder svg,double x,double y,String text,String anchor,int size) {
        svg.append("<text x=\"").append(num(x)).append("\" y=\"").append(num(y)).append("\" text-anchor=\"").append(anchor).append("\" font-size=\"").append(size).append("\">").append(esc(text)).append("</text>");
    }
    public static List<AnalyticsQueryResponse.ChartData> unitCharts(AnalyticsQueryResponse.ChartData chart) {
        if(chart.getSeries()==null||chart.getSeries().isEmpty())return List.of(chart);
        var units=chart.getSeries().stream().map(s->s.getUnit()==null?chart.getUnit():s.getUnit()).distinct().toList();
        if(units.size()==1)return List.of(chart);
        List<AnalyticsQueryResponse.ChartData> result=new ArrayList<>();
        for(String unit:units) {
            var series=chart.getSeries().stream().filter(s->java.util.Objects.equals(unit,s.getUnit()==null?chart.getUnit():s.getUnit())).toList();
            result.add(AnalyticsQueryResponse.ChartData.builder().title(chart.getTitle()+" · "+unit+" 축").type(chart.getType()).labels(chart.getLabels()).series(series)
                .values(series.get(0).getValues()).unit(unit).valueKey(series.get(0).getKey()).totalPoints(chart.getTotalPoints()).stacked(chart.isStacked())
                .note("다른 단위는 별도 축으로 비교합니다. 항목과 순서는 화면과 같습니다. "+(chart.getNote()==null?"":chart.getNote())).build());
        }return result;
    }
    public static String svg(AnalyticsQueryResponse.ChartData chart) {
        var charts=unitCharts(chart);if(charts.size()==1)return render(chart,0,chart.getLabels().size());
        StringBuilder svg=new StringBuilder();double offset=0;
        for(var panel:charts){String part=render(panel,0,panel.getLabels().size());double height="horizontal".equals(panel.getType())?Math.max(340,125+panel.getLabels().size()*36):440;
            svg.append(part.replace("style=\"width:100%;height:auto;background:white\"", "").replace("<svg xmlns=", "<svg x=\"0\" y=\""+num(offset)+"\" width=\"1200\" height=\""+num(height)+"\" xmlns="));offset+=height;}
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1200 "+num(offset)+"\" style=\"width:100%;height:auto\">"+svg+"</svg>";
    }
    public static List<String> pages(AnalyticsQueryResponse.ChartData chart) {
        List<String> pages=new ArrayList<>();
        for(var panel:unitCharts(chart)) {
            int chunk="horizontal".equals(panel.getType())?16:panel.getLabels().size();
            for(int start=0;start<panel.getLabels().size();start+=Math.max(1,chunk)) pages.add(render(panel,start,Math.min(start+chunk,panel.getLabels().size())));
        }
        return pages;
    }
    private static String render(AnalyticsQueryResponse.ChartData chart,int start,int end) {
        List<AnalyticsQueryResponse.ChartSeries> series=chart.getSeries();
        if(series==null||series.isEmpty())series=List.of(AnalyticsQueryResponse.ChartSeries.builder().label(chart.getTitle()).color("#2563eb").values(chart.getValues()).build());
        boolean horizontal="horizontal".equals(chart.getType()), line="line".equals(chart.getType());
        int n=end-start;double width=1200,height=horizontal?Math.max(340,125+n*36):440;
        double left=horizontal?290:102,right=36,top=90+Math.max(0,(series.size()-1)/4)*18,bottom=horizontal?55:88,plotW=width-left-right,plotH=height-top-bottom;
        double min=0,max=0;
        for(var s:series)for(Number value:s.getValues()){min=Math.min(min,value.doubleValue());max=Math.max(max,value.doubleValue());}
        if(max==min)max=min+1;
        double rough=(max-min)/5, power=Math.pow(10,Math.floor(Math.log10(rough))), fraction=rough/power;
        double step=Math.max(1,(fraction<=1?1:fraction<=2?2:fraction<=5?5:10)*power);
        min=Math.floor(min/step)*step;max=Math.ceil(max/step)*step;if(max==min)max=min+step;
        double range=max-min;int ticks=(int)Math.round(range/step);
        StringBuilder svg=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" viewBox=\"0 0 1200 "+num(height)+"\" style=\"width:100%;height:auto;background:white\"><title>"+esc(chart.getTitle())+"</title><rect width=\"100%\" height=\"100%\" fill=\"white\"/><g fill=\"#334155\" font-family=\"Noto Sans CJK KR, Malgun Gothic, Arial, sans-serif\">");
        text(svg,24,27,chart.getTitle(),"start",18);
        for(int k=0;k<series.size();k++){var s=series.get(k);String color=s.getColor()!=null&&s.getColor().matches("#[0-9a-fA-F]{6}")?s.getColor():"#2563eb";double x=24+(k%4)*290;
            svg.append("<rect x=\"").append(x).append("\" y=\""+(44+(k/4)*18)+"\" width=\"14\" height=\"10\" rx=\"2\" fill=\"").append(color).append("\"/>");text(svg,x+21,54+(k/4)*18,s.getLabel(),"start",12);}
        text(svg,width-right,75,"단위: "+chart.getUnit()+(start>0||end<chart.getLabels().size()?" · "+(start+1)+"~"+end+" / "+chart.getLabels().size():""),"end",12);
        for(int tick=0;tick<=ticks;tick++){double value=min+step*tick,x=left+plotW*(value-min)/range,y=top+plotH*(max-value)/range;
            if(horizontal){svg.append("<line x1=\"").append(num(x)).append("\" x2=\"").append(num(x)).append("\" y1=\"").append(top).append("\" y2=\"").append(num(top+plotH)).append("\" stroke=\"#e2e8f0\"/>");text(svg,x,top+plotH+23,label(value),"middle",11);}
            else{svg.append("<line x1=\"").append(left).append("\" x2=\"").append(num(left+plotW)).append("\" y1=\"").append(num(y)).append("\" y2=\"").append(num(y)).append("\" stroke=\"#e2e8f0\"/>");text(svg,left-12,y+4,label(value),"end",11);}}
        double zero=horizontal?left+plotW*(0-min)/range:top+plotH*(max-0)/range;
        svg.append(horizontal?"<line y1=\""+top+"\" y2=\""+num(top+plotH)+"\" x1=\""+num(zero)+"\" x2=\""+num(zero)+"\"":"<line x1=\""+left+"\" x2=\""+num(left+plotW)+"\" y1=\""+num(zero)+"\" y2=\""+num(zero)+"\"").append(" stroke=\"#94a3b8\"/>");
        for(int j=0;j<n;j++){String full=chart.getLabels().get(start+j);if(horizontal)text(svg,left-12,top+(j+.5)*plotH/n+4,shortened(full,22),"end",12);
            else if(j==0||j==n-1||j%Math.max(1,(int)Math.ceil(n/10.0))==0)text(svg,left+(j+.5)*plotW/n,top+plotH+24,shortened(full.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")?full.substring(5):full,Math.max(4,Math.min(16,(int)(plotW/n*Math.max(1,Math.ceil(n/10.0))/11)))),"middle",11);}
        for(int k=0;k<series.size();k++){var s=series.get(k);String color=s.getColor()!=null&&s.getColor().matches("#[0-9a-fA-F]{6}")?s.getColor():"#2563eb";StringBuilder points=new StringBuilder();
            for(int j=0;j<n;j++){double value=s.getValues().get(start+j).doubleValue(),x=left+(j+.5)*plotW/n,y=top+plotH*(max-value)/range;
                String title=esc(chart.getLabels().get(start+j)+" / "+s.getLabel()+": "+label(value)+chart.getUnit());
                if(line){points.append(num(x)).append(',').append(num(y)).append(' ');if(n<=60)svg.append("<circle cx=\"").append(num(x)).append("\" cy=\"").append(num(y)).append("\" r=\"3\" fill=\"").append(color).append("\"><title>").append(title).append("</title></circle>");}
                else if(horizontal){double endX=left+plotW*(value-min)/range,barH=Math.min(22,(plotH/n*.72)/series.size()),barY=top+(j+.5)*plotH/n+(k-series.size()/2.0)*barH;
                    svg.append("<rect x=\"").append(num(Math.min(zero,endX))).append("\" y=\"").append(num(barY)).append("\" width=\"").append(num(Math.abs(endX-zero))).append("\" height=\"").append(num(barH-1)).append("\" rx=\"2\" fill=\"").append(color).append("\"><title>").append(title).append("</title></rect>");}
                else{double barW=plotW/n*.72/series.size(),barX=left+(j+.5)*plotW/n+(k-series.size()/2.0)*barW;
                    svg.append("<rect x=\"").append(num(barX)).append("\" y=\"").append(num(Math.min(zero,y))).append("\" width=\"").append(num(Math.max(.1,barW-1))).append("\" height=\"").append(num(Math.abs(y-zero))).append("\" rx=\"2\" fill=\"").append(color).append("\"><title>").append(title).append("</title></rect>");}}
            if(line)svg.append("<polyline points=\"").append(points).append("\" fill=\"none\" stroke=\"").append(color).append("\" stroke-width=\"2.5\"/>");
        }
        text(svg,24,height-12,chart.getNote()==null?"":chart.getNote(),"start",11);
        return svg.append("</g></svg>").toString();
    }
}
