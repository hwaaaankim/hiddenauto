package com.dev.HiddenBATHAuto.dto.ordernotification;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderNotificationItemDto {
    private Long id;
    private Long eventId;
    private Long orderId;
    private String orderStatus;
    private String orderStatusLabel;
    private Long taskId;
    private String category;
    private String categoryLabel;
    private String title;
    private String message;
    private String sourceArea;
    private String sourceAreaLabel;
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
    private List<OrderNotificationFieldDto> changes;
    /** 취소/비노출 알림은 false이며 URL도 null입니다. */
    private boolean shortcutEnabled;
    private String shortcutLabel;
    private String shortcutUrl;
}
