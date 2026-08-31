package com.dev.HiddenBATHAuto.enums.notification;

public enum AsNotificationRecipientGroup {
    ADMIN("최고관리자(admin)"),
    MANAGER_02("AS 관리담당자(manager_02)"),
    AS_HANDLER_CURRENT("현재 AS 담당직원"),
    CUSTOMER("신청 고객");

    private final String label;

    AsNotificationRecipientGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
