package com.dev.HiddenBATHAuto.model.notification;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationRecipientGroup;
import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "tb_order_notification_policy",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_notification_policy_key",
                columnNames = {"source_area", "notification_action", "recipient_group"}
        ),
        indexes = {
                @Index(name = "idx_order_notification_policy_lookup",
                        columnList = "source_area,notification_action,recipient_group")
        }
)
@Getter
@NoArgsConstructor
public class OrderNotificationPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_area", nullable = false, length = 30)
    private OrderChangeSourceArea sourceArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_action", nullable = false, length = 50)
    private OrderNotificationAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_group", nullable = false, length = 50)
    private OrderNotificationRecipientGroup recipientGroup;

    @Column(name = "web_enabled", nullable = false)
    private boolean webEnabled;

    @Column(name = "kakao_enabled", nullable = false)
    private boolean kakaoEnabled;

    @Column(name = "important_enabled", nullable = false)
    private boolean importantEnabled;

    @Column(name = "updated_by_member_id")
    private Long updatedByMemberId;

    @Column(name = "updated_by_username", length = 100)
    private String updatedByUsername;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static OrderNotificationPolicy create(
            OrderChangeSourceArea sourceArea,
            OrderNotificationAction action,
            OrderNotificationRecipientGroup recipientGroup,
            boolean webEnabled,
            boolean kakaoEnabled,
            boolean importantEnabled,
            Long updatedByMemberId,
            String updatedByUsername
    ) {
        OrderNotificationPolicy policy = new OrderNotificationPolicy();
        policy.sourceArea = sourceArea;
        policy.action = action;
        policy.recipientGroup = recipientGroup;
        policy.update(webEnabled, kakaoEnabled, importantEnabled, updatedByMemberId, updatedByUsername);
        return policy;
    }

    public void update(
            boolean webEnabled,
            boolean kakaoEnabled,
            boolean importantEnabled,
            Long updatedByMemberId,
            String updatedByUsername
    ) {
        this.webEnabled = webEnabled;
        this.kakaoEnabled = kakaoEnabled;
        this.importantEnabled = importantEnabled;
        this.updatedByMemberId = updatedByMemberId;
        this.updatedByUsername = normalize(updatedByUsername, 100);
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private static String normalize(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
