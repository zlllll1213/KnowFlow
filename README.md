# KnowFlow

基于 RAG（Retrieval-Augmented Generation）的智能知识库问答平台。用户可以注册登录、创建知识库、上传文档、追踪文档解析状态，并在指定知识库中进行智能问答。

## 项目结构

```
KnowFlow/
├── knowflow-backend/          # Spring Boot 3 后端
│   ├── src/main/java/com/knowflow/
│   ├── sql/init.sql           # 数据库初始化脚本
│   ├── docker-compose.yml     # PostgreSQL + Redis + MinIO
│   ├── pom.xml
│   └── README.md
├── knowflow-frontend/         # Vue 3 前端
│   ├── src/
│   ├── package.json
│   └── README.md
├── 前端.md                    # 前端开发规格说明
├── 后端.md                    # 后端开发规格说明
└── README.md                  # 本文件
```

## 技术栈

### 后端

| 组件 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| 构建 | Maven |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | PostgreSQL 16 |
| 缓存 / 队列 | Redis 7 |
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

## 快速启动

### 前置条件

- JDK 17+
- Node.js 18+
- Docker & Docker Compose

### 1. 启动基础设施

```bash
cd knowflow-backend
docker compose up -d
```

这会启动 PostgreSQL 16（端口 5432）、Redis 7（端口 6379）、MinIO（API 9000 / Console 9001），并自动执行 `sql/init.sql` 创建表结构。

### 2. 启动后端

```bash
cd knowflow-backend
mvn spring-boot:run
```

后端运行在 `http://localhost:8081`。

### 3. 启动前端

```bash
cd knowflow-frontend
npm install
npm run dev
```

前端运行在 `http://localhost:5173`，API 请求自动代理至后端。

### 4. 快速测试

```bash
# 注册
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456","email":"test@knowflow.com"}'

# 登录（获取 token）
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 创建知识库（替换 YOUR_TOKEN）
curl -X POST http://localhost:8081/api/kb \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"name":"我的知识库","description":"测试知识库"}'
```

## 已实现接口

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/register` | 用户注册 | 否 |
| POST | `/api/auth/login` | 用户登录，返回 JWT | 否 |
| GET | `/api/auth/me` | 获取当前用户信息 | 是 |
| POST | `/api/kb` | 创建知识库 | 是 |
| GET | `/api/kb/list` | 知识库列表 | 是 |
| GET | `/api/kb/{id}` | 知识库详情 | 是 |
| PUT | `/api/kb/{id}` | 更新知识库 | 是 |
| DELETE | `/api/kb/{id}` | 删除知识库 | 是 |
| POST | `/api/document/upload` | 上传文档 | 是 |
| GET | `/api/document/list?kbId=` | 文档列表 | 是 |
| GET | `/api/document/{id}` | 文档详情 | 是 |
| DELETE | `/api/document/{id}` | 删除文档 | 是 |
| GET | `/api/document/{id}/status` | 文档解析状态 | 是 |
| POST | `/api/chat/session` | 创建聊天会话 | 是 |
| GET | `/api/chat/session/list?kbId=` | 会话列表 | 是 |
| POST | `/api/chat/ask` | 提问 | 是 |
| GET | `/api/chat/history?sessionId=` | 聊天历史 | 是 |

详见 [knowflow-backend/API.md](knowflow-backend/API.md)。

## 前端页面

| 路由 | 页面 | 功能 |
|------|------|------|
| `/login` | 登录 | JWT 登录 |
| `/register` | 注册 | 用户注册 |
| `/dashboard` | 工作台 | 统计概览、最近文档/会话 |
| `/kb` | 知识库列表 | 增删改查知识库 |
| `/kb/:id` | 知识库详情 | 文档列表、上传、进入问答 |
| `/documents` | 文档管理 | 跨知识库文档管理、状态追踪 |
| `/chat` | 智能问答 | 三栏布局（会话/对话/来源） |
| `/settings` | 系统设置 | 用户信息、功能规划 |

## 统一返回格式

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

错误码：`0` 成功 / `40xxx` 业务错误 / `50xxx` 服务器错误。

## 后续规划

- **Python Worker** —— 从 Redis 队列 `knowflow:parse:queue` 消费解析任务，完成文档解析与向量化
- **Go RAG 服务** —— 替换当前的 mock 回答，通过 HTTP 调用 Go 端的 RAG 检索与生成服务
- **pgvector 检索** —— 启用 PostgreSQL pgvector 扩展，实现向量相似度检索
- **SSE 流式输出** —— 聊天接口支持 Server-Sent Events 流式返回生成结果
- **引用来源展示** —— 将召回文档片段在右侧 SourcePanel 实时展示
- **MinIO 集成** —— 将文件存储从本地目录切换至 MinIO 对象存储
- **文档解析进度实时刷新** —— 通过轮询或 WebSocket 推送解析状态变化

## License

MIT
