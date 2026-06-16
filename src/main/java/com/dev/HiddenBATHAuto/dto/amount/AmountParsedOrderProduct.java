package com.dev.HiddenBATHAuto.dto.amount;

import java.util.Map;

public record AmountParsedOrderProduct(
        Long orderId,
        String category,
        String series,
        String productName,
        String color,
        String sizeText,
        Integer width,
        Integer height,
        Integer depth,
        Integer doorCount,
        String unitHint,
        Map<String, String> optionMap
) {
    public String displayText() {
        return String.join(" / ",
                nonNull(category),
                nonNull(series),
                nonNull(productName),
                nonNull(color),
                nonNull(sizeText),
                doorCount == null ? "" : doorCount + "도어"
        ).replaceAll("( / )+", " / ").replaceAll("^ / | / $", "").trim();
    }

    private static String nonNull(String value) {
        return value == null ? "" : value.trim();
    }
}
