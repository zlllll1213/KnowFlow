# KnowFlow 智能知识库前端

基于 RAG 的智能知识库问答平台前端，支持文档上传、解析状态追踪、AI 问答等功能。

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

## 目录结构

```
src/
├── api/          # Axios 封装 + 各模块接口
├── assets/       # 全局样式
├── components/   # 公共组件
├── layouts/      # 页面布局
├── router/       # 路由配置 + 守卫
├── stores/       # Pinia 状态管理
├── types/        # TypeScript 类型定义
├── utils/        # 工具函数（token）
└── views/        # 页面组件
```

## 启动命令

```bash
# 安装依赖
npm install

# 开发模式
npm run dev

# 生产构建
npm run build
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `VITE_API_BASE_URL` | 后端 API 地址 | `http://localhost:8081` |

## 页面功能说明

| 路由 | 页面 | 功能 |
|------|------|------|
| `/login` | 登录 | JWT 登录，保存 token |
| `/register` | 注册 | 用户注册，密码一致性校验 |
| `/dashboard` | 工作台 | 统计卡片、最近文档、最近会话 |
| `/kb` | 知识库列表 | 增删改查知识库 |
| `/kb/:id` | 知识库详情 | 文档列表、上传文档、进入问答 |
| `/documents` | 文档管理 | 跨知识库文档管理、状态追踪 |
| `/chat` | 智能问答 | 三栏布局（会话/对话/来源），SSE 预留 |
| `/settings` | 系统设置 | 用户信息、功能规划 |

## 后端接口依赖

后端运行于 `http://localhost:8081`，主要接口：
- `POST /api/auth/login` — 登录
- `POST /api/auth/register` — 注册
- `GET /api/kb/list` — 知识库列表
- `POST /api/document/upload` — 上传文档
- `POST /api/chat/ask` — 智能问答

详见 `../knowflow-backend/API.md`。

## 后续规划

- **SSE 流式输出**：`/api/chat/ask` 改为 SSE，前端逐字追加回答内容
- **引用来源展示**：将后端返回的 `DocumentChunk` 在右侧 SourcePanel 实时展示
- **文档解析进度实时刷新**：通过轮询或 WebSocket 推送 `PARSING → EMBEDDING → DONE` 状态变化
- **全文检索**：跨知识库关键词搜索
