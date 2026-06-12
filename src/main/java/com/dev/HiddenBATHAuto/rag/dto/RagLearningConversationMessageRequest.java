package com.dev.HiddenBATHAuto.rag.dto;

public record RagLearningConversationMessageRequest(
        String message,
        Boolean forceSave
) {}
