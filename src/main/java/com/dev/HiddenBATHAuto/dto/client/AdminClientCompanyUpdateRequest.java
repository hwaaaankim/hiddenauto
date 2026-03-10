package com.dev.HiddenBATHAuto.dto.client;

import org.springframework.web.multipart.MultipartFile;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminClientCompanyUpdateRequest {

    private String companyName;
    private Integer point;
    private String businessNumber;

    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;

    /**
     * KEEP / REPLACE / DELETE
     */
    private String licenseAction;

    private MultipartFile businessLicenseFile;
}