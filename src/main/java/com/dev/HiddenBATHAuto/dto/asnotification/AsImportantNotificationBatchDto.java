package com.dev.HiddenBATHAuto.dto.asnotification;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AsImportantNotificationBatchDto {
    private List<AsNotificationItemDto> content;
    private long totalPendingCount;
    private boolean hasMore;
    private int size;
}
