package com.dev.HiddenBATHAuto.dto.delivery;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
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
    private String productText;

    private String status;
    private String statusLabel;

    // ✅ 완료처리에 사용한 이미지(배송 증빙 이미지) URL 목록
    private List<String> deliveryImageUrls;
}