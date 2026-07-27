package com.dev.HiddenBATHAuto.dto.delivery;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class DeliveryStatementLayoutDtos {

    private DeliveryStatementLayoutDtos() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayoutRequest {
        private String layoutType;
        private List<Long> orderIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayoutResponse {
        private String layoutType;
        private String generatedDateText;
        private List<StatementPageDto> pages;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatementPageDto {
        private int sequence;
        private int pageNumber;
        private int pageCount;

        private Long taskId;
        private String documentType;
        private String documentTypeLabel;

        private String companyName;
        private String requesterName;
        private String managedByName;
        private String orderIdsText;
        private String orderDateText;
        private String deliveryDateText;

        private String recipientLabel;
        private String recipientName;
        private String contactLabel;
        private String recipientPhone;
        private String addressLabel;
        private String postalCode;
        private String addressText;

        private String deliveryMethodName;
        private String deliveryHandlerName;
        private String auxiliaryLabel;
        private String auxiliaryValue;

        private long totalQuantity;
        private long packingCost;
        private long deliveryCost;
        private long totalAmount;
        private String noteText;

        private List<StatementItemDto> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatementItemDto {
        private int no;
        private Long orderId;
        private String productName;
        private String sizeText;
        private String color;
        private int quantity;
        private String memo;
    }
}
