package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagSqlAgentService {

    private static final int MAX_AGENT_TURNS = 6;
    private static final int MAX_FILE_PREVIEW_CHARS = 60000;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final OpenAiRagClient openAiClient;
    private final RagRepository repository;
    private final RagAgentSchemaService schemaService;
    private final RagAgentSqlExecutorService sqlExecutorService;
    private final RagAgentChangeSetService changeSetService;
    private final RagFileKnowledgeParser fileKnowledgeParser;
    private final RagFileStorageService fileStorageService;
    private final RagConversationWorkingMemoryService workingMemoryService;

    public RagSqlAgentService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                              ObjectMapper objectMapper,
                              OpenAiRagClient openAiClient,
                              RagRepository repository,
                              RagAgentSchemaService schemaService,
                              RagAgentSqlExecutorService sqlExecutorService,
                              RagAgentChangeSetService changeSetService,
                              RagFileKnowledgeParser fileKnowledgeParser,
                              RagFileStorageService fileStorageService,
                              RagConversationWorkingMemoryService workingMemoryService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.openAiClient = openAiClient;
        this.repository = repository;
        this.schemaService = schemaService;
        this.sqlExecutorService = sqlExecutorService;
        this.changeSetService = changeSetService;
        this.fileKnowledgeParser = fileKnowledgeParser;
        this.fileStorageService = fileStorageService;
        this.workingMemoryService = workingMemoryService;
    }

    public Map<String, Object> handle(UUID projectId,
                                      UUID versionId,
                                      UUID sessionId,
                                      String sourceScope,
                                      String message,
                                      boolean forceSave,
                                      List<MultipartFile> files) {
        UUID runId = UUID.randomUUID();
        String cleanMessage = message == null ? "" : message.trim();
        insertRun(runId, projectId, versionId, sessionId, sourceScope, cleanMessage, forceSave, Map.of("initializing", true));

        List<MultipartFile> safeFiles = safeFiles(files);
        List<Map<String, Object>> fileStages = stageFiles(projectId, versionId, runId, sourceScope, safeFiles);
        Map<String, Object> schema = schemaService.snapshot(projectId, versionId);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("schema", schema);
        context.put("recentMessages", recentMessages(projectId, versionId, sessionId, sourceScope));
        context.put("workingMemory", workingMemoryService.load(projectId, versionId, sessionId, sourceScope));
        context.put("files", fileStages);
        context.put("forceSave", forceSave);
        context.put("sourceScope", sourceScope);
        context.put("agentPolicy", agentPolicy());
        updateRunContext(runId, context);

        List<Map<String, Object>> sqlResults = new ArrayList<>();
        Map<String, Object> lastStep = Map.of();
        String status = "RUNNING";
        String answer = "";
        Map<String, Object> changeResult = Map.of();
        try {
            for (int turn = 1; turn <= MAX_AGENT_TURNS; turn++) {
                Map<String, Object> step = askAgent(projectId, versionId, sessionId, sourceScope, cleanMessage, forceSave, context, sqlResults, turn);
                lastStep = step;
                status = text(step.get("status"), "ERROR").toUpperCase();
                answer = text(step.get("answer"), "");

                if ("NEED_SQL".equals(status)) {
                    List<Map<String, Object>> requests = listOfMaps(step.get("readSqlRequests"));
                    if (requests.isEmpty()) {
                        throw new IllegalStateException("GPT가 NEED_SQL을 반환했지만 readSqlRequests가 비어 있습니다.");
                    }
                    for (Map<String, Object> request : requests) {
                        Map<String, Object> result = sqlExecutorService.executeRead(
                                runId,
                                projectId,
                                versionId,
                                sessionId,
                                text(request.get("requestId"), "q" + turn),
                                text(request.get("reason"), ""),
                                text(request.get("sql"), ""),
                                text(request.get("paramsJson"), "{}")
                        );
                        sqlResults.add(result);
                    }
                    continue;
                }

                if ("READY_TO_CHANGE".equals(status)) {
                    Map<String, Object> changeSet = mapOf(step.get("changeSet"));
                    changeResult = changeSetService.persistAndMaybeApply(runId, projectId, versionId, sessionId, sourceScope, changeSet, forceSave);
                    if (!StringUtils.hasText(answer)) {
                        answer = Boolean.TRUE.equals(changeResult.get("applied"))
                                ? "요청하신 변경 사항을 검증 후 저장했습니다."
                                : "변경 계획을 만들었지만 충돌 가능성 또는 확인 필요 조건이 있어 자동 저장하지 않았습니다.";
                    }
                    break;
                }

                if ("READY_TO_ANSWER".equals(status) || "NEED_CLARIFICATION".equals(status) || "BLOCKED".equals(status)) {
                    break;
                }

                if ("ERROR".equals(status)) {
                    break;
                }
            }

            Map<String, Object> response = buildResponse(runId, status, lastStep, answer, sqlResults, changeResult);
            saveConversationMessage(projectId, versionId, sessionId, sourceScope, cleanMessage, response);
            updateRun(runId, response, status, null);
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("handled", false);
            response.put("intentType", "GPT_SQL_AGENT_FALLBACK");
            response.put("actionStatus", "FALLBACK_TO_EXISTING_FLOW");
            response.put("confidence", BigDecimal.ZERO);
            response.put("answer", "GPT SQL Agent 처리 중 문제가 발생해 기존 학습/챗봇 흐름으로 넘깁니다. 원인: " + e.getMessage());
            response.put("agentRunId", runId);
            response.put("agentError", e.getMessage());
            updateRun(runId, response, "FAILED", e.getMessage());
            return response;
        }
    }

    private Map<String, Object> askAgent(UUID projectId,
                                         UUID versionId,
                                         UUID sessionId,
                                         String sourceScope,
                                         String message,
                                         boolean forceSave,
                                         Map<String, Object> context,
                                         List<Map<String, Object>> sqlResults,
                                         int turn) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("projectId", projectId);
        user.put("versionId", versionId);
        user.put("sessionId", sessionId);
        user.put("sourceScope", sourceScope);
        user.put("forceSave", forceSave);
        user.put("turn", turn);
        user.put("userMessage", message);
        user.put("context", context);
        user.put("sqlResultsSoFar", sqlResults);

        String raw = openAiClient.responseJsonSchema(
                systemPrompt(),
                RagJsonUtils.toJson(objectMapper, user),
                "hiddenauto_sql_agent_step",
                RagAgentSchemaFactory.agentStepSchema(),
                true
        );
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("GPT SQL Agent 응답 JSON 파싱 실패: " + e.getMessage() + " / raw=" + raw, e);
        }
    }

    private String systemPrompt() {
        return """
                당신은 HiddenBATHAuto 프로젝트 안에서 동작하는 GPT SQL Agent입니다.
                목표는 Java가 사용자의 의미를 분기하지 않도록, 당신이 직접 사용자의 말과 업로드 파일을 해석하고 DB를 읽고 판단하는 것입니다.

                핵심 원칙:
                1. 사용자의 표현을 Java enum/actionType에 맞추려고 하지 마십시오. 사용자의 자연어 의미를 직접 해석하십시오.
                2. 필요한 정보가 있으면 readSqlRequests에 SELECT 또는 WITH SQL을 직접 작성하십시오.
                3. 모든 SELECT SQL은 반드시 rag_ 테이블만 사용하고, project_id = :projectId AND version_id = :versionId 조건을 포함해야 합니다.
                4. SQL 파라미터는 :projectId, :versionId, :sessionId, :p1~:p50만 사용하십시오. paramsJson에는 p1~p50 값을 JSON object로 넣으십시오.
                5. “모든 제품”, “전체 제품”, “전체 정보”는 특정 제품명 검색이 아니라 저장된 관련 row/노드/표 전체를 조회해야 하는 의미일 수 있습니다.
                6. 수정/학습/삭제/교체 요청은 먼저 관련 DB를 충분히 조회하여 기존 데이터, 가격규칙, 대화규칙, override, knowledge_node 간 모순 가능성을 확인하십시오.
                6-1. context.workingMemory에는 현재 세션에서 직전 제품, intent, factor, 규칙, 견적 문맥이 들어 있습니다. 사용자가 "1350이면?", "그럼 1500은?", "그 색상도 돼?"처럼 생략하면 workingMemory를 참고해 문맥을 복원하십시오.
                7. 직접 write SQL을 즉시 실행하지 않습니다. 변경은 changeSet.items에 UPDATE_SQL, INSERT_SQL, INSERT_KNOWLEDGE_NODE 형태로 계획하십시오.
                8. UPDATE_SQL/INSERT_SQL의 writeSql도 rag_ 테이블만 허용됩니다. :projectId, :versionId, :targetId, :p1~:p50만 사용하십시오.
                9. 모순 가능성·연관 영향·확신 부족이 있으면 requiresUserConfirmation=true 및 changeSet.requiresConfirmation=true로 두십시오.
                10. 사용자가 forceSave=true로 보낸 경우에도, 데이터 충돌이 치명적이면 NEED_CLARIFICATION 또는 BLOCKED로 답하십시오.
                11. 알 수 없는 내용은 추측 저장하지 말고 필요한 SELECT를 요청하거나 확인 질문을 하십시오.
                12. 파일이 있으면 파일 미리보기와 기존 DB를 함께 비교하고, 기존 자료 교체/보완/신규학습 여부를 판단하십시오.
                13. 업로드 파일 전체 원문은 rag_document/rag_chunk에 source_type='GPT_SQL_AGENT_FILE_STAGE', topic='agent-file-stage'로 embedding 없이 저장됩니다. 미리보기만으로 부족하면 SQL로 해당 document/chunk를 조회하십시오.

                status 규칙:
                - NEED_SQL: DB 조회가 더 필요함. readSqlRequests를 1개 이상 작성.
                - READY_TO_ANSWER: 조회 결과만으로 답변 가능하고 DB 변경 없음.
                - READY_TO_CHANGE: 저장/수정 계획이 완성됨. changeSet.items를 작성.
                - NEED_CLARIFICATION: 사용자 확인 없이는 진행하면 안 됨.
                - BLOCKED: 안전하지 않거나 범위 밖 요청.
                - ERROR: 내부적으로 해석 불가.

                응답은 반드시 스키마에 맞는 JSON으로만 작성하십시오.
                """;
    }

    private List<Map<String, Object>> stageFiles(UUID projectId, UUID versionId, UUID runId, String sourceScope, List<MultipartFile> files) {
        List<Map<String, Object>> result = new ArrayList<>();
        int index = 0;
        for (MultipartFile file : files) {
            index++;
            try {
                RagUploadedKnowledgeDocument parsed = fileKnowledgeParser.parse(file);
                String preview = truncate(parsed.rawText(), MAX_FILE_PREVIEW_CHARS);
                Map<String, Object> asset = fileStorageService.saveAsset(projectId, versionId, "GPT_SQL_AGENT", runId.toString(), file, "GPT SQL Agent 파일 staging");
                UUID documentId = persistFileAsRawKnowledge(projectId, versionId, runId, parsed, asset);
                UUID stageId = UUID.randomUUID();
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("stageId", stageId);
                one.put("documentId", documentId);
                one.put("index", index);
                one.put("filename", parsed.originalFilename());
                one.put("contentType", parsed.contentType());
                one.put("sourceType", parsed.sourceType());
                one.put("previewText", preview);
                one.put("metadata", parsed.metadata());
                one.put("asset", asset);
                one.put("dbNotice", "파일 전체 텍스트는 rag_document/rag_chunk에 embedding 없이 원문 보존되었습니다. GPT는 필요 시 SQL로 raw_text/content를 조회해야 합니다.");
                result.add(one);
                insertFileStage(stageId, runId, projectId, versionId, sourceScope, one, preview);
            } catch (Exception e) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("index", index);
                one.put("filename", file.getOriginalFilename());
                one.put("error", e.getMessage());
                result.add(one);
            }
        }
        return result;
    }

    private UUID persistFileAsRawKnowledge(UUID projectId, UUID versionId, UUID runId, RagUploadedKnowledgeDocument parsed, Map<String, Object> asset) {
        UUID documentId = UUID.randomUUID();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "GPT_SQL_AGENT_FILE_STAGE");
        metadata.put("runId", runId);
        metadata.put("parserMetadata", parsed.metadata());
        metadata.put("asset", asset);
        String title = parsed.originalFilename();
        String rawText = parsed.rawText() == null ? "" : parsed.rawText();
        repository.insertDocument(documentId, projectId, versionId, "agent-file-stage", "GPT_SQL_AGENT_FILE_STAGE", title, parsed.originalFilename(), rawText, metadata);
        int chunkNo = 1;
        for (String chunk : splitChunks(rawText, 14000)) {
            repository.insertChunkWithoutEmbedding(UUID.randomUUID(), documentId, projectId, versionId, chunkNo++, "agent-file-stage", chunk, metadata);
        }
        return documentId;
    }

    private List<String> splitChunks(String text, int size) {
        if (!StringUtils.hasText(text)) return List.of("");
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            chunks.add(text.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private void insertFileStage(UUID stageId, UUID runId, UUID projectId, UUID versionId, String sourceScope, Map<String, Object> fileMeta, String previewText) {
        String sql = """
                INSERT INTO rag_agent_file_stage(id, run_id, project_id, version_id, source_scope, file_meta_json, preview_text, created_at)
                VALUES (:id, :runId, :projectId, :versionId, :sourceScope, CAST(:fileMetaJson AS jsonb), :previewText, now())
                """;
        try {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", stageId)
                    .addValue("runId", runId)
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("sourceScope", sourceScope)
                    .addValue("fileMetaJson", RagJsonUtils.toJson(objectMapper, fileMeta))
                    .addValue("previewText", previewText));
        } catch (Exception ignored) {
            // staging 로그 실패가 실제 파일 처리 흐름을 막으면 안 됩니다.
        }
    }

    private Map<String, Object> buildResponse(UUID runId,
                                              String status,
                                              Map<String, Object> step,
                                              String answer,
                                              List<Map<String, Object>> sqlResults,
                                              Map<String, Object> changeResult) {
        BigDecimal confidence = decimal(step.get("confidence"), new BigDecimal("0.8000"));
        boolean needsClarification = "NEED_CLARIFICATION".equals(status) || Boolean.TRUE.equals(step.get("requiresUserConfirmation"));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("handled", true);
        response.put("intentType", "GPT_SQL_AGENT");
        response.put("actionStatus", status);
        response.put("confidence", confidence);
        response.put("requiresClarification", needsClarification);
        response.put("shouldPersist", "READY_TO_CHANGE".equals(status));
        response.put("answer", StringUtils.hasText(answer) ? answer : defaultAnswer(status));
        response.put("agentRunId", runId);
        response.put("agentStep", step);
        response.put("agentSqlResults", sqlResults);
        response.put("changeResult", changeResult);
        response.put("saveStatus", saveStatus(status, changeResult));
        response.put("saveMessage", saveMessage(status, changeResult));
        response.put("memory", memory(status, changeResult));
        return response;
    }

    private String saveStatus(String status, Map<String, Object> changeResult) {
        if ("READY_TO_CHANGE".equals(status)) {
            return Boolean.TRUE.equals(changeResult.get("applied")) ? "지식 저장: GPT Agent 저장됨" : "지식 저장: GPT Agent 변경계획 보류";
        }
        return "지식 저장: 변경 없음";
    }

    private String saveMessage(String status, Map<String, Object> changeResult) {
        if ("READY_TO_CHANGE".equals(status)) {
            Object message = changeResult.get("message");
            if (message != null) return String.valueOf(message);
            return Boolean.TRUE.equals(changeResult.get("applied"))
                    ? "GPT가 DB를 조회해 변경계획을 만들고 Java가 검증 후 트랜잭션으로 적용했습니다."
                    : "GPT가 변경계획을 만들었지만 확인 필요 상태로 저장했습니다.";
        }
        return "조회/답변 요청으로 처리되어 새 지식 저장은 수행하지 않았습니다.";
    }

    private Map<String, Object> memory(String status, Map<String, Object> changeResult) {
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("status", Boolean.TRUE.equals(changeResult.get("applied")) ? "SAVED" : ("READY_TO_CHANGE".equals(status) ? "WAITING_USER" : "NO_KNOWLEDGE_CHANGE"));
        memory.put("saveLabel", saveStatus(status, changeResult));
        memory.put("message", saveMessage(status, changeResult));
        memory.put("changeSetId", changeResult.get("changeSetId"));
        return memory;
    }

    private String defaultAnswer(String status) {
        return switch (status) {
            case "NEED_CLARIFICATION" -> "확인 없이 저장하면 충돌 가능성이 있어 추가 확인이 필요합니다.";
            case "BLOCKED" -> "요청이 안전 범위를 벗어나 자동 처리하지 않았습니다.";
            case "ERROR" -> "요청을 해석하지 못했습니다. 조금 더 구체적으로 입력해 주세요.";
            default -> "요청을 처리했습니다.";
        };
    }

    private void insertRun(UUID runId,
                           UUID projectId,
                           UUID versionId,
                           UUID sessionId,
                           String sourceScope,
                           String userMessage,
                           boolean forceSave,
                           Object context) {
        String sql = """
                INSERT INTO rag_agent_run(
                    id, project_id, version_id, session_id, source_scope, user_message, force_save,
                    status, context_json, created_at, updated_at
                ) VALUES (
                    :id, :projectId, :versionId, :sessionId, :sourceScope, :userMessage, :forceSave,
                    'RUNNING', CAST(:contextJson AS jsonb), now(), now()
                )
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", runId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("sessionId", sessionId)
                .addValue("sourceScope", sourceScope)
                .addValue("userMessage", userMessage)
                .addValue("forceSave", forceSave)
                .addValue("contextJson", RagJsonUtils.toJson(objectMapper, context)));
    }

    private void updateRunContext(UUID runId, Object context) {
        String sql = """
                UPDATE rag_agent_run
                SET context_json = CAST(:contextJson AS jsonb),
                    updated_at = now()
                WHERE id = :id
                """;
        try {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", runId)
                    .addValue("contextJson", RagJsonUtils.toJson(objectMapper, context)));
        } catch (Exception ignored) {
            // context 갱신 실패가 실제 응답을 막으면 안 됩니다.
        }
    }

    private void updateRun(UUID runId, Object response, String status, String errorMessage) {
        String sql = """
                UPDATE rag_agent_run
                SET status = :status,
                    final_response_json = CAST(:responseJson AS jsonb),
                    error_message = :errorMessage,
                    updated_at = now(),
                    completed_at = now()
                WHERE id = :id
                """;
        try {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("id", runId)
                    .addValue("status", status)
                    .addValue("responseJson", RagJsonUtils.toJson(objectMapper, response))
                    .addValue("errorMessage", errorMessage));
        } catch (Exception ignored) {
            // run 업데이트 실패가 응답을 막으면 안 됩니다.
        }
    }

    private void saveConversationMessage(UUID projectId, UUID versionId, UUID sessionId, String sourceScope, String userMessage, Map<String, Object> response) {
        try {
            if (sessionId == null) return;
            if ("CHAT".equalsIgnoreCase(sourceScope)) {
                repository.insertChatMessage(UUID.randomUUID(), sessionId, "USER", userMessage, Map.of("agent", true), Map.of());
                repository.insertChatMessage(UUID.randomUUID(), sessionId, "ASSISTANT", text(response.get("answer"), ""), response, Map.of("agentRunId", response.get("agentRunId")));
            } else if ("LEARNING".equalsIgnoreCase(sourceScope)) {
                repository.insertLearningMessage(UUID.randomUUID(), sessionId, projectId, versionId, "USER", userMessage);
                repository.insertLearningMessage(UUID.randomUUID(), sessionId, projectId, versionId, "ASSISTANT", text(response.get("answer"), ""));
            }
        } catch (Exception ignored) {
            // 대화 로그 저장 실패가 실제 응답을 막으면 안 됩니다.
        }
    }

    private List<Map<String, Object>> recentMessages(UUID projectId, UUID versionId, UUID sessionId, String sourceScope) {
        try {
            if (sessionId != null && "CHAT".equalsIgnoreCase(sourceScope)) {
                return repository.findRecentChatMessages(sessionId, 20);
            }
            return repository.findRecentLearningMessages(projectId, versionId, 20);
        } catch (Exception e) {
            Map<String, Object> notice = new LinkedHashMap<>();
            notice.put("notice", "최근 대화 조회 실패");
            notice.put("error", e.getMessage());
            return List.of(notice);
        }
    }

    private Map<String, Object> agentPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("sqlWriteDirectlyAllowed", false);
        policy.put("readSqlAllowed", true);
        policy.put("writeViaChangeSetOnly", true);
        policy.put("embeddingRequired", false);
        policy.put("embeddingAlternative", "구조화 JSON, raw_text, searchable_text, SQL 반복조회, ChangeSet 검증 로그를 사용해 임베딩 없이도 준-검색/추론 흐름을 구성합니다.");
        return policy;
    }

    private List<MultipartFile> safeFiles(List<MultipartFile> files) {
        if (files == null) return List.of();
        List<MultipartFile> result = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) result.add(file);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) result.add(mapOf(item));
            return result;
        }
        return List.of();
    }

    private Map<String, Object> mapOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "\n...[TRUNCATED]";
    }

    private String text(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return value == null ? fallback : new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }
}
