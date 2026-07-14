\set ON_ERROR_STOP on
\echo '[SEMANTIC] 전체 프로젝트/버전 재색인 queue 등록'
DO $$
DECLARE
    r record;
    affected integer;
    total_affected bigint := 0;
BEGIN
    IF to_regprocedure('public.rag_semantic_enqueue_scope(uuid,uuid,jsonb)') IS NULL THEN
        RAISE EXCEPTION 'rag_semantic_enqueue_scope 함수가 없습니다. 022 패치를 먼저 적용하십시오.';
    END IF;

    FOR r IN
        SELECT p.id AS project_id, v.id AS version_id
          FROM rag_project p
          JOIN rag_project_version v ON v.project_id = p.id
         ORDER BY p.created_at NULLS LAST, v.version_no NULLS LAST, v.created_at NULLS LAST
    LOOP
        SELECT rag_semantic_enqueue_scope(r.project_id, r.version_id, '[]'::jsonb)
          INTO affected;
        total_affected := total_affected + COALESCE(affected, 0);
        RAISE NOTICE 'project=% version=% queued=%', r.project_id, r.version_id, affected;
    END LOOP;
    RAISE NOTICE 'total queued/upserted=%', total_affected;
END
$$;

SELECT status, COUNT(*) AS queue_count
  FROM rag_semantic_index_queue
 GROUP BY status
 ORDER BY status;
