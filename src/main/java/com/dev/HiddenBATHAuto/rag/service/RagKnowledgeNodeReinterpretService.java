package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RAW/서버 추출 상태로 남은 지식 노드를 나중에 다시 해석해서 AI_PARSED 노드로 승격시키는 서비스입니다.
 *
 * 원칙:
 * - 원본 노드를 바로 삭제하지 않습니다.
 * - 재해석 성공 시 원본 노드는 SUPERSEDED_BY_AI_RETRY로 비활성화하고, 새 AI_PARSED 트리를 같은 부모 아래에 연결합니다.
 * - 재해석 실패 시 원본 노드는 다시 NEEDS_AI_RETRY 상태로 남겨 다음 작업에서 더 작게 재시도할 수 있게 합니다.
 */
@Service
public class RagKnowledgeNodeReinterpretService {

    private final RagRepository repository;
    private final RagLearningChunkedCognitiveService chunkedCognitiveService;
    private final OpenAiRagClient openAi;
    private final ObjectMapper objectMapper;

    public RagKnowledgeNodeReinterpretService(RagRepository repository,
                                              RagLearningChunkedCognitiveService chunkedCognitiveService,
                                              OpenAiRagClient openAi,
                                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.chunkedCognitiveService = chunkedCognitiveService;
        this.openAi = openAi;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> retryRetryableNodes(UUID projectId,
                                                    UUID versionId,
                                                    String topic,
                                                    int limit,
                                                    RagConversationalLearningService.RagLearningProgressListener progressListener) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        List<Map<String, Object>> candidates = repository.findRetryableKnowledgeNodes(projectId, versionId, topic, safeLimit);
        List<Map<String, Object>> results = new ArrayList<>();
        notify(progressListener, "GPT_INTERPRETING", 20, "재해석 대기 지식 노드 " + candidates.size() + "개를 찾았습니다.");

        int success = 0;
        int failed = 0;
        for (int i = 0; i < candidates.size(); i++) {
            Map<String, Object> node = candidates.get(i);
            UUID nodeId = (UUID) node.get("id");
            int progress = 20 + (int) Math.floor(((i + 1) * 55.0) / Math.max(1, candidates.size()));
            notify(progressListener, "GPT_INTERPRETING", progress,
                    "재해석 노드 " + (i + 1) + "/" + candidates.size() + " 처리 중입니다: " + RagJsonUtils.stringValue(node, "title"));
            try {
                Map<String, Object> row = retryOneNode(node);
                results.add(row);
                if (Boolean.TRUE.equals(row.get("success"))) success++; else failed++;
            } catch (Exception e) {
                failed++;
                repository.markKnowledgeNodeRetryFailed(nodeId, String.valueOf(e.getMessage()));
                results.add(Map.of(
                        "success", false,
                        "nodeId", nodeId,
                        "error", String.valueOf(e.getMessage())
                ));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("candidateCount", candidates.size());
        result.put("promotedCount", success);
        result.put("failedCount", failed);
        result.put("results", results);
        result.put("message", "재해석 대기 노드 처리 완료: 승격 " + success + "개, 재대기 " + failed + "개");
        notify(progressListener, "COMPLETED", 100, RagJsonUtils.stringValue(result, "message"));
        return result;
    }

    public Map<String, Object> retryOneNode(UUID nodeId) {
        Map<String, Object> node = repository.findKnowledgeNode(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("지식 노드를 찾을 수 없습니다."));
        return retryOneNode(node);
    }

    public Map<String, Object> retryOneNode(UUID nodeId, UUID expectedProjectId, UUID expectedVersionId) {
        Map<String, Object> node = repository.findKnowledgeNode(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("지식 노드를 찾을 수 없습니다."));
        if (expectedProjectId != null && !expectedProjectId.equals(node.get("project_id"))) {
            throw new IllegalArgumentException("요청한 프로젝트의 지식 노드가 아닙니다.");
        }
        if (expectedVersionId != null && !expectedVersionId.equals(node.get("version_id"))) {
            throw new IllegalArgumentException("요청한 버전의 지식 노드가 아닙니다.");
        }
        return retryOneNode(node);
    }

    private Map<String, Object> retryOneNode(Map<String, Object> node) {
        UUID nodeId = (UUID) node.get("id");
        UUID projectId = (UUID) node.get("project_id");
        UUID versionId = (UUID) node.get("version_id");
        UUID documentId = (UUID) node.get("document_id");
        UUID parentId = (UUID) node.get("parent_id");
        String topic = RagJsonUtils.stringValue(node, "topic");
        String title = firstText(RagJsonUtils.stringValue(node, "title"), RagJsonUtils.stringValue(node, "node_key"), "재해석 대상 노드");
        String rawText = sourceText(node);
        if (!StringUtils.hasText(rawText)) {
            throw new IllegalStateException("재해석할 원문이 없습니다.");
        }

        repository.markKnowledgeNodeRetrying(nodeId);

        Map<String, Object> analysis = chunkedCognitiveService.interpret(
                Map.of(),
                List.of(),
                List.of(),
                Map.of(),
                "[기존 실패 노드 재해석] " + title + "\n\n" + rawText,
                null,
                null,
                "기존 RAW/서버추출 노드를 더 작은 leaf로 재분할하여 AI_PARSED 트리로 승격합니다.",
                true,
                null
        );

        Map<String, Object> tree = RagJsonUtils.childMap(analysis, "knowledgeTreeJson");
        List<Object> rawNodes = RagJsonUtils.childList(tree, "nodes");
        if (rawNodes.isEmpty()) {
            repository.markKnowledgeNodeRetryFailed(nodeId, "재해석 결과 트리 노드가 생성되지 않았습니다.");
            return Map.of("success", false, "nodeId", nodeId, "reason", "NO_TREE_NODES");
        }

        String baseKey = firstText(RagJsonUtils.stringValue(node, "node_key"), nodeId.toString());
        int originalRetryCount = RagJsonUtils.intValue(node, "retry_count", 0);
        String retryPrefix = baseKey + "/ai_retry_" + (originalRetryCount + 1) + "/";

        Map<String, UUID> idByKey = new LinkedHashMap<>();
        for (Object raw : rawNodes) {
            Map<String, Object> treeNode = RagJsonUtils.toMap(objectMapper, raw);
            String nodeKey = firstText(RagJsonUtils.stringValue(treeNode, "nodeKey"), "node-" + (idByKey.size() + 1));
            idByKey.putIfAbsent(nodeKey, UUID.randomUUID());
        }

        int inserted = 0;
        int sort = RagJsonUtils.intValue(node, "sort_order", 0) + 1;
        for (Object raw : rawNodes) {
            Map<String, Object> treeNode = RagJsonUtils.toMap(objectMapper, raw);
            String nodeKey = firstText(RagJsonUtils.stringValue(treeNode, "nodeKey"), "node-" + (inserted + 1));
            String parentKey = RagJsonUtils.stringValue(treeNode, "parentKey");
            UUID newNodeId = idByKey.get(nodeKey);
            UUID newParentId = StringUtils.hasText(parentKey) && idByKey.containsKey(parentKey)
                    ? idByKey.get(parentKey)
                    : parentId;
            String nodeType = firstText(RagJsonUtils.stringValue(treeNode, "nodeType"), "AI_RETRY_NODE");
            String newTitle = firstText(RagJsonUtils.stringValue(treeNode, "title"), nodeKey);
            String summary = RagJsonUtils.stringValue(treeNode, "summary");
            String childRawText = RagJsonUtils.stringValue(treeNode, "rawText");
            Object structuredJson = treeNode.getOrDefault("structuredJson", Map.of());
            String interpretationStatus = firstText(RagJsonUtils.stringValue(treeNode, "interpretationStatus"), "AI_PARSED");
            boolean retryable = RagJsonUtils.boolValue(treeNode, "retryable", false)
                    || interpretationStatus.contains("PENDING")
                    || interpretationStatus.contains("NEEDS_AI_RETRY");
            String lastError = RagJsonUtils.stringValue(treeNode, "retryReason");

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", "KNOWLEDGE_NODE_AI_RETRY");
            metadata.put("supersedesNodeId", nodeId.toString());
            metadata.put("sourceNodeKey", baseKey);
            metadata.put("cognitiveEngine", RagJsonUtils.stringValue(analysis, "cognitiveEngine"));
            metadata.put("cognitiveEngineVersion", RagJsonUtils.stringValue(analysis, "cognitiveEngineVersion"));
            metadata.put("interpretationStatus", interpretationStatus);
            metadata.put("retryable", retryable);
            if (StringUtils.hasText(lastError)) metadata.put("lastError", lastError);

            repository.insertKnowledgeNode(
                    newNodeId,
                    newParentId,
                    projectId,
                    versionId,
                    documentId,
                    topic,
                    nodeType,
                    retryPrefix + nodeKey,
                    newTitle,
                    summary,
                    childRawText,
                    structuredJson,
                    metadata,
                    true,
                    RagJsonUtils.intValue(node, "depth", 0) + 1,
                    sort + inserted,
                    interpretationStatus,
                    retryable,
                    0,
                    lastError,
                    nodeId
            );
            insertSearchChunkSafely(newNodeId, documentId, projectId, versionId, topic, retryPrefix + nodeKey, nodeType, newTitle, summary, childRawText, structuredJson);
            inserted++;
        }

        repository.markKnowledgeNodeSupersededByRetry(nodeId, "재해석 성공으로 AI_PARSED 대체 노드를 생성했습니다. inserted=" + inserted);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("nodeId", nodeId);
        result.put("insertedNodeCount", inserted);
        result.put("answer", firstText(RagJsonUtils.stringValue(analysis, "answer"), "재해석으로 AI_PARSED 대체 노드를 생성했습니다."));
        return result;
    }

    private String sourceText(Map<String, Object> node) {
        String raw = RagJsonUtils.stringValue(node, "raw_text");
        if (StringUtils.hasText(raw)) return raw;
        Map<String, Object> structured = RagJsonUtils.toMap(objectMapper, node.get("structured_json"));
        String rawTextPreview = RagJsonUtils.stringValue(structured, "rawTextPreview");
        if (StringUtils.hasText(rawTextPreview)) return rawTextPreview;
        String knowledgeText = RagJsonUtils.stringValue(structured, "knowledgeText");
        if (StringUtils.hasText(knowledgeText)) return knowledgeText;
        String summary = RagJsonUtils.stringValue(node, "summary");
        if (StringUtils.hasText(summary)) return summary;
        return RagJsonUtils.pretty(objectMapper, structured);
    }

    private void insertSearchChunkSafely(UUID nodeId,
                                         UUID documentId,
                                         UUID projectId,
                                         UUID versionId,
                                         String topic,
                                         String nodeKey,
                                         String nodeType,
                                         String title,
                                         String summary,
                                         String rawText,
                                         Object structuredJson) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(title)) sb.append(title).append('\n');
        if (StringUtils.hasText(summary)) sb.append(summary).append('\n');
        if (StringUtils.hasText(rawText)) sb.append(rawText).append('\n');
        sb.append(RagJsonUtils.pretty(objectMapper, structuredJson));
        String content = RagJsonUtils.truncate(sb.toString(), 6000);
        if (!StringUtils.hasText(content)) return;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "KNOWLEDGE_NODE_AI_RETRY");
        metadata.put("nodeId", nodeId.toString());
        metadata.put("nodeKey", nodeKey);
        metadata.put("nodeType", nodeType);
        metadata.put("topic", topic);
        try {
            String vector = RagRepository.toVectorLiteral(openAi.embedding(content));
            repository.insertChunk(UUID.randomUUID(), documentId, projectId, versionId, 20000 + Math.abs(nodeKey.hashCode() % 100000), topic, content, metadata, vector);
        } catch (Exception e) {
            metadata.put("embeddingError", e.getMessage());
            repository.insertChunkWithoutEmbedding(UUID.randomUUID(), documentId, projectId, versionId, 20000 + Math.abs(nodeKey.hashCode() % 100000), topic, content, metadata);
        }
    }

    private void notify(RagConversationalLearningService.RagLearningProgressListener listener, String status, int progress, String message) {
        if (listener == null) return;
        try {
            listener.update(status, progress, message);
        } catch (Exception ignored) {
            // 진행 상태 저장 실패가 실제 재해석을 막으면 안 됩니다.
        }
    }

    private String firstText(String... values) {
        if (values == null) return "";
        for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        return "";
    }
}
