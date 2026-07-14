package com.dev.HiddenBATHAuto.rag.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.service.RagAgentAuditQueryService;
import com.dev.HiddenBATHAuto.rag.service.RagAgentChangeSetService;
import com.dev.HiddenBATHAuto.rag.service.RagAgentRunState;
import com.dev.HiddenBATHAuto.rag.service.RagAgentSchemaService;
import com.dev.HiddenBATHAuto.rag.service.RagAgentToolContext;
import com.dev.HiddenBATHAuto.rag.service.RagSemanticIndexWorker;
import com.dev.HiddenBATHAuto.rag.service.RagSemanticMemoryService;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/admin/rag/api/agent")
@ConditionalOnBean(RagRepository.class)
public class RagAgentApiController {

    private final RagAgentChangeSetService changeSetService;
    private final RagAgentSchemaService schemaService;
    private final RagAgentAuditQueryService auditQueryService;
    private final RagSemanticMemoryService semanticMemoryService;
    private final RagSemanticIndexWorker semanticIndexWorker;

    public RagAgentApiController(RagAgentChangeSetService changeSetService,
                                 RagAgentSchemaService schemaService,
                                 RagAgentAuditQueryService auditQueryService,
                                 RagSemanticMemoryService semanticMemoryService,
                                 RagSemanticIndexWorker semanticIndexWorker) {
        this.changeSetService = changeSetService;
        this.schemaService = schemaService;
        this.auditQueryService = auditQueryService;
        this.semanticMemoryService = semanticMemoryService;
        this.semanticIndexWorker = semanticIndexWorker;
    }

    @GetMapping("/database/overview")
    public Map<String, Object> databaseOverview(@RequestParam UUID projectId,
                                                 @RequestParam UUID versionId) {
        return schemaService.bootstrapContext(projectId, versionId);
    }

    @GetMapping("/database/catalog")
    public List<Map<String, Object>> databaseCatalog(@RequestParam UUID projectId,
                                                      @RequestParam UUID versionId,
                                                      @RequestParam(defaultValue = "") String query,
                                                      @RequestParam(required = false) List<String> objectTypes,
                                                      @RequestParam(defaultValue = "100") int limit) {
        return schemaService.searchCatalog(projectId, versionId, query, objectTypes, limit);
    }

    @GetMapping("/database/tables/{tableName}")
    public Map<String, Object> describeTable(@PathVariable String tableName,
                                              @RequestParam UUID projectId,
                                              @RequestParam UUID versionId,
                                              @RequestParam(defaultValue = "public") String schemaName,
                                              @RequestParam(defaultValue = "5") int sampleLimit) {
        return schemaService.describeTable(schemaName, tableName, projectId, versionId, sampleLimit);
    }

    @GetMapping("/database/relationships")
    public List<Map<String, Object>> relationships(
            @RequestParam(defaultValue = "public") String schemaName,
            @RequestParam(required = false) String tableName) {
        return schemaService.relationships(schemaName, tableName);
    }

    @GetMapping("/database/tables/{tableName}/statistics")
    public Map<String, Object> statistics(@PathVariable String tableName,
                                           @RequestParam UUID projectId,
                                           @RequestParam UUID versionId,
                                           @RequestParam(defaultValue = "public") String schemaName,
                                           @RequestParam(defaultValue = "false") boolean exactCount) {
        return schemaService.tableStatistics(schemaName, tableName, projectId, versionId, exactCount);
    }

    @GetMapping("/runs")
    public List<Map<String, Object>> recentRuns(@RequestParam UUID projectId,
                                                @RequestParam UUID versionId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "50") int limit) {
        return auditQueryService.recentRuns(projectId, versionId, status, limit);
    }

    @GetMapping("/runs/{runId}")
    public Map<String, Object> runDetail(@PathVariable UUID runId) {
        return auditQueryService.runDetail(runId);
    }

    @GetMapping("/change-sets/{changeSetId}")
    public Map<String, Object> changeSet(@PathVariable UUID changeSetId) {
        return changeSetService.detail(changeSetId);
    }

    @PostMapping("/change-sets/{changeSetId}/apply")
    public Map<String, Object> applyChangeSet(@PathVariable UUID changeSetId,
                                              @RequestParam(value = "force", defaultValue = "false") boolean force) {
        return changeSetService.applyExisting(changeSetId, force);
    }
    @GetMapping("/semantic/status")
    public Map<String, Object> semanticStatus(@RequestParam UUID projectId,
                                               @RequestParam UUID versionId) {
        return semanticMemoryService.status(projectId, versionId);
    }

    @GetMapping("/semantic/inventory")
    public Map<String, Object> semanticInventory(@RequestParam UUID projectId,
                                                  @RequestParam UUID versionId,
                                                  @RequestParam(required = false) List<String> domains,
                                                  @RequestParam(defaultValue = "true") boolean exactCounts,
                                                  @RequestParam(defaultValue = "true") boolean includeSamples,
                                                  @RequestParam(defaultValue = "3") int sampleLimit) {
        return semanticMemoryService.inventory(
                adminContext(projectId, versionId), domains, exactCounts, includeSamples, sampleLimit);
    }

    @GetMapping("/semantic/search")
    public Map<String, Object> semanticSearch(@RequestParam UUID projectId,
                                               @RequestParam UUID versionId,
                                               @RequestParam String query,
                                               @RequestParam(required = false) List<String> domains,
                                               @RequestParam(required = false) List<String> sourceKinds,
                                               @RequestParam(defaultValue = "20") int limit,
                                               @RequestParam(defaultValue = "0") BigDecimal minimumScore,
                                               @RequestParam(defaultValue = "false") boolean includeInactive) {
        return semanticMemoryService.search(
                adminContext(projectId, versionId), query, domains, sourceKinds, limit, minimumScore, includeInactive);
    }

    @PostMapping("/semantic/rebuild")
    public Map<String, Object> semanticRebuild(@RequestParam UUID projectId,
                                                @RequestParam UUID versionId,
                                                @RequestParam(required = false) List<String> sourceTables) {
        return semanticMemoryService.enqueueScope(projectId, versionId, sourceTables);
    }

    @PostMapping("/semantic/process")
    public Map<String, Object> semanticProcess(@RequestParam(defaultValue = "50") int limit) {
        return semanticIndexWorker.processAvailable(limit);
    }

    private RagAgentToolContext adminContext(UUID projectId, UUID versionId) {
        return new RagAgentToolContext(
                UUID.randomUUID(), projectId, versionId, null, "LEARNING", false, 0, null, new RagAgentRunState());
    }

}
