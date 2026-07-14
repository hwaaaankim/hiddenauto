#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib_db.sh
source "${SCRIPT_DIR}/lib_db.sh"
load_env_file
check_mode
target="${1:-}"
[[ "$target" =~ ^(local|dev|prod)$ ]] || die '사용법: ./scripts/server/verify_agent_v2.sh local|dev|prod'
db="$(db_for_target "$target")"
log "[$target] Agent v2 검증: $db"
run_psql_file "$db" "${PROJECT_ROOT}/db/verify_agent_v2.sql"
