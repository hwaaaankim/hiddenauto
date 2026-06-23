-- 014. Canonical Knowledge Engine
-- 목적: 가격요소/질문유형을 코드 enum으로 제한하지 않고, GPT와 서버가 발견한 모든 요소를 JSON factor/rule로 정본화합니다.

CREATE TABLE IF NOT EXISTS rag_canonical_dataset (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    title varchar(500) NOT NULL,
    instruction text,
    source_scope varchar(60) NOT NULL DEFAULT 'CANONICAL_ENGINE',
    build_mode varchar(60) NOT NULL DEFAULT 'REBUILD_FROM_ACTIVE_STRUCTURED_DATA',
    status varchar(40) NOT NULL DEFAULT 'RUNNING',
    active boolean NOT NULL DEFAULT false,
    summary_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_by_run_id uuid NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_dataset_scope
    ON rag_canonical_dataset(project_id, version_id, active, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_canonical_entity (
    id uuid PRIMARY KEY,
    dataset_id uuid NOT NULL REFERENCES rag_canonical_dataset(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    entity_type varchar(120) NOT NULL DEFAULT 'DYNAMIC_ENTITY',
    entity_key varchar(500) NOT NULL,
    display_name varchar(500),
    identity_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    attribute_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(80) NOT NULL DEFAULT 'ACTIVE',
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(dataset_id, entity_type, entity_key)
);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_entity_scope
    ON rag_canonical_entity(project_id, version_id, active, entity_type, entity_key);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_entity_identity_gin
    ON rag_canonical_entity USING gin(identity_json);

CREATE TABLE IF NOT EXISTS rag_canonical_fact (
    id uuid PRIMARY KEY,
    dataset_id uuid NOT NULL REFERENCES rag_canonical_dataset(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    entity_id uuid NULL REFERENCES rag_canonical_entity(id) ON DELETE SET NULL,
    entity_type varchar(120) NOT NULL DEFAULT 'DYNAMIC_ENTITY',
    subject_key varchar(500) NOT NULL,
    subject_name varchar(500),
    fact_type varchar(120) NOT NULL DEFAULT 'DYNAMIC_FACT',
    fact_key varchar(500) NOT NULL,
    factor_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    value_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    effective_from date NULL,
    effective_to date NULL,
    status varchar(80) NOT NULL DEFAULT 'ACTIVE',
    active boolean NOT NULL DEFAULT true,
    confidence numeric(8,4) NOT NULL DEFAULT 0.8000,
    source_table_id uuid NULL,
    source_row_id uuid NULL,
    source_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(dataset_id, subject_key, fact_type, fact_key, factor_json)
);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_fact_scope
    ON rag_canonical_fact(project_id, version_id, active, fact_type, subject_key);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_fact_factor_gin
    ON rag_canonical_fact USING gin(factor_json);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_fact_value_gin
    ON rag_canonical_fact USING gin(value_json);

CREATE TABLE IF NOT EXISTS rag_canonical_pricing_rule (
    id uuid PRIMARY KEY,
    dataset_id uuid NOT NULL REFERENCES rag_canonical_dataset(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    rule_key varchar(500) NOT NULL,
    title varchar(500),
    description text,
    factor_schema_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    formula_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    condition_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    priority int NOT NULL DEFAULT 100,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(dataset_id, rule_key)
);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_pricing_rule_scope
    ON rag_canonical_pricing_rule(project_id, version_id, active, priority);

CREATE TABLE IF NOT EXISTS rag_canonical_dialog_flow (
    id uuid PRIMARY KEY,
    dataset_id uuid NOT NULL REFERENCES rag_canonical_dataset(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    flow_key varchar(500) NOT NULL,
    purpose varchar(300),
    question_flow_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    validation_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    condition_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(dataset_id, flow_key)
);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_dialog_flow_scope
    ON rag_canonical_dialog_flow(project_id, version_id, active, flow_key);

CREATE TABLE IF NOT EXISTS rag_canonical_quality_issue (
    id uuid PRIMARY KEY,
    dataset_id uuid NOT NULL REFERENCES rag_canonical_dataset(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    issue_type varchar(120) NOT NULL,
    severity varchar(40) NOT NULL DEFAULT 'INFO',
    subject_key varchar(500),
    fact_type varchar(120),
    issue_key varchar(700),
    issue_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    resolved boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_quality_issue_scope
    ON rag_canonical_quality_issue(project_id, version_id, severity, issue_type, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_canonical_change_event (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    dataset_id uuid NULL REFERENCES rag_canonical_dataset(id) ON DELETE SET NULL,
    event_type varchar(120) NOT NULL,
    instruction text,
    before_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    after_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    impact_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    source_scope varchar(60) NOT NULL DEFAULT 'CANONICAL_ENGINE',
    created_by_run_id uuid NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_change_event_scope
    ON rag_canonical_change_event(project_id, version_id, created_at DESC);

-- 기존 running 컨테이너에 적용 시 harmless. 신규 볼륨 init에도 포함됩니다.
