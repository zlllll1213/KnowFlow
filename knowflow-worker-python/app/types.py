"""
共享类型定义。
"""

from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass
class ParseTask:
    id: int
    document_id: int
    kb_id: int
    status: str
    error_message: str = ""


@dataclass
class Document:
    id: int
    kb_id: int
    user_id: int
    file_name: str
    file_path: str  # MinIO object key 或本地路径
    file_size: int
    file_type: str
    status: str
    chunk_count: int = 0


@dataclass
class DocumentChunk:
    document_id: int
    kb_id: int
    chunk_index: int
    content: str
    embedding: Optional[list[float]] = None


# 任务状态常量
class TaskStatus:
    PENDING = "PENDING"
    PROCESSING = "PROCESSING"
    PARSING = "PARSING"
    EMBEDDING = "EMBEDDING"
    DONE = "DONE"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


# 文档状态常量
class DocStatus:
    UPLOADED = "UPLOADED"
    PARSING = "PARSING"
    EMBEDDING = "EMBEDDING"
    DONE = "DONE"
    FAILED = "FAILED"
