package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagConversationalLearningService {

    private static final int RETRIEVE_LIMIT = 8;
    private static final int HISTORY_LIMIT = 18;

    private final RagRepository repository;
    private final OpenAiRagClient openAi;
    private final ObjectMapper objectMapper;
    private final RagDomainRuleExpansionService domainExpansionService;
    private final RagLearningCognitiveService cognitiveService;
    private final RagLearningChunkedCognitiveService chunkedCognitiveService;
    private final RagLearningGuardrailService guardrailService;

    @FunctionalInterface
    public interface RagLearningProgressListener {
        void update(String status, int progress, String message);
    }

    public RagConversationalLearningService(RagRepository repository,
                                            OpenAiRagClient openAi,
                                            ObjectMapper objectMapper,
                                            RagDomainRuleExpansionService domainExpansionService,
                                            RagLearningCognitiveService cognitiveService,
                                            RagLearningChunkedCognitiveService chunkedCognitiveService,
                                            RagLearningGuardrailService guardrailService) {
        this.repository = repository;
        this.openAi = openAi;
        this.objectMapper = objectMapper;
        this.domainExpansionService = domainExpansionService;
        this.cognitiveService = cognitiveService;
        this.chunkedCognitiveService = chunkedCognitiveService;
        this.guardrailService = guardrailService;
    }

    public Map<String, Object> start(UUID projectId, UUID versionId, String title) {
        return start(projectId, versionId, title, null);
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> start(UUID projectId, UUID versionId, String title, String topic) {
        if (projectId == null) throw new IllegalArgumentException("projectId가 필요합니다.");
        UUID resolvedVersionId = versionId;
        if (resolvedVersionId == null) {
            resolvedVersionId = repository.findActiveVersion(projectId)
                    .or(() -> repository.findLatestVersion(projectId))
                    .map(row -> (UUID) row.get("id"))
                    .orElseThrow(() -> new IllegalArgumentException("학습할 버전을 찾을 수 없습니다."));
        }

        UUID sessionId = UUID.randomUUID();
        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : "대화형 지식 학습";
        String resolvedTopic = StringUtils.hasText(topic) ? topic.trim() : resolvedTitle;
        Map<String, Object> session = repository.createLearningSession(sessionId, projectId, resolvedVersionId, resolvedTitle, resolvedTopic);
        repository.clearLearningSessionPendingResolution(sessionId);

        String welcome = "대화형 학습을 시작합니다. 규칙을 문장으로 설명해 주셔도 되고, 파일을 올릴 예정이면 먼저 말씀해 주셔도 됩니다. 기존 지식과 충돌되는 부분이 발견되면 저장하지 않고 먼저 확인 질문을 드리겠습니다.";
        repository.insertLearningMessage(UUID.randomUUID(), sessionId, projectId, resolvedVersionId, "assistant", welcome);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("session", session);
        result.put("sessionId", sessionId);
        result.put("answer", welcome);
        return result;
    }

    public Map<String, Object> session(UUID sessionId) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId가 필요합니다.");
        return repository.findLearningSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("학습 세션을 찾을 수 없습니다."));
    }

    /**
     * 학습 전처리 단계에서 자료 역할/적용 범위/추가·교체 여부가 부족한 경우 사용합니다.
     * 이 단계에서는 벡터 지식과 버전 JSON을 변경하지 않고, 대화 로그와 pending_resolution_json만 남깁니다.
     */
    @Transactional("ragTransactionManager")
    public Map<String, Object> recordPreprocessClarification(UUID sessionId,
                                                             String message,
                                                             String attachmentText,
                                                             String answer,
                                                             Object preprocessResult) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId가 필요합니다.");
        Map<String, Object> session = repository.findLearningSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("학습 세션을 찾을 수 없습니다."));
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");

        String storedUserMessage = buildStoredUserMessage(message, attachmentText, "전처리 확인 필요 업로드");
        repository.insertLearningMessage(UUID.randomUUID(), sessionId, projectId, versionId, "user", storedUserMessage);

        String resolvedAnswer = StringUtils.hasText(answer)
                ? answer
                : "업로드 자료를 저장하기 전에 적용 대상, 자료 역할, 추가/교체 여부를 확인해야 합니다.";

        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("projectId", projectId);
        pending.put("versionId", versionId);
        pending.put("status", "WAITING_PREPROCESS_CLARIFICATION");
        pending.put("userMessage", message);
        pending.put("attachmentTextPreview", RagJsonUtils.truncate(attachmentText, 8000));
        pending.put("preprocessResult", preprocessResult);
        repository.updateLearningSessionPendingResolution(sessionId, pending);

        repository.insertLearningMessage(UUID.randomUUID(), sessionId, projectId, versionId, "assistant", resolvedAnswer);
        repository.touchLearningSession(sessionId);

        Map<String, Object> version = repository.findVersion(versionId).orElse(Map.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("sessionId", sessionId);
        result.put("intent", "PREPROCESS_CLARIFICATION");
        result.put("requiresUpload", false);
        result.put("requiresClarification", true);
        result.put("shouldPersist", false);
        result.put("answer", resolvedAnswer);
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("intent", "PREPROCESS_CLARIFICATION");
        analysis.put("requiresClarification", true);
        analysis.put("shouldPersist", false);
        analysis.put("preprocessResult", preprocessResult == null ? Map.of() : preprocessResult);
        result.put("analysis", analysis);
        result.put("retrieved", List.of());
        result.put("version", version);
        result.put("saveStatus", "지식 저장: 저장 보류");
        result.put("saveMessage", "자료 역할/적용 범위/추가·교체 여부가 확정되지 않아 벡터DB와 구조화DB 저장을 보류했습니다.");
        return result;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> resetKnowledge(UUID sessionId, String topic, String reason, boolean resetWholeVersion) {
        Map<String, Object> session = session(sessionId);
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        String resolvedTopic = StringUtils.hasText(topic) ? topic.trim() : RagJsonUtils.stringValue(session, "topic");
        if (!StringUtils.hasText(resolvedTopic)) {
            resolvedTopic = RagJsonUtils.stringValue(session, "title");
        }
        return resetKnowledge(projectId, versionId, resolvedTopic, reason, resetWholeVersion, sessionId);
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> resetKnowledge(UUID projectId, UUID versionId, String topic, String reason, boolean resetWholeVersion) {
        if (projectId == null) throw new IllegalArgumentException("projectId가 필요합니다.");
        if (versionId == null) throw new IllegalArgumentException("versionId가 필요합니다.");
        return resetKnowledge(projectId, versionId, topic, reason, resetWholeVersion, null);
    }

    private Map<String, Object> resetKnowledge(UUID projectId, UUID versionId, String topic, String reason, boolean resetWholeVersion, UUID sessionId) {
        String resolvedReason = StringUtils.hasText(reason) ? reason.trim() : "사용자 요청에 따른 학습 초기화";
        Map<String, Object> resetResult = repository.resetKnowledge(projectId, versionId, topic, resetWholeVersion, resolvedReason);
        Map<String, Object> version = repository.findVersion(versionId).orElse(Map.of());
        String scoped = resetWholeVersion ? "현재 버전 전체" : "현재 학습 주제(" + (StringUtils.hasText(topic) ? topic : "제목 기준") + ")";
        String answer = scoped + "의 학습 데이터, 벡터 청크, 학습 대화, 업로드 자산을 초기화했습니다. 이후 같은 주제로 다시 대화하거나 파일을 올리면 새 지식으로 다시 구축됩니다.";
        // resetKnowledge는 선택 범위의 rag_learning_session까지 삭제할 수 있습니다.
        // 따라서 여기서 기존 sessionId로 메시지를 다시 넣지 않습니다.
        // 화면에서는 초기화 후 새 학습 세션을 다시 시작하도록 처리합니다.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("answer", answer);
        result.put("saveStatus", "지식 저장: 초기화");
        result.put("saveMessage", "요청한 범위의 학습 지식을 초기화했습니다.");
        result.put("resetResult", resetResult);
        result.put("version", version);
        return result;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> talk(UUID sessionId,
                                    String message,
                                    String attachmentText,
                                    String attachmentFilename,
                                    boolean forceSave) {
        return talk(sessionId, message, attachmentText, attachmentFilename, forceSave, null);
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> talk(UUID sessionId,
                                    String message,
                                    String attachmentText,
                                    String attachmentFilename,
                                    boolean forceSave,
                                    RagLearningProgressListener progressListener) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId가 필요합니다.");
        if (!StringUtils.hasText(message) && !StringUtils.hasText(attachmentText)) {
            throw new IllegalArgumentException("메시지 또는 첨부 텍스트가 필요합니다.");
        }

        Map<String, Object> session = repository.findLearningSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("학습 세션을 찾을 수 없습니다."));
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        Map<String, Object> version = repository.findVersion(versionId)
                .orElseThrow(() -> new IllegalArgumentException("학습 버전을 찾을 수 없습니다."));

        String storedUserMessage = buildStoredUserMessage(message, attachmentText, attachmentFilename);
        repository.insertLearningMessage(UUID.randomUUID(), sessionId, projectId, versionId, "user", storedUserMessage);
        notifyProgress(progressListener, "PREPROCESSING", 15, "사용자 입력을 학습 대화 로그에 저장했습니다.");

        Map<String, Object> pendingResolution = pendingResolution(session);
        boolean resolvingPendingConflict = !pendingResolution.isEmpty();

        /*
         * 입력 라우팅 분리:
         * - 학습/추가/교체/확정 입력은 기존처럼 계층형 학습 엔진으로 보냅니다.
         * - 단순 조회 질문은 절대 학습 노드를 만들지 않고, 현재 버전 JSON + 벡터 검색 결과로 답변만 생성합니다.
         *
         * 이전 버그 원인:
         * "걸레받이가 필요한건 어떤거야?" 같은 읽기 질문도 analyzeAndMerge()로 들어가
         * LEARN_TEXT / knowledgeTreeJson 생성 응답이 반환되었습니다.
         */
        List<Map<String, Object>> retrieved = retrieve(projectId, versionId, RagJsonUtils.truncate(storedUserMessage, 8000));
        List<Map<String, Object>> history = recentHistory(repository.findLearningMessages(sessionId), HISTORY_LIMIT);

        if (isReadOnlyKnowledgeQuestion(message, attachmentText, attachmentFilename, forceSave)) {
            notifyProgress(progressListener, "GPT_INTERPRETING", 30, "기존 학습 지식에서 답변을 찾고 있습니다.");
            Map<String, Object> questionResult = answerLearningQuestion(projectId, versionId, sessionId, message, version, retrieved, history);
            notifyProgress(progressListener, "COMPLETED", 100, "질문 답변 처리가 완료되었습니다. 지식 저장은 수행하지 않았습니다.");
            return questionResult;
        }

        notifyProgress(progressListener, "GPT_INTERPRETING", 30, "GPT가 입력을 해석하고 기존 지식과 비교하고 있습니다.");
        Map<String, Object> analysis = analyzeAndMerge(message, attachmentText, attachmentFilename, forceSave, version, retrieved, history, pendingResolution, progressListener);
        notifyProgress(progressListener, "VALIDATING", 62, "GPT 해석 결과를 서버 검증 규칙으로 정규화했습니다.");

        String answer = RagJsonUtils.stringValue(analysis, "answer");
        if (!StringUtils.hasText(answer)) {
            answer = "분석은 완료했지만 응답 문장을 만들지 못했습니다. 입력 내용을 조금 더 구체적으로 다시 보내주세요.";
        }

        boolean requiresClarification = requiresClarification(analysis);
        boolean hasConflict = hasUnresolvedConflict(analysis);
        if (hasConflict && !requiresClarification) {
            requiresClarification = true;
            analysis.put("requiresClarification", true);
        }

        boolean shouldPersistCandidate = RagJsonUtils.boolValue(analysis, "shouldPersist", false)
                || forceSave
                || StringUtils.hasText(attachmentText);
        boolean shouldPersist = shouldPersistCandidate && !requiresClarification && !hasConflict;

        Map<String, Object> updatedVersion = version;
        if (requiresClarification || hasConflict) {
            shouldPersist = false;
            Map<String, Object> pending = buildPendingResolution(projectId, versionId, message, attachmentText, attachmentFilename, analysis, retrieved, resolvingPendingConflict);
            repository.updateLearningSessionPendingResolution(sessionId, pending);
            answer = ensureNoPersistClarificationAnswer(answer, analysis);
        } else if (shouldPersist) {
            sanitizeValidationReportBeforePersist(analysis);
            notifyProgress(progressListener, "MERGING", 72, "확정 가능한 지식을 현재 버전 JSON에 병합하고 있습니다.");
            updatedVersion = persistKnowledge(projectId, versionId, RagJsonUtils.stringValue(session, "topic"), message, attachmentText, attachmentFilename, analysis, version, progressListener);
            repository.clearLearningSessionPendingResolution(sessionId);

            // 사용자가 확인 답변과 함께 "이 규칙으로 답변해봐"라고 한 경우, 저장 안내만 하지 말고
            // 방금 저장된 구조화 규칙으로 바로 답을 만들어 줍니다.
            String directAnswerAfterSave = directStructuredAnswer(message, updatedVersion);
            if (StringUtils.hasText(directAnswerAfterSave)) {
                answer = directAnswerAfterSave + "\n\n[저장됨] 대화 내용을 기존 지식과 병합해 버전 JSON과 벡터DB에 저장했습니다.";
            }
        } else if (resolvingPendingConflict) {
            repository.clearLearningSessionPendingResolution(sessionId);
        }

        repository.insertLearningMessage(UUID.randomUUID(), sessionId, projectId, versionId, "assistant", answer);
        repository.touchLearningSession(sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("sessionId", sessionId);
        result.put("intent", RagJsonUtils.stringValue(analysis, "intent"));
        result.put("requiresUpload", RagJsonUtils.boolValue(analysis, "requiresUpload", false));
        result.put("requiresClarification", requiresClarification || hasConflict);
        result.put("shouldPersist", shouldPersist);
        result.put("answer", answer);
        result.put("analysis", analysis);
        result.put("retrieved", retrieved);
        result.put("version", updatedVersion);
        if (requiresClarification || hasConflict) {
            result.put("saveStatus", "지식 저장: 저장 보류");
            result.put("saveMessage", "기존 지식과 충돌되거나 확인이 필요한 내용이 있어 아직 벡터DB에 저장하지 않았습니다. 답변으로 기준을 확정해 주세요.");
        } else if (shouldPersist) {
            result.put("saveStatus", "지식 저장: 저장됨");
            result.put("saveMessage", "이번 대화에서 재사용 가능한 지식을 분석·병합하여 벡터DB와 현재 버전에 반영했습니다.");
        } else {
            result.put("saveStatus", "지식 저장: 저장 생략");
            result.put("saveMessage", "대화 로그는 저장되었지만 재사용 가능한 새 지식이 아니어서 지식 저장은 생략했습니다.");
        }
        notifyProgress(progressListener, "COMPLETED", 100, "학습 대화 처리가 완료되었습니다.");
        return result;
    }


    private Map<String, Object> answerLearningQuestion(UUID projectId,
                                                       UUID versionId,
                                                       UUID sessionId,
                                                       String message,
                                                       Map<String, Object> version,
                                                       List<Map<String, Object>> retrieved,
                                                       List<Map<String, Object>> history) {
        String answer = directStructuredAnswer(message, version);
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("intent", "ANSWER_FROM_KNOWLEDGE");
        analysis.put("requiresUpload", false);
        analysis.put("requiresClarification", false);
        analysis.put("shouldPersist", false);
        analysis.put("conflicts", List.of());
        analysis.put("clarificationQuestions", List.of());

        if (!StringUtils.hasText(answer)) {
            answer = answerQuestionWithAi(message, version, retrieved, history);
        }
        if (!StringUtils.hasText(answer)) {
            answer = "현재 저장된 지식에서는 해당 질문에 대한 확정 규칙을 찾지 못했습니다. "
                    + "이 내용을 새 지식으로 학습시키려면 '학습해', '저장해', '추가 자료야', '이 규칙으로 확정해'처럼 입력해 주세요.";
        }
        analysis.put("answer", answer);

        repository.insertLearningMessage(UUID.randomUUID(), sessionId, projectId, versionId, "assistant", answer);
        repository.touchLearningSession(sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("sessionId", sessionId);
        result.put("intent", "ANSWER_FROM_KNOWLEDGE");
        result.put("requiresUpload", false);
        result.put("requiresClarification", false);
        result.put("shouldPersist", false);
        result.put("answer", answer);
        result.put("analysis", analysis);
        result.put("retrieved", retrieved);
        result.put("version", version);
        result.put("saveStatus", "지식 저장: 저장 생략");
        result.put("saveMessage", "질문 답변 대화는 저장되었지만 재사용 가능한 새 지식이 아니어서 지식 저장은 생략했습니다.");
        return result;
    }

    private boolean isReadOnlyKnowledgeQuestion(String message,
                                                String attachmentText,
                                                String attachmentFilename,
                                                boolean forceSave) {
        if (forceSave) return false;
        if (StringUtils.hasText(attachmentText) || StringUtils.hasText(attachmentFilename)) return false;
        if (!StringUtils.hasText(message)) return false;

        String raw = message.trim();
        String compact = raw.replaceAll("\\s+", "");

        // 명시적 학습/변경/교체/확정 명령은 질문처럼 보여도 학습 입력입니다.
        if (containsAny(compact,
                "학습해", "학습시켜", "저장해", "저장시켜", "기억해", "반영해", "추가해", "추가자료",
                "교체해", "새것으로교체", "비활성화", "활성자료", "확정해", "규칙으로확정",
                "수정해", "변경해", "업데이트해", "재학습", "다시학습", "초기화")) {
            return false;
        }

        // 긴 문서형 입력은 질문 부호가 섞여 있어도 학습 문서로 봅니다.
        if (raw.length() >= 450 && containsAny(raw, "학습 주제", "다음 내용을", "가격계산", "주문 프로세스", "자료 교체 규칙")) {
            return false;
        }

        // 한국어 조회 질문 패턴입니다.
        if (raw.contains("?")) return true;
        if (compact.endsWith("뭐야") || compact.endsWith("뭔가") || compact.endsWith("무엇이야")
                || compact.endsWith("어떤거야") || compact.endsWith("어떤것이야") || compact.endsWith("어느거야")
                || compact.endsWith("알려줘") || compact.endsWith("설명해줘") || compact.endsWith("말해줘")
                || compact.endsWith("가능해") || compact.endsWith("되나") || compact.endsWith("돼")) {
            return true;
        }
        return containsAny(compact,
                "어떤", "무엇", "뭐", "몇개", "몇개까지", "언제", "어디", "왜", "어떻게",
                "필요한건", "필요한것", "조건이뭐", "규칙이뭐", "가격이얼마", "계산해",
                "가능한", "가능여부", "알려줘", "설명해줘", "답해줘", "조회해줘");
    }

    private String directStructuredAnswer(String message, Map<String, Object> version) {
        if (!StringUtils.hasText(message) || version == null) return null;
        Map<String, Object> processJson = currentJson(version.get("process_json"));
        Map<String, Object> combined = new LinkedHashMap<>(processJson);
        combined.put("processJson", processJson);
        combined.put("pricingJson", currentJson(version.get("pricing_json")));
        combined.put("constraintsJson", currentJson(version.get("constraints_json")));
        combined.put("summary", version.get("summary"));
        combined.put("validationReportJson", currentJson(version.get("validation_report_json")));
        return domainExpansionService.tryAnswerFromExpandedRules(message, combined);
    }

    private String answerQuestionWithAi(String message,
                                        Map<String, Object> version,
                                        List<Map<String, Object>> retrieved,
                                        List<Map<String, Object>> history) {
        String system = """
                당신은 HiddenBATHAuto의 RAG 지식 답변 AI입니다.
                지금 입력은 '새 지식 저장'이 아니라 '기존 지식에 대한 질문'입니다.
                절대 학습 노드 개수, 저장 결과, 트리 생성 결과를 답하지 마세요.
                절대 모순 확인 질문으로 돌리지 마세요.
                현재 버전 JSON, retrievedChunks, conversationHistory에 근거가 있으면 사용자의 질문에 바로 답하세요.
                근거가 없을 때만 '현재 저장된 지식에서는 확인되지 않습니다'라고 말하세요.
                반드시 JSON 객체 하나만 반환하세요.
                반환 스키마: {"answer":"한국어 답변", "confidence":0.0}
                """;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currentVersion", compactVersion(version));
        payload.put("retrievedChunks", retrieved == null ? List.of() : retrieved);
        payload.put("conversationHistory", history == null ? List.of() : history);
        payload.put("userQuestion", message);
        try {
            String raw = openAi.responseJson(system, RagJsonUtils.pretty(objectMapper, payload));
            JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, raw);
            Map<String, Object> parsed = RagJsonUtils.toMap(objectMapper, node.toString());
            return RagJsonUtils.stringValue(parsed, "answer");
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> persistKnowledge(UUID projectId,
                                                 UUID versionId,
                                                 String learningTopic,
                                                 String message,
                                                 String attachmentText,
                                                 String attachmentFilename,
                                                 Map<String, Object> analysis,
                                                 Map<String, Object> currentVersion,
                                                 RagLearningProgressListener progressListener) {
        Object processJson = valueOrCurrent(analysis, "processJson", currentJson(currentVersion.get("process_json")));
        Object pricingJson = valueOrCurrent(analysis, "pricingJson", currentJson(currentVersion.get("pricing_json")));
        Object constraintsJson = valueOrCurrent(analysis, "constraintsJson", currentJson(currentVersion.get("constraints_json")));
        Object validationReportJson = valueOrCurrent(analysis, "validationReportJson", currentJson(currentVersion.get("validation_report_json")));
        String summary = RagJsonUtils.stringValue(analysis, "summary");
        if (!StringUtils.hasText(summary)) summary = String.valueOf(currentVersion.getOrDefault("summary", ""));

        Map<String, Object> updatedVersion = repository.updateVersionSynthesis(
                versionId,
                summary,
                processJson,
                pricingJson,
                constraintsJson,
                validationReportJson
        );

        String knowledgeText = RagJsonUtils.stringValue(analysis, "knowledgeText");
        if (!StringUtils.hasText(knowledgeText)) {
            knowledgeText = buildStoredUserMessage(message, attachmentText, attachmentFilename);
        }
        if (StringUtils.hasText(knowledgeText)) {
            UUID docId = UUID.randomUUID();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", "CONVERSATIONAL_LEARNING");
            metadata.put("attachmentFilename", attachmentFilename);
            metadata.put("model", openAi.chatModel());
            metadata.put("embeddingModel", openAi.embeddingModel());
            metadata.put("analysis", analysis);
            String topic = StringUtils.hasText(learningTopic) ? learningTopic.trim() : "conversation";
            repository.insertDocument(docId, projectId, versionId, topic, "CONVERSATION", "대화형 학습 지식", attachmentFilename, knowledgeText, metadata);
            notifyProgress(progressListener, "MERGING", 76, "계층형 지식 트리 노드를 DB에 저장하고 있습니다.");
            persistKnowledgeTreeNodes(projectId, versionId, docId, topic, analysis, message);
            notifyProgress(progressListener, "VECTOR_INDEXING", 84, "벡터 검색용 청크와 임베딩을 생성하고 있습니다.");
            insertEmbeddedChunks(docId, projectId, versionId, knowledgeText, metadata);
            insertKnowledgeTreeSearchChunks(docId, projectId, versionId, topic, analysis);
        }
        // 저장된 학습 지식은 바로 챗봇에서 조회되어야 하므로 현재 버전을 ACTIVE로 발행합니다.
        repository.publishVersion(projectId, versionId);
        return repository.findVersion(versionId).orElse(updatedVersion);
    }

    private Object valueOrCurrent(Map<String, Object> map, String key, Object current) {
        Object value = map == null ? null : map.get(key);
        return value == null ? current : value;
    }

    private Map<String, Object> currentJson(Object value) {
        return RagJsonUtils.toMap(objectMapper, value);
    }



    private void persistKnowledgeTreeNodes(UUID projectId,
                                           UUID versionId,
                                           UUID docId,
                                           String topic,
                                           Map<String, Object> analysis,
                                           String userMessage) {
        Map<String, Object> tree = RagJsonUtils.childMap(analysis, "knowledgeTreeJson");
        List<Object> rawNodes = RagJsonUtils.childList(tree, "nodes");
        if (rawNodes.isEmpty()) return;

        if (shouldReplaceExistingKnowledge(userMessage, analysis)) {
            repository.deactivateKnowledgeNodes(projectId, versionId, topic, "사용자 입력 또는 changePlan에 기존 지식 교체/재학습 요청이 감지되었습니다.");
        }

        Map<String, UUID> idByKey = new LinkedHashMap<>();
        for (Object raw : rawNodes) {
            Map<String, Object> node = RagJsonUtils.toMap(objectMapper, raw);
            String nodeKey = RagJsonUtils.stringValue(node, "nodeKey");
            if (!StringUtils.hasText(nodeKey)) nodeKey = "node-" + (idByKey.size() + 1);
            idByKey.putIfAbsent(nodeKey, UUID.randomUUID());
        }

        int sort = 0;
        for (Object raw : rawNodes) {
            Map<String, Object> node = RagJsonUtils.toMap(objectMapper, raw);
            String nodeKey = firstText(RagJsonUtils.stringValue(node, "nodeKey"), "node-" + (sort + 1));
            String parentKey = RagJsonUtils.stringValue(node, "parentKey");
            UUID nodeId = idByKey.get(nodeKey);
            UUID parentId = StringUtils.hasText(parentKey) ? idByKey.get(parentKey) : null;
            String nodeType = firstText(RagJsonUtils.stringValue(node, "nodeType"), "KNOWLEDGE_NODE");
            String title = firstText(RagJsonUtils.stringValue(node, "title"), nodeKey);
            String summary = RagJsonUtils.stringValue(node, "summary");
            String rawText = RagJsonUtils.stringValue(node, "rawText");
            Object structuredJson = node.getOrDefault("structuredJson", Map.of());
            int depth = estimateDepth(nodeKey, parentKey, rawNodes);
            int sortOrder = RagJsonUtils.intValue(node, "sortOrder", sort++);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", "ADAPTIVE_TREE_LEARNING");
            metadata.put("cognitiveEngine", RagJsonUtils.stringValue(analysis, "cognitiveEngine"));
            metadata.put("cognitiveEngineVersion", RagJsonUtils.stringValue(analysis, "cognitiveEngineVersion"));
            metadata.put("validationStatus", RagJsonUtils.stringValue(RagJsonUtils.childMap(analysis, "validationReportJson"), "status"));

            String interpretationStatus = firstText(RagJsonUtils.stringValue(node, "interpretationStatus"), "AI_PARSED");
            boolean retryable = RagJsonUtils.boolValue(node, "retryable", false)
                    || interpretationStatus.contains("PENDING")
                    || interpretationStatus.contains("NEEDS_AI_RETRY");
            String lastError = RagJsonUtils.stringValue(node, "retryReason");
            metadata.put("interpretationStatus", interpretationStatus);
            metadata.put("retryable", retryable);
            if (StringUtils.hasText(lastError)) metadata.put("lastError", lastError);

            repository.insertKnowledgeNode(
                    nodeId,
                    parentId,
                    projectId,
                    versionId,
                    docId,
                    topic,
                    nodeType,
                    nodeKey,
                    title,
                    summary,
                    rawText,
                    structuredJson,
                    metadata,
                    RagJsonUtils.boolValue(node, "active", true),
                    depth,
                    sortOrder,
                    interpretationStatus,
                    retryable,
                    0,
                    lastError,
                    null
            );
        }
    }

    private void insertKnowledgeTreeSearchChunks(UUID docId,
                                                 UUID projectId,
                                                 UUID versionId,
                                                 String topic,
                                                 Map<String, Object> analysis) {
        Map<String, Object> tree = RagJsonUtils.childMap(analysis, "knowledgeTreeJson");
        List<Object> rawNodes = RagJsonUtils.childList(tree, "nodes");
        if (rawNodes.isEmpty()) return;

        int chunkNo = 10000;
        for (Object raw : rawNodes) {
            Map<String, Object> node = RagJsonUtils.toMap(objectMapper, raw);
            String content = buildNodeSearchContent(node);
            if (!StringUtils.hasText(content)) continue;

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", "KNOWLEDGE_TREE_NODE");
            metadata.put("nodeKey", RagJsonUtils.stringValue(node, "nodeKey"));
            metadata.put("parentKey", RagJsonUtils.stringValue(node, "parentKey"));
            metadata.put("nodeType", RagJsonUtils.stringValue(node, "nodeType"));
            metadata.put("topic", topic);
            try {
                String vector = RagRepository.toVectorLiteral(openAi.embedding(content));
                repository.insertChunk(UUID.randomUUID(), docId, projectId, versionId, chunkNo++, "knowledge_node", content, metadata, vector);
            } catch (Exception e) {
                metadata.put("embeddingError", e.getMessage());
                repository.insertChunk(UUID.randomUUID(), docId, projectId, versionId, chunkNo++, "knowledge_node", content, metadata, zeroVectorLiteral());
            }
        }
    }

    private String buildNodeSearchContent(Map<String, Object> node) {
        StringBuilder sb = new StringBuilder();
        appendIfText(sb, "노드유형", RagJsonUtils.stringValue(node, "nodeType"));
        appendIfText(sb, "제목", RagJsonUtils.stringValue(node, "title"));
        appendIfText(sb, "요약", RagJsonUtils.stringValue(node, "summary"));
        appendIfText(sb, "원문", RagJsonUtils.stringValue(node, "rawText"));
        Object structured = node.get("structuredJson");
        if (structured != null) {
            appendIfText(sb, "구조화JSON", RagJsonUtils.truncate(RagJsonUtils.pretty(objectMapper, structured), 2400));
        }
        return RagJsonUtils.truncate(sb.toString(), 3600);
    }

    private void appendIfText(StringBuilder sb, String label, String value) {
        if (!StringUtils.hasText(value)) return;
        if (sb.length() > 0) sb.append("\n");
        sb.append("[").append(label).append("]\n").append(value.trim());
    }

    private boolean shouldReplaceExistingKnowledge(String userMessage, Map<String, Object> analysis) {
        String message = userMessage == null ? "" : userMessage.replace(" ", "");
        if (message.contains("교체") || message.contains("새것으로") || message.contains("재학습") || message.contains("다시학습")) {
            return true;
        }
        Map<String, Object> validation = RagJsonUtils.childMap(analysis, "validationReportJson");
        for (Object item : RagJsonUtils.childList(validation, "changePlan")) {
            String text = String.valueOf(item).toUpperCase();
            if (text.contains("REPLACE") || text.contains("UPDATE_REQUEST_DETECTED")) return true;
        }
        return false;
    }

    private int estimateDepth(String nodeKey, String parentKey, List<Object> rawNodes) {
        if (!StringUtils.hasText(parentKey)) return 0;
        Map<String, String> parentByKey = new LinkedHashMap<>();
        for (Object raw : rawNodes) {
            Map<String, Object> node = RagJsonUtils.toMap(objectMapper, raw);
            String key = RagJsonUtils.stringValue(node, "nodeKey");
            String parent = RagJsonUtils.stringValue(node, "parentKey");
            if (StringUtils.hasText(key)) parentByKey.put(key, parent);
        }
        int depth = 0;
        String cursor = nodeKey;
        while (StringUtils.hasText(cursor) && parentByKey.containsKey(cursor) && depth < 20) {
            String parent = parentByKey.get(cursor);
            if (!StringUtils.hasText(parent)) break;
            depth++;
            cursor = parent;
        }
        return depth;
    }

    private void insertEmbeddedChunks(UUID docId, UUID projectId, UUID versionId, String text, Map<String, Object> metadata) {
        List<String> chunks = splitChunks(text, 2200);
        int chunkNo = 1;
        for (String chunk : chunks) {
            try {
                String vector = RagRepository.toVectorLiteral(openAi.embedding(chunk));
                repository.insertChunk(UUID.randomUUID(), docId, projectId, versionId, chunkNo++, "conversation", chunk, metadata, vector);
            } catch (Exception e) {
                Map<String, Object> meta = new LinkedHashMap<>(metadata);
                meta.put("embeddingError", e.getMessage());
                repository.insertChunk(UUID.randomUUID(), docId, projectId, versionId, chunkNo++, "conversation", chunk, meta, zeroVectorLiteral());
            }
        }
    }

    private List<Map<String, Object>> retrieve(UUID projectId, UUID versionId, String query) {
        try {
            String vector = RagRepository.toVectorLiteral(openAi.embedding(query));
            return repository.searchChunks(projectId, versionId, vector, RETRIEVE_LIMIT);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("retrievalError", e.getMessage());
            return List.of(error);
        }
    }

    private Map<String, Object> analyzeAndMerge(String message,
                                                String attachmentText,
                                                String attachmentFilename,
                                                boolean forceSave,
                                                Map<String, Object> version,
                                                List<Map<String, Object>> retrieved,
                                                List<Map<String, Object>> history,
                                                Map<String, Object> pendingResolution,
                                                RagLearningProgressListener progressListener) {
        String storedInput = buildStoredUserMessage(message, attachmentText, attachmentFilename);
        String expansionHint = domainExpansionService.buildExpansionText(storedInput);

        Map<String, Object> analysis;
        if (chunkedCognitiveService.shouldUseChunking(message, attachmentText)) {
            notifyProgress(progressListener, "GPT_INTERPRETING", 31, "입력이 길어 계층형 청크 해석으로 전환했습니다.");
            analysis = chunkedCognitiveService.interpret(
                    version,
                    retrieved,
                    history,
                    pendingResolution,
                    message,
                    attachmentFilename,
                    attachmentText,
                    expansionHint,
                    forceSave,
                    progressListener
            );
        } else {
            analysis = cognitiveService.interpret(
                    version,
                    retrieved,
                    history,
                    pendingResolution,
                    message,
                    attachmentFilename,
                    attachmentText,
                    expansionHint,
                    forceSave
            );
            if (isGptInterpretationError(analysis)) {
                notifyProgress(progressListener, "GPT_INTERPRETING", 34, "일반 GPT 해석이 제한 시간 안에 끝나지 않아 계층형 청크 해석으로 재시도합니다.");
                analysis = chunkedCognitiveService.interpret(
                        version,
                        retrieved,
                        history,
                        pendingResolution,
                        message,
                        attachmentFilename,
                        attachmentText,
                        expansionHint,
                        forceSave,
                        progressListener
                );
            }
        }

        analysis = guardrailService.validateAndNormalize(analysis, version, forceSave);

        /*
         * 기존 테스트용 규칙 확장 기능은 유지합니다.
         * 단, 중심 판단은 GPT가 끝낸 뒤에 서버 보조 확장으로만 사용합니다.
         */
        applyDeterministicExpansionIfPresent(analysis, version, storedInput);
        analysis = guardrailService.validateAndNormalize(analysis, version, forceSave);
        return analysis;
    }

    private boolean isGptInterpretationError(Map<String, Object> analysis) {
        if (analysis == null || analysis.isEmpty()) return false;
        Map<String, Object> report = RagJsonUtils.childMap(analysis, "validationReportJson");
        String status = RagJsonUtils.stringValue(report, "status");
        if ("GPT_INTERPRETATION_ERROR".equalsIgnoreCase(status)) return true;
        String answer = RagJsonUtils.stringValue(analysis, "answer");
        if (StringUtils.hasText(answer) && answer.contains("GPT 해석 단계에서 오류")) return true;
        String engine = RagJsonUtils.stringValue(analysis, "cognitiveEngine");
        return "RagLearningCognitiveService".equals(engine)
                && RagJsonUtils.boolValue(analysis, "requiresClarification", false)
                && !RagJsonUtils.boolValue(analysis, "shouldPersist", false)
                && StringUtils.hasText(answer)
                && answer.contains("오류");
    }

    private void applyDeterministicExpansionIfPresent(Map<String, Object> analysis,
                                                      Map<String, Object> version,
                                                      String inputText) {
        String expansionText = domainExpansionService.buildExpansionText(inputText);
        if (!StringUtils.hasText(expansionText)) return;
        if (requiresClarification(analysis) || hasUnresolvedConflict(analysis)) {
            return;
        }

        analysis.put("shouldPersist", true);
        analysis.put("intent", "KNOWLEDGE_UPDATE");

        Map<String, Object> process = RagJsonUtils.toMap(objectMapper, analysis.get("processJson"));
        if (process.isEmpty()) process = currentJson(version.get("process_json"));
        analysis.put("processJson", domainExpansionService.mergeIntoProcessJson(process, inputText));

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

        String answer = RagJsonUtils.stringValue(analysis, "answer");
        String savedNotice = "학습 내용을 규칙형 지식으로 확장해 저장합니다. 예를 들어 C 시리즈는 c, cc, ccc, cccc, ccccc 품목과 각 품목 반복 수만큼의 색상 규칙으로 저장됩니다.";
        if (!StringUtils.hasText(answer)) analysis.put("answer", savedNotice);
        else if (!answer.contains("규칙형 지식")) analysis.put("answer", answer + "\n\n" + savedNotice);

        String summary = RagJsonUtils.stringValue(analysis, "summary");
        String add = "시리즈 A~E, 품목/색상 생성 규칙, 문 분할 규칙, 추가옵션 규칙을 포함합니다.";
        if (!StringUtils.hasText(summary)) summary = add;
        else if (!summary.contains("시리즈 A~E")) summary = summary + " " + add;
        analysis.put("summary", summary);
    }

    private void normalizeAnalysis(Map<String, Object> map, Map<String, Object> version) {
        if (!StringUtils.hasText(RagJsonUtils.stringValue(map, "summary"))) {
            map.put("summary", String.valueOf(version.getOrDefault("summary", "")));
        }
        map.putIfAbsent("processJson", currentJson(version.get("process_json")));
        map.putIfAbsent("pricingJson", currentJson(version.get("pricing_json")));
        map.putIfAbsent("constraintsJson", currentJson(version.get("constraints_json")));
        map.putIfAbsent("validationReportJson", currentJson(version.get("validation_report_json")));
        if (!map.containsKey("shouldPersist")) map.put("shouldPersist", false);
        if (!map.containsKey("requiresUpload")) map.put("requiresUpload", false);
        if (!map.containsKey("requiresClarification")) map.put("requiresClarification", false);
        map.putIfAbsent("conflicts", List.of());
        map.putIfAbsent("clarificationQuestions", List.of());
    }

    private boolean requiresClarification(Map<String, Object> analysis) {
        if (RagJsonUtils.boolValue(analysis, "requiresClarification", false)) return true;
        return !RagJsonUtils.childList(analysis, "clarificationQuestions").isEmpty();
    }

    private boolean hasUnresolvedConflict(Map<String, Object> analysis) {
        if (!RagJsonUtils.childList(analysis, "conflicts").isEmpty()) return true;
        Map<String, Object> validation = RagJsonUtils.childMap(analysis, "validationReportJson");
        return !RagJsonUtils.childList(validation, "conflicts").isEmpty();
    }

    private Map<String, Object> buildPendingResolution(UUID projectId,
                                                       UUID versionId,
                                                       String message,
                                                       String attachmentText,
                                                       String attachmentFilename,
                                                       Map<String, Object> analysis,
                                                       List<Map<String, Object>> retrieved,
                                                       boolean wasAlreadyPending) {
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("projectId", projectId);
        pending.put("versionId", versionId);
        pending.put("status", "WAITING_USER");
        pending.put("wasAlreadyPending", wasAlreadyPending);
        pending.put("userMessage", message);
        pending.put("attachmentFilename", attachmentFilename);
        pending.put("attachmentTextPreview", RagJsonUtils.truncate(attachmentText, 8000));
        pending.put("conflicts", RagJsonUtils.childList(analysis, "conflicts"));
        pending.put("clarificationQuestions", RagJsonUtils.childList(analysis, "clarificationQuestions"));
        pending.put("analysis", analysis);
        pending.put("retrieved", retrieved);
        return pending;
    }

    private String ensureNoPersistClarificationAnswer(String answer, Map<String, Object> analysis) {
        String prefix = "기존 지식과 충돌되거나 확인이 필요한 부분이 있어 아직 저장하지 않았습니다.\n";
        if (StringUtils.hasText(answer)) {
            if (answer.contains("저장하지")) return answer;
            return prefix + answer;
        }
        List<Object> questions = RagJsonUtils.childList(analysis, "clarificationQuestions");
        if (!questions.isEmpty()) {
            return prefix + "아래 내용을 확인해 주세요.\n- " + String.join("\n- ", questions.stream().map(String::valueOf).toList());
        }
        return prefix + "어떤 기준으로 확정할지 알려주세요.";
    }

    private void sanitizeValidationReportBeforePersist(Map<String, Object> analysis) {
        Map<String, Object> validation = RagJsonUtils.childMap(analysis, "validationReportJson");
        validation.remove("conflicts");
        validation.putIfAbsent("warnings", List.of());
        validation.putIfAbsent("assumptions", List.of());
        validation.putIfAbsent("resolvedClarifications", List.of());
        analysis.put("validationReportJson", validation);
        analysis.put("conflicts", List.of());
        analysis.put("clarificationQuestions", List.of());
        analysis.put("requiresClarification", false);
    }

    private Map<String, Object> pendingResolution(Map<String, Object> session) {
        String status = RagJsonUtils.stringValue(session, "resolution_status");
        if (!"WAITING_USER".equalsIgnoreCase(status)) return new LinkedHashMap<>();
        return RagJsonUtils.toMap(objectMapper, session.get("pending_resolution_json"));
    }

    private Map<String, Object> compactVersion(Map<String, Object> version) {
        Map<String, Object> compact = new LinkedHashMap<>();
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

    private List<Map<String, Object>> recentHistory(List<Map<String, Object>> messages, int limit) {
        if (messages == null || messages.isEmpty()) return List.of();
        int from = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(from, messages.size()));
    }

    private String buildStoredUserMessage(String message, String attachmentText, String attachmentFilename) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(message)) sb.append(message.trim());
        if (StringUtils.hasText(attachmentText)) {
            sb.append("\n\n[첨부파일: ").append(StringUtils.hasText(attachmentFilename) ? attachmentFilename : "이름 없음").append("]\n");
            sb.append(RagJsonUtils.truncate(attachmentText, 60_000));
        }
        return sb.toString().trim();
    }

    private boolean isUploadRequestWithoutFile(String message, String attachmentText) {
        if (StringUtils.hasText(attachmentText)) return false;
        if (!StringUtils.hasText(message)) return false;
        String m = message.replace(" ", "");
        boolean mentionsUpload = m.contains("업로드") || m.contains("파일") || m.contains("엑셀") || m.toLowerCase().contains("xlsx");
        boolean future = m.contains("할테니") || m.contains("할테니까") || m.contains("올릴") || m.contains("올려") || m.contains("올리면") || m.contains("보낼");
        boolean asksAnalysis = m.contains("분석") || m.contains("봐") || m.contains("검토");
        return mentionsUpload && (future || asksAnalysis);
    }

    private Map<String, Object> uploadRequestResponse(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("intent", "UPLOAD_REQUEST");
        map.put("requiresUpload", true);
        map.put("requiresClarification", false);
        map.put("shouldPersist", false);
        map.put("answer", "알겠습니다. 엑셀 파일을 업로드해 주시면 기존에 저장된 지식과 비교해서 품목/옵션/가격 규칙을 분석하겠습니다. 아직 파일 내용이 없으므로 분석 결과로 저장하지는 않겠습니다.");
        map.put("summary", "");
        map.put("processJson", Map.of());
        map.put("pricingJson", Map.of());
        map.put("constraintsJson", Map.of());
        map.put("validationReportJson", Map.of("warnings", List.of("파일 업로드 대기"), "assumptions", List.of(), "resolvedClarifications", List.of()));
        map.put("knowledgeText", "");
        map.put("confidence", 1.0);
        return map;
    }

    private List<String> splitChunks(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) return chunks;
        String normalized = text.trim();
        for (int start = 0; start < normalized.length(); start += maxLength) {
            int end = Math.min(normalized.length(), start + maxLength);
            chunks.add(normalized.substring(start, end));
        }
        return chunks;
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



    private String firstText(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return "";
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (needle != null && text.contains(needle)) return true;
        }
        return false;
    }


    private void notifyProgress(RagLearningProgressListener listener, String status, int progress, String message) {
        if (listener == null) return;
        try {
            listener.update(status, progress, message);
        } catch (Exception ignored) {
            // 진행상태 저장 실패가 실제 학습 저장을 방해하면 안 됩니다.
        }
    }

    private String zeroVectorLiteral() {
        List<String> zeros = new ArrayList<>(1536);
        for (int i = 0; i < 1536; i++) zeros.add("0");
        return "[" + String.join(",", zeros) + "]";
    }
}
