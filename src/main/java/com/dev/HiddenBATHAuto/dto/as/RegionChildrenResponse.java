package com.dev.HiddenBATHAuto.dto.as;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RegionChildrenResponse {
    private String type; // "CITY" or "DISTRICT"
    private List<RegionOptionDto> items;

    @Data
    @AllArgsConstructor
    public static class RegionOptionDto {
        private Long id;
        private String name;
    }
}