package com.dev.HiddenBATHAuto.dto.notification;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationSendResult {

    private boolean success;

    private Long logId;

    private String requestKey;

    private String groupId;

    private String messageId;

    private String statusCode;

    private String statusMessage;

    private String failureReason;
}