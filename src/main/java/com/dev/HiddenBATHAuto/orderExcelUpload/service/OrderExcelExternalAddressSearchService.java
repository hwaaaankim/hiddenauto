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
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.dev.HiddenBATHAuto.orderExcelUpload.support.ResolvedExternalAddress;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OrderExcelExternalAddressSearchService {

    private static final Pattern ROAD_NUMBER_PATTERN = Pattern.compile("^(.+?(?:대로|로|길)(?:\\s*\\d+(?:번길|길))?\\s*\\d+(?:-\\d+)?)");
    private static final Pattern ROAD_KEY_PATTERN = Pattern.compile("([가-힣A-Za-z0-9·.]+(?:대로|로|길)(?:\\s*\\d+(?:번길|길))?)\\s*(\\d+(?:-\\d+)?)");
    private static final Pattern DISTRICT_PATTERN = Pattern.compile("(?:^|\\s)([가-힣]+(?:구|군))(?:\\s|$)");

    /**
     * 카카오 주소 API가 일부 시·도를 축약해서 반환할 수 있으므로
     * 담당구역 DB와 비교하기 전에 행정구역 정식 명칭으로 통일합니다.
     */
    private static final Map<String, String> PROVINCE_ALIASES = Map.ofEntries(
            Map.entry("서울", "서울특별시"),
            Map.entry("서울시", "서울특별시"),
            Map.entry("서울특별시", "서울특별시"),
            Map.entry("부산", "부산광역시"),
            Map.entry("부산시", "부산광역시"),
            Map.entry("부산광역시", "부산광역시"),
            Map.entry("대구", "대구광역시"),
            Map.entry("대구시", "대구광역시"),
            Map.entry("대구광역시", "대구광역시"),
            Map.entry("인천", "인천광역시"),
            Map.entry("인천시", "인천광역시"),
            Map.entry("인천광역시", "인천광역시"),
            Map.entry("광주", "광주광역시"),
            Map.entry("광주시", "광주광역시"),
            Map.entry("광주광역시", "광주광역시"),
            Map.entry("대전", "대전광역시"),
            Map.entry("대전시", "대전광역시"),
            Map.entry("대전광역시", "대전광역시"),
            Map.entry("울산", "울산광역시"),
            Map.entry("울산시", "울산광역시"),
            Map.entry("울산광역시", "울산광역시"),
            Map.entry("세종", "세종특별자치시"),
            Map.entry("세종시", "세종특별자치시"),
            Map.entry("세종특별자치시", "세종특별자치시"),
            Map.entry("경기", "경기도"),
            Map.entry("경기도", "경기도"),
            Map.entry("강원", "강원특별자치도"),
            Map.entry("강원도", "강원특별자치도"),
            Map.entry("강원특별자치도", "강원특별자치도"),
            Map.entry("충북", "충청북도"),
            Map.entry("충청북도", "충청북도"),
            Map.entry("충남", "충청남도"),
            Map.entry("충청남도", "충청남도"),
            Map.entry("전북", "전북특별자치도"),
            Map.entry("전라북도", "전북특별자치도"),
            Map.entry("전북특별자치도", "전북특별자치도"),
            Map.entry("전남", "전라남도"),
            Map.entry("전라남도", "전라남도"),
            Map.entry("경북", "경상북도"),
            Map.entry("경상북도", "경상북도"),
            Map.entry("경남", "경상남도"),
            Map.entry("경상남도", "경상남도"),
            Map.entry("제주", "제주특별자치도"),
            Map.entry("제주도", "제주특별자치도"),
            Map.entry("제주특별자치도", "제주특별자치도")
    );

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

    @Value("${kakao.timeout-seconds:5}")
    private int kakaoTimeoutSeconds;

    /**
     * 현장주소 원문을 행정구역이 포함된 정규 주소로 해석합니다.
     *
     * 처리 순서:
     * 1. 도로명주소(JUSO) 검색
     * 2. 카카오 주소 검색(analyze_type=similar)
     * 3. 카카오 키워드 검색 후 주소/좌표 재조회
     */
    public Optional<ResolvedExternalAddress> resolve(String rawAddress) {
        String keyword = normalize(rawAddress);
        if (keyword.isBlank()) {
            return Optional.empty();
        }

        List<String> candidates = buildCandidates(keyword);

        for (String candidate : candidates) {
            Optional<ResolvedExternalAddress> juso = searchJuso(candidate, keyword);
            if (juso.isPresent()) {
                return juso;
            }
        }

        for (String candidate : candidates) {
            Optional<ResolvedExternalAddress> kakao = searchKakaoAddress(candidate, keyword);
            if (kakao.isPresent()) {
                return kakao;
            }
        }

        for (String candidate : candidates) {
            Optional<ResolvedExternalAddress> kakaoKeyword = searchKakaoKeywordAndResolve(candidate, keyword);
            if (kakaoKeyword.isPresent()) {
                return kakaoKeyword;
            }
        }

        log.warn("현장주소 외부 검색 결과 없음: keyword={}", keyword);
        return Optional.empty();
    }

    private Optional<ResolvedExternalAddress> searchJuso(String apiKeyword, String originalKeyword) {
        if (jusoConfmKey == null || jusoConfmKey.isBlank() || apiKeyword == null || apiKeyword.isBlank()) {
            return Optional.empty();
        }

        String safeKeyword = sanitizeApiKeyword(apiKeyword);
        if (!isJusoSearchable(safeKeyword)) {
            return Optional.empty();
        }

        throttle(jusoThrottleMs);

        try {
            String body = jusoWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/addrlink/addrLinkApi.do")
                            .queryParam("confmKey", jusoConfmKey)
                            .queryParam("currentPage", 1)
                            .queryParam("countPerPage", Math.max(1, Math.min(jusoCountPerPage, 100)))
                            .queryParam("keyword", safeKeyword)
                            .queryParam("resultType", "json")
                            .queryParam("firstSort", jusoFirstSort == null || jusoFirstSort.isBlank() ? "location" : jusoFirstSort)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(Math.max(1, jusoTimeoutSeconds)))
                    .block();

            if (body == null || body.isBlank()) {
                return Optional.empty();
            }

            Map<String, Object> root = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> results = asMap(root.get("results"));
            Map<String, Object> common = asMap(results.get("common"));
            String errorCode = str(common.get("errorCode"));
            if (!errorCode.isBlank() && !"0".equals(errorCode)) {
                log.warn("JUSO 주소 검색 오류: code={}, message={}, keyword={}",
                        errorCode,
                        str(common.get("errorMessage")),
                        safeKeyword);
                return Optional.empty();
            }

            List<Map<String, Object>> jusoList = asListOfMap(results.get("juso"));
            Map<String, Object> best = selectBestJuso(originalKeyword, jusoList);
            if (best.isEmpty()) {
                return Optional.empty();
            }

            String siNm = canonicalProvince(str(best.get("siNm")));
            String sggNm = str(best.get("sggNm"));
            RegionParts region = splitRegion(siNm, sggNm, str(best.get("emdNm")));

            return Optional.of(ResolvedExternalAddress.builder()
                    .resolved(true)
                    .source("JUSO")
                    .zipCode(str(best.get("zipNo")))
                    .doName(region.doName())
                    .siName(region.siName())
                    .guName(region.guName())
                    .roadAddress(firstNonBlank(str(best.get("roadAddrPart1")), str(best.get("roadAddr"))))
                    .jibunAddress(str(best.get("jibunAddr")))
                    .build());
        } catch (WebClientResponseException e) {
            log.warn("JUSO 주소 검색 HTTP 오류: status={}, apiKeyword={}, originalKeyword={}, body={}",
                    e.getStatusCode().value(), safeKeyword, originalKeyword, abbreviate(e.getResponseBodyAsString(), 300));
            return Optional.empty();
        } catch (Exception e) {
            log.warn("JUSO 주소 검색 실패: apiKeyword={}, originalKeyword={}, error={}", safeKeyword, originalKeyword, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ResolvedExternalAddress> searchKakaoAddress(String apiKeyword, String originalKeyword) {
        if (!canUseKakao(apiKeyword)) {
            return Optional.empty();
        }

        throttle(kakaoThrottleMs);

        try {
            String safeKeyword = sanitizeApiKeyword(apiKeyword);
            if (!isKakaoSearchable(safeKeyword)) {
                return Optional.empty();
            }

            String body = kakaoWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/address.json")
                            .queryParam("query", safeKeyword)
                            .queryParam("analyze_type", "similar")
                            .queryParam("page", 1)
                            .queryParam("size", 30)
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(Math.max(1, kakaoTimeoutSeconds)))
                    .block();
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }

            Map<String, Object> root = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> docs = asListOfMap(root.get("documents"));
            Map<String, Object> best = selectBestKakaoAddress(originalKeyword, docs);
            if (best.isEmpty()) {
                return Optional.empty();
            }

            return toResolvedKakaoAddress(best, "KAKAO_ADDRESS");
        } catch (WebClientResponseException e) {
            log.warn("카카오 주소 검색 HTTP 오류: status={}, apiKeyword={}, originalKeyword={}, body={}",
                    e.getStatusCode().value(), apiKeyword, originalKeyword, abbreviate(e.getResponseBodyAsString(), 300));
            return Optional.empty();
        } catch (Exception e) {
            log.warn("카카오 주소 검색 실패: apiKeyword={}, originalKeyword={}, error={}", apiKeyword, originalKeyword, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 주소 API에서 결과가 없을 때 장소 키워드 검색을 수행한 뒤,
     * 검색된 도로명 주소 또는 좌표를 다시 주소 API에 넣어 행정구역을 확정합니다.
     */
    private Optional<ResolvedExternalAddress> searchKakaoKeywordAndResolve(String apiKeyword, String originalKeyword) {
        if (!canUseKakao(apiKeyword)) {
            return Optional.empty();
        }

        throttle(kakaoThrottleMs);

        try {
            String safeKeyword = sanitizeApiKeyword(apiKeyword);
            if (!isKakaoSearchable(safeKeyword)) {
                return Optional.empty();
            }

            String body = kakaoWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", safeKeyword)
                            .queryParam("page", 1)
                            .queryParam("size", 15)
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(Math.max(1, kakaoTimeoutSeconds)))
                    .block();
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }

            Map<String, Object> root = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> docs = asListOfMap(root.get("documents"));
            Map<String, Object> best = selectBestKakaoKeyword(originalKeyword, docs);
            if (best.isEmpty()) {
                return Optional.empty();
            }

            String roadAddress = str(best.get("road_address_name"));
            if (!roadAddress.isBlank()) {
                Optional<ResolvedExternalAddress> byRoad = searchKakaoAddress(roadAddress, originalKeyword);
                if (byRoad.isPresent()) {
                    return byRoad;
                }
            }

            String x = str(best.get("x"));
            String y = str(best.get("y"));
            if (!x.isBlank() && !y.isBlank()) {
                return searchKakaoCoordinate(x, y);
            }

            return Optional.empty();
        } catch (WebClientResponseException e) {
            log.warn("카카오 키워드 검색 HTTP 오류: status={}, apiKeyword={}, originalKeyword={}, body={}",
                    e.getStatusCode().value(), apiKeyword, originalKeyword, abbreviate(e.getResponseBodyAsString(), 300));
            return Optional.empty();
        } catch (Exception e) {
            log.warn("카카오 키워드 주소 검색 실패: apiKeyword={}, originalKeyword={}, error={}", apiKeyword, originalKeyword, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ResolvedExternalAddress> searchKakaoCoordinate(String x, String y) {
        if (!canUseKakao(x) || y == null || y.isBlank()) {
            return Optional.empty();
        }

        throttle(kakaoThrottleMs);

        try {
            String body = kakaoWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/geo/coord2address.json")
                            .queryParam("x", x)
                            .queryParam("y", y)
                            .queryParam("input_coord", "WGS84")
                            .build())
                    .header("Authorization", "KakaoAK " + kakaoRestApiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(Math.max(1, kakaoTimeoutSeconds)))
                    .block();
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }

            Map<String, Object> root = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> docs = asListOfMap(root.get("documents"));
            if (docs.isEmpty()) {
                return Optional.empty();
            }

            return toResolvedKakaoAddress(docs.get(0), "KAKAO_COORD");
        } catch (Exception e) {
            log.warn("카카오 좌표 주소 변환 실패: x={}, y={}, error={}", x, y, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ResolvedExternalAddress> toResolvedKakaoAddress(Map<String, Object> doc, String source) {
        if (doc == null || doc.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> road = asMap(doc.get("road_address"));
        Map<String, Object> address = asMap(doc.get("address"));

        String region1 = canonicalProvince(firstNonBlank(
                str(road.get("region_1depth_name")),
                str(address.get("region_1depth_name"))
        ));
        String region2 = firstNonBlank(
                str(road.get("region_2depth_name")),
                str(address.get("region_2depth_name"))
        );
        String region3 = firstNonBlank(
                str(road.get("region_3depth_name")),
                str(address.get("region_3depth_name"))
        );

        RegionParts region = splitRegion(region1, region2, region3);
        String roadAddress = firstNonBlank(
                str(road.get("address_name")),
                str(doc.get("address_name")),
                str(address.get("address_name"))
        );

        if (region.doName().isBlank() && region.siName().isBlank() && region.guName().isBlank()) {
            return Optional.empty();
        }

        return Optional.of(ResolvedExternalAddress.builder()
                .resolved(true)
                .source(source)
                .zipCode(str(road.get("zone_no")))
                .doName(region.doName())
                .siName(region.siName())
                .guName(region.guName())
                .roadAddress(roadAddress)
                .jibunAddress(str(address.get("address_name")))
                .build());
    }

    private Map<String, Object> selectBestJuso(String keyword, List<Map<String, Object>> rows) {
        Map<String, Object> best = Map.of();
        int bestScore = Integer.MIN_VALUE;
        for (Map<String, Object> row : rows) {
            int score = scoreCandidate(
                    keyword,
                    firstNonBlank(str(row.get("roadAddrPart1")), str(row.get("roadAddr"))),
                    str(row.get("jibunAddr")),
                    str(row.get("sggNm"))
            );
            if (score > bestScore) {
                bestScore = score;
                best = row;
            }
        }
        return best;
    }

    private Map<String, Object> selectBestKakaoAddress(String keyword, List<Map<String, Object>> docs) {
        Map<String, Object> best = Map.of();
        int bestScore = Integer.MIN_VALUE;
        for (Map<String, Object> doc : docs) {
            Map<String, Object> road = asMap(doc.get("road_address"));
            Map<String, Object> address = asMap(doc.get("address"));
            int score = scoreCandidate(
                    keyword,
                    firstNonBlank(str(road.get("address_name")), str(doc.get("address_name"))),
                    str(address.get("address_name")),
                    firstNonBlank(str(road.get("region_2depth_name")), str(address.get("region_2depth_name")))
            );
            if (score > bestScore) {
                bestScore = score;
                best = doc;
            }
        }
        return best;
    }

    private Map<String, Object> selectBestKakaoKeyword(String keyword, List<Map<String, Object>> docs) {
        Map<String, Object> best = Map.of();
        int bestScore = Integer.MIN_VALUE;
        for (Map<String, Object> doc : docs) {
            int score = scoreCandidate(
                    keyword,
                    str(doc.get("road_address_name")),
                    str(doc.get("address_name")),
                    ""
            );
            if (score > bestScore) {
                bestScore = score;
                best = doc;
            }
        }
        return best;
    }

    private int scoreCandidate(String keyword, String roadAddress, String jibunAddress, String districtText) {
        String query = compact(keyword);
        String road = compact(roadAddress);
        String jibun = compact(jibunAddress);
        String district = compact(districtText);
        int score = 0;

        if (!query.isBlank() && query.equals(road)) {
            score += 300;
        } else if (!query.isBlank() && road.contains(query)) {
            score += 180;
        } else if (!road.isBlank() && query.contains(road)) {
            score += 120;
        }

        if (!query.isBlank() && query.equals(jibun)) {
            score += 240;
        } else if (!query.isBlank() && jibun.contains(query)) {
            score += 140;
        }

        String roadKey = extractRoadKey(keyword);
        if (!roadKey.isBlank() && road.contains(roadKey)) {
            score += 220;
        }

        String districtKey = extractDistrict(keyword);
        if (!districtKey.isBlank() && (road.contains(districtKey) || jibun.contains(districtKey) || district.contains(districtKey))) {
            score += 80;
        }

        if (!road.isBlank()) {
            score += 10;
        }
        return score;
    }

    private String extractRoadKey(String value) {
        Matcher matcher = ROAD_KEY_PATTERN.matcher(normalize(value));
        if (!matcher.find()) {
            return "";
        }
        return compact(matcher.group(1) + matcher.group(2));
    }

    private String extractDistrict(String value) {
        Matcher matcher = DISTRICT_PATTERN.matcher(normalize(value));
        if (!matcher.find()) {
            return "";
        }
        return compact(matcher.group(1));
    }

    private List<String> buildCandidates(String raw) {
        List<String> result = new ArrayList<>();
        String normalizedRaw = normalize(raw);
        if (normalizedRaw.isBlank()) {
            return result;
        }

        String noParen = normalize(normalizedRaw.replaceAll("\\([^)]*\\)", " "));
        String noDetail = stripDetail(noParen);

        addRoadCandidates(result, noDetail);
        addRoadCandidates(result, normalizedRaw);
        addJibunCandidates(result, noDetail);
        addApartmentCandidates(result, normalizedRaw);

        addCandidate(result, noDetail);
        addCandidate(result, normalizedRaw);

        return result;
    }

    private void addRoadCandidates(List<String> result, String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return;
        }

        Matcher roadNumberMatcher = ROAD_NUMBER_PATTERN.matcher(normalized);
        if (roadNumberMatcher.find()) {
            addCandidate(result, roadNumberMatcher.group(1));
        }

        Matcher roadKeyMatcher = ROAD_KEY_PATTERN.matcher(normalized);
        if (roadKeyMatcher.find()) {
            String roadKey = roadKeyMatcher.group(1) + " " + roadKeyMatcher.group(2);
            String prefix = normalized.substring(0, roadKeyMatcher.start()).trim();
            String district = extractDistrictText(normalized);

            if (!prefix.isBlank()) {
                addCandidate(result, normalize(prefix + " " + roadKey));
            }
            if (!district.isBlank()) {
                addCandidate(result, normalize(district + " " + roadKey));
            }
            addCandidate(result, roadKey);
        }
    }

    private void addJibunCandidates(List<String> result, String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return;
        }

        Pattern jibunPattern = Pattern.compile("((?:[가-힣]+(?:시|군|구)\\s+)?[가-힣]+(?:동|읍|면|리)\\s+\\d+(?:-\\d+)?)");
        Matcher matcher = jibunPattern.matcher(normalized);
        while (matcher.find()) {
            addCandidate(result, matcher.group(1));
        }
    }

    private void addApartmentCandidates(List<String> result, String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return;
        }

        String district = extractDistrictText(normalized);
        String dong = extractLegalDongText(normalized);

        Matcher parenMatcher = Pattern.compile("\\(([^)]*)\\)").matcher(normalized);
        while (parenMatcher.find()) {
            String inside = normalize(parenMatcher.group(1).replace(',', ' '));
            addPlaceCandidate(result, district, dong, inside);
        }

        Matcher aptMatcher = Pattern.compile("([가-힣A-Za-z0-9·.\\s]{2,30}(?:아파트|빌라|오피스텔|주상복합|타운|마을|래미안|푸르지오|자이|힐스테이트|아이파크|더샵|센트럴|파크|리버|라피니엘|꿈에그린))").matcher(normalized);
        while (aptMatcher.find()) {
            addPlaceCandidate(result, district, dong, aptMatcher.group(1));
        }
    }

    private void addPlaceCandidate(List<String> result, String district, String dong, String place) {
        String cleanedPlace = sanitizeApiKeyword(place)
                .replaceAll("^(?:[가-힣]+(?:동|읍|면|리)\\s*)+", "")
                .replaceAll("\\b\\d{1,4}(?:동|호)\\b.*$", "")
                .trim();

        if (cleanedPlace.isBlank()) {
            return;
        }

        if (!district.isBlank() && !dong.isBlank()) {
            addCandidate(result, district + " " + dong + " " + cleanedPlace);
        }
        if (!district.isBlank()) {
            addCandidate(result, district + " " + cleanedPlace);
        }
        addCandidate(result, cleanedPlace);
    }

    private String stripDetail(String value) {
        return normalize(value)
                .replaceAll("\\b\\d{1,4}동\\b.*$", "")
                .replaceAll("\\b\\d{1,4}호\\b.*$", "")
                .replaceAll("[A-Za-z]?동\\s*\\d{1,4}호.*$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String extractDistrictText(String value) {
        Matcher matcher = DISTRICT_PATTERN.matcher(normalize(value));
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractLegalDongText(String value) {
        Matcher matcher = Pattern.compile("(?:^|\\s)([가-힣]+(?:동|읍|면|리))(?:\\s|$)").matcher(normalize(value));
        return matcher.find() ? matcher.group(1) : "";
    }

    private void addCandidate(List<String> result, String value) {
        String normalized = normalize(value);
        if (!normalized.isBlank() && !result.contains(normalized)) {
            result.add(normalized);
        }
    }

    private RegionParts splitRegion(String region1, String region2, String region3) {
        String r1 = canonicalProvince(region1);
        String r2 = normalize(region2);
        String r3 = normalize(region3);

        if (isMetropolitanProvince(r1)) {
            String district = firstDistrictToken(r2);
            if (district.isBlank() && (r3.endsWith("구") || r3.endsWith("군"))) {
                district = r3;
            }
            return new RegionParts(r1, "", district);
        }

        if (r2.contains(" ")) {
            String[] parts = r2.split("\\s+");
            if (parts.length >= 2 && (parts[0].endsWith("시") || parts[0].endsWith("군"))) {
                return new RegionParts(r1, parts[0], parts[1]);
            }
        }

        if (r2.endsWith("구")) {
            return new RegionParts(r1, "", r2);
        }

        String gu = "";
        if (r3.endsWith("구") || r3.endsWith("군")) {
            gu = r3;
        }

        return new RegionParts(r1, r2, gu);
    }

    private String firstDistrictToken(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return "";
        }
        for (String token : normalized.split("\\s+")) {
            if (token.endsWith("구") || token.endsWith("군")) {
                return token;
            }
        }
        return normalized;
    }

    private String canonicalProvince(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return "";
        }
        return PROVINCE_ALIASES.getOrDefault(normalized, normalized);
    }

    private boolean isMetropolitanProvince(String value) {
        return value.endsWith("특별시")
                || value.endsWith("광역시")
                || value.endsWith("특별자치시")
                || "세종특별자치시".equals(value);
    }

    private String sanitizeApiKeyword(String value) {
        return normalize(value)
                .replaceAll("[\\[\\]{}<>%=]", " ")
                .replaceAll("[\\(\\)]", " ")
                .replace(',', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isJusoSearchable(String keyword) {
        String normalized = sanitizeApiKeyword(keyword);
        if (normalized.isBlank()) {
            return false;
        }
        long koreanCount = normalized.codePoints()
                .filter(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL)
                .count();
        return koreanCount <= 40 && normalized.length() <= 80;
    }

    private boolean isKakaoSearchable(String keyword) {
        String normalized = sanitizeApiKeyword(keyword);
        return !normalized.isBlank() && normalized.length() <= 90;
    }

    private boolean canUseKakao(String keyword) {
        return kakaoRestApiKey != null
                && !kakaoRestApiKey.isBlank()
                && keyword != null
                && !keyword.isBlank();
    }

    private String abbreviate(String value, int maxLength) {
        String normalized = normalize(value);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength)) + "...";
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

    private String compact(String value) {
        return normalize(value)
                .replaceAll("[\\s,()\\[\\]{}]", "")
                .toLowerCase();
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
