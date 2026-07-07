package com.dev.HiddenBATHAuto.orderExcelUpload.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.dev.HiddenBATHAuto.orderExcelUpload.support.ResolvedExternalAddress;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OrderExcelExternalAddressSearchService {

    private static final Pattern ROAD_NUMBER_PATTERN = Pattern.compile("^(.+?(?:대로|로|길|번길)\\s*\\d+(?:-\\d+)?)");

    private final WebClient jusoWebClient;
    private final WebClient kakaoWebClient;
    private final ObjectMapper objectMapper;


    public OrderExcelExternalAddressSearchService(
            @Qualifier("jusoWebClient") WebClient jusoWebClient,
            @Qualifier("kakaoWebClient") WebClient kakaoWebClient,
            ObjectMapper objectMapper
    ) {
        this.jusoWebClient = jusoWebClient;
        this.kakaoWebClient = kakaoWebClient;
        this.objectMapper = objectMapper;
    }

    @Value("${juso.confm-key:}")
    private String jusoConfmKey;

    @Value("${juso.first-sort:location}")
    private String jusoFirstSort;

    @Value("${juso.count-per-page:10}")
    private int jusoCountPerPage;

    @Value("${juso.timeout-seconds:5}")
    private int jusoTimeoutSeconds;

    @Value("${juso.throttle-ms:0}")
    private long jusoThrottleMs;

    @Value("${kakao.rest.api-key:}")
    private String kakaoRestApiKey;

    @Value("${kakao.throttle-ms:120}")
    private long kakaoThrottleMs;

    public Optional<ResolvedExternalAddress> resolve(String rawAddress) {
        String keyword = normalize(rawAddress);
        if (keyword.isBlank()) {
            return Optional.empty();
        }

        for (String candidate : buildCandidates(keyword)) {
            Optional<ResolvedExternalAddress> juso = searchJuso(candidate);
            if (juso.isPresent()) {
                return juso;
            }
        }

        for (String candidate : buildCandidates(keyword)) {
            Optional<ResolvedExternalAddress> kakao = searchKakao(candidate);
            if (kakao.isPresent()) {
                return kakao;
            }
        }

        return Optional.empty();
    }

    private Optional<ResolvedExternalAddress> searchJuso(String keyword) {
        if (jusoConfmKey == null || jusoConfmKey.isBlank() || keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }

        throttle(jusoThrottleMs);

        try {
            String uri = UriComponentsBuilder.fromPath("/addrlink/addrLinkApi.do")
                    .queryParam("confmKey", jusoConfmKey)
                    .queryParam("currentPage", 1)
                    .queryParam("countPerPage", Math.max(1, jusoCountPerPage))
                    .queryParam("keyword", keyword)
                    .queryParam("resultType", "json")
                    .queryParam("firstSort", jusoFirstSort == null || jusoFirstSort.isBlank() ? "location" : jusoFirstSort)
                    .build()
                    .encode()
                    .toUriString();

            String body = jusoWebClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(Math.max(1, jusoTimeoutSeconds)))
                    .block();

            if (body == null || body.isBlank()) {
                return Optional.empty();
            }

            Map<String, Object> root = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> results = asMap(root.get("results"));
            List<Map<String, Object>> jusoList = asListOfMap(results.get("juso"));
            if (jusoList.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Object> first = jusoList.get(0);
            String siNm = str(first.get("siNm"));
            String sggNm = str(first.get("sggNm"));

            RegionParts region = splitRegion(siNm, sggNm, str(first.get("emdNm")));

            return Optional.of(ResolvedExternalAddress.builder()
                    .resolved(true)
                    .source("JUSO")
                    .zipCode(str(first.get("zipNo")))
                    .doName(region.doName())
                    .siName(region.siName())
                    .guName(region.guName())
                    .roadAddress(firstNonBlank(str(first.get("roadAddrPart1")), str(first.get("roadAddr"))))
                    .jibunAddress(str(first.get("jibunAddr")))
                    .build());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<ResolvedExternalAddress> searchKakao(String keyword) {
        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank() || keyword == null || keyword.isBlank()) {
            return Optional.empty();
        }

        throttle(kakaoThrottleMs);

        try {
            String uri = UriComponentsBuilder.fromPath("/v2/local/search/address.json")
                    .queryParam("query", keyword)
                    .build()
                    .encode()
                    .toUriString();

            String body = kakaoWebClient.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            if (body == null || body.isBlank()) {
                return Optional.empty();
            }

            Map<String, Object> root = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> docs = asListOfMap(root.get("documents"));
            if (docs.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Object> doc = docs.get(0);
            Map<String, Object> road = asMap(doc.get("road_address"));
            Map<String, Object> address = asMap(doc.get("address"));

            String region1 = firstNonBlank(str(road.get("region_1depth_name")), str(address.get("region_1depth_name")));
            String region2 = firstNonBlank(str(road.get("region_2depth_name")), str(address.get("region_2depth_name")));
            String region3 = firstNonBlank(str(road.get("region_3depth_name")), str(address.get("region_3depth_name")));
            RegionParts region = splitRegion(region1, region2, region3);

            return Optional.of(ResolvedExternalAddress.builder()
                    .resolved(true)
                    .source("KAKAO")
                    .zipCode(str(road.get("zone_no")))
                    .doName(region.doName())
                    .siName(region.siName())
                    .guName(region.guName())
                    .roadAddress(firstNonBlank(str(road.get("address_name")), str(doc.get("address_name"))))
                    .jibunAddress(str(address.get("address_name")))
                    .build());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private List<String> buildCandidates(String raw) {
        List<String> result = new ArrayList<>();
        addCandidate(result, raw);
        addCandidate(result, raw.replaceAll("\\([^)]*\\)", " ").replaceAll("\\s+", " ").trim());

        Matcher roadNumberMatcher = ROAD_NUMBER_PATTERN.matcher(raw);
        if (roadNumberMatcher.find()) {
            addCandidate(result, roadNumberMatcher.group(1));
        }

        String withoutDetail = raw
                .replaceAll("\\b\\d{1,4}동\\b.*$", "")
                .replaceAll("\\b\\d{1,4}호\\b.*$", "")
                .replaceAll("[A-Za-z]?동\\s*\\d{1,4}호.*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        addCandidate(result, withoutDetail);
        return result;
    }

    private void addCandidate(List<String> result, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank() && !result.contains(normalized)) {
            result.add(normalized);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private RegionParts splitRegion(String region1, String region2, String region3) {
        String r1 = normalize(region1);
        String r2 = normalize(region2);
        String r3 = normalize(region3);

        if (isMetropolitanProvince(r1)) {
            return new RegionParts(r1, "", r2);
        }

        if (r2.contains(" ")) {
            String[] parts = r2.split("\\s+");
            if (parts.length >= 2 && (parts[0].endsWith("시") || parts[0].endsWith("군"))) {
                return new RegionParts(r1, parts[0], parts[1]);
            }
        }

        if (r2.endsWith("구") && !r3.isBlank()) {
            return new RegionParts(r1, "", r2);
        }

        String gu = "";
        if (r3.endsWith("구") || r3.endsWith("군")) {
            gu = r3;
        }

        return new RegionParts(r1, r2, gu);
    }

    private boolean isMetropolitanProvince(String value) {
        return value.endsWith("특별시")
                || value.endsWith("광역시")
                || value.endsWith("특별자치시")
                || "세종특별자치시".equals(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asListOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                result.add(row);
            }
        }
        return result;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private void throttle(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record RegionParts(String doName, String siName, String guName) {
    }
}
