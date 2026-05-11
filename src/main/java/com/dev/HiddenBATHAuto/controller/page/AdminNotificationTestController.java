package com.dev.HiddenBATHAuto.controller.page;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.dev.HiddenBATHAuto.dto.notification.NotificationSendCommand;
import com.dev.HiddenBATHAuto.dto.notification.NotificationSendResult;
import com.dev.HiddenBATHAuto.service.notification.NotificationMessageService;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/notification")
public class AdminNotificationTestController {

    private final NotificationMessageService notificationMessageService;

    @GetMapping("/test")
    public String testPage() {
        return "administration/notification/notification-test";
    }

    @PostMapping("/test-send")
    @ResponseBody
    public ResponseEntity<NotificationSendResult> testSend(@RequestBody NotificationTestSendRequest request) {
        Map<String, String> variables = new LinkedHashMap<>();

        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }

        NotificationSendResult result = notificationMessageService.sendAlimtalk(NotificationSendCommand.builder()
                .templateCode(request.getTemplateCode())
                .from(request.getFrom())
                .to(request.getTo())
                .messageTextForLog(request.getMessageTextForLog())
                .variables(variables)
                .businessType("ADMIN_TEST")
                .businessId(null)
                .requestedByMemberId(null)
                .requestedByUsername("admin-test")
                .build());

        return ResponseEntity.ok(result);
    }

    @Getter
    @Setter
    public static class NotificationTestSendRequest {
        private String templateCode;
        private String from;
        private String to;
        private String messageTextForLog;
        private Map<String, String> variables;
    }
}