package com.dev.HiddenBATHAuto.service.calculate.excel;

import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorStandardPrice;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorStandardPriceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MirrorStandardPriceExcelService {

    private final MirrorStandardPriceRepository repository;

    public void uploadStandardPriceExcel(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);

            repository.deleteAll(); // 기존 데이터 삭제 후 재삽입

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || row.getCell(0) == null) continue;

                MirrorStandardPrice entity = new MirrorStandardPrice();
                entity.setProductName(getString(row.getCell(0)));
                entity.setPriceLedOff(getInt(row.getCell(1)));
                entity.setPriceLedOn(getInt(row.getCell(2)));
                repository.save(entity);
            }
        }
    }

    private String getString(Cell cell) {
        return (cell == null) ? "" : cell.toString().trim();
    }

    private int getInt(Cell cell) {
        if (cell == null) return 0;
        return (int) cell.getNumericCellValue();
    }
}
