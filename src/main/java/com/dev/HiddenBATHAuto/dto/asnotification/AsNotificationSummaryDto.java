package com.dev.HiddenBATHAuto.dto.asnotification;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AsNotificationSummaryDto {
    private long totalUnreadCount;
    private long importantUnreadCount;
    private long pendingImportantConfirmationCount;
}
