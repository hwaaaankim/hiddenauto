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

import com.dev.HiddenBATHAuto.rag.dto.RagProjectCreateRequest;
import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.service.RagProjectService;

@RestController
@RequestMapping("/admin/rag/api")
@ConditionalOnBean(RagRepository.class)
public class RagProjectApiController {

    private final RagProjectService projectService;

    public RagProjectApiController(RagProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/projects")
    public List<Map<String, Object>> projects() {
        return projectService.findProjects();
    }

    @PostMapping("/projects")
    public Map<String, Object> createProject(@RequestBody RagProjectCreateRequest request) {
        return projectService.createProject(request);
    }

    @GetMapping("/projects/{projectId}")
    public Map<String, Object> project(@PathVariable UUID projectId) {
        return projectService.detailProject(projectId);
    }

    @GetMapping("/projects/{projectId}/versions")
    public List<Map<String, Object>> versions(@PathVariable UUID projectId) {
        return (List<Map<String, Object>>) projectService.detailProject(projectId).get("versions");
    }

    @PostMapping("/projects/{projectId}/versions")
    public Map<String, Object> createVersion(@PathVariable UUID projectId,
                                             @RequestParam(required = false) String learningDirection) {
        return projectService.createNewVersion(projectId, learningDirection);
    }

    @GetMapping("/versions/{versionId}")
    public Map<String, Object> version(@PathVariable UUID versionId) {
        return projectService.version(versionId);
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/publish")
    public Map<String, Object> publish(@PathVariable UUID projectId,
                                       @PathVariable UUID versionId) {
        return projectService.publish(projectId, versionId);
    }
}
