#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

usage() {
  cat <<'USAGE'
사용법:
  ./scripts/local/copy-to-spring-project.sh /path/to/existing-spring-project

ZIP의 업로드 원본 구조를 다음 기존 Spring 경로로 복사합니다.
  java/*      -> src/main/java/com/dev/HiddenBATHAuto/rag/
  static/*    -> src/main/resources/static/
  templates/* -> src/main/resources/templates/
  db/scripts/docs/docker-compose*.yml -> 프로젝트 루트의 같은 이름

기존 파일은 .hiddenauto-replace-backup/<timestamp>/에 먼저 백업합니다.
실제 .env와 대상에만 있던 파일은 삭제하거나 덮어쓰지 않습니다.
USAGE
}

DESTINATION="${1:-}"
[[ -n "$DESTINATION" ]] || { usage; exit 2; }
mkdir -p "$DESTINATION"
DESTINATION="$(cd -- "$DESTINATION" && pwd)"
[[ "$SOURCE_ROOT" != "$DESTINATION" ]] || {
  echo 'ERROR: 압축 해제 패키지 폴더와 기존 Spring 프로젝트 폴더는 서로 달라야 합니다.' >&2
  exit 3
}
[[ -f "$SOURCE_ROOT/java/service/RagSqlAgentService.java" ]] || {
  echo 'ERROR: 올바른 전체 패키지 루트가 아닙니다.' >&2
  exit 4
}

STAMP="$(date '+%Y%m%d_%H%M%S')"
BACKUP_ROOT="$DESTINATION/.hiddenauto-replace-backup/$STAMP"
mkdir -p "$BACKUP_ROOT"

backup_dir() {
  local target="$1" relative="$2"
  if [[ -d "$target" ]]; then
    mkdir -p "$BACKUP_ROOT/$relative"
    cp -a "$target/." "$BACKUP_ROOT/$relative/"
  fi
}

copy_dir() {
  local source="$1" target="$2" relative="$3"
  [[ -d "$source" ]] || return 0
  backup_dir "$target" "$relative"
  mkdir -p "$target"
  cp -a "$source/." "$target/"
  printf 'COPIED DIR  %s -> %s\n' "$source" "$target"
}

copy_file() {
  local relative="$1"
  local source="$SOURCE_ROOT/$relative"
  local target="$DESTINATION/$relative"
  [[ -f "$source" ]] || return 0
  if [[ -f "$target" ]]; then
    mkdir -p "$(dirname -- "$BACKUP_ROOT/$relative")"
    cp -a "$target" "$BACKUP_ROOT/$relative"
  fi
  mkdir -p "$(dirname -- "$target")"
  cp -a "$source" "$target"
  printf 'COPIED FILE %s\n' "$relative"
}

copy_dir "$SOURCE_ROOT/java" \
  "$DESTINATION/src/main/java/com/dev/HiddenBATHAuto/rag" \
  "src/main/java/com/dev/HiddenBATHAuto/rag"
copy_dir "$SOURCE_ROOT/static" "$DESTINATION/src/main/resources/static" "src/main/resources/static"
copy_dir "$SOURCE_ROOT/templates" "$DESTINATION/src/main/resources/templates" "src/main/resources/templates"
copy_dir "$SOURCE_ROOT/db" "$DESTINATION/db" "db"
copy_dir "$SOURCE_ROOT/scripts" "$DESTINATION/scripts" "scripts"
copy_dir "$SOURCE_ROOT/docs" "$DESTINATION/docs" "docs"

for file in \
  docker-compose.yml docker-compose.local.yml docker-compose.server-dev.yml docker-compose.server-prod.yml \
  .env.example .env.local.example .env.server-dev.example .env.server-prod.example \
  .gitignore README.md APPLY_FIRST_KO.txt COPY_TARGET_MAP_KO.txt CHANGED_FILES_KO.txt PACKAGE_CONTENTS.txt VALIDATION_SUMMARY.txt MANIFEST.sha256; do
  copy_file "$file"
done

find "$DESTINATION/scripts" "$DESTINATION/db" -type f -name '*.sh' -exec chmod +x {} + 2>/dev/null || true

echo "완료: $DESTINATION"
echo "교체 전 파일 백업: $BACKUP_ROOT"
echo '실제 .env는 보존했습니다. git status와 git diff를 반드시 확인하십시오.'
