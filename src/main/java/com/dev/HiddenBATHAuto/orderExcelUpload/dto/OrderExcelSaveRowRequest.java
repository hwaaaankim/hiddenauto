package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderExcelSaveRowRequest {
    private Integer excelRowNumber;
    private boolean saveTarget = true;

    private String preferredDeliveryDate;
    private String originalItemName;
    private String itemNameForSave;
    private String calculatedProductName;
    private String categoryName;
    private Long productionCategoryId;
    private String middleCategoryName;

    private String size;
    private String color;
    private int quantity;
    private String adminMemo;

    private String deliveryHandlerName;
    private Long deliveryHandlerMemberId;

    private int productCost;
    private int supplyPrice;
    private int vatAmount;
    private int totalAmount;

    private boolean standard;
    private boolean mirrorCuttingProduct;
}
