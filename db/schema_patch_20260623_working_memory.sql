-- 018. Conversation working memory for GPT-style contextual dialogue

CREATE TABLE IF NOT EXISTS rag_conversation_working_memory (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NOT NULL,
    source_scope varchar(60) NOT NULL DEFAULT 'API',
    memory_key varchar(160) NOT NULL DEFAULT 'ACTIVE_CONVERSATION_CONTEXT',
    memory_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    confidence numeric(8,4) NOT NULL DEFAULT 0.8000,
    reason text,
    active boolean NOT NULL DEFAULT true,
    expires_at timestamptz NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(project_id, version_id, session_id, source_scope, memory_key)
);

CREATE INDEX IF NOT EXISTS idx_rag_conversation_working_memory_scope
    ON rag_conversation_working_memory(project_id, version_id, session_id, source_scope, active, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_conversation_working_memory_json
    ON rag_conversation_working_memory USING gin(memory_json);
