package com.dev.HiddenBATHAuto.dto.profile;

/**
 * 관리자 화면 공통 헤더의 내정보 팝업에 노출할 개인정보입니다.
 *
 * 비밀번호, 권한, 팀, 팀 카테고리, 담당구역은 응답에 포함하지 않습니다.
 */
public record InternalProfileResponse(
		String username,
		String name,
		String phone,
		String telephone,
		String email
) {
}
