"""
KnowFlow Python Worker — 入口。
启动后循环消费 Redis 队列中的解析任务。

工作流:
1. BLPOP knowflow:parse:queue
2. 查询 parse_task → 更新状态 PROCESSING
3. 查询 document → 更新状态 PARSING
4. 下载原始文件
5. 解析文件内容（TXT / MD / PDF / DOCX）
6. 文本切片
7. 生成 embedding
8. 写入 document_chunk
9. 更新 document/parse_task → DONE
10. 失败时更新状态 FAILED，记录错误信息
"""

import logging
import os
import sys
import tempfile
import time
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
# 确保 app 包可导入
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.config import config

# 日志配置
logging.basicConfig(
    level=getattr(logging, config.log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
log = logging.getLogger("worker")


def _call_with_retry(fn, max_attempts: int = 3, step_name: str = ""):
    """对瞬态故障指数退避重试。"""
    base_delay = 2.0
    for attempt in range(1, max_attempts + 1):
        try:
            return fn()
        except Exception as e:
            if attempt >= max_attempts:
                raise
            if not _is_transient_error(e):
                raise
            delay = base_delay * (2 ** (attempt - 1))
            log.warning("%s 瞬态故障，%d 秒后重试 (attempt=%d/%d): %s",
                        step_name, delay, attempt, max_attempts, e)
            time.sleep(delay)


def _is_transient_error(exc: Exception) -> bool:
    """判断异常是否为瞬态故障。"""
    try:
        import httpx
        if isinstance(exc, httpx.HTTPStatusError):
            return exc.response.status_code in {429, 500, 502, 503, 504}
        if isinstance(exc, (httpx.ConnectError, httpx.TimeoutException, httpx.RemoteProtocolError)):
            return True
    except ImportError:
        pass
    msg = str(exc).lower()
    return any(kw in msg for kw in ("connection reset", "connection refused", "timeout", "temporarily unavailable"))


def process_task(task_id: int):
    """处理单个解析任务。"""
    from app.embedding import generate_embeddings
    from app.parser import parse_file
    from app.repository import (
        claim_task,
        fetch_document,
        fetch_task,
        get_connection,
        put_connection,
        insert_chunks,
        update_document_status,
        update_task_status,
    )
    from app.splitter import split_text
    from app.types import DocStatus, TaskStatus

    conn = get_connection()
    try:
        # 1. 原子认领任务，避免重复消费和覆盖已完成状态
        task = claim_task(conn, task_id, config.task_claim_stale_minutes)
        if task is None:
            current = fetch_task(conn, task_id)
            if current is None:
                log.warning("任务不存在: taskId=%d", task_id)
            else:
                log.info("任务当前不可处理，跳过: taskId=%d, status=%s", task_id, current.status)
            return

        log.info("开始处理任务: taskId=%d, docId=%d, kbId=%d",
                 task.id, task.document_id, task.kb_id)

        # 2. 查询文档
        doc = fetch_document(conn, task.document_id)
        if doc is None:
            raise RuntimeError(f"文档不存在: docId={task.document_id}")

        # 3. 更新文档状态 → PARSING
        update_task_status(conn, task.id, TaskStatus.PARSING)
        update_document_status(conn, doc.id, DocStatus.PARSING)

        # 4. 下载原始文件到临时目录
        with tempfile.TemporaryDirectory() as tmpdir:
            local_path = os.path.join(tmpdir, doc.file_name or "document")
            _download_file(doc.file_path, local_path)

            # 5. 解析文件
            text = parse_file(local_path, doc.file_type)

        # 6. 切片
        chunks = split_text(text, doc.id, doc.kb_id)

        if not chunks:
            log.warning("文档无有效内容: docId=%d", doc.id)
            update_document_status(conn, doc.id, DocStatus.DONE, 0)
            update_task_status(conn, task.id, TaskStatus.DONE)
            return

        # 7. 更新文档状态 → EMBEDDING
        update_task_status(conn, task.id, TaskStatus.EMBEDDING)
        update_document_status(conn, doc.id, DocStatus.EMBEDDING)

        # 8. 生成 embedding（瞬态故障自动重试）
        _call_with_retry(lambda: generate_embeddings(chunks), max_attempts=3, step_name="embedding")

        # 9. 写入 document_chunk
        insert_chunks(conn, chunks)

        # 10. 更新状态 → DONE
        update_document_status(conn, doc.id, DocStatus.DONE, len(chunks))
        update_task_status(conn, task.id, TaskStatus.DONE)

        log.info("任务处理完成: taskId=%d, docId=%d, chunks=%d",
                 task.id, doc.id, len(chunks))

    except Exception as e:
        log.exception("任务处理失败: taskId=%d, error=%s", task_id, e)
        try:
            error_message = str(e)
            update_task_status(conn, task_id, TaskStatus.FAILED, error_message)
            # 同时更新文档状态
            task = fetch_task(conn, task_id)
            if task:
                update_document_status(conn, task.document_id, DocStatus.FAILED, error_message=error_message)
        except Exception:
            log.error("更新失败状态时出错", exc_info=True)
    finally:
        put_connection(conn)


def _download_file(file_path: str, local_path: str):
    """根据存储类型下载文件。"""
    from app.parser import download_from_local, download_from_minio

    if config.storage_type == "minio":
        from minio import Minio
        client = Minio(
            config.minio_endpoint,
            access_key=config.minio_access_key,
            secret_key=config.minio_secret_key,
            secure=config.minio_secure,
        )
        download_from_minio(client, config.minio_bucket, file_path, local_path)
    else:
        download_from_local(config.storage_local_path, file_path, local_path)


def recover_tasks_on_start(redis_client):
    """启动时恢复未投递或卡住的解析任务。"""
    if not config.task_recovery_on_start:
        return

    from app.queue import push_task
    from app.repository import get_connection, put_connection, list_recoverable_tasks

    conn = get_connection()
    try:
        task_ids = list_recoverable_tasks(
            conn,
            config.task_claim_stale_minutes,
            config.task_recovery_limit,
        )
    finally:
        put_connection(conn)

    for task_id in task_ids:
        push_task(redis_client, task_id)

    if task_ids:
        log.info("启动恢复任务已重新入队: count=%d, ids=%s", len(task_ids), task_ids[:20])
    else:
        log.info("启动恢复检查完成，无需恢复的任务")


def run_checks():
    from app.queue import create_redis_client
    from app.repository import check_database

    log.info("检查 Redis 连接")
    redis_client = create_redis_client()
    try:
        redis_client.ping()
    finally:
        redis_client.close()

    log.info("检查 PostgreSQL 连接")
    check_database()

    if config.storage_type == "minio":
        from minio import Minio
        client = Minio(
            config.minio_endpoint,
            access_key=config.minio_access_key,
            secret_key=config.minio_secret_key,
            secure=config.minio_secure,
        )
        if not client.bucket_exists(config.minio_bucket):
            raise RuntimeError(f"MinIO bucket 不存在: {config.minio_bucket}")
    else:
        root = Path(config.storage_local_path).resolve()
        root.mkdir(parents=True, exist_ok=True)
        test_file = root / ".knowflow-worker-check"
        test_file.write_text("ok", encoding="utf-8")
        test_file.unlink(missing_ok=True)


def main():
    """Worker 主循环。"""
    errors = config.validate()
    if errors:
        for error in errors:
            log.error("配置错误: %s", error)
        raise SystemExit(2)
    config.ensure_local_storage_dir()

    if "--check" in sys.argv:
        run_checks()
        log.info("KnowFlow Python Worker 配置检查通过")
        return

    log.info("KnowFlow Python Worker 启动")
    log.info("配置: redis=%s, db=%s, storage=%s, embedding=%s, concurrency=%d",
             config.redis_url, config.db_dsn,
             config.storage_type, config.embedding_provider, config.concurrency)

    from app.queue import block_pop_task, create_redis_client
    from app.repository import init_pool, close_pool, init_pgvector_check

    init_pool(minconn=1, maxconn=max(4, config.concurrency + 1))
    init_pgvector_check()
    redis_client = create_redis_client()
    recover_tasks_on_start(redis_client)

    with ThreadPoolExecutor(max_workers=config.concurrency) as executor:
        while True:
            try:
                task_id = block_pop_task(redis_client, timeout=5)
                if task_id is None:
                    continue
                executor.submit(process_task, task_id)
            except KeyboardInterrupt:
                log.info("Worker 收到停止信号，退出")
                break
            except GeneratorExit:
                break
            except Exception as e:
                log.error("Worker 主循环异常: %s", e, exc_info=True)
                time.sleep(5)

    redis_client.close()
    close_pool()


if __name__ == "__main__":
    main()
