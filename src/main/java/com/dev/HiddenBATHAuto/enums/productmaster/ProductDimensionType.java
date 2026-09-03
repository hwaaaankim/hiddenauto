package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductDimensionType {
    NONE("일반 고정값"),
    WIDTH_HEIGHT("2차원 · W-H 고정 입력"),
    WIDTH_DEPTH_HEIGHT("3차원 · W-D-H 고정 입력"),
    CUSTOM("비규격 · 주문 시 입력");

    private final String labelKr;
}
