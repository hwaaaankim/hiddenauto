package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductOrderCompanyOptionResponse {

    private Long companyId;
    private String companyName;
    private String representativeName;
    private String representativePhone;
    private LocalDateTime joinedAt;

    private String address;
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;
}
