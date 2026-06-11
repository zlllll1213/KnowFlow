-- ============================================================
-- KnowFlow Database Initialization
-- PostgreSQL 16 + pgvector
-- ============================================================

-- Enable pgvector extension (requires pgvector/pgvector:pg16 image)
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS user_account (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  user_account IS '用户表';
COMMENT ON COLUMN user_account.password_hash IS 'BCrypt 加密后的密码';
COMMENT ON COLUMN user_account.is_deleted IS '逻辑删除: 0=正常, 1=已删除';

-- 2. 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description TEXT         DEFAULT '',
    is_deleted  SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  knowledge_base IS '知识库表';
COMMENT ON COLUMN knowledge_base.user_id IS '所属用户 ID';
CREATE INDEX IF NOT EXISTS idx_kb_user_id ON knowledge_base(user_id) WHERE is_deleted = 0;

-- 3. 文档表
CREATE TABLE IF NOT EXISTS document (
    id            BIGSERIAL    PRIMARY KEY,
    kb_id         BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    file_path     VARCHAR(512) NOT NULL,
    file_size     BIGINT       NOT NULL DEFAULT 0,
    file_type     VARCHAR(32)  DEFAULT '',
    status        VARCHAR(32)  NOT NULL DEFAULT 'UPLOADED',
    chunk_count   INT          NOT NULL DEFAULT 0,
    error_message TEXT         DEFAULT '',
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  document IS '文档表';
COMMENT ON COLUMN document.kb_id IS '所属知识库 ID';
COMMENT ON COLUMN document.status IS 'UPLOADED / PARSING / EMBEDDING / DONE / FAILED';
COMMENT ON COLUMN document.chunk_count IS '已完成的切片数量';
COMMENT ON COLUMN document.file_path IS 'MinIO object key 或本地路径';
CREATE INDEX IF NOT EXISTS idx_doc_kb_id   ON document(kb_id)   WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_doc_user_id ON document(user_id)  WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_doc_status  ON document(status)   WHERE is_deleted = 0;

-- 4. 文档分块表（pgvector）
CREATE TABLE IF NOT EXISTS document_chunk (
    id          BIGSERIAL    PRIMARY KEY,
    document_id BIGINT       NOT NULL,
    kb_id       BIGINT       NOT NULL,
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    embedding   VECTOR(1536),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  document_chunk IS '文档分块表，存储切片内容和向量';
COMMENT ON COLUMN document_chunk.embedding IS '文本向量，1536 维（text-embedding-3-small），余弦相似度检索';
CREATE INDEX IF NOT EXISTS idx_chunk_document_id ON document_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_kb_id       ON document_chunk(kb_id);
-- 向量检索索引（待切片数据量充足后创建）
-- CREATE INDEX IF NOT EXISTS idx_chunk_embedding_ivf
--     ON document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 5. 解析任务表
CREATE TABLE IF NOT EXISTS parse_task (
    id            BIGSERIAL    PRIMARY KEY,
    document_id   BIGINT       NOT NULL,
    kb_id         BIGINT       NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    error_message TEXT         DEFAULT '',
    retry_count   INT          NOT NULL DEFAULT 0,
    last_error_at TIMESTAMP,
    is_deleted    SMALLINT     NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  parse_task IS '文档解析任务表';
COMMENT ON COLUMN parse_task.status IS 'PENDING / PROCESSING / PARSING / EMBEDDING / DONE / FAILED / CANCELLED';
COMMENT ON COLUMN parse_task.is_deleted IS '逻辑删除: 0=正常, 1=已删除';
ALTER TABLE parse_task ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_task_document_id ON parse_task(document_id) WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_task_status      ON parse_task(status) WHERE is_deleted = 0;

-- 6. 聊天会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id         BIGSERIAL    PRIMARY KEY,
    kb_id      BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(255) DEFAULT 'New Chat',
    is_deleted SMALLINT     NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE chat_session IS '聊天会话表';
CREATE INDEX IF NOT EXISTS idx_session_kb_user ON chat_session(kb_id, user_id) WHERE is_deleted = 0;

-- 7. 聊天消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id         BIGSERIAL    PRIMARY KEY,
    session_id BIGINT       NOT NULL,
    kb_id      BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    role       VARCHAR(16)  NOT NULL,
    content    TEXT         NOT NULL,
    sources    JSONB        NOT NULL DEFAULT '[]',
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  chat_message IS '聊天消息表';
COMMENT ON COLUMN chat_message.role IS 'user / assistant';
COMMENT ON COLUMN chat_message.sources IS '引用来源 SourceChunk 数组';
CREATE INDEX IF NOT EXISTS idx_msg_session_id ON chat_message(session_id);
CREATE INDEX IF NOT EXISTS idx_msg_session_created_at ON chat_message(session_id, created_at);
CREATE INDEX IF NOT EXISTS idx_msg_kb_user_created_at ON chat_message(kb_id, user_id, created_at DESC);

-- 8. RAG / Agent 调用日志
CREATE TABLE IF NOT EXISTS rag_call_log (
    id                 BIGSERIAL PRIMARY KEY,
    kb_id              BIGINT    NOT NULL,
    user_id            BIGINT,
    session_id         BIGINT,
    mode               VARCHAR(32) NOT NULL DEFAULT 'rag',
    intent             VARCHAR(64) DEFAULT '',
    question_summary   VARCHAR(512) NOT NULL,
    top_k              INT NOT NULL DEFAULT 5,
    source_count       INT NOT NULL DEFAULT 0,
    retrieve_latency_ms BIGINT NOT NULL DEFAULT 0,
    llm_latency_ms      BIGINT NOT NULL DEFAULT 0,
    total_latency_ms    BIGINT NOT NULL DEFAULT 0,
    confidence          DOUBLE PRECISION NOT NULL DEFAULT 0,
    trace               JSONB NOT NULL DEFAULT '[]',
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_rag_log_kb_created_at ON rag_call_log(kb_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_log_user_created_at ON rag_call_log(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_log_created_at ON rag_call_log(created_at);
