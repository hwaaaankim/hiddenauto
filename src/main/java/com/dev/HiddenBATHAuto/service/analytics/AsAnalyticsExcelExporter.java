package com.dev.HiddenBATHAuto.service.analytics;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import com.dev.HiddenBATHAuto.dto.analytics.*;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor
public class AsAnalyticsExcelExporter {
 private final AnalyticsExcelExporter charts;
 public byte[] export(AsAnalyticsResponse response){
  try(var wb=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
   var bundle=response.getBundle();var header=wb.createCellStyle();var font=wb.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());header.setFont(font);header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());header.setFillPattern(FillPatternType.SOLID_FOREGROUND);header.setWrapText(true);
   var number=wb.createCellStyle();number.setDataFormat(wb.createDataFormat().getFormat("#,##0;[Red]-#,##0"));number.setAlignment(HorizontalAlignment.RIGHT);
   var text=wb.createCellStyle();text.setWrapText(true);text.setVerticalAlignment(VerticalAlignment.TOP);
   var summary=wb.createSheet("집계기준·요약");int i=0;var meta=bundle.getMeta();
   for(String line:List.of("AS 분석",meta.getPeriodText(),meta.getBasisText(),meta.getYAxisText(),meta.getXAxisText(),meta.getFilterText(),meta.getViewText(),meta.getSortText(),meta.getChartBasisText(),meta.getGeneratedAtText())){summary.createRow(i++).createCell(0).setCellValue(line);}
   for(String line:response.getInsights())summary.createRow(i++).createCell(0).setCellValue(line);
   for(String line:bundle.getWarnings())summary.createRow(i++).createCell(0).setCellValue("확인: "+line);
   i++;for(var metric:AsAnalyticsMetric.values()){var row=summary.createRow(i++);row.createCell(0).setCellValue(metric.label()+" ("+metric.unit()+")");var cell=row.createCell(1);cell.setCellValue(bundle.getTotals().getMetrics().get(metric.key()));cell.setCellStyle(number);row.createCell(2).setCellValue(metric.help());}
   summary.setColumnWidth(0,100*256);summary.setColumnWidth(1,22*256);summary.setColumnWidth(2,90*256);
   for(var row:summary)for(var cell:row)if(cell.getCellType()==CellType.STRING)cell.setCellStyle(text);
   summary.createFreezePane(0,1);
   var sheet=wb.createSheet("조회결과");var columns=bundle.getColumns();var h=sheet.createRow(0);for(int c=0;c<columns.size();c++){var cell=h.createCell(c);var col=columns.get(c);cell.setCellValue(col.getLabel()+(col.getUnit()==null?"":" ("+col.getUnit()+")"));cell.setCellStyle(header);}
   int rowIndex=1;for(var values:bundle.getRows()){var row=sheet.createRow(rowIndex++);for(int c=0;c<columns.size();c++){var cell=row.createCell(c);var value=values.get(columns.get(c).getKey());if(value instanceof Number n){cell.setCellValue(n.doubleValue());cell.setCellStyle(number);}else{cell.setCellValue(value==null?"":String.valueOf(value));cell.setCellStyle(text);}}}
   if(rowIndex>1)sheet.setAutoFilter(new CellRangeAddress(0,rowIndex-1,0,columns.size()-1));
   var total=sheet.createRow(rowIndex);for(int c=0;c<columns.size();c++){var cell=total.createCell(c);var col=columns.get(c);if(col.getUnit()!=null){cell.setCellValue(bundle.getTotals().getMetrics().get(col.getKey()));cell.setCellStyle(number);}else cell.setCellValue(c==0?"전체 합계":"");}
   for(int c=0;c<columns.size();c++)sheet.setColumnWidth(c,columns.get(c).getUnit()==null?32*256:23*256);
   sheet.createFreezePane(1,1);sheet.setRepeatingRows(new CellRangeAddress(0,0,-1,-1));sheet.getPrintSetup().setLandscape(true);sheet.getPrintSetup().setFitWidth((short)1);sheet.setFitToPage(true);
   var definitions=wb.createSheet("지표설명");i=0;for(var metric:AsAnalyticsMetric.values()){var row=definitions.createRow(i++);row.createCell(0).setCellValue(metric.label());row.createCell(1).setCellValue(metric.unit());row.createCell(2).setCellValue(metric.help());}definitions.setColumnWidth(0,28*256);definitions.setColumnWidth(1,10*256);definitions.setColumnWidth(2,120*256);
   charts.writeSelectedChart(wb,bundle.getChart(),meta,header,number);
   wb.write(out);return out.toByteArray();
  }catch(java.io.IOException e){throw new IllegalStateException("AS 분석 엑셀을 만들지 못했습니다.",e);}
 }
}
