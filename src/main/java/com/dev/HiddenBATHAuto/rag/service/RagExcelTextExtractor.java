package com.dev.HiddenBATHAuto.rag.service;

import java.io.InputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class RagExcelTextExtractor {

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("엑셀 파일이 비어 있습니다.");
        }
        DataFormatter formatter = new DataFormatter();
        StringBuilder sb = new StringBuilder();
        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            sb.append("[EXCEL_FILE] ").append(file.getOriginalFilename()).append('\n');
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                sb.append("\n[SHEET] ").append(sheet.getSheetName()).append('\n');
                int lastRow = sheet.getLastRowNum();
                for (int r = sheet.getFirstRowNum(); r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    int lastCell = Math.max(row.getLastCellNum(), 0);
                    boolean hasValue = false;
                    StringBuilder line = new StringBuilder();
                    line.append("ROW ").append(r + 1).append(" | ");
                    for (int c = 0; c < lastCell; c++) {
                        Cell cell = row.getCell(c);
                        String value = formatter.formatCellValue(cell).trim();
                        if (!value.isBlank()) hasValue = true;
                        line.append(columnName(c)).append('=').append(value.replace("\n", " ")).append(" | ");
                    }
                    if (hasValue) sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("엑셀 파싱 실패: " + e.getMessage(), e);
        }
    }

    private String columnName(int index) {
        StringBuilder sb = new StringBuilder();
        int i = index;
        do {
            sb.insert(0, (char) ('A' + (i % 26)));
            i = i / 26 - 1;
        } while (i >= 0);
        return sb.toString();
    }
}
