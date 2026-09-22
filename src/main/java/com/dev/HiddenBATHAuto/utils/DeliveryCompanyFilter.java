package com.dev.HiddenBATHAuto.utils;

import java.util.Locale;
import com.dev.HiddenBATHAuto.model.task.Order;

public final class DeliveryCompanyFilter {
    private DeliveryCompanyFilter() { }
    public static boolean matches(Order order, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        if (order == null || order.getTask() == null || order.getTask().getRequestedBy() == null
                || order.getTask().getRequestedBy().getCompany() == null) return false;
        String name = order.getTask().getRequestedBy().getCompany().getCompanyName();
        return name != null && name.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }
    public static void validateRange(Long from, Long to) {
        if ((from != null && from <= 0) || (to != null && to <= 0)
                || (from != null && to != null && from > to)) {
            throw new IllegalArgumentException("Order ID 범위를 확인해주세요. 1 이상이며 From은 To 이하여야 합니다.");
        }
    }
}
