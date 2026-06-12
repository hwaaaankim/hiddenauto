package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.repository.RagStructuredRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 고객 상담 중 가격은 AI가 직접 산술 추론하지 않고 이 엔진이 계산합니다.
 *
 * 지원 방식:
 * 1) 구조화 1차원 표(BASE_PRICE_TABLE): 제품명/색상/사이즈/기준가격 컬럼 매칭
 * 2) 구조화 2차원 표(BASE_PRICE_MATRIX, COUNTERTOP_PRICE_MATRIX): W/D 100단위 올림 후 교차값 조회
 * 3) pricing_json.calculationRules 기반 옵션/조건 계산
 * 4) 하부장 학습 예시에서 자주 쓰는 기본 규칙은 pricing_json에 명시되어 있으면 그대로 사용하고,
 *    없더라도 답변에 관련 값이 있을 때만 보수적으로 계산합니다.
 */
@Service
public class RagOrderPriceCalculator {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:,\\d{3})*(?:\\.\\d+)?|-?\\d+(?:\\.\\d+)?");

    private final RagStructuredRepository structuredRepository;
    private final ObjectMapper objectMapper;

    public RagOrderPriceCalculator(RagStructuredRepository structuredRepository,
                                   ObjectMapper objectMapper) {
        this.structuredRepository = structuredRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> calculate(UUID projectId,
                                         UUID versionId,
                                         Map<String, Object> version,
                                         Map<String, Object> state) {
        Map<String, Object> answers = RagJsonUtils.childMap(state, "answers");
        return calculateFromAnswers(projectId, versionId, version, answers);
    }

    public Map<String, Object> calculateFromAnswers(UUID projectId,
                                                    UUID versionId,
                                                    Map<String, Object> version,
                                                    Map<String, Object> answers) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> lines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (projectId == null || versionId == null) {
            result.put("calculated", false);
            result.put("reason", "projectId/versionId가 없어 계산하지 않았습니다.");
            return result;
        }
        if (answers == null || answers.isEmpty()) {
            result.put("calculated", false);
            result.put("reason", "아직 고객 답변이 없습니다.");
            return result;
        }

        Map<String, Object> flat = flattenAnswers(answers);
        Dimensions dim = dimensions(flat, warnings);
        String product = firstText(flat, "품목", "제품", "product", "item", "model", "제품명", "품목명");
        String color = firstText(flat, "색상", "color", "컬러");
        String sizeText = firstText(flat, "사이즈", "규격", "size");

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal base = findBasePriceFromStructuredTables(projectId, versionId, product, color, sizeText, dim, lines, warnings);
        if (base == null) {
            base = findBasePriceFromMatrices(projectId, versionId, "BASE_PRICE_MATRIX", dim, lines, warnings);
        }
        if (base != null) total = total.add(base);

        BigDecimal countertop = shouldUseCountertop(flat)
                ? findBasePriceFromMatrices(projectId, versionId, "COUNTERTOP_PRICE_MATRIX", dim, lines, warnings)
                : null;
        if (countertop != null) total = total.add(countertop);

        Map<String, Object> pricing = RagJsonUtils.toMap(objectMapper, version == null ? null : version.get("pricing_json"));
        List<Map<String, Object>> rules = RagJsonUtils.toMapList(objectMapper, pricing.get("calculationRules"));
        if (rules.isEmpty()) {
            rules = defaultRulesWhenExplicitAnswersExist(flat);
        }
        for (Map<String, Object> rule : rules) {
            BigDecimal amount = applyRule(rule, flat, dim, lines, warnings);
            if (amount != null) total = total.add(amount);
        }

        result.put("calculated", !lines.isEmpty());
        result.put("deterministic", true);
        result.put("total", total.setScale(0, RoundingMode.HALF_UP).longValue());
        result.put("currency", "KRW");
        result.put("lines", lines);
        result.put("warnings", warnings);
        result.put("dimensions", dim.toMap());
        result.put("flatAnswers", flat);
        result.put("calculatedAt", LocalDateTime.now().toString());
        if (lines.isEmpty()) {
            result.put("reason", "계산 가능한 구조화 단가표 또는 계산 규칙을 아직 찾지 못했습니다.");
        }
        return result;
    }

    private BigDecimal findBasePriceFromStructuredTables(UUID projectId,
                                                        UUID versionId,
                                                        String product,
                                                        String color,
                                                        String sizeText,
                                                        Dimensions dim,
                                                        List<Map<String, Object>> lines,
                                                        List<String> warnings) {
        List<Map<String, Object>> rows = structuredRepository.findActiveStructuredRows(projectId, versionId);
        if (rows.isEmpty()) return null;

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String role = str(row.get("table_role"), str(row.get("artifact_role"), ""));
            if (!role.contains("PRICE_TABLE") && !role.contains("BASE_PRICE_TABLE")) continue;
            Map<String, Object> data = RagJsonUtils.toMap(objectMapper, row.get("row_json"));
            BigDecimal price = priceValue(data);
            if (price == null) continue;
            int score = matchScore(data, product, color, sizeText, dim);
            if (score > 0) {
                Map<String, Object> candidate = new LinkedHashMap<>(row);
                candidate.put("_score", score);
                candidate.put("_price", price);
                candidate.put("_rowData", data);
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparingInt(o -> -intVal(o.get("_score"))));
        Map<String, Object> best = candidates.get(0);
        BigDecimal price = decimal(best.get("_price"));
        lines.add(line("BASE_PRICE_TABLE", "기본금액", price,
                "구조화 1차원 표 " + best.get("table_key") + " / rowNo=" + best.get("row_no"), best.get("_rowData")));
        if (candidates.size() > 1 && intVal(candidates.get(0).get("_score")) == intVal(candidates.get(1).get("_score"))) {
            warnings.add("동일 점수의 기본금액 후보가 여러 개 있습니다. 제품명/색상/사이즈 답변을 더 구체화하는 것이 안전합니다.");
        }
        return price;
    }

    private int matchScore(Map<String, Object> row,
                           String product,
                           String color,
                           String sizeText,
                           Dimensions dim) {
        int score = 0;
        String rowText = row.toString().toLowerCase(Locale.ROOT);
        if (StringUtils.hasText(product) && rowText.contains(product.toLowerCase(Locale.ROOT))) score += 40;
        if (StringUtils.hasText(color) && rowText.contains(color.toLowerCase(Locale.ROOT))) score += 20;
        if (StringUtils.hasText(sizeText) && normalizeSize(rowText).contains(normalizeSize(sizeText))) score += 25;
        if (dim.width != null && rowText.contains(String.valueOf(dim.width.intValue()))) score += 8;
        if (dim.depth != null && rowText.contains(String.valueOf(dim.depth.intValue()))) score += 8;
        if (dim.height != null && rowText.contains(String.valueOf(dim.height.intValue()))) score += 8;
        if (!StringUtils.hasText(product) && !StringUtils.hasText(color) && !StringUtils.hasText(sizeText)
                && dim.width == null && dim.depth == null && dim.height == null) return 0;
        return score;
    }

    private BigDecimal findBasePriceFromMatrices(UUID projectId,
                                                 UUID versionId,
                                                 String roleNeedle,
                                                 Dimensions dim,
                                                 List<Map<String, Object>> lines,
                                                 List<String> warnings) {
        if (dim.width == null || dim.depth == null) return null;
        BigDecimal w = ceil100(dim.width);
        BigDecimal d = ceil100(dim.depth);
        List<Map<String, Object>> cells = structuredRepository.findActiveMatrixCells(projectId, versionId);
        if (cells.isEmpty()) return null;
        for (Map<String, Object> cell : cells) {
            String role = str(cell.get("matrix_role"), str(cell.get("artifact_role"), ""));
            if (!role.contains(roleNeedle)) continue;
            BigDecimal row = decimal(cell.get("row_numeric"));
            BigDecimal col = decimal(cell.get("col_numeric"));
            BigDecimal value = decimal(cell.get("numeric_value"));
            if (row == null || col == null || value == null) continue;
            boolean direct = same(row, w) && same(col, d);
            boolean swapped = same(row, d) && same(col, w);
            if (direct || swapped) {
                String label = roleNeedle.contains("COUNTERTOP") ? "상판 추가금" : "기본금액";
                lines.add(line(roleNeedle, label, value,
                        "구조화 2차원 표 " + cell.get("matrix_key") + " / W=" + w + ", D=" + d, cell));
                return value;
            }
        }
        warnings.add(roleNeedle + "에서 W=" + w + ", D=" + d + "에 해당하는 교차값을 찾지 못했습니다.");
        return null;
    }

    private BigDecimal applyRule(Map<String, Object> rule,
                                 Map<String, Object> flat,
                                 Dimensions dim,
                                 List<Map<String, Object>> lines,
                                 List<String> warnings) {
        if (rule == null || rule.isEmpty()) return null;
        String type = str(rule.get("type"), "").toUpperCase(Locale.ROOT);
        String code = str(rule.get("code"), type);
        String label = str(rule.get("label"), code);
        if (!conditionMatches(rule, flat, dim)) return BigDecimal.ZERO;

        switch (type) {
            case "HEIGHT_SURCHARGE_BY_100_CEIL" -> {
                if (dim.height == null) return BigDecimal.ZERO;
                BigDecimal baseHeight = decimalOr(rule.get("baseHeight"), BigDecimal.valueOf(600));
                BigDecimal unit = decimalOr(rule.get("unit"), BigDecimal.valueOf(100));
                BigDecimal amountPerUnit = decimalOr(firstNonNull(rule.get("amountPerUnit"), rule.get("amount")), BigDecimal.ZERO);
                BigDecimal rounded = ceil100(dim.height);
                if (rounded.compareTo(baseHeight) <= 0 || amountPerUnit.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
                BigDecimal units = rounded.subtract(baseHeight).divide(unit, 0, RoundingMode.CEILING);
                BigDecimal amount = units.multiply(amountPerUnit);
                lines.add(line(code, label, amount, "H=" + dim.height + " → " + rounded + ", 기준=" + baseHeight + ", 단위=" + units, rule));
                return amount;
            }
            case "FIXED_PER_COUNT" -> {
                int count = countForRule(rule, flat);
                BigDecimal amount = decimalOr(rule.get("amount"), BigDecimal.ZERO).multiply(BigDecimal.valueOf(count));
                if (count > 0 && amount.compareTo(BigDecimal.ZERO) != 0) {
                    lines.add(line(code, label, amount, "수량=" + count, rule));
                }
                return amount;
            }
            case "FREE_COUNT_THEN_UNIT" -> {
                int count = countForRule(rule, flat);
                int freeCount = intVal(rule.getOrDefault("freeCount", 0));
                int chargeCount = Math.max(0, count - freeCount);
                BigDecimal amount = decimalOr(firstNonNull(rule.get("amountPerExtra"), rule.get("amount")), BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(chargeCount));
                if (chargeCount > 0 && amount.compareTo(BigDecimal.ZERO) != 0) {
                    lines.add(line(code, label, amount, "총 " + count + "개 중 무료 " + freeCount + "개 제외", rule));
                }
                return amount;
            }
            case "HANDLE_PRICE_BY_TYPE" -> {
                String handleType = firstText(flat, "손잡이", "handle");
                if (!StringUtils.hasText(handleType)) return BigDecimal.ZERO;
                BigDecimal amount = priceFromMap(rule.get("priceMap"), handleType);
                if (amount == null) amount = decimal(rule.get("amount"));
                if (amount != null && amount.compareTo(BigDecimal.ZERO) != 0) {
                    int count = Math.max(1, countForRule(rule, flat));
                    BigDecimal total = amount.multiply(BigDecimal.valueOf(count));
                    lines.add(line(code, label, total, "손잡이=" + handleType + ", 수량=" + count, rule));
                    return total;
                }
                warnings.add("손잡이 종류(" + handleType + ")의 단가를 찾지 못했습니다.");
                return BigDecimal.ZERO;
            }
            case "EDGE_BANDING_BY_SIDES" -> {
                return edgeBanding(rule, flat, dim, lines);
            }
            default -> {
                return BigDecimal.ZERO;
            }
        }
    }

    private BigDecimal edgeBanding(Map<String, Object> rule,
                                   Map<String, Object> flat,
                                   Dimensions dim,
                                   List<Map<String, Object>> lines) {
        BigDecimal edgeHeight = firstNumber(flat, "마구리높이", "마구리 높이", "edgeHeight", "edge_height");
        if (edgeHeight == null) edgeHeight = decimal(rule.get("height"));
        if (edgeHeight == null) return BigDecimal.ZERO;
        BigDecimal freeBelow = decimalOr(rule.get("freeBelowHeight"), BigDecimal.valueOf(150));
        if (edgeHeight.compareTo(freeBelow) < 0) {
            lines.add(line(str(rule.get("code"), "EDGE_BANDING"), str(rule.get("label"), "마구리"), BigDecimal.ZERO,
                    "마구리 높이 " + edgeHeight + "mm는 " + freeBelow + "mm 미만이라 무료", rule));
            return BigDecimal.ZERO;
        }
        BigDecimal w = dim.width == null ? BigDecimal.ZERO : dim.width;
        BigDecimal d = dim.depth == null ? BigDecimal.ZERO : dim.depth;
        String sides = firstText(flat, "마구리", "설치면", "edgeSides", "edge_sides");
        BigDecimal length = sideLength(sides, w, d);
        BigDecimal amountPerLength = decimalOr(rule.get("amountPerLength"), BigDecimal.valueOf(100));
        BigDecimal amount = length.multiply(amountPerLength);
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(line(str(rule.get("code"), "EDGE_BANDING"), str(rule.get("label"), "마구리"), amount,
                    "설치면=" + sides + ", 길이=" + length + ", 단가=" + amountPerLength, rule));
        }
        return amount;
    }

    private BigDecimal sideLength(String sides, BigDecimal width, BigDecimal depth) {
        if (!StringUtils.hasText(sides)) return BigDecimal.ZERO;
        String s = sides.replace(" ", "");
        BigDecimal length = BigDecimal.ZERO;
        if (s.contains("좌측") || s.contains("좌")) length = length.add(depth);
        if (s.contains("우측") || s.contains("우")) length = length.add(depth);
        if (s.contains("후방") || s.contains("뒤") || s.contains("후")) length = length.add(width);
        if (s.contains("전면") || s.contains("앞") || s.contains("전")) length = length.add(width);
        return length;
    }

    private boolean conditionMatches(Map<String, Object> rule, Map<String, Object> flat, Dimensions dim) {
        Object raw = rule.get("condition");
        if (!(raw instanceof Map<?, ?> anyMap)) return true;
        Map<String, Object> condition = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : anyMap.entrySet()) if (e.getKey() != null) condition.put(String.valueOf(e.getKey()), e.getValue());
        String keyContains = str(condition.get("answerKeyContains"), null);
        String valueContains = str(condition.get("answerValueContains"), null);
        if (StringUtils.hasText(keyContains)) {
            boolean found = flat.keySet().stream().anyMatch(k -> k.toLowerCase(Locale.ROOT).contains(keyContains.toLowerCase(Locale.ROOT)));
            if (!found) return false;
        }
        if (StringUtils.hasText(valueContains)) {
            boolean found = flat.values().stream().anyMatch(v -> String.valueOf(v).toLowerCase(Locale.ROOT).contains(valueContains.toLowerCase(Locale.ROOT)));
            if (!found) return false;
        }
        BigDecimal minWidth = decimal(condition.get("minWidth"));
        if (minWidth != null && (dim.width == null || dim.width.compareTo(minWidth) < 0)) return false;
        BigDecimal maxWidthExclusive = decimal(condition.get("maxWidthExclusive"));
        if (maxWidthExclusive != null && (dim.width == null || dim.width.compareTo(maxWidthExclusive) >= 0)) return false;
        return true;
    }

    private List<Map<String, Object>> defaultRulesWhenExplicitAnswersExist(Map<String, Object> flat) {
        List<Map<String, Object>> rules = new ArrayList<>();
        if (hasKeyLike(flat, "높이", "height", "h")) {
            rules.add(rule("HEIGHT_SURCHARGE", "H 추가금", "HEIGHT_SURCHARGE_BY_100_CEIL", Map.of(
                    "baseHeight", 600, "unit", 100, "amountPerUnit", 5000
            )));
        }
        if (hasKeyLike(flat, "여닫이", "hinge", "door")) {
            rules.add(rule("HINGE_DOOR", "여닫이 문", "FIXED_PER_COUNT", Map.of("answerKeyContains", "여닫이", "amount", 10000)));
        }
        if (hasKeyLike(flat, "서랍", "drawer")) {
            rules.add(rule("DRAWER", "서랍", "FIXED_PER_COUNT", Map.of("answerKeyContains", "서랍", "amount", 5000)));
        }
        if (hasKeyLike(flat, "타공", "hole")) {
            rules.add(rule("HOLE", "타공", "FREE_COUNT_THEN_UNIT", Map.of("answerKeyContains", "타공", "freeCount", 1, "amountPerExtra", 20000)));
        }
        if (hasKeyLike(flat, "손잡이", "handle")) {
            Map<String, Object> priceMap = Map.of("1", 10000, "2", 15000, "3", 20000, "4", 25000, "5", 30000);
            rules.add(rule("HANDLE", "손잡이", "HANDLE_PRICE_BY_TYPE", Map.of("answerKeyContains", "손잡이", "priceMap", priceMap)));
        }
        if (hasValueLike(flat, "드라이걸이")) rules.add(rule("DRYER_HOLDER", "드라이걸이", "FIXED_PER_COUNT", Map.of("answerValueContains", "드라이걸이", "amount", 5000, "defaultCount", 1)));
        if (hasValueLike(flat, "휴지걸이")) rules.add(rule("PAPER_HOLDER", "휴지걸이", "FIXED_PER_COUNT", Map.of("answerValueContains", "휴지걸이", "amount", 5000, "defaultCount", 1)));
        if (hasValueLike(flat, "LED", "led")) rules.add(rule("LED", "LED", "FIXED_PER_COUNT", Map.of("answerValueContains", "LED", "amount", 20000, "defaultCount", 1)));
        if (hasKeyLike(flat, "마구리", "edge")) {
            rules.add(rule("EDGE_BANDING", "마구리", "EDGE_BANDING_BY_SIDES", Map.of("freeBelowHeight", 150, "amountPerLength", 100)));
        }
        return rules;
    }

    private Map<String, Object> rule(String code, String label, String type, Map<String, Object> body) {
        Map<String, Object> rule = new LinkedHashMap<>(body);
        rule.put("code", code);
        rule.put("label", label);
        rule.put("type", type);
        return rule;
    }

    private int countForRule(Map<String, Object> rule, Map<String, Object> flat) {
        String answerKey = str(firstNonNull(rule.get("answerKey"), rule.get("answerKeyContains")), null);
        BigDecimal number = null;
        if (StringUtils.hasText(answerKey)) {
            number = firstNumber(flat, answerKey);
        }
        if (number == null) number = decimal(rule.get("defaultCount"));
        if (number == null) return 0;
        return Math.max(0, number.setScale(0, RoundingMode.DOWN).intValue());
    }

    private Map<String, Object> flattenAnswers(Map<String, Object> answers) {
        Map<String, Object> flat = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : answers.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> map) {
                Object v = firstNonNull(map.get("value"), map.get("displayValue"), map.get("answer"), map.get("text"));
                flat.put(key, v == null ? map.toString() : v);
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) flat.put(key + "." + e.getKey(), e.getValue());
                }
            } else {
                flat.put(key, value);
            }
        }
        return flat;
    }

    private Dimensions dimensions(Map<String, Object> flat, List<String> warnings) {
        BigDecimal w = firstNumber(flat, "width", "넓이", "가로", "폭", " W", "W", "w");
        BigDecimal d = firstNumber(flat, "depth", "깊이", "세로", " D", "D", "d");
        BigDecimal h = firstNumber(flat, "height", "높이", " H", "H", "h");
        if (w == null || d == null || h == null) {
            String size = firstText(flat, "사이즈", "규격", "size");
            List<BigDecimal> nums = numbers(size);
            if (nums.size() >= 3) {
                if (w == null) w = nums.get(0);
                if (d == null) d = nums.get(1);
                if (h == null) h = nums.get(2);
            }
        }
        if (w == null || d == null) warnings.add("W/D 값이 부족하면 2차원 단가표 조회를 할 수 없습니다.");
        return new Dimensions(w, d, h);
    }

    private String firstText(Map<String, Object> flat, String... keyNeedles) {
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            for (String needle : keyNeedles) {
                if (needle != null && key.contains(needle.toLowerCase(Locale.ROOT))) {
                    String text = str(entry.getValue(), null);
                    if (StringUtils.hasText(text)) return text;
                }
            }
        }
        return null;
    }

    private BigDecimal firstNumber(Map<String, Object> flat, String... keyNeedles) {
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            for (String needle : keyNeedles) {
                if (needle != null && key.contains(needle.toLowerCase(Locale.ROOT))) {
                    BigDecimal number = firstNumber(entry.getValue());
                    if (number != null) return number;
                }
            }
        }
        return null;
    }

    private BigDecimal firstNumber(Object value) {
        List<BigDecimal> numbers = numbers(str(value, ""));
        return numbers.isEmpty() ? null : numbers.get(0);
    }

    private List<BigDecimal> numbers(String text) {
        List<BigDecimal> result = new ArrayList<>();
        if (!StringUtils.hasText(text)) return result;
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                result.add(new BigDecimal(matcher.group().replace(",", "")));
            } catch (Exception ignored) {}
        }
        return result;
    }

    private BigDecimal priceValue(Map<String, Object> row) {
        BigDecimal fallback = null;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            if (key.contains("가격") || key.contains("금액") || key.contains("단가") || key.contains("price")) {
                BigDecimal n = decimal(entry.getValue());
                if (n != null) return n;
            }
            BigDecimal n = decimal(entry.getValue());
            if (n != null) fallback = n;
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal priceFromMap(Object raw, String key) {
        if (!(raw instanceof Map<?, ?> map) || !StringUtils.hasText(key)) return null;
        String lower = key.toLowerCase(Locale.ROOT);
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String k = String.valueOf(e.getKey()).toLowerCase(Locale.ROOT);
            if (lower.contains(k) || k.contains(lower)) {
                return decimal(e.getValue());
            }
        }
        List<BigDecimal> nums = numbers(key);
        if (!nums.isEmpty()) {
            Object value = map.get(String.valueOf(nums.get(0).intValue()));
            if (value != null) return decimal(value);
        }
        return null;
    }

    private boolean shouldUseCountertop(Map<String, Object> flat) {
        return hasKeyLike(flat, "상판", "countertop") || hasValueLike(flat, "상판", "마블");
    }

    private boolean hasKeyLike(Map<String, Object> flat, String... needles) {
        for (String key : flat.keySet()) {
            String lower = key.toLowerCase(Locale.ROOT);
            for (String n : needles) if (n != null && lower.contains(n.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean hasValueLike(Map<String, Object> flat, String... needles) {
        for (Object value : flat.values()) {
            String lower = String.valueOf(value).toLowerCase(Locale.ROOT);
            for (String n : needles) if (n != null && lower.contains(n.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private BigDecimal ceil100(BigDecimal value) {
        if (value == null) return null;
        return value.divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).multiply(BigDecimal.valueOf(100));
    }

    private boolean same(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        String text = String.valueOf(value).replace(",", "").replace("원", "").trim();
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text)) return null;
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) return null;
        try {
            return new BigDecimal(matcher.group().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal decimalOr(Object value, BigDecimal fallback) {
        BigDecimal d = decimal(value);
        return d == null ? fallback : d;
    }

    private String str(Object value, String fallback) {
        if (value == null) return fallback;
        String s = String.valueOf(value);
        return StringUtils.hasText(s) ? s : fallback;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private int intVal(Object value) {
        BigDecimal d = decimal(value);
        return d == null ? 0 : d.intValue();
    }

    private String normalizeSize(String value) {
        if (value == null) return "";
        return value.replaceAll("[^0-9xX*]", "").replace("X", "x").replace("*", "x").toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> line(String code, String label, BigDecimal amount, String basis, Object source) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("code", code);
        line.put("label", label);
        line.put("amount", amount == null ? 0 : amount.setScale(0, RoundingMode.HALF_UP).longValue());
        line.put("basis", basis);
        line.put("source", source);
        return line;
    }

    private record Dimensions(BigDecimal width, BigDecimal depth, BigDecimal height) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("width", width);
            map.put("depth", depth);
            map.put("height", height);
            map.put("roundedWidth", width == null ? null : width.divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).multiply(BigDecimal.valueOf(100)));
            map.put("roundedDepth", depth == null ? null : depth.divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).multiply(BigDecimal.valueOf(100)));
            map.put("roundedHeight", height == null ? null : height.divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).multiply(BigDecimal.valueOf(100)));
            return map;
        }
    }
}
