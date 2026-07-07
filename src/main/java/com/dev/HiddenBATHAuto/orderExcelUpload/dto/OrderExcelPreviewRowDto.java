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

    /**
     * 미리보기 화면에서 선택한 이미지 파일들을 저장 요청 multipart와 매칭하기 위한 클라이언트 키입니다.
     * DB에는 저장하지 않습니다.
     */
    private String imageKey;
    private int imageCount;

    private List<OrderExcelIssueDto> issues = new ArrayList<>();
}
