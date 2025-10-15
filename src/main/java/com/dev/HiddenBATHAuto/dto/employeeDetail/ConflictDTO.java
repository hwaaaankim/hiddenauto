package com.dev.HiddenBATHAuto.dto.employeeDetail;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConflictDTO {
	private Long conflictMemberId;
	private String conflictMemberName;
	private String conflictPath; // 예: "서울특별시", "경기도 용인시", "경기도 용인시 수지구"
}
