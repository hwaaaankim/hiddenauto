-- 013. GPT SQL Agent / ChangeSet orchestration
-- 목적: Java가 사용자 의미를 업무 enum으로 분기하지 않고, GPT가 직접 rag_ DB를 읽고 변경 계획을 만든 뒤 Java가 안전 검증/트랜잭션 적용만 수행합니다.

CREATE TABLE IF NOT EXISTS rag_agent_run (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NULL,
    source_scope varchar(40) NOT NULL DEFAULT 'API',
    user_message text,
    force_save boolean NOT NULL DEFAULT false,
    status varchar(40) NOT NULL DEFAULT 'RUNNING',
    context_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    final_response_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz NULL
);

CREATE INDEX IF NOT EXISTS idx_rag_agent_run_scope
    ON rag_agent_run(project_id, version_id, source_scope, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_agent_run_session
    ON rag_agent_run(session_id, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_agent_sql_query (
    id uuid PRIMARY KEY,
    run_id uuid NULL REFERENCES rag_agent_run(id) ON DELETE SET NULL,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NULL,
    request_id varchar(120),
    query_kind varchar(20) NOT NULL DEFAULT 'READ',
    reason text,
    original_sql text NOT NULL,
    executed_sql text,
    params_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    result_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    row_count integer NOT NULL DEFAULT 0,
    status varchar(30) NOT NULL DEFAULT 'SUCCESS',
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_agent_sql_query_run
    ON rag_agent_sql_query(run_id, created_at ASC);

CREATE INDEX IF NOT EXISTS idx_rag_agent_sql_query_scope
    ON rag_agent_sql_query(project_id, version_id, query_kind, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_agent_change_set (
    id uuid PRIMARY KEY,
    run_id uuid NULL REFERENCES rag_agent_run(id) ON DELETE SET NULL,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NULL,
    source_scope varchar(40) NOT NULL DEFAULT 'API',
    title varchar(500) NOT NULL,
    summary text,
    confidence numeric(8,4) NOT NULL DEFAULT 0,
    requires_confirmation boolean NOT NULL DEFAULT true,
    status varchar(40) NOT NULL DEFAULT 'PENDING_REVIEW',
    conflict_report_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    raw_change_set_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    applied_at timestamptz NULL
);

CREATE INDEX IF NOT EXISTS idx_rag_agent_change_set_scope
    ON rag_agent_change_set(project_id, version_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_agent_change_set_run
    ON rag_agent_change_set(run_id, created_at ASC);

CREATE TABLE IF NOT EXISTS rag_agent_change_item (
    id uuid PRIMARY KEY,
    change_set_id uuid NOT NULL REFERENCES rag_agent_change_set(id) ON DELETE CASCADE,
    ordinal_no integer NOT NULL,
    operation varchar(60) NOT NULL,
    target_table varchar(160),
    target_id varchar(120),
    write_sql text,
    params_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    before_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    after_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    reason text,
    impact text,
    validation_status varchar(40) NOT NULL DEFAULT 'PENDING',
    apply_status varchar(40) NOT NULL DEFAULT 'PENDING',
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    applied_at timestamptz NULL,
    UNIQUE(change_set_id, ordinal_no)
);

CREATE INDEX IF NOT EXISTS idx_rag_agent_change_item_set
    ON rag_agent_change_item(change_set_id, ordinal_no ASC);

CREATE TABLE IF NOT EXISTS rag_agent_file_stage (
    id uuid PRIMARY KEY,
    run_id uuid NULL REFERENCES rag_agent_run(id) ON DELETE SET NULL,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    source_scope varchar(40) NOT NULL DEFAULT 'API',
    file_meta_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    preview_text text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_agent_file_stage_run
    ON rag_agent_file_stage(run_id, created_at ASC);

-- 기존 이벤트 테이블에 GPT SQL Agent 추적정보를 넣을 수 있는 확장 컬럼입니다.
ALTER TABLE rag_interaction_event
    ADD COLUMN IF NOT EXISTS agent_run_id uuid NULL,
    ADD COLUMN IF NOT EXISTS agent_mode varchar(40) NULL;

CREATE INDEX IF NOT EXISTS idx_rag_interaction_event_agent_run
    ON rag_interaction_event(agent_run_id);
