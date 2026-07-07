package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderExcelPreviewGroupDto {
    private int groupNo;
    private String rawCompanyText;
    private String companyName;
    private Long companyId;
    private String requestedByName;
    private Long requestedByMemberId;

    private String managedByName;
    private Long managedByMemberId;

    private Long deliveryMethodId;
    private String deliveryMethodName;

    /**
     * 동일 배송지 Task 묶음 기준 배송 담당자입니다.
     * 화면에서는 배송수단 옆에 표시하고, 저장 시 각 Order에 동일하게 반영합니다.
     */
    private String deliveryHandlerName;
    private Long deliveryHandlerMemberId;

    private String deliveryRuleCode;
    private String deliveryRuleLabel;
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
    private String siteAddressRaw;

    private String ordererName;
    private String ordererPhone;

    private List<OrderExcelPreviewRowDto> rows = new ArrayList<>();
    private List<OrderExcelIssueDto> issues = new ArrayList<>();
}
