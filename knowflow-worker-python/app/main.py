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
7. 生成 embedding（当前为 mock）
8. 写入 document_chunk
9. 更新 document/parse_task → DONE
10. 失败时更新状态 FAILED，记录错误信息
"""

import logging
import os
import sys
import tempfile
import time
import traceback
from pathlib import Path

# 确保 app 包可导入
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.config import config
from app.queue import create_redis_client, block_pop_task
from app.repository import (
    get_connection,
    fetch_task,
    fetch_document,
    update_task_status,
    update_document_status,
    insert_chunks,
)
from app.parser import parse_file, download_from_minio, download_from_local
from app.splitter import split_text
from app.embedding import generate_embeddings
from app.types import TaskStatus, DocStatus

# 日志配置
logging.basicConfig(
    level=getattr(logging, config.log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
log = logging.getLogger("worker")


def process_task(task_id: int):
    """处理单个解析任务。"""
    conn = get_connection()
    try:
        # 1. 查询任务
        task = fetch_task(conn, task_id)
        if task is None:
            log.warning("任务不存在: taskId=%d", task_id)
            return
        if task.status == TaskStatus.CANCELLED:
            log.info("任务已取消，跳过: taskId=%d", task_id)
            return

        log.info("开始处理任务: taskId=%d, docId=%d, kbId=%d",
                 task.id, task.document_id, task.kb_id)

        # 2. 更新任务状态 → PROCESSING
        update_task_status(conn, task.id, TaskStatus.PROCESSING)

        # 3. 查询文档
        doc = fetch_document(conn, task.document_id)
        if doc is None:
            raise RuntimeError(f"文档不存在: docId={task.document_id}")

        # 4. 更新文档状态 → PARSING
        update_document_status(conn, doc.id, DocStatus.PARSING)

        # 5. 下载原始文件到临时目录
        with tempfile.TemporaryDirectory() as tmpdir:
            local_path = os.path.join(tmpdir, doc.file_name or "document")
            _download_file(doc.file_path, local_path)

            # 6. 解析文件
            text = parse_file(local_path, doc.file_type)

        # 7. 切片
        chunks = split_text(text, doc.id, doc.kb_id)

        if not chunks:
            log.warning("文档无有效内容: docId=%d", doc.id)
            update_document_status(conn, doc.id, DocStatus.DONE, 0)
            update_task_status(conn, task.id, TaskStatus.DONE)
            return

        # 8. 更新文档状态 → EMBEDDING
        update_document_status(conn, doc.id, DocStatus.EMBEDDING)

        # 9. 生成 embedding（当前为 mock）
        generate_embeddings(chunks)

        # 10. 写入 document_chunk
        insert_chunks(conn, chunks)

        # 11. 更新状态 → DONE
        update_document_status(conn, doc.id, DocStatus.DONE, len(chunks))
        update_task_status(conn, task.id, TaskStatus.DONE)

        log.info("任务处理完成: taskId=%d, docId=%d, chunks=%d",
                 task.id, doc.id, len(chunks))

    except Exception as e:
        log.error("任务处理失败: taskId=%d, error=%s", task_id, e)
        traceback.print_exc()
        try:
            update_task_status(conn, task_id, TaskStatus.FAILED, str(e))
            # 同时更新文档状态
            task = fetch_task(conn, task_id)
            if task:
                update_document_status(conn, task.document_id, DocStatus.FAILED)
        except Exception:
            log.error("更新失败状态时出错", exc_info=True)
    finally:
        conn.close()


def _download_file(file_path: str, local_path: str):
    """根据存储类型下载文件。"""
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


def main():
    """Worker 主循环。"""
    log.info("KnowFlow Python Worker 启动")
    log.info("配置: redis=%s, db=%s, storage=%s, embedding=%s, concurrency=%d",
             config.redis_url, config.db_dsn,
             config.storage_type, config.embedding_provider, config.concurrency)

    redis_client = create_redis_client()

    # 简单的单线程循环（后续可改为多线程/多进程）
    while True:
        try:
            task_id = block_pop_task(redis_client, timeout=5)
            if task_id is None:
                continue
            process_task(task_id)
        except KeyboardInterrupt:
            log.info("Worker 收到停止信号，退出")
            break
        except Exception as e:
            log.error("Worker 主循环异常: %s", e, exc_info=True)
            time.sleep(5)  # 异常后等待一段时间

    redis_client.close()


if __name__ == "__main__":
    main()
