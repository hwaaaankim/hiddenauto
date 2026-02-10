package com.dev.HiddenBATHAuto.dto.analytics;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductOptionNormalized {
    private boolean standard;   // true=규격, false=비규격

    // 공통 트리
    private String top;         // 규격: 카테고리, 비규격: 카테고리(=Sort 의미로 쓰거나 별도)
    private String mid;         // 규격: 시리즈, 비규격: 제품시리즈
    private String product;     // 제품명
    private String color;       // 색상
    private String productCode; // 규격 제품코드 등

    private Map<String, Object> raw;
}