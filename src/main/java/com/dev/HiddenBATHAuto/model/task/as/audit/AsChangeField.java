package com.dev.HiddenBATHAuto.model.task.as.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
        name = "tb_as_change_field",
        indexes = @Index(name = "idx_as_change_field_event", columnList = "event_id,sort_order")
)
@Getter
@NoArgsConstructor
public class AsChangeField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @ToString.Exclude
    private AsChangeEvent event;

    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    @Column(name = "field_label", nullable = false, length = 150)
    private String fieldLabel;

    @Lob
    @Column(name = "before_value")
    private String beforeValue;

    @Lob
    @Column(name = "after_value")
    private String afterValue;

    /** 고객 카카오 메시지로 확장할 때 노출 가능한 필드인지 구분합니다. */
    @Column(name = "customer_visible", nullable = false)
    private boolean customerVisible;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static AsChangeField of(
            String fieldKey,
            String fieldLabel,
            String beforeValue,
            String afterValue,
            boolean customerVisible,
            int sortOrder
    ) {
        AsChangeField field = new AsChangeField();
        field.fieldKey = required(fieldKey, "field", 100);
        field.fieldLabel = required(fieldLabel, field.fieldKey, 150);
        field.beforeValue = normalize(beforeValue);
        field.afterValue = normalize(afterValue);
        field.customerVisible = customerVisible;
        field.sortOrder = Math.max(0, sortOrder);
        return field;
    }

    void attach(AsChangeEvent event) {
        this.event = event;
    }

    private static String required(String value, String fallback, int max) {
        String normalized = normalize(value);
        if (normalized == null) normalized = fallback;
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
