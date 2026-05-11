package com.dev.HiddenBATHAuto.dto.notification;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationSendCommand {

    private String templateCode;

    private String from;

    private String to;

    private String messageTextForLog;

    @Builder.Default
    private Map<String, String> variables = new LinkedHashMap<>();

    private String businessType;

    private Long businessId;

    private Long requestedByMemberId;

    private String requestedByUsername;
}