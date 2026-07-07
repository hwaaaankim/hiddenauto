package com.dev.HiddenBATHAuto.rag.controller;

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
import com.dev.HiddenBATHAuto.rag.service.RagAgentSchemaService;

@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/admin/rag/api/agent")
@ConditionalOnBean(RagRepository.class)
public class RagAgentApiController {

    private final RagAgentChangeSetService changeSetService;
    private final RagAgentSchemaService schemaService;
    private final RagAgentAuditQueryService auditQueryService;

    public RagAgentApiController(RagAgentChangeSetService changeSetService,
                                 RagAgentSchemaService schemaService,
                                 RagAgentAuditQueryService auditQueryService) {
        this.changeSetService = changeSetService;
        this.schemaService = schemaService;
        this.auditQueryService = auditQueryService;
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
}
