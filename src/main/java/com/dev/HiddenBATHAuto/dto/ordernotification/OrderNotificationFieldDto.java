package com.dev.HiddenBATHAuto.dto.ordernotification;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderNotificationFieldDto {
    private String fieldKey;
    private String fieldLabel;
    private String beforeValue;
    private String afterValue;
}
