package com.dev.HiddenBATHAuto.rag.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class RagJsonUtils {
    private RagJsonUtils() {}

    public static String toJson(ObjectMapper objectMapper, Object value) {
        try {
            if (value == null) return "{}";
            if (value instanceof String s) return s.isBlank() ? "{}" : s;
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 변환 실패: " + e.getMessage(), e);
        }
    }

    public static Map<String, Object> toMap(ObjectMapper objectMapper, Object jsonValue) {
        if (jsonValue == null) return new LinkedHashMap<>();
        if (jsonValue instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return toMap(objectMapper, String.valueOf(jsonValue));
    }

    public static Map<String, Object> toMap(ObjectMapper objectMapper, String json) {
        try {
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", json);
            fallback.put("parseError", e.getMessage());
            return fallback;
        }
    }

    public static List<Map<String, Object>> toMapList(ObjectMapper objectMapper, Object value) {
        try {
            if (value == null) return new ArrayList<>();
            return objectMapper.convertValue(value, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static JsonNode extractObjectNode(ObjectMapper objectMapper, String text) {
        try {
            if (text == null || text.isBlank()) return objectMapper.createObjectNode();
            String trimmed = stripCodeFence(text.trim());
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end >= start) trimmed = trimmed.substring(start, end + 1);
            JsonNode node = objectMapper.readTree(trimmed);
            if (node == null || !node.isObject()) {
                ObjectNode fallback = objectMapper.createObjectNode();
                fallback.put("raw", text);
                return fallback;
            }
            return node;
        } catch (Exception e) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("raw", text);
            fallback.put("parseError", e.getMessage());
            return fallback;
        }
    }

    public static String stringValue(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static int intValue(Map<String, Object> map, String key, int fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (Exception e) { return fallback; }
    }

    public static boolean boolValue(Map<String, Object> map, String key, boolean fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Boolean b) return b;
        if (value == null) return fallback;
        String s = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(s) || "Y".equalsIgnoreCase(s) || "1".equals(s)) return true;
        if ("false".equalsIgnoreCase(s) || "N".equalsIgnoreCase(s) || "0".equals(s)) return false;
        return fallback;
    }

    public static Map<String, Object> childMap(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> childList(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof List<?> list) return (List<Object>) list;
        return new ArrayList<>();
    }

    public static String pretty(ObjectMapper objectMapper, Object value) {
        try {
            if (value == null) return "null";
            if (value instanceof String str) return str;
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    public static String truncate(String value, int maxLength) {
        if (value == null) return "";
        if (maxLength <= 0 || value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "\n... 생략 ...";
    }

    public static String safeString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String stripCodeFence(String text) {
        if (text.startsWith("```")) {
            String withoutStart = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            return withoutStart.replaceFirst("\\s*```$", "").trim();
        }
        return text;
    }
}
