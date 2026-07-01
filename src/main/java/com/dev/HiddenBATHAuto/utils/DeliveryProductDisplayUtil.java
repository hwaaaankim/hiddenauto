package com.dev.HiddenBATHAuto.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 배송팀 화면 전용 제품 표시 유틸입니다.
 *
 * 목적:
 * - 리스트와 상세 모달에서 같은 기준으로 제품정보를 표시합니다.
 * - optionJson 전체를 그대로 노출하지 않고,
 *   카테고리 / 제품명 / 사이즈 / 색상 / 수량 / 옵션 계열만 정리해서 표시합니다.
 *
 * 표시 대상 optionJson 예:
 * {
 *   "카테고리":"상부장",
 *   "제품시리즈":"판재도어",
 *   "제품시리즈ID":"2",
 *   "제품명":"모듈",
 *   "사이즈":"500*800",
 *   "색상":"HC",
 *   "옵션":"원도어 좌경첩, 하단 오픈, 미니HW",
 *   "옵션2":"**샘플**"
 * }
 *
 * 주의:
 * - 제품시리즈 / 제품시리즈ID는 배송팀 표시 대상이 아니므로 제외합니다.
 * - DB 변경 없이 OrderItem의 @Transient 표시 필드만 채웁니다.
 */
public final class DeliveryProductDisplayUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final List<String> CATEGORY_KEYS = List.of(
            "카테고리", "분류", "category", "Category"
    );

    private static final List<String> PRODUCT_NAME_KEYS = List.of(
            "제품명", "상품명", "제품", "productName", "ProductName", "product", "Product", "name", "Name"
    );

    private static final List<String> SIZE_KEYS = List.of(
            "사이즈", "규격", "size", "Size", "productSize", "ProductSize"
    );

    private static final List<String> COLOR_KEYS = List.of(
            "색상", "제품색상", "컬러", "color", "Color", "productColor", "ProductColor"
    );

    private static final Map<String, String> COLOR_NAME_MAP = buildColorNameMap();

    private DeliveryProductDisplayUtil() {
    }

    public static void enrich(Order order) {
        if (order == null) {
            return;
        }

        OrderItem item = order.getOrderItem();

        if (item == null) {
            return;
        }

        DeliveryProductDisplay display = build(order, item);

        item.setDeliveryCategoryText(display.category());
        item.setDeliveryProductName(display.productName());
        item.setDeliverySizeText(display.size());
        item.setDeliveryColorText(display.color());
        item.setDeliveryOptionText(display.optionText());
        item.setDeliveryQuantityText(display.quantityText());
        item.setDeliveryProductSummaryText(display.summaryText());

        /*
         * 기존 화면/엑셀/다른 코드에서 formattedOptionText를 참조하는 경우가 있어
         * 배송팀 표시 기준과 크게 어긋나지 않도록 같이 채웁니다.
         * 단, 기존 util에서 이미 더 구체적인 값이 들어와 있더라도 배송팀에서는 이 값을 우선 사용합니다.
         */
        item.setFormattedOptionText(display.summaryText());
    }

    public static String buildModalProductText(Order order) {
        if (order == null) {
            return "-";
        }

        OrderItem item = order.getOrderItem();

        if (item == null) {
            int quantity = resolveQuantity(order, null);

            List<String> fallbackLines = new ArrayList<>();
            fallbackLines.add("카테고리: " + dash(order.getProductCategory() != null ? order.getProductCategory().getName() : ""));
            fallbackLines.add("제품명: -");
            fallbackLines.add("사이즈: -");
            fallbackLines.add("색상: -");
            fallbackLines.add("수량: " + (quantity > 0 ? quantity + "개" : "-"));
            fallbackLines.add("옵션: -");

            return String.join("\n", fallbackLines);
        }

        DeliveryProductDisplay display = build(order, item);

        List<String> lines = new ArrayList<>();
        lines.add("카테고리: " + display.category());
        lines.add("제품명: " + display.productName());
        lines.add("사이즈: " + display.size());
        lines.add("색상: " + display.color());
        lines.add("수량: " + display.quantityText().replaceFirst("^수량\\s*", ""));
        lines.add("옵션: " + display.optionText());

        return String.join("\n", lines);
    }

    public static DeliveryProductDisplay build(Order order, OrderItem item) {
        Map<String, Object> optionMap = parseOptionJson(item != null ? item.getOptionJson() : null);

        String category = firstNonBlank(
                pickFirstValue(optionMap, CATEGORY_KEYS),
                order != null && order.getProductCategory() != null ? order.getProductCategory().getName() : null
        );

        String productName = firstNonBlank(
                pickFirstValue(optionMap, PRODUCT_NAME_KEYS),
                item != null ? item.getProductName() : null
        );

        String size = firstNonBlank(
                normalizeSize(pickFirstValue(optionMap, SIZE_KEYS))
        );

        String color = firstNonBlank(
                normalizeColor(pickFirstValue(optionMap, COLOR_KEYS))
        );

        int quantity = resolveQuantity(order, item);
        String quantityText = quantity > 0 ? "수량 " + quantity + "개" : "수량 -";

        String optionText = buildOptionText(optionMap);

        String summaryText = buildSummaryText(
                dash(category),
                dash(productName),
                dash(size),
                dash(color),
                quantityText,
                dash(optionText)
        );

        return new DeliveryProductDisplay(
                dash(category),
                dash(productName),
                dash(size),
                dash(color),
                quantityText,
                dash(optionText),
                summaryText
        );
    }

    private static String buildSummaryText(
            String category,
            String productName,
            String size,
            String color,
            String quantityText,
            String optionText
    ) {
        List<String> tokens = new ArrayList<>();

        addLabeledToken(tokens, "카테고리", category);
        addLabeledToken(tokens, "제품명", productName);
        addLabeledToken(tokens, "사이즈", size);
        addLabeledToken(tokens, "색상", color);

        if (!isBlank(quantityText) && !"-".equals(quantityText)) {
            tokens.add(quantityText);
        }

        addLabeledToken(tokens, "옵션", optionText);

        return tokens.isEmpty() ? "-" : String.join(" / ", tokens);
    }

    private static void addLabeledToken(List<String> tokens, String label, String value) {
        if (tokens == null || isBlank(label) || isBlank(value) || "-".equals(value.trim())) {
            return;
        }

        tokens.add(label + " " + value.trim());
    }

    private static String buildOptionText(Map<String, Object> optionMap) {
        if (optionMap == null || optionMap.isEmpty()) {
            return "-";
        }

        List<Map.Entry<String, Object>> optionEntries = optionMap.entrySet()
                .stream()
                .filter(entry -> isDeliveryOptionKey(entry.getKey()))
                .sorted(Comparator.comparingInt(entry -> optionKeyOrder(entry.getKey())))
                .toList();

        List<String> tokens = new ArrayList<>();

        for (Map.Entry<String, Object> entry : optionEntries) {
            for (String token : normalizeOptionValue(entry.getValue())) {
                if (!isBlank(token) && !isNoneValue(token)) {
                    tokens.add(token);
                }
            }
        }

        return tokens.isEmpty() ? "-" : String.join(" / ", tokens);
    }

    private static boolean isDeliveryOptionKey(String key) {
        if (isBlank(key)) {
            return false;
        }

        String normalized = key.trim().toLowerCase(Locale.ROOT);

        if (normalized.startsWith("옵션_사이즈")
                || normalized.startsWith("옵션사이즈")
                || normalized.startsWith("옵션_색상")
                || normalized.startsWith("옵션색상")) {
            return false;
        }

        if (normalized.equals("option") || normalized.startsWith("option")) {
            return true;
        }

        return normalized.equals("옵션") || normalized.matches("^옵션\\d+$");
    }

    private static int optionKeyOrder(String key) {
        if (isBlank(key)) {
            return Integer.MAX_VALUE;
        }

        String normalized = key.trim().toLowerCase(Locale.ROOT);

        if ("옵션".equals(normalized) || "option".equals(normalized)) {
            return 0;
        }

        String digits = normalized.replaceAll("[^0-9]", "");

        if (digits.isBlank()) {
            return 999;
        }

        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 999;
        }
    }

    private static List<String> normalizeOptionValue(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }

        if (rawValue instanceof Collection<?> collection) {
            List<String> tokens = new ArrayList<>();

            for (Object value : collection) {
                tokens.addAll(normalizeOptionValue(value));
            }

            return tokens;
        }

        String raw = safeText(rawValue);

        if (raw.isBlank()) {
            return List.of();
        }

        String[] parts = raw.split("\\s*,\\s*");
        List<String> tokens = new ArrayList<>();

        for (String part : parts) {
            String text = safeText(part);

            if (!text.isBlank()) {
                tokens.add(text);
            }
        }

        return tokens;
    }

    private static int resolveQuantity(Order order, OrderItem item) {
        if (item != null && item.getQuantity() > 0) {
            return item.getQuantity();
        }

        if (order != null && order.getQuantity() > 0) {
            return order.getQuantity();
        }

        return 0;
    }

    private static Map<String, Object> parseOptionJson(String optionJson) {
        if (isBlank(optionJson)) {
            return Map.of();
        }

        try {
            return OBJECT_MAPPER.readValue(optionJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String pickFirstValue(Map<String, Object> map, List<String> keys) {
        if (map == null || map.isEmpty() || keys == null || keys.isEmpty()) {
            return "";
        }

        for (String key : keys) {
            if (isBlank(key)) {
                continue;
            }

            Object value = map.get(key);
            String text = safeText(value);

            if (!text.isBlank() && !"-".equals(text)) {
                return text;
            }
        }

        return "";
    }

    private static String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return "";
        }

        for (String value : values) {
            String text = safeText(value);

            if (!text.isBlank() && !"-".equals(text)) {
                return text;
            }
        }

        return "";
    }

    private static String normalizeColor(String raw) {
        String text = safeText(raw);

        if (text.isBlank()) {
            return "";
        }

        String code = text;
        int cut = code.indexOf(' ');

        if (cut > 0) {
            code = code.substring(0, cut);
        }

        cut = code.indexOf('(');

        if (cut > 0) {
            code = code.substring(0, cut);
        }

        code = code.trim().toUpperCase(Locale.ROOT);

        String colorName = COLOR_NAME_MAP.get(code);

        if (!isBlank(colorName)) {
            return code + " (" + colorName + ")";
        }

        return text;
    }

    private static String normalizeSize(String raw) {
        String text = safeText(raw);

        if (text.isBlank()) {
            return "";
        }

        return text
                .replaceAll("\\s*\\*\\s*", "*")
                .replaceAll("\\s+x\\s+", "*")
                .trim();
    }

    private static boolean isNoneValue(String value) {
        if (isBlank(value)) {
            return true;
        }

        String text = value.trim().toLowerCase(Locale.ROOT);

        return text.equals("-")
                || text.equals("없음")
                || text.equals("없다")
                || text.equals("무")
                || text.equals("x")
                || text.equals("no")
                || text.equals("n")
                || text.equals("false")
                || text.contains("추가안함")
                || text.contains("추가 안함")
                || text.contains("선택안함")
                || text.contains("선택 안함")
                || text.contains("미선택")
                || text.contains("해당없음")
                || text.contains("해당 없음");
    }

    private static String safeText(Object value) {
        if (value == null) {
            return "";
        }

        return String.valueOf(value)
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private static String dash(String value) {
        String text = safeText(value);

        return text.isBlank() ? "-" : text;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Map<String, String> buildColorNameMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("HW", "히든 화이트");
        map.put("HB", "히든 블랙");
        map.put("HC", "히든 크림");
        map.put("HG", "히든 그레이");
        map.put("G", "골드");
        map.put("S", "실버");
        map.put("IV", "아이보리");
        map.put("HN", "히든 네츄럴");
        map.put("DB", "다크블루");
        map.put("LW", "라이트 우드");
        map.put("MG", "미스트 그레이");
        map.put("GB", "그레이쉬 브라운");
        map.put("SP", "소프트 핑크");
        map.put("SB", "소프트 블루");
        return Map.copyOf(map);
    }

    public record DeliveryProductDisplay(
            String category,
            String productName,
            String size,
            String color,
            String quantityText,
            String optionText,
            String summaryText
    ) {
    }
}
