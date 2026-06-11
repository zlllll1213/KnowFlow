"""
数据库操作模块。
查询/更新 parse_task、document、写入 document_chunk。
"""

import logging
import json
import threading
from datetime import datetime, timezone

import psycopg2
import psycopg2.extras
from psycopg2 import pool

from app.config import config
from app.types import ParseTask, Document, DocumentChunk, TaskStatus, DocStatus

log = logging.getLogger(__name__)

# 注册 psycopg2 的 VECTOR 适配器（如果安装了 pgvector）
try:
    from pgvector.psycopg2 import register_vector
    VECTOR_AVAILABLE = True
except ImportError:
    VECTOR_AVAILABLE = False

# 线程安全连接池（启动时初始化）
_connection_pool: "pool.ThreadedConnectionPool | None" = None
_pool_lock = threading.Lock()


def init_pool(minconn: int = 1, maxconn: int = 4):
    """初始化数据库连接池（线程安全）。"""
    global _connection_pool
    with _pool_lock:
        if _connection_pool is not None:
            return
        _connection_pool = pool.ThreadedConnectionPool(minconn, maxconn, config.db_dsn)
        log.info("DB 连接池已初始化: min=%d, max=%d", minconn, maxconn)


def get_connection():
    """从连接池获取数据库连接。如果连接池不可用则回退到新建连接。"""
    if _connection_pool is not None:
        return _connection_pool.getconn()
    return psycopg2.connect(config.db_dsn)


def put_connection(conn):
    """归还连接池或关闭连接。"""
    if _connection_pool is not None:
        _connection_pool.putconn(conn)
    else:
        conn.close()


def close_pool():
    """关闭连接池。"""
    global _connection_pool
    with _pool_lock:
        if _connection_pool is not None:
            _connection_pool.closeall()
            _connection_pool = None


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
    只有 PENDING/FAILED，或已经超时卡住的 PROCESSING/PARSING/EMBEDDING 任务可以被认领。
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
                      status IN (%s, %s, %s)
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
                TaskStatus.PARSING,
                TaskStatus.EMBEDDING,
                stale_minutes,
            ),
        )
        row = cur.fetchone()
    conn.commit()
    if row is None:
        return None
    return ParseTask(**row)


def list_recoverable_tasks(conn, stale_minutes: int, limit: int) -> list[int]:
    """列出启动时需要重新投递的 PENDING/超时 PROCESSING/PARSING/EMBEDDING 任务。"""
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id
            FROM parse_task
            WHERE status = %s
               OR (
                   status IN (%s, %s, %s)
                   AND updated_at < NOW() - (%s * INTERVAL '1 minute')
               )
            ORDER BY updated_at ASC, id ASC
            LIMIT %s
            """,
            (TaskStatus.PENDING, TaskStatus.PROCESSING, TaskStatus.PARSING, TaskStatus.EMBEDDING, stale_minutes, limit),
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
        if status == TaskStatus.FAILED:
            cur.execute(
                "UPDATE parse_task SET status = %s, error_message = %s, retry_count = COALESCE(retry_count, 0) + 1, "
                "last_error_at = %s, updated_at = %s WHERE id = %s",
                (status, error_message, datetime.now(timezone.utc), datetime.now(timezone.utc), task_id),
            )
        else:
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
    """批量插入 document_chunk（使用 executemany 提升性能）。"""
    if not chunks:
        return

    with conn.cursor() as cur:
        cur.execute("DELETE FROM document_chunk WHERE document_id = %s", (chunks[0].document_id,))
        if VECTOR_AVAILABLE and chunks[0].embedding and _is_pgvector_embedding_column():
            register_vector(conn)
            data = [
                (c.document_id, c.kb_id, c.chunk_index, c.content, c.embedding)
                for c in chunks
            ]
            cur.executemany(
                "INSERT INTO document_chunk (document_id, kb_id, chunk_index, content, embedding) "
                "VALUES (%s, %s, %s, %s, %s)",
                data,
            )
        else:
            data = [
                (
                    c.document_id,
                    c.kb_id,
                    c.chunk_index,
                    c.content,
                    json.dumps(c.embedding) if c.embedding else "[]",
                )
                for c in chunks
            ]
            cur.executemany(
                "INSERT INTO document_chunk (document_id, kb_id, chunk_index, content, embedding) "
                "VALUES (%s, %s, %s, %s, %s)",
                data,
            )
    conn.commit()
    log.info("已写入 %d 个 chunk 到数据库", len(chunks))


# 缓存 pgvector 列类型检测结果（启动时初始化一次）
_pgvector_column_cached: bool | None = None


def init_pgvector_check():
    """启动时检查 document_chunk.embedding 列是否为 vector 类型。"""
    global _pgvector_column_cached
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT udt_name FROM information_schema.columns "
                "WHERE table_name = 'document_chunk' AND column_name = 'embedding'"
            )
            row = cur.fetchone()
        _pgvector_column_cached = bool(row and row[0] == "vector")
        log.info("pgvector 列检查: vector=%s", _pgvector_column_cached)
    finally:
        put_connection(conn)


def _is_pgvector_embedding_column(_conn=None) -> bool:
    """返回缓存的 pgvector 列类型检测结果。"""
    if _pgvector_column_cached is None:
        return False
    return _pgvector_column_cached


def check_database() -> None:
    conn = get_connection()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT 1")
            cur.fetchone()
    finally:
        put_connection(conn)
