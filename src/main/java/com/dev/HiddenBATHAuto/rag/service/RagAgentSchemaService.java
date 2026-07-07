package com.dev.HiddenBATHAuto.rag.service;

import java.sql.Timestamp;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RagAgentSchemaService {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int MAX_SAMPLE_ROWS = 20;
    private static final int MAX_SAMPLE_TEXT = 6000;

    private final NamedParameterJdbcTemplate jdbc;

    public RagAgentSchemaService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 모델 첫 입력에 넣는 compact DB 지도입니다. 상세정보는 function tool로 탐색합니다. */
    public Map<String, Object> bootstrapContext(UUID projectId, UUID versionId) {
        return bootstrapContext(projectId, versionId, "LEARNING");
    }

    /**
     * 요청 출처에 맞춘 DB 지도입니다.
     * 소비자 CHAT에는 내부 감사/관리 테이블명과 전역 row 추정치까지 노출하지 않습니다.
     */
    public Map<String, Object> bootstrapContext(UUID projectId, UUID versionId, String sourceScope) {
        boolean chatScope = RagAgentDataAccessPolicy.isChatScope(sourceScope);
        List<Map<String, Object>> inventory = new ArrayList<>();
        for (Map<String, Object> table : tableInventory(projectId, versionId)) {
            String tableName = text(table.get("tableName"), "");
            if (chatScope && RagAgentDataAccessPolicy.isChatReadBlocked(tableName, sourceScope)) {
                continue;
            }
            Map<String, Object> visible = new LinkedHashMap<>(table);
            if (chatScope) {
                visible.remove("estimatedRows");
            }
            inventory.add(visible);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("database", databaseIdentity());
        result.put("projectId", projectId);
        result.put("versionId", versionId);
        result.put("sourceScope", sourceScope);
        result.put("tableInventory", inventory);
        result.put("scopePolicy", Map.of(
                "metadataSource", "public.rag_*",
                "queryDataSource", "rag_agent_view.rag_* security_barrier views",
                "readMetadata", chatScope
                        ? "소비자 응답에 필요한 제품/가격/발주 지식 테이블의 메타데이터만 제공"
                        : "information_schema 및 제한된 pg_catalog",
                "readScope", "Java transaction-local project/version setting이 rag_agent_view에서 자동 강제",
                "writeScope", chatScope
                        ? "소비자 CHAT에서는 변경계획이 자동 적용되지 않음"
                        : "모든 변경은 create_change_set 도구와 Java 검증/트랜잭션을 경유",
                "deletePolicy", "UPDATE/soft delete/물리 DELETE 모두 id=:targetId 단건만 허용"
        ));
        result.put("importantRule", "사용자 용어를 Java 고정 매핑으로 해석하지 말고 catalog/table/column/comment/sample을 도구로 탐색해 의미를 판단하십시오.");
        return result;
    }

    /** 관리자 진단 API에서 사용하는 전체 스냅샷입니다. */
    public Map<String, Object> snapshot(UUID projectId, UUID versionId) {
        Map<String, Object> result = new LinkedHashMap<>(bootstrapContext(projectId, versionId));
        result.put("relationships", relationships("public", null));
        List<Map<String, Object>> details = new ArrayList<>();
        for (Map<String, Object> table : tableInventory(projectId, versionId)) {
            String tableName = text(table.get("tableName"), "");
            if (!StringUtils.hasText(tableName)) continue;
            details.add(describeTable("public", tableName, projectId, versionId, 2));
        }
        result.put("tableDetails", details);
        return result;
    }

    public Map<String, Object> databaseIdentity() {
        String sql = """
                SELECT current_database() AS database_name,
                       current_schema() AS current_schema,
                       version() AS database_version,
                       current_setting('server_version_num') AS server_version_num
                """;
        Map<String, Object> row = jdbc.queryForMap(sql, Map.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("databaseType", "PostgreSQL");
        result.put("databaseName", row.get("database_name"));
        result.put("currentSchema", row.get("current_schema"));
        result.put("databaseVersion", row.get("database_version"));
        result.put("serverVersionNum", row.get("server_version_num"));
        try {
            result.put("extensions", jdbc.queryForList(
                    "SELECT extname, extversion FROM pg_extension ORDER BY extname", Map.of()));
        } catch (Exception e) {
            result.put("extensions", List.of());
        }
        return result;
    }

    public List<Map<String, Object>> tableInventory(UUID projectId, UUID versionId) {
        Map<String, Map<String, Object>> notes = schemaNotes(projectId, versionId);
        String sql = """
                SELECT t.table_schema,
                       t.table_name,
                       obj_description((quote_ident(t.table_schema) || '.' || quote_ident(t.table_name))::regclass, 'pg_class') AS table_comment,
                       COALESCE(s.n_live_tup, 0) AS estimated_rows,
                       COUNT(c.column_name) AS column_count,
                       BOOL_OR(c.column_name = 'project_id') AS has_project_id,
                       BOOL_OR(c.column_name = 'version_id') AS has_version_id,
                       BOOL_OR(c.column_name = 'active') AS has_active,
                       BOOL_OR(c.column_name = 'status') AS has_status
                FROM information_schema.tables t
                JOIN information_schema.columns c
                  ON c.table_schema = t.table_schema
                 AND c.table_name = t.table_name
                LEFT JOIN pg_stat_user_tables s
                  ON s.schemaname = t.table_schema
                 AND s.relname = t.table_name
                WHERE t.table_schema = 'public'
                  AND t.table_type = 'BASE TABLE'
                  AND t.table_name LIKE 'rag\\_%' ESCAPE '\\'
                GROUP BY t.table_schema, t.table_name, s.n_live_tup
                ORDER BY t.table_name
                """;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String tableName = text(row.get("table_name"), "");
            Map<String, Object> note = notes.getOrDefault(tableName, Map.of());
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("schemaName", row.get("table_schema"));
            one.put("tableName", tableName);
            one.put("description", firstNonBlank(
                    text(note.get("description"), ""),
                    text(row.get("table_comment"), ""),
                    builtInTableDescription(tableName)));
            one.put("usageGuide", firstNonBlank(text(note.get("usageGuide"), ""), builtInUsage(tableName)));
            one.put("whenToRead", firstNonBlank(text(note.get("whenToRead"), ""), builtInWhenToRead(tableName)));
            one.put("whenToWrite", firstNonBlank(text(note.get("whenToWrite"), ""), builtInWhenToWrite(tableName)));
            one.put("riskNote", firstNonBlank(text(note.get("riskNote"), ""), builtInRisk(tableName)));
            one.put("estimatedRows", number(row.get("estimated_rows")));
            one.put("columnCount", number(row.get("column_count")));
            one.put("hasProjectId", bool(row.get("has_project_id")));
            one.put("hasVersionId", bool(row.get("has_version_id")));
            one.put("hasActive", bool(row.get("has_active")));
            one.put("hasStatus", bool(row.get("has_status")));
            result.add(one);
        }
        return result;
    }

    public List<Map<String, Object>> searchCatalog(UUID projectId,
                                                   UUID versionId,
                                                   String query,
                                                   List<String> objectTypes,
                                                   int limit) {
        String clean = StringUtils.hasText(query) ? query.trim() : "";
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<String> types = objectTypes == null || objectTypes.isEmpty()
                ? List.of("TABLE", "COLUMN", "SCHEMA_NOTE")
                : objectTypes.stream().map(v -> v == null ? "" : v.toUpperCase(Locale.ROOT)).toList();
        List<Map<String, Object>> result = new ArrayList<>();
        String like = "%" + clean + "%";

        if (types.contains("TABLE")) {
            String sql = """
                    SELECT 'TABLE' AS object_type,
                           t.table_schema AS schema_name,
                           t.table_name AS table_name,
                           NULL::text AS object_name,
                           COALESCE(obj_description((quote_ident(t.table_schema) || '.' || quote_ident(t.table_name))::regclass, 'pg_class'), '') AS description
                    FROM information_schema.tables t
                    WHERE t.table_schema = 'public'
                      AND t.table_name LIKE 'rag\\_%' ESCAPE '\\'
                      AND (t.table_name ILIKE :q OR COALESCE(obj_description((quote_ident(t.table_schema) || '.' || quote_ident(t.table_name))::regclass, 'pg_class'), '') ILIKE :q)
                    ORDER BY t.table_name
                    LIMIT :limit
                    """;
            result.addAll(jdbc.queryForList(sql, new MapSqlParameterSource().addValue("q", like).addValue("limit", safeLimit)));
        }

        if (types.contains("COLUMN") && result.size() < safeLimit) {
            String sql = """
                    SELECT 'COLUMN' AS object_type,
                           c.table_schema AS schema_name,
                           c.table_name AS table_name,
                           c.column_name AS object_name,
                           CONCAT(c.data_type, ' / ', COALESCE(col_description((quote_ident(c.table_schema) || '.' || quote_ident(c.table_name))::regclass, c.ordinal_position), '')) AS description
                    FROM information_schema.columns c
                    WHERE c.table_schema = 'public'
                      AND c.table_name LIKE 'rag\\_%' ESCAPE '\\'
                      AND (c.table_name ILIKE :q
                           OR c.column_name ILIKE :q
                           OR c.data_type ILIKE :q
                           OR COALESCE(col_description((quote_ident(c.table_schema) || '.' || quote_ident(c.table_name))::regclass, c.ordinal_position), '') ILIKE :q)
                    ORDER BY c.table_name, c.ordinal_position
                    LIMIT :limit
                    """;
            result.addAll(jdbc.queryForList(sql, new MapSqlParameterSource()
                    .addValue("q", like)
                    .addValue("limit", safeLimit - result.size())));
        }

        if (types.contains("SCHEMA_NOTE") && result.size() < safeLimit && tableExists("rag_agent_schema_note")) {
            String sql = """
                    SELECT 'SCHEMA_NOTE' AS object_type,
                           'public' AS schema_name,
                           table_name,
                           object_name,
                           CONCAT_WS(' / ', title, description, usage_guide, when_to_read, when_to_write, risk_note) AS description
                    FROM rag_agent_schema_note
                    WHERE active = true
                      AND (project_id IS NULL OR project_id = :projectId)
                      AND (version_id IS NULL OR version_id = :versionId)
                      AND (table_name ILIKE :q
                           OR COALESCE(object_name, '') ILIKE :q
                           OR COALESCE(title, '') ILIKE :q
                           OR COALESCE(description, '') ILIKE :q
                           OR COALESCE(usage_guide, '') ILIKE :q
                           OR COALESCE(when_to_read, '') ILIKE :q)
                    ORDER BY priority, table_name, object_name NULLS FIRST
                    LIMIT :limit
                    """;
            result.addAll(jdbc.queryForList(sql, new MapSqlParameterSource()
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId)
                    .addValue("q", like)
                    .addValue("limit", safeLimit - result.size())));
        }

        if (!StringUtils.hasText(clean) && result.isEmpty()) {
            return tableInventory(projectId, versionId).stream().limit(safeLimit).toList();
        }
        return result.stream().limit(safeLimit).toList();
    }

    public Map<String, Object> describeTable(String schemaName,
                                             String tableName,
                                             UUID projectId,
                                             UUID versionId,
                                             int sampleLimit) {
        return describeTable(schemaName, tableName, projectId, versionId, sampleLimit, "LEARNING");
    }

    public Map<String, Object> describeTable(String schemaName,
                                             String tableName,
                                             UUID projectId,
                                             UUID versionId,
                                             int sampleLimit,
                                             String sourceScope) {
        String schema = validateSchema(schemaName);
        String table = validateRagTable(tableName);
        if (!tableExists(table)) {
            throw new IllegalArgumentException("존재하지 않는 RAG 테이블입니다: " + table);
        }
        if (RagAgentDataAccessPolicy.isChatReadBlocked(table, sourceScope)) {
            throw new IllegalArgumentException("소비자 CHAT에서 접근할 수 없는 내부 관리/감사 테이블입니다: " + table);
        }
        Map<String, Map<String, Object>> notes = schemaNotes(projectId, versionId);
        Map<String, Object> note = notes.getOrDefault(table, Map.of());

        String columnSql = """
                SELECT c.column_name,
                       c.ordinal_position,
                       c.data_type,
                       c.udt_name,
                       c.is_nullable,
                       c.column_default,
                       col_description((quote_ident(c.table_schema) || '.' || quote_ident(c.table_name))::regclass, c.ordinal_position) AS column_comment,
                       EXISTS (
                           SELECT 1
                           FROM information_schema.table_constraints tc
                           JOIN information_schema.key_column_usage kcu
                             ON kcu.constraint_name = tc.constraint_name
                            AND kcu.constraint_schema = tc.constraint_schema
                           WHERE tc.table_schema = c.table_schema
                             AND tc.table_name = c.table_name
                             AND tc.constraint_type = 'PRIMARY KEY'
                             AND kcu.column_name = c.column_name
                       ) AS primary_key
                FROM information_schema.columns c
                WHERE c.table_schema = :schema
                  AND c.table_name = :table
                ORDER BY c.ordinal_position
                """;
        List<Map<String, Object>> columns = jdbc.queryForList(columnSql,
                new MapSqlParameterSource().addValue("schema", schema).addValue("table", table));
        for (Map<String, Object> column : columns) {
            String columnName = text(column.get("column_name"), "");
            column.put("meaning", firstNonBlank(
                    findColumnNote(projectId, versionId, table, columnName),
                    text(column.get("column_comment"), ""),
                    builtInColumnMeaning(columnName)));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaName", schema);
        result.put("tableName", table);
        result.put("description", firstNonBlank(text(note.get("description"), ""), tableComment(schema, table), builtInTableDescription(table)));
        result.put("usageGuide", firstNonBlank(text(note.get("usageGuide"), ""), builtInUsage(table)));
        result.put("whenToRead", firstNonBlank(text(note.get("whenToRead"), ""), builtInWhenToRead(table)));
        result.put("whenToWrite", firstNonBlank(text(note.get("whenToWrite"), ""), builtInWhenToWrite(table)));
        result.put("riskNote", firstNonBlank(text(note.get("riskNote"), ""), builtInRisk(table)));
        result.put("columns", columns);
        result.put("constraints", constraints(schema, table));
        result.put("indexes", indexes(schema, table));
        result.put("relationships", relationships(schema, table));
        result.put("scope", Map.of(
                "hasProjectId", hasColumn(table, "project_id"),
                "hasVersionId", hasColumn(table, "version_id"),
                "hasActive", hasColumn(table, "active"),
                "hasStatus", hasColumn(table, "status")
        ));
        result.put("statistics", tableStatistics(schema, table, projectId, versionId, false, sourceScope));
        result.put("sampleRows", sampleRows(schema, table, projectId, versionId, sampleLimit, sourceScope));
        if (RagAgentDataAccessPolicy.isChatReadBlocked(table, sourceScope)) {
            result.put("sampleAccess", "BLOCKED_FOR_CHAT");
        } else {
            result.put("sampleAccess", "CURRENT_PROJECT_VERSION_ONLY");
        }
        return result;
    }

    public List<Map<String, Object>> relationships(String schemaName, String tableName) {
        String schema = validateSchema(schemaName);
        String table = StringUtils.hasText(tableName) ? validateRagTable(tableName) : null;
        String sql = """
                SELECT tc.table_schema AS source_schema,
                       tc.table_name AS source_table,
                       kcu.column_name AS source_column,
                       ccu.table_schema AS target_schema,
                       ccu.table_name AS target_table,
                       ccu.column_name AS target_column,
                       tc.constraint_name,
                       rc.update_rule,
                       rc.delete_rule
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_schema = kcu.constraint_schema
                 AND tc.constraint_name = kcu.constraint_name
                JOIN information_schema.referential_constraints rc
                  ON rc.constraint_schema = tc.constraint_schema
                 AND rc.constraint_name = tc.constraint_name
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_schema = rc.unique_constraint_schema
                 AND ccu.constraint_name = rc.unique_constraint_name
                WHERE tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_schema = :schema
                  AND tc.table_name LIKE 'rag\\_%' ESCAPE '\\'
                  AND (:tableName IS NULL OR tc.table_name = :tableName OR ccu.table_name = :tableName)
                ORDER BY tc.table_name, tc.constraint_name, kcu.ordinal_position
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("schema", schema)
                .addValue("tableName", table));
    }

    public Map<String, Object> tableStatistics(String schemaName,
                                               String tableName,
                                               UUID projectId,
                                               UUID versionId,
                                               boolean exactCount) {
        return tableStatistics(schemaName, tableName, projectId, versionId, exactCount, "LEARNING");
    }

    public Map<String, Object> tableStatistics(String schemaName,
                                               String tableName,
                                               UUID projectId,
                                               UUID versionId,
                                               boolean exactCount,
                                               String sourceScope) {
        String schema = validateSchema(schemaName);
        String table = validateRagTable(tableName);
        if (!tableExists(table)) {
            throw new IllegalArgumentException("존재하지 않는 RAG 테이블입니다: " + table);
        }
        if (RagAgentDataAccessPolicy.isChatReadBlocked(table, sourceScope)) {
            throw new IllegalArgumentException("소비자 CHAT에서 접근할 수 없는 내부 관리/감사 테이블입니다: " + table);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaName", schema);
        result.put("tableName", table);
        Long estimated = jdbc.queryForObject("""
                SELECT COALESCE(s.n_live_tup, c.reltuples)::bigint
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
                WHERE n.nspname = :schema AND c.relname = :table
                """, new MapSqlParameterSource().addValue("schema", schema).addValue("table", table), Long.class);
        result.put("estimatedRows", estimated == null ? 0L : estimated);

        ScopedTableQuery scoped = scopedTableQuery(schema, table, projectId, versionId, sourceScope);
        result.put("scopeMode", scoped.scopeMode());
        if (scoped.blocked()) {
            result.put("dataAccess", "BLOCKED_FOR_CHAT");
            return result;
        }
        if (exactCount) {
            Long count = jdbc.queryForObject("SELECT count(*) " + scoped.fromAndWhere(), scoped.params(), Long.class);
            result.put("exactScopedRows", count == null ? 0L : count);
        }
        if (hasColumn(table, "active")) {
            String activeSql = "SELECT t.active, count(*) AS count " + scoped.fromAndWhere()
                    + " GROUP BY t.active ORDER BY t.active DESC";
            result.put("activeCounts", jdbc.queryForList(activeSql, scoped.params()));
        }
        if (hasColumn(table, "status")) {
            String statusSql = "SELECT t.status, count(*) AS count " + scoped.fromAndWhere()
                    + " GROUP BY t.status ORDER BY count DESC, t.status NULLS LAST LIMIT 50";
            result.put("statusCounts", jdbc.queryForList(statusSql, scoped.params()));
        }
        return result;
    }

    public boolean tableExists(String tableName) {
        if (!StringUtils.hasText(tableName) || !IDENTIFIER.matcher(tableName).matches()) return false;
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = :tableName
                """, new MapSqlParameterSource("tableName", tableName), Integer.class);
        return count != null && count > 0;
    }

    public boolean hasColumn(String tableName, String columnName) {
        if (!StringUtils.hasText(tableName) || !StringUtils.hasText(columnName)) return false;
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = :tableName
                  AND column_name = :columnName
                """, new MapSqlParameterSource()
                .addValue("tableName", tableName)
                .addValue("columnName", columnName), Integer.class);
        return count != null && count > 0;
    }

    public boolean isPrimaryKeyColumn(String tableName, String columnName) {
        String table = validateRagTable(tableName);
        if (!StringUtils.hasText(columnName) || !IDENTIFIER.matcher(columnName).matches()) return false;
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_schema = tc.constraint_schema
                 AND kcu.constraint_name = tc.constraint_name
                 AND kcu.table_name = tc.table_name
                WHERE tc.table_schema = 'public'
                  AND tc.table_name = :table
                  AND tc.constraint_type = 'PRIMARY KEY'
                  AND kcu.column_name = :column
                """, new MapSqlParameterSource()
                .addValue("table", table)
                .addValue("column", columnName), Integer.class);
        return count != null && count > 0;
    }

    public List<String> columnNames(String tableName) {
        String table = validateRagTable(tableName);
        return jdbc.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = :table
                ORDER BY ordinal_position
                """, new MapSqlParameterSource("table", table), String.class);
    }

    private List<Map<String, Object>> sampleRows(String schema,
                                                 String table,
                                                 UUID projectId,
                                                 UUID versionId,
                                                 int sampleLimit,
                                                 String sourceScope) {
        int safeLimit = Math.max(0, Math.min(sampleLimit, MAX_SAMPLE_ROWS));
        if (safeLimit == 0) return List.of();
        ScopedTableQuery scoped = scopedTableQuery(schema, table, projectId, versionId, sourceScope);
        if (scoped.blocked()) return List.of();
        String sql = "SELECT t.* " + scoped.fromAndWhere() + sampleOrder(table, "t") + " LIMIT " + safeLimit;
        List<Map<String, Object>> rows = jdbc.queryForList(sql, scoped.params());
        List<Map<String, Object>> safe = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> one = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                one.put(entry.getKey(), sanitizeValue(entry.getValue()));
            }
            safe.add(one);
        }
        return safe;
    }

    private List<Map<String, Object>> constraints(String schema, String table) {
        String sql = """
                SELECT tc.constraint_name,
                       tc.constraint_type,
                       STRING_AGG(kcu.column_name, ', ' ORDER BY kcu.ordinal_position) AS columns
                FROM information_schema.table_constraints tc
                LEFT JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_schema = tc.constraint_schema
                 AND kcu.constraint_name = tc.constraint_name
                 AND kcu.table_name = tc.table_name
                WHERE tc.table_schema = :schema AND tc.table_name = :table
                GROUP BY tc.constraint_name, tc.constraint_type
                ORDER BY tc.constraint_type, tc.constraint_name
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource().addValue("schema", schema).addValue("table", table));
    }

    private List<Map<String, Object>> indexes(String schema, String table) {
        return jdbc.queryForList("""
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = :schema AND tablename = :table
                ORDER BY indexname
                """, new MapSqlParameterSource().addValue("schema", schema).addValue("table", table));
    }

    private String tableComment(String schema, String table) {
        try {
            return jdbc.queryForObject("""
                    SELECT COALESCE(obj_description((quote_ident(:schema) || '.' || quote_ident(:table))::regclass, 'pg_class'), '')
                    """, new MapSqlParameterSource().addValue("schema", schema).addValue("table", table), String.class);
        } catch (Exception e) {
            return "";
        }
    }

    private Map<String, Map<String, Object>> schemaNotes(UUID projectId, UUID versionId) {
        if (!tableExists("rag_agent_schema_note")) return Map.of();
        String sql = """
                SELECT table_name, description, usage_guide, when_to_read, when_to_write, risk_note, priority
                FROM rag_agent_schema_note
                WHERE active = true
                  AND note_kind = 'TABLE'
                  AND (project_id IS NULL OR project_id = :projectId)
                  AND (version_id IS NULL OR version_id = :versionId)
                ORDER BY table_name, priority, created_at
                """;
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId))) {
            String table = text(row.get("table_name"), "");
            if (!StringUtils.hasText(table)) continue;
            Map<String, Object> note = result.computeIfAbsent(table, key -> new LinkedHashMap<>());
            note.putIfAbsent("description", row.get("description"));
            note.putIfAbsent("usageGuide", row.get("usage_guide"));
            note.putIfAbsent("whenToRead", row.get("when_to_read"));
            note.putIfAbsent("whenToWrite", row.get("when_to_write"));
            note.putIfAbsent("riskNote", row.get("risk_note"));
        }
        return result;
    }

    private String findColumnNote(UUID projectId, UUID versionId, String table, String column) {
        if (!tableExists("rag_agent_schema_note")) return "";
        try {
            return jdbc.queryForObject("""
                    SELECT COALESCE(description, usage_guide, title, '')
                    FROM rag_agent_schema_note
                    WHERE active = true
                      AND note_kind = 'COLUMN'
                      AND table_name = :table
                      AND object_name = :column
                      AND (project_id IS NULL OR project_id = :projectId)
                      AND (version_id IS NULL OR version_id = :versionId)
                    ORDER BY priority, created_at
                    LIMIT 1
                    """, new MapSqlParameterSource()
                    .addValue("table", table)
                    .addValue("column", column)
                    .addValue("projectId", projectId)
                    .addValue("versionId", versionId), String.class);
        } catch (Exception e) {
            return "";
        }
    }

    private ScopedTableQuery scopedTableQuery(String schema,
                                              String table,
                                              UUID projectId,
                                              UUID versionId,
                                              String sourceScope) {
        MapSqlParameterSource params = scopeParams(projectId, versionId);
        if (RagAgentDataAccessPolicy.isChatReadBlocked(table, sourceScope)) {
            return new ScopedTableQuery("FROM " + qualified(schema, table) + " t WHERE 1 = 0", params,
                    true, "CHAT_BLOCKED");
        }

        List<String> conditions = new ArrayList<>();
        String from = "FROM " + qualified(schema, table) + " t";
        String mode = "DIRECT";

        if ("rag_project".equals(table)) {
            conditions.add("t.id = :projectId");
            mode = "PROJECT_ID";
        } else if ("rag_project_version".equals(table)) {
            conditions.add("t.project_id = :projectId");
            conditions.add("t.id = :versionId");
            mode = "PROJECT_VERSION_ID";
        } else if (hasColumn(table, "project_id") || hasColumn(table, "version_id")) {
            if (hasColumn(table, "project_id")) conditions.add("t.project_id = :projectId");
            if (hasColumn(table, "version_id")) conditions.add("t.version_id = :versionId");
        } else {
            RagAgentDataAccessPolicy.IndirectScopeRule rule = RagAgentDataAccessPolicy.indirectScopeRule(table)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "현재 프로젝트/버전 범위를 증명할 수 없는 테이블이라 row 조회를 차단했습니다: " + table));
            String parent = validateRagTable(rule.parentTable());
            from += " JOIN " + qualified(schema, parent) + " p ON t." + quote(rule.childForeignKey())
                    + " = p." + quote(rule.parentPrimaryKey());
            if (hasColumn(parent, "project_id")) conditions.add("p.project_id = :projectId");
            if (hasColumn(parent, "version_id")) conditions.add("p.version_id = :versionId");
            if ("rag_project".equals(parent)) conditions.add("p.id = :projectId");
            if ("rag_project_version".equals(parent)) conditions.add("p.id = :versionId");
            mode = "INDIRECT_VIA_" + parent;
        }

        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("현재 프로젝트/버전 범위 조건을 만들 수 없는 테이블입니다: " + table);
        }
        return new ScopedTableQuery(from + " WHERE " + String.join(" AND ", conditions), params, false, mode);
    }

    private MapSqlParameterSource scopeParams(UUID projectId, UUID versionId) {
        return new MapSqlParameterSource().addValue("projectId", projectId).addValue("versionId", versionId);
    }

    private String sampleOrder(String table, String alias) {
        String prefix = StringUtils.hasText(alias) ? alias + "." : "";
        if (hasColumn(table, "updated_at")) return " ORDER BY " + prefix + "updated_at DESC NULLS LAST";
        if (hasColumn(table, "created_at")) return " ORDER BY " + prefix + "created_at DESC NULLS LAST";
        if (hasColumn(table, "id")) return " ORDER BY " + prefix + "id DESC";
        return "";
    }

    private String validateSchema(String schemaName) {
        String schema = StringUtils.hasText(schemaName) ? schemaName.trim() : "public";
        if (!"public".equals(schema)) {
            throw new IllegalArgumentException("현재 Agent DB 도구는 public 스키마만 허용합니다.");
        }
        return schema;
    }

    private String validateRagTable(String tableName) {
        if (!StringUtils.hasText(tableName)) throw new IllegalArgumentException("tableName이 비어 있습니다.");
        String table = tableName.trim().replace("\"", "");
        if (table.contains(".")) table = table.substring(table.lastIndexOf('.') + 1);
        if (!IDENTIFIER.matcher(table).matches() || !table.startsWith("rag_")) {
            throw new IllegalArgumentException("public.rag_* 테이블만 허용합니다: " + tableName);
        }
        return table;
    }

    private String qualified(String schema, String table) {
        return quote(schema) + "." + quote(table);
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private Object sanitizeValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof UUID) return value;
        if (value instanceof Timestamp ts) return ts.toInstant().toString();
        if (value instanceof TemporalAccessor) return String.valueOf(value);
        String text = String.valueOf(value);
        return text.length() <= MAX_SAMPLE_TEXT ? text : text.substring(0, MAX_SAMPLE_TEXT) + "\n...[TRUNCATED]";
    }

    private String builtInTableDescription(String table) {
        if (table.contains("canonical_entity")) return "제품, 옵션, 시리즈 등 정본 엔티티 목록입니다.";
        if (table.contains("canonical_fact")) return "정본 엔티티의 색상, 사이즈, 가능조건, 제약 등 속성 fact입니다.";
        if (table.contains("pricing") || table.contains("price_matrix")) return "가격표 또는 가격 계산 규칙입니다.";
        if (table.contains("dialog")) return "발주 상담 질문 순서, 필수 입력, 조건 분기 규칙입니다.";
        if (table.contains("structured_table_row")) return "엑셀/표에서 파싱된 실제 제품/가격/조건 row JSON입니다.";
        if (table.contains("knowledge_node")) return "자연어 및 파일에서 해석한 핵심 제품/발주 지식입니다.";
        if (table.contains("document") || table.contains("chunk")) return "업로드/대화 원문과 분할 청크입니다.";
        if (table.contains("agent_")) return "GPT DB Tool Agent의 실행, SQL, 도구 호출, 변경 계획 감사 로그입니다.";
        return "HiddenBATHAuto RAG 업무 테이블입니다. 컬럼/관계/샘플을 함께 확인해 의미를 판단해야 합니다.";
    }

    private String builtInUsage(String table) {
        if (table.contains("canonical") || table.contains("structured") || table.contains("knowledge")) {
            return "사용자가 제품정보, 옵션, 색상, 사이즈, 발주조건을 요청할 때 후보로 탐색합니다.";
        }
        if (table.contains("pricing") || table.contains("price")) return "가격/견적 요청에서 조회합니다.";
        if (table.contains("dialog")) return "필요 질문, 누락값, 발주 순서를 판단할 때 조회합니다.";
        return "catalog 검색 결과와 실제 컬럼/샘플을 확인한 뒤 사용합니다.";
    }

    private String builtInWhenToRead(String table) {
        return "사용자 표현과 테이블/컬럼/설명/샘플의 의미가 연결될 때 조회합니다.";
    }

    private String builtInWhenToWrite(String table) {
        if (table.startsWith("rag_agent_")) return "서비스가 자동 기록하는 감사 테이블이므로 GPT 변경 대상으로 사용하지 않습니다.";
        return "기존 row와 관계 테이블을 조회해 대상과 영향이 확인된 뒤 create_change_set으로만 변경합니다.";
    }

    private String builtInRisk(String table) {
        if (table.contains("pricing") || table.contains("price")) return "가격 변경은 모든 견적 결과에 영향을 줄 수 있습니다.";
        if (table.contains("dialog")) return "대화 규칙 변경은 소비자 발주 흐름을 바꿀 수 있습니다.";
        if (table.contains("canonical")) return "정본 데이터는 여러 출처를 병합한 결과이므로 직접 수정 시 재빌드와 충돌을 확인해야 합니다.";
        return "변경 전 기존 데이터, 참조 관계, 중복, soft delete 가능 여부를 확인해야 합니다.";
    }

    private String builtInColumnMeaning(String column) {
        return switch (column) {
            case "id" -> "row 고유 식별자";
            case "project_id" -> "현재 RAG 프로젝트 범위";
            case "version_id" -> "현재 RAG 프로젝트 버전 범위";
            case "session_id" -> "학습/챗봇/Agent 대화 세션";
            case "active" -> "현재 사용 여부. 교체/삭제 시 false soft delete에 사용";
            case "status" -> "업무 처리 상태";
            case "row_json" -> "구조화 표의 실제 한 행 JSON";
            case "structured_json" -> "GPT가 구조화한 업무 지식 JSON";
            case "metadata_json" -> "출처/파서/Agent 부가정보 JSON";
            case "raw_text" -> "업로드 또는 학습 원문";
            case "content" -> "문서 청크 본문";
            case "entity_key" -> "제품/옵션/시리즈 등 엔티티 식별 키";
            case "entity_type" -> "PRODUCT/OPTION/SERIES 등 엔티티 종류";
            case "base_price" -> "기준 가격";
            case "base_width", "base_height", "base_depth" -> "가격 계산 기준 치수";
            case "width_step", "height_step", "depth_step" -> "치수 증가 단위";
            case "priority" -> "규칙 적용 우선순위";
            case "created_at" -> "생성 시각";
            case "updated_at" -> "수정 시각";
            default -> "테이블 설명과 실제 샘플 row를 함께 보고 의미를 판단해야 하는 컬럼";
        };
    }

    private record ScopedTableQuery(String fromAndWhere,
                                    MapSqlParameterSource params,
                                    boolean blocked,
                                    String scopeMode) {
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) if (StringUtils.hasText(value)) return value;
        return "";
    }

    private String text(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : fallback;
    }

    private long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (Exception e) { return 0L; }
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
