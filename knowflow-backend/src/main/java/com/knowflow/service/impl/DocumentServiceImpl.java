package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowflow.common.BusinessException;
import com.knowflow.entity.Document;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.DocumentMapper;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.service.DocumentService;
import com.knowflow.service.TaskService;
import com.knowflow.vo.DocumentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final KnowledgeBaseMapper kbMapper;
    private final TaskService taskService;

    /** 本地存储根目录 */
    private static final String STORAGE_DIR = "./storage";

    @Override
    public DocumentVO upload(Long userId, Long kbId, MultipartFile file) {
        // 校验知识库归属
        checkKbOwnership(userId, kbId);

        // 保存文件到本地
        String storedPath = saveToLocal(file);

        // 创建文档记录
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setUserId(userId);
        doc.setFileName(file.getOriginalFilename());
        doc.setFilePath(storedPath);
        doc.setFileSize(file.getSize());
        doc.setFileType(getExtension(file.getOriginalFilename()));
        doc.setStatus("UPLOADED");
        documentMapper.insert(doc);

        // 创建解析任务
        taskService.createParseTask(doc.getId(), kbId);

        return toVO(doc);
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
    public void delete(Long userId, Long docId) {
        Document doc = getAndCheckOwner(userId, docId);
        documentMapper.deleteById(doc.getId());
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

    private String saveToLocal(MultipartFile file) {
        try {
            Path dir = Paths.get(STORAGE_DIR);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String originalName = file.getOriginalFilename();
            String storedName = UUID.randomUUID() + "_" + (originalName != null ? originalName : "file");
            Path target = dir.resolve(storedName);
            file.transferTo(target.toFile());

            log.info("文件已保存到: {}", target.toAbsolutePath());
            return target.toString();
        } catch (IOException e) {
            throw new BusinessException(50001, "文件保存失败: " + e.getMessage());
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private DocumentVO toVO(Document doc) {
        return DocumentVO.builder()
                .id(doc.getId())
                .kbId(doc.getKbId())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .status(doc.getStatus())
                .errorMessage(doc.getErrorMessage())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }
}
