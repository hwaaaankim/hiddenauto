package com.dev.HiddenBATHAuto.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class OrderItemOptionJsonUtil {

    private static final ObjectMapper OM = new ObjectMapper();

    private OrderItemOptionJsonUtil() {}

    /**
     * optionJson을 파싱해서 parsedOptionMap, formattedOptionText를 채웁니다.
     *
     * 배송팀 화면 기준:
     * - "카테고리", "제품시리즈", "제품시리즈ID", "제품명", "사이즈", "색상"은 옵션 표시에서 제외합니다.
     * - "옵션", "옵션2", "옵션3" ... 값만 깔끔하게 표시합니다.
     *
     * 예)
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
     * 결과:
     * formattedOptionText = "원도어 좌경첩 / 하단 오픈 / 미니HW / **샘플**"
     */
    public static void enrich(OrderItem item) {
        if (item == null) {
            return;
        }

        String json = item.getOptionJson();

        if (json == null || json.isBlank()) {
            String fallbackProductName = safe(item.getProductName());

            item.setFormattedOptionText(fallbackProductName);
            item.setFormattedOptionHtml(fallbackProductName);
            item.setParsedOptionMap(new LinkedHashMap<>());

            applyDeliveryTransientFields(item, fallbackProductName, "", fallbackProductName);
            return;
        }

        Map<String, String> map = parseToFlatMap(json);
        item.setParsedOptionMap(map);

        String productName = firstNonBlank(
                findIgnoreCase(map, "제품명"),
                findIgnoreCase(map, "productName"),
                findIgnoreCase(map, "product"),
                findIgnoreCase(map, "ProductName"),
                findIgnoreCase(map, "Product"),
                item.getProductName()
        );

        String optionText = buildDeliveryOptionText(map);

        /*
         * 옵션 계열 값이 하나도 없으면 기존 productName으로 fallback합니다.
         * 단, 제품시리즈/제품시리즈ID 같은 값은 fallback으로 쓰지 않습니다.
         */
        String formattedOptionText = optionText.isBlank()
                ? safe(productName)
                : optionText;

        item.setFormattedOptionText(formattedOptionText);
        item.setFormattedOptionHtml(formattedOptionText);

        applyDeliveryTransientFields(item, productName, optionText, formattedOptionText);
    }

    /**
     * JSON을 최대한 평탄화해서 key -> value Map으로 변환합니다.
     */
    private static Map<String, String> parseToFlatMap(String json) {
        Map<String, String> out = new LinkedHashMap<>();

        try {
            JsonNode root = OM.readTree(json);

            /*
             * 1) 가장 흔한 케이스:
             * {
             *   "카테고리":"...",
             *   "제품명":"...",
             *   "옵션":"..."
             * }
             */
            if (root.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = root.fields();

                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();

                    String key = safe(e.getKey());
                    String value = nodeToText(e.getValue());

                    if (!key.isBlank() && !value.isBlank()) {
                        out.put(key, value);
                    }
                }

                return out;
            }

            /*
             * 2) 배열 케이스:
             * [
             *   {"key":"옵션", "value":"..."},
             *   {"name":"색상", "value":"..."}
             * ]
             */
            if (root.isArray()) {
                for (JsonNode n : root) {
                    if (!n.isObject()) {
                        continue;
                    }

                    String key = firstNonBlank(
                            nodeToText(n.get("key")),
                            nodeToText(n.get("name")),
                            nodeToText(n.get("label")),
                            nodeToText(n.get("title"))
                    );

                    String value = firstNonBlank(
                            nodeToText(n.get("value")),
                            nodeToText(n.get("val")),
                            nodeToText(n.get("text"))
                    );

                    if (!key.isBlank() && !value.isBlank()) {
                        out.put(key, value);
                    }
                }

                return out;
            }

            /*
             * 3) 문자열 등 이상 케이스:
             * 통째로 옵션으로 취급합니다.
             */
            String raw = nodeToText(root);

            if (!raw.isBlank()) {
                out.put("옵션", raw);
            }

            return out;

        } catch (Exception ignore) {
            return out;
        }
    }

    /**
     * 배송팀 화면에 표시할 옵션 텍스트만 생성합니다.
     *
     * 포함:
     * - 옵션
     * - 옵션2
     * - 옵션3
     * - option
     * - option2
     *
     * 제외:
     * - 카테고리
     * - 제품시리즈
     * - 제품시리즈ID
     * - 제품명
     * - 사이즈
     * - 색상
     */
    private static String buildDeliveryOptionText(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }

        Set<String> tokens = new LinkedHashSet<>();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = safe(entry.getKey());
            String value = safe(entry.getValue());

            if (key.isBlank() || value.isBlank()) {
                continue;
            }

            if (!isOptionKey(key)) {
                continue;
            }

            List<String> splitValues = splitOptionValue(value);

            for (String splitValue : splitValues) {
                String cleaned = normalizeOptionValue(splitValue);

                if (cleaned.isBlank()) {
                    continue;
                }

                if (isNoneOptionValue(cleaned)) {
                    continue;
                }

                tokens.add(cleaned);
            }
        }

        return String.join(" / ", tokens).trim();
    }

    /**
     * 옵션 key 판별.
     *
     * 허용:
     * - 옵션
     * - 옵션1
     * - 옵션2
     * - 옵션 2
     * - option
     * - option1
     * - option2
     *
     * 제외:
     * - 옵션사이즈
     * - 옵션색상
     * - 제품옵션명 같은 애매한 key
     */
    private static boolean isOptionKey(String key) {
        String k = safe(key);

        if (k.isBlank()) {
            return false;
        }

        String compact = k
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .trim();

        String lower = compact.toLowerCase(Locale.ROOT);

        if (compact.matches("^옵션\\d*$")) {
            return true;
        }

        return lower.matches("^option\\d*$");
    }

    /**
     * "원도어 좌경첩, 하단 오픈, 미니HW"
     * -> ["원도어 좌경첩", "하단 오픈", "미니HW"]
     */
    private static List<String> splitOptionValue(String value) {
        String text = safe(value);

        if (text.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        result.add(text);

        String[] splitRegexes = {
                "\\r?\\n",
                ",",
                "\\|",
                ";",
                "\\s+/\\s+"
        };

        for (String regex : splitRegexes) {
            List<String> next = new ArrayList<>();

            for (String current : result) {
                if (current == null || current.isBlank()) {
                    continue;
                }

                String[] parts = current.split(regex);

                for (String part : parts) {
                    String cleaned = safe(part);

                    if (!cleaned.isBlank()) {
                        next.add(cleaned);
                    }
                }
            }

            result = next;
        }

        return result;
    }

    private static String normalizeOptionValue(String value) {
        String text = safe(value);

        if (text.isBlank()) {
            return "";
        }

        text = text
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        return text;
    }

    private static boolean isNoneOptionValue(String value) {
        String v = safe(value).toLowerCase(Locale.ROOT);

        if (v.isBlank()) {
            return true;
        }

        return v.equals("-")
                || v.equals("없음")
                || v.equals("없다")
                || v.equals("무")
                || v.equals("x")
                || v.equals("no")
                || v.equals("n")
                || v.equals("false")
                || v.equals("null")
                || v.contains("없음")
                || v.contains("추가안함")
                || v.contains("추가 안함")
                || v.contains("선택안함")
                || v.contains("선택 안함")
                || v.contains("미선택")
                || v.contains("해당없음")
                || v.contains("해당 없음");
    }

    /**
     * 기존 buildSummary는 유지하되, 현재 enrich에서는 직접 사용하지 않습니다.
     * 혹시 다른 곳에서 재활용할 가능성을 고려해 남겨둡니다.
     */
    @SuppressWarnings("unused")
    private static String buildSummary(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }

        String[] preferredKeys = {
                "카테고리", "category", "Category",
                "제품명", "productName", "product", "Product",
                "사이즈", "size", "Size",
                "색상", "color", "Color"
        };

        Map<String, String> ordered = new LinkedHashMap<>();

        for (String k : preferredKeys) {
            String v = findIgnoreCase(map, k);

            if (v != null && !v.isBlank()) {
                String label = normalizeLabel(k);
                ordered.put(label, v);
            }
        }

        for (Map.Entry<String, String> e : map.entrySet()) {
            String k = safe(e.getKey());
            String v = safe(e.getValue());

            if (k.isBlank() || v.isBlank()) {
                continue;
            }

            String label = normalizeLabel(k);

            if (ordered.containsKey(label)) {
                continue;
            }

            ordered.put(label, v);
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, String> e : ordered.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }

            sb.append(e.getKey()).append(": ").append(e.getValue());
        }

        return sb.toString().trim();
    }

    private static String normalizeLabel(String key) {
        String k = safe(key);
        String lower = k.toLowerCase(Locale.ROOT);

        if (lower.equals("category")) {
            return "카테고리";
        }

        if (lower.equals("productname") || lower.equals("product")) {
            return "제품명";
        }

        if (lower.equals("size")) {
            return "사이즈";
        }

        if (lower.equals("color")) {
            return "색상";
        }

        return k;
    }

    private static String findIgnoreCase(Map<String, String> map, String key) {
        if (map == null || key == null) {
            return null;
        }

        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }

            if (e.getKey().equalsIgnoreCase(key)) {
                return safe(e.getValue());
            }
        }

        return null;
    }

    private static String nodeToText(JsonNode n) {
        if (n == null || n.isNull()) {
            return "";
        }

        if (n.isTextual()) {
            return safe(n.asText());
        }

        if (n.isNumber() || n.isBoolean()) {
            return safe(n.asText());
        }

        try {
            return safe(OM.convertValue(n, new TypeReference<Object>() {}).toString());
        } catch (Exception e) {
            return safe(n.toString());
        }
    }

    private static String firstNonBlank(String... arr) {
        if (arr == null) {
            return "";
        }

        for (String s : arr) {
            if (s != null && !s.isBlank()) {
                return s.trim();
            }
        }

        return "";
    }

    private static String safe(String s) {
        if (s == null) {
            return "";
        }

        return s
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    /**
     * OrderItem에 배송팀 전용 @Transient 필드를 추가한 경우 같이 세팅됩니다.
     *
     * 필요한 필드:
     * - deliveryProductName
     * - deliveryQuantityText
     * - deliveryOptionText
     * - deliveryProductSummaryText
     *
     * 해당 필드가 아직 없으면 OrderItem에 먼저 추가해야 컴파일됩니다.
     */
    private static void applyDeliveryTransientFields(
            OrderItem item,
            String productName,
            String optionText,
            String formattedOptionText
    ) {
        if (item == null) {
            return;
        }

        String safeProductName = safe(productName);
        String safeOptionText = safe(optionText);

        if (safeProductName.isBlank()) {
            safeProductName = safe(item.getProductName());
        }

        if (safeProductName.isBlank()) {
            safeProductName = "-";
        }

        String quantityText = item.getQuantity() > 0
                ? item.getQuantity() + "개"
                : "-";

        String safeDeliveryOptionText = safeOptionText.isBlank()
                ? "-"
                : safeOptionText;

        String summary = safeProductName;

        if (!"-".equals(quantityText)) {
            summary += " / 수량 " + quantityText;
        }

        if (!"-".equals(safeDeliveryOptionText)) {
            summary += " / 옵션: " + safeDeliveryOptionText;
        }

        /*
         * 아래 4개 setter는 OrderItem에 @Transient 필드가 추가되어 있어야 합니다.
         */
        item.setDeliveryProductName(safeProductName);
        item.setDeliveryQuantityText(quantityText);
        item.setDeliveryOptionText(safeDeliveryOptionText);
        item.setDeliveryProductSummaryText(summary);
    }
}