package com.dev.HiddenBATHAuto.dto.orderchange;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import com.dev.HiddenBATHAuto.enums.order.OrderWorkArea;

public record OrderFieldChangeCommand(
        String fieldKey,
        String fieldLabel,
        String beforeValue,
        String afterValue,
        Set<OrderWorkArea> affectedAreas
) {
    public OrderFieldChangeCommand {
        affectedAreas = affectedAreas == null || affectedAreas.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(affectedAreas));
    }

    public static OrderFieldChangeCommand of(
            String fieldKey,
            String fieldLabel,
            Object beforeValue,
            Object afterValue,
            OrderWorkArea... affectedAreas
    ) {
        EnumSet<OrderWorkArea> areas = EnumSet.noneOf(OrderWorkArea.class);
        if (affectedAreas != null) {
            for (OrderWorkArea area : affectedAreas) {
                if (area != null) areas.add(area);
            }
        }

        return new OrderFieldChangeCommand(
                fieldKey,
                fieldLabel,
                text(beforeValue),
                text(afterValue),
                areas
        );
    }

    public boolean isActuallyChanged() {
        return !java.util.Objects.equals(normalize(beforeValue), normalize(afterValue));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
