package com.dev.HiddenBATHAuto.controller.api.productmaster;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.dev.HiddenBATHAuto.dto.productmaster.ProductMasterDtos.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice(assignableTypes = {
        ProductAttributeApiController.class,
        ProductAttributeImageApiController.class,
        ProductMasterApiController.class,
        ProductMasterAutomationApiController.class,
        ProductPublicConfigurationApiController.class
})
public class ProductMasterApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleConflict(RuntimeException exception) {
        String message = exception instanceof ObjectOptimisticLockingFailureException
                ? "다른 사용자가 먼저 정보를 변경했습니다. 새로고침 후 다시 시도해 주세요."
                : exception.getMessage();
        return error(HttpStatus.CONFLICT, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? error.getField() : error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining(" / "));
        return error(HttpStatus.BAD_REQUEST, message.isBlank() ? "입력값을 확인해 주세요." : message);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "요청값 또는 선택값의 형식이 올바르지 않습니다.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "업로드 이미지 용량이 서버 허용 한도를 초과했습니다.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException exception) {
        return error(HttpStatus.BAD_REQUEST, "이미지 업로드 요청을 읽을 수 없습니다. 파일을 다시 선택해 주세요.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("제품관리 데이터 무결성 충돌", exception);
        return error(HttpStatus.CONFLICT, "이미 사용 중인 코드·이름이거나 다른 데이터가 참조 중입니다.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("제품관리 처리 중 예기치 않은 오류가 발생했습니다.", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "제품관리 처리 중 오류가 발생했습니다.");
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        String safeMessage = message == null || message.isBlank()
                ? status.getReasonPhrase()
                : message.trim();
        return ResponseEntity.status(status).body(ApiResponse.fail(safeMessage));
    }
}
