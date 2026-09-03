package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductRuleSourceField {
    SELECTED_VALUE("선택 옵션"),
    WIDTH_MM("W 가로"),
    DEPTH_MM("D 깊이"),
    HEIGHT_MM("H 높이"),
    NUMBER_VALUE("숫자 입력값");

    private final String labelKr;
}
