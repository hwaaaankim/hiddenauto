package com.dev.HiddenBATHAuto.repository.as;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class AdminAsSort {
    private AdminAsSort() {}
    public static Map<String, String> parse(String state, String field, String direction) {
        Map<String, String> result = new LinkedHashMap<>();
        String source = state != null ? state : (field == null ? "" : field + ":" + direction);
        for (String token : source.split(",")) {
            String[] pair = token.split(":");
            if (pair.length == 2 && expression(pair[0]) != null
                    && ("asc".equals(pair[1]) || "desc".equals(pair[1]))) {
                result.putIfAbsent(pair[0], pair[1]);
            }
        }
        return result;
    }
    public static String encode(Map<String, String> sorts) {
        return sorts.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }
    public static String expression(String field) {
        return switch (field) {
            case "id" -> "a.id";
            case "companyName" -> "c.companyName";
            case "requesterName" -> "rb.name";
            case "customerName" -> "a.customerName";
            case "handlerName" -> "ah.name";
            case "requestedAt" -> "a.requestedAt";
            case "scheduledDate" -> "s.scheduledDate";
            case "asProcessDate" -> "a.asProcessDate";
            case "status" -> "a.status";
            case "subject" -> "a.subject";
            case "productName" -> "a.productName";
            case "applicantName" -> "a.applicantName";
            case "applicantPhone" -> "a.applicantPhone";
            case "onsiteContact" -> "a.onsiteContact";
            case "address" -> "CONCAT(COALESCE(a.roadAddress, ''), ' ', COALESCE(a.detailAddress, ''))";
            default -> null;
        };
    }
}
