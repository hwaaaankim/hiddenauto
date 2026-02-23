package com.dev.HiddenBATHAuto.model.task;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum AsStatus {
    REQUESTED("요청됨"),
    IN_PROGRESS("진행중"),
    COMPLETED("완료"),
    CANCELED("취소");

    private final String labelKr;

    AsStatus(String labelKr) {
        this.labelKr = labelKr;
    }

    public String getLabelKr() {
        return labelKr;
    }

    /**
     * ✅ Thymeleaf/JS에서 쓰기 좋은 "영문키 -> 한글라벨" 맵
     * - key: enum.name() (REQUESTED 등)
     * - value: 한글 라벨 (요청됨 등)
     */
    public static Map<String, String> labelMap() {
        Map<String, String> m = new LinkedHashMap<>();
        Arrays.stream(values()).forEach(s -> m.put(s.name(), s.getLabelKr()));
        return Collections.unmodifiableMap(m);
    }

    /** null/잘못된 값 방어용(선택) */
    public static String labelOf(String name) {
        if (name == null || name.isBlank()) return "-";
        try {
            return AsStatus.valueOf(name).getLabelKr();
        } catch (IllegalArgumentException e) {
            return name; // 모르는 값이면 원문 그대로
        }
    }
}