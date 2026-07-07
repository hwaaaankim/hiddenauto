package com.dev.HiddenBATHAuto.rag.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagAgentToolAuditService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagAgentToolAuditService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                    ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public UUID log(RagAgentToolContext context,
                    String callId,
                    String toolName,
                    Object arguments,
                    Object result,
                    String status,
                    long durationMs,
                    String errorMessage) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO rag_agent_tool_call(
                        id, run_id, project_id, version_id, session_id,
                        response_id, call_id, turn_no, tool_name,
                        arguments_json, result_json, status, duration_ms, error_message, created_at
                    ) VALUES (
                        :id, :runId, :projectId, :versionId, :sessionId,
                        :responseId, :callId, :turnNo, :toolName,
                        CAST(:argumentsJson AS jsonb), CAST(:resultJson AS jsonb),
                        :status, :durationMs, :errorMessage, now()
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", id)
                    .addValue("runId", context.runId())
                    .addValue("projectId", context.projectId())
                    .addValue("versionId", context.versionId())
                    .addValue("sessionId", context.sessionId())
                    .addValue("responseId", context.responseId())
                    .addValue("callId", callId)
                    .addValue("turnNo", context.turnNo())
                    .addValue("toolName", toolName)
                    .addValue("argumentsJson", RagJsonUtils.toJson(objectMapper, arguments == null ? java.util.Map.of() : arguments))
                    .addValue("resultJson", RagJsonUtils.toJson(objectMapper, result == null ? java.util.Map.of() : result))
                    .addValue("status", status)
                    .addValue("durationMs", durationMs)
                    .addValue("errorMessage", errorMessage));
        } catch (Exception ignored) {
            // 감사 로그 장애가 실제 사용자 요청을 막지 않도록 합니다.
        }
        return id;
    }
}
