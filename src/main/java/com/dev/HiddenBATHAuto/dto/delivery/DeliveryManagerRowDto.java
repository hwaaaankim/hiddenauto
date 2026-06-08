package com.dev.HiddenBATHAuto.dto.delivery;

import java.time.LocalDateTime;

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
public class DeliveryManagerRowDto {

    private Long orderId;
    private Long taskId;

    private String companyName;

    private String representativeName;
    private String representativePhone;

    private String ordererName;
    private String ordererPhone;

    private String productName;
    private String productSize;

    private int quantity;

    // ✅ 추가
    private Long deliveryMethodId;

    // ✅ 추가
    private String deliveryMethodName;

    private String deliveryAddress;

    private LocalDateTime preferredDeliveryDate;

    private String categoryName;

    private String statusName;
    private String statusLabel;

    private int orderIndex;
}