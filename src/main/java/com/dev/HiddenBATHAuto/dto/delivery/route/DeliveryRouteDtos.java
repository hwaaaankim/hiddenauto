package com.dev.HiddenBATHAuto.dto.delivery.route;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public final class DeliveryRouteDtos {

    private DeliveryRouteDtos() {
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Page {
        private LocalDate deliveryDate;
        private Long handlerId;
        private String handlerName;
        private List<Group> directGroups;
        private List<Group> freightGroups;
        private int directGroupCount;
        private int freightGroupCount;
        private int totalGroupCount;
        private int directOrderCount;
        private int freightOrderCount;
        private int totalOrderCount;
        private int deliveryDoneCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Group {
        private String domId;
        private String section;
        private int sequence;
        private int firstOrderIndex;
        private String deliveryMethodName;
        private String companyName;
        private String address;
        private String zipCode;
        private String primaryContact;
        private List<OrderRow> orders;
        private int orderCount;
        private int completableOrderCount;
        private int totalQuantity;
        private int deliveryDoneCount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderRow {
        private Long orderId;
        private Long taskId;
        private int orderIndex;
        private String status;
        private String statusLabel;
        private String deliveryMethodName;
        private String address;
        private String ordererName;
        private String ordererPhone;
        private String category;
        private String productName;
        private String size;
        private String color;
        private int quantity;
        private String quantityText;
        private String adminMemo;
        private String orderComment;
        private String preferredDeliveryDateText;
        private boolean deliveryDone;
        private boolean completable;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrintRow {
        private Long orderId;
        private int orderIndex;
        private String companyName;
        private String deliveryMethodName;
        private String statusLabel;
        private String address;
        private String ordererName;
        private String ordererPhone;
        private String category;
        private String productName;
        private String size;
        private String color;
        private String quantityText;
        private String adminMemo;
    }
}
