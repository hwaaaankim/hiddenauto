package com.dev.HiddenBATHAuto.dto.delivery;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class DeliveryReorderByTaskRequest {
    private Long deliveryHandlerId;
    private LocalDate deliveryDate;
    private List<Long> pendingOrderIds;
}