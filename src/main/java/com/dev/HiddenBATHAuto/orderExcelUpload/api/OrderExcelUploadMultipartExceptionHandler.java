package com.dev.HiddenBATHAuto.orderExcelUpload.api;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import com.dev.HiddenBATHAuto.orderExcelUpload.config.OrderExcelUploadImageProperties;
import com.dev.HiddenBATHAuto.orderExcelUpload.dto.OrderExcelErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * multipart 본문 파싱 단계에서 발생하는 예외를 JSON으로 반환합니다.
 * 이 단계의 예외는 Controller 메서드 진입 전에 발생하므로 별도 Advice가 필요합니다.
 */
@Slf4j
@RestControllerAdvice(basePackageClasses = OrderExcelUploadApiController.class)
@RequiredArgsConstructor
public class OrderExcelUploadMultipartExceptionHandler {

    private final OrderExcelUploadImageProperties imageProperties;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<OrderExcelErrorResponse> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Multipart upload size exceeded. uri={}, maxUploadSize={}, message={}",
                request == null ? null : request.getRequestURI(),
                exception.getMaxUploadSize(),
                exception.getMessage()
        );

        String message = "첨부 요청이 서버 multipart 허용 범위를 초과했습니다. "
                + "엑셀 발주 이미지 업무 최대값은 파일당 "
                + formatFileSize(imageProperties.resolvedMaxFileSizeBytes())
                + ", 전체 "
                + formatFileSize(imageProperties.resolvedMaxTotalSizeBytes())
                + "입니다.";

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new OrderExcelErrorResponse(false, message, List.of()));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<OrderExcelErrorResponse> handleMultipartException(
            MultipartException exception,
            HttpServletRequest request
    ) {
        log.error(
                "Multipart request parsing failed. uri={}, message={}",
                request == null ? null : request.getRequestURI(),
                exception.getMessage(),
                exception
        );

        return ResponseEntity.badRequest().body(
                new OrderExcelErrorResponse(
                        false,
                        "첨부 파일 요청을 처리하지 못했습니다. 파일 형식, 용량, 서버 프록시 제한을 확인해 주세요.",
                        List.of()
                )
        );
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.KOREA, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024.0) {
            return String.format(Locale.KOREA, "%.1f MB", mb);
        }
        return String.format(Locale.KOREA, "%.2f GB", mb / 1024.0);
    }
}
