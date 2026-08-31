package com.dev.HiddenBATHAuto.model.notification;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.notification.AsNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationRecipientGroup;
import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationKakaoStatus;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.as.audit.AsChangeEvent;

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
        name = "tb_as_notification",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_as_notification_event_recipient",
                columnNames = {"event_id", "recipient_member_id"}
        ),
        indexes = {
                @Index(name = "idx_as_notification_recipient_unread", columnList = "recipient_member_id,read_at,created_at,id"),
                @Index(name = "idx_as_notification_recipient_web", columnList = "recipient_member_id,web_enabled,read_at,id"),
                @Index(name = "idx_as_notification_task", columnList = "as_task_id,created_at,id"),
                @Index(name = "idx_as_notification_policy_snapshot", columnList = "notification_action,recipient_group,web_enabled,kakao_enabled"),
                @Index(name = "idx_as_notification_important_pending", columnList = "recipient_member_id,important_enabled,important_confirmed_at,id")
        }
)
@Getter
@NoArgsConstructor
public class AsNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @ToString.Exclude
    private AsChangeEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "as_task_id")
    @ToString.Exclude
    private AsTask asTask;

    @Column(name = "as_task_id_snapshot", nullable = false)
    private Long asTaskIdSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "as_status_snapshot", length = 30)
    private AsStatus asStatusSnapshot;

    @Column(name = "subject_snapshot", length = 500)
    private String subjectSnapshot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_member_id", nullable = false)
    @ToString.Exclude
    private Member recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_action", nullable = false, length = 50)
    private AsNotificationAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_group", nullable = false, length = 50)
    private AsNotificationRecipientGroup recipientGroup;

    @Column(name = "web_enabled", nullable = false)
    private boolean webEnabled;

    @Column(name = "kakao_enabled", nullable = false)
    private boolean kakaoEnabled;

    @Column(name = "important_enabled", nullable = false)
    private boolean importantEnabled;

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

    public static AsNotification create(
            AsChangeEvent event,
            Member recipient,
            AsNotificationRecipientGroup recipientGroup,
            boolean webEnabled,
            boolean kakaoEnabled,
            boolean importantEnabled,
            String title,
            String message
    ) {
        if (event == null || event.resolveAsTaskId() == null) {
            throw new IllegalArgumentException("AS 알림 이벤트가 없습니다.");
        }
        if (recipient == null || recipient.getId() == null) {
            throw new IllegalArgumentException("AS 알림 수신자가 없습니다.");
        }
        AsNotification notification = new AsNotification();
        notification.event = event;
        notification.asTask = event.getAsTask();
        notification.asTaskIdSnapshot = event.resolveAsTaskId();
        notification.asStatusSnapshot = event.resolveAsStatus();
        notification.subjectSnapshot = normalize(event.getSubjectSnapshot(), 500);
        notification.recipient = recipient;
        notification.action = event.getAction();
        notification.recipientGroup = recipientGroup;
        notification.webEnabled = webEnabled;
        notification.kakaoEnabled = kakaoEnabled;
        notification.importantEnabled = importantEnabled;
        notification.title = required(title, "AS 알림", 200);
        notification.message = required(message, "AS 업무 정보가 변경되었습니다.", 4000);
        notification.createdAt = LocalDateTime.now();
        return notification;
    }

    public Long resolveAsTaskId() {
        return asTask != null && asTask.getId() != null ? asTask.getId() : asTaskIdSnapshot;
    }

    public AsStatus resolveAsStatus() {
        return asTask != null && asTask.getStatus() != null ? asTask.getStatus() : asStatusSnapshot;
    }

    public String resolveSubject() {
        String current = asTask != null ? normalize(asTask.getSubject(), 500) : null;
        return current != null ? current : subjectSnapshot;
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
        kakaoFailureReason = null;
        kakaoCompletedAt = LocalDateTime.now();
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
        if (kakaoStatus == null) kakaoStatus = OrderNotificationKakaoStatus.NOT_REQUESTED;
    }

    private static String required(String value, String fallback, int max) {
        String normalized = normalize(value, max);
        return normalized == null ? fallback : normalized;
    }

    private static String normalize(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        return v.length() <= max ? v : v.substring(0, max);
    }
}
