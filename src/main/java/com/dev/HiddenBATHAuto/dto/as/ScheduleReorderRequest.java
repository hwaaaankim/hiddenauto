package com.dev.HiddenBATHAuto.dto.as;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleReorderRequest {
	private LocalDate scheduledDate;
	private java.util.List<Long> taskIdsInOrder; // 모달에서 정렬 후 taskId 순서대로
}