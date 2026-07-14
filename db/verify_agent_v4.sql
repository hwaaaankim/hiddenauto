\set ON_ERROR_STOP on

DO $verify_v4$
DECLARE
    missing text[] := ARRAY[]::text[];
    tool_count integer;
    provenance_constraint_count integer;
BEGIN
    IF to_regclass('public.rag_agent_tool_capability') IS NULL THEN missing := array_append(missing,'rag_agent_tool_capability'); END IF;
    IF to_regclass('public.rag_agent_model_profile') IS NULL THEN missing := array_append(missing,'rag_agent_model_profile'); END IF;
    IF to_regclass('public.rag_agent_entity_resolution') IS NULL THEN missing := array_append(missing,'rag_agent_entity_resolution'); END IF;
    IF to_regclass('public.rag_agent_order_state_snapshot') IS NULL THEN missing := array_append(missing,'rag_agent_order_state_snapshot'); END IF;
    IF to_regclass('public.rag_agent_answer_provenance') IS NULL THEN missing := array_append(missing,'rag_agent_answer_provenance'); END IF;
    IF to_regclass('rag_agent_view.rag_agent_answer_provenance') IS NULL THEN missing := array_append(missing,'rag_agent_view.rag_agent_answer_provenance'); END IF;
    IF to_regclass('rag_agent_view.rag_agent_model_profile') IS NULL THEN missing := array_append(missing,'rag_agent_view.rag_agent_model_profile'); END IF;
    IF to_regclass('rag_agent_view.rag_agent_context_snapshot') IS NULL THEN missing := array_append(missing,'rag_agent_view.rag_agent_context_snapshot'); END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='rag_agent_run' AND column_name='capability_version') THEN missing := array_append(missing,'rag_agent_run.capability_version'); END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='rag_agent_run' AND column_name='response_type') THEN missing := array_append(missing,'rag_agent_run.response_type'); END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='rag_agent_run' AND column_name='answer_source') THEN missing := array_append(missing,'rag_agent_run.answer_source'); END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='rag_agent_run' AND column_name='context_compaction_count') THEN missing := array_append(missing,'rag_agent_run.context_compaction_count'); END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='rag_agent_request_plan' AND column_name='requires_entity_resolution') THEN missing := array_append(missing,'rag_agent_request_plan.requires_entity_resolution'); END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='rag_agent_request_plan' AND column_name='requires_order_validation') THEN missing := array_append(missing,'rag_agent_request_plan.requires_order_validation'); END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='rag_agent_request_plan' AND column_name='requires_impact_preview') THEN missing := array_append(missing,'rag_agent_request_plan.requires_impact_preview'); END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='rag_agent_request_plan' AND column_name='requires_conversation_memory') THEN missing := array_append(missing,'rag_agent_request_plan.requires_conversation_memory'); END IF;

    SELECT count(*) INTO tool_count
    FROM rag_agent_tool_capability
    WHERE capability_version='V4-20260714' AND active=true;
    IF tool_count <> 29 THEN
        missing := array_append(missing, 'active V4 tools=' || tool_count || ' (expected 29)');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM rag_agent_model_profile
         WHERE profile_key='DEFAULT' AND active=true
           AND chat_model='gpt-5.5'
           AND embedding_model='text-embedding-3-small'
           AND embedding_dimensions=1536
           AND gpt_only_answer=true
    ) THEN
        missing := array_append(missing,'active V4 model profile');
    END IF;

    SELECT count(*) INTO provenance_constraint_count
    FROM pg_constraint
    WHERE conrelid='rag_agent_answer_provenance'::regclass
      AND conname='ck_rag_agent_answer_provenance_source';
    IF provenance_constraint_count <> 1 THEN
        missing := array_append(missing,'GPT-only answer provenance check constraint');
    END IF;

    IF cardinality(missing) > 0 THEN
        RAISE EXCEPTION 'Agent V4 verification failed: %', array_to_string(missing, ', ');
    END IF;
END
$verify_v4$;

SELECT capability_version, category, count(*) AS tool_count
FROM rag_agent_tool_capability
WHERE active=true
GROUP BY capability_version, category
ORDER BY capability_version, category;

SELECT 'Agent V4 DB verification passed' AS result,
       count(*) FILTER (WHERE capability_version='V4-20260714' AND active=true) AS active_v4_tools
FROM rag_agent_tool_capability;

SELECT profile_key, capability_version, chat_model, embedding_model, embedding_dimensions, gpt_only_answer
FROM rag_agent_model_profile
WHERE active=true;
