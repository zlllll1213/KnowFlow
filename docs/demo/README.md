# KnowFlow Demo Data

## Quick Start

After all services are running (`docker compose up`), seed demo data:

```bash
./scripts/seed-demo-data.sh
```

## What It Creates

- Demo user account (`demo` / `Demo123456`)
- A knowledge base "KnowFlow 架构文档"
- A Markdown document describing KnowFlow's multi-service architecture
- Waits for Worker to parse and embed the document

## Manual Verification

```bash
# Check the document was parsed
curl http://localhost:8081/api/document/list?kbId=1 \
  -H "Authorization: Bearer <token>"

# Ask a question via SSE
curl -N -X POST http://localhost:8081/api/chat/ask/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"kbId":1,"sessionId":1,"question":"KnowFlow 是什么？"}'
```

## E2E Smoke Test

For a full automated test (register → upload → wait → ask → verify):

```bash
./scripts/smoke-e2e.sh
```

This validates sources are real (not mock), token events arrive, and the full pipeline works end-to-end.
