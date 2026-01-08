package com.dev.HiddenBATHAuto.dto.production;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StickerPrintDto {
    private Long orderId;
    private String status;
    private String companyName;

    private String roadAddress;
    private String detailAddress;
    private String regionText;   // 도/시/구 + 우편번호

    private String productName;
    private Integer quantity;
    private String optionSummary;

    private LocalDate preferredDeliveryDate;
}