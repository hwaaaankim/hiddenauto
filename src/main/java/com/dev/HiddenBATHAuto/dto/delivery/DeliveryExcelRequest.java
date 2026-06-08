package com.dev.HiddenBATHAuto.dto.delivery;

import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeliveryExcelRequest {

    private Long deliveryHandlerId;

    /**
     * 기존 코드 호환용입니다.
     * fromDate/toDate가 같은 경우 JS에서 함께 넘기지만,
     * 이번 구현에서는 orderedOrderIds 기준으로 출력하므로 필수값으로 사용하지 않습니다.
     */
    private LocalDate deliveryDate;

    private LocalDate fromDate;
    private LocalDate toDate;

    /**
     * 현재 화면 DOM 순서 그대로 들어오는 주문 ID 목록입니다.
     */
    private List<Long> orderedOrderIds;
}