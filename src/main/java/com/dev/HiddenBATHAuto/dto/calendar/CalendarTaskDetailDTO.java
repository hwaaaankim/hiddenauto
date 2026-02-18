package com.dev.HiddenBATHAuto.dto.calendar;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class CalendarTaskDetailDTO {

    private String type;   // "AS" or "TASK"
    private Long id;

    private String title;  // AS: subject or productName 등
    private String date;   // 기준에 따른 날짜(yyyy-MM-dd)
    private String address;

    private List<OrderBriefDTO> orders = new ArrayList<>();

    @Data
    public static class OrderBriefDTO {
        private Long orderId;
        private String createdAt;            // yyyy-MM-dd HH:mm (원하시면 포맷 수정 가능)
        private String preferredDeliveryDate; // yyyy-MM-dd HH:mm or yyyy-MM-dd (원하시면 통일)
        private String address;
        private Integer quantity;
        private Integer price;
        private String categoryName;
    }
}
