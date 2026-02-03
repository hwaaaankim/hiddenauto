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
    private String telephone;
    private String email;

    private String role;

    private Long teamId;
    private Long teamCategoryId;

    /**
     * 배송/AS 담당구역이 있는 직원이
     * 다른 팀(생산/관리 등)으로 변경될 때 담당구역이 삭제됩니다.
     * 프론트 confirm을 거친 경우에만 true로 전달합니다.
     */
    private Boolean confirmRegionReset;
}
