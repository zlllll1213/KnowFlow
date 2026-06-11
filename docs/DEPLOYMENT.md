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

The root `docker-compose.yml` starts PostgreSQL + pgvector, password-protected Redis, MinIO, bucket initialization, Spring Boot, Go RAG, Python Worker, and an nginx-served Vue build.

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
- Go RAG health: `http://localhost:8090/health`
- MinIO Console: `http://localhost:9001`

PostgreSQL, Redis, Go RAG, and MinIO ports are bound to `127.0.0.1` by default. To expose them beyond the host, change the Compose port binding deliberately and protect the host firewall.

## Environment

Key variables:

- `KNOWFLOW_JWT_SECRET`: at least 32 bytes; never use the dev secret in production.
- `REDIS_PASSWORD`: required by Compose; Spring and Worker must use the same value.
- `KNOWFLOW_RAG_BASE_URL`: Spring to Go RAG URL.
- `KNOWFLOW_RAG_INTERNAL_TOKEN` / `RAG_INTERNAL_TOKEN`: shared internal token for Spring-to-Go RAG calls.
- `KNOWFLOW_RAG_FALLBACK_ENABLED`: default `false`.
- `KNOWFLOW_CORS_ALLOWED_ORIGINS`: comma-separated concrete frontend origins. Production must not use `*`.
- `KNOWFLOW_COOKIE_SECURE`: set `true` behind HTTPS.
- `KNOWFLOW_TRUSTED_PROXY_CIDRS`: only these proxies may supply `X-Forwarded-For`.
- `WORKER_TASK_RECOVERY_INTERVAL_SECONDS`: default `60`, re-enqueues recoverable `PENDING` / stale processing tasks.
- `WORKER_DB_MAX_CONNECTIONS`: caps Python Worker PostgreSQL pool size, default `8`.
- `RAG_LLM_PROVIDER`: `mock`, `openai`, `deepseek`, or `ollama`.
- `RAG_LLM_API_KEY`: set only on the server side; never commit real keys.
- `RAG_LLM_BASE_URL`: DeepSeek default is `https://api.deepseek.com`.
- `RAG_LLM_MODEL`: DeepSeek default is `deepseek-v4-flash`.
- `RAG_LLM_THINKING_ENABLED`: default `false`; set `true` only when you intentionally want DeepSeek thinking mode.
- `RAG_EMBEDDING_PROVIDER`: `mock`, `openai`, or `ollama`; DeepSeek is not used for embeddings.
- `RAG_EMBEDDING_API_KEY`: independent from `RAG_LLM_API_KEY` to avoid sending an LLM key to the wrong endpoint.
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
