package com.dev.HiddenBATHAuto.service.production;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.dev.HiddenBATHAuto.dto.production.MaterialCuttingDtos.MaterialCuttingParsedOptionsDto;
import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.OrderItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MaterialCuttingOptionParser {

    private static final int DEFAULT_THICKNESS_MM = 15;
    private static final int DEFAULT_LEG_HEIGHT_MM = 150;

    /*
     * 지원 예시
     *
     * 1200(W) * 500(D) * 800(H)
     * 1200W x 500D x 800H
     * 1200 * 500 * 800
     * 1200 × 500 × 800
     * 1200 / 500 / 800
     * 사이즈 : 1200 / 500 / 800
     * 사이즈 : 1200 * 500 * 800
     */
    private static final Pattern TRIPLE_SIZE_PATTERN = Pattern.compile(
            "(?<!\\d)"
                    + "(\\d{2,5})\\s*(?:mm)?\\s*(?:\\(\\s*W\\s*\\)|W|w|넓이|폭|가로)?"
                    + "\\s*(?:[xX×*\\/／])\\s*"
                    + "(\\d{2,5})\\s*(?:mm)?\\s*(?:\\(\\s*D\\s*\\)|D|d|깊이|세로)?"
                    + "\\s*(?:[xX×*\\/／])\\s*"
                    + "(\\d{2,5})\\s*(?:mm)?\\s*(?:\\(\\s*H\\s*\\)|H|h|높이)?"
                    + "(?!\\d)"
    );

    private static final Pattern THICKNESS_T_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,3})\\s*(?:T|t|티)(?![a-zA-Z가-힣])"
    );

    private static final Pattern THICKNESS_LABELED_PATTERN = Pattern.compile(
            "(?:두께|두깨|자재두께|자재두깨|판재두께|판재두깨|두께값|두깨값|thickness)"
                    + "\\s*[:=：]?\\s*(\\d{1,3})\\s*(?:T|t|티|mm)?",
            Pattern.CASE_INSENSITIVE
    );

    private final ObjectMapper objectMapper;

    public MaterialCuttingParsedOptionsDto parse(Order order) {
        OrderItem item = order != null ? order.getOrderItem() : null;

        Map<String, Object> rawMap = parseJsonToMap(item != null ? item.getOptionJson() : null);
        Map<String, String> sourceOptions = flattenToStringMap(rawMap);

        SearchTexts searchTexts = buildSearchTexts(order, item, sourceOptions);

        Dimension dimension = parseDimension(sourceOptions, searchTexts);

        int thicknessMm = parseThickness(sourceOptions, searchTexts);
        BodyType bodyType = parseBodyType(searchTexts);
        DoorMode doorMode = parseDoorMode(searchTexts);
        EdgeSides edgeSides = parseEdgeSides(sourceOptions, searchTexts);

        return new MaterialCuttingParsedOptionsDto(
                dimension != null ? dimension.widthMm() : null,
                dimension != null ? dimension.depthMm() : null,
                dimension != null ? dimension.heightMm() : null,
                thicknessMm,
                DEFAULT_LEG_HEIGHT_MM,
                bodyType.name(),
                bodyType.label,
                doorMode.name(),
                doorMode.label,
                edgeSides.front,
                edgeSides.back,
                edgeSides.left,
                edgeSides.right,
                edgeSides.label(),
                sourceOptions
        );
    }

    /**
     * 핵심 우선순위:
     *
     * 1. adminMemo
     * 2. optionJson
     * 3. orderComment, productName, categoryName
     */
    private SearchTexts buildSearchTexts(Order order, OrderItem item, Map<String, String> sourceOptions) {
        String adminMemoText = order != null ? safeText(order.getAdminMemo()) : "";

        String optionJsonText = buildOptionText(sourceOptions);

        List<String> otherTokens = new ArrayList<>();

        if (order != null) {
            otherTokens.add(order.getOrderComment());

            if (order.getProductCategory() != null) {
                otherTokens.add(order.getProductCategory().getName());
            }
        }

        if (item != null) {
            otherTokens.add(item.getProductName());
        }

        String otherText = String.join(" / ", otherTokens.stream()
                .map(this::safeText)
                .filter(v -> !v.isBlank())
                .toList());

        String fullText = joinNonBlank(" / ", adminMemoText, optionJsonText, otherText);

        return new SearchTexts(
                adminMemoText,
                optionJsonText,
                otherText,
                fullText,
                normalize(adminMemoText),
                normalize(optionJsonText),
                normalize(otherText),
                normalize(fullText)
        );
    }

    private Dimension parseDimension(Map<String, String> sourceOptions, SearchTexts searchTexts) {
        /*
         * 1순위: adminMemo
         */
        Dimension dimensionFromAdminMemo = parseDimensionFromText(searchTexts.adminMemoText());

        if (dimensionFromAdminMemo != null) {
            return dimensionFromAdminMemo;
        }

        /*
         * 2순위: optionJson의 명확한 W/D/H key
         */
        Integer widthByKey = parseNumberFromKnownKeys(sourceOptions, List.of(
                "W", "w",
                "넓이", "폭", "가로",
                "넓이(W)", "폭(W)", "가로(W)",
                "width", "Width", "WIDTH",
                "productWidth", "ProductWidth"
        ));

        Integer depthByKey = parseNumberFromKnownKeys(sourceOptions, List.of(
                "D", "d",
                "깊이", "세로",
                "깊이(D)", "세로(D)",
                "depth", "Depth", "DEPTH",
                "productDepth", "ProductDepth"
        ));

        Integer heightByKey = parseNumberFromKnownKeys(sourceOptions, List.of(
                "H", "h",
                "높이",
                "높이(H)",
                "height", "Height", "HEIGHT",
                "productHeight", "ProductHeight"
        ));

        if (widthByKey != null && depthByKey != null && heightByKey != null) {
            return new Dimension(widthByKey, depthByKey, heightByKey);
        }

        /*
         * 3순위: optionJson의 사이즈/규격 value
         */
        String sizeValue = pickFirstValue(sourceOptions, List.of(
                "사이즈",
                "제품사이즈",
                "규격",
                "제품규격",
                "size",
                "Size",
                "SIZE",
                "productSize",
                "ProductSize",
                "dimension",
                "Dimension"
        ));

        Dimension dimensionFromSizeValue = parseDimensionFromText(sizeValue);

        if (dimensionFromSizeValue != null) {
            return dimensionFromSizeValue;
        }

        /*
         * 4순위: optionJson 전체 텍스트
         */
        Dimension dimensionFromOptionJson = parseDimensionFromText(searchTexts.optionJsonText());

        if (dimensionFromOptionJson != null) {
            return dimensionFromOptionJson;
        }

        /*
         * 5순위: orderComment, productName, categoryName
         */
        return parseDimensionFromText(searchTexts.otherText());
    }

    private Dimension parseDimensionFromText(String text) {
        if (isBlank(text)) {
            return null;
        }

        /*
         * 먼저 "사이즈:" 또는 "규격:" 뒤쪽만 잘라서 우선 분석합니다.
         * 비고 안에 다른 숫자가 섞여 있어도 오탐을 줄이기 위함입니다.
         */
        String focused = extractFocusedSizeSegment(text);
        Dimension focusedResult = parseTripleSize(focused);

        if (focusedResult != null) {
            return focusedResult;
        }

        Dimension tripleResult = parseTripleSize(text);

        if (tripleResult != null) {
            return tripleResult;
        }

        Integer w = findLabeledNumber(text, List.of("W", "w", "넓이", "폭", "가로"));
        Integer d = findLabeledNumber(text, List.of("D", "d", "깊이", "세로"));
        Integer h = findLabeledNumber(text, List.of("H", "h", "높이"));

        if (w != null && d != null && h != null) {
            return new Dimension(w, d, h);
        }

        return null;
    }

    private String extractFocusedSizeSegment(String text) {
        if (isBlank(text)) {
            return "";
        }

        Pattern pattern = Pattern.compile(
                "(?:사이즈|제품사이즈|규격|제품규격|size|dimension)\\s*[:=：]?\\s*(.{0,100})",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(text);

        if (!matcher.find()) {
            return text;
        }

        return matcher.group(1);
    }

    private Dimension parseTripleSize(String text) {
        if (isBlank(text)) {
            return null;
        }

        Matcher matcher = TRIPLE_SIZE_PATTERN.matcher(text);

        if (!matcher.find()) {
            return null;
        }

        int w = parseInt(matcher.group(1), 0);
        int d = parseInt(matcher.group(2), 0);
        int h = parseInt(matcher.group(3), 0);

        if (w <= 0 || d <= 0 || h <= 0) {
            return null;
        }

        return new Dimension(w, d, h);
    }

    private Integer parseNumberFromKnownKeys(Map<String, String> sourceOptions, List<String> keys) {
        if (sourceOptions == null || sourceOptions.isEmpty()) {
            return null;
        }

        for (String key : keys) {
            String value = sourceOptions.get(key);

            Integer parsed = parseFirstPositiveNumber(value);

            if (parsed != null) {
                return parsed;
            }
        }

        /*
         * key가 정확히 "넓이(W)"가 아니라 "넓이(W) mm" 같은 식으로 저장된 경우 대비
         */
        for (Map.Entry<String, String> entry : sourceOptions.entrySet()) {
            String optionKey = normalize(entry.getKey());

            for (String key : keys) {
                String normalizedKey = normalize(key);

                if (!normalizedKey.isBlank() && optionKey.contains(normalizedKey)) {
                    Integer parsed = parseFirstPositiveNumber(entry.getValue());

                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }

        return null;
    }

    private Integer findLabeledNumber(String text, List<String> labels) {
        if (isBlank(text) || labels == null || labels.isEmpty()) {
            return null;
        }

        for (String label : labels) {
            String quoted = Pattern.quote(label);

            Pattern beforePattern = Pattern.compile(
                    "(?i)(?:" + quoted + ")\\s*(?:\\([^)]*\\))?\\s*[:=：]?\\s*(\\d{2,5})\\s*(?:mm)?"
            );

            Matcher beforeMatcher = beforePattern.matcher(text);

            if (beforeMatcher.find()) {
                return parseInt(beforeMatcher.group(1), 0);
            }

            Pattern afterPattern = Pattern.compile(
                    "(?i)(\\d{2,5})\\s*(?:mm)?\\s*(?:\\(\\s*" + quoted + "\\s*\\)|" + quoted + ")"
            );

            Matcher afterMatcher = afterPattern.matcher(text);

            if (afterMatcher.find()) {
                return parseInt(afterMatcher.group(1), 0);
            }
        }

        return null;
    }

    private int parseThickness(Map<String, String> sourceOptions, SearchTexts searchTexts) {
        /*
         * 1순위: adminMemo
         */
        Integer byAdminMemo = parseThicknessFromText(searchTexts.adminMemoText());

        if (byAdminMemo != null) {
            return byAdminMemo;
        }

        /*
         * 2순위: optionJson의 두께/두깨 key
         */
        String thicknessValue = pickFirstValue(sourceOptions, List.of(
                "두께",
                "두깨",
                "자재두께",
                "자재두깨",
                "판재두께",
                "판재두깨",
                "두께값",
                "두깨값",
                "T",
                "t",
                "thickness",
                "Thickness",
                "THICKNESS"
        ));

        Integer byOptionKey = parseThicknessFromText(thicknessValue);

        if (byOptionKey != null) {
            return byOptionKey;
        }

        /*
         * 3순위: optionJson 전체 텍스트
         */
        Integer byOptionText = parseThicknessFromText(searchTexts.optionJsonText());

        if (byOptionText != null) {
            return byOptionText;
        }

        /*
         * 4순위: orderComment, productName, categoryName
         */
        Integer byOtherText = parseThicknessFromText(searchTexts.otherText());

        if (byOtherText != null) {
            return byOtherText;
        }

        return DEFAULT_THICKNESS_MM;
    }

    private Integer parseThicknessFromText(String text) {
        if (isBlank(text)) {
            return null;
        }

        Matcher labeledMatcher = THICKNESS_LABELED_PATTERN.matcher(text);

        if (labeledMatcher.find()) {
            int parsed = parseInt(labeledMatcher.group(1), 0);

            if (isValidThickness(parsed)) {
                return parsed;
            }
        }

        Matcher tMatcher = THICKNESS_T_PATTERN.matcher(text);

        if (tMatcher.find()) {
            int parsed = parseInt(tMatcher.group(1), 0);

            if (isValidThickness(parsed)) {
                return parsed;
            }
        }

        /*
         * key가 "두께"이고 value가 단순히 "15"로 들어온 경우 대비
         */
        Integer numberOnly = parseFirstPositiveNumber(text);

        if (numberOnly != null && isValidThickness(numberOnly)) {
            return numberOnly;
        }

        return null;
    }

    private boolean isValidThickness(int value) {
        return value >= 3 && value <= 50;
    }

    private BodyType parseBodyType(SearchTexts searchTexts) {
        BodyType byAdminMemo = parseBodyTypeFromText(searchTexts.normalizedAdminMemoText());

        if (byAdminMemo != null) {
            return byAdminMemo;
        }

        BodyType byOptionJson = parseBodyTypeFromText(searchTexts.normalizedOptionJsonText());

        if (byOptionJson != null) {
            return byOptionJson;
        }

        BodyType byOtherText = parseBodyTypeFromText(searchTexts.normalizedOtherText());

        if (byOtherText != null) {
            return byOtherText;
        }

        return BodyType.WALL;
    }

    private BodyType parseBodyTypeFromText(String normalizedText) {
        if (isBlank(normalizedText)) {
            return null;
        }

        if (containsAny(normalizedText, "벽걸이", "벽부착", "벽걸이형")) {
            return BodyType.WALL;
        }

        boolean negativeLeg = containsAny(
                normalizedText,
                "다리없음",
                "다리없다",
                "다리무",
                "다리x",
                "다리미적용",
                "다리안함",
                "다리없게"
        );

        if (!negativeLeg && containsAny(
                normalizedText,
                "다리형",
                "다리타입",
                "다리있음",
                "다리유",
                "다리有"
        )) {
            return BodyType.LEG;
        }

        return null;
    }

    private DoorMode parseDoorMode(SearchTexts searchTexts) {
        DoorMode byAdminMemo = parseDoorModeFromText(searchTexts.normalizedAdminMemoText());

        if (byAdminMemo != null) {
            return byAdminMemo;
        }

        DoorMode byOptionJson = parseDoorModeFromText(searchTexts.normalizedOptionJsonText());

        if (byOptionJson != null) {
            return byOptionJson;
        }

        DoorMode byOtherText = parseDoorModeFromText(searchTexts.normalizedOtherText());

        if (byOtherText != null) {
            return byOtherText;
        }

        return DoorMode.OUTDOOR;
    }

    private DoorMode parseDoorModeFromText(String normalizedText) {
        if (isBlank(normalizedText)) {
            return null;
        }

        if (containsAny(normalizedText, "인도어", "인도어형", "인도어타입", "in_door", "indoor")) {
            return DoorMode.INDOOR;
        }

        if (containsAny(normalizedText, "아웃도어", "아웃도어형", "아웃도어타입", "out_door", "outdoor")) {
            return DoorMode.OUTDOOR;
        }

        return null;
    }

    private EdgeSides parseEdgeSides(Map<String, String> sourceOptions, SearchTexts searchTexts) {
        /*
         * 1순위: adminMemo
         */
        String adminMemoEdgeSegment = extractEdgeSegment(searchTexts.normalizedAdminMemoText());

        if (!adminMemoEdgeSegment.isBlank()) {
            return parseEdgeSidesFromSegment(adminMemoEdgeSegment);
        }

        /*
         * 2순위: optionJson의 마구리 key
         */
        String edgeValue = pickFirstValue(sourceOptions, List.of(
                "마구리",
                "마구리면",
                "마구리방향",
                "마구리위치",
                "마구리 여부",
                "마구리여부",
                "edge",
                "edgeSide",
                "edgeSides"
        ));

        if (!edgeValue.isBlank()) {
            return parseEdgeSidesFromSegment(normalize(edgeValue));
        }

        /*
         * 3순위: optionJson 전체 텍스트
         */
        String optionJsonEdgeSegment = extractEdgeSegment(searchTexts.normalizedOptionJsonText());

        if (!optionJsonEdgeSegment.isBlank()) {
            return parseEdgeSidesFromSegment(optionJsonEdgeSegment);
        }

        /*
         * 4순위: orderComment, productName, categoryName
         */
        String otherEdgeSegment = extractEdgeSegment(searchTexts.normalizedOtherText());

        if (!otherEdgeSegment.isBlank()) {
            return parseEdgeSidesFromSegment(otherEdgeSegment);
        }

        /*
         * 기본값: 마구리 3면 전좌우
         */
        return EdgeSides.defaultThreeSides();
    }

    private String extractEdgeSegment(String normalizedText) {
        if (isBlank(normalizedText)) {
            return "";
        }

        int index = normalizedText.indexOf("마구리");

        if (index < 0) {
            return "";
        }

        int start = Math.max(0, index - 10);
        int end = Math.min(normalizedText.length(), index + 80);

        return normalizedText.substring(start, end);
    }

    private EdgeSides parseEdgeSidesFromSegment(String segment) {
        if (isBlank(segment)) {
            return EdgeSides.defaultThreeSides();
        }

        /*
         * 명시적 없음
         */
        if (containsAny(segment,
                "없음",
                "없다",
                "없슴",
                "무",
                "미적용",
                "안함",
                "하지않음",
                "x",
                "no"
        )) {
            return new EdgeSides(false, false, false, false);
        }

        /*
         * 4면/사면
         */
        if (containsAny(segment, "4면", "사면", "네면", "전체", "전후좌우")) {
            return new EdgeSides(true, true, true, true);
        }

        boolean front = containsAny(segment, "전", "앞", "앞면", "전면", "front");
        boolean back = containsAny(segment, "후", "뒤", "뒷", "뒷면", "후면", "back");
        boolean left = containsAny(segment, "좌", "왼", "왼쪽", "좌측", "left");
        boolean right = containsAny(segment, "우", "오른", "오른쪽", "우측", "right");

        /*
         * 좌우라고 붙어있는 경우
         */
        if (segment.contains("좌우")) {
            left = true;
            right = true;
        }

        boolean hasSpecificSide = front || back || left || right;

        if (hasSpecificSide) {
            return new EdgeSides(front, back, left, right);
        }

        /*
         * 3면/삼면만 있고 방향이 없으면 기본 전좌우
         */
        if (containsAny(segment, "3면", "삼면", "세면")) {
            return EdgeSides.defaultThreeSides();
        }

        /*
         * "마구리 : 있음", "마구리 있음"처럼 방향이 없으면 기본 전좌우
         */
        if (containsAny(segment, "있음", "있슴", "유", "有", "적용", "y", "yes")) {
            return EdgeSides.defaultThreeSides();
        }

        /*
         * 마구리라는 단어는 있는데 구체 방향이 없으면 기본값
         */
        return EdgeSides.defaultThreeSides();
    }

    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, String> flattenToStringMap(Map<String, Object> rawMap) {
        Map<String, String> result = new LinkedHashMap<>();

        if (rawMap == null || rawMap.isEmpty()) {
            return result;
        }

        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
            String key = safeText(entry.getKey());
            String value = safeText(flattenValue(entry.getValue()));

            if (!key.isBlank() && !value.isBlank()) {
                result.put(key, value);
            }
        }

        return result;
    }

    private String flattenValue(Object value) {
        if (value == null) {
            return "";
        }

        if (value instanceof Map<?, ?> mapValue) {
            List<String> tokens = new ArrayList<>();

            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                String key = safeText(entry.getKey());
                String childValue = flattenValue(entry.getValue());

                if (!key.isBlank() && !childValue.isBlank()) {
                    tokens.add(key + ": " + childValue);
                } else if (!childValue.isBlank()) {
                    tokens.add(childValue);
                }
            }

            return String.join(" / ", tokens);
        }

        if (value instanceof List<?> listValue) {
            List<String> tokens = new ArrayList<>();

            for (Object child : listValue) {
                String childValue = flattenValue(child);

                if (!childValue.isBlank()) {
                    tokens.add(childValue);
                }
            }

            return String.join(" / ", tokens);
        }

        return safeText(value);
    }

    private String buildOptionText(Map<String, String> sourceOptions) {
        if (sourceOptions == null || sourceOptions.isEmpty()) {
            return "";
        }

        List<String> tokens = new ArrayList<>();

        for (Map.Entry<String, String> entry : sourceOptions.entrySet()) {
            String key = safeText(entry.getKey());
            String value = safeText(entry.getValue());

            if (!key.isBlank() && !value.isBlank()) {
                tokens.add(key + " : " + value);
            } else if (!value.isBlank()) {
                tokens.add(value);
            }
        }

        return String.join(" / ", tokens);
    }

    private String pickFirstValue(Map<String, String> map, List<String> keys) {
        if (map == null || map.isEmpty() || keys == null) {
            return "";
        }

        for (String key : keys) {
            String value = safeText(map.get(key));

            if (!value.isBlank()) {
                return value;
            }
        }

        /*
         * key가 정확히 일치하지 않는 경우 대비
         * 예: "제품 사이즈", "마구리 방향", "자재 두께"
         */
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String optionKey = normalize(entry.getKey());

            for (String key : keys) {
                String normalizedKey = normalize(key);

                if (!normalizedKey.isBlank() && optionKey.contains(normalizedKey)) {
                    String value = safeText(entry.getValue());

                    if (!value.isBlank()) {
                        return value;
                    }
                }
            }
        }

        return "";
    }

    private Integer parseFirstPositiveNumber(String text) {
        if (isBlank(text)) {
            return null;
        }

        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{1,5})(?!\\d)").matcher(text);

        if (!matcher.find()) {
            return null;
        }

        int parsed = parseInt(matcher.group(1), 0);

        return parsed > 0 ? parsed : null;
    }

    private boolean containsAny(String text, String... keywords) {
        if (isBlank(text) || keywords == null || keywords.length == 0) {
            return false;
        }

        String normalizedText = text.toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {
            if (isBlank(keyword)) {
                continue;
            }

            String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);

            if (normalizedText.contains(normalizedKeyword)) {
                return true;
            }
        }

        return false;
    }

    private String joinNonBlank(String delimiter, String... values) {
        if (values == null || values.length == 0) {
            return "";
        }

        List<String> tokens = new ArrayList<>();

        for (String value : values) {
            String text = safeText(value);

            if (!text.isBlank()) {
                tokens.add(text);
            }
        }

        return String.join(delimiter, tokens);
    }

    private int parseInt(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String normalize(String text) {
        return safeText(text)
                .replace("：", ":")
                .replace("／", "/")
                .replace("×", "*")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String safeText(Object value) {
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record SearchTexts(
            String adminMemoText,
            String optionJsonText,
            String otherText,
            String fullText,
            String normalizedAdminMemoText,
            String normalizedOptionJsonText,
            String normalizedOtherText,
            String normalizedFullText
    ) {
    }

    private record Dimension(
            int widthMm,
            int depthMm,
            int heightMm
    ) {
    }

    private enum BodyType {
        WALL("벽걸이형"),
        LEG("다리형");

        private final String label;

        BodyType(String label) {
            this.label = label;
        }
    }

    private enum DoorMode {
        INDOOR("인도어"),
        OUTDOOR("아웃도어");

        private final String label;

        DoorMode(String label) {
            this.label = label;
        }
    }

    private record EdgeSides(
            boolean front,
            boolean back,
            boolean left,
            boolean right
    ) {
        private static EdgeSides defaultThreeSides() {
            return new EdgeSides(true, false, true, true);
        }

        private String label() {
            List<String> tokens = new ArrayList<>();

            if (front) {
                tokens.add("전");
            }

            if (back) {
                tokens.add("후");
            }

            if (left) {
                tokens.add("좌");
            }

            if (right) {
                tokens.add("우");
            }

            if (tokens.isEmpty()) {
                return "없음";
            }

            return String.join("", tokens);
        }
    }
}