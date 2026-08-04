package com.dev.HiddenBATHAuto.service.ordernotification;

import java.util.List;

public record OrderNotificationsCreatedEvent(List<Long> notificationIds) {
    public OrderNotificationsCreatedEvent {
        notificationIds = notificationIds == null ? List.of() : List.copyOf(notificationIds);
    }
}
