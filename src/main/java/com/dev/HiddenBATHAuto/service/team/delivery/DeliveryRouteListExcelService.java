package com.dev.HiddenBATHAuto.service.team.delivery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Footer;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.dto.delivery.route.DeliveryRouteDtos.PrintRow;

/**
 * 업체별 배송 화면의 일반 목록형 엑셀 및 인쇄 공통 데이터 전용 서비스입니다.
 *
 * 다른 화면에서 사용하는 DeliveryExcelService는 수정하지 않습니다.
 * 이 서비스는 기존 DeliveryExcelService의 10개 열 구성과 순서를 기준으로 하며,
 * 업체별 배송 화면의 엑셀과 바로 인쇄가 같은 정규화 데이터를 사용하도록 합니다.
 */
@Service
public class DeliveryRouteListExcelService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private static final DateTimeFormatter GENERATED_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final Pattern SIGNED_INTEGER_PATTERN = Pattern.compile("[-+]?\\d+");

    private static final String DELIVERY_METHOD_FILTER = "직배송 · 현장배송 · 화물";
    private static final String STATUS_FILTER = "승인완료 · 생산완료 · 출고완료 · 배송완료";
    private static final String DISPLAY_ORDER_FILTER = "현재 화면 표시 순서";

    private static final int COLUMN_COUNT = 10;
    private static final int LAST_COLUMN_INDEX = COLUMN_COUNT - 1;
    private static final int HEADER_ROW_INDEX = 2;
    private static final int FIRST_DATA_ROW_INDEX = 3;

    private static final String[] HEADERS = {
            "거래처명",
            "품목",
            "규격(사이즈)",
            "색상",
            "수량",
            "비고",
            "단위",
            "배송수단",
            "담당자",
            "주소"
    };

    private static final int[] COLUMN_WIDTHS = {
            18,
            22,
            19,
            15,
            7,
            28,
            10,
            12,
            12,
            36
    };

    /**
     * 엑셀과 바로 인쇄가 함께 사용하는 출력 데이터입니다.
     */
    public DeliveryRouteListOutput buildOutput(
            LocalDate deliveryDate,
            String handlerName,
            List<PrintRow> sourceRows
    ) {
        if (deliveryDate == null) {
            throw new IllegalArgumentException("출력할 배송일이 없습니다.");
        }

        List<PrintRow> safeSourceRows = sourceRows == null
                ? List.of()
                : sourceRows.stream()
                        .filter(Objects::nonNull)
                        .toList();

        if (safeSourceRows.isEmpty()) {
            throw new IllegalArgumentException("출력할 배송 주문이 없습니다.");
        }

        String normalizedHandlerName = valueOrDash(handlerName);
        String generatedAt = LocalDateTime.now(KOREA_ZONE).format(GENERATED_AT_FORMATTER);

        List<DeliveryRouteListRow> rows = safeSourceRows.stream()
                .map(row -> new DeliveryRouteListRow(
                        valueOrDash(row.getCompanyName()),
                        valueOrDash(row.getProductName()),
                        valueOrDash(row.getSize()),
                        valueOrDash(row.getColor()),
                        resolveSignedQuantity(row.getQuantityText()),
                        valueOrDash(row.getAdminMemo()),
                        valueOrDash(row.getCategory()),
                        valueOrDash(row.getDeliveryMethodName()),
                        normalizedHandlerName,
                        valueOrDash(row.getAddress())
                ))
                .toList();

        return new DeliveryRouteListOutput(
                deliveryDate,
                normalizedHandlerName,
                rows.size(),
                generatedAt,
                DELIVERY_METHOD_FILTER,
                STATUS_FILTER,
                DISPLAY_ORDER_FILTER,
                rows
        );
    }

    public byte[] buildExcel(DeliveryRouteListOutput output) {
        if (output == null || output.rows().isEmpty()) {
            throw new IllegalArgumentException("엑셀로 출력할 배송 주문이 없습니다.");
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("배송리스트");
            ExcelStyles styles = createStyles(workbook);

            configureSheet(sheet);
            writeTitleAndFilters(sheet, output, styles);
            writeTable(sheet, output.rows(), styles);
            configurePrintArea(workbook, sheet, output.rows().size());

            workbook.setActiveSheet(0);
            workbook.write(outputStream);
            return outputStream.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("업체별 배송리스트 엑셀 생성 중 오류가 발생했습니다.", e);
        }
    }

    private void configureSheet(Sheet sheet) {
        sheet.setFitToPage(true);
        sheet.setAutobreaks(true);
        sheet.setHorizontallyCenter(true);
        sheet.setVerticallyCenter(false);
        sheet.setDisplayGridlines(false);
        sheet.setPrintGridlines(false);
        sheet.createFreezePane(0, FIRST_DATA_ROW_INDEX);
        sheet.setRepeatingRows(new CellRangeAddress(0, HEADER_ROW_INDEX, -1, -1));

        for (int columnIndex = 0; columnIndex < COLUMN_WIDTHS.length; columnIndex++) {
            sheet.setColumnWidth(columnIndex, COLUMN_WIDTHS[columnIndex] * 256);
        }

        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setLandscape(true);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);

        sheet.setMargin(PageMargin.LEFT, 0.25);
        sheet.setMargin(PageMargin.RIGHT, 0.25);
        sheet.setMargin(PageMargin.TOP, 0.35);
        sheet.setMargin(PageMargin.BOTTOM, 0.35);
        sheet.setMargin(PageMargin.HEADER, 0.0);
        sheet.setMargin(PageMargin.FOOTER, 0.15);

        Footer footer = sheet.getFooter();
        footer.setLeft("HiddenBath Auto · 배송팀");
        footer.setRight("Page &P / &N");
    }

    private void writeTitleAndFilters(
            Sheet sheet,
            DeliveryRouteListOutput output,
            ExcelStyles styles
    ) {
        Row titleRow = getOrCreateRow(sheet, 0);
        titleRow.setHeightInPoints(25);

        setMergedValue(
                sheet,
                0,
                0,
                LAST_COLUMN_INDEX,
                "배송리스트  "
                        + output.deliveryDate()
                        + "  /  담당자: "
                        + output.handlerName(),
                styles.title()
        );

        Row filterRow = getOrCreateRow(sheet, 1);
        filterRow.setHeightInPoints(31);

        String filterText = "조회조건"
                + "  |  배송일: " + output.deliveryDate()
                + "  |  담당자: " + output.handlerName()
                + "  |  배송수단: " + output.deliveryMethodFilter()
                + "  |  상태: " + output.statusFilter()
                + "  |  표시순서: " + output.displayOrderFilter()
                + "  |  총 주문: " + output.totalOrderCount() + "건"
                + "  |  생성: " + output.generatedAt();

        setMergedValue(
                sheet,
                1,
                0,
                LAST_COLUMN_INDEX,
                filterText,
                styles.filter()
        );
    }

    private void writeTable(
            Sheet sheet,
            List<DeliveryRouteListRow> rows,
            ExcelStyles styles
    ) {
        Row headerRow = getOrCreateRow(sheet, HEADER_ROW_INDEX);
        headerRow.setHeightInPoints(22);

        for (int columnIndex = 0; columnIndex < HEADERS.length; columnIndex++) {
            setCellValue(
                    headerRow,
                    columnIndex,
                    HEADERS[columnIndex],
                    styles.header()
            );
        }

        int excelRowIndex = FIRST_DATA_ROW_INDEX;

        for (DeliveryRouteListRow rowData : rows) {
            Row row = getOrCreateRow(sheet, excelRowIndex++);
            row.setHeightInPoints(resolveBodyRowHeight(rowData));

            setCellValue(row, 0, rowData.companyName(), styles.body());
            setCellValue(row, 1, rowData.productName(), styles.body());
            setCellValue(row, 2, rowData.size(), styles.body());
            setCellValue(row, 3, rowData.color(), styles.bodyCenter());
            setNumericCellValue(row, 4, rowData.quantity(), styles.quantity());
            setCellValue(row, 5, rowData.memo(), styles.body());
            setCellValue(row, 6, rowData.unit(), styles.bodyCenter());
            setCellValue(row, 7, rowData.deliveryMethodName(), styles.bodyCenter());
            setCellValue(row, 8, rowData.handlerName(), styles.bodyCenter());
            setCellValue(row, 9, rowData.address(), styles.body());
        }
    }

    private void configurePrintArea(
            XSSFWorkbook workbook,
            Sheet sheet,
            int dataRowCount
    ) {
        int lastRowIndex = FIRST_DATA_ROW_INDEX + Math.max(0, dataRowCount - 1);

        workbook.setPrintArea(
                workbook.getSheetIndex(sheet),
                0,
                LAST_COLUMN_INDEX,
                0,
                lastRowIndex
        );
    }

    private ExcelStyles createStyles(XSSFWorkbook workbook) {
        Font titleFont = workbook.createFont();
        titleFont.setFontName("맑은 고딕");
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);

        Font filterFont = workbook.createFont();
        filterFont.setFontName("맑은 고딕");
        filterFont.setFontHeightInPoints((short) 9);

        Font headerFont = workbook.createFont();
        headerFont.setFontName("맑은 고딕");
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 10);

        Font bodyFont = workbook.createFont();
        bodyFont.setFontName("맑은 고딕");
        bodyFont.setFontHeightInPoints((short) 9);

        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);
        title.setWrapText(true);

        CellStyle filter = workbook.createCellStyle();
        filter.setFont(filterFont);
        filter.setAlignment(HorizontalAlignment.LEFT);
        filter.setVerticalAlignment(VerticalAlignment.CENTER);
        filter.setWrapText(true);
        setAllBorders(filter);

        CellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);
        setAllBorders(header);

        CellStyle body = workbook.createCellStyle();
        body.setFont(bodyFont);
        body.setAlignment(HorizontalAlignment.LEFT);
        // 기존 공용 엑셀의 TOP 정렬을 업체별 배송 전용 출력에서는 중앙정렬로 변경합니다.
        body.setVerticalAlignment(VerticalAlignment.CENTER);
        body.setWrapText(true);
        setAllBorders(body);

        CellStyle bodyCenter = workbook.createCellStyle();
        bodyCenter.cloneStyleFrom(body);
        bodyCenter.setAlignment(HorizontalAlignment.CENTER);

        CellStyle quantity = workbook.createCellStyle();
        quantity.cloneStyleFrom(bodyCenter);
        quantity.setDataFormat(workbook.createDataFormat().getFormat("0;-0;0"));

        return new ExcelStyles(title, filter, header, body, bodyCenter, quantity);
    }

    private float resolveBodyRowHeight(DeliveryRouteListRow row) {
        int maxLineCount = Math.max(
                estimateWrappedLines(row.productName(), 20),
                Math.max(
                        estimateWrappedLines(row.memo(), 26),
                        estimateWrappedLines(row.address(), 34)
                )
        );

        return Math.max(46.0f, 16.0f + (maxLineCount * 14.0f));
    }

    private int estimateWrappedLines(String value, int charactersPerLine) {
        String text = safe(value);

        if (text.isBlank()) {
            return 1;
        }

        int safeCharactersPerLine = Math.max(8, charactersPerLine);
        int lineCount = 0;

        for (String line : text.split("\\n", -1)) {
            int length = Math.max(1, line.length());
            lineCount += (int) Math.ceil(length / (double) safeCharactersPerLine);
        }

        return Math.max(1, lineCount);
    }

    private int resolveSignedQuantity(String quantityText) {
        String text = safe(quantityText).replace(",", "");
        Matcher matcher = SIGNED_INTEGER_PATTERN.matcher(text);

        if (!matcher.find()) {
            return 0;
        }

        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setMergedValue(
            Sheet sheet,
            int rowIndex,
            int firstColumn,
            int lastColumn,
            String value,
            CellStyle style
    ) {
        Row row = getOrCreateRow(sheet, rowIndex);

        for (int columnIndex = firstColumn; columnIndex <= lastColumn; columnIndex++) {
            Cell cell = getOrCreateCell(row, columnIndex);
            cell.setCellStyle(style);
        }

        Cell firstCell = getOrCreateCell(row, firstColumn);
        firstCell.setCellValue(value == null ? "" : value);

        if (lastColumn > firstColumn) {
            sheet.addMergedRegion(new CellRangeAddress(
                    rowIndex,
                    rowIndex,
                    firstColumn,
                    lastColumn
            ));
        }
    }

    private void setCellValue(
            Row row,
            int columnIndex,
            String value,
            CellStyle style
    ) {
        Cell cell = getOrCreateCell(row, columnIndex);
        cell.setCellStyle(style);
        cell.setCellValue(value == null ? "" : value);
    }

    private void setNumericCellValue(
            Row row,
            int columnIndex,
            int value,
            CellStyle style
    ) {
        Cell cell = getOrCreateCell(row, columnIndex);
        cell.setCellStyle(style);
        cell.setCellValue(value);
    }

    private Row getOrCreateRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row != null ? row : sheet.createRow(rowIndex);
    }

    private Cell getOrCreateCell(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        return cell != null ? cell : row.createCell(columnIndex);
    }

    private static void setAllBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private String valueOrDash(Object value) {
        String text = safe(value);
        return text.isBlank() ? "-" : text;
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

    public record DeliveryRouteListOutput(
            LocalDate deliveryDate,
            String handlerName,
            int totalOrderCount,
            String generatedAt,
            String deliveryMethodFilter,
            String statusFilter,
            String displayOrderFilter,
            List<DeliveryRouteListRow> rows
    ) {
        public DeliveryRouteListOutput {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record DeliveryRouteListRow(
            String companyName,
            String productName,
            String size,
            String color,
            int quantity,
            String memo,
            String unit,
            String deliveryMethodName,
            String handlerName,
            String address
    ) {
    }

    private record ExcelStyles(
            CellStyle title,
            CellStyle filter,
            CellStyle header,
            CellStyle body,
            CellStyle bodyCenter,
            CellStyle quantity
    ) {
    }
}
