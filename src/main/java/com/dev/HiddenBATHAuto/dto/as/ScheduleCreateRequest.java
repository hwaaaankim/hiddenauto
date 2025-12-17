package com.dev.HiddenBATHAuto.dto.as;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleCreateRequest {
	private Long taskId;
	private LocalDate scheduledDate;
}
