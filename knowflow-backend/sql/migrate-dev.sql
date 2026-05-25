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
