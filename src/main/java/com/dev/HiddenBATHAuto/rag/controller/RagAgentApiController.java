package com.dev.HiddenBATHAuto.rag.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.service.RagAgentChangeSetService;

@RestController
@RequestMapping("/admin/rag/api/agent")
@ConditionalOnBean(RagRepository.class)
public class RagAgentApiController {

    private final RagAgentChangeSetService changeSetService;

    public RagAgentApiController(RagAgentChangeSetService changeSetService) {
        this.changeSetService = changeSetService;
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
