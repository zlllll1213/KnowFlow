# KnowFlow Go RAG Service

基于 Go 的 RAG（检索增强生成）引擎。负责向量检索、Prompt 构建和 LLM 调用。

> ⚠️ 当前为第一版：使用数据库 keyword LIKE 检索 + Mock LLM。真实向量检索和 LLM 调用待后续实现。

## 架构

```
Spring Boot → POST /rag/ask → Go RAG Service → PostgreSQL (document_chunk)
                                   ↓
                              Prompt Builder
                                   ↓
                              LLM Provider (mock / deepseek / openai / ollama)
```

## 接口

### `GET /health`

```json
{ "status": "ok", "version": "0.1.0" }
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

data: {"type":"token","content":"RAG"}
data: {"type":"token","content":"（检索"}
data: {"type":"sources","sources":[...]}
data: {"type":"done"}
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `RAG_PORT` | 服务端口 | 8090 |
| `RAG_DB_DSN` | PostgreSQL 连接串 | postgres://knowflow:knowflow123@localhost:5432/knowflow |
| `RAG_LLM_PROVIDER` | LLM 提供商 (mock/deepseek/openai/ollama) | mock |
| `RAG_LLM_API_KEY` | LLM API Key | — |
| `RAG_LLM_BASE_URL` | LLM API 地址 | — |
| `RAG_LLM_MODEL` | 模型名称 | — |

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
| keyword 检索 | ✅ | ILIKE 关键词匹配 + fallback |
| pgvector 向量检索 | TODO | 需 pgvector 扩展 + embedding 数据 |
| Mock LLM | ✅ | 返回固定模板 |
| DeepSeek / OpenAI | TODO | 需配置 API key |
| SSE 流式输出 | ✅ | text/event-stream |
| Prompt 构建 | ✅ | system + user messages |

## 后续规划

- [ ] pgvector 向量检索 `ORDER BY embedding <=> $1`
- [ ] DeepSeek / OpenAI / Ollama Provider 实现
- [ ] embedding 生成（query embedding）
- [ ] 检索缓存
- [ ] 并发控制
