#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib_db.sh
source "${SCRIPT_DIR}/lib_db.sh"
load_env_file
check_mode
target="${1:-}"
[[ "$target" =~ ^(local|dev|prod)$ ]] || die '사용법: ./scripts/server/backup_db.sh local|dev|prod'
db="$(db_for_target "$target")"
timestamp="$(date '+%Y%m%d_%H%M%S')"
output="${RAG_BACKUP_DIR}/${db}_${timestamp}.dump"
log "[$target] 백업 생성: $output"
backup_db "$db" "$output"
log "완료: $output"
