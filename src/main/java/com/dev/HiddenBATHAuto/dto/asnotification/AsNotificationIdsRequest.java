package com.dev.HiddenBATHAuto.dto.asnotification;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AsNotificationIdsRequest {
    private List<Long> notificationIds;
}
