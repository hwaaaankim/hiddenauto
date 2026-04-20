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
    private String address;
}