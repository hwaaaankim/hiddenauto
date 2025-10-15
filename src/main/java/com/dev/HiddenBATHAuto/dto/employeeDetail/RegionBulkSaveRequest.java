package com.dev.HiddenBATHAuto.dto.employeeDetail;

import java.util.List;

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
public class RegionBulkSaveRequest {
	private Long memberId;
	private List<RegionSelectionDTO> selections;
}
