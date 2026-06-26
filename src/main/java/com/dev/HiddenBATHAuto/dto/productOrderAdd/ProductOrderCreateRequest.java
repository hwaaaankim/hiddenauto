package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import java.util.List;

import lombok.Data;

@Data
public class ProductOrderCreateRequest {

    private Boolean standard;

    private Long standardCategoryId;

    private Long standardProductSeriesId;

    private Long productionCategoryId;

    /**
     * 관리자 등록 화면에서 주문별로 직접 ON/OFF 하는 거울재단용 수동 지정값입니다.
     * null 또는 false여도 저장 서비스에서 제품명/시리즈/옵션 키워드 자동 판정을 한 번 더 수행합니다.
     */
    private Boolean mirrorCuttingProduct;

    private String productName;

    private String productSize;

    private String productColor;

    private int productCost;

    private int quantity;

    private int supplyPrice;

    private int totalAmount;

    private String orderComment;

    private String adminMemo;

    private List<ProductOrderOptionEntryRequest> optionEntries;
}
