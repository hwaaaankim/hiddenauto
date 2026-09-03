package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductRuleMatchMode {
    ALL("모든 조건 만족"),
    ANY("조건 중 하나 만족");

    private final String labelKr;
}
