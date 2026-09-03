package com.dev.HiddenBATHAuto.dto.profile;

/**
 * 로그인한 내부 사용자가 직접 변경할 수 있는 개인정보만 받습니다.
 *
 * username/memberId/role/team/teamCategory/MemberRegion을 요청 항목에서
 * 제외하여 클라이언트 요청만으로 조직 정보를 변경할 수 없도록 합니다.
 */
public record InternalProfileUpdateRequest(
		String name,
		String phone,
		String telephone,
		String email,
		String password,
		String passwordConfirm
) {
}
