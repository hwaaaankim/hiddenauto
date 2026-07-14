\set ON_ERROR_STOP on
\echo '[PRECHECK] Agent v2 base schema 확인'
DO $$
DECLARE
    required_table text;
BEGIN
    FOREACH required_table IN ARRAY ARRAY[
        'rag_project','rag_project_version','rag_agent_run','rag_agent_schema_note',
        'rag_document','rag_chunk','rag_knowledge_node','rag_structured_table',
        'rag_price_matrix','rag_dialog_rule','rag_canonical_entity','rag_canonical_fact'
    ] LOOP
        IF to_regclass('public.' || required_table) IS NULL THEN
            RAISE EXCEPTION '필수 선행 테이블이 없습니다: public.% (001~021 적용 여부 확인)', required_table;
        END IF;
    END LOOP;

    IF to_regnamespace('rag_agent_view') IS NULL THEN
        RAISE EXCEPTION '필수 선행 스키마 rag_agent_view가 없습니다. 021 패치를 먼저 적용하십시오.';
    END IF;
    IF to_regprocedure('rag_agent_view.current_project_id()') IS NULL
       OR to_regprocedure('rag_agent_view.current_version_id()') IS NULL THEN
        RAISE EXCEPTION 'Agent scope 함수가 없습니다. 021 패치를 먼저 적용하십시오.';
    END IF;
END
$$;
SELECT current_database() AS database_name,
       current_user AS migration_user,
       current_setting('server_version') AS postgres_version;
