package com.dev.HiddenBATHAuto.dto.delivery;

import java.util.List;

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
public class DeliveryOrderSummaryRes {

    private Long orderId;

    private String companyName;
    private String requesterName;
    private String companyContact;
    private String companyAddress;

    private String orderAddress;
    private String ordererPhone;

    private String productText;

    private String status;
    private String statusLabel;

    @Builder.Default
    private List<String> deliveryImageUrls = List.of();
}
