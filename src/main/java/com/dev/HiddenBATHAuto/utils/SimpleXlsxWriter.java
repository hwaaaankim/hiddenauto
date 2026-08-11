package com.dev.HiddenBATHAuto.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

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
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 고객용 목록 XLSX 생성 유틸리티입니다.
 *
 * <p>중요:</p>
 * <ul>
 *     <li>OOXML(sheet1.xml)을 문자열로 직접 조립하지 않습니다.</li>
 *     <li>프로젝트에서 이미 사용 중인 Apache POI(XSSFWorkbook)로 생성합니다.</li>
 *     <li>Excel XML 복구 경고를 유발할 수 있는 제어문자를 제거합니다.</li>
 *     <li>Excel 셀 문자열 최대 길이(32,767자)를 넘지 않도록 방어합니다.</li>
 * </ul>
 */
public final class SimpleXlsxWriter {

    private static final int EXCEL_MAX_CELL_TEXT_LENGTH = 32_767;
    private static final int EXCEL_MAX_COLUMN_WIDTH = 255 * 256;

    private SimpleXlsxWriter() {
    }

    public static byte[] write(
            String sheetName,
            String title,
            List<String> filterLines,
            List<String> headers,
            List<List<String>> rows,
            double[] columnWidths) {

        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("엑셀 헤더가 비어 있습니다.");
        }

        List<String> safeFilters = filterLines == null ? List.of() : filterLines;
        List<List<String>> safeRows = rows == null ? List.of() : rows;
        String safeSheetName = sanitizeSheetName(sheetName);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(safeSheetName);

            int columnCount = headers.size();
            int lastColumnIndex = columnCount - 1;

            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle filterStyle = createFilterStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle bodyStyle = createBodyStyle(workbook);

            // 1) 제목
            int rowIndex = 0;
            Row titleRow = sheet.createRow(rowIndex++);
            titleRow.setHeightInPoints(28f);
            setTextCell(titleRow, 0, title, titleStyle);
            mergeAcross(sheet, titleRow.getRowNum(), lastColumnIndex);

            // 2) 필터 조건
            for (String filterLine : safeFilters) {
                Row filterRow = sheet.createRow(rowIndex++);
                filterRow.setHeightInPoints(20f);
                setTextCell(filterRow, 0, filterLine, filterStyle);
                mergeAcross(sheet, filterRow.getRowNum(), lastColumnIndex);
            }

            // 기존 출력 형식과 동일하게 필터와 헤더 사이 한 줄 여백을 둡니다.
            Row spacerRow = sheet.createRow(rowIndex++);
            spacerRow.setHeightInPoints(7f);

            // 3) 헤더
            int headerRowIndex = rowIndex;
            Row headerRow = sheet.createRow(rowIndex++);
            headerRow.setHeightInPoints(24f);

            for (int col = 0; col < columnCount; col++) {
                setTextCell(headerRow, col, headers.get(col), headerStyle);
            }

            // 4) 데이터
            for (List<String> sourceRow : safeRows) {
                Row row = sheet.createRow(rowIndex++);
                row.setHeightInPoints(28f);

                for (int col = 0; col < columnCount; col++) {
                    String value = sourceRow != null && col < sourceRow.size()
                            ? sourceRow.get(col)
                            : "";
                    setTextCell(row, col, value, bodyStyle);
                }
            }

            // 5) 열 너비
            for (int col = 0; col < columnCount; col++) {
                double requestedWidth = columnWidths != null && col < columnWidths.length
                        ? columnWidths[col]
                        : Math.max(12d, Math.min(36d, sanitizeCellText(headers.get(col)).length() * 2d + 3d));

                sheet.setColumnWidth(col, toPoiColumnWidth(requestedWidth));
            }

            // 6) 고정/자동필터
            // 헤더까지 고정하고 데이터부터 스크롤되도록 합니다.
            sheet.createFreezePane(0, headerRowIndex + 1);

            if (!safeRows.isEmpty()) {
                int lastDataRowIndex = rowIndex - 1;
                sheet.setAutoFilter(new CellRangeAddress(
                        headerRowIndex,
                        lastDataRowIndex,
                        0,
                        lastColumnIndex));
            }

            sheet.setDisplayGridlines(false);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("엑셀 파일 생성 중 오류가 발생했습니다.", e);
        }
    }

    private static CellStyle createTitleStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("맑은 고딕");
        font.setFontHeightInPoints((short) 14);
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle createFilterStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("맑은 고딕");
        font.setFontHeightInPoints((short) 10);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        return style;
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("맑은 고딕");
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        setThinBorder(style);
        return style;
    }

    private static CellStyle createBodyStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontName("맑은 고딕");
        font.setFontHeightInPoints((short) 9);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        setThinBorder(style);
        return style;
    }

    private static void setThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static void setTextCell(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(sanitizeCellText(value));
        cell.setCellStyle(style);
    }

    private static void mergeAcross(Sheet sheet, int rowIndex, int lastColumnIndex) {
        if (lastColumnIndex <= 0) {
            return;
        }
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, lastColumnIndex));
    }

    private static int toPoiColumnWidth(double requestedWidth) {
        double safeWidth = Double.isFinite(requestedWidth) ? requestedWidth : 12d;
        safeWidth = Math.max(4d, Math.min(255d, safeWidth));

        int width = (int) Math.round(safeWidth * 256d);
        return Math.max(1, Math.min(EXCEL_MAX_COLUMN_WIDTH, width));
    }

    private static String sanitizeSheetName(String value) {
        String source = value == null || value.isBlank() ? "Sheet1" : value.trim();
        StringBuilder result = new StringBuilder(31);

        for (int offset = 0; offset < source.length() && result.length() < 31;) {
            int codePoint = source.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (!isValidXml10CodePoint(codePoint)) {
                continue;
            }

            if (codePoint == '\\' || codePoint == '/' || codePoint == ':'
                    || codePoint == '*' || codePoint == '?' || codePoint == '[' || codePoint == ']') {
                codePoint = ' ';
            }

            int charCount = Character.charCount(codePoint);
            if (result.length() + charCount > 31) {
                break;
            }
            result.appendCodePoint(codePoint);
        }

        String safe = result.toString().trim();
        return safe.isEmpty() ? "Sheet1" : safe;
    }

    /**
     * Apache POI가 OOXML을 작성하더라도 DB 문자열 안에 XML 1.0 금지 제어문자가 들어오면
     * Excel 복구 경고의 원인이 될 수 있으므로 저장 전에 제거합니다.
     */
    private static String sanitizeCellText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(Math.min(value.length(), EXCEL_MAX_CELL_TEXT_LENGTH));

        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (!isValidXml10CodePoint(codePoint)) {
                continue;
            }

            int charCount = Character.charCount(codePoint);
            if (result.length() + charCount > EXCEL_MAX_CELL_TEXT_LENGTH) {
                break;
            }

            result.appendCodePoint(codePoint);
        }

        return result.toString();
    }

    private static boolean isValidXml10CodePoint(int codePoint) {
        return codePoint == 0x9
                || codePoint == 0xA
                || codePoint == 0xD
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }
}