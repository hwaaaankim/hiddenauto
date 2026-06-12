-- 2026-06-12 최종 재해석/AI 승격 패치입니다.
-- 목적:
-- 1) GPT timeout 등으로 RAW/서버추출 상태로 저장된 지식 노드를 재해석 대기 상태로 관리합니다.
-- 2) 재해석 성공 시 기존 노드는 SUPERSEDED_BY_AI_RETRY로 비활성화하고 새 AI_PARSED 노드를 연결합니다.
-- 3) 같은 DB 안에서 재학습/변경/부분 재해석을 반복해도 트리 이력이 유지되도록 합니다.

ALTER TABLE rag_knowledge_node
    ADD COLUMN IF NOT EXISTS interpretation_status varchar(80) NOT NULL DEFAULT 'AI_PARSED';

ALTER TABLE rag_knowledge_node
    ADD COLUMN IF NOT EXISTS retryable boolean NOT NULL DEFAULT false;

ALTER TABLE rag_knowledge_node
    ADD COLUMN IF NOT EXISTS retry_count int NOT NULL DEFAULT 0;

ALTER TABLE rag_knowledge_node
    ADD COLUMN IF NOT EXISTS last_error text;

ALTER TABLE rag_knowledge_node
    ADD COLUMN IF NOT EXISTS supersedes_node_id uuid NULL REFERENCES rag_knowledge_node(id) ON DELETE SET NULL;

ALTER TABLE rag_knowledge_node
    ADD COLUMN IF NOT EXISTS retry_after_at timestamptz;

-- 기존 v3 패치에서 원문 보존/서버추출 상태로 들어간 노드를 재해석 대기 상태로 승격합니다.
UPDATE rag_knowledge_node
SET interpretation_status = 'AI_PARSE_PENDING',
    retryable = true,
    status = CASE WHEN status = 'ACTIVE' THEN 'NEEDS_AI_RETRY' ELSE status END,
    updated_at = now()
WHERE active = true
  AND (
      node_type IN ('RAW_PRESERVED_SEGMENT', 'LEAF_DETERMINISTIC_FALLBACK')
      OR structured_json::text LIKE '%RAW_PRESERVED_AFTER_AI_FAILURE%'
      OR structured_json::text LIKE '%SERVER_EXTRACTED_NEEDS_AI_RETRY%'
      OR metadata_json::text LIKE '%NEEDS_AI_RETRY%'
  );

CREATE INDEX IF NOT EXISTS idx_rag_knowledge_node_retryable
    ON rag_knowledge_node(project_id, version_id, topic, retryable, retry_count, created_at)
    WHERE active = true AND retryable = true;

CREATE INDEX IF NOT EXISTS idx_rag_knowledge_node_interpretation_status
    ON rag_knowledge_node(project_id, version_id, interpretation_status, active);

CREATE INDEX IF NOT EXISTS idx_rag_knowledge_node_supersedes
    ON rag_knowledge_node(supersedes_node_id);
