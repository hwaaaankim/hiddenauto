package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 학습 대화의 중심 판단 엔진입니다.
 *
 * 설계 원칙:
 * - 모든 사용자 입력은 먼저 GPT가 해석합니다.
 * - 서버의 문자열 조건문은 최종 판단자가 아니라, GPT 결과의 JSON 검증/저장 안전장치 역할만 합니다.
 * - 엑셀/파일/채팅/기존 지식/검색 결과/미해결 질문을 한 번에 넣고
 *   의도, 학습 가능 내용, 부족 정보, 교체 범위, 구조화 JSON, 사용자 답변을 GPT가 결정합니다.
 * - 가격 계산은 GPT가 계산식을 구조화하고, 실제 산술은 서버 계산기가 수행할 수 있게 JSON rule로 저장합니다.
 */
@Service
public class RagLearningCognitiveService {

    private final OpenAiRagClient openAi;
    private final ObjectMapper objectMapper;

    public RagLearningCognitiveService(OpenAiRagClient openAi, ObjectMapper objectMapper) {
        this.openAi = openAi;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> interpret(Map<String, Object> currentVersion,
                                         List<Map<String, Object>> retrievedChunks,
                                         List<Map<String, Object>> conversationHistory,
                                         Map<String, Object> pendingResolution,
                                         String userMessage,
                                         String attachmentFilename,
                                         String attachmentText,
                                         String deterministicExpansionHint,
                                         boolean forceSave) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currentVersion", compactVersion(currentVersion));
        payload.put("retrievedChunks", retrievedChunks == null ? List.of() : retrievedChunks);
        payload.put("conversationHistory", conversationHistory == null ? List.of() : conversationHistory);
        payload.put("pendingResolution", pendingResolution == null ? Map.of() : pendingResolution);
        payload.put("userInput", Map.of(
                "message", userMessage == null ? "" : userMessage,
                "attachmentFilename", attachmentFilename == null ? "" : attachmentFilename,
                "attachmentText", RagJsonUtils.truncate(attachmentText, 70_000),
                "forceSave", forceSave
        ));
        payload.put("deterministicExpansionHint", deterministicExpansionHint == null ? "" : deterministicExpansionHint);

        try {
            String raw = openAi.responseJson(systemPrompt(), RagJsonUtils.pretty(objectMapper, payload));
            JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, raw);
            Map<String, Object> parsed = RagJsonUtils.toMap(objectMapper, node.toString());
            parsed.putIfAbsent("model", openAi.chatModel());
            parsed.putIfAbsent("cognitiveEngine", "RagLearningCognitiveService");
            parsed.putIfAbsent("cognitiveEngineVersion", "20260612-gpt-brain-v1");
            return parsed;
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("intent", "CLARIFY");
            fallback.put("requiresUpload", false);
            fallback.put("requiresClarification", true);
            fallback.put("shouldPersist", false);
            fallback.put("answer", "GPT 해석 단계에서 오류가 발생해 아직 저장하지 않았습니다. 입력 내용은 보존되었으니, 오류 로그를 확인한 뒤 다시 시도해 주세요. 오류: " + e.getMessage());
            fallback.put("clarificationQuestions", List.of("GPT 해석 오류가 발생했습니다. 서버 로그와 OpenAI API 응답을 확인해 주세요."));
            fallback.put("conflicts", List.of());
            fallback.put("summary", stringValue(currentVersion, "summary"));
            fallback.put("processJson", jsonOrEmpty(currentVersion, "process_json", "ORDER_PROCESS_BUILDER_V3"));
            fallback.put("pricingJson", jsonOrEmpty(currentVersion, "pricing_json", "ORDER_PRICING_ENGINE_V3"));
            fallback.put("constraintsJson", jsonOrEmpty(currentVersion, "constraints_json", "ORDER_CONSTRAINTS_V3"));
            fallback.put("validationReportJson", Map.of(
                    "status", "GPT_INTERPRETATION_ERROR",
                    "warnings", List.of(e.getMessage()),
                    "assumptions", List.of(),
                    "resolvedClarifications", List.of(),
                    "changePlan", List.of(),
                    "readyToPublish", false
            ));
            fallback.put("knowledgeText", "");
            fallback.put("materials", List.of());
            fallback.put("confidence", 0.0);
            fallback.put("cognitiveEngine", "RagLearningCognitiveService");
            fallback.put("cognitiveEngineVersion", "20260612-gpt-brain-v1");
            return fallback;
        }
    }

    private String systemPrompt() {
        return """
                당신은 HiddenBATHAuto의 학습형 RAG/주문 프로세스 구축을 담당하는 중심 판단 AI입니다.
                당신의 역할은 단순 문서 요약이 아니라, 사람의 뇌처럼 입력을 해석하고 기존 기억과 연결해
                실제 고객 상담/발주/가격 계산에 사용할 수 있는 구조화 지식으로 변환하는 것입니다.

                전체 구조 비유:
                - 사용자 채팅, 엑셀, 파일 파서 결과, 벡터 검색 결과, 기존 버전 JSON은 감각 입력입니다.
                - 당신은 이 감각 입력을 해석하는 뇌입니다.
                - 서버는 당신의 판단 결과를 검증하고 저장하는 장기기억/계산기입니다.
                - 벡터DB는 설명형 기억이고, processJson/pricingJson/constraintsJson/structured table은 실행형 기억입니다.

                반드시 지켜야 할 최상위 원칙:
                1. 모든 입력을 먼저 의도 분석하세요. 파일이 있든 없든, 텍스트만 있든 무조건 해석합니다.
                2. 사용자가 엑셀/파일을 추후 업로드한다고 말해도, 현재 텍스트에 학습 가능한 규칙이 있으면 반드시 shouldPersist=true로 판단하세요.
                   단, 기존 확정 지식과 충돌하거나 교체 범위가 불명확해서 저장하면 기존 지식이 잘못 바뀌는 경우에만 requiresClarification=true로 두고 저장하지 않습니다.
                   현재 텍스트 안에 확정 규칙과 미확정 규칙이 섞여 있으면, 확정 규칙은 저장하고 미확정 규칙은 PENDING_CONFIRMATION/PENDING_UPLOAD로 분리하세요.
                3. '파일이 필요하다' 또는 '추가 확인이 필요하다'는 사실만으로 현재 확정 텍스트 지식 저장을 막지 마세요.
                   필요한 파일은 requiredArtifacts/PENDING_UPLOAD로 기록하고, 계산 전에 물어봐야 할 내용은 validationReport.serverCalculationNotes 또는 nonBlockingQuestions에 기록하세요.
                4. 가격을 임의로 추정하지 마세요. 가격표가 없으면 가격표 필요 자료로 기록하고, 공식/계산순서만 저장하세요.
                5. 계산식은 사람이 읽는 설명만 쓰지 말고, 서버가 계산할 수 있는 rule JSON으로 정리하세요.
                6. 기존 확정 지식과 충돌하거나 교체 범위가 불명확하면 shouldPersist=false, requiresClarification=true입니다.
                7. 사용자가 교체를 말했더라도 topic + semanticRole + scopeLevel + series + item 같은 교체 범위가 불명확하면 절대 교체하지 마세요.
                8. 사용자가 질문을 한 것인지, 새 학습 내용을 준 것인지, 둘이 섞였는지 구분하세요.
                9. 반드시 JSON 객체 하나만 반환하세요. Markdown, 코드블록, 설명문을 JSON 밖에 쓰지 마세요.

                intent 후보:
                - LEARN_TEXT: 텍스트만으로 새 지식을 학습해야 함
                - LEARN_FILE: 파일 중심 학습
                - LEARN_MIXED: 채팅+파일을 함께 학습
                - ANSWER_FROM_KNOWLEDGE: 저장된 지식에 대한 질문 답변
                - CLARIFY: 저장 전 확인 질문 필요
                - CONFLICT_RESOLUTION: 이전 pending 질문에 대한 해결 답변
                - STRUCTURED_REPLACE: 기존 자료 교체/단가 인상/새 표 반영
                - ORDER_SIMULATION: 고객 주문 흐름 테스트 또는 가격계산 테스트
                - IGNORE_SMALLTALK: 재사용 지식이 아닌 일반 대화

                processJson 구성 규칙:
                - schemaType은 ORDER_PROCESS_BUILDER_V3를 사용하세요.
                - steps는 orderNo, stepKey, title, question, answerType, required, options, showCondition, validation, pricingBindings, uiHints를 포함하세요.
                - answerType 후보: SELECT, MULTI_SELECT, BOOLEAN, NUMBER, SIZE_WDH, TEXT, FILE_IMAGE, FILE_EXCEL, COORDINATE_2D, OPTION_GROUP, REPEAT_GROUP.
                - 조건부 질문은 showCondition으로 분리하세요.
                - 사용자 답변을 챗봇이 쉽게 해석해야 하므로 synonyms, examples, normalizationHints가 필요하면 넣으세요.

                pricingJson 구성 규칙:
                - schemaType은 ORDER_PRICING_ENGINE_V3를 사용하세요.
                - calculationRules는 서버가 산술 계산할 수 있는 원자적 rule 목록으로 작성하세요.
                - 지원 rule type 우선 후보:
                  BASE_TABLE_LOOKUP, MATRIX_LOOKUP, HEIGHT_SURCHARGE_BY_100_CEIL,
                  FIXED_PER_COUNT, FREE_COUNT_THEN_UNIT, FIXED_AMOUNT_WHEN_SELECTED,
                  HANDLE_PRICE_BY_TYPE, EDGE_BANDING_BY_SIDES, CONDITIONAL_RULE_GROUP,
                  SUM, ROUND_UP_TO_UNIT, CUSTOM_FORMULA_PENDING_IMPLEMENTATION.
                - 복잡한 계산식이라도 가능한 한 입력값, 조건, 산식, 필요한 테이블, roundingPolicy, outputKey를 분리하세요.
                - 서버 계산 구현이 아직 없는 복잡식은 type=CUSTOM_FORMULA_PENDING_IMPLEMENTATION으로 두고 formulaText와 requiredImplementation을 남기세요.
                - excelTables/requiredArtifacts에는 필요한 엑셀의 역할과 상태를 기록하세요.

                constraintsJson 구성 규칙:
                - schemaType은 ORDER_CONSTRAINTS_V3를 사용하세요.
                - 제작 가능 범위, AS 불가 조건, 색상 가능 조건, 질문 skip 조건, 입력 검증 조건을 rules/skipRules/answerFilterRules/asPolicyRules로 분리하세요.

                materials 구성 규칙:
                - 업로드 파일 또는 사용자가 언급한 자료 단위마다 materials 항목을 만드세요.
                - semanticRole 후보:
                  PROCESS_RULE_TEXT, SIZE_CONSTRAINT_TABLE, COLOR_RULE_TABLE,
                  BASE_PRICE_MATRIX, BASE_PRICE_TABLE,
                  COUNTERTOP_PRICE_MATRIX, COUNTERTOP_PRICE_TABLE,
                  SINK_PRICE_TABLE, HANDLE_PRICE_TABLE, OPTION_PRICE_TABLE,
                  DRAWING_GUIDE_FILE, GENERAL_KNOWLEDGE_TABLE, GENERAL_MATRIX.
                - operation 후보: ADD, MERGE, REPLACE, PENDING_UPLOAD, UNKNOWN.
                - scopeLevel 후보: TOPIC, SERIES, ITEM, MULTI_ITEM, GLOBAL, UNKNOWN.
                - 실제 파일이 아직 없으면 status=PENDING_UPLOAD, canStoreStructured=false로 둡니다.
                - 실제 업로드 파일이고 구조화 저장 가능하면 canStoreStructured=true로 둡니다.
                - artifactKey는 topic:semanticRole:scopeLevel:series:item 형태로 만드세요. 모호하면 artifactKey를 확정하지 말고 missingFields에 적으세요.

                반환 JSON 스키마:
                {
                  "intent": "LEARN_TEXT|LEARN_FILE|LEARN_MIXED|ANSWER_FROM_KNOWLEDGE|CLARIFY|CONFLICT_RESOLUTION|STRUCTURED_REPLACE|ORDER_SIMULATION|IGNORE_SMALLTALK",
                  "inputInterpretation": {
                    "userGoal": "사용자가 실제로 원하는 것",
                    "normalizedUserInput": "GPT와 서버가 이해하기 쉬운 표준 학습 문장",
                    "isLearningInput": true,
                    "isQuestion": false,
                    "hasFile": false,
                    "hasPersistableKnowledge": true
                  },
                  "requiresUpload": false,
                  "requiresClarification": false,
                  "shouldPersist": false,
                  "answer": "사용자에게 보여줄 한국어 답변",
                  "clarificationQuestions": [],
                  "nonBlockingQuestions": [],
                  "conflicts": [],
                  "materials": [],
                  "summary": "병합 후 전체 요약",
                  "processJson": {"schemaType":"ORDER_PROCESS_BUILDER_V3", "steps": []},
                  "pricingJson": {"schemaType":"ORDER_PRICING_ENGINE_V3", "calculationRules": [], "excelTables": [], "requiredArtifacts": []},
                  "constraintsJson": {"schemaType":"ORDER_CONSTRAINTS_V3", "rules": [], "skipRules": [], "answerFilterRules": [], "asPolicyRules": []},
                  "validationReportJson": {
                    "status":"OK|NEEDS_CLARIFICATION|CONFLICT|INSUFFICIENT_EVIDENCE|ANSWER_ONLY",
                    "warnings": [],
                    "assumptions": [],
                    "resolvedClarifications": [],
                    "changePlan": [],
                    "requiredArtifacts": [],
                    "serverCalculationNotes": [],
                    "readyToPublish": false
                  },
                  "knowledgeText": "벡터DB에 저장할 정리된 지식 문장",
                  "confidence": 0.0
                }
                """;
    }

    private Map<String, Object> compactVersion(Map<String, Object> version) {
        Map<String, Object> compact = new LinkedHashMap<>();
        if (version == null) return compact;
        compact.put("id", version.get("id"));
        compact.put("versionNo", version.get("version_no"));
        compact.put("title", version.get("title"));
        compact.put("learningDirection", version.get("learning_direction"));
        compact.put("summary", version.get("summary"));
        compact.put("processJson", version.get("process_json"));
        compact.put("pricingJson", version.get("pricing_json"));
        compact.put("constraintsJson", version.get("constraints_json"));
        compact.put("validationReportJson", version.get("validation_report_json"));
        return compact;
    }

    private String stringValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> jsonOrEmpty(Map<String, Object> map, String key, String schemaType) {
        Map<String, Object> value = RagJsonUtils.toMap(objectMapper, map == null ? null : map.get(key));
        if (value.isEmpty() && StringUtils.hasText(schemaType)) {
            value.put("schemaType", schemaType);
        }
        if (schemaType != null && schemaType.contains("PROCESS")) value.putIfAbsent("steps", new ArrayList<>());
        if (schemaType != null && schemaType.contains("PRICING")) {
            value.putIfAbsent("calculationRules", new ArrayList<>());
            value.putIfAbsent("excelTables", new ArrayList<>());
            value.putIfAbsent("requiredArtifacts", new ArrayList<>());
        }
        if (schemaType != null && schemaType.contains("CONSTRAINTS")) {
            value.putIfAbsent("rules", new ArrayList<>());
            value.putIfAbsent("skipRules", new ArrayList<>());
            value.putIfAbsent("answerFilterRules", new ArrayList<>());
            value.putIfAbsent("asPolicyRules", new ArrayList<>());
        }
        return value;
    }
}
