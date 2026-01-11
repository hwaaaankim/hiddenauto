package com.dev.HiddenBATHAuto.constant;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.dev.HiddenBATHAuto.dto.address.JusoAddrLinkResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JusoAddressClient {

    private static final Logger log = LoggerFactory.getLogger(JusoAddressClient.class);

    private final WebClient jusoWebClient;

    // 생성 직후
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${juso.confm-key}")
    private String confmKey;

    @Value("${juso.throttle-ms:0}")
    private long throttleMs;

    @Value("${juso.count-per-page:10}")
    private int countPerPage;

    @Value("${juso.first-sort:location}")
    private String firstSort; // road or location

    public JusoAddrLinkResponse search(String keyword) {
        String k = keyword == null ? "" : keyword.trim();
        if (!StringUtils.hasText(k)) return new JusoAddrLinkResponse();

        if (throttleMs > 0) {
            try { TimeUnit.MILLISECONDS.sleep(throttleMs); } catch (InterruptedException ignored) {}
        }

        // 공식 endpoint: https://business.juso.go.kr/addrlink/addrLinkApi.do :contentReference[oaicite:3]{index=3}
        return jusoWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/addrlink/addrLinkApi.do")
                        .queryParam("confmKey", confmKey)
                        .queryParam("currentPage", 1)
                        .queryParam("countPerPage", countPerPage)
                        .queryParam("keyword", k)
                        .queryParam("resultType", "json")
                        // 우선정렬(선택): road/location
                        .queryParam("firstSort", firstSort)
                        .build())
                .exchangeToMono(resp -> parseJsonWithDiagnostics(resp, JusoAddrLinkResponse.class, "[JUSO]", k))
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> {
                    log.warn("[JUSO] EXCEPTION keyword='{}' err={}", k, shortErr(e));
                    return Mono.just(new JusoAddrLinkResponse());
                })
                .block();
    }

    private <T> Mono<T> parseJsonWithDiagnostics(ClientResponse resp, Class<T> type, String tag, String key) {
        HttpStatusCode sc = resp.statusCode();

        return resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    String bodyHead = head(body, 1000);

                    if (!sc.is2xxSuccessful()) {
                        log.warn("{} HTTP {} key='{}' body='{}'", tag, sc.value(), key, bodyHead);
                        return emptyInstance(type);
                    }

                    // ✅ (1) zipNo 존재 여부를 raw JSON으로 확인
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode firstZip = root.path("results").path("juso").path(0).path("zipNo");
                        log.info("{} RAW-ZIP key='{}' zipNo='{}'", tag, key, firstZip.isMissingNode() ? "(missing)" : firstZip.asText());
                    } catch (Exception ignore) {
                        log.warn("{} RAW-ZIP-PARSE-FAIL key='{}' body='{}'", tag, key, bodyHead);
                    }

                    try {
                        T parsed = objectMapper.readValue(body.getBytes(StandardCharsets.UTF_8), type);
                        return parsed;
                    } catch (Exception ex) {
                        log.warn("{} JSON_PARSE_FAIL key='{}' err={} body='{}'", tag, key, shortErr(ex), bodyHead);
                        return emptyInstance(type);
                    }
                });
    }

    private <T> T emptyInstance(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate " + type.getName(), e);
        }
    }

    private String head(String s, int max) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String shortErr(Throwable e) {
        if (e == null) return "";
        String m = e.getMessage();
        return e.getClass().getSimpleName() + (m == null ? "" : (": " + m));
    }
}
