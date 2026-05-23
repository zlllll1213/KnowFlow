# KnowFlow 优化规划文档

> 版本：v1.0 · 日期：2026-05-23  
> 定位：将 KnowFlow 从 CRUD 演示项目升级为真正可运行的 RAG 智能知识库平台

---

## 目录

1. [项目现状分析](#1-项目现状分析)
2. [总体优化目标](#2-总体优化目标)
3. [模块职责划分](#3-模块职责划分)
4. [Go RAG Service 专项设计](#4-go-rag-service-专项设计)
5. [Python Worker 专项设计](#5-python-worker-专项设计)
6. [数据库优化设计](#6-数据库优化设计)
7. [API 设计优化](#7-api-设计优化)
8. [前端体验优化](#8-前端体验优化)
9. [工程化优化](#9-工程化优化)
10. [分阶段落地路线](#10-分阶段落地路线)
11. [TODO Roadmap](#11-todo-roadmap)
12. [简历项目描述](#12-简历项目描述)

---

## 1. 项目现状分析

### 1.1 已完成模块

| 模块 | 状态 | 说明 |
|------|:----:|------|
| 用户注册 / 登录 | ✅ | BCrypt 密码加密，JWT 24h 有效期，Spring Security 过滤器链 |
| 知识库 CRUD | ✅ | 完整增删改查，逻辑删除，所有权校验 |
| 文档上传 | ✅ | 文件保存至本地 `./storage`，创建 Document 记录 |
| 文档列表 / 详情 / 删除 / 状态查询 | ✅ | 接口完整，逻辑删除 |
| 解析任务入队 | ✅ | `TaskService` 向 Redis List `knowflow:parse:queue` push taskId |
| 聊天会话创建 / 列表 | ✅ | 基本 CRUD，按时间排序 |
| 聊天历史持久化 | ✅ | 用户消息 + AI 回复均写入 `chat_message` |
| 前端完整页面框架 | ✅ | 8 个页面、路由守卫、JWT 自动携带 |
| Docker 基础设施 | ✅ | PostgreSQL + Redis + MinIO 一键启动 |

### 1.2 Mock / 未闭环功能

| 功能 | 问题描述 | 影响 |
|------|----------|------|
| RAG 问答 | `RagClient.mockAsk()` 返回固定模板字符串，无任何检索或 LLM 调用 | 核心功能不可用 |
| 引用来源 | `Chat.vue` 中 SourcePanel 数据为硬编码假数据 | 展示完全失真 |
| 文档解析 | Redis 队列有 push 但无 consumer，文档状态永远停在 UPLOADED/PENDING | 知识库无可检索内容 |
| 向量存储 | `document_chunk.embedding` 字段类型为 `TEXT`，pgvector 未安装启用 | 向量检索无法工作 |
| MinIO 存储 | `MinioConfig` bean 存在，`DocumentServiceImpl` 未注入，文件存在本地无备份 | 文件存储不可靠 |
| Dashboard 统计 | 仅聚合第一个知识库的文档数，活跃会话数恒为 0 | 数据不准确 |

### 1.3 各层现有问题清单

#### 后端 (Spring Boot)

| 问题 | 位置 | 风险 |
|------|------|------|
| JWT secret 硬编码在 `application.yml` | `application.yml` | 安全漏洞，不可上线 |
| 文件存储逻辑内联在 `DocumentServiceImpl` | `DocumentServiceImpl.java` | 违反单一职责，切换 MinIO 需大改 |
| `MinioStorageService` 接口 + `LocalStorageService` 实现 + 内联逻辑三套并存 | `util/` | 代码混乱，实现不一致 |
| `PageResult` 类存在但无任何接口使用分页 | 所有 Controller | 大数据量时全量返回 OOM 风险 |
| 无 Swagger / springdoc-openapi | — | 接口文档需手动维护 |
| 无 `application-dev.yml` / `application-prod.yml` Profile 分离 | — | 无法安全部署生产 |
| ChatController 返回的 `ChatMessageVO` 无 sources 字段 | `ChatController.java` | SSE / 引用来源改造需重设计 |

#### 前端 (Vue 3)

| 问题 | 位置 | 风险 |
|------|------|------|
| `vite.config.ts` dev proxy 端口为 8080，后端实际为 8081 | `vite.config.ts` | proxy 配置实际失效，靠 `.env.development` 兜底 |
| `ChatStore`、`KbStore` 存在但 Chat/Dashboard 页面绕过 store 直接调 API | `Chat.vue`、`Dashboard.vue` | 状态不共享，重复请求 |
| Chat.vue 回答后 sources 为硬编码假数据 | `Chat.vue` | 展示完全失真 |
| Dashboard.vue 聚合统计逻辑错误（只查第一个 KB） | `Dashboard.vue` | 数据不准确 |
| 无 `.env.example` | — | 协作者不知道需配置哪些变量 |
| 无全局 loading 状态 / 骨架屏 | 多处 | 网络慢时体验差 |

#### 数据库

| 问题 | 说明 |
|------|------|
| `document_chunk.embedding` 为 `TEXT` | 无法进行向量检索 |
| pgvector 扩展未安装 | `VECTOR` 类型不可用 |
| 无 `document_chunk` 上的向量索引（IVFFlat / HNSW） | 即使安装 pgvector 检索也慢 |
| `parse_task.status` 无 `CANCELLED` 状态 | 异常场景处理不完整 |
| `document` 表无 `chunk_count` 字段 | 无法快速知道文档切片完成数量 |

#### Docker / 工程化

| 问题 | 说明 |
|------|------|
| Spring Boot 未容器化 | 生产部署困难 |
| 无健康检查 healthcheck | 服务重启顺序无保障 |
| MinIO bucket 需手动创建 | 首次启动需额外步骤 |
| 无 `.env` 文件注入敏感配置 | docker-compose 中明文密码 |

### 1.4 作为简历项目的优缺点

**优势**
- 技术栈完整（Vue3 + Spring Boot 3 + PostgreSQL + Redis + MinIO）
- 架构分层清晰（Controller / Service / Mapper / Entity）
- 安全设计到位（JWT + BCrypt + Spring Security + CORS）
- 业务闭环合理（上传 → 解析 → 检索 → 问答 → 历史）
- 已预留 Go RAG Service 扩展点（`RagClient.mockAsk()`）

**短板**
- 核心 RAG 功能（向量检索 + LLM 调用）完全 mock，上线即穿帮
- Python Worker 只有队列 push，无实际 consumer
- 无可量化技术指标（检索耗时 / 召回率 / 并发量等）
- 缺少截图 / Demo 视频，简历难以直观展示

---

## 2. 总体优化目标

**核心目标：** 上传一份 PDF/DOCX 文档后，能基于文档内容进行真实 RAG 问答，并在前端展示引用来源片段。

### 2.1 各组件最终职责

| 组件 | 定位 | 职责边界 |
|------|------|----------|
| Spring Boot | 主业务后端 | 用户 / KB / 文档 / 会话 CRUD，JWT 鉴权，调用 Go RAG Service |
| Vue 3 | Web 前端 | 用户交互，SSE 流式渲染，引用来源展示 |
| Python Worker | 文档处理服务 | 消费队列 → 解析 → 切片 → embedding → 写 DB |
| Go RAG Service | RAG 引擎 | embedding 查询 / pgvector 检索 / Prompt 构建 / LLM 调用 / SSE 输出 |
| PostgreSQL + pgvector | 结构化 + 向量存储 | 所有业务数据，向量相似度检索 |
| Redis | 消息队列 + 缓存 | 解析任务队列，热点 KB 缓存 |
| MinIO | 对象存储 | 原始文档文件持久化 |

### 2.2 推荐最终架构图

```mermaid
graph TB
    Browser["浏览器<br/>Vue 3 + TypeScript"]

    subgraph Backend["Spring Boot 3 (Port 8081)"]
        Auth["AuthController<br/>JWT 认证"]
        KbCtrl["KbController<br/>知识库管理"]
        DocCtrl["DocumentController<br/>文档管理"]
        ChatCtrl["ChatController<br/>会话 & 问答"]
        RagCli["RagClient<br/>HTTP 调用 Go 服务"]
    end

    subgraph Storage["存储层"]
        PG[("PostgreSQL 16<br/>+ pgvector")]
        Redis[("Redis 7<br/>任务队列 / 缓存")]
        Minio[("MinIO<br/>原始文档")]
    end

    subgraph GoRAG["Go RAG Service (Port 8090)"]
        RAGApi["/rag/ask<br/>/rag/ask/stream"]
        EmbedSvc["Embedding 生成"]
        VecSearch["pgvector TopK 检索"]
        PromptBld["Prompt Builder"]
        LLMProvider["LLM Provider<br/>DeepSeek / OpenAI / Ollama"]
    end

    subgraph PythonWorker["Python Worker"]
        Consumer["Redis 队列消费者"]
        Parser["文档解析<br/>PDF/DOCX/MD/TXT"]
        Chunker["文本切片"]
        Embedder["Embedding 生成"]
        Writer["写入 document_chunk<br/>更新 document 状态"]
    end

    Browser -->|"REST + SSE"| Backend
    ChatCtrl -->|"HTTP POST /rag/ask/stream"| GoRAG
    Backend -->|"JDBC"| PG
    Backend -->|"RPUSH"| Redis
    Backend -->|"PUT object"| Minio

    Consumer -->|"BLPOP"| Redis
    Consumer --> Parser --> Chunker --> Embedder --> Writer
    Writer -->|"INSERT document_chunk"| PG
    Writer -->|"UPDATE document.status"| PG

    RAGApi --> EmbedSvc --> VecSearch
    VecSearch -->|"SELECT … ORDER BY embedding <=> $1"| PG
    VecSearch --> PromptBld --> LLMProvider
    LLMProvider -->|"SSE chunks"| RAGApi
```

---

## 3. 模块职责划分

### 3.1 Spring Boot 主后端

**负责：**
- 用户注册 / 登录 / 信息管理（JWT + BCrypt）
- 知识库 CRUD（所有权校验、逻辑删除）
- 文档上传（接收文件 → 存 MinIO → 创建 Document 记录 → 入队 Redis）
- 文档状态查询（UPLOADED / PARSING / EMBEDDING / DONE / FAILED）
- 聊天会话 / 历史管理
- **编排调用 Go RAG Service**（传入 kbId + question，获取 answer + sources）
- 统一返回格式、全局异常处理、CORS、JWT 过滤器

**不负责：**
- 文档解析、切片、向量化（交 Python Worker）
- 向量检索、Prompt 构建、LLM 调用（交 Go RAG Service）

### 3.2 Vue 3 前端

**负责：**
- 用户登录 / 注册表单，token 本地持久化
- 知识库列表 / 详情页
- 文档上传进度显示、解析状态轮询
- 聊天页面（会话选择、消息发送、流式文字渲染）
- **SourcePanel**：展示回答引用的文档片段（fileName、pageNo、score、content 摘要）
- 全局 loading 状态、错误提示

**不负责：**
- 直接调用 Go 服务或 Python Worker
- 任何 RAG 或 embedding 逻辑

### 3.3 Python Worker

**负责：**
- 从 Redis `knowflow:parse:queue` BLPOP 任务
- 根据 taskId 查询 `parse_task`，获取 documentId、kbId
- 从 MinIO 下载原始文件
- 解析文档内容（Unstructured / pypdf / python-docx / markdown）
- 文本清洗（去空白行、特殊字符）
- 按 chunk_size（如 512 token）切片，维护 chunk_index
- 调用 embedding API（OpenAI / Ollama / local model）生成向量
- 批量 INSERT `document_chunk`（content + embedding）
- UPDATE `document.status` 和 `parse_task.status`

**不负责：**
- 用户管理、知识库管理
- 向量检索、问答生成

### 3.4 Go RAG Service

**负责：**
- 提供 `/rag/ask` 和 `/rag/ask/stream` 两个端点
- 接收 kbId + question，生成 question embedding
- 查询 PostgreSQL pgvector：`ORDER BY embedding <=> $1 LIMIT k`
- 拼装 Prompt（system prompt + context chunks + user question）
- 调用 LLM Provider（DeepSeek / OpenAI / Ollama）
- 普通接口返回 `{ answer, sources }` JSON
- 流式接口通过 SSE 逐 token 推送，最后推送 sources

**不负责：**
- 任何 CRUD 业务逻辑
- 用户认证（由 Spring Boot 网关负责，Go 服务为内部服务）
- 文档解析和 embedding 写入

### 3.5 PostgreSQL + pgvector

**负责：**
- 所有业务表（用户、KB、文档、切片、会话、消息、任务）
- `document_chunk.embedding VECTOR(1536)` 向量字段
- IVFFlat 或 HNSW 近邻索引

### 3.6 Redis

**负责：**
- `knowflow:parse:queue`（List）—— 文档解析任务队列
- 热点知识库元数据缓存（可选）
- JWT 黑名单（可选，实现登出）

### 3.7 MinIO

**负责：**
- 存储用户上传的原始文件（PDF / DOCX / MD / TXT）
- 提供 Presigned URL 供前端直接下载（可选）

---

## 4. Go RAG Service 专项设计

### 4.1 设计原则

Go 服务是一个**纯 RAG Engine**，不是第二套业务后端。它只关心"给我一个 kbId 和 question，我返回 answer 和 sources"。

### 4.2 目录结构

```
knowflow-rag/
├── cmd/
│   └── main.go
├── internal/
│   ├── handler/
│   │   ├── health.go          # GET /health
│   │   └── rag.go             # POST /rag/ask, POST /rag/ask/stream
│   ├── embedding/
│   │   └── client.go          # 生成 query embedding
│   ├── retrieval/
│   │   └── pgvector.go        # pgvector TopK 检索
│   ├── prompt/
│   │   └── builder.go         # Prompt 模板拼装
│   ├── llm/
│   │   ├── provider.go        # LLM Provider 接口
│   │   ├── deepseek.go        # DeepSeek Provider
│   │   ├── openai.go          # OpenAI Provider
│   │   └── ollama.go          # Ollama Provider
│   └── model/
│       ├── request.go         # RagRequest
│       └── response.go        # RagResponse、SourceChunk
├── config/
│   └── config.go
└── go.mod
```

### 4.3 接口定义

#### `POST /rag/ask`（同步）

```json
// Request
{
  "kbId": 1,
  "question": "什么是 RAG？",
  "topK": 5
}

// Response
{
  "answer": "RAG（Retrieval-Augmented Generation）是...",
  "sources": [
    {
      "chunkId": 42,
      "documentId": 7,
      "fileName": "rag-intro.pdf",
      "chunkIndex": 3,
      "content": "RAG 是一种结合检索和生成的技术...",
      "score": 0.91
    }
  ],
  "latencyMs": 1240
}
```

#### `POST /rag/ask/stream`（SSE 流式）

```
Content-Type: text/event-stream

data: {"type":"token","content":"RAG"}
data: {"type":"token","content":"（检索"}
data: {"type":"token","content":"增强生成"}
...
data: {"type":"sources","sources":[{"chunkId":42,...}]}
data: {"type":"done"}
```

#### `GET /health`

```json
{ "status": "ok", "version": "0.1.0" }
```

### 4.4 LLM Provider 接口

```go
type LLMProvider interface {
    Chat(ctx context.Context, messages []Message) (string, error)
    ChatStream(ctx context.Context, messages []Message, out chan<- string) error
}
```

通过配置 `RAG_LLM_PROVIDER=deepseek|openai|ollama` 切换实现，无需改代码。

### 4.5 配置项（环境变量）

```env
RAG_PORT=8090
RAG_DB_DSN=postgres://knowflow:YOUR_DB_PASS@localhost:5432/knowflow
RAG_LLM_PROVIDER=deepseek
RAG_LLM_API_KEY=YOUR_LLM_API_KEY
RAG_LLM_BASE_URL=https://api.deepseek.com/v1
RAG_LLM_MODEL=deepseek-chat
RAG_EMBEDDING_PROVIDER=openai
RAG_EMBEDDING_API_KEY=YOUR_EMBEDDING_API_KEY
RAG_EMBEDDING_MODEL=text-embedding-3-small
RAG_EMBEDDING_DIM=1536
RAG_DEFAULT_TOP_K=5
```

### 4.6 Prompt 模板

```
你是一个专业的知识库问答助手。请根据以下参考资料回答用户问题。
如果参考资料中没有相关信息，请明确说明无法从知识库中找到答案，不要编造。

参考资料：
---
{{range .Chunks}}
[文档：{{.FileName}}，片段 {{.ChunkIndex}}]
{{.Content}}
---
{{end}}

用户问题：{{.Question}}
```

---

## 5. Python Worker 专项设计

### 5.1 设计原则

Python Worker 是一个**纯文档处理服务**，消费队列、解析文件、生成向量、写入数据库，不暴露任何 HTTP 接口（健康检查除外）。

### 5.2 目录结构

```
knowflow-worker/
├── main.py                    # 入口，启动 worker 线程
├── worker.py                  # 队列消费主循环
├── parser/
│   ├── __init__.py
│   ├── pdf_parser.py          # pypdf / pdfminer
│   ├── docx_parser.py         # python-docx
│   ├── markdown_parser.py     # markdown → text
│   └── txt_parser.py
├── chunker/
│   ├── __init__.py
│   └── token_chunker.py       # 按 token 切片，维护 overlap
├── embedder/
│   ├── __init__.py
│   ├── base.py                # EmbeddingProvider 抽象
│   ├── openai_embedder.py
│   └── ollama_embedder.py
├── db/
│   ├── __init__.py
│   └── postgres.py            # psycopg2 / asyncpg 连接池
├── storage/
│   └── minio_client.py        # MinIO 文件下载
├── config.py                  # 环境变量配置
└── requirements.txt
```

### 5.3 工作流

```mermaid
flowchart TD
    A[BLPOP knowflow:parse:queue] --> B[查询 parse_task by taskId]
    B --> C[UPDATE parse_task.status = PROCESSING]
    C --> D[UPDATE document.status = PARSING]
    D --> E[从 MinIO 下载文件]
    E --> F{文件类型}
    F -->|PDF| G[pdf_parser]
    F -->|DOCX| H[docx_parser]
    F -->|MD| I[markdown_parser]
    F -->|TXT| J[txt_parser]
    G & H & I & J --> K[文本清洗]
    K --> L[UPDATE document.status = EMBEDDING]
    L --> M[token_chunker 切片]
    M --> N[批量调用 Embedding API]
    N --> O[批量 INSERT document_chunk]
    O --> P[UPDATE document.status = DONE<br/>UPDATE parse_task.status = DONE]
    P --> Q[返回 BLPOP 等待下一个任务]

    E -->|下载失败| ERR[UPDATE status = FAILED<br/>记录 error_message]
    N -->|Embedding 失败| ERR
    O -->|写入失败| ERR
```

### 5.4 切片策略

```python
CHUNK_SIZE = 512       # tokens per chunk
CHUNK_OVERLAP = 64     # overlap tokens between adjacent chunks
MAX_CHUNKS_PER_DOC = 1000  # 单文档最大切片数，防止超大文件爆内存
```

### 5.5 配置项（环境变量）

```env
WORKER_REDIS_URL=redis://localhost:6379/0
WORKER_DB_DSN=postgresql://knowflow:YOUR_DB_PASS@localhost:5432/knowflow
WORKER_MINIO_ENDPOINT=localhost:9000
WORKER_MINIO_ACCESS_KEY=YOUR_MINIO_ACCESS_KEY
WORKER_MINIO_SECRET_KEY=YOUR_MINIO_SECRET_KEY
WORKER_MINIO_BUCKET=knowflow
WORKER_EMBEDDING_PROVIDER=openai
WORKER_EMBEDDING_API_KEY=YOUR_EMBEDDING_API_KEY
WORKER_EMBEDDING_MODEL=text-embedding-3-small
WORKER_EMBEDDING_DIM=1536
WORKER_CONCURRENCY=2
```

---

## 6. 数据库优化设计

### 6.1 当前 schema 问题总览

| 问题 | 当前状态 | 优化方向 |
|------|----------|----------|
| `document_chunk.embedding` | `TEXT DEFAULT ''` | 改为 `VECTOR(1536)` |
| pgvector 扩展 | 未安装 | `CREATE EXTENSION IF NOT EXISTS vector` |
| 无向量索引 | — | 添加 IVFFlat 或 HNSW 索引 |
| `parse_task.status` 缺少 CANCELLED | PENDING/PROCESSING/DONE/FAILED | 新增 CANCELLED |
| `document` 无 `chunk_count` | — | 新增 `chunk_count INT DEFAULT 0` |
| `chat_message` 无 `sources` | — | 新增 `sources JSONB DEFAULT '[]'` |
| 无 `model_config` 表 | — | 存储用户自定义 LLM 配置 |

### 6.2 优化后 schema

```sql
-- 启用 pgvector 扩展（需 PostgreSQL 16 + vector 插件镜像）
CREATE EXTENSION IF NOT EXISTS vector;

-- 用户表
CREATE TABLE IF NOT EXISTS user_account (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    is_deleted  SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_kb_user_id ON knowledge_base(user_id) WHERE is_deleted = 0;

-- 文档表
CREATE TABLE IF NOT EXISTS document (
    id            BIGSERIAL    PRIMARY KEY,
    kb_id         BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_path     VARCHAR(512) NOT NULL,  -- MinIO object key
    file_size     BIGINT       NOT NULL DEFAULT 0,
    file_type     VARCHAR(32),
    status        VARCHAR(32)  NOT NULL DEFAULT 'UPLOADED',
    -- 状态枚举: UPLOADED | PARSING | EMBEDDING | DONE | FAILED
    chunk_count   INT          NOT NULL DEFAULT 0,
    error_message TEXT,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_document_kb_id  ON document(kb_id)  WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_document_status ON document(status)  WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_document_user   ON document(user_id) WHERE is_deleted = 0;

-- 文档切片表（核心向量表）
CREATE TABLE IF NOT EXISTS document_chunk (
    id          BIGSERIAL    PRIMARY KEY,
    document_id BIGINT       NOT NULL,
    kb_id       BIGINT       NOT NULL,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    embedding   VECTOR(1536),              -- pgvector，维度与 embedding 模型对齐
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chunk_document_id ON document_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_kb_id       ON document_chunk(kb_id);
-- 向量检索索引（待切片数据量 > 1000 后创建）
-- IVFFlat 适合数据量 < 100 万；HNSW 适合更大数据量，内存占用较高
-- CREATE INDEX IF NOT EXISTS idx_chunk_embedding_ivf
--     ON document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
-- CREATE INDEX IF NOT EXISTS idx_chunk_embedding_hnsw
--     ON document_chunk USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- 解析任务表
CREATE TABLE IF NOT EXISTS parse_task (
    id           BIGSERIAL PRIMARY KEY,
    document_id  BIGINT    NOT NULL,
    kb_id        BIGINT    NOT NULL,
    status       VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    -- 状态枚举: PENDING | PROCESSING | DONE | FAILED | CANCELLED
    error_message TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_task_document_id ON parse_task(document_id);
CREATE INDEX IF NOT EXISTS idx_task_status      ON parse_task(status);

-- 聊天会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id         BIGSERIAL    PRIMARY KEY,
    kb_id      BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(255),
    is_deleted SMALLINT     NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_session_kb_user ON chat_session(kb_id, user_id) WHERE is_deleted = 0;

-- 聊天消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id         BIGSERIAL PRIMARY KEY,
    session_id BIGINT    NOT NULL,
    kb_id      BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    role       VARCHAR(16) NOT NULL,    -- user | assistant
    content    TEXT      NOT NULL,
    sources    JSONB     NOT NULL DEFAULT '[]',  -- 引用来源，SourceChunk 数组
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_message_session_id ON chat_message(session_id);

-- 模型配置表（可选，用于用户自定义 LLM）
CREATE TABLE IF NOT EXISTS model_config (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL UNIQUE,
    provider    VARCHAR(32)  NOT NULL DEFAULT 'deepseek',
    model_name  VARCHAR(128) NOT NULL DEFAULT 'deepseek-chat',
    api_key     VARCHAR(512),           -- 加密存储
    base_url    VARCHAR(512),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

### 6.3 向量检索 SQL 示例

```sql
-- TopK 余弦相似度检索（pgvector 语法）
SELECT
    dc.id,
    dc.document_id,
    dc.chunk_index,
    dc.content,
    d.file_name,
    1 - (dc.embedding <=> $1::vector) AS score
FROM document_chunk dc
JOIN document d ON d.id = dc.document_id
WHERE dc.kb_id = $2
  AND d.is_deleted = 0
ORDER BY dc.embedding <=> $1::vector
LIMIT $3;
```

### 6.4 Docker 镜像选型

pgvector 需要专用镜像，替换 docker-compose 中的 postgres 镜像：

```yaml
# 替换原来的 postgres:16-alpine
image: pgvector/pgvector:pg16
```

---

## 7. API 设计优化

### 7.1 统一响应格式（保持不变）

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

### 7.2 接口设计优化清单

#### 用户认证（无变化）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录，返回 JWT |
| GET | `/api/auth/me` | 当前用户信息 |
| POST | `/api/auth/logout` | **新增**：JWT 加入 Redis 黑名单 |

#### 知识库管理（新增统计字段）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/kb` | 创建知识库 |
| GET | `/api/kb/list` | 列表（**返回 documentCount**） |
| GET | `/api/kb/{id}` | 详情 |
| PUT | `/api/kb/{id}` | 更新 |
| DELETE | `/api/kb/{id}` | 删除 |

`KbVO` 新增字段：
```json
{
  "id": 1,
  "name": "技术文档库",
  "description": "...",
  "documentCount": 5,
  "doneCount": 3,
  "createdAt": "2026-05-23T10:00:00",
  "updatedAt": "2026-05-23T12:00:00"
}
```

#### 文档管理（新增 chunkCount）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/document/upload` | 上传（multipart/form-data） |
| GET | `/api/document/list?kbId=` | 列表 |
| GET | `/api/document/{id}` | 详情 |
| DELETE | `/api/document/{id}` | 删除 |
| GET | `/api/document/{id}/status` | 状态查询（轮询用） |
| GET | `/api/document/{id}/chunks` | **新增**：查询切片列表（调试用） |

`DocumentVO` 新增字段：`chunkCount`、`parseTaskId`

#### 聊天会话

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/session` | 创建会话 |
| GET | `/api/chat/session/list?kbId=` | 会话列表 |
| DELETE | `/api/chat/session/{id}` | **新增**：删除会话 |
| POST | `/api/chat/ask` | 普通问答（返回 answer + sources） |
| GET | `/api/chat/ask/stream` | **新增**：SSE 流式问答（EventSource） |
| GET | `/api/chat/history?sessionId=` | 历史消息（含 sources 字段） |

#### SSE 流式问答接口

```
GET /api/chat/ask/stream?kbId=1&sessionId=2&question=xxx
Authorization: Bearer <token>

Response: Content-Type: text/event-stream

data: {"type":"token","content":"RAG"}
data: {"type":"token","content":"（检索增强"}
...
data: {"type":"sources","sources":[{"chunkId":42,"fileName":"doc.pdf","content":"...","score":0.91}]}
data: {"type":"done"}
```

#### Dashboard 统计接口（新增）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/stats/overview` | **新增**：返回 kbCount、docCount、doneDocCount、sessionCount |

---

## 8. 前端体验优化

### 8.1 登录页 / 注册页

- [ ] 修复 `vite.config.ts` dev proxy 端口从 8080 改为 8081
- [ ] 增加表单字段实时校验（用户名格式、密码强度、邮箱格式）
- [ ] 登录失败错误信息本地化（"用户名或密码错误" 而非 500 raw message）
- [ ] 记住我功能（7 天 token 有效期可选）

### 8.2 Dashboard

- [ ] 新增 `/api/stats/overview` 接口，一次性返回所有统计数字
- [ ] 修复"活跃会话"统计（当前恒为 0）
- [ ] 统计卡片增加骨架屏 loading 状态
- [ ] 最近文档列表从全部知识库聚合，非只第一个 KB

### 8.3 知识库列表

- [ ] `KbVO` 展示 documentCount（已有文档数）、doneCount（已解析完成数）
- [ ] 点击知识库卡片跳转到详情页，悬浮显示操作按钮（编辑 / 删除）
- [ ] 删除知识库增加二次确认 dialog

### 8.4 文档管理 / 知识库详情

- [ ] 上传进度条（`onUploadProgress` axios 事件）
- [ ] 文档状态图标：
  - `UPLOADED` → 灰色时钟
  - `PARSING` → 蓝色旋转
  - `EMBEDDING` → 紫色旋转
  - `DONE` → 绿色对勾
  - `FAILED` → 红色叹号（hover 显示 error_message）
- [ ] 状态轮询：每 3s 轮询一次 `GET /api/document/{id}/status`，DONE/FAILED 停止
- [ ] 显示 chunkCount（文档被切成了多少片段）

### 8.5 聊天页面（核心优化）

- [ ] 使用 Pinia ChatStore 管理状态，消除 `Chat.vue` 内所有局部 `ref` 冗余
- [ ] 接入真实 SSE 流式问答（`EventSource` 或 `fetch` + `ReadableStream`）：
  ```typescript
  const source = new EventSource(`/api/chat/ask/stream?...`)
  source.onmessage = (e) => {
    const data = JSON.parse(e.data)
    if (data.type === 'token') appendToken(data.content)
    if (data.type === 'sources') setSources(data.sources)
    if (data.type === 'done') source.close()
  }
  ```
- [ ] 消息流式渲染（逐字追加，打字机效果）
- [ ] 发送中禁用输入框和发送按钮，显示 spinner
- [ ] 消息错误时显示重试按钮
- [ ] 代码块高亮（highlight.js 或 shiki）
- [ ] Markdown 渲染（marked.js）

### 8.6 SourcePanel（引用来源面板）

- [ ] 移除硬编码假数据，改为接收真实 `sources` 数组
- [ ] 每个 source 卡片展示：文件名、片段序号、相似度分数（进度条形式）、内容摘要（最多 200 字）
- [ ] 点击来源卡片高亮对应文档（可选）
- [ ] 无引用时展示"本次回答未找到相关文档片段"

### 8.7 设置页面

- [ ] 用户信息修改（昵称、邮箱、密码修改）
- [ ] **模型配置面板**：选择 Provider（DeepSeek / OpenAI / Ollama）、填写 API Key 和 Base URL
- [ ] 存储用量统计（文档数、切片数、文件大小合计）

---

## 9. 工程化优化

### 9.1 后端配置分离

**`application.yml`（公共配置）**
```yaml
spring:
  application:
    name: knowflow-backend
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}

server:
  port: ${SERVER_PORT:8081}
```

**`application-dev.yml`（开发环境）**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/knowflow
    username: knowflow
    password: ${DB_PASSWORD:knowflow123}
  data:
    redis:
      host: localhost
      port: 6379

knowflow:
  jwt:
    secret: ${JWT_SECRET:dev-secret-please-change-in-prod}
    expiration: 86400000
  storage:
    minio:
      endpoint: http://localhost:9000
      access-key: ${MINIO_ACCESS_KEY:minioadmin}
      secret-key: ${MINIO_SECRET_KEY:minioadmin123}
      bucket: knowflow
  rag:
    service-url: http://localhost:8090
```

**`application-prod.yml`（生产环境）**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT}

knowflow:
  jwt:
    secret: ${JWT_SECRET}    # 必须通过环境变量注入，禁止硬编码
    expiration: 86400000
```

### 9.2 `.env.example` 模板

**后端 `knowflow-backend/.env.example`**
```env
SPRING_PROFILES_ACTIVE=dev
DB_PASSWORD=your_db_password
JWT_SECRET=your_jwt_secret_at_least_32_characters
MINIO_ACCESS_KEY=your_minio_access_key
MINIO_SECRET_KEY=your_minio_secret_key
RAG_SERVICE_URL=http://localhost:8090
```

**前端 `knowflow-frontend/.env.example`**
```env
VITE_API_BASE_URL=http://localhost:8081
```

**Python Worker `knowflow-worker/.env.example`**
```env
WORKER_REDIS_URL=redis://localhost:6379/0
WORKER_DB_DSN=postgresql://knowflow:your_db_pass@localhost:5432/knowflow
WORKER_MINIO_ENDPOINT=localhost:9000
WORKER_MINIO_ACCESS_KEY=your_minio_access_key
WORKER_MINIO_SECRET_KEY=your_minio_secret_key
WORKER_EMBEDDING_API_KEY=your_embedding_api_key
```

**Go RAG Service `knowflow-rag/.env.example`**
```env
RAG_PORT=8090
RAG_DB_DSN=postgres://knowflow:your_db_pass@localhost:5432/knowflow
RAG_LLM_PROVIDER=deepseek
RAG_LLM_API_KEY=your_llm_api_key
RAG_EMBEDDING_API_KEY=your_embedding_api_key
```

### 9.3 Swagger / springdoc-openapi

在 `pom.xml` 中添加：
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

启动后访问 `http://localhost:8081/swagger-ui.html`，免维护 API 文档。

在 `SecurityConfig` 放开 swagger 路径：
```java
.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
```

### 9.4 存储层重构

将 `DocumentServiceImpl` 内联的文件存储逻辑抽出，统一走 `StorageService` 接口：

```java
public interface StorageService {
    String upload(String bucket, String objectKey, InputStream in, long size, String contentType);
    InputStream download(String bucket, String objectKey);
    void delete(String bucket, String objectKey);
}
```

实现：`MinioStorageServiceImpl`（生产）/ `LocalStorageServiceImpl`（本地开发）  
通过 `@ConditionalOnProperty(name="knowflow.storage.type", havingValue="minio")` 切换。

### 9.5 异常处理规范

当前 `GlobalExceptionHandler` 已覆盖基本场景，补充：
- `MethodArgumentNotValidException` → 返回字段级错误信息
- `MaxUploadSizeExceededException` → 友好提示文件大小超限
- `DataAccessException` → 日志打印，返回 50001 服务器错误（不暴露 SQL 细节）

### 9.6 日志规范

```yaml
logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  level:
    root: INFO
    com.knowflow: DEBUG
    org.springframework.security: WARN
```

关键节点必须打日志：
- 文档上传（size、类型、kbId）
- 解析任务入队（taskId、documentId）
- RAG 服务调用（kbId、question 前 50 字、耗时）
- JWT 验证失败

### 9.7 Docker Compose 完整化

```yaml
version: "3.8"

services:
  postgres:
    image: pgvector/pgvector:pg16    # 替换为支持 pgvector 的镜像
    container_name: knowflow-postgres
    environment:
      POSTGRES_DB: knowflow
      POSTGRES_USER: knowflow
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./knowflow-backend/sql/init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U knowflow"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: knowflow-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  minio:
    image: minio/minio:latest
    container_name: knowflow-minio
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    ports:
      - "9000:9000"
      - "9001:9001"
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 10s
      retries: 3

  # 待阶段 4 后添加：
  # rag-service:
  #   build: ./knowflow-rag
  #   ports: ["8090:8090"]
  #   depends_on: [postgres]
  #   env_file: ./knowflow-rag/.env

  # worker:
  #   build: ./knowflow-worker
  #   depends_on:
  #     postgres: { condition: service_healthy }
  #     redis: { condition: service_healthy }
  #     minio: { condition: service_healthy }
  #   env_file: ./knowflow-worker/.env

volumes:
  postgres_data:
  redis_data:
  minio_data:
```

---

## 10. 分阶段落地路线

### 阶段 1：基础修复与工程化整理

**目标：** 消除现有 bug，规范工程配置，确保项目能干净启动，为后续开发奠定基础。

**需要修改的文件：**

| 文件 | 修改内容 |
|------|----------|
| `application.yml` | 移除硬编码 JWT secret，改为 `${JWT_SECRET}` |
| `application-dev.yml` | 新建，放开发环境默认值 |
| `application-prod.yml` | 新建，所有值走环境变量 |
| `vite.config.ts` | 修复 proxy 端口 8080 → 8081 |
| `knowflow-backend/.env.example` | 新建 |
| `knowflow-frontend/.env.example` | 新建 |
| `docker-compose.yml` | 添加 healthcheck，改用 `pgvector/pgvector:pg16` 镜像 |
| `DocumentServiceImpl.java` | 存储逻辑抽离到 `StorageService` 接口 |
| `GlobalExceptionHandler.java` | 补充 `MethodArgumentNotValidException` 处理 |
| `pom.xml` | 添加 springdoc-openapi 依赖 |
| `Dashboard.vue` | 修复统计逻辑，新增 `/api/stats/overview` 接口 |
| `KbVO.java` | 新增 `documentCount`、`doneCount` 字段 |

**具体任务清单：**
- [ ] 移除 application.yml 中的明文 JWT secret
- [ ] 创建 `application-dev.yml` 和 `application-prod.yml`
- [ ] 修复 `vite.config.ts` proxy 端口错误
- [ ] 创建 `knowflow-backend/.env.example` 和 `knowflow-frontend/.env.example`
- [ ] docker-compose.yml 替换 postgres 镜像为 pgvector 版本，添加 healthcheck
- [ ] `DocumentServiceImpl` 移除内联存储逻辑，注入 `LocalStorageService`
- [ ] 添加 springdoc-openapi，放开 SecurityConfig 中 swagger 路径
- [ ] `KbVO` 添加 `documentCount`，`KnowledgeBaseServiceImpl` 聚合查询文档数
- [ ] 新增 `GET /api/stats/overview`，修复 `Dashboard.vue` 统计逻辑
- [ ] `GlobalExceptionHandler` 补充字段校验错误处理

**验收标准：**
- `mvn spring-boot:run` 无 WARNING 启动，Swagger UI 可访问
- Dashboard 四个数字全部准确
- 不存在明文密钥（grep -r 检查）
- `vite.config.ts` proxy 与后端端口一致

**风险点：**
- 修改 storage 层抽象可能影响现有上传流程，需做集成测试

---

### 阶段 2：文档上传与 MinIO 闭环

**目标：** 文件真正存入 MinIO，Python Worker 能从 MinIO 下载文件，为解析做准备。

**需要修改的文件：**

| 文件 | 修改内容 |
|------|----------|
| `MinioStorageServiceImpl.java` | 实现 `StorageService`，真实调用 MinIO SDK |
| `DocumentServiceImpl.java` | 注入 `StorageService`，上传至 MinIO |
| `DocumentController.java` | 上传响应增加 `parseTaskId` 字段 |
| `document.status` 轮询 | 前端 `DocumentManage.vue` 增加轮询逻辑 |
| `DocumentVO.java` | 增加 `chunkCount`、`parseTaskId` |

**具体任务清单：**
- [ ] 实现 `MinioStorageServiceImpl.upload()`：自动创建 bucket（不存在时），上传文件，返回 object key
- [ ] `DocumentServiceImpl.upload()` 改为存 MinIO，`file_path` 存 MinIO object key
- [ ] MinIO bucket 命名规范：`knowflow-docs`
- [ ] 前端 `DocumentUpload.vue` 显示真实上传进度条（axios `onUploadProgress`）
- [ ] 前端 `DocumentManage.vue` 文档状态轮询（每 3s，DONE/FAILED 停止）
- [ ] 状态图标：UPLOADING/PARSING/EMBEDDING/DONE/FAILED 分别对应不同颜色图标

**验收标准：**
- 上传 PDF 后，MinIO Console（localhost:9001）可以看到文件
- 前端显示上传进度条
- 文档状态图标正确渲染

**风险点：**
- MinIO 首次启动无 bucket，需在 `MinioStorageServiceImpl` 初始化时自动创建

---

### 阶段 3：Python Worker 文档解析与切片

**目标：** 上传文档后，Worker 自动完成解析和切片，`document_chunk` 表有真实数据。

**需要新建的文件：**

```
knowflow-worker/
├── requirements.txt
├── .env.example
├── main.py
├── worker.py
├── config.py
├── parser/pdf_parser.py
├── parser/docx_parser.py
├── parser/markdown_parser.py
├── parser/txt_parser.py
├── chunker/token_chunker.py
├── embedder/openai_embedder.py
├── db/postgres.py
└── storage/minio_client.py
```

**具体任务清单：**
- [ ] 搭建 Python Worker 项目骨架（venv / requirements.txt）
- [ ] 实现 Redis BLPOP 任务消费循环
- [ ] 实现 PDF 解析（pypdf2 / pdfplumber）
- [ ] 实现 DOCX 解析（python-docx）
- [ ] 实现 MD / TXT 解析
- [ ] 实现按 token 切片（tiktoken）
- [ ] 实现 Embedding 生成（OpenAI / Ollama，可配置）
- [ ] 实现 `document_chunk` 批量写入（psycopg2 executemany）
- [ ] 更新 `document.status` 和 `parse_task.status` 流转逻辑
- [ ] 添加 Worker 异常处理（解析失败 → status=FAILED，记录 error_message）

**验收标准：**
- 上传 PDF 后，约 30s 内 `document.status` 变为 DONE
- `document_chunk` 表有真实内容数据
- embedding 字段为非空向量数组（此阶段可先存 TEXT，下阶段改 VECTOR 类型）
- Worker 日志清晰打印每个阶段耗时

**风险点：**
- 大 PDF（>10MB）切片数量多，embedding API 并发调用可能触发限速，需要加 batch + retry

---

### 阶段 4：pgvector 与 embedding 检索

**目标：** `document_chunk.embedding` 改为真实 VECTOR 类型，pgvector 检索可用。

**需要修改的文件：**

| 文件 | 修改内容 |
|------|----------|
| `sql/init.sql` | 添加 `CREATE EXTENSION IF NOT EXISTS vector`，embedding 改为 `VECTOR(1536)` |
| `docker-compose.yml` | postgres 镜像改为 `pgvector/pgvector:pg16` |
| `knowflow-worker/db/postgres.py` | 使用 `pgvector` Python 库写入向量 |
| `DocumentChunk.java` | embedding 字段类型改为 String（后端暂不查询，由 Go 服务查） |

**具体任务清单：**
- [ ] 将 `postgres:16-alpine` 替换为 `pgvector/pgvector:pg16`
- [ ] 更新 `init.sql`：`CREATE EXTENSION vector`，embedding 列改为 `VECTOR(1536)`
- [ ] Python Worker 安装 `pgvector` 库，改用向量类型写入
- [ ] 手动验证向量检索 SQL：`SELECT ... ORDER BY embedding <=> $1 LIMIT 5`
- [ ] 在数据量 >1000 条时创建 IVFFlat 索引
- [ ] 验证检索结果相关性（对上传内容提问，看 score > 0.7 的片段是否相关）

**验收标准：**
- `\dx` 显示 vector 扩展已安装
- `document_chunk.embedding` 类型为 `vector(1536)`
- 手动 SQL 检索返回相关片段，score > 0.7

**风险点：**
- pgvector 镜像与原 init.sql 的 schema 版本需要同步，注意数据库重建顺序

---

### 阶段 5：Go RAG Service 真正问答

**目标：** `RagClient` 真实调用 Go 服务，问答返回真实内容和引用来源。

**需要新建的项目：**
```
knowflow-rag/   (Go 服务)
```

**需要修改的文件：**

| 文件 | 修改内容 |
|------|----------|
| `RagClient.java` | 改为 HTTP 调用 Go `/rag/ask`，超时 30s |
| `ChatController.java` | 响应 sources 字段 |
| `ChatMessageVO.java` | 新增 `sources` 字段 |
| `chat_message` 表 | 新增 `sources JSONB` 字段 |
| `ChatServiceImpl.java` | 将 sources 持久化至 chat_message |

**具体任务清单：**
- [ ] 搭建 Go RAG Service 项目骨架（`go mod init knowflow-rag`）
- [ ] 实现 `/health` 端点
- [ ] 实现 embedding 查询（调用 OpenAI / Ollama embedding API）
- [ ] 实现 pgvector TopK 检索（pgx 驱动）
- [ ] 实现 Prompt Builder（system prompt + context + question）
- [ ] 实现 LLM Provider 接口，先接入 DeepSeek
- [ ] 实现 `/rag/ask` 同步接口
- [ ] `RagClient.java` 改为 `RestTemplate` / `WebClient` HTTP 调用
- [ ] `ChatServiceImpl` 解析 sources，写入 `chat_message.sources`
- [ ] `ChatMessageVO` 新增 `sources` 字段，前端 `Chat.vue` 接入真实 sources

**验收标准：**
- 上传 PDF → 等待解析完成 → 提问 → 收到非 mock 回答
- 回答内容与上传文档内容相关
- SourcePanel 展示真实引用片段和 score
- 问答历史保存 sources 到数据库

**风险点：**
- LLM API 网络延迟高，需设置合理超时（30s），前端要有等待态
- pgvector 检索时如向量维度不匹配会报错，需严格对齐 embedding 维度

---

### 阶段 6：SSE 流式输出与前端体验增强

**目标：** 聊天页面流式显示生成内容，达到 ChatGPT 级用户体验。

**需要修改的文件：**

| 文件 | 修改内容 |
|------|----------|
| `knowflow-rag/internal/handler/rag.go` | 实现 `/rag/ask/stream` SSE 端点 |
| `ChatController.java` | 新增 `GET /api/chat/ask/stream` SSE 端点（Spring `SseEmitter`） |
| `Chat.vue` | 接入 `EventSource`，实现流式渲染 |
| `SourcePanel.vue` | 接收真实 sources 数组展示 |

**具体任务清单：**
- [ ] Go 服务实现 `/rag/ask/stream`：逐 token 写 SSE，最后写 sources event
- [ ] Spring Boot 新增 `GET /api/chat/ask/stream`，用 `SseEmitter` 透传 Go SSE
- [ ] 前端 `Chat.vue` 改用 `EventSource` 发起流式请求
- [ ] 实现打字机效果（token 追加到当前消息 content）
- [ ] SSE 结束时渲染 SourcePanel
- [ ] 流式发送中禁用输入框，显示停止按钮
- [ ] 消息框支持 Markdown 渲染（marked.js + DOMPurify）
- [ ] 代码块语法高亮（highlight.js）
- [ ] `Settings.vue` 添加模型配置面板（Provider、API Key、Base URL）

**验收标准：**
- 问答时文字逐字出现，无整块刷新
- 回答完毕后 SourcePanel 自动展示引用来源
- 刷新页面历史消息仍可展示（含 sources）
- 代码块有语法高亮

**风险点：**
- Spring Boot 做 SSE 中继时需注意 timeout 配置（默认 30s 可能不够）
- 部分 nginx / 反向代理默认缓冲 SSE，需配置 `X-Accel-Buffering: no`

---

## 11. TODO Roadmap

### 阶段 1：基础修复（约 1-2 天）

- [ ] 移除 `application.yml` 中明文 JWT secret，改为 `${JWT_SECRET}`
- [ ] 新建 `application-dev.yml`，将开发默认值放入
- [ ] 新建 `application-prod.yml`，所有配置走环境变量
- [ ] 创建 `knowflow-backend/.env.example`
- [ ] 创建 `knowflow-frontend/.env.example`
- [ ] 修复 `vite.config.ts` proxy 端口 8080 → 8081
- [ ] 添加 springdoc-openapi 依赖，放开 Swagger 路径
- [ ] 重构 `DocumentServiceImpl`：存储逻辑走 `StorageService` 接口
- [ ] 实现 `LocalStorageServiceImpl`（开发用）
- [ ] 新增 `GET /api/stats/overview` 端点
- [ ] 修复 `Dashboard.vue` 统计逻辑（调用 overview 接口）
- [ ] `KbVO` 新增 `documentCount`、`doneCount`，后端聚合查询
- [ ] `GlobalExceptionHandler` 补充字段级错误信息返回
- [ ] `docker-compose.yml` 添加 healthcheck
- [ ] `docker-compose.yml` 替换 postgres 镜像为 `pgvector/pgvector:pg16`

### 阶段 2：MinIO 闭环（约 1-2 天）

- [ ] 实现 `MinioStorageServiceImpl.upload()`，自动创建 bucket
- [ ] `DocumentServiceImpl` 改为调用 MinIO 存储
- [ ] 前端上传组件添加进度条
- [ ] 前端文档列表添加状态图标和 3s 轮询逻辑
- [ ] `DocumentVO` 新增 `chunkCount`、`parseTaskId`

### 阶段 3：Python Worker（约 3-5 天）

- [ ] 初始化 `knowflow-worker` 项目，创建 venv 和 requirements.txt
- [ ] 实现 Redis BLPOP 消费循环（main.py / worker.py）
- [ ] 实现 `parser/pdf_parser.py`（pypdf2 / pdfplumber）
- [ ] 实现 `parser/docx_parser.py`（python-docx）
- [ ] 实现 `parser/markdown_parser.py`
- [ ] 实现 `parser/txt_parser.py`
- [ ] 实现 `chunker/token_chunker.py`（tiktoken，512 token，64 overlap）
- [ ] 实现 `embedder/openai_embedder.py`
- [ ] 实现 `db/postgres.py`（psycopg2 连接池，批量写入 document_chunk）
- [ ] 实现 `storage/minio_client.py`（从 MinIO 下载文件）
- [ ] 实现文档状态流转（PARSING → EMBEDDING → DONE/FAILED）
- [ ] 添加 Worker 异常处理和 error_message 记录
- [ ] 添加 `.env.example` 和启动说明

### 阶段 4：pgvector（约 1 天）

- [ ] 更新 `init.sql`：添加 vector 扩展，embedding 改为 `VECTOR(1536)`
- [ ] Python Worker 安装 `pgvector` Python 库，改用向量类型写入
- [ ] 手动验证向量检索 SQL 可运行
- [ ] 数据量 >1000 时创建 IVFFlat 索引

### 阶段 5：Go RAG Service（约 3-5 天）

- [ ] 初始化 `knowflow-rag` Go 项目（`go mod init`）
- [ ] 实现 `GET /health` 端点
- [ ] 实现 embedding API 调用（HTTP client 封装）
- [ ] 实现 pgvector TopK 检索（pgx 驱动）
- [ ] 实现 Prompt Builder
- [ ] 实现 LLM Provider 接口
- [ ] 实现 DeepSeek Provider（Chat completions）
- [ ] 实现 `POST /rag/ask` 同步接口
- [ ] `RagClient.java` 改为 HTTP 调用 Go `/rag/ask`
- [ ] `chat_message` 表新增 `sources JSONB` 列
- [ ] `ChatMessageVO` 新增 `sources` 字段
- [ ] `ChatServiceImpl` 持久化 sources
- [ ] 前端 `Chat.vue` SourcePanel 接入真实 sources

### 阶段 6：SSE 流式（约 2-3 天）

- [ ] Go 服务实现 `/rag/ask/stream` SSE 端点
- [ ] Spring Boot 新增 `GET /api/chat/ask/stream`（SseEmitter 透传）
- [ ] 前端 `Chat.vue` 改用 EventSource 实现流式渲染
- [ ] 实现打字机效果（逐 token 追加）
- [ ] 消息框接入 marked.js Markdown 渲染
- [ ] 代码块接入 highlight.js 语法高亮
- [ ] `Settings.vue` 添加模型配置面板
- [ ] `chat_message` 历史展示 sources

---

## 12. 简历项目描述

### 12.1 项目简介

**KnowFlow** 是一个面向个人和团队的智能知识库问答平台，基于 RAG（检索增强生成）技术实现"上传文档即可问答"。用户上传 PDF/DOCX 等文档后，系统自动解析并向量化，问答时通过 pgvector 语义检索最相关文档片段，结合大语言模型生成有引用来源的准确回答。

### 12.2 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + Pinia |
| 主后端 | Spring Boot 3 + MyBatis-Plus + PostgreSQL + Redis + MinIO |
| RAG 引擎 | Go + pgvector + DeepSeek / OpenAI API |
| 文档处理 | Python + pypdf + tiktoken + Embedding API |
| 基础设施 | Docker Compose（PostgreSQL pgvector / Redis / MinIO） |

### 12.3 个人负责模块

- 系统整体架构设计，拆分 Spring Boot / Go RAG Engine / Python Worker 三层职责
- Spring Boot 主后端全栈开发：用户认证（JWT + BCrypt + Spring Security）、知识库 / 文档 / 会话 CRUD、MinIO 文件存储集成
- Go RAG Service 设计与实现：pgvector TopK 检索、LLM Provider 抽象、SSE 流式输出
- Python Worker 文档处理流水线：PDF/DOCX 解析、按 token 切片、embedding 生成与写入
- Vue 3 前端全页面开发：聊天页面 SSE 流式渲染、SourcePanel 引用来源展示、文档上传进度与状态轮询

### 12.4 技术亮点

1. **多服务协作架构**：Spring Boot 负责业务编排，Go 专注 RAG 性能路径，Python 异步处理 CPU 密集型任务，各司其职
2. **异步文档处理流水线**：上传触发 Redis 队列，Python Worker 消费解析，文档状态机（UPLOADED → PARSING → EMBEDDING → DONE）可观测
3. **向量检索与引用溯源**：pgvector 余弦相似度检索，问答结果携带具体文档片段和相似度分数，消除 LLM 幻觉风险
4. **SSE 流式问答**：Go 服务 token 级 SSE 输出，Spring Boot 中继，前端打字机渲染，用户体验对标主流 AI 产品
5. **LLM Provider 抽象**：Go 服务通过接口隔离 DeepSeek / OpenAI / Ollama，切换模型只需修改配置

### 12.5 可量化成果描述模板

以下为模板，实际数字在开发完成后替换：

- 支持上传 PDF / DOCX / Markdown / TXT，单文档最大 **50MB**，自动切分为 **512 token** 片段并向量化
- pgvector 余弦相似度检索 TopK=5，在 **X 万**片段数据集上检索延迟 < **Xms**
- 端到端问答延迟（检索 + LLM 生成首 token）< **Xs**
- SSE 流式输出，首 token 延迟 < **Xs**，用户等待感知显著优于轮询方案
- 系统支持多知识库隔离，单用户可创建 **N** 个知识库，文档数据完全按知识库 kbId 隔离检索

---

*文档维护者：KnowFlow 团队 · 最后更新：2026-05-23*
