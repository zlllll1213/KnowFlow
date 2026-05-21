-- KnowFlow Database Initialization
-- PostgreSQL 16

-- 1. 用户表
CREATE TABLE IF NOT EXISTS user_account (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email       VARCHAR(128) NOT NULL UNIQUE,
    is_deleted  SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  user_account IS '用户表';
COMMENT ON COLUMN user_account.password_hash IS 'BCrypt 加密后的密码';
COMMENT ON COLUMN user_account.is_deleted IS '逻辑删除: 0=正常, 1=已删除';

-- 2. 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description TEXT         DEFAULT '',
    is_deleted  SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  knowledge_base IS '知识库表';
COMMENT ON COLUMN knowledge_base.user_id IS '所属用户 ID';

-- 3. 文档表
CREATE TABLE IF NOT EXISTS document (
    id             BIGSERIAL PRIMARY KEY,
    kb_id          BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    file_name      VARCHAR(255) NOT NULL,
    file_path      VARCHAR(512) NOT NULL,
    file_size      BIGINT       DEFAULT 0,
    file_type      VARCHAR(32)  DEFAULT '',
    status         VARCHAR(32)  NOT NULL DEFAULT 'UPLOADED',
    error_message  TEXT         DEFAULT '',
    is_deleted     SMALLINT     NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  document IS '文档表';
COMMENT ON COLUMN document.kb_id IS '所属知识库 ID';
COMMENT ON COLUMN document.status IS 'UPLOADED / PARSING / EMBEDDING / DONE / FAILED';

-- 4. 文档分块表（预留 pgvector）
-- 注意：如需启用向量检索，请先 CREATE EXTENSION vector;
-- 然后将 embedding 列改为 vector(1024) 类型
CREATE TABLE IF NOT EXISTS document_chunk (
    id            BIGSERIAL PRIMARY KEY,
    document_id   BIGINT       NOT NULL,
    kb_id         BIGINT       NOT NULL,
    chunk_index   INT          NOT NULL,
    content       TEXT         NOT NULL,
    -- embedding  VECTOR(1024),  -- 取消注释并安装 pgvector 扩展后启用
    embedding     TEXT         DEFAULT '',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  document_chunk IS '文档分块表';
COMMENT ON COLUMN document_chunk.embedding IS '文本向量，后续替换为 pgvector 的 VECTOR 类型';

-- 5. 解析任务表
CREATE TABLE IF NOT EXISTS parse_task (
    id            BIGSERIAL PRIMARY KEY,
    document_id   BIGINT       NOT NULL,
    kb_id         BIGINT       NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    error_message TEXT         DEFAULT '',
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  parse_task IS '文档解析任务表';
COMMENT ON COLUMN parse_task.status IS 'PENDING / PROCESSING / DONE / FAILED';

-- 6. 聊天会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGSERIAL PRIMARY KEY,
    kb_id       BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(255) DEFAULT 'New Chat',
    is_deleted  SMALLINT     NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE chat_session IS '聊天会话表';

-- 7. 聊天消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT       NOT NULL,
    kb_id         BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    content       TEXT         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE  chat_message IS '聊天消息表';
COMMENT ON COLUMN chat_message.role IS 'user / assistant';

-- 索引
CREATE INDEX IF NOT EXISTS idx_kb_user_id      ON knowledge_base(user_id) WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_doc_kb_id       ON document(kb_id) WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_doc_status      ON document(status);
CREATE INDEX IF NOT EXISTS idx_chunk_doc_id    ON document_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_task_doc_id     ON parse_task(document_id);
CREATE INDEX IF NOT EXISTS idx_session_kb_id   ON chat_session(kb_id, user_id);
CREATE INDEX IF NOT EXISTS idx_msg_session_id  ON chat_message(session_id);
