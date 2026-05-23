"""
Embedding 生成模块。
第一版提供 mock embedding（全零向量），后续接入 OpenAI / Ollama 等真实 API。
"""

import logging
from app.config import config
from app.types import DocumentChunk

log = logging.getLogger(__name__)


def generate_embeddings(chunks: list[DocumentChunk]) -> None:
    """
    为每个 chunk 生成 embedding 向量，原地修改。
    """
    provider = config.embedding_provider.lower()

    if provider == "mock":
        _mock_embed(chunks)
    elif provider == "openai":
        _openai_embed(chunks)
    elif provider == "ollama":
        _ollama_embed(chunks)
    else:
        raise ValueError(f"不支持的 embedding provider: {provider}")


def _mock_embed(chunks: list[DocumentChunk]) -> None:
    """
    模拟 embedding — 使用全零向量。
    明确标记为 MOCK，仅用于开发测试流程。
    """
    dim = config.embedding_dim
    mock_vec = [0.0] * dim
    for chunk in chunks:
        chunk.embedding = mock_vec
    log.warning("MOCK embedding: 生成了 %d 个零向量 (dim=%d)", len(chunks), dim)


def _openai_embed(chunks: list[DocumentChunk]) -> None:
    """
    调用 OpenAI / 兼容 API 生成 embedding。
    TODO: 实现真实 API 调用。
    """
    # import httpx
    # texts = [c.content for c in chunks]
    # resp = httpx.post(
    #     f"{config.embedding_base_url}/embeddings",
    #     headers={"Authorization": f"Bearer {config.embedding_api_key}"},
    #     json={"model": config.embedding_model, "input": texts},
    # )
    # data = resp.json()
    # for i, chunk in enumerate(chunks):
    #     chunk.embedding = data["data"][i]["embedding"]
    raise NotImplementedError("OpenAI embedding 尚未实现 — 请先配置 API key 和 base URL")


def _ollama_embed(chunks: list[DocumentChunk]) -> None:
    """
    调用 Ollama 本地 API 生成 embedding。
    TODO: 实现。
    """
    raise NotImplementedError("Ollama embedding 尚未实现")
