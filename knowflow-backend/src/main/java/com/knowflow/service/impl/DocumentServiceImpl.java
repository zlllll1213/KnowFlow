package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.BusinessException;
import com.knowflow.entity.Document;
import com.knowflow.entity.DocumentChunk;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.entity.ParseTask;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.mapper.DocumentMapper;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.mapper.ParseTaskMapper;
import com.knowflow.service.DocumentService;
import com.knowflow.service.TaskService;
import com.knowflow.util.FileStorageService;
import com.knowflow.vo.DocumentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final ParseTaskMapper parseTaskMapper;
    private final TaskService taskService;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public DocumentVO upload(Long userId, Long kbId, MultipartFile file) {
        checkKbOwnership(userId, kbId);

        String originalName = file.getOriginalFilename();
        String objectKey = buildObjectKey(userId, kbId, originalName);

        String storedPath = fileStorageService.upload(file, objectKey);

        try {
            Document doc = new Document();
            doc.setKbId(kbId);
            doc.setUserId(userId);
            doc.setFileName(originalName);
            doc.setFilePath(storedPath);
            doc.setFileSize(file.getSize());
            doc.setFileType(getExtension(originalName));
            doc.setStatus("UPLOADED");
            doc.setChunkCount(0);
            documentMapper.insert(doc);

            taskService.createParseTask(doc.getId(), kbId);

            log.info("文档上传完成: docId={}, kbId={}, file={}, size={}",
                    doc.getId(), kbId, originalName, file.getSize());

            return toVO(doc);
        } catch (RuntimeException e) {
            safeDeleteFile(storedPath);
            throw e;
        }
    }

    @Override
    public List<DocumentVO> listByKb(Long userId, Long kbId) {
        checkKbOwnership(userId, kbId);

        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKbId, kbId)
                        .eq(Document::getUserId, userId)
                        .orderByDesc(Document::getCreatedAt));
        return docs.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public DocumentVO getById(Long userId, Long docId) {
        Document doc = getAndCheckOwner(userId, docId);
        return toVO(doc);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long docId) {
        Document doc = getAndCheckOwner(userId, docId);

        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, doc.getId()));
        parseTaskMapper.delete(new LambdaQueryWrapper<ParseTask>()
                .eq(ParseTask::getDocumentId, doc.getId()));
        documentMapper.deleteById(doc.getId());
        safeDeleteFile(doc.getFilePath());
    }

    @Override
    public String getStatus(Long userId, Long docId) {
        Document doc = getAndCheckOwner(userId, docId);
        return doc.getStatus();
    }

    // ---------- 私有方法 ----------

    private void checkKbOwnership(Long userId, Long kbId) {
        KnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(40020, "知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException(40030, "无权访问该知识库");
        }
    }

    private Document getAndCheckOwner(Long userId, Long docId) {
        Document doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new BusinessException(40021, "文档不存在");
        }
        if (!doc.getUserId().equals(userId)) {
            throw new BusinessException(40030, "无权访问该文档");
        }
        return doc;
    }

    /**
     * 构建存储 object key。
     * 格式: {userId}/{kbId}/{uuid}_{originalName}
     */
    private String buildObjectKey(Long userId, Long kbId, String originalName) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String safeName = sanitizeFileName(originalName);
        return String.format("%d/%d/%s_%s", userId, kbId, uuid, safeName);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private String sanitizeFileName(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }

        String safeName = filename
                .replaceAll("[\\\\/]+", "_")
                .replaceAll("[\\p{Cntrl}]+", "")
                .replaceAll("[^\\p{L}\\p{N}._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^\\.+", "")
                .trim();

        if (safeName.isBlank()) {
            return "file";
        }
        if (safeName.length() > 120) {
            return safeName.substring(safeName.length() - 120);
        }
        return safeName;
    }

    private void safeDeleteFile(String filePath) {
        try {
            fileStorageService.delete(filePath);
        } catch (RuntimeException e) {
            log.warn("清理文件失败: {}", filePath, e);
        }
    }

    private DocumentVO toVO(Document doc) {
        return DocumentVO.builder()
                .id(doc.getId())
                .kbId(doc.getKbId())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .chunkCount(doc.getChunkCount())
                .errorMessage(doc.getErrorMessage())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
