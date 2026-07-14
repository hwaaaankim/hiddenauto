package com.dev.HiddenBATHAuto.rag.service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * GPT DB Tool Agent의 데이터 접근 범위를 한 곳에서 관리합니다.
 *
 * <p>메타데이터는 전체 RAG 테이블 구조를 보여주되 실제 row는 현재 project/version 범위만
 * 읽도록 합니다. project_id/version_id가 직접 없는 자식 테이블은 상위 테이블 조인 규칙으로
 * 범위를 증명해야 합니다.</p>
 */
public final class RagAgentDataAccessPolicy {

    private RagAgentDataAccessPolicy() {
    }

    private static final Map<String, IndirectScopeRule> INDIRECT_SCOPE_RULES = Map.ofEntries(
            Map.entry("rag_agent_change_item", new IndirectScopeRule("rag_agent_change_set", "change_set_id", "id")),
            Map.entry("rag_agent_answer_provenance", new IndirectScopeRule("rag_agent_run", "run_id", "id")),
            Map.entry("rag_canonical_job_log", new IndirectScopeRule("rag_canonical_job", "job_id", "id")),
            Map.entry("rag_chat_message", new IndirectScopeRule("rag_chat_session", "session_id", "id")),
            Map.entry("rag_learning_job_file", new IndirectScopeRule("rag_learning_job", "job_id", "id")),
            Map.entry("rag_learning_job_log", new IndirectScopeRule("rag_learning_job", "job_id", "id")),
            Map.entry("rag_price_matrix_cell", new IndirectScopeRule("rag_price_matrix", "matrix_id", "id")),
            Map.entry("rag_structured_table_row", new IndirectScopeRule("rag_structured_table", "table_id", "id"))
    );

    /** 소비자 채팅에서는 내부 관리자/감사/다른 사용자 대화 row를 자유 SQL로 보지 못하게 합니다. */
    private static final Set<String> CHAT_BLOCKED_TABLES = Set.of(
            "rag_project", "rag_project_version",
            "rag_learning_session", "rag_learning_message", "rag_learning_job", "rag_learning_job_file",
            "rag_learning_job_log", "rag_chat_session", "rag_chat_message", "rag_inquiry", "rag_reset_event",
            "rag_interaction_event", "rag_conversation_working_memory",
            "rag_agent_run", "rag_agent_sql_query", "rag_agent_change_set", "rag_agent_change_item",
            "rag_agent_file_stage", "rag_agent_tool_call", "rag_agent_schema_note",
            "rag_agent_request_plan", "rag_agent_observation",
            "rag_agent_tool_capability", "rag_agent_entity_resolution",
            "rag_agent_order_state_snapshot", "rag_agent_answer_provenance",
            "rag_agent_context_snapshot", "rag_agent_unresolved_reference", "rag_agent_entity_alias",
            "rag_semantic_memory", "rag_semantic_index_queue",
            "rag_canonical_job", "rag_canonical_job_log"
    );

    public static Optional<IndirectScopeRule> indirectScopeRule(String tableName) {
        return Optional.ofNullable(INDIRECT_SCOPE_RULES.get(normalize(tableName)));
    }

    public static boolean isChatReadBlocked(String tableName, String sourceScope) {
        return "CHAT".equalsIgnoreCase(sourceScope == null ? "" : sourceScope.trim())
                && CHAT_BLOCKED_TABLES.contains(normalize(tableName));
    }

    public static boolean isChatScope(String sourceScope) {
        return "CHAT".equalsIgnoreCase(sourceScope == null ? "" : sourceScope.trim());
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim().replace("\"", "").toLowerCase();
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    public record IndirectScopeRule(String parentTable, String childForeignKey, String parentPrimaryKey) {
    }
}
