package com.dev.HiddenBATHAuto.dto.production;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductionListExcelRowDto {

    private Long orderId;

    private String productName;

    private String productColor;

    private String productSize;

    private Integer quantity;

    private String adminMemo;

    private String categoryName;
}