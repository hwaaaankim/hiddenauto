package com.dev.HiddenBATHAuto.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductOrderRequest {

    // 공통 선택값
    private Map<String, Object> selections; // category, product, etc.

    private int quantity; // 수량
}
