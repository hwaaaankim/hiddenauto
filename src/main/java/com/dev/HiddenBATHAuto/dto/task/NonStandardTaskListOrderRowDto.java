package com.dev.HiddenBATHAuto.dto.task;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dev.HiddenBATHAuto.enums.order.OrderCheckState;
import com.dev.HiddenBATHAuto.model.task.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NonStandardTaskListOrderRowDto {

    private Long orderId;
    private Long taskId;

    private Long companyId;
    private String companyName;
    private String representativeName;

    private Long requesterMemberId;
    private String requesterName;

    private boolean standard;
    private String standardLabel;

    private Long productCategoryId;
    private String productCategoryName;
    
    private String dispatchCompleteMessage;

    private String productName;
    private int quantity;
    private int productCost;
    private int supplyPrice;
    private int vatPrice;
    private int totalAmount;
    private int packingCost;
    private int deliveryCost;
    private String productSummary;

    @Builder.Default
    private Map<String, String> optionMap = new LinkedHashMap<>();

    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;
    private String fullAddress;

    private String ordererName;
    private String ordererPhone;
    private String ordererSummary;

    private String orderComment;
    private String adminMemo;
    private String noteSummary;

    private LocalDateTime createdAt;
    private LocalDateTime preferredDeliveryDate;

    private Long deliveryMethodId;
    private String deliveryMethodName;

    private Long assignedDeliveryHandlerId;
    private String assignedDeliveryHandlerName;

    private OrderStatus status;
    private String statusName;
    private String statusLabel;

    /*
     * 생산팀 확인 상태 관련
     *
     * checked:
     * - 기존 호환용 필드입니다.
     * - true이면 현재 최신 상태가 CHECKED, 즉 생산팀이 확인완료한 상태입니다.
     * - 재수정 표시용으로 사용하면 안 됩니다.
     *
     * revisedAfterCheck:
     * - 생산팀이 한번 CHECKED 처리한 뒤,
     *   관리자가 생산팀이 봐야 하는 항목을 다시 수정한 상태입니다.
     * - 목록에서 강조 표시해야 하는 기준은 이 필드입니다.
     *
     * needProductionCheck:
     * - 생산팀이 확인해야 하는 상태입니다.
     * - UNCHECKED 또는 REVISED_AFTER_CHECK이면 true입니다.
     */
    private boolean checked;
    private boolean revisedAfterCheck;
    private boolean latestChecked;
    private boolean needProductionCheck;

    @Builder.Default
    private OrderCheckState checkState = OrderCheckState.UNCHECKED;

    private String checkStateName;
    private String checkStateLabel;

    private String checkedByUsername;
    private LocalDateTime checkedAt;

    private String revisionMarkedByUsername;
    private LocalDateTime revisionMarkedAt;
    private String revisionReason;
    private int revisionCount;

    @Builder.Default
    private List<NonStandardTaskListOrderImageDto> adminImages = List.of();
}