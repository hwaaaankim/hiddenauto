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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.config.RagOpenAiProperties;
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
    private final RagOpenAiProperties properties;
    private final TransactionTemplate transactionTemplate;

    public RagAgentChangeSetService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                    ObjectMapper objectMapper,
                                    RagRepository repository,
                                    RagAgentSqlExecutorService sqlExecutorService,
                                    RagAgentSqlSafetyService sqlSafetyService,
                                    RagOpenAiProperties properties,
                                    @Qualifier("ragTransactionManager") PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.sqlExecutorService = sqlExecutorService;
        this.sqlSafetyService = sqlSafetyService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Map<String, Object> persistAndMaybeApply(UUID runId,
                                                    UUID projectId,
                                                    UUID versionId,
                                                    UUID sessionId,
                                                    String sourceScope,
                                                    Map<String, Object> changeSet,
                                                    boolean forceSave) {
        UUID changeSetId = UUID.randomUUID();
        String title = truncate(text(changeSet.get("title"), "GPT Agent 변경 계획"), 500);
        String summary = text(changeSet.get("summary"), "");
        BigDecimal confidence = decimal(changeSet.get("confidence"), new BigDecimal("0.0000"));
        List<Map<String, Object>> items = listOfMaps(changeSet.get("items"));
        if (items.size() > properties.getAgentMaxChangeItems()) {
            throw new IllegalArgumentException("ChangeSet 항목 수(" + items.size() + ")가 서버 제한("
                    + properties.getAgentMaxChangeItems() + ")을 초과했습니다.");
        }
        boolean modelRequiresConfirmation = bool(changeSet.get("requiresConfirmation"), true);
        boolean requiresConfirmation = modelRequiresConfirmation
                || requiresServerConfirmation(items, confidence, sourceScope);
        String initialStatus = requiresConfirmation && !forceSave ? "PENDING_REVIEW" : "VALIDATING";
        List<Map<String, Object>> itemResults = new ArrayList<>();

        transactionTemplate.executeWithoutResult(transactionStatus -> {
            insertChangeSet(changeSetId, runId, projectId, versionId, sessionId, sourceScope, title, summary,
                    confidence, requiresConfirmation, initialStatus,
                    text(changeSet.get("conflictReportJson"), "{}"), changeSet);

            int ordinal = 0;
            for (Map<String, Object> item : items) {
                ordinal++;
                UUID itemId = UUID.randomUUID();
                String operation = text(item.get("operation"), "READ_ONLY_NOTE").toUpperCase();
                String targetTable = text(item.get("targetTable"), "");
                String targetIdText = text(item.get("targetId"), "");
                String writeSql = text(item.get("writeSql"), "");
                String paramsJson = normalizeJsonObject(text(item.get("paramsJson"), "{}"), "paramsJson");
                String beforeJson = normalizeJsonValue(text(item.get("beforeJson"), "{}"), "beforeJson");
                String afterJson = normalizeJsonValue(text(item.get("afterJson"), "{}"), "afterJson");
                String validationStatus = validateItem(operation, targetTable, targetIdText, writeSql, paramsJson);
                insertChangeItem(itemId, changeSetId, ordinal, operation, targetTable, targetIdText,
                        writeSql, paramsJson, beforeJson,
                        afterJson, text(item.get("reason"), ""),
                        text(item.get("impact"), ""), validationStatus, "PENDING", null);
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("id", itemId);
                one.put("operation", operation);
                one.put("targetTable", targetTable);
                one.put("targetId", targetIdText);
                one.put("validationStatus", validationStatus);
                itemResults.add(one);
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("changeSetId", changeSetId);
        result.put("title", title);
        result.put("summary", summary);
        result.put("confidence", confidence);
        result.put("requiresConfirmation", requiresConfirmation);
        result.put("status", initialStatus);
        result.put("items", itemResults);

        if (!hasEffectiveChange(items)) {
            markChangeSet(changeSetId, "NO_CHANGE_ITEMS", null);
            result.put("status", "NO_CHANGE_ITEMS");
            result.put("applied", false);
            return result;
        }
        if (requiresConfirmation && !forceSave) {
            markChangeSet(changeSetId, "PENDING_REVIEW", null);
            result.put("status", "PENDING_REVIEW");
            result.put("applied", false);
            result.put("message", "가격/수정/삭제/대량변경/낮은 신뢰도 등 서버 안전조건으로 변경계획을 보류했습니다. 관리자 확인 후 적용해야 합니다.");
            return result;
        }
        return applyPersisted(runId, projectId, versionId, sessionId, changeSetId, items, result);
    }

    public Map<String, Object> applyExisting(UUID changeSetId, boolean force) {
        Map<String, Object> header = jdbc.queryForMap("""
                SELECT *
                FROM rag_agent_change_set
                WHERE id = :id
                """, Map.of("id", changeSetId));
        String currentStatus = text(header.get("status"), "");
        if ("APPLIED".equalsIgnoreCase(currentStatus)) {
            Map<String, Object> result = detail(changeSetId);
            result.put("applied", true);
            result.put("alreadyApplied", true);
            result.put("message", "이미 적용된 변경계획이므로 중복 실행하지 않았습니다.");
            return result;
        }
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
                SELECT id, change_set_id, ordinal_no, operation, target_table, target_id, write_sql,
                       params_json::text AS params_json_text,
                       before_json::text AS before_json_text,
                       after_json::text AS after_json_text,
                       reason, impact, validation_status, apply_status, error_message, created_at, applied_at
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
            item.put("paramsJson", text(row.get("params_json_text"), "{}"));
            item.put("beforeJson", text(row.get("before_json_text"), "{}"));
            item.put("afterJson", text(row.get("after_json_text"), "{}"));
            item.put("reason", row.get("reason"));
            item.put("impact", row.get("impact"));
            items.add(item);
        }
        Map<String, Object> base = detail(changeSetId);
        return applyPersisted(runId, projectId, versionId, sessionId, changeSetId, items, base);
    }

    private Map<String, Object> applyPersisted(UUID runId,
                                               UUID projectId,
                                               UUID versionId,
                                               UUID sessionId,
                                               UUID changeSetId,
                                               List<Map<String, Object>> items,
                                               Map<String, Object> result) {
        try {
            Integer applied = transactionTemplate.execute(transactionStatus -> {
                claimChangeSet(changeSetId);
                int count = applyItems(runId, projectId, versionId, sessionId, changeSetId, items);
                markChangeSet(changeSetId, "APPLIED", null);
                repository.publishVersion(projectId, versionId);
                return count;
            });
            result.put("status", "APPLIED");
            result.put("applied", true);
            result.put("appliedItemCount", applied == null ? 0 : applied);
            return result;
        } catch (ChangeSetClaimException e) {
            Map<String, Object> current = detail(changeSetId);
            current.put("applied", "APPLIED".equalsIgnoreCase(text(current.get("status"), "")));
            current.put("message", e.getMessage());
            return current;
        } catch (ChangeApplyException e) {
            updateItemStatus(changeSetId, e.ordinalNo(), "FAILED", e.getCauseMessage());
            markChangeSet(changeSetId, "FAILED", e.getCauseMessage());
            result.put("status", "FAILED");
            result.put("applied", false);
            result.put("failedOrdinalNo", e.ordinalNo());
            result.put("error", e.getCauseMessage());
            result.put("message", "변경 트랜잭션이 롤백되었습니다. 실패 항목과 SQL 로그를 확인해 주세요.");
            return result;
        } catch (RuntimeException e) {
            markChangeSet(changeSetId, "FAILED", e.getMessage());
            result.put("status", "FAILED");
            result.put("applied", false);
            result.put("error", e.getMessage());
            result.put("message", "변경 트랜잭션이 롤백되었습니다.");
            return result;
        }
    }

    public Map<String, Object> detail(UUID changeSetId) {
        return detailInternal(changeSetId, null, null, null);
    }

    /** GPT 도구에서 조회할 때는 현재 project/version/session 범위를 반드시 강제합니다. */
    public Map<String, Object> detailScoped(UUID changeSetId,
                                            UUID projectId,
                                            UUID versionId,
                                            UUID sessionId) {
        if (projectId == null || versionId == null) {
            throw new IllegalArgumentException("ChangeSet 범위 조회에는 projectId와 versionId가 필요합니다.");
        }
        return detailInternal(changeSetId, projectId, versionId, sessionId);
    }

    private Map<String, Object> detailInternal(UUID changeSetId,
                                               UUID projectId,
                                               UUID versionId,
                                               UUID sessionId) {
        if (changeSetId == null) throw new IllegalArgumentException("changeSetId가 필요합니다.");
        StringBuilder headerSql = new StringBuilder("""
                SELECT *
                FROM rag_agent_change_set
                WHERE id = :id
                """);
        MapSqlParameterSource params = new MapSqlParameterSource("id", changeSetId);
        if (projectId != null) {
            headerSql.append(" AND project_id = :projectId");
            params.addValue("projectId", projectId);
        }
        if (versionId != null) {
            headerSql.append(" AND version_id = :versionId");
            params.addValue("versionId", versionId);
        }
        if (sessionId != null) {
            headerSql.append(" AND session_id = :sessionId");
            params.addValue("sessionId", sessionId);
        }
        List<Map<String, Object>> headers = jdbc.queryForList(headerSql.toString(), params);
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("현재 프로젝트/버전/세션 범위에서 ChangeSet을 찾을 수 없습니다.");
        }
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT *
                FROM rag_agent_change_item
                WHERE change_set_id = :changeSetId
                ORDER BY ordinal_no ASC
                """, Map.of("changeSetId", changeSetId));
        Map<String, Object> result = new LinkedHashMap<>(headers.get(0));
        result.put("items", items);
        return result;
    }

    private String validateItem(String operation,
                                String targetTable,
                                String targetId,
                                String writeSql,
                                String paramsJson) {
        if ("READ_ONLY_NOTE".equals(operation)) return "VALID";
        if ("INSERT_KNOWLEDGE_NODE".equals(operation)) {
            if (StringUtils.hasText(targetTable) && !"rag_knowledge_node".equals(targetTable)) {
                throw new IllegalArgumentException("INSERT_KNOWLEDGE_NODE의 targetTable은 rag_knowledge_node여야 합니다.");
            }
            return "VALID";
        }
        if (!("UPDATE_SQL".equals(operation)
                || "INSERT_SQL".equals(operation)
                || "DELETE_SQL".equals(operation)
                || "SOFT_DELETE_SQL".equals(operation))) {
            throw new IllegalArgumentException("허용되지 않은 ChangeSet operation입니다: " + operation
                    + ". 허용값: READ_ONLY_NOTE, INSERT_KNOWLEDGE_NODE, INSERT_SQL, UPDATE_SQL, SOFT_DELETE_SQL, DELETE_SQL");
        }
        if (!StringUtils.hasText(writeSql)) {
            throw new IllegalArgumentException(operation + " 변경 항목에는 writeSql이 필요합니다.");
        }
        sqlExecutorService.parseObject(paramsJson);
        RagAgentSqlSafetyService.ValidatedSql validated = sqlSafetyService.validateWriteSql(writeSql);
        String sqlTarget = sqlSafetyService.writeTargetTable(validated.sql());
        if (!StringUtils.hasText(targetTable)) {
            throw new IllegalArgumentException(operation + " 변경 항목에는 targetTable이 필요합니다.");
        }
        String normalizedTarget = targetTable.replace("public.", "").replace("\"", "").trim();
        if (!normalizedTarget.equalsIgnoreCase(sqlTarget)) {
            throw new IllegalArgumentException("targetTable과 실제 SQL 대상이 다릅니다. targetTable="
                    + normalizedTarget + ", sqlTarget=" + sqlTarget);
        }

        String lower = validated.sql().trim().toLowerCase();
        if ("INSERT_SQL".equals(operation) && !lower.startsWith("insert into ")) {
            throw new IllegalArgumentException("INSERT_SQL operation은 INSERT INTO SQL이어야 합니다.");
        }
        if ("UPDATE_SQL".equals(operation) && !lower.startsWith("update ")) {
            throw new IllegalArgumentException("UPDATE_SQL operation은 UPDATE SQL이어야 합니다.");
        }
        if ("SOFT_DELETE_SQL".equals(operation)) {
            if (!lower.startsWith("update ")) {
                throw new IllegalArgumentException("SOFT_DELETE_SQL operation은 UPDATE SQL이어야 합니다.");
            }
            if (!(lower.contains("active") || lower.contains("status"))) {
                throw new IllegalArgumentException("SOFT_DELETE_SQL에는 active 또는 status 변경이 포함되어야 합니다.");
            }
        }
        if (RagAgentDataAccessPolicy.indirectScopeRule(normalizedTarget).isPresent()
                && !"INSERT_SQL".equals(operation)
                && parseUuid(targetId) == null) {
            throw new IllegalArgumentException(normalizedTarget
                    + " 자식 테이블의 수정/soft delete/delete에는 유효한 UUID targetId가 필요합니다.");
        }
        if ("DELETE_SQL".equals(operation)) {
            if (!lower.startsWith("delete from ")) {
                throw new IllegalArgumentException("DELETE_SQL operation은 DELETE FROM SQL이어야 합니다.");
            }
            if (parseUuid(targetId) == null) {
                throw new IllegalArgumentException("DELETE_SQL에는 유효한 UUID targetId가 필요합니다.");
            }
        }
        return "VALID";
    }

    private boolean requiresServerConfirmation(List<Map<String, Object>> items,
                                               BigDecimal confidence,
                                               String sourceScope) {
        if (hasEffectiveChange(items) && !"LEARNING".equalsIgnoreCase(text(sourceScope, ""))) return true;
        if (confidence == null || confidence.compareTo(new BigDecimal("0.8500")) < 0) return true;
        if (items.size() > 10) return true;
        for (Map<String, Object> item : items) {
            String operation = text(item.get("operation"), "READ_ONLY_NOTE").toUpperCase();
            String table = text(item.get("targetTable"), "").toLowerCase();
            if ("DELETE_SQL".equals(operation)
                    || "SOFT_DELETE_SQL".equals(operation)
                    || "UPDATE_SQL".equals(operation)) return true;
            if (table.contains("pricing") || table.contains("price_matrix")
                    || table.contains("canonical") || table.contains("dialog")) return true;
        }
        return false;
    }

    private boolean hasEffectiveChange(List<Map<String, Object>> items) {
        for (Map<String, Object> item : items) {
            String operation = text(item.get("operation"), "READ_ONLY_NOTE").toUpperCase();
            if (!"READ_ONLY_NOTE".equals(operation)) return true;
        }
        return false;
    }

    private void claimChangeSet(UUID changeSetId) {
        int claimed = jdbc.update("""
                UPDATE rag_agent_change_set
                SET status = 'APPLYING', updated_at = now()
                WHERE id = :id
                  AND status IN ('VALIDATING', 'PENDING_REVIEW', 'FAILED')
                """, new MapSqlParameterSource("id", changeSetId));
        if (claimed == 1) return;
        String current;
        try {
            current = jdbc.queryForObject("SELECT status FROM rag_agent_change_set WHERE id = :id",
                    new MapSqlParameterSource("id", changeSetId), String.class);
        } catch (Exception e) {
            current = "NOT_FOUND";
        }
        throw new ChangeSetClaimException("변경계획을 적용할 수 없습니다. 현재 상태=" + current
                + ". 이미 적용 중이거나 적용 완료된 계획일 수 있습니다.");
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
                throw new ChangeApplyException(ordinal, e);
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

    private static final class ChangeSetClaimException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private ChangeSetClaimException(String message) {
            super(message);
        }
    }

    private static final class ChangeApplyException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int ordinalNo;

        private ChangeApplyException(int ordinalNo, Throwable cause) {
            super(cause == null ? null : cause.getMessage(), cause);
            this.ordinalNo = ordinalNo;
        }

        private int ordinalNo() {
            return ordinalNo;
        }

        private String getCauseMessage() {
            Throwable cause = getCause();
            return cause == null ? getMessage() : cause.getMessage();
        }
    }

    private String jsonOrEmptyObject(String json) {
        return normalizeJsonValue(json, "JSON");
    }

    private String normalizeJsonObject(String json, String fieldName) {
        String normalized = normalizeJsonValue(json, fieldName);
        try {
            Object parsed = objectMapper.readValue(normalized, Object.class);
            if (!(parsed instanceof Map<?, ?>)) {
                throw new IllegalArgumentException(fieldName + "은 JSON object여야 합니다.");
            }
            return normalized;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " JSON 검증 실패: " + e.getMessage(), e);
        }
    }

    private String normalizeJsonValue(String json, String fieldName) {
        String value = StringUtils.hasText(json) ? json.trim() : "{}";
        if (value.length() > properties.getAgentMaxParamsChars()) {
            throw new IllegalArgumentException(fieldName + " 길이가 서버 제한("
                    + properties.getAgentMaxParamsChars() + "자)을 초과했습니다.");
        }
        try {
            Object parsed = objectMapper.readValue(value, Object.class);
            if (!(parsed instanceof Map<?, ?>) && !(parsed instanceof List<?>)) {
                throw new IllegalArgumentException(fieldName + "은 JSON object 또는 array여야 합니다.");
            }
            return objectMapper.writeValueAsString(parsed);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " JSON 검증 실패: " + e.getMessage(), e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
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
