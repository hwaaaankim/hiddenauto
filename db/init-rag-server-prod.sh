#!/usr/bin/env bash
set -euo pipefail
COMMON_SCRIPT="${RAG_INIT_COMMON_PATH:-/schema/init-rag-common.sh}"
if [[ ! -f "$COMMON_SCRIPT" ]]; then
  COMMON_SCRIPT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/init-rag-common.sh"
fi
# shellcheck source=init-rag-common.sh
source "$COMMON_SCRIPT"

: "${RAG_PROD_DB:=ax_rag_prod}"
: "${RAG_PROD_USER:=ax_rag_prod_user}"
: "${RAG_PROD_PASSWORD:?RAG_PROD_PASSWORD is required}"
initialize_rag_database "$RAG_PROD_DB" "$RAG_PROD_USER" "$RAG_PROD_PASSWORD"
echo "========== SERVER PROD RAG PostgreSQL initialization completed =========="
