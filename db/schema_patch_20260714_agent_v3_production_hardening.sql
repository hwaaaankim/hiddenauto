-- Agent V3 production hardening / idempotent
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS rag_agent_entity_alias (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(), project_id uuid NOT NULL, version_id uuid,
    entity_type varchar(80) NOT NULL, entity_id varchar(200) NOT NULL, alias_text varchar(500) NOT NULL,
    normalized_alias varchar(500) NOT NULL, metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    active boolean NOT NULL DEFAULT true, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(project_id, entity_type, entity_id, normalized_alias)
);
CREATE INDEX IF NOT EXISTS idx_rag_agent_entity_alias_trgm ON rag_agent_entity_alias USING gin(normalized_alias gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_rag_agent_entity_alias_scope ON rag_agent_entity_alias(project_id, version_id, entity_type, active);

CREATE TABLE IF NOT EXISTS rag_agent_unresolved_reference (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(), run_id uuid, project_id uuid NOT NULL, version_id uuid, session_id uuid,
    user_expression text NOT NULL, candidate_json jsonb NOT NULL DEFAULT '[]'::jsonb, status varchar(30) NOT NULL DEFAULT 'OPEN',
    resolved_entity_type varchar(80), resolved_entity_id varchar(200), created_at timestamptz NOT NULL DEFAULT now(), resolved_at timestamptz
);
CREATE INDEX IF NOT EXISTS idx_rag_agent_unresolved_scope ON rag_agent_unresolved_reference(project_id, version_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_agent_context_snapshot (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(), run_id uuid NOT NULL, turn_no integer NOT NULL,
    input_chars integer NOT NULL DEFAULT 0, compacted boolean NOT NULL DEFAULT false, summary_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(run_id, turn_no)
);

CREATE INDEX IF NOT EXISTS idx_rag_chunk_project_version ON rag_chunk(project_id, version_id, chunk_no);
CREATE INDEX IF NOT EXISTS idx_rag_agent_run_scope_status ON rag_agent_run(project_id, version_id, status, created_at DESC);

COMMENT ON TABLE rag_agent_entity_alias IS '제품명·품목명·가격규칙·주문 프로세스의 오타/별칭/동의어 메타';
COMMENT ON TABLE rag_agent_unresolved_reference IS 'GPT가 단일 대상으로 확정하지 못한 표현과 후보';
COMMENT ON TABLE rag_agent_context_snapshot IS 'Agent 컨텍스트 압축 및 토큰 초과 진단 메타';
