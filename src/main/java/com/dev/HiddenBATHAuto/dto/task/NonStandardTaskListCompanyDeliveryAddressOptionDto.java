package com.dev.HiddenBATHAuto.dto.task;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NonStandardTaskListCompanyDeliveryAddressOptionDto {

    private Long companyId;
    private Long addressId;
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;
    private String fullAddress;
}
