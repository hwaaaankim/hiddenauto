package com.dev.HiddenBATHAuto.dto.production;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionOrderCheckResponse {

    private Long orderId;

    private boolean checked;

    private String checkedByUsername;

    private String checkedAtText;

    private String message;
}