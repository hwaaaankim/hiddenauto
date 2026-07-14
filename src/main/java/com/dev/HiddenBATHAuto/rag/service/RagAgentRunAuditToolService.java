package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 현재 요청 또는 동일 프로젝트/버전의 Agent 실행 근거를 제한적으로 조회합니다.
 * 소비자 채팅에서는 자신의 현재 세션 실행만 조회할 수 있습니다.
 */
@Service
public class RagAgentRunAuditToolService {

    private final NamedParameterJdbcTemplate jdbc;

    public RagAgentRunAuditToolService(
            @Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> inspect(RagAgentToolContext context,
                                       UUID requestedRunId,
                                       boolean includeToolArguments,
                                       boolean includeToolResults,
                                       int limit) {
        UUID runId = requestedRunId == null ? context.runId() : requestedRunId;
        int safeLimit = Math.max(1, Math.min(limit, 100));

        StringBuilder runSql = new StringBuilder("""
                SELECT id, project_id, version_id, session_id, source_scope, status, phase,
                       agent_mode, capability_version, model_name, tool_turn_count,
                       response_type, answer_source, recovered, recovery_count,
                       no_progress_count, context_compaction_count,
                       created_at, updated_at, completed_at
                FROM rag_agent_run
                WHERE id=:runId AND project_id=:projectId AND version_id=:versionId
                """);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("projectId", context.projectId())
                .addValue("versionId", context.versionId())
                .addValue("sessionId", context.sessionId())
                .addValue("limit", safeLimit);
        if (RagAgentDataAccessPolicy.isChatScope(context.sourceScope())) {
            if (context.sessionId() == null) {
                throw new IllegalArgumentException("CHAT 실행 감사 조회에는 sessionId가 필요합니다.");
            }
            runSql.append(" AND session_id=:sessionId");
        }
        List<Map<String, Object>> runs = jdbc.queryForList(runSql.toString(), p);
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("현재 프로젝트/버전/세션 범위에서 Agent 실행을 찾을 수 없습니다.");
        }

        String argumentExpression = includeToolArguments ? "arguments_json" : "'{}'::jsonb AS arguments_json";
        String resultExpression = includeToolResults ? "result_json" : "'{}'::jsonb AS result_json";
        List<Map<String, Object>> toolCalls = jdbc.queryForList("""
                SELECT id, turn_no, tool_name, status, duration_ms, response_id, call_id,
                       %s, %s, error_message, created_at
                FROM rag_agent_tool_call
                WHERE run_id=:runId
                ORDER BY turn_no, created_at
                LIMIT :limit
                """.formatted(argumentExpression, resultExpression), p);

        List<Map<String, Object>> queries = jdbc.queryForList("""
                SELECT id, query_kind, reason, row_count, status, error_message, created_at
                FROM rag_agent_sql_query
                WHERE run_id=:runId
                ORDER BY created_at
                LIMIT :limit
                """, p);

        List<Map<String, Object>> changeSets = jdbc.queryForList("""
                SELECT id, title, summary, confidence, requires_confirmation,
                       status, conflict_report_json, created_at, applied_at
                FROM rag_agent_change_set
                WHERE run_id=:runId
                ORDER BY created_at
                LIMIT :limit
                """, p);

        List<Map<String, Object>> provenance = jdbc.queryForList("""
                SELECT answer_source, model_name, openai_response_id,
                       tool_names_json, evidence_json, answer_sha256, created_at
                FROM rag_agent_answer_provenance
                WHERE run_id=:runId
                ORDER BY created_at DESC
                LIMIT 1
                """, p);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run", runs.get(0));
        result.put("toolCalls", toolCalls);
        result.put("sqlQueries", queries);
        result.put("changeSets", changeSets);
        result.put("answerProvenance", provenance.isEmpty() ? Map.of() : provenance.get(0));
        result.put("currentRun", runId.equals(context.runId()));
        result.put("guidance", "현재 실행의 최종 answer provenance는 submit_final_answer가 승인되고 응답이 저장된 뒤 확정됩니다.");
        return result;
    }
}
