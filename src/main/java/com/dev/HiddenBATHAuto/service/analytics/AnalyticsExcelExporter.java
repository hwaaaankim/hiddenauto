package com.dev.HiddenBATHAuto.service.analytics;

import java.io.ByteArrayOutputStream;
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
import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.dto.analytics.AnalyticsQueryResponse;

@Component
public class AnalyticsExcelExporter {

    public byte[] export(
            String title,
            AnalyticsQueryResponse.Meta meta,
            AnalyticsQueryResponse.Summary summary,
            List<AnalyticsQueryResponse.ColumnMeta> columns,
            AnalyticsQueryResponse.ChartData chart,
            List<Map<String, Object>> rows
    ) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle bodyStyle = buildBodyStyle(wb);

            // =========================
            // 1) 요약 시트
            // =========================
            Sheet summarySheet = wb.createSheet("요약");

            int r = 0;
            r = writeTitle(summarySheet, r, title == null ? "Analytics Export" : title, wb);

            r = writeMeta(summarySheet, r, meta, wb);

            r += 1;
            r = writeSummaryBlock(summarySheet, r, "Top 5 요약", summary, wb, headerStyle, bodyStyle);

            r += 1;
            r = writeChartBlock(summarySheet, r, "그래프(Top 10)", chart, wb, headerStyle, bodyStyle);

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
                    c.setCellValue(cols.get(i).getLabel());
                    c.setCellStyle(headerStyle);
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

            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("엑셀 생성 실패", e);
        }
    }

    // --------------------
    // Style helpers
    // --------------------
    private CellStyle buildHeaderStyle(Workbook wb) {
        Font headerFont = wb.createFont();
        headerFont.setBold(true);

        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
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
        bodyStyle.setBorderBottom(BorderStyle.THIN);
        bodyStyle.setBorderTop(BorderStyle.THIN);
        bodyStyle.setBorderLeft(BorderStyle.THIN);
        bodyStyle.setBorderRight(BorderStyle.THIN);
        bodyStyle.setWrapText(true);
        return bodyStyle;
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
        return r;
    }

    private int writeLine(Sheet sheet, int r, String text) {
        Row row = sheet.createRow(r++);
        row.createCell(0).setCellValue(text == null ? "" : text);
        return r;
    }

    private int writeSummaryBlock(Sheet sheet, int r, String title, AnalyticsQueryResponse.Summary summary,
                                  Workbook wb, CellStyle headerStyle, CellStyle bodyStyle) {
        Row t = sheet.createRow(r++);
        t.createCell(0).setCellValue(title);

        if (summary == null) {
            sheet.createRow(r++).createCell(0).setCellValue("요약 데이터가 없습니다.");
            return r;
        }

        r = writeRankTable(sheet, r, "업체 Top 5", summary.getTopCompanies(), headerStyle, bodyStyle);
        r += 1;
        r = writeRankTable(sheet, r, "지역 Top 5", summary.getTopRegions(), headerStyle, bodyStyle);
        r += 1;
        r = writeRankTable(sheet, r, "카테고리 Top 5", summary.getTopCategories(), headerStyle, bodyStyle);
        r += 1;
        r = writeRankTable(sheet, r, "시리즈 Top 5", summary.getTopSeries(), headerStyle, bodyStyle);
        r += 1;
        r = writeRankTable(sheet, r, "제품 Top 5", summary.getTopProducts(), headerStyle, bodyStyle);

        return r;
    }

    private int writeRankTable(Sheet sheet, int r, String title,
                               List<AnalyticsQueryResponse.RankItem> items,
                               CellStyle headerStyle, CellStyle bodyStyle) {
        Row tr = sheet.createRow(r++);
        tr.createCell(0).setCellValue(title);

        Row hr = sheet.createRow(r++);
        String[] headers = {"순위", "항목", "매출액", "주문건수", "수량"};
        for (int i = 0; i < headers.length; i++) {
            Cell c = hr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
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

            Cell c2 = row.createCell(c++);
            c2.setCellValue(it.getSales());
            c2.setCellStyle(bodyStyle);

            Cell c3 = row.createCell(c++);
            c3.setCellValue(it.getTaskCount());
            c3.setCellStyle(bodyStyle);

            Cell c4 = row.createCell(c++);
            c4.setCellValue(it.getQty());
            c4.setCellStyle(bodyStyle);
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
        h1.setCellValue("값(" + (chart.getValueKey() == null ? "value" : chart.getValueKey()) + ")");
        h1.setCellStyle(headerStyle);

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