# KnowFlow Optimization Changelog

## Staged Execution Status · 2026-05-27

**全部六个阶段已完成。** 第六阶段（Dashboard 统计可信度）已补齐 `DashboardServiceImpl` 最小单元测试，验证当前用户维度统计与 SQL 作用域参数。

### 第一阶段：稳定 RAG 主链路 ✅

- Spring `parse_task` 创建仍保留在上传事务内，Redis 入队移动到事务 `afterCommit` 后执行。
- Python Worker 保持线程池并发消费，`WORKER_CONCURRENCY` 与实际行为一致。
- Worker 和 Go RAG 均增加 embedding 维度校验。
- Go RAG mock embedding 按输入文本生成确定性变体向量。
- Spring RAG fallback 默认关闭（`knowflow.rag.fallback-enabled=false`）。
- 后端 `RestTemplate` 增加 connect 3s / read 120s 超时。
- 后端 `sendSse` 改为返回 boolean + try-catch。

### 第二阶段：轻量级 Agent 工作流 ✅

- Go RAG Service 内置 Intent Router / Citation Guard。
- Agent 链路：Router → Retriever → Citation Guard → Answer。
- `/agent/ask` 和 `/agent/ask/stream` 接口。
- AskAgentStream 改为真正流式（ChatStream 逐 token）。
- 前端 AgentTracePanel 展示 trace/confidence/intent。

### 第三阶段：前端 Agent 展示与解析刷新 ✅

- Chat 页 Agent/RAG 模式切换，Agent 消息展示 intent/confidence。
- 文档上传后按文档 ID 每 2s 自动轮询状态，DONE / FAILED 后停止，FAILED 展示 errorMessage。
- 上传进度条 + 分页控件。

### 第四阶段：Dashboard、工程化加固与代码质量 ✅

- `/api/dashboard/stats` 移入 `DashboardService`，聚合当前用户统计（不跨用户）。
- 统计字段对齐 `kbCount / docCount / doneDocCount / failedDocCount / chunkCount / chatCount`，并返回最近文档、最近会话和最近失败任务。
- 前端 Dashboard 改为读取统一统计接口的新字段，展示真实最近文档、失败任务和会话。
- 全量错误处理加固：5 处 `catch {}` → `console.error`，Go handler 脱敏，Worker 瞬态重试。
- DB 连接池、向量 NaN 防护、requestId 中间件、embedding 批量请求。
- 死代码清理、md 文档同步更新。
- GitHub Actions CI 已补充：Backend `mvn test`、Frontend `npm ci && npm run build`、Go RAG `go test ./...`、Python Worker `compileall + --check`。

### 第五阶段：最终验收补强 ✅

- `scripts/smoke-e2e.sh` 增加 Agent 流式断言：`event:meta`（confidence 数值 0-1、trace 非空 step/detail）、`event:token`、`event:sources`（真实字段）、`event:done`。
- RAG 与 Agent 流均增加 fallback-disabled 验证：回答中不含 `mock`/`fallback`/`模拟`/`mock-notice.txt`。
- 验证 Go Ollama embedding（`/api/embeddings` + `"prompt"`）兼容实际 Ollama 单文本接口 — Go 侧仅做查询时单条 embedding，批量为 Python Worker 职责，无需修改。
- 新增 `knowflow-rag-go/internal/embedding/provider_test.go`：MockProvider 确定性/不同文本差异性/维度，OllamaProvider 单次请求体格式/空向量报错/HTTP 错误状态码。
- `go test ./...` 全量通过（含新增 7 个 embedding 用例）。

### 第六阶段：Dashboard 统计可信度 ✅

- 新增 `DashboardServiceImplTest`，使用 JUnit5 + Mockito 做轻量单元测试，不启动 Spring 容器。
- 验证 `/api/dashboard/stats` 背后的聚合字段：知识库数、文档数、DONE/FAILED 文档数、chunk 数、用户提问数、最近文档、最近会话、最近失败任务。
- 验证知识库、文档、聊天消息、最近文档、最近会话统计查询均被纳入 `getStats` 聚合路径。
- 使用手写 `RecordingJdbcTemplate` 捕获原生 SQL 与参数，检查 `chunkCount` 和 `recentFailedTasks` 均使用 `d.user_id = ?`、`kb.user_id = ?`，且参数为当前 `userId` 两次，避免跨用户统计。
- 覆盖 `chunkCount` SQL 返回 `null` 时降级为 0 的边界行为。

### 下一步

- 启动完整 Docker Compose，运行 `scripts/smoke-e2e.sh` 验证全链路。
- 做一次最终 diff review，确认没有无关改动和敏感配置。
- 可选补充 Chat/Agent SSE 解析的前端单元测试或轻量 Playwright 演示验收。

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
- `cd knowflow-backend && mvn test -q` after DashboardServiceImpl test: passed.
- `python3 -m compileall app`: passed.
- `./.venv/bin/python -m app.main --check`: passed.
- `python3 -m app.main --check`: failed in the global interpreter because dependencies such as `redis` are not installed there.
- `docker compose config`: passed.
- Full Docker smoke test was not run in this pass because it requires building and starting the full compose stack.
