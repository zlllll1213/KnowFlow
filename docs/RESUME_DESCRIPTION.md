# KnowFlow Resume Description

## 项目描述

KnowFlow 是一个多服务 RAG 智能知识库问答平台，支持用户注册登录、知识库管理、文档上传、异步解析切片、向量检索、流式问答、引用溯源、聊天历史和轻量 Agent 工作流。

## 技术栈

- Frontend: Vue 3, TypeScript, Vite, Element Plus, Pinia
- Backend: Java 17, Spring Boot 3, Spring Security, JWT, MyBatis-Plus
- RAG: Go, Gin, pgvector, LLM Provider abstraction
- Worker: Python, Redis queue, pypdf, python-docx, embedding pipeline
- Storage: PostgreSQL 16 + pgvector, Redis, MinIO
- Engineering: Docker Compose, health checks, smoke test

## 项目难点

- 多服务边界设计：Spring 负责业务编排，Go 负责检索生成，Python Worker 负责异步文档处理。
- RAG 可信闭环：按知识库隔离检索，返回真实 chunk sources，并通过 Citation Guard 防止无依据回答。
- 流式输出：Spring 使用 SSE 透传 Go RAG token/sources/done/error，前端实时渲染。
- 工程化：完整 compose、环境变量、健康检查、smoke test 覆盖上传到问答链路。

## 优化亮点

- 将 mock fallback 改为 fail-closed，避免假引用污染历史。
- 增加 Worker 配置检查、并发消费、路径安全和 embedding 维度校验。
- 增加 Agent 模式，支持意图识别、检索规划、回答生成、引用守卫和 trace。
- 增加 dashboard 真实统计、RAG 调用日志和最近失败任务。

## 可量化写法

- “设计并落地 Spring Boot + Go + Python Worker 多服务 RAG 架构，完成文档解析、向量检索、SSE 流式问答和引用溯源闭环。”
- “通过 Redis 队列和 Worker 状态机实现异步文档处理，支持解析失败追踪、重复任务恢复和并发消费。”
- “新增 Agent 工作流和 Citation Guard，在 sources 不足时自动降级，降低知识库问答幻觉风险。”

## 面试讲解话术

先讲架构边界，再讲数据流：上传文件进入 MinIO/本地存储，Spring 写 document 和 parse_task，Worker 消费 Redis 任务并写入 pgvector，Go RAG 按 kbId 检索并调用 LLM，Spring 保存聊天历史，前端用 SSE 实时展示 answer 和 sources。最后补充 Agent 模式如何在普通 RAG 上增加意图路由和引用守卫。
