package com.dev.HiddenBATHAuto.dto.task;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NonStandardTaskListCompanyMemberOptionDto {
    private Long companyId;
    private Long memberId;
    private String memberName;
}