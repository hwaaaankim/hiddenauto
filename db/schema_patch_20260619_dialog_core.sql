-- 2026-06-19 Dialog Core Patch
-- 목적:
-- 모든 대화형 학습/챗봇 입력이 GPT semantic plan -> Java 검증/실행 계약으로 흐르도록
-- 비규격 주문 질문흐름, 조건, 검증, 가격식, 옵션 가능 규칙을 구조화 저장합니다.

CREATE TABLE IF NOT EXISTS rag_dialog_rule (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,

    topic varchar(255),
    rule_key varchar(500) NOT NULL,
    rule_type varchar(80) NOT NULL,

    entity_type varchar(80) NOT NULL DEFAULT 'PRODUCT',
    entity_key varchar(255) NOT NULL DEFAULT '',
    step_key varchar(255) NOT NULL DEFAULT '',
    field_name varchar(120) NOT NULL DEFAULT '',

    priority integer NOT NULL DEFAULT 100,
    condition_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    action_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    validation_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    pricing_json jsonb NOT NULL DEFAULT '{}'::jsonb,

    source_message text,
    plan_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    confidence numeric(5,4) NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_rag_dialog_rule_key UNIQUE(project_id, version_id, rule_key)
);

CREATE INDEX IF NOT EXISTS idx_rag_dialog_rule_lookup
    ON rag_dialog_rule(project_id, version_id, entity_type, entity_key, rule_type, active, priority);

CREATE INDEX IF NOT EXISTS idx_rag_dialog_rule_step
    ON rag_dialog_rule(project_id, version_id, step_key, active, priority);

CREATE INDEX IF NOT EXISTS idx_rag_dialog_rule_topic
    ON rag_dialog_rule(project_id, version_id, topic, active, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_dialog_rule_condition
    ON rag_dialog_rule USING gin (condition_json);

CREATE INDEX IF NOT EXISTS idx_rag_dialog_rule_action
    ON rag_dialog_rule USING gin (action_json);
