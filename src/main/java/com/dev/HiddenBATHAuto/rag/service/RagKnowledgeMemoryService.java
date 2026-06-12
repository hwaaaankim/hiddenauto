package com.dev.HiddenBATHAuto.rag.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagKnowledgeMemoryService {

    private static final int MAX_CHUNK_LENGTH = 1400;
    private static final int MAX_EMBED_TEXT_LENGTH = 7600;

    private final RagRepository repository;
    private final OpenAiRagClient openAi;
    private final ObjectMapper objectMapper;
    private final RagDomainRuleExpansionService domainExpansionService;

    public RagKnowledgeMemoryService(RagRepository repository,
                                     OpenAiRagClient openAi,
                                     ObjectMapper objectMapper,
                                     RagDomainRuleExpansionService domainExpansionService) {
        this.repository = repository;
        this.openAi = openAi;
        this.objectMapper = objectMapper;
        this.domainExpansionService = domainExpansionService;
    }

    /**
     * 채팅/학습에서 공통으로 사용하는 지식 커밋 파이프라인입니다.
     * - 모든 메시지는 별도의 chat/learning message 테이블에 이미 저장됩니다.
     * - 여기서는 "재검색 가능한 지식"으로 반영할 수 있는 내용만 버전 JSON + rag_document + rag_chunk에 저장합니다.
     * - 모순/부족/이상점이 있으면 저장하지 않고 clarificationQuestion을 반환합니다.
     */
    public Map<String, Object> analyzeAndCommitChatKnowledge(UUID projectId,
                                                              UUID versionId,
                                                              UUID sessionId,
                                                              String userMessage,
                                                              String assistantAnswer,
                                                              Map<String, Object> currentState,
                                                              List<Map<String, Object>> retrieved,
                                                              Map<String, Object> currentVersion) {
        Map<String, Object> pendingResolution = RagJsonUtils.childMap(currentState, "pendingKnowledgeResolution");

        // 질문만 있는 입력은 "학습 대상"이 아닙니다. 이미 채팅 로그에는 저장되므로,
        // 지식 병합/모순 검사를 하지 않고 NO_KNOWLEDGE_CHANGE로 종료합니다.
        if (pendingResolution.isEmpty() && looksLikeKnowledgeQuestion(userMessage) && !looksLikeLearningCommand(userMessage)) {
            Map<String, Object> analysis = new LinkedHashMap<>();
            analysis.put("intent", "ANSWER_ONLY");
            analysis.put("hasKnowledge", false);
            analysis.put("shouldPersist", false);
            analysis.put("requiresClarification", false);
            analysis.put("conflicts", List.of());
            analysis.put("anomalies", List.of());
            analysis.put("missingCriticalInformation", List.of());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("analysis", analysis);
            result.put("checkedAt", OffsetDateTime.now().toString());
            result.put("status", "NO_KNOWLEDGE_CHANGE");
            result.put("saveLabel", "지식 저장: 저장 생략");
            result.put("saved", false);
            result.put("knowledgeSaved", false);
            result.put("requiresClarification", false);
            result.put("pendingResolution", Map.of());
            result.put("message", "질문 답변 대화는 저장되었지만 재사용 가능한 새 지식이 아니어서 지식 저장은 생략했습니다.");
            result.put("version", currentVersion);
            return result;
        }

        Map<String, Object> analysis = analyzeKnowledge(projectId, versionId, sessionId, userMessage,
                assistantAnswer, currentState, retrieved, currentVersion, pendingResolution);

        boolean requiresClarification = RagJsonUtils.boolValue(analysis, "requiresClarification", false)
                || hasItems(analysis, "conflicts")
                || hasItems(analysis, "anomalies")
                || hasItems(analysis, "missingCriticalInformation");
        boolean shouldPersist = RagJsonUtils.boolValue(analysis, "shouldPersist", false);
        boolean hasKnowledge = RagJsonUtils.boolValue(analysis, "hasKnowledge", false)
                || StringUtils.hasText(RagJsonUtils.stringValue(analysis, "knowledgeText"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("analysis", analysis);
        result.put("checkedAt", OffsetDateTime.now().toString());

        if (requiresClarification) {
            Map<String, Object> pending = buildPending(projectId, versionId, sessionId, userMessage,
                    assistantAnswer, analysis, retrieved, pendingResolution);
            result.put("status", "WAITING_USER");
            result.put("saveLabel", "지식 저장: 저장 보류");
            result.put("saved", false);
            result.put("knowledgeSaved", false);
            result.put("requiresClarification", true);
            result.put("pendingResolution", pending);
            result.put("message", clarificationMessage(analysis));
            result.put("version", currentVersion);
            return result;
        }

        if (!shouldPersist || !hasKnowledge) {
            result.put("status", "NO_KNOWLEDGE_CHANGE");
            result.put("saveLabel", "지식 저장: 저장 생략");
            result.put("saved", false);
            result.put("knowledgeSaved", false);
            result.put("requiresClarification", false);
            result.put("pendingResolution", Map.of());
            result.put("message", "대화 로그는 저장되었지만 재사용 가능한 새 지식이 아니어서 지식 저장은 생략했습니다.");
            result.put("version", currentVersion);
            return result;
        }

        Map<String, Object> updatedVersion = persistKnowledge(projectId, versionId, sessionId, userMessage,
                assistantAnswer, analysis, currentVersion, pendingResolution);

        result.put("status", "PERSISTED");
        result.put("saveLabel", "지식 저장: 저장됨");
        result.put("saved", true);
        result.put("knowledgeSaved", true);
        result.put("requiresClarification", false);
        result.put("pendingResolution", Map.of());
        result.put("message", savedMessage(analysis));
        result.put("version", updatedVersion);
        return result;
    }

    /**
     * 학습 페이지에서도 동일 정책을 쓰고 싶을 때 호출할 수 있도록 공개해 둡니다.
     */
    public Map<String, Object> analyzeAndCommitLearningKnowledge(UUID projectId,
                                                                  UUID versionId,
                                                                  UUID sessionId,
                                                                  String userMessage,
                                                                  String attachmentText,
                                                                  String attachmentFilename,
                                                                  Map<String, Object> currentVersion,
                                                                  List<Map<String, Object>> retrieved,
                                                                  Map<String, Object> pendingResolution) {
        String mergedInput = mergeUserAndAttachment(userMessage, attachmentText, attachmentFilename);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("pendingKnowledgeResolution", pendingResolution == null ? Map.of() : pendingResolution);
        state.put("source", "LEARNING_CONVERSATION");
        return analyzeAndCommitChatKnowledge(projectId, versionId, sessionId, mergedInput, "", state, retrieved, currentVersion);
    }

    private Map<String, Object> analyzeKnowledge(UUID projectId,
                                                 UUID versionId,
                                                 UUID sessionId,
                                                 String userMessage,
                                                 String assistantAnswer,
                                                 Map<String, Object> currentState,
                                                 List<Map<String, Object>> retrieved,
                                                 Map<String, Object> currentVersion,
                                                 Map<String, Object> pendingResolution) {
        String system = """
                당신은 HiddenBATHAuto의 고도화된 대화형 학습 엔진입니다.
                목표는 사용자의 모든 채팅을 해석해서, 실제 재검색 가능한 지식으로 저장할 수 있는 내용은 즉시 기존 지식과 병합하는 것입니다.

                절대 규칙:
                1. 사용자의 메시지를 단순 로그로만 두지 말고, 제품/시리즈/품목/사이즈/색상/옵션/가격/조건/예외/상담 흐름 지식으로 추출하세요.
                2. 질문만 있고 새로운 사실이 없으면 shouldPersist=false, hasKnowledge=false로 두세요. 단, 대화 로그는 별도 저장됩니다.
                3. 새 지식이 기존 지식과 충돌하거나, 이상하거나, 필수 조건이 부족해서 잘못 저장될 가능성이 있으면 절대 저장하지 말고 requiresClarification=true로 두세요.
                4. 충돌/부족/이상점이 있으면 conflicts/anomalies/missingCriticalInformation에 넣고, clarificationQuestion에 사용자가 선택/확정할 수 있는 한국어 질문을 작성하세요.
                5. pendingResolution이 있으면 사용자의 이번 메시지는 이전 확인 질문에 대한 답일 가능성이 큽니다. 이전 pending과 결합해서 충돌이 해소되었을 때만 저장하세요.
                6. 저장 가능한 경우에는 기존 processJson/pricingJson/constraintsJson을 보존하면서 누락 없이 병합한 최종 JSON을 반환하세요.
                7. 기존 지식을 임의로 삭제하지 마세요. 사용자가 '교체/삭제/폐기'라고 명확히 말한 경우에만 제거 또는 대체하세요.
                8. 근거가 없는 값을 만들어내지 마세요. 단, 사용자가 "가정하자", "아무 색이나 넣어줘", "규칙으로 해"라고 명시한 값은 테스트용 확정 지식으로 취급하고, 가능한 범위에서는 규칙을 펼쳐 명시 지식으로 저장하세요.
                9. 시리즈/품목/색상/문 구성처럼 규칙으로 파생되는 지식은 "C 시리즈 = c, cc, ccc, cccc, ccccc"처럼 나중에 검색어에 걸리도록 구체적인 문장으로 knowledgeText에 반복 작성하세요.
                10. 반드시 JSON 객체 하나만 반환하세요.

                반환 스키마:
                {
                  "intent": "LEARN_FACT|ANSWER_ONLY|RESOLVE_PENDING|ASK_CLARIFICATION|IGNORE_NOISE",
                  "hasKnowledge": true,
                  "shouldPersist": true,
                  "requiresClarification": false,
                  "clarificationQuestion": "",
                  "summary": "버전 전체 요약",
                  "knowledgeText": "벡터DB에 저장할 정규화된 한국어 지식 문장. 검색어에 걸리도록 시리즈/품목/조건을 명확히 반복해서 작성",
                  "processJson": {},
                  "pricingJson": {},
                  "constraintsJson": {},
                  "validationReportJson": {"status":"OK", "notes":[], "conflicts":[], "anomalies":[], "missingCriticalInformation":[]},
                  "extractedFacts": [],
                  "conflicts": [],
                  "anomalies": [],
                  "missingCriticalInformation": [],
                  "confidence": 0.0
                }
                """;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("versionId", versionId);
        payload.put("sessionId", sessionId);
        payload.put("currentVersion", compactVersion(currentVersion));
        payload.put("currentState", currentState == null ? Map.of() : currentState);
        payload.put("pendingResolution", pendingResolution == null ? Map.of() : pendingResolution);
        payload.put("retrievedChunks", retrieved == null ? List.of() : retrieved);
        payload.put("deterministicExpansionHint", domainExpansionService.buildExpansionText(userMessage));
        payload.put("userMessage", userMessage);
        payload.put("assistantAnswer", assistantAnswer);

        String raw = openAi.responseJson(system, RagJsonUtils.pretty(objectMapper, payload));
        JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, raw);
        Map<String, Object> result = RagJsonUtils.toMap(objectMapper, node.toString());
        normalizeAnalysisDefaults(result, currentVersion);
        applyDeterministicExpansionIfPresent(result, currentVersion, userMessage);
        return result;
    }

    private void normalizeAnalysisDefaults(Map<String, Object> analysis, Map<String, Object> currentVersion) {
        if (!analysis.containsKey("processJson")) {
            analysis.put("processJson", RagJsonUtils.toMap(objectMapper, currentVersion.get("process_json")));
        }
        if (!analysis.containsKey("pricingJson")) {
            analysis.put("pricingJson", RagJsonUtils.toMap(objectMapper, currentVersion.get("pricing_json")));
        }
        if (!analysis.containsKey("constraintsJson")) {
            analysis.put("constraintsJson", RagJsonUtils.toMap(objectMapper, currentVersion.get("constraints_json")));
        }
        if (!analysis.containsKey("validationReportJson")) {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("status", "OK");
            report.put("notes", List.of());
            report.put("conflicts", List.of());
            report.put("anomalies", List.of());
            report.put("missingCriticalInformation", List.of());
            analysis.put("validationReportJson", report);
        }
        if (!StringUtils.hasText(RagJsonUtils.stringValue(analysis, "summary"))) {
            analysis.put("summary", RagJsonUtils.safeString(currentVersion.get("summary")));
        }
    }

    @SuppressWarnings("unchecked")
    private void applyDeterministicExpansionIfPresent(Map<String, Object> analysis,
                                                      Map<String, Object> currentVersion,
                                                      String userMessage) {
        String expansionText = domainExpansionService.buildExpansionText(userMessage);
        if (!StringUtils.hasText(expansionText)) return;

        analysis.put("hasKnowledge", true);
        analysis.put("shouldPersist", true);
        analysis.put("requiresClarification", false);
        analysis.put("conflicts", List.of());
        analysis.put("anomalies", List.of());
        analysis.put("missingCriticalInformation", List.of());
        if (!analysis.containsKey("intent") || !StringUtils.hasText(RagJsonUtils.stringValue(analysis, "intent"))) {
            analysis.put("intent", "LEARN_FACT");
        }

        Map<String, Object> process = RagJsonUtils.toMap(objectMapper, analysis.get("processJson"));
        if (process.isEmpty()) {
            process = RagJsonUtils.toMap(objectMapper, currentVersion.get("process_json"));
        }
        analysis.put("processJson", domainExpansionService.mergeIntoProcessJson(process, userMessage));

        Map<String, Object> validation = RagJsonUtils.childMap(analysis, "validationReportJson");
        validation.put("status", "OK");
        validation.put("conflicts", List.of());
        validation.put("anomalies", List.of());
        validation.put("missingCriticalInformation", List.of());
        validation.put("assumptions", List.of("사용자가 테스트/가정으로 명시한 규칙형 지식"));
        analysis.put("validationReportJson", validation);

        String knowledgeText = RagJsonUtils.stringValue(analysis, "knowledgeText");
        if (!StringUtils.hasText(knowledgeText)) knowledgeText = "";
        if (!knowledgeText.contains("[규칙 확장 명시 지식]")) {
            knowledgeText = (knowledgeText + "\n\n" + expansionText).trim();
        }
        analysis.put("knowledgeText", knowledgeText);

        List<Object> facts = RagJsonUtils.childList(analysis, "extractedFacts");
        List<Object> nextFacts = new ArrayList<>(facts);
        nextFacts.add("규칙형 학습 내용을 명시 지식으로 확장 저장함");
        analysis.put("extractedFacts", nextFacts);

        String summary = RagJsonUtils.stringValue(analysis, "summary");
        String add = "시리즈 A~E, 품목/색상 생성 규칙, 문 분할 규칙, 추가옵션 규칙을 포함합니다.";
        if (!StringUtils.hasText(summary)) summary = add;
        else if (!summary.contains("시리즈 A~E")) summary = summary + " " + add;
        analysis.put("summary", summary);
    }

    private Map<String, Object> persistKnowledge(UUID projectId,
                                                 UUID versionId,
                                                 UUID sessionId,
                                                 String userMessage,
                                                 String assistantAnswer,
                                                 Map<String, Object> analysis,
                                                 Map<String, Object> currentVersion,
                                                 Map<String, Object> pendingResolution) {
        sanitizeValidationReport(analysis);

        Map<String, Object> updatedVersion = repository.updateVersionSynthesis(
                versionId,
                RagJsonUtils.stringValue(analysis, "summary"),
                analysis.get("processJson"),
                analysis.get("pricingJson"),
                analysis.get("constraintsJson"),
                analysis.get("validationReportJson")
        );

        String knowledgeText = RagJsonUtils.stringValue(analysis, "knowledgeText");
        if (!StringUtils.hasText(knowledgeText)) {
            knowledgeText = fallbackKnowledgeText(userMessage, analysis);
        }

        UUID docId = UUID.randomUUID();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "CHAT_AUTO_LEARNING");
        metadata.put("sessionId", sessionId);
        metadata.put("model", openAi.chatModel());
        metadata.put("embeddingModel", openAi.embeddingModel());
        metadata.put("savedAt", OffsetDateTime.now().toString());
        metadata.put("userMessage", userMessage);
        metadata.put("assistantAnswer", assistantAnswer);
        metadata.put("pendingResolutionBeforeSave", pendingResolution == null ? Map.of() : pendingResolution);
        metadata.put("extractedFacts", analysis.getOrDefault("extractedFacts", List.of()));
        metadata.put("analysis", analysis);

        repository.insertDocument(docId, projectId, versionId,
                "chat-auto-learning", "CHAT_AUTO_LEARNING",
                "채팅 자동 학습 지식", null, knowledgeText, metadata);
        insertEmbeddedChunks(docId, projectId, versionId, knowledgeText, metadata);

        // 대화형 학습/채팅에서 저장한 지식은 즉시 챗봇에서 조회되어야 하므로 현재 버전을 ACTIVE로 발행합니다.
        repository.publishVersion(projectId, versionId);
        return repository.findVersion(versionId).orElse(updatedVersion);
    }

    private void insertEmbeddedChunks(UUID docId,
                                      UUID projectId,
                                      UUID versionId,
                                      String knowledgeText,
                                      Map<String, Object> metadata) {
        List<String> chunks = splitKnowledgeText(knowledgeText);
        int no = 1;
        for (String chunk : chunks) {
            if (!StringUtils.hasText(chunk)) continue;
            String embedInput = RagJsonUtils.truncate(chunk, MAX_EMBED_TEXT_LENGTH);
            String vector = RagRepository.toVectorLiteral(openAi.embedding(embedInput));
            repository.insertChunk(UUID.randomUUID(), docId, projectId, versionId, no++,
                    "chat-auto-learning", chunk, metadata, vector);
        }
    }

    private List<String> splitKnowledgeText(String text) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) return chunks;
        String normalized = text.trim();
        if (normalized.length() <= MAX_CHUNK_LENGTH) {
            chunks.add(normalized);
            return chunks;
        }
        String[] paragraphs = normalized.split("\\n\\s*\\n|\\r?\\n");
        StringBuilder current = new StringBuilder();
        for (String p : paragraphs) {
            String piece = p == null ? "" : p.trim();
            if (!StringUtils.hasText(piece)) continue;
            if (current.length() + piece.length() + 2 > MAX_CHUNK_LENGTH && current.length() > 0) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (piece.length() > MAX_CHUNK_LENGTH) {
                for (int i = 0; i < piece.length(); i += MAX_CHUNK_LENGTH) {
                    chunks.add(piece.substring(i, Math.min(piece.length(), i + MAX_CHUNK_LENGTH)));
                }
            } else {
                if (current.length() > 0) current.append("\n");
                current.append(piece);
            }
        }
        if (current.length() > 0) chunks.add(current.toString().trim());
        return chunks;
    }

    @SuppressWarnings("unchecked")
    private boolean hasItems(Map<String, Object> analysis, String key) {
        Object value = analysis == null ? null : analysis.get(key);
        if (value instanceof List<?> list) return !list.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        if (value instanceof String s) return StringUtils.hasText(s);
        Map<String, Object> report = RagJsonUtils.childMap(analysis, "validationReportJson");
        Object nested = report.get(key);
        if (nested instanceof List<?> nestedList) return !nestedList.isEmpty();
        if (nested instanceof String nestedString) return StringUtils.hasText(nestedString);
        return false;
    }

    private Map<String, Object> buildPending(UUID projectId,
                                             UUID versionId,
                                             UUID sessionId,
                                             String userMessage,
                                             String assistantAnswer,
                                             Map<String, Object> analysis,
                                             List<Map<String, Object>> retrieved,
                                             Map<String, Object> previousPending) {
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("projectId", projectId);
        pending.put("versionId", versionId);
        pending.put("sessionId", sessionId);
        pending.put("createdAt", OffsetDateTime.now().toString());
        pending.put("userMessage", userMessage);
        pending.put("assistantAnswer", assistantAnswer);
        pending.put("previousPending", previousPending == null ? Map.of() : previousPending);
        pending.put("analysis", analysis);
        pending.put("retrieved", retrieved == null ? List.of() : retrieved);
        pending.put("clarificationQuestion", clarificationMessage(analysis));
        return pending;
    }

    private String clarificationMessage(Map<String, Object> analysis) {
        String question = RagJsonUtils.stringValue(analysis, "clarificationQuestion");
        if (StringUtils.hasText(question)) return question;
        return "기존 지식과 충돌되거나 그대로 저장하기에는 부족한 부분이 있습니다. 어떤 기준으로 저장해야 하는지 확정해 주세요.";
    }

    private String savedMessage(Map<String, Object> analysis) {
        String text = RagJsonUtils.stringValue(analysis, "savedMessage");
        if (StringUtils.hasText(text)) return text;
        List<Object> facts = RagJsonUtils.childList(analysis, "extractedFacts");
        if (!facts.isEmpty()) return "이번 대화에서 재사용 가능한 지식 " + facts.size() + "개를 분석·병합하여 벡터DB와 현재 버전에 반영했습니다.";
        return "이번 대화에서 재사용 가능한 지식을 분석·병합하여 벡터DB와 현재 버전에 반영했습니다.";
    }


    private boolean looksLikeKnowledgeQuestion(String message) {
        if (!StringUtils.hasText(message)) return false;
        String m = message.trim();
        if (m.contains("?")) return true;
        return containsAny(m,
                "알려줘", "말해봐", "나열", "뭐", "무엇", "어떤", "가능한", "답변해봐",
                "찾아", "조회", "설명해", "계산해", "가능해", "있지");
    }

    private boolean looksLikeLearningCommand(String message) {
        if (!StringUtils.hasText(message)) return false;
        String m = message.trim();
        return containsAny(m,
                "학습", "저장", "기억", "규칙", "가정하자", "추가해", "교체", "삭제", "수정",
                "맞아", "아니야", "기준", "범위", "경우", "이면", "일때", "가능하고", "가능함");
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (needle != null && text.contains(needle)) return true;
        }
        return false;
    }

    private String fallbackKnowledgeText(String userMessage, Map<String, Object> analysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("[채팅 자동 학습 지식]\n");
        if (StringUtils.hasText(userMessage)) sb.append(userMessage.trim()).append("\n");
        List<Object> facts = RagJsonUtils.childList(analysis, "extractedFacts");
        if (!facts.isEmpty()) {
            sb.append("\n[추출 사실]\n");
            for (Object fact : facts) sb.append("- ").append(fact).append("\n");
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private void sanitizeValidationReport(Map<String, Object> analysis) {
        Map<String, Object> report = RagJsonUtils.childMap(analysis, "validationReportJson");
        report.put("status", "OK");
        report.put("conflicts", List.of());
        report.put("anomalies", List.of());
        report.put("missingCriticalInformation", List.of());
        report.put("lastSavedAt", OffsetDateTime.now().toString());
        analysis.put("validationReportJson", report);
    }

    private String mergeUserAndAttachment(String userMessage, String attachmentText, String attachmentFilename) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(userMessage)) sb.append(userMessage.trim()).append("\n");
        if (StringUtils.hasText(attachmentText)) {
            sb.append("\n[첨부파일: ").append(StringUtils.hasText(attachmentFilename) ? attachmentFilename : "unknown").append("]\n");
            sb.append(attachmentText.trim());
        }
        return sb.toString().trim();
    }

    private Map<String, Object> compactVersion(Map<String, Object> version) {
        Map<String, Object> compact = new LinkedHashMap<>();
        compact.put("id", version.get("id"));
        compact.put("versionNo", version.get("version_no"));
        compact.put("title", version.get("title"));
        compact.put("summary", version.get("summary"));
        compact.put("processJson", RagJsonUtils.toMap(objectMapper, version.get("process_json")));
        compact.put("pricingJson", RagJsonUtils.toMap(objectMapper, version.get("pricing_json")));
        compact.put("constraintsJson", RagJsonUtils.toMap(objectMapper, version.get("constraints_json")));
        compact.put("validationReportJson", RagJsonUtils.toMap(objectMapper, version.get("validation_report_json")));
        return compact;
    }
}
