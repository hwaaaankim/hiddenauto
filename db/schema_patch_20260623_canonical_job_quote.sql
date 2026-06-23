-- 015. Canonical async jobs / preview / dynamic change / quote support

CREATE TABLE IF NOT EXISTS rag_canonical_job (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NULL,
    job_type varchar(80) NOT NULL,
    instruction text,
    run_status varchar(30) NOT NULL DEFAULT 'RUNNING',
    status varchar(40) NOT NULL DEFAULT 'SUBMITTED',
    progress int NOT NULL DEFAULT 0,
    status_message text,
    result_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    error_message text,
    submitted_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz NULL,
    completed_at timestamptz NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_job_scope
    ON rag_canonical_job(project_id, version_id, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_job_status
    ON rag_canonical_job(run_status, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS rag_canonical_job_log (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES rag_canonical_job(id) ON DELETE CASCADE,
    status varchar(40) NOT NULL,
    progress int NOT NULL DEFAULT 0,
    message text,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_job_log_job
    ON rag_canonical_job_log(job_id, created_at ASC);

ALTER TABLE rag_canonical_fact
    ADD COLUMN IF NOT EXISTS changed_by_instruction text NULL,
    ADD COLUMN IF NOT EXISTS previous_fact_id uuid NULL,
    ADD COLUMN IF NOT EXISTS change_event_id uuid NULL;

CREATE INDEX IF NOT EXISTS idx_rag_canonical_fact_prev
    ON rag_canonical_fact(previous_fact_id);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_fact_change_event
    ON rag_canonical_fact(change_event_id);

CREATE TABLE IF NOT EXISTS rag_canonical_quote_log (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    dataset_id uuid NULL REFERENCES rag_canonical_dataset(id) ON DELETE SET NULL,
    request_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    result_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_canonical_quote_log_scope
    ON rag_canonical_quote_log(project_id, version_id, created_at DESC);
