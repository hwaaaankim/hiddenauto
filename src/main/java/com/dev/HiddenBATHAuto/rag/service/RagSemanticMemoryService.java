package com.dev.HiddenBATHAuto.rag.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

/**
 * 여러 업무 테이블을 통합한 semantic memory의 인벤토리, 하이브리드 검색,
 * 재색인 상태를 제공합니다. 검색 인덱스는 후보 발견용이며 변경 전 원본 row 확인이 필요합니다.
 */
@Service
public class RagSemanticMemoryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RagAgentSchemaService schemaService;
    private final OpenAiRagClient openAiClient;
    private final RagOpenAiProperties properties;

    public RagSemanticMemoryService(
            @Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            RagAgentSchemaService schemaService,
            OpenAiRagClient openAiClient,
            RagOpenAiProperties properties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.schemaService = schemaService;
        this.openAiClient = openAiClient;
        this.properties = properties;
    }

    public Map<String, Object> inventory(RagAgentToolContext context,
                                         List<String> requestedDomains,
                                         boolean exactCounts,
                                         boolean includeSamples,
                                         int sampleLimit) {
        Set<String> domainFilter = normalizedSet(requestedDomains, 20);
        Map<String, Map<String, Object>> inventoryRows = new LinkedHashMap<>();
        for (Map<String, Object> row : schemaService.tableInventory(context.projectId(), context.versionId())) {
            String table = text(row.get("tableName"));
            if (StringUtils.hasText(table)) inventoryRows.put(table, row);
        }

        List<Map<String, Object>> tables = new ArrayList<>();
        Map<String, Long> domainTotals = new LinkedHashMap<>();
        long totalRows = 0L;
        int populated = 0;
        int failed = 0;

        List<String> supportedTables = new ArrayList<>(RagSemanticSourceDocumentFactory.SUPPORTED_TABLES);
        supportedTables.sort(Comparator.naturalOrder());
        int safeSampleLimit = Math.max(0, Math.min(sampleLimit, 10));

        for (String table : supportedTables) {
            if (!schemaService.tableExists(table)) continue;
            String domain = tableDomain(table);
            if (!domainFilter.isEmpty() && !domainFilter.contains(domain)) continue;

            Map<String, Object> one = new LinkedHashMap<>();
            one.put("tableName", table);
            one.put("domain", domain);
            one.put("sourceKind", tableSourceKind(table));
            Map<String, Object> base = inventoryRows.getOrDefault(table, Map.of());
            one.put("description", base.getOrDefault("description", ""));
            one.put("usageGuide", base.getOrDefault("usageGuide", ""));
            try {
                Map<String, Object> statistics = schemaService.tableStatistics(
                        "public", table, context.projectId(), context.versionId(), exactCounts, context.sourceScope());
                long count = longValue(statistics.get(exactCounts ? "exactScopedRows" : "estimatedRows"));
                one.putAll(statistics);
                one.put("scopedRows", count);
                if (count > 0) populated++;
                totalRows += Math.max(0L, count);
                domainTotals.merge(domain, Math.max(0L, count), Long::sum);
                if (includeSamples && safeSampleLimit > 0 && count > 0) {
                    one.put("semanticSamples", semanticSamples(
                            context.projectId(), context.versionId(), table, safeSampleLimit));
                }
            } catch (Exception e) {
                failed++;
                one.put("status", "COUNT_FAILED");
                one.put("error", safeError(e));
            }
            tables.add(one);
        }

        Map<String, Object> semanticStatus = status(context.projectId(), context.versionId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("projectId", context.projectId());
        result.put("versionId", context.versionId());
        result.put("requestedDomains", List.copyOf(domainFilter));
        result.put("supportedTableCount", tables.size());
        result.put("populatedTableCount", populated);
        result.put("countFailureCount", failed);
        result.put("totalRows", totalRows);
        result.put("domainTotals", domainTotals);
        result.put("tables", tables);
        result.put("semanticIndex", semanticStatus);
        result.put("guidance", "원본 테이블의 scopedRows가 진실의 기준이며 semantic memory는 유사 후보 검색용 인덱스입니다.");
        return result;
    }

    public Map<String, Object> search(RagAgentToolContext context,
                                      String query,
                                      List<String> requestedDomains,
                                      List<String> requestedSourceKinds,
                                      int limit,
                                      BigDecimal minimumScore,
                                      boolean includeInactive) {
        String clean = query == null ? "" : query.trim();
        if (!StringUtils.hasText(clean)) {
            throw new IllegalArgumentException("semantic 검색 query가 비어 있습니다.");
        }
        int safeLimit = Math.max(1, Math.min(limit, properties.getSemanticHardSearchLimit()));
        BigDecimal safeMinimum = clamp(minimumScore == null ? BigDecimal.ZERO : minimumScore, BigDecimal.ZERO, BigDecimal.ONE);
        Set<String> domains = normalizedSet(requestedDomains, 20);
        Set<String> sourceKinds = normalizedSet(requestedSourceKinds, 20);

        List<Double> embedding = List.of();
        boolean embeddingUsed = false;
        List<String> warnings = new ArrayList<>();
        if (properties.isSemanticQueryEmbeddingEnabled() && openAiClient.hasApiKey()) {
            try {
                embedding = openAiClient.embedding(truncate(clean, properties.getSemanticEmbeddingInputChars()));
                validateDimensions(embedding);
                embeddingUsed = true;
            } catch (Exception e) {
                warnings.add("질의 임베딩 생성에 실패하여 FTS/문자열 유사도로 검색했습니다: " + safeError(e));
                embedding = List.of();
            }
        } else if (properties.isSemanticQueryEmbeddingEnabled()) {
            warnings.add("OPENAI_API_KEY가 없어 FTS/문자열 유사도로 검색했습니다.");
        }

        StringBuilder where = new StringBuilder(" WHERE m.project_id = :projectId AND m.version_id = :versionId ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", context.projectId())
                .addValue("versionId", context.versionId())
                .addValue("query", clean)
                .addValue("queryLike", "%" + clean + "%")
                .addValue("hasEmbedding", embeddingUsed)
                .addValue("queryEmbedding", embeddingUsed ? vectorLiteral(embedding) : null)
                .addValue("minimumScore", safeMinimum)
                .addValue("limit", safeLimit);
        if (!includeInactive) where.append(" AND m.active = true AND m.embedding_status <> 'STALE' ");
        if (!domains.isEmpty()) {
            where.append(" AND upper(m.domain_key) IN (:domains) ");
            params.addValue("domains", domains);
        }
        if (!sourceKinds.isEmpty()) {
            where.append(" AND upper(m.source_kind) IN (:sourceKinds) ");
            params.addValue("sourceKinds", sourceKinds);
        }

        String sql = """
                WITH scored AS (
                    SELECT m.id, m.source_table, m.source_id, m.source_kind, m.domain_key,
                           m.entity_type, m.entity_key, m.title, m.content, m.keywords, m.aliases,
                           m.metadata_json, m.embedding_status, m.active, m.source_updated_at, m.indexed_at,
                           CASE WHEN :hasEmbedding = true AND m.embedding IS NOT NULL
                                THEN greatest(0.0, 1.0 - (m.embedding <=> CAST(:queryEmbedding AS vector)))
                                ELSE 0.0 END AS vector_score,
                           least(1.0, ts_rank_cd(m.search_vector, plainto_tsquery('simple', :query))) AS fts_score,
                           greatest(
                               similarity(coalesce(m.title, ''), :query),
                               similarity(left(coalesce(m.content, ''), 4000), :query),
                               CASE WHEN :query = ANY(m.aliases) THEN 1.0 ELSE 0.0 END,
                               CASE WHEN :query = ANY(m.keywords) THEN 1.0 ELSE 0.0 END
                           ) AS lexical_score,
                           CASE
                               WHEN lower(coalesce(m.title, '')) = lower(:query) THEN 1.0
                               WHEN lower(coalesce(m.entity_key, '')) = lower(:query) THEN 1.0
                               WHEN lower(coalesce(m.title, '')) LIKE lower(:queryLike) THEN 0.7
                               WHEN lower(coalesce(m.content, '')) LIKE lower(:queryLike) THEN 0.5
                               ELSE 0.0
                           END AS exact_score
                      FROM rag_semantic_memory m
                """ + where + """
                ), ranked AS (
                    SELECT scored.*,
                           CASE WHEN :hasEmbedding = true
                                THEN (0.55 * vector_score) + (0.20 * fts_score) + (0.15 * lexical_score) + (0.10 * exact_score)
                                ELSE (0.45 * fts_score) + (0.35 * lexical_score) + (0.20 * exact_score)
                           END AS score
                      FROM scored
                )
                SELECT id, source_table, source_id, source_kind, domain_key, entity_type, entity_key,
                       title, left(content, 5000) AS content_preview, keywords, aliases, metadata_json,
                       embedding_status, active, source_updated_at, indexed_at,
                       round(vector_score::numeric, 6) AS vector_score,
                       round(fts_score::numeric, 6) AS fts_score,
                       round(lexical_score::numeric, 6) AS lexical_score,
                       round(exact_score::numeric, 6) AS exact_score,
                       round(score::numeric, 6) AS score
                  FROM ranked
                 WHERE score >= :minimumScore
                 ORDER BY score DESC, source_updated_at DESC NULLS LAST, indexed_at DESC NULLS LAST
                 LIMIT :limit
                """;

        List<Map<String, Object>> rows = jdbc.query(sql, params, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("memoryId", rs.getObject("id"));
            row.put("sourceTable", rs.getString("source_table"));
            row.put("sourceId", rs.getObject("source_id"));
            row.put("sourceKind", rs.getString("source_kind"));
            row.put("domain", rs.getString("domain_key"));
            row.put("entityType", rs.getString("entity_type"));
            row.put("entityKey", rs.getString("entity_key"));
            row.put("title", rs.getString("title"));
            row.put("contentPreview", rs.getString("content_preview"));
            row.put("keywords", sqlArray(rs.getArray("keywords")));
            row.put("aliases", sqlArray(rs.getArray("aliases")));
            row.put("metadata", parseJsonObject(rs.getObject("metadata_json")));
            row.put("embeddingStatus", rs.getString("embedding_status"));
            row.put("active", rs.getBoolean("active"));
            row.put("sourceUpdatedAt", rs.getObject("source_updated_at"));
            row.put("indexedAt", rs.getObject("indexed_at"));
            row.put("vectorScore", rs.getBigDecimal("vector_score"));
            row.put("ftsScore", rs.getBigDecimal("fts_score"));
            row.put("lexicalScore", rs.getBigDecimal("lexical_score"));
            row.put("exactScore", rs.getBigDecimal("exact_score"));
            row.put("score", rs.getBigDecimal("score"));
            return row;
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("query", clean);
        result.put("domains", List.copyOf(domains));
        result.put("sourceKinds", List.copyOf(sourceKinds));
        result.put("embeddingUsed", embeddingUsed);
        result.put("minimumScore", safeMinimum);
        result.put("resultCount", rows.size());
        result.put("results", rows);
        result.put("warnings", warnings);
        result.put("verificationRequired", true);
        result.put("verificationGuidance", "수정·삭제·가격 확정 전 sourceTable/sourceId로 원본 row와 관계를 query_database에서 재확인하십시오.");
        return result;
    }

    public Map<String, Object> status(UUID projectId, UUID versionId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("versionId", versionId);
        try {
            Map<String, Object> memory = jdbc.queryForMap("""
                    SELECT count(*) AS total,
                           count(*) FILTER (WHERE active) AS active,
                           count(*) FILTER (WHERE embedding_status = 'READY') AS ready,
                           count(*) FILTER (WHERE embedding_status = 'PENDING') AS pending,
                           count(*) FILTER (WHERE embedding_status = 'ERROR') AS error,
                           count(*) FILTER (WHERE embedding_status = 'STALE') AS stale,
                           count(*) FILTER (WHERE embedding IS NOT NULL) AS with_embedding
                      FROM rag_semantic_memory
                     WHERE project_id = :projectId AND version_id = :versionId
                    """, params);
            result.put("memory", memory);
            result.put("semanticReadyCount", longValue(memory.get("ready")));
        } catch (Exception e) {
            result.put("memoryError", safeError(e));
            result.put("semanticReadyCount", 0L);
        }
        try {
            List<Map<String, Object>> queue = jdbc.queryForList("""
                    SELECT status, count(*) AS count
                      FROM rag_semantic_index_queue
                     WHERE project_id = :projectId AND version_id = :versionId
                     GROUP BY status
                     ORDER BY status
                    """, params);
            result.put("queue", queue);
            long pending = queue.stream()
                    .filter(row -> List.of("PENDING", "ERROR", "PROCESSING").contains(text(row.get("status")).toUpperCase(Locale.ROOT)))
                    .mapToLong(row -> longValue(row.get("count")))
                    .sum();
            result.put("queuePendingCount", pending);
        } catch (Exception e) {
            result.put("queueError", safeError(e));
            result.put("queuePendingCount", 0L);
        }
        return result;
    }

    public Map<String, Object> enqueueScope(UUID projectId, UUID versionId, List<String> sourceTables) {
        List<String> safeTables = new ArrayList<>();
        if (sourceTables != null) {
            for (String table : sourceTables) {
                String normalized = text(table).toLowerCase(Locale.ROOT);
                if (!StringUtils.hasText(normalized)) continue;
                if (!RagSemanticSourceDocumentFactory.SUPPORTED_TABLES.contains(normalized)) {
                    throw new IllegalArgumentException("지원하지 않는 semantic source table입니다: " + table);
                }
                safeTables.add(normalized);
            }
        }
        String tablesJson;
        try {
            tablesJson = objectMapper.writeValueAsString(safeTables);
        } catch (Exception e) {
            throw new IllegalStateException("sourceTables JSON 직렬화 실패", e);
        }
        Integer count = jdbc.queryForObject(
                "SELECT rag_semantic_enqueue_scope(:projectId, :versionId, CAST(:tables AS jsonb))",
                new MapSqlParameterSource()
                        .addValue("projectId", projectId)
                        .addValue("versionId", versionId)
                        .addValue("tables", tablesJson),
                Integer.class);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("projectId", projectId);
        result.put("versionId", versionId);
        result.put("sourceTables", safeTables);
        result.put("enqueued", count == null ? 0 : count);
        result.put("status", status(projectId, versionId));
        return result;
    }

    private List<Map<String, Object>> semanticSamples(UUID projectId, UUID versionId, String table, int limit) {
        try {
            return jdbc.queryForList("""
                    SELECT source_id AS "sourceId", title, entity_type AS "entityType",
                           entity_key AS "entityKey", embedding_status AS "embeddingStatus", active
                      FROM rag_semantic_memory
                     WHERE project_id = :projectId AND version_id = :versionId AND source_table = :sourceTable
                     ORDER BY active DESC, source_updated_at DESC NULLS LAST, updated_at DESC
                     LIMIT :limit
                    """, new MapSqlParameterSource()
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("sourceTable", table)
                    .addValue("limit", limit));
        } catch (Exception e) {
            return List.of(Map.of("error", safeError(e)));
        }
    }

    private Map<String, Object> parseJsonObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (value == null) return Map.of();
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", String.valueOf(value));
        }
    }

    private List<Object> sqlArray(Array value) {
        if (value == null) return List.of();
        try {
            Object array = value.getArray();
            if (array instanceof Object[] objects) return List.of(objects);
            if (array instanceof Collection<?> collection) return List.copyOf(collection);
            return List.of(String.valueOf(array));
        } catch (Exception e) {
            return List.of();
        }
    }

    private void validateDimensions(List<Double> embedding) {
        int expected = properties.getSemanticEmbeddingDimensions();
        if (embedding == null || embedding.size() != expected) {
            throw new IllegalStateException("embedding 차원 불일치: expected=" + expected
                    + ", actual=" + (embedding == null ? 0 : embedding.size()));
        }
    }

    private String vectorLiteral(List<Double> vector) {
        StringBuilder sb = new StringBuilder(vector.size() * 10 + 2).append('[');
        for (int i = 0; i < vector.size(); i++) {
            if (i > 0) sb.append(',');
            Double value = vector.get(i);
            if (value == null || value.isNaN() || value.isInfinite()) {
                throw new IllegalArgumentException("embedding에 유효하지 않은 숫자가 있습니다.");
            }
            sb.append(value);
        }
        return sb.append(']').toString();
    }

    private Set<String> normalizedSet(List<String> values, int max) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values == null) return result;
        for (String value : values) {
            String normalized = text(value).toUpperCase(Locale.ROOT);
            if (StringUtils.hasText(normalized)) result.add(normalized);
            if (result.size() >= max) break;
        }
        return result;
    }

    private String tableDomain(String table) {
        if (table.contains("price") || table.contains("pricing")) return "PRICE";
        if (table.contains("dialog")) return "DIALOG";
        if (table.contains("entity") || table.contains("alias") || table.contains("fact")) return "PRODUCT";
        if (table.contains("document") || table.contains("chunk") || table.contains("artifact")) return "FILE";
        if (table.contains("structured")) return "STRUCTURED";
        return "KNOWLEDGE";
    }

    private String tableSourceKind(String table) {
        if (table.contains("document") || table.contains("chunk") || table.contains("artifact")) return "FILE";
        if (table.contains("price") || table.contains("pricing")) return "PRICE";
        if (table.contains("dialog")) return "DIALOG";
        if (table.contains("canonical")) return "CANONICAL";
        if (table.contains("alias")) return "ALIAS";
        if (table.contains("structured")) return "STRUCTURED";
        return "KNOWLEDGE";
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) return min;
        if (value.compareTo(max) > 0) return max;
        return value.setScale(Math.min(Math.max(value.scale(), 0), 6), RoundingMode.HALF_UP);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (Exception e) { return 0L; }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String safeError(Exception error) {
        String message = error == null ? "" : error.getMessage();
        if (!StringUtils.hasText(message)) message = error == null ? "unknown" : error.getClass().getSimpleName();
        return message.length() <= 1200 ? message : message.substring(0, 1200) + "...[TRUNCATED]";
    }
}
