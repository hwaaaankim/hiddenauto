package com.dev.HiddenBATHAuto.dto.amount;

import java.util.List;

import com.dev.HiddenBATHAuto.dto.amount.AmountItemMasterSyncResponse.UnmatchedItem;

public record AmountItemMasterSyncResponse(

		String fileName,

		String sheetName,

		/**
		 * A~C열 중 하나라도 값이 존재했던 데이터 행 수입니다.
		 */
		int scannedRowCount,

		/**
		 * 엑셀에 존재한 고유 제품코드 수입니다.
		 */
		int uniqueItemCodeCount,

		/**
		 * DB에서 매칭된 고유 제품코드 수입니다.
		 */
		int matchedItemCodeCount,

		/**
		 * 실제 값이 변경된 DB 엔티티 수입니다.
		 */
		int updatedEntityCount,

		/**
		 * DB 값이 이미 엑셀과 같았던 엔티티 수입니다.
		 */
		int unchangedEntityCount,

		/**
		 * DB에 존재하지 않는 고유 제품코드 수입니다.
		 */
		int unmatchedItemCodeCount,

		/**
		 * 엑셀 내 동일 제품코드 중복 행 수입니다.
		 */
		int duplicateExcelRowCount,

		List<UnmatchedItem> unmatchedItems

) {

	public record UnmatchedItem(String itemCode, int excelRowNumber) {
	}
}