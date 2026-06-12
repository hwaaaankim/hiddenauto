package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;

@Service
public class RagDocumentIngestService {

    private static final int CHUNK_SIZE = 2400;
    private static final int CHUNK_OVERLAP = 250;

    private final RagRepository repository;
    private final OpenAiRagClient openAiClient;

    public RagDocumentIngestService(RagRepository repository, OpenAiRagClient openAiClient) {
        this.repository = repository;
        this.openAiClient = openAiClient;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> ingest(UUID projectId,
                                      UUID versionId,
                                      String topic,
                                      String sourceType,
                                      String title,
                                      String originalFilename,
                                      String rawText,
                                      Map<String, Object> metadata) {
        if (!StringUtils.hasText(rawText)) {
            throw new IllegalArgumentException("학습할 내용이 비어 있습니다.");
        }
        UUID documentId = UUID.randomUUID();
        Map<String, Object> document = repository.insertDocument(
                documentId,
                projectId,
                versionId,
                StringUtils.hasText(topic) ? topic : "GENERAL",
                sourceType,
                StringUtils.hasText(title) ? title : sourceType,
                originalFilename,
                rawText,
                metadata == null ? Map.of() : metadata
        );

        List<String> chunks = split(rawText);
        int no = 1;
        for (String chunk : chunks) {
            List<Double> vector = openAiClient.embedding(chunk);
            repository.insertChunk(
                    UUID.randomUUID(),
                    documentId,
                    projectId,
                    versionId,
                    no++,
                    StringUtils.hasText(topic) ? topic : "GENERAL",
                    chunk,
                    Map.of("sourceType", sourceType, "title", title == null ? "" : title),
                    RagRepository.toVectorLiteral(vector)
            );
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document", document);
        result.put("chunkCount", chunks.size());
        return result;
    }

    public List<Map<String, Object>> retrieve(UUID projectId, UUID versionId, String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return new ArrayList<>();
        }
        List<Double> vector = openAiClient.embedding(query);
        return repository.searchChunks(projectId, versionId, RagRepository.toVectorLiteral(vector), Math.max(1, limit));
    }

    private List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n").trim();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + CHUNK_SIZE);
            chunks.add(normalized.substring(start, end));
            if (end >= normalized.length()) break;
            start = Math.max(0, end - CHUNK_OVERLAP);
        }
        return chunks;
    }
}
