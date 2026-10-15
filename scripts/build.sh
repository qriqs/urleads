#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v bun >/dev/null 2>&1; then
  echo "Bun is required to build the frontend." >&2
  exit 1
fi

if ! "$ROOT_DIR/backend/mvnw" -v 2>&1 | grep -q 'Java version: 21'; then
  echo "Java 21 is required to build UrLeads." >&2
  exit 1
fi

(
  cd "$ROOT_DIR/frontend"
  bun run lint
  bun run build
)

cd "$ROOT_DIR/backend"
./mvnw --batch-mode --no-transfer-progress clean verify "$@"
