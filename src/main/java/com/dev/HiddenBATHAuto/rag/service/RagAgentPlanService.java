package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagAgentPlanService {

    private static final List<String> INTENT_TYPES = List.of(
            "GENERAL_CONVERSATION", "KNOWLEDGE_QUERY", "KNOWLEDGE_INVENTORY",
            "ORDER_CONSULTATION", "PRICE_CALCULATION", "CREATE", "UPDATE", "DELETE",
            "FILE_LEARNING", "SYSTEM_EVENT", "MIXED");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagAgentPlanService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                               ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> submit(RagAgentToolContext context, Map<String, Object> rawPlan) {
        Map<String, Object> plan = normalize(rawPlan);
        validateConsistency(plan);
        UUID planId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO rag_agent_request_plan(
                    id, run_id, project_id, version_id, session_id, source_scope,
                    intent_type, user_goal, requires_database, requires_semantic_search,
                    requires_entity_resolution, requires_order_validation,
                    requires_mutation, requires_impact_preview, requires_deterministic_pricing,
                    requires_conversation_memory, ambiguity_detected,
                    clarification_question, target_domains, entity_hints_json,
                    planned_steps_json, risk_level, plan_json, created_at, updated_at
                ) VALUES (
                    :id, :runId, :projectId, :versionId, :sessionId, :sourceScope,
                    :intentType, :userGoal, :requiresDatabase, :requiresSemanticSearch,
                    :requiresEntityResolution, :requiresOrderValidation,
                    :requiresMutation, :requiresImpactPreview, :requiresDeterministicPricing,
                    :requiresConversationMemory, :ambiguityDetected,
                    :clarificationQuestion, CAST(:targetDomains AS jsonb), CAST(:entityHints AS jsonb),
                    CAST(:plannedSteps AS jsonb), :riskLevel, CAST(:planJson AS jsonb), now(), now()
                )
                ON CONFLICT (run_id)
                DO UPDATE SET
                    intent_type = EXCLUDED.intent_type,
                    user_goal = EXCLUDED.user_goal,
                    requires_database = EXCLUDED.requires_database,
                    requires_semantic_search = EXCLUDED.requires_semantic_search,
                    requires_entity_resolution = EXCLUDED.requires_entity_resolution,
                    requires_order_validation = EXCLUDED.requires_order_validation,
                    requires_mutation = EXCLUDED.requires_mutation,
                    requires_impact_preview = EXCLUDED.requires_impact_preview,
                    requires_deterministic_pricing = EXCLUDED.requires_deterministic_pricing,
                    requires_conversation_memory = EXCLUDED.requires_conversation_memory,
                    ambiguity_detected = EXCLUDED.ambiguity_detected,
                    clarification_question = EXCLUDED.clarification_question,
                    target_domains = EXCLUDED.target_domains,
                    entity_hints_json = EXCLUDED.entity_hints_json,
                    planned_steps_json = EXCLUDED.planned_steps_json,
                    risk_level = EXCLUDED.risk_level,
                    plan_json = EXCLUDED.plan_json,
                    updated_at = now()
                """, new MapSqlParameterSource()
                .addValue("id", planId)
                .addValue("runId", context.runId())
                .addValue("projectId", context.projectId())
                .addValue("versionId", context.versionId())
                .addValue("sessionId", context.sessionId())
                .addValue("sourceScope", context.sourceScope())
                .addValue("intentType", plan.get("intentType"))
                .addValue("userGoal", plan.get("userGoal"))
                .addValue("requiresDatabase", plan.get("requiresDatabase"))
                .addValue("requiresSemanticSearch", plan.get("requiresSemanticSearch"))
                .addValue("requiresEntityResolution", plan.get("requiresEntityResolution"))
                .addValue("requiresOrderValidation", plan.get("requiresOrderValidation"))
                .addValue("requiresMutation", plan.get("requiresMutation"))
                .addValue("requiresImpactPreview", plan.get("requiresImpactPreview"))
                .addValue("requiresDeterministicPricing", plan.get("requiresDeterministicPricing"))
                .addValue("requiresConversationMemory", plan.get("requiresConversationMemory"))
                .addValue("ambiguityDetected", plan.get("ambiguityDetected"))
                .addValue("clarificationQuestion", plan.get("clarificationQuestion"))
                .addValue("targetDomains", RagJsonUtils.toJson(objectMapper, plan.get("targetDomains")))
                .addValue("entityHints", RagJsonUtils.toJson(objectMapper, plan.get("entityHints")))
                .addValue("plannedSteps", RagJsonUtils.toJson(objectMapper, plan.get("plannedSteps")))
                .addValue("riskLevel", plan.get("riskLevel"))
                .addValue("planJson", RagJsonUtils.toJson(objectMapper, plan)));

        jdbc.update("""
                UPDATE rag_agent_run
                   SET phase = 'PLANNED',
                       plan_json = CAST(:planJson AS jsonb),
                       updated_at = now()
                 WHERE id = :runId
                """, new MapSqlParameterSource()
                .addValue("runId", context.runId())
                .addValue("planJson", RagJsonUtils.toJson(objectMapper, plan)));

        context.runState().submitPlan(plan);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("accepted", true);
        result.put("runId", context.runId());
        result.put("plan", plan);
        result.put("nextInstruction", bool(plan.get("ambiguityDetected"), false)
                && StringUtils.hasText(text(plan.get("clarificationQuestion")))
                ? "필요한 최소 조회만 수행한 뒤 확인 질문으로 종료할 수 있습니다."
                : "계획에 필요한 도구를 실행하십시오.");
        return result;
    }

    private Map<String, Object> normalize(Map<String, Object> raw) {
        Map<String, Object> plan = new LinkedHashMap<>();
        String intent = text(raw.get("intentType")).toUpperCase(Locale.ROOT);
        if (!INTENT_TYPES.contains(intent)) intent = "MIXED";
        plan.put("intentType", intent);
        plan.put("userGoal", requiredText(raw.get("userGoal"), "userGoal"));
        plan.put("requiresDatabase", bool(raw.get("requiresDatabase"), !"GENERAL_CONVERSATION".equals(intent)));
        plan.put("requiresSemanticSearch", bool(raw.get("requiresSemanticSearch"), false));
        plan.put("requiresEntityResolution", bool(raw.get("requiresEntityResolution"), false));
        plan.put("requiresOrderValidation", bool(raw.get("requiresOrderValidation"), "ORDER_CONSULTATION".equals(intent)));
        plan.put("requiresMutation", bool(raw.get("requiresMutation"), List.of("CREATE", "UPDATE", "DELETE", "FILE_LEARNING").contains(intent)));
        plan.put("requiresImpactPreview", bool(raw.get("requiresImpactPreview"), List.of("UPDATE", "DELETE").contains(intent)));
        plan.put("requiresDeterministicPricing", bool(raw.get("requiresDeterministicPricing"), "PRICE_CALCULATION".equals(intent)));
        plan.put("requiresConversationMemory", bool(raw.get("requiresConversationMemory"), List.of("ORDER_CONSULTATION", "PRICE_CALCULATION").contains(intent)));
        plan.put("ambiguityDetected", bool(raw.get("ambiguityDetected"), false));
        String clarification = text(raw.get("clarificationQuestion"));
        plan.put("clarificationQuestion", StringUtils.hasText(clarification) ? clarification : null);
        plan.put("targetDomains", stringList(raw.get("targetDomains"), 20));
        plan.put("entityHints", mapList(raw.get("entityHints"), 30));
        plan.put("plannedSteps", stringList(raw.get("plannedSteps"), 40));
        String risk = text(raw.get("riskLevel")).toUpperCase(Locale.ROOT);
        plan.put("riskLevel", List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(risk) ? risk : "LOW");
        return plan;
    }

    private void validateConsistency(Map<String, Object> plan) {
        String intent = text(plan.get("intentType"));
        boolean requiresDatabase = bool(plan.get("requiresDatabase"), false);
        boolean requiresEntityResolution = bool(plan.get("requiresEntityResolution"), false);
        boolean requiresOrderValidation = bool(plan.get("requiresOrderValidation"), false);
        boolean requiresMutation = bool(plan.get("requiresMutation"), false);
        boolean requiresImpactPreview = bool(plan.get("requiresImpactPreview"), false);
        boolean requiresPricing = bool(plan.get("requiresDeterministicPricing"), false);
        boolean requiresConversationMemory = bool(plan.get("requiresConversationMemory"), false);
        if ("GENERAL_CONVERSATION".equals(intent) && (requiresMutation || requiresPricing)) {
            throw new IllegalArgumentException("GENERAL_CONVERSATION 계획에는 변경 또는 가격 계산을 선언할 수 없습니다.");
        }
        if ((requiresEntityResolution || requiresOrderValidation || requiresMutation || requiresImpactPreview
                || requiresPricing || requiresConversationMemory) && !requiresDatabase) {
            throw new IllegalArgumentException("엔티티 해석·주문 검증·변경·영향분석·가격·메모리 계획은 requiresDatabase=true여야 합니다.");
        }
        if (requiresImpactPreview && !requiresMutation) {
            throw new IllegalArgumentException("requiresImpactPreview=true이면 requiresMutation=true여야 합니다.");
        }
        if (bool(plan.get("ambiguityDetected"), false)
                && !StringUtils.hasText(text(plan.get("clarificationQuestion")))) {
            throw new IllegalArgumentException("ambiguityDetected=true이면 clarificationQuestion이 필요합니다.");
        }
    }

    private List<String> stringList(Object value, int max) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = text(item);
            if (StringUtils.hasText(text)) result.add(text);
            if (result.size() >= max) break;
        }
        return List.copyOf(result);
    }

    private List<Map<String, Object>> mapList(Object value, int max) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                result.add(copy);
            }
            if (result.size() >= max) break;
        }
        return List.copyOf(result);
    }

    private String requiredText(Object value, String name) {
        String text = text(value);
        if (!StringUtils.hasText(text)) throw new IllegalArgumentException(name + "이 비어 있습니다.");
        return text;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
