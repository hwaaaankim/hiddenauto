package com.dev.HiddenBATHAuto.dto.orderchange;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderMemberCheckStateDto {
    private Long memberId;
    private String memberName;
    private String username;
    private String checkState;
    private String checkStateLabel;
    private long lastCheckedVersion;
    private long currentVersion;
    private String checkedAtText;
}
