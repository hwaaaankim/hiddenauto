package com.dev.HiddenBATHAuto.dto.production;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionOverviewFieldDto {

    private String label;
    private String value;

    @Builder.Default
    private boolean important = false;

    public static ProductionOverviewFieldDto of(String label, String value) {
        return ProductionOverviewFieldDto.builder()
                .label(label)
                .value(value == null || value.isBlank() ? "-" : value)
                .important(false)
                .build();
    }

    public static ProductionOverviewFieldDto important(String label, String value) {
        return ProductionOverviewFieldDto.builder()
                .label(label)
                .value(value == null || value.isBlank() ? "-" : value)
                .important(true)
                .build();
    }
}