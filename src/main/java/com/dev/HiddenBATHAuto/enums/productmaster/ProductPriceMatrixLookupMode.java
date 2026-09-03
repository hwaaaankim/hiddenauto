package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductPriceMatrixLookupMode {
    CEILING("올림 구간", "입력값 이상인 가장 가까운 축 값을 사용합니다."),
    EXACT("정확히 일치", "등록된 축 값과 정확히 같은 경우만 계산합니다."),
    FLOOR("내림 구간", "입력값 이하인 가장 가까운 축 값을 사용합니다.");

    private final String labelKr;
    private final String description;
}
