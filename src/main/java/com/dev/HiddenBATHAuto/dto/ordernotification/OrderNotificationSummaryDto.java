package com.dev.HiddenBATHAuto.dto.ordernotification;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderNotificationSummaryDto {
    private long totalUnreadCount;
    private Map<String, Long> unreadCountByCategory;
}
