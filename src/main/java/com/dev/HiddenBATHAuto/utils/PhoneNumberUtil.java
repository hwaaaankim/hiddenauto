package com.dev.HiddenBATHAuto.utils;

import java.util.Optional;

public final class PhoneNumberUtil {

    private PhoneNumberUtil() {}

    /**
     * 입력값에서 숫자만 추출하여 정규화합니다.
     * - 010-1234-1234 / 01012341234 => 01012341234
     * - +82 10-1234-1234 / 821012341234 => 01012341234 (가능한 경우)
     *
     * @return 정규화된 번호(숫자만) Optional, 유효하지 않으면 Optional.empty()
     */
    public static Optional<String> normalizeKoreanMobile(String raw) {
        if (raw == null) return Optional.empty();

        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return Optional.empty();

        // +82 / 82 로 시작하는 경우 국내형으로 변환 시도
        // 예: 821012341234 -> 01012341234
        if (digits.startsWith("82")) {
            String rest = digits.substring(2);
            if (rest.startsWith("10")) {
                digits = "0" + rest; // 010...
            }
        }

        // 대한민국 휴대폰은 보통 010 포함 11자리(010xxxxxxxx)
        // 일부 구형/특수 케이스는 10자리도 있으나(011 등), 여기서는 최소한의 검증만
        if (digits.length() == 11 && digits.startsWith("010")) {
            return Optional.of(digits);
        }

        // 10자리면서 010으로 시작하는 경우(이상 케이스) - 운영 정책에 따라 허용/불허
        // 여기서는 안전하게 거절합니다.
        return Optional.empty();
    }
}