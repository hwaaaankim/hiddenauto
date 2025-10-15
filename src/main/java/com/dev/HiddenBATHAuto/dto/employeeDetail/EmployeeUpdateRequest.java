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
public class EmployeeUpdateRequest {
	private Long memberId;
	private String name;
	private String phone;
	private String telephone; // optional
	private String email; // optional
	private String role; // MemberRole name
	private Long teamId;
	private Long teamCategoryId;
}
