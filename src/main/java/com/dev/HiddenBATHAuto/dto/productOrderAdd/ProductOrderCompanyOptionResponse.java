package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductOrderCompanyOptionResponse {

    private Long companyId;
    private String companyName;
    private String representativeName;
    private LocalDateTime joinedAt;

    // 드롭다운/요약 표시용 전체 주소
    private String address;

    // 회원주소와 동일 체크 시 자동 입력할 실제 주소 필드
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;
}