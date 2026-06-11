#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR/knowflow-backend"
docker compose up -d

"$ROOT_DIR/scripts/dev-migrate-db.sh"

echo
echo "Infrastructure is ready:"
echo "- PostgreSQL: localhost:5432"
echo "- Redis:      localhost:6379 (password from REDIS_PASSWORD)"
echo "- MinIO:      http://localhost:9001"
