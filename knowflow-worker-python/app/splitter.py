"""
文本切片器。
按 token 数量切片，维护 chunk_index 和 overlap。
"""

import logging
import re
from app.config import config
from app.types import DocumentChunk

log = logging.getLogger(__name__)


def split_text(text: str, document_id: int, kb_id: int, start_index: int = 0) -> list[DocumentChunk]:
    """
    将文本切分为固定大小的 chunk。
    当前使用简单字符数估算（中文 ≈ 1 token/字，英文 ≈ 1 token/4 字符），
    后续可替换为 tiktoken 精确分词。
    """
    chunk_size = config.chunk_size
    overlap = config.chunk_overlap
    max_chunks = config.max_chunks_per_doc

    # 简单估算：1 字符 ≈ 0.5 token（中文）、0.25 token（英文）
    # 这里用字符数 * 1.5 作为上限，确保不超 token 限制
    char_limit = chunk_size * 2
    overlap_chars = overlap * 2

    chunks: list[DocumentChunk] = []
    pos = 0
    text_len = len(text)

    while pos < text_len and len(chunks) < max_chunks:
        end = min(pos + char_limit, text_len)

        # 尽量在句子边界切断
        chunk_text = text[pos:end]
        if end < text_len:
            # 向后找最近的句号、换行、空格
            for sep in ["\n\n", "\n", "。", ". ", " "]:
                last = chunk_text.rfind(sep)
                if last > char_limit // 2:
                    end = pos + last + len(sep)
                    chunk_text = text[pos:end]
                    break

        chunk_text = chunk_text.strip()
        if chunk_text:
            chunks.append(DocumentChunk(
                document_id=document_id,
                kb_id=kb_id,
                chunk_index=start_index + len(chunks),
                content=chunk_text,
            ))

        pos = end - overlap_chars if end - overlap_chars > pos else end

    log.info("文本切片完成: docId=%d, chunks=%d", document_id, len(chunks))
    return chunks
