#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
TARGET_ROOT="${1:-}"
[[ -n "$TARGET_ROOT" ]] || { echo "사용법: ./scripts/server/copy-db-deployment-files.sh /target/project/root [--include-compose]" >&2; exit 2; }
shift || true
INCLUDE_COMPOSE=false
[[ "${1:-}" == "--include-compose" ]] && INCLUDE_COMPOSE=true
mkdir -p "$TARGET_ROOT/db" "$TARGET_ROOT/scripts/server"
cp -a "$PROJECT_ROOT/db/." "$TARGET_ROOT/db/"
cp -a "$PROJECT_ROOT/scripts/server/." "$TARGET_ROOT/scripts/server/"
if [[ "$INCLUDE_COMPOSE" == true ]]; then
  cp -a "$PROJECT_ROOT"/docker-compose*.yml "$TARGET_ROOT/"
fi
printf 'Copied DB deployment files to: %s\n' "$TARGET_ROOT"
printf 'Next: cd %q && chmod +x db/*.sh scripts/server/*.sh && ./scripts/server/apply-db-dev.sh\n' "$TARGET_ROOT"
