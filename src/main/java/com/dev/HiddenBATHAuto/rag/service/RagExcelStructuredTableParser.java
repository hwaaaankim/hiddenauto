package com.dev.HiddenBATHAuto.rag.service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 정확 계산에 필요한 엑셀을 JSON 후보가 아니라 DB에 넣을 수 있는 구조화 자료로 변환합니다.
 *
 * 이 파서는 특정 양식 하나에 고정하지 않습니다.
 * - 1차원 표: 제품명/색상/사이즈/기준가격 같은 컬럼 기반 자료
 * - 2차원 표: W-D 가격표처럼 행축/열축의 교차값으로 가격을 찾는 자료
 * 를 동시에 감지합니다.
 */
@Component
public class RagExcelStructuredTableParser {

    private static final int MAX_SHEETS = 30;
    private static final int MAX_ROWS_PER_REGION = 3_000;
    private static final int MAX_COLS_PER_REGION = 120;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(?:,\\d{3})*(?:\\.\\d+)?|-?\\d+(?:\\.\\d+)?");

    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);

    public Map<String, Object> parse(MultipartFile file, String instruction, String topic) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("구조화할 엑셀 파일이 비어 있습니다.");
        }
        String filename = file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new IllegalArgumentException("구조화 학습은 xlsx/xls 엑셀 파일만 처리합니다: " + filename);
        }

        try {
            byte[] bytes = file.getBytes();
            String fingerprint = sha256(bytes);
            try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                List<Map<String, Object>> tables = new ArrayList<>();
                List<Map<String, Object>> matrices = new ArrayList<>();
                List<String> warnings = new ArrayList<>();
                int sheetCount = Math.min(workbook.getNumberOfSheets(), MAX_SHEETS);
                if (workbook.getNumberOfSheets() > MAX_SHEETS) {
                    warnings.add("시트가 많아 앞 " + MAX_SHEETS + "개만 구조화했습니다.");
                }

                for (int i = 0; i < sheetCount; i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    UsedRange used = usedRange(sheet, evaluator);
                    if (used == null) continue;
                    for (Region region : splitByBlankRows(sheet, used, evaluator)) {
                        if (region.rowCount() > MAX_ROWS_PER_REGION || region.colCount() > MAX_COLS_PER_REGION) {
                            warnings.add(sheet.getSheetName() + "!" + region.toA1() + " 영역이 너무 커서 일부 분석 정확도가 낮을 수 있습니다.");
                        }
                        List<List<String>> grid = readGrid(sheet, region, evaluator);
                        Map<String, Object> table = tableCandidate(sheet.getSheetName(), region, grid, instruction, topic, filename);
                        if (table != null) tables.add(table);
                        Map<String, Object> matrix = matrixCandidate(sheet.getSheetName(), region, grid, instruction, topic, filename);
                        if (matrix != null) matrices.add(matrix);
                    }
                }

                String semanticRole = chooseWorkbookRole(instruction, topic, filename, tables, matrices);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("parser", "RagExcelStructuredTableParser");
                result.put("parserVersion", "20260612-structured-v1");
                result.put("originalFilename", filename);
                result.put("fingerprint", fingerprint);
                result.put("topic", topic);
                result.put("instruction", instruction);
                result.put("semanticRole", semanticRole);
                result.put("replaceRequested", looksLikeReplacement(instruction));
                result.put("tables", tables);
                result.put("matrices", matrices);
                result.put("warnings", warnings);
                result.put("summaryText", buildSummaryText(filename, semanticRole, tables, matrices, warnings));
                return result;
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("엑셀 구조화 분석 실패: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String chooseWorkbookRole(String instruction,
                                      String topic,
                                      String filename,
                                      List<Map<String, Object>> tables,
                                      List<Map<String, Object>> matrices) {
        String joined = compact(instruction + " " + topic + " " + filename + " " + tables + " " + matrices).toLowerCase(Locale.ROOT);
        if (containsAny(joined, "상판", "countertop", "marble top", "마블상판")) {
            return matrices.isEmpty() ? "COUNTERTOP_PRICE_TABLE" : "COUNTERTOP_PRICE_MATRIX";
        }
        if (containsAny(joined, "색상", "color", "컬러")) return "COLOR_RULE_TABLE";
        if (containsAny(joined, "사이즈", "규격", "최소", "최대", "min", "max", "w", "d", "h")
                && containsAny(joined, "가능", "범위", "제약", "as", "무상")) {
            return "SIZE_CONSTRAINT_TABLE";
        }
        if (containsAny(joined, "손잡이", "타공", "led", "휴지걸이", "드라이걸이", "옵션")) return "OPTION_PRICE_TABLE";
        if (containsAny(joined, "가격", "금액", "단가", "price", "기준가격", "원")) {
            return matrices.isEmpty() ? "BASE_PRICE_TABLE" : "BASE_PRICE_MATRIX";
        }
        for (Map<String, Object> table : tables) {
            List<String> headers = (List<String>) table.getOrDefault("headers", List.of());
            String headerText = String.join(" ", headers).toLowerCase(Locale.ROOT);
            if (containsAny(headerText, "가격", "금액", "단가", "price")) return "BASE_PRICE_TABLE";
        }
        return "GENERAL_KNOWLEDGE_TABLE";
    }

    private Map<String, Object> tableCandidate(String sheetName,
                                               Region region,
                                               List<List<String>> grid,
                                               String instruction,
                                               String topic,
                                               String filename) {
        if (grid.size() < 2) return null;
        int headerRow = inferHeaderRow(grid);
        if (headerRow < 0 || headerRow >= grid.size() - 1) return null;
        List<String> headers = uniqueHeaders(grid.get(headerRow));
        long usefulHeaders = headers.stream().filter(h -> !h.startsWith("열")).count();
        if (usefulHeaders < 2) return null;

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int r = headerRow + 1; r < grid.size(); r++) {
            List<String> row = grid.get(r);
            if (nonEmpty(row) == 0) continue;
            Map<String, Object> rowMap = new LinkedHashMap<>();
            rowMap.put("_excelRowNo", region.minRow + r + 1);
            for (int c = 0; c < headers.size(); c++) {
                String value = c < row.size() ? row.get(c) : "";
                rowMap.put(headers.get(c), value);
            }
            rows.add(rowMap);
        }
        if (rows.isEmpty()) return null;

        String role = chooseRegionRole(instruction, topic, filename, sheetName, headers, rows.toString(), false);
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("tableKey", safeKey(sheetName + "_" + region.toA1() + "_table"));
        table.put("semanticRole", role);
        table.put("sheetName", sheetName);
        table.put("rangeA1", region.toA1());
        table.put("headerRowNo", region.minRow + headerRow + 1);
        table.put("headers", headers);
        table.put("rows", rows);
        table.put("rowCount", rows.size());
        table.put("metadata", Map.of(
                "source", "EXCEL_ONE_DIMENSION_TABLE",
                "detectedBy", "header-row-plus-data-rows"
        ));
        return table;
    }

    private Map<String, Object> matrixCandidate(String sheetName,
                                                Region region,
                                                List<List<String>> grid,
                                                String instruction,
                                                String topic,
                                                String filename) {
        if (grid.size() < 3 || grid.get(0).size() < 3) return null;
        int headerRows = 1;
        int leftCols = 1;
        int rows = grid.size();
        int cols = grid.get(0).size();

        int numericInterior = 0;
        int nonEmptyInterior = 0;
        for (int r = headerRows; r < rows; r++) {
            for (int c = leftCols; c < cols; c++) {
                String v = grid.get(r).get(c);
                if (StringUtils.hasText(v)) {
                    nonEmptyInterior++;
                    if (toBigDecimal(v) != null) numericInterior++;
                }
            }
        }
        if (nonEmptyInterior < 4 || numericInterior < Math.max(3, nonEmptyInterior / 3)) return null;

        List<String> colLabels = new ArrayList<>();
        for (int c = leftCols; c < cols; c++) {
            String label = grid.get(0).get(c);
            colLabels.add(StringUtils.hasText(label) ? label : "C" + (region.minCol + c + 1));
        }
        List<String> rowLabels = new ArrayList<>();
        for (int r = headerRows; r < rows; r++) {
            String label = grid.get(r).get(0);
            rowLabels.add(StringUtils.hasText(label) ? label : "R" + (region.minRow + r + 1));
        }

        List<Map<String, Object>> cells = new ArrayList<>();
        for (int r = headerRows; r < rows; r++) {
            for (int c = leftCols; c < cols; c++) {
                String display = grid.get(r).get(c);
                if (!StringUtils.hasText(display)) continue;
                BigDecimal value = toBigDecimal(display);
                Map<String, Object> cell = new LinkedHashMap<>();
                String rowKey = rowLabels.get(r - headerRows);
                String colKey = colLabels.get(c - leftCols);
                cell.put("rowKey", rowKey);
                cell.put("colKey", colKey);
                cell.put("rowNumeric", toBigDecimal(rowKey));
                cell.put("colNumeric", toBigDecimal(colKey));
                cell.put("numericValue", value);
                cell.put("displayValue", display);
                cell.put("excelRowNo", region.minRow + r + 1);
                cell.put("excelColNo", region.minCol + c + 1);
                cells.add(cell);
            }
        }
        if (cells.isEmpty()) return null;

        String role = chooseRegionRole(instruction, topic, filename, sheetName, List.of(grid.get(0).toString(), grid.toString()), cells.toString(), true);
        Map<String, Object> matrix = new LinkedHashMap<>();
        matrix.put("matrixKey", safeKey(sheetName + "_" + region.toA1() + "_matrix"));
        matrix.put("semanticRole", role);
        matrix.put("sheetName", sheetName);
        matrix.put("rangeA1", region.toA1());
        matrix.put("rowAxisName", guessAxisName(grid.get(0).get(0), "ROW_AXIS"));
        matrix.put("colAxisName", guessAxisName(grid.get(0).get(0), "COL_AXIS"));
        matrix.put("rowLabels", rowLabels);
        matrix.put("colLabels", colLabels);
        matrix.put("cells", cells);
        matrix.put("roundingPolicy", Map.of(
                "width", "CEIL_100",
                "depth", "CEIL_100",
                "height", "CEIL_100_WHEN_RULE_REQUIRES"
        ));
        matrix.put("metadata", Map.of(
                "source", "EXCEL_TWO_DIMENSION_MATRIX",
                "detectedBy", "row-axis-col-axis-numeric-interior"
        ));
        return matrix;
    }

    private String chooseRegionRole(String instruction,
                                    String topic,
                                    String filename,
                                    String sheetName,
                                    List<String> headers,
                                    String sample,
                                    boolean matrix) {
        String text = compact(instruction + " " + topic + " " + filename + " " + sheetName + " " + String.join(" ", headers) + " " + sample)
                .toLowerCase(Locale.ROOT);
        if (containsAny(text, "상판", "countertop")) return matrix ? "COUNTERTOP_PRICE_MATRIX" : "COUNTERTOP_PRICE_TABLE";
        if (containsAny(text, "색상", "color", "컬러")) return "COLOR_RULE_TABLE";
        if (containsAny(text, "최소", "최대", "min", "max", "무상 as", "as 불가능", "사이즈 가능")) return "SIZE_CONSTRAINT_TABLE";
        if (containsAny(text, "손잡이", "타공", "led", "휴지걸이", "드라이걸이", "옵션")) return "OPTION_PRICE_TABLE";
        if (containsAny(text, "가격", "금액", "단가", "price", "기준가격", "원")) return matrix ? "BASE_PRICE_MATRIX" : "BASE_PRICE_TABLE";
        return matrix ? "GENERAL_MATRIX" : "GENERAL_KNOWLEDGE_TABLE";
    }

    private int inferHeaderRow(List<List<String>> grid) {
        int maxScan = Math.min(8, grid.size() - 2);
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int r = 0; r <= maxScan; r++) {
            List<String> row = grid.get(r);
            int nonEmpty = nonEmpty(row);
            if (nonEmpty < 2) continue;
            int textLike = 0;
            int numericLike = 0;
            Set<String> unique = new LinkedHashSet<>();
            for (String v : row) {
                if (!StringUtils.hasText(v)) continue;
                unique.add(v.trim());
                if (toBigDecimal(v) == null) textLike++; else numericLike++;
            }
            int nextRows = 0;
            for (int i = r + 1; i < Math.min(grid.size(), r + 5); i++) {
                if (nonEmpty(grid.get(i)) >= Math.min(2, nonEmpty)) nextRows++;
            }
            double score = nonEmpty * 2.0 + textLike * 1.5 - numericLike * 0.7 + unique.size() + nextRows * 1.2;
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    private UsedRange usedRange(Sheet sheet, FormulaEvaluator evaluator) {
        int minRow = Integer.MAX_VALUE;
        int maxRow = -1;
        int minCol = Integer.MAX_VALUE;
        int maxCol = -1;
        for (Row row : sheet) {
            if (row == null) continue;
            short first = row.getFirstCellNum();
            short last = row.getLastCellNum();
            if (first < 0 || last < 0) continue;
            for (int c = first; c < last; c++) {
                String value = cellValue(row.getCell(c), evaluator);
                if (StringUtils.hasText(value)) {
                    minRow = Math.min(minRow, row.getRowNum());
                    maxRow = Math.max(maxRow, row.getRowNum());
                    minCol = Math.min(minCol, c);
                    maxCol = Math.max(maxCol, c);
                }
            }
        }
        if (maxRow < 0 || maxCol < 0) return null;
        return new UsedRange(minRow, maxRow, minCol, maxCol);
    }

    private List<Region> splitByBlankRows(Sheet sheet, UsedRange used, FormulaEvaluator evaluator) {
        List<Region> regions = new ArrayList<>();
        int start = -1;
        for (int r = used.minRow; r <= used.maxRow; r++) {
            boolean hasAny = false;
            Row row = sheet.getRow(r);
            for (int c = used.minCol; c <= used.maxCol; c++) {
                if (row != null && StringUtils.hasText(cellValue(row.getCell(c), evaluator))) {
                    hasAny = true;
                    break;
                }
            }
            if (hasAny && start < 0) start = r;
            if ((!hasAny || r == used.maxRow) && start >= 0) {
                int end = hasAny && r == used.maxRow ? r : r - 1;
                regions.add(new Region(start, end, used.minCol, used.maxCol));
                start = -1;
            }
        }
        if (regions.isEmpty()) regions.add(new Region(used.minRow, used.maxRow, used.minCol, used.maxCol));
        return regions;
    }

    private List<List<String>> readGrid(Sheet sheet, Region region, FormulaEvaluator evaluator) {
        List<List<String>> rows = new ArrayList<>();
        for (int r = region.minRow; r <= region.maxRow; r++) {
            Row row = sheet.getRow(r);
            List<String> values = new ArrayList<>();
            for (int c = region.minCol; c <= region.maxCol; c++) {
                values.add(row == null ? "" : truncate(cellValue(row.getCell(c), evaluator), 300));
            }
            rows.add(values);
        }
        return trimBlankColumns(rows);
    }

    private List<List<String>> trimBlankColumns(List<List<String>> rows) {
        if (rows.isEmpty()) return rows;
        int cols = rows.get(0).size();
        int last = -1;
        for (int c = 0; c < cols; c++) {
            for (List<String> row : rows) {
                if (c < row.size() && StringUtils.hasText(row.get(c))) {
                    last = c;
                    break;
                }
            }
        }
        if (last < 0) return rows;
        List<List<String>> trimmed = new ArrayList<>();
        for (List<String> row : rows) {
            List<String> one = new ArrayList<>();
            for (int c = 0; c <= last; c++) one.add(c < row.size() ? row.get(c) : "");
            trimmed.add(one);
        }
        return trimmed;
    }

    private String cellValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) return "";
        try {
            return formatter.formatCellValue(cell, evaluator).trim();
        } catch (Exception e) {
            try {
                return formatter.formatCellValue(cell).trim();
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    private List<String> uniqueHeaders(List<String> rawHeaders) {
        List<String> result = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < rawHeaders.size(); i++) {
            String base = rawHeaders.get(i);
            if (!StringUtils.hasText(base)) base = "열" + (i + 1);
            base = base.trim().replaceAll("\\s+", " ");
            int count = counts.getOrDefault(base, 0) + 1;
            counts.put(base, count);
            result.add(count == 1 ? base : base + "_" + count);
        }
        return result;
    }

    private int nonEmpty(List<String> values) {
        int count = 0;
        for (String value : values) if (StringUtils.hasText(value)) count++;
        return count;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) return null;
        Matcher matcher = NUMBER_PATTERN.matcher(text.replace("원", ""));
        if (!matcher.find()) return null;
        try {
            return new BigDecimal(matcher.group().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean looksLikeReplacement(String instruction) {
        String text = instruction == null ? "" : instruction.toLowerCase(Locale.ROOT);
        return containsAny(text, "교체", "대체", "새로운", "새 파일", "업데이트", "단가 인상", "인상", "변경", "replace", "update");
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String guessAxisName(String topLeft, String fallback) {
        if (!StringUtils.hasText(topLeft)) return fallback;
        String text = topLeft.toUpperCase(Locale.ROOT);
        if (text.contains("W") && text.contains("D")) return topLeft;
        return topLeft;
    }

    private String safeKey(String value) {
        String text = value == null ? "key" : value.trim();
        text = text.replaceAll("[^0-9A-Za-z가-힣_:-]+", "_");
        text = text.replaceAll("_+", "_");
        return text.length() > 180 ? text.substring(0, 180) : text;
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashed = digest.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hashed) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String buildSummaryText(String filename,
                                    String semanticRole,
                                    List<Map<String, Object>> tables,
                                    List<Map<String, Object>> matrices,
                                    List<String> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("[구조화 엑셀 분석]\n");
        sb.append("파일명: ").append(filename).append('\n');
        sb.append("대표 역할: ").append(semanticRole).append('\n');
        sb.append("1차원 표 수: ").append(tables.size()).append('\n');
        for (Map<String, Object> table : tables) {
            sb.append("- 표 ").append(table.get("tableKey"))
                    .append(" / 역할=").append(table.get("semanticRole"))
                    .append(" / 시트=").append(table.get("sheetName"))
                    .append(" / 범위=").append(table.get("rangeA1"))
                    .append(" / 행수=").append(table.get("rowCount"))
                    .append("\n");
        }
        sb.append("2차원 행렬 수: ").append(matrices.size()).append('\n');
        for (Map<String, Object> matrix : matrices) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cells = (List<Map<String, Object>>) matrix.getOrDefault("cells", List.of());
            sb.append("- 행렬 ").append(matrix.get("matrixKey"))
                    .append(" / 역할=").append(matrix.get("semanticRole"))
                    .append(" / 시트=").append(matrix.get("sheetName"))
                    .append(" / 범위=").append(matrix.get("rangeA1"))
                    .append(" / 셀수=").append(cells.size())
                    .append("\n");
        }
        if (!warnings.isEmpty()) {
            sb.append("경고:\n");
            for (String warning : warnings) sb.append("- ").append(warning).append('\n');
        }
        return new String(sb.toString().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private record UsedRange(int minRow, int maxRow, int minCol, int maxCol) {}

    private record Region(int minRow, int maxRow, int minCol, int maxCol) {
        int rowCount() { return maxRow - minRow + 1; }
        int colCount() { return maxCol - minCol + 1; }
        String toA1() { return cellRef(minRow, minCol) + ":" + cellRef(maxRow, maxCol); }
        private static String cellRef(int row, int col) {
            int x = col + 1;
            StringBuilder sb = new StringBuilder();
            while (x > 0) {
                int mod = (x - 1) % 26;
                sb.insert(0, (char) ('A' + mod));
                x = (x - 1) / 26;
            }
            return sb + String.valueOf(row + 1);
        }
    }
}
