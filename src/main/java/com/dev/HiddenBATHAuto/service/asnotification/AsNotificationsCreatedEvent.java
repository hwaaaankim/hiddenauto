package com.dev.HiddenBATHAuto.service.asnotification;

import java.util.List;

public record AsNotificationsCreatedEvent(List<Long> notificationIds) {
    public AsNotificationsCreatedEvent {
        notificationIds = notificationIds == null ? List.of() : List.copyOf(notificationIds);
    }
}
