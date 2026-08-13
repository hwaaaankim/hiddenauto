package com.dev.HiddenBATHAuto.dto.ordernotification;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderNotificationConfirmImportantRequest {
    /** 현재 강제 중요알림 팝업에 실제로 표시된 알림 ID만 전달합니다. */
    private List<Long> notificationIds;
}
