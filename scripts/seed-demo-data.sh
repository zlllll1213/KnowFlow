#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8081}"
USERNAME="${DEMO_USERNAME:-demo}"
PASSWORD="${DEMO_PASSWORD:-Demo123456}"
EMAIL="${DEMO_EMAIL:-demo@example.com}"

json_get() {
  python3 - "$1" "$2" <<'PY'
import json
import sys
payload = json.loads(sys.argv[1])
value = payload
for part in sys.argv[2].split("."):
    value = value[int(part)] if part.isdigit() else value[part]
print(value)
PY
}

curl -fsS -X POST "$BACKEND_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"email\":\"$EMAIL\"}" >/dev/null || true

login_json="$(curl -fsS -X POST "$BACKEND_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")"
token="$(json_get "$login_json" "data.token")"

kb_json="$(curl -fsS -X POST "$BACKEND_URL/api/kb" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $token" \
  -d '{"name":"KnowFlow Demo KB","description":"用于演示 RAG + Agent + 引用溯源"}')"
kb_id="$(json_get "$kb_json" "data.id")"

tmpfile="$(mktemp /tmp/knowflow-demo-XXXXXX.md)"
trap 'rm -f "$tmpfile"' EXIT
cat > "$tmpfile" <<'EOF'
# KnowFlow 架构说明

KnowFlow 是一个基于 RAG 的智能知识库问答平台。

核心服务包括：

- Spring Boot 后端：负责用户、知识库、文档、会话和权限校验。
- Python Worker：负责文档解析、文本切片、Embedding 和写入 pgvector。
- Go RAG Service：负责按知识库隔离检索、构建 Prompt、调用 LLM 并返回引用来源。
- Vue 前端：负责文档管理、聊天、SSE 流式输出和 sources 展示。

Agent 模式会先判断问题意图，再检索资料、生成回答，并通过 Citation Guard 检查回答是否有来源支撑。
EOF

curl -fsS -X POST "$BACKEND_URL/api/document/upload" \
  -H "Authorization: Bearer $token" \
  -F "kbId=$kb_id" \
  -F "file=@$tmpfile;filename=knowflow-demo.md" >/dev/null

echo "Demo data uploaded."
echo "username=$USERNAME password=$PASSWORD kbId=$kb_id"
