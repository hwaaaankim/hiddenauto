package com.dev.HiddenBATHAuto.service.calculate.excel;

import java.io.InputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.calculate.flap.FlapBasicPrice;
import com.dev.HiddenBATHAuto.model.calculate.flap.FlapHandlePrice;
import com.dev.HiddenBATHAuto.model.calculate.flap.FlapOptionPrice;
import com.dev.HiddenBATHAuto.repository.caculate.flap.FlapBasicPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.flap.FlapHandlePriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.flap.FlapOptionPriceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FlapExcelUploadService {

    private final FlapBasicPriceRepository flapBasicPriceRepository;
    private final FlapHandlePriceRepository flapHandlePriceRepository;
    private final FlapOptionPriceRepository flapOptionPriceRepository;

    public void uploadFlapExcel(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            // 0. 전체 삭제
            flapBasicPriceRepository.deleteAll();
            flapHandlePriceRepository.deleteAll();
            flapOptionPriceRepository.deleteAll();

            // 1. 기본가격 시트
            Sheet baseSheet = workbook.getSheet("기본가격");
            for (int i = 1; i <= baseSheet.getLastRowNum(); i++) {
                Row row = baseSheet.getRow(i);
                if (row == null || isEmptyCell(row.getCell(0))) continue;

                String name = getStringValue(row.getCell(0));
                int price = getIntValue(row.getCell(1));

                FlapBasicPrice entity = new FlapBasicPrice();
                entity.setProductName(name);
                entity.setBasicPrice(price);
                flapBasicPriceRepository.save(entity);
            }

            // 2. 손잡이 시트
            Sheet handleSheet = workbook.getSheet("손잡이");
            for (int i = 1; i <= handleSheet.getLastRowNum(); i++) {
                Row row = handleSheet.getRow(i);
                if (row == null || isEmptyCell(row.getCell(1))) continue;

                String handleName = getStringValue(row.getCell(1));
                int price = getIntValue(row.getCell(2));

                FlapHandlePrice entity = new FlapHandlePrice();
                entity.setHandleName(handleName);
                entity.setPrice(price);
                flapHandlePriceRepository.save(entity);
            }

            // 3. 기타옵션 시트
            Sheet optionSheet = workbook.getSheet("기타옵션");
            for (int i = 1; i <= optionSheet.getLastRowNum(); i++) {
                Row row = optionSheet.getRow(i);
                if (row == null || isEmptyCell(row.getCell(1))) continue;

                String optionName = getStringValue(row.getCell(1));
                int price = getIntValue(row.getCell(2));

                FlapOptionPrice entity = new FlapOptionPrice();
                entity.setOptionName(optionName);
                entity.setPrice(price);
                flapOptionPriceRepository.save(entity);
            }
        }
    }

    private String getStringValue(Cell cell) {
        if (cell == null) return "";
        return cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : String.valueOf((int) cell.getNumericCellValue());
    }

    private int getIntValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return 0;
        return (int) cell.getNumericCellValue();
    }

    private boolean isEmptyCell(Cell cell) {
        return cell == null || cell.getCellType() == CellType.BLANK || getStringValue(cell).trim().isEmpty();
    }
}

