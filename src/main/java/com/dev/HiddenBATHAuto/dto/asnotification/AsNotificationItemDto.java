package com.dev.HiddenBATHAuto.dto.asnotification;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AsNotificationItemDto {
    private Long id;
    private Long eventId;
    private String notificationDomain;
    private Long asTaskId;
    private String asStatus;
    private String asStatusLabel;
    private String subject;
    private String title;
    private String message;
    private String sourceArea;
    private String sourceAreaLabel;
    private String action;
    private String actionLabel;
    private String recipientGroup;
    private String recipientGroupLabel;
    private Long actorMemberId;
    private String actorUsername;
    private String actorDisplayName;
    private String operationCode;
    private String operationLabel;
    private String summary;
    private boolean webEnabled;
    private boolean important;
    private boolean importantConfirmed;
    private LocalDateTime importantConfirmedAt;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private String createdAtText;
    private List<AsNotificationFieldDto> changes;
    private boolean shortcutEnabled;
    private String shortcutLabel;
    private String shortcutUrl;
}
