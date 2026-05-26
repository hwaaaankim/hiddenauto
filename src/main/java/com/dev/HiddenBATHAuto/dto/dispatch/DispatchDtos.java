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

        private String doName;
        private String siName;
        private String guName;

        /**
         * 발주일 기준.
         * Order.createdAt 날짜와 비교합니다.
         */
        private LocalDate orderDate;

        private Long deliveryMethodId;

        private Integer size = 50;

        /**
         * 무한스크롤 커서.
         * 상태 정렬값:
         * CONFIRMED = 1
         * PRODUCTION_DONE = 2
         * DISPATCH_DONE = 3
         */
        private Integer lastStatusSort;

        /**
         * 같은 상태 정렬값 안에서 id desc 기준 커서.
         */
        private Long lastOrderId;

        /**
         * 이미 화면에 로드된 주문 ID.
         * 완료 처리 후 상태가 DISPATCH_DONE으로 바뀌어도
         * 다음 조회에서 중복으로 다시 내려오지 않게 막기 위함입니다.
         */
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

        private String doName;
        private String siName;
        private String guName;
        private String roadAddress;
        private String detailAddress;
        private String fullAddress;

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