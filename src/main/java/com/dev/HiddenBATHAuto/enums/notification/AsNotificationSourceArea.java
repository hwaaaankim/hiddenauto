package com.dev.HiddenBATHAuto.enums.notification;

public enum AsNotificationSourceArea {
    CUSTOMER("고객"),
    MANAGEMENT("관리팀"),
    AS_TEAM("AS팀");

    private final String label;

    AsNotificationSourceArea(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
