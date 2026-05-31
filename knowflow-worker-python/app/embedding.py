"""
Embedding 生成模块。
支持 mock、OpenAI-compatible API 和 Ollama，本地开发可继续使用 mock。
"""

import logging
import math
from typing import Iterable

import httpx

from app.config import config
from app.types import DocumentChunk

log = logging.getLogger(__name__)


def generate_embeddings(chunks: list[DocumentChunk]) -> None:
    """为每个 chunk 生成 embedding 向量，原地修改。"""
    provider = config.embedding_provider.lower()

    if provider == "mock":
        _mock_embed(chunks)
    elif provider in {"openai", "deepseek"}:
        _openai_compatible_embed(chunks)
    elif provider == "ollama":
        _ollama_embed(chunks)
    else:
        raise ValueError(f"不支持的 embedding provider: {provider}")
    _validate_embedding_dimensions(chunks)


def _mock_embed(chunks: list[DocumentChunk]) -> None:
    dim = config.embedding_dim
    for chunk in chunks:
        chunk.embedding = _mock_embedding_for_text(chunk.content, dim)
    log.warning("MOCK embedding: 生成了 %d 个确定性非零向量 (dim=%d)", len(chunks), dim)


def _mock_embedding_for_text(text: str, dim: int) -> list[float]:
    if dim <= 0:
        raise RuntimeError(f"WORKER_EMBEDDING_DIM 必须大于 0: {dim}")

    vector = [0.0] * dim
    for char in (text or "").lower():
        if char.isspace():
            continue
        code = ord(char) & 0xFFFFFFFF
        hashed = (code * 2654435761) & 0xFFFFFFFF
        index = hashed % dim
        sign = 1.0 if ((hashed >> 8) & 1) == 0 else -1.0
        weight = 1.0 + ((hashed >> 16) % 7) / 10.0
        vector[index] += sign * weight

    norm = math.sqrt(sum(value * value for value in vector))
    if norm == 0:
        vector[0] = 1.0
        return vector
    return [value / norm for value in vector]


def _openai_compatible_embed(chunks: list[DocumentChunk]) -> None:
    if not config.embedding_api_key:
        raise RuntimeError("WORKER_EMBEDDING_API_KEY 未配置")

    base_url = (config.embedding_base_url or "https://api.openai.com/v1").rstrip("/")
    batch_size = config.embedding_batch_size
    headers = {"Authorization": f"Bearer {config.embedding_api_key}"}

    with httpx.Client(timeout=config.embedding_timeout_seconds) as client:
        for batch in _batched(chunks, batch_size):
            texts = [chunk.content for chunk in batch]
            resp = client.post(
                f"{base_url}/embeddings",
                headers=headers,
                json={"model": config.embedding_model, "input": texts},
            )
            resp.raise_for_status()
            payload = resp.json()
            data = sorted(payload.get("data", []), key=lambda item: item.get("index", 0))
            if len(data) != len(batch):
                raise RuntimeError(f"embedding 返回数量不匹配: expected={len(batch)}, got={len(data)}")
            for chunk, item in zip(batch, data):
                chunk.embedding = item["embedding"]

    log.info("OpenAI-compatible embedding 完成: chunks=%d, model=%s", len(chunks), config.embedding_model)


def _ollama_embed(chunks: list[DocumentChunk]) -> None:
    base_url = (config.embedding_base_url or "http://localhost:11434").rstrip("/")
    model = config.embedding_model or "nomic-embed-text"
    batch_size = config.embedding_batch_size

    with httpx.Client(timeout=config.embedding_timeout_seconds) as client:
        for batch in _batched(chunks, batch_size):
            texts = [chunk.content for chunk in batch]
            resp = client.post(
                f"{base_url}/api/embeddings",
                json={"model": model, "input": texts},
            )
            resp.raise_for_status()
            payload = resp.json()
            embeddings = payload.get("embeddings") or []
            if not embeddings:
                # 回退：可能是旧版 Ollama，尝试逐个请求
                for chunk in batch:
                    single_resp = client.post(
                        f"{base_url}/api/embeddings",
                        json={"model": model, "prompt": chunk.content},
                    )
                    single_resp.raise_for_status()
                    emb = single_resp.json().get("embedding")
                    if not emb:
                        raise RuntimeError("Ollama embedding 未返回向量")
                    chunk.embedding = emb
                continue
            if len(embeddings) != len(batch):
                raise RuntimeError(
                    f"Ollama embedding 返回数量不匹配: expected={len(batch)}, got={len(embeddings)}"
                )
            for chunk, emb in zip(batch, embeddings):
                chunk.embedding = emb

    log.info("Ollama embedding 完成: chunks=%d, model=%s", len(chunks), model)


def _batched(items: list[DocumentChunk], size: int) -> Iterable[list[DocumentChunk]]:
    size = max(size, 1)
    for idx in range(0, len(items), size):
        yield items[idx:idx + size]


def _validate_embedding_dimensions(chunks: list[DocumentChunk]) -> None:
    expected = config.embedding_dim
    for chunk in chunks:
        actual = len(chunk.embedding or [])
        if actual != expected:
            raise RuntimeError(
                f"embedding dimension mismatch: expected={expected}, actual={actual}, "
                f"docId={chunk.document_id}, chunkIndex={chunk.chunk_index}"
            )
