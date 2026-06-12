package com.dev.HiddenBATHAuto.rag.dto;

public record RagConversationLearningMessageRequest(
        String message,
        String attachmentText,
        String attachmentFilename,
        Boolean forceSave
) {}
