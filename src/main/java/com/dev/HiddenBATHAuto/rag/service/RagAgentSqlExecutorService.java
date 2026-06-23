package com.dev.HiddenBATHAuto.rag.service;

import java.sql.Timestamp;
import java.time.temporal.TemporalAccessor;
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

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagAgentSqlExecutorService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RagAgentSqlSafetyService safetyService;

    public RagAgentSqlExecutorService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                      ObjectMapper objectMapper,
                                      RagAgentSqlSafetyService safetyService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.safetyService = safetyService;
    }

    public Map<String, Object> executeRead(UUID runId,
                                           UUID projectId,
                                           UUID versionId,
                                           UUID sessionId,
                                           String requestId,
                                           String reason,
                                           String sql,
                                           String paramsJson) {
        UUID queryId = UUID.randomUUID();
        MapSqlParameterSource params = buildParams(projectId, versionId, sessionId, paramsJson, null);
        try {
            RagAgentSqlSafetyService.ValidatedSql validated = safetyService.validateReadSql(sql);
            List<Map<String, Object>> rows = jdbc.queryForList(validated.sql(), params);
            List<Map<String, Object>> safeRows = sanitizeRows(rows, 500, 16000);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("queryId", queryId);
            result.put("requestId", requestId);
            result.put("reason", reason);
            result.put("rowCount", safeRows.size());
            result.put("rows", safeRows);
            insertSqlLog(queryId, runId, projectId, versionId, sessionId, requestId, "READ", reason, sql, validated.sql(), paramsJson, result, safeRows.size(), "SUCCESS", null);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("queryId", queryId);
            result.put("requestId", requestId);
            result.put("reason", reason);
            result.put("error", e.getMessage());
            insertSqlLog(queryId, runId, projectId, versionId, sessionId, requestId, "READ", reason, sql, null, paramsJson, result, 0, "FAILED", e.getMessage());
            throw new IllegalArgumentException("GPT SQL 조회가 안전성 검증 또는 실행에 실패했습니다. requestId=" + requestId + ", 원인: " + e.getMessage(), e);
        }
    }

    public int executeWrite(UUID runId,
                            UUID projectId,
                            UUID versionId,
                            UUID sessionId,
                            String requestId,
                            String reason,
                            String sql,
                            String paramsJson,
                            UUID targetId) {
        UUID queryId = UUID.randomUUID();
        MapSqlParameterSource params = buildParams(projectId, versionId, sessionId, paramsJson, targetId);
        try {
            RagAgentSqlSafetyService.ValidatedSql validated = safetyService.validateWriteSql(sql);
            int updated = jdbc.update(validated.sql(), params);
            Map<String, Object> result = Map.of("updated", updated);
            insertSqlLog(queryId, runId, projectId, versionId, sessionId, requestId, "WRITE", reason, sql, validated.sql(), paramsJson, result, updated, "SUCCESS", null);
            return updated;
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            insertSqlLog(queryId, runId, projectId, versionId, sessionId, requestId, "WRITE", reason, sql, null, paramsJson, result, 0, "FAILED", e.getMessage());
            throw new IllegalArgumentException("GPT 변경 SQL이 안전성 검증 또는 실행에 실패했습니다. requestId=" + requestId + ", 원인: " + e.getMessage(), e);
        }
    }

    private MapSqlParameterSource buildParams(UUID projectId,
                                              UUID versionId,
                                              UUID sessionId,
                                              String paramsJson,
                                              UUID targetId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("sessionId", sessionId)
                .addValue("agentLimit", 500);
        if (targetId != null) {
            params.addValue("targetId", targetId);
        }
        Map<String, Object> userParams = parseObject(paramsJson);
        for (Map.Entry<String, Object> entry : userParams.entrySet()) {
            String key = entry.getKey();
            if ("projectId".equals(key) || "versionId".equals(key) || "sessionId".equals(key) || "agentLimit".equals(key)) {
                continue;
            }
            if (key.matches("p([1-9]|[1-4][0-9]|50)") || "targetId".equals(key)) {
                params.addValue(key, normalizeParamValue(entry.getValue()));
            }
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseObject(String json) {
        if (!StringUtils.hasText(json)) return new LinkedHashMap<>();
        try {
            Object value = objectMapper.readValue(json, Object.class);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
                }
                return result;
            }
            return new LinkedHashMap<>();
        } catch (Exception e) {
            throw new IllegalArgumentException("paramsJson은 JSON object여야 합니다. 원인: " + e.getMessage(), e);
        }
    }

    private Object normalizeParamValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof UUID) {
            return value;
        }
        return RagJsonUtils.toJson(objectMapper, value);
    }

    private List<Map<String, Object>> sanitizeRows(List<Map<String, Object>> rows, int maxRows, int maxStringLength) {
        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        for (Map<String, Object> row : rows) {
            if (++count > maxRows) break;
            Map<String, Object> safe = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                safe.put(entry.getKey(), sanitizeValue(entry.getValue(), maxStringLength));
            }
            result.add(safe);
        }
        return result;
    }

    private Object sanitizeValue(Object value, int maxStringLength) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof UUID) return value;
        if (value instanceof Timestamp ts) return ts.toInstant().toString();
        if (value instanceof TemporalAccessor) return String.valueOf(value);
        String text = String.valueOf(value);
        String trimmed = text.length() > maxStringLength ? text.substring(0, maxStringLength) + "\n...[TRUNCATED]" : text;
        String starts = trimmed.trim();
        if ((starts.startsWith("{") && starts.endsWith("}")) || (starts.startsWith("[") && starts.endsWith("]"))) {
            try {
                return objectMapper.readValue(starts, new TypeReference<Object>() {});
            } catch (Exception ignored) {
                return trimmed;
            }
        }
        return trimmed;
    }

    private void insertSqlLog(UUID queryId,
                              UUID runId,
                              UUID projectId,
                              UUID versionId,
                              UUID sessionId,
                              String requestId,
                              String queryKind,
                              String reason,
                              String originalSql,
                              String executedSql,
                              String paramsJson,
                              Object result,
                              int rowCount,
                              String status,
                              String errorMessage) {
        String sql = """
                INSERT INTO rag_agent_sql_query(
                    id, run_id, project_id, version_id, session_id, request_id, query_kind, reason,
                    original_sql, executed_sql, params_json, result_json, row_count, status, error_message, created_at
                ) VALUES (
                    :id, :runId, :projectId, :versionId, :sessionId, :requestId, :queryKind, :reason,
                    :originalSql, :executedSql, CAST(:paramsJson AS jsonb), CAST(:resultJson AS jsonb), :rowCount, :status, :errorMessage, now()
                )
                """;
        try {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", queryId)
                    .addValue("runId", runId)
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("sessionId", sessionId)
                    .addValue("requestId", requestId)
                    .addValue("queryKind", queryKind)
                    .addValue("reason", reason)
                    .addValue("originalSql", originalSql)
                    .addValue("executedSql", executedSql)
                    .addValue("paramsJson", StringUtils.hasText(paramsJson) ? paramsJson : "{}")
                    .addValue("resultJson", RagJsonUtils.toJson(objectMapper, result))
                    .addValue("rowCount", rowCount)
                    .addValue("status", status)
                    .addValue("errorMessage", errorMessage));
        } catch (Exception ignored) {
            // SQL 로그 저장 실패가 실제 작업을 막으면 안 됩니다.
        }
    }
}
