package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagStructuredPricingRuleService {

    private static final Pattern COLOR_PRICE_PATTERN = Pattern.compile("([A-Z]{1,10})\\s*(?:는|은|=|:)\\s*([0-9]+(?:\\.[0-9]+)?)(만원|천원|원)?");
    private static final Pattern BASE_WIDTH_PATTERN = Pattern.compile("(?:넓이|폭|W|w|가로|사이즈)\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:기준)?");
    private static final Pattern STEP_PATTERN = Pattern.compile("(?:넓이|폭|W|w|가로)?\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:이|가)?\\s*(?:올라가|증가|커지|늘어나|추가)\\s*(?:면|할때|할 때)?\\s*([0-9]+(?:\\.[0-9]+)?)(만원|천원|원)?");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagStructuredPricingRuleService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                           ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> saveRulesFromPlan(UUID projectId, UUID versionId, Map<String, Object> plan, String sourceMessage) {
        Map<String, Object> pricingRule = childMap(plan, "pricingRule");
        String entityKey = firstNonBlank(text(pricingRule.get("entityKey")), childText(plan, "primaryEntity", "name"), extractEntity(sourceMessage));
        BigDecimal baseWidth = decimal(pricingRule.get("baseWidth"), extractBaseWidth(sourceMessage));
        BigDecimal widthStep = decimal(pricingRule.get("widthStep"), extractWidthStep(sourceMessage));
        BigDecimal widthStepPrice = decimal(pricingRule.get("widthStepPrice"), extractWidthStepPrice(sourceMessage));
        String reason = firstNonBlank(text(pricingRule.get("reason")), sourceMessage);

        List<Map<String, Object>> optionPrices = mapList(pricingRule.get("optionPrices"));
        if (optionPrices.isEmpty()) optionPrices = extractOptionPrices(sourceMessage);

        List<Map<String, Object>> saved = new ArrayList<>();
        for (Map<String, Object> item : optionPrices) {
            String optionField = firstNonBlank(text(item.get("optionField")), "색상");
            String optionValue = firstNonBlank(text(item.get("optionValue")), text(item.get("color")));
            BigDecimal basePrice = decimal(item.get("basePrice"), null);
            if (!StringUtils.hasText(entityKey) || !StringUtils.hasText(optionValue) || basePrice == null) continue;
            saved.add(upsertRule(projectId, versionId, entityKey, optionField, optionValue, baseWidth, basePrice, widthStep, widthStepPrice, reason, sourceMessage, plan));
        }
        return saved;
    }

    public Map<String, Object> calculatePrice(UUID projectId, UUID versionId, Map<String, Object> plan, String userMessage) {
        Map<String, Object> priceQuery = childMap(plan, "priceQuery");
        String entityKey = firstNonBlank(text(priceQuery.get("entityKey")), childText(plan, "primaryEntity", "name"), extractEntity(userMessage));
        String optionField = firstNonBlank(text(priceQuery.get("optionField")), "색상");
        String optionValue = firstNonBlank(text(priceQuery.get("optionValue")), extractColor(userMessage));
        BigDecimal width = decimal(priceQuery.get("width"), extractWidth(userMessage));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityKey", entityKey);
        result.put("optionField", optionField);
        result.put("optionValue", optionValue);
        result.put("width", width);

        if (!StringUtils.hasText(entityKey) || !StringUtils.hasText(optionValue)) {
            result.put("calculated", false);
            result.put("reason", "가격 계산에 필요한 제품명 또는 옵션값이 부족합니다.");
            return result;
        }

        List<Map<String, Object>> rules = findRules(projectId, versionId, entityKey, optionField, optionValue);
        result.put("rules", rules);
        if (rules.isEmpty()) {
            Map<String, Object> dialogPrice = calculateDialogPricingRule(projectId, versionId, entityKey, width);
            if (Boolean.TRUE.equals(dialogPrice.get("calculated"))) {
                result.putAll(dialogPrice);
                return result;
            }
            result.put("calculated", false);
            result.put("reason", "해당 제품/옵션의 가격 규칙이 저장되어 있지 않습니다.");
            result.put("dialogPricingResult", dialogPrice);
            return result;
        }

        Map<String, Object> rule = rules.get(0);
        BigDecimal basePrice = decimal(rule.get("base_price"), null);
        BigDecimal baseWidth = decimal(rule.get("base_width"), null);
        BigDecimal widthStep = decimal(rule.get("width_step"), null);
        BigDecimal widthStepPrice = decimal(rule.get("width_step_price"), BigDecimal.ZERO);
        BigDecimal finalPrice = basePrice;
        BigDecimal widthExtra = BigDecimal.ZERO;
        BigDecimal stepCount = BigDecimal.ZERO;

        if (basePrice != null && width != null && baseWidth != null && widthStep != null && widthStep.compareTo(BigDecimal.ZERO) > 0 && widthStepPrice != null) {
            BigDecimal diff = width.subtract(baseWidth);
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                stepCount = diff.divide(widthStep, 0, RoundingMode.CEILING);
                widthExtra = stepCount.multiply(widthStepPrice);
                finalPrice = basePrice.add(widthExtra);
            }
        }

        result.put("calculated", true);
        result.put("basePrice", basePrice);
        result.put("baseWidth", baseWidth);
        result.put("widthStep", widthStep);
        result.put("widthStepPrice", widthStepPrice);
        result.put("stepCount", stepCount);
        result.put("widthExtra", widthExtra);
        result.put("finalPrice", finalPrice);
        result.put("currency", rule.getOrDefault("currency", "KRW"));
        result.put("formulaText", "기준가 " + money(basePrice) + " + 폭 추가 " + money(widthExtra) + " = " + money(finalPrice));
        return result;
    }

    private Map<String, Object> calculateDialogPricingRule(UUID projectId, UUID versionId, String entityKey, BigDecimal width) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> dialogRules = findDialogPricingRules(projectId, versionId, entityKey);
        result.put("dialogRules", dialogRules);
        if (dialogRules.isEmpty()) {
            result.put("calculated", false);
            result.put("reason", "대화형 가격식 규칙도 없습니다.");
            return result;
        }
        Map<String, Object> rule = dialogRules.get(0);
        Map<String, Object> pricing = jsonMap(rule.get("pricing_json"));
        BigDecimal basePrice = decimal(pricing.get("basePrice"), null);
        BigDecimal stepSize = decimal(pricing.get("stepSize"), null);
        BigDecimal stepPrice = decimal(pricing.get("stepPrice"), BigDecimal.ZERO);
        if (basePrice == null) {
            result.put("calculated", false);
            result.put("reason", "대화형 가격식에 basePrice가 없습니다.");
            return result;
        }
        BigDecimal finalPrice = basePrice;
        BigDecimal stepCount = BigDecimal.ZERO;
        BigDecimal extra = BigDecimal.ZERO;
        if (width != null && stepSize != null && stepSize.compareTo(BigDecimal.ZERO) > 0 && stepPrice != null) {
            // dialog pricing rule은 기준값을 명시하지 못한 경우 0부터 단계 계산하지 않고 basePrice만 사용합니다.
            BigDecimal baseWidth = decimal(pricing.get("baseWidth"), null);
            if (baseWidth != null && width.compareTo(baseWidth) > 0) {
                stepCount = width.subtract(baseWidth).divide(stepSize, 0, RoundingMode.CEILING);
                extra = stepCount.multiply(stepPrice);
                finalPrice = basePrice.add(extra);
            }
        }
        result.put("calculated", true);
        result.put("calculationSource", "DIALOG_PRICING_RULE");
        result.put("basePrice", basePrice);
        result.put("stepCount", stepCount);
        result.put("widthExtra", extra);
        result.put("finalPrice", finalPrice);
        result.put("formulaText", firstNonBlank(text(pricing.get("formulaText")), "대화형 가격식 기준가 " + money(basePrice) + " + 추가 " + money(extra) + " = " + money(finalPrice)));
        return result;
    }

    private List<Map<String, Object>> findDialogPricingRules(UUID projectId, UUID versionId, String entityKey) {
        String sql = """
                SELECT id, rule_key, rule_type, entity_key, step_key, field_name,
                       pricing_json::text AS pricing_json, condition_json::text AS condition_json, action_json::text AS action_json,
                       confidence, source_message, updated_at
                FROM rag_dialog_rule
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                  AND rule_type IN ('PRICING_FORMULA', 'PRICE_ADJUSTMENT')
                  AND (:entityKey = '' OR entity_key = :entityKey OR entity_key = '')
                ORDER BY CASE WHEN entity_key = :entityKey THEN 0 ELSE 1 END, priority ASC, updated_at DESC
                LIMIT 20
                """;
        try {
            return jdbc.queryForList(sql, new MapSqlParameterSource()
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("entityKey", StringUtils.hasText(entityKey) ? entityKey : ""));
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    public List<Map<String, Object>> findRules(UUID projectId, UUID versionId, String entityKey, String optionField, String optionValue) {
        String sql = """
                SELECT *
                FROM rag_structured_pricing_rule
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                  AND entity_key = :entityKey
                  AND option_field = :optionField
                  AND option_value = :optionValue
                ORDER BY updated_at DESC
                LIMIT 10
                """;
        try {
            return jdbc.queryForList(sql, new MapSqlParameterSource()
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("entityKey", entityKey)
                    .addValue("optionField", optionField)
                    .addValue("optionValue", optionValue));
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private Map<String, Object> upsertRule(UUID projectId,
                                           UUID versionId,
                                           String entityKey,
                                           String optionField,
                                           String optionValue,
                                           BigDecimal baseWidth,
                                           BigDecimal basePrice,
                                           BigDecimal widthStep,
                                           BigDecimal widthStepPrice,
                                           String reason,
                                           String sourceMessage,
                                           Map<String, Object> plan) {
        String sql = """
                INSERT INTO rag_structured_pricing_rule(
                    id, project_id, version_id, entity_type, entity_key,
                    option_field, option_value,
                    base_width, base_price, width_step, width_step_price,
                    currency, reason, source_message, plan_json, confidence, active,
                    created_at, updated_at
                ) VALUES (
                    :id, :projectId, :versionId, 'PRODUCT', :entityKey,
                    :optionField, :optionValue,
                    :baseWidth, :basePrice, :widthStep, :widthStepPrice,
                    'KRW', :reason, :sourceMessage, CAST(:planJson AS jsonb), :confidence, true,
                    now(), now()
                )
                ON CONFLICT(project_id, version_id, entity_type, entity_key, option_field, option_value)
                DO UPDATE SET
                    base_width = EXCLUDED.base_width,
                    base_price = EXCLUDED.base_price,
                    width_step = EXCLUDED.width_step,
                    width_step_price = EXCLUDED.width_step_price,
                    reason = EXCLUDED.reason,
                    source_message = EXCLUDED.source_message,
                    plan_json = EXCLUDED.plan_json,
                    confidence = EXCLUDED.confidence,
                    active = true,
                    updated_at = now()
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("entityKey", entityKey)
                .addValue("optionField", optionField)
                .addValue("optionValue", optionValue)
                .addValue("baseWidth", baseWidth)
                .addValue("basePrice", basePrice)
                .addValue("widthStep", widthStep)
                .addValue("widthStepPrice", widthStepPrice)
                .addValue("reason", reason)
                .addValue("sourceMessage", sourceMessage)
                .addValue("planJson", RagJsonUtils.toJson(objectMapper, plan))
                .addValue("confidence", decimal(plan.get("confidence"), new BigDecimal("0.9000"))));
    }

    private String extractEntity(String message) {
        if (!StringUtils.hasText(message)) return "";
        String cleaned = message.replaceAll("(지금|올라가있는|정보|규격|제품|의|는|은|이야|코드|가격|금액|얼마|사이즈|넓이|기준|반영|해줘|이걸|색상|컬러|[0-9]+|만원|천원|원|,|\\(|\\))", " ").trim();
        String[] parts = cleaned.split("\\s+");
        for (String p : parts) {
            if (p.length() >= 2 && !p.matches("[A-Z]+")) return p;
        }
        return "";
    }

    private String extractColor(String message) {
        if (!StringUtils.hasText(message)) return "";
        Matcher m = Pattern.compile("(?<![A-Za-z0-9가-힣])([A-Z]{1,10})(?![A-Za-z0-9가-힣])").matcher(message);
        return m.find() ? m.group(1) : "";
    }

    private BigDecimal extractBaseWidth(String message) {
        Matcher m = BASE_WIDTH_PATTERN.matcher(message == null ? "" : message);
        return m.find() ? decimal(m.group(1), null) : null;
    }

    private BigDecimal extractWidth(String message) {
        Matcher m = Pattern.compile("(?:사이즈|넓이|폭|W|w|가로)\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(message == null ? "" : message);
        if (m.find()) return decimal(m.group(1), null);
        Matcher any = Pattern.compile("(?<![0-9])([0-9]{3,4})(?![0-9])").matcher(message == null ? "" : message);
        return any.find() ? decimal(any.group(1), null) : null;
    }

    private BigDecimal extractWidthStep(String message) {
        Matcher m = STEP_PATTERN.matcher(message == null ? "" : message);
        return m.find() ? decimal(m.group(1), null) : null;
    }

    private BigDecimal extractWidthStepPrice(String message) {
        Matcher m = STEP_PATTERN.matcher(message == null ? "" : message);
        if (!m.find()) return null;
        return moneyToNumber(m.group(2), m.group(3));
    }

    private List<Map<String, Object>> extractOptionPrices(String message) {
        List<Map<String, Object>> result = new ArrayList<>();
        Matcher m = COLOR_PRICE_PATTERN.matcher(message == null ? "" : message);
        while (m.find()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("optionField", "색상");
            item.put("optionValue", m.group(1));
            item.put("basePrice", moneyToNumber(m.group(2), m.group(3)));
            result.add(item);
        }
        return result;
    }

    private BigDecimal moneyToNumber(String number, String unit) {
        BigDecimal value = decimal(number, BigDecimal.ZERO);
        if (unit == null) return value;
        return switch (unit) {
            case "만원" -> value.multiply(new BigDecimal("10000"));
            case "천원" -> value.multiply(new BigDecimal("1000"));
            default -> value;
        };
    }

    private String money(BigDecimal value) {
        if (value == null) return "0원";
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString() + "원";
    }

    private Map<String, Object> jsonMap(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) if (e.getKey() != null) r.put(String.valueOf(e.getKey()), e.getValue());
            return r;
        }
        if (value instanceof String s && StringUtils.hasText(s) && s.trim().startsWith("{")) {
            try {
                return RagJsonUtils.toMap(objectMapper, s);
            } catch (Exception ignored) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> childMap(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) if (e.getKey() != null) r.put(String.valueOf(e.getKey()), e.getValue());
            return r;
        }
        return new LinkedHashMap<>();
    }

    private List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) if (e.getKey() != null) r.put(String.valueOf(e.getKey()), e.getValue());
                    result.add(r);
                }
            }
        }
        return result;
    }

    private String childText(Map<String, Object> map, String child, String key) {
        return text(childMap(map, child).get(key));
    }

    private String firstNonBlank(String... values) {
        for (String v : values) if (StringUtils.hasText(v)) return v;
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return value == null || !StringUtils.hasText(String.valueOf(value)) ? fallback : new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }
}
