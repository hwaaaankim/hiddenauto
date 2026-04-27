package com.dev.HiddenBATHAuto.model.task;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(
        name = "tb_order_check_status",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tb_order_check_status_order_id", columnNames = "order_id")
        }
)
@Getter
@Setter
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

    @Column(name = "checked_by_username", length = 100)
    private String checkedByUsername;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static OrderCheckStatus unchecked(Order order) {
        OrderCheckStatus status = new OrderCheckStatus();
        status.setOrder(order);
        status.setChecked(false);
        status.setCreatedAt(LocalDateTime.now());
        return status;
    }

    public void markChecked(String checkedByUsername) {
        LocalDateTime now = LocalDateTime.now();
        this.checked = true;
        this.checkedByUsername = checkedByUsername;
        this.checkedAt = now;
        this.updatedAt = now;
    }
}