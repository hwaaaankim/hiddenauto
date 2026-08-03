package com.dev.HiddenBATHAuto.controller.amount;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.amount.AmountItemMasterSyncResponse;
import com.dev.HiddenBATHAuto.handler.AmountItemMasterSyncValidationException;
import com.dev.HiddenBATHAuto.service.amount.AmountItemMasterSyncService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/excel/amount-item-master")
@ConditionalOnProperty(name = "feature.amount-item-sync.enabled", havingValue = "true")
public class AmountItemMasterSyncController {

	private final AmountItemMasterSyncService amountItemMasterSyncService;

	/**
	 * POST /api/excel/amount-item-master/sync
	 *
	 * multipart/form-data
	 *
	 * file: 엑셀 파일 sheetIndex: 시트 번호, 기본값 0 headerRowCount: 위에서부터 건너뛸 행 개수, 기본값 1
	 */
	@PostMapping(value = "/sync", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<AmountItemMasterSyncResponse> synchronize(@RequestParam("file") MultipartFile file,

			@RequestParam(name = "sheetIndex", defaultValue = "0") int sheetIndex,

			@RequestParam(name = "headerRowCount", defaultValue = "1") int headerRowCount) {

		AmountItemMasterSyncResponse response = amountItemMasterSyncService.synchronize(file, sheetIndex,
				headerRowCount);

		return ResponseEntity.ok(response);
	}

	@ExceptionHandler(AmountItemMasterSyncValidationException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(AmountItemMasterSyncValidationException e) {

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage(), e.getDetails()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {

		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage(), List.of()));
	}

	public record ErrorResponse(String message, List<String> details) {
	}
}