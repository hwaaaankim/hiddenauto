package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductRuleOperator {
    EQUALS("같음"),
    NOT_EQUALS("같지 않음"),
    GREATER_THAN_OR_EQUAL("이상"),
    LESS_THAN_OR_EQUAL("이하"),
    BETWEEN("범위 포함");

    private final String labelKr;
}
