#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${1:-${PROJECT_ROOT}/.env.server-prod}"
[[ -f "$ENV_FILE" ]] || { echo "ERROR: env file not found: $ENV_FILE" >&2; exit 2; }
read -r -p "새 PROD DB 컨테이너를 시작합니다. START-PROD를 입력하십시오: " answer
[[ "$answer" == START-PROD ]] || { echo 'Cancelled.'; exit 4; }
cd "$PROJECT_ROOT"
docker compose --env-file "$ENV_FILE" -f docker-compose.server-prod.yml up -d
