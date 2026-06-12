package com.dev.HiddenBATHAuto.rag.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class RagLearningJobRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagLearningJobRepository(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                    ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> createJob(UUID jobId,
                                         UUID sessionId,
                                         UUID projectId,
                                         UUID versionId,
                                         String topic,
                                         String message,
                                         boolean forceSave,
                                         int fileCount) {
        String sql = """
                INSERT INTO rag_learning_job(
                    id, session_id, project_id, version_id, topic,
                    run_status, status, progress, status_message,
                    input_message, force_save, file_count, submitted_at, updated_at
                ) VALUES (
                    :id, :sessionId, :projectId, :versionId, :topic,
                    'RUNNING', 'SUBMITTED', 0, '학습 작업이 접수되었습니다.',
                    :inputMessage, :forceSave, :fileCount, now(), now()
                )
                RETURNING *
                """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("sessionId", sessionId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("topic", topic)
                .addValue("inputMessage", message)
                .addValue("forceSave", forceSave)
                .addValue("fileCount", fileCount));
    }

    public void updateJob(UUID jobId, String status, int progress, String statusMessage) {
        String sql = """
                UPDATE rag_learning_job
                SET status = :status,
                    progress = :progress,
                    status_message = :statusMessage,
                    started_at = COALESCE(started_at, now()),
                    updated_at = now()
                WHERE id = :jobId
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("jobId", jobId)
                .addValue("status", status)
                .addValue("progress", Math.max(0, Math.min(100, progress)))
                .addValue("statusMessage", statusMessage));
        insertLog(jobId, status, progress, statusMessage, Map.of());
    }

    public void completeJob(UUID jobId, Object resultJson, String answer) {
        String sql = """
                UPDATE rag_learning_job
                SET run_status = 'COMPLETED',
                    status = 'COMPLETED',
                    progress = 100,
                    status_message = '학습 작업이 완료되었습니다.',
                    answer = :answer,
                    result_json = CAST(:resultJson AS jsonb),
                    completed_at = now(),
                    updated_at = now()
                WHERE id = :jobId
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("jobId", jobId)
                .addValue("answer", answer)
                .addValue("resultJson", toJson(resultJson)));
        insertLog(jobId, "COMPLETED", 100, "학습 작업이 완료되었습니다.", Map.of());
    }

    public void failJob(UUID jobId, String errorMessage, Object resultJson) {
        String sql = """
                UPDATE rag_learning_job
                SET run_status = 'FAILED',
                    status = 'FAILED',
                    status_message = :errorMessage,
                    error_message = :errorMessage,
                    result_json = CAST(:resultJson AS jsonb),
                    completed_at = now(),
                    updated_at = now()
                WHERE id = :jobId
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("jobId", jobId)
                .addValue("errorMessage", errorMessage)
                .addValue("resultJson", toJson(resultJson)));
        insertLog(jobId, "FAILED", 100, errorMessage, Map.of("error", errorMessage));
    }

    public Optional<Map<String, Object>> findJob(UUID jobId) {
        try {
            return Optional.of(jdbc.queryForMap("SELECT * FROM rag_learning_job WHERE id = :jobId", Map.of("jobId", jobId)));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Map<String, Object>> findJobsBySession(UUID sessionId, int limit) {
        String sql = """
                SELECT *
                FROM rag_learning_job
                WHERE session_id = :sessionId
                ORDER BY submitted_at DESC
                LIMIT :limit
                """;
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("limit", limit));
    }

    public void insertJobFile(UUID id,
                              UUID jobId,
                              UUID assetId,
                              String originalFilename,
                              String contentType,
                              String filePath,
                              long sizeBytes) {
        String sql = """
                INSERT INTO rag_learning_job_file(
                    id, job_id, asset_id, original_filename, content_type, file_path, size_bytes
                ) VALUES (
                    :id, :jobId, :assetId, :originalFilename, :contentType, :filePath, :sizeBytes
                )
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("jobId", jobId)
                .addValue("assetId", assetId)
                .addValue("originalFilename", originalFilename)
                .addValue("contentType", contentType)
                .addValue("filePath", filePath)
                .addValue("sizeBytes", sizeBytes));
    }

    public List<Map<String, Object>> findJobFiles(UUID jobId) {
        String sql = """
                SELECT *
                FROM rag_learning_job_file
                WHERE job_id = :jobId
                ORDER BY created_at ASC
                """;
        return jdbc.queryForList(sql, Map.of("jobId", jobId));
    }

    public void insertLog(UUID jobId, String status, int progress, String message, Object metadataJson) {
        String sql = """
                INSERT INTO rag_learning_job_log(id, job_id, status, progress, message, metadata_json)
                VALUES (:id, :jobId, :status, :progress, :message, CAST(:metadataJson AS jsonb))
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("jobId", jobId)
                .addValue("status", status)
                .addValue("progress", Math.max(0, Math.min(100, progress)))
                .addValue("message", message)
                .addValue("metadataJson", toJson(metadataJson)));
    }

    public List<Map<String, Object>> findJobLogs(UUID jobId) {
        String sql = """
                SELECT id, job_id, status, progress, message, metadata_json, created_at
                FROM rag_learning_job_log
                WHERE job_id = :jobId
                ORDER BY created_at ASC
                """;
        return jdbc.queryForList(sql, Map.of("jobId", jobId));
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
}
