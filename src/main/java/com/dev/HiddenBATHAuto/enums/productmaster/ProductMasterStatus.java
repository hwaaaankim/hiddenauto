package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductMasterStatus {
    DRAFT("작성중"),
    ACTIVE("사용중"),
    DISCONTINUED("단종");

    private final String labelKr;
}
