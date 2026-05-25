#!/usr/bin/env bash
set -euo pipefail

CONTAINER="${POSTGRES_CONTAINER:-knowflow-postgres}"
DB_USER="${POSTGRES_USER:-knowflow}"
DB_NAME="${POSTGRES_DB:-knowflow}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "PostgreSQL container is not running: $CONTAINER" >&2
  echo "Start it with: cd knowflow-backend && docker compose up -d" >&2
  exit 1
fi

docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" \
  < "$ROOT_DIR/knowflow-backend/sql/migrate-dev.sql"

echo "Database migration finished."
