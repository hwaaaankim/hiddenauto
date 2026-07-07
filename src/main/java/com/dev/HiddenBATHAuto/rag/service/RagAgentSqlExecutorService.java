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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.config.RagOpenAiProperties;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagAgentSqlExecutorService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RagAgentSqlSafetyService safetyService;
    private final RagAgentSchemaService schemaService;
    private final RagOpenAiProperties properties;
    private final TransactionTemplate readTransactionTemplate;

    public RagAgentSqlExecutorService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                      ObjectMapper objectMapper,
                                      RagAgentSqlSafetyService safetyService,
                                      RagAgentSchemaService schemaService,
                                      RagOpenAiProperties properties,
                                      @Qualifier("ragTransactionManager") PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.safetyService = safetyService;
        this.schemaService = schemaService;
        this.properties = properties;
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
        this.readTransactionTemplate.setTimeout(properties.getAgentQueryTimeoutSeconds());
    }

    public Map<String, Object> executeRead(UUID runId,
                                           UUID projectId,
                                           UUID versionId,
                                           UUID sessionId,
                                           String requestId,
                                           String reason,
                                           String sql,
                                           String paramsJson) {
        return executeRead(runId, projectId, versionId, sessionId, "LEARNING", requestId, reason, sql, paramsJson,
                properties.getAgentDefaultReadRows());
    }

    public Map<String, Object> executeRead(UUID runId,
                                           UUID projectId,
                                           UUID versionId,
                                           UUID sessionId,
                                           String requestId,
                                           String reason,
                                           String sql,
                                           String paramsJson,
                                           int maxRows) {
        return executeRead(runId, projectId, versionId, sessionId, "LEARNING", requestId, reason, sql, paramsJson, maxRows);
    }

    public Map<String, Object> executeRead(UUID runId,
                                           UUID projectId,
                                           UUID versionId,
                                           UUID sessionId,
                                           String sourceScope,
                                           String requestId,
                                           String reason,
                                           String sql,
                                           String paramsJson,
                                           int maxRows) {
        UUID queryId = UUID.randomUUID();
        int safeMaxRows = Math.max(1, Math.min(maxRows, properties.getAgentHardMaxReadRows()));
        try {
            MapSqlParameterSource params = buildParams(projectId, versionId, sessionId, paramsJson, null, safeMaxRows);
            RagAgentSqlSafetyService.ValidatedSql validated = safetyService.validateReadSql(sql, safeMaxRows, sourceScope);
            List<Map<String, Object>> rows = readTransactionTemplate.execute(status -> {
                applyReadScopeSettings(projectId, versionId, sessionId);
                return jdbc.queryForList(validated.sql(), params);
            });
            if (rows == null) rows = List.of();
            List<Map<String, Object>> safeRows = sanitizeRows(rows, safeMaxRows, 16000);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("queryId", queryId);
            result.put("requestId", requestId);
            result.put("reason", reason);
            result.put("executedSql", validated.sql());
            result.put("warnings", validated.warnings());
            result.put("rowCount", safeRows.size());
            result.put("truncated", rows.size() > safeRows.size());
            result.put("rows", safeRows);
            insertSqlLog(queryId, runId, projectId, versionId, sessionId, requestId, "READ", reason,
                    sql, validated.sql(), paramsJson, result, safeRows.size(), "SUCCESS", null);
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("queryId", queryId);
            result.put("requestId", requestId);
            result.put("reason", reason);
            result.put("error", e.getMessage());
            insertSqlLog(queryId, runId, projectId, versionId, sessionId, requestId, "READ", reason,
                    sql, null, paramsJson, result, 0, "FAILED", e.getMessage());
            throw new IllegalArgumentException("GPT DB 조회가 안전성 검증 또는 실행에 실패했습니다. requestId="
                    + requestId + ", 원인: " + e.getMessage(), e);
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
        try {
            MapSqlParameterSource params = buildParams(projectId, versionId, sessionId, paramsJson, targetId, 1);
            RagAgentSqlSafetyService.ValidatedSql validated = safetyService.validateWriteSql(sql);
            ensureWriteTargetBelongsToScope(projectId, versionId, validated.sql(), paramsJson, targetId);
            int updated = jdbc.update(validated.sql(), params);
            if (updated > properties.getAgentMaxAffectedRowsPerStatement()) {
                throw new IllegalStateException("한 변경 SQL의 영향 row 수(" + updated
                        + ")가 서버 제한(" + properties.getAgentMaxAffectedRowsPerStatement() + ")을 초과했습니다.");
            }
            Map<String, Object> result = Map.of("updated", updated, "executedSql", validated.sql());
            insertSqlLog(queryId, runId, projectId, versionId, sessionId, requestId, "WRITE", reason,
                    sql, validated.sql(), paramsJson, result, updated, "SUCCESS", null);
            return updated;
        } catch (Exception e) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            insertSqlLog(queryId, runId, projectId, versionId, sessionId, requestId, "WRITE", reason,
                    sql, null, paramsJson, result, 0, "FAILED", e.getMessage());
            throw new IllegalArgumentException("GPT 변경 SQL이 안전성 검증 또는 실행에 실패했습니다. requestId="
                    + requestId + ", 원인: " + e.getMessage(), e);
        }
    }

    private void ensureWriteTargetBelongsToScope(UUID projectId,
                                                    UUID versionId,
                                                    String sql,
                                                    String paramsJson,
                                                    UUID targetId) {
        String targetTable = safetyService.writeTargetTable(sql);
        RagAgentDataAccessPolicy.IndirectScopeRule rule = RagAgentDataAccessPolicy.indirectScopeRule(targetTable)
                .orElse(null);

        String lower = sql.trim().toLowerCase();
        if (rule == null) {
            if (!lower.startsWith("insert into ")) {
                ensureDirectTargetBelongsToScope(projectId, versionId, targetTable, targetId);
            }
            return;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId);
        String parentPredicate = parentScopePredicate(rule.parentTable(), "p");

        if (lower.startsWith("insert into ")) {
            String parameterName = safetyService.insertValueParameterForColumn(sql, rule.childForeignKey());
            Object rawParentId = parseObject(paramsJson).get(parameterName);
            UUID parentId = uuidValue(rawParentId);
            if (parentId == null) {
                throw new IllegalArgumentException(targetTable + " INSERT의 상위 ID 파라미터 :"
                        + parameterName + "는 UUID여야 합니다.");
            }
            params.addValue("parentId", parentId);
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + quote(rule.parentTable()) + " p WHERE p."
                            + quote(rule.parentPrimaryKey()) + " = :parentId AND " + parentPredicate,
                    params, Long.class);
            if (count == null || count < 1L) {
                throw new IllegalArgumentException(targetTable + " INSERT의 상위 row가 현재 프로젝트/버전에 존재하지 않습니다: "
                        + rule.parentTable() + "/" + parentId);
            }
            return;
        }

        if (targetId == null) {
            throw new IllegalArgumentException(targetTable + " 수정/삭제에는 서버 검증용 targetId UUID가 필요합니다.");
        }
        params.addValue("targetId", targetId);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + quote(targetTable) + " c JOIN " + quote(rule.parentTable())
                        + " p ON c." + quote(rule.childForeignKey()) + " = p." + quote(rule.parentPrimaryKey())
                        + " WHERE c.id = :targetId AND " + parentPredicate,
                params, Long.class);
        if (count == null || count < 1L) {
            throw new IllegalArgumentException(targetTable + " 대상 row가 현재 프로젝트/버전에 속하지 않습니다: " + targetId);
        }
    }

    private void applyReadScopeSettings(UUID projectId, UUID versionId, UUID sessionId) {
        MapSqlParameterSource settings = new MapSqlParameterSource()
                .addValue("projectId", projectId == null ? "" : projectId.toString())
                .addValue("versionId", versionId == null ? "" : versionId.toString())
                .addValue("sessionId", sessionId == null ? "" : sessionId.toString());
        jdbc.queryForObject("SELECT set_config('hiddenbath.rag_project_id', :projectId, true)", settings, String.class);
        jdbc.queryForObject("SELECT set_config('hiddenbath.rag_version_id', :versionId, true)", settings, String.class);
        jdbc.queryForObject("SELECT set_config('hiddenbath.rag_session_id', :sessionId, true)", settings, String.class);
    }

    private void ensureDirectTargetBelongsToScope(UUID projectId,
                                                   UUID versionId,
                                                   String targetTable,
                                                   UUID targetId) {
        if (targetId == null) {
            throw new IllegalArgumentException(targetTable + " 수정/삭제에는 서버 검증용 targetId UUID가 필요합니다.");
        }
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("targetId", targetId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId);
        conditions.add("id = :targetId");
        if (schemaService.hasColumn(targetTable, "project_id")) conditions.add("project_id = :projectId");
        if (schemaService.hasColumn(targetTable, "version_id")) conditions.add("version_id = :versionId");
        if ("rag_project".equals(targetTable)) conditions.add("id = :projectId");
        if ("rag_project_version".equals(targetTable)) {
            conditions.add("project_id = :projectId");
            conditions.add("id = :versionId");
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + quote(targetTable) + " WHERE " + String.join(" AND ", conditions),
                params,
                Long.class);
        if (count == null || count < 1L) {
            throw new IllegalArgumentException(targetTable + " 대상 row가 현재 프로젝트/버전에 속하지 않습니다: " + targetId);
        }
    }

    private String parentScopePredicate(String parentTable, String alias) {
        List<String> conditions = new ArrayList<>();
        if ("rag_project".equals(parentTable)) {
            conditions.add(alias + ".id = :projectId");
        } else if ("rag_project_version".equals(parentTable)) {
            conditions.add(alias + ".project_id = :projectId");
            conditions.add(alias + ".id = :versionId");
        } else {
            if (schemaService.hasColumn(parentTable, "project_id")) {
                conditions.add(alias + ".project_id = :projectId");
            }
            if (schemaService.hasColumn(parentTable, "version_id")) {
                conditions.add(alias + ".version_id = :versionId");
            }
        }
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("상위 테이블의 프로젝트/버전 범위를 확인할 수 없습니다: " + parentTable);
        }
        return String.join(" AND ", conditions);
    }

    private UUID uuidValue(Object value) {
        if (value instanceof UUID uuid) return uuid;
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String quote(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("허용되지 않은 DB identifier입니다: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private MapSqlParameterSource buildParams(UUID projectId,
                                              UUID versionId,
                                              UUID sessionId,
                                              String paramsJson,
                                              UUID targetId,
                                              int maxRows) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("sessionId", sessionId)
                .addValue("agentLimit", maxRows);
        if (targetId != null) params.addValue("targetId", targetId);

        Map<String, Object> userParams = parseObject(paramsJson);
        for (Map.Entry<String, Object> entry : userParams.entrySet()) {
            String key = entry.getKey();
            if ("projectId".equals(key) || "versionId".equals(key) || "sessionId".equals(key)
                    || "agentLimit".equals(key)) {
                continue;
            }
            if (key.matches("p([1-9]|[1-4][0-9]|50)")) {
                params.addValue(key, normalizeParamValue(entry.getValue()));
            }
            // targetId는 ChangeSet 항목의 서버 검증값만 사용하며 paramsJson으로 덮어쓸 수 없습니다.
        }
        return params;
    }

    public Map<String, Object> parseObject(String json) {
        if (!StringUtils.hasText(json)) return new LinkedHashMap<>();
        if (json.length() > properties.getAgentMaxParamsChars()) {
            throw new IllegalArgumentException("paramsJson 길이가 Agent 허용 최대값("
                    + properties.getAgentMaxParamsChars() + "자)을 초과했습니다.");
        }
        try {
            Object value = objectMapper.readValue(json, Object.class);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
                }
                return result;
            }
            throw new IllegalArgumentException("JSON object가 아닙니다.");
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
        String trimmed = text.length() > maxStringLength
                ? text.substring(0, maxStringLength) + "\n...[TRUNCATED]"
                : text;
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

    private String auditResultJson(Object result) {
        String json = RagJsonUtils.toJson(objectMapper, result);
        int max = Math.max(20000, properties.getAgentMaxToolOutputChars() * 2);
        if (json.length() <= max) return json;
        Map<String, Object> limited = new LinkedHashMap<>();
        limited.put("truncated", true);
        limited.put("originalCharacters", json.length());
        limited.put("preview", json.substring(0, max));
        return RagJsonUtils.toJson(objectMapper, limited);
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
        String insert = """
                INSERT INTO rag_agent_sql_query(
                    id, run_id, project_id, version_id, session_id, request_id, query_kind, reason,
                    original_sql, executed_sql, params_json, result_json, row_count, status, error_message, created_at
                ) VALUES (
                    :id, :runId, :projectId, :versionId, :sessionId, :requestId, :queryKind, :reason,
                    :originalSql, :executedSql, CAST(:paramsJson AS jsonb), CAST(:resultJson AS jsonb),
                    :rowCount, :status, :errorMessage, now()
                )
                """;
        try {
            jdbc.update(insert, new MapSqlParameterSource()
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
                    .addValue("resultJson", auditResultJson(result))
                    .addValue("rowCount", rowCount)
                    .addValue("status", status)
                    .addValue("errorMessage", errorMessage));
        } catch (Exception ignored) {
            // 감사 로그 장애가 실제 질의를 막지는 않도록 합니다.
        }
    }
}
