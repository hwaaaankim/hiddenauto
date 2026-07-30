package com.dev.HiddenBATHAuto.model.task.audit;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.model.task.Order;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
        name = "tb_order_member_check_status",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_member_check_order_area_member",
                columnNames = {"order_id", "work_area", "member_id"}
        ),
        indexes = {
                @Index(name = "idx_order_member_check_member_area", columnList = "member_id,work_area"),
                @Index(name = "idx_order_member_check_order_area", columnList = "order_id,work_area")
        }
)
@Getter
@NoArgsConstructor
public class OrderMemberCheckStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    @ToString.Exclude
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_area", nullable = false, length = 30)
    private OrderWorkArea workArea;

    @Column(name = "last_checked_version", nullable = false)
    private long lastCheckedVersion;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static OrderMemberCheckStatus checked(
            Order order,
            Member member,
            OrderWorkArea workArea,
            long version
    ) {
        LocalDateTime now = LocalDateTime.now();
        OrderMemberCheckStatus status = new OrderMemberCheckStatus();
        status.order = order;
        status.member = member;
        status.workArea = workArea;
        status.lastCheckedVersion = Math.max(0L, version);
        status.checkedAt = now;
        status.createdAt = now;
        status.updatedAt = now;
        return status;
    }

    public void markChecked(long version) {
        this.lastCheckedVersion = Math.max(0L, version);
        this.checkedAt = LocalDateTime.now();
        this.updatedAt = this.checkedAt;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (checkedAt == null) checkedAt = now;
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
