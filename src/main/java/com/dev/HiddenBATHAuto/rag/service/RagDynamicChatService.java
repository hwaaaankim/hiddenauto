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

@Service
public class RagDynamicChatService {

    private final RagRepository repository;
    private final OpenAiRagClient ai;
    private final RagKnowledgeIngestionService ingestionService;
    private final RagKnowledgeMemoryService memoryService;
    private final RagDialogRuleService dialogRuleService;
    private final ObjectMapper objectMapper;

    public RagDynamicChatService(RagRepository repository,
                                 OpenAiRagClient ai,
                                 RagKnowledgeIngestionService ingestionService,
                                 RagKnowledgeMemoryService memoryService,
                                 RagDialogRuleService dialogRuleService,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.ai = ai;
        this.ingestionService = ingestionService;
        this.memoryService = memoryService;
        this.dialogRuleService = dialogRuleService;
        this.objectMapper = objectMapper;
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

        Map<String, Object> session = repository.createChatSession(sessionId, projectId, versionId, userLabel, state, audit);
        String greeting = "상담을 시작합니다. 원하시는 제품, 설치 위치, 사이즈, 색상, 수량을 알려주시면 가능한 조합과 가격을 확인해 드리겠습니다. 관련 파일이 있으면 이 대화창에 드래그앤드랍해 주세요.";
        repository.insertChatMessage(UUID.randomUUID(), sessionId, "assistant", greeting, state, List.of());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("session", session);
        result.put("state", state);
        result.put("answer", greeting);
        result.put("version", version);
        return result;
    }

    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> message(UUID sessionId, String message) {
        return messageInternal(sessionId, message, List.of());
    }

    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> messageWithFiles(UUID sessionId, String message, List<MultipartFile> files) {
        return messageInternal(sessionId, message, files == null ? List.of() : files);
    }

    @Transactional(transactionManager = "ragTransactionManager")
    protected Map<String, Object> messageInternal(UUID sessionId, String message, List<MultipartFile> files) {
        Map<String, Object> session = repository.findChatSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("채팅 세션을 찾을 수 없습니다."));
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        Map<String, Object> version = repository.findVersion(versionId)
                .orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다."));

        String cleanMessage = message == null ? "" : message.trim();
        List<MultipartFile> safeFiles = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (!StringUtils.hasText(cleanMessage) && safeFiles.isEmpty()) {
            throw new IllegalArgumentException("메시지 또는 파일이 필요합니다.");
        }

        Map<String, Object> state = RagJsonUtils.toMap(objectMapper, session.get("state_json"));
        Map<String, Object> audit = RagJsonUtils.toMap(objectMapper, session.get("audit_json"));
        List<Map<String, Object>> recentMessages = repository.findRecentChatMessages(sessionId, 12);
        List<Map<String, Object>> retrieved = retrieve(projectId, versionId, cleanMessage, 10);
        List<Map<String, Object>> dialogRules = dialogRuleService.findActiveRules(projectId, versionId, null);
        if (!dialogRules.isEmpty()) {
            Map<String, Object> ruleContext = new LinkedHashMap<>();
            ruleContext.put("source_type", "DIALOG_RULES");
            ruleContext.put("description", "학습 화면에서 대화로 저장된 주문 질문흐름/조건/검증/가격식 규칙입니다. 상담 답변과 다음 질문 결정에 우선 사용해야 합니다.");
            ruleContext.put("rules", dialogRules);
            retrieved = new ArrayList<>(retrieved);
            retrieved.add(0, ruleContext);
        }

        Map<String, Object> fileAnalysis = new LinkedHashMap<>();
        if (!safeFiles.isEmpty()) {
            fileAnalysis = ingestionService.analyzeChatFilesOnly(projectId, versionId, sessionId, cleanMessage, safeFiles);
        }

        String answer = generateChatAnswer(version, state, recentMessages, retrieved, cleanMessage, fileAnalysis);
        Map<String, Object> newState = updateState(state, cleanMessage, answer, fileAnalysis);
        appendAudit(audit, "MESSAGE", cleanMessage, fileAnalysis);

        String renderedUserContent = renderUserContent(cleanMessage, safeFiles, fileAnalysis);

        Map<String, Object> memory = memoryService.analyzeAndCommitChatKnowledge(
                projectId,
                versionId,
                sessionId,
                renderedUserContent,
                answer,
                newState,
                retrieved,
                version
        );
        if (Boolean.TRUE.equals(memory.get("requiresClarification"))) {
            newState.put("pendingKnowledgeResolution", memory.getOrDefault("pendingResolution", Map.of()));
        } else {
            newState.remove("pendingKnowledgeResolution");
        }
        newState.put("lastKnowledgeSaveStatus", memory);

        repository.insertChatMessage(UUID.randomUUID(), sessionId, "user", renderedUserContent, state, retrieved);
        repository.insertChatMessage(UUID.randomUUID(), sessionId, "assistant", answer, newState, retrieved);
        repository.updateChatSession(sessionId, newState, audit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("state", newState);
        result.put("audit", audit);
        result.put("retrieved", retrieved);
        result.put("fileAnalysis", fileAnalysis);
        result.put("memory", memory);
        result.put("saveStatus", memory.getOrDefault("saveLabel", "지식 저장: 저장 생략"));
        result.put("saveMessage", memory.getOrDefault("message", "대화 로그는 저장되었지만 재사용 가능한 새 지식이 아니어서 지식 저장은 생략했습니다."));
        result.put("version", memory.getOrDefault("version", version));
        return result;
    }

    @Transactional(transactionManager = "ragTransactionManager")
    public Map<String, Object> resetStep(UUID sessionId, String stepKey, String reason) {
        Map<String, Object> session = repository.findChatSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("채팅 세션을 찾을 수 없습니다."));
        Map<String, Object> state = RagJsonUtils.toMap(objectMapper, session.get("state_json"));
        Map<String, Object> audit = RagJsonUtils.toMap(objectMapper, session.get("audit_json"));
        state.put("stepKey", StringUtils.hasText(stepKey) ? stepKey : "DOMAIN_SELECT");
        state.put("resetAt", LocalDateTime.now().toString());
        appendAudit(audit, "RESET_STEP", reason, Map.of("stepKey", stepKey));
        repository.updateChatSession(sessionId, state, audit);
        String answer = "선택한 단계부터 다시 진행하겠습니다. 필요한 제품 조건을 다시 알려주세요.";
        repository.insertChatMessage(UUID.randomUUID(), sessionId, "assistant", answer, state, List.of());
        return Map.of("answer", answer, "state", state, "audit", audit);
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
                state, audit, messages
        );
        return Map.of("saved", true, "inquiry", inquiry, "message", "문의가 저장되었습니다.");
    }

    private String generateChatAnswer(Map<String, Object> version,
                                      Map<String, Object> state,
                                      List<Map<String, Object>> recentMessages,
                                      List<Map<String, Object>> retrieved,
                                      String message,
                                      Map<String, Object> fileAnalysis) {
        String systemPrompt = """
                당신은 HiddenBATH 제품 발주 및 가격계산 상담 챗봇입니다.
                반드시 학습된 제품명/코드/사이즈/색상/가격 조합과 검색 근거를 우선 사용하세요.
                검색 근거 안에 DIALOG_RULES가 있으면 그것은 대화 학습으로 저장된 주문 질문흐름/조건부 질문/입력 검증/가격식 규칙입니다.
                주문 상담 중에는 DIALOG_RULES를 최우선으로 사용해서 현재까지 받은 답변에 따라 다음에 물어볼 질문을 결정하세요.
                가격은 학습된 규칙과 근거가 있을 때만 계산하고, 누락된 치수/옵션/조건이 있으면 추정하지 말고 확인 질문을 하세요.
                모르는 제품, 가격 누락, 메시지와 파일 관계가 불명확한 경우 추정하지 말고 확인 질문을 하세요.
                고객 상담 첨부 파일도 사용자의 메시지와 함께 해석하되, 전역 지식으로 저장할지는 별도 학습 분석 결과에 따릅니다.
                답변은 한국어 존댓말로 간결하지만 계산 근거가 보이게 작성하세요.
                """;
        String userPrompt = """
                [현재 버전 요약]
                %s

                [프로세스 JSON]
                %s

                [가격 JSON]
                %s

                [제약 JSON]
                %s

                [현재 상담 상태]
                %s

                [최근 대화]
                %s

                [검색된 근거]
                %s

                [첨부 파일 분석]
                %s

                [사용자 메시지]
                %s
                """.formatted(
                RagJsonUtils.truncate(String.valueOf(version.get("summary")), 4_000),
                RagJsonUtils.truncate(String.valueOf(version.get("process_json")), 10_000),
                RagJsonUtils.truncate(String.valueOf(version.get("pricing_json")), 12_000),
                RagJsonUtils.truncate(String.valueOf(version.get("constraints_json")), 8_000),
                RagJsonUtils.pretty(objectMapper, state),
                RagJsonUtils.truncate(RagJsonUtils.pretty(objectMapper, recentMessages), 8_000),
                RagJsonUtils.truncate(RagJsonUtils.pretty(objectMapper, retrieved), 8_000),
                RagJsonUtils.truncate(RagJsonUtils.pretty(objectMapper, fileAnalysis), 8_000),
                message
        );
        return ai.responseText(systemPrompt, userPrompt);
    }

    private List<Map<String, Object>> retrieve(UUID projectId, UUID versionId, String message, int limit) {
        if (!StringUtils.hasText(message)) return List.of();
        try {
            String vectorLiteral = RagRepository.toVectorLiteral(ai.embedding(message));
            return repository.searchChunks(projectId, versionId, vectorLiteral, limit);
        } catch (Exception e) {
            return List.of(Map.of("retrieveError", e.getMessage()));
        }
    }

    private Map<String, Object> updateState(Map<String, Object> state,
                                            String message,
                                            String answer,
                                            Map<String, Object> fileAnalysis) {
        Map<String, Object> newState = new LinkedHashMap<>(state);
        newState.put("lastUserMessage", message);
        newState.put("lastAnswer", answer);
        newState.put("lastFileAnalysis", fileAnalysis);
        newState.put("updatedAt", LocalDateTime.now().toString());
        if (!newState.containsKey("stepKey")) newState.put("stepKey", "FREE_CHAT");
        return newState;
    }

    @SuppressWarnings("unchecked")
    private void appendAudit(Map<String, Object> audit, String type, String message, Object detail) {
        List<Object> events = audit.get("events") instanceof List<?> list ? (List<Object>) list : new ArrayList<>();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("message", message);
        event.put("detail", detail);
        event.put("createdAt", LocalDateTime.now().toString());
        events.add(event);
        audit.put("events", events);
    }

    private String renderUserContent(String message, List<MultipartFile> files, Map<String, Object> fileAnalysis) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(message)) sb.append(message.trim());
        if (files != null && !files.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("첨부 파일:\n");
            for (MultipartFile file : files) {
                sb.append("- ").append(file.getOriginalFilename()).append("\n");
            }
        }
        if (fileAnalysis != null && !fileAnalysis.isEmpty()) {
            sb.append("\n[첨부 분석 상태] ").append(fileAnalysis.getOrDefault("saveMessage", "분석 완료"));
            Object analysis = fileAnalysis.get("analysis");
            if (analysis != null) {
                sb.append("\n[첨부 AI 분석]\n").append(RagJsonUtils.truncate(RagJsonUtils.safeString(analysis), 12_000));
            }
            String rawInputText = RagJsonUtils.stringValue(fileAnalysis, "rawInputText");
            if (StringUtils.hasText(rawInputText)) {
                sb.append("\n[첨부 원문 추출]\n").append(RagJsonUtils.truncate(rawInputText, 24_000));
            }
        }
        return sb.toString();
    }
}
