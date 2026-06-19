package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RagSemanticSchemaFactory {

    private RagSemanticSchemaFactory() {}

    public static Map<String, Object> semanticPlanSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("intentType", enumString(List.of(
                "ASK_PRODUCT_AVAILABILITY",
                "ASK_PRODUCT_PRICE",
                "ASK_KNOWLEDGE_SUMMARY",
                "UPDATE_PRODUCT_AVAILABILITY_RULE",
                "UPDATE_PRODUCT_PRICING_RULE",
                "LEARN_STRUCTURED_FILE",
                "LEARN_DIALOG_RULES",
                "LINK_ASSET_TO_ENTITY",
                "ORDER_CONVERSATION",
                "GENERAL_CHAT",
                "OUT_OF_DOMAIN",
                "UNRELATED_INPUT",
                "RESET_KNOWLEDGE",
                "UNKNOWN"
        )));
        properties.put("confidence", number(0, 1));
        properties.put("primaryEntity", obj(linkedMap(
                "entityType", enumString(List.of("PRODUCT", "CATEGORY", "OPTION", "COLOR", "SIZE", "FILE", "UNKNOWN")),
                "name", string(),
                "normalizedName", string()
        ), List.of("entityType", "name", "normalizedName")));
        properties.put("relatedEntities", array(obj(linkedMap(
                "entityType", enumString(List.of("PRODUCT", "CATEGORY", "OPTION", "COLOR", "SIZE", "FILE", "UNKNOWN")),
                "name", string(),
                "relationship", enumString(List.of("SIMILAR_NAME", "VARIANT", "CATEGORY_MEMBER", "MENTIONED", "AMBIGUOUS", "UNKNOWN")),
                "includeInMainAnswer", bool(),
                "reason", string()
        ), List.of("entityType", "name", "relationship", "includeInMainAnswer", "reason"))));
        properties.put("requestedFields", array(enumString(List.of("색상", "사이즈", "가격", "규격", "옵션", "이미지", "설명", "전체", "질문흐름", "검증", "조건", "기타"))));
        properties.put("retrievalPlan", obj(linkedMap(
                "exactFirst", bool(),
                "includeRelatedAsSuggestion", bool(),
                "includeRelatedInMainAnswer", bool(),
                "rowLimit", integer(1, 800),
                "queryScope", enumString(List.of("EXACT_ENTITY", "RELATED_ENTITIES", "ALL_PRODUCTS", "ALL_KNOWLEDGE", "PRICING_RULES", "DIALOG_RULES", "ASSETS", "UNKNOWN")),
                "needsStructuredRows", bool(),
                "needsOverrideRules", bool(),
                "needsPricingRules", bool(),
                "needsDialogRules", bool(),
                "needsAssets", bool(),
                "needsKnowledgeNodes", bool()
        ), List.of("exactFirst", "includeRelatedAsSuggestion", "includeRelatedInMainAnswer", "rowLimit", "queryScope", "needsStructuredRows", "needsOverrideRules", "needsPricingRules", "needsDialogRules", "needsAssets", "needsKnowledgeNodes")));
        properties.put("updateRule", obj(linkedMap(
                "entityType", enumString(List.of("PRODUCT", "CATEGORY", "OPTION", "COLOR", "SIZE", "UNKNOWN")),
                "entityKey", string(),
                "fieldName", enumString(List.of("색상", "사이즈", "가격", "규격", "옵션", "제품", "조건", "기타")),
                "ruleType", enumString(List.of("DISALLOW", "ALLOW", "REPLACE", "DELETE", "DEPRECATE", "NOTE", "NONE")),
                "ruleValue", string(),
                "replacementValue", string(),
                "reason", string(),
                "effectiveMeaning", string()
        ), List.of("entityType", "entityKey", "fieldName", "ruleType", "ruleValue", "replacementValue", "reason", "effectiveMeaning")));
        properties.put("pricingRule", obj(linkedMap(
                "entityKey", string(),
                "optionField", enumString(List.of("색상", "사이즈", "규격", "옵션", "기타")),
                "baseWidth", number(0, 100000),
                "baseHeight", number(0, 100000),
                "baseDepth", number(0, 100000),
                "widthStep", number(0, 100000),
                "widthStepPrice", number(0, 100000000),
                "formulaText", string(),
                "optionPrices", array(obj(linkedMap(
                        "optionField", enumString(List.of("색상", "사이즈", "규격", "옵션", "기타")),
                        "optionValue", string(),
                        "basePrice", number(0, 100000000)
                ), List.of("optionField", "optionValue", "basePrice"))),
                "reason", string()
        ), List.of("entityKey", "optionField", "baseWidth", "baseHeight", "baseDepth", "widthStep", "widthStepPrice", "formulaText", "optionPrices", "reason")));
        properties.put("priceQuery", obj(linkedMap(
                "entityKey", string(),
                "optionField", enumString(List.of("색상", "사이즈", "규격", "옵션", "기타")),
                "optionValue", string(),
                "width", number(0, 100000),
                "height", number(0, 100000),
                "depth", number(0, 100000),
                "quantity", number(0, 100000)
        ), List.of("entityKey", "optionField", "optionValue", "width", "height", "depth", "quantity")));
        properties.put("dialogRules", array(obj(linkedMap(
                "ruleType", enumString(List.of("QUESTION_FLOW", "NEXT_QUESTION", "CONDITION", "VALIDATION", "OPTION_AVAILABILITY", "PRICING_FORMULA", "PRICE_ADJUSTMENT", "ORDER_STEP", "CLARIFICATION_RULE")),
                "ruleKey", string(),
                "entityType", enumString(List.of("PRODUCT", "CATEGORY", "OPTION", "ORDER", "UNKNOWN")),
                "entityKey", string(),
                "stepKey", string(),
                "fieldName", string(),
                "priority", integer(1, 10000),
                "condition", obj(linkedMap(
                        "whenField", string(),
                        "operator", enumString(List.of("ALWAYS", "EQUALS", "NOT_EQUALS", "IN", "NOT_IN", "GT", "GTE", "LT", "LTE", "CONTAINS", "EXPRESSION")),
                        "value", string(),
                        "expressionText", string()
                ), List.of("whenField", "operator", "value", "expressionText")),
                "action", obj(linkedMap(
                        "actionType", enumString(List.of("ASK", "SKIP", "ALLOW", "DISALLOW", "SET_VALUE", "CALCULATE", "ADD_PRICE", "ASK_CLARIFICATION", "END")),
                        "questionText", string(),
                        "answerType", enumString(List.of("TEXT", "NUMBER", "BOOLEAN", "SELECT_ONE", "SELECT_MANY", "SIZE", "PRICE", "DATE", "UNKNOWN")),
                        "allowedValuesText", string(),
                        "nextStepKey", string(),
                        "message", string()
                ), List.of("actionType", "questionText", "answerType", "allowedValuesText", "nextStepKey", "message")),
                "validation", obj(linkedMap(
                        "required", bool(),
                        "minValue", number(-100000000, 100000000),
                        "maxValue", number(-100000000, 100000000),
                        "pattern", string(),
                        "errorMessage", string()
                ), List.of("required", "minValue", "maxValue", "pattern", "errorMessage")),
                "pricing", obj(linkedMap(
                        "formulaText", string(),
                        "basePrice", number(0, 1000000000),
                        "baseWidth", number(0, 100000),
                        "stepField", string(),
                        "stepSize", number(0, 1000000),
                        "stepPrice", number(-100000000, 100000000),
                        "roundingPolicy", string()
                ), List.of("formulaText", "basePrice", "baseWidth", "stepField", "stepSize", "stepPrice", "roundingPolicy")),
                "reason", string()
        ), List.of("ruleType", "ruleKey", "entityType", "entityKey", "stepKey", "fieldName", "priority", "condition", "action", "validation", "pricing", "reason"))));
        properties.put("storagePlan", obj(linkedMap(
                "storeAs", enumString(List.of("NONE", "STRUCTURED_OVERRIDE_RULE", "STRUCTURED_PRICING_RULE", "STRUCTURED_TABLE", "DIALOG_RULE", "QUESTION_FLOW", "PRICING_FORMULA", "ASSET_LINK", "KNOWLEDGE_NODE", "ORDER_STATE")),
                "alsoStoreAsKnowledgeNode", bool(),
                "affectsExistingRows", bool(),
                "applyAtQueryAndOrderValidation", bool(),
                "replacementPolicy", enumString(List.of("NONE", "REPLACE_SAME_ROLE", "MERGE", "APPEND", "ASK_CONFIRMATION"))
        ), List.of("storeAs", "alsoStoreAsKnowledgeNode", "affectsExistingRows", "applyAtQueryAndOrderValidation", "replacementPolicy")));
        properties.put("executionPlan", obj(linkedMap(
                "actionType", enumString(List.of("ANSWER_FROM_DB", "SAVE_DIALOG_RULES", "SAVE_STRUCTURED_RULE", "SAVE_PRICING_RULE", "SAVE_STRUCTURED_TABLE", "LINK_ASSET", "PASS_TO_LEARNING_JOB", "PASS_TO_CHAT_ENGINE", "ASK_CLARIFICATION", "CALCULATE_PRICE", "RESET_BLOCKED", "DIRECT_REPLY", "UNRELATED_INPUT_GUIDE", "NOOP")),
                "requiresDbLookup", bool(),
                "requiresGptAnswer", bool(),
                "requiresAsyncJob", bool(),
                "requiresUserConfirmation", bool(),
                "serverAction", string()
        ), List.of("actionType", "requiresDbLookup", "requiresGptAnswer", "requiresAsyncJob", "requiresUserConfirmation", "serverAction")));
        properties.put("directAnswer", string());
        properties.put("answerPolicy", obj(linkedMap(
                "mainAnswerFocus", string(),
                "mentionRelatedAsSuggestion", bool(),
                "askClarifyingQuestion", bool(),
                "tone", enumString(List.of("KOREAN_POLITE_BUSINESS", "KOREAN_SIMPLE", "INTERNAL_DEBUG")),
                "doNotPersist", bool()
        ), List.of("mainAnswerFocus", "mentionRelatedAsSuggestion", "askClarifyingQuestion", "tone", "doNotPersist")));
        properties.put("missingQuestions", array(string()));
        properties.put("reason", string());

        Map<String, Object> schema = obj(properties, List.of("intentType", "confidence", "primaryEntity", "relatedEntities", "requestedFields", "retrievalPlan", "updateRule", "pricingRule", "priceQuery", "dialogRules", "storagePlan", "executionPlan", "directAnswer", "answerPolicy", "missingQuestions", "reason"));
        schema.put("description", "HiddenBATHAuto RAG semantic plan. Must choose one exact intent and executable storage/retrieval/update plan.");
        return schema;
    }

    public static Map<String, Object> answerSchema() {
        Map<String, Object> schema = obj(Map.of(
                "answer", string(),
                "naturalSummary", string(),
                "usedRules", array(obj(Map.of(
                        "entityKey", string(),
                        "fieldName", string(),
                        "ruleType", string(),
                        "ruleValue", string(),
                        "reason", string()
                ), List.of("entityKey", "fieldName", "ruleType", "ruleValue", "reason"))),
                "relatedSuggestions", array(string()),
                "warnings", array(string())
        ), List.of("answer", "naturalSummary", "usedRules", "relatedSuggestions", "warnings"));
        schema.put("description", "Natural Korean answer composed from retrieved RAG data and semantic plan.");
        return schema;
    }

    private static Map<String, Object> obj(Map<String, Object> properties, List<String> required) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "object");
        map.put("properties", properties);
        map.put("required", required);
        map.put("additionalProperties", false);
        return map;
    }

    private static Map<String, Object> array(Map<String, Object> itemSchema) {
        return Map.of("type", "array", "items", itemSchema);
    }

    private static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> enumString(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    private static Map<String, Object> bool() {
        return Map.of("type", "boolean");
    }

    private static Map<String, Object> number(double min, double max) {
        return Map.of("type", "number");
    }

    private static Map<String, Object> integer(int min, int max) {
        return Map.of("type", "integer");
    }

    private static Map<String, Object> linkedMap(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("linkedMap requires even number of arguments");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
