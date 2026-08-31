package com.dev.HiddenBATHAuto.model.task.as.audit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.enums.notification.AsNotificationAction;
import com.dev.HiddenBATHAuto.enums.notification.AsNotificationSourceArea;
import com.dev.HiddenBATHAuto.model.task.AsStatus;
import com.dev.HiddenBATHAuto.model.task.AsTask;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
        name = "tb_as_change_event",
        indexes = {
                @Index(name = "idx_as_change_event_task", columnList = "as_task_id,id"),
                @Index(name = "idx_as_change_event_snapshot", columnList = "as_task_id_snapshot,id"),
                @Index(name = "idx_as_change_event_created", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor
public class AsChangeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** AS가 물리 삭제되어도 로그를 보존하기 위해 DB FK를 ON DELETE SET NULL로 구성합니다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "as_task_id")
    @ToString.Exclude
    private AsTask asTask;

    @Column(name = "as_task_id_snapshot", nullable = false)
    private Long asTaskIdSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "as_status_snapshot", length = 30)
    private AsStatus asStatusSnapshot;

    @Column(name = "requested_by_member_id_snapshot")
    private Long requestedByMemberIdSnapshot;

    @Column(name = "assigned_handler_id_snapshot")
    private Long assignedHandlerIdSnapshot;

    @Column(name = "previous_assigned_handler_id_snapshot")
    private Long previousAssignedHandlerIdSnapshot;

    @Column(name = "subject_snapshot", length = 500)
    private String subjectSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_area", nullable = false, length = 30)
    private AsNotificationSourceArea sourceArea;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_action", nullable = false, length = 50)
    private AsNotificationAction action;

    @Column(name = "actor_member_id")
    private Long actorMemberId;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Column(name = "actor_display_name", length = 100)
    private String actorDisplayName;

    @Column(name = "operation_code", nullable = false, length = 100)
    private String operationCode;

    @Column(name = "operation_label", nullable = false, length = 150)
    private String operationLabel;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    @Column(name = "summary", nullable = false, length = 1000)
    private String summary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AsChangeField> fields = new ArrayList<>();

    public static AsChangeEvent create(
            AsTask task,
            AsNotificationSourceArea sourceArea,
            AsNotificationAction action,
            Long previousAssignedHandlerId,
            Long actorMemberId,
            String actorUsername,
            String actorDisplayName,
            String operationCode,
            String operationLabel,
            String requestPath,
            String summary
    ) {
        if (task == null || task.getId() == null) {
            throw new IllegalArgumentException("AS 변경이력을 기록할 업무가 없습니다.");
        }
        AsChangeEvent event = new AsChangeEvent();
        event.asTask = task;
        event.asTaskIdSnapshot = task.getId();
        event.asStatusSnapshot = task.getStatus();
        event.requestedByMemberIdSnapshot = task.getRequestedBy() != null ? task.getRequestedBy().getId() : null;
        event.assignedHandlerIdSnapshot = task.getAssignedHandler() != null ? task.getAssignedHandler().getId() : null;
        event.previousAssignedHandlerIdSnapshot = previousAssignedHandlerId;
        event.subjectSnapshot = normalize(task.getSubject(), 500);
        event.sourceArea = sourceArea == null ? AsNotificationSourceArea.MANAGEMENT : sourceArea;
        event.action = action == null ? AsNotificationAction.DETAIL_UPDATE : action;
        event.actorMemberId = actorMemberId;
        event.actorUsername = normalize(actorUsername, 100);
        event.actorDisplayName = normalize(actorDisplayName, 100);
        event.operationCode = required(operationCode, event.action.name(), 100);
        event.operationLabel = required(operationLabel, event.action.getLabel(), 150);
        event.requestPath = normalize(requestPath, 500);
        event.summary = required(summary, event.action.getLabel(), 1000);
        event.createdAt = LocalDateTime.now();
        return event;
    }

    public void addField(AsChangeField field) {
        if (field == null) return;
        fields.add(field);
        field.attach(this);
    }

    public Long resolveAsTaskId() {
        return asTask != null && asTask.getId() != null ? asTask.getId() : asTaskIdSnapshot;
    }

    public AsStatus resolveAsStatus() {
        return asTask != null && asTask.getStatus() != null ? asTask.getStatus() : asStatusSnapshot;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (asTaskIdSnapshot == null && asTask != null) asTaskIdSnapshot = asTask.getId();
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
