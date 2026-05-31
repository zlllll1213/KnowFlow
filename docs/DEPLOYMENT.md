# KnowFlow Deployment

## Local Development

```bash
./scripts/dev-infra-up.sh

cd knowflow-backend
mvn spring-boot:run

cd ../knowflow-rag-go
go run cmd/rag-server/main.go

cd ../knowflow-worker-python
python3 -m app.main

cd ../knowflow-frontend
npm install
npm run dev
```

## Docker Compose

The root `docker-compose.yml` starts PostgreSQL + pgvector, Redis, MinIO, bucket initialization, Spring Boot, Go RAG, Python Worker, and Vue preview.

```bash
docker compose up --build
```

If local containers named `knowflow-postgres`, `knowflow-redis`, or `knowflow-minio` already exist, start an isolated smoke stack instead:

```bash
docker compose -p knowflow-smoke -f docker-compose.yml -f docker-compose.smoke.yml up --build -d
```

The isolated override uses Docker Compose `!override`, so use Docker Compose v2.23 or newer.

Default URLs:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8081`
- Go RAG: `http://localhost:8090/health`
- MinIO Console: `http://localhost:9001`

## Environment

Key variables:

- `KNOWFLOW_JWT_SECRET`: at least 32 bytes; never use the dev secret in production.
- `KNOWFLOW_RAG_BASE_URL`: Spring to Go RAG URL.
- `KNOWFLOW_RAG_FALLBACK_ENABLED`: default `false`.
- `WORKER_TASK_RECOVERY_INTERVAL_SECONDS`: default `60`, re-enqueues recoverable `PENDING` / stale processing tasks.
- `RAG_LLM_PROVIDER`: `mock`, `openai`, `deepseek`, or `ollama`.
- `RAG_EMBEDDING_PROVIDER`: `mock`, `openai`, `deepseek`, or `ollama`.
- `WORKER_STORAGE_TYPE`: `local` or `minio`.

## Smoke Test

After all services are running:

```bash
./scripts/smoke-e2e.sh
```

For the isolated smoke stack:

```bash
BACKEND_URL=http://localhost:18081 RAG_URL=http://localhost:18090 ./scripts/smoke-e2e.sh
```

The smoke test registers a user, creates a knowledge base, uploads a document, waits for parsing, asks through SSE, and verifies real sources.

## FAQ

- If documents stay `UPLOADED`, check the Python Worker logs and Redis connection.
- If chat returns “RAG 服务暂不可用”, check `KNOWFLOW_RAG_BASE_URL` and Go RAG `/health`.
- If sources are empty, ensure the Worker has produced chunks and embedding dimensions match the database vector column.
