package com.dev.HiddenBATHAuto.service.calculate.excel;

import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.calculate.top.TopBasicPrice;
import com.dev.HiddenBATHAuto.model.calculate.top.TopHandlePrice;
import com.dev.HiddenBATHAuto.model.calculate.top.TopOptionPrice;
import com.dev.HiddenBATHAuto.repository.caculate.top.TopBasicPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.top.TopHandlePriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.top.TopOptionPriceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TopExcelUploadService {

	private final TopBasicPriceRepository topBasicPriceRepository;
	private final TopHandlePriceRepository topHandlePriceRepository;
	private final TopOptionPriceRepository topOptionPriceRepository;

	public void uploadTopExcel(MultipartFile file) throws IOException {
		// 기존 데이터 삭제
		topBasicPriceRepository.deleteAll();
		topHandlePriceRepository.deleteAll();
		topOptionPriceRepository.deleteAll();

		try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
			// 1. 기본가격 시트
			Sheet baseSheet = workbook.getSheet("기본가격");
			for (int i = 1; i <= baseSheet.getLastRowNum(); i++) {
				Row row = baseSheet.getRow(i);
				if (row == null)
					continue;

				String name = getStringValue(row.getCell(0)).trim();
				Integer price = getSafeIntValue(row.getCell(1));

				if (name.isEmpty() || price == null || price == 0)
					continue;

				TopBasicPrice entity = new TopBasicPrice();
				entity.setProductName(name);
				entity.setBasicPrice(price);
				topBasicPriceRepository.save(entity);
			}

			// 2. 손잡이 시트
			Sheet handleSheet = workbook.getSheet("손잡이");
			for (int i = 1; i <= handleSheet.getLastRowNum(); i++) {
				Row row = handleSheet.getRow(i);
				if (row == null)
					continue;

				String handleName = getStringValue(row.getCell(1)).trim();
				Integer price = getSafeIntValue(row.getCell(2));

				if (handleName.isEmpty() || price == null || price == 0)
					continue;

				TopHandlePrice entity = new TopHandlePrice();
				entity.setHandleName(handleName);
				entity.setPrice(price);
				topHandlePriceRepository.save(entity);
			}

			// 3. 기타옵션 시트
			Sheet optionSheet = workbook.getSheet("기타옵션");
			for (int i = 1; i <= optionSheet.getLastRowNum(); i++) {
				Row row = optionSheet.getRow(i);
				if (row == null)
					continue;

				String optionName = getStringValue(row.getCell(1)).trim();
				Integer price = getSafeIntValue(row.getCell(2));

				if (optionName.isEmpty() || price == null || price == 0)
					continue;

				TopOptionPrice entity = new TopOptionPrice();
				entity.setOptionName(optionName);
				entity.setPrice(price);
				topOptionPriceRepository.save(entity);
			}
		}
	}

	private String getStringValue(Cell cell) {
		if (cell == null || cell.getCellType() == CellType.BLANK)
			return "";
		return switch (cell.getCellType()) {
		case STRING -> cell.getStringCellValue();
		case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
		default -> "";
		};
	}

	private Integer getSafeIntValue(Cell cell) {
		if (cell == null || cell.getCellType() == CellType.BLANK)
			return null;
		try {
			return (int) cell.getNumericCellValue();
		} catch (Exception e) {
			return null;
		}
	}
}