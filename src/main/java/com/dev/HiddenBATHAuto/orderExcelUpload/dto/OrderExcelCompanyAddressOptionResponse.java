package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderExcelCompanyAddressOptionResponse {
    private String sourceType;
    private Long addressId;
    private String label;
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String jibunAddress;
    private String originAddress;
    private String detailAddress;
    private String fullAddress;

    /** 서버 공통 주소 검증을 통과한 등록주소지만 선택할 수 있습니다. */
    private boolean valid;
    private String validationMessage;
}
