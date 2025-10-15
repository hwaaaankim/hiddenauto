package com.dev.HiddenBATHAuto.dto.employeeDetail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MemberRegionSimpleDTO {
    private Long id;
    private String provinceName;
    private String cityName;
    private String districtName;
}