# KnowFlow Backend

KnowFlow —— 基于 RAG 的智能知识库问答平台后端。

## 技术栈

| 组件 | 版本 / 技术 |
|------|-------------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| 构建 | Maven |
| ORM | MyBatis-Plus 3.5.6 |
| 数据库 | PostgreSQL 16 |
| 缓存 / 队列 | Redis 7 |
| 对象存储 | MinIO（预留） |
| 鉴权 | JWT (jjwt 0.12.5) |
| 密码加密 | BCrypt |

## 项目目录结构

```
knowflow-backend/
├── pom.xml
├── docker-compose.yml
├── sql/
│   └── init.sql                          # 数据库初始化脚本
├── README.md
├── API.md                                # 接口文档
└── src/main/java/com/knowflow/
    ├── KnowFlowApplication.java          # 启动类
    ├── common/                           # 通用模块
    │   ├── Result.java                   # 统一返回体
    │   ├── PageResult.java               # 分页返回
    │   ├── BusinessException.java        # 业务异常
    │   └── GlobalExceptionHandler.java   # 全局异常处理
    ├── config/                           # 配置
    │   ├── SecurityConfig.java           # Spring Security
    │   ├── RedisConfig.java              # Redis 序列化
    │   ├── MybatisPlusConfig.java        # MyBatis-Plus 分页
    │   ├── MinioConfig.java              # MinIO 客户端
    │   └── WebConfig.java                # CORS 跨域
    ├── entity/                           # 实体
    │   ├── User.java
    │   ├── KnowledgeBase.java
    │   ├── Document.java
    │   ├── DocumentChunk.java
    │   ├── ChatSession.java
    │   ├── ChatMessage.java
    │   └── ParseTask.java
    ├── dto/                              # 请求 DTO
    ├── vo/                               # 返回 VO
    ├── mapper/                           # MyBatis-Plus Mapper
    ├── security/                         # 安全
    │   ├── JwtUtil.java                  # JWT 工具
    │   ├── JwtAuthenticationFilter.java  # JWT 过滤器
    │   └── LoginUser.java                # 当前用户
    ├── service/                          # 业务接口
    │   └── impl/                         # 业务实现
    ├── controller/                       # 控制器
    └── util/                             # 工具
        ├── RagClient.java                # RAG 服务客户端（mock）
        ├── MinioStorageService.java      # MinIO 存储接口
        └── LocalStorageService.java      # 本地存储实现
```

## 本地启动方式

### 1. 启动依赖服务（Docker Compose）

```bash
cd knowflow-backend
docker compose up -d
```

这会启动：
- PostgreSQL 16 —— 端口 `5432`
- Redis 7 —— 端口 `6379`
- MinIO —— API 端口 `9000`，Console 端口 `9001`

### 2. 初始化数据库

数据库表会在容器首次启动时通过 `sql/init.sql` 自动创建。
如需手动执行：

```bash
docker exec -i knowflow-postgres psql -U knowflow -d knowflow < sql/init.sql
```

### 3. 启动后端

```bash
# 编译并启动
mvn spring-boot:run

# 或先打包再运行
mvn clean package -DskipTests
java -jar target/knowflow-backend-1.0.0-SNAPSHOT.jar
```

服务启动后访问：`http://localhost:8081`

### 4. 接口测试

详见 [API.md](./API.md)

### 快速测试示例

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

# 上传文档
curl -X POST http://localhost:8081/api/document/upload \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "kbId=1" \
  -F "file=@/path/to/your/document.pdf"

# 创建聊天会话
curl -X POST http://localhost:8081/api/chat/session \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"kbId":1,"title":"测试会话"}'

# 提问
curl -X POST http://localhost:8081/api/chat/ask \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{"kbId":1,"sessionId":1,"question":"什么是 KnowFlow？"}'
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

## 统一返回格式

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

错误码范围：
- `0` —— 成功
- `40xxx` —— 业务错误（参数校验、权限、资源不存在等）
- `50xxx` —— 服务器错误

## 后续规划

1. **Python Worker** —— 从 Redis 队列 `knowflow:parse:queue` 消费 parse_task，完成文档解析与向量化
2. **Go RAG 服务** —— 替换 RagClient.mockAsk()，通过 HTTP 调用 Go 端的 RAG 检索与生成服务
3. **pgvector 检索** —— 启用 PostgreSQL 的 pgvector 扩展，将 document_chunk.embedding 改为 VECTOR 类型，实现向量相似度检索
4. **SSE 流式输出** —— 聊天接口支持 Server-Sent Events 流式返回生成结果
5. **MinIO 集成** —— 将文件存储从本地目录切换至 MinIO 对象存储
