# KnowFlow Go RAG Service

基于 Go 的 RAG（检索增强生成）引擎。负责 query embedding、知识库检索、Prompt 构建、LLM 调用和 SSE 流式输出。

## 架构

```
Spring Boot → POST /rag/ask 或 /rag/ask/stream → Go RAG Service
                                                  ↓
                                      Embedding Provider (mock / openai / deepseek / ollama)
                                                  ↓
                                      PostgreSQL document_chunk (pgvector / keyword)
                                                  ↓
                                      LLM Provider (mock / deepseek / openai / ollama)
```

## 接口

### `GET /health`

```json
{
  "status": "ok",
  "version": "0.1.0",
  "config": {
    "llmProvider": "mock",
    "embeddingProvider": "mock",
    "embeddingDim": 1536,
    "defaultTopK": 5,
    "maxTopK": 20
  }
}
```

### `POST /rag/ask`（同步）

请求：
```json
{
  "kbId": 1,
  "question": "什么是 RAG？",
  "topK": 5
}
```

返回：
```json
{
  "answer": "...",
  "sources": [
    {
      "chunkId": 42,
      "documentId": 7,
      "fileName": "rag-intro.pdf",
      "chunkIndex": 3,
      "content": "RAG 是一种...",
      "score": 0.91
    }
  ],
  "latencyMs": 1240
}
```

### `POST /rag/ask/stream`（SSE 流式）

```
Content-Type: text/event-stream

event: token
data: {"type":"token","content":"RAG"}

event: sources
data: {"type":"sources","sources":[...]}

event: done
data: {"type":"done"}
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `RAG_PORT` | 服务端口 | 8090 |
| `RAG_DB_DSN` | PostgreSQL 连接串 | postgres://knowflow:change_me@localhost:5432/knowflow |
| `RAG_REQUEST_TIMEOUT_SECONDS` | 外部模型请求超时时间 | 60 |
| `RAG_DEFAULT_TOP_K` | 默认检索条数 | 5 |
| `RAG_MAX_TOP_K` | 最大检索条数上限 | 20 |
| `RAG_LLM_PROVIDER` | LLM 提供商 (mock/deepseek/openai/ollama) | mock |
| `RAG_LLM_API_KEY` | LLM API Key | — |
| `RAG_LLM_BASE_URL` | LLM API 地址 | — |
| `RAG_LLM_MODEL` | 模型名称 | — |
| `RAG_EMBEDDING_PROVIDER` | Embedding 提供商 (mock/openai/deepseek/ollama) | mock |
| `RAG_EMBEDDING_API_KEY` | Embedding API Key | — |
| `RAG_EMBEDDING_BASE_URL` | Embedding API 地址 | — |
| `RAG_EMBEDDING_MODEL` | Embedding 模型名称 | text-embedding-3-small |
| `RAG_EMBEDDING_DIM` | Embedding 维度 | 1536 |

## 本地启动

### 前置条件

- Go 1.21+
- PostgreSQL（含 document_chunk 数据）

### 启动

```bash
cd knowflow-rag-go

# 安装依赖
go mod tidy

# 运行
go run cmd/rag-server/main.go
```

### 测试

```bash
# 健康检查
curl http://localhost:8090/health

# 问答测试
curl -X POST http://localhost:8090/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"kbId":1,"question":"测试问题","topK":3}'

# 流式问答
curl -X POST http://localhost:8090/rag/ask/stream \
  -H "Content-Type: application/json" \
  -d '{"kbId":1,"question":"测试问题","topK":3}'
```

## 当前状态

| 功能 | 状态 | 说明 |
|------|:----:|------|
| keyword 检索 | ✅ | mock embedding 或向量生成失败时自动使用 ILIKE |
| pgvector 向量检索 | ✅ | query embedding 存在时使用 `embedding <=> query` 排序 |
| Query embedding | ✅ | 支持 OpenAI 兼容接口和 Ollama |
| Mock LLM | ✅ | 返回固定模板 |
| DeepSeek / OpenAI / Ollama LLM | ✅ | 支持同步与流式调用 |
| SSE 流式输出 | ✅ | text/event-stream |
| Agent 工作流 | ✅ | Intent Router / Retriever / Answer / Citation Guard |
| Prompt 构建 | ✅ | system + user messages |

## 后续规划

- [ ] 检索缓存
- [ ] 并发控制
- [ ] 更细粒度的 provider 重试和熔断
