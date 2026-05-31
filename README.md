# KnowFlow

> 基于 RAG（Retrieval-Augmented Generation）的智能知识库问答平台

用户可以注册登录、创建知识库、上传文档、追踪文档解析进度，并在指定知识库中进行 AI 驱动的智能问答。当前版本补齐了多服务 Docker 部署、真实 sources 校验、Dashboard 统计和轻量 Agent 工作流。

---

## 架构总览

```
┌──────────────────────────────────────────────────────────────┐
│  Browser (Vue 3 + TypeScript + Element Plus)                 │
└──────────────────────────┬───────────────────────────────────┘
                           │ REST + SSE
┌──────────────────────────▼───────────────────────────────────┐
│  Spring Boot 3  (Port 8081)                                  │
│  JWT Auth · MyBatis-Plus · 业务编排 · RAG/Agent API          │
└──────┬──────────────┬────────────────┬──────────────────────┘
       │              │                │
┌──────▼──────┐ ┌─────▼──────┐ ┌──────▼──────┐
│ PostgreSQL  │ │  Redis 7   │ │    MinIO    │
│  16+vector  │ │ 队列/缓存   │ │  对象存储    │
└──────▲──────┘ └─────▲──────┘ └──────▲──────┘
       │              │                │
┌──────┴──────┐ ┌─────┴──────┐        │
│ Python      │ │ Go RAG     │        │
│ Worker      │ │ Service    │        │
│ 解析·切片·   │ │ 检索·生成·  │        │
│ embedding   │ │ Agent·SSE  │        │
└─────────────┘ └────────────┘        │
       │                              │
       └──────────────────────────────┘
```

---

## 项目结构

```
KnowFlow/
├── knowflow-backend/           # Spring Boot 3 后端
│   ├── src/main/java/com/knowflow/
│   ├── sql/init.sql            # 数据库初始化（含 pgvector）
│   ├── docker-compose.yml      # PostgreSQL + Redis + MinIO
│   ├── API.md                  # 完整接口文档
│   ├── Dockerfile
│   └── pom.xml
├── knowflow-frontend/          # Vue 3 前端
│   ├── src/
│   ├── Dockerfile
│   └── package.json
├── knowflow-worker-python/     # Python 文档解析 Worker
│   ├── app/                    # 队列消费 · 解析 · 切片 · embedding
│   ├── requirements.txt
│   └── Dockerfile
├── knowflow-rag-go/            # Go RAG 检索与生成服务
│   ├── cmd/rag-server/
│   ├── internal/               # handler · service · retriever · llm · prompt
│   ├── Dockerfile
│   └── go.mod
├── scripts/
│   ├── dev-infra-up.sh         # 启动 Docker 基础设施并执行本地迁移
│   ├── dev-migrate-db.sh       # 对旧本地数据库补齐兼容字段/索引
│   ├── smoke-e2e.sh            # 上传 → 解析 → 检索 → SSE 问答链路检查
│   └── seed-demo-data.sh       # Demo 数据
├── docker-compose.yml          # 完整多服务本地部署
├── .env.example                # 全局环境变量模板
├── docs/
│   └── OPTIMIZATION_PLAN.md    # 完整优化规划文档
└── README.md
```

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3.4 · TypeScript 5.3 · Vite 5 · Element Plus 2.6 · Pinia · Axios |
| 主后端 | Java 17 · Spring Boot 3.2.5 · MyBatis-Plus 3.5.6 · JWT · BCrypt · Swagger |
| RAG 引擎 | Go 1.21 · Gin · pgvector 检索 · LLM Provider 抽象 (mock/deepseek/openai/ollama) |
| 文档处理 | Python 3.12 · Redis Queue · pypdf · python-docx · 文本切片 · Embedding |
| 数据库 | PostgreSQL 16 + pgvector · Redis 7 · MinIO |
| 工程化 | Docker Compose · 多 Profile 配置 · 环境变量注入 · Health Check |

---

## 快速启动

### 前置条件

- JDK 17+ · Node.js 18+ · Go 1.21+ · Python 3.10+ · Docker & Docker Compose

### 1. 一键启动完整链路

```bash
docker compose up --build
```

这会启动 PostgreSQL 16 + pgvector、Redis、MinIO、MinIO bucket 初始化、Spring Boot、Go RAG、Python Worker 和 Vue Preview。

如果本机已经有 `knowflow-postgres` / `knowflow-redis` / `knowflow-minio` 等旧容器在运行，可以使用隔离 smoke 环境，避免端口和容器名冲突：

```bash
docker compose -p knowflow-smoke -f docker-compose.yml -f docker-compose.smoke.yml up --build -d
BACKEND_URL=http://localhost:18081 RAG_URL=http://localhost:18090 ./scripts/smoke-e2e.sh
```

### 2. 或手动启动基础设施

```bash
cd knowflow-backend
docker compose up -d
```

启动 PostgreSQL 16 + pgvector（5432）、Redis 7（6379）、MinIO（9000/9001），并自动建表。

如果本机已有旧数据卷，建议运行兼容迁移：

```bash
cd ..
./scripts/dev-migrate-db.sh
```

也可以直接使用：

```bash
./scripts/dev-infra-up.sh
```

它会启动 Docker 基础设施并自动执行本地兼容迁移。

### 3. 启动后端

```bash
cd knowflow-backend
mvn spring-boot:run
# → http://localhost:8081
# → Swagger UI: http://localhost:8081/swagger-ui.html
```

### 4. 启动前端

```bash
cd knowflow-frontend
npm install && npm run dev
# → http://localhost:5173
```

### 5. 启动 Python Worker（完整 RAG 闭环必需）

```bash
cd knowflow-worker-python
pip install -r requirements.txt
python3 -m app.main
```

### 6. 启动 Go RAG Service（完整 RAG 闭环必需）

```bash
cd knowflow-rag-go
go mod tidy && go run cmd/rag-server/main.go
# → http://localhost:8090
```

---

## API 接口总览

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|:----:|
| POST | `/api/auth/register` | 用户注册 | |
| POST | `/api/auth/login` | 登录，返回 JWT | |
| GET | `/api/auth/me` | 当前用户信息 | ✓ |
| POST | `/api/kb` | 创建知识库 | ✓ |
| GET | `/api/kb/list` | 知识库列表 | ✓ |
| GET | `/api/kb/{id}` | 知识库详情 | ✓ |
| PUT | `/api/kb/{id}` | 更新知识库 | ✓ |
| DELETE | `/api/kb/{id}` | 删除知识库 | ✓ |
| POST | `/api/document/upload` | 上传文档 | ✓ |
| GET | `/api/document/list?kbId=` | 文档列表 | ✓ |
| GET | `/api/document/{id}` | 文档详情 | ✓ |
| DELETE | `/api/document/{id}` | 删除文档 | ✓ |
| GET | `/api/document/{id}/status` | 解析状态 | ✓ |
| POST | `/api/chat/session` | 创建会话 | ✓ |
| GET | `/api/chat/session/list?kbId=` | 会话列表 | ✓ |
| POST | `/api/chat/ask` | 智能问答（返回 answer + sources） | ✓ |
| POST | `/api/chat/ask/stream` | SSE 流式智能问答 | ✓ |
| GET | `/api/chat/history?sessionId=` | 聊天历史 | ✓ |
| POST | `/api/agent/ask` | Agent 模式问答 | ✓ |
| POST | `/api/agent/ask/stream` | Agent SSE 流式问答 | ✓ |
| GET | `/api/health` | 后端依赖健康检查 | |

完整接口文档见 [knowflow-backend/API.md](knowflow-backend/API.md)。

---

## 前端页面

| 路由 | 页面 | 功能 |
|------|------|------|
| `/login` | 登录 | JWT 登录 |
| `/register` | 注册 | 用户注册 |
| `/dashboard` | 工作台 | 统计概览 |
| `/kb` | 知识库列表 | 增删改查 |
| `/kb/:id` | 知识库详情 | 文档列表、上传、问答入口 |
| `/documents` | 文档管理 | 状态追踪 |
| `/chat` | 智能问答 | 三栏布局（会话/对话/来源引用） |
| `/settings` | 系统设置 | 用户信息 |

---

## 状态流转

```
文档上传 → UPLOADED
  ↓ (Worker 接手)
PARSING → EMBEDDING → DONE
  ↓ (失败)
FAILED
```

---

## 健康检查与 Smoke Test

```bash
# 后端依赖检查：database / redis / rag / storage
curl http://localhost:8081/api/health

# Go RAG 配置与服务检查
curl http://localhost:8090/health

# Python Worker 配置检查
cd knowflow-worker-python
python3 -m app.main --check
```

服务全部启动后，可以跑一条真实链路：

```bash
./scripts/smoke-e2e.sh
```

脚本会自动注册临时用户、创建知识库、上传测试文档、等待 Worker 解析完成，然后调用 `/api/chat/ask/stream` 验证 SSE token/sources/done 事件，并拒绝 mock sources。

隔离 smoke 环境默认使用 `18081`（Backend）、`18090`（Go RAG）、`15173`（Frontend），不会占用常规开发端口。

Demo 数据：

```bash
./scripts/seed-demo-data.sh
```

---

## Agent 模式

Chat 页面支持普通 RAG 与 Agent 两种模式：

- 普通 RAG：调用 `/api/chat/ask/stream`，返回流式 answer 与 sources。
- Agent 模式：调用 `/api/agent/ask/stream`，返回 answer、sources、intent、confidence、trace 和 latencyMs。
- Citation Guard 会在 sources 为空时直接返回“知识库中未找到足够依据，无法回答该问题。”，不会调用 LLM 编造答案。
- sources 较少或最高 score 较低时会降低 confidence，并在弱依据回答前提示“当前知识库依据较弱，仅供参考。”

---

## Demo 流程

1. 注册并登录 KnowFlow。
2. 创建知识库，例如“项目资料库”。
3. 上传 TXT / MD / PDF / DOCX 文档。
4. 在文档管理页观察状态从 `UPLOADED → PARSING → EMBEDDING → DONE`。
5. 进入 Chat，先用普通 RAG 提问并查看 sources。
6. 切换 Agent 模式，再提问“帮我总结这份文档”或“给我一个学习计划”，查看 intent、confidence 和 trace。
7. 打开 Dashboard，确认知识库、文档、chunk、问答次数、最近文档、最近会话和失败任务均来自后端统计接口。

---

## CI

已新增 GitHub Actions 工作流 [`.github/workflows/ci.yml`](.github/workflows/ci.yml)，在 push 到 `main` 或 PR 时运行：

- Backend: `mvn test`
- Frontend: `npm ci && npm run build`
- Go RAG: `go test ./...`
- Python Worker: `python -m compileall app` 和 `python -m app.main --check`

---

## 后续规划

| 功能 | 状态 |
|------|:----:|
| Python Worker 文档解析 + 切片 | ✅ 已实现 |
| Go RAG Service 检索 + 生成 | ✅ 已实现 |
| pgvector 向量检索 | ✅ 已实现（支持 keyword fallback） |
| DeepSeek / OpenAI / Ollama Provider | ✅ 已实现 |
| SSE 流式输出 | ✅ 前后端贯通 |
| 引用来源展示 | ✅ 前后端贯通 |
| 文档解析进度实时刷新 | ✅ 已实现 |
| Agent 工作流 | ✅ Router/Retriever/Answer/Citation Guard |
| MinIO 存储切换 | ✅ 已实现（配置切换） |
| 端到端 smoke test | ✅ 已实现 |

---

## 简历亮点

- 多服务 RAG 架构：Spring Boot 负责业务编排，Go RAG 负责检索生成，Python Worker 负责异步文档处理。
- 可信问答闭环：pgvector 按 `kbId` 隔离检索，返回真实 sources，Citation Guard 在证据不足时降级。
- 工程化：完整 Docker Compose、健康检查、环境变量、Smoke Test 和 demo 数据脚本。
- Agent 化：支持意图路由、检索规划、结构化回答、引用守卫和 trace。

---

## 环境变量

所有敏感配置均通过环境变量注入，详见各模块的 `.env.example`：

- 根目录 [`.env.example`](.env.example) — 全局配置
- [`knowflow-backend/.env.example`](knowflow-backend/.env.example) — 后端
- [`knowflow-frontend/.env.example`](knowflow-frontend/.env.example) — 前端
- [`knowflow-worker-python/.env.example`](knowflow-worker-python/.env.example) — Worker
- [`knowflow-rag-go/.env.example`](knowflow-rag-go/.env.example) — Go RAG

---

## License

MIT
