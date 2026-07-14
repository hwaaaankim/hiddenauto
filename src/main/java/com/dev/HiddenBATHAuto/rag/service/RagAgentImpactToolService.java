package com.dev.HiddenBATHAuto.rag.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** 실제 변경 전에 FK 영향, 대상 존재 여부, 참조 건수를 점검합니다. */
@Service
public class RagAgentImpactToolService {

    private static final Pattern SAFE_TABLE = Pattern.compile("rag_[a-z0-9_]{1,120}");

    private final NamedParameterJdbcTemplate jdbc;
    private final RagAgentSchemaService schemaService;

    public RagAgentImpactToolService(
            @Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
            RagAgentSchemaService schemaService) {
        this.jdbc = jdbc;
        this.schemaService = schemaService;
    }

    public Map<String, Object> preview(RagAgentToolContext context,
                                        String targetTable,
                                        UUID targetId,
                                        String operation,
                                        int sampleLimit) {
        String table = normalizeTable(targetTable);
        if (targetId == null) throw new IllegalArgumentException("targetId가 필요합니다.");
        String normalizedOperation = operation == null ? "UPDATE" : operation.trim().toUpperCase();
        if (!Set.of("UPDATE", "SOFT_DELETE", "DELETE", "REPLACE").contains(normalizedOperation)) {
            throw new IllegalArgumentException("지원하지 않는 영향분석 operation입니다: " + operation);
        }
        if (RagAgentDataAccessPolicy.isChatReadBlocked(table, context.sourceScope())) {
            throw new IllegalArgumentException("CHAT 범위에서는 내부 감사/세션 테이블의 변경 영향을 조회할 수 없습니다: " + table);
        }
        int safeLimit = Math.max(0, Math.min(sampleLimit, 20));
        Map<String, Object> target = findScopedTarget(context, table, targetId);
        List<Map<String, Object>> relationships = schemaService.relationships("public", table);
        List<Map<String, Object>> inbound = new ArrayList<>();
        for (Map<String, Object> relation : relationships) {
            String targetName = text(relation.get("target_table"));
            if (!table.equalsIgnoreCase(targetName)) continue;
            String sourceTable = normalizeTable(text(relation.get("source_table")));
            String sourceColumn = safeIdentifier(text(relation.get("source_column")));
            if (sourceTable.isBlank() || sourceColumn.isBlank()) continue;
            Map<String, Object> item = new LinkedHashMap<>(relation);
            try {
                Set<String> sourceColumns = tableColumns(sourceTable);
                MapSqlParameterSource p = new MapSqlParameterSource()
                        .addValue("targetId", targetId)
                        .addValue("limit", safeLimit);
                StringBuilder predicate = new StringBuilder(sourceColumn).append(" = :targetId");
                if (sourceColumns.contains("project_id")) {
                    predicate.append(" AND project_id = :projectId");
                    p.addValue("projectId", context.projectId());
                }
                if (sourceColumns.contains("version_id")) {
                    predicate.append(" AND version_id = :versionId");
                    p.addValue("versionId", context.versionId());
                }
                Integer count = jdbc.queryForObject(
                        "SELECT count(*) FROM " + sourceTable + " WHERE " + predicate,
                        p, Integer.class);
                item.put("referenceCount", count == null ? 0 : count);
                item.put("scopeApplied", Map.of(
                        "project", sourceColumns.contains("project_id"),
                        "version", sourceColumns.contains("version_id")));
                if (safeLimit > 0 && count != null && count > 0) {
                    item.put("samples", jdbc.queryForList(
                            "SELECT * FROM " + sourceTable + " WHERE " + predicate + " LIMIT :limit", p));
                }
            } catch (Exception e) {
                item.put("referenceCount", null);
                item.put("inspectionError", e.getMessage());
            }
            inbound.add(item);
        }
        boolean destructive = "DELETE".equals(normalizedOperation) || "SOFT_DELETE".equals(normalizedOperation);
        long totalReferences = inbound.stream()
                .map(m -> m.get("referenceCount"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .mapToLong(Number::longValue)
                .sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetTable", table);
        result.put("targetId", targetId);
        result.put("operation", normalizedOperation);
        result.put("target", target);
        result.put("inboundRelationships", inbound);
        result.put("totalReferenceCount", totalReferences);
        result.put("requiresConfirmation", destructive || totalReferences > 0);
        result.put("recommendedOperation", destructive && hasActiveColumn(table) ? "SOFT_DELETE" : normalizedOperation);
        return result;
    }

    private Map<String, Object> findScopedTarget(RagAgentToolContext context, String table, UUID targetId) {
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema='public' AND table_name=:table
                """, Map.of("table", table));
        boolean projectScoped = columns.stream().anyMatch(r -> "project_id".equals(r.get("column_name")));
        boolean versionScoped = columns.stream().anyMatch(r -> "version_id".equals(r.get("column_name")));
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table).append(" WHERE id=:targetId");
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("targetId", targetId);
        if (projectScoped) {
            sql.append(" AND project_id=:projectId");
            p.addValue("projectId", context.projectId());
        }
        if (versionScoped) {
            sql.append(" AND version_id=:versionId");
            p.addValue("versionId", context.versionId());
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), p);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("현재 프로젝트/버전 범위에서 변경 대상을 찾을 수 없습니다.");
        }
        return rows.get(0);
    }


    private Set<String> tableColumns(String table) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema='public' AND table_name=:table
                """, Map.of("table", table));
        return rows.stream()
                .map(row -> text(row.get("column_name")))
                .filter(name -> !name.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean hasActiveColumn(String table) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name=:table AND column_name='active'
                """, Map.of("table", table), Integer.class);
        return count != null && count > 0;
    }

    private String normalizeTable(String value) {
        String table = value == null ? "" : value.trim().replace("\"", "").toLowerCase();
        int dot = table.lastIndexOf('.');
        if (dot >= 0) table = table.substring(dot + 1);
        if (!SAFE_TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("허용되지 않는 RAG 테이블명입니다: " + value);
        }
        return table;
    }

    private String safeIdentifier(String value) {
        return value != null && value.matches("[a-z_][a-z0-9_]{0,120}") ? value : "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
