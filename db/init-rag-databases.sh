#!/usr/bin/env bash
set -euo pipefail

create_user_and_db() {
  local db_name="$1"
  local db_user="$2"
  local db_password="$3"

  echo "========== Create role/database: ${db_name} / ${db_user} =========="

  psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname postgres \
    -v dbname="${db_name}" \
    -v username="${db_user}" \
    -v password="${db_password}" <<'EOSQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'username', :'password')
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'username'
)\gexec

SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'username', :'password')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'dbname', :'username')
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = :'dbname'
)\gexec
EOSQL
}

apply_schema() {
  local db_name="$1"
  local db_user="$2"

  echo "========== Apply RAG schema to ${db_name} =========="

  local sql_files=(
    /schema/001_schema-rag-builder.sql
    /schema/002_conversation_learning_patch.sql
    /schema/003_excel_dnd_reset_patch.sql
    /schema/004_structured_order_learning_patch.sql
    /schema/005_async_learning_job_patch.sql
    /schema/006_adaptive_knowledge_tree_patch.sql
    /schema/007_eventual_ai_reparse_patch.sql
    /schema/008_ai_interaction_asset_link_patch.sql
    /schema/009_semantic_planner_override_patch.sql
    /schema/010_pricing_rule_patch.sql
    /schema/011_semantic_orchestrator_patch.sql
    /schema/012_dialog_core_patch.sql
    /schema/013_canonical_engine_patch.sql
    /schema/014_canonical_job_quote_patch.sql
    /schema/015_sql_agent_patch.sql
    /schema/016_dynamic_nl_pricing_patch.sql
    /schema/017_gpt_final_answer_patch.sql
    /schema/018_working_memory_patch.sql
    /schema/019_gpt_agent_schema_dictionary_patch.sql
    /schema/020_db_tool_agent_patch.sql
    /schema/021_agent_scoped_views_patch.sql
  )

  for sql_file in "${sql_files[@]}"; do
    if [[ ! -f "${sql_file}" ]]; then
      echo "ERROR: SQL file not found: ${sql_file}" >&2
      exit 1
    fi
    echo "----- ${db_name}: ${sql_file} -----"
    psql -X -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${db_name}" -f "${sql_file}"
  done

  echo "========== Grant privileges on ${db_name} to ${db_user} =========="

  psql -X -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${db_name}" \
    -v dbname="${db_name}" -v username="${db_user}" <<'EOSQL'
GRANT CONNECT ON DATABASE :"dbname" TO :"username";
GRANT USAGE, CREATE ON SCHEMA public TO :"username";
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO :"username";
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO :"username";
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO :"username";

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT ALL PRIVILEGES ON TABLES TO :"username";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT ALL PRIVILEGES ON SEQUENCES TO :"username";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT ALL PRIVILEGES ON FUNCTIONS TO :"username";
EOSQL
}

create_user_and_db "${RAG_DEV_DB}" "${RAG_DEV_USER}" "${RAG_DEV_PASSWORD}"
create_user_and_db "${RAG_PROD_DB}" "${RAG_PROD_USER}" "${RAG_PROD_PASSWORD}"

apply_schema "${RAG_DEV_DB}" "${RAG_DEV_USER}"
apply_schema "${RAG_PROD_DB}" "${RAG_PROD_USER}"

echo "========== RAG PostgreSQL init completed =========="
