package com.dev.HiddenBATHAuto.dto.employee;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeUpdateRequest {
    private Long memberId;

    private String name;
    private String phone;
    private String telephone;
    private String email;

    private String role;

    private Long teamId;
    private Long teamCategoryId;
}