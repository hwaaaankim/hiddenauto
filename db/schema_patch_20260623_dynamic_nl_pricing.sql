-- 016. Dynamic natural language pricing interpreter / canonical formula quote support

CREATE TABLE IF NOT EXISTS rag_canonical_nl_parse_log (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NULL,
    source_scope varchar(60) NOT NULL DEFAULT 'API',
    user_message text NOT NULL,
    interpretation_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    result_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_nl_parse_log_scope
    ON rag_canonical_nl_parse_log(project_id, version_id, source_scope, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_nl_parse_log_session
    ON rag_canonical_nl_parse_log(session_id, created_at DESC);

-- Older canonical pricing rule patch versions did not have these operational columns.
ALTER TABLE rag_canonical_pricing_rule
    ADD COLUMN IF NOT EXISTS status varchar(80) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS active boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_rag_canonical_pricing_rule_active
    ON rag_canonical_pricing_rule(project_id, version_id, active, rule_key);

-- Canonical fact formula rules are queried heavily for dynamic natural-language quotes.
CREATE INDEX IF NOT EXISTS idx_rag_canonical_fact_pricing_rule
    ON rag_canonical_fact(project_id, version_id, active, fact_type, subject_name);
