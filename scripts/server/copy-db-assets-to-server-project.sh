#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
DESTINATION="${1:-}"

if [[ -z "$DESTINATION" ]]; then
  echo '사용법: ./scripts/server/copy-db-assets-to-server-project.sh /path/to/server-project' >&2
  exit 2
fi
mkdir -p "$DESTINATION"
DESTINATION="$(cd -- "$DESTINATION" && pwd)"

[[ -f "$SOURCE_ROOT/db/schema_patch_20260714_agent_v2_semantic_memory.sql" ]] \
  || { echo 'ERROR: Agent v2 DB 패치가 없습니다.' >&2; exit 3; }

if [[ "$SOURCE_ROOT" == "$DESTINATION" ]]; then
  echo "이미 서버 프로젝트 루트에서 실행 중입니다: $DESTINATION"
  exit 0
fi

STAMP="$(date '+%Y%m%d_%H%M%S')"
BACKUP_ROOT="$DESTINATION/.hiddenauto-db-files-backup/$STAMP"
mkdir -p "$BACKUP_ROOT"

backup_and_copy_dir() {
  local relative="$1"
  local src="$SOURCE_ROOT/$relative" dst="$DESTINATION/$relative"
  [[ -d "$src" ]] || return 0
  if [[ -d "$dst" ]]; then
    mkdir -p "$BACKUP_ROOT/$relative"
    cp -a "$dst/." "$BACKUP_ROOT/$relative/"
  fi
  mkdir -p "$dst"
  cp -a "$src/." "$dst/"
  echo "COPIED $relative/"
}

backup_and_copy_file() {
  local relative="$1"
  local src="$SOURCE_ROOT/$relative" dst="$DESTINATION/$relative"
  [[ -f "$src" ]] || return 0
  if [[ -f "$dst" ]]; then
    mkdir -p "$(dirname -- "$BACKUP_ROOT/$relative")"
    cp -a "$dst" "$BACKUP_ROOT/$relative"
  fi
  mkdir -p "$(dirname -- "$dst")"
  cp -a "$src" "$dst"
  echo "COPIED $relative"
}

backup_and_copy_dir db
backup_and_copy_dir scripts/server
for file in docker-compose.yml docker-compose.local.yml docker-compose.server-dev.yml docker-compose.server-prod.yml \
            .env.example .env.local.example .env.server-dev.example .env.server-prod.example; do
  backup_and_copy_file "$file"
done

find "$DESTINATION/db" "$DESTINATION/scripts/server" -type f -name '*.sh' -exec chmod +x {} +

echo "DB/Compose/서버 스크립트 복사 완료: $DESTINATION"
echo "기존 파일 백업: $BACKUP_ROOT"
echo '실제 .env는 복사하지 않았습니다.'
