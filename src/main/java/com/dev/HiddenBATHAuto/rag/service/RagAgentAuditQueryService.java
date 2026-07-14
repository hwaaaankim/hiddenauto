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
public class RagAgentAuditQueryService {

    private final NamedParameterJdbcTemplate jdbc;

    public RagAgentAuditQueryService(
            @Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> runDetail(UUID runId) {
        MapSqlParameterSource params = new MapSqlParameterSource("runId", runId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run", one("""
                SELECT id, project_id, version_id, session_id, source_scope,
                       user_message, force_save, status, phase, agent_mode, model_name,
                       last_response_id, tool_turn_count, recovery_count, recovered,
                       last_tool_name, no_progress_count, error_code,
                       usage_json::text AS usage_json,
                       plan_json::text AS plan_json,
                       context_json::text AS context_json,
                       evidence_json::text AS evidence_json,
                       final_response_json::text AS final_response_json,
                       user_answer, error_detail_json::text AS error_detail_json, error_message,
                       created_at, updated_at, completed_at
                FROM rag_agent_run
                WHERE id = :runId
                """, params));
        result.put("requestPlan", list("""
                SELECT id, intent_type, user_goal, requires_database, requires_semantic_search,
                       requires_mutation, requires_deterministic_pricing, ambiguity_detected,
                       clarification_question, target_domains::text AS target_domains,
                       entity_hints_json::text AS entity_hints_json,
                       planned_steps_json::text AS planned_steps_json, risk_level,
                       plan_json::text AS plan_json, created_at, updated_at
                FROM rag_agent_request_plan
                WHERE run_id = :runId
                """, params));
        result.put("observations", list("""
                SELECT id, turn_no, response_id, call_id, tool_name, status,
                       observation_json::text AS observation_json, created_at
                FROM rag_agent_observation
                WHERE run_id = :runId
                ORDER BY turn_no ASC, created_at ASC
                """, params));
        result.put("toolCalls", list("""
                SELECT id, response_id, call_id, turn_no, tool_name,
                       arguments_json::text AS arguments_json,
                       result_json::text AS result_json,
                       status, duration_ms,
                       error_message, created_at
                FROM rag_agent_tool_call
                WHERE run_id = :runId
                ORDER BY turn_no ASC, created_at ASC
                """, params));
        result.put("sqlQueries", list("""
                SELECT id, request_id, query_kind, reason, original_sql,
                       executed_sql,
                       params_json::text AS params_json,
                       result_json::text AS result_json,
                       row_count,
                       status, error_message, created_at
                FROM rag_agent_sql_query
                WHERE run_id = :runId
                ORDER BY created_at ASC
                """, params));
        result.put("changeSets", list("""
                SELECT id, title, summary, confidence, requires_confirmation,
                       status,
                       conflict_report_json::text AS conflict_report_json,
                       raw_change_set_json::text AS raw_change_set_json,
                       error_message, created_at, updated_at, applied_at
                FROM rag_agent_change_set
                WHERE run_id = :runId
                ORDER BY created_at ASC
                """, params));
        result.put("fileStages", list("""
                SELECT id, source_scope,
                       file_meta_json::text AS file_meta_json,
                       preview_text, created_at
                FROM rag_agent_file_stage
                WHERE run_id = :runId
                ORDER BY created_at ASC
                """, params));
        return result;
    }

    public List<Map<String, Object>> recentRuns(UUID projectId,
                                                 UUID versionId,
                                                 String status,
                                                 int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        StringBuilder sql = new StringBuilder("""
                SELECT id, session_id, source_scope, user_message, force_save,
                       status, phase, agent_mode, model_name, last_response_id,
                       tool_turn_count, recovered, recovery_count, last_tool_name,
                       no_progress_count, error_code, error_message, created_at, completed_at
                FROM rag_agent_run
                WHERE project_id = :projectId
                  AND version_id = :versionId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("projectId", projectId)
                .addValue("versionId", versionId)
                .addValue("limit", safeLimit);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = :status ");
            params.addValue("status", status.trim());
        }
        sql.append(" ORDER BY created_at DESC LIMIT :limit ");
        return list(sql.toString(), params);
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = list(sql, params);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Agent 실행 이력을 찾을 수 없습니다.");
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> list(String sql, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return rows.stream()
                .map(row -> (Map<String, Object>) new LinkedHashMap<>(row))
                .toList();
    }
}
