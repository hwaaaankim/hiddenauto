package com.dev.HiddenBATHAuto.model.notification;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.notification.NotificationMessageType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_notification_template",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_template_code", columnNames = "template_code")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_code", nullable = false, length = 80)
    private String templateCode;

    @Column(name = "template_name", nullable = false, length = 150)
    private String templateName;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider = "SOLAPI";

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    private NotificationMessageType messageType = NotificationMessageType.ATA;

    @Column(name = "pf_id", nullable = false, length = 80)
    private String pfId;

    @Column(name = "provider_template_id", nullable = false, length = 80)
    private String providerTemplateId;

    @Column(name = "default_from_number", length = 30)
    private String defaultFromNumber;

    @Column(name = "disable_sms", nullable = false)
    private boolean disableSms = false;

    @Lob
    @Column(name = "template_preview", columnDefinition = "LONGTEXT")
    private String templatePreview;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}