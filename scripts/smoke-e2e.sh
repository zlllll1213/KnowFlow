#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8081}"
RAG_URL="${RAG_URL:-http://localhost:8090}"
QUESTION="${QUESTION:-KnowFlow 是什么？}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-90}"

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing command: $1" >&2
    exit 2
  fi
}

json_get() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

payload = json.loads(sys.argv[1])
path = sys.argv[2].split(".")
value = payload
for part in path:
    if part.isdigit():
        value = value[int(part)]
    else:
        value = value[part]
print(value)
PY
}

wait_http() {
  local url="$1"
  local name="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))

  until curl -fsS "$url" >/dev/null 2>&1; do
    if (( SECONDS >= deadline )); then
      echo "$name not ready: $url" >&2
      exit 1
    fi
    sleep 2
  done
  echo "$name ready"
}

need_cmd curl
need_cmd python3

echo "Checking services..."
wait_http "$BACKEND_URL/api/health" "backend"
wait_http "$RAG_URL/health" "rag"

suffix="$(date +%s)"
username="smoke_$suffix"
password="Smoke123456"
email="$username@example.com"

echo "Registering $username..."
curl -fsS -X POST "$BACKEND_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$username\",\"password\":\"$password\",\"email\":\"$email\"}" >/dev/null

login_json="$(curl -fsS -X POST "$BACKEND_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$username\",\"password\":\"$password\"}")"
token="$(json_get "$login_json" "data.token")"

kb_json="$(curl -fsS -X POST "$BACKEND_URL/api/kb" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $token" \
  -d "{\"name\":\"Smoke KB $suffix\",\"description\":\"End-to-end smoke test\"}")"
kb_id="$(json_get "$kb_json" "data.id")"
echo "Created knowledge base: $kb_id"

tmpfile="$(mktemp /tmp/knowflow-smoke-XXXXXX.txt)"
trap 'rm -f "$tmpfile"' EXIT
cat > "$tmpfile" <<'EOF'
KnowFlow 是一个智能知识库问答平台。
它包含 Spring Boot 后端、Vue 前端、Python 文档解析 Worker 和 Go RAG 服务。
这个文件用于端到端 smoke test。
EOF

doc_json="$(curl -fsS -X POST "$BACKEND_URL/api/document/upload" \
  -H "Authorization: Bearer $token" \
  -F "kbId=$kb_id" \
  -F "file=@$tmpfile;filename=smoke.txt")"
doc_id="$(json_get "$doc_json" "data.id")"
echo "Uploaded document: $doc_id"

deadline=$((SECONDS + TIMEOUT_SECONDS))
status="UNKNOWN"
while (( SECONDS < deadline )); do
  status_json="$(curl -fsS "$BACKEND_URL/api/document/$doc_id/status" \
    -H "Authorization: Bearer $token")"
  status="$(json_get "$status_json" "data")"
  echo "Document status: $status"

  if [[ "$status" == "DONE" ]]; then
    break
  fi
  if [[ "$status" == "FAILED" ]]; then
    echo "document parsing failed" >&2
    exit 1
  fi
  sleep 3
done

if [[ "$status" != "DONE" ]]; then
  echo "document did not finish before timeout; is the Python Worker running?" >&2
  exit 1
fi

session_json="$(curl -fsS -X POST "$BACKEND_URL/api/chat/session" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $token" \
  -d "{\"kbId\":$kb_id,\"title\":\"Smoke Chat\"}")"
session_id="$(json_get "$session_json" "data.id")"
echo "Created chat session: $session_id"

stream_file="$(mktemp /tmp/knowflow-stream-XXXXXX.txt)"
trap 'rm -f "$tmpfile" "$stream_file"' EXIT
curl -fsS -N --max-time 30 -X POST "$BACKEND_URL/api/chat/ask/stream" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $token" \
  -d "{\"kbId\":$kb_id,\"sessionId\":$session_id,\"question\":\"$QUESTION\"}" \
  > "$stream_file"

if ! grep -q '^event:done' "$stream_file"; then
  echo "stream did not finish with done event" >&2
  cat "$stream_file" >&2
  exit 1
fi

if ! grep -q '^event:token' "$stream_file"; then
  echo "stream did not include token events" >&2
  cat "$stream_file" >&2
  exit 1
fi

echo "Smoke test passed."
