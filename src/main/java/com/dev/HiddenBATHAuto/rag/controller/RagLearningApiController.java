package com.dev.HiddenBATHAuto.rag.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.dto.RagLearningMessageRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagLearningResetKnowledgeRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagLearningSessionStartRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagTextLearningRequest;
import com.dev.HiddenBATHAuto.rag.service.RagAssetService;
import com.dev.HiddenBATHAuto.rag.service.RagConversationalLearningService;
import com.dev.HiddenBATHAuto.rag.service.RagLearningConversationService;
import com.dev.HiddenBATHAuto.rag.service.RagStructuredLearningService;

@RestController
@RequestMapping("/admin/rag/api")
public class RagLearningApiController {

    private final RagLearningConversationService learningService;
    private final RagConversationalLearningService conversationalLearningService;
    private final RagStructuredLearningService structuredLearningService;
    private final RagAssetService assetService;

    public RagLearningApiController(RagLearningConversationService learningService,
                                    RagConversationalLearningService conversationalLearningService,
                                    RagStructuredLearningService structuredLearningService,
                                    RagAssetService assetService) {
        this.learningService = learningService;
        this.conversationalLearningService = conversationalLearningService;
        this.structuredLearningService = structuredLearningService;
        this.assetService = assetService;
    }

    @PostMapping("/learning/sessions")
    public Map<String, Object> start(@RequestBody RagLearningSessionStartRequest request) {
        return learningService.start(request);
    }

    @PostMapping("/learning/sessions/{sessionId}/messages")
    public Map<String, Object> message(@PathVariable UUID sessionId,
                                       @RequestBody RagLearningMessageRequest request) {
        return learningService.message(sessionId, request);
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/learn-text")
    public Map<String, Object> learnText(@PathVariable UUID projectId,
                                         @PathVariable UUID versionId,
                                         @RequestBody RagTextLearningRequest request) {
        return learningService.learnText(projectId, versionId, request);
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/learn-excel")
    public Map<String, Object> learnExcel(@PathVariable UUID projectId,
                                          @PathVariable UUID versionId,
                                          @RequestParam(required = false) String topic,
                                          @RequestParam(required = false) String learningDirection,
                                          @RequestParam MultipartFile file) {
        return learningService.learnExcel(projectId, versionId, topic, learningDirection, file);
    }

    /**
     * 정확 가격계산용 엑셀 구조화 저장 API입니다.
     * 기존 learn-excel은 벡터/요약 지식 생성용이고,
     * 이 API는 1차원 표/2차원 W-D 단가표를 DB 테이블에 저장합니다.
     */
    @PostMapping("/projects/{projectId}/versions/{versionId}/learn-structured-excel")
    public Map<String, Object> learnStructuredExcel(@PathVariable UUID projectId,
                                                    @PathVariable UUID versionId,
                                                    @RequestParam(required = false) String topic,
                                                    @RequestParam(required = false) String instruction,
                                                    @RequestParam MultipartFile file) {
        return structuredLearningService.ingestUploadedKnowledge(projectId, versionId, topic, instruction, file);
    }

    @PostMapping("/learning/sessions/{sessionId}/reset-knowledge")
    public Map<String, Object> resetKnowledgeBySession(@PathVariable UUID sessionId,
                                                       @RequestBody RagLearningResetKnowledgeRequest request) {
        return conversationalLearningService.resetKnowledge(
                sessionId,
                request.topic(),
                request.reason(),
                Boolean.TRUE.equals(request.resetWholeVersion())
        );
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/reset-knowledge")
    public Map<String, Object> resetKnowledgeByVersion(@PathVariable UUID projectId,
                                                       @PathVariable UUID versionId,
                                                       @RequestBody RagLearningResetKnowledgeRequest request) {
        structuredLearningService.resetStructuredKnowledge(
                projectId,
                versionId,
                request.topic(),
                Boolean.TRUE.equals(request.resetWholeVersion()),
                request.reason()
        );
        return conversationalLearningService.resetKnowledge(
                projectId,
                versionId,
                request.topic(),
                request.reason(),
                Boolean.TRUE.equals(request.resetWholeVersion())
        );
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/assets")
    public Map<String, Object> uploadAsset(@PathVariable UUID projectId,
                                           @PathVariable UUID versionId,
                                           @RequestParam String ownerType,
                                           @RequestParam String ownerKey,
                                           @RequestParam(required = false) String note,
                                           @RequestParam MultipartFile file) {
        return assetService.upload(projectId, versionId, ownerType, ownerKey, note, file);
    }

    @GetMapping("/projects/{projectId}/versions/{versionId}/assets")
    public Object assets(@PathVariable UUID projectId,
                         @PathVariable UUID versionId,
                         @RequestParam(required = false) String ownerType,
                         @RequestParam(required = false) String ownerKey) {
        return assetService.findAssets(projectId, versionId, ownerType, ownerKey);
    }
}
