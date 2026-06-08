package com.dev.HiddenBATHAuto.dto.task;

import java.time.LocalDateTime;
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
@NoArgsConstructor
@AllArgsConstructor
@Builder
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

    private String productName;
    private int quantity;
    private int productCost;
    private int supplyPrice;
    private int vatPrice;
    private int totalAmount;
    private int packingCost;
    private int deliveryCost;
    private String productSummary;
    private Map<String, String> optionMap;

    private String zipCode;
    private String doName;
    private String siName;
    private String guName;
    private String roadAddress;
    private String detailAddress;
    private String fullAddress;

    private String siteZipCode;
    private String siteDoName;
    private String siteSiName;
    private String siteGuName;
    private String siteRoadAddress;
    private String siteDetailAddress;
    private String siteFullAddress;
    private boolean siteDelivery;

    private String ordererName;
    private String ordererPhone;
    private String ordererSummary;

    private String orderComment;
    private String adminMemo;
    private String dispatchCompleteMessage;
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

    private boolean checked;
    private boolean latestChecked;
    private boolean revisedAfterCheck;
    private boolean needProductionCheck;
    private OrderCheckState checkState;
    private String checkStateName;
    private String checkStateLabel;

    private String checkedByUsername;
    private LocalDateTime checkedAt;

    private String revisionMarkedByUsername;
    private LocalDateTime revisionMarkedAt;
    private String revisionReason;
    private int revisionCount;

    private List<NonStandardTaskListOrderImageDto> adminImages;
}
