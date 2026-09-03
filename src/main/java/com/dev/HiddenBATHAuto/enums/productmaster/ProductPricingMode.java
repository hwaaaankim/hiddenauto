package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductPricingMode {
    FIXED("고정 공급가", "입력한 기본 공급가를 제품 공급가로 사용합니다."),
    BASE_PLUS_COMPONENTS("기본가 + 구성요소", "기본 공급가에 제품 정체성 구성요소의 가격 조정액을 합산합니다."),
    RULE_ENGINE("규칙 기반 견적", "기본 공급가에서 옵션 단가·수량·가격표·구간 규칙을 적용해 주문 시 계산합니다.");

    private final String labelKr;
    private final String description;
}
