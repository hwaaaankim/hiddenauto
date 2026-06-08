package com.dev.HiddenBATHAuto.dto.task;

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
public class NonStandardTaskListCompanyOptionDto {

    private Long companyId;
    private String companyName;
    private String representativeName;

    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;
    private String fullAddress;

    public NonStandardTaskListCompanyOptionDto(
            Long companyId,
            String companyName,
            String representativeName
    ) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.representativeName = representativeName;
    }
}
