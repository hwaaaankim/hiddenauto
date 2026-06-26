package com.dev.HiddenBATHAuto.service.productOrderAdd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.config.order.MirrorCuttingProductProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MirrorCuttingProductMatcher {

    private final MirrorCuttingProductProperties properties;

    public boolean isMirrorCuttingProduct(
            Boolean manualChecked,
            String productName,
            String categoryName,
            String seriesName,
            LinkedHashMap<String, String> optionMap
    ) {
        if (Boolean.TRUE.equals(manualChecked)) {
            return true;
        }

        if (!properties.isEnabled()) {
            return false;
        }

        List<String> productNameTexts = List.of(nullToEmpty(productName));
        List<String> seriesTexts = List.of(
                nullToEmpty(categoryName),
                nullToEmpty(seriesName),
                nullToEmpty(optionMap == null ? null : optionMap.get("카테고리")),
                nullToEmpty(optionMap == null ? null : optionMap.get("제품시리즈"))
        );
        List<String> optionTexts = extractOptionTexts(optionMap);

        List<String> allTexts = new ArrayList<>();
        allTexts.addAll(productNameTexts);
        allTexts.addAll(seriesTexts);
        allTexts.addAll(optionTexts);

        if (containsAnyKeyword(allTexts, properties.getAnyTextKeywords())) {
            return true;
        }

        if (containsAnyKeyword(productNameTexts, properties.getProductNameKeywords())) {
            return true;
        }

        if (containsAnyKeyword(seriesTexts, properties.getSeriesKeywords())) {
            return true;
        }

        if (containsAnyKeyword(optionTexts, properties.getOptionKeywords())) {
            return true;
        }

        return matchesCanonicalProductName(productName, categoryName, seriesName, optionMap);
    }

    private boolean matchesCanonicalProductName(
            String productName,
            String categoryName,
            String seriesName,
            LinkedHashMap<String, String> optionMap
    ) {
        List<String> canonicalProductNames = properties.getCanonicalProductNames();

        if (canonicalProductNames == null || canonicalProductNames.isEmpty()) {
            return false;
        }

        String combined = String.join(" ",
                nullToEmpty(categoryName),
                nullToEmpty(seriesName),
                nullToEmpty(productName),
                nullToEmpty(optionMap == null ? null : optionMap.get("카테고리")),
                nullToEmpty(optionMap == null ? null : optionMap.get("제품시리즈")),
                nullToEmpty(optionMap == null ? null : optionMap.get("제품명"))
        );

        String normalizedCombined = normalizeForIdentity(combined);

        if (normalizedCombined.isBlank()) {
            return false;
        }

        for (String canonicalProductName : canonicalProductNames) {
            String normalizedCanonical = normalizeForIdentity(canonicalProductName);

            if (normalizedCanonical.isBlank()) {
                continue;
            }

            if (normalizedCombined.contains(normalizedCanonical)
                    || normalizedCanonical.contains(normalizedCombined)) {
                return true;
            }
        }

        return false;
    }

    private List<String> extractOptionTexts(LinkedHashMap<String, String> optionMap) {
        List<String> result = new ArrayList<>();

        if (optionMap == null || optionMap.isEmpty()) {
            return result;
        }

        for (Map.Entry<String, String> entry : optionMap.entrySet()) {
            String key = nullToEmpty(entry.getKey());
            String value = nullToEmpty(entry.getValue());

            result.add(key);
            result.add(value);

            if (key.startsWith("옵션")) {
                result.add(value);
            }
        }

        return result;
    }

    private boolean containsAnyKeyword(List<String> texts, List<String> keywords) {
        if (texts == null || texts.isEmpty() || keywords == null || keywords.isEmpty()) {
            return false;
        }

        List<String> normalizedTexts = texts.stream()
                .map(this::normalizeForContains)
                .filter(text -> !text.isBlank())
                .toList();

        if (normalizedTexts.isEmpty()) {
            return false;
        }

        for (String keyword : keywords) {
            String normalizedKeyword = normalizeForContains(keyword);

            if (normalizedKeyword.isBlank()) {
                continue;
            }

            for (String text : normalizedTexts) {
                if (text.contains(normalizedKeyword)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String normalizeForIdentity(String value) {
        String normalized = normalizeForContains(value);

        for (String ignoreToken : properties.getIgnoreTokens()) {
            String normalizedIgnoreToken = normalizeForContains(ignoreToken);

            if (!normalizedIgnoreToken.isBlank()) {
                normalized = normalized.replace(normalizedIgnoreToken, "");
            }
        }

        return normalized.trim();
    }

    private String normalizeForContains(String value) {
        return nullToEmpty(value)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^0-9A-Z가-힣]", "");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
