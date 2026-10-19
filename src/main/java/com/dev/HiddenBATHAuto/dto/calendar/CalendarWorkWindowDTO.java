package com.dev.HiddenBATHAuto.dto.calendar;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * index 메인 상단의 최근/예정 7일 업무 흐름 응답 DTO.
 */
@Data
@NoArgsConstructor
public class CalendarWorkWindowDTO {

    /** 서버 기준 오늘 날짜 */
    private String today;

    /** 오늘 포함 최근 7일 */
    private String recentStartDate;
    private String recentEndDate;

    /** 오늘 포함 앞으로 7일 */
    private String upcomingStartDate;
    private String upcomingEndDate;

    private long recentCompletedCount;
    private long recentOrderCount;
    private long recentAsCount;

    private long upcomingCount;
    private long upcomingOrderCount;
    private long upcomingAsCount;

    private List<WorkItemDTO> recentCompleted = new ArrayList<>();
    private List<WorkItemDTO> upcoming = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkItemDTO {
        /** ORDER / AS */
        private String type;

        /** ORDER.id 또는 AsTask.id */
        private Long id;

        /** ORDER인 경우 상위 Task.id, AS는 null */
        private Long taskId;

        /** yyyy-MM-dd */
        private String date;

        /** yyyy-MM-dd HH:mm, 시간이 없는 스케줄은 yyyy-MM-dd */
        private String dateTime;

        private String title;
        private String description;
        private String region;

        private String statusKey;
        private String statusLabel;

        private long amount;

        /**
         * ORDER: Order.ordererName / Order.ordererPhone
         * AS: AsTask.customerName / AsTask.onsiteContact
         */
        private String contactName;
        private String contactPhone;
        private List<com.dev.HiddenBATHAuto.dto.customer.CustomerOrderChange> changes = new ArrayList<>();
        public WorkItemDTO(String type, Long id, Long taskId, String date, String dateTime,
                String title, String description, String region, String statusKey, String statusLabel,
                long amount, String contactName, String contactPhone) {
            this(type, id, taskId, date, dateTime, title, description, region, statusKey, statusLabel,
                amount, contactName, contactPhone, new ArrayList<>());
        }

    }
}
