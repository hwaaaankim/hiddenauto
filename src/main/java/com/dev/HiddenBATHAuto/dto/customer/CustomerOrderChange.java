package com.dev.HiddenBATHAuto.dto.customer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** 고객 공개용 허용 항목만 포함합니다. 내부 감사 DTO/직원명/작업 메모는 전달하지 않습니다. */
@Getter @Builder
public class CustomerOrderChange {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.dev.HiddenBATHAuto.model.task.audit.CustomerOrderSearchSnapshot beforeSearch;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private com.dev.HiddenBATHAuto.model.task.audit.CustomerOrderSearchSnapshot currentSearch;
    public String getCompactChange() {
        return switch(field == null ? "" : field) {
            case "preferredDeliveryDate" -> "배송일 " + beforeValue + " → " + afterValue;
            case "deliveryAddress", "siteAddress" -> "주소 변경";
            case "deliveryMethod" -> "배송수단 변경";
            case "deleted" -> "삭제";
            default -> "배송정보 변경";
        };
    }
    private Long taskId;
    private Long orderId;
    private String productName;
    private String field;
    private String label;
    private String beforeValue;
    private String afterValue;
    private LocalDateTime changedAt;
    private LocalDate previousDate;
    private LocalDate currentDate;
    private LocalDate requestedDate;
    private boolean deleted;
    private boolean taskAvailable;
    public String getDescription() {
        String time = changedAt == null ? "" : changedAt.toLocalDate() + " · ";
        return time + "Order #" + orderId + " " + (productName == null ? "" : productName + " · ")
                + ("deleted".equals(field) ? "발주가 삭제되었습니다." : label + " 변경: " + beforeValue + " → " + afterValue);
    }
}
