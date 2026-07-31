package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderExcelDeliveryHandlerAssignmentRequest {
    private Long deliveryMethodId;
    private String addressType;
    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String jibunAddress;
    private String originAddress;
    private String detailAddress;
}
