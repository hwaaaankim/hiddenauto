package com.dev.HiddenBATHAuto.enums.order;

public enum OrderChangeSourceArea {
    MANAGEMENT("관리팀"),
    PRODUCTION("생산팀"),
    DISPATCH("출고팀"),
    DELIVERY("배송팀"),
    CUSTOMER("고객"),
    SYSTEM("시스템");

    private final String label;

    OrderChangeSourceArea(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
