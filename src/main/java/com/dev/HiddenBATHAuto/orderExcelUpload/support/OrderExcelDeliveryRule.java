package com.dev.HiddenBATHAuto.orderExcelUpload.support;

public enum OrderExcelDeliveryRule {
    DIRECT("DIRECT", "직배송", "직배송", true),
    SITE("SITE", "현장배송", "현장배송", true),
    CARGO("CARGO", "화물", "화물", true),
    VISIT("VISIT", "방문", "방문", false),
    PARCEL("PARCEL", "택배", "택배", false),
    UNDELIVERED("UNDELIVERED", "미배송", "미배송", false);

    private final String code;
    private final String label;
    private final String deliveryMethodName;
    private final boolean handlerAssignable;

    OrderExcelDeliveryRule(String code, String label, String deliveryMethodName, boolean handlerAssignable) {
        this.code = code;
        this.label = label;
        this.deliveryMethodName = deliveryMethodName;
        this.handlerAssignable = handlerAssignable;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDeliveryMethodName() {
        return deliveryMethodName;
    }

    public boolean isHandlerAssignable() {
        return handlerAssignable;
    }

    public boolean needsSiteAddressForAssignment() {
        return this == SITE || this == CARGO;
    }

    public boolean isNoHandlerRule() {
        return this == VISIT || this == PARCEL || this == UNDELIVERED;
    }

    public static OrderExcelDeliveryRule fromCode(String code) {
        if (code == null || code.trim().isBlank()) {
            return DIRECT;
        }
        String normalized = code.trim();
        for (OrderExcelDeliveryRule value : values()) {
            if (value.code.equalsIgnoreCase(normalized) || value.name().equalsIgnoreCase(normalized)) {
                return value;
            }
        }
        return DIRECT;
    }
}
