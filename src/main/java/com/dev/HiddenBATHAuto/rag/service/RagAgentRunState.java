package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.util.StringUtils;

/**
 * 한 사용자 요청의 Agent 도구 사용 상태입니다.
 * 요청 처리 스레드 안에서만 사용하며 Spring singleton 상태로 보관하지 않습니다.
 */
public final class RagAgentRunState {

    private Map<String, Object> requestPlan = Map.of();
    private final Set<String> successfulTools = new LinkedHashSet<>();
    private final List<Map<String, Object>> observations = new ArrayList<>();
    private Map<String, Object> latestInventory = Map.of();
    private Map<String, Object> latestSemanticResult = Map.of();
    private Map<String, Object> latestPricingResult = Map.of();
    private Map<String, Object> latestChangeResult = Map.of();
    private int toolFailureCount;
    private int noProgressCount;
    private int finalRejectionCount;
    private boolean recovered;

    public void submitPlan(Map<String, Object> plan) {
        this.requestPlan = immutableCopy(plan);
        successfulTools.add("submit_request_plan");
        noProgressCount = 0;
    }

    public boolean hasPlan() {
        return !requestPlan.isEmpty();
    }

    public Map<String, Object> requestPlan() {
        return requestPlan;
    }

    public void recordSuccess(String toolName, Map<String, Object> result) {
        if (StringUtils.hasText(toolName)) {
            successfulTools.add(toolName);
        }
        noProgressCount = 0;
        if ("get_knowledge_inventory".equals(toolName)) {
            latestInventory = immutableCopy(result);
        } else if ("search_semantic_memory".equals(toolName)
                || "find_canonical_price_candidates".equals(toolName)) {
            latestSemanticResult = immutableCopy(result);
        } else if ("calculate_order_price".equals(toolName)) {
            latestPricingResult = immutableCopy(result);
        } else if ("create_change_set".equals(toolName)) {
            latestChangeResult = immutableCopy(result);
        }
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("tool", toolName);
        observation.put("status", "SUCCESS");
        observation.put("summary", compact(result));
        observations.add(observation);
        trimObservations();
    }

    public void recordFailure(String toolName, String errorMessage) {
        toolFailureCount++;
        noProgressCount++;
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("tool", toolName);
        observation.put("status", "FAILED");
        observation.put("error", truncate(errorMessage, 1200));
        observations.add(observation);
        trimObservations();
    }

    public void recordNoProgress() {
        noProgressCount++;
    }

    public void recordFinalRejection(List<String> reasons) {
        finalRejectionCount++;
        noProgressCount++;
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("tool", "submit_final_answer");
        observation.put("status", "REJECTED");
        observation.put("missingRequirements", reasons == null ? List.of() : List.copyOf(reasons));
        observations.add(observation);
        trimObservations();
    }

    public List<String> validateFinalPayload(Map<String, Object> finalPayload) {
        List<String> missing = new ArrayList<>();
        if (!hasPlan()) {
            missing.add("submit_request_plan이 먼저 완료되어야 합니다.");
            return missing;
        }

        boolean clarification = bool(finalPayload.get("requiresClarification"), false)
                || "NEED_CLARIFICATION".equals(text(finalPayload.get("status")));
        boolean requiresDatabase = bool(requestPlan.get("requiresDatabase"), false);
        boolean requiresSemantic = bool(requestPlan.get("requiresSemanticSearch"), false);
        boolean requiresMutation = bool(requestPlan.get("requiresMutation"), false);
        boolean requiresPricing = bool(requestPlan.get("requiresDeterministicPricing"), false);
        String finalStatus = text(finalPayload.get("status"));
        boolean blockedOrError = "BLOCKED".equals(finalStatus) || "ERROR".equals(finalStatus);

        if (requiresDatabase && !clarification && !blockedOrError && !hasAnySuccessfulTool(
                "get_database_overview", "get_knowledge_inventory", "search_database_catalog",
                "describe_table", "list_table_relationships", "get_table_statistics",
                "query_database", "search_semantic_memory", "find_canonical_price_candidates",
                "calculate_order_price")) {
            missing.add("DB 또는 RAG 근거 도구가 한 번 이상 성공해야 합니다.");
        }
        if (requiresSemantic && !clarification && !blockedOrError
                && !hasAnySuccessfulTool("search_semantic_memory", "find_canonical_price_candidates")) {
            missing.add("유사 후보 판단을 위해 search_semantic_memory 또는 find_canonical_price_candidates가 필요합니다.");
        }
        if (requiresPricing && !clarification && !blockedOrError) {
            if (!successfulTools.contains("calculate_order_price")) {
                missing.add("확정 가격 답변에는 calculate_order_price가 필요합니다.");
            } else if (!bool(latestPricingResult.get("calculated"), false)) {
                missing.add("가격 계산이 완료되지 않았으므로 확정 가격 대신 필요한 입력을 질문해야 합니다.");
            }
        }
        if (requiresMutation && !clarification && !blockedOrError
                && !successfulTools.contains("create_change_set")) {
            missing.add("저장·수정·삭제 요청에는 create_change_set 또는 명시적인 확인 질문이 필요합니다.");
        }

        String payloadChangeSetId = text(finalPayload.get("changeSetId"));
        String actualChangeSetId = text(latestChangeResult.get("changeSetId"));
        if (StringUtils.hasText(payloadChangeSetId)
                && StringUtils.hasText(actualChangeSetId)
                && !payloadChangeSetId.equals(actualChangeSetId)) {
            missing.add("최종답변의 changeSetId가 실제 생성된 ChangeSet과 일치하지 않습니다.");
        }
        if (StringUtils.hasText(actualChangeSetId)
                && !StringUtils.hasText(payloadChangeSetId)
                && requiresMutation
                && !clarification) {
            missing.add("생성된 ChangeSet ID를 최종답변에 포함해야 합니다.");
        }
        return missing;
    }

    public boolean hasAnySuccessfulTool(String... names) {
        if (names == null) return false;
        for (String name : names) {
            if (successfulTools.contains(name)) return true;
        }
        return false;
    }

    public Set<String> successfulTools() {
        return Collections.unmodifiableSet(successfulTools);
    }

    public List<Map<String, Object>> observations() {
        return List.copyOf(observations);
    }

    public Map<String, Object> latestInventory() {
        return latestInventory;
    }

    public Map<String, Object> latestSemanticResult() {
        return latestSemanticResult;
    }

    public Map<String, Object> latestPricingResult() {
        return latestPricingResult;
    }

    public Map<String, Object> latestChangeResult() {
        return latestChangeResult;
    }

    public int toolFailureCount() {
        return toolFailureCount;
    }

    public int noProgressCount() {
        return noProgressCount;
    }

    public int finalRejectionCount() {
        return finalRejectionCount;
    }

    public boolean recovered() {
        return recovered;
    }

    public void markRecovered() {
        this.recovered = true;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestPlan", requestPlan);
        result.put("successfulTools", List.copyOf(successfulTools));
        result.put("observations", observations());
        result.put("latestInventory", latestInventory);
        result.put("latestSemanticResult", latestSemanticResult);
        result.put("latestPricingResult", latestPricingResult);
        result.put("latestChangeResult", latestChangeResult);
        result.put("toolFailureCount", toolFailureCount);
        result.put("noProgressCount", noProgressCount);
        result.put("finalRejectionCount", finalRejectionCount);
        result.put("recovered", recovered);
        return result;
    }

    private Map<String, Object> compact(Map<String, Object> result) {
        if (result == null || result.isEmpty()) return Map.of();
        Map<String, Object> compact = new LinkedHashMap<>();
        for (String key : List.of(
                "success", "accepted", "rowCount", "totalRows", "resultCount", "candidateCount",
                "calculated", "total", "currency", "changeSetId", "applied", "status",
                "populatedTableCount", "semanticReadyCount", "queuePendingCount")) {
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

    private void trimObservations() {
        while (observations.size() > 40) observations.remove(0);
    }

    private Map<String, Object> immutableCopy(Map<String, Object> input) {
        if (input == null || input.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...[TRUNCATED]";
    }
}
