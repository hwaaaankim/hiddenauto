package com.dev.HiddenBATHAuto.dto.ordernotification;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderNotificationSummaryDto {
    private long totalUnreadCount;
    private long importantUnreadCount;
    private long pendingImportantConfirmationCount;
    private Map<String, Long> unreadCountByCategory;
}
