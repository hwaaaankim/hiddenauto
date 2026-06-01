package com.dev.HiddenBATHAuto.dto.production;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
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
    private int quantity;

    private String createdDateText;
    private String preferredDeliveryDateText;

    private String orderComment;
    private String adminMemo;

    @Builder.Default
    private List<ProductionOverviewFieldDto> fields = new ArrayList<>();

    @Builder.Default
    private List<ProductionOverviewImageDto> adminImages = new ArrayList<>();

    /** 기존 JS 호환용: 최신 확인 상태일 때만 true */
    private boolean checked;

    /** UNCHECKED / CHECKED / REVISED_AFTER_CHECK */
    private String checkState;

    /** 미확인 / 확인 / 재수정 */
    private String checkStateLabel;

    private String checkedByUsername;
    private String checkedAtText;

    private String revisionMarkedByUsername;
    private String revisionMarkedAtText;
    private String revisionReason;
    private int revisionCount;
}
