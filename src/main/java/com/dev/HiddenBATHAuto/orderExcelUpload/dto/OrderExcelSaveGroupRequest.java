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
     * 프론트에서는 배송수단 옆에서 한 번만 수정합니다. 저장 시 고객 발주/취소 Order는 제외하고 나머지 Order에 반영합니다.
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
    /** 업체 기본주소의 지번주소입니다. Order 저장 시 도로명 주소가 비어 있으면 fallback으로 사용합니다. */
    private String jibunAddress;
    /** 업체 기본주소의 원본주소입니다. Order 저장 시 도로명/지번 주소가 비어 있으면 fallback으로 사용합니다. */
    private String originAddress;
    private String detailAddress;

    private String siteZipCode;
    private String siteDoName;
    private String siteSiName;
    private String siteGuName;
    private String siteRoadAddress;
    /** 현장 배송지의 지번주소입니다. 현재 Order 스키마에는 별도 지번 컬럼이 없어 도로명 주소 fallback으로 사용합니다. */
    private String siteJibunAddress;
    /** 주소검색 전 원본 또는 등록주소의 원본주소입니다. */
    private String siteOriginAddress;
    private String siteDetailAddress;
    private String siteRecipientName;
    private String siteRecipientPhone;

    private String ordererName;
    private String ordererPhone;

    private List<OrderExcelSaveRowRequest> rows = new ArrayList<>();
}
