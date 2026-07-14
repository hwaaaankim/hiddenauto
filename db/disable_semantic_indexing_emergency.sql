\set ON_ERROR_STOP on
\echo '[EMERGENCY] semantic source trigger 비활성화'
DO $$
DECLARE
    r record;
BEGIN
    FOR r IN
        SELECT n.nspname AS schema_name, c.relname AS table_name, t.tgname AS trigger_name
          FROM pg_trigger t
          JOIN pg_class c ON c.oid = t.tgrelid
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE NOT t.tgisinternal
           AND n.nspname = 'public'
           AND t.tgname LIKE 'trg_semantic_%'
    LOOP
        EXECUTE format('ALTER TABLE %I.%I DISABLE TRIGGER %I', r.schema_name, r.table_name, r.trigger_name);
        RAISE NOTICE 'disabled %.% / %', r.schema_name, r.table_name, r.trigger_name;
    END LOOP;
END
$$;

UPDATE rag_semantic_index_queue
   SET available_at = 'infinity'::timestamptz,
       updated_at = now(),
       result_note = 'EMERGENCY_PAUSED:disable_semantic_indexing_emergency.sql'
 WHERE status IN ('PENDING','ERROR');
