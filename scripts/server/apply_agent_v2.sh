#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib_db.sh
source "${SCRIPT_DIR}/lib_db.sh"
load_env_file
check_mode

usage() {
  cat <<'USAGE'
사용법: ./scripts/server/apply_agent_v2.sh local|dev|prod|all [--yes] [--enqueue]
  --yes      prod 확인 문구를 생략합니다(CI용; 신중히 사용).
  --enqueue  패치/검증 후 기존 데이터 전체를 semantic queue에 등록합니다.
환경: RAG_DB_MODE=docker|direct, RAG_ENV_FILE, RAG_POSTGRES_CONTAINER, PGHOST/PGPORT/PGUSER/PGPASSWORD
USAGE
}

target="${1:-}"
[[ -n "$target" ]] || { usage; exit 2; }
shift || true
assume_yes=false
enqueue=false
for arg in "$@"; do
  case "$arg" in
    --yes) assume_yes=true ;;
    --enqueue) enqueue=true ;;
    *) die "알 수 없는 옵션: $arg" ;;
  esac
done
[[ "$target" =~ ^(local|dev|prod|all)$ ]] || { usage; exit 2; }

apply_one() {
  local env_name="$1" db timestamp backup
  db="$(db_for_target "$env_name")"
  if [[ "$env_name" == prod ]]; then confirm_prod "$assume_yes"; fi
  timestamp="$(date '+%Y%m%d_%H%M%S')"
  backup="${RAG_BACKUP_DIR}/${db}_before_agent_v2_${timestamp}.dump"

  log "[$env_name] 사전 점검: $db"
  run_psql_file "$db" "${PROJECT_ROOT}/db/precheck_agent_v2.sql"
  log "[$env_name] 백업 생성: $backup"
  backup_db "$db" "$backup"
  log "[$env_name] 022 Agent v2 패치 적용"
  run_psql_file "$db" "${PROJECT_ROOT}/db/schema_patch_20260714_agent_v2_semantic_memory.sql"
  log "[$env_name] 검증"
  run_psql_file "$db" "${PROJECT_ROOT}/db/verify_agent_v2.sql"
  if [[ "$enqueue" == true ]]; then
    log "[$env_name] 기존 데이터 semantic rebuild queue 등록"
    run_psql_file "$db" "${PROJECT_ROOT}/db/enqueue_semantic_rebuild_all.sql"
  else
    log "[$env_name] 기존 데이터 enqueue는 생략했습니다. 검증 후 enqueue_semantic_rebuild.sh를 실행하십시오."
  fi
  log "[$env_name] 완료. 백업: $backup"
}

case "$target" in
  local) apply_one local ;;
  dev) apply_one dev ;;
  prod) apply_one prod ;;
  all) apply_one dev; apply_one prod ;;
esac
