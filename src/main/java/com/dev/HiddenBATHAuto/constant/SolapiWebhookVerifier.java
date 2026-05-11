package com.dev.HiddenBATHAuto.constant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SolapiWebhookVerifier {

    private final SolapiProperties properties;

    public boolean isValid(String receivedHeaderValue) {
        String secret = properties.getWebhookSecret();

        if (secret == null || secret.isBlank()) {
            return false;
        }

        if (receivedHeaderValue == null || receivedHeaderValue.isBlank()) {
            return false;
        }

        String expected = sha1Hex(secret.trim());
        return expected.equalsIgnoreCase(receivedHeaderValue.trim());
    }

    private String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception e) {
            throw new IllegalStateException("SOLAPI 웹훅 Secret SHA1 검증 실패", e);
        }
    }
}