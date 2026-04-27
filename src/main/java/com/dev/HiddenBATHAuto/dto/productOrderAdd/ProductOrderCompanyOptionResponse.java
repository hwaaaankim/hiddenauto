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
    private LocalDateTime joinedAt;

    // 화면 표시용 전체 주소
    private String address;

    // 실제 우편번호
    private String zipCode;

    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;
}