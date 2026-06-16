package com.dev.HiddenBATHAuto.service.amount;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.dto.amount.AmountParsedOrderProduct;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AmountOrderOptionParser {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d{2,5})");
    private static final Pattern DOOR_PATTERN = Pattern.compile("(\\d+)\\s*(도어|door|문)", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;

    public AmountParsedOrderProduct parse(Order order) {
        OrderItem item = order == null ? null : order.getOrderItem();
        Map<String, String> optionMap = parseOptionMap(item);

        String category = first(optionMap, "카테고리", "제품분류", "분류");
        if (!StringUtils.hasText(category) && order != null && order.getProductCategory() != null) {
            category = order.getProductCategory().getName();
        }

        String series = first(optionMap, "제품시리즈", "시리즈", "제품 시리즈");
        String productName = first(optionMap, "제품명", "제품", "품목명", "품목");
        if (!StringUtils.hasText(productName) && item != null) {
            productName = item.getProductName();
        }
        String color = first(optionMap, "색상", "제품색상", "바디색상", "컬러");
        String size = first(optionMap, "사이즈", "규격", "크기", "제품사이즈");
        Integer[] whd = parseWidthHeightDepth(size);
        Integer doorCount = parseDoorCount(optionMap);
        String unitHint = StringUtils.hasText(category) ? category : "";

        return new AmountParsedOrderProduct(
                order == null ? null : order.getId(),
                safe(category), safe(series), safe(productName), safe(color), safe(size),
                whd[0], whd[1], whd[2], doorCount, unitHint, optionMap
        );
    }

    private Map<String, String> parseOptionMap(OrderItem item) {
        if (item == null || !StringUtils.hasText(item.getOptionJson())) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(item.getOptionJson(), new TypeReference<>() {});
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((k, v) -> result.put(k == null ? "" : k.trim(), v == null ? "" : String.valueOf(v).trim()));
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String first(Map<String, String> map, String... keys) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        for (String key : keys) {
            Optional<Map.Entry<String, String>> exact = map.entrySet().stream()
                    .filter(e -> key.equalsIgnoreCase(e.getKey()))
                    .findFirst();
            if (exact.isPresent() && StringUtils.hasText(exact.get().getValue())) {
                return exact.get().getValue();
            }
        }
        for (String key : keys) {
            Optional<Map.Entry<String, String>> contains = map.entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getKey().replace(" ", "").contains(key.replace(" ", "")))
                    .findFirst();
            if (contains.isPresent() && StringUtils.hasText(contains.get().getValue())) {
                return contains.get().getValue();
            }
        }
        return "";
    }

    private Integer[] parseWidthHeightDepth(String size) {
        Integer[] result = new Integer[] { null, null, null };
        if (!StringUtils.hasText(size)) {
            return result;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(size.replace(",", ""));
        int index = 0;
        while (matcher.find() && index < 3) {
            try {
                result[index++] = Integer.valueOf(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private Integer parseDoorCount(Map<String, String> optionMap) {
        if (optionMap == null || optionMap.isEmpty()) {
            return null;
        }
        String joined = String.join(" ", optionMap.keySet()) + " " + String.join(" ", optionMap.values());
        Matcher matcher = DOOR_PATTERN.matcher(joined);
        if (matcher.find()) {
            try {
                return Integer.valueOf(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        if (joined.contains("원도어") || joined.contains("1도어")) {
            return 1;
        }
        if (joined.contains("투도어") || joined.contains("2도어")) {
            return 2;
        }
        return null;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
