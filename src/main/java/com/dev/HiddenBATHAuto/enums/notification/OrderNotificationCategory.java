package com.dev.HiddenBATHAuto.enums.notification;

public enum OrderNotificationCategory {
    PRODUCTION("생산팀"),
    DELIVERY("배송팀"),
    DISPATCH("출고팀"),
    EMERGENCY("긴급");

    private final String label;

    OrderNotificationCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
