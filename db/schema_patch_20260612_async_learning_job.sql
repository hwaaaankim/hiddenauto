-- 2026-06-12 긴 입력/대용량 파일용 비동기 RAG 학습 작업 패치입니다.
-- 목적:
-- 1) HTTP 요청 안에서 GPT 해석을 끝내지 않고 작업만 생성합니다.
-- 2) DB에는 run_status=RUNNING, status=SUBMITTED/PREPROCESSING/GPT_INTERPRETING/VALIDATING/MERGING/VECTOR_INDEXING/COMPLETED 순서로 진행률을 기록합니다.
-- 3) 화면은 jobs/{jobId}를 폴링해서 완료 결과를 표시합니다.

CREATE TABLE IF NOT EXISTS rag_learning_job (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES rag_learning_session(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    topic varchar(255),
    run_status varchar(30) NOT NULL DEFAULT 'RUNNING',
    status varchar(40) NOT NULL DEFAULT 'SUBMITTED',
    progress int NOT NULL DEFAULT 0,
    status_message text,
    input_message text,
    force_save boolean NOT NULL DEFAULT false,
    file_count int NOT NULL DEFAULT 0,
    answer text,
    result_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    error_message text,
    submitted_at timestamptz NOT NULL DEFAULT now(),
    started_at timestamptz,
    completed_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_learning_job_session_created
    ON rag_learning_job(session_id, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_learning_job_status
    ON rag_learning_job(run_status, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS rag_learning_job_file (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES rag_learning_job(id) ON DELETE CASCADE,
    asset_id uuid REFERENCES rag_asset(id) ON DELETE SET NULL,
    original_filename varchar(300),
    content_type varchar(100),
    file_path text NOT NULL,
    size_bytes bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_learning_job_file_job
    ON rag_learning_job_file(job_id, created_at ASC);

CREATE TABLE IF NOT EXISTS rag_learning_job_log (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES rag_learning_job(id) ON DELETE CASCADE,
    status varchar(40) NOT NULL,
    progress int NOT NULL DEFAULT 0,
    message text,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_learning_job_log_job
    ON rag_learning_job_log(job_id, created_at ASC);
