package com.dev.HiddenBATHAuto.rag.dto;

import java.util.UUID;

/**
 * 기존 호출부 호환용 학습 세션 시작 요청 DTO입니다.
 * 신규 대화형 학습 DTO(RagLearningConversationStartRequest)와 함께 사용 가능합니다.
 */
public record RagLearningSessionStartRequest(
        UUID projectId,
        UUID versionId,
        String title,
        String topic,
        String domain
) {}
