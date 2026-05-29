package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductOrderCreateRequest {

    private Boolean standard;

    private Long standardCategoryId;
    private Long standardProductSeriesId;
    private Long productionCategoryId;

    private String productName;
    private String productSize;
    private String productColor;

    private int productCost = 0;
    private int quantity = 1;

    /** 실제 공급가입니다. 할인/DP/무상 제공 대응을 위해 단가*수량과 다를 수 있습니다. */
    private int supplyPrice = 0;

    /** 부가세 포함 총액입니다. */
    private int totalAmount = 0;

    /** 고객 남김말로 저장됩니다. */
    private String orderComment;

    /** 관리자 남김말로 저장됩니다. */
    private String adminMemo;

    private List<ProductOrderOptionEntryRequest> optionEntries = new ArrayList<>();
}
