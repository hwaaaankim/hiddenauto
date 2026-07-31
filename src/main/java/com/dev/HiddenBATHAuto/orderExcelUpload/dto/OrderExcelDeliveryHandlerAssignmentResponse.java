package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderExcelDeliveryHandlerAssignmentResponse {
    private boolean matched;
    private Long memberId;
    private String memberName;
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String jibunAddress;
    private String originAddress;
    private String detailAddress;
    private String message;
}
