package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductOrderAddRequest {

    @NotNull(message = "대리점을 선택해 주세요.")
    private Long companyId;

    @NotNull(message = "배송 희망일을 선택해 주세요.")
    private LocalDate preferredDeliveryDate;

    @NotNull(message = "배송수단을 선택해 주세요.")
    private Long deliveryMethodId;

    // 직배송/현장배송/화물에서 관리자가 직접 지정한 경우 우선 적용됩니다.
    // null이면 주소 기준 자동 배정을 시도하고, 매칭 담당자가 없으면 미지정으로 저장합니다.
    private Long deliveryHandlerId;

    private int packingCost = 0;
    private int deliveryCost = 0;

    private String ordererName;
    private String ordererPhone;

    // 공통 배송지: 직배송/화물 자동배정 기준 주소
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;

    // 현장 배송지: 현장배송 자동배정 기준 주소
    private String siteZipCode;
    private String siteDoName;
    private String siteSiName;
    private String siteGuName;
    private String siteRoadAddress;
    private String siteDetailAddress;

    @Valid
    @NotEmpty(message = "최소 1개의 주문이 필요합니다.")
    private List<ProductOrderCreateRequest> orders = new ArrayList<>();
}
