package com.dev.HiddenBATHAuto.dto.task;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private boolean checked;
    private String checkedByUsername;
    private LocalDateTime checkedAt;

    @Builder.Default
    private List<NonStandardTaskListOrderImageDto> adminImages = List.of();
}
