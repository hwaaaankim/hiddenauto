-- 017. GPT final answer composer log

CREATE TABLE IF NOT EXISTS rag_gpt_final_answer_log (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NULL,
    source_scope varchar(60) NOT NULL DEFAULT 'API',
    user_message text NOT NULL,
    interpretation_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    verified_result_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    gpt_result_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(60) NOT NULL DEFAULT 'GPT_COMPOSED',
    final_answer text,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_gpt_final_answer_log_scope
    ON rag_gpt_final_answer_log(project_id, version_id, source_scope, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_gpt_final_answer_log_session
    ON rag_gpt_final_answer_log(session_id, created_at DESC);
