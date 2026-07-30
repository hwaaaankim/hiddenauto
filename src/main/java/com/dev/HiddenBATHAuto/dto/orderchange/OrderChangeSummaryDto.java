package com.dev.HiddenBATHAuto.dto.orderchange;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderChangeSummaryDto {
    private Long eventId;
    private Long orderId;
    private String sourceArea;
    private String sourceAreaLabel;
    private String actorDisplay;
    private String operationLabel;
    private String summary;
    private String requestPath;
    private String changedAtText;
}
