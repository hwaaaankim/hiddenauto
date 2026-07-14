package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;

/**
 * 학습 세션과 운영 상태만 관리합니다.
 * 학습 의도 판단, 지식 검색, 충돌 확인, 저장/수정/삭제 계획, 사용자 답변은 GPT DB Tool Agent가 담당합니다.
 */
@Service
public class RagConversationalLearningService {

    private final RagRepository repository;
    private final RagKnowledgeInteractionService interactionService;

    @FunctionalInterface
    public interface RagLearningProgressListener {
        void update(String status, int progress, String message);
    }

    public RagConversationalLearningService(RagRepository repository,
                                            RagKnowledgeInteractionService interactionService) {
        this.repository = repository;
        this.interactionService = interactionService;
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
        Map<String, Object> session = repository.createLearningSession(
                sessionId, projectId, resolvedVersionId, resolvedTitle, resolvedTopic);
        repository.clearLearningSessionPendingResolution(sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("responseType", "SESSION_CREATED");
        result.put("answerSource", "NONE");
        result.put("session", session);
        result.put("sessionId", sessionId);
        return result;
    }

    public Map<String, Object> session(UUID sessionId) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId가 필요합니다.");
        return repository.findLearningSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("학습 세션을 찾을 수 없습니다."));
    }

    /**
     * 전처리에서 확인이 필요하다고 판정된 사실은 pending 상태로만 저장하고,
     * 실제 확인 질문 문장은 GPT가 DB 근거를 확인한 뒤 작성합니다.
     */
    @Transactional("ragTransactionManager")
    public Map<String, Object> recordPreprocessClarification(UUID sessionId,
                                                             String message,
                                                             String attachmentText,
                                                             String suggestedAnswer,
                                                             Object preprocessResult) {
        Map<String, Object> current = session(sessionId);
        UUID projectId = (UUID) current.get("project_id");
        UUID versionId = (UUID) current.get("version_id");

        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("projectId", projectId);
        pending.put("versionId", versionId);
        pending.put("status", "WAITING_PREPROCESS_CLARIFICATION");
        pending.put("userMessage", message == null ? "" : message);
        pending.put("attachmentTextPreview", RagJsonUtils.truncate(attachmentText, 8000));
        pending.put("preprocessResult", preprocessResult == null ? Map.of() : preprocessResult);
        repository.updateLearningSessionPendingResolution(sessionId, pending);

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("originalMessage", message == null ? "" : message);
        eventPayload.put("attachmentTextPreview", RagJsonUtils.truncate(attachmentText, 8000));
        eventPayload.put("preprocessResult", preprocessResult == null ? Map.of() : preprocessResult);
        eventPayload.put("sourceSuggestedAnswer", suggestedAnswer == null ? "" : suggestedAnswer);
        eventPayload.put("instruction", "자료 역할, 적용 범위, 추가/교체 여부 중 부족한 값을 기존 DB 지식과 비교한 뒤 최소한의 능동적 확인 질문을 직접 작성하십시오.");

        Map<String, Object> result = new LinkedHashMap<>(interactionService.routeLearningSystemEvent(
                sessionId, "PREPROCESS_CLARIFICATION_REQUIRED", eventPayload));
        result.put("intent", "PREPROCESS_CLARIFICATION");
        result.put("requiresClarification", true);
        result.put("shouldPersist", false);
        result.put("saveStatus", "PENDING_CLARIFICATION");
        result.put("saveMessage", "KNOWLEDGE_CHANGE_NOT_APPLIED");
        return result;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> resetKnowledge(UUID sessionId,
                                              String topic,
                                              String reason,
                                              boolean resetWholeVersion) {
        Map<String, Object> current = session(sessionId);
        UUID projectId = (UUID) current.get("project_id");
        UUID versionId = (UUID) current.get("version_id");
        String resolvedTopic = StringUtils.hasText(topic) ? topic.trim() : text(current.get("topic"));
        if (!StringUtils.hasText(resolvedTopic)) resolvedTopic = text(current.get("title"));
        return resetKnowledge(projectId, versionId, resolvedTopic, reason, resetWholeVersion, sessionId);
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> resetKnowledge(UUID projectId,
                                              UUID versionId,
                                              String topic,
                                              String reason,
                                              boolean resetWholeVersion) {
        if (projectId == null) throw new IllegalArgumentException("projectId가 필요합니다.");
        if (versionId == null) throw new IllegalArgumentException("versionId가 필요합니다.");
        return resetKnowledge(projectId, versionId, topic, reason, resetWholeVersion, null);
    }

    private Map<String, Object> resetKnowledge(UUID projectId,
                                               UUID versionId,
                                               String topic,
                                               String reason,
                                               boolean resetWholeVersion,
                                               UUID sessionId) {
        String resolvedReason = StringUtils.hasText(reason)
                ? reason.trim() : "사용자 요청에 따른 학습 초기화";
        Map<String, Object> resetResult = repository.resetKnowledge(
                projectId, versionId, topic, resetWholeVersion, resolvedReason);
        Map<String, Object> version = repository.findVersion(versionId).orElse(Map.of());
        if (sessionId != null) repository.clearLearningSessionPendingResolution(sessionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("responseType", "KNOWLEDGE_RESET_COMPLETED");
        result.put("answerSource", "NONE");
        result.put("saveStatus", "RESET_COMPLETED");
        result.put("saveMessage", "KNOWLEDGE_SCOPE_CLEARED");
        result.put("resetWholeVersion", resetWholeVersion);
        result.put("topic", topic);
        result.put("resetResult", resetResult);
        result.put("version", version);
        return result;
    }

    public Map<String, Object> talk(UUID sessionId,
                                    String message,
                                    String attachmentText,
                                    String attachmentFilename,
                                    boolean forceSave) {
        return talk(sessionId, message, attachmentText, attachmentFilename, forceSave, null);
    }

    /**
     * 업로드 파일은 텍스트로 모두 펼쳐 넣지 않고 Agent staging으로 전달합니다.
     * 원문은 rag_document/rag_chunk에 저장되고 GPT는 documentId 기반 도구로 필요한 부분만 읽습니다.
     */
    public Map<String, Object> talkWithFiles(UUID sessionId,
                                             String message,
                                             boolean forceSave,
                                             List<? extends MultipartFile> files,
                                             RagLearningProgressListener progressListener) {
        if (sessionId == null) throw new IllegalArgumentException("sessionId가 필요합니다.");
        List<MultipartFile> safeFiles = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).map(f -> (MultipartFile) f).toList();
        if (!StringUtils.hasText(message) && safeFiles.isEmpty()) {
            throw new IllegalArgumentException("메시지 또는 파일이 필요합니다.");
        }
        notifyProgress(progressListener, "PREPROCESSING", 15,
                "파일 원문을 Agent 문서 저장소에 staging하고 있습니다.");
        notifyProgress(progressListener, "GPT_INTERPRETING", 30,
                "GPT가 문서 메타와 DB 지식을 비교하고 있습니다.");
        Map<String, Object> result = interactionService.routeLearningInput(
                sessionId, message, forceSave, safeFiles);
        if ("TECHNICAL_ERROR".equals(text(result.get("responseType")))) {
            notifyProgress(progressListener, "FAILED", 100,
                    "GPT DB Tool Agent 실행이 완료되지 않았습니다.");
        } else {
            notifyProgress(progressListener, "COMPLETED", 100,
                    "GPT DB Tool Agent 파일 학습 처리가 완료되었습니다.");
        }
        return result;
    }

    /** 비동기 작업을 포함한 모든 학습 진입은 동일한 GPT DB Tool Agent를 사용합니다. */
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

        notifyProgress(progressListener, "PREPROCESSING", 15,
                "입력 내용을 GPT DB Tool Agent에 전달할 준비를 하고 있습니다.");
        String agentMessage = buildAgentMessage(message, attachmentText, attachmentFilename);
        notifyProgress(progressListener, "GPT_INTERPRETING", 30,
                "GPT가 DB 구조와 기존 지식을 조사하고 있습니다.");

        Map<String, Object> result = interactionService.routeLearningInput(
                sessionId, agentMessage, forceSave, List.of());
        if ("TECHNICAL_ERROR".equals(text(result.get("responseType")))) {
            notifyProgress(progressListener, "FAILED", 100,
                    "GPT DB Tool Agent 실행이 완료되지 않았습니다.");
        } else {
            notifyProgress(progressListener, "COMPLETED", 100,
                    "GPT DB Tool Agent 학습 처리가 완료되었습니다.");
        }
        return result;
    }

    private String buildAgentMessage(String message,
                                     String attachmentText,
                                     String attachmentFilename) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(message)) builder.append(message.trim());
        if (StringUtils.hasText(attachmentText)) {
            if (!builder.isEmpty()) builder.append("\n\n");
            builder.append("[[ATTACHMENT_TEXT]]\n");
            if (StringUtils.hasText(attachmentFilename)) {
                builder.append("filename: ").append(attachmentFilename.trim()).append('\n');
            }
            builder.append(attachmentText);
        }
        return builder.toString();
    }

    private void notifyProgress(RagLearningProgressListener listener,
                                String status,
                                int progress,
                                String message) {
        if (listener != null) listener.update(status, progress, message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
