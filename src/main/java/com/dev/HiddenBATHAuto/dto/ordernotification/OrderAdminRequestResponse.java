package com.dev.HiddenBATHAuto.dto.ordernotification;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderAdminRequestResponse {
    private boolean success;
    private Long orderId;
    private Long taskId;
    private Long eventId;
    private Long managedById;
    private String managedByName;
    private String message;
}
