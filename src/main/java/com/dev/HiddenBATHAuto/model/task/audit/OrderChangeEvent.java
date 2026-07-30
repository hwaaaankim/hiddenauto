package com.dev.HiddenBATHAuto.model.task.audit;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.enums.order.OrderChangeSourceArea;
import com.dev.HiddenBATHAuto.model.task.Order;

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
        name = "tb_order_change_event",
        indexes = {
                @Index(name = "idx_order_change_event_order_id", columnList = "order_id,id"),
                @Index(name = "idx_order_change_event_created_at", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor
public class OrderChangeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_area", nullable = false, length = 30)
    private OrderChangeSourceArea sourceArea;

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

    @Column(name = "external_reference", length = 190, unique = true)
    private String externalReference;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderChangeField> fields = new ArrayList<>();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderChangeImpact> impacts = new ArrayList<>();

    public static OrderChangeEvent create(
            Order order,
            OrderChangeSourceArea sourceArea,
            Long actorMemberId,
            String actorUsername,
            String actorDisplayName,
            String operationCode,
            String operationLabel,
            String requestPath,
            String summary
    ) {
        OrderChangeEvent event = new OrderChangeEvent();
        event.order = order;
        event.sourceArea = sourceArea == null ? OrderChangeSourceArea.SYSTEM : sourceArea;
        event.actorMemberId = actorMemberId;
        event.actorUsername = normalize(actorUsername, 100);
        event.actorDisplayName = normalize(actorDisplayName, 100);
        event.operationCode = required(operationCode, "ORDER_CHANGE", 100);
        event.operationLabel = required(operationLabel, "오더 변경", 150);
        event.requestPath = normalize(requestPath, 500);
        event.summary = required(summary, "오더 정보 변경", 1000);
        event.createdAt = LocalDateTime.now();
        return event;
    }

    public void addField(OrderChangeField field) {
        if (field == null) return;
        fields.add(field);
        field.attach(this);
    }

    public void addImpact(OrderChangeImpact impact) {
        if (impact == null) return;
        impacts.add(impact);
        impact.attach(this);
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
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
