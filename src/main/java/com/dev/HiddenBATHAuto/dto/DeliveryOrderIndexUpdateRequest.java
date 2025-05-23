package com.dev.HiddenBATHAuto.dto;

import java.util.List;

import lombok.Data;

@Data
public class DeliveryOrderIndexUpdateRequest {
    private Long deliveryHandlerId;
    private String deliveryDate; // "yyyy-MM-dd"
    private List<OrderIndexDto> orderList;

    @Data
    public static class OrderIndexDto {
        private Long orderId;
        private int orderIndex;
    }
}