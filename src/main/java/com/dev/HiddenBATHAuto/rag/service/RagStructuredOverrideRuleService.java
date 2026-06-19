package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagStructuredOverrideRuleService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagStructuredOverrideRuleService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> saveRuleFromPlan(UUID projectId, UUID versionId, Map<String, Object> plan, String sourceMessage) {
        Map<String, Object> updateRule = childMap(plan, "updateRule");
        String entityType = defaultText(updateRule.get("entityType"), "PRODUCT");
        String entityKey = defaultText(updateRule.get("entityKey"), childText(plan, "primaryEntity", "name"));
        String fieldName = defaultText(updateRule.get("fieldName"), "조건");
        String ruleType = defaultText(updateRule.get("ruleType"), "DISALLOW").toUpperCase();
        String ruleValue = text(updateRule.get("ruleValue"));
        String reason = defaultText(updateRule.get("reason"), sourceMessage);
        BigDecimal confidence = decimal(plan.get("confidence"), new BigDecimal("0.8500"));

        if (!StringUtils.hasText(entityKey) || !StringUtils.hasText(ruleValue)) {
            Map<String, Object> skipped = new LinkedHashMap<>();
            skipped.put("saved", false);
            skipped.put("reason", "override rule 저장에 필요한 entityKey 또는 ruleValue가 비어 있습니다.");
            skipped.put("plan", plan);
            return skipped;
        }

        String sql = """
                INSERT INTO rag_structured_override_rule(
                    id, project_id, version_id,
                    entity_type, entity_key,
                    field_name, rule_type, rule_value,
                    reason, source_message, plan_json, confidence, active,
                    created_at, updated_at
                ) VALUES (
                    :id, :projectId, :versionId,
                    :entityType, :entityKey,
                    :fieldName, :ruleType, :ruleValue,
                    :reason, :sourceMessage, CAST(:planJson AS jsonb), :confidence, true,
                    now(), now()
                )
                ON CONFLICT(project_id, version_id, entity_type, entity_key, field_name, rule_type, rule_value)
                DO UPDATE SET
                    reason = EXCLUDED.reason,
                    source_message = EXCLUDED.source_message,
                    plan_json = EXCLUDED.plan_json,
                    confidence = EXCLUDED.confidence,
                    active = true,
                    updated_at = now()
                RETURNING *
                """;

        try {
            Map<String, Object> saved = jdbc.queryForMap(sql, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("entityType", entityType)
                    .addValue("entityKey", entityKey)
                    .addValue("fieldName", fieldName)
                    .addValue("ruleType", ruleType)
                    .addValue("ruleValue", ruleValue)
                    .addValue("reason", reason)
                    .addValue("sourceMessage", sourceMessage)
                    .addValue("planJson", toJson(plan))
                    .addValue("confidence", confidence));
            saved.put("saved", true);
            return saved;
        } catch (DataAccessException e) {
            throw new IllegalStateException("rag_structured_override_rule 저장 실패. DB 패치가 적용됐는지 확인해 주세요: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> findActiveRules(UUID projectId, UUID versionId, String entityKey) {
        String sql;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId);
        if (StringUtils.hasText(entityKey)) {
            sql = """
                    SELECT *
                    FROM rag_structured_override_rule
                    WHERE project_id = :projectId
                      AND version_id = :versionId
                      AND active = true
                      AND (entity_key = :entityKey OR :entityKey ILIKE '%' || entity_key || '%')
                    ORDER BY updated_at DESC, created_at DESC
                    """;
            params.addValue("entityKey", entityKey);
        } else {
            sql = """
                    SELECT *
                    FROM rag_structured_override_rule
                    WHERE project_id = :projectId
                      AND version_id = :versionId
                      AND active = true
                    ORDER BY updated_at DESC, created_at DESC
                    LIMIT 200
                    """;
        }
        try {
            return jdbc.queryForList(sql, params);
        } catch (DataAccessException e) {
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> applyRulesToRows(List<Map<String, Object>> rows, List<Map<String, Object>> rules) {
        if (rows == null || rows.isEmpty() || rules == null || rules.isEmpty()) return rows == null ? List.of() : rows;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (!isExcluded(row, rules)) result.add(row);
        }
        return result;
    }

    public List<Map<String, Object>> findExcludedRows(List<Map<String, Object>> rows, List<Map<String, Object>> rules) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows == null || rules == null) return result;
        for (Map<String, Object> row : rows) {
            if (isExcluded(row, rules)) result.add(row);
        }
        return result;
    }

    private boolean isExcluded(Map<String, Object> row, List<Map<String, Object>> rules) {
        for (Map<String, Object> rule : rules) {
            String type = text(rule.get("rule_type"));
            if (!"DISALLOW".equalsIgnoreCase(type)) continue;
            String field = text(rule.get("field_name"));
            String value = text(rule.get("rule_value"));
            if (!StringUtils.hasText(field) || !StringUtils.hasText(value)) continue;
            String rowValue = valueForField(row, field);
            if (StringUtils.hasText(rowValue) && normalize(rowValue).equals(normalize(value))) return true;
        }
        return false;
    }

    private String valueForField(Map<String, Object> row, String field) {
        String normalizedField = normalize(field);
        for (String key : row.keySet()) {
            if (normalize(key).equals(normalizedField)) return text(row.get(key));
        }
        if ("색상".equals(field) || normalizedField.contains("색") || normalizedField.contains("color")) {
            return firstText(row, "색상", "색", "컬러", "color", "Color", "COLOR");
        }
        if ("사이즈".equals(field) || normalizedField.contains("규격") || normalizedField.contains("size")) {
            return firstText(row, "사이즈", "규격", "크기", "size", "Size", "SIZE");
        }
        return firstText(row, field);
    }

    private String firstText(Map<String, Object> row, String... keys) {
        if (row == null) return "";
        for (String key : keys) {
            Object v = row.get(key);
            if (v != null && StringUtils.hasText(String.valueOf(v))) return String.valueOf(v).trim();
        }
        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim().toUpperCase();
    }

    private String toJson(Object value) {
        return RagJsonUtils.toJson(objectMapper, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> childMap(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String childText(Map<String, Object> map, String child, String key) {
        return text(childMap(map, child).get(key));
    }

    private String defaultText(Object value, String fallback) {
        String s = text(value);
        return StringUtils.hasText(s) ? s : fallback;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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
