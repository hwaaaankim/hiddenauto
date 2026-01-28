package com.dev.HiddenBATHAuto.dto.as;

import java.time.LocalDate;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ScheduleMoveRequest {
    private Long taskId;
    private LocalDate scheduledDate;
}