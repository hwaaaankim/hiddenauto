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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagAgentChangeSetService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RagRepository repository;
    private final RagAgentSqlExecutorService sqlExecutorService;
    private final RagAgentSqlSafetyService sqlSafetyService;

    public RagAgentChangeSetService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                    ObjectMapper objectMapper,
                                    RagRepository repository,
                                    RagAgentSqlExecutorService sqlExecutorService,
                                    RagAgentSqlSafetyService sqlSafetyService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.sqlExecutorService = sqlExecutorService;
        this.sqlSafetyService = sqlSafetyService;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> persistAndMaybeApply(UUID runId,
                                                    UUID projectId,
                                                    UUID versionId,
                                                    UUID sessionId,
                                                    String sourceScope,
                                                    Map<String, Object> changeSet,
                                                    boolean forceSave) {
        UUID changeSetId = UUID.randomUUID();
        String title = text(changeSet.get("title"), "GPT Agent 변경 계획");
        String summary = text(changeSet.get("summary"), "");
        BigDecimal confidence = decimal(changeSet.get("confidence"), new BigDecimal("0.0000"));
        boolean requiresConfirmation = bool(changeSet.get("requiresConfirmation"), true);
        List<Map<String, Object>> items = listOfMaps(changeSet.get("items"));
        String status = requiresConfirmation && !forceSave ? "PENDING_REVIEW" : "VALIDATING";

        insertChangeSet(changeSetId, runId, projectId, versionId, sessionId, sourceScope, title, summary,
                confidence, requiresConfirmation, status, text(changeSet.get("conflictReportJson"), "{}"), changeSet);

        int ordinal = 0;
        List<Map<String, Object>> itemResults = new ArrayList<>();
        for (Map<String, Object> item : items) {
            ordinal++;
            UUID itemId = UUID.randomUUID();
            String operation = text(item.get("operation"), "READ_ONLY_NOTE").toUpperCase();
            String targetTable = text(item.get("targetTable"), "");
            String targetIdText = text(item.get("targetId"), "");
            String writeSql = text(item.get("writeSql"), "");
            String paramsJson = text(item.get("paramsJson"), "{}");
            String validationStatus = validateItem(operation, writeSql);
            insertChangeItem(itemId, changeSetId, ordinal, operation, targetTable, targetIdText, writeSql, paramsJson,
                    text(item.get("beforeJson"), "{}"), text(item.get("afterJson"), "{}"), text(item.get("reason"), ""),
                    text(item.get("impact"), ""), validationStatus, "PENDING", null);
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", itemId);
            one.put("operation", operation);
            one.put("targetTable", targetTable);
            one.put("targetId", targetIdText);
            one.put("validationStatus", validationStatus);
            itemResults.add(one);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changeSetId", changeSetId);
        result.put("title", title);
        result.put("summary", summary);
        result.put("confidence", confidence);
        result.put("requiresConfirmation", requiresConfirmation);
        result.put("status", status);
        result.put("items", itemResults);

        if (items.isEmpty()) {
            markChangeSet(changeSetId, "NO_CHANGE_ITEMS", null);
            result.put("status", "NO_CHANGE_ITEMS");
            result.put("applied", false);
            return result;
        }
        if (requiresConfirmation && !forceSave) {
            markChangeSet(changeSetId, "PENDING_REVIEW", null);
            result.put("status", "PENDING_REVIEW");
            result.put("applied", false);
            result.put("message", "모순/연관 영향 가능성이 있어 자동 저장하지 않고 변경계획을 보류했습니다. forceSave=true 또는 화면 확인 후 적용해야 합니다.");
            return result;
        }

        int applied = applyItems(runId, projectId, versionId, sessionId, changeSetId, items);
        markChangeSet(changeSetId, "APPLIED", null);
        repository.publishVersion(projectId, versionId);
        result.put("status", "APPLIED");
        result.put("applied", true);
        result.put("appliedItemCount", applied);
        return result;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> applyExisting(UUID changeSetId, boolean force) {
        Map<String, Object> header = jdbc.queryForMap("""
                SELECT *
                FROM rag_agent_change_set
                WHERE id = :id
                """, Map.of("id", changeSetId));
        boolean requiresConfirmation = bool(header.get("requires_confirmation"), true);
        if (requiresConfirmation && !force) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("changeSetId", changeSetId);
            result.put("status", "PENDING_REVIEW");
            result.put("applied", false);
            result.put("message", "확인 필요 변경계획입니다. force=true로 명시 적용해야 합니다.");
            return result;
        }
        UUID runId = (UUID) header.get("run_id");
        UUID projectId = (UUID) header.get("project_id");
        UUID versionId = (UUID) header.get("version_id");
        UUID sessionId = (UUID) header.get("session_id");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT *
                FROM rag_agent_change_item
                WHERE change_set_id = :changeSetId
                ORDER BY ordinal_no ASC
                """, Map.of("changeSetId", changeSetId));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("operation", row.get("operation"));
            item.put("targetTable", row.get("target_table"));
            item.put("targetId", row.get("target_id"));
            item.put("writeSql", row.get("write_sql"));
            item.put("paramsJson", RagJsonUtils.toJson(objectMapper, row.get("params_json")));
            item.put("beforeJson", RagJsonUtils.toJson(objectMapper, row.get("before_json")));
            item.put("afterJson", RagJsonUtils.toJson(objectMapper, row.get("after_json")));
            item.put("reason", row.get("reason"));
            item.put("impact", row.get("impact"));
            items.add(item);
        }
        int applied = applyItems(runId, projectId, versionId, sessionId, changeSetId, items);
        markChangeSet(changeSetId, "APPLIED", null);
        repository.publishVersion(projectId, versionId);
        Map<String, Object> result = detail(changeSetId);
        result.put("applied", true);
        result.put("appliedItemCount", applied);
        return result;
    }

    public Map<String, Object> detail(UUID changeSetId) {
        Map<String, Object> header = jdbc.queryForMap("""
                SELECT *
                FROM rag_agent_change_set
                WHERE id = :id
                """, Map.of("id", changeSetId));
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT *
                FROM rag_agent_change_item
                WHERE change_set_id = :changeSetId
                ORDER BY ordinal_no ASC
                """, Map.of("changeSetId", changeSetId));
        Map<String, Object> result = new LinkedHashMap<>(header);
        result.put("items", items);
        return result;
    }

    private String validateItem(String operation, String writeSql) {
        if ("READ_ONLY_NOTE".equals(operation)) return "VALID";
        if ("INSERT_KNOWLEDGE_NODE".equals(operation)) return "VALID";
        if ("UPDATE_SQL".equals(operation) || "INSERT_SQL".equals(operation)) {
            if (!StringUtils.hasText(writeSql)) {
                throw new IllegalArgumentException(operation + " 변경 항목에는 writeSql이 필요합니다.");
            }
            sqlSafetyService.validateWriteSql(writeSql);
            return "VALID";
        }
        throw new IllegalArgumentException("허용되지 않은 ChangeSet operation입니다: " + operation);
    }

    private int applyItems(UUID runId,
                           UUID projectId,
                           UUID versionId,
                           UUID sessionId,
                           UUID changeSetId,
                           List<Map<String, Object>> items) {
        int applied = 0;
        int ordinal = 0;
        for (Map<String, Object> item : items) {
            ordinal++;
            String operation = text(item.get("operation"), "READ_ONLY_NOTE").toUpperCase();
            String writeSql = text(item.get("writeSql"), "");
            String paramsJson = text(item.get("paramsJson"), "{}");
            String targetIdText = text(item.get("targetId"), "");
            UUID targetId = parseUuid(targetIdText);
            try {
                if ("READ_ONLY_NOTE".equals(operation)) {
                    updateItemStatus(changeSetId, ordinal, "SKIPPED", null);
                    continue;
                }
                if ("INSERT_KNOWLEDGE_NODE".equals(operation)) {
                    insertKnowledgeNodeFromItem(projectId, versionId, item);
                    applied++;
                    updateItemStatus(changeSetId, ordinal, "APPLIED", null);
                    continue;
                }
                int updated = sqlExecutorService.executeWrite(runId, projectId, versionId, sessionId,
                        "changeset-" + ordinal, text(item.get("reason"), ""), writeSql, paramsJson, targetId);
                applied += updated;
                updateItemStatus(changeSetId, ordinal, "APPLIED", null);
            } catch (Exception e) {
                updateItemStatus(changeSetId, ordinal, "FAILED", e.getMessage());
                markChangeSet(changeSetId, "FAILED", e.getMessage());
                throw e;
            }
        }
        return applied;
    }

    private void insertKnowledgeNodeFromItem(UUID projectId, UUID versionId, Map<String, Object> item) {
        Map<String, Object> after = parseJsonObject(text(item.get("afterJson"), "{}"));
        String topic = text(after.get("topic"), "agent-learned");
        String title = text(after.get("title"), text(item.get("reason"), "GPT Agent 학습 지식"));
        String rawText = text(after.get("rawText"), text(after.get("raw_text"), text(item.get("afterJson"), "")));
        String summary = text(after.get("summary"), text(item.get("reason"), ""));
        String nodeType = text(after.get("nodeType"), text(after.get("node_type"), "AGENT_KNOWLEDGE"));
        String nodeKey = text(after.get("nodeKey"), text(after.get("node_key"), title));
        Object structuredJson = after.getOrDefault("structuredJson", after);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "GPT_SQL_AGENT_CHANGESET");
        metadata.put("reason", item.get("reason"));
        UUID documentId = UUID.randomUUID();
        repository.insertDocument(documentId, projectId, versionId, topic, "GPT_SQL_AGENT", title, null, rawText, metadata);
        repository.insertChunkWithoutEmbedding(UUID.randomUUID(), documentId, projectId, versionId, 1, topic, rawText, metadata);
        repository.insertKnowledgeNode(UUID.randomUUID(), null, projectId, versionId, documentId, topic, nodeType, nodeKey, title, summary,
                rawText, structuredJson, metadata, true, 0, 0, "AI_PARSED", false, 0, null, null);
    }

    private void insertChangeSet(UUID id,
                                 UUID runId,
                                 UUID projectId,
                                 UUID versionId,
                                 UUID sessionId,
                                 String sourceScope,
                                 String title,
                                 String summary,
                                 BigDecimal confidence,
                                 boolean requiresConfirmation,
                                 String status,
                                 String conflictReportJson,
                                 Object rawChangeSet) {
        String sql = """
                INSERT INTO rag_agent_change_set(
                    id, run_id, project_id, version_id, session_id, source_scope, title, summary,
                    confidence, requires_confirmation, status, conflict_report_json, raw_change_set_json, created_at, updated_at
                ) VALUES (
                    :id, :runId, :projectId, :versionId, :sessionId, :sourceScope, :title, :summary,
                    :confidence, :requiresConfirmation, :status, CAST(:conflictReportJson AS jsonb), CAST(:rawChangeSetJson AS jsonb), now(), now()
                )
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("runId", runId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("sessionId", sessionId)
                .addValue("sourceScope", sourceScope)
                .addValue("title", title)
                .addValue("summary", summary)
                .addValue("confidence", confidence)
                .addValue("requiresConfirmation", requiresConfirmation)
                .addValue("status", status)
                .addValue("conflictReportJson", jsonOrEmptyObject(conflictReportJson))
                .addValue("rawChangeSetJson", RagJsonUtils.toJson(objectMapper, rawChangeSet)));
    }

    private void insertChangeItem(UUID id,
                                  UUID changeSetId,
                                  int ordinalNo,
                                  String operation,
                                  String targetTable,
                                  String targetId,
                                  String writeSql,
                                  String paramsJson,
                                  String beforeJson,
                                  String afterJson,
                                  String reason,
                                  String impact,
                                  String validationStatus,
                                  String applyStatus,
                                  String errorMessage) {
        String sql = """
                INSERT INTO rag_agent_change_item(
                    id, change_set_id, ordinal_no, operation, target_table, target_id, write_sql, params_json,
                    before_json, after_json, reason, impact, validation_status, apply_status, error_message, created_at
                ) VALUES (
                    :id, :changeSetId, :ordinalNo, :operation, :targetTable, :targetId, :writeSql, CAST(:paramsJson AS jsonb),
                    CAST(:beforeJson AS jsonb), CAST(:afterJson AS jsonb), :reason, :impact, :validationStatus, :applyStatus, :errorMessage, now()
                )
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("changeSetId", changeSetId)
                .addValue("ordinalNo", ordinalNo)
                .addValue("operation", operation)
                .addValue("targetTable", targetTable)
                .addValue("targetId", StringUtils.hasText(targetId) ? targetId : null)
                .addValue("writeSql", writeSql)
                .addValue("paramsJson", jsonOrEmptyObject(paramsJson))
                .addValue("beforeJson", jsonOrEmptyObject(beforeJson))
                .addValue("afterJson", jsonOrEmptyObject(afterJson))
                .addValue("reason", reason)
                .addValue("impact", impact)
                .addValue("validationStatus", validationStatus)
                .addValue("applyStatus", applyStatus)
                .addValue("errorMessage", errorMessage));
    }

    private void markChangeSet(UUID changeSetId, String status, String errorMessage) {
        jdbc.update("""
                UPDATE rag_agent_change_set
                SET status = :status,
                    error_message = :errorMessage,
                    applied_at = CASE WHEN :status = 'APPLIED' THEN now() ELSE applied_at END,
                    updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", changeSetId)
                .addValue("status", status)
                .addValue("errorMessage", errorMessage));
    }

    private void updateItemStatus(UUID changeSetId, int ordinalNo, String status, String errorMessage) {
        jdbc.update("""
                UPDATE rag_agent_change_item
                SET apply_status = :status,
                    error_message = :errorMessage,
                    applied_at = CASE WHEN :status = 'APPLIED' THEN now() ELSE applied_at END
                WHERE change_set_id = :changeSetId
                  AND ordinal_no = :ordinalNo
                """, new MapSqlParameterSource()
                .addValue("changeSetId", changeSetId)
                .addValue("ordinalNo", ordinalNo)
                .addValue("status", status)
                .addValue("errorMessage", errorMessage));
    }

    private String jsonOrEmptyObject(String json) {
        if (!StringUtils.hasText(json)) return "{}";
        String t = json.trim();
        return (t.startsWith("{") || t.startsWith("[")) ? t : "{}";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        if (e.getKey() != null) copy.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    result.add(copy);
                }
            }
            return result;
        }
        return List.of();
    }

    private Map<String, Object> parseJsonObject(String json) {
        try {
            Object value = objectMapper.readValue(jsonOrEmptyObject(json), Object.class);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
                }
                return result;
            }
            return new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private UUID parseUuid(String text) {
        if (!StringUtils.hasText(text)) return null;
        try {
            return UUID.fromString(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String text(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        if (value == null) return fallback;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return value == null ? fallback : new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }
}
