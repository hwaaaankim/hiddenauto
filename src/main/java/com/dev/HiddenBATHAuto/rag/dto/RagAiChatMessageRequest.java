package com.dev.HiddenBATHAuto.rag.dto;

import java.util.UUID;

public record RagAiChatMessageRequest(UUID sessionId, String message) {}
