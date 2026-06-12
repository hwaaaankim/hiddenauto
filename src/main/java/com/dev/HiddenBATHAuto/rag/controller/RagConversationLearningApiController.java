package com.dev.HiddenBATHAuto.rag.controller;

import java.util.ArrayList;
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
import com.dev.HiddenBATHAuto.rag.service.RagLearningJobService;
import com.dev.HiddenBATHAuto.rag.service.RagStructuredLearningService;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;

@RestController
@RequestMapping("/admin/rag/api/learning-conversation")
@ConditionalOnBean(RagRepository.class)
public class RagConversationLearningApiController {

    private final RagConversationalLearningService learningService;
    private final RagStructuredLearningService structuredLearningService;
    private final RagLearningJobService jobService;

    public RagConversationLearningApiController(RagConversationalLearningService learningService,
                                                RagStructuredLearningService structuredLearningService,
                                                RagLearningJobService jobService) {
        this.learningService = learningService;
        this.structuredLearningService = structuredLearningService;
        this.jobService = jobService;
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestBody RagConversationLearningStartRequest request) {
        return learningService.start(request.projectId(), request.versionId(), request.title(), request.topic());
    }

    /**
     * 메시지 학습 요청은 즉시 GPT를 호출하지 않고 작업만 생성합니다.
     * 실제 GPT 해석/검증/병합/벡터화는 백그라운드 스레드에서 진행되며 화면은 jobs/{jobId}를 폴링합니다.
     */
    @PostMapping("/{sessionId}/message")
    public Map<String, Object> message(@PathVariable UUID sessionId,
                                       @RequestBody RagConversationLearningMessageRequest request) {
        return jobService.submitMessage(
                sessionId,
                request.message(),
                request.attachmentText(),
                request.attachmentFilename(),
                Boolean.TRUE.equals(request.forceSave())
        );
    }

    /** 기존 단일 파일 호출부 호환용입니다. */
    @PostMapping(value = "/{sessionId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> file(@PathVariable UUID sessionId,
                                    @RequestParam(value = "message", required = false) String message,
                                    @RequestParam(value = "forceSave", required = false, defaultValue = "false") boolean forceSave,
                                    @RequestParam("file") MultipartFile file) {
        return jobService.submitMessageWithFiles(sessionId, message, forceSave, collectFiles(null, file));
    }

    /**
     * 드래그앤드랍 학습 전용입니다.
     * 파일은 요청 중 먼저 안정적인 서버 저장소에 보존하고, 이후 백그라운드 작업에서 파싱/GPT/구조화 저장을 진행합니다.
     */
    @PostMapping(value = "/{sessionId}/message-with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> messageWithFiles(@PathVariable UUID sessionId,
                                                @RequestParam(value = "message", required = false) String message,
                                                @RequestParam(value = "forceSave", required = false, defaultValue = "false") boolean forceSave,
                                                @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                                @RequestPart(value = "file", required = false) MultipartFile singleFile) {
        List<MultipartFile> safeFiles = collectFiles(files, singleFile);
        if (safeFiles.isEmpty()) {
            return jobService.submitMessage(sessionId, message, null, null, forceSave);
        }
        return jobService.submitMessageWithFiles(sessionId, message, forceSave, safeFiles);
    }

    /**
     * 이전 학습에서 GPT timeout 등으로 서버 추출/재해석 대기 상태로 남은 노드들을
     * 별도 백그라운드 작업으로 다시 더 작게 쪼개 AI_PARSED 노드로 승격합니다.
     */
    @PostMapping("/{sessionId}/retry-raw-nodes")
    public Map<String, Object> retryRawNodes(@PathVariable UUID sessionId,
                                             @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return jobService.submitRetryRawNodes(sessionId, limit);
    }

    @PostMapping("/{sessionId}/knowledge-nodes/{nodeId}/retry")
    public Map<String, Object> retryOneNode(@PathVariable UUID sessionId,
                                            @PathVariable UUID nodeId) {
        return jobService.submitRetryOneNode(sessionId, nodeId);
    }

    @GetMapping("/{sessionId}/jobs/{jobId}")
    public Map<String, Object> job(@PathVariable UUID sessionId,
                                   @PathVariable UUID jobId) {
        Map<String, Object> job = jobService.findJob(jobId);
        if (!sessionId.equals(job.get("session_id"))) {
            throw new IllegalArgumentException("요청한 세션의 학습 작업이 아닙니다.");
        }
        return job;
    }

    @GetMapping("/{sessionId}/jobs")
    public List<Map<String, Object>> jobs(@PathVariable UUID sessionId,
                                          @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return jobService.findJobsBySession(sessionId, limit);
    }

    @PostMapping("/{sessionId}/reset-knowledge")
    public Map<String, Object> resetKnowledge(@PathVariable UUID sessionId,
                                              @RequestBody RagLearningResetKnowledgeRequest request) {
        Map<String, Object> session = learningService.session(sessionId);
        UUID projectId = (UUID) session.get("project_id");
        UUID versionId = (UUID) session.get("version_id");
        String sessionTopic = RagJsonUtils.stringValue(session, "topic");
        String topic = request.topic() == null || request.topic().isBlank() ? sessionTopic : request.topic();

        Map<String, Object> result = learningService.resetKnowledge(
                sessionId,
                topic,
                request.reason(),
                Boolean.TRUE.equals(request.resetWholeVersion())
        );
        structuredLearningService.resetStructuredKnowledge(
                projectId,
                versionId,
                topic,
                Boolean.TRUE.equals(request.resetWholeVersion()),
                request.reason()
        );
        result.put("structuredReset", true);
        return result;
    }

    private List<MultipartFile> collectFiles(List<MultipartFile> files, MultipartFile singleFile) {
        List<MultipartFile> safeFiles = new ArrayList<>();
        if (files != null) {
            for (MultipartFile f : files) {
                if (f != null && !f.isEmpty()) safeFiles.add(f);
            }
        }
        if (singleFile != null && !singleFile.isEmpty()) safeFiles.add(singleFile);
        return safeFiles;
    }
}
