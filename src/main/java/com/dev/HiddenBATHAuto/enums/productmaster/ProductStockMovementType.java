package com.dev.HiddenBATHAuto.enums.productmaster;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProductStockMovementType {
    INITIAL("최초재고"),
    INBOUND("입고"),
    OUTBOUND("출고"),
    RETURN("반품입고"),
    DAMAGE("파손/폐기"),
    ADJUSTMENT("재고조정");

    private final String labelKr;
}
