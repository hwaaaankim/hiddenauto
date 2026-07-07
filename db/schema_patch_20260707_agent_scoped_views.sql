-- 2026-07-07 GPT DB Tool Agent 프로젝트/버전 범위 강제 조회 뷰
-- 전제: 001~020 적용 완료
-- 목적:
-- 1) 모델이 작성한 자유 SELECT가 public 원본 테이블을 직접 읽지 못하게 합니다.
-- 2) 현재 project/version은 Java가 transaction-local setting으로 주입합니다.
-- 3) 모든 rag_* 조회 뷰는 해당 범위의 row만 반환합니다.

CREATE SCHEMA IF NOT EXISTS rag_agent_view;
COMMENT ON SCHEMA rag_agent_view IS
'GPT DB Tool Agent 전용 읽기 스키마. current_setting 기반으로 현재 project/version 범위가 강제된 security_barrier view만 포함합니다.';

CREATE OR REPLACE FUNCTION rag_agent_view.current_project_id()
RETURNS uuid
LANGUAGE sql
STABLE
PARALLEL SAFE
AS $$
    SELECT NULLIF(current_setting('hiddenbath.rag_project_id', true), '')::uuid
$$;

CREATE OR REPLACE FUNCTION rag_agent_view.current_version_id()
RETURNS uuid
LANGUAGE sql
STABLE
PARALLEL SAFE
AS $$
    SELECT NULLIF(current_setting('hiddenbath.rag_version_id', true), '')::uuid
$$;

CREATE OR REPLACE FUNCTION rag_agent_view.current_session_id()
RETURNS uuid
LANGUAGE sql
STABLE
PARALLEL SAFE
AS $$
    SELECT NULLIF(current_setting('hiddenbath.rag_session_id', true), '')::uuid
$$;

DO $agent_views$
DECLARE
    r record;
    predicate text;
BEGIN
    FOR r IN
        SELECT t.table_name,
               bool_or(c.column_name = 'project_id') AS has_project_id,
               bool_or(c.column_name = 'version_id') AS has_version_id
        FROM information_schema.tables t
        LEFT JOIN information_schema.columns c
          ON c.table_schema = t.table_schema
         AND c.table_name = t.table_name
        WHERE t.table_schema = 'public'
          AND t.table_type = 'BASE TABLE'
          AND t.table_name LIKE 'rag\_%' ESCAPE '\'
        GROUP BY t.table_name
        ORDER BY t.table_name
    LOOP
        predicate := NULL;

        IF r.table_name = 'rag_project' THEN
            predicate := 'id = rag_agent_view.current_project_id()';
        ELSIF r.table_name = 'rag_project_version' THEN
            predicate := 'project_id = rag_agent_view.current_project_id() AND id = rag_agent_view.current_version_id()';
        ELSIF r.table_name = 'rag_agent_schema_note' THEN
            predicate := '(project_id IS NULL OR project_id = rag_agent_view.current_project_id()) '
                      || 'AND (version_id IS NULL OR version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name = 'rag_agent_change_item' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_agent_change_set p '
                      || 'WHERE p.id = rag_agent_change_item.change_set_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name = 'rag_canonical_job_log' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_canonical_job p '
                      || 'WHERE p.id = rag_canonical_job_log.job_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name = 'rag_chat_message' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_chat_session p '
                      || 'WHERE p.id = rag_chat_message.session_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name = 'rag_learning_job_file' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_learning_job p '
                      || 'WHERE p.id = rag_learning_job_file.job_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name = 'rag_learning_job_log' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_learning_job p '
                      || 'WHERE p.id = rag_learning_job_log.job_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name = 'rag_price_matrix_cell' THEN
            predicate := 'EXISTS (SELECT 1 FROM public.rag_price_matrix p '
                      || 'WHERE p.id = rag_price_matrix_cell.matrix_id '
                      || 'AND p.project_id = rag_agent_view.current_project_id() '
                      || 'AND p.version_id = rag_agent_view.current_version_id())';
        ELSIF r.table_name = 'rag_structured_table_row' THEN
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
            RAISE EXCEPTION 'Agent scoped view rule is missing for table %', r.table_name;
        END IF;

        EXECUTE format(
            'CREATE OR REPLACE VIEW rag_agent_view.%I WITH (security_barrier=true) AS SELECT * FROM public.%I WHERE %s',
            r.table_name,
            r.table_name,
            predicate
        );
        EXECUTE format(
            'COMMENT ON VIEW rag_agent_view.%I IS %L',
            r.table_name,
            'GPT Agent project/version scoped read view for public.' || r.table_name
        );
    END LOOP;
END
$agent_views$;

REVOKE ALL ON SCHEMA rag_agent_view FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA rag_agent_view FROM PUBLIC;
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA rag_agent_view FROM PUBLIC;

-- 서버의 실제 애플리케이션 계정이 존재하면 명시적으로 재부여합니다.
DO $grant_agent_views$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ax_rag_dev_user') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA rag_agent_view TO ax_rag_dev_user';
        EXECUTE 'GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA rag_agent_view TO ax_rag_dev_user';
        EXECUTE 'GRANT SELECT ON ALL TABLES IN SCHEMA rag_agent_view TO ax_rag_dev_user';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ax_rag_prod_user') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA rag_agent_view TO ax_rag_prod_user';
        EXECUTE 'GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA rag_agent_view TO ax_rag_prod_user';
        EXECUTE 'GRANT SELECT ON ALL TABLES IN SCHEMA rag_agent_view TO ax_rag_prod_user';
    END IF;
END
$grant_agent_views$;
