package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductAttributeSelectionMode {
    SINGLE("하나 선택"),
    MULTIPLE("여러 개 선택");

    private final String labelKr;
}
