-- 2026-06-19 Semantic Orchestrator Patch
-- 목적:
-- Java 1차 후보/조회 결과와 GPT 의미판정을 서로 재검증하여 사용자의 진짜 의도를 확정합니다.
-- 예: "제품모든정보 보여줘"를 특정 제품명 조회가 아니라 ALL_PRODUCTS 전체조회로 보정합니다.

CREATE TABLE IF NOT EXISTS rag_semantic_resolution_event (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid,
    source_scope varchar(40) NOT NULL,
    user_message text NOT NULL,

    first_plan_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    first_retrieval_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    repaired boolean NOT NULL DEFAULT false,
    repair_reason text,
    repaired_plan_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    final_plan_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    final_retrieval_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,

    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_semantic_resolution_event_scope
    ON rag_semantic_resolution_event(project_id, version_id, source_scope, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_semantic_resolution_event_session
    ON rag_semantic_resolution_event(session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_semantic_resolution_event_final_plan
    ON rag_semantic_resolution_event USING gin (final_plan_json);
