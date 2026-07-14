package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.StringUtils;

/** 한 사용자 요청의 Agent 도구 사용 및 최종답변 검증 상태입니다. */
public final class RagAgentRunState {

    private Map<String, Object> requestPlan = Map.of();
    private final Set<String> successfulTools = new LinkedHashSet<>();
    private final List<Map<String, Object>> observations = new ArrayList<>();
    private Map<String, Object> latestInventory = Map.of();
    private Map<String, Object> latestSemanticResult = Map.of();
    private Map<String, Object> latestEntityResolution = Map.of();
    private Map<String, Object> latestOrderValidation = Map.of();
    private Map<String, Object> latestPricingResult = Map.of();
    private Map<String, Object> latestImpactResult = Map.of();
    private Map<String, Object> latestChangeResult = Map.of();
    private int toolFailureCount;
    private int noProgressCount;
    private int finalRejectionCount;
    private int compactionCount;
    private boolean recovered;

    public void submitPlan(Map<String, Object> plan) {
        requestPlan = immutableCopy(plan);
        successfulTools.add("submit_request_plan");
        noProgressCount = 0;
    }

    public boolean hasPlan() { return !requestPlan.isEmpty(); }
    public Map<String, Object> requestPlan() { return requestPlan; }

    public void recordSuccess(String toolName, Map<String, Object> result) {
        if (StringUtils.hasText(toolName)) successfulTools.add(toolName);
        noProgressCount = 0;
        switch (toolName == null ? "" : toolName) {
            case "get_knowledge_inventory" -> latestInventory = immutableCopy(result);
            case "search_semantic_memory", "search_knowledge_sources", "find_canonical_price_candidates" -> latestSemanticResult = immutableCopy(result);
            case "resolve_entity_reference", "compare_entity_candidates", "get_entity_context_bundle" -> latestEntityResolution = immutableCopy(result);
            case "validate_order_state", "get_order_flow", "get_effective_rules" -> latestOrderValidation = immutableCopy(result);
            case "calculate_order_price", "simulate_price_scenarios" -> latestPricingResult = immutableCopy(result);
            case "preview_change_impact" -> latestImpactResult = immutableCopy(result);
            case "create_change_set" -> latestChangeResult = immutableCopy(result);
            default -> { }
        }
        observations.add(observation(toolName, "SUCCESS", compact(result)));
        trimObservations();
    }

    public void recordFailure(String toolName, String errorMessage) {
        toolFailureCount++;
        noProgressCount++;
        observations.add(observation(toolName, "FAILED", Map.of("error", truncate(errorMessage, 1200))));
        trimObservations();
    }

    public void recordNoProgress() { noProgressCount++; }
    public void recordCompaction() { compactionCount++; }

    public void recordFinalRejection(List<String> reasons) {
        finalRejectionCount++;
        noProgressCount++;
        observations.add(observation("submit_final_answer", "REJECTED",
                Map.of("missingRequirements", reasons == null ? List.of() : List.copyOf(reasons))));
        trimObservations();
    }

    public List<String> validateFinalPayload(Map<String, Object> finalPayload) {
        List<String> missing = new ArrayList<>();
        if (!hasPlan()) {
            missing.add("submit_request_plan이 먼저 완료되어야 합니다.");
            return missing;
        }
        String answer = text(finalPayload.get("answer"));
        if (!StringUtils.hasText(answer)) missing.add("최종 answer는 GPT가 작성한 비어 있지 않은 문장이어야 합니다.");

        String finalStatus = text(finalPayload.get("status"));
        boolean clarification = bool(finalPayload.get("requiresClarification"), false)
                || "NEED_CLARIFICATION".equals(finalStatus);
        boolean blocked = "BLOCKED".equals(finalStatus);
        boolean requiresDatabase = bool(requestPlan.get("requiresDatabase"), false);
        boolean requiresSemantic = bool(requestPlan.get("requiresSemanticSearch"), false);
        boolean requiresEntity = bool(requestPlan.get("requiresEntityResolution"), false);
        boolean requiresOrder = bool(requestPlan.get("requiresOrderValidation"), false);
        boolean requiresMutation = bool(requestPlan.get("requiresMutation"), false);
        boolean requiresImpact = bool(requestPlan.get("requiresImpactPreview"), false);
        boolean requiresPricing = bool(requestPlan.get("requiresDeterministicPricing"), false);
        boolean requiresMemory = bool(requestPlan.get("requiresConversationMemory"), false);

        if (requiresDatabase && !clarification && !blocked && !hasAnySuccessfulTool(
                "get_database_overview", "get_knowledge_inventory", "search_database_catalog",
                "search_semantic_memory", "search_knowledge_sources", "get_document_context",
                "resolve_entity_reference", "get_entity_context_bundle", "get_effective_rules",
                "get_order_flow", "validate_order_state", "describe_table", "list_table_relationships",
                "get_table_statistics", "query_database", "find_canonical_price_candidates",
                "calculate_order_price", "simulate_price_scenarios")) {
            missing.add("DB 또는 RAG 근거 도구가 한 번 이상 성공해야 합니다.");
        }
        if (requiresSemantic && !clarification && !blocked
                && !hasAnySuccessfulTool("search_semantic_memory", "search_knowledge_sources", "resolve_entity_reference")) {
            missing.add("유사 표현 판단에는 semantic/source/entity resolution 도구가 필요합니다.");
        }
        if (requiresEntity && !clarification && !blocked
                && !hasAnySuccessfulTool("resolve_entity_reference", "get_entity_context_bundle", "compare_entity_candidates")) {
            missing.add("대상 확정을 위해 entity resolution/context 도구가 필요합니다.");
        }
        if (requiresOrder && !clarification && !blocked && !successfulTools.contains("validate_order_state")) {
            missing.add("주문 상담의 확정 답변 전 validate_order_state가 필요합니다.");
        }
        if (requiresPricing && !clarification && !blocked) {
            if (!hasAnySuccessfulTool("calculate_order_price", "simulate_price_scenarios")) {
                missing.add("확정 가격 답변에는 결정론적 가격 도구가 필요합니다.");
            } else if (!isPricingComplete(latestPricingResult)) {
                missing.add("가격 계산이 완료되지 않았으므로 missingInputs를 질문해야 합니다.");
            }
        }
        if (requiresMemory && !clarification && !blocked
                && !hasAnySuccessfulTool("get_conversation_memory", "get_conversation_history", "update_conversation_memory")) {
            missing.add("세션 문맥이 필요한 요청에는 conversation memory/history 도구가 필요합니다.");
        }
        if (requiresImpact && !clarification && !blocked && !successfulTools.contains("preview_change_impact")) {
            missing.add("변경 전 preview_change_impact가 필요합니다.");
        }
        if (requiresMutation && !clarification && !blocked && !successfulTools.contains("create_change_set")) {
            missing.add("저장·수정·삭제 요청에는 create_change_set 또는 명시적 확인 질문이 필요합니다.");
        }

        String payloadChangeSetId = text(finalPayload.get("changeSetId"));
        String actualChangeSetId = text(latestChangeResult.get("changeSetId"));
        if (StringUtils.hasText(payloadChangeSetId) && StringUtils.hasText(actualChangeSetId)
                && !payloadChangeSetId.equals(actualChangeSetId)) {
            missing.add("최종답변 changeSetId가 실제 ChangeSet과 일치하지 않습니다.");
        }
        if (StringUtils.hasText(actualChangeSetId) && !StringUtils.hasText(payloadChangeSetId)
                && requiresMutation && !clarification) {
            missing.add("생성된 ChangeSet ID를 최종답변에 포함해야 합니다.");
        }
        return missing;
    }

    private boolean isPricingComplete(Map<String, Object> result) {
        if (bool(result.get("calculated"), false)) return true;
        if (bool(result.get("allCalculated"), false)) return true;
        Object nested = result.get("result");
        return nested instanceof Map<?, ?> m && bool(m.get("calculated"), false);
    }

    public boolean hasAnySuccessfulTool(String... names) {
        if (names == null) return false;
        for (String name : names) if (successfulTools.contains(name)) return true;
        return false;
    }

    public Set<String> successfulTools() { return Collections.unmodifiableSet(successfulTools); }
    public List<Map<String, Object>> observations() { return List.copyOf(observations); }
    public Map<String, Object> latestInventory() { return latestInventory; }
    public Map<String, Object> latestSemanticResult() { return latestSemanticResult; }
    public Map<String, Object> latestEntityResolution() { return latestEntityResolution; }
    public Map<String, Object> latestOrderValidation() { return latestOrderValidation; }
    public Map<String, Object> latestPricingResult() { return latestPricingResult; }
    public Map<String, Object> latestImpactResult() { return latestImpactResult; }
    public Map<String, Object> latestChangeResult() { return latestChangeResult; }
    public int toolFailureCount() { return toolFailureCount; }
    public int noProgressCount() { return noProgressCount; }
    public int finalRejectionCount() { return finalRejectionCount; }
    public int compactionCount() { return compactionCount; }
    public boolean recovered() { return recovered; }
    public void markRecovered() { recovered = true; }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestPlan", requestPlan);
        result.put("successfulTools", List.copyOf(successfulTools));
        result.put("observations", observations());
        result.put("latestInventory", latestInventory);
        result.put("latestSemanticResult", latestSemanticResult);
        result.put("latestEntityResolution", latestEntityResolution);
        result.put("latestOrderValidation", latestOrderValidation);
        result.put("latestPricingResult", latestPricingResult);
        result.put("latestImpactResult", latestImpactResult);
        result.put("latestChangeResult", latestChangeResult);
        result.put("toolFailureCount", toolFailureCount);
        result.put("noProgressCount", noProgressCount);
        result.put("finalRejectionCount", finalRejectionCount);
        result.put("compactionCount", compactionCount);
        result.put("recovered", recovered);
        return result;
    }

    private Map<String, Object> observation(String tool, String status, Map<String, Object> detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", tool);
        result.put("status", status);
        result.putAll(detail);
        return result;
    }

    private Map<String, Object> compact(Map<String, Object> result) {
        if (result == null || result.isEmpty()) return Map.of();
        Map<String, Object> compact = new LinkedHashMap<>();
        for (String key : List.of("success","accepted","rowCount","totalRows","resultCount","candidateCount",
                "calculated","allCalculated","valid","missingFields","conflicts","total","currency",
                "changeSetId","applied","status","requiresConfirmation","totalReferenceCount")) {
            if (result.containsKey(key)) compact.put(key, result.get(key));
        }
        if (compact.isEmpty()) {
            int count = 0;
            for (Map.Entry<String, Object> entry : result.entrySet()) {
                compact.put(entry.getKey(), entry.getValue());
                if (++count >= 8) break;
            }
        }
        return compact;
    }

    private void trimObservations() { while (observations.size() > 60) observations.remove(0); }
    private Map<String, Object> immutableCopy(Map<String, Object> input) {
        return input == null || input.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }
    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String truncate(String value, int max) { return value == null ? "" : (value.length() <= max ? value : value.substring(0, max) + "...[TRUNCATED]"); }
}
