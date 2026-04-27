package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductOrderCompanyDeliveryAddressResponse {

    private Long id;

    /**
     * 우편번호
     */
    private String zipCode;

    /**
     * 도/시
     */
    private String doName;

    /**
     * 시
     */
    private String siName;

    /**
     * 구/군
     */
    private String guName;

    /**
     * 도로명 주소
     */
    private String roadAddress;

    /**
     * 상세주소
     */
    private String detailAddress;

    /**
     * 화면 표시용 전체 주소
     */
    private String address;
}