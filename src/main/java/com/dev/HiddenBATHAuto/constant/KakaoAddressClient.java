package com.dev.HiddenBATHAuto.constant;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.dev.HiddenBATHAuto.dto.excel.AddressPickResult;
import com.dev.HiddenBATHAuto.dto.excel.KakaoDocument;
import com.dev.HiddenBATHAuto.dto.excel.KakaoKeywordDoc;
import com.dev.HiddenBATHAuto.dto.excel.KakaoKeywordResponse;
import com.dev.HiddenBATHAuto.dto.excel.KakaoResponse;
import com.dev.HiddenBATHAuto.utils.AddressNormalizer;
import com.dev.HiddenBATHAuto.utils.AddressPreprocessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
@Slf4j
public class KakaoAddressClient {

	private final WebClient kakaoWebClient;

    @Value("${kakao.rest.api-key}")
    private String apiKey;

    @Value("${kakao.throttle-ms:120}")
    private long throttleMs;

    private String auth() {
        return "KakaoAK " + apiKey;
    }

    public KakaoResponse searchAddress(String query) {
        String q = query == null ? "" : query.trim();

        Mono<KakaoResponse> call = kakaoWebClient.get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v2/local/search/address.json")
                    .queryParam("analyze_type", "similar")
                    .queryParam("size", 10)
                    .queryParam("query", q) // ← enc(...) 붙이지 않음
                    .build()
            )
            .header(HttpHeaders.AUTHORIZATION, auth())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, resp ->
                resp.bodyToMono(String.class).flatMap(body -> {
                    log.warn("Kakao address 4xx: {}", body);
                    return Mono.error(new RuntimeException("ADDR_4XX"));
                })
            )
            .onStatus(HttpStatusCode::is5xxServerError, resp ->
                Mono.error(new RuntimeException("ADDR_5XX"))
            )
            .bodyToMono(KakaoResponse.class)
            .onErrorReturn(new KakaoResponse());

        return Mono.delay(Duration.ofMillis(Math.max(0, throttleMs)))
                .then(call)
                .block(Duration.ofSeconds(6));
    }

    public KakaoKeywordResponse searchKeyword(String compactQuery) {
        // 1) 1차 압축 (토큰/잡음 제거는 기존 AddressPreprocessor에서)
        String compact = AddressPreprocessor.buildCompactQuery(compactQuery, 100);
        // 2) 보수적 2차 절단 (코드포인트 기준)
        String q = AddressPreprocessor.cutByCodePoint(compact, 60);

        // 디버그 로그
        log.debug("[KW] q='{}' (len={})", q, q.codePointCount(0, q.length()));

        Mono<KakaoKeywordResponse> call = kakaoWebClient.get()
            .uri(uriBuilder -> uriBuilder
                    .path("/v2/local/search/keyword.json")
                    .queryParam("size", 10)
                    .queryParam("query", q) // ← 직접 인코딩 붙이지 않음
                    .build()
            )
            .header(HttpHeaders.AUTHORIZATION, auth())
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, resp ->
                resp.bodyToMono(String.class).flatMap(body -> {
                    log.warn("Kakao keyword 4xx: {}", body);
                    return Mono.error(new RuntimeException("KW_4XX"));
                })
            )
            .onStatus(HttpStatusCode::is5xxServerError, resp ->
                Mono.error(new RuntimeException("KW_5XX"))
            )
            .bodyToMono(KakaoKeywordResponse.class)
            .onErrorReturn(new KakaoKeywordResponse());

        return Mono.delay(Duration.ofMillis(Math.max(0, throttleMs)))
                .then(call)
                .block(Duration.ofSeconds(6));
    }

    /** 통합 검색: 전처리 → variant 순서대로 호출 */
    public AddressPickResult resolve(String raw) {
        // 1) 정규화
        String cleaned     = AddressPreprocessor.clean(raw);
        String parenDetail = AddressPreprocessor.extractParenDetail(cleaned);
        String stripped    = AddressPreprocessor.stripParen(cleaned);
        String extraDetail = AddressPreprocessor.extractCommaTailDetail(stripped);
        String base        = AddressPreprocessor.stripAfterComma(stripped);

        // 도로명 띄우기 + 잡음 제거 + 도 보강 + 'N가' 제거
        String qRoad = AddressPreprocessor.removeGaAfterRoad(AddressPreprocessor.ensureProvince(
                AddressPreprocessor.stripNoise(AddressPreprocessor.normalizeRoadSpacing(base))
        ));
        // 지번(동/리 + 지번)만 뽑기
        String qJibun = AddressPreprocessor.buildJibunQuery(base);

        // V1: 도로명형
        KakaoResponse r1 = searchAddress(qRoad);
        KakaoDocument best1 = AddressNormalizer.pickBest(r1, qRoad);

        if (best1 != null) {
            return AddressPickResult.from(best1, parenDetail, extraDetail,
                    AddressPreprocessor.extractNoiseToDetail(base));
        }

        // V2: 지번형
        if (!qJibun.isBlank() && !qJibun.equals(qRoad)) {
            KakaoResponse r2 = searchAddress(qJibun);
            KakaoDocument best2 = AddressNormalizer.pickBest(r2, qJibun);
            if (best2 != null) {
                return AddressPickResult.from(best2, parenDetail, extraDetail,
                        AddressPreprocessor.extractNoiseToDetail(base));
            }
        }

        // V3: 키워드
        String kw = AddressPreprocessor.buildCompactQuery(qRoad, 100);
        KakaoKeywordResponse kresp = searchKeyword(kw);
        List<KakaoKeywordDoc> ks = kresp.getDocuments();
        if (ks != null && !ks.isEmpty()) {
            AddressNormalizer.NormalizedAddress na = AddressNormalizer.fromKeyword(ks.get(0));
            if (na != null) return AddressPickResult.from(na, parenDetail, extraDetail,
                    AddressPreprocessor.extractNoiseToDetail(base));
        }

        // 실패
        return AddressPickResult.empty(parenDetail, extraDetail, AddressPreprocessor.extractNoiseToDetail(base));
    }
}