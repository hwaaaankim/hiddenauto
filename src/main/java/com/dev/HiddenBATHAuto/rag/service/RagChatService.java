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

import com.dev.HiddenBATHAuto.rag.dto.RagChatMessageRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagChatStartRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagInquiryRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagResetStepRequest;
import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagChatService {

    private final RagRepository repository;
    private final OpenAiRagClient openAiClient;
    private final RagDocumentIngestService ingestService;
    private final RagOrderPriceCalculator priceCalculator;
    private final ObjectMapper objectMapper;

    public RagChatService(RagRepository repository,
                          OpenAiRagClient openAiClient,
                          RagDocumentIngestService ingestService,
                          RagOrderPriceCalculator priceCalculator,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.openAiClient = openAiClient;
        this.ingestService = ingestService;
        this.priceCalculator = priceCalculator;
        this.objectMapper = objectMapper;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> start(RagChatStartRequest request) {
        if (request == null || request.projectId() == null) {
            throw new IllegalArgumentException("프로젝트를 선택해 주세요.");
        }
        Map<String, Object> version = repository.findActiveVersion(request.projectId())
                .orElseThrow(() -> new IllegalArgumentException("ACTIVE 버전이 없습니다. 관리자 학습 화면에서 현재 버전을 발행해 주세요."));

        UUID sessionId = UUID.randomUUID();
        Map<String, Object> process = RagJsonUtils.toMap(objectMapper, version.get("process_json"));
        List<Map<String, Object>> steps = steps(process);
        String firstStepKey = steps.isEmpty() ? null : String.valueOf(steps.get(0).get("stepKey"));

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("currentStepKey", firstStepKey);
        state.put("answers", new LinkedHashMap<>());
        state.put("completed", false);
        state.put("priceResult", null);
        state.put("helpAssetKeys", helpKeysForStep(steps.isEmpty() ? null : steps.get(0)));
        state.put("startedAt", LocalDateTime.now().toString());
        state.put("runtimeMode", "STRUCTURED_PROCESS_AND_DETERMINISTIC_PRICE");

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("resets", new ArrayList<>());
        audit.put("allAnswerHistory", new ArrayList<>());
        audit.put("priceCalculations", new ArrayList<>());

        Map<String, Object> session = repository.createChatSession(sessionId, request.projectId(), (UUID) version.get("id"), request.userLabel(), state, audit);
        String reply = questionForStep(steps.isEmpty() ? null : steps.get(0), process);
        repository.insertChatMessage(UUID.randomUUID(), sessionId, "assistant", reply, state, Map.of("reason", "START"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session", session);
        result.put("version", version);
        result.put("state", state);
        result.put("reply", reply);
        result.put("process", process);
        return result;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> message(UUID sessionId, RagChatMessageRequest request) {
        if (request == null || !StringUtils.hasText(request.content())) {
            throw new IllegalArgumentException("메시지를 입력해 주세요.");
        }
        Map<String, Object> session = repository.findChatSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("채팅 세션을 찾을 수 없습니다."));
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        Map<String, Object> version = repository.findVersion(versionId)
                .orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다."));

        Map<String, Object> state = RagJsonUtils.toMap(objectMapper, session.get("state_json"));
        Map<String, Object> audit = RagJsonUtils.toMap(objectMapper, session.get("audit_json"));
        String userContent = request.content().trim();
        repository.insertChatMessage(UUID.randomUUID(), sessionId, "user", userContent, state, Map.of());

        List<Map<String, Object>> retrieved = ingestService.retrieve(projectId, versionId, userContent, 5);
        Map<String, Object> aiResult = answerWithProcess(version, state, audit, userContent, retrieved);

        Map<String, Object> newState = aiResult.get("state") instanceof Map<?, ?> ? castMap(aiResult.get("state")) : state;
        Map<String, Object> newAudit = aiResult.get("audit") instanceof Map<?, ?> ? castMap(aiResult.get("audit")) : audit;
        String reply = String.valueOf(aiResult.getOrDefault("reply", "다음 정보를 입력해 주세요."));

        Map<String, Object> priceResult = priceCalculator.calculate(projectId, versionId, version, newState);
        appendPriceAudit(newAudit, priceResult);
        if (Boolean.TRUE.equals(priceResult.get("calculated"))) {
            newState.put("priceResult", priceResult);
            if (Boolean.TRUE.equals(newState.get("completed")) || shouldShowPriceNow(newState, priceResult)) {
                reply = appendPriceSummary(reply, priceResult);
            }
        } else {
            newState.put("priceResult", priceResult);
        }

        repository.updateChatSession(sessionId, newState, newAudit);
        repository.insertChatMessage(UUID.randomUUID(), sessionId, "assistant", reply, newState, Map.of("retrieved", retrieved, "ai", aiResult, "priceResult", priceResult));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reply", reply);
        result.put("state", newState);
        result.put("audit", newAudit);
        result.put("retrieved", retrieved);
        result.put("priceResult", priceResult);
        result.put("process", RagJsonUtils.toMap(objectMapper, version.get("process_json")));
        return result;
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> resetStep(UUID sessionId, RagResetStepRequest request) {
        if (request == null || !StringUtils.hasText(request.stepKey())) {
            throw new IllegalArgumentException("초기화할 stepKey를 입력해 주세요.");
        }
        Map<String, Object> session = repository.findChatSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("채팅 세션을 찾을 수 없습니다."));
        UUID versionId = (UUID) session.get("version_id");
        Map<String, Object> version = repository.findVersion(versionId)
                .orElseThrow(() -> new IllegalArgumentException("버전을 찾을 수 없습니다."));
        Map<String, Object> process = RagJsonUtils.toMap(objectMapper, version.get("process_json"));
        List<Map<String, Object>> steps = steps(process);

        Map<String, Object> state = RagJsonUtils.toMap(objectMapper, session.get("state_json"));
        Map<String, Object> audit = RagJsonUtils.toMap(objectMapper, session.get("audit_json"));
        Map<String, Object> answers = RagJsonUtils.childMap(state, "answers");

        int resetOrder = orderOf(steps, request.stepKey());
        Map<String, Object> removed = new LinkedHashMap<>();
        for (Map<String, Object> step : steps) {
            String key = String.valueOf(step.get("stepKey"));
            if (orderOf(steps, key) >= resetOrder && answers.containsKey(key)) {
                removed.put(key, answers.remove(key));
            }
        }
        if (removed.isEmpty() && answers.containsKey(request.stepKey())) {
            removed.put(request.stepKey(), answers.remove(request.stepKey()));
        }

        List<Object> resets = RagJsonUtils.childList(audit, "resets");
        Map<String, Object> resetLog = new LinkedHashMap<>();
        resetLog.put("stepKey", request.stepKey());
        resetLog.put("reason", request.reason());
        resetLog.put("removedAnswers", removed);
        resetLog.put("resetAt", LocalDateTime.now().toString());
        resets.add(resetLog);
        audit.put("resets", resets);

        Map<String, Object> targetStep = findStep(steps, request.stepKey());
        state.put("answers", answers);
        state.put("currentStepKey", request.stepKey());
        state.put("completed", false);
        state.put("helpAssetKeys", helpKeysForStep(targetStep));
        state.put("priceResult", null);

        repository.updateChatSession(sessionId, state, audit);
        String reply = "요청하신 스텝부터 다시 진행하겠습니다.\n" + questionForStep(targetStep, process);
        repository.insertChatMessage(UUID.randomUUID(), sessionId, "system", "RESET " + request.stepKey(), state, Map.of("removed", removed));
        repository.insertChatMessage(UUID.randomUUID(), sessionId, "assistant", reply, state, Map.of("reason", "RESET"));

        return Map.of("message", reply, "state", state, "audit", audit, "removed", removed, "process", process);
    }

    @Transactional("ragTransactionManager")
    public Map<String, Object> inquiry(UUID sessionId, RagInquiryRequest request) {
        Map<String, Object> session = repository.findChatSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("채팅 세션을 찾을 수 없습니다."));
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        Map<String, Object> state = RagJsonUtils.toMap(objectMapper, session.get("state_json"));
        Map<String, Object> audit = RagJsonUtils.toMap(objectMapper, session.get("audit_json"));
        List<Map<String, Object>> messages = repository.findChatMessages(sessionId);
        Map<String, Object> inquiry = repository.insertInquiry(
                UUID.randomUUID(),
                sessionId,
                projectId,
                versionId,
                request == null ? null : request.companyName(),
                request == null ? null : request.customerName(),
                request == null ? null : request.phone(),
                request == null ? null : request.email(),
                request == null ? null : request.memo(),
                state,
                audit,
                messages
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inquiry", inquiry);
        result.put("session", session);
        result.put("state", state);
        result.put("audit", audit);
        result.put("messages", messages);
        return result;
    }

    private Map<String, Object> answerWithProcess(Map<String, Object> version,
                                                  Map<String, Object> state,
                                                  Map<String, Object> audit,
                                                  String userMessage,
                                                  List<Map<String, Object>> retrieved) {
        String systemPrompt = """
                당신은 발주 챗봇 런타임입니다.
                관리자 학습으로 만들어진 process/pricing/constraints JSON만 기준으로 고객에게 다음 질문을 합니다.
                가격 산술 계산은 서버의 RagOrderPriceCalculator가 수행하므로, 당신은 가격을 직접 더하거나 추정하지 마세요.
                사용자의 답변을 현재 state.currentStepKey에 맞는 구조화 값으로 정리하고, 다음 질문을 선택하는 역할만 합니다.
                모르는 규칙은 추측하지 말고 관리자 확인이 필요하다고 말하세요.
                반드시 JSON 객체만 반환하세요. 코드블록/마크다운 금지.

                반환 JSON:
                {
                  "reply": "고객에게 보여줄 답변 또는 다음 질문. 가격 계산 문장은 직접 만들지 말 것",
                  "state": {
                    "currentStepKey": "... 또는 null",
                    "answers": {},
                    "completed": false,
                    "priceResult": null,
                    "helpAssetKeys": []
                  },
                  "audit": {"resets":[],"allAnswerHistory":[],"priceCalculations":[]},
                  "debug": {"usedRuleKeys":[],"warnings":[]}
                }

                처리 규칙:
                - 현재 state.currentStepKey의 답변으로 userMessage를 저장하세요.
                - answers에는 stepKey별로 value, stepTitle, answeredAt, displayValue를 저장하세요.
                - 사이즈 답변은 가능하면 value 안에 width/depth/height 숫자도 같이 넣으세요.
                - 문/서랍/손잡이/타공/마구리/상판/세면대 같은 옵션은 개수와 종류가 계산 가능하도록 숫자를 보존하세요.
                - audit.allAnswerHistory에는 모든 답변 이력을 누적하세요.
                - 다음 스텝은 process.steps orderNo 및 nextRules/constraints를 고려하세요.
                - 모든 필수 스텝이 끝났거나 견적 표시가 가능하면 completed=true로 두되, priceResult는 null로 두세요.
                """;
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("versionSummary", version.get("summary"));
        prompt.put("process", RagJsonUtils.toMap(objectMapper, version.get("process_json")));
        prompt.put("pricing", RagJsonUtils.toMap(objectMapper, version.get("pricing_json")));
        prompt.put("constraints", RagJsonUtils.toMap(objectMapper, version.get("constraints_json")));
        prompt.put("state", state);
        prompt.put("audit", audit);
        prompt.put("userMessage", userMessage);
        prompt.put("retrieved", retrieved);
        try {
            String raw = openAiClient.responseText(systemPrompt, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(prompt));
            JsonNode node = RagJsonUtils.extractObjectNode(objectMapper, raw);
            Map<String, Object> parsed = RagJsonUtils.toMap(objectMapper, node.toString());
            parsed.putIfAbsent("state", state);
            parsed.putIfAbsent("audit", audit);
            parsed.putIfAbsent("reply", "다음 정보를 입력해 주세요.");
            return parsed;
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("reply", "처리 중 오류가 발생했습니다: " + e.getMessage());
            fallback.put("state", state);
            fallback.put("audit", audit);
            fallback.put("debug", Map.of("error", e.getMessage()));
            return fallback;
        }
    }

    private boolean shouldShowPriceNow(Map<String, Object> state, Map<String, Object> priceResult) {
        return Boolean.TRUE.equals(priceResult.get("calculated")) && Boolean.TRUE.equals(state.get("completed"));
    }

    @SuppressWarnings("unchecked")
    private void appendPriceAudit(Map<String, Object> audit, Map<String, Object> priceResult) {
        List<Object> list = RagJsonUtils.childList(audit, "priceCalculations");
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("calculatedAt", LocalDateTime.now().toString());
        log.put("calculated", priceResult.get("calculated"));
        log.put("total", priceResult.get("total"));
        log.put("warnings", priceResult.get("warnings"));
        list.add(log);
        audit.put("priceCalculations", list);
    }

    @SuppressWarnings("unchecked")
    private String appendPriceSummary(String reply, Map<String, Object> priceResult) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(reply)) sb.append(reply.trim()).append("\n\n");
        sb.append("현재 입력 기준 예상 금액은 ").append(priceResult.getOrDefault("total", 0)).append("원입니다.");
        Object linesObj = priceResult.get("lines");
        if (linesObj instanceof List<?> lines && !lines.isEmpty()) {
            sb.append("\n계산 근거:");
            for (Object obj : lines) {
                if (!(obj instanceof Map<?, ?> map)) continue;
                Object label = map.get("label") == null ? "항목" : map.get("label");
                Object amount = map.get("amount") == null ? 0 : map.get("amount");
                Object basis = map.get("basis") == null ? "" : map.get("basis");
                sb.append("\n- ").append(label)
                        .append(": ").append(amount).append("원")
                        .append(" (").append(basis).append(")");
            }
        }
        Object warningsObj = priceResult.get("warnings");
        if (warningsObj instanceof List<?> warnings && !warnings.isEmpty()) {
            sb.append("\n확인 필요:");
            for (Object warning : warnings) sb.append("\n- ").append(warning);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return result;
    }

    private List<Map<String, Object>> steps(Map<String, Object> process) {
        return RagJsonUtils.toMapList(objectMapper, process.get("steps"));
    }

    private Map<String, Object> findStep(List<Map<String, Object>> steps, String stepKey) {
        if (steps == null) return null;
        for (Map<String, Object> step : steps) {
            if (stepKey.equals(String.valueOf(step.get("stepKey")))) return step;
        }
        return null;
    }

    private int orderOf(List<Map<String, Object>> steps, String stepKey) {
        int idx = 0;
        for (Map<String, Object> step : steps) {
            idx++;
            if (stepKey.equals(String.valueOf(step.get("stepKey")))) {
                return RagJsonUtils.intValue(step, "orderNo", idx);
            }
        }
        return Integer.MAX_VALUE;
    }

    private String questionForStep(Map<String, Object> step, Map<String, Object> process) {
        if (step == null) {
            return "이 프로젝트는 아직 고객 질문 스텝이 구성되지 않았습니다. 관리자 학습 화면에서 프로세스부터 완성해 주세요.";
        }
        String question = RagJsonUtils.stringValue(step, "question");
        if (StringUtils.hasText(question)) return question;
        String title = RagJsonUtils.stringValue(step, "title");
        return StringUtils.hasText(title) ? title + " 정보를 입력해 주세요." : "다음 정보를 입력해 주세요.";
    }

    private List<Object> helpKeysForStep(Map<String, Object> step) {
        if (step == null) return List.of();
        List<Object> keys = new ArrayList<>();
        Object stepKey = step.get("stepKey");
        if (stepKey != null) keys.add(stepKey);
        keys.addAll(RagJsonUtils.childList(step, "helpAssetKeys"));
        return keys;
    }
}
