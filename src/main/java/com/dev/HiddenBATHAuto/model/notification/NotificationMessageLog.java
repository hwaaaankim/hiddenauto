package com.dev.HiddenBATHAuto.model.notification;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.notification.NotificationMessageType;
import com.dev.HiddenBATHAuto.enums.notification.NotificationSendStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "tb_notification_message_log",
        indexes = {
                @Index(name = "idx_notification_business", columnList = "business_type,business_id"),
                @Index(name = "idx_notification_to_number", columnList = "to_number"),
                @Index(name = "idx_notification_status", columnList = "send_status"),
                @Index(name = "idx_notification_solapi_message_id", columnList = "solapi_message_id"),
                @Index(name = "idx_notification_solapi_group_id", columnList = "solapi_group_id"),
                @Index(name = "idx_notification_requested_at", columnList = "requested_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_request_key", columnNames = "request_key")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_key", nullable = false, length = 80)
    private String requestKey;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider = "SOLAPI";

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    private NotificationMessageType messageType = NotificationMessageType.ATA;

    @Enumerated(EnumType.STRING)
    @Column(name = "send_status", nullable = false, length = 40)
    private NotificationSendStatus sendStatus = NotificationSendStatus.READY;

    @Column(name = "business_type", length = 80)
    private String businessType;

    @Column(name = "business_id")
    private Long businessId;

    @Column(name = "template_code", length = 80)
    private String templateCode;

    @Column(name = "provider_template_id", length = 80)
    private String providerTemplateId;

    @Column(name = "pf_id", length = 80)
    private String pfId;

    @Column(name = "from_number", length = 30)
    private String fromNumber;

    @Column(name = "to_number", nullable = false, length = 30)
    private String toNumber;

    @Lob
    @Column(name = "message_text", columnDefinition = "LONGTEXT")
    private String messageText;

    @Lob
    @Column(name = "variables_json", columnDefinition = "LONGTEXT")
    private String variablesJson;

    @Lob
    @Column(name = "buttons_json", columnDefinition = "LONGTEXT")
    private String buttonsJson;

    @Lob
    @Column(name = "request_body_json", columnDefinition = "LONGTEXT")
    private String requestBodyJson;

    @Lob
    @Column(name = "response_body_json", columnDefinition = "LONGTEXT")
    private String responseBodyJson;

    @Lob
    @Column(name = "webhook_body_json", columnDefinition = "LONGTEXT")
    private String webhookBodyJson;

    @Column(name = "solapi_group_id", length = 80)
    private String solapiGroupId;

    @Column(name = "solapi_message_id", length = 80)
    private String solapiMessageId;

    @Column(name = "status_code", length = 20)
    private String statusCode;

    @Column(name = "status_message", length = 255)
    private String statusMessage;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "replacement")
    private Boolean replacement;

    @Column(name = "requested_by_member_id")
    private Long requestedByMemberId;

    @Column(name = "requested_by_username", length = 100)
    private String requestedByUsername;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "reported_at")
    private LocalDateTime reportedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}