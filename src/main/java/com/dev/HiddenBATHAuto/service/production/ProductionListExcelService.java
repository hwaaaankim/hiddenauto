package com.dev.HiddenBATHAuto.service.production;

import java.time.LocalDate;
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

@Service
public class ProductionListExcelService {

    public Workbook createProductionListWorkbook(List<ProductionListExcelRowDto> rows) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("생산 제작 목록");

        applyPrintSetting(workbook, sheet, rows);

        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle infoStyle = createInfoStyle(workbook);
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle bodyStyle = createBodyStyle(workbook);
        CellStyle centerStyle = createCenterStyle(workbook);

        createTitleRows(sheet, titleStyle, infoStyle, rows);
        createHeaderRow(sheet, headerStyle);
        createBodyRows(sheet, bodyStyle, centerStyle, rows);

        return workbook;
    }

    private void applyPrintSetting(Workbook workbook, Sheet sheet, List<ProductionListExcelRowDto> rows) {
        sheet.setMargin(PageMargin.LEFT, 0.25D);
        sheet.setMargin(PageMargin.RIGHT, 0.25D);
        sheet.setMargin(PageMargin.TOP, 0.35D);
        sheet.setMargin(PageMargin.BOTTOM, 0.35D);

        PrintSetup printSetup = sheet.getPrintSetup();
        printSetup.setLandscape(true);
        printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
        printSetup.setFitWidth((short) 1);
        printSetup.setFitHeight((short) 0);

        sheet.setFitToPage(true);
        sheet.setAutobreaks(true);
        sheet.setHorizontallyCenter(true);

        sheet.setColumnWidth(0, 10 * 256); // 오더ID
        sheet.setColumnWidth(1, 26 * 256); // 제품명
        sheet.setColumnWidth(2, 18 * 256); // 제품색상
        sheet.setColumnWidth(3, 30 * 256); // 제품사이즈
        sheet.setColumnWidth(4, 8 * 256);  // 수량
        sheet.setColumnWidth(5, 60 * 256); // 남김말
        sheet.setColumnWidth(6, 16 * 256); // 카테고리
        sheet.setColumnWidth(7, 12 * 256); // 체크상태

        sheet.createFreezePane(0, 3);

        int lastRow = Math.max(3, (rows == null ? 0 : rows.size()) + 2);

        /*
         * 0열: 오더ID
         * 7열: 체크상태
         */
        workbook.setPrintArea(0, 0, 7, 0, lastRow);
    }

    private void createTitleRows(
            Sheet sheet,
            CellStyle titleStyle,
            CellStyle infoStyle,
            List<ProductionListExcelRowDto> rows
    ) {
        Row titleRow = sheet.createRow(0);
        titleRow.setHeightInPoints(28F);

        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("생산팀 제작 목록");
        titleCell.setCellStyle(titleStyle);

        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

        Row infoRow = sheet.createRow(1);
        infoRow.setHeightInPoints(20F);

        Cell infoCell = infoRow.createCell(0);
        infoCell.setCellValue("출력일: " + LocalDate.now() + " / 현재 화면 기준 " + safeSize(rows) + "건");
        infoCell.setCellStyle(infoStyle);

        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 7));
    }

    private void createHeaderRow(Sheet sheet, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(2);
        headerRow.setHeightInPoints(24F);

        String[] headers = {
                "오더ID",
                "제품명",
                "제품색상",
                "제품사이즈",
                "수량",
                "남김말",
                "카테고리",
                "체크상태"
        };

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void createBodyRows(
            Sheet sheet,
            CellStyle bodyStyle,
            CellStyle centerStyle,
            List<ProductionListExcelRowDto> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            Row row = sheet.createRow(3);
            row.setHeightInPoints(28F);

            Cell cell = row.createCell(0);
            cell.setCellValue("조회된 생산 주문이 없습니다.");
            cell.setCellStyle(bodyStyle);

            sheet.addMergedRegion(new CellRangeAddress(3, 3, 0, 7));
            return;
        }

        int rowIndex = 3;

        for (ProductionListExcelRowDto dto : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.setHeightInPoints(estimateRowHeight(dto.getAdminMemo()));

            setCell(row, 0, dto.getOrderId() == null ? "-" : String.valueOf(dto.getOrderId()), centerStyle);
            setCell(row, 1, text(dto.getProductName()), bodyStyle);
            setCell(row, 2, text(dto.getProductColor()), bodyStyle);
            setCell(row, 3, text(dto.getProductSize()), bodyStyle);
            setCell(row, 4, dto.getQuantity() == null ? "-" : String.valueOf(dto.getQuantity()), centerStyle);
            setCell(row, 5, text(dto.getAdminMemo()), bodyStyle);
            setCell(row, 6, text(dto.getCategoryName()), bodyStyle);
            setCell(row, 7, resolveCheckStateLabel(dto), centerStyle);
        }
    }

    private String resolveCheckStateLabel(ProductionListExcelRowDto dto) {
        if (dto == null) {
            return "-";
        }

        String label = text(dto.getCheckStateLabel());

        if (!"-".equals(label)) {
            return label;
        }

        String state = text(dto.getCheckState());

        if ("CHECKED".equalsIgnoreCase(state)) {
            return "확인";
        }

        if ("REVISED_AFTER_CHECK".equalsIgnoreCase(state)) {
            return "재수정";
        }

        if ("UNCHECKED".equalsIgnoreCase(state)) {
            return "미확인";
        }

        return "-";
    }

    private void setCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 17);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    private CellStyle createInfoStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);

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

    private CellStyle createBodyStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        applyBorder(style);

        return style;
    }

    private CellStyle createCenterStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);

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

    private float estimateRowHeight(String memo) {
        String text = text(memo);

        if ("-".equals(text)) {
            return 24F;
        }

        int lineCount = text.split("\\R", -1).length;
        int lengthBasedLineCount = Math.max(1, text.length() / 34);

        int estimatedLines = Math.max(lineCount, lengthBasedLineCount);

        return Math.min(95F, Math.max(28F, estimatedLines * 16F));
    }

    private int safeSize(List<?> rows) {
        return rows == null ? 0 : rows.size();
    }

    private String text(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }

        return value
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
    }
}