package com.dev.HiddenBATHAuto.service.production;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.dto.production.ProductionListExcelRowDto;
import com.dev.HiddenBATHAuto.dto.production.ProductionListOutputOptions;

@Service
public class ProductionListExcelService {

    private static final int DEFAULT_FONT_SIZE = 10;
    private static final int MIN_FONT_SIZE = 8;
    private static final int MAX_FONT_SIZE = 14;
    private static final int MAX_EXCEL_COLUMN_WIDTH = 255;
    private static final float MAX_BODY_ROW_HEIGHT = 320F;

    public Workbook createProductionListWorkbook(List<ProductionListExcelRowDto> rows) {
        return createProductionListWorkbook(rows, ProductionListOutputOptions.defaults());
    }

    public Workbook createProductionListWorkbook(
            List<ProductionListExcelRowDto> rows,
            ProductionListOutputOptions options
    ) {
        ProductionListOutputOptions resolvedOptions = options == null
                ? ProductionListOutputOptions.defaults()
                : options;
        int fontSize = normalizeFontSize(resolvedOptions.fontSize());

        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("생산 제작 목록");
        List<ColumnDefinition> columns = buildColumns(resolvedOptions, fontSize);

        applyPrintSetting(workbook, sheet, rows, columns, fontSize);

        CellStyle titleStyle = createTitleStyle(workbook, fontSize);
        CellStyle infoStyle = createInfoStyle(workbook, fontSize);
        CellStyle filterStyle = createFilterStyle(workbook, fontSize);
        CellStyle headerStyle = createHeaderStyle(workbook, fontSize);
        CellStyle bodyStyle = createBodyStyle(workbook, fontSize);
        CellStyle centerStyle = createCenterStyle(workbook, fontSize);

        int headerRowIndex = createTitleRows(
                sheet,
                titleStyle,
                infoStyle,
                filterStyle,
                rows,
                resolvedOptions,
                columns.size(),
                fontSize
        );

        createHeaderRow(sheet, headerStyle, columns, headerRowIndex, fontSize);
        createBodyRows(
                sheet,
                bodyStyle,
                centerStyle,
                rows,
                columns,
                headerRowIndex + 1,
                fontSize
        );
        sheet.createFreezePane(0, headerRowIndex + 1);

        return workbook;
    }

    private List<ColumnDefinition> buildColumns(
            ProductionListOutputOptions options,
            int fontSize
    ) {
        int growth = Math.max(0, fontSize - DEFAULT_FONT_SIZE);
        List<ColumnDefinition> columns = new ArrayList<>();

        columns.add(new ColumnDefinition("orderId", "오더ID", 10, true));

        if (options.includeCompanyName()) {
            columns.add(new ColumnDefinition("companyName", "거래처명", 22 + growth, false));
        }

        /*
         * 제품명과 남김말은 긴 문장이 많이 들어오므로 폰트가 커질수록 열 너비를 함께 키웁니다.
         * - 제품명: 10pt 25칸 → 14pt 37칸
         * - 남김말: 10pt 55칸 → 14pt 75칸
         */
        columns.add(new ColumnDefinition("productName", "제품명", 25 + (growth * 3), false));
        columns.add(new ColumnDefinition("productColor", "제품색상", 18, false));
        columns.add(new ColumnDefinition("productSize", "제품사이즈", 30 + growth, false));
        columns.add(new ColumnDefinition("quantity", "수량", 8, true));
        columns.add(new ColumnDefinition("adminMemo", "남김말", 55 + (growth * 5), false));

        if (options.includeDeliveryDate()) {
            columns.add(new ColumnDefinition("preferredDeliveryDate", "출고일", 14, true));
        }

        return columns;
    }

    private void applyPrintSetting(
            Workbook workbook,
            Sheet sheet,
            List<ProductionListExcelRowDto> rows,
            List<ColumnDefinition> columns,
            int fontSize
    ) {
        sheet.setMargin(PageMargin.LEFT, 0.20D);
        sheet.setMargin(PageMargin.RIGHT, 0.20D);
        sheet.setMargin(PageMargin.TOP, 0.30D);
        sheet.setMargin(PageMargin.BOTTOM, 0.30D);

        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setLandscape(true);
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);

        sheet.setFitToPage(true);
        sheet.setAutobreaks(true);
        sheet.setHorizontallyCenter(true);
        sheet.setDefaultRowHeightInPoints(Math.max(18F, fontSize * 1.5F));

        for (int i = 0; i < columns.size(); i++) {
            int width = Math.min(MAX_EXCEL_COLUMN_WIDTH, Math.max(1, columns.get(i).width()));
            sheet.setColumnWidth(i, width * 256);
        }

        int lastRow = Math.max(4, safeSize(rows) + 4);
        workbook.setPrintArea(0, 0, columns.size() - 1, 0, lastRow);
    }

    private int createTitleRows(
            Sheet sheet,
            CellStyle titleStyle,
            CellStyle infoStyle,
            CellStyle filterStyle,
            List<ProductionListExcelRowDto> rows,
            ProductionListOutputOptions options,
            int columnCount,
            int fontSize
    ) {
        float growth = Math.max(0, fontSize - DEFAULT_FONT_SIZE);

        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(30F + (growth * 2.5F));

        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("생산팀 제작 목록");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, columnCount - 1));

        Row infoRow = sheet.createRow(1);
        infoRow.setHeightInPoints(22F + (growth * 1.5F));
        Cell infoCell = infoRow.createCell(0);
        infoCell.setCellValue("출력일: " + LocalDate.now() + " / 현재 화면 기준 " + safeSize(rows) + "건");
        infoCell.setCellStyle(infoStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, columnCount - 1));

        Row filterRow = sheet.createRow(2);
        filterRow.setHeightInPoints(30F + (growth * 2F));
        Cell filterCell = filterRow.createCell(0);
        filterCell.setCellValue("검색필터: " + (options.filterSummary().isBlank() ? "없음" : options.filterSummary()));
        filterCell.setCellStyle(filterStyle);
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 0, columnCount - 1));

        return 3;
    }

    private void createHeaderRow(
            Sheet sheet,
            CellStyle headerStyle,
            List<ColumnDefinition> columns,
            int rowIndex,
            int fontSize
    ) {
        Row headerRow = sheet.createRow(rowIndex);
        headerRow.setHeightInPoints(Math.max(25F, fontSize * 2.2F));

        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i).label());
            cell.setCellStyle(headerStyle);
        }
    }

    private void createBodyRows(
            Sheet sheet,
            CellStyle bodyStyle,
            CellStyle centerStyle,
            List<ProductionListExcelRowDto> rows,
            List<ColumnDefinition> columns,
            int startRowIndex,
            int fontSize
    ) {
        if (rows == null || rows.isEmpty()) {
            Row row = sheet.createRow(startRowIndex);
            row.setHeightInPoints(Math.max(28F, fontSize * 2.2F));
            Cell cell = row.createCell(0);
            cell.setCellValue("조회된 생산 주문이 없습니다.");
            cell.setCellStyle(centerStyle);
            sheet.addMergedRegion(new CellRangeAddress(startRowIndex, startRowIndex, 0, columns.size() - 1));
            return;
        }

        int productNameWidth = findColumnWidth(columns, "productName", 25);
        int adminMemoWidth = findColumnWidth(columns, "adminMemo", 55);
        int rowIndex = startRowIndex;

        for (ProductionListExcelRowDto dto : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(estimateRowHeight(
                    dto != null ? dto.getProductName() : null,
                    dto != null ? dto.getAdminMemo() : null,
                    productNameWidth,
                    adminMemoWidth,
                    fontSize
            ));

            for (int colIndex = 0; colIndex < columns.size(); colIndex++) {
                ColumnDefinition column = columns.get(colIndex);
                String value = resolveValue(dto, column.key());
                setCell(row, colIndex, value, column.centered() ? centerStyle : bodyStyle);
            }
        }
    }

    private int findColumnWidth(
            List<ColumnDefinition> columns,
            String key,
            int defaultWidth
    ) {
        if (columns == null) {
            return defaultWidth;
        }

        return columns.stream()
                .filter(column -> key.equals(column.key()))
                .map(ColumnDefinition::width)
                .findFirst()
                .orElse(defaultWidth);
    }

    private String resolveValue(ProductionListExcelRowDto dto, String key) {
        if (dto == null) {
            return "-";
        }

        return switch (key) {
            case "orderId" -> dto.getOrderId() == null ? "-" : String.valueOf(dto.getOrderId());
            case "companyName" -> text(dto.getCompanyName());
            case "productName" -> text(dto.getProductName());
            case "productColor" -> text(dto.getProductColor());
            case "productSize" -> text(dto.getProductSize());
            case "quantity" -> dto.getQuantity() == null ? "-" : String.valueOf(dto.getQuantity());
            case "adminMemo" -> text(dto.getAdminMemo());
            case "preferredDeliveryDate" -> text(dto.getPreferredDeliveryDateText());
            default -> "-";
        };
    }

    private void setCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private CellStyle createTitleStyle(Workbook workbook, int fontSize) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) Math.max(16, fontSize + 7));

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createInfoStyle(Workbook workbook, int fontSize) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) fontSize);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createFilterStyle(Workbook workbook, int fontSize) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) fontSize);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook, int fontSize) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) fontSize);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyBorder(style);
        return style;
    }

    private CellStyle createBodyStyle(Workbook workbook, int fontSize) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) fontSize);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        applyBorder(style);
        return style;
    }

    private CellStyle createCenterStyle(Workbook workbook, int fontSize) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) fontSize);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        applyBorder(style);
        return style;
    }

    private void applyBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
    }

    private float estimateRowHeight(
            String productName,
            String adminMemo,
            int productNameWidth,
            int adminMemoWidth,
            int fontSize
    ) {
        int productLines = estimateWrappedLineCount(productName, productNameWidth);
        int memoLines = estimateWrappedLineCount(adminMemo, adminMemoWidth);
        int maxLines = Math.max(1, Math.max(productLines, memoLines));

        float lineHeight = Math.max(15F, fontSize * 1.55F);
        float minimumHeight = Math.max(28F, fontSize * 2.2F);
        float estimatedHeight = (maxLines * lineHeight) + 8F;

        return Math.min(MAX_BODY_ROW_HEIGHT, Math.max(minimumHeight, estimatedHeight));
    }

    private int estimateWrappedLineCount(String value, int columnWidth) {
        String normalized = text(value);
        if ("-".equals(normalized)) {
            return 1;
        }

        int usableWidth = Math.max(4, columnWidth - 2);
        String[] explicitLines = normalized.split("\\R", -1);
        int totalLines = 0;

        for (String explicitLine : explicitLines) {
            int displayLength = estimateDisplayLength(explicitLine);
            totalLines += Math.max(1, (int) Math.ceil(displayLength / (double) usableWidth));
        }

        return Math.max(1, totalLines);
    }

    private int estimateDisplayLength(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }

        int length = 0;

        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            length += codePoint <= 0x7F ? 1 : 2;
            offset += Character.charCount(codePoint);
        }

        return length;
    }

    private int normalizeFontSize(int fontSize) {
        return Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, fontSize));
    }

    private int safeSize(List<?> rows) {
        return rows == null ? 0 : rows.size();
    }

    private String text(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }

        return value.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private record ColumnDefinition(String key, String label, int width, boolean centered) {
    }
}
