"""
Redis 队列消费者。
使用 BLPOP 阻塞读取解析任务。
"""

import logging
import json
import redis
from app.config import config

log = logging.getLogger(__name__)

PARSE_QUEUE_KEY = "knowflow:parse:queue"


def create_redis_client() -> redis.Redis:
    """创建 Redis 客户端。"""
    return redis.Redis.from_url(config.redis_url, decode_responses=True)


def push_task(client: redis.Redis, task_id: int):
    """投递解析任务。"""
    client.rpush(PARSE_QUEUE_KEY, str(task_id))


def parse_task_id(raw_value: str) -> int:
    """兼容字符串数字、JSON 数字、JSON 字符串等历史入队格式。"""
    try:
        return int(raw_value)
    except (TypeError, ValueError):
        parsed = json.loads(raw_value)
        return int(parsed)


def block_pop_task(client: redis.Redis, timeout: int = 5) -> int | None:
    """
    阻塞等待队列中的任务。
    返回 taskId（int），超时返回 None。
    """
    result = client.blpop(PARSE_QUEUE_KEY, timeout=timeout)
    if result is None:
        return None
    _, task_id_str = result
    log.info("从队列获取任务: taskId=%s", task_id_str)
    return parse_task_id(task_id_str)
