"""
数据库操作模块。
查询/更新 parse_task、document、写入 document_chunk。
"""

import logging
import json
from datetime import datetime, timezone

import psycopg2
import psycopg2.extras

from app.config import config
from app.types import ParseTask, Document, DocumentChunk, TaskStatus, DocStatus

log = logging.getLogger(__name__)

# 注册 psycopg2 的 VECTOR 适配器（如果安装了 pgvector）
try:
    from pgvector.psycopg2 import register_vector
    VECTOR_AVAILABLE = True
except ImportError:
    VECTOR_AVAILABLE = False


def get_connection():
    """获取数据库连接。"""
    return psycopg2.connect(config.db_dsn)


def fetch_task(conn, task_id: int) -> ParseTask | None:
    """查询解析任务。"""
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            "SELECT id, document_id, kb_id, status, error_message FROM parse_task WHERE id = %s",
            (task_id,),
        )
        row = cur.fetchone()
        if row is None:
            return None
        return ParseTask(**row)


def fetch_document(conn, doc_id: int) -> Document | None:
    """查询文档记录。"""
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            "SELECT id, kb_id, user_id, file_name, file_path, file_size, file_type, status, "
            "COALESCE(chunk_count, 0) AS chunk_count FROM document WHERE id = %s",
            (doc_id,),
        )
        row = cur.fetchone()
        if row is None:
            return None
        return Document(**row)


def update_task_status(conn, task_id: int, status: str, error_message: str = ""):
    """更新解析任务状态。"""
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE parse_task SET status = %s, error_message = %s, updated_at = %s WHERE id = %s",
            (status, error_message, datetime.now(timezone.utc), task_id),
        )
    conn.commit()


def update_document_status(conn, doc_id: int, status: str, chunk_count: int | None = None):
    """更新文档状态和切片数量。"""
    with conn.cursor() as cur:
        if chunk_count is not None:
            cur.execute(
                "UPDATE document SET status = %s, chunk_count = %s, updated_at = %s WHERE id = %s",
                (status, chunk_count, datetime.now(timezone.utc), doc_id),
            )
        else:
            cur.execute(
                "UPDATE document SET status = %s, updated_at = %s WHERE id = %s",
                (status, datetime.now(timezone.utc), doc_id),
            )
    conn.commit()


def insert_chunks(conn, chunks: list[DocumentChunk]):
    """批量插入 document_chunk。"""
    if not chunks:
        return

    with conn.cursor() as cur:
        if VECTOR_AVAILABLE and chunks[0].embedding:
            # 使用 pgvector 的 VECTOR 类型
            register_vector(conn)
            for chunk in chunks:
                cur.execute(
                    "INSERT INTO document_chunk (document_id, kb_id, chunk_index, content, embedding) "
                    "VALUES (%s, %s, %s, %s, %s)",
                    (
                        chunk.document_id,
                        chunk.kb_id,
                        chunk.chunk_index,
                        chunk.content,
                        chunk.embedding,
                    ),
                )
        else:
            # fallback: embedding 以 JSON 字符串存储
            for chunk in chunks:
                embedding_json = json.dumps(chunk.embedding) if chunk.embedding else "[]"
                cur.execute(
                    "INSERT INTO document_chunk (document_id, kb_id, chunk_index, content, embedding) "
                    "VALUES (%s, %s, %s, %s, %s)",
                    (
                        chunk.document_id,
                        chunk.kb_id,
                        chunk.chunk_index,
                        chunk.content,
                        embedding_json,
                    ),
                )
    conn.commit()
    log.info("已写入 %d 个 chunk 到数据库", len(chunks))
