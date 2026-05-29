package com.dev.HiddenBATHAuto.dto.productOrderAdd;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductOrderCompanyOrdererInfoResponse {

    private Long id;
    private String ordererName;
    private String ordererPhone;
    private LocalDateTime createdAt;
}
