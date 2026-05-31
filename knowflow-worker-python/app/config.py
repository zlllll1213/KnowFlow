"""
KnowFlow Python Worker — 配置管理。
所有配置从环境变量读取，开发时可通过 .env 文件覆盖。
"""

import os
from dataclasses import dataclass
from pathlib import Path

# 加载 .env 文件（如果存在）
try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass


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
    db_dsn_override: str = os.getenv("WORKER_DB_DSN", "")

    @property
    def db_dsn(self) -> str:
        if self.db_dsn_override:
            return self.db_dsn_override
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
    embedding_batch_size: int = int(os.getenv("WORKER_EMBEDDING_BATCH_SIZE", "64"))
    embedding_timeout_seconds: int = int(os.getenv("WORKER_EMBEDDING_TIMEOUT_SECONDS", "60"))

    # 切片参数
    chunk_size: int = int(os.getenv("WORKER_CHUNK_SIZE", "512"))
    chunk_overlap: int = int(os.getenv("WORKER_CHUNK_OVERLAP", "64"))
    max_chunks_per_doc: int = int(os.getenv("WORKER_MAX_CHUNKS_PER_DOC", "1000"))

    # Worker 并发数
    concurrency: int = int(os.getenv("WORKER_CONCURRENCY", "2"))
    task_claim_stale_minutes: int = int(os.getenv("WORKER_TASK_CLAIM_STALE_MINUTES", "30"))
    task_recovery_on_start: bool = os.getenv("WORKER_TASK_RECOVERY_ON_START", "true").lower() == "true"
    task_recovery_limit: int = int(os.getenv("WORKER_TASK_RECOVERY_LIMIT", "500"))
    task_recovery_interval_seconds: int = int(os.getenv("WORKER_TASK_RECOVERY_INTERVAL_SECONDS", "60"))

    # 日志
    log_level: str = os.getenv("WORKER_LOG_LEVEL", "INFO")

    def validate(self) -> list[str]:
        """返回配置错误列表。空列表表示配置可用。"""
        errors: list[str] = []

        if self.storage_type not in {"local", "minio"}:
            errors.append("WORKER_STORAGE_TYPE 仅支持 local/minio")
        if self.storage_type == "local" and not self.storage_local_path:
            errors.append("WORKER_STORAGE_LOCAL_PATH 不能为空")
        if self.storage_type == "minio":
            if not self.minio_endpoint:
                errors.append("WORKER_MINIO_ENDPOINT 不能为空")
            if not self.minio_access_key:
                errors.append("WORKER_MINIO_ACCESS_KEY 不能为空")
            if not self.minio_secret_key:
                errors.append("WORKER_MINIO_SECRET_KEY 不能为空")
            if not self.minio_bucket:
                errors.append("WORKER_MINIO_BUCKET 不能为空")

        if self.embedding_provider not in {"mock", "openai", "deepseek", "ollama"}:
            errors.append("WORKER_EMBEDDING_PROVIDER 仅支持 mock/openai/deepseek/ollama")
        if self.embedding_provider in {"openai", "deepseek"} and not self.embedding_api_key:
            errors.append("WORKER_EMBEDDING_API_KEY 不能为空")
        if self.embedding_dim <= 0:
            errors.append("WORKER_EMBEDDING_DIM 必须大于 0")
        if self.embedding_batch_size <= 0:
            errors.append("WORKER_EMBEDDING_BATCH_SIZE 必须大于 0")
        if self.embedding_timeout_seconds <= 0:
            errors.append("WORKER_EMBEDDING_TIMEOUT_SECONDS 必须大于 0")

        if self.chunk_size <= 0:
            errors.append("WORKER_CHUNK_SIZE 必须大于 0")
        if self.chunk_overlap < 0:
            errors.append("WORKER_CHUNK_OVERLAP 不能小于 0")
        if self.chunk_overlap >= self.chunk_size:
            errors.append("WORKER_CHUNK_OVERLAP 必须小于 WORKER_CHUNK_SIZE")
        if self.max_chunks_per_doc <= 0:
            errors.append("WORKER_MAX_CHUNKS_PER_DOC 必须大于 0")
        if self.concurrency <= 0:
            errors.append("WORKER_CONCURRENCY 必须大于 0")
        if self.task_claim_stale_minutes <= 0:
            errors.append("WORKER_TASK_CLAIM_STALE_MINUTES 必须大于 0")
        if self.task_recovery_limit <= 0:
            errors.append("WORKER_TASK_RECOVERY_LIMIT 必须大于 0")
        if self.task_recovery_interval_seconds < 0:
            errors.append("WORKER_TASK_RECOVERY_INTERVAL_SECONDS 不能小于 0")

        return errors

    def ensure_local_storage_dir(self):
        if self.storage_type == "local":
            Path(self.storage_local_path).mkdir(parents=True, exist_ok=True)


# 全局单例
config = Config()
