package com.dev.HiddenBATHAuto.utils;

import java.text.Normalizer;
import java.util.Map;
import java.util.Set;

/**
 * 대한민국 시/도 명칭을 현재 프로젝트의 행정구역 DB 기준 명칭으로 정규화합니다.
 *
 * <p>외부 주소 API/Daum 우편번호 서비스가 반환하는 축약명 또는 변경 명칭과
 * tb_province에 저장된 기존 명칭이 달라도 동일 시/도로 비교하기 위한 공통 규칙입니다.</p>
 *
 * <p>특히 광주 관련 입력은 아래 값을 모두 {@code 광주광역시}로 취급합니다.</p>
 * <ul>
 *     <li>광주</li>
 *     <li>광주시</li>
 *     <li>광주광역시</li>
 *     <li>전남광주통합특별시 (공백 포함 변형 포함)</li>
 *     <li>광주전남통합특별시 (공백 포함 변형 포함)</li>
 * </ul>
 *
 * <p>DB 데이터를 일괄 변경하지 않고 입력/검증/담당자 매칭 경계에서만 정규화합니다.</p>
 */
public final class KoreanAdministrativeRegionNormalizer {

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
            Map.entry("전남광주통합특별시", "광주광역시"),
            Map.entry("광주전남통합특별시", "광주광역시"),
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

    private static final Set<String> CANONICAL_PROVINCES = Set.of(
            "서울특별시",
            "부산광역시",
            "대구광역시",
            "인천광역시",
            "광주광역시",
            "대전광역시",
            "울산광역시",
            "세종특별자치시",
            "경기도",
            "강원특별자치도",
            "충청북도",
            "충청남도",
            "전북특별자치도",
            "전라남도",
            "경상북도",
            "경상남도",
            "제주특별자치도"
    );

    private static final Set<String> METROPOLITAN_PROVINCES = Set.of(
            "서울특별시",
            "부산광역시",
            "대구광역시",
            "인천광역시",
            "광주광역시",
            "대전광역시",
            "울산광역시",
            "세종특별자치시"
    );

    private KoreanAdministrativeRegionNormalizer() {
    }

    /**
     * 시/도 값을 프로젝트의 canonical 명칭으로 변환합니다.
     * 알 수 없는 값은 손실 없이 공백만 정리한 원문을 반환합니다.
     */
    public static String canonicalProvinceName(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            return "";
        }

        String compact = compact(cleaned);
        String aliased = PROVINCE_ALIASES.get(compact);
        if (aliased != null) {
            return aliased;
        }

        // Daum 주소검색 등 외부 주소 소스에서 공백/접두어가 달라져도
        // "광주 + 통합특별시" 조합이면 기존 DB의 광주광역시와 동일 지역으로 취급합니다.
        if (compact.contains("광주") && compact.contains("통합특별시")) {
            return "광주광역시";
        }

        return cleaned;
    }

    public static boolean isKnownProvince(String value) {
        return CANONICAL_PROVINCES.contains(canonicalProvinceName(value));
    }

    public static boolean isMetropolitanProvince(String value) {
        return METROPOLITAN_PROVINCES.contains(canonicalProvinceName(value));
    }

    public static boolean isSejong(String value) {
        return "세종특별자치시".equals(canonicalProvinceName(value));
    }

    /**
     * Province 엔티티명과 유연 비교할 때 사용하는 핵심 키입니다.
     * 예: 광주시/광주광역시/전남광주통합특별시 -> 광주
     */
    public static String provinceMatchKey(String value) {
        String canonical = canonicalProvinceName(value);
        if (canonical.isBlank()) {
            return "";
        }

        return switch (canonical) {
            case "서울특별시" -> "서울";
            case "부산광역시" -> "부산";
            case "대구광역시" -> "대구";
            case "인천광역시" -> "인천";
            case "광주광역시" -> "광주";
            case "대전광역시" -> "대전";
            case "울산광역시" -> "울산";
            case "세종특별자치시" -> "세종";
            case "경기도" -> "경기";
            case "강원특별자치도" -> "강원";
            case "충청북도" -> "충북";
            case "충청남도" -> "충남";
            case "전북특별자치도" -> "전북";
            case "전라남도" -> "전남";
            case "경상북도" -> "경북";
            case "경상남도" -> "경남";
            case "제주특별자치도" -> "제주";
            default -> stripAdministrativeSuffix(compact(canonical));
        };
    }

    private static String stripAdministrativeSuffix(String value) {
        String[] suffixes = {
                "특별자치도",
                "특별자치시",
                "통합특별시",
                "광역시",
                "특별시",
                "자치시",
                "자치구",
                "자치군",
                "도",
                "시",
                "군",
                "구"
        };

        for (String suffix : suffixes) {
            if (value.endsWith(suffix) && value.length() > suffix.length()) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private static String compact(String value) {
        return clean(value).replaceAll("\\s+", "");
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }
}
