-- 2026-06-12 적응형 계층/트리 학습 노드 패치입니다.
-- 목적:
-- 1) 긴 입력을 leaf → packet → root 형태의 지식 트리로 남깁니다.
-- 2) GPT 해석 실패 leaf도 원문 보존 노드로 저장하여 재시도/검토가 가능하게 합니다.
-- 3) 같은 주제 재학습/교체 요청 시 기존 active 노드를 비활성화하고 새 트리를 active로 사용합니다.

CREATE TABLE IF NOT EXISTS rag_knowledge_node (
    id uuid PRIMARY KEY,
    parent_id uuid NULL REFERENCES rag_knowledge_node(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    document_id uuid NULL REFERENCES rag_document(id) ON DELETE SET NULL,
    topic varchar(255),
    node_type varchar(80) NOT NULL,
    node_key varchar(300) NOT NULL,
    title varchar(500),
    summary text,
    raw_text text,
    structured_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    active boolean NOT NULL DEFAULT true,
    status varchar(40) NOT NULL DEFAULT 'ACTIVE',
    depth int NOT NULL DEFAULT 0,
    sort_order int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_knowledge_node_scope_active
    ON rag_knowledge_node(project_id, version_id, topic, active, depth, sort_order);

CREATE INDEX IF NOT EXISTS idx_rag_knowledge_node_parent
    ON rag_knowledge_node(parent_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_rag_knowledge_node_type
    ON rag_knowledge_node(project_id, version_id, node_type, active);

CREATE INDEX IF NOT EXISTS idx_rag_knowledge_node_key
    ON rag_knowledge_node(project_id, version_id, node_key, active);

CREATE INDEX IF NOT EXISTS idx_rag_knowledge_node_structured_gin
    ON rag_knowledge_node USING gin (structured_json);

CREATE INDEX IF NOT EXISTS idx_rag_knowledge_node_text_search
    ON rag_knowledge_node USING gin (
        to_tsvector('simple', COALESCE(title, '') || ' ' || COALESCE(summary, '') || ' ' || COALESCE(raw_text, ''))
    );
