package com.dev.HiddenBATHAuto.rag.service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 엑셀을 특정 컬럼명에 묶지 않고 학습 가능한 구조 텍스트로 변환합니다.
 *
 * 핵심 원칙
 * 1) 엑셀 원본은 결국 격자 데이터이므로 원본 격자를 먼저 보존합니다.
 * 2) 비어 있는 행/열을 기준으로 시트 내 독립 영역을 나눕니다.
 * 3) 각 영역마다 1차원 테이블 후보와 2차원 교차표 후보를 동시에 생성합니다.
 * 4) 제품명/사이즈/색상/가격 같은 도메인 의미 확정은 AI 검수 단계에서
 *    사용자 메시지, 기존 지식, 검색 근거와 함께 판단합니다.
 */
@Component
public class RagExcelKnowledgeExtractor {

    private static final int MAX_SCAN_SHEETS = 30;
    private static final int MAX_RAW_GRID_ROWS = 80;
    private static final int MAX_RAW_GRID_COLS = 40;
    private static final int MAX_TABLE_ROWS = 300;
    private static final int MAX_MATRIX_FACTS = 2_500;
    private static final int MAX_CELL_TEXT = 240;

    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);

    public RagUploadedKnowledgeDocument extract(MultipartFile file) {
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("엑셀 시트가 없습니다.");
            }

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            StringBuilder knowledgeText = new StringBuilder(128_000);
            List<Map<String, Object>> sheetMetas = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            knowledgeText.append("[엑셀 일반 구조 학습 자료]\n");
            knowledgeText.append("파일명: ").append(safe(file.getOriginalFilename(), "upload.xlsx")).append('\n');
            knowledgeText.append("해석 원칙: 이 엑셀은 고정 양식으로 가정하지 않는다. 원본 격자, 1차원 테이블 후보, 2차원 교차표 후보를 함께 제공한다. ")
                    .append("AI 검수 단계에서 사용자 메시지와 기존 지식의 연관성을 확인한 뒤 제품/규격/색상/가격/조건/프로세스 의미를 확정해야 한다.\n\n");

            int sheetCount = Math.min(workbook.getNumberOfSheets(), MAX_SCAN_SHEETS);
            if (workbook.getNumberOfSheets() > MAX_SCAN_SHEETS) {
                warnings.add("시트 수가 많아 앞 " + MAX_SCAN_SHEETS + "개 시트만 분석했습니다.");
            }

            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                Map<String, Object> sheetMeta = analyzeSheet(sheet, evaluator, knowledgeText);
                sheetMetas.add(sheetMeta);
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("parser", "RagExcelKnowledgeExtractor");
            metadata.put("parserVersion", "20260611-generic-grid-v2");
            metadata.put("strategy", "RAW_GRID_PLUS_ONE_DIMENSION_AND_TWO_DIMENSION_CANDIDATES");
            metadata.put("workbookSheetCount", workbook.getNumberOfSheets());
            metadata.put("analyzedSheetCount", sheetCount);
            metadata.put("sheets", sheetMetas);
            metadata.put("warnings", warnings);

            return new RagUploadedKnowledgeDocument(
                    safe(file.getOriginalFilename(), "upload.xlsx"),
                    file.getContentType(),
                    "EXCEL_STRUCTURED_GRID",
                    knowledgeText.toString().trim(),
                    metadata
            );
        } catch (Exception e) {
            throw new IllegalStateException("엑셀 학습 파일 분석 실패: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> analyzeSheet(Sheet sheet,
                                             FormulaEvaluator evaluator,
                                             StringBuilder knowledgeText) {
        Map<String, Object> sheetMeta = new LinkedHashMap<>();
        sheetMeta.put("sheetName", sheet.getSheetName());

        UsedRange used = findUsedRange(sheet, evaluator);
        if (used == null) {
            sheetMeta.put("empty", true);
            knowledgeText.append("[시트: ").append(sheet.getSheetName()).append("]\n비어 있는 시트입니다.\n\n");
            return sheetMeta;
        }

        List<Region> regions = detectRegions(sheet, used, evaluator);
        sheetMeta.put("empty", false);
        sheetMeta.put("usedRange", used.toA1());
        sheetMeta.put("regionCount", regions.size());

        List<Map<String, Object>> regionMetas = new ArrayList<>();
        knowledgeText.append("[시트: ").append(sheet.getSheetName()).append("]\n");
        knowledgeText.append("사용 범위: ").append(used.toA1()).append('\n');
        knowledgeText.append("감지 영역 수: ").append(regions.size()).append("\n\n");

        int regionNo = 1;
        for (Region region : regions) {
            Map<String, Object> regionMeta = analyzeRegion(sheet, evaluator, region, regionNo, knowledgeText);
            regionMetas.add(regionMeta);
            regionNo++;
        }

        sheetMeta.put("regions", regionMetas);
        return sheetMeta;
    }

    private Map<String, Object> analyzeRegion(Sheet sheet,
                                              FormulaEvaluator evaluator,
                                              Region region,
                                              int regionNo,
                                              StringBuilder knowledgeText) {
        List<List<String>> matrix = readMatrix(sheet, evaluator, region);
        Candidate oneDimensional = buildOneDimensionalCandidate(matrix, region);
        Candidate twoDimensional = buildTwoDimensionalCandidate(matrix, region);
        String structureType = structureType(oneDimensional, twoDimensional);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("regionNo", regionNo);
        meta.put("range", region.toA1());
        meta.put("rowCount", region.rowCount());
        meta.put("columnCount", region.colCount());
        meta.put("nonEmptyCellCount", countNonEmptyMatrix(matrix));
        meta.put("structureType", structureType);
        meta.put("oneDimensionalCandidate", oneDimensional.meta());
        meta.put("twoDimensionalCandidate", twoDimensional.meta());

        knowledgeText.append("[영역 ").append(regionNo).append(": ").append(region.toA1()).append("]\n");
        knowledgeText.append("구조 판정 후보: ").append(structureType).append('\n');
        knowledgeText.append("주의: 아래 후보는 확정 지식이 아니라 AI 검수용 구조화 후보입니다. 사용자 설명과 기존 지식까지 비교해 의미를 확정해야 합니다.\n\n");

        knowledgeText.append("원본 격자 미리보기:\n");
        for (String line : rawGridLines(matrix, region)) {
            knowledgeText.append(line).append('\n');
        }
        knowledgeText.append('\n');

        appendCandidateText(knowledgeText, oneDimensional);
        appendCandidateText(knowledgeText, twoDimensional);
        knowledgeText.append('\n');

        return meta;
    }

    private Candidate buildOneDimensionalCandidate(List<List<String>> matrix, Region region) {
        int rowCount = matrix.size();
        int colCount = rowCount == 0 ? 0 : matrix.get(0).size();
        if (rowCount < 2 || colCount < 1) {
            return Candidate.empty("ONE_DIMENSION_TABLE_CANDIDATE");
        }

        int headerRow = inferHeaderRow(matrix);
        List<String> headers = combinedHeaders(matrix, 0, headerRow);
        headers = makeUniqueHeaders(headers);

        List<String> lines = new ArrayList<>();
        int dataRows = 0;
        int omitted = 0;
        for (int r = headerRow + 1; r < rowCount; r++) {
            List<String> row = matrix.get(r);
            if (countNonEmpty(row) == 0) continue;
            dataRows++;
            if (lines.size() >= MAX_TABLE_ROWS) {
                omitted++;
                continue;
            }
            List<String> parts = new ArrayList<>();
            for (int c = 0; c < colCount; c++) {
                String value = row.get(c);
                if (!StringUtils.hasText(value)) continue;
                parts.add(headers.get(c) + "=" + value);
            }
            if (!parts.isEmpty()) {
                lines.add("- 엑셀 " + (region.minRow + r + 1) + "행: " + String.join(" | ", parts));
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("candidateType", "ONE_DIMENSION_TABLE_CANDIDATE");
        meta.put("available", dataRows > 0 && headers.stream().anyMatch(StringUtils::hasText));
        meta.put("headerRowNo", region.minRow + headerRow + 1);
        meta.put("headers", headers);
        meta.put("dataRowCount", dataRows);
        meta.put("omittedRowCount", omitted);

        List<String> text = new ArrayList<>();
        text.add("1차원 테이블 후보:");
        text.add("- 헤더 행: 엑셀 " + (region.minRow + headerRow + 1) + "행");
        text.add("- 헤더: " + String.join(" | ", headers));
        text.addAll(lines);
        if (omitted > 0) text.add("- 생략된 데이터 행 수: " + omitted);
        return new Candidate("ONE_DIMENSION_TABLE_CANDIDATE", dataRows > 0, meta, text);
    }

    private Candidate buildTwoDimensionalCandidate(List<List<String>> matrix, Region region) {
        int rowCount = matrix.size();
        int colCount = rowCount == 0 ? 0 : matrix.get(0).size();
        if (rowCount < 2 || colCount < 2) {
            return Candidate.empty("TWO_DIMENSION_MATRIX_CANDIDATE");
        }

        int headerBandRows = inferHeaderBandRows(matrix);
        int leftHeaderCols = inferLeftHeaderCols(matrix, headerBandRows);
        if (headerBandRows >= rowCount || leftHeaderCols >= colCount) {
            return Candidate.empty("TWO_DIMENSION_MATRIX_CANDIDATE");
        }

        List<String> columnLabels = new ArrayList<>();
        for (int c = leftHeaderCols; c < colCount; c++) {
            String label = joinNonBlankColumn(matrix, 0, headerBandRows - 1, c);
            columnLabels.add(StringUtils.hasText(label) ? label : "열" + (region.minCol + c + 1));
        }

        List<String> rowAxisNames = new ArrayList<>();
        for (int c = 0; c < leftHeaderCols; c++) {
            String axisName = joinNonBlankColumn(matrix, 0, headerBandRows - 1, c);
            rowAxisNames.add(StringUtils.hasText(axisName) ? axisName : "행축" + (c + 1));
        }

        List<String> lines = new ArrayList<>();
        int factCount = 0;
        int omitted = 0;
        Set<String> rowLabelSamples = new LinkedHashSet<>();
        for (int r = headerBandRows; r < rowCount; r++) {
            String rowLabel = joinNonBlankRow(matrix.get(r), 0, leftHeaderCols - 1);
            if (!StringUtils.hasText(rowLabel)) {
                rowLabel = "엑셀 " + (region.minRow + r + 1) + "행";
            }
            rowLabelSamples.add(rowLabel);
            for (int c = leftHeaderCols; c < colCount; c++) {
                String value = matrix.get(r).get(c);
                if (!StringUtils.hasText(value)) continue;
                factCount++;
                if (lines.size() >= MAX_MATRIX_FACTS) {
                    omitted++;
                    continue;
                }
                String columnLabel = columnLabels.get(c - leftHeaderCols);
                lines.add("- " + rowLabel + " × " + columnLabel + " => " + value
                        + " (셀 " + toA1(region.minRow + r, region.minCol + c) + ")");
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("candidateType", "TWO_DIMENSION_MATRIX_CANDIDATE");
        meta.put("available", factCount > 0);
        meta.put("headerBandRows", headerBandRows);
        meta.put("leftHeaderColumns", leftHeaderCols);
        meta.put("headerRowNos", absoluteRows(region.minRow, 0, headerBandRows - 1));
        meta.put("rowAxisNames", rowAxisNames);
        meta.put("columnLabels", columnLabels.stream().limit(80).toList());
        meta.put("rowLabelSamples", rowLabelSamples.stream().limit(80).toList());
        meta.put("factCount", factCount);
        meta.put("omittedFactCount", omitted);

        List<String> text = new ArrayList<>();
        text.add("2차원 교차표 후보:");
        text.add("- 열 헤더 범위: 엑셀 " + (region.minRow + 1) + "~" + (region.minRow + headerBandRows) + "행");
        text.add("- 행축 후보: " + String.join(" / ", rowAxisNames));
        text.add("- 열축 후보: " + String.join(" | ", columnLabels.stream().limit(40).toList()));
        text.addAll(lines);
        if (omitted > 0) text.add("- 생략된 교차값 수: " + omitted);
        return new Candidate("TWO_DIMENSION_MATRIX_CANDIDATE", factCount > 0, meta, text);
    }

    private String structureType(Candidate one, Candidate two) {
        boolean hasOne = one.available();
        boolean hasTwo = two.available();
        if (hasOne && hasTwo) return "TABLE_AND_MATRIX_CANDIDATES";
        if (hasTwo) return "TWO_DIMENSION_MATRIX_CANDIDATE";
        if (hasOne) return "ONE_DIMENSION_TABLE_CANDIDATE";
        return "RAW_GRID_ONLY";
    }

    private void appendCandidateText(StringBuilder sb, Candidate candidate) {
        if (!candidate.available()) {
            sb.append(candidate.type()).append(": 사용 가능한 후보가 충분하지 않습니다.\n\n");
            return;
        }
        for (String line : candidate.textLines()) {
            sb.append(line).append('\n');
        }
        sb.append('\n');
    }

    private UsedRange findUsedRange(Sheet sheet, FormulaEvaluator evaluator) {
        int minRow = Integer.MAX_VALUE;
        int maxRow = -1;
        int minCol = Integer.MAX_VALUE;
        int maxCol = -1;

        for (Row row : sheet) {
            if (row == null) continue;
            for (int c = Math.max(0, row.getFirstCellNum()); c < Math.max(0, row.getLastCellNum()); c++) {
                if (StringUtils.hasText(cellValue(sheet, row.getRowNum(), c, evaluator))) {
                    minRow = Math.min(minRow, row.getRowNum());
                    maxRow = Math.max(maxRow, row.getRowNum());
                    minCol = Math.min(minCol, c);
                    maxCol = Math.max(maxCol, c);
                }
            }
        }

        for (CellRangeAddress merged : sheet.getMergedRegions()) {
            String topLeft = cellValue(sheet, merged.getFirstRow(), merged.getFirstColumn(), evaluator);
            if (StringUtils.hasText(topLeft)) {
                minRow = Math.min(minRow, merged.getFirstRow());
                maxRow = Math.max(maxRow, merged.getLastRow());
                minCol = Math.min(minCol, merged.getFirstColumn());
                maxCol = Math.max(maxCol, merged.getLastColumn());
            }
        }

        if (maxRow < 0 || maxCol < 0) return null;
        return new UsedRange(minRow, maxRow, minCol, maxCol);
    }

    private List<Region> detectRegions(Sheet sheet, UsedRange used, FormulaEvaluator evaluator) {
        List<int[]> rowSegments = new ArrayList<>();
        int rowStart = -1;
        for (int r = used.minRow; r <= used.maxRow; r++) {
            boolean hasValue = rowHasAny(sheet, r, used.minCol, used.maxCol, evaluator);
            if (hasValue && rowStart < 0) rowStart = r;
            if ((!hasValue || r == used.maxRow) && rowStart >= 0) {
                int end = hasValue && r == used.maxRow ? r : r - 1;
                rowSegments.add(new int[]{rowStart, end});
                rowStart = -1;
            }
        }

        List<Region> regions = new ArrayList<>();
        for (int[] rowSegment : rowSegments) {
            int colStart = -1;
            for (int c = used.minCol; c <= used.maxCol; c++) {
                boolean hasValue = colHasAny(sheet, rowSegment[0], rowSegment[1], c, evaluator);
                if (hasValue && colStart < 0) colStart = c;
                if ((!hasValue || c == used.maxCol) && colStart >= 0) {
                    int end = hasValue && c == used.maxCol ? c : c - 1;
                    regions.add(new Region(rowSegment[0], rowSegment[1], colStart, end));
                    colStart = -1;
                }
            }
        }

        if (regions.isEmpty()) {
            regions.add(new Region(used.minRow, used.maxRow, used.minCol, used.maxCol));
        }
        return regions;
    }

    private List<List<String>> readMatrix(Sheet sheet, FormulaEvaluator evaluator, Region region) {
        List<List<String>> matrix = new ArrayList<>();
        for (int r = region.minRow; r <= region.maxRow; r++) {
            List<String> row = new ArrayList<>();
            for (int c = region.minCol; c <= region.maxCol; c++) {
                row.add(truncateCell(cellValue(sheet, r, c, evaluator)));
            }
            matrix.add(row);
        }
        return matrix;
    }

    private boolean rowHasAny(Sheet sheet, int rowNo, int minCol, int maxCol, FormulaEvaluator evaluator) {
        for (int c = minCol; c <= maxCol; c++) {
            if (StringUtils.hasText(cellValue(sheet, rowNo, c, evaluator))) return true;
        }
        return false;
    }

    private boolean colHasAny(Sheet sheet, int minRow, int maxRow, int colNo, FormulaEvaluator evaluator) {
        for (int r = minRow; r <= maxRow; r++) {
            if (StringUtils.hasText(cellValue(sheet, r, colNo, evaluator))) return true;
        }
        return false;
    }

    private String cellValue(Sheet sheet, int rowNo, int colNo, FormulaEvaluator evaluator) {
        Cell cell = directOrMergedCell(sheet, rowNo, colNo);
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

    private Cell directOrMergedCell(Sheet sheet, int rowNo, int colNo) {
        Row row = sheet.getRow(rowNo);
        Cell direct = row == null ? null : row.getCell(colNo);
        if (direct != null && StringUtils.hasText(formatter.formatCellValue(direct).trim())) {
            return direct;
        }
        for (CellRangeAddress merged : sheet.getMergedRegions()) {
            if (merged.isInRange(rowNo, colNo)) {
                Row firstRow = sheet.getRow(merged.getFirstRow());
                return firstRow == null ? null : firstRow.getCell(merged.getFirstColumn());
            }
        }
        return direct;
    }

    private int inferHeaderRow(List<List<String>> matrix) {
        int maxScan = Math.min(matrix.size() - 1, 8);
        int bestRow = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int r = 0; r <= maxScan; r++) {
            List<String> row = matrix.get(r);
            int nonEmpty = countNonEmpty(row);
            if (nonEmpty < 2 && matrix.get(0).size() > 1) continue;
            int text = countTextLike(row);
            int numeric = countNumericLike(row);
            double nextAvg = averageNextNonEmpty(matrix, r + 1, Math.min(matrix.size() - 1, r + 3));
            double score = nonEmpty * 2.0 + text * 0.8 - numeric * 0.7 + Math.min(nextAvg, nonEmpty) * 0.6;
            if (r > 0 && countNonEmpty(matrix.get(r - 1)) <= 1) score += 1.0;
            if (score > bestScore) {
                bestScore = score;
                bestRow = r;
            }
        }
        return bestRow;
    }

    private int inferHeaderBandRows(List<List<String>> matrix) {
        int rowCount = matrix.size();
        if (rowCount <= 1) return 1;
        int max = Math.min(3, rowCount - 1);
        int best = 1;
        double bestScore = matrixScore(matrix, 1, 1);
        for (int headerRows = 1; headerRows <= max; headerRows++) {
            for (int leftCols = 1; leftCols <= Math.min(3, matrix.get(0).size() - 1); leftCols++) {
                double score = matrixScore(matrix, headerRows, leftCols);
                if (score > bestScore + 0.75) {
                    bestScore = score;
                    best = headerRows;
                }
            }
        }
        return best;
    }

    private int inferLeftHeaderCols(List<List<String>> matrix, int headerBandRows) {
        int colCount = matrix.get(0).size();
        if (colCount <= 1) return 1;
        int best = 1;
        double bestScore = matrixScore(matrix, headerBandRows, 1);
        for (int leftCols = 1; leftCols <= Math.min(3, colCount - 1); leftCols++) {
            double score = matrixScore(matrix, headerBandRows, leftCols);
            if (score > bestScore + 0.75) {
                bestScore = score;
                best = leftCols;
            }
        }
        return best;
    }

    private double matrixScore(List<List<String>> matrix, int headerRows, int leftCols) {
        int rows = matrix.size();
        int cols = matrix.get(0).size();
        if (headerRows >= rows || leftCols >= cols) return -1_000;

        int columnHeaderLabels = 0;
        for (int c = leftCols; c < cols; c++) {
            if (StringUtils.hasText(joinNonBlankColumn(matrix, 0, headerRows - 1, c))) columnHeaderLabels++;
        }

        int rowHeaderLabels = 0;
        for (int r = headerRows; r < rows; r++) {
            if (StringUtils.hasText(joinNonBlankRow(matrix.get(r), 0, leftCols - 1))) rowHeaderLabels++;
        }

        int interiorValues = 0;
        for (int r = headerRows; r < rows; r++) {
            for (int c = leftCols; c < cols; c++) {
                if (StringUtils.hasText(matrix.get(r).get(c))) interiorValues++;
            }
        }

        int headerText = 0;
        for (int r = 0; r < headerRows; r++) {
            headerText += countTextLike(matrix.get(r));
        }

        return columnHeaderLabels * 1.8 + rowHeaderLabels * 1.5 + Math.min(interiorValues, 120) * 0.2 + headerText * 0.3
                - Math.max(0, headerRows - 2) * 1.2 - Math.max(0, leftCols - 2) * 0.8;
    }

    private List<String> combinedHeaders(List<List<String>> matrix, int fromRow, int toRow) {
        int colCount = matrix.get(0).size();
        List<String> headers = new ArrayList<>();
        for (int c = 0; c < colCount; c++) {
            List<String> parts = new ArrayList<>();
            for (int r = fromRow; r <= toRow && r < matrix.size(); r++) {
                if (countNonEmpty(matrix.get(r)) <= 1 && toRow > fromRow) continue;
                String value = matrix.get(r).get(c);
                if (StringUtils.hasText(value) && !parts.contains(value)) parts.add(value);
            }
            String header = String.join(" / ", parts).trim();
            headers.add(StringUtils.hasText(header) ? header : "열" + (c + 1));
        }
        return headers;
    }

    private List<String> makeUniqueHeaders(List<String> headers) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String base = StringUtils.hasText(headers.get(i)) ? headers.get(i).trim() : "열" + (i + 1);
            int count = seen.getOrDefault(base, 0) + 1;
            seen.put(base, count);
            result.add(count == 1 ? base : base + "#" + count);
        }
        return result;
    }

    private String joinNonBlankColumn(List<List<String>> matrix, int fromRow, int toRow, int col) {
        List<String> parts = new ArrayList<>();
        for (int r = fromRow; r <= toRow && r < matrix.size(); r++) {
            if (col >= matrix.get(r).size()) continue;
            String value = matrix.get(r).get(col);
            if (StringUtils.hasText(value) && !parts.contains(value)) parts.add(value);
        }
        return String.join(" / ", parts).trim();
    }

    private String joinNonBlankRow(List<String> row, int fromCol, int toCol) {
        List<String> parts = new ArrayList<>();
        for (int c = fromCol; c <= toCol && c < row.size(); c++) {
            String value = row.get(c);
            if (StringUtils.hasText(value) && !parts.contains(value)) parts.add(value);
        }
        return String.join(" / ", parts).trim();
    }

    private List<String> rawGridLines(List<List<String>> matrix, Region region) {
        List<String> lines = new ArrayList<>();
        int rows = Math.min(matrix.size(), MAX_RAW_GRID_ROWS);
        int cols = matrix.isEmpty() ? 0 : Math.min(matrix.get(0).size(), MAX_RAW_GRID_COLS);
        for (int r = 0; r < rows; r++) {
            List<String> parts = new ArrayList<>();
            parts.add("엑셀" + (region.minRow + r + 1) + "행");
            for (int c = 0; c < cols; c++) {
                parts.add(toColumnName(region.minCol + c) + "=" + escape(matrix.get(r).get(c)));
            }
            lines.add(String.join(" | ", parts));
        }
        if (matrix.size() > rows) lines.add("... 생략된 행 수: " + (matrix.size() - rows));
        if (!matrix.isEmpty() && matrix.get(0).size() > cols) lines.add("... 생략된 열 수: " + (matrix.get(0).size() - cols));
        return lines;
    }

    private int countNonEmptyMatrix(List<List<String>> matrix) {
        int count = 0;
        for (List<String> row : matrix) count += countNonEmpty(row);
        return count;
    }

    private int countNonEmpty(List<String> row) {
        int count = 0;
        for (String value : row) if (StringUtils.hasText(value)) count++;
        return count;
    }

    private int countTextLike(List<String> row) {
        int count = 0;
        for (String value : row) {
            if (StringUtils.hasText(value) && !isNumericLike(value)) count++;
        }
        return count;
    }

    private int countNumericLike(List<String> row) {
        int count = 0;
        for (String value : row) {
            if (isNumericLike(value)) count++;
        }
        return count;
    }

    private double averageNextNonEmpty(List<List<String>> matrix, int fromRow, int toRow) {
        if (fromRow >= matrix.size()) return 0;
        int rows = 0;
        int total = 0;
        for (int r = fromRow; r <= toRow && r < matrix.size(); r++) {
            rows++;
            total += countNonEmpty(matrix.get(r));
        }
        return rows == 0 ? 0 : (double) total / rows;
    }

    private boolean isNumericLike(String value) {
        if (!StringUtils.hasText(value)) return false;
        String s = value.replace(",", "").replace("원", "").replace("%", "").trim();
        return s.matches("[-+]?\\d+(\\.\\d+)?");
    }

    private List<Integer> absoluteRows(int baseRow, int from, int to) {
        List<Integer> rows = new ArrayList<>();
        for (int i = from; i <= to; i++) rows.add(baseRow + i + 1);
        return rows;
    }

    private String truncateCell(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (clean.length() <= MAX_CELL_TEXT) return clean;
        return clean.substring(0, MAX_CELL_TEXT) + "...";
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("|", "｜").trim();
    }

    private String safe(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String toA1(int rowZeroBased, int colZeroBased) {
        return toColumnName(colZeroBased) + (rowZeroBased + 1);
    }

    private String toColumnName(int colZeroBased) {
        int col = colZeroBased + 1;
        StringBuilder sb = new StringBuilder();
        while (col > 0) {
            int rem = (col - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            col = (col - 1) / 26;
        }
        return sb.toString();
    }

    private record UsedRange(int minRow, int maxRow, int minCol, int maxCol) {
        String toA1() {
            return toColumnNameStatic(minCol) + (minRow + 1) + ":" + toColumnNameStatic(maxCol) + (maxRow + 1);
        }
    }

    private record Region(int minRow, int maxRow, int minCol, int maxCol) {
        int rowCount() { return maxRow - minRow + 1; }
        int colCount() { return maxCol - minCol + 1; }
        String toA1() {
            return toColumnNameStatic(minCol) + (minRow + 1) + ":" + toColumnNameStatic(maxCol) + (maxRow + 1);
        }
    }

    private record Candidate(String type, boolean available, Map<String, Object> meta, List<String> textLines) {
        static Candidate empty(String type) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("candidateType", type);
            meta.put("available", false);
            return new Candidate(type, false, meta, List.of());
        }
    }

    private static String toColumnNameStatic(int colZeroBased) {
        int col = colZeroBased + 1;
        StringBuilder sb = new StringBuilder();
        while (col > 0) {
            int rem = (col - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            col = (col - 1) / 26;
        }
        return sb.toString();
    }
}
