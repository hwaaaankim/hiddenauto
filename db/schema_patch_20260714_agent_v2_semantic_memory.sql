-- 022. GPT RAG DB Agent v2 / 통합 semantic memory / 실행계획·근거 검증
-- 적용 순서: 001~021 이후. 멱등 실행을 전제로 작성했습니다.
-- 주의: embedding vector 차원은 Java 기본값(text-embedding-3-small)과 동일한 1536입니다.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE rag_agent_run ADD COLUMN IF NOT EXISTS phase varchar(60) NOT NULL DEFAULT 'INITIALIZING';
ALTER TABLE rag_agent_run ADD COLUMN IF NOT EXISTS plan_json jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE rag_agent_run ADD COLUMN IF NOT EXISTS recovery_count integer NOT NULL DEFAULT 0;
ALTER TABLE rag_agent_run ADD COLUMN IF NOT EXISTS error_code varchar(160);
ALTER TABLE rag_agent_run ADD COLUMN IF NOT EXISTS error_detail_json jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE rag_agent_run ADD COLUMN IF NOT EXISTS user_answer text;
ALTER TABLE rag_agent_run ADD COLUMN IF NOT EXISTS evidence_json jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE rag_agent_run ADD COLUMN IF NOT EXISTS recovered boolean NOT NULL DEFAULT false;
ALTER TABLE rag_agent_run ADD COLUMN IF NOT EXISTS last_tool_name varchar(160);
ALTER TABLE rag_agent_run ADD COLUMN IF NOT EXISTS no_progress_count integer NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_rag_agent_run_phase
    ON rag_agent_run(project_id, version_id, phase, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_agent_request_plan (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id uuid NOT NULL UNIQUE REFERENCES rag_agent_run(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NULL,
    source_scope varchar(60) NOT NULL DEFAULT 'API',
    intent_type varchar(80) NOT NULL,
    user_goal text NOT NULL,
    requires_database boolean NOT NULL DEFAULT true,
    requires_semantic_search boolean NOT NULL DEFAULT false,
    requires_mutation boolean NOT NULL DEFAULT false,
    requires_deterministic_pricing boolean NOT NULL DEFAULT false,
    ambiguity_detected boolean NOT NULL DEFAULT false,
    clarification_question text,
    target_domains jsonb NOT NULL DEFAULT '[]'::jsonb,
    entity_hints_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    planned_steps_json jsonb NOT NULL DEFAULT '[]'::jsonb,
    risk_level varchar(20) NOT NULL DEFAULT 'LOW',
    plan_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_rag_agent_plan_risk CHECK (risk_level IN ('LOW','MEDIUM','HIGH'))
);

CREATE INDEX IF NOT EXISTS idx_rag_agent_request_plan_scope
    ON rag_agent_request_plan(project_id, version_id, intent_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_agent_request_plan_flags
    ON rag_agent_request_plan(project_id, version_id, requires_mutation, requires_deterministic_pricing, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_agent_observation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id uuid NOT NULL REFERENCES rag_agent_run(id) ON DELETE CASCADE,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    session_id uuid NULL,
    source_scope varchar(60) NOT NULL DEFAULT 'API',
    turn_no integer NOT NULL DEFAULT 0,
    response_id varchar(200),
    call_id varchar(200),
    tool_name varchar(160) NOT NULL,
    status varchar(40) NOT NULL,
    observation_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_agent_observation_run
    ON rag_agent_observation(run_id, turn_no, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_rag_agent_observation_scope
    ON rag_agent_observation(project_id, version_id, tool_name, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_semantic_memory (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    source_table varchar(160) NOT NULL,
    source_id uuid NOT NULL,
    source_kind varchar(80) NOT NULL DEFAULT 'KNOWLEDGE',
    domain_key varchar(80) NOT NULL DEFAULT 'OTHER',
    entity_type varchar(200),
    entity_key varchar(500),
    title text NOT NULL,
    content text NOT NULL,
    keywords text[] NOT NULL DEFAULT ARRAY[]::text[],
    aliases text[] NOT NULL DEFAULT ARRAY[]::text[],
    metadata_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    content_hash char(64) NOT NULL,
    embedding vector(1536),
    embedding_model varchar(160),
    embedding_status varchar(30) NOT NULL DEFAULT 'PENDING',
    embedding_error text,
    active boolean NOT NULL DEFAULT true,
    source_updated_at timestamptz,
    indexed_at timestamptz,
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector(
            'simple',
            coalesce(title, '') || ' ' || coalesce(content, '') || ' '
            || coalesce(entity_type, '') || ' ' || coalesce(entity_key, '')
        )
    ) STORED,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(project_id, version_id, source_table, source_id),
    CONSTRAINT chk_rag_semantic_memory_status
        CHECK (embedding_status IN ('PENDING','READY','ERROR','STALE'))
);

CREATE INDEX IF NOT EXISTS idx_rag_semantic_memory_scope
    ON rag_semantic_memory(project_id, version_id, active, domain_key, source_table, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_semantic_memory_entity
    ON rag_semantic_memory(project_id, version_id, entity_type, entity_key, active);
CREATE INDEX IF NOT EXISTS idx_rag_semantic_memory_search_vector
    ON rag_semantic_memory USING gin(search_vector);
CREATE INDEX IF NOT EXISTS idx_rag_semantic_memory_title_trgm
    ON rag_semantic_memory USING gin(title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_rag_semantic_memory_content_trgm
    ON rag_semantic_memory USING gin(content gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_rag_semantic_memory_embedding_hnsw
    ON rag_semantic_memory USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE embedding IS NOT NULL AND active = true;

CREATE TABLE IF NOT EXISTS rag_semantic_index_queue (
    id bigserial PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES rag_project(id) ON DELETE CASCADE,
    version_id uuid NOT NULL REFERENCES rag_project_version(id) ON DELETE CASCADE,
    source_table varchar(160) NOT NULL,
    source_id uuid NOT NULL,
    operation varchar(20) NOT NULL DEFAULT 'UPSERT',
    priority integer NOT NULL DEFAULT 100,
    status varchar(30) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL DEFAULT now(),
    locked_at timestamptz,
    lock_token uuid,
    last_error text,
    result_note text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    UNIQUE(project_id, version_id, source_table, source_id),
    CONSTRAINT chk_rag_semantic_queue_operation CHECK (operation IN ('UPSERT','DELETE')),
    CONSTRAINT chk_rag_semantic_queue_status CHECK (status IN ('PENDING','PROCESSING','DONE','ERROR'))
);

ALTER TABLE rag_semantic_index_queue ADD COLUMN IF NOT EXISTS lock_token uuid;

CREATE INDEX IF NOT EXISTS idx_rag_semantic_queue_claim
    ON rag_semantic_index_queue(project_id, version_id, status, available_at, priority, id);
CREATE INDEX IF NOT EXISTS idx_rag_semantic_queue_source
    ON rag_semantic_index_queue(source_table, source_id, updated_at DESC);

CREATE OR REPLACE FUNCTION rag_semantic_queue_source(
    p_project_id uuid,
    p_version_id uuid,
    p_source_table text,
    p_source_id uuid,
    p_operation text DEFAULT 'UPSERT',
    p_priority integer DEFAULT 100
)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    normalized_operation text := CASE
        WHEN upper(coalesce(p_operation, 'UPSERT')) = 'DELETE' THEN 'DELETE'
        ELSE 'UPSERT'
    END;
BEGIN
    IF p_project_id IS NULL OR p_version_id IS NULL OR p_source_id IS NULL OR p_source_table IS NULL THEN
        RETURN;
    END IF;

    -- 원본이 바뀐 순간 오래된 semantic 문서가 검색되지 않도록 즉시 STALE 처리합니다.
    UPDATE rag_semantic_memory
       SET embedding_status = 'STALE',
           active = false,
           updated_at = now()
     WHERE project_id = p_project_id
       AND version_id = p_version_id
       AND source_table = lower(p_source_table)
       AND source_id = p_source_id;

    INSERT INTO rag_semantic_index_queue(
        project_id, version_id, source_table, source_id, operation, priority,
        status, attempt_count, available_at, locked_at, lock_token, last_error, result_note,
        created_at, updated_at, completed_at
    ) VALUES (
        p_project_id, p_version_id, lower(p_source_table), p_source_id,
        normalized_operation,
        greatest(1, coalesce(p_priority, 100)),
        'PENDING', 0, now(), NULL, NULL, NULL, NULL, now(), now(), NULL
    )
    ON CONFLICT (project_id, version_id, source_table, source_id)
    DO UPDATE SET
        operation = EXCLUDED.operation,
        priority = least(rag_semantic_index_queue.priority, EXCLUDED.priority),
        status = 'PENDING',
        attempt_count = 0,
        available_at = now(),
        locked_at = NULL,
        lock_token = NULL,
        last_error = NULL,
        result_note = NULL,
        completed_at = NULL,
        updated_at = now();
END;
$$;

CREATE OR REPLACE FUNCTION rag_semantic_enqueue_trigger()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    row_data jsonb;
    v_project_id uuid;
    v_version_id uuid;
    v_source_id uuid;
    v_operation text;
BEGIN
    IF TG_OP = 'DELETE' THEN
        row_data := to_jsonb(OLD);
        v_operation := 'DELETE';
    ELSE
        row_data := to_jsonb(NEW);
        v_operation := 'UPSERT';
    END IF;

    v_source_id := nullif(row_data ->> 'id', '')::uuid;
    v_project_id := nullif(row_data ->> 'project_id', '')::uuid;
    v_version_id := nullif(row_data ->> 'version_id', '')::uuid;

    IF TG_TABLE_NAME = 'rag_structured_table_row' THEN
        SELECT p.project_id, p.version_id
          INTO v_project_id, v_version_id
          FROM rag_structured_table p
         WHERE p.id = nullif(row_data ->> 'table_id', '')::uuid;
    ELSIF TG_TABLE_NAME = 'rag_price_matrix_cell' THEN
        SELECT p.project_id, p.version_id
          INTO v_project_id, v_version_id
          FROM rag_price_matrix p
         WHERE p.id = nullif(row_data ->> 'matrix_id', '')::uuid;
    END IF;

    -- 부모 cascade DELETE 시 부모 row가 이미 보이지 않는 경우 기존 semantic meta에서 scope를 복구합니다.
    IF v_project_id IS NULL OR v_version_id IS NULL THEN
        SELECT m.project_id, m.version_id
          INTO v_project_id, v_version_id
          FROM rag_semantic_memory m
         WHERE m.source_table = TG_TABLE_NAME
           AND m.source_id = v_source_id
         ORDER BY m.updated_at DESC
         LIMIT 1;
    END IF;

    PERFORM rag_semantic_queue_source(
        v_project_id, v_version_id, TG_TABLE_NAME, v_source_id, v_operation, 100
    );

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

DO $attach_semantic_triggers$
DECLARE
    table_name text;
    supported_tables text[] := ARRAY[
        'rag_document',
        'rag_chunk',
        'rag_knowledge_node',
        'rag_knowledge_artifact',
        'rag_structured_table',
        'rag_structured_table_row',
        'rag_price_matrix',
        'rag_price_matrix_cell',
        'rag_dialog_rule',
        'rag_structured_pricing_rule',
        'rag_structured_override_rule',
        'rag_canonical_dataset',
        'rag_canonical_entity',
        'rag_canonical_fact',
        'rag_canonical_pricing_rule',
        'rag_canonical_dialog_flow',
        'rag_entity_alias',
        'rag_entity_asset_link'
    ];
BEGIN
    FOREACH table_name IN ARRAY supported_tables LOOP
        IF to_regclass('public.' || table_name) IS NULL THEN
            CONTINUE;
        END IF;
        EXECUTE format('DROP TRIGGER IF EXISTS %I ON public.%I',
                       'trg_semantic_upsert_' || table_name, table_name);
        EXECUTE format('CREATE TRIGGER %I AFTER INSERT OR UPDATE ON public.%I '
                       || 'FOR EACH ROW EXECUTE FUNCTION rag_semantic_enqueue_trigger()',
                       'trg_semantic_upsert_' || table_name, table_name);
        EXECUTE format('DROP TRIGGER IF EXISTS %I ON public.%I',
                       'trg_semantic_delete_' || table_name, table_name);
        EXECUTE format('CREATE TRIGGER %I BEFORE DELETE ON public.%I '
                       || 'FOR EACH ROW EXECUTE FUNCTION rag_semantic_enqueue_trigger()',
                       'trg_semantic_delete_' || table_name, table_name);
    END LOOP;
END
$attach_semantic_triggers$;

CREATE OR REPLACE FUNCTION rag_semantic_enqueue_scope(
    p_project_id uuid,
    p_version_id uuid,
    p_source_tables jsonb DEFAULT '[]'::jsonb
)
RETURNS integer
LANGUAGE plpgsql
AS $$
DECLARE
    supported_tables text[] := ARRAY[
        'rag_document','rag_chunk','rag_knowledge_node','rag_knowledge_artifact',
        'rag_structured_table','rag_structured_table_row','rag_price_matrix','rag_price_matrix_cell',
        'rag_dialog_rule','rag_structured_pricing_rule','rag_structured_override_rule',
        'rag_canonical_dataset','rag_canonical_entity','rag_canonical_fact',
        'rag_canonical_pricing_rule','rag_canonical_dialog_flow','rag_entity_alias','rag_entity_asset_link'
    ];
    requested_tables text[];
    table_name text;
    affected integer;
    total_affected integer := 0;
    sql_text text;
BEGIN
    IF p_project_id IS NULL OR p_version_id IS NULL THEN
        RAISE EXCEPTION 'project_id/version_id are required';
    END IF;

    IF p_source_tables IS NULL
       OR jsonb_typeof(p_source_tables) <> 'array'
       OR jsonb_array_length(p_source_tables) = 0 THEN
        requested_tables := supported_tables;
    ELSE
        SELECT array_agg(lower(value) ORDER BY ordinality)
          INTO requested_tables
          FROM jsonb_array_elements_text(p_source_tables) WITH ORDINALITY AS x(value, ordinality);
    END IF;

    FOREACH table_name IN ARRAY requested_tables LOOP
        IF NOT (table_name = ANY(supported_tables)) THEN
            RAISE EXCEPTION 'unsupported semantic source table: %', table_name;
        END IF;
        IF to_regclass('public.' || table_name) IS NULL THEN
            CONTINUE;
        END IF;

        IF table_name = 'rag_structured_table_row' THEN
            sql_text := $sql$
                INSERT INTO rag_semantic_index_queue(
                    project_id, version_id, source_table, source_id, operation, priority,
                    status, attempt_count, available_at, created_at, updated_at
                )
                SELECT p.project_id, p.version_id, 'rag_structured_table_row', c.id, 'UPSERT', 100,
                       'PENDING', 0, now(), now(), now()
                  FROM rag_structured_table_row c
                  JOIN rag_structured_table p ON p.id = c.table_id
                 WHERE p.project_id = $1 AND p.version_id = $2
                ON CONFLICT (project_id, version_id, source_table, source_id)
                DO UPDATE SET operation='UPSERT', status='PENDING', attempt_count=0,
                              available_at=now(), locked_at=NULL, lock_token=NULL, last_error=NULL,
                              result_note=NULL, completed_at=NULL, updated_at=now()
            $sql$;
        ELSIF table_name = 'rag_price_matrix_cell' THEN
            sql_text := $sql$
                INSERT INTO rag_semantic_index_queue(
                    project_id, version_id, source_table, source_id, operation, priority,
                    status, attempt_count, available_at, created_at, updated_at
                )
                SELECT p.project_id, p.version_id, 'rag_price_matrix_cell', c.id, 'UPSERT', 100,
                       'PENDING', 0, now(), now(), now()
                  FROM rag_price_matrix_cell c
                  JOIN rag_price_matrix p ON p.id = c.matrix_id
                 WHERE p.project_id = $1 AND p.version_id = $2
                ON CONFLICT (project_id, version_id, source_table, source_id)
                DO UPDATE SET operation='UPSERT', status='PENDING', attempt_count=0,
                              available_at=now(), locked_at=NULL, lock_token=NULL, last_error=NULL,
                              result_note=NULL, completed_at=NULL, updated_at=now()
            $sql$;
        ELSE
            sql_text := format($sql$
                INSERT INTO rag_semantic_index_queue(
                    project_id, version_id, source_table, source_id, operation, priority,
                    status, attempt_count, available_at, created_at, updated_at
                )
                SELECT project_id, version_id, %L, id, 'UPSERT', 100,
                       'PENDING', 0, now(), now(), now()
                  FROM public.%I
                 WHERE project_id = $1 AND version_id = $2
                ON CONFLICT (project_id, version_id, source_table, source_id)
                DO UPDATE SET operation='UPSERT', status='PENDING', attempt_count=0,
                              available_at=now(), locked_at=NULL, lock_token=NULL, last_error=NULL,
                              result_note=NULL, completed_at=NULL, updated_at=now()
            $sql$, table_name, table_name);
        END IF;

        EXECUTE sql_text USING p_project_id, p_version_id;
        GET DIAGNOSTICS affected = ROW_COUNT;
        total_affected := total_affected + affected;
    END LOOP;

    RETURN total_affected;
END;
$$;

COMMENT ON TABLE rag_agent_request_plan IS 'GPT Agent가 첫 function tool에서 선언한 요청 의도와 DB/semantic/변경/가격 실행계획';
COMMENT ON TABLE rag_agent_observation IS '도구별 사용자 답변 근거와 실행 관찰의 축약 감사 로그';
COMMENT ON TABLE rag_semantic_memory IS '여러 RAG 업무 테이블 row를 메타데이터와 embedding으로 통합한 하이브리드 검색 인덱스';
COMMENT ON TABLE rag_semantic_index_queue IS '원본 RAG row 변경을 semantic memory 재임베딩으로 연결하는 대기열';
COMMENT ON FUNCTION rag_semantic_enqueue_scope(uuid, uuid, jsonb) IS '지정 프로젝트/버전의 지원 source row를 semantic index queue에 멱등 등록';

-- GPT 스키마 사전에도 신규 역할을 설명합니다. NULL scope의 기존 동일 항목을 정리한 뒤 재삽입합니다.
DELETE FROM rag_agent_schema_note
 WHERE project_id IS NULL
   AND version_id IS NULL
   AND note_kind = 'TABLE'
   AND table_name IN ('rag_agent_request_plan','rag_agent_observation','rag_semantic_memory','rag_semantic_index_queue');

INSERT INTO rag_agent_schema_note(
    note_kind, table_name, object_name, title, description, usage_guide,
    when_to_read, when_to_write, risk_note, priority
) VALUES
('TABLE','rag_agent_request_plan',NULL,'Agent 요청 실행계획','첫 function call에서 GPT가 선언한 의도, DB/유사도/변경/가격 필요성과 모호성입니다.','관리자 실행 진단에서 계획과 실제 도구가 일치했는지 확인합니다.','Agent 오류·과잉조회·근거 누락 원인을 확인할 때 읽습니다.','Agent가 자동 기록하며 일반 GPT SQL로 수정하지 않습니다.','사용자 채팅에 직접 노출할 내부 계획 전문이 아닙니다.',12),
('TABLE','rag_agent_observation',NULL,'Agent 근거 관찰','각 도구 호출의 row 수, 후보, 계산, ChangeSet 등 사용자 답변 근거를 축약 저장합니다.','관리자 감사와 복구 답변 근거 확인에 사용합니다.','최종답변이 어떤 DB/semantic/가격 결과를 근거로 했는지 확인할 때 읽습니다.','Agent가 자동 기록합니다.','원본 전체 결과가 아니라 안전한 축약본입니다.',13),
('TABLE','rag_semantic_memory',NULL,'통합 Semantic Memory','제품·가격·발주·대화규칙·원문·정본 row를 공통 content/meta/embedding으로 정규화한 검색 인덱스입니다.','일반 SQL보다 search_semantic_memory 도구로 하이브리드 검색하는 것이 안전합니다.','완전일치하지 않는 제품명, 별칭, 중복 지식, 수정/삭제 후보를 찾을 때 사용합니다.','원본 트리거와 Java indexer가 갱신하며 직접 편집하지 않습니다.','원본 진실의 근거가 아니라 후보 인덱스이므로 최종 변경 전 원본 row 확인이 필요합니다.',18),
('TABLE','rag_semantic_index_queue',NULL,'Semantic Index Queue','원본 row 생성·수정·삭제 시 재임베딩 작업을 등록합니다.','관리자 상태/재처리에서 사용하고 소비자 채팅에는 노출하지 않습니다.','임베딩 지연·오류·대기 건수를 진단할 때 읽습니다.','원본 트리거와 관리자 rebuild가 기록합니다.','대량 rebuild는 OpenAI 비용과 처리시간을 증가시킬 수 있습니다.',19);

-- 신규 테이블도 current_setting 기반 범위 뷰를 생성합니다.
CREATE SCHEMA IF NOT EXISTS rag_agent_view;
DO $new_agent_views$
DECLARE
    table_name text;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'rag_agent_request_plan','rag_agent_observation','rag_semantic_memory','rag_semantic_index_queue'
    ] LOOP
        EXECUTE format(
            'CREATE OR REPLACE VIEW rag_agent_view.%I WITH (security_barrier=true) AS '
            || 'SELECT * FROM public.%I WHERE project_id = rag_agent_view.current_project_id() '
            || 'AND version_id = rag_agent_view.current_version_id()',
            table_name, table_name
        );
        EXECUTE format(
            'COMMENT ON VIEW rag_agent_view.%I IS %L',
            table_name,
            'GPT Agent project/version scoped read view for public.' || table_name
        );
    END LOOP;
END
$new_agent_views$;

REVOKE ALL ON TABLE rag_agent_request_plan FROM PUBLIC;
REVOKE ALL ON TABLE rag_agent_observation FROM PUBLIC;
REVOKE ALL ON TABLE rag_semantic_memory FROM PUBLIC;
REVOKE ALL ON TABLE rag_semantic_index_queue FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA rag_agent_view FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION rag_semantic_queue_source(uuid, uuid, text, uuid, text, integer) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION rag_semantic_enqueue_scope(uuid, uuid, jsonb) FROM PUBLIC;

DO $grant_agent_v2$
DECLARE
    role_name text;
    known_role text;
BEGIN
    -- 021 이전 버전에서 두 환경 역할에 공통 승인된 경우를 바로잡습니다.
    -- 현재 DB의 기존 업무 테이블 쓰기 권한이 없는 알려진 상대 환경 역할만 회수합니다.
    FOREACH known_role IN ARRAY ARRAY['ax_rag_dev_user','ax_rag_prod_user'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = known_role)
           AND NOT (
                has_table_privilege(known_role, 'public.rag_agent_run', 'SELECT')
                AND has_table_privilege(known_role, 'public.rag_agent_run', 'INSERT')
                AND has_table_privilege(known_role, 'public.rag_agent_run', 'UPDATE')
           ) THEN
            EXECUTE format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA rag_agent_view FROM %I', known_role);
            EXECUTE format('REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA rag_agent_view FROM %I', known_role);
            EXECUTE format('REVOKE USAGE ON SCHEMA rag_agent_view FROM %I', known_role);
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE rag_agent_request_plan, rag_agent_observation, rag_semantic_memory, rag_semantic_index_queue FROM %I', known_role);
            EXECUTE format('REVOKE ALL PRIVILEGES ON SEQUENCE rag_semantic_index_queue_id_seq FROM %I', known_role);
            EXECUTE format('REVOKE EXECUTE ON FUNCTION rag_semantic_queue_source(uuid, uuid, text, uuid, text, integer) FROM %I', known_role);
            EXECUTE format('REVOKE EXECUTE ON FUNCTION rag_semantic_enqueue_scope(uuid, uuid, jsonb) FROM %I', known_role);
        END IF;
    END LOOP;

    -- PostgreSQL 역할은 클러스터 전역이므로 dev/prod 역할명을 하드코딩해 교차 승인하지 않습니다.
    -- 현재 DB 소유자 또는 기존 rag_agent_run에 읽기/쓰기 권한을 이미 가진 로그인 역할만 승계합니다.
    FOR role_name IN
        SELECT DISTINCT r.rolname
          FROM pg_roles r
         WHERE r.rolcanlogin
           AND (
                r.oid = (SELECT d.datdba FROM pg_database d WHERE d.datname = current_database())
                OR (
                    has_table_privilege(r.rolname, 'public.rag_agent_run', 'SELECT')
                    AND has_table_privilege(r.rolname, 'public.rag_agent_run', 'INSERT')
                    AND has_table_privilege(r.rolname, 'public.rag_agent_run', 'UPDATE')
                )
           )
    LOOP
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE rag_agent_request_plan TO %I', role_name);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE rag_agent_observation TO %I', role_name);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE rag_semantic_memory TO %I', role_name);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE rag_semantic_index_queue TO %I', role_name);
        EXECUTE format('GRANT USAGE, SELECT, UPDATE ON SEQUENCE rag_semantic_index_queue_id_seq TO %I', role_name);
        EXECUTE format('GRANT EXECUTE ON FUNCTION rag_semantic_queue_source(uuid, uuid, text, uuid, text, integer) TO %I', role_name);
        EXECUTE format('GRANT EXECUTE ON FUNCTION rag_semantic_enqueue_scope(uuid, uuid, jsonb) TO %I', role_name);
        EXECUTE format('GRANT USAGE ON SCHEMA rag_agent_view TO %I', role_name);
        EXECUTE format('GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA rag_agent_view TO %I', role_name);
        EXECUTE format('GRANT SELECT ON ALL TABLES IN SCHEMA rag_agent_view TO %I', role_name);
    END LOOP;
END
$grant_agent_v2$;

-- 기존 데이터 전체 enqueue는 운영 마이그레이션 트랜잭션과 분리합니다.
-- 패치 적용 후 db/enqueue_semantic_rebuild_all.sql 또는 관리자 rebuild API를 실행하십시오.

COMMIT;
