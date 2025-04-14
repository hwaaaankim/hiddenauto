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

import com.dev.HiddenBATHAuto.model.calculate.low.BasePrice;
import com.dev.HiddenBATHAuto.model.calculate.low.BodyOnly;
import com.dev.HiddenBATHAuto.model.calculate.low.Drawer;
import com.dev.HiddenBATHAuto.model.calculate.low.HandlePrice;
import com.dev.HiddenBATHAuto.model.calculate.low.MarbleLengthPrice;
import com.dev.HiddenBATHAuto.model.calculate.low.MarbleType;
import com.dev.HiddenBATHAuto.model.calculate.low.OptionPrice;
import com.dev.HiddenBATHAuto.model.calculate.low.SeriesPrice;
import com.dev.HiddenBATHAuto.model.calculate.low.WashPrice;
import com.dev.HiddenBATHAuto.repository.caculate.low.BasePriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.BodyOnlyRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.DrawerRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.HandlePriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.MarbleLengthPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.MarbleTypeRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.OptionPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.SeriesPriceRepository;
import com.dev.HiddenBATHAuto.repository.caculate.low.WashPriceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LowCalculateExcelService {

	private final BasePriceRepository baseRepo;
	private final DrawerRepository drawerRepo;
	private final SeriesPriceRepository seriesRepo;
	private final BodyOnlyRepository bodyRepo;
	private final HandlePriceRepository handleRepo;
	private final WashPriceRepository washRepo;
	private final OptionPriceRepository optionRepo;
	private final MarbleTypeRepository marbleTypeRepo;
	private final MarbleLengthPriceRepository marbleLengthRepo;

	public void uploadExcel(InputStream excelInputStream) throws Exception {
		Workbook workbook = new XSSFWorkbook(excelInputStream);

		// 전체 삭제
		baseRepo.deleteAll();
		drawerRepo.deleteAll();
		seriesRepo.deleteAll();
		bodyRepo.deleteAll();
		handleRepo.deleteAll();
		washRepo.deleteAll();
		optionRepo.deleteAll();
		marbleTypeRepo.deleteAll();
		marbleLengthRepo.deleteAll();

		for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
			Sheet sheet = workbook.getSheetAt(i);
			String sheetName = sheet.getSheetName();
			Iterator<Row> rows = sheet.iterator();
			if (!rows.hasNext())
				continue;
			rows.next(); // 헤더 스킵

			switch (sheetName) {
			case "기본가격" -> {
				List<BasePrice> list = new ArrayList<>();
				while (rows.hasNext()) {
					Row r = rows.next();
					if (isEmptyRow(r))
						continue;
					BasePrice item = new BasePrice();
					item.setStandardWidth(getIntValue(r, 1));
					item.setPrice460(getIntValue(r, 2));
					item.setPrice560(getIntValue(r, 3));
					item.setPrice620(getIntValue(r, 4));
					item.setPrice700(getIntValue(r, 5));
					list.add(item);
				}
				baseRepo.saveAll(list);
			}
			case "서랍" -> {
				List<Drawer> list = new ArrayList<>();
				while (rows.hasNext()) {
					Row r = rows.next();
					if (isEmptyRow(r))
						continue;
					Drawer item = new Drawer();
					item.setStandardWidth(getIntValue(r, 1));
					item.setUnder600(getIntValue(r, 2));
					item.setOver600(getIntValue(r, 3));
					list.add(item);
				}
				drawerRepo.saveAll(list);
			}
			case "시리즈" -> {
				List<SeriesPrice> list = new ArrayList<>();
				while (rows.hasNext()) {
					Row r = rows.next();
					if (isEmptyRow(r))
						continue;
					SeriesPrice item = new SeriesPrice();
					item.setStandardWidth(getIntValue(r, 1));
					item.setPremium(getIntValue(r, 2));
					item.setRound(getIntValue(r,3));
					item.setSlide(getIntValue(r, 4));
					list.add(item);
				}
				seriesRepo.saveAll(list);
			}
			case "바디만" -> {
				List<BodyOnly> list = new ArrayList<>();
				while (rows.hasNext()) {
					Row r = rows.next();
					if (isEmptyRow(r))
						continue;
					BodyOnly item = new BodyOnly();
					item.setStandardWidth(getIntValue(r, 1));
					item.setPrice(getIntValue(r, 2));
					list.add(item);
				}
				bodyRepo.saveAll(list);
			}
			case "손잡이" -> {
				List<HandlePrice> list = new ArrayList<>();
				while (rows.hasNext()) {
					Row r = rows.next();
					if (isEmptyRow(r))
						continue;
					HandlePrice item = new HandlePrice();
					item.setHandleType(getStringValue(r, 1));
					item.setPrice(getIntValue(r, 2));
					list.add(item);
				}
				handleRepo.saveAll(list);
			}
			case "세면대" -> {
				List<WashPrice> list = new ArrayList<>();
				while (rows.hasNext()) {
					Row r = rows.next();
					if (isEmptyRow(r))
						continue;
					WashPrice item = new WashPrice();
					item.setBasinType(getStringValue(r, 1));
					item.setBasePrice(getIntValue(r, 2));
					item.setAdditionalFee(getIntValue(r, 3));
					list.add(item);
				}
				washRepo.saveAll(list);
			}
			case "기타옵션" -> {
				List<OptionPrice> list = new ArrayList<>();
				while (rows.hasNext()) {
					Row r = rows.next();
					if (isEmptyRow(r))
						continue;
					OptionPrice item = new OptionPrice();
					item.setOptionName(getStringValue(r, 1));
					item.setPrice(getIntValue(r, 2));
					list.add(item);
				}
				optionRepo.saveAll(list);
			}
			case "대리석분류" -> {
				List<MarbleType> list = new ArrayList<>();
				while (rows.hasNext()) {
					Row r = rows.next();
					if (isEmptyRow(r))
						continue;
					MarbleType item = new MarbleType();
					item.setMarbleName(getStringValue(r, 1));
					item.setUnitPrice(getIntValue(r, 2));
					list.add(item);
				}
				marbleTypeRepo.saveAll(list);
			}
			case "일자대리석" -> {
				List<MarbleLengthPrice> list = new ArrayList<>();
				while (rows.hasNext()) {
					Row r = rows.next();
					if (isEmptyRow(r))
						continue;
					MarbleLengthPrice item = new MarbleLengthPrice();
					item.setStandardWidth(getIntValue(r, 1));
					item.setPrice1(getIntValue(r, 2));
					item.setPrice2(getIntValue(r, 3));
					item.setPrice3(getIntValue(r, 4));
					item.setAdditionalFee(getIntValue(r, 5));
					list.add(item);
				}
				marbleLengthRepo.saveAll(list);
			}
			}
		}
		workbook.close();
	}

	private int getIntValue(Row row, int index) {
		try {
			Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
			if (cell == null) {
				return 0;
			}

			CellType type = cell.getCellType();
			int result;

			switch (type) {
			case STRING -> {
				String val = cell.getStringCellValue().trim();
				result = val.isEmpty() ? 0 : Integer.parseInt(val);
			}
			case NUMERIC, FORMULA -> result = (int) cell.getNumericCellValue();
			default -> result = 0;
			}

			return result;
		} catch (Exception e) {
			return 0;
		}
	}

	private String getStringValue(Row row, int index) {
		try {
			Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
			if (cell == null) {
				return "";
			}

			CellType type = cell.getCellType();
			String result;

			switch (type) {
			case STRING -> result = cell.getStringCellValue().trim();
			case NUMERIC -> result = String.valueOf((int) cell.getNumericCellValue());
			case FORMULA -> result = cell.getStringCellValue().trim();
			default -> result = "";
			}

			return result;
		} catch (Exception e) {
			return "";
		}
	}

	private boolean isEmptyRow(Row row) {
		for (int i = 0; i < row.getLastCellNum(); i++) {
			Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
			if (cell != null && cell.getCellType() != CellType.BLANK)
				return false;
		}
		return true;
	}
}
