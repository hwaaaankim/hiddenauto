package com.dev.HiddenBATHAuto.dto.production;

import java.util.List;

import com.dev.HiddenBATHAuto.dto.orderchange.OrderChangeNoticeDto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductionOrderCheckResponse {
    private Long orderId;
    private boolean checked;
    private String checkState;
    private String checkStateLabel;
    private String checkedByUsername;
    private String checkedAtText;
    private boolean revisedBeforeCheck;
    private List<OrderChangeNoticeDto> changeNotices;
    private String message;
}
