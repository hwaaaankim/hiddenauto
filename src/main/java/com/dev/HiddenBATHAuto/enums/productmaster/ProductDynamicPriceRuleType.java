package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductDynamicPriceRuleType {
    FIXED_ADD("조건부 고정금액", "선택 옵션이 존재하면 지정 금액을 적용합니다."),
    OPTION_X_NUMBER("옵션 단가 × 수량", "옵션값 단가 또는 지정 단가에 다른 숫자 그룹 값을 곱합니다."),
    MATRIX("2축 가격표", "W-H, W-D 등 두 축 가격표에서 금액을 찾습니다."),
    STEP_ADD("구간별 증분", "기준값을 넘을 때마다 지정 단위별 금액을 더합니다.");

    private final String labelKr;
    private final String description;
}
