"""
文档解析器。
支持 TXT、Markdown，预留 PDF、DOCX 扩展点。
"""

import logging
import os
from pathlib import Path

log = logging.getLogger(__name__)


def parse_file(file_path: str, file_type: str) -> str:
    """
    解析文件内容为纯文本。
    file_path 为本地临时文件路径。
    """
    file_type_lower = file_type.lower().lstrip(".")

    parsers = {
        "txt": _parse_txt,
        "md": _parse_markdown,
        "markdown": _parse_markdown,
        "pdf": _parse_pdf,
        "docx": _parse_docx,
    }

    parser = parsers.get(file_type_lower)
    if parser is None:
        raise ValueError(f"不支持的文件类型: {file_type_lower}")

    log.info("开始解析文件: type=%s, path=%s", file_type_lower, file_path)
    text = parser(file_path)
    log.info("文件解析完成: type=%s, chars=%d", file_type_lower, len(text))
    return text


def _parse_txt(file_path: str) -> str:
    """解析纯文本文件。"""
    with open(file_path, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


def _parse_markdown(file_path: str) -> str:
    """
    解析 Markdown 文件。
    第一版简单去除 Markdown 语法标记，保留纯文本。
    后续可接入 markdown 库做更精细处理。
    """
    try:
        import markdown as md_lib
        from io import StringIO
        from html.parser import HTMLParser

        with open(file_path, "r", encoding="utf-8", errors="replace") as f:
            md_content = f.read()

        # 转为 HTML 再剥离标签
        html = md_lib.markdown(md_content)

        class MLStripper(HTMLParser):
            def __init__(self):
                super().__init__()
                self.text = StringIO()

            def handle_data(self, data):
                self.text.write(data)

        stripper = MLStripper()
        stripper.feed(html)
        return stripper.text.getvalue()
    except ImportError:
        # 无 markdown 库时回退为简单处理：移除常见 MD 标记
        return _parse_txt(file_path)


def _parse_pdf(file_path: str) -> str:
    """
    解析 PDF 文件。
    TODO: 使用 pypdf 或 pdfplumber 实现。
    当前返回占位提示。
    """
    try:
        from pypdf import PdfReader
        reader = PdfReader(file_path)
        text_parts = []
        for page in reader.pages:
            page_text = page.extract_text()
            if page_text:
                text_parts.append(page_text)
        return "\n\n".join(text_parts)
    except ImportError:
        raise RuntimeError(
            "PDF 解析需要安装 pypdf: pip install pypdf"
        )
    except Exception as e:
        raise RuntimeError(f"PDF 解析失败: {e}")


def _parse_docx(file_path: str) -> str:
    """
    解析 DOCX 文件。
    TODO: 使用 python-docx 实现。
    当前返回占位提示。
    """
    try:
        from docx import Document as DocxDocument
        doc = DocxDocument(file_path)
        text_parts = [p.text for p in doc.paragraphs if p.text.strip()]
        return "\n\n".join(text_parts)
    except ImportError:
        raise RuntimeError(
            "DOCX 解析需要安装 python-docx: pip install python-docx"
        )
    except Exception as e:
        raise RuntimeError(f"DOCX 解析失败: {e}")


# ---------- 文件下载 ----------

def download_from_minio(client, bucket: str, object_key: str, local_path: str):
    """从 MinIO 下载文件到本地。"""
    log.info("从 MinIO 下载: bucket=%s, key=%s", bucket, object_key)
    client.fget_object(bucket, object_key, local_path)


def download_from_local(storage_root: str, object_key: str, local_path: str):
    """从本地存储复制文件。"""
    src = Path(storage_root) / object_key
    log.info("从本地复制: src=%s", src)
    if not src.exists():
        raise FileNotFoundError(f"文件不存在: {src}")
    import shutil
    shutil.copy2(src, local_path)
