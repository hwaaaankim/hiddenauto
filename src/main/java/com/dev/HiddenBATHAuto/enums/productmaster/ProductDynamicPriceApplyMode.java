package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductDynamicPriceApplyMode {
    ADD("가산", "현재 계산금액에 더합니다."),
    REPLACE_BASE("기본가 대체", "제품 기본 공급가를 계산 결과로 대체합니다.");

    private final String labelKr;
    private final String description;
}
