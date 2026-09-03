package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductAttributeInputType {
    CHOICE("선택형", "등록된 옵션값 중 하나 또는 여러 개를 선택합니다."),
    NUMBER("숫자형", "수량처럼 숫자를 입력하고 단위·범위·간격을 검증합니다."),
    TEXT("문자형", "주문 시 간단한 사용자 지정 내용을 입력합니다."),
    DIMENSION("사이즈형", "W-H 또는 W-D-H 치수를 입력합니다.");

    private final String labelKr;
    private final String description;
}
