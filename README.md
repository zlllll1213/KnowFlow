# KnowFlow

> 基于 RAG（Retrieval-Augmented Generation）的智能知识库问答平台

用户可以注册登录、创建知识库、上传文档、追踪文档解析进度，并在指定知识库中进行 AI 驱动的智能问答。

---

## 架构总览

```
┌─────────────────────────────────────────────────────────────────┐
│  Browser                                                        │
│  Vue 3 + TypeScript + Element Plus  (Port 5173)                 │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP / REST
┌────────────────────────────▼────────────────────────────────────┐
│  Spring Boot 3 Backend  (Port 8081)                             │
│  JWT Auth · MyBatis-Plus · REST API                             │
└──────────┬─────────────────┬───────────────────┬───────────────┘
           │                 │                   │
    ┌──────▼──────┐   ┌──────▼──────┐   ┌────────▼────────┐
    │ PostgreSQL  │   │   Redis 7   │   │     MinIO       │
    │     16      │   │ Cache/Queue │   │ Object Storage  │
    └─────────────┘   └─────────────┘   └─────────────────┘
                             │
              ┌──────────────▼──────────────┐
              │  Python Worker (计划中)      │
              │  文档解析 · 向量化           │
              └──────────────┬──────────────┘
                             │
              ┌──────────────▼──────────────┐
              │  Go RAG 服务 (计划中)        │
              │  pgvector 检索 · LLM 生成   │
              └─────────────────────────────┘
```

---

## 项目结构

```
KnowFlow/
├── knowflow-backend/          # Spring Boot 3 后端
│   ├── src/main/java/com/knowflow/
│   ├── sql/init.sql           # 数据库初始化脚本
│   ├── docker-compose.yml     # PostgreSQL + Redis + MinIO
│   ├── API.md                 # 完整接口文档
│   └── pom.xml
├── knowflow-frontend/         # Vue 3 前端
│   ├── src/
│   └── package.json
├── 前端.md                    # 前端开发规格说明
├── 后端.md                    # 后端开发规格说明
└── README.md
```

---

## 技术栈

### 后端

| 组件 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| 构建 | Maven |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | PostgreSQL 16 |
| 缓存 / 消息队列 | Redis 7 |
| 对象存储 | MinIO（预留） |
| 鉴权 | JWT (jjwt 0.12.5) |
| 密码加密 | BCrypt |

### 前端

| 组件 | 技术 |
|------|------|
| 框架 | Vue 3.4 |
| 语言 | TypeScript 5.3 |
| 构建 | Vite 5 |
| UI 组件库 | Element Plus 2.6 |
| 状态管理 | Pinia 2.1 |
| 路由 | Vue Router 4.3 |
| HTTP 客户端 | Axios 1.6 |

---

## 快速启动

### 前置条件

- JDK 17+
- Node.js 18+
- Docker & Docker Compose

### 第一步：启动基础设施

```bash
cd knowflow-backend
docker compose up -d
```

这会启动：
- **PostgreSQL 16** — 端口 `5432`，并自动执行 `sql/init.sql` 建表
- **Redis 7** — 端口 `6379`
- **MinIO** — API `9000` / Console `9001`（管理员：`minioadmin / minioadmin123`）

### 第二步：启动后端

```bash
cd knowflow-backend
mvn spring-boot:run
```

后端运行在 `http://localhost:8081`。

### 第三步：启动前端

```bash
cd knowflow-frontend
npm install
npm run dev
```

前端运行在 `http://localhost:5173`，API 请求自动代理至后端。

### 接口快速验证

```bash
# 注册
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456","email":"test@knowflow.com"}'

# 登录（返回 JWT Token）
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 创建知识库（将 YOUR_TOKEN 替换为登录返回的 token）
curl -X POST http://localhost:8081/api/kb \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"name":"我的知识库","description":"测试知识库"}'
```

---

## API 接口总览

| 方法 | 路径 | 说明 | 需要认证 |
|------|------|------|:--------:|
| POST | `/api/auth/register` | 用户注册 | |
| POST | `/api/auth/login` | 登录，返回 JWT | |
| GET | `/api/auth/me` | 获取当前用户信息 | ✓ |
| POST | `/api/kb` | 创建知识库 | ✓ |
| GET | `/api/kb/list` | 知识库列表 | ✓ |
| GET | `/api/kb/{id}` | 知识库详情 | ✓ |
| PUT | `/api/kb/{id}` | 更新知识库 | ✓ |
| DELETE | `/api/kb/{id}` | 删除知识库 | ✓ |
| POST | `/api/document/upload` | 上传文档 | ✓ |
| GET | `/api/document/list?kbId=` | 文档列表 | ✓ |
| GET | `/api/document/{id}` | 文档详情 | ✓ |
| DELETE | `/api/document/{id}` | 删除文档 | ✓ |
| GET | `/api/document/{id}/status` | 文档解析状态 | ✓ |
| POST | `/api/chat/session` | 创建聊天会话 | ✓ |
| GET | `/api/chat/session/list?kbId=` | 会话列表 | ✓ |
| POST | `/api/chat/ask` | 智能提问 | ✓ |
| GET | `/api/chat/history?sessionId=` | 聊天历史 | ✓ |

完整接口文档见 [knowflow-backend/API.md](knowflow-backend/API.md)。

---

## 前端页面

| 路由 | 页面 | 功能说明 |
|------|------|----------|
| `/login` | 登录 | JWT 登录，本地持久化 token |
| `/register` | 注册 | 用户注册，密码一致性校验 |
| `/dashboard` | 工作台 | 统计概览、最近文档 / 会话 |
| `/kb` | 知识库列表 | 增删改查知识库 |
| `/kb/:id` | 知识库详情 | 文档列表、上传文档、进入问答 |
| `/documents` | 文档管理 | 跨知识库文档管理、解析状态追踪 |
| `/chat` | 智能问答 | 三栏布局（会话列表 / 对话 / 来源引用） |
| `/settings` | 系统设置 | 用户信息、功能规划 |

---

## 统一响应格式

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

| 错误码范围 | 含义 |
|-----------|------|
| `0` | 成功 |
| `40xxx` | 业务错误（参数校验、权限、资源不存在等） |
| `50xxx` | 服务器内部错误 |

---

## 后续规划

| 功能 | 说明 |
|------|------|
| Python Worker | 消费 Redis 队列 `knowflow:parse:queue`，完成文档解析与向量化 |
| Go RAG 服务 | 替换当前 mock 回答，通过 HTTP 调用 Go 端 RAG 检索与生成 |
| pgvector 检索 | 启用 PostgreSQL pgvector 扩展，实现向量相似度检索 |
| SSE 流式输出 | 聊天接口支持 Server-Sent Events 逐字流式返回 |
| 引用来源展示 | 将召回的 `DocumentChunk` 在右侧 SourcePanel 实时展示 |
| MinIO 集成 | 将本地文件存储切换至 MinIO 对象存储 |
| 解析状态实时推送 | 通过轮询或 WebSocket 推送 `PARSING → EMBEDDING → DONE` 状态变化 |

---

## License

MIT
