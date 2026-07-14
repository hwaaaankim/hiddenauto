#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib_db.sh
source "${SCRIPT_DIR}/lib_db.sh"
load_env_file
check_mode
target="${1:-}"
[[ "$target" =~ ^(local|dev|prod)$ ]] || die '사용법: ./scripts/server/enqueue_semantic_rebuild.sh local|dev|prod'
if [[ "$target" == prod ]]; then confirm_prod false; fi
db="$(db_for_target "$target")"
log "[$target] 전체 프로젝트/버전 semantic queue 등록: $db"
run_psql_file "$db" "${PROJECT_ROOT}/db/enqueue_semantic_rebuild_all.sql"
