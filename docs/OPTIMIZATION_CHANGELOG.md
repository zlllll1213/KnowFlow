# KnowFlow Optimization Changelog

## Staged Execution Status · 2026-05-27

当前已完成到：第三阶段（前端 Agent 模式展示与文档解析状态实时刷新）。

### 第一阶段：稳定 RAG 主链路（已完成）

- Spring `parse_task` 创建仍保留在上传事务内，Redis 入队移动到事务 `afterCommit` 后执行，避免 Worker 读到未提交任务。
- Python Worker 保持线程池并发消费，`WORKER_CONCURRENCY` 与实际行为一致，并通过 DB `claim_task` 避免重复处理。
- Worker 和 Go RAG 均增加 embedding 维度校验，错误信息包含 `expected` 与 `actual`。
- Go RAG mock embedding 按配置维度生成向量。
- Spring RAG fallback 改为 `knowflow.rag.fallback-enabled` 控制，dev 默认允许，prod 默认禁用。

### 第二阶段：轻量级 Agent 工作流（已完成）

- Go RAG Service 新增 `internal/agent`，沉淀 Router 与 Citation Guard 规则。
- Agent 链路按 Router → Retriever → Citation Guard → Answer 执行，并返回 `intent / answer / sources / confidence / trace / latencyMs`。
- Citation Guard 实现空 sources 禁止调用 LLM、低引用数 confidence 封顶、低 score 弱依据提示等防幻觉规则。
- 保留 `/agent/ask`，并新增 `/rag/agent/ask` 与 `/rag/agent/ask/stream` 兼容接口。
- Spring 与前端类型补齐 Agent `latencyMs` 透传。

### 第三阶段：前端 Agent 模式展示与解析状态刷新（已完成）

- Chat 页面保留普通 RAG / Agent 模式切换；Agent 模式下助手消息展示 Agent 标识、intent 与 confidence。
- 新增 `AgentTracePanel`，展示 Agent trace、confidence、intent 和 latency。
- SourcePanel 继续复用现有 sources 展示，右侧区域组合 sources 与 Agent trace。
- 文档管理页上传成功后按文档 ID 每 2 秒调用状态接口轮询，DONE / FAILED 后停止。
- FAILED 终态会拉取文档详情并展示 `errorMessage`；页面切换、翻页、删除和卸载都会清理轮询定时器。
- 增加最大轮询次数，避免异常状态导致无限轮询。

### 下一阶段：第四阶段（真实 Dashboard 与工程化）

- 核对并完善 `/api/dashboard/stats` 的当前用户维度统计，确保知识库、文档、chunk、会话和失败任务不跨用户。
- 前端 Dashboard 继续以统一统计接口为准，修正任何残留的前端本地聚合逻辑。
- 补充 GitHub Actions CI：Backend `mvn test`、Frontend `npm ci && npm run build`、Go `go test ./...`、Worker `python -m app.main --check` 或最小配置检查。
- 同步 README、API、Agent Design、Resume Description 文档。

## P0: 真实闭环与可信度

- Spring `RagClient` 默认关闭 mock fallback，增加连接/读取超时配置；Go RAG 不可用时返回明确错误，不再落库 mock sources。
- Spring SSE 增加发送失败保护、超时/错误回调和空回答保护。
- Go RAG 流式接口改为标准 SSE `event + data`，同时保留 `type` 字段兼容旧解析。
- Python Worker `--check` 增加 Redis、PostgreSQL、MinIO/本地存储连通性检查；`WORKER_CONCURRENCY` 改为线程池消费。
- Worker 本地存储下载增加路径归一化限制，embedding 生成后校验维度。
- Smoke test 增加真实 sources 校验，拒绝 `mock-notice.txt`。
- 新增后端、Go RAG、前端 Dockerfile 和根目录完整 `docker-compose.yml`。
- 根目录 compose 为后端、Go RAG、Worker、前端补充 healthcheck，并在前端镜像构建阶段注入 `VITE_API_BASE_URL`。

## P1: 工程质量与可维护性

- 知识库、文档、会话、聊天历史列表改为 `PageResult<T>` 分页返回，前端同步适配。
- 知识库列表、文档管理、知识库详情文档表增加分页控件。
- 统一 401 JSON 响应；500 不再向客户端暴露内部异常详情。
- 健康检查实际验证 PostgreSQL、Redis、Go RAG、MinIO bucket 或本地存储目录。
- 文档上传增加空文件、扩展名和 MIME 基础校验。
- 文档管理与知识库详情页增加解析状态轮询、失败原因 tooltip、`chunkCount` 展示。
- 新增 Dashboard 真实统计接口，前端改为展示后端聚合数据。
- Go RAG 增加 embedding/retrieve/LLM/total 分段耗时日志和资料不足降级。
- 数据库迁移补充 `parse_task.retry_count/last_error_at`、消息统计索引、`rag_call_log`。

## P2: Agent 与演示能力

- Go RAG 内置轻量 Agent 工作流：Intent Router、Retriever、Answer、Citation Guard、Trace。
- 新增 Spring `/api/agent/ask` 与 `/api/agent/ask/stream`，原 chat 接口保持兼容。
- 前端 Chat 增加 Agent/RAG 模式开关，Agent 模式继续复用 sources 面板。
- 新增 RAG/Agent 调用日志表设计，Go 服务写入 question 摘要、intent、耗时、source 数、confidence、trace。
- 新增 demo 种子脚本与最终交付文档。

## P1/P2 Follow-up: 前端闭环细节

- 流式问答 reader 在错误事件或解析异常时会主动 cancel 并释放锁，避免连接残留。
- 文档上传接入 axios `onUploadProgress`，文档管理页和知识库详情页显示真实上传进度。
- 文档解析状态轮询改为随列表数据自动启动/停止，页面加载、切换知识库、翻页和上传后都能保持状态同步。
- 引用来源面板展示 `documentId`、`chunkIndex` 和低匹配度提示，提升引用溯源可解释性。
- 清理 Python Worker 解析器中过期 TODO 注释，使代码说明与 PDF/DOCX 解析现状一致。
- 聊天消息接入 `marked`、`DOMPurify`、`highlight.js`，支持 Markdown 安全渲染和代码块高亮。
- 401 失效跳转改为 Vue Router 内跳转，并保留登录后的 redirect 回跳地址。
- 知识库接口补齐 `documentCount`、`doneCount`，前端卡片展示文档总数和已解析数量。
- 文档响应补齐 `parseTaskId`，上传返回值和列表/详情可追踪对应解析任务。
- 文档管理和知识库详情页补齐状态图标：已上传、解析中、向量化、已完成、失败分别使用独立图标和颜色。

## Verification

- Current follow-up `npm run build` after status icon changes: passed.
- Current follow-up `mvn test -q` after count/task contract changes: passed.
- Current follow-up `npm run build` after count/task contract changes: passed.
- Current follow-up `npm run build` after Markdown rendering: passed.
- Current follow-up `npm audit --omit=dev`: passed.
- Current follow-up `npm audit`: found 2 moderate dev-server vulnerabilities from Vite/esbuild; fix requires a breaking Vite major upgrade, so left for a dedicated dependency upgrade pass.
- Current follow-up `npm run build`: passed.
- Current follow-up `python3 -m compileall app`: passed.
- `mvn test -q`: passed.
- `npm run build`: passed.
- `go test ./...`: passed.
- `python3 -m compileall app`: passed.
- `./.venv/bin/python -m app.main --check`: passed.
- `python3 -m app.main --check`: failed in the global interpreter because dependencies such as `redis` are not installed there.
- `docker compose config`: passed.
- Full Docker smoke test was not run in this pass because it requires building and starting the full compose stack.
