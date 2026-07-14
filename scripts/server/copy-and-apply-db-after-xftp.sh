#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'USAGE'
사용법:
  ./scripts/server/copy-and-apply-db-after-xftp.sh /path/to/server-project dev [--enqueue]
  ./scripts/server/copy-and-apply-db-after-xftp.sh /path/to/server-project prod [--enqueue] [--yes]

XFTP로 올린 ZIP 압축 해제 폴더에서 실행합니다.
1) 대상 서버 프로젝트의 기존 DB/compose/서버 스크립트를 timestamp 폴더에 백업
2) 이번 패키지의 DB/compose/서버 스크립트를 같은 상대경로로 복사
3) 대상 프로젝트의 .env 또는 RAG_ENV_FILE을 사용해 DB 백업→사전검사→022 적용→검증
실제 .env는 복사하거나 덮어쓰지 않습니다.
USAGE
}

DESTINATION="${1:-}"
TARGET="${2:-}"
[[ -n "$DESTINATION" && "$TARGET" =~ ^(dev|prod)$ ]] || { usage; exit 2; }
shift 2

"${SCRIPT_DIR}/copy-db-assets-to-server-project.sh" "$DESTINATION"
DESTINATION="$(cd -- "$DESTINATION" && pwd)"
export RAG_ENV_FILE="${RAG_ENV_FILE:-${DESTINATION}/.env}"

[[ -f "$RAG_ENV_FILE" ]] || {
  echo "ERROR: DB 접속정보 파일이 없습니다: $RAG_ENV_FILE" >&2
  echo '대상 프로젝트 .env를 유지하거나 RAG_ENV_FILE=/secure/path/.env 를 지정하십시오.' >&2
  exit 4
}

exec "${DESTINATION}/scripts/server/apply_agent_v2.sh" "$TARGET" "$@"
