package com.dev.HiddenBATHAuto.service.notification;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.constant.SolapiProperties;
import com.dev.HiddenBATHAuto.dto.notification.NotificationSendCommand;
import com.dev.HiddenBATHAuto.dto.notification.NotificationSendResult;
import com.dev.HiddenBATHAuto.dto.notification.SolapiKakaoOptions;
import com.dev.HiddenBATHAuto.dto.notification.SolapiMessage;
import com.dev.HiddenBATHAuto.dto.notification.SolapiSendApiResult;
import com.dev.HiddenBATHAuto.dto.notification.SolapiSendRequest;
import com.dev.HiddenBATHAuto.enums.notification.NotificationMessageType;
import com.dev.HiddenBATHAuto.enums.notification.NotificationSendStatus;
import com.dev.HiddenBATHAuto.model.notification.NotificationMessageLog;
import com.dev.HiddenBATHAuto.model.notification.NotificationTemplate;
import com.dev.HiddenBATHAuto.provider.notification.PhoneNumberNormalizer;
import com.dev.HiddenBATHAuto.provider.notification.SolapiMessageClient;
import com.dev.HiddenBATHAuto.repository.notification.NotificationMessageLogRepository;
import com.dev.HiddenBATHAuto.repository.notification.NotificationTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationMessageService {

    private final SolapiProperties solapiProperties;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationMessageLogRepository messageLogRepository;
    private final SolapiMessageClient solapiMessageClient;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationSendResult sendAlimtalk(NotificationSendCommand command) {
        if (!solapiProperties.isEnabled()) {
            return saveDisabledLog(command);
        }

        NotificationTemplate template = templateRepository.findByTemplateCodeAndEnabledTrue(command.getTemplateCode())
                .orElseThrow(() -> new IllegalArgumentException("사용 가능한 알림 템플릿이 없습니다: " + command.getTemplateCode()));

        String requestKey = UUID.randomUUID().toString();
        String to = phoneNumberNormalizer.normalizeKoreanPhone(command.getTo());
        String from = resolveFrom(command.getFrom(), template.getDefaultFromNumber());

        Map<String, String> variables = normalizeVariables(command.getVariables());

        NotificationMessageLog log = new NotificationMessageLog();
        log.setRequestKey(requestKey);
        log.setProvider("SOLAPI");
        log.setMessageType(NotificationMessageType.ATA);
        log.setSendStatus(NotificationSendStatus.READY);
        log.setBusinessType(command.getBusinessType());
        log.setBusinessId(command.getBusinessId());
        log.setTemplateCode(template.getTemplateCode());
        log.setProviderTemplateId(template.getProviderTemplateId());
        log.setPfId(template.getPfId());
        log.setFromNumber(from);
        log.setToNumber(to);
        log.setMessageText(command.getMessageTextForLog());
        log.setVariablesJson(toJson(variables));
        log.setRequestedByMemberId(command.getRequestedByMemberId());
        log.setRequestedByUsername(command.getRequestedByUsername());
        log.setRequestedAt(LocalDateTime.now());
        messageLogRepository.save(log);

        SolapiSendRequest request = buildSolapiRequest(template, requestKey, from, to, variables);
        log.setRequestBodyJson(toJson(request));

        SolapiSendApiResult apiResult = solapiMessageClient.sendManyDetail(request);

        log.setResponseBodyJson(apiResult.getResponseBody());
        log.setSolapiGroupId(apiResult.getGroupId());
        log.setSolapiMessageId(apiResult.getMessageId());
        log.setStatusCode(apiResult.getStatusCode());
        log.setStatusMessage(apiResult.getStatusMessage());

        boolean accepted = apiResult.getHttpStatus() >= 200 && apiResult.getHttpStatus() < 300;

        if (accepted) {
            log.setSendStatus(NotificationSendStatus.REQUEST_ACCEPTED);
            log.setAcceptedAt(LocalDateTime.now());
        } else {
            log.setSendStatus(NotificationSendStatus.REQUEST_FAILED);
            log.setFailureReason(apiResult.getStatusMessage());
            log.setCompletedAt(LocalDateTime.now());
        }

        return NotificationSendResult.builder()
                .success(accepted)
                .logId(log.getId())
                .requestKey(log.getRequestKey())
                .groupId(log.getSolapiGroupId())
                .messageId(log.getSolapiMessageId())
                .statusCode(log.getStatusCode())
                .statusMessage(log.getStatusMessage())
                .failureReason(log.getFailureReason())
                .build();
    }

    private SolapiSendRequest buildSolapiRequest(
            NotificationTemplate template,
            String requestKey,
            String from,
            String to,
            Map<String, String> variables
    ) {
        Map<String, String> customFields = new LinkedHashMap<>();
        customFields.put("requestKey", requestKey);
        customFields.put("templateCode", template.getTemplateCode());

        SolapiKakaoOptions kakaoOptions = SolapiKakaoOptions.builder()
                .pfId(template.getPfId())
                .templateId(template.getProviderTemplateId())
                .disableSms(template.isDisableSms())
                .variables(variables)
                .build();

        SolapiMessage message = SolapiMessage.builder()
                .to(to)
                .from(from)
                .type("ATA")
                .kakaoOptions(kakaoOptions)
                .customFields(customFields)
                .build();

        return SolapiSendRequest.builder()
                .messages(List.of(message))
                .strict(solapiProperties.isStrict())
                .allowDuplicates(solapiProperties.isAllowDuplicates())
                .showMessageList(true)
                .build();
    }

    private NotificationSendResult saveDisabledLog(NotificationSendCommand command) {
        NotificationMessageLog log = new NotificationMessageLog();
        log.setRequestKey(UUID.randomUUID().toString());
        log.setProvider("SOLAPI");
        log.setMessageType(NotificationMessageType.ATA);
        log.setSendStatus(NotificationSendStatus.REQUEST_FAILED);
        log.setBusinessType(command.getBusinessType());
        log.setBusinessId(command.getBusinessId());
        log.setTemplateCode(command.getTemplateCode());
        log.setToNumber(command.getTo());
        log.setFromNumber(command.getFrom());
        log.setMessageText(command.getMessageTextForLog());
        log.setVariablesJson(toJson(command.getVariables()));
        log.setFailureReason("SOLAPI 발송 기능이 비활성화되어 있습니다.");
        log.setRequestedByMemberId(command.getRequestedByMemberId());
        log.setRequestedByUsername(command.getRequestedByUsername());
        log.setRequestedAt(LocalDateTime.now());
        log.setCompletedAt(LocalDateTime.now());
        messageLogRepository.save(log);

        return NotificationSendResult.builder()
                .success(false)
                .logId(log.getId())
                .requestKey(log.getRequestKey())
                .failureReason(log.getFailureReason())
                .build();
    }

    private String resolveFrom(String commandFrom, String templateFrom) {
        if (commandFrom != null && !commandFrom.isBlank()) {
            return phoneNumberNormalizer.normalizeKoreanPhone(commandFrom);
        }

        if (templateFrom != null && !templateFrom.isBlank()) {
            return phoneNumberNormalizer.normalizeKoreanPhone(templateFrom);
        }

        if (solapiProperties.getDefaultFrom() != null && !solapiProperties.getDefaultFrom().isBlank()) {
            return phoneNumberNormalizer.normalizeKoreanPhone(solapiProperties.getDefaultFrom());
        }

        throw new IllegalArgumentException("발신번호가 없습니다. SOLAPI 대체발송을 사용하려면 등록된 발신번호가 필요합니다.");
    }

    private Map<String, String> normalizeVariables(Map<String, String> variables) {
        Map<String, String> normalized = new LinkedHashMap<>();

        if (variables == null) {
            return normalized;
        }

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key == null || key.isBlank()) {
                continue;
            }

            String normalizedKey = key.trim();
            if (!normalizedKey.startsWith("#{")) {
                normalizedKey = "#{" + normalizedKey;
            }
            if (!normalizedKey.endsWith("}")) {
                normalizedKey = normalizedKey + "}";
            }

            normalized.put(normalizedKey, value == null || value.isBlank() ? "-" : value.trim());
        }

        return normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}