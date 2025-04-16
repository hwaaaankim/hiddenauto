package com.dev.HiddenBATHAuto.service.calculate.excel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesEight;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesEleven;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesFive;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesFiveLed;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesFour;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesFourLed;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesNine;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesOne;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesOneLed;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesSeven;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesSix;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesSixLed;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesTen;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesThree;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesThreeLed;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesTwo;
import com.dev.HiddenBATHAuto.model.calculate.mirror.MirrorSeriesTwoLed;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesEightRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesElevenRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesFiveLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesFiveRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesFourLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesFourRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesNineRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesOneLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesOneRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesSevenRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesSixLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesSixRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesTenRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesThreeLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesThreeRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesTwoLedRepository;
import com.dev.HiddenBATHAuto.repository.caculate.mirror.MirrorSeriesTwoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MirrorUnstandardExcelUploadService {

	private final MirrorSeriesOneRepository mirrorSeriesOneRepository;
	private final MirrorSeriesOneLedRepository mirrorSeriesOneLedRepository;
	private final MirrorSeriesTwoRepository mirrorSeriesTwoRepository;
	private final MirrorSeriesTwoLedRepository mirrorSeriesTwoLedRepository;
	private final MirrorSeriesThreeRepository mirrorSeriesThreeRepository;
	private final MirrorSeriesThreeLedRepository mirrorSeriesThreeLedRepository;
	private final MirrorSeriesFourRepository mirrorSeriesFourRepository;
	private final MirrorSeriesFourLedRepository mirrorSeriesFourLedRepository;
	private final MirrorSeriesFiveRepository mirrorSeriesFiveRepository;
	private final MirrorSeriesFiveLedRepository mirrorSeriesFiveLedRepository;
	private final MirrorSeriesSixRepository mirrorSeriesSixRepository;
	private final MirrorSeriesSixLedRepository mirrorSeriesSixLedRepository;
	private final MirrorSeriesSevenRepository mirrorSeriesSevenRepository;
	private final MirrorSeriesEightRepository mirrorSeriesEightRepository;
	private final MirrorSeriesNineRepository mirrorSeriesNineRepository;
	private final MirrorSeriesTenRepository mirrorSeriesTenRepository;
	private final MirrorSeriesElevenRepository mirrorSeriesElevenRepository;

	public void uploadExcel(MultipartFile file) throws Exception {
		// 🔥 1. 기존 데이터 삭제
		clearAllMirrorSeries();

		try (InputStream is = file.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
			for (Sheet sheet : workbook) {
				String sheetName = sheet.getSheetName().trim();
				if (sheet.getLastRowNum() < 1) continue;

				Row header = sheet.getRow(0);
				List<Integer> columnPrices = new ArrayList<>();
				Map<String, Integer> colIndexMap = new HashMap<>();

				for (int i = 2; i < header.getLastCellNum(); i++) {
					String colValue = getSafeCellStringValue(header.getCell(i));
					if (colValue == null || colValue.isBlank()) continue;

					try {
						int colNumber = Integer.parseInt(colValue.trim());
						String key = "price" + colNumber;
						colIndexMap.put(key, i);
						columnPrices.add(colNumber);
					} catch (NumberFormatException e) {
						System.out.println("⚠️ 숫자가 아닌 헤더: " + colValue);
					}
				}

				for (int r = 1; r <= sheet.getLastRowNum(); r++) {
					Row row = sheet.getRow(r);
					if (row == null || row.getCell(1) == null) break;

					int standardWidth = (int) row.getCell(1).getNumericCellValue();
					Map<String, Integer> prices = new HashMap<>();

					for (Map.Entry<String, Integer> entry : colIndexMap.entrySet()) {
						Cell cell = row.getCell(entry.getValue());
						int value = (cell != null && cell.getCellType() == CellType.NUMERIC) ?
							(int) cell.getNumericCellValue() : 0;
						prices.put(entry.getKey(), value);
					}

					saveToRepository(sheetName, standardWidth, prices);
				}
			}
		}
	}

	private void saveToRepository(String sheetName, int standardWidth, Map<String, Integer> prices) {
		switch (sheetName) {
			case "시리즈01" -> mirrorSeriesOneRepository.save(buildMirrorSeries(MirrorSeriesOne.class, standardWidth, prices));
			case "시리즈01_LED추가" -> mirrorSeriesOneLedRepository.save(buildMirrorSeries(MirrorSeriesOneLed.class, standardWidth, prices));
			case "시리즈02" -> mirrorSeriesTwoRepository.save(buildMirrorSeries(MirrorSeriesTwo.class, standardWidth, prices));
			case "시리즈02_LED추가" -> mirrorSeriesTwoLedRepository.save(buildMirrorSeries(MirrorSeriesTwoLed.class, standardWidth, prices));
			case "시리즈03" -> mirrorSeriesThreeRepository.save(buildMirrorSeries(MirrorSeriesThree.class, standardWidth, prices));
			case "시리즈03_LED추가" -> mirrorSeriesThreeLedRepository.save(buildMirrorSeries(MirrorSeriesThreeLed.class, standardWidth, prices));
			case "시리즈04" -> mirrorSeriesFourRepository.save(buildMirrorSeries(MirrorSeriesFour.class, standardWidth, prices));
			case "시리즈04_LED추가" -> mirrorSeriesFourLedRepository.save(buildMirrorSeries(MirrorSeriesFourLed.class, standardWidth, prices));
			case "시리즈05" -> mirrorSeriesFiveRepository.save(buildMirrorSeries(MirrorSeriesFive.class, standardWidth, prices));
			case "시리즈05_LED추가" -> mirrorSeriesFiveLedRepository.save(buildMirrorSeries(MirrorSeriesFiveLed.class, standardWidth, prices));
			case "시리즈06" -> mirrorSeriesSixRepository.save(buildMirrorSeries(MirrorSeriesSix.class, standardWidth, prices));
			case "시리즈06_LED추가" -> mirrorSeriesSixLedRepository.save(buildMirrorSeries(MirrorSeriesSixLed.class, standardWidth, prices));
			case "시리즈07" -> mirrorSeriesSevenRepository.save(buildMirrorSeries(MirrorSeriesSeven.class, standardWidth, prices));
			case "시리즈08" -> mirrorSeriesEightRepository.save(buildMirrorSeries(MirrorSeriesEight.class, standardWidth, prices));
			case "시리즈09" -> mirrorSeriesNineRepository.save(buildMirrorSeries(MirrorSeriesNine.class, standardWidth, prices));
			case "시리즈10" -> mirrorSeriesTenRepository.save(buildMirrorSeries(MirrorSeriesTen.class, standardWidth, prices));
			case "시리즈11" -> mirrorSeriesElevenRepository.save(buildMirrorSeries(MirrorSeriesEleven.class, standardWidth, prices));
			default -> System.out.println("❌ 알 수 없는 시트명: " + sheetName);
		}
	}

	private <T> T buildMirrorSeries(Class<T> clazz, int standardWidth, Map<String, Integer> prices) {
		try {
			T instance = clazz.getDeclaredConstructor().newInstance();
			clazz.getMethod("setStandardWidth", int.class).invoke(instance, standardWidth);

			for (Map.Entry<String, Integer> entry : prices.entrySet()) {
				String methodName = "set" + capitalize(entry.getKey());
				clazz.getMethod(methodName, int.class).invoke(instance, entry.getValue());
			}
			return instance;
		} catch (Exception e) {
			throw new RuntimeException("객체 생성 오류: " + clazz.getSimpleName(), e);
		}
	}

	private String getSafeCellStringValue(Cell cell) {
		if (cell == null) return null;
		return switch (cell.getCellType()) {
			case STRING -> cell.getStringCellValue();
			case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
			default -> null;
		};
	}

	private String capitalize(String str) {
		if (str == null || str.isEmpty()) return str;
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	private void clearAllMirrorSeries() {
		mirrorSeriesOneRepository.deleteAll();
		mirrorSeriesOneLedRepository.deleteAll();
		mirrorSeriesTwoRepository.deleteAll();
		mirrorSeriesTwoLedRepository.deleteAll();
		mirrorSeriesThreeRepository.deleteAll();
		mirrorSeriesThreeLedRepository.deleteAll();
		mirrorSeriesFourRepository.deleteAll();
		mirrorSeriesFourLedRepository.deleteAll();
		mirrorSeriesFiveRepository.deleteAll();
		mirrorSeriesFiveLedRepository.deleteAll();
		mirrorSeriesSixRepository.deleteAll();
		mirrorSeriesSixLedRepository.deleteAll();
		mirrorSeriesSevenRepository.deleteAll();
		mirrorSeriesEightRepository.deleteAll();
		mirrorSeriesNineRepository.deleteAll();
		mirrorSeriesTenRepository.deleteAll();
		mirrorSeriesElevenRepository.deleteAll();
	}
}