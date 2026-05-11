package com.dev.HiddenBATHAuto.repository.notification;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.notification.NotificationMessageLog;

public interface NotificationMessageLogRepository extends JpaRepository<NotificationMessageLog, Long> {

    Optional<NotificationMessageLog> findByRequestKey(String requestKey);

    Optional<NotificationMessageLog> findFirstBySolapiMessageId(String solapiMessageId);
}