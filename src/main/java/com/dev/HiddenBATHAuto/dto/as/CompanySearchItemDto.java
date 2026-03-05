package com.dev.HiddenBATHAuto.dto.as;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySearchItemDto {
	private Long companyId;
	private String companyName;
	private String address;
	private String representativeName;
	private boolean hasRepresentative;
}