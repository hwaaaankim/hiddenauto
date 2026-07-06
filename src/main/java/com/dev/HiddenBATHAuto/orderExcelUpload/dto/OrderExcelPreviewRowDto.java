package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderExcelPreviewRowDto {
    private Integer excelRowNumber;
    private String originalCompanyText;
    private String originalItemName;

    private String preferredDeliveryDate;
    private String itemNameForSave;
    private String calculatedProductName;
    private String categoryName;
    private Long productionCategoryId;
    private String middleCategoryName;
    private Long amountItemMasterId;

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
    private boolean saveTarget = true;

    private List<OrderExcelIssueDto> issues = new ArrayList<>();
}
