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

    /**
     * 거울 재단 상품 여부
     * Order.mirrorCuttingProduct 값을 관리자 오더 리스트 넓게보기 수정폼에서 표시/수정하기 위한 필드입니다.
     */
    private boolean mirrorCuttingProduct;

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

    /**
     * 제품명까지 포함된 전체 제품정보입니다.
     * 일괄보기, 출력 등 기존 화면 호환을 위해 유지합니다.
     */
    private String productSummary;

    /**
     * 관리자 발주 목록에서 굵은 제품명 아래에 표시할 상세정보입니다.
     * 제품명은 제외하고 제품코드/시리즈/사이즈/색상/수량만 포함합니다.
     */
    private String productDetailSummary;

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