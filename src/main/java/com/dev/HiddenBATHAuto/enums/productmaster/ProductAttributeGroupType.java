package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductAttributeGroupType {
    CORE("제품 정체성", "제품 코드와 SKU 단위 총재고를 결정합니다."),
    INTERNAL("내부 구성", "같은 SKU 안에서 생산 사양과 가격만 달라집니다."),
    ADD_ON("외부 추가옵션", "같은 SKU의 재고 안에서 포함 수량과 추가금액을 별도로 관리합니다.");

    private final String labelKr;
    private final String description;
}
