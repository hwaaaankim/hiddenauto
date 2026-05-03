package com.dev.HiddenBATHAuto.dto.production;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionOverviewCompleteResponse {

    private Long orderId;
    private String status;
    private String statusLabel;
    private String message;
}