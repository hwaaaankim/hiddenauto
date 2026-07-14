package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagCanonicalJobService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RagCanonicalKnowledgeService canonicalKnowledgeService;
    private final Executor executor;

    public RagCanonicalJobService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                  ObjectMapper objectMapper,
                                  RagCanonicalKnowledgeService canonicalKnowledgeService,
                                  @Qualifier("ragLearningJobExecutor") Executor executor) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.canonicalKnowledgeService = canonicalKnowledgeService;
        this.executor = executor;
    }

    public Map<String, Object> submitRebuild(UUID projectId, UUID versionId, UUID sessionId, String instruction) {
        UUID jobId = UUID.randomUUID();
        insertJob(jobId, projectId, versionId, sessionId, "CANONICAL_REBUILD", instruction);
        log(jobId, "SUBMITTED", 0, "정본화 작업을 접수했습니다.", Map.of());
        executor.execute(() -> runRebuild(jobId, projectId, versionId, sessionId, instruction));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("jobId", jobId);
        result.put("runStatus", "RUNNING");
        result.put("status", "SUBMITTED");
        result.put("responseType", "CANONICAL_JOB_SUBMITTED");
        result.put("answerSource", "NONE");
        result.put("statusMessage", "정본화 작업이 접수되었습니다.");
        return result;
    }

    public Map<String, Object> findJob(UUID jobId) {
        Map<String, Object> job = jdbc.queryForMap("SELECT * FROM rag_canonical_job WHERE id = :id", Map.of("id", jobId));
        List<Map<String, Object>> logs = jdbc.queryForList("""
                SELECT *
                FROM rag_canonical_job_log
                WHERE job_id = :jobId
                ORDER BY created_at ASC
                """, Map.of("jobId", jobId));
        Map<String, Object> result = new LinkedHashMap<>(job);
        result.put("logs", logs);
        return result;
    }

    public List<Map<String, Object>> findJobs(UUID projectId, UUID versionId, int limit) {
        return jdbc.queryForList("""
                SELECT *
                FROM rag_canonical_job
                WHERE project_id = :projectId
                  AND version_id = :versionId
                ORDER BY submitted_at DESC
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("limit", Math.max(1, Math.min(limit, 100))));
    }

    private void runRebuild(UUID jobId, UUID projectId, UUID versionId, UUID sessionId, String instruction) {
        try {
            updateJob(jobId, "RUNNING", "PREPARING", 5, "정본화 준비 중", null, null);
            log(jobId, "PREPARING", 5, "활성 구조화 데이터 기준으로 정본 snapshot을 준비합니다.", Map.of());
            updateJob(jobId, "RUNNING", "CANONICALIZING", 40, "정본 데이터 생성 중", null, null);
            log(jobId, "CANONICALIZING", 40, "가격요소를 고정 enum이 아닌 factor_json으로 분해합니다.", Map.of());
            Map<String, Object> result = canonicalKnowledgeService.rebuild(projectId, versionId, null, sessionId, instruction, true);
            updateJob(jobId, "COMPLETED", "COMPLETED", 100, "정본화 작업이 완료되었습니다.", result, null);
            log(jobId, "COMPLETED", 100, "정본화 작업이 완료되었습니다.", result);
        } catch (Exception e) {
            updateJob(jobId, "FAILED", "FAILED", 100, "정본화 작업이 실패했습니다.", null, e.getMessage());
            log(jobId, "FAILED", 100, e.getMessage(), Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private void insertJob(UUID jobId, UUID projectId, UUID versionId, UUID sessionId, String jobType, String instruction) {
        jdbc.update("""
                INSERT INTO rag_canonical_job(
                    id, project_id, version_id, session_id, job_type, instruction, run_status, status, progress, status_message, submitted_at, updated_at
                ) VALUES (
                    :id, :projectId, :versionId, :sessionId, :jobType, :instruction, 'RUNNING', 'SUBMITTED', 0, '정본화 작업 접수', now(), now()
                )
                """, new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("sessionId", sessionId)
                .addValue("jobType", jobType)
                .addValue("instruction", instruction));
    }

    private void updateJob(UUID jobId, String runStatus, String status, int progress, String message, Object result, String error) {
        jdbc.update("""
                UPDATE rag_canonical_job
                SET run_status = :runStatus,
                    status = :status,
                    progress = :progress,
                    status_message = :message,
                    result_json = COALESCE(CAST(:resultJson AS jsonb), result_json),
                    error_message = :errorMessage,
                    started_at = COALESCE(started_at, now()),
                    completed_at = CASE WHEN :runStatus IN ('COMPLETED','FAILED','CANCELED') THEN now() ELSE completed_at END,
                    updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource()
                .addValue("id", jobId)
                .addValue("runStatus", runStatus)
                .addValue("status", status)
                .addValue("progress", progress)
                .addValue("message", message)
                .addValue("resultJson", result == null ? null : RagJsonUtils.toJson(objectMapper, result))
                .addValue("errorMessage", error));
    }

    private void log(UUID jobId, String status, int progress, String message, Object metadata) {
        jdbc.update("""
                INSERT INTO rag_canonical_job_log(id, job_id, status, progress, message, metadata_json, created_at)
                VALUES (:id, :jobId, :status, :progress, :message, CAST(:metadataJson AS jsonb), now())
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("jobId", jobId)
                .addValue("status", status)
                .addValue("progress", progress)
                .addValue("message", StringUtils.hasText(message) ? message : "")
                .addValue("metadataJson", RagJsonUtils.toJson(objectMapper, metadata == null ? Map.of() : metadata)));
    }
}
