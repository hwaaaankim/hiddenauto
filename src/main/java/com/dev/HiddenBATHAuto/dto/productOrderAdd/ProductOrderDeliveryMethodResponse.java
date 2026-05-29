package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductOrderDeliveryMethodResponse {

    private Long id;
    private String methodName;
    private int methodPrice;
    private boolean directDelivery;
}
