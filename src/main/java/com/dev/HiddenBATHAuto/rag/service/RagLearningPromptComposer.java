package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 감각기관 역할의 1차 전처리기입니다.
 *
 * 이 클래스는 최종 판단자가 아닙니다.
 * 파일명, 파서 결과, 엑셀 구조 미리보기, 사용자 설명을 GPT가 읽기 쉬운 감각 입력으로 정리합니다.
 * 최종 의도/저장/교체/질문 여부는 RagLearningCognitiveService가 다시 판단합니다.
 */
@Component
public class RagLearningPromptComposer {

    private static final Pattern SERIES_PATTERN = Pattern.compile("([A-Za-z가-힣0-9_ -]{1,30}시리즈)");
    private static final Pattern ITEM_PATTERN = Pattern.compile("([A-Za-z가-힣]{0,20}[-_ ]?[A-Z]{0,3}[-_ ]?\\d{2,5}[A-Za-z가-힣0-9_-]*)");

    private final OpenAiRagClient openAi;
    private final ObjectMapper objectMapper;

    public RagLearningPromptComposer(OpenAiRagClient openAi, ObjectMapper objectMapper) {
        this.openAi = openAi;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> compose(Map<String, Object> session,
                                       String userMessage,
                                       List<Map<String, Object>> fileContexts,
                                       boolean forceSave) {
        String topic = firstText(
                RagJsonUtils.stringValue(session, "topic"),
                RagJsonUtils.stringValue(session, "title"),
                "미지정 주제"
        );
        List<Map<String, Object>> safeFiles = fileContexts == null ? List.of() : fileContexts;

        Map<String, Object> ai = normalizeWithAi(topic, userMessage, safeFiles, forceSave);
        if (ai.isEmpty() || ai.containsKey("parseError")) {
            ai = fallbackNormalize(topic, userMessage, safeFiles, forceSave);
        }

        Map<String, Object> normalized = normalizeResult(topic, userMessage, safeFiles, ai, forceSave);
        normalized.put("normalizedPrompt", buildNormalizedPrompt(topic, userMessage, normalized, safeFiles));
        normalized.put("enrichedAttachmentText", buildEnrichedAttachmentText(normalized, safeFiles));
        normalized.put("clarificationAnswer", buildClarificationAnswer(normalized));
        return normalized;
    }

    private Map<String, Object> normalizeWithAi(String topic,
                                                String userMessage,
                                                List<Map<String, Object>> fileContexts,
                                                boolean forceSave) {
        String system = """
                당신은 HiddenBATHAuto RAG 학습 시스템의 감각기관 전처리 AI입니다.
                사용자의 자연어 지시와 업로드 파일 분석 결과를 중심 판단 AI가 읽기 쉬운 표준 감각 입력으로 정규화합니다.
                저장/교체/보류의 최종 결정자는 당신이 아니라 다음 단계의 RagLearningCognitiveService입니다.

                절대 규칙:
                - 사용자가 말하지 않은 시리즈, 품목, 적용 범위, 교체 범위를 추측해서 확정하지 마세요.
                - 파일명에서 명확히 읽히는 시리즈/품목은 후보로 사용할 수 있지만, 애매하면 UNKNOWN으로 두세요.
                - '추가'인지 '교체'인지 불명확하면 operation=UNKNOWN으로 두세요.
                - 자료 역할이 불명확하면 semanticRole=GENERAL_KNOWLEDGE_TABLE로 두고 확인 질문을 만드세요.
                - 여러 파일이 있으면 파일별로 materials 항목을 반드시 하나씩 만드세요.
                - 엑셀 파일이 아닌 자료는 canStoreStructured=false로 두고 벡터 학습 보조자료로 분류하세요.
                - JSON 객체 하나만 반환하세요.

                semanticRole 후보:
                SIZE_CONSTRAINT_TABLE, COLOR_RULE_TABLE, BASE_PRICE_MATRIX, BASE_PRICE_TABLE,
                COUNTERTOP_PRICE_MATRIX, COUNTERTOP_PRICE_TABLE, OPTION_PRICE_TABLE,
                SINK_PRICE_TABLE, HANDLE_PRICE_TABLE, PROCESS_RULE_TEXT, GENERAL_KNOWLEDGE_TABLE, GENERAL_MATRIX

                operation 후보: ADD, REPLACE, UNKNOWN
                scopeLevel 후보: TOPIC, SERIES, ITEM, MULTI_ITEM, UNKNOWN

                반환 스키마:
                {
                  "topic":"주제",
                  "requiresClarification":false,
                  "clarificationQuestions":[],
                  "materials":[
                    {
                      "filename":"업로드 파일명",
                      "sourceType":"EXCEL|TEXT|PDF|UNKNOWN",
                      "semanticRole":"SIZE_CONSTRAINT_TABLE",
                      "operation":"ADD|REPLACE|UNKNOWN",
                      "scopeLevel":"TOPIC|SERIES|ITEM|MULTI_ITEM|UNKNOWN",
                      "series":"",
                      "item":"",
                      "artifactKey":"topic:role:scope:series:item 형태의 안전한 키",
                      "canStoreStructured":true,
                      "missingFields":[],
                      "notes":[]
                    }
                  ],
                  "globalNotes":[],
                  "confidence":0.0
                }
                """;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("topic", topic);
        payload.put("userMessage", userMessage);
        payload.put("fileContexts", slimFileContextsForAi(fileContexts));
        payload.put("forceSave", forceSave);
        try {
            String raw = openAi.responseJson(system, RagJsonUtils.pretty(objectMapper, payload));
            JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, raw);
            return RagJsonUtils.toMap(objectMapper, node.toString());
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("aiNormalizeError", e.getMessage());
            return fallback;
        }
    }

    private Map<String, Object> fallbackNormalize(String topic,
                                                  String userMessage,
                                                  List<Map<String, Object>> fileContexts,
                                                  boolean forceSave) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", topic);
        List<Map<String, Object>> materials = new ArrayList<>();
        String combinedInstruction = compact(userMessage);

        for (Map<String, Object> file : fileContexts) {
            String filename = firstText(str(file.get("filename")), str(file.get("originalFilename")), "upload");
            String text = compact(userMessage + " " + filename + " " + file);
            String role = firstText(str(file.get("semanticRole")), inferRole(text), "GENERAL_KNOWLEDGE_TABLE");
            String operation = inferOperation(text);
            String series = inferSeries(text);
            String item = inferItem(text, series);
            String scope = inferScope(text, series, item);

            Map<String, Object> material = new LinkedHashMap<>();
            material.put("filename", filename);
            material.put("sourceType", firstText(str(file.get("sourceType")), "UNKNOWN"));
            material.put("semanticRole", role);
            material.put("operation", operation);
            material.put("scopeLevel", scope);
            material.put("series", series);
            material.put("item", item);
            material.put("canStoreStructured", Boolean.TRUE.equals(file.get("structured")) || isExcel(filename));
            material.put("notes", List.of("OpenAI 정규화 실패 또는 생략으로 기본 휴리스틱 전처리를 적용했습니다."));
            material.put("artifactKey", artifactKey(topic, role, scope, series, item, filename));
            materials.add(material);
        }

        result.put("materials", materials);
        result.put("globalNotes", StringUtils.hasText(combinedInstruction)
                ? List.of("사용자 원문을 표준 학습 프롬프트로 변환했습니다.")
                : List.of("사용자 설명이 비어 있어 파일명과 엑셀 구조만으로 전처리했습니다."));
        result.put("confidence", 0.45);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeResult(String topic,
                                                String userMessage,
                                                List<Map<String, Object>> fileContexts,
                                                Map<String, Object> raw,
                                                boolean forceSave) {
        Map<String, Object> result = new LinkedHashMap<>();
        String resolvedTopic = firstText(str(raw.get("topic")), topic);
        result.put("topic", resolvedTopic);
        result.put("userOriginalText", userMessage);
        result.put("forceSave", forceSave);
        result.put("preprocessor", "RagLearningPromptComposer");
        result.put("preprocessorVersion", "20260612-sensory-preprocessor-v2");

        List<Map<String, Object>> materials = new ArrayList<>();
        Object rawMaterials = raw.get("materials");
        if (rawMaterials instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> m) materials.add(cast(m));
            }
        }
        if (materials.isEmpty() && !fileContexts.isEmpty()) {
            materials = (List<Map<String, Object>>) fallbackNormalize(resolvedTopic, userMessage, fileContexts, forceSave).get("materials");
        }

        Map<String, Map<String, Object>> fileIndex = new LinkedHashMap<>();
        for (Map<String, Object> f : fileContexts) {
            fileIndex.put(firstText(str(f.get("filename")), str(f.get("originalFilename")), "upload"), f);
        }

        List<String> allQuestions = new ArrayList<>();
        List<Map<String, Object>> normalizedMaterials = new ArrayList<>();
        for (Map<String, Object> material : materials) {
            Map<String, Object> m = new LinkedHashMap<>(material);
            String filename = firstText(str(m.get("filename")), "upload");
            Map<String, Object> file = fileIndex.getOrDefault(filename, Map.of());

            String semanticRole = firstText(str(m.get("semanticRole")), str(file.get("semanticRole")), inferRole(userMessage + " " + filename), "GENERAL_KNOWLEDGE_TABLE");
            String operation = normalizeOperation(firstText(str(m.get("operation")), inferOperation(userMessage + " " + filename)));
            String series = clean(firstText(str(m.get("series")), inferSeries(userMessage + " " + filename)));
            String item = clean(firstText(str(m.get("item")), inferItem(userMessage + " " + filename, series)));
            String scope = normalizeScope(firstText(str(m.get("scopeLevel")), inferScope(userMessage + " " + filename, series, item)));
            boolean excel = isExcel(filename) || Boolean.TRUE.equals(file.get("structured"));

            List<String> missing = new ArrayList<>();
            if (isAmbiguousRole(semanticRole)) missing.add("자료 역할");
            if ("UNKNOWN".equals(operation)) missing.add("처리 방식(신규 추가인지 기존 자료 교체인지)");
            if (excel && "UNKNOWN".equals(scope)) missing.add("적용 범위(전체 주제/시리즈/품목 중 어디에 적용되는지)");
            if ("ITEM".equals(scope) && !StringUtils.hasText(item)) missing.add("적용 품목");
            if (("SERIES".equals(scope) || "ITEM".equals(scope)) && !StringUtils.hasText(series)) missing.add("적용 시리즈");

            List<Object> existingMissing = RagJsonUtils.childList(m, "missingFields");
            for (Object x : existingMissing) {
                String s = String.valueOf(x);
                if (StringUtils.hasText(s) && !missing.contains(s)) missing.add(s);
            }

            m.put("filename", filename);
            m.put("semanticRole", semanticRole);
            m.put("operation", operation);
            m.put("scopeLevel", scope);
            m.put("series", series);
            m.put("item", item);
            m.put("canStoreStructured", excel && !isAmbiguousRole(semanticRole));
            m.put("missingFields", missing);
            m.put("artifactKey", firstText(str(m.get("artifactKey")), artifactKey(resolvedTopic, semanticRole, scope, series, item, filename)));
            m.put("fileAnalysis", slimFileAnalysis(file));

            if (!missing.isEmpty() && !forceSave) {
                allQuestions.add(filename + " 파일: " + String.join(", ", missing) + "을/를 알려주세요.");
            }
            normalizedMaterials.add(m);
        }

        List<Object> aiQuestions = RagJsonUtils.childList(raw, "clarificationQuestions");
        for (Object q : aiQuestions) {
            String s = String.valueOf(q);
            if (StringUtils.hasText(s) && !allQuestions.contains(s)) allQuestions.add(s);
        }

        boolean requiresClarification = !forceSave && (!allQuestions.isEmpty() || RagJsonUtils.boolValue(raw, "requiresClarification", false));
        result.put("materials", normalizedMaterials);
        result.put("requiresClarification", requiresClarification);
        result.put("clarificationQuestions", allQuestions);
        result.put("globalNotes", raw.getOrDefault("globalNotes", List.of()));
        result.put("confidence", raw.getOrDefault("confidence", 0.5));
        return result;
    }


    private List<Map<String, Object>> slimFileContextsForAi(List<Map<String, Object>> fileContexts) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (fileContexts == null) return result;
        for (Map<String, Object> file : fileContexts) {
            Map<String, Object> slim = new LinkedHashMap<>();
            slim.put("filename", firstText(str(file.get("filename")), str(file.get("originalFilename")), "upload"));
            slim.put("originalFilename", str(file.get("originalFilename")));
            slim.put("sourceType", str(file.get("sourceType")));
            slim.put("rawTextPreview", RagJsonUtils.truncate(firstText(str(file.get("rawTextPreview")), str(file.get("rawText"))), 5000));
            slim.put("parserMetadata", file.getOrDefault("parserMetadata", Map.of()));
            slim.put("structured", file.get("structured"));
            slim.put("semanticRole", file.get("semanticRole"));
            slim.put("summaryText", RagJsonUtils.truncate(str(file.get("summaryText")), 2500));
            slim.put("warnings", file.getOrDefault("warnings", List.of()));
            slim.put("tableCount", file.getOrDefault("tableCount", 0));
            slim.put("matrixCount", file.getOrDefault("matrixCount", 0));
            slim.put("structuredPreview", slimStructuredPreview(file.get("structuredPreview")));
            result.add(slim);
        }
        return result;
    }

    private Map<String, Object> slimStructuredPreview(Object preview) {
        Map<String, Object> source = RagJsonUtils.toMap(objectMapper, preview);
        Map<String, Object> slim = new LinkedHashMap<>();
        slim.put("structured", source.get("structured"));
        slim.put("semanticRole", source.get("semanticRole"));
        slim.put("summaryText", RagJsonUtils.truncate(str(source.get("summaryText")), 2500));
        slim.put("warnings", source.getOrDefault("warnings", List.of()));
        slim.put("tableCount", source.getOrDefault("tableCount", 0));
        slim.put("matrixCount", source.getOrDefault("matrixCount", 0));
        slim.put("tables", RagJsonUtils.childList(source, "tables").stream().limit(3).toList());
        slim.put("matrices", RagJsonUtils.childList(source, "matrices").stream().limit(3).toList());
        return slim;
    }

    private Map<String, Object> slimFileAnalysis(Map<String, Object> file) {
        Map<String, Object> slim = new LinkedHashMap<>();
        slim.put("filename", firstText(str(file.get("filename")), str(file.get("originalFilename")), ""));
        slim.put("sourceType", file.get("sourceType"));
        slim.put("semanticRole", file.get("semanticRole"));
        slim.put("structured", file.get("structured"));
        slim.put("summaryText", RagJsonUtils.truncate(str(file.get("summaryText")), 2500));
        slim.put("warnings", file.getOrDefault("warnings", List.of()));
        slim.put("tableCount", file.getOrDefault("tableCount", 0));
        slim.put("matrixCount", file.getOrDefault("matrixCount", 0));
        return slim;
    }

    @SuppressWarnings("unchecked")
    private String buildNormalizedPrompt(String topic,
                                         String userMessage,
                                         Map<String, Object> normalized,
                                         List<Map<String, Object>> fileContexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("감각 전처리 결과입니다. 아래 내용은 최종 판단이 아니라 GPT 중심 판단 엔진에 전달할 입력 정리본입니다.\n\n");
        sb.append("학습 주제: ").append(topic).append("\n");
        sb.append("사용자 원문: ").append(StringUtils.hasText(userMessage) ? userMessage.trim() : "(없음)").append("\n\n");
        sb.append("저장 원칙:\n");
        sb.append("1. 가격/사이즈/색상/옵션 자료는 AI 추정이 아니라 구조화 테이블 또는 가격 매트릭스로 저장한다.\n");
        sb.append("2. 적용 범위가 다른 자료는 서로 교체하지 않는다.\n");
        sb.append("3. 교체 요청은 동일 topic + semanticRole + scopeLevel + series + item 기준으로만 기존 active 자료를 비활성화한다.\n");
        sb.append("4. 애매한 내용은 저장하지 않고 사용자에게 질문한다.\n\n");

        List<Map<String, Object>> materials = (List<Map<String, Object>>) normalized.getOrDefault("materials", List.of());
        sb.append("업로드 자료 해석:\n");
        if (materials.isEmpty()) {
            sb.append("- 업로드 자료 없음\n");
        }
        for (Map<String, Object> m : materials) {
            sb.append("- 파일명: ").append(str(m.get("filename"))).append("\n");
            sb.append("  자료 역할: ").append(str(m.get("semanticRole"))).append("\n");
            sb.append("  처리 방식: ").append(str(m.get("operation"))).append("\n");
            sb.append("  적용 범위: ").append(str(m.get("scopeLevel"))).append("\n");
            sb.append("  적용 시리즈: ").append(blankToDash(str(m.get("series")))).append("\n");
            sb.append("  적용 품목: ").append(blankToDash(str(m.get("item")))).append("\n");
            sb.append("  artifactKey: ").append(str(m.get("artifactKey"))).append("\n");
            List<Object> missing = RagJsonUtils.childList(m, "missingFields");
            if (!missing.isEmpty()) sb.append("  부족 정보: ").append(missing).append("\n");
        }

        sb.append("\n파일 분석 요약:\n");
        for (Map<String, Object> f : fileContexts) {
            sb.append("[파일] ").append(firstText(str(f.get("filename")), str(f.get("originalFilename")), "upload")).append("\n");
            if (StringUtils.hasText(str(f.get("summaryText")))) {
                sb.append(RagJsonUtils.truncate(str(f.get("summaryText")), 5000)).append("\n");
            }
            if (StringUtils.hasText(str(f.get("rawTextPreview")))) {
                sb.append("원문 미리보기:\n").append(RagJsonUtils.truncate(str(f.get("rawTextPreview")), 5000)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String buildEnrichedAttachmentText(Map<String, Object> normalized, List<Map<String, Object>> fileContexts) {
        StringBuilder sb = new StringBuilder();
        sb.append("[GPT 감각 전처리 입력]\n");
        sb.append(str(normalized.get("normalizedPrompt"))).append("\n\n");
        sb.append("[전처리 JSON]\n");
        sb.append(RagJsonUtils.pretty(objectMapper, normalized)).append("\n\n");
        for (Map<String, Object> f : fileContexts) {
            sb.append("[업로드 파일: ").append(firstText(str(f.get("filename")), str(f.get("originalFilename")), "upload")).append("]\n");
            sb.append("sourceType: ").append(firstText(str(f.get("sourceType")), "UNKNOWN")).append("\n");
            if (StringUtils.hasText(str(f.get("rawText")))) {
                sb.append(RagJsonUtils.truncate(str(f.get("rawText")), 30_000)).append("\n");
            }
            if (StringUtils.hasText(str(f.get("summaryText")))) {
                sb.append("구조화 분석 요약:\n").append(str(f.get("summaryText"))).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String buildClarificationAnswer(Map<String, Object> normalized) {
        if (!RagJsonUtils.boolValue(normalized, "requiresClarification", false)) {
            return "";
        }
        List<Object> questions = RagJsonUtils.childList(normalized, "clarificationQuestions");
        StringBuilder sb = new StringBuilder();
        sb.append("업로드 파일을 바로 저장하기에는 부족한 정보가 있어 아직 학습 저장하지 않았습니다.\n");
        sb.append("아래 항목을 알려주시면, 제가 학습용 표준 프롬프트로 다시 정리해서 저장하겠습니다.\n");
        for (Object q : questions) {
            sb.append("- ").append(q).append("\n");
        }
        sb.append("\n예시: 이 파일은 하부장 / C시리즈 / C-100 품목의 사이즈 제한표이고, 기존 자료 교체가 아니라 신규 추가야.");
        return sb.toString().trim();
    }

    private String inferRole(String text) {
        String t = compact(text).toLowerCase(Locale.ROOT);
        if (containsAny(t, "상판", "마블상판", "countertop")) return containsAny(t, "가격", "단가", "금액", "price") ? "COUNTERTOP_PRICE_MATRIX" : "COUNTERTOP_PRICE_TABLE";
        if (containsAny(t, "사이즈", "규격", "w", "d", "h", "최소", "최대", "as", "무상")) return "SIZE_CONSTRAINT_TABLE";
        if (containsAny(t, "색상", "컬러", "color")) return "COLOR_RULE_TABLE";
        if (containsAny(t, "손잡이")) return "HANDLE_PRICE_TABLE";
        if (containsAny(t, "세면대")) return "SINK_PRICE_TABLE";
        if (containsAny(t, "타공", "led", "휴지걸이", "드라이걸이", "옵션")) return "OPTION_PRICE_TABLE";
        if (containsAny(t, "가격", "금액", "단가", "price", "원")) return "BASE_PRICE_MATRIX";
        if (containsAny(t, "프로세스", "질문", "흐름", "단계")) return "PROCESS_RULE_TEXT";
        return "GENERAL_KNOWLEDGE_TABLE";
    }

    private String inferOperation(String text) {
        String t = compact(text).toLowerCase(Locale.ROOT);
        if (containsAny(t, "교체", "대체", "replace", "업데이트", "기존것을새", "기존것", "단가인상", "인상분", "변경")) return "REPLACE";
        if (containsAny(t, "추가", "신규", "새로추가", "등록", "저장", "add")) return "ADD";
        return "UNKNOWN";
    }

    private String normalizeOperation(String op) {
        if (!StringUtils.hasText(op)) return "UNKNOWN";
        String o = op.trim().toUpperCase(Locale.ROOT);
        if (o.contains("REPLACE") || o.contains("교체") || o.contains("UPDATE")) return "REPLACE";
        if (o.contains("ADD") || o.contains("추가") || o.contains("신규")) return "ADD";
        return "UNKNOWN";
    }

    private String inferScope(String text, String series, String item) {
        String t = compact(text).toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(item)) return "ITEM";
        if (StringUtils.hasText(series)) return "SERIES";
        if (containsAny(t, "전체", "공통", "모든", "전품목", "주제 전체", "하부장 전체")) return "TOPIC";
        if (containsAny(t, "여러 품목", "복수", "목록")) return "MULTI_ITEM";
        return "UNKNOWN";
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) return "UNKNOWN";
        String s = scope.trim().toUpperCase(Locale.ROOT);
        if (s.contains("TOPIC") || s.contains("전체") || s.contains("공통")) return "TOPIC";
        if (s.contains("SERIES") || s.contains("시리즈")) return "SERIES";
        if (s.contains("ITEM") || s.contains("품목")) return "ITEM";
        if (s.contains("MULTI")) return "MULTI_ITEM";
        return "UNKNOWN";
    }

    private String inferSeries(String text) {
        Matcher m = SERIES_PATTERN.matcher(text == null ? "" : text);
        if (m.find()) return clean(m.group(1));
        return "";
    }

    private String inferItem(String text, String series) {
        String t = text == null ? "" : text;
        Matcher m = ITEM_PATTERN.matcher(t);
        while (m.find()) {
            String candidate = clean(m.group(1));
            if (!StringUtils.hasText(candidate)) continue;
            if (candidate.endsWith("시리즈")) continue;
            if (StringUtils.hasText(series) && series.contains(candidate)) continue;
            if (candidate.matches(".*\\d{2,5}.*")) return candidate;
        }
        return "";
    }

    private boolean isAmbiguousRole(String role) {
        return !StringUtils.hasText(role)
                || "GENERAL_KNOWLEDGE_TABLE".equals(role)
                || "GENERAL_MATRIX".equals(role)
                || "UNKNOWN".equalsIgnoreCase(role);
    }

    private String artifactKey(String topic, String role, String scope, String series, String item, String filename) {
        String key = String.join(":",
                firstText(topic, "GLOBAL"),
                firstText(role, "GENERAL"),
                firstText(scope, "UNKNOWN"),
                firstText(series, "ALL_SERIES"),
                firstText(item, "ALL_ITEMS")
        );
        if ("UNKNOWN".equals(firstText(scope, "UNKNOWN"))) {
            key += ":" + firstText(filename, "upload");
        }
        return safeKey(key, 240);
    }

    private boolean isExcel(String filename) {
        String f = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return f.endsWith(".xlsx") || f.endsWith(".xls");
    }

    private String safeKey(String value, int max) {
        String s = value == null ? "key" : value.trim();
        s = s.replaceAll("[^0-9A-Za-z가-힣_:-]+", "_").replaceAll("_+", "_");
        if (s.length() > max) s = s.substring(0, max);
        return s;
    }

    private String clean(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String blankToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String firstText(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (StringUtils.hasText(v)) return v.trim();
        }
        return "";
    }

    private Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
        }
        return result;
    }
}
