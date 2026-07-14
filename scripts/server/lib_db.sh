#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

log() { printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

load_env_file() {
  local env_file="${RAG_ENV_FILE:-${PROJECT_ROOT}/.env}"
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
  : "${RAG_DB_MODE:=docker}"
  : "${RAG_POSTGRES_CONTAINER:=hiddenauto-rag-postgres}"
  : "${RAG_POSTGRES_ADMIN_USER:=ax_admin}"
  : "${RAG_LOCAL_DB:=ax_rag_local}"
  : "${RAG_DEV_DB:=ax_rag_dev}"
  : "${RAG_PROD_DB:=ax_rag_prod}"
  : "${RAG_BACKUP_DIR:=${PROJECT_ROOT}/backups}"
  : "${PGPORT:=5432}"
  export PGPASSWORD="${PGPASSWORD:-${RAG_POSTGRES_ADMIN_PASSWORD:-}}"
  mkdir -p "$RAG_BACKUP_DIR"
}

check_mode() {
  case "${RAG_DB_MODE:-}" in
    docker) command -v docker >/dev/null 2>&1 || die 'docker 명령을 찾을 수 없습니다.' ;;
    direct)
      command -v psql >/dev/null 2>&1 || die 'psql 명령을 찾을 수 없습니다.'
      command -v pg_dump >/dev/null 2>&1 || die 'pg_dump 명령을 찾을 수 없습니다.'
      : "${PGHOST:?RAG_DB_MODE=direct에서는 PGHOST가 필요합니다.}"
      : "${PGUSER:=${RAG_POSTGRES_ADMIN_USER}}"
      export PGUSER
      ;;
    *) die "RAG_DB_MODE는 docker 또는 direct여야 합니다: ${RAG_DB_MODE:-<empty>}" ;;
  esac
}

db_for_target() {
  case "$1" in
    local) printf '%s\n' "$RAG_LOCAL_DB" ;;
    dev) printf '%s\n' "$RAG_DEV_DB" ;;
    prod) printf '%s\n' "$RAG_PROD_DB" ;;
    *) die "대상은 local, dev 또는 prod여야 합니다: $1" ;;
  esac
}
confirm_prod() {
  local assume_yes="${1:-false}"
  if [[ "$assume_yes" == true || "${CONFIRM_PROD:-}" == YES ]]; then
    return 0
  fi
  local answer
  read -r -p "PROD DB에 적용합니다. APPLY-PROD를 정확히 입력하십시오: " answer
  [[ "$answer" == APPLY-PROD ]] || die '운영 적용이 취소되었습니다.'
}

run_psql_file() {
  local db="$1" file="$2"
  [[ -f "$file" ]] || die "SQL 파일이 없습니다: $file"
  if [[ "$RAG_DB_MODE" == docker ]]; then
    docker exec -i \
      -e "PGPASSWORD=${RAG_POSTGRES_ADMIN_PASSWORD:-${PGPASSWORD:-}}" \
      "$RAG_POSTGRES_CONTAINER" \
      psql -X -v ON_ERROR_STOP=1 -U "$RAG_POSTGRES_ADMIN_USER" -d "$db" < "$file"
  else
    PGPASSWORD="${PGPASSWORD:-}" psql -X -v ON_ERROR_STOP=1 -d "$db" -f "$file"
  fi
}

run_psql_command() {
  local db="$1" sql="$2"
  if [[ "$RAG_DB_MODE" == docker ]]; then
    docker exec -i \
      -e "PGPASSWORD=${RAG_POSTGRES_ADMIN_PASSWORD:-${PGPASSWORD:-}}" \
      "$RAG_POSTGRES_CONTAINER" \
      psql -X -v ON_ERROR_STOP=1 -U "$RAG_POSTGRES_ADMIN_USER" -d "$db" -c "$sql"
  else
    PGPASSWORD="${PGPASSWORD:-}" psql -X -v ON_ERROR_STOP=1 -d "$db" -c "$sql"
  fi
}

backup_db() {
  local db="$1" output="$2"
  mkdir -p "$(dirname -- "$output")"
  if [[ "$RAG_DB_MODE" == docker ]]; then
    docker exec \
      -e "PGPASSWORD=${RAG_POSTGRES_ADMIN_PASSWORD:-${PGPASSWORD:-}}" \
      "$RAG_POSTGRES_CONTAINER" \
      pg_dump -Fc -U "$RAG_POSTGRES_ADMIN_USER" -d "$db" > "$output"
  else
    PGPASSWORD="${PGPASSWORD:-}" pg_dump -Fc -d "$db" -f "$output"
  fi
  [[ -s "$output" ]] || die "백업 파일 생성에 실패했습니다: $output"
}
