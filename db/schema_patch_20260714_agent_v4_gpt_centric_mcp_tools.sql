-- HiddenBATHAuto RAG Agent V4 / GPT-centric MCP-like database tools
-- 적용 전제: 001~023(V3 production hardening 포함) 적용 완료
-- 특징: GPT-only answer provenance, 29개 tool capability dictionary,
--       entity resolution/order validation snapshots, model profile, scoped view refresh
-- 반복 실행 가능한 idempotent patch입니다.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE SCHEMA IF NOT EXISTS rag_agent_view;

ALTER TABLE rag_agent_run
    ADD COLUMN IF NOT EXISTS capability_version varchar(80) NOT NULL DEFAULT 'V4-20260714',
    ADD COLUMN IF NOT EXISTS response_type varchar(80) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS answer_source varchar(80) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS context_compaction_count integer NOT NULL DEFAULT 0;

UPDATE rag_agent_run
SET capability_version = COALESCE(NULLIF(capability_version, ''), 'V4-20260714'),
    response_type = COALESCE(NULLIF(response_type, ''), 'PENDING'),
    answer_source = COALESCE(NULLIF(answer_source, ''), 'NONE'),
    context_compaction_count = GREATEST(COALESCE(context_compaction_count, 0), 0)
WHERE capability_version IS NULL OR capability_version = ''
   OR response_type IS NULL OR response_type = ''
   OR answer_source IS NULL OR answer_source = ''
   OR context_compaction_count IS NULL OR context_compaction_count < 0;

-- V4 계획 스키마의 세분화된 판단 플래그를 실제 감사 테이블에도 보존합니다.
ALTER TABLE rag_agent_request_plan
    ADD COLUMN IF NOT EXISTS requires_entity_resolution boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS requires_order_validation boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS requires_impact_preview boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS requires_conversation_memory boolean NOT NULL DEFAULT false;

-- V4 plan schema는 파괴적/광범위 작업을 CRITICAL로 표시할 수 있습니다.
ALTER TABLE rag_agent_request_plan DROP CONSTRAINT IF EXISTS chk_rag_agent_plan_risk;
ALTER TABLE rag_agent_request_plan
    ADD CONSTRAINT chk_rag_agent_plan_risk
    CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')) NOT VALID;
ALTER TABLE rag_agent_request_plan VALIDATE CONSTRAINT chk_rag_agent_plan_risk;

CREATE INDEX IF NOT EXISTS idx_rag_agent_request_plan_v4_flags
    ON rag_agent_request_plan(
        project_id, version_id,
        requires_entity_resolution, requires_order_validation,
        requires_impact_preview, requires_conversation_memory,
        created_at DESC
    );

CREATE INDEX IF NOT EXISTS idx_rag_agent_run_v4_response
    ON rag_agent_run(project_id, version_id, response_type, answer_source, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_agent_run_v4_capability
    ON rag_agent_run(capability_version, status, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_agent_tool_capability (
    tool_name varchar(160) PRIMARY KEY,
    capability_version varchar(80) NOT NULL,
    category varchar(60) NOT NULL,
    description text NOT NULL,
    read_capable boolean NOT NULL DEFAULT true,
    write_capable boolean NOT NULL DEFAULT false,
    requires_confirmation boolean NOT NULL DEFAULT false,
    allowed_scopes text[] NOT NULL DEFAULT ARRAY['CHAT','LEARNING','API']::text[],
    active boolean NOT NULL DEFAULT true,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rag_agent_tool_capability_category
    ON rag_agent_tool_capability(capability_version, category, active, tool_name);

CREATE TABLE IF NOT EXISTS rag_agent_model_profile (
    profile_key varchar(80) PRIMARY KEY,
    capability_version varchar(80) NOT NULL,
    chat_model varchar(120) NOT NULL,
    embedding_model varchar(120) NOT NULL,
    embedding_dimensions integer NOT NULL,
    embedding_input_char_limit integer NOT NULL,
    gpt_only_answer boolean NOT NULL DEFAULT true,
    active boolean NOT NULL DEFAULT true,
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_rag_agent_model_profile_dimensions CHECK (embedding_dimensions > 0),
    CONSTRAINT ck_rag_agent_model_profile_input_limit CHECK (embedding_input_char_limit > 0)
);
UPDATE rag_agent_model_profile
   SET active = false, updated_at = now()
 WHERE profile_key <> 'DEFAULT' AND active = true;

INSERT INTO rag_agent_model_profile(
    profile_key, capability_version, chat_model, embedding_model,
    embedding_dimensions, embedding_input_char_limit, gpt_only_answer, active, metadata_json, updated_at
) VALUES (
    'DEFAULT', 'V4-20260714', 'gpt-5.5', 'text-embedding-3-small',
    1536, 4500, true, true,
    '{"embeddingDimensionStrategy":"dimensions_parameter","requiresFullReindex":false,"responseApi":"responses"}'::jsonb, now()
)
ON CONFLICT(profile_key) DO UPDATE SET
    capability_version=EXCLUDED.capability_version,
    chat_model=EXCLUDED.chat_model,
    embedding_model=EXCLUDED.embedding_model,
    embedding_dimensions=EXCLUDED.embedding_dimensions,
    embedding_input_char_limit=EXCLUDED.embedding_input_char_limit,
    gpt_only_answer=EXCLUDED.gpt_only_answer,
    active=EXCLUDED.active,
    metadata_json=EXCLUDED.metadata_json,
    updated_at=now();

CREATE UNIQUE INDEX IF NOT EXISTS uq_rag_agent_model_profile_active
    ON rag_agent_model_profile(active) WHERE active = true;

-- 기존 설정과 다른 임베딩 모델 또는 모델 미기록 벡터만 혼합 검색되지 않도록 stale 처리합니다.
-- 이후 enqueue_semantic_rebuild_all.sql을 실행하면 V4 worker가 text-embedding-3-small / 1536차원으로 다시 생성합니다.
UPDATE rag_semantic_memory
   SET embedding = NULL,
       embedding_model = NULL,
       embedding_status = 'STALE',
       embedding_error = 'V4_EMBEDDING_MODEL_REINDEX_REQUIRED:text-embedding-3-small',
       updated_at = now()
 WHERE embedding IS NOT NULL
   AND COALESCE(embedding_model, '') <> 'text-embedding-3-small';

CREATE TABLE IF NOT EXISTS rag_agent_entity_resolution (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id uuid NULL REFERENCES rag_agent_run(id) ON DELETE SET NULL,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NULL,
    source_scope varchar(40) NOT NULL DEFAULT 'API',
    input_expression text NOT NULL,
    entity_type_hint varchar(200),
    candidates_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    resolution_status varchar(40) NOT NULL DEFAULT 'AMBIGUOUS',
    selected_entity_type varchar(200),
    selected_entity_key varchar(500),
    confidence numeric(8,4) NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rag_agent_entity_resolution_scope
    ON rag_agent_entity_resolution(project_id, version_id, session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_agent_entity_resolution_expression
    ON rag_agent_entity_resolution USING gin (input_expression gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_rag_agent_entity_resolution_run
    ON rag_agent_entity_resolution(run_id, created_at);

CREATE TABLE IF NOT EXISTS rag_agent_order_state_snapshot (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id uuid NULL REFERENCES rag_agent_run(id) ON DELETE SET NULL,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NULL,
    source_scope varchar(40) NOT NULL DEFAULT 'API',
    order_state_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    validation_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    missing_fields_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    conflicts_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    valid boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_rag_agent_order_snapshot_scope
    ON rag_agent_order_state_snapshot(project_id, version_id, session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_agent_order_snapshot_run
    ON rag_agent_order_state_snapshot(run_id, created_at);
CREATE INDEX IF NOT EXISTS idx_rag_agent_order_snapshot_invalid
    ON rag_agent_order_state_snapshot(project_id, version_id, created_at DESC)
    WHERE valid = false;

CREATE TABLE IF NOT EXISTS rag_agent_answer_provenance (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id uuid NOT NULL REFERENCES rag_agent_run(id) ON DELETE CASCADE,
    answer_source varchar(80) NOT NULL,
    model_name varchar(120) NOT NULL,
    openai_response_id varchar(200),
    tool_names_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    evidence_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    answer_sha256 char(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_rag_agent_answer_provenance_source
        CHECK (answer_source IN ('GPT_SUBMIT_FINAL_ANSWER', 'GPT_STRUCTURED_RECOVERY')),
    CONSTRAINT uq_rag_agent_answer_provenance_run UNIQUE(run_id)
);
CREATE INDEX IF NOT EXISTS idx_rag_agent_answer_provenance_model
    ON rag_agent_answer_provenance(model_name, answer_source, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_agent_answer_provenance_response
    ON rag_agent_answer_provenance(openai_response_id)
    WHERE openai_response_id IS NOT NULL;

-- CREATE TABLE IF NOT EXISTS가 기존 부분 배포 테이블의 제약을 보강하지 못하는 경우를 처리합니다.
DO $v4_answer_constraints$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_rag_agent_answer_provenance_source') THEN
        ALTER TABLE rag_agent_answer_provenance
            ADD CONSTRAINT ck_rag_agent_answer_provenance_source
            CHECK (answer_source IN ('GPT_SUBMIT_FINAL_ANSWER', 'GPT_STRUCTURED_RECOVERY')) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='uq_rag_agent_answer_provenance_run') THEN
        ALTER TABLE rag_agent_answer_provenance
            ADD CONSTRAINT uq_rag_agent_answer_provenance_run UNIQUE(run_id);
    END IF;
END
$v4_answer_constraints$;

-- V3 메타 테이블에 누락될 수 있는 FK를 NOT VALID로 안전하게 추가합니다.
-- 기존 운영 orphan row 때문에 배포 전체가 롤백되지 않도록 즉시 VALIDATE하지 않습니다. verify 후 별도 검증할 수 있습니다.
DO $v4_fk$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_rag_agent_entity_alias_project') THEN
        ALTER TABLE rag_agent_entity_alias
            ADD CONSTRAINT fk_rag_agent_entity_alias_project
            FOREIGN KEY(project_id) REFERENCES rag_project(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_rag_agent_entity_alias_version') THEN
        ALTER TABLE rag_agent_entity_alias
            ADD CONSTRAINT fk_rag_agent_entity_alias_version
            FOREIGN KEY(version_id) REFERENCES rag_project_version(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_rag_agent_unresolved_run') THEN
        ALTER TABLE rag_agent_unresolved_reference
            ADD CONSTRAINT fk_rag_agent_unresolved_run
            FOREIGN KEY(run_id) REFERENCES rag_agent_run(id) ON DELETE SET NULL NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_rag_agent_unresolved_project') THEN
        ALTER TABLE rag_agent_unresolved_reference
            ADD CONSTRAINT fk_rag_agent_unresolved_project
            FOREIGN KEY(project_id) REFERENCES rag_project(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_rag_agent_unresolved_version') THEN
        ALTER TABLE rag_agent_unresolved_reference
            ADD CONSTRAINT fk_rag_agent_unresolved_version
            FOREIGN KEY(version_id) REFERENCES rag_project_version(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_rag_agent_context_snapshot_run') THEN
        ALTER TABLE rag_agent_context_snapshot
            ADD CONSTRAINT fk_rag_agent_context_snapshot_run
            FOREIGN KEY(run_id) REFERENCES rag_agent_run(id) ON DELETE CASCADE NOT VALID;
    END IF;
END
$v4_fk$;

-- 29개 GPT function tool을 DB 메타데이터로 제공하여 Agent/관리자 모두 실제 배포 capability를 확인할 수 있게 합니다.
INSERT INTO rag_agent_tool_capability(
    tool_name, capability_version, category, description,
    read_capable, write_capable, requires_confirmation, allowed_scopes, active, metadata_json, updated_at
) VALUES
('submit_request_plan','V4-20260714','CONTROL','요청 의도·필요 근거·변경 위험을 GPT가 먼저 계획합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{"firstTool":true}'::jsonb,now()),
('get_agent_capabilities','V4-20260714','CONTROL','현재 Agent 도구와 GPT-only 응답 계약을 확인합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('get_database_overview','V4-20260714','SCHEMA','RAG DB 테이블과 범위 정책을 확인합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('get_knowledge_inventory','V4-20260714','KNOWLEDGE','저장된 지식 영역과 실제 row 수를 조사합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('search_database_catalog','V4-20260714','SCHEMA','업무 용어로 테이블·컬럼·스키마 노트를 찾습니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('search_semantic_memory','V4-20260714','KNOWLEDGE','임베딩·FTS·별칭 기반 후보를 찾습니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('search_knowledge_sources','V4-20260714','KNOWLEDGE','원문과 source row를 어휘 기반으로 폭넓게 찾습니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('get_document_context','V4-20260714','KNOWLEDGE','문서 원문과 인접 청크를 조회합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('resolve_entity_reference','V4-20260714','ENTITY','오타·별칭·유사 표현을 실제 엔티티 후보로 해석합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('get_entity_context_bundle','V4-20260714','ENTITY','하나의 엔티티에 연결된 정본·규칙·가격·원문을 묶어 조회합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('get_effective_rules','V4-20260714','RULE','지정 날짜에 유효한 dialog·pricing·override·fact 규칙을 조회합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('get_order_flow','V4-20260714','ORDER','주문 질문 순서·조건부 분기·검증 흐름을 조회합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('validate_order_state','V4-20260714','ORDER','현재 주문 답변의 누락·충돌·다음 질문을 검증합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('compare_entity_candidates','V4-20260714','ENTITY','복수 후보의 실제 근거 차이를 비교합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('describe_table','V4-20260714','SCHEMA','테이블 컬럼·PK·인덱스·제한된 샘플을 확인합니다.',true,false,false,ARRAY['LEARNING','API'],true,'{}'::jsonb,now()),
('list_table_relationships','V4-20260714','SCHEMA','외래키 방향과 연관 테이블을 확인합니다.',true,false,false,ARRAY['LEARNING','API'],true,'{}'::jsonb,now()),
('get_table_statistics','V4-20260714','SCHEMA','row 수와 active/status 분포를 확인합니다.',true,false,false,ARRAY['LEARNING','API'],true,'{}'::jsonb,now()),
('query_database','V4-20260714','DATABASE','프로젝트·버전 제한 뷰에서 검증된 SELECT/WITH를 실행합니다.',true,false,false,ARRAY['LEARNING','API'],true,'{"scopedViewOnly":true}'::jsonb,now()),
('find_canonical_price_candidates','V4-20260714','PRICE','가격 엔티티와 규칙 후보를 찾습니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('calculate_order_price','V4-20260714','PRICE','확정 입력을 결정론적 Java 가격계산기로 계산합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{"deterministic":true}'::jsonb,now()),
('simulate_price_scenarios','V4-20260714','PRICE','복수 주문 조건을 같은 결정론적 계산기로 비교합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{"deterministic":true}'::jsonb,now()),
('preview_change_impact','V4-20260714','MUTATION','변경 전에 대상·FK·참조 영향을 확인합니다.',true,false,false,ARRAY['LEARNING','API'],true,'{}'::jsonb,now()),
('get_conversation_memory','V4-20260714','MEMORY','현재 세션 working memory를 읽습니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('update_conversation_memory','V4-20260714','MEMORY','확정된 대화 문맥을 세션 메모리에 갱신합니다.',true,true,false,ARRAY['CHAT','LEARNING','API'],true,'{"persistentKnowledge":false}'::jsonb,now()),
('get_conversation_history','V4-20260714','MEMORY','현재 세션의 최근 대화를 조회합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{}'::jsonb,now()),
('create_change_set','V4-20260714','MUTATION','검증된 저장·수정·삭제 계획을 보류 또는 트랜잭션 적용합니다.',true,true,true,ARRAY['LEARNING','API'],true,'{"directSqlWrite":false}'::jsonb,now()),
('get_change_set','V4-20260714','MUTATION','변경계획과 적용 상태를 조회합니다.',true,false,false,ARRAY['LEARNING','API'],true,'{}'::jsonb,now()),
('get_agent_run_audit','V4-20260714','CONTROL','Agent 실행의 도구·SQL·변경·GPT 답변 출처를 범위 제한해 확인합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{"chatOwnSessionOnly":true}'::jsonb,now()),
('submit_final_answer','V4-20260714','CONTROL','GPT가 작성한 최종 사용자 답변을 제출하며 빈 답변은 거부합니다.',true,false,false,ARRAY['CHAT','LEARNING','API'],true,'{"answerOwner":"GPT"}'::jsonb,now())
ON CONFLICT(tool_name) DO UPDATE SET
    capability_version=EXCLUDED.capability_version,
    category=EXCLUDED.category,
    description=EXCLUDED.description,
    read_capable=EXCLUDED.read_capable,
    write_capable=EXCLUDED.write_capable,
    requires_confirmation=EXCLUDED.requires_confirmation,
    allowed_scopes=EXCLUDED.allowed_scopes,
    active=EXCLUDED.active,
    metadata_json=EXCLUDED.metadata_json,
    updated_at=now();

-- 스키마 사전에 신규 메타 테이블을 설명합니다.
DELETE FROM rag_agent_schema_note
WHERE project_id IS NULL AND version_id IS NULL AND note_kind='TABLE'
  AND table_name IN (
    'rag_agent_tool_capability','rag_agent_model_profile','rag_agent_entity_resolution',
    'rag_agent_order_state_snapshot','rag_agent_answer_provenance'
  );

INSERT INTO rag_agent_schema_note(
    note_kind, table_name, object_name, title, description,
    usage_guide, when_to_read, when_to_write, risk_note, priority, active
) VALUES
('TABLE','rag_agent_tool_capability',NULL,'Agent Tool Capability 사전','현재 배포된 29개 GPT function tool의 기능·범위·쓰기 가능 여부를 저장합니다.','Agent 기능 확인과 배포 버전 검증에 사용합니다.','어떤 DB Tool을 사용할 수 있는지 또는 배포 누락을 점검할 때 조회합니다.','V4 스키마 패치가 upsert하며 일반 업무 Agent가 직접 수정하지 않습니다.','도구 코드와 메타 버전이 불일치하면 잘못된 계획을 세울 수 있습니다.',5,true),
('TABLE','rag_agent_model_profile',NULL,'Agent 모델 프로필','GPT-5.5, text-embedding-3-small, 1536차원, GPT-only 응답 정책의 활성 배포값을 저장합니다.','Java/YML/DB 재색인 설정이 같은 모델 프로필인지 확인합니다.','모델 교체, 임베딩 재색인, 답변 출처 정책 점검 시 조회합니다.','V4 스키마 패치가 upsert하며 런타임 환경변수 변경 시 함께 갱신해야 합니다.','임베딩 모델이 다른 벡터를 혼합하면 유사도 결과가 왜곡될 수 있습니다.',5,true),
('TABLE','rag_agent_entity_resolution',NULL,'엔티티 표현 해석 이력','사용자의 오타·별칭·불완전 표현과 후보·신뢰도·확정 여부를 기록합니다.','유사 제품/규칙을 능동적으로 찾은 근거와 반복 모호성을 분석합니다.','대상 후보가 여러 개였던 이유나 별칭 보강 대상을 확인할 때 조회합니다.','resolve_entity_reference 도구가 자동 기록합니다.','후보는 확정 사실이 아니므로 원본 context bundle 재검증이 필요합니다.',35,true),
('TABLE','rag_agent_order_state_snapshot',NULL,'주문 상태 검증 스냅샷','상담 중 수집된 주문 JSON과 누락값·충돌·유효성 결과를 기록합니다.','복잡한 주문 프로세스의 다음 질문과 실패 원인을 분석합니다.','주문 상담 누락/충돌 또는 규칙 품질을 점검할 때 조회합니다.','validate_order_state 도구가 자동 기록합니다.','개인정보가 포함될 수 있으므로 관리자 범위로만 노출해야 합니다.',36,true),
('TABLE','rag_agent_answer_provenance',NULL,'GPT 답변 출처 증명','최종 사용자 답변이 GPT submit 또는 GPT structured recovery에서 생성되었음을 모델·도구·근거·SHA-256으로 기록합니다.','Java/JavaScript 고정 답변이 answer로 유입되지 않았는지 감사합니다.','답변 출처, 사용 모델, 근거 도구를 검증할 때 조회합니다.','최종 GPT 답변 승인 직후 서버가 자동 기록합니다.','answer 원문 대신 hash만 보관하며 run/evidence 접근 권한을 제한해야 합니다.',5,true);

COMMENT ON COLUMN rag_agent_run.capability_version IS '배포된 GPT DB Tool capability 버전';
COMMENT ON COLUMN rag_agent_run.response_type IS 'GPT_ANSWER, TECHNICAL_ERROR, SYSTEM_EVENT 등 응답 종류';
COMMENT ON COLUMN rag_agent_run.answer_source IS 'GPT_SUBMIT_FINAL_ANSWER, GPT_STRUCTURED_RECOVERY 또는 NONE';
COMMENT ON COLUMN rag_agent_run.context_compaction_count IS '도구 루프 중 context budget 압축 적용 횟수';
COMMENT ON TABLE rag_agent_tool_capability IS 'GPT Agent가 사용할 수 있는 29개 function tool의 배포 메타';
COMMENT ON TABLE rag_agent_model_profile IS '활성 GPT/embedding 모델과 차원·GPT-only 정책 배포 메타';
COMMENT ON TABLE rag_agent_entity_resolution IS '유사명/오타/별칭 엔티티 후보 해석 감사 이력';
COMMENT ON TABLE rag_agent_order_state_snapshot IS '복잡한 주문 입력의 누락·충돌·다음 질문 검증 이력';
COMMENT ON TABLE rag_agent_answer_provenance IS '사용자 answer가 GPT가 작성했음을 모델·도구·근거 hash로 증명';

-- V3/V4 신규 테이블까지 포함해 project/version security_barrier view를 전체 재생성합니다.
CREATE OR REPLACE FUNCTION rag_agent_view.current_project_id()
RETURNS uuid LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT NULLIF(current_setting('hiddenbath.rag_project_id', true), '')::uuid
$$;
CREATE OR REPLACE FUNCTION rag_agent_view.current_version_id()
RETURNS uuid LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT NULLIF(current_setting('hiddenbath.rag_version_id', true), '')::uuid
$$;
CREATE OR REPLACE FUNCTION rag_agent_view.current_session_id()
RETURNS uuid LANGUAGE sql STABLE PARALLEL SAFE AS $$
    SELECT NULLIF(current_setting('hiddenbath.rag_session_id', true), '')::uuid
$$;

DO $agent_views_v4$
DECLARE
    r record;
    predicate text;
BEGIN
    FOR r IN
        SELECT t.table_name,
               bool_or(c.column_name='project_id') AS has_project_id,
               bool_or(c.column_name='version_id') AS has_version_id
        FROM information_schema.tables t
        LEFT JOIN information_schema.columns c
          ON c.table_schema=t.table_schema AND c.table_name=t.table_name
        WHERE t.table_schema='public'
          AND t.table_type='BASE TABLE'
          AND t.table_name LIKE 'rag\_%' ESCAPE '\'
        GROUP BY t.table_name
        ORDER BY t.table_name
    LOOP
        predicate := NULL;
        IF r.table_name='rag_project' THEN
            predicate := 'id = rag_agent_view.current_project_id()';
        ELSIF r.table_name='rag_project_version' THEN
            predicate := 'project_id = rag_agent_view.current_project_id() AND id = rag_agent_view.current_version_id()';
        ELSIF r.table_name='rag_agent_schema_note' THEN
            predicate := '(project_id IS NULL OR project_id = rag_agent_view.current_project_id()) '
                      || 'AND (version_id IS NULL OR version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name='rag_agent_tool_capability' THEN
            predicate := 'active = true';
        ELSIF r.table_name='rag_agent_model_profile' THEN
            predicate := 'active = true';
        ELSIF r.table_name='rag_agent_entity_alias' THEN
            predicate := 'project_id = rag_agent_view.current_project_id() '
                      || 'AND (version_id IS NULL OR version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name='rag_agent_unresolved_reference' THEN
            predicate := 'project_id = rag_agent_view.current_project_id() '
                      || 'AND (version_id IS NULL OR version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name='rag_agent_context_snapshot' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_agent_run p '
                      || 'WHERE p.id = rag_agent_context_snapshot.run_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name='rag_agent_answer_provenance' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_agent_run p '
                      || 'WHERE p.id = rag_agent_answer_provenance.run_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name='rag_agent_change_item' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_agent_change_set p '
                      || 'WHERE p.id = rag_agent_change_item.change_set_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name='rag_canonical_job_log' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_canonical_job p '
                      || 'WHERE p.id = rag_canonical_job_log.job_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name='rag_chat_message' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_chat_session p '
                      || 'WHERE p.id = rag_chat_message.session_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name='rag_learning_job_file' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_learning_job p '
                      || 'WHERE p.id = rag_learning_job_file.job_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name='rag_learning_job_log' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_learning_job p '
                      || 'WHERE p.id = rag_learning_job_log.job_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name='rag_price_matrix_cell' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_price_matrix p '
                      || 'WHERE p.id = rag_price_matrix_cell.matrix_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name='rag_structured_table_row' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_structured_table p '
                      || 'WHERE p.id = rag_structured_table_row.table_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.has_project_id AND r.has_version_id THEN
            predicate := 'project_id = rag_agent_view.current_project_id() '
                      || 'AND version_id = rag_agent_view.current_version_id()';
        ELSIF r.has_project_id THEN
            predicate := 'project_id = rag_agent_view.current_project_id()';
        ELSIF r.has_version_id THEN
            predicate := 'version_id = rag_agent_view.current_version_id()';
        ELSE
            -- 패키지 외 과거/실험 rag_* 테이블이 서버에 남아 있어도 배포를 중단하지 않습니다.
            -- 범위 규칙을 증명할 수 없는 테이블은 deny-all view로 생성해 GPT 자유조회에서 노출하지 않습니다.
            predicate := 'false';
            RAISE WARNING 'Agent V4 scoped view rule is missing for table %. deny-all view is created.', r.table_name;
        END IF;

        EXECUTE format(
            'CREATE OR REPLACE VIEW rag_agent_view.%I WITH (security_barrier=true) AS SELECT * FROM public.%I WHERE %s',
            r.table_name, r.table_name, predicate
        );
    END LOOP;
END
$agent_views_v4$;

REVOKE ALL ON SCHEMA rag_agent_view FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA rag_agent_view FROM PUBLIC;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA rag_agent_view FROM PUBLIC;

DO $grant_agent_views_v4$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='ax_rag_dev_user') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA rag_agent_view TO ax_rag_dev_user';
        EXECUTE 'GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA rag_agent_view TO ax_rag_dev_user';
        EXECUTE 'GRANT SELECT ON ALL TABLES IN SCHEMA rag_agent_view TO ax_rag_dev_user';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON rag_agent_tool_capability, rag_agent_model_profile, rag_agent_entity_resolution, rag_agent_order_state_snapshot, rag_agent_answer_provenance TO ax_rag_dev_user';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='ax_rag_prod_user') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA rag_agent_view TO ax_rag_prod_user';
        EXECUTE 'GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA rag_agent_view TO ax_rag_prod_user';
        EXECUTE 'GRANT SELECT ON ALL TABLES IN SCHEMA rag_agent_view TO ax_rag_prod_user';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON rag_agent_tool_capability, rag_agent_model_profile, rag_agent_entity_resolution, rag_agent_order_state_snapshot, rag_agent_answer_provenance TO ax_rag_prod_user';
    END IF;
END
$grant_agent_views_v4$;

COMMIT;
