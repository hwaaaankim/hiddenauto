package com.dev.HiddenBATHAuto.rag.dto;

import java.util.UUID;

public record RagChatStartRequest(UUID projectId, String userLabel) {}
