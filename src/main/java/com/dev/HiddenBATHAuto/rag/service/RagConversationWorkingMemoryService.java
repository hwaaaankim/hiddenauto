package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ChatGPT처럼 이어지는 대화를 위해 세션 단위 working memory를 관리합니다.
 *
 * 이 메모리는 영구 학습 지식이 아니라 현재 대화에서 생략된 표현을 해석하기 위한 임시 문맥입니다.
 * 예: "라운드 빌트 1도어장 1250은 얼마야?" 다음 "1350이면?"을 같은 제품/W 질문으로 해석.
 */
@Service
public class RagConversationWorkingMemoryService {

    public static final String DEFAULT_MEMORY_KEY = "ACTIVE_CONVERSATION_CONTEXT";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagConversationWorkingMemoryService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                               ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> load(UUID projectId, UUID versionId, UUID sessionId, String sourceScope) {
        if (sessionId == null) {
            return Map.of();
        }
        try {
            Map<String, Object> row = jdbc.queryForMap("""
                    SELECT memory_json
                    FROM rag_conversation_working_memory
                    WHERE project_id = :projectId
                      AND version_id = :versionId
                      AND session_id = :sessionId
                      AND source_scope = :sourceScope
                      AND memory_key = :memoryKey
                      AND active = true
                      AND (expires_at IS NULL OR expires_at > now())
                    ORDER BY updated_at DESC
                    LIMIT 1
                    """, params(projectId, versionId, sessionId, sourceScope)
                    .addValue("memoryKey", DEFAULT_MEMORY_KEY));
            return jsonMap(row.get("memory_json"));
        } catch (EmptyResultDataAccessException e) {
            return Map.of();
        }
    }

    public void upsert(UUID projectId,
                       UUID versionId,
                       UUID sessionId,
                       String sourceScope,
                       Map<String, Object> memory,
                       BigDecimal confidence,
                       String reason) {
        if (sessionId == null || memory == null || memory.isEmpty()) {
            return;
        }
        MapSqlParameterSource p = params(projectId, versionId, sessionId, sourceScope)
                .addValue("id", UUID.randomUUID())
                .addValue("memoryKey", DEFAULT_MEMORY_KEY)
                .addValue("memoryJson", RagJsonUtils.toJson(objectMapper, memory))
                .addValue("confidence", confidence == null ? new BigDecimal("0.8000") : confidence)
                .addValue("reason", reason == null ? "" : reason)
                .addValue("updatedAt", OffsetDateTime.now());
        jdbc.update("""
                INSERT INTO rag_conversation_working_memory(
                    id, project_id, version_id, session_id, source_scope, memory_key,
                    memory_json, confidence, reason, active, created_at, updated_at
                ) VALUES (
                    :id, :projectId, :versionId, :sessionId, :sourceScope, :memoryKey,
                    CAST(:memoryJson AS jsonb), :confidence, :reason, true, :updatedAt, :updatedAt
                )
                ON CONFLICT (project_id, version_id, session_id, source_scope, memory_key)
                DO UPDATE SET
                    memory_json = EXCLUDED.memory_json,
                    confidence = EXCLUDED.confidence,
                    reason = EXCLUDED.reason,
                    active = true,
                    updated_at = EXCLUDED.updated_at
                """, p);
    }

    public void rememberQuote(UUID projectId,
                              UUID versionId,
                              UUID sessionId,
                              String sourceScope,
                              String userMessage,
                              Map<String, Object> interpretation,
                              Map<String, Object> response) {
        if (sessionId == null || interpretation == null || response == null) {
            return;
        }
        Map<String, Object> quote = mapOf(response.get("quote"));
        if (quote.isEmpty()) {
            return;
        }
        Map<String, Object> memory = new LinkedHashMap<>(load(projectId, versionId, sessionId, sourceScope));
        String productName = firstText(
                quote.get("productName"),
                interpretation.get("productName"),
                interpretation.get("productQuery"),
                memory.get("activeSubject"));
        String productCode = firstText(
                interpretation.get("productCode"),
                memory.get("activeProductCode"));
        Map<String, Object> requestedFactors = mapOf(interpretation.get("requestedFactors"));
        Object requestedW = firstNonNull(quote.get("requestedW"), requestedFactors.get("W"), requestedFactors.get("넓이"), requestedFactors.get("폭"));

        memory.put("activeIntent", "PRICE_QUOTE");
        if (StringUtils.hasText(productName)) memory.put("activeSubject", productName);
        if (StringUtils.hasText(productCode)) memory.put("activeProductCode", productCode);
        memory.put("activeFactor", requestedW == null ? firstText(memory.get("activeFactor"), "") : "W");
        memory.put("lastUserMessage", userMessage == null ? "" : userMessage);
        memory.put("lastQuoteStatus", firstText(quote.get("quoteStatus"), ""));
        memory.put("lastRuleKey", extractRuleKey(quote));
        memory.put("lastFactors", requestedFactors);
        if (requestedW != null) memory.put("lastRequestedW", requestedW);
        memory.put("lastQuote", quote);
        memory.put("updatedAt", OffsetDateTime.now().toString());
        upsert(projectId, versionId, sessionId, sourceScope, memory, new BigDecimal("0.9500"), "QUOTE_CONTEXT_UPDATED");
    }

    public void clear(UUID projectId, UUID versionId, UUID sessionId, String sourceScope) {
        if (sessionId == null) return;
        jdbc.update("""
                UPDATE rag_conversation_working_memory
                SET active = false, updated_at = now()
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND session_id = :sessionId
                  AND source_scope = :sourceScope
                """, params(projectId, versionId, sessionId, sourceScope));
    }

    private MapSqlParameterSource params(UUID projectId, UUID versionId, UUID sessionId, String sourceScope) {
        return new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("sessionId", sessionId)
                .addValue("sourceScope", StringUtils.hasText(sourceScope) ? sourceScope : "API");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonMap(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) result.put(String.valueOf(e.getKey()), e.getValue());
            return result;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) result.put(String.valueOf(e.getKey()), e.getValue());
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String extractRuleKey(Map<String, Object> quote) {
        Map<String, Object> rule = mapOf(quote.get("rule"));
        return firstText(rule.get("factKey"), rule.get("fact_key"));
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) return value;
        }
        return null;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }
}
