package com.dev.HiddenBATHAuto.dto;

import lombok.Data;

@Data
public class CompanyDeliveryAddressRequest {
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;
}