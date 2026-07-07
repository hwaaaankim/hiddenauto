package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderExcelDeliveryMethodOptionResponse {
    private Long id;
    private String methodName;
    private int methodPrice;
    private boolean directCandidate;
    private boolean siteCandidate;
    private boolean cargoCandidate;
    private boolean visitCandidate;
    private boolean parcelCandidate;
}
