#!/usr/bin/env bash
set -euo pipefail

RAG_SCHEMA_DIR="${RAG_SCHEMA_DIR:-/schema}"

rag_require_value() {
  local name="$1"
  local value="${2:-}"
  if [[ -z "$value" ]]; then
    echo "ERROR: required environment variable is empty: ${name}" >&2
    exit 2
  fi
}

initialize_rag_database() {
  local db_name="$1"
  local db_user="$2"
  local db_password="$3"

  rag_require_value "POSTGRES_USER" "${POSTGRES_USER:-}"
  rag_require_value "database name" "$db_name"
  rag_require_value "database user" "$db_user"
  rag_require_value "database password" "$db_password"

  echo "========== Create/refresh role and database: ${db_name} / ${db_user} =========="
  psql -X -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname postgres \
    -v dbname="${db_name}" \
    -v username="${db_user}" \
    -v password="${db_password}" <<'EOSQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'username', :'password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'username')\gexec

SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'username', :'password')\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'dbname', :'username')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'dbname')\gexec
EOSQL

  local sql_files=(
    001_schema-rag-builder.sql
    002_conversation_learning_patch.sql
    003_excel_dnd_reset_patch.sql
    004_structured_order_learning_patch.sql
    005_async_learning_job_patch.sql
    006_adaptive_knowledge_tree_patch.sql
    007_eventual_ai_reparse_patch.sql
    008_ai_interaction_asset_link_patch.sql
    009_semantic_planner_override_patch.sql
    010_pricing_rule_patch.sql
    011_semantic_orchestrator_patch.sql
    012_dialog_core_patch.sql
    013_canonical_engine_patch.sql
    014_canonical_job_quote_patch.sql
    015_sql_agent_patch.sql
    016_dynamic_nl_pricing_patch.sql
    017_gpt_final_answer_patch.sql
    018_working_memory_patch.sql
    019_gpt_agent_schema_dictionary_patch.sql
    020_db_tool_agent_patch.sql
    021_agent_scoped_views_patch.sql
    022_agent_v2_semantic_memory_patch.sql
    023_agent_v3_production_hardening_patch.sql
    024_agent_v4_gpt_centric_mcp_tools_patch.sql
  )

  echo "========== Apply RAG schema 001~024 to ${db_name} =========="
  local sql_name sql_file
  for sql_name in "${sql_files[@]}"; do
    sql_file="${RAG_SCHEMA_DIR}/${sql_name}"
    if [[ ! -f "$sql_file" ]]; then
      echo "ERROR: SQL file not found: ${sql_file}" >&2
      exit 3
    fi
    echo "----- ${db_name}: ${sql_name} -----"
    psql -X -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${db_name}" -f "$sql_file"
  done

  echo "========== Grant application privileges: ${db_name} -> ${db_user} =========="
  psql -X -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${db_name}" \
    -v dbname="${db_name}" -v username="${db_user}" <<'EOSQL'
GRANT CONNECT ON DATABASE :"dbname" TO :"username";
GRANT USAGE, CREATE ON SCHEMA public TO :"username";
GRANT USAGE ON SCHEMA rag_agent_view TO :"username";
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO :"username";
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO :"username";
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO :"username";
GRANT SELECT ON ALL TABLES IN SCHEMA rag_agent_view TO :"username";

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO :"username";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO :"username";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON FUNCTIONS TO :"username";
ALTER DEFAULT PRIVILEGES IN SCHEMA rag_agent_view GRANT SELECT ON TABLES TO :"username";
EOSQL

  echo "========== Completed: ${db_name} =========="
}
