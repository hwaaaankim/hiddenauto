package com.dev.HiddenBATHAuto.dto.ordernotification;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderNotificationReadLoadedRequest {
    /** 현재 알림 모달에 실제로 로드되어 표시 중인 알림 ID만 전달합니다. */
    private List<Long> notificationIds;
}
