package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderExcelAddressValidationResponse {
    private boolean valid;
    private String addressType;
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String jibunAddress;
    private String originAddress;
    private String detailAddress;
    private String message;
    private List<String> messages = new ArrayList<>();
}
