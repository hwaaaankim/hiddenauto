package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.order.OrderCheckState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
        name = "tb_order_check_status",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tb_order_check_status_order_id", columnNames = "order_id")
        }
)
@Getter
@NoArgsConstructor
public class OrderCheckStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    @Column(name = "checked", nullable = false)
    private boolean checked = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_state", nullable = false, length = 30)
    private OrderCheckState checkState = OrderCheckState.UNCHECKED;

    @Column(name = "checked_by_username", length = 100)
    private String checkedByUsername;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Column(name = "revision_marked_by_username", length = 100)
    private String revisionMarkedByUsername;

    @Column(name = "revision_marked_at")
    private LocalDateTime revisionMarkedAt;

    @Column(name = "revision_reason", length = 500)
    private String revisionReason;

    @Column(name = "revision_count", nullable = false)
    private int revisionCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static OrderCheckStatus unchecked(Order order) {
        OrderCheckStatus status = new OrderCheckStatus();
        status.order = order;
        status.checked = false;
        status.checkState = OrderCheckState.UNCHECKED;
        status.createdAt = LocalDateTime.now();
        return status;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    /**
     * 기존 코드에서 setChecked(true/false)를 쓰고 있을 가능성이 있으므로
     * checked와 checkState가 어긋나지 않도록 직접 제어합니다.
     */
    public void setChecked(boolean checked) {
        this.checked = checked;

        if (checked) {
            this.checkState = OrderCheckState.CHECKED;
        } else if (this.checkState == null || this.checkState == OrderCheckState.CHECKED) {
            this.checkState = OrderCheckState.UNCHECKED;
        }
    }

    public void setCheckState(OrderCheckState checkState) {
        this.checkState = checkState == null ? OrderCheckState.UNCHECKED : checkState;
        this.checked = this.checkState == OrderCheckState.CHECKED;
    }

    public void setCheckedByUsername(String checkedByUsername) {
        this.checkedByUsername = normalizeText(checkedByUsername);
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isRevisedAfterCheck() {
        return getResolvedCheckState() == OrderCheckState.REVISED_AFTER_CHECK;
    }

    public boolean isLatestChecked() {
        return getResolvedCheckState() == OrderCheckState.CHECKED;
    }

    public boolean isNeedProductionCheck() {
        return getResolvedCheckState().isNeedProductionCheck();
    }

    public OrderCheckState getResolvedCheckState() {
        if (checkState != null) {
            return checkState;
        }

        return checked ? OrderCheckState.CHECKED : OrderCheckState.UNCHECKED;
    }

    public String getCheckStateName() {
        return getResolvedCheckState().name();
    }

    public String getCheckStateLabel() {
        return getResolvedCheckState().getLabel();
    }

    public void markChecked(String checkedByUsername) {
        LocalDateTime now = LocalDateTime.now();

        this.checked = true;
        this.checkState = OrderCheckState.CHECKED;
        this.checkedByUsername = normalizeText(checkedByUsername);
        this.checkedAt = now;

        this.revisionMarkedByUsername = null;
        this.revisionMarkedAt = null;
        this.revisionReason = null;

        this.updatedAt = now;
    }

    public void markRevisedAfterCheck(String revisedByUsername, String reason) {
        LocalDateTime now = LocalDateTime.now();

        this.checked = false;
        this.checkState = OrderCheckState.REVISED_AFTER_CHECK;

        this.revisionMarkedByUsername = normalizeText(revisedByUsername);
        this.revisionMarkedAt = now;
        this.revisionReason = trimToMax(normalizeText(reason), 500);
        this.revisionCount = this.revisionCount + 1;

        this.updatedAt = now;
    }

    @PrePersist
    protected void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        syncCheckedByState();
    }

    @PreUpdate
    protected void preUpdate() {
        updatedAt = LocalDateTime.now();
        syncCheckedByState();
    }

    private void syncCheckedByState() {
        if (checkState == null) {
            checkState = checked ? OrderCheckState.CHECKED : OrderCheckState.UNCHECKED;
        }

        checked = checkState == OrderCheckState.CHECKED;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToMax(String value, int max) {
        if (value == null) {
            return null;
        }

        return value.length() <= max ? value : value.substring(0, max);
    }
}