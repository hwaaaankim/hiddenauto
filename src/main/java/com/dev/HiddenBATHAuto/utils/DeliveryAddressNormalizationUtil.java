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

        String region = joinNonBlank(" ", cleanDo, cleanSi, cleanGu);
        String normalizedRoad = normalizeAddressKeyPart(cleanRoad);
        String normalizedRegion = normalizeAddressKeyPart(region);
        String normalizedDetail = normalizeAddressKeyPart(cleanDetail);
        String normalizedZip = normalizeAddressKeyPart(cleanZip);

        List<String> missingRegionDisplayParts = new ArrayList<>();
        StringBuilder missingRegionKeyPrefix = new StringBuilder();

        for (String regionPart : List.of(cleanDo, cleanSi, cleanGu)) {
            String normalizedPart = normalizeAddressKeyPart(regionPart);

            if (!normalizedPart.isBlank() && !normalizedRoad.contains(normalizedPart)) {
                missingRegionDisplayParts.add(regionPart);
                missingRegionKeyPrefix.append(normalizedPart);
            }
        }

        List<String> displayParts = new ArrayList<>();

        if (!cleanZip.isBlank()) {
            displayParts.add("(" + cleanZip + ")");
        }

        if (!cleanRoad.isBlank()) {
            if (!missingRegionDisplayParts.isEmpty()) {
                displayParts.add(String.join(" ", missingRegionDisplayParts));
            }
            displayParts.add(cleanRoad);
        } else if (!region.isBlank()) {
            displayParts.add(region);
        }

        boolean roadAlreadyContainsDetail = !normalizedRoad.isBlank()
                && !normalizedDetail.isBlank()
                && normalizedRoad.endsWith(normalizedDetail);

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

    private static String joinNonBlank(String delimiter, String... values) {
        List<String> parts = new ArrayList<>();

        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    parts.add(value);
                }
            }
        }

        return String.join(delimiter, parts);
    }

    public record AddressValue(String key, String display, String zipCode) {
    }
}
