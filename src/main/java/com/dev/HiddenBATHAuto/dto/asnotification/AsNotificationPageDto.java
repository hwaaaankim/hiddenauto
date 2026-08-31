package com.dev.HiddenBATHAuto.dto.asnotification;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AsNotificationPageDto {
    private List<AsNotificationItemDto> content;
    private Long nextCursor;
    private boolean hasNext;
    private int size;
}
