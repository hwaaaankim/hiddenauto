-- 2026-06-19 Structured product pricing rule patch
-- 자연어 가격 규칙(예: 코지장 HW 5만원, HB 10만원, 넓이 500 기준, 100 증가당 5천원)을 구조화 저장합니다.

CREATE TABLE IF NOT EXISTS rag_structured_pricing_rule (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,

    entity_type varchar(80) NOT NULL DEFAULT 'PRODUCT',
    entity_key varchar(255) NOT NULL,

    option_field varchar(120) NOT NULL DEFAULT '색상',
    option_value varchar(255) NOT NULL DEFAULT '',

    base_width numeric(14,2),
    base_height numeric(14,2),
    base_depth numeric(14,2),
    base_price numeric(14,2) NOT NULL,

    width_step numeric(14,2),
    width_step_price numeric(14,2),
    height_step numeric(14,2),
    height_step_price numeric(14,2),
    depth_step numeric(14,2),
    depth_step_price numeric(14,2),

    min_width numeric(14,2),
    max_width numeric(14,2),
    currency varchar(20) NOT NULL DEFAULT 'KRW',

    reason text,
    source_message text,
    plan_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    confidence numeric(5,4) NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uq_rag_structured_pricing_rule UNIQUE(
        project_id, version_id, entity_type, entity_key, option_field, option_value
    )
);

CREATE INDEX IF NOT EXISTS idx_rag_structured_pricing_rule_lookup
    ON rag_structured_pricing_rule(project_id, version_id, entity_type, entity_key, option_field, option_value, active);

CREATE INDEX IF NOT EXISTS idx_rag_structured_pricing_rule_scope
    ON rag_structured_pricing_rule(project_id, version_id, active, updated_at DESC);
