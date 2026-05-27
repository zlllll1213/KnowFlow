# KnowFlow Frontend

KnowFlow 智能知识库问答平台 — Vue 3 前端应用。

---

## 技术栈

| 技术 | 版本 |
|------|------|
| Vue 3 | ^3.4 |
| TypeScript | ^5.3 |
| Vite | ^5.1 |
| Element Plus | ^2.6 |
| Pinia | ^2.1 |
| Vue Router | ^4.3 |
| Axios | ^1.6 |

---

## 目录结构

```
src/
├── api/          # Axios 封装 + 各模块接口定义
├── assets/       # 全局样式、静态资源
├── components/   # 公共组件
├── layouts/      # 页面布局（侧边栏 + 顶栏）
├── router/       # 路由配置 + 路由守卫
├── stores/       # Pinia 状态管理
├── types/        # TypeScript 类型定义
├── utils/        # 工具函数（token 管理等）
└── views/        # 页面组件
    ├── Login.vue
    ├── Register.vue
    ├── Dashboard.vue
    ├── KnowledgeBaseList.vue
    ├── KnowledgeBaseDetail.vue
    ├── DocumentManage.vue
    ├── Chat.vue
    └── Settings.vue
```

---

## 本地启动

### 前置条件

- Node.js 18+
- 后端服务运行在 `http://localhost:8081`（见 [../knowflow-backend/README.md](../knowflow-backend/README.md)）

### 安装与启动

```bash
# 安装依赖
npm install

# 开发模式（默认端口 5173）
npm run dev

# 生产构建
npm run build

# 预览生产构建
npm run preview
```

---

## 环境变量

在项目根目录创建 `.env.local` 文件覆盖默认配置：

```env
VITE_API_BASE_URL=http://localhost:8081
```

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `VITE_API_BASE_URL` | 后端 API 地址 | `http://localhost:8081` |

开发模式下 Vite 会通过 proxy 将 `/api` 请求转发至后端，无需手动配置跨域。

---

## 页面功能

| 路由 | 页面 | 功能说明 |
|------|------|----------|
| `/login` | 登录 | JWT 登录，自动持久化 token |
| `/register` | 注册 | 用户注册，密码一致性校验 |
| `/dashboard` | 工作台 | 统计概览、最近文档 / 聊天会话 |
| `/kb` | 知识库列表 | 增删改查知识库 |
| `/kb/:id` | 知识库详情 | 文档列表、上传文档、进入问答 |
| `/documents` | 文档管理 | 跨知识库文档管理、解析状态追踪 |
| `/chat` | 智能问答 | 三栏布局（会话列表 / 对话 / 来源引用），支持 SSE 逐字输出 |
| `/settings` | 系统设置 | 用户信息、功能规划 |

---

## 后端接口依赖

| 接口 | 说明 |
|------|------|
| `POST /api/auth/login` | 登录获取 JWT |
| `POST /api/auth/register` | 用户注册 |
| `GET /api/kb/list` | 知识库列表 |
| `POST /api/document/upload` | 上传文档（multipart/form-data） |
| `GET /api/document/{id}/status` | 文档解析状态轮询 |
| `POST /api/chat/ask` | 智能问答 |
| `POST /api/chat/ask/stream` | SSE 流式智能问答 |

完整接口文档见 [../knowflow-backend/API.md](../knowflow-backend/API.md)。

---

## 当前能力

| 功能 | 状态 | 说明 |
|------|:----:|------|
| 引用来源展示 | ✅ | 后端返回的 `sources` 在 SourcePanel 展示，含 documentId/chunkIndex/score |
| 解析状态实时轮询 | ✅ | 上传后自动 3s 轮询 `PARSING → EMBEDDING → DONE`，完成后停止 |
| Agent 模式 | ✅ | Chat 页支持 Agent/RAG 切换，展示 intent/confidence/trace |
| Dashboard 真实统计 | ✅ | 后端 `/api/dashboard/stats` 聚合数据 |
| Markdown 安全渲染 | ✅ | `marked` + `DOMPurify` + `highlight.js` |
| 上传进度条 | ✅ | `onUploadProgress` 实时进度 |
