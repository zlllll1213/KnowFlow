# KnowFlow 项目代码审查报告

审查日期：2026-05-27  
审查范围：`knowflow-backend`、`knowflow-frontend`、`knowflow-rag-go`、`knowflow-worker-python`、`sql`、`docker-compose`、`scripts`、根 README 与环境变量示例。  
审查原则：本阶段只做代码阅读与问题清单，不改动业务代码；已实现的能力不重复标记为“未实现”。

## 1. 当前项目结构分析

当前仓库已经形成清晰的多服务架构：

- `knowflow-backend/`：Spring Boot 3 主业务后端，承担认证、知识库/文档/会话/消息管理、上传文件、投递解析任务、编排 Go RAG 调用。入口为 `src/main/java/com/knowflow/KnowFlowApplication.java`。
- `knowflow-frontend/`：Vue 3 + TypeScript + Vite 前端，包含登录注册、工作台、知识库、文档管理、聊天与引用来源展示。
- `knowflow-rag-go/`：Go + Gin RAG 服务，包含 `handler`、`service`、`retriever`、`prompt`、`llm`、`embedding` 分层，已提供 `/rag/ask` 与 `/rag/ask/stream`。
- `knowflow-worker-python/`：Python Worker，消费 Redis 队列，下载文档、解析、切片、生成 embedding、写入 `document_chunk`。
- `knowflow-backend/sql/`：PostgreSQL schema，已包含 `CREATE EXTENSION vector` 与核心业务表。
- `scripts/`：本地基础设施启动、数据库兼容迁移、端到端 smoke test。

整体方向符合“Spring Boot 主后端 + Go RAG + Python Worker + Vue 前端”的目标，不需要推翻架构。主要问题集中在接口契约稳定性、分页与统计、健康检查深度、状态机细节、SSE 标准格式、可观测性和 Agent 化能力缺失。

## 2. 各模块职责是否清晰

职责总体清晰：

- Spring Boot 没有直接做向量检索和 LLM 生成，RAG 调用通过 `RagClient` 编排，边界合理。
- Python Worker 没有耦合 HTTP API，使用 Redis + PostgreSQL 状态表异步处理，方向合理。
- Go RAG Service 只关心 `kbId + question -> answer + sources`，没有接管用户权限和知识库 CRUD，边界合理。
- 前端通过 `src/api` 封装后端接口，具备基本统一 API 层。

需要优化的职责边界：

- `knowflow-frontend/src/views/Chat.vue` 直接维护会话、消息、SSE、sources 状态，而 `src/stores/chat.ts` 只提供简单 setter，业务状态没有真正收敛到 store。
- `knowflow-frontend/src/views/Dashboard.vue` 自行拼统计，且只统计第一个知识库，后端没有提供 dashboard 聚合接口。
- Spring Boot 的 `HealthController` 已检查 PostgreSQL、Redis、Go RAG，但存储层只返回配置，没有实际检查本地路径或 MinIO bucket。
- Go RAG Service 目前仍是“RAG Engine”，尚未有 Agent Router / Citation Guard / Trace 等 Agent 层。

## 3. 后端问题清单

### P0

1. RAG 不可用时自动返回 mock 回答，可能污染真实问答与简历演示可信度  
   文件：`knowflow-backend/src/main/java/com/knowflow/util/RagClient.java:71-75`、`165-179`  
   现状：同步问答调用 Go RAG 失败后直接返回带 `mock-notice.txt` 的模拟来源。流式接口也在 `108-113` 降级为 mock token/source/done。  
   风险：用户会以为系统基于知识库回答，实际是后端模拟；sources 不是数据库真实 chunk，违背引用溯源与防幻觉目标。  
   推荐改法：开发环境可保留可配置 mock，但默认应返回明确错误；增加 `knowflow.rag.mock-fallback-enabled=false` 配置，生产禁用。前端展示“RAG 服务不可用”，不要保存 mock sources 为聊天历史。

2. `RestTemplate` 未配置连接/读取超时，RAG 阻塞会拖住业务线程  
   文件：`knowflow-backend/src/main/java/com/knowflow/util/RagClient.java:36-42`、`90-107`  
   现状：直接 `new RestTemplate()`，没有 request factory timeout。  
   风险：Go RAG 或 LLM 卡住时，Spring 请求线程可能长时间阻塞；SSE 连接也无法及时失败。  
   推荐改法：注入统一 `RestTemplate`/`RestClient` Bean，设置 connect/read timeout，例如 3s/120s，并暴露环境变量。

3. SSE 中途发送失败会抛 RuntimeException，可能导致后台线程异常但客户端收不到稳定 error/done  
   文件：`knowflow-backend/src/main/java/com/knowflow/service/impl/ChatServiceImpl.java:243-248`  
   现状：`sendSse` 捕获异常后直接 `throw new RuntimeException(e)`；上游 listener 回调内抛出后由 `CompletableFuture.exceptionally` 兜底，但可能已部分写入。  
   风险：浏览器断开、网络抖动时日志噪声大，消息保存状态不确定。  
   推荐改法：`sendSse` 返回 boolean；发送失败时设置 completed 并停止保存 assistant 空消息。增加 `emitter.onTimeout/onError/onCompletion`。

### P1

4. 分页能力未落到接口，列表均全量返回  
   文件：`KnowledgeBaseController.java:31-35`、`DocumentController.java:30-35`、`ChatController.java:33-38`、`ChatController.java:57-61`  
   现状：存在 `PageResult` 类，但知识库、文档、会话、历史消息都返回 `List`。  
   风险：文档/会话增长后页面加载慢，数据库与网络压力大，移动端体验下降。  
   推荐改法：新增 `page/size` 参数，Service 使用 MyBatis-Plus `Page`，返回 `Result<PageResult<T>>`；默认 `size=20`，最大不超过 100。

5. 全局异常会把内部异常信息返回给客户端  
   文件：`knowflow-backend/src/main/java/com/knowflow/common/GlobalExceptionHandler.java:37-42`  
   现状：兜底异常返回 `"服务器内部错误: " + e.getMessage()`。  
   风险：可能泄露数据库连接、SQL、MinIO object key、第三方 API 错误等内部细节。  
   推荐改法：客户端只返回固定文案和 requestId；详细堆栈只写日志。

6. 未统一处理认证失败的 JSON 返回  
   文件：`knowflow-backend/src/main/java/com/knowflow/config/SecurityConfig.java:32-44`、`JwtAuthenticationFilter.java:32-39`  
   现状：token 无效时 filter 不写错误，仅让请求进入 Spring Security；未配置 `AuthenticationEntryPoint`。  
   风险：前端可能收到非统一 `Result` 或空 401，影响 token 失效提示。  
   推荐改法：配置 `authenticationEntryPoint`，返回 `Result.error(40100, "登录已过期")`。

7. 健康检查未实际验证 MinIO 或本地存储可用性  
   文件：`knowflow-backend/src/main/java/com/knowflow/controller/HealthController.java:42-45`  
   现状：`storage` 只返回 `type/localPath`，没有检查本地目录读写，也没有检查 MinIO bucket。  
   风险：`/api/health` 显示 ok，但上传可能因目录权限或 bucket 不存在失败。  
   推荐改法：在 `FileStorageService` 增加 `health()` 或独立 `StorageHealthIndicator`；MinIO 检查 bucket exists，本地检查目录存在且可写。

8. JWT dev profile 存在硬编码默认 secret  
   文件：`knowflow-backend/src/main/resources/application-dev.yml:24-27`  
   现状：`KNOWFLOW_JWT_SECRET` 未配置时使用固定开发 secret。  
   风险：若误用 dev profile 部署，所有实例共享已知密钥。  
   推荐改法：dev 可允许默认值但启动日志明确警告；prod 已要求环境变量，建议增加启动校验，禁止 `KnowFlow-Dev-...` 在非 dev profile 使用。

9. 会话更新时间更新可能无效  
   文件：`knowflow-backend/src/main/java/com/knowflow/service/impl/ChatServiceImpl.java:82-83`、`135-138`  
   现状：保存问答后调用 `sessionMapper.updateById(session)`，但没有显式修改任何字段。是否触发 `MetaObjectHandler` 的 `updatedAt` 填充需要进一步验证。  
   风险：会话列表按 `updatedAt` 排序可能不反映最新聊天。  
   推荐改法：显式设置 `session.setUpdatedAt(LocalDateTime.now())` 或使用专门 SQL 更新。

10. 上传文件缺少类型白名单与空文件校验  
    文件：`knowflow-backend/src/main/java/com/knowflow/service/impl/DocumentServiceImpl.java:41-57`  
    现状：直接取扩展名入库，未校验文件是否为空、扩展名是否支持、MIME 是否可接受。  
    风险：无效文件会进入 Worker 失败队列；恶意大批量无效上传会消耗存储和任务资源。  
    推荐改法：限制 `pdf/docx/txt/md/markdown`，校验 size > 0，并对超长原始文件名做清晰错误返回。

### P2

11. `ChatServiceImpl.serializeSources(RagResponse)` 未被使用  
    文件：`knowflow-backend/src/main/java/com/knowflow/service/impl/ChatServiceImpl.java:182-190`  
    风险：重复/遗留代码增加维护成本。  
    推荐改法：删除或改为复用在 `saveMessage` 中。

12. `DocumentChunk.embedding` Java 实体用 `String` 表示 vector  
    文件：`knowflow-backend/src/main/java/com/knowflow/entity/DocumentChunk.java:23-24`  
    现状：后端当前不直接写 embedding，因此可运行。  
    风险：后续若 Spring 侧管理 chunk，会与 PostgreSQL `VECTOR(1536)` 类型不匹配。  
    推荐改法：若 Spring 只读 chunk，可明确注释“不由 Java 写入”；若要写入，增加 pgvector TypeHandler。

## 4. 前端问题清单

### P0

1. 流式接口错误事件在 reader 循环中抛出，未取消 reader/response  
   文件：`knowflow-frontend/src/api/chat.ts:44-65`、`67-80`  
   现状：`handleEvent` 遇到 `event === 'error'` 直接 throw；外层没有 `reader.cancel()`。  
   风险：部分浏览器/代理下连接残留，UI 只弹错误，后端可能仍在生成。  
   推荐改法：在 catch/finally 中 cancel reader；增加 `onError` callback，让页面能落空 assistant 消息或展示错误消息。

2. Dashboard 统计只统计第一个知识库，不是真实全局统计  
   文件：`knowflow-frontend/src/views/Dashboard.vue:76-85`  
   现状：加载知识库列表后只对 `kbs[0].id` 调用 `getDocumentList`。  
   风险：工作台数据明显失真；简历演示中的指标统计不可信。  
   推荐改法：后端新增 `/api/dashboard/stats` 聚合接口；前端只展示后端真实统计。

### P1

3. 文档上传后没有自动轮询解析状态  
   文件：`knowflow-frontend/src/views/DocumentManage.vue:100-115`  
   现状：上传完成后 `loadDocs()` 一次；`getDocumentStatus` API 已存在但页面未使用。  
   风险：用户看不到 `PARSING/EMBEDDING/DONE/FAILED` 进度变化，需要手动刷新。  
   推荐改法：上传后对未完成文档启动 3s 轮询，状态为 `DONE/FAILED` 停止；切换知识库或卸载组件时清理 timer。

4. Store 没有承接主要业务流，页面直接散乱调用 API  
   文件：`knowflow-frontend/src/views/Chat.vue:92-96`、`127-206`；`src/stores/chat.ts:5-30`  
   现状：Chat 页直接调用 `createSession/listSessions/askQuestionStream/getChatHistory`，store 基本未使用。  
   风险：后续增加 Agent 模式、重试、历史同步、sources 选择时状态容易分散。  
   推荐改法：将会话列表、当前会话、消息流式追加、sources 更新封装进 `useChatStore` action。

5. token 失效跳转逻辑绕过 router  
   文件：`knowflow-frontend/src/api/request.ts:15-20`  
   现状：401 时 `window.location.href = '/login'`。  
   风险：丢失当前路由信息，SPA 状态重载；无法统一提示“登录已过期”。  
   推荐改法：使用 router 或事件总线处理 401，附带 redirect 参数。

6. SSE parser 假设 `JSON.parse` 必定成功  
   文件：`knowflow-frontend/src/api/chat.ts:54-60`  
   风险：后端若发送注释行、心跳、非 JSON 错误文本，页面会直接异常退出。  
   推荐改法：try/catch JSON.parse，未知事件忽略或进入 `onError`。

### P2

7. `DocumentVO` 类型缺少 `chunkCount` 字段  
   文件：`knowflow-frontend/src/types/document.ts:3-13`；后端返回字段见 `DocumentVO.java:17-19`  
   风险：前端无法展示切片数量，无法体现解析成果。  
   推荐改法：补齐 `chunkCount: number` 并在表格/详情页展示。

8. `SourcePanel` 已展示真实 props sources，但缺少 documentId/chunkIndex 与低分提示  
   文件：`knowflow-frontend/src/components/SourcePanel.vue:13-19`  
   现状：展示文件名、score、content。  
   风险：引用溯源粒度不够，面试演示时无法清晰说明“来自哪个 chunk”。  
   推荐改法：展示 `documentId`、`chunkIndex`，score 为 0 或 mock 时给出弱引用提示。

## 5. Go RAG Service 问题清单

### P0

1. Go SSE 输出不包含标准 `event:` 字段，依赖 Spring 二次解析  
   文件：`knowflow-rag-go/internal/handler/handler.go:73-82`、`87-99`  
   现状：Go 服务输出 `data: {"type":"token"}`；Spring `RagClient` 按 `type` 转成 `event: token`。  
   风险：如果前端或 smoke test 直接连 Go `/rag/ask/stream`，无法按事件名消费；协议不稳定。  
   推荐改法：Go 端也输出标准 SSE：`event: token\ndata: {...}\n\n`，同时保留 `type` 兼容。

2. 请求超时配置没有包裹 RAG Service 的整体执行上下文  
   文件：`knowflow-rag-go/internal/config/config.go:36`、`knowflow-rag-go/internal/service/rag.go:29-62`、`64-102`  
   现状：HTTP client 使用 timeout，但 handler 没有 `context.WithTimeout(c.Request.Context(), cfg.RequestTimeout)` 包裹检索 + LLM。  
   风险：数据库检索或内部逻辑卡住时无法按配置中断。  
   推荐改法：在 handler 层创建 timeout context 传入 service。

### P1

3. 关键词检索空 terms 会返回 0，而不是给出明确“无检索词”降级  
   文件：`knowflow-rag-go/internal/retriever/pgvector.go:149-183`；测试见 `pgvector_test.go:21-25`  
   现状：单字符问题 `a` 得到空 terms，随后 SQL `unnest($2::text[])` 为空，返回无 sources。  
   风险：短问题或符号问题体验差，LLM 可能生成无依据回答。  
   推荐改法：terms 为空时直接返回空 sources 并记录 reason；Answer/Citation Guard 要输出“资料不足”。

4. 没有分段耗时日志  
   文件：`knowflow-rag-go/internal/service/rag.go:30-55`  
   现状：只记录总 latency。  
   风险：无法判断慢在 embedding、pgvector、LLM 还是 SSE。  
   推荐改法：记录 `embeddingMs/retrieveMs/llmMs/totalMs/sourceCount/topK/kbId`。

5. Citation Guard 缺失，LLM 回答不受 sources 充分性校验  
   文件：`knowflow-rag-go/internal/service/rag.go:44-60`、`internal/prompt/builder.go:14-15`  
   现状：Prompt 已要求资料不足时说明无法回答，但没有程序级 guard。  
   风险：模型仍可能幻觉；sources 为空时 mock provider 也会说“找到了一些相关内容”。  
   推荐改法：sources 为空或最高分低于阈值时直接返回“不足以回答”，或实现 Agent Citation Guard。

6. `EmbeddingDim` 只做大于 0 校验，没有与数据库 vector 维度核对  
   文件：`knowflow-rag-go/internal/config/config.go:63-65`、`retriever/pgvector.go:99-114`  
   风险：配置成非 1536 时，`$2::vector` 查询运行时失败。  
   推荐改法：启动时查询 `document_chunk.embedding` 的维度或固定校验为 1536，错误提前暴露。

### P2

7. Gin 默认 logger 会记录所有路径，但缺少结构化 requestId  
   文件：`knowflow-rag-go/cmd/rag-server/main.go:39`  
   风险：跨 Spring/Go/Worker 排查链路困难。  
   推荐改法：增加 requestId middleware，日志带 `requestId/kbId/sessionId`。

## 6. Python Worker 问题清单

### P0

1. `WORKER_CONCURRENCY` 配置未生效  
   文件：`knowflow-worker-python/app/config.py:55-59`、`knowflow-worker-python/app/main.py:194-200`  
   现状：配置里有 `concurrency`，主循环仍是单线程串行处理。  
   风险：多文档上传时队列积压，演示大文件解析耗时明显。  
   推荐改法：使用线程池或多进程 worker 数量按 `WORKER_CONCURRENCY` 启动；保留数据库原子 claim 避免重复消费。

2. `--check` 只校验配置，不检查 Redis/PostgreSQL/MinIO 连通性  
   文件：`knowflow-worker-python/app/main.py:171-182`  
   现状：`--check` 在 `config.validate()` 后直接返回。  
   风险：文档要求“Worker 能连接 Redis、PostgreSQL、MinIO”，但当前 check 不能发现服务不可达、账号错误、bucket 缺失。  
   推荐改法：`--check` 增加 Redis ping、PostgreSQL `SELECT 1`、MinIO bucket exists 或本地存储路径读写检查。

### P1

3. 任务状态流转缺少 `parse_task` 的 `PARSING/EMBEDDING`，只有 `PROCESSING -> DONE/FAILED`  
   文件：`knowflow-worker-python/app/main.py:75-106`、`types.py:41-47`；schema 注释见 `init.sql:80-93`  
   现状：document 有 `PARSING/EMBEDDING`，task 只有 `PROCESSING/DONE/FAILED`。  
   风险：任务表无法精确表达卡在解析还是向量化，后台排障不够细。  
   推荐改法：扩展 `TaskStatus.PARSING/EMBEDDING`，与 document 同步更新；或在报告/README 中明确 task 只有粗粒度状态。

4. `insert_chunks` 删除旧 chunk 与插入新 chunk 在同一事务中，但失败后文档状态可能停在 EMBEDDING  
   文件：`knowflow-worker-python/app/repository.py:140-178`、`main.py:95-120`  
   现状：插入失败会进入异常并更新 FAILED，但旧 chunk 已在未提交事务中删除，连接关闭通常回滚；需要进一步验证 psycopg2 连接异常后的事务状态。  
   风险：重复解析失败时可能出现旧 chunk 保留或删除行为不明确。  
   推荐改法：显式 `conn.rollback()` 后再独立连接/事务更新失败状态；为重复解析增加测试。

5. embedding 维度未逐条校验  
   文件：`knowflow-worker-python/app/embedding.py:38-62`、`65-81`  
   现状：API 返回 embedding 后直接赋值，没有校验长度等于 `WORKER_EMBEDDING_DIM`。  
   风险：写入 `VECTOR(1536)` 时才失败，错误定位晚。  
   推荐改法：生成后统一校验 `len(chunk.embedding) == config.embedding_dim`。

6. 本地文件下载路径没有做 path traversal 防护  
   文件：`knowflow-worker-python/app/parser.py:127-134`  
   现状：`src = Path(storage_root) / object_key`，没有 normalize 后检查仍在 storage_root 内。  
   风险：如果数据库 `file_path` 被污染，Worker 可能读取任意本地文件。  
   推荐改法：使用 `resolve()` 并校验 `src.resolve().is_relative_to(root.resolve())`。

### P2

7. Parser 注释仍写“预留 PDF、DOCX 扩展点/TODO”，但代码和 requirements 已实现  
   文件：`knowflow-worker-python/app/parser.py:1-4`、`77-104`、`requirements.txt:15-18`  
   风险：文档和代码不一致，降低项目可信度。  
   推荐改法：更新注释，说明已支持 PDF/DOCX/TXT/MD，限制是扫描版 PDF 需要 OCR。

8. Markdown 解析会丢失代码块结构  
   文件：`knowflow-worker-python/app/parser.py:44-74`  
   风险：技术文档问答时代码块上下文变差。  
   推荐改法：Markdown 保留 fenced code block，或使用 markdown-it/token 级解析。

## 7. 数据库设计问题清单

### 已实现能力

- 已启用 pgvector：`knowflow-backend/sql/init.sql:6-7`。
- `document_chunk.embedding` 使用 `VECTOR(1536)`：`init.sql:63-70`。
- `chat_message.sources` 使用 JSONB：`init.sql:109-117`。
- 用户、知识库、文档、会话具备逻辑删除字段：`init.sql:15`、`29`、`49`、`101`。
- 常用字段已有部分索引：`idx_kb_user_id`、`idx_doc_kb_id`、`idx_doc_user_id`、`idx_doc_status`、`idx_chunk_document_id`、`idx_chunk_kb_id`、`idx_task_status`、`idx_session_kb_user`、`idx_msg_session_id`。

### P1

1. `document_chunk` 没有逻辑删除字段  
   文件：`knowflow-backend/sql/init.sql:63-70`  
   现状：删除文档时直接删除 chunk。  
   风险：审计和恢复能力弱；但对 RAG 检索性能更简单。  
   推荐改法：如果项目要体现工程完整性，可新增 `is_deleted` 并让检索过滤；否则在设计文档中明确 chunk 随文档物理删除。

2. 缺少外键约束或应用层一致性校验说明  
   文件：`init.sql:23-122`  
   现状：表间没有 FK。  
   风险：异常删除或手工操作后可能出现孤儿 `document_chunk/chat_message/parse_task`。  
   推荐改法：可选择加 FK `ON DELETE CASCADE`，或保留无 FK 但增加一致性清理脚本和说明。

3. `chat_message` 缺少 `(kb_id,user_id,created_at)` 索引  
   文件：`init.sql:109-122`  
   风险：后续做统计“问答次数/最近会话/最近消息”时查询慢。  
   推荐改法：新增 `idx_msg_kb_user_created_at`。

4. `parse_task` 缺少失败时间、重试次数  
   文件：`init.sql:80-93`  
   现状：只有 `status/error_message/created_at/updated_at`。  
   风险：无法区分偶发失败与持续失败，Worker 恢复策略只能基于状态和更新时间。  
   推荐改法：新增 `retry_count/last_error_at`，失败时递增。

### P2

5. 向量索引被注释，数据量上来后 pgvector 检索会慢  
   文件：`init.sql:76-78`  
   现状：`ivfflat` 索引注释为“待切片数据量充足后创建”。  
   风险：大知识库下向量检索全表排序。  
   推荐改法：提供可选迁移 SQL，例如数据量超过 1 万 chunk 后创建 `ivfflat` 或 HNSW 索引。

## 8. Docker / 环境变量 / 启动脚本问题清单

### 已实现能力

- `knowflow-backend/docker-compose.yml` 能启动 PostgreSQL、Redis、MinIO，且三者都有 healthcheck：`docker-compose.yml:4-54`。
- MinIO bucket 能由 Spring 的 `MinioStorageServiceImpl.ensureBucket` 自动创建：`MinioStorageServiceImpl.java:80-103`。但这依赖后端使用 MinIO 存储并首次上传，不是 compose 启动即创建。
- 根目录和各模块均提供 `.env.example`。
- `scripts/smoke-e2e.sh` 覆盖注册、创建知识库、上传、等待解析、SSE 问答。

### P0

1. 没有一键启动完整应用栈的 compose  
   文件：`knowflow-backend/docker-compose.yml:3-54`；Dockerfile 仅见 `knowflow-worker-python/Dockerfile`  
   现状：compose 只启动基础设施，不启动 Spring Boot、Go RAG、Python Worker、前端。Go RAG 和后端没有 Dockerfile。  
   风险：工程化部署能力不足，面试官不能一条命令体验完整系统。  
   推荐改法：新增根目录 `docker-compose.yml` 或 `docker-compose.full.yml`，包括 backend、rag-go、worker、frontend，配置 `depends_on: condition: service_healthy`。

2. smoke test 没有验证 sources 真实性  
   文件：`scripts/smoke-e2e.sh:122-142`  
   现状：只检查 `event:done` 和 `event:token`。  
   风险：RAG 返回 mock 或无引用也会通过。  
   推荐改法：检查 stream 中包含 `event:sources`，并验证 `chunkId/documentId/fileName/content` 非空且不是 `mock-notice.txt`。

### P1

3. MinIO bucket 不是 compose 启动时自动创建  
   文件：`knowflow-backend/docker-compose.yml:37-54`  
   现状：只有 MinIO server，没有 `mc mb` 初始化服务。  
   风险：Worker 使用 MinIO 但后端尚未触发 bucket 创建时，下载/上传链路不稳定。  
   推荐改法：compose 增加 `minio-init` 服务，等待 MinIO healthy 后创建 bucket。

4. README 启动顺序把 Python Worker 和 Go RAG 标为“可选”，但完整闭环依赖二者  
   文件：`README.md:131-145`  
   风险：用户只启动前后端会看到 mock 回答或文档一直未解析。  
   推荐改法：把 Worker/RAG 写成“完整 RAG 闭环必需”，仅 UI/CRUD 演示才可选。

5. Go RAG `.env.example` 仍使用真实默认密码  
   文件：`knowflow-rag-go/.env.example:6-8`  
   现状：`RAG_DB_DSN=postgres://knowflow:knowflow123@localhost...`。  
   风险：虽然是本地默认，但与“敏感配置都 change_me”不一致。  
   推荐改法：改为 `change_me`，README 说明本地基础设施默认密码。

### P2

6. `scripts/dev-infra-up.sh` 不等待 MinIO health  
   文件：`scripts/dev-infra-up.sh:6-15`  
   风险：脚本结束时 MinIO 可能还没 ready。  
   推荐改法：增加 `docker compose up -d --wait` 或显式轮询 health。

## 9. 安全性问题

### P0

1. mock fallback 可能导致用户误信非真实来源  
   文件：`RagClient.java:165-179`  
   推荐：生产禁用 mock fallback，mock sources 不落库。

2. Worker 本地路径下载缺少归一化限制  
   文件：`knowflow-worker-python/app/parser.py:127-134`  
   推荐：路径必须限制在 `WORKER_STORAGE_LOCAL_PATH` 内。

### P1

3. 异常信息可能泄露内部细节  
   文件：`GlobalExceptionHandler.java:37-42`、`RagClient.java:146`、`ChatServiceImpl.java:150`  
   推荐：对外固定错误文案，日志保留详细错误。

4. JWT 缺少强度与环境校验  
   文件：`JwtUtil.java:18-21`、`application-dev.yml:24-27`  
   推荐：启动时校验 secret 长度、禁止非 dev 使用默认 secret。

5. 上传文件缺少白名单与内容校验  
   文件：`DocumentServiceImpl.java:44-57`  
   推荐：限制扩展名、size、MIME，并对 Worker 不支持类型提前拒绝。

### P2

6. 健康检查公开 Go RAG provider/model 信息  
   文件：`knowflow-rag-go/internal/handler/handler.go:24-30`  
   现状：未暴露 API key，风险可控。  
   推荐：生产可隐藏模型名称或只对内网开放 `/health` 详情。

## 10. 简历展示角度的短板

1. Agent 化能力尚未实现  
   目前只有 RAG 问答，没有 Intent Router、Retriever Agent、Answer Agent、Citation Guard、Trace 输出。建议第三阶段在 Go RAG 中新增 agent 层，避免引入重框架。

2. 可观测性不足  
   Go 只记录总耗时；Spring 不记录 RAG 调用的 question hash/session/user/source count；Worker 没有任务耗时统计；数据库没有 RAG 调用日志表。

3. 统计指标不真实  
   Dashboard 只统计第一个知识库，后端没有聚合指标接口，也没有平均检索耗时/回答耗时/最近失败任务。

4. 工程化部署未完整  
   基础设施 compose 已有，但没有完整多服务 compose、后端/Go Dockerfile、MinIO 初始化服务。

5. 防幻觉只靠 prompt  
   Prompt 已写“不要编造”，但没有程序级 guard 和 confidence，面试中容易被追问。

6. 测试覆盖薄弱  
   Go 有少量单元测试；Spring Boot 未发现 `src/test` 测试；前端无组件/接口测试；Worker 无单元测试。Smoke test 有闭环但没有 sources 真实性断言。

## 11. 优先级排序：P0 / P1 / P2

### P0：先修真实闭环与可信度

1. 禁用或配置化 Spring `RagClient` mock fallback，避免假 sources 落库。
2. 为 Spring `RagClient` 增加 timeout，稳定同步和 SSE 调用。
3. 修正 Go SSE 输出为标准 `event + data`，并保持与 Spring/前端兼容。
4. Worker `--check` 增加 Redis/PostgreSQL/MinIO/本地存储连通性检查。
5. 让 `WORKER_CONCURRENCY` 真正生效，或先移除/注明未启用，避免配置欺骗。
6. smoke test 增加 `sources` 真实性断言。
7. 新增完整应用 compose 或至少补齐 backend/rag Dockerfile 与启动文档。

### P1：提升工程质量与可维护性

1. 列表接口分页：知识库、文档、会话、历史消息。
2. 统一认证失败与异常返回，避免内部错误泄露。
3. 健康检查实际检查存储层，MinIO bucket/local path 都要可验证。
4. 前端文档解析状态轮询与失败原因展示。
5. Dashboard 改为后端真实统计接口。
6. Go RAG 增加 embedding/retrieve/LLM 分段耗时日志。
7. Worker 增加 embedding 维度校验、路径安全校验、任务细粒度状态。
8. 数据库补充消息统计索引、parse_task 重试字段。

### P2：简历与演示增强

1. 新增轻量 Agent 工作流设计与实现：Intent Router、Retriever、Answer、Citation Guard、Trace。
2. 增加 RAG 调用日志表或 JSONB trace 字段。
3. 增加 demo 数据和面试演示脚本。
4. README 增加架构图、截图占位、简历亮点、常见问题。
5. 清理遗留代码和注释：未使用 `serializeSources`、Parser TODO、`.env.example` 默认密码一致性。
6. 补充 Spring/Worker/Frontend 测试。

## 需要进一步验证的点

- `sessionMapper.updateById(session)` 是否会触发 `updatedAt` 自动填充，需要通过实际问答后查询 `chat_session.updated_at` 验证。
- Worker 在 `insert_chunks` 过程中失败时，旧 chunk 删除事务是否总是按预期回滚，需要补充重复解析失败测试。
- `mvn test`、`npm run build`、`go test ./...`、`python -m app.main --check` 尚未在本阶段执行；本阶段目标是审查报告，后续进入优化阶段时应按批次验证。
