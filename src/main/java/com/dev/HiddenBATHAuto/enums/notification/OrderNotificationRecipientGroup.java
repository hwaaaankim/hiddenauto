package com.dev.HiddenBATHAuto.enums.notification;

/**
 * 발주 변경 알림의 수신자 역할 그룹입니다.
 */
public enum OrderNotificationRecipientGroup {
    MANAGEMENT("관리 담당자·admin"),
    PRODUCTION_CURRENT("현재 생산 담당 분류"),
    PRODUCTION_PREVIOUS("변경 전 생산 담당 분류"),
    DELIVERY_CURRENT("현재·신규 배송 담당자"),
    DELIVERY_PREVIOUS("변경 전 배송 담당자"),
    DISPATCH("출고팀"),
    AUDIT_ONLY("감사이력만 저장");

    private final String label;

    OrderNotificationRecipientGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
