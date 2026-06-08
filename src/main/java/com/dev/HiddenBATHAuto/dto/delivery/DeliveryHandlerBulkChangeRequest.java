package com.dev.HiddenBATHAuto.dto.delivery;

import java.util.List;

import lombok.Data;

@Data
public class DeliveryHandlerBulkChangeRequest {

    private List<Long> orderIds;
    private Long newHandlerId;
}
