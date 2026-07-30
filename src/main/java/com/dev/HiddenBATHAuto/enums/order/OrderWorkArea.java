package com.dev.HiddenBATHAuto.enums.order;

public enum OrderWorkArea {
    PRODUCTION("생산팀"),
    DISPATCH("출고팀"),
    DELIVERY("배송팀");

    private final String label;

    OrderWorkArea(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
