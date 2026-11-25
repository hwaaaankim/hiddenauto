package com.dev.HiddenBATHAuto.service.analytics;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsQueryResponse;
/** Data CSV: raw numeric values are preserved; spreadsheet formula-like text is escaped. */
public final class AnalyticsCsvExporter {
 private AnalyticsCsvExporter() {}
 public static byte[] export(AnalyticsQueryResponse bundle) {
  StringBuilder text=new StringBuilder("\uFEFF");
  line(text,bundle.getColumns().stream().map(c->c.getLabel()+(c.getUnit()==null?"":" ("+c.getUnit()+")")).toList());
  for(var row:bundle.getRows()) {List<Object> values=new ArrayList<>();for(var column:bundle.getColumns())values.add(row.get(column.getKey()));line(text,values);}
  return text.toString().getBytes(StandardCharsets.UTF_8);
 }
 private static void line(StringBuilder out,List<?> values) {
  for(int i=0;i<values.size();i++){if(i>0)out.append(',');Object value=values.get(i);if(value instanceof Number)out.append(value);else{String text=value==null?"":String.valueOf(value);String start=text.stripLeading();if(!start.isEmpty()&&"=+-@\t\r".indexOf(start.charAt(0))>=0)text="'"+text;out.append('"').append(text.replace("\"","\"\"")).append('"');}}
  out.append("\r\n");
 }
}
