package com.dev.HiddenBATHAuto.repository.notification;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dev.HiddenBATHAuto.model.notification.NotificationTemplate;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByTemplateCodeAndEnabledTrue(String templateCode);
}