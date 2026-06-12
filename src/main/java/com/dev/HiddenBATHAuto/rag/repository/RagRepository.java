package com.dev.HiddenBATHAuto.rag.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class RagRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagRepository(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                         ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> findProjects() {
        String sql = """
                SELECT p.id, p.title, p.description, p.default_chat_model, p.default_embedding_model,
                       p.active_version_id, p.created_at, p.updated_at,
                       av.version_no AS active_version_no,
                       av.status AS active_version_status,
                       av.summary AS active_summary,
                       lv.id AS latest_version_id,
                       lv.version_no AS latest_version_no,
                       lv.status AS latest_version_status,
                       COALESCE(p.active_version_id, lv.id) AS open_version_id,
                       COALESCE(av.version_no, lv.version_no) AS open_version_no
                FROM rag_project p
                LEFT JOIN rag_project_version av ON av.id = p.active_version_id
                LEFT JOIN LATERAL (
                    SELECT v.*
                    FROM rag_project_version v
                    WHERE v.project_id = p.id
                    ORDER BY v.version_no DESC
                    LIMIT 1
                ) lv ON true
                ORDER BY p.created_at DESC
                """;
        return jdbc.queryForList(sql, Map.of());
    }

    public Optional<Map<String, Object>> findProject(UUID projectId) {
        String sql = """
                SELECT *
                FROM rag_project
                WHERE id = :projectId
                """;
        try {
            return Optional.of(jdbc.queryForMap(sql, Map.of("projectId", projectId)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Map<String, Object> createProject(UUID projectId,
                                             String title,
                                             String description,
                                             String chatModel,
                                             String embeddingModel) {
        String sql = """
                INSERT INTO rag_project(id, title, description, default_chat_model, default_embedding_model)
                VALUES (:id, :title, :description, :chatModel, :embeddingModel)
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", projectId)
                .addValue("title", title)
                .addValue("description", description)
                .addValue("chatModel", chatModel)
                .addValue("embeddingModel", embeddingModel));
    }

    public int findNextVersionNo(UUID projectId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version_no), 0) FROM rag_project_version WHERE project_id = :projectId",
                Map.of("projectId", projectId), Integer.class);
        return (max == null ? 0 : max) + 1;
    }

    public Map<String, Object> createVersion(UUID versionId,
                                             UUID projectId,
                                             int versionNo,
                                             String title,
                                             String learningDirection) {
        String sql = """
                INSERT INTO rag_project_version(
                    id, project_id, version_no, title, learning_direction,
                    process_json, pricing_json, constraints_json, validation_report_json
                ) VALUES (
                    :id, :projectId, :versionNo, :title, :learningDirection,
                    '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb
                )
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", versionId)
                .addValue("projectId", projectId)
                .addValue("versionNo", versionNo)
                .addValue("title", title)
                .addValue("learningDirection", learningDirection));
    }

    public List<Map<String, Object>> findVersions(UUID projectId) {
        String sql = """
                SELECT *
                FROM rag_project_version
                WHERE project_id = :projectId
                ORDER BY version_no DESC
                """;
        return jdbc.queryForList(sql, Map.of("projectId", projectId));
    }

    public Optional<Map<String, Object>> findVersion(UUID versionId) {
        String sql = """
                SELECT *
                FROM rag_project_version
                WHERE id = :versionId
                """;
        try {
            return Optional.of(jdbc.queryForMap(sql, Map.of("versionId", versionId)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> findLatestVersion(UUID projectId) {
        String sql = """
                SELECT *
                FROM rag_project_version
                WHERE project_id = :projectId
                ORDER BY version_no DESC
                LIMIT 1
                """;
        try {
            return Optional.of(jdbc.queryForMap(sql, Map.of("projectId", projectId)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Map<String, Object>> findActiveVersion(UUID projectId) {
        String sql = """
                SELECT v.*
                FROM rag_project p
                JOIN rag_project_version v ON v.id = p.active_version_id
                WHERE p.id = :projectId
                """;
        try {
            return Optional.of(jdbc.queryForMap(sql, Map.of("projectId", projectId)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void publishVersion(UUID projectId, UUID versionId) {
        jdbc.update("UPDATE rag_project_version SET status = 'ARCHIVED', updated_at = now() WHERE project_id = :projectId AND status = 'ACTIVE'",
                Map.of("projectId", projectId));
        jdbc.update("UPDATE rag_project_version SET status = 'ACTIVE', updated_at = now() WHERE id = :versionId",
                Map.of("versionId", versionId));
        jdbc.update("UPDATE rag_project SET active_version_id = :versionId, updated_at = now() WHERE id = :projectId",
                Map.of("projectId", projectId, "versionId", versionId));
    }

    public Map<String, Object> updateVersionSynthesis(UUID versionId,
                                                      String summary,
                                                      Object processJson,
                                                      Object pricingJson,
                                                      Object constraintsJson,
                                                      Object validationReportJson) {
        String sql = """
                UPDATE rag_project_version
                SET summary = :summary,
                    process_json = CAST(:processJson AS jsonb),
                    pricing_json = CAST(:pricingJson AS jsonb),
                    constraints_json = CAST(:constraintsJson AS jsonb),
                    validation_report_json = CAST(:validationReportJson AS jsonb),
                    updated_at = now()
                WHERE id = :versionId
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("versionId", versionId)
                .addValue("summary", summary)
                .addValue("processJson", toJson(processJson))
                .addValue("pricingJson", toJson(pricingJson))
                .addValue("constraintsJson", toJson(constraintsJson))
                .addValue("validationReportJson", toJson(validationReportJson)));
    }

    public Map<String, Object> insertDocument(UUID documentId,
                                              UUID projectId,
                                              UUID versionId,
                                              String topic,
                                              String sourceType,
                                              String title,
                                              String originalFilename,
                                              String rawText,
                                              Object metadata) {
        String sql = """
                INSERT INTO rag_document(id, project_id, version_id, topic, source_type, title, original_filename, raw_text, metadata)
                VALUES (:id, :projectId, :versionId, :topic, :sourceType, :title, :originalFilename, :rawText, CAST(:metadata AS jsonb))
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", documentId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", topic)
                .addValue("sourceType", sourceType)
                .addValue("title", title)
                .addValue("originalFilename", originalFilename)
                .addValue("rawText", rawText)
                .addValue("metadata", toJson(metadata)));
    }

    public void insertChunk(UUID chunkId,
                            UUID documentId,
                            UUID projectId,
                            UUID versionId,
                            int chunkNo,
                            String topic,
                            String content,
                            Object metadata,
                            String vectorLiteral) {
        String sql = """
                INSERT INTO rag_chunk(id, document_id, project_id, version_id, chunk_no, topic, content, metadata, embedding)
                VALUES (:id, :documentId, :projectId, :versionId, :chunkNo, :topic, :content, CAST(:metadata AS jsonb), CAST(:embedding AS vector))
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", chunkId)
                .addValue("documentId", documentId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("chunkNo", chunkNo)
                .addValue("topic", topic)
                .addValue("content", content)
                .addValue("metadata", toJson(metadata))
                .addValue("embedding", vectorLiteral));
    }

    public void insertChunkWithoutEmbedding(UUID chunkId,
                                            UUID documentId,
                                            UUID projectId,
                                            UUID versionId,
                                            int chunkNo,
                                            String topic,
                                            String content,
                                            Object metadata) {
        String sql = """
                INSERT INTO rag_chunk(id, document_id, project_id, version_id, chunk_no, topic, content, metadata, embedding)
                VALUES (:id, :documentId, :projectId, :versionId, :chunkNo, :topic, :content, CAST(:metadata AS jsonb), NULL)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", chunkId)
                .addValue("documentId", documentId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("chunkNo", chunkNo)
                .addValue("topic", topic)
                .addValue("content", content)
                .addValue("metadata", toJson(metadata)));
    }

    public List<Map<String, Object>> searchChunks(UUID projectId,
                                                  UUID versionId,
                                                  String vectorLiteral,
                                                  int limit) {
        String sql = """
                SELECT c.id, c.document_id, c.project_id, c.version_id, c.chunk_no, c.topic, c.content,
                       c.metadata, c.created_at,
                       (c.embedding <=> CAST(:embedding AS vector)) AS distance
                FROM rag_chunk c
                WHERE c.project_id = :projectId
                  AND c.version_id = :versionId
                  AND c.embedding IS NOT NULL
                ORDER BY c.embedding <=> CAST(:embedding AS vector)
                LIMIT :limit
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("embedding", vectorLiteral)
                .addValue("limit", limit));
    }

    public List<Map<String, Object>> findRecentDocuments(UUID projectId, UUID versionId, int limit) {
        String sql = """
                SELECT id, project_id, version_id, topic, source_type, title, original_filename, metadata, created_at,
                       left(raw_text, 1200) AS preview
                FROM rag_document
                WHERE project_id = :projectId
                  AND version_id = :versionId
                ORDER BY created_at DESC
                LIMIT :limit
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("limit", limit));
    }

    public Map<String, Object> createLearningSession(UUID sessionId, UUID projectId, UUID versionId, String title) {
        return createLearningSession(sessionId, projectId, versionId, title, title);
    }

    public Map<String, Object> createLearningSession(UUID sessionId, UUID projectId, UUID versionId, String title, String topic) {
        String sql = """
                INSERT INTO rag_learning_session(id, project_id, version_id, title, topic)
                VALUES (:id, :projectId, :versionId, :title, :topic)
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", sessionId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("title", title)
                .addValue("topic", StringUtils.hasText(topic) ? topic : title));
    }

    public Optional<Map<String, Object>> findLearningSession(UUID sessionId) {
        String sql = """
                SELECT *
                FROM rag_learning_session
                WHERE id = :sessionId
                """;
        try {
            return Optional.of(jdbc.queryForMap(sql, Map.of("sessionId", sessionId)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void insertLearningMessage(UUID messageId,
                                      UUID sessionId,
                                      UUID projectId,
                                      UUID versionId,
                                      String role,
                                      String message) {
        String sql = """
                INSERT INTO rag_learning_message(id, session_id, project_id, version_id, role, message)
                VALUES (:id, :sessionId, :projectId, :versionId, :role, :message)
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", messageId)
                .addValue("sessionId", sessionId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("role", role)
                .addValue("message", message));
    }

    public List<Map<String, Object>> findLearningMessages(UUID sessionId) {
        String sql = """
                SELECT *
                FROM rag_learning_message
                WHERE session_id = :sessionId
                ORDER BY created_at ASC
                """;
        return jdbc.queryForList(sql, Map.of("sessionId", sessionId));
    }

    public void touchLearningSession(UUID sessionId) {
        jdbc.update("UPDATE rag_learning_session SET updated_at = now() WHERE id = :sessionId",
                Map.of("sessionId", sessionId));
    }

    public void updateLearningSessionPendingResolution(UUID sessionId, Object pendingResolutionJson) {
        String sql = """
                UPDATE rag_learning_session
                SET pending_resolution_json = CAST(:pendingResolutionJson AS jsonb),
                    resolution_status = 'WAITING_USER',
                    updated_at = now()
                WHERE id = :sessionId
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("pendingResolutionJson", toJson(pendingResolutionJson)));
    }

    public void clearLearningSessionPendingResolution(UUID sessionId) {
        String sql = """
                UPDATE rag_learning_session
                SET pending_resolution_json = '{}'::jsonb,
                    resolution_status = 'NONE',
                    updated_at = now()
                WHERE id = :sessionId
                """;
        jdbc.update(sql, Map.of("sessionId", sessionId));
    }

    public Map<String, Object> insertAsset(UUID assetId,
                                           UUID projectId,
                                           UUID versionId,
                                           String ownerType,
                                           String ownerKey,
                                           String originalFilename,
                                           String storedFilename,
                                           String contentType,
                                           String filePath,
                                           String fileUrl,
                                           String note) {
        String sql = """
                INSERT INTO rag_asset(id, project_id, version_id, owner_type, owner_key, original_filename,
                                      stored_filename, content_type, file_path, file_url, note)
                VALUES (:id, :projectId, :versionId, :ownerType, :ownerKey, :originalFilename,
                        :storedFilename, :contentType, :filePath, :fileUrl, :note)
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", assetId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("ownerType", ownerType)
                .addValue("ownerKey", ownerKey)
                .addValue("originalFilename", originalFilename)
                .addValue("storedFilename", storedFilename)
                .addValue("contentType", contentType)
                .addValue("filePath", filePath)
                .addValue("fileUrl", fileUrl)
                .addValue("note", note));
    }

    public List<Map<String, Object>> findAssets(UUID projectId, UUID versionId, String ownerType, String ownerKey) {
        StringBuilder sql = new StringBuilder("""
                SELECT *
                FROM rag_asset
                WHERE project_id = :projectId
                  AND version_id = :versionId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId);
        if (StringUtils.hasText(ownerType)) {
            sql.append(" AND owner_type = :ownerType");
            params.addValue("ownerType", ownerType);
        }
        if (StringUtils.hasText(ownerKey)) {
            sql.append(" AND owner_key = :ownerKey");
            params.addValue("ownerKey", ownerKey);
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbc.queryForList(sql.toString(), params);
    }

    public Map<String, Object> createChatSession(UUID sessionId,
                                                 UUID projectId,
                                                 UUID versionId,
                                                 String userLabel,
                                                 Object stateJson,
                                                 Object auditJson) {
        String sql = """
                INSERT INTO rag_chat_session(id, project_id, version_id, user_label, state_json, audit_json)
                VALUES (:id, :projectId, :versionId, :userLabel, CAST(:stateJson AS jsonb), CAST(:auditJson AS jsonb))
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", sessionId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("userLabel", userLabel)
                .addValue("stateJson", toJson(stateJson))
                .addValue("auditJson", toJson(auditJson)));
    }

    public Optional<Map<String, Object>> findChatSession(UUID sessionId) {
        String sql = """
                SELECT *
                FROM rag_chat_session
                WHERE id = :sessionId
                """;
        try {
            return Optional.of(jdbc.queryForMap(sql, Map.of("sessionId", sessionId)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Map<String, Object> updateChatSession(UUID sessionId, Object stateJson, Object auditJson) {
        String sql = """
                UPDATE rag_chat_session
                SET state_json = CAST(:stateJson AS jsonb),
                    audit_json = CAST(:auditJson AS jsonb),
                    updated_at = now()
                WHERE id = :sessionId
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("stateJson", toJson(stateJson))
                .addValue("auditJson", toJson(auditJson)));
    }

    public void insertChatMessage(UUID messageId,
                                  UUID sessionId,
                                  String role,
                                  String content,
                                  Object stateSnapshot,
                                  Object retrievedJson) {
        String sql = """
                INSERT INTO rag_chat_message(id, session_id, role, content, state_snapshot, retrieved_json)
                VALUES (:id, :sessionId, :role, :content, CAST(:stateSnapshot AS jsonb), CAST(:retrievedJson AS jsonb))
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", messageId)
                .addValue("sessionId", sessionId)
                .addValue("role", role)
                .addValue("content", content)
                .addValue("stateSnapshot", toJson(stateSnapshot))
                .addValue("retrievedJson", toJson(retrievedJson)));
    }

    public List<Map<String, Object>> findChatMessages(UUID sessionId) {
        String sql = """
                SELECT *
                FROM rag_chat_message
                WHERE session_id = :sessionId
                ORDER BY created_at ASC
                """;
        return jdbc.queryForList(sql, Map.of("sessionId", sessionId));
    }

    public List<Map<String, Object>> findRecentChatMessages(UUID sessionId, int limit) {
        String sql = """
                SELECT *
                FROM (
                    SELECT *
                    FROM rag_chat_message
                    WHERE session_id = :sessionId
                    ORDER BY created_at DESC
                    LIMIT :limit
                ) x
                ORDER BY created_at ASC
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("limit", limit));
    }

    public Map<String, Object> insertInquiry(UUID inquiryId,
                                             UUID sessionId,
                                             UUID projectId,
                                             UUID versionId,
                                             String companyName,
                                             String customerName,
                                             String phone,
                                             String email,
                                             String memo,
                                             Object stateJson,
                                             Object auditJson,
                                             Object messagesJson) {
        String sql = """
                INSERT INTO rag_inquiry(id, session_id, project_id, version_id, company_name, customer_name,
                                        phone, email, memo, state_json, audit_json, messages_json)
                VALUES (:id, :sessionId, :projectId, :versionId, :companyName, :customerName,
                        :phone, :email, :memo, CAST(:stateJson AS jsonb), CAST(:auditJson AS jsonb), CAST(:messagesJson AS jsonb))
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", inquiryId)
                .addValue("sessionId", sessionId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("companyName", companyName)
                .addValue("customerName", customerName)
                .addValue("phone", phone)
                .addValue("email", email)
                .addValue("memo", memo)
                .addValue("stateJson", toJson(stateJson))
                .addValue("auditJson", toJson(auditJson))
                .addValue("messagesJson", toJson(messagesJson)));
    }

    public List<Map<String, Object>> findRecentLearningMessages(UUID projectId, UUID versionId, int limit) {
        String sql = """
                SELECT *
                FROM rag_learning_message
                WHERE project_id = :projectId
                  AND version_id = :versionId
                ORDER BY created_at DESC
                LIMIT :limit
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("limit", limit));
    }

    public Map<String, Object> detailProject(UUID projectId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", findProject(projectId).orElseThrow(() -> new IllegalArgumentException("프로젝트를 찾을 수 없습니다.")));
        result.put("versions", findVersions(projectId));
        return result;
    }

    public Map<String, Object> resetKnowledge(UUID projectId,
                                              UUID versionId,
                                              String topic,
                                              boolean resetWholeVersion,
                                              String reason) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", topic);

        boolean scopedTopic = StringUtils.hasText(topic) && !resetWholeVersion;
        String docWhere = scopedTopic
                ? "project_id = :projectId AND version_id = :versionId AND topic = :topic"
                : "project_id = :projectId AND version_id = :versionId";
        String sessionWhere = scopedTopic
                ? "project_id = :projectId AND version_id = :versionId AND COALESCE(topic, title) = :topic"
                : "project_id = :projectId AND version_id = :versionId";

        int deletedAssetCount;
        if (scopedTopic) {
            deletedAssetCount = jdbc.update("""
                    DELETE FROM rag_asset a
                    WHERE a.project_id = :projectId
                      AND a.version_id = :versionId
                      AND EXISTS (
                          SELECT 1
                          FROM rag_learning_session s
                          WHERE s.id::text = a.owner_key
                            AND COALESCE(s.topic, s.title) = :topic
                      )
                    """, params);
        } else {
            deletedAssetCount = jdbc.update("""
                    DELETE FROM rag_asset
                    WHERE project_id = :projectId
                      AND version_id = :versionId
                      AND owner_type IN ('LEARNING_FILE', 'LEARNING_CONVERSATION')
                    """, params);
        }

        String docPredicateForAlias = scopedTopic
                ? "d.project_id = :projectId AND d.version_id = :versionId AND d.topic = :topic"
                : "d.project_id = :projectId AND d.version_id = :versionId";

        int deletedKnowledgeNodeCount = jdbc.update("""
                DELETE FROM rag_knowledge_node n
                WHERE n.project_id = :projectId
                  AND n.version_id = :versionId
                  AND (:topic IS NULL OR n.topic = :topic)
                """, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", scopedTopic ? topic : null));

        int deletedChunkCount = jdbc.update("""
                DELETE FROM rag_chunk c
                USING rag_document d
                WHERE c.document_id = d.id
                  AND 
                """ + docPredicateForAlias, params);

        int deletedDocumentCount = jdbc.update("DELETE FROM rag_document WHERE " + docWhere, params);

        String sessionPredicateForAlias = scopedTopic
                ? "s.project_id = :projectId AND s.version_id = :versionId AND COALESCE(s.topic, s.title) = :topic"
                : "s.project_id = :projectId AND s.version_id = :versionId";

        int deletedLearningMessageCount = jdbc.update("""
                DELETE FROM rag_learning_message lm
                WHERE EXISTS (
                    SELECT 1
                    FROM rag_learning_session s
                    WHERE s.id = lm.session_id
                      AND 
                """ + sessionPredicateForAlias + """
                )
                """, params);

        int deletedLearningSessionCount = jdbc.update("DELETE FROM rag_learning_session WHERE " + sessionWhere, params);

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("reset", true);
        validation.put("topic", scopedTopic ? topic : "ALL_VERSION_TOPICS");
        validation.put("reason", reason);
        validation.put("deletedKnowledgeNodes", deletedKnowledgeNodeCount);
        validation.put("deletedChunks", deletedChunkCount);
        validation.put("deletedDocuments", deletedDocumentCount);
        validation.put("deletedLearningMessages", deletedLearningMessageCount);
        validation.put("deletedLearningSessions", deletedLearningSessionCount);
        validation.put("deletedAssets", deletedAssetCount);

        if (!scopedTopic) {
            updateVersionSynthesis(versionId, "초기화됨: 선택한 버전의 학습 지식이 비워졌습니다.", Map.of(), Map.of(), Map.of(), validation);
        }

        insertResetEvent(UUID.randomUUID(), projectId, versionId, scopedTopic ? topic : null, reason, validation);
        return validation;
    }

    public void insertResetEvent(UUID eventId,
                                 UUID projectId,
                                 UUID versionId,
                                 String topic,
                                 String reason,
                                 Object resultJson) {
        String sql = """
                INSERT INTO rag_reset_event(id, project_id, version_id, topic, reason, result_json)
                VALUES (:id, :projectId, :versionId, :topic, :reason, CAST(:resultJson AS jsonb))
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", eventId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", topic)
                .addValue("reason", reason)
                .addValue("resultJson", toJson(resultJson)));
    }


    public void deactivateKnowledgeNodes(UUID projectId,
                                         UUID versionId,
                                         String topic,
                                         String reason) {
        String sql = """
                UPDATE rag_knowledge_node
                SET active = false,
                    status = 'REPLACED',
                    metadata_json = COALESCE(metadata_json, '{}'::jsonb) || CAST(:patchJson AS jsonb),
                    updated_at = now()
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                  AND (:topic IS NULL OR topic = :topic)
                """;
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("deactivatedReason", reason);
        patch.put("deactivatedAt", java.time.OffsetDateTime.now().toString());
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", StringUtils.hasText(topic) ? topic : null)
                .addValue("patchJson", toJson(patch)));
    }

    public void insertKnowledgeNode(UUID nodeId,
                                    UUID parentId,
                                    UUID projectId,
                                    UUID versionId,
                                    UUID documentId,
                                    String topic,
                                    String nodeType,
                                    String nodeKey,
                                    String title,
                                    String summary,
                                    String rawText,
                                    Object structuredJson,
                                    Object metadataJson,
                                    boolean active,
                                    int depth,
                                    int sortOrder,
                                    String interpretationStatus,
                                    boolean retryable,
                                    int retryCount,
                                    String lastError,
                                    UUID supersedesNodeId) {
        String sql = """
                INSERT INTO rag_knowledge_node(
                    id, parent_id, project_id, version_id, document_id, topic,
                    node_type, node_key, title, summary, raw_text,
                    structured_json, metadata_json, active, status, depth, sort_order,
                    interpretation_status, retryable, retry_count, last_error, supersedes_node_id,
                    created_at, updated_at
                ) VALUES (
                    :id, :parentId, :projectId, :versionId, :documentId, :topic,
                    :nodeType, :nodeKey, :title, :summary, :rawText,
                    CAST(:structuredJson AS jsonb), CAST(:metadataJson AS jsonb), :active,
                    CASE
                        WHEN :active = false THEN 'INACTIVE'
                        WHEN :retryable = true THEN 'NEEDS_AI_RETRY'
                        ELSE 'ACTIVE'
                    END,
                    :depth, :sortOrder, :interpretationStatus, :retryable, :retryCount, :lastError, :supersedesNodeId,
                    now(), now()
                )
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", nodeId)
                .addValue("parentId", parentId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("documentId", documentId)
                .addValue("topic", topic)
                .addValue("nodeType", nodeType)
                .addValue("nodeKey", nodeKey)
                .addValue("title", title)
                .addValue("summary", summary)
                .addValue("rawText", rawText)
                .addValue("structuredJson", toJson(structuredJson))
                .addValue("metadataJson", toJson(metadataJson))
                .addValue("active", active)
                .addValue("depth", depth)
                .addValue("sortOrder", sortOrder)
                .addValue("interpretationStatus", StringUtils.hasText(interpretationStatus) ? interpretationStatus : "AI_PARSED")
                .addValue("retryable", retryable)
                .addValue("retryCount", Math.max(0, retryCount))
                .addValue("lastError", lastError)
                .addValue("supersedesNodeId", supersedesNodeId));
    }

    public List<Map<String, Object>> findActiveKnowledgeNodes(UUID projectId,
                                                               UUID versionId,
                                                               String topic,
                                                               int limit) {
        String sql = """
                SELECT *
                FROM rag_knowledge_node
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                  AND (:topic IS NULL OR topic = :topic)
                ORDER BY depth ASC, sort_order ASC, created_at ASC
                LIMIT :limit
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", StringUtils.hasText(topic) ? topic : null)
                .addValue("limit", Math.max(1, limit)));
    }

    public Optional<Map<String, Object>> findKnowledgeNode(UUID nodeId) {
        try {
            return Optional.of(jdbc.queryForMap("SELECT * FROM rag_knowledge_node WHERE id = :id", Map.of("id", nodeId)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Map<String, Object>> findRetryableKnowledgeNodes(UUID projectId,
                                                                  UUID versionId,
                                                                  String topic,
                                                                  int limit) {
        String sql = """
                SELECT *
                FROM rag_knowledge_node
                WHERE project_id = :projectId
                  AND version_id = :versionId
                  AND active = true
                  AND (:topic IS NULL OR topic = :topic)
                  AND (
                      retryable = true
                      OR interpretation_status IN ('AI_PARSE_PENDING', 'SERVER_EXTRACTED_NEEDS_AI_RETRY')
                      OR status = 'NEEDS_AI_RETRY'
                      OR node_type IN ('RAW_PRESERVED_SEGMENT', 'LEAF_DETERMINISTIC_FALLBACK')
                  )
                ORDER BY retry_count ASC, depth DESC, created_at ASC
                LIMIT :limit
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", StringUtils.hasText(topic) ? topic : null)
                .addValue("limit", Math.max(1, limit)));
    }

    public void markKnowledgeNodeRetrying(UUID nodeId) {
        jdbc.update("""
                UPDATE rag_knowledge_node
                SET status = 'AI_RETRYING',
                    interpretation_status = 'AI_RETRYING',
                    retry_count = retry_count + 1,
                    updated_at = now()
                WHERE id = :id
                """, Map.of("id", nodeId));
    }

    public void markKnowledgeNodeRetryFailed(UUID nodeId, String errorMessage) {
        jdbc.update("""
                UPDATE rag_knowledge_node
                SET status = 'NEEDS_AI_RETRY',
                    interpretation_status = 'AI_PARSE_PENDING',
                    retryable = true,
                    last_error = :errorMessage,
                    updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", nodeId)
                .addValue("errorMessage", errorMessage));
    }

    public void markKnowledgeNodeSupersededByRetry(UUID nodeId, String reason) {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("retrySupersededReason", reason);
        patch.put("retrySupersededAt", java.time.OffsetDateTime.now().toString());
        jdbc.update("""
                UPDATE rag_knowledge_node
                SET active = false,
                    status = 'SUPERSEDED_BY_AI_RETRY',
                    interpretation_status = 'SUPERSEDED_BY_AI_RETRY',
                    retryable = false,
                    metadata_json = COALESCE(metadata_json, '{}'::jsonb) || CAST(:patchJson AS jsonb),
                    updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", nodeId)
                .addValue("patchJson", toJson(patch)));
    }

    private String toJson(Object value) {
        try {
            if (value == null) return "{}";
            if (value instanceof String s) return s.isBlank() ? "{}" : s;
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 직렬화 실패: " + e.getMessage(), e);
        }
    }

    public static String toVectorLiteral(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("embedding vector가 비어 있습니다.");
        }
        List<String> values = new ArrayList<>(vector.size());
        for (Double v : vector) {
            if (v == null || v.isNaN() || v.isInfinite()) values.add("0");
            else values.add(Double.toString(v));
        }
        return "[" + String.join(",", values) + "]";
    }
}
