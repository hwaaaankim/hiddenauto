package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private final RagAgentCapabilityToolService capabilityToolService;
    private final RagAgentKnowledgeToolService knowledgeToolService;
    private final RagAgentConversationToolService conversationToolService;
    private final RagAgentImpactToolService impactToolService;
    private final RagAgentRunAuditToolService runAuditToolService;
    private final RagOpenAiProperties properties;

    public RagAgentToolExecutionService(ObjectMapper objectMapper,
                                        RagAgentDatabaseToolService databaseToolService,
                                        RagAgentChangeSetService changeSetService,
                                        RagAgentToolAuditService auditService,
                                        RagAgentPlanService planService,
                                        RagAgentObservationService observationService,
                                        RagSemanticMemoryService semanticMemoryService,
                                        RagAgentPricingToolService pricingToolService,
                                        RagAgentCapabilityToolService capabilityToolService,
                                        RagAgentKnowledgeToolService knowledgeToolService,
                                        RagAgentConversationToolService conversationToolService,
                                        RagAgentImpactToolService impactToolService,
                                        RagAgentRunAuditToolService runAuditToolService,
                                        RagOpenAiProperties properties) {
        this.objectMapper = objectMapper;
        this.databaseToolService = databaseToolService;
        this.changeSetService = changeSetService;
        this.auditService = auditService;
        this.planService = planService;
        this.observationService = observationService;
        this.semanticMemoryService = semanticMemoryService;
        this.pricingToolService = pricingToolService;
        this.capabilityToolService = capabilityToolService;
        this.knowledgeToolService = knowledgeToolService;
        this.conversationToolService = conversationToolService;
        this.impactToolService = impactToolService;
        this.runAuditToolService = runAuditToolService;
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
            if (!RagAgentToolDefinitionFactory.isAllowedForScope(name, context.sourceScope())) {
                throw new IllegalArgumentException("현재 " + context.sourceScope() + " 범위에서 허용되지 않은 Agent 도구입니다: " + name);
            }
            enforcePlanOrder(context, name);
            result = switch (name) {
                case "submit_request_plan" -> planService.submit(context, args);
                case "get_agent_capabilities" -> capabilityToolService.capabilities(context, stringList(args.get("categories")));
                case "get_database_overview" -> databaseToolService.overview(context);
                case "get_knowledge_inventory" -> semanticMemoryService.inventory(
                        context, stringList(args.get("domains")), bool(args.get("exactCounts"), true),
                        bool(args.get("includeSamples"), true), integer(args.get("sampleLimit"), 3));
                case "search_database_catalog" -> databaseToolService.searchCatalog(
                        context, requiredText(args.get("query"), "query"), stringList(args.get("objectTypes")),
                        integer(args.get("limit"), 50));
                case "search_semantic_memory" -> semanticMemoryService.search(
                        context, requiredText(args.get("query"), "query"), stringList(args.get("domains")),
                        stringList(args.get("sourceKinds")), integer(args.get("limit"), properties.getSemanticDefaultSearchLimit()),
                        decimal(args.get("minimumScore"), BigDecimal.ZERO), bool(args.get("includeInactive"), false));
                case "search_knowledge_sources" -> knowledgeToolService.searchKnowledgeSources(
                        context, requiredText(args.get("query"), "query"), stringList(args.get("domains")),
                        stringList(args.get("sourceKinds")), integer(args.get("limit"), 20),
                        bool(args.get("includeInactive"), false));
                case "get_document_context" -> knowledgeToolService.documentContext(
                        context, parseUuid(requiredText(args.get("documentId"), "documentId")),
                        nullableText(args.get("searchText")), integer(args.get("beforeChunks"), 2),
                        integer(args.get("afterChunks"), 2), integer(args.get("maxCharacters"), 30000));
                case "resolve_entity_reference" -> knowledgeToolService.resolveEntityReference(
                        context, requiredText(args.get("expression"), "expression"), nullableText(args.get("entityType")),
                        integer(args.get("limit"), 15), decimal(args.get("minimumConfidence"), BigDecimal.ZERO));
                case "get_entity_context_bundle" -> knowledgeToolService.entityContextBundle(
                        context, nullableText(args.get("entityType")), requiredText(args.get("entityKey"), "entityKey"),
                        bool(args.get("includeInactive"), false), integer(args.get("limitPerSection"), 30));
                case "get_effective_rules" -> knowledgeToolService.effectiveRules(
                        context, nullableText(args.get("entityType")), nullableText(args.get("entityKey")),
                        parseNullableDate(args.get("effectiveDate")), stringList(args.get("ruleTypes")),
                        bool(args.get("includeInactive"), false));
                case "get_order_flow" -> knowledgeToolService.orderFlow(
                        context, nullableText(args.get("entityType")), nullableText(args.get("entityKey")),
                        nullableText(args.get("purpose")), bool(args.get("includeInactive"), false));
                case "validate_order_state" -> knowledgeToolService.validateOrderState(
                        context, nullableText(args.get("entityType")), nullableText(args.get("entityKey")),
                        parseJsonObject(requiredText(args.get("orderStateJson"), "orderStateJson"), "orderStateJson"),
                        parseNullableDate(args.get("effectiveDate")));
                case "compare_entity_candidates" -> knowledgeToolService.compareCandidates(
                        context, parseJsonArrayOfObjects(requiredText(args.get("candidateRefsJson"), "candidateRefsJson"), "candidateRefsJson"),
                        integer(args.get("limitPerSection"), 15));
                case "describe_table" -> databaseToolService.describeTable(
                        context, text(args.get("schemaName"), "public"), requiredText(args.get("tableName"), "tableName"),
                        integer(args.get("sampleLimit"), 3));
                case "list_table_relationships" -> databaseToolService.relationships(
                        context, text(args.get("schemaName"), "public"), nullableText(args.get("tableName")));
                case "get_table_statistics" -> databaseToolService.statistics(
                        context, text(args.get("schemaName"), "public"), requiredText(args.get("tableName"), "tableName"),
                        bool(args.get("exactCount"), false));
                case "query_database" -> databaseToolService.query(
                        context, requiredText(args.get("purpose"), "purpose"), requiredText(args.get("sql"), "sql"),
                        text(args.get("paramsJson"), "{}"), integer(args.get("maxRows"), properties.getAgentDefaultReadRows()),
                        toolCall.callId());
                case "find_canonical_price_candidates" -> pricingToolService.findCandidates(
                        context, requiredText(args.get("query"), "query"), nullableText(args.get("entityType")),
                        integer(args.get("limit"), 15));
                case "calculate_order_price" -> pricingToolService.calculate(
                        context, parseJsonObject(requiredText(args.get("answersJson"), "answersJson"), "answersJson"));
                case "simulate_price_scenarios" -> pricingToolService.simulate(
                        context, parseJsonArrayOfObjects(requiredText(args.get("scenariosJson"), "scenariosJson"), "scenariosJson"),
                        bool(args.get("stopOnError"), false));
                case "preview_change_impact" -> impactToolService.preview(
                        context, requiredText(args.get("targetTable"), "targetTable"),
                        parseUuid(requiredText(args.get("targetId"), "targetId")),
                        requiredText(args.get("operation"), "operation"), integer(args.get("sampleLimit"), 5));
                case "get_conversation_memory" -> conversationToolService.getMemory(context);
                case "update_conversation_memory" -> conversationToolService.updateMemory(
                        context, requiredText(args.get("mode"), "mode"),
                        parseJsonObject(text(args.get("memoryJson"), "{}"), "memoryJson"),
                        decimal(args.get("confidence"), new BigDecimal("0.8000")),
                        requiredText(args.get("reason"), "reason"));
                case "get_conversation_history" -> conversationToolService.history(
                        context, integer(args.get("limit"), 20), bool(args.get("includeSystem"), true));
                case "create_change_set" -> {
                    Map<String, Object> applied = changeSetService.persistAndMaybeApply(
                            context.runId(), context.projectId(), context.versionId(), context.sessionId(),
                            context.sourceScope(), args, context.forceSave());
                    changeResult = applied;
                    yield applied;
                }
                case "get_change_set" -> changeSetService.detailScoped(
                        parseUuid(requiredText(args.get("changeSetId"), "changeSetId")),
                        context.projectId(), context.versionId(), context.sessionId());
                case "get_agent_run_audit" -> runAuditToolService.inspect(
                        context, parseNullableUuid(args.get("runId")),
                        bool(args.get("includeToolArguments"), false),
                        bool(args.get("includeToolResults"), false),
                        integer(args.get("limit"), 30));
                case "submit_final_answer" -> {
                    Map<String, Object> normalized = normalizeFinalPayload(args);
                    List<String> missingRequirements = context.runState().validateFinalPayload(normalized);
                    if (missingRequirements.isEmpty()) {
                        terminal = true;
                        finalPayload = normalized;
                        yield Map.of("accepted", true, "terminal", true, "answerOwner", "GPT");
                    }
                    status = "REJECTED";
                    context.runState().recordFinalRejection(missingRequirements);
                    yield Map.of(
                            "accepted", false,
                            "terminal", false,
                            "missingRequirements", missingRequirements,
                            "nextInstruction", "누락된 근거 도구를 실행하거나, 근거가 부족하면 requiresClarification=true인 구체적 확인 질문으로 submit_final_answer를 다시 호출하십시오."
                    );
                }
                default -> throw new IllegalArgumentException("등록되지 않은 Agent 도구입니다: " + name);
            };

            if ("SUCCESS".equals(status)) context.runState().recordSuccess(toolCall.name(), result);
        } catch (Exception e) {
            status = "FAILED";
            errorMessage = safeError(e);
            context.runState().recordFailure(toolCall.name(), errorMessage);
            result = new LinkedHashMap<>();
            result.put("success", false);
            result.put("tool", toolCall.name());
            result.put("error", errorMessage);
            result.put("retryGuidance", "오류에 맞게 인자 또는 조사 순서를 수정해 재호출하십시오. 테이블을 모르면 catalog/describe, 엔티티가 모호하면 resolve_entity_reference를 먼저 사용하십시오.");
        }

        long durationMs = (System.nanoTime() - started) / 1_000_000L;
        Map<String, Object> modelOutput = limitForModel(result);
        auditService.log(context, toolCall.callId(), toolCall.name(), args, modelOutput, status, durationMs, errorMessage);
        observationService.record(context, toolCall.callId(), toolCall.name(), status, modelOutput);
        return new ToolExecutionResult(
                toolCall.name(), RagJsonUtils.toJson(objectMapper, modelOutput), modelOutput,
                terminal, finalPayload, changeResult, status, errorMessage);
    }

    private void enforcePlanOrder(RagAgentToolContext context, String toolName) {
        if ("submit_request_plan".equals(toolName)) return;
        if (!context.runState().hasPlan()) throw new IllegalStateException("첫 도구는 submit_request_plan이어야 합니다.");
    }

    private Map<String, Object> normalizeFinalPayload(Map<String, Object> args) {
        String status = requiredText(args.get("status"), "status");
        if (!List.of("READY_TO_ANSWER", "NEED_CLARIFICATION", "BLOCKED").contains(status)) {
            throw new IllegalArgumentException("허용되지 않는 최종 status입니다: " + status);
        }
        String answer = requiredText(args.get("answer"), "answer");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("responseClass", requiredText(args.get("responseClass"), "responseClass"));
        result.put("answer", answer);
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
        limited.put("guidance", "결과가 커서 잘렸습니다. 더 구체적인 필터, document context, entity bundle 또는 LIMIT으로 다시 호출하십시오.");
        return limited;
    }

    private Map<String, Object> parseArguments(String json) { return parseJsonObject(text(json, "{}"), "function arguments"); }

    private Map<String, Object> parseJsonObject(String json, String label) {
        try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) { throw new IllegalArgumentException(label + " JSON object 파싱 실패: " + e.getMessage(), e); }
    }

    private List<Map<String, Object>> parseJsonArrayOfObjects(String json, String label) {
        try { return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {}); }
        catch (Exception e) { throw new IllegalArgumentException(label + " JSON array 파싱 실패: " + e.getMessage(), e); }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) if (item != null && StringUtils.hasText(String.valueOf(item))) result.add(String.valueOf(item).trim());
        return List.copyOf(result);
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) if (item instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) if (entry.getKey() != null) copy.put(String.valueOf(entry.getKey()), entry.getValue());
            result.add(copy);
        }
        return List.copyOf(result);
    }

    private UUID parseUuid(String value) {
        try { return UUID.fromString(value); }
        catch (Exception e) { throw new IllegalArgumentException("유효한 UUID가 아닙니다: " + value, e); }
    }

    private UUID parseNullableUuid(Object value) {
        String text = nullableText(value);
        return StringUtils.hasText(text) ? parseUuid(text) : null;
    }

    private LocalDate parseNullableDate(Object value) {
        String text = nullableText(value);
        if (!StringUtils.hasText(text)) return null;
        try { return LocalDate.parse(text); }
        catch (Exception e) { throw new IllegalArgumentException("날짜는 YYYY-MM-DD 형식이어야 합니다: " + text, e); }
    }

    private String requiredText(Object value, String field) {
        String text = nullableText(value);
        if (!StringUtils.hasText(text)) throw new IllegalArgumentException(field + " 값이 비어 있습니다.");
        return text;
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
            String toolName, String outputJson, Map<String, Object> modelOutput,
            boolean terminal, Map<String, Object> finalPayload, Map<String, Object> changeResult,
            String status, String errorMessage) {}
}
