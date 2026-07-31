package com.dev.HiddenBATHAuto.orderExcelUpload.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class OrderExcelCompanyAddressLookupResponse {
    private Long companyId;
    private String companyName;
    private String businessNumber;
    private Long requestedByMemberId;
    private String requestedByName;
    private List<OrderExcelCompanyAddressOptionResponse> addresses = new ArrayList<>();
}
