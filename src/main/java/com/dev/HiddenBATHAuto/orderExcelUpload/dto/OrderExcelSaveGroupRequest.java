package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderExcelSaveGroupRequest {
    private int groupNo;
    private String companyName;
    /** 엑셀 S열 사업자등록번호입니다. 숫자만 10자리로 전달합니다. */
    private String businessNumber;
    private Long companyId;
    private String requestedByName;
    private Long requestedByMemberId;

    private String managedByName;
    private Long managedByMemberId;

    private Long deliveryMethodId;

    /**
     * 동일 배송지 Task 묶음 기준 배송 담당자입니다.
     * 프론트에서는 배송수단 옆에서 한 번만 수정하고, 저장 시 각 Order에 동일하게 반영합니다.
     */
    private String deliveryHandlerName;
    private Long deliveryHandlerMemberId;

    private String deliveryRuleCode;
    private boolean siteDelivery;
    private int deliveryCost;
    private int packingCost;

    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;

    private String siteZipCode;
    private String siteDoName;
    private String siteSiName;
    private String siteGuName;
    private String siteRoadAddress;
    private String siteDetailAddress;
    private String siteRecipientName;
    private String siteRecipientPhone;

    private String ordererName;
    private String ordererPhone;

    private List<OrderExcelSaveRowRequest> rows = new ArrayList<>();
}
