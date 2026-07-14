package com.dev.HiddenBATHAuto.rag.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.dev.HiddenBATHAuto.rag.controller")
public class RagApiExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(RagApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(
            IllegalArgumentException error
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                error
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> serverError(
            Exception error
    ) {
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "RAG_API_ERROR",
                error
        );
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status,
            String code,
            Exception error
    ) {
        String traceId = UUID.randomUUID().toString();

        if (status.is5xxServerError()) {
            log.error(
                    "RAG API 처리 오류. traceId={}, code={}",
                    traceId,
                    code,
                    error
            );
        } else {
            log.warn(
                    "RAG API 요청 오류. traceId={}, code={}, message={}",
                    traceId,
                    code,
                    error.getMessage()
            );
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("handled", true);
        body.put("status", status.value());
        body.put("responseType", "TECHNICAL_ERROR");
        body.put("answerSource", "NONE");
        body.put("traceId", traceId);
        body.put(
                "systemError",
                Map.of(
                        "code", code,
                        "message",
                        status == HttpStatus.BAD_REQUEST
                                ? "요청 형식 또는 필수값을 확인해 주세요."
                                : "RAG 요청 처리 중 시스템 오류가 발생했습니다.",
                        "detailAvailableInServerLog", true
                )
        );

        // 내부 예외문은 assistant 답변이나 공개 message에 넣지 않습니다.
        return ResponseEntity.status(status).body(body);
    }
}
