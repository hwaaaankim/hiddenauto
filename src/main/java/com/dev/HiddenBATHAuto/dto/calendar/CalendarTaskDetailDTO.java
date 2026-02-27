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

    // ✅ 추가: 방문예정일(스케줄러 등록일)
    private String scheduledDate; // yyyy-MM-dd

    // ✅ 추가: 현 담당자 정보
    private String handlerName;
    private String handlerContact;

    private List<OrderBriefDTO> orders = new ArrayList<>();

    @Data
    public static class OrderBriefDTO {
        private Long orderId;
        private String createdAt;             // yyyy-MM-dd HH:mm
        private String preferredDeliveryDate; // yyyy-MM-dd HH:mm
        private String address;
        private Integer quantity;
        private Integer price;
        private String categoryName;
    }
}