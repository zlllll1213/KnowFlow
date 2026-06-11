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
cookie_jar="$(mktemp /tmp/knowflow-cookies-XXXXXX.txt)"
trap 'rm -f "$cookie_jar"' EXIT

refresh_csrf() {
  curl -fsS -c "$cookie_jar" -b "$cookie_jar" "$BACKEND_URL/api/auth/csrf" >/dev/null
  awk '$6 == "XSRF-TOKEN" { token = $7 } END { print token }' "$cookie_jar"
}

csrf_token="$(refresh_csrf)"

echo "Registering $username..."
curl -fsS -X POST "$BACKEND_URL/api/auth/register" \
  -b "$cookie_jar" -c "$cookie_jar" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $csrf_token" \
  -d "{\"username\":\"$username\",\"password\":\"$password\",\"email\":\"$email\"}" >/dev/null

login_json="$(curl -fsS -X POST "$BACKEND_URL/api/auth/login" \
  -b "$cookie_jar" -c "$cookie_jar" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $csrf_token" \
  -d "{\"username\":\"$username\",\"password\":\"$password\"}")"

kb_json="$(curl -fsS -X POST "$BACKEND_URL/api/kb" \
  -b "$cookie_jar" -c "$cookie_jar" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $csrf_token" \
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
  -b "$cookie_jar" -c "$cookie_jar" \
  -H "X-XSRF-TOKEN: $csrf_token" \
  -F "kbId=$kb_id" \
  -F "file=@$tmpfile;filename=smoke.txt")"
doc_id="$(json_get "$doc_json" "data.id")"
echo "Uploaded document: $doc_id"

deadline=$((SECONDS + TIMEOUT_SECONDS))
status="UNKNOWN"
while (( SECONDS < deadline )); do
  status_json="$(curl -fsS "$BACKEND_URL/api/document/$doc_id/status" \
    -b "$cookie_jar" -c "$cookie_jar")"
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
  -b "$cookie_jar" -c "$cookie_jar" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $csrf_token" \
  -d "{\"kbId\":$kb_id,\"title\":\"Smoke Chat\"}")"
session_id="$(json_get "$session_json" "data.id")"
echo "Created chat session: $session_id"

stream_file="$(mktemp /tmp/knowflow-stream-XXXXXX.txt)"
trap 'rm -f "$cookie_jar" "$tmpfile" "$stream_file"' EXIT
curl -fsS -N --max-time 30 -X POST "$BACKEND_URL/api/chat/ask/stream" \
  -b "$cookie_jar" -c "$cookie_jar" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $csrf_token" \
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

if ! grep -q '^event:sources' "$stream_file"; then
  echo "stream did not include sources event" >&2
  cat "$stream_file" >&2
  exit 1
fi

python3 - "$stream_file" <<'PY'
import json
import sys

path = sys.argv[1]
events = []
event = "message"
data_lines = []
for raw in open(path, encoding="utf-8"):
    line = raw.rstrip("\n")
    if not line:
        if data_lines:
            events.append((event, "\n".join(data_lines)))
        event = "message"
        data_lines = []
        continue
    if line.startswith("event:"):
        event = line.split(":", 1)[1].strip()
    elif line.startswith("data:"):
        data_lines.append(line.split(":", 1)[1].strip())

sources_payloads = [json.loads(data) for name, data in events if name == "sources"]
if not sources_payloads:
    raise SystemExit("missing sources payload")

sources = sources_payloads[-1]
if isinstance(sources, dict):
    sources = sources.get("sources", [])
if not sources:
    raise SystemExit("sources payload is empty")
for source in sources:
    if source.get("fileName") == "mock-notice.txt":
        raise SystemExit("sources contain mock-notice.txt")
    for key in ("chunkId", "documentId", "fileName", "content"):
        if source.get(key) in (None, "", 0):
            raise SystemExit(f"source missing real {key}: {source}")

# 验证 fallback 未启用：回答中不应出现 mock/fallback/模拟 字样
all_answers = []
for name, data in events:
    if name in ("token", "done"):
        try:
            payload = json.loads(data)
            content = payload.get("content") or payload.get("answer") or ""
            if isinstance(content, str):
                all_answers.append(content)
        except (json.JSONDecodeError, TypeError):
            pass
full_answer = " ".join(all_answers).lower()
for bad in ("mock", "fallback", "模拟", "mock-notice"):
    if bad in full_answer:
        raise SystemExit(f"answer contains forbidden fallback indicator: {bad}")
PY

echo "RAG stream assertions passed."

# ============================================================
# Agent 模式流式问答
# ============================================================
echo "Testing Agent stream..."

agent_session_json="$(curl -fsS -X POST "$BACKEND_URL/api/chat/session" \
  -b "$cookie_jar" -c "$cookie_jar" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $csrf_token" \
  -d "{\"kbId\":$kb_id,\"title\":\"Agent Smoke Chat\"}")"
agent_session_id="$(json_get "$agent_session_json" "data.id")"
echo "Created agent session: $agent_session_id"

agent_stream_file="$(mktemp /tmp/knowflow-agent-stream-XXXXXX.txt)"
trap 'rm -f "$cookie_jar" "$tmpfile" "$stream_file" "$agent_stream_file"' EXIT
curl -fsS -N --max-time 30 -X POST "$BACKEND_URL/api/agent/ask/stream" \
  -b "$cookie_jar" -c "$cookie_jar" \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $csrf_token" \
  -d "{\"kbId\":$kb_id,\"sessionId\":$agent_session_id,\"question\":\"$QUESTION\"}" \
  > "$agent_stream_file"

if ! grep -q '^event:done' "$agent_stream_file"; then
  echo "agent stream did not finish with done event" >&2
  cat "$agent_stream_file" >&2
  exit 1
fi

if ! grep -q '^event:token' "$agent_stream_file"; then
  echo "agent stream did not include token events" >&2
  cat "$agent_stream_file" >&2
  exit 1
fi

if ! grep -q '^event:sources' "$agent_stream_file"; then
  echo "agent stream did not include sources event" >&2
  cat "$agent_stream_file" >&2
  exit 1
fi

if ! grep -q '^event:meta' "$agent_stream_file"; then
  echo "agent stream did not include meta event" >&2
  cat "$agent_stream_file" >&2
  exit 1
fi

python3 - "$agent_stream_file" <<'PY'
import json
import sys

path = sys.argv[1]
events = []
event = "message"
data_lines = []
for raw in open(path, encoding="utf-8"):
    line = raw.rstrip("\n")
    if not line:
        if data_lines:
            events.append((event, "\n".join(data_lines)))
        event = "message"
        data_lines = []
        continue
    if line.startswith("event:"):
        event = line.split(":", 1)[1].strip()
    elif line.startswith("data:"):
        data_lines.append(line.split(":", 1)[1].strip())

# 验证 meta 事件包含 confidence / trace
meta_payloads = [json.loads(data) for name, data in events if name == "meta"]
if not meta_payloads:
    raise SystemExit("missing agent meta payload")

meta = meta_payloads[-1]
confidence = meta.get("confidence")
if confidence is None:
    raise SystemExit("meta missing confidence")
if not isinstance(confidence, (int, float)):
    raise SystemExit(f"confidence is not numeric: {type(confidence).__name__}")
if not (0.0 <= float(confidence) <= 1.0):
    raise SystemExit(f"confidence out of range: {confidence}")

trace = meta.get("trace")
if not trace or not isinstance(trace, list) or len(trace) == 0:
    raise SystemExit("meta missing trace steps")
seen_steps = set()
for step in trace:
    if not isinstance(step, dict):
        raise SystemExit(f"trace step is not dict: {step}")
    if "step" not in step or "detail" not in step:
        raise SystemExit(f"trace step missing step/detail: {step}")
    seen_steps.add(step.get("step"))
required_steps = {"router", "retriever", "answer", "citation_guard"}
missing_steps = sorted(required_steps - seen_steps)
if missing_steps:
    raise SystemExit(f"agent trace missing required steps: {missing_steps}")

# 验证 sources
sources_payloads = [json.loads(data) for name, data in events if name == "sources"]
if not sources_payloads:
    raise SystemExit("agent missing sources payload")

sources = sources_payloads[-1]
if isinstance(sources, dict):
    sources = sources.get("sources", [])
if not sources:
    raise SystemExit("agent sources payload is empty")
for source in sources:
    if source.get("fileName") == "mock-notice.txt":
        raise SystemExit("agent sources contain mock-notice.txt")
    for key in ("chunkId", "documentId", "fileName", "content"):
        if source.get(key) in (None, "", 0):
            raise SystemExit(f"agent source missing real {key}: {source}")

# 验证 fallback 未启用
all_answers = []
for name, data in events:
    if name in ("token", "done"):
        try:
            payload = json.loads(data)
            content = payload.get("content") or payload.get("answer") or ""
            if isinstance(content, str):
                all_answers.append(content)
        except (json.JSONDecodeError, TypeError):
            pass
full_answer = " ".join(all_answers).lower()
for bad in ("mock", "fallback", "模拟", "mock-notice"):
    if bad in full_answer:
        raise SystemExit(f"agent answer contains forbidden fallback indicator: {bad}")
PY

echo "Smoke test passed."
