package com.dev.HiddenBATHAuto.dto.calendar;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class CalendarTaskDetailDTO {

    private String type;   // "AS" or "TASK"
    private Long id;

    private String title;  // TASK: "TASK_{id}" 등
    private String date;   // 달력 기준(basis)에 따른 날짜(yyyy-MM-dd)
    private String address;

    // ✅ 기존: 방문예정일(스케줄러 등록일)
    private String scheduledDate; // yyyy-MM-dd

    // ✅ 기존: 현 담당자 정보
    private String handlerName;
    private String handlerContact;

    // =========================================================
    // ✅ AS 모달 표기를 위한 추가 필드
    // - 고객 정보는 "회원(Member)"이 아니라 "AS 신청 시 입력한 값" 기준
    // =========================================================
    private String customerName;   // 고객 성함(AS 신청 시 입력)
    private String onsiteContact;  // 현장 연락처(AS 신청 시 입력)
    private String requestedAt;    // 신청일(yyyy-MM-dd HH:mm)

    private String productName;
    private String productSize;
    private String productColor;
    private String productOptions;

    private String symptom;        // AS 증상 = subject

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