-- ============================================================
-- KnowFlow development schema compatibility migration
-- Safe to run repeatedly against an existing local database.
-- ============================================================

DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS vector;
EXCEPTION
    WHEN OTHERS THEN
        RAISE NOTICE 'pgvector extension is not available in this PostgreSQL image; keyword fallback still works. (%)', SQLERRM;
END
$$;

ALTER TABLE document
    ADD COLUMN IF NOT EXISTS chunk_count INT NOT NULL DEFAULT 0;

ALTER TABLE document
    ADD COLUMN IF NOT EXISTS error_message TEXT DEFAULT '';

ALTER TABLE document
    ADD COLUMN IF NOT EXISTS is_deleted SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS sources JSONB NOT NULL DEFAULT '[]';

ALTER TABLE parse_task
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE parse_task
    ADD COLUMN IF NOT EXISTS last_error_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_doc_kb_id
    ON document(kb_id) WHERE is_deleted = 0;

CREATE INDEX IF NOT EXISTS idx_doc_user_id
    ON document(user_id) WHERE is_deleted = 0;

CREATE INDEX IF NOT EXISTS idx_doc_status
    ON document(status) WHERE is_deleted = 0;

CREATE INDEX IF NOT EXISTS idx_chunk_document_id
    ON document_chunk(document_id);

CREATE INDEX IF NOT EXISTS idx_chunk_kb_id
    ON document_chunk(kb_id);

CREATE INDEX IF NOT EXISTS idx_task_document_id
    ON parse_task(document_id);

CREATE INDEX IF NOT EXISTS idx_task_status
    ON parse_task(status);

CREATE INDEX IF NOT EXISTS idx_msg_kb_user_created_at
    ON chat_message(kb_id, user_id, created_at DESC);

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

CREATE INDEX IF NOT EXISTS idx_rag_log_kb_created_at
    ON rag_call_log(kb_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_log_user_created_at
    ON rag_call_log(user_id, created_at DESC);
