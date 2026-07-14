#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

usage() {
  cat <<'USAGE'
사용법:
  ./scripts/server/copy-complete-package-to-project.sh /path/to/existing-project

현재 압축 해제 폴더의 java/static/templates/db/scripts/docs와 루트 compose/env 예제 파일을
기존 프로젝트에 같은 상대경로로 병합 복사합니다. 기존 파일은 먼저
<대상>/.hiddenauto-replace-backup/<timestamp>/ 아래에 백업합니다.
실제 .env는 복사하거나 덮어쓰지 않으며, 대상에만 있던 파일도 삭제하지 않습니다.
USAGE
}

DESTINATION="${1:-}"
[[ -n "$DESTINATION" ]] || { usage; exit 2; }
mkdir -p "$DESTINATION"
DESTINATION="$(cd -- "$DESTINATION" && pwd)"

[[ -f "$SOURCE_ROOT/java/service/RagSqlAgentService.java" ]] \
  || { echo 'ERROR: 올바른 전체 패키지 루트가 아닙니다.' >&2; exit 3; }
[[ -f "$SOURCE_ROOT/db/schema_patch_20260714_agent_v2_semantic_memory.sql" ]] \
  || { echo 'ERROR: Agent v2 DB 패치가 없습니다.' >&2; exit 3; }

if [[ "$SOURCE_ROOT" == "$DESTINATION" ]]; then
  echo "이미 대상 프로젝트 루트에서 실행 중입니다: $DESTINATION"
  exit 0
fi

STAMP="$(date '+%Y%m%d_%H%M%S')"
BACKUP_ROOT="$DESTINATION/.hiddenauto-replace-backup/$STAMP"
mkdir -p "$BACKUP_ROOT"

copy_directory() {
  local name="$1"
  local src="$SOURCE_ROOT/$name"
  local dst="$DESTINATION/$name"
  [[ -d "$src" ]] || return 0
  if [[ -e "$dst" ]]; then
    mkdir -p "$BACKUP_ROOT/$name"
    cp -a "$dst/." "$BACKUP_ROOT/$name/"
  fi
  mkdir -p "$dst"
  cp -a "$src/." "$dst/"
  printf 'COPIED DIR  %s/\n' "$name"
}

copy_file() {
  local name="$1"
  local src="$SOURCE_ROOT/$name"
  local dst="$DESTINATION/$name"
  [[ -f "$src" ]] || return 0
  if [[ -e "$dst" ]]; then
    mkdir -p "$(dirname -- "$BACKUP_ROOT/$name")"
    cp -a "$dst" "$BACKUP_ROOT/$name"
  fi
  mkdir -p "$(dirname -- "$dst")"
  cp -a "$src" "$dst"
  printf 'COPIED FILE %s\n' "$name"
}

for directory in java static templates db scripts docs; do
  copy_directory "$directory"
done
for file in \
  docker-compose.yml docker-compose.local.yml docker-compose.server-dev.yml docker-compose.server-prod.yml \
  .env.example .env.local.example .env.server-dev.example .env.server-prod.example \
  .gitignore README.md APPLY_FIRST_KO.txt COPY_TARGET_MAP_KO.txt CHANGED_FILES_KO.txt PACKAGE_CONTENTS.txt VALIDATION_SUMMARY.txt MANIFEST.sha256; do
  copy_file "$file"
done

find "$DESTINATION/scripts" "$DESTINATION/db" -type f -name '*.sh' -exec chmod +x {} + 2>/dev/null || true

echo "완료: $DESTINATION"
echo "기존 파일 백업: $BACKUP_ROOT"
echo '주의: 실제 .env와 대상에만 있던 파일은 건드리지 않았습니다. git status / git diff로 확인하십시오.'
