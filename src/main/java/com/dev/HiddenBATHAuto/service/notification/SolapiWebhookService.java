package com.dev.HiddenBATHAuto.service.notification;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.enums.notification.NotificationSendStatus;
import com.dev.HiddenBATHAuto.model.notification.NotificationMessageLog;
import com.dev.HiddenBATHAuto.model.notification.NotificationWebhookLog;
import com.dev.HiddenBATHAuto.repository.notification.NotificationMessageLogRepository;
import com.dev.HiddenBATHAuto.repository.notification.NotificationWebhookLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SolapiWebhookService {

    private final NotificationWebhookLogRepository webhookLogRepository;
    private final NotificationMessageLogRepository messageLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveAndApply(String eventName, boolean secretValid, JsonNode payload) {
        if (payload == null || !payload.isArray()) {
            saveRawWebhook(eventName, secretValid, null);
            return;
        }

        for (JsonNode event : payload) {
            saveSingleEvent(eventName, secretValid, event);
            applyMessageReport(eventName, event);
        }
    }

    private void saveSingleEvent(String eventName, boolean secretValid, JsonNode event) {
        NotificationWebhookLog log = new NotificationWebhookLog();
        log.setEventName(eventName);
        log.setSecretValid(secretValid);
        log.setSolapiMessageId(text(event, "messageId"));
        log.setSolapiGroupId(text(event, "groupId"));
        log.setStatusCode(text(event, "statusCode"));
        log.setStatusMessage(text(event, "statusMessage"));
        log.setRawJson(toJson(event));
        log.setReceivedAt(LocalDateTime.now());
        webhookLogRepository.save(log);
    }

    private void saveRawWebhook(String eventName, boolean secretValid, JsonNode payload) {
        NotificationWebhookLog log = new NotificationWebhookLog();
        log.setEventName(eventName);
        log.setSecretValid(secretValid);
        log.setRawJson(toJson(payload));
        log.setReceivedAt(LocalDateTime.now());
        webhookLogRepository.save(log);
    }

    private void applyMessageReport(String eventName, JsonNode event) {
        String messageId = text(event, "messageId");
        String requestKey = event.path("customFields").path("requestKey").asText(null);

        Optional<NotificationMessageLog> optionalLog = Optional.empty();

        if (messageId != null && !messageId.isBlank()) {
            optionalLog = messageLogRepository.findFirstBySolapiMessageId(messageId);
        }

        if (optionalLog.isEmpty() && requestKey != null && !requestKey.isBlank()) {
            optionalLog = messageLogRepository.findByRequestKey(requestKey);
        }

        if (optionalLog.isEmpty()) {
            return;
        }

        NotificationMessageLog log = optionalLog.get();

        String statusCode = text(event, "statusCode");
        String statusMessage = text(event, "statusMessage");

        log.setWebhookBodyJson(toJson(event));
        log.setSolapiMessageId(messageId != null ? messageId : log.getSolapiMessageId());
        log.setSolapiGroupId(text(event, "groupId"));
        log.setStatusCode(statusCode);
        log.setStatusMessage(statusMessage);
        log.setReportedAt(LocalDateTime.now());

        Boolean replacement = event.has("replacement") && !event.get("replacement").isNull()
                ? event.get("replacement").asBoolean()
                : null;
        log.setReplacement(replacement);

        if ("4000".equals(statusCode)) {
            log.setSendStatus(NotificationSendStatus.DELIVERY_SUCCESS);
            log.setCompletedAt(LocalDateTime.now());
        } else if (statusCode != null && (statusCode.startsWith("1") || statusCode.startsWith("2") || statusCode.startsWith("3") || statusCode.startsWith("5"))) {
            log.setSendStatus(NotificationSendStatus.DELIVERY_FAILED);
            log.setFailureReason(statusMessage);
            log.setCompletedAt(LocalDateTime.now());
        } else {
            log.setSendStatus(NotificationSendStatus.WEBHOOK_RECEIVED);
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }

        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}