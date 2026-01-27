package com.dev.HiddenBATHAuto.dto.delivery;

import lombok.Data;

// DeliveryExcelRequest.java
@Data
public class DeliveryExcelRequest {
    private Long deliveryHandlerId;
    private java.time.LocalDate deliveryDate;
    private java.util.List<Long> orderedOrderIds;
}