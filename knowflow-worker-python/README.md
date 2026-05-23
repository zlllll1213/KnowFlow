# KnowFlow Python Worker

文档解析与向量化 Worker。从 Redis 队列消费解析任务，对上传的文档进行解析、切片、embedding 生成，并将结果写入 PostgreSQL。

## 工作流

```
Redis BLPOP → 查询 parse_task → 下载文件 → 解析 → 切片 → embedding → 写入 document_chunk → 更新状态
```

## 支持的文件类型

| 类型 | 状态 | 依赖 |
|------|:----:|------|
| TXT | ✅ | 无 |
| Markdown | ✅ | `markdown`（可选） |
| PDF | ✅ | `pypdf` |
| DOCX | ✅ | `python-docx` |

## 支持的 Embedding Provider

| Provider | 状态 | 说明 |
|----------|:----:|------|
| mock | ✅ | **开发用**：生成全零向量，用于测试流程 |
| openai | TODO | OpenAI / 兼容 API |
| ollama | TODO | Ollama 本地模型 |

> ⚠️ `mock` embedding 仅用于开发测试。生产环境请配置真实 embedding API。

## 本地启动

### 1. 安装依赖

```bash
cd knowflow-worker-python
pip install -r requirements.txt
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env，修改数据库密码等
```

### 3. 启动 Worker

```bash
python -m app.main
```

### 4. Docker 启动

```bash
docker build -t knowflow-worker .
docker run --env-file .env knowflow-worker
```

## 测试方法

1. 确保后端、PostgreSQL、Redis 已启动
2. 通过后端 API 或 Swagger 上传一个 TXT/MD 文件
3. Worker 会自动从队列获取任务并处理
4. 查询 `document_chunk` 表确认切片已写入
5. 查询 `document.status` 确认状态已更新为 `DONE`

```sql
-- 查看切片
SELECT id, document_id, chunk_index, LEFT(content, 100) FROM document_chunk ORDER BY document_id, chunk_index;

-- 查看文档状态
SELECT id, file_name, status, chunk_count FROM document;
```

## 状态流转

```
document: UPLOADED → PARSING → EMBEDDING → DONE
task:     PENDING  → PROCESSING → DONE

失败时:
document: → FAILED
task:     → FAILED (记录 error_message)
```

## 后续规划

- [ ] 多线程并发处理
- [ ] 接入 OpenAI / Ollama embedding API
- [ ] PDF 表格提取优化
- [ ] 大文件分段处理
