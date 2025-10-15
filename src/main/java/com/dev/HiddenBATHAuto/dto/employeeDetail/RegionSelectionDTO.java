package com.dev.HiddenBATHAuto.dto.employeeDetail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegionSelectionDTO {
	private Long provinceId;
	private Long cityId; // nullable
	private Long districtId; // nullable
}
