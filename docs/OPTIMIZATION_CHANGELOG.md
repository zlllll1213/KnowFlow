# KnowFlow Optimization Changelog

## Staged Execution Status · 2026-06-01

**前十二个阶段已完成。** 第十二阶段完成移动端 Chat / Agent 演示体验补强：窄屏下不再隐藏引用来源和 Agent Trace，普通 RAG 默认展示 sources，Agent 模式默认展示 trace；RAG 与 Agent 主链路已通过端到端 smoke，前端流式协议也有单元测试兜底。

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
- Go Ollama embedding 支持新版 `/api/embed` + `input`，并保留旧版 `/api/embeddings` + `prompt` fallback。
- 新增 `knowflow-rag-go/internal/embedding/provider_test.go`：MockProvider 确定性/不同文本差异性/维度，OllamaProvider 单次请求体格式/空向量报错/HTTP 错误状态码。
- `go test ./...` 全量通过（含新增 7 个 embedding 用例）。

### 第六阶段：Dashboard 统计可信度 ✅

- 新增 `DashboardServiceImplTest`，使用 JUnit5 + Mockito 做轻量单元测试，不启动 Spring 容器。
- 验证 `/api/dashboard/stats` 背后的聚合字段：知识库数、文档数、DONE/FAILED 文档数、chunk 数、用户提问数、最近文档、最近会话、最近失败任务。
- 验证知识库、文档、聊天消息、最近文档、最近会话统计查询均被纳入 `getStats` 聚合路径。
- 使用手写 `RecordingJdbcTemplate` 捕获原生 SQL 与参数，检查 `chunkCount` 和 `recentFailedTasks` 均使用 `d.user_id = ?`、`kb.user_id = ?`，且参数为当前 `userId` 两次，避免跨用户统计。
- 覆盖 `chunkCount` SQL 返回 `null` 时降级为 0 的边界行为。

### 第七阶段：完整 Compose Smoke 验收 ✅

- 新增 `docker-compose.smoke.yml`，使用 `knowflow-smoke-*` 容器名和 `18081/18090/15173` 等隔离端口，避免本机已有旧 KnowFlow 容器时发生端口或容器名冲突。
- Python Worker mock embedding 从全零向量改为确定性非零向量，避免 pgvector cosine 距离产生 `NaN`。
- Go RAG mock embedding 使用同类确定性字符哈希向量，与 Worker mock 行为对齐，开发环境也能完成真实检索路径。
- Go RAG 检索分数增加 `NaN/Inf` 清洗，sources JSON 不再因非法 float 序列化为空。
- Go mock LLM 文案移除 `mock/fallback` 显示字样，避免与 Spring RAG fallback 语义混淆；fallback-disabled 仍由 smoke 脚本校验。
- 前端 Docker healthcheck 改为访问 `127.0.0.1:5173`，避免容器内 `localhost` IPv6 解析导致长期 `health: starting`。
- 使用隔离 compose 环境完成 `scripts/smoke-e2e.sh`：上传、解析、RAG SSE sources、Agent SSE meta/trace/confidence 全部通过。

### 第八阶段：Reasonix + Codex 联合 Review 收口 ✅

- 使用 `npx reasonix@latest code --new` 启动 DeepSeek 审查会话，配合 Codex 本地人工复核 `origin/main...HEAD`。
- 修复 Go RAG / Agent SSE handler 的错误优先级：当后台流式调用报错并关闭 token channel 时，handler 先检查 pending error，再决定是否发送 `done`，避免错误被伪装成成功完成。
- 新增 `internal/handler/handler_test.go`，覆盖 buffered error 优先返回与空 closed channel 的边界。
- 知识库详情页文档状态刷新改为与文档管理页一致：按文档 ID 每 2s 轮询、最多 180 次、DONE/FAILED 停止、FAILED 拉取详情并展示 `errorMessage`，避免列表级无限轮询。
- Worker 处理任务时改为短连接段：认领/状态更新/写入时借用 DB 连接，下载、解析、embedding 期间释放连接，降低并发下连接池被 I/O 占满的风险。
- Worker 增加运行中周期恢复：按 `WORKER_TASK_RECOVERY_INTERVAL_SECONDS` 扫描 PENDING 和超时任务并重新投递，兜底事务提交后 Redis 入队短暂失败。
- Spring `TaskServiceImpl.afterCommit` 捕获 Redis 入队异常并记录 taskId/documentId，不再让 afterCommit 异常只停留在框架日志里。
- Dashboard `recentSessions` 改为显式 JOIN `knowledge_base` 并过滤 `cs.is_deleted = 0` / `kb.is_deleted = 0`，避免显示已删除知识库下的会话。
- Agent 流式早返回路径的 `meta` 不再携带 `answer`，回答文本统一通过 `token` 事件输出，前后端 SSE 契约更稳定。
- Go Ollama embedding 支持新版 `/api/embed` + 旧版 `/api/embeddings` fallback，并补充对应测试。

### 第九阶段：前端 SSE 契约测试与依赖风险收口 ✅

- 继续使用 `npx reasonix@latest code --new` 做前端流式链路 review，重点审查 Chat/Agent SSE 契约、错误处理和 CI 覆盖缺口。
- 将前端流式请求逻辑从 `chat.ts` 抽到 `src/api/sse.ts`，保留原有 `askStream` / `askAgentStream` 调用方式，降低 Chat API 文件复杂度。
- 新增 `src/api/sse.test.ts`，覆盖 `token`、`meta`、`sources`、`done`、`error`、裸数组 sources、跨 chunk 事件、缺少 done、非法 JSON 等边界。
- 修复错误事件回调遮蔽问题：即使 UI 层 `onError` 回调自身抛错，`streamRequest` 也会继续抛出后端原始错误，例如 `Go RAG Service unavailable, fallback disabled.`。
- 引入 Vitest，并在 GitHub Actions 前端任务中加入 `npm run test:unit`，让 SSE 契约测试随 PR / push 自动执行。
- 升级 `vite` 到 `8.0.15`、`@vitejs/plugin-vue` 到 `6.0.7`、`vitest` 到 `4.1.7`，清理 Vite/esbuild dev-server 审计风险。
- 适配 Vite 8 / Rolldown 的 `manualChunks` 配置，将对象写法改为函数写法，前端生产构建恢复通过。

### 第十阶段：Chat 运行时体验与 SSE 取消收口 ✅

- `streamRequest` 新增 `AskStreamOptions.signal`，`askQuestionStream` 和 `askAgentStream` 透传 `AbortSignal`，为页面切换、会话切换和停止生成提供底层能力。
- Chat 页新增当前流式请求的 `AbortController` 管理：切换知识库、切换会话、组件卸载时会静默取消正在进行的 SSE；生成中发送按钮切换为停止按钮，用户可主动停止。
- Agent/RAG 响应判断从 `'answer' in message` / `'trace' in message` 改为 `isAgentResponse` 显式 type guard，并新增单元测试覆盖普通消息和 Agent 响应边界。
- 浏览器 smoke 发现 Chat 页使用了 `<el-switch>` 但未注册 `ElSwitch`，导致运行时 warning；已在 `main.ts` 注册，Agent/RAG 切换控件恢复为 Element Plus 组件。
- 使用隔离 smoke 后端 + 新建临时用户/知识库/文档/会话，在浏览器中验证 Chat 普通 RAG 模式和 Agent 模式：sources 面板、confidence、intent、Agent Trace 均正常渲染。
- Browser post-fix log window 未出现新的 error/warn；历史日志中保留了修复前的 `el-switch` warning 记录。

### 第十一阶段：DeepSeek API Key 安全接入 ✅

- 核对 DeepSeek 官方 API 文档后，将 Go RAG DeepSeek 默认 base URL 更新为 `https://api.deepseek.com`，默认模型更新为 `deepseek-v4-flash`，避免继续默认使用即将废弃的 `deepseek-chat`。
- 新增 `RAG_LLM_THINKING_ENABLED`，DeepSeek thinking 默认关闭，普通 RAG / Agent 演示默认走更低延迟、低成本的非 thinking 模式；需要推理模式时可显式设为 `true`。
- 修复关键安全风险：`RAG_EMBEDDING_PROVIDER` 不再默认继承 `RAG_LLM_PROVIDER`，`RAG_EMBEDDING_API_KEY` 不再继承 `RAG_LLM_API_KEY`，避免 DeepSeek LLM Key 被误发到 embedding endpoint。
- 明确禁止 `RAG_EMBEDDING_PROVIDER=deepseek` 和 `WORKER_EMBEDDING_PROVIDER=deepseek`，配置校验会返回清晰错误，提示使用 `mock`、`openai` 或 `ollama`。
- `.gitignore` 增加 `.env`，真实 API Key 可放在本地 `.env`，不会被误提交；`.env.example` 继续作为无密钥模板保留。
- 更新根 README、Go RAG README、部署文档和环境变量示例，说明 DeepSeek Key 只能在服务端环境变量中配置，不能进入前端 `VITE_*`、源码、文档或 Git 历史。
- 新增 Go 单元测试覆盖：DeepSeek 默认 endpoint/model/thinking、embedding 不继承 LLM Key、拒绝 DeepSeek embedding provider；Worker 配置同步禁用 DeepSeek embedding。

### 第十二阶段：移动端 Agent 演示体验补强 ✅

- Reasonix 联合审查会话继续保持只审查、不改文件、不读取 `.env` 的协作边界；本轮先落地优化文档中已列出的窄屏 Agent 信息展示缺口。
- Chat 页新增移动端证据面板，在 `max-width: 1024px` 下复用现有 `SourcePanel` 与 `AgentTracePanel`，桌面端右侧 sources/trace 面板保持不变。
- 移动端证据面板提供“引用来源 / Agent Trace”标签切换：普通 RAG 流式回答默认展示 sources，Agent 模式发送问题后默认切到 trace，保留 sources 标签用于继续查看引用来源。
- sources、meta、done 事件到达时触发滚动收口；在窄屏页面中让引用来源或 Agent Trace 出现后尽量进入视口，减少演示时手动寻找 trace 的成本。
- 使用临时无头 Chrome 390x844 视口完成 browser smoke：临时用户、知识库、文档、会话均通过后端 API 创建，JWT 只写入临时浏览器 localStorage，不打印 token，不读取或输出 `.env`。
- 验证普通 RAG 窄屏下 sources 面板可见，Agent 模式下 active tab 为 `Agent Trace`，trace 包含 Router / Retriever / Answer / Citation Guard。

### 下一步

- 第十三阶段建议把本轮临时浏览器 smoke 固化为仓库脚本，覆盖登录、选择知识库、RAG 提问、Agent 提问、sources/trace 视口断言，并保证不打印 token / API Key。
- 继续观察移动端 Chat 的信息密度：如果后续演示问题更长，可考虑把移动端证据面板做成底部抽屉或可折叠面板，进一步降低长回答时的滚动成本。
- DeepSeek Key 已可用于真实 RAG / Agent smoke；后续仍建议保持 `RAG_EMBEDDING_PROVIDER` 与 Worker embedding provider 显式一致，避免 LLM Key 被误用于 embedding。

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

- Current follow-up `cd knowflow-rag-go && go test ./...`: passed after DeepSeek secure config changes.
- Current follow-up with local ignored `.env` DeepSeek key: `BACKEND_URL=http://localhost:18081 RAG_URL=http://localhost:18090 TIMEOUT_SECONDS=120 QUESTION='请用一句话说明 KnowFlow 是什么。' ./scripts/smoke-e2e.sh`: passed for real DeepSeek RAG stream and Agent stream.
- Current follow-up `cd knowflow-frontend && npm run test:unit`: passed, 10 tests.
- Current follow-up `cd knowflow-frontend && npm run build`: passed after `ElSwitch` registration and AbortSignal/type-guard changes; Rolldown still reports third-party `@vueuse/core` pure annotation warnings but exits 0.
- Current follow-up mobile browser smoke on `http://127.0.0.1:15174/chat`: passed at 390x844; RAG sources panel visible, Agent Trace active with Router/Retriever/Answer/Citation Guard.
- Current follow-up `cd knowflow-frontend && npm run test:unit`: passed, 10 tests after mobile evidence panel changes.
- Current follow-up `cd knowflow-frontend && npm run build`: passed after mobile evidence panel changes; Rolldown still reports third-party `@vueuse/core` pure annotation warnings but exits 0.
- Current follow-up `git diff --check`: passed after mobile evidence panel changes.
- Current follow-up Browser smoke on `http://127.0.0.1:15174/chat?kbId=7`: passed for RAG answer + sources and Agent answer + sources + confidence + trace on desktop viewport.
- Current follow-up Browser post-fix console window: passed, no new error/warn after reloading Chat with `ElSwitch` registered.
- Current follow-up `git diff --check`: passed after Chat/SSE cancellation changes.
- Current follow-up `cd knowflow-frontend && npm run test:unit`: passed, 7 tests.
- Current follow-up `cd knowflow-frontend && npm run build`: passed on Vite 8.0.15; Rolldown reported third-party `@vueuse/core` pure annotation warnings but exited 0.
- Current follow-up `cd knowflow-frontend && npm audit --omit=dev`: passed, 0 vulnerabilities.
- Current follow-up `cd knowflow-frontend && npm audit`: passed, 0 vulnerabilities.
- Current follow-up `git diff --check`: passed after SSE tests and Vite config changes.
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
- `docker compose -p knowflow-smoke -f docker-compose.yml -f docker-compose.smoke.yml up --build -d`: passed.
- `BACKEND_URL=http://localhost:18081 RAG_URL=http://localhost:18090 TIMEOUT_SECONDS=120 ./scripts/smoke-e2e.sh`: passed.
- `cd knowflow-rag-go && go test ./...` after SSE error-priority fix: passed.
- `cd knowflow-frontend && npm run build` after KnowledgeBaseDetail polling fix: passed.
- `python3 -m compileall app`: passed.
- `./.venv/bin/python -m app.main --check`: passed.
- `python3 -m app.main --check`: failed in the global interpreter because dependencies such as `redis` are not installed there.
- `docker compose config`: passed.
- `git diff --check`: passed after Reasonix review hardening.
- `cd knowflow-rag-go && go test ./...`: passed after SSE / Ollama compatibility hardening.
- `cd knowflow-backend && mvn test -q`: passed after Dashboard recentSessions and TaskService afterCommit logging changes.
- `cd knowflow-frontend && npm run build`: passed after KnowledgeBaseDetail polling changes.
- `cd knowflow-worker-python && python3 -m compileall app`: passed after Worker short-connection and periodic recovery changes.
- `cd knowflow-worker-python && WORKER_REDIS_URL=redis://localhost:16379/0 WORKER_DB_DSN=postgresql://knowflow:knowflow123@localhost:15432/knowflow WORKER_STORAGE_TYPE=minio WORKER_MINIO_ENDPOINT=localhost:19000 WORKER_MINIO_ACCESS_KEY=minioadmin WORKER_MINIO_SECRET_KEY=minioadmin123 WORKER_MINIO_BUCKET=knowflow ./.venv/bin/python -m app.main --check`: passed.
- `docker compose -p knowflow-smoke -f docker-compose.yml -f docker-compose.smoke.yml up --build -d`: passed after rebuilding backend/rag/worker/frontend images.
- `BACKEND_URL=http://localhost:18081 RAG_URL=http://localhost:18090 TIMEOUT_SECONDS=120 ./scripts/smoke-e2e.sh`: passed after Reasonix review hardening.
