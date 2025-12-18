package com.dev.HiddenBATHAuto.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import lombok.Getter;

public class AddressPreprocessor {

    public static String removeGaAfterRoad(String s) {
        if (s == null) return "";
        String r = s;

        r = r.replaceAll("([가-힣A-Za-z]+로)\\s*\\d+가\\b", "$1");
        r = r.replaceAll("([가-힣A-Za-z]+로)\\s*\\d+가\\s*", "$1 ");
        r = r.replaceAll("\\s+", " ").trim();
        return r;
    }

    public static String splitNumberGa(String s) {
        if (s == null) return "";
        return s.replaceAll("([가-힣A-Za-z]+)(\\d+가)\\b", "$1 $2")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static String buildJibunQuery(String s) {
        if (s == null) return "";
        String t = stripParen(s);
        t = t.replaceAll("\\s+", " ").trim();
        t = ensureProvince(t);

        Matcher m = Pattern.compile("^(?<head>.*?)(?<dongri>\\S+(동|리|가))\\s+(?<no>\\d+(?:-\\d+)?)").matcher(t);
        if (m.find()) {
            String head = m.group("head").trim();
            String dr = m.group("dongri").trim();
            String no = m.group("no").trim();
            return (head + " " + dr + " " + no).replaceAll("\\s+", " ").trim();
        }
        return "";
    }

    public static QueryAndDetail separateTrailingUnitNumber(String s) {
        if (s == null) return new QueryAndDetail("", "");

        String t = s.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return new QueryAndDetail("", "");

        Pattern p = Pattern.compile("^(.*?(?:로|길)\\s*\\d{1,5})\\s+(\\d{2,4})\\b(.*)$");
        Matcher m = p.matcher(t);
        if (m.find()) {
            String q = (m.group(1) + safe(m.group(3))).replaceAll("\\s+", " ").trim();
            String detail = m.group(2).trim();
            return new QueryAndDetail(q, detail);
        }
        return new QueryAndDetail(t, "");
    }

    public static String buildCompactQuery(String s, int maxLen) {
        if (s == null) return "";
        String q = s.trim();

        q = stripAfterComma(stripParen(q));
        q = normalizeRoadSpacing(q);
        q = stripNoise(q);
        q = ensureProvince(q);

        String[] toks = q.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String t : toks) {
            if (sb.length() == 0) {
                if (t.length() <= maxLen) sb.append(t);
                else sb.append(cutByCodePoint(t, maxLen));
            } else {
                if (sb.length() + 1 + t.length() <= maxLen) sb.append(' ').append(t);
                else break;
            }
        }
        return sb.toString();
    }

    public static String cutByCodePoint(String s, int maxLen) {
        if (s == null) return "";
        int count = s.codePointCount(0, s.length());
        if (count <= maxLen) return s;
        int endIndex = s.offsetByCodePoints(0, maxLen);
        return s.substring(0, endIndex);
    }

    private static final Map<String, String> REGION_ALIAS = Map.ofEntries(
            Map.entry("서울시", "서울특별시"),
            Map.entry("인천시", "인천광역시"),
            Map.entry("부산시", "부산광역시"),
            Map.entry("대구시", "대구광역시"),
            Map.entry("광주시", "광주광역시"),
            Map.entry("대전시", "대전광역시"),
            Map.entry("울산시", "울산광역시")
    );

    private static final Map<String, String> CITY_TO_DO = Map.ofEntries(
            Map.entry("군포시", "경기도"),
            Map.entry("성남시", "경기도"),
            Map.entry("수원시", "경기도"),
            Map.entry("고양시", "경기도"),
            Map.entry("하남시", "경기도"),
            Map.entry("안양시", "경기도"),
            Map.entry("부천시", "경기도"),
            Map.entry("남양주시", "경기도"),
            Map.entry("구리시", "경기도"),
            Map.entry("시흥시", "경기도")
    );

    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw.trim();

        s = s.replaceAll("^(창고주소\\s*[:：])\\s*", "");
        s = s.replaceAll("[,，]+", ", ");
        s = s.replaceAll("\\s+", " ");

        for (var e : REGION_ALIAS.entrySet()) {
            if (s.startsWith(e.getKey())) {
                s = s.replaceFirst("^" + Pattern.quote(e.getKey()), e.getValue());
                break;
            }
        }
        return s;
    }

    public static String extractParenDetail(String cleaned) {
        if (cleaned == null) return "";
        Matcher m = Pattern.compile("\\(([^)]+)\\)").matcher(cleaned);
        List<String> d = new ArrayList<>();
        while (m.find()) d.add(m.group(1));
        return d.isEmpty() ? "" : String.join(" ", d);
    }

    public static String stripParen(String cleaned) {
        if (cleaned == null) return "";
        return cleaned.replaceAll("\\([^)]*\\)", "").trim();
    }

    public static String extractCommaTailDetail(String stripped) {
        if (stripped == null) return "";
        int idx = stripped.indexOf(',');
        if (idx > -1 && idx < stripped.length() - 1) return stripped.substring(idx + 1).trim();
        return "";
    }

    public static String stripAfterComma(String stripped) {
        if (stripped == null) return "";
        int idx = stripped.indexOf(',');
        if (idx > -1) return stripped.substring(0, idx).trim();
        return stripped.trim();
    }

    private static final Pattern P_ROAD_MERGE = Pattern.compile("([가-힣A-Za-z]+)\\s*(\\d+)\\s*(로|길)\\b");
    private static final Pattern P_BUILDING_SPACE = Pattern.compile("((?:로|길))\\s*(\\d+)(\\b)");
    private static final Pattern P_RO_107BEON = Pattern.compile("([가-힣A-Za-z]+로)\\s*(\\d+번길)\\b");
    private static final Pattern P_RO_SPLIT_24GIL = Pattern.compile("([가-힣A-Za-z]+로)\\s+(\\d+길)\\b");

    // ✅ 추가: "을지로115" 같은 패턴 (로 + 숫자 붙음) → "을지로 115"
    private static final Pattern P_RO_JOINED_BUILDING = Pattern.compile("([가-힣A-Za-z]+로)\\s*(\\d{1,5})\\b");

    public static String normalizeRoadSpacing(String q) {
        if (q == null) return "";
        String s = q.trim();
        if (s.isEmpty()) return s;

        s = s.replaceAll("\\s{2,}", " ").trim();
        s = P_RO_107BEON.matcher(s).replaceAll("$1 $2");
        s = P_RO_SPLIT_24GIL.matcher(s).replaceAll("$1$2");

        // "천호대로 69길" 같은 케이스 합치기
        Matcher m = P_ROAD_MERGE.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group(1) + m.group(2) + m.group(3));
        }
        m.appendTail(sb);
        s = sb.toString();

        // ✅ "을지로115" → "을지로 115"
        s = P_RO_JOINED_BUILDING.matcher(s).replaceAll("$1 $2");

        // (로|길) 뒤 건물번호 띄우기
        s = P_BUILDING_SPACE.matcher(s).replaceAll("$1 $2$3");

        s = s.replaceAll("\\s{2,}", " ").trim();
        return s;
    }

    public static String extractNoiseToDetail(String s) {
        if (s == null) return "";
        String t = s;

        Pattern p = Pattern.compile(
                "(지하\\s*\\d+층|B\\s*\\d+|\\d+\\s*층|\\d+\\s*호|\\d+동|\\d+-\\d+\\s*호|\\S+빌딩|\\S+상가|\\S+호실)",
                Pattern.CASE_INSENSITIVE
        );

        Matcher m = p.matcher(t);
        List<String> d = new ArrayList<>();
        while (m.find()) d.add(m.group().trim());
        return d.isEmpty() ? "" : String.join(" ", d);
    }

    public static String stripNoise(String s) {
        if (s == null) return "";
        String r = s;

        r = r.replaceAll("번지", "");

        r = r.replaceAll("지하\\s*\\d+층", "");
        r = r.replaceAll("\\bB\\s*\\d+\\b", "");
        r = r.replaceAll("\\d+\\s*층", "");
        r = r.replaceAll("\\d+\\s*호", "");
        r = r.replaceAll("\\d+동", "");
        r = r.replaceAll("\\d+-\\d+\\s*호", "");

        r = r.replaceAll("\\S+빌딩", "");
        r = r.replaceAll("\\S+상가", "");
        r = r.replaceAll("\\S+호실", "");

        r = r.replaceAll("\\s+", " ").trim();
        return r;
    }

    public static String ensureProvince(String s) {
        if (s == null || s.isBlank()) return s;

        Matcher m = Pattern.compile("^(\\S+시|\\S+군|\\S+구)\\b").matcher(s);
        if (m.find()) {
            String si = m.group(1);
            String province = CITY_TO_DO.get(si);
            if (province != null) return province + " " + s;
        }
        return s;
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    @Getter
    @AllArgsConstructor
    public static class QueryAndDetail {
        private String query;
        private String detail;
    }
}