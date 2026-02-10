package com.dev.HiddenBATHAuto.utils;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.dto.analytics.ProductOptionNormalized;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ProductOptionJsonParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProductOptionNormalized parse(boolean isStandardOrder, String optionJson) {
        Map<String, Object> raw = safeParse(optionJson);

        if (isStandardOrder) {
            return parseStandard(raw);
        }
        return parseNonStandard(raw);
    }

    private Map<String, Object> safeParse(String optionJson) {
        if (optionJson == null || optionJson.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(optionJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // JSON이 깨진 경우라도 분석 화면이 죽으면 안 되므로 빈 map으로 처리
            return new LinkedHashMap<>();
        }
    }

    private ProductOptionNormalized parseStandard(Map<String, Object> raw) {
        return ProductOptionNormalized.builder()
                .standard(true)
                .top(str(raw.get("카테고리")))
                .mid(str(raw.get("제품시리즈"))) // 표준 JSON에는 없을 수 있음
                .product(str(raw.get("제품명")))
                .color(str(raw.get("색상")))
                .productCode(str(raw.get("제품코드")))
                .raw(raw)
                .build();
    }

    private ProductOptionNormalized parseNonStandard(Map<String, Object> raw) {
        // 예시 JSON 기준
        return ProductOptionNormalized.builder()
                .standard(false)
                .top(str(raw.get("카테고리")))
                .mid(str(raw.get("제품시리즈")))
                .product(str(raw.get("제품")))
                .color(str(raw.get("색상")))
                .productCode(null)
                .raw(raw)
                .build();
    }

    private String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isBlank() ? null : s;
    }
}