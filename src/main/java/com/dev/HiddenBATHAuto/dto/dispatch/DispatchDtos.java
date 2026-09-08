package com.dev.HiddenBATHAuto.dto.dispatch;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

public class DispatchDtos {

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DispatchOrderSearchRequest {

        /**
         * COMPANY_NAME
         * MEMBER_NAME
         * MEMBER_USERNAME
         * MEMBER_PHONE
         * MEMBER_EMAIL
         */
        private String keywordType;

        private String keyword;

        private Long productCategoryId;

        /**
         * ALL
         * STANDARD
         * NON_STANDARD
         */
        private String standard = "ALL";

        /**
         * ALL
         * CONFIRMED
         * PRODUCTION_DONE
         * DISPATCH_DONE
         */
        private String status = "ALL";

        private String doName;
        private String siName;
        private String guName;

        /**
         * 출고일 기준.
         * Order.preferredDeliveryDate 날짜와 비교합니다.
         */
        private LocalDate orderDate;

        /** Order.id 범위: From만 있으면 이상, To만 있으면 이하, 같은 값이면 단건 */
        private Long orderIdFrom;
        private Long orderIdTo;

        private Long deliveryMethodId;

        /**
         * 실제 배송수단 ID로 표현할 수 없는 조회 범위입니다.
         * BUSAN_VISIT: 배송수단이 방문이면서 일반 도로명주소가 부산이거나 지정 거래처인 주문
         */
        private String deliveryMethodScope;

        private Integer size = 50;

        private Integer lastStatusSort;

        private Long lastOrderId;

        private List<Long> loadedOrderIds = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class BulkDispatchCompleteRequest {
        private List<Long> orderIds = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class UpdateDeliveryMethodRequest {
        private Long deliveryMethodId;

        /**
         * 새 배송수단이 직배송일 때만 필수입니다.
         */
        private Long deliveryHandlerId;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatchOrderSearchResponse {

        @Builder.Default
        private List<DispatchOrderRowDto> orders = new ArrayList<>();

        private boolean hasNext;

        private Integer nextLastStatusSort;
        private Long nextLastOrderId;

        private int requestedSize;
        private int returnedSize;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DispatchOrderRowDto {

        private Long orderId;

        private String status;
        private String statusLabel;
        private Integer statusSort;

        private boolean dispatchCompletable;

        private boolean standard;
        private String standardLabel;

        private Long productCategoryId;
        private String productCategoryName;

        private String companyName;

        private String memberName;
        private String memberUsername;
        private String memberPhone;
        private String memberEmail;

        private String productName;
        private String color;
        private String sizeText;

        private int quantity;

        private String adminMemo;

        private Long deliveryMethodId;
        private String deliveryMethodName;

        private Long deliveryHandlerId;
        private String deliveryHandlerName;
        private Integer deliveryOrderIndex;

        private String doName;
        private String siName;
        private String guName;
        private String zipCode;
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

        private String ordererName;
        private String ordererPhone;
        private int deliveryCost;
        private String preferredDeliveryDateText;

        private String createdAtText;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkDispatchCompleteResponse {

        @Builder.Default
        private List<Long> updatedOrderIds = new ArrayList<>();

        @Builder.Default
        private List<DispatchOrderRowDto> updatedRows = new ArrayList<>();

        @Builder.Default
        private List<BulkDispatchFailDto> failedItems = new ArrayList<>();

        private int requestedCount;
        private int updatedCount;
        private int failedCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkDispatchFailDto {
        private Long orderId;
        private String message;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryMethodDto {
        private Long id;
        private String methodName;
        private int methodPrice;

        private boolean directDelivery;

        private Long deliveryHandlerId;
        private String deliveryHandlerName;
        private Integer deliveryOrderIndex;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionOptionDto {
        private Long id;
        private String name;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProvinceChildrenResponse {

        @Builder.Default
        private List<RegionOptionDto> cities = new ArrayList<>();

        @Builder.Default
        private List<RegionOptionDto> districts = new ArrayList<>();
    }
}
