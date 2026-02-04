package com.dev.HiddenBATHAuto.dto.client;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CompanyListRowDto {
    private Long id;
    private String companyName;
    private String representativeName; // 대표자명 (없으면 null/빈값)
    private LocalDateTime createdAt;
    private String salesManagerName;   // 담당영업사원명 (없으면 null)
    private long memberCount;          // 직원수
}