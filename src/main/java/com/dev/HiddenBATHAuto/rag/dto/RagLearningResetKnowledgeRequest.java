package com.dev.HiddenBATHAuto.rag.dto;

import java.util.UUID;

public record RagLearningResetKnowledgeRequest(
        UUID projectId,
        UUID versionId,
        UUID sessionId,
        String topic,
        String reason,
        Boolean resetWholeVersion
) {}
