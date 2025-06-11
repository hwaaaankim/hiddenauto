package com.dev.HiddenBATHAuto.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelConvertService {

    public File processAndGenerate(MultipartFile file) throws IOException {
        // 1. 엑셀 로드
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet productSheet = workbook.getSheet("제품정보");
        Sheet sizeSheet = workbook.getSheet("사이즈정보");
        Sheet colorSheet = workbook.getSheet("제품색상정보");

        Map<String, String[]> sizeMap = new HashMap<>();
        for (Row row : sizeSheet) {
            if (row.getRowNum() == 0) continue;
            String id = getString(row.getCell(0));
            String title = getString(row.getCell(2));
            String code = getString(row.getCell(1));
            sizeMap.put(id, new String[]{title, code});
        }

        Map<String, String[]> colorMap = new HashMap<>();
        for (Row row : colorSheet) {
            if (row.getRowNum() == 0) continue;
            String id = getString(row.getCell(0));
            String title = getString(row.getCell(3));
            String code = getString(row.getCell(1));
            colorMap.put(id, new String[]{title, code});
        }

        // 2. 결과 워크북 생성
        Workbook outputWb = new XSSFWorkbook();
        Sheet outputSheet = outputWb.createSheet("결과");

        // 헤더
        String[] headers = {"제품명", "제품코드", "사이즈 TITLE", "사이즈ID", "사이즈고유코드", "색상 TITLE", "색상ID", "색상고유코드", "기준가격"};
        Row headerRow = outputSheet.createRow(0);
        for (int i = 0; i < headers.length; i++) headerRow.createCell(i).setCellValue(headers[i]);

        int rowIdx = 1;
        for (Row row : productSheet) {
            if (row.getRowNum() == 0) continue;

            String productCode = getString(row.getCell(0));
            String productName = getString(row.getCell(1));
            String sizeCodes = getString(row.getCell(2));
            String colorCodes = getString(row.getCell(3));

            String[] sizeIds = sizeCodes != null ? sizeCodes.split(",") : new String[]{null};
            String[] colorIds = colorCodes != null ? colorCodes.split(",") : new String[]{null};

            for (String sizeId : sizeIds) {
                String[] sizeInfo = sizeMap.get(sizeId);
                String sizeTitle = sizeInfo != null ? sizeInfo[0] : null;
                String sizeCode = sizeInfo != null ? sizeInfo[1] : null;

                for (String colorId : colorIds) {
                    String[] colorInfo = colorMap.get(colorId);
                    String colorTitle = colorInfo != null ? colorInfo[0] : null;
                    String colorCode = colorInfo != null ? colorInfo[1] : null;

                    Row outRow = outputSheet.createRow(rowIdx++);
                    outRow.createCell(0).setCellValue(productName);
                    outRow.createCell(1).setCellValue(productCode);
                    outRow.createCell(2).setCellValue(sizeTitle);
                    outRow.createCell(3).setCellValue(sizeId);
                    outRow.createCell(4).setCellValue(sizeCode);
                    outRow.createCell(5).setCellValue(colorTitle);
                    outRow.createCell(6).setCellValue(colorId);
                    outRow.createCell(7).setCellValue(colorCode);
                    outRow.createCell(8).setCellValue("");  // 기준가격 비워둠
                }
            }
        }

        // 3. 파일 저장
        File outFile = File.createTempFile("converted_", ".xlsx");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            outputWb.write(fos);
        }
        return outFile;
    }

    private String getString(Cell cell) {
        return cell == null ? null : cell.toString().trim();
    }
}

