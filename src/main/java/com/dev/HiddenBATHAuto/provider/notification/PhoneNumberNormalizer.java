package com.dev.HiddenBATHAuto.provider.notification;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {

    private static final Set<String> MOBILE_PREFIXES = Set.of("010", "011", "016", "017", "018", "019");

    /** 발신번호처럼 휴대전화·유선전화가 모두 가능한 국내 번호를 숫자 형식으로 정규화합니다. */
    public String normalizeKoreanPhone(String raw) {
        String digits = normalizeCountryCode(raw);
        if (!digits.startsWith("0") || digits.length() < 9 || digits.length() > 11) {
            throw new InvalidPhoneNumberException("국내 전화번호 형식이 올바르지 않습니다.");
        }
        return digits;
    }

    /** 알림톡 수신자 번호를 국내 휴대전화 형식으로 정규화하고 검증합니다. */
    public String normalizeKoreanMobile(String raw) {
        String digits = normalizeCountryCode(raw);
        if (digits.length() < 10 || digits.length() > 11) {
            throw new InvalidPhoneNumberException("수신 휴대전화 번호의 자릿수가 올바르지 않습니다.");
        }
        String prefix = digits.substring(0, 3);
        if (!MOBILE_PREFIXES.contains(prefix)) {
            throw new InvalidPhoneNumberException("수신 번호가 국내 휴대전화 번호 형식이 아닙니다.");
        }
        if ("010".equals(prefix) && digits.length() != 11) {
            throw new InvalidPhoneNumberException("010 휴대전화 번호는 11자리여야 합니다.");
        }
        return digits;
    }

    private String normalizeCountryCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidPhoneNumberException("전화번호가 비어 있습니다.");
        }

        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("0082")) {
            digits = "0" + digits.substring(4);
        } else if (digits.startsWith("82")) {
            digits = "0" + digits.substring(2);
        }
        return digits;
    }
}
