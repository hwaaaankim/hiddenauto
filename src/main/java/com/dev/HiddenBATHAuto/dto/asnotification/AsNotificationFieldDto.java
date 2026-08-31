package com.dev.HiddenBATHAuto.dto.asnotification;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AsNotificationFieldDto {
    private String fieldKey;
    private String fieldLabel;
    private String beforeValue;
    private String afterValue;
}
