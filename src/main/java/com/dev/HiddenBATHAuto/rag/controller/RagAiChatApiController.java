package com.dev.HiddenBATHAuto.rag.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.dto.RagAiChatMessageRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagAiChatStartRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagInquiryRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagResetStepRequest;
import com.dev.HiddenBATHAuto.rag.service.RagDynamicChatService;
import com.dev.HiddenBATHAuto.rag.service.RagKnowledgeInteractionService;

@RestController
@RequestMapping("/admin/rag/api/ai-chat")
public class RagAiChatApiController {

    private final RagDynamicChatService chatService;
    private final RagKnowledgeInteractionService interactionService;

    public RagAiChatApiController(RagDynamicChatService chatService,
                                  RagKnowledgeInteractionService interactionService) {
        this.chatService = chatService;
        this.interactionService = interactionService;
    }

    @PostMapping("/start")
    public Map<String, Object> start(@RequestBody RagAiChatStartRequest request) {
        return chatService.start(request.projectId(), request.userLabel());
    }

    @PostMapping("/message")
    public Map<String, Object> message(@RequestBody RagAiChatMessageRequest request) {
        Map<String, Object> routed = interactionService.routeChatInput(request.sessionId(), request.message(), List.of());
        if (Boolean.TRUE.equals(routed.get("handled"))) {
            return routed;
        }
        return chatService.message(request.sessionId(), request.message());
    }

    @PostMapping("/{sessionId}/message")
    public Map<String, Object> messageByPath(@PathVariable UUID sessionId,
                                             @RequestBody RagAiChatMessageRequest request) {
        Map<String, Object> routed = interactionService.routeChatInput(sessionId, request.message(), List.of());
        if (Boolean.TRUE.equals(routed.get("handled"))) {
            return routed;
        }
        return chatService.message(sessionId, request.message());
    }

    @PostMapping(path = "/{sessionId}/message-with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> messageWithFiles(@PathVariable UUID sessionId,
                                                @RequestParam(value = "message", required = false) String message,
                                                @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                                @RequestPart(value = "file", required = false) MultipartFile singleFile) {
        List<MultipartFile> merged = new ArrayList<>();
        if (files != null) merged.addAll(files);
        if (singleFile != null && !singleFile.isEmpty()) merged.add(singleFile);
        Map<String, Object> routed = interactionService.routeChatInput(sessionId, message, merged);
        if (Boolean.TRUE.equals(routed.get("handled"))) {
            return routed;
        }
        return chatService.messageWithFiles(sessionId, message, merged);
    }

    @PostMapping("/{sessionId}/reset-step")
    public Map<String, Object> resetStep(@PathVariable UUID sessionId,
                                         @RequestBody RagResetStepRequest request) {
        return chatService.resetStep(sessionId, request.stepKey(), request.reason());
    }

    @PostMapping("/inquiry")
    public Map<String, Object> inquiry(@RequestBody RagInquiryRequest request) {
        return chatService.saveInquiry(
                request.sessionId(),
                request.companyName(),
                request.customerName(),
                request.phone(),
                request.email(),
                request.memo()
        );
    }
}
