package com.dev.HiddenBATHAuto.service.ordernotification;

import java.util.Locale;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.enums.notification.OrderNotificationAction;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeEvent;
import com.dev.HiddenBATHAuto.model.task.audit.OrderChangeField;

@Component
public class OrderNotificationActionResolver {

    public OrderNotificationAction resolve(OrderChangeEvent event) {
        if (event == null) return OrderNotificationAction.UPDATE;

        String code = safe(event.getOperationCode()).toUpperCase(Locale.ROOT);
        if (code.startsWith("ADMIN_REQUEST_")) return OrderNotificationAction.ADMIN_REQUEST;
        if (code.contains("CHECK_CONFIRM") || code.contains("MARK_CHECKED")) {
            return OrderNotificationAction.CHECK_CONFIRM;
        }
        if (code.contains("DELETE")) return OrderNotificationAction.DELETE;
        if (code.contains("URGENT_REGISTER") || code.contains("URGENT_ORDER_CREATED")) {
            return OrderNotificationAction.URGENT_REGISTER;
        }
        if (code.contains("NORMAL_REGISTER") || code.contains("NORMAL_ORDER_CREATED")) {
            return OrderNotificationAction.NORMAL_REGISTER;
        }
        if (code.contains("ORDER_CREATED") || code.contains("ORDER_REGISTER") || code.contains("REGISTRATION")) {
            return OrderNotificationAction.REGISTER;
        }
        if (code.contains("DELIVERY_METHOD") && code.contains("CHANGE")) {
            return OrderNotificationAction.DELIVERY_METHOD_CHANGE;
        }
        if (code.contains("HANDLER") && code.contains("CHANGE")) {
            return OrderNotificationAction.DELIVERY_HANDLER_CHANGE;
        }
        if (code.startsWith("PRODUCTION_") && code.contains("COMPLETE")) {
            return OrderNotificationAction.PRODUCTION_COMPLETE;
        }
        if (code.startsWith("DELIVERY_") && code.contains("COMPLETE")) {
            return OrderNotificationAction.DELIVERY_COMPLETE;
        }
        if (code.startsWith("DISPATCH_") && code.contains("COMPLETE")) {
            return OrderNotificationAction.DISPATCH_COMPLETE;
        }

        StatusTransition transition = resolveTransition(event);
        if (transition.visibleToHidden()) return OrderNotificationAction.CANCEL_OR_HIDE;
        if (transition.hiddenToVisible() || transition.backwardVisible()) {
            return OrderNotificationAction.RESTORE_OR_ROLLBACK;
        }
        if (transition.changed()) return OrderNotificationAction.STATUS_CHANGE;
        return OrderNotificationAction.UPDATE;
    }

    private StatusTransition resolveTransition(OrderChangeEvent event) {
        if (event == null || event.getFields() == null) return StatusTransition.none();
        for (OrderChangeField field : event.getFields()) {
            if (field == null) continue;
            String key = safe(field.getFieldKey());
            String label = safe(field.getFieldLabel());
            if ("status".equalsIgnoreCase(key) || "오더 상태".equals(label) || "발주상태".equals(label)) {
                return new StatusTransition(parseStatus(field.getBeforeValue()), parseStatus(field.getAfterValue()));
            }
        }
        return StatusTransition.none();
    }

    private OrderStatus parseStatus(String value) {
        String normalized = safe(value);
        if (normalized.isBlank() || "-".equals(normalized)) return null;
        for (OrderStatus status : OrderStatus.values()) {
            if (status.name().equalsIgnoreCase(normalized) || status.getLabel().equals(normalized)) return status;
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record StatusTransition(OrderStatus before, OrderStatus after) {
        static StatusTransition none() {
            return new StatusTransition(null, null);
        }

        boolean changed() {
            return before != null && after != null && before != after;
        }

        boolean visibleToHidden() {
            return changed() && !hidden(before) && hidden(after);
        }

        boolean hiddenToVisible() {
            return changed() && hidden(before) && !hidden(after);
        }

        boolean backwardVisible() {
            return changed() && !hidden(before) && !hidden(after) && rank(after) < rank(before);
        }

        private static boolean hidden(OrderStatus status) {
            return status == OrderStatus.REQUESTED || status == OrderStatus.CANCELED;
        }

        private static int rank(OrderStatus status) {
            if (status == null || status == OrderStatus.CANCELED) return -1;
            return switch (status) {
                case REQUESTED -> 0;
                case CONFIRMED -> 1;
                case PRODUCTION_DONE -> 2;
                case DISPATCH_DONE -> 3;
                case DELIVERY_DONE -> 4;
                case CANCELED -> -1;
            };
        }
    }
}
