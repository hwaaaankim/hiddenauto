#!/usr/bin/env bash
set -euo pipefail

COMMON_SCRIPT="${RAG_INIT_COMMON_PATH:-/schema/init-rag-common.sh}"
if [[ ! -f "$COMMON_SCRIPT" ]]; then
  COMMON_SCRIPT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/init-rag-common.sh"
fi
# shellcheck source=init-rag-common.sh
source "$COMMON_SCRIPT"

: "${RAG_DEV_DB:?RAG_DEV_DB is required}"
: "${RAG_DEV_USER:?RAG_DEV_USER is required}"
: "${RAG_DEV_PASSWORD:?RAG_DEV_PASSWORD is required}"
: "${RAG_PROD_DB:?RAG_PROD_DB is required}"
: "${RAG_PROD_USER:?RAG_PROD_USER is required}"
: "${RAG_PROD_PASSWORD:?RAG_PROD_PASSWORD is required}"

if [[ "$RAG_DEV_DB" == "$RAG_PROD_DB" || "$RAG_DEV_USER" == "$RAG_PROD_USER" ]]; then
  echo "ERROR: DEV and PROD database/user names must be different." >&2
  exit 4
fi

initialize_rag_database "$RAG_DEV_DB" "$RAG_DEV_USER" "$RAG_DEV_PASSWORD"
initialize_rag_database "$RAG_PROD_DB" "$RAG_PROD_USER" "$RAG_PROD_PASSWORD"

echo "========== DEV + PROD RAG PostgreSQL initialization completed =========="
