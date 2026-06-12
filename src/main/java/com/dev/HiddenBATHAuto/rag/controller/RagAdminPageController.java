package com.dev.HiddenBATHAuto.rag.controller;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/rag")
public class RagAdminPageController {

    @GetMapping("/chat")
    public String chat() {
        return "administration/rag/rag-chat";
    }

    @GetMapping("/learning")
    public String learningHome() {
        return "administration/rag/rag-learning-home";
    }

    @GetMapping("/learning/{projectId}/{versionId}")
    public String learningBuilder(@PathVariable UUID projectId,
                                  @PathVariable UUID versionId,
                                  Model model) {
        model.addAttribute("projectId", projectId);
        model.addAttribute("versionId", versionId);
        return "administration/rag/rag-learning-builder";
    }
}
