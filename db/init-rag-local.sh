#!/usr/bin/env bash
set -euo pipefail
COMMON_SCRIPT="${RAG_INIT_COMMON_PATH:-/schema/init-rag-common.sh}"
if [[ ! -f "$COMMON_SCRIPT" ]]; then
  COMMON_SCRIPT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/init-rag-common.sh"
fi
# shellcheck source=init-rag-common.sh
source "$COMMON_SCRIPT"

: "${RAG_LOCAL_DB:=ax_rag_local}"
: "${RAG_LOCAL_USER:=ax_rag_local_user}"
: "${RAG_LOCAL_PASSWORD:?RAG_LOCAL_PASSWORD is required}"
initialize_rag_database "$RAG_LOCAL_DB" "$RAG_LOCAL_USER" "$RAG_LOCAL_PASSWORD"
echo "========== LOCAL RAG PostgreSQL initialization completed =========="
