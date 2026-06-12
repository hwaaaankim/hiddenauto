package com.dev.HiddenBATHAuto.rag.dto;

/**
 * 기존 학습 메시지 API 호환용 DTO입니다.
 * 화면/컨트롤러 버전에 따라 message, content, text, learningText 중 하나로 들어와도 처리됩니다.
 */
public record RagLearningMessageRequest(
        String message,
        String content,
        String text,
        String learningText,
        Boolean forceSave
) {}
