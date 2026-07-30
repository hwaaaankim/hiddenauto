package com.dev.HiddenBATHAuto.dto.production;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductionCheckViewDto {
    private Long orderId;
    private String checkState;
    private String checkStateLabel;
    private boolean checked;
    private String checkedByUsername;
    private String checkedAtText;
    private String revisionMarkedByUsername;
    private String revisionMarkedAtText;
    private String revisionReason;
    private int revisionCount;
}
