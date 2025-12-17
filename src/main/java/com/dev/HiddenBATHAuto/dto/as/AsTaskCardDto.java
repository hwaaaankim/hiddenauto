package com.dev.HiddenBATHAuto.dto.as;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
public class AsTaskCardDto {
	private Long taskId;
	private String companyName;
	private LocalDateTime requestedAt;
	private LocalDateTime asProcessDate;
	private String address; // do/si/gu + road + detail
	private String status; // REQUESTED/IN_PROGRESS/COMPLETED/CANCELED
	private LocalDate scheduledDate; // 등록된 날짜(없으면 null)
}
