package com.dev.HiddenBATHAuto.rag.service;

import java.util.Map;

public record RagUploadedKnowledgeDocument(
        String originalFilename,
        String contentType,
        String sourceType,
        String rawText,
        Map<String, Object> metadata
) {}
