package com.dev.HiddenBATHAuto.service.auth;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.auth.Member;

/**
 * 본인정보와 직원관리 화면이 동일한 비밀번호 변경 규칙을 사용하도록 합니다.
 */
@Service
public class MemberPasswordService {

	private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

	private final PasswordEncoder passwordEncoder;

	public MemberPasswordService(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
	}

	/**
	 * 두 입력값이 모두 비어 있으면 기존 비밀번호를 유지합니다.
	 * 하나라도 입력된 경우에는 두 값의 존재 여부와 일치 여부를 검증한 뒤에만
	 * BCrypt 해시로 교체합니다.
	 *
	 * @return 비밀번호가 실제로 변경되었으면 true
	 */
	public boolean updatePasswordIfRequested(Member member, String password, String passwordConfirm) {
		if (member == null) {
			throw new IllegalArgumentException("회원 정보를 확인할 수 없습니다.");
		}

		boolean passwordEntered = hasInput(password);
		boolean confirmationEntered = hasInput(passwordConfirm);

		if (!passwordEntered && !confirmationEntered) {
			return false;
		}

		if (!passwordEntered || !confirmationEntered) {
			throw new IllegalArgumentException("새 비밀번호와 비밀번호 확인을 모두 입력해 주세요.");
		}

		if (password.isBlank()) {
			throw new IllegalArgumentException("비밀번호는 공백으로만 설정할 수 없습니다.");
		}

		if (!Objects.equals(password, passwordConfirm)) {
			throw new IllegalArgumentException("새 비밀번호와 비밀번호 확인이 일치하지 않습니다.");
		}

		if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
			throw new IllegalArgumentException("비밀번호는 UTF-8 기준 72바이트 이하로 입력해 주세요.");
		}

		member.setPassword(passwordEncoder.encode(password));
		return true;
	}

	private boolean hasInput(String value) {
		return value != null && !value.isEmpty();
	}
}
