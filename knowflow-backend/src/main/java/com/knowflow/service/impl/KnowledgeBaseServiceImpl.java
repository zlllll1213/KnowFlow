package com.knowflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowflow.common.BusinessException;
import com.knowflow.common.PageResult;
import com.knowflow.dto.KbCreateRequest;
import com.knowflow.dto.KbUpdateRequest;
import com.knowflow.entity.ChatMessage;
import com.knowflow.entity.ChatSession;
import com.knowflow.entity.Document;
import com.knowflow.entity.KnowledgeBase;
import com.knowflow.mapper.ChatMessageMapper;
import com.knowflow.mapper.ChatSessionMapper;
import com.knowflow.mapper.DocumentMapper;
import com.knowflow.mapper.KnowledgeBaseMapper;
import com.knowflow.service.DocumentService;
import com.knowflow.service.KnowledgeBaseService;
import com.knowflow.service.security.OwnershipChecker;
import com.knowflow.vo.KbVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper kbMapper;
    private final DocumentMapper documentMapper;
    private final DocumentService documentService;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final OwnershipChecker ownershipChecker;

    @Override
    public KbVO create(Long userId, KbCreateRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new BusinessException(40022, "知识库名称不能为空");
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(userId);
        // 服务层保留兜底校验，避免内部调用绕过 Controller 参数校验。
        kb.setName(name);
        kb.setDescription(request.getDescription());
        kbMapper.insert(kb);
        return toVO(kb);
    }

    @Override
    public PageResult<KbVO> list(Long userId, long page, long size) {
        Page<KnowledgeBase> result = kbMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getUserId, userId)
                        .orderByDesc(KnowledgeBase::getUpdatedAt));
        List<KnowledgeBase> knowledgeBases = result.getRecords();
        Map<Long, KbDocumentCounts> countsByKbId = batchDocumentCounts(userId, knowledgeBases);
        List<KbVO> records = knowledgeBases.stream()
                .map(kb -> toVO(kb, countsByKbId))
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public KbVO getById(Long userId, Long kbId) {
        KnowledgeBase kb = getAndCheckOwner(userId, kbId);
        return toVO(kb);
    }

    @Override
    public KbVO update(Long userId, Long kbId, KbUpdateRequest request) {
        KnowledgeBase kb = getAndCheckOwner(userId, kbId);

        if (StringUtils.hasText(request.getName())) {
            kb.setName(request.getName());
        }
        if (request.getDescription() != null) {
            kb.setDescription(request.getDescription());
        }
        kbMapper.updateById(kb);
        return toVO(kb);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long kbId) {
        KnowledgeBase kb = getAndCheckOwner(userId, kbId);

        List<Document> docs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .eq(Document::getKbId, kbId)
                        .eq(Document::getUserId, userId));
        for (Document doc : docs) {
            documentService.delete(userId, doc.getId());
        }

        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getKbId, kbId)
                .eq(ChatMessage::getUserId, userId));
        chatSessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getKbId, kbId)
                .eq(ChatSession::getUserId, userId));
        kbMapper.deleteById(kb.getId());
    }

    /** 获取知识库并校验所有者 */
    private KnowledgeBase getAndCheckOwner(Long userId, Long kbId) {
        return ownershipChecker.requireKbOwner(userId, kbId);
    }

    private KbVO toVO(KnowledgeBase kb) {
        return KbVO.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .documentCount(countDocuments(kb.getUserId(), kb.getId(), null))
                .doneCount(countDocuments(kb.getUserId(), kb.getId(), "DONE"))
                .createdAt(kb.getCreatedAt())
                .updatedAt(kb.getUpdatedAt())
                .build();
    }

    private KbVO toVO(KnowledgeBase kb, Map<Long, KbDocumentCounts> countsByKbId) {
        KbDocumentCounts counts = countsByKbId.getOrDefault(kb.getId(), KbDocumentCounts.ZERO);
        return KbVO.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .documentCount(counts.documentCount())
                .doneCount(counts.doneCount())
                .createdAt(kb.getCreatedAt())
                .updatedAt(kb.getUpdatedAt())
                .build();
    }

    private Map<Long, KbDocumentCounts> batchDocumentCounts(Long userId, List<KnowledgeBase> knowledgeBases) {
        if (knowledgeBases.isEmpty()) {
            return Map.of();
        }
        List<Long> kbIds = knowledgeBases.stream()
                .map(KnowledgeBase::getId)
                .toList();
        return documentMapper.selectKbDocumentCounts(userId, kbIds).stream()
                .map(this::toKbDocumentCounts)
                .collect(Collectors.toMap(KbDocumentCounts::kbId, Function.identity()));
    }

    private KbDocumentCounts toKbDocumentCounts(Map<String, Object> row) {
        Long kbId = number(row, "kb_id", "kbId", "kbid");
        Long documentCount = number(row, "document_count", "documentCount", "documentcount");
        Long doneCount = number(row, "done_count", "doneCount", "donecount");
        return new KbDocumentCounts(kbId, documentCount, doneCount);
    }

    private Long number(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
        }
        return 0L;
    }

    private Long countDocuments(Long userId, Long kbId, String status) {
        LambdaQueryWrapper<Document> query = new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId)
                .eq(Document::getKbId, kbId);
        if (StringUtils.hasText(status)) {
            query.eq(Document::getStatus, status);
        }
        return documentMapper.selectCount(query);
    }

    private record KbDocumentCounts(Long kbId, Long documentCount, Long doneCount) {
        private static final KbDocumentCounts ZERO = new KbDocumentCounts(0L, 0L, 0L);
    }
}
