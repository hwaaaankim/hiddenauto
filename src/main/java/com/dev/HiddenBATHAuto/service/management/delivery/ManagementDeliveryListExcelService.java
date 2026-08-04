package com.dev.HiddenBATHAuto.service.management.delivery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.FilterItem;
import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.GroupRow;
import com.dev.HiddenBATHAuto.dto.management.delivery.ManagementDeliveryListDtos.OrderRow;

/**
 * 관리자 배송관리 목록의 묶음 단위 엑셀 생성 서비스입니다.
 */
@Service
public class ManagementDeliveryListExcelService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter GENERATED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private static final String[] HEADERS = {
            "묶음",
            "오더 ID",
            "대리점명",
            "신청자",
            "신청일",
            "배송일",
            "배송수단",
            "배송담당자",
            "발주수량",
            "총수량",
            "상태",
            "배송지",
            "제품내역",
            "관리자메모",
            "배송이미지"
    };

    private static final int[] COLUMN_WIDTHS = {
            8, 24, 20, 16, 22, 13, 14, 18, 10, 10, 20, 42, 58, 42, 12
    };

    public byte[] buildExcel(List<GroupRow> groups, List<FilterItem> filters) {
        List<GroupRow> safeGroups = groups == null
                ? List.of()
                : groups.stream().filter(Objects::nonNull).toList();

        if (safeGroups.isEmpty()) {
            throw new IllegalArgumentException("엑셀로 출력할 배송 묶음이 없습니다.");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("관리자 배송관리");
            ExcelStyles styles = createStyles(workbook);
            configureSheet(sheet);
            writeTitleAndFilters(sheet, safeGroups, filters, styles);
            writeHeader(sheet, styles);
            writeRows(sheet, safeGroups, styles);
            configurePrintArea(workbook, sheet, safeGroups.size());

            workbook.setActiveSheet(0);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("관리자 배송관리 엑셀 생성 중 오류가 발생했습니다.", e);
        }
    }

    private void configureSheet(Sheet sheet) {
        sheet.setFitToPage(true);
        sheet.setAutobreaks(true);
        sheet.setDisplayGridlines(false);
        sheet.setPrintGridlines(false);
        sheet.setHorizontallyCenter(true);
        sheet.createFreezePane(0, 4);
        sheet.setRepeatingRows(new CellRangeAddress(0, 3, -1, -1));

        for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
            sheet.setColumnWidth(i, COLUMN_WIDTHS[i] * 256);
        }

        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setLandscape(true);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);

        sheet.setMargin(PageMargin.LEFT, 0.2);
        sheet.setMargin(PageMargin.RIGHT, 0.2);
        sheet.setMargin(PageMargin.TOP, 0.3);
        sheet.setMargin(PageMargin.BOTTOM, 0.3);
    }

    private void writeTitleAndFilters(
            Sheet sheet,
            List<GroupRow> groups,
            List<FilterItem> filters,
            ExcelStyles styles
    ) {
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(27);
        setMergedValue(
                sheet,
                0,
                0,
                HEADERS.length - 1,
                "관리자 배송관리 조회 결과",
                styles.title()
        );

        String filterText = filters == null || filters.isEmpty()
                ? "조회조건: 전체"
                : "조회조건  |  " + filters.stream()
                        .filter(Objects::nonNull)
                        .map(filter -> safe(filter.label()) + ": " + safe(filter.value()))
                        .collect(Collectors.joining("  |  "));

        Row filterRow = sheet.createRow(1);
        filterRow.setHeightInPoints(38);
        setMergedValue(
                sheet,
                1,
                0,
                HEADERS.length - 1,
                filterText,
                styles.filter()
        );

        int totalOrderCount = groups.stream().mapToInt(GroupRow::orderCount).sum();
        String summaryText = "배송 묶음: " + groups.size() + "개"
                + "  |  포함 오더: " + totalOrderCount + "건"
                + "  |  생성일시: " + LocalDateTime.now(KOREA_ZONE).format(GENERATED_AT_FORMATTER);

        Row summaryRow = sheet.createRow(2);
        summaryRow.setHeightInPoints(22);
        setMergedValue(
                sheet,
                2,
                0,
                HEADERS.length - 1,
                summaryText,
                styles.summary()
        );
    }

    private void writeHeader(Sheet sheet, ExcelStyles styles) {
        Row header = sheet.createRow(3);
        header.setHeightInPoints(24);

        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(styles.header());
        }
    }

    private void writeRows(Sheet sheet, List<GroupRow> groups, ExcelStyles styles) {
        int rowIndex = 4;
        int sequence = 1;

        for (GroupRow group : groups) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(resolveRowHeight(group));

            String productText = limitExcelText(group.orders().stream()
                    .map(this::buildOrderProductText)
                    .collect(Collectors.joining("\n")));

            String memoText = limitExcelText(group.orders().stream()
                    .map(OrderRow::adminMemo)
                    .filter(this::isMeaningful)
                    .distinct()
                    .collect(Collectors.joining("\n")));

            setCell(row, 0, String.valueOf(sequence++), styles.center());
            setCell(row, 1, safe(group.orderIdsText()), styles.wrap());
            setCell(row, 2, safe(group.companyName()), styles.wrap());
            setCell(row, 3, safe(group.requesterNames()), styles.wrap());
            setCell(row, 4, safe(group.createdDateText()), styles.center());
            setCell(row, 5, safe(group.deliveryDateText()), styles.center());
            setCell(row, 6, safe(group.deliveryMethodName()), styles.center());
            setCell(row, 7, safe(group.handlerNames()), styles.wrap());
            setNumericCell(row, 8, group.orderCount(), styles.centerNumber());
            setNumericCell(row, 9, group.totalQuantity(), styles.centerNumber());
            setCell(row, 10, safe(group.statusLabel()), styles.wrapCenter());
            setCell(row, 11, safe(group.address()), styles.wrap());
            setCell(row, 12, productText, styles.wrap());
            setCell(row, 13, memoText.isBlank() ? "-" : memoText, styles.wrap());
            setNumericCell(row, 14, group.imageCount(), styles.centerNumber());
        }
    }

    private String buildOrderProductText(OrderRow order) {
        StringBuilder text = new StringBuilder();
        text.append("[").append(order.orderId()).append("] ")
                .append(safe(order.productName()))
                .append(" / ").append(safe(order.size()))
                .append(" / ").append(safe(order.color()))
                .append(" / 수량 ").append(order.quantity());

        if (isMeaningful(order.optionText())) {
            text.append(" / ").append(order.optionText());
        }

        return text.toString();
    }

    private float resolveRowHeight(GroupRow group) {
        int orderLines = Math.max(1, group.orderCount());
        int addressLines = estimateLines(group.address(), 34);
        int memoLines = Math.max(1, group.orders().stream()
                .map(OrderRow::adminMemo)
                .filter(this::isMeaningful)
                .mapToInt(value -> estimateLines(value, 34))
                .sum());
        int lineCount = Math.max(orderLines, Math.max(addressLines, memoLines));
        return Math.min(400.0f, Math.max(48.0f, 16.0f + (lineCount * 15.0f)));
    }

    private int estimateLines(String value, int charactersPerLine) {
        String text = safe(value);
        if (text.isBlank()) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(text.length() / (double) charactersPerLine));
    }

    private ExcelStyles createStyles(XSSFWorkbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setFontName("맑은 고딕");
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 15);

        Font headerFont = workbook.createFont();
        headerFont.setFontName("맑은 고딕");
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 10);
        headerFont.setColor(IndexedColors.WHITE.getIndex());

        Font bodyFont = workbook.createFont();
        bodyFont.setFontName("맑은 고딕");
        bodyFont.setFontHeightInPoints((short) 9);

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle filter = workbook.createCellStyle();
        filter.setFont(bodyFont);
        filter.setAlignment(HorizontalAlignment.LEFT);
        filter.setVerticalAlignment(VerticalAlignment.CENTER);
        filter.setWrapText(true);
        setBorders(filter);

        CellStyle summary = workbook.createCellStyle();
        summary.cloneStyleFrom(filter);
        summary.setAlignment(HorizontalAlignment.RIGHT);

        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);
        header.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorders(header);

        CellStyle wrap = workbook.createCellStyle();
        wrap.setFont(bodyFont);
        wrap.setAlignment(HorizontalAlignment.LEFT);
        wrap.setVerticalAlignment(VerticalAlignment.CENTER);
        wrap.setWrapText(true);
        setBorders(wrap);

        CellStyle center = workbook.createCellStyle();
        center.cloneStyleFrom(wrap);
        center.setAlignment(HorizontalAlignment.CENTER);

        CellStyle wrapCenter = workbook.createCellStyle();
        wrapCenter.cloneStyleFrom(wrap);
        wrapCenter.setAlignment(HorizontalAlignment.CENTER);

        CellStyle centerNumber = workbook.createCellStyle();
        centerNumber.cloneStyleFrom(center);
        centerNumber.setDataFormat(workbook.createDataFormat().getFormat("0;-0;0"));

        return new ExcelStyles(title, filter, summary, header, wrap, center, wrapCenter, centerNumber);
    }

    private void configurePrintArea(XSSFWorkbook workbook, Sheet sheet, int dataRowCount) {
        int lastRow = 4 + Math.max(0, dataRowCount - 1);
        workbook.setPrintArea(
                workbook.getSheetIndex(sheet),
                0,
                HEADERS.length - 1,
                0,
                lastRow
        );
    }

    private void setMergedValue(
            Sheet sheet,
            int rowIndex,
            int firstColumn,
            int lastColumn,
            String value,
            CellStyle style
    ) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }

        for (int column = firstColumn; column <= lastColumn; column++) {
            Cell cell = row.getCell(column);
            if (cell == null) {
                cell = row.createCell(column);
            }
            cell.setCellStyle(style);
        }

        row.getCell(firstColumn).setCellValue(value == null ? "" : value);
        if (lastColumn > firstColumn) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, firstColumn, lastColumn));
        }
    }

    private void setCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void setNumericCell(Row row, int column, int value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private String limitExcelText(String value) {
        String text = safe(value);
        return text.length() <= 32_000 ? text : text.substring(0, 32_000) + "…";
    }

    private boolean isMeaningful(String value) {
        return value != null && !value.isBlank() && !"-".equals(value.trim());
    }

    private String safe(Object value) {
        return value == null
                ? ""
                : String.valueOf(value)
                        .replace("\r", " ")
                        .replace("\t", " ")
                        .replaceAll(" {2,}", " ")
                        .trim();
    }

    private record ExcelStyles(
            CellStyle title,
            CellStyle filter,
            CellStyle summary,
            CellStyle header,
            CellStyle wrap,
            CellStyle center,
            CellStyle wrapCenter,
            CellStyle centerNumber
    ) {
    }
}
