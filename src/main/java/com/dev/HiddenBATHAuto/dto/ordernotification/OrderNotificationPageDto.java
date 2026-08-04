package com.dev.HiddenBATHAuto.dto.ordernotification;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderNotificationPageDto {
    private List<OrderNotificationItemDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
}
