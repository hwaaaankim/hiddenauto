package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Java와 GPT가 의미를 한 번만 판단하고 끝내지 않도록 만든 공통 오케스트레이터입니다.
 *
 * 동작:
 * 1. Java가 후보 제품/규칙/파일상태를 수집합니다.
 * 2. GPT semantic planner가 1차 실행 계획을 만듭니다.
 * 3. Java가 그 계획으로 실제 DB 조회를 해 봅니다.
 * 4. 조회 결과가 0건이거나, 전체조회 문장을 제품명으로 오해했거나, 가격 계산 값이 부족하면 GPT에 2차 보정 질문을 합니다.
 * 5. 보정된 finalPlan으로 다시 조회하고, 서비스 계층은 finalPlan만 믿고 실행합니다.
 */
@Service
public class RagSemanticOrchestratorService {

    private static final Pattern W_PATTERN = Pattern.compile("(?:^|[^A-Za-z0-9가-힣])(?:W|w|WIDTH|width|넓이|폭|가로)\\s*[:=]?\\s*([0-9]{2,5}(?:\\.[0-9]+)?)");
    private static final Pattern H_PATTERN = Pattern.compile("(?:^|[^A-Za-z0-9가-힣])(?:H|h|HEIGHT|height|높이|세로)\\s*[:=]?\\s*([0-9]{2,5}(?:\\.[0-9]+)?)");
    private static final Pattern D_PATTERN = Pattern.compile("(?:^|[^A-Za-z0-9가-힣])(?:D|d|DEPTH|depth|깊이|뎁스)\\s*[:=]?\\s*([0-9]{2,5}(?:\\.[0-9]+)?)");
    private static final Pattern QTY_PATTERN = Pattern.compile("([0-9]{1,4})\\s*(?:개|EA|ea|수량)");

    private final RagSemanticPlannerService plannerService;
    private final RagKnowledgeRetrievalService retrievalService;
    private final OpenAiRagClient openAi;
    private final ObjectMapper objectMapper;
    private final NamedParameterJdbcTemplate jdbc;

    public RagSemanticOrchestratorService(RagSemanticPlannerService plannerService,
                                          RagKnowledgeRetrievalService retrievalService,
                                          OpenAiRagClient openAi,
                                          ObjectMapper objectMapper,
                                          @Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.plannerService = plannerService;
        this.retrievalService = retrievalService;
        this.openAi = openAi;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    public SemanticResolution resolve(UUID projectId,
                                      UUID versionId,
                                      UUID sessionId,
                                      String sourceScope,
                                      String userMessage,
                                      boolean hasFiles,
                                      boolean hasImages,
                                      boolean hasExcels,
                                      List<Map<String, Object>> entityCandidates,
                                      List<Map<String, Object>> activeRules) {
        String message = userMessage == null ? "" : userMessage.trim();
        List<Map<String, Object>> candidates = entityCandidates == null ? List.of() : entityCandidates;
        List<Map<String, Object>> rules = activeRules == null ? List.of() : activeRules;

        Map<String, Object> firstPlan = plannerService.plan(projectId, versionId, sourceScope, message, hasFiles, hasImages, hasExcels, candidates, rules);
        firstPlan = enrichPlanWithJavaSignals(firstPlan, message, candidates);

        Map<String, Object> firstRetrieval = shouldRetrieve(firstPlan)
                ? retrievalService.retrieve(projectId, versionId, firstPlan, message)
                : Map.of();

        List<String> repairSignals = repairSignals(message, firstPlan, firstRetrieval, candidates);
        boolean shouldRepair = !repairSignals.isEmpty();

        Map<String, Object> finalPlan = firstPlan;
        Map<String, Object> repairedPlan = Map.of();
        Map<String, Object> finalRetrieval = firstRetrieval;
        boolean repaired = false;
        String repairReason = "";

        if (shouldRepair) {
            repairReason = String.join(" / ", repairSignals);
            repairedPlan = repairPlanWithGpt(projectId, versionId, sourceScope, message, hasFiles, hasImages, hasExcels, candidates, rules, firstPlan, firstRetrieval, repairSignals);
            if (repairedPlan == null || repairedPlan.isEmpty()) {
                repairedPlan = javaRepairFallback(message, firstPlan, firstRetrieval, repairSignals);
            }
            if (repairedPlan != null && !repairedPlan.isEmpty()) {
                finalPlan = enrichPlanWithJavaSignals(mergePlan(firstPlan, repairedPlan), message, candidates);
                finalRetrieval = shouldRetrieve(finalPlan)
                        ? retrievalService.retrieve(projectId, versionId, finalPlan, message)
                        : Map.of();
                repaired = true;
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("repaired", repaired);
        meta.put("repairReason", repairReason);
        meta.put("repairSignals", repairSignals);
        meta.put("firstCounts", counts(firstRetrieval));
        meta.put("finalCounts", counts(finalRetrieval));
        meta.put("sourceScope", sourceScope);
        meta.put("chatModel", openAi.chatModel());

        saveResolutionEvent(projectId, versionId, sessionId, sourceScope, message, firstPlan, firstRetrieval, repaired, repairReason, repairedPlan, finalPlan, finalRetrieval, meta);
        return new SemanticResolution(finalPlan, finalRetrieval, meta);
    }

    private boolean shouldRetrieve(Map<String, Object> plan) {
        String intent = str(plan.get("intentType"));
        return List.of("ASK_PRODUCT_AVAILABILITY", "ASK_PRODUCT_PRICE", "ASK_KNOWLEDGE_SUMMARY", "ORDER_CONVERSATION").contains(intent);
    }

    private List<String> repairSignals(String message,
                                       Map<String, Object> plan,
                                       Map<String, Object> retrieved,
                                       List<Map<String, Object>> candidates) {
        List<String> signals = new ArrayList<>();
        String intent = str(plan.get("intentType"));
        Map<String, Object> retrievalPlan = map(plan.get("retrievalPlan"));
        String queryScope = str(retrievalPlan.get("queryScope"));
        String entity = childText(plan, "primaryEntity", "name");
        int total = totalRetrievedCount(retrieved);

        if ("UNKNOWN".equals(intent)) signals.add("intent_unknown");
        if (decimal(plan.get("confidence"), BigDecimal.ONE).compareTo(new BigDecimal("0.6700")) < 0) signals.add("low_confidence");

        if (looksLikeGlobalProductAsk(message) && !isGlobalScope(queryScope)) {
            signals.add("global_product_expression_but_scope_is_not_global");
        }
        if (looksLikeGlobalProductAsk(message) && StringUtils.hasText(entity) && looksLikePseudoEntity(entity)) {
            signals.add("pseudo_entity_should_be_global_scope");
        }
        if ("EXACT_ENTITY".equals(queryScope) && total == 0 && looksLikePseudoEntity(entity)) {
            signals.add("exact_entity_zero_result_with_pseudo_entity");
        }
        if (("ASK_PRODUCT_AVAILABILITY".equals(intent) || "ASK_KNOWLEDGE_SUMMARY".equals(intent)) && total == 0 && !candidates.isEmpty()) {
            signals.add("zero_result_but_candidates_exist");
        }
        if ("ASK_PRODUCT_PRICE".equals(intent)) {
            Map<String, Object> pq = map(plan.get("priceQuery"));
            Map<String, Object> dims = extractDimensions(message);
            if (pq.get("width") == null && dims.get("width") != null) signals.add("price_width_missing_but_java_detected");
            if (pq.get("height") == null && dims.get("height") != null) signals.add("price_height_missing_but_java_detected");
            if (pq.get("depth") == null && dims.get("depth") != null) signals.add("price_depth_missing_but_java_detected");
        }
        return signals;
    }

    private Map<String, Object> repairPlanWithGpt(UUID projectId,
                                                  UUID versionId,
                                                  String sourceScope,
                                                  String userMessage,
                                                  boolean hasFiles,
                                                  boolean hasImages,
                                                  boolean hasExcels,
                                                  List<Map<String, Object>> entityCandidates,
                                                  List<Map<String, Object>> activeRules,
                                                  Map<String, Object> firstPlan,
                                                  Map<String, Object> firstRetrieval,
                                                  List<String> repairSignals) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("projectId", projectId);
            payload.put("versionId", versionId);
            payload.put("sourceScope", sourceScope);
            payload.put("userMessage", userMessage);
            payload.put("hasFiles", hasFiles);
            payload.put("hasImages", hasImages);
            payload.put("hasExcels", hasExcels);
            payload.put("entityCandidates", entityCandidates);
            payload.put("activeOverrideRules", activeRules);
            payload.put("firstPlan", firstPlan);
            payload.put("firstRetrievalCounts", counts(firstRetrieval));
            payload.put("firstRetrievalDebug", firstRetrieval.getOrDefault("debugRetrieval", Map.of()));
            payload.put("javaDetectedDimensions", extractDimensions(userMessage));
            payload.put("repairSignals", repairSignals);
            payload.put("instruction", "firstPlan이 틀렸거나 부족할 가능성이 있습니다. 사용자의 진짜 의도를 final plan JSON으로 다시 작성하세요. 제품모든정보/전체상품/모든품목 같은 표현은 제품명이 아니라 전체 조회일 수 있습니다. 가격 질문이면 width/height/depth/quantity/includeCountertop/options를 priceQuery에 보강하세요.");

            String raw = openAi.responseJsonSchema(
                    repairSystemPrompt(),
                    RagJsonUtils.pretty(objectMapper, payload),
                    "rag_semantic_plan_repair",
                    RagSemanticSchemaFactory.semanticPlanSchema(),
                    true
            );
            JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, raw);
            Map<String, Object> repaired = RagJsonUtils.toMap(objectMapper, node.toString());
            repaired.put("semanticRepairSource", "GPT_REPAIR");
            return repaired;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String repairSystemPrompt() {
        return """
                당신은 HiddenBATHAuto RAG 의미 보정 오케스트레이터입니다.
                Java가 1차 GPT plan으로 실제 DB 조회를 수행한 결과와 후보 목록을 보고, final plan을 다시 작성해야 합니다.

                원칙:
                1. 사용자가 전체 제품/모든 제품/제품 전체/제품정보 전체를 요구하면 queryScope=ALL_PRODUCTS 또는 ALL_KNOWLEDGE로 보정합니다.
                2. 제품모든정보, 상품전체정보, 모든제품정보 같은 합성어를 실제 제품명으로 보지 마세요.
                3. 특정 제품 후보가 명확하고 사용자가 색상/사이즈/가격을 물으면 EXACT_ENTITY로 둡니다.
                4. 조회 결과가 0건인데 후보가 있고 문장에 후보명이 있으면 primaryEntity를 후보명으로 보정합니다.
                5. 가격 질문이면 priceQuery에 entityKey, width, height, depth, quantity, includeCountertop, options를 최대한 구조화합니다.
                6. 학습 입력이 비규격 주문 흐름/조건/검증/가격식이면 LEARN_DIALOG_RULES로 보정하고 dialogRules에 QUESTION_FLOW/CONDITION/VALIDATION/PRICING_FORMULA 규칙을 만듭니다.
                7. 모르는 값은 추정하지 말고 missingQuestions에 넣습니다.
                8. 바보방구/ㅋㅋ/무의미 단어처럼 제품·주문·가격·학습과 연결할 수 없는 입력은 UNRELATED_INPUT + UNRELATED_INPUT_GUIDE로 보정하고 directAnswer에 안내 문구를 넣습니다.
                9. 일반 인사나 잡담은 GENERAL_CHAT + DIRECT_REPLY로 보정하고 DB 조회/저장 없이 답변하게 합니다.
                10. JSON 밖에 설명을 쓰지 마세요.
                """;
    }

    private Map<String, Object> javaRepairFallback(String message,
                                                   Map<String, Object> firstPlan,
                                                   Map<String, Object> firstRetrieval,
                                                   List<String> repairSignals) {
        Map<String, Object> repaired = new LinkedHashMap<>();
        repaired.put("semanticRepairSource", "JAVA_REPAIR_FALLBACK");
        repaired.put("repairSignals", repairSignals);

        if (looksLikeGlobalProductAsk(message)) {
            repaired.put("intentType", "ASK_KNOWLEDGE_SUMMARY");
            repaired.put("confidence", new BigDecimal("0.9100"));
            repaired.put("primaryEntity", Map.of("entityType", "PRODUCT", "name", ""));
            repaired.put("retrievalPlan", linkedMap(
                    "exactFirst", false,
                    "includeRelatedAsSuggestion", false,
                    "includeRelatedInMainAnswer", true,
                    "rowLimit", 500,
                    "queryScope", "ALL_PRODUCTS"
            ));
            repaired.put("reason", "Java 보정: 전체 제품정보 요청 표현으로 판단했습니다.");
            return repaired;
        }

        if ("ASK_PRODUCT_PRICE".equals(str(firstPlan.get("intentType")))) {
            Map<String, Object> priceQuery = map(firstPlan.get("priceQuery"));
            priceQuery.putAll(extractDimensions(message));
            if (!priceQuery.containsKey("entityKey")) priceQuery.put("entityKey", childText(firstPlan, "primaryEntity", "name"));
            repaired.put("priceQuery", priceQuery);
            repaired.put("reason", "Java 보정: 가격 질문의 치수값을 문장에서 보강했습니다.");
            return repaired;
        }

        repaired.put("reason", "Java 보정 대상이 제한적이라 firstPlan을 유지합니다.");
        return repaired;
    }

    private Map<String, Object> enrichPlanWithJavaSignals(Map<String, Object> plan,
                                                          String message,
                                                          List<Map<String, Object>> candidates) {
        Map<String, Object> result = new LinkedHashMap<>(plan == null ? Map.of() : plan);
        String intent = str(result.get("intentType"));
        Map<String, Object> retrievalPlan = map(result.get("retrievalPlan"));
        Map<String, Object> primary = map(result.get("primaryEntity"));

        if (!StringUtils.hasText(intent)) {
            intent = looksLikeGlobalProductAsk(message) ? "ASK_KNOWLEDGE_SUMMARY" : "UNKNOWN";
            result.put("intentType", intent);
        }

        if (looksLikeGlobalProductAsk(message)) {
            retrievalPlan.put("queryScope", "ALL_PRODUCTS");
            retrievalPlan.put("exactFirst", false);
            retrievalPlan.put("includeRelatedInMainAnswer", true);
            retrievalPlan.put("rowLimit", Math.max(intValue(retrievalPlan.get("rowLimit"), 160), 500));
            primary.put("entityType", "PRODUCT");
            primary.put("name", "");
            if ("UNKNOWN".equals(intent) || "ASK_PRODUCT_AVAILABILITY".equals(intent)) {
                result.put("intentType", "ASK_KNOWLEDGE_SUMMARY");
            }
        }

        if ("ASK_PRODUCT_PRICE".equals(result.get("intentType"))) {
            Map<String, Object> priceQuery = map(result.get("priceQuery"));
            Map<String, Object> dims = extractDimensions(message);
            for (Map.Entry<String, Object> e : dims.entrySet()) {
                priceQuery.putIfAbsent(e.getKey(), e.getValue());
            }
            if (!StringUtils.hasText(str(priceQuery.get("entityKey")))) {
                priceQuery.put("entityKey", firstNonBlank(childText(result, "primaryEntity", "name"), firstCandidateName(candidates)));
            }
            result.put("priceQuery", priceQuery);
        }

        retrievalPlan.putIfAbsent("queryScope", looksLikeGlobalProductAsk(message) ? "ALL_PRODUCTS" : "EXACT_ENTITY");
        retrievalPlan.putIfAbsent("rowLimit", 160);
        result.put("retrievalPlan", retrievalPlan);
        result.put("primaryEntity", primary);
        return result;
    }

    private Map<String, Object> mergePlan(Map<String, Object> base, Map<String, Object> patch) {
        Map<String, Object> merged = new LinkedHashMap<>(base == null ? Map.of() : base);
        if (patch == null) return merged;
        for (Map.Entry<String, Object> e : patch.entrySet()) {
            if (e.getValue() == null) continue;
            if (e.getValue() instanceof Map<?, ?> pm && merged.get(e.getKey()) instanceof Map<?, ?> bm) {
                Map<String, Object> nested = map(bm);
                nested.putAll(map(pm));
                merged.put(e.getKey(), nested);
            } else if (e.getValue() instanceof List<?> list && list.isEmpty()) {
                continue;
            } else if (StringUtils.hasText(str(e.getValue())) || e.getValue() instanceof Number || e.getValue() instanceof Boolean || e.getValue() instanceof Map<?, ?> || e.getValue() instanceof List<?>) {
                merged.put(e.getKey(), e.getValue());
            }
        }
        return merged;
    }

    private Map<String, Object> extractDimensions(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        putIfFound(result, "width", W_PATTERN, message);
        putIfFound(result, "height", H_PATTERN, message);
        putIfFound(result, "depth", D_PATTERN, message);
        Matcher qty = QTY_PATTERN.matcher(message == null ? "" : message);
        if (qty.find()) result.put("quantity", Integer.parseInt(qty.group(1)));
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (lower.contains("상판") || lower.contains("countertop")) result.put("includeCountertop", true);
        return result;
    }

    private void putIfFound(Map<String, Object> map, String key, Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message == null ? "" : message);
        if (matcher.find()) map.put(key, new BigDecimal(matcher.group(1)));
    }

    private boolean looksLikeGlobalProductAsk(String message) {
        String compact = message == null ? "" : message.replaceAll("\\s+", "");
        return containsAny(compact, "모든제품", "전체제품", "제품전체", "상품전체", "전체상품", "모든상품", "모든품목", "전체품목", "품목전체", "제품모든정보", "제품정보전체", "제품정보싹", "전부", "싹다")
                || (containsAny(compact, "제품", "상품", "품목") && containsAny(compact, "모든", "전체", "전부", "싹", "다보여", "다알려"));
    }

    private boolean looksLikePseudoEntity(String entity) {
        String compact = entity == null ? "" : entity.replaceAll("\\s+", "");
        if (!StringUtils.hasText(compact)) return false;
        return containsAny(compact, "모든제품", "전체제품", "제품전체", "제품정보", "상품전체", "품목전체", "저장데이터", "학습내용")
                || (containsAny(compact, "제품", "상품", "품목") && containsAny(compact, "모든", "전체", "정보", "전부"));
    }

    private boolean isGlobalScope(String queryScope) {
        return List.of("ALL_PRODUCTS", "ALL_KNOWLEDGE", "PRICING_RULES").contains(str(queryScope));
    }

    private int totalRetrievedCount(Map<String, Object> retrieved) {
        Map<String, Object> counts = counts(retrieved);
        int total = 0;
        for (Object value : counts.values()) total += intValue(value, 0);
        return total;
    }

    private Map<String, Object> counts(Map<String, Object> retrieved) {
        if (retrieved == null || retrieved.isEmpty()) return Map.of();
        Map<String, Object> raw = map(retrieved.get("counts"));
        if (!raw.isEmpty()) return raw;
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("exactRows", "fallbackRows", "effectiveRows", "relatedRows", "knowledgeNodes", "chunks", "pricingRules", "assets")) {
            Object value = retrieved.get(key);
            if (value instanceof List<?> list) result.put(key, list.size());
        }
        return result;
    }

    private void saveResolutionEvent(UUID projectId,
                                     UUID versionId,
                                     UUID sessionId,
                                     String sourceScope,
                                     String userMessage,
                                     Map<String, Object> firstPlan,
                                     Map<String, Object> firstRetrieval,
                                     boolean repaired,
                                     String repairReason,
                                     Map<String, Object> repairedPlan,
                                     Map<String, Object> finalPlan,
                                     Map<String, Object> finalRetrieval,
                                     Map<String, Object> meta) {
        try {
            jdbc.update("""
                    INSERT INTO rag_semantic_resolution_event(
                        id, project_id, version_id, session_id, source_scope, user_message,
                        first_plan_json, first_retrieval_json, repaired, repair_reason,
                        repaired_plan_json, final_plan_json, final_retrieval_json, metadata_json, created_at
                    ) VALUES (
                        :id, :projectId, :versionId, :sessionId, :sourceScope, :userMessage,
                        CAST(:firstPlan AS jsonb), CAST(:firstRetrieval AS jsonb), :repaired, :repairReason,
                        CAST(:repairedPlan AS jsonb), CAST(:finalPlan AS jsonb), CAST(:finalRetrieval AS jsonb), CAST(:metadata AS jsonb), now()
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("sessionId", sessionId)
                    .addValue("sourceScope", sourceScope)
                    .addValue("userMessage", userMessage)
                    .addValue("firstPlan", toJson(firstPlan))
                    .addValue("firstRetrieval", toJson(slimRetrieval(firstRetrieval)))
                    .addValue("repaired", repaired)
                    .addValue("repairReason", repairReason)
                    .addValue("repairedPlan", toJson(repairedPlan))
                    .addValue("finalPlan", toJson(finalPlan))
                    .addValue("finalRetrieval", toJson(slimRetrieval(finalRetrieval)))
                    .addValue("metadata", toJson(meta)));
        } catch (DataAccessException ignored) {
            // DB 패치 적용 전에도 서비스가 죽지 않도록 의미판정 이력 저장 실패는 무시합니다.
        }
    }

    private Map<String, Object> slimRetrieval(Map<String, Object> retrieval) {
        if (retrieval == null || retrieval.isEmpty()) return Map.of();
        Map<String, Object> slim = new LinkedHashMap<>();
        slim.put("counts", counts(retrieval));
        slim.put("debugRetrieval", retrieval.getOrDefault("debugRetrieval", Map.of()));
        slim.put("availabilitySummary", retrieval.getOrDefault("availabilitySummary", Map.of()));
        slim.put("relatedSummary", retrieval.getOrDefault("relatedSummary", Map.of()));
        return slim;
    }

    private String toJson(Object value) {
        return RagJsonUtils.toJson(objectMapper, value == null ? Map.of() : value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
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
        return str(map(map.get(child)).get(key));
    }

    private String firstCandidateName(List<Map<String, Object>> candidates) {
        if (candidates == null) return "";
        for (Map<String, Object> c : candidates) {
            String name = str(c.get("name"));
            if (StringUtils.hasText(name)) return name;
        }
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String v : values) if (StringUtils.hasText(v)) return v.trim();
        return "";
    }

    private Map<String, Object> linkedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        return map;
    }

    private boolean containsAny(String text, String... words) {
        if (text == null) return false;
        for (String word : words) {
            if (StringUtils.hasText(word) && text.contains(word)) return true;
        }
        return false;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (Exception e) { return fallback; }
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return value == null ? fallback : new BigDecimal(String.valueOf(value)); }
        catch (Exception e) { return fallback; }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record SemanticResolution(Map<String, Object> plan,
                                     Map<String, Object> retrieved,
                                     Map<String, Object> meta) {}
}
