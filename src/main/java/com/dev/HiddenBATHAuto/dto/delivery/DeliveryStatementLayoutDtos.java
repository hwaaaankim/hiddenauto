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
        /** HORIZONTAL / VERTICAL */
        private String layoutType;

        /** SITE / PARCEL */
        private String statementType;

        private List<Long> orderIds;

        /**
         * 출고팀 명세서 담당자 정보 출력 옵션입니다.
         * 기존 호출부와의 하위 호환을 위해 null은 true(출력)로 처리합니다.
         */
        private Boolean showDeliveryTeamContactName;
        private Boolean showDeliveryTeamContactPhone;
        private Boolean showDispatchTeamContactName;
        private Boolean showDispatchTeamContactPhone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LayoutResponse {
        private String layoutType;
        private String statementType;
        private String statementTypeLabel;
        private String generatedDateText;
        private int requestedOrderCount;
        private int includedOrderCount;
        private int excludedOrderCount;
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
        private boolean lastPage;

        private Long taskId;
        private String documentType;
        private String documentTypeLabel;

        private String companyName;
        private String orderIdsText;
        private String dateLabel;
        private String dateText;
        private String recipientName;
        private String recipientPhone;
        private String postalCode;
        private String addressText;
        private String deliveryMethodName;

        /**
         * 명세서에 표시할 실제 작업 담당자입니다.
         * 현장배송/직배송은 배송팀 담당자, 그 외 명세서 배송수단은 기존 출고팀 담당자를 사용합니다.
         */
        private String deliveryContactName;
        private String deliveryContactPhone;

        /** 택배명세서 수기 작성란. 현재는 공란으로 전달합니다. */
        private String trackingNumber;
        private String freightType;
        private String packingMethod;

        /** 택배명세서 담당자 표기(하위 호환 필드) */
        private String managerName;

        private String acceptanceText;
        private String signatureText;

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
