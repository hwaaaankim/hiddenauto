package com.dev.HiddenBATHAuto.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 배송지 표시와 동일배송지 묶음 키가 서로 다른 규칙을 사용하지 않도록 하는 공통 유틸리티입니다.
 *
 * <p>핵심 규칙:</p>
 * <ul>
 *     <li>Unicode NFKC 정규화</li>
 *     <li>주소 필드에 단독으로 저장된 '-', '－', '—' 등의 placeholder 제거</li>
 *     <li>'- 서울 ...'처럼 주소 앞에 붙은 구분용 하이픈 제거</li>
 *     <li>'398-1'처럼 숫자 사이의 실제 번지 하이픈은 보존</li>
 *     <li>도로명주소가 있으면 우편번호 입력 유무는 동일배송지 판단에서 제외</li>
 *     <li>시/도 축약명과 정식명칭(예: 경기/경기도)은 동일한 주소 키로 처리</li>
 *     <li>roadAddress 선두의 시/도 표기도 축약/정식명칭 차이를 동일하게 처리</li>
 *     <li>화면 표시값은 행정구역 메타데이터를 다시 붙이지 않고 roadAddress + detailAddress만 사용</li>
 *     <li>roadAddress에 상세주소가 이미 포함된 경우 detailAddress 중복 제거</li>
 * </ul>
 */
public final class DeliveryAddressNormalizationUtil {

    private static final Pattern ZIP_CODE_PATTERN = Pattern.compile("(?<!\\d)(\\d{5})(?!\\d)");
    private static final Pattern ZIP_IN_PARENTHESES_PATTERN = Pattern.compile("\\(\\s*\\d{5}\\s*\\)");
    private static final Pattern NUMERIC_HYPHEN_PATTERN = Pattern.compile("(?<=\\d)\\s*-\\s*(?=\\d)");
    private static final Pattern STANDALONE_HYPHEN_PATTERN = Pattern.compile("(^|\\s)-+(?=\\s|$)");
    private static final Pattern LEADING_HYPHEN_PATTERN = Pattern.compile("^-+\\s*");
    private static final Pattern TRAILING_HYPHEN_PATTERN = Pattern.compile("\\s*-+$");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
            "^(?:-|--|없음|null|미입력|해당없음|해당 없음)$",
            Pattern.CASE_INSENSITIVE
    );

    private DeliveryAddressNormalizationUtil() {
    }

    public static AddressValue build(
            String zipCode,
            String doName,
            String siName,
            String guName,
            String roadAddress,
            String detailAddress
    ) {
        String cleanZip = cleanZipCode(zipCode);
        String cleanDo = cleanAddressComponent(doName);
        String cleanSi = cleanAddressComponent(siName);
        String cleanGu = cleanAddressComponent(guName);
        String cleanRoad = cleanAddressComponent(roadAddress);
        String cleanDetail = cleanAddressComponent(detailAddress);

        String normalizedDo = normalizeProvinceKeyPart(cleanDo);
        String normalizedSi = normalizeAddressKeyPart(cleanSi);
        String normalizedGu = normalizeAddressKeyPart(cleanGu);
        String normalizedRegion = normalizedDo + normalizedSi + normalizedGu;
        String normalizedRoad = normalizeRoadAddressKeyPart(cleanRoad);
        String normalizedDetail = normalizeAddressKeyPart(cleanDetail);
        String normalizedZip = normalizeAddressKeyPart(cleanZip);

        StringBuilder missingRegionKeyPrefix = new StringBuilder();

        if (!normalizedDo.isBlank() && !roadContainsSameProvince(cleanRoad, normalizedDo)) {
            missingRegionKeyPrefix.append(normalizedDo);
        }
        if (!normalizedSi.isBlank() && !normalizedRoad.contains(normalizedSi)) {
            missingRegionKeyPrefix.append(normalizedSi);
        }
        if (!normalizedGu.isBlank() && !normalizedRoad.contains(normalizedGu)) {
            missingRegionKeyPrefix.append(normalizedGu);
        }

        boolean roadAlreadyContainsDetail = !normalizedRoad.isBlank()
                && !normalizedDetail.isBlank()
                && normalizedRoad.endsWith(normalizedDetail);

        List<String> displayParts = new ArrayList<>();
        if (!cleanRoad.isBlank()) {
            displayParts.add(cleanRoad);
        }
        if (!cleanDetail.isBlank() && !roadAlreadyContainsDetail) {
            displayParts.add(cleanDetail);
        }

        String key;

        if (!normalizedRoad.isBlank()) {
            StringBuilder canonical = new StringBuilder(missingRegionKeyPrefix)
                    .append(normalizedRoad);

            if (!normalizedDetail.isBlank() && !canonical.toString().endsWith(normalizedDetail)) {
                canonical.append(normalizedDetail);
            }

            key = "ROAD|" + canonical;
        } else if (!normalizedRegion.isBlank()) {
            StringBuilder canonical = new StringBuilder(normalizedRegion);

            if (!normalizedDetail.isBlank() && !canonical.toString().endsWith(normalizedDetail)) {
                canonical.append(normalizedDetail);
            }

            key = "REGION|" + canonical;
        } else if (!normalizedDetail.isBlank()) {
            key = "DETAIL|" + normalizedDetail + "|" + normalizedZip;
        } else if (!normalizedZip.isBlank()) {
            key = "ZIP|" + normalizedZip;
        } else {
            key = "";
        }

        return new AddressValue(
                key,
                displayParts.isEmpty() ? "-" : String.join(" ", displayParts),
                cleanZip
        );
    }

    public static boolean hasAnyMeaningfulAddressText(String... values) {
        if (values == null) {
            return false;
        }

        for (String value : values) {
            if (!cleanAddressComponent(value).isBlank()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 화면 표시에도 사용할 수 있도록 placeholder 하이픈을 제거한 주소 필드입니다.
     */
    public static String cleanAddressComponent(String value) {
        if (value == null) {
            return "";
        }

        String text = normalizeUnicodeAndHyphens(value);

        if (PLACEHOLDER_PATTERN.matcher(text.trim()).matches()) {
            return "";
        }

        // 숫자 사이의 하이픈은 먼저 붙여서 번지 표기를 보호합니다. 예: 398 - 1 -> 398-1
        text = NUMERIC_HYPHEN_PATTERN.matcher(text).replaceAll("-");

        // 필드 자체 또는 토큰 사이에 들어간 구분용 단독 하이픈만 제거합니다.
        text = STANDALONE_HYPHEN_PATTERN.matcher(text).replaceAll("$1");
        text = LEADING_HYPHEN_PATTERN.matcher(text).replaceFirst("");
        text = TRAILING_HYPHEN_PATTERN.matcher(text).replaceFirst("");
        text = text.replaceAll("\\s+", " ").trim();

        return PLACEHOLDER_PATTERN.matcher(text).matches() ? "" : text;
    }

    private static String normalizeProvinceKeyPart(String value) {
        String clean = cleanAddressComponent(value);
        if (clean.isBlank()) {
            return "";
        }

        if (KoreanAdministrativeRegionNormalizer.isKnownProvince(clean)) {
            return normalizeAddressKeyPart(KoreanAdministrativeRegionNormalizer.provinceMatchKey(clean));
        }

        return normalizeAddressKeyPart(KoreanAdministrativeRegionNormalizer.canonicalProvinceName(clean));
    }

    /**
     * roadAddress의 첫 토큰이 대한민국 시/도라면 프로젝트 공통 비교키(경기도 -> 경기)로 치환합니다.
     * 따라서 외부 주소 API가 "경기 ..."를 주고 DB 메타데이터가 "경기도"인 경우에도 동일 주소가 됩니다.
     */
    private static String normalizeRoadAddressKeyPart(String roadAddress) {
        String cleanRoad = cleanAddressComponent(roadAddress);
        if (cleanRoad.isBlank()) {
            return "";
        }

        String[] tokens = cleanRoad.split("\\s+", 2);
        String firstToken = tokens[0];

        if (KoreanAdministrativeRegionNormalizer.isKnownProvince(firstToken)) {
            String provinceKey = KoreanAdministrativeRegionNormalizer.provinceMatchKey(firstToken);
            cleanRoad = tokens.length > 1
                    ? provinceKey + " " + tokens[1]
                    : provinceKey;
        }

        return normalizeAddressKeyPart(cleanRoad);
    }

    private static boolean roadContainsSameProvince(String roadAddress, String normalizedProvinceKey) {
        if (normalizedProvinceKey == null || normalizedProvinceKey.isBlank()) {
            return true;
        }

        String cleanRoad = cleanAddressComponent(roadAddress);
        if (cleanRoad.isBlank()) {
            return false;
        }

        String[] tokens = cleanRoad.split("\\s+", 2);
        String firstToken = tokens[0];

        if (KoreanAdministrativeRegionNormalizer.isKnownProvince(firstToken)) {
            String roadProvinceKey = normalizeAddressKeyPart(
                    KoreanAdministrativeRegionNormalizer.provinceMatchKey(firstToken)
            );
            return normalizedProvinceKey.equals(roadProvinceKey);
        }

        // 알 수 없는 행정구역명은 기존 데이터 호환성을 위해 정규화된 road 선두 비교를 허용합니다.
        return normalizeRoadAddressKeyPart(cleanRoad).startsWith(normalizedProvinceKey);
    }

    private static String normalizeAddressKeyPart(String value) {
        String normalized = cleanAddressComponent(value).toLowerCase(Locale.ROOT);

        normalized = ZIP_IN_PARENTHESES_PATTERN.matcher(normalized).replaceAll("");

        // 숫자-숫자 형태의 번지 하이픈만 남기고 나머지 구분용 하이픈은 제거합니다.
        normalized = normalized
                .replaceAll("(?<!\\d)-(?!\\d)", "")
                .replaceAll("(?<!\\d)-(?=\\d)", "")
                .replaceAll("(?<=\\d)-(?!\\d)", "")
                .replaceAll("[\\s,·ㆍ:;]+", "")
                .replaceAll("[\\[\\](){}]", "")
                .trim();

        return normalized;
    }

    private static String cleanZipCode(String value) {
        String text = cleanAddressComponent(value);

        if (text.isBlank()) {
            return "";
        }

        Matcher matcher = ZIP_CODE_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : text;
    }

    private static String normalizeUnicodeAndHyphens(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2012', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2015', '-')
                .replace('\u2212', '-')
                .replace('\uFE58', '-')
                .replace('\uFE63', '-')
                .replace('\uFF0D', '-')
                .replace('\u00A0', ' ')
                .trim();
    }

    public record AddressValue(String key, String display, String zipCode) {
    }
}
