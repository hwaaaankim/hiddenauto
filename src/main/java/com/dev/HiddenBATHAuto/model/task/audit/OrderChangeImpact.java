package com.dev.HiddenBATHAuto.model.task.audit;

import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
        name = "tb_order_change_impact",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_change_impact_event_area",
                columnNames = {"event_id", "work_area"}
        ),
        indexes = @Index(name = "idx_order_change_impact_area_version", columnList = "work_area,version,event_id")
)
@Getter
@NoArgsConstructor
public class OrderChangeImpact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @ToString.Exclude
    private OrderChangeEvent event;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_area", nullable = false, length = 30)
    private OrderWorkArea workArea;

    @Column(name = "version", nullable = false)
    private long version;

    public static OrderChangeImpact of(OrderWorkArea workArea, long version) {
        OrderChangeImpact impact = new OrderChangeImpact();
        impact.workArea = workArea;
        impact.version = Math.max(0L, version);
        return impact;
    }

    void attach(OrderChangeEvent event) {
        this.event = event;
    }
}
