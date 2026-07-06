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
    private Long companyId;
    private String requestedByName;
    private Long requestedByMemberId;

    private String managedByName;
    private Long managedByMemberId;

    private Long deliveryMethodId;
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
