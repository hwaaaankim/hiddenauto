package com.dev.HiddenBATHAuto.rag.service;

import java.util.UUID;

public record RagAgentToolContext(
        UUID runId,
        UUID projectId,
        UUID versionId,
        UUID sessionId,
        String sourceScope,
        boolean forceSave,
        int turnNo,
        String responseId
) {
}
