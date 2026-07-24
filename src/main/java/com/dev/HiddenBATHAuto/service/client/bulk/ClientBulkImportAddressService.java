package com.dev.HiddenBATHAuto.service.client.bulk;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientBulkImportAddressService {

    private static final Pattern PARENTHESIS_PATTERN = Pattern.compile("\\([^)]*\\)");
    private static final Pattern FORBIDDEN_JUSO_CHARS = Pattern.compile("[%=><\\[\\]]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${juso.confm-key:}")
    private String jusoConfmKey;

    @Value("${juso.base-url:https://business.juso.go.kr}")
    private String jusoBaseUrl;

    @Value("${juso.first-sort:location}")
    private String jusoFirstSort;

    @Value("${juso.count-per-page:10}")
    private int jusoCountPerPage;

    @Value("${juso.timeout-seconds:5}")
    private int timeoutSeconds;

    @Value("${juso.throttle-ms:0}")
    private long jusoThrottleMs;

    @Value("${kakao.base-url:https://dapi.kakao.com}")
    private String kakaoBaseUrl;

    @Value("${kakao.rest.api-key:}")
    private String kakaoApiKey;

    @Value("${kakao.throttle-ms:120}")
    private long kakaoThrottleMs;

    private long lastJusoRequestAt;
    private long lastKakaoRequestAt;

    public AddressResolution resolve(String originAddress, String excelDetailAddress) {
        String origin = normalizeText(originAddress);
        String excelDetail = normalizeText(excelDetailAddress);

        if (origin.isBlank()) {
            return AddressResolution.unresolved(excelDetail);
        }

        List<String> candidates = buildSearchCandidates(origin);

        for (String candidate : candidates) {
            AddressResolution jusoResult = searchJuso(candidate, origin, excelDetail);
            if (jusoResult != null && jusoResult.resolved()) {
                return jusoResult;
            }
        }

        for (String candidate : candidates) {
            AddressResolution kakaoAddressResult = searchKakaoAddress(candidate, origin, excelDetail);
            if (kakaoAddressResult != null && kakaoAddressResult.resolved()) {
                return kakaoAddressResult;
            }
        }

        for (String candidate : candidates) {
            AddressResolution kakaoKeywordResult = searchKakaoKeyword(candidate, origin, excelDetail);
            if (kakaoKeywordResult != null && kakaoKeywordResult.resolved()) {
                return kakaoKeywordResult;
            }
        }

        return AddressResolution.unresolved(excelDetail);
    }

    private List<String> buildSearchCandidates(String origin) {
        Set<String> candidates = new LinkedHashSet<>();

        String normalized = normalizeText(origin);
        String withoutParenthesis = normalizeText(PARENTHESIS_PATTERN.matcher(normalized).replaceAll(" "));
        String beforeComma = normalizeText(substringBeforeFirstComma(normalized));
        String beforeCommaWithoutParenthesis = normalizeText(PARENTHESIS_PATTERN.matcher(beforeComma).replaceAll(" "));

        addCandidate(candidates, beforeCommaWithoutParenthesis);
        addCandidate(candidates, beforeComma);
        addCandidate(candidates, withoutParenthesis);
        addCandidate(candidates, normalized);

        return new ArrayList<>(candidates);
    }

    private void addCandidate(Set<String> candidates, String value) {
        String candidate = sanitizeSearchKeyword(value);
        if (candidate.isBlank()) {
            return;
        }

        // JUSO의 한글 검색어 길이 제한과 Kakao 검색어의 UTF-8 바이트 제한을
        // 모두 넘지 않도록 후보를 줄입니다.
        if (candidate.length() > 40) {
            candidate = normalizeText(candidate.substring(0, 40));
        }
        candidate = truncateUtf8(candidate, 90);

        if (!candidate.isBlank()) {
            candidates.add(candidate);
        }
    }

    private AddressResolution searchJuso(String keyword, String origin, String excelDetail) {
        if (isBlank(jusoConfmKey) || isBlank(jusoBaseUrl) || isBlank(keyword)) {
            return null;
        }

        try {
            throttleJuso();

            String url = trimTrailingSlash(jusoBaseUrl)
                    + "/addrlink/addrLinkApi.do"
                    + "?confmKey=" + encode(jusoConfmKey)
                    + "&currentPage=1"
                    + "&countPerPage=" + Math.max(1, jusoCountPerPage)
                    + "&keyword=" + encode(keyword)
                    + "&resultType=json"
                    + "&firstSort=" + encode(defaultIfBlank(jusoFirstSort, "location"));

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("대리점 일괄등록 JUSO 주소검색 HTTP 오류: status={}, keyword={}", response.statusCode(), keyword);
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode common = root.path("results").path("common");
            String errorCode = common.path("errorCode").asText("");
            if (!"0".equals(errorCode)) {
                log.debug("대리점 일괄등록 JUSO 검색 결과 없음: code={}, keyword={}", errorCode, keyword);
                return null;
            }

            JsonNode rows = root.path("results").path("juso");
            if (!rows.isArray() || rows.size() == 0) {
                return null;
            }

            JsonNode row = rows.get(0);
            String doName = row.path("siNm").asText("").trim();
            RegionParts region = splitRegion(doName, row.path("sggNm").asText(""));
            String roadAddress = row.path("roadAddr").asText("").trim();
            String jibunAddress = row.path("jibunAddr").asText("").trim();
            String zipCode = row.path("zipNo").asText("").trim();
            String detailAddress = extractDetailAddress(origin, roadAddress, excelDetail);

            if (roadAddress.isBlank() && jibunAddress.isBlank()) {
                return null;
            }

            return new AddressResolution(
                    true,
                    "JUSO",
                    zipCode,
                    region.doName(),
                    region.siName(),
                    region.guName(),
                    jibunAddress,
                    roadAddress,
                    detailAddress
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("대리점 일괄등록 JUSO 주소검색 실패: keyword={}, message={}", keyword, e.getMessage());
            return null;
        }
    }

    private AddressResolution searchKakaoAddress(String keyword, String origin, String excelDetail) {
        if (isBlank(kakaoApiKey) || isBlank(kakaoBaseUrl) || isBlank(keyword)) {
            return null;
        }

        try {
            throttleKakao();

            String url = trimTrailingSlash(kakaoBaseUrl)
                    + "/v2/local/search/address.json?query=" + encode(keyword)
                    + "&size=10";

            JsonNode root = sendKakaoRequest(url, keyword);
            if (root == null) {
                return null;
            }

            JsonNode documents = root.path("documents");
            if (!documents.isArray() || documents.size() == 0) {
                return null;
            }

            JsonNode document = documents.get(0);
            JsonNode road = document.path("road_address");
            JsonNode address = document.path("address");

            String roadAddress = road.path("address_name").asText("").trim();
            String jibunAddress = address.path("address_name").asText("").trim();
            String zipCode = road.path("zone_no").asText("").trim();

            JsonNode regionNode = !roadAddress.isBlank() ? road : address;
            String doName = regionNode.path("region_1depth_name").asText("").trim();
            String secondDepth = regionNode.path("region_2depth_name").asText("").trim();
            RegionParts region = splitRegion(doName, secondDepth);

            if (roadAddress.isBlank() && jibunAddress.isBlank()) {
                return null;
            }

            return new AddressResolution(
                    true,
                    "KAKAO_ADDRESS",
                    zipCode,
                    region.doName(),
                    region.siName(),
                    region.guName(),
                    jibunAddress,
                    roadAddress,
                    extractDetailAddress(origin, roadAddress, excelDetail)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("대리점 일괄등록 카카오 주소검색 실패: keyword={}, message={}", keyword, e.getMessage());
            return null;
        }
    }

    private AddressResolution searchKakaoKeyword(String keyword, String origin, String excelDetail) {
        if (isBlank(kakaoApiKey) || isBlank(kakaoBaseUrl) || isBlank(keyword)) {
            return null;
        }

        try {
            throttleKakao();

            String url = trimTrailingSlash(kakaoBaseUrl)
                    + "/v2/local/search/keyword.json?query=" + encode(keyword)
                    + "&size=10";

            JsonNode root = sendKakaoRequest(url, keyword);
            if (root == null) {
                return null;
            }

            JsonNode documents = root.path("documents");
            if (!documents.isArray() || documents.size() == 0) {
                return null;
            }

            JsonNode document = documents.get(0);
            String roadAddress = document.path("road_address_name").asText("").trim();
            String jibunAddress = document.path("address_name").asText("").trim();
            String regionSource = !roadAddress.isBlank() ? roadAddress : jibunAddress;
            RegionParts region = splitRegionFromFullAddress(regionSource);

            if (roadAddress.isBlank() && jibunAddress.isBlank()) {
                return null;
            }

            return new AddressResolution(
                    true,
                    "KAKAO_KEYWORD",
                    "",
                    region.doName(),
                    region.siName(),
                    region.guName(),
                    jibunAddress,
                    roadAddress,
                    extractDetailAddress(origin, roadAddress, excelDetail)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("대리점 일괄등록 카카오 키워드검색 실패: keyword={}, message={}", keyword, e.getMessage());
            return null;
        }
    }

    private JsonNode sendKakaoRequest(String url, String keyword) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .header("Authorization", "KakaoAK " + kakaoApiKey.trim())
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("대리점 일괄등록 카카오 주소검색 HTTP 오류: status={}, keyword={}", response.statusCode(), keyword);
            return null;
        }

        return objectMapper.readTree(response.body());
    }

    private String extractDetailAddress(String origin, String matchedRoadAddress, String excelDetail) {
        if (!isBlank(excelDetail)) {
            return normalizeText(excelDetail);
        }

        String normalizedOrigin = normalizeText(origin);
        int commaIndex = normalizedOrigin.indexOf(',');
        if (commaIndex >= 0 && commaIndex + 1 < normalizedOrigin.length()) {
            return normalizeText(normalizedOrigin.substring(commaIndex + 1));
        }

        String road = normalizeText(matchedRoadAddress);
        if (!road.isBlank() && normalizedOrigin.startsWith(road) && normalizedOrigin.length() > road.length()) {
            return normalizeText(normalizedOrigin.substring(road.length()));
        }

        return "";
    }

    private RegionParts splitRegion(String doName, String secondDepth) {
        String province = normalizeText(doName);
        String sgg = normalizeText(secondDepth);
        if (sgg.isBlank()) {
            return new RegionParts(province, "", "");
        }

        String[] parts = sgg.split("\\s+");
        if (isMetropolitanProvince(province)) {
            return new RegionParts(province, "", parts[0]);
        }

        if (parts.length >= 2 && (parts[0].endsWith("시") || parts[0].endsWith("군"))) {
            return new RegionParts(province, parts[0], parts[1]);
        }

        if (parts[0].endsWith("시") || parts[0].endsWith("군")) {
            return new RegionParts(province, parts[0], "");
        }

        if (parts[0].endsWith("구")) {
            return new RegionParts(province, "", parts[0]);
        }

        return new RegionParts(province, parts[0], parts.length >= 2 ? parts[1] : "");
    }

    private RegionParts splitRegionFromFullAddress(String fullAddress) {
        String normalized = normalizeText(fullAddress);
        if (normalized.isBlank()) {
            return new RegionParts("", "", "");
        }

        String[] parts = normalized.split("\\s+");
        String doName = parts.length > 0 ? parts[0] : "";
        String secondDepth = parts.length > 1 ? parts[1] : "";
        String thirdDepth = parts.length > 2 ? parts[2] : "";

        if (isMetropolitanProvince(doName)) {
            return new RegionParts(doName, "", secondDepth);
        }

        if ((secondDepth.endsWith("시") || secondDepth.endsWith("군")) && thirdDepth.endsWith("구")) {
            return new RegionParts(doName, secondDepth, thirdDepth);
        }

        if (secondDepth.endsWith("시") || secondDepth.endsWith("군")) {
            return new RegionParts(doName, secondDepth, "");
        }

        if (secondDepth.endsWith("구")) {
            return new RegionParts(doName, "", secondDepth);
        }

        return new RegionParts(doName, secondDepth, thirdDepth);
    }

    private boolean isMetropolitanProvince(String value) {
        String text = normalizeText(value);
        return text.endsWith("특별시")
                || text.endsWith("광역시")
                || text.endsWith("특별자치시")
                || "세종특별자치시".equals(text);
    }

    private synchronized void throttleJuso() throws InterruptedException {
        long waitMs = calculateWait(lastJusoRequestAt, jusoThrottleMs);
        if (waitMs > 0) {
            Thread.sleep(waitMs);
        }
        lastJusoRequestAt = System.currentTimeMillis();
    }

    private synchronized void throttleKakao() throws InterruptedException {
        long waitMs = calculateWait(lastKakaoRequestAt, kakaoThrottleMs);
        if (waitMs > 0) {
            Thread.sleep(waitMs);
        }
        lastKakaoRequestAt = System.currentTimeMillis();
    }

    private long calculateWait(long lastRequestAt, long throttleMs) {
        if (throttleMs <= 0 || lastRequestAt <= 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastRequestAt;
        return Math.max(0, throttleMs - elapsed);
    }

    private String sanitizeSearchKeyword(String value) {
        String sanitized = FORBIDDEN_JUSO_CHARS.matcher(defaultIfBlank(value, "")).replaceAll(" ");
        sanitized = sanitized.replace('/', ' ').replace('|', ' ');
        return normalizeText(sanitized);
    }

    private String substringBeforeFirstComma(String value) {
        int index = value.indexOf(',');
        return index < 0 ? value : value.substring(0, index);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return MULTI_SPACE.matcher(value.replace('\u00A0', ' ').trim()).replaceAll(" ");
    }

    private String truncateUtf8(String value, int maxBytes) {
        String normalized = normalizeText(value);
        if (normalized.isBlank() || maxBytes <= 0) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int usedBytes = 0;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (usedBytes + characterBytes > maxBytes) {
                break;
            }
            result.append(character);
            usedBytes += characterBytes;
            offset += Character.charCount(codePoint);
        }
        return normalizeText(result.toString());
    }

    private String encode(String value) {
        return URLEncoder.encode(defaultIfBlank(value, ""), StandardCharsets.UTF_8);
    }

    private String trimTrailingSlash(String value) {
        String result = defaultIfBlank(value, "").trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record RegionParts(String doName, String siName, String guName) {
    }

    public record AddressResolution(
            boolean resolved,
            String source,
            String zipCode,
            String doName,
            String siName,
            String guName,
            String jibunAddress,
            String roadAddress,
            String detailAddress
    ) {
        public static AddressResolution unresolved(String detailAddress) {
            return new AddressResolution(
                    false,
                    "NONE",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    detailAddress == null ? "" : detailAddress.trim()
            );
        }
    }
}
