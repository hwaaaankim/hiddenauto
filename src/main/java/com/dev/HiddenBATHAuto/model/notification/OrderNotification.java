package com.dev.HiddenBATHAuto.model.notification;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationCategory;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationKakaoStatus;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationRecipientGroup;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
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
                @Index(name = "idx_order_notification_recipient_unread",
                        columnList = "recipient_member_id,read_at,created_at,id"),
                @Index(name = "idx_order_notification_recipient_web_unread",
                        columnList = "recipient_member_id,web_enabled,read_at,created_at,id"),
                @Index(name = "idx_order_notification_recipient_category",
                        columnList = "recipient_member_id,category,created_at,id"),
                @Index(name = "idx_order_notification_order", columnList = "order_id,created_at,id"),
                @Index(name = "idx_order_notification_task", columnList = "task_id,created_at,id"),
                @Index(name = "idx_order_notification_kakao_batch",
                        columnList = "kakao_batch_key,recipient_member_id,id"),
                @Index(name = "idx_order_notification_kakao_batch_enabled",
                        columnList = "kakao_batch_key,recipient_member_id,kakao_enabled,id"),
                @Index(name = "idx_order_notification_policy_snapshot",
                        columnList = "notification_action,recipient_group,web_enabled,kakao_enabled"),
                @Index(name = "idx_order_notification_recipient_important_pending",
                        columnList = "recipient_member_id,important_enabled,important_confirmed_at,id"),
                @Index(name = "idx_order_notification_recipient_important_unread",
                        columnList = "recipient_member_id,important_enabled,read_at,id")
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

    /**
     * 삭제 알림과 감사이력을 보존하기 위해 nullable입니다.
     * DB FK는 ON DELETE SET NULL로 적용하고 orderIdSnapshot을 함께 저장합니다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    @ToString.Exclude
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    @ToString.Exclude
    private Task task;

    @Column(name = "order_id_snapshot", nullable = false)
    private Long orderIdSnapshot;

    @Column(name = "task_id_snapshot")
    private Long taskIdSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status_snapshot", length = 30)
    private OrderStatus orderStatusSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_member_id", nullable = false)
    @ToString.Exclude
    private Member recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private OrderNotificationCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_action", nullable = false, length = 50)
    private OrderNotificationAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_group", nullable = false, length = 50)
    private OrderNotificationRecipientGroup recipientGroup;

    /** 생성 당시 관리 정책의 스냅샷입니다. 이후 설정을 바꿔도 이미 생성된 알림은 뒤집지 않습니다. */
    @Column(name = "web_enabled", nullable = false)
    private boolean webEnabled;

    @Column(name = "kakao_enabled", nullable = false)
    private boolean kakaoEnabled;

    /** 중요알림 강제 팝업 및 종 알림의 중요 탭 노출 여부에 대한 생성 시점 정책 스냅샷입니다. */
    @Column(name = "important_enabled", nullable = false)
    private boolean importantEnabled;

    /** 강제 중요알림 팝업에서 사용자가 확인 버튼을 누른 시각입니다. 종 알림 readAt과 별도 상태입니다. */
    @Column(name = "important_confirmed_at")
    private LocalDateTime importantConfirmedAt;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Lob
    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "kakao_batch_key", length = 64)
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
            OrderNotificationAction action,
            OrderNotificationRecipientGroup recipientGroup,
            boolean webEnabled,
            boolean kakaoEnabled,
            boolean importantEnabled,
            String title,
            String message,
            String kakaoBatchKey
    ) {
        if (event == null || event.getOrder() == null || event.getOrder().getId() == null) {
            throw new IllegalArgumentException("알림 이벤트 또는 오더가 없습니다.");
        }
        if (recipient == null || recipient.getId() == null) {
            throw new IllegalArgumentException("알림 수신자가 없습니다.");
        }

        Order currentOrder = event.getOrder();
        OrderNotification notification = new OrderNotification();
        notification.event = event;
        notification.order = currentOrder;
        notification.task = currentOrder.getTask();
        notification.orderIdSnapshot = currentOrder.getId();
        notification.taskIdSnapshot = currentOrder.getTask() != null ? currentOrder.getTask().getId() : null;
        notification.orderStatusSnapshot = currentOrder.getStatus();
        notification.recipient = recipient;
        notification.category = category == null ? OrderNotificationCategory.EMERGENCY : category;
        notification.action = action == null ? OrderNotificationAction.UPDATE : action;
        notification.recipientGroup = recipientGroup == null
                ? OrderNotificationRecipientGroup.MANAGEMENT
                : recipientGroup;
        notification.webEnabled = webEnabled;
        notification.kakaoEnabled = kakaoEnabled;
        notification.importantEnabled = importantEnabled;
        notification.title = required(title, "발주 변경 알림", 200);
        notification.message = required(message, "발주 정보가 변경되었습니다.", 4000);
        notification.kakaoBatchKey = required(kakaoBatchKey, java.util.UUID.randomUUID().toString(), 64);
        notification.createdAt = LocalDateTime.now();
        return notification;
    }

    public Long resolveOrderId() {
        return order != null && order.getId() != null ? order.getId() : orderIdSnapshot;
    }

    public Long resolveTaskId() {
        return task != null && task.getId() != null ? task.getId() : taskIdSnapshot;
    }

    public OrderStatus resolveOrderStatus() {
        return order != null && order.getStatus() != null ? order.getStatus() : orderStatusSnapshot;
    }

    public void markRead(LocalDateTime when) {
        if (readAt == null) readAt = when == null ? LocalDateTime.now() : when;
    }

    public void markImportantConfirmed(LocalDateTime when) {
        if (importantEnabled && importantConfirmedAt == null) {
            importantConfirmedAt = when == null ? LocalDateTime.now() : when;
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
        if (action == null) action = OrderNotificationAction.UPDATE;
        if (recipientGroup == null) recipientGroup = OrderNotificationRecipientGroup.MANAGEMENT;
        if (orderIdSnapshot == null && order != null) orderIdSnapshot = order.getId();
        if (taskIdSnapshot == null && task != null) taskIdSnapshot = task.getId();
        if (orderStatusSnapshot == null && order != null) orderStatusSnapshot = order.getStatus();
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
