-- 2026-06-10 대화형 RAG 학습/상담 패치입니다.
-- 기존 데이터를 삭제하지 않습니다.
-- 모순/충돌이 발견되면 확정 지식에 기록하지 않고 세션의 해결 대기 상태로만 보관합니다.

ALTER TABLE rag_learning_session
    ADD COLUMN IF NOT EXISTS pending_resolution_json jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE rag_learning_session
    ADD COLUMN IF NOT EXISTS resolution_status varchar(30) NOT NULL DEFAULT 'NONE';

CREATE INDEX IF NOT EXISTS idx_rag_learning_session_resolution_status
    ON rag_learning_session(resolution_status);

CREATE INDEX IF NOT EXISTS idx_rag_document_project_version_created
    ON rag_document(project_id, version_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_learning_message_project_version_created
    ON rag_learning_message(project_id, version_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_chat_session_project_version_updated
    ON rag_chat_session(project_id, version_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_chat_message_session_created_desc
    ON rag_chat_message(session_id, created_at DESC);
