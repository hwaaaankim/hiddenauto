package com.dev.HiddenBATHAuto.dto.production;

import java.util.Arrays;
import java.util.List;

public record ProductionListOutputOptions(
        int fontSize,
        boolean includeCompanyName,
        boolean includeDeliveryDate,
        String filterSummary
) {
    public static final int DEFAULT_FONT_SIZE = 10;

    public ProductionListOutputOptions {
        fontSize = Math.max(8, Math.min(14, fontSize));
        filterSummary = normalizeFilterSummary(filterSummary);
    }

    public static ProductionListOutputOptions defaults() {
        return new ProductionListOutputOptions(DEFAULT_FONT_SIZE, false, false, "");
    }

    public List<String> filterTokens() {
        if (filterSummary == null || filterSummary.isBlank()) {
            return List.of("검색 조건: 없음");
        }

        return Arrays.stream(filterSummary.split("\\|"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .limit(20)
                .toList();
    }

    private static String normalizeFilterSummary(String value) {
        if (value == null) return "";
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }
}
