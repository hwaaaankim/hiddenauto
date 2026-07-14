package com.dev.HiddenBATHAuto.rag.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.config.RagOpenAiProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 원본 업무 row를 공통 semantic 문서로 정규화합니다. */
@Service
public class RagSemanticSourceDocumentFactory {

    public static final Set<String> SUPPORTED_TABLES = Set.of(
            "rag_document", "rag_chunk", "rag_knowledge_node", "rag_knowledge_artifact",
            "rag_structured_table", "rag_structured_table_row", "rag_price_matrix", "rag_price_matrix_cell",
            "rag_dialog_rule", "rag_structured_pricing_rule", "rag_structured_override_rule",
            "rag_canonical_dataset", "rag_canonical_entity", "rag_canonical_fact",
            "rag_canonical_pricing_rule", "rag_canonical_dialog_flow", "rag_entity_alias",
            "rag_entity_asset_link");

    private static final Map<String, ParentScope> PARENT_SCOPES = Map.of(
            "rag_structured_table_row", new ParentScope("rag_structured_table", "table_id"),
            "rag_price_matrix_cell", new ParentScope("rag_price_matrix", "matrix_id"));

    private static final List<String> TITLE_FIELDS = List.of(
            "title", "display_name", "subject_name", "entity_key", "subject_key", "fact_key",
            "rule_key", "flow_key", "table_key", "matrix_key", "artifact_key", "node_key",
            "original_filename", "topic", "alias", "id");

    private static final List<String> ENTITY_TYPE_FIELDS = List.of("entity_type", "node_type", "fact_type", "artifact_type");
    private static final List<String> ENTITY_KEY_FIELDS = List.of(
            "entity_key", "subject_key", "node_key", "rule_key", "flow_key", "table_key",
            "matrix_key", "artifact_key", "alias", "id");
    private static final List<String> LARGE_FIELDS = List.of(
            "raw_text", "content", "summary", "source_message", "link_source_message", "instruction");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RagOpenAiProperties properties;

    public RagSemanticSourceDocumentFactory(
            @Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            RagOpenAiProperties properties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<SemanticDocument> load(UUID projectId,
                                           UUID versionId,
                                           String sourceTable,
                                           UUID sourceId) {
        String table = normalizeTable(sourceTable);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("sourceId", sourceId);
        String sql;
        ParentScope parentScope = PARENT_SCOPES.get(table);
        if (parentScope != null) {
            sql = "SELECT to_jsonb(c) AS row_json, p.project_id, p.version_id "
                    + "FROM public." + table + " c "
                    + "JOIN public." + parentScope.parentTable() + " p ON p.id = c." + parentScope.foreignKey() + " "
                    + "WHERE c.id = :sourceId AND p.project_id = :projectId AND p.version_id = :versionId";
        } else {
            sql = "SELECT to_jsonb(s) AS row_json, s.project_id, s.version_id "
                    + "FROM public." + table + " s "
                    + "WHERE s.id = :sourceId AND s.project_id = :projectId AND s.version_id = :versionId";
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> raw = parseRowJson(rows.get(0).get("row_json"));
        if (raw.isEmpty()) return Optional.empty();
        return Optional.of(toDocument(projectId, versionId, table, sourceId, raw));
    }

    public SemanticDocument toDocument(UUID projectId,
                                       UUID versionId,
                                       String table,
                                       UUID sourceId,
                                       Map<String, Object> row) {
        String sourceKind = sourceKind(table);
        String domain = domain(table, row);
        String entityType = firstText(row, ENTITY_TYPE_FIELDS);
        String entityKey = firstText(row, ENTITY_KEY_FIELDS);
        String title = firstText(row, TITLE_FIELDS);
        if (!StringUtils.hasText(title)) title = table + " / " + sourceId;

        LinkedHashSet<String> aliases = collectAliases(row);
        LinkedHashSet<String> keywords = collectKeywords(table, domain, sourceKind, row, aliases);
        String content = buildContent(table, title, row);
        content = truncate(content, properties.getSemanticContentMaxChars());
        Map<String, Object> metadata = compactMetadata(row);
        metadata.put("sourceTable", table);
        metadata.put("sourceId", sourceId);
        metadata.put("domain", domain);
        metadata.put("sourceKind", sourceKind);

        boolean active = active(row);
        Object sourceUpdatedAt = firstNonNull(row.get("updated_at"), row.get("created_at"));
        String hashInput = String.join("\n",
                table, sourceId.toString(), title, content,
                String.join("|", aliases), String.join("|", keywords),
                String.valueOf(active), String.valueOf(sourceUpdatedAt));

        return new SemanticDocument(
                projectId,
                versionId,
                table,
                sourceId,
                sourceKind,
                domain,
                nullIfBlank(entityType),
                nullIfBlank(entityKey),
                title,
                content,
                List.copyOf(keywords),
                List.copyOf(aliases),
                metadata,
                sha256(hashInput),
                active,
                sourceUpdatedAt);
    }

    public String embeddingInput(SemanticDocument document) {
        StringBuilder text = new StringBuilder();
        text.append("domain: ").append(document.domainKey()).append('\n');
        text.append("source: ").append(document.sourceTable()).append('\n');
        if (StringUtils.hasText(document.entityType())) text.append("entityType: ").append(document.entityType()).append('\n');
        if (StringUtils.hasText(document.entityKey())) text.append("entityKey: ").append(document.entityKey()).append('\n');
        text.append("title: ").append(document.title()).append('\n');
        if (!document.aliases().isEmpty()) text.append("aliases: ").append(String.join(", ", document.aliases())).append('\n');
        if (!document.keywords().isEmpty()) text.append("keywords: ").append(String.join(", ", document.keywords())).append('\n');
        text.append("content:\n").append(document.content());
        return truncate(text.toString(), properties.getSemanticEmbeddingInputChars());
    }

    private Map<String, Object> parseRowJson(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (raw == null) return Map.of();
        try {
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("원본 row JSON 변환 실패: " + e.getMessage(), e);
        }
    }

    private String buildContent(String table, String title, Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        sb.append("table: ").append(table).append('\n');
        sb.append("title: ").append(title).append('\n');
        appendMap(sb, "", row, 0);
        return sb.toString().trim();
    }

    private void appendMap(StringBuilder sb, String prefix, Map<?, ?> map, int depth) {
        if (depth > 5) return;
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String key = prefix + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> child) {
                appendMap(sb, key + ".", child, depth + 1);
            } else if (value instanceof Collection<?> collection) {
                int index = 0;
                for (Object item : collection) {
                    if (index >= 40) break;
                    if (item instanceof Map<?, ?> itemMap) appendMap(sb, key + "[" + index + "].", itemMap, depth + 1);
                    else appendScalar(sb, key + "[" + index + "]", item);
                    index++;
                }
            } else {
                appendScalar(sb, key, value);
            }
            if (++count >= 200) break;
            if (sb.length() >= properties.getSemanticContentMaxChars()) break;
        }
    }

    private void appendScalar(StringBuilder sb, String key, Object value) {
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) return;
        sb.append(key).append(": ").append(truncate(text, 12000)).append('\n');
    }

    private Map<String, Object> compactMetadata(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (LARGE_FIELDS.contains(entry.getKey())) continue;
            Object value = entry.getValue();
            if (value instanceof String text && text.length() > 3000) value = truncate(text, 3000);
            result.put(entry.getKey(), value);
            if (++count >= 80) break;
        }
        return result;
    }

    private LinkedHashSet<String> collectAliases(Map<String, Object> row) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String field : List.of("alias", "aliases", "synonyms", "display_name", "subject_name", "title")) {
            addTextValues(result, row.get(field));
        }
        Object metadata = firstNonNull(row.get("metadata_json"), row.get("metadata"), row.get("identity_json"));
        if (metadata instanceof Map<?, ?> map) {
            for (String field : List.of("alias", "aliases", "synonyms", "alternativeNames", "displayName")) {
                addTextValues(result, map.get(field));
            }
        }
        result.removeIf(v -> v.length() > 500);
        return result;
    }

    private LinkedHashSet<String> collectKeywords(String table,
                                                  String domain,
                                                  String sourceKind,
                                                  Map<String, Object> row,
                                                  Set<String> aliases) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.add(table);
        result.add(domain);
        result.add(sourceKind);
        result.addAll(aliases);
        for (String field : List.of(
                "topic", "entity_type", "entity_key", "node_type", "node_key", "artifact_type",
                "semantic_role", "rule_type", "rule_key", "fact_type", "fact_key", "field_name",
                "option_field", "option_value", "status", "source_type")) {
            addTextValues(result, row.get(field));
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String value : new ArrayList<>(result)) {
            Arrays.stream(value.split("[\\s,;/|:_()\\[\\]{}]+"))
                    .map(String::trim)
                    .filter(v -> v.length() >= 2 && v.length() <= 80)
                    .limit(30)
                    .forEach(tokens::add);
        }
        result.addAll(tokens);
        while (result.size() > 120) {
            String last = null;
            for (String value : result) last = value;
            if (last != null) result.remove(last);
        }
        return result;
    }

    private void addTextValues(Set<String> target, Object value) {
        if (value == null) return;
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) addTextValues(target, item);
            return;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text)) return;
        if (text.startsWith("[") && text.endsWith("]")) {
            try {
                List<Object> parsed = objectMapper.readValue(text, new TypeReference<List<Object>>() {});
                for (Object item : parsed) addTextValues(target, item);
                return;
            } catch (Exception ignored) {
            }
        }
        target.add(text);
    }

    private boolean active(Map<String, Object> row) {
        Object active = row.get("active");
        if (active instanceof Boolean b && !b) return false;
        if (active != null && !Boolean.parseBoolean(String.valueOf(active))) return false;
        String status = text(row.get("status")).toUpperCase(Locale.ROOT);
        return !List.of("DELETED", "SUPERSEDED", "ARCHIVED", "DISABLED", "INACTIVE", "FAILED").contains(status);
    }

    private String sourceKind(String table) {
        if (table.contains("document") || table.contains("chunk") || table.contains("artifact")) return "FILE";
        if (table.contains("price") || table.contains("pricing")) return "PRICE";
        if (table.contains("dialog")) return "DIALOG";
        if (table.contains("canonical")) return "CANONICAL";
        if (table.contains("alias")) return "ALIAS";
        if (table.contains("structured")) return "STRUCTURED";
        return "KNOWLEDGE";
    }

    private String domain(String table, Map<String, Object> row) {
        if (table.contains("price") || table.contains("pricing")) return "PRICE";
        if (table.contains("dialog")) return "DIALOG";
        if (table.contains("alias") || table.contains("entity") || table.contains("fact")) return "PRODUCT";
        if (table.contains("document") || table.contains("chunk") || table.contains("artifact")) return "FILE";
        if (table.contains("structured_table")) {
            String role = (text(row.get("semantic_role")) + " " + text(row.get("table_role"))).toUpperCase(Locale.ROOT);
            if (role.contains("PRICE")) return "PRICE";
            if (role.contains("ORDER") || role.contains("FLOW")) return "ORDER";
            return "STRUCTURED";
        }
        String ruleType = text(row.get("rule_type")).toUpperCase(Locale.ROOT);
        if (ruleType.contains("PRICE")) return "PRICE";
        if (ruleType.contains("ORDER") || ruleType.contains("REQUIRED") || ruleType.contains("VALIDATION")) return "ORDER";
        return "KNOWLEDGE";
    }

    private String firstText(Map<String, Object> row, List<String> fields) {
        for (String field : fields) {
            String value = text(row.get(field));
            if (StringUtils.hasText(value) && !"null".equalsIgnoreCase(value)) return value;
        }
        return "";
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String normalizeTable(String sourceTable) {
        String table = sourceTable == null ? "" : sourceTable.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TABLES.contains(table)) {
            throw new IllegalArgumentException("지원하지 않는 semantic source table입니다: " + sourceTable);
        }
        return table;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 생성 실패", e);
        }
    }

    private String nullIfBlank(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...[TRUNCATED]";
    }

    private record ParentScope(String parentTable, String foreignKey) {}

    public record SemanticDocument(
            UUID projectId,
            UUID versionId,
            String sourceTable,
            UUID sourceId,
            String sourceKind,
            String domainKey,
            String entityType,
            String entityKey,
            String title,
            String content,
            List<String> keywords,
            List<String> aliases,
            Map<String, Object> metadata,
            String contentHash,
            boolean active,
            Object sourceUpdatedAt) {
    }
}
