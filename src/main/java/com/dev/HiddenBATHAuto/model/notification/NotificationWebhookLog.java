package com.dev.HiddenBATHAuto.model.notification;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_notification_webhook_log",
        indexes = {
                @Index(name = "idx_webhook_message_id", columnList = "solapi_message_id"),
                @Index(name = "idx_webhook_group_id", columnList = "solapi_group_id"),
                @Index(name = "idx_webhook_received_at", columnList = "received_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationWebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_name", length = 80)
    private String eventName;

    @Column(name = "secret_valid", nullable = false)
    private boolean secretValid;

    @Column(name = "solapi_message_id", length = 80)
    private String solapiMessageId;

    @Column(name = "solapi_group_id", length = 80)
    private String solapiGroupId;

    @Column(name = "status_code", length = 20)
    private String statusCode;

    @Column(name = "status_message", length = 255)
    private String statusMessage;

    @Lob
    @Column(name = "raw_json", nullable = false, columnDefinition = "LONGTEXT")
    private String rawJson;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();
}