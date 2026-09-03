package com.dev.HiddenBATHAuto.dto.customer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.dev.HiddenBATHAuto.model.task.AsTask;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.Task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;

public final class CustomerPageDtos {

    private CustomerPageDtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AsListFilter {
        @Builder.Default
        private int page = 0;

        @Builder.Default
        private int size = 30;

        private String textType;
        private String keyword;
        private String dateType;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate endDate;
        private String billingType;
        private String status;
        private Long provinceId;
        private Long cityId;
        private Long districtId;
        private String sort;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskListFilter {
        @Builder.Default
        private int page = 0;

        @Builder.Default
        private int size = 30;

        private String textType;
        private String keyword;
        private String dateType;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate endDate;
        private String status;
        private String category;
        private Long provinceId;
        private Long cityId;
        private Long districtId;
        private String sort;
    }

    @Getter
    @Builder
    public static class AsListRow {
        private AsTask asTask;
        private LocalDate scheduledDate;
        private String handlerName;
        private String handlerContact;
        private String productInfo;
        private boolean hasImages;
        private boolean hasVideos;
    }

    @Getter
    @Builder
    public static class TaskListRow {
        private Task task;
        private Order representativeOrder;
        private String ordererName;
        private String ordererPhone;
        private int orderCount;

        @Builder.Default
        private List<CategoryCount> categoryCounts = new ArrayList<>();

        /**
         * 고객 발주 목록의 제품 요약/펼침 상세에서 공통으로 사용하는 오더별 표시 데이터입니다.
         * 엔티티를 화면에서 직접 다시 해석하지 않고 한 번 계산한 값을 재사용하여
         * 제품명/규격/색상/수량이 목록과 펼침 상세에서 서로 다르게 보이는 것을 방지합니다.
         */
        @Builder.Default
        private List<TaskOrderSummary> orderSummaries = new ArrayList<>();

        private String deliveryMethodName;

        /** 상세/엑셀 등 기존 화면 호환을 위한 전체 주소 */
        private String deliveryAddress;

        /** 목록 전용 축약 주소: 서울 송파구 / 부산 북구 / 경기 화성시 형태 */
        private String deliveryRegion;

        private LocalDateTime deliveryDate;
        private String statusKey;
        private String statusLabel;
        private String managerName;

        /** Task에 포함된 모든 Order.supplyPrice의 합계 */
        private long supplyPrice;

        /** supplyPrice 합계에 VAT 10%를 가산한 금액 */
        private long vatIncludedTotalPrice;
    }

    @Getter
    @Builder
    public static class TaskOrderSummary {
        private Long orderId;
        private String categoryName;
        private String productName;
        private String size;
        private String color;
        private int quantity;
        private String deliveryMethodName;
        private String deliveryAddress;
        private LocalDateTime deliveryDate;
        private String statusKey;
        private String statusLabel;
        private String orderComment;
        private String adminMemo;
    }

    @Getter
    @AllArgsConstructor
    public static class CategoryCount {
        private String name;
        private long count;
    }

    @Getter
    @AllArgsConstructor
    public static class SortSpec {
        private String field;
        private String direction;

        public boolean isAscending() {
            return "asc".equals(direction);
        }
    }
}
