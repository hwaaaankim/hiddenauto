package com.dev.HiddenBATHAuto.dto.as;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarEventDto {
    private Long taskId;
    private String title; // 업체명만
    private LocalDate date; // scheduled_date
    private String status; // ✅ 추가: REQUESTED/IN_PROGRESS/...
}