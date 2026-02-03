package com.dev.HiddenBATHAuto.service;

import java.security.SecureRandom;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dev.HiddenBATHAuto.model.auth.Member;
import com.dev.HiddenBATHAuto.repository.auth.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MemberAdminService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final SMSService smsService;

	private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
	private static final int TEMP_PASSWORD_LEN = 8;

	@Transactional
	public void resetPasswordAndSendSms(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new IllegalArgumentException("해당 멤버가 존재하지 않습니다. ID=" + memberId));

		if (member.getPhone() == null || member.getPhone().trim().isEmpty()) {
			throw new IllegalStateException("해당 멤버의 휴대폰번호가 없습니다. 비밀번호 초기화 문자를 보낼 수 없습니다.");
		}

		String rawTempPassword = generateTempPassword(TEMP_PASSWORD_LEN);
		String encoded = passwordEncoder.encode(rawTempPassword);

		member.setPassword(encoded);
		memberRepository.save(member);

		// SMS 발송: 아이디 + 초기화 비밀번호
		String message = buildSmsMessage(member.getUsername(), rawTempPassword);

		try {
			smsService.sendMessage(member.getPhone(), message);
		} catch (Exception e) {
			// 비밀번호는 이미 바뀐 상태이므로, 실패를 알려서 재시도/대체 안내 가능하게 합니다.
			throw new IllegalStateException("비밀번호는 초기화되었으나 SMS 발송에 실패했습니다. 사유: " + e.getMessage(), e);
		}
	}

	@Transactional
	public void disableMember(Long memberId) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new IllegalArgumentException("해당 멤버가 존재하지 않습니다. ID=" + memberId));

		member.setEnabled(false);
		memberRepository.save(member);
	}

	private String generateTempPassword(int len) {
		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder(len);
		for (int i = 0; i < len; i++) {
			int idx = random.nextInt(CHARS.length());
			sb.append(CHARS.charAt(idx));
		}
		return sb.toString();
	}

	private String buildSmsMessage(String username, String tempPassword) {
		return "[HiddenBATH]\n"
				+ "비밀번호가 초기화되었습니다.\n"
				+ "아이디: " + (username == null ? "-" : username) + "\n"
				+ "임시비밀번호: " + tempPassword + "\n"
				+ "로그인 후 비밀번호를 변경해주세요.";
	}
}