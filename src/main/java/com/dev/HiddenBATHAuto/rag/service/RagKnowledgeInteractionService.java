package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * V4에서는 모든 학습/상담 입력을 GPT DB Tool Agent로만 전달합니다.
 * 기존 Java 의미분류·답변 조립·고정 매핑 fallback은 이 진입점에서 사용하지 않습니다.
 */
@Service
public class RagKnowledgeInteractionService {

    private final RagRepository repository;
    private final RagSqlAgentService sqlAgentService;
    private final ObjectMapper objectMapper;

    public RagKnowledgeInteractionService(RagRepository repository,
                                          RagSqlAgentService sqlAgentService,
                                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.sqlAgentService = sqlAgentService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> routeLearningInput(UUID sessionId,
                                                   String message,
                                                   boolean forceSave,
                                                   List<MultipartFile> files) {
        RagScope scope = findLearningScope(sessionId);
        return sqlAgentService.handle(scope.projectId(), scope.versionId(), sessionId,
                "LEARNING", clean(message), forceSave, safeFiles(files));
    }

    public Map<String, Object> routeChatInput(UUID sessionId,
                                               String message,
                                               List<MultipartFile> files) {
        RagScope scope = findChatScope(sessionId);
        return sqlAgentService.handle(scope.projectId(), scope.versionId(), sessionId,
                "CHAT", clean(message), false, safeFiles(files));
    }

    public Map<String, Object> routeLearningSystemEvent(UUID sessionId,
                                                         String eventType,
                                                         Map<String, Object> payload) {
        RagScope scope = findLearningScope(sessionId);
        return sqlAgentService.handle(scope.projectId(), scope.versionId(), sessionId,
                "LEARNING", systemEvent(eventType, payload), false, List.of());
    }

    public Map<String, Object> routeChatSystemEvent(UUID sessionId,
                                                     String eventType,
                                                     Map<String, Object> payload) {
        RagScope scope = findChatScope(sessionId);
        return sqlAgentService.handle(scope.projectId(), scope.versionId(), sessionId,
                "CHAT", systemEvent(eventType, payload), false, List.of());
    }

    public Map<String, Object> summarizeKnowledge(UUID projectId,
                                                   UUID versionId,
                                                   String query,
                                                   UUID sessionId,
                                                   String sourceScope) {
        if (projectId == null || versionId == null) {
            throw new IllegalArgumentException("projectId와 versionId가 필요합니다.");
        }
        String scope = StringUtils.hasText(sourceScope) ? sourceScope.trim().toUpperCase() : "API";
        return sqlAgentService.handle(projectId, versionId, sessionId, scope, clean(query), false, List.of());
    }

    private RagScope findLearningScope(UUID sessionId) {
        Map<String, Object> session = repository.findLearningSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("학습 세션을 찾을 수 없습니다: " + sessionId));
        return new RagScope((UUID) session.get("project_id"), (UUID) session.get("version_id"));
    }

    private RagScope findChatScope(UUID sessionId) {
        Map<String, Object> session = repository.findChatSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("채팅 세션을 찾을 수 없습니다: " + sessionId));
        return new RagScope((UUID) session.get("project_id"), (UUID) session.get("version_id"));
    }

    private String systemEvent(String eventType, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", StringUtils.hasText(eventType) ? eventType.trim().toUpperCase() : "UNKNOWN");
        event.put("payload", payload == null ? Map.of() : payload);
        event.put("instruction", "이 시스템 이벤트를 현재 DB 지식과 대화 상태에 맞는 자연스러운 사용자용 응답으로 작성하십시오. 필요한 경우 DB 도구를 사용하십시오.");
        return "[[SYSTEM_EVENT:" + event.get("eventType") + "]]\n" + RagJsonUtils.toJson(objectMapper, event);
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }

    private List<MultipartFile> safeFiles(List<MultipartFile> files) {
        if (files == null) return List.of();
        return files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }

    private record RagScope(UUID projectId, UUID versionId) {}
}
