package com.dev.HiddenBATHAuto.rag.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.service.RagCanonicalJobService;
import com.dev.HiddenBATHAuto.rag.service.RagCanonicalKnowledgeService;
import com.dev.HiddenBATHAuto.rag.service.RagCanonicalQuoteService;

@RestController
@RequestMapping("/admin/rag/api/canonical")
@ConditionalOnBean(RagRepository.class)
public class RagCanonicalApiController {

    private final RagCanonicalKnowledgeService canonicalKnowledgeService;
    private final RagCanonicalJobService canonicalJobService;
    private final RagCanonicalQuoteService canonicalQuoteService;

    public RagCanonicalApiController(RagCanonicalKnowledgeService canonicalKnowledgeService,
                                     RagCanonicalJobService canonicalJobService,
                                     RagCanonicalQuoteService canonicalQuoteService) {
        this.canonicalKnowledgeService = canonicalKnowledgeService;
        this.canonicalJobService = canonicalJobService;
        this.canonicalQuoteService = canonicalQuoteService;
    }

    @PostMapping("/{projectId}/{versionId}/rebuild")
    public Map<String, Object> rebuild(@PathVariable UUID projectId,
                                       @PathVariable UUID versionId,
                                       @RequestParam(value = "instruction", required = false, defaultValue = "정본 데이터 재구성") String instruction,
                                       @RequestParam(value = "activate", required = false, defaultValue = "true") boolean activate) {
        return canonicalKnowledgeService.rebuild(projectId, versionId, null, null, instruction, activate);
    }

    @PostMapping("/{projectId}/{versionId}/rebuild-async")
    public Map<String, Object> rebuildAsync(@PathVariable UUID projectId,
                                            @PathVariable UUID versionId,
                                            @RequestParam(value = "sessionId", required = false) UUID sessionId,
                                            @RequestParam(value = "instruction", required = false, defaultValue = "정본 데이터 비동기 재구성") String instruction) {
        return canonicalJobService.submitRebuild(projectId, versionId, sessionId, instruction);
    }

    @GetMapping("/{projectId}/{versionId}/jobs")
    public List<Map<String, Object>> jobs(@PathVariable UUID projectId,
                                          @PathVariable UUID versionId,
                                          @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return canonicalJobService.findJobs(projectId, versionId, limit);
    }

    @GetMapping("/jobs/{jobId}")
    public Map<String, Object> job(@PathVariable UUID jobId) {
        return canonicalJobService.findJob(jobId);
    }

    @GetMapping("/{projectId}/{versionId}/summary")
    public Map<String, Object> summary(@PathVariable UUID projectId,
                                       @PathVariable UUID versionId) {
        return canonicalKnowledgeService.summary(projectId, versionId);
    }

    @PostMapping("/{projectId}/{versionId}/quote")
    public Map<String, Object> quote(@PathVariable UUID projectId,
                                     @PathVariable UUID versionId,
                                     @RequestBody Map<String, Object> request) {
        return canonicalQuoteService.quote(projectId, versionId, request);
    }
}
