package com.dev.HiddenBATHAuto.dto.task;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NonStandardTaskListCompanyOrdererInfoOptionDto {

    private Long companyId;
    private Long ordererInfoId;
    private String ordererName;
    private String ordererPhone;
}
