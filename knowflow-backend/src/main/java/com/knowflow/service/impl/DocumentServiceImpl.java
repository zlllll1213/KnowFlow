package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.BusinessException;
import com.knowflow.common.PageResult;
import com.knowflow.entity.Document;
import com.knowflow.entity.DocumentChunk;
import com.knowflow.entity.ParseTask;
import com.knowflow.mapper.DocumentChunkMapper;
import com.knowflow.mapper.DocumentMapper;
import com.knowflow.mapper.ParseTaskMapper;
import com.knowflow.service.DocumentService;
import com.knowflow.service.TaskService;
import com.knowflow.service.security.OwnershipChecker;
import com.knowflow.util.FileStorageService;
import com.knowflow.vo.DocumentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final ParseTaskMapper parseTaskMapper;
    private final TaskService taskService;
    private final FileStorageService fileStorageService;
    private final OwnershipChecker ownershipChecker;
    private final long maxFileSizeBytes;

    public DocumentServiceImpl(DocumentMapper documentMapper,
                               DocumentChunkMapper documentChunkMapper,
                               ParseTaskMapper parseTaskMapper,
                               TaskService taskService,
                               FileStorageService fileStorageService,
                               OwnershipChecker ownershipChecker,
                               @Value("${knowflow.upload.max-file-size-bytes:52428800}") long maxFileSizeBytes) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.parseTaskMapper = parseTaskMapper;
        this.taskService = taskService;
        this.fileStorageService = fileStorageService;
        this.ownershipChecker = ownershipChecker;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    @Transactional
    public DocumentVO upload(Long userId, Long kbId, MultipartFile file) {
        ownershipChecker.requireKbOwner(userId, kbId);
        validateFile(file);

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

            Long parseTaskId = taskService.createParseTask(doc.getId(), kbId);

            log.info("文档上传完成: docId={}, kbId={}, file={}, size={}",
                    doc.getId(), kbId, originalName, file.getSize());

            return toVO(doc, parseTaskId);
        } catch (RuntimeException e) {
            safeDeleteFile(storedPath);
            throw e;
        }
    }

    @Override
    public PageResult<DocumentVO> listByKb(Long userId, Long kbId, long page, long size) {
        ownershipChecker.requireKbOwner(userId, kbId);

        Page<Document> result = documentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKbId, kbId)
                        .eq(Document::getUserId, userId)
                        .orderByDesc(Document::getCreatedAt));
        List<DocumentVO> records = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
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

        safeDeleteFile(doc.getFilePath());
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocumentId, doc.getId()));
        parseTaskMapper.delete(new LambdaQueryWrapper<ParseTask>()
                .eq(ParseTask::getDocumentId, doc.getId()));
        documentMapper.deleteById(doc.getId());
    }

    @Override
    public String getStatus(Long userId, Long docId) {
        Document doc = getAndCheckOwner(userId, docId);
        return doc.getStatus();
    }

    // ---------- 私有方法 ----------

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

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40040, "上传文件不能为空");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(40043, "文件大小不能超过 " + maxFileSizeBytes + " 字节");
        }
        String originalName = file.getOriginalFilename();
        String ext = getExtension(originalName);
        if (!List.of("pdf", "docx", "txt", "md", "markdown").contains(ext)) {
            throw new BusinessException(40041, "仅支持 PDF、DOCX、TXT、MD、Markdown 文件");
        }
        if (!matchesDeclaredType(file, ext)) {
            throw new BusinessException(40042, "文件类型与支持格式不匹配");
        }
    }

    private boolean matchesDeclaredType(MultipartFile file, String ext) {
        try {
            return switch (ext) {
                case "pdf" -> hasPrefix(file.getBytes(), "%PDF-".getBytes(StandardCharsets.US_ASCII));
                case "docx" -> isDocx(file);
                case "txt", "md", "markdown" -> isUtf8Text(file.getBytes());
                default -> false;
            };
        } catch (IOException e) {
            throw new BusinessException(40042, "文件类型与支持格式不匹配");
        }
    }

    private boolean hasPrefix(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isDocx(MultipartFile file) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean isUtf8Text(byte[] content) {
        for (byte value : content) {
            if (value == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
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
        return toVO(doc, findLatestParseTaskId(doc.getId()));
    }

    private DocumentVO toVO(Document doc, Long parseTaskId) {
        return DocumentVO.builder()
                .id(doc.getId())
                .kbId(doc.getKbId())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .chunkCount(doc.getChunkCount())
                .parseTaskId(parseTaskId)
                .errorMessage(doc.getErrorMessage())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private Long findLatestParseTaskId(Long docId) {
        ParseTask task = parseTaskMapper.selectOne(new LambdaQueryWrapper<ParseTask>()
                .eq(ParseTask::getDocumentId, docId)
                .orderByDesc(ParseTask::getId)
                .last("LIMIT 1"));
        return task == null ? null : task.getId();
    }
}
