-- 2026-06-19 AI 입력 의도 분류/저장지식 조회/이미지-지식 연결 고도화 패치
-- 적용 위치: 서버 PostgreSQL dev/prod DB 모두, 로컬은 SSH 터널로 동일 DB에 적용하거나 로컬 도커 DB를 별도로 쓰면 로컬 DB에도 적용합니다.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 목적:
-- 1) 사용자가 아무렇게나 입력해도 "학습/질문/발주상담/이미지연결/초기화"를 먼저 분류합니다.
-- 2) "현재 저장된 데이터 설명/보여줘" 류의 질문을 학습으로 오해하지 않고 조회 응답으로 처리합니다.
-- 3) "코지장의 이미지는 방금 업로드한 이미지" 같은 문장을 제품/지식 노드와 업로드 자산으로 연결합니다.

CREATE TABLE IF NOT EXISTS rag_interaction_event (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid,
    source_scope varchar(40) NOT NULL,          -- LEARNING / CHAT / API
    user_message text,
    file_count int NOT NULL DEFAULT 0,
    intent_type varchar(60) NOT NULL,           -- LEARN_KNOWLEDGE / ASK_KNOWLEDGE / ORDER_CONVERSATION / ASSET_LINK / RESET_KNOWLEDGE / UNKNOWN
    confidence numeric(5,4) NOT NULL DEFAULT 0,
    action_status varchar(60) NOT NULL DEFAULT 'ROUTED', -- HANDLED / PASS_TO_LEARNING / PASS_TO_CHAT / NEEDS_CLARIFICATION / FAILED
    answer text,
    reason text,
    extracted_entities_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    retrieved_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    result_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_interaction_event_scope
    ON rag_interaction_event(project_id, version_id, source_scope, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_interaction_event_intent
    ON rag_interaction_event(project_id, version_id, intent_type, action_status, created_at DESC);

ALTER TABLE rag_learning_message
    ADD COLUMN IF NOT EXISTS intent_type varchar(60),
    ADD COLUMN IF NOT EXISTS action_status varchar(60),
    ADD COLUMN IF NOT EXISTS intent_json jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE rag_chat_message
    ADD COLUMN IF NOT EXISTS intent_type varchar(60),
    ADD COLUMN IF NOT EXISTS action_status varchar(60),
    ADD COLUMN IF NOT EXISTS intent_json jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE rag_asset
    ADD COLUMN IF NOT EXISTS asset_kind varchar(40) NOT NULL DEFAULT 'FILE',
    ADD COLUMN IF NOT EXISTS semantic_caption text,
    ADD COLUMN IF NOT EXISTS linked_entity_type varchar(80),
    ADD COLUMN IF NOT EXISTS linked_entity_key varchar(255),
    ADD COLUMN IF NOT EXISTS ai_detected_entities_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_rag_asset_linked_entity
    ON rag_asset(project_id, version_id, linked_entity_type, linked_entity_key, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_entity_alias (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    entity_type varchar(80) NOT NULL DEFAULT 'PRODUCT',
    entity_key varchar(255) NOT NULL,
    alias varchar(255) NOT NULL,
    source varchar(80) NOT NULL DEFAULT 'AI_ROUTER',
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_rag_entity_alias UNIQUE(project_id, version_id, entity_type, entity_key, alias)
);

CREATE INDEX IF NOT EXISTS idx_rag_entity_alias_lookup
    ON rag_entity_alias(project_id, version_id, entity_type, alias, active);

CREATE TABLE IF NOT EXISTS rag_entity_asset_link (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    entity_type varchar(80) NOT NULL DEFAULT 'PRODUCT',
    entity_key varchar(255) NOT NULL,
    display_name varchar(255),
    asset_id uuid NOT NULL REFERENCES rag_asset(id) ON DELETE CASCADE,
    link_source_message text,
    confidence numeric(5,4) NOT NULL DEFAULT 0,
    status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_entity_asset_link_entity
    ON rag_entity_asset_link(project_id, version_id, entity_type, entity_key, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_entity_asset_link_asset
    ON rag_entity_asset_link(asset_id);

CREATE TABLE IF NOT EXISTS rag_knowledge_query_cache (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    query_hash varchar(128) NOT NULL,
    query_text text NOT NULL,
    answer text NOT NULL,
    retrieved_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_rag_knowledge_query_cache UNIQUE(project_id, version_id, query_hash)
);

CREATE INDEX IF NOT EXISTS idx_rag_knowledge_query_cache_alive
    ON rag_knowledge_query_cache(project_id, version_id, expires_at DESC);

-- 기존 구조화 행을 조회형 답변에서 빠르게 찾기 위한 보조 인덱스입니다.
CREATE INDEX IF NOT EXISTS idx_rag_structured_table_row_product_name
    ON rag_structured_table_row USING gin ((row_json::text) gin_trgm_ops);

