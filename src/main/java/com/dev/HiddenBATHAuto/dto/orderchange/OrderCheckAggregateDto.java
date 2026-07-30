package com.dev.HiddenBATHAuto.dto.orderchange;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderCheckAggregateDto {
    private Long orderId;
    private int trackedMemberCount;
    private int checkedCount;
    private int revisedCount;

    public String getDisplayText() {
        if (trackedMemberCount <= 0) {
            return "개인 확인기록 없음";
        }

        if (revisedCount > 0) {
            return "확인 " + checkedCount + "명 / 재확인 " + revisedCount + "명";
        }

        return "확인 " + checkedCount + "명";
    }
}
