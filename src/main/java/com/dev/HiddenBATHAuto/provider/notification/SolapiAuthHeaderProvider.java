package com.dev.HiddenBATHAuto.provider.notification;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.constant.SolapiProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SolapiAuthHeaderProvider {

    private final SolapiProperties properties;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String createAuthorizationHeader() {
        String apiKey = requireText(properties.getApiKey(), "SOLAPI API Key가 비어 있습니다.");
        String apiSecret = requireText(properties.getApiSecret(), "SOLAPI API Secret이 비어 있습니다.");

        String date = Instant.now()
                .truncatedTo(ChronoUnit.SECONDS)
                .toString();

        String salt = createSalt();
        String signature = hmacSha256Hex(apiSecret, date + salt);

        return "HMAC-SHA256 apiKey=" + apiKey
                + ", date=" + date
                + ", salt=" + salt
                + ", signature=" + signature;
    }

    private String createSalt() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SOLAPI HMAC 서명 생성 실패", e);
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }
}