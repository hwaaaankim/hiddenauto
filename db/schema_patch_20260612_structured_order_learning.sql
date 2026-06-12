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