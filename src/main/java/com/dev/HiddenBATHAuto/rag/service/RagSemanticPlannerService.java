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

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagSemanticPlannerService {

    private static final Pattern QUOTED = Pattern.compile("[\\'\\\"‘’“”]([^\\'\\\"‘’“”]{1,80})[\\'\\\"‘’“”]");
    private static final Pattern COLOR_TOKEN = Pattern.compile("(?<![A-Za-z0-9가-힣])([A-Z]{1,8}|[가-힣]{1,12})(?:\\s*)(?:색상|컬러|색)?(?:은|는|이|가)?(?:\\s*)(?:불가|제외|안됨|안 돼|못|만들 수 없어|생산 불가|단종)");

    private final OpenAiRagClient openAi;
    private final ObjectMapper objectMapper;

    public RagSemanticPlannerService(OpenAiRagClient openAi, ObjectMapper objectMapper) {
        this.openAi = openAi;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> plan(UUID projectId,
                                    UUID versionId,
                                    String sourceScope,
                                    String userMessage,
                                    boolean hasFiles,
                                    boolean hasImages,
                                    boolean hasExcels,
                                    List<Map<String, Object>> entityCandidates,
                                    List<Map<String, Object>> activeRules) {
        String clean = userMessage == null ? "" : userMessage.trim();
        Map<String, Object> fallback = fallbackPlan(clean, sourceScope, hasFiles, hasImages, hasExcels, entityCandidates);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("projectId", projectId);
            payload.put("versionId", versionId);
            payload.put("sourceScope", sourceScope);
            payload.put("userMessage", clean);
            payload.put("hasFiles", hasFiles);
            payload.put("hasImages", hasImages);
            payload.put("hasExcels", hasExcels);
            payload.put("entityCandidates", entityCandidates == null ? List.of() : entityCandidates);
            payload.put("activeOverrideRules", activeRules == null ? List.of() : activeRules);
            payload.put("fallbackPlan", fallback);

            String raw = openAi.responseJsonSchema(
                    systemPrompt(),
                    RagJsonUtils.pretty(objectMapper, payload),
                    "rag_semantic_plan",
                    RagSemanticSchemaFactory.semanticPlanSchema(),
                    true
            );
            JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, raw);
            Map<String, Object> plan = RagJsonUtils.toMap(objectMapper, node.toString());
            return normalizePlan(plan, fallback, clean, entityCandidates);
        } catch (Exception e) {
            fallback.put("plannerError", e.getMessage());
            return normalizePlan(fallback, fallback, clean, entityCandidates);
        }
    }

    private String systemPrompt() {
        return """
                당신은 HiddenBATHAuto RAG 의미 해석 플래너입니다.
                사용자의 문장을 단순 키워드나 Java if문으로 처리하지 않도록, 반드시 실행 가능한 JSON 계획을 만드세요.

                목표:
                1. 사용자가 학습시키려는지, 저장된 지식에 질문하는지, 주문 상담 중인지, 이미지/파일을 연결하려는지, 기존 지식을 수정하려는지 판단합니다.
                2. 조회 범위는 retrievalPlan.queryScope로 반드시 결정합니다.
                   - 특정 제품 질문: EXACT_ENTITY
                   - 비슷한/관련 제품까지: RELATED_ENTITIES
                   - 저장된 모든 제품/제품 전체/상품 전체/등록된 품목 전체: ALL_PRODUCTS
                   - 저장된 지식 전체 요약: ALL_KNOWLEDGE
                   - 가격 규칙 목록/가격표: PRICING_RULES
                   Java 서버가 "모든", "전체" 같은 단어를 직접 찾지 않도록 queryScope를 의미로 판단하세요.
                3. 특정 제품 질문에서는 primaryEntity와 relatedEntities를 구분합니다.
                   예: 사용자가 "코지장"을 물었고 후보에 "라운드 코지장"도 있으면,
                   primaryEntity는 "코지장", relatedEntities에는 "라운드 코지장"을 넣고,
                   main answer에는 코지장 중심으로 답하며 라운드 코지장은 제안으로만 언급하게 합니다.
                4. "더 이상", "불가", "못 만든다", "제외", "단종", "없다", "없어", "안 된다", "빼줘" 같은 수정 문장은 단순 노드가 아니라 structured override rule로 저장하도록 계획합니다.
                   예: "코지장은 더 이상 HC 색상 불가", "코지장에 HC 색상은 없어", "코지장에서 HC는 빼줘"는
                   entity=코지장, fieldName=색상, ruleType=DISALLOW, ruleValue=HC 입니다.
                5. 가격 규칙 문장은 UPDATE_PRODUCT_PRICING_RULE로 판단합니다.
                   예: "코지장 HW는 5만원, HB는 10만원, 넓이 500 기준, 100 증가당 5천원"은
                   entity=코지장, optionPrices=[HW=50000, HB=100000], baseWidth=500, widthStep=100, widthStepPrice=5000 입니다.
                6. 가격 질문은 ASK_PRODUCT_PRICE로 판단합니다.
                   예: "사이즈 600, HB 코지장은 얼마야"는 entity=코지장, optionValue=HB, width=600 입니다.
                7. 비규격 주문 프로세스/질문 순서/조건부 질문/답변 검증/가격 계산 순서/옵션 조건을 학습시키는 문장은 LEARN_DIALOG_RULES로 판단하고 dialogRules에 실행 가능한 규칙으로 분해합니다.
                   - 질문 흐름은 QUESTION_FLOW 또는 NEXT_QUESTION
                   - 특정 조건에서만 나타나는 질문은 CONDITION 또는 NEXT_QUESTION
                   - 입력값 검증은 VALIDATION
                   - 옵션 가능/불가능은 OPTION_AVAILABILITY
                   - 가격 계산식/추가금 공식은 PRICING_FORMULA 또는 PRICE_ADJUSTMENT
                8. 엑셀/파일 학습은 STRUCTURED_TABLE, PRODUCT_AVAILABILITY_FACT, PRICE_TABLE 같은 저장 구조를 계획합니다.
                9. 모르는 값은 추정하지 말고 confidence를 낮추고 missingQuestions에 적습니다.
                10. 제품/주문/가격/학습/파일과 무관한 일반 인사는 GENERAL_CHAT + DIRECT_REPLY로 처리합니다.
                11. "바보방구", "ㅋㅋ", 의미 없는 욕설/장난/무의미 단어처럼 학습·주문·조회와 연결할 수 없는 입력은 UNRELATED_INPUT + UNRELATED_INPUT_GUIDE로 처리하고 DB 조회/저장 없이 directAnswer에 안내 문구를 넣습니다.

                반환 JSON 스키마:
                {
                  "intentType": "ASK_PRODUCT_AVAILABILITY | ASK_PRODUCT_PRICE | ASK_KNOWLEDGE_SUMMARY | UPDATE_PRODUCT_AVAILABILITY_RULE | UPDATE_PRODUCT_PRICING_RULE | LEARN_STRUCTURED_FILE | LEARN_DIALOG_RULES | LINK_ASSET_TO_ENTITY | ORDER_CONVERSATION | GENERAL_CHAT | OUT_OF_DOMAIN | UNRELATED_INPUT | RESET_KNOWLEDGE | UNKNOWN",
                  "confidence": 0.0,
                  "primaryEntity": {"entityType":"PRODUCT", "name":""},
                  "relatedEntities": [{"entityType":"PRODUCT", "name":"", "relationship":"SIMILAR_NAME", "includeInMainAnswer":false}],
                  "requestedFields": ["색상", "사이즈"],
                  "retrievalPlan": {
                    "exactFirst": true,
                    "includeRelatedAsSuggestion": true,
                    "includeRelatedInMainAnswer": false,
                    "rowLimit": 160,
                    "queryScope":"EXACT_ENTITY"
                  },
                  "updateRule": {
                    "entityType":"PRODUCT",
                    "entityKey":"",
                    "fieldName":"색상",
                    "ruleType":"DISALLOW",
                    "ruleValue":"",
                    "reason":""
                  },
                  "pricingRule": {
                    "entityKey":"",
                    "optionField":"색상",
                    "baseWidth":0,
                    "baseHeight":0,
                    "baseDepth":0,
                    "widthStep":0,
                    "widthStepPrice":0,
                    "optionPrices":[{"optionField":"색상", "optionValue":"HW", "basePrice":50000}],
                    "reason":""
                  },
                  "priceQuery": {
                    "entityKey":"",
                    "optionField":"색상",
                    "optionValue":"HB",
                    "width":600,
                    "height":0,
                    "depth":0,
                    "quantity":1
                  },
                  "dialogRules": [
                    {
                      "ruleType":"QUESTION_FLOW",
                      "ruleKey":"",
                      "entityType":"PRODUCT",
                      "entityKey":"상부장_비규격",
                      "stepKey":"select_series",
                      "fieldName":"시리즈",
                      "priority":100,
                      "condition":{"whenField":"","operator":"ALWAYS","value":"","expressionText":"항상 먼저 시리즈를 묻는다"},
                      "action":{"actionType":"ASK","questionText":"원하시는 시리즈를 선택해 주세요.","answerType":"SELECT_ONE","allowedValuesText":"학습된 시리즈 목록","nextStepKey":"select_product","message":""},
                      "validation":{"required":true,"minValue":0,"maxValue":0,"pattern":"","errorMessage":"시리즈를 선택해야 합니다."},
                      "pricing":{"formulaText":"","basePrice":0,"stepField":"","stepSize":0,"stepPrice":0,"roundingPolicy":""},
                      "reason":""
                    }
                  ],
                  "storagePlan": {
                    "storeAs":"NONE | STRUCTURED_OVERRIDE_RULE | STRUCTURED_PRICING_RULE | STRUCTURED_TABLE | DIALOG_RULE | QUESTION_FLOW | PRICING_FORMULA | ASSET_LINK | KNOWLEDGE_NODE",
                    "alsoStoreAsKnowledgeNode": true,
                    "affectsExistingRows": false,
                    "applyAtQueryAndOrderValidation": false
                  },
                  "executionPlan": {
                    "actionType":"ANSWER_FROM_DB | SAVE_DIALOG_RULES | SAVE_STRUCTURED_RULE | SAVE_PRICING_RULE | SAVE_STRUCTURED_TABLE | LINK_ASSET | PASS_TO_LEARNING_JOB | PASS_TO_CHAT_ENGINE | ASK_CLARIFICATION | CALCULATE_PRICE | RESET_BLOCKED | DIRECT_REPLY | UNRELATED_INPUT_GUIDE | NOOP",
                    "requiresDbLookup":true,
                    "requiresGptAnswer":true,
                    "requiresAsyncJob":false,
                    "requiresUserConfirmation":false,
                    "serverAction":""
                  },
                  "directAnswer":"",
                  "answerPolicy": {
                    "mainAnswerFocus":"",
                    "mentionRelatedAsSuggestion":true,
                    "askClarifyingQuestion":false,
                    "tone":"KOREAN_POLITE_BUSINESS"
                  },
                  "missingQuestions": [],
                  "reason":""
                }
                JSON 하나만 반환하세요. 설명 문장은 JSON 밖에 쓰지 마세요.
                """;
    }

    private Map<String, Object> normalizePlan(Map<String, Object> plan,
                                              Map<String, Object> fallback,
                                              String message,
                                              List<Map<String, Object>> candidates) {
        Map<String, Object> result = new LinkedHashMap<>();
        String intent = str(plan.get("intentType"));
        if (!StringUtils.hasText(intent)) intent = str(fallback.get("intentType"));
        result.put("intentType", normalizeIntent(intent));

        BigDecimal confidence = decimal(plan.get("confidence"), decimal(fallback.get("confidence"), new BigDecimal("0.5000")));
        result.put("confidence", confidence);

        Map<String, Object> primary = map(plan.get("primaryEntity"));
        if (!StringUtils.hasText(str(primary.get("name")))) primary = map(fallback.get("primaryEntity"));
        if (!StringUtils.hasText(str(primary.get("name")))) {
            String candidate = firstCandidateName(candidates);
            if (StringUtils.hasText(candidate)) {
                primary.put("entityType", "PRODUCT");
                primary.put("name", candidate);
            }
        }
        if (!StringUtils.hasText(str(primary.get("entityType")))) primary.put("entityType", "PRODUCT");
        result.put("primaryEntity", primary);

        List<Map<String, Object>> related = mapList(plan.get("relatedEntities"));
        if (related.isEmpty()) related = mapList(fallback.get("relatedEntities"));
        result.put("relatedEntities", related);

        List<String> fields = stringList(plan.get("requestedFields"));
        if (fields.isEmpty()) fields = stringList(fallback.get("requestedFields"));
        if (fields.isEmpty() && (message.contains("색") || message.contains("컬러"))) fields.add("색상");
        if (fields.isEmpty() && (message.contains("사이즈") || message.contains("규격") || message.contains("크기"))) fields.add("사이즈");
        result.put("requestedFields", fields);

        Map<String, Object> retrieval = map(plan.get("retrievalPlan"));
        if (retrieval.isEmpty()) retrieval = map(fallback.get("retrievalPlan"));
        retrieval.putIfAbsent("exactFirst", true);
        retrieval.putIfAbsent("includeRelatedAsSuggestion", true);
        retrieval.putIfAbsent("includeRelatedInMainAnswer", false);
        retrieval.putIfAbsent("rowLimit", 160);
        retrieval.putIfAbsent("queryScope", defaultQueryScope(str(result.get("intentType")), str(primary.get("name")), message));
        result.put("retrievalPlan", retrieval);

        Map<String, Object> update = map(plan.get("updateRule"));
        if (update.isEmpty() || !StringUtils.hasText(str(update.get("ruleValue")))) {
            Map<String, Object> fallbackUpdate = map(fallback.get("updateRule"));
            if (!fallbackUpdate.isEmpty()) update = mergePreferLeft(update, fallbackUpdate);
        }
        if (!StringUtils.hasText(str(update.get("entityType")))) update.put("entityType", "PRODUCT");
        if (!StringUtils.hasText(str(update.get("entityKey")))) update.put("entityKey", str(primary.get("name")));
        result.put("updateRule", update);
        result.put("pricingRule", map(plan.get("pricingRule")));
        result.put("priceQuery", map(plan.get("priceQuery")));
        List<Map<String, Object>> dialogRules = mapList(plan.get("dialogRules"));
        if (dialogRules.isEmpty()) dialogRules = mapList(fallback.get("dialogRules"));
        result.put("dialogRules", dialogRules);

        Map<String, Object> storage = map(plan.get("storagePlan"));
        if (storage.isEmpty()) storage = map(fallback.get("storagePlan"));
        storage.putIfAbsent("storeAs", storageForIntent(str(result.get("intentType"))));
        storage.putIfAbsent("alsoStoreAsKnowledgeNode", true);
        storage.putIfAbsent("affectsExistingRows", false);
        storage.putIfAbsent("applyAtQueryAndOrderValidation", false);
        storage.putIfAbsent("replacementPolicy", "MERGE");
        result.put("storagePlan", storage);

        Map<String, Object> execution = map(plan.get("executionPlan"));
        if (execution.isEmpty()) execution = map(fallback.get("executionPlan"));
        execution.putIfAbsent("actionType", executionActionForIntent(str(result.get("intentType")), str(storage.get("storeAs"))));
        execution.putIfAbsent("requiresDbLookup", requiresDbLookup(str(result.get("intentType"))));
        execution.putIfAbsent("requiresGptAnswer", true);
        execution.putIfAbsent("requiresAsyncJob", "LEARN_STRUCTURED_FILE".equals(str(result.get("intentType"))));
        execution.putIfAbsent("requiresUserConfirmation", false);
        execution.putIfAbsent("serverAction", execution.get("actionType"));
        result.put("executionPlan", execution);

        result.put("directAnswer", StringUtils.hasText(str(plan.get("directAnswer"))) ? str(plan.get("directAnswer")) : str(fallback.get("directAnswer")));

        Map<String, Object> answer = map(plan.get("answerPolicy"));
        if (answer.isEmpty()) answer = map(fallback.get("answerPolicy"));
        answer.putIfAbsent("mainAnswerFocus", str(primary.get("name")));
        answer.putIfAbsent("mentionRelatedAsSuggestion", true);
        answer.putIfAbsent("askClarifyingQuestion", false);
        answer.putIfAbsent("tone", "KOREAN_POLITE_BUSINESS");
        result.put("answerPolicy", answer);

        result.put("missingQuestions", mapListOrStringList(plan.get("missingQuestions")));
        result.put("reason", StringUtils.hasText(str(plan.get("reason"))) ? str(plan.get("reason")) : str(fallback.get("reason")));
        if (plan.containsKey("plannerError")) result.put("plannerError", plan.get("plannerError"));
        if (fallback.containsKey("plannerError")) result.put("plannerError", fallback.get("plannerError"));
        return forceNaturalCorrectionPlanIfNeeded(result, message, candidates);
    }


    private Map<String, Object> forceNaturalCorrectionPlanIfNeeded(Map<String, Object> plan,
                                                                    String message,
                                                                    List<Map<String, Object>> candidates) {
        String compact = message == null ? "" : message.replaceAll("\\s+", "");
        if (!looksLikeUpdateRule(compact)) return plan;

        String currentIntent = str(plan.get("intentType"));
        if ("UPDATE_PRODUCT_AVAILABILITY_RULE".equals(currentIntent)) return plan;

        String entity = firstEntityFromMessageOrCandidates(message, candidates);
        if (!StringUtils.hasText(entity)) entity = childText(plan, "primaryEntity", "name");
        String field = (message.contains("색") || message.contains("컬러")) ? "색상"
                : (message.contains("사이즈") || message.contains("규격") || message.contains("크기")) ? "사이즈"
                : (message.contains("옵션") ? "옵션" : "조건");
        String value = extractRuleValue(message, field);

        if (!StringUtils.hasText(entity) || !StringUtils.hasText(value)) {
            return plan;
        }

        Map<String, Object> updateRule = new LinkedHashMap<>();
        updateRule.put("entityType", "PRODUCT");
        updateRule.put("entityKey", entity);
        updateRule.put("fieldName", field);
        updateRule.put("ruleType", "DISALLOW");
        updateRule.put("ruleValue", value);
        updateRule.put("reason", "사용자 자연어 수정 지시: " + message);

        Map<String, Object> primary = map(plan.get("primaryEntity"));
        primary.put("entityType", "PRODUCT");
        primary.put("name", entity);

        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("storeAs", "STRUCTURED_OVERRIDE_RULE");
        storage.put("alsoStoreAsKnowledgeNode", true);
        storage.put("affectsExistingRows", true);
        storage.put("applyAtQueryAndOrderValidation", true);

        plan.put("directAnswer", "");

        Map<String, Object> answer = map(plan.get("answerPolicy"));
        answer.put("mainAnswerFocus", entity);
        answer.put("mentionRelatedAsSuggestion", true);
        answer.put("askClarifyingQuestion", false);
        answer.put("tone", "KOREAN_POLITE_BUSINESS");

        plan.put("intentType", "UPDATE_PRODUCT_AVAILABILITY_RULE");
        plan.put("confidence", new BigDecimal("0.9600"));
        plan.put("primaryEntity", primary);
        plan.put("requestedFields", List.of(field));
        plan.put("updateRule", updateRule);
        plan.put("storagePlan", storage);
        plan.put("answerPolicy", answer);
        plan.put("reason", "사용자의 자연어 부정/제외 표현을 구조화 수정 규칙으로 강제 보정했습니다.");
        return plan;
    }

    private String childText(Map<String, Object> map, String child, String key) {
        return str(map(map.get(child)).get(key));
    }

    private Map<String, Object> fallbackPlan(String message,
                                             String sourceScope,
                                             boolean hasFiles,
                                             boolean hasImages,
                                             boolean hasExcels,
                                             List<Map<String, Object>> candidates) {
        String compact = message.replaceAll("\\s+", "");
        String entity = firstEntityFromMessageOrCandidates(message, candidates);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("confidence", new BigDecimal("0.6200"));
        p.put("primaryEntity", Map.of("entityType", "PRODUCT", "name", entity == null ? "" : entity));
        p.put("relatedEntities", relatedFromCandidates(entity, candidates));
        p.put("requestedFields", requestedFields(message));
        p.put("retrievalPlan", linkedMap("exactFirst", true, "includeRelatedAsSuggestion", true, "includeRelatedInMainAnswer", false, "rowLimit", 160, "queryScope", defaultQueryScope("UNKNOWN", entity, message)));
        p.put("answerPolicy", Map.of("mainAnswerFocus", entity == null ? "" : entity, "mentionRelatedAsSuggestion", true, "askClarifyingQuestion", false, "tone", "KOREAN_POLITE_BUSINESS", "doNotPersist", false));
        p.put("dialogRules", List.of());
        p.put("directAnswer", "");
        p.put("executionPlan", Map.of("actionType", "NOOP", "requiresDbLookup", false, "requiresGptAnswer", true, "requiresAsyncJob", false, "requiresUserConfirmation", false, "serverAction", "NOOP"));
        p.put("reason", "서버 fallback 의미 해석");

        if (!hasFiles && looksLikeGreeting(compact)) {
            p.put("intentType", "GENERAL_CHAT");
            p.put("confidence", new BigDecimal("0.9000"));
            p.put("directAnswer", "안녕하세요. 제품 옵션 조회, 가격 계산, 주문 상담, 학습 입력 중 필요한 내용을 말씀해 주세요.");
            p.put("storagePlan", Map.of("storeAs", "NONE", "alsoStoreAsKnowledgeNode", false, "affectsExistingRows", false, "applyAtQueryAndOrderValidation", false, "replacementPolicy", "NONE"));
            p.put("executionPlan", Map.of("actionType", "DIRECT_REPLY", "requiresDbLookup", false, "requiresGptAnswer", false, "requiresAsyncJob", false, "requiresUserConfirmation", false, "serverAction", "DIRECT_REPLY"));
            return p;
        }
        if (!hasFiles && looksLikeUnrelatedInput(compact)) {
            p.put("intentType", "UNRELATED_INPUT");
            p.put("confidence", new BigDecimal("0.9200"));
            p.put("directAnswer", unrelatedInputGuide());
            p.put("storagePlan", Map.of("storeAs", "NONE", "alsoStoreAsKnowledgeNode", false, "affectsExistingRows", false, "applyAtQueryAndOrderValidation", false, "replacementPolicy", "NONE"));
            p.put("executionPlan", Map.of("actionType", "UNRELATED_INPUT_GUIDE", "requiresDbLookup", false, "requiresGptAnswer", false, "requiresAsyncJob", false, "requiresUserConfirmation", false, "serverAction", "UNRELATED_INPUT_GUIDE"));
            p.put("reason", "제품/주문/가격/학습과 연관되지 않는 의미 없는 입력으로 판단했습니다.");
            return p;
        }
        if (looksLikeReset(compact)) {
            p.put("intentType", "RESET_KNOWLEDGE");
            p.put("storagePlan", Map.of("storeAs", "NONE"));
            return p;
        }
        if (hasFiles && (hasImages || containsAny(compact, "이미지", "사진", "그림", "도면", "첨부"))
                && containsAny(compact, "연결", "등록", "매칭", "이야", "이다", "대표")) {
            p.put("intentType", "LINK_ASSET_TO_ENTITY");
            p.put("storagePlan", Map.of("storeAs", "ASSET_LINK", "alsoStoreAsKnowledgeNode", true));
            return p;
        }
        if (looksLikeUpdateRule(compact)) {
            p.put("intentType", "UPDATE_PRODUCT_AVAILABILITY_RULE");
            p.put("confidence", new BigDecimal("0.8400"));
            p.put("updateRule", fallbackUpdateRule(message, entity));
            p.put("storagePlan", Map.of("storeAs", "STRUCTURED_OVERRIDE_RULE", "alsoStoreAsKnowledgeNode", true, "affectsExistingRows", true, "applyAtQueryAndOrderValidation", true, "replacementPolicy", "MERGE"));
            return p;
        }
        if (looksLikePricingRule(compact)) {
            p.put("intentType", "UPDATE_PRODUCT_PRICING_RULE");
            p.put("confidence", new BigDecimal("0.9000"));
            p.put("storagePlan", Map.of("storeAs", "STRUCTURED_PRICING_RULE", "alsoStoreAsKnowledgeNode", true, "affectsExistingRows", true, "applyAtQueryAndOrderValidation", true, "replacementPolicy", "MERGE"));
            return p;
        }
        if (looksLikePriceAsk(compact)) {
            p.put("intentType", "ASK_PRODUCT_PRICE");
            p.put("storagePlan", Map.of("storeAs", "NONE", "alsoStoreAsKnowledgeNode", false, "affectsExistingRows", false, "applyAtQueryAndOrderValidation", false, "replacementPolicy", "NONE"));
            return p;
        }
        if (!hasExcels && (looksLikeDialogLearning(compact) || (looksLikeLearning(compact) && looksLikeOrderProcess(compact)))) {
            p.put("intentType", "LEARN_DIALOG_RULES");
            p.put("confidence", new BigDecimal("0.8800"));
            p.put("storagePlan", Map.of("storeAs", "DIALOG_RULE", "alsoStoreAsKnowledgeNode", true, "affectsExistingRows", true, "applyAtQueryAndOrderValidation", true, "replacementPolicy", "MERGE"));
            p.put("executionPlan", Map.of("actionType", "SAVE_DIALOG_RULES", "requiresDbLookup", false, "requiresGptAnswer", true, "requiresAsyncJob", false, "requiresUserConfirmation", false, "serverAction", "SAVE_DIALOG_RULES"));
            return p;
        }
        if (hasExcels || looksLikeLearning(compact)) {
            p.put("intentType", "LEARN_STRUCTURED_FILE");
            p.put("storagePlan", Map.of("storeAs", "STRUCTURED_TABLE", "alsoStoreAsKnowledgeNode", true, "affectsExistingRows", true, "applyAtQueryAndOrderValidation", true, "replacementPolicy", "MERGE"));
            p.put("executionPlan", Map.of("actionType", "PASS_TO_LEARNING_JOB", "requiresDbLookup", false, "requiresGptAnswer", true, "requiresAsyncJob", true, "requiresUserConfirmation", false, "serverAction", "SUBMIT_LEARNING_JOB"));
            return p;
        }
        if (looksLikeSummaryAsk(compact)) {
            p.put("intentType", "ASK_KNOWLEDGE_SUMMARY");
            p.put("storagePlan", Map.of("storeAs", "NONE"));
            return p;
        }
        if (looksLikeAvailabilityAsk(compact)) {
            p.put("intentType", "ASK_PRODUCT_AVAILABILITY");
            p.put("storagePlan", Map.of("storeAs", "NONE"));
            return p;
        }
        if ("CHAT".equals(sourceScope) || looksLikeOrder(compact)) {
            p.put("intentType", "ORDER_CONVERSATION");
            p.put("storagePlan", Map.of("storeAs", "NONE"));
            return p;
        }
        p.put("intentType", "UNKNOWN");
        p.put("storagePlan", Map.of("storeAs", "NONE"));
        return p;
    }


    private String defaultQueryScope(String intentType, String entity, String message) {
        String compact = message == null ? "" : message.replaceAll("\\s+", "");
        if ("ASK_PRODUCT_AVAILABILITY".equals(intentType) || "ASK_PRODUCT_PRICE".equals(intentType)) {
            return StringUtils.hasText(entity) ? "EXACT_ENTITY" : "UNKNOWN";
        }
        if ("ASK_KNOWLEDGE_SUMMARY".equals(intentType) || !StringUtils.hasText(entity)) {
            if (compact.contains("제품") || compact.contains("상품") || compact.contains("품목")) return "ALL_PRODUCTS";
            return "ALL_KNOWLEDGE";
        }
        return "EXACT_ENTITY";
    }

    private Map<String, Object> linkedMap(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private Map<String, Object> fallbackUpdateRule(String message, String entity) {
        String field = (message.contains("색") || message.contains("컬러")) ? "색상" : (message.contains("사이즈") || message.contains("규격") ? "사이즈" : "조건");
        String value = extractRuleValue(message, field);
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("entityType", "PRODUCT");
        rule.put("entityKey", entity == null ? "" : entity);
        rule.put("fieldName", field);
        rule.put("ruleType", "DISALLOW");
        rule.put("ruleValue", value);
        rule.put("reason", "사용자 수정 지시: " + message);
        return rule;
    }

    private String extractRuleValue(String message, String field) {
        if ("색상".equals(field)) {
            Matcher matcher = Pattern.compile("(?<![A-Za-z0-9가-힣])([A-Z]{1,8})(?![A-Za-z0-9가-힣])").matcher(message);
            if (matcher.find()) return matcher.group(1).trim();
            Matcher kor = Pattern.compile("([가-힣]{1,12})\\s*(?:색상|컬러|색)").matcher(message);
            if (kor.find()) return kor.group(1).trim();
        }
        Matcher quoted = QUOTED.matcher(message);
        if (quoted.find()) return quoted.group(1).trim();
        return "";
    }

    private String firstEntityFromMessageOrCandidates(String message, List<Map<String, Object>> candidates) {
        if (candidates != null) {
            for (Map<String, Object> c : candidates) {
                String name = str(c.get("name"));
                if (StringUtils.hasText(name) && message.contains(name)) return name;
            }
            for (Map<String, Object> c : candidates) {
                String name = str(c.get("name"));
                if (StringUtils.hasText(name)) return name;
            }
        }
        Matcher quoted = QUOTED.matcher(message);
        if (quoted.find()) return quoted.group(1).trim();
        String cleaned = message.replaceAll("(가능한|색상|사이즈|규격|뭐뭐야|무엇|알려줘|설명해줘|설명해봐|반영해|더이상|더 이상|만들 수 없어|못|불가|제외|은|는|이|가|의|를|을|\\?|,|\\.)", " ").trim();
        String[] parts = cleaned.split("\\s+");
        return parts.length > 0 && parts[0].length() >= 2 ? parts[0] : null;
    }

    private List<Map<String, Object>> relatedFromCandidates(String entity, List<Map<String, Object>> candidates) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!StringUtils.hasText(entity) || candidates == null) return result;
        for (Map<String, Object> c : candidates) {
            String name = str(c.get("name"));
            if (StringUtils.hasText(name) && !name.equals(entity) && name.contains(entity)) {
                result.add(Map.of("entityType", "PRODUCT", "name", name, "relationship", "SIMILAR_NAME", "includeInMainAnswer", false));
            }
        }
        return result;
    }

    private List<String> requestedFields(String message) {
        List<String> fields = new ArrayList<>();
        if (message.contains("색") || message.toLowerCase(Locale.ROOT).contains("color") || message.contains("컬러")) fields.add("색상");
        if (message.contains("사이즈") || message.contains("규격") || message.contains("크기") || message.toLowerCase(Locale.ROOT).contains("size")) fields.add("사이즈");
        if (message.contains("가격") || message.contains("금액") || message.contains("단가")) fields.add("가격");
        return fields;
    }

    private String normalizeIntent(String intent) {
        if (!StringUtils.hasText(intent)) return "UNKNOWN";
        String s = intent.trim().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "ASK_KNOWLEDGE", "ASK_KNOWLEDGE_SUMMARY", "KNOWLEDGE_QUERY" -> "ASK_KNOWLEDGE_SUMMARY";
            case "ASK_PRODUCT", "ASK_PRODUCT_AVAILABILITY", "ASK_AVAILABILITY" -> "ASK_PRODUCT_AVAILABILITY";
            case "ASK_PRODUCT_PRICE", "ASK_PRICE", "PRICE_QUERY", "QUOTE_PRICE" -> "ASK_PRODUCT_PRICE";
            case "UPDATE_PRODUCT_AVAILABILITY_RULE", "UPDATE_RULE", "CORRECTION", "STRUCTURED_OVERRIDE_RULE" -> "UPDATE_PRODUCT_AVAILABILITY_RULE";
            case "UPDATE_PRODUCT_PRICING_RULE", "UPDATE_PRICING_RULE", "PRICE_RULE", "STRUCTURED_PRICING_RULE" -> "UPDATE_PRODUCT_PRICING_RULE";
            case "LEARN_DIALOG_RULES", "LEARN_DIALOG_RULE", "DIALOG_RULE", "QUESTION_FLOW", "LEARN_ORDER_FLOW", "LEARN_PROCESS" -> "LEARN_DIALOG_RULES";
            case "LEARN_KNOWLEDGE", "LEARN_STRUCTURED_FILE", "LEARNING", "LEARN_FILE" -> "LEARN_STRUCTURED_FILE";
            case "ASSET_LINK", "LINK_ASSET", "LINK_ASSET_TO_ENTITY" -> "LINK_ASSET_TO_ENTITY";
            case "ORDER_CONVERSATION", "ORDER", "QUOTE" -> "ORDER_CONVERSATION";
            case "GENERAL_CHAT", "SMALL_TALK", "GREETING" -> "GENERAL_CHAT";
            case "OUT_OF_DOMAIN", "OUT_OF_SCOPE" -> "OUT_OF_DOMAIN";
            case "UNRELATED_INPUT", "NONSENSE", "NOISE", "IRRELEVANT" -> "UNRELATED_INPUT";
            case "RESET_KNOWLEDGE", "RESET", "DELETE_KNOWLEDGE" -> "RESET_KNOWLEDGE";
            default -> "UNKNOWN";
        };
    }

    private String storageForIntent(String intent) {
        return switch (intent) {
            case "UPDATE_PRODUCT_AVAILABILITY_RULE" -> "STRUCTURED_OVERRIDE_RULE";
            case "UPDATE_PRODUCT_PRICING_RULE" -> "STRUCTURED_PRICING_RULE";
            case "LEARN_DIALOG_RULES" -> "DIALOG_RULE";
            case "LEARN_STRUCTURED_FILE" -> "STRUCTURED_TABLE";
            case "LINK_ASSET_TO_ENTITY" -> "ASSET_LINK";
            default -> "NONE";
        };
    }


    private String executionActionForIntent(String intent, String storeAs) {
        if ("LEARN_DIALOG_RULES".equals(intent) || "DIALOG_RULE".equals(storeAs) || "QUESTION_FLOW".equals(storeAs) || "PRICING_FORMULA".equals(storeAs)) return "SAVE_DIALOG_RULES";
        if ("UPDATE_PRODUCT_AVAILABILITY_RULE".equals(intent)) return "SAVE_STRUCTURED_RULE";
        if ("UPDATE_PRODUCT_PRICING_RULE".equals(intent)) return "SAVE_PRICING_RULE";
        if ("LEARN_STRUCTURED_FILE".equals(intent)) return "PASS_TO_LEARNING_JOB";
        if ("ASK_PRODUCT_PRICE".equals(intent)) return "CALCULATE_PRICE";
        if ("ASK_PRODUCT_AVAILABILITY".equals(intent) || "ASK_KNOWLEDGE_SUMMARY".equals(intent)) return "ANSWER_FROM_DB";
        if ("LINK_ASSET_TO_ENTITY".equals(intent)) return "LINK_ASSET";
        if ("RESET_KNOWLEDGE".equals(intent)) return "RESET_BLOCKED";
        if ("GENERAL_CHAT".equals(intent) || "OUT_OF_DOMAIN".equals(intent)) return "DIRECT_REPLY";
        if ("UNRELATED_INPUT".equals(intent)) return "UNRELATED_INPUT_GUIDE";
        if ("ORDER_CONVERSATION".equals(intent)) return "PASS_TO_CHAT_ENGINE";
        return "NOOP";
    }

    private boolean requiresDbLookup(String intent) {
        return List.of("ASK_PRODUCT_AVAILABILITY", "ASK_PRODUCT_PRICE", "ASK_KNOWLEDGE_SUMMARY", "ORDER_CONVERSATION").contains(intent);
    }

    private boolean looksLikeDialogLearning(String compact) {
        return containsAny(compact, "주문프로세스", "주문흐름", "질문흐름", "질문순서", "조건부질문", "답변형태", "검증규칙", "가격계산순서", "가격계산프로세스", "비규격", "경우의수");
    }

    private boolean looksLikeOrderProcess(String compact) {
        return containsAny(compact, "질문", "답변", "단계", "흐름", "프로세스", "조건", "검증", "경우", "가격계산", "주문");
    }

    private boolean looksLikePricingRule(String compact) {
        return containsAny(compact, "가격", "금액", "단가", "만원", "천원", "원", "추가금", "추가")
                && containsAny(compact, "기준", "올라가", "증가", "커지", "늘어나", "반영", "저장", "규칙");
    }

    private boolean looksLikePriceAsk(String compact) {
        return containsAny(compact, "얼마", "가격", "금액", "견적", "계산")
                && !containsAny(compact, "반영", "저장", "학습", "규칙", "기준");
    }

    private boolean looksLikeUpdateRule(String compact) {
        return containsAny(compact,
                "더이상", "더이상은", "더이상안", "더이상못",
                "불가", "제외", "삭제", "빼", "빼줘", "빼라",
                "만들수없", "생산불가", "단종", "못만들", "못해", "안돼", "안됨", "안되",
                "없어", "없다", "없습니다", "없는", "없음", "아니야", "아님")
                && containsAny(compact, "색상", "색", "컬러", "사이즈", "규격", "옵션", "가능");
    }

    private boolean looksLikeAvailabilityAsk(String compact) {
        return containsAny(compact, "가능한", "가능", "뭐뭐", "무엇", "어떤", "알려줘", "조회", "보여줘")
                && containsAny(compact, "색상", "색", "컬러", "사이즈", "규격", "옵션", "가격", "금액");
    }

    private boolean looksLikeSummaryAsk(String compact) {
        return containsAny(compact, "저장된데이터", "저장된지식", "학습된내용", "현재까지저장", "현재데이터", "현재지식", "설명해", "보여줘", "요약해");
    }

    private boolean looksLikeLearning(String compact) {
        return containsAny(compact, "학습", "저장해", "반영해", "기억해", "교체", "업로드", "엑셀학습", "구조화");
    }

    private boolean looksLikeReset(String compact) {
        return containsAny(compact, "초기화", "삭제", "지워", "리셋", "reset");
    }

    private boolean looksLikeOrder(String compact) {
        return containsAny(compact, "주문", "발주", "견적", "계산", "가격", "금액", "고객", "문의");
    }

    private boolean looksLikeGreeting(String compact) {
        return containsAny(compact, "안녕", "안녕하세요", "하이", "hello", "hi", "반가워", "반갑습니다");
    }

    private boolean looksLikeUnrelatedInput(String compact) {
        if (!StringUtils.hasText(compact)) return true;
        if (compact.length() <= 1) return true;
        if (containsAny(compact, "ㅋㅋ", "ㅎㅎ", "바보방구", "방구", "멍청", "뻘소리", "아무말", "asdf", "qwer")) return true;
        boolean domainLike = looksLikeLearning(compact) || looksLikeOrder(compact) || looksLikeAvailabilityAsk(compact)
                || looksLikeSummaryAsk(compact) || looksLikeUpdateRule(compact) || looksLikePricingRule(compact)
                || looksLikePriceAsk(compact) || looksLikeDialogLearning(compact) || looksLikeOrderProcess(compact)
                || looksLikeReset(compact);
        return !domainLike && compact.length() <= 20;
    }

    private String unrelatedInputGuide() {
        return "학습 또는 주문 상담과 연관이 없는 말로 이해되어 처리할 수 없습니다. 다음 예시처럼 말씀해 주세요.\n"
                + "- 코지장이 가능한 색상은 뭐가 있어?\n"
                + "- 모든 제품 정보를 보여줘.\n"
                + "- 상부장 비규격 주문 흐름은 시리즈 선택 후 품목을 선택한다.\n"
                + "- 코지장 HB 색상은 500 기준 10만원이고 100 증가당 5천원 추가야.";
    }

    private boolean containsAny(String text, String... words) {
        if (text == null) return false;
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }

    private String firstCandidateName(List<Map<String, Object>> candidates) {
        if (candidates == null) return "";
        for (Map<String, Object> c : candidates) {
            String name = str(c.get("name"));
            if (StringUtils.hasText(name)) return name;
        }
        return "";
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) result.add(map(m));
            }
        }
        return result;
    }

    private List<Object> mapListOrStringList(Object value) {
        List<Object> result = new ArrayList<>();
        if (value instanceof List<?> list) result.addAll(list);
        return result;
    }

    private List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private Map<String, Object> mergePreferLeft(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> result = new LinkedHashMap<>(right);
        if (left != null) {
            for (Map.Entry<String, Object> e : left.entrySet()) {
                if (StringUtils.hasText(str(e.getValue()))) result.put(e.getKey(), e.getValue());
            }
        }
        return result;
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

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
