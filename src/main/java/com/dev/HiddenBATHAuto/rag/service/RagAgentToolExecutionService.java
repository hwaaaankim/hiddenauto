package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.config.RagOpenAiProperties;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagAgentToolExecutionService {

    private final ObjectMapper objectMapper;
    private final RagAgentDatabaseToolService databaseToolService;
    private final RagAgentChangeSetService changeSetService;
    private final RagAgentToolAuditService auditService;
    private final RagAgentPlanService planService;
    private final RagAgentObservationService observationService;
    private final RagSemanticMemoryService semanticMemoryService;
    private final RagAgentPricingToolService pricingToolService;
    private final RagOpenAiProperties properties;

    public RagAgentToolExecutionService(ObjectMapper objectMapper,
                                        RagAgentDatabaseToolService databaseToolService,
                                        RagAgentChangeSetService changeSetService,
                                        RagAgentToolAuditService auditService,
                                        RagAgentPlanService planService,
                                        RagAgentObservationService observationService,
                                        RagSemanticMemoryService semanticMemoryService,
                                        RagAgentPricingToolService pricingToolService,
                                        RagOpenAiProperties properties) {
        this.objectMapper = objectMapper;
        this.databaseToolService = databaseToolService;
        this.changeSetService = changeSetService;
        this.auditService = auditService;
        this.planService = planService;
        this.observationService = observationService;
        this.semanticMemoryService = semanticMemoryService;
        this.pricingToolService = pricingToolService;
        this.properties = properties;
    }

    public ToolExecutionResult execute(RagAgentToolContext context, OpenAiRagClient.ToolCall toolCall) {
        long started = System.nanoTime();
        Map<String, Object> args = new LinkedHashMap<>();
        Map<String, Object> result;
        boolean terminal = false;
        Map<String, Object> finalPayload = Map.of();
        Map<String, Object> changeResult = Map.of();
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            args = parseArguments(toolCall.argumentsJson());
            String name = toolCall.name();
            enforcePlanOrder(context, name);
            result = switch (name) {
                case "submit_request_plan" -> planService.submit(context, args);
                case "get_database_overview" -> databaseToolService.overview(context);
                case "get_knowledge_inventory" -> semanticMemoryService.inventory(
                        context,
                        stringList(args.get("domains")),
                        bool(args.get("exactCounts"), true),
                        bool(args.get("includeSamples"), true),
                        integer(args.get("sampleLimit"), 3));
                case "search_database_catalog" -> databaseToolService.searchCatalog(
                        context,
                        text(args.get("query"), ""),
                        stringList(args.get("objectTypes")),
                        integer(args.get("limit"), 50));
                case "search_semantic_memory" -> semanticMemoryService.search(
                        context,
                        text(args.get("query"), ""),
                        stringList(args.get("domains")),
                        stringList(args.get("sourceKinds")),
                        integer(args.get("limit"), properties.getSemanticDefaultSearchLimit()),
                        decimal(args.get("minimumScore"), BigDecimal.ZERO),
                        bool(args.get("includeInactive"), false));
                case "describe_table" -> databaseToolService.describeTable(
                        context,
                        text(args.get("schemaName"), "public"),
                        text(args.get("tableName"), ""),
                        integer(args.get("sampleLimit"), 3));
                case "list_table_relationships" -> databaseToolService.relationships(
                        context,
                        text(args.get("schemaName"), "public"),
                        nullableText(args.get("tableName")));
                case "get_table_statistics" -> databaseToolService.statistics(
                        context,
                        text(args.get("schemaName"), "public"),
                        text(args.get("tableName"), ""),
                        bool(args.get("exactCount"), false));
                case "query_database" -> databaseToolService.query(
                        context,
                        text(args.get("purpose"), "GPT DB tool query"),
                        text(args.get("sql"), ""),
                        text(args.get("paramsJson"), "{}"),
                        integer(args.get("maxRows"), properties.getAgentDefaultReadRows()),
                        toolCall.callId());
                case "find_canonical_price_candidates" -> pricingToolService.findCandidates(
                        context,
                        text(args.get("query"), ""),
                        nullableText(args.get("entityType")),
                        integer(args.get("limit"), 15));
                case "calculate_order_price" -> pricingToolService.calculate(
                        context,
                        parseJsonObject(text(args.get("answersJson"), "{}"), "answersJson"));
                case "create_change_set" -> {
                    Map<String, Object> applied = changeSetService.persistAndMaybeApply(
                            context.runId(), context.projectId(), context.versionId(), context.sessionId(),
                            context.sourceScope(), args, context.forceSave());
                    changeResult = applied;
                    yield applied;
                }
                case "get_change_set" -> changeSetService.detail(parseUuid(text(args.get("changeSetId"), "")));
                case "submit_final_answer" -> {
                    Map<String, Object> normalized = normalizeFinalPayload(args);
                    List<String> missingRequirements = context.runState().validateFinalPayload(normalized);
                    if (missingRequirements.isEmpty()) {
                        terminal = true;
                        finalPayload = normalized;
                        yield Map.of("accepted", true, "terminal", true);
                    }
                    status = "REJECTED";
                    context.runState().recordFinalRejection(missingRequirements);
                    Map<String, Object> rejected = new LinkedHashMap<>();
                    rejected.put("accepted", false);
                    rejected.put("terminal", false);
                    rejected.put("missingRequirements", missingRequirements);
                    rejected.put("nextInstruction", "누락된 근거 도구를 실행하거나, 필요한 정보가 없으면 requiresClarification=true인 확인 질문으로 다시 submit_final_answer를 호출하십시오.");
                    yield rejected;
                }
                default -> throw new IllegalArgumentException("등록되지 않은 Agent 도구입니다: " + name);
            };

            if ("SUCCESS".equals(status)) {
                context.runState().recordSuccess(toolCall.name(), result);
            }
        } catch (Exception e) {
            status = "FAILED";
            errorMessage = safeError(e);
            context.runState().recordFailure(toolCall.name(), errorMessage);
            result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("tool", toolCall.name());
            result.put("error", errorMessage);
            result.put("retryGuidance", "오류 내용을 확인하고 catalog/describe/semantic 도구로 구조와 후보를 다시 확인한 뒤 수정된 인자로 재호출하십시오.");
        }

        long durationMs = (System.nanoTime() - started) / 1_000_000L;
        Map<String, Object> modelOutput = limitForModel(result);
        auditService.log(context, toolCall.callId(), toolCall.name(), args, modelOutput, status, durationMs, errorMessage);
        observationService.record(context, toolCall.callId(), toolCall.name(), status, modelOutput);

        String outputJson = RagJsonUtils.toJson(objectMapper, modelOutput);
        return new ToolExecutionResult(toolCall.name(), outputJson, modelOutput, terminal, finalPayload, changeResult, status, errorMessage);
    }

    private void enforcePlanOrder(RagAgentToolContext context, String toolName) {
        if ("submit_request_plan".equals(toolName)) return;
        if (!context.runState().hasPlan()) {
            throw new IllegalStateException("첫 도구는 submit_request_plan이어야 합니다.");
        }
    }

    private Map<String, Object> normalizeFinalPayload(Map<String, Object> args) {
        Map<String, Object> result = new LinkedHashMap<>();
        String status = text(args.get("status"), "ERROR");
        if (!List.of("READY_TO_ANSWER", "NEED_CLARIFICATION", "BLOCKED", "ERROR").contains(status)) {
            status = "ERROR";
        }
        result.put("status", status);
        result.put("answer", text(args.get("answer"), "요청 처리 결과를 생성하지 못했습니다."));
        result.put("confidence", decimal(args.get("confidence"), BigDecimal.ZERO));
        result.put("requiresClarification", bool(args.get("requiresClarification"), "NEED_CLARIFICATION".equals(status)));
        result.put("changeSetId", nullableText(args.get("changeSetId")));
        result.put("evidence", mapList(args.get("evidence")));
        result.put("riskNotes", stringList(args.get("riskNotes")));
        return result;
    }

    private Map<String, Object> limitForModel(Map<String, Object> result) {
        String json = RagJsonUtils.toJson(objectMapper, result);
        int max = properties.getAgentMaxToolOutputChars();
        if (json.length() <= max) return result;
        Map<String, Object> limited = new LinkedHashMap<>();
        limited.put("success", !Boolean.FALSE.equals(result.get("success")));
        limited.put("truncated", true);
        limited.put("originalCharacters", json.length());
        limited.put("outputPreview", json.substring(0, max));
        limited.put("guidance", "결과가 커서 잘렸습니다. 더 구체적인 WHERE/LIMIT, domain 필터 또는 집계 조회로 다시 호출하십시오.");
        return limited;
    }

    private Map<String, Object> parseArguments(String json) {
        if (!StringUtils.hasText(json)) return new LinkedHashMap<>();
        return parseJsonObject(json, "function arguments");
    }

    private Map<String, Object> parseJsonObject(String json, String label) {
        if (!StringUtils.hasText(json)) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException(label + " JSON 파싱 실패: " + e.getMessage(), e);
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) if (item != null) result.add(String.valueOf(item));
        return List.copyOf(result);
    }

    private List<Map<String, Object>> mapList(Object value) {
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
        }
        return List.copyOf(result);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("유효한 UUID가 아닙니다: " + value, e);
        }
    }

    private String text(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String nullableText(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) && !"null".equalsIgnoreCase(text) ? text : null;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (Exception e) { return fallback; }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) return b;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return value == null ? fallback : new BigDecimal(String.valueOf(value)); }
        catch (Exception e) { return fallback; }
    }

    private String safeError(Exception error) {
        String message = error == null ? "" : error.getMessage();
        if (!StringUtils.hasText(message)) message = error == null ? "unknown" : error.getClass().getSimpleName();
        return message.length() <= 4000 ? message : message.substring(0, 4000) + "...[TRUNCATED]";
    }

    public record ToolExecutionResult(
            String toolName,
            String outputJson,
            Map<String, Object> modelOutput,
            boolean terminal,
            Map<String, Object> finalPayload,
            Map<String, Object> changeResult,
            String status,
            String errorMessage
    ) {
    }
}
