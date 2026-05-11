package com.dev.HiddenBATHAuto.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dev.HiddenBATHAuto.constant.SolapiWebhookVerifier;
import com.dev.HiddenBATHAuto.service.notification.SolapiWebhookService;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/public/solapi/webhook")
public class SolapiWebhookController {

    private final SolapiWebhookVerifier webhookVerifier;
    private final SolapiWebhookService webhookService;

    @PostMapping("/message-report")
    public ResponseEntity<String> receiveMessageReport(
            @RequestHeader(value = "X-Solapi-Event-Name", required = false) String eventName,
            @RequestHeader(value = "X-Solapi-Secret", required = false) String solapiSecret,
            @RequestBody JsonNode payload
    ) {
        boolean secretValid = webhookVerifier.isValid(solapiSecret);

        if (!secretValid) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        webhookService.saveAndApply(eventName, true, payload);

        return ResponseEntity.ok("OK");
    }
}