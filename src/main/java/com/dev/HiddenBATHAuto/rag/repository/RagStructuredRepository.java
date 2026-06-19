package com.dev.HiddenBATHAuto.rag.repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class RagStructuredRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagStructuredRepository(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                   ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void deactivateArtifacts(UUID projectId,
                                    UUID versionId,
                                    String topic,
                                    String semanticRole,
                                    String artifactKey,
                                    UUID replacedById,
                                    String reason) {
        StringBuilder sql = new StringBuilder("""
                UPDATE rag_knowledge_artifact
                SET active = false,
                    status = 'REPLACED',
                    replaced_by_id = :replacedById,
                    metadata_json = metadata_json || CAST(:patchJson AS jsonb),
                    updated_at = now()
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("replacedById", replacedById)
                .addValue("patchJson", toJson(Map.of(
                        "deactivatedReason", StringUtils.hasText(reason) ? reason : "사용자 요청에 따른 구조화 지식 교체",
                        "deactivatedBy", "RagStructuredLearningService"
                )));
        if (StringUtils.hasText(topic)) {
            sql.append(" AND topic = :topic");
            params.addValue("topic", topic.trim());
        }
        if (StringUtils.hasText(semanticRole)) {
            sql.append(" AND semantic_role = :semanticRole");
            params.addValue("semanticRole", semanticRole.trim());
        }
        if (StringUtils.hasText(artifactKey)) {
            sql.append(" AND artifact_key = :artifactKey");
            params.addValue("artifactKey", artifactKey.trim());
        }
        jdbc.update(sql.toString(), params);
    }

    public void resetStructuredKnowledge(UUID projectId,
                                         UUID versionId,
                                         String topic,
                                         boolean resetWholeVersion,
                                         String reason) {
        StringBuilder sql = new StringBuilder("""
                UPDATE rag_knowledge_artifact
                SET active = false,
                    status = 'RESET',
                    metadata_json = metadata_json || CAST(:patchJson AS jsonb),
                    updated_at = now()
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("patchJson", toJson(Map.of(
                        "resetReason", StringUtils.hasText(reason) ? reason : "사용자 요청에 따른 구조화 지식 초기화",
                        "resetWholeVersion", resetWholeVersion
                )));
        if (!resetWholeVersion && StringUtils.hasText(topic)) {
            sql.append(" AND topic = :topic");
            params.addValue("topic", topic.trim());
        }
        jdbc.update(sql.toString(), params);

        String topicPredicate = (!resetWholeVersion && StringUtils.hasText(topic)) ? " AND topic = :topic" : "";
        jdbc.update("""
                UPDATE rag_structured_table
                SET active = false
                WHERE project_id = :projectId
                  AND version_id = :versionId
                """ + topicPredicate, params);
        jdbc.update("""
                UPDATE rag_price_matrix
                SET active = false
                WHERE project_id = :projectId
                  AND version_id = :versionId
                """ + topicPredicate, params);
        jdbc.update("""
                UPDATE rag_structured_override_rule
                SET active = false,
                    updated_at = now()
                WHERE project_id = :projectId
                  AND version_id = :versionId
                """, params);
        jdbc.update("""
                UPDATE rag_structured_pricing_rule
                SET active = false,
                    updated_at = now()
                WHERE project_id = :projectId
                  AND version_id = :versionId
                """, params);
        jdbc.update("""
                UPDATE rag_dialog_rule
                SET active = false,
                    updated_at = now()
                WHERE project_id = :projectId
                  AND version_id = :versionId
                """, params);
    }

    public Map<String, Object> insertArtifact(UUID id,
                                              UUID projectId,
                                              UUID versionId,
                                              String topic,
                                              String artifactKey,
                                              String artifactType,
                                              String semanticRole,
                                              String title,
                                              String originalFilename,
                                              String fingerprint,
                                              boolean active,
                                              String status,
                                              Object parsedJson,
                                              Object metadataJson) {
        String sql = """
                INSERT INTO rag_knowledge_artifact(
                    id, project_id, version_id, topic, artifact_key, artifact_type, semantic_role,
                    title, original_filename, fingerprint, active, status, parsed_json, metadata_json
                ) VALUES (
                    :id, :projectId, :versionId, :topic, :artifactKey, :artifactType, :semanticRole,
                    :title, :originalFilename, :fingerprint, :active, :status,
                    CAST(:parsedJson AS jsonb), CAST(:metadataJson AS jsonb)
                )
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", topic)
                .addValue("artifactKey", artifactKey)
                .addValue("artifactType", artifactType)
                .addValue("semanticRole", semanticRole)
                .addValue("title", title)
                .addValue("originalFilename", originalFilename)
                .addValue("fingerprint", fingerprint)
                .addValue("active", active)
                .addValue("status", status)
                .addValue("parsedJson", toJson(parsedJson))
                .addValue("metadataJson", toJson(metadataJson)));
    }

    public Map<String, Object> insertStructuredTable(UUID id,
                                                     UUID artifactId,
                                                     UUID projectId,
                                                     UUID versionId,
                                                     String topic,
                                                     String tableKey,
                                                     String semanticRole,
                                                     String sheetName,
                                                     String rangeA1,
                                                     Object headerJson,
                                                     Object metadataJson,
                                                     boolean active) {
        String sql = """
                INSERT INTO rag_structured_table(
                    id, artifact_id, project_id, version_id, topic, table_key, semantic_role,
                    sheet_name, range_a1, header_json, metadata_json, active
                ) VALUES (
                    :id, :artifactId, :projectId, :versionId, :topic, :tableKey, :semanticRole,
                    :sheetName, :rangeA1, CAST(:headerJson AS jsonb), CAST(:metadataJson AS jsonb), :active
                )
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("artifactId", artifactId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", topic)
                .addValue("tableKey", tableKey)
                .addValue("semanticRole", semanticRole)
                .addValue("sheetName", sheetName)
                .addValue("rangeA1", rangeA1)
                .addValue("headerJson", toJson(headerJson))
                .addValue("metadataJson", toJson(metadataJson))
                .addValue("active", active));
    }

    public void insertStructuredTableRow(UUID id,
                                         UUID tableId,
                                         int rowNo,
                                         Object rowJson,
                                         String searchableText) {
        String sql = """
                INSERT INTO rag_structured_table_row(id, table_id, row_no, row_json, searchable_text)
                VALUES (:id, :tableId, :rowNo, CAST(:rowJson AS jsonb), :searchableText)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tableId", tableId)
                .addValue("rowNo", rowNo)
                .addValue("rowJson", toJson(rowJson))
                .addValue("searchableText", searchableText));
    }

    public Map<String, Object> insertPriceMatrix(UUID id,
                                                 UUID artifactId,
                                                 UUID projectId,
                                                 UUID versionId,
                                                 String topic,
                                                 String matrixKey,
                                                 String semanticRole,
                                                 String sheetName,
                                                 String rangeA1,
                                                 String rowAxisName,
                                                 String colAxisName,
                                                 Object roundingPolicyJson,
                                                 Object metadataJson,
                                                 boolean active) {
        String sql = """
                INSERT INTO rag_price_matrix(
                    id, artifact_id, project_id, version_id, topic, matrix_key, semantic_role,
                    sheet_name, range_a1, row_axis_name, col_axis_name,
                    rounding_policy_json, metadata_json, active
                ) VALUES (
                    :id, :artifactId, :projectId, :versionId, :topic, :matrixKey, :semanticRole,
                    :sheetName, :rangeA1, :rowAxisName, :colAxisName,
                    CAST(:roundingPolicyJson AS jsonb), CAST(:metadataJson AS jsonb), :active
                )
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("artifactId", artifactId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", topic)
                .addValue("matrixKey", matrixKey)
                .addValue("semanticRole", semanticRole)
                .addValue("sheetName", sheetName)
                .addValue("rangeA1", rangeA1)
                .addValue("rowAxisName", rowAxisName)
                .addValue("colAxisName", colAxisName)
                .addValue("roundingPolicyJson", toJson(roundingPolicyJson))
                .addValue("metadataJson", toJson(metadataJson))
                .addValue("active", active));
    }

    public void insertPriceMatrixCell(UUID id,
                                      UUID matrixId,
                                      String rowKey,
                                      String colKey,
                                      BigDecimal rowNumeric,
                                      BigDecimal colNumeric,
                                      BigDecimal numericValue,
                                      String displayValue,
                                      Object metadataJson) {
        String sql = """
                INSERT INTO rag_price_matrix_cell(
                    id, matrix_id, row_key, col_key, row_numeric, col_numeric,
                    numeric_value, display_value, metadata_json
                ) VALUES (
                    :id, :matrixId, :rowKey, :colKey, :rowNumeric, :colNumeric,
                    :numericValue, :displayValue, CAST(:metadataJson AS jsonb)
                )
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("matrixId", matrixId)
                .addValue("rowKey", rowKey)
                .addValue("colKey", colKey)
                .addValue("rowNumeric", rowNumeric)
                .addValue("colNumeric", colNumeric)
                .addValue("numericValue", numericValue)
                .addValue("displayValue", displayValue)
                .addValue("metadataJson", toJson(metadataJson)));
    }

    public List<Map<String, Object>> findActiveStructuredRows(UUID projectId, UUID versionId) {
        String sql = """
                SELECT a.id AS artifact_id,
                       a.topic,
                       a.artifact_key,
                       a.semantic_role AS artifact_role,
                       a.title AS artifact_title,
                       a.original_filename,
                       t.id AS table_id,
                       t.table_key,
                       t.semantic_role AS table_role,
                       t.sheet_name,
                       t.range_a1,
                       t.header_json,
                       t.metadata_json AS table_metadata,
                       r.row_no,
                       r.row_json,
                       r.searchable_text
                FROM rag_knowledge_artifact a
                JOIN rag_structured_table t ON t.artifact_id = a.id AND t.active = true
                JOIN rag_structured_table_row r ON r.table_id = t.id
                WHERE a.project_id = :projectId
                  AND a.version_id = :versionId
                  AND a.active = true
                ORDER BY a.created_at DESC, t.created_at DESC, r.row_no ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId));
    }

    public List<Map<String, Object>> findActiveMatrixCells(UUID projectId, UUID versionId) {
        String sql = """
                SELECT a.id AS artifact_id,
                       a.topic,
                       a.artifact_key,
                       a.semantic_role AS artifact_role,
                       a.title AS artifact_title,
                       a.original_filename,
                       m.id AS matrix_id,
                       m.matrix_key,
                       m.semantic_role AS matrix_role,
                       m.sheet_name,
                       m.range_a1,
                       m.row_axis_name,
                       m.col_axis_name,
                       m.rounding_policy_json,
                       m.metadata_json AS matrix_metadata,
                       c.row_key,
                       c.col_key,
                       c.row_numeric,
                       c.col_numeric,
                       c.numeric_value,
                       c.display_value,
                       c.metadata_json AS cell_metadata
                FROM rag_knowledge_artifact a
                JOIN rag_price_matrix m ON m.artifact_id = a.id AND m.active = true
                JOIN rag_price_matrix_cell c ON c.matrix_id = m.id
                WHERE a.project_id = :projectId
                  AND a.version_id = :versionId
                  AND a.active = true
                ORDER BY a.created_at DESC, m.created_at DESC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId));
    }

    public List<Map<String, Object>> findActiveArtifacts(UUID projectId, UUID versionId) {
        String sql = """
                SELECT *
                FROM rag_knowledge_artifact
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                ORDER BY created_at DESC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId));
    }

    private String toJson(Object value) {
        try {
            if (value == null) return "{}";
            if (value instanceof String s) return s.isBlank() ? "{}" : s;
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("jsonError", e.getMessage());
            try {
                return objectMapper.writeValueAsString(fallback);
            } catch (Exception ignored) {
                return "{}";
            }
        }
    }
}
