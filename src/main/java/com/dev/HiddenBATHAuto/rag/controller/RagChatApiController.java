package com.dev.HiddenBATHAuto.rag.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.rag.dto.RagChatMessageRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagChatStartRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagInquiryRequest;
import com.dev.HiddenBATHAuto.rag.dto.RagResetStepRequest;
import com.dev.HiddenBATHAuto.rag.service.RagDynamicChatService;

/**
 * 구형 /admin/rag/api/chat 호출부 호환 컨트롤러입니다.
 * 실제 상담/자동학습 처리는 RagDynamicChatService 한 곳으로 통합합니다.
 */
@RestController
@RequestMapping("/admin/rag/api/chat")
public class RagChatApiController {

    private final RagDynamicChatService chatService;

    public RagChatApiController(RagDynamicChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sessions")
    public Map<String, Object> start(@RequestBody RagChatStartRequest request) {
        return chatService.start(request.projectId(), request.userLabel());
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> message(@PathVariable UUID sessionId,
                                       @RequestBody RagChatMessageRequest request) {
        return chatService.message(sessionId, request.content());
    }

    @PostMapping("/sessions/{sessionId}/reset-step")
    public Map<String, Object> resetStep(@PathVariable UUID sessionId,
                                         @RequestBody RagResetStepRequest request) {
        return chatService.resetStep(sessionId, request.stepKey(), request.reason());
    }

    @PostMapping("/sessions/{sessionId}/inquiry")
    public Map<String, Object> inquiry(@PathVariable UUID sessionId,
                                       @RequestBody RagInquiryRequest request) {
        return chatService.saveInquiry(
                sessionId,
                request.companyName(),
                request.customerName(),
                request.phone(),
                request.email(),
                request.memo()
        );
    }
}
