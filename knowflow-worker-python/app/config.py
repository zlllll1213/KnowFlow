"""
KnowFlow Python Worker — 配置管理。
所有配置从环境变量读取，开发时可通过 .env 文件覆盖。
"""

import os
from dataclasses import dataclass, field


@dataclass
class Config:
    # Redis
    redis_url: str = os.getenv("WORKER_REDIS_URL", "redis://localhost:6379/0")

    # PostgreSQL
    db_host: str = os.getenv("WORKER_DB_HOST", "localhost")
    db_port: int = int(os.getenv("WORKER_DB_PORT", "5432"))
    db_name: str = os.getenv("WORKER_DB_NAME", "knowflow")
    db_user: str = os.getenv("WORKER_DB_USER", "knowflow")
    db_password: str = os.getenv("WORKER_DB_PASSWORD", "knowflow123")

    @property
    def db_dsn(self) -> str:
        return f"postgresql://{self.db_user}:{self.db_password}@{self.db_host}:{self.db_port}/{self.db_name}"

    # MinIO
    minio_endpoint: str = os.getenv("WORKER_MINIO_ENDPOINT", "localhost:9000")
    minio_access_key: str = os.getenv("WORKER_MINIO_ACCESS_KEY", "minioadmin")
    minio_secret_key: str = os.getenv("WORKER_MINIO_SECRET_KEY", "minioadmin123")
    minio_bucket: str = os.getenv("WORKER_MINIO_BUCKET", "knowflow")
    minio_secure: bool = os.getenv("WORKER_MINIO_SECURE", "false").lower() == "true"

    # 存储类型: local | minio
    storage_type: str = os.getenv("WORKER_STORAGE_TYPE", "local")
    storage_local_path: str = os.getenv("WORKER_STORAGE_LOCAL_PATH", "../knowflow-backend/storage")

    # Embedding
    embedding_provider: str = os.getenv("WORKER_EMBEDDING_PROVIDER", "mock")
    embedding_api_key: str = os.getenv("WORKER_EMBEDDING_API_KEY", "")
    embedding_model: str = os.getenv("WORKER_EMBEDDING_MODEL", "text-embedding-3-small")
    embedding_dim: int = int(os.getenv("WORKER_EMBEDDING_DIM", "1536"))
    embedding_base_url: str = os.getenv("WORKER_EMBEDDING_BASE_URL", "")

    # 切片参数
    chunk_size: int = int(os.getenv("WORKER_CHUNK_SIZE", "512"))
    chunk_overlap: int = int(os.getenv("WORKER_CHUNK_OVERLAP", "64"))
    max_chunks_per_doc: int = int(os.getenv("WORKER_MAX_CHUNKS_PER_DOC", "1000"))

    # Worker 并发数
    concurrency: int = int(os.getenv("WORKER_CONCURRENCY", "2"))

    # 日志
    log_level: str = os.getenv("WORKER_LOG_LEVEL", "INFO")


# 全局单例
config = Config()
