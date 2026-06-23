package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagCanonicalKnowledgeService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagCanonicalKnowledgeService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                        ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> rebuild(UUID projectId,
                                       UUID versionId,
                                       UUID runId,
                                       UUID sessionId,
                                       String instruction,
                                       boolean activate) {
        UUID datasetId = UUID.randomUUID();
        insertDataset(datasetId, projectId, versionId, runId, instruction);

        List<Map<String, Object>> rows = findActiveStructuredRows(projectId, versionId);
        CanonicalBuildState state = new CanonicalBuildState(datasetId, projectId, versionId);
        for (Map<String, Object> row : rows) {
            canonicalizeStructuredRow(state, row);
        }
        writeIssues(state);
        writeDefaultPricingRule(state, instruction);
        writeDefaultDialogFlow(state, instruction);

        if (activate) {
            jdbc.update("""
                    UPDATE rag_canonical_dataset
                    SET active = false, updated_at = now()
                    WHERE project_id = :projectId
                      AND version_id = :versionId
                      AND id <> :datasetId
                    """, new MapSqlParameterSource()
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("datasetId", datasetId));
        }

        Map<String, Object> summary = state.summary();
        jdbc.update("""
                UPDATE rag_canonical_dataset
                SET status = 'COMPLETED',
                    active = :active,
                    summary_json = CAST(:summaryJson AS jsonb),
                    completed_at = now(),
                    updated_at = now()
                WHERE id = :datasetId
                """, new MapSqlParameterSource()
                .addValue("datasetId", datasetId)
                .addValue("active", activate)
                .addValue("summaryJson", toJson(summary)));

        insertChangeEvent(projectId, versionId, datasetId, runId, instruction, summary);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", datasetId);
        result.put("active", activate);
        result.put("summary", summary);
        return result;
    }

    public Map<String, Object> summary(UUID projectId, UUID versionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> dataset = findActiveDataset(projectId, versionId);
        result.put("activeDataset", dataset);
        if (dataset.isEmpty()) {
            result.put("message", "활성 정본 데이터셋이 없습니다. canonical rebuild를 먼저 실행해야 합니다.");
            return result;
        }
        UUID datasetId = (UUID) dataset.get("id");
        result.put("counts", jdbc.queryForList("""
                SELECT 'entity' AS kind, count(*) AS cnt FROM rag_canonical_entity WHERE dataset_id = :datasetId AND active = true
                UNION ALL SELECT 'fact', count(*) FROM rag_canonical_fact WHERE dataset_id = :datasetId AND active = true
                UNION ALL SELECT 'pricing_rule', count(*) FROM rag_canonical_pricing_rule WHERE dataset_id = :datasetId AND active = true
                UNION ALL SELECT 'dialog_flow', count(*) FROM rag_canonical_dialog_flow WHERE dataset_id = :datasetId AND active = true
                UNION ALL SELECT 'quality_issue', count(*) FROM rag_canonical_quality_issue WHERE dataset_id = :datasetId
                """, Map.of("datasetId", datasetId)));
        result.put("factTypes", jdbc.queryForList("""
                SELECT fact_type, count(*) AS cnt
                FROM rag_canonical_fact
                WHERE dataset_id = :datasetId AND active = true
                GROUP BY fact_type
                ORDER BY cnt DESC
                """, Map.of("datasetId", datasetId)));
        result.put("sampleFacts", jdbc.queryForList("""
                SELECT subject_name, fact_type, fact_key, factor_json, value_json
                FROM rag_canonical_fact
                WHERE dataset_id = :datasetId AND active = true
                ORDER BY created_at ASC
                LIMIT 20
                """, Map.of("datasetId", datasetId)));
        result.put("issues", jdbc.queryForList("""
                SELECT issue_type, severity, count(*) AS cnt
                FROM rag_canonical_quality_issue
                WHERE dataset_id = :datasetId
                GROUP BY issue_type, severity
                ORDER BY severity DESC, cnt DESC
                """, Map.of("datasetId", datasetId)));
        return result;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> applyDynamicFactChange(UUID projectId, UUID versionId, UUID runId, String instruction, Map<String, Object> change) {
        Map<String, Object> dataset = findActiveDataset(projectId, versionId);
        if (dataset.isEmpty()) {
            Map<String, Object> rebuilt = rebuild(projectId, versionId, runId, null, "동적 변경 전 정본 자동 생성", true);
            dataset = findActiveDataset(projectId, versionId);
            change.put("autoRebuild", rebuilt);
        }
        UUID datasetId = (UUID) dataset.get("id");
        String subjectKey = stringValue(change.getOrDefault("subjectKey", "DYNAMIC:" + shortHash(stableJson(change))));
        String subjectName = stringValue(change.getOrDefault("subjectName", subjectKey));
        String factType = stringValue(change.getOrDefault("factType", "DYNAMIC_CHANGE"));
        String factKey = stringValue(change.getOrDefault("factKey", "USER_CHANGE"));
        Map<String, Object> factorJson = parseJsonMap(change.getOrDefault("factorJson", Map.of()));
        Map<String, Object> valueJson = parseJsonMap(change.getOrDefault("valueJson", change));
        UUID changeEventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO rag_canonical_change_event(
                    id, project_id, version_id, dataset_id, event_type, instruction, before_json, after_json, impact_json, source_scope, created_by_run_id, created_at
                ) VALUES (
                    :id, :projectId, :versionId, :datasetId, :eventType, :instruction, '{}'::jsonb, CAST(:afterJson AS jsonb), CAST(:impactJson AS jsonb), 'GPT_SQL_AGENT', :runId, now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", changeEventId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("datasetId", datasetId)
                .addValue("eventType", factType)
                .addValue("instruction", instruction)
                .addValue("afterJson", toJson(change))
                .addValue("impactJson", toJson(Map.of("policy", "이전 raw data는 보존하고 canonical fact 이력으로 변경 반영")))
                .addValue("runId", runId));
        jdbc.update("""
                INSERT INTO rag_canonical_fact(
                    id, dataset_id, project_id, version_id, entity_type, subject_key, subject_name, fact_type, fact_key,
                    factor_json, value_json, status, active, confidence, source_json, changed_by_instruction, change_event_id, created_at, updated_at
                ) VALUES (
                    :id, :datasetId, :projectId, :versionId, 'DYNAMIC_ENTITY', :subjectKey, :subjectName, :factType, :factKey,
                    CAST(:factorJson AS jsonb), CAST(:valueJson AS jsonb), :status, true, :confidence, CAST(:sourceJson AS jsonb), :instruction, :changeEventId, now(), now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("datasetId", datasetId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("subjectKey", subjectKey)
                .addValue("subjectName", subjectName)
                .addValue("factType", factType)
                .addValue("factKey", factKey)
                .addValue("factorJson", toJson(factorJson))
                .addValue("valueJson", toJson(valueJson))
                .addValue("status", stringValue(change.getOrDefault("status", "ACTIVE")))
                .addValue("confidence", new BigDecimal("0.9200"))
                .addValue("sourceJson", toJson(change))
                .addValue("instruction", instruction)
                .addValue("changeEventId", changeEventId));
        return Map.of("applied", true, "datasetId", datasetId, "changeEventId", changeEventId, "change", change);
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> applyDialogFlowChange(UUID projectId, UUID versionId, UUID runId, String instruction, Map<String, Object> flowChange) {
        Map<String, Object> dataset = findActiveDataset(projectId, versionId);
        if (dataset.isEmpty()) {
            rebuild(projectId, versionId, runId, null, "질문 흐름 변경 전 정본 자동 생성", true);
            dataset = findActiveDataset(projectId, versionId);
        }
        UUID datasetId = (UUID) dataset.get("id");
        String flowKey = stringValue(flowChange.getOrDefault("flowKey", "GPT_DYNAMIC_FLOW:" + shortHash(stableJson(flowChange))));
        jdbc.update("""
                INSERT INTO rag_canonical_dialog_flow(
                    id, dataset_id, project_id, version_id, flow_key, purpose, question_flow_json, validation_json, condition_json, active, created_at, updated_at
                ) VALUES (
                    :id, :datasetId, :projectId, :versionId, :flowKey, :purpose, CAST(:flowJson AS jsonb), CAST(:validationJson AS jsonb), CAST(:conditionJson AS jsonb), true, now(), now()
                )
                ON CONFLICT(dataset_id, flow_key) DO UPDATE SET
                    purpose = EXCLUDED.purpose,
                    question_flow_json = EXCLUDED.question_flow_json,
                    validation_json = EXCLUDED.validation_json,
                    condition_json = EXCLUDED.condition_json,
                    active = true,
                    updated_at = now()
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("datasetId", datasetId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("flowKey", flowKey)
                .addValue("purpose", stringValue(flowChange.getOrDefault("purpose", instruction)))
                .addValue("flowJson", toJson(flowChange.getOrDefault("questionFlowJson", flowChange)))
                .addValue("validationJson", toJson(flowChange.getOrDefault("validationJson", Map.of())))
                .addValue("conditionJson", toJson(flowChange.getOrDefault("conditionJson", Map.of()))));
        return Map.of("applied", true, "datasetId", datasetId, "flowKey", flowKey);
    }

    private void insertDataset(UUID datasetId, UUID projectId, UUID versionId, UUID runId, String instruction) {
        jdbc.update("""
                INSERT INTO rag_canonical_dataset(
                    id, project_id, version_id, title, instruction, source_scope, build_mode, status, active, created_by_run_id, created_at, updated_at
                ) VALUES (
                    :id, :projectId, :versionId, :title, :instruction, 'CANONICAL_ENGINE', 'REBUILD_FROM_ACTIVE_STRUCTURED_DATA', 'RUNNING', false, :runId, now(), now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", datasetId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("title", "정본 데이터셋 " + LocalDate.now())
                .addValue("instruction", instruction)
                .addValue("runId", runId));
    }

    private List<Map<String, Object>> findActiveStructuredRows(UUID projectId, UUID versionId) {
        String sql = """
                SELECT t.id AS table_id,
                       t.table_key,
                       t.semantic_role,
                       t.sheet_name,
                       t.range_a1,
                       t.header_json,
                       t.metadata_json AS table_metadata_json,
                       a.id AS artifact_id,
                       a.artifact_key,
                       a.status AS artifact_status,
                       a.active AS artifact_active,
                       a.created_at AS artifact_created_at,
                       r.id AS row_id,
                       r.row_no,
                       r.row_json,
                       r.searchable_text
                FROM rag_structured_table t
                JOIN rag_knowledge_artifact a ON a.id = t.artifact_id
                JOIN rag_structured_table_row r ON r.table_id = t.id
                WHERE t.project_id = :projectId
                  AND t.version_id = :versionId
                  AND t.active = true
                  AND a.active = true
                  AND a.status = 'ACTIVE'
                ORDER BY a.created_at DESC, t.created_at DESC, r.row_no ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId));
    }

    private void canonicalizeStructuredRow(CanonicalBuildState state, Map<String, Object> row) {
        Map<String, Object> rowJson = parseJsonMap(row.get("row_json"));
        if (rowJson.isEmpty()) return;
        Map<String, Object> normalized = normalizeRow(rowJson);
        if (normalized.isEmpty()) return;

        Set<String> priceFields = detectPriceFields(normalized);
        Set<String> identityFields = detectIdentityFields(normalized, priceFields);
        String subjectName = findDisplayName(normalized, identityFields);
        String subjectKey = buildSubjectKey(normalized, identityFields, subjectName);
        Map<String, Object> identityJson = pick(normalized, identityFields);
        Map<String, Object> factorJson = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            if (!priceFields.contains(entry.getKey()) && StringUtils.hasText(stringValue(entry.getValue()))) {
                factorJson.put(entry.getKey(), entry.getValue());
            }
        }

        UUID entityId = state.entityIds.get(subjectKey);
        if (entityId == null) {
            entityId = UUID.randomUUID();
            state.entityIds.put(subjectKey, entityId);
            insertEntity(entityId, state, subjectKey, subjectName, identityJson, factorJson);
        }

        if (priceFields.isEmpty()) {
            insertOrSkipFact(state, entityId, subjectKey, subjectName, "ATTRIBUTE", "ROW_ATTRIBUTES", factorJson,
                    Map.of("row", normalized), row);
            return;
        }

        for (String priceField : priceFields) {
            Object value = normalized.get(priceField);
            Map<String, Object> valueJson = new LinkedHashMap<>();
            valueJson.put("field", priceField);
            valueJson.put("displayValue", stringValue(value));
            BigDecimal numeric = parseMoney(value);
            if (numeric != null) valueJson.put("numericValue", numeric);
            valueJson.put("isBlank", !StringUtils.hasText(stringValue(value)));
            insertOrSkipFact(state, entityId, subjectKey, subjectName, "PRICE", priceField, factorJson, valueJson, row);
        }
    }

    private void insertOrSkipFact(CanonicalBuildState state,
                                  UUID entityId,
                                  String subjectKey,
                                  String subjectName,
                                  String factType,
                                  String factKey,
                                  Map<String, Object> factorJson,
                                  Map<String, Object> valueJson,
                                  Map<String, Object> sourceRow) {
        String unique = subjectKey + "|" + factType + "|" + factKey + "|" + stableJson(factorJson);
        String value = stableJson(valueJson);
        String old = state.factValues.get(unique);
        if (old != null) {
            if (!old.equals(value)) {
                state.addIssue("VALUE_CONFLICT", "HIGH", subjectKey, factType, unique,
                        Map.of("oldValue", old, "newValue", value, "factor", factorJson));
            } else {
                state.duplicateFacts++;
            }
            return;
        }
        state.factValues.put(unique, value);
        jdbc.update("""
                INSERT INTO rag_canonical_fact(
                    id, dataset_id, project_id, version_id, entity_id, entity_type, subject_key, subject_name,
                    fact_type, fact_key, factor_json, value_json, status, active, confidence,
                    source_table_id, source_row_id, source_json, created_at, updated_at
                ) VALUES (
                    :id, :datasetId, :projectId, :versionId, :entityId, 'PRODUCT_OR_ITEM', :subjectKey, :subjectName,
                    :factType, :factKey, CAST(:factorJson AS jsonb), CAST(:valueJson AS jsonb), :status, true, :confidence,
                    :sourceTableId, :sourceRowId, CAST(:sourceJson AS jsonb), now(), now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("datasetId", state.datasetId)
                .addValue("projectId", state.projectId)
                .addValue("versionId", state.versionId)
                .addValue("entityId", entityId)
                .addValue("subjectKey", subjectKey)
                .addValue("subjectName", subjectName)
                .addValue("factType", factType)
                .addValue("factKey", factKey)
                .addValue("factorJson", toJson(factorJson))
                .addValue("valueJson", toJson(valueJson))
                .addValue("status", "ACTIVE")
                .addValue("confidence", new BigDecimal("0.9000"))
                .addValue("sourceTableId", sourceRow.get("table_id"))
                .addValue("sourceRowId", sourceRow.get("row_id"))
                .addValue("sourceJson", toJson(sourceRow)));
        state.facts++;
        state.factTypeCounts.merge(factType, 1, Integer::sum);
    }

    private void insertEntity(UUID entityId, CanonicalBuildState state, String subjectKey, String subjectName,
                              Map<String, Object> identityJson, Map<String, Object> attributeJson) {
        jdbc.update("""
                INSERT INTO rag_canonical_entity(
                    id, dataset_id, project_id, version_id, entity_type, entity_key, display_name,
                    identity_json, attribute_json, status, active, created_at, updated_at
                ) VALUES (
                    :id, :datasetId, :projectId, :versionId, 'PRODUCT_OR_ITEM', :entityKey, :displayName,
                    CAST(:identityJson AS jsonb), CAST(:attributeJson AS jsonb), 'ACTIVE', true, now(), now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", entityId)
                .addValue("datasetId", state.datasetId)
                .addValue("projectId", state.projectId)
                .addValue("versionId", state.versionId)
                .addValue("entityKey", subjectKey)
                .addValue("displayName", subjectName)
                .addValue("identityJson", toJson(identityJson))
                .addValue("attributeJson", toJson(attributeJson)));
        state.entities++;
    }

    private void writeIssues(CanonicalBuildState state) {
        if (state.duplicateFacts > 0) {
            state.addIssue("DUPLICATE_FACT_COLLAPSED", "INFO", null, null, "duplicateFacts",
                    Map.of("count", state.duplicateFacts, "message", "동일한 subject/factor/value fact는 정본에서 1개로 병합했습니다."));
        }
        for (Map<String, Object> issue : state.issues) {
            jdbc.update("""
                    INSERT INTO rag_canonical_quality_issue(
                        id, dataset_id, project_id, version_id, issue_type, severity, subject_key, fact_type, issue_key, issue_json, resolved, created_at
                    ) VALUES (
                        :id, :datasetId, :projectId, :versionId, :issueType, :severity, :subjectKey, :factType, :issueKey, CAST(:issueJson AS jsonb), false, now()
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("datasetId", state.datasetId)
                    .addValue("projectId", state.projectId)
                    .addValue("versionId", state.versionId)
                    .addValue("issueType", issue.get("issueType"))
                    .addValue("severity", issue.get("severity"))
                    .addValue("subjectKey", issue.get("subjectKey"))
                    .addValue("factType", issue.get("factType"))
                    .addValue("issueKey", issue.get("issueKey"))
                    .addValue("issueJson", toJson(issue.get("issueJson"))));
        }
    }

    private void writeDefaultPricingRule(CanonicalBuildState state, String instruction) {
        Map<String, Object> factorSchema = new LinkedHashMap<>();
        factorSchema.put("policy", "코드에 가격요소를 한정하지 않는다. canonical fact의 factor_json에 존재하는 모든 키가 가격계산 후보 요소다.");
        factorSchema.put("examples", List.of("사이즈", "색상", "두께", "자재", "마감", "수량", "옵션", "사용자가 새로 정의하는 임의 요소"));
        Map<String, Object> formula = new LinkedHashMap<>();
        formula.put("type", "LOOKUP_FIRST_THEN_GPT_EXPLAIN");
        formula.put("lookupTable", "rag_canonical_fact");
        formula.put("match", "subject_key + factor_json contains user provided factors");
        formula.put("priceValue", "value_json.numericValue 또는 displayValue");
        formula.put("missingPolicy", "가격요소가 부족하면 GPT가 필요한 질문을 생성한다.");
        jdbc.update("""
                INSERT INTO rag_canonical_pricing_rule(
                    id, dataset_id, project_id, version_id, rule_key, title, description,
                    factor_schema_json, formula_json, condition_json, priority, active, created_at, updated_at
                ) VALUES (
                    :id, :datasetId, :projectId, :versionId, :ruleKey, :title, :description,
                    CAST(:factorSchema AS jsonb), CAST(:formulaJson AS jsonb), '{}'::jsonb, 10, true, now(), now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("datasetId", state.datasetId)
                .addValue("projectId", state.projectId)
                .addValue("versionId", state.versionId)
                .addValue("ruleKey", "DYNAMIC_FACTOR_PRICE_LOOKUP")
                .addValue("title", "동적 가격요소 기반 가격조회")
                .addValue("description", instruction)
                .addValue("factorSchema", toJson(factorSchema))
                .addValue("formulaJson", toJson(formula)));
    }

    private void writeDefaultDialogFlow(CanonicalBuildState state, String instruction) {
        Map<String, Object> flow = new LinkedHashMap<>();
        flow.put("policy", "질문 의미 종류를 코드 enum으로 제한하지 않는다. GPT가 현재 canonical facts/pricing rules/dialog flows를 조회해 필요한 질문을 동적으로 결정한다.");
        flow.put("questionGeneration", "가격계산 또는 발주에 필요한 factor_json 키 중 사용자 입력에 없는 요소를 질문한다.");
        flow.put("changeHandling", List.of("단가 변경", "단종", "색상 불가", "옵션 추가", "두께 추가", "새 계산 요소 추가", "질문 순서 변경"));
        jdbc.update("""
                INSERT INTO rag_canonical_dialog_flow(
                    id, dataset_id, project_id, version_id, flow_key, purpose, question_flow_json,
                    validation_json, condition_json, active, created_at, updated_at
                ) VALUES (
                    :id, :datasetId, :projectId, :versionId, :flowKey, :purpose, CAST(:flowJson AS jsonb),
                    '{}'::jsonb, '{}'::jsonb, true, now(), now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("datasetId", state.datasetId)
                .addValue("projectId", state.projectId)
                .addValue("versionId", state.versionId)
                .addValue("flowKey", "DYNAMIC_ORDER_QUESTION_FLOW")
                .addValue("purpose", "동적 발주 질문 흐름")
                .addValue("flowJson", toJson(flow)));
    }

    private void insertChangeEvent(UUID projectId, UUID versionId, UUID datasetId, UUID runId, String instruction, Map<String, Object> summary) {
        jdbc.update("""
                INSERT INTO rag_canonical_change_event(
                    id, project_id, version_id, dataset_id, event_type, instruction, before_json, after_json, impact_json,
                    source_scope, created_by_run_id, created_at
                ) VALUES (
                    :id, :projectId, :versionId, :datasetId, 'CANONICAL_REBUILD', :instruction, '{}'::jsonb, CAST(:afterJson AS jsonb), CAST(:impactJson AS jsonb),
                    'CANONICAL_ENGINE', :runId, now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("datasetId", datasetId)
                .addValue("instruction", instruction)
                .addValue("afterJson", toJson(summary))
                .addValue("impactJson", toJson(Map.of("activeDataset", datasetId)))
                .addValue("runId", runId));
    }

    private Map<String, Object> findActiveDataset(UUID projectId, UUID versionId) {
        try {
            return jdbc.queryForMap("""
                    SELECT *
                    FROM rag_canonical_dataset
                    WHERE project_id = :projectId
                      AND version_id = :versionId
                      AND active = true
                    ORDER BY created_at DESC
                    LIMIT 1
                    """, new MapSqlParameterSource()
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Set<String> detectPriceFields(Map<String, Object> row) {
        Set<String> result = new LinkedHashSet<>();
        for (String key : row.keySet()) {
            String k = normalizeKey(key);
            if (k.contains("가격") || k.contains("금액") || k.contains("단가") || k.contains("price") || k.contains("amount") || k.contains("cost")) {
                result.add(key);
            }
        }
        return result;
    }

    private Set<String> detectIdentityFields(Map<String, Object> row, Set<String> priceFields) {
        Set<String> result = new LinkedHashSet<>();
        for (String key : row.keySet()) {
            if (priceFields.contains(key)) continue;
            String k = normalizeKey(key);
            if (k.contains("제품코드") || k.equals("코드") || k.contains("품번") || k.contains("model") || k.contains("sku")) result.add(key);
        }
        for (String key : row.keySet()) {
            if (priceFields.contains(key)) continue;
            String k = normalizeKey(key);
            if (k.contains("제품명") || k.contains("품명") || k.contains("상품명") || k.contains("name")) result.add(key);
        }
        if (result.isEmpty()) {
            for (String key : row.keySet()) {
                if (!priceFields.contains(key) && StringUtils.hasText(stringValue(row.get(key)))) {
                    result.add(key);
                    if (result.size() >= 2) break;
                }
            }
        }
        return result;
    }

    private String findDisplayName(Map<String, Object> row, Set<String> identityFields) {
        List<String> keys = new ArrayList<>(identityFields);
        keys.sort(Comparator.comparingInt(k -> normalizeKey(k).contains("제품명") || normalizeKey(k).contains("품명") || normalizeKey(k).contains("name") ? 0 : 1));
        for (String key : keys) {
            String value = stringValue(row.get(key));
            if (StringUtils.hasText(value)) return value;
        }
        return "DYNAMIC_ENTITY:" + shortHash(stableJson(row));
    }

    private String buildSubjectKey(Map<String, Object> row, Set<String> identityFields, String subjectName) {
        Map<String, Object> identity = pick(row, identityFields);
        if (!identity.isEmpty()) return "ENTITY:" + shortHash(stableJson(identity)) + ":" + normalizeForKey(subjectName);
        return "ROW:" + shortHash(stableJson(row));
    }

    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = stringValue(entry.getKey()).trim();
            if (!StringUtils.hasText(key)) continue;
            Object value = entry.getValue();
            if (value == null) continue;
            String text = stringValue(value);
            if (!StringUtils.hasText(text) || "-".equals(text.trim())) continue;
            result.put(key, value);
        }
        return result;
    }

    private Map<String, Object> pick(Map<String, Object> source, Set<String> keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : keys) {
            if (source.containsKey(key)) result.put(key, source.get(key));
        }
        return result;
    }

    private Map<String, Object> parseJsonMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
            return result;
        }
        String text = stringValue(value);
        if (!StringUtils.hasText(text)) return new LinkedHashMap<>();
        try {
            Object parsed = objectMapper.readValue(text, new TypeReference<Object>() {});
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
                return result;
            }
        } catch (Exception ignored) {
        }
        return new LinkedHashMap<>();
    }

    private BigDecimal parseMoney(Object value) {
        String text = stringValue(value).replaceAll("[^0-9.\\-]", "");
        if (!StringUtils.hasText(text)) return null;
        try { return new BigDecimal(text); } catch (Exception e) { return null; }
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String normalizeForKey(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "_");
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String stableJson(Object value) {
        return RagJsonUtils.toJson(objectMapper, value);
    }

    private String toJson(Object value) {
        return RagJsonUtils.toJson(objectMapper, value);
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static class CanonicalBuildState {
        final UUID datasetId;
        final UUID projectId;
        final UUID versionId;
        int entities;
        int facts;
        int duplicateFacts;
        final Map<String, UUID> entityIds = new LinkedHashMap<>();
        final Map<String, String> factValues = new LinkedHashMap<>();
        final Map<String, Integer> factTypeCounts = new LinkedHashMap<>();
        final List<Map<String, Object>> issues = new ArrayList<>();

        CanonicalBuildState(UUID datasetId, UUID projectId, UUID versionId) {
            this.datasetId = datasetId;
            this.projectId = projectId;
            this.versionId = versionId;
        }

        void addIssue(String issueType, String severity, String subjectKey, String factType, String issueKey, Object issueJson) {
            Map<String, Object> issue = new LinkedHashMap<>();
            issue.put("issueType", issueType);
            issue.put("severity", severity);
            issue.put("subjectKey", subjectKey);
            issue.put("factType", factType);
            issue.put("issueKey", issueKey);
            issue.put("issueJson", issueJson);
            issues.add(issue);
        }

        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("datasetId", datasetId);
            result.put("entities", entities);
            result.put("facts", facts);
            result.put("duplicateFactsCollapsed", duplicateFacts);
            result.put("factTypeCounts", factTypeCounts);
            result.put("qualityIssues", issues.size());
            result.put("policy", "가격요소/질문유형을 코드 enum으로 제한하지 않고 factor_json, formula_json, question_flow_json에 동적으로 저장합니다.");
            return result;
        }
    }
}
