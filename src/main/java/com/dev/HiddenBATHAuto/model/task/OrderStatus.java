package com.dev.HiddenBATHAuto.model.task;

public enum OrderStatus {
    REQUESTED("고객 발주"),
    CONFIRMED("승인 완료"),
    PRODUCTION_DONE("생산 완료"),
    DELIVERY_DONE("배송 완료"),
    CANCELED("취소");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
