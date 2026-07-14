package com.dev.HiddenBATHAuto.rag.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.dto.RagConversationLearningMessageRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagConversationLearningStartRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagLearningResetKnowledgeRequest;
import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.service.RagConversationalLearningService;
import com.dev.HiddenBATHAuto.rag.service.RagKnowledgeInteractionService;
import com.dev.HiddenBATHAuto.rag.service.RagLearningJobService;
import com.dev.HiddenBATHAuto.rag.service.RagStructuredLearningService;

@RestController
@RequestMapping("/admin/rag/api/learning-conversation")
@ConditionalOnBean(RagRepository.class)
public class RagConversationLearningApiController {

    private final RagConversationalLearningService learningService;
    private final RagStructuredLearningService structuredLearningService;
    private final RagLearningJobService jobService;
    private final RagKnowledgeInteractionService interactionService;

    public RagConversationLearningApiController(RagConversationalLearningService learningService,
                                                RagStructuredLearningService structuredLearningService,
                                                RagLearningJobService jobService,
                                                RagKnowledgeInteractionService interactionService) {
        this.learningService = learningService;
        this.structuredLearningService = structuredLearningService;
        this.jobService = jobService;
        this.interactionService = interactionService;
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestBody RagConversationLearningStartRequest request) {
        Map<String, Object> created = learningService.start(
                request.projectId(), request.versionId(), request.title(), request.topic());
        UUID sessionId = (UUID) created.get("sessionId");
        Map<String, Object> gpt = interactionService.routeLearningSystemEvent(
                sessionId,
                "LEARNING_SESSION_STARTED",
                Map.of(
                        "title", request.title() == null ? "" : request.title(),
                        "topic", request.topic() == null ? "" : request.topic(),
                        "instruction", "현재 저장된 지식과 학습 도구를 고려하여 사용자가 규칙·가격표·주문프로세스를 입력할 수 있도록 자연스러운 첫 안내를 작성합니다."
                ));
        Map<String, Object> result = new LinkedHashMap<>(created);
        result.putAll(gpt);
        result.put("sessionId", sessionId);
        result.put("session", created.get("session"));
        return result;
    }

    @PostMapping("/{sessionId}/message")
    public Map<String, Object> message(@PathVariable UUID sessionId,
                                       @RequestBody RagConversationLearningMessageRequest request) {
        return interactionService.routeLearningInput(
                sessionId, request.message(), Boolean.TRUE.equals(request.forceSave()), List.of());
    }

    @PostMapping(value = "/{sessionId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> file(@PathVariable UUID sessionId,
                                    @RequestParam(value = "message", required = false) String message,
                                    @RequestParam(value = "forceSave", required = false, defaultValue = "false") boolean forceSave,
                                    @RequestParam("file") MultipartFile file) {
        return interactionService.routeLearningInput(sessionId, message, forceSave, collectFiles(null, file));
    }

    @PostMapping(value = "/{sessionId}/message-with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> messageWithFiles(@PathVariable UUID sessionId,
                                                @RequestParam(value = "message", required = false) String message,
                                                @RequestParam(value = "forceSave", required = false, defaultValue = "false") boolean forceSave,
                                                @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                                @RequestPart(value = "file", required = false) MultipartFile singleFile) {
        return interactionService.routeLearningInput(sessionId, message, forceSave, collectFiles(files, singleFile));
    }

    /** 관리자가 명시적으로 요청하는 기존 비동기 재색인/재해석 작업은 작업 상태 API로 유지합니다. */
    @PostMapping("/{sessionId}/retry-raw-nodes")
    public Map<String, Object> retryRawNodes(@PathVariable UUID sessionId,
                                             @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return asOperationalEvent(jobService.submitRetryRawNodes(sessionId, limit), "LEARNING_JOB_SUBMITTED");
    }

    @PostMapping("/{sessionId}/knowledge-nodes/{nodeId}/retry")
    public Map<String, Object> retryOneNode(@PathVariable UUID sessionId,
                                            @PathVariable UUID nodeId) {
        return asOperationalEvent(jobService.submitRetryOneNode(sessionId, nodeId), "LEARNING_JOB_SUBMITTED");
    }

    @GetMapping("/{sessionId}/jobs/{jobId}")
    public Map<String, Object> job(@PathVariable UUID sessionId, @PathVariable UUID jobId) {
        Map<String, Object> job = jobService.findJob(jobId);
        if (!sessionId.equals(job.get("session_id"))) {
            throw new IllegalArgumentException("요청한 세션의 학습 작업이 아닙니다.");
        }
        return asOperationalEvent(job, "LEARNING_JOB_STATUS");
    }

    @GetMapping("/{sessionId}/jobs")
    public List<Map<String, Object>> jobs(@PathVariable UUID sessionId,
                                          @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return jobService.findJobsBySession(sessionId, limit).stream()
                .map(j -> asOperationalEvent(j, "LEARNING_JOB_STATUS"))
                .toList();
    }

    @PostMapping("/{sessionId}/reset-knowledge")
    public Map<String, Object> resetKnowledge(@PathVariable UUID sessionId,
                                              @RequestBody RagLearningResetKnowledgeRequest request) {
        Map<String, Object> session = learningService.session(sessionId);
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        String reason = request.reason() == null || request.reason().isBlank()
                ? "현재 버전 전체 학습 지식 초기화" : request.reason();
        Map<String, Object> result = learningService.resetKnowledge(sessionId, null, reason, true);
        structuredLearningService.resetStructuredKnowledge(projectId, versionId, null, true, reason);
        Map<String, Object> response = new LinkedHashMap<>(result);
        response.remove("answer");
        response.put("structuredReset", true);
        response.put("responseType", "KNOWLEDGE_RESET_COMPLETED");
        response.put("answerSource", "NONE");
        return response;
    }

    private Map<String, Object> asOperationalEvent(Map<String, Object> source, String responseType) {
        Map<String, Object> result = new LinkedHashMap<>(source == null ? Map.of() : source);
        result.remove("answer");
        result.remove("message");
        result.put("responseType", responseType);
        result.put("answerSource", "NONE");
        return result;
    }

    private List<MultipartFile> collectFiles(List<MultipartFile> files, MultipartFile singleFile) {
        List<MultipartFile> safeFiles = new ArrayList<>();
        if (files != null) files.stream().filter(f -> f != null && !f.isEmpty()).forEach(safeFiles::add);
        if (singleFile != null && !singleFile.isEmpty()) safeFiles.add(singleFile);
        return safeFiles;
    }
}
