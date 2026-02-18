package com.dev.HiddenBATHAuto.enums;

public enum CalendarDateBasis {
    REQUEST, // 신청일 기준
    PROCESS; // 처리일 기준

    public static CalendarDateBasis from(String s) {
        if (s == null || s.isBlank()) return REQUEST;
        try {
            return CalendarDateBasis.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return REQUEST;
        }
    }
}