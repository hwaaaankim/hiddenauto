-- 2026-06-19 GPT semantic planner / structured override rule patch
-- 목적:
-- 1) 자연어 수정 지식(예: 코지장은 더 이상 HC 색상 불가)을 구조화 규칙으로 저장합니다.
-- 2) 조회/주문/가격계산 시 원본 엑셀 행보다 최신 override rule을 우선 적용할 수 있게 합니다.
-- 3) Java가 경우의 수를 직접 늘리지 않고 GPT plan -> 서버 검증/실행 구조로 전환합니다.

CREATE TABLE IF NOT EXISTS rag_structured_override_rule (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,

    entity_type varchar(80) NOT NULL DEFAULT 'PRODUCT',
    entity_key varchar(255) NOT NULL,

    field_name varchar(120) NOT NULL,
    rule_type varchar(60) NOT NULL,
    rule_value varchar(255) NOT NULL,

    reason text,
    source_message text,
    plan_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    confidence numeric(5,4) NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_rag_structured_override_rule UNIQUE(
        project_id, version_id, entity_type, entity_key, field_name, rule_type, rule_value
    )
);

CREATE INDEX IF NOT EXISTS idx_rag_structured_override_rule_lookup
    ON rag_structured_override_rule(project_id, version_id, entity_type, entity_key, field_name, active);

CREATE INDEX IF NOT EXISTS idx_rag_structured_override_rule_scope
    ON rag_structured_override_rule(project_id, version_id, active, created_at DESC);

ALTER TABLE rag_interaction_event
    ADD COLUMN IF NOT EXISTS planner_json jsonb NOT NULL DEFAULT '{}'::jsonb;
