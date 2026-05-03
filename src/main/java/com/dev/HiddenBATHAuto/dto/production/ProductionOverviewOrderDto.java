package com.dev.HiddenBATHAuto.dto.production;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionOverviewOrderDto {

    private Long orderId;

    private String status;
    private String statusLabel;
    private boolean canComplete;

    private String companyName;
    private String productName;
    private String categoryName;
    private String standardLabel;

    private Integer quantity;
    private String createdDateText;
    private String preferredDeliveryDateText;

    private String orderComment;
    private String adminMemo;

    @Builder.Default
    private List<ProductionOverviewFieldDto> fields = new ArrayList<>();

    @Builder.Default
    private List<ProductionOverviewImageDto> adminImages = new ArrayList<>();
}