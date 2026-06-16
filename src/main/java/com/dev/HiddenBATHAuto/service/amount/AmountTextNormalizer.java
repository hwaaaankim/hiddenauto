package com.dev.HiddenBATHAuto.service.amount;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

public final class AmountTextNormalizer {

    private static final Pattern PUNCT = Pattern.compile("[\\s\\-_/\\[\\]{}()（）.,·:;|+]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern KEEP = Pattern.compile("[^0-9a-zA-Z가-힣]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern UNUSED_PREFIX = Pattern.compile("^(\\*|사용x|사용X|\\(사용X\\)|\\(사용x\\)|블랙|\\(블랙\\))+");

    private AmountTextNormalizer() {
    }

    public static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static String compact(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .trim()
                .toLowerCase(Locale.ROOT);
        normalized = normalized.replace("시리즈", "");
        normalized = UNUSED_PREFIX.matcher(normalized).replaceAll("");
        normalized = PUNCT.matcher(normalized).replaceAll("");
        normalized = KEEP.matcher(normalized).replaceAll("");
        return normalized;
    }

    public static String spaced(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .trim()
                .toLowerCase(Locale.ROOT);
        normalized = UNUSED_PREFIX.matcher(normalized).replaceAll("");
        return normalized.replaceAll("\\s+", " ");
    }

    public static Set<String> tokens(String value) {
        String spaced = spaced(value);
        if (!StringUtils.hasText(spaced)) {
            return Set.of();
        }
        return Arrays.stream(spaced.split("[^0-9a-zA-Z가-힣]+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .filter(token -> token.length() >= 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static int similarity100(String left, String right) {
        String a = compact(left);
        String b = compact(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 100;
        }
        if (a.contains(b) || b.contains(a)) {
            int min = Math.min(a.length(), b.length());
            int max = Math.max(a.length(), b.length());
            return Math.max(72, (int) Math.round((min * 100.0) / max));
        }
        int distance = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        int levenshteinScore = Math.max(0, (int) Math.round((1.0 - (distance * 1.0 / max)) * 100));
        int tokenScore = tokenOverlap100(a, b);
        return Math.max(levenshteinScore, tokenScore);
    }

    private static int tokenOverlap100(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0;
        }
        long common = ta.stream().filter(tb::contains).count();
        int denom = Math.max(ta.size(), tb.size());
        return (int) Math.round(common * 100.0 / denom);
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    public static String joinForSearch(Object... values) {
        return Arrays.stream(values)
                .filter(value -> value != null && StringUtils.hasText(String.valueOf(value)))
                .map(String::valueOf)
                .collect(Collectors.joining(" "));
    }
}
