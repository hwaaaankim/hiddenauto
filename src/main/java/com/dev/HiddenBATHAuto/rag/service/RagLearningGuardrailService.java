package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * GPT의 판단을 서버 저장 전에 검증하는 안전장치입니다.
 * 이 클래스는 '뇌'가 아니라 '반사신경/검문소'입니다.
 * 판단은 GPT가 하지만, DB 손상/잘못된 교체/실행 불가능한 JSON 저장은 서버가 막아야 합니다.
 */
@Service
public class RagLearningGuardrailService {

    private final ObjectMapper objectMapper;

    public RagLearningGuardrailService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> validateAndNormalize(Map<String, Object> analysis,
                                                    Map<String, Object> currentVersion,
                                                    boolean forceSave) {
        Map<String, Object> result = analysis == null ? new LinkedHashMap<>() : new LinkedHashMap<>(analysis);

        result.put("intent", normalizeIntent(RagJsonUtils.stringValue(result, "intent")));
        result.putIfAbsent("inputInterpretation", Map.of());
        result.putIfAbsent("materials", List.of());
        result.putIfAbsent("conflicts", List.of());
        result.putIfAbsent("clarificationQuestions", List.of());
        result.putIfAbsent("requiresUpload", false);
        result.putIfAbsent("requiresClarification", false);
        result.putIfAbsent("shouldPersist", false);
        result.putIfAbsent("answer", "");
        result.putIfAbsent("summary", stringOrCurrent(result, currentVersion, "summary"));
        result.put("processJson", normalizeProcessJson(valueOrCurrent(result, currentVersion, "processJson", "process_json")));
        result.put("pricingJson", normalizePricingJson(valueOrCurrent(result, currentVersion, "pricingJson", "pricing_json")));
        result.put("constraintsJson", normalizeConstraintsJson(valueOrCurrent(result, currentVersion, "constraintsJson", "constraints_json")));
        result.put("validationReportJson", normalizeValidationReport(result.get("validationReportJson")));

        List<String> guardrailWarnings = new ArrayList<>();
        List<String> guardrailQuestions = new ArrayList<>();

        normalizeMaterials(result, guardrailWarnings, guardrailQuestions);

        boolean hasConflicts = !RagJsonUtils.childList(result, "conflicts").isEmpty()
                || !RagJsonUtils.childList(RagJsonUtils.childMap(result, "validationReportJson"), "conflicts").isEmpty();
        boolean hasQuestions = !RagJsonUtils.childList(result, "clarificationQuestions").isEmpty() || !guardrailQuestions.isEmpty();
        boolean requiresClarification = RagJsonUtils.boolValue(result, "requiresClarification", false) || hasConflicts || hasQuestions;

        List<Object> mergedQuestions = new ArrayList<>(RagJsonUtils.childList(result, "clarificationQuestions"));
        mergedQuestions.addAll(guardrailQuestions);
        result.put("clarificationQuestions", mergedQuestions);

        Map<String, Object> validation = RagJsonUtils.childMap(result, "validationReportJson");
        List<Object> warnings = new ArrayList<>(RagJsonUtils.childList(validation, "warnings"));
        warnings.addAll(guardrailWarnings);
        validation.put("warnings", warnings);

        if (hasConflicts) validation.put("status", "CONFLICT");
        else if (requiresClarification) validation.put("status", "NEEDS_CLARIFICATION");
        else if ("ANSWER_FROM_KNOWLEDGE".equals(result.get("intent"))) validation.put("status", "ANSWER_ONLY");
        else validation.putIfAbsent("status", "OK");

        result.put("requiresClarification", requiresClarification);

        boolean hasPersistable = hasPersistablePayload(result);
        boolean shouldPersist = (RagJsonUtils.boolValue(result, "shouldPersist", false) || forceSave) && hasPersistable;

        if (requiresClarification || hasConflicts) shouldPersist = false;
        if ("ANSWER_FROM_KNOWLEDGE".equals(result.get("intent")) || "IGNORE_SMALLTALK".equals(result.get("intent"))) {
            shouldPersist = false;
        }
        result.put("shouldPersist", shouldPersist);
        validation.put("readyToPublish", shouldPersist && !requiresClarification);
        result.put("validationReportJson", validation);

        if (!StringUtils.hasText(RagJsonUtils.stringValue(result, "answer"))) {
            result.put("answer", fallbackAnswer(result));
        }
        if (!StringUtils.hasText(RagJsonUtils.stringValue(result, "knowledgeText")) && shouldPersist) {
            result.put("knowledgeText", buildKnowledgeText(result));
        }
        result.put("serverGuardrail", "RagLearningGuardrailService");
        result.put("serverGuardrailVersion", "20260612-gpt-brain-v1");
        return result;
    }

    private void normalizeMaterials(Map<String, Object> result,
                                    List<String> warnings,
                                    List<String> questions) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        Object raw = result.get("materials");
        if (raw instanceof List<?> list) {
            for (Object obj : list) {
                if (!(obj instanceof Map<?, ?> any)) continue;
                Map<String, Object> m = cast(any);
                String semanticRole = upper(firstText(str(m.get("semanticRole")), "GENERAL_KNOWLEDGE_TABLE"));
                String operation = upper(firstText(str(m.get("operation")), "UNKNOWN"));
                String scopeLevel = upper(firstText(str(m.get("scopeLevel")), "UNKNOWN"));
                String topic = firstText(str(m.get("topic")), str(result.get("topic")));
                String series = str(m.get("series"));
                String item = str(m.get("item"));
                String filename = firstText(str(m.get("filename")), str(m.get("originalFilename")), "");
                String artifactKey = firstText(str(m.get("artifactKey")), buildArtifactKey(topic, semanticRole, scopeLevel, series, item, filename));

                m.put("semanticRole", semanticRole);
                m.put("operation", operation);
                m.put("scopeLevel", scopeLevel);
                m.put("series", series);
                m.put("item", item);
                m.put("filename", filename);
                m.put("artifactKey", artifactKey);

                List<Object> missing = new ArrayList<>(RagJsonUtils.childList(m, "missingFields"));
                if ("REPLACE".equals(operation)) {
                    if (!StringUtils.hasText(topic)) missing.add("교체할 주제(topic)가 필요합니다.");
                    if (!StringUtils.hasText(semanticRole) || "GENERAL_KNOWLEDGE_TABLE".equals(semanticRole)) missing.add("교체할 자료 역할(semanticRole)이 필요합니다.");
                    if ("UNKNOWN".equals(scopeLevel)) missing.add("교체 범위(scopeLevel)가 필요합니다.");
                    if (("SERIES".equals(scopeLevel) || "ITEM".equals(scopeLevel)) && !StringUtils.hasText(series)) missing.add("교체할 시리즈가 필요합니다.");
                    if ("ITEM".equals(scopeLevel) && !StringUtils.hasText(item)) missing.add("교체할 품목이 필요합니다.");
                }
                if ("UNKNOWN".equals(operation) && Boolean.TRUE.equals(m.get("canStoreStructured"))) {
                    missing.add("업로드 자료가 신규 추가인지 기존 자료 교체인지 필요합니다.");
                }
                if (!missing.isEmpty()) {
                    m.put("missingFields", distinct(missing));
                    questions.add("자료 저장 전 확인 필요: " + filename + " / " + String.join(", ", distinct(missing).stream().map(String::valueOf).toList()));
                }
                normalized.add(m);
            }
        }
        result.put("materials", normalized);
    }

    private boolean hasPersistablePayload(Map<String, Object> result) {
        Map<String, Object> input = RagJsonUtils.childMap(result, "inputInterpretation");
        if (RagJsonUtils.boolValue(input, "hasPersistableKnowledge", false)) return true;
        if (StringUtils.hasText(RagJsonUtils.stringValue(result, "knowledgeText"))) return true;
        if (!RagJsonUtils.childList(RagJsonUtils.childMap(result, "processJson"), "steps").isEmpty()) return true;
        Map<String, Object> pricing = RagJsonUtils.childMap(result, "pricingJson");
        if (!RagJsonUtils.childList(pricing, "calculationRules").isEmpty()) return true;
        if (!RagJsonUtils.childList(pricing, "requiredArtifacts").isEmpty()) return true;
        Map<String, Object> constraints = RagJsonUtils.childMap(result, "constraintsJson");
        return !RagJsonUtils.childList(constraints, "rules").isEmpty()
                || !RagJsonUtils.childList(constraints, "skipRules").isEmpty()
                || !RagJsonUtils.childList(constraints, "answerFilterRules").isEmpty()
                || !RagJsonUtils.childList(constraints, "asPolicyRules").isEmpty();
    }

    private Map<String, Object> normalizeProcessJson(Object raw) {
        Map<String, Object> process = RagJsonUtils.toMap(objectMapper, raw);
        process.putIfAbsent("schemaType", "ORDER_PROCESS_BUILDER_V3");
        process.putIfAbsent("steps", List.of());
        return process;
    }

    private Map<String, Object> normalizePricingJson(Object raw) {
        Map<String, Object> pricing = RagJsonUtils.toMap(objectMapper, raw);
        pricing.putIfAbsent("schemaType", "ORDER_PRICING_ENGINE_V3");
        pricing.putIfAbsent("calculationRules", List.of());
        pricing.putIfAbsent("excelTables", List.of());
        pricing.putIfAbsent("requiredArtifacts", List.of());
        return pricing;
    }

    private Map<String, Object> normalizeConstraintsJson(Object raw) {
        Map<String, Object> constraints = RagJsonUtils.toMap(objectMapper, raw);
        constraints.putIfAbsent("schemaType", "ORDER_CONSTRAINTS_V3");
        constraints.putIfAbsent("rules", List.of());
        constraints.putIfAbsent("skipRules", List.of());
        constraints.putIfAbsent("answerFilterRules", List.of());
        constraints.putIfAbsent("asPolicyRules", List.of());
        return constraints;
    }

    private Map<String, Object> normalizeValidationReport(Object raw) {
        Map<String, Object> validation = RagJsonUtils.toMap(objectMapper, raw);
        validation.putIfAbsent("status", "OK");
        validation.putIfAbsent("warnings", List.of());
        validation.putIfAbsent("assumptions", List.of());
        validation.putIfAbsent("resolvedClarifications", List.of());
        validation.putIfAbsent("changePlan", List.of());
        validation.putIfAbsent("requiredArtifacts", List.of());
        validation.putIfAbsent("serverCalculationNotes", List.of());
        validation.putIfAbsent("readyToPublish", false);
        return validation;
    }

    private Object valueOrCurrent(Map<String, Object> result, Map<String, Object> currentVersion, String resultKey, String currentKey) {
        Object value = result.get(resultKey);
        if (value != null) return value;
        return currentVersion == null ? null : currentVersion.get(currentKey);
    }

    private String stringOrCurrent(Map<String, Object> result, Map<String, Object> currentVersion, String key) {
        String value = RagJsonUtils.stringValue(result, key);
        if (StringUtils.hasText(value)) return value;
        Object current = currentVersion == null ? null : currentVersion.get(key);
        return current == null ? "" : String.valueOf(current);
    }

    private String normalizeIntent(String intent) {
        if (!StringUtils.hasText(intent)) return "CLARIFY";
        String i = intent.trim().toUpperCase(Locale.ROOT);
        return switch (i) {
            case "LEARN_TEXT", "LEARN_FILE", "LEARN_MIXED", "KNOWLEDGE_UPDATE", "ANSWER_FROM_KNOWLEDGE", "CLARIFY",
                 "CONFLICT_RESOLUTION", "STRUCTURED_REPLACE", "ORDER_SIMULATION", "IGNORE_SMALLTALK" -> i;
            default -> "CLARIFY";
        };
    }

    private String fallbackAnswer(Map<String, Object> result) {
        if (RagJsonUtils.boolValue(result, "requiresClarification", false)) {
            List<Object> questions = RagJsonUtils.childList(result, "clarificationQuestions");
            if (!questions.isEmpty()) {
                return "저장 전에 확인이 필요합니다.\n- " + String.join("\n- ", questions.stream().map(String::valueOf).toList());
            }
            return "저장 전에 확인이 필요한 내용이 있습니다.";
        }
        if (RagJsonUtils.boolValue(result, "shouldPersist", false)) {
            return "입력 내용을 GPT가 해석해 구조화 지식으로 저장했습니다.";
        }
        return "입력 내용을 해석했습니다.";
    }

    private String buildKnowledgeText(Map<String, Object> result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[GPT 해석 학습 지식]\n");
        sb.append("의도: ").append(result.get("intent")).append('\n');
        sb.append("요약: ").append(RagJsonUtils.stringValue(result, "summary")).append("\n\n");
        Map<String, Object> input = RagJsonUtils.childMap(result, "inputInterpretation");
        String normalized = RagJsonUtils.stringValue(input, "normalizedUserInput");
        if (StringUtils.hasText(normalized)) sb.append("정규화 입력:\n").append(normalized).append("\n\n");
        sb.append("프로세스 JSON:\n").append(RagJsonUtils.pretty(objectMapper, result.get("processJson"))).append("\n\n");
        sb.append("가격 JSON:\n").append(RagJsonUtils.pretty(objectMapper, result.get("pricingJson"))).append("\n\n");
        sb.append("제약 JSON:\n").append(RagJsonUtils.pretty(objectMapper, result.get("constraintsJson")));
        return sb.toString();
    }

    private String buildArtifactKey(String topic, String role, String scope, String series, String item, String filename) {
        String base = String.join(":",
                firstText(topic, "GLOBAL"),
                firstText(role, "GENERAL"),
                firstText(scope, "UNKNOWN"),
                firstText(series, "ALL_SERIES"),
                firstText(item, "ALL_ITEMS")
        );
        if ("UNKNOWN".equals(scope) && StringUtils.hasText(filename)) base += ":" + filename;
        return base.replaceAll("[^0-9A-Za-z가-힣_:-]+", "_").replaceAll("_+", "_");
    }

    private List<Object> distinct(List<Object> values) {
        List<Object> result = new ArrayList<>();
        for (Object v : values) {
            if (v == null) continue;
            if (!result.contains(v)) result.add(v);
        }
        return result;
    }

    private Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
        }
        return result;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstText(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (StringUtils.hasText(v)) return v.trim();
        }
        return "";
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
