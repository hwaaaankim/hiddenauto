package com.dev.HiddenBATHAuto.rag.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        Map<String, Object> created = chatService.start(request.projectId(), request.userLabel());
        UUID sessionId = (UUID) created.get("sessionId");
        Map<String, Object> gpt = interactionService.routeChatSystemEvent(
                sessionId,
                "SESSION_STARTED",
                Map.of(
                        "userLabel", request.userLabel() == null ? "" : request.userLabel(),
                        "instruction", "현재 저장된 주문 프로세스와 제품 지식을 확인하여 자연스러운 첫 안내 또는 첫 질문을 작성합니다."
                ));
        Map<String, Object> result = new LinkedHashMap<>(created);
        result.putAll(gpt);
        result.put("sessionId", sessionId);
        result.put("session", created.get("session"));
        result.put("state", created.get("state"));
        result.put("version", created.get("version"));
        return result;
    }

    @PostMapping("/message")
    public Map<String, Object> message(@RequestBody RagAiChatMessageRequest request) {
        return interactionService.routeChatInput(request.sessionId(), request.message(), List.of());
    }

    @PostMapping("/{sessionId}/message")
    public Map<String, Object> messageByPath(@PathVariable UUID sessionId,
                                             @RequestBody RagAiChatMessageRequest request) {
        return interactionService.routeChatInput(sessionId, request.message(), List.of());
    }

    @PostMapping(path = "/{sessionId}/message-with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> messageWithFiles(@PathVariable UUID sessionId,
                                                @RequestParam(value = "message", required = false) String message,
                                                @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                                @RequestPart(value = "file", required = false) MultipartFile singleFile) {
        List<MultipartFile> merged = new ArrayList<>();
        if (files != null) files.stream().filter(f -> f != null && !f.isEmpty()).forEach(merged::add);
        if (singleFile != null && !singleFile.isEmpty()) merged.add(singleFile);
        return interactionService.routeChatInput(sessionId, message, merged);
    }

    @PostMapping("/{sessionId}/reset-step")
    public Map<String, Object> resetStep(@PathVariable UUID sessionId,
                                         @RequestBody RagResetStepRequest request) {
        Map<String, Object> reset = chatService.resetStep(sessionId, request.stepKey(), request.reason());
        Map<String, Object> gpt = interactionService.routeChatSystemEvent(
                sessionId,
                "STEP_RESET",
                Map.of(
                        "stepKey", request.stepKey() == null ? "DOMAIN_SELECT" : request.stepKey(),
                        "reason", request.reason() == null ? "" : request.reason(),
                        "state", reset.getOrDefault("state", Map.of())
                ));
        Map<String, Object> result = new LinkedHashMap<>(reset);
        result.putAll(gpt);
        return result;
    }

    @PostMapping("/inquiry")
    public Map<String, Object> inquiry(@RequestBody RagInquiryRequest request) {
        Map<String, Object> saved = chatService.saveInquiry(
                request.sessionId(), request.companyName(), request.customerName(),
                request.phone(), request.email(), request.memo());
        Map<String, Object> result = new LinkedHashMap<>(saved);
        result.remove("message");
        result.put("responseType", "INQUIRY_SAVED");
        return result;
    }
}
