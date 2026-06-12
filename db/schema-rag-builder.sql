CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS rag_project (
    id uuid PRIMARY KEY,
    title varchar(200) NOT NULL,
    description text,
    default_chat_model varchar(100),
    default_embedding_model varchar(100),
    active_version_id uuid,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS rag_project_version (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_no int NOT NULL,
    title varchar(200) NOT NULL,
    learning_direction text,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    summary text,
    process_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    pricing_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    constraints_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    validation_report_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT uq_rag_project_version UNIQUE(project_id, version_no)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_rag_project_active_version'
    ) THEN
        ALTER TABLE rag_project
            ADD CONSTRAINT fk_rag_project_active_version
            FOREIGN KEY (active_version_id) REFERENCES rag_project_version(id) DEFERRABLE INITIALLY DEFERRED;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS rag_document (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    topic varchar(100),
    source_type varchar(50) NOT NULL,
    title varchar(300),
    original_filename varchar(300),
    raw_text text NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS rag_chunk (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES rag_document(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    chunk_no int NOT NULL,
    topic varchar(100),
    content text NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    embedding vector(1536),
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_chunk_project_version ON rag_chunk(project_id, version_id);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_topic ON rag_chunk(topic);

CREATE TABLE IF NOT EXISTS rag_learning_session (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    title varchar(300),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS rag_learning_message (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES rag_learning_session(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    role varchar(20) NOT NULL,
    message text NOT NULL,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_learning_message_session ON rag_learning_message(session_id, created_at);

CREATE TABLE IF NOT EXISTS rag_asset (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    owner_type varchar(30) NOT NULL,
    owner_key varchar(200) NOT NULL,
    original_filename varchar(300),
    stored_filename varchar(300) NOT NULL,
    content_type varchar(100),
    file_path text NOT NULL,
    file_url text NOT NULL,
    note text,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_asset_owner ON rag_asset(project_id, version_id, owner_type, owner_key);

CREATE TABLE IF NOT EXISTS rag_chat_session (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    user_label varchar(200),
    state_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    audit_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS rag_chat_message (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES rag_chat_session(id) ON DELETE CASCADE,
    role varchar(20) NOT NULL,
    content text NOT NULL,
    state_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
    retrieved_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_chat_message_session ON rag_chat_message(session_id, created_at);

CREATE TABLE IF NOT EXISTS rag_inquiry (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES rag_chat_session(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    company_name varchar(200),
    customer_name varchar(100),
    phone varchar(100),
    email varchar(200),
    memo text,
    state_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    audit_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    messages_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at timestamp NOT NULL DEFAULT now()
);

-- 전체 초기화가 필요할 때만 실행하세요.
-- TRUNCATE rag_inquiry, rag_chat_message, rag_chat_session, rag_asset, rag_learning_message,
--          rag_learning_session, rag_chunk, rag_document, rag_project_version, rag_project RESTART IDENTITY CASCADE;
-- 2026-06-12 주제별 구조화 학습/정확 가격계산 패치입니다.
-- 목적:
-- 1) 기존 rag_document/rag_chunk 벡터 검색은 유지합니다.
-- 2) 엑셀 가격표/제약표/색상표처럼 정확 계산이 필요한 자료는 별도 구조화 테이블에 저장합니다.
-- 3) "이 사이즈/단가 엑셀을 기존 것으로 교체" 요청 시 이전 구조화 자료를 비활성화하고 새 자료만 ACTIVE로 사용합니다.

CREATE TABLE IF NOT EXISTS rag_knowledge_artifact (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    topic varchar(255),
    artifact_key varchar(255) NOT NULL,
    artifact_type varchar(50) NOT NULL,
    semantic_role varchar(100) NOT NULL,
    title varchar(300),
    original_filename varchar(300),
    fingerprint varchar(128),
    active boolean NOT NULL DEFAULT true,
    status varchar(30) NOT NULL DEFAULT 'ACTIVE',
    parsed_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    replaced_by_id uuid NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_artifact_scope_active
    ON rag_knowledge_artifact(project_id, version_id, topic, semantic_role, active);

CREATE INDEX IF NOT EXISTS idx_rag_artifact_key_active
    ON rag_knowledge_artifact(project_id, version_id, artifact_key, active);

CREATE INDEX IF NOT EXISTS idx_rag_artifact_fingerprint
    ON rag_knowledge_artifact(project_id, version_id, fingerprint);

CREATE TABLE IF NOT EXISTS rag_structured_table (
    id uuid PRIMARY KEY,
    artifact_id uuid NOT NULL REFERENCES rag_knowledge_artifact(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    topic varchar(255),
    table_key varchar(255) NOT NULL,
    semantic_role varchar(100) NOT NULL,
    sheet_name varchar(255),
    range_a1 varchar(80),
    header_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_structured_table_scope
    ON rag_structured_table(project_id, version_id, semantic_role, active);

CREATE TABLE IF NOT EXISTS rag_structured_table_row (
    id uuid PRIMARY KEY,
    table_id uuid NOT NULL REFERENCES rag_structured_table(id) ON DELETE CASCADE,
    row_no int NOT NULL,
    row_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    searchable_text text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_structured_table_row_table
    ON rag_structured_table_row(table_id, row_no);

CREATE INDEX IF NOT EXISTS idx_rag_structured_table_row_search
    ON rag_structured_table_row USING gin (to_tsvector('simple', COALESCE(searchable_text, '')));

CREATE TABLE IF NOT EXISTS rag_price_matrix (
    id uuid PRIMARY KEY,
    artifact_id uuid NOT NULL REFERENCES rag_knowledge_artifact(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    topic varchar(255),
    matrix_key varchar(255) NOT NULL,
    semantic_role varchar(100) NOT NULL,
    sheet_name varchar(255),
    range_a1 varchar(80),
    row_axis_name varchar(100),
    col_axis_name varchar(100),
    rounding_policy_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_price_matrix_scope
    ON rag_price_matrix(project_id, version_id, semantic_role, active);

CREATE TABLE IF NOT EXISTS rag_price_matrix_cell (
    id uuid PRIMARY KEY,
    matrix_id uuid NOT NULL REFERENCES rag_price_matrix(id) ON DELETE CASCADE,
    row_key varchar(100) NOT NULL,
    col_key varchar(100) NOT NULL,
    row_numeric numeric,
    col_numeric numeric,
    numeric_value numeric,
    display_value varchar(255),
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_price_matrix_cell_lookup
    ON rag_price_matrix_cell(matrix_id, row_key, col_key);

CREATE INDEX IF NOT EXISTS idx_rag_price_matrix_cell_numeric
    ON rag_price_matrix_cell(matrix_id, row_numeric, col_numeric);

-- 기존 주제 초기화 기능과 같이 구조화 자료도 비활성화할 수 있도록 updated_at 유지용 함수성 패치입니다.
-- 실제 삭제 대신 active=false로 두는 이유는 단가 인상/교체 이력을 추적하기 위함입니다.
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
