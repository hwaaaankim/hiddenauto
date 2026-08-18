package com.dev.HiddenBATHAuto.utils;

import java.util.Locale;

/**
 * OrderItem.productName 표시 전용 유틸입니다.
 *
 * <p>엑셀 발주 등록 시 productName은 다음 형태로 저장될 수 있습니다.</p>
 *
 * <pre>
 * 표시용제품명[엑셀원문]
 * </pre>
 *
 * <p>예)</p>
 * <pre>
 * [세면기] 아메리칸 / 네오모던 탑볼 / CCASF633[[세면기] 아메리칸 / 네오모던 탑볼 / CCASF633]
 * </pre>
 *
 * <p>이 경우 화면에서는 저장용으로 붙어 있는 가장 바깥 대괄호를 제거하고,
 * 그 안의 엑셀 원문인 {@code [세면기] 아메리칸 / 네오모던 탑볼 / CCASF633}만 표시합니다.</p>
 *
 * <p>단순히 문자열의 마지막 대괄호를 무조건 제거하지 않습니다.
 * 자연스러운 제품명 자체가 {@code 제품 [NEW]}처럼 끝날 수도 있으므로,
 * 마지막 바깥 대괄호 내용이 앞쪽 표시명과 실제로 연관된 경우에만
 * 엑셀 저장용 원문 suffix로 판단합니다.</p>
 */
public final class OrderProductNameDisplayUtil {

    private OrderProductNameDisplayUtil() {
    }

    public static String toDisplayName(String rawProductName) {
        String value = normalizeWhitespace(rawProductName);
        if (value == null) {
            return null;
        }

        int lastIndex = value.length() - 1;
        if (lastIndex < 1 || value.charAt(lastIndex) != ']') {
            return value;
        }

        int openingIndex = findMatchingOpeningBracket(value, lastIndex);
        if (openingIndex <= 0) {
            return value;
        }

        String displayPart = value.substring(0, openingIndex).trim();
        String originalPart = value.substring(openingIndex + 1, lastIndex).trim();

        if (displayPart.isBlank() || originalPart.isBlank()) {
            return value;
        }

        if (looksLikeSavedOriginalSuffix(displayPart, originalPart)) {
            return originalPart;
        }

        return value;
    }

    private static int findMatchingOpeningBracket(String value, int closingIndex) {
        int depth = 0;

        for (int index = closingIndex; index >= 0; index--) {
            char ch = value.charAt(index);

            if (ch == ']') {
                depth++;
            } else if (ch == '[') {
                depth--;
                if (depth == 0) {
                    return index;
                }
                if (depth < 0) {
                    return -1;
                }
            }
        }

        return -1;
    }

    private static boolean looksLikeSavedOriginalSuffix(String displayPart, String originalPart) {
        String displayComparable = comparable(displayPart);
        String originalComparable = comparable(originalPart);

        if (displayComparable.isBlank() || originalComparable.isBlank()) {
            return false;
        }

        // parser가 만든 원문 suffix는 앞쪽 표시명과 동일하거나,
        // 실제 제품명에 모델명/규격 문자열이 추가되어 표시명이 중간중간 끊겨 보이는 형태가 있습니다.
        // 예) "모듈 (1도어) (미니)[모듈MG-450장 (1도어) (미니)]"
        //     displayComparable = "모듈1도어미니"
        //     originalComparable = "모듈mg450장1도어미니"
        // 이 경우 단순 contains()로는 판별할 수 없으므로 display 쪽 문자가
        // original 안에 같은 순서로 모두 등장하는지도 확인합니다.
        return originalComparable.contains(displayComparable)
                || displayComparable.equals(originalComparable)
                || isSubsequence(displayComparable, originalComparable);
    }

    private static boolean isSubsequence(String candidate, String target) {
        if (candidate == null || target == null || candidate.isBlank() || target.isBlank()) {
            return false;
        }

        int candidateIndex = 0;

        for (int targetIndex = 0; targetIndex < target.length() && candidateIndex < candidate.length(); targetIndex++) {
            if (candidate.charAt(candidateIndex) == target.charAt(targetIndex)) {
                candidateIndex++;
            }
        }

        return candidateIndex == candidate.length();
    }

    private static String comparable(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("[^0-9A-Za-z가-힣]", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();

        return normalized.isBlank() ? null : normalized;
    }
}
