package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class RagAgentDatabaseToolService {

    private final RagAgentSchemaService schemaService;
    private final RagAgentSqlExecutorService sqlExecutorService;

    public RagAgentDatabaseToolService(RagAgentSchemaService schemaService,
                                       RagAgentSqlExecutorService sqlExecutorService) {
        this.schemaService = schemaService;
        this.sqlExecutorService = sqlExecutorService;
    }

    public Map<String, Object> overview(RagAgentToolContext context) {
        Map<String, Object> bootstrap = schemaService.bootstrapContext(
                context.projectId(), context.versionId(), context.sourceScope());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("database", bootstrap.get("database"));
        result.put("projectId", context.projectId());
        result.put("versionId", context.versionId());
        result.put("sourceScope", context.sourceScope());
        result.put("tables", bootstrap.get("tableInventory"));
        result.put("scopePolicy", bootstrap.get("scopePolicy"));
        result.put("usage", "사용자 용어와 관련된 후보를 찾은 뒤 search_database_catalog/describe_table/query_database를 순서대로 사용하십시오.");
        return result;
    }

    public Map<String, Object> searchCatalog(RagAgentToolContext context,
                                             String query,
                                             List<String> objectTypes,
                                             int limit) {
        List<Map<String, Object>> rows = schemaService.searchCatalog(
                context.projectId(), context.versionId(), query, objectTypes, limit);
        List<Map<String, Object>> visibleRows = rows.stream()
                .filter(row -> !RagAgentDataAccessPolicy.isChatReadBlocked(
                        tableName(row), context.sourceScope()))
                .toList();
        return Map.of(
                "query", query == null ? "" : query,
                "results", visibleRows
        );
    }

    public Map<String, Object> describeTable(RagAgentToolContext context,
                                             String schemaName,
                                             String tableName,
                                             int sampleLimit) {
        return schemaService.describeTable(schemaName, tableName,
                context.projectId(), context.versionId(), sampleLimit, context.sourceScope());
    }

    public Map<String, Object> relationships(RagAgentToolContext context,
                                             String schemaName,
                                             String tableName) {
        if (tableName != null && RagAgentDataAccessPolicy.isChatReadBlocked(tableName, context.sourceScope())) {
            throw new IllegalArgumentException("소비자 CHAT에서 접근할 수 없는 내부 관리/감사 테이블입니다: " + tableName);
        }
        List<Map<String, Object>> rows = schemaService.relationships(schemaName, tableName).stream()
                .filter(row -> !RagAgentDataAccessPolicy.isChatReadBlocked(
                        String.valueOf(row.getOrDefault("source_table", "")), context.sourceScope()))
                .filter(row -> !RagAgentDataAccessPolicy.isChatReadBlocked(
                        String.valueOf(row.getOrDefault("target_table", "")), context.sourceScope()))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaName", schemaName);
        result.put("tableName", tableName);
        result.put("relationships", rows);
        return result;
    }

    public Map<String, Object> statistics(RagAgentToolContext context,
                                          String schemaName,
                                          String tableName,
                                          boolean exactCount) {
        return schemaService.tableStatistics(schemaName, tableName,
                context.projectId(), context.versionId(), exactCount, context.sourceScope());
    }

    private String tableName(Map<String, Object> row) {
        Object snakeCase = row.get("table_name");
        if (snakeCase != null) return String.valueOf(snakeCase);
        Object camelCase = row.get("tableName");
        return camelCase == null ? "" : String.valueOf(camelCase);
    }

    public Map<String, Object> query(RagAgentToolContext context,
                                     String purpose,
                                     String sql,
                                     String paramsJson,
                                     int maxRows,
                                     String requestId) {
        return sqlExecutorService.executeRead(
                context.runId(),
                context.projectId(),
                context.versionId(),
                context.sessionId(),
                context.sourceScope(),
                requestId,
                purpose,
                sql,
                paramsJson,
                maxRows
        );
    }
}
