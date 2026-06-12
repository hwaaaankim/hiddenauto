-- 2026-06-11 RAG 엑셀 학습/드래그앤드랍/주제 초기화 패치입니다.
-- 기존 데이터를 삭제하지 않습니다. 초기화 기능은 API 호출 시 선택한 주제 또는 버전에 대해서만 삭제합니다.
-- 2026-06-10 대화형 학습 패치의 컬럼/인덱스도 포함합니다.

ALTER TABLE rag_learning_session
    ADD COLUMN IF NOT EXISTS topic varchar(255);

UPDATE rag_learning_session
SET topic = title
WHERE topic IS NULL;

ALTER TABLE rag_learning_session
    ADD COLUMN IF NOT EXISTS pending_resolution_json jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE rag_learning_session
    ADD COLUMN IF NOT EXISTS resolution_status varchar(30) NOT NULL DEFAULT 'NONE';

CREATE TABLE IF NOT EXISTS rag_reset_event (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL,
    version_id uuid NOT NULL,
    topic varchar(255),
    reason text,
    result_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_learning_session_topic
    ON rag_learning_session(project_id, version_id, topic);

CREATE INDEX IF NOT EXISTS idx_rag_learning_session_resolution_status
    ON rag_learning_session(resolution_status);

CREATE INDEX IF NOT EXISTS idx_rag_document_project_version_created
    ON rag_document(project_id, version_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_document_project_version_topic_created
    ON rag_document(project_id, version_id, topic, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_learning_message_project_version_created
    ON rag_learning_message(project_id, version_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_chat_session_project_version_updated
    ON rag_chat_session(project_id, version_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_chat_message_session_created_desc
    ON rag_chat_message(session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_chunk_project_version_topic
    ON rag_chunk(project_id, version_id, topic);

CREATE INDEX IF NOT EXISTS idx_rag_asset_owner
    ON rag_asset(project_id, version_id, owner_type, owner_key);

CREATE INDEX IF NOT EXISTS idx_rag_reset_event_project_version_created
    ON rag_reset_event(project_id, version_id, created_at DESC);

-- 임베딩 생성이 일시 실패해도 원문 청크는 보존되도록 embedding NULL을 허용합니다.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'rag_chunk'
          AND column_name = 'embedding'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE rag_chunk ALTER COLUMN embedding DROP NOT NULL;
    END IF;
END $$;
