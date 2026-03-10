package com.dev.HiddenBATHAuto.dto.as;

import lombok.Data;

@Data
public class CustomerAsUpdateRequest {

    private String customerName;

    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;

    private String reason;

    private String productName;
    private String productSize;
    private String productColor;
    private String productOptions;

    private String onsiteContact;

    /** "상부장 - 도어 파손" 형태 */
    private String subject;
}