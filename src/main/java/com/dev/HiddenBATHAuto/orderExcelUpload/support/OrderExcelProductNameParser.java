package com.dev.HiddenBATHAuto.orderExcelUpload.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class OrderExcelProductNameParser {

    private final List<ColorToken> colorTokens;

    public OrderExcelProductNameParser() {
        this.colorTokens = buildColorTokens();
    }

    public ParsedProductName parse(String originalItemName, String categoryName) {
        String original = safe(originalItemName);
        String category = normalizeCategory(categoryName);

        if (original.isBlank()) {
            return new ParsedProductName("", "", "");
        }

        ColorMatch colorMatch = findColor(original);
        String color = colorMatch == null ? "" : colorMatch.saveValue();

        String productName;
        if (isBathroomGoods(category)) {
            productName = original;
        } else {
            productName = original;

            if (colorMatch != null) {
                productName = removeColorToken(productName, colorMatch.token());
            }

            productName = productName
                    .replace("비규격", "")
                    .replace("규격", "")
                    .trim();

            if (isCabinet(category)) {
                productName = productName.replaceAll("\\d+\\s*[가-힣A-Za-z]*장", " ");
                productName = productName.replaceAll("(?i)(?<=\\D)\\d{2,5}(?=$|[-_\\s])", " ");
            } else if (isMirror(category)) {
                productName = removeMirrorSizeTokens(productName);
            }

            productName = cleanupProductName(productName);
        }

        if (productName.isBlank()) {
            productName = original;
        }

        String productNameForSave = productName + "[" + original + "]";
        return new ParsedProductName(productName, color, productNameForSave);
    }

    private ColorMatch findColor(String original) {
        String text = original == null ? "" : original;

        for (ColorToken token : colorTokens) {
            if (token.searchToken().isBlank()) {
                continue;
            }

            if (token.searchToken().length() == 1 && token.searchToken().matches("[A-Za-z]")) {
                if (matchesSingleLetterColor(text, token.searchToken())) {
                    return new ColorMatch(token.searchToken(), token.saveValue());
                }
                continue;
            }

            if (containsIgnoreCase(text, token.searchToken())) {
                return new ColorMatch(token.searchToken(), token.saveValue());
            }
        }

        return null;
    }

    private boolean matchesSingleLetterColor(String text, String token) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String upper = text.toUpperCase(Locale.ROOT);
        String t = token.toUpperCase(Locale.ROOT);

        List<Pattern> patterns = List.of(
                Pattern.compile("(^|[^A-Z가-힣])" + Pattern.quote(t) + "($|[^A-Z가-힣])"),
                Pattern.compile(Pattern.quote(t) + "[-_\\s]*\\d+"),
                Pattern.compile("\\d+[-_\\s]*" + Pattern.quote(t) + "($|[^A-Z가-힣])"),
                Pattern.compile(Pattern.quote(t) + "$")
        );

        return patterns.stream().anyMatch(pattern -> pattern.matcher(upper).find());
    }

    private String removeColorToken(String value, String token) {
        if (value == null || token == null || token.isBlank()) {
            return value;
        }

        if (token.length() == 1 && token.matches("[A-Za-z]")) {
            String result = value.replaceAll("(?i)(^|[^A-Z가-힣])" + Pattern.quote(token) + "($|[^A-Z가-힣])", "$1 $2");
            result = result.replaceAll("(?i)" + Pattern.quote(token) + "(?=[-_\\s]*\\d+)", " ");
            result = result.replaceAll("(?i)" + Pattern.quote(token) + "$", " ");
            return result;
        }

        return value.replaceAll("(?i)" + Pattern.quote(token), " ");
    }

    private boolean containsIgnoreCase(String text, String token) {
        return text.toUpperCase(Locale.ROOT).contains(token.toUpperCase(Locale.ROOT));
    }

    private boolean isBathroomGoods(String category) {
        return category.contains("욕실용품");
    }

    private boolean isCabinet(String category) {
        return category.contains("하부장")
                || category.contains("상부장")
                || category.contains("슬라이드장")
                || category.equals("슬라이드")
                || category.contains("플랩장")
                || category.equals("플랩");
    }

    private boolean isMirror(String category) {
        return category.contains("거울");
    }


    /**
     * 거울 품목의 사이즈 숫자만 제거합니다.
     *
     * 기존 정규식은 "누드-600거울"에서 600 전체가 아니라 60까지만 역추적 매칭하여
     * "누드 0거울"을 만들 수 있었습니다. 숫자 양쪽 경계에 숫자까지 포함하고,
     * "숫자+장", "숫자+거울", 독립 숫자 토큰을 순서대로 제거해 부분 매칭을 차단합니다.
     */
    private String removeMirrorSizeTokens(String value) {
        String result = safe(value);

        // 예: 심플LW-500장 -> 심플. 숫자 앞의 공백/하이픈도 함께 제거합니다.
        result = result.replaceAll("(?<!\\d)[-_\\s]*\\d{2,5}\\s*장", " ");

        // 예: 누드-600거울 -> 누드거울. 600 일부(60)만 지워 0이 남는 부분 매칭을 차단합니다.
        result = result.replaceAll("(?<!\\d)[-_\\s]*\\d{2,5}\\s*(?=거울)", "");

        // 예: 원형거울700 -> 원형거울
        result = result.replaceAll("(?<=거울)[-_\\s]*\\d{2,5}(?!\\d)", "");

        // 예: LED 원형거울 700, 사이클원형거울 600 -> 독립 숫자 토큰만 제거
        result = result.replaceAll("(?<![A-Za-z가-힣0-9])\\d{2,5}(?![A-Za-z가-힣0-9])", " ");

        return result;
    }

    public String normalizeCategory(String value) {
        String normalized = safe(value).replaceAll("\\s+", "");
        if ("기타".equals(normalized)) {
            return "욕실용품";
        }
        if ("슬라이드".equals(normalized)) {
            return "슬라이드장";
        }
        if ("플랩".equals(normalized)) {
            return "플랩장";
        }
        if ("LED거울".equalsIgnoreCase(normalized) || "LED거울".equals(normalized)) {
            return "LED거울";
        }
        return normalized;
    }

    private String cleanupProductName(String value) {
        return safe(value)
                .replaceAll("[\\[\\]{}]", " ")
                .replaceAll("[-_/]+", " ")
                .replaceAll("\\(\\s*\\)", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<ColorToken> buildColorTokens() {
        List<ColorToken> result = new ArrayList<>();

        add(result, "HW", "HW");
        add(result, "히든 화이트", "HW");
        add(result, "히든화이트", "HW");
        add(result, "W", "W");

        add(result, "HB", "HB");
        add(result, "히든 블랙", "HB");
        add(result, "히든블랙", "HB");
        add(result, "B", "B");

        add(result, "HC", "HC");
        add(result, "히든 크림", "HC");
        add(result, "히든크림", "HC");

        add(result, "HG", "HG");
        add(result, "히든 그레이", "HG");
        add(result, "히든그레이", "HG");

        add(result, "DB", "DB");
        add(result, "다크 블루", "DB");
        add(result, "다크블루", "DB");

        add(result, "HN", "HN");
        add(result, "히든 내추럴", "HN");
        add(result, "히든내추럴", "HN");

        add(result, "LW", "LW");
        add(result, "라이트 우드", "LW");
        add(result, "라이트우드", "LW");

        add(result, "IV", "IV");
        add(result, "아이보리", "IV");

        add(result, "MG", "MG");
        add(result, "미스트 그레이", "MG");
        add(result, "미스트그레이", "MG");

        add(result, "GB", "GB");
        add(result, "그레이쉬 브라운", "GB");
        add(result, "그레이쉬브라운", "GB");

        add(result, "골드", "G");
        add(result, "(골드)", "G");
        add(result, "G", "G");
        add(result, "실버", "S");
        add(result, "(실버)", "S");
        add(result, "S", "S");

        add(result, "SP", "SP");
        add(result, "소프트 핑크", "SP");
        add(result, "소프트핑크", "SP");
        add(result, "SB", "SB");
        add(result, "스카이 블루", "SB");
        add(result, "스카이블루", "SB");

        add(result, "페블 프러스트", "페블 프러스트");
        add(result, "페블프러스트", "페블 프러스트");
        add(result, "페블 에버니", "페블 에버니");
        add(result, "페블에버니", "페블 에버니");
        add(result, "페블 사라토가", "페블 사라토가");
        add(result, "사라토가", "페블 사라토가");
        add(result, "페블 츄파로사", "페블 츄파로사");
        add(result, "츄파로사", "페블 츄파로사");
        add(result, "라토나", "라토나");
        add(result, "아스펜 그레이", "아스펜 그레이");
        add(result, "아스펜그레이", "아스펜 그레이");
        add(result, "아스펜 스노우", "아스펜 스노우");
        add(result, "스노우", "아스펜 스노우");
        add(result, "페블 티로즈", "페블 티로즈");
        add(result, "티로즈", "페블 티로즈");
        add(result, "터레인", "터레인");
        add(result, "퓨어 화이트", "퓨어 화이트");
        add(result, "퓨어W", "퓨어 화이트");
        add(result, "스카디", "스카디");
        add(result, "레이니 스카이", "레이니 스카이");
        add(result, "레이니스카이", "레이니 스카이");
        add(result, "샌디드 크림", "샌디드 크림");
        add(result, "샌디드크림", "샌디드 크림");
        add(result, "아스펜 페퍼", "아스펜 페퍼");
        add(result, "아스펜페퍼", "아스펜 페퍼");
        add(result, "오로라 블랑", "오로라 블랑");
        add(result, "오로라블랑", "오로라 블랑");
        add(result, "오로라 그레이", "오로라 그레이");
        add(result, "오로라그레이", "오로라 그레이");
        add(result, "오로라 비스크", "오로라 비스크");
        add(result, "오로라비스크", "오로라 비스크");
        add(result, "오로라 앙고라", "오로라 앙고라");
        add(result, "오로라앙고라", "오로라 앙고라");

        result.sort(Comparator.comparingInt((ColorToken token) -> token.searchToken().length()).reversed());
        return result;
    }

    private void add(List<ColorToken> result, String searchToken, String saveValue) {
        result.add(new ColorToken(searchToken, saveValue));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record ColorToken(String searchToken, String saveValue) {
    }

    private record ColorMatch(String token, String saveValue) {
    }
}
