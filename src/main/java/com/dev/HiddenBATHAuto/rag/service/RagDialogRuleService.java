package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
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
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 대화식 학습의 중심 구조 저장소입니다.
 *
 * 비규격 주문은 단순 문서/벡터 노드만으로는 부족합니다.
 * 사용자가 대화로 알려준 질문 순서, 조건부 질문, 입력 검증, 옵션 가능 규칙, 가격식을
 * rag_dialog_rule에 저장하고 챗봇/가격계산/주문 검증 시 같은 규칙을 다시 사용합니다.
 */
@Service
public class RagDialogRuleService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagDialogRuleService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> findActiveRules(UUID projectId, UUID versionId, String entityKey) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("entityKey", StringUtils.hasText(entityKey) ? entityKey.trim() : "");
        String sql = """
                SELECT id, topic, rule_key, rule_type, entity_type, entity_key, step_key, field_name,
                       priority, condition_json, action_json, validation_json, pricing_json,
                       confidence, source_message, plan_json, active, created_at, updated_at
                FROM rag_dialog_rule
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                  AND (:entityKey = '' OR entity_key = :entityKey OR entity_key = '')
                ORDER BY priority ASC, updated_at DESC
                LIMIT 300
                """;
        try {
            return jdbc.queryForList(sql, params);
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> saveRulesFromPlan(UUID projectId,
                                                       UUID versionId,
                                                       String topic,
                                                       Map<String, Object> plan,
                                                       String sourceMessage) {
        List<Map<String, Object>> dialogRules = mapList(plan.get("dialogRules"));
        if (dialogRules.isEmpty() && shouldCreateFallbackRule(plan, sourceMessage)) {
            dialogRules = List.of(fallbackRuleFromPlan(plan, sourceMessage));
        }
        List<Map<String, Object>> saved = new ArrayList<>();
        for (Map<String, Object> rule : dialogRules) {
            Map<String, Object> normalized = normalizeRule(rule, plan, topic, sourceMessage);
            if (!StringUtils.hasText(str(normalized.get("ruleType")))) continue;
            if (!StringUtils.hasText(str(normalized.get("ruleKey")))) {
                normalized.put("ruleKey", buildRuleKey(projectId, versionId, normalized, sourceMessage));
            }
            saved.add(upsertRule(projectId, versionId, normalized, plan, sourceMessage));
        }
        return saved;
    }

    private Map<String, Object> upsertRule(UUID projectId,
                                           UUID versionId,
                                           Map<String, Object> rule,
                                           Map<String, Object> plan,
                                           String sourceMessage) {
        String sql = """
                INSERT INTO rag_dialog_rule(
                    id, project_id, version_id, topic, rule_key, rule_type,
                    entity_type, entity_key, step_key, field_name, priority,
                    condition_json, action_json, validation_json, pricing_json,
                    source_message, plan_json, confidence, active, created_at, updated_at
                ) VALUES (
                    :id, :projectId, :versionId, :topic, :ruleKey, :ruleType,
                    :entityType, :entityKey, :stepKey, :fieldName, :priority,
                    CAST(:conditionJson AS jsonb), CAST(:actionJson AS jsonb), CAST(:validationJson AS jsonb), CAST(:pricingJson AS jsonb),
                    :sourceMessage, CAST(:planJson AS jsonb), :confidence, true, now(), now()
                )
                ON CONFLICT(project_id, version_id, rule_key)
                DO UPDATE SET
                    topic = EXCLUDED.topic,
                    rule_type = EXCLUDED.rule_type,
                    entity_type = EXCLUDED.entity_type,
                    entity_key = EXCLUDED.entity_key,
                    step_key = EXCLUDED.step_key,
                    field_name = EXCLUDED.field_name,
                    priority = EXCLUDED.priority,
                    condition_json = EXCLUDED.condition_json,
                    action_json = EXCLUDED.action_json,
                    validation_json = EXCLUDED.validation_json,
                    pricing_json = EXCLUDED.pricing_json,
                    source_message = EXCLUDED.source_message,
                    plan_json = EXCLUDED.plan_json,
                    confidence = EXCLUDED.confidence,
                    active = true,
                    updated_at = now()
                RETURNING id, topic, rule_key, rule_type, entity_type, entity_key, step_key, field_name,
                          priority, condition_json, action_json, validation_json, pricing_json,
                          confidence, source_message, active, created_at, updated_at
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", str(rule.get("topic")))
                .addValue("ruleKey", str(rule.get("ruleKey")))
                .addValue("ruleType", str(rule.get("ruleType")))
                .addValue("entityType", firstNonBlank(str(rule.get("entityType")), "PRODUCT"))
                .addValue("entityKey", str(rule.get("entityKey")))
                .addValue("stepKey", str(rule.get("stepKey")))
                .addValue("fieldName", str(rule.get("fieldName")))
                .addValue("priority", integer(rule.get("priority"), 100))
                .addValue("conditionJson", toJson(map(rule.get("condition"))))
                .addValue("actionJson", toJson(map(rule.get("action"))))
                .addValue("validationJson", toJson(map(rule.get("validation"))))
                .addValue("pricingJson", toJson(map(rule.get("pricing"))))
                .addValue("sourceMessage", sourceMessage)
                .addValue("planJson", toJson(plan))
                .addValue("confidence", decimal(plan.get("confidence"), new BigDecimal("0.7000"))));
    }

    private Map<String, Object> normalizeRule(Map<String, Object> rule,
                                              Map<String, Object> plan,
                                              String topic,
                                              String sourceMessage) {
        Map<String, Object> normalized = new LinkedHashMap<>(rule == null ? Map.of() : rule);
        Map<String, Object> primary = map(plan.get("primaryEntity"));
        normalized.putIfAbsent("topic", topic);
        normalized.putIfAbsent("entityType", firstNonBlank(str(primary.get("entityType")), "PRODUCT"));
        normalized.putIfAbsent("entityKey", firstNonBlank(str(primary.get("name")), str(primary.get("normalizedName"))));
        normalized.putIfAbsent("stepKey", firstNonBlank(str(normalized.get("stepKey")), str(map(normalized.get("action")).get("nextStepKey"))));
        normalized.putIfAbsent("fieldName", firstNonBlank(str(normalized.get("fieldName")), str(map(normalized.get("condition")).get("whenField"))));
        normalized.putIfAbsent("priority", 100);
        normalized.putIfAbsent("condition", Map.of("expressionText", sourceMessage));
        normalized.putIfAbsent("action", Map.of("actionType", "STORE_RULE", "message", sourceMessage));
        normalized.putIfAbsent("validation", Map.of());
        normalized.putIfAbsent("pricing", Map.of());
        return normalized;
    }

    private boolean shouldCreateFallbackRule(Map<String, Object> plan, String sourceMessage) {
        String intent = str(plan.get("intentType"));
        String storeAs = str(map(plan.get("storagePlan")).get("storeAs"));
        return "LEARN_DIALOG_RULES".equals(intent)
                || "ORDER_CONVERSATION".equals(intent)
                || "DIALOG_RULE".equals(storeAs)
                || "QUESTION_FLOW".equals(storeAs)
                || "PRICING_FORMULA".equals(storeAs)
                || looksLikeDialogRule(sourceMessage);
    }

    private Map<String, Object> fallbackRuleFromPlan(Map<String, Object> plan, String sourceMessage) {
        Map<String, Object> primary = map(plan.get("primaryEntity"));
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("ruleType", looksLikePricing(sourceMessage) ? "PRICING_FORMULA" : "QUESTION_FLOW");
        rule.put("entityType", firstNonBlank(str(primary.get("entityType")), "PRODUCT"));
        rule.put("entityKey", firstNonBlank(str(primary.get("name")), str(primary.get("normalizedName"))));
        rule.put("stepKey", "AUTO_" + Math.abs(sourceMessage == null ? 0 : sourceMessage.hashCode()));
        rule.put("fieldName", "조건");
        rule.put("priority", 100);
        rule.put("condition", Map.of("expressionText", firstNonBlank(sourceMessage, "")));
        rule.put("action", Map.of("actionType", "ASK_OR_APPLY", "message", firstNonBlank(sourceMessage, "")));
        rule.put("validation", Map.of());
        rule.put("pricing", looksLikePricing(sourceMessage) ? Map.of("formulaText", firstNonBlank(sourceMessage, "")) : Map.of());
        return rule;
    }

    private String buildRuleKey(UUID projectId, UUID versionId, Map<String, Object> rule, String sourceMessage) {
        String basis = projectId + "|" + versionId + "|" + str(rule.get("ruleType")) + "|" + str(rule.get("entityKey"))
                + "|" + str(rule.get("stepKey")) + "|" + str(rule.get("fieldName")) + "|" + toJson(map(rule.get("condition")))
                + "|" + toJson(map(rule.get("action"))) + "|" + sourceMessage;
        return str(rule.get("ruleType")) + ":" + digest(basis);
    }

    private boolean looksLikeDialogRule(String text) {
        String compact = text == null ? "" : text.replaceAll("\\s+", "");
        return compact.contains("질문") || compact.contains("답변") || compact.contains("단계") || compact.contains("조건")
                || compact.contains("경우") || compact.contains("이면") || compact.contains("라면") || compact.contains("검증")
                || compact.contains("선택") || compact.contains("흐름") || compact.contains("프로세스");
    }

    private boolean looksLikePricing(String text) {
        String compact = text == null ? "" : text.replaceAll("\\s+", "");
        return compact.contains("가격") || compact.contains("금액") || compact.contains("단가") || compact.contains("추가금") || compact.contains("계산");
    }

    private String digest(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 32);
        } catch (Exception e) {
            return String.valueOf(Math.abs(text.hashCode()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) r.put(String.valueOf(e.getKey()), e.getValue());
            }
            return r;
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) result.add(map(m));
            }
        }
        return result;
    }

    private String toJson(Object value) {
        return RagJsonUtils.toJson(objectMapper, value);
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return "";
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

    private int integer(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }
}
