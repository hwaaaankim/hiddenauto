package com.dev.HiddenBATHAuto.repository.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.notification.NotificationWebhookLog;

public interface NotificationWebhookLogRepository extends JpaRepository<NotificationWebhookLog, Long> {
}