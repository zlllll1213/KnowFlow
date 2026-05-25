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


def claim_task(conn, task_id: int, stale_minutes: int) -> ParseTask | None:
    """
    原子认领解析任务。
    只有 PENDING/FAILED，或已经超时卡住的 PROCESSING 任务可以被认领。
    """
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            """
            UPDATE parse_task
            SET status = %s, error_message = '', updated_at = NOW()
            WHERE id = %s
              AND (
                  status IN (%s, %s)
                  OR (
                      status = %s
                      AND updated_at < NOW() - (%s * INTERVAL '1 minute')
                  )
              )
            RETURNING id, document_id, kb_id, status, error_message
            """,
            (
                TaskStatus.PROCESSING,
                task_id,
                TaskStatus.PENDING,
                TaskStatus.FAILED,
                TaskStatus.PROCESSING,
                stale_minutes,
            ),
        )
        row = cur.fetchone()
    conn.commit()
    if row is None:
        return None
    return ParseTask(**row)


def list_recoverable_tasks(conn, stale_minutes: int, limit: int) -> list[int]:
    """列出启动时需要重新投递的 PENDING/超时 PROCESSING 任务。"""
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id
            FROM parse_task
            WHERE status = %s
               OR (
                   status = %s
                   AND updated_at < NOW() - (%s * INTERVAL '1 minute')
               )
            ORDER BY updated_at ASC, id ASC
            LIMIT %s
            """,
            (TaskStatus.PENDING, TaskStatus.PROCESSING, stale_minutes, limit),
        )
        return [int(row[0]) for row in cur.fetchall()]


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


def update_document_status(conn, doc_id: int, status: str, chunk_count: int | None = None, error_message: str = ""):
    """更新文档状态和切片数量。"""
    with conn.cursor() as cur:
        if chunk_count is not None:
            cur.execute(
                "UPDATE document SET status = %s, chunk_count = %s, error_message = %s, updated_at = %s WHERE id = %s",
                (status, chunk_count, error_message, datetime.now(timezone.utc), doc_id),
            )
        else:
            cur.execute(
                "UPDATE document SET status = %s, error_message = %s, updated_at = %s WHERE id = %s",
                (status, error_message, datetime.now(timezone.utc), doc_id),
            )
    conn.commit()


def insert_chunks(conn, chunks: list[DocumentChunk]):
    """批量插入 document_chunk。"""
    if not chunks:
        return

    with conn.cursor() as cur:
        cur.execute("DELETE FROM document_chunk WHERE document_id = %s", (chunks[0].document_id,))
        if VECTOR_AVAILABLE and chunks[0].embedding and _is_pgvector_embedding_column(conn):
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


def _is_pgvector_embedding_column(conn) -> bool:
    """兼容旧库：只有 embedding 列真实为 vector 类型时才使用 pgvector adapter。"""
    with conn.cursor() as cur:
        cur.execute(
            "SELECT udt_name FROM information_schema.columns "
            "WHERE table_name = 'document_chunk' AND column_name = 'embedding'"
        )
        row = cur.fetchone()
    return bool(row and row[0] == "vector")
