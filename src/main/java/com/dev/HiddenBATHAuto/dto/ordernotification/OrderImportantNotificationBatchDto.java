package com.dev.HiddenBATHAuto.dto.ordernotification;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderImportantNotificationBatchDto {
    private List<OrderNotificationItemDto> content;
    private long totalPendingCount;
    private boolean hasMore;
    private int size;
}
