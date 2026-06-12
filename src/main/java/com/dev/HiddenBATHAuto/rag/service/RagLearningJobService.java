package com.dev.HiddenBATHAuto.rag.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.repository.RagLearningJobRepository;
import com.dev.HiddenBATHAuto.rag.service.RagConversationalLearningService.RagLearningProgressListener;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagLearningJobService {

    private final RagLearningJobRepository jobRepository;
    private final RagConversationalLearningService learningService;
    private final RagFileStorageService fileStorageService;
    private final RagFileKnowledgeParser fileParser;
    private final RagStructuredLearningService structuredLearningService;
    private final RagLearningPromptComposer promptComposer;
    private final RagKnowledgeNodeReinterpretService reinterpretService;
    private final Executor executor;
    private final TransactionTemplate requiresNewTx;
    private final ObjectMapper objectMapper;

    public RagLearningJobService(RagLearningJobRepository jobRepository,
                                 RagConversationalLearningService learningService,
                                 RagFileStorageService fileStorageService,
                                 RagFileKnowledgeParser fileParser,
                                 RagStructuredLearningService structuredLearningService,
                                 RagLearningPromptComposer promptComposer,
                                 RagKnowledgeNodeReinterpretService reinterpretService,
                                 @Qualifier("ragLearningJobExecutor") Executor executor,
                                 @Qualifier("ragTransactionManager") PlatformTransactionManager transactionManager,
                                 ObjectMapper objectMapper) {
        this.jobRepository = jobRepository;
        this.learningService = learningService;
        this.fileStorageService = fileStorageService;
        this.fileParser = fileParser;
        this.structuredLearningService = structuredLearningService;
        this.promptComposer = promptComposer;
        this.reinterpretService = reinterpretService;
        this.executor = executor;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> submitMessage(UUID sessionId,
                                             String message,
                                             String attachmentText,
                                             String attachmentFilename,
                                             boolean forceSave) {
        Map<String, Object> session = learningService.session(sessionId);
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        String topic = resolveTopic(session);
        UUID jobId = UUID.randomUUID();
        Map<String, Object> job = jobRepository.createJob(jobId, sessionId, projectId, versionId, topic, message, forceSave, 0);
        jobRepository.insertLog(jobId, "SUBMITTED", 0, "학습 작업이 접수되었습니다.", Map.of("sessionId", sessionId));
        executor.execute(() -> runMessageJob(jobId, sessionId, message, attachmentText, attachmentFilename, forceSave));
        return submitted(job);
    }

    public Map<String, Object> submitMessageWithFiles(UUID sessionId,
                                                      String message,
                                                      boolean forceSave,
                                                      List<MultipartFile> files) {
        Map<String, Object> session = learningService.session(sessionId);
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        String topic = resolveTopic(session);
        List<MultipartFile> safeFiles = collectFiles(files);
        UUID jobId = UUID.randomUUID();
        Map<String, Object> job = jobRepository.createJob(jobId, sessionId, projectId, versionId, topic, message, forceSave, safeFiles.size());
        jobRepository.insertLog(jobId, "SUBMITTED", 0, "학습 작업이 접수되었습니다.", Map.of("sessionId", sessionId, "fileCount", safeFiles.size()));

        try {
            for (MultipartFile file : safeFiles) {
                Map<String, Object> asset = fileStorageService.saveAsset(
                        projectId,
                        versionId,
                        "LEARNING_CONVERSATION",
                        sessionId.toString(),
                        file,
                        message
                );
                jobRepository.insertJobFile(
                        UUID.randomUUID(),
                        jobId,
                        (UUID) asset.get("id"),
                        str(asset.get("original_filename")),
                        str(asset.get("content_type")),
                        str(asset.get("file_path")),
                        longValue(asset.get("size"), file.getSize())
                );
            }
        } catch (Exception e) {
            failInNewTx(jobId, "파일을 작업 저장소에 보존하지 못했습니다: " + String.valueOf(e.getMessage()), Map.of("error", String.valueOf(e.getMessage())));
            throw e;
        }

        executor.execute(() -> runMessageJob(jobId, sessionId, message, null, null, forceSave));
        return submitted(job);
    }

    public Map<String, Object> submitRetryRawNodes(UUID sessionId, int limit) {
        Map<String, Object> session = learningService.session(sessionId);
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        String topic = resolveTopic(session);
        String message = "[AI 재해석] 이전 학습에서 남은 RAW/서버추출 지식 노드를 다시 더 작게 쪼개서 AI_PARSED 노드로 승격합니다.";
        UUID jobId = UUID.randomUUID();
        Map<String, Object> job = jobRepository.createJob(jobId, sessionId, projectId, versionId, topic, message, true, 0);
        jobRepository.insertLog(jobId, "SUBMITTED", 0, "재해석 작업이 접수되었습니다.", Map.of("sessionId", sessionId, "limit", limit));
        executor.execute(() -> runRetryJob(jobId, projectId, versionId, topic, limit));
        return submitted(job);
    }

    public Map<String, Object> submitRetryOneNode(UUID sessionId, UUID nodeId) {
        Map<String, Object> session = learningService.session(sessionId);
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        String topic = resolveTopic(session);
        String message = "[AI 재해석] 선택한 지식 노드만 다시 더 작게 쪼개서 AI_PARSED 노드로 승격합니다. nodeId=" + nodeId;
        UUID jobId = UUID.randomUUID();
        Map<String, Object> job = jobRepository.createJob(jobId, sessionId, projectId, versionId, topic, message, true, 0);
        jobRepository.insertLog(jobId, "SUBMITTED", 0, "선택 노드 재해석 작업이 접수되었습니다.", Map.of("sessionId", sessionId, "nodeId", nodeId));
        executor.execute(() -> runRetryOneNodeJob(jobId, nodeId, projectId, versionId));
        return submitted(job);
    }

    public Map<String, Object> findJob(UUID jobId) {
        Map<String, Object> job = jobRepository.findJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("학습 작업을 찾을 수 없습니다."));
        return jobView(job, true);
    }

    public List<Map<String, Object>> findJobsBySession(UUID sessionId, int limit) {
        List<Map<String, Object>> rows = jobRepository.findJobsBySession(sessionId, Math.max(1, Math.min(limit, 50)));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) result.add(jobView(row, false));
        return result;
    }

    private void runMessageJob(UUID jobId,
                               UUID sessionId,
                               String message,
                               String attachmentText,
                               String attachmentFilename,
                               boolean forceSave) {
        try {
            updateInNewTx(jobId, "PREPROCESSING", 5, "입력과 업로드 파일을 전처리하고 있습니다.");
            List<Map<String, Object>> jobFiles = jobRepository.findJobFiles(jobId);
            Map<String, Object> result;
            if (jobFiles.isEmpty()) {
                result = learningService.talk(sessionId, message, attachmentText, attachmentFilename, forceSave, listener(jobId));
            } else {
                result = runFileLearning(jobId, sessionId, message, forceSave, jobFiles);
            }
            if (isTechnicalGptFailure(result)) {
                failInNewTx(jobId, firstText(RagJsonUtils.stringValue(result, "answer"), "GPT 해석 작업이 기술적으로 실패했습니다."), result);
                return;
            }
            completeInNewTx(jobId, result, RagJsonUtils.stringValue(result, "answer"));
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("exceptionClass", e.getClass().getName());
            failInNewTx(jobId, String.valueOf(e.getMessage()), error);
        }
    }

    private Map<String, Object> runFileLearning(UUID jobId,
                                                UUID sessionId,
                                                String message,
                                                boolean forceSave,
                                                List<Map<String, Object>> jobFiles) {
        Map<String, Object> session = learningService.session(sessionId);
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        String topic = resolveTopic(session);

        List<RagStoredMultipartFile> files = new ArrayList<>();
        List<String> filenames = new ArrayList<>();
        for (Map<String, Object> row : jobFiles) {
            String filename = firstText(str(row.get("original_filename")), "upload");
            String contentType = str(row.get("content_type"));
            Path path = Path.of(str(row.get("file_path"))).toAbsolutePath().normalize();
            long size = longValue(row.get("size_bytes"), 0L);
            files.add(new RagStoredMultipartFile("files", filename, contentType, path, size));
            filenames.add(filename);
        }

        List<Map<String, Object>> fileContexts = new ArrayList<>();
        updateInNewTx(jobId, "PREPROCESSING", 10, "업로드 파일을 파싱하고 표/매트릭스 구조를 미리 분석하고 있습니다.");
        for (RagStoredMultipartFile file : files) {
            RagUploadedKnowledgeDocument doc = fileParser.parse(file);
            Map<String, Object> structuredPreview = structuredLearningService.analyzeUploadedKnowledge(file, message, topic);
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("filename", doc.originalFilename());
            ctx.put("originalFilename", doc.originalFilename());
            ctx.put("sourceType", doc.sourceType());
            ctx.put("rawText", doc.rawText());
            ctx.put("rawTextPreview", RagJsonUtils.truncate(doc.rawText(), 8000));
            ctx.put("parserMetadata", doc.metadata());
            ctx.put("structured", structuredPreview.get("structured"));
            ctx.put("semanticRole", structuredPreview.get("semanticRole"));
            ctx.put("summaryText", structuredPreview.get("summaryText"));
            ctx.put("warnings", structuredPreview.getOrDefault("warnings", List.of()));
            ctx.put("tableCount", structuredPreview.getOrDefault("tableCount", 0));
            ctx.put("matrixCount", structuredPreview.getOrDefault("matrixCount", 0));
            ctx.put("structuredPreview", structuredPreview);
            fileContexts.add(ctx);
        }

        updateInNewTx(jobId, "PREPROCESSING", 18, "파일 역할과 적용 범위를 표준 학습 입력으로 정리하고 있습니다.");
        Map<String, Object> preprocess = promptComposer.compose(session, message, fileContexts, forceSave);
        String enrichedAttachmentText = RagJsonUtils.stringValue(preprocess, "enrichedAttachmentText");

        Map<String, Object> result = learningService.talk(
                sessionId,
                message,
                enrichedAttachmentText,
                String.join(", ", filenames),
                forceSave,
                listener(jobId)
        );

        List<Map<String, Object>> structuredResults = List.of();
        boolean canSaveStructured = Boolean.TRUE.equals(result.get("shouldPersist"))
                && !Boolean.TRUE.equals(result.get("requiresClarification"));
        if (canSaveStructured) {
            updateInNewTx(jobId, "MERGING", 78, "GPT 판단 결과에 따라 구조화 엑셀 자료를 저장하고 있습니다.");
            Map<String, Object> structuredPlan = new LinkedHashMap<>(preprocess);
            Map<String, Object> analysis = castMap(result.get("analysis"));
            Object materials = analysis.get("materials");
            if (materials instanceof List<?>) structuredPlan.put("materials", materials);
            String normalized = RagJsonUtils.stringValue(RagJsonUtils.childMap(analysis, "inputInterpretation"), "normalizedUserInput");
            if (StringUtils.hasText(normalized)) structuredPlan.put("normalizedPrompt", normalized);
            structuredResults = saveStructuredFiles(projectId, versionId, topic, forceSave, structuredPlan, files, fileContexts);
        }

        result.put("uploadedFileCount", files.size());
        result.put("jobFiles", jobFiles);
        result.put("preprocess", preprocess);
        result.put("structuredResults", structuredResults);
        if (!structuredResults.isEmpty()) {
            result.put("structuredSaveStatus", "GPT 판단 후 구조화 엑셀 저장 " + structuredResults.size() + "건 처리");
        } else if (!canSaveStructured) {
            result.put("structuredSaveStatus", "구조화 저장 보류: GPT 판단 결과 저장 가능 상태가 아닙니다.");
        }
        return result;
    }

    private void runRetryJob(UUID jobId, UUID projectId, UUID versionId, String topic, int limit) {
        try {
            updateInNewTx(jobId, "PREPROCESSING", 10, "재해석 대상 RAW/서버추출 노드를 조회하고 있습니다.");
            Map<String, Object> result = reinterpretService.retryRetryableNodes(projectId, versionId, topic, limit, listener(jobId));
            updateInNewTx(jobId, "VECTOR_INDEXING", 92, "재해석된 노드의 검색 청크를 반영했습니다.");
            completeInNewTx(jobId, result, RagJsonUtils.stringValue(result, "message"));
        } catch (Exception e) {
            failInNewTx(jobId, "재해석 작업 실패: " + String.valueOf(e.getMessage()), Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private void runRetryOneNodeJob(UUID jobId, UUID nodeId, UUID projectId, UUID versionId) {
        try {
            updateInNewTx(jobId, "GPT_INTERPRETING", 20, "선택한 지식 노드를 재해석하고 있습니다.");
            Map<String, Object> result = reinterpretService.retryOneNode(nodeId, projectId, versionId);
            updateInNewTx(jobId, "VECTOR_INDEXING", 92, "재해석된 선택 노드의 검색 청크를 반영했습니다.");
            completeInNewTx(jobId, result, Boolean.TRUE.equals(result.get("success")) ? "선택 노드 재해석이 완료되었습니다." : "선택 노드 재해석이 다시 대기 상태로 남았습니다.");
        } catch (Exception e) {
            failInNewTx(jobId, "선택 노드 재해석 실패: " + String.valueOf(e.getMessage()), Map.of("nodeId", nodeId, "error", String.valueOf(e.getMessage())));
        }
    }

    private List<Map<String, Object>> saveStructuredFiles(UUID projectId,
                                                          UUID versionId,
                                                          String topic,
                                                          boolean forceSave,
                                                          Map<String, Object> preprocess,
                                                          List<RagStoredMultipartFile> files,
                                                          List<Map<String, Object>> fileContexts) {
        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> materials = new ArrayList<>();
        Object rawMaterials = preprocess.get("materials");
        if (rawMaterials instanceof List<?> list) {
            for (Object obj : list) if (obj instanceof Map<?, ?> m) materials.add(cast(m));
        }
        String normalizedPrompt = RagJsonUtils.stringValue(preprocess, "normalizedPrompt");
        for (int i = 0; i < files.size(); i++) {
            RagStoredMultipartFile file = files.get(i);
            Map<String, Object> ctx = i < fileContexts.size() ? fileContexts.get(i) : Map.of();
            Map<String, Object> preview = castMap(ctx.get("structuredPreview"));
            if (!Boolean.TRUE.equals(preview.get("structured"))) continue;
            String filename = firstText(file.getOriginalFilename(), "upload");
            Map<String, Object> material = findMaterial(materials, filename);
            if (material.isEmpty()) continue;
            Map<String, Object> saved = structuredLearningService.ingestPreparedKnowledge(
                    projectId,
                    versionId,
                    topic,
                    normalizedPrompt,
                    material,
                    file,
                    preview,
                    forceSave
            );
            results.add(saved);
        }
        return results;
    }

    private RagLearningProgressListener listener(UUID jobId) {
        return (status, progress, message) -> updateInNewTx(jobId, status, progress, message);
    }

    private void updateInNewTx(UUID jobId, String status, int progress, String message) {
        requiresNewTx.executeWithoutResult(tx -> jobRepository.updateJob(jobId, status, progress, message));
    }

    private void completeInNewTx(UUID jobId, Object resultJson, String answer) {
        requiresNewTx.executeWithoutResult(tx -> jobRepository.completeJob(jobId, resultJson, answer));
    }

    private void failInNewTx(UUID jobId, String errorMessage, Object resultJson) {
        requiresNewTx.executeWithoutResult(tx -> jobRepository.failJob(jobId, errorMessage, resultJson));
    }

    private boolean isTechnicalGptFailure(Map<String, Object> result) {
        if (result == null || result.isEmpty()) return false;
        Map<String, Object> analysis = castMap(result.get("analysis"));
        Map<String, Object> report = RagJsonUtils.childMap(analysis, "validationReportJson");
        String status = RagJsonUtils.stringValue(report, "status");
        if (StringUtils.hasText(status) && status.startsWith("GPT_") && status.endsWith("ERROR")) return true;
        String answer = firstText(RagJsonUtils.stringValue(result, "answer"), RagJsonUtils.stringValue(analysis, "answer"));
        return StringUtils.hasText(answer)
                && (answer.contains("GPT 해석 단계에서 오류") || answer.contains("최종 병합 단계에서 오류"));
    }

    private Map<String, Object> submitted(Map<String, Object> job) {
        Map<String, Object> result = jobView(job, false);
        result.put("success", true);
        result.put("accepted", true);
        result.put("jobId", job.get("id"));
        result.put("answer", "학습 작업을 접수했습니다. 화면에서 진행 상태를 확인합니다.");
        return result;
    }

    private Map<String, Object> jobView(Map<String, Object> job, boolean includeLogs) {
        Map<String, Object> result = new LinkedHashMap<>(job);
        result.remove("result_json");
        Object inputMessage = result.remove("input_message");
        if (inputMessage != null) result.put("inputPreview", truncateForScreen(String.valueOf(inputMessage), 1000));
        UUID jobId = (UUID) job.get("id");
        result.put("jobId", jobId);
        result.put("runStatus", job.get("run_status"));
        result.put("status", job.get("status"));
        result.put("progress", job.get("progress"));
        result.put("statusMessage", job.get("status_message"));

        Map<String, Object> resultJson = RagJsonUtils.toMap(objectMapper, job.get("result_json"));
        // 일부 JDBC 드라이버/PGobject 조합에서는 jsonb가 문자열 안의 raw로 한 번 더 감싸져 보일 수 있습니다.
        // 화면이 최종 answer를 못 찾아 '학습 작업이 완료되었습니다'만 표시하는 문제를 막기 위해 한 번 더 복구합니다.
        Object raw = resultJson.get("raw");
        if (raw != null && !resultJson.containsKey("answer")) {
            Map<String, Object> reparsed = RagJsonUtils.toMap(objectMapper, String.valueOf(raw));
            if (!reparsed.isEmpty() && !reparsed.containsKey("parseError")) resultJson = reparsed;
        }
        String answer = firstText(RagJsonUtils.stringValue(resultJson, "answer"), str(job.get("answer")));
        Map<String, Object> viewResultJson = compactMapForScreen(resultJson);
        if (StringUtils.hasText(answer)) {
            viewResultJson.put("answer", answer);
            result.put("answer", answer);
        }
        result.put("result", viewResultJson);
        if (includeLogs) {
            List<Map<String, Object>> logs = jobRepository.findJobLogs(jobId);
            if (logs.size() > 120) logs = logs.subList(logs.size() - 120, logs.size());
            result.put("logs", logs);
            result.put("files", jobRepository.findJobFiles(jobId));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> compactMapForScreen(Map<String, Object> source) {
        Object compact = compactForScreen(source, 0, "");
        if (compact instanceof Map<?, ?> map) return cast(map);
        return new LinkedHashMap<>();
    }

    private Object compactForScreen(Object value, int depth, String key) {
        if (value == null) return null;
        if (value instanceof String text) {
            int limit = "answer".equalsIgnoreCase(key) ? 12000 : 3000;
            if (isHeavyScreenKey(key)) limit = 300;
            return truncateForScreen(text, limit);
        }
        if (value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof Map<?, ?> map) {
            if (depth >= 5) return "[화면 표시 생략: 중첩 데이터가 너무 깊습니다]";
            Map<String, Object> result = new LinkedHashMap<>();
            int index = 0;
            int total = map.size();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() == null) continue;
                String childKey = String.valueOf(e.getKey());
                if (index >= 120) {
                    result.put("_screenOmittedKeys", (total - index) + "개 키 생략");
                    break;
                }
                if (isHeavyScreenKey(childKey)) {
                    result.put(childKey, "[화면 표시 생략: 원문/대용량 데이터]");
                } else {
                    result.put(childKey, compactForScreen(e.getValue(), depth + 1, childKey));
                }
                index++;
            }
            return result;
        }
        if (value instanceof List<?> list) {
            if (depth >= 5) return "[화면 표시 생략: 중첩 목록이 너무 깊습니다]";
            int limit = Math.min(list.size(), 50);
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < limit; i++) result.add(compactForScreen(list.get(i), depth + 1, key));
            if (list.size() > limit) result.add("[화면 표시 생략: " + (list.size() - limit) + "건 추가 데이터]");
            return result;
        }
        return truncateForScreen(String.valueOf(value), 1000);
    }

    private String truncateForScreen(String text, int maxLength) {
        if (!StringUtils.hasText(text)) return text;
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "\n... 화면 응답에서는 생략됨. DB에는 전체 결과가 저장되어 있습니다.";
    }

    private boolean isHeavyScreenKey(String key) {
        if (key == null) return false;
        String k = key.toLowerCase();
        return k.contains("rawtext")
                || k.contains("enrichedattachmenttext")
                || k.contains("attachmenttext")
                || k.contains("fulltext")
                || k.equals("content")
                || k.equals("documenttext")
                || k.equals("embedding")
                || k.equals("vector")
                || k.equals("result_json");
    }

    private List<MultipartFile> collectFiles(List<MultipartFile> files) {
        List<MultipartFile> safe = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) if (f != null && !f.isEmpty()) safe.add(f);
        }
        return safe;
    }

    private Map<String, Object> findMaterial(List<Map<String, Object>> materials, String filename) {
        for (Map<String, Object> m : materials) {
            String f = RagJsonUtils.stringValue(m, "filename");
            if (filename != null && filename.equals(f)) return m;
        }
        return materials.isEmpty() ? Map.of() : materials.get(0);
    }

    private String resolveTopic(Map<String, Object> session) {
        return firstText(RagJsonUtils.stringValue(session, "topic"), RagJsonUtils.stringValue(session, "title"), "미지정 주제");
    }

    private String firstText(String... values) {
        if (values == null) return "";
        for (String value : values) if (StringUtils.hasText(value)) return value.trim();
        return "";
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); }
        catch (Exception e) { return fallback; }
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
        return result;
    }

    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) return cast(map);
        return new LinkedHashMap<>();
    }
}
