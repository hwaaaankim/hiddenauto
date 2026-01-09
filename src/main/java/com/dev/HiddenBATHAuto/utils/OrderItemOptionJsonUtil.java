package com.dev.HiddenBATHAuto.utils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class OrderItemOptionJsonUtil {

    private static final ObjectMapper OM = new ObjectMapper();

    private OrderItemOptionJsonUtil() {}

    /** optionJson을 파싱해서 parsedOptionMap, formattedOptionText를 채웁니다. */
    public static void enrich(OrderItem item) {
        if (item == null) return;

        String json = item.getOptionJson();
        if (json == null || json.isBlank()) {
            // optionJson이 없으면 기존 productName을 그대로 사용하도록 둡니다.
            item.setFormattedOptionText(safe(item.getProductName()));
            return;
        }

        Map<String, String> map = parseToFlatMap(json);
        item.setParsedOptionMap(map);

        // 한 줄 요약 만들기: 우선순위 키(자주 쓰는 것부터)
        String summary = buildSummary(map);

        // 파싱 결과가 비었으면 productName fallback
        if (summary.isBlank()) summary = safe(item.getProductName());

        item.setFormattedOptionText(summary);
    }

    /** JSON을 최대한 평탄화해서 key->value Map으로 변환 */
    private static Map<String, String> parseToFlatMap(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            JsonNode root = OM.readTree(json);

            // 1) 가장 흔한 케이스: { "카테고리":"...", "제품명":"...", ... }
            if (root.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = root.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String k = safe(e.getKey());
                    String v = nodeToText(e.getValue());
                    if (!k.isBlank() && !v.isBlank()) out.put(k, v);
                }
                return out;
            }

            // 2) 배열 케이스: [ {key:"", value:""}, ... ] or [ {name:"", value:""}, ... ] 등
            if (root.isArray()) {
                for (JsonNode n : root) {
                    if (!n.isObject()) continue;

                    // 가능한 키 조합들
                    String key = firstNonBlank(
                        nodeToText(n.get("key")),
                        nodeToText(n.get("name")),
                        nodeToText(n.get("label")),
                        nodeToText(n.get("title"))
                    );
                    String val = firstNonBlank(
                        nodeToText(n.get("value")),
                        nodeToText(n.get("val")),
                        nodeToText(n.get("text"))
                    );

                    if (!key.isBlank() && !val.isBlank()) out.put(key, val);
                }
                return out;
            }

            // 3) 문자열 등 이상 케이스: 그냥 통째로
            String raw = nodeToText(root);
            if (!raw.isBlank()) out.put("옵션", raw);
            return out;

        } catch (Exception ignore) {
            // 파싱 실패 시 빈 map 반환(상위에서 productName fallback)
            return out;
        }
    }

    private static String buildSummary(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "";

        // 출력 라벨 정리 + 우선순위(원하시는 순서대로 조정 가능)
        String[] preferredKeys = {
            "카테고리", "category", "Category",
            "제품명", "productName", "product", "Product",
            "사이즈", "size", "Size",
            "색상", "color", "Color"
        };

        Map<String, String> ordered = new LinkedHashMap<>();

        // 1) 우선순위 키 먼저 담기
        for (String k : preferredKeys) {
            String v = findIgnoreCase(map, k);
            if (v != null && !v.isBlank()) {
                String label = normalizeLabel(k);
                ordered.put(label, v);
            }
        }

        // 2) 나머지 키들 추가(중복 제외)
        for (Map.Entry<String, String> e : map.entrySet()) {
            String k = safe(e.getKey());
            String v = safe(e.getValue());
            if (k.isBlank() || v.isBlank()) continue;

            String label = normalizeLabel(k);

            // 이미 같은 라벨이 들어갔으면 스킵(중복 방지)
            if (ordered.containsKey(label)) continue;

            ordered.put(label, v);
        }

        // 3) "라벨: 값 / 라벨: 값" 형태로 조합(줄바꿈 없음)
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : ordered.entrySet()) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(e.getKey()).append(": ").append(e.getValue());
        }
        return sb.toString().trim();
    }

    private static String normalizeLabel(String key) {
        String k = safe(key);

        // 영문 키를 한글 라벨로 맞춰주기(필요 시 추가)
        String lower = k.toLowerCase(Locale.ROOT);
        if (lower.equals("category")) return "카테고리";
        if (lower.equals("productname") || lower.equals("product")) return "제품명";
        if (lower.equals("size")) return "사이즈";
        if (lower.equals("color")) return "색상";

        // 이미 한글이면 그대로
        return k;
    }

    private static String findIgnoreCase(Map<String, String> map, String key) {
        if (map == null || key == null) return null;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey() == null) continue;
            if (e.getKey().equalsIgnoreCase(key)) return safe(e.getValue());
        }
        return null;
    }

    private static String nodeToText(JsonNode n) {
        if (n == null || n.isNull()) return "";
        if (n.isTextual()) return safe(n.asText());
        if (n.isNumber() || n.isBoolean()) return safe(n.asText());
        // object/array는 가능한 한 문자열로
        try {
            return safe(OM.convertValue(n, new TypeReference<Object>() {}).toString());
        } catch (Exception e) {
            return safe(n.toString());
        }
    }

    private static String firstNonBlank(String... arr) {
        if (arr == null) return "";
        for (String s : arr) {
            if (s != null && !s.isBlank()) return s;
        }
        return "";
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }
}