package com.dev.HiddenBATHAuto.enums.order;

public enum OrderCheckState {

    UNCHECKED("미확인"),
    CHECKED("확인"),
    REVISED_AFTER_CHECK("재수정");

    private final String label;

    OrderCheckState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isLatestChecked() {
        return this == CHECKED;
    }

    public boolean isNeedProductionCheck() {
        return this == UNCHECKED || this == REVISED_AFTER_CHECK;
    }
}