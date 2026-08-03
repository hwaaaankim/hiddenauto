package com.dev.HiddenBATHAuto.service.amount;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.amount.AmountItemMasterSyncResponse;
import com.dev.HiddenBATHAuto.dto.amount.AmountItemMasterSyncResponse.UnmatchedItem;
import com.dev.HiddenBATHAuto.handler.AmountItemMasterSyncValidationException;
import com.dev.HiddenBATHAuto.model.amount.AmountItemMaster;
import com.dev.HiddenBATHAuto.repository.amount.AmountItemMasterSyncRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AmountItemMasterSyncService {

	/**
	 * A열: 제품코드
	 */
	private static final int ITEM_CODE_COLUMN_INDEX = 0;

	/**
	 * B열: 거울재단 여부
	 */
	private static final int MIRROR_CUTTING_COLUMN_INDEX = 1;

	/**
	 * C열: 규격 여부
	 */
	private static final int STANDARD_COLUMN_INDEX = 2;

	/**
	 * 매우 많은 제품코드를 한 번에 IN 절에 전달하지 않도록 500개씩 나누어 조회합니다.
	 */
	private static final int QUERY_CHUNK_SIZE = 500;

	private final AmountItemMasterSyncRepository amountItemMasterSyncRepository;

	/**
	 * 엑셀 값을 기준으로 기존 AmountItemMaster를 동기화합니다.
	 *
	 * @param file           엑셀 파일
	 * @param sheetIndex     읽을 시트 번호. 첫 번째 시트는 0
	 * @param headerRowCount 위에서부터 건너뛸 행 개수
	 */
	@Transactional
	public AmountItemMasterSyncResponse synchronize(MultipartFile file, int sheetIndex, int headerRowCount) {

		validateRequest(file, sheetIndex, headerRowCount);

		ParsedExcel parsedExcel = parseExcel(file, sheetIndex, headerRowCount);

		Map<String, List<AmountItemMaster>> masterMap = findMasterMap(new ArrayList<>(parsedExcel.itemMap().keySet()));

		int matchedItemCodeCount = 0;
		int updatedEntityCount = 0;
		int unchangedEntityCount = 0;

		List<UnmatchedItem> unmatchedItems = new ArrayList<>();

		for (Map.Entry<String, ExcelItemValue> entry : parsedExcel.itemMap().entrySet()) {

			String itemCode = entry.getKey();
			ExcelItemValue excelValue = entry.getValue();

			List<AmountItemMaster> matchedMasters = masterMap.get(itemCode);

			if (matchedMasters == null || matchedMasters.isEmpty()) {

				unmatchedItems.add(new UnmatchedItem(itemCode, excelValue.excelRowNumber()));

				continue;
			}

			matchedItemCodeCount++;

			/*
			 * DB에 동일 item_code가 여러 건 있으면 해당되는 모든 레코드를 동일하게 업데이트합니다.
			 */
			for (AmountItemMaster master : matchedMasters) {

				boolean changed = master.isMirrorCuttingProduct() != excelValue.mirrorCuttingProduct()
						|| master.isStandard() != excelValue.standard();

				if (!changed) {
					unchangedEntityCount++;
					continue;
				}

				master.setMirrorCuttingProduct(excelValue.mirrorCuttingProduct());

				master.setStandard(excelValue.standard());

				updatedEntityCount++;
			}
		}

		/*
		 * 현재 트랜잭션 안에서 조회한 엔티티는 영속 상태이므로 save()를 개별 호출할 필요는 없습니다.
		 *
		 * 여기서 flush하여 SQL 실행 중 오류가 있으면 API 응답 전에 확인되도록 합니다.
		 */
		amountItemMasterSyncRepository.flush();

		return new AmountItemMasterSyncResponse(file.getOriginalFilename(), parsedExcel.sheetName(),
				parsedExcel.scannedRowCount(), parsedExcel.itemMap().size(), matchedItemCodeCount, updatedEntityCount,
				unchangedEntityCount, unmatchedItems.size(), parsedExcel.duplicateExcelRowCount(),
				List.copyOf(unmatchedItems));
	}

	private void validateRequest(MultipartFile file, int sheetIndex, int headerRowCount) {

		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("업로드할 엑셀 파일이 없습니다.");
		}

		if (sheetIndex < 0) {
			throw new IllegalArgumentException("sheetIndex는 0 이상이어야 합니다.");
		}

		if (headerRowCount < 0) {
			throw new IllegalArgumentException("headerRowCount는 0 이상이어야 합니다.");
		}
	}

	private ParsedExcel parseExcel(MultipartFile file, int sheetIndex, int headerRowCount) {

		try (InputStream inputStream = file.getInputStream();

				Workbook workbook = WorkbookFactory.create(inputStream)) {

			if (workbook.getNumberOfSheets() == 0) {
				throw new IllegalArgumentException("엑셀 파일에 시트가 없습니다.");
			}

			if (sheetIndex >= workbook.getNumberOfSheets()) {
				throw new IllegalArgumentException(
						"sheetIndex가 엑셀 시트 개수를 초과했습니다. " + "전체 시트 수=" + workbook.getNumberOfSheets());
			}

			Sheet sheet = workbook.getSheetAt(sheetIndex);

			DataFormatter dataFormatter = new DataFormatter(Locale.KOREA);

			FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();

			Map<String, ExcelItemValue> itemMap = new LinkedHashMap<>();

			List<String> validationErrors = new ArrayList<>();

			int scannedRowCount = 0;
			int duplicateExcelRowCount = 0;

			/*
			 * headerRowCount=1: 첫 번째 행을 제목 행으로 보고 두 번째 행부터 처리합니다.
			 *
			 * headerRowCount=0: 첫 번째 행부터 데이터로 처리합니다.
			 */
			int firstDataRowIndex = sheet.getFirstRowNum() + headerRowCount;

			int lastRowIndex = sheet.getLastRowNum();

			for (int rowIndex = firstDataRowIndex; rowIndex <= lastRowIndex; rowIndex++) {

				Row row = sheet.getRow(rowIndex);

				if (row == null) {
					continue;
				}

				int excelRowNumber = rowIndex + 1;

				String itemCode = readCell(row, ITEM_CODE_COLUMN_INDEX, dataFormatter, formulaEvaluator);

				String mirrorCuttingText = readCell(row, MIRROR_CUTTING_COLUMN_INDEX, dataFormatter, formulaEvaluator);

				String standardText = readCell(row, STANDARD_COLUMN_INDEX, dataFormatter, formulaEvaluator);

				/*
				 * A, B, C열이 전부 비어 있는 행은 단순 공백 행으로 보고 건너뜁니다.
				 */
				if (itemCode.isBlank() && mirrorCuttingText.isBlank() && standardText.isBlank()) {
					continue;
				}

				scannedRowCount++;

				if (itemCode.isBlank()) {

					validationErrors.add(excelRowNumber + "행 A열 제품코드가 비어 있습니다.");

					continue;
				}

				Boolean mirrorCuttingProduct = parseMirrorCuttingProduct(mirrorCuttingText, excelRowNumber,
						validationErrors);

				Boolean standard = parseStandard(standardText, excelRowNumber, validationErrors);

				if (mirrorCuttingProduct == null || standard == null) {
					continue;
				}

				ExcelItemValue newValue = new ExcelItemValue(itemCode, mirrorCuttingProduct, standard, excelRowNumber);

				ExcelItemValue previousValue = itemMap.get(itemCode);

				if (previousValue == null) {
					itemMap.put(itemCode, newValue);

					continue;
				}

				duplicateExcelRowCount++;

				/*
				 * 동일 제품코드가 중복되어도 값이 같으면 첫 번째 행만 사용합니다.
				 *
				 * 값이 서로 다르면 어떤 값을 적용할지 임의로 결정하지 않고 전체 업로드를 중단합니다.
				 */
				boolean sameValue = previousValue.mirrorCuttingProduct() == newValue.mirrorCuttingProduct()
						&& previousValue.standard() == newValue.standard();

				if (!sameValue) {

					validationErrors.add(excelRowNumber + "행 제품코드 [" + itemCode + "]는 " + previousValue.excelRowNumber()
							+ "행과 값이 다르게 중복되어 있습니다.");
				}
			}

			if (!validationErrors.isEmpty()) {
				throw new AmountItemMasterSyncValidationException(validationErrors);
			}

			if (itemMap.isEmpty()) {
				throw new IllegalArgumentException("업데이트할 유효한 데이터가 없습니다.");
			}

			return new ParsedExcel(sheet.getSheetName(), scannedRowCount, duplicateExcelRowCount, itemMap);

		} catch (EncryptedDocumentException e) {

			throw new IllegalArgumentException("암호가 설정된 엑셀 파일은 처리할 수 없습니다.", e);

		} catch (IOException e) {

			throw new IllegalArgumentException("엑셀 파일을 읽는 중 오류가 발생했습니다.", e);
		}
	}

	private Map<String, List<AmountItemMaster>> findMasterMap(List<String> itemCodes) {

		Map<String, List<AmountItemMaster>> result = new HashMap<>();

		for (int start = 0; start < itemCodes.size(); start += QUERY_CHUNK_SIZE) {

			int end = Math.min(start + QUERY_CHUNK_SIZE, itemCodes.size());

			List<String> chunk = itemCodes.subList(start, end);

			List<AmountItemMaster> masters = amountItemMasterSyncRepository.findAllByItemCodeIn(chunk);

			for (AmountItemMaster master : masters) {

				if (master.getItemCode() == null) {
					continue;
				}

				result.computeIfAbsent(master.getItemCode(), ignored -> new ArrayList<>()).add(master);
			}
		}

		return result;
	}

	private String readCell(Row row, int columnIndex, DataFormatter dataFormatter, FormulaEvaluator formulaEvaluator) {

		if (row.getCell(columnIndex) == null) {
			return "";
		}

		String value = dataFormatter.formatCellValue(row.getCell(columnIndex), formulaEvaluator);

		if (value == null) {
			return "";
		}

		/*
		 * 일반 공백과 NBSP를 정리합니다.
		 */
		return value.replace('\u00A0', ' ').trim();
	}

	private Boolean parseMirrorCuttingProduct(String value, int excelRowNumber, List<String> validationErrors) {

		/*
		 * B열 빈칸은 false입니다.
		 */
		if (value == null || value.isBlank()) {
			return false;
		}

		/*
		 * 사용자가 지정한 값인 'ㅇ'만 true로 처리합니다.
		 */
		if ("ㅇ".equals(value)) {
			return true;
		}

		validationErrors.add(excelRowNumber + "행 B열 거울재단 여부 값이 올바르지 않습니다. " + "허용값: 빈칸 또는 ㅇ, 현재값=[" + value + "]");

		return null;
	}

	private Boolean parseStandard(String value, int excelRowNumber, List<String> validationErrors) {

		if ("규격".equals(value)) {
			return true;
		}

		if ("비규격".equals(value)) {
			return false;
		}

		validationErrors.add(excelRowNumber + "행 C열 규격 여부 값이 올바르지 않습니다. " + "허용값: 규격 또는 비규격, 현재값=[" + value + "]");

		return null;
	}

	private record ExcelItemValue(String itemCode, boolean mirrorCuttingProduct, boolean standard, int excelRowNumber) {
	}

	private record ParsedExcel(String sheetName, int scannedRowCount, int duplicateExcelRowCount,
			Map<String, ExcelItemValue> itemMap) {
	}
}