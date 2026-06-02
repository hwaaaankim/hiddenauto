package com.dev.HiddenBATHAuto.dto.delivery;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
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

    private String deliveryAddress;
    private LocalDateTime preferredDeliveryDate;

    private String categoryName;

    private String statusName;
    private String statusLabel;

    private int orderIndex;
}