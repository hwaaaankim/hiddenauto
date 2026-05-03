package com.dev.HiddenBATHAuto.dto.production;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductionOverviewImageDto {

    private Long imageId;
    private String url;
    private String filename;
    private String type;
}