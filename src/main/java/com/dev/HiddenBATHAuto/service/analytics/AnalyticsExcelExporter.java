package com.dev.HiddenBATHAuto.service.analytics;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xddf.usermodel.XDDFColor;
import org.apache.poi.xddf.usermodel.XDDFSolidFillProperties;
import org.apache.poi.xddf.usermodel.XDDFShapeProperties;
import org.apache.poi.xddf.usermodel.XDDFLineProperties;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.AxisOrientation;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.BarGrouping;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsQueryResponse;
import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsMetric;

@Component
public class AnalyticsExcelExporter {

    public byte[] export(
            String title,
            AnalyticsQueryResponse.Meta meta,
            AnalyticsQueryResponse.Summary summary,
            AnalyticsQueryResponse.Totals totals,
            List<String> warnings,
            List<AnalyticsQueryResponse.ColumnMeta> columns,
            AnalyticsQueryResponse.ChartData chart,
            List<Map<String, Object>> rows
    ) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle numericHeaderStyle = numericHeader(wb, headerStyle);
            CellStyle bodyStyle = buildBodyStyle(wb);

            // =========================
            // 1) 요약 시트
            // =========================
            Sheet summarySheet = wb.createSheet("요약");

            int r = 0;
            r = writeTitle(summarySheet, r, title == null ? "Analytics Export" : title, wb);

            r = writeMeta(summarySheet, r, meta, wb);
            List<AnalyticsQueryResponse.ColumnMeta> metrics = columns.stream()
                    .filter(c -> c.getUnit() != null).toList();
            if (totals != null) {
                r = writeLine(summarySheet, r, "상품 매출: 공급가액 + 부가세 = 합계 (배송·포장비 제외)");
                for (var metric : List.of(AnalyticsMetric.SUPPLY_AMOUNT, AnalyticsMetric.VAT_AMOUNT, AnalyticsMetric.SALES)) {
                    Row line = summarySheet.createRow(r++);
                    line.createCell(0).setCellValue(metric.label() + " (원)");
                    Cell amount = line.createCell(1);
                    amount.setCellValue(totals.getMetrics().getOrDefault(metric.key(), 0L));
                    amount.setCellStyle(bodyStyle);
                }
                r = writeLine(summarySheet, r, "조회 대상 전체 합계");
                for (var metric : metrics) {
                    Row line = summarySheet.createRow(r++);
                    line.createCell(0).setCellValue(metric.getLabel());
                    Cell amount = line.createCell(1);
                    amount.setCellValue(totals.getMetrics().getOrDefault(metric.getKey(), 0L));
                    amount.setCellStyle(bodyStyle);
                }
            }
            if (totals != null) {
                r = writeLine(summarySheet, r, "유상 주문·0원 반품용·생산·회수 비교");
                for (var metric : List.of(AnalyticsMetric.PAID_COUNT,
                        AnalyticsMetric.PAID_QTY,
                        AnalyticsMetric.RETURN_COUNT,
                        AnalyticsMetric.RETURN_QTY,
                        AnalyticsMetric.PRODUCTION_REQUEST_QTY,
                        AnalyticsMetric.PRODUCTION_DONE_QTY,
                        AnalyticsMetric.PRODUCTION_PENDING_QTY,
                        AnalyticsMetric.COLLECTION_COUNT,
                        AnalyticsMetric.COLLECTION_QTY,
                        AnalyticsMetric.COLLECTION_SALES)) {
                    Row line = summarySheet.createRow(r++);
                    line.createCell(0).setCellValue(metric.label() + " (" + metric.unit() + ")");
                    Cell value = line.createCell(1); value.setCellValue(totals.getMetrics().getOrDefault(metric.key(), 0L));
                    value.setCellStyle(bodyStyle);
                }
            }
            if (warnings != null) for (String warning : warnings) r = writeLine(summarySheet, r, warning);

            r += 1;
            r = writeSummaryBlock(summarySheet, r, summary==null?"5개 요약":summary.getTitle(), summary, metrics, wb, headerStyle, bodyStyle);

            r += 1;
            r = writeChartBlock(summarySheet, r, "선택 그래프 데이터", chart, wb, headerStyle, bodyStyle);

            autosizeAll(summarySheet, 10);

            // =========================
            // 2) 원본데이터 시트
            // =========================
            Sheet dataSheet = wb.createSheet("원본데이터");

            if (rows == null || rows.isEmpty()) {
                Row rr = dataSheet.createRow(0);
                rr.createCell(0).setCellValue("데이터가 없습니다.");
            } else {
                // 컬럼은 columns 기준(한글 라벨)
                List<AnalyticsQueryResponse.ColumnMeta> cols = columns;

                Row headerRow = dataSheet.createRow(0);
                for (int i = 0; i < cols.size(); i++) {
                    Cell c = headerRow.createCell(i);
                    c.setCellValue(cols.get(i).getLabel() + (cols.get(i).getUnit() == null ? "" : " (" + cols.get(i).getUnit() + ")"));
                    c.setCellStyle(cols.get(i).getUnit() != null || "rank".equals(cols.get(i).getKey()) ? numericHeaderStyle : headerStyle);
                }

                int rowIdx = 1;
                for (Map<String, Object> row : rows) {
                    Row dr = dataSheet.createRow(rowIdx++);
                    for (int c = 0; c < cols.size(); c++) {
                        String key = cols.get(c).getKey();
                        Object v = row.get(key);

                        Cell cell = dr.createCell(c);
                        if (v instanceof Number) cell.setCellValue(((Number) v).doubleValue());
                        else cell.setCellValue(v == null ? "" : String.valueOf(v));
                        cell.setCellStyle(bodyStyle);
                    }
                }

                // 폭 자동 + 보정
                for (int i = 0; i < cols.size(); i++) {
                    dataSheet.autoSizeColumn(i);
                    int w = dataSheet.getColumnWidth(i);
                    dataSheet.setColumnWidth(i, Math.min(w + 1024, 22000));
                }
            }

            dataSheet.createFreezePane(0, 1);
            if (rows != null && !rows.isEmpty()) dataSheet.setAutoFilter(new CellRangeAddress(0, rows.size(), 0, columns.size()-1));
            writeComparisonSheet(wb, rows, columns, totals, headerStyle, bodyStyle);
            writeSelectedChart(wb, chart, meta, headerStyle, bodyStyle);
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("엑셀 생성 실패", e);
        }
    }

    private void writeSelectedChart(Workbook workbook, AnalyticsQueryResponse.ChartData data,
                                    AnalyticsQueryResponse.Meta meta, CellStyle headerStyle, CellStyle bodyStyle) {
        if(data==null||data.getLabels()==null||data.getLabels().isEmpty())return;
        XSSFSheet sheet=(XSSFSheet)workbook.createSheet("선택그래프");
        sheet.createRow(0).createCell(0).setCellValue(data.getTitle());
        sheet.createRow(1).createCell(0).setCellValue(meta==null?"":meta.getViewText());
        sheet.createRow(2).createCell(0).setCellValue(meta==null?"":meta.getFilterText());
        sheet.createRow(3).createCell(0).setCellValue(data.getNote());
        List<AnalyticsQueryResponse.ChartSeries> series=data.getSeries();
        if(series==null||series.isEmpty())series=List.of(AnalyticsQueryResponse.ChartSeries.builder().label(data.getTitle()).color("#2563eb").values(data.getValues()).build());
        int header=33;CellStyle numberHeader=numericHeader(workbook,headerStyle);
        Row titles=sheet.createRow(header);titles.createCell(0).setCellValue("항목 / 기간");titles.getCell(0).setCellStyle(headerStyle);
        for(int j=0;j<series.size();j++){Cell c=titles.createCell(j+1);c.setCellValue(series.get(j).getLabel()+" ("+data.getUnit()+")");c.setCellStyle(numberHeader);}
        for(int i=0;i<data.getLabels().size();i++){Row row=sheet.createRow(header+1+i);row.createCell(0).setCellValue(data.getLabels().get(i));for(int j=0;j<series.size();j++){Cell c=row.createCell(j+1);c.setCellValue(series.get(j).getValues().get(i).doubleValue());c.setCellStyle(bodyStyle);}}
        var drawing=sheet.createDrawingPatriarch();var anchor=drawing.createAnchor(0,0,0,0,0,5,12,31);var chart=drawing.createChart(anchor);
        chart.setTitleText(data.getTitle());chart.setTitleOverlay(false);chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);
        boolean horizontal="horizontal".equals(data.getType()),line="line".equals(data.getType());
        var categories=chart.createCategoryAxis(horizontal?AxisPosition.LEFT:AxisPosition.BOTTOM);
        var values=chart.createValueAxis(horizontal?AxisPosition.BOTTOM:AxisPosition.LEFT);values.setNumberFormat("#,##0");
        if(horizontal){categories.setOrientation(AxisOrientation.MAX_MIN);values.setCrosses(AxisCrosses.MAX);}
        var plot=chart.createData(line?ChartTypes.LINE:ChartTypes.BAR,categories,values);
        if(plot instanceof XDDFBarChartData bars){bars.setBarDirection(horizontal?BarDirection.BAR:BarDirection.COL);bars.setBarGrouping(BarGrouping.CLUSTERED);bars.setVaryColors(false);}
        var labels=XDDFDataSourcesFactory.fromStringCellRange(sheet,new CellRangeAddress(header+1,header+data.getLabels().size(),0,0));
        for(int j=0;j<series.size();j++){
            var source=series.get(j);var numbers=XDDFDataSourcesFactory.fromNumericCellRange(sheet,new CellRangeAddress(header+1,header+data.getLabels().size(),j+1,j+1));
            var item=plot.addSeries(labels,numbers);item.setTitle(source.getLabel());String color=source.getColor()==null?"2563eb":source.getColor().replace("#","");
            var fill=new XDDFSolidFillProperties(XDDFColor.from(Integer.parseInt(color.substring(0,2),16),Integer.parseInt(color.substring(2,4),16),Integer.parseInt(color.substring(4,6),16)));
            var shape=new XDDFShapeProperties();shape.setFillProperties(fill);var outline=new XDDFLineProperties();outline.setFillProperties(fill);outline.setWidth(2.0);shape.setLineProperties(outline);item.setShapeProperties(shape);
        }
        chart.plot(plot);for(int i=0;i<12;i++)sheet.setColumnWidth(i,i==0?30*256:15*256);sheet.createFreezePane(1,header+1);
    }

    private void writeComparisonSheet(Workbook workbook, List<Map<String, Object>> rows,
                                      List<AnalyticsQueryResponse.ColumnMeta> selectedColumns,
                                      AnalyticsQueryResponse.Totals totals, CellStyle headerStyle, CellStyle bodyStyle) {
        Sheet sheet = workbook.createSheet("주문생산회수비교");
        List<AnalyticsQueryResponse.ColumnMeta> identities = selectedColumns.stream().filter(c -> c.getUnit() == null).toList();
        List<AnalyticsMetric> metrics = List.of(AnalyticsMetric.SUPPLY_AMOUNT, AnalyticsMetric.VAT_AMOUNT, AnalyticsMetric.SALES,
                AnalyticsMetric.TASK_COUNT, AnalyticsMetric.QTY, AnalyticsMetric.PAID_COUNT, AnalyticsMetric.PAID_QTY, AnalyticsMetric.PAID_SALES,
                AnalyticsMetric.RETURN_COUNT, AnalyticsMetric.RETURN_QTY, AnalyticsMetric.PRODUCTION_REQUEST_QTY,
                AnalyticsMetric.PRODUCTION_DONE_QTY, AnalyticsMetric.PRODUCTION_PENDING_QTY,
                AnalyticsMetric.COLLECTION_COUNT, AnalyticsMetric.COLLECTION_QTY, AnalyticsMetric.COLLECTION_SALES,
                AnalyticsMetric.ADJUSTMENT_COUNT, AnalyticsMetric.ADJUSTMENT_QTY, AnalyticsMetric.ADJUSTMENT_SALES);
        Row header = sheet.createRow(0); int column = 0;
        for (var identity : identities) { Cell cell=header.createCell(column++); cell.setCellValue(identity.getLabel()); cell.setCellStyle("rank".equals(identity.getKey())?numericHeader(workbook,headerStyle):headerStyle); }
        CellStyle numberHeader=numericHeader(workbook,headerStyle);
        for (var metric : metrics) { Cell cell=header.createCell(column++); cell.setCellValue(metric.label()+" ("+metric.unit()+")");cell.setCellStyle(numberHeader); }
        int rowNumber=1;
        if (rows != null) for (var values : rows) {
            Row row=sheet.createRow(rowNumber++); column=0;
            for (var identity : identities) { Cell cell=row.createCell(column++);Object value=values.get(identity.getKey());if(value instanceof Number n)cell.setCellValue(n.doubleValue());else cell.setCellValue(value==null?"":String.valueOf(value));cell.setCellStyle(bodyStyle); }
            for (var metric : metrics) { Cell cell=row.createCell(column++);Object value=values.get(metric.key());cell.setCellValue(value instanceof Number n?n.doubleValue():0);cell.setCellStyle(bodyStyle); }
        }
        if (totals != null) {
            Row row=sheet.createRow(rowNumber);row.createCell(0).setCellValue("합계");column=identities.size();
            for (var metric : metrics) { Cell cell=row.createCell(column++);cell.setCellValue(totals.getMetrics().getOrDefault(metric.key(),0L));cell.setCellStyle(bodyStyle); }
        }
        sheet.createFreezePane(identities.size(),1);
        if (rowNumber>1)sheet.setAutoFilter(new CellRangeAddress(0,rowNumber-1,0,identities.size()+metrics.size()-1));
        for(int i=0;i<identities.size()+metrics.size();i++){sheet.autoSizeColumn(i);sheet.setColumnWidth(i,Math.min(sheet.getColumnWidth(i)+768,12000));}
    }

    // --------------------
    // Style helpers
    // --------------------
    private CellStyle buildHeaderStyle(Workbook wb) {
        Font headerFont = wb.createFont();
        headerFont.setBold(true);

        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.LEFT);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        return headerStyle;
    }

    private CellStyle buildBodyStyle(Workbook wb) {
        CellStyle bodyStyle = wb.createCellStyle();
        bodyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        bodyStyle.setAlignment(HorizontalAlignment.GENERAL);
        bodyStyle.setBorderBottom(BorderStyle.THIN);
        bodyStyle.setBorderTop(BorderStyle.THIN);
        bodyStyle.setBorderLeft(BorderStyle.THIN);
        bodyStyle.setBorderRight(BorderStyle.THIN);
        bodyStyle.setWrapText(true);
        bodyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0;[Red]-#,##0;0"));
        return bodyStyle;
    }

    private CellStyle numericHeader(Workbook workbook, CellStyle base) {
        CellStyle style=workbook.createCellStyle();style.cloneStyleFrom(base);style.setAlignment(HorizontalAlignment.RIGHT);return style;
    }

    private int writeTitle(Sheet sheet, int r, String title, Workbook wb) {
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);

        CellStyle cs = wb.createCellStyle();
        cs.setFont(f);

        Row row = sheet.createRow(r++);
        Cell c = row.createCell(0);
        c.setCellValue(title);
        c.setCellStyle(cs);
        return r;
    }

    private int writeMeta(Sheet sheet, int r, AnalyticsQueryResponse.Meta meta, Workbook wb) {
        if (meta == null) return r;

        r = writeLine(sheet, r, meta.getPeriodText());
        r = writeLine(sheet, r, meta.getYAxisText());
        r = writeLine(sheet, r, meta.getYSubText());
        r = writeLine(sheet, r, meta.getXAxisText());
        r = writeLine(sheet, r, meta.getXDimText());
        r = writeLine(sheet, r, meta.getAddressText());
        r = writeLine(sheet, r, meta.getHourText());
        r = writeLine(sheet, r, meta.getSortText());
        r = writeLine(sheet, r, meta.getBasisText());
        r = writeLine(sheet, r, meta.getChartBasisText());
        r = writeLine(sheet, r, meta.getViewText());
        r = writeLine(sheet, r, meta.getFilterText());
        r = writeLine(sheet, r, meta.getGeneratedAtText());
        return r;
    }

    private int writeLine(Sheet sheet, int r, String text) {
        Row row = sheet.createRow(r++);
        row.createCell(0).setCellValue(text == null ? "" : text);
        return r;
    }

    private int writeSummaryBlock(Sheet sheet, int r, String title, AnalyticsQueryResponse.Summary summary,
                                  List<AnalyticsQueryResponse.ColumnMeta> metrics, Workbook wb, CellStyle headerStyle, CellStyle bodyStyle) {
        Row t = sheet.createRow(r++);
        t.createCell(0).setCellValue(title);

        if (summary == null) {
            sheet.createRow(r++).createCell(0).setCellValue("요약 데이터가 없습니다.");
            return r;
        }

        if(summary.getMetricColumns()!=null)metrics=summary.getMetricColumns();
        r = writeRankTable(sheet, r, "업체별", summary.getTopCompanies(), metrics, headerStyle, bodyStyle);
        r += 1;
        r = writeRankTable(sheet, r, "지역별", summary.getTopRegions(), metrics, headerStyle, bodyStyle);
        r += 1;
        r = writeRankTable(sheet, r, "대분류별", summary.getTopCategories(), metrics, headerStyle, bodyStyle);
        r += 1;
        r = writeRankTable(sheet, r, "시리즈별", summary.getTopSeries(), metrics, headerStyle, bodyStyle);
        r += 1;
        r = writeRankTable(sheet, r, "제품별", summary.getTopProducts(), metrics, headerStyle, bodyStyle);

        return r;
    }

    private int writeRankTable(Sheet sheet, int r, String title,
                               List<AnalyticsQueryResponse.RankItem> items,
                               List<AnalyticsQueryResponse.ColumnMeta> metrics, CellStyle headerStyle, CellStyle bodyStyle) {
        Row tr = sheet.createRow(r++);
        tr.createCell(0).setCellValue(title);

        Row hr = sheet.createRow(r++);
        List<String> labels = new ArrayList<>(List.of("순위", "항목"));
        metrics.forEach(metric -> labels.add(metric.getLabel() + " (" + metric.getUnit() + ")"));
        String[] headers = labels.toArray(String[]::new);
        CellStyle numberHeader=numericHeader(sheet.getWorkbook(),headerStyle);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(i==1?headerStyle:numberHeader);
        }

        if (items == null || items.isEmpty()) {
            Row rr = sheet.createRow(r++);
            rr.createCell(0).setCellValue("데이터 없음");
            return r;
        }

        for (AnalyticsQueryResponse.RankItem it : items) {
            Row row = sheet.createRow(r++);
            int c = 0;

            Cell c0 = row.createCell(c++);
            c0.setCellValue(it.getRank());
            c0.setCellStyle(bodyStyle);

            Cell c1 = row.createCell(c++);
            c1.setCellValue(it.getName());
            c1.setCellStyle(bodyStyle);

            for (var metric : metrics) {
                Cell value = row.createCell(c++);
                value.setCellValue(it.getMetrics().getOrDefault(metric.getKey(), 0L));
                value.setCellStyle(bodyStyle);
            }
        }

        return r;
    }

    private int writeChartBlock(Sheet sheet, int r, String title, AnalyticsQueryResponse.ChartData chart,
                               Workbook wb, CellStyle headerStyle, CellStyle bodyStyle) {
        Row tr = sheet.createRow(r++);
        tr.createCell(0).setCellValue(title);

        if (chart == null || chart.getLabels() == null || chart.getValues() == null) {
            sheet.createRow(r++).createCell(0).setCellValue("그래프 데이터가 없습니다.");
            return r;
        }

        Row hr = sheet.createRow(r++);
        Cell h0 = hr.createCell(0);
        h0.setCellValue("항목");
        h0.setCellStyle(headerStyle);

        Cell h1 = hr.createCell(1);
        h1.setCellValue("첫 그래프 지표: " + (chart.getSeries()!=null&&!chart.getSeries().isEmpty()?chart.getSeries().get(0).getLabel():chart.getValueKey()));
        h1.setCellStyle(numericHeader(wb,headerStyle));

        for (int i = 0; i < chart.getLabels().size(); i++) {
            Row row = sheet.createRow(r++);
            Cell c0 = row.createCell(0);
            c0.setCellValue(chart.getLabels().get(i));
            c0.setCellStyle(bodyStyle);

            Cell c1 = row.createCell(1);
            Number v = chart.getValues().get(i);
            c1.setCellValue(v == null ? 0 : v.doubleValue());
            c1.setCellStyle(bodyStyle);
        }

        return r;
    }

    private void autosizeAll(Sheet sheet, int maxCols) {
        for (int i = 0; i < maxCols; i++) {
            sheet.autoSizeColumn(i);
            int w = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(w + 1024, 22000));
        }
    }
}
