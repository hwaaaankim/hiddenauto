package com.dev.HiddenBATHAuto.rag.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.rag.util.RagJsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class RagAgentObservationService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RagAgentObservationService(@Qualifier("ragJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                      ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void record(RagAgentToolContext context,
                       String callId,
                       String toolName,
                       String status,
                       Map<String, Object> result) {
        try {
            Map<String, Object> compact = compact(result);
            jdbc.update("""
                    INSERT INTO rag_agent_observation(
                        id, run_id, project_id, version_id, session_id, source_scope,
                        turn_no, response_id, call_id, tool_name, status, observation_json, created_at
                    ) VALUES (
                        :id, :runId, :projectId, :versionId, :sessionId, :sourceScope,
                        :turnNo, :responseId, :callId, :toolName, :status,
                        CAST(:observationJson AS jsonb), now()
                    )
                    """, new MapSqlParameterSource()
                    .addValue("id", UUID.randomUUID())
                    .addValue("runId", context.runId())
                    .addValue("projectId", context.projectId())
                    .addValue("versionId", context.versionId())
                    .addValue("sessionId", context.sessionId())
                    .addValue("sourceScope", context.sourceScope())
                    .addValue("turnNo", context.turnNo())
                    .addValue("responseId", context.responseId())
                    .addValue("callId", callId)
                    .addValue("toolName", toolName)
                    .addValue("status", status)
                    .addValue("observationJson", RagJsonUtils.toJson(objectMapper, compact)));

            jdbc.update("""
                    UPDATE rag_agent_run
                       SET phase = CASE WHEN :toolName = 'submit_final_answer' THEN phase ELSE 'TOOLS_RUNNING' END,
                           last_tool_name = :toolName,
                           no_progress_count = :noProgressCount,
                           updated_at = now()
                     WHERE id = :runId
                    """, new MapSqlParameterSource()
                    .addValue("runId", context.runId())
                    .addValue("toolName", toolName)
                    .addValue("noProgressCount", context.runState().noProgressCount()));
        } catch (Exception ignored) {
            // 관찰 로그 장애는 사용자 요청을 중단시키지 않습니다.
        }
    }

    private Map<String, Object> compact(Map<String, Object> input) {
        if (input == null || input.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text && text.length() > 4000) {
                value = text.substring(0, 4000) + "...[TRUNCATED]";
            }
            result.put(entry.getKey(), value);
            if (++count >= 30) break;
        }
        return result;
    }
}
