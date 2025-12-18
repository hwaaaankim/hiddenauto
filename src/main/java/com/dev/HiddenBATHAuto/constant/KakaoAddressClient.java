package com.dev.HiddenBATHAuto.constant;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import com.dev.HiddenBATHAuto.dto.address.AddressPickResult;
import com.dev.HiddenBATHAuto.dto.address.KakaoCoord2AddressResponse;
import com.dev.HiddenBATHAuto.dto.address.KakaoDocument;
import com.dev.HiddenBATHAuto.dto.address.KakaoKeywordDoc;
import com.dev.HiddenBATHAuto.dto.address.KakaoKeywordResponse;
import com.dev.HiddenBATHAuto.dto.address.KakaoResponse;
import com.dev.HiddenBATHAuto.utils.AddressFallbackParser;
import com.dev.HiddenBATHAuto.utils.AddressNormalizer;
import com.dev.HiddenBATHAuto.utils.AddressPreprocessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class KakaoAddressClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoAddressClient.class);

    private final WebClient kakaoWebClient;

    // ✅ raw 응답 로그를 위해 ObjectMapper 사용
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kakao.rest.api-key}")
    private String apiKey;

    @Value("${kakao.throttle-ms:0}")
    private long throttleMs;

    private String auth() {
        return "KakaoAK " + apiKey;
    }

    // =========================
    // 1) API 호출부 (AD/KW/C2A)
    // =========================

    public KakaoResponse searchAddress(String query) {
        String q = query == null ? "" : query.trim();
        log.debug("[AD] q='{}' (len={})", q, q.codePointCount(0, q.length()));

        return kakaoWebClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v2/local/search/address.json")
                .queryParam("analyze_type", "similar") // 필요시 exact/미지정도 fallback로 사용 가능
                .queryParam("size", 10)
                .queryParam("query", q)
                .build()
            )
            .header(HttpHeaders.AUTHORIZATION, auth())
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .exchangeToMono(resp -> parseJsonWithDiagnostics(resp, KakaoResponse.class, "[AD]", q))
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(e -> {
                log.warn("[AD] EXCEPTION q='{}' err={}", q, shortErr(e));
                return Mono.just(new KakaoResponse());
            })
            .block();
    }

    public KakaoKeywordResponse searchKeyword(String query) {
        String q = query == null ? "" : query.trim();
        log.debug("[KW] q='{}' (len={})", q, q.codePointCount(0, q.length()));

        return kakaoWebClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v2/local/search/keyword.json")
                .queryParam("size", 10)
                .queryParam("query", q)
                .build()
            )
            .header(HttpHeaders.AUTHORIZATION, auth())
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .exchangeToMono(resp -> parseJsonWithDiagnostics(resp, KakaoKeywordResponse.class, "[KW]", q))
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(e -> {
                log.warn("[KW] EXCEPTION q='{}' err={}", q, shortErr(e));
                return Mono.just(new KakaoKeywordResponse());
            })
            .block();
    }

    public KakaoCoord2AddressResponse coord2Address(String x, String y) {
        String sx = safe(x);
        String sy = safe(y);
        if (!StringUtils.hasText(sx) || !StringUtils.hasText(sy)) return null;

        log.debug("[C2A] x={}, y={}", sx, sy);

        return kakaoWebClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/v2/local/geo/coord2address.json")
                .queryParam("x", sx)
                .queryParam("y", sy)
                .queryParam("input_coord", "WGS84")
                .build()
            )
            .header(HttpHeaders.AUTHORIZATION, auth())
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .exchangeToMono(resp -> parseJsonWithDiagnostics(resp, KakaoCoord2AddressResponse.class, "[C2A]", sx + "," + sy))
            .timeout(Duration.ofSeconds(5))
            .onErrorResume(e -> {
                log.warn("[C2A] EXCEPTION x={}, y={} err={}", sx, sy, shortErr(e));
                return Mono.just(new KakaoCoord2AddressResponse());
            })
            .block();
    }

    /**
     * ✅ 여기서 핵심:
     * - status가 2xx가 아니면 raw body를 찍고 빈 객체 반환
     * - status가 2xx여도 raw body를 (필요 시) 일부 찍어서 "documents가 비는 이유"를 추적 가능
     */
    private <T> Mono<T> parseJsonWithDiagnostics(ClientResponse resp, Class<T> type, String tag, String key) {
        HttpStatusCode sc = resp.statusCode();

        return resp.bodyToMono(String.class)
            .defaultIfEmpty("")
            .map(body -> {
                String bodyHead = head(body, 500);

                if (!sc.is2xxSuccessful()) {
                    // ✅ 여기서 401/403/429 같은게 명확히 드러납니다.
                    log.warn("{} HTTP {} key='{}' body='{}'", tag, sc.value(), key, bodyHead);
                    return emptyInstance(type);
                }

                try {
                    T parsed = objectMapper.readValue(body.getBytes(StandardCharsets.UTF_8), type);

                    // ✅ 성공이더라도 documents가 비면 원인 추적 위해 body를 일부 남김
                    if (isDocumentsEmpty(parsed)) {
                        log.warn("{} EMPTY_DOCS key='{}' body='{}'", tag, key, bodyHead);
                    }
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
            // 최후: null로 떨어지면 호출부가 더 꼬이니, 런타임 예외로 올려서 바로 알게 하는 게 낫습니다.
            throw new RuntimeException("Cannot instantiate " + type.getName(), e);
        }
    }

    private boolean isDocumentsEmpty(Object parsed) {
        if (parsed == null) return true;
        if (parsed instanceof KakaoResponse kr) {
            return kr.getDocuments() == null || kr.getDocuments().isEmpty();
        }
        if (parsed instanceof KakaoKeywordResponse kw) {
            return kw.getDocuments() == null || kw.getDocuments().isEmpty();
        }
        if (parsed instanceof KakaoCoord2AddressResponse c2a) {
            return c2a.getDocuments() == null || c2a.getDocuments().isEmpty();
        }
        return false;
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

    // =========================
    // 2) Resolve (전처리/후보/보강)
    // =========================

    public AddressPickResult resolve(String rawAddress) {
        String raw = rawAddress == null ? "" : rawAddress.trim();
        if (!StringUtils.hasText(raw)) return AddressPickResult.empty("");

        if (throttleMs > 0) {
            try { TimeUnit.MILLISECONDS.sleep(throttleMs); } catch (InterruptedException ignored) {}
        }

        // 1) clean
        String cleaned = AddressPreprocessor.clean(raw);

        // 2) 괄호/노이즈 상세
        String parenDetail = AddressPreprocessor.extractParenDetail(cleaned);
        String noParen = AddressPreprocessor.stripParen(cleaned);

        // ✅ 3) 콤마 뒤는 무조건 상세로 이동
        String commaTail = extractCommaTail(noParen);
        String noComma = stripAfterComma(noParen);

        // 4) 기타 상세/노이즈
        String noiseDetail = AddressPreprocessor.extractNoiseToDetail(noComma);
        String base = AddressPreprocessor.stripNoise(noComma);

        // 5) 끝 숫자 상세 분리
        AddressPreprocessor.QueryAndDetail qd = AddressPreprocessor.separateTrailingUnitNumber(base);
        String query0 = safe(qd.getQuery());
        String tailUnit = safe(qd.getDetail());

        // 후보 쿼리들
        String query1 = AddressPreprocessor.normalizeRoadSpacing(query0);
        String query2 = AddressPreprocessor.splitNumberGa(query1);
        String queryCompact = AddressPreprocessor.buildCompactQuery(query2, 40);
        String jibunQuery = AddressPreprocessor.buildJibunQuery(query2);

        // 상세 합치기
        String detail = AddressNormalizer.mergeDetails(parenDetail, commaTail, noiseDetail, tailUnit);

        List<String> candidates = dedupKeepOrder(Arrays.asList(
            query2, query1, query0, jibunQuery, queryCompact
        ));

        // AD 먼저
        for (String q : candidates) {
            if (!StringUtils.hasText(q)) continue;

            KakaoResponse resp = searchAddress(q);
            KakaoDocument best = AddressNormalizer.pickBest(resp, q);
            if (best == null) continue;

            AddressPickResult r = AddressPickResult.from(best, detail);

            // ✅ 보강: zip/주소가 비면 C2A
            boolean needC2A = isBlank(r.getZip()) || (isBlank(r.getRoadAddress()) && isBlank(r.getJibunAddress()));
            if (needC2A && StringUtils.hasText(best.getX()) && StringUtils.hasText(best.getY())) {
                KakaoCoord2AddressResponse c2a = coord2Address(best.getX(), best.getY());
                AddressNormalizer.NormalizedAddress na = AddressNormalizer.fromCoord2Address(c2a);
                if (na != null) {
                    if (isBlank(r.getZip())) r.setZip(safe(na.getZipCode()));
                    if (isBlank(r.getRoadAddress())) r.setRoadAddress(safe(na.getRoadAddress()));
                    if (isBlank(r.getJibunAddress())) r.setJibunAddress(safe(na.getJibunAddress()));
                    if (isBlank(r.getDoName())) r.setDoName(safe(na.getDoName()));
                    if (isBlank(r.getSiName())) r.setSiName(safe(na.getSiName()));
                    if (isBlank(r.getGuName())) r.setGuName(safe(na.getGuName()));
                }
            }

            // 최종 성공
            if (!isBlank(r.getRoadAddress()) || !isBlank(r.getJibunAddress())) {
                r.setSuccess(true);
                log.debug("[RES] OK via AD q='{}' zip='{}' do='{}' si='{}' gu='{}' road='{}' jibun='{}' detail='{}'",
                    q, r.getZip(), r.getDoName(), r.getSiName(), r.getGuName(), r.getRoadAddress(), r.getJibunAddress(), r.getDetailAddress());
                return r;
            }
        }

        // KW → C2A
        String kwQuery = StringUtils.hasText(queryCompact) ? queryCompact : query2;
        KakaoKeywordResponse kw = searchKeyword(kwQuery);
        KakaoKeywordDoc kd = (kw != null && kw.getDocuments() != null && !kw.getDocuments().isEmpty())
            ? kw.getDocuments().get(0) : null;

        if (kd != null && StringUtils.hasText(kd.getX()) && StringUtils.hasText(kd.getY())) {
            KakaoCoord2AddressResponse c2a = coord2Address(kd.getX(), kd.getY());
            AddressNormalizer.NormalizedAddress na = AddressNormalizer.fromCoord2Address(c2a);
            if (na != null) {
                AddressPickResult r = AddressPickResult.from(na, detail);
                r.setSuccess(true);
                log.debug("[RES] OK via KW->C2A kw='{}' zip='{}' do='{}' si='{}' gu='{}' road='{}' jibun='{}' detail='{}'",
                    kwQuery, r.getZip(), r.getDoName(), r.getSiName(), r.getGuName(), r.getRoadAddress(), r.getJibunAddress(), r.getDetailAddress());
                return r;
            }
        }

     // 실패: 카카오 documents=[] 인 경우라도 최소 도/시/구는 로컬 파싱으로 채움
        AddressFallbackParser.FallbackResult fb = AddressFallbackParser.parse(cleaned);

        // cleaned는 콤마/괄호 포함 원문이므로, query 본문(콤마/괄호 제거된 noComma 등)을 쓰셔도 됩니다.
        // 저는 cleaned로 두되, 아래에서 road/jibun guess는 내부에서 괄호/콤마 제거합니다.
        AddressPickResult fallback = AddressPickResult.empty(detail);

        // ✅ 최소 행정구역 채우기
        fallback.setDoName(fb.doName);
        fallback.setSiName(fb.siName);
        fallback.setGuName(fb.guName);

        // ✅ 주소 형태 guess (카카오가 못찾아도 원문을 기반으로라도 넣어줌)
        if (fallback.getRoadAddress() == null || fallback.getRoadAddress().isBlank()) {
            fallback.setRoadAddress(fb.roadGuess);
        }
        if (fallback.getJibunAddress() == null || fallback.getJibunAddress().isBlank()) {
            fallback.setJibunAddress(fb.jibunGuess);
        }

        // ✅ 여기 중요: 엑셀에 값이 들어가려면 success=true 여야 합니다.
        // (현재 서비스 로직이 res.isSuccess()일 때만 칼럼을 채우니까요)
        boolean hasAny =
                !(fallback.getDoName() == null || fallback.getDoName().isBlank()) ||
                !(fallback.getGuName() == null || fallback.getGuName().isBlank()) ||
                !(fallback.getRoadAddress() == null || fallback.getRoadAddress().isBlank()) ||
                !(fallback.getJibunAddress() == null || fallback.getJibunAddress().isBlank());

        fallback.setSuccess(hasAny);

        log.debug("[RES] FAIL(KAKAO_EMPTY) raw='{}' cleaned='{}' detail='{}' -> fallback do='{}' si='{}' gu='{}' road='{}' jibun='{}'",
                raw, cleaned, detail, fallback.getDoName(), fallback.getSiName(), fallback.getGuName(),
                fallback.getRoadAddress(), fallback.getJibunAddress());

        return fallback;

    }

    // =========================
    // 3) comma util (resolve에서 확실히 적용)
    // =========================

    private String extractCommaTail(String s) {
        if (s == null) return "";
        int idx = s.indexOf(',');
        if (idx > -1 && idx < s.length() - 1) {
            return s.substring(idx + 1).trim();
        }
        return "";
    }

    private String stripAfterComma(String s) {
        if (s == null) return "";
        int idx = s.indexOf(',');
        return idx > -1 ? s.substring(0, idx).trim() : s.trim();
    }

    // =========================
    // 4) common util
    // =========================

    private List<String> dedupKeepOrder(List<String> list) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : list) {
            if (StringUtils.hasText(s)) set.add(s.trim());
        }
        return new ArrayList<>(set);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}