package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 세션별 대화 문맥을 GPT가 명시적으로 읽고 갱신할 수 있게 하는 제한형 도구입니다. */
@Service
public class RagAgentConversationToolService {

    private final NamedParameterJdbcTemplate jdbc;
    private final RagConversationWorkingMemoryService workingMemoryService;

    public RagAgentConversationToolService(
            @Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            RagConversationWorkingMemoryService workingMemoryService) {
        this.jdbc = jdbc;
        this.workingMemoryService = workingMemoryService;
    }

    public Map<String, Object> getMemory(RagAgentToolContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", context.sessionId());
        result.put("sourceScope", context.sourceScope());
        result.put("memory", context.sessionId() == null
                ? Map.of()
                : workingMemoryService.load(
                        context.projectId(), context.versionId(), context.sessionId(), context.sourceScope()));
        return result;
    }

    public Map<String, Object> updateMemory(RagAgentToolContext context,
                                             String mode,
                                             Map<String, Object> memory,
                                             BigDecimal confidence,
                                             String reason) {
        if (context.sessionId() == null) {
            throw new IllegalArgumentException("대화 메모리를 갱신하려면 sessionId가 필요합니다.");
        }
        String normalizedMode = StringUtils.hasText(mode) ? mode.trim().toUpperCase() : "MERGE";
        if ("CLEAR".equals(normalizedMode)) {
            workingMemoryService.clear(context.projectId(), context.versionId(), context.sessionId(), context.sourceScope());
            return Map.of("success", true, "mode", "CLEAR", "memory", Map.of());
        }
        if (!List.of("MERGE", "REPLACE").contains(normalizedMode)) {
            throw new IllegalArgumentException("memory mode는 MERGE, REPLACE, CLEAR 중 하나여야 합니다.");
        }
        Map<String, Object> next = new LinkedHashMap<>();
        if ("MERGE".equals(normalizedMode)) {
            next.putAll(workingMemoryService.load(
                    context.projectId(), context.versionId(), context.sessionId(), context.sourceScope()));
        }
        if (memory != null) next.putAll(memory);
        if (next.isEmpty()) {
            throw new IllegalArgumentException("저장할 memory가 비어 있습니다.");
        }
        workingMemoryService.upsert(
                context.projectId(), context.versionId(), context.sessionId(), context.sourceScope(),
                next, confidence == null ? new BigDecimal("0.8000") : confidence,
                StringUtils.hasText(reason) ? reason : "GPT_AGENT_MEMORY_UPDATE");
        return Map.of("success", true, "mode", normalizedMode, "memory", next);
    }

    public Map<String, Object> history(RagAgentToolContext context, int limit, boolean includeSystem) {
        if (context.sessionId() == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sessionId", null);
            result.put("sourceScope", context.sourceScope());
            result.put("messages", List.of());
            result.put("count", 0);
            return result;
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> rows;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("sessionId", context.sessionId())
                .addValue("limit", safeLimit);
        if ("CHAT".equalsIgnoreCase(context.sourceScope())) {
            String sql = """
                    SELECT id, role, content, created_at, state_snapshot, retrieved_json
                    FROM rag_chat_message
                    WHERE session_id = :sessionId
                      AND (:includeSystem = true OR upper(role) <> 'SYSTEM')
                    ORDER BY created_at DESC
                    LIMIT :limit
                    """;
            p.addValue("includeSystem", includeSystem);
            rows = jdbc.queryForList(sql, p);
        } else {
            String sql = """
                    SELECT id, role, message AS content, created_at
                    FROM rag_learning_message
                    WHERE session_id = :sessionId
                      AND (:includeSystem = true OR upper(role) <> 'SYSTEM')
                    ORDER BY created_at DESC
                    LIMIT :limit
                    """;
            p.addValue("includeSystem", includeSystem);
            rows = jdbc.queryForList(sql, p);
        }
        List<Map<String, Object>> chronological = new ArrayList<>(rows);
        java.util.Collections.reverse(chronological);
        return Map.of(
                "sessionId", context.sessionId(),
                "sourceScope", context.sourceScope(),
                "count", chronological.size(),
                "messages", chronological
        );
    }
}
