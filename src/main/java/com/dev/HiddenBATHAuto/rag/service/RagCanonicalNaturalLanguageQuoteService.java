package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 사용자의 모든 가격/견적 자연어를 먼저 GPT로 해석한 뒤 canonical fact/rule을 기준으로 계산합니다.
 *
 * 이 서비스는 "라운드 빌트 1도어장 넓이 1550이면 얼마지?" 같은 문장을 Java enum으로 분기하지 않습니다.
 * GPT가 product/factor/value 의미를 JSON으로 정규화하고, Java는 정본 DB와 안전한 산술 계산만 수행합니다.
 *
 * 가격 요소는 W/H/D/색상/두께처럼 고정하지 않고 factor_json/value_json에 저장된 동적 키를 사용합니다.
 */
@Service
public class RagCanonicalNaturalLanguageQuoteService {

    private static final int MAX_RULE_ROWS = 80;
    private static final int MAX_PRICE_ROWS = 500;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![A-Za-z0-9])([0-9]{2,5})(?:\\s*(?:mm|미리|밀리))?");
    private static final Pattern WIDTH_PATTERN = Pattern.compile("(?:W|w|폭|넓이|너비|width|WIDTH)\\s*[:=은는이가]*\\s*([0-9]{2,5})");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final OpenAiRagClient openAiClient;
    private final RagGptFinalAnswerComposerService finalAnswerComposer;
    private final RagConversationWorkingMemoryService workingMemoryService;

    public RagCanonicalNaturalLanguageQuoteService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                                   ObjectMapper objectMapper,
                                                   OpenAiRagClient openAiClient,
                                                   RagGptFinalAnswerComposerService finalAnswerComposer,
                                                   RagConversationWorkingMemoryService workingMemoryService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.openAiClient = openAiClient;
        this.finalAnswerComposer = finalAnswerComposer;
        this.workingMemoryService = workingMemoryService;
    }

    public Map<String, Object> tryHandle(UUID projectId,
                                         UUID versionId,
                                         UUID sessionId,
                                         String sourceScope,
                                         String userMessage) {
        String message = userMessage == null ? "" : userMessage.trim();
        if (!StringUtils.hasText(message)) {
            return notHandled("EMPTY_MESSAGE");
        }

        Map<String, Object> workingMemory = workingMemoryService.load(projectId, versionId, sessionId, sourceScope);
        Map<String, Object> interpretation = interpretWithGpt(projectId, versionId, sessionId, sourceScope, message, workingMemory);
        String intent = text(interpretation.get("intent"), "OTHER").toUpperCase(Locale.ROOT);
        if (!"PRICE_QUOTE".equals(intent) && !"PRICE_VALIDATION".equals(intent)) {
            logParse(projectId, versionId, sessionId, sourceScope, message, interpretation, Map.of("handled", false, "reason", "NOT_PRICE_INTENT"));
            return notHandled("NOT_PRICE_INTENT");
        }

        Map<String, Object> result = quote(projectId, versionId, sessionId, sourceScope, message, interpretation);
        if (Boolean.TRUE.equals(result.get("handled"))) {
            workingMemoryService.rememberQuote(projectId, versionId, sessionId, sourceScope, message, interpretation, result);
        }
        logParse(projectId, versionId, sessionId, sourceScope, message, interpretation, result);
        return result;
    }

    private Map<String, Object> quote(UUID projectId,
                                      UUID versionId,
                                      UUID sessionId,
                                      String sourceScope,
                                      String message,
                                      Map<String, Object> interpretation) {
        String productQuery = firstText(
                interpretation.get("productName"),
                interpretation.get("productQuery"),
                interpretation.get("subject"));
        String productCode = firstText(interpretation.get("productCode"), interpretation.get("code"));
        Map<String, Object> workingMemory = mapOf(interpretation.get("_workingMemory"));
        if (!StringUtils.hasText(productQuery)) {
            productQuery = firstText(workingMemory.get("activeSubject"));
        }
        if (!StringUtils.hasText(productCode)) {
            productCode = firstText(workingMemory.get("activeProductCode"));
        }
        Map<String, Object> requestedFactors = mapOf(interpretation.get("requestedFactors"));
        enrichFactorsFromMessage(requestedFactors, message, workingMemory);

        if (!StringUtils.hasText(productQuery) && !StringUtils.hasText(productCode)) {
            return priceAnswer(false,
                    "가격 계산을 완료하지 못했습니다. 제품명이 부족합니다. 예: 라운드 빌트 1도어장 W 650",
                    interpretation,
                    Map.of("missing", List.of("productName")));
        }

        List<Map<String, Object>> rules = findPricingRuleFacts(projectId, versionId, productQuery, productCode);
        List<Map<String, Object>> priceFacts = findPriceFacts(projectId, versionId, productQuery, productCode);
        if (rules.isEmpty() && priceFacts.isEmpty()) {
            return priceAnswer(false,
                    "정본 데이터에서 해당 제품의 가격 또는 가격 규칙을 찾지 못했습니다. 제품명/제품코드를 확인해 주세요.",
                    interpretation,
                    Map.of("productQuery", productQuery, "productCode", productCode));
        }

        final String resolvedProductQuery = productQuery;
        final String resolvedProductCode = productCode;
        Optional<Map<String, Object>> dynamicWidthRule = rules.stream()
                .filter(row -> isWidthSurchargeRule(jsonMap(row.get("factor_json")), jsonMap(row.get("value_json"))))
                .max(Comparator.comparing(row -> scoreRule(row, resolvedProductQuery, resolvedProductCode)));

        if (dynamicWidthRule.isPresent() && containsAnyWidthSignal(message, requestedFactors)) {
            return quoteWidthRule(projectId, versionId, sessionId, sourceScope, message, interpretation, requestedFactors,
                    dynamicWidthRule.get(), priceFacts);
        }

        // 동적 공식 규칙이 없거나 폭 관련 질문이 아니면, 정본 PRICE fact 기반 후보를 반환합니다.
        return quoteByNearestPriceFact(projectId, versionId, sessionId, sourceScope, message, interpretation, requestedFactors, priceFacts);
    }

    private Map<String, Object> quoteWidthRule(UUID projectId,
                                               UUID versionId,
                                               UUID sessionId,
                                               String sourceScope,
                                               String message,
                                               Map<String, Object> interpretation,
                                               Map<String, Object> requestedFactors,
                                               Map<String, Object> ruleRow,
                                               List<Map<String, Object>> priceFacts) {
        Map<String, Object> factor = jsonMap(ruleRow.get("factor_json"));
        Map<String, Object> value = jsonMap(ruleRow.get("value_json"));

        BigDecimal requestedW = numberFromFactor(requestedFactors, "W", "width", "넓이", "너비", "폭", "dimensionW");
        if (requestedW == null) {
            requestedW = extractWidthFromMessage(message);
        }
        if (requestedW == null) {
            return priceAnswer(false,
                    "가격 계산에 필요한 W/넓이 값이 부족합니다. 예: W 650 또는 넓이 650처럼 입력해 주세요.",
                    interpretation,
                    Map.of("missing", List.of("W"), "rule", safeRule(ruleRow)));
        }

        BigDecimal minWidth = number(factor.get("minWidth"), BigDecimal.ZERO);
        BigDecimal maxWidth = number(factor.get("maxWidth"), null);
        BigDecimal baseWidth = number(factor.get("baseWidth"), number(value.get("baseWidth"), new BigDecimal("500")));
        BigDecimal stepWidth = number(value.get("surchargeStepWidth"), number(factor.get("stepWidth"), new BigDecimal("100")));
        BigDecimal surchargePerStep = number(value.get("surchargePerStep"), new BigDecimal("0"));
        BigDecimal basePrice = number(value.get("basePriceNumericValue"), null);
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            BasePriceSelection selectedBase = selectBasePrice(factor, priceFacts, requestedFactors);
            if (!selectedBase.resolved()) {
                return priceAnswer(false, selectedBase.message(), interpretation, selectedBase.meta());
            }
            basePrice = selectedBase.basePrice();
        }

        String productName = text(ruleRow.get("subject_name"), text(interpretation.get("productName"), "해당 제품"));
        String displayW = strip(requestedW);

        if (maxWidth != null && requestedW.compareTo(maxWidth) > 0) {
            BigDecimal maxBasis = ceilToStep(maxWidth, stepWidth);
            BigDecimal maxSurcharge = surcharge(baseWidth, maxBasis, stepWidth, surchargePerStep);
            BigDecimal maxFinal = basePrice.add(maxSurcharge);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("quoteStatus", "OUT_OF_RANGE_MAX");
            meta.put("productName", productName);
            meta.put("requestedW", requestedW);
            meta.put("maxWidth", maxWidth);
            meta.put("basePrice", basePrice);
            meta.put("maxSurcharge", maxSurcharge);
            meta.put("maxFinalPrice", maxFinal);
            meta.put("rule", safeRule(ruleRow));
            String fallback = productName + "은(는) W 최대 " + strip(maxWidth) + "mm까지 가능합니다. "
                    + "입력하신 W " + displayW + "mm는 최대 허용 넓이를 초과해서 견적 계산이 불가합니다.\n\n"
                    + "참고로 최대 가능 W " + strip(maxWidth) + "mm 기준 가격은 "
                    + money(basePrice) + " + " + money(maxSurcharge) + " = " + money(maxFinal) + "입니다.";
            String answer = finalAnswerComposer.compose(projectId, versionId, sessionId, sourceScope, message, interpretation, meta, fallback);
            return priceAnswer(true, answer, interpretation, meta);
        }
        if (requestedW.compareTo(minWidth) < 0) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("quoteStatus", "OUT_OF_RANGE_MIN");
            meta.put("productName", productName);
            meta.put("requestedW", requestedW);
            meta.put("minWidth", minWidth);
            meta.put("rule", safeRule(ruleRow));
            String fallback = productName + "은(는) W 최소 " + strip(minWidth) + "mm부터 가능합니다. "
                    + "입력하신 W " + displayW + "mm는 최소 허용 넓이보다 작아 견적 계산이 불가합니다.";
            String answer = finalAnswerComposer.compose(projectId, versionId, sessionId, sourceScope, message, interpretation, meta, fallback);
            return priceAnswer(true, answer, interpretation, meta);
        }

        BigDecimal basisWidth = ceilToStep(requestedW, stepWidth);
        BigDecimal surcharge = surcharge(baseWidth, basisWidth, stepWidth, surchargePerStep);
        BigDecimal finalPrice = basePrice.add(surcharge);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("quoteStatus", "QUOTED");
        meta.put("productName", productName);
        meta.put("requestedW", requestedW);
        meta.put("basisWidth", basisWidth);
        meta.put("baseWidth", baseWidth);
        meta.put("stepWidth", stepWidth);
        meta.put("surchargePerStep", surchargePerStep);
        meta.put("basePrice", basePrice);
        meta.put("surcharge", surcharge);
        meta.put("finalPrice", finalPrice);
        meta.put("rule", safeRule(ruleRow));
        String fallback = productName + " W " + displayW + "mm 기준 견적입니다.\n"
                + "- 계산 기준 W: " + strip(basisWidth) + "mm\n"
                + "- 기준가: " + money(basePrice) + "\n"
                + "- 추가금: " + money(surcharge) + "\n"
                + "- 최종가: " + money(finalPrice);
        String answer = finalAnswerComposer.compose(projectId, versionId, sessionId, sourceScope, message, interpretation, meta, fallback);
        return priceAnswer(true, answer, interpretation, meta);
    }

    private Map<String, Object> quoteByNearestPriceFact(UUID projectId,
                                                        UUID versionId,
                                                        UUID sessionId,
                                                        String sourceScope,
                                                        String message,
                                                        Map<String, Object> interpretation,
                                                        Map<String, Object> requestedFactors,
                                                        List<Map<String, Object>> priceFacts) {
        if (priceFacts.isEmpty()) {
            return priceAnswer(false,
                    "정본 가격 fact를 찾지 못했습니다. 가격표 또는 정본 재구성을 확인해 주세요.",
                    interpretation,
                    Map.of("requestedFactors", requestedFactors));
        }
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> row : priceFacts) {
            Map<String, Object> factor = jsonMap(row.get("factor_json"));
            Map<String, Object> value = jsonMap(row.get("value_json"));
            int score = 0;
            for (Map.Entry<String, Object> e : requestedFactors.entrySet()) {
                String key = normalize(e.getKey());
                String requested = normalize(String.valueOf(e.getValue()));
                for (Map.Entry<String, Object> f : factor.entrySet()) {
                    if (normalize(f.getKey()).equals(key) && normalize(String.valueOf(f.getValue())).equals(requested)) {
                        score += 10;
                    }
                }
            }
            Map<String, Object> one = new LinkedHashMap<>(row);
            one.put("matchScore", score);
            one.put("factor", factor);
            one.put("value", value);
            scored.add(one);
        }
        scored.sort((a, b) -> Integer.compare(intValue(b.get("matchScore")), intValue(a.get("matchScore"))));
        Map<String, Object> best = scored.get(0);
        Map<String, Object> value = mapOf(best.get("value"));
        BigDecimal price = number(value.get("numericValue"), null);
        if (price == null) {
            return priceAnswer(false,
                    "가장 가까운 가격 후보를 찾았지만 numericValue가 없어 계산할 수 없습니다.",
                    interpretation,
                    Map.of("candidate", compactFact(best)));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("quoteStatus", "QUOTED_BY_PRICE_FACT");
        meta.put("productName", text(best.get("subject_name"), "해당 제품"));
        meta.put("basePrice", price);
        meta.put("candidate", compactFact(best));
        String fallback = text(best.get("subject_name"), "해당 제품") + " 기준가격은 " + money(price) + "입니다.";
        String answer = finalAnswerComposer.compose(projectId, versionId, sessionId, sourceScope, message, interpretation, meta, fallback);
        return priceAnswer(true, answer, interpretation, meta);
    }

    private BasePriceSelection selectBasePrice(Map<String, Object> ruleFactor,
                                               List<Map<String, Object>> priceFacts,
                                               Map<String, Object> requestedFactors) {
        String baseSize = text(ruleFactor.get("baseSize"), "");
        Set<BigDecimal> prices = new LinkedHashSet<>();
        List<Map<String, Object>> candidates = new ArrayList<>();
        String requestedColor = firstText(requestedFactors.get("색상"), requestedFactors.get("color"), requestedFactors.get("COLOR"));
        for (Map<String, Object> row : priceFacts) {
            Map<String, Object> factor = jsonMap(row.get("factor_json"));
            Map<String, Object> value = jsonMap(row.get("value_json"));
            if (StringUtils.hasText(baseSize) && !baseSize.equals(text(factor.get("사이즈"), ""))) {
                continue;
            }
            if (StringUtils.hasText(requestedColor) && !requestedColor.equalsIgnoreCase(text(factor.get("색상"), ""))) {
                continue;
            }
            BigDecimal p = number(value.get("numericValue"), null);
            if (p != null && p.compareTo(BigDecimal.ZERO) > 0) {
                prices.add(p);
                candidates.add(compactFact(row));
            }
        }
        if (prices.size() == 1) {
            return BasePriceSelection.resolved(prices.iterator().next(), Map.of("baseCandidates", candidates));
        }
        if (prices.size() > 1) {
            return BasePriceSelection.unresolved(
                    "기준 가격이 색상/옵션별로 달라 추가 확인이 필요합니다. 색상 또는 기준 옵션을 함께 입력해 주세요.",
                    Map.of("baseCandidates", candidates, "distinctBasePrices", prices));
        }
        return BasePriceSelection.unresolved(
                "가격 규칙은 찾았지만 기준 가격 fact를 찾지 못했습니다. 정본 가격 데이터 또는 basePriceNumericValue를 확인해 주세요.",
                Map.of("baseSize", baseSize, "priceFacts", priceFacts.size()));
    }

    private List<Map<String, Object>> findPricingRuleFacts(UUID projectId, UUID versionId, String productQuery, String productCode) {
        String q = StringUtils.hasText(productQuery) ? productQuery : productCode;
        String like = "%" + escapeLike(q) + "%";
        String codeLike = StringUtils.hasText(productCode) ? "%" + escapeLike(productCode) + "%" : like;
        return jdbc.queryForList("""
                SELECT id, subject_name, subject_key, fact_type, fact_key, factor_json, value_json, status, active, created_at
                FROM rag_canonical_fact
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                  AND fact_type IN ('PRICING_RULE', 'PRICE_RULE', 'DYNAMIC_PRICING_RULE')
                  AND (
                        subject_name ILIKE :q ESCAPE '\\'
                     OR subject_key ILIKE :q ESCAPE '\\'
                     OR factor_json::text ILIKE :q ESCAPE '\\'
                     OR value_json::text ILIKE :q ESCAPE '\\'
                     OR factor_json::text ILIKE :code ESCAPE '\\'
                     OR value_json::text ILIKE :code ESCAPE '\\'
                  )
                ORDER BY created_at DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("q", like)
                .addValue("code", codeLike)
                .addValue("limit", MAX_RULE_ROWS));
    }

    private List<Map<String, Object>> findPriceFacts(UUID projectId, UUID versionId, String productQuery, String productCode) {
        String q = StringUtils.hasText(productQuery) ? productQuery : productCode;
        String like = "%" + escapeLike(q) + "%";
        String codeLike = StringUtils.hasText(productCode) ? "%" + escapeLike(productCode) + "%" : like;
        return jdbc.queryForList("""
                SELECT id, subject_name, subject_key, fact_type, fact_key, factor_json, value_json, status, active, created_at
                FROM rag_canonical_fact
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                  AND fact_type = 'PRICE'
                  AND (
                        subject_name ILIKE :q ESCAPE '\\'
                     OR subject_key ILIKE :q ESCAPE '\\'
                     OR factor_json::text ILIKE :q ESCAPE '\\'
                     OR value_json::text ILIKE :q ESCAPE '\\'
                     OR factor_json::text ILIKE :code ESCAPE '\\'
                     OR value_json::text ILIKE :code ESCAPE '\\'
                  )
                ORDER BY created_at DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("q", like)
                .addValue("code", codeLike)
                .addValue("limit", MAX_PRICE_ROWS));
    }

    private Map<String, Object> interpretWithGpt(UUID projectId, UUID versionId, UUID sessionId, String sourceScope, String message, Map<String, Object> workingMemory) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("projectId", projectId);
        user.put("versionId", versionId);
        user.put("sessionId", sessionId);
        user.put("sourceScope", sourceScope);
        user.put("message", message);
        user.put("workingMemory", workingMemory == null ? Map.of() : workingMemory);
        user.put("policy", Map.of(
                "doNotLimitQuestionTypes", true,
                "doNotLimitPriceFactors", true,
                "meaningMustBeInterpretedByGptFirst", true,
                "factorSynonyms", Map.of(
                        "W", List.of("W", "폭", "넓이", "너비", "width"),
                        "H", List.of("H", "높이", "height"),
                        "D", List.of("D", "깊이", "depth"),
                        "COLOR", List.of("색상", "색", "컬러", "color"),
                        "THICKNESS", List.of("두께", "T", "thickness")
                )
        ));
        try {
            String raw = openAiClient.responseJsonSchema(systemPrompt(), RagJsonUtils.toJson(objectMapper, user),
                    "hiddenauto_canonical_nl_interpretation", interpretationSchema(), true);
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            parsed.put("_gptParsed", true);
            parsed.put("_workingMemory", workingMemory == null ? Map.of() : workingMemory);
            return parsed;
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("intent", looksLikePriceQuestion(message) ? "PRICE_QUOTE" : "OTHER");
            String fallbackProduct = extractLikelyProductName(message);
            if (!StringUtils.hasText(fallbackProduct) && workingMemory != null) {
                fallbackProduct = text(workingMemory.get("activeSubject"), "");
            }
            String fallbackCode = extractProductCode(message);
            if (!StringUtils.hasText(fallbackCode) && workingMemory != null) {
                fallbackCode = text(workingMemory.get("activeProductCode"), "");
            }
            fallback.put("productName", fallbackProduct);
            fallback.put("productCode", fallbackCode);
            Map<String, Object> factors = new LinkedHashMap<>();
            BigDecimal w = extractWidthFromMessage(message);
            if (w == null && workingMemory != null && "W".equalsIgnoreCase(text(workingMemory.get("activeFactor"), ""))) {
                w = extractAnyReasonableDimension(message);
            }
            if (w != null) factors.put("W", w);
            fallback.put("requestedFactors", factors);
            fallback.put("_workingMemory", workingMemory == null ? Map.of() : workingMemory);
            fallback.put("_gptParsed", false);
            fallback.put("_gptError", e.getMessage());
            return fallback;
        }
    }

    private String systemPrompt() {
        return """
                당신은 HiddenBATHAuto 정본 데이터 가격/발주 자연어 해석기입니다.
                Java는 사용자의 말 종류와 가격요소를 enum으로 제한하지 않습니다. 반드시 당신이 먼저 의미를 해석하십시오.

                해야 할 일:
                1. 사용자 문장이 가격/견적/가능여부/발주질문/학습변경 중 무엇인지 판단합니다.
                2. 가격 또는 가능여부 질문이면 intent를 PRICE_QUOTE 또는 PRICE_VALIDATION으로 둡니다.
                3. 제품명, 제품코드, 사용자가 말한 모든 factor를 requestedFactors에 넣습니다.
                3-1. 현재 문장이 "1350이면?", "그럼 1500은?", "HC도 돼?"처럼 생략형이면 workingMemory의 activeSubject, activeProductCode, activeFactor, lastRuleKey를 활용해 문맥을 복원하십시오.
                4. 넓이/너비/폭/W/width는 같은 의미의 W factor입니다.
                5. 두께/자재/색상/옵션 등 새로운 요소가 나와도 버리지 말고 requestedFactors에 그대로 넣습니다.
                6. 단가변경/단종/색상불가/옵션추가/발주프로세스 학습은 OTHER로 넘겨도 됩니다. 이 서비스는 가격 계산 전용입니다.
                7. 모르는 값은 추측하지 않습니다.

                JSON만 반환하십시오.
                """;
    }

    private Map<String, Object> interpretationSchema() {
        Map<String, Object> string = Map.of("type", "string");
        Map<String, Object> number = Map.of("type", "number");
        Map<String, Object> bool = Map.of("type", "boolean");
        Map<String, Object> anyValue = Map.of("anyOf", List.of(string, number, bool, Map.of("type", "null")));
        Map<String, Object> factors = new LinkedHashMap<>();
        factors.put("type", "object");
        factors.put("additionalProperties", anyValue);
        return object(Map.of(
                "intent", string,
                "confidence", number,
                "productName", string,
                "productCode", string,
                "requestedFactors", factors,
                "missingInfo", array(string),
                "notes", string
        ), List.of("intent", "confidence", "productName", "productCode", "requestedFactors", "missingInfo", "notes"));
    }

    private Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> array(Map<String, Object> item) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", item);
        return schema;
    }

    private boolean isWidthSurchargeRule(Map<String, Object> factor, Map<String, Object> value) {
        String dimension = normalize(firstText(factor.get("dimension"), factor.get("dimensionName"), value.get("dimension")));
        boolean dimensionMatches = dimension.contains("w") || dimension.contains("폭") || dimension.contains("넓") || dimension.contains("너비") || dimension.contains("width");
        boolean hasBounds = factor.containsKey("maxWidth") || factor.containsKey("minWidth") || value.toString().contains("requestedW");
        boolean hasStep = value.containsKey("surchargeStepWidth") || value.containsKey("surchargePerStep") || value.toString().contains("basisWidth");
        return dimensionMatches && hasBounds && hasStep;
    }

    private int scoreRule(Map<String, Object> row, String productQuery, String productCode) {
        String haystack = normalize(String.join(" ",
                text(row.get("subject_name"), ""),
                text(row.get("subject_key"), ""),
                String.valueOf(row.get("factor_json")),
                String.valueOf(row.get("value_json"))));
        int score = 0;
        if (StringUtils.hasText(productQuery) && haystack.contains(normalize(productQuery))) score += 100;
        if (StringUtils.hasText(productCode) && haystack.contains(normalize(productCode))) score += 100;
        if (haystack.contains("w_custom_width_surcharge")) score += 20;
        return score;
    }

    private boolean containsAnyWidthSignal(String message, Map<String, Object> factors) {
        if (WIDTH_PATTERN.matcher(message).find()) return true;
        for (String key : factors.keySet()) {
            String k = normalize(key);
            if (k.equals("w") || k.contains("넓") || k.contains("너비") || k.contains("폭") || k.contains("width")) return true;
        }
        return false;
    }

    private void enrichFactorsFromMessage(Map<String, Object> factors, String message, Map<String, Object> workingMemory) {
        if (!factors.containsKey("W")) {
            BigDecimal w = extractWidthFromMessage(message);
            if (w == null && workingMemory != null && "W".equalsIgnoreCase(text(workingMemory.get("activeFactor"), ""))) {
                w = extractAnyReasonableDimension(message);
            }
            if (w != null) factors.put("W", w);
        }
    }

    private BigDecimal extractWidthFromMessage(String message) {
        Matcher explicit = WIDTH_PATTERN.matcher(message == null ? "" : message);
        if (explicit.find()) {
            return new BigDecimal(explicit.group(1));
        }
        // 가격 질문에서 제품명 뒤에 "넓이 1550"처럼 들어오지 않고 "1550이면"만 남는 경우의 보조 추출입니다.
        if (looksLikePriceQuestion(message)) {
            Matcher any = NUMBER_PATTERN.matcher(message == null ? "" : message);
            BigDecimal lastReasonableDimension = null;
            while (any.find()) {
                BigDecimal n = new BigDecimal(any.group(1));
                if (n.compareTo(new BigDecimal("100")) >= 0 && n.compareTo(new BigDecimal("5000")) <= 0) {
                    lastReasonableDimension = n;
                }
            }
            return lastReasonableDimension;
        }
        return null;
    }

    private BigDecimal extractAnyReasonableDimension(String message) {
        Matcher any = NUMBER_PATTERN.matcher(message == null ? "" : message);
        BigDecimal last = null;
        while (any.find()) {
            BigDecimal n = new BigDecimal(any.group(1));
            if (n.compareTo(new BigDecimal("100")) >= 0 && n.compareTo(new BigDecimal("5000")) <= 0) {
                last = n;
            }
        }
        return last;
    }

    private boolean looksLikePriceQuestion(String message) {
        String m = normalize(message);
        return m.contains("얼마") || m.contains("가격") || m.contains("견적") || m.contains("계산") || m.contains("비용");
    }

    private String extractLikelyProductName(String message) {
        String m = message == null ? "" : message;
        int idx = m.indexOf("넓이");
        if (idx < 0) idx = m.indexOf("W");
        if (idx < 0) idx = m.indexOf("폭");
        if (idx > 0) return m.substring(0, idx).trim();
        return m.replaceAll("얼마.*$", "").replaceAll("가격.*$", "").replaceAll("견적.*$", "").trim();
    }

    private String extractProductCode(String message) {
        Matcher m = Pattern.compile("HD-[0-9A-Za-z]+", Pattern.CASE_INSENSITIVE).matcher(message == null ? "" : message);
        return m.find() ? m.group().toUpperCase(Locale.ROOT) : "";
    }

    private BigDecimal numberFromFactor(Map<String, Object> factors, String... keys) {
        for (String key : keys) {
            for (Map.Entry<String, Object> e : factors.entrySet()) {
                if (normalize(e.getKey()).equals(normalize(key))) {
                    BigDecimal n = number(e.getValue(), null);
                    if (n != null) return n;
                }
            }
        }
        return null;
    }

    private BigDecimal ceilToStep(BigDecimal value, BigDecimal step) {
        if (step == null || step.compareTo(BigDecimal.ZERO) <= 0) return value;
        return value.divide(step, 0, RoundingMode.CEILING).multiply(step);
    }

    private BigDecimal surcharge(BigDecimal baseWidth, BigDecimal basisWidth, BigDecimal stepWidth, BigDecimal surchargePerStep) {
        BigDecimal steps = basisWidth.subtract(baseWidth).divide(stepWidth, 0, RoundingMode.CEILING);
        if (steps.compareTo(BigDecimal.ZERO) < 0) steps = BigDecimal.ZERO;
        return steps.multiply(surchargePerStep);
    }

    private Map<String, Object> priceAnswer(boolean handled, String answer, Map<String, Object> interpretation, Map<String, Object> meta) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("handled", handled);
        response.put("intentType", "CANONICAL_DYNAMIC_PRICE_QUOTE");
        response.put("actionStatus", handled ? "HANDLED" : "NEED_CLARIFICATION");
        response.put("confidence", interpretation.getOrDefault("confidence", new BigDecimal("0.9000")));
        response.put("answer", answer);
        response.put("interpretation", interpretation);
        response.put("quote", meta);
        response.put("saveStatus", "지식 저장: 조회 응답");
        response.put("saveMessage", "가격 질문으로 판정되어 새 지식 저장 없이 정본 가격 규칙으로 계산했습니다.");
        response.put("memory", Map.of(
                "status", "NO_KNOWLEDGE_CHANGE",
                "saveLabel", "지식 저장: 조회 응답",
                "message", response.get("saveMessage")
        ));
        return response;
    }

    private Map<String, Object> notHandled(String reason) {
        return Map.of("success", true, "handled", false, "reason", reason);
    }

    private void logParse(UUID projectId, UUID versionId, UUID sessionId, String sourceScope, String message,
                          Map<String, Object> interpretation, Map<String, Object> result) {
        try {
            jdbc.update("""
                    INSERT INTO rag_canonical_nl_parse_log(
                        id, project_id, version_id, session_id, source_scope, user_message,
                        interpretation_json, result_json, created_at
                    ) VALUES (
                        :id, :projectId, :versionId, :sessionId, :sourceScope, :userMessage,
                        CAST(:interpretationJson AS jsonb), CAST(:resultJson AS jsonb), :createdAt
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("sessionId", sessionId)
                    .addValue("sourceScope", sourceScope == null ? "API" : sourceScope)
                    .addValue("userMessage", message)
                    .addValue("interpretationJson", RagJsonUtils.toJson(objectMapper, interpretation))
                    .addValue("resultJson", RagJsonUtils.toJson(objectMapper, result))
                    .addValue("createdAt", OffsetDateTime.now()));
        } catch (Exception ignored) {
            // 로그 실패가 사용자 응답을 막으면 안 됩니다.
        }
    }

    private Map<String, Object> safeRule(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("subjectName", row.get("subject_name"));
        m.put("factType", row.get("fact_type"));
        m.put("factKey", row.get("fact_key"));
        m.put("factorJson", jsonMap(row.get("factor_json")));
        m.put("valueJson", jsonMap(row.get("value_json")));
        return m;
    }

    private Map<String, Object> compactFact(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("subjectName", row.get("subject_name"));
        m.put("factType", row.get("fact_type"));
        m.put("factKey", row.get("fact_key"));
        m.put("factorJson", jsonMap(row.get("factor_json")));
        m.put("valueJson", jsonMap(row.get("value_json")));
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonMap(Object value) {
        if (value == null) return new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) result.put(String.valueOf(e.getKey()), e.getValue());
            return result;
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) result.put(String.valueOf(e.getKey()), e.getValue());
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String s = text(value, "").trim();
            if (StringUtils.hasText(s)) return s;
        }
        return "";
    }

    private BigDecimal number(Object value, BigDecimal fallback) {
        if (value == null) return fallback;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return new BigDecimal(String.valueOf(n));
        String s = String.valueOf(value).replaceAll("[^0-9.\\-]", "");
        if (!StringUtils.hasText(s)) return fallback;
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return fallback;
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return 0; }
    }

    private String money(BigDecimal value) {
        if (value == null) return "-";
        return String.format("%,.0f원", value.setScale(0, RoundingMode.HALF_UP));
    }

    private String strip(BigDecimal value) {
        if (value == null) return "-";
        return value.stripTrailingZeros().toPlainString();
    }

    private String escapeLike(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record BasePriceSelection(boolean resolved, BigDecimal basePrice, String message, Map<String, Object> meta) {
        static BasePriceSelection resolved(BigDecimal basePrice, Map<String, Object> meta) {
            return new BasePriceSelection(true, basePrice, "", meta == null ? Map.of() : meta);
        }
        static BasePriceSelection unresolved(String message, Map<String, Object> meta) {
            return new BasePriceSelection(false, null, message, meta == null ? Map.of() : meta);
        }
    }
}
