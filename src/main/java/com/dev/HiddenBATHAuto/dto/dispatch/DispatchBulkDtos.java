package com.dev.HiddenBATHAuto.dto.dispatch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.dto.dispatch.DispatchDtos.DispatchOrderRowDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class DispatchBulkDtos {

    private DispatchBulkDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkHandlerChangePreviewRequest {
        @Builder.Default
        private List<Long> orderIds = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkHandlerChangePreviewResponse {
        private int requestedCount;
        private int changeableCount;
        private int excludedCount;
        private int blockingCount;
        private boolean exclusionConfirmationRequired;

        @Builder.Default
        private List<BulkOrderInfoDto> changeableOrders = new ArrayList<>();

        @Builder.Default
        private List<BulkOrderInfoDto> excludedOrders = new ArrayList<>();

        @Builder.Default
        private List<BulkOrderInfoDto> blockingOrders = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkHandlerChangeRequest {
        @Builder.Default
        private List<Long> orderIds = new ArrayList<>();

        private Long deliveryHandlerId;
        private boolean excludeUnavailable;

        /** 미리보기에서 사용자가 제외 진행을 확인한 주문 ID */
        @Builder.Default
        private List<Long> acknowledgedExcludedOrderIds = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkHandlerChangeResponse {
        private int requestedCount;
        private int updatedCount;
        private int excludedCount;

        @Builder.Default
        private List<Long> updatedOrderIds = new ArrayList<>();

        @Builder.Default
        private List<Long> excludedOrderIds = new ArrayList<>();

        @Builder.Default
        private List<DispatchOrderRowDto> updatedRows = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkDeliveryMethodPreviewRequest {
        @Builder.Default
        private List<Long> orderIds = new ArrayList<>();

        private Long deliveryMethodId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkDeliveryMethodPreviewResponse {
        private int requestedCount;
        private int assignmentRequiredCount;
        private int assignmentRemovalCount;
        private int preservedAssignmentCount;
        private int blockingCount;
        private boolean assignmentRemovalConfirmationRequired;
        private DeliveryMethodOptionDto targetMethod;

        @Builder.Default
        private List<BulkOrderInfoDto> assignmentRequiredOrders = new ArrayList<>();

        @Builder.Default
        private List<BulkOrderInfoDto> assignmentRemovalOrders = new ArrayList<>();

        @Builder.Default
        private List<BulkOrderInfoDto> preservedAssignmentOrders = new ArrayList<>();

        @Builder.Default
        private List<BulkOrderInfoDto> blockingOrders = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkDeliveryMethodChangeRequest {
        @Builder.Default
        private List<Long> orderIds = new ArrayList<>();

        private Long deliveryMethodId;

        @Builder.Default
        private List<OrderHandlerAssignmentDto> assignments = new ArrayList<>();

        private boolean confirmAssignmentRemoval;

        /** 미리보기에서 사용자가 배정업무 삭제를 확인한 주문 ID */
        @Builder.Default
        private List<Long> acknowledgedAssignmentRemovalOrderIds = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkDeliveryMethodChangeResponse {
        private int requestedCount;
        private int updatedCount;
        private int assignmentCreatedCount;
        private int assignmentRemovedCount;
        private int assignmentPreservedCount;

        @Builder.Default
        private List<Long> updatedOrderIds = new ArrayList<>();

        @Builder.Default
        private List<DispatchOrderRowDto> updatedRows = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderHandlerAssignmentDto {
        private Long orderId;
        private Long deliveryHandlerId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeliveryMethodOptionDto {
        private Long id;
        private String methodName;
        private String methodGroup;
        private boolean handlerRequired;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BulkOrderInfoDto {
        private Long orderId;
        private String companyName;
        private String productName;
        private String deliveryMethodName;
        private Long deliveryHandlerId;
        private String deliveryHandlerName;
        private LocalDate deliveryDate;
        private String reason;
    }
}
