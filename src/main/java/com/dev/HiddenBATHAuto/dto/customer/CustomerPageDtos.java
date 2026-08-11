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

        private String deliveryMethodName;
        private String deliveryAddress;
        private LocalDateTime deliveryDate;
        private String statusKey;
        private String statusLabel;
        private String managerName;
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
