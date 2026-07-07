package com.dev.HiddenBATHAuto.rag.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.repository.RagRepository;
import com.dev.HiddenBATHAuto.rag.service.RagKnowledgeInteractionService;

@RestController
@RequestMapping("/admin/rag/api/knowledge-interaction")
@ConditionalOnBean(RagRepository.class)
@PreAuthorize("hasRole('ADMIN')")
public class RagKnowledgeInteractionApiController {

    private final RagKnowledgeInteractionService interactionService;

    public RagKnowledgeInteractionApiController(RagKnowledgeInteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping(value = "/learning/{sessionId}/route", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> routeLearningInput(@PathVariable UUID sessionId,
                                                  @RequestParam(value = "message", required = false, defaultValue = "") String message,
                                                  @RequestParam(value = "forceSave", required = false, defaultValue = "false") boolean forceSave,
                                                  @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                                  @RequestPart(value = "file", required = false) MultipartFile singleFile) {
        return interactionService.routeLearningInput(sessionId, message, forceSave, collectFiles(files, singleFile));
    }

    @PostMapping(value = "/chat/{sessionId}/route", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> routeChatInput(@PathVariable UUID sessionId,
                                              @RequestParam(value = "message", required = false, defaultValue = "") String message,
                                              @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                              @RequestPart(value = "file", required = false) MultipartFile singleFile) {
        return interactionService.routeChatInput(sessionId, message, collectFiles(files, singleFile));
    }

    @GetMapping("/summary")
    public Map<String, Object> summarizeKnowledge(@RequestParam UUID projectId,
                                                  @RequestParam UUID versionId,
                                                  @RequestParam(value = "q", required = false, defaultValue = "") String query) {
        return interactionService.summarizeKnowledge(projectId, versionId, query, null, "API");
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
