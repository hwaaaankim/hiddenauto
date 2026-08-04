package com.dev.HiddenBATHAuto.dto.ordernotification;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderNotificationPageDto {
    private List<OrderNotificationItemDto> content;
    /** 다음 조회 시 사용할 마지막 알림 ID입니다. */
    private Long nextCursor;
    private boolean hasNext;
    private int size;
}
