package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductOrderCompanyDeliveryAddressResponse {

    private Long id;

    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;

    // 화면 표시용 전체 주소
    private String address;
}