package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RagAgentSchemaService {

    private final NamedParameterJdbcTemplate jdbc;

    public RagAgentSchemaService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> snapshot(UUID projectId, UUID versionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("versionId", versionId);
        result.put("sqlScopeRule", "모든 SQL은 project_id = :projectId AND version_id = :versionId 범위를 반드시 포함해야 합니다.");
        result.put("readOnlyRule", "직접 SQL은 SELECT/WITH 조회만 허용됩니다. 저장/수정은 ChangeSet의 검증된 UPDATE/INSERT로만 적용됩니다.");
        result.put("allowedTables", tables());
        result.put("rowStats", rowStats(projectId, versionId));
        return result;
    }

    private List<Map<String, Object>> tables() {
        String sql = """
                SELECT c.table_name,
                       c.column_name,
                       c.data_type,
                       c.udt_name,
                       c.is_nullable,
                       c.ordinal_position
                FROM information_schema.columns c
                WHERE c.table_schema = 'public'
                  AND c.table_name LIKE 'rag\\_%' ESCAPE '\\'
                ORDER BY c.table_name, c.ordinal_position
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of());
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String tableName = String.valueOf(row.get("table_name"));
            Map<String, Object> table = grouped.computeIfAbsent(tableName, k -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("table", k);
                m.put("columns", new java.util.ArrayList<Map<String, Object>>());
                return m;
            });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> columns = (List<Map<String, Object>>) table.get("columns");
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("name", row.get("column_name"));
            col.put("dataType", row.get("data_type"));
            col.put("udtName", row.get("udt_name"));
            col.put("nullable", row.get("is_nullable"));
            columns.add(col);
        }
        return List.copyOf(grouped.values());
    }

    private List<Map<String, Object>> rowStats(UUID projectId, UUID versionId) {
        String sql = """
                SELECT 'rag_document' AS table_name, count(*) AS row_count FROM rag_document WHERE project_id = :projectId AND version_id = :versionId
                UNION ALL SELECT 'rag_chunk', count(*) FROM rag_chunk WHERE project_id = :projectId AND version_id = :versionId
                UNION ALL SELECT 'rag_knowledge_node', count(*) FROM rag_knowledge_node WHERE project_id = :projectId AND version_id = :versionId
                UNION ALL SELECT 'rag_structured_table', count(*) FROM rag_structured_table WHERE project_id = :projectId AND version_id = :versionId
                UNION ALL SELECT 'rag_structured_table_row', count(*) FROM rag_structured_table_row WHERE project_id = :projectId AND version_id = :versionId
                UNION ALL SELECT 'rag_structured_pricing_rule', count(*) FROM rag_structured_pricing_rule WHERE project_id = :projectId AND version_id = :versionId
                UNION ALL SELECT 'rag_dialog_rule', count(*) FROM rag_dialog_rule WHERE project_id = :projectId AND version_id = :versionId
                UNION ALL SELECT 'rag_structured_override_rule', count(*) FROM rag_structured_override_rule WHERE project_id = :projectId AND version_id = :versionId
                """;
        try {
            return jdbc.queryForList(sql, new MapSqlParameterSource()
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId));
        } catch (Exception e) {
            Map<String, Object> notice = new LinkedHashMap<>();
            notice.put("notice", "일부 테이블이 아직 생성되지 않았거나 통계 조회에 실패했습니다.");
            notice.put("error", e.getMessage());
            return List.of(notice);
        }
    }
}
