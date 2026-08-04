package com.dev.HiddenBATHAuto.model.notification;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationCategory;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationKakaoStatus;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.Task;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
        name = "tb_order_notification",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_notification_event_recipient_category",
                columnNames = {"event_id", "recipient_member_id", "category"}
        ),
        indexes = {
                @Index(name = "idx_order_notification_recipient_unread", columnList = "recipient_member_id,read_at,created_at,id"),
                @Index(name = "idx_order_notification_recipient_category", columnList = "recipient_member_id,category,created_at,id"),
                @Index(name = "idx_order_notification_order", columnList = "order_id,created_at,id"),
                @Index(name = "idx_order_notification_task", columnList = "task_id,created_at,id"),
                @Index(name = "idx_order_notification_kakao_batch", columnList = "kakao_batch_key,recipient_member_id,id")
        }
)
@Getter
@NoArgsConstructor
public class OrderNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @ToString.Exclude
    private OrderChangeEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    @ToString.Exclude
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_member_id", nullable = false)
    @ToString.Exclude
    private Member recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private OrderNotificationCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 같은 트랜잭션에서 생성된 알림의 카카오 발송 묶음 키 */
    @Column(name = "kakao_batch_key", nullable = true, length = 64)
    private String kakaoBatchKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "kakao_status", nullable = false, length = 30)
    private OrderNotificationKakaoStatus kakaoStatus = OrderNotificationKakaoStatus.NOT_REQUESTED;

    @Column(name = "kakao_log_id")
    private Long kakaoLogId;

    @Column(name = "kakao_failure_reason", length = 1000)
    private String kakaoFailureReason;

    @Column(name = "kakao_requested_at")
    private LocalDateTime kakaoRequestedAt;

    @Column(name = "kakao_completed_at")
    private LocalDateTime kakaoCompletedAt;

    public static OrderNotification create(
            OrderChangeEvent event,
            Member recipient,
            OrderNotificationCategory category,
            String title,
            String message,
            String kakaoBatchKey
    ) {
        if (event == null || event.getOrder() == null) {
            throw new IllegalArgumentException("알림 이벤트 또는 오더가 없습니다.");
        }
        if (recipient == null || recipient.getId() == null) {
            throw new IllegalArgumentException("알림 수신자가 없습니다.");
        }

        OrderNotification notification = new OrderNotification();
        notification.event = event;
        notification.order = event.getOrder();
        notification.task = event.getOrder().getTask();
        notification.recipient = recipient;
        notification.category = category == null ? OrderNotificationCategory.EMERGENCY : category;
        notification.title = required(title, "발주 변경 알림", 200);
        notification.message = required(message, "발주 정보가 변경되었습니다.", 4000);
        notification.kakaoBatchKey = required(kakaoBatchKey, java.util.UUID.randomUUID().toString(), 64);
        notification.createdAt = LocalDateTime.now();
        return notification;
    }

    public void markRead(LocalDateTime when) {
        if (readAt == null) {
            readAt = when == null ? LocalDateTime.now() : when;
        }
    }

    public void markKakaoSkipped(String reason) {
        kakaoStatus = OrderNotificationKakaoStatus.SKIPPED;
        kakaoFailureReason = normalize(reason, 1000);
        kakaoCompletedAt = LocalDateTime.now();
    }

    public void markKakaoRequested() {
        kakaoStatus = OrderNotificationKakaoStatus.REQUESTED;
        kakaoRequestedAt = LocalDateTime.now();
        kakaoFailureReason = null;
    }

    public void markKakaoAccepted(Long logId) {
        kakaoStatus = OrderNotificationKakaoStatus.ACCEPTED;
        kakaoLogId = logId;
        kakaoCompletedAt = LocalDateTime.now();
        kakaoFailureReason = null;
    }

    public void markKakaoFailed(Long logId, String reason) {
        kakaoStatus = OrderNotificationKakaoStatus.FAILED;
        kakaoLogId = logId;
        kakaoFailureReason = normalize(reason, 1000);
        kakaoCompletedAt = LocalDateTime.now();
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (kakaoBatchKey == null || kakaoBatchKey.isBlank()) {
            kakaoBatchKey = java.util.UUID.randomUUID().toString();
        }
        if (kakaoStatus == null) kakaoStatus = OrderNotificationKakaoStatus.NOT_REQUESTED;
    }

    private static String required(String value, String fallback, int max) {
        String normalized = normalize(value, max);
        return normalized == null ? fallback : normalized;
    }

    private static String normalize(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
