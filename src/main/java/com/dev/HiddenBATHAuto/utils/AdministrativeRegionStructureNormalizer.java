package com.dev.HiddenBATHAuto.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 주소 입력값을 프로젝트의 Province - City - District 저장 구조로 정리합니다.
 *
 * <p>외부 주소 API마다 {@code sigungu}, {@code bname}, 전체 주소의 구성이 달라
 * 같은 행정구역이 City 또는 District 칸으로 뒤바뀌지 않도록 저장 직전에
 * 공통으로 적용하는 구조 정규화 규칙입니다.</p>
 *
 * <ul>
 *     <li>서울특별시 관악구 -> 서울특별시 / 빈값 / 관악구</li>
 *     <li>경기도 이천시 -> 경기도 / 이천시 / 빈값</li>
 *     <li>경기도 용인시 수지구 -> 경기도 / 용인시 / 수지구</li>
 *     <li>인천광역시 강화군 -> 인천광역시 / 빈값 / 강화군</li>
 * </ul>
 */
public final class AdministrativeRegionStructureNormalizer {

    private static final int ROAD_PREFIX_TOKEN_LIMIT = 5;

    private AdministrativeRegionStructureNormalizer() {
    }

    public static NormalizedRegion normalize(
            String doName,
            String siName,
            String guName,
            String roadAddress
    ) {
        String province = resolveProvince(doName, roadAddress);
        boolean metropolitan = isMetropolitanProvince(province);

        List<String> siTokens = fieldTokens(siName, province);
        List<String> guTokens = fieldTokens(guName, province);
        List<String> roadTokens = roadPrefixTokens(roadAddress, province);
        boolean authoritativeRoad = startsWithProvince(roadAddress, province);

        String city = "";
        String district = "";

        if (metropolitan) {
            // 특별시/광역시/특별자치시는 Province 바로 아래에 구/군이 옵니다.
            district = firstEndingWith(roadTokens, "구", "군");
            if (district.isBlank() && !authoritativeRoad) {
                district = firstEndingWith(guTokens, "구", "군");
            }
            if (district.isBlank() && !authoritativeRoad) {
                district = firstEndingWith(siTokens, "구", "군");
            }
        } else {
            // 도 아래의 시/군은 City 단계입니다. guName으로 잘못 넘어온 값도 복구합니다.
            city = firstEndingWith(roadTokens, "시", "군");
            if (city.isBlank() && !authoritativeRoad) {
                city = firstEndingWith(siTokens, "시", "군");
            }
            if (city.isBlank() && !authoritativeRoad) {
                city = firstEndingWith(guTokens, "시", "군");
            }

            // 도 지역의 District에는 시/군을 허용하지 않고 구만 저장합니다.
            district = firstEndingWith(roadTokens, "구");
            if (district.isBlank() && !authoritativeRoad) {
                district = firstEndingWith(guTokens, "구");
            }
            if (district.isBlank() && !authoritativeRoad) {
                district = firstEndingWith(siTokens, "구");
            }
        }

        if (!city.isBlank() && city.equals(district)) {
            district = "";
        }

        return new NormalizedRegion(province, city, district);
    }

    private static String resolveProvince(String doName, String roadAddress) {
        String requested = KoreanAdministrativeRegionNormalizer.canonicalProvinceName(doName);
        List<String> roadTokens = rawTokens(roadAddress);
        int limit = Math.min(roadTokens.size(), 2);
        for (int i = 0; i < limit; i++) {
            String token = roadTokens.get(i);
            String candidate = KoreanAdministrativeRegionNormalizer.canonicalProvinceName(token);
            if (KoreanAdministrativeRegionNormalizer.isKnownProvince(candidate)) {
                return candidate;
            }
        }

        if (KoreanAdministrativeRegionNormalizer.isKnownProvince(requested)) {
            return requested;
        }

        if (!requested.isBlank()) {
            return requested;
        }

        return roadTokens.isEmpty()
                ? ""
                : KoreanAdministrativeRegionNormalizer.canonicalProvinceName(roadTokens.get(0));
    }

    private static boolean isMetropolitanProvince(String province) {
        if (KoreanAdministrativeRegionNormalizer.isMetropolitanProvince(province)) {
            return true;
        }

        String value = clean(province);
        return value.endsWith("특별시")
                || value.endsWith("광역시")
                || value.endsWith("특별자치시");
    }

    private static boolean startsWithProvince(String roadAddress, String province) {
        String expected = KoreanAdministrativeRegionNormalizer.canonicalProvinceName(province);
        if (expected.isBlank()) {
            return false;
        }

        List<String> tokens = rawTokens(roadAddress);
        int limit = Math.min(tokens.size(), 2);
        for (int i = 0; i < limit; i++) {
            String candidate = KoreanAdministrativeRegionNormalizer.canonicalProvinceName(tokens.get(i));
            if (expected.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> fieldTokens(String value, String province) {
        Set<String> result = new LinkedHashSet<>();
        for (String token : rawTokens(value)) {
            addRegionToken(result, token, province);
        }
        return new ArrayList<>(result);
    }

    private static List<String> roadPrefixTokens(String value, String province) {
        Set<String> result = new LinkedHashSet<>();
        List<String> tokens = rawTokens(value);
        int limit = Math.min(tokens.size(), ROAD_PREFIX_TOKEN_LIMIT);

        for (int i = 0; i < limit; i++) {
            addRegionToken(result, tokens.get(i), province);
        }

        return new ArrayList<>(result);
    }

    private static void addRegionToken(Set<String> target, String value, String province) {
        String token = cleanToken(value);
        if (token.isBlank() || isSameProvince(token, province)) {
            return;
        }
        target.add(token);
    }

    private static boolean isSameProvince(String value, String province) {
        if (clean(province).isBlank()) {
            return false;
        }

        String left = KoreanAdministrativeRegionNormalizer.canonicalProvinceName(value);
        String right = KoreanAdministrativeRegionNormalizer.canonicalProvinceName(province);
        return !left.isBlank() && left.equals(right);
    }

    private static String firstEndingWith(List<String> values, String... suffixes) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        for (String value : values) {
            for (String suffix : suffixes) {
                if (value.endsWith(suffix)) {
                    return value;
                }
            }
        }
        return "";
    }

    private static List<String> rawTokens(String value) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            return List.of();
        }

        String[] split = cleaned.split("\\s+");
        List<String> result = new ArrayList<>(split.length);
        for (String token : split) {
            String cleanedToken = cleanToken(token);
            if (!cleanedToken.isBlank()) {
                result.add(cleanedToken);
            }
        }
        return result;
    }

    private static String cleanToken(String value) {
        return clean(value)
                .replaceAll("^[\\(\\[\\{,]+", "")
                .replaceAll("[\\)\\]\\},]+$", "");
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .trim()
                .replaceAll("\\s+", " ");
        if ("-".equals(cleaned) || "NULL".equalsIgnoreCase(cleaned)) {
            return "";
        }
        return cleaned;
    }

    public record NormalizedRegion(
            String doName,
            String siName,
            String guName
    ) {
    }
}
