package com.dev.HiddenBATHAuto.service.calculate.excel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowBasePriceOne;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowBasePriceThree;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowBasePriceTwo;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowBodyOnly;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowDrawer;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowHandlePrice;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowLengthPrice;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowOptionPrice;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowType;
import com.dev.HiddenBATHAuto.model.calculate.marble.MarbleLowWash;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowBasePriceOneRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowBasePriceThreeRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowBasePriceTwoRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowBodyOnlyRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowDrawerRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowHandlePriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowLengthPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowOptionPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowTypeRepository;
import com.dev.HiddenBATHAuto.repository.caculate.marble.MarbleLowWashRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MarbleLowCalculateExcelService {

	private final MarbleLowBasePriceOneRepository baseOneRepo;
	private final MarbleLowBasePriceTwoRepository baseTwoRepo;
	private final MarbleLowBasePriceThreeRepository baseThreeRepo;
	private final MarbleLowDrawerRepository drawerRepo;
	private final MarbleLowWashRepository washRepo;
	private final MarbleLowBodyOnlyRepository bodyRepo;
	private final MarbleLowHandlePriceRepository handleRepo;
	private final MarbleLowOptionPriceRepository optionRepo;
	private final MarbleLowTypeRepository typeRepo;
	private final MarbleLowLengthPriceRepository lengthRepo;

	public void uploadExcel(InputStream excelInputStream) throws Exception {
		Workbook workbook = new XSSFWorkbook(excelInputStream);

		for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
			Sheet sheet = workbook.getSheetAt(i);
			String sheetName = sheet.getSheetName();
			Iterator<Row> rows = sheet.iterator();
			if (!rows.hasNext()) continue;
			rows.next(); // skip header

			switch (sheetName) {
				case "기본가격_1단가" -> baseOneRepo.deleteAll();
				case "기본가격_2단가" -> baseTwoRepo.deleteAll();
				case "기본가격_3단가" -> baseThreeRepo.deleteAll();
				case "서랍" -> drawerRepo.deleteAll();
				case "세면대" -> washRepo.deleteAll();
				case "바디만" -> bodyRepo.deleteAll();
				case "손잡이" -> handleRepo.deleteAll();
				case "기타옵션" -> optionRepo.deleteAll();
				case "대리석" -> typeRepo.deleteAll();
				case "일자대리석" -> lengthRepo.deleteAll();
			}

			switch (sheetName) {
				case "기본가격_1단가" -> baseOneRepo.saveAll(parseBasePriceOne(rows));
				case "기본가격_2단가" -> baseTwoRepo.saveAll(parseBasePriceTwo(rows));
				case "기본가격_3단가" -> baseThreeRepo.saveAll(parseBasePriceThree(rows));
				case "서랍" -> drawerRepo.saveAll(parseDrawer(rows));
				case "세면대" -> washRepo.saveAll(parseWash(rows));
				case "바디만" -> bodyRepo.saveAll(parseBody(rows));
				case "손잡이" -> handleRepo.saveAll(parseHandle(rows));
				case "기타옵션" -> optionRepo.saveAll(parseOption(rows));
				case "대리석" -> typeRepo.saveAll(parseType(rows));
				case "일자대리석" -> lengthRepo.saveAll(parseLength(rows));
			}
		}
		workbook.close();
	}

	private List<MarbleLowBasePriceOne> parseBasePriceOne(Iterator<Row> rows) {
		List<MarbleLowBasePriceOne> list = new ArrayList<>();
		while (rows.hasNext()) {
			Row r = rows.next();
			if (isRowEmpty(r, 1, 2, 3, 4)) continue;
			MarbleLowBasePriceOne item = new MarbleLowBasePriceOne();
			item.setStandardWidth((int) getCellNumericValue(r, 1));
			item.setPrice500((int) getCellNumericValue(r, 2));
			item.setPrice600((int) getCellNumericValue(r, 3));
			item.setPrice700((int) getCellNumericValue(r, 4));
			list.add(item);
		}
		return list;
	}

	private List<MarbleLowBasePriceTwo> parseBasePriceTwo(Iterator<Row> rows) {
		List<MarbleLowBasePriceTwo> list = new ArrayList<>();
		while (rows.hasNext()) {
			Row r = rows.next();
			if (isRowEmpty(r, 1, 2, 3, 4)) continue;
			MarbleLowBasePriceTwo item = new MarbleLowBasePriceTwo();
			item.setStandardWidth((int) getCellNumericValue(r, 1));
			item.setPrice500((int) getCellNumericValue(r, 2));
			item.setPrice600((int) getCellNumericValue(r, 3));
			item.setPrice700((int) getCellNumericValue(r, 4));
			list.add(item);
		}
		return list;
	}

	private List<MarbleLowBasePriceThree> parseBasePriceThree(Iterator<Row> rows) {
		List<MarbleLowBasePriceThree> list = new ArrayList<>();
		while (rows.hasNext()) {
			Row r = rows.next();
			if (isRowEmpty(r, 1, 2, 3, 4)) continue;
			MarbleLowBasePriceThree item = new MarbleLowBasePriceThree();
			item.setStandardWidth((int) getCellNumericValue(r, 1));
			item.setPrice500((int) getCellNumericValue(r, 2));
			item.setPrice600((int) getCellNumericValue(r, 3));
			item.setPrice700((int) getCellNumericValue(r, 4));
			list.add(item);
		}
		return list;
	}

	private List<MarbleLowDrawer> parseDrawer(Iterator<Row> rows) {
		List<MarbleLowDrawer> list = new ArrayList<>();
		while (rows.hasNext()) {
			Row r = rows.next();
			if (isRowEmpty(r, 1, 2, 3)) continue;
			MarbleLowDrawer item = new MarbleLowDrawer();
			item.setStandardWidth((int) getCellNumericValue(r, 1));
			item.setUnder600((int) getCellNumericValue(r, 2));
			item.setOver600((int) getCellNumericValue(r, 3));
			list.add(item);
		}
		return list;
	}

	private List<MarbleLowWash> parseWash(Iterator<Row> rows) {
		List<MarbleLowWash> list = new ArrayList<>();
		while (rows.hasNext()) {
			Row r = rows.next();
			if (isRowEmpty(r, 1, 2, 3)) continue;
			MarbleLowWash item = new MarbleLowWash();
			item.setStandardWidth((int) getCellNumericValue(r, 1));
			item.setPrice((int) getCellNumericValue(r, 2));
			list.add(item);
		}
		return list;
	}

	private List<MarbleLowBodyOnly> parseBody(Iterator<Row> rows) {
		List<MarbleLowBodyOnly> list = new ArrayList<>();
		while (rows.hasNext()) {
			Row r = rows.next();
			if (isRowEmpty(r, 1, 2)) continue;
			MarbleLowBodyOnly item = new MarbleLowBodyOnly();
			item.setStandardWidth((int) getCellNumericValue(r, 1));
			item.setPrice((int) getCellNumericValue(r, 2));
			list.add(item);
		}
		return list;
	}

	private List<MarbleLowHandlePrice> parseHandle(Iterator<Row> rows) {
		List<MarbleLowHandlePrice> list = new ArrayList<>();
		while (rows.hasNext()) {
			Row r = rows.next();
			if (isRowEmpty(r, 1, 2)) continue;
			MarbleLowHandlePrice item = new MarbleLowHandlePrice();
			item.setHandleType(getCellStringValue(r, 1));
			item.setPrice((int) getCellNumericValue(r, 2));
			list.add(item);
		}
		return list;
	}

	private List<MarbleLowOptionPrice> parseOption(Iterator<Row> rows) {
		List<MarbleLowOptionPrice> list = new ArrayList<>();
		while (rows.hasNext()) {
			Row r = rows.next();
			if (isRowEmpty(r, 1, 2)) continue;
			MarbleLowOptionPrice item = new MarbleLowOptionPrice();
			item.setOptionName(getCellStringValue(r, 1));
			item.setPrice((int) getCellNumericValue(r, 2));
			list.add(item);
		}
		return list;
	}

	private List<MarbleLowType> parseType(Iterator<Row> rows) {
		List<MarbleLowType> list = new ArrayList<>();
		while (rows.hasNext()) {
			Row r = rows.next();
			if (isRowEmpty(r, 1, 2)) continue;
			MarbleLowType item = new MarbleLowType();
			item.setMarbleName(getCellStringValue(r, 1));
			item.setUnitPrice((int) getCellNumericValue(r, 2));
			list.add(item);
		}
		return list;
	}

	private List<MarbleLowLengthPrice> parseLength(Iterator<Row> rows) {
		List<MarbleLowLengthPrice> list = new ArrayList<>();
		while (rows.hasNext()) {
			Row r = rows.next();
			if (isRowEmpty(r, 1, 2, 3, 4, 5)) continue;
			MarbleLowLengthPrice item = new MarbleLowLengthPrice();
			item.setStandardWidth((int) getCellNumericValue(r, 1));
			item.setPrice1((int) getCellNumericValue(r, 2));
			item.setPrice2((int) getCellNumericValue(r, 3));
			item.setPrice3((int) getCellNumericValue(r, 4));
			item.setAdditionalFee((int) getCellNumericValue(r, 5));
			list.add(item);
		}
		return list;
	}

	private boolean isRowEmpty(Row row, int... colIndices) {
		for (int col : colIndices) {
			Cell cell = row.getCell(col);
			if (cell != null && cell.getCellType() != CellType.BLANK) {
				String value = cell.toString().trim();
				if (!value.isEmpty()) return false;
			}
		}
		return true;
	}

	private double getCellNumericValue(Row row, int colIndex) {
		Cell cell = row.getCell(colIndex);
		if (cell == null) return 0;
		return switch (cell.getCellType()) {
			case NUMERIC -> cell.getNumericCellValue();
			case STRING -> Double.parseDouble(cell.getStringCellValue());
			default -> 0;
		};
	}

	private String getCellStringValue(Row row, int colIndex) {
		Cell cell = row.getCell(colIndex);
		if (cell == null) return "";
		return switch (cell.getCellType()) {
			case STRING -> cell.getStringCellValue();
			case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
			default -> "";
		};
	}
}