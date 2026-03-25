package com.dev.HiddenBATHAuto.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AsBillingTarget {

    CUSTOMER("고객 청구"),
    DEALER("대리점 청구");

    private final String labelKr;
}