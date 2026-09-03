package com.dev.HiddenBATHAuto.service.auth;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.dto.profile.InternalProfileResponse;
import com.dev.HiddenBATHAuto.dto.profile.InternalProfileUpdateRequest;
import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

@Service
public class InternalProfileService {

	private static final int MAX_MEMBER_FIELD_LENGTH = 255;

	private final MemberRepository memberRepository;
	private final MemberPasswordService memberPasswordService;

	public InternalProfileService(
			MemberRepository memberRepository,
			MemberPasswordService memberPasswordService
	) {
		this.memberRepository = Objects.requireNonNull(memberRepository, "memberRepository");
		this.memberPasswordService = Objects.requireNonNull(memberPasswordService, "memberPasswordService");
	}

	@Transactional(readOnly = true)
	public InternalProfileResponse getProfile(Long authenticatedMemberId) {
		return toResponse(findMember(authenticatedMemberId));
	}

	@Transactional
	public InternalProfileResponse updateProfile(
			Long authenticatedMemberId,
			InternalProfileUpdateRequest request
	) {
		if (request == null) {
			throw new IllegalArgumentException("변경할 개인정보가 없습니다.");
		}

		Member member = findMember(authenticatedMemberId);
		String name = requiredText(request.name(), "이름");
		String phone = optionalText(request.phone(), "휴대전화");
		String telephone = optionalText(request.telephone(), "유선전화");
		String email = optionalText(request.email(), "이메일");

		// username, role, team, teamCategory, MemberRegion은 변경하지 않습니다.
		member.setName(name);
		member.setPhone(phone);
		member.setTelephone(telephone);
		member.setEmail(email);
		memberPasswordService.updatePasswordIfRequested(
				member,
				request.password(),
				request.passwordConfirm()
		);
		member.setUpdatedAt(LocalDateTime.now());

		Member saved = memberRepository.save(member);
		return toResponse(saved);
	}

	private Member findMember(Long authenticatedMemberId) {
		if (authenticatedMemberId == null) {
			throw new IllegalArgumentException("로그인 사용자 정보를 확인할 수 없습니다.");
		}

		return memberRepository.findById(authenticatedMemberId)
				.orElseThrow(() -> new IllegalArgumentException("로그인한 회원 정보를 찾을 수 없습니다."));
	}

	private String requiredText(String value, String fieldName) {
		String normalized = optionalText(value, fieldName);
		if (normalized == null) {
			throw new IllegalArgumentException(fieldName + " 항목은 필수입니다.");
		}
		return normalized;
	}

	private String optionalText(String value, String fieldName) {
		if (value == null) {
			return null;
		}

		String normalized = value.trim();
		if (normalized.isEmpty()) {
			return null;
		}

		if (normalized.length() > MAX_MEMBER_FIELD_LENGTH) {
			throw new IllegalArgumentException(fieldName + " 항목은 255자 이하로 입력해 주세요.");
		}

		return normalized;
	}

	private InternalProfileResponse toResponse(Member member) {
		return new InternalProfileResponse(
				member.getUsername(),
				member.getName(),
				member.getPhone(),
				member.getTelephone(),
				member.getEmail()
		);
	}
}
