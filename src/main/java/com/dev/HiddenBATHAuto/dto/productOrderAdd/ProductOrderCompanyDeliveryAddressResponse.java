package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductOrderCompanyDeliveryAddressResponse {

    private Long id;

    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;

    private String address;
}