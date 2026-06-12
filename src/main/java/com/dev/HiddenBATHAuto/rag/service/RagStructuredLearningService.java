package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.dev.HiddenBATHAuto.rag.repository.RagStructuredRepository;
import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;

@Service
public class RagStructuredLearningService {

    private final RagStructuredRepository structuredRepository;
    private final RagExcelStructuredTableParser excelParser;

    public RagStructuredLearningService(RagStructuredRepository structuredRepository,
                                        RagExcelStructuredTableParser excelParser) {
        this.structuredRepository = structuredRepository;
        this.excelParser = excelParser;
    }

    /**
     * 저장 없이 업로드 엑셀의 구조만 분석합니다.
     * 전처리 단계에서 사용자 문장과 함께 표준 학습 프롬프트를 만들 때 사용합니다.
     */
    public Map<String, Object> analyzeUploadedKnowledge(MultipartFile file,
                                                        String instruction,
                                                        String topic) {
        String original = file == null || file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        if (file == null || file.isEmpty()) {
            return Map.of(
                    "structured", false,
                    "filename", original,
                    "originalFilename", original,
                    "sourceType", "EMPTY",
                    "reason", "빈 파일입니다."
            );
        }
        if (!isExcel(original)) {
            return Map.of(
                    "structured", false,
                    "filename", original,
                    "originalFilename", original,
                    "sourceType", "NON_EXCEL",
                    "reason", "엑셀이 아닌 파일은 구조화 테이블이 아니라 벡터 지식 보조자료로 사용합니다."
            );
        }
        Map<String, Object> parsed = excelParser.parse(file, instruction, topic);
        parsed.put("structured", true);
        parsed.put("filename", original);
        parsed.put("sourceType", "EXCEL");
        parsed.put("tableCount", list(parsed.get("tables")).size());
        parsed.put("matrixCount", list(parsed.get("matrices")).size());
        return parsed;
    }

    /**
     * 기존 호출부 호환용입니다.
     * 전처리 정보가 없으면 엑셀 파서의 추정값으로 저장하되, 역할/범위가 애매한 경우 PENDING_REVIEW로 저장됩니다.
     */
    @Transactional("ragTransactionManager")
    public Map<String, Object> ingestUploadedKnowledge(UUID projectId,
                                                       UUID versionId,
                                                       String topic,
                                                       String instruction,
                                                       MultipartFile file) {
        Map<String, Object> parsed = analyzeUploadedKnowledge(file, instruction, topic);
        if (!Boolean.TRUE.equals(parsed.get("structured"))) {
            return parsed;
        }
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("filename", parsed.get("filename"));
        material.put("semanticRole", parsed.get("semanticRole"));
        material.put("operation", Boolean.TRUE.equals(parsed.get("replaceRequested")) ? "REPLACE" : "UNKNOWN");
        material.put("scopeLevel", "UNKNOWN");
        material.put("series", "");
        material.put("item", "");
        material.put("artifactKey", buildArtifactKey(topic, str(parsed.get("semanticRole"), "GENERAL_KNOWLEDGE_TABLE"), "UNKNOWN", "", "", str(parsed.get("filename"), "upload")));
        material.put("missingFields", List.of("전처리 없이 호출되어 적용 범위/처리 방식 확정이 필요합니다."));
        material.put("canStoreStructured", !isAmbiguousRole(str(parsed.get("semanticRole"), "")));
        return ingestPreparedKnowledge(projectId, versionId, topic, instruction, material, file, parsed, false);
    }

    /**
     * 전처리에서 확정된 역할/대상/교체 범위를 기준으로 구조화 엑셀을 저장합니다.
     */
    @Transactional("ragTransactionManager")
    public Map<String, Object> ingestPreparedKnowledge(UUID projectId,
                                                       UUID versionId,
                                                       String topic,
                                                       String normalizedPrompt,
                                                       Map<String, Object> material,
                                                       MultipartFile file,
                                                       Map<String, Object> parsed,
                                                       boolean forceSave) {
        if (projectId == null) throw new IllegalArgumentException("projectId가 필요합니다.");
        if (versionId == null) throw new IllegalArgumentException("versionId가 필요합니다.");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("구조화할 파일이 필요합니다.");

        String original = file.getOriginalFilename() == null ? str(material.get("filename"), "upload.xlsx") : file.getOriginalFilename();
        if (!isExcel(original)) {
            return Map.of(
                    "structured", false,
                    "reason", "엑셀이 아닌 파일은 벡터 지식으로만 저장합니다.",
                    "originalFilename", original
            );
        }
        if (parsed == null || parsed.isEmpty()) {
            parsed = excelParser.parse(file, normalizedPrompt, topic);
        }

        String semanticRole = str(material.get("semanticRole"), str(parsed.get("semanticRole"), "GENERAL_KNOWLEDGE_TABLE"));
        String operation = normalizeOperation(str(material.get("operation"), Boolean.TRUE.equals(parsed.get("replaceRequested")) ? "REPLACE" : "UNKNOWN"));
        String scopeLevel = normalizeScope(str(material.get("scopeLevel"), "UNKNOWN"));
        String series = str(material.get("series"), "");
        String item = str(material.get("item"), "");
        String artifactKey = str(material.get("artifactKey"), buildArtifactKey(topic, semanticRole, scopeLevel, series, item, original));
        List<Object> missingFields = RagJsonUtils.childList(material, "missingFields");

        boolean hasMissing = !missingFields.isEmpty();
        boolean active = isCalculableRole(semanticRole) && !hasMissing;
        if (forceSave && isCalculableRole(semanticRole) && !isAmbiguousRole(semanticRole)) {
            // 강제 저장은 허용하되, 부족 정보가 있으면 가격계산 ACTIVE가 아니라 검토 상태로 둡니다.
            active = !hasMissing;
        }
        String status = active ? "ACTIVE" : "PENDING_REVIEW";
        UUID artifactId = UUID.randomUUID();

        if ("REPLACE".equals(operation) && active) {
            structuredRepository.deactivateArtifacts(
                    projectId,
                    versionId,
                    topic,
                    semanticRole,
                    artifactKey,
                    artifactId,
                    StringUtils.hasText(normalizedPrompt) ? normalizedPrompt : "전처리 확정에 따른 구조화 엑셀 교체"
            );
        }

        Map<String, Object> parsedForSave = new LinkedHashMap<>(parsed);
        parsedForSave.put("semanticRole", semanticRole);
        parsedForSave.put("preprocessedMaterial", material);
        parsedForSave.put("normalizedPrompt", normalizedPrompt);
        parsedForSave.put("operation", operation);
        parsedForSave.put("scopeLevel", scopeLevel);
        parsedForSave.put("series", series);
        parsedForSave.put("item", item);
        parsedForSave.put("artifactKey", artifactKey);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("instruction", normalizedPrompt);
        metadata.put("operation", operation);
        metadata.put("replaceRequested", "REPLACE".equals(operation));
        metadata.put("topic", topic);
        metadata.put("scopeLevel", scopeLevel);
        metadata.put("series", series);
        metadata.put("item", item);
        metadata.put("artifactKey", artifactKey);
        metadata.put("missingFields", missingFields);
        metadata.put("activeForCalculation", active);
        metadata.put("statusReason", active
                ? "전처리에서 역할/범위/처리방식이 확정되어 ACTIVE 처리했습니다."
                : "역할/범위/처리방식 중 일부가 불명확하여 PENDING_REVIEW로 저장하거나 구조화 저장을 보류했습니다.");
        metadata.put("preprocessor", "RagLearningPromptComposer");

        Map<String, Object> artifact = structuredRepository.insertArtifact(
                artifactId,
                projectId,
                versionId,
                topic,
                artifactKey,
                "EXCEL",
                semanticRole,
                buildTitle(topic, semanticRole, series, item, original),
                original,
                str(parsed.get("fingerprint"), null),
                active,
                status,
                parsedForSave,
                metadata
        );

        List<Map<String, Object>> savedTables = saveTables(projectId, versionId, topic, artifactId, parsedForSave, active, material);
        List<Map<String, Object>> savedMatrices = saveMatrices(projectId, versionId, topic, artifactId, parsedForSave, active, material);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("structured", true);
        result.put("active", active);
        result.put("status", status);
        result.put("semanticRole", semanticRole);
        result.put("operation", operation);
        result.put("scopeLevel", scopeLevel);
        result.put("series", series);
        result.put("item", item);
        result.put("artifactKey", artifactKey);
        result.put("missingFields", missingFields);
        result.put("artifact", artifact);
        result.put("savedTableCount", savedTables.size());
        result.put("savedMatrixCount", savedMatrices.size());
        result.put("savedTables", savedTables);
        result.put("savedMatrices", savedMatrices);
        result.put("warnings", parsed.getOrDefault("warnings", List.of()));
        result.put("summaryText", parsed.get("summaryText"));
        result.put("message", buildUserMessage(status, semanticRole, savedTables.size(), savedMatrices.size(), "REPLACE".equals(operation), missingFields));
        return result;
    }

    @Transactional("ragTransactionManager")
    public void resetStructuredKnowledge(UUID projectId,
                                         UUID versionId,
                                         String topic,
                                         boolean resetWholeVersion,
                                         String reason) {
        structuredRepository.resetStructuredKnowledge(projectId, versionId, topic, resetWholeVersion, reason);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> saveTables(UUID projectId,
                                                 UUID versionId,
                                                 String topic,
                                                 UUID artifactId,
                                                 Map<String, Object> parsed,
                                                 boolean artifactActive,
                                                 Map<String, Object> material) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object raw = parsed.get("tables");
        if (!(raw instanceof List<?> tables)) return result;
        for (Object obj : tables) {
            if (!(obj instanceof Map<?, ?> anyMap)) continue;
            Map<String, Object> table = cast(anyMap);
            String semanticRole = str(table.get("semanticRole"), str(parsed.get("semanticRole"), "GENERAL_KNOWLEDGE_TABLE"));
            if (isGeneralRole(semanticRole)) semanticRole = str(parsed.get("semanticRole"), semanticRole);
            boolean active = artifactActive && isCalculableRole(semanticRole);
            UUID tableId = UUID.randomUUID();
            Map<String, Object> tableMetadata = new LinkedHashMap<>(castMap(table.getOrDefault("metadata", Map.of())));
            tableMetadata.put("preprocessedMaterial", material);
            Map<String, Object> saved = structuredRepository.insertStructuredTable(
                    tableId,
                    artifactId,
                    projectId,
                    versionId,
                    topic,
                    str(table.get("tableKey"), tableId.toString()),
                    semanticRole,
                    str(table.get("sheetName"), null),
                    str(table.get("rangeA1"), null),
                    table.getOrDefault("headers", List.of()),
                    tableMetadata,
                    active
            );
            List<Object> rows = table.get("rows") instanceof List<?> list ? (List<Object>) list : List.of();
            int rowNo = 1;
            for (Object rowObj : rows) {
                if (!(rowObj instanceof Map<?, ?> rowMap)) continue;
                Map<String, Object> row = cast(rowMap);
                structuredRepository.insertStructuredTableRow(
                        UUID.randomUUID(),
                        tableId,
                        rowNo++,
                        row,
                        row.toString()
                );
            }
            result.add(saved);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> saveMatrices(UUID projectId,
                                                   UUID versionId,
                                                   String topic,
                                                   UUID artifactId,
                                                   Map<String, Object> parsed,
                                                   boolean artifactActive,
                                                   Map<String, Object> material) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object raw = parsed.get("matrices");
        if (!(raw instanceof List<?> matrices)) return result;
        for (Object obj : matrices) {
            if (!(obj instanceof Map<?, ?> anyMap)) continue;
            Map<String, Object> matrix = cast(anyMap);
            String semanticRole = str(matrix.get("semanticRole"), str(parsed.get("semanticRole"), "GENERAL_MATRIX"));
            if (isGeneralRole(semanticRole)) semanticRole = str(parsed.get("semanticRole"), semanticRole);
            boolean active = artifactActive && isCalculableRole(semanticRole);
            UUID matrixId = UUID.randomUUID();
            Map<String, Object> matrixMetadata = new LinkedHashMap<>(castMap(matrix.getOrDefault("metadata", Map.of())));
            matrixMetadata.put("preprocessedMaterial", material);
            Map<String, Object> saved = structuredRepository.insertPriceMatrix(
                    matrixId,
                    artifactId,
                    projectId,
                    versionId,
                    topic,
                    str(matrix.get("matrixKey"), matrixId.toString()),
                    semanticRole,
                    str(matrix.get("sheetName"), null),
                    str(matrix.get("rangeA1"), null),
                    str(matrix.get("rowAxisName"), null),
                    str(matrix.get("colAxisName"), null),
                    matrix.getOrDefault("roundingPolicy", Map.of()),
                    matrixMetadata,
                    active
            );

            List<Object> cells = matrix.get("cells") instanceof List<?> list ? (List<Object>) list : List.of();
            for (Object cellObj : cells) {
                if (!(cellObj instanceof Map<?, ?> cellMap)) continue;
                Map<String, Object> cell = cast(cellMap);
                structuredRepository.insertPriceMatrixCell(
                        UUID.randomUUID(),
                        matrixId,
                        str(cell.get("rowKey"), ""),
                        str(cell.get("colKey"), ""),
                        decimal(cell.get("rowNumeric")),
                        decimal(cell.get("colNumeric")),
                        decimal(cell.get("numericValue")),
                        str(cell.get("displayValue"), null),
                        cell
                );
            }
            result.add(saved);
        }
        return result;
    }

    private boolean isCalculableRole(String semanticRole) {
        if (!StringUtils.hasText(semanticRole)) return false;
        String role = semanticRole.toUpperCase(Locale.ROOT);
        return role.contains("PRICE")
                || role.contains("MATRIX")
                || role.contains("COLOR_RULE")
                || role.contains("SIZE_CONSTRAINT")
                || role.contains("OPTION")
                || role.contains("HANDLE")
                || role.contains("SINK");
    }

    private boolean isAmbiguousRole(String role) {
        return !StringUtils.hasText(role) || isGeneralRole(role) || "UNKNOWN".equalsIgnoreCase(role);
    }

    private boolean isGeneralRole(String role) {
        return "GENERAL_KNOWLEDGE_TABLE".equals(role) || "GENERAL_MATRIX".equals(role);
    }

    private boolean isExcel(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private String normalizeOperation(String operation) {
        if (!StringUtils.hasText(operation)) return "UNKNOWN";
        String op = operation.trim().toUpperCase(Locale.ROOT);
        if (op.contains("REPLACE") || op.contains("교체") || op.contains("UPDATE")) return "REPLACE";
        if (op.contains("ADD") || op.contains("추가") || op.contains("신규")) return "ADD";
        return "UNKNOWN";
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) return "UNKNOWN";
        String s = scope.trim().toUpperCase(Locale.ROOT);
        if (s.contains("TOPIC") || s.contains("전체")) return "TOPIC";
        if (s.contains("SERIES") || s.contains("시리즈")) return "SERIES";
        if (s.contains("ITEM") || s.contains("품목")) return "ITEM";
        if (s.contains("MULTI")) return "MULTI_ITEM";
        return "UNKNOWN";
    }

    private String buildArtifactKey(String topic, String semanticRole, String scopeLevel, String series, String item, String filename) {
        String key = String.join(":",
                StringUtils.hasText(topic) ? topic.trim() : "GLOBAL",
                StringUtils.hasText(semanticRole) ? semanticRole.trim() : "GENERAL",
                StringUtils.hasText(scopeLevel) ? scopeLevel.trim() : "UNKNOWN",
                StringUtils.hasText(series) ? series.trim() : "ALL_SERIES",
                StringUtils.hasText(item) ? item.trim() : "ALL_ITEMS"
        );
        if ("UNKNOWN".equalsIgnoreCase(scopeLevel)) key += ":" + (StringUtils.hasText(filename) ? filename.trim() : "upload");
        key = key.replaceAll("[^0-9A-Za-z가-힣_:-]+", "_").replaceAll("_+", "_");
        return key.length() > 240 ? key.substring(0, 240) : key;
    }

    private String buildTitle(String topic, String role, String series, String item, String filename) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(topic)) sb.append(topic).append(" / ");
        if (StringUtils.hasText(series)) sb.append(series).append(" / ");
        if (StringUtils.hasText(item)) sb.append(item).append(" / ");
        sb.append(role).append(" / ").append(filename);
        return sb.toString();
    }

    private String buildUserMessage(String status,
                                    String semanticRole,
                                    int tableCount,
                                    int matrixCount,
                                    boolean replaceRequested,
                                    List<Object> missingFields) {
        StringBuilder sb = new StringBuilder();
        sb.append("구조화 엑셀 저장 상태: ").append(status)
                .append(" / 역할: ").append(semanticRole)
                .append(" / 1차원 표 ").append(tableCount).append("개")
                .append(" / 2차원 단가표 ").append(matrixCount).append("개");
        if (replaceRequested && "ACTIVE".equals(status)) sb.append(" / 동일 적용 범위의 기존 자료는 비활성화 후 교체했습니다.");
        if (!missingFields.isEmpty()) sb.append(" / 부족 정보: ").append(missingFields);
        if ("PENDING_REVIEW".equals(status)) {
            sb.append(" 가격계산 ACTIVE 자료로 쓰기 전에 역할/적용 범위/처리 방식을 확정해야 합니다.");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Object> list(Object value) {
        if (value instanceof List<?> l) return (List<Object>) l;
        return List.of();
    }

    private String str(Object value, String fallback) {
        if (value == null) return fallback;
        String s = String.valueOf(value);
        return StringUtils.hasText(s) ? s : fallback;
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            String s = String.valueOf(value).replace(",", "").trim();
            if (!StringUtils.hasText(s) || "null".equalsIgnoreCase(s)) return null;
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) result.put(String.valueOf(e.getKey()), e.getValue());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) return cast(map);
        return new LinkedHashMap<>();
    }
}
