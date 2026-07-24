package com.dev.HiddenBATHAuto.controller.api.client;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.dto.client.bulk.ClientBulkImportIssue;
import com.dev.HiddenBATHAuto.dto.client.bulk.ClientBulkImportPreviewResponse;
import com.dev.HiddenBATHAuto.dto.client.bulk.ClientBulkImportSaveRequest;
import com.dev.HiddenBATHAuto.dto.client.bulk.ClientBulkImportSaveResponse;
import com.dev.HiddenBATHAuto.service.client.bulk.ClientBulkImportService;
import com.dev.HiddenBATHAuto.service.client.bulk.ClientBulkImportValidationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/management/clientList/excel-import")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGEMENT')")
public class ClientBulkImportController {

    private final ClientBulkImportService clientBulkImportService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClientBulkImportPreviewResponse> preview(
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(clientBulkImportService.preview(file));
    }

    @PostMapping(value = "/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ClientBulkImportSaveResponse> save(
            @RequestBody ClientBulkImportSaveRequest request
    ) {
        return ResponseEntity.ok(clientBulkImportService.save(request));
    }

    @ExceptionHandler(ClientBulkImportValidationException.class)
    public ResponseEntity<ClientBulkImportSaveResponse> handleValidationException(
            ClientBulkImportValidationException e
    ) {
        return ResponseEntity.badRequest().body(
                ClientBulkImportSaveResponse.failure(e.getMessage(), e.getIssues())
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(false, e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalStateException(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(false, e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ClientBulkImportSaveResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        log.warn("대리점 엑셀 일괄등록 DB 제약조건 오류", e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ClientBulkImportSaveResponse.failure(
                        "저장 직전에 동일한 사업자등록번호 또는 멤버 아이디가 등록되었습니다. 기존 데이터는 변경되지 않았습니다.",
                        java.util.List.of(ClientBulkImportIssue.error(
                                "DATABASE_DUPLICATE",
                                "사업자등록번호 또는 대표 멤버 아이디 중복으로 저장하지 못했습니다.",
                                null
                        ))
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnexpectedException(Exception e) {
        log.error("대리점 엑셀 일괄등록 처리 중 예기치 않은 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(false, "대리점 엑셀 일괄등록 처리 중 오류가 발생했습니다."));
    }

    private record ErrorResponse(boolean success, String message) {
    }
}
