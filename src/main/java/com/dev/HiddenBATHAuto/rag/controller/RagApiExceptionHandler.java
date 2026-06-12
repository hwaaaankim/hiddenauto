package com.dev.HiddenBATHAuto.rag.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.dev.HiddenBATHAuto.rag.controller")
public class RagApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> serverError(Exception e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, e);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, Exception e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("status", status.value());
        body.put("message", e.getMessage());
        return ResponseEntity.status(status).body(body);
    }
}
