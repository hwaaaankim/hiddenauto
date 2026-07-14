#!/usr/bin/env bash
set -euo pipefail
COMMON_SCRIPT="${RAG_INIT_COMMON_PATH:-/schema/init-rag-common.sh}"
if [[ ! -f "$COMMON_SCRIPT" ]]; then
  COMMON_SCRIPT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/init-rag-common.sh"
fi
# shellcheck source=init-rag-common.sh
source "$COMMON_SCRIPT"

: "${RAG_DEV_DB:=ax_rag_dev}"
: "${RAG_DEV_USER:=ax_rag_dev_user}"
: "${RAG_DEV_PASSWORD:?RAG_DEV_PASSWORD is required}"
initialize_rag_database "$RAG_DEV_DB" "$RAG_DEV_USER" "$RAG_DEV_PASSWORD"
echo "========== SERVER DEV RAG PostgreSQL initialization completed =========="
