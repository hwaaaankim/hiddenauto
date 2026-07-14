package com.dev.HiddenBATHAuto.rag.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 채팅 세션의 생성·상태·문의 저장만 담당합니다.
 * 사용자 메시지의 해석과 답변 작성은 전부 RagKnowledgeInteractionService의 GPT Agent가 담당합니다.
 */
@Service
public class RagDynamicChatService {

    private final RagRepository repository;
    private final ObjectMapper objectMapper;
    private final RagKnowledgeInteractionService interactionService;

    public RagDynamicChatService(RagRepository repository,
                                 ObjectMapper objectMapper,
                                 RagKnowledgeInteractionService interactionService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.interactionService = interactionService;
    }

    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> start(UUID projectId, String userLabel) {
        if (projectId == null) throw new IllegalArgumentException("projectId가 필요합니다.");
        Map<String, Object> version = repository.findActiveVersion(projectId)
                .or(() -> repository.findLatestVersion(projectId))
                .orElseThrow(() -> new IllegalArgumentException("상담에 사용할 ACTIVE 또는 최신 버전이 없습니다."));
        UUID versionId = (UUID) version.get("id");
        UUID sessionId = UUID.randomUUID();

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("stepKey", "DOMAIN_SELECT");
        state.put("answers", new LinkedHashMap<>());
        state.put("createdAt", LocalDateTime.now().toString());

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("events", new ArrayList<>());

        Map<String, Object> session = repository.createChatSession(
                sessionId, projectId, versionId, userLabel, state, audit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("responseType", "SESSION_CREATED");
        result.put("answerSource", "NONE");
        result.put("sessionId", sessionId);
        result.put("session", session);
        result.put("state", state);
        result.put("version", version);
        return result;
    }

    /** Controller 또는 다른 서비스가 직접 호출해도 GPT DB Tool Agent만 사용합니다. */
    public Map<String, Object> message(UUID sessionId, String message) {
        return interactionService.routeChatInput(sessionId, message, List.of());
    }

    /** Controller 또는 다른 서비스가 직접 호출해도 GPT DB Tool Agent만 사용합니다. */
    public Map<String, Object> messageWithFiles(UUID sessionId,
                                                String message,
                                                List<MultipartFile> files) {
        return interactionService.routeChatInput(
                sessionId, message, files == null ? List.of() : files);
    }

    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> resetStep(UUID sessionId, String stepKey, String reason) {
        Map<String, Object> session = repository.findChatSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("채팅 세션을 찾을 수 없습니다."));
        Map<String, Object> state = RagJsonUtils.toMap(objectMapper, session.get("state_json"));
        Map<String, Object> audit = RagJsonUtils.toMap(objectMapper, session.get("audit_json"));
        String resolvedStepKey = StringUtils.hasText(stepKey) ? stepKey.trim() : "DOMAIN_SELECT";
        state.put("stepKey", resolvedStepKey);
        state.put("resetAt", LocalDateTime.now().toString());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("stepKey", resolvedStepKey);
        appendAudit(audit, "RESET_STEP", reason, detail);
        repository.updateChatSession(sessionId, state, audit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("responseType", "STEP_RESET");
        result.put("answerSource", "NONE");
        result.put("state", state);
        result.put("audit", audit);
        return result;
    }

    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> saveInquiry(UUID sessionId,
                                           String companyName,
                                           String customerName,
                                           String phone,
                                           String email,
                                           String memo) {
        Map<String, Object> session = repository.findChatSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("채팅 세션을 찾을 수 없습니다."));
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        Map<String, Object> state = RagJsonUtils.toMap(objectMapper, session.get("state_json"));
        Map<String, Object> audit = RagJsonUtils.toMap(objectMapper, session.get("audit_json"));
        List<Map<String, Object>> messages = repository.findChatMessages(sessionId);
        Map<String, Object> inquiry = repository.insertInquiry(
                UUID.randomUUID(), sessionId, projectId, versionId,
                companyName, customerName, phone, email, memo,
                state, audit, messages);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", true);
        result.put("inquiry", inquiry);
        result.put("responseType", "INQUIRY_SAVED");
        result.put("answerSource", "NONE");
        return result;
    }

    @SuppressWarnings("unchecked")
    private void appendAudit(Map<String, Object> audit,
                             String type,
                             String text,
                             Map<String, Object> detail) {
        Object value = audit.get("events");
        List<Map<String, Object>> events;
        if (value instanceof List<?> raw) {
            events = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() != null) copy.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    events.add(copy);
                }
            }
        } else {
            events = new ArrayList<>();
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("text", text == null ? "" : text);
        event.put("detail", detail == null ? Map.of() : detail);
        event.put("at", LocalDateTime.now().toString());
        events.add(event);
        audit.put("events", events);
    }
}
