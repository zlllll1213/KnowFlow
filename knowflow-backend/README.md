# KnowFlow Backend

KnowFlow 智能知识库问答平台 — Spring Boot 3 后端服务。

---

## 技术栈

| 组件 | 版本 / 技术 |
|------|-------------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| 构建 | Maven |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | PostgreSQL 16 |
| 缓存 / 消息队列 | Redis 7 |
| 对象存储 | MinIO（预留） |
| 鉴权 | JWT (jjwt 0.12.5) |
| 密码加密 | BCrypt |

---

## 目录结构

```
knowflow-backend/
├── pom.xml
├── docker-compose.yml
├── API.md                                    # 完整接口文档
├── sql/
│   └── init.sql                              # 数据库初始化脚本
└── src/main/java/com/knowflow/
    ├── KnowFlowApplication.java              # 启动入口
    ├── common/                               # 通用模块
    │   ├── Result.java                       # 统一响应体
    │   ├── PageResult.java                   # 分页响应
    │   ├── BusinessException.java            # 业务异常
    │   └── GlobalExceptionHandler.java       # 全局异常处理
    ├── config/                               # 配置
    │   ├── SecurityConfig.java               # Spring Security
    │   ├── RedisConfig.java                  # Redis 序列化
    │   ├── MybatisPlusConfig.java            # MyBatis-Plus 分页插件
    │   ├── MinioConfig.java                  # MinIO 客户端
    │   └── WebConfig.java                    # CORS 跨域
    ├── entity/                               # 数据实体
    │   ├── User.java
    │   ├── KnowledgeBase.java
    │   ├── Document.java
    │   ├── DocumentChunk.java
    │   ├── ChatSession.java
    │   ├── ChatMessage.java
    │   └── ParseTask.java
    ├── dto/                                  # 请求 DTO
    ├── vo/                                   # 响应 VO
    ├── mapper/                               # MyBatis-Plus Mapper
    ├── security/                             # 安全模块
    │   ├── JwtUtil.java                      # JWT 工具类
    │   ├── JwtAuthenticationFilter.java      # JWT 认证过滤器
    │   └── LoginUser.java                    # 当前登录用户
    ├── service/                              # 业务接口
    │   └── impl/                             # 业务实现
    ├── controller/                           # 控制器
    └── util/                                 # 工具类
        ├── RagClient.java                    # RAG 服务客户端（当前为 mock）
        ├── MinioStorageService.java          # MinIO 存储接口
        └── LocalStorageService.java          # 本地存储实现
```

---

## 本地启动

### 前置条件

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose

### 第一步：启动依赖服务

```bash
cd knowflow-backend
docker compose up -d
```

启动的服务：

| 服务 | 端口 | 说明 |
|------|------|------|
| PostgreSQL 16 | `5432` | 主数据库，自动执行 `sql/init.sql` 建表 |
| Redis 7 | `6379` | 缓存 & 解析任务队列 |
| MinIO | `9000` / `9001` | 对象存储（API / Console） |

MinIO 默认凭据：`minioadmin / minioadmin123`

### 第二步：（可选）手动初始化数据库

容器首次启动时会自动执行建表脚本。如需手动执行：

```bash
docker exec -i knowflow-postgres psql -U knowflow -d knowflow < sql/init.sql
```

### 第三步：启动后端服务

```bash
# 直接运行（开发模式）
mvn spring-boot:run

# 打包后运行
mvn clean package -DskipTests
java -jar target/knowflow-backend-1.0.0-SNAPSHOT.jar
```

服务启动后访问：`http://localhost:8081`

---

## 接口快速测试

```bash
# 注册
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456","email":"test@knowflow.com"}'

# 登录（获取 JWT Token）
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 创建知识库（将 YOUR_TOKEN 替换为登录返回的 token）
curl -X POST http://localhost:8081/api/kb \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"name":"我的知识库","description":"测试知识库"}'

# 上传文档
curl -X POST http://localhost:8081/api/document/upload \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "kbId=1" \
  -F "file=@/path/to/document.pdf"

# 创建聊天会话
curl -X POST http://localhost:8081/api/chat/session \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"kbId":1,"title":"测试会话"}'

# 智能提问
curl -X POST http://localhost:8081/api/chat/ask \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"kbId":1,"sessionId":1,"question":"什么是 KnowFlow？"}'
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

完整请求 / 响应字段说明见 [API.md](./API.md)。

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
| `40xxx` | 业务错误（参数校验、权限不足、资源不存在等） |
| `50xxx` | 服务器内部错误 |

---

## 后续规划

| 功能 | 说明 |
|------|------|
| Python Worker | 消费 Redis 队列 `knowflow:parse:queue`，完成文档解析与向量化 |
| Go RAG 服务 | 替换 `RagClient.mockAsk()`，通过 HTTP 调用 Go 端 RAG 检索与生成 |
| pgvector 检索 | 启用 PostgreSQL pgvector 扩展，将 `document_chunk.embedding` 改为 `VECTOR` 类型 |
| SSE 流式输出 | 聊天接口支持 Server-Sent Events 逐字流式返回 |
| MinIO 集成 | 将本地文件存储切换至 MinIO 对象存储 |
