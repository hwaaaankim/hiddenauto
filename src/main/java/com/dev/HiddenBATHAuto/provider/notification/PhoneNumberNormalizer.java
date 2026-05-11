package com.dev.HiddenBATHAuto.provider.notification;

import org.springframework.stereotype.Component;

@Component
public class PhoneNumberNormalizer {

    public String normalizeKoreanPhone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("전화번호가 비어 있습니다.");
        }

        String digits = raw.replaceAll("[^0-9]", "");

        if (digits.startsWith("82") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }

        if (digits.length() < 9 || digits.length() > 11) {
            throw new IllegalArgumentException("전화번호 형식이 올바르지 않습니다: " + raw);
        }

        return digits;
    }
}