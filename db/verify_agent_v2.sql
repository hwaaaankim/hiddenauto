\set ON_ERROR_STOP on
\echo '[VERIFY] Agent v2 DB 객체 및 vector 차원 확인'
DO $$
DECLARE
    required_table text;
    required_function text;
    wrong_vector_type text;
BEGIN
    FOREACH required_table IN ARRAY ARRAY[
        'rag_agent_request_plan','rag_agent_observation',
        'rag_semantic_memory','rag_semantic_index_queue'
    ] LOOP
        IF to_regclass('public.' || required_table) IS NULL THEN
            RAISE EXCEPTION 'Agent v2 테이블이 없습니다: public.%', required_table;
        END IF;
        IF to_regclass('rag_agent_view.' || required_table) IS NULL THEN
            RAISE EXCEPTION 'Agent v2 범위 뷰가 없습니다: rag_agent_view.%', required_table;
        END IF;
    END LOOP;

    IF to_regprocedure('public.rag_semantic_queue_source(uuid,uuid,text,uuid,text,integer)') IS NULL THEN
        RAISE EXCEPTION 'rag_semantic_queue_source 함수가 없습니다.';
    END IF;
    IF to_regprocedure('public.rag_semantic_enqueue_scope(uuid,uuid,jsonb)') IS NULL THEN
        RAISE EXCEPTION 'rag_semantic_enqueue_scope 함수가 없습니다.';
    END IF;

    SELECT format_type(a.atttypid, a.atttypmod)
      INTO wrong_vector_type
      FROM pg_attribute a
     WHERE a.attrelid = 'public.rag_semantic_memory'::regclass
       AND a.attname = 'embedding'
       AND NOT a.attisdropped;
    IF wrong_vector_type IS DISTINCT FROM 'vector(1536)' THEN
        RAISE EXCEPTION 'embedding 컬럼 형식이 vector(1536)이 아닙니다: %', wrong_vector_type;
    END IF;
END
$$;

WITH supported(table_name) AS (
    VALUES
      ('rag_document'),('rag_chunk'),('rag_knowledge_node'),('rag_knowledge_artifact'),
      ('rag_structured_table'),('rag_structured_table_row'),('rag_price_matrix'),('rag_price_matrix_cell'),
      ('rag_dialog_rule'),('rag_structured_pricing_rule'),('rag_structured_override_rule'),
      ('rag_canonical_dataset'),('rag_canonical_entity'),('rag_canonical_fact'),
      ('rag_canonical_pricing_rule'),('rag_canonical_dialog_flow'),('rag_entity_alias'),('rag_entity_asset_link')
), existing AS (
    SELECT table_name FROM supported WHERE to_regclass('public.' || table_name) IS NOT NULL
), trigger_counts AS (
    SELECT event_object_table AS table_name, COUNT(*) AS trigger_count
    FROM information_schema.triggers
    WHERE trigger_schema = 'public'
      AND trigger_name LIKE 'trg_semantic_%'
    GROUP BY event_object_table
)
SELECT e.table_name,
       COALESCE(t.trigger_count, 0) AS trigger_count,
       CASE WHEN COALESCE(t.trigger_count, 0) >= 2 THEN 'OK' ELSE 'MISSING' END AS status
FROM existing e
LEFT JOIN trigger_counts t USING (table_name)
ORDER BY e.table_name;

DO $$
DECLARE
    missing_count integer;
BEGIN
    WITH supported(table_name) AS (
        VALUES
          ('rag_document'),('rag_chunk'),('rag_knowledge_node'),('rag_knowledge_artifact'),
          ('rag_structured_table'),('rag_structured_table_row'),('rag_price_matrix'),('rag_price_matrix_cell'),
          ('rag_dialog_rule'),('rag_structured_pricing_rule'),('rag_structured_override_rule'),
          ('rag_canonical_dataset'),('rag_canonical_entity'),('rag_canonical_fact'),
          ('rag_canonical_pricing_rule'),('rag_canonical_dialog_flow'),('rag_entity_alias'),('rag_entity_asset_link')
    ), existing AS (
        SELECT table_name FROM supported WHERE to_regclass('public.' || table_name) IS NOT NULL
    )
    SELECT COUNT(*) INTO missing_count
    FROM existing e
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.triggers t
        WHERE t.trigger_schema='public'
          AND t.event_object_table=e.table_name
          AND t.trigger_name='trg_semantic_upsert_' || e.table_name
    ) OR NOT EXISTS (
        SELECT 1 FROM information_schema.triggers t
        WHERE t.trigger_schema='public'
          AND t.event_object_table=e.table_name
          AND t.trigger_name='trg_semantic_delete_' || e.table_name
    );
    IF missing_count > 0 THEN
        RAISE EXCEPTION 'semantic trigger가 누락된 source table 수: %', missing_count;
    END IF;
END
$$;

SELECT extname, extversion
FROM pg_extension
WHERE extname IN ('pgcrypto','vector','pg_trgm')
ORDER BY extname;

SELECT embedding_status, active, COUNT(*) AS memory_count
FROM rag_semantic_memory
GROUP BY embedding_status, active
ORDER BY embedding_status, active DESC;

SELECT status, COUNT(*) AS queue_count
FROM rag_semantic_index_queue
GROUP BY status
ORDER BY status;

\echo '[VERIFY] Agent v2 DB 검증 완료'
