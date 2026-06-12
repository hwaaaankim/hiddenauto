package com.dev.HiddenBATHAuto.rag.dto;

import java.util.UUID;

public record RagAiChatStartRequest(UUID projectId, String userLabel) {}
