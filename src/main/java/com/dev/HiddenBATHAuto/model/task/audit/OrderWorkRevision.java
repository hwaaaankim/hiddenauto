package com.dev.HiddenBATHAuto.model.task.audit;

import java.time.LocalDateTime;

import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;
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
        name = "tb_order_work_revision",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_work_revision_order_area",
                columnNames = {"order_id", "work_area"}
        ),
        indexes = @Index(name = "idx_order_work_revision_area_order", columnList = "work_area,order_id")
)
@Getter
@NoArgsConstructor
public class OrderWorkRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_area", nullable = false, length = 30)
    private OrderWorkArea workArea;

    @Column(name = "current_version", nullable = false)
    private long currentVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static OrderWorkRevision initial(Order order, OrderWorkArea workArea) {
        OrderWorkRevision revision = new OrderWorkRevision();
        revision.order = order;
        revision.workArea = workArea;
        revision.currentVersion = 0L;
        revision.createdAt = LocalDateTime.now();
        revision.updatedAt = revision.createdAt;
        return revision;
    }

    public long incrementAndGet() {
        this.currentVersion++;
        this.updatedAt = LocalDateTime.now();
        return this.currentVersion;
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
}
