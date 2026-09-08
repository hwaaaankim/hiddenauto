package com.dev.HiddenBATHAuto.dto.client;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 대리점 목록에서 멤버 검색 조건과 일치한 실제 필드 정보를 표시하기 위한 DTO입니다.
 */
@Getter
@AllArgsConstructor
public class CompanyMemberSearchMatchDto {

    private Long companyId;
    private Long memberId;
    private String memberName;
    private String username;
    private String fieldLabel;
    private String matchedValue;
}
