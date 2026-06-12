package com.dev.HiddenBATHAuto.rag.dto;

import java.util.UUID;

public record RagLearningConversationStartRequest(
        UUID projectId,
        UUID versionId,
        String title,
        String topic
) {}
